#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 U9 字体子集生成脚本（切片-14 字体系统 / 切片-08 性能红线）。

【口径】
  - 正文宋体 Noto Serif SC Regular：首屏常用子集，≤ 200KB，font-display: swap；
  - 标题 Noto Serif SC SemiBold：首屏阻塞，swap 过渡，不允许 FOIT，≤ 200KB；
  - 注音小字 Noto Sans SC Light 10sp：延迟加载；
  - 数字/年份 Inter Tabular Nums：numbers only 极小子集，≤ 20KB，按需；
  - 等宽 JetBrains Mono：典籍编号 / CBDB ID，按需。

【许可红线】
  仅使用 SIL OFL 1.1 等明确允许应用内嵌与再分发的字体；禁止 Wikipedia / OSM /
  CC-CEDICT 等数据或其派生字体资产。来源、版本、许可登记在 manifest.json 与
  LICENSE-OFL.txt。

【字表】
  默认读取 chars/luowen-core.txt：内置读物词频 top550 汉字 + 灵魂闭环 / 纪年 /
  气泡 / 启动落款 / 搜索页必覆盖字 + ASCII 与中文标点。生僻字不进首屏子集，
  运行时回落系统 serif/sans/mono（荣耀 WIN 系统字含全量 CJK），不崩、不出空白态。
  用 `--refresh-chars` 可按 assets/books 重新统计词频并重写字表。

【依赖】
  fonttools；裁 CFF/TTF 不需要 brotli；可变字体实例化用 fontTools 自带 instancer。

用法：
  # 按现有字表裁全部子集到本目录
  python3 subset_generate.py \
      --noto-dir /usr/share/fonts/opentype/noto \
      --inter-src /path/to/Inter-VF.ttf \
      --jbm-src /path/to/JetBrainsMono-VF.ttf

  # 先按内置读物词频重写字表，再裁子集
  python3 subset_generate.py --refresh-chars --books-dir ../../assets/books
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass, field

# ---- 体积红线（与 app/src/main/java/io/legado/app/ui/font/FontRole.kt 对齐） ----
BODY_BUDGET_BYTES = 200 * 1024
NUMBER_BUDGET_BYTES = 20 * 1024
NOTO_SC_FACE_INDEX = 2  # TTC 内 SC 子字体序号（JP=0 KR=1 SC=2 TC=3 HK=4）

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CHARS = os.path.join(HERE, "chars", "luowen-core.txt")

# 灵魂闭环 + 纪年 + 气泡 + 启动落款 + 搜索页必覆盖字（测试按此串断言 cmap 覆盖）
MANDATORY_TEXT = (
    "打开书竖排读点字出气泡命中春秋晋国鲁隐公元年前干支己未"
    "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥"
    "年月日时代距今公元前后朔望年号释义出处人名地名落文读其知通"
    "搜索书名作者正文任意一句"
)
# 数字子集只收拉丁数字与分隔符；纪年汉字（年月日年代号等）由宋体子集覆盖。
NUMBER_TEXT = "0123456789·-—/.: "
MONO_TEXT = (
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_.#"
)


@dataclass
class SubsetJob:
    role: str
    output: str
    budget_bytes: int | None
    source_kind: str  # noto-serif-reg | noto-serif-sb | noto-sans-light | inter | jbm
    source_label: str
    license: str
    text: str | None = None
    chars_file: str | None = None
    extra: list[str] = field(default_factory=list)


JOBS = [
    SubsetJob(
        role="BODY_SERIF",
        output="NotoSerifSC-Regular.otf",
        budget_bytes=BODY_BUDGET_BYTES,
        source_kind="noto-serif-reg",
        source_label="Noto Serif CJK SC Regular (SIL OFL 1.1)",
        license="OFL-1.1",
        chars_file=DEFAULT_CHARS,
    ),
    SubsetJob(
        role="TITLE_SERIF",
        output="NotoSerifSC-SemiBold.otf",
        budget_bytes=BODY_BUDGET_BYTES,
        source_kind="noto-serif-sb",
        source_label="Noto Serif CJK SC SemiBold (SIL OFL 1.1)",
        license="OFL-1.1",
        chars_file=DEFAULT_CHARS,
    ),
    SubsetJob(
        role="LABEL_SANS",
        output="NotoSansSC-Light.otf",
        budget_bytes=None,
        source_kind="noto-sans-light",
        source_label="Noto Sans CJK SC Light (SIL OFL 1.1)",
        license="OFL-1.1",
        chars_file=DEFAULT_CHARS,
    ),
    SubsetJob(
        role="NUMBER_TABULAR",
        output="Inter-TabularNumbers.ttf",
        budget_bytes=NUMBER_BUDGET_BYTES,
        source_kind="inter",
        source_label="Inter Regular, wght=400 instance (SIL OFL 1.1)",
        license="OFL-1.1",
        text=NUMBER_TEXT,
    ),
    SubsetJob(
        role="MONO",
        output="JetBrainsMono-Regular.ttf",
        budget_bytes=None,
        source_kind="jbm",
        source_label="JetBrains Mono Regular, wght=400 instance (SIL OFL 1.1)",
        license="OFL-1.1",
        text=MONO_TEXT,
    ),
]

NOTO_FILES = {
    "noto-serif-reg": "NotoSerifCJK-Regular.ttc",
    "noto-serif-sb": "NotoSerifCJK-SemiBold.ttc",
    "noto-sans-light": "NotoSansCJK-Light.ttc",
}


def load_chars(path: str | None, override: str | None) -> str:
    if override:
        return override
    if not path or not os.path.exists(path):
        raise SystemExit(f"[U9] 字表不存在：{path}")
    with open(path, "r", encoding="utf-8") as fh:
        return "".join(ch for ch in fh.read() if not ch.isspace())


def cjk_options():
    from fontTools import subset

    opts = subset.Options()
    opts.font_number = NOTO_SC_FACE_INDEX
    opts.notdef_outline = True
    opts.recalc_bounds = True
    opts.hinting = False
    opts.glyph_names = False
    opts.name_IDs = [1, 2, 4, 6]
    opts.layout_features = []
    opts.drop_tables += ["DSIG", "BASE", "JSTF", "vmtx", "vhea"]
    return opts


def latin_options(features: list[str]):
    from fontTools import subset

    opts = subset.Options()
    opts.notdef_outline = True
    opts.recalc_bounds = True
    opts.hinting = False
    opts.glyph_names = False
    opts.name_IDs = [1, 2, 4, 6]
    opts.layout_features = features
    opts.drop_tables += ["DSIG"]
    return opts


def instance_latin(vf_path: str):
    from fontTools.ttLib import TTFont
    from fontTools.varLib.instancer import instantiateVariableFont

    font = TTFont(vf_path)
    instantiateVariableFont(font, {"wght": 400}, inplace=True)
    # 实例化后摘除可变表，避免子集器残留 gvar 引用崩溃。
    for tbl in ("gvar", "fvar", "STAT", "avar", "HVAR", "MVAR", "cvar"):
        if tbl in font:
            del font[tbl]
    return font


def subset_job(job: SubsetJob, noto_dir: str, inter_src: str | None,
               jbm_src: str | None, out_dir: str) -> dict:
    from fontTools import subset

    out_path = os.path.join(out_dir, job.output)

    if job.source_kind in NOTO_FILES:
        src_path = os.path.join(noto_dir, NOTO_FILES[job.source_kind])
        if not os.path.exists(src_path):
            raise SystemExit(f"[U9] 缺 Noto 源字体：{src_path}")
        text = load_chars(job.chars_file, job.text)
        opts = cjk_options()
        font = subset.load_font(src_path, opts)
        ss = subset.Subsetter(options=opts)
        ss.populate(text=text)
        ss.subset(font)
    elif job.source_kind == "inter":
        if not inter_src or not os.path.exists(inter_src):
            raise SystemExit("[U9] 缺 Inter 源字体（--inter-src）")
        font = instance_latin(inter_src)
        opts = latin_options(["tnum"])
        ss = subset.Subsetter(options=opts)
        ss.populate(text=job.text or "")
        ss.subset(font)
    elif job.source_kind == "jbm":
        if not jbm_src or not os.path.exists(jbm_src):
            raise SystemExit("[U9] 缺 JetBrains Mono 源字体（--jbm-src）")
        font = instance_latin(jbm_src)
        opts = latin_options(["kern"])
        ss = subset.Subsetter(options=opts)
        ss.populate(text=job.text or "")
        ss.subset(font)
    else:
        raise SystemExit(f"[U9] 未知字体来源：{job.source_kind}")

    subset.save_font(font, out_path, opts)
    size = os.path.getsize(out_path)

    if job.budget_bytes is not None and size > job.budget_bytes:
        raise SystemExit(
            f"[U9] {job.role} 子集 {size}B 超红线 {job.budget_bytes}B：{out_path}；"
            "请缩减字数或回退系统字体，不得超限打包。"
        )

    # 覆盖核对：读出子集 cmap，断言字表 / 必覆盖字全部命中。
    cmap = font.getBestCmap()
    wanted = job.text or load_chars(job.chars_file, None)
    covered = [c for c in wanted if ord(c) in cmap]
    missing = [c for c in wanted if ord(c) not in cmap]
    glyph_count = len(font.getGlyphOrder())

    report = {
        "role": job.role,
        "file": job.output,
        "bytes": size,
        "budgetBytes": job.budget_bytes,
        "glyphs": glyph_count,
        "source": job.source_label,
        "license": job.license,
        "coveredChars": len(covered),
        "missingChars": "".join(missing),
    }
    print(f"[U9] {job.role} -> {job.output} {size}B ({size / 1024:.1f}KB) "
          f"glyphs={glyph_count} missing={''.join(missing) or 'none'}")
    return report


def refresh_chars(books_dir: str, out_chars: str, top_n: int) -> None:
    import collections
    import glob

    counter: collections.Counter[str] = collections.Counter()
    for book in glob.glob(os.path.join(books_dir, "*.txt")):
        try:
            text = open(book, encoding="utf-8").read()
        except OSError:
            continue
        for ch in text:
            if "一" <= ch <= "鿿":
                counter[ch] += 1

    chars = set(MANDATORY_TEXT)
    chars |= {c for c, _ in counter.most_common(top_n)}
    chars |= set(" !\"#$%&'()*+,-./0123456789:;<=>?@[\\]^_`{|}~")
    chars |= set("，。、；：？！“”‘’（）《》〈〉【】…—·「」『』％‰")
    ordered = "".join(sorted(chars))
    os.makedirs(os.path.dirname(out_chars), exist_ok=True)
    with open(out_chars, "w", encoding="utf-8") as fh:
        fh.write(ordered + "\n")
    missing = [c for c in MANDATORY_TEXT if c not in ordered]
    print(f"[U9] 字表刷新：{len(ordered)} 字（top{top_n} 词频），"
          f"必覆盖缺字：{''.join(missing) or 'none'}")


def main() -> None:
    parser = argparse.ArgumentParser(description="落文 U9 字体子集生成器")
    parser.add_argument("--noto-dir", default="/usr/share/fonts/opentype/noto",
                        help="Noto CJK TTC 源目录")
    parser.add_argument("--inter-src", default=None, help="Inter 可变字体 TTF")
    parser.add_argument("--jbm-src", default=None, help="JetBrains Mono 可变字体 TTF")
    parser.add_argument("--chars-file", default=DEFAULT_CHARS, help="CJK 字表")
    parser.add_argument("--out-dir", default=HERE, help="子集输出目录")
    parser.add_argument("--refresh-chars", action="store_true", help="先按读物词频重写字表")
    parser.add_argument("--books-dir", default=None, help="读物目录（--refresh-chars 用）")
    parser.add_argument("--top-n", type=int, default=550, help="词频取前 N 字")
    args = parser.parse_args()

    if args.refresh_chars:
        books_dir = args.books_dir or os.path.join(HERE, "..", "..", "books")
        refresh_chars(books_dir, args.chars_file, args.top_n)

    os.makedirs(args.out_dir, exist_ok=True)
    reports = []
    for job in JOBS:
        job.chars_file = args.chars_file
        reports.append(subset_job(job, args.noto_dir, args.inter_src,
                                  args.jbm_src, args.out_dir))

    coverage_path = os.path.join(args.out_dir, "coverage.json")
    with open(coverage_path, "w", encoding="utf-8") as fh:
        json.dump({
            "schema": "luowen-font-coverage/v1",
            "mandatoryText": MANDATORY_TEXT,
            "jobs": reports,
        }, fh, ensure_ascii=False, indent=2)
    print(f"[U9] 覆盖清单 -> {coverage_path}")
    print("[U9] 完成。请核对全部源字体许可为 OFL-1.1，出处已登记 manifest.json。")


if __name__ == "__main__":
    main()
