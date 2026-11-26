#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 57 空槽即红（2026-11-26 用户质问「怎么每次都要我说满槽位」）。

为什么需要它：槽位空着＝机器闲着，而队列里还有 PENDING；这种"等用户提醒才派单"的停顿
每次都靠人喊才修，必须机械判。判据只有一条：PENDING 存在时 RUNNING 必须等于硬上限 5。
另外补一条反向判据：RUNNING 超过 5 也要红（超编＝并发失控）。

口径来源：`docs/派单队列.md` 表格状态格。硬上限取环境变量 SLOT_CAP，默认 5。
豁免：队列清空（PENDING=0 且 RUNNING=0）时判 PASS 并打印 EMPTY 说明，不判假绿。

用法：python3 data_scripts/check-slot-full.py [--selftest]
退出码：0 PASS；1 FAIL；2 EMPTY（队列空，非同题）。
"""
import re
import sys
import tempfile
import os
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
QUEUE = ROOT / 'docs' / '派单队列.md'
CAP = int(os.environ.get('SLOT_CAP', '5'))
ROW = re.compile(r'^\|\s*(Q-\d+)\s*\|')


def parse(path: pathlib.Path) -> tuple[int, int, list[str]]:
    pend = run = 0
    rows = []
    for line in path.read_text(encoding='utf-8').splitlines():
        m = ROW.match(line)
        if not m:
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        status = cells[3] if len(cells) > 3 else ''
        if status.startswith('RUNNING'):
            run += 1
        elif status.startswith('PENDING'):
            pend += 1
            rows.append(m.group(1))
    return pend, run, rows


def judge(pend: int, run: int) -> tuple[bool, str]:
    if run > CAP:
        return False, f'RUNNING {run} 超硬上限 {CAP}（并发失控）'
    if pend > 0 and run < CAP:
        return False, f'空槽 {CAP - run} 个，队列仍有 {pend} 条 PENDING 没派'
    if pend == 0 and run == 0:
        return True, 'EMPTY：队列已清空（无 PENDING 无在飞），非空转'
    return True, f'满槽：RUNNING {run}/{CAP}，PENDING {pend}'


def main(argv: list[str]) -> int:
    if '--selftest' in argv:
        cases = [((1, 3), False), ((0, 5), True), ((2, 5), True), ((0, 6), False), ((0, 0), True)]
        ok = True
        for (p, r), want in cases:
            got, why = judge(p, r)
            if got != want:
                print(f'  SELFTEST FAIL pend={p} run={r}: expect {want} got {got} ({why})')
                ok = False
        print('SELFTEST: ' + ('PASS（5 组边界：空槽红/满槽绿/超编红/清空豁免）' if ok else 'FAIL'))
        return 0 if ok else 1
    if not QUEUE.is_file():
        print('SLOT-FULL: FAIL（队列文件不存在）')
        return 1
    p, r, names = parse(QUEUE)
    ok, why = judge(p, r)
    if not ok:
        print('SLOT-FULL: FAIL（' + why + '）')
        if names:
            print('  待派：' + '、'.join(names))
        return 1
    print('SLOT-FULL: PASS（' + why + '）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
