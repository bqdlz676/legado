#!/usr/bin/env python3
"""门禁 ㉝ 本地禁止编译守卫（F-107）。

为什么：沙箱无 JDK21/Android SDK，本地跑 gradle 只会产出假绿假红并留下常驻 daemon。
判据（近 120 分钟窗口内的活动才算违规，历史残留不永久卡）：
  ①存在 GradleDaemon 进程 → FAIL
  ②~/.gradle/daemon/*/daemon-*.out.log 在「处置锚点」之后被写 → FAIL
  ③仓库 app/build 或 build 目录在锚点之后被写 → FAIL
  锚点 = repo/docs/台账-本地编译违规.md 内最后一次处置的 epoch 秒（没有锚点则退回 120 分钟窗口）。
  为什么要有锚点：抓到一次违规后要能"清零重算"，否则杀完 daemon 日志 mtime 还在窗口内，门禁永远红。
"""
import os
import subprocess
import sys
import time
from pathlib import Path

WINDOW_S = 120 * 60


MARKER = 'docs/台账-本地编译违规.md'


def marker_epoch(repo: Path) -> float | None:
    """读处置锚点：台账里最后一行 `处置锚点 epoch=<秒>`。"""
    f = repo / MARKER
    if not f.exists():
        return None
    epochs = []
    for ln in f.read_text(encoding='utf-8', errors='ignore').splitlines():
        if '处置锚点 epoch=' in ln:
            # 锚点数字后面常紧跟中文括号，不能按空白切；按正则取第一段数字
            import re as _re
            m = _re.search(r'处置锚点 epoch=(\d+)', ln)
            if m:
                epochs.append(float(m.group(1)))
    return max(epochs) if epochs else None


def recent(path: Path) -> bool:
    try:
        return (time.time() - path.stat().st_mtime) < WINDOW_S
    except OSError:
        return False


def main(repo: str) -> int:
    bad = []
    try:
        ps = subprocess.run(['ps', '-eo', 'args'], capture_output=True, text=True, timeout=10).stdout
        if 'GradleDaemon' in ps or 'org.gradle.launcher.daemon' in ps:
            bad.append('有 GradleDaemon 进程在跑')
    except Exception as e:  # 无 ps 时降级为不判
        print(f'NO-LOCAL-BUILD: WARN（ps 不可用：{e}）')

    base0 = Path(repo)
    anchor = marker_epoch(base0)
    for log in Path.home().glob('.gradle/daemon/*/daemon-*.out.log'):
        mtime = log.stat().st_mtime if log.exists() else 0
        hit = (mtime > anchor) if anchor else recent(log)
        if hit:
            bad.append(f'gradle daemon 日志近 2 小时被写：{log}')
            break

    base = base0
    for d in (base / 'app' / 'build', base / 'build'):
        if d.is_dir() and (d.stat().st_mtime > anchor if anchor else recent(d)):
            bad.append(f'本地构建产物目录近 2 小时被写：{d}')

    if bad:
        print('NO-LOCAL-BUILD: FAIL（违反 F-107 本地禁止编译）')
        for b in bad:
            print('  ' + b)
        return 1
    print('NO-LOCAL-BUILD: PASS（无 gradle 进程/无近 2 小时本地构建痕迹）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
