#!/usr/bin/env python3
"""CI 轮询脚本（推完云端用它看绿红，替代调度层阻塞等待）。

为什么：调度层禁止 wait/sleep 空转（机制-总控必读 §17「禁止 wait_agent 空等」「主控不许休眠，必须轮询」）。
把轮询交给脚本在后台跑，主控只读它落盘的日志，既有外部事实、又不阻塞。
用法：
  python3 data_scripts/watch-ci.py            # 查当前 HEAD 最新 run 一次
  python3 data_scripts/watch-ci.py --sha <sha>
  python3 data_scripts/watch-ci.py --loop 30 --max 40   # 每 30s 查一次，最多 40 次（后台跑）
退出码：0 全绿 / 1 有红 / 2 仍在跑 / 3 无 run / 4 凭据缺失
凭据：只读 ~/.git-credentials 里的 github 行，绝不打印 token。
"""
import argparse
import json
import os
import pathlib
import re
import subprocess
import sys
import time

REPO = 'bqdlz676/legado'


def token():
    c = pathlib.Path.home() / '.git-credentials'
    if not c.exists():
        return None
    for line in c.read_text().splitlines():
        m = re.match(r'https://[^:@]+:([^@]+)@github\.com', line)
        if m:
            return m.group(1)
    return None


def head_sha(repo_dir='.'):
    return subprocess.run(['git', '-C', repo_dir, 'rev-parse', 'HEAD'],
                          capture_output=True, text=True).stdout.strip()


def runs(tok, sha, per_page=20):
    url = ('https://api.github.com/repos/%s/actions/runs?per_page=%d' % (REPO, per_page))
    r = subprocess.run(['curl', '-s', '-H', 'Authorization: token ' + tok, url],
                       capture_output=True, text=True)
    try:
        data = json.loads(r.stdout)
    except json.JSONDecodeError:
        return []
    out = []
    for w in data.get('workflow_runs', []):
        if w.get('head_sha') == sha:
            out.append({
                'name': w.get('name'), 'id': w.get('id'),
                'status': w.get('status'), 'conclusion': w.get('conclusion'),
                'url': w.get('html_url'), 'created': w.get('created_at'),
            })
    return out


def summarize(rs):
    if not rs:
        print('CI: 无 run（该 HEAD 还没触发工作流）')
        return 3
    done = [r for r in rs if r['status'] == 'completed']
    bad = [r for r in done if r['conclusion'] not in ('success', 'neutral', 'skipped')]
    for r in rs:
        print('  · %-22s %-10s %-12s %s' % (r['name'], r['status'], r['conclusion'] or '-', r['url']))
    if len(done) < len(rs):
        print('CI: 进行中（%d/%d 完成）' % (len(done), len(rs)))
        return 2
    if bad:
        print('CI: FAIL（%d 个 run 非绿）' % len(bad))
        return 1
    print('CI: PASS（%d 个 run 全绿）' % len(rs))
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--sha', default=None)
    ap.add_argument('--loop', type=int, default=0)
    ap.add_argument('--max', type=int, default=1)
    ap.add_argument('--log', default='/tmp/watch-ci.log')
    a = ap.parse_args()
    tok = token()
    if not tok:
        print('CI: 凭据缺失（~/.git-credentials 无 github 行）')
        return 4
    sha = a.sha or head_sha()
    print('WATCH-CI sha=%s repo=%s' % (sha[:12], REPO))
    rc = 2
    for i in range(max(1, a.max)):
        rs = runs(tok, sha)
        with open(a.log, 'a', encoding='utf-8') as fh:
            fh.write('[%s] %s\n' % (time.strftime('%H:%M:%S'), json.dumps(rs, ensure_ascii=False)))
        rc = summarize(rs)
        if rc in (0, 1, 3):
            break
        if a.loop and i + 1 < a.max:
            time.sleep(a.loop)
    return rc


if __name__ == '__main__':
    sys.exit(main())
