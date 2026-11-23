#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逐密度像素核对：尺寸、边框厚度、断口角度、字形高度占比、安全区余量。"""
import math
import os
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DENS = {
    "mdpi":    {"d": 1.0, "launcher": 48,  "fg": 108},
    "hdpi":    {"d": 1.5, "launcher": 72,  "fg": 162},
    "xhdpi":   {"d": 2.0, "launcher": 96,  "fg": 216},
    "xxhdpi":  {"d": 3.0, "launcher": 144, "fg": 324},
    "xxxhdpi": {"d": 4.0, "launcher": 192, "fg": 432},
}
GOLD = (212, 165, 74)


def gold_coverage(px):
    """返回金色不透明度 0..1（容忍抗锯齿与色差）。"""
    r, g, b, a = px
    if a < 40:
        return 0.0
    # 暖金判定：RGB 接近目标且明显暖色调（r>g>b，差值拉开），排除墨字 AA 灰像素
    warm = r - g > 12 and g - b > 12
    if warm and abs(r - GOLD[0]) < 60 and abs(g - GOLD[1]) < 60 and abs(b - GOLD[2]) < 60:
        return a / 255.0
    return 0.0


def ink_coverage(px):
    r, g, b, a = px
    return a / 255.0 if (a > 120 and r < 90 and g < 80 and b < 70) else 0.0


def border_thickness(img, c):
    """沿正上方射线列方向，数金色覆盖的径向跨度（含半透明 AA 像素）。"""
    x = int(round(c))
    ys = [y for y in range(0, int(c)) if gold_coverage(img.getpixel((x, y))) > 0.15]
    return (max(ys) - min(ys) + 1) if ys else 0


def gap_check(img, c, r_mid):
    """缺口内部 132°–138° 应无金；环体其余角度应有金（中径±1px 容差）。"""
    w, h = img.size
    gap_hit = gap_tot = ring_hit = ring_tot = 0
    for deg in range(0, 360):
        found = False
        for dr in (-1, 0, 1):
            x = int(round(c + (r_mid + dr) * math.cos(math.radians(deg))))
            y = int(round(c + (r_mid + dr) * math.sin(math.radians(deg))))
            if 0 <= x < w and 0 <= y < h and gold_coverage(img.getpixel((x, y))) > 0.15:
                found = True
                break
        if 132 <= deg <= 138:
            gap_tot += 1
            gap_hit += found
        else:
            ring_tot += 1
            ring_hit += found
    return gap_hit, gap_tot, ring_hit, ring_tot


def glyph_ratio(img, r_out):
    w, h = img.size
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            if ink_coverage(img.getpixel((x, y))) > 0.15:
                xs.append(x); ys.append(y)
    if not ys:
        return None
    return round((max(ys) - min(ys)) / (2 * r_out), 3)


def main():
    print(f"{'density':10} {'kind':10} {'size':10} {'border_px':10} "
          f"{'gap_gold/tot':14} {'ring_gold/tot':14} {'glyph_h/diam'}")
    for name, meta in DENS.items():
        d, px, fg = meta["d"], meta["launcher"], meta["fg"]
        for kind, path, expect in (
            ("launcher", os.path.join(RES, f"mipmap-{name}", "ic_launcher.png"), px),
            ("round", os.path.join(RES, f"mipmap-{name}", "ic_launcher_round.png"), px),
            ("fg", os.path.join(RES, f"drawable-{name}", "ic_launcher_foreground.png"), fg),
            ("mono", os.path.join(RES, f"drawable-{name}", "ic_launcher_monochrome.png"), fg),
        ):
            img = Image.open(path).convert("RGBA")
            assert img.size == (expect, expect), f"{path} size {img.size}"
            c = expect / 2.0
            r_out = px / 2.0 * 0.95 if kind in ("launcher", "round") else 31.0 * d
            r_mid = r_out - 1.5 * d / 2.0
            bt = border_thickness(img, c)
            gh, gt, rh, rt = gap_check(img, c, r_mid)
            ratio = glyph_ratio(img, r_out) if kind != "mono" else "-"
            print(f"{name:10} {kind:10} {str(img.size):10} {bt:<10} "
                  f"{f'{gh}/{gt}':14} {f'{rh}/{rt}':14} {ratio}")
        print(f"  理论边框厚度 1.5dp × {d} = {1.5*d:.2f} px")


if __name__ == "__main__":
    main()
