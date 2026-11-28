#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
落文 · 星空天象层 §十四 判据机器化总入口（规格 §十四 12 条 + 4 bench）
=====================================================================

本脚本只做「本来可以机器判」的那一部分；判不了的条目如实登记为 DEFER/UNKNOWN，
不许把没测出写成通过（总纲 B1 / B14；验收口径 L1 静态验收，不越级借 L2/L3 结论）。

判据分级（分类依据与逐条对照见 `docs/机检-星空天象层-判据机器化.md`）：
  - 机判（MACHINE）：纯文本/静态可断言，违规样本必红。
  - 半机判（SEMI）：静态部分（常量/用例/分支在库）可断言；运行时结论（耗时、
    渲染结果、可达性）取自 CI 或真机，沙箱无 JDK21/SDK，标 UNKNOWN，不冒充 PASS。
  - 人判（MANUAL）：帧间观感 / 截图工件 / 真机流程，机器判不了；脚本只登记
    「为什么判不了」+「人判最小证据清单」。

复用既有脚本（不重造数据校验与孤儿扫描，事故三：禁平行机制）：
  - `astro_check_catalog.py`：内置星表行数/坐标/主键/88 星座/体积/引用完整性。
  - `astro_check_pyramid.py`：金字塔契约 + L0 逐行 mag≤6.5 断言 + 缓存切片。
  - `check-orphan-entries.py`：新星空层公开入口孤儿（写完没人调＝死链路，事故七）。

用法：
  python3 data_scripts/check-sky-spec.py              # 全量机判 + 半机/人判登记
  python3 data_scripts/check-sky-spec.py --selftest  # 好坏双样本双向自测
  python3 data_scripts/check-sky-spec.py --json      # 汇总同时落 JSON（供上游门禁消费）

退出码：
  0 = 全部机判项 PASS（半机/人判项已如实登记，不计失败）；
  2 = 有条件：机判项 PASS，但存在未闭环的 UNKNOWN / DEFER 项；
  1 = 有 FAIL（任一机判项不一致 / 缺锚点 / 违规样本命中）。

边界：本脚本是正则级静态解析，不编译 Kotlin（沙箱禁本地编译，铁律 E8）；
解析不了的复杂表达式标 UNKNOWN，不冒充通过。判定函数见 `sky_spec_checks.py`，
规格常量表见 `sky_spec_model.py`。
"""

import argparse
import datetime
import json
import subprocess
import sys
import tempfile
from pathlib import Path

from sky_spec_model import CORE_FILES, ROOT
import sky_spec_checks as checks


def _git_head(root: Path) -> str:
    try:
        proc = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(root),
                              capture_output=True, text=True, timeout=10)
        if proc.returncode == 0:
            return proc.stdout.strip()[:12]
    except OSError:
        pass
    return "unknown"


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="星空天象层 §十四 判据机器化总入口")
    ap.add_argument("--selftest", action="store_true", help="好坏双样本双向自测")
    ap.add_argument("--json", action="store_true", help="汇总同时输出 JSON")
    ap.add_argument("--root", default=str(ROOT), help="仓库根（selftest 夹具用）")
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()

    root = Path(args.root)
    now = datetime.datetime.now().strftime("%F %T")
    head = _git_head(root)
    items = checks.collect(root)
    rc, stats = checks.summarize(items)

    def status_line() -> str:
        if rc == 1:
            return "SKY-SPEC: FAIL（存在机判不一致项，须先修红）"
        if rc == 2:
            return "SKY-SPEC: CONDITIONAL（机判项通过；仍有半机 UNKNOWN / 人判 DEFER 待闭环）"
        return "SKY-SPEC: PASS（机判项全部通过）"

    if args.json:
        # JSON 独占 stdout 供上游门禁解析；人读摘要走 stderr，避免混在 JSON 流里。
        payload = {"head": head, "time": now, "stats": stats, "exit": rc,
                   "status": status_line(),
                   "items": [{"id": i.cid, "level": i.level, "conclusion": i.conclusion,
                              "evidence": i.evidence, "note": i.note} for i in items]}
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        print(status_line(), file=sys.stderr)
        return rc

    print(f"== check-sky-spec.py 星空天象层判据机检（{now}，HEAD {head}） ==")
    for it in items:
        print(it.render())
    print("")
    print(f"== 汇总: PASS {stats['PASS']} / FAIL {stats['FAIL']} / "
          f"UNKNOWN {stats['UNKNOWN']} / DEFER {stats['DEFER']} ==")
    print(status_line())
    return rc


# ─────────────────────────── 双向自测（必失败样本 + 放行样本）───────────────────────────

GOOD_PARAMS = """
package x
object SkyParams {
    const val SKY_PROJECTION_FAR: Double = 900.0
    const val SKY_ENTER_EARTH_DIAMETER_PX: Float = 40f
    const val SKY_TRANSITION_MS: Long = 600L
    const val VIEW_SWITCH_MS: Long = 800L
    const val MAG_DEPTH_LIMIT: Float = 11.5f
    const val MAG_BRIGHT_ENTITY: Float = 6.5f
    const val PYRAMID_LEVELS: Int = 2
    const val STAR_SPIKE_MAG: Float = 0.5f
    const val TWINKLE_MAG: Float = 2.5f
    const val FIELD_PULSE_MS: Long = 1200L
    const val METEOR_INTERVAL_MIN_MS: Long = 90_000L
    const val METEOR_INTERVAL_MAX_MS: Long = 240_000L
    const val GROUND_VIEW_FOV_DEG: Double = 60.0
    const val OBSERVER_HORIZON_GLOW_DEG: Double = 5.0
    const val SKY_BLEND_FULL_ZOOM: Float = 12.0f
    const val STAR_DIAMETER_VERY_BRIGHT_PX: Float = 9f
    const val STAR_DIAMETER_0_PX: Float = 5f
    const val STAR_DIAMETER_2_PX: Float = 3f
    const val STAR_DIAMETER_4_5_PX: Float = 1.5f
    const val STAR_DIAMETER_6_5_PX: Float = 1.0f
    const val STAR_DIAMETER_FLOOR_PX: Float = 0.8f
    const val CONSTELLATION_LINE_WIDTH_PX: Float = 0.8f
    const val CONSTELLATION_LINE_ALPHA_RATIO: Float = 0.45f
    const val ASTERISM_LINE_WIDTH_PX: Float = 1.0f
    const val ASTERISM_LINE_ALPHA_RATIO: Float = 0.55f
    const val FIELD_BORDER_WIDTH_PX: Float = 1.5f
    const val FIELD_BORDER_DASH_PX: Float = 6f
    const val FIELD_BORDER_GAP_PX: Float = 4f
    const val STAR_LABEL_FONT_SP: Float = 14f
    const val CONSTELLATION_NAME_FONT_SP: Float = 14f
    const val SHEET_TITLE_FONT_SP: Float = 16f
    const val SHEET_BODY_FONT_SP: Float = 14f
    const val SHEET_SOURCE_FONT_SP: Float = 14f
    const val LABEL_GAP_DP: Float = 8f
    const val HIT_ZONE_SIDE_DP: Float = 48f
    const val SHEET_RADIUS_DP: Float = 20f
    const val STAR_NAME_BOX_RADIUS_DP: Float = 2f
    const val LIST_SPACING_DP: Float = 8f
}
"""

GOOD_COLOR = """
package x
object SkySpecColor {
    const val STAR_ANCHOR_EARLY_RGB: Int = 0xD8E4F0
    const val STAR_ANCHOR_MID_RGB: Int = 0xFFFDF6
    const val STAR_ANCHOR_LATE_RGB: Int = 0xFFEED8
    const val STAR_ANCHOR_VERY_LATE_RGB: Int = 0xFFB37A
    const val METEOR_HEAD_RGB: Int = 0xFFFDF6
}
"""


def _build_fixture(root: Path, *, bad: bool) -> None:
    base = root / "app/src/main/java/io/legado/app/ui/map/globe"
    (base / "astro/contract").mkdir(parents=True, exist_ok=True)
    (base / "host").mkdir(parents=True, exist_ok=True)
    (root / "app/src/main/assets/astro").mkdir(parents=True, exist_ok=True)
    params = GOOD_PARAMS
    color = GOOD_COLOR
    renderer_lines = 300
    if bad:
        # 坏样本：常量改值 + 非允许表裸 hex + 纯黑 + 内联小字 + 渲染器超 400 行。
        params = params.replace("MAG_DEPTH_LIMIT: Float = 11.5f",
                                "MAG_DEPTH_LIMIT: Float = 9.0f")
        color = color.replace("0xFFB37A", "0xFF0000")
        (base / "host" / "SkySheet.kt").write_text(
            "package x\nval small = 12.sp\n", encoding="utf-8")
        (base / "host" / "Renegade.kt").write_text(
            "package x\nval c = Color(#123456)\nval b = Color(#000000)\n",
            encoding="utf-8")
        renderer_lines = 401
    (base / "astro/contract/SkyParams.kt").write_text(params, encoding="utf-8")
    (base / "astro/contract/SkySpecColor.kt").write_text(color, encoding="utf-8")
    # 夹具补齐规格点名的其余核心文件（空壳即可，锚点存在性闸只判在库）。
    for rel in CORE_FILES:
        if rel in ("astro/contract/SkyParams.kt", "astro/contract/SkySpecColor.kt",
                   "host/SkyCanvasRenderer.kt"):
            continue
        p = base / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("package x\n", encoding="utf-8")
    (base / "host/SkyCanvasRenderer.kt").write_text(
        "\n".join("// line" for _ in range(renderer_lines)), encoding="utf-8")
    (root / "docs/落文-规格切片").mkdir(parents=True, exist_ok=True)
    (root / "docs/落文-规格切片/切片-26-星空天象层.md").write_text("# 切片-26\n", encoding="utf-8")
    (root / "docs/落文-规格切片/切片-20-3D沙盘与地球仪合并.md").write_text(
        "# 头部\n> ⚠️ 2026-11-28 补：「Stellarium 星图」选项作废，见切片-26。\n",
        encoding="utf-8")
    (root / "docs/台账-数据源许可.md").write_text(
        "Source: ESA Hipparcos mission; Tycho-2 Catalogue (Høg et al. 2000), CDS I/259\n"
        "I/259 非商用；商业化即整体换源\n", encoding="utf-8")


def selftest() -> int:
    """好坏双样本：放行样本机判项不得红；坏样本必须在常量/锚色/禁色/字号/行数判红。"""
    results: list[tuple[str, bool, list[str]]] = []
    with tempfile.TemporaryDirectory() as gd, tempfile.TemporaryDirectory() as bd:
        good_root, bad_root = Path(gd), Path(bd)
        _build_fixture(good_root, bad=False)
        _build_fixture(bad_root, bad=True)

        good_items = checks.collect(good_root, include_reused=False)
        good_fails = [i for i in good_items if i.conclusion == checks.FAIL]
        results.append(("放行样本无机判 FAIL", not good_fails,
                        [i.render() for i in good_fails]))

        bad_items = checks.collect(bad_root, include_reused=False)
        bad_fails = [i for i in bad_items if i.conclusion == checks.FAIL]
        fail_ids = {i.cid for i in bad_fails}
        expect = {"§十六/§五 常量", "§六.12 锚色", "§十四.7 禁色",
                  "§五.5 字号", "§三.8 渲染器行数"}
        missing = expect - fail_ids
        results.append(("坏样本违规项均判红", not missing,
                        [f"未覆盖: {missing}"] if missing else
                        [i.render() for i in bad_fails]))

    ok = True
    for label, passed, out in results:
        print(f"-- selftest {label}: {'PASS' if passed else 'FAIL'}")
        for row in out:
            print("   " + row)
        ok = ok and passed
    print("SELFTEST: PASS" if ok else "SELFTEST: FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
