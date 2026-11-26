#!/usr/bin/env python3
"""机制索引（事故"机制双轨"的产物，2026-11-26）。

事故：我先写了一份"七层金字塔"，写完才发现仓库里早有 F-92 组合验收金字塔五层
+ 铁律 #46 组装层级 + 预存三件套。同一件事两套口径 = 双轨，等于我自己制造缝合怪。
根因：机制散落在 244 条 F-xx 规则与十余份 机制-*.md 里，没有一张能查的表，新建机制前
没人会先去翻。

对策：把规则全集里的每条 F-xx 与所有 机制-*.md 抽成索引，新建机制前 grep 关键词即可查重。
本脚本只读，不改任何既有文档。

用法：python3 data_scripts/mechanism-index.py [--write]
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.path.join(ROOT, "docs", "规则全集.md")
DOCS = os.path.join(ROOT, "docs")
OUT = os.path.join(DOCS, "索引-机制.md")


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


def main():
    rs = rules()
    docs = mechanism_docs()
    with_machine = sum(1 for _, _, h in rs if h)
    print(f"规则 {len(rs)} 条，其中点名机检入口的 {with_machine} 条；机制类文档 {len(docs)} 份")

    if "--write" not in sys.argv:
        print("（未落盘；加 --write 生成 docs/索引-机制.md）")
        return 0

    lines = ["锚点卡：docs/锚点/S-02-气泡协议.md", "",
             "# 机制索引（自动生成，勿手改）", "",
             f"规则 {len(rs)} 条（点名机检入口 {with_machine} 条），机制类文档 {len(docs)} 份。", "",
             "新建机制前先用关键词在本文件 grep：已有同类就扩写，不许另立第二套口径（事故：七层金字塔与 F-92 双轨）。", "",
             "## 一、规则全集（F-xx）", ""]
    for fid, text, mach in rs:
        tag = "机检" if mach else "人工"
        lines.append(f"- {fid} [{tag}] {text[:120]}")
    lines += ["", "## 二、机制类文档", ""]
    for f in docs:
        lines.append(f"- `docs/{f}`")
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print(f"已写 {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
