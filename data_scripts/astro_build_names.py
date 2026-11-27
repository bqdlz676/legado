#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 星空星名表可重跑生成脚本（验收 D4 前半）。

职责（规格 §四 第 7、11 条；§十四.4 三名字段）：
  - 保留既有 440 条 `en-iau`（IAU WGSN 西名）记录原样不动；
  - 在同一 `starId` 上补 `zh-modern`（现代中文通用星名）与 `zh-xingguan`（星官位次名）；
  - 无公版依据的星一律留空，不编造古名 / 今名。

两类中文名的出处口径（互不混用）：
  1. `zh-xingguan`（古星官位次名，如「参宿四」「井宿一」）：
     只从仓库已登记的 `asterism_members.csv` 成员锚点表取位次名，出处逐条回链
     `asterisms.csv` 的 source 列（《史记·天官书》卷二十七 / 《晋书·天文志》/《步天歌》）。
     成员表未登记的星官成员，待古籍逐星校订后再补，本脚本不自行推定位次。
  2. `zh-modern`（现代中文通用星名，如「天狼」「织女一」「心宿二」「天津四」）：
     取全国科学技术名词审定委员会《天文学名词》（第二版，1998）公布的通用中文名，
     并按拜耳字母 + 88 星座缩写与内置 BSC5/HYG v44 星表交叉核对身份；字典内每条都带
     身份锚点（星座 + 拜耳字母），星表错位时 --check 直接 FAIL，防止张冠李戴。

用法：
  python3 data_scripts/astro_build_names.py          # 重写 star_names.csv（幂等）
  python3 data_scripts/astro_build_names.py --check   # 只跑断言不写盘

退出码：0 = 全部断言通过；1 = 存在 FAIL 项。
"""

import argparse
import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASTRO = ROOT / "app" / "src" / "main" / "assets" / "astro"

STARS_CSV = ASTRO / "stars.csv"
NAMES_CSV = ASTRO / "star_names.csv"
MEMBERS_CSV = ASTRO / "asterism_members.csv"
ASTERISMS_CSV = ASTRO / "asterisms.csv"

LANG_IAU = "en-iau"
LANG_MODERN = "zh-modern"
LANG_XINGGUAN = "zh-xingguan"
ALLOWED_LANG = {LANG_IAU, LANG_MODERN, LANG_XINGGUAN}

# 现代中文名统一出处（《天文学名词》第二版为公版公布的规范名词；交叉核对源 BSC5/HYG v44）。
MODERN_SOURCE = (
    "《天文学名词》（第二版，全国科学技术名词审定委员会，1998）；"
    "与 BSC5/HYG v44 按拜耳字母 + 星座缩写交叉核对"
)

# ─────────────────────────────────────────────────────────────────────────────
# zh-modern 字典：starId -> (现代中文名, 星座三字母, 拜耳字母)
#
# 只收录身份可被「星座 + 拜耳字母」双重锚定、且中文名为规范公布名的亮星；
# 南半球远星、拜耳名不规范的双星伴星、无名暗星一律不录，留空不编。
# 身份锚点用于 --check：与 stars.csv 不符即 FAIL，避免星表重排后误标。
# ─────────────────────────────────────────────────────────────────────────────
MODERN: dict[str, tuple[str, str, str]] = {
    # 北斗七星 + 辅（UMa）
    "53905": ("天枢", "UMa", "Alp"),
    "53754": ("天璇", "UMa", "Bet"),
    "57828": ("天玑", "UMa", "Gam"),
    "59592": ("天权", "UMa", "Del"),
    "62757": ("玉衡", "UMa", "Eps"),
    "65173": ("开阳", "UMa", "Zet"),
    "67088": ("摇光", "UMa", "Eta"),
    "65272": ("辅", "UMa", ""),
    # 北极（UMi）
    "11734": ("勾陈一", "UMi", "Alp"),
    # 仙女座（And）
    "676": ("壁宿二", "And", "Alp"),
    "5436": ("奎宿九", "And", "Bet"),
    "9618": ("天大将军一", "And", "Gam-1"),
    # 飞马座（Peg）
    "113603": ("室宿一", "Peg", "Alp"),
    "113521": ("室宿二", "Peg", "Bet"),
    "1065": ("壁宿一", "Peg", "Gam"),
    "106972": ("危宿三", "Peg", "Eps"),
    # 白羊座（Ari）
    "9861": ("娄宿三", "Ari", "Alp"),
    "8884": ("娄宿一", "Ari", "Bet"),
    "8813": ("娄宿二", "Ari", "Gam-2"),
    # 金牛座（Tau）
    "21368": ("毕宿五", "Tau", "Alp"),
    "25364": ("五车五", "Tau", "Bet"),
    "17661": ("昴宿六", "Tau", "Eta"),
    "26386": ("天关", "Tau", "Zet"),
    # 猎户座（Ori）
    "27919": ("参宿四", "Ori", "Alp"),
    "24378": ("参宿七", "Ori", "Bet"),
    "25273": ("参宿五", "Ori", "Gam"),
    "25865": ("参宿三", "Ori", "Del"),
    "26246": ("参宿二", "Ori", "Eps"),
    "26662": ("参宿一", "Ori", "Zet"),
    "27298": ("参宿六", "Ori", "Kap"),
    "26142": ("觜宿一", "Ori", "Lam"),
    # 双子座（Gem）
    "36744": ("北河二", "Gem", "Alp"),
    "37718": ("北河三", "Gem", "Bet"),
    "31601": ("井宿七", "Gem", "Gam"),
    "30270": ("井宿一", "Gem", "Mu"),
    "32161": ("井宿三", "Gem", "Eps"),
    "35453": ("井宿四", "Gem", "Del"),
    # 大犬座（CMa）
    "32263": ("天狼", "CMa", "Alp"),
    "34354": ("弧矢一", "CMa", "Del"),
    "33492": ("弧矢七", "CMa", "Eps"),
    "35806": ("弧矢二", "CMa", "Eta"),
    # 小犬座（CMi）
    "37173": ("南河三", "CMi", "Alp"),
    "36087": ("南河二", "CMi", "Bet"),
    # 船底座（Car）
    "30365": ("老人", "Car", "Alp"),
    # 长蛇座（Hya）
    "46259": ("星宿一", "Hya", "Alp"),
    # 狮子座（Leo）
    "49528": ("轩辕十四", "Leo", "Alp"),
    "50440": ("轩辕十二", "Leo", "Gam-1"),
    "50193": ("轩辕十一", "Leo", "Zet"),
    "47772": ("轩辕九", "Leo", "Eps"),
    "48318": ("轩辕十", "Leo", "Mu"),
    "57459": ("五帝座一", "Leo", "Bet"),
    # 室女座（Vir）
    "65269": ("角宿一", "Vir", "Alp"),
    "61748": ("东上相", "Vir", "Gam"),
    # 牧夫座（Boo）
    "69451": ("大角", "Boo", "Alp"),
    # 北冕座（CrB）
    "76035": ("贯索四", "CrB", "Alp"),
    # 天蝎座（Sco）
    "80519": ("心宿二", "Sco", "Alp"),
    "78029": ("房宿一", "Sco", "Pi"),
    "77868": ("房宿二", "Sco", "Rho"),
    "78165": ("房宿三", "Sco", "Del"),
    "78580": ("房宿四", "Sco", "Bet-1"),
    "79870": ("房宿五", "Sco", "Sig"),
    "85665": ("尾宿八", "Sco", "Lam"),
    "85436": ("尾宿九", "Sco", "Ups"),
    "85965": ("尾宿五", "Sco", "The"),
    # 人马座（Sgr）
    "88361": ("斗宿一", "Sgr", "Gam-2"),
    "90217": ("斗宿二", "Sgr", "Lam"),
    "89653": ("斗宿三", "Sgr", "Del"),
    "92564": ("斗宿四", "Sgr", "Sig"),
    "89906": ("斗宿六", "Sgr", "Eps"),
    "93213": ("斗宿七", "Sgr", "Zet"),
    # 天鹅座（Cyg）
    "101767": ("天津四", "Cyg", "Alp"),
    "100128": ("天津一", "Cyg", "Gam"),
    "96858": ("天津二", "Cyg", "Del"),
    "102157": ("天津九", "Cyg", "Eps"),
    "95648": ("辇道增七", "Cyg", "Bet-1"),
    # 天鹰座（Aql）
    "97338": ("河鼓二", "Aql", "Alp"),
    "97725": ("河鼓一", "Aql", "Bet"),
    "96970": ("河鼓三", "Aql", "Gam"),
    # 天琴座（Lyr）
    "90979": ("织女一", "Lyr", "Alp"),
    "92133": ("织女二", "Lyr", "Bet"),
    "92902": ("织女三", "Lyr", "Gam"),
    # 蛇夫座（Oph）
    "85769": ("侯", "Oph", "Alp"),
    # 武仙座（Her）
    "84086": ("帝座", "Her", "Alp-1"),
    # 乌鸦座（Crv）
    "59621": ("轸宿一", "Crv", "Gam"),
    "60776": ("轸宿三", "Crv", "Del"),
    "61166": ("轸宿四", "Crv", "Bet"),
    "59020": ("轸宿五", "Crv", "Alp"),
    # 半人马座（Cen）
    "68483": ("马腹一", "Cen", "Bet"),
    "71456": ("南门二", "Cen", "Alp-1"),
    # 南十字座（Cru）
    "60530": ("十字架二", "Cru", "Alp-1"),
    "60893": ("十字架一", "Cru", "Gam"),
    "62239": ("十字架三", "Cru", "Bet"),
    # 波江座（Eri）
    "7574": ("水委一", "Eri", "Alp"),
    # 鲸鱼座（Cet）
    "3413": ("土司空", "Cet", "Bet"),
    "14100": ("天囷一", "Cet", "Alp"),
    "10800": ("蒭藁增二", "Cet", "Omi"),
    # 英仙座（Per）
    "14540": ("大陵五", "Per", "Bet"),
    "15824": ("天船三", "Per", "Alp"),
    # 御夫座（Aur）
    "24549": ("五车二", "Aur", "Alp"),
    "28288": ("五车三", "Aur", "Bet"),
    # 天龙座（Dra）
    "68537": ("右枢", "Dra", "Alp"),
    "87561": ("天棓四", "Dra", "Gam"),
    "85410": ("天棓三", "Dra", "Bet"),
    # 南鱼座（PsA）
    "113008": ("北落师门", "PsA", "Alp"),
    # 猎犬座（CVn）
    "62925": ("常陈一", "CVn", "Alp-2"),
}


def _read_csv(path: Path) -> list[dict]:
    with path.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def load_star_ids() -> dict[str, dict]:
    """starId -> 星表行（身份锚点核对用）。"""
    return {row["id"]: row for row in _read_csv(STARS_CSV)}


def load_xingguan() -> dict[str, tuple[str, str]]:
    """starId -> (位次名, 出处)；严格取自已登记成员锚点表，不自行推定。"""
    asterisms = {row["code"]: row for row in _read_csv(ASTERISMS_CSV)}
    out: dict[str, tuple[str, str]] = {}
    for m in _read_csv(MEMBERS_CSV):
        code = m["asterismCode"]
        index = m["memberIndex"].strip()
        if not index:
            continue
        source = asterisms.get(code, {}).get("source", "").strip()
        out[m["starId"].strip()] = (index, source)
    return out


def build_rows() -> tuple[list[dict], dict]:
    stars = load_star_ids()
    xingguan = load_xingguan()

    existing = _read_csv(NAMES_CSV)
    iau_rows = [r for r in existing if r["lang"] == LANG_IAU]

    rows: list[dict] = []
    modern_ids: set[str] = set()
    xingguan_ids: set[str] = set()

    # 1) 既有 en-iau 原样保留（不改表结构、不改西名）。
    for r in iau_rows:
        rows.append({
            "starId": r["starId"].strip(),
            "name": r["name"],
            "lang": LANG_IAU,
            "source": r["source"],
            "isPrimary": r["isPrimary"],
        })

    # 2) zh-xingguan：成员锚点表登记一条落一条。
    for star_id, (index, source) in sorted(xingguan.items(), key=lambda kv: int(kv[0])):
        rows.append({
            "starId": star_id,
            "name": index,
            "lang": LANG_XINGGUAN,
            "source": source,
            "isPrimary": "0",
        })
        xingguan_ids.add(star_id)

    # 3) zh-modern：规范中文名字典；身份锚点在 --check 阶段核对。
    for star_id, (zh, _cons, _bayer) in sorted(MODERN.items(), key=lambda kv: int(kv[0])):
        rows.append({
            "starId": star_id,
            "name": zh,
            "lang": LANG_MODERN,
            "source": MODERN_SOURCE,
            "isPrimary": "1",
        })
        modern_ids.add(star_id)

    stats = {
        "stars": stars,
        "iau_count": len(iau_rows),
        "modern_ids": modern_ids,
        "xingguan_ids": xingguan_ids,
        "total_rows": len(rows),
    }
    return rows, stats


def check(rows: list[dict], stats: dict) -> list[str]:
    failures: list[str] = []
    stars: dict[str, dict] = stats["stars"]

    # 断言 1：starId 必须存在于 stars.csv。
    for r in rows:
        if r["starId"] not in stars:
            failures.append(f"starId {r['starId']} 不在 stars.csv（{r['lang']} {r['name']}）")

    # 断言 2：lang 取值合法。
    for r in rows:
        if r["lang"] not in ALLOWED_LANG:
            failures.append(f"非法 lang={r['lang']}（starId={r['starId']}）")

    # 断言 3：每颗星至多 1 条 zh-modern；zh-xingguan 可缺；(starId, lang) 不重复。
    seen_modern: set[str] = set()
    seen_xg: set[str] = set()
    seen_pair: set[tuple[str, str]] = set()
    for r in rows:
        pair = (r["starId"], r["lang"])
        if pair in seen_pair:
            failures.append(f"重复 (starId, lang)={pair}")
        seen_pair.add(pair)
        if r["lang"] == LANG_MODERN:
            if r["starId"] in seen_modern:
                failures.append(f"zh-modern 重复：starId={r['starId']}")
            seen_modern.add(r["starId"])
        if r["lang"] == LANG_XINGGUAN:
            if r["starId"] in seen_xg:
                failures.append(f"zh-xingguan 重复：starId={r['starId']}")
            seen_xg.add(r["starId"])

    # 断言 4：zh-modern 身份锚点与星表一致（星座 + 拜耳字母），防错位误标。
    for star_id, (zh, cons, bayer) in MODERN.items():
        s = stars.get(star_id)
        if s is None:
            continue  # 存在性已在断言 1 报
        if s["constellation"] != cons:
            failures.append(
                f"身份锚点不符：starId={star_id} {zh} 期望星座 {cons}，实表 {s['constellation']}"
            )
        if bayer and s["bayer"] != bayer:
            failures.append(
                f"身份锚点不符：starId={star_id} {zh} 期望拜耳 {bayer}，实表 {s['bayer']}"
            )

    # 断言 5：既有 en-iau 一条不丢。
    if stats["iau_count"] != 440:
        failures.append(f"en-iau 记录数={stats['iau_count']}，期望 440")

    return failures


def write_csv(rows: list[dict]) -> None:
    rows_sorted = sorted(
        rows,
        key=lambda r: (int(r["starId"]), {"en-iau": 0, "zh-xingguan": 1, "zh-modern": 2}[r["lang"]]),
    )
    # lineterminator="\n"：保持与既有资产一致的 LF 换行，避免整文件 CRLF 抖动刷大 diff。
    with NAMES_CSV.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(
            f,
            fieldnames=["starId", "name", "lang", "source", "isPrimary"],
            lineterminator="\n",
        )
        w.writeheader()
        w.writerows(rows_sorted)


def main() -> int:
    parser = argparse.ArgumentParser(description="星空星名表生成 / 校验")
    parser.add_argument("--check", action="store_true", help="只断言不写盘")
    args = parser.parse_args()

    rows, stats = build_rows()
    failures = check(rows, stats)

    modern_n = len(stats["modern_ids"])
    xg_n = len(stats["xingguan_ids"])
    iau_n = stats["iau_count"]
    blank_n = iau_n - modern_n  # 仅有西名、无中文名的星

    print(f"en-iau 保留：{iau_n} 条")
    print(f"zh-modern 补录：{modern_n} 条")
    print(f"zh-xingguan 补录：{xg_n} 条（仅成员锚点表登记星）")
    print(f"仅西名、中文名留空：{blank_n} 条")

    if failures:
        print("NAMES CHECK: FAIL")
        for f in failures:
            print("  " + f)
        return 1

    print("NAMES CHECK: PASS")
    if args.check:
        print("（--check 模式，未写盘）")
        return 0

    write_csv(rows)
    print(f"已写盘：{NAMES_CSV.relative_to(ROOT)}（{len(rows)} 行数据）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
