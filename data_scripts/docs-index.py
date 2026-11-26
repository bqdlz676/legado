#!/usr/bin/env python3
"""文档索引（用户原话："之前就有很多报告"）。

问题：`docs/` 里 438 份文档、102 份验收、105 份修复，其中至少 4 份复盘讲的是同一批根因。
没有人能靠记忆检索，于是每轮都重新论证一遍同样的事——报告越写越多，知识却不增长。

做法：扫一遍 docs，把每份文档的标题、结论行、日期、锚点卡抽成一张索引。
索引只做"指路"，不复制结论；看结论仍要回原文（锚点强制可回源）。

M-56 根治（2026-11-26 Q-242）：`--write` 只改写 `<!-- GEN:START -->`…`<!-- GEN:END -->`
标记区间，区间外正文（人工维护节）逐字保留；旧实现整文件重写会冲掉人工节。
首次迁移：把现有生成正文包进标记、人工节留在标记外，并在文件头写迁移说明。

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

GEN_START = "<!-- GEN:START -->"
GEN_END = "<!-- GEN:END -->"
# 首次迁移切分点：人工维护节以该标题开头，整段留在标记区间外。
MANUAL_HEAD = r"^## 附："
MIGRATION_NOTE = ("> 人工维护区：生成器只改写下方 GEN:START…GEN:END 标记区间，"
                  "区间外正文逐字保留（M-56 根治，门禁 58 `check-manual-section.py`）。")

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


def split_regions(text):
    """返回 (pre, post)：区间外人工内容逐字保留。

    已有标记：取 START 之前 / END 之后；首次迁移（无标记）：标题行之前为 preamble，
    首个人工标题起到 EOF 为人工尾段，中间旧生成正文整体弃用、由本次重生成。
    标记行必须整行相等：人工节正文里反引号提及的 `<!-- GEN:START -->` 不算标记，
    否则会被误切（M-56 根治现场踩到：机制索引 §三 风险行字面写了标记串）。
    """
    lines = text.splitlines()
    start = next((i for i, l in enumerate(lines) if l.strip() == GEN_START), None)
    end = next((i for i, l in enumerate(lines) if l.strip() == GEN_END), None)
    if start is not None and end is not None:
        pre = "\n".join(lines[:start])
        post = "\n".join(lines[end + 1:])
        return pre.strip("\n"), post.strip("\n")
    title_idx = next((i for i, l in enumerate(lines) if l.startswith("# ")), 0)
    pre_lines = lines[:title_idx]
    manual_idx = next((i for i, l in enumerate(lines) if re.match(MANUAL_HEAD, l)), None)
    post_lines = lines[manual_idx:] if manual_idx is not None else []
    return "\n".join(pre_lines).strip("\n"), "\n".join(post_lines).strip("\n")


def compose(pre, body_lines, post, first_migration):
    """组装新文件：人工 preamble + 标记区间（新生成正文）+ 人工尾段。"""
    if first_migration and pre:
        pre = "\n".join([pre, MIGRATION_NOTE])
    out = []
    if pre:
        out.append(pre)
    out.append("")
    out.append(GEN_START)
    out += body_lines
    out.append(GEN_END)
    if post:
        out.append("")
        out.append(post)
    return "\n".join(out) + "\n"


def main():
    entries = scan()
    counts = collections.Counter(e["kind"] for e in entries)
    total = len(entries)
    print(f"文档 {total} 份：" + "、".join(f"{k} {v}" for k, v in counts.most_common()))
    if "--write" not in sys.argv:
        print("（未落盘；加 --write 生成 docs/索引-文档.md）")
        return 0

    existing = ""
    if os.path.isfile(OUT):
        with open(OUT, encoding="utf-8") as fh:
            existing = fh.read()
    pre, post = split_regions(existing)
    # 迁移判定按整行标记算：正文反引号提及标记串不算已迁移（同 split_regions 口径）。
    first_migration = bool(existing) and not any(
        l.strip() == GEN_START for l in existing.splitlines())

    body = ["# 文档索引（自动生成，勿手改）", "",
            f"共 {total} 份。按类：{'、'.join(f'{k} {v}' for k, v in counts.most_common())}。", "",
            "用法：先按类找，再按标题找，读原文时回锚点卡。索引只指路，结论仍以原文为准。", ""]
    for kind in ["验收", "修复", "复盘", "事故", "诊断", "分析", "判定", "规划", "其它"]:
        group = [e for e in entries if e["kind"] == kind]
        if not group:
            continue
        body.append(f"## {kind}（{len(group)} 份）")
        for e in group:
            tail = f" —— {e['concl']}" if e["concl"] else ""
            body.append(f"- `docs/{e['file']}`{tail}")
        body.append("")

    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(compose(pre, body, post, first_migration))
    print(f"已写 {OUT}（GEN 区间外人工节逐字保留）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
