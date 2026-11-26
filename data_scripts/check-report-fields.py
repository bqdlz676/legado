#!/usr/bin/env python3
"""报告字段门禁（把 L1「改坏推演」从口号变成可机检，2026-11-26）。

背景：pyramid 实测 211 份修复/验收报告里只有 17 份写了改坏推演或自查反例。
L1 是返工的最大来源，却完全没有机检——于是"要有自查反例"只是我在派单时口头强调。

判据（只查本轮新增/改动的报告，历史留档不追改，避免一次判红 200 份）：
  A 首行锚点卡（`锚点卡：docs/锚点/...md`），且该文件真实存在。
  B 有结论标记（总结论/PASS/有条件通过/FAIL 任一）。
  C 修复类报告必须有「自查反例」或「改坏」节（验收类必须有「改坏」推演段）。
  D 至少 5 处 `文件:行` 形式的证据锚点（防止空口结论）。

用法：python3 data_scripts/check-report-fields.py [报告路径...]
  不给参数则自动取 `git status` 里 docs/(修复|验收)-*.md 的改动/新增文件。
退出码：任一不符 = 1。
"""

import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def only_backfill(path):
    """本轮改动是否为"纯回执补录/留档标记"：只有新增行、且新增行全是回执留痕。

    为什么放行：Q-199/200 这类存量补录会触碰 87 份老报告头部，老报告成文早于
    锚点卡/证据三件套规则，拿新规回头判死它们等于让补录永远推不上去。判据要严：
    必须是 +N/-0（纯插入）且每行都含「回执补录」或「回执缺失（规则上线前交付」。
    """
    out = subprocess.run(["git", "diff", "--numstat", "--", path], cwd=ROOT,
                         capture_output=True, text=True,
                         env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}).stdout.strip()
    if not out:
        return False
    added, deleted = out.split("\t")[:2]
    if deleted != "0":
        return False
    diff = subprocess.run(["git", "diff", "-U0", "--", path], cwd=ROOT,
                          capture_output=True, text=True,
                          env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}).stdout
    ins = [l[1:].strip() for l in diff.splitlines() if l.startswith("+") and not l.startswith("+++")]
    ins = [l for l in ins if l]
    return bool(ins) and all(("回执补录" in l) or ("回执缺失" in l and "留档" in l) for l in ins)


def is_fixture(path):
    # 回归夹具（data_scripts/fixtures/ 下、.fixture 后缀）是测试资产，不按真报告判（2026-11-26 追加）。
    return "data_scripts/fixtures" in path or path.endswith(".fixture")


def changed_reports():
    out = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT,
                         capture_output=True, text=True,
                         env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}).stdout
    paths = []
    for line in out.splitlines():
        p = line[3:].strip().strip('"')
        if is_fixture(p):
            continue
        if re.match(r"docs/(修复|验收|组装|分析)-.*\.md$", p) and not only_backfill(p):
            paths.append(p)
    return paths


def check(path):
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        return [f"{path}: 文件不存在"]
    body = open(full, encoding="utf-8", errors="ignore").read()
    fails = []
    head = body.splitlines()[:3]
    anchor = next((l for l in head if l.strip().startswith("锚点卡：")), None)
    if not anchor:
        fails.append("A 首行缺锚点卡")
    else:
        m = re.search(r"docs/锚点/[^\s`]+\.md", anchor)
        if not m or not os.path.isfile(os.path.join(ROOT, m.group(0))):
            fails.append("A 锚点卡指向的文件不存在")
    if not re.search(r"(总结论|结论)[：:]|PASS|有条件通过|FAIL", body):
        fails.append("B 缺结论标记")
    is_fix = os.path.basename(path).startswith(("修复-", "组装-"))
    if is_fix and "自查反例" not in body and "改坏" not in body:
        fails.append("C 修复/组装类报告缺「自查反例」或「改坏」节")
    if not is_fix and "改坏" not in body and "推演" not in body:
        fails.append("C 验收类报告缺「改坏推演」段")
    anchors = len(re.findall(r"[\w./]+\.(?:kt|py|sh|xml|md):\d+", body))
    if anchors < 5:
        fails.append(f"D 证据锚点仅 {anchors} 处（<5）")
    return fails


def selftest() -> int:
    """双向自测：四件套齐全的修复类报告 → check() 返回空列表 PASS；缺锚点卡/结论/反例/锚点 → FAIL。
    做法：临时目录接管 ROOT 后直接调生产 check(path)，A/B/C/D 判据全走原函数，不另写字段比对。"""
    saved_root = ROOT

    good = tempfile.mkdtemp(prefix='report-fields-selftest-')
    try:
        gdocs = os.path.join(good, 'docs', '锚点')
        os.makedirs(gdocs, exist_ok=True)
        open(os.path.join(gdocs, 'selftest-good.md'), 'w', encoding='utf-8').write('# 锚点卡\n')
        anchors = '\n'.join([
            'data_scripts/check-no-idle.py:23',
            'data_scripts/check-queue-running.py:15',
            'data_scripts/check-dispatch-queue.py:35',
            'data_scripts/check-report-fields.py:68',
            'data_scripts/check-onboarding.py:77',
        ])
        body = ("锚点卡：docs/锚点/selftest-good.md\n\n"
                "总结论：PASS\n\n"
                "## 自查反例\n反例推演：若放宽锚点计数，空口结论会蒙混过关。\n\n"
                "证据锚点：\n" + anchors + "\n")
        rel_good = 'docs/修复-selftest-good.md'
        open(os.path.join(good, rel_good), 'w', encoding='utf-8').write(body)
        globals()['ROOT'] = good
        fails_good = check(rel_good)
        assert fails_good == [], f"四件套齐全的报告应 PASS，fails={fails_good}"
        print('[selftest] 好样本（锚点卡+结论+反例+5 处锚点）PASS 通路 OK')
    finally:
        globals()['ROOT'] = saved_root
        shutil.rmtree(good, ignore_errors=True)

    bad = tempfile.mkdtemp(prefix='report-fields-selftest-')
    try:
        os.makedirs(os.path.join(bad, 'docs'), exist_ok=True)
        rel_bad = 'docs/修复-selftest-bad.md'
        # 坏样本：首行无锚点卡、无结论标记、无「自查反例/改坏」、0 处文件:行锚点，A/B/C/D 全中
        open(os.path.join(bad, rel_bad), 'w', encoding='utf-8').write(
            '# 修复-selftest-bad\n只有一段正文，缺锚点卡、缺结论、缺反例推演、缺证据锚点。\n')
        globals()['ROOT'] = bad
        fails_bad = check(rel_bad)
        assert fails_bad, f"字段全缺的报告应 FAIL，fails={fails_bad}"
        print('[selftest] 坏样本（四件套全缺）FAIL 通路 OK：' + '；'.join(fails_bad))
    finally:
        globals()['ROOT'] = saved_root
        shutil.rmtree(bad, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


def main():
    if '--selftest' in sys.argv:
        return selftest()
    paths = sys.argv[1:] or changed_reports()
    if not paths:
        print("REPORT-FIELDS: PASS（本轮无报告改动）")
        return 0
    bad = 0
    for p in paths:
        if is_fixture(p):
            continue
        fails = check(p)
        if fails:
            bad += 1
            print(f"FAIL {p}")
            for f in fails:
                print(f"    {f}")
        else:
            print(f"PASS {p}")
    print(f"REPORT-FIELDS: {'PASS' if bad == 0 else 'FAIL'}（{len(paths)} 份，{bad} 份不合格）")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
