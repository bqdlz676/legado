#!/usr/bin/env python3
"""门禁 ㉞ Gradle 语法守卫（K-19 机制化）。

为什么：settings.gradle / *.gradle 是 Groovy，行首 `#` 不是注释而是非法字符，
settings 文件一红整条流水线都进不了 Task（run 45525688582 实证）。
判据：仓库内 settings.gradle、build.gradle、**/build.gradle 行首出现 `#` → FAIL（`#!` 行除外）。
"""
import sys
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


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
