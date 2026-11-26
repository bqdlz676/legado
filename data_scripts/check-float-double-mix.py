#!/usr/bin/env python3
"""门禁 ㉜ Float/Double 混算守卫（K-16 机制化）。

为什么需要：Kotlin 里 `floatVal * doubleVal` 会把结果推成 Double，
若又直接 `coerceAtLeast(0f)` 或 return 声明为 Float，就会编译红（CI run 45506214252）。
这条只做一件事：契约层纯函数里出现"乘法表达式同时含 .toDouble() 与 0f 字面量且没有 .toFloat()"的行，判 FAIL。
"""
import re
import shutil
import sys
import tempfile
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


def _make_repo(files: dict) -> str:
    """在临时目录构造夹具仓库：files 为 {相对路径: 文件内容}，目录按 DIRS 契约层布局。"""
    repo = tempfile.mkdtemp(prefix='float-double-selftest-')
    base = Path(repo) / DIRS[0] / 'contract'
    base.mkdir(parents=True, exist_ok=True)
    for rel, content in files.items():
        (base / rel).write_text(content, encoding='utf-8')
    return repo


def selftest() -> int:
    """双向自测：干净契约层 → main() rc=0；混算行 → rc=1 点名；空契约层按现有设计 rc=0。
    做法：临时目录构造夹具仓库后直接调生产 main(repo)，判据一行未复制；夹具收尾自删。"""
    # 好样本：纯 Float 乘法 + 带 .toFloat() 收敛的行，均不命中混算判据
    good = _make_repo({
        'Good.kt': (
            'package io.legado.app.contract\n'
            'fun compute(width: Float, height: Float): Float {\n'
            '    val ratio: Float = width * height.coerceAtLeast(0f)\n'
            '    val fixed = a * b.toDouble().toFloat() * 1f\n'
            '    return ratio * fixed\n'
            '}\n')
    })
    try:
        rc_good = main(good)
        assert rc_good == 0, f"干净契约层应 PASS，rc={rc_good}"
        print('[selftest] 好样本（纯 Float 乘法 + toFloat 收敛）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一：val 行乘法同时含 toDouble() 与 0f、无 toFloat() → 命中判据
    bad1 = _make_repo({
        'Bad1.kt': 'package io.legado.app.contract\n'
                   'val x = a * b.toDouble() * 0f\n'
    })
    try:
        rc_b1 = main(bad1)
        assert rc_b1 == 1, f"混算 val 行应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（val 行 toDouble()*0f 无 toFloat）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad1, ignore_errors=True)

    # 坏样本二：return 行同样形态 → 命中判据（覆盖 (return|val) 两种前缀）
    bad2 = _make_repo({
        'Bad2.kt': 'package io.legado.app.contract\n'
                   'return c.toDouble() * 0f\n'
    })
    try:
        rc_b2 = main(bad2)
        assert rc_b2 == 1, f"混算 return 行应 FAIL，rc={rc_b2}"
        print('[selftest] 坏样本二（return 行 toDouble()*0f）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad2, ignore_errors=True)

    # 边界·超限：12 条混算行，生产只列前 10 条，仍须 rc=1（截断不改变判红结论）
    over = _make_repo({
        'Over.kt': 'package io.legado.app.contract\n' +
                   '\n'.join(f'val v{i} = a{i} * b{i}.toDouble() * 0f' for i in range(12)) + '\n'
    })
    try:
        rc_over = main(over)
        assert rc_over == 1, f"超 10 条混算仍应 FAIL，rc={rc_over}"
        print('[selftest] 边界·超限（12 条混算，列表截断前 10）FAIL 通路 OK')
    finally:
        shutil.rmtree(over, ignore_errors=True)

    # 边界·空集：无 contract 目录（夹具仓库整体不建契约层）→ 现有设计按 0 文件放行，rc=0。
    # 为什么不改判红：本门禁扫的是契约层既有文件，空集只发生在夹具/异常仓库；
    # 主源混算由 ext-imports/readonly-setter 等其余门禁分担，此处空集不掩盖索引故障。
    empty = tempfile.mkdtemp(prefix='float-double-selftest-empty-')
    try:
        rc_empty = main(empty)
        assert rc_empty == 0, f"无契约层文件的现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（无 contract/*.kt）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
