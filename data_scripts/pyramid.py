#!/usr/bin/env python3
"""金字塔验收：每轮打印七层现状（2026-11-26 定稿，配套 docs/机制-金字塔验收.md）。

为什么要有它：验收被当成一道门的时候，人只能靠记忆决定"这单验到什么程度"。
拆成七层以后，每层的绿/红/缺口都能机检或按事实数出来，于是"验到哪一层"不再是自由心证。

只读：不提交、不推送、不改任何文件。
用法：python3 data_scripts/pyramid.py [--commits 6]
"""

import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def sh(args):
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True).stdout


def tail_ok(cmd):
    out = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, shell=True).stdout
    last = out.strip().splitlines()[-1] if out.strip() else "（无输出）"
    return ("FAIL" not in last and "红" not in last), last


def ci_status():
    token = subprocess.run("grep -o 'ghp_[A-Za-z0-9]*' ~/.git-credentials | head -1",
                           shell=True, capture_output=True, text=True).stdout.strip()
    head = sh(["git", "rev-parse", "--short", "HEAD"]).strip()
    api = subprocess.run(
        f"curl -s -H 'Authorization: Bearer {token}' "
        "'https://api.github.com/repos/bqdlz676/legado/actions/runs?per_page=8'",
        shell=True, capture_output=True, text=True).stdout
    rows = []
    for m in re.finditer(r'"head_sha":"(\w{7})\w*"[^}]*?"name":"([^"]+)"[^}]*?"status":"(\w+)"[^}]*?"conclusion":(null|"\w+")', api):
        if m.group(1) == head:
            rows.append((m.group(2), m.group(3), (m.group(4) or "").strip('"') or "-"))
    return head, rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--commits", type=int, default=6)
    ap.parse_args()

    print("=== 金字塔验收现状 ===")

    # L0 静态事实层
    g_ok, g_tail = tail_ok("python3 data_scripts/cross-module-guard.py")
    f_ok, f_tail = tail_ok("python3 data_scripts/check-frankenstein.py")
    r_ok, r_tail = tail_ok("python3 data_scripts/debt-ratchet.py")
    print(f"L0 静态事实：guard {'绿' if g_ok else '红'} / frankenstein {'绿' if f_ok else '红'} / ratchet {'绿' if r_ok else '红'}")
    for t in (g_tail, f_tail, r_tail):
        print(f"    {t}")

    # L1 单元行为层：数"自查反例"节的覆盖率（有这节才说明做过改坏推演）
    docs = [f for f in os.listdir(os.path.join(ROOT, "docs"))
            if f.startswith(("修复-", "验收-")) and f.endswith(".md")]
    with_sec = 0
    for f in docs:
        body = open(os.path.join(ROOT, "docs", f), encoding="utf-8", errors="ignore").read()
        if "自查反例" in body or "改坏" in body:
            with_sec += 1
    print(f"L1 单元行为：报告 {len(docs)} 份，含改坏推演/自查反例的 {with_sec} 份")

    # L2 结构契约：数"单源/唯一/全仓"这类回证出现次数，作为人工判断的输入
    prints = len(re.findall(r"单源", "\n".join(
        open(os.path.join(ROOT, "docs", f), encoding="utf-8", errors="ignore").read() for f in docs)))
    print(f"L2 结构契约：报告里出现「单源」回证 {prints} 处（人类判断输入，非机检结论）")

    # L3/L4 云端
    head, rows = ci_status()
    if not rows:
        print(f"L3/L4 云端：HEAD {head} 没有对应 run（可能还没触发）")
    else:
        for name, status, concl in rows:
            layer = "L4" if name.startswith("Verify") else ("L3" if name.startswith("Screenshot") else "—")
            print(f"{layer} 云端 {name}: {status} {concl}")

    # L5 独立验收：最近 N 笔里验收报告数量
    n = ap.parse_args().commits
    recent = sh(["git", "log", f"-{n}", "--name-only", "--pretty=format:"])
    acc = len(re.findall(r"docs/验收-", recent))
    print(f"L5 独立验收：最近 {n} 笔提交里新增/改动验收报告 {acc} 次")

    # L6 规格对照：锚点卡覆盖率
    with_anchor = 0
    for f in docs:
        head_body = open(os.path.join(ROOT, "docs", f), encoding="utf-8", errors="ignore").read(200)
        if "锚点卡：" in head_body:
            with_anchor += 1
    print(f"L6 规格对照：报告首行锚点卡覆盖 {with_anchor}/{len(docs)}")

    print("L7 真机：无设备，长期挂账（每份报告必须写进未验证项）")
    print("结论：下层红则上层不计；当前以 L0~L4 机检结果为准，L5/L6 看报告覆盖率。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
