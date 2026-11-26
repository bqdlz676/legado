#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 58 人工节保留检查（M-56 根治，2026-11-26 Q-242）。

为什么需要它：索引生成器 `--write` 旧实现整文件重写，手工维护的门禁表/风险行被静默
冲掉（M-56：Q-229 现场 §三 人工门禁表被覆盖，回贴后又被并发 `--write` 二次覆盖）。
生成器改造为只写 `<!-- GEN:START -->`…`<!-- GEN:END -->` 区间后，必须再守一道机检：
人工节不仅要存在，还必须落在 GEN 区间之外——写在区间内照样会被下次 `--write` 冲掉。

判据（任一不满足即红）：
  ① 索引文件存在，且成对出现 GEN:START / GEN:END，START 在 END 之前，区间非空；
  ② 人工节点名标记齐备：机制索引「52~56」「M-56」，文档索引「52 视觉基线」「M-56」；
  ③ 每个标记所在行位于 GEN 区间之外（START 之前或 END 之后）；落在区间内判红。

用法：
  python3 data_scripts/check-manual-section.py            # 判两份真索引
  python3 data_scripts/check-manual-section.py --selftest # 双向夹具自测
退出码：全部 PASS=0；任一 FAIL=1。
"""
import pathlib
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
GEN_START = "<!-- GEN:START -->"
GEN_END = "<!-- GEN:END -->"

# 每份索引必须点名的人工节标记；缺失即红。
TARGETS = [
    {"file": "docs/索引-机制.md", "keywords": ["52~56", "M-56"]},
    {"file": "docs/索引-文档.md", "keywords": ["52 视觉基线", "M-56"]},
]


def check_path(path: pathlib.Path, keywords: list) -> list:
    """返回问题清单；空列表＝该文件人工节合规。"""
    problems = []
    if not path.is_file():
        return [f"{path.name} 不存在"]
    text = path.read_text(encoding="utf-8", errors="strict")
    lines = text.splitlines()
    # 标记行整行相等才算：人工节正文里反引号提及的标记串不算标记（同生成器口径）。
    start = next((i for i, l in enumerate(lines) if l.strip() == GEN_START), None)
    end = next((i for i, l in enumerate(lines) if l.strip() == GEN_END), None)
    if start is None or end is None:
        problems.append(f"{path.name} 缺 GEN:START/GEN:END 标记")
        return problems
    if start >= end:
        problems.append(f"{path.name} GEN:START 在 GEN:END 之后")
    region = "\n".join(lines[start + 1:end]).strip()
    if not region:
        problems.append(f"{path.name} GEN 区间为空（生成正文被掏空）")
    for kw in keywords:
        hits = [i for i, l in enumerate(lines) if kw in l]
        if not hits:
            problems.append(f"{path.name} 人工节缺点名标记「{kw}」")
            continue
        for i in hits:
            if start < i < end:
                problems.append(
                    f"{path.name} 「{kw}」位于 GEN 区间内（行 {i + 1}），会被 --write 冲掉")
    return problems


def main() -> int:
    problems = []
    for t in TARGETS:
        problems += check_path(ROOT / t["file"], t["keywords"])
    if problems:
        print("MANUAL-SECTION: FAIL（人工节缺失或落在 GEN 区间内）")
        for p in problems:
            print(f"  - {p}")
        return 1
    print(f"MANUAL-SECTION: PASS（{len(TARGETS)} 份索引人工节均存在且在 GEN 区间外）")
    return 0


def selftest() -> int:
    """双向自测：①人工节在区间外必须 PASS；②落在区间内、③标记缺失必须 FAIL。"""
    d = pathlib.Path(tempfile.mkdtemp(prefix="q242-"))
    kw = ["52~56", "M-56"]
    good = d / "good.md"
    good.write_text(
        "<!-- GEN:START -->\n# 生成正文\n<!-- GEN:END -->\n\n"
        "## 三、门禁 52~56\n\n> 风险（M-56）：人工节在区间外。\n", encoding="utf-8")
    inside = d / "inside.md"
    inside.write_text(
        "<!-- GEN:START -->\n## 三、门禁 52~56\n\n> 风险（M-56）：被包进区间。\n"
        "<!-- GEN:END -->\n", encoding="utf-8")
    missing = d / "missing.md"
    missing.write_text("<!-- GEN:START -->\n# 生成正文\n<!-- GEN:END -->\n", encoding="utf-8")
    g = check_path(good, kw)
    i = check_path(inside, kw)
    m = check_path(missing, kw)
    ok = not g and bool(i) and bool(m)
    print("SELFTEST: " + ("PASS（夹具：区间外 PASS；区间内/缺失均判红）" if ok else
                          f"FAIL（good={g} inside={i} missing={m}）"))
    return 0 if ok else 1


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        sys.exit(selftest())
    sys.exit(main())
