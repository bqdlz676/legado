#!/usr/bin/env python3
"""门禁 ㉘ 测试函数名合法性守卫（F-100）。

为什么需要：Kotlin 反引号函数名在 JVM 目标下禁止出现 `. : [ ] / ; < >`，
一旦出现，CI 会在 `compileAppDebugUnitTestKotlin` 直接 FAIL（不是单测失败，是编译失败），
表现为"代码看起来没问题却红"，且每次红都要等一整轮 CI（约 15 分钟）才被证实。
实证：run 45453607335（`anchor contract: ...` 含冒号）、run 45465010171（`栏宽 = 885.6` 含点号）。

判据：扫 app/src/test 下所有 .kt，凡 `fun \`名字\`(` 且名字含上述字符 → FAIL 并点名文件:行。
"""
import re
import shutil
import sys
import tempfile
from pathlib import Path

ILLEGAL = '.:[]/;<>'
FUN_RE = re.compile(r'fun `([^`]*)`\s*\(')


def main(root: str) -> int:
    base = Path(root).resolve()
    hits = []
    for p in sorted(base.rglob('*.kt')):
        text = p.read_text(encoding='utf-8', errors='ignore')
        for m in FUN_RE.finditer(text):
            name = m.group(1)
            bad = [c for c in name if c in ILLEGAL]
            if bad:
                line = text[:m.start()].count('\n') + 1
                hits.append((p, line, name, ''.join(sorted(set(bad)))))
    if hits:
        print('TEST-FUN-NAME: FAIL（反引号函数名含 JVM 非法字符，CI 会在 compileAppDebugUnitTestKotlin 红）')
        for p, line, name, bad in hits:
            print(f'  {p}:{line}  非法字符[{bad}]  名字：{name[:60]}')
        return 1
    print('TEST-FUN-NAME: PASS（无反引号函数名含 . : [ ] / ; < >）')
    return 0


def _make_root(files: dict) -> str:
    """临时目录构造夹具测试源集：files 为 {相对路径: 内容}。"""
    root = tempfile.mkdtemp(prefix='test-fun-names-selftest-')
    for rel, content in files.items():
        p = Path(root) / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding='utf-8')
    return root


def selftest() -> int:
    """双向自测：合法反引号名 → main() rc=0；含 / 或 . : 非法字符 → rc=1 点名文件:行。
    夹具 tempfile.mkdtemp + rmtree 自删，判据回调生产 main(root)。"""
    # 好样本：中文/数字/空格的反引号名 + 普通函数，均不含 JVM 非法字符
    good = _make_root({
        'test/GoodTest.kt': (
            'package io.legado.app\n'
            'class GoodTest {\n'
            '    fun `正常用例 中文与数字 123`() {}\n'
            '    fun plainMethod() {}\n'
            '}\n')
    })
    try:
        rc_good = main(good)
        assert rc_good == 0, f"合法函数名应 PASS，rc={rc_good}"
        print('[selftest] 好样本（合法反引号名 + 普通函数）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一：反引号名含 `/`（事故 run 45453607335 同形态）→ 命中判据
    bad1 = _make_root({
        'test/BadSlash.kt': 'class BadSlash {\n'
                            '    fun `B9-10 1/4/10 → 3/6/8`() {}\n'
                            '}\n'
    })
    try:
        rc_b1 = main(bad1)
        assert rc_b1 == 1, f"含 / 的函数名应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（反引号名含 /）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad1, ignore_errors=True)

    # 坏样本二：反引号名同时含 `.` 与 `:`（事故 run 45465010171 同形态）→ 命中判据
    bad2 = _make_root({
        'test/BadDotColon.kt': 'class BadDotColon {\n'
                               '    fun `栏宽 = 885.6: 主态`() {}\n'
                               '}\n'
    })
    try:
        rc_b2 = main(bad2)
        assert rc_b2 == 1, f"含 . : 的函数名应 FAIL，rc={rc_b2}"
        print('[selftest] 坏样本二（反引号名含 . 与 :）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad2, ignore_errors=True)

    # 边界·空集：目录内无 .kt 文件 → 现有设计按 0 文件放行 rc=0。
    # 空集只发生在夹具/异常源集；真实 test 源集缺失由编译链与 test-source 门禁兜底。
    empty = tempfile.mkdtemp(prefix='test-fun-names-selftest-empty-')
    try:
        rc_empty = main(empty)
        assert rc_empty == 0, f"无 .kt 文件的现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（无 .kt 源文件）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'app/src/test'))
