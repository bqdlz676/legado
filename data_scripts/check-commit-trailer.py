#!/usr/bin/env python3
"""门禁 ㊴ 提交认证书尾巡检（F-111，WARN 型，不作 push 闸门）。

为什么：两条 push 纪律（槽永不空 vs 攒批等终态）过去都靠人记；认证书尾让 git log 能机械追溯
"这个 commit 验没验"。但验收没过的 commit 也要能进云端保槽，硬 FAIL 会顶掉"保槽"铁律，
故本脚本只打 WARN：缺 trailer 打印短号与建议，退出码恒 0。
trailer（写在 message 末尾空一行后）：
  - `Verified-by: <验收代理id>` 独立验收通过（写码代理自评无效，由复核兜）
  - `Wip: <未验收原因>` 未验收保槽
  - `Docs-only: <说明>` 纯文档/规则/台账单，免验收代理
判据：扫 `git log origin/main..HEAD`；远端 ref 不可解析时回退最近 5 条（历史留档不追改，仅提示）。
用法：`python3 data_scripts/check-commit-trailer.py`（门禁 ㊴ 以 say 展示，不 bad）。
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

TRAILER_RE = re.compile(r'^(Verified-by|Wip|Docs-only):\s*(.*)$')
SUGGEST = ('建议补 trailer（三选一，写在 message 末尾空一行后）：'
           '`Verified-by: <验收代理id>`（验收已过）/ '
           '`Wip: <未验收原因>`（未验收保槽）/ '
           '`Docs-only: <说明>`（纯文档/规则单）')


def git(repo: str, *args) -> tuple[int, str]:
    r = subprocess.run(['git', *args], cwd=repo, capture_output=True, text=True)
    return r.returncode, r.stdout


def collect(repo: str) -> tuple[list[tuple[str, str, str]], bool, bool]:
    """返回 [(短号, 主题, 正文), 是否回退窗口, git 是否可用]。

    git log 返回码必须检查：调用失败时 out 为空，旧实现会落到空集分支被当成
    "无提交" 放行；无法核验应与 provenance 门禁同口径视为未过（Q-257 收紧）。
    """
    rc, _ = git(repo, 'rev-parse', '--verify', 'origin/main')
    if rc == 0:
        rng, fallback = 'origin/main..HEAD', False
    else:
        rng, fallback = 'HEAD~5..HEAD', True
    rc, out = git(repo, 'log', rng, '--format=%h%x1f%s%x1f%b%x1e')
    if rc != 0:
        return [], fallback, False
    commits: list[tuple[str, str, str]] = []
    for rec in out.split('\x1e'):
        rec = rec.strip('\n')
        if not rec.strip():
            continue
        parts = rec.split('\x1f')
        if len(parts) < 3:
            continue
        commits.append((parts[0].strip(), parts[1].strip(), parts[2]))
    return commits, fallback, True


def classify(body: str) -> tuple[str, list[tuple[str, str, str]]]:
    """返回 (类别, 命中行列表)。类别：Verified-by / Wip / Docs-only / MISSING / INVALID。"""
    hits: list[tuple[str, str, str]] = []
    for line in body.splitlines():
        m = TRAILER_RE.match(line.strip())
        if m:
            hits.append((m.group(1), m.group(2).strip(), line.strip()))
    if not hits:
        return 'MISSING', []
    if len(hits) > 1:
        return 'INVALID', hits
    kind, val, _ = hits[0]
    if kind == 'Verified-by' and not val:
        return 'INVALID', hits
    return kind, hits


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('repo', nargs='?', default='.')
    args = ap.parse_args()

    commits, fallback, git_ok = collect(args.repo)
    if not git_ok:
        print('COMMIT-TRAILER: FAIL（git log 不可用，无法核验认证书尾，不得按空集放行）')
        return 1
    if not commits:
        # 空集是真实"窗口内无提交"，只报 INFO，不表述为合规结论（Q-257 收紧）。
        print('COMMIT-TRAILER: INFO（窗口内无提交，无可核验对象）')
        return 0

    rows = []
    for sha, subject, body in commits:
        kind, hits = classify(body)
        rows.append((sha, subject, kind, hits))

    bad = [r for r in rows if r[2] in ('MISSING', 'INVALID')]
    wip = [r for r in rows if r[2] == 'Wip']
    verified = [r for r in rows if r[2] in ('Verified-by', 'Docs-only')]

    if not bad:
        print(f'COMMIT-TRAILER: PASS（{len(rows)} 条未推提交：verified/docs={len(verified)} wip={len(wip)}）')
        return 0

    scope = '回退窗口历史' if fallback else '未推提交'
    print(f'COMMIT-TRAILER: WARN（{len(bad)}/{len(rows)} 条{scope}缺/不合规认证书尾；仅提示，不阻断 push）')
    for sha, subject, kind, hits in bad:
        if kind == 'INVALID':
            print(f'  {sha} {subject} -> INVALID（trailer 多行或 Verified-by 空值：{[h[0] for h in hits]}）')
        else:
            print(f'  {sha} {subject} -> MISSING（末尾无 Verified-by/Wip/Docs-only trailer）')
        print(f'    {SUGGEST}')
    return 0


def _git(repo: str, *args) -> subprocess.CompletedProcess:
    return subprocess.run(['git', '-c', 'core.quotepath=false', *args],
                          cwd=repo, capture_output=True, text=True)


def _make_repo(commits: list[tuple[str, dict]]) -> str:
    """临时 git 仓库夹具：commits 为 [(提交信息, {相对路径: 内容})]。

    为什么在 /tmp 建真 git 仓库：collect() 走 git rev-parse / git log 子进程，
    纯目录夹具触发不了提交枚举；身份用 -c 内联配置，不写全局 gitconfig。"""
    repo = tempfile.mkdtemp(prefix='commit-trailer-selftest-')
    _git(repo, 'init', '-q')
    for msg, files in commits:
        for rel, content in files.items():
            p = os.path.join(repo, rel)
            if os.path.dirname(rel):
                os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, 'w', encoding='utf-8').write(content)
        _git(repo, 'add', '-A')
        _git(repo, '-c', 'user.email=selftest@example.com', '-c', 'user.name=selftest',
             'commit', '-q', '-m', msg)
    # 挂本地 origin/main 到首提交：rev-parse origin/main 成功后走 origin/main..HEAD 窗口，
    # 避免单提交仓库回退 HEAD~5..HEAD 时 HEAD~5 不可解析、误触 git 不可用 FAIL 通路。
    if len(commits) >= 2:
        _git(repo, 'branch', '-f', 'origin/main', 'HEAD~1')
    return repo


def _run(argv: list[str]) -> int:
    old = sys.argv
    sys.argv = ['check-commit-trailer.py'] + argv
    try:
        return main()
    finally:
        sys.argv = old


def selftest() -> int:
    """双向自测：git log 不可用 → FAIL rc=1；合规 trailer → PASS rc=0；
    缺/不合规 trailer → WARN rc=0（WARN-only 设计，不阻断 push）。
    夹具全部 tempfile.mkdtemp + rmtree 自删；判定回调生产 main()/collect()。"""
    # 坏样本一：非 git 目录 → rev-parse 与 git log 均失败，命中「git 不可用不许按空集放行」FAIL 通路
    bad_git = tempfile.mkdtemp(prefix='commit-trailer-selftest-nogit-')
    try:
        rc_b1 = _run([bad_git])
        assert rc_b1 == 1, f"git 不可用应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（非 git 目录，git log 不可用）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad_git, ignore_errors=True)

    # 好样本：纯文档单带 Docs-only trailer → classify 合规，PASS 通路
    good = _make_repo([('chore: 基线', {'docs/base.md': '# base\n'}),
                       ('docs: 夹具说明\n\nDocs-only: 夹具纯文档单', {'docs/note.md': '# 笔记\n'})])
    try:
        rc_good = _run([good])
        assert rc_good == 0, f"Docs-only trailer 应 PASS，rc={rc_good}"
        print('[selftest] 好样本（Docs-only 认证书尾）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本二：提交无任何 trailer → MISSING，WARN 通路（WARN-only 设计，rc=0）
    warn_missing = _make_repo([('chore: 基线', {'docs/base.md': '# base\n'}),
                               ('无尾注提交', {'docs/a.md': '# a\n'})])
    try:
        rc_w1 = _run([warn_missing])
        assert rc_w1 == 0, f"缺 trailer 仅 WARN 应 rc=0，rc={rc_w1}"
        print('[selftest] 坏样本二（缺 trailer）WARN 通路 OK（WARN-only 不阻断）')
    finally:
        shutil.rmtree(warn_missing, ignore_errors=True)

    # 坏样本三：两条 trailer 并列 → INVALID，WARN 通路
    warn_invalid = _make_repo([('chore: 基线', {'docs/base.md': '# base\n'}),
                               ('双尾注提交\n\nVerified-by: s_acc1\nWip: 保槽', {'docs/b.md': '# b\n'})])
    try:
        rc_w2 = _run([warn_invalid])
        assert rc_w2 == 0, f"多 trailer 仅 WARN 应 rc=0，rc={rc_w2}"
        print('[selftest] 坏样本三（trailer 多行 INVALID）WARN 通路 OK（WARN-only 不阻断）')
    finally:
        shutil.rmtree(warn_invalid, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main())
