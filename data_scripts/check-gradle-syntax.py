#!/usr/bin/env python3
"""门禁 ㉞ Gradle 语法守卫（K-19 机制化）。

为什么：settings.gradle / *.gradle 是 Groovy，行首 `#` 不是注释而是非法字符，
settings 文件一红整条流水线都进不了 Task（run 45525688582 实证）。
判据：仓库内 settings.gradle、build.gradle、**/build.gradle 行首出现 `#` → FAIL（`#!` 行除外）。
"""
import shutil
import sys
import tempfile
from pathlib import Path


def main(repo: str) -> int:
    base = Path(repo)
    files = list(base.glob('settings.gradle')) + list(base.glob('build.gradle')) + list(base.rglob('build.gradle'))
    bad = []
    for f in files:
        if '.git' in f.parts:
            continue
        for i, ln in enumerate(f.read_text(encoding='utf-8', errors='ignore').splitlines(), 1):
            if ln.lstrip().startswith('#'):
                bad.append(f'{f}:{i}: {ln.strip()[:80]}')
    if bad:
        print('GRADLE-SYNTAX: FAIL（Groovy 文件里出现行首 # 注释）')
        for b in bad[:10]:
            print('  ' + b)
        return 1
    print(f'GRADLE-SYNTAX: PASS（{len(files)} 个 gradle 文件无行首 # 注释）')
    return 0


def _make_repo(files: dict) -> str:
    """临时目录构造夹具仓库：files 为 {相对路径: 内容}。"""
    repo = tempfile.mkdtemp(prefix='gradle-syntax-selftest-')
    for rel, content in files.items():
        p = Path(repo) / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding='utf-8')
    return repo


def selftest() -> int:
    """双向自测：Groovy 注释用 // → main() rc=0；行首 # → rc=1 点名；无 gradle 文件按现有设计 rc=0。
    夹具全部 tempfile.mkdtemp + rmtree 自删，判据回调生产 main(repo)。"""
    # 好样本：settings / 根 build / 子模块 build 均为合法 Groovy（// 注释）
    good = _make_repo({
        'settings.gradle': '// 项目名\nrootProject.name = "luowen"\n',
        'build.gradle': '// 根构建\nbuildscript { }\n',
        'app/build.gradle': '// 模块构建\nplugins { }\n',
    })
    try:
        rc_good = main(good)
        assert rc_good == 0, f"合法 Groovy 文件应 PASS，rc={rc_good}"
        print('[selftest] 好样本（// 注释的 gradle 文件）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一：settings.gradle 行首 #（Groovy 非法字符）→ 命中判据
    bad1 = _make_repo({'settings.gradle': '# 这行 # 在 Groovy 里不是注释\nrootProject.name = "x"\n'})
    try:
        rc_b1 = main(bad1)
        assert rc_b1 == 1, f"settings.gradle 行首 # 应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（settings.gradle 行首 #）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad1, ignore_errors=True)

    # 坏样本二：子模块 build.gradle 行首 # → 命中判据（覆盖 rglob 子目录通路）
    bad2 = _make_repo({'app/build.gradle': '# 模块文件里的行首 #\nplugins { }\n'})
    try:
        rc_b2 = main(bad2)
        assert rc_b2 == 1, f"子模块 build.gradle 行首 # 应 FAIL，rc={rc_b2}"
        print('[selftest] 坏样本二（子模块 build.gradle 行首 #）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad2, ignore_errors=True)

    # 边界·超限：3 个 gradle 文件各带一行 #，仍 rc=1（命中即红，不逐条放行）
    over = _make_repo({
        'settings.gradle': '# s\n',
        'build.gradle': '# b\n',
        'app/build.gradle': '# a\n',
    })
    try:
        rc_over = main(over)
        assert rc_over == 1, f"多处行首 # 仍应 FAIL，rc={rc_over}"
        print('[selftest] 边界·超限（3 个 gradle 文件均带行首 #）FAIL 通路 OK')
    finally:
        shutil.rmtree(over, ignore_errors=True)

    # 边界·空集：仓库内无任何 gradle 文件 → 现有设计按 0 文件放行 rc=0。
    # 空集只发生在夹具/异常仓库；真实仓库 settings.gradle 缺失由编译链与其余门禁兜底。
    empty = tempfile.mkdtemp(prefix='gradle-syntax-selftest-empty-')
    try:
        rc_empty = main(empty)
        assert rc_empty == 0, f"无 gradle 文件的现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（无 gradle 文件）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
