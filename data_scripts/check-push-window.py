#!/usr/bin/env python3
"""推送窗口提示（O3，K-20 机制化；F-111 降为提示型）：只报在飞 run 与建议，退出码恒 0。

为什么：K-20 实证 CI 中 push 会把在飞 run 打成 cancelled；但硬阻断会顶掉"保槽"铁律、
拖慢调度，故本脚本不再 FAIL——节拍攒批由 pre-push.sh 的 15 分钟时间戳守卫，这里只展示事实。
判据：GitHub API 查最近 6 个 run，列出 status=in_progress/queued/waiting 的在飞项与建议。
边界：无 token / 无网络 → WARN 提示人工确认，仍退出 0。
用法：`python3 data_scripts/check-push-window.py`（门禁 ㉟ 以 say 展示，不 bad）。
"""
import json
import os
import re
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
        print('PUSH-WINDOW: WARN（无 token，无法判在飞 run；推前人工确认）')
        return 0
    req = urllib.request.Request(
        f'https://api.github.com/repos/{REPO}/actions/runs?per_page=6',
        headers={'Authorization': f'token {tok}', 'Accept': 'application/vnd.github+json'},
    )
    try:
        data = json.load(urllib.request.urlopen(req, timeout=20))
    except Exception as e:  # noqa: BLE001
        print(f'PUSH-WINDOW: WARN（API 不可达：{e}；推前人工确认）')
        return 0
    busy = [(r['id'], r['head_sha'][:7], r['name'][:18]) for r in data.get('workflow_runs', [])
            if r.get('status') in ('in_progress', 'queued', 'waiting')]
    if busy:
        print(f'PUSH-WINDOW: WARN（{len(busy)} 个 run 在飞；建议等终态更稳，节拍窗口外仍可推）')
        for b in busy:
            print(f'  在飞 {b[0]} {b[1]} {b[2]}')
        return 0
    print('PUSH-WINDOW: OK（无在飞 run）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
