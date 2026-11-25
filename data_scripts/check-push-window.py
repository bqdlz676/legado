#!/usr/bin/env python3
"""推送窗口守卫（O3，K-20 机制化）：CI 有 run 在飞时不许 push。

为什么：本棒 3 分钟内连推 3 次，把前一次 push 的 run 全打成 cancelled，等于每次 push 都在
烧掉上一批验证（K-20）。规则写进文档没用，必须做成 push 前的机械闸。
判据：GitHub API 查最近 6 个 run，若存在 status=in_progress/queued → 退出码 1（拒绝 push）。
边界：无 token 或无网络 → 退出码 0 但打印 WARN（离线不能替代"不许推"的判断，由人确认）。
用法：`python3 data_scripts/check-push-window.py && git push`，或直接用 `bash data_scripts/pre-push.sh`。
"""
import json
import os
import re
import subprocess
import sys
import urllib.request

REPO = 'bqdlz676/legado'
CRED = os.path.expanduser('~/.git-credentials')


def token() -> str:
    try:
        txt = open(CRED, encoding='utf-8', errors='ignore').read()
        m = re.search(r'ghp_[A-Za-z0-9]+', txt)
        return m.group(0) if m else ''
    except OSError:
        return ''


def main() -> int:
    tok = token()
    if not tok:
        print('PUSH-WINDOW: WARN（无 token，无法判在飞 run；push 前须人工确认）')
        return 0
    req = urllib.request.Request(
        f'https://api.github.com/repos/{REPO}/actions/runs?per_page=6',
        headers={'Authorization': f'token {tok}', 'Accept': 'application/vnd.github+json'},
    )
    try:
        data = json.load(urllib.request.urlopen(req, timeout=20))
    except Exception as e:  # noqa: BLE001
        print(f'PUSH-WINDOW: WARN（API 不可达：{e}）')
        return 0
    busy = [(r['id'], r['head_sha'][:7], r['name'][:18]) for r in data.get('workflow_runs', [])
            if r.get('status') in ('in_progress', 'queued', 'waiting')]
    if busy:
        print('PUSH-WINDOW: FAIL（有 run 在飞，禁 push；等终态再推，否则把这批验证打掉）')
        for b in busy:
            print(f'  在飞 {b[0]} {b[1]} {b[2]}')
        return 1
    print('PUSH-WINDOW: PASS（无在飞 run，可以 push）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
