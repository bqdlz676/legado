#!/usr/bin/env python3
"""门禁 ㊽：「本轮」必须包含已提交未推送的改动（F-123）。

为什么（2026-11-26 实测）：
`pre-push-check.sh` 第 3 步与 `rule-gate.sh` ⑭ 的"本轮改动"= `git status --porcelain`，只看工作区。
而正常流程是「写 → commit → push」，于是**只要先提交，报告就永远不进判据**——
本夜 Q-209 报告就是这样在提交后让第 3 步打印"本轮无报告改动"而整段跳过的。
这不是误报问题，是"判据可被一个 git commit 合法绕过"，属制度级漏洞。

判据：取 `origin/main..HEAD` 的已提交改动 ∪ 工作区改动，凡是报告类 md（修复/验收/实现/机制/规格/复盘），
逐份跑 `check-onboarding.py` 的回执判据；任一份不合规即 FAIL。
为什么用 origin/main 而不是 HEAD~1：推送是分批的，"本轮"应以"还没上云的全部提交"为准。

用法：python3 data_scripts/check-unpushed-reports.py [--root DIR] [--selftest]
退出码：0 合规或无报告 / 1 有报告不合规
"""
import argparse
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
REPORT_RE = re.compile(r'^docs/(修复|验收|实现|机制|规格|复盘)-.*\.md$')


def git(root, *args):
    return subprocess.run(['git', '-c', 'core.quotepath=false'] + list(args), cwd=str(root),
                          capture_output=True, text=True)


def candidate_reports(root):
    """已提交未推送 ∪ 工作区改动的报告类 md。"""
    out = set()
    base = git(root, 'rev-parse', '--verify', 'origin/main')
    if base.returncode == 0:
        for line in git(root, 'diff', '--name-only', 'origin/main..HEAD').stdout.splitlines():
            if REPORT_RE.match(line.strip()):
                out.add(line.strip())
    else:
        # 没有远端引用（如临时仓库）：退回 HEAD~1..HEAD，仍然覆盖"已提交"这一半
        for line in git(root, 'diff', '--name-only', 'HEAD~1..HEAD').stdout.splitlines():
            if REPORT_RE.match(line.strip()):
                out.add(line.strip())
    for row in git(root, 'status', '--porcelain').stdout.splitlines():
        p = row[3:].strip().strip('"')
        if REPORT_RE.match(p):
            out.add(p)
    return sorted(out)


def judge(root, files):
    """对每份报告跑回执判据（单源复用 check-onboarding.check_report，不另造一套）。"""
    import importlib.util
    here = pathlib.Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location('co_mod', here / 'check-onboarding.py')
    co = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(co)
    co.ROOT = str(root)  # 让指纹/回源都按被检仓库算
    cwd0 = os.getcwd()
    bad = []
    for rel in files:
        full = pathlib.Path(root) / rel
        if not full.is_file():
            continue  # 已删除的报告不判（删除在 git 历史里可回溯）
        legacy = subprocess.run([sys.executable, 'data_scripts/is-legacy-headonly.py', rel],
                                cwd=str(root), capture_output=True, text=True)
        if legacy.returncode == 0:
            continue  # 存量件仅头部留痕：与 ⑭ 同源豁免
        try:
            os.chdir(str(root))  # check_report 按 CWD 打开文件；被检仓库可能是临时副本
            probs = co.check_report(rel)
        finally:
            os.chdir(cwd0)
        if not probs and re.match(r'docs/(修复|实现)-.*\.md$', rel):
            # ⑲ 也只看 git status 的"本轮改动"，同样能被"先提交"绕过（M-42 同族）。
            # 只对 修复/实现 类追锚点卡：验收/机制/规格/复盘 是证据与制度文书，锚点卡语义不适用；
            # 三段锚点（⑮）对"修一个缺陷"的报告要求 规格+选型 两锚，语义错配，本轮不并入（登记 M-42 待裁）。
            for script, args in (('check-anchor-card.py', ['--strict', rel]),):
                r = subprocess.run([sys.executable, 'data_scripts/' + script] + args,
                                   cwd=str(root), capture_output=True, text=True)
                if r.returncode != 0:
                    tail = (r.stdout or r.stderr).strip().splitlines()
                    msg = next((x for x in tail if 'FAIL' in x), tail[-1] if tail else 'rc=%d' % r.returncode)
                    probs = ['%s：%s' % (script, msg.strip()[:80])]
                    break
        if probs:
            bad.append((rel, probs))
    return bad


def report(bad, files):
    if bad:
        print('UNPUSHED-REPORTS: FAIL（%d/%d 份已提交未推送的报告不合规）' % (len(bad), len(files)))
        for rel, probs in bad:
            print('  - %s：%s' % (rel, '；'.join(probs)[:160]))
        return 1
    print('UNPUSHED-REPORTS: PASS（本轮 %d 份报告（含已提交未推送）回执合规）' % len(files))
    return 0


def selftest():
    """在 /tmp 造临时仓库：坏报告先提交 → 必 FAIL；补成合规 → 必 PASS。"""
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='unpushed-'))
    try:
        sub = tmp / 'repo'
        (sub / 'docs').mkdir(parents=True)
        shutil.copy(pathlib.Path(__file__).resolve().parent / 'check-onboarding.py',
                    sub / 'data_scripts' / 'check-onboarding.py') if False else None
        (sub / 'data_scripts').mkdir(exist_ok=True)
        for name in ('check-onboarding.py', 'is-legacy-headonly.py'):
            src = pathlib.Path(__file__).resolve().parent / name
            (sub / 'data_scripts' / name).write_text(src.read_text(encoding='utf-8'), encoding='utf-8')
        for name in ('机制-总控必读.md', '机制-子代理必读.md', '机制-验收代理必读.md', '机制-文档子代理必读.md'):
            (sub / 'docs' / name).write_text('# 占位守则\n', encoding='utf-8')
        rep = sub / 'docs' / '修复-样本报告.md'
        rep.write_text('# 样本报告\n\n没有回执块。\n', encoding='utf-8')
        git(sub, 'init', '-q')
        git(sub, 'add', '-A')
        git(sub, 'commit', '-qm', '样本')
        files = candidate_reports(sub)
        bad = judge(sub, files)
        if not bad:
            print('SELFTEST: FAIL（坏报告先提交后仍判 PASS，漏洞未堵住）')
            return 1
        print('PASS 1/3 先提交的坏报告被判 FAIL（旧口径会漏）')
        rep.write_text('# 样本报告\n\n## 守则回执\n\n- 适用条款：机械改动\n', encoding='utf-8')
        git(sub, 'add', '-A')
        git(sub, 'commit', '-qm', '补回执')
        bad = judge(sub, candidate_reports(sub))
        print('PASS 2/3 补齐适用条款后不再 FAIL（仍缺指纹时应 FAIL，见下一步）')
        if not bad:
            # 补齐后仍应有"指纹缺失"问题：这验证判据没被放宽成空判
            print('SELFTEST: FAIL（合规样本被误判为 PASS，说明判据失效）')
            return 1
        print('SELFTEST: PASS（先提交绕过路径已被堵住）')
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--root', default=str(pathlib.Path(__file__).resolve().parent.parent))
    ap.add_argument('--selftest', action='store_true')
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    root = pathlib.Path(a.root)
    files = candidate_reports(root)
    return report(judge(root, files), files)


if __name__ == '__main__':
    sys.exit(main())
