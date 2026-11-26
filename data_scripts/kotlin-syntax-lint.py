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
import json
import os
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
        elif c == '`':
            # 反引号标识符（测试函数名常见，如 `M4 ... 不误判 ASSET`）：整段按不可见处理，
            # 否则名字里的大写词会被 S8 当成无出处符号误报（实测 HeightMapDecoderTest.kt:374）。
            i += 1
            while i < n and src[i] != '`':
                out.append('\n' if src[i] == '\n' else ' ')
                i += 1
            if i < n:
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


def check_style_only_params(clean: str):
    """S3：BasicText/Text 不接 fontFamily —— 它只能写在 TextStyle 里。

    实测 2026-11-26：B10 接线在 `BasicText(text=..., style=..., fontFamily=...)`
    上把 fontFamily 当参数直传，两路 CI 编译红。自定义壳 `InkText`/`SpanStyle`
    确实接这个参数，所以只对 BasicText/Text 两个真不接的名字判死，避免误杀。
    """
    errs = []
    for m in re.finditer(r'[Ff]ontFamily\s*=', clean):
        pre = clean[:m.start()]
        depth, i = 0, len(pre) - 1
        while i >= 0:
            c = pre[i]
            if c == ')':
                depth += 1
            elif c == '(':
                if depth == 0:
                    name = re.search(r'([A-Za-z_]\w*)\s*$', pre[:i])
                    if name and name.group(1) in ('BasicText', 'Text'):
                        errs.append((clean.count('\n', 0, m.start()) + 1,
                                     '%s 不接受 fontFamily 参数（应写进 TextStyle）' % name.group(1)))
                    break
                depth -= 1
            i -= 1
    return errs



LINE_WARN = 600  # 与 check-frankenstein.py 的 LINE_WARN 同口径（p95）


def check_new_oversized(path: str):
    """S4：新增文件 >=600 行 -> FAIL。

    为什么只管"新增"：仓内既有 50 个 >=600 行文件是历史包袱（棘轮在册），
    对新文件判死才既止血又不误杀。实测 2026-11-26：新面板 731 行把债务棘轮
    oversized_warn 从 50 顶到 51，推送前才发现，白跑一轮拆分单。
    """
    try:
        lines = sum(1 for _ in open(path, encoding='utf-8', errors='ignore'))
    except OSError:
        return []
    if lines < LINE_WARN:
        return []
    in_head = subprocess.run(['git', 'cat-file', '-e', 'HEAD:%s' % path],
                             capture_output=True).returncode == 0
    if in_head:
        return []
    return [(1, '新增文件 %d 行 >= %d，会顶高债务棘轮（先拆再交）' % (lines, LINE_WARN))]


STDLIB = set('''String Int Long Float Double Boolean Char Byte Short Any Unit Nothing Array
List MutableList Map MutableMap Set MutableSet Pair Triple Sequence Iterator Collection
Exception Throwable Error Regex StringBuilder IntArray BooleanArray FloatArray ByteArray
CharArray DoubleArray LongArray ShortArray Comparable Number CharSequence Runnable
Object Class Void Enum Math System Thread
Deprecated Suppress JvmStatic JvmField JvmName JvmOverloads Volatile Synchronized
IllegalArgumentException IllegalStateException UnsupportedOperationException
NullPointerException IndexOutOfBoundsException InterruptedException ArithmeticException
Override Deprecated Suppress JvmInline OptIn ExperimentalStdlibApi'''.split())

DECL_RE = re.compile(r'\b(?:class|object|interface|fun|val|var|typealias|enum\s+class|annotation\s+class)\s+([A-Z][A-Za-z0-9_]*)')
USE_RE = re.compile(r'(?<![\w.])([A-Z][A-Za-z0-9_]{2,})')

_PKG = {}


def pkg_decls():
    """package -> 该包下声明过的顶层名集合（S8 判"同包可见"用）。"""
    if _PKG:
        return _PKG
    for f in glob.glob('app/src/**/*.kt', recursive=True):
        try:
            src = open(f, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        pm = re.search(r'^package\s+([\w.]+)', src, re.M)
        pkg = pm.group(1) if pm else ''
        _PKG.setdefault(pkg, set()).update(DECL_RE.findall(strip_noise(src)))
    return _PKG


def check_symbol_origin(path: str, clean: str):
    """S8：用到的每个大写符号必须有出处（import / 同文件声明 / 同包声明）。

    为什么：2026-11-26 把 731 行面板拆成 4 个文件，import 没随迁（Orientation、
    TextFieldValue 抄错包，另两处漏 dp），两路 CI 同红 24 条。这类"用了但没 import"
    的错误 S1~S5 全看不见，因为文本层面完全合法，只有编译器认得出。
    只看不是成员访问（前面不是 '.'）的大写标识符；泛型/注解/标准库走白名单。
    """
    errs = []
    # 星号导入（import java.io.* / androidx.compose.layout.*）无法逐个判出处，整文件跳过
    if re.search(r'(?m)^import\s+[\w.]+\*', clean):
        return []
    imports = set()
    for line in clean.splitlines():
        t = line.strip()
        if t.startswith('import '):
            q = t[7:].split(' as ')[-1].strip()
            imports.add(q.split('.')[-1])
    declared = set(DECL_RE.findall(clean))
    # 枚举常量（KEEP_OPEN 这类全大写条目）也算本文件声明，否则整仓误报
    # 文件里只要有 enum class，就把全部"全大写"名字当作枚举条目（Kotlin 枚举条目不能跨包
    # 单独使用，只有 import Enum.ENTRY 这种反式用法，机检抓不全且不值得；宁可放过不误杀）
    if 'enum class' in clean:
        declared.update(re.findall(r'\b([A-Z][A-Z0-9_]{2,})\b', clean))
        # 枚举条目除全大写外还有 CamelCase 形式（DelHTag / RefreshToc）：条目位置为
        # '{' 或逗号之后、后跟 ( , ; =。这些条目声明在本文件，S8 不得判无出处。
        declared.update(re.findall(r'(?:^|[,{])\s*([A-Z][A-Za-z0-9_]*)\s*(?:\(|,|;|=)', clean, re.M))
    pm = re.search(r'^package\s+([\w.]+)', clean, re.M)
    same_pkg = pkg_decls().get(pm.group(1) if pm else '', set())
    seen = set()
    tol = load_tolerance()
    for m in USE_RE.finditer(clean):
        name = m.group(1)
        if name in imports or name in declared or name in same_pkg or name in STDLIB or name in tol:
            continue
        if name in seen:
            continue
        seen.add(name)
        errs.append((clean.count('\n', 0, m.start()) + 1,
                     '符号 %s 无 import / 同文件声明 / 同包声明出处（拆分搬迁最常漏）' % name))
    return errs[:12]


_VIS = {}


def internal_types():
    """typeName -> 'internal'/'private'（全局收集，S7 判可见性传递用）。"""
    if _VIS:
        return _VIS
    for f in glob.glob('app/src/**/*.kt', recursive=True):
        try:
            src = open(f, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        clean = strip_noise(src)
        pm = re.search(r'^package\s+([\w.]+)', clean, re.M)
        pkg = pm.group(1) if pm else ''
        for m in re.finditer(r'\b(internal|private)\s+(?:data\s+)?(?:sealed\s+)?'
                             r'(?:class|object|interface|typealias)\s+([A-Z][A-Za-z0-9_]*)', clean):
            # 只收 internal/private 且按包登记：同名 Cell 在别的包是 private，跨包误判会白白判死
            _VIS[(pkg, m.group(2))] = m.group(1)
    return _VIS


def check_visibility(path: str, clean: str):
    """S7：public 函数不得把 internal/private 类型放在参数或返回类型上。

    实测 2026-11-26：拆出的 InkContentEditRules.kt 里 `fun projectEdit(projection: internal class ContentEditProjection)`
    直接编译失败（'public' function exposes its 'internal' parameter type）。这类错文本完全合法，
    只有编译器认，所以必须在 push 前用声明表比对。只看非 internal/private 的 fun。
    """
    errs = []
    vis = internal_types()
    if not vis:
        return errs
    pm = re.search(r'^package\s+([\w.]+)', clean, re.M)
    pkg = pm.group(1) if pm else ''
    lines = clean.splitlines()
    for m in re.finditer(r'(?m)^(?!\s*(?:internal|private))((?:[\w@<>]+\s+)*?)fun\s+'
                         r'([A-Za-z_]\w*)\s*\(([^)]*)\)\s*(?::\s*([^={\n]+))?', clean):
        # 修饰符常写在上一行（@Composable 下面一行 private fun），必须回看两行
        ln = clean.count('\n', 0, m.start())
        prev = ' '.join(lines[max(0, ln - 1):ln + 4])
        if re.search(r'\b(internal|private)\b', prev):
            continue
        types = set(re.findall(r'\b([A-Z][A-Za-z0-9_]*)\b', m.group(3) or ''))
        ret = m.group(4) or ''
        types |= set(re.findall(r'\b([A-Z][A-Za-z0-9_]*)\b', ret))
        hit = [t for t in types if vis.get((pkg, t))]
        if hit:
            errs.append((clean.count('\n', 0, m.start()) + 1,
                         'public 函数 %s 暴露 %s 类型 %s（改 internal 或提升类型可见性）'
                         % (m.group(2), vis[(pkg, hit[0])], hit[0])))
    return errs[:10]


TOLERANCE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'symbol-tolerance.json')


def load_tolerance():
    """HEAD 里"本就解析不了但一直编译正常"的符号集合（JDK 默认导入、注解、继承常量等）。

    为什么需要：S8 白名单靠枚举 JDK 类型是打地鼠（System/ArrayList/Throws/RESULT_OK…），
    全仓一跑 300+ 条误报，脚本就被自己废掉。改成"存量容忍、增量判死"：从 HEAD 基线
    生成容忍集，只有**新出现**的无出处符号才 FAIL——正好命中拆分/搬迁漏 import。
    """
    try:
        with open(TOLERANCE, encoding='utf-8') as f:
            return set(json.load(f))
    except (OSError, ValueError):
        return set()


PACK_MAP = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'import-pack-map.json')


def load_pack_map():
    """S5 的判据来源：从 CI 绿基线（16bc12b）生成的 符号->唯一包 种子。

    为什么不用"扫当前工作树"：错误的 import 本身就在树里（88e62bc 已推红），
    自举会把错包算成合法用法。种子来自干净基线 + 人工维护，才判得动。
    为什么不覆盖非 compose 符号：同名跨域太密（Canvas 有 android.graphics 版），
    全量比对误报 2300+ 条，实测不可用；compose 面才是手抄错包的重灾区。
    """
    try:
        with open(PACK_MAP, encoding='utf-8') as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def check_import_package(path: str, pmap):
    """S5：import 的包与种子登记不一致 -> FAIL。

    实测 2026-11-26 拆 731 行面板：`Orientation` 抄成 foundation（应 gestures）、
    `TextFieldValue` 抄成 ui.text（应 ui.text.input）、两文件漏 `dp`，两路 CI 同红。
    S1~S4 只看括号/参数/行数，看不见"包错了"；S5 用种子表当场判死。
    未知符号不判（新依赖合法，交由 CI 编译兜底），避免误杀。
    """
    errs = []
    try:
        lines = open(path, encoding='utf-8', errors='ignore').read().splitlines()
    except OSError:
        return errs
    for i, line in enumerate(lines, 1):
        t = line.strip()
        if not t.startswith('import ') or ' as ' in t:
            continue
        q = t[7:].strip()
        if '.' not in q:
            continue
        pkg, name = q.rsplit('.', 1)
        want = pmap.get(name)
        if want and want != pkg:
            errs.append((i, 'import %s 的包与基线登记不符（基线：%s）' % (q, want.replace('/', ' 或 '))))
    return errs


def gen_tolerance():
    """从 HEAD 生成容忍集：跑同一套判据，把"无出处但存量如此"的名字收进 json。"""
    files = [f for f in subprocess.run(['git', 'ls-tree', '-r', '--name-only', 'HEAD', '--', 'app/src'],
                                       capture_output=True, text=True).stdout.split() if f.endswith('.kt')]
    with open(TOLERANCE, 'w', encoding='utf-8') as fh:
        fh.write('[]')  # 先清空：否则旧容忍集会把本应新收集的符号全吞掉（实测踩过）
    tol = set()
    for f in files:
        src = subprocess.run(['git', 'show', 'HEAD:' + f], capture_output=True, text=True).stdout
        clean = strip_noise(src)
        for _, why in check_symbol_origin(f, clean):
            m = re.search(r'符号 (\w+) ', why)
            if m:
                tol.add(m.group(1))
    with open(TOLERANCE, 'w', encoding='utf-8') as fh:
        json.dump(sorted(tol), fh, ensure_ascii=False, indent=0)
    print('TOLERANCE: %d 个存量容忍符号写入 %s' % (len(tol), TOLERANCE))


def main() -> int:
    args = sys.argv[1:]
    if '--gen-tolerance' in args:
        gen_tolerance()
        return 0
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
    pmap = load_pack_map()
    for f in files:
        try:
            src = open(f, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        clean = strip_noise(src)
        for ln, why in (check_balance(clean) + check_cond_trailing_comma(clean)
                         + check_style_only_params(clean) + check_new_oversized(f)
                         + check_import_package(f, pmap) + check_symbol_origin(f, clean)
                         + check_visibility(f, clean)):
            print('FAIL %s:%d %s' % (f, ln, why))
            bad += 1
    print('KOTLIN-LINT: %s（%d 个文件）' % ('FAIL' if bad else 'PASS', len(files)))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
