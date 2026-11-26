#!/usr/bin/env python3
"""门禁 ㊿：规则文本里不许再出现"用 wait_agent 挂起"的有效条款（F-124）。

为什么（2026-11-26 用户口令「把wait agent删了」）：规则自己写着"用 wait_agent 阻塞等"，
主控照着做就是空等，用户已将整档"等"删掉。文本不一致＝读哪条都能开脱，必须机检。
判据：扫 docs/机制-总控必读.md 与 docs/落文-规矩总纲.md，凡是出现 wait_agent 的行，
必须带"作废/不许/删除/禁止"之一，否则 FAIL 并点名 文件:行。
用法：python3 data_scripts/check-no-wait-rule.py [--selftest]
"""
import pathlib
import sys
import tempfile
import shutil

ROOT = pathlib.Path(__file__).resolve().parent.parent
TARGETS = ['docs/机制-总控必读.md', 'docs/落文-规矩总纲.md',
           'docs/机制-防限流彻底方案.md', 'docs/派单队列.md']
OKWORDS = ('作废', '不许', '删除', '禁止', '禁用', '不使用')


def scan(root=ROOT):
    bad = []
    for rel in TARGETS:
        p = root / rel
        if not p.is_file():
            continue
        for i, line in enumerate(p.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
            if 'wait_agent' in line and not any(w in line for w in OKWORDS):
                bad.append('%s:%d %s' % (rel, i, line.strip()[:90]))
    return bad


def main():
    if '--selftest' in sys.argv:
        tmp = pathlib.Path(tempfile.mkdtemp(prefix='nowait-'))
        try:
            (tmp / 'docs').mkdir()
            (tmp / 'docs' / '机制-总控必读.md').write_text('用 wait_agent 阻塞等结果\n', encoding='utf-8')
            (tmp / 'docs' / '落文-规矩总纲.md').write_text('wait_agent 已删除（2026-11-26）\n', encoding='utf-8')
            hit = scan(tmp)
            miss = scan(ROOT)
            ok = bool(hit) and not miss
            print('SELFTEST: %s（坏样本 %d 命中，真仓 %d 命中）' % ('PASS' if ok else 'FAIL', len(hit), len(miss)))
            return 0 if ok else 1
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    bad = scan()
    if bad:
        print('NO-WAIT-RULE: FAIL（%d 行仍在教人挂起）' % len(bad))
        for b in bad[:6]:
            print('  - ' + b)
        return 1
    print('NO-WAIT-RULE: PASS（规则文本无有效 wait_agent 条款）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
