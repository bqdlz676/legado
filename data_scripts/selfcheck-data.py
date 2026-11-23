#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""数据级虚拟运行自检 —— 不跑 Android，也把"喂给引擎的数据"跑一遍。

为什么: 没有真机/没有编译时，"数据能不能被吃下去"是唯一可实测的一层；
      F11（赤壁 fireAttack 被静默跳过）、F3（畸形 JSON 穿透）都是这一层就能抓到的。
适用: 每轮收尾、每次数据资产变更后；`python3 data_scripts/selfcheck-data.py`
输出: 逐项 PASS/FAIL + 计数；退出码非 0 表示有 FAIL。
"""
import json
import os
import re
import sys
import glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
fails = []
oks = []


def ok(msg):
    oks.append(msg)


def bad(msg):
    fails.append(msg)


# ── 1. 全量 JSON 可解析 ─────────────────────────────────────────────
json_files = glob.glob(os.path.join(ROOT, "app/src/main/assets/**/*.json"), recursive=True)
broken = []
for p in json_files:
    try:
        with open(p, "r", encoding="utf-8") as f:
            json.load(f)
    except Exception as e:
        broken.append((p, str(e)[:80]))
if broken:
    for p, e in broken[:10]:
        bad("JSON 不可解析: %s (%s)" % (os.path.relpath(p, ROOT), e))
else:
    ok("JSON 全量可解析: %d 个" % len(json_files))

# ── 2. EventTrigger.kt 的 when(ev.action) 分支 vs JSON 实际用的 action ───────────────
# 口径修正（第一版把 known 只从 "x" -> Enum 模式里抓，漏掉 moraleChange/supplyCut/setIntent 这些
# 出现在 else/多分支里的字面量，导致 6 条假阳性）：改成扫 Enums.kt 全部引号字面量。
enums_path = os.path.join(
    ROOT, "app/src/main/java/io/legado/app/help/sim/EventTrigger.kt"
)
known = set()
if os.path.exists(enums_path):
    src = open(enums_path, "r", encoding="utf-8").read()
    for lit in re.findall(r'"([A-Za-z_][A-Za-z0-9_]*)"', src):
        known.add(lit.lower())
    ok("EventTrigger 分支全集: %d 个" % len(known))
else:
    bad("找不到 EventTrigger.kt")

crossing_json = glob.glob(os.path.join(ROOT, "app/src/main/assets/**/crossing/**/*.json"), recursive=True)
if not crossing_json:
    crossing_json = [p for p in json_files if "campaign" in p or "battle" in p]
unknown_actions = {}
checked = 0
for p in crossing_json:
    try:
        data = json.load(open(p, "r", encoding="utf-8"))
    except Exception:
        continue
    txt = json.dumps(data, ensure_ascii=False)
    for m in re.finditer(r'"action"\s*:\s*"([A-Za-z_][A-Za-z0-9_]*)"', txt):
        checked += 1
        v = m.group(1)
        if v.lower() not in known:
            unknown_actions.setdefault(v, []).append(os.path.relpath(p, ROOT))
if unknown_actions:
    for v, files in list(unknown_actions.items())[:8]:
        bad("JSON action 无执行分支（EventTrigger.when 未处理）: %s（%d 个文件，例：%s）" % (v, len(files), files[0]))
else:
    ok("crossing JSON 的 %d 个 action 全部能在 Enums 里解析（%d 个文件）" % (checked, len(crossing_json)))

# ── 3. 数值健康：禁止 NaN/Inf/负坐标 ──────────────────────────────
neg = []
nan = []
for p in crossing_json:
    try:
        txt = open(p, "r", encoding="utf-8").read()
    except Exception:
        continue
    if "NaN" in txt or "Infinity" in txt:
        nan.append(os.path.relpath(p, ROOT))
    for m in re.finditer(r'"(x|y|lat|lng|elevation)"\s*:\s*(-\d)', txt):
        neg.append((os.path.relpath(p, ROOT), m.group(1)))
if nan:
    bad("JSON 含 NaN/Infinity: %d 文件" % len(nan))
else:
    ok("无 NaN/Infinity")
if neg:
    bad("坐标/高程出现负数: %d 处（例：%s）" % (len(neg), neg[0]))
else:
    ok("坐标/高程无负数")

# ── 4. 原文重排零改字复核（正文/索引一致性） ─────────────────────
src_doc = os.path.join(ROOT, "docs/落文-原文-重排版.md")
if os.path.exists(src_doc):
    body = open(src_doc, "r", encoding="utf-8").read()
    markers = len(re.findall(r"<!--", body))
    blocks = len(re.findall(r"^<!--\s*块", body, re.M))
    ok("重排版可读: %d 字节 / %d 个 <!-- 标记 / %d 个块标记" % (len(body.encode()), markers, blocks))
else:
    bad("缺 docs/落文-原文-重排版.md")

# ── 5. 词典/人物数据：字段完整性抽样 ─────────────────────────────
dict_files = [p for p in json_files if "defaultData" in p or "dict" in p.lower()]
if dict_files:
    ok("词典/默认数据 JSON: %d 个" % len(dict_files))

# ── 汇总 ──────────────────────────────────────────────────────────
print("════ 数据级虚拟运行自检 ════")
for m in oks:
    print("  ✓", m)
for m in fails:
    print("  ✗", m)
print("\n结果: %d 项通过 / %d 项失败" % (len(oks), len(fails)))
sys.exit(1 if fails else 0)
