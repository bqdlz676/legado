#!/usr/bin/env python3
"""设计 token 契约守卫（Q-272 可判定体验基线）。

为什么需要它：既有 50 个门禁全部是否定式（有没有文档状态词 / 回源 / 锚点 / 混桶），
对「这东西到底好不好看、手感对不对」零覆盖。本脚本把规格冻结的设计常量
（颜色 / 几何 / 动效 / 内存 / 帧率预算）落成 `docs/契约-设计token.json`，
逐项比对源码常量：契约值 / 源码实际值 / 出处行，不一致即红。

判据：
  - 默认（校验）：读契约 JSON，`check=source` 的项逐条解析主锚点与 aliases 锚点，
    按 kind 归一化后比对；任一缺失 / 不一致判 FAIL，退出码 1。
  - `check=spec-only` 的项：真值在规格文档 / 真机 bench / 帧像素里，源码暂无常量，
    只列入「未机检」清单，不置 FAIL（不许把没测出写成通过）。
  - `forbidden_residue`：旧口径残留正则在 scope 内扫到命中即 FAIL
    （当前用于旧 ≤180MB 红线清零，切片-21:27 要求 grep 0 残留）。
  - `--emit [输出路径]`：从源码 token 文件抽取常量生成初版契约骨架（draft），
    供人工审；默认拒绝覆盖锁定契约，需 `--force`。
  - `--selftest`：/tmp 夹具四通路——全一致 PASS；改一个 hex 判红；改一个时长判红；
    删一个项判红；跑完自删夹具。
  - `--sweep-literals`：启发式扫组件层内联裸色值 / 裸尺寸字面量（token 定义文件豁免），
    只出 WARN 候选清单，不置 FAIL（AST 级识别留待后续工单）。

取值口径（与契约 JSON `unit_conventions` 一致）：
  - argb：0xAARRGGBB 八进制十六进制；#RRGGBB 按 FF alpha 折算。
  - ms / fps / kb / mb / int：整数值；float / ratio：浮点，容差 1e-6。
  - bezier：floatArrayOf 四参数列表，逐参数容差 1e-6。

边界：本脚本是正则级静态解析，不编译 Kotlin（沙箱无 JDK，铁律 E8）；
解析不了的复杂表达式（函数调用 / 转发别名）在报告里标 UNPARSEABLE，不冒充通过。
"""
import argparse
import fnmatch
import json
import os
import re
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_CONTRACT = os.path.join(ROOT, "docs", "契约-设计token.json")

DECL_RE = re.compile(r'(?:const\s+val|val)\s+(\w+)\s*(?::\s*[A-Za-z0-9_.<>, ]+)?\s*=\s*(.+)')
ARGB_RE = re.compile(r'0[xX]([0-9A-Fa-f]{8})\b')
HEX6_RE = re.compile(r'#([0-9A-Fa-f]{6})\b')
NUM_RE = re.compile(r'-?\d+(?:\.\d+)?')
BEZIER_RE = re.compile(r'(?:floatArrayOf|CubicBezierEasing)\(([^)]*)\)')
COLOR_LITERAL_RE = re.compile(r'0[xX][0-9A-Fa-f]{8}\b')

# --emit 扫描的源码 token 定义文件（按文件名模式，递归 app/src/main）--
EMIT_GLOBS = [
    "*Tokens.kt", "*Palette.kt", "*Config.kt", "*Params.kt", "*Spec.kt",
]

CATEGORY_BY_PREFIX = [
    ("color", ("BG_", "TEXT_", "BRAND_", "COLOR_", "STONE_")),
    ("motion", ("DURATION_", "EASING_", "SPRING_", "SHEET_DETENT_", "PEEL_", "BURST_", "PRESS_")),
    ("geometry", ("RADIUS", "GAP", "WIDTH", "HEIGHT", "ELEVATION", "LIFT", "GRID", "ARROW_")),
    ("perf", ("FPS", "BUDGET", "PARTICLE", "DRAW_CALL", "P90", "CHAPTER", "FONT_", "FIRST_LOAD", "MODEL_")),
    ("memory", ("MEMORY", "MB_", "_MB")),
]


# ─────────────────────────── 取值解析与归一化 ───────────────────────────

def parse_numeric(text: str, kind: str):
    """从 RHS 文本取第一个数值；int 类返回 int，其余返回 float。解析不到返回 None。"""
    m = NUM_RE.search(text)
    if not m:
        return None
    raw = m.group(0)
    val = float(raw)
    if kind in ("int", "ms", "fps", "kb", "mb", "dp", "sp"):
        return int(round(val))
    return val


def parse_value(kind: str, rhs: str):
    """按 kind 把源码 RHS 解析成可比较值；解析失败返回 ('unparseable', 原文)。"""
    if kind == "argb":
        m = ARGB_RE.search(rhs)
        if m:
            return int(m.group(1), 16)
        m = HEX6_RE.search(rhs)
        if m:
            return int("FF" + m.group(1), 16)
        return ("unparseable", rhs.strip())
    if kind == "bezier":
        m = BEZIER_RE.search(rhs)
        if not m:
            return ("unparseable", rhs.strip())
        nums = [float(x) for x in NUM_RE.findall(m.group(1))]
        if len(nums) != 4:
            return ("unparseable", rhs.strip())
        return nums
    return parse_numeric(rhs, kind)


def to_expected(kind: str, value):
    """把契约 JSON 里的期望值转成与源码同型的比较值。"""
    if kind == "argb":
        if isinstance(value, str) and value.lower().startswith("0x"):
            return int(value, 16)
        if isinstance(value, str) and value.startswith("#"):
            return int("FF" + value[1:], 16)
        return int(value)
    if kind == "bezier":
        return [float(x) for x in value]
    if kind in ("int", "ms", "fps", "kb", "mb", "dp", "sp"):
        return int(round(float(value)))
    return float(value)


def values_equal(kind: str, expected, actual) -> bool:
    if isinstance(actual, tuple) and actual[0] == "unparseable":
        return False
    if kind == "bezier":
        return len(expected) == len(actual) and all(
            abs(a - b) <= 1e-6 for a, b in zip(expected, actual)
        )
    if kind in ("float", "ratio"):
        return abs(float(expected) - float(actual)) <= 1e-6
    return expected == actual


# ─────────────────────────── 锚点读取 ───────────────────────────

def resolve_anchor(root: str, anchor: str):
    """'path:line' → (abs_path, line_no 1-based)；无行号返回 (path, None)。"""
    if ":" in anchor and re.search(r":\d+$", anchor):
        rel, line = anchor.rsplit(":", 1)
        return os.path.join(root, rel), int(line)
    return os.path.join(root, anchor), None


def read_anchor_line(root: str, anchor: str):
    """返回 (status, symbol, rhs, line_text)；status ∈ ok / missing / unparseable。"""
    path, line_no = resolve_anchor(root, anchor)
    if not os.path.isfile(path):
        return "missing", None, None, None
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()
    if line_no is None or line_no < 1 or line_no > len(lines):
        return "missing", None, None, None
    text = lines[line_no - 1].rstrip("\n")
    m = DECL_RE.search(text)
    if not m:
        return "unparseable", None, None, text
    return "ok", m.group(1), m.group(2).strip(), text


# ─────────────────────────── 校验主流程 ───────────────────────────

def verify(root: str, contract_path: str):
    """返回 (exit_code, 报告行列表)。"""
    lines = []
    fails = []
    warns = []
    enforced = 0
    spec_only = []
    counts = {}

    if not os.path.isfile(contract_path):
        return 1, [f"契约文件不存在：{contract_path}"]

    with open(contract_path, encoding="utf-8") as f:
        contract = json.load(f)

    tokens = contract.get("tokens", [])
    if contract.get("review") == "draft":
        warns.append("契约标记为 draft（--emit 初版），未经人工裁决，结果仅供参考。")

    for tok in tokens:
        cat = tok.get("category", "?")
        counts[cat] = counts.get(cat, 0) + 1
        name = tok.get("name", "?")
        kind = tok.get("kind")
        expected = to_expected(kind, tok["value"])

        if tok.get("check") != "source":
            spec_only.append(tok)
            continue

        enforced += 1

        # 锁定项必须带规格出处与裁决引用，防止契约被顺手改宽（防御 a）
        if tok.get("locked"):
            if not tok.get("spec_source"):
                fails.append(f"[契约] {name} locked 但缺 spec_source，不许无裁决改宽")
            if not tok.get("ruling_ref"):
                fails.append(f"[契约] {name} locked 但缺 ruling_ref 设计裁决行")

        anchors = [tok["source"]] + list(tok.get("aliases", []))
        for anchor in anchors:
            status, symbol, rhs, text = read_anchor_line(root, anchor)
            if status == "missing":
                fails.append(f"[FAIL] {name} 源码锚点缺失 ← {anchor}")
                continue
            if status == "unparseable":
                fails.append(f"[FAIL] {name} 锚点行无法解析（非常量声明）← {anchor}：{text.strip()}")
                continue
            actual = parse_value(kind, rhs)
            if isinstance(actual, tuple) and actual[0] == "unparseable":
                fails.append(f"[FAIL] {name} 取值解析失败 ← {anchor}：{actual[1]}")
                continue
            if not values_equal(kind, expected, actual):
                exp_disp = tok.get("spec_value", tok["value"])
                fails.append(
                    f"[FAIL] {name} 契约值 {exp_disp} / 源码实际值 {actual} ← {anchor}"
                )
            else:
                lines.append(f"[OK] {cat:8s} {name} = {tok['value']} ← {anchor}")

    # 旧口径残留扫描
    for residue in contract.get("forbidden_residue", []):
        pattern = re.compile(residue["pattern"])
        hits = []
        for scope in residue.get("scope", []):
            for path in _glob(root, scope):
                try:
                    with open(path, encoding="utf-8") as f:
                        for i, row in enumerate(f, 1):
                            if pattern.search(row):
                                hits.append(f"{os.path.relpath(path, root)}:{i}")
                except (OSError, UnicodeDecodeError):
                    continue
        if hits:
            fails.append(
                f"[FAIL] 残留口径 {residue['id']} 命中 {len(hits)} 处："
                + "; ".join(hits[:5])
                + f"（规格 {residue.get('spec_source', '?')}）"
            )
        else:
            lines.append(f"[OK] 残留扫描 {residue['id']} 0 命中")

    # 汇总
    lines.append("")
    lines.append("== 分类统计 ==")
    for cat in ("color", "geometry", "motion", "perf", "memory"):
        if cat in counts:
            lines.append(f"  {cat}: {counts[cat]}")
    lines.append(f"  机检项: {enforced}；spec-only 未机检: {len(spec_only)}")
    if spec_only:
        lines.append("== 未机检（spec-only，转人工 / 外部基准，不许冒充通过）==")
        for tok in spec_only:
            lines.append(f"  - {tok['name']} = {tok['value']} {tok.get('unit', '')} ← {tok.get('spec_source')}")
    for w in warns:
        lines.append(f"[WARN] {w}")

    if fails:
        lines.append("")
        lines.append("== 不一致 / 残留明细 ==")
        lines.extend(fails)
        lines.append(f"DESIGN-TOKENS: FAIL（{len(fails)} 项）")
        return 1, lines

    lines.append(f"DESIGN-TOKENS: PASS（机检 {enforced} 项全一致，spec-only {len(spec_only)} 项登记在册）")
    return 0, lines


def _glob(root: str, pattern: str):
    """把 docs 风格的 ** 通配展开成实际文件列表。"""
    import glob as _glob
    return _glob.glob(os.path.join(root, pattern), recursive=True)


# ─────────────────────────── --emit 初版契约抽取 ───────────────────────────

def emit(root: str, out_path: str, force: bool):
    if os.path.abspath(out_path) == os.path.abspath(DEFAULT_CONTRACT) and not force:
        print("拒绝覆盖锁定契约 docs/契约-设计token.json；如需覆写加 --force。", file=sys.stderr)
        return 2

    records = []
    src_root = os.path.join(root, "app", "src", "main", "java")
    if not os.path.isdir(src_root):
        print(f"源码根不存在：{src_root}", file=sys.stderr)
        return 2

    for dirpath, _, files in os.walk(src_root):
        for fn in files:
            if not any(fnmatch.fnmatch(fn, g) for g in EMIT_GLOBS):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, root)
            with open(path, encoding="utf-8") as f:
                for i, row in enumerate(f, 1):
                    m = DECL_RE.search(row)
                    if not m:
                        continue
                    symbol, rhs = m.group(1), m.group(2).strip()
                    kind = _infer_kind(symbol, rhs)
                    records.append({
                        "name": _kebab(symbol),
                        "symbol": symbol,
                        "category": _infer_category(symbol),
                        "kind": kind,
                        "value": _raw_value(kind, rhs),
                        "unit": None,
                        "source": f"{rel}:{i}",
                        "aliases": [],
                        "spec_source": None,
                        "check": "source",
                        "locked": False,
                        "ruling_ref": None,
                        "note": "--emit 初版抽取，待人工核对规格出处后再锁定。",
                    })

    contract = {
        "schema_version": "1.0",
        "review": "draft",
        "generated": "emit",
        "purpose": "--emit 从源码抽取的初版骨架；人工补 spec_source / ruling_ref / 单位口径后再转 locked。",
        "tokens": records,
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(contract, f, ensure_ascii=False, indent=2)
    print(f"EMIT: 抽取 {len(records)} 条 → {os.path.relpath(out_path, root)}（draft，未锁定）")
    return 0


def _infer_category(symbol: str) -> str:
    for cat, prefixes in CATEGORY_BY_PREFIX:
        if symbol.startswith(prefixes) or any(p in symbol for p in prefixes):
            return cat
    return "geometry"


def _infer_kind(symbol: str, rhs: str) -> str:
    if BEZIER_RE.search(rhs):
        return "bezier"
    if ARGB_RE.search(rhs) or HEX6_RE.search(rhs):
        return "argb"
    if "MS" in symbol or "DURATION" in symbol:
        return "ms"
    if "FPS" in symbol:
        return "fps"
    if "RATIO" in symbol or "ALPHA" in symbol or "DAMPING" in symbol:
        return "ratio" if "ALPHA" in symbol or "RATIO" in symbol else "float"
    if "DP" in symbol or "SP" in symbol:
        return "dp"
    if NUM_RE.search(rhs):
        return "int"
    return "float"


def _raw_value(kind: str, rhs: str):
    val = parse_value(kind, rhs)
    if isinstance(val, tuple):
        return rhs.strip()
    return val


def _kebab(symbol: str) -> str:
    s = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1-\2", symbol)
    s = re.sub(r"([a-z\d])([A-Z])", r"\1-\2", s)
    return s.lower().replace("_", "-")


# ─────────────────────────── --sweep-literals 字面量旁路扫描 ───────────────────────────

def sweep_literals(root: str, contract: dict):
    exempt = contract.get("literal_sweep_exempt_files", [])
    src_root = os.path.join(root, "app", "src", "main", "java")
    candidates = []
    for dirpath, _, files in os.walk(src_root):
        for fn in files:
            if not fn.endswith(".kt"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, root)
            if any(fnmatch.fnmatch(rel, e) for e in exempt):
                continue
            with open(path, encoding="utf-8") as f:
                for i, row in enumerate(f, 1):
                    if COLOR_LITERAL_RE.search(row) and "Color(" in row:
                        candidates.append(f"{rel}:{i} 裸色值：{row.strip()}")
    print(f"== 字面量旁路扫描（启发式 WARN，豁免 {len(exempt)} 个 token 定义文件）==")
    if candidates:
        for c in candidates[:30]:
            print(f"[WARN] {c}")
        print(f"共 {len(candidates)} 处候选；AST 级常量引用识别留待后续工单。")
    else:
        print("组件层未发现内联裸色值候选。")
    return 0


# ─────────────────────────── --selftest 夹具 ───────────────────────────

def selftest() -> int:
    tmp = tempfile.mkdtemp(prefix="design-tokens-selftest-")
    try:
        fixture_kt = (
            "object FixturePalette {\n"
            "    const val BG_PAGE: Long = 0xFFF7F1E6\n"
            "    const val DURATION_MICRO: Int = 120\n"
            "    val CARD_ENTER_EASING = floatArrayOf(0.22f, 0.61f, 0.36f, 1f)\n"
            "}\n"
        )
        kt_rel = os.path.join("src", "FixturePalette.kt")
        os.makedirs(os.path.join(tmp, "src"), exist_ok=True)
        with open(os.path.join(tmp, kt_rel), "w", encoding="utf-8") as f:
            f.write(fixture_kt)

        def make_contract():
            return {
                "tokens": [
                    {"name": "bg-page", "symbol": "BG_PAGE", "category": "color",
                     "kind": "argb", "value": "0xFFF7F1E6",
                     "source": f"{kt_rel}:2", "aliases": [],
                     "spec_source": "docs/x.md:1", "check": "source",
                     "locked": True, "ruling_ref": "测试裁决"},
                    {"name": "duration-micro-ms", "symbol": "DURATION_MICRO",
                     "category": "motion", "kind": "ms", "value": "120",
                     "source": f"{kt_rel}:3", "aliases": [],
                     "spec_source": "docs/x.md:1", "check": "source",
                     "locked": True, "ruling_ref": "测试裁决"},
                    {"name": "easing-card-enter", "symbol": "CARD_ENTER_EASING",
                     "category": "motion", "kind": "bezier",
                     "value": [0.22, 0.61, 0.36, 1],
                     "source": f"{kt_rel}:4", "aliases": [],
                     "spec_source": "docs/x.md:1", "check": "source",
                     "locked": True, "ruling_ref": "测试裁决"},
                ],
                "forbidden_residue": [],
            }

        results = []

        # 通路 1：全一致 → PASS
        c1 = os.path.join(tmp, "c1.json")
        with open(c1, "w", encoding="utf-8") as f:
            json.dump(make_contract(), f)
        rc1, out1 = verify(tmp, c1)
        results.append(("全一致 PASS", rc1 == 0, out1))

        # 通路 2：改一个 hex → FAIL
        c2 = os.path.join(tmp, "c2.json")
        with open(c2, "w", encoding="utf-8") as f:
            json.dump(make_contract(), f)
        with open(os.path.join(tmp, kt_rel), "w", encoding="utf-8") as f:
            f.write(fixture_kt.replace("0xFFF7F1E6", "0xFFEFE8D8"))
        rc2, out2 = verify(tmp, c2)
        results.append(("改 hex 判红", rc2 == 1, out2))

        # 通路 3：改一个时长 → FAIL
        with open(os.path.join(tmp, kt_rel), "w", encoding="utf-8") as f:
            f.write(fixture_kt)
        c3 = os.path.join(tmp, "c3.json")
        con3 = make_contract()
        con3["tokens"][1]["value"] = "500"
        with open(c3, "w", encoding="utf-8") as f:
            json.dump(con3, f)
        rc3, out3 = verify(tmp, c3)
        results.append(("改时长判红", rc3 == 1, out3))

        # 通路 4：删一个项（源码声明移除）→ FAIL
        c4 = os.path.join(tmp, "c4.json")
        with open(c4, "w", encoding="utf-8") as f:
            json.dump(make_contract(), f)
        with open(os.path.join(tmp, kt_rel), "w", encoding="utf-8") as f:
            f.write("object FixturePalette {\n"
                    "    const val BG_PAGE: Long = 0xFFF7F1E6\n"
                    "    val CARD_ENTER_EASING = floatArrayOf(0.22f, 0.61f, 0.36f, 1f)\n"
                    "}\n")
        rc4, out4 = verify(tmp, c4)
        results.append(("删项判红", rc4 == 1, out4))

        ok = True
        for label, passed, out in results:
            print(f"-- selftest {label}: {'PASS' if passed else 'FAIL'}")
            if not passed:
                ok = False
                for row in out:
                    print("   " + row)
        print("SELFTEST: PASS" if ok else "SELFTEST: FAIL")
        return 0 if ok else 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


# ─────────────────────────── 入口 ───────────────────────────

def main(argv=None):
    ap = argparse.ArgumentParser(description="设计 token 契约守卫（Q-272）")
    ap.add_argument("--contract", default=DEFAULT_CONTRACT, help="契约 JSON 路径")
    ap.add_argument("--root", default=ROOT, help="仓库根（selftest / 夹具用）")
    ap.add_argument("--emit", nargs="?", const=os.path.join(ROOT, "docs", "契约-设计token.emit.json"),
                    help="从源码抽取初版契约骨架")
    ap.add_argument("--force", action="store_true", help="--emit 允许覆盖锁定契约")
    ap.add_argument("--selftest", action="store_true", help="跑 /tmp 夹具双向自测后自删")
    ap.add_argument("--sweep-literals", action="store_true", help="启发式扫组件层内联字面量")
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()
    if args.emit:
        return emit(args.root, args.emit, args.force)
    if args.sweep_literals:
        with open(args.contract, encoding="utf-8") as f:
            contract = json.load(f)
        return sweep_literals(args.root, contract)

    rc, out = verify(args.root, args.contract)
    print("== 设计 token 契约校验（check-design-tokens.py）==")
    for row in out:
        print(row)
    return rc


if __name__ == "__main__":
    sys.exit(main())
