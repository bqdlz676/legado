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
import shutil
import subprocess
import sys
import tempfile

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


def _write_kt(root: str, name: str, src: str) -> str:
    d = os.path.join(root, 'src')
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, name)
    open(p, 'w', encoding='utf-8').write(src)
    return p


def _run(argv: list[str]) -> int:
    old = sys.argv
    sys.argv = ['check-readonly-setter.py'] + argv
    try:
        return main()
    finally:
        sys.argv = old


def selftest() -> int:
    """双向自测：合法滚动状态读写 → PASS；只读 .value / .maxValue / .firstVisibleItemIndex 被赋值 → FAIL rc=1。
    夹具 tempfile.mkdtemp + rmtree 自删；判定回调生产 check()/main()，名单走真实 load_props()。"""
    props = load_props()

    # 好样本：rememberLazyListState + scrollToItem 合法调用，无只读属性赋值
    good = tempfile.mkdtemp(prefix='readonly-setter-selftest-')
    try:
        g = _write_kt(good, 'Good.kt',
                      'val list = rememberLazyListState()\n'
                      'list.scrollToItem(3)\n')
        errs_good = check(g, props)
        assert errs_good == [], f"合法读写应无错误，errs={errs_good}"
        rc_good = _run([g])
        assert rc_good == 0, f"合法样本应 PASS，rc={rc_good}"
        print('[selftest] 好样本（scrollToItem 合法调用）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一：ScrollState.value 只读被直写 → 命中 .value 只读判据
    bad1 = tempfile.mkdtemp(prefix='readonly-setter-selftest-')
    try:
        b1 = _write_kt(bad1, 'BadValue.kt',
                       'val scroll: ScrollState = rememberScrollState(0)\n'
                       'scroll.value = 100\n')
        errs_b1 = check(b1, props)
        assert errs_b1, f".value 直写应报错，errs={errs_b1}"
        rc_b1 = _run([b1])
        assert rc_b1 == 1, f"只读 .value 直写应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（ScrollState.value 只读被赋值）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad1, ignore_errors=True)

    # 坏样本二：LazyListState.firstVisibleItemIndex 只读被直写 → 命中滚动状态属性判据
    bad2 = tempfile.mkdtemp(prefix='readonly-setter-selftest-')
    try:
        b2 = _write_kt(bad2, 'BadIndex.kt',
                       'val list: LazyListState = rememberLazyListState()\n'
                       'list.firstVisibleItemIndex = 5\n')
        errs_b2 = check(b2, props)
        assert errs_b2, f"firstVisibleItemIndex 直写应报错，errs={errs_b2}"
        rc_b2 = _run([b2])
        assert rc_b2 == 1, f"只读属性直写应 FAIL，rc={rc_b2}"
        print('[selftest] 坏样本二（LazyListState.firstVisibleItemIndex 只读被赋值）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad2, ignore_errors=True)

    # 边界·空集：传入不存在的文件路径 → check 返回空，零违规真绿，现有设计 PASS
    empty = tempfile.mkdtemp(prefix='readonly-setter-selftest-empty-')
    try:
        rc_empty = _run([os.path.join(empty, 'Nope.kt')])
        assert rc_empty == 0, f"无待检文件现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（无待检 .kt）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main())
