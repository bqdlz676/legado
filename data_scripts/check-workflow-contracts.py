#!/usr/bin/env python3
"""CI 契约机械校验 —— 2026-11-25 CI 双车道拆分五轮红的沉淀。

为什么有这个脚本：那次拆分连红五轮，根因全在"结构改动没人机械复核"，靠人眼看到第三轮才抓到。
适用条件：仓库里存在 .github/workflows/ 与 app/src/test/ 会话；任何 workflow 增删改名、或改契约测试前后各跑一次。

检查两件事（对应复盘里的 R-CI-1 / R-CI-2）：
1. 每个 workflow 的 on.workflow_run.workflows 列出的名字，必须等于某个 workflow 文件的 name 字段。
   填文件名不报错但闸门永不触发（第 5 轮就是这样：verify 绿了，package 一直没起）。
2. 契约测试里出现的 .github/workflows/<x>.yml 路径必须真实存在。
   旧 build.yml 退役那轮，5 个契约测试拿它当仓库根锚点，整组集体误红。

退出码：0 全过；1 有违规。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WF_DIR = ROOT / '.github' / 'workflows'
TEST_DIR = ROOT / 'app' / 'src' / 'test'


def workflow_names():
    """返回 {文件名: name 字段}，用于比对 workflow_run 引用。"""
    names = {}
    for p in sorted(WF_DIR.glob('*.yml')):
        text = p.read_text(encoding='utf-8')
        m = re.search(r'^name:\s*(.+?)\s*$', text, re.M)
        names[p.name] = m.group(1).strip('"\'') if m else ''
    return names


def check_workflow_run(names):
    """R-CI-1：workflow_run 引用必须是 name 而不是文件名。"""
    bad = []
    known = set(names.values())
    for p in sorted(WF_DIR.glob('*.yml')):
        text = p.read_text(encoding='utf-8')
        # 只取 on.workflow_run 下的 workflows 列表，单行或多行块都覆盖
        for m in re.finditer(r'workflows:\s*\[([^\]]*)\]', text):
            refs = [x.strip().strip('"\'') for x in m.group(1).split(',') if x.strip()]
            for ref in refs:
                if ref not in known:
                    hint = '（这是文件名，应填 workflow 的 name 字段）' if ref + '.yml' in names else ''
                    bad.append(f'{p.name}: workflow_run 引用了未知名字 "{ref}"{hint}')
    return bad


def check_contract_paths():
    """R-CI-2：契约测试里写死的 workflow 路径必须存在。"""
    bad = []
    if not TEST_DIR.is_dir():
        return bad
    for p in TEST_DIR.rglob('*.kt'):
        text = p.read_text(encoding='utf-8', errors='ignore')
        for line in text.splitlines():
            # 断言"某旧 workflow 已移除"是合法用法（assertFalse(File(...).exists())），
            # 它本就要求文件不存在，不能算失配——否则会天天误报。
            if 'assertFalse' in line:
                continue
            for ref in set(re.findall(r'\.github/workflows/([A-Za-z0-9_.\-]+\.yml)', line)):
                if not (WF_DIR / ref).is_file():
                    bad.append(f'{p.relative_to(ROOT)}: 引用了不存在的 workflow 文件 .github/workflows/{ref}')
    return bad


def main():
    names = workflow_names()
    problems = check_workflow_run(names) + check_contract_paths()
    if problems:
        for x in problems:
            print('FAIL', x)
        print(f'CI-CONTRACT: FAIL（{len(problems)} 处）')
        return 1
    print(f'CI-CONTRACT: PASS（workflow {len(names)} 个，workflow_run 引用与契约测试路径均一致）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
