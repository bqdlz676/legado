#!/usr/bin/env python3
"""门禁 ㉘ 测试函数名合法性守卫（F-100）。

为什么需要：Kotlin 反引号函数名在 JVM 目标下禁止出现 `. : [ ] / ; < >`，
一旦出现，CI 会在 `compileAppDebugUnitTestKotlin` 直接 FAIL（不是单测失败，是编译失败），
表现为"代码看起来没问题却红"，且每次红都要等一整轮 CI（约 15 分钟）才被证实。
实证：run 45453607335（`anchor contract: ...` 含冒号）、run 45465010171（`栏宽 = 885.6` 含点号）。

判据：扫 app/src/test 下所有 .kt，凡 `fun \`名字\`(` 且名字含上述字符 → FAIL 并点名文件:行。
"""
import re
import sys
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


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'app/src/test'))
