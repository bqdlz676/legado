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
退出码：0 = 无 NODEF / ARITY；1 = 有 FAIL。
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
    r'^import\s+(?P<path>[A-Za-z0-9_.]+)(?:\s+as\s+(?P<alias>\w+))?',
    re.MULTILINE)  # 必须多行模式：import 常位于 package 之后的非首行，裸 ^ 只匹首行。


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


def package_import_issues(root: str, files: list) -> list:
    """包级 import 真值（2026-11-27 CI 红根因 c82c585）。
    InkReadController.kt 写了 `import io.legado.app.ui.book.read.vertical.VerticalPageView`，
    而该类定义在同包 `io.legado.app.ui.book.read.ink/VerticalPageView.kt`；kotlinc 报
    Unresolved reference。旧判据只查「这个名字仓内有没有定义」——名字当然有，包不对照样红。

    判法（只认一种错：换了包）：被 import 的 FQN 在仓内找不到同包声明，却在别的包里有同名声明。
    三重放行，防假红（三条都是实跑踩出来的）：
    A 嵌套类 / 伴生成员 —— FQN 前缀链逐级回退命中即放行；
    B 扩展函数接收者带泛型/空格/逗号（`fun <T> HashMap<String, T>.get(...)`）—— 用「同包内声明名宽松搜索」兜住；
    C 名字仓内根本没有 —— 归 NODEF，这里不重复报。
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
            if not path_.startswith(OWN_PREFIX) or path_ in fqn:
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
            loose = re.compile(
                r'^\s*(?:[A-Za-z@ ]*\s)?(?:fun\s+.*?\b|(?:class|object|interface|typealias|val|var)\s+)'
                + re.escape(simple) + r'\b', re.MULTILINE)
            if any(loose.search(x) for x in pkg_src.get(pkg_path, [])):
                continue  # 同包内确有该声明（宽松兜底）
            others = [f for f, _rel in by_name.get(simple, [])]
            if not others:
                continue  # 名字仓内没有，归 NODEF
            line = src.count('\n', 0, im.start()) + 1
            issues.append((rel, line, path_, ' / '.join(others[:3])))
    return issues


def main(root: str = ROOT) -> int:
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
    for rel, line, path_, real in pkg_bad:
        print('  PACKAGE %s:%d import %s → 仓内同名定义实际在 %s' % (rel, line, path_, real))

    for sev, rel, name, line, call_arity, msg in all_issues:
        print('  %s %s:%d %s(%d) → %s' % (sev, rel, line, name, call_arity, msg))
    print('SYMBOL-EXISTENCE: %s（扫描 %d 文件，调用 %d 处，NODEF %d / ARITY %d / PACKAGE %d，'
          '跳过生成 %d / 外部库 %d / 标准库 %d / 未知 %d）'
          % ('FAIL' if (nodef + arity + len(pkg_bad)) else 'PASS', len(files), total_calls,
             nodef, arity, len(pkg_bad), skip['generated'], skip['external'], skip['stdlib'], skip['unknown']))
    return 1 if (nodef + arity + len(pkg_bad)) else 0


def _kt(root: str, rel: str, src: str) -> None:
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(src)


def selftest() -> int:
    """双向自测（tempfile 迷你仓，判定回调生产 main()，夹具自删）：
    必失败 NODEF 一条、必失败 ARITY 一条；重载放行一条、合法一参一条必通过。"""

    # 仓内定义：one 一参无兜底；two 两个重载（一参/两参）；Extension 扩展一参。
    lib = '''package io.legado.app.lib

fun one(a: Int): Int = a
fun two(a: Int): Int = a
fun two(a: Int, b: Int): Int = a + b
fun String.ext(x: Int): String = this + x
'''

    # 必失败样本一 NODEF：调用自有 import 的 missingSym，索引里无定义。
    t1 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t1, 'app/src/main/java/io/legado/app/lib/Lib.kt', lib)
        _kt(t1, 'app/src/main/java/io/legado/app/use/Use1.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.missingSym\n\n'
            'fun call() = missingSym(1, 2)\n')
        rc = main(t1)
        assert rc == 1, f"NODEF 应 FAIL，rc={rc}"
        print('[selftest] 坏样本一（NODEF 自有符号无定义）FAIL 通路 OK')
    finally:
        shutil.rmtree(t1, ignore_errors=True)

    # 必失败样本二 ARITY：one 只有一参定义，按三参调用，无 vararg/默认参。
    t2 = tempfile.mkdtemp(prefix='symbol-exist-selftest-')
    try:
        _kt(t2, 'app/src/main/java/io/legado/app/lib/Lib.kt', lib)
        _kt(t2, 'app/src/main/java/io/legado/app/use/Use2.kt',
            'package io.legado.app.use\n\n'
            'import io.legado.app.lib.one\n\n'
            'fun call() = one(1, 2, 3)\n')
        rc = main(t2)
        assert rc == 1, f"ARITY 应 FAIL，rc={rc}"
        print('[selftest] 坏样本二（ARITY 重载参个数不配平）FAIL 通路 OK')
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
        rc = main(t3)
        assert rc == 0, f"重载放行应 PASS，rc={rc}"
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
        rc = main(t4)
        assert rc == 0, f"合法一参应 PASS，rc={rc}"
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
        rc = main(t5)
        assert rc == 0, f"安全调用扩展到扩展定义应 PASS（不得与同名顶层 fun 错配），rc={rc}"
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
        rc = main(t6)
        assert rc == 1, f"跨包错引应 PACKAGE FAIL，rc={rc}"
        print('[selftest] 坏样本三（import 换包，类实际在别包）PACKAGE FAIL 通路 OK')
    finally:
        shutil.rmtree(t6, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main())
