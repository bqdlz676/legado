#!/usr/bin/env python3
"""回执证据守卫（2026-11-24 事故复盘产物，只改脚本不改业务代码）。

事故：子代理 s_0007oNQJnkb 领了"修三处卡片"的长派单，零工具调用，直接把
"Stop bargaining." 重复四句当回执交回；三处一处没改，承诺的修复文档也不存在。
旧 check-report.sh 只查报告行数/结构，不查报告里到底有没有可回源取证，空话报告照样 PASS。

本脚本把"回执必须带回证"变成可机械判定。三条规则**同时成立**才 PASS，规则可解释、低误报：
  E1 命令证据：文中存在 ≥1 行真跑过东西的命令 —— grep -n / grep -rn / rg -n /
     python3 data_scripts/ / bash data_scripts/。
  E2 命中定位：≥1 个 `文件名.kt:行号` / `.py:行` / `.md:行` 形态定位串
     （正则 `[\\w\\-/\\\\.]+\\.[A-Za-z]+:[0-9]+`）。
  E3 结果标记：≥1 处 ✓ / PASS / ERROR=0 / 配平差 0 / 配平语境下的 `数字/数字` 配平写法。

误报控制：报告里出现 grep 字样但后面没有任何 `文件:行`（纯说明书式）→ E1 命中但 E2 缺
→ 仍 FAIL，这是有意为之，正是本次事故的形态。

用法：
  python3 data_scripts/check-evidence.py [--strict] [报告.md ...]
  不传文件时默认批量体检 docs/修复-*.md 与 docs/验收-*.md（只报告，不为凑 PASS 改历史文档）。
退出码：全部 PASS=0；任一 FAIL=1。--strict 只打印 FAIL 明细。
"""

import argparse
import glob
import os
import re
import sys

# E1：真跑过命令的形态（选项字母放宽到 r/n/R/q 等常见 grep 开关，必须带 `-` 开关才算命令行）
E1_PATTERNS = [
    re.compile(r'grep\s+-[A-Za-z]+'),
    re.compile(r'rg\s+-n'),
    re.compile(r'python3\s+data_scripts/'),
    re.compile(r'bash\s+data_scripts/'),
]

# E2：文件:行 定位串（文件名可含字母/数字/下划线/连字符/斜杠/反斜杠/点，扩展名 + 冒号 + 行号）
E2_RE = re.compile(r'[\w\-/\\.]+\.[A-Za-z]+:[0-9]+')

# E3：实测结论标记（纯符号/关键字一类）
E3_PLAIN = [
    re.compile(r'✓'),
    re.compile(r'PASS'),
    re.compile(r'ERROR=0'),
    re.compile(r'配平差\s*0'),
]
# E3 配平写法：`数字/数字` 仅在同行出现配平/相等/一致等语境词时才算，避免把日期 2026/11/24 误判
E3_RATIO_RE = re.compile(r'\d+\s*/\s*\d+')
E3_RATIO_CTX = re.compile(r'配平|平衡|相等|一致|通过|差|命中|空')

E_LABELS = {
    'E1': '命令证据',
    'E2': '命中定位',
    'E3': '结果标记',
}


def evaluate(text: str):
    """返回 (e1, e2, e3) 三个命中计数；计数只用于展示，门禁只看是否 >0。"""
    lines = text.splitlines()
    e1 = sum(1 for line in lines if any(p.search(line) for p in E1_PATTERNS))
    e2 = len(E2_RE.findall(text))
    e3 = 0
    for line in lines:
        if any(p.search(line) for p in E3_PLAIN):
            e3 += 1
            continue
        if E3_RATIO_RE.search(line) and E3_RATIO_CTX.search(line):
            e3 += 1
    return e1, e2, e3


def verify_e2(text: str, root: str):
    """E2 命中必须回源：引用的 文件:行 要真存在且行号在文件长度内（M-31 幻影路径同族）。

    为什么：旧判据只数 `文件:行` 的字符串形态，随便写 `a.kt:123` 就能凑够 E2——
    证据成了排版，不是事实。这里按仓库根解析，允许纯文件名（在 app/ 与 data_scripts/ 下模糊找）。
    """
    ok, phantom = 0, []
    for m in E2_RE.finditer(text):
        ref = m.group(0)
        path_part, _, line_part = ref.rpartition(':')
        cands = [os.path.join(root, path_part)]
        if not os.path.isabs(path_part):
            cands += [os.path.join(root, d, path_part) for d in ('app', 'data_scripts')]
        hit = next((c for c in cands if os.path.isfile(c)), None)
        if not hit:
            phantom.append(ref)
            continue
        try:
            n = sum(1 for _ in open(hit, encoding='utf-8', errors='replace'))
        except OSError:
            phantom.append(ref)
            continue
        if int(line_part) <= n:
            ok += 1
        else:
            phantom.append(ref + '（行号越界，文件仅 %d 行）' % n)
    return ok, phantom


def skip_legacy(text: str) -> bool:
    """规则上线前交付的报告：文件内明写「证据三件套留档」即豁免。

    为什么不回填 E1/E2/E3：那要拿真命令真命中填，按现在的树重填等于把旧结论
    伪装成新证据。豁免必须带文件内声明（可审计），并挂号重判单，不许静默跳过。
    """
    return '证据三件套留档' in text


def check_file(path: str):
    """单份报告判定。返回 dict：path / ok / counts / missing / readable。"""
    if not os.path.isfile(path):
        return {'path': path, 'ok': False, 'counts': (0, 0, 0),
                'missing': ['E1', 'E2', 'E3'], 'readable': False}
    try:
        with open(path, encoding='utf-8', errors='strict') as fh:
            text = fh.read()
    except UnicodeDecodeError as e:
        return {'path': path, 'ok': False, 'counts': (0, 0, 0),
                'missing': ['E1', 'E2', 'E3'], 'readable': True,
                'decode_error': f'非法 UTF-8（第 {e.start} 字节起）'}
    if skip_legacy(text):
        return {'path': path, 'ok': True, 'counts': (0, 0, 0), 'missing': [], 'readable': True}
    e1, e2, e3 = evaluate(text)
    e2_ok, phantom = verify_e2(text, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    missing = [name for name, hit in (('E1', e1 > 0), ('E2', e2_ok > 0), ('E3', e3 > 0)) if not hit]
    return {'path': path, 'ok': not missing, 'counts': (e1, e2, e3),
            'missing': missing, 'readable': True, 'e2_ok': e2_ok, 'phantom': phantom}


def main(argv=None):
    parser = argparse.ArgumentParser(description='回执证据守卫：E1/E2/E3 三条同时成立才 PASS')
    parser.add_argument('--strict', action='store_true', help='只打印 FAIL 明细，PASS 行不展开')
    parser.add_argument('reports', nargs='*', help='报告文件；缺省批量体检 docs/修复-*.md 与 docs/验收-*.md')
    args = parser.parse_args(argv)

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if args.reports:
        files = args.reports
    else:
        files = sorted(glob.glob(os.path.join(root, 'docs', '修复-*.md'))
                       + glob.glob(os.path.join(root, 'docs', '验收-*.md')))

    if not files:
        # 空集不得判 PASS（㊾）：漏传参数或 diff 口径空集都不该表述成"合规"。
        print('EMPTY: 未提供待检报告（空集不判 PASS；diff 型调用请走 ㊽ 口径）')
        return 2

    passed = failed = 0
    for path in files:
        res = check_file(path)
        e1, e2, e3 = res['counts']
        if res.get('decode_error'):
            failed += 1
            print(f"FAIL {path}：{res['decode_error']}，不得按可读文件判 PASS")
            continue
        if res['ok']:
            passed += 1
            if not args.strict:
                print(f"── {path}  E1={e1} E2={e2} E3={e3} → PASS")
        else:
            failed += 1
            if not res['readable']:
                print(f"── {path}  → FAIL（文件不存在，E1/E2/E3 全缺）")
            else:
                miss_txt = '、'.join(f"{m} {E_LABELS[m]}" for m in res['missing'])
                extra = ''
                if res.get('phantom'):
                    extra = '；幻影/越界 %d 处：%s' % (len(res['phantom']), '、'.join(res['phantom'][:3]))
                print(f"── {path}  E1={e1} E2={e2}(回源{res.get('e2_ok', 0)}) E3={e3} → FAIL（缺 {miss_txt}）{extra}")

    print(f"汇总: PASS={passed} FAIL={failed}（共 {passed + failed} 份）")
    return 0 if failed == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
