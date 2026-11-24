#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成启动图标「A · 绢帛墨山」全套资产（F-73 三段流水线第③段写码）。

规格唯一来源（逐字回查，禁凭记忆改写）：
  - 选型结论：docs/验收-选型-启动图标.md §3（选中 A，B/C 淘汰，0 份硬判低）
  - 规格原文：docs/规格候选-启动图标-A-绢帛墨印.md §2 画布几何 / §3 逐部件坐标 /
              §4 色值与派生 / §5 monochrome / §6 三态 / §7 边界降级 / §9 判据
  - 同族口径：docs/落文-规格切片/切片-24-功能图标全集.md（圆角端点/拐点、线宽口径）

产出：
  1. 五档旧版启动器 PNG：mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png
     尺寸 48 / 72 / 96 / 144 / 192 px，正方形，按 108dp 画布等比缩放（规格 §2 密度实拍尺寸）。
  2. 自适应图标：
       mipmap-anydpi-v26/ic_launcher.xml
       drawable/ic_launcher_background.xml  背景层（绢帛底 + B2 绢丝纹理）
       drawable/ic_launcher_foreground.xml  前景层（F1 远山 / F5 薄雾 / F2 近山 / F3 碎屑 / F4 金线）
       drawable/ic_launcher_monochrome.xml 单色层（仅 F1/F2/F4，alpha 分层，系统着色）
  3. colors.xml 启动图标色块：五色回挂 CardTokens，两个中间色按 §4 lerp 派生，无表外裸 HEX。

为什么脚本集中参数：规格坐标/色值只在本文件 SPEC 块出现一次，并与规格 md 原文交叉核验；
PNG 与 vector XML 共用同一组几何，避免位图与矢量两套造型漂移。

用法：
  python3 data_scripts/gen-launcher-icon.py          # 写盘生成全部资产
  python3 data_scripts/gen-launcher-icon.py --check  # 只跑断言，不写盘
"""

import argparse
import math
import os
import random
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SPEC_MD = ROOT / "docs/规格候选-启动图标-A-绢帛墨印.md"
RES = ROOT / "app/src/main/res"

# ─────────────────────────────────────────────────────────────────────────────
# SPEC · 全部参数集中于此；每条注释回挂 A 规格小节。改动前先回查规格原文。
# ─────────────────────────────────────────────────────────────────────────────

CANVAS_DP = 108  # 规格 §2：自适应图标画布 108×108dp

# 规格 §2 密度实拍尺寸：mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192
TIERS = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# 规格 §4：既有 token 五色（CardTokens.kt 原值逐一相符）；cinnabar 朱砂刻意不取用。
PAPER = "0xFFF7F1E6"   # cardPaper 纸白  B1 绢帛底
INK = "0xFF1A1410"     # ink 墨色        B2 纹理 / F3 碎屑
STONE = "0xFF3A6B66"   # stoneBlue 石青
CLOUD = "0xFF9B9687"   # cloudGray 云灰  F5 薄雾
GOLD = "0xFFD4A54A"    # warmGold 暖金   F4 金线 / 金点

# 规格 §4 派生色：
#   iconMountainNear = ink → stoneBlue lerp 20%（F2 近山墨，略偏青保留墨重）
#   iconMountainFar  = stoneBlue → cloudGray lerp（F1 远山青，后退层次）。
#   冲突收口：§4 标称 lerp 35%，但 §9 判据 8 要求远山/纸白 ≥4.5:1；35% 实测 4.17:1 不达，
#   按验收硬地板把向 cloudGray 的插值收到 20%（仍在同一对 token 间 lerp，无表外裸 HEX），
#   实测 4.63:1；§4 标称值与 §9 对比度地板的冲突留规格 owner 复核（见回执 §遗留）。
FAR_T = 0.20
NEAR_T = 0.20

# 规格 §3 几何（108dp 画布，原点左上）：
# F1 远山脊：x 18→90，y 62→74 起伏，下沿 y=80；峰位 (36,64)、(66,62)
F1_RIDGE = [(18, 74), (27, 68), (36, 64), (46, 69), (56, 67), (66, 62), (77, 68), (90, 74)]
F1_BASE_Y = 80
# F2 近山主脊：主峰顶 (54,40)，左足 (26,80)，右足 (82,80)；左坡略缓、右坡略陡（两段贝塞尔）
F2_APEX = (54, 40)
F2_LEFT = (26, 80)
F2_RIGHT = (82, 80)
F2_LEFT_CTRL = ((44, 54), (32, 68))
F2_RIGHT_CTRL = ((68, 66), (62, 52))
# F3 墨爆碎屑：y∈[70,84]、x∈[24,84]，圆粒 r 0.8–2.0dp；飞白细丝 3–6dp×0.6dp，alpha 0.5
F3_BOX = (24, 70, 84, 84)
F3_DOTS = 9            # 规格 §3：8–10 粒
F3_FILAMENTS = 3       # 规格 §3：2–3 粒飞白细丝
F3_RADIUS = (0.8, 2.0)
F3_FIL_LEN = (3.0, 6.0)
F3_FIL_W = 0.6
F3_FIL_ALPHA = 0.5
# F4 暖金笔意线：(22,60)→(50,58)→(76,56)，线宽 1.2dp，圆角端点/拐点；金点 (78,55) r=1.4
F4_LINE = [(22, 60), (50, 58), (76, 56)]
F4_WIDTH_DP = 1.2
F4_DOT = (78, 55)
F4_DOT_R = 1.4
# F5 山脚薄雾：y∈[76,82] 横向柔边带，高 4dp，cloudGray alpha 0.18
F5_BOX = (20, 76, 88, 80)
F5_ALPHA = 0.18
# B2 绢丝纹理：noiseGrid 2dp 网格 / noiseAlpha 0.03 / noiseSeed 4102517L（CardTokens 原值）
NOISE_GRID = 2.0
NOISE_ALPHA = 0.03
NOISE_PROB = 0.22      # CardTokens.noiseProbability（规格 §3 B2 复用同一套颗粒参数）
NOISE_SEED = 4102517
NOISE_DOT = 0.6

# 规格 §7：mdpi 48px 坍缩——碎屑 ≤3 粒或整组丢弃；纹理/薄雾丢弃；金线不足 1.5px 简化为实心折线
COLLAPSE_TIER = "mdpi"
COLLAPSE_DOTS = 3
GOLD_MIN_PX = 1.5


# ─────────────────────────────────────────────────────────────────────────────
# 颜色工具
# ─────────────────────────────────────────────────────────────────────────────

def hex_rgb(h):
    h = h.replace("0xFF", "#").lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rgb_hex(rgb):
    return "#%02X%02X%02X" % tuple(round(v) for v in rgb)


def lerp_hex(a, b, t):
    """规格 §4 派生色：a → b 按 t 线性插值，输出 #RRGGBB。"""
    ar, ag, ab = hex_rgb(a)
    br, bg, bb = hex_rgb(b)
    return rgb_hex((
        ar + (br - ar) * t,
        ag + (bg - ag) * t,
        ab + (bb - ab) * t,
    ))


FAR_HEX = lerp_hex(STONE, CLOUD, FAR_T)
NEAR_HEX = lerp_hex(INK, STONE, NEAR_T)

# 生成期用到的 RGBA（alpha 0–255）
def rgba(hexstr, alpha=255):
    return hex_rgb(hexstr) + (alpha,)


def wcag_ratio(hex_fg, hex_bg):
    """WCAG 相对亮度对比度（规格 §9 判据 8 口径：近山≥7:1 / 远山≥4.5:1 / 金线≥3:1）。"""
    def lum(hexc):
        out = []
        for c in hex_rgb(hexc):
            c /= 255.0
            out.append(c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4)
        return 0.2126 * out[0] + 0.7152 * out[1] + 0.0722 * out[2]
    l1, l2 = lum(hex_fg), lum(hex_bg)
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


# ─────────────────────────────────────────────────────────────────────────────
# 规格原文交叉核验（防参数与规格 md 漂移）
# ─────────────────────────────────────────────────────────────────────────────

def verify_against_spec():
    text = SPEC_MD.read_text(encoding="utf-8")
    flat = re.sub(r"\s+", "", text)  # 规格原文逗号后带空格，归一化后比对
    missing = []
    for size in TIERS.values():
        if str(size) not in flat:
            missing.append(f"档位尺寸 {size} 未在规格 §2 出现")
    for token in (PAPER, INK, STONE, CLOUD, GOLD):
        if token.replace("0xFF", "#") not in flat and token not in flat:
            missing.append(f"色值 {token} 未在规格 §4 出现")
    for coord in ("(54,40)", "(26,80)", "(82,80)", "(36,64)", "(66,62)",
                  "(22,60)", "(50,58)", "(76,56)", "(78,55)"):
        if coord not in flat:
            missing.append(f"坐标 {coord} 未在规格 §3 出现")
    # noiseSeed 数值落在 CardTokens.kt（规格 §3 B2 注明沿用 CardTokens.noiseSeed）
    kt = next((ROOT / "app/src/main").rglob("CardTokens.kt")).read_text(encoding="utf-8")
    if f"noiseSeed: Long = {NOISE_SEED}L" not in kt:
        missing.append(f"noiseSeed {NOISE_SEED} 与 CardTokens.kt 不符")
    if missing:
        raise AssertionError("规格交叉核验失败：" + "；".join(missing))


# ─────────────────────────────────────────────────────────────────────────────
# 几何采样（PNG 与 vector 共用）
# ─────────────────────────────────────────────────────────────────────────────

def quad_bezier(p0, p1, p2, n=12):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        pts.append((x, y))
    return pts


def near_mountain_polygon():
    """F2 近山轮廓：左坡 apex→左足、右坡右足→apex，二次贝塞尔采样后闭合到基线。"""
    left = quad_bezier(F2_APEX, F2_LEFT_CTRL[0], F2_LEFT)
    right = quad_bezier(F2_RIGHT, F2_RIGHT_CTRL[1], F2_APEX)
    return left + right[1:-1]


def far_mountain_polygon():
    return F1_RIDGE + [(90, F1_BASE_Y), (18, F1_BASE_Y)]


def splatter_points(rng, count):
    x0, y0, x1, y1 = F3_BOX
    dots = []
    for _ in range(count):
        r = rng.uniform(*F3_RADIUS)
        # 圆心收进盒内并留出半径余量，碎屑不越 F3 边界（规格 §3）
        x = rng.uniform(x0 + r, x1 - r)
        y = rng.uniform(y0 + r, y1 - r)
        dots.append((x, y, r))
    filaments = []
    for _ in range(F3_FILAMENTS):
        length = rng.uniform(*F3_FIL_LEN)
        angle = rng.uniform(0, math.pi)
        x = rng.uniform(x0, x1)
        y = rng.uniform(y0, y1)
        # 端点 clamp 回盒内；收拢后不足 1.5dp 则丢弃该细丝
        x2 = min(max(x + length * math.cos(angle), x0), x1)
        y2 = min(max(y + length * math.sin(angle), y0), y1)
        if math.hypot(x2 - x, y2 - y) >= 1.5:
            filaments.append((x, y, x2, y2))
    return dots, filaments


def noise_cells():
    """B2 绢丝纹理：固定种子撒点，PNG 与 vector 共用同一份格子坐标。"""
    rng = random.Random(NOISE_SEED)
    cells = []
    step = NOISE_GRID
    for gy in range(0, int(CANVAS_DP // step) + 1):
        for gx in range(0, int(CANVAS_DP // step) + 1):
            if rng.random() < NOISE_PROB:
                cells.append((gx * step, gy * step))
    return cells


# ─────────────────────────────────────────────────────────────────────────────
# 1. 五档 PNG（PIL 栅格化，禁手绘近似：坐标全部来自 SPEC）
# ─────────────────────────────────────────────────────────────────────────────

def render_png(size: int, collapse: bool) -> Image.Image:
    s = size / CANVAS_DP
    img = Image.new("RGBA", (size, size), rgba(PAPER))
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def sc(p):
        return (p[0] * s, p[1] * s)

    # B2 绢丝纹理（48px 坍缩丢弃，规格 §7）
    if not collapse:
        ink_a = round(NOISE_ALPHA * 255)
        for x, y in noise_cells():
            d.rectangle([x * s, y * s, (x + NOISE_DOT) * s, (y + NOISE_DOT) * s],
                        fill=rgba(INK, ink_a))

    # F1 远山脊
    d.polygon([sc(p) for p in far_mountain_polygon()], fill=rgba(FAR_HEX))

    # F5 山脚薄雾（48px 坍缩丢弃，规格 §7）
    if not collapse:
        x0, y0, x1, y1 = F5_BOX
        d.rounded_rectangle([x0 * s, y0 * s, x1 * s, y1 * s], radius=2 * s,
                            fill=rgba(CLOUD, round(F5_ALPHA * 255)))

    # F2 近山主脊
    d.polygon([sc(p) for p in near_mountain_polygon()], fill=rgba(NEAR_HEX))

    # F3 墨爆碎屑（48px 只留 ≤3 粒，规格 §7）
    rng = random.Random(NOISE_SEED)
    dots, filaments = splatter_points(rng, COLLAPSE_DOTS if collapse else F3_DOTS)
    for x, y, r in dots:
        d.ellipse([(x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s], fill=rgba(INK))
    for x, y, x2, y2 in filaments:
        d.line([sc((x, y)), sc((x2, y2))], fill=rgba(INK, round(F3_FIL_ALPHA * 255)),
               width=max(1, round(F3_FIL_W * s)))

    # F4 暖金笔意线 + 金点
    width = F4_WIDTH_DP * s
    if collapse and width < GOLD_MIN_PX:
        # 规格 §7：金线不足 1.5px 简化为山脊上一段暖金实心折线
        band = GOLD_MIN_PX
        pts = [sc(p) for p in F4_LINE]
        thick = []
        for i, (x, y) in enumerate(pts):
            thick.append((x, y - band / 2))
        for x, y in reversed(pts):
            thick.append((x, y + band / 2))
        d.polygon(thick, fill=rgba(GOLD))
    else:
        pts = [sc(p) for p in F4_LINE]
        d.line(pts, fill=rgba(GOLD), width=max(1, round(width)), joint="curve")
        for p in (F4_LINE[0], F4_LINE[-1]):  # 圆角端点
            x, y = sc(p)
            r = max(0.6, width / 2)
            d.ellipse([x - r, y - r, x + r, y + r], fill=rgba(GOLD))
    dx, dy = sc(F4_DOT)
    dr = F4_DOT_R * s
    d.ellipse([dx - dr, dy - dr, dx + dr, dy + dr], fill=rgba(GOLD))

    img.alpha_composite(layer)
    return img.convert("RGB")


def write_pngs():
    written = []
    for density, size in TIERS.items():
        collapse = density == COLLAPSE_TIER
        img = render_png(size, collapse)
        out = RES / f"mipmap-{density}" / "ic_launcher.png"
        img.save(out, "PNG", optimize=True)
        written.append(out)
    return written


# ─────────────────────────────────────────────────────────────────────────────
# 2. VectorDrawable（108dp 画布，72dp 安全区 [18,90]）
# ─────────────────────────────────────────────────────────────────────────────

def circle_path(cx, cy, r):
    return (f"M {cx - r:.2f},{cy:.2f} a {r:.2f},{r:.2f} 0 1 0 {2 * r:.2f},0 "
            f"a {r:.2f},{r:.2f} 0 1 0 -{2 * r:.2f},0 z")


def poly_path(pts, close=True):
    head = "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in pts)
    return head + (" Z" if close else "")


def f2_path():
    ax, ay = F2_APEX
    lx, ly = F2_LEFT
    rx, ry = F2_RIGHT
    (c1x, c1y), (c2x, c2y) = F2_LEFT_CTRL
    (c3x, c3y), (c4x, c4y) = F2_RIGHT_CTRL
    return (f"M {ax},{ay} "
            f"C {c1x},{c1y} {c2x},{c2y} {lx},{ly} "
            f"L {rx},{ry} "
            f"C {c3x},{c3y} {c4x},{c4y} {ax},{ay} Z")


def vector_header():
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n'
            '    android:height="108dp"\n'
            '    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n')


def write_background_vector():
    cells = noise_cells()
    subs = [f"M {x:.1f},{y:.1f} h {NOISE_DOT} v {NOISE_DOT} h -{NOISE_DOT} z" for x, y in cells]
    noise_path = " ".join(subs)
    xml = vector_header()
    xml += '    <!-- B1 绢帛底：铺满 108×108，cardPaper 纸白（规格 §3 / §4） -->\n'
    xml += '    <path android:pathData="M 0,0 h 108 v 108 h -108 z" ' \
           'android:fillColor="@color/ic_launcher_background" />\n'
    xml += f'    <!-- B2 绢丝纹理：noiseGrid 2dp / noiseAlpha 0.03 / seed {NOISE_SEED}，只铺背景层 -->\n'
    xml += f'    <path android:pathData="{noise_path}" ' \
           f'android:fillColor="@color/ic_launcher_ink" android:fillAlpha="{NOISE_ALPHA}" />\n'
    xml += "</vector>\n"
    out = RES / "drawable" / "ic_launcher_background.xml"
    out.write_text(xml, encoding="utf-8")
    return out


def write_foreground_vector():
    rng = random.Random(NOISE_SEED)
    dots, filaments = splatter_points(rng, F3_DOTS)
    xml = vector_header()
    xml += '    <!-- 前景层：全部内容收进 72dp 安全区 [18,90]dp（规格 §2） -->\n'
    # F1 远山脊
    xml += '    <!-- F1 远山脊：峰位 (36,64)、(66,62)，下沿 y=80；iconMountainFar 派生色 -->\n'
    xml += f'    <path android:pathData="{poly_path(far_mountain_polygon())}" ' \
           f'android:fillColor="@color/ic_launcher_mountain_far" />\n'
    # F5 薄雾
    x0, y0, x1, y1 = F5_BOX
    xml += '    <!-- F5 山脚薄雾：y 76→80，cloudGray alpha 0.18，仅远山与近山交界 -->\n'
    xml += f'    <path android:pathData="M {x0},{y0} h {x1 - x0} v {y1 - y0} h -{x1 - x0} z" ' \
           f'android:fillColor="@color/ic_launcher_cloud" android:fillAlpha="{F5_ALPHA}" />\n'
    # F2 近山主脊
    xml += '    <!-- F2 近山主脊：顶 (54,40)，左足 (26,80)，右足 (82,80)；iconMountainNear 派生色 -->\n'
    xml += f'    <path android:pathData="{f2_path()}" ' \
           f'android:fillColor="@color/ic_launcher_mountain_near" />\n'
    # F3 碎屑
    xml += '    <!-- F3 墨爆碎屑：圆粒 ink；飞白细丝 alpha 0.5 -->\n'
    for x, y, r in dots:
        xml += f'    <path android:pathData="{circle_path(x, y, r)}" ' \
               f'android:fillColor="@color/ic_launcher_ink" />\n'
    for x, y, x2, y2 in filaments:
        xml += f'    <path android:pathData="M {x:.2f},{y:.2f} L {x2:.2f},{y2:.2f}" ' \
               f'android:strokeColor="@color/ic_launcher_ink" android:strokeWidth="{F3_FIL_W}" ' \
               f'android:strokeAlpha="{F3_FIL_ALPHA}" android:strokeLineCap="round" />\n'
    # F4 金线 + 金点
    xml += '    <!-- F4 暖金笔意线：(22,60)→(50,58)→(76,56)，线宽 1.2dp，圆角端点/拐点；金点 (78,55) r1.4 -->\n'
    xml += f'    <path android:pathData="{poly_path(F4_LINE, close=False)}" ' \
           f'android:strokeColor="@color/ic_launcher_gold" android:strokeWidth="{F4_WIDTH_DP}" ' \
           f'android:strokeLineCap="round" android:strokeLineJoin="round" />\n'
    dx, dy = F4_DOT
    xml += f'    <path android:pathData="{circle_path(dx, dy, F4_DOT_R)}" ' \
           f'android:fillColor="@color/ic_launcher_gold" />\n'
    xml += "</vector>\n"
    out = RES / "drawable" / "ic_launcher_foreground.xml"
    out.write_text(xml, encoding="utf-8")
    return out


def write_monochrome_vector():
    # 规格 §5：仅留 F1/F2/F4；丢弃纹理/碎屑/薄雾/渐变辉光；近山 1.0、远山 0.55、金线点 0.8；
    # 单色层整体中性墨色，由系统主题着色，本层不写死颜色（用 #000000 占位供系统覆写）。
    xml = vector_header()
    xml += '    <!-- monochrome 单色层（规格 §5）：F2 近山 alpha 1.0 / F1 远山 0.55 / F4 金线点 0.8 -->\n'
    xml += '    <path android:pathData="' + poly_path(far_mountain_polygon()) + '" ' \
           'android:fillColor="#000000" android:fillAlpha="0.55" />\n'
    xml += f'    <path android:pathData="{f2_path()}" android:fillColor="#000000" />\n'
    xml += f'    <path android:pathData="{poly_path(F4_LINE, close=False)}" ' \
           'android:strokeColor="#000000" android:strokeAlpha="0.8" ' \
           f'android:strokeWidth="{F4_WIDTH_DP}" android:strokeLineCap="round" ' \
           'android:strokeLineJoin="round" />\n'
    dx, dy = F4_DOT
    xml += f'    <path android:pathData="{circle_path(dx, dy, F4_DOT_R)}" ' \
           'android:fillColor="#000000" android:fillAlpha="0.8" />\n'
    xml += "</vector>\n"
    out = RES / "drawable" / "ic_launcher_monochrome.xml"
    out.write_text(xml, encoding="utf-8")
    return out


def write_adaptive_xml():
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <!-- A·绢帛墨山（规格 docs/规格候选-启动图标-A-绢帛墨印.md）：背景/前景/单色三层 -->\n'
           '    <background android:drawable="@drawable/ic_launcher_background" />\n'
           '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
           '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
           '</adaptive-icon>\n')
    out = RES / "mipmap-anydpi-v26" / "ic_launcher.xml"
    out.write_text(xml, encoding="utf-8")
    return out


# ─────────────────────────────────────────────────────────────────────────────
# 3. colors.xml 启动图标色块（五色回挂 token，两中间色 lerp 派生）
# ─────────────────────────────────────────────────────────────────────────────

COLORS_XML = RES / "values" / "colors.xml"
BEGIN = "    <!-- LAUNCHER-A:BEGIN 绢帛墨山启动图标色块（规格 §4，脚本生成，勿手改） -->"
END = "    <!-- LAUNCHER-A:END -->"


def launcher_color_block():
    lines = [
        BEGIN,
        f'    <color name="ic_launcher_background">{rgb_hex(hex_rgb(PAPER))}</color>  <!-- B1 绢帛底 = cardPaper -->',
        f'    <color name="ic_launcher_ink">{rgb_hex(hex_rgb(INK))}</color>          <!-- B2/F3 墨 = ink -->',
        f'    <color name="ic_launcher_cloud">{rgb_hex(hex_rgb(CLOUD))}</color>        <!-- F5 薄雾 = cloudGray -->',
        f'    <color name="ic_launcher_gold">{rgb_hex(hex_rgb(GOLD))}</color>          <!-- F4 暖金 = warmGold -->',
        f'    <color name="ic_launcher_mountain_far">{FAR_HEX}</color>  <!-- F1 远山 = stoneBlue→cloudGray lerp 20%（§4 标称35%不达§9对比度地板，收口见脚本注释） -->',
        f'    <color name="ic_launcher_mountain_near">{NEAR_HEX}</color> <!-- F2 近山 = ink→stoneBlue lerp 20% -->',
        END,
    ]
    return "\n".join(lines)


def patch_colors_xml():
    text = COLORS_XML.read_text(encoding="utf-8")
    block = launcher_color_block()
    if BEGIN in text and END in text:
        text = re.sub(re.escape(BEGIN) + r".*?" + re.escape(END), block, text,
                      flags=re.S)
    else:
        # 旧块：colors.xml:123-125 玻璃金星作废注释 + ic_launcher_background=#3B342A
        old_pattern = re.compile(
            r"    <!-- 玻璃金星盘底色.*?-->\n"
            r"    <!-- F-73 作废：启动图标无规格原文，.*?-->\n"
            r'    <color name="ic_launcher_background">#3B342A</color>\n?',
            re.S)
        if not old_pattern.search(text):
            raise AssertionError("colors.xml 旧作废色块未匹配到，拒绝改写")
        text = old_pattern.sub(block + "\n", text)
    COLORS_XML.write_text(text, encoding="utf-8")
    return COLORS_XML


# ─────────────────────────────────────────────────────────────────────────────
# 自检断言（第一道：写码后、push 前）
# ─────────────────────────────────────────────────────────────────────────────

def self_check():
    errors = []
    # ① 五档 PNG 存在且尺寸精确（PIL 读 size 逐档比对）
    for density, size in TIERS.items():
        p = RES / f"mipmap-{density}" / "ic_launcher.png"
        if not p.exists():
            errors.append(f"缺 PNG：{p}")
            continue
        im = Image.open(p)
        if im.size != (size, size):
            errors.append(f"{p.name} 尺寸 {im.size} ≠ ({size},{size})")
        if im.mode not in ("RGB", "RGBA"):
            errors.append(f"{p.name} 模式 {im.mode} 异常")
        # PNG 签名 89 50 4E 47 0D 0A 1A 0A
        sig = p.read_bytes()[:8]
        if sig != b"\x89PNG\r\n\x1a\n":
            errors.append(f"{p.name} PNG 签名异常：{sig.hex()}")

    # ② 三层 XML 存在且可被 xml.etree 解析
    xmls = [
        RES / "mipmap-anydpi-v26" / "ic_launcher.xml",
        RES / "drawable" / "ic_launcher_background.xml",
        RES / "drawable" / "ic_launcher_foreground.xml",
        RES / "drawable" / "ic_launcher_monochrome.xml",
    ]
    for x in xmls:
        if not x.exists():
            errors.append(f"缺 XML：{x}")
            continue
        try:
            ET.parse(x)
        except ET.ParseError as e:
            errors.append(f"{x} XML 解析失败：{e}")

    # ③ anydpi-v26 引用路径存在
    adap = ET.parse(xmls[0]).getroot()
    ns = {"a": "http://schemas.android.com/apk/res/android"}
    for layer in ("background", "foreground", "monochrome"):
        node = adap.find(layer, ns)
        if node is None:
            errors.append(f"adaptive-icon 缺 {layer} 层")
            continue
        ref = node.get(f"{{{ns['a']}}}drawable")
        if not ref:
            errors.append(f"{layer} 层无 drawable 引用")
            continue
        kind, name = ref[1:].split("/")
        if kind == "color":
            target = RES / "values" / "colors.xml"
            if f'name="{name}"' not in target.read_text(encoding="utf-8"):
                errors.append(f"{layer} 引用颜色 {ref} 未在 colors.xml 定义")
        else:
            cand = list((RES / kind).glob(f"{name}.xml")) + list((RES / kind).glob(f"{name}.png"))
            if not cand:
                errors.append(f"{layer} 引用 {ref} 资源不存在")

    # ④ 色值血缘：colors.xml 中图标色只允许来自五色 + 两派生色，无表外裸 HEX
    ct = COLORS_XML.read_text(encoding="utf-8")
    block = ct[ct.index(BEGIN):ct.index(END)]
    allowed = {rgb_hex(hex_rgb(PAPER)), rgb_hex(hex_rgb(INK)), rgb_hex(hex_rgb(CLOUD)),
               rgb_hex(hex_rgb(GOLD)), FAR_HEX, NEAR_HEX}
    for hx in re.findall(r"#([0-9A-Fa-f]{6})", block):
        if "#" + hx.upper() not in allowed:
            errors.append(f"表外裸 HEX #{hx} 出现在图标色块")

    # ⑤ 对比度实测（规格 §9 判据 8）
    r_near = wcag_ratio(NEAR_HEX, rgb_hex(hex_rgb(PAPER)))
    r_far = wcag_ratio(FAR_HEX, rgb_hex(hex_rgb(PAPER)))
    r_gold = wcag_ratio(GOLD, NEAR_HEX)
    if r_near < 7.0:
        errors.append(f"近山墨 vs 绢帛底对比度 {r_near:.2f} < 7:1")
    if r_far < 4.5:
        errors.append(f"远山青 vs 绢帛底对比度 {r_far:.2f} < 4.5:1")
    if r_gold < 3.0:
        errors.append(f"金线局部对比度 {r_gold:.2f} < 3:1")

    # ⑥ 安全区/可见圆几何断言（规格 §9 判据 1/2）
    def in_square(x, y):
        return 18 <= x <= 90 and 18 <= y <= 90
    for p in F1_RIDGE + [F2_APEX, F2_LEFT, F2_RIGHT]:
        if not in_square(*p):
            errors.append(f"部件点 {p} 越出 72dp 安全区")
    import math
    for p in (F2_APEX, F4_DOT):
        d = math.hypot(p[0] - 54, p[1] - 54)
        if d > 33:
            errors.append(f"关键识别点 {p} 越出 r=33 可见圆（d={d:.2f}）")
    # F3 碎屑圆粒与细丝端点必须落在规格盒 [24,70]-[84,84] 内
    rng3 = random.Random(NOISE_SEED)
    dots3, fils3 = splatter_points(rng3, F3_DOTS)
    bx0, by0, bx1, by1 = F3_BOX
    for x, y, r in dots3:
        if not (bx0 <= x - r and x + r <= bx1 and by0 <= y - r and y + r <= by1):
            errors.append(f"碎屑 ({x:.2f},{y:.2f}) r={r:.2f} 越出 F3 盒")
    for x, y, x2, y2 in fils3:
        if not (bx0 <= min(x, x2) and max(x, x2) <= bx1 and by0 <= min(y, y2) and max(y, y2) <= by1):
            errors.append(f"细丝 ({x:.2f},{y:.2f})→({x2:.2f},{y2:.2f}) 越出 F3 盒")

    print(f"派生色：far={FAR_HEX}（stoneBlue→cloudGray {FAR_T * 100:.0f}%）"
          f" near={NEAR_HEX}（ink→stoneBlue {NEAR_T * 100:.0f}%）")
    print(f"对比度：近山/纸白={r_near:.2f}:1  远山/纸白={r_far:.2f}:1  金线/近山={r_gold:.2f}:1")
    if errors:
        print("SELF-CHECK FAIL：")
        for e in errors:
            print("  -", e)
        return 1
    print("SELF-CHECK PASS：五档尺寸 / XML 解析 / 引用路径 / 色值血缘 / 对比度 / 几何安全区 全部通过")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只断言不写盘")
    args = ap.parse_args()
    verify_against_spec()
    if args.check:
        return self_check()
    write_pngs()
    write_background_vector()
    write_foreground_vector()
    write_monochrome_vector()
    write_adaptive_xml()
    patch_colors_xml()
    return self_check()


if __name__ == "__main__":
    sys.exit(main())
