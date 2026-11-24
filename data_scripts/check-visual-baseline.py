#!/usr/bin/env python3
"""观感基线守卫（P 阶段观感打磨工序 P3 冻结产物；对应 docs/工序-观感打磨.md）。

为什么需要它：观感打磨是交付前置条件，不是进度缓冲。P3 冻结之后，已通过用户真机
判定的屏与资产不许被后续改动悄悄破坏；机械断言只能证明"东西还在"，证明不了
"冻结时那一屏的截图与资产没漂"，所以冻结轮的截图 + 资产 sha256 必须落基线文件，
本脚本逐条复核。

判据：
  - 基线文件不存在或为空 → WARN（工序未启动，P0/P1 尚未走到 P3），退出码 0，不算 FAIL。
  - 基线存在且非空 → 逐条校验被登记资产/截图的 sha256 与当前文件一致；
    任一不一致判 FAIL，并输出"哪一屏/哪个资产被破坏"；退出码 1。
  - --freeze <路径...>：把一批截图目录与对应资产文件（递归）哈希登记进基线文件，
    供 P3 冻结使用（覆盖写，一次冻结 = 一轮通过轮的完整快照）。
  - --selftest：造两个临时文件（一致 / 不一致各一）跑通两种判定，跑完自删，
    输出 PASS/FAIL 原文。
  - --ux [源码根]：UX 九维静态审计（对应 docs/工序-观感打磨.md 四补节）。能静态判的
    机械判（字号/间距/热区/反馈延迟/占位与兜底关键词配对）；判不了的维显式登记
    DEFER（需帧像素 / 需人眼 / 需真机 bench），不许假装判了。FAIL 退出 1；
    WARN（需人眼复核）与 DEFER（需帧/真机）不置 FAIL，但必须逐条列出。
  - --selftest 同时覆盖 UX 双向通路：违例夹具 → FAIL 点名；正常夹具 → PASS，
    且 DEFER 项必须出现在输出里。

门禁接入：本脚本暂未接入 data_scripts/rule-gate.sh；待总控接入为 ⑳ 门禁
（在 rule-gate.sh 增一行调用即可，本文件不改 rule-gate.sh）。

基线格式：标准 sha256sum 两行式 `<64位哈希>  <路径>`；`#` 开头为注释行；
路径优先按仓库根相对解析，解析不到再按绝对路径解析。
"""
import argparse
import hashlib
import os
import re
import shutil
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASELINE = os.path.join(ROOT, "docs", ".visual_baseline.sha256")


def sha256_file(path: str) -> str:
    """计算单个文件的 sha256；读取失败返回空串（缺失文件另由调用方判）。"""
    h = hashlib.sha256()
    try:
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(65536), b""):
                h.update(chunk)
        return h.hexdigest()
    except OSError:
        return ""


def resolve_path(rel: str) -> str:
    """基线路径 → 实际文件路径：先相对仓库根，再绝对路径。"""
    if os.path.isabs(rel):
        return rel
    cand = os.path.join(ROOT, rel)
    return cand if os.path.exists(cand) else rel


def load_baseline(baseline: str):
    """解析基线文件，返回 [(hash, path), ...]；空/缺失返回空列表。"""
    entries: list[tuple[str, str]] = []
    if not os.path.isfile(baseline) or os.path.getsize(baseline) == 0:
        return entries
    for line in open(baseline, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        parts = line.split("  ", 1)
        if len(parts) != 2:
            parts = line.split(None, 1)
        if len(parts) != 2 or len(parts[0]) != 64:
            continue  # 格式坏行不静默吞，verify 阶段在缺失/不一致里体现
        entries.append((parts[0].strip(), parts[1].strip()))
    return entries


def verify(baseline: str) -> int:
    """复核基线；返回退出码 0/1。WARN（无基线）退出 0。"""
    if not os.path.isfile(baseline) or os.path.getsize(baseline) == 0:
        print("WARN  观感基线不存在或为空：工序未启动（P0 截图基建前判 WARN，不算 FAIL）")
        return 0

    entries = load_baseline(baseline)
    if not entries:
        print("WARN  观感基线无有效登记项：P3 尚未冻结，不算 FAIL")
        return 0

    broken: list[str] = []
    missing: list[str] = []
    for registered, rel in entries:
        actual_path = resolve_path(rel)
        if not os.path.isfile(actual_path):
            missing.append(rel)
            continue
        current = sha256_file(actual_path)
        if current != registered:
            broken.append(
                f"{rel}（登记 {registered[:12]}…，当前 {current[:12] if current else '读不到'}…）"
            )

    for rel in missing:
        print(f"FAIL  观感基线被破坏：{rel} 已丢失（冻结屏/资产不存在）")
    for item in broken:
        print(f"FAIL  观感基线被破坏：{item}")

    if missing or broken:
        print(f"FAIL  观感基线复核未过：{len(missing)} 项丢失、{len(broken)} 项哈希不一致；"
              "破冻结即 FAIL，须复验不得破坏已冻结屏")
        return 1
    print(f"PASS  观感基线复核一致：{len(entries)} 项登记资产/截图哈希全部匹配")
    return 0


def freeze(paths: list[str], baseline: str, note: str = "") -> int:
    """把给定文件/目录（递归）登记进基线文件，覆盖写；返回退出码。"""
    files: list[str] = []
    for raw in paths:
        if not os.path.exists(raw):
            print(f"FAIL  freeze 输入不存在：{raw}")
            return 1
        if os.path.isfile(raw):
            files.append(raw)
        else:
            for dirpath, _, names in os.walk(raw):
                for name in sorted(names):
                    files.append(os.path.join(dirpath, name))
    if not files:
        print("FAIL  freeze 未收集到任何文件")
        return 1

    os.makedirs(os.path.dirname(baseline), exist_ok=True)
    lines = [f"# 观感基线冻结（P3）：{time.strftime('%Y-%m-%d %H:%M:%S')} {note}".rstrip()]
    for f in sorted(set(files)):
        h = sha256_file(f)
        if not h:
            print(f"FAIL  freeze 读取失败：{f}")
            return 1
        rel = os.path.relpath(f, ROOT) if os.path.abspath(f).startswith(ROOT) else os.path.abspath(f)
        lines.append(f"{h}  {rel}")
    with open(baseline, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    print(f"PASS  观感基线已冻结：{len(files)} 项登记 → {os.path.relpath(baseline, ROOT)}")
    return 0


def selftest() -> int:
    """造一致/不一致临时文件各一，跑通 PASS 与 FAIL 两种判定，跑完自删。"""
    tmp = tempfile.mkdtemp(prefix="visual-baseline-selftest-")
    tmp_baseline = os.path.join(tmp, "baseline.sha256")
    good = os.path.join(tmp, "good-screen.png")
    bad = os.path.join(tmp, "bad-asset.json")
    try:
        with open(good, "w", encoding="utf-8") as f:
            f.write("frozen-content-unchanged")
        with open(bad, "w", encoding="utf-8") as f:
            f.write("old-content")

        good_hash = sha256_file(good)
        bad_hash_old = sha256_file(bad)
        with open(tmp_baseline, "w", encoding="utf-8") as f:
            f.write(f"{good_hash}  {good}\n{bad_hash_old}  {bad}\n")

        # 判定 1：坏文件改动后，verify 应 FAIL 且点名 bad-asset.json；good 不得被报。
        with open(bad, "w", encoding="utf-8") as f:
            f.write("changed-after-freeze")
        # verify 直接打印，抓取输出用于断言
        import io
        import contextlib
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc1 = verify(tmp_baseline)
        out1 = buf.getvalue()
        assert rc1 == 1, f"不一致场景应 FAIL，实际 rc={rc1}"
        assert "bad-asset.json" in out1, f"FAIL 输出未点名被破坏屏：\n{out1}"
        assert "good-screen.png" not in out1, f"一致文件被误报：\n{out1}"

        # 判定 2：基线缺失 → WARN 且退出码 0。
        buf2 = io.StringIO()
        with contextlib.redirect_stdout(buf2):
            rc2 = verify(os.path.join(tmp, "missing.sha256"))
        out2 = buf2.getvalue()
        assert rc2 == 0, f"无基线应 WARN 且 exit 0，实际 rc={rc2}"
        assert "WARN" in out2, f"缺基线未输出 WARN：\n{out2}"

        print("PASS  selftest：不一致场景 FAIL 且点名被破坏屏；无基线场景 WARN 且 exit 0")
        return 0
    except AssertionError as exc:
        print(f"FAIL  selftest：{exc}")
        return 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


# ---- UX 九维静态审计（docs/工序-观感打磨.md 四补节，Q-20）----
# 设计原则：能静态判的机械判；判不了的显式 DEFER（需帧像素/需人眼/需真机 bench），
# 不许把"没测出"写成通过；WARN 是静态线索、需人眼复核，不直接等同 FAIL。

UX_SRC_DEFAULT = os.path.join(ROOT, "app", "src", "main")

RE_SP_SIZE = re.compile(r"([0-9.]+)\.sp(?:[0-9]+)?(?:\s*\()?")
RE_SP_HELPER = re.compile(r"\bsp([0-9]+)\s*\(")
# 只认热区声明（size/minHeight/minWidth）；裸 width/height 多为分隔条/描边高度，不算点击目标。
RE_DP_SIZE = re.compile(r"\b(?:size|minHeight|minWidth)\s*\(\s*([0-9.]+)\s*\.dp")
RE_SPACED = re.compile(r"spacedBy\(\s*([0-9.]+)\s*\.dp")
RE_DELAY = re.compile(r"\bdelay(?:Millis)?\(\s*([0-9]+)")

MIN_FONT_SP = 14.0      # UX-01 最小字号 ≥14sp
MIN_TARGET_DP = 48.0    # UX-03 点击目标 ≥48dp
MIN_GAP_DP = 8.0        # UX-03 间距 ≥8dp
MAX_FEEDBACK_MS = 100   # UX-04 反馈时延 ≤100ms（静态链路口径）

CLICKABLE_PATH_RE = re.compile(
    r"(button|icon|tab|nav|bubble|pill|action|fab|chip|slate|toolbar|menu|bar)", re.I)
ASYNC_KW = ("collectAsState", "LaunchedEffect", "collectAsStateWithLifecycle", "produceState")
PLACEHOLDER_KW = ("骨架", "占位", "loading", "empty", "placeholder", "空态", "skeleton", "Skeleton")
ERROR_KW = ("error", "Error", "uiState", "offline", "离线", "network", "Network")
RETRY_KW = ("retry", "重试", "兜底", "offline", "离线", "empty", "恢复")
GESTURE_AREA_KW = ("翻页", "滑动", "缩放", "zoom", "swipe", "捏合")
GESTURE_HINT_KW = ("hint", "提示", "手势", "轻扫", "引导", "swipeHint", "Indicator")

UX_DIMENSIONS = [
    ("UX-01", "可读性（字号/行距/对比度）"),
    ("UX-02", "暖金辉光/暗色下正文对比度保底"),
    ("UX-03", "点击目标≥48dp、间距≥8dp"),
    ("UX-04", "点击到视觉反馈≤100ms"),
    ("UX-05", "加载与空态（异步入口骨架/占位）"),
    ("UX-06", "错误恢复（断网/缺数据重试或兜底）"),
    ("UX-07", "手势可发现性（首访可见提示）"),
    ("UX-08", "返回栈一致性（进度/视角恢复）"),
    ("UX-09", "长文性能手感（连续翻页≥50帧无掉帧）"),
]

# 静态判不了、必须显式登记 DEFER 的替代口径与降级证据要求（不许静默放过）。
UX_DEFER = {
    "UX-01": [
        "行距≥1.4倍字号：静态配不上对的条目需渲染帧像素复核（lineHeight÷fontSize）",
        "正文对比度≥4.5:1：需帧像素取色算 WCAG 对比度；无帧不冒充通过",
    ],
    "UX-02": [
        "暗色/辉光叠加下正文对比度≥4.5:1：需暗色帧像素取色；无帧显式 DEFER 需帧/真机",
    ],
    "UX-03": [
        "热区是否真为可点归属：静态只判数值，需验收代理对帧/代码复核",
    ],
    "UX-04": [
        "手势落点→反馈可见时延：截图帧测不到时间，需帧序列序号差推算或真机 bench；无真机必须登记替代口径与降级证据",
    ],
    "UX-05": [
        "占位是否真展示、三变体（loading/empty/offline）是否齐全：需人眼/帧复核",
    ],
    "UX-06": [
        "错误分支是否真有重试/兜底文案、无异常逃逸：需验收代理走查 + 单测证据",
    ],
    "UX-07": [
        "首访提示是否首次可见、是否被后续操作顶掉：需人眼/帧复核",
    ],
    "UX-08": [
        "保存-恢复是否真跨返回生效：需集成走查/真机复核",
    ],
    "UX-09": [
        "连续翻页≥50帧无掉帧：需真机 bench；帧序列替代口径不得冒充真机帧率结论",
    ],
}


def _iter_kt_files(src: str):
    """递归枚举源码根下 .kt 文件；目录不存在返回空迭代。"""
    if not os.path.isdir(src):
        return
    for dirpath, _, names in os.walk(src):
        for name in sorted(names):
            if name.endswith(".kt"):
                yield os.path.join(dirpath, name)


def _font_sp_values(line: str) -> list[float]:
    """从一行文本提取字号数值，兼容 `13.sp`、`13.sp13()`、`sp12()` 三种写法。"""
    vals: list[float] = []
    for m in RE_SP_SIZE.finditer(line):
        vals.append(float(m.group(1)))
    for m in RE_SP_HELPER.finditer(line):
        vals.append(float(m.group(1)))
    return vals


def _rel(path: str) -> str:
    return os.path.relpath(path, ROOT) if os.path.abspath(path).startswith(ROOT) else path


def audit_ux(src_root: str) -> int:
    """UX 九维静态审计；返回退出码 0/1。WARN/DEFER 不置 FAIL，但逐条列出。"""
    findings: dict[str, set[str]] = {"FAIL": set(), "WARN": set()}

    if not os.path.isdir(src_root):
        print(f"WARN  UX 源码根不存在：{_rel(src_root)}；静态项跳过，全部维转 DEFER/人眼复核")
    else:
        for path in _iter_kt_files(src_root):
            try:
                with open(path, encoding="utf-8") as f:
                    lines = f.readlines()
            except OSError as exc:
                findings["WARN"].add(f"UX-00 读取失败 {_rel(path)}：{exc}")
                continue
            text = "".join(lines)
            rel = _rel(path)
            is_ui = "/ui/" in path.replace(os.sep, "/")

            for lineno, line in enumerate(lines, start=1):
                # UX-01 最小字号 ≥14sp；letterSpacing/lineHeight 是字距/行距，不是字号，排除。
                if (".sp" in line or re.search(r"\bsp[0-9]+\s*\(", line)) and \
                        "letterSpacing" not in line and "lineHeight" not in line:
                    for val in _font_sp_values(line):
                        if val < MIN_FONT_SP:
                            findings["FAIL"].add(
                                f"UX-01 {rel}:{lineno} 字号 {val:g}sp < {MIN_FONT_SP:g}sp（最小字号红线）")
                # UX-03 列表/卡片间距 ≥8dp
                for m in RE_SPACED.finditer(line):
                    if float(m.group(1)) < MIN_GAP_DP:
                        findings["FAIL"].add(
                            f"UX-03 {rel}:{lineno} spacedBy {m.group(1)}dp < {MIN_GAP_DP:g}dp（间距红线）")
                # UX-03 可点文件内热区 ≥48dp
                if CLICKABLE_PATH_RE.search(path):
                    for m in RE_DP_SIZE.finditer(line):
                        if float(m.group(1)) < MIN_TARGET_DP:
                            findings["FAIL"].add(
                                f"UX-03 {rel}:{lineno} 热区 {m.group(1)}dp < {MIN_TARGET_DP:g}dp（点击目标红线）")
                # UX-04 交互路径 >100ms delay 仅作线索（可能是 Toast 等待/轮询，非反馈时延），
                # 降级 WARN 需人眼复核；≤100ms 反馈时延本体属 DEFER 真机/帧序列测量。
                if is_ui:
                    for m in RE_DELAY.finditer(line):
                        if int(m.group(1)) > MAX_FEEDBACK_MS:
                            findings["WARN"].add(
                                f"UX-04 {rel}:{lineno} 交互路径 delay {m.group(1)}ms > {MAX_FEEDBACK_MS}ms，"
                                "需人眼复核是否卡在反馈链路上（轮询/Toast 等待豁免）")

            # UX-05 异步入口必须有骨架/占位/空态之一
            if any(k in text for k in ASYNC_KW) and not any(k in text for k in PLACEHOLDER_KW):
                findings["WARN"].add(
                    f"UX-05 {rel} 异步入口未见骨架/占位/空态关键词，需人眼复核是否存在空白态")
            # UX-06 错误/离线分支必须有重试或兜底文案之一
            if any(k in text for k in ERROR_KW) and not any(k in text for k in RETRY_KW):
                findings["WARN"].add(
                    f"UX-06 {rel} 错误/离线分支未见重试或兜底关键词，需走查是否崩溃死页")
            # UX-07 翻页/缩放手势区应有首访提示关键词
            if any(k in text for k in GESTURE_AREA_KW) and not any(k in text for k in GESTURE_HINT_KW):
                findings["WARN"].add(
                    f"UX-07 {rel} 手势区未见首访提示关键词，需人眼/帧复核可发现性")

    print("== UX 审计（check-visual-baseline.py --ux，九维静态 + DEFER 登记）==")
    for uid, title in UX_DIMENSIONS:
        print(f"\n{uid} {title}")
        for item in sorted(findings["FAIL"]):
            if item.startswith(uid):
                print(f"  FAIL  {item}")
        for item in sorted(findings["WARN"]):
            if item.startswith(uid):
                print(f"  WARN  {item}")
        for defer in UX_DEFER[uid]:
            print(f"  DEFER {defer}（需人眼/需真机，未判通过）")

    fail_n = sum(1 for i in findings["FAIL"])
    warn_n = sum(1 for i in findings["WARN"])
    defer_n = sum(len(v) for v in UX_DEFER.values())
    print(f"\nSUMMARY: FAIL {fail_n} 项；WARN {warn_n} 项（需人眼复核）；"
          f"DEFER {defer_n} 项（需帧/真机，显式登记，未冒充通过）")
    if fail_n:
        print("FAIL  UX 静态审计存在红线违例，须按台账-观感UX.md 闭环后再进 P5/P6")
        return 1
    print("PASS  UX 静态审计无红线违例；WARN/DEFER 项须在台账登记替代口径与降级证据")
    return 0


def selftest_ux() -> int:
    """UX 双向自测：违例夹具 → FAIL 点名；正常夹具 → PASS 且 DEFER 仍在；跑完自删。"""
    tmp = tempfile.mkdtemp(prefix="visual-baseline-ux-selftest-")
    try:
        bad_dir = os.path.join(tmp, "bad", "ui", "icon")
        good_dir = os.path.join(tmp, "good", "ui", "bookshelf")
        os.makedirs(bad_dir)
        os.makedirs(good_dir)
        bad = os.path.join(bad_dir, "BadScreen.kt")
        good = os.path.join(good_dir, "GoodScreen.kt")
        with open(bad, "w", encoding="utf-8") as f:
            f.write(
                "Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {\n"
                "    Text(fontSize = 12.sp)\n"
                "    Icon(Modifier.size(40.dp))\n"
                "    LaunchedEffect(Unit) { delay(300) }\n"
                "}\n")
        with open(good, "w", encoding="utf-8") as f:
            f.write(
                "Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {\n"
                "    Text(fontSize = 16.sp, lineHeight = 24.sp)\n"
                "    Icon(Modifier.size(48.dp))\n"
                "    LaunchedEffect(Unit) { delay(80) }\n"
                "    if (loading) { /* 骨架占位 skeleton */ } else { EmptyState(重试 = { retry() }) }\n"
                "}\n")

        import contextlib
        import io
        buf_bad = io.StringIO()
        with contextlib.redirect_stdout(buf_bad):
            rc_bad = audit_ux(os.path.join(tmp, "bad"))
        out_bad = buf_bad.getvalue()
        assert rc_bad == 1, f"违例夹具应 FAIL，实际 rc={rc_bad}"
        for token in ["12sp", "4dp", "40dp", "300ms"]:
            assert token in out_bad, f"FAIL 输出未点名违例 {token}：\n{out_bad}"

        buf_good = io.StringIO()
        with contextlib.redirect_stdout(buf_good):
            rc_good = audit_ux(os.path.join(tmp, "good"))
        out_good = buf_good.getvalue()
        assert rc_good == 0, f"正常夹具应 PASS，实际 rc={rc_good}"
        assert "FAIL  UX 静态审计存在红线违例" not in out_good, f"正常夹具被误报 FAIL：\n{out_good}"
        assert "DEFER" in out_good, "正常夹具仍须列出 DEFER 项，不许假装全判了"
        assert "对比度≥4.5:1" in out_good, "对比度 DEFER 条目缺失"

        print("PASS  selftest-ux：违例夹具 FAIL 并点名字号/间距/热区，延迟线索入 WARN；"
              "正常夹具 PASS 且 DEFER 项逐条保留")
        return 0
    except AssertionError as exc:
        print(f"FAIL  selftest-ux：{exc}")
        return 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main() -> int:
    ap = argparse.ArgumentParser(description="观感基线守卫（P 阶段观感打磨 P3 冻结）")
    ap.add_argument("--baseline", default=BASELINE, help="基线文件路径（默认 docs/.visual_baseline.sha256）")
    ap.add_argument("--freeze", nargs="+", metavar="PATH", help="冻结：登记给定截图目录/资产文件哈希")
    ap.add_argument("--note", default="", help="freeze 备注（冻结轮次/轮次日期）")
    ap.add_argument("--selftest", action="store_true", help="跑内置自测（临时文件，跑完自删）")
    ap.add_argument("--ux", nargs="?", const=UX_SRC_DEFAULT, metavar="SRC",
                    help="UX 九维静态审计（默认扫 app/src/main）")
    args = ap.parse_args()

    if args.selftest:
        rc1 = selftest()
        rc2 = selftest_ux()
        return rc1 or rc2
    if args.ux is not None:
        return audit_ux(args.ux)
    if args.freeze:
        return freeze(args.freeze, args.baseline, args.note)
    return verify(args.baseline)


if __name__ == "__main__":
    sys.exit(main())
