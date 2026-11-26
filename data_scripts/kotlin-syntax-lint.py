#!/usr/bin/env python3
"""Kotlin 语法级预检（2026-11-26 事故产物）。

为什么要有它：沙箱没有 Android 编译链（禁本地编译），语法错误只能由云端 CI 抓；
而一次语法错误会让 Verify + Screenshot 两路同时红（实测 38960a6 红：
SearchPagingOrchestrator.kt:75 `),` 后接 `)`，if 条件里多了一个逗号），
代价是两轮 runner + 两轮人工排查。压成脚本，push 前必跑，把「编译才能发现的
最低级错误」提前到本地零成本拦掉。

只做能确定判死的两条（宁缺不误杀）：
  S1 括号不配平（去注释/字符串后，{} () [] 三类）
  S2 if/while 条件内多余尾逗号（Kotlin 允许实参列表尾逗号，但不允许在
     条件表达式里跟 ),  —— 这是本仓真实踩到的坑）

用法：python3 data_scripts/kotlin-syntax-lint.py [文件...]
（不给文件时：只查 git 本轮改动/新增的 .kt，避免历史文件误伤）
退出码：0 = 无硬错；1 = 有硬错（不推）。
"""
import glob
import re
import subprocess
import sys


def strip_noise(src: str) -> str:
    """去掉注释与字符串内容，保留换行以便回源行号；保留引号占位避免误配括号。"""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n':
                i += 1
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            i += 2
            while i + 1 < n and not (src[i] == '*' and src[i + 1] == '/'):
                out.append('\n' if src[i] == '\n' else ' ')
                i += 1
            i += 2
        elif c == '"':
            triple = src[i:i + 3] == '"""'
            out.append('""' if not triple else '"""')
            i += 3 if triple else 1
            while i < n:
                if triple:
                    # 原始串结尾常见 `"""...""""`：连续 3+ 个引号一并算作收尾，
                    # 多出的引号是内容里的引号，不能当成重新开串（实测误报来源）。
                    run = 0
                    while i + run < n and src[i + run] == '"':
                        run += 1
                    if run >= 3:
                        i += run
                        break
                    out.append(' ')
                    i += 1
                    continue
                if i >= 0 and src[i] == '"':
                    i += 1
                    break
                if not triple and src[i] == '$' and i + 1 < n and src[i + 1] == '{':
                    # 模板 ${...} 里可以跨行，内容自成配平：整段按不可见处理，
                    # 否则规范串被换行截断，模板代码被当成真代码 → 括号失衡误报（实测）。
                    depth, i = 0, i + 1
                    while i < n:
                        if src[i] == '{':
                            depth += 1
                        elif src[i] == '}':
                            depth -= 1
                            if depth == 0:
                                i += 1
                                break
                        out.append('\n' if src[i] == '\n' else ' ')
                        i += 1
                    continue
                if not triple and src[i] == '\n':
                    break
                if src[i] == '\\':  # 转义：\" 不是字符串结束（实测误报来源）
                    out.append('  ')
                    i += 2
                    continue
                out.append('\n' if src[i] == '\n' else ' ')
                i += 1
        elif c == "'":
            out.append("'")
            i += 1
            while i < n:
                if src[i] == '\\':
                    out.append('  ')
                    i += 2
                    continue
                if src[i] == "'":
                    out.append("'")
                    i += 1
                    break
                out.append(' ')
                i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)


PAIRS = {'{': '}', '(': ')', '[': ']'}


def check_balance(clean: str):
    stack, errs = [], []
    line = 1
    for ch in clean:
        if ch == '\n':
            line += 1
        elif ch in PAIRS:
            stack.append((ch, line))
        elif ch in PAIRS.values():
            if not stack or PAIRS[stack[-1][0]] != ch:
                errs.append((line, '闭合符 %s 不匹配' % ch))
                continue
            stack.pop()
    for op, ln in stack[-3:]:
        errs.append((ln, '未闭合的 %s' % op))
    return errs


IF_HEAD = re.compile(r'(?:^|[^\w.])if\s*\(|(?:^|[^\w.])while\s*\(')


def check_cond_trailing_comma(clean: str):
    """S2：if/while 条件文本以逗号结尾 —— Kotlin 语法硬错。"""
    errs = []
    for m in IF_HEAD.finditer(clean):
        start = m.end() - 1  # 指向 (
        depth, i = 0, start
        while i < len(clean):
            if clean[i] in '([':
                depth += 1
            elif clean[i] in ')]':
                depth -= 1
                if depth == 0:
                    break
            i += 1
        inner = clean[start + 1:i].rstrip()
        if inner.endswith(','):
            errs.append((clean.count('\n', 0, i) + 1, '条件表达式内多余尾逗号（,)）'))
    return errs


def main() -> int:
    args = sys.argv[1:]
    if args:
        files = args
    else:
        files = []
        out = subprocess.run(['git', 'status', '--porcelain', '-uno'],
                             capture_output=True, text=True).stdout
        for row in out.splitlines():
            path = row[3:].strip().strip('"')
            if path.endswith('.kt'):
                files.append(path)
        if not files:
            files = glob.glob('app/src/**/*.kt', recursive=True)
    bad = 0
    for f in files:
        try:
            src = open(f, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        clean = strip_noise(src)
        for ln, why in check_balance(clean) + check_cond_trailing_comma(clean):
            print('FAIL %s:%d %s' % (f, ln, why))
            bad += 1
    print('KOTLIN-LINT: %s（%d 个文件）' % ('FAIL' if bad else 'PASS', len(files)))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
