#!/usr/bin/env python3
"""门禁 ㊵ 提交路径分离巡检（F-112）。

为什么：结单/台账/文档类 commit 必须与代码类 commit 逐路径分开。事故 E-05 实证：调度层用
`git add -A` 结单，把在飞子代理尚未收口的代码扫进自己的 commit，导致 commit 与单号无法一一
回源。这条把"混合提交"变成机械判据，不靠人记。
判据：扫最近 N 个 commit（默认 5，`--count` 可调），逐条取 `--name-status` 名单：
      同一个 commit 同时含 `docs/` 内容改动与 `app/src/` 改动 → FAIL，打印短哈希、摘要与两侧路径；
      全部分离 → PASS。
R100 纯改名豁免（Q-259）：`docs/ ↔ docs/` 且相似度 R100（零内容增删）的纯改名不计入
"文档桶混代码桶"。理由：改名搬运不改变交付语义，不产生可回源的内容 diff；锚点回源由
锚点卡/报告沿革段维护旧名→新名对应。豁免**仅限** R100 双侧 docs：
  - 同一 commit 只要还有一处非 R100 的 docs 内容改动（A/M/D/C/R<100）混进代码桶，照旧红；
  - 跨桶改名（docs↔app/src）不豁免；代码侧改名不豁免（本脚本只判 docs 内容 vs 代码）。
边界：
  - 单文件紧急修复允许混合，但须在 commit message 写明（F-112 例外）；本脚本只做路径机械判据，
    豁免登记归人工台账，脚本不读 message 内容放水。
  - 纯标准库实现：不依赖网络、JDK、gradle；只读本地 git 历史。git 不可用/不可解析时按 FAIL
    处理——无法核验即视为未通过，避免静默放绿。
  - `--commit <sha>` 为定向复核/自测入口：只检查指定单条 commit，忽略 `--count`。
  - `--selftest` 双向夹具：R100 改名+代码同提交 → PASS；docs 内容改写+代码同提交 → 红。
用法：`python3 data_scripts/check-commit-split.py [--count N] [--commit <sha>] [--selftest] [repo]`。
"""
import argparse
import shutil
import subprocess
import sys
import tempfile

# 路径前缀判据：docs/ = 文档/台账/规则侧；app/src/ = 代码侧（main 与 test 同属代码，一并计）
DOCS_PREFIX = 'docs/'
CODE_PREFIX = 'app/src/'
# 豁免档位：仅 R100（相似度 100%，零内容变化）的双侧 docs 改名不计文档桶；R90/R80 等带内容
# 差异的改名仍按 docs 内容改动计——改名携带改写时交付语义已变，不能享受纯搬运豁免。
PURE_RENAME_STATUS = 'R100'


def git(repo: str, *args):
    # core.quotepath=false：中文路径默认被 git 转义成八进制串，不放开就匹配不到 docs/ 前缀
    r = subprocess.run(['git', '-c', 'core.quotepath=false', *args],
                       cwd=repo, capture_output=True, text=True)
    return r.returncode, r.stdout


def commit_changes(repo: str, sha: str):
    # diff-tree + -M 给重命名状态与两侧路径；R 行格式 `R100<TAB>旧路径<TAB>新路径`，其余
    # 状态（A/M/D/C）为 `状态<TAB>路径`。只用 plumbing 名单，不掺 diff 内容。
    rc, out = git(repo, 'diff-tree', '--no-commit-id', '--name-status', '-M', '-r', sha)
    if rc != 0:
        return []
    changes = []
    for line in out.splitlines():
        if not line.strip():
            continue
        parts = line.split('\t')
        status = parts[0]
        if status.startswith('R'):
            changes.append((status, parts[1], parts[2]))
        else:
            changes.append((status, parts[1], parts[1]))
    return changes


def classify(changes):
    """分三桶：docs 内容改动、代码改动、豁免的 docs↔docs R100 纯改名。

    其他前缀（配置/脚本/资源等）维持原口径：不计入任一桶。
    """
    docs_content, code, pure_renames = [], [], []
    for status, old, new in changes:
        old_doc = old.startswith(DOCS_PREFIX)
        new_doc = new.startswith(DOCS_PREFIX)
        old_code = old.startswith(CODE_PREFIX)
        new_code = new.startswith(CODE_PREFIX)
        if status == PURE_RENAME_STATUS and old_doc and new_doc:
            pure_renames.append(new)
            continue
        target = new if status.startswith('R') else old
        if old_code or new_code:
            code.append(target)
        elif old_doc or new_doc:
            docs_content.append(target)
        # 其余前缀：两桶都不计
    return docs_content, code, pure_renames


def inspect(repo: str, sha: str, subject: str):
    """返回 (是否分离, 命中描述行, 豁免改名清单)；混合时描述行列出两侧路径供回源。"""
    docs_content, code, pure_renames = classify(commit_changes(repo, sha))
    if docs_content and code:
        line = (f'  {sha[:7]} {subject} -> 混合结单与代码：'
                f'docs={docs_content} code={code}')
        if pure_renames:
            line += f'（同提交纯改名 R100 已豁免：{pure_renames}）'
        return False, line, pure_renames
    return True, None, pure_renames


def check_single(repo: str, sha: str) -> int:
    rc, out = git(repo, 'log', '-1', '--format=%H%x1f%s', sha)
    if rc != 0 or not out.strip():
        print(f'COMMIT-SPLIT: FAIL（无法解析 commit {sha}）')
        return 1
    full, subject = out.strip().split('\x1f', 1)
    ok, line, renames = inspect(repo, full, subject)
    if not ok:
        print(line)
        print(f'COMMIT-SPLIT: FAIL（{full[:7]} 同时含 docs/ 内容改动与 app/src/，结单与代码未分离）')
        return 1
    note = f'；豁免纯改名 {renames}' if renames else ''
    print(f'COMMIT-SPLIT: PASS（{full[:7]} {subject} 路径已分离{note}）')
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
    exempted = 0
    for full, subject in commits:
        ok, line, renames = inspect(repo, full, subject)
        if not ok:
            mixed.append(line)
        elif renames:
            exempted += 1
    for line in mixed:
        print(line)
    if mixed:
        print(f'COMMIT-SPLIT: FAIL（{len(mixed)}/{len(commits)} 条 commit 混合 docs/ 内容改动与 '
              f'app/src/，须逐路径分开提交）')
        return 1
    note = f'；其中 {exempted} 条仅含 docs↔docs R100 纯改名' if exempted else ''
    print(f'COMMIT-SPLIT: PASS（最近 {len(commits)} 条 commit 文档/代码已分离{note}）')
    return 0


def _sh(tmp: str, *args) -> int:
    r = subprocess.run(['git', '-c', 'core.quotepath=false', *args],
                       cwd=tmp, capture_output=True, text=True)
    return r.returncode


def _rev_parse(tmp: str) -> str:
    rc, out = git(tmp, 'rev-parse', 'HEAD')
    return out.strip() if rc == 0 else ''


def selftest() -> int:
    """双向自测：R100 纯改名+代码同提交 PASS；docs 内容改写+代码同提交红。夹具用完自删。"""
    if not shutil.which('git'):
        print('COMMIT-SPLIT: FAIL（--selftest 需要本地 git 打夹具，git 不可用）')
        return 1
    tmp = tempfile.mkdtemp(prefix='commit-split-selftest-')
    try:
        _sh(tmp, 'init', '-q')
        _sh(tmp, 'config', 'user.email', 'selftest@example.com')
        _sh(tmp, 'config', 'user.name', 'selftest')

        import os
        docs_dir = os.path.join(tmp, 'docs')
        code_dir = os.path.join(tmp, 'app', 'src')
        os.makedirs(docs_dir, exist_ok=True)
        os.makedirs(code_dir, exist_ok=True)

        # 夹具 A：base 提交 → R100 纯改名（内容一字不动）+ 代码改动同提交 → 应 PASS。
        with open(os.path.join(docs_dir, 'old.md'), 'w', encoding='utf-8') as f:
            f.write('# 纯改名文档\n内容保持不变\n')
        with open(os.path.join(code_dir, 'FixtureA.kt'), 'w', encoding='utf-8') as f:
            f.write('fun a() = 1\n')
        _sh(tmp, 'add', '-A')
        _sh(tmp, 'commit', '-q', '-m', 'base A')

        _sh(tmp, 'mv', os.path.join(docs_dir, 'old.md'), os.path.join(docs_dir, 'new.md'))
        with open(os.path.join(code_dir, 'FixtureA.kt'), 'w', encoding='utf-8') as f:
            f.write('fun a() = 2\n')
        _sh(tmp, 'add', '-A')
        _sh(tmp, 'commit', '-q', '-m', 'rename docs + touch code')
        sha_a = _rev_parse(tmp)
        ok_a, line_a, renames_a = inspect(tmp, sha_a, 'rename docs + touch code')
        assert ok_a, f'R100 纯改名+代码同提交应 PASS，实际 FAIL：{line_a}'
        assert len(renames_a) == 1, f'夹具 A 应识别出 1 条 R100 改名，实际：{renames_a}'
        print('[selftest] R100 纯改名+代码同提交 PASS 通路 OK，豁免：', renames_a)

        # 夹具 B：base 提交 → docs 内容改写 + 代码改动同提交 → 应 FAIL（严格侧不放宽）。
        with open(os.path.join(docs_dir, 'rewrite.md'), 'w', encoding='utf-8') as f:
            f.write('v1 原稿\n')
        with open(os.path.join(code_dir, 'FixtureB.kt'), 'w', encoding='utf-8') as f:
            f.write('fun b() = 1\n')
        _sh(tmp, 'add', '-A')
        _sh(tmp, 'commit', '-q', '-m', 'base B')
        with open(os.path.join(docs_dir, 'rewrite.md'), 'w', encoding='utf-8') as f:
            f.write('v2 改写后内容（交付语义已变）\n')
        with open(os.path.join(code_dir, 'FixtureB.kt'), 'w', encoding='utf-8') as f:
            f.write('fun b() = 2\n')
        _sh(tmp, 'add', '-A')
        _sh(tmp, 'commit', '-q', '-m', 'rewrite docs + touch code')
        sha_b = _rev_parse(tmp)
        ok_b, line_b, renames_b = inspect(tmp, sha_b, 'rewrite docs + touch code')
        assert not ok_b, 'docs 内容改写+代码同提交应 FAIL，实际 PASS'
        assert not renames_b, f'夹具 B 不应识别到 R100 改名，实际：{renames_b}'
        print('[selftest] docs 内容改写+代码同提交 FAIL 通路 OK，命中：', line_b.strip())

        print('[selftest] 夹具已自删，双向自测通过。')
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('repo', nargs='?', default='.')
    ap.add_argument('--count', type=int, default=5, help='扫描最近 N 条 commit（默认 5）')
    ap.add_argument('--commit', help='定向检查指定 commit（自测/复核用），忽略 --count')
    ap.add_argument('--selftest', action='store_true', help='跑内置双向夹具，用完自删')
    args = ap.parse_args()

    if args.selftest:
        return selftest()
    if args.commit:
        return check_single(args.repo, args.commit)
    return check_recent(args.repo, args.count)


if __name__ == '__main__':
    sys.exit(main())
