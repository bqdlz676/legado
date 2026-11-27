#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 63 仓内符号存在性与重载配平守卫（2026-11-27 本轮 CI 红一类根因产物）。

事故形态：`B3Row.matchesB3Query` 仓内只有一参扩展定义，调用处按四参形态写；纯文本
编译期才报 `Argument count mismatch`，回执/静态排版看不出来。本闸把「仓内自有符号的
定义存在性 + 扩展/顶层重载参个数配平」提前到提交前机械判定。

判据（只扫 `app/src/**` 与 `screenshot/src/**` 的 `.kt`）：
  NODEF（FAIL）：调用的名字从自有包（`io.legado...`）import 进来，但仓内索引反查不到
    任何 `fun` 或类主构造定义——定义被删/改名，或 import 指向了不存在的符号。
  ARITY（FAIL）：仓内同名定义，但可核对的重载（顶层函数 / 扩展函数 / 类主构造）没有任一
    能容纳本次调用实参个数；定义处无 `vararg`、无默认参、也无匹配重载。
  重载放行：同名多定义时任一重载可容纳（个数相等，或 vararg/默认参兜底）即放行。

位点分类（高 Precision 优先，拿不到接收者类型就不硬判）：
  - 顶层 `fun name(`、扩展 `fun R.name(`：进索引，可做配平核对；
  - 类/对象内部的成员 `fun name(`：进索引仅作名字存在性参考；成员调用不参与 ARITY
    核对——静态闸解析不出接收者类型，同名成员撞 SDK 方法只会刷假红；
  - `class Name(...)` / `data class Name(...)` 主构造：进索引当构造定义，裸调用核对。

跳过并在报告计数（不判红）：
  生成符号：接收者为 `R` / `BuildConfig` 的点调用；
  外部库 import：import 前缀属 androidx/android/kotlin/java/javax/kotlinx/... 等外部根；
  标准库/运行时/测试框架常用方法白名单；
  自有索引里查不到、也不是自有 import 的名字：归为「外部/未知」跳过，不硬判 NODEF。

限制：本闸不做类型推断，接收者类型不参与解析；同名单参/多参重载只按参个数与
vararg/默认参判配平。它是编译期错误的前置筛子，不替代编译。
退出码：0 = 无 NODEF / ARITY / PACKAGE；1 = 有 FAIL。
用法：python3 data_scripts/check-symbol-existence.py [--selftest]
"""
import os
import re
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_ROOTS = ['app/src', 'screenshot/src']
OWN_PREFIX = 'io.legado'

# 外部库根前缀：import 落到这些根上一律按外部符号跳过。
EXTERNAL_ROOTS = (
    'androidx.', 'android.', 'kotlin.', 'kotlinx.', 'java.', 'javax.',
    'com.google.', 'org.jetbrains.', 'org.koin.', 'com.bumptech.',
    'io.ktor.', 'okhttp3.', 'retrofit2.', 'org.json.', 'io.reactivex.',
    'com.squareup.', 'org.apache.', 'com.github.', 'io.github.',
    'com.android.', 'org.slf4j.', 'cn.hutool.', 'org.junit.',
    'org.mockito.', 'org.robolectric.', 'io.mockk.',
)

# 关键字/控制结构名，不是调用。
KEYWORDS = {
    'if', 'when', 'for', 'while', 'return', 'throw', 'switch', 'catch',
    'do', 'else', 'try', 'fun', 'class', 'object', 'interface', 'enum',
    'data', 'companion', 'init', 'by', 'get', 'set', 'field', 'delegate',
    'provider', 'sealed', 'suspend', 'var', 'val', 'const', 'typealias',
    'constructor', 'this', 'super', 'null', 'true', 'false', 'package',
    'import', 'as', 'is', 'in', 'out', 'vararg', 'dynamic', 'continue',
    'break', 'file', 'param', 'receiver', 'where', 'override', 'open',
    'final', 'abstract', 'internal', 'private', 'public', 'protected',
    'actual', 'expect', 'lateinit', 'readonly', 'inner', 'tailrec',
    'external', 'operator', 'infix', 'inline', 'noinline', 'crossinline',
    'reified', 'annotation', 'data', 'value', 'expect', 'actual',
}

# 标准库 / Java-Android 运行时 / 测试框架常用方法：静态闸拿不到接收者类型，这些不进
# 仓内索引也不该报 NODEF。白名单只收高频且定义在 SDK/运行时/测试框架的名字；仓内自有
# 同名函数仍会被索引优先匹配。
STDLIB_METHODS = {
    'toString', 'equals', 'hashCode', 'compareTo', 'let', 'run', 'also',
    'apply', 'with', 'to', 'first', 'last', 'firstOrNull', 'lastOrNull',
    'map', 'mapNotNull', 'mapIndexed', 'filter', 'filterNotNull', 'flatMap',
    'forEach', 'forEachIndexed', 'contains', 'containsKey', 'containsValue',
    'get', 'set', 'put', 'remove', 'clear', 'add', 'offer', 'poll', 'peek',
    'size', 'length', 'charAt', 'substring', 'trim', 'trimStart', 'trimEnd',
    'split', 'replace', 'matches', 'indexOf', 'indexOfFirst', 'indexOfLast',
    'groupBy', 'associateBy', 'associate', 'associateWith', 'distinct',
    'sortedBy', 'sortedDescending', 'maxByOrNull', 'minByOrNull', 'maxOrNull',
    'minOrNull', 'count', 'any', 'all', 'none', 'sumBy', 'sumOf', 'average',
    'joinToString', 'chunked', 'windowed', 'zip', 'withIndex', 'takeIf',
    'takeUnless', 'require', 'check', 'checkNotNull', 'requireNotNull',
    'println', 'print', 'repeat', 'lazy', 'copy', 'invoke', 'iterator',
    'keys', 'values', 'entries', 'component1', 'component2', 'component3',
    'component4', 'component5', 'toList', 'toMap', 'toSet', 'mutableListOf',
    'mutableMapOf', 'mutableSetOf', 'listOf', 'setOf', 'mapOf', 'arrayOf',
    'toInt', 'toLong', 'toDouble', 'toFloat', 'toBoolean', 'toCharArray',
    'plus', 'minus', 'times', 'div', 'rem', 'rangeTo', 'inc', 'dec',
    'not', 'and', 'or', 'xor', 'onEach', 'zipWithNext', 'binarySearch',
    'reversed', 'drop', 'take', 'slice', 'distinctBy', 'partition',
    'minOf', 'maxOf', 'coerceAtLeast', 'coerceAtMost', 'getOrElse',
    'getOrDefault', 'getValue', 'setValue', 'collect', 'collectLatest',
    'stateIn', 'launch', 'async', 'await', 'postValue', 'observe',
    'onClick', 'onValueChange', 'value', 'startActivity', 'finish',
    'getString', 'getDrawable', 'getSharedPreferences', 'setOnClickListener',
    'findViewById', 'addView', 'removeView', 'getResources', 'getSystemService',
    'beginTransaction', 'replace', 'show', 'dismiss', 'close', 'flush',
    'readText', 'writeText', 'readLines', 'forEachLine', 'delete', 'exists',
    'mkdirs', 'listFiles', 'inputStream', 'outputStream', 'use', 'copyTo',
    'post', 'obtainMessage', 'sendMessage', 'putExtra', 'getSerializableExtra',
    'sleep', 'join', 'currentTimeMillis', 'nanoTime', 'printStackTrace',
    'valueOf', 'forName', 'newInstance', 'setAccessible', 'getDeclaredField',
    # 测试框架（JUnit/Mockito/MockK/断言）
    'verify', 'mock', 'when', 'thenReturn', 'then', 'given', 'every',
    'runBlocking', 'assertTrue', 'assertEquals', 'assertNotNull',
    'assertNull', 'assertThat', 'assertIs', 'assertNotEquals', 'assertFailsWith',
    'fail', 'describe', 'it', 'beforeEach', 'afterEach', 'before', 'after',
    'rule', 'argumentCaptor', 'capture', 'orNull', 'containsNoItems',
    'isEqualTo', 'hasSize', 'startsWith', 'endsWith', 'assertTrue',
    'on', 'block', 'coEvery', 'coVerify', 'coMatch', 'answers', 'just',
    'onView', 'onData', 'perform', 'check', 'matches', 'inRoot', 'withId',
    'withText', 'click', 'longClick', 'typeText', 'pressBack', 'swipeUp',
    'swipeDown', 'swipeLeft', 'swipeRight', 'scrollTo', 'scrollIntoView',
}

# 形参列表用配平括号扫描提取：箭头类型 `(String) -> Unit`、泛型 `<T>` 内部都带括号，
# 初版用 `[^)]*` 截取会在第一个内层 `)` 处断掉，把 7 参组件数成 3 参。
# fun 前允许一串修饰符（inline/suspend/override/...）；泛型角括号在代码里手动配平跳过，
# 嵌套泛型 `<T, K : Comparable<K>>` 正则写不平。
FUN_PREFIX_RE = re.compile(
    r'(?:(?:inline|crossinline|noinline|operator|infix|suspend|external|'
    r'tailrec|abstract|open|final|private|public|protected|internal|override|'
    r'const|lateinit|inner|companion|sealed|data)\s+)*fun\s+'
)
NAME_RE = re.compile(
    r'(?P<receiver>[A-Za-z0-9_.<>,?\s]+?\.)?'
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\('
)
JAVA_CLASS_RE = re.compile(r'\b(?:class|interface|enum)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)')
JAVA_METHOD_RE = re.compile(
    r'(?:public|private|protected|static|final|synchronized|abstract|native)\s+'
    r'[A-Za-z0-9_<>,\[\]\s.?]+?\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\('
)


def skip_angles(src: str, i: int) -> int:
    """跳过 fun 名前的泛型实参/形参角括号 `<...>`（可嵌套）。"""
    if i < len(src) and src[i] == '<':
        depth = 0
        while i < len(src):
            if src[i] == '<':
                depth += 1
            elif src[i] == '>':
                depth -= 1
                if depth == 0:
                    return i + 1
            i += 1
    return i


def skip_ws(src: str, i: int) -> int:
    while i < len(src) and src[i] in ' \t\r\n':
        i += 1
    return i
# 类声明两步解析：头部截到 `:`/`{`/`;` 为止，父类段 `: Super(...)` 整体丢弃——
# 里面的括号不是主构造形参（初版把 DefaultLogger() 的空括号当成主构造 arity=0）；
# super 段若贪婪到下一个 `{`，还会把后续类声明一并吞掉，故这里不做 super 捕获。
CLASS_RE = re.compile(
    r'(?:data\s+|value\s+|inner\s+|open\s+|abstract\s+|sealed\s+|final\s+)?'
    r'class\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)'
    r'(?P<tail>[^\{:;]*)'
)
# 安全调用 `?.` 必须并入接收者链：初版只认 `a.b.`，于是 `chapter?.verticalTitle()` 被当成
# 裸调用，拿去和同名的顶层 fun verticalTitle(String)（arity=1）配平，刷出必红假报 ARITY。
# 实测 3 条 ARITY 全由此产生（InkReadController.kt:105/:128/:182 的 chapter?.verticalTitle()）。
CALL_RE = re.compile(
    r'(?P<receiver>(?:[A-Za-z_][A-Za-z0-9_]*\s*(?:\?\.|\.)\s*)+)?'
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\('
)
IMPORT_RE = re.compile(
    r'^import\s+(?P<path>[A-Za-z0-9_.*]+)(?:\s+as\s+(?P<alias>\w+))?',
    re.MULTILINE)  # 必须多行模式：import 常位于 package 之后的非首行，裸 ^ 只匹首行；
# 路径字符类含 `*`：通配符 import（`import a.b.*`）必须被解析出来进 PACKAGE 判定，
# 旧版字符类不含星号，整行正则落空等于整行跳过（N2 加固）。


def strip_noise(src: str) -> str:
    """去注释与字符串字面量，避免注释/文案里的函数名造成假报（沿用 ext-imports 口径）。"""
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
            # 字符串用占位符顶替而不是删空：保留括号内的参数结构与逗号，否则
            # readLong("$.id") 会被数成 0 参调用（初版误报 ARITY 的主要来源之一）。
            out.append(' __STR__ ')
            i += 3 if src[i:i + 3] == '"""' else 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == '"':
                    i += 1
                    break
                i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def balanced_params(src: str, open_idx: int) -> tuple[str, int]:
    """从 `(` 下标配平到对应闭括号，返回 (形参串, 闭括号下标)。

    形参类型里可能嵌套箭头函数 `(String) -> Unit`、泛型 `<T>`、方括号 `Array<out T>`，
    不能用贪婪/非贪婪正则截，按括号深度走字符流才不会断。
    """
    depth, i, n = 0, open_idx, len(src)
    start = open_idx + 1
    while i < n:
        c = src[i]
        if c in '([{':
            depth += 1
        elif c in ')]}':
            depth -= 1
            if depth == 0:
                return src[start:i], i
        # 尖括号不参与配平：箭头 `->` 的 `>` 不是闭括号，泛角括号也不在形参层嵌套。
        i += 1
    return src[start:], i


def split_params(params: str) -> list:
    """按顶层逗号拆形参（忽略泛型/括号内逗号）。

    泛型 `<String, *>`、`<T, K>` 里也有逗号，必须单独记一层角括号深度，否则
    Map<String, *> 会被拆成两个形参，把一参函数数成两参刷 ARITY。
    """
    parts, depth, ang, cur = [], 0, 0, []
    for ch in params:
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        elif ch == '<':
            ang += 1
        elif ch == '>':
            ang = max(0, ang - 1)
        elif ch == ',' and depth == 0 and ang == 0:
            parts.append(''.join(cur).strip())
            cur = []
            continue
        cur.append(ch)
    if ''.join(cur).strip():
        parts.append(''.join(cur).strip())
    return parts


def brace_depths(src: str) -> list:
    """逐字符大括号深度数组；用于区分顶层定义与类内成员定义。"""
    depths, d = [], 0
    for ch in src:
        if ch == '{':
            d += 1
        elif ch == '}':
            d = max(0, d - 1)
        depths.append(d)
    return depths


def parse_definitions(src: str) -> dict:
    """名字 -> 定义列表 [{arity, vararg, default, receiver, kind}]。

    kind: top 顶层函数 / extension 扩展函数 / member 类内成员 / ctor 类主构造 /
    java Java 符号（只做存在性参考，不参与 ARITY 核对——Java 形参静态解析口径不同）。
    成员/Java 定义只进索引做名字存在性参考，不参与 ARITY 核对（接收者类型静态解析不出）。
    """
    index: dict[str, list] = {}
    depths = brace_depths(src)
    for fm in FUN_PREFIX_RE.finditer(src):
        i = skip_angles(src, fm.end())
        i = skip_ws(src, i)  # 泛型 `<T>` 与函数名之间常有空格，不跳开会整段漏索引。
        nm = NAME_RE.match(src, i)
        if not nm:
            continue
        name = nm.group('name')
        if name in KEYWORDS:
            continue
        open_idx = src.find('(', nm.end() - 1)
        params_str, _ = balanced_params(src, open_idx) if open_idx >= 0 else ('', open_idx)
        params = split_params(params_str)
        vararg = any('vararg' in p for p in params)
        default = any(re.search(r'=\s*\S', p) for p in params)
        depth = depths[fm.start()] if fm.start() < len(depths) else 0
        receiver = (nm.group('receiver') or '').strip('.')
        if receiver:
            kind = 'extension'
        elif depth == 0:
            kind = 'top'
        else:
            kind = 'member'
        index.setdefault(name, []).append({
            'arity': len(params), 'vararg': vararg, 'default': default,
            'receiver': receiver, 'kind': kind,
            # private 顶层/扩展只在本文件可见；进全局索引会跨文件错配到别的文件的同名
            # 私有 fun（SwitchRow data class 调用被 CheckSourceConfig 的私有 fun 错配）。
            'private': 'private' in src[fm.start():fm.end()],
        })
    for m in CLASS_RE.finditer(src):
        name = m.group('name')
        if name in KEYWORDS:
            continue
        tail = m.group('tail') or ''
        p_open = tail.find('(')
        if p_open >= 0:
            params_str, _ = balanced_params(tail, p_open)
            params = split_params(params_str)
        else:
            params = []
        vararg = any('vararg' in p for p in params)
        default = any(re.search(r'=\s*\S', p) for p in params)
        index.setdefault(name, []).append({
            'arity': len(params), 'vararg': vararg, 'default': default,
            'receiver': '', 'kind': 'ctor',
        })
    return index


def parse_java(src: str) -> dict:
    """Java 源码只做名字存在性索引：类/接口/枚举名进 ctor，方法名进 java。

    为什么不解析 Java 形参：Java 形参口径与 Kotlin 不同，且本闸 ARITY 只核对 Kotlin
    自有扩展/顶层；Java 符号进索引只是为了不误报 NODEF（QueryTTF 这类 Java 实体类）。
    """
    index: dict[str, list] = {}
    for m in JAVA_CLASS_RE.finditer(src):
        name = m.group('name')
        if name in KEYWORDS:
            continue
        index.setdefault(name, []).append({
            'arity': 0, 'vararg': False, 'default': False,
            'receiver': '', 'kind': 'ctor',
        })
    for m in JAVA_METHOD_RE.finditer(src):
        name = m.group('name')
        if name in KEYWORDS:
            continue
        index.setdefault(name, []).append({
            'arity': 0, 'vararg': False, 'default': False,
            'receiver': '', 'kind': 'java',
        })
    return index


def count_args(src: str, open_paren: int) -> tuple[int, int]:
    """从 `(` 位置起配平括号，返回 (实参个数, 闭括号下标)。空括号 `()` 为 0 参。"""
    inside, close_idx = balanced_params(src, open_paren)
    if not inside.strip():
        return 0, close_idx
    # 尾部逗号 `a, b,` 是合法 Kotlin 写法，但裸逗号计数会把空尾段数成一参，先剥掉。
    inside = inside.rstrip()
    if inside.endswith(','):
        inside = inside[:-1].rstrip()
    depth, commas = 0, 0
    for c in inside:
        if c in '([{':
            depth += 1
        elif c in ')]}':
            depth -= 1
        elif c == ',' and depth == 0:
            commas += 1
    return commas + 1, close_idx


def trailing_lambda(src: str, close_idx: int) -> bool:
    """闭括号后紧跟 `{`：尾随 lambda 是最后一个实参，Kotlin 语法不写在括号里。"""
    j = close_idx + 1
    while j < len(src) and src[j].isspace():
        j += 1
    return j < len(src) and src[j] == '{'


def accommodates(defn: dict, call_arity: int) -> bool:
    """任一重载可容纳：个数相等；或 vararg 兜底；或有默认参（尾部可选，允许少传）。

    注意默认参方向：初版误写成「调用个数 ≥ 定义个数」，实际默认参是把尾部形参变可选，
    调用个数可以少于定义个数——Compose 组件一堆带默认参的顶层 fun 就是这么被误报 ARITY 的。
    """
    if defn['arity'] == call_arity:
        return True
    if defn['vararg']:
        return call_arity >= max(0, defn['arity'] - 1)
    if defn['default'] and call_arity <= defn['arity']:
        return True
    return False


def collect_files(root: str) -> list:
    """调用扫描只看 .kt（任务口径：抽取 Kotlin 调用与 import）。"""
    out = []
    for rel in SOURCE_ROOTS:
        base = os.path.join(root, rel)
        if not os.path.isdir(base):
            continue
        for dirpath, _, files in os.walk(base):
            for fn in files:
                if fn.endswith('.kt'):
                    out.append(os.path.relpath(os.path.join(dirpath, fn), root).replace(os.sep, '/'))
    return sorted(out)


def collect_def_files(root: str) -> list:
    """定义索引覆盖 .kt 与 .java：Java 实体类/方法反查得到，才不会误报 NODEF。"""
    out = []
    for rel in SOURCE_ROOTS:
        base = os.path.join(root, rel)
        if not os.path.isdir(base):
            continue
        for dirpath, _, files in os.walk(base):
            for fn in files:
                if fn.endswith(('.kt', '.java')):
                    out.append(os.path.relpath(os.path.join(dirpath, fn), root).replace(os.sep, '/'))
    return sorted(out)


def scan_file(rel: str, root: str, index: dict, external_names: set):
    """返回 (issues, skipped, checked)；issues=[(severity, name, line, arity, msg)]。"""
    issues, skipped = [], {'generated': 0, 'external': 0, 'stdlib': 0, 'unknown': 0}
    checked = 0
    path = os.path.join(root, rel)
    try:
        src = open(path, encoding='utf-8', errors='replace').read()
    except OSError:
        return issues, skipped, checked
    clean = strip_noise(src)
    imports: dict[str, tuple[str, str]] = {}
    for im in IMPORT_RE.finditer(src):
        p = im.group('path')
        last = p.rsplit('.', 1)[-1]
        imports[im.group('alias') or last] = (p, last)
    # 别名映射：`import x.Y as Z` 时，调用名 Z 要回原符号 Y 查索引，否则别名类全报 NODEF。
    alias_orig = {alias: orig for alias, (_p, orig) in imports.items() if alias != orig}
    file_own = {n for n, (p, _o) in imports.items() if p.startswith(OWN_PREFIX)}
    file_external = {n for n, (p, _o) in imports.items() if p.startswith(EXTERNAL_ROOTS)}

    for m in CALL_RE.finditer(clean):
        name = m.group('name')
        # 声明位点（`fun name(`）不是调用，跳过；否则定义自己会被当成 0 参调用刷 ARITY。
        pre = clean[:m.start()].rstrip()
        if pre.endswith('fun'):
            continue
        if name in KEYWORDS:
            continue
        receiver = m.group('receiver') or ''
        first_seg = receiver.split('.')[0] if receiver else ''
        if first_seg in ('R', 'BuildConfig'):
            skipped['generated'] += 1
            continue
        if name in STDLIB_METHODS:
            skipped['stdlib'] += 1
            continue
        if name in file_external or name in external_names:
            skipped['external'] += 1
            continue

        defs = index.get(name, [])
        orig = alias_orig.get(name)
        if not defs and orig:
            defs = index.get(orig, [])  # 别名回原符号查定义。
        # 自有判定只看本文件 import：全局自有表会把别处 import 的同名符号带到本文件，
        # 导致本文件里的 SDK 同名调用被误当成自有扩展配平。
        own = name in file_own
        # 配平核对只对「自有包显式 import 的符号」开门：SDK/成员同名方法（如
        # SharedPreferences.getBoolean）静态解析不出接收者类型，拿去和自有扩展比参个数
        # 只会刷假红。点调用只核对扩展定义，裸调用核对顶层函数；成员/主构造不参与配平。
        # private 定义只在本文件可见：跨文件的同名私有 fun 不进候选，避免错配。
        visible = [d for d in defs if not d.get('private') or d.get('file') == rel]
        if receiver:
            candidates = [d for d in visible if d['kind'] == 'extension']
            member_collision = any(d['kind'] == 'member' for d in defs)
        else:
            # 裸调用只核对顶层 fun；主构造形参跨多行/带注解/继承段，正则静态解析不可靠，
            # 构造定义只参与 NODEF 存在性反查，不进 ARITY 候选（参与会把 data class 调用
            # 错配到误解析的 ctor 形参上，刷出上千条假红）。
            candidates = [d for d in visible if d['kind'] == 'top']
            member_collision = False

        if not defs:
            if own:
                line = clean[:m.start()].count('\n') + 1
                checked += 1
                issues.append(('NODEF', name, line, 0,
                               '自有 import 指向的符号仓内反查不到 fun/构造定义'))
            else:
                skipped['unknown'] += 1
            continue

        open_paren = clean.find('(', m.end() - 1)
        call_arity, close_idx = count_args(clean, open_paren)
        if trailing_lambda(clean, close_idx):
            call_arity += 1  # 尾随 lambda 是末参，括号外传参。
        if not own or not candidates or member_collision:
            # 非自有 import、只落成员定义，或成员/扩展同名撞车：接收者类型未知，不硬判。
            skipped['unknown'] += 1
            continue
        checked += 1
        if not any(accommodates(d, call_arity) for d in candidates):
            line = clean[:m.start()].count('\n') + 1
            sigs = '/'.join('arity=%d%s%s(%s)' % (
                d['arity'], '/vararg' if d['vararg'] else '',
                '/default' if d['default'] else '', d['kind']) for d in candidates)
            issues.append(('ARITY', name, line, call_arity,
                           '调用 %d 参，可核对重载：%s，无一可容纳' % (call_arity, sigs)))
    return issues, skipped, checked



PKG_RE = re.compile(r'^package\s+([A-Za-z0-9_.]+)', re.MULTILINE)
TOP_DECL_RE = re.compile(
    r'^(?:@[A-Za-z0-9_.]+(?:\s*\([^)]*\))?\s*)*'
    r'(?:(?:public|internal|private|protected|data|sealed|enum|open|abstract|expect|actual|inline|'
    r'value|annotation|fun|companion|external)\s+)*'
    r'(class|object|interface|typealias|fun|val|var)\s+([A-Za-z_][A-Za-z0-9_]*)',
    re.MULTILINE)


# 接收者类型段必须非贪婪且以点结尾：首版把字符类写成贪婪的 `[...]*`，会把 `TextChapter.verticalTitle`
# 整个吃进接收者、只留最后一个字母（实跑抓到 group=('e','t')），于是扩展函数认不出来。
EXT_DECL_RE = re.compile(
    r'^\s*fun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_.<>?]*?\.)'
    r'([A-Za-z_][A-Za-z0-9_]*)\s*\(',
    re.MULTILINE)

# 声明位扫描（宽松兜底专用，N1 加固）：修饰符/注解前缀串，fun 与非 fun 两支共用。
_DECL_MOD = (
    r'(?:@[A-Za-z0-9_.]+(?:\s*\([^)]*\))?\s*|'
    r'(?:public|internal|private|protected|open|abstract|final|sealed|data|value|inner|'
    r'enum|expect|actual|inline|operator|infix|suspend|tailrec|override|const|companion)\s+)*'
)
# fun 声明位：行首/缩进后 → 修饰符 → fun → 可选泛型形参 → 可选接收者段（含泛型/逗号/空格/可空符）
# → 名字 → 必须紧接左括号。名字与 `(` 之间只允许空白；不许再用 `.*?` 跨入函数体匹配提及。
FUN_DECL_POS_RE = re.compile(
    r'^\s*' + _DECL_MOD + r'fun\s+'
    r'(?:<[^<>]*(?:<[^<>]*>[^<>]*)*>\s*)?'
    # 接收者字符类含 ?/逗号/空格，覆盖泛型可空接收者（如 HashMap<String, T>?）。
    r'(?:[A-Za-z_][A-Za-z0-9_.<>?, ]*?\.)?'
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\('
)
# 非 fun 声明位：class/object/interface/typealias/val/var 后接名字（无左括号要求，构造形参可省略）。
OTHER_DECL_POS_RE = re.compile(
    r'^\s*' + _DECL_MOD + r'(?:class|object|interface|typealias|val|var)\s+'
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)'
)
# 块开括号归类头：类 / 接口 / object / 伴生对象体是「声明上下文」；其余开括号（fun 体、
# if/for/while/when/try/catch、lambda、匿名块）是「局部上下文」。为什么按头部分类而不数深度：
# 顶层函数体与类体都在大括号 depth=1，单看深度分不清成员声明与局部影子声明。
_DECL_HEADER_RE = re.compile(r'\b(?:companion\s+object|class|interface|object)\b')


def _decl_stack_ok(stack: list) -> bool:
    """栈空（包级顶层）或帧全为声明上下文才算声明位；任一局部/块帧在栈上即排除。"""
    return all(kind == 'decl' for kind in stack)


def _declares_name(src: str, simple: str) -> bool:
    """逐行判断源码是否在声明位声明了 simple（顶层 / 类·接口·object·伴生对象成员）。

    N1 加固背景：旧版宽松兜底正则 fun 段为 `fun\\s+.*?\\b名\\b`，`.*?` 可越过 fun 名吃到
    同一行函数体里提及该名的位置（探针 F：beta 包 Helper 函数体提及 AlphaBox::class），
    真跨包错引被当成「同包声明」放行，rc=0 假阴性。现改为声明位严格匹配：
    fun 分支必须名字紧接左括号；非 fun 分支必须关键字后接名字；函数体后续行、同行函数体内的
    同名提及一律不算声明。接收者段仍放宽到含泛型/空格/逗号，兜住 `fun <T> HashMap<String, T>.get`。

    g63 影子声明补丁：N1 只收口了「同行函数体内提及」；多行函数体内局部 `var`/`val`/局部
    `fun` 独占一行且与目标同名时，旧版无大括号深度感知，仍被当成包级声明放行。现叠一层
    上下文帧栈：逐字符扫描，括号不计入双引号串 / 三引号串 / 字符字面量 / 注释内的 `{` `}`；
    类/接口/object/伴生对象体压 `decl` 帧，fun 体/控制流/lambda/匿名块压 `block` 帧；
    栈空或全 `decl` 的行才参与声明位正则匹配，函数体内局部影子声明一律不算声明位移据。
    """
    lines = src.splitlines()
    eligible: list[bool] = []
    stack: list[str] = []
    # 字符状态机：code 代码 / line 行注释 / block_comment 块注释 / dq 双引号串 /
    # tq 三引号串 / char 字符字面量。串与注释内的大括号不参与帧栈配平。
    state = 'code'
    block_depth = 0  # 块注释嵌套深度：Kotlin 块注释可嵌套，内层 `/*` 必须计数，首个 `*/` 不退出外层。
    cur_code = ''   # 当前行代码态下累积的文本（仅用于开括号头部分类，串/注释不计入）
    prev_nonempty: list[str] = []
    i, n = 0, len(src)
    if lines:
        eligible.append(_decl_stack_ok(stack))

    def classify_opener() -> str:
        # 两步归类：先认本行开括号前文本；本行只有形参闭括号/空白（类头/fun 头跨多行）
        # 才回看前若干非空行找声明关键字。回看只做兜底，不能把上一行残留的 class/object
        # 关键字带到本行 fun 体开括号上（否则 fun 体帧被错分成声明帧）。
        if _DECL_HEADER_RE.search(cur_code):
            return 'decl'
        if re.search(r'\b(?:fun|if|for|while|when|try|catch|do|else|init)\b', cur_code):
            return 'block'
        header = cur_code + ' ' + ' '.join(reversed(prev_nonempty[-4:]))
        return 'decl' if _DECL_HEADER_RE.search(header) else 'block'

    def new_line() -> None:
        nonlocal cur_code
        if cur_code.strip():
            prev_nonempty.append(cur_code.strip())
        eligible.append(_decl_stack_ok(stack))
        cur_code = ''

    while i < n:
        c = src[i]
        # 换行在任何状态下都推进行号并记行首快照：串/注释跨多行时行号不能错位，
        # 否则后续真实代码行会挂到错误的 eligibility 槽上。
        if c == '\n':
            if state == 'line':
                # 行注释随换行结束：旧版调 new_line() 后未把 state 退回 code，探针留在
                # line 态逐字符吞掉后续真实代码行，`{` `}` 不进帧栈，行注释后顶层 fun
                # 被误判成函数体局部影子声明。换行后必须显式复位回 code。
                new_line()
                state = 'code'
            elif state == 'code':
                new_line()
            else:
                eligible.append(_decl_stack_ok(stack))
            i += 1; continue
        if state == 'line':
            i += 1; continue
        if state == 'block_comment':
            if c == '*' and i + 1 < n and src[i + 1] == '/':
                block_depth -= 1
                i += 2
                if block_depth == 0:
                    state = 'code'
                continue
            if c == '/' and i + 1 < n and src[i + 1] == '*':
                block_depth += 1  # 嵌套块注释：内层开注释计数，不提前退出外层。
                i += 2
                continue
            i += 1; continue
        if state == 'dq':
            if c == '\\':
                i += 2; continue
            if c == '"':
                state = 'code'
            i += 1; continue
        if state == 'tq':
            if src[i:i + 3] == '"""':
                state = 'code'; i += 3; continue
            i += 1; continue
        if state == 'char':
            if c == '\\':
                i += 2; continue
            if c == "'":
                state = 'code'
            i += 1; continue
        # code 态
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            state = 'line'; i += 2; continue
        if c == '/' and i + 1 < n and src[i + 1] == '*':
            state = 'block_comment'; block_depth = 1; i += 2; continue
        if src[i:i + 3] == '"""':
            state = 'tq'; i += 3; continue
        if c == '"':
            state = 'dq'; i += 1; continue
        if c == "'":
            state = 'char'; i += 1; continue
        if c == '{':
            stack.append(classify_opener())
            cur_code += c
            i += 1; continue
        if c == '}':
            if stack:
                stack.pop()
            cur_code += c
            i += 1; continue
        cur_code += c
        i += 1

    # 末尾无换行的最后一行：补记其行首 eligibility，不能因缺换行被整行跳过。
    while len(eligible) < len(lines):
        eligible.append(_decl_stack_ok(stack))

    for idx, line in enumerate(lines):
        if not eligible[idx]:
            continue
        m = FUN_DECL_POS_RE.match(line)
        if m and m.group('name') == simple:
            return True
        m = OTHER_DECL_POS_RE.match(line)
        if m and m.group('name') == simple:
            return True
    return False


def package_import_issues(root: str, files: list) -> list:
    """包级 import 真值（2026-11-27 CI 红根因 c82c585）。
    InkReadController.kt 写了 `import io.legado.app.ui.book.read.vertical.VerticalPageView`，
    而该类定义在同包 `io.legado.app.ui.book.read.ink/VerticalPageView.kt`；kotlinc 报
    Unresolved reference。旧判据只查「这个名字仓内有没有定义」——名字当然有，包不对照样红。

    判法（只认一种错：换了包）：被 import 的 FQN 在仓内找不到同包声明，却在别的包里有同名声明。
    三重放行，防假红（三条都是实跑踩出来的）：
    A 嵌套类 / 伴生成员 —— FQN 前缀链逐级回退命中即放行；
    B 扩展函数接收者带泛型/空格/逗号（`fun <T> HashMap<String, T>.get(...)`）—— 由声明位严格
      扫描器 `_declares_name` 兜住（fun 名紧接左括号，接收者段允许泛型/逗号/空格）；
    C 名字仓内根本没有 —— 归 NODEF，这里不重复报。
    N2 加固：通配符 import（`import a.b.*`）参与判定——被通配包在仓内无任何 package 声明即判
    PACKAGE；包存在则放行（通配符语义为整包导入，不绑定具体符号）。
    性能：包→文件表只建一次，逐 import 查表，不逐文件 repeated read。"""
    pkg_of, pkg_src = {}, {}
    fqn = {}
    for rel in files:
        if not rel.endswith('.kt'):
            continue
        try:
            src = open(os.path.join(root, rel), encoding='utf-8', errors='replace').read()
        except OSError:
            continue
        pkg = PKG_RE.search(src)
        if not pkg:
            continue
        package = pkg.group(1)
        pkg_of[rel] = package
        pkg_src.setdefault(package, []).append(src)
        for m in TOP_DECL_RE.finditer(src):
            fqn[package + '.' + m.group(2)] = rel
        for m in EXT_DECL_RE.finditer(src):
            fqn[package + '.' + m.group(1)] = rel
    packages = set(pkg_of.values())
    by_name = {}
    for f, rel in fqn.items():
        by_name.setdefault(f.rsplit('.', 1)[-1], []).append((f, rel))

    issues = []
    for rel in files:
        if not rel.endswith('.kt'):
            continue
        try:
            src = open(os.path.join(root, rel), encoding='utf-8', errors='replace').read()
        except OSError:
            continue
        for im in IMPORT_RE.finditer(src):
            path_ = im.group('path')
            if not path_.startswith(OWN_PREFIX):
                continue
            line = src.count('\n', 0, im.start()) + 1
            # N2：通配符路径不整行跳过；包不存在判 PACKAGE，包存在放行。
            if path_.endswith('.*'):
                wildcard_pkg = path_[:-2]
                if wildcard_pkg not in packages:
                    issues.append((rel, line, path_, '通配符包在仓内无任何 package 声明'))
                continue
            if path_ in fqn:
                continue
            head = path_
            while '.' in head:
                head = head.rsplit('.', 1)[0]
                if head in fqn:
                    break
            else:
                head = ''
            if head:
                continue  # 嵌套类/伴生成员
            pkg_path = path_.rsplit('.', 1)[0]
            simple = path_.rsplit('.', 1)[-1]
            if any(_declares_name(x, simple) for x in pkg_src.get(pkg_path, [])):
                continue  # 同包声明位确有该声明（声明位严格匹配，不跨函数体）
            others = [f for f, _rel in by_name.get(simple, [])]
            if not others:
                continue  # 名字仓内没有，归 NODEF
            issues.append((rel, line, path_, ' / '.join(others[:3])))
    return issues


def analyze(root: str = ROOT) -> tuple[int, list, list, dict]:
    """跑全仓判定，返回 (rc, call_issues, pkg_issues, stats)。

    call_issues=[(sev, rel, name, line, call_arity, msg)]；pkg_issues=[(rel, line, path_, real)]；
    stats 携带文件数/调用数/三类计数/跳过计数，供 main 打印与 selftest 结构化断言复用。
    拆分目的：selftest 不只比退出码，还能直接断言命中类别标签与被判文件行（N3 加固）。
    """
    files = collect_files(root)
    index: dict[str, list] = {}
    external_names: set[str] = set()
    # 第一遍：建仓内定义索引（.kt 全量解析，.java 仅名字存在性）+ 收集外部 import 归属。
    for rel in collect_def_files(root):
        path = os.path.join(root, rel)
        try:
            src = open(path, encoding='utf-8', errors='replace').read()
        except OSError:
            continue
        parser = parse_java if rel.endswith('.java') else parse_definitions
        for dname, defs in parser(strip_noise(src) if rel.endswith('.kt') else src).items():
            for d in defs:
                d.setdefault('file', rel)
                d.setdefault('private', False)
            index.setdefault(dname, []).extend(defs)
        for im in IMPORT_RE.finditer(src):
            p = im.group('path')
            if p.endswith('.*'):
                continue  # 通配符不提供具体简单名，不进外部名表（N2）。
            last = p.rsplit('.', 1)[-1]
            if p.startswith(OWN_PREFIX):
                pass  # 自有 import 归属在 scan_file 内按本文件判定，不进全局表。
            elif p.startswith(EXTERNAL_ROOTS):
                external_names.add(last)

    nodef = arity = 0
    skip = {'generated': 0, 'external': 0, 'stdlib': 0, 'unknown': 0}
    total_calls = 0
    all_issues = []
    for rel in files:
        issues, skipped, checked = scan_file(rel, root, index, external_names)
        for k in skip:
            skip[k] += skipped[k]
        total_calls += checked + sum(skipped.values())
        for sev, name, line, call_arity, msg in issues:
            all_issues.append((sev, rel, name, line, call_arity, msg))
            if sev == 'NODEF':
                nodef += 1
            else:
                arity += 1

    pkg_bad = package_import_issues(root, files)
    stats = {'files': len(files), 'calls': total_calls, 'nodef': nodef, 'arity': arity,
             'pkg': len(pkg_bad), 'skip': skip}
    rc = 1 if (nodef + arity + len(pkg_bad)) else 0
    return rc, all_issues, pkg_bad, stats


def main(root: str = ROOT) -> int:
    rc, all_issues, pkg_bad, stats = analyze(root)
    for rel, line, path_, real in pkg_bad:
        print('  PACKAGE %s:%d import %s → 仓内同名定义实际在 %s' % (rel, line, path_, real))

    for sev, rel, name, line, call_arity, msg in all_issues:
        print('  %s %s:%d %s(%d) → %s' % (sev, rel, line, name, call_arity, msg))
    skip = stats['skip']
    print('SYMBOL-EXISTENCE: %s（扫描 %d 文件，调用 %d 处，NODEF %d / ARITY %d / PACKAGE %d，'
          '跳过生成 %d / 外部库 %d / 标准库 %d / 未知 %d）'
          % ('FAIL' if rc else 'PASS', stats['files'], stats['calls'],
             stats['nodef'], stats['arity'], stats['pkg'],
             skip['generated'], skip['external'], skip['stdlib'], skip['unknown']))
    return rc


def _kt(root: str, rel: str, src: str) -> None:
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(src)


def _find_call(calls: list, suffix: str, sev: str, name: str | None = None) -> tuple:
    """从 call issues 里按文件后缀 + 类别标签（+符号名）取命中条目；缺命中即断言失败。

    N3 加固：坏样本不再只比退出码——必须断言命中的 severity 标签（NODEF/ARITY/PACKAGE）
    与被判文件行，防止别类红蒙混成本样本通过。
    """
    hits = [c for c in calls if c[1].endswith(suffix) and c[0] == sev
            and (name is None or c[2] == name)]
    assert hits, f"未在调用类 issues 命中 {sev} {suffix}（{name}）；calls={calls}"
    return hits[0]


def _find_pkg(pkgs: list, suffix: str, imp: str) -> tuple:
    """从 PACKAGE issues 里按文件后缀 + import 路径取命中条目。"""
    hits = [p for p in pkgs if p[0].endswith(suffix) and imp in p[2]]
    assert hits, f"未在 PACKAGE issues 命中 {suffix} {imp}；pkgs={pkgs}"
    return hits[0]


def selftest() -> int:
    """双向自测（tempfile 迷你仓，判定回调生产 analyze()，夹具自删）。

    必失败：NODEF / ARITY / 类跨包 PACKAGE / 函数体提及不豁免 PACKAGE / 扩展跨包 PACKAGE /
    通配符包不存在 PACKAGE / 多行函数体内局部 var 同名 / 局部 fun 同名 / 行注释后局部 fun 同名；
    必放行：重载配平 / 一参匹配 / ?. 安全调用 / 嵌套伴生外层 import / 泛型接收者扩展 /
    通配符包存在 / 包级顶层声明 / interface 成员 / object 成员 / 伴生对象成员 / 抽象类成员 /
    行注释后顶层扩展 fun / 块注释夹括号 / 三引号串内 // 字样。每个坏样本断言类别标签与被判文件行（N3）。"""

    # 仓内定义：one 一参无兜底；two 两个重载（一参/两参）；Extension 扩展一参。
    lib = '''package io.legado.app.lib

fun one(a: Int): Int = a
fun two(a: Int): Int = a
fun two(a: Int, b: Int): Int = a + b
fun String.ext(x: Int): String = this + x
'''

    # 必失败样本一 NODEF：调用自有 import 的 missingSym，索引里无定义。调用行在夹具第 5 行。
    t1 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t1, 'app/src/main/java/io/legado/app/lib/Lib.kt', lib)
        _kt(t1, 'app/src/main/java/io/legado/app/use/Use1.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.missingSym\n\n'
            'fun call() = missingSym(1, 2)\n')
        rc, calls, _pkgs, _st = analyze(t1)
        assert rc == 1, f"NODEF 应 FAIL，rc={rc}"
        hit = _find_call(calls, 'Use1.kt', 'NODEF', 'missingSym')
        assert hit[3] == 5, f"NODEF 应判在调用行 5，实际行号 {hit[3]}"
        print('[selftest] 坏样本一（NODEF 自有符号无定义）FAIL 通路 OK：NODEF Use1.kt:5')
    finally:
        shutil.rmtree(t1, ignore_errors=True)

    # 必失败样本二 ARITY：one 只有一参定义，按三参调用，无 vararg/默认参。调用行在夹具第 5 行。
    t2 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t2, 'app/src/main/java/io/legado/app/lib/Lib.kt', lib)
        _kt(t2, 'app/src/main/java/io/legado/app/use/Use2.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.one\n\n'
            'fun call() = one(1, 2, 3)\n')
        rc, calls, _pkgs, _st = analyze(t2)
        assert rc == 1, f"ARITY 应 FAIL，rc={rc}"
        hit = _find_call(calls, 'Use2.kt', 'ARITY', 'one')
        assert hit[3] == 5, f"ARITY 应判在调用行 5，实际行号 {hit[3]}"
        print('[selftest] 坏样本二（ARITY 重载参个数不配平）FAIL 通路 OK：ARITY Use2.kt:5')
    finally:
        shutil.rmtree(t2, ignore_errors=True)

    # 放行样本一：two 两参调用命中两参重载 → PASS。
    t3 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t3, 'app/src/main/java/io/legado/app/lib/Lib.kt', lib)
        _kt(t3, 'app/src/main/java/io/legado/app/use/Use3.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.two\n\n'
            'fun call() = two(1, 2)\n')
        rc, calls, pkgs, _st = analyze(t3)
        assert rc == 0, f"重载放行应 PASS，rc={rc}"
        assert not calls and not pkgs, f"放行仓不应有 issues：{calls} {pkgs}"
        print('[selftest] 放行样本（两参调用命中两参重载）PASS 通路 OK')
    finally:
        shutil.rmtree(t3, ignore_errors=True)

    # 放行样本二：one 一参调用匹配一参定义 → PASS。
    t4 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t4, 'app/src/main/java/io/legado/app/lib/Lib.kt', lib)
        _kt(t4, 'app/src/main/java/io/legado/app/use/Use4.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.one\n\n'
            'fun call() = one(1)\n')
        rc, calls, pkgs, _st = analyze(t4)
        assert rc == 0, f"合法一参应 PASS，rc={rc}"
        assert not calls and not pkgs, f"放行仓不应有 issues：{calls} {pkgs}"
        print('[selftest] 放行样本（一参调用匹配一参定义）PASS 通路 OK')
    finally:
        shutil.rmtree(t4, ignore_errors=True)

    # 放行样本三（2026-11-27 回归夹具）：安全调用 `?.` 打到同名扩展，必须按扩展配平而不是
    # 拿去和同名的顶层一参 fun 比个数。初版漏这个形态，实测把 chapter?.verticalTitle() 判成 ARITY 假红。
    t5 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t5, 'app/src/main/java/io/legado/app/lib/Lib.kt',
            'package io.legado.app.lib\n\n'
            'fun title(a: String): String = a\n'
            'class Chapter(val t: String)\n'
            'fun Chapter.title(): String = t\n')
        _kt(t5, 'app/src/main/java/io/legado/app/use/Use5.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.Chapter\n'
            'import io.legado.app.lib.title\n\n'
            'fun call(c: Chapter?) = c?.title()\n')
        rc, calls, pkgs, _st = analyze(t5)
        assert rc == 0, f"安全调用扩展到扩展定义应 PASS（不得与同名顶层 fun 错配），rc={rc}"
        assert not calls and not pkgs, f"放行仓不应有 issues：{calls} {pkgs}"
        print('[selftest] 放行样本（?. 安全调用打到同名扩展）PASS 通路 OK')
    finally:
        shutil.rmtree(t5, ignore_errors=True)

    # 坏样本三（2026-11-27 CI 红根因）：import 指到别的包去 —— 名字仓内有，包不对，kotlinc 必红。
    t6 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t6, 'app/src/main/java/io/legado/app/ui/book/read/ink/Widget.kt',
            'package io.legado.app.ui.book.read.ink\n\nclass Widget\n')
        _kt(t6, 'app/src/main/java/io/legado/app/ui/book/read/vertical/Other.kt',
            'package io.legado.app.ui.book.read.vertical\n\n'
            'import io.legado.app.ui.book.read.ink.Widget\n')
        # 把 Widget 的 import 改成从 vertical 包引（该包里没有 Widget）→ 必须判 PACKAGE
        _kt(t6, 'app/src/main/java/io/legado/app/ui/book/read/vertical/Bad.kt',
            'package io.legado.app.ui.book.read.vertical\n\n'
            'import io.legado.app.ui.book.read.vertical.Widget\n')
        rc, _calls, pkgs, _st = analyze(t6)
        assert rc == 1, f"跨包错引应 PACKAGE FAIL，rc={rc}"
        hit = _find_pkg(pkgs, 'vertical/Bad.kt',
                        'io.legado.app.ui.book.read.vertical.Widget')
        assert hit[1] == 3, f"PACKAGE 应判在 import 行 3，实际行号 {hit[1]}"
        print('[selftest] 坏样本三（import 换包，类实际在别包）PACKAGE FAIL 通路 OK：PACKAGE Bad.kt:3')
    finally:
        shutil.rmtree(t6, ignore_errors=True)

    # 坏样本四（N1 加固回归锁）：错包源文件 fun 函数体同行提及同名，不得被宽松兜底当成同包声明。
    # 探针 F 旧版复现 rc=0 假阴性；加固后必须 PACKAGE 红。
    t7 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t7, 'app/src/main/java/io/legado/app/alpha/Box.kt',
            'package io.legado.app.alpha\n\nclass AlphaBox(val n: Int)\n')
        _kt(t7, 'app/src/main/java/io/legado/app/beta/Helper.kt',
            'package io.legado.app.beta\n\n'
            'fun helper() { val ref = AlphaBox::class }\n')
        _kt(t7, 'app/src/main/java/io/legado/app/use/Use7.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.beta.AlphaBox\n\n'
            'fun call() = AlphaBox(1)\n')
        rc, _calls, pkgs, _st = analyze(t7)
        assert rc == 1, f"函数体提及不得豁免真跨包错引，rc={rc}"
        hit = _find_pkg(pkgs, 'use/Use7.kt', 'io.legado.app.beta.AlphaBox')
        assert hit[1] == 3, f"PACKAGE 应判在 import 行 3，实际行号 {hit[1]}"
        print('[selftest] 坏样本四（函数体同名提及不豁免，跨包错引）PACKAGE FAIL 通路 OK：PACKAGE Use7.kt:3')
    finally:
        shutil.rmtree(t7, ignore_errors=True)

    # 坏样本五（N4 通路夹具一）：扩展函数定义在 lib 包，导入方错引 other 包 → 必判 PACKAGE。
    t8 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t8, 'app/src/main/java/io/legado/app/lib/Ext.kt',
            'package io.legado.app.lib\n\n'
            'fun String.libExt(x: Int): String = this + x\n')
        _kt(t8, 'app/src/main/java/io/legado/app/use/BadExt.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.other.libExt\n\n'
            'fun call(s: String) = s.libExt(1)\n')
        rc, _calls, pkgs, _st = analyze(t8)
        assert rc == 1, f"扩展函数跨包错引应 PACKAGE FAIL，rc={rc}"
        hit = _find_pkg(pkgs, 'use/BadExt.kt', 'io.legado.app.other.libExt')
        assert hit[1] == 3, f"PACKAGE 应判在 import 行 3，实际行号 {hit[1]}"
        print('[selftest] 坏样本五（扩展函数跨包错引）PACKAGE FAIL 通路 OK：PACKAGE BadExt.kt:3')
    finally:
        shutil.rmtree(t8, ignore_errors=True)

    # 放行样本四（N4 通路夹具二）：嵌套类 / 伴生成员从外层 import，前缀链回退命中即放行。
    t9 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t9, 'app/src/main/java/io/legado/app/delta/Outer.kt',
            'package io.legado.app.delta\n\n'
            'class Outer {\n'
            '    class Nested(val x: Int)\n'
            '    companion object {\n'
            '        const val TAG = "outer"\n'
            '        fun companionMember(): String = TAG\n'
            '    }\n'
            '}\n')
        _kt(t9, 'app/src/main/java/io/legado/app/use/UseNested.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.delta.Outer.Nested\n'
            'import io.legado.app.delta.Outer.companionMember\n\n'
            'fun call() = Nested(1).let { companionMember() }\n')
        rc, calls, pkgs, _st = analyze(t9)
        assert rc == 0, f"嵌套类/伴生成员外层 import 应 PASS，rc={rc}"
        assert not calls and not pkgs, f"放行仓不应有 issues：{calls} {pkgs}"
        print('[selftest] 放行样本（嵌套类/伴生成员外层 import）PASS 通路 OK')
    finally:
        shutil.rmtree(t9, ignore_errors=True)

    # 放行样本五（N4 通路夹具三）：泛型接收者扩展 `fun <T> HashMap<String, T>.get(...)`，
    # EXT_DECL_RE 收不进泛型接收者，由声明位扫描器兜底；同包 import 必须放行，不得误判 PACKAGE。
    t10 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t10, 'app/src/main/java/io/legado/app/utils/MapExt.kt',
            'package io.legado.app.utils\n\n'
            'fun <T> HashMap<String, T>.get(key: String, ignoreCase: Boolean = false): T? = null\n')
        _kt(t10, 'app/src/main/java/io/legado/app/use/UseMap.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.utils.get\n\n'
            'fun call(m: HashMap<String, String>) = m.get("k")\n')
        rc, calls, pkgs, _st = analyze(t10)
        assert rc == 0, f"泛型接收者扩展同包 import 应 PASS，rc={rc}"
        assert not pkgs, f"泛型接收者扩展不得判 PACKAGE：{pkgs}"
        print('[selftest] 放行样本（泛型接收者扩展声明位兜底）PASS 通路 OK')
    finally:
        shutil.rmtree(t10, ignore_errors=True)

    # 坏样本六（N2 加固回归锁）：通配符包在仓内无任何 package 声明 → 必须判 PACKAGE，不得整行跳过。
    t11 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t11, 'app/src/main/java/io/legado/app/lib/Lib.kt', lib)
        _kt(t11, 'app/src/main/java/io/legado/app/use/BadWild.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.missing.*\n')
        rc, _calls, pkgs, _st = analyze(t11)
        assert rc == 1, f"通配符包不存在应 PACKAGE FAIL，rc={rc}"
        hit = _find_pkg(pkgs, 'use/BadWild.kt', 'io.legado.app.missing.*')
        assert hit[1] == 3, f"PACKAGE 应判在 import 行 3，实际行号 {hit[1]}"
        print('[selftest] 坏样本六（通配符包不存在）PACKAGE FAIL 通路 OK：PACKAGE BadWild.kt:3')
    finally:
        shutil.rmtree(t11, ignore_errors=True)

    # 放行样本六（N2 正向锁）：通配符包在仓内有 package 声明 → 放行。
    t12 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t12, 'app/src/main/java/io/legado/app/lib/Lib.kt', lib)
        _kt(t12, 'app/src/main/java/io/legado/app/use/UseWild.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.*\n\n'
            'fun call() = two(1, 2)\n')
        rc, calls, pkgs, _st = analyze(t12)
        assert rc == 0, f"通配符包存在应 PASS，rc={rc}"
        assert not pkgs, f"存在的通配符包不得判 PACKAGE：{pkgs}"
        print('[selftest] 放行样本（通配符包存在）PASS 通路 OK')
    finally:
        shutil.rmtree(t12, ignore_errors=True)

    # 坏样本七（g63 影子声明补丁回归锁一）：多行函数体内局部 `var` 独占一行与目标同名，
    # 不得被上下文帧栈当成包级声明放行；真跨包错引必须判 PACKAGE。
    t13 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t13, 'app/src/main/java/io/legado/app/alpha/Box.kt',
            'package io.legado.app.alpha\n\nclass ShadowBox(val n: Int)\n')
        _kt(t13, 'app/src/main/java/io/legado/app/beta/Helper.kt',
            'package io.legado.app.beta\n\n'
            'fun helper() {\n'
            '    var ShadowBox = "shadow"\n'
            '}\n')
        _kt(t13, 'app/src/main/java/io/legado/app/use/Use13.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.beta.ShadowBox\n\n'
            'fun call() = ShadowBox(1)\n')
        rc, _calls, pkgs, _st = analyze(t13)
        assert rc == 1, f"函数体内局部 var 影子声明不得豁免跨包错引，rc={rc}"
        hit = _find_pkg(pkgs, 'use/Use13.kt', 'io.legado.app.beta.ShadowBox')
        assert hit[1] == 3, f"PACKAGE 应判在 import 行 3，实际行号 {hit[1]}"
        print('[selftest] 坏样本七（多行函数体内局部 var 同名）PACKAGE FAIL 通路 OK：PACKAGE Use13.kt:3')
    finally:
        shutil.rmtree(t13, ignore_errors=True)

    # 坏样本八（g63 影子声明补丁回归锁二）：多行函数体内局部 `fun` 独占一行与目标同名，
    # 同样落在局部上下文帧内，不算声明位，必须判 PACKAGE。
    t14 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t14, 'app/src/main/java/io/legado/app/alpha/Box.kt',
            'package io.legado.app.alpha\n\nclass ShadowBox(val n: Int)\n')
        _kt(t14, 'app/src/main/java/io/legado/app/beta/Helper.kt',
            'package io.legado.app.beta\n\n'
            'fun helper() {\n'
            '    fun ShadowBox() {}\n'
            '}\n')
        _kt(t14, 'app/src/main/java/io/legado/app/use/Use14.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.beta.ShadowBox\n\n'
            'fun call() = ShadowBox(1)\n')
        rc, _calls, pkgs, _st = analyze(t14)
        assert rc == 1, f"函数体内局部 fun 影子声明不得豁免跨包错引，rc={rc}"
        hit = _find_pkg(pkgs, 'use/Use14.kt', 'io.legado.app.beta.ShadowBox')
        assert hit[1] == 3, f"PACKAGE 应判在 import 行 3，实际行号 {hit[1]}"
        print('[selftest] 坏样本八（多行函数体内局部 fun 同名）PACKAGE FAIL 通路 OK：PACKAGE Use14.kt:3')
    finally:
        shutil.rmtree(t14, ignore_errors=True)

    # 放行样本七（g63 正向锁一）：真正的包级顶层 fun 同包 import → 必须放行。
    t15 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t15, 'app/src/main/java/io/legado/app/lib/Top.kt',
            'package io.legado.app.lib\n\nfun topMark(): String = "x"\n')
        _kt(t15, 'app/src/main/java/io/legado/app/use/Use15.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.topMark\n\n'
            'fun call() = topMark()\n')
        rc, calls, pkgs, _st = analyze(t15)
        assert rc == 0, f"包级顶层声明应 PASS，rc={rc}"
        assert not calls and not pkgs, f"放行仓不应有 issues：{calls} {pkgs}"
        print('[selftest] 放行样本（真包级顶层声明）PASS 通路 OK')
    finally:
        shutil.rmtree(t15, ignore_errors=True)

    # 放行样本八（g63 正向锁二）：interface 内成员 fun，声明上下文帧，不得误伤。
    t16 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t16, 'app/src/main/java/io/legado/app/lib/Iface.kt',
            'package io.legado.app.lib\n\n'
            'interface Iface {\n'
            '    fun memberMark(): String\n'
            '}\n')
        _kt(t16, 'app/src/main/java/io/legado/app/use/Use16.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.memberMark\n\n'
            'fun call() = memberMark()\n')
        rc, calls, pkgs, _st = analyze(t16)
        assert rc == 0, f"interface 成员应 PASS，rc={rc}"
        assert not pkgs, f"interface 成员不得判 PACKAGE：{pkgs}"
        print('[selftest] 放行样本（interface 成员）PASS 通路 OK')
    finally:
        shutil.rmtree(t16, ignore_errors=True)

    # 放行样本九（g63 正向锁三）：object 内成员 fun，声明上下文帧，不得误伤。
    t17 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t17, 'app/src/main/java/io/legado/app/lib/Holder.kt',
            'package io.legado.app.lib\n\n'
            'object Holder {\n'
            '    fun memberMark(): String = "x"\n'
            '}\n')
        _kt(t17, 'app/src/main/java/io/legado/app/use/Use17.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.memberMark\n\n'
            'fun call() = memberMark()\n')
        rc, calls, pkgs, _st = analyze(t17)
        assert rc == 0, f"object 成员应 PASS，rc={rc}"
        assert not pkgs, f"object 成员不得判 PACKAGE：{pkgs}"
        print('[selftest] 放行样本（object 成员）PASS 通路 OK')
    finally:
        shutil.rmtree(t17, ignore_errors=True)

    # 放行样本十（g63 正向锁四）：伴生对象内成员 fun，声明上下文帧，不得误伤。
    t18 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t18, 'app/src/main/java/io/legado/app/lib/Companion.kt',
            'package io.legado.app.lib\n\n'
            'class C {\n'
            '    companion object {\n'
            '        fun memberMark(): String = "x"\n'
            '    }\n'
            '}\n')
        _kt(t18, 'app/src/main/java/io/legado/app/use/Use18.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.memberMark\n\n'
            'fun call() = memberMark()\n')
        rc, calls, pkgs, _st = analyze(t18)
        assert rc == 0, f"伴生对象成员应 PASS，rc={rc}"
        assert not pkgs, f"伴生对象成员不得判 PACKAGE：{pkgs}"
        print('[selftest] 放行样本（伴生对象成员）PASS 通路 OK')
    finally:
        shutil.rmtree(t18, ignore_errors=True)

    # 放行样本十一（g63 正向锁五）：抽象类内成员 fun，声明上下文帧，不得误伤。
    t19 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t19, 'app/src/main/java/io/legado/app/lib/Base.kt',
            'package io.legado.app.lib\n\n'
            'abstract class Base {\n'
            '    abstract fun memberMark(): String\n'
            '}\n')
        _kt(t19, 'app/src/main/java/io/legado/app/use/Use19.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.memberMark\n\n'
            'fun call() = memberMark()\n')
        rc, calls, pkgs, _st = analyze(t19)
        assert rc == 0, f"抽象类成员应 PASS，rc={rc}"
        assert not pkgs, f"抽象类成员不得判 PACKAGE：{pkgs}"
        print('[selftest] 放行样本（抽象类成员）PASS 通路 OK')
    finally:
        shutil.rmtree(t19, ignore_errors=True)

    # 放行样本十二（g63 行注释栈回归锁一）：函数体内行注释之后的顶层扩展 fun。行注释换行后
    # 状态机必须退回 code；旧版留在 line 态逐字符吞掉后续代码行，扩展 fun 被误判局部影子声明。
    t20 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t20, 'app/src/main/java/io/legado/app/lib/Ext.kt',
            'package io.legado.app.lib\n\n'
            'fun helper() {\n'
            '    // 体内行注释：其后代码不得被留在 line 态吞掉\n'
            '}\n\n'
            'fun HashMap<String, String>.memberMark(): String? = null\n')
        _kt(t20, 'app/src/main/java/io/legado/app/use/Use20.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.memberMark\n\n'
            'fun call(m: HashMap<String, String>) = m.memberMark()\n')
        rc, calls, pkgs, _st = analyze(t20)
        assert rc == 0, f"行注释后顶层扩展 fun 应 PASS，rc={rc}"
        assert not calls and not pkgs, f"放行仓不应有 issues：{calls} {pkgs}"
        print('[selftest] 放行样本（行注释后顶层扩展 fun）PASS 通路 OK')
    finally:
        shutil.rmtree(t20, ignore_errors=True)

    # 坏样本九（g63 行注释栈回归锁二）：行注释之后的真局部影子 fun 仍落在局部帧内，
    # 修反不得把局部声明放成包级；真跨包错引必须判 PACKAGE。
    t21 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t21, 'app/src/main/java/io/legado/app/alpha/Alpha.kt',
            'package io.legado.app.alpha\n\nclass memberMark(val n: Int)\n')
        _kt(t21, 'app/src/main/java/io/legado/app/beta/Shadow.kt',
            'package io.legado.app.beta\n\n'
            'fun helper() {\n'
            '    // 体内行注释\n'
            '    fun memberMark() {}\n'
            '}\n')
        _kt(t21, 'app/src/main/java/io/legado/app/use/Use21.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.beta.memberMark\n\n'
            'fun call() = memberMark()\n')
        rc, _calls, pkgs, _st = analyze(t21)
        assert rc == 1, f"行注释后真局部影子 fun 不得豁免跨包错引，rc={rc}"
        hit = _find_pkg(pkgs, 'use/Use21.kt', 'io.legado.app.beta.memberMark')
        assert hit[1] == 3, f"PACKAGE 应判在 import 行 3，实际行号 {hit[1]}"
        print('[selftest] 坏样本九（行注释后局部 fun 同名）PACKAGE FAIL 通路 OK：PACKAGE Use21.kt:3')
    finally:
        shutil.rmtree(t21, ignore_errors=True)

    # 放行样本十三（g63 行注释栈回归锁三）：块注释中间夹 `}`，注释内括号不参与帧栈配平。
    t22 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t22, 'app/src/main/java/io/legado/app/lib/Ext.kt',
            'package io.legado.app.lib\n\n'
            'fun helper() {\n'
            '    /* 块注释里夹一个 } 字符，不应弹栈\n'
            '       配平只认代码态的括号 */\n'
            '}\n\n'
            'fun HashMap<String, String>.memberMark(): String? = null\n')
        _kt(t22, 'app/src/main/java/io/legado/app/use/Use22.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.memberMark\n\n'
            'fun call(m: HashMap<String, String>) = m.memberMark()\n')
        rc, calls, pkgs, _st = analyze(t22)
        assert rc == 0, f"块注释夹括号应 PASS，rc={rc}"
        assert not calls and not pkgs, f"放行仓不应有 issues：{calls} {pkgs}"
        print('[selftest] 放行样本（块注释中间夹 }）PASS 通路 OK')
    finally:
        shutil.rmtree(t22, ignore_errors=True)

    # 放行样本十四（g63 行注释栈回归锁四）：三引号串内的行注释字样 `//` 与花括号不进状态机。
    t23 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t23, 'app/src/main/java/io/legado/app/lib/Ext.kt',
            'package io.legado.app.lib\n\n'
            'fun helper() {\n'
            '    val s = """这里写着 // 不是行注释，{ 也不是开帧"""\n'
            '}\n\n'
            'fun HashMap<String, String>.memberMark(): String? = null\n')
        _kt(t23, 'app/src/main/java/io/legado/app/use/Use23.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.memberMark\n\n'
            'fun call(m: HashMap<String, String>) = m.memberMark()\n')
        rc, calls, pkgs, _st = analyze(t23)
        assert rc == 0, f"三引号串内 // 字样应 PASS，rc={rc}"
        assert not calls and not pkgs, f"放行仓不应有 issues：{calls} {pkgs}"
        print('[selftest] 放行样本（三引号串内行注释字样 //）PASS 通路 OK')
    finally:
        shutil.rmtree(t23, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main())
