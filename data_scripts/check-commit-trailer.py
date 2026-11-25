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
import re
import subprocess
import sys

TRAILER_RE = re.compile(r'^(Verified-by|Wip|Docs-only):\s*(.*)$')
SUGGEST = ('建议补 trailer（三选一，写在 message 末尾空一行后）：'
           '`Verified-by: <验收代理id>`（验收已过）/ '
           '`Wip: <未验收原因>`（未验收保槽）/ '
           '`Docs-only: <说明>`（纯文档/规则单）')


def git(repo: str, *args) -> tuple[int, str]:
    r = subprocess.run(['git', *args], cwd=repo, capture_output=True, text=True)
    return r.returncode, r.stdout


def collect(repo: str) -> tuple[list[tuple[str, str, str]], bool]:
    """返回 [(短号, 主题, 正文), 是否回退窗口]。"""
    rc, _ = git(repo, 'rev-parse', '--verify', 'origin/main')
    if rc == 0:
        rng, fallback = 'origin/main..HEAD', False
    else:
        rng, fallback = 'HEAD~5..HEAD', True
    rc, out = git(repo, 'log', rng, '--format=%h%x1f%s%x1f%b%x1e')
    commits: list[tuple[str, str, str]] = []
    for rec in out.split('\x1e'):
        rec = rec.strip('\n')
        if not rec.strip():
            continue
        parts = rec.split('\x1f')
        if len(parts) < 3:
            continue
        commits.append((parts[0].strip(), parts[1].strip(), parts[2]))
    return commits, fallback


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

    commits, fallback = collect(args.repo)
    if not commits:
        print('COMMIT-TRAILER: PASS（无未推提交）')
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


if __name__ == '__main__':
    sys.exit(main())
