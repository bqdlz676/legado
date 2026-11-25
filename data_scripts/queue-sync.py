#!/usr/bin/env python3
"""队列状态自动派生（2026-11-26 机制空转诊断的 F1 落地）。

问题：队列/台账的状态格靠人（我）手写，而事实早就变了——实测有 3 条 Q 单
（Q-124/Q-130/Q-134）报告与代码都完成，状态仍写 PENDING。手写状态必然漂移，
而每次改格子又要提交一次 docs，机制就这样把自己变成了主业。

做法：状态只在两种场合自动改，其余一律只报不改，避免脚本瞎猜：
  1) RUNNING/PENDING 的行，其"交付物"列指向的 docs 文件已存在 → 改成
     `DONE（报告在盘，待确认结论）`。报告存在是硬事实，结论好坏仍由验收单说话，
     所以措辞保留"待确认"。
  2) RUNNING 且带 agent id、但对应报告文件不存在且该 agent 早已结束 → 只报警，
     不改（可能是代理挂了，需要我判断）。
  3) 老化的 PENDING（立单时间无法解析或超过 6 小时）→ 只报警。

用法：
  python3 data_scripts/queue-sync.py          # 只报告（默认，安全）
  python3 data_scripts/queue-sync.py --apply  # 真的改 派单队列.md
"""

import argparse
import datetime as dt
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QUEUE = os.path.join(ROOT, "docs", "派单队列.md")


def rows(lines):
    """返回 [(行号, cells)]，cells 是 '|' 切分后的列表，保持原样以便回写。"""
    out = []
    for i, line in enumerate(lines):
        if line.startswith("| Q-") and line.count("|") >= 6:
            out.append((i, line.split("|")))
    return out


def doc_path(cells):
    # 交付物列固定在第 7 格（索引 6）；里面用反引号包路径。
    if len(cells) < 7:
        return None
    m = re.findall(r"`([^`]+\.md)`", cells[6])
    return m[0] if m else None


def stale_hours(t):
    m = re.match(r"(\d{2})-(\d{2}) (\d{2}):(\d{2})", t.strip())
    if not m:
        return None
    now = dt.datetime.now()
    try:
        ts = dt.datetime(now.year, int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4)))
    except ValueError:
        return None
    return (now - ts).total_seconds() / 3600.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--age", type=float, default=6.0, help="PENDING 老化阈值（小时）")
    args = ap.parse_args()

    with open(QUEUE, encoding="utf-8") as fh:
        lines = fh.read().split("\n")

    changed, alerts = [], []
    for idx, cells in rows(lines):
        qid = cells[1].strip()
        status = cells[4].strip()
        path = doc_path(cells)
        exists = bool(path) and os.path.isfile(os.path.join(ROOT, path))
        hours = stale_hours(cells[3])

        if status.startswith(("PENDING", "RUNNING")) and exists:
            changed.append((idx, qid, status, path))
            if args.apply:
                cells[4] = " DONE（报告在盘，待确认结论） "
                lines[idx] = "|".join(cells)
        elif status.startswith("PENDING") and hours is not None and hours > args.age:
            alerts.append(f"{qid} 已挂 {hours:.0f} 小时（> {args.age:.0f}），派或作废，不许继续挂")
        elif status.startswith("RUNNING") and not exists:
            alerts.append(f"{qid} 标 RUNNING 但交付物 {path or '未登记'} 不在盘，核对代理是否已挂")

    for idx, qid, old, path in changed:
        print(f"{'已改' if args.apply else '可改'}: {qid} {old.split('（')[0]} -> DONE（报告在盘：{path}）")
    for a in alerts:
        print(f"ALERT: {a}")

    if not changed and not alerts:
        print("队列状态与事实一致，无需改动")
        return 0
    if changed and not args.apply:
        print(f"共 {len(changed)} 条可自动收口；加 --apply 落盘")
    if changed and args.apply:
        # 只在确有改动时回写，避免无意义地改动时间戳；写前保留末尾换行形态。
        with open(QUEUE, "w", encoding="utf-8") as fh:
            fh.write("\n".join(lines))
        print(f"已回写 {QUEUE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
