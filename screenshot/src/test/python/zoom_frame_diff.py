#!/usr/bin/env python3
"""
判据 1 连续缩放序列帧 · 相邻帧差分量化（本地分析，不在 CI 内跑）。

输入：Paparazzi artifact 下拉到本地的帧目录，帧名形如 sky-zoom-09.00.png。
输出：
  - zoom-diff.csv：每对相邻帧的像素差异均值 / 分位数 / 变化像素占比
  - zoom-diff-summary.md：中位数、尖峰判定（差异均值 > 3 倍中位数）与档位跳变结论

口径：
  - 差异按亮度（Rec.601 加权）逐像素取绝对值，再给均值与 p50 / p95 / p99；
  - 连续单曲线的相邻差异应平滑，档边突变会在某一对上吞掉整段差值形成尖峰；
  - 外侧平台段（9.00 对 10.00、12.00 对 12.50）blend 钳平，差异应接近底噪。

用法：
  python3 zoom_frame_diff.py <帧目录> [输出目录]
"""

import csv
import glob
import os
import re
import sys

import numpy as np
from PIL import Image

NAME_RE = re.compile(r"sky-zoom-(\d+\.\d+)\.png")


def load_rgb(path):
    with Image.open(path) as im:
        return np.asarray(im.convert("RGB"), dtype=np.float32)


def luminance(arr):
    # Rec.601 亮度加权，与肉眼明暗感受一致；范围 0..255。
    return 0.299 * arr[..., 0] + 0.587 * arr[..., 1] + 0.114 * arr[..., 2]


def percentile(sorted_vals, p):
    if len(sorted_vals) == 0:
        return 0.0
    idx = int(round(p * (len(sorted_vals) - 1)))
    return float(sorted_vals[idx])


def main():
    frames_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    out_dir = sys.argv[2] if len(sys.argv) > 2 else frames_dir

    paths = []
    for path in sorted(glob.glob(os.path.join(frames_dir, "*.png"))):
        m = NAME_RE.search(os.path.basename(path))
        if m:
            paths.append((float(m.group(1)), path))
    paths.sort(key=lambda item: item[0])

    if len(paths) < 2:
        sys.exit("帧不足 2 档，无法做相邻帧差分：%s" % frames_dir)

    images = [(zoom, load_rgb(path)) for zoom, path in paths]
    rows = []
    for (za, pa), (zb, pb) in zip(images, images[1:]):
        la, lb = luminance(pa), luminance(pb)
        if la.shape != lb.shape:
            sys.exit("帧尺寸不一致：%s vs %s" % (la.shape, lb.shape))
        diff = np.abs(la - lb)
        flat = diff.reshape(-1)
        ordered = np.sort(flat)
        mean = float(flat.mean())
        p50 = percentile(ordered, 0.50)
        p95 = percentile(ordered, 0.95)
        p99 = percentile(ordered, 0.99)
        changed = float((flat > 2.0).mean())  # 大于 2 灰度级视为真变化，压掉编码底噪
        rows.append(
            {
                "zoom_a": f"{za:.2f}",
                "zoom_b": f"{zb:.2f}",
                "mean_abs": f"{mean:.4f}",
                "p50": f"{p50:.4f}",
                "p95": f"{p95:.4f}",
                "p99": f"{p99:.4f}",
                "changed_frac": f"{changed:.5f}",
            }
        )

    means = [float(r["mean_abs"]) for r in rows]
    median_mean = float(np.median(means))
    spike_ratio = 3.0
    spikes = [r for r in rows if float(r["mean_abs"]) > spike_ratio * median_mean]

    os.makedirs(out_dir, exist_ok=True)
    csv_path = os.path.join(out_dir, "zoom-diff.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(
            fh,
            fieldnames=["zoom_a", "zoom_b", "mean_abs", "p50", "p95", "p99", "changed_frac"],
        )
        writer.writeheader()
        writer.writerows(rows)

    md_path = os.path.join(out_dir, "zoom-diff-summary.md")
    with open(md_path, "w", encoding="utf-8") as fh:
        fh.write("# 连续缩放相邻帧差分汇总\n\n")
        fh.write(f"帧档数：{len(images)}；相邻帧对：{len(rows)}\n\n")
        fh.write(f"差异均值中位数：{median_mean:.4f} 灰度级\n\n")
        fh.write(f"尖峰判定线：> {spike_ratio} × 中位数 = {spike_ratio * median_mean:.4f}\n\n")
        if spikes:
            fh.write("## 尖峰（疑似档位突变）\n\n")
            for r in spikes:
                fh.write(
                    f"- {r['zoom_a']} → {r['zoom_b']}：mean={r['mean_abs']} "
                    f"p95={r['p95']} p99={r['p99']}\n"
                )
        else:
            fh.write("## 结论：无相邻对超过 3 倍中位数尖峰，差分曲线区间内平滑。\n\n")
        fh.write("## 逐对数值\n\n")
        fh.write("| A(R) | B(R) | 均值 | p50 | p95 | p99 | 变化占比 |\n")
        fh.write("| --- | --- | --- | --- | --- | --- | --- |\n")
        for r in rows:
            fh.write(
                f"| {r['zoom_a']} | {r['zoom_b']} | {r['mean_abs']} | {r['p50']} | "
                f"{r['p95']} | {r['p99']} | {r['changed_frac']} |\n"
            )

    print(f"frames={len(images)} pairs={len(rows)} median_mean={median_mean:.4f} "
          f"spikes={len(spikes)}")
    print(f"csv={csv_path}")
    print(f"summary={md_path}")


if __name__ == "__main__":
    main()
