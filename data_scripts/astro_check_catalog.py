#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 星空内置资产校验脚本（规格 §四 体积口径 + §十四 第 6 条「内置行数以导入日志校验」）。

校验项：
  1. 行数区间：内置星表 8,000~9,500（mag ≤ 6.5 约 9,100 量级，规格 §四 第 13 条/切片:13）。
  2. 逐行断言（全量、禁抽样；D48 / 判据 6）：
     - 星等 magVt ≤ 6.5（含等于 6.5）；
     - ra/dec/mag 解析失败计数；
     - NaN / Infinity 非有限值计数；
     - 重复主键计数；
     - 坐标域越界计数（ra ∈ [0,360)，dec ∈ [-90,90]）。
     违规逐行打印 `stars.csv:<物理行号>` 与实测值，不只报总数。
  3. 主键唯一：star.id 无重复（与逐行重复计数同源）。
  4. 星座代号：全部落在 88 个 IAU 三字母缩写集合内。
  5. 体积上限：stars+star_name < 1.5MB；其余各表 < 150KB（规格 §四 体积段）。
  6. 引用完整性：连线/成员/分野/星名引用的 starId、asterismCode 必须在内置表内。
  7. 可回源校验摘要：末尾输出固定 JSON 块（源表行数 / 逐行通过数 / 异常分类计数 /
     内层 L0 累积数），供 CI 静态闸与验收回源。

用法：
  python3 data_scripts/astro_check_catalog.py            # 校验仓库内置资产
  python3 data_scripts/astro_check_catalog.py --selftest # 好坏双样本自测
退出码：0 = 全部断言通过；1 = 存在 FAIL 项。
"""

import csv
import json
import math
import sys
import tempfile
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
# 规格 §十六.6：实体星与密度层分界 6.5，内置层含等于 6.5。
MAG_LIMIT_INCLUSIVE = 6.5

OTHER_TABLES = ["constellation_lines.csv", "asterisms.csv",
                "asterism_members.csv", "field_allocations.csv"]


def _read(name: str, base: Path) -> list[dict]:
    p = base / name
    if not p.exists():
        raise AssertionError(f"缺少资产文件: {p.name}")
    with p.open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def _size(name: str, base: Path) -> int:
    return (base / name).stat().st_size


def _row_violation(line_no: int, sid: str, field: str, value: str, reason: str) -> None:
    """逐行违规留痕：物理行号 + 主键 + 字段 + 实测值 + 原因（禁抽样、禁只报总数）。"""
    print(f"  stars.csv:{line_no}: id={sid} {field}={value!r} {reason}")


def check_catalog(base: Path, check_row_range: bool = True) -> tuple[list[str], dict]:
    """跑全部断言；返回 (失败消息列表, 统计摘要 dict)。selftest 可用临时目录作 base。"""
    failures: list[str] = []

    stars = _read("stars.csv", base)
    names = _read("star_names.csv", base)
    lines = _read("constellation_lines.csv", base)
    asterisms = _read("asterisms.csv", base)
    members = _read("asterism_members.csv", base)
    fields = _read("field_allocations.csv", base)

    # 1. 行数区间
    n = len(stars)
    print(f"[行数] stars={n} names={len(names)} lines={len(lines)} "
          f"asterisms={len(asterisms)} members={len(members)} fields={len(fields)}")
    if check_row_range and not (STARS_MIN <= n <= STARS_MAX):
        failures.append(f"星表行数 {n} 不在区间 [{STARS_MIN},{STARS_MAX}]")

    # 2/3/4. 逐行断言：星等 / 解析 / 有限性 / 主键 / 坐标域 / 星座代号
    seen_ids: set[str] = set()
    parse_failure = 0      # ra/dec/mag 无法解析为浮点
    non_finite = 0         # NaN / Infinity
    mag_over = 0           # mag > 6.5（含等于 6.5 放行）
    dup_key = 0            # 重复主键
    coord_bad = 0          # 坐标域越界
    con_bad = 0            # 非 88 星座代号
    pass_rows = 0
    mag_values: list[float] = []

    for idx, r in enumerate(stars, start=2):  # CSV 物理行号：表头占第 1 行
        sid = r.get("id", "")
        row_anomaly = False

        # 星等解析 + 有限性 + 上限
        mag_raw = r.get("magVt", "")
        mag: float | None
        try:
            mag = float(mag_raw)
        except ValueError:
            parse_failure += 1
            row_anomaly = True
            _row_violation(idx, sid, "magVt", mag_raw, "无法解析为浮点")
            mag = None
        if mag is not None and not math.isfinite(mag):
            non_finite += 1
            row_anomaly = True
            _row_violation(idx, sid, "magVt", mag_raw, "非有限值（NaN/Infinity）")
        elif mag is not None and math.isfinite(mag):
            mag_values.append(mag)
            if mag > MAG_LIMIT_INCLUSIVE:
                mag_over += 1
                row_anomaly = True
                _row_violation(idx, sid, "magVt", mag_raw,
                                f"星等 {mag} > {MAG_LIMIT_INCLUSIVE}")

        # 坐标解析 + 坐标域
        coord_ok = True
        try:
            ra = float(r["raDeg"])
            dec = float(r["decDeg"])
        except (ValueError, KeyError):
            parse_failure += 1
            coord_bad += 1
            coord_ok = False
            row_anomaly = True
            _row_violation(idx, sid, "raDeg/decDeg",
                            f"{r.get('raDeg', '')!r}/{r.get('decDeg', '')!r}",
                            "坐标无法解析为浮点")
            ra = dec = math.nan
        if coord_ok and (not (math.isfinite(ra) and math.isfinite(dec)) or
                         not (0.0 <= ra < 360.0 and -90.0 <= dec <= 90.0)):
            coord_bad += 1
            row_anomaly = True
            _row_violation(idx, sid, "raDeg/decDeg", f"{ra}/{dec}", "坐标域越界")
            coord_ok = False

        # 主键唯一
        if sid in seen_ids:
            dup_key += 1
            row_anomaly = True
            _row_violation(idx, sid, "id", sid, "重复主键")
        seen_ids.add(sid)

        # 星座代号
        if r.get("constellation", "") not in CONSTELLATION_88:
            con_bad += 1
            row_anomaly = True
            _row_violation(idx, sid, "constellation", r.get("constellation", ""),
                           "非 88 集合星座代号")

        if not row_anomaly:
            pass_rows += 1

    if coord_bad:
        failures.append(f"越界坐标 {coord_bad} 行")
    if parse_failure:
        failures.append(f"解析失败 {parse_failure} 行")
    if non_finite:
        failures.append(f"非有限星等 {non_finite} 行")
    if mag_over:
        failures.append(f"星等 > {MAG_LIMIT_INCLUSIVE} {mag_over} 行")
    if dup_key:
        failures.append(f"重复 star.id {dup_key} 个")
    if con_bad:
        failures.append(f"非 88 集合星座代号 {con_bad} 行")

    # 5. 体积上限
    star_name_bytes = _size("stars.csv", base) + _size("star_names.csv", base)
    print(f"[体积] stars+names = {star_name_bytes} 字节（上限 {BUDGET_STAR_NAME_BYTES}）")
    if star_name_bytes >= BUDGET_STAR_NAME_BYTES:
        failures.append(f"star+star_name 体积 {star_name_bytes} ≥ {BUDGET_STAR_NAME_BYTES}")
    for name in OTHER_TABLES:
        b = _size(name, base)
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

    anomalies = {
        "parse_failure": parse_failure,
        "non_finite": non_finite,
        "mag_over_6_5": mag_over,
        "duplicate_key": dup_key,
        "coord_out_of_range": coord_bad,
        "bad_constellation": con_bad,
        "dangling_line": len(dangling_line),
        "dangling_member": len(dangling_member),
        "dangling_field": len(dangling_field),
        "dangling_name": len(dangling_name),
    }
    summary = {
        "source": "app/src/main/assets/astro/stars.csv",
        "source_rows": n,
        "per_row_pass": pass_rows,
        # 内层 L0（内置亮星层）累积数：内置表全量即 L0 累积，应等于源表行数。
        "l0_cumulative": n,
        "mag_max": max(mag_values) if mag_values else None,
        "mag_min": min(mag_values) if mag_values else None,
        "anomalies": anomalies,
    }
    return failures, summary


def _print_summary(summary: dict) -> None:
    print("=== ASTRO CATALOG SUMMARY BEGIN ===")
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
    print("=== ASTRO CATALOG SUMMARY END ===")


def run(base: Path, check_row_range: bool = True) -> int:
    failures, summary = check_catalog(base, check_row_range=check_row_range)
    anomalies_total = sum(summary["anomalies"].values())
    print(f"[异常] 合计 {anomalies_total} 处")
    _print_summary(summary)
    if failures:
        print("FAIL:")
        for fmsg in failures:
            print(f"  - {fmsg}")
        return 1
    print("PASS: 内置星表全部断言通过")
    return 0


# ─────────────────────────── selftest 双样本 ───────────────────────────

_HEADER = ["id", "tychoId", "raDeg", "decDeg", "magVt", "bayer", "flamsteed",
           "hip", "hd", "colorBv", "spectralType", "distanceLy", "constellation"]


def _write_table(dir_: Path, name: str, rows: list[dict]) -> None:
    with (dir_ / name).open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=_HEADER if name == "stars.csv" else
                           list(rows[0].keys()) if rows else ["id"])
        w.writeheader()
        for r in rows:
            w.writerow(r)


def _seed_empty_tables(dir_: Path) -> None:
    # 引用完整性涉及的其余五张表在样本目录内置空表头，避免缺文件误判。
    _write_table(dir_, "star_names.csv", [])
    _write_table(dir_, "constellation_lines.csv", [])
    _write_table(dir_, "asterisms.csv", [])
    _write_table(dir_, "asterism_members.csv", [])
    _write_table(dir_, "field_allocations.csv", [])


def _selftest() -> int:
    """好样本放行（exit 0）；坏样本必须判红（exit 1）：mag=7.0、NaN 星等、重复主键。"""
    good_rows = [
        {"id": "800001", "tychoId": "", "raDeg": "12.500000", "decDeg": "30.000000",
         "magVt": "4.200", "bayer": "", "flamsteed": "", "hip": "800001", "hd": "",
         "colorBv": "0.500", "spectralType": "", "distanceLy": "100.0", "constellation": "Ori"},
        {"id": "800002", "tychoId": "", "raDeg": "200.000000", "decDeg": "-10.000000",
         "magVt": "6.500", "bayer": "", "flamsteed": "", "hip": "800002", "hd": "",
         "colorBv": "", "spectralType": "", "distanceLy": "", "constellation": "Sco"},
    ]
    bad_rows = [
        # 坏样本 1：星等 7.0 > 6.5。
        {"id": "800003", "tychoId": "", "raDeg": "40.000000", "decDeg": "20.000000",
         "magVt": "7.000", "bayer": "", "flamsteed": "", "hip": "800003", "hd": "",
         "colorBv": "", "spectralType": "", "distanceLy": "", "constellation": "Ori"},
        # 坏样本 2：星等 NaN（非有限值）。
        {"id": "800004", "tychoId": "", "raDeg": "50.000000", "decDeg": "-20.000000",
         "magVt": "nan", "bayer": "", "flamsteed": "", "hip": "800004", "hd": "",
         "colorBv": "", "spectralType": "", "distanceLy": "", "constellation": "Sco"},
        # 坏样本 3：与上行之后重复主键（id 800003 二次出现）。
        {"id": "800003", "tychoId": "", "raDeg": "60.000000", "decDeg": "10.000000",
         "magVt": "5.100", "bayer": "", "flamsteed": "", "hip": "800005", "hd": "",
         "colorBv": "", "spectralType": "", "distanceLy": "", "constellation": "Leo"},
    ]

    with tempfile.TemporaryDirectory() as gd, tempfile.TemporaryDirectory() as bd:
        g, b = Path(gd), Path(bd)
        _seed_empty_tables(g)
        _seed_empty_tables(b)
        _write_table(g, "stars.csv", good_rows)
        _write_table(b, "stars.csv", bad_rows)

        print("--- 好样本应 PASS ---")
        rc_good = run(g, check_row_range=False)
        print("--- 坏样本应 FAIL ---")
        rc_bad = run(b, check_row_range=False)

    ok = rc_good == 0 and rc_bad == 1
    print(f"[selftest] 放行样本 exit={rc_good}（期望 0），必失败样本 exit={rc_bad}（期望 1）")
    return 0 if ok else 1


def main() -> int:
    if "--selftest" in sys.argv:
        return _selftest()
    return run(ASTRO)


if __name__ == "__main__":
    sys.exit(main())
