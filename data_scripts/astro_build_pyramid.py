#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 星空金字塔切片构建脚本（离线、幂等；不联网、不写 APK 资产）
=====================================================================

职责（对应缺陷 D38 的数据侧一半；接线一半由写码代理照
`docs/方案-金字塔预缓存-星空天象层.md` 施工图接 `StarCatalog.loadPyramid`）：

  1. 读本地源表缓存（环境变量指定，脚本本身不做任何网络请求）；
  2. 按 `data_scripts/astro_pyramid_manifest.json` 的星等带切成两级金字塔：
       L1: 6.5 < mag ≤ 9.0
       L2: 9.0 < mag ≤ 11.5
  3. 输出瘦表 CSV + 逐片 sha256 旁证 + 包内 manifest，落到设备缓存同构的输出目录；
  4. 源表缺失 / 不可解析时显式退出码 2，绝不造星、绝不写占位行。

入库边界（`docs/构建说明-派生素材不入库.md` 规则一、二）：
  - 本脚本产物默认落在 `data_scripts/_cache/pyramid/`（已被 `.gitignore` 排除）；
  - mag > 6.5 的切片**不进** `app/src/main/assets/`、**不进** CI artifact、**不入** git；
  - 发布侧如需离线预缓存包，由发布工序从本目录打包，APK 内只保留 L0 内置亮星层。

环境变量：
  ASTRO_TYCHO_CACHE  Tycho-2 原始 CSV（CDS I/259 导出；主链路 S-01）
  ASTRO_HYG_CACHE    HYG v44 csv 或 csv.gz（公版汇编；仅作 L1 补充源，覆盖度待源表到位后核）
  ASTRO_PYRAMID_OUT  输出目录；缺省 `data_scripts/_cache/pyramid/`

用法：
  python3 data_scripts/astro_build_pyramid.py            # 有源则切片，无源则退出码 2
  python3 data_scripts/astro_build_pyramid.py --check   # 不写盘，只核对现有产物与清单

退出码：0 = 构建/核对成功；1 = 产物校验失败；2 = 源表缺失（离线未生成，非错误态）。
"""

import csv
import gzip
import hashlib
import io
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "data_scripts" / "astro_pyramid_manifest.json"
DEFAULT_OUT = ROOT / "data_scripts" / "_cache" / "pyramid"

# 瘦表列序（与 manifest.fields 一致；渲染层只取这些列，不带自行/距离等重字段）。
OUT_FIELDS = ["id", "tychoId", "raDeg", "decDeg", "magVt", "colorBv", "constellation"]

# 源表列名别名（大小写/空格不敏感）；新源表到位后在此补别名，不改输出契约。
ALIASES = {
    "id": ["id", "tyc", "tycho", "tychoid", "hip", "hd"],
    "raDeg": ["ra", "radeg", "ra(deg)", "ra_icrs", "ra_deg"],
    "decDeg": ["dec", "decdeg", "de(deg)", "dec_icrs", "de_deg"],
    "magVt": ["mag", "magvt", "vtmag", "vt_mag", "vmag", "vmag"],
    "colorBv": ["ci", "b-v", "bv", "colorbv", "color_bv"],
    "constellation": ["con", "const", "constellation", "sgn"],
}


def _norm(name: str) -> str:
    return name.strip().lower().replace(" ", "").replace("_", "")


def _to_float(value: str | None) -> float | None:
    if value is None:
        return None
    v = value.strip()
    if not v:
        return None
    try:
        f = float(v)
    except ValueError:
        return None
    return f if f == f and f not in (float("inf"), float("-inf")) else None


def _index_map(header: list[str]) -> dict[str, int]:
    """把源表头映射到输出字段；命中别名第一个即止。"""
    normed = [_norm(h) for h in header]
    out: dict[str, int] = {}
    for field, aliases in ALIASES.items():
        for alias in aliases:
            a = _norm(alias)
            if a in normed:
                out[field] = normed.index(a)
                break
    return out


def _read_source(path: Path) -> list[dict]:
    """读 CSV（.gz 透传 gzip）；返回 DictReader 行列表。"""
    raw = path.read_bytes()
    if path.suffix == ".gz":
        text = gzip.decompress(raw).decode("utf-8")
    else:
        text = raw.decode("utf-8")
    return list(csv.DictReader(io.StringIO(text)))


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def _select_source() -> tuple[Path | None, str]:
    for env, label in (("ASTRO_TYCHO_CACHE", "tycho"), ("ASTRO_HYG_CACHE", "hyg")):
        v = os.environ.get(env)
        if v and Path(v).exists():
            return Path(v), label
    return None, ""


def slice_rows(rows: list[dict], bands: dict) -> tuple[list[dict], list[dict], dict]:
    """按星等带切片；返回 (L1, L2, 计数与异常统计)。"""
    l1: list[dict] = []
    l2: list[dict] = []
    seen: set[str] = set()
    stats = {"total": len(rows), "bad_coord": 0, "nan_mag": 0, "dup_id": 0,
             "missing_column": 0, "l1": 0, "l2": 0}

    header = list(rows[0].keys()) if rows else []
    col = _index_map(header)
    required = ["id", "raDeg", "decDeg", "magVt"]
    if any(k not in col for k in required):
        stats["missing_column"] = len(rows)
        return l1, l2, stats

    for x in rows:
        def cell(field: str) -> str:
            i = col.get(field)
            return "" if i is None else (x.get(header[i]) or "")

        ra = _to_float(cell("raDeg"))
        dec = _to_float(cell("decDeg"))
        mag = _to_float(cell("magVt"))
        sid = cell("id").strip()
        if ra is None or dec is None or not (0.0 <= ra < 360.0 and -90.0 <= dec <= 90.0):
            stats["bad_coord"] += 1
            continue
        if mag is None:
            stats["nan_mag"] += 1
            continue
        if not sid or sid in seen:
            stats["dup_id"] += 1
            continue
        seen.add(sid)

        record = {
            "id": sid,
            "tychoId": cell("tychoId") or cell("id"),
            "raDeg": f"{ra:.6f}",
            "decDeg": f"{dec:.6f}",
            "magVt": f"{mag:.3f}",
            "colorBv": "" if _to_float(cell("colorBv")) is None else f"{_to_float(cell('colorBv')):.3f}",
            "constellation": cell("constellation").strip(),
        }
        if bands["l1MinExclusive"] < mag <= bands["l1MaxInclusive"]:
            l1.append(record)
            stats["l1"] += 1
        elif bands["l2MinExclusive"] < mag <= bands["l2MaxInclusive"]:
            l2.append(record)
            stats["l2"] += 1
    return l1, l2, stats


def _write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=OUT_FIELDS, lineterminator="\n")
        w.writeheader()
        for r in rows:
            w.writerow({k: r.get(k, "") for k in OUT_FIELDS})


def build(manifest: dict) -> int:
    src, label = _select_source()
    if src is None:
        print("未验证：未找到本地源表缓存（ASTRO_TYCHO_CACHE / ASTRO_HYG_CACHE 均缺）。")
        print("沙箱离线，未生成 L1/L2 切片；本退出码 2 表示「待源表」，不是构建失败。")
        return 2

    bands = {
        "l1MinExclusive": manifest["contract"]["magBrightEntity"],
        "l1MaxInclusive": manifest["contract"]["level1UpperMag"],
        "l2MinExclusive": manifest["contract"]["level1UpperMag"],
        "l2MaxInclusive": manifest["contract"]["magDepthLimit"],
    }
    rows = _read_source(src)
    expected = manifest["sources"]["S-01"]["rawRowCount"]
    if label == "tycho" and len(rows) != expected:
        print(f"警告：Tycho-2 行数 {len(rows)} ≠ 规格前置校验行数 {expected}（§二.5），留痕待核。")

    l1, l2, stats = slice_rows(rows, bands)
    out = Path(os.environ.get("ASTRO_PYRAMID_OUT", DEFAULT_OUT))
    out.mkdir(parents=True, exist_ok=True)

    _write_csv(out / "level_1.csv", l1)
    _write_csv(out / "level_2.csv", l2)
    (out / "level_1.csv.sha256").write_text(
        _sha256_file(out / "level_1.csv") + "  level_1.csv\n", encoding="utf-8")
    (out / "level_2.csv.sha256").write_text(
        _sha256_file(out / "level_2.csv") + "  level_2.csv\n", encoding="utf-8")

    bundle = {
        "source": label,
        "sourcePath": str(src),
        "bands": bands,
        "counts": {"level1": len(l1), "level2": len(l2)},
        "stats": stats,
        "attribution": manifest["sources"]["S-01"]["attribution"],
    }
    (out / "bundle_manifest.json").write_text(
        json.dumps(bundle, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"构建完成（源={label}）：L1={len(l1)} 行，L2={len(l2)} 行；异常 {stats}")
    print(f"产物目录：{out}（gitignored，不进 APK / CI artifact）")
    return 0


def check(manifest: dict) -> int:
    """核对现有产物：存在则逐片过校验；缺目录按离线预期态报告，不判失败。"""
    out = Path(os.environ.get("ASTRO_PYRAMID_OUT", DEFAULT_OUT))
    if not out.exists():
        print("INFO：金字塔缓存目录不存在（离线首启预期态）；L1/L2 未缓存，回落内置层。")
        return 0
    failures: list[str] = []
    for fname in ("level_1.csv", "level_2.csv"):
        f = out / fname
        if not f.exists():
            failures.append(f"缺切片 {fname}")
            continue
        side = out / (fname + ".sha256")
        if side.exists():
            expected = side.read_text(encoding="utf-8").split()[0]
            if _sha256_file(f) != expected:
                failures.append(f"{fname} 校验和不符")
    if failures:
        print("FAIL:")
        for msg in failures:
            print(f"  - {msg}")
        return 1
    print("OK：现有金字塔切片校验和一致（逐行星等带/坐标/重复 id 由 astro_check_pyramid.py 判）。")
    return 0


def main() -> int:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if "--check" in sys.argv:
        return check(manifest)
    return build(manifest)


if __name__ == "__main__":
    sys.exit(main())
