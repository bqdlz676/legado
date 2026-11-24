#!/usr/bin/env python3
"""门禁 ㉚ 队首推进守卫（F-104 落地）。

为什么需要：㉙ 只能发现"HEAD 长时间不动"。但事故里还有一种形态——HEAD 在动（别的单在提交），
而队列里仍有可派的 PENDING、RUNNING 却为 0：这就是"调度层在跑，但没派单"的并行空转。
判据：队列里 PENDING > 0 且 RUNNING == 0 → FAIL（必须至少一单在飞）；全 DONE/作废 → PASS。
"""
import re
import sys
from pathlib import Path


def main(repo: str) -> int:
    p = Path(repo) / 'docs' / '派单队列.md'
    if not p.exists():
        print('QUEUE-RUNNING: PASS（无队列文件）')
        return 0
    rows = [ln for ln in p.read_text(encoding='utf-8', errors='ignore').splitlines() if re.match(r'\| Q-', ln)]
    pending = len([1 for ln in rows if 'PENDING' in ln])
    running = len([1 for ln in rows if ('RUNNING' in ln or '在飞' in ln)])
    if pending > 0 and running == 0:
        print(f'QUEUE-RUNNING: FAIL（PENDING {pending} 条但 RUNNING 为 0：调度层没派单）')
        return 1
    print(f'QUEUE-RUNNING: PASS（PENDING {pending} / RUNNING {running}）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
