#!/usr/bin/env python3
"""文档索引（用户原话："之前就有很多报告"）。

问题：`docs/` 里 438 份文档、102 份验收、105 份修复，其中至少 4 份复盘讲的是同一批根因。
没有人能靠记忆检索，于是每轮都重新论证一遍同样的事——报告越写越多，知识却不增长。

做法：扫一遍 docs，把每份文档的标题、结论行、日期、锚点卡抽成一张索引。
索引只做"指路"，不复制结论；看结论仍要回原文（锚点强制可回源）。

用法：python3 data_scripts/docs-index.py [--write]
  --write 才落盘到 docs/索引-文档.md，默认只打印统计。
"""

import os
import re
import sys
import collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
OUT = os.path.join(DOCS, "索引-文档.md")

CONCL = re.compile(r"(总结论|结论)[：:]\s*(.{0,60})")
KIND = [("验收-", "验收"), ("修复-", "修复"), ("复盘", "复盘"), ("事故", "事故"),
        ("诊断", "诊断"), ("规划-", "规划"), ("分析-", "分析"), ("判定-", "判定"),
        ("规划", "规划")]


def kind_of(name):
    for pre, k in KIND:
        if name.startswith(pre) or (pre in name and pre not in ("规划",)):
            return k
    return "其它"


def scan():
    entries = []
    for fn in sorted(os.listdir(DOCS)):
        if not fn.endswith(".md") or fn == os.path.basename(OUT):
            continue
        path = os.path.join(DOCS, fn)
        try:
            with open(path, encoding="utf-8") as fh:
                head = fh.read(1200)
        except OSError:
            continue
        title = ""
        anchor = ""
        concl = ""
        for line in head.splitlines():
            s = line.strip()
            if not title and s.startswith("# "):
                title = s[2:].strip()
            if s.startswith("锚点卡："):
                anchor = s.replace("锚点卡：", "").strip("` ")
            m = CONCL.search(s)
            if m and not concl:
                concl = m.group(2).strip()
        if not title:
            title = fn[:-3]
        entries.append({"file": fn, "kind": kind_of(fn), "title": title,
                        "anchor": anchor, "concl": concl})
    return entries


def main():
    entries = scan()
    counts = collections.Counter(e["kind"] for e in entries)
    total = len(entries)
    print(f"文档 {total} 份：" + "、".join(f"{k} {v}" for k, v in counts.most_common()))
    if "--write" not in sys.argv:
        print("（未落盘；加 --write 生成 docs/索引-文档.md）")
        return 0

    lines = ["锚点卡：docs/锚点/S-02-气泡协议.md", "",
             "# 文档索引（自动生成，勿手改）", "",
             f"共 {total} 份。按类：{'、'.join(f'{k} {v}' for k, v in counts.most_common())}。", "",
             "用法：先按类找，再按标题找，读原文时回锚点卡。索引只指路，结论仍以原文为准。", ""]
    for kind in ["验收", "修复", "复盘", "事故", "诊断", "分析", "判定", "规划", "其它"]:
        group = [e for e in entries if e["kind"] == kind]
        if not group:
            continue
        lines.append(f"## {kind}（{len(group)} 份）")
        for e in group:
            tail = f" —— {e['concl']}" if e["concl"] else ""
            lines.append(f"- `docs/{e['file']}`{tail}")
        lines.append("")
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print(f"已写 {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
