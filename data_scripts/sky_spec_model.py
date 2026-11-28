#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
星空天象层机检 · 模型与规格常量表（被 `check-sky-spec.py` 与 `sky_spec_checks.py` 共用）。

为什么拆出去：常量表是「规格原文 → 机器断言」的唯一对照面，集中一处便于规格改动时
只改本表；检查逻辑与夹具不混在常量表里，避免改数值时误碰判定流程。
"""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 复用脚本固定取本仓 data_scripts；被校验源码路径在各 check 函数内按 root 推导，
# 便于 --selftest 把夹具指向临时目录（不把仓库绝对路径焊死在函数里）。
CATALOG_SCRIPT = ROOT / "data_scripts" / "astro_check_catalog.py"
PYRAMID_SCRIPT = ROOT / "data_scripts" / "astro_check_pyramid.py"
ORPHAN_SCRIPT = ROOT / "data_scripts" / "check-orphan-entries.py"

# ─────────────────────────── 分级与结论常量 ───────────────────────────

MACHINE, SEMI, MANUAL = "机判", "半机判", "人判"
PASS, FAIL, UNKNOWN, DEFER, INFO = "PASS", "FAIL", "UNKNOWN", "DEFER", "INFO"

# ─────────────────────────── 契约常量表（期望值取自规格原文，非源码 substring）──
# 每条：(符号, 期望值, 规格出处, 数值类型)。比对方式：在 SkyParams.kt 按符号定位
# `const val NAME: Type = RHS` 声明，解析 RHS 首个数值与期望值容差比对；
# 声明缺失 / 解析失败 / 数值不符均判 FAIL（不靠子串命中刷绿）。
CONTRACT_CONSTANTS = [
    ("SKY_PROJECTION_FAR", 900.0, "§十六.1", "float"),
    ("SKY_ENTER_EARTH_DIAMETER_PX", 40.0, "§十六.2", "float"),
    ("SKY_TRANSITION_MS", 600, "§十六.3", "int"),
    ("VIEW_SWITCH_MS", 800, "§十六.4", "int"),
    ("MAG_DEPTH_LIMIT", 11.5, "§十六.5", "float"),
    ("MAG_BRIGHT_ENTITY", 6.5, "§十六.6", "float"),
    ("PYRAMID_LEVELS", 2, "§十六.7", "int"),
    ("STAR_SPIKE_MAG", 0.5, "§十六.8", "float"),
    ("TWINKLE_MAG", 2.5, "§十六.9", "float"),
    ("FIELD_PULSE_MS", 1200, "§十六.10", "int"),
    ("METEOR_INTERVAL_MIN_MS", 90000, "§十六.11", "int"),
    ("METEOR_INTERVAL_MAX_MS", 240000, "§十六.11", "int"),
    ("GROUND_VIEW_FOV_DEG", 60.0, "§十六.12", "float"),
    ("OBSERVER_HORIZON_GLOW_DEG", 5.0, "§十六.13", "float"),
    ("SKY_BLEND_FULL_ZOOM", 12.0, "§十六.14", "float"),
    # §五.1 星点 px 曲线：-1.5→9 / 0→5 / 2→3 / 4.5→1.5 / 6.5→1 / >6.5→0.8
    ("STAR_DIAMETER_VERY_BRIGHT_PX", 9.0, "§五.1", "float"),
    ("STAR_DIAMETER_0_PX", 5.0, "§五.1", "float"),
    ("STAR_DIAMETER_2_PX", 3.0, "§五.1", "float"),
    ("STAR_DIAMETER_4_5_PX", 1.5, "§五.1", "float"),
    ("STAR_DIAMETER_6_5_PX", 1.0, "§五.1", "float"),
    ("STAR_DIAMETER_FLOOR_PX", 0.8, "§五.1", "float"),
    # §五.3 线宽与分层 alpha
    ("CONSTELLATION_LINE_WIDTH_PX", 0.8, "§五.3", "float"),
    ("CONSTELLATION_LINE_ALPHA_RATIO", 0.45, "§五.3", "float"),
    ("ASTERISM_LINE_WIDTH_PX", 1.0, "§五.3", "float"),
    ("ASTERISM_LINE_ALPHA_RATIO", 0.55, "§五.3", "float"),
    ("FIELD_BORDER_WIDTH_PX", 1.5, "§五.3", "float"),
    ("FIELD_BORDER_DASH_PX", 6.0, "§五.3", "float"),
    ("FIELD_BORDER_GAP_PX", 4.0, "§五.3", "float"),
    # §五.5 字号（全部 ≥14sp；标题 16sp 为规格定值）
    ("STAR_LABEL_FONT_SP", 14.0, "§五.5", "float"),
    ("CONSTELLATION_NAME_FONT_SP", 14.0, "§五.5", "float"),
    ("SHEET_TITLE_FONT_SP", 16.0, "§五.5", "float"),
    ("SHEET_BODY_FONT_SP", 14.0, "§五.5", "float"),
    ("SHEET_SOURCE_FONT_SP", 14.0, "§五.5", "float"),
    # §五.6/7 间距、圆角、热区
    ("LABEL_GAP_DP", 8.0, "§五.6", "float"),
    ("HIT_ZONE_SIDE_DP", 48.0, "§五.6", "float"),
    ("SHEET_RADIUS_DP", 20.0, "§五.7", "float"),
    ("STAR_NAME_BOX_RADIUS_DP", 2.0, "§五.7", "float"),
    ("LIST_SPACING_DP", 8.0, "§五.7", "float"),
]

# §六.12 四锚色（B-V 连续插值两端/中段），必须逐字落在 SkySpecColor.kt。
ANCHOR_COLORS = [
    ("STAR_ANCHOR_EARLY_RGB", "0xD8E4F0", "§六.12 早型月白"),
    ("STAR_ANCHOR_MID_RGB", "0xFFFDF6", "§六.12 中间纸白"),
    ("STAR_ANCHOR_LATE_RGB", "0xFFEED8", "§六.12 晚型暖白"),
    ("STAR_ANCHOR_VERY_LATE_RGB", "0xFFB37A", "§六.12 极晚橙暖"),
]

# §六.17 允许出现代码行裸 hex 的既有表：SkySpecColor（内容色）+ 既有地形纹理色表。
# 规格原文称「GlobeSpecSurface 既有表」；仓内实际对应 OceanSpec.kt / CoverType.kt。
ALLOWED_CODE_HEX_BASENAMES = {"SkySpecColor.kt", "OceanSpec.kt", "CoverType.kt"}

# §三.1-8 契约/渲染核心文件（锚点可回源：规格点名的文件必须在库）。
CORE_FILES = [
    "astro/contract/SkyParams.kt",
    "astro/contract/SkyFrame.kt",
    "astro/contract/SkySpecColor.kt",
    "astro/proj/StarProjector.kt",
    "astro/data/StarStore.kt",
    "astro/data/StarCatalog.kt",
    "astro/pick/StarPicker.kt",
    "astro/layer/ConstellationLayer.kt",
    "astro/layer/AsterismLayer.kt",
    "astro/layer/FieldAllocationLayer.kt",
    "host/SkyCanvasRenderer.kt",
]

DECL_RE = re.compile(
    r"const\s+val\s+(\w+)\s*:\s*[\w.<>]+\s*=\s*([^\n/]+)")
NUM_RE = re.compile(r"-?\d+(?:_\d+)*(?:\.\d+)?")
HEX6_RE = re.compile(r"#([0-9A-Fa-f]{6})\b")
ARGB_RE = re.compile(r"0[xX]([0-9A-Fa-f]{8})\b")
SP_RE = re.compile(r"(\d+(?:\.\d+)?)\.sp\b")
BLACK_WHITE = {"000000", "FFFFFF"}
TOL = 1e-6


class Item:
    """一条判据结论：条目号 / 分级 / 结论 / 证据位置 / 备注。"""

    def __init__(self, cid: str, level: str, conclusion: str,
                 evidence: list[str], note: str = ""):
        self.cid = cid
        self.level = level
        self.conclusion = conclusion
        self.evidence = evidence
        self.note = note

    def render(self) -> str:
        # 证据条目自身可能带尾分号，先剥掉再统一拼接，避免出现「；；」双分隔。
        ev = "；".join(e.rstrip("；") for e in self.evidence) if self.evidence else "—"
        line = f"[{self.cid}] {self.level} {self.conclusion} — 证据: {ev}"
        if self.note:
            line += f" — 备注: {self.note}"
        return line
