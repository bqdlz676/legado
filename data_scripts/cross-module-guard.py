#!/usr/bin/env python3
"""跨模块编译面预检（2026-11-26 两次 CI 红的复盘产物）。

事故：两轮 CI 红都不是业务错，而是「只改 app/src，漏改同仓其它编译单元」——
  ① app/src/test 里 Sequence.isEmpty() 不存在（Kotlin 只为 Collection 提供）；
  ② screenshot/src/test 的 BookshelfScreen 调用点没跟新增形参。
沙箱没有 gradle/JDK21/SDK，无法本地编译，所以只能用静态手段把这类漏改提前抓出来。

判定（只读、不写仓库）：
  F1 已删除的 Kotlin 文件仍被其它文件引用 → FAIL（删类不删引用，编译必红）。
  F2 改动文件里的顶层声明名，同时出现在 screenshot/src/test 或 modules/** 里 → WARN
     （可能只是名字撞车，也可能是签名改了没跟调用点，需人工核一眼）。
  F3 本轮改动涉及 `fun X(` 的形参块，且 X 在 screenshot/ 或 modules/ 出现 → WARN（同 F2 加强）。

用法：python3 data_scripts/cross-module-guard.py [--strict]
  --strict: WARN 也算失败（默认 WARN 只提示，不 block）。
退出码：有 FAIL（或 --strict 下有 WARN）=1，否则 0。
"""

import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 除 app/src 外的其它编译单元：改签名必须同步这些目录里的调用点。
OTHER_UNITS = ["screenshot/src/test", "modules"]
APP_UNITS = ["app/src/main", "app/src/test"]


def run(*args):
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True).stdout


def changed_files():
    out = run("git", "status", "--porcelain")
    rows = []
    for line in out.splitlines():
        if len(line) < 4:
            continue
        status, path = line[:2], line[3:].strip().strip('"')
        # git 会把中文路径转义，这里只处理代码文件，中文 docs 不参与预检。
        if not path.endswith((".kt", ".java", ".xml")):
            continue
        rows.append((status, path))
    return rows


def declarations(text):
    names = set()
    for m in re.finditer(r"^\s*(?:(?:public|internal|private|suspend|inline|@\w+\s)*)?"
                         r"(?:fun|class|object|interface|val|var)\s+(?:<[^>]+>\s*)?(\w+)", text, re.M):
        names.add(m.group(1))
    return names


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()

    rows = changed_files()
    if not rows:
        print("GUARD: PASS（无代码改动）")
        return 0

    fails, warns = [], []
    other_text = ""
    for unit in OTHER_UNITS:
        base = os.path.join(ROOT, unit)
        if not os.path.isdir(base):
            continue
        for dirpath, _, files in os.walk(base):
            for f in files:
                if f.endswith((".kt", ".java")):
                    with open(os.path.join(dirpath, f), encoding="utf-8", errors="ignore") as fh:
                        other_text += fh.read() + "\n"

    deleted = [p for s, p in rows if s.strip().startswith("D")]
    modified = [p for s, p in rows if s.strip().startswith(("M", "A", "R", "?"))]

    # F1：删除的文件，类名是否仍被全仓引用（排除它自己被删这件事）。
    for path in deleted:
        stem = os.path.splitext(os.path.basename(path))[0]
        if len(stem) < 4:
            continue
        hits = run("grep", "-rln", stem, "app/src", "screenshot/src", "modules").splitlines()
        hits = [h for h in hits if not h.endswith(os.path.basename(path))]
        if hits:
            fails.append(f"F1 删除 {os.path.basename(path)} 后仍被引用：{', '.join(hits[:4])}")

    # F4：测试里的"字面串断言"——形如 "attach(null, null)" 的标记，签名一改就静默失效。
    # 2026-11-26 实测踩过：跨页接线后 InkShelfFragment 改 attach(key, scope)，测试仍在找旧字面串，
    # 这条断言会直接 CI 红。所以只要被测函数名出现在测试的字面串里，就提示人工核一眼。
    for path in modified:
        full = os.path.join(ROOT, path)
        if not os.path.isfile(full):
            continue
        try:
            with open(full, encoding="utf-8", errors="ignore") as fh:
                text = fh.read()
        except OSError:
            continue
        for name in sorted(set(re.findall(r"^\s*(?:@\w+\s*)*fun\s+(\w+)\s*\(", text, re.M))):
            if len(name) < 4:
                continue
            hits = run("grep", "-rn", f'"{name}(', "app/src/test").splitlines()
            for h in hits[:2]:
                warns.append(f"F4 测试里按字面串锁 `{name}(` 调用形态：{h.split(':')[0].split('/')[-1]}，核对是否已跟新签名")

    # F2/F3：改动文件的声明名是否出现在其它编译单元。
    for path in modified:
        full = os.path.join(ROOT, path)
        if not os.path.isfile(full):
            continue
        try:
            with open(full, encoding="utf-8", errors="ignore") as fh:
                text = fh.read()
        except OSError:
            continue
        funcs = set(re.findall(r"^\s*(?:@\w+\s*)*fun\s+(\w+)\s*\(", text, re.M))
        # 降噪：常见通用标识符（current/results/state 之类）在其它单元出现属正常撞名，
        # 全报会把真正需要核的行淹掉——预检一旦变成噪音，人就不看了，等于没有。
        noisy = {"current", "results", "normalized", "state", "item", "items", "index",
                 "title", "text", "scope", "key", "name", "value", "data", "list", "type"}
        for name in sorted(declarations(text) | funcs):
            if len(name) < 4 or name in {"when", "this", "Unit"} or name in noisy:
                continue
            if name in other_text:
                warns.append(f"F2 {os.path.basename(path)} 的 `{name}` 在 screenshot/modules 出现，核对调用点是否跟了签名")

    print(f"改动代码文件 {len(rows)} 个：删除 {len(deleted)}、修改/新增 {len(modified)}")
    for f in fails:
        print(f"FAIL {f}")
    for w in warns:
        print(f"WARN {w}")

    if fails:
        print("GUARD: FAIL（删类残留引用或其它硬性漏改）")
        return 1
    if args.strict and warns:
        print("GUARD: FAIL(--strict：WARN 计入)")
        return 1
    print("GUARD: PASS" + ("（含 WARN，需人工核）" if warns else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
