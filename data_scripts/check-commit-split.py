#!/usr/bin/env python3
"""门禁 ㊵ 提交路径分离巡检（F-112）。

为什么：结单/台账/文档类 commit 必须与代码类 commit 逐路径分开。事故 E-05 实证：调度层用
`git add -A` 结单，把在飞子代理尚未收口的代码扫进自己的 commit，导致 commit 与单号无法一一
回源。这条把"混合提交"变成机械判据，不靠人记。
判据：扫最近 N 个 commit（默认 5，`--count` 可调），逐条取 `--name-only` 名单：
      同一个 commit 同时含 `docs/` 路径与 `app/src/` 路径 → FAIL，打印短哈希、摘要与两侧路径；
      全部分离 → PASS。
边界：
  - 单文件紧急修复允许混合，但须在 commit message 写明（F-112 例外）；本脚本只做路径机械判据，
    豁免登记归人工台账，脚本不读 message 内容放水。
  - 纯标准库实现：不依赖网络、JDK、gradle；只读本地 git 历史。git 不可用/不可解析时按 FAIL
    处理——无法核验即视为未通过，避免静默放绿。
  - `--commit <sha>` 为定向复核/自测入口：只检查指定单条 commit，忽略 `--count`。
用法：`python3 data_scripts/check-commit-split.py [--count N] [--commit <sha>] [repo]`。
"""
import argparse
import subprocess
import sys

# 路径前缀判据：docs/ = 文档/台账/规则侧；app/src/ = 代码侧（main 与 test 同属代码，一并计）
DOCS_PREFIX = 'docs/'
CODE_PREFIX = 'app/src/'


def git(repo: str, *args):
    # core.quotepath=false：中文路径默认被 git 转义成八进制串，不放开就匹配不到 docs/ 前缀
    r = subprocess.run(['git', '-c', 'core.quotepath=false', *args],
                       cwd=repo, capture_output=True, text=True)
    return r.returncode, r.stdout


def commit_paths(repo: str, sha: str):
    # diff-tree 是 plumbing 命令，只给路径名单，不掺 diff 内容，口径与 --name-only 一致
    rc, out = git(repo, 'diff-tree', '--no-commit-id', '--name-only', '-r', sha)
    if rc != 0:
        return []
    return [line.strip() for line in out.splitlines() if line.strip()]


def split_paths(paths):
    docs = [p for p in paths if p.startswith(DOCS_PREFIX)]
    code = [p for p in paths if p.startswith(CODE_PREFIX)]
    return docs, code


def inspect(repo: str, sha: str, subject: str):
    """返回 (是否分离, 命中描述行)；混合时描述行列出两侧路径供回源。"""
    docs, code = split_paths(commit_paths(repo, sha))
    if docs and code:
        line = (f'  {sha[:7]} {subject} -> 混合结单与代码：'
                f'docs={docs} code={code}')
        return False, line
    return True, None


def check_single(repo: str, sha: str) -> int:
    rc, out = git(repo, 'log', '-1', '--format=%H%x1f%s', sha)
    if rc != 0 or not out.strip():
        print(f'COMMIT-SPLIT: FAIL（无法解析 commit {sha}）')
        return 1
    full, subject = out.strip().split('\x1f', 1)
    ok, line = inspect(repo, full, subject)
    if not ok:
        print(line)
        print(f'COMMIT-SPLIT: FAIL（{full[:7]} 同时含 docs/ 与 app/src/，结单与代码未分离）')
        return 1
    print(f'COMMIT-SPLIT: PASS（{full[:7]} {subject} 路径已分离）')
    return 0


def check_recent(repo: str, count: int) -> int:
    rc, out = git(repo, 'log', f'-n{max(1, count)}', '--format=%H%x1f%s')
    if rc != 0:
        print('COMMIT-SPLIT: FAIL（git log 不可用，无法核验提交路径分离）')
        return 1
    commits = []
    for rec in out.splitlines():
        if '\x1f' in rec:
            full, subject = rec.split('\x1f', 1)
            commits.append((full.strip(), subject.strip()))
    if not commits:
        print('COMMIT-SPLIT: PASS（无历史 commit 可检）')
        return 0

    mixed = []
    for full, subject in commits:
        ok, line = inspect(repo, full, subject)
        if not ok:
            mixed.append(line)
    for line in mixed:
        print(line)
    if mixed:
        print(f'COMMIT-SPLIT: FAIL（{len(mixed)}/{len(commits)} 条 commit 混合 docs/ 与 '
              f'app/src/，须逐路径分开提交）')
        return 1
    print(f'COMMIT-SPLIT: PASS（最近 {len(commits)} 条 commit 文档/代码已分离）')
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('repo', nargs='?', default='.')
    ap.add_argument('--count', type=int, default=5, help='扫描最近 N 条 commit（默认 5）')
    ap.add_argument('--commit', help='定向检查指定 commit（自测/复核用），忽略 --count')
    args = ap.parse_args()

    if args.commit:
        return check_single(args.repo, args.commit)
    return check_recent(args.repo, args.count)


if __name__ == '__main__':
    sys.exit(main())
