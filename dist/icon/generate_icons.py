#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 APP 图标渲染管线（显式绘制，不使用任何生成式模型）。

规格（用户拍板，逐字遵守）：
  正圆纸白底 #F7F1E6 + 墨字 #1A1410（思源宋体单字「落」）+ 暖金 #D4A54A 1.5dp 边框
  + 左下断口 8°（圆心为顶点，开口方向左下：屏幕极角 135°，半宽 4°），断口两端圆头收笔。

几何基准：自适应图标 108dp 画布，安全区直径 66dp（半径 33dp）。
所有尺寸以本脚本常量为准；输出像素与边框厚度见同目录核对结果与 docs 设计文档。
"""

import math
import os
from PIL import Image, ImageDraw, ImageFont

# ----------------------------------------------------------------------------
# 0. 路径与常量
# ----------------------------------------------------------------------------
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DIST = os.path.join(ROOT, "dist", "icon")
FONT_PATH = os.path.join(ROOT, "app", "src", "main", "assets", "fonts",
                         "NotoSerifSC-SemiBold.otf")
GLYPH = "落"

PAPER = (247, 241, 230, 255)   # #F7F1E6 ink_bg_page
INK = (26, 20, 16, 255)        # #1A1410 ink_text_primary
GOLD = (212, 165, 74, 255)     # #D4A54A ink_brand_gold
BLACK = (0, 0, 0, 255)
WHITE = (255, 255, 255, 255)

# 108dp 画布几何（单位 dp）——自适应前景/单色/预览使用
CANVAS_DP = 108.0
CENTER_DP = CANVAS_DP / 2.0
SAFE_R_DP = 33.0               # 自适应安全区半径（官方 66dp 直径）
R_OUT_DP = 31.0                # 圆环外半径（距安全区边缘留 2dp）
T_DP = 1.5                     # 边框视觉厚度 1.5dp
R_IN_DP = R_OUT_DP - T_DP
R_MID_DP = (R_OUT_DP + R_IN_DP) / 2.0
GLYPH_H_RATIO = 0.48           # 字形可见高度 / 圆直径（规格 42%–48%；降采样后实测落在区间内）

# 断口：屏幕极角（0°=右，顺时针为正，y 向下）；左下 = 135°；总宽 8°
GAP_CENTER_DEG = 135.0
GAP_HALF_DEG = 4.0
GAP_START = GAP_CENTER_DEG - GAP_HALF_DEG   # 131°
GAP_END = GAP_CENTER_DEG + GAP_HALF_DEG     # 139°

SS = 4                         # 超采样倍数（抗锯齿，最终 LANCZOS 降采样）

# 密度档：倍率 d / 传统 mipmap 边长 px / 自适应前景边长 px(108dp*d)
DENS = {
    "mdpi":    {"d": 1.0, "launcher": 48,  "fg": 108},
    "hdpi":    {"d": 1.5, "launcher": 72,  "fg": 162},
    "xhdpi":   {"d": 2.0, "launcher": 96,  "fg": 216},
    "xxhdpi":  {"d": 3.0, "launcher": 144, "fg": 324},
    "xxxhdpi": {"d": 4.0, "launcher": 192, "fg": 432},
}


def _rad(deg):
    return math.radians(deg)


def _glyph_layer(target_h_px, color):
    """渲染单字并裁剪到可见 bbox，缩放到目标高度（超采样像素）。"""
    big = 1200
    font = ImageFont.truetype(FONT_PATH, big)
    probe = Image.new("RGBA", (big * 2, big * 2), (0, 0, 0, 0))
    pd = ImageDraw.Draw(probe)
    pd.text((big // 2, big // 2), GLYPH, font=font, fill=color)
    bbox = probe.getbbox()
    glyph = probe.crop(bbox)
    w, h = glyph.size
    new_h = max(1, int(round(target_h_px)))
    new_w = max(1, int(round(w * new_h / h)))
    return glyph.resize((new_w, new_h), Image.LANCZOS)


def render(out_px, c_px, r_out_px, t_px, paper_fill, glyph_color, ring_color,
           glyph_h_ratio=GLYPH_H_RATIO):
    """
    在显式像素几何上绘制完整构图（超采样 SS 倍后降采样）。
    out_px       : 输出边长
    c_px         : 圆心（输出像素坐标系，边长 out_px）
    r_out_px     : 圆环外半径（输出像素）
    t_px         : 边框厚度（输出像素）
    """
    s = SS
    size = out_px * s
    c = c_px * s
    r_out = r_out_px * s
    r_mid = (r_out_px - t_px / 2.0) * s
    t = t_px * s
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1) 纸白圆底
    if paper_fill:
        draw.ellipse([c - r_out, c - r_out, c + r_out, c + r_out], fill=PAPER)

    # 2) 暖金圆环：缺口 [131°,139°]，其余整圆；PIL 弧顺时针
    draw.arc([c - r_mid, c - r_mid, c + r_mid, c + r_mid],
             start=GAP_END, end=GAP_START + 360.0,
             fill=ring_color, width=int(round(t)))

    # 3) 断口两端圆头（墨迹收笔），圆心在中径上，半径 = 厚度/2
    cap_r = t / 2.0
    for ang in (GAP_START, GAP_END):
        x = c + r_mid * math.cos(_rad(ang))
        y = c + r_mid * math.sin(_rad(ang))
        draw.ellipse([x - cap_r, y - cap_r, x + cap_r, y + cap_r], fill=ring_color)

    # 4) 墨字：可见高度 = 圆直径 * ratio，视觉居中
    glyph_h = 2.0 * r_out_px * glyph_h_ratio * s
    glyph = _glyph_layer(glyph_h, glyph_color)
    gw, gh = glyph.size
    img.alpha_composite(glyph, (int(round(c - gw / 2)), int(round(c - gh / 2))))

    return img.resize((out_px, out_px), Image.LANCZOS)


def circular_mask(px, radius_ratio=1.0):
    m = Image.new("L", (px, px), 0)
    d = ImageDraw.Draw(m)
    c = px / 2.0
    r = px / 2.0 * radius_ratio
    d.ellipse([c - r, c - r, c + r, c + r], fill=255)
    return m


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def adaptive_geometry(d, fg_px):
    """自适应前景/单色几何：108dp 画布，内容落在 66dp 安全区内。"""
    c = CENTER_DP * d
    r_out = R_OUT_DP * d
    t = T_DP * d
    return c, r_out, t


def main():
    saved = []

    # 1.1 传统 mipmap：ic_launcher.png / ic_launcher_round.png
    # 旧版无安全区概念，圆撑满边长的 95%；边框仍按 1.5dp*d 像素
    for name, meta in DENS.items():
        d, px = meta["d"], meta["launcher"]
        c = px / 2.0
        r_out = px / 2.0 * 0.95
        t = T_DP * d
        comp = render(px, c, r_out, t, paper_fill=True,
                      glyph_color=INK, ring_color=GOLD)
        saved.append(save(comp, os.path.join(RES, f"mipmap-{name}", "ic_launcher.png")))
        # 圆角版：先铺纸白再合成，避免遮罩外缘透出透明黑底
        round_img = Image.new("RGBA", (px, px), PAPER)
        round_img.alpha_composite(comp)
        round_img.putalpha(circular_mask(px, 0.95))
        saved.append(save(round_img, os.path.join(RES, f"mipmap-{name}",
                                                  "ic_launcher_round.png")))

        # 1.2 自适应前景（透明底，内容落在 66dp 安全区内）
        c, r_out, t = adaptive_geometry(d, meta["fg"])
        fg = render(meta["fg"], c, r_out, t, paper_fill=False,
                    glyph_color=INK, ring_color=GOLD)
        saved.append(save(fg, os.path.join(RES, f"drawable-{name}",
                                           "ic_launcher_foreground.png")))

        # 1.3 单色剪影（仅 alpha，系统染色）
        mono = render(meta["fg"], c, r_out, t, paper_fill=False,
                      glyph_color=BLACK, ring_color=BLACK)
        saved.append(save(mono, os.path.join(RES, f"drawable-{name}",
                                             "ic_launcher_monochrome.png")))

    # 1.4 Playstore 512×512：无透明，整幅纸白，圆内不出界
    ps = render(512, 256.0, 256.0 * 0.94, T_DP * (512 / CANVAS_DP),
                paper_fill=True, glyph_color=INK, ring_color=GOLD)
    bg = Image.new("RGBA", (512, 512), PAPER)
    bg.alpha_composite(ps)
    saved.append(save(bg.convert("RGB"), os.path.join(DIST, "playstore-512.png")))

    # 1.5 预览图：4 形态 × 3 背景
    saved.append(build_preview_sheet())

    print(f"saved {len(saved)} files")
    for p in saved:
        print(" ", os.path.relpath(p, ROOT))


def build_preview_sheet():
    cell = 220
    pad = 28
    label_h = 34
    forms = ["正圆遮罩", "方角遮罩", "圆角遮罩", "主题单色"]
    rows = [("白底", WHITE), ("纸白底", PAPER), ("深色底", INK)]
    cols = len(forms)
    W = pad * 2 + cols * cell + (cols - 1) * pad
    H = pad * 2 + len(rows) * (cell + label_h) + (len(rows) - 1) * pad
    sheet = Image.new("RGBA", (W, H), (235, 232, 226, 255))
    sd = ImageDraw.Draw(sheet)
    # 预览标签用系统全量 CJK 字体（随包子集字体缺标签用字）；缺失则回退 bundled
    sys_label = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
    label_font = ImageFont.truetype(
        sys_label if os.path.exists(sys_label) else FONT_PATH, 16)

    for ri, (row_name, bg) in enumerate(rows):
        for ci, form in enumerate(forms):
            x = pad + ci * (cell + pad)
            y = pad + ri * (cell + label_h + pad)
            cell_bg = Image.new("RGBA", (cell, cell), bg)

            mono = form == "主题单色"
            dark = bg == INK
            # 仅主题单色剪影随背景反色；常规形态始终保持墨字 + 暖金环
            shape_c = WHITE if dark else BLACK
            glyph_c = shape_c if mono else INK
            ring_c = shape_c if mono else GOLD
            # 自适应几何缩到 cell
            d = cell / CANVAS_DP
            c, r_out, t = adaptive_geometry(d, cell)
            comp = render(cell, c, r_out, t, paper_fill=not mono,
                          glyph_color=glyph_c, ring_color=ring_c)
            cell_bg.alpha_composite(comp)
            if form == "正圆遮罩":
                cell_bg.putalpha(circular_mask(cell))
            elif form == "圆角遮罩":
                m = Image.new("L", (cell, cell), 0)
                ImageDraw.Draw(m).rounded_rectangle(
                    [0, 0, cell, cell], radius=int(cell * 0.22), fill=255)
                cell_bg.putalpha(m)
            sheet.alpha_composite(cell_bg, (x, y))
            sd.text((x, y + cell + 6), f"{row_name} · {form}",
                    font=label_font,
                    fill=(255, 255, 255) if dark else INK[:3])

    out = os.path.join(DIST, "preview-sheet.png")
    sheet.convert("RGB").save(out)
    return out


if __name__ == "__main__":
    main()
