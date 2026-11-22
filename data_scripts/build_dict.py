#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 字典与词库数据生成脚本（0 人工 / 三源交叉 / 每条带 source + confidence）

三源（字头交叉打分）：
  kangxi     康熙字典：1716 公版原文 + 现代释义汇编
             （数据包 shunshi-kangxi-core，MIT；原典 PD）
  unihan     Unicode Unihan 官方 UCD
             （kMandarin / kHanyuPinyin / kDefinition / kFanqie /
              kRSUnicode / kTotalStrokes …）
  ctext-freq 古籍语料字频：内置公版十三经 / 诸子 / 楚辞等 corpus
             （ctext / kanripo 公版文本）

交叉打分（数据 Schema 契约 §三）：
  3/3 一致 → high    （直接收录）
  2/3 一致 → medium  （收录，标「待校」）
  1/3 或 0/3 → low   （收录，标「待考·来源冲突」，不进默认命中）

许可铁律（零 copyleft）：
  * 不使用 CC-CEDICT（CC BY-SA 传染）
  * 不使用 OSM（ODbL）
  * 中文词库 = 康熙 + Unihan + 既有 zh_ci 自合成
  * 英汉词库 = 既有 en_dict.bin 原样重打包

用法：
  python3 data_scripts/build_dict.py            # 全量重跑；源缺失时自动下载到 data_scripts/sources/
  python3 data_scripts/build_dict.py --offline  # 仅用本地缓存，不联网

产出（app/src/main/assets/）：
  char_dict.txt    字头 \\t 读音 \\t 部首 \\t 笔画 \\t 释义 \\t source \\t confidence
  char_pinyin.txt  字头 \\t 读音,读音… \\t source \\t confidence
  char_shuowen.txt 字 \\t 说文释文 \\t 反切 \\t 部首 \\t source \\t confidence
  char_usage.txt   字 \\t 出处 \\t 例句 \\t source \\t confidence
  zh_ci.bin        LDIC：词 \\t 释义 \\t source \\t confidence
  ci_prefix.txt    首字 \\t 词1,词2,…（2–3 字词，每桶 40 条）
  ci_pinyin.bin    LDIC：词 \\t 拼音 \\t source \\t confidence
  en_dict.bin      LDIC：词 \\t 音标 \\t 释义 \\t source \\t confidence
"""

from __future__ import annotations

import argparse
import collections
import gzip
import json
import os
import re
import struct
import sys
import tarfile
import unicodedata
import urllib.request
import zipfile

# --------------------------------------------------------------------------- #
# 路径
# --------------------------------------------------------------------------- #

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(SCRIPT_DIR)
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
CACHE = os.path.join(SCRIPT_DIR, "sources")

UNIHAN_URL = "https://www.unicode.org/Public/UCD/latest/ucd/Unihan.zip"
KANGXI_URL = "https://registry.npmjs.org/shunshi-kangxi-core/-/shunshi-kangxi-core-0.1.1.tgz"

UNIHAN_ZIP = os.path.join(CACHE, "Unihan.zip")
KANGXI_TGZ = os.path.join(CACHE, "kangxi-core.tgz")

# 输出文件（本模块独占域）
F_CHAR_DICT = os.path.join(ASSETS, "char_dict.txt")
F_CHAR_PINYIN = os.path.join(ASSETS, "char_pinyin.txt")
F_CHAR_SHUOWEN = os.path.join(ASSETS, "char_shuowen.txt")
F_CHAR_USAGE = os.path.join(ASSETS, "char_usage.txt")
F_ZH_CI = os.path.join(ASSETS, "zh_ci.bin")
F_CI_PREFIX = os.path.join(ASSETS, "ci_prefix.txt")
F_CI_PINYIN = os.path.join(ASSETS, "ci_pinyin.bin")
F_EN_DICT = os.path.join(ASSETS, "en_dict.bin")

CONFIDENCE = ("high", "medium", "low")

CJK_RE = re.compile(
    r"[㐀-䶿一-鿿豈-﫿𠀀-𪛖𪜀-𫜿𫝀-𫠿𬀀-𬯀𰀀-𱅒]"
)
SENTENCE_RE = re.compile(r"[^。！？!?\n\r]{2,40}")


# --------------------------------------------------------------------------- #
# 工具
# --------------------------------------------------------------------------- #

def ideograph(ch: str) -> bool:
    """是否为 CJK 表意文字（含扩展 A–F / 兼容表意文字）。"""
    if len(ch) != 1:
        return False
    cp = ord(ch)
    return (
        0x3400 <= cp <= 0x4DBF
        or 0x4E00 <= cp <= 0x9FFF
        or 0xF900 <= cp <= 0xFAFF
        or 0x20000 <= cp <= 0x2A6DF
        or 0x2A700 <= cp <= 0x2B73F
        or 0x2B740 <= cp <= 0x2B81F
        or 0x2B820 <= cp <= 0x2CEAF
        or 0x30000 <= cp <= 0x3134F
    )


def fetch(url: str, dest: str, offline: bool) -> bool:
    """下载到缓存；已存在或离线失败时返回 False（调用方按缺源兜底）。"""
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return True
    if offline:
        return False
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "luowen-build/1.0"})
        with urllib.request.urlopen(req, timeout=120) as resp, open(dest, "wb") as fh:
            fh.write(resp.read())
        return True
    except Exception as exc:  # 网络边界：不崩，缺源走兜底
        print(f"[warn] 下载失败 {url}: {exc}", file=sys.stderr)
        return False


def clean_text(text: str) -> str:
    """释义 / 例句清洗：压缩空白，保留 \\u0001 作为运行时换行占位；
    归一化所有异型换行 / 控制符（\\f \\v NEL 文件分隔符等），避免破坏列结构。"""
    if not text:
        return ""
    text = text.replace("\r", " ").replace("\n", "\u0001")
    text = (text.replace("\f", " ").replace("\v", " ").replace("\x85", " ")
                .replace("\x1c", " ").replace("\x1d", " ").replace("\x1e", " "))
    text = "".join(
        ch if ch == "\u0001" or ord(ch) >= 32 else " " for ch in text
    )
    text = re.sub(r"[ \t　]+", " ", text)
    return text.strip(" \t　\u0001")


def dedupe(seq):
    """保序去空去重（NFC 归一化，消除同读音的码位变体重复）。"""
    seen, out = set(), []
    for item in seq:
        item = unicodedata.normalize("NFC", (item or "").strip())
        if item and item not in seen:
            seen.add(item)
            out.append(item)
    return out


# --------------------------------------------------------------------------- #
# 源 1：Unihan（官方 UCD）
# --------------------------------------------------------------------------- #

READING_PROPS = {
    "kMandarin", "kHanyuPinyin", "kFanqie", "kDefinition",
    "kCantonese", "kXHC1983", "kTGHZ2013", "kSMSZD2003Readings", "kTang",
}


def load_unihan(path: str):
    """返回 { char: {prop: value} } 与有读音/释义的字头集合。"""
    props: dict[str, dict[str, str]] = {}
    if not os.path.exists(path):
        return props, set()
    try:
        with zipfile.ZipFile(path) as zf:
            names = ("Unihan_Readings.txt", "Unihan_IRGSources.txt")
            for name in names:
                if name not in zf.namelist():
                    continue
                for line in zf.read(name).decode("utf-8", "replace").splitlines():
                    if not line or line.startswith("#"):
                        continue
                    parts = line.split("\t")
                    if len(parts) < 3:
                        continue
                    ch = chr(int(parts[0][2:], 16))
                    props.setdefault(ch, {})[parts[1]] = parts[2]
    except Exception as exc:
        print(f"[warn] Unihan 解析失败: {exc}", file=sys.stderr)
        return {}, set()

    readable = {
        ch for ch, val in props.items()
        if any(k in val for k in READING_PROPS) and ideograph(ch)
    }
    return props, readable


def unihan_readings(val: dict[str, str]) -> list[str]:
    """普通话读音：kMandarin 优先，kHanyuPinyin 兜底。"""
    out: list[str] = []
    if "kMandarin" in val:
        out.extend(val["kMandarin"].split())
    if "kHanyuPinyin" in val:
        for entry in val["kHanyuPinyin"].split(";"):
            _, _, reading = entry.partition(":")
            for r in reading.split(","):  # 条目内读音亦可能逗号分隔
                if r:
                    out.append(r)
    return dedupe(out)


def unihan_radical_strokes(val: dict[str, str], radical_table: list[str]):
    """kRSUnicode → (部首字, 残余笔画)；kTotalStrokes → 总笔画。"""
    radical, residual = "", ""
    rs = val.get("kRSUnicode", "")
    if rs:
        first = rs.split()[0]
        num, _, rest = first.partition(".")
        if num.isdigit():
            idx = int(num) - 1
            if 0 <= idx < len(radical_table):
                radical = radical_table[idx]
            residual = rest.strip("'’")
    strokes = val.get("kTotalStrokes", "")
    if strokes:
        strokes = strokes.split()[0]
    return radical, residual, strokes


# --------------------------------------------------------------------------- #
# 源 2：康熙字典（MIT 数据包，原典 PD）
# --------------------------------------------------------------------------- #

def load_kangxi(path: str):
    """返回 chars / defs / ktext 三张表与字头集合。"""
    chars: dict[str, dict] = {}
    defs: dict[str, str] = {}
    ktext: dict[str, str] = {}
    if not os.path.exists(path):
        return chars, defs, ktext, set()
    try:
        with tarfile.open(path) as tf:
            def pkg(name: str) -> dict:
                fh = tf.extractfile(f"package/data/{name}.json.gz")
                if fh is None:
                    return {}
                return json.loads(gzip.decompress(fh.read()).decode("utf-8"))

            chars = pkg("chars").get("chars", {})
            defs = pkg("defs")
            ktext = pkg("kangxi-text")
    except Exception as exc:
        print(f"[warn] 康熙数据包解析失败: {exc}", file=sys.stderr)
        return {}, {}, {}, set()
    heads = set(chars) | set(defs) | set(ktext)
    return chars, defs, ktext, heads


# --------------------------------------------------------------------------- #
# 源 3：古籍语料字频（公版 corpus）
# --------------------------------------------------------------------------- #

def load_corpus(books_dir: str):
    """字频表 + 每字首见例句（出处, 例句）。"""
    freq: collections.Counter[str] = collections.Counter()
    sentences: dict[str, list[str]] = {}
    if not os.path.isdir(books_dir):
        return freq, sentences
    for fname in sorted(os.listdir(books_dir)):
        if not fname.endswith(".txt"):
            continue
        book = fname[:-4]
        text = open(os.path.join(books_dir, fname), encoding="utf-8", errors="ignore").read()
        freq.update(CJK_RE.findall(text))
        for sent in SENTENCE_RE.findall(text):
            hits = set(CJK_RE.findall(sent))
            for ch in hits:
                if ch not in sentences:
                    sentences[ch] = [book, sent.strip()]
    return freq, sentences


# --------------------------------------------------------------------------- #
# 既有资产（legacy 种子；兼容旧五列与新七列格式）
# --------------------------------------------------------------------------- #

def _split_meta(line: str):
    """剥离行尾 source / confidence 元信息，返回 (字段列表, source, confidence)。"""
    parts = line.rstrip("\n").split("\t")
    source, confidence = "", ""
    if parts and parts[-1] in CONFIDENCE:
        confidence = parts.pop()
        if parts:
            source = parts.pop()
    return parts, source, confidence


def load_legacy_dict() -> dict[str, dict]:
    out: dict[str, dict] = {}
    if not os.path.exists(F_CHAR_DICT):
        return out
    for line in open(F_CHAR_DICT, encoding="utf-8"):
        parts, _, _ = _split_meta(line)
        if len(parts) < 5:
            continue
        out[parts[0]] = {
            "pinyin": parts[1], "radical": parts[2],
            "strokes": parts[3], "def": parts[4],
        }
    return out


def load_legacy_pinyin() -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    if not os.path.exists(F_CHAR_PINYIN):
        return out
    for line in open(F_CHAR_PINYIN, encoding="utf-8"):
        parts, _, _ = _split_meta(line)
        if len(parts) >= 2:
            out[parts[0]] = [x.strip() for x in parts[1].split(",") if x.strip()]
    return out


def load_legacy_shuowen() -> dict[str, dict]:
    out: dict[str, dict] = {}
    if not os.path.exists(F_CHAR_SHUOWEN):
        return out
    for line in open(F_CHAR_SHUOWEN, encoding="utf-8"):
        parts, _, _ = _split_meta(line)
        if len(parts) < 2:
            continue
        out[parts[0]] = {
            "text": parts[1],
            "fanqie": parts[2] if len(parts) > 2 else "",
            "radical": parts[3] if len(parts) > 3 else "",
        }
    return out


def load_legacy_usage() -> dict[str, dict]:
    out: dict[str, dict] = {}
    if not os.path.exists(F_CHAR_USAGE):
        return out
    for line in open(F_CHAR_USAGE, encoding="utf-8"):
        parts, _, _ = _split_meta(line)
        if len(parts) >= 3:
            out[parts[0]] = {"source": parts[1], "sentence": parts[2]}
    return out


def read_ldic(path: str) -> dict[str, str]:
    """解码 CompactDict LDIC 二进制 → { key: rest }。"""
    out: dict[str, str] = {}
    if not os.path.exists(path):
        return out
    blob = open(path, "rb").read()
    if len(blob) < 16 or blob[:4] != b"LDIC":
        return out
    count, idx_off, data_off = struct.unpack(">iii", blob[4:16])
    for i in range(count):
        rel = struct.unpack(">i", blob[idx_off + i * 4: idx_off + i * 4 + 4])[0]
        off = data_off + rel
        end = blob.find(b"\n", off)
        if end < 0:
            end = len(blob)
        rec = blob[off:end].decode("utf-8", "replace")
        key, _, rest = rec.partition("\t")
        out[key] = rest
    return out


def write_ldic(path: str, records: list[tuple[str, str]]) -> int:
    """按 UTF-16BE 码位序写 LDIC（与 Java compareTo / 二分一致）。"""
    records = [(k, r) for k, r in records if k]
    records.sort(key=lambda r: r[0].encode("utf-16-be"))
    data = bytearray()
    index: list[int] = []
    for key, rest in records:
        index.append(len(data))
        data += f"{key}\t{rest}\n".encode("utf-8")
    count = len(records)
    data_off = 16 + count * 4
    buf = bytearray(b"LDIC")
    buf += struct.pack(">iii", count, 16, data_off)
    for off in index:
        buf += struct.pack(">i", off)
    buf += data
    with open(path, "wb") as fh:
        fh.write(buf)
    return count


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #

def build(offline: bool) -> dict:
    os.makedirs(CACHE, exist_ok=True)
    fetch(UNIHAN_URL, UNIHAN_ZIP, offline)
    fetch(KANGXI_URL, KANGXI_TGZ, offline)

    # 214 康熙部首表：直接由 Unicode 康熙部首区块 NFKC 规范化得到，避免手写错漏
    radical_table = [
        unicodedata.normalize("NFKC", chr(cp))
        for cp in range(0x2F00, 0x2FD6)
        if "KANGXI RADICAL" in unicodedata.name(chr(cp), "")
    ]

    # 三源载入
    unihan, uh_readable = load_unihan(UNIHAN_ZIP)
    kx_chars, kx_defs, kx_text, kx_heads = load_kangxi(KANGXI_TGZ)
    freq, corpus_sentences = load_corpus(os.path.join(ASSETS, "books"))
    corpus_heads = set(freq)

    # legacy 种子载入
    leg_dict = load_legacy_dict()
    leg_py = load_legacy_pinyin()
    leg_shuowen = load_legacy_shuowen()
    leg_usage = load_legacy_usage()
    leg_zh_ci = read_ldic(F_ZH_CI)
    leg_ci_py = read_ldic(F_CI_PINYIN)
    leg_en = read_ldic(F_EN_DICT)

    evidence_sources = (("kangxi", kx_heads), ("unihan", uh_readable),
                        ("ctext-freq", corpus_heads))

    # 字头并集：legacy 种子原样保留（含占位字形 / 假名等旧表字头）；
    # 三源新增字头限定 CJK 表意文字。
    legacy_heads = set(leg_dict) | set(leg_py) | set(leg_shuowen) | set(leg_usage)
    heads = legacy_heads | {
        ch for ch in (kx_heads | uh_readable | corpus_heads) if ideograph(ch)
    }

    merged: dict[str, dict] = {}
    stats = collections.Counter()

    for ch in sorted(heads, key=lambda c: c.encode("utf-16-be")):
        uh = unihan.get(ch, {})
        kx = kx_chars.get(ch, {})
        leg = leg_dict.get(ch, {})

        present = [name for name, src in evidence_sources if ch in src]
        n = len(present)
        confidence = "high" if n == 3 else "medium" if n == 2 else "low"
        source = ";".join(present) if present else "legacy-seed"
        stats[confidence] += 1

        # 读音：legacy → 康熙 → Unihan
        readings = dedupe(
            (leg.get("pinyin", "").split() if leg.get("pinyin") else [])
            + ([kx.get("py", "")] if kx.get("py") else [])
            + unihan_readings(uh)
            + leg_py.get(ch, [])
        )
        # 部首：legacy → 康熙 → Unihan
        rad_u, _, strokes_u = unihan_radical_strokes(uh, radical_table)
        radical = leg.get("radical", "") or kx.get("rad", "") or rad_u
        # 笔画：legacy → 康熙总笔画 → Unihan
        strokes = leg.get("strokes", "") or str(kx.get("ts") or kx.get("kx") or "") or strokes_u
        # 释义：legacy → 康熙现代释义 → 康熙原文 → Unihan 英文释义
        definition = (
            leg.get("def", "")
            or clean_text(kx_defs.get(ch, ""))
            or clean_text(kx_text.get(ch, ""))
            or uh.get("kDefinition", "")
        )

        merged[ch] = {
            "readings": readings,
            "radical": radical,
            "strokes": strokes,
            "definition": clean_text(definition),
            "source": source,
            "confidence": confidence,
            "fanqie": uh.get("kFanqie", ""),
        }

    # ---- 1. char_dict.txt ------------------------------------------------ #
    dict_rows = 0
    with open(F_CHAR_DICT, "w", encoding="utf-8") as fh:
        for ch, m in merged.items():
            py = " ".join(m["readings"][:1]) if m["readings"] else ""
            # 仅有说文 / 用例种子、无读音与释义的字头不进正字典
            if not py and not m["definition"] and not m["radical"] and not m["strokes"]:
                continue
            fh.write("\t".join([
                ch, py, m["radical"], m["strokes"], m["definition"],
                m["source"], m["confidence"],
            ]) + "\n")
            dict_rows += 1

    # ---- 2. char_pinyin.txt ---------------------------------------------- #
    with open(F_CHAR_PINYIN, "w", encoding="utf-8") as fh:
        for ch, m in merged.items():
            if not m["readings"]:
                continue
            fh.write("\t".join([
                ch, ",".join(m["readings"]), m["source"], m["confidence"],
            ]) + "\n")

    # ---- 3. char_shuowen.txt --------------------------------------------- #
    # 只收有说文释文的字头；Unihan kFanqie 仅用于补缺反切，不生成空释文记录。
    with open(F_CHAR_SHUOWEN, "w", encoding="utf-8") as fh:
        for ch, m in merged.items():
            leg = leg_shuowen.get(ch)
            text = leg["text"] if leg else ""
            if not text:
                continue
            fanqie = leg["fanqie"] if leg else m["fanqie"]
            radical = leg["radical"] if leg else m["radical"]
            src_parts = ["shuowen-legacy"] if leg else []
            if m["fanqie"]:
                src_parts.append("unihan-fanqie")
            src_parts.append(m["source"])
            fh.write("\t".join([
                ch, clean_text(text), fanqie, radical,
                ";".join(dedupe(src_parts)), m["confidence"],
            ]) + "\n")

    # ---- 4. char_usage.txt ------------------------------------------------ #
    with open(F_CHAR_USAGE, "w", encoding="utf-8") as fh:
        for ch, m in merged.items():
            leg = leg_usage.get(ch)
            if leg:
                book, sentence = leg["source"], leg["sentence"]
                src = "usage-legacy"
            elif ch in corpus_sentences:
                book, sentence = corpus_sentences[ch]
                src = "ctext-corpus"
            else:
                continue
            fh.write("\t".join([
                ch, book, clean_text(sentence),
                f"{src};{m['source']}", m["confidence"],
            ]) + "\n")

    # ---- 5. zh_ci.bin（legacy 种子 + 语料共现自合成） --------------------- #
    zh_records: list[tuple[str, str]] = []
    synthetic: list[tuple[str, int]] = []
    bigrams = collections.Counter()
    for book in sorted(os.listdir(os.path.join(ASSETS, "books"))):
        if not book.endswith(".txt"):
            continue
        text = open(os.path.join(ASSETS, "books", book), encoding="utf-8", errors="ignore").read()
        run = "".join(CJK_RE.findall(text))
        for i in range(len(run) - 1):
            bigrams[run[i:i + 2]] += 1

    existing_words = set(leg_zh_ci)
    # 后置虚词 / 数词碎片：X-之、X-不 这类共现不成词，不入自合成
    stop_tail = set("之乎者也而以于與与其則则不一三二曰焉哉夫蓋故斯吾余汝爾云为爲")
    for word, cnt in bigrams.most_common(30000):
        if (cnt < 5 or word[0] == word[1] or word in existing_words
                or word[1] in stop_tail):
            continue
        if word[0] in merged and word[1] in merged:
            synthetic.append((word, cnt))
            existing_words.add(word)

    for word, rest in leg_zh_ci.items():
        # 保留旧运行已写入的 source / confidence；无元信息的旧种子标 medium
        parts = rest.split("\t")
        meaning = parts[0]
        if len(parts) >= 3 and parts[-1] in CONFIDENCE:
            source, confidence = parts[-2], parts[-1]
        else:
            source, confidence = "zh_ci-legacy", "medium"
        zh_records.append((word, f"{meaning}\t{source}\t{confidence}"))
    for word, _ in synthetic:
        zh_records.append((
            word,
            f"〔自合成〕古籍共现词，待校\tkangxi;unihan;ctext-freq\tlow",
        ))
    write_ldic(F_ZH_CI, zh_records)

    # ---- 6. ci_prefix.txt（2–3 字词，每桶 40） ---------------------------- #
    buckets: dict[str, list[str]] = collections.defaultdict(list)
    seen_in_bucket: dict[str, set[str]] = collections.defaultdict(set)
    for word, _ in zh_records:
        if 2 <= len(word) <= 3 and ideograph(word[0]):
            bucket = buckets[word[0]]
            if word not in seen_in_bucket[word[0]]:
                seen_in_bucket[word[0]].add(word)
                bucket.append(word)
    with open(F_CI_PREFIX, "w", encoding="utf-8") as fh:
        for ch in sorted(buckets, key=lambda c: c.encode("utf-16-be")):
            words = buckets[ch][:40]
            fh.write(f"{ch}\t{','.join(words)}\n")

    # ---- 7. ci_pinyin.bin ------------------------------------------------- #
    # 词级读音表只收既有权威词级读音（治多音字在词组里的定读）；
    # 缺词级读音的词由 pinyinOfWord 逐字取首音兜底，不在此重复造条目占内存。
    py_records: list[tuple[str, str]] = []
    for word, legacy in leg_ci_py.items():
        lparts = legacy.split("\t") if legacy else []
        legacy_py = lparts[0] if lparts else ""
        if not legacy_py:
            continue
        if len(lparts) >= 3 and lparts[-1] in CONFIDENCE:
            source, confidence = lparts[-2], lparts[-1]
        else:
            source, confidence = "zh_ci-legacy", "medium"
        py_records.append((word, f"{legacy_py}\t{source}\t{confidence}"))
    write_ldic(F_CI_PINYIN, py_records)

    # ---- 8. en_dict.bin（既有英汉词库原样重打包） ------------------------- #
    en_records: list[tuple[str, str]] = []
    for word, rest in leg_en.items():
        parts = rest.split("\t")
        source, confidence = "en_dict-legacy", "medium"
        if parts and parts[-1] in CONFIDENCE:
            confidence = parts.pop()
        if len(parts) >= 2 and parts[-1].startswith("en_dict"):
            source = parts.pop()
        phonetic = parts[0] if parts else ""
        translation = parts[1] if len(parts) > 1 else ""
        en_records.append((word, f"{phonetic}\t{translation}\t{source}\t{confidence}"))
    write_ldic(F_EN_DICT, en_records)

    return {
        "heads": dict_rows,
        "merged_heads": len(merged),
        "confidence": dict(stats),
        "zh_ci": len(zh_records),
        "synthetic": len(synthetic),
        "ci_prefix": len(buckets),
        "ci_pinyin": len(py_records),
        "en_dict": len(en_records),
        "shuowen": sum(1 for _ in open(F_CHAR_SHUOWEN, encoding="utf-8")),
        "usage": sum(1 for _ in open(F_CHAR_USAGE, encoding="utf-8")),
    }


def self_check(report: dict) -> list[str]:
    """交付前自检：返回失败项列表（空 = 全过）。"""
    failures: list[str] = []

    # 字头下限 4.7 万
    if report["heads"] < 47000:
        failures.append(f"字头数 {report['heads']} < 47000")

    # 每条记录带 source + confidence；置信度分级合法
    for path, min_cols in (
        (F_CHAR_DICT, 7), (F_CHAR_PINYIN, 4),
        (F_CHAR_SHUOWEN, 6), (F_CHAR_USAGE, 5),
    ):
        for i, line in enumerate(open(path, encoding="utf-8")):
            parts = line.rstrip("\n").split("\t")
            if len(parts) < min_cols:
                failures.append(f"{os.path.basename(path)}:{i + 1} 列数不足 {min_cols}")
                break
            if parts[-1] not in CONFIDENCE:
                failures.append(f"{os.path.basename(path)}:{i + 1} confidence 非法: {parts[-1]}")
                break
            if not parts[-2]:
                failures.append(f"{os.path.basename(path)}:{i + 1} source 为空")
                break

    # LDIC 魔数与计数一致
    for path in (F_ZH_CI, F_CI_PINYIN, F_EN_DICT):
        blob = open(path, "rb").read()
        if blob[:4] != b"LDIC":
            failures.append(f"{os.path.basename(path)} 魔数错误")
            continue
        count, _, _ = struct.unpack(">iii", blob[4:16])
        if count <= 0:
            failures.append(f"{os.path.basename(path)} 计数异常 {count}")

    # 许可铁律：source 标签不得出现 CC-CEDICT / OSM（只查来源列，不误伤释义正文）
    forbidden = ("cedict", "openstreetmap", "osm")
    for path in (F_CHAR_DICT, F_CHAR_PINYIN, F_CHAR_SHUOWEN, F_CHAR_USAGE,
                 F_ZH_CI, F_CI_PINYIN, F_EN_DICT):
        if not os.path.exists(path):
            continue
        if path.endswith(".bin"):
            records = read_ldic(path)
            sources = [rest.split("\t")[-2] for rest in records.values()]
        else:
            sources = []
            for line in open(path, encoding="utf-8"):
                parts = line.rstrip("\n").split("\t")
                if len(parts) >= 2:
                    sources.append(parts[-2])
        for label in sources:
            for tag in forbidden:
                if tag in label.lower():
                    failures.append(f"{os.path.basename(path)} 出现禁用来源 {tag}")

    # 三源分级落地
    dist = report["confidence"]
    if not (dist.get("high") and dist.get("medium") and dist.get("low")):
        failures.append(f"置信度分级不完整: {dist}")

    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description="落文字典词库生成脚本")
    parser.add_argument("--offline", action="store_true", help="仅用本地缓存，不联网")
    args = parser.parse_args()

    report = build(args.offline)

    print("=== 落文字典词库生成报告 ===")
    print(f"字头总数        : {report['heads']}")
    print(f"  high (3/3)    : {report['confidence'].get('high', 0)}")
    print(f"  medium (2/3)  : {report['confidence'].get('medium', 0)}")
    print(f"  low (≤1/3)    : {report['confidence'].get('low', 0)}")
    print(f"说文条目        : {report['shuowen']}")
    print(f"用例条目        : {report['usage']}")
    print(f"中文词库        : {report['zh_ci']}（自合成 {report['synthetic']}）")
    print(f"首字索引桶      : {report['ci_prefix']}")
    print(f"词级拼音        : {report['ci_pinyin']}")
    print(f"英汉词库        : {report['en_dict']}")

    failures = self_check(report)
    if failures:
        print("\n=== 自检未通过 ===")
        for item in failures:
            print(f"  ✗ {item}")
        return 1
    print("\n=== 自检全部通过 ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())
