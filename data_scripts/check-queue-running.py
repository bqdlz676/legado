#!/usr/bin/env python3
"""门禁 ㉚ 队首推进守卫（F-104；O1 后改用 queue_stats 单源计数）。

为什么：㉙ 只能发现"HEAD 长时间不动"。另一种形态是 HEAD 在动、队列还有可派 PENDING 而 RUNNING=0，
即"调度层在跑但没派单"的并行空转。判据：PENDING > 0 且 RUNNING == 0 → FAIL。
（旧版用行内子串匹配 'PENDING'/'RUNNING'/'在飞'，会把备注文字当状态，见 C1；已废弃。）
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from queue_stats import stats  # noqa: E402


def main(repo: str) -> int:
    st = stats(str(Path(repo) / 'docs' / '派单队列.md'))
    if st['ROWS'] == 0:
        print('QUEUE-RUNNING: PASS（无队列文件或无有效行）')
        return 0
    if st['PENDING'] > 0 and st['RUNNING'] == 0:
        print(f"QUEUE-RUNNING: FAIL（PENDING {st['PENDING']} 条但 RUNNING 为 0：调度层没派单）")
        return 1
    print(f"QUEUE-RUNNING: PASS（PENDING {st['PENDING']} / RUNNING {st['RUNNING']} / DONE {st['DONE']}）")
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
