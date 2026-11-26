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
import shutil
import subprocess
import sys
import tempfile
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


def _init_fixture_repo(tmp: str, age_minutes: float) -> None:
    """在临时目录造一个 git 仓库：队列挂一条 PENDING，HEAD 提交时间按 age_minutes 回填。
    为什么必须造 git：本门禁判据锚是 HEAD commit 的 %ct，裸目录取不到提交时间，无法复现「沉睡超时」通路。"""
    docs = os.path.join(tmp, 'docs')
    os.makedirs(docs, exist_ok=True)
    queue = ("| 序号 | 标题 | 创建时间 | 状态 | 依赖 | 域 |\n"
             "|---|---|---|---|---|---|\n"
             "| Q1 | 自测单 | 2026-11-27 10:00 | PENDING | 无 | 验收 |\n")
    open(os.path.join(docs, '派单队列.md'), 'w', encoding='utf-8').write(queue)
    env = dict(os.environ)
    ts = int(time.time() - age_minutes * 60)
    env['GIT_AUTHOR_DATE'] = str(ts)
    env['GIT_COMMITTER_DATE'] = str(ts)  # %ct 取 committer date，两头都回填才能压出旧 HEAD
    subprocess.run(['git', '-C', tmp, 'init', '-q'], check=True)
    subprocess.run(['git', '-C', tmp, '-c', 'user.name=selftest',
                    '-c', 'user.email=selftest@example.com', 'add', '.'], check=True)
    subprocess.run(['git', '-C', tmp, '-c', 'user.name=selftest',
                     '-c', 'user.email=selftest@example.com', 'commit', '-q',
                     '-m', 'selftest fixture'], check=True, env=env)


def selftest() -> int:
    """双向自测：新鲜 HEAD + 有积压 → PASS；HEAD 沉睡 240 分钟 + 有积压 → FAIL。
    为什么直接调 main(repo)：夹具只换仓库目录，判定函数（head_age_minutes / queue_counts / main）全程复用生产口径，
    不另写一套比对，避免夹具与判据漂移。"""
    good = tempfile.mkdtemp(prefix='no-idle-selftest-')
    try:
        _init_fixture_repo(good, 0)  # 新鲜提交：年龄 0 ≤ 120 分钟，应放行
        rc_good = main(good)
        assert rc_good == 0, f"新鲜 HEAD + PENDING 应 PASS，rc={rc_good}"
        print('[selftest] 好样本（新鲜 HEAD + PENDING）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    bad = tempfile.mkdtemp(prefix='no-idle-selftest-')
    try:
        _init_fixture_repo(bad, 240)  # 沉睡 240 分钟 > 默认 120 分钟窗口，且有 PENDING，应判停工
        rc_bad = main(bad)
        assert rc_bad == 1, f"HEAD 沉睡 240 分钟 + PENDING 应 FAIL，rc={rc_bad}"
        print('[selftest] 坏样本（HEAD 沉睡 240 分钟 + PENDING）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
