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
   `val src by lazy { File(...).readText() }`；2026-11-27 加固前正则只认 `=`，lazy 块里
   的绑定名抓不到，整文件 contains 被漏判，本次一并补齐。

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

# 普通赋值绑定：val src[: String] = File(...).readText()[.lines().filterNot{...}...]
# RHS 截到下一条声明（val/var/fun/private/import/@/注释）为止，续行链式调用留在截面上：
# filterNot 写在续行时旧正则只截到 readText()，白名单看不见，会把已剥注释面的合法写法打成假红。
ASSIGN_READTEXT_PAT = re.compile(
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*[A-Za-z0-9_<>.?]+)?\s*='
    r'\s*(?P<expr>.*?)(?=\n\s*(?:val\s|var\s|fun\s|private\s|import\s|@|//)|\Z)', re.S)

# lazy 委托绑定：val src[: String] by lazy { File(...).readText()[....] }
# 加固前盲区：readText 包进 lazy 块后旧正则只认 `=`，绑定名抓不到，整文件 contains 漏判。
LAZY_READTEXT_PAT = re.compile(
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*[A-Za-z0-9_<>.?]+)?'
    r'\s*by\s+lazy\s*\{(?P<expr>.*?)\}', re.S)

ASSERT_PAT = re.compile(r'(assertFalse|assertTrue)\s*\(')

# 空断言：判据恒真/恒假，等于没断言（下限：断言必须能被打假）
EMPTY_ASSERT_PAT = re.compile(
    r'(assertTrue\s*\(\s*true\s*\)|assertFalse\s*\(\s*false\s*\)'
    r'|assertTrue\s*\(\s*1\s*==\s*1\s*\)|assertNotNull\s*\(\s*"[^"]*"\s*\))'
)


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

    # 读全文且未过滤的变量名（普通赋值与 lazy 委托两种绑定形态都认）
    raw_vals = {}
    for pat in (ASSIGN_READTEXT_PAT, LAZY_READTEXT_PAT):
        for m in pat.finditer(code):
            expr = m.group('expr')
            if 'readText' not in expr:
                continue  # 绑定 RHS / lazy 块内没有 readText，不属本闸对象
            if 'filterNot' in expr or 'filter {' in expr or 'strip_comments' in expr:
                continue  # 已剥注释面 / 已滤行，属合规写法
            raw_vals.setdefault(m.group('name'), line_of(code, m.start()))

    for m in ASSERT_PAT.finditer(code):
        # 取本次断言的实参范围：从 '(' 起做括号配平，最多 400 字符，够覆盖单条断言
        start = m.end() - 1
        depth, j = 0, start
        while j < len(code) and j - start < 400:
            if code[j] == '(':
                depth += 1
            elif code[j] == ')':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = code[start:j + 1]
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
    全仓存量 306 处是历史件「源码文本断言」，属债务不是本轮回归，按 audit 台账处理，
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

    # 坏样本逐条独立跑红（含本次新增的 lazy 委托整文件 contains）
    bad_cases = [
        ('REFLECT 反射取成员',
         'package a\nclass BadReflectTest { fun t() { Foo::class.members } }\n'),
        ('RAWSOURCE 赋值整文件 contains',
         'package a\nclass BadRawSourceTest {\n'
         '    fun t() {\n'
         '        val src = java.io.File("x.kt").readText()\n'
         '        org.junit.Assert.assertFalse("不得预判", src.contains("mastered"))\n'
         '    }\n}\n'),
        ('RAWSOURCE lazy 委托整文件 contains',
         'package a\nclass BadLazyTest {\n'
         '    fun t() {\n'
         '        val src by lazy { java.io.File("x.kt").readText() }\n'
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
