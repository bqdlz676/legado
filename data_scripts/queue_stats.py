#!/usr/bin/env python3
"""队列计数唯一事实源（O1，修 C1「计数三源打架」）。

为什么：⑨/⑥/⑱/㉙/㉚ 曾各用一套解析——台账抓「在飞」行得 4，⑱ 精确匹配状态列得 0/0，
㉚ 用行内子串匹配得 7/6，同一次门禁吐三个互斥数字。计数必须单源。
判据：按 `docs/派单队列.md` 表格的状态列**前缀**分类（PENDING/RUNNING/DONE/VOID），
      不再用行内子串（备注里出现「在飞」会被误计）。
用法：`python3 data_scripts/queue_stats.py` 打印 `ROWS=.. PENDING=.. RUNNING=.. DONE=.. VOID=..`
      或作为模块 `from queue_stats import stats`。
"""
import os
import sys

QUEUE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'docs', '派单队列.md')


def classify(status: str) -> str:
    s = status.strip().upper()
    if s.startswith('PENDING'):
        return 'PENDING'
    if s.startswith('RUNNING') or s.startswith('在飞'):
        return 'RUNNING'
    if s.startswith('DONE') or s.startswith('已完'):
        return 'DONE'
    if s.startswith('作废') or s.startswith('取消') or s.startswith('VOID'):
        return 'VOID'
    return 'OTHER'


def parse(path: str = QUEUE):
    if not os.path.isfile(path):
        return []
    rows = []
    for line in open(path, encoding='utf-8', errors='ignore').read().splitlines():
        if not line.startswith('|'):
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if len(cells) < 6:
            continue
        if cells[0] in ('序号', '') or set(cells[0]) <= set('-: '):
            continue
        rows.append({
            'id': cells[0], 'title': cells[1], 'created': cells[2],
            'status': cells[3], 'kind': classify(cells[3]), 'dep': cells[4], 'domain': cells[5],
        })
    return rows


def stats(path: str = QUEUE) -> dict:
    rows = parse(path)
    out = {'ROWS': len(rows), 'PENDING': 0, 'RUNNING': 0, 'DONE': 0, 'VOID': 0, 'OTHER': 0}
    for r in rows:
        out[r['kind']] = out.get(r['kind'], 0) + 1
    out['rows'] = rows
    return out


if __name__ == '__main__':
    st = stats()
    print(' '.join(f'{k}={v}' for k, v in st.items() if k != 'rows'))
