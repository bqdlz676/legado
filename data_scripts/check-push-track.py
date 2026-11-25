#!/usr/bin/env python3
"""推送口径双轨判别（用户在 2026-11-25 拍板落地的那条口径）。

为什么有这个东西：一条 push 口径套不住两种改动。
  - 纯文档/规则改动：CI 只跑验证、没有编译产物，先推等于把未验收的历史留在远端，还打散 F-109 攒批。
    这类走 **A 轨**：本地 commit → 自检/验收过 → 再 push。
  - 碰代码或 CI 定义的改动：沙箱没有 JDK21/SDK，本地编译是假绿（F-107），CI 是唯一编译验证。
    这类走 **B 轨**：写完就 push，让 CI 当验收；红了立刻最小改动再推（CI 即验收）。
判据必须是路径机械判，不靠人记；人一眼看不出某个 commit 属于哪轨。

判定规则（保守优先：认不出的路径一律算代码轨）：
  某 commit 的路径全部落在文档白名单 → A 轨
  全部落在代码侧 → B 轨
  两类混在同一个 commit → 违规，指向 F-112（文档与代码必须逐路径分离）

用法：python3 data_scripts/check-push-track.py [--range origin/main..HEAD] [--sha <sha>] [repo]
退出码：0 = 只有 A/B 轨（或干净）；1 = 存在混合 commit。
注意：本脚本只判轨别、不替你决定推不推；A 轨要不要推仍由验收结论决定。
"""
import argparse
import subprocess
import sys

# 文档侧：docs/ 与 data_scripts/ 都不进编译产物，改了也不会影响 APK/单测口径。
DOC_PREFIXES = ('docs/', 'data_scripts/')
# 文档侧的文件后缀：仓库根上的 README/CHANGELOG 之类也算文档，不必逼着走代码轨。
DOC_SUFFIXES = ('.md', '.txt', '.png', '.jpg', '.svg', '.html')


def git(repo, *args):
    # core.quotepath=false：中文路径不放开会被转义成八进制，前缀就匹配不上
    r = subprocess.run(['git', '-c', 'core.quotepath=false', *args],
                       cwd=repo, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip() or 'git 调用失败')
    return r.stdout


def commit_paths(repo, sha):
    out = git(repo, 'diff-tree', '--no-commit-id', '--name-only', '-r', sha)
    return [x.strip() for x in out.splitlines() if x.strip()]


def classify(path):
    if path.startswith(DOC_PREFIXES):
        return 'doc'
    if path.lower().endswith(DOC_SUFFIXES) and '/' not in path:
        return 'doc'
    return 'code'


def track_of(paths):
    kinds = {classify(p) for p in paths}
    if not kinds:
        return 'A', '空提交（无路径）'
    if kinds == {'doc'}:
        return 'A', '纯文档轨'
    if kinds == {'code'}:
        return 'B', '代码轨'
    return 'MIX', '文档与代码混在同一 commit（违反 F-112 逐路径分离）'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--range', default='origin/main..HEAD')
    ap.add_argument('--sha')
    ap.add_argument('repo', nargs='?', default='.')
    a = ap.parse_args()

    shas = [a.sha] if a.sha else [s for s in git(a.repo, 'rev-list', a.range).split() if s]
    if not shas:
        print(f'PUSH-TRACK: 干净（{a.range} 无待推提交）')
        return 0

    bad = []
    for sha in shas:
        short = git(a.repo, 'rev-parse', '--short', sha).strip()
        subject = git(a.repo, 'log', '-1', '--format=%s', sha).strip()
        paths = commit_paths(a.repo, sha)
        track, why = track_of(paths)
        print(f'{track:3s} {short} {len(paths):>3d} 文件 | {why} | {subject[:46]}')
        if track == 'MIX':
            bad.append(short)

    if bad:
        print(f'PUSH-TRACK: FAIL（{len(bad)} 个混合提交：{", ".join(bad)}）——按 F-112 拆分后再推')
        return 1
    print(f'PUSH-TRACK: OK（{len(shas)} 个待推提交，轨别已判；A 轨须验收后推，B 轨推了由 CI 验收）')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except RuntimeError as e:
        # 判不出就当场报错，不静默放行——沿用仓库"无法核验即视为未通过"的口径
        print(f'PUSH-TRACK: ERROR {e}')
        sys.exit(1)
