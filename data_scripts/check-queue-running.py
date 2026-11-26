#!/usr/bin/env python3
"""门禁 ㉚ 队首推进守卫（F-104；O1 后改用 queue_stats 单源计数）。

为什么：㉙ 只能发现"HEAD 长时间不动"。另一种形态是 HEAD 在动、队列还有可派 PENDING 而 RUNNING=0，
即"调度层在跑但没派单"的并行空转。判据：PENDING > 0 且 RUNNING == 0 → FAIL。
（旧版用行内子串匹配 'PENDING'/'RUNNING'/'在飞'，会把备注文字当状态，见 C1；已废弃。）
"""
import os
import shutil
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from queue_stats import stats  # noqa: E402


def _make_queue(tmp: str, rows: list[str]) -> None:
    """在临时目录造 docs/派单队列.md；列数按 queue_stats 单源口径 ≥6 列（少于 6 列会被 parse 丢弃）。"""
    docs = os.path.join(tmp, 'docs')
    os.makedirs(docs, exist_ok=True)
    head = ("| 序号 | 标题 | 创建时间 | 状态 | 依赖 | 域 |\n"
            "|---|---|---|---|---|---|\n")
    open(os.path.join(docs, '派单队列.md'), 'w', encoding='utf-8').write(
        head + '\n'.join(rows) + '\n')


def selftest() -> int:
    """双向自测：有 PENDING 且有 RUNNING → PASS；有 PENDING 而 RUNNING=0 → FAIL。
    直接调 main(repo)，计数复用 queue_stats 单源，不另写状态匹配。"""
    good = tempfile.mkdtemp(prefix='queue-running-selftest-')
    try:
        _make_queue(good, [
            "| Q1 | 待派单 | 2026-11-27 10:00 | PENDING | 无 | 验收 |",
            "| Q2 | 在飞单 | 2026-11-27 10:01 | RUNNING | 无 | 写码 |",
        ])
        rc_good = main(good)
        assert rc_good == 0, f"PENDING 1 / RUNNING 1 应 PASS，rc={rc_good}"
        print('[selftest] 好样本（PENDING + RUNNING 并存）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    bad = tempfile.mkdtemp(prefix='queue-running-selftest-')
    try:
        # 坏样本：有可派 PENDING 却 0 在飞，正是本门禁要抓的「跑着但没派单」空转形态
        _make_queue(bad, [
            "| Q3 | 待派单 | 2026-11-27 10:02 | PENDING | 无 | 验收 |",
        ])
        rc_bad = main(bad)
        assert rc_bad == 1, f"PENDING 1 / RUNNING 0 应 FAIL，rc={rc_bad}"
        print('[selftest] 坏样本（PENDING 而 RUNNING=0）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


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
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
