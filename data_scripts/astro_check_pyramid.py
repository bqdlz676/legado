#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 星空金字塔分层校验脚本（离线可跑，无第三方依赖）
=========================================================

校验对象：
  A. 契约一致性：`astro_pyramid_manifest.json` 与 `SkyParams.kt` 常量单源不漂移；
  B. L0 内置亮星层（真实公版数据，`app/src/main/assets/astro/stars.csv`）：
     坐标域、星等带、主键唯一、星座代号、行数区间；
  C. L1/L2 设备缓存切片（存在才校验；缺目录 = 离线首启预期态，记 INFO 不判 FAIL）：
     层级完整性（表头/字段/文件齐备）、星数累积单调、星等落在对应带、
     坐标范围、重复 id（片内 + 跨层互斥）、sha256 旁证一致。

用法：
  python3 data_scripts/astro_check_pyramid.py
退出码：0 = PASS；1 = FAIL。

口径锚点：
  - 规格 §十六.5/6/7：MAG_DEPTH_LIMIT=11.5，MAG_BRIGHT_ENTITY=6.5，PYRAMID_LEVELS=2；
  - 规格 §十四.6：内置行数以导入日志校验 = mag≤6.5 全量，不许抽样；
  - 规格 §四.138：金字塔按需 <60MB 可删可重建。
"""

import csv
import gzip
import hashlib
import io
import json
import math
import os
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "data_scripts" / "astro_pyramid_manifest.json"
ASTRO = ROOT / "app" / "src" / "main" / "assets" / "astro"
SKY_PARAMS = ROOT / "app" / "src" / "main" / "java" / "io" / "legado" / \
    "app" / "ui" / "map" / "globe" / "astro" / "contract" / "SkyParams.kt"
# 缓存目录可被环境变量覆盖，便于 --selftest 在临时目录构造好/坏样本。
CACHE_DIR = Path(os.environ.get("ASTRO_PYRAMID_CACHE",
                               ROOT / "data_scripts" / "_cache" / "pyramid"))

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
PYRAMID_FIELDS = ["id", "tychoId", "raDeg", "decDeg", "magVt", "colorBv", "constellation"]


def _sha256_file(p: Path) -> str:
    h = hashlib.sha256()
    h.update(p.read_bytes())
    return h.hexdigest()


def _read_csv(p: Path) -> list[dict]:
    raw = p.read_bytes()
    if p.suffix == ".gz":
        raw = gzip.decompress(raw)
    return list(csv.DictReader(io.StringIO(raw.decode("utf-8"))))


def _valid_coord(ra: str, dec: str) -> tuple[bool, float | None, float | None]:
    try:
        raf, decf = float(ra), float(dec)
    except ValueError:
        return False, None, None
    if not (0.0 <= raf < 360.0 and -90.0 <= decf <= 90.0):
        return False, raf, decf
    return True, raf, decf


def check_contract(manifest: dict, failures: list[str]) -> None:
    c = manifest["contract"]
    if c["pyramidLevels"] != 2:
        failures.append(f"PYRAMID_LEVELS 契约 = {c['pyramidLevels']}，应为 2")
    if c["magBrightEntity"] != 6.5 or c["magDepthLimit"] != 11.5:
        failures.append("星等分界契约与 §十六.5/6 不符")
    if not (c["magBrightEntity"] < c["level1UpperMag"] < c["magDepthLimit"]):
        failures.append("星等带不单调：6.5 < 9.0 < 11.5 不成立")

    # 与 SkyParams.kt 单源回读，防 manifest 与契约常量漂移。
    text = SKY_PARAMS.read_text(encoding="utf-8")
    anchors = {
        "MAG_DEPTH_LIMIT": r"const val MAG_DEPTH_LIMIT: Float = 11\.5f",
        "MAG_BRIGHT_ENTITY": r"const val MAG_BRIGHT_ENTITY: Float = 6\.5f",
        "PYRAMID_LEVELS": r"const val PYRAMID_LEVELS: Int = 2",
    }
    for name, pattern in anchors.items():
        if not re.search(pattern, text):
            failures.append(f"SkyParams.kt 未回读常量 {name}（manifest 与代码可能漂移）")

    levels = manifest["levels"]
    if len(levels) != c["pyramidLevels"] + 1:
        failures.append(f"层级数 {len(levels)} ≠ L0 + {c['pyramidLevels']} 级")
    mag_ceiling = [lv["magMaxInclusive"] for lv in levels]
    if mag_ceiling != sorted(mag_ceiling):
        failures.append(f"各层星等上限非单调：{mag_ceiling}")


def check_l0(failures: list[str]) -> tuple[list[str], int]:
    rows = _read_csv(ASTRO / "stars.csv")
    n = len(rows)
    print(f"[L0 内置层] rows={n}")
    if not (STARS_MIN <= n <= STARS_MAX):
        failures.append(f"L0 行数 {n} 不在 [{STARS_MIN},{STARS_MAX}]")

    seen: set[str] = set()
    bad_coord, bad_mag, bad_con, dup = 0, 0, 0, 0
    ids: list[str] = []
    for r in rows:
        sid = r["id"]
        if sid in seen:
            dup += 1
        seen.add(sid)
        ids.append(sid)
        try:
            mag = float(r["magVt"])
        except ValueError:
            bad_mag += 1
            mag = None
        if mag is not None and (not math.isfinite(mag) or mag > 6.5):
            bad_mag += 1
        ok, _, _ = _valid_coord(r["raDeg"], r["decDeg"])
        if not ok:
            bad_coord += 1
            continue
        if r["constellation"] not in CONSTELLATION_88:
            bad_con += 1

    if bad_coord:
        failures.append(f"L0 越界坐标 {bad_coord} 行")
    if bad_mag:
        failures.append(f"L0 非有限 / >6.5 星等 {bad_mag} 行")
    if bad_con:
        failures.append(f"L0 非 88 星座代号 {bad_con} 行")
    if dup:
        failures.append(f"L0 重复主键 {dup} 个")
    return ids, n


def check_shard(dir_: Path, fname: str, lo_exclusive: float, hi_inclusive: float,
                known_ids: set[str], failures: list[str], stats: dict) -> list[str]:
    # 设备缓存与仓库发布片都认：plain CSV 优先，其次同名单 .csv.gz。
    candidates = [fname, fname + ".gz"]
    p = next((dir_ / c for c in candidates if (dir_ / c).exists()), None)
    if p is None:
        print(f"[缓存] {fname} 不存在（离线预期态，INFO）")
        return []

    rows = _read_csv(p)
    header = rows[0].keys() if rows else []
    missing_cols = [c for c in PYRAMID_FIELDS if c not in header]
    if missing_cols:
        failures.append(f"{p.name} 缺字段 {missing_cols}")

    side = dir_ / (p.name + ".sha256")
    if side.exists():
        expected = side.read_text(encoding="utf-8").split()[0]
        if _sha256_file(p) != expected:
            failures.append(f"{p.name} sha256 与旁证不符（疑似损坏/被裁剪）")

    seen: set[str] = set()
    bad_coord, bad_mag, dup, cross = 0, 0, 0, 0
    ids: list[str] = []
    for r in rows:
        sid = r["id"]
        # 重复 / 跨层互斥先判，不被坐标 continue 掩盖（一行坏不许藏住另一类缺陷）。
        if sid in seen:
            dup += 1
        if sid in known_ids:
            cross += 1
        seen.add(sid)
        ids.append(sid)
        try:
            mag = float(r["magVt"])
        except ValueError:
            bad_mag += 1
            mag = None
        if mag is not None and (not math.isfinite(mag) or
                                not (lo_exclusive < mag <= hi_inclusive)):
            bad_mag += 1
        ok, _, _ = _valid_coord(r["raDeg"], r["decDeg"])
        if not ok:
            bad_coord += 1
            continue

    stats[fname] = len(rows)
    print(f"[缓存] {p.name} rows={len(rows)} 带=({lo_exclusive}, {hi_inclusive}]")
    if bad_coord:
        failures.append(f"{p.name} 越界坐标 {bad_coord} 行")
    if bad_mag:
        failures.append(f"{p.name} 星等越带/非有限 {bad_mag} 行")
    if dup:
        failures.append(f"{p.name} 片内重复 id {dup} 个")
    if cross:
        failures.append(f"{p.name} 与已有层跨层重复 id {cross} 个")
    return ids


def run(cache_dir: Path) -> int:
    failures: list[str] = []
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    check_contract(manifest, failures)
    l0_ids, l0_n = check_l0(failures)
    known = set(l0_ids)
    stats = {"level_1.csv": 0, "level_2.csv": 0}

    l1_ids = check_shard(cache_dir, "level_1.csv", 6.5, 9.0, known, failures, stats)
    known.update(l1_ids)
    l2_ids = check_shard(cache_dir, "level_2.csv", 9.0, 11.5, known, failures, stats)

    # 星数累积单调：L0 ≤ L0+L1 ≤ L0+L1+L2（缺层按 0 计，离线态不判红）。
    cum0 = l0_n
    cum1 = cum0 + stats["level_1.csv"]
    cum2 = cum1 + stats["level_2.csv"]
    if not (cum0 <= cum1 <= cum2):
        failures.append(f"累积星数非单调：{cum0} → {cum1} → {cum2}")
    print(f"[单调] 累积星数 L0={cum0}  +L1={cum1}  +L2={cum2}")

    # 体积口径：内置层实数；缓存层缺省时标注未计入。
    builtin_bytes = sum(f.stat().st_size for f in ASTRO.iterdir() if f.is_file())
    print(f"[体积] assets/astro 内置合计 = {builtin_bytes} 字节 "
          f"({builtin_bytes / 1024 / 1024:.3f} MiB)；金字塔缓存未计入（按需落设备）。")

    if failures:
        print("FAIL:")
        for msg in failures:
            print(f"  - {msg}")
        return 1
    print("PASS: 金字塔契约 / L0 内置层 / 现有缓存切片全部断言通过")
    return 0


def _write_shard(dir_: Path, fname: str, rows: list[dict], with_sum: bool) -> None:
    p = dir_ / fname
    with p.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=PYRAMID_FIELDS, lineterminator="\n")
        w.writeheader()
        for r in rows:
            w.writerow(r)
    if with_sum:
        (dir_ / (fname + ".sha256")).write_text(
            hashlib.sha256(p.read_bytes()).hexdigest() + f"  {fname}\n", encoding="utf-8")


def _selftest() -> int:
    """必失败样本 + 放行样本双跑：好样本必须 0，坏样本必须 1（F8 补② 门禁口径）。"""
    good_row_l1 = {"id": "900001", "tychoId": "", "raDeg": "12.500000",
                    "decDeg": "30.000000", "magVt": "8.000", "colorBv": "0.500",
                    "constellation": "Ori"}
    good_row_l2 = {"id": "900002", "tychoId": "", "raDeg": "200.000000",
                    "decDeg": "-10.000000", "magVt": "10.500", "colorBv": "",
                    "constellation": "Sco"}
    bad_row_l1 = {"id": "900001", "tychoId": "", "raDeg": "400.000000",
                  "decDeg": "30.000000", "magVt": "12.000", "colorBv": "",
                  "constellation": "Ori"}  # 越界坐标 + 越星等带 + 与 L2 跨层重复 id
    bad_row_l2 = {"id": "900001", "tychoId": "", "raDeg": "200.000000",
                  "decDeg": "-10.000000", "magVt": "10.500", "colorBv": "",
                  "constellation": "Sco"}

    with tempfile.TemporaryDirectory() as gd, tempfile.TemporaryDirectory() as bd:
        g, b = Path(gd), Path(bd)
        _write_shard(g, "level_1.csv", [good_row_l1], True)
        _write_shard(g, "level_2.csv", [good_row_l2], True)
        _write_shard(b, "level_1.csv", [bad_row_l1], False)
        _write_shard(b, "level_2.csv", [bad_row_l2], False)
        rc_good = run(g)
        print("--- 坏样本应 FAIL ---")
        rc_bad = run(b)

    ok = rc_good == 0 and rc_bad == 1
    print(f"[selftest] 放行样本 exit={rc_good}（期望 0），必失败样本 exit={rc_bad}（期望 1）")
    return 0 if ok else 1


def main() -> int:
    if "--selftest" in sys.argv:
        return _selftest()
    return run(CACHE_DIR)


if __name__ == "__main__":
    sys.exit(main())
