#!/usr/bin/env python3
"""g64 单测判据真值闸：拦住「文本层合法、运行期必红」的两类单测写法。

两类都是 2026-11-27 CI 实测红出来的，且都在同一轮里被发现两次，说明不是偶发：

A 反射面（REFLECT）：`X::class.members` / `memberProperties` / `import kotlin.reflect.*`。
   本仓 app 单测 classpath 没有 kotlin-reflect（未加依赖），运行期直接抛
   kotlin.jvm.KotlinReflectionNotSupportedError，编译期一点事都没有。实测命中
   DiscoverViewModelTest.kt（反射写法已改写，现 :295 留有事故注释）。要断言「类里没有某个字段」只能读源码文本，不能反射。

B 注释面（RAWSOURCE）：把 readText() 读来的**整份源文件**直接丢进 assertFalse(contains)。
   仓库里「不预判 mastered 新列」这种话本身写在 KDoc 里，整文件 contains 会把注释里的
   这句话自证成红。实测命中 DiscoverModelsTest.kt:200。判据必须落在剥过注释的代码面。
   绑定形态除普通赋值 `val src = File(...).readText()` 外，还包括 lazy 委托
   `val src by lazy { File(...).readText() }`。

2026-11-27 二次加固：旧版 lazy 块体用「首个 `}`」正则截断，被验收实测出双向失真——
readText 藏在首个 `}` 之后的嵌套 lambda 里会漏判（P2）；合法 filterNot 写法的关键词
落在首个 `}` 之后会被假红（P7/P8）；字符串字面量里的 `}` 会把截面提前截断（P3）。
本次把块体提取改成括号 / 字符串状态机配平：区分代码括号与字符串内的 `{` `}`，覆盖
双引号串、字符字面量、三引号串与反斜杠转义；readText 出现在语句块内任何深度都能识别，
白名单（已剥注释面 / 已 filterNot / 已 strip_comments）判断也在整个配平块内生效，
不只看首行或首段。

2026-11-27 三次加固（g64 再加固），两条验收实测假通路：
- D1 先声明后赋值漏判：`var src: String? = null` 与后续跨语句 `src = File(...).readText()`
  旧版只认声明头那一条语句，后续裸赋值不进绑定模型，整文件 contains 断言漏判。本次把 var
  声明后的裸赋值纳入归并，RHS 带 readText 即并入同一绑定，行号回指最初声明行。
- D2 串内白名单字样误放行：本绑定 RHS 内部字符串字面量（如 `replace("filterNot", "")`）
  出现白名单关键词时，旧版子串判定把它当成真 filterNot 调用而误放行。本次先扫字符串区间
  生成掩码副本，readText 与白名单都只在掩码后的代码面上判断。

已知不做（登记在报告清单，不在本单建模）：
- 内联 readText 断言（断言实参内直接 `File(...).readText().contains(...)`，无绑定变量）；
- 分支控制流内多次赋值（不同分支各自 readText，需 CFA 才能判定断言取到哪一支的值）。

口径：只扫 app/src/test 下 *.kt；单行/块注释里出现上述字样不算命中（先剥注释面再判）。
零输入按「没东西可判」直接 PASS，不假红。
"""
import re
import shutil
import sys
import tempfile
import os

TEST_ROOT = 'app/src/test'

REFLECT_PAT = re.compile(
    r'(::class\s*\.\s*(members|memberProperties|memberFunctions|declaredMembers|declaredMemberFunctions)'
    r'|import\s+kotlin\.reflect)'
)

# 声明头：val / var 绑定名（含可选类型标注），修饰符（private 等）在前面不影响匹配。
DECL_HEAD_PAT = re.compile(
    r'\b(?P<kind>val|var)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)'
    r'\s*(?::\s*[A-Za-z0-9_<>.?]+)?'
)

# 赋值 RHS 的语句边界：行首出现下一条声明 / 成员 / 注解即截断（注释面已先剥掉，无需再认 //）。
RHS_BOUNDARY_WORDS = ('val ', 'var ', 'fun ', 'private ', 'import ', '@')

ASSERT_PAT = re.compile(r'(assertFalse|assertTrue)\s*\(')

# 空断言：判据恒真/恒假，等于没断言（下限：断言必须能被打假）
EMPTY_ASSERT_PAT = re.compile(
    r'(assertTrue\s*\(\s*true\s*\)|assertFalse\s*\(\s*false\s*\)'
    r'|assertTrue\s*\(\s*1\s*==\s*1\s*\)|assertNotNull\s*\(\s*"[^"]*"\s*\))'
)

# 白名单关键词：块体内出现即视为已剥注释面 / 已滤行，属合规写法。
WHITELIST_MARKS = ('filterNot', 'filter {', 'strip_comments')


def strip_comments(text: str) -> str:
    """剥掉 // 行注释、/* */ 块注释，并把字符串外的内容原样留下。
    字符串里的 // 不能误剥，故按字符状态机走，不图省事用正则整体替换。"""
    out = []
    i, n = 0, len(text)
    state = None  # None / 'line' / 'block' / 'str' / 'raw'
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        three = text[i:i + 3]
        if state is None:
            if two == '//':
                state = 'line'
                i += 2
                continue
            if two == '/*':
                state = 'block'
                i += 2
                continue
            if three == '"""':
                state = 'raw'
                out.append(three)
                i += 3
                continue
            if c == '"':
                state = 'str'
                out.append(c)
                i += 1
                continue
            out.append(c)
            i += 1
            continue
        if state == 'line':
            if c == '\n':
                state = None
                out.append(c)
            i += 1
            continue
        if state == 'block':
            if two == '*/':
                state = None
                i += 2
                continue
            if c == '\n':
                out.append(c)
            i += 1
            continue
        if state == 'str':
            out.append(c)
            if c == '\\':
                out.append(text[i + 1:i + 2])
                i += 2
                continue
            if c == '"':
                state = None
            i += 1
            continue
        if state == 'raw':
            if three == '"""':
                state = None
                out.append(three)
                i += 3
                continue
            out.append(c)
            i += 1
            continue
    return ''.join(out)


def line_of(text: str, idx: int) -> int:
    return text.count('\n', 0, idx) + 1


def _skip_ws(text: str, i: int) -> int:
    n = len(text)
    while i < n and text[i] in ' \t\r\n':
        i += 1
    return i


def _scan_string_ranges(text: str) -> list:
    """返回字符串 / 字符字面量的 [start, end) 区间（含引号）。
    为什么要单独扫一遍：声明头正则若落在字符串字面量内部（如测试串里写着 "val x ="），
    不能当成真实绑定；区间表供匹配点回查。"""
    ranges = []
    i, n = 0, len(text)
    state = None  # None / 'str' / 'char' / 'raw'
    start = 0
    while i < n:
        c = text[i]
        three = text[i:i + 3]
        if state is None:
            if three == '"""':
                state, start, i = 'raw', i, i + 3
                continue
            if c == '"':
                state, start, i = 'str', i, i + 1
                continue
            if c == "'":
                state, start, i = 'char', i, i + 1
                continue
            i += 1
            continue
        if state == 'raw':
            if three == '"""':
                ranges.append((start, i + 3))
                state, i = None, i + 3
                continue
            i += 1
            continue
        if state == 'str':
            if c == '\\':
                i += 2
                continue
            if c == '"':
                ranges.append((start, i + 1))
                state = None
            i += 1
            continue
        if state == 'char':
            if c == '\\':
                i += 2
                continue
            if c == "'":
                ranges.append((start, i + 1))
                state = None
            i += 1
            continue
    return ranges


def _in_ranges(ranges: list, idx: int) -> bool:
    return any(a <= idx < b for a, b in ranges)


def _mask_strings(code: str) -> str:
    """把字符串 / 字符字面量区间内的字符换成空格（换行保留），返回与 code 等长的掩码副本。

    为什么要掩码：readText / 白名单标记（filterNot、filter {、strip_comments）必须判在代码面；
    测试串里写着 `"filterNot"` 这类字样时，子串命中只是被断言的文本，不是真调用。区间外的
    字符原样留下，区间内逐字符置空，下标与行号都不变，切片可直接对齐回 code。
    """
    ranges = _scan_string_ranges(code)
    chars = list(code)
    for a, b in ranges:
        for i in range(a, b):
            if chars[i] != '\n':
                chars[i] = ' '
    return ''.join(chars)


def _slice_has(masked: str, start: int, expr: str, mark: str) -> bool:
    """判断 expr 截面（在 masked 副本上对齐）代码面是否出现 mark。"""
    return mark in masked[start:start + len(expr)]


def _balanced_block(text: str, i: int, open_ch: str, close_ch: str) -> tuple:
    """从 text[i] 的开括号起做括号 / 字符串感知配平，返回 (块体文本, 闭括号下标)。

    字符串（双引号串 / 字符字面量 / 三引号串）内的括号不计深度；双引号串与字符串
    内的反斜杠转义跳过下一字符。配平失败（源码截断）兜底取到末尾，不抛异常。
    """
    depth = 0
    state = None  # None / 'str' / 'char' / 'raw'
    n = len(text)
    j = i
    while j < n:
        c = text[j]
        three = text[j:j + 3]
        if state is None:
            if three == '"""':
                state, j = 'raw', j + 3
                continue
            if c == '"':
                state, j = 'str', j + 1
                continue
            if c == "'":
                state, j = 'char', j + 1
                continue
            if c == open_ch:
                depth += 1
            elif c == close_ch:
                depth -= 1
                if depth == 0:
                    return text[i + 1:j], j
            j += 1
            continue
        if state == 'raw':
            if three == '"""':
                state, j = None, j + 3
                continue
            j += 1
            continue
        if state == 'str':
            if c == '\\':
                j += 2
                continue
            if c == '"':
                state = None
            j += 1
            continue
        if state == 'char':
            if c == '\\':
                j += 2
                continue
            if c == "'":
                state = None
            j += 1
            continue
    return text[i + 1:], n - 1


# 续行尾运算符：当前物理行以这些字符收尾时，下一行视为链式 / 表达式续行，RHS 不收口。
RHS_TAIL_OPERATORS = set('(,+-*/%&|?:=')


def _rhs_until_boundary(text: str, i: int) -> str:
    """赋值 RHS：括号 / 字符串感知地取到本语句结束为止。

    收口条件（深度 0 的换行处判断）：行首出现下一条声明边界词；或当前语句已结束，
    且下一行不是链式续行（不以 `.` 开头）、当前行尾也没有未闭合括号 / 续行运算符。
    为什么必须按语句收口：旧实现一直吞到下一条声明行，会把后面兄弟语句 / 辅助函数
    体一起卷进截面，readText 出现在被吞进的辅助函数里就会把派生变量误登记为全文持有人。
    字符串内的换行 / 括号不参与边界判断。
    """
    n = len(text)
    depth = 0
    state = None
    j = i
    start = i
    line_start = i
    while j < n:
        c = text[j]
        three = text[j:j + 3]
        if state is None:
            if three == '"""':
                state, j = 'raw', j + 3
                continue
            if c == '"':
                state, j = 'str', j + 1
                continue
            if c == "'":
                state, j = 'char', j + 1
                continue
            if c in '([{':
                depth += 1
            elif c in ')]}':
                depth -= 1
            elif c == '\n' and depth == 0:
                k = j + 1
                while k < n and text[k] in ' \t':
                    k += 1
                next_is_boundary = any(text[k:k + len(w)] == w for w in RHS_BOUNDARY_WORDS)
                next_is_chain = text[k:k + 1] == '.'
                tail = text[line_start:j].rstrip()
                tail_opened = bool(tail) and tail[-1] in RHS_TAIL_OPERATORS
                blank_line = not tail.strip()  # RHS 换行起步（`=` 后直接换行），下一物理行仍是续行
                if next_is_boundary or not (next_is_chain or tail_opened or blank_line):
                    return text[start:j]
                line_start = j + 1
            j += 1
            continue
        if state == 'raw':
            if three == '"""':
                state, j = None, j + 3
                continue
            j += 1
            continue
        if state == 'str':
            if c == '\\':
                j += 2
                continue
            if c == '"':
                state = None
            j += 1
            continue
        if state == 'char':
            if c == '\\':
                j += 2
                continue
            if c == "'":
                state = None
            j += 1
            continue
    return text[start:]


def _assert_arg_body(code: str, start: int) -> str:
    """取断言实参范围：从 '(' 起做括号 / 字符串感知配平，最多 400 字符，够覆盖单条断言。"""
    depth = 0
    state = None
    j = start
    n = len(code)
    while j < n and j - start < 400:
        c = code[j]
        three = code[j:j + 3]
        if state is None:
            if three == '"""':
                state, j = 'raw', j + 3
                continue
            if c == '"':
                state, j = 'str', j + 1
                continue
            if c == "'":
                state, j = 'char', j + 1
                continue
            if c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
            continue
        if state == 'raw':
            if three == '"""':
                state, j = None, j + 3
                continue
            j += 1
            continue
        if state == 'str':
            if c == '\\':
                j += 2
                continue
            if c == '"':
                state = None
            j += 1
            continue
        if state == 'char':
            if c == '\\':
                j += 2
                continue
            if c == "'":
                state = None
            j += 1
            continue
    return code[start:j + 1]


def extract_readtext_bindings(code: str) -> dict:
    """收集「读全文且未过滤」的绑定名 → 声明行号。

    三种绑定形态都认：
    - 普通赋值 `val src = ...`；
    - lazy 委托 `val src by lazy { ... }`；
    - 先声明后赋值（D1）：`var src: String? = null` 之后跨语句 `src = File(...).readText()`，
      两条语句归并为同一绑定，绑定行号回指最初的声明行。

    块体由状态机配平提取，嵌套 lambda 任意深度、字符串内括号都不影响截面；readText 与
    白名单关键词只判掩码副本上的代码面，字符串字面量内部的字样不算真调用（D2）。
    """
    str_ranges = _scan_string_ranges(code)
    masked = _mask_strings(code)
    bindings = {}
    declared = {}  # name → (声明行号, 是否 var)；var 才可被后续裸赋值归并

    for m in DECL_HEAD_PAT.finditer(code):
        if _in_ranges(str_ranges, m.start()):
            continue  # 关键词落在字符串字面量里，不是真实声明
        name = m.group('name')
        decl_line = line_of(code, m.start())
        declared.setdefault(name, (decl_line, m.group('kind') == 'var'))
        j = _skip_ws(code, m.end())
        expr = None
        start = -1

        # 形态一：by lazy { 块体 }
        if code[j:j + 2] == 'by' and (j + 2 >= len(code) or code[j + 2] in ' \t\r\n'):
            k = _skip_ws(code, j + 2)
            if code[k:k + 4] == 'lazy' and (k + 4 >= len(code) or code[k + 4] in ' \t\r\n({'):
                k = _skip_ws(code, k + 4)
                if k < len(code) and code[k] == '{':
                    expr, _ = _balanced_block(code, k, '{', '}')
                    start = k + 1

        # 形态二：= RHS（含续行链式调用）
        if expr is None and j < len(code) and code[j] == '=':
            start = j + 1
            expr = _rhs_until_boundary(code, start)

        if expr is None:
            continue
        if not _slice_has(masked, start, expr, 'readText'):
            continue  # 绑定 RHS / lazy 块代码面没有 readText，不属本闸对象
        if any(_slice_has(masked, start, expr, mark) for mark in WHITELIST_MARKS):
            continue  # 已剥注释面 / 已滤行，属合规写法（串内字样已被掩码剔除）
        bindings.setdefault(name, decl_line)

    # D1 跨语句归并：var 声明后又出现裸赋值 `name = ...`，RHS 代码面带 readText 即并入同一绑定。
    # 为什么只认 var：val 不可重新赋值；为什么按声明行号回指：断言提示要能定位到最初声明处。
    for name, (decl_line, is_var) in declared.items():
        if name in bindings or not is_var:
            continue
        pat = re.compile(r'(?<![A-Za-z0-9_])' + re.escape(name) + r'\s*=(?!=)')
        for am in pat.finditer(code):
            if _in_ranges(str_ranges, am.start()):
                continue
            rhs_start = am.end()
            expr = _rhs_until_boundary(code, rhs_start)
            if not _slice_has(masked, rhs_start, expr, 'readText'):
                continue
            if any(_slice_has(masked, rhs_start, expr, mark) for mark in WHITELIST_MARKS):
                continue
            bindings.setdefault(name, decl_line)
            break

    return bindings


def check_file(path: str):
    raw = open(path, encoding='utf-8').read()
    code = strip_comments(raw)
    issues = []

    for m in REFLECT_PAT.finditer(code):
        issues.append(('REFLECT', line_of(code, m.start()),
                       f'反射取成员（{m.group(0).strip()}）：单测 classpath 无 kotlin-reflect，运行期抛 KotlinReflectionNotSupportedError，改读源码文本断言'))

    for m in EMPTY_ASSERT_PAT.finditer(code):
        issues.append(('EMPTYASSERT', line_of(code, m.start()),
                       f'空断言（{m.group(0).strip()}）判据恒真，打不假任何实现，等于没测'))

    raw_vals = extract_readtext_bindings(code)

    for m in ASSERT_PAT.finditer(code):
        body = _assert_arg_body(code, m.end() - 1)
        for name, decl_line in raw_vals.items():
            if re.search(r'\b' + re.escape(name) + r'\.contains\s*\(', body):
                issues.append(('RAWSOURCE', line_of(code, m.start()),
                               f'拿 readText() 全文 {name}（第 {decl_line} 行）直接做 contains 断言：源文件 KDoc 里写着同一句话就自证成红，必须先在代码面剥注释再断'))
    return issues


def collect(root: str):
    files = []
    for dirpath, _, names in os.walk(root):
        for nm in names:
            if nm.endswith('.kt'):
                files.append(os.path.join(dirpath, nm))
    return sorted(files)


def changed_files() -> list:
    """相对 origin/main 新增/改动的测试文件：门禁只判本轮改动面。
    全仓存量是历史件「源码文本断言」，属债务不是本轮回归，按 audit 台账处理，
    否则一上来就红墙、没人会去修它，门禁只剩噪声。"""
    import subprocess
    try:
        out = subprocess.run(
            ['git', '-c', 'core.quotepath=false', 'diff', '--name-only', 'origin/main', '--', TEST_ROOT],
            capture_output=True, text=True, timeout=60)
        names = [n.strip() for n in out.stdout.splitlines() if n.strip().endswith('.kt')]
    except Exception:
        names = []
    return [n for n in names if os.path.isfile(n)]


def main(root: str = TEST_ROOT, only_changed: bool = False) -> int:
    files = changed_files() if only_changed else collect(root)
    total, bad = 0, 0
    for f in files:
        rel = os.path.relpath(f, root)
        issues = check_file(f) if f.endswith('.kt') else []
        for kind, line, msg in issues:
            bad += 1
            print(f'FAIL {f}:{line} [{kind}] {msg}')
        total += 1
    if bad:
        print(f'TEST-ASSERT-TRUTH: FAIL（扫描 {total} 个测试文件，命中 {bad} 处）')
        return 1
    print(f'TEST-ASSERT-TRUTH: PASS（扫描 {total} 个测试文件，REFLECT/RAWSOURCE/EMPTYASSERT 三类 0 命中）')
    return 0


def _kt(root, rel, body):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(body)


def _one_file(body: str) -> int:
    """单样本独立跑：一个临时目录只放一个夹具文件，返回 main() 退出码。
    为什么逐样本独立：必失败样本必须自己能跑红，不能靠聚合结果一次性盖住。"""
    d = tempfile.mkdtemp(prefix='test-assert-truth-')
    try:
        _kt(d, 'a/Sample.kt', body)
        return main(d)
    finally:
        shutil.rmtree(d, ignore_errors=True)


def selftest() -> int:
    # 聚合基线：坏样本同目录，闸门必须 FAIL（放行样本不进聚合，避免盖住坏样本）
    d = tempfile.mkdtemp(prefix='test-assert-truth-')
    try:
        _kt(d, 'a/BadReflectTest.kt',
            'package a\nclass BadReflectTest { fun t() { Foo::class.members } }\n')
        _kt(d, 'a/BadRawSourceTest.kt',
            'package a\nclass BadRawSourceTest {\n'
            '    fun t() {\n'
            '        val src = java.io.File("x.kt").readText()\n'
            '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
            '    }\n}\n')
        _kt(d, 'a/BadLazyTest.kt',
            'package a\nclass BadLazyTest {\n'
            '    fun t() {\n'
            '        val src by lazy { java.io.File("x.kt").readText() }\n'
            '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
            '    }\n}\n')
        assert main(d) == 1, '聚合坏样本应 FAIL'
    finally:
        shutil.rmtree(d, ignore_errors=True)

    # 坏样本逐条独立跑红（含嵌套 lambda、字符串含 } 的边界夹具）
    bad_cases = [
        ('REFLECT 反射取成员',
         'package a\nclass BadReflectTest { fun t() { Foo::class.members } }\n'),
        ('RAWSOURCE 赋值整文件 contains',
         'package a\nclass BadRawSourceTest {\n'
         '    fun t() {\n'
         '        val src = java.io.File("x.kt").readText()\n'
         '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
         '    }\n}\n'),
        ('RAWSOURCE lazy 委托整文件 contains（P1 基线）',
         'package a\nclass BadLazyTest {\n'
         '    fun t() {\n'
         '        val src by lazy { java.io.File("x.kt").readText() }\n'
         '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
         '    }\n}\n'),
        ('RAWSOURCE 嵌套 lambda 内 readText（P2，首 } 之后）',
         'package a\nclass BadNestedLambdaTest {\n'
         '    fun t() {\n'
         '        val src by lazy {\n'
         '            listOf(1).map { it }.let { _ -> java.io.File("x.kt").readText() }\n'
         '        }\n'
         '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
         '    }\n}\n'),
        ('RAWSOURCE 字符串含 } 的坏样本（P3）',
         'package a\nclass BadBraceInStringTest {\n'
         '    fun t() {\n'
         '        val src by lazy { java.io.File("x.kt").readText().replace("}", "") }\n'
         '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
         '    }\n}\n'),
        ('RAWSOURCE 先声明 null 后跨语句赋值 readText（D1）',
         'package a\nclass BadReassignTest {\n'
         '    fun t() {\n'
         '        var src: String? = null\n'
         '        src = java.io.File("x.kt").readText()\n'
         '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
         '    }\n}\n'),
        ('RAWSOURCE RHS 串内写着 filterNot 字样（D2）',
         'package a\nclass BadStringMarkTest {\n'
         '    fun t() {\n'
         '        val src = java.io.File("x.kt").readText().replace("filterNot", "")\n'
         '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
         '    }\n}\n'),
        ('EMPTYASSERT 空断言',
         'package a\nclass BadEmptyTest { fun t() { org.junit.Assert.assertTrue(true) } }\n'),
    ]
    for label, body in bad_cases:
        assert _one_file(body) == 1, f'{label} 单样本应 FAIL'
        print(f'[selftest] 坏样本（{label}）独立 FAIL OK')

    # 放行样本逐条独立跑绿：已剥注释面 / 已 filterNot 的合法写法不许假红
    good_cases = [
        ('剥注释面（赋值 + 续行 filterNot）',
         'package a\nclass GoodTest {\n'
         '    fun t() {\n'
         '        val code = java.io.File("x.kt").readText().lines()\n'
         '            .filterNot { it.trimStart().startsWith("*") }\n'
         '            .joinToString("\\n")\n'
         '        org.junit.Assert.assertFalse("不得预判", code.contains("mastered"))\n'
         '    }\n}\n'),
        ('剥注释面（lazy 块内 filterNot）',
         'package a\nclass GoodLazyTest {\n'
         '    fun t() {\n'
         '        val code by lazy {\n'
         '            java.io.File("x.kt").readText().lines()\n'
         '                .filterNot { it.trimStart().startsWith("*") }\n'
         '                .joinToString("\\n")\n'
         '        }\n'
         '        org.junit.Assert.assertFalse("k", code.contains("mastered"))\n'
         '    }\n}\n'),
        ('剥注释面（P7，filterNot 在内层 lambda 闭括号之后）',
         'package a\nclass GoodNestedFilterTest {\n'
         '    fun t() {\n'
         '        val code by lazy {\n'
         '            java.io.File("x.kt").readText().lines()\n'
         '                .map { it.trim() }\n'
         '                .filterNot { it.trimStart().startsWith("*") }\n'
         '        }\n'
         '        org.junit.Assert.assertFalse("k", code.contains("mastered"))\n'
         '    }\n}\n'),
        ('剥注释面（P8，字符串内 } 之后接 filterNot）',
         'package a\nclass GoodBraceInStringFilterTest {\n'
         '    fun t() {\n'
         '        val code by lazy {\n'
         '            java.io.File("x.kt").readText().replace("}", "")\n'
         '                .filterNot { it.trimStart().startsWith("*") }\n'
         '        }\n'
         '        org.junit.Assert.assertFalse("k", code.contains("mastered"))\n'
         '    }\n}\n'),
        ('剥注释面（D1，先声明 null 后跨语句赋值 filterNot 链）',
         'package a\nclass GoodReassignTest {\n'
         '    fun t() {\n'
         '        var code: String? = null\n'
         '        code = java.io.File("x.kt").readText().lines()\n'
         '            .filterNot { it.trimStart().startsWith("*") }\n'
         '            .joinToString("\\n")\n'
         '        org.junit.Assert.assertFalse("k", code.contains("mastered"))\n'
         '    }\n}\n'),
        ('剥注释面（D2，串内 filterNot 字样 + 代码面真 filterNot）',
         'package a\nclass GoodStringMarkFilterTest {\n'
         '    fun t() {\n'
         '        val code = java.io.File("x.kt").readText().replace("filterNot", "")\n'
         '            .filterNot { it.trimStart().startsWith("*") }\n'
         '        org.junit.Assert.assertFalse("k", code.contains("mastered"))\n'
         '    }\n}\n'),
        ('普通字符串断言（无 readText 绑定）',
         'package a\nclass GoodPlainTest { fun t() { val code = "x".trim()\n'
         '        org.junit.Assert.assertFalse("k", code.contains("mastered")) } }\n'),
    ]
    for label, body in good_cases:
        assert _one_file(body) == 0, f'{label} 单样本应 PASS'
        print(f'[selftest] 放行样本（{label}）独立 PASS OK')

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    root = TEST_ROOT
    only_changed = '--changed' in sys.argv
    for a in sys.argv[1:]:
        if not a.startswith('-'):
            root = a
    sys.exit(main(root, only_changed))
