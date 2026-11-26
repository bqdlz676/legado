#!/usr/bin/env python3
"""门禁 59 提交来源巡检（F-126 / M-57）——源码文件进提交前，必须能在 docs 报告里回源到单。

为什么需要它（2026-11-26 真事故，commit `5ec4ada`）：总控用目录级 `git add app/src/main
app/src/test` 结单，把子代理尚未验收的在飞产物（`ImportSourceGroup.kt` 等 B34 产物）一并扫进
提交推上远端，新一轮 CI 在编译期即炸（`ImportSourceGroup.kt:40:26 Unresolved reference
'lastPathSegment' on receiver of type 'URI'`），整个分片矩阵 skipped。㊵ 只判"同一 commit 是否
混合 docs 与代码"，判不到"代码提了但对应报告/验收单还没落盘"；这条把"有单有报告才提"变成机械判据。

判据：
  1. 扫最近 N 条 commit（默认 10，`--count` 可调），取窗口内 A（新增）/M（修改）/R（改名）
     的源码文件：`app/src/**/*.kt`、`data_scripts/*.py`、`.github/workflows/*.yml`；删除(D)不计。
  2. 每个源码文件必须在 `docs/**/*.md`（同窗口或更早落盘的现存报告）里被以文件级路径提及：
     全相对路径命中，或带后缀的文件名（`Foo.kt`）命中；只提到所在目录名（如 `bookshelf/`）
     不算数——目录名谁都能写，证明不了该文件有单有报告。
  3. 未被任何 docs 提及的源码文件列入名单并判红；窗口内同一文件只取首次出现的 commit。

边界：
  - 纯标准库实现，只读本地 git 历史与 docs 工作树；git 不可用/不可解析按 FAIL 处理（无法核验
    即视为未通过，避免静默放绿）。
  - 本门禁只做来源回源机械判据；"报告是否真验收过"属验收代理职责，门禁不读报告结论放水。
  - `--selftest`：在 /tmp 造三条夹具 git 仓库（有报告→PASS、无报告→红、仅目录名提及→红）。
用法：`python3 data_scripts/check-commit-provenance.py [--count N] [repo] [--selftest]`。
"""
import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

SOURCE_PREFIXES = ('app/src/', 'data_scripts/', '.github/workflows/')
SOURCE_SUFFIXES = ('.kt', '.py', '.yml')
DOCS_DIR = 'docs'
DEFAULT_WINDOW = 10


def git(repo: str, *args):
    # core.quotepath=false：中文路径默认被 git 转义成八进制串，不放开就匹配不到 docs/ 前缀。
    r = subprocess.run(['git', '-c', 'core.quotepath=false', *args],
                       cwd=repo, capture_output=True, text=True)
    return r.returncode, r.stdout


def is_source(path: str) -> bool:
    return path.startswith(SOURCE_PREFIXES) and path.endswith(SOURCE_SUFFIXES)


def window_commits(repo: str, count: int):
    rc, out = git(repo, 'log', f'-n{max(1, count)}', '--format=%H')
    if rc != 0:
        return None
    return [x.strip() for x in out.splitlines() if x.strip()]


def changed_files(repo: str, sha: str):
    # -z 分隔：状态 \0 旧路径 \0 [新路径]；--root 让首个提交也能列出新增文件；diff-tree 只给名单。
    rc, out = git(repo, 'diff-tree', '-r', '--root', '--no-commit-id', '--name-status', '-z', sha)
    if rc != 0:
        return []
    tokens = out.split('\0')
    res, k = [], 0
    while k < len(tokens) - 1:
        status, path = tokens[k], tokens[k + 1]
        if not status:
            break
        if status[0] in ('R', 'C'):
            res.append((status, tokens[k + 2]))
            k += 3
        else:
            res.append((status, path))
            k += 2
    return res


def docs_corpus(repo: str) -> str:
    root = pathlib.Path(repo)
    parts = []
    d = root / DOCS_DIR
    if d.is_dir():
        for p in sorted(d.rglob('*.md')):
            parts.append(p.read_text(encoding='utf-8', errors='replace'))
    return '\n'.join(parts)


def documented(corpus: str, rel: str) -> bool:
    """文件级回源：全路径命中，或带后缀文件名作为独立 token 命中；目录名提及不算。"""
    base = pathlib.Path(rel).name
    if rel in corpus:
        return True
    pat = rf'(?<![\w/]){re.escape(base)}(?![\w-])'
    return re.search(pat, corpus) is not None


def inspect(repo: str, count: int):
    """返回 (commit 列表或 None, 未回源名单 [(sha, path), ...])。"""
    commits = window_commits(repo, count)
    if commits is None:
        return None, []
    corpus = docs_corpus(repo)
    offenders, seen = [], {}
    for sha in commits:
        for status, path in changed_files(repo, sha):
            if status == 'D' or not path:
                continue
            if not is_source(path) or path in seen:
                continue
            seen[path] = sha
            if not documented(corpus, path):
                offenders.append((sha, path))
    return commits, offenders


def check_recent(repo: str, count: int) -> int:
    commits, offenders = inspect(repo, count)
    if commits is None:
        print('COMMIT-PROVENANCE: FAIL（git log 不可用，无法核验提交来源回源）')
        return 1
    if not commits:
        print('COMMIT-PROVENANCE: INFO（窗口内无 commit，无可核验对象）')
        return 0
    for sha, path in offenders:
        print(f'  - {sha[:7]} {path}（窗口内提交，docs 报告未提及该文件）')
    if offenders:
        print(f'COMMIT-PROVENANCE: FAIL（{len(offenders)}/{len(commits)} 条 commit 窗口内 '
              f'{len(offenders)} 个源码文件无 docs 报告回源，疑似目录级 add 扫入在飞产物）')
        return 1
    print(f'COMMIT-PROVENANCE: PASS（最近 {len(commits)} 条 commit 窗口内源码文件均有 docs 报告回源）')
    return 0


def _init_repo(base: pathlib.Path, name: str, files):
    d = base / name
    d.mkdir(parents=True)
    git(str(d), 'init', '-q')
    git(str(d), 'config', 'user.email', 'selftest@example.com')
    git(str(d), 'config', 'user.name', 'selftest')
    for rel, content, msg in files:
        fp = d / rel
        fp.parent.mkdir(parents=True, exist_ok=True)
        fp.write_text(content, encoding='utf-8')
        git(str(d), 'add', rel)
        git(str(d), 'commit', '-q', '-m', msg)
    return str(d)


def selftest() -> int:
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='commit-provenance-selftest-'))
    try:
        # 夹具 1：有报告（docs 先落盘并写明全路径）→ PASS。
        good = _init_repo(tmp, 'good', [
            ('docs/report.md', '# 单 Q-SELFT\n改动：`app/src/main/java/x/Foo.kt`。\n', 'docs: report'),
            ('app/src/main/java/x/Foo.kt', 'package x\nclass Foo\n', 'src: Foo'),
        ])
        rc_good = check_recent(good, 10)

        # 夹具 2：无报告 → 红，点名 Bar.kt。
        no_report = _init_repo(tmp, 'noreport', [
            ('app/src/main/java/x/Bar.kt', 'package x\nclass Bar\n', 'src: Bar'),
        ])
        _, off_bar = inspect(no_report, 10)
        rc_bar = check_recent(no_report, 10)

        # 夹具 3：docs 只提到目录名 bookshelf/，未提 Baz.kt → 红（目录名不算回源）。
        dir_only = _init_repo(tmp, 'dironly', [
            ('docs/report.md', '# 单 Q-SELFT\n本轮调整 bookshelf/ 目录若干文件。\n', 'docs: dir talk'),
            ('app/src/main/java/x/bookshelf/Baz.kt', 'package x.bookshelf\nclass Baz\n', 'src: Baz'),
        ])
        _, off_baz = inspect(dir_only, 10)
        rc_baz = check_recent(dir_only, 10)

        fail = []
        if rc_good != 0:
            fail.append('夹具1 有报告却判红')
        if rc_bar != 1 or not any(p.endswith('Bar.kt') for _, p in off_bar):
            fail.append('夹具2 无报告未判红/未点名 Bar.kt')
        if rc_baz != 1 or not any(p.endswith('Baz.kt') for _, p in off_baz):
            fail.append('夹具3 仅目录名提及未判红/未点名 Baz.kt')
        # 直接证伪目录名匹配：corpus 只含目录串时 documented() 必须为 False。
        if documented('bookshelf/ 目录调整', 'app/src/main/java/x/bookshelf/Baz.kt'):
            fail.append('目录名串被误判为文件级回源')

        if fail:
            print('SELFTEST: FAIL（' + '；'.join(fail) + '）')
            return 1
        print('SELFTEST: PASS（3/3 夹具：有报告 PASS；无报告红；仅目录名提及红；目录名不构成回源）')
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main() -> int:
    ap = argparse.ArgumentParser(description='门禁 59 提交来源回源巡检')
    ap.add_argument('repo', nargs='?', default='.')
    ap.add_argument('--count', type=int, default=DEFAULT_WINDOW, help='扫描最近 N 条 commit（默认 10）')
    ap.add_argument('--selftest', action='store_true', help='跑三条夹具自测后删临时目录')
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    return check_recent(args.repo, args.count)


if __name__ == '__main__':
    sys.exit(main())
