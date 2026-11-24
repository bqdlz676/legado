#!/usr/bin/env python3
"""门禁 ⑯ 台账新鲜度守卫（2026-11-24 并发机制空转事故复盘产物）。

背景：规则停在"文档 + 脚本"层，没有接进"派单动作"链路——create_agent 之后
没人登记台账，门禁又只读台账，于是 ⑥/⑨ 每次必过，并发约束被架空。
本脚本把"派单即登记"变成机械判据：

  FAIL 条件（退出码 1）：
    ① HEAD 提交时间比台账 mtime 新 > 60 分钟 → 有提交/派单但台账未登记；
    ② 台账 `在飞` 行数 > 5 → 超并发硬上限（硬顶 8 档也只允许 ≤8，这里按
       满编 5 基线判，超限先红，档位放宽由 ⑨ 读 `docs/并发档位.md` 复核）。
  WARN 条件（退出码 0，仅提示）：
    ③ 台账里没有任何今年今日行 → 当日尚未派单/登记，提示核对。

为什么新鲜度用 60 分钟：派单与台账回写必须在同一轮内完成；超过 1 小时
仍未登记，基本可判定登记动作被当成"事后动作"漏掉了（事故当晚台账冻结
8h45m，20+ 单零登记）。
"""
import datetime
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEDGER = os.path.join(ROOT, "docs", "派单台账.md")
FRESHNESS_MINUTES = 60
HARD_CAP = 5  # 基线满编 5；档位放宽由 ⑨ 读并发档位文件复核，此处只兜底硬上限


def head_commit_ts() -> float:
    try:
        out = subprocess.run(
            ["git", "log", "-1", "--format=%ct"],
            cwd=ROOT, capture_output=True, text=True, timeout=10,
        )
        return float(out.stdout.strip() or 0)
    except Exception:
        return 0.0


def main() -> int:
    fails: list[str] = []
    warns: list[str] = []

    if not os.path.isfile(LEDGER):
        print(f"FAIL 缺台账 docs/派单台账.md")
        return 1

    text = open(LEDGER, encoding="utf-8").read()

    # ① 新鲜度：HEAD 比台账新 >60 分钟 → FAIL
    ledger_mtime = os.path.getmtime(LEDGER)
    head_ts = head_commit_ts()
    if head_ts > 0:
        gap_min = (head_ts - ledger_mtime) / 60.0
        if gap_min > FRESHNESS_MINUTES:
            fails.append(
                f"HEAD 比台账新 {gap_min:.0f} 分钟（> {FRESHNESS_MINUTES}），有提交/派单未登记台账"
            )
        else:
            print(f"ok    台账新鲜度：台账 mtime 距 HEAD {gap_min:.0f} 分钟内")

    # ② 在飞行数超硬上限 → FAIL
    open_count = sum(
        1 for line in text.splitlines() if re.match(r"^\|.*\|\s*在飞", line)
    )
    if open_count > HARD_CAP:
        fails.append(f"在飞 {open_count} 条，超硬上限 {HARD_CAP}")
    else:
        print(f"ok    在飞计数：{open_count} 条（硬上限 {HARD_CAP}）")

    # ③ 当日行缺失 → WARN 不 FAIL
    today = datetime.date.today()
    today_tokens = (today.strftime("%Y-%m-%d"), today.strftime("%m-%d"))
    if not any(tok in text for tok in today_tokens):
        warns.append(f"台账无 {today_tokens[0]} / {today_tokens[1]} 当日行（今日尚未登记派单）")

    for w in warns:
        print(f"WARN  {w}")
    for f in fails:
        print(f"FAIL  {f}")

    if fails:
        return 1
    print("PASS  台账新鲜度与在飞计数合格")
    return 0


if __name__ == "__main__":
    sys.exit(main())
