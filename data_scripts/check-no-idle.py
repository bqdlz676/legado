#!/usr/bin/env python3
"""门禁 ㉙ 停工守卫（F-103）。

为什么需要：用户明确要求"没有我的指令，中途不允许停工"。但"有没有在干活"不能靠口头声明，
只能靠外部可见事实——HEAD commit 的时间戳。若 HEAD 长时间不动、而队列里仍躺着 PENDING/RUNNING，
就说明调度层在用"汇报"结束回合（或空转），判 FAIL。

判据：now - HEAD commit time > IDLE_ALLOW_MINUTES(默认 120) 且队列里 PENDING+RUNNING > 0 → FAIL。
为什么是 120 分钟：一轮 CI 约 15 分钟，正常等 CI 不会被误判；而"整晚没动"必然命中。
"""
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
    p = Path(repo) / 'docs' / '派单队列.md'
    if not p.exists():
        return 0, 0
    text = p.read_text(encoding='utf-8', errors='ignore')
    pending = len(re.findall(r'\| PENDING \|', text)) + len([1 for ln in text.splitlines() if re.match(r'\| Q-', ln) and '| PENDING |' in ln])
    running = len([1 for ln in text.splitlines() if re.match(r'\| Q-', ln) and ('RUNNING' in ln or '在飞' in ln)])
    # 同一行可能被两种模式重复计数，取去重后的行计数为准
    rows = [ln for ln in text.splitlines() if re.match(r'\| Q-', ln)]
    pending = len([1 for ln in rows if 'PENDING' in ln])
    running = len([1 for ln in rows if ('RUNNING' in ln or '在飞' in ln)])
    return pending, running


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
