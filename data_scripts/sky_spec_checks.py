#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
星空天象层机检 · 判定函数集（被 `check-sky-spec.py` 入口调用）。

分组：
  - 常量/锚色/禁色/字号/行数/文件锚点/台账/切片：纯静态断言；
  - 复用闸：子进程转发 catalog / pyramid / orphan 既有脚本结论，不重造；
  - bench 存在性 / 半机判登记：静态旁证在库，运行时结论标 UNKNOWN；
  - 人判登记：判不了的条目写明原因与最小证据清单，不冒充 PASS。
"""

import re
import subprocess
import sys
from pathlib import Path

from sky_spec_model import (
    ALLOWED_CODE_HEX_BASENAMES,
    ANCHOR_COLORS,
    ARGB_RE,
    BLACK_WHITE,
    CATALOG_SCRIPT,
    CORE_FILES,
    CONTRACT_CONSTANTS,
    DECL_RE,
    DEFER,
    FAIL,
    HEX6_RE,
    Item,
    MACHINE,
    MANUAL,
    NUM_RE,
    ORPHAN_SCRIPT,
    PASS,
    PYRAMID_SCRIPT,
    SEMI,
    SP_RE,
    TOL,
    UNKNOWN,
)


# ─────────────────────────── 常量解析 ───────────────────────────

def _parse_rhs_number(rhs: str, kind: str):
    """从声明 RHS 取首个数值；解析失败返回 None。下划线分隔符先剔除。"""
    m = NUM_RE.search(rhs.replace("_", ""))
    if not m:
        return None
    val = float(m.group(0))
    return int(round(val)) if kind == "int" else val


def _read_declarations(text: str) -> dict[str, tuple[str, int]]:
    """返回 {符号: (RHS 原文, 1-based 行号)}，只收 const val 顶层声明。"""
    out: dict[str, tuple[str, int]] = {}
    for i, line in enumerate(text.splitlines(), 1):
        m = DECL_RE.search(line)
        if m:
            out[m.group(1)] = (m.group(2).strip(), i)
    return out


def check_contract_constants(root: Path) -> list[Item]:
    """§十六 + §五 契约常量：按符号定位源码声明，数值逐字比对（非子串匹配）。"""
    params = root / "app/src/main/java/io/legado/app/ui/map/globe/astro/contract/SkyParams.kt"
    if not params.exists():
        return [Item("§十六", MACHINE, FAIL, [str(params.relative_to(root))],
                     "SkyParams.kt 缺失，契约常量无从回读")]
    decls = _read_declarations(params.read_text(encoding="utf-8"))
    fails, oks = [], []
    for sym, expected, ref, kind in CONTRACT_CONSTANTS:
        if sym not in decls:
            fails.append(f"{sym} 声明缺失（{ref}）")
            continue
        rhs, lineno = decls[sym]
        actual = _parse_rhs_number(rhs, kind)
        anchor = f"{params.relative_to(root)}:{lineno}"
        if actual is None:
            fails.append(f"{sym} RHS 解析失败 ← {anchor}：{rhs}")
            continue
        exp = float(expected)
        if abs(actual - exp) > TOL:
            fails.append(f"{sym} 契约 {expected} / 源码 {actual} ← {anchor}（{ref}）")
        else:
            oks.append(f"{sym}={actual:g} ← {anchor}")
    if fails:
        return [Item("§十六/§五 常量", MACHINE, FAIL,
                     [f"{params.relative_to(root)}"], "；".join(fails))]
    return [Item("§十六/§五 常量", MACHINE, PASS, oks,
                 f"{len(CONTRACT_CONSTANTS)} 条契约常量与源码逐字一致")]


def check_anchor_colors(root: Path) -> list[Item]:
    """§六.12 四锚色逐字落 SkySpecColor.kt。"""
    color_file = root / "app/src/main/java/io/legado/app/ui/map/globe/astro/contract/SkySpecColor.kt"
    if not color_file.exists():
        return [Item("§六.12 锚色", MACHINE, FAIL, [str(color_file.relative_to(root))],
                     "SkySpecColor.kt 缺失")]
    text = color_file.read_text(encoding="utf-8")
    fails, oks = [], []
    for sym, hexval, ref in ANCHOR_COLORS:
        if sym not in text:
            fails.append(f"{sym} 缺失（{ref}）")
        elif hexval.lower() not in text.lower():
            fails.append(f"{sym} 色值 {hexval} 与规格 {ref} 不符")
        else:
            oks.append(f"{sym}={hexval}")
    if fails:
        return [Item("§六.12 锚色", MACHINE, FAIL,
                     [str(color_file.relative_to(root))], "；".join(fails))]
    return [Item("§六.12 锚色", MACHINE, PASS, oks, "四锚色逐字落库")]


def _strip_code_line(line: str) -> str:
    """去掉行注释与 KDoc 行，返回纯代码片段；注释里的 hex 不算绘制字面量。"""
    s = line.strip()
    if s.startswith(("//", "*", "/*")):
        return ""
    # 朴素剔除行尾 // 注释（Kotlin 字符串内罕见 //，既有脚本同口径）。
    if "//" in s:
        s = s.split("//", 1)[0]
    return s


def check_forbidden_colors(root: Path) -> list[Item]:
    """§十四.7 / §六.16-17：globe 树代码行裸 hex 只允许既有表；禁纯黑纯白绘制字面量。"""
    globe = root / "app/src/main/java/io/legado/app/ui/map/globe"
    if not globe.exists():
        return [Item("§十四.7 禁色", MACHINE, FAIL, [str(globe)], "globe 源码树缺失")]
    hex_hits: list[str] = []
    bw_hits: list[str] = []
    for path in sorted(globe.rglob("*.kt")):
        rel = str(path.relative_to(root))
        allowed = path.name in ALLOWED_CODE_HEX_BASENAMES
        for i, raw in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
            code = _strip_code_line(raw)
            if not code:
                continue
            for m in HEX6_RE.finditer(code):
                if not allowed:
                    hex_hits.append(f"{rel}:{i} #{m.group(1)}")
                if m.group(1).upper() in BLACK_WHITE:
                    bw_hits.append(f"{rel}:{i} #{m.group(1)}")
            for m in ARGB_RE.finditer(code):
                rgb = m.group(1)[2:]
                # 0xFFFFFF / 0x000000 作位掩码（and 0xFFFFFF）不算绘制色；
                # 仅当整字面值就是纯黑/纯白 ARGB 时判红。
                if rgb.upper() in BLACK_WHITE and "and" not in code:
                    bw_hits.append(f"{rel}:{i} 0x{m.group(1)}")
    fails: list[str] = []
    if hex_hits:
        fails.append(f"非允许表代码行裸 hex {len(hex_hits)} 处：{hex_hits[:5]}")
    if bw_hits:
        fails.append(f"纯黑/纯白绘制字面量 {len(bw_hits)} 处：{bw_hits[:5]}")
    if fails:
        return [Item("§十四.7 禁色", MACHINE, FAIL,
                     [f"{globe.relative_to(root)}/**/*.kt"], "；".join(fails))]
    return [Item("§十四.7 禁色", MACHINE, PASS,
                 [f"{globe.relative_to(root)}/**/*.kt"],
                 "代码行零非允许裸 hex、零纯黑/纯白绘制字面量；像素级残留归人判")]


def check_font_sizes(root: Path) -> list[Item]:
    """§五.5：字号常量 ≥14sp；星空层代码内联 N.sp 不得低于 14。"""
    fails, oks = [], []
    params = root / "app/src/main/java/io/legado/app/ui/map/globe/astro/contract/SkyParams.kt"
    if params.exists():
        decls = _read_declarations(params.read_text(encoding="utf-8"))
        for sym, _expected, _ref, _kind in CONTRACT_CONSTANTS:
            if not sym.endswith("_FONT_SP"):
                continue
            if sym not in decls:
                fails.append(f"{sym} 声明缺失")
                continue
            rhs, lineno = decls[sym]
            actual = _parse_rhs_number(rhs, "float")
            if actual is None:
                fails.append(f"{sym} RHS 解析失败 ← {params.relative_to(root)}:{lineno}")
            elif actual < 14:
                fails.append(f"{sym}={actual:g}sp < 14sp ← {params.relative_to(root)}:{lineno}")
            else:
                oks.append(f"{sym}={actual:g}sp")
    # 内联字号字面量扫描：只扫星空层自己的文件（astro/** + host/Sky*.kt + render/Field*.kt）。
    # 为什么不整扫 host/：GlobeScreenHost.kt 里既有战役/时间轴/控制行等地球 UI 沿用旧字号，
    # 不属于 §五.5 星空标签口径；整扫会把既有合规实现误判红（反例边界见交付文档）。
    globe = root / "app/src/main/java/io/legado/app/ui/map/globe"
    scoped: list[Path] = []
    astro_dir = globe / "astro"
    if astro_dir.exists():
        scoped += list(astro_dir.rglob("*.kt"))
    for sub, prefixes in (("host", ("Sky",)), ("render", ("Field",))):
        d = globe / sub
        if d.exists():
            scoped += [p for p in d.rglob("*.kt") if p.name.startswith(prefixes)]
    for path in sorted(scoped):
        rel = str(path.relative_to(root))
        for i, raw in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
            code = _strip_code_line(raw)
            for m in SP_RE.finditer(code):
                if float(m.group(1)) < 14:
                    fails.append(f"{rel}:{i} 内联 {m.group(1)}.sp < 14sp")
    if fails:
        return [Item("§五.5 字号", MACHINE, FAIL,
                     [str(params.relative_to(root)) if params.exists() else "astro/"],
                     "；".join(fails))]
    return [Item("§五.5 字号", MACHINE, PASS, oks or ["astro/", "host/ 内联扫描"],
                 "字号常量 ≥14sp，内联字号无低于 14sp")]


def check_line_limits(root: Path) -> list[Item]:
    """§三.8：SkyCanvasRenderer.kt ≤400 行；宿主净增 ≤5 行属半机（需基线 commit 比对）。"""
    items: list[Item] = []
    renderer = root / "app/src/main/java/io/legado/app/ui/map/globe/host/SkyCanvasRenderer.kt"
    host = root / "app/src/main/java/io/legado/app/ui/map/globe/host/GlobeScreenHost.kt"
    if renderer.exists():
        n = len(renderer.read_text(encoding="utf-8").splitlines())
        rel = str(renderer.relative_to(root))
        if n > 400:
            items.append(Item("§三.8 渲染器行数", MACHINE, FAIL, [f"{rel}:1-{n}"],
                              f"{rel} {n} 行 > 400 行上限"))
        else:
            items.append(Item("§三.8 渲染器行数", MACHINE, PASS, [f"{rel}:{n}"],
                              f"{n} 行 ≤ 400 行上限"))
    else:
        items.append(Item("§三.8 渲染器行数", MACHINE, FAIL,
                          [str(renderer.relative_to(root))], "渲染器文件缺失"))
    if host.exists():
        n = len(host.read_text(encoding="utf-8").splitlines())
        items.append(Item("§三.17 宿主净增", SEMI, UNKNOWN,
                          [f"{host.relative_to(root)}:{n}"],
                          f"宿主当前 {n} 行；净增 ≤5 行须与基线 commit 逐版比对，"
                          "静态单行计数不能证明净增量，归半机判"))
    return items


def check_core_files_exist(root: Path) -> list[Item]:
    """锚点可回源：规格 §三 点名的核心文件必须在库（缺失即死锚点）。"""
    globe = root / "app/src/main/java/io/legado/app/ui/map/globe"
    missing, oks = [], []
    for rel in CORE_FILES:
        p = globe / rel
        if p.exists():
            oks.append(rel)
        else:
            missing.append(rel)
    assets = root / "app/src/main/assets/astro"
    if not assets.exists():
        missing.append("assets/astro/")
    else:
        oks.append("assets/astro/")
    if missing:
        return [Item("§三 文件锚点", MACHINE, FAIL, oks, f"缺失：{missing}")]
    return [Item("§三 文件锚点", MACHINE, PASS, oks, "规格点名核心文件全部在库")]


def check_ledger(root: Path) -> list[Item]:
    """§十四.9：数据台账含 Tycho 署名串、I/259、NC 边界与商业化换源条款。"""
    ledger = root / "docs/台账-数据源许可.md"
    if not ledger.exists():
        return [Item("§十四.9 台账许可", MACHINE, FAIL,
                     [str(ledger.relative_to(root))], "台账文件缺失")]
    text = ledger.read_text(encoding="utf-8")
    signature = "Source: ESA Hipparcos mission; Tycho-2 Catalogue (Høg et al. 2000), CDS I/259"
    probes = [
        ("署名串逐字", signature in text),
        ("CDS I/259 编号", "I/259" in text),
        ("NC 非商用口径", "非商用" in text),
        ("商业化整体换源", "商业化即整体换源" in text or "整体更换" in text),
    ]
    bad = [name for name, ok in probes if not ok]
    if bad:
        return [Item("§十四.9 台账许可", MACHINE, FAIL,
                     [str(ledger.relative_to(root))], f"缺：{bad}")]
    return [Item("§十四.9 台账许可", MACHINE, PASS,
                 [str(ledger.relative_to(root))],
                 "署名串 / I/259 / NC 边界 / 商业化换源条款齐备")]


def check_slices(root: Path) -> list[Item]:
    """§十四.10：切片-26 落档；切片-20 头部作废标记落档。"""
    items: list[Item] = []
    slice26 = root / "docs/落文-规格切片/切片-26-星空天象层.md"
    slice20 = root / "docs/落文-规格切片/切片-20-3D沙盘与地球仪合并.md"
    if slice26.exists():
        items.append(Item("§十四.10 切片-26", MACHINE, PASS,
                          [str(slice26.relative_to(root))], "切片-26 在库"))
    else:
        items.append(Item("§十四.10 切片-26", MACHINE, FAIL,
                          [str(slice26.relative_to(root))], "切片-26 缺失"))
    if not slice20.exists():
        items.append(Item("§十四.10 切片-20 作废", MACHINE, FAIL,
                          [str(slice20.relative_to(root))], "切片-20 缺失"))
        return items
    head = "\n".join(slice20.read_text(encoding="utf-8").splitlines()[:12])
    ok_mark = ("Stellarium 星图" in head and "作废" in head and "切片-26" in head)
    if ok_mark:
        items.append(Item("§十四.10 切片-20 作废", MACHINE, PASS,
                          [f"{slice20.relative_to(root)}:1-12"],
                          "头部含 Stellarium 作废标记并指向切片-26"))
    else:
        items.append(Item("§十四.10 切片-20 作废", MACHINE, FAIL,
                          [f"{slice20.relative_to(root)}:1-12"],
                          "头部作废标记 / 指向切片-26 缺失"))
    return items


# ─────────────────────────── 复用既有脚本（子进程，不重造）───────────────────────────

def _run(script: Path, root: Path) -> tuple[int, list[str]]:
    """跑既有校验脚本，回传退出码与末尾输出行；脚本缺失记 FAIL。"""
    if not script.exists():
        return 1, [f"复用脚本缺失: {script.name}"]
    proc = subprocess.run(
        [sys.executable, str(script)], cwd=str(root),
        capture_output=True, text=True, timeout=120)
    tail = [ln for ln in (proc.stdout + proc.stderr).splitlines() if ln.strip()][-3:]
    return proc.returncode, tail


def check_reused_gates(root: Path) -> list[Item]:
    """复用 catalog / pyramid / orphan 三闸结论；结论只转发，不重新断言。"""
    items: list[Item] = []
    for cid, script, label in [
        ("§十四.6 内置星表", CATALOG_SCRIPT, "astro_check_catalog"),
        ("§十四.6 金字塔契约", PYRAMID_SCRIPT, "astro_check_pyramid"),
        ("§三 孤儿入口", ORPHAN_SCRIPT, "check-orphan-entries"),
    ]:
        rc, tail = _run(script, root)
        if rc == 0:
            items.append(Item(cid, MACHINE, PASS, tail, f"复用 {label} 退出码 0"))
        else:
            items.append(Item(cid, MACHINE, FAIL, tail, f"复用 {label} 退出码 {rc}"))
    return items


def check_bench_existence(root: Path) -> list[Item]:
    """§十四.8/13-16：四条 bench 用例在库（静态旁证）；耗时结论取自 CI，沙箱标 UNKNOWN。"""
    bench = root / "app/src/test/java/io/legado/app/ui/map/globe/astro/bench/SkyBenchTest.kt"
    if not bench.exists():
        return [Item("§十四.8 bench", SEMI, UNKNOWN,
                     [str(bench.relative_to(root))],
                     "bench 测试类缺失；耗时结论须 CI Verify job 背书")]
    text = bench.read_text(encoding="utf-8")
    tests = re.findall(r"@Test", text)
    rel = str(bench.relative_to(root))
    if len(tests) < 4:
        return [Item("§十四.8 bench", SEMI, FAIL, [f"{rel}"],
                     f"@Test 仅 {len(tests)} 条，规格要求 4 条 bench")]
    return [Item("§十四.8/13-16 bench", SEMI, UNKNOWN,
                 [f"{rel}（@Test {len(tests)} 条在库）"],
                 "用例在库；<4ms/<16ms/<24ms/<5ms 耗时结论须 CI 或真机帧计时背书，"
                 "沙箱无 JDK21，不冒充 PASS")]


# ─────────────────────────── 人判登记（判不了就写明为什么 + 最小证据）───────────────────────────

MANUAL_REGISTRY: dict[str, dict] = {
    "§十四.1": {
        "why": "「无网格换面跳变 / 无星点突增 / 无整层淡入」是帧间观感判据，"
               "需要逐帧录屏比对；静态只能证明连续纯函数在库，证明不了画面连续。",
        "evidence": [
            "荣耀 WIN 真机（或带屏 CI）长安视图双指捏合全程录屏，不抬指贯穿州→国→地球仪→天球；",
            "逐帧核对：网格无换面、星点数无突增、星空 alpha 无整层淡入；",
            "留存录屏工件与帧抽样，回链 SkyParams.skyBlendFromDiameter / magLimitFromBlend 纯函数。",
        ],
    },
    "§十四.3": {
        "why": "双向闭环（点井宿→雍州脉冲+州名；点雍州→井鬼高亮）是运行时可达性 + 画面结果，"
               "静态映射函数在库不等于触发链路接通；截图工件仓库当前不存在。",
        "evidence": [
            "正向：点井宿，留帧天球成员星连线高亮 + 地球雍州暖金脉冲 + 竖排州名；",
            "反向：点雍州域，留帧天球转向井鬼并高亮；",
            "两张截图归档到 screenshot 测试目录或验收证据台，并标注时刻与观测点。",
        ],
    },
    "§十四.5": {
        "why": "断网 + 清缓存后「不空白、不崩」须真机或 instrumented 流程驱动；"
               "静态占位路径在库只能算旁证，不能证明运行时不崩。",
        "evidence": [
            "真机断网并清空星空缓存，冷启动进入星空；",
            "留帧：程序化散点兜底 + 「星表未载，当前为示意星空」横幅；",
            "确认无空白页、无崩溃堆栈，异常计数回落路径可复现。",
        ],
    },
    "§十四.11": {
        "why": "八态逐态截图是画面态判据；态 2 悬停 120ms 时序、态 4 真实 0..1 进度源"
               "属运行时行为，静态字段在库不代表时序成立。",
        "evidence": [
            "逐态驱动：默认 / 悬停 / 按下 / 加载 / 禁用 / 空 / 错误 / 离线，每态留一张帧；",
            "态 2 核对 1.3× 放大与 120ms 回弹；态 4 核对 1px 金线进度随真实进度源走动；",
            "每态对照 §七.1-8 判据标注过/不过，帧工件入库可回源。",
        ],
    },
    "§十四.12": {
        "why": "Sheet 关闭前后相机姿态与选中 id 逐位相等，是开合快照比对的运行时结论；"
               "须有快照比对测试或真机前后帧核对，静态读不到运行时四元数/选中态。",
        "evidence": [
            "打开星点 Sheet 前后各抓一帧（相机姿态 + selectedId）；",
            "关闭 Sheet 后再抓一帧，核对姿态与选中态口径（注意规格文字称四元数，"
            "当前实现为 yaw/pitch 双标量，比对口径须先统一）；",
            "留存前后帧与比对结果；若实现为关闭即取消选中，须由验收方按规格口径裁定。",
        ],
    },
}


def emit_manual() -> list[Item]:
    items: list[Item] = []
    for cid, reg in MANUAL_REGISTRY.items():
        items.append(Item(cid, MANUAL, DEFER, reg["evidence"],
                          f"机器判不了的原因: {reg['why']}"))
    return items


# ─────────────────────────── 半机判登记（静态旁证 + 未闭环部分）───────────────────────────

def emit_semi_static(root: Path) -> list[Item]:
    """半机判条目：静态可扫部分给结论，运行时部分标 UNKNOWN。"""
    items: list[Item] = []

    # §十四.2：800ms 常量已在常量闸覆盖；此处登记 alt/az ≤1° 契约断言缺口。
    sidereal = root / "app/src/test/java/io/legado/app/ui/map/globe/astro/data/SiderealTimeTest.kt"
    altaz_probe = root / "app/src/test/java/io/legado/app/ui/map/globe/astro/contract/SkyContractTest.kt"
    ev = []
    if sidereal.exists():
        ev.append(f"{sidereal.relative_to(root)}（LST 三时刻用例在库）")
    if altaz_probe.exists():
        ev.append(f"{altaz_probe.relative_to(root)}（地平换算几何用例）")
    items.append(Item("§十四.2 恒星时比对", SEMI, UNKNOWN, ev or ["测试目录未找到 LST/地平用例"],
                      "800ms 常量与 LST 纯函数静态在库；地平换算目前仅单例几何断言，"
                      "三时刻 alt/az ≤1° 手算对拍与观测点注入链路须测试/真机闭环，沙箱不判 PASS"))

    # §十四.4：默认星官 / 清旧逻辑 / CSV 三语由 catalog 与源码旁证；DAO 注入运行时渲染归人判。
    renderer = root / "app/src/main/java/io/legado/app/ui/map/globe/host/SkyCanvasRenderer.kt"
    ev = []
    if renderer.exists():
        txt = renderer.read_text(encoding="utf-8")
        if "mode-asterism" in txt:
            ev.append(f"{renderer.relative_to(root)}（默认 mode-asterism）")
    items.append(Item("§十四.4 体系切换/Sheet 字段", SEMI, UNKNOWN, ev,
                      "默认星官与清旧逻辑静态在库、CSV 三语由 catalog 闸覆盖；"
                      "DAO 实体行 / 距离哨兵抑制的运行时渲染须测试或真机闭环"))

    # §十四.6 运行时金字塔：下载/合并/缓存校验链路表现归人判；数据层由复用闸覆盖。
    items.append(Item("§十四.6 暗星长出", SEMI, UNKNOWN,
                      ["assets/astro 内置层；金字塔按需下载链路"],
                      "内置全量由 catalog/pyramid 闸机判；9–11.5 等暗星运行时连续长出"
                      "须真机局部放大复核"))
    return items


def collect(root: Path, include_reused: bool = True) -> list[Item]:
    items: list[Item] = []
    items += check_contract_constants(root)
    items += check_anchor_colors(root)
    items += check_forbidden_colors(root)
    items += check_font_sizes(root)
    items += check_line_limits(root)
    items += check_core_files_exist(root)
    items += check_ledger(root)
    items += check_slices(root)
    if include_reused:
        # 复用闸各自带 --selftest；本闸自测夹具里跳过，避免耦合真实仓库数据。
        items += check_reused_gates(root)
    items += check_bench_existence(root)
    items += emit_semi_static(root)
    items += emit_manual()
    return items


def summarize(items: list[Item]) -> tuple[int, dict]:
    fails = [i for i in items if i.conclusion == FAIL]
    unknown = [i for i in items if i.conclusion in (UNKNOWN, DEFER)]
    stats = {
        "PASS": len([i for i in items if i.conclusion == PASS]),
        "FAIL": len(fails),
        "UNKNOWN": len([i for i in items if i.conclusion == UNKNOWN]),
        "DEFER": len([i for i in items if i.conclusion == DEFER]),
    }
    if fails:
        return 1, stats
    if unknown:
        return 2, stats
    return 0, stats
