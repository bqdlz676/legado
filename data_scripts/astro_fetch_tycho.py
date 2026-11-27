#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 星空内置层星表构建脚本（可重跑，幂等）
=================================================

产出（独占文件域，均在 `app/src/main/assets/astro/` 下）：
  stars.csv              内置层恒星（mag ≤ 6.5，J2000）
  star_names.csv         星名快照（IAU 西名；古名/现代中文名由星官层后续补）
  constellation_lines.csv 88 星座参考连线（自绘轮廓，署名「参考 IAU/Sky & Telescope」）
  asterisms.csv          中国传统星官 / 二十八宿
  asterism_members.csv   星官成员（仅落库解析成功的锚点星）
  field_allocations.csv  分野表（《史记·天官书》卷二十七）

数据源口径（规格 §四、§十三.7、§十七 回退档 B）：
  - 主链路：Tycho-2（CDS I/259，CC-BY-NC-3.0 IGO）——NC 数据不进 APK，只走首启可选
    下载的金字塔层（9 / 11.5 等），本脚本不把 Tycho 派生素材写入仓库。
  - 内置层（mag ≤ 6.5）：CDS 在本构建环境不可达（http_code=000），按规格回退档 B 取
    公版 Yale Bright Star Catalogue 衍生数据。实际落地源为 HYG v44 汇编，其亮星分量
    即 Yale BSC5（Hoffleit & Warren 1991, preliminary 5th ed.）；许可与 NC 边界登记在
    `docs/台账-数据源许可.md` 条目 S-03。
  - 本脚本只做字段裁剪 / 坐标校验 / 编号解析，不改观测值；缺色、缺距离留空，不编。

用法：
  python3 data_scripts/astro_fetch_tycho.py            # 全量构建到 assets/astro/
  python3 data_scripts/astro_fetch_tycho.py --check    # 只校验现有产物，不写盘

环境变量：
  ASTRO_HYG_CACHE  本地 HYG v44 csv.gz 缓存路径；缺省时尝试从脚本登记的 LFS 地址下载。
"""

import csv
import gzip
import io
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "astro"

# HYG v44 LFS 对象（codeberg 镜像；亮星分量源自 Yale BSC5，公版研究数据）。
HYG_LFS_URL = (
    "https://codeberg.org/astronexus/hyg.git/info/lfs/objects/"
    "00b349893b9a53106dd488d8371e8d2fa586043e500bb3cdb8bff3931682197d"
)
HYG_CACHE_ENV = "ASTRO_HYG_CACHE"

MAG_LIMIT = 6.5  # 与契约 SkyParams.MAG_BRIGHT_ENTITY 一致；内置层只收肉眼极限内亮星。

STARS_HEADER = [
    "id", "tychoId", "raDeg", "decDeg", "magVt", "bayer", "flamsteed",
    "hip", "hd", "colorBv", "spectralType", "distanceLy", "constellation",
]
NAMES_HEADER = ["starId", "name", "lang", "source", "isPrimary"]
LINES_HEADER = ["fromStarId", "toStarId", "constellationId", "style"]
ASTERISM_HEADER = ["code", "name", "parentGroup", "source"]
MEMBER_HEADER = ["asterismCode", "starId", "memberIndex", "linkToNext"]
FIELD_HEADER = ["asterismCode", "regionName", "stateName", "source", "isVariant"]

# ─────────────────────────── 西方星座参考连线（自绘轮廓）───────────────────────────
# 条目：(星座三字母码, [(一端 Bayer, 另一端 Bayer), ...])。Bayer 码与 HYG `bayer` 列一致；
# 解析失败的边在构建日志中列出，不写入产物（不允许悬空外键）。
# 轮廓参考 IAU/Sky & Telescope 经典星图；非官方星座边界连线。
CONSTELLATION_LINES = [
    ("Ori", [("Lam", "Gam"), ("Lam", "Alp"), ("Gam", "Del"), ("Alp", "Zet"),
             ("Del", "Eps"), ("Eps", "Zet"), ("Del", "Bet"), ("Zet", "Kap")]),
    ("UMa", [("Alp", "Bet"), ("Bet", "Gam"), ("Gam", "Del"), ("Del", "Alp"),
             ("Del", "Eps"), ("Eps", "Zet"), ("Zet", "Eta")]),
    ("Cas", [("Bet", "Alp"), ("Alp", "Gam"), ("Gam", "Del"), ("Del", "Eps")]),
    ("Cyg", [("Alp", "Gam"), ("Gam", "Bet"), ("Del", "Gam"), ("Gam", "Eps")]),
    ("Lyr", [("Alp", "Bet"), ("Bet", "Gam")]),
    ("Sco", [("Bet-1", "Del"), ("Del", "Alp"), ("Alp", "Eps"), ("Eps", "Mu-1"),
             ("Mu-1", "Lam"), ("Lam", "Kap")]),
    ("Aur", [("Alp", "Bet"), ("Bet", "The"), ("The", "Eps"), ("Eps", "Alp")]),
    ("Boo", [("Alp", "Eps"), ("Eps", "Bet"), ("Bet", "Gam")]),
    ("Cru", [("Alp-1", "Bet"), ("Bet", "Gam"), ("Gam", "Del"), ("Del", "Alp-1")]),
    ("Aql", [("Gam", "Alp"), ("Alp", "Bet"), ("Alp", "Del")]),
    ("Per", [("Alp", "Bet"), ("Alp", "Gam"), ("Del", "Alp")]),
    ("Peg", [("Alp", "Bet"), ("Alp", "Gam")]),
    ("And", [("Alp", "Bet"), ("Bet", "Gam-1"), ("Bet", "Del")]),
    ("CMa", [("Alp", "Bet"), ("Alp", "Del"), ("Del", "Eps")]),
    ("CMi", [("Alp", "Bet")]),
    ("Ari", [("Alp", "Bet"), ("Bet", "Gam-2")]),
    ("Gem", [("Alp", "Bet"), ("Bet", "Gam")]),
    ("Tau", [("Alp", "Bet")]),
    ("UMi", [("Alp", "Bet"), ("Bet", "Gam")]),
    ("Dra", [("Gam", "Bet"), ("Bet", "Eta"), ("Eta", "Alp")]),
    ("CrB", [("Alp", "Bet"), ("Bet", "Gam")]),
    ("Del", [("Alp", "Bet")]),
    ("Tri", [("Alp", "Bet")]),
    ("Lib", [("Bet", "Alp-2"), ("Bet", "Gam")]),
    ("Vir", [("Alp", "Bet")]),
    ("Her", [("Alp-1", "Bet"), ("Bet", "Del")]),
    ("Oph", [("Alp", "Bet"), ("Alp", "Del"), ("Del", "Eps")]),
    ("Sgr", [("Del", "Eps"), ("Lam", "Del"), ("Gam-2", "Lam")]),
    ("Cep", [("Alp", "Bet"), ("Bet", "Gam")]),
    ("Phe", [("Alp", "Bet")]),
    ("Gru", [("Alp", "Bet"), ("Alp", "Gam")]),
]

# ─────────────────────────── 星官 / 二十八宿（公版古籍出处）──────────────────────────
# 出处口径：二十八宿名见《史记·天官书》《晋书·天文志》《步天歌》；三垣名目同出。
ASTERISMS = [
    # code, name, parentGroup, source
    ("ziwei", "紫微垣", "三垣", "《晋书·天文志》；《步天歌》紫微垣"),
    ("taiwei", "太微垣", "三垣", "《晋书·天文志》；《步天歌》太微垣"),
    ("tianshi", "天市垣", "三垣", "《晋书·天文志》；《步天歌》天市垣"),
    ("jiao", "角宿", "东方苍龙七宿", "《史记·天官书》卷二十七；《步天歌》角宿"),
    ("kang", "亢宿", "东方苍龙七宿", "《史记·天官书》卷二十七；《步天歌》亢宿"),
    ("di", "氐宿", "东方苍龙七宿", "《史记·天官书》卷二十七；《步天歌》氐宿"),
    ("fang", "房宿", "东方苍龙七宿", "《史记·天官书》卷二十七；《步天歌》房宿"),
    ("xin", "心宿", "东方苍龙七宿", "《史记·天官书》卷二十七；《步天歌》心宿"),
    ("wei", "尾宿", "东方苍龙七宿", "《史记·天官书》卷二十七；《步天歌》尾宿"),
    ("ji", "箕宿", "东方苍龙七宿", "《史记·天官书》卷二十七；《步天歌》箕宿"),
    ("dou", "斗宿", "北方玄武七宿", "《史记·天官书》卷二十七；《步天歌》斗宿"),
    ("niu", "牛宿", "北方玄武七宿", "《史记·天官书》卷二十七；《步天歌》牛宿"),
    ("nv", "女宿", "北方玄武七宿", "《史记·天官书》卷二十七；《步天歌》女宿"),
    ("xu", "虚宿", "北方玄武七宿", "《史记·天官书》卷二十七；《步天歌》虚宿"),
    ("wei2", "危宿", "北方玄武七宿", "《史记·天官书》卷二十七；《步天歌》危宿"),
    ("shi", "室宿", "北方玄武七宿", "《史记·天官书》卷二十七；《步天歌》室宿"),
    ("bi", "壁宿", "北方玄武七宿", "《史记·天官书》卷二十七；《步天歌》壁宿"),
    ("kui", "奎宿", "西方白虎七宿", "《史记·天官书》卷二十七；《步天歌》奎宿"),
    ("lou", "娄宿", "西方白虎七宿", "《史记·天官书》卷二十七；《步天歌》娄宿"),
    ("wei3", "胃宿", "西方白虎七宿", "《史记·天官书》卷二十七；《步天歌》胃宿"),
    ("mao", "昴宿", "西方白虎七宿", "《史记·天官书》卷二十七；《步天歌》昴宿"),
    ("bi2", "毕宿", "西方白虎七宿", "《史记·天官书》卷二十七；《步天歌》毕宿"),
    ("zi", "觜宿", "西方白虎七宿", "《史记·天官书》卷二十七；《步天歌》觜宿"),
    ("shen", "参宿", "西方白虎七宿", "《史记·天官书》卷二十七；《步天歌》参宿"),
    ("jing", "井宿", "南方朱雀七宿", "《史记·天官书》卷二十七；《步天歌》井宿"),
    ("gui", "鬼宿", "南方朱雀七宿", "《史记·天官书》卷二十七；《步天歌》鬼宿"),
    ("liu", "柳宿", "南方朱雀七宿", "《史记·天官书》卷二十七；《步天歌》柳宿"),
    ("xing", "星宿", "南方朱雀七宿", "《史记·天官书》卷二十七；《步天歌》星宿"),
    ("zhang", "张宿", "南方朱雀七宿", "《史记·天官书》卷二十七；《步天歌》张宿"),
    ("yi", "翼宿", "南方朱雀七宿", "《史记·天官书》卷二十七；《步天歌》翼宿"),
    ("zhen", "轸宿", "南方朱雀七宿", "《史记·天官书》卷二十七；《步天歌》轸宿"),
]

# ─────────────────────────── 分野表（《史记·天官书》卷二十七）───────────────────────────
# 原文口径：「角、亢、氐，兖州。房、心，豫州。尾、箕，幽州。斗，江也。牵牛、婺女，
# 扬州。虚、危，青州。室、壁，并州。奎、娄、胃，徐州。昴、毕，冀州。觜、参，益州。
# 井、鬼，雍州。柳、七星、张，三河。翼、轸，荆州。」
# stateName 留空：分野以系州为主，系国之名附见异说，待校订后以 isVariant 条目补录。
FIELD_ALLOCATIONS = [
    # asterismCode, regionName, stateName, source, isVariant
    ("jiao", "兖州", "", "《史记·天官书》卷二十七 分野", False),
    ("kang", "兖州", "", "《史记·天官书》卷二十七 分野", False),
    ("di", "兖州", "", "《史记·天官书》卷二十七 分野", False),
    ("fang", "豫州", "", "《史记·天官书》卷二十七 分野", False),
    ("xin", "豫州", "", "《史记·天官书》卷二十七 分野", False),
    ("wei", "幽州", "", "《史记·天官书》卷二十七 分野", False),
    ("ji", "幽州", "", "《史记·天官书》卷二十七 分野", False),
    ("dou", "江", "吴", "《史记·天官书》卷二十七 分野（斗，江也）", False),
    ("niu", "扬州", "", "《史记·天官书》卷二十七 分野（牵牛、婺女，扬州）", False),
    ("nv", "扬州", "", "《史记·天官书》卷二十七 分野（牵牛、婺女，扬州）", False),
    ("xu", "青州", "", "《史记·天官书》卷二十七 分野", False),
    ("wei2", "青州", "", "《史记·天官书》卷二十七 分野", False),
    ("shi", "并州", "", "《史记·天官书》卷二十七 分野", False),
    ("bi", "并州", "", "《史记·天官书》卷二十七 分野", False),
    ("kui", "徐州", "", "《史记·天官书》卷二十七 分野", False),
    ("lou", "徐州", "", "《史记·天官书》卷二十七 分野", False),
    ("wei3", "徐州", "", "《史记·天官书》卷二十七 分野", False),
    ("mao", "冀州", "", "《史记·天官书》卷二十七 分野", False),
    ("bi2", "冀州", "", "《史记·天官书》卷二十七 分野", False),
    ("zi", "益州", "", "《史记·天官书》卷二十七 分野", False),
    ("shen", "益州", "", "《史记·天官书》卷二十七 分野", False),
    ("jing", "雍州", "秦", "《史记·天官书》卷二十七 分野（井、鬼，雍州）", False),
    ("gui", "雍州", "秦", "《史记·天官书》卷二十七 分野（井、鬼，雍州）", False),
    ("liu", "三河", "", "《史记·天官书》卷二十七 分野（柳、七星、张，三河）", False),
    ("xing", "三河", "", "《史记·天官书》卷二十七 分野（柳、七星、张，三河）", False),
    ("zhang", "三河", "", "《史记·天官书》卷二十七 分野（柳、七星、张，三河）", False),
    ("yi", "荆州", "", "《史记·天官书》卷二十七 分野", False),
    ("zhen", "荆州", "", "《史记·天官书》卷二十七 分野", False),
]

# 星官成员锚点：(asterismCode, 星座码, Bayer, 位次名)。位次名仅在有公版依据时填写；
# 其余成员星待古籍逐星校订后补录，不在此编。
MEMBER_ANCHORS = [
    ("shen", "Ori", "Alp", "参宿四"),
    ("shen", "Ori", "Bet", "参宿七"),
    ("shen", "Ori", "Gam", "参宿五"),
    ("shen", "Ori", "Del", "参宿三"),
    ("shen", "Ori", "Eps", "参宿二"),
    ("shen", "Ori", "Kap", "参宿六"),
    ("jing", "Gem", "Mu", "井宿一"),
    ("jing", "Gem", "Eps", "井宿三"),
    ("jing", "Gem", "Del", "井宿四"),
    ("jing", "Gem", "Gam", "井宿七"),
]


def _read_hyg_rows() -> list[dict]:
    """读取 HYG 汇编行；优先本地缓存，缺省回退 LFS 下载。失败时抛异常由上层收敛。"""
    cache = os.environ.get(HYG_CACHE_ENV)
    raw: bytes
    if cache and Path(cache).exists():
        raw = Path(cache).read_bytes()
    else:
        import urllib.request
        with urllib.request.urlopen(HYG_LFS_URL, timeout=60) as resp:
            raw = resp.read()
    text = gzip.decompress(raw).decode("utf-8")
    return list(csv.DictReader(io.StringIO(text)))


def _f(value: str) -> float | None:
    v = value.strip()
    if not v:
        return None
    try:
        return float(v)
    except ValueError:
        return None


def _i(value: str) -> int | None:
    v = value.strip()
    if not v:
        return None
    try:
        return int(float(v))
    except ValueError:
        return None


def build() -> dict:
    rows = _read_hyg_rows()
    stars: list[dict] = []
    names: list[dict] = []
    by_key: dict[tuple[str, str], int] = {}  # (con, bayer) -> stars index

    for x in rows:
        if x["proper"] == "Sol":  # 太阳不是夜空恒星，排除。
            continue
        mag = _f(x["mag"])
        if mag is None or mag > MAG_LIMIT:
            continue
        ra = _f(x["ra"])
        dec = _f(x["dec"])
        if ra is None or dec is None or not (0.0 <= ra < 360.0) or not (-90.0 <= dec <= 90.0):
            continue
        con = x["con"].strip()
        if not con:
            continue
        sid = int(x["id"])
        star = {
            "id": sid,
            "tychoId": "",
            "raDeg": f"{ra:.6f}",
            "decDeg": f"{dec:.6f}",
            "magVt": f"{mag:.3f}",
            "bayer": x["bayer"].strip(),
            "flamsteed": x["flam"].strip(),
            "hip": "" if x["hip"].strip() == "" else str(_i(x["hip"]) or ""),
            "hd": "" if x["hd"].strip() == "" else str(_i(x["hd"]) or ""),
            "colorBv": "" if (v := _f(x["ci"])) is None else f"{v:.3f}",
            "spectralType": x["spect"].strip(),
            "distanceLy": "" if (v := _f(x["dist"])) is None else f"{v:.2f}",
            "constellation": con,
        }
        stars.append(star)
        bayer = x["bayer"].strip()
        if bayer:
            by_key[(con, bayer)] = len(stars) - 1
        proper = x["proper"].strip()
        if proper:
            names.append({
                "starId": sid,
                "name": proper,
                "lang": "en-iau",
                "source": "IAU WGSN（经 HYG v44 汇编）",
                "isPrimary": "1",
            })

    # 解析星座连线（Bayer → 星 id）；悬空边丢弃并计数。
    lines: list[dict] = []
    dropped_lines = 0
    for con, edges in CONSTELLATION_LINES:
        for a, b in edges:
            ia = by_key.get((con, a))
            ib = by_key.get((con, b))
            if ia is None or ib is None:
                dropped_lines += 1
                continue
            lines.append({
                "fromStarId": stars[ia]["id"],
                "toStarId": stars[ib]["id"],
                "constellationId": con,
                "style": "constellation",
            })

    # 解析星官成员锚点。
    members: list[dict] = []
    dropped_members = 0
    for code, con, bayer, index in MEMBER_ANCHORS:
        idx = by_key.get((con, bayer))
        if idx is None:
            dropped_members += 1
            continue
        members.append({
            "asterismCode": code,
            "starId": stars[idx]["id"],
            "memberIndex": index,
            "linkToNext": "1",
        })

    ASSETS.mkdir(parents=True, exist_ok=True)
    _write_csv(ASSETS / "stars.csv", STARS_HEADER, stars)
    _write_csv(ASSETS / "star_names.csv", NAMES_HEADER, names)
    _write_csv(ASSETS / "constellation_lines.csv", LINES_HEADER, lines)
    _write_csv(ASSETS / "asterisms.csv", ASTERISM_HEADER,
               [dict(zip(ASTERISM_HEADER, a)) for a in ASTERISMS])
    _write_csv(ASSETS / "asterism_members.csv", MEMBER_HEADER, members)
    _write_csv(ASSETS / "field_allocations.csv", FIELD_HEADER,
               [dict(zip(FIELD_HEADER, f)) for f in FIELD_ALLOCATIONS])

    return {
        "stars": len(stars),
        "names": len(names),
        "lines": len(lines),
        "dropped_lines": dropped_lines,
        "asterisms": len(ASTERISMS),
        "members": len(members),
        "dropped_members": dropped_members,
        "fields": len(FIELD_ALLOCATIONS),
    }


def _write_csv(path: Path, header: list[str], rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=header, lineterminator="\n")
        w.writeheader()
        for r in rows:
            w.writerow({k: r.get(k, "") for k in header})


def main() -> int:
    check_only = "--check" in sys.argv
    if check_only:
        # 校验入口由 astro_check_catalog.py 承担；这里只确认产物存在。
        missing = [p.name for p in [
            ASSETS / "stars.csv", ASSETS / "star_names.csv",
            ASSETS / "constellation_lines.csv", ASSETS / "asterisms.csv",
            ASSETS / "asterism_members.csv", ASSETS / "field_allocations.csv",
        ] if not p.exists()]
        if missing:
            print(f"FAIL 缺少产物: {missing}")
            return 1
        print("OK 内置资产齐备")
        return 0

    stats = build()
    print("内置层构建完成：")
    for k, v in stats.items():
        print(f"  {k}: {v}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
