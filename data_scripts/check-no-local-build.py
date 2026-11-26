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
import shutil
import subprocess
import sys
import tempfile
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
        # 只认 comm=java 的守护进程行：调用方 shell 的 argv 里可能正好含 "GradleDaemon"
        # 这个字样（例如本脚本被 heredoc/管道包裹时），整表匹配会自匹配成假阳性。
        ps = subprocess.run(['ps', '-eo', 'comm=,args='], capture_output=True, text=True, timeout=10).stdout
        for row in ps.splitlines():
            parts = row.split(None, 1)
            if len(parts) != 2:
                continue
            comm, args = parts
            if not comm.lower().startswith('java'):
                continue
            if 'GradleDaemon' in args or 'org.gradle.launcher.daemon' in args or 'KotlinCompileDaemon' in args:
                bad.append(f'有 Gradle/Kotlin 守护进程在跑：{args[:120]}')
                break
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


def selftest() -> int:
    """双向自测：锚点清零后无新构建痕迹 → PASS；临时仓库内新建 app/build 目录 → ③ FAIL。
    直接调 main(repo)，ps/日志/产物目录三条判据全走生产代码；夹具不碰真实 ~/.gradle 与仓库 build 目录。"""
    good = tempfile.mkdtemp(prefix='no-local-build-selftest-')
    try:
        gdocs = Path(good) / 'docs'
        gdocs.mkdir(parents=True, exist_ok=True)
        # 锚点取当前时刻：本机 ~/.gradle 旧日志 mtime 早于锚点即不计近窗，避免历史残留把好样本误红
        (gdocs / '台账-本地编译违规.md').write_text(
            f'处置锚点 epoch={int(time.time())}\n', encoding='utf-8')
        rc_good = main(good)
        assert rc_good == 0, f"锚点清零后无新痕迹应 PASS，rc={rc_good}"
        print('[selftest] 好样本（锚点清零后无新构建痕迹）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    bad = tempfile.mkdtemp(prefix='no-local-build-selftest-')
    try:
        # 坏样本：无处置锚点，新建 app/build 目录 mtime 落在近 2 小时窗内，命中判据③
        (Path(bad) / 'app' / 'build').mkdir(parents=True, exist_ok=True)
        rc_bad = main(bad)
        assert rc_bad == 1, f"新建 app/build 目录应 FAIL，rc={rc_bad}"
        print('[selftest] 坏样本（app/build 近 2 小时被写）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
