#!/usr/bin/env python3
"""机制索引（事故"机制双轨"的产物，2026-11-26）。

事故：我先写了一份"七层金字塔"，写完才发现仓库里早有 F-92 组合验收金字塔五层
+ 铁律 #46 组装层级 + 预存三件套。同一件事两套口径 = 双轨，等于我自己制造缝合怪。
根因：机制散落在 244 条 F-xx 规则与十余份 机制-*.md 里，没有一张能查的表，新建机制前
没人会先去翻。

对策：把规则全集里的每条 F-xx 与所有 机制-*.md 抽成索引，新建机制前 grep 关键词即可查重。
本脚本只读规则/文档，不改任何既有文档。

M-56 根治（2026-11-26 Q-242）：`--write` 只改写 `<!-- GEN:START -->`…`<!-- GEN:END -->`
标记区间，区间外正文（人工维护节）逐字保留；旧实现整文件重写会冲掉人工节。
首次迁移：把现有生成正文包进标记、人工节留在标记外，并在文件头写迁移说明。

用法：python3 data_scripts/mechanism-index.py [--write]
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.path.join(ROOT, "docs", "规则全集.md")
DOCS = os.path.join(ROOT, "docs")
OUT = os.path.join(DOCS, "索引-机制.md")

GEN_START = "<!-- GEN:START -->"
GEN_END = "<!-- GEN:END -->"
# 首次迁移切分点：人工维护节以该标题开头，整段留在标记区间外。
MANUAL_HEAD = r"^## 三、"
MIGRATION_NOTE = ("> 人工维护区：生成器只改写下方 GEN:START…GEN:END 标记区间，"
                  "区间外正文逐字保留（M-56 根治，门禁 58 `check-manual-section.py`）。")


def has_machine_evidence(body, fid):
    """规则全集里 F-xx 的正文块（表格之外）若出现「机械门禁」，说明这条有脚本兜底。
    只看表格行会漏掉全部机检——表格只是摘要，正文块才写门禁编号与脚本名。"""
    for m in re.finditer(re.escape(fid), body):
        window = body[m.start(): m.start() + 400]
        if "机械门禁" in window:
            return True
    return False


def rules():
    try:
        body = open(RULES, encoding="utf-8").read()
    except OSError:
        return []
    rows = []
    for m in re.finditer(r"^\|\s*(F-\d+)\s*\|\s*([^|]+?)\s*\|", body, re.M):
        text = re.sub(r"\s+", " ", m.group(2)).strip()
        rows.append((m.group(1), text, has_machine_evidence(body, m.group(1))))
    return rows


def mechanism_docs():
    return sorted(f for f in os.listdir(DOCS)
                  if f.endswith(".md") and (f.startswith(("机制-", "铁律-", "规则-", "口径冻结")) or "机制" in f))


def machine_check(text):
    # 描述里点名脚本/门禁编号的，视为已有机器执行入口；其余只能靠人。
    return bool(re.search(r"\.py|\.sh|门禁\s*\d|㉑|⑳|⑲|⑱", text))


def split_regions(text):
    """返回 (pre, post)：区间外人工内容逐字保留。

    已有标记：取 START 之前 / END 之后；首次迁移（无标记）：标题行之前为 preamble，
    首个人工标题起到 EOF 为人工尾段，中间旧生成正文整体弃用、由本次重生成。
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
    rs = rules()
    docs = mechanism_docs()
    with_machine = sum(1 for _, _, h in rs if h)
    print(f"规则 {len(rs)} 条，其中点名机检入口的 {with_machine} 条；机制类文档 {len(docs)} 份")

    if "--write" not in sys.argv:
        print("（未落盘；加 --write 生成 docs/索引-机制.md）")
        return 0

    existing = ""
    if os.path.isfile(OUT):
        with open(OUT, encoding="utf-8") as fh:
            existing = fh.read()
    pre, post = split_regions(existing)
    # 迁移判定按整行标记算：正文反引号提及标记串不算已迁移（同 split_regions 口径）。
    first_migration = bool(existing) and not any(
        l.strip() == GEN_START for l in existing.splitlines())

    body = ["# 机制索引（自动生成，勿手改）", "",
            f"规则 {len(rs)} 条（点名机检入口 {with_machine} 条），机制类文档 {len(docs)} 份。", "",
            "新建机制前先用关键词在本文件 grep：已有同类就扩写，不许另立第二套口径（事故：七层金字塔与 F-92 双轨）。", "",
            "## 一、规则全集（F-xx）", ""]
    for fid, text, mach in rs:
        tag = "机检" if mach else "人工"
        body.append(f"- {fid} [{tag}] {text[:120]}")
    body += ["", "## 二、机制类文档", ""]
    for f in docs:
        body.append(f"- `docs/{f}`")

    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(compose(pre, body, post, first_migration))
    print(f"已写 {OUT}（GEN 区间外人工节逐字保留）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
