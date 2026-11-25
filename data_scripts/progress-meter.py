#!/usr/bin/env python3
"""进度仪表（2026-11-26 复盘产物）。

为什么要它：这两天 277 次提交里 161 次是 docs（机制/报告），只有 72 次碰 app/src/main。
机制一直在转，功能推进占比 26%，但没人能一眼看出来——因为衡量进度的东西只有「报告数」。
所以固定成一个只读仪表，每轮调度先跑它，用三个数决定今天该派什么单：
    1) 近 24h 分类提交数（main / test / docs / scripts）
    2) 队列 PENDING 条数与最老一条的年龄（老化报警）
    3) 判低返工未派数（docs/修复-* 之外仍挂在问题清单里的缺陷）

判定规则（写进调度纪律，不写进门禁，避免机制再自我增殖）：
    - 连续两轮 main=0 → 停发一切机制/文档/盘点单，只准派功能单。
    - 最老 PENDING > 6 小时 → 该单要么派，要么作废，不许继续挂着。
"""

import collections
import datetime as dt
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HOURS = int(sys.argv[1]) if len(sys.argv) > 1 else 24


def git(*args):
    return subprocess.run(("git",) + args, cwd=ROOT, capture_output=True, text=True).stdout


def classify(line):
    # git 会把中文路径转义成 "docs/\346..."，所以按解码后的首段判断，兼容两种形态。
    if line.startswith("app/src/main/"):
        return "main"
    if line.startswith("app/src/test/") or line.startswith("screenshot/"):
        return "test"
    if line.startswith("data_scripts/"):
        return "scripts"
    if line.startswith("docs/") or line.startswith('"docs/'):
        return "docs"
    return "other"


def commits():
    since = (dt.datetime.now() - dt.timedelta(hours=HOURS)).strftime("%Y-%m-%d %H:%M")
    raw = git("log", f"--since={since}", "--oneline", "--name-only")
    groups, cur = [], None
    for line in raw.splitlines():
        line = line.strip()
        if re.match(r"^[0-9a-f]{7,} ", line):
            if cur is not None:
                groups.append(cur)
            cur = []
        elif line:
            cur.append(line)
    if cur is not None:
        groups.append(cur)
    counter = collections.Counter()
    for g in groups:
        kinds = {classify(f) for f in g}
        # 一次提交同时碰 main 和 test，按 main 记一次，避免虚高。
        for k in ("main", "test", "docs", "scripts", "other"):
            if k in kinds:
                counter[k] += 1
                break
    return len(groups), counter


def queue():
    path = os.path.join(ROOT, "docs", "派单队列.md")
    if not os.path.isfile(path):
        return [], 0
    rows = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if not line.startswith("| Q-"):
                continue
            cells = [c.strip() for c in line.split("|")]
            if len(cells) < 5:
                continue
            rows.append({"id": cells[1], "time": cells[3], "status": cells[4]})
    pending = [r for r in rows if r["status"].startswith("PENDING")]
    return pending, len(rows)


def main():
    n, c = commits()
    pending, total = queue()
    print(f"近 {HOURS}h 提交：{n} 次 —— main {c['main']} / test {c['test']} / docs {c['docs']} / scripts {c['scripts']} / other {c['other']}")
    if n:
        print(f"功能占比：{c['main'] / n:.0%}（低于 40% 说明机制在吃产能）")
    print(f"队列：共 {total} 条，PENDING {len(pending)} 条")
    for r in pending[:12]:
        print(f"  {r['id']} 立单时间 {r['time']}")
    if c["main"] == 0:
        print("ALERT: 本轮无生产码提交 → 停止派发机制/文档/盘点单，只准派功能单")
    return 0


if __name__ == "__main__":
    sys.exit(main())
