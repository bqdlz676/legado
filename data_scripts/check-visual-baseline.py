#!/usr/bin/env python3
"""观感基线守卫（P 阶段观感打磨工序 P3 冻结产物；对应 docs/工序-观感打磨.md）。

为什么需要它：观感打磨是交付前置条件，不是进度缓冲。P3 冻结之后，已通过用户真机
判定的屏与资产不许被后续改动悄悄破坏；机械断言只能证明"东西还在"，证明不了
"冻结时那一屏的截图与资产没漂"，所以冻结轮的截图 + 资产 sha256 必须落基线文件，
本脚本逐条复核。

双层登记（源码层 + 帧像素层，2026-11-27 扩；N2 收口：旧版只锁源码契约形状，锁不住画面）：
  - 源码层：测试套件源码、帧契约清单文本等仓库内资产，哈希漂移即 FAIL（既有语义不动）。
  - 帧层：CI Paparazzi 产出的 golden PNG 帧，哈希锁画面像素；帧由 CI artifact 产出，
    仓内本地常态缺席，故帧缺席只 WARN 点名、不打红；帧在本地存在但哈希不符才 FAIL。

判据（逐分支退出码）：
  - 基线文件不存在或为空 → WARN（工序未启动，P0/P1 尚未走到 P3），退出码 0，不算 FAIL。
  - 源码层条目丢失（文件不存在）→ FAIL 点名路径，退出码 1（既有语义不动）。
  - 源码层哈希不一致 → FAIL 点名前后 12 位，退出码 1（既有语义不动）。
  - 帧层 PNG 存在且哈希一致 → 计入匹配，不单独出声。
  - 帧层 PNG 存在但哈希与登记不符 → FAIL 点名帧标签与路径，退出码 1。
  - 帧层已登记但 PNG 本地缺席 → WARN 点名帧标签与路径（不许静默跳过），退出码 0；
    若同次复核另有 FAIL 项，退出码仍为 1，WARN 行照常列出。
  - 无任何 FAIL、仅帧缺席 WARN → 退出码 0；源码层 + 帧层全匹配 → PASS 退出码 0。
  - --freeze <路径...>：把一批截图目录与对应资产文件（递归）哈希登记进基线文件，
    供 P3 冻结使用（覆盖写，一次冻结 = 一轮通过轮的完整快照）。PNG 存在才登记帧层；
    PNG 缺席帧跳过登记、不写幽灵条目；但整批零真实文件可收集时按「未收集到任何文件」FAIL 退出（空跑不许假绿）。
  - --selftest：临时目录造真夹具（源码好样本 / 源码失配 FAIL / 帧失配 FAIL /
    帧缺席 WARN / 无基线 WARN）跑通各向判定，跑完自删，输出 PASS/FAIL 原文。
  - --ux [源码根]：UX 九维静态审计（对应 docs/工序-观感打磨.md 四补节）。能静态判的
    机械判（字号/间距/热区/反馈延迟/占位与兜底关键词配对）；判不了的维显式登记
    DEFER（需帧像素 / 需人眼 / 需真机 bench），不许假装判了。FAIL 退出 1；
    WARN（需人眼复核）与 DEFER（需帧/真机）不置 FAIL，但必须逐条列出。
  - --selftest 同时覆盖 UX 双向通路：违例夹具 → FAIL 点名；正常夹具 → PASS，
    且 DEFER 项必须出现在输出里。

门禁接入：本脚本由 data_scripts/rule-gate.sh 第 52 项调用；本文件不改 rule-gate.sh。

基线格式（向后兼容：旧版源码行原样可解析）：
  - 源码层行：`<64位哈希>  <相对路径>`（标准 sha256sum 两行式，与旧版逐字一致）。
  - 帧层行：`frame:<组>/<方法名>  <64位哈希>  <相对PNG路径>`；
    组 / 方法名取自 screenshot/frames-baseline.json 的 block / method；
    清单查不到时回落 `frame:<PNG文件名去扩展名>`。
  - `#` 开头为注释行；路径优先按仓库根相对解析，解析不到再按绝对路径解析。
解析正则（与 load_baseline 实现一致）：
  - 帧层：`^frame:(?P<label>\\S+)\\s{2}(?P<hash>[0-9a-f]{64})\\s{2}(?P<path>\\S.*)$`
  - 源码层：`^(?P<hash>[0-9a-f]{64})\\s{2}(?P<path>\\S.*)$`（双空格切不开时回落空白切分）。
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASELINE = os.path.join(ROOT, "docs", ".visual_baseline.sha256")
# 帧契约清单：帧层标签 <组>/<方法名> 的唯一事实源（与 frame-manifest.py 同 schema）。
FRAME_MANIFEST = os.path.join(ROOT, "screenshot", "frames-baseline.json")

# 帧层行：frame:<组>/<方法名>  <哈希>  <PNG 相对路径>；源码层行保持旧版 sha256sum 两行式。
# 为什么标签前置 frame: 前缀：与源码层行同文件混排时，解析器按前缀分流两层，不必另开文件。
RE_FRAME_LINE = re.compile(r"^frame:(?P<label>\S+)\s{2}(?P<hash>[0-9a-f]{64})\s{2}(?P<path>\S.*)$")
RE_SRC_LINE = re.compile(r"^(?P<hash>[0-9a-f]{64})\s{2}(?P<path>\S.*)$")


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


def load_frame_manifest() -> dict[str, tuple[str, str]]:
    """读帧契约清单，返回 {PNG 相对路径: (block, method)}；清单缺失/坏 JSON 返回空表。

    为什么降级为空表而不是 FAIL：清单解析失败不应让基线守卫自己崩；帧标签回落文件名，
    哈希复核通路不受影响（标签只用于点名与可读性）。
    """
    mapping: dict[str, tuple[str, str]] = {}
    try:
        with open(FRAME_MANIFEST, encoding="utf-8") as fh:
            doc = json.load(fh)
        for fr in doc.get("frames", []):
            op = fr.get("outputPath")
            if op:
                mapping[op] = (fr.get("block") or "unknown", fr.get("method") or "unknown")
    except (OSError, ValueError, AttributeError):
        pass
    return mapping


def load_baseline(baseline: str):
    """解析基线文件，返回条目列表；每条为 dict：
    {"layer": "src"|"frame", "hash": str, "path": str, "label": str}。
    空/缺失返回空列表；旧版源码行原样解析为 src 层（向后兼容）。"""
    entries: list[dict[str, str]] = []
    if not os.path.isfile(baseline) or os.path.getsize(baseline) == 0:
        return entries
    for line in open(baseline, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        m_frame = RE_FRAME_LINE.match(line)
        if m_frame:
            entries.append({"layer": "frame", "hash": m_frame.group("hash").strip(),
                            "path": m_frame.group("path").strip(),
                            "label": m_frame.group("label").strip()})
            continue
        m_src = RE_SRC_LINE.match(line)
        if m_src:
            hash_v, rel_v = m_src.group("hash").strip(), m_src.group("path").strip()
        else:
            parts = line.split("  ", 1)
            if len(parts) != 2:
                parts = line.split(None, 1)
            if len(parts) != 2 or len(parts[0]) != 64:
                continue  # 格式坏行不静默吞，verify 阶段在缺失/不一致里体现
            hash_v, rel_v = parts[0].strip(), parts[1].strip()
        entries.append({"layer": "src", "hash": hash_v, "path": rel_v, "label": ""})
    return entries


def verify(baseline: str) -> int:
    """复核基线；返回退出码 0/1。WARN（无基线 / 帧缺席）退出 0；任一 FAIL 退出 1。"""
    if not os.path.isfile(baseline) or os.path.getsize(baseline) == 0:
        print("WARN  观感基线不存在或为空：工序未启动（P0 截图基建前判 WARN，不算 FAIL）")
        return 0

    entries = load_baseline(baseline)
    if not entries:
        print("WARN  观感基线无有效登记项：P3 尚未冻结，不算 FAIL")
        return 0

    src_missing: list[str] = []
    src_broken: list[str] = []
    frame_broken: list[dict[str, str]] = []
    frame_missing: list[dict[str, str]] = []
    for e in entries:
        registered, rel = e["hash"], e["path"]
        actual_path = resolve_path(rel)
        if not os.path.isfile(actual_path):
            # 帧缺席是本地常态（帧由 CI artifact 产出，仓内不存 PNG）：WARN 点名，不静默；
            # 源码条目丢失仍是 FAIL（冻结资产被删/移走，不属本地缺席常态）。
            if e["layer"] == "frame":
                frame_missing.append(e)
            else:
                src_missing.append(rel)
            continue
        current = sha256_file(actual_path)
        if current != registered:
            if e["layer"] == "frame":
                frame_broken.append(e)
            else:
                src_broken.append(
                    f"{rel}（登记 {registered[:12]}…，当前 {current[:12] if current else '读不到'}…）"
                )

    for rel in src_missing:
        print(f"FAIL  观感基线被破坏：{rel} 已丢失（冻结屏/资产不存在）")
    for item in src_broken:
        print(f"FAIL  观感基线被破坏：{item}")
    for e in frame_broken:
        cur = sha256_file(resolve_path(e["path"]))
        print(f"FAIL  观感基线帧被破坏：frame:{e['label']}（{e['path']}）"
              f"登记 {e['hash'][:12]}…，当前 {cur[:12] if cur else '读不到'}…")
    for e in frame_missing:
        # 不许静默：每条缺席帧都点名标签与路径；本地未渲染 / 未取到 artifact 同归此路。
        print(f"WARN  观感基线帧缺席：frame:{e['label']}（{e['path']}）"
              "本地未渲染或未取到 CI artifact，待帧落盘后复核（不静默跳过）")

    fail_n = len(src_missing) + len(src_broken) + len(frame_broken)
    if fail_n:
        print(f"FAIL  观感基线复核未过：源码 {len(src_missing)} 项丢失、{len(src_broken)} 项哈希不一致；"
              f"帧层 {len(frame_broken)} 项哈希不一致；破冻结即 FAIL，须复验不得破坏已冻结屏")
        return 1
    if frame_missing:
        src_n = sum(1 for e in entries if e["layer"] == "src")
        print(f"WARN  观感基线：源码层 {src_n} 项一致；帧层 {len(frame_missing)} 项本地缺席"
              "（本地无 PNG 常态，待 CI 帧落盘后复核；缺席帧不置 FAIL，已逐条点名）")
        return 0
    src_n = sum(1 for e in entries if e["layer"] == "src")
    frame_n = len(entries) - src_n
    if frame_n:
        print(f"PASS  观感基线复核一致：源码层 {src_n} 项 + 帧层 {frame_n} 项哈希全部匹配")
    else:
        print(f"PASS  观感基线复核一致：{src_n} 项登记资产/截图哈希全部匹配")
    return 0


def freeze(paths: list[str], baseline: str, note: str = "") -> int:
    """把给定文件/目录（递归）登记进基线文件，覆盖写；返回退出码。

    双层口径：.png 走帧层（frame:<组>/<方法> 前缀，标签查帧契约清单）；其余走源码层。
    PNG 本地缺席是常态（帧由 CI artifact 产出，仓内不存）：缺席帧跳过登记、不写幽灵帧条目；
    非 PNG 输入不存在仍是调用错误；整批零真实文件可收集时按未收集到文件 RC=1 退出，空跑不许假绿。
    """
    files: list[str] = []
    skipped_frames: list[str] = []
    manifest = load_frame_manifest()
    for raw in paths:
        if not os.path.exists(raw):
            if _looks_like_frame_path(raw):
                skipped_frames.append(raw)
                continue
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
    src_lines: list[str] = []
    frame_lines: list[str] = []
    for f in sorted(set(files)):
        h = sha256_file(f)
        if not h:
            print(f"FAIL  freeze 读取失败：{f}")
            return 1
        rel = os.path.relpath(f, ROOT) if os.path.abspath(f).startswith(ROOT) else os.path.abspath(f)
        if f.lower().endswith(".png"):
            block, method = manifest.get(rel, ("unknown", os.path.splitext(os.path.basename(f))[0]))
            frame_lines.append(f"frame:{block}/{method}  {h}  {rel}")
        else:
            src_lines.append(f"{h}  {rel}")
    lines.extend(src_lines)
    lines.extend(frame_lines)
    with open(baseline, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    msg = (f"PASS  观感基线已冻结：源码层 {len(src_lines)} 项 + 帧层 {len(frame_lines)} 项登记 "
           f"→ {os.path.relpath(baseline, ROOT)}")
    if skipped_frames:
        msg += f"；{len(skipped_frames)} 个本地缺席帧未登记（待 CI 帧落盘后重冻）"
    print(msg)
    return 0


def _looks_like_frame_path(raw: str) -> bool:
    """判断不存在的输入是否属于帧产物（PNG 或快照/帧目录）；是则缺席降级不报错。

    为什么收窄到路径特征而非一律放行：源码/资产路径不存在仍是派单或路径写错，必须 FAIL。
    """
    low = raw.lower()
    if low.endswith(".png"):
        return True
    return any(part in low for part in ("snapshots", "images", "screenshots"))


def selftest() -> int:
    """三向以上自测：源码好样本 PASS / 源码失配 FAIL / 帧失配 FAIL / 帧缺席 WARN /
    无基线 WARN；全部夹具落在 mkdtemp 临时目录，finally 自删。"""
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

        # 判定 1：源码坏文件改动后，verify 应 FAIL 且点名 bad-asset.json；good 不得被报。
        with open(bad, "w", encoding="utf-8") as f:
            f.write("changed-after-freeze")
        # verify 直接打印，抓取输出用于断言
        import io
        import contextlib
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc1 = verify(tmp_baseline)
        out1 = buf.getvalue()
        assert rc1 == 1, f"源码失配场景应 FAIL，实际 rc={rc1}"
        assert "bad-asset.json" in out1, f"FAIL 输出未点名被破坏屏：\n{out1}"
        assert "good-screen.png" not in out1, f"一致文件被误报：\n{out1}"

        # 判定 2：基线缺失 → WARN 且退出码 0。
        buf2 = io.StringIO()
        with contextlib.redirect_stdout(buf2):
            rc2 = verify(os.path.join(tmp, "missing.sha256"))
        out2 = buf2.getvalue()
        assert rc2 == 0, f"无基线应 WARN 且 exit 0，实际 rc={rc2}"
        assert "WARN" in out2, f"缺基线未输出 WARN：\n{out2}"

        # 判定 3：无 PNG 的好样本（仅源码登记，本地无帧常态）→ PASS rc=0，必须保持绿。
        good_only = os.path.join(tmp, "baseline-good-only.sha256")
        with open(good_only, "w", encoding="utf-8") as f:
            f.write(f"{good_hash}  {good}\n")
        buf3 = io.StringIO()
        with contextlib.redirect_stdout(buf3):
            rc3 = verify(good_only)
        out3 = buf3.getvalue()
        assert rc3 == 0, f"无 PNG 好样本应 PASS，实际 rc={rc3}"
        assert "PASS" in out3, f"好样本未输出 PASS：\n{out3}"

        # 判定 4：帧层 PNG 存在但哈希失配 → FAIL 点名帧标签与路径（假 PNG，几个字节即可）。
        frames_dir = os.path.join(tmp, "frames")
        os.makedirs(frames_dir)
        frame_png = os.path.join(frames_dir, "io_legado_ci_shellMismatch.png")
        with open(frame_png, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\nfake-png-v1")
        frame_hash = sha256_file(frame_png)
        frame_base = os.path.join(tmp, "baseline-frame-broken.sha256")
        with open(frame_base, "w", encoding="utf-8") as f:
            f.write(f"{good_hash}  {good}\nframe:shell/shellMismatch  {frame_hash}  {frame_png}\n")
        with open(frame_png, "wb") as f:  # 冻结后改动像素 → 帧哈希失配
            f.write(b"\x89PNG\r\n\x1a\nfake-png-v2-changed")
        buf4 = io.StringIO()
        with contextlib.redirect_stdout(buf4):
            rc4 = verify(frame_base)
        out4 = buf4.getvalue()
        assert rc4 == 1, f"帧失配应 FAIL，实际 rc={rc4}"
        assert "shellMismatch" in out4, f"FAIL 未点名帧标签：\n{out4}"
        assert os.path.basename(frame_png) in out4, f"FAIL 未点名帧路径：\n{out4}"

        # 判定 5：帧已登记但 PNG 缺席 → WARN 点名帧名，rc=0（不许静默跳过）。
        ghost_png = os.path.join(frames_dir, "io_legado_ci_shellGhost.png")
        ghost_hash = hashlib.sha256(b"ghost-registered-bytes").hexdigest()
        ghost_base = os.path.join(tmp, "baseline-frame-missing.sha256")
        with open(ghost_base, "w", encoding="utf-8") as f:
            f.write(f"{good_hash}  {good}\nframe:shell/shellGhost  {ghost_hash}  {ghost_png}\n")
        buf5 = io.StringIO()
        with contextlib.redirect_stdout(buf5):
            rc5 = verify(ghost_base)
        out5 = buf5.getvalue()
        assert rc5 == 0, f"帧缺席应 WARN 且 exit 0，实际 rc={rc5}"
        assert "WARN" in out5 and "shellGhost" in out5, f"帧缺席未点名 WARN：\n{out5}"

        print("PASS  selftest：源码失配 FAIL 点名；无 PNG 好样本 PASS；帧失配 FAIL 点名；"
              "帧缺席 WARN 点名且 exit 0；无基线 WARN 且 exit 0")
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

            # UX-05/06/07 属 UI 层体验项；api/help/config/dict 等逻辑层不参与关键词配对，免误报。
            if is_ui:
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
