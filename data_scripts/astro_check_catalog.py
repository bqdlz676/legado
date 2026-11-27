#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 星空内置资产校验脚本（规格 §四 体积口径 + §十四 第 6 条「内置行数以导入日志校验」）。

校验项：
  1. 行数区间：内置星表 8,000~9,500（mag ≤ 6.5 约 9,100 量级，规格 §四 第 13 条/切片:13）。
  2. 坐标域：ra ∈ [0,360)，dec ∈ [-90,90]，J2000（越界即 FAIL）。
  3. 主键唯一：star.id 无重复。
  4. 星座代号：全部落在 88 个 IAU 三字母缩写集合内。
  5. 体积上限：stars+star_name < 1.5MB；其余各表 < 150KB（规格 §四 体积段）。
  6. 引用完整性：连线/成员/分野引用的 starId、asterismCode 必须在内置表内。

用法：
  python3 data_scripts/astro_check_catalog.py
退出码：0 = 全部断言通过；1 = 存在 FAIL 项。
"""

import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASTRO = ROOT / "app" / "src" / "main" / "assets" / "astro"

# 88 星座 IAU 三字母缩写集合（与内置表 constellation 列取值一致）。
CONSTELLATION_88 = {
    "And", "Ant", "Aps", "Aql", "Aqr", "Ara", "Ari", "Aur", "Boo", "CMa",
    "CMi", "CVn", "Cae", "Cam", "Cap", "Car", "Cas", "Cen", "Cep", "Cet",
    "Cha", "Cir", "Cnc", "Col", "Com", "CrA", "CrB", "Crt", "Cru", "Crv",
    "Cyg", "Del", "Dor", "Dra", "Equ", "Eri", "For", "Gem", "Gru", "Her",
    "Hor", "Hya", "Hyi", "Ind", "LMi", "Lac", "Leo", "Lep", "Lib", "Lup",
    "Lyn", "Lyr", "Men", "Mic", "Mon", "Mus", "Nor", "Oct", "Oph", "Ori",
    "Pav", "Peg", "Per", "Phe", "Pic", "PsA", "Psc", "Pup", "Pyx", "Ret",
    "Scl", "Sco", "Sct", "Ser", "Sex", "Sge", "Sgr", "Tau", "Tel", "TrA",
    "Tri", "Tuc", "UMa", "UMi", "Vel", "Vir", "Vol", "Vul",
}

STARS_MIN, STARS_MAX = 8000, 9500
BUDGET_STAR_NAME_BYTES = 1_500_000
BUDGET_OTHER_BYTES = 150_000


def _read(name: str) -> list[dict]:
    p = ASTRO / name
    if not p.exists():
        raise AssertionError(f"缺少资产文件: {p.name}")
    with p.open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def _size(name: str) -> int:
    return (ASTRO / name).stat().st_size


def main() -> int:
    failures: list[str] = []
    anomalies = 0

    stars = _read("stars.csv")
    names = _read("star_names.csv")
    lines = _read("constellation_lines.csv")
    asterisms = _read("asterisms.csv")
    members = _read("asterism_members.csv")
    fields = _read("field_allocations.csv")

    # 1. 行数区间
    n = len(stars)
    print(f"[行数] stars={n} names={len(names)} lines={len(lines)} "
          f"asterisms={len(asterisms)} members={len(members)} fields={len(fields)}")
    if not (STARS_MIN <= n <= STARS_MAX):
        failures.append(f"星表行数 {n} 不在区间 [{STARS_MIN},{STARS_MAX}]")

    # 2. 坐标域 + 4. 星座代号
    seen_ids: set[str] = set()
    bad_coord: list[str] = []
    bad_con: list[str] = []
    for r in stars:
        try:
            ra = float(r["raDeg"])
            dec = float(r["decDeg"])
        except ValueError:
            bad_coord.append(r["id"])
            continue
        if not (0.0 <= ra < 360.0 and -90.0 <= dec <= 90.0):
            bad_coord.append(r["id"])
        if r["constellation"] not in CONSTELLATION_88:
            bad_con.append(r["id"])
    if bad_coord:
        failures.append(f"越界坐标 {len(bad_coord)} 行，示例: {bad_coord[:5]}")
    if bad_con:
        failures.append(f"非 88 集合星座代号 {len(bad_con)} 行，示例: {bad_con[:5]}")

    # 3. 主键唯一
    dup = [sid for sid in seen_ids if sid in seen_ids]  # 占位防呆
    seen_ids = set()
    duplicates: list[str] = []
    for r in stars:
        sid = r["id"]
        if sid in seen_ids:
            duplicates.append(sid)
        seen_ids.add(sid)
    if duplicates:
        failures.append(f"重复 star.id {len(duplicates)} 个，示例: {duplicates[:5]}")

    # 5. 体积上限
    star_name_bytes = _size("stars.csv") + _size("star_names.csv")
    print(f"[体积] stars+names = {star_name_bytes} 字节（上限 {BUDGET_STAR_NAME_BYTES}）")
    if star_name_bytes >= BUDGET_STAR_NAME_BYTES:
        failures.append(f"star+star_name 体积 {star_name_bytes} ≥ {BUDGET_STAR_NAME_BYTES}")
    for name in ["constellation_lines.csv", "asterisms.csv",
                 "asterism_members.csv", "field_allocations.csv"]:
        b = _size(name)
        print(f"[体积] {name} = {b} 字节（上限 {BUDGET_OTHER_BYTES}）")
        if b >= BUDGET_OTHER_BYTES:
            failures.append(f"{name} 体积 {b} ≥ {BUDGET_OTHER_BYTES}")

    # 6. 引用完整性
    star_ids = seen_ids
    con_codes = {a["code"] for a in asterisms}
    dangling_line = [l["fromStarId"] for l in lines
                     if l["fromStarId"] not in star_ids or l["toStarId"] not in star_ids]
    dangling_member = [m["starId"] for m in members
                       if m["starId"] not in star_ids or m["asterismCode"] not in con_codes]
    dangling_field = [f["asterismCode"] for f in fields if f["asterismCode"] not in con_codes]
    dangling_name = [n["starId"] for n in names if n["starId"] not in star_ids]
    if dangling_line:
        failures.append(f"连线悬空引用 {len(dangling_line)} 条")
    if dangling_member:
        failures.append(f"成员悬空引用 {len(dangling_member)} 条")
    if dangling_field:
        failures.append(f"分野悬空引用 {len(dangling_field)} 条")
    if dangling_name:
        failures.append(f"星名悬空引用 {len(dangling_name)} 条")

    anomalies = len(bad_coord) + len(bad_con) + len(duplicates) + \
        len(dangling_line) + len(dangling_member) + len(dangling_field) + len(dangling_name)

    print(f"[异常] 合计 {anomalies} 处")
    if failures:
        print("FAIL:")
        for fmsg in failures:
            print(f"  - {fmsg}")
        return 1
    print("PASS: 内置星表全部断言通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
