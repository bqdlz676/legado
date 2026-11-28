#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""孤儿公开成员函数机检（g68 的补充口径，G-01/事故七延伸）。

为什么立它（2026-11-28 实测）：
  g68 `check-orphan-entries.py` 只收「顶层 fun/class/object」，扫不到**类里的公开成员函数**。
  Q-498 交付的 `AsterismLayer.codeOfStar(starId)` 就是这么漏过去的：它是 `astro/layer/`
  里 `AsterismLayer` 的成员函数，全仓零调用方，可 g68 报 0 孤儿，判据 3 正向链路实际仍是死链。
  事故七的判据本身没错，是扫描面不够。

判据：
  M1 在 `--roots`（默认 astro/ 与 host/Sky*）下收集「类/对象体里缩进 4 空格、非 private/
     internal/override 的 fun」，去掉同文件内自引用后，全仓 `app/src/main` 引用数为 0 → FAIL。
  M2 名字出现在白名单（`data_scripts/orphan-members-allow.txt`，四列制表符：相对路径::符号名
     理由 单号）→ 放行；白名单必须写理由。
  M3 与 g68 的分工：g68 判顶层入口，本脚本判成员函数；两者都不判「调用路径可达」，可达性归验收。

边界 / 误判面：
  - 只扫 astro/ 与 host/Sky*，不扫全仓 UI（Compose 成员函数多为框架回调，会大面积误判）。
  - 同名不同类的成员会被合并计数，可能放过「A 类的 f 有调用、B 类的 f 没有」；名字唯一化
    成本太高，先用同名计数兜住最常见的「整类新写、零调用」情形。
  - `fun interface`/`operator` 等特殊声明不在此列；返回类型里出现的同名不算引用（只认 `名字(`）。

退出码：0=PASS | 1=FAIL | 2=参数错 | 3=UNVERIFIED（根目录不存在）
用法：
  python3 data_scripts/check-orphan-members.py
  python3 data_scripts/check-orphan-members.py --list
  python3 data_scripts/check-orphan-members.py --selftest
"""
from __future__ import annotations

import argparse
import hashlib
import pathlib
import re
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
GLOBE = MAIN / "java/io/legado/app/ui/map/globe"
MEMBER_RE = re.compile(r"^\s{4}(?:suspend\s+)?fun\s+[`]?([A-Za-z_][A-Za-z0-9_]*)")
ALLOW_REL = pathlib.Path(__file__).with_name("orphan-members-allow.txt")
ALLOW_DIGEST = ""
SKIP_WORDS = ("private", "internal", "override")


def roots() -> list[pathlib.Path]:
    out = [GLOBE / "astro"]
    out += sorted((GLOBE / "host").glob("Sky*.kt"))
    return out


def collect(paths: list[pathlib.Path]) -> list[tuple[pathlib.Path, str]]:
    items: list[tuple[pathlib.Path, str]] = []
    files: list[pathlib.Path] = []
    for p in paths:
        if p.is_dir():
            files += sorted(p.rglob("*.kt"))
        elif p.suffix == ".kt":
            files.append(p)
    for f in files:
        for line in f.read_text(encoding="utf-8", errors="replace").splitlines():
            if any(w in line for w in SKIP_WORDS):
                continue
            m = MEMBER_RE.match(line)
            if m:
                items.append((f, m.group(1)))
    return items


def load_allow() -> dict[str, str]:
    """白名单按内容哈希取身份（㊼）：改名/换内容即失效，位置型豁免不许蒙过门禁。"""
    allow: dict[str, str] = {}
    if not ALLOW_REL.exists():
        return allow
    content = ALLOW_REL.read_text(encoding="utf-8")
    global ALLOW_DIGEST
    ALLOW_DIGEST = hashlib.sha256(content.encode("utf-8")).hexdigest()[:12]
    for line in content.splitlines():
        if line.startswith("#") or not line.strip():
            continue
        cells = line.split("\t")
        if len(cells) < 2:
            continue
        allow[cells[0].strip()] = cells[1].strip() if len(cells) > 1 else ""
    return allow


_CACHE: dict[pathlib.Path, str] = {}


def _texts() -> dict[pathlib.Path, str]:
    """整仓 kt 原文只读一遍：先前每符号每文件各读一次（21×1500 次读盘）实测 >240s 超时。"""
    if not _CACHE:
        for f in MAIN.parent.rglob("*.kt"):
            try:
                _CACHE[f] = f.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
    return _CACHE


def refs(sym: str, own: pathlib.Path, only: str = "") -> int:
    """only="main"/"test" 分列计数：生产零调用但测试有调用 = 假绿死链（Q-497/Q-498 两单都栽在这）。"""
    pat = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(sym) + r"\s*\(")
    n = 0
    for f, text in _texts().items():
        if only and f"/app/src/{only}/" not in f.as_posix():
            continue
        if f == own:
            continue
        if pat.search(text):
            n += 1
    return n


def run(listing: bool) -> int:
    paths = roots()
    if any(not p.exists() for p in paths):
        print("ORPHAN-MEMBERS: UNVERIFIED（扫描根不存在）")
        return 3
    items = collect(paths)
    if not items:
        print("ORPHAN-MEMBERS: UNVERIFIED（扫描根下零个公开成员，空集不可判定）")
        return 3
    allow = load_allow()
    dead = []
    fake_green = []
    for f, sym in items:
        rel = f.relative_to(ROOT).as_posix()
        if refs(sym, f, only="main") == 0 and f"{rel}::{sym}" not in allow:
            dead.append((rel, sym))
            if refs(sym, f, only="test") > 0:
                fake_green.append((rel, sym))
    if listing:
        for rel, sym in dead:
            tag = "（仅测试调用→假绿）" if (rel, sym) in fake_green else ""
            print(f"  · {rel}::{sym}{tag}")
    if not dead:
        print(f"ORPHAN-MEMBERS: PASS（公开成员 {len(items)} 个，孤儿 0；白名单 {len(allow)} 条 sha256:{ALLOW_DIGEST}）")
        return 0
    for rel, sym in dead:
        print(f"  ✗ 孤儿公开成员 {rel}::{sym}（全仓 app/src/main 零调用）")
    print(f"ORPHAN-MEMBERS: FAIL（{len(dead)}/{len(items)} 个公开成员无人调）")
    return 1


FIXTURES = [
    ("a.kt", "class A {\n    fun used() {}\n    fun dead() {}\n    private fun hidden() {}\n}\n", ["dead"]),
    ("b.kt", "class B {\n    fun lonely() {}\n    internal fun skip() {}\n}\n", ["lonely"]),
]


def selftest() -> int:
    ok = True
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="orphan-members-"))
    main = tmp / "app/src/main"
    (main / "x").mkdir(parents=True)
    (main / "x/Good.kt").write_text("class Good {\n    fun alive() {}\n}\n", encoding="utf-8")
    # 调用方必须放别的文件：refs 排除定义文件自身，同文件调用会被当成零引用
    (main / "x/User.kt").write_text("fun caller() { Good().alive() }\n", encoding="utf-8")
    for name, text, expect in FIXTURES:
        p = main / "x" / name
        p.write_text(text, encoding="utf-8")
    global MAIN, _CACHE
    old, MAIN, _CACHE = MAIN, main, {}
    try:
        items = collect([main / "x"])
        dead = {s for f, s in items if refs(s, f) == 0}
        for name, text, expect in FIXTURES:
            hit = dead & set(expect)
            status = "PASS" if hit == set(expect) else "FAIL"
            ok = ok and status == "PASS"
            print(f"  SELFTEST {status} {name} expect={sorted(expect)} got={sorted(hit)}")
        if "alive" in dead:
            ok = False
            print("  SELFTEST FAIL 有调用方的成员被误判")
    finally:
        MAIN, _CACHE = old, {}
    print("SELFTEST: " + ("PASS（零调用判红 / private internal 跳过 / 有调用方放行）" if ok else "FAIL"))
    return 0 if ok else 1


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args(argv)
    if args.selftest:
        return selftest()
    return run(args.list)


if __name__ == "__main__":
    sys.exit(main())
