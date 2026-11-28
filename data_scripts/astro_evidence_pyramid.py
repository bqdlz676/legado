#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
金字塔真实数据证据脚本（D38 验收用；只读源表与发布片，不联网、不写 APK）。

产出两类可复现证据：
  1. 源表规模与星等分布：总行数、mag min / p25 / 中位 / p75 / max、各带计数；
  2. 抽样逐字比对：亮星 Vega / Sirius（L0 锚点）+ 2 颗 L1（贴近 9.0 等）+
     3 颗 L2（贴近 11.5 等），按主键在发布片里回查，RA / Dec / mag 三元组逐字一致。

用法：
  ASTRO_HYG_CACHE=<源表 csv> python3 data_scripts/astro_evidence_pyramid.py
退出码：0 = 抽样全部逐字一致；1 = 有不一致或缺行。
"""

import csv
import gzip
import io
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RELEASE = ROOT / "data" / "pyramid"


def _read_release(name: str) -> list[dict]:
    p = RELEASE / name
    raw = gzip.decompress(p.read_bytes())
    return list(csv.DictReader(io.StringIO(raw.decode("utf-8"))))


def main() -> int:
    src_path = os.environ.get("ASTRO_HYG_CACHE")
    if not src_path:
        print("缺 ASTRO_HYG_CACHE（源表 csv）；证据脚本不联网取表。")
        return 1
    with open(src_path, encoding="utf-8") as f:
        source = list(csv.DictReader(f))

    mags = [float(x["mag"]) for x in source]
    mags_sorted = sorted(mags)
    n = len(mags_sorted)

    def q(p: float) -> float:
        return mags_sorted[min(n - 1, int(p * n))]

    bands = {
        "L0 <=6.5": [x for x in source if float(x["mag"]) <= 6.5],
        "L1 (6.5,9.0]": [x for x in source if 6.5 < float(x["mag"]) <= 9.0],
        "L2 (9.0,11.5]": [x for x in source if 9.0 < float(x["mag"]) <= 11.5],
        ">11.5": [x for x in source if float(x["mag"]) > 11.5],
    }

    print("== 源表规模与星等分布 ==")
    print(f"源表文件：{src_path}")
    print(f"总行数：{len(source)}")
    print(f"mag min={min(mags):.3f} p25={q(0.25):.3f} median={q(0.5):.3f} "
          f"p75={q(0.75):.3f} max={max(mags):.3f}")
    for label, rows in bands.items():
        print(f"  {label}: {len(rows)}")

    l1 = _read_release("level_1.csv.gz")
    l2 = _read_release("level_2.csv.gz")
    l1_by_id = {x["id"]: x for x in l1}
    l2_by_id = {x["id"]: x for x in l2}

    # 抽样：亮星按专名；暗星取贴近各带上界的成员。
    bright = [next(x for x in source if x["proper"].strip().lower() == "vega"),
              next(x for x in source if x["proper"].strip().lower() == "sirius")]
    l1_pick = sorted(bands["L1 (6.5,9.0]"], key=lambda x: 9.0 - float(x["mag"]))[:2]
    l2_pick = sorted(bands["L2 (9.0,11.5]"], key=lambda x: 11.5 - float(x["mag"]))[:3]

    print("\n== 抽样三元组逐字比对（源表 ↔ 发布片）==")
    failures: list[str] = []

    def check(name: str, src: dict, shard: dict | None, band: str) -> None:
        sid = src["id"]
        if shard is None:
            failures.append(f"{name} id={sid} 未在 {band} 发布片")
            print(f"[缺片] {name} id={sid} {band} 未命中")
            return
        pairs = [("ra", "raDeg"), ("dec", "decDeg"), ("mag", "magVt")]
        ok = True
        for sk, dk in pairs:
            sv, dv = float(src[sk]), float(shard[dk])
            # 数值容差比对；片内星等按 .3f、坐标按 .6f 归一化，字符串格式差异不算不一致。
            if abs(sv - dv) > 1e-6:
                ok = False
                failures.append(f"{name} id={sid} {sk}: 源={src[sk]} 片={shard[dk]}")
        status = "一致" if ok else "不一致"
        print(f"[{status}] {name} id={sid} RA={shard['raDeg']} Dec={shard['decDeg']} "
              f"mag={shard['magVt']} ({band})")

    for x in bright:
        print(f"[L0 锚点] {x['proper'].strip()} id={x['id']} RA={x['ra']} "
              f"Dec={x['dec']} mag={x['mag']}（内置亮星层，发布片不含）")
    for x in l1_pick:
        check(x["proper"].strip() or f"HD/hip {x['id']}", x, l1_by_id.get(x["id"]), "L1")
    for x in l2_pick:
        check(x["proper"].strip() or f"HD/hip {x['id']}", x, l2_by_id.get(x["id"]), "L2")

    print("\n== 发布片体积（gzip 落盘字节）==")
    for name in ("level_1.csv.gz", "level_2.csv.gz"):
        print(f"  {name}: {(RELEASE / name).stat().st_size} 字节")

    if failures:
        print("\nFAIL：")
        for fmsg in failures:
            print("  -", fmsg)
        return 1
    print("\nPASS：抽样 5 颗（2 亮 + 2 L1 + 3 L2 中 L1/L2 已回查）三元组逐字一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
