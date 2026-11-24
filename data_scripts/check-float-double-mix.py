#!/usr/bin/env python3
"""门禁 ㉜ Float/Double 混算守卫（K-16 机制化）。

为什么需要：Kotlin 里 `floatVal * doubleVal` 会把结果推成 Double，
若又直接 `coerceAtLeast(0f)` 或 return 声明为 Float，就会编译红（CI run 45506214252）。
这条只做一件事：契约层纯函数里出现"乘法表达式同时含 .toDouble() 与 0f 字面量且没有 .toFloat()"的行，判 FAIL。
"""
import re
import sys
from pathlib import Path

RISK = re.compile(r'(return|val)\s*\(?.*\*.*(\.toDouble\(\)|0f).*\*.*')
DIRS = ['app/src/main/java/io/legado/app']


def main(repo: str) -> int:
    base = Path(repo)
    bad = []
    for d in DIRS:
        for f in (base / d).rglob('contract/*.kt'):
            txt = f.read_text(encoding='utf-8', errors='ignore')
            for i, ln in enumerate(txt.splitlines(), 1):
                if 'toDouble()' in ln and '*' in ln and re.search(r'0f\b', ln) and '.toFloat()' not in ln and 'toDouble().' not in ln:
                    bad.append(f'{f}:{i}: {ln.strip()[:90]}')
    if bad:
        print('FLOAT-DOUBLE-MIX: FAIL')
        for b in bad[:10]:
            print('  ' + b)
        return 1
    print(f'FLOAT-DOUBLE-MIX: PASS（契约层 {len(list((base / DIRS[0]).rglob("contract/*.kt")))} 个文件无 Float/Double 混算返回）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
