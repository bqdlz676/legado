#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 g70 · 空转盘点门（G-01 / F-72 三类分账机检补全）。

为什么立这条（见 docs/台账-三件缺项.md G-01、docs/规则总账.md F-72）：
  2026-11-24 空转盘点 10 例里，三类形态此前只有人工台账：① 连续 assistant 轮零工具调用；
  ② 报告声称已落地的产物路径 `ls` 不到；③ 承诺句/拒答句（"稍后补/待补/暂不处理/
  无法验证"）同句既无缺陷号也无台账条目。现行 ㉗ check-agent-liveness.py 只扫当前会话
  首轮零工具调用，⑭ check-evidence.py 只查回执 E1/E2/E3 条数、不核产物路径是否真实存在；
  承诺句/拒答句与历史回溯盘点长期靠人工。本闸把这三类补成机判，缺件台账 G-01 由此闭环。

三类判据：
  ① IDLE-SEGMENT：轨迹（~/.doubao/.../agents/<id>/system/trajectory.jsonl）里出现
     连续 ≥2 个零工具调用的 assistant 轮、且其后轨迹仍在推进（不是收尾段）→ FAIL。
     已登记在 docs/台账-空转盘点.md / docs/台账-子代理存活.md 的 id 视为已闭环留档，豁免。
  ② PRODUCT-MISSING：本轮改动 ∪ 未推送报告（口径同 ㊽ check-unpushed-reports.py）里
     反引号包裹的仓内产物路径，按仓根 `ls` 不到 → FAIL。源码证据锚点的死文件/越界行归
     g62 check-line-anchors.py 判，本闸不重复判那一类。
  ③ PROMISE-UNREGISTERED：报告句子含承诺/拒答词，同句既无缺陷号（F-/Q-/B-/C-/D-/RC-/
     DEF-/M-/OB-）也无台账/清单/登记字样 → FAIL（承诺落不了地就会变成假完成）。

适用边界 / 误判面 / 不能替代什么：
  - 只判本轮改动与未推送报告（历史留档不追改，边界同 ⑭/⑮/㊽）；全仓历史报告的回溯盘点
    仍由 docs/台账-空转盘点.md 人工兜底，本闸不拿存量当门禁。
  - 轨迹目录不可读 / 本轮无待检报告时判 UNVERIFIED（退出码 3），不是 PASS：没查到不等于
    合规（同 g69 三态口径）。
  - ① 只看 tool_calls 列表与 role=tool 行，判不到"调了工具但工具返回空转"的语义空转；
    ③ 词表是正则匹配，复合否定句（"并非无法验证"）需人工复核，误判面在复合句。
  - 本闸不替代 ㉗ 首轮存活校验、不替代 ⑭ 回执证据三要素；三者判据面互补不重叠。

退出码：0=PASS | 1=FAIL | 2=参数错 | 3=UNVERIFIED（无轨迹目录 / 无待检对象）
用法：
  python3 data_scripts/check-idle-scan.py
  python3 data_scripts/check-idle-scan.py --selftest
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import pathlib
import re
import sys
import tempfile
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / 'docs'
IDLE_LEDGER = DOCS / '台账-空转盘点.md'
LIVENESS_LEDGER = DOCS / '台账-子代理存活.md'

# 承诺句 / 拒答句词表：说"以后再做 / 现在处理不了"但没挂任何在册凭据。
PROMISE_RE = re.compile(
    r'稍后补|待补|暂不处理|无法验证|无法确认|后续补|回头补|暂未处理|待后续|'
    r'稍后再补|先不处理|暂不确认|待补登|后续再补'
)
# 在册凭据号：命中其一即说明承诺挂到了具体条目上。
TICKET_RE = re.compile(
    r'F-\d+|Q-\d+|B\d+|C-\d+|D\d+|RC-\d+|DEF-\d+|M-\d+|OB-\d+|N-\d+'
)
LEDGER_WORD_RE = re.compile(r'台账|清单|登记|在册|落账|入队|留档')
# 反引号包裹的产物路径：只判 docs/ 交付文档（F-72 第②类原事故＝"声称补的报告 ls 不到"）。
# 源码/脚本证据锚点（.kt/.py 等）的死文件、越界行归 g62 check-line-anchors.py 判，本闸不重复；
# 裸基名 `Foo.kt` 同样归 g62 按文件名反查。锚点 :NN 后缀另行剥掉。
BACKTICK_PATH_RE = re.compile(
    r'`(docs/[\w\-/\\.]+\.md)(?::\d+(?:-\d+)?)?`'
)
REPORT_RE = re.compile(r'^docs/(修复|验收|实现|交付|机制|规格|复盘)-.*\.md$')
# 轨迹审窗：只审近 48 小时动过的代理（历史留档不追改，同 ㉗ 口径）。
TRAJ_WINDOW_HOURS = float(os.environ.get('IDLE_SCAN_WINDOW_HOURS', '48'))


def registered_ids() -> set[str]:
    """两份存活/盘点台账里出现过的代理 id → 已闭环豁免集合。"""
    ids: set[str] = set()
    for p in (IDLE_LEDGER, LIVENESS_LEDGER):
        if p.is_file():
            ids |= set(re.findall(r's_[0-9A-Za-z]+', p.read_text(encoding='utf-8', errors='replace')))
    return ids


def session_agent_dirs() -> list[str]:
    home = os.path.expanduser('~')
    cands = glob.glob(os.path.join(home, '.doubao', 'agent_mode', 'workspace', '.sessions', '*', 'agents'))
    if not cands:
        return []

    def latest_mtime(base: str) -> float:
        sub = glob.glob(os.path.join(base, 's_*'))
        return max((os.path.getmtime(d) for d in sub), default=0.0)

    best = max(cands, key=latest_mtime)
    return [best]


def read_trajectory(path: pathlib.Path) -> list[dict]:
    rows: list[dict] = []
    try:
        with path.open(encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    rows.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    except FileNotFoundError:
        pass
    return rows


def scan_trajectories(agents_root: str | None, registered: set[str]) -> list[str]:
    """① 零工具调用段扫描。返回失败描述列表；空列表 = 本类无命中。"""
    fails: list[str] = []
    if not agents_root or not os.path.isdir(agents_root):
        return fails
    now = time.time()
    for agent_dir in sorted(glob.glob(os.path.join(agents_root, 's_*'))):
        agent_id = os.path.basename(agent_dir)
        if agent_id in registered:
            continue
        traj = pathlib.Path(agent_dir) / 'system' / 'trajectory.jsonl'
        if not traj.is_file():
            continue
        if now - traj.stat().st_mtime > TRAJ_WINDOW_HOURS * 3600:
            continue
        rows = read_trajectory(traj)
        if not rows:
            continue
        run = 0  # 当前自上个 tool 行以来，零工具调用的 assistant 轮数
        closed_runs: list[int] = []
        for row in rows:
            role = row.get('role')
            if role == 'tool':
                if run >= 2:
                    closed_runs.append(run)
                run = 0
            elif role == 'assistant':
                calls = row.get('tool_calls') or []
                if isinstance(calls, list) and calls:
                    run = 0  # 本轮有工具动作，段断开
                else:
                    run += 1
        # 轨迹末尾的收尾段不计（最后一条 assistant 本就该纯文本收口）。
        for n in closed_runs:
            fails.append(f'{agent_id} 轨迹含 {n} 连轮零工具调用段（其后仍有推进，非收尾）')
    return fails


def split_sentences(text: str) -> list[str]:
    return [s for s in re.split(r'[。；;\n]+', text) if s.strip()]


def scan_report(root: pathlib.Path, rel: str) -> tuple[list[str], list[str]]:
    """对单份报告判 ② 产物路径缺失 与 ③ 承诺句未登记。返回 (misses, promises)。"""
    misses: list[str] = []
    promises: list[str] = []
    full = root / rel
    if not full.is_file():
        return misses, promises
    text = full.read_text(encoding='utf-8', errors='replace')
    for line_no, line in enumerate(text.splitlines(), 1):
        for m in BACKTICK_PATH_RE.finditer(line):
            token = m.group(1)
            # 只判仓内相对路径；URL / 绝对系统路径不在本闸范围。
            if token.startswith(('http://', 'https://', '/')):
                continue
            if not (root / token).exists():
                misses.append(f'{rel}:{line_no} 产物路径 `{token}` ls 不到')
        for sent in split_sentences(line):
            if PROMISE_RE.search(sent) and not TICKET_RE.search(sent) \
                    and not LEDGER_WORD_RE.search(sent):
                promises.append(f'{rel}:{line_no} 承诺/拒答句未挂在册凭据：{sent.strip()[:60]}')
    return misses, promises


def collect_reports(root: pathlib.Path) -> list[str]:
    """本轮改动 ∪ 未推送报告（口径同 ㊽）。临时仓库无 origin/main 时退回 HEAD~1..HEAD。"""
    import subprocess

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


def run(root: pathlib.Path, reports: list[str] | None = None,
        agents_root: str | None = None) -> tuple[int, list[str], list[str], list[str]]:
    """返回 (退出码, ①失败, ②缺失, ③承诺)。退出码 3 = 无可检对象 / 轨迹不可用。"""
    registered = registered_ids()
    seg_fails = scan_trajectories(agents_root, registered)
    if reports is None:
        reports = collect_reports(root)
    misses: list[str] = []
    promises: list[str] = []
    for rel in reports:
        m, p = scan_report(root, rel)
        misses += m
        promises += p
    fails = seg_fails + misses + promises
    if not reports and not seg_fails:
        return 3, seg_fails, misses, promises
    return (1 if fails else 0), seg_fails, misses, promises


def selftest() -> int:
    """双向自测：三类坏样本各一必须 FAIL；一类好样本必须 PASS；夹具跑完自删。"""
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='idle-scan-selftest-'))
    agents = tmp / 'agents'
    (tmp / 'docs').mkdir(parents=True)

    def write(rel: str, text: str) -> None:
        p = tmp / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding='utf-8')

    # 坏样本 ①：轨迹两连轮零工具调用、其后再有 tool 行（非收尾段）。
    ag = agents / 's_bad1' / 'system'
    ag.mkdir(parents=True)
    (ag / 'trajectory.jsonl').write_text('\n'.join([
        json.dumps({'role': 'user', 'content': '做单'}, ensure_ascii=False),
        json.dumps({'role': 'assistant', 'tool_calls': [], 'content': '我先想想'}, ensure_ascii=False),
        json.dumps({'role': 'assistant', 'tool_calls': [], 'content': '再想想'}, ensure_ascii=False),
        json.dumps({'role': 'tool', 'tool_call_id': 'x', 'content': '回证'}, ensure_ascii=False),
        json.dumps({'role': 'assistant', 'tool_calls': [{'id': 't'}], 'content': '收尾'}, ensure_ascii=False),
    ]) + '\n', encoding='utf-8')
    # 坏样本 ②：报告声称的产物路径不存在。
    write('docs/修复-坏样本产物.md',
          '# 修复-坏样本产物\n\n产物已落地：`docs/修复-不存在的产物.md`。\n')
    # 坏样本 ③：承诺句无缺陷号、无台账字样。
    write('docs/验收-坏样本承诺.md',
          '# 验收-坏样本承诺\n\n该处样式稍后补，本轮先交付主链路。\n')
    # 好样本：产物路径真实存在；承诺句挂了缺陷号。
    write('docs/修复-真实产物.md', '# 修复-真实产物\n\n本体：`docs/修复-真实产物.md`。\n')
    write('docs/验收-好样本.md',
          '# 验收-好样本\n\n遗留项待补，已登记 F-118 入账，下一轮收口。\n')

    code, seg, misses, promises = run(tmp, reports=[
        'docs/修复-坏样本产物.md', 'docs/验收-坏样本承诺.md',
        'docs/修复-真实产物.md', 'docs/验收-好样本.md',
    ], agents_root=str(agents))
    assert code == 1, f'三类坏样本应 FAIL，实际 code={code}'
    assert len(seg) == 1, f'① 应抓到 1 条零工具段，实际 {seg}'
    assert any('不存在的产物' in m for m in misses), f'② 应抓到缺失产物：{misses}'
    assert any('稍后补' in p for p in promises), f'③ 应抓到未登记承诺句：{promises}'
    # 好样本两份不得命中任何一类。
    good_misses = [m for m in misses if '真实产物' in m]
    good_prom = [p for p in promises if '好样本' in p]
    assert not good_misses and not good_prom, f'好样本被误判：{good_misses}{good_prom}'
    print('SELFTEST: PASS（① 零工具段判红、② 产物缺失判红、③ 未登记承诺句判红；好样本放行）')

    import shutil
    shutil.rmtree(tmp)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description='空转盘点门（G-01 / F-72）')
    ap.add_argument('--selftest', action='store_true')
    ap.add_argument('--root', help='测试用仓根（默认本仓）')
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    root = pathlib.Path(args.root).resolve() if args.root else ROOT
    agents_root = session_agent_dirs()[0] if session_agent_dirs() else None
    code, seg, misses, promises = run(root, agents_root=agents_root)
    print('== 空转盘点门（G-01）==')
    for label, items in (('① 零工具调用段', seg), ('② 产物路径缺失', misses), ('③ 承诺句未登记', promises)):
        if items:
            print(f'-- {label}：')
            for it in items:
                print(f'   · {it}')
        else:
            print(f'-- {label}：无')
    if code == 3:
        print('IDLE-SCAN: UNVERIFIED（本轮无可检轨迹/报告，未验证不算通过）')
        return 3
    print('IDLE-SCAN: PASS' if code == 0 else 'IDLE-SCAN: FAIL')
    return code


if __name__ == '__main__':
    sys.exit(main())
