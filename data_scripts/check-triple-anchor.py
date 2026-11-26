#!/usr/bin/env python3
"""三段流水线锚点守卫（口径冻结 F-78，2026-11-24 新铁律）。

背景：无具体规格的功能必须走 规格设计 → 独立选型 →（判低回炉循环）→ 写码 三段
流水线（F-73~F-78）。实现/交付类报告若缺三段任一锚点，或选型文档无明确结论，
即视为未走流水线，门禁 FAIL。

三锚点（逐字前缀，约定写在报告首行）：
  规格：docs/…              选中规格设计稿路径
  选型：docs/验收-选型-….md  独立选型结论路径
  验收：docs/验收-….md       实现验收报告路径

选型结论硬判据（F-78）：选型文档必须含明确的「选中第 N 份」+「判低份数与理由」
（淘汰/判低/落选 任一标记）；只有"看过了/差不多"没有结论的一律 FAIL。

例外口径（F-78 退回设计段）：报告写明"该功能无规格→已按 F-73 退回设计"时，
不要求 `规格：` 锚点，但必须显式带退回声明；既无 `规格：` 锚点、又无退回声明
却声称新功能实现的，FAIL 并提示应退回第①段重新设计规格。

边界：只由 rule-gate.sh ⑮ 传入本次 git status 新增/修改的报告；历史文档不纳入
（同 check-evidence.py 的历史留档口径，避免一上来就红）。

用法：python3 data_scripts/check-triple-anchor.py [报告.md ...]
退出码：全部 PASS=0；任一 FAIL=1。
"""

import argparse
import glob
import os
import re
import sys

# 始终必备的锚点：选型结论 / 实现验收
REQUIRED = [('选型', '选型：'), ('验收', '验收：')]
# 规格锚点；缺失时可用退回设计段声明替代
SPEC_ANCHOR = '规格：'
# 退回设计段声明（命中任一即视为已按 F-73 退回第①段）
RETURN_PATTERNS = [
    re.compile(r'退回设计'),
    re.compile(r'按\s*F-73\s*退回'),
    re.compile(r'F-73\s*退回'),
    re.compile(r'无规格.*退回'),
]
# 选型文档路径抽取（选型：docs/验收-选型-x.md）
SELECTION_PATH_RE = re.compile(r'选型：\s*([^\s；;，,]+?\.md)')
# 选型结论：明确选中第几份/哪个方案
SELECTED_RE = re.compile(r'选中第?\s*[0-9一二三四五六七八九A-Ca-c]|选中方案|选中：')
# 选型结论：判低/淘汰/落选份数与理由
REJECTED_RE = re.compile(r'淘汰|判低|落选')
# 全部判低（触发双换回炉，F-73③）：须有下一轮 v2 设计/审验记录
ALL_LOW_RE = re.compile(r'全部判低|全判低|无一符合|全部落选')
# 下一轮产物命名：v2 规格候选（两种历史命名都认）+ v2 选型报告
V2_DESIGN_GLOBS = ['docs/规格候选-*-v2*.md', 'docs/规格-设计-*-v2*.md']
V2_SELECT_GLOBS = ['docs/验收-选型-*-v2*.md']

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _resolve(path: str) -> str:
    """锚点路径相对仓库根解析；绝对路径原样返回。"""
    return path if os.path.isabs(path) else os.path.join(ROOT, path)


def check_selection_doc(report_path: str, text: str):
    """核验选型锚点指向的文档是否有明确选型结论。返回缺失项列表（空=通过）。"""
    m = SELECTION_PATH_RE.search(text)
    if not m:
        return ['选型锚点路径无法解析']
    sel_path = _resolve(m.group(1))
    if not os.path.isfile(sel_path):
        return [f'选型文档不存在（{m.group(1)}）']
    with open(sel_path, encoding='utf-8', errors='replace') as fh:
        sel_text = fh.read()
    missing = []
    if not SELECTED_RE.search(sel_text):
        missing.append('选型文档缺"选中第 N 份"结论')
    if not REJECTED_RE.search(sel_text):
        missing.append('选型文档缺判低/淘汰份数与理由')
    # 全部判低 → 必须留得下一轮（双换后）v2 设计/审验记录，防判低后悄悄按旧稿开工（F-78）
    if ALL_LOW_RE.search(sel_text):
        v2_design = [f for pat in V2_DESIGN_GLOBS for f in glob.glob(os.path.join(ROOT, pat))]
        v2_select = [f for pat in V2_SELECT_GLOBS for f in glob.glob(os.path.join(ROOT, pat))]
        if not v2_design:
            missing.append('全部判低后缺下一轮 v2 规格候选记录（docs/规格候选-*-v2.md）')
        if not v2_select:
            missing.append('全部判低后缺下一轮 v2 选型报告')
    return missing


def check_file(path: str):
    """单份报告判定。返回 dict：path / ok / missing / readable / returned。"""
    if not os.path.isfile(path):
        return {'path': path, 'ok': False,
                'missing': [k for k, _ in REQUIRED] + ['规格'],
                'readable': False, 'returned': False}
    with open(path, encoding='utf-8', errors='replace') as fh:
        text = fh.read()

    missing = [k for k, prefix in REQUIRED if prefix not in text]
    returned = any(p.search(text) for p in RETURN_PATTERNS)

    if SPEC_ANCHOR not in text and not returned:
        # 既无规格锚点、又无退回声明：提示应退回第①段（F-73）
        missing.append('规格（或 F-73 退回设计段声明）')

    # 选型文档结论核验（选型锚点在场时才查；锚点缺失已计入 missing）
    if '选型' not in missing:
        missing.extend(check_selection_doc(path, text))

    return {'path': path, 'ok': not missing, 'missing': missing,
            'readable': True, 'returned': returned}


def main(argv=None):
    parser = argparse.ArgumentParser(
        description='三段流水线锚点守卫：规格/选型/验收 三锚点齐全且选型有结论才 PASS')
    parser.add_argument('reports', nargs='*', help='报告文件；缺省提示无待检报告')
    args = parser.parse_args(argv)

    if not args.reports:
        # 空集不得判 PASS（㊾）；同 check-evidence，空集只可能是漏传或口径混淆。
        print('EMPTY: 未提供待检报告（空集不判 PASS；diff 型调用请走 ㊽ 口径）')
        return 2

    passed = failed = 0
    for path in args.reports:
        res = check_file(path)
        if res['ok']:
            passed += 1
            note = '（已退回设计段，免规格锚点）' if res['returned'] else ''
            print(f"── {path}  → PASS（三锚点/退回声明 + 选型结论齐全）{note}")
        else:
            failed += 1
            if not res['readable']:
                print(f"── {path}  → FAIL（文件不存在，三锚点全缺）")
            else:
                hint = '；无规格应退回第①段重新设计规格（F-73）' \
                    if any('规格' in m for m in res['missing']) else ''
                print(f"── {path}  → FAIL（缺 {'、'.join(res['missing'])}）{hint}")

    print(f"汇总: PASS={passed} FAIL={failed}（共 {passed + failed} 份）")
    return 0 if failed == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
