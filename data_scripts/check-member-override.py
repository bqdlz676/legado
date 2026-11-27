#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""g66 同签名实现成员缺 override 静态判据闸。

事故下限（2026-11-27 CI 红 0c9fd30，修红提交 35bbb54）：
`ReaderShellHost` 接口声明 `currentTextPage(): TextPage?`（ReaderShellHost.kt:72）
与 `textPageState(): InkTextPageState`（:78）；`InkReadController` 里同签名声明
没写 `override`，kotlinc 报
`'currentTextPage' hides member of supertype 'ReaderShellHost' and needs an 'override' modifier`
（InkReadController.kt:141/:149）。当时 g63 符号存在配平（NODEF/ARITY/PACKAGE 全 0）、
g64 单测判据真值、g65 消费方反查、compose 陷阱、lint 全绿，没有一个静态闸能拦——
这就是判据空白：接口/抽象类成员被实现方同签名重声明却缺 `override`。

三面判据（每条可单条命令判真假）：

1) 接口表：扫仓内 `interface` 与 `abstract class` 的成员方法签名
   （名字 + 参数个数 + 参数类型串归一）与属性成员名，建成父类型成员表。
2) 实现方反查：对所有直接或间接继承该接口/抽象类的类/对象体，同签名声明
   缺 `override` 判 MEMBER-OVERRIDE 红；同名字段/属性同理。
3) 误报防制（均有放行样本与标签）：
   - SKIP:PRIVATE  private 成员不参与 override 解析；
   - SKIP:EXTENSION 不同接收者的扩展函数（`fun Recv.name(...)`）；
   - SKIP:DIFF-SIG 同名同参个数但归一后类型串不同（泛型实参不同等），不算同签名；
   - SKIP:LOCAL 函数体内部的局部函数（深于类体一层）；
   - SKIP:COMPANION 伴生对象内同名成员；
   - SKIP:TOPLEVEL 接口/类体外的普通同名顶层函数。

间接实现取舍（在报告与锚点卡写明）：类 A : Iface、类 B : A 这条链，按 BFS 沿
父类型链把接口/抽象类成员表追进 B；B 重声明同签名成员同样要 `override`。
只追仓内 Kotlin 声明的链；中间具体类自带的 `open fun` 不进父类型表（静态闸拿不到
其 open/abstract 标注以外的 JVM 契约），Java/Android 父类（Fragment、MutableMap 等）
不在表内，天然不误判。

口径：默认 `--changed` 只判相对 origin/main 的改动面；不带参数同样按改动面跑；
`--audit` 全仓只出清单，不得从 0 变红墙（存量债务走台账，不进 blocking 改动面）。
退出码：0 = 改动面无 MEMBER-OVERRIDE；1 = 有红。
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_ROOTS = ['app/src', 'screenshot/src']

# ---- 声明头正则 -------------------------------------------------------------

# 注解前缀：`@Inject` / `@StringRes(0)` 独占一行或贴在修饰符前。
ANNOT = r'(?:@\w+(?:\([^)]*\))?\s+)*'
MOD = (r'(?:private|protected|internal|public|open|abstract|final|suspend|const|'
       r'lateinit|operator|infix|external|tailrec|inline|override|expect|actual|'
       r'vararg|sealed|data|companion)\s+')
MOD_RE = re.compile(r'^(' + MOD + r')+')

IFACE_HEAD = re.compile(r'^\s*' + ANNOT + r'(?:sealed\s+)?interface\s+(?P<name>[A-Za-z_]\w*)')
ABSTRACT_HEAD = re.compile(r'^\s*' + ANNOT + r'abstract\s+class\s+(?P<name>[A-Za-z_]\w*)')
CLASS_HEAD = re.compile(
    r'^\s*' + ANNOT + r'(?P<mod>(?:open\s+|final\s+|data\s+|sealed\s+|private\s+|'
    r'internal\s+|abstract\s+|enum\s+)*)'
    r'(?P<kind>class|object)\s+(?P<name>[A-Za-z_]\w*)')
COMPANION_HEAD = re.compile(r'^\s*companion\s+object')

FUN_RE = re.compile(
    r'^\s*(?P<mod>(?:' + MOD + r'|' + ANNOT + r')*)fun\s+'
    r'(?:<[^>]*>\s*)?'
    r'(?P<recv>[A-Za-z_][\w.]*\.)?(?P<name>[A-Za-z_]\w*)'
    r'\s*(?P<params>\([^()]*\))?')
PROP_RE = re.compile(
    r'^\s*(?P<mod>(?:' + MOD + r'|' + ANNOT + r')*)(?P<kind>val|var)\s+'
    r'(?P<name>[A-Za-z_]\w*)')
IDENT = re.compile(r'[A-Za-z_]\w*')


# ---- 注释 / 字符串状态机（与 g65 同口径：剥注释，字符串原样保留）-------------

def strip_comments(text: str) -> str:
    """剥 // 行注释与 /* */ 块注释；字符串（双引号 / 字符 / 三引号）原样保留。

    为什么保留字符串：成员名匹配只看声明位，字符串里提到旧名不算声明；状态机口径
    与 g65 一致，避免两套注释解析出两套行号。
    """
    out = []
    i, n = 0, len(text)
    state = None  # None / line / block / str / char / raw
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        three = text[i:i + 3]
        if state is None:
            if two == '//':
                state, i = 'line', i + 2
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
            if c == "'":
                state = 'char'
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
                state, i = None, i + 2
                continue
            if c == '\n':
                out.append(c)
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
        if state == 'char':
            out.append(c)
            if c == '\\':
                if i + 1 < n:
                    out.append(text[i + 1])
                i += 2
                continue
            if c == "'":
                state = None
            i += 1
            continue
    return ''.join(out)


def _mask(text: str) -> str:
    """等长掩码：字符串 / 字符 / 注释内的字符替换成空格（换行保留）。

    为什么不用状态机直接配平：转义字符（'\\n'、\"、${}）一旦状态走错，括号深度
    就会累积漂移，类成员被误判成局部函数。掩码后括号计数只认代码位，长度 1:1，
    偏移量可直接复用；串内花括号不计入嵌套，属保守边界。
    """
    out = []
    i, n = 0, len(text)
    state = None  # None / line / block / str / char / raw
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        three = text[i:i + 3]
        if state is None:
            if two == '//':
                state, i = 'line', i + 2
                out.append(' '); out.append(' ')
                continue
            if two == '/*':
                state = 'block'; i += 2
                out.append(' '); out.append(' ')
                continue
            if three == '"""':
                state = 'raw'
                out.extend([' ', ' ', ' '])
                i += 3
                continue
            if c == '"':
                state = 'str'; out.append(' '); i += 1; continue
            if c == "'":
                state = 'char'; out.append(' '); i += 1; continue
            out.append(c)
            i += 1
            continue
        if state == 'line':
            out.append('\n' if c == '\n' else ' ')
            if c == '\n':
                state = None
            i += 1
            continue
        if state == 'block':
            if two == '*/':
                out.append(' ')
                out.append(' ')
                state, i = None, i + 2
                continue
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        if state == 'raw':
            if three == '"""':
                state = None
                out.extend([' ', ' ', ' '])
                i += 3
                continue
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        if state == 'str':
            if c == '\\':
                out.append(' ')
                if i + 1 < n:
                    out.append(' ')
                i += 2
                continue
            if c == '"':
                state = None
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        if state == 'char':
            if c == '\\':
                out.append(' ')
                if i + 1 < n:
                    out.append(' ')
                i += 2
                continue
            if c == "'":
                state = None
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
    return ''.join(out)


def _balanced_block(text: str, open_idx: int) -> str:
    """从 text[open_idx] 的 `{` 起按掩码配平，返回块体（不含最外层花括号）。

    内层花括号必须保留：成员解析要靠它们算嵌套深度（方法体一层 = 局部函数层）。
    """
    masked = _mask(text)
    depth = 0
    n = len(text)
    j = open_idx
    body = []
    # 外层开口括号：depth 置 1，不进块体。
    if j < n and masked[j] == '{':
        depth = 1
        j += 1
    while j < n:
        if masked[j] == '{':
            depth += 1
            body.append(text[j])
        elif masked[j] == '}':
            depth -= 1
            if depth == 0:
                return ''.join(body)
            body.append(text[j])
        else:
            body.append(text[j])
        j += 1
    return ''.join(body)


def _split_top_commas(seg: str) -> list:
    """按顶层逗号切分参数列表；括号 / 字符串内逗号不计。"""
    parts, depth, state, cur = [], 0, None, []
    i = 0
    while i < len(seg):
        c = seg[i]
        three = seg[i:i + 3]
        if state is None:
            if three == '"""':
                state = 'raw'; cur.append(three); i += 3; continue
            if c == '"':
                state = 'str'; cur.append(c); i += 1; continue
            if c == "'":
                state = 'char'; cur.append(c); i += 1; continue
            if c in '([<':
                depth += 1
            elif c in ')]>':
                depth -= 1
            elif c == ',' and depth == 0:
                parts.append(''.join(cur)); cur = []; i += 1; continue
            cur.append(c)
        elif state == 'raw':
            if three == '"""':
                state = None
            cur.append(c); i += 1; continue
        elif state == 'str':
            cur.append(c)
            if c == '\\':
                cur.append(seg[i + 1:i + 2]); i += 2; continue
            if c == '"':
                state = None
            i += 1; continue
        elif state == 'char':
            cur.append(c)
            if c == '\\':
                i += 2; continue
            if c == "'":
                state = None
            i += 1; continue
        i += 1
    if ''.join(cur).strip():
        parts.append(''.join(cur))
    return parts


def normalize_type(t: str) -> str:
    """参数类型串归一：去空白、去可空后缀、去泛型协变/逆变标记；保留泛型实参。

    为什么保留泛型实参：`List<String>` 与 `List<Long>` 源码签名不同，擦除后同为
    List 是 JVM 层面的事；Kotlin 源码判据按源码签名比对，二者不应当作同签名放行误杀，
    也不应当作同签名判红——类型串不同即归 SKIP:DIFF-SIG。
    """
    t = t.strip().replace(' ', '')
    t = t.rstrip('?')
    t = re.sub(r'^(?:in|out)(?=[A-Z])', '', t)
    return t


def param_key(params: str | None) -> tuple:
    """归一参数表：返回 (arity, 类型串列表)。无参数表按 0 参处理。"""
    if not params:
        return 0, []
    inner = params.strip()[1:-1] if params.strip().startswith('(') else params
    types = []
    for piece in _split_top_commas(inner):
        p = piece.strip()
        p = re.sub(r'@\w+(?:\([^)]*\))?\s*', '', p)
        p = re.sub(r'^vararg\s+', '', p)
        if ':' in p:
            p = p.split(':', 1)[1].strip()
        p = p.split('=', 1)[0].strip()  # 去默认值：`Boolean = true` 与 `Boolean` 同签名
        types.append(normalize_type(p))
    return len(types), types


def method_key(name: str, params: str | None) -> str:
    arity, types = param_key(params)
    return f'fn|{name}|{arity}|{",".join(types)}'


def _modifiers(mod: str) -> set:
    return set(mod.split()) if mod else set()


def _supertypes(head_text: str) -> list:
    """从声明头文本（名字后到 `{` 之前）提取父类型短名列表。

    先剥主构造参数平衡括号，再取第一个冒号之后的段，按顶层逗号切分；
    泛型实参 `<...>` 与可空后缀去掉，取点号末段。
    """
    # 剥主构造参数：名字后第一个平衡 (...)
    paren = re.search(r'\((?:[^()]|\([^()]*\))*\)', head_text)
    rest = head_text
    if paren:
        rest = head_text[:paren.start()] + ' ' + head_text[paren.end():]
    if ':' not in rest:
        return []
    after = rest.split(':', 1)[1]
    after = after.split('{', 1)[0]  # 截掉类体花括号，`Host {` 不能混进父类型名
    out = []
    for piece in _split_top_commas(after):
        t = piece.strip()
        t = re.sub(r'<[^>]*>', '', t)
        t = t.rstrip('?').strip()
        t = t.split('.')[-1]
        if IDENT.fullmatch(t):
            out.append(t)
    return out


# ---- 源码模型 ---------------------------------------------------------------

class Decl:
    __slots__ = ('name', 'kind', 'file', 'line', 'supertypes', 'methods', 'props',
                 'members', 'body')

    def __init__(self, name, kind, file, line, supertypes, body):
        self.name = name
        self.kind = kind  # interface / abstract / class / object
        self.file = file
        self.line = line
        self.supertypes = supertypes
        self.methods = set()   # 父类型表用：方法签名 key
        self.props = set()     # 父类型表用：属性名
        self.members = []      # 实现方反查用：原始成员字典
        self.body = body


def scan_members(decl: Decl, body: str, base_line: int) -> None:
    """扫类体一层成员；深于一层的局部函数 / 嵌套伴生 / 嵌套类分别打标签或递归。

    深度口径：body 是类体花括号内文本；行首相对 body 花括号的嵌套层数为 0 才是
    类成员。深度统计走字符串感知状态机，串内花括号不计层。
    """
    lines = body.splitlines()
    # 预计算每行【行首】嵌套深度（花括号净层数，串内忽略）。
    # 为什么用行首深度：成员声明行自带开口括号 `fun f() {`，行末深度已 +1，
    # 按行末判会把类成员误打成局部函数；行首深度才是它所属的层。
    # 括号只在掩码文本上计数：转义串 / 字符字面量不再让深度漂移。
    masked = _mask(body)
    depths, depth = [0], 0
    for c in masked:
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
        elif c == '\n':
            depths.append(max(depth, 0))
    # 末行无换行兜底
    while len(depths) < len(lines):
        depths.append(max(depth, 0))

    idx = 0
    while idx < len(lines):
        line = lines[idx]
        depth = depths[idx] if idx < len(depths) else 0
        stripped = line.strip()

        if depth == 0 and COMPANION_HEAD.match(line):
            # 伴生对象整段跳过：其内同名成员不是类体重声明；块内 fun/val/var
            # 记 SKIP:COMPANION 放行计数（可审计），不进类成员匹配，放行行为不变。
            brace = body.find('{', sum(len(l) + 1 for l in lines[:idx]))
            if brace >= 0:
                comp_body = _balanced_block(body, brace)
                c_lines = comp_body.splitlines()
                c_masked = _mask(comp_body)
                c_depths, c_depth = [0], 0
                for ch in c_masked:
                    if ch == '{':
                        c_depth += 1
                    elif ch == '}':
                        c_depth -= 1
                    elif ch == '\n':
                        c_depths.append(max(c_depth, 0))
                for j, cl in enumerate(c_lines):
                    cd = c_depths[j] if j < len(c_depths) else 0
                    if cd != 0:
                        continue
                    if FUN_RE.match(cl) or PROP_RE.match(cl):
                        decl.members.append({
                            'skip': 'COMPANION',
                            'line': base_line + idx + 1 + j,
                            'raw': cl.strip(),
                        })
            idx += 1
            continue

        if depth == 0:
            m_cls = CLASS_HEAD.match(line)
            if m_cls:
                # 嵌套类/对象：递归进它自己的体，不并入外层成员。
                decl2 = Decl(m_cls.group('name'), m_cls.group('kind'), decl.file,
                              base_line + idx, [], '')
                brace = body.find('{', sum(len(l) + 1 for l in lines[:idx]))
                if brace >= 0:
                    body2 = _balanced_block(body, brace)
                    decl2.body = body2
                    decl2.supertypes = _supertypes(line + ' ' + '\n'.join(lines[idx + 1:idx + 3]))
                    scan_members(decl2, body2, base_line + idx)
                    decl.members.append({'nested': decl2})
                idx += 1
                continue

        if depth != 0:
            # 函数体内局部函数 / 局部属性：不参与类成员匹配。
            if FUN_RE.match(line):
                decl.members.append({'skip': 'LOCAL', 'line': base_line + idx,
                                     'raw': stripped})
            idx += 1
            continue

        m_fun = FUN_RE.match(line)
        if m_fun:
            mods = _modifiers(m_fun.group('mod'))
            decl.members.append({
                'kind': 'fun', 'name': m_fun.group('name'),
                'recv': m_fun.group('recv'), 'params': m_fun.group('params'),
                'mods': mods, 'line': base_line + idx, 'raw': stripped,
            })
            idx += 1
            continue

        m_prop = PROP_RE.match(line)
        if m_prop:
            mods = _modifiers(m_prop.group('mod'))
            decl.members.append({
                'kind': 'prop', 'name': m_prop.group('name'),
                'mods': mods, 'line': base_line + idx, 'raw': stripped,
            })
        idx += 1


def parse_file(rel: str, text: str) -> tuple:
    """抽出文件内全部顶层/嵌套声明；返回 (Decl 列表, 顶层声明计数)。

    嵌套类挂在 members.nested；顶层 fun/val/var（类/接口体外）不进任何声明体，
    单列计数交 SKIP:TOPLEVEL 放行审计，不参与类成员匹配。

    配平在原文上做：strip_comments 会吃掉字符转义的第二字符（'\\n' 变 '\\'），
    再交给掩码就会把闭引号当转义吞掉、深度漂移。成员正则才用剥注释后的体。
    """
    code = strip_comments(text)
    lines = code.splitlines()
    orig_lines = text.splitlines()
    offsets, off = [], 0
    for ln in orig_lines:
        offsets.append(off)
        off += len(ln) + 1

    # 文件级深度：掩码等长，花括号与圆括号深度都为 0 的 fun/val/var 才是顶层声明。
    # 为什么要带括号深度：无类体的 data class 主构造参数跨多行、没有类体花括号，
    # 只看花括号深度会把构造参数属性误判成顶层声明（Book.kt 一度 35 处误计）。
    toplevel = 0
    code_masked = _mask(code)
    tl_depths, tl_parens, tl_depth, tl_paren = [], [], 0, 0
    for ch in code_masked:
        if ch == '{':
            tl_depth += 1
        elif ch == '}':
            tl_depth -= 1
        elif ch == '(':
            tl_paren += 1
        elif ch == ')':
            tl_paren -= 1
        elif ch == '\n':
            tl_depths.append(max(tl_depth, 0))
            tl_parens.append(max(tl_paren, 0))
    for ln, raw in enumerate(lines):
        d = tl_depths[ln] if ln < len(tl_depths) else 0
        p = tl_parens[ln] if ln < len(tl_parens) else 0
        if d != 0 or p != 0:
            continue
        if IFACE_HEAD.match(raw) or ABSTRACT_HEAD.match(raw) or CLASS_HEAD.match(raw):
            continue
        if FUN_RE.match(raw) or PROP_RE.match(raw):
            toplevel += 1

    decls: list[Decl] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        m_iface = IFACE_HEAD.match(line)
        m_abs = ABSTRACT_HEAD.match(line)
        m_cls = CLASS_HEAD.match(line)
        head_kind = None
        if m_iface:
            head_kind, name, m = 'interface', m_iface.group('name'), m_iface
        elif m_abs:
            head_kind, name, m = 'abstract', m_abs.group('name'), m_abs
        elif m_cls:
            head_kind, name, m = m_cls.group('kind'), m_cls.group('name'), m_cls
        else:
            i += 1
            continue

        # 头可能跨多行：向后找第一个 `{`（行号口径与剥注释后一致：换行均保留）。
        brace_line = i
        brace = orig_lines[brace_line].find('{')
        while brace < 0 and brace_line + 1 < len(orig_lines):
            brace_line += 1
            brace = orig_lines[brace_line].find('{')
        if brace < 0:
            i = brace_line + 1
            continue
        head_text = '\n'.join(orig_lines[i:brace_line + 1])
        raw_body = _balanced_block(text, offsets[brace_line] + brace)
        body = strip_comments(raw_body)
        decl = Decl(name, head_kind, rel, i + 1, _supertypes(head_text), body)
        scan_members(decl, body, i + 1)
        decls.append(decl)
        i = brace_line + 1
    return decls, toplevel


def build_model(root_dirs: list) -> tuple:
    """全仓建模：返回 (decls 扁平列表, table: 父类型短名 -> {methods, props}, 顶层声明计数表)。

    table 只收 interface 全部成员；abstract class 只收 abstract/open 成员
    （Kotlin 类成员默认 final，final 同名不是被重写的候选）。

    路径口径：Decl.file 一律存仓内相对根（`app/src/main/...`），与 git_kt_diff()
    返回的改动面文件名同一命名空间；不再按各自源码根取相对名，避免改动面直配落空。
    """
    decls: list[Decl] = []
    toplevel_by_file: dict[str, int] = {}
    for root in root_dirs:
        if not os.path.isdir(root):
            continue
        for dirpath, _, names in os.walk(root):
            for nm in sorted(names):
                if not nm.endswith('.kt'):
                    continue
                f = os.path.join(dirpath, nm)
                try:
                    text = open(f, encoding='utf-8', errors='ignore').read()
                except OSError:
                    continue
                try:
                    rel = os.path.relpath(f, ROOT)
                except ValueError:
                    rel = f
                file_decls, toplevel = parse_file(rel, text)
                decls.extend(file_decls)
                toplevel_by_file[rel] = toplevel

    # 展开嵌套类到扁平表（嵌套类自身也是实现方）。
    flat: list[Decl] = []
    def walk(d: Decl):
        flat.append(d)
        for mb in d.members:
            if 'nested' in mb:
                walk(mb['nested'])
    for d in decls:
        walk(d)

    table: dict[str, dict] = {}
    for d in flat:
        if d.kind not in ('interface', 'abstract'):
            continue
        entry = {'methods': set(), 'props': set()}
        for mb in d.members:
            if mb.get('kind') == 'fun':
                # abstract 类只收 abstract/open 方法；Kotlin 类成员默认 final，
                # final 同名不是被重写候选，进表只会误报。接口方法全部进表。
                if d.kind == 'interface' or ({'abstract', 'open'} & mb['mods']):
                    entry['methods'].add(method_key(mb['name'], mb['params']))
            elif mb.get('kind') == 'prop':
                # abstract 类的 final 普通属性不进表；接口属性全部进表。
                if d.kind == 'interface' or ({'abstract', 'open'} & mb['mods']):
                    entry['props'].add(mb['name'])
        table[d.name] = entry
    return flat, table, toplevel_by_file


def resolve_inherited(decl: Decl, decl_by_name: dict, table: dict) -> dict:
    """BFS 沿父类型链追接口/抽象类成员表；返回合并后的 {methods, props}。

    间接实现取舍：链上经过的仓内 Kotlin 类若又继承别的接口/抽象类，一并追进去；
    Java/Android 父类不在 decl_by_name 里，自然落空不误判。
    """
    merged = {'methods': set(), 'props': set()}
    seen, queue = set(), list(decl.supertypes)
    while queue:
        t = queue.pop(0)
        if t in seen:
            continue
        seen.add(t)
        if t in table:
            merged['methods'] |= table[t]['methods']
            merged['props'] |= table[t]['props']
        node = decl_by_name.get(t)
        if node is not None:
            queue.extend(node.supertypes)
    return merged


def check_decl(decl: Decl, inherited: dict) -> tuple:
    """对一个实现方声明反查；返回 (fails, skips 计数 dict, matched)。"""
    fails = []
    skips = {'PRIVATE': 0, 'EXTENSION': 0, 'DIFF-SIG': 0, 'LOCAL': 0,
             'COMPANION': 0, 'TOPLEVEL': 0}
    matched = 0

    def consider(mb: dict):
        nonlocal matched
        mods = mb['mods']
        if 'private' in mods:
            skips['PRIVATE'] += 1
            return
        if mb['kind'] == 'fun' and mb.get('recv'):
            skips['EXTENSION'] += 1
            return
        if mb['kind'] == 'fun':
            key = method_key(mb['name'], mb['params'])
            if key in inherited['methods']:
                if 'override' not in mods:
                    fails.append({
                        'label': 'MEMBER-OVERRIDE', 'file': decl.file,
                        'line': mb['line'], 'kind': '方法', 'name': mb['name'],
                        'raw': mb['raw'], 'super': decl.supertypes,
                    })
                else:
                    matched += 1
                return
            # 同名同参个数、类型串不同：不算同签名，放行计数。
            arity, _ = param_key(mb['params'])
            for mk in inherited['methods']:
                parts = mk.split('|')
                if len(parts) == 4 and parts[1] == mb['name'] and parts[2] == str(arity):
                    skips['DIFF-SIG'] += 1
                    return
        else:
            if mb['name'] in inherited['props']:
                if 'override' not in mods:
                    fails.append({
                        'label': 'MEMBER-OVERRIDE', 'file': decl.file,
                        'line': mb['line'], 'kind': '属性', 'name': mb['name'],
                        'raw': mb['raw'], 'super': decl.supertypes,
                    })
                else:
                    matched += 1

    for mb in decl.members:
        if 'nested' in mb:
            continue
        if mb.get('skip'):
            # 放行类别计数：LOCAL / COMPANION 等已登记标签各归其计数器；
            # 未登记标签不计数，避免新标签静默吃掉计数。
            label = mb['skip']
            if label in skips:
                skips[label] += 1
            continue
        consider(mb)
    return fails, skips, matched


# ---- 改动面采集（口径同 g65：git diff + 未跟踪 .kt） -------------------------

def git_kt_diff(base: str = 'origin/main') -> list:
    names = []
    try:
        out = subprocess.run(
            ['git', '-c', 'core.quotepath=false', 'diff', '--name-only', base],
            capture_output=True, text=True, timeout=60, cwd=ROOT)
        names = [n.strip().strip('"') for n in out.stdout.splitlines()]
        st = subprocess.run(
            ['git', '-c', 'core.quotepath=false', 'status', '--porcelain', '-uall'],
            capture_output=True, text=True, timeout=60, cwd=ROOT)
        for row in st.stdout.splitlines():
            body = row[3:].strip().strip('"')
            if row[:2].strip().startswith('??'):
                names.append(body)
    except Exception:
        names = []
    names = [n for n in names if n.endswith('.kt')]
    return [n for n in names if os.path.isfile(os.path.join(ROOT, n))]


def collect_roots() -> list:
    return [os.path.join(ROOT, r) for r in SOURCE_ROOTS]


# ---- 主判定 -----------------------------------------------------------------

def evaluate(flat: list, table: dict, judge_files: set | None = None,
             toplevel: dict | None = None) -> tuple:
    """judge_files 非空时只判这些文件内的声明（改动面口径）。

    返回 (fails, skips 汇总, matched 数, 参与判定的实现声明数)。
    toplevel: 文件 → 顶层声明计数；改动面口径只汇总命中文件，全仓口径汇总全部。
    """
    decl_by_name = {d.name: d for d in flat}
    fails, total_skips, matched, impl_count = [], {}, 0, 0
    for d in flat:
        if d.kind not in ('class', 'object'):
            continue
        if judge_files is not None and d.file not in judge_files:
            continue
        inherited = resolve_inherited(d, decl_by_name, table)
        if not inherited['methods'] and not inherited['props']:
            continue
        impl_count += 1
        f, sk, m = check_decl(d, inherited)
        fails.extend(f)
        matched += m
        for k, v in sk.items():
            total_skips[k] = total_skips.get(k, 0) + v
    if toplevel:
        # 与其余放行计数同口径：只在有计数时落末行，0 不占位。
        tl = (sum(toplevel.values()) if judge_files is None
              else sum(v for k, v in toplevel.items() if k in judge_files))
        if tl:
            total_skips['TOPLEVEL'] = tl
    return fails, total_skips, matched, impl_count


def main(only_changed: bool = False, audit: bool = False) -> int:
    roots = collect_roots()
    flat, table, toplevel = build_model(roots)

    if audit:
        # 全仓 audit 清单：只盘点不判红，存量债务不挡 push。
        fails, skips, matched, impl_count = evaluate(
            flat, table, judge_files=None, toplevel=toplevel)
        print(f'MEMBER-OVERRIDE AUDIT: 全仓清单（接口/抽象类 {len(table)} 个 / '
              f'实现声明 {impl_count} 个 / 候选缺 override {len(fails)} 处 / '
              f'已配对 override {matched} 处；存量只登记不判红）')
        for f in fails[:20]:
            print(f'  INFO  [AUDIT] {f["file"]}:{f["line"]} {f["kind"]} '
                  f'`{f["name"]}` 缺 override（父类型 {",".join(f["super"]) or "?"}）')
        return 0

    if only_changed:
        judge = set(git_kt_diff())
    else:
        judge = set(git_kt_diff())

    fails, skips, matched, impl_count = evaluate(
        flat, table, judge_files=judge, toplevel=toplevel)

    for f in fails:
        print(f'FAIL  [{f["label"]}] {f["file"]}:{f["line"]} {f["kind"]} '
              f'`{f["name"]}` 缺 override（重写/隐藏父类型 '
              f'{",".join(f["super"]) or "?"} 成员；原文：{f["raw"][:60]}）')

    skip_txt = ' / '.join(f'{k}={v}' for k, v in sorted(skips.items()))
    if fails:
        print(f'MEMBER-OVERRIDE: FAIL（改动面 {impl_count} 个实现声明；'
              f'缺 override {len(fails)} 处；放行 {skip_txt}）')
        return 1
    print(f'MEMBER-OVERRIDE: PASS（改动面 {impl_count} 个实现声明；缺 override 0；'
          f'已配对 override {matched}；放行 {skip_txt}）')
    return 0


# ---- 自测夹具 ---------------------------------------------------------------

def _write_kt(root, rel, body):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(body)
    return p


def _run_case(files: dict) -> tuple:
    """单样本独立跑：临时目录写夹具，直接走生产 build_model/evaluate，返回 (rc, fails, skips)。"""
    d = tempfile.mkdtemp(prefix='member-override-')
    try:
        for rel, body in files.items():
            _write_kt(d, rel, body)
        flat, table, toplevel = build_model([d])
        fails, skips, matched, impl = evaluate(
            flat, table, judge_files=None, toplevel=toplevel)
        return (1 if fails else 0), fails, skips, matched, impl
    finally:
        shutil.rmtree(d, ignore_errors=True)


def _run_changed(files: dict, targets: list) -> tuple:
    """改动面口径样本：临时目录写夹具，evaluate 只判文件名命中 targets 的声明。

    返回 (rc, fails, skips, matched, impl, judge 集)；targets 用仓内相对名后缀匹配，
    与生产 git_kt_diff 返回的仓内相对名同一口径。
    """
    d = tempfile.mkdtemp(prefix='member-override-')
    try:
        for rel, body in files.items():
            _write_kt(d, rel, body)
        flat, table, toplevel = build_model([d])
        judge = {x.file for x in flat if any(t in x.file for t in targets)}
        fails, skips, matched, impl = evaluate(
            flat, table, judge_files=judge, toplevel=toplevel)
        return (1 if fails else 0), fails, skips, matched, impl, judge
    finally:
        shutil.rmtree(d, ignore_errors=True)


def selftest() -> int:
    ok = True

    # 夹具一（真事故镜像，造在仓外 tempfile）：接口两方法 + 一属性，实现方同签名
    # 声明全缺 override → 必须 3 条 MEMBER-OVERRIDE 红，逐条带 文件:行 与成员名。
    bad_mirror = {
        'app/src/main/java/a/Host.kt': (
            'package a\n'
            'interface Host {\n'
            '    fun currentTextPage(): TextPage?\n'
            '    fun textPageState(): InkState\n'
            '    val title: String\n'
            '}\n'),
        'app/src/main/java/a/Controller.kt': (
            'package a\n'
            'class Controller : Host {\n'
            '    fun currentTextPage(): TextPage? = null\n'
            '    fun textPageState(): InkState = InkState.Ok\n'
            '    val title: String = "x"\n'
            '}\n'),
    }
    rc, fails, _, _, _ = _run_case(bad_mirror)
    names = [f['name'] for f in fails]
    lines = [f['line'] for f in fails]
    if rc != 1 or len(fails) != 3 or not all(f['label'] == 'MEMBER-OVERRIDE' for f in fails):
        ok = False
        print(f'  SELFTEST FAIL 事故镜像应 3 条 MEMBER-OVERRIDE 红：rc={rc} fails={fails}')
    elif not (set(names) == {'currentTextPage', 'textPageState', 'title'} and
              all(f['file'].endswith('Controller.kt') for f in fails)):
        ok = False
        print(f'  SELFTEST FAIL 红行落点成员/文件不符：{fails}')
    else:
        print(f'[selftest] 夹具一 事故镜像 → 独立 FAIL OK：{len(fails)} 条，'
              f'行号 {lines}，成员 {names}')

    # 夹具二：补齐 override → PASS，已配对 3。
    good_mirror = {
        'app/src/main/java/a/Host.kt': (
            'package a\n'
            'interface Host {\n'
            '    fun currentTextPage(): TextPage?\n'
            '    fun textPageState(): InkState\n'
            '    val title: String\n'
            '}\n'),
        'app/src/main/java/a/Controller.kt': (
            'package a\n'
            'class Controller : Host {\n'
            '    override fun currentTextPage(): TextPage? = null\n'
            '    override fun textPageState(): InkState = InkState.Ok\n'
            '    override val title: String = "x"\n'
            '}\n'),
    }
    rc, fails, _, matched, _ = _run_case(good_mirror)
    if rc != 0 or fails or matched != 3:
        ok = False
        print(f'  SELFTEST FAIL 补 override 样本应 PASS 且配对 3：rc={rc} fails={fails} matched={matched}')
    else:
        print('[selftest] 夹具二 补齐 override → 独立 PASS OK，已配对 3')

    # 夹具三（放行 PRIVATE）：private 同名成员不参与 override 解析，不判红。
    priv = {
        'app/src/main/java/a/Host.kt': 'package a\ninterface Host { fun bar(): Int }\n',
        'app/src/main/java/a/C.kt': (
            'package a\nclass C : Host {\n'
            '    private fun bar(): Int = 1\n'
            '    override fun bar(): Int = 2\n'
            '}\n'),
    }
    rc, fails, skips, _, _ = _run_case(priv)
    if rc != 0 or fails or skips.get('PRIVATE', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL private 同名应放行：rc={rc} fails={fails} skips={skips}')
    else:
        print('[selftest] 夹具三 private 同名 → SKIP:PRIVATE 放行 OK')

    # 夹具四（放行 EXTENSION）：不同接收者的扩展函数同名同签名，不进匹配。
    ext = {
        'app/src/main/java/a/Foo.kt': 'package a\ninterface Foo { fun bar(x: Int) }\n',
        'app/src/main/java/a/C.kt': (
            'package a\nclass C : Foo {\n'
            '    fun Other.bar(x: Int) = Unit\n'
            '    override fun bar(x: Int) {}\n'
            '}\n'),
    }
    rc, fails, skips, _, _ = _run_case(ext)
    if rc != 0 or fails or skips.get('EXTENSION', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL 扩展接收者同名应放行：rc={rc} fails={fails} skips={skips}')
    else:
        print('[selftest] 夹具四 不同接收者扩展函数 → SKIP:EXTENSION 放行 OK')

    # 夹具五（放行 DIFF-SIG）：同名同参个数，泛型实参不同，类型串不同 → 不判同签名。
    diff_sig = {
        'app/src/main/java/a/Foo.kt': 'package a\ninterface Foo { fun f(list: List<String>) }\n',
        'app/src/main/java/a/C.kt': 'package a\nclass C : Foo { fun f(list: List<Long>) = Unit }\n',
    }
    rc, fails, skips, _, _ = _run_case(diff_sig)
    if rc != 0 or fails or skips.get('DIFF-SIG', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL 泛型实参不同应放行：rc={rc} fails={fails} skips={skips}')
    else:
        print('[selftest] 夹具五 泛型擦除后不同签名 → SKIP:DIFF-SIG 放行 OK')

    # 夹具六（放行 TOPLEVEL）：类体外顶层普通同名函数，不进类成员匹配。
    top = {
        'app/src/main/java/a/Foo.kt': 'package a\ninterface Foo { fun bar() }\n',
        'app/src/main/java/a/C.kt': (
            'package a\n'
            'class C : Foo { override fun bar() {} }\n'
            'fun bar() = Unit\n'),
    }
    rc, fails, skips, _, _ = _run_case(top)
    if rc != 0 or fails or skips.get('TOPLEVEL', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL 顶层同名函数应放行且计数递增：rc={rc} fails={fails} skips={skips}')
    else:
        print('[selftest] 夹具六 接口外普通同名顶层函数 → SKIP:TOPLEVEL 放行 OK（计数 '
              f'{skips.get("TOPLEVEL", 0)}）')

    # 夹具七（放行 COMPANION）：伴生对象内同名成员不是类体重声明。
    comp = {
        'app/src/main/java/a/Foo.kt': 'package a\ninterface Foo { fun bar() }\n',
        'app/src/main/java/a/C.kt': (
            'package a\nclass C : Foo {\n'
            '    companion object { fun bar() = Unit }\n'
            '    override fun bar() {}\n'
            '}\n'),
    }
    rc, fails, skips, _, _ = _run_case(comp)
    if rc != 0 or fails or skips.get('COMPANION', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL 伴生对象同名应放行且计数递增：rc={rc} fails={fails} skips={skips}')
    else:
        print('[selftest] 夹具七 伴生对象内同名 → SKIP:COMPANION 放行 OK（计数 '
              f'{skips.get("COMPANION", 0)}）')

    # 夹具八（放行 LOCAL）：方法体内部局部函数同名，不参与类成员匹配。
    local = {
        'app/src/main/java/a/Foo.kt': 'package a\ninterface Foo { fun bar() }\n',
        'app/src/main/java/a/C.kt': (
            'package a\nclass C : Foo {\n'
            '    override fun bar() {\n'
            '        fun localBar() = Unit\n'
            '    }\n'
            '}\n'),
    }
    rc, fails, skips, _, _ = _run_case(local)
    if rc != 0 or fails or skips.get('LOCAL', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL 局部函数应放行：rc={rc} fails={fails} skips={skips}')
    else:
        print('[selftest] 夹具八 函数体内部局部函数 → SKIP:LOCAL 放行 OK')

    # 夹具九（间接实现）：A : Foo 已配对；B : A 重声明同名缺 override → 沿链追到 Foo，判红。
    indirect = {
        'app/src/main/java/a/Foo.kt': 'package a\ninterface Foo { fun bar() }\n',
        'app/src/main/java/a/A.kt': 'package a\nopen class A : Foo { override fun bar() {} }\n',
        'app/src/main/java/a/B.kt': 'package a\nclass B : A { fun bar() {} }\n',
    }
    rc, fails, _, _, _ = _run_case(indirect)
    if rc != 1 or len(fails) != 1 or fails[0]['name'] != 'bar' or not fails[0]['file'].endswith('B.kt'):
        ok = False
        print(f'  SELFTEST FAIL 间接实现重声明应沿链判红：rc={rc} fails={fails}')
    else:
        print(f'[selftest] 夹具九 间接实现 B : A : Foo 重声明缺 override → 沿链 FAIL OK：'
              f'{fails[0]["file"]}:{fails[0]["line"]}')

    # 夹具十（改动面必红，样本 a）：改动文件内实现缺 override → --changed 口径 rc=1，
    # 红行点名 文件:行 + 成员名。
    changed_bad = {
        'app/src/main/java/a/ProbeHost.kt': (
            'package a\ninterface ProbeHost {\n'
            '    fun probeName(): String\n    val probeTitle: String\n}\n'),
        'app/src/main/java/a/ProbeBad.kt': (
            'package a\nclass ProbeBad : ProbeHost {\n'
            '    fun probeName(): String = "bad"\n    val probeTitle: String = "t"\n}\n'),
        'app/src/main/java/a/ProbeGood.kt': (
            'package a\nclass ProbeGood : ProbeHost {\n'
            '    override fun probeName(): String = "good"\n'
            '    override val probeTitle: String = "t"\n}\n'),
    }
    rc, fails, _, _, _, judge = _run_changed(changed_bad, ['ProbeBad.kt'])
    bad_fails = [f for f in fails if f['file'].endswith('ProbeBad.kt')]
    if rc != 1 or len(bad_fails) != 2 or not all(f['line'] and f['name'] for f in bad_fails):
        ok = False
        print(f'  SELFTEST FAIL 改动面内缺 override 应判红并点名行/成员：rc={rc} fails={fails}')
    else:
        print(f'[selftest] 夹具十 改动面必红 OK：{len(bad_fails)} 条，'
              f'{bad_fails[0]["file"]}:{bad_fails[0]["line"]} `{bad_fails[0]["name"]}`，'
              f'{bad_fails[1]["file"]}:{bad_fails[1]["line"]} `{bad_fails[1]["name"]}`')

    # 夹具十一（改动面必绿，样本 b）：改动文件实现已带 override → --changed 口径 rc=0，
    # 已配对 2，不误报。
    rc, fails, _, matched, _, _ = _run_changed(changed_bad, ['ProbeGood.kt'])
    if rc != 0 or fails or matched != 2:
        ok = False
        print(f'  SELFTEST FAIL 改动面内已配对应 PASS：rc={rc} fails={fails} matched={matched}')
    else:
        print('[selftest] 夹具十一 改动面必绿 OK，已配对 2')

    # 夹具十二（防反，样本 c）：缺 override 的 ProbeBad 不在改动面 → 不得被 --changed 判红；
    # judge 集只含 ProbeGood.kt，ProbeBad.kt 排除在外。
    rc, fails, _, _, _, judge = _run_changed(changed_bad, ['ProbeGood.kt'])
    bad_in_judge = any('ProbeBad.kt' in j for j in judge)
    if rc != 0 or fails or bad_in_judge:
        ok = False
        print(f'  SELFTEST FAIL 非改动文件缺 override 不得判红：rc={rc} fails={fails} judge={judge}')
    else:
        print('[selftest] 夹具十二 防反 OK：非改动文件 ProbeBad.kt 未进改动面，0 红')

    if not ok:
        print('SELFTEST: FAIL（存在未通过的夹具）')
        return 1
    print('[selftest] 夹具已自删，双向自测通过（事故镜像红/补 override 绿；PRIVATE/EXTENSION/'
          'DIFF-SIG/TOPLEVEL/COMPANION/LOCAL 六类放行；间接实现沿链判红）。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    only_changed = '--changed' in sys.argv
    audit = '--audit' in sys.argv
    sys.exit(main(only_changed=only_changed, audit=audit))
