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
用法：python3 data_scripts/check-workflow-contracts.py [--selftest]
  --selftest：在临时目录造三条双向夹具（未知名红 / 缺失路径红 / 全真实指向 PASS），
  跑完自删；夹具一律落在 /tmp，不污染真仓 .github/workflows/ 与 app/src/test/。
"""
import argparse
import re
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WF_DIR = ROOT / '.github' / 'workflows'
TEST_DIR = ROOT / 'app' / 'src' / 'test'


def workflow_names(wf_dir=WF_DIR):
    """返回 {文件名: name 字段}，用于比对 workflow_run 引用。

    目录参数化：真仓默认 WF_DIR；--selftest 把临时夹具目录传进来，
    保证自测读写不到真仓 workflows（夹具污染真仓会让门禁结论不可复现）。
    """
    names = {}
    if not wf_dir.is_dir():
        return names
    for p in sorted(wf_dir.glob('*.yml')):
        text = p.read_text(encoding='utf-8')
        m = re.search(r'^name:\s*(.+?)\s*$', text, re.M)
        names[p.name] = m.group(1).strip('"\'') if m else ''
    return names


def check_workflow_run(names, wf_dir=WF_DIR):
    """R-CI-1：workflow_run 引用必须是 name 而不是文件名。"""
    bad = []
    if not wf_dir.is_dir():
        return bad
    known = set(names.values())
    for p in sorted(wf_dir.glob('*.yml')):
        text = p.read_text(encoding='utf-8')
        # 只取 on.workflow_run 下的 workflows 列表，单行或多行块都覆盖
        for m in re.finditer(r'workflows:\s*\[([^\]]*)\]', text):
            refs = [x.strip().strip('"\'') for x in m.group(1).split(',') if x.strip()]
            for ref in refs:
                if ref not in known:
                    hint = '（这是文件名，应填 workflow 的 name 字段）' if ref + '.yml' in names else ''
                    bad.append(f'{p.name}: workflow_run 引用了未知名字 "{ref}"{hint}')
    return bad


def _rel_display(p: Path) -> str:
    """真仓文件相对 ROOT 显示；selftest 夹具在 /tmp 下，回退相对 test 目录上层，避免 relative_to 抛错。"""
    try:
        return str(p.relative_to(ROOT))
    except ValueError:
        return p.name


def check_contract_paths(test_dir=TEST_DIR, wf_dir=WF_DIR):
    """R-CI-2：契约测试里写死的 workflow 路径必须存在。"""
    bad = []
    if not test_dir.is_dir():
        return bad
    for p in test_dir.rglob('*.kt'):
        text = p.read_text(encoding='utf-8', errors='ignore')
        for line in text.splitlines():
            # 断言"某旧 workflow 已移除"是合法用法（assertFalse(File(...).exists())），
            # 它本就要求文件不存在，不能算失配——否则会天天误报。
            if 'assertFalse' in line:
                continue
            for ref in set(re.findall(r'\.github/workflows/([A-Za-z0-9_.\-]+\.yml)', line)):
                if not (wf_dir / ref).is_file():
                    bad.append(f'{_rel_display(p)}: 引用了不存在的 workflow 文件 .github/workflows/{ref}')
    return bad


def collect_problems(wf_dir=WF_DIR, test_dir=TEST_DIR):
    """汇总两件检查；返回 (workflow 名字表, 问题清单)。目录参数化供 selftest 复用同一套判据。"""
    names = workflow_names(wf_dir)
    problems = check_workflow_run(names, wf_dir) + check_contract_paths(test_dir, wf_dir)
    return names, problems


def report(names, problems):
    if problems:
        for x in problems:
            print('FAIL', x)
        print(f'CI-CONTRACT: FAIL（{len(problems)} 处）')
        return 1
    print(f'CI-CONTRACT: PASS（workflow {len(names)} 个，workflow_run 引用与契约测试路径均一致）')
    return 0


def selftest():
    """三条双向夹具（临时目录，跑完自删，不碰真仓 .github/workflows/）：
    a) workflow_run 引用不存在的 workflow 名 → 红；
    b) 契约测试写死的 workflow 文件不存在 → 红；
    c) 名字与路径全部指向真实夹具文件 → PASS。
    为什么必须双向：只造红样本证明不了"真指向不会被误判"，只造绿样本证明不了判据能打红。
    """
    tmp = Path(tempfile.mkdtemp(prefix='workflow-contract-selftest-'))
    try:
        wf = tmp / '.github' / 'workflows'
        tdir = tmp / 'app' / 'src' / 'test'
        wf.mkdir(parents=True)
        tdir.mkdir(parents=True)

        # c 通路夹具：真实存在的被触发 workflow + 触发器，测试也引用真实文件。
        (wf / 'deploy.yml').write_text(
            "name: Deploy Unit\non: push\njobs: {}\n", encoding='utf-8')
        (wf / 'trigger.yml').write_text(
            "name: Trigger Deploy\n"
            "on:\n"
            "  workflow_run:\n"
            "    workflows: [Deploy Unit]\n"
            "jobs: {}\n", encoding='utf-8')
        (tdir / 'ContractDeployTest.kt').write_text(
            "class ContractDeployTest {\n"
            "    fun `deploy workflow exists`() {\n"
            "        assertTrue(File(\".github/workflows/deploy.yml\").exists())\n"
            "    }\n"
            "}\n", encoding='utf-8')

        # a 通路夹具：引用一个没有任何 yml 声明过的 name。
        (wf / 'ghost.yml').write_text(
            "name: Ghost\n"
            "on:\n"
            "  workflow_run:\n"
            "    workflows: [Nonexistent Runner]\n"
            "jobs: {}\n", encoding='utf-8')

        # b 通路夹具：测试写死缺失的 workflow 路径，且不是 assertFalse 移除断言。
        (tdir / 'ContractMissingTest.kt').write_text(
            "class ContractMissingTest {\n"
            "    fun `missing workflow referenced`() {\n"
            "        assertTrue(File(\".github/workflows/absent.yml\").exists())\n"
            "    }\n"
            "}\n", encoding='utf-8')

        _, problems = collect_problems(wf, tdir)

        # a：未知名字必须被点名。
        if not any('Nonexistent Runner' in x for x in problems):
            print('SELFTEST: FAIL（workflow_run 引用未知名字未判红：%s）' % problems)
            return 1
        print('PASS 1/3 未知 workflow 名引用被点名')

        # b：缺失契约路径必须被点名。
        if not any('absent.yml' in x for x in problems):
            print('SELFTEST: FAIL（缺失 workflow 路径未判红：%s）' % problems)
            return 1
        print('PASS 2/3 不存在的契约测试路径被点名')

        # c（混合目录内）：真实指向的 deploy.yml / Deploy Unit 不得出现在问题清单。
        if any('deploy.yml' in x or 'Deploy Unit' in x for x in problems):
            print('SELFTEST: FAIL（真实指向被误判：%s）' % problems)
            return 1

        # c 独立通路：只留绿色夹具的临时目录，问题清单必须为空。
        tmp2 = Path(tempfile.mkdtemp(prefix='workflow-contract-green-'))
        try:
            wf2 = tmp2 / '.github' / 'workflows'
            t2 = tmp2 / 'app' / 'src' / 'test'
            wf2.mkdir(parents=True)
            t2.mkdir(parents=True)
            (wf2 / 'deploy.yml').write_text("name: Deploy Unit\non: push\njobs: {}\n", encoding='utf-8')
            (wf2 / 'trigger.yml').write_text(
                "name: Trigger Deploy\n"
                "on:\n"
                "  workflow_run:\n"
                "    workflows: [Deploy Unit]\n"
                "jobs: {}\n", encoding='utf-8')
            (t2 / 'ContractDeployTest.kt').write_text(
                "class ContractDeployTest {\n"
                "    fun exists() { assertTrue(File(\".github/workflows/deploy.yml\").exists()) }\n"
                "}\n", encoding='utf-8')
            _, green = collect_problems(wf2, t2)
            if green:
                print('SELFTEST: FAIL（全真实指向夹具被误判：%s）' % green)
                return 1
        finally:
            shutil.rmtree(tmp2, ignore_errors=True)
        print('PASS 3/3 名字与路径全指向真实夹具时 PASS')
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser(description='CI 契约机械校验（workflow_run 引用名 + 契约测试路径）')
    ap.add_argument('--selftest', action='store_true', help='临时目录造双向夹具自测，跑完自删')
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    names, problems = collect_problems()
    return report(names, problems)


if __name__ == '__main__':
    sys.exit(main())
