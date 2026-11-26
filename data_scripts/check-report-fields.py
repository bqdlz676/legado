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
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def changed_reports():
    out = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT,
                         capture_output=True, text=True,
                         env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}).stdout
    paths = []
    for line in out.splitlines():
        p = line[3:].strip().strip('"')
        if re.match(r"docs/(修复|验收|组装|分析)-.*\.md$", p):
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


def main():
    paths = sys.argv[1:] or changed_reports()
    if not paths:
        print("REPORT-FIELDS: PASS（本轮无报告改动）")
        return 0
    bad = 0
    for p in paths:
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
