#!/usr/bin/env python3
"""门禁 ㉙ 停工守卫（F-103）。

为什么需要：用户明确要求"没有我的指令，中途不允许停工"。但"有没有在干活"不能靠口头声明，
只能靠外部可见事实——HEAD commit 的时间戳。若 HEAD 长时间不动、而队列里仍躺着 PENDING/RUNNING，
就说明调度层在用"汇报"结束回合（或空转），判 FAIL。

判据：now - HEAD commit time > IDLE_ALLOW_MINUTES(默认 120) 且队列里 PENDING+RUNNING > 0 → FAIL。
为什么是 120 分钟：一轮 CI 约 15 分钟，正常等 CI 不会被误判；而"整晚没动"必然命中。
"""
import sys as _sys
from pathlib import Path as _Path
_sys.path.insert(0, str(_Path(__file__).parent))
from queue_stats import stats as _qstats  # O1：计数单源
import os
import re
import subprocess
import sys
import time
from pathlib import Path


def head_age_minutes(repo: str) -> float:
    ts = subprocess.run(['git', '-C', repo, 'log', '-1', '--format=%ct'],
                        capture_output=True, text=True, check=True).stdout.strip()
    return (time.time() - int(ts)) / 60.0


def queue_counts(repo: str):
    # O1：改用 queue_stats 单源（旧版行内子串匹配会把备注文字当状态，得 7/6，与 ⑱ 的 6/1 互斥）
    import sys
    sys.path.insert(0, str(Path(__file__).parent))
    from queue_stats import stats
    st = stats(str(Path(repo) / 'docs' / '派单队列.md'))
    return st['PENDING'], st['RUNNING']


def main(repo: str) -> int:
    allow = float(os.environ.get('IDLE_ALLOW_MINUTES', '120'))
    age = head_age_minutes(repo)
    pending, running = queue_counts(repo)
    if age > allow and (pending + running) > 0:
        print(f'NO-IDLE: FAIL（疑似停工：HEAD 已 {age:.0f} 分钟未动，'
              f'而队列仍有 PENDING {pending} / RUNNING {running}）')
        return 1
    print(f'NO-IDLE: PASS（HEAD 距今 {age:.0f} 分钟 ≤ {allow:.0f}'
          f'，或队列已清空；PENDING {pending} / RUNNING {running}）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
