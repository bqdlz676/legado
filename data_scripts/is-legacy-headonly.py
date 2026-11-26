#!/usr/bin/env python3
"""判"存量件 + 纯头部留痕"：给门禁 ⑲/⑭/⑮ 用。

为什么：
  230 份历史报告批量补回执/证据留痕行时，新增行不一定逐字命中五标记
  （回执块里会夹 E1/E2 证据行、指纹行、引用行），按"逐行命中标记"判会把
  合规留痕判死，逼人伪造证据；改用"位置判据"：新增行全部落在文件前 60 行
  （即头部区块），且文件在 HEAD 已存在，就视为纯头部留痕。

适用条件：
  只判"存量件"（HEAD 已存在）。新文件一律不豁免；正文（60 行以后）有任何
  新增行一律不豁免 —— 那可能是改结论。

用法：
  python3 data_scripts/is-legacy-headonly.py <path>  → 0 豁免 / 1 不豁免
"""
import re
import subprocess
import sys

HEAD_LIMIT = 60


def git(*args):
    p = subprocess.run(['git', *args], capture_output=True, text=True)
    return p.returncode, p.stdout


def main():
    if len(sys.argv) < 2:
        return 2
    path = sys.argv[1]

    rc, _ = git('cat-file', '-e', 'HEAD:%s' % path)
    if rc != 0:
        return 1  # 新文件不豁免

    rc, diff = git('diff', '-U0', '--', path)
    if not diff.strip():
        rc, diff = git('diff', '-U0', '--cached', '--', path)
    if not diff.strip():
        return 1  # 没有本轮改动，不属"头部留痕"

    added_lines = []
    new_ln = None
    for line in diff.splitlines():
        m = re.match(r'^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@', line)
        if m:
            new_ln = int(m.group(1))
            continue
        if new_ln is None:
            continue
        if line.startswith('+') and not line.startswith('+++'):
            added_lines.append(new_ln)
            new_ln += 1
        elif line.startswith('-') and not line.startswith('---'):
            pass
        elif line.startswith(' '):
            new_ln += 1

    if not added_lines:
        return 1
    # 新增行若全部在前 HEAD_LIMIT 行内，视为纯头部留痕。
    return 0 if max(added_lines) <= HEAD_LIMIT else 1


if __name__ == '__main__':
    sys.exit(main())
