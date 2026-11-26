#!/usr/bin/env python3
"""门禁 52：判据必须被证伪过——每个 check-*.py 都要能 --selftest（M-48）。

为什么（2026-11-26 穷举审计）：全仓 43 个门禁里有 23 个没有 --selftest，等于判据从未被负样本
打红过；一个永远绿的检查器不是判据，是装饰。已存在的不追溯（历史欠账走棘轮基线），
但**新增门禁必须自带 --selftest**，且基线只许缩小。
判据：
  1. data_scripts/check-*.py 每个文件须出现 `--selftest`（或 `selftest` 函数定义）；
  2. 未满足者必须登记在 data_scripts/selftest-baseline.json 的 debt 里；
  3. 该基线只许缩小：HEAD 的 debt 必须是 origin/main 的子集；且 debt 里的文件若已有 selftest，
     必须从基线删除（基线不许滞留）。
用法：python3 data_scripts/check-selftest-coverage.py [--write-baseline]
"""
import json
import os
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASE = ROOT / 'data_scripts' / 'selftest-baseline.json'


def gates():
    return sorted(p for p in (ROOT / 'data_scripts').glob('check-*.py') if p.name != 'check-selftest-coverage.py')


def has_selftest(p: pathlib.Path) -> bool:
    t = p.read_text(encoding='utf-8', errors='replace')
    return '--selftest' in t or re.search(r'def selftest', t) is not None


def ref_set(ref):
    r = subprocess.run(['git', 'show', '%s:data_scripts/selftest-baseline.json' % ref],
                       cwd=str(ROOT), capture_output=True, text=True)
    if r.returncode != 0:
        return None
    return set(json.loads(r.stdout).get('debt', []))


def main():
    missing = [p.name for p in gates() if not has_selftest(p)]
    if '--write-baseline' in sys.argv:
        BASE.write_text(json.dumps({'note': '历史欠账棘轮：只许缩小，新增门禁必须自带 --selftest',
                                    'debt': missing}, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
        print('基线已写入 %d 条欠账' % len(missing))
        return 0
    debt = set(json.loads(BASE.read_text(encoding='utf-8')).get('debt', [])) if BASE.is_file() else set()
    # 基线不许滞留：已有 selftest 的必须从 debt 里删掉
    stale = [n for n in debt if n not in missing]
    new = [n for n in missing if n not in debt]
    grew = False
    for ref in ('origin/main', 'HEAD~1'):
        rs = ref_set(ref)
        if rs is not None:
            grew = bool(debt - rs)
            break
    if stale:
        print('SELFTEST-COVERAGE: FAIL（基线滞留 %d 条：已补 selftest 却仍挂在欠账里）' % len(stale))
        for n in stale[:5]:
            print('  - ' + n)
        return 1
    if new:
        print('SELFTEST-COVERAGE: FAIL（%d 个门禁既无 --selftest 也不在基线内）' % len(new))
        for n in new[:8]:
            print('  - ' + n)
        return 1
    if grew:
        print('SELFTEST-COVERAGE: FAIL（基线扩张，棘轮只许缩小）')
        return 1
    print('SELFTEST-COVERAGE: PASS（%d 个门禁中 %d 已自带 selftest，%d 条历史欠账待销）'
          % (len(gates()), len(gates()) - len(missing), len(debt)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
