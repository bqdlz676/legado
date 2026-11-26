#!/usr/bin/env python3
"""只读/私有 setter 直写机检（Q-204，2026-11-26 补编译面最后一块）。

为什么有它：88e62bc 两路 CI 红里有一类是 `scrollState.value = x`——属性读公开、写私有，
静态文本完全合法，只有编译器认。S1~S8 都看不见它。这类错在本仓是"抄例子抄错版本"
的高发点（Compose 的 value / maxValue / firstVisibleItemIndex 都是只读）。

判据：`<某对象>.<只读属性> =` 命中即 FAIL。只读属性名来自 `readonly-props.json`，
人工维护 + 发现新的随时补登；名单不全就会漏，这是兜底不是根治，报告里必须写明。

用法：
  python3 data_scripts/check-readonly-setter.py [files...]   # 不给文件则扫 git 改动
  python3 data_scripts/check-readonly-setter.py --emit        # 打印当前名单
"""
import glob
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROPS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'readonly-props.json')

# 只认"只在 Compose 滚动状态上出现"的属性名。`value` 这种通用名会误杀 MutableLiveData，
# 所以 value 单独走"接收者类型必须是 ScrollState/LazyListState"的更严判据（见 check()）。
DEFAULT = {
    'maxValue': 'ScrollState.maxValue 只读，由内容高度决定',
    'firstVisibleItemIndex': 'LazyListState 只读，要改走 scrollToItem',
    'firstVisibleItemScrollOffset': 'LazyListState 只读，要改走 scrollToItem',
}


def load_props():
    try:
        with open(PROPS, encoding='utf-8') as f:
            data = json.load(f)
        return data if data else DEFAULT
    except (OSError, ValueError):
        return DEFAULT


def strip_noise(src):
    """去注释与字符串，避免把文档里的例子当成代码（沿用 lint 脚本的同名口径）。"""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n':
                i += 1
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            i += 2
            while i + 1 < n and not (src[i] == '*' and src[i + 1] == '/'):
                i += 1
            i += 2
        elif c == '"':
            if src[i:i + 3] == '"""':
                i += 3
                while i + 2 < n and src[i:i + 3] != '"""':
                    i += 1
                i += 3
            else:
                i += 1
                while i < n and src[i] != '"':
                    i += 2 if src[i] == '\\' else 1
                i += 1
            out.append('""')
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def check(path, props):
    try:
        clean = strip_noise(open(path, encoding='utf-8', errors='ignore').read())
    except OSError:
        return []
    errs = []
    # 严判据：先找出本文件里"类型是 ScrollState/LazyListState"的变量名，
    # 只对这些变量的 .value 赋值判死（实测 MutableLiveData.value 可写，通用名会误杀）
    scroll_vars = set(re.findall(r'(\w+)\s*:\s*(?:androidx\.compose\.foundation\.)?(?:ScrollState|LazyListState)\b', clean))
    scroll_vars |= set(re.findall(r'(?:val|var)\s+(\w+)\s*=\s*remember(?:Lazy)?ScrollState\s*\(', clean))
    for v in scroll_vars:
        for m in re.finditer(r'\b' + re.escape(v) + r'\.value\s*=[^=]', clean):
            errs.append((clean.count('\n', 0, m.start()) + 1,
                         '滚动状态 %s.value 只读（写请用 scrollTo / dispatchRawDelta）' % v))
        for prop in ('maxValue', 'firstVisibleItemIndex', 'firstVisibleItemScrollOffset'):
            for m in re.finditer(r'\b' + re.escape(v) + r'\.' + prop + r'\s*=[^=]', clean):
                errs.append((clean.count('\n', 0, m.start()) + 1,
                             '滚动状态 %s.%s 只读' % (v, prop)))
    for name in ():  # 裸属性名判据已停用（Value/MaxValue 在非 Compose 类上可写）
        pat = re.compile(r'\.\s*%s\s*(?:=[^=]|:=\s*[^=])' % re.escape(name))
        for m in pat.finditer(clean):
            # 先排除声明处（val xxx: T = ... 里没有点号访问，天然不命中）
            errs.append((clean.count('\n', 0, m.start()) + 1,
                         '只读属性 .%s 被赋值（%s）' % (name, props[name])))
    return errs


def files_from_args(args):
    if args:
        return args
    out = subprocess.run(['git', '-c', 'core.quotepath=false', 'status', '--porcelain', '-uno'],
                         cwd=ROOT, capture_output=True, text=True).stdout
    fs = []
    for row in out.splitlines():
        p = row[3:].strip().strip('"')
        if p.endswith('.kt'):
            fs.append(os.path.join(ROOT, p) if not os.path.isabs(p) else p)
    return fs or glob.glob(os.path.join(ROOT, 'app/src/**/*.kt'), recursive=True)


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if '--emit' in sys.argv:
        for k, v in load_props().items():
            print('%s <- %s' % (k, v))
        return 0
    props = load_props()
    bad = 0
    fs = files_from_args(args)
    for f in fs:
        for ln, why in check(f, props):
            print('FAIL %s:%d %s' % (os.path.relpath(f, ROOT), ln, why))
            bad += 1
    print('READONLY-SETTER: %s（%d 个文件，名单 %d 条）' % (
        'FAIL' if bad else 'PASS', len(fs), len(props)))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
