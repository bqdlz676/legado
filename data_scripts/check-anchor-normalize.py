#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 g74 · 锚点规范化门（G-12 / M-32 书写规范缺口）。

为什么立这条（见 docs/台账-三件缺项.md G-12、docs/台账-机制缺陷.md M-32、
docs/修复-crossing-companion嵌套.md:3-4）：
  M-32 遗留：既有锚点闸 g62 check-line-anchors.py 只判锚点"能不能回源"（死文件 /
  越界行 / 落点空白），不管锚点"写得规不规范"。报告里仍允许两种未全路径化的写法：
  ① 裸行号 `:NN` / `:NN-MM`（同行/上文没给文件名，靠最近上下文猜归属）；
  ② 裸基名 `Foo.kt:NN`（只有文件名，不带仓内目录路径）。这类书写在锚点维护（路径
  补全）时最容易指错对象，M-32 当时只能靠头部两条声明绕过 ⑲/⑭ 判红，缺一条
  专门认"锚点规范化"类别的机判据。

与 g62 的分工（不重复判定）：
  - g62：锚点指向的文件是否存在、行号是否越界、落点是否空白/注释（回源有效性）。
  - 本闸 g74：锚点书写形态是否全路径化（规范性）；不判断文件死活、不判行号越界。
    同一条锚点 g62 判有效性、g74 判写法，两类结论互不替代。

判据（只扫本轮改动 ∪ 未推送报告，历史留档不追改，边界同 ⑭/㊽）：
  N1 BARE-LINE：裸 `:NN` / `:NN-MM`，前面不是文件名/路径（时间 `16:45` 由数字
    前导排除）→ 书写缺口。
  N2 BARE-NAME：`基名.扩展名:NN` 不含目录斜杠 → 未全路径化。
  已规范化锚点（含 `/` 的仓内相对路径 `docs/x.md:5` / `app/.../Foo.kt:12`）放行。

适用边界 / 误判面 / 不能替代什么：
  - 排除代码围栏块、`docs/落文-规格切片/`、`docs/锚点/`、`docs/落文-原文/`（同 g62
    惯例：切片/卡表/底稿不参与报告锚点判定）。
  - 正则只认常见代码/文档扩展名；配置/资源新扩展名未收录时会漏判，收口全量 gate 补。
  - 本闸不判锚点语义归属对不对、不判漂移；那归 g62 与人工回源。
  - 全仓历史报告的存量裸锚点不追改（同 ⑭/⑮ 存量豁免口径），只拦本轮新增/改动写法。

退出码：0=PASS | 1=FAIL | 2=参数错 | 3=UNVERIFIED（无待检报告）
用法：
  python3 data_scripts/check-anchor-normalize.py
  python3 data_scripts/check-anchor-normalize.py --selftest
"""
from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / 'docs'
EXCLUDE_DIRS = {'落文-规格切片', '锚点', '落文-原文'}
REPORT_RE = re.compile(r'^docs/(修复|验收|实现|交付|机制|规格|复盘)-.*\.md$')

EXT = r'(?:kt|md|py|xml|json|yml|yaml|txt|html|css|js|ts|toml)'
# 已规范化：路径里含斜杠的仓内相对路径 文件:行（必须有目录段，否则算裸基名）。
FULL_ANCHOR_RE = re.compile(r'[\w\-/\\.]+/[\w\-\\.]+\.\w+:\d+(?:-\d+)?')
# 裸基名锚点：基名.扩展名:行号，token 内不含斜杠。
BARE_NAME_RE = re.compile(r'(?<![\w/\\.])([\w\-]+\.%s):(\d+)(?:-\d+)?' % EXT)
# 裸行号：冒号+数字，前导不是数字（排时间）、不是路径/文件名/另一个冒号。
BARE_LINE_RE = re.compile(r'(?<!\d)(?<![\w/.:]):(\d{2,4})(?:-\d{2,4})?')


def collect_reports(root: pathlib.Path) -> list[str]:
    """本轮改动 ∪ 未推送报告（口径同 ㊽ check-unpushed-reports.py）。"""
    out: set[str] = set()

    def git(*args: str) -> subprocess.CompletedProcess:
        return subprocess.run(['git', '-c', 'core.quotepath=false', *args], cwd=str(root),
                              capture_output=True, text=True)

    base = git('rev-parse', '--verify', 'origin/main')
    if base.returncode == 0:
        for line in git('diff', '--diff-filter=A', '--name-only', 'origin/main..HEAD').stdout.splitlines():
            if REPORT_RE.match(line.strip()):
                out.add(line.strip())
    else:
        for line in git('diff', '--name-only', 'HEAD~1..HEAD').stdout.splitlines():
            if REPORT_RE.match(line.strip()):
                out.add(line.strip())
    for row in git('status', '--porcelain').stdout.splitlines():
        p = row[3:].strip().strip('"')
        if REPORT_RE.match(p):
            out.add(p)
    return sorted(out)


def normalize_line(line: str) -> str:
    """把已规范化的全路径锚点抠掉，避免裸基名正则在长路径里重复命中基名段。"""
    return FULL_ANCHOR_RE.sub(' ', line)


def scan_text(rel: str, text: str) -> list[str]:
    hits: list[str] = []
    in_fence = False
    for lineno, line in enumerate(text.splitlines(), 1):
        if line.lstrip().startswith('```'):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        stripped = normalize_line(line)
        for m in BARE_NAME_RE.finditer(stripped):
            hits.append('%s:%s 裸基名锚点 `%s:%s` 未全路径化'
                        % (rel, lineno, m.group(1), m.group(2)))
        for m in BARE_LINE_RE.finditer(stripped):
            hits.append('%s:%s 裸行号锚点 `:%s` 未挂文件路径' % (rel, lineno, m.group(1)))
    return hits


def run(root: pathlib.Path, reports: list[str] | None = None) -> tuple[int, list[str]]:
    if reports is None:
        reports = collect_reports(root)
    fails: list[str] = []
    for rel in reports:
        if any(d in rel for d in EXCLUDE_DIRS):
            continue
        full = root / rel
        if not full.is_file():
            continue
        fails += scan_text(rel, full.read_text(encoding='utf-8', errors='replace'))
    if not reports:
        return 3, []
    return (1 if fails else 0), fails


def selftest() -> int:
    """双向自测：裸行号 / 裸基名两类坏样本判红；全路径好样本放行。"""
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='anchor-norm-selftest-'))
    (tmp / 'docs').mkdir(parents=True)
    bad = (
        '# 修复-坏样本\n\n'
        '见 修复-crossing.md:17 与 :206 两处锚点；另见 Foo.kt:13 佐证。\n'
    )
    good = (
        '# 验收-好样本\n\n'
        '依据 `docs/落文-规矩总纲.md:5` 与 `app/src/main/java/io/legado/app/x/Foo.kt:13`。\n'
    )
    (tmp / 'docs/修复-坏样本.md').write_text(bad, encoding='utf-8')
    (tmp / 'docs/验收-好样本.md').write_text(good, encoding='utf-8')

    code, fails = run(tmp, reports=['docs/修复-坏样本.md', 'docs/验收-好样本.md'])
    assert code == 1, f'坏样本应 FAIL，实际 code={code}'
    assert any('裸行号' in f for f in fails), f'应抓到裸行号：{fails}'
    assert any('裸基名' in f for f in fails), f'应抓到裸基名：{fails}'
    assert not any('好样本' in f for f in fails), f'好样本被误判：{fails}'
    print('SELFTEST: PASS（裸行号判红、裸基名判红、全路径锚点放行）')
    import shutil
    shutil.rmtree(tmp)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description='锚点规范化门（G-12 / M-32）')
    ap.add_argument('--selftest', action='store_true')
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    code, fails = run(ROOT)
    print('== 锚点规范化门（G-12）==')
    if code == 3:
        print('ANCHOR-NORM: UNVERIFIED（本轮无待检报告，未验证不算通过）')
        return 3
    for f in fails:
        print('   ✗ ' + f)
    print('ANCHOR-NORM: PASS' if code == 0 else 'ANCHOR-NORM: FAIL')
    return code


if __name__ == '__main__':
    sys.exit(main())
