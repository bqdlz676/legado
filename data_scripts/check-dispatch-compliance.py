#!/usr/bin/env python3
"""门禁 ㊹ 调度执行合规（A 测试残留 / B 僵尸 RUNNING / C 熔断超时）。

为什么需要：2026-11-26 第 4 棒实测三类"规则只写在纸上"的失效——
  A 复核代理被允许在仓库内造坏样本（docs/修复-坏样本-临时.md），把整轮门禁末行污染成 ⑭ FAIL；
  B 代理已收口而队列仍挂 RUNNING（Q-207），队列变成"假在飞"，㉙/㉚ 的计数全部失准；
  C 派单写了"15 分钟熔断"，实际三个代理跑了 1547 / 1213 / 843 秒，没有一处外部捕获。
判据（三条都只用外部可见事实，不读任何自评）：
  A：git status 出现 临时/坏样本/neg-/regress-/zzq 样式的文件（排除 data_scripts/fixtures/）→ FAIL，点名。
  B：队列 RUNNING 行的 agent 轨迹 10 分钟无更新（或轨迹不存在）→ FAIL，点名 Q 号与 agent id。
  C：RUNNING 行带起时（`RUNNING（<id>，<HH:MM> 起）`）且超过 15 分钟 → FAIL；没写起时 → INFO（不判红）。
用法：python3 data_scripts/check-dispatch-compliance.py [--selftest]
退出码：0 PASS / 1 FAIL；selftest 自建 /tmp 夹具，双向验证后退出。
"""
import argparse
import glob
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
QUEUE = ROOT / 'docs' / '派单队列.md'
POLLUTION_RE = re.compile(r'(临时|坏样本|neg-|regress-|zzq)', re.I)
FIXTURE_OK = 'data_scripts/fixtures/'
ZOMBIE_MINUTES = 10
TIMEOUT_MINUTES = 15
ROW_RE = re.compile(r'^\|\s*(Q-\d+)\s*\|(.*?)\|\s*(.*?)\s*\|\s*(.*?)\s*\|\s*(.*?)\s*\|\s*(.*?)\s*\|\s*$')
TIME_RE = re.compile(r'(\d{1,2}):(\d{2})\s*起')


def git_status_paths():
    r = subprocess.run(['git', '-c', 'core.quotepath=false', 'status', '--porcelain'],
                       cwd=str(ROOT), capture_output=True, text=True)
    out = []
    for line in r.stdout.splitlines():
        p = line[3:].strip().strip('"')
        if p:
            out.append(p)
    return out


def paths_ok(paths):
    bad = [p for p in paths
           if POLLUTION_RE.search(os.path.basename(p)) and FIXTURE_OK not in p]
    return bad


def running_rows(queue_path):
    rows = []
    if not Path(queue_path).exists():
        return rows
    for line in Path(queue_path).read_text(encoding='utf-8', errors='ignore').splitlines():
        m = ROW_RE.match(line)
        if not m:
            continue
        qid, status_cell = m.group(1), m.group(4)
        if 'RUNNING' not in status_cell:
            continue
        aid = re.search(r's_[0-9A-Za-z]+', status_cell) or re.search(r's_[0-9A-Za-z]+', m.group(3))
        rows.append((qid, aid.group(0) if aid else '', status_cell))
    return rows


def trajectory(agent_id, agents_dir=None):
    if not agent_id:
        return None
    if agents_dir:
        cand = sorted(glob.glob(os.path.join(agents_dir, agent_id, 'system', 'trajectory.jsonl')))
        return cand[-1] if cand else None
    pat = os.path.expanduser('~/.doubao/agent_mode/workspace/.sessions/*/agents/%s/system/trajectory.jsonl' % agent_id)
    cand = sorted(glob.glob(pat), key=os.path.getmtime)
    return cand[-1] if cand else None


def zombies(rows, agents_dir=None, now=None):
    now = now or time.time()
    bad = []
    for qid, aid, cell in rows:
        f = trajectory(aid, agents_dir)
        if not f:
            bad.append((qid, aid, '轨迹不存在（agent 未创建或已清理）'))
            continue
        age = (now - os.path.getmtime(f)) / 60.0
        if age > ZOMBIE_MINUTES:
            bad.append((qid, aid, '轨迹 %.0f 分钟无更新（疑似僵尸 RUNNING）' % age))
    return bad


def timed_out(rows, now=None):
    now = now or time.time()
    bad, no_stamp = [], []
    for qid, aid, cell in rows:
        m = TIME_RE.search(cell)
        if not m:
            no_stamp.append(qid)
            continue
        hh, mm = int(m.group(1)), int(m.group(2))
        lt = time.localtime(now)
        start = time.mktime((lt.tm_year, lt.tm_mon, lt.tm_mday, hh, mm, 0, 0, 0, -1))
        if start > now:  # 跨天（起时在未来即昨天）
            start -= 86400
        if (now - start) / 60.0 > TIMEOUT_MINUTES:
            bad.append((qid, aid, '%.0f 分钟未收口（超 %d 分钟熔断线）' % ((now - start) / 60.0, TIMEOUT_MINUTES)))
    return bad, no_stamp


def run(queue_path, agents_dir=None, now=None) -> int:
    failed = 0
    bad = paths_ok(git_status_paths())
    if bad:
        failed += 1
        print('DISPATCH-A: FAIL 工作区存在测试残留（负样本只许放 /tmp）：')
        for p in bad:
            print('  - %s' % p)
    else:
        print('DISPATCH-A: PASS 工作区无测试残留')

    rows = running_rows(queue_path)
    zb = zombies(rows, agents_dir, now)
    if zb:
        failed += 1
        print('DISPATCH-B: FAIL RUNNING 行与真实在飞不一致：')
        for qid, aid, why in zb:
            print('  - %s %s：%s' % (qid, aid, why))
    else:
        print('DISPATCH-B: PASS（RUNNING %d 行与轨迹一致）' % len(rows))

    to, no_stamp = timed_out(rows, now)
    if to:
        failed += 1
        print('DISPATCH-C: FAIL 超过 15 分钟熔断线未收口：')
        for qid, aid, why in to:
            print('  - %s %s：%s' % (qid, aid, why))
    else:
        msg = 'DISPATCH-C: PASS'
        if no_stamp:
            msg += '（INFO：%s 未写起时，无法核算熔断，派单时请写 `RUNNING（<id>，<HH:MM> 起）`）' % '、'.join(no_stamp)
        print(msg)

    print('DISPATCH-COMPLIANCE: %s' % ('FAIL' if failed else 'PASS'))
    return 1 if failed else 0


def selftest() -> int:
    import tempfile
    tmp = Path(tempfile.mkdtemp(prefix='dispatch-selftest-'))
    agents = tmp / 'agents'
    agents.mkdir(parents=True)
    good_q = tmp / 'q_good.md'
    good_q.write_text('| Q-900 | 样本 | s_good | RUNNING（s_good，%s 起） | 域 | 锚 |\n'
                      % time.strftime('%H:%M'), encoding='utf-8')
    (agents / 's_good' / 'system').mkdir(parents=True)
    (agents / 's_good' / 'system' / 'trajectory.jsonl').write_text('{"role":"user"}\n', encoding='utf-8')

    bad_q = tmp / 'q_bad.md'
    old = time.strftime('%H:%M', time.localtime(time.time() - 40 * 60))
    bad_q.write_text('| Q-901 | 样本 | s_dead | RUNNING（s_dead，%s 起） | 域 | 锚 |\n'
                     '| Q-902 | 样本 | s_ghost | RUNNING（s_ghost，%s 起） | 域 | 锚 |\n' % (old, old),
                     encoding='utf-8')
    (agents / 's_dead' / 'system').mkdir(parents=True)
    tj = agents / 's_dead' / 'system' / 'trajectory.jsonl'
    tj.write_text('{"role":"user"}\n', encoding='utf-8')
    os.utime(tj, (time.time() - 3600, time.time() - 3600))  # 1 小时前 → 僵尸

    rc_good = run(good_q, str(agents))
    rc_bad = run(bad_q, str(agents))
    ok = (rc_good == 0 and rc_bad == 1)
    print('SELFTEST: %s（合规样本 rc=%d 应为 0；坏样本 rc=%d 应为 1）'
          % ('PASS' if ok else 'FAIL', rc_good, rc_bad))
    return 0 if ok else 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--selftest', action='store_true')
    ap.add_argument('--queue', default=str(QUEUE))
    ap.add_argument('--agents-dir', default=None)
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    return run(a.queue, a.agents_dir)


if __name__ == '__main__':
    sys.exit(main())
