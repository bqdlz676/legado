#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 g71 · CI 红窗停发门（G-07 / E5 P0 红灯优先）。

为什么立这条（见 docs/台账-三件缺项.md G-07、docs/落文-规矩总纲.md E5）：
  E5 口径「CI 红或没包时，停发新功能派单，全队先修红；已在飞的允许收尾，不许再开
  新功能单」此前只写在总纲文档里，rule-gate.sh 没有任何脚本读 CI 结论、也不阻断
  红窗内新单派发，停发约束靠人工自觉。本闸把"最近一次 CI 是否处于红窗 + 红窗内
  是否新增了 RUNNING 派单"做成机判。

判据：
  1. 取最近 30 条 workflow run，按 updated_at 取终态最新一条；conclusion=failure
     即视为红窗开启（cancelled 不算红，口径同 g69）。
  2. 红窗开启时，解析 docs/派单队列.md 的 RUNNING 行：行内创建时间晚于失败 run
     时间戳 → FAIL（红窗内新增在飞单）。
  3. 绿窗（最新结论 success / neutral / skipped）→ PASS，不查队列。
  4. 网络 / 凭据不可用 → UNVERIFIED（退出码 3），不是 PASS：没查到 CI 状态不得
     解释为"可以派新功能单"（同 g69 三态口径）。

适用边界 / 误判面 / 不能替代什么：
  - 只读 Actions 结论与队列登记时间，不重跑编译；判不到"CI 红但队列里是修红单/
    独立验收单"这两类 E5 允许在红窗派出的单——本闸只点出红窗内新增 RUNNING，
    单型合法性（修红 / 验收 / 新功能）仍由调度层与人工复核。
  - 队列时间只有日期无时分的行，与失败时间同日时无法判先后，记 WARN 不判 FAIL；
    晚一日才判红，避免把同日收尾单误杀。
  - 本闸不替代 g69 CI 三同时（编译错/测试失败/ BUILD SUCCESSFUL 日志级核验）；
    两者一个管"能不能派新单"，一个管"转绿证据是否成立"。
  - 队列文件缺失 / 解析不出 RUNNING 行时按空集处理：红窗下无在飞单不算违规，
    绿窗下同样 PASS；不报假绿。

退出码：0=PASS | 1=FAIL | 2=参数错 | 3=UNVERIFIED（网络/凭据不可用）
用法：
  python3 data_scripts/check-ci-gate-dispatch.py
  python3 data_scripts/check-ci-gate-dispatch.py --selftest
"""
from __future__ import annotations

import argparse
import datetime as dt
import importlib.util
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
QUEUE = ROOT / 'docs' / '派单队列.md'

# 复用 g69 的 token 读取 / API 封装 / Unverified 异常，避免同一套 GitHub 凭据逻辑双写。
_here = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location('ci_green', _here / 'check-ci-green.py')
ci_green = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ci_green)

RUNNING_RE = re.compile(r'^RUNNING\b', re.I)
QID_RE = re.compile(r'Q-\d+')
AID_RE = re.compile(r's_[0-9A-Za-z]+')
# 时间形态：2026-11-28 04:35 / 2026-11-28 04:35:22 / 2026-11-28（仅日期）
DT_RE = re.compile(r'(\d{4}-\d{2}-\d{2})(?:\s+(\d{2}:\d{2})(?::\d{2})?)?')


def fetch_runs() -> list[dict]:
    """取最近 30 条 workflow run；网络/凭据问题抛 ci_green.Unverified。"""
    code, body = ci_green.api_get('/actions/runs?per_page=30')
    if code != 200:
        raise ci_green.Unverified('runs 列表查询失败 HTTP %s' % code)
    return json.loads(body)['workflow_runs']


def parse_running_rows(text: str) -> list[dict]:
    """解析队列 RUNNING 行 → [{qid, aid, raw, dt(None=解析不出), date_only}]。"""
    rows: list[dict] = []
    for line in text.splitlines():
        if not line.strip().startswith('|'):
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        status = next((c for c in cells if RUNNING_RE.match(c)), '')
        if not status:
            continue
        qm = QID_RE.search(line)
        am = AID_RE.search(line)
        tm = DT_RE.search(line)
        dt_obj = None
        date_only = True
        if tm:
            date_only = not bool(tm.group(2))
            try:
                if tm.group(2):
                    dt_obj = dt.datetime.strptime(
                        tm.group(1) + ' ' + tm.group(2), '%Y-%m-%d %H:%M')
                else:
                    dt_obj = dt.datetime.strptime(tm.group(1), '%Y-%m-%d')
            except ValueError:
                dt_obj = None
        rows.append({
            'qid': qm.group(0) if qm else '?',
            'aid': am.group(0) if am else '',
            'raw': tm.group(0) if tm else '',
            'dt': dt_obj,
            'date_only': date_only,
        })
    return rows


def judge(runs: list[dict], rows: list[dict]) -> tuple[int, list[str], list[str], str]:
    """返回 (退出码, FAIL 项, WARN 项, 结论说明)。"""
    if not runs:
        return 3, [], [], 'UNVERIFIED：Actions 无任何 run 记录'
    latest = max(runs, key=lambda r: r.get('updated_at') or '')
    concl = latest.get('conclusion')
    updated = latest.get('updated_at') or ''
    try:
        red_dt = dt.datetime.strptime(updated[:19], '%Y-%m-%dT%H:%M:%S')
    except ValueError:
        red_dt = None
    if concl != 'failure':
        return 0, [], [], '绿窗：最新 run %s conclusion=%s（%s），未拦新单' % (
            latest.get('id'), concl, updated)
    fails: list[str] = []
    warns: list[str] = []
    if red_dt is None:
        warns.append('失败 run %s 时间戳不可解析：%s' % (latest.get('id'), updated))
        return 1, fails, warns, '红窗：最新 run 为 failure（%s），时间戳缺失需人工核' % updated
    for row in rows:
        if row['dt'] is None:
            warns.append('%s RUNNING 行时间不可解析（%s），未参与先后判定' % (row['qid'], row['raw']))
            continue
        if row['dt'] > red_dt:
            fails.append('%s 红窗内新增 RUNNING：派单时间 %s 晚于失败 run 时间 %s（run %s）'
                         % (row['qid'], row['raw'], updated, latest.get('id')))
        elif row['date_only'] and row['dt'].date() == red_dt.date():
            warns.append('%s 仅日期 %s 与失败同日，先后无法机判，人工复核是否红窗派出'
                         % (row['qid'], row['raw']))
    head = '红窗：最新 run %s conclusion=failure（%s）' % (latest.get('id'), updated)
    return (1 if fails else 0), fails, warns, head


def selftest() -> int:
    """双向自测：红窗+晚派单 → FAIL；红窗+同日仅日期 → WARN 不 FAIL；绿窗 → PASS。"""
    runs_red = [{'id': 111, 'conclusion': 'failure', 'updated_at': '2026-11-28T09:00:00Z',
                 'head_sha': 'a' * 40, 'name': 'Verify'}]
    runs_green = [{'id': 222, 'conclusion': 'success', 'updated_at': '2026-11-28T11:00:00Z',
                   'head_sha': 'b' * 40, 'name': 'Verify'}]
    queue_bad = (
        '| 序号 | 派单标题 | 创建时间 | 状态 | 依赖 | 文件域/判据锚 |\n'
        '|---|---|---|---|---|---|\n'
        '| Q-490 | 新功能单 | 2026-11-28 10:30 | RUNNING（s_bad1，一次性写码） | 无 | x |\n'
    )
    queue_warn = (
        '| 序号 | 派单标题 | 创建时间 | 状态 | 依赖 | 文件域/判据锚 |\n'
        '|---|---|---|---|---|---|\n'
        '| Q-491 | 同日单 | 2026-11-28 | RUNNING（s_bad2，一次性写码） | 无 | x |\n'
    )
    # 坏夹具：红窗内晚派单必须 FAIL。
    code, fails, warns, _ = judge(runs_red, parse_running_rows(queue_bad))
    assert code == 1 and fails, f'红窗晚派单应 FAIL：{code} {fails}'
    # 边界夹具：同日仅日期不判 FAIL，只 WARN。
    code2, fails2, warns2, _ = judge(runs_red, parse_running_rows(queue_warn))
    assert code2 == 0 and not fails2 and warns2, f'同日仅日期应 WARN 不 FAIL：{code2} {fails2} {warns2}'
    # 好夹具：绿窗 PASS，队列有 RUNNING 也不拦。
    code3, fails3, _, head3 = judge(runs_green, parse_running_rows(queue_bad))
    assert code3 == 0 and not fails3, f'绿窗应 PASS：{code3} {fails3}'
    print('SELFTEST: PASS（红窗晚派单判红、同日仅日期 WARN 放行、绿窗 PASS）')
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description='CI 红窗停发门（G-07 / E5）')
    ap.add_argument('--selftest', action='store_true')
    ap.add_argument('--queue', help='测试用队列文件路径')
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    qpath = pathlib.Path(args.queue) if args.queue else QUEUE
    try:
        runs = fetch_runs()
    except ci_green.Unverified as exc:
        print('CI-DISPATCH: UNVERIFIED（%s）' % exc)
        return 3
    queue_text = qpath.read_text(encoding='utf-8', errors='replace') if qpath.is_file() else ''
    rows = parse_running_rows(queue_text)
    code, fails, warns, head = judge(runs, rows)
    print('== CI 红窗停发门（G-07）==')
    print('-- ' + head)
    for w in warns:
        print('   · WARN ' + w)
    for f in fails:
        print('   · FAIL ' + f)
    if code == 3:
        print('CI-DISPATCH: UNVERIFIED（未查到 CI 状态，不得视为可派新功能单）')
        return 3
    print('CI-DISPATCH: PASS' if code == 0 else 'CI-DISPATCH: FAIL')
    return code


if __name__ == '__main__':
    sys.exit(main())
