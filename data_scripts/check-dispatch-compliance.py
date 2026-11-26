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
# 出处：ZOMBIE 10 分钟＝`docs/机制-总控必读.md` :141 小节/F-94「90 秒存活校验 + 轨迹长期无更新即判废」的松弛线；
# TIMEOUT 15 分钟＝§2「每单写死 15 分钟熔断」。两者都是调度口径常量，改口径必须先改口径再改这里。
ZOMBIE_MINUTES = 10
TIMEOUT_MINUTES = 15
ROW_RE = re.compile(r'^\s*\|\s*(Q-\d+)\s*\|')
STATUS_WORDS = ('RUNNING', 'PENDING', 'DONE', 'BLOCKED')
TIME_RE = re.compile(r'(\d{1,2}):(\d{2})\s*起')


def git_status_paths():
    r = subprocess.run(['git', '-c', 'core.quotepath=false', 'status', '--porcelain', '-uall'],
                       cwd=str(ROOT), capture_output=True, text=True)
    out = []
    for line in r.stdout.splitlines():
        p = line[3:].strip().strip('"')
        if p:
            out.append(p)
    return out


def tracked(path):
    """是否 git 已跟踪。为什么：豁免只认"仓库里本来就有的夹具"，
    未跟踪/新增/改名塞进 fixtures 的文件一律不豁免——否则豁免目录就是后门（Q-208 D-1）。"""
    r = subprocess.run(['git', 'ls-files', '--error-unmatch', path],
                       cwd=str(ROOT), capture_output=True, text=True)
    return r.returncode == 0


def paths_ok(paths):
    bad = []
    for p in paths:
        if not POLLUTION_RE.search(os.path.basename(p)):
            continue
        if FIXTURE_OK in p and tracked(p):
            continue  # 已跟踪夹具豁免：自测夹具是仓库资产，不是残留样本
        bad.append(p + ('' if FIXTURE_OK not in p else '（豁免目录内未跟踪文件，疑似改名塞样本）'))
    return bad


def running_rows(queue_path):
    rows = []
    if not Path(queue_path).exists():
        return rows
    for line in Path(queue_path).read_text(encoding='utf-8', errors='ignore').splitlines():
        m = ROW_RE.match(line)
        if not m:
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        qid = m.group(1)
        # 为什么按"含状态词的那一格"定位：队列列数会随写作习惯漂移（4 列/6 列都出现过），
        # 写死第 4 列会让 RUNNING 行整体消失，B/C 判据变成永久盲区（Q-208 D-2）。
        # N-2：先认"整格就是状态词（可带括号备注）"，再退回子串；否则 DONE 行备注里出现
        # 「RUNNING」会被当成在飞（Q-210 N-2 实测）。状态词必须是格子开头的 token。
        status_cell = next((c for c in cells if re.match(r'^(%s)\b' % '|'.join(STATUS_WORDS), c.upper())), '')
        if not status_cell:
            status_cell = next((c for c in cells if any(w in c.upper() for w in STATUS_WORDS)), '')
        if not re.match(r'^RUNNING\b', status_cell.upper()):
            continue
        aid = re.search(r's_[0-9A-Za-z]+', ' '.join(cells))
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
    bad, no_stamp, alive = [], [], []
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
        elapsed = (now - start) / 60.0
        if elapsed <= TIMEOUT_MINUTES:
            continue
        # 熔断的真意是"没进展"，不是"时钟走过了 15 分钟"：代理正在推进时掐掉它等于自废产能。
        # 判据：超线且轨迹 5 分钟无更新 → FAIL；轨迹仍在更新 → INFO（点名已撑多久，留痕不判红）。
        f = trajectory(aid)
        stale = (now - os.path.getmtime(f)) / 60.0 if f else 1e9
        if stale > 5:
            bad.append((qid, aid, '%.0f 分钟未收口、轨迹 %.0f 分钟无更新（超 %d 分钟熔断线且已停摆）'
                        % (elapsed, stale, TIMEOUT_MINUTES)))
        else:
            alive.append((qid, aid, '%.0f 分钟仍在推进（轨迹 %.0f 分钟内有更新）' % (elapsed, stale)))
    return bad, no_stamp, alive


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
    # D-2：凡"含 Q 号又含 RUNNING"的行都必须被判据看见——哪怕它不是标准表格行。
    # 旧口径只认 `^|Q-` 开头，非管道行/前导空格行被静默吞掉，队列写歪就成永久盲区（Q-210 D-2）。
    all_lines = Path(queue_path).read_text(encoding='utf-8', errors='ignore').splitlines() \
        if Path(queue_path).exists() else []
    def _status_of(line):
        m = ROW_RE.match(line)
        if not m:
            return ''
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        c = next((x for x in cells if re.match(r'^(%s)\b' % '|'.join(STATUS_WORDS), x.upper())), '')
        return c.split('（')[0].strip().upper()

    # 只认"行首是 Q 号"的候选行；状态格已明确 DONE/BLOCKED 的不算在飞（备注里提到 RUNNING 不算）
    raw_running = [l for l in all_lines
                   if re.match(r'^\s*\|?\s*Q-\d+', l) and 'RUNNING' in l
                   and _status_of(l) not in ('DONE', 'BLOCKED')]
    unparsed = len(raw_running) - len(rows)
    malformed = [l[:80] for l in raw_running if not ROW_RE.match(l)]
    if unparsed > 0:
        failed += 1
        print('DISPATCH-B: FAIL 队列有 %d 行含 RUNNING 但无法解析（列数漂移/非表格行会让 B/C 变永久盲区，先修行再跑）'
              % unparsed)
        for l in malformed:
            print('  - 非标准行：%s' % l)
    zb = zombies(rows, agents_dir, now)
    if zb:
        failed += 1
        print('DISPATCH-B: FAIL RUNNING 行与真实在飞不一致：')
        for qid, aid, why in zb:
            print('  - %s %s：%s' % (qid, aid, why))
    else:
        print('DISPATCH-B: PASS（RUNNING %d 行与轨迹一致）' % len(rows))

    to, no_stamp, alive = timed_out(rows, now)
    if to:
        failed += 1
        print('DISPATCH-C: FAIL 超过 15 分钟熔断线未收口：')
        for qid, aid, why in to:
            print('  - %s %s：%s' % (qid, aid, why))
    else:
        msg = 'DISPATCH-C: PASS'
        for qid, aid, why in alive:
            msg += '（INFO %s %s）' % (qid, why)
        if no_stamp:
            msg += '（INFO：%s 未写起时，无法核算熔断，派单时请写 `RUNNING（<id>，<HH:MM> 起）`）' % '、'.join(no_stamp)
        print(msg)

    print('DISPATCH-COMPLIANCE: %s' % ('FAIL' if failed else 'PASS'))
    return 1 if failed else 0


def selftest() -> int:
    import shutil
    import tempfile
    tmp = Path(tempfile.mkdtemp(prefix='dispatch-selftest-'))
    try:
        return _selftest_body(tmp)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)  # D-5：夹具必须自删，否则 /tmp 无限堆积（Q-208 判上门）


def _selftest_body(tmp) -> int:
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
