#!/usr/bin/env python3
"""S6 依赖符号存在性索引检查（离线，不编译；2026-11-26 88e62bc 两路 CI 红复盘产物）。

为什么需要它：
  S5（kotlin-syntax-lint.py 的种子表）只能抓"同一符号在仓内已有文件里包不一致"，
  抓不到"抄到一个根本不存在的符号"。本脚本从真实依赖包（Google Maven 下载的
  AAR classes.jar + sources.jar）抽出类名与包级声明名，落成 data_scripts/dep-symbols.json，
  push 前对每条 import 判：这个包、这个符号到底在不在依赖里。

判据（保守，宁 WARN 不 FAIL，避免误杀把门禁打成噪音）：
  作用域：只判索引根（androidx.compose.* / androidx.activity.* / androidx.lifecycle.compose.*）
  下的 import；其余依赖（androidx.core、kotlinx、okhttp、仓内 io.legado…）直接 SKIP，不判。
  对每条 import <pkg>.<sym>（通配符 import …* 跳过）：
    ① 包路径不在索引            → FAIL（包不存在，硬事实）
    ② sym 命中 classes/decls/ktClasses → PASS
    ③ 近似兜底：首字母大写化 + "Kt" 命中 ktClasses（顶层文件类）→ PASS（近似）
    ④ 仍未命中：
       - sym 首字母小写（函数/属性形态）→ WARN（可能是未收录构件里的扩展函数，不判死）
       - sym 首字母大写（类/对象/枚举形态）→ FAIL（类不存在，硬事实）

退出码：0 = 无 FAIL（WARN 不计失败）；1 = 有 FAIL。
用法：
  python3 data_scripts/check-dependency-symbols.py <kt 文件…>   # 指定文件
  python3 data_scripts/check-dependency-symbols.py             # 只查 git 本轮改动的 kt
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INDEX = os.path.join(ROOT, "data_scripts", "dep-symbols.json")

IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$")


def load_index():
    with open(INDEX, encoding="utf-8") as fh:
        doc = json.load(fh)
    return doc["packages"], tuple(doc["meta"]["indexedRoots"])


def changed_kt_files():
    """本轮新增/改动的 kt 文件；工作区干净时返回空列表（调用方按"无需检查"处理）。"""
    # -uall：未跟踪目录默认被折叠成一条目录记录，列不出里面的 .kt，必须展开到文件级
    out = subprocess.run(
        ["git", "-c", "core.quotepath=false", "status", "--porcelain", "-uall"],
        cwd=ROOT, capture_output=True, text=True,
        env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"},
    ).stdout
    files = []
    for row in out.splitlines():
        status, path = row[:2], row[3:].strip().strip('"')
        if status.strip() and status[0] in "AM?" and path.endswith(".kt"):
            files.append(path)
    return files


def check_file(path, packages, roots):
    """返回 (fails, warns, checked, skipped)。"""
    fails, warns, checked, skipped = [], [], 0, 0
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        return fails, warns, checked, skipped
    with open(full, encoding="utf-8", errors="ignore") as fh:
        for lineno, line in enumerate(fh, 1):
            m = IMPORT_RE.match(line)
            if not m:
                continue
            fqn = m.group(1)
            if fqn.endswith(".*"):
                continue
            if not fqn.startswith(roots):
                skipped += 1
                continue
            pkg, _, sym = fqn.rpartition(".")
            checked += 1
            if pkg not in packages:
                fails.append(f"{path}:{lineno} FAIL 包不存在：{fqn}")
                continue
            bucket = packages[pkg]
            if sym in bucket["classes"] or sym in bucket["decls"] or sym in bucket["ktClasses"]:
                continue
            # 近似兜底：顶层文件类 XxxKt（函数名首字母大写化 + Kt）
            kt_candidate = sym[:1].upper() + sym[1:] + "Kt"
            if kt_candidate in bucket["ktClasses"]:
                continue
            if sym[:1].islower():
                warns.append(f"{path}:{lineno} WARN 顶层函数/属性未在索引命中（不判死）：{fqn}")
            else:
                fails.append(f"{path}:{lineno} FAIL 类不存在：{fqn}")
    return fails, warns, checked, skipped


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not os.path.isfile(INDEX):
        print(f"DEP-SYMBOLS: FAIL 索引缺失：{INDEX}（先按 docs/机制-S6依赖符号索引.md 重建）")
        return 1
    packages, roots = load_index()

    files = args or changed_kt_files()
    if not files:
        print("DEP-SYMBOLS: PASS（本轮无 kt 改动）")
        return 0

    total_fail = total_warn = total_checked = total_skip = 0
    for path in files:
        fails, warns, checked, skipped = check_file(path, packages, roots)
        for f in fails:
            print(f)
        for w in warns:
            print(w)
        total_fail += len(fails)
        total_warn += len(warns)
        total_checked += checked
        total_skip += skipped

    verdict = "FAIL" if total_fail else "PASS"
    print(f"DEP-SYMBOLS: {verdict}（{len(files)} 文件，{total_checked} 条索引域 import，"
          f"{total_warn} WARN，{total_skip} 条域内跳过）")
    return 1 if total_fail else 0


if __name__ == "__main__":
    sys.exit(main())
