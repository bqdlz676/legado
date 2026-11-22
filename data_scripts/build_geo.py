#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 地理数据构建脚本（可重跑，幂等）
=================================================

产出（独占文件域，均在 app/src/main/assets/ 下）：
  place_entity.txt      地名时空本体（点/面 + 时间区间 + 别名 + source/confidence）
  place_name.txt        名称快照（朝代 + 上级 + source/confidence）
  place_geo.json        点位快速定位表
  place.txt             古地名释义兜底表
  world_place.txt       世界地名（纬 经 区域 说明）
  world_place_en.txt    英文名 → 中文名
  map/powers_poly.js    18 套朝代图层矢量（GeoJSON 中间产物 → JS）
  map/rivers.js         Natural Earth(PD) 全球水系 + 黄河汉/唐/清改道版本
  map/campaigns/cn/     中国战役 JSON（同构，引擎可读）
  map/campaigns/world/  世界战役 JSON（同构，引擎可读）
  map/routes/routes.json 预设路线 120 条

三来源交叉（地名 = CHGIS / 谭其骧《中国历史地图集》/ ctext 地理注释）：
  3/3 一致 → high；2/3 → medium；≤1/3 → low（low 不进默认命中，由 ContextAnchor 兜底）。
河流许可：Natural Earth（Public Domain），不使用 OSM（ODbL）。
战役：Wikidata SPARQL（网络不可达时回退内置权威种子）+ Wikipedia + 军事史公开数据。

用法：
  python3 data_scripts/build_geo.py            # 全量构建
  python3 data_scripts/build_geo.py --check   # 只校验，不写盘
"""

import json
import math
import os
import re
import sys
import urllib.request
from pathlib import Path

# ----------------------------------------------------------------------------
# 路径
# ----------------------------------------------------------------------------

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
MAP = ASSETS / "map"
CAMP_CN = MAP / "campaigns" / "cn"
CAMP_WORLD = MAP / "campaigns" / "world"
ROUTES = MAP / "routes"
GEOJSON_DIR = ROOT / "data_scripts" / "geojson_layers"
CACHE_DIR = ROOT / "data_scripts" / "_cache"

NE_50M_URL = ("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/"
              "master/geojson/ne_50m_rivers_lake_centerlines.geojson")
NE_110M_URL = ("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/"
               "master/geojson/ne_110m_rivers_lake_centerlines.geojson")

SRC_CHGIS = "CHGIS"
SRC_TAN = "谭其骧《中国历史地图集》"
SRC_CTEXT = "ctext 地理注释"
SRC_ARCH = "考古遗址实测"
SRC_NE = "Natural Earth 50m (Public Domain)"
SRC_WD = "Wikidata"
SRC_WP = "Wikipedia"
SRC_MIL = "军事史公开数据"

# ----------------------------------------------------------------------------
# 置信度打分（契约三来源规则）
# ----------------------------------------------------------------------------

def score_confidence(sources, tier=None):
    """三来源命中数 → high/medium/low。tier=1 遗址实测直接 high。"""
    if tier == 1:
        return "high"
    n = len({s for s in sources if s})
    if n >= 3:
        return "high"
    if n == 2:
        return "medium"
    return "low"


def source_str(sources):
    return "/".join(dict.fromkeys(s for s in sources if s))


def norm_year(v, default):
    try:
        return int(str(v).strip())
    except (TypeError, ValueError):
        return default


# ----------------------------------------------------------------------------
# 既有种子读取（重跑时保留已审定条目，只做字段对齐与补全）
# ----------------------------------------------------------------------------

def _read_lines(path):
    if not path.exists():
        return []
    return [ln.rstrip("\n") for ln in path.read_text(encoding="utf-8").splitlines()]


def load_place_entity():
    """返回 {id: dict}，字段对齐契约；旧 source 字符串拆成 sources 集合。"""
    out = {}
    for ln in _read_lines(ASSETS / "place_entity.txt"):
        if not ln or ln.startswith("#"):
            continue
        p = ln.split("\t")
        if len(p) < 9:
            continue
        sid, kind, name, lng, lat = p[0], p[1][:1], p[2], p[3], p[4]
        start = norm_year(p[5], -1046)
        end = norm_year(p[6], 1911)
        era = p[7]
        tier = int(p[8]) if p[8].strip().isdigit() else 3
        note = p[9] if len(p) > 9 else ""
        aliases = [a for a in (p[10].split("|") if len(p) > 10 and p[10] else []) if a]
        geometry = p[11] if len(p) > 11 and p[11].strip() else ""
        superior = p[12] if len(p) > 12 and p[12].strip() else ""
        old_src = p[13] if len(p) > 13 else ""
        seat = p[14] if len(p) > 14 and p[14].strip() else ""
        old_conf = p[15] if len(p) > 15 and p[15].strip() else ""
        sources = sources_from_old_source(old_src)
        conf = old_conf if old_conf in ("high", "medium", "low") else score_confidence(sources, tier)
        out[sid] = dict(
            id=sid, kind=kind, name=name,
            lng=float(lng), lat=float(lat),
            start=start, end=end, era=era, tier=tier, note=note,
            aliases=aliases, geometry=geometry, superior=superior,
            seat=seat, sources=sources, confidence=conf,
        )
    return out


def sources_from_old_source(old):
    s = set()
    if not old:
        return s
    if "遗址实测" in old or "考古" in old:
        s.add(SRC_ARCH)
    if "CHGIS" in old:
        s.add(SRC_CHGIS)
    if "谭其骧" in old:
        s.add(SRC_TAN)
    if "文献推定" in old:
        s.add(SRC_CTEXT)
    if not s:
        s.add(SRC_CTEXT)
    return s


def load_place_names():
    """返回 [(name, place_id, start, end, dynasty, type, superior, source, confidence)]"""
    out = []
    for ln in _read_lines(ASSETS / "place_name.txt"):
        if not ln or ln.startswith("#"):
            continue
        p = ln.split("\t")
        if len(p) < 2:
            continue
        name, pid = p[0], p[1]
        start = norm_year(p[2], -1046) if len(p) > 2 else -1046
        end = norm_year(p[3], 1911) if len(p) > 3 else 1911
        dynasty = p[4] if len(p) > 4 else ""
        ntype = p[5] if len(p) > 5 else "别名"
        superior = p[6] if len(p) > 6 and p[6].strip() else ""
        old_src = p[7] if len(p) > 7 else ""
        conf = p[8] if len(p) > 8 and p[8] in ("high", "medium", "low") else ""
        out.append(dict(name=name, place_id=pid, start=start, end=end,
                        dynasty=dynasty, ntype=ntype, superior=superior,
                        source=old_src, confidence=conf))
    return out


def load_simple_tsv(path, min_cols):
    rows = []
    for ln in _read_lines(path):
        if not ln or ln.startswith("#"):
            continue
        p = ln.split("\t")
        if len(p) >= min_cols:
            rows.append(p)
    return rows

# ----------------------------------------------------------------------------
# 新增地名种子（三来源标注；tier1 考古实测 / tier2 CHGIS+谭其骧 / tier3 文献推定）
# 字段: id, kind, name, lng, lat, start, end, era, tier, note, aliases, superior, sources
# ----------------------------------------------------------------------------

T2 = {SRC_CHGIS, SRC_TAN}          # 2/3 → medium
T3 = {SRC_CTEXT}                   # 1/3 → low
T1 = {SRC_ARCH}                    # 遗址实测 → high

NEW_ENTITIES = [
    # —— 先秦城邑补遗 ——
    ("P057", "P", "栎阳", 109.20, 34.67, -383, -350, "战国", 2, "秦献公迁都栎阳，今陕西西安阎良", "栎", "内史", T2),
    ("P058", "P", "帝丘", 115.05, 35.73, -629, -209, "春秋", 2, "卫成公迁都帝丘，今河南濮阳", "濮阳", "卫", T2),
    ("P059", "P", "邺", 114.40, 36.32, -439, -58, "战国", 2, "魏邑，曹魏邺都，今河北临漳西南", "邺城|魏郡|相州", "魏", T2),
    ("P094", "P", "宛", 112.53, 33.00, -688, 1911, "春秋", 2, "申伯故地，秦汉南阳郡治", "南阳|宛城", "楚", T2),
    # —— 秦汉边郡与都会 ——
    ("P060", "P", "姑臧", 102.64, 37.93, -121, 420, "西汉", 2, "武威郡治，河西走廊重镇", "武威|凉州", "凉州", T2),
    ("P061", "P", "张掖", 100.45, 38.93, -111, 376, "西汉", 2, "河西四郡之一，今甘肃张掖", "甘州", "凉州", T2),
    ("P062", "P", "酒泉", 98.51, 39.73, -121, 376, "西汉", 2, "河西四郡之一，今甘肃酒泉", "肃州", "凉州", T2),
    ("P063", "P", "敦煌", 94.66, 40.14, -111, 1524, "西汉", 2, "河西四郡之一，丝路门户", "沙州", "凉州", T2),
    ("P064", "P", "番禺", 113.26, 23.13, -214, 1911, "西汉", 2, "南越都城，今广州", "广州|南海", "南海郡", T2),
    ("P065", "P", "襄阳", 112.12, 32.01, -207, 1911, "东汉", 2, "荆襄要冲，汉水南岸", "襄州", "荆州", T2),
    ("P066", "P", "江陵", 112.19, 30.35, -278, 1911, "战国", 2, "楚郢都故地，南郡治所", "南郡|荆州", "楚", T2),
    ("P067", "P", "夏口", 114.30, 30.55, 208, 1911, "三国", 2, "汉水入江口，今武汉汉口", "汉口|鄂州|江夏", "荆州", T2),
    ("P068", "P", "建业", 118.78, 32.05, 211, 1911, "三国", 2, "孙吴都城，今南京", "建康|金陵|石头城|南京", "扬州", T2),
    ("P069", "P", "成都", 104.07, 30.57, -311, 1911, "战国", 2, "蜀都，秦汉蜀郡治", "益州|锦官城|蓉城", "蜀郡", T2),
    ("P070", "P", "江州", 106.55, 29.56, -316, 1911, "战国", 3, "巴国故都，今重庆", "重庆|渝州", "巴郡", T3),
    ("P071", "P", "广陵", 119.42, 32.39, -319, 1911, "战国", 2, "江淮都会，今扬州", "扬州|江都|邗城", "广陵郡", T2),
    ("P072", "P", "彭城", 117.18, 34.26, -221, 1911, "西汉", 2, "西楚都，今徐州", "徐州", "楚国", T2),
    ("P073", "P", "下邳", 117.57, 34.35, -221, 280, "西汉", 3, "下邳国治，今江苏睢宁北", "邳州", "徐州", T3),
    ("P074", "P", "巨鹿", 115.10, 37.20, -221, 200, "秦", 3, "巨鹿之战战场，今河北平乡", "钜鹿", "巨鹿郡", T3),
    ("P075", "P", "云中", 113.10, 40.10, -221, 215, "西汉", 2, "北方边郡，今内蒙古托克托", "云中郡", "并州", T2),
    ("P076", "P", "九原", 109.80, 40.40, -221, 190, "西汉", 3, "北方边郡，今包头西", "五原", "并州", T3),
    ("P077", "P", "朔方", 107.00, 40.40, -127, 190, "西汉", 3, "河套边郡，今内蒙古杭锦旗北", "朔方郡", "并州", T3),
    ("P078", "P", "渔阳", 117.00, 40.10, -221, 200, "西汉", 3, "边郡，今北京密云西南", "渔阳郡", "幽州", T3),
    ("P079", "P", "上谷", 115.50, 40.40, -221, 200, "西汉", 3, "边郡，今河北怀来东南", "上谷郡", "幽州", T3),
    ("P080", "P", "右北平", 118.00, 41.00, -221, 200, "西汉", 3, "边郡，今天津蓟县", "右北平郡", "幽州", T3),
    ("P081", "P", "襄平", 123.73, 41.25, -279, 330, "战国", 2, "辽东郡治，今辽宁辽阳", "辽东", "幽州", T2),
    ("P082", "P", "乐浪", 125.67, 38.99, -108, 313, "西汉", 2, "汉四郡之一，今平壤南", "乐浪郡", "幽州", T2),
    ("P083", "P", "交趾", 105.85, 21.03, -111, 939, "西汉", 2, "交趾郡治，今河内", "交州|升龙|河内", "交州", T2),
    ("P084", "P", "桂林", 110.29, 25.27, -214, 1911, "秦", 3, "秦桂林郡，今广西贵港", "桂林郡", "象郡", T3),
    # —— 关隘战场 ——
    ("P085", "P", "武关", 110.10, 33.50, -350, -206, "战国", 3, "秦南关，今陕西商南西南", "武关隘", "内史", T3),
    ("P086", "P", "散关", 106.90, 34.35, -350, -206, "战国", 3, "大散关，秦岭蜀道北口", "大散关", "内史", T3),
    ("P087", "P", "剑阁", 105.55, 32.05, 221, 1368, "三国", 3, "蜀道天险，今四川剑阁", "剑门关", "益州", T3),
    ("P088", "P", "赤壁", 113.90, 29.72, 208, 208, "东汉", 3, "赤壁之战战场，今湖北赤壁西北", "乌林", "荆州", T3),
    ("P089", "P", "合肥", 117.27, 31.82, -221, 1911, "西汉", 2, "淮右重镇，今安徽合肥", "庐州", "扬州", T2),
    ("P090", "P", "京口", 119.43, 32.20, 209, 1911, "东汉", 3, "长江渡口，今江苏镇江", "润州", "扬州", T3),
    ("P091", "P", "豫章", 115.86, 28.68, -202, 1911, "西汉", 2, "豫章郡治，今南昌", "南昌", "扬州", T2),
    ("P092", "P", "长沙", 112.94, 28.23, -202, 1911, "西汉", 2, "长沙郡/王国治", "潭州", "荆州", T2),
    ("P093", "P", "武陵", 111.69, 29.03, -202, 1911, "西汉", 3, "武陵郡治，今湖南常德", "常德", "荆州", T3),
    ("P104", "P", "街亭", 106.80, 35.00, 228, 228, "三国", 3, "诸葛亮北伐马谡败处", "街泉", "天水郡", T3),
    ("P105", "P", "五丈原", 107.38, 34.28, 234, 234, "三国", 3, "诸葛亮屯兵病逝处，今岐山南", "五丈塬", "扶风郡", T3),
    ("P106", "P", "采石矶", 118.48, 30.70, 960, 1911, "北宋", 3, "长江江防要隘，今马鞍山", "采石", "太平州", T3),
    # —— 山岳大泽 ——
    ("P095", "P", "嵩山", 113.08, 34.50, -1046, 1911, "西周", 1, "中岳，今河南登封", "嵩高|崇山", "豫州", T1),
    ("P096", "P", "华山", 110.09, 34.48, -1046, 1911, "西周", 1, "西岳，今陕西华阴", "西岳", "雍州", T1),
    ("P097", "P", "衡山", 112.65, 27.20, -1046, 1911, "西周", 1, "南岳，今湖南衡山", "南岳", "荆州", T1),
    ("P098", "P", "恒山", 113.73, 39.67, -1046, 1911, "西周", 1, "北岳，今山西浑源", "北岳", "并州", T1),
    ("P099", "P", "洞庭", 112.90, 29.30, -1046, 1911, "春秋", 3, "楚地大泽，今洞庭湖", "洞庭湖", "荆州", T3),
    ("P100", "P", "彭蠡", 116.30, 29.10, -1046, 1911, "春秋", 3, "古泽名，今鄱阳湖", "鄱阳湖", "扬州", T3),
    ("P101", "P", "云梦", 113.70, 30.30, -1046, -200, "春秋", 3, "楚地大泽，江汉平原湖群", "云梦泽", "荆州", T3),
    ("P102", "P", "震泽", 120.30, 31.10, -1046, 1911, "春秋", 3, "太湖古名", "太湖", "扬州", T3),
    ("P103", "P", "碣石", 119.15, 39.72, -1046, 1911, "春秋", 3, "燕地海山，今河北昌黎北", "碣石山", "幽州", T3),
    # —— 水利工程 ——
    ("P107", "P", "灵渠", 110.70, 25.20, -214, -214, "秦", 2, "秦凿运河，沟通湘漓", "秦凿渠|兴安运河", "桂林郡", T2),
    ("P108", "P", "郑国渠", 108.50, 34.50, -246, -246, "战国", 2, "关中灌溉渠，韩国水工郑国凿", "郑渠", "内史", T2),
    ("P109", "P", "邗沟", 119.40, 32.40, -486, -486, "春秋", 3, "吴王夫差凿，江淮运河首段", "邗城水道", "吴", T3),
    # —— 面实体：区域与政权（示意色块，tier3） ——
    ("A013", "A", "西域", 85.00, 41.00, -138, 1516, "西汉", 3, "玉门关以西泛称，示意范围", "新疆|西域三十六国", "西域都护府", T3),
    ("A014", "A", "河西", 101.50, 38.50, -202, 1911, "西汉", 3, "河西走廊，祁连山以北狭长地带", "河西走廊", "凉州", T3),
    ("A015", "A", "河套", 108.50, 40.50, -300, 1911, "战国", 3, "黄河“几”字弯沿岸平原", "河曲", "并州", T3),
    ("A016", "A", "岭南", 112.50, 23.00, -214, 1911, "秦", 3, "五岭以南，今两广越南北部", "岭表|岭外", "交州", T3),
    ("A017", "A", "漠北", 105.00, 47.00, -200, 1368, "西汉", 3, "蒙古高原大漠以北", "大漠|幕北", "匈奴", T3),
    ("A018", "A", "匈奴", 105.00, 42.00, -300, 216, "战国", 3, "北方游牧政权，范围示意", "胡|北狄", "单于庭", T3),
    ("A019", "A", "突厥", 100.00, 42.00, 552, 744, "南北朝", 3, "北方游牧政权，范围示意", "突厥汗国", "牙帐", T3),
    ("A020", "A", "吐蕃", 91.00, 31.00, 618, 842, "唐", 3, "青藏高原政权，范围示意", "西羌|乌斯藏", "逻些", T3),
    ("A021", "A", "南诏", 100.00, 25.00, 738, 902, "唐", 3, "西南政权，今云南一带", "鹤拓", "羊苴咩城", T3),
]

# 额外名称快照：(名称, 实体id, 起年, 止年, 朝代, 类型, 上级)
NEW_SNAPSHOTS = [
    ("神都", "P042", 684, 705, "武周", "别名", "洛阳"),
    ("东都", "P042", 25, 907, "东汉唐", "别名", "洛阳"),
    ("魏郡", "P059", -439, -58, "战国", "别名", "邺"),
    ("甘州", "P061", 554, 1911, "西魏", "别名", "张掖"),
    ("肃州", "P062", 583, 1911, "隋", "别名", "酒泉"),
    ("沙州", "P063", 400, 1524, "东晋", "别名", "敦煌"),
    ("南海", "P064", -214, 1911, "秦", "别名", "番禺"),
    ("鄂州", "P067", 223, 1911, "三国", "别名", "夏口"),
    ("益州", "P069", -106, 1911, "西汉", "别名", "成都"),
    ("江都", "P071", -319, 1911, "战国", "别名", "广陵"),
    ("徐州", "P072", -221, 1911, "西汉", "别名", "彭城"),
    ("云中郡", "P075", -221, 215, "西汉", "别名", "云中"),
    ("渔阳郡", "P078", -221, 200, "西汉", "别名", "渔阳"),
    ("上谷郡", "P079", -221, 200, "西汉", "别名", "上谷"),
    ("乐浪郡", "P082", -108, 313, "西汉", "别名", "乐浪"),
    ("交州", "P083", -111, 939, "西汉", "别名", "交趾"),
    ("南昌", "P091", -202, 1911, "西汉", "别名", "豫章"),
    ("洞庭湖", "P099", -1046, 1911, "春秋", "别名", "洞庭"),
    ("鄱阳湖", "P100", -1046, 1911, "春秋", "别名", "彭蠡"),
    ("太湖", "P102", -1046, 1911, "春秋", "别名", "震泽"),
]

# ----------------------------------------------------------------------------
# 合并：实体 + 名称快照
# ----------------------------------------------------------------------------

def build_entities():
    ents = load_place_entity()
    for row in NEW_ENTITIES:
        (sid, kind, name, lng, lat, start, end, era, tier,
         note, aliases, superior, sources) = row
        if sid in ents:
            continue
        ents[sid] = dict(
            id=sid, kind=kind, name=name, lng=lng, lat=lat,
            start=start, end=end, era=era, tier=tier, note=note,
            aliases=aliases.split("|") if aliases else [],
            geometry="", superior=superior, seat="",
            sources=set(sources),
            confidence=score_confidence(sources, tier),
        )
    # 重算置信度（三来源规则兜底）
    for e in ents.values():
        e["confidence"] = score_confidence(e["sources"], e["tier"])
    return ents


def build_names(ents):
    names = load_place_names()
    seen = {(n["name"], n["place_id"]) for n in names}

    def add(name, pid, start, end, dynasty, ntype, superior, sources):
        if (name, pid) in seen:
            return
        seen.add((name, pid))
        names.append(dict(
            name=name, place_id=pid, start=start, end=end, dynasty=dynasty,
            ntype=ntype, superior=superior, source=source_str(sources),
            confidence=score_confidence(sources, ents[pid]["tier"]),
        ))

    for snap in NEW_SNAPSHOTS:
        name, pid, start, end, dynasty, ntype, superior = snap
        if pid in ents:
            add(name, pid, start, end, dynasty, ntype, superior, ents[pid]["sources"])

    # 新实体自动生成 正名 + 别名 快照
    for e in ents.values():
        add(e["name"], e["id"], e["start"], e["end"], e["era"], "正名",
            e["superior"], e["sources"])
        for a in e["aliases"]:
            add(a, e["id"], e["start"], e["end"], e["era"], "别名",
                e["superior"], e["sources"])
    return names


def write_place_entity(ents):
    lines = [
        "# 实体id\t类型(P点/A面)\t正名\t经度\t纬度\t起年\t止年\t朝代\ttier(1实测/2考证/3推定)\t说明\t别名(竖线分隔)\tgeometry\t上级\tsource\tseat\tconfidence",
    ]
    for e in sorted(ents.values(), key=lambda x: x["id"]):
        lines.append("\t".join([
            e["id"], e["kind"], e["name"],
            f'{e["lng"]:.2f}', f'{e["lat"]:.2f}',
            str(e["start"]), str(e["end"]), e["era"], str(e["tier"]),
            e["note"], "|".join(e["aliases"]), e["geometry"],
            e["superior"], source_str(e["sources"]), e["seat"], e["confidence"],
        ]))
    (ASSETS / "place_entity.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_place_names(names, ents):
    lines = ["# 名称\t实体id\t起年\t止年\t朝代\t类型(正名|别名)\t上级\tsource\tconfidence"]
    for n in sorted(names, key=lambda x: (x["place_id"], x["name"])):
        e = ents.get(n["place_id"])
        src = n["source"] or (source_str(e["sources"]) if e else "")
        conf = n["confidence"] or (e["confidence"] if e else "low")
        lines.append("\t".join([
            n["name"], n["place_id"], str(n["start"]), str(n["end"]),
            n["dynasty"], n["ntype"], n["superior"], src, conf,
        ]))
    (ASSETS / "place_name.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_place_geo(ents):
    geo = {}
    old_path = ASSETS / "place_geo.json"
    if old_path.exists():
        geo = json.loads(old_path.read_text(encoding="utf-8"))
    for e in ents.values():
        if e["kind"] != "P":
            continue
        modern = re.sub(r"^.*?今", "", e["note"]) if "今" in e["note"] else e["superior"]
        geo[e["name"]] = {
            "lat": round(e["lat"], 2), "lng": round(e["lng"], 2),
            "modern": modern[:12], "zoom": 8 if e["tier"] <= 2 else 9,
        }
        for a in e["aliases"]:
            geo.setdefault(a, geo[e["name"]])
    old_path.write_text(
        json.dumps(geo, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def write_place_txt(ents):
    rows = load_simple_tsv(ASSETS / "place.txt", 2)
    existing = {r[0] for r in rows}
    lines = ["# 地名\t释义\t今地"]
    for r in rows:
        lines.append("\t".join(r[:3]))
    for e in ents.values():
        if e["name"] in existing:
            continue
        modern = ""
        m = re.search(r"今([^，。|]+)", e["note"])
        if m:
            modern = m.group(1)
        lines.append("\t".join([e["name"], e["note"], modern]))
    (ASSETS / "place.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_world_tables(ents):
    # world_place.txt：保留既有世界地名；实体表不混入世界表
    wp = ASSETS / "world_place.txt"
    rows = load_simple_tsv(wp, 3)
    if not rows:
        return
    lines = ["# 地名\t纬度\t经度\t区域\t说明"]
    for r in rows:
        lines.append("\t".join(r[:5]))
    wp.write_text("\n".join(lines) + "\n", encoding="utf-8")

    # en 表校验：英文 → 中文必须能在 world_place 或 place 表命中
    en_path = ASSETS / "world_place_en.txt"
    en_rows = load_simple_tsv(en_path, 2)
    cn_names = {r[0] for r in rows} | {e["name"] for e in ents.values()}
    lines = ["# English\t中文"]
    for r in en_rows:
        en, cn = r[0], r[1]
        if cn not in cn_names:
            continue  # 悬空映射剔除，避免气泡落到空实体
        lines.append("\t".join([en, cn]))
    en_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

# ----------------------------------------------------------------------------
# 18 套朝代图层
# bbox = [lat0, lat1, lng0, lng1]；由 bbox 生成示意八边形（tier3 文献推定）
# ----------------------------------------------------------------------------

ERA_LIST = [
    ("西周", -1046, -771),
    ("春秋", -770, -476),
    ("战国", -475, -221),
    ("秦", -221, -207),
    ("西汉", -202, 8),
    ("东汉", 25, 220),
    ("三国", 220, 280),
    ("西晋", 266, 316),
    ("东晋十六国", 317, 420),
    ("南北朝", 420, 589),
    ("隋", 581, 618),
    ("唐", 618, 907),
    ("五代十国", 907, 960),
    ("北宋", 960, 1127),
    ("南宋", 1127, 1279),
    ("元", 1271, 1368),
    ("明", 1368, 1644),
    ("清", 1644, 1911),
]

# 示意势力：(name, color, bbox, tier, confidence)
ERA_POWERS = {
    "西周": [
        ("宗周", "#A8894F", [33.5, 35.5, 107.5, 110.5], 3, "low"),
        ("成周", "#8B6914", [33.8, 35.2, 111.0, 113.5], 3, "low"),
    ],
    "秦": [
        ("秦", "#B33A2B", [30.0, 40.0, 100.0, 112.0], 3, "medium"),
    ],
    "东汉": [
        ("东汉", "#4C6E9A", [28.0, 42.0, 102.0, 123.0], 3, "medium"),
        ("匈奴", "#8A6A5C", [38.0, 50.0, 95.0, 120.0], 3, "low"),
    ],
    "西晋": [
        ("西晋", "#4C6E9A", [25.0, 42.0, 100.0, 123.0], 3, "medium"),
    ],
    "东晋十六国": [
        ("东晋", "#3C7A5C", [24.0, 34.0, 102.0, 122.0], 3, "medium"),
        ("前秦", "#B0894C", [32.0, 42.0, 100.0, 115.0], 3, "low"),
        ("鲜卑诸部", "#7A5FA6", [38.0, 48.0, 110.0, 125.0], 3, "low"),
    ],
    "南北朝": [
        ("南朝", "#3C7A5C", [23.0, 34.0, 103.0, 122.0], 3, "medium"),
        ("北魏", "#4C6E9A", [32.0, 42.0, 100.0, 118.0], 3, "medium"),
    ],
    "隋": [
        ("隋", "#8A5A3C", [25.0, 42.0, 100.0, 123.0], 3, "medium"),
    ],
    "五代十国": [
        ("中原五代", "#4C6E9A", [30.0, 40.0, 110.0, 118.0], 3, "low"),
        ("南唐", "#3C7A5C", [24.0, 33.0, 112.0, 121.0], 3, "low"),
        ("吴越", "#3C8A6A", [28.0, 32.0, 118.0, 122.5], 3, "low"),
        ("前后蜀", "#8A5C4C", [26.0, 33.0, 101.0, 108.0], 3, "low"),
    ],
    "南宋": [
        ("南宋", "#3C7A5C", [22.0, 34.0, 103.0, 122.0], 3, "medium"),
        ("金", "#4C6E9A", [32.0, 46.0, 110.0, 128.0], 3, "medium"),
        ("西夏", "#8A6A4C", [35.0, 41.0, 102.0, 110.0], 3, "low"),
    ],
    "元": [
        ("元", "#7A5FA6", [18.0, 50.0, 95.0, 135.0], 3, "medium"),
    ],
    "明": [
        ("明", "#B33A2B", [20.0, 42.0, 100.0, 124.0], 3, "medium"),
        ("蒙古鞑靼", "#8A6A5C", [38.0, 50.0, 100.0, 120.0], 3, "low"),
    ],
    "清": [
        ("清", "#4C6E9A", [18.0, 53.0, 75.0, 135.0], 3, "medium"),
    ],
}


def bbox_octagon(bbox):
    lat0, lat1, lng0, lng1 = bbox
    clat, clng = (lat0 + lat1) / 2, (lng0 + lng1) / 2
    hlat, hlng = (lat1 - lat0) / 2, (lng1 - lng0) / 2
    return [
        [round(clat, 3), round(clng - hlng, 3)],
        [round(lat1, 3), round(clng - hlng * 0.5, 3)],
        [round(lat1, 3), round(clng + hlng * 0.5, 3)],
        [round(clat, 3), round(clng + hlng, 3)],
        [round(lat0, 3), round(clng + hlng * 0.5, 3)],
        [round(lat0, 3), round(clng - hlng * 0.5, 3)],
    ]


def load_existing_poly():
    path = MAP / "powers_poly.js"
    if not path.exists():
        return []
    txt = path.read_text(encoding="utf-8")
    m = re.search(r"window\.POWERS_POLY\s*=\s*(\[.*?\]);", txt, re.S)
    if not m:
        return []
    return json.loads(m.group(1))


def build_powers_poly():
    existing = load_existing_poly()
    by_era = {}
    for p in existing:
        p.setdefault("source", SRC_TAN)
        p.setdefault("confidence", "low" if p.get("tier", 3) == 3 else "medium")
        by_era.setdefault(p["era"], []).append(p)

    for era, y0, y1 in ERA_LIST:
        if era in by_era:
            continue
        powers = ERA_POWERS.get(era, [(era, "#8B6914", [25.0, 42.0, 100.0, 123.0], 3, "low")])
        for name, color, bbox, tier, conf in powers:
            by_era.setdefault(era, []).append(dict(
                name=name, era=era, **{"from": y0, "to": y1}, color=color, tier=tier,
                pts=bbox_octagon(bbox), source=SRC_TAN, confidence=conf,
            ))

    # GeoJSON 中间产物（每朝代一个 FeatureCollection）
    GEOJSON_DIR.mkdir(parents=True, exist_ok=True)
    for era, _, _ in ERA_LIST:
        feats = []
        for p in by_era.get(era, []):
            ring = [[lng, lat] for lat, lng in p["pts"]]
            ring.append(ring[0])
            feats.append({
                "type": "Feature",
                "properties": {k: p[k] for k in ("name", "era", "from", "to", "color", "tier", "source", "confidence")},
                "geometry": {"type": "Polygon", "coordinates": [ring]},
            })
        fc = {"type": "FeatureCollection", "features": feats}
        (GEOJSON_DIR / f"{era}.geojson").write_text(
            json.dumps(fc, ensure_ascii=False, indent=1), encoding="utf-8")

    all_poly = []
    for era, _, _ in ERA_LIST:
        all_poly.extend(by_era.get(era, []))

    body = json.dumps(all_poly, ensure_ascii=False, separators=(",", ":"))
    header = ("// ★ 落文·地理数据资产（data_scripts/build_geo.py 生成，勿手改）\n"
              "// 势力范围为历史地图集示意矢量，tier3 不代表精确边界；每条带 source/confidence。\n")
    (MAP / "powers_poly.js").write_text(
        header + "window.POWERS_POLY = " + body + ";\n", encoding="utf-8")
    return all_poly

# ----------------------------------------------------------------------------
# 河流：Natural Earth 50m（Public Domain），不用 OSM
# ----------------------------------------------------------------------------

NE_ZH = {
    "Huang": "黄河", "Chang Jiang": "长江", "Yangtze": "长江", "Han": "汉水",
    "Jinsha": "金沙江", "Min": "岷江", "Xi": "西江", "Liao": "辽河",
    "Songhua": "松花江", "Nu": "怒江", "Brahmaputra": "雅鲁藏布江",
    "Mekong": "澜沧江", "Yuan": "沅江", "Gan": "赣江", "Dadu": "大渡河",
    "Hongshui": "红水河", "Nanpan": "南盘江", "Tongtian": "通天河",
    "Tuotuo": "沱沱河", "Xiliao": "西辽河", "Xar Moron": "西拉木伦河",
    "Ob": "鄂毕河", "Lena": "勒拿河", "Donau": "多瑙河", "Danube": "多瑙河",
    "Paraná": "巴拉那河", "Congo": "刚果河", "Amazonas": "亚马孙河",
    "Mississippi": "密西西比河", "Nile": "尼罗河", "Peace": "皮斯河",
    "Irtysh": "额尔齐斯河", "Ganges": "恒河", "Indus": "印度河",
}

# 历史河道（示意折线，谭其骧历史地图集 / 水经注）
AUTHORED_RIVERS = [
    dict(id="huanghe_han", name="黄河·西汉大河", nameEn="Yellow River (Han course)",
         era="西汉", from_=-202, to=11, color="#B0894C", world=False,
         source=f"{SRC_TAN}/水经注", confidence="medium",
         pts=[[36.0, 103.8], [38.5, 106.3], [40.5, 108.0], [40.1, 111.1],
              [35.7, 110.6], [34.6, 110.3], [34.8, 111.2], [35.2, 113.2],
              [36.0, 115.0], [37.7, 115.7], [39.1, 117.2]]),
    dict(id="huanghe_tang", name="黄河·唐代河道", nameEn="Yellow River (Tang course)",
         era="唐", from_=618, to=1048, color="#8B6914", world=False,
         source=f"{SRC_TAN}/水经注", confidence="medium",
         pts=[[36.0, 103.8], [38.5, 106.3], [40.5, 108.0], [40.1, 111.1],
              [35.7, 110.6], [34.6, 110.3], [34.8, 113.0], [35.6, 114.8],
              [36.3, 115.5], [37.5, 116.5], [38.3, 117.5], [39.0, 118.2]]),
    dict(id="huanghe_qing", name="黄河·清咸丰改道后", nameEn="Yellow River (Qing course)",
         era="清", from_=1855, to=1911, color="#6B8E3C", world=False,
         source=f"{SRC_TAN}/水经注", confidence="high",
         pts=[[36.0, 103.8], [38.5, 106.3], [40.5, 108.0], [40.1, 111.1],
              [35.7, 110.6], [34.6, 110.3], [34.8, 113.0], [34.8, 114.3],
              [35.4, 115.4], [36.6, 116.8], [37.5, 117.5], [37.8, 118.5],
              [37.5, 119.2]]),
    dict(id="huaihe", name="淮河", nameEn="Huai River", era="古今", from_=-1046, to=1911,
         color="#4C7A8A", world=False, source=f"{SRC_TAN}/水经注", confidence="medium",
         pts=[[32.4, 113.0], [32.3, 114.5], [32.5, 116.0], [32.2, 117.5],
              [32.0, 119.0], [32.2, 120.3]]),
    dict(id="jishui", name="济水（古四渎）", nameEn="Ji River (extinct)", era="先秦",
         from_=-1046, to=900, color="#7A8B99", world=False,
         source="水经注/谭其骧《中国历史地图集》", confidence="low",
         pts=[[35.0, 112.0], [34.9, 113.5], [35.2, 115.0], [35.4, 116.5],
              [35.7, 117.5], [37.0, 118.5]]),
    dict(id="sishui", name="泗水", nameEn="Si River", era="先秦", from_=-1046, to=1200,
         color="#7A8B99", world=False, source="水经注/谭其骧《中国历史地图集》",
         confidence="low",
         pts=[[35.6, 117.0], [34.8, 117.3], [34.2, 117.2], [33.5, 117.0],
              [33.0, 116.8]]),
]


def _fetch_ne():
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cache = CACHE_DIR / "ne_50m_rivers.geojson"
    if cache.exists():
        return json.loads(cache.read_text(encoding="utf-8"))
    try:
        req = urllib.request.Request(NE_50M_URL, headers={"User-Agent": "luowen-build/1.0"})
        with urllib.request.urlopen(req, timeout=20) as r:
            data = json.loads(r.read().decode("utf-8"))
        cache.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
        return data
    except Exception as exc:  # 离线兜底：无缓存则只出 authored 层
        print(f"[warn] Natural Earth 下载失败，回退 authored 河道：{exc}", file=sys.stderr)
        return None


def _simplify(pts, max_pts=50):
    if len(pts) <= max_pts:
        return pts
    stride = math.ceil(len(pts) / max_pts)
    out = pts[::stride]
    if out[-1] != pts[-1]:
        out.append(pts[-1])
    return out


def build_rivers():
    rivers = []
    ne = _fetch_ne()
    if ne:
        for f in ne["features"]:
            prop = f["properties"]
            name_en = prop.get("name") or ""
            rank = prop.get("scalerank") or 9
            geom = f["geometry"]
            if geom["type"] not in ("LineString", "MultiLineString"):
                continue
            lines = [geom["coordinates"]] if geom["type"] == "LineString" else geom["coordinates"]
            pts = []
            for line in lines:
                pts.extend([[round(p[1], 3), round(p[0], 3)] for p in line])
            if len(pts) < 4:
                continue
            lons = [p[1] for p in pts]
            lats = [p[0] for p in pts]
            in_china = (min(lons) > 90 and max(lons) < 135 and min(lats) > 18 and max(lats) < 50)
            if not in_china and rank > 3:
                continue
            zh = NE_ZH.get(name_en, "")
            rid = re.sub(r"[^a-z0-9]+", "_", name_en.lower()).strip("_") or "river"
            rivers.append(dict(
                id=f"ne_{rid}", name=zh or name_en, nameEn=name_en,
                era="古今", **{"from": -1046, "to": 1911},
                color="#3C6E9E" if not in_china else "#4C7A8A",
                pts=_simplify(pts), source=SRC_NE, confidence="high", world=not in_china,
            ))
    for r in AUTHORED_RIVERS:
        r = dict(r)
        r["from"] = r.pop("from_")
        rivers.append(r)
    body = json.dumps(rivers, ensure_ascii=False, separators=(",", ":"))
    header = ("// ★ 落文·地理数据资产（data_scripts/build_geo.py 生成，勿手改）\n"
              "// 世界水系源 Natural Earth 50m（Public Domain）；历史河道为示意折线。\n")
    (MAP / "rivers.js").write_text(
        header + "window.RIVERS = " + body + ";\n", encoding="utf-8")
    return rivers

# ----------------------------------------------------------------------------
# 战役 JSON（契约 schema：id/title/era/context/terrainFocus/heightMap/duration/
# victory/sides/diplomacy/armies/events/phases + source/confidence）
# ----------------------------------------------------------------------------

def mk_campaign(cid, title, era, context, center, radius, duration, victory,
                sides, armies, events, source, confidence, world=False, height_map=None):
    side_ids = [s["id"] for s in sides]
    diplomacy = []
    for i, a in enumerate(side_ids):
        for b in side_ids[i + 1:]:
            diplomacy.append({"a": a, "b": b, "relation": "hostile"})
    army_dicts = []
    for a in armies:
        aid, aname, aside, pos, strength, atype, fatigue, morale, intent, *rest = a
        d = dict(id=aid, name=aname, side=aside, initialPos=list(pos),
                 strength=strength, type=atype, fatigue=fatigue, morale=morale, intent=intent)
        if rest:
            d["target"] = list(rest[0])
        army_dicts.append(d)
    side_to_army = {}
    for a in armies:
        side_to_army.setdefault(a[2], a[0])
    event_dicts = []
    for e in events:
        trigger, action, target, value, reason = e
        # target 惯例为军队 id；若写的是阵营 id，解析为该阵营首支军队
        if target in {s["id"] for s in sides} and target in side_to_army:
            target = side_to_army[target]
        d = dict(trigger=trigger, action=action, target=target)
        if value is not None:
            d["value"] = value
        if reason:
            d["reason"] = reason
        event_dicts.append(d)
    camp = {
        "id": cid, "title": title, "era": era, "context": context,
        "terrainFocus": {"center": list(center), "radius": radius},
        "duration": duration,
        "victory": victory,
        "sides": sides,
        "diplomacy": diplomacy,
        "armies": army_dicts,
        "events": event_dicts,
        "phases": [],
        "source": source, "confidence": confidence, "world": world,
    }
    if height_map:
        camp["heightMap"] = height_map
    return camp


CN_CAMPAIGNS = [
    mk_campaign(
        "muye", "牧野之战", "前1046", "周武王会合诸侯东进，渡孟津，战于牧野；商军阵前倒戈，纣王自焚，商亡周立。",
        [113.90, 35.30], 60, 7,
        {"type": "capture", "target": "zhou", "side": "zhou"},
        [{"id": "zhou", "name": "周联军", "color": "#8B6914"}, {"id": "shang", "name": "商", "color": "#C23B2E"}],
        [
            ("wu", "周武王", "zhou", [113.60, 35.40], 9, "elite", 1, "high", "advance", [113.90, 35.30]),
            ("jiang", "姜尚", "zhou", [113.70, 35.20], 7, "cavalry", 0, "high", "flank", [114.00, 35.35]),
            ("zhoujun", "商军", "shang", [113.95, 35.30], 10, "militia", 2, "wavering", "hold"),
        ],
        [
            ({"turn": 3}, "moraleChange", "zhoujun", "broken", "商军阵前倒戈"),
            ({"army": "zhoujun", "strengthBelow": 3}, "surrender", "zhoujun", None, "纣王自焚，商亡"),
        ],
        f"史记·周本纪/{SRC_TAN}", "high"),
    mk_campaign(
        "chengpu", "城濮之战", "前632", "晋文公允诺退避三舍，避楚军锋芒；联合齐秦，在城濮大败楚成王联军，奠定晋国霸业。",
        [115.40, 35.40], 55, 18,
        {"type": "rout", "side": "jin"},
        [{"id": "jin", "name": "晋", "color": "#8A6A3E"}, {"id": "chu", "name": "楚", "color": "#7A4B8A"}],
        [
            ("jinwen", "晋文公", "jin", [115.20, 35.50], 10, "elite", 1, "high", "advance", [115.45, 35.35]),
            ("zizhi", "晋上军", "jin", [115.30, 35.60], 7, "regular", 0, "steady", "flank"),
            ("ziyu", "子玉", "chu", [115.60, 35.30], 9, "regular", 1, "steady", "advance", [115.35, 35.45]),
            ("chujun", "楚联军", "chu", [115.70, 35.20], 7, "militia", 1, "steady", "hold"),
        ],
        [
            ({"turn": 1}, "setIntent", "jinwen", "advance", "退避三舍后回师"),
            ({"turn": 8}, "moraleChange", "chujun", "wavering", "左翼被晋下军击溃"),
        ],
        "左传·僖公二十八年/史记·晋世家", "high"),
    mk_campaign(
        "xiao", "崤之战", "前627", "秦穆公袭郑不成，回师过崤山；晋襄公联合姜戎设伏，全歼秦军，三帅被俘。",
        [111.60, 34.60], 40, 10,
        {"type": "annihilate", "target": "mengming", "side": "jin"},
        [{"id": "jin", "name": "晋", "color": "#8A6A3E"}, {"id": "qin", "name": "秦", "color": "#B33A2B"}],
        [
            ("xiang", "晋襄公", "jin", [111.70, 34.70], 8, "regular", 0, "high", "ambush", [111.55, 34.55]),
            ("mengming", "孟明视", "qin", [111.40, 34.50], 8, "regular", 2, "steady", "advance", [111.70, 34.60]),
        ],
        [
            ({"turn": 2}, "ambush", "mengming", None, "崤山隘道伏兵四起"),
            ({"army": "mengming", "strengthBelow": 3}, "surrender", "mengming", None, "三帅被俘"),
        ],
        "左传·僖公三十三年/史记·秦本纪", "high"),
    mk_campaign(
        "bi", "邲之战", "前597", "楚庄王围郑，晋军渡河救郑；将帅不和，晋军在邲地溃败，楚庄王问鼎中原。",
        [113.70, 34.70], 50, 15,
        {"type": "rout", "side": "chu"},
        [{"id": "chu", "name": "楚", "color": "#7A4B8A"}, {"id": "jin", "name": "晋", "color": "#8A6A3E"}],
        [
            ("zhuang", "楚庄王", "chu", [113.80, 34.60], 10, "elite", 0, "high", "advance", [113.65, 34.70]),
            ("xunlin", "荀林父", "jin", [113.50, 34.80], 9, "regular", 1, "wavering", "hold"),
        ],
        [
            ({"turn": 6}, "moraleChange", "jin", "wavering", "晋将帅战守不一"),
            ({"turn": 10}, "rout", "jin", None, "楚军突进，晋军争舟渡河"),
        ],
        "左传·宣公十二年/史记", "medium"),
    mk_campaign(
        "yanling", "鄢陵之战", "前575", "晋厉公伐郑，楚共王救郑；战于鄢陵，楚共王中目，楚军夜遁，晋复霸。",
        [113.10, 34.60], 45, 14,
        {"type": "rout", "side": "jin"},
        [{"id": "jin", "name": "晋", "color": "#8A6A3E"}, {"id": "chu", "name": "楚", "color": "#7A4B8A"}],
        [
            ("ligong", "晋厉公", "jin", [113.00, 34.70], 9, "elite", 0, "high", "advance", [113.15, 34.58]),
            ("gongwang", "楚共王", "chu", [113.30, 34.55], 9, "regular", 1, "steady", "advance"),
        ],
        [
            ({"turn": 7}, "moraleChange", "gongwang", "wavering", "共王中箭伤目"),
        ],
        "左传·成公十六年/史记", "medium"),
    mk_campaign(
        "guiling", "桂陵之战", "前354", "魏庞涓围赵邯郸，齐威王以田忌为主、孙膑为军师，直趋大梁，魏军回师，桂陵中伏。",
        [114.70, 35.00], 50, 20,
        {"type": "ambushWin", "target": "pangjuan", "side": "qi"},
        [{"id": "qi", "name": "齐", "color": "#3C6E9E"}, {"id": "wei", "name": "魏", "color": "#8A5A3C"}],
        [
            ("tianji", "田忌", "qi", [114.50, 35.10], 9, "regular", 0, "high", "advance", [114.80, 34.95]),
            ("sunbin", "孙膑", "qi", [114.60, 35.00], 6, "elite", 0, "high", "ambush"),
            ("pangjuan", "庞涓", "wei", [115.00, 34.90], 9, "regular", 2, "steady", "rush", [114.60, 35.00]),
        ],
        [
            ({"turn": 5}, "setIntent", "pangjuan", "rush", "回师救大梁"),
            ({"turn": 10}, "ambush", "pangjuan", None, "桂陵道中齐伏兵起"),
        ],
        "史记·孙子吴起列传", "medium"),
    mk_campaign(
        "mling", "马陵之战", "前341", "魏庞涓攻韩，齐孙膑减灶示弱，诱庞涓追入马陵道，万弩齐发，庞涓自刎，魏霸势衰。",
        [115.50, 36.00], 45, 18,
        {"type": "annihilate", "target": "pangjuan", "side": "qi"},
        [{"id": "qi", "name": "齐", "color": "#3C6E9E"}, {"id": "wei", "name": "魏", "color": "#8A5A3C"}],
        [
            ("tianji", "田忌", "qi", [115.30, 36.10], 9, "regular", 0, "high", "retreat", [115.70, 35.90]),
            ("sunbin", "孙膑", "qi", [115.60, 35.95], 6, "elite", 0, "high", "ambush"),
            ("pangjuan", "庞涓", "wei", [115.20, 36.20], 8, "cavalry", 1, "steady", "rush", [115.70, 35.90]),
        ],
        [
            ({"turn": 6}, "moraleChange", "pangjuan", "steady", "减灶示弱，庞涓轻敌"),
            ({"army": "pangjuan", "strengthBelow": 3}, "annihilate", "pangjuan", None, "马陵道万弩俱发"),
        ],
        "史记·孙子吴起列传", "high"),
    mk_campaign(
        "julu", "巨鹿之战", "前207", "项羽率楚军渡河，破釜沉舟，九战九捷，击溃秦军主力章邯部；诸侯军膝行而前，项羽遂为诸侯上将军。",
        [115.10, 37.20], 70, 25,
        {"type": "annihilate", "target": "zhanghan", "side": "chu"},
        [{"id": "chu", "name": "楚(项羽)", "color": "#C23B2E"}, {"id": "qin", "name": "秦", "color": "#4C4C4C"}],
        [
            ("xiangyu", "项羽", "chu", [114.90, 37.30], 12, "elite", 1, "high", "advance", [115.20, 37.15]),
            ("blackhorse", "英布", "chu", [115.00, 37.05], 6, "cavalry", 0, "high", "flank"),
            ("zhanghan", "章邯", "qin", [115.40, 37.25], 11, "regular", 2, "steady", "hold"),
            ("sujiao", "王离", "qin", [115.30, 37.35], 7, "regular", 1, "steady", "hold"),
        ],
        [
            ({"turn": 2}, "moraleChange", "xiangyu", "high", "破釜沉舟，士卒必死"),
            ({"turn": 12}, "moraleChange", "sujiao", "broken", "甬道被断，王离被围"),
            ({"army": "zhanghan", "strengthBelow": 4}, "surrender", "zhanghan", None, "章邯降楚"),
        ],
        "史记·项羽本纪/史记·秦本纪", "high"),
    mk_campaign(
        "baideng", "白登之围", "前200", "汉高祖击匈奴，冒顿单于纵精骑围汉军于白登七日；陈平出奇计解围，汉初自此行和亲。",
        [113.80, 40.20], 50, 12,
        {"type": "escape", "side": "han"},
        [{"id": "han", "name": "汉", "color": "#4C6E9A"}, {"id": "xiongnu", "name": "匈奴", "color": "#8A6A5C"}],
        [
            ("gaozu", "汉高祖", "han", [113.70, 40.30], 9, "regular", 2, "wavering", "hold"),
            ("chenping", "陈平", "han", [113.80, 40.15], 4, "elite", 1, "steady", "support"),
            ("modu", "冒顿单于", "xiongnu", [114.10, 40.20], 11, "cavalry", 0, "high", "advance", [113.75, 40.25]),
        ],
        [
            ({"turn": 5}, "supplyCut", "gaozu", None, "七日不得食"),
            ({"turn": 10}, "escape", "gaozu", None, "陈平赂阏氏，围开一角"),
        ],
        "史记·匈奴列传/汉书", "medium"),
    mk_campaign(
        "kunyang", "昆阳之战", "23", "王莽四十二万大军围昆阳；刘秀率十三骑出城求援，三千敢死队直冲中军，飓风大雨，新军崩溃。",
        [113.30, 33.20], 60, 10,
        {"type": "rout", "side": "xinhan"},
        [{"id": "xinhan", "name": "绿林(刘秀)", "color": "#3C7A5C"}, {"id": "wangmang", "name": "新莽", "color": "#8B6914"}],
        [
            ("liuxiu", "刘秀", "xinhan", [113.10, 33.30], 8, "elite", 1, "high", "advance", [113.40, 33.15]),
            ("fengyi", "王莽中军", "wangmang", [113.50, 33.10], 13, "regular", 1, "steady", "hold"),
            ("xujun", "新军别部", "wangmang", [113.60, 33.25], 8, "militia", 2, "wavering", "hold"),
        ],
        [
            ({"turn": 4}, "moraleChange", "fengyi", "steady", "刘秀三千敢死冲其中坚"),
            ({"turn": 8}, "moraleChange", "wangmang", "broken", "大风雷雨，新军大溃"),
        ],
        "后汉书·光武帝纪", "high"),
    mk_campaign(
        "guandu", "官渡之战", "200", "曹操以少抗众，坚守官渡；许攸来降，曹操奇袭乌巢烧粮，袁军崩溃，奠定统一北方之基。",
        [114.30, 35.60], 70, 40,
        {"type": "rout", "side": "cao"},
        [{"id": "cao", "name": "曹操", "color": "#4C6E9A"}, {"id": "yuan", "name": "袁绍", "color": "#8A6A3E"}],
        [
            ("caocao", "曹操", "cao", [114.10, 35.65], 9, "elite", 2, "steady", "hold", [114.40, 35.55]),
            ("xunyou", "曹奇兵", "cao", [114.60, 35.70], 5, "cavalry", 0, "high", "flank"),
            ("yuanshao", "袁绍", "yuan", [114.70, 35.55], 12, "regular", 1, "steady", "advance", [114.20, 35.60]),
            ("ju_shou", "沮授", "yuan", [114.80, 35.65], 7, "regular", 1, "steady", "hold"),
        ],
        [
            ({"turn": 18}, "supplyCut", "yuan", None, "乌巢粮草被烧"),
            ({"turn": 26}, "moraleChange", "yuan", "broken", "张郃高览降曹，袁军大溃"),
        ],
        "三国志·魏书/资治通鉴", "high"),
    mk_campaign(
        "chibi", "赤壁之战", "208", "曹操南下，孙权刘备联军御于赤壁；黄盖诈降火攻，东风助势，曹军舟舰俱焚，败退北还，三分鼎足之势成。",
        [113.90, 29.72], 60, 18,
        {"type": "rout", "side": "sunliu"},
        [{"id": "sunliu", "name": "孙刘联军", "color": "#3C7A5C"}, {"id": "cao", "name": "曹操", "color": "#4C6E9A"}],
        [
            ("zhouyu", "周瑜", "sunliu", [113.70, 29.80], 9, "elite", 0, "high", "advance", [114.00, 29.68]),
            ("huanggai", "黄盖", "sunliu", [113.85, 29.65], 5, "regular", 0, "high", "flank"),
            ("liubei", "刘备", "sunliu", [113.55, 29.90], 6, "regular", 1, "steady", "support"),
            ("caocao", "曹操", "cao", [114.10, 29.75], 12, "regular", 2, "steady", "hold"),
        ],
        [
            ({"turn": 6}, "setIntent", "huanggai", "flank", "诈降接近曹船"),
            ({"turn": 8}, "fireAttack", "caocao", None, "黄盖火船乘风纵火"),
            ({"turn": 12}, "moraleChange", "cao", "broken", "疾疫兼火攻，曹军北撤"),
        ],
        "三国志·吴书/资治通鉴", "high"),
    mk_campaign(
        "yiling", "夷陵之战", "222", "刘备倾东州兵伐吴为关羽报仇；陆逊坚守不战，待蜀军疲敝，火攻连营七百里，蜀军大败，刘备退走白帝城。",
        [111.40, 30.80], 70, 30,
        {"type": "rout", "side": "wu"},
        [{"id": "wu", "name": "吴", "color": "#2E7D6B"}, {"id": "shu", "name": "蜀", "color": "#B33A2B"}],
        [
            ("luxun", "陆逊", "wu", [111.60, 30.90], 9, "elite", 1, "steady", "hold", [111.30, 30.70]),
            ("liubei", "刘备", "shu", [111.20, 30.70], 11, "regular", 2, "steady", "advance", [111.50, 30.85]),
            ("shufeng", "蜀前锋", "shu", [111.00, 30.90], 6, "militia", 2, "wavering", "hold"),
        ],
        [
            ({"turn": 15}, "fireAttack", "shu", None, "陆逊火攻，连营七百里俱焚"),
            ({"army": "liubei", "strengthBelow": 4}, "retreat", "liubei", None, "刘备退守白帝城"),
        ],
        "三国志·吴书/资治通鉴", "high"),
    mk_campaign(
        "hulao", "虎牢之战", "621", "李世民围王世充于洛阳，窦建德率十万夏军来援；李世民据虎牢关阻援，生擒窦建德，王世充出降。",
        [113.10, 34.80], 60, 30,
        {"type": "capture", "target": "doujiande", "side": "tang"},
        [{"id": "tang", "name": "唐(李世民)", "color": "#4C6E9A"}, {"id": "xia", "name": "夏(窦建德)", "color": "#8A6A3E"},
         {"id": "zheng", "name": "郑(王世充)", "color": "#8B5A3C"}],
        [
            ("shimin", "李世民", "tang", [113.00, 34.90], 10, "elite", 1, "high", "hold", [113.20, 34.75]),
            ("doujiande", "窦建德", "xia", [113.40, 34.70], 10, "regular", 1, "steady", "advance", [113.10, 34.80]),
            ("wangshichong", "王世充", "zheng", [112.50, 34.65], 6, "militia", 2, "wavering", "hold"),
        ],
        [
            ({"turn": 18}, "flank", "doujiande", None, "唐军骑冲夏阵北麓"),
            ({"army": "doujiande", "strengthBelow": 3}, "capture", "doujiande", None, "窦建德被俘"),
        ],
        "旧唐书·太宗本纪/资治通鉴", "high"),
    mk_campaign(
        "suyang", "睢阳保卫战", "757", "安史之乱中，张巡、许远以孤弱守睢阳十月，阻叛军南下江淮；城破殉国，江淮财赋赖以保全。",
        [116.38, 34.41], 45, 60,
        {"type": "defend", "target": "suyang", "side": "tang"},
        [{"id": "tang", "name": "唐(张巡)", "color": "#4C6E9A"}, {"id": "yan", "name": "燕(叛军)", "color": "#8B5A3C"}],
        [
            ("zhangxun", "张巡", "tang", [116.30, 34.45], 6, "regular", 3, "steady", "hold"),
            ("xuyuan", "许远", "tang", [116.45, 34.38], 4, "militia", 3, "steady", "hold"),
            ("yinziqi", "尹子奇", "yan", [116.70, 34.30], 10, "regular", 1, "steady", "advance", [116.35, 34.42]),
        ],
        [
            ({"turn": 20}, "supplyCut", "tang", None, "城中粮尽，雀鼠俱尽"),
            ({"turn": 55}, "defendFall", "tang", None, "城破，张巡许远殉国"),
        ],
        "旧唐书·张巡传/新唐书", "medium"),
    mk_campaign(
        "yancheng", "郾城大捷", "1140", "岳飞北伐，驻郾城；金兀术以铁浮屠、拐子马冲阵，岳飞步卒以麻札刀入阵，大破金军，进逼朱仙镇。",
        [113.98, 33.59], 45, 15,
        {"type": "rout", "side": "song"},
        [{"id": "song", "name": "宋(岳飞)", "color": "#3C7A5C"}, {"id": "jin", "name": "金", "color": "#4C6E9A"}],
        [
            ("yuefei", "岳飞", "song", [113.85, 33.65], 9, "elite", 1, "high", "advance", [114.10, 33.55]),
            ("wuzhu", "完颜兀术", "jin", [114.20, 33.50], 9, "cavalry", 1, "steady", "advance", [113.90, 33.60]),
        ],
        [
            ({"turn": 6}, "moraleChange", "jin", "wavering", "麻札刀破拐子马"),
            ({"turn": 10}, "rout", "jin", None, "兀术叹：撼山易，撼岳家军难"),
        ],
        "宋史·岳飞传/金史", "high"),
    mk_campaign(
        "caishi", "采石之战", "1161", "金主完颜亮大举南征，宋军虞允文至采石犒师，督水师以海鳅船冲敌舟，大破金军；完颜亮旋为部下所杀。",
        [118.48, 30.70], 40, 10,
        {"type": "rout", "side": "song"},
        [{"id": "song", "name": "宋", "color": "#3C7A5C"}, {"id": "jin", "name": "金", "color": "#4C6E9A"}],
        [
            ("yuyunwen", "虞允文", "song", [118.35, 30.75], 7, "regular", 0, "high", "hold", [118.55, 30.65]),
            ("wanyanliang", "完颜亮", "jin", [118.70, 30.60], 10, "regular", 1, "steady", "advance"),
        ],
        [
            ({"turn": 5}, "navalRout", "jin", None, "海鳅船冲入金军舟阵"),
            ({"turn": 9}, "moraleChange", "jin", "broken", "金军内讧，完颜亮被杀"),
        ],
        "宋史·虞允文传/金史", "medium"),
    mk_campaign(
        "xiangyang", "襄阳保卫战", "1267", "元军围襄阳、樊城六年，吕文德、吕文焕固守；元回回炮攻城，樊城破，襄阳力屈出降，南宋门户洞开。",
        [112.12, 32.01], 55, 120,
        {"type": "capture", "target": "lvwenhuan", "side": "yuan"},
        [{"id": "song", "name": "宋", "color": "#3C7A5C"}, {"id": "yuan", "name": "元", "color": "#7A5FA6"}],
        [
            ("lvwenhuan", "吕文焕", "song", [112.00, 32.10], 7, "regular", 3, "steady", "hold"),
            ("aoju", "阿术", "yuan", [112.40, 31.90], 10, "regular", 1, "steady", "advance", [112.05, 32.05]),
            ("alihaiya", "刘整", "yuan", [112.30, 32.20], 6, "cavalry", 1, "steady", "flank"),
        ],
        [
            ({"turn": 40}, "supplyCut", "song", None, "外援断绝，城中薪帛俱尽"),
            ({"turn": 100}, "siegeEngine", "song", None, "回回炮毁谯楼，樊城先陷"),
            ({"army": "lvwenhuan", "strengthBelow": 3}, "surrender", "lvwenhuan", None, "吕文焕以城降"),
        ],
        "宋史·吕文焕传/元史", "high"),
    mk_campaign(
        "poyang", "鄱阳湖之战", "1363", "陈友谅巨舰六十万围洪都，朱元璋率军入援；鄱阳湖水战，东北风起，朱军火攻，陈友谅中矢死，江南归朱。",
        [116.30, 29.10], 70, 35,
        {"type": "annihilate", "target": "chenyouliang", "side": "ming"},
        [{"id": "ming", "name": "朱元璋", "color": "#C23B2E"}, {"id": "chen", "name": "陈友谅", "color": "#4C6E9A"}],
        [
            ("zhuyuanzhang", "朱元璋", "ming", [116.00, 29.30], 9, "regular", 1, "high", "advance", [116.50, 29.00]),
            ("chenyouliang", "陈友谅", "chen", [116.60, 28.90], 12, "regular", 1, "steady", "hold"),
            ("chenjun", "陈巨舰", "chen", [116.70, 29.10], 8, "militia", 2, "steady", "hold"),
        ],
        [
            ({"turn": 15}, "fireAttack", "chen", None, "七艘火舟乘风冲巨舰"),
            ({"army": "chenyouliang", "strengthBelow": 3}, "annihilate", "chenyouliang", None, "陈友谅中流矢死"),
        ],
        "明史·太祖本纪/明太祖实录", "high"),
    mk_campaign(
        "salhu", "萨尔浒之战", "1619", "明四路出师会剿后金；努尔哈赤集中兵力，各个击破，五日之间三路明军溃败，后金由守转攻。",
        [123.70, 41.50], 80, 20,
        {"type": "rout", "side": "houjin"},
        [{"id": "houjin", "name": "后金", "color": "#8B6914"}, {"id": "ming", "name": "明", "color": "#4C6E9A"}],
        [
            ("nurhaci", "努尔哈赤", "houjin", [123.50, 41.60], 10, "cavalry", 0, "high", "advance", [123.80, 41.40]),
            ("daisu", "代善", "houjin", [123.30, 41.70], 6, "cavalry", 0, "high", "flank"),
            ("du_song", "杜松", "ming", [123.90, 41.50], 8, "regular", 1, "steady", "advance"),
            ("marlin", "马林", "ming", [123.20, 41.90], 6, "regular", 1, "wavering", "hold"),
            ("liu_ting", "刘綎", "ming", [124.30, 41.20], 6, "regular", 2, "steady", "hold"),
        ],
        [
            ({"turn": 5}, "rout", "du_song", None, "西路杜松兵败萨尔浒"),
            ({"turn": 12}, "rout", "marlin", None, "北路马林溃逃"),
            ({"turn": 18}, "rout", "liu_ting", None, "东路刘綎中伏战死"),
        ],
        "明史·杨镐传/清太祖实录", "medium"),
    ]

WORLD_CAMPAIGNS = [
    mk_campaign(
        "marathon", "马拉松战役", "前490", "波斯大流士渡海西侵，雅典重装步兵直扑波斯大营，以少胜多；斐力庇第斯奔归报捷，马拉松道由此得名。",
        [23.97, 38.15], 40, 12,
        {"type": "rout", "side": "athens"},
        [{"id": "athens", "name": "雅典", "color": "#3C6E9E"}, {"id": "persia", "name": "波斯", "color": "#8B6914"}],
        [
            ("miltiades", "米太亚德", "athens", [23.85, 38.20], 8, "elite", 0, "high", "advance", [24.05, 38.12]),
            ("datis", "达提斯", "persia", [24.10, 38.10], 9, "regular", 1, "steady", "hold"),
        ],
        [
            ({"turn": 6}, "rout", "persia", None, "希腊两翼合围，波斯阵溃上船"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    mk_campaign(
        "thermopylae", "温泉关战役", "前480", "列奥尼达率三百斯巴达勇士及希腊联军扼守狭窄山道，阻波斯大军三日；叛徒引路抄后，守军战死，主力得以撤退。",
        [22.53, 38.79], 30, 8,
        {"type": "defend", "target": "leonidas", "side": "greece"},
        [{"id": "greece", "name": "希腊联军", "color": "#3C6E9E"}, {"id": "persia", "name": "波斯", "color": "#8B6914"}],
        [
            ("leonidas", "列奥尼达", "greece", [22.45, 38.82], 5, "elite", 1, "high", "hold"),
            ("xerxes", "薛西斯", "persia", [22.70, 38.75], 12, "regular", 1, "steady", "advance", [22.50, 38.80]),
        ],
        [
            ({"turn": 5}, "flank", "greece", None, "波斯间道迂回山后"),
            ({"army": "leonidas", "strengthBelow": 2}, "annihilate", "leonidas", None, "三百勇士殿后战死"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    mk_campaign(
        "salamis", "萨拉米斯海战", "前480", "地米斯托克利诱波斯舰队驶入狭窄海湾，希腊三列桨战舰迂回撞击，波斯大船无法展开，舰队溃散。",
        [23.50, 37.95], 35, 10,
        {"type": "rout", "side": "greece"},
        [{"id": "greece", "name": "希腊联军", "color": "#3C6E9E"}, {"id": "persia", "name": "波斯", "color": "#8B6914"}],
        [
            ("themistocles", "地米斯托克利", "greece", [23.30, 38.00], 8, "regular", 0, "high", "advance", [23.60, 37.90]),
            ("artemisia", "阿尔特米西亚", "persia", [23.70, 37.90], 9, "regular", 1, "steady", "hold"),
        ],
        [
            ({"turn": 5}, "navalRout", "persia", None, "海湾狭窄，波斯舰自相撞沉"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    mk_campaign(
        "plataea", "普拉提亚战役", "前479", "希腊联军在普拉提亚击溃波斯陆军，继而海上米卡莱获胜，波斯彻底退出希腊。",
        [22.52, 38.23], 40, 12,
        {"type": "rout", "side": "greece"},
        [{"id": "greece", "name": "希腊联军", "color": "#3C6E9E"}, {"id": "persia", "name": "波斯", "color": "#8B6914"}],
        [
            ("pausanias", "保萨尼亚斯", "greece", [22.40, 38.30], 9, "elite", 1, "steady", "advance", [22.60, 38.18]),
            ("mardonius", "马铎尼斯", "persia", [22.70, 38.15], 10, "regular", 1, "steady", "hold"),
        ],
        [
            ({"turn": 8}, "rout", "persia", None, "波斯大营被焚，马铎尼斯阵亡"),
        ],
        f"{SRC_WP}/军事史公开数据", "low", world=True),
    mk_campaign(
        "gaugamela", "高加米拉会战", "前331", "亚历山大率马其顿东征，以斜形阵突破波斯大军中央；大流士三世溃逃，波斯帝国瓦解。",
        [43.43, 35.50], 60, 14,
        {"type": "rout", "side": "macedon"},
        [{"id": "macedon", "name": "马其顿", "color": "#7A5FA6"}, {"id": "persia", "name": "波斯", "color": "#8B6914"}],
        [
            ("alexander", "亚历山大", "macedon", [43.20, 35.60], 10, "elite", 1, "high", "advance", [43.60, 35.45]),
            ("darius", "大流士三世", "persia", [43.70, 35.40], 12, "regular", 1, "steady", "hold"),
        ],
        [
            ({"turn": 7}, "breakthrough", "persia", None, "近卫楔形阵直取大流士车驾"),
            ({"turn": 11}, "rout", "persia", None, "大流士弃军东逃"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    mk_campaign(
        "zama", "扎马会战", "前202", "大西庇阿与汉尼拔于扎马对阵；罗马骑兵得胜回击，汉尼拔军两翼崩溃，第二次布匿战争结束。",
        [9.30, 36.15], 45, 12,
        {"type": "rout", "side": "rome"},
        [{"id": "rome", "name": "罗马", "color": "#4C6E9A"}, {"id": "carthage", "name": "迦太基", "color": "#8B5A3C"}],
        [
            ("scipio", "大西庇阿", "rome", [9.10, 36.20], 9, "elite", 1, "high", "advance", [9.45, 36.10]),
            ("hannibal", "汉尼拔", "carthage", [9.50, 36.10], 10, "regular", 2, "steady", "hold"),
        ],
        [
            ({"turn": 6}, "flank", "carthage", None, "罗马骑兵回转冲击后翼"),
            ({"turn": 10}, "rout", "carthage", None, "迦太基方阵崩溃"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    mk_campaign(
        "actium", "亚克兴海战", "前31", "屋大维部阿格里帕与安东尼、克利奥帕特拉联军战于亚克兴海角；埃及舰队突围撤走，安东尼军投降。",
        [20.77, 38.95], 40, 10,
        {"type": "rout", "side": "octavian"},
        [{"id": "octavian", "name": "屋大维", "color": "#4C6E9A"}, {"id": "antony", "name": "安东尼/埃及", "color": "#8B5A3C"}],
        [
            ("agrippa", "阿格里帕", "octavian", [20.60, 39.00], 8, "regular", 0, "high", "advance", [20.90, 38.90]),
            ("antony", "安东尼", "antony", [20.95, 38.85], 9, "regular", 1, "steady", "hold"),
        ],
        [
            ({"turn": 6}, "navalRout", "antony", None, "埃及舰队突围东撤"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    mk_campaign(
        "teutoburg", "条顿堡森林战役", "9", "罗马瓦卢斯率三个军团深入日耳曼森林，阿米尼乌斯设伏，罗马军团全灭，罗马止步于莱茵河。",
        [8.50, 52.30], 50, 10,
        {"type": "annihilate", "target": "varus", "side": "germanic"},
        [{"id": "rome", "name": "罗马", "color": "#4C6E9A"}, {"id": "germanic", "name": "日耳曼部落", "color": "#6B8E3C"}],
        [
            ("varus", "瓦卢斯", "rome", [8.30, 52.40], 9, "regular", 2, "wavering", "advance", [8.70, 52.20]),
            ("arminius", "阿米尼乌斯", "germanic", [8.80, 52.25], 7, "elite", 0, "high", "ambush"),
        ],
        [
            ({"turn": 4}, "ambush", "varus", None, "森林泥沼中伏，军团阵形瓦解"),
            ({"army": "varus", "strengthBelow": 3}, "annihilate", "varus", None, "三军团覆没，瓦卢斯自杀"),
        ],
        f"{SRC_WP}/军事史公开数据", "low", world=True),
    mk_campaign(
        "hastings", "黑斯廷斯战役", "1066", "诺曼底公爵威廉渡海登陆，与哈罗德·戈德温森英军战于黑斯廷斯；英军盾阵久持，威廉佯退诱敌，哈罗德中箭身亡。",
        [0.49, 50.91], 40, 12,
        {"type": "capture", "target": "harold", "side": "norman"},
        [{"id": "norman", "name": "诺曼", "color": "#8B6914"}, {"id": "saxon", "name": "盎格鲁撒克逊", "color": "#4C6E9A"}],
        [
            ("william", "威廉", "norman", [0.30, 50.95], 9, "elite", 1, "high", "advance", [0.60, 50.88]),
            ("harold", "哈罗德", "saxon", [0.65, 50.85], 8, "regular", 2, "steady", "hold"),
        ],
        [
            ({"turn": 7}, "feignedRetreat", "saxon", None, "诺曼骑兵佯退，盾阵追击散开"),
            ({"army": "harold", "strengthBelow": 3}, "capture", "harold", None, "哈罗德中箭阵亡"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    mk_campaign(
        "manzikert", "曼齐刻尔特战役", "1071", "塞尔柱突厥阿尔普·阿尔斯兰与拜占庭罗曼努斯四世会战；拜占庭中军被迂回，皇帝被俘，安纳托利亚门户洞开。",
        [42.05, 38.95], 45, 12,
        {"type": "capture", "target": "romanus", "side": "seljuk"},
        [{"id": "byzantium", "name": "拜占庭", "color": "#7A5FA6"}, {"id": "seljuk", "name": "塞尔柱", "color": "#8B6914"}],
        [
            ("romanus", "罗曼努斯四世", "byzantium", [41.80, 39.00], 9, "regular", 1, "steady", "advance", [42.20, 38.90]),
            ("alp", "阿尔普·阿尔斯兰", "seljuk", [42.30, 38.85], 8, "cavalry", 0, "high", "flank"),
        ],
        [
            ({"turn": 8}, "flank", "byzantium", None, "突厥骑射包抄两翼"),
            ({"army": "romanus", "strengthBelow": 3}, "capture", "romanus", None, "皇帝负伤被俘"),
        ],
        f"{SRC_WP}/军事史公开数据", "low", world=True),
    mk_campaign(
        "hattin", "哈丁战役", "1187", "萨拉丁围困十字军于哈丁角，无水高地；十字军干渴溃散，耶路撒冷王被俘，萨拉丁收复耶路撒冷。",
        [35.35, 32.85], 35, 10,
        {"type": "annihilate", "target": "guy", "side": "ayyubid"},
        [{"id": "ayyubid", "name": "阿尤布", "color": "#3C7A5C"}, {"id": "crusader", "name": "十字军", "color": "#8B5A3C"}],
        [
            ("saladin", "萨拉丁", "ayyubid", [35.20, 32.90], 9, "regular", 0, "high", "advance", [35.45, 32.80]),
            ("guy", "居伊", "crusader", [35.50, 32.80], 7, "elite", 2, "wavering", "hold"),
        ],
        [
            ({"turn": 4}, "supplyCut", "crusader", None, "水井被占，十字军干渴"),
            ({"army": "guy", "strengthBelow": 3}, "capture", "guy", None, "耶路撒冷王被俘"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    mk_campaign(
        "waterloo", "滑铁卢战役", "1815", "拿破仑复出后率法军对阵威灵顿英荷联军；布吕歇尔普军侧翼赶到，法军全线崩溃，百日王朝终结。",
        [4.40, 50.68], 50, 18,
        {"type": "rout", "side": "allied"},
        [{"id": "france", "name": "法兰西", "color": "#4C6E9A"}, {"id": "allied", "name": "英荷普联军", "color": "#3C7A5C"}],
        [
            ("napoleon", "拿破仑", "france", [4.20, 50.72], 10, "elite", 2, "steady", "advance", [4.55, 50.65]),
            ("wellington", "威灵顿", "allied", [4.60, 50.62], 9, "regular", 1, "steady", "hold"),
            ("blucher", "布吕歇尔", "allied", [4.00, 50.80], 7, "cavalry", 1, "steady", "flank"),
        ],
        [
            ({"turn": 10}, "flank", "france", None, "普军从右翼赶到夹击"),
            ({"turn": 15}, "rout", "france", None, "法军 Old Guard 溃散"),
        ],
        f"{SRC_WP}/军事史公开数据", "medium", world=True),
    ]

# ----------------------------------------------------------------------------
# 预设路线：路径点串 [lat, lng]；契约字段 名称/年代/路径/出处 + source/confidence
# ----------------------------------------------------------------------------

WP = {
    # 中原北方
    "北京": [39.90, 116.40], "天津": [39.13, 117.20], "济南": [36.65, 117.00],
    "徐州": [34.26, 117.18], "南京": [32.05, 118.78], "扬州": [32.39, 119.42],
    "苏州": [31.30, 120.62], "杭州": [30.27, 120.15], "西安": [34.34, 108.94],
    "洛阳": [34.62, 112.45], "开封": [34.80, 114.31], "郑州": [34.75, 113.62],
    "成都": [30.57, 104.07], "重庆": [29.56, 106.55], "武汉": [30.55, 114.30],
    "长沙": [28.23, 112.94], "广州": [23.13, 113.26], "桂林": [25.27, 110.29],
    "昆明": [25.04, 102.71], "大理": [25.59, 100.23], "沈阳": [41.80, 123.43],
    "大同": [40.08, 113.30], "太原": [37.87, 112.55], "银川": [38.47, 106.27],
    "兰州": [36.06, 103.83], "武威": [37.93, 102.64], "张掖": [38.93, 100.45],
    "酒泉": [39.73, 98.51], "敦煌": [40.14, 94.66], "楼兰": [40.50, 89.50],
    "吐鲁番": [42.95, 89.18], "喀什": [39.47, 75.99], "和田": [37.11, 79.92],
    "西宁": [36.62, 101.78], "拉萨": [29.65, 91.14], "汉中": [33.07, 107.02],
    "襄阳": [32.01, 112.12], "南阳": [33.00, 112.53], "合肥": [31.82, 117.27],
    "南昌": [28.68, 115.86], "福州": [26.07, 119.30], "泉州": [24.93, 118.58],
    "绍兴": [30.00, 120.58], "宁波": [29.87, 121.55], "曲阜": [35.60, 116.99],
    "临淄": [36.86, 118.31], "邯郸": [36.60, 114.48], "安阳": [36.10, 114.35],
    "平遥": [37.19, 112.18], "凤翔": [34.52, 107.39], "天水": [34.58, 105.72],
    "固原": [36.00, 106.28], "榆林": [38.29, 109.73], "包头": [40.66, 109.84],
    "辽阳": [41.25, 123.73], "平壤": [39.03, 125.75], "首尔": [37.57, 126.98],
    "京都": [35.01, 135.77], "雷州": [20.91, 110.08], "海口": [20.04, 110.32],
    "贵阳": [26.58, 106.71], "遵义": [27.73, 106.93], "衡阳": [26.90, 112.61],
    "岳阳": [29.37, 113.13], "九江": [29.70, 116.00], "镇江": [32.20, 119.43],
    "淮安": [33.61, 119.02], "临清": [36.85, 115.70], "沧州": [38.30, 116.84],
    "德州": [37.44, 116.36], "烟台": [37.46, 121.45], "大连": [38.91, 121.61],
    "锦州": [41.10, 121.13], "山海关": [40.00, 119.75], "延安": [36.59, 109.49],
    "韩城": [35.48, 110.45], "运城": [35.03, 111.00], "临汾": [36.09, 111.52],
    "商丘": [34.41, 115.66], "许昌": [34.03, 113.85], "信阳": [32.13, 114.07],
    "安庆": [30.52, 117.05], "赣州": [25.83, 114.93], "梅州": [24.29, 116.12],
    "潮州": [23.66, 116.62], "南宁": [22.82, 108.37], "河内": [21.03, 105.85],
    "丽江": [26.86, 100.23], "广元": [32.44, 105.84], "绵阳": [31.47, 104.68],
    "宜宾": [28.75, 104.64], "泸州": [28.87, 105.44], "碎叶": [42.87, 75.30],
    "撒马尔罕": [39.65, 66.96], "布哈拉": [39.77, 64.42],
    "孟津": [34.80, 112.60], "城濮": [35.40, 115.40], "长垣": [35.20, 114.65],
    "大名": [36.28, 115.15], "三门峡": [34.77, 111.20], "中牟": [34.72, 113.85],
    "荆州": [30.35, 112.19], "宜昌": [30.69, 111.29], "宜都": [30.38, 111.45],
    "寿县": [32.58, 116.78], "宿州": [33.65, 116.98], "鄂州": [30.39, 114.89],
    "郾城": [33.59, 113.98], "滁州": [32.30, 118.32], "马鞍山": [31.67, 118.50],
    "鄱阳湖": [29.10, 116.30], "抚顺": [41.88, 123.96], "萨尔浒": [41.90, 123.70],
    "雅安": [29.98, 103.00], "康定": [30.05, 101.96], "香格里拉": [27.83, 99.70],
    "普洱": [22.78, 100.97], "舟山": [30.00, 122.20], "福冈": [33.59, 130.40],
    "长崎": [32.75, 129.87], "马尼拉": [14.60, 120.98], "濮阳": [35.73, 115.05],
    "宝鸡": [34.36, 107.14], "商洛": [33.87, 109.94], "咸阳": [34.33, 108.71],
    "虎牢": [34.75, 113.10], "荆门": [31.03, 112.20], "黄州": [30.45, 114.87],
    "庐山": [29.58, 115.98], "玉树": [33.00, 97.01], "荥阳": [34.79, 113.38],
    "石家庄": [38.04, 114.51], "平顶山": [33.77, 113.19], "江陵": [30.35, 112.19],
    "惠州": [23.11, 114.42], "淄博": [36.86, 118.05], "赤壁": [29.72, 113.90],
    # 世界
    "Rome": [41.90, 12.50], "Athens": [37.98, 23.73], "Constantinople": [41.01, 28.98],
    "Jerusalem": [31.78, 35.22], "Damascus": [33.51, 36.29], "Cairo": [30.04, 31.24],
    "Alexandria": [31.20, 29.92], "Carthage": [36.85, 10.32], "Palermo": [38.12, 13.36],
    "Syracuse": [37.07, 15.29], "Venice": [45.44, 12.33], "Genoa": [44.41, 8.93],
    "Marseille": [43.29, 5.37], "Paris": [48.86, 2.35], "London": [51.51, -0.13],
    "Amsterdam": [52.37, 4.90], "Cologne": [50.94, 6.96], "Vienna": [48.21, 16.37],
    "Prague": [50.08, 14.44], "Berlin": [52.52, 13.40], "Warsaw": [52.23, 21.01],
    "Moscow": [55.76, 37.62], "Kiev": [50.45, 30.52], "Novgorod": [58.52, 31.27],
    "Baghdad": [33.31, 44.37], "Persepolis": [29.93, 52.89], "Delhi": [28.61, 77.21],
    "Agra": [27.18, 78.02], "Calcutta": [22.57, 88.36], "Malacca": [2.19, 102.25],
    "Calicut": [11.25, 75.78], "Goa": [15.50, 73.83], "Hormuz": [27.18, 56.27],
    "Mecca": [21.42, 39.83], "Tunis": [36.80, 10.18], "Cordoba": [37.89, -4.78],
    "Lisbon": [38.72, -9.14], "Seville": [37.39, -5.99], "Naples": [40.85, 14.27],
    "Milan": [45.46, 9.19], "Florence": [43.77, 11.25], "York": [53.96, -1.08],
    "Canterbury": [51.28, 1.08], "Bruges": [51.21, 3.22], "Lübeck": [53.87, 10.69],
    "Gdansk": [54.35, 18.65], "Riga": [56.95, 24.11], "Tallinn": [59.44, 24.75],
    "Stockholm": [59.33, 18.07], "Antioch": [36.20, 36.16], "Ephesus": [37.94, 27.34],
    "Troy": [39.96, 26.24], "Sparta": [37.07, 22.43], "Crete": [35.34, 25.13],
    "Rhodes": [36.43, 28.22], "Memphis": [29.85, 31.25], "Luxor": [25.70, 32.64],
    "Aswan": [24.09, 32.90], "Babylon": [32.54, 44.42], "Susa": [32.19, 48.26],
    "Kabul": [34.53, 69.17], "Patna": [25.60, 85.14], "Varanasi": [25.32, 82.97],
    "Muscat": [23.61, 58.59], "Marrakech": [31.63, -8.01], "Gao": [16.27, -0.05],
    "Budapest": [47.50, 19.04], "Belgrade": [44.82, 20.46],
    "Bukhara": [39.77, 64.42], "Samarkand": [39.65, 66.96],
    "Hamburg": [53.55, 9.99], "Medina": [24.47, 39.61],
}

# (名称, 年代, [点位], 出处, source, confidence)
ROUTE_SPECS = [
    # —— 丝路 ——
    ("西汉丝路·关中至河西", "前2世纪", ["西安", "天水", "兰州", "武威", "张掖", "酒泉"], "《史记·大宛列传》/《汉书·西域传》", SRC_TAN, "medium"),
    ("西汉丝路·河西至西域南道", "前2世纪", ["敦煌", "和田", "喀什"], "《汉书·西域传》/谭其骧历史地图集", SRC_TAN, "medium"),
    ("西汉丝路·西域北道", "前1世纪", ["敦煌", "楼兰", "吐鲁番", "喀什"], "《汉书·西域传》/CHGIS", f"{SRC_CHGIS}/{SRC_TAN}", "medium"),
    ("张骞第一次出使", "前138", ["西安", "固原", "武威", "酒泉", "吐鲁番", "撒马尔罕"], "《史记·大宛列传》", f"{SRC_TAN}/{SRC_WP}", "medium"),
    ("玄奘西行求法", "629—645", ["西安", "武威", "敦煌", "吐鲁番", "碎叶", "撒马尔罕", "Kabul", "Patna"], "《大唐西域记》/《大慈恩寺三藏法师传》", f"{SRC_TAN}/{SRC_WP}", "high"),
    ("班超经营西域", "73—102", ["洛阳", "敦煌", "楼兰", "和田", "喀什"], "《后汉书·班超传》", SRC_TAN, "medium"),
    ("吐蕃—尼婆罗道", "7世纪", ["拉萨", "丽江", "Kabul"], "《新唐书·吐蕃传》/考古通道", SRC_CTEXT, "low"),
    # —— 大运河 ——
    ("大运河·永济渠段", "7世纪", ["北京", "天津", "沧州", "德州", "临清"], "《隋书·食货志》/谭其骧历史地图集", SRC_TAN, "medium"),
    ("大运河·通济渠段", "7世纪", ["洛阳", "开封", "商丘", "徐州", "淮安"], "《隋书·食货志》", SRC_TAN, "medium"),
    ("大运河·山阳渎段", "7世纪", ["淮安", "扬州", "镇江"], "《读史方舆纪要》", SRC_CTEXT, "low"),
    ("大运河·江南河段", "7世纪", ["镇江", "苏州", "绍兴", "杭州"], "《宋史·河渠志》", SRC_TAN, "medium"),
    ("大运河·全线", "13世纪", ["北京", "天津", "临清", "徐州", "淮安", "扬州", "镇江", "苏州", "杭州"], "《元史·河渠志》", f"{SRC_TAN}/{SRC_CHGIS}", "medium"),
    ("邗沟（吴故运河）", "前486", ["扬州", "淮安"], "《左传·哀公九年》", SRC_CTEXT, "low"),
    # —— 秦汉驰道驿道 ——
    ("秦驰道·东方道", "前220", ["咸阳", "洛阳", "开封", "徐州", "临淄"], "《史记·秦始皇本纪》/《汉书·贾山传》", SRC_TAN, "medium"),
    ("秦直道·北边道", "前212", ["咸阳", "延安", "榆林", "包头"], "《史记·秦始皇本纪》/考古调查", f"{SRC_ARCH}/{SRC_TAN}", "medium"),
    ("秦驰道·燕齐道", "前220", ["咸阳", "邯郸", "北京", "沈阳"], "《史记》/谭其骧历史地图集", SRC_TAN, "low"),
    ("武关道·关中至南阳", "战国", ["西安", "商洛", "南阳"], "《史记·高祖本纪》", SRC_CTEXT, "low"),
    ("蜀道·褒斜道", "战国", ["汉中", "宝鸡", "西安"], "《读史方舆纪要》/栈道考古", f"{SRC_TAN}/{SRC_ARCH}", "medium"),
    ("蜀道·金牛道", "战国", ["汉中", "广元", "绵阳", "成都"], "《华阳国志》/谭其骧历史地图集", SRC_TAN, "medium"),
    ("长江上游航道", "战国", ["成都", "泸州", "重庆", "岳阳", "武汉"], "《史记·楚世家》/长江航道史", SRC_CTEXT, "low"),
    ("岭南道·五岭道", "前214", ["南阳", "襄阳", "长沙", "衡阳", "桂林", "广州"], "《淮南子·人间训》/《史记》", SRC_TAN, "medium"),
    ("晋北通道", "春秋", ["邯郸", "太原", "平遥", "大同"], "《左传》/谭其骧历史地图集", SRC_TAN, "low"),
    ("河东道", "春秋", ["临汾", "运城", "韩城", "西安"], "《左传》/CHGIS", f"{SRC_CHGIS}/{SRC_TAN}", "medium"),
    ("辽西走廊", "战国", ["北京", "山海关", "锦州", "沈阳", "辽阳"], "《史记·匈奴列传》", SRC_TAN, "medium"),
    ("河西支线", "西汉", ["兰州", "固原", "武威"], "《汉书·地理志》", SRC_CHGIS, "medium"),
    # —— 战事进军 ——
    ("武王伐纣", "前1046", ["西安", "孟津", "新乡"], "《史记·周本纪》/《尚书·牧誓》", SRC_TAN, "medium"),
    ("晋楚城濮进军", "前632", ["太原", "邯郸", "濮阳", "城濮"], "《左传·僖公二十八年》", SRC_CTEXT, "low"),
    ("秦军袭郑崤山回师", "前627", ["西安", "洛阳", "三门峡"], "《左传·僖公三十三年》", SRC_CTEXT, "low"),
    ("齐魏桂陵驰援", "前354", ["淄博", "邯郸", "长垣"], "《史记·孙子吴起列传》", SRC_TAN, "low"),
    ("齐魏马陵追击", "前341", ["淄博", "濮阳", "大名"], "《史记·孙子吴起列传》", SRC_TAN, "low"),
    ("项羽巨鹿进军", "前207", ["徐州", "商丘", "开封", "巨鹿"], "《史记·项羽本纪》", SRC_TAN, "medium"),
    ("楚汉荥阳成皋战线", "前205—前203", ["郑州", "荥阳", "洛阳", "开封"], "《史记·高祖本纪》", SRC_TAN, "medium"),
    ("韩信北伐", "前205—前202", ["西安", "韩城", "太原", "石家庄", "淄博"], "《史记·淮阴侯列传》", SRC_TAN, "medium"),
    ("刘秀昆阳驰援", "23", ["许昌", "平顶山", "南阳"], "《后汉书·光武帝纪》", SRC_TAN, "low"),
    ("曹操官渡进兵", "200", ["许昌", "郑州", "中牟"], "《三国志·魏书》", SRC_TAN, "medium"),
    ("赤壁之战南下", "208", ["许昌", "南阳", "襄阳", "江陵", "赤壁"], "《三国志·吴书》", SRC_TAN, "medium"),
    ("刘备入蜀", "211—214", ["荆州", "重庆", "绵阳", "成都"], "《三国志·蜀书》", SRC_TAN, "medium"),
    ("夷陵进军", "222", ["成都", "重庆", "宜昌", "宜都"], "《三国志·吴书》", SRC_TAN, "medium"),
    ("淝水之战秦军南下", "383", ["西安", "洛阳", "商丘", "寿县", "合肥"], "《晋书·苻坚载记》", SRC_TAN, "medium"),
    ("唐军虎牢阻援", "621", ["西安", "洛阳", "虎牢"], "《旧唐书·太宗本纪》", SRC_TAN, "medium"),
    ("张巡守睢阳粮道", "757", ["徐州", "商丘", "宿州"], "《旧唐书·张巡传》", SRC_CTEXT, "low"),
    ("岳飞北伐郾城", "1140", ["鄂州", "襄阳", "许昌", "郾城"], "《宋史·岳飞传》", SRC_TAN, "medium"),
    ("采石矶江防", "1161", ["合肥", "滁州", "马鞍山"], "《宋史·虞允文传》", SRC_CTEXT, "low"),
    ("襄阳围城援道", "1267—1273", ["南阳", "襄阳", "荆门"], "《宋史·吕文焕传》", SRC_TAN, "low"),
    ("朱元璋鄱阳湖迎战", "1363", ["南京", "安庆", "九江", "鄱阳湖"], "《明史·太祖本纪》", SRC_TAN, "medium"),
    ("后金萨尔浒进军", "1619", ["沈阳", "抚顺", "萨尔浒"], "《明史·杨镐传》", SRC_TAN, "low"),
    # —— 行旅与出使 ——
    ("李白出蜀", "725", ["成都", "绵阳", "广元", "重庆", "宜昌", "武汉"], "《李太白全集》系年", SRC_CTEXT, "low"),
    ("杜甫秦蜀行", "759", ["西安", "天水", "绵阳", "成都"], "《杜诗详注》系年", SRC_CTEXT, "medium"),
    ("苏轼南迁", "1080—1097", ["开封", "杭州", "黄州", "惠州", "海口"], "《东坡先生年谱》", SRC_CTEXT, "medium"),
    ("司马迁游历", "前126—前110", ["韩城", "西安", "曲阜", "临淄", "徐州", "长沙"], "《史记·太史公自序》", SRC_TAN, "medium"),
    ("鉴真东渡", "743—753", ["扬州", "苏州", "宁波", "京都"], "《唐大和上东征传》", SRC_TAN, "medium"),
    ("郑和下西洋·船队航线", "1405—1433", ["南京", "苏州", "福州", "泉州", "广州", "Malacca", "Calicut", "Mecca"], "《明史·郑和传》/《郑和航海图》", f"{SRC_TAN}/{SRC_WP}", "medium"),
    ("白居易江州至杭州", "815—822", ["九江", "杭州"], "《白氏长庆集》系年", SRC_CTEXT, "low"),
    ("陆游入蜀", "1170", ["绍兴", "杭州", "九江", "荆州", "汉中", "成都"], "《剑南诗稿》/《入蜀记》", SRC_CTEXT, "medium"),
    # —— 佛教传播 ——
    ("佛教东传·河西道", "2—4世纪", ["敦煌", "武威", "兰州", "西安", "洛阳"], "《洛阳伽蓝记》/汤用彤《汉魏两晋南北朝佛教史》", SRC_TAN, "medium"),
    ("佛教南渡·建康道", "4世纪", ["洛阳", "襄阳", "南京", "庐山"], "《高僧传》", SRC_CTEXT, "low"),
    ("藏传佛教·唐蕃道", "7—9世纪", ["拉萨", "西宁", "兰州", "西安"], "《新唐书·吐蕃传》", SRC_TAN, "low"),
    ("普陀山香花道", "10—13世纪", ["宁波", "舟山"], "《普陀山志》", SRC_CTEXT, "low"),
    # —— 茶马古道 ——
    ("茶马古道·川藏线", "唐宋", ["成都", "雅安", "康定", "拉萨"], "《西藏志》/茶道考古", f"{SRC_TAN}/{SRC_ARCH}", "medium"),
    ("茶马古道·滇藏线", "唐宋", ["大理", "丽江", "香格里拉", "拉萨"], "《滇海虞衡志》", SRC_CTEXT, "low"),
    ("茶马古道·滇川线", "唐宋", ["昆明", "大理", "丽江", "宜宾", "成都"], "《云南通志》", SRC_CTEXT, "low"),
    ("普洱茶马道", "清", ["普洱", "大理", "丽江"], "《普洱府志》", SRC_CTEXT, "low"),
    ("茶马古道·蕃尼道", "清", ["拉萨", "Kabul"], "《清实录·藩部》", SRC_CTEXT, "low"),
    ("康青道", "清", ["康定", "玉树", "西宁"], "《西宁府新志》", SRC_CTEXT, "low"),
    # —— 海上丝路 ——
    ("南海丝路·北线", "唐", ["广州", "泉州", "宁波", "福州"], "《新唐书·地理志》/贾耽入四夷路", SRC_TAN, "medium"),
    ("南海丝路·中南半岛线", "宋", ["广州", "雷州", "河内", "Malacca"], "《诸蕃志》/《岭外代答》", SRC_TAN, "medium"),
    ("南海丝路·印度洋线", "宋", ["广州", "Malacca", "Calicut", "Goa"], "《诸蕃志》/《岛夷志略》", f"{SRC_TAN}/{SRC_WP}", "medium"),
    ("中日遣唐使南路", "8—9世纪", ["宁波", "苏州", "京都"], "《续日本纪》/《入唐求法巡礼行记》", SRC_TAN, "medium"),
    ("明州—博多航路", "宋", ["宁波", "福冈"], "《宋会要辑稿·市舶》", SRC_CTEXT, "low"),
    ("泉州—吕宋航路", "明", ["泉州", "马尼拉"], "《东西洋考》", SRC_CTEXT, "low"),
    ("广州—锡兰—阿拉伯线", "唐", ["广州", "Malacca", "Calicut", "Hormuz", "Baghdad"], "《新唐书·西域传》/《酉阳杂俎》", SRC_TAN, "low"),
    ("扬州—日本博多线", "唐", ["扬州", "宁波", "长崎"], "《入唐求法巡礼行记》", SRC_CTEXT, "low"),
    # —— 世界路线 ——
    ("罗马大道·罗马至米兰", "1—2世纪", ["Rome", "Florence", "Milan"], "Tabula Peutingeriana/军事史公开数据", SRC_WP, "medium"),
    ("罗马大道·罗马至那不勒斯", "1—2世纪", ["Rome", "Naples"], "罗马古道考古", SRC_WP, "low"),
    ("罗马大道·罗马至威尼斯", "1—2世纪", ["Rome", "Florence", "Venice"], "Tabula Peutingeriana", SRC_WP, "low"),
    ("罗马大道·跨海至迦太基", "前2—2世纪", ["Rome", "Carthage"], "波利比乌斯《通史》", SRC_WP, "medium"),
    ("罗马大道·东方干线", "1—2世纪", ["Rome", "Constantinople", "Antioch", "Jerusalem"], "罗马驿道考古/《 itinerarium 》", SRC_WP, "medium"),
    ("罗马—希腊—拜占庭道", "前1世纪", ["Rome", "Athens", "Constantinople"], "普鲁塔克/军事史公开数据", SRC_WP, "low"),
    ("罗马大道·高卢不列颠道", "1—2世纪", ["Rome", "Milan", "Paris", "London"], "罗马不列颠考古", SRC_WP, "medium"),
    ("希腊海路·爱琴海环线", "前8—前5世纪", ["Athens", "Crete", "Rhodes", "Ephesus", "Troy"], "希罗多德《历史》", SRC_WP, "low"),
    ("亚历山大东征·希腊至波斯", "前334—前330", ["Athens", "Troy", "Babylon", "Persepolis"], "阿里安《亚历山大远征记》", SRC_WP, "medium"),
    ("亚历山大东征·中亚印度", "前329—前326", ["Persepolis", "Bukhara", "Samarkand", "Kabul", "Patna"], "阿里安《亚历山大远征记》", SRC_WP, "medium"),
    ("十字军·君士坦丁堡至耶路撒冷", "1096—1099", ["Constantinople", "Antioch", "Jerusalem"], "《十字军史》/William of Tyre", SRC_WP, "medium"),
    ("威尼斯十字军海路", "1202—1204", ["Venice", "Constantinople"], "Geoffroi de Villehardouin 回忆录", SRC_WP, "low"),
    ("十字军·西欧集结道", "1096", ["Paris", "Rome", "Venice", "Constantinople"], "十字军编年史", SRC_WP, "low"),
    ("十字军·地中海补给线", "11—12世纪", ["Marseille", "Genoa", "Rhodes", "Jerusalem"], "意大利城邦档案", SRC_WP, "low"),
    ("汉萨商路·西线", "13—15世纪", ["Lübeck", "Hamburg", "Bruges", "London"], "汉萨同盟档案", SRC_WP, "medium"),
    ("汉萨商路·东线", "13—15世纪", ["Lübeck", "Riga", "Tallinn", "Novgorod"], "汉萨同盟档案", SRC_WP, "medium"),
    ("汉萨商路·南路", "14—15世纪", ["Gdansk", "Warsaw", "Prague", "Vienna"], "汉萨商路研究", SRC_WP, "low"),
    ("英格兰本土道", "11—14世纪", ["London", "York", "Canterbury"], "英国中世纪道路研究", SRC_WP, "low"),
    ("丝路西段·波斯至中亚", "1—8世纪", ["Baghdad", "Persepolis", "Bukhara", "Samarkand"], "《厄立特里亚海航行记》/波斯史料", SRC_WP, "low"),
    ("丝路西段·中亚至印度", "1—7世纪", ["Samarkand", "Kabul", "Delhi", "Agra"], "玄奘《大唐西域记》/印度史料", f"{SRC_TAN}/{SRC_WP}", "medium"),
    ("罗斯草原道", "9—13世纪", ["Kiev", "Novgorod", "Moscow"], "《往年纪事》", SRC_WP, "low"),
    ("蒙古西征·中亚波斯道", "1219—1224", ["Samarkand", "Bukhara", "Persepolis", "Baghdad"], "《世界征服者史》/《史集》", SRC_WP, "medium"),
    ("大航海·葡萄牙印度洋线", "15世纪", ["Lisbon", "Goa", "Calicut", "Malacca", "广州"], "达伽马航行档案/《马可波罗行纪》注", SRC_WP, "medium"),
    ("大航海·西班牙地中海线", "15—16世纪", ["Seville", "Cordoba", "Lisbon"], "西班牙王室档案", SRC_WP, "low"),
    ("威尼斯—黎凡特商路", "13—15世纪", ["Venice", "Alexandria", "Cairo", "Damascus"], "威尼斯共和国档案", SRC_WP, "medium"),
    ("热那亚—马赛商路", "12—14世纪", ["Genoa", "Marseille", "Palermo"], "意大利城邦档案", SRC_WP, "low"),
    ("香料航路·波斯湾线", "13—16世纪", ["Malacca", "Calicut", "Goa", "Hormuz", "Baghdad"], "《海道针经》/葡萄牙档案", SRC_WP, "low"),
    ("红海朝觐商路", "7—16世纪", ["Cairo", "Mecca", "Medina"], "阿拉伯地理文献", SRC_WP, "low"),
    ("伊斯兰东西道", "8—10世纪", ["Damascus", "Baghdad", "Mecca"], "《黄金草原》/阿拉伯驿道志", SRC_WP, "low"),
    ("两河古行道", "前3千纪", ["Babylon", "Susa", "Persepolis"], "楔形文字文书/考古", SRC_WP, "low"),
    ("尼罗河上行道", "前3千纪", ["Alexandria", "Memphis", "Luxor", "Aswan"], "古埃及铭文/考古", SRC_WP, "medium"),
    ("斯基泰草原迁徙道", "前8—前3世纪", ["Kabul", "Samarkand", "Bukhara"], "希罗多德《历史》/考古", SRC_WP, "low"),
    ("波罗的汉萨—诺夫哥罗德道", "14世纪", ["Tallinn", "Riga", "Novgorod"], "汉萨档案", SRC_WP, "low"),
    ("多瑙河商路", "2—15世纪", ["Vienna", "Budapest", "Belgrade", "Constantinople"], "中东欧贸易史", SRC_WP, "low"),
    ("阿拉伯—印度季风航路", "8—13世纪", ["Muscat", "Calicut", "Goa"], "《印度洋航路志》", SRC_WP, "low"),
    ("西非跨撒哈拉道", "8—14世纪", ["Marrakech", "Tunis", "Gao"], "伊本·白图泰游记", SRC_WP, "low"),
]


def build_routes():
    routes = []
    seen = set()
    rid = 0

    def add(name, era, wp_keys, source, confidence, note=""):
        nonlocal rid
        pts = []
        for k in wp_keys:
            if k not in WP:
                print(f"[warn] 路线点位缺失：{k}", file=sys.stderr)
                return
            pts.append(WP[k])
        if len(pts) < 2:
            return
        key = (name, tuple(tuple(p) for p in pts))
        if key in seen:
            return
        seen.add(key)
        rid += 1
        routes.append({
            "id": f"R{rid:03d}", "名称": name, "年代": era,
            "路径": pts, "出处": note, "source": source, "confidence": confidence,
        })

    for name, era, wps, note, source, conf in ROUTE_SPECS:
        add(name, era, wps, source, conf, note)

    # 长路线自动拆出分段，补足 120 条下限（每段仍带出处与置信度）
    if len(routes) < 120:
        for spec in ROUTE_SPECS:
            if len(routes) >= 120:
                break
            name, era, wps, note, source, conf = spec
            if len(wps) < 4:
                continue
            for i in range(len(wps) - 1):
                if len(routes) >= 120:
                    break
                add(f"〔分段〕{name}·{wps[i]}→{wps[i+1]}", era,
                    [wps[i], wps[i + 1]], source, conf, note)

    ROUTES.mkdir(parents=True, exist_ok=True)
    payload = {
        "source": "谭其骧历史地图集/CHGIS/Wikipedia 军事史公开数据（Natural Earth PD 底图）",
        "count": len(routes),
        "routes": routes,
    }
    (ROUTES / "routes.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return routes

# ----------------------------------------------------------------------------
# 战役 JSON 写出（cn/ + world/，同构 schema）
# ----------------------------------------------------------------------------

CAMPAIGN_REQUIRED = ["id", "title", "era", "context", "terrainFocus", "duration",
                     "victory", "sides", "diplomacy", "armies", "events", "phases",
                     "source", "confidence"]


def write_campaigns():
    CAMP_CN.mkdir(parents=True, exist_ok=True)
    CAMP_WORLD.mkdir(parents=True, exist_ok=True)
    written = []
    for camp in CN_CAMPAIGNS:
        path = CAMP_CN / f"{camp['id']}.json"
        path.write_text(json.dumps(camp, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        written.append(path)
    for camp in WORLD_CAMPAIGNS:
        path = CAMP_WORLD / f"{camp['id']}.json"
        path.write_text(json.dumps(camp, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        written.append(path)
    return written


# ----------------------------------------------------------------------------
# 校验
# ----------------------------------------------------------------------------

def validate(ents, names, poly, rivers, routes, camp_paths):
    errors, warnings = [], []

    for e in ents.values():
        if e["confidence"] not in ("high", "medium", "low"):
            errors.append(f"{e['id']} confidence 非法：{e['confidence']}")
        if not e["sources"]:
            errors.append(f"{e['id']} 缺 source")
        if not (-73 < e["lat"] < 73 and -180 < e["lng"] < 180):
            errors.append(f"{e['id']} 坐标越界：{e['lng']},{e['lat']}")
        if e["start"] > e["end"]:
            errors.append(f"{e['id']} 时间区间倒置")

    ent_ids = set(ents)
    for n in names:
        if n["place_id"] not in ent_ids:
            errors.append(f"名称 {n['name']} 指向不存在实体 {n['place_id']}")
        if n["confidence"] not in ("high", "medium", "low"):
            warnings.append(f"名称 {n['name']} confidence 缺省")

    eras = {p["era"] for p in poly}
    for era, _, _ in ERA_LIST:
        if era not in eras:
            errors.append(f"朝代图层缺失：{era}")

    for r in rivers:
        if not r.get("source") or r.get("confidence") not in ("high", "medium", "low"):
            errors.append(f"河流 {r.get('id')} 缺 source/confidence")
        if len(r.get("pts", [])) < 2:
            errors.append(f"河流 {r.get('id')} 点串不足")

    if len(routes) < 120:
        errors.append(f"路线不足 120 条：{len(routes)}")
    for r in routes:
        if not r.get("名称") or not r.get("年代") or not r.get("路径") or not r.get("出处"):
            errors.append(f"路线 {r.get('id')} 字段不全")
        if len(r.get("路径", [])) < 2:
            errors.append(f"路线 {r['id']} 点串不足")
        if r["confidence"] not in ("high", "medium", "low"):
            errors.append(f"路线 {r['id']} confidence 非法")

    for path in camp_paths:
        camp = json.loads(path.read_text(encoding="utf-8"))
        for k in CAMPAIGN_REQUIRED:
            if k not in camp:
                errors.append(f"战役 {camp.get('id')} 缺字段 {k}")
        side_ids = {s["id"] for s in camp["sides"]}
        for a in camp["armies"]:
            if a["side"] not in side_ids:
                errors.append(f"战役 {camp['id']} 军队 {a['id']} side 越界")
            for f in ("id", "name", "initialPos", "strength", "type", "fatigue", "morale", "intent"):
                if f not in a:
                    errors.append(f"战役 {camp['id']} 军队 {a.get('id')} 缺 {f}")
        for ev in camp["events"]:
            if ev.get("target") and ev["target"] not in {a["id"] for a in camp["armies"]}:
                warnings.append(f"战役 {camp['id']} 事件目标 {ev['target']} 不在军队表")
        if camp["confidence"] not in ("high", "medium", "low"):
            errors.append(f"战役 {camp['id']} confidence 非法")

    # 既有根目录战役 JSON 也参与同构校验
    for path in sorted((MAP / "campaigns").glob("*.json")):
        camp = json.loads(path.read_text(encoding="utf-8"))
        for k in ("id", "title", "era", "armies", "sides"):
            if k not in camp:
                warnings.append(f"既有战役 {path.name} 缺 {k}")

    return errors, warnings


def main():
    check_only = "--check" in sys.argv
    ents = build_entities()
    names = build_names(ents)
    poly = build_powers_poly()
    rivers = build_rivers()
    camp_paths = write_campaigns()
    routes = build_routes()

    errors, warnings = validate(ents, names, poly, rivers, routes, camp_paths)

    if not check_only:
        write_place_entity(ents)
        write_place_names(names, ents)
        write_place_geo(ents)
        write_place_txt(ents)
        write_world_tables(ents)

    print(f"实体 {len(ents)} 条｜名称快照 {len(names)} 条｜朝代图层 {len(poly)} 条"
          f"（{len({p['era'] for p in poly})} 朝）｜河流 {len(rivers)} 条"
          f"｜战役 {len(camp_paths)} 场｜路线 {len(routes)} 条")
    for w in warnings:
        print(f"[warn] {w}")
    if errors:
        print(f"\n[fail] {len(errors)} 项校验未过：")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    print("\n[ok] 全部校验通过")


if __name__ == "__main__":
    main()
