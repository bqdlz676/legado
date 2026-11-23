#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 U9 字体子集生成脚本（切片-14 字体系统 / 切片-08 性能红线）。

【当前状态：子集未实际生成】
沙箱离线构建未拉取字体源文件，本脚本未在沙箱执行；assets/fonts/ 下当前没有任何打包
子集，运行时加载器探测不到资产即回落系统 serif / sans / mono，不崩、不出空白态。
本脚本供 CI 或本地后续跑：把 SIL OFL 等允许内嵌再分发的字体源文件裁成极小子集，
输出到本目录，文件名命中 manifest.json 中对应 candidates 即被自动识别。

规格红线：
  - 正文常用 2500 字首屏子集 ≤ 200KB（Noto Serif SC / 思源宋体 Regular）；
  - 标题 SemiBold 子集 ≤ 200KB；数字极小子集 ≤ 20KB（Inter Tabular Nums, numbers only）；
  - 注音小字 Noto Sans SC Light 10sp；等宽 JetBrains Mono 按需；
  - 仅允许 OFL 等明确允许内嵌再分发的字体；禁止 Wikipedia / OSM / CC-CEDICT 派生资产。

依赖：fonttools（pip install fonttools[woff]）；woff2 输出需 brotli。
用法示例：
  python subset_generate.py \
      --source-dir /path/to/noto-sans-serif-sc \
      --chars-file chars/common2500.txt \
      --out-dir .
不传 --chars-file 时，正文子集仅保留 ASCII 可打印字符与常用中文标点，用于管线自检；
真机读物所需汉字务必传入古籍常用字表（目标 2500 字量级）。
"""

from __future__ import annotations

import argparse
import os
import sys
from dataclasses import dataclass

# ---- 体积红线（与 app/src/main/java/io/legado/app/ui/font/FontRole.kt 对齐） ----
BODY_BUDGET_BYTES = 200 * 1024
NUMBER_BUDGET_BYTES = 20 * 1024

# 正文默认附带的 ASCII 可打印字符与常用中文标点（汉字来自 --chars-file）。
DEFAULT_EXTRA_TEXT = (
    " !\"#$%&'()*+,-./0123456789:;<=>?@[\\]^_`{|}~"
    "，。、；：？！“”‘’（）《》〈〉【】…—·「」『』％‰"
)


@dataclass
class SubsetJob:
    role: str
    source: str
    output: str
    budget_bytes: int | None
    chars_file: str | None
    text_override: str | None = None  # 数字子集只保留 0-9 与分隔符


JOBS = [
    SubsetJob(
        role="BODY_SERIF",
        source="NotoSerifSC-Regular.otf",
        output="NotoSerifSC-Regular.otf",
        budget_bytes=BODY_BUDGET_BYTES,
        chars_file="chars/common2500.txt",
    ),
    SubsetJob(
        role="TITLE_SERIF",
        source="NotoSerifSC-SemiBold.otf",
        output="NotoSerifSC-SemiBold.otf",
        budget_bytes=BODY_BUDGET_BYTES,
        chars_file="chars/common2500.txt",
    ),
    SubsetJob(
        role="LABEL_SANS",
        source="NotoSansSC-Light.otf",
        output="NotoSansSC-Light.otf",
        budget_bytes=None,
        chars_file="chars/common2500.txt",
    ),
    SubsetJob(
        role="NUMBER_TABULAR",
        source="Inter-TabularNumbers.otf",
        output="Inter-TabularNumbers.otf",
        budget_bytes=NUMBER_BUDGET_BYTES,
        chars_file=None,
        text_override="0123456789年月日年代号·-—/. ",
    ),
    SubsetJob(
        role="MONO",
        source="JetBrainsMono-Regular.otf",
        output="JetBrainsMono-Regular.otf",
        budget_bytes=None,
        chars_file=None,
        text_override="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_.#",
    ),
]


def load_chars(chars_file: str | None, extra: str) -> str:
    chars = set(extra)
    if chars_file and os.path.exists(chars_file):
        with open(chars_file, "r", encoding="utf-8") as fh:
            chars.update(ch for ch in fh.read() if not ch.isspace())
    return "".join(sorted(chars))


def run_pyftsubset(job: SubsetJob, source_dir: str, out_dir: str, chars_file: str | None) -> None:
    try:
        from fontTools import subset  # type: ignore
    except ImportError:
        sys.stderr.write(
            "[U9] 缺少 fonttools：pip install fonttools[woff] brotli；本次未生成子集。\n"
        )
        raise

    src_path = os.path.join(source_dir, job.source)
    if not os.path.exists(src_path):
        print(f"[U9] 跳过 {job.role}：源字体不存在 {src_path}（沙箱常态，子集未生成）")
        return

    out_path = os.path.join(out_dir, job.output)
    text = job.text_override or load_chars(chars_file or job.chars_file, DEFAULT_EXTRA_TEXT)

    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.recalc_bounds = True
    options.drop_tables += ["DSIG"]

    font = subset.load_font(src_path, options)
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(text=text)
    subsetter.subset(font)
    subset.save_font(font, out_path, options)

    size = os.path.getsize(out_path)
    budget = job.budget_bytes
    if budget is not None and size > budget:
        raise SystemExit(
            f"[U9] {job.role} 子集 {size}B 超出红线 {budget}B：{out_path}；"
            "请缩减字数或改用 woff2，不得超限打包。"
        )
    print(f"[U9] {job.role} -> {out_path} ({size}B, budget={budget})")


def main() -> None:
    parser = argparse.ArgumentParser(description="落文 U9 字体子集生成器（CI / 本地运行）")
    parser.add_argument("--source-dir", default=".", help="字体源文件所在目录")
    parser.add_argument("--chars-file", default=None, help="古籍常用字表（一行一字或纯文本）")
    parser.add_argument("--out-dir", default=os.path.dirname(os.path.abspath(__file__)))
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)
    for job in JOBS:
        run_pyftsubset(job, args.source_dir, args.out_dir, args.chars_file)

    print("[U9] 完成。请核对输出字体许可为 OFL 等允许内嵌再分发协议，并登记字库出处清单。")


if __name__ == "__main__":
    main()
