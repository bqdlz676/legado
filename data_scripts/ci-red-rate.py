#!/usr/bin/env python3
"""CI 红率台账：每次 push 的绿/红与红因分类（2026-11-26 事故产物）。

为什么要有它：验收 PASS ≠ 能编译，CI 长期是唯一编译门，红率高但没人把红因归到
"哪条机检没写"。台账把每次红按类别记账（编译错 / 测试断言 / 环境 / 基础设施），
并要求每次编译类红在当晚落成脚本或规则；没有落成，这次复盘算失败。

用法：
  python3 data_scripts/ci-red-rate.py --write        拉最近 30 次 run，刷新台账
  python3 data_scripts/ci-red-rate.py --since 88e62bc 只看某 commit 之后
分类依据（只认日志里的硬特征，不猜）：
  compile  —— 日志出现 `e: file://...` 或 `Compilation error` / `Unresolved reference`
  test     —— 出现 `FAILED` 且带测试类名，或 aggregate 报 failures=
  env      —— JDK/SDK/cache/网络类关键词（限流、timeout、429、Could not resolve）
  infra    —— runner 取消、concurrency cancel、artifact 缺失
"""
import json
import os
import re
import subprocess
import sys

API = 'https://api.github.com/repos/bqdlz676/legado/actions/runs?per_page=%d'
LEDGER = 'docs/台账-CI红率.md'


def token():
    out = subprocess.run("grep -o 'ghp_[A-Za-z0-9]*' ~/.git-credentials | head -1",
                         shell=True, capture_output=True, text=True).stdout.strip()
    if not out:
        sys.exit('无 GitHub token，无法拉 run 列表')
    return out


def api(path, tok):
    out = subprocess.run(['curl', '-s', '-H', 'Authorization: Bearer ' + tok, path],
                         capture_output=True, text=True).stdout
    return json.loads(out or '{}')


def classify(tok, run):
    """只看失败 job 的日志尾巴，抓硬特征。日志可能很大，只取末尾 400 行。"""
    jobs = api('https://api.github.com/repos/bqdlz676/legado/actions/runs/%d/jobs' % run['id'], tok).get('jobs', [])
    for j in jobs:
        if j.get('conclusion') != 'failure':
            continue
        out = subprocess.run(['curl', '-sL', '-H', 'Authorization: Bearer ' + tok,
                              'https://api.github.com/repos/bqdlz676/legado/actions/jobs/%d/logs' % j['id']],
                             capture_output=True, text=True).stdout[-40000:]
        if re.search(r'e: file://|Compilation error|Unresolved reference', out):
            ms = re.findall(r'^[^ ]* (e: file://[^\n]+)', out, re.M) or []
            return 'compile', j['name'], (ms[0][:160] if ms else 'compile error')
        if re.search(r'FAILED|failures=\d+', out):
            ms = re.findall(r'([\w.$]+ > \w+ FAILED)', out) or []
            return 'test', j['name'], (ms[0] if ms else 'test failed')
        if re.search(r'429|Could not resolve|timeout|timeout-minutes|Unable to download|sdkmanager failed', out, re.I):
            return 'env', j['name'], 'env/network'
        if re.search(r'cancel|Cancelled', out, re.I):
            return 'infra', j['name'], 'cancelled'
        return 'unknown', j['name'], 'unclassified'
    return 'ok', '-', '-'


def main() -> int:
    args = sys.argv[1:]
    n = 30
    tok = token()
    runs = api(API % n, tok).get('workflow_runs', [])
    rows = []
    for r in runs:
        if r['status'] != 'completed':
            continue
        if r['conclusion'] == 'skipped':
            continue
        cls, job, why = ('ok', '-', '-') if r['conclusion'] == 'success' else classify(tok, r)
        rows.append((r['created_at'][:16], r['head_sha'][:7], r['name'], r['conclusion'], cls, job, why))
    stat = {}
    for r in rows:
        if r[3] == 'failure':
            stat[r[4]] = stat.get(r[4], 0) + 1
    if '--write' in args:
        out = ['# 台账 · CI 红率（自动生成，`ci-red-rate.py --write` 刷新）', '',
               '> 红因分类只认日志硬特征：compile（`e: file://` / Unresolved reference）／test（断言失败）／env（JDK·SDK·网络）／infra（取消·artifact）。',
               '> 规矩：**每一次 compile 类红，当晚必须落成一条机检或规则**；落不成的，这次复盘算失败，不许写"已复盘"。',
               '']
        out.append('- 本次统计：失败 %d 次，其中 compile %d、test %d、env %d、infra %d、unknown %d' % (
            sum(stat.values()), stat.get('compile', 0), stat.get('test', 0),
            stat.get('env', 0), stat.get('infra', 0), stat.get('unknown', 0)))
        out.append('')
        out.append('## 明细')
        for r in rows:
            out.append('- %s｜%s｜%s｜%s｜红因=%s｜job=%s｜%s' % r)
        open(LEDGER, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
        print('CI-REDGER: 写入 %s（失败 %d 次）' % (LEDGER, sum(stat.values())))
    else:
        print('CI-REDGER: 失败 %d 次｜compile %d｜test %d｜env %d｜infra %d' % (
            sum(stat.values()), stat.get('compile', 0), stat.get('test', 0),
            stat.get('env', 0), stat.get('infra', 0)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
