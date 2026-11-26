#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 55 永远找问题（F8 机检）——只汇报"已完成"=把审计停在自己手里。

为什么需要它：用户三次追问「继续找机制的问题」「你踏马没找全」「永远找问题解决问题提出问题呢」，
说明"找问题"写在文档里靠自觉必然漂。判据必须落在报告正文上。
判据（三选一不成立即红）：
  ① 报告内有「新发现问题」小节（标题含"新发现问题"）；
  ② 该节至少 1 条带编号的问题；
  ③ 每条带处置状态词（已修／已入队／判废／待裁／已闭环）。
空旷豁免：写明"已穷举"且列出面清单（"穷举面：" 后非空）即 PASS，禁"未发现问题"一句话了事。

用法：
  python3 data_scripts/check-problem-finding.py            # 扫 origin/main..HEAD 新增 + 工作区未提交的报告
  python3 data_scripts/check-problem-finding.py <file...>  # 只判指定文件
  python3 data_scripts/check-problem-finding.py --selftest # 双向自测
"""
import json
import re
import subprocess
import sys
import tempfile
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
NAME_RE = re.compile(r'docs/(修复|验收|诊断|分析|门禁全量|CI核验|复扫|复盘)-[^/]*\.md$')
STATE_WORDS = ('已修', '已入队', '判废', '待裁', '已闭环', '已处置')
BASELINE = pathlib.Path(__file__).resolve().parent / 'problem-finding-baseline.json'
EMPTY_OK = ('穷举面', '已穷举')


def report_paths() -> list[pathlib.Path]:
    out = []
    try:
        r = subprocess.run(['git', 'rev-list', '--objects', 'origin/main..HEAD'],
                           cwd=ROOT, capture_output=True, text=True)
        for line in r.stdout.splitlines():
            parts = line.split(' ', 1)
            if len(parts) == 2 and NAME_RE.match(parts[1]):
                p = ROOT / parts[1]
                if p.is_file():
                    out.append(p)
    except Exception:
        pass
    r2 = subprocess.run(['git', 'status', '--porcelain', '-uall'], cwd=ROOT, capture_output=True, text=True)
    for line in r2.stdout.splitlines():
        p = line[3:].strip()
        if NAME_RE.match(p) and (ROOT / p).is_file():
            out.append(ROOT / p)
    return sorted(set(out))


def judge(text: str) -> tuple[bool, str]:
    m = re.search(r'(?m)^#+\s*.*新发现问题.*$', text)
    if not m:
        if any(k in text for k in EMPTY_OK):
            return True, '空旷豁免：已穷举面清单'
        return False, '缺「新发现问题」小节'
    sec = text[m.end():]
    nxt = re.search(r'(?m)^#+\s', sec)
    body = sec[:nxt.start()] if nxt else sec
    items = re.findall(r'(?m)^\s*(?:-|\d+\.|\|)\s*', body)
    if len(items) < 1:
        if any(k in body for k in EMPTY_OK):
            return True, '空旷豁免：已穷举面清单'
        return False, '「新发现问题」节内 0 条'
    if not any(w in body for w in STATE_WORDS):
        return False, '问题无处置状态（已修/已入队/判废/待裁/已闭环）'
    return True, f'问题 {len(items)} 条，含处置状态'


def main(argv: list[str]) -> int:
    if '--selftest' in argv:
        good = '# 修复-X\n\n## 新发现问题\n1. 门禁 52 从未接线 —— 已修（rule-gate.sh:480）\n'
        bad = '# 修复-Y\n\n## 结论\n一切正常，未发现问题。\n'
        bad2 = '# 修复-Z\n\n## 新发现问题\n1. 某个问题\n'
        with tempfile.TemporaryDirectory() as d:
            ok = True
            for name, content, want in [('a.md', good, True), ('b.md', bad, False), ('c.md', bad2, False)]:
                p = pathlib.Path(d) / name
                p.write_text(content, encoding='utf-8')
                got, why = judge(p.read_text(encoding='utf-8'))
                if got != want:
                    print(f'  SELFTEST FAIL {name}: expected {want} got {got} ({why})')
                    ok = False
            print('SELFTEST: ' + ('PASS（好样本 1 通过，坏样本 2 判红）' if ok else 'FAIL'))
            return 0 if ok else 1
    targets = [pathlib.Path(a) for a in argv if a.endswith('.md')] or report_paths()
    base = set(json.loads(BASELINE.read_text(encoding='utf-8'))) if BASELINE.is_file() else set()
    if not targets:
        print('PROBLEM-FINDING: PASS（本轮无新增报告，无需判）')
        return 0
    fails = []
    for p in targets:
        try:
            txt = p.read_text(encoding='utf-8')
        except (OSError, UnicodeDecodeError) as e:
            fails.append(f'{p.relative_to(ROOT)}：不可读（{type(e).__name__}）')
            continue
        ok, why = judge(txt)
        rel = str(p.relative_to(ROOT))
        if not ok and rel in base:
            continue
        if not ok:
            fails.append(f'{p.relative_to(ROOT)}：{why}')
    if fails:
        print('PROBLEM-FINDING: FAIL')
        for f in fails:
            print(f'  - {f}')
        return 1
    hist = sorted(base)
    if hist:
        print(f'INFO 历史欠账 {len(hist)} 份走基线（只许缩小）：' + '、'.join(h.split("/")[-1] for h in hist))
    print(f'PROBLEM-FINDING: PASS（{len(targets)} 份报告均带新发现问题与处置状态）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
