#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
判据 7（§十四.7 / §六.16-17）禁色与主题色像素级实测 + 字号像素旁证
=====================================================================

既有静态闸 `check-sky-spec.py :: check_forbidden_colors` 只扫代码行裸 hex / 纯黑白字面量
（L0 静态），渲染后帧里是否残留纯黑 / 纯白像素、是否冒出四锚色之外的第五个主题色，
静态扫描看不到。本脚本对 CI Paparazzi 帧 PNG 逐像素统计，把判据 7 从「PASS（静态）」
抬到「像素级实测」。

判据口径（规格原文锚点）：
  - §六.16（docs/星空天象层-实施规格-合并定稿.md:201）：禁 `#000000`、禁 `#FFFFFF`、
    禁 Material 默认色、禁第 14 基础色。
  - §六.12（同文件:192）：四锚星色 `#D8E4F0` / `#FFFDF6` / `#FFEED8` / `#FFB37A`。
  - §十四.7（同文件:317）：全屏无 `#000000`、UI 层无第 14 色。
  - §五.5（同文件:156-158）：全部字号 ≥14sp。

像素判定规则（与验收口径对齐，见任务 hint ①）：
  1. 纯黑 / 纯白只数「不透明且三通道严格等于 0 或 255」的像素；半透明黑/白、
     AA 抗锯齿过渡像素不计入禁色（它们是边缘混合，不是绘制字面量）。
  2. 主题色数量 ≠ 抗锯齿过渡像素：落在两调色板颜色连线上的过渡像素判为 AA blend，
     不计入越界主题色；调色板之外、成簇的实色块才计入「候选第 14 色」。
  3. 调色板 = 四锚星色 + ComponentPalette 12 色 token + GlobeSpecSurface 地表数据色
     （取值出处见下方 PALETTE 注释，逐条可回源到文件:行）。

字号换算（帧宽 562 ↔ 设备宽 1080）：
  - 设备标称宽 W0 = 1080px（NEXUS_5，screenshot/.../SkyStateFixtures.kt FRAME_WIDTH_PX）；
    帧实际宽 W = PNG IHDR 实测（基线 562px）。缩放系数 k = W / W0。
  - NEXUS_5 密度 420dpi → 密度 D = 420 / 160 = 2.625 px/sp。
  - 帧内测得文字条带高 h_png（PNG 像素）→ 设备像素 = h_png / k；
    反算 sp = h_png / (k · D)。
  - 旁证口径警告：截图夹具 `SkyRenderView.density = 1f`（SkyStateFixtures.kt:119），
    layoutlib 内 textSize = sp × 1；本换算是按设备密度 D 回推的上界参考，
    字号达标的主判据仍是静态常量闸（SkyParams 各 FONT_SP ≥14sp，check-sky-spec.py
    §五.5）。像素侧只回答「帧里有没有小到异常的文字条带」。

用法：
  python3 data_scripts/check-sky-colors.py                 # 扫默认星空帧目录
  python3 data_scripts/check-sky-colors.py <png 或目录>... # 显式路径（目录递归全部 PNG）
  python3 data_scripts/check-sky-colors.py --selftest      # 坏图判红 / 干净图判绿双向自测
  python3 data_scripts/check-sky-colors.py --json          # 额外落 JSON 汇总

退出码：0 = 全部帧禁色计数为 0 且无越界主题簇；1 = 有 FAIL；2 = 未收集到任何 PNG。
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent

# ─────────────────────────── 调色板（逐条回源）───────────────────────────

# 四锚星色：app/src/main/.../astro/contract/SkySpecColor.kt:23/26/29/32
ANCHORS: dict[str, tuple[int, int, int]] = {
    "D8E4F0": (0xD8, 0xE4, 0xF0),
    "FFFDF6": (0xFF, 0xFD, 0xF6),
    "FFEED8": (0xFF, 0xEE, 0xD8),
    "FFB37A": (0xFF, 0xB3, 0x7A),
}

# 12 色 UI token（RGB 部分）：app/src/main/.../ui/components/ComponentPalette.kt
# :19 BG_PAGE / :22 BG_SURFACE / :28 TEXT_PRIMARY / :31 TEXT_SECONDARY / :34
# TEXT_TERTIARY / :37 TEXT_ACCENT / :40 BRAND_GOLD / :43 BRAND_COPPER / :59 STONE_BLUE
TOKENS: dict[str, tuple[int, int, int]] = {
    "BG_PAGE": (0xF7, 0xF1, 0xE6),
    "BG_SURFACE": (0xEF, 0xE8, 0xD8),
    "TEXT_PRIMARY": (0x1A, 0x14, 0x10),
    "TEXT_SECONDARY": (0x5C, 0x4F, 0x3A),
    "TEXT_TERTIARY": (0x9B, 0x96, 0x87),
    "TEXT_ACCENT": (0xC2, 0x3B, 0x2E),
    "BRAND_GOLD": (0xD4, 0xA5, 0x4A),
    "BRAND_COPPER": (0x8B, 0x69, 0x14),
    "STONE_BLUE": (0x3A, 0x6B, 0x66),
}

# 地表数据色（地貌纹理解码基准，不是 UI 配色）：GlobeTokens.kt:109-125
SURFACE: dict[str, tuple[int, int, int]] = {
    "FOREST": (0x2D, 0x5A, 0x27),
    "GRASS": (0x7A, 0x9A, 0x3A),
    "SOIL": (0xA0, 0x80, 0x60),
    "DESERT": (0xD4, 0xB8, 0x96),
    "SNOW": (0xF0, 0xF0, 0xEE),
    "WATER": (0x3C, 0x6E, 0x8A),
    "URBAN": (0x8A, 0x7A, 0x6A),
    "ICE": (0xDC, 0xE8, 0xEE),
    "OCEAN_DEEP": (0x1B, 0x3A, 0x5C),
    "OCEAN_SHALLOW": (0x3A, 0x7A, 0x8A),
}

PALETTE: dict[str, tuple[int, int, int]] = {**ANCHORS, **TOKENS, **SURFACE}
PAL_NAMES = list(PALETTE.keys())
PAL_ARR = np.array([PALETTE[n] for n in PAL_NAMES], dtype=np.float64)

# 夜空派生底色：skyNightBase = darken(TEXT_PRIMARY 1A1410, 0.60)（GlobeTokens.kt:62/80-87）
# R=round(26*0.6)=16, G=round(20*0.6)=12, B=round(16*0.6)=10 → 100C0A
SKY_NIGHT_BASE = (0x10, 0x0C, 0x0A)

# 判定容差（RGB 欧氏距离）：近锚色 / 近 token 视为调色板内。
NEAR_TOL = 18.0
# AA 过渡残差：像素落在两调色板色连线上的最大允许通道残差。
AA_RESIDUAL = 10.0
# 候选越界簇最小像素数（562000 像素帧 × 0.027% ≈ 150）；低于此数视为零星噪点。
MIN_OFF_PALETTE_CLUSTER_PX = 150

DEVICE_WIDTH_PX = 1080.0          # NEXUS_5 标称宽（SkyStateFixtures.kt FRAME_WIDTH_PX）
DEVICE_DENSITY = 420.0 / 160.0    # 420dpi → 2.625 px/sp
MIN_FONT_SP = 14.0                # §五.5 最小字号


def _rgb_hsv(rgb: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """返回 (hue 0-360, saturation)。"""
    arr = rgb.astype(np.float64) / 255.0
    r, g, b = arr[:, 0], arr[:, 1], arr[:, 2]
    mx = arr.max(axis=1)
    mn = arr.min(axis=1)
    diff = mx - mn
    sat = np.where(mx > 0, diff / np.where(mx == 0, 1, mx), 0)
    mask = diff > 1e-9
    diff_safe = np.where(diff == 0, 1, diff)
    rc = np.where(mask & (mx == r), ((g - b) / diff_safe) % 6, 0.0)
    gc = np.where(mask & (mx == g), (b - r) / diff_safe + 2, 0.0)
    bc = np.where(mask & (mx == b), (r - g) / diff_safe + 4, 0.0)
    hue = (rc + gc + bc) * 60.0
    return hue, sat


def _rgb_to_hue_band(rgb: np.ndarray) -> list[str]:
    """向量化 RGB(uint8) → 12 色相带；低饱和归 gray。返回字符串列表。"""
    hue, sat = _rgb_hsv(rgb)
    bands: list[str] = []
    for s, hv in zip(sat, hue):
        if s < 0.12:
            bands.append("gray")
        else:
            bands.append("h%d" % min(11, int(hv // 30)))
    return bands


def analyze_frame(path: Path) -> dict:
    """逐像素统计一帧；返回结构化结果（数值全部实算，不估）。"""
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    arr = np.asarray(im)
    total = arr.shape[0] * arr.shape[1]
    flat = arr.reshape(-1, 4).astype(np.int64)
    r, g, b, a = flat[:, 0], flat[:, 1], flat[:, 2], flat[:, 3]

    opaque = a == 255
    black = int(np.count_nonzero(opaque & (r == 0) & (g == 0) & (b == 0)))
    white = int(np.count_nonzero(opaque & (r == 255) & (g == 255) & (b == 255)))
    transparent = int(np.count_nonzero(a == 0))

    rgb = flat[:, :3].astype(np.float64)
    # 调色板最近距离（N × P 广播）
    dist = np.sqrt(((rgb[:, None, :] - PAL_ARR[None, :, :]) ** 2).sum(axis=2))
    nearest_idx = dist.argmin(axis=1)
    nearest_d = dist[np.arange(len(rgb)), nearest_idx]

    anchor_exact: dict[str, int] = {}
    anchor_near: dict[str, int] = {}
    for name, ac in ANCHORS.items():
        ac_arr = np.array(ac, dtype=np.float64)
        d = np.sqrt(((rgb - ac_arr) ** 2).sum(axis=1))
        anchor_exact[name] = int(np.count_nonzero(opaque & (d == 0)))
        anchor_near[name] = int(np.count_nonzero(opaque & (d <= NEAR_TOL)))

    palette_hits: dict[str, int] = {}
    in_palette = opaque & (nearest_d <= NEAR_TOL)
    for i, name in enumerate(PAL_NAMES):
        n = int(np.count_nonzero(in_palette & (nearest_idx == i)))
        if n:
            palette_hits[name] = n

    # 调色板外、不透明像素；AA 过渡判定只在这批剩余像素上做。
    off_mask = opaque & ~in_palette
    off_idx = np.nonzero(off_mask)[0]
    off_rgb = rgb[off_idx]

    # AA blend：星点 / 线条叠在夜空底上，过渡像素是「某调色板色 ↔ 夜空底」的凸混合。
    # 越界像素量小（千级），对全体调色板端点逐对求混合残差成本可控；两调色板色之间的
    # 边缘过渡由最近邻容差 NEAR_TOL 兜住，不在此重复查。
    bg_arr = np.array(SKY_NIGHT_BASE, dtype=np.float64)
    aa_flag = np.zeros(len(off_rgb), dtype=bool)
    for ep in PAL_ARR:
        span = ep - bg_arr
        denom = float((span * span).sum())
        if denom == 0:
            continue
        t = ((off_rgb - bg_arr) * span).sum(axis=1) / denom
        t = np.clip(t, 0.0, 1.0)
        blend = bg_arr + t[:, None] * span
        residual = np.abs(off_rgb - blend).max(axis=1)
        aa_flag |= residual <= AA_RESIDUAL
    solid_off = off_rgb[~aa_flag]

    # 越界实色按色相带聚类；低饱和暖灰归 neutral（AA / 渐变残色，不算主题色）。
    clusters: dict[str, int] = {}
    neutral: dict[str, int] = {}
    if len(solid_off):
        bands = np.array(_rgb_to_hue_band(solid_off))
        for band, cnt in zip(*np.unique(bands, return_counts=True)):
            entry = neutral if band == "gray" else clusters
            entry[str(band)] = int(cnt)
    # 候选第 14 色：饱和色相带且成簇达阈值；neutral 灰只登记不判红。
    suspect = {k: v for k, v in clusters.items() if v >= MIN_OFF_PALETTE_CLUSTER_PX}

    # 字号旁证：与主背景色差异足够大的像素按行聚条带，取最大条带高（PNG px）。
    bg = np.array(SKY_NIGHT_BASE, dtype=np.float64)
    row_diff = np.sqrt(((arr[:, :, :3].astype(np.float64) - bg) ** 2).sum(axis=2))
    row_has = (row_diff > 40).any(axis=1)
    bands_px: list[int] = []
    run = 0
    for has in row_has:
        if has:
            run += 1
        elif run > 0:
            bands_px.append(run)
            run = 0
    if run > 0:
        bands_px.append(run)
    max_band_px = max(bands_px) if bands_px else 0
    k = w / DEVICE_WIDTH_PX
    max_font_sp = (max_band_px / (k * DEVICE_DENSITY)) if max_band_px > 0 else 0.0

    anchor_present = sum(1 for n in anchor_near.values() if n > 0)
    result = {
        "file": str(path),
        "width": w,
        "height": h,
        "total_px": int(total),
        "transparent_px": transparent,
        "pure_black_px": black,
        "pure_white_px": white,
        "anchor_exact": anchor_exact,
        "anchor_near": anchor_near,
        "anchor_clusters_present": anchor_present,
        "palette_hits": dict(sorted(palette_hits.items(), key=lambda kv: -kv[1])[:8]),
        "off_palette_cluster_px": clusters,
        "neutral_cluster_px": neutral,
        "suspect_off_palette": suspect,
        "max_text_band_px": max_band_px,
        "scale_k": round(k, 5),
        "est_max_font_sp": round(max_font_sp, 2),
    }
    result["fail"] = bool(black or white or suspect)
    return result


def _default_frame_paths(scan_all: bool) -> list[Path]:
    """默认帧目录：CI artifact 落盘目录优先，其次本地 screenshot build 输出。

    判据 7 是星空层禁色口径，默认只取 SkyEight 八态帧；书架 / 卡片 / 主壳等帧属其它
    模块域，须显式 --all 才纳入，避免跨模块误报。
    """
    paths: list[Path] = []
    for art in sorted((ROOT / "artifacts").glob("sky-frames-*")):
        for p in art.glob("*.png"):
            if scan_all or "SkyEight" in p.name:
                paths.append(p)
    local = ROOT / "screenshot/build/outputs/screenshots"
    if local.exists():
        for p in local.glob("*.png"):
            if scan_all or "SkyEight" in p.name:
                paths.append(p)
    seen, uniq = set(), []
    for p in paths:
        rp = str(p)
        if rp not in seen:
            seen.add(rp)
            uniq.append(p)
    return uniq


def render_report(results: list[dict]) -> str:
    lines = ["== check-sky-colors.py 禁色 / 主题色 / 字号像素实测 =="]
    for r in results:
        tag = "FAIL" if r["fail"] else "PASS"
        lines.append(f"[{tag}] {os.path.relpath(r['file'], ROOT)} "
                     f"({r['width']}×{r['height']})")
        lines.append(f"   纯黑像素={r['pure_black_px']}  纯白像素={r['pure_white_px']}  "
                     f"透明像素={r['transparent_px']}")
        lines.append("   四锚近邻命中: " +
                     "  ".join(f"{k}={v}" for k, v in r["anchor_near"].items()) +
                     f" （在现锚簇 {r['anchor_clusters_present']}/4）")
        if r["suspect_off_palette"]:
            lines.append(f"   ⚠ 越界饱和实色簇（候选第 14 色）: {r['suspect_off_palette']}")
        else:
            lines.append("   越界饱和实色簇: 无（≥%d px 的调色板外成簇饱和色为 0）"
                         % MIN_OFF_PALETTE_CLUSTER_PX)
        if r.get("neutral_cluster_px"):
            lines.append(f"   低饱和残色簇（neutral/AA，不判主题色）: {r['neutral_cluster_px']}")
        lines.append(f"   最大文字条带={r['max_text_band_px']}px → 折算 "
                     f"{r['est_max_font_sp']}sp（k={r['scale_k']}, D={DEVICE_DENSITY}；"
                     f"旁证口径，主判据为静态常量闸）")
    return "\n".join(lines)


# ─────────────────────────── 双向自测 ───────────────────────────

def _make_png(path: Path, *, bad: bool) -> None:
    im = Image.new("RGBA", (200, 300), (*SKY_NIGHT_BASE, 255))
    px = im.load()
    for x in range(20, 60, 4):
        for y in range(20, 60, 4):
            px[x, y] = (*ANCHORS["FFB37A"], 255)
    for x in range(120, 160, 4):
        for y in range(20, 60, 4):
            px[x, y] = (*ANCHORS["D8E4F0"], 255)
    if bad:
        for x in range(70, 90):
            for y in range(120, 150):
                px[x, y] = (0, 0, 0, 255)
        for x in range(110, 130):
            for y in range(120, 150):
                px[x, y] = (255, 255, 255, 255)
        for x in range(70, 130):
            for y in range(200, 250):
                px[x, y] = (0x00, 0xFF, 0x88, 255)
    im.save(path)


def selftest() -> int:
    with tempfile.TemporaryDirectory() as td:
        good = Path(td) / "good.png"
        bad = Path(td) / "bad.png"
        _make_png(good, bad=False)
        _make_png(bad, bad=True)
        gr = analyze_frame(good)
        br = analyze_frame(bad)

        checks = [
            ("干净图纯黑/纯白为 0、无越界簇",
             gr["pure_black_px"] == 0 and gr["pure_white_px"] == 0
             and not gr["suspect_off_palette"],
             f"black={gr['pure_black_px']} white={gr['pure_white_px']} "
             f"suspect={gr['suspect_off_palette']}"),
            ("坏图纯黑被抓出", br["pure_black_px"] > 0,
             f"black={br['pure_black_px']}"),
            ("坏图纯白被抓出", br["pure_white_px"] > 0,
             f"white={br['pure_white_px']}"),
            ("坏图越界亮青簇被判候选第 14 色", bool(br["suspect_off_palette"]),
             f"suspect={br['suspect_off_palette']}"),
        ]
        ok = True
        for label, passed, detail in checks:
            print(f"-- selftest {label}: {'PASS' if passed else 'FAIL'}（{detail}）")
            ok = ok and passed
    print("SELFTEST: PASS" if ok else "SELFTEST: FAIL")
    return 0 if ok else 1


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="判据 7 禁色 / 主题色像素级实测")
    ap.add_argument("paths", nargs="*", help="PNG 文件或目录（缺省扫默认星空帧目录）")
    ap.add_argument("--selftest", action="store_true", help="好坏双样本双向自测")
    ap.add_argument("--all", action="store_true",
                    help="纳入非星空帧（书架/卡片/主壳）；默认只扫 SkyEight 八态帧")
    ap.add_argument("--json", action="store_true", help="输出 JSON 汇总")
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()

    targets: list[Path] = []
    for raw in args.paths or []:
        p = Path(raw)
        if p.is_dir():
            for q in sorted(p.glob("**/*.png")):
                if args.all or "SkyEight" in q.name:
                    targets.append(q)
        elif p.exists():
            targets.append(p)
    if not targets:
        targets = _default_frame_paths(args.all)
    if not targets:
        print("未收集到任何 PNG 帧（artifacts/ 与本地 build 输出均缺席）。", file=sys.stderr)
        return 2

    results = [analyze_frame(p) for p in sorted(targets)]

    fails = [r for r in results if r["fail"]]
    if args.json:
        # JSON 独占 stdout 供上游门禁/报告取用；人读摘要走 stderr，避免混进 JSON 流。
        print(json.dumps(results, ensure_ascii=False, indent=2))
        print(render_report(results), file=sys.stderr)
    else:
        print(render_report(results))
    print("COLOR-SPEC: PASS" if not fails else f"COLOR-SPEC: FAIL（{len(fails)} 帧违规）",
          file=sys.stderr)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
