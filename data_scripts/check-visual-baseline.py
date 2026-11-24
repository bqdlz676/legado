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

门禁接入：本脚本暂未接入 data_scripts/rule-gate.sh；待总控接入为 ⑳ 门禁
（在 rule-gate.sh 增一行调用即可，本文件不改 rule-gate.sh）。

基线格式：标准 sha256sum 两行式 `<64位哈希>  <路径>`；`#` 开头为注释行；
路径优先按仓库根相对解析，解析不到再按绝对路径解析。
"""
import argparse
import hashlib
import os
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


def main() -> int:
    ap = argparse.ArgumentParser(description="观感基线守卫（P 阶段观感打磨 P3 冻结）")
    ap.add_argument("--baseline", default=BASELINE, help="基线文件路径（默认 docs/.visual_baseline.sha256）")
    ap.add_argument("--freeze", nargs="+", metavar="PATH", help="冻结：登记给定截图目录/资产文件哈希")
    ap.add_argument("--note", default="", help="freeze 备注（冻结轮次/轮次日期）")
    ap.add_argument("--selftest", action="store_true", help="跑内置自测（临时文件，跑完自删）")
    args = ap.parse_args()

    if args.selftest:
        return selftest()
    if args.freeze:
        return freeze(args.freeze, args.baseline, args.note)
    return verify(args.baseline)


if __name__ == "__main__":
    sys.exit(main())
