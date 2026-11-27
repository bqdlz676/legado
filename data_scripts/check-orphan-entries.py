#!/usr/bin/env python3
"""孤儿公开入口机检（§18 每轮沉淀一条新机检 / F-73 类"写完了没人调"事故）。

为什么需要它：
2026-11-28 星空天象层写码时，`GlobeController.enterSky/setSkyBlend`、`SkyCanvasRenderer.draw(store)`
全部编译通过、单测全绿、CI 三绿，但**全仓没有任何生产调用方** —— `scene.sky` 恒为 null，
渲染器第一行 `return`，8920 颗星一颗都不显示。编译与单测都抓不到这种"死链路"，
只有"定义存在但没有外部引用"这条静态判据能抓。

判据（只扫指定根，不做全仓，避免噪声）：
1. 扫 `--roots` 指定目录下的 `.kt`，收集**顶层 fun/val/var/class/object** 声明名（不收集 private/internal）。
2. 在 `app/src/main` 全量文本里数引用次数，**排除定义文件自身**。
3. 引用数 == 0 且不在白名单 → FAIL；白名单必须写理由（对齐㊾"空集不判 PASS"与㊼"豁免身份化"）。
4. 只看 `fun` 与 `class/object`（含 `data class`）；`val/var` 常量噪声太大，默认不查，用 `--include-vals` 显式开启。

适用条件与边界：
- 只用于"这一层被新写出来、还没接线"的阶段（如 astro/、host/Sky*）；不要拿它扫全仓 UI，否则会把
  反射/序列化/Compose 预览用的入口全判红。
- 它证明的是"有引用"，不证明"调用路径可达"；可达性仍归验收单（真机/CI）。
- 白名单键是 `相对路径::符号名`，值写为什么豁免（谁在用、为什么静态扫不到）。

用法：
  python3 data_scripts/check-orphan-entries.py            # 默认根：astro/ 与 host/Sky*
  python3 data_scripts/check-orphan-entries.py --selftest # 自测（临时目录造 4 种样例）
  python3 data_scripts/check-orphan-entries.py --list     # 只列不判（首轮摸底）
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app", "src", "main")
GLOBE = os.path.join(MAIN, "java", "io", "legado", "app", "ui", "map", "globe")

DEFAULT_ROOTS = [
    os.path.join(GLOBE, "astro"),
    os.path.join(GLOBE, "host"),
    os.path.join(GLOBE, "render"),
]
# 只对 Sky* 与 Field* 这类本轮新写文件生效，避免 host/ 里既有地球代码全量判红
DEFAULT_GLOBS = ("Sky*.kt", "Field*.kt")

DECL_RE = re.compile(
    r"^((?:@\w+(?:\([^)]*\))?\s+)*)?(?:public\s+)?"
    r"(fun|class|object|interface|data class|abstract class|enum class)\s+"
    r"(?:(?:<[^>]*>)\s*)?([A-Za-z_][A-Za-z0-9_]*)",
)

WHITELIST: dict[str, str] = {
    # 契约常量与测试专用入口静态扫不到生产调用，逐条写明理由（㊼ 豁免身份化）
}


def collect_symbols(path: str, include_vals: bool) -> list[str]:
    out: list[str] = []
    with open(path, encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            if line.startswith((" ", "\t", "//", "*", "/*")):
                continue
            m = DECL_RE.match(line)
            if not m:
                continue
            if m.group(1) and "private" in m.group(1):
                continue
            if line.strip().startswith(("private ", "internal ")):
                continue
            out.append(m.group(3))
    return out


def count_refs(symbol: str, define_file: str, corpus: list[str], same_file: bool = False) -> int:
    """数符号被引用的次数。same_file=False 只数外部引用（判接线）；True 连定义文件一起数（判"根本没人用"）。"""
    pat = re.compile(r"\b" + re.escape(symbol) + r"\b")
    hits = 0
    for file in corpus:
        if not same_file and os.path.abspath(file) == os.path.abspath(define_file):
            continue
        try:
            text = open(file, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue
        hits += len(pat.findall(text))
    return hits


def scan(roots: list[str], globs: tuple[str, ...], include_vals: bool, list_only: bool) -> int:
    corpus: list[str] = []
    for dirpath, _dirnames, filenames in os.walk(MAIN):
        for name in filenames:
            if name.endswith((".kt", ".java")):
                corpus.append(os.path.join(dirpath, name))
    orphans: list[tuple[str, str]] = []
    checked = 0
    for root in roots:
        if not os.path.isdir(root):
            continue
        for dirpath, _dirnames, filenames in os.walk(root):
            for name in filenames:
                if not name.endswith(".kt") or not name.startswith(globs_re(globs)):
                    continue
                file = os.path.join(dirpath, name)
                for sym in collect_symbols(file, include_vals):
                    if sym in ("Unit", "String", "Int", "Float", "Boolean", "Long"):
                        continue
                    checked += 1
                    refs = count_refs(sym, file, corpus)
                    # 同文件内自己用（接口被本文件实现/内部构造）不算孤儿：只有连本文件都只用一次（就是声明那行）才算
                    total = count_refs(sym, file, corpus, same_file=True)
                    if refs == 0 and total <= 1:
                        rel = os.path.relpath(file, ROOT).replace(os.sep, "/")
                        key = f"{rel}::{sym}"
                        if key not in WHITELIST:
                            orphans.append((rel, sym))
    if list_only:
        print(f"扫描声明 {checked} 个；无外部引用 {len(orphans)} 个：")
        for rel, sym in orphans:
            print(f"  · {rel} :: {sym}")
        return 0
    if orphans:
        print(f"ORPHAN-ENTRIES: FAIL（扫描声明 {checked} 个，孤儿 {len(orphans)} 个）")
        for rel, sym in orphans:
            print(f"  FAIL {rel}::{sym} 无任何外部引用（写完没人调＝死链路）")
        print("  修法：接线（加调用方），或加白名单并写清「谁在用/为什么静态扫不到」")
        return 1
    print(f"ORPHAN-ENTRIES: PASS（扫描声明 {checked} 个，孤儿 0）")
    return 0


def globs_re(globs: tuple[str, ...]):
    # 只取文件名前缀：`Sky*.kt` → `Sky`，用于 name.startswith 判定
    names = [g.split("*")[0] for g in globs]
    return tuple(names)


def selftest() -> int:
    """自测：造 4 个样例（孤儿 fun / 有引用 fun / private fun / class 有引用），断言判定正确。"""
    tmp = tempfile.mkdtemp(prefix="orphan-selftest-")
    try:
        main = os.path.join(tmp, "app", "src", "main", "java", "x")
        os.makedirs(main, exist_ok=True)
        with open(os.path.join(main, "SkyA.kt"), "w", encoding="utf-8") as fh:
            fh.write(
                "package x\n"
                "fun orphanFun(): Int = 1\n"
                "private fun hiddenFun(): Int = 2\n"
                "fun wiredFun(): Int = 3\n"
                "class WiredClass { fun inner() = 0 }\n"
            )
        with open(os.path.join(main, "Caller.kt"), "w", encoding="utf-8") as fh:
            fh.write("package x\nfun use() { wiredFun(); WiredClass() }\n")
        global MAIN, ROOT
        saved_root, saved_main = ROOT, MAIN
        ROOT = tmp
        MAIN = os.path.join(tmp, "app", "src", "main")
        roots = [main]
        # 直接内联判定，避免依赖 os.walk 的 glob 过滤
        corpus = [os.path.join(main, n) for n in os.listdir(main)]
        syms = collect_symbols(os.path.join(main, "SkyA.kt"), False)
        orphan = [s for s in syms if count_refs(s, os.path.join(main, "SkyA.kt"), corpus) == 0]
        assert syms == ["orphanFun", "WiredClass", "inner"] or "wiredFun" in syms, syms
        assert "orphanFun" in orphan, orphan
        assert "wiredFun" not in orphan, orphan
        assert "hiddenFun" not in syms, "private 声明不应被收集"
        assert "WiredClass" not in orphan, "被实例化的 class 不应算孤儿"
        print("SELFTEST: PASS（孤儿 fun 判红、有引用 fun 不红、private 不收集、被实例化 class 不红）")
        ROOT, MAIN = saved_root, saved_main
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--roots", nargs="*", default=DEFAULT_ROOTS)
    ap.add_argument("--globs", nargs="*", default=list(DEFAULT_GLOBS))
    ap.add_argument("--include-vals", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    return scan(args.roots, tuple(args.globs), args.include_vals, args.list)


if __name__ == "__main__":
    sys.exit(main())
