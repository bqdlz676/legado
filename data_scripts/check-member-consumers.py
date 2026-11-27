#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""g65 消费方反查闸：删/改枚举 · 密封成员 · 伴生常量前，先机械查全仓消费方。

事故下限（2026-11-27 复盘-B6-04）：`DiscoverRegion` 枚举被删前没人 grep 消费方
（后来实查零消费才安全）；`InkReadController.kt` 删 `VerticalPageView` 跨包 import
是真红（该类在同包 ink 定义）；B4-07 一轮动四个 ink 文件，跨模块风险就在这里。
成员名字从源码面消失后，import / when 分支 / 类型引用 / 字符串 handler 映射里的
残留引用只有编译期才炸，沙箱无编译链，必须在 push 前静态拦住。

三面判据（全部可单条命令判真假）：

1) 删除面（DELETE，FAIL）：改动面相对 origin/main 删掉了 enum entry、sealed 子类
   声明或伴生常量名字，则在扫描根内全仓 grep 该名字（token 边界；注释剥掉、字符串
   保留——字符串 handler 映射也算残留）。任何残留引用即红，点名 文件:行。
2) 新增面（ADD，WARN 不硬红）：新增 sealed 子类或 enum entry 后，找全仓对外层类型
   的 when 表达式：有 else 分支、或已有对应新成员候选分支即记配对；无 else 且未覆盖
   新成员必须点名 文件:行 与缺哪个成员，不许默不吭声。
3) 改名面（RENAME）：同文件内一个公开名字被删且同时新增一个新名字，按改名判：
   旧名残留为零 → 通过并输出配对记录；旧名有残留 → 红。

形态覆盖：多行 when、带 subject 的 when、`when (val x = …)`、sealed 嵌套子类
`object Foo : Bar.Baz` 都在解析面内。

口径：默认 `--changed` 只判相对 origin/main 的改动面；不带参数跑全仓只出 audit
清单，不得从 0 变红墙（存量债务走台账，不进 blocking 改动面）。
退出码：0 = 无 DELETE/RENAME 残留；1 = 有 FAIL。新增面 WARN 不改变退出码。
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_ROOTS = ['app/src', 'screenshot/src']

# ---- 声明头扫描 -------------------------------------------------------------

# enum class 头
ENUM_HEAD = re.compile(r'^\s*enum\s+class\s+(?P<name>[A-Za-z_]\w*)')
COMPANION_HEAD = re.compile(r'^\s*companion\s+object')
VAL_HEAD = re.compile(r'^\s*(?:const\s+)?val\s+(?P<name>[A-Za-z_]\w*)')
# when 块在 find_whens 里手工配平 subject 括号，兼容 `when (val x = f(a, b)) {` 内层括号。
IDENT = re.compile(r'[A-Za-z_]\w*')

# 根声明修饰符：带这些修饰符的 class/object 是类型根本身，不登记成某个父类型的子类。
# 为什么单列：同行扫描时 `abstract class Foo : Bar`、`sealed class Foo : Bar` 也带父类型冒号，
# 若不剔除会把根错并进子类集合；`data` 不是根修饰符（data class 仍是子类形态）。
_ROOT_MODS = {'sealed', 'enum', 'abstract', 'open', 'final', 'inner', 'interface',
              'private', 'internal', 'public', 'protected', 'companion', 'const',
              'override', 'lateinit'}


def _skip_ws(s: str, i: int) -> int:
    while i < len(s) and s[i] in ' \t':
        i += 1
    return i


def _balanced_paren(s: str, i: int, open_c: str = '(', close_c: str = ')'):
    """s[i] 为开括号；字符串 / 字符字面量感知配平，返回闭括号后一位下标；配不平返回 None。

    为什么不靠 `[^)]*` 惰性正则：主构造参数含函数类型 `() -> Unit`、嵌套括号
    `(val f: (String) -> Unit)` 或串内括号时，第一个 `)` 就会截断参数列表，父类型识别落空
    （真仓实例 InkBottomSheet.kt:91 的 `Wired` 因此漏登记）。
    """
    depth = 0
    state = None  # None / str / char / raw
    n = len(s)
    while i < n:
        c = s[i]
        three = s[i:i + 3]
        if state is None:
            if three == '"""':
                state, i = 'raw', i + 3
                continue
            if c == '"':
                state, i = 'str', i + 1
                continue
            if c == "'":
                state, i = 'char', i + 1
                continue
            if c == open_c:
                depth += 1
            elif c == close_c:
                depth -= 1
                if depth == 0:
                    return i + 1
            i += 1
            continue
        if state == 'raw':
            if three == '"""':
                state, i = None, i + 3
                continue
            i += 1
            continue
        if state == 'str':
            if c == '\\':
                i += 2
                continue
            if c == '"':
                state = None
            i += 1
            continue
        if state == 'char':
            if c == '\\':
                i += 2
                continue
            if c == "'":
                state = None
            i += 1
            continue
    return None


def _read_parent(s: str, i: int):
    """从冒号后读父类型短名 / 限定名 + 可选泛型；返回 (parent, 新下标)，无则 (None, i)。"""
    i = _skip_ws(s, i)
    m = re.match(r'[A-Za-z_][\w.]*', s[i:])
    if not m:
        return None, i
    parent = m.group(0)
    i += m.end()
    if i < len(s) and s[i] == '<':
        close = _balanced_paren(s, i, '<', '>')
        if close:
            i = close
    return parent, i


def _iter_sub_heads(line: str):
    """扫描一行内全部子类声明，产出 (name, parent)；支持同行多个声明与内联花括号写法。

    覆盖形态：`data class Wired(val onAction: () -> Unit) : SheetOfflineAction`、
    `object Foo : Bar.Baz`、`data object HintOnly : SheetOfflineAction`，以及
    `sealed interface A { object X : A; object Y : A }` 同行排布。根修饰符前置的声明跳过。
    """
    s = line
    n = len(s)
    i = 0
    pending_root = False
    while i < n:
        if not (s[i].isalpha() or s[i] == '_'):
            i += 1
            continue
        m = re.match(r'[A-Za-z_]\w*', s[i:])
        if not m:
            # 非 ASCII 字母（如串内汉字）不构成 Kotlin 标识符，跳过该字符。
            i += 1
            continue
        word, wend = m.group(0), i + m.end()
        if word in _ROOT_MODS:
            pending_root = True
            i = wend
            continue
        if word == 'data':
            i = wend
            continue
        if word in ('object', 'class'):
            is_root, pending_root = pending_root, False
            j = _skip_ws(s, wend)
            nm = re.match(r'[A-Za-z_]\w*', s[j:])
            if not nm:
                i = wend
                continue
            name = nm.group(0)
            j += nm.end()
            j = _skip_ws(s, j)
            if j < n and s[j] == '(':
                close = _balanced_paren(s, j)
                j = close if close else n
                j = _skip_ws(s, j)
            parent = None
            if j < n and s[j] == ':':
                parent, _ = _read_parent(s, j + 1)
            if parent and not is_root:
                yield name, parent
            i = wend
            continue
        pending_root = False
        i = wend


# ---- 注释 / 字符串状态机 ----------------------------------------------------

def strip_comments(text: str) -> str:
    """剥 // 行注释与 /* */ 块注释；字符串（双引号 / 字符 / 三引号）原样保留。

    为什么保留字符串：handler 映射 `"VerticalPageView"` 这类字符串字面量是真消费方，
    剥掉就会把真残留放走；注释里提到旧名只是文档痕迹，不能算残留引用。
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
                state, i = 'block', i + 2
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
                i += 2
                continue
            if c == "'":
                state = None
            i += 1
            continue
    return ''.join(out)


def _mask_code(text: str) -> str:
    """等长掩码：注释面 + 字符串 / 字符字面量内容替换成空格（换行保留）。

    与 strip_comments 的分工：删除面残留 grep 保留字符串（handler 字符串映射算真残留）；
    归属提及匹配反过来要把注释和字符串都抹掉——注释里写父类型名、字符串里拼父类型名都不是
    真消费方，不能据此把文件纳入 when 覆盖候选。等长掩码偏移可复用，串内括号不计入代码位。
    写法思路同仓内 check-member-override.py 的 _mask 状态机。
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
                state, i = 'block', i + 2
                out.append(' '); out.append(' ')
                continue
            if three == '"""':
                state, i = 'raw', i + 3
                out.extend([' ', ' ', ' '])
                continue
            if c == '"':
                state, i = 'str', i + 1
                out.append(' ')
                continue
            if c == "'":
                state, i = 'char', i + 1
                out.append(' ')
                continue
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
                out.append(' '); out.append(' ')
                state, i = None, i + 2
                continue
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        if state == 'raw':
            if three == '"""':
                state, i = None, i + 3
                out.extend([' ', ' ', ' '])
                continue
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        if state == 'str':
            if c == '\\':
                out.append(' '); out.append(' ')
                i += 2
                continue
            if c == '"':
                state = None
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        if state == 'char':
            if c == '\\':
                out.append(' '); out.append(' ')
                i += 2
                continue
            if c == "'":
                state = None
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
    return ''.join(out)


def _balanced_block(text: str, open_idx: int) -> str:
    """从 text[open_idx] 的 `{` 起做括号 / 字符串感知配平，返回块体文本（不含花括号）。

    字符串开引号必须落进块体：否则串内逗号会被当成顶层分隔符，枚举条目被错并。
    """
    depth = 0
    state = None
    n = len(text)
    j = open_idx
    body = []
    while j < n:
        c = text[j]
        three = text[j:j + 3]
        if state is None:
            if three == '"""':
                state = 'raw'
                body.append(three)
                j += 3
                continue
            if c == '"':
                state = 'str'
                body.append(c)
                j += 1
                continue
            if c == "'":
                state = 'char'
                body.append(c)
                j += 1
                continue
            if c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    return ''.join(body)
            else:
                body.append(c)
            j += 1
            continue
        if state == 'raw':
            if three == '"""':
                state = None
                body.append(three)
                j += 3
                continue
            body.append(c)
            j += 1
            continue
        if state == 'str':
            body.append(c)
            if c == '\\':
                body.append(text[j + 1:j + 2])
                j += 2
                continue
            if c == '"':
                state = None
            j += 1
            continue
        if state == 'char':
            body.append(c)
            if c == '\\':
                j += 2
                continue
            if c == "'":
                state = None
            j += 1
            continue
    return ''.join(body)


def _split_top_level_commas(seg: str) -> list:
    """按顶层逗号切分枚举条目；括号 / 字符串内逗号不计。"""
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


def parse_decls(text: str) -> dict:
    """从一份 Kotlin 源码抽出三类成员声明。

    返回：
      enums:   [(enum_type, entry_name)]
      subs:    [(subclass_name, parent_qualified)]
      consts:  [companion 块内 val / const val 名字]
    同名集合按 kind 归并；解析失败兜底为空集，不抛异常。
    """
    code = strip_comments(text)
    enums, subs, consts = [], [], []
    lines = code.splitlines()

    # 每行在 code 里的字符下标，供花括号配平定位（不依赖行文本重复出现位置）。
    offsets, off = [], 0
    for ln in lines:
        offsets.append(off)
        off += len(ln) + 1

    enum_bodies = []  # (type_name, body)

    def block_at(line_idx: int):
        li = line_idx
        brace = lines[li].find('{')
        while brace < 0 and li + 1 < len(lines):
            li += 1
            brace = lines[li].find('{')
        if brace < 0:
            return None
        return _balanced_block(code, offsets[li] + brace)

    i = 0
    while i < len(lines):
        line = lines[i]
        m_enum = ENUM_HEAD.match(line)
        if m_enum:
            body = block_at(i)
            if body is not None:
                enum_bodies.append((m_enum.group('name'), body))
            i += 1
            continue

        if COMPANION_HEAD.match(line):
            body = block_at(i)
            if body is not None:
                for vl in body.splitlines():
                    vm = VAL_HEAD.match(vl)
                    if vm:
                        consts.append(vm.group('name'))
            i += 1
            continue

        # 子类声明走括号配平扫描器：支持函数类型 / 嵌套括号构造参数、同行多个声明。
        # 先过掩码抹掉字符串 / 字符内容，避免串里写着 `object Foo : Bar` 被全局扫描误登记。
        for sub_name, sub_parent in _iter_sub_heads(_mask_code(line)):
            sub_parent = re.sub(r'<[^>]*>$', '', sub_parent)
            subs.append((sub_name, sub_parent))
        i += 1

    for type_name, body in enum_bodies:
        semi = re.search(r'(?<![\w.]);', body)
        entry_seg = body[:semi.start()] if semi else body
        for piece in _split_top_level_commas(entry_seg):
            em = IDENT.match(piece.strip())
            if em:
                enums.append((type_name, em.group(0)))

    return {'enums': enums, 'subs': subs, 'consts': consts}


def _decl_key(kind: str, name: str) -> str:
    return f'{kind}:{name}'


def diff_decls(old: str, new: str) -> dict:
    """比对新旧两份源码，返回 removed / added 三组成员。

    每组元素：(kind, name, parent_or_type)。
    """
    o = parse_decls(old or '')
    n = parse_decls(new or '')
    old_map, new_map = {}, {}
    for t, e in o['enums']:
        old_map.setdefault(_decl_key('enum', e), ('enum', e, t))
    for t, e in n['enums']:
        new_map.setdefault(_decl_key('enum', e), ('enum', e, t))
    for nm, par in o['subs']:
        old_map.setdefault(_decl_key('sub', nm), ('sub', nm, par))
    for nm, par in n['subs']:
        new_map.setdefault(_decl_key('sub', nm), ('sub', nm, par))
    for nm in o['consts']:
        old_map.setdefault(_decl_key('const', nm), ('const', nm, 'companion'))
    for nm in n['consts']:
        new_map.setdefault(_decl_key('const', nm), ('const', nm, 'companion'))

    removed = [v for k, v in old_map.items() if k not in new_map]
    added = [v for k, v in new_map.items() if k not in old_map]
    return {'removed': removed, 'added': added}


# ---- 消费方残留 grep ---------------------------------------------------------

def _kt_files(root: str) -> list:
    out = []
    for dirpath, _, names in os.walk(root):
        for nm in names:
            if nm.endswith('.kt'):
                out.append(os.path.join(dirpath, nm))
    return sorted(out)


# 局部绑定收集：val / var / fun 声明名 + 函数形参名。最小符号表只认同文件内的局部绑定，
# 不做跨函数作用域解析（静态闸拿不到完整类型表）；命中是否降级还要叠加位置强弱判定。
LOCAL_BIND = re.compile(r'\b(?:val|var|fun)\s+([A-Za-z_]\w*)')
LOCAL_PARAM = re.compile(r'\(\s*([A-Za-z_]\w*)\s*:')


def _local_bindings(code: str) -> set:
    names = set()
    for m in LOCAL_BIND.finditer(code):
        names.add(m.group(1))
    for m in LOCAL_PARAM.finditer(code):
        names.add(m.group(1))
    return names


def _strong_position(line: str, start: int) -> bool:
    """命中是否落在类型 / 限定引用位：`.Name`、import、is/as、类型标注 `: Name`。

    这些位置基本不可能是同名局部变量遮蔽；裸标识符独立出现（赋值右值、表达式变量）
    才可能是局部变量撞名，交给 local 标记降级。
    """
    k = start - 1
    while k >= 0 and line[k] in ' \t':
        k -= 1
    if k >= 0 and line[k] == '.':
        return True
    head = line[:start]
    if re.search(r'\b(import|is|as)\s*$', head) or re.search(r':\s*$', head):
        return True
    return False


def grep_name(name: str, roots: list) -> list:
    """全仓 token 边界 grep 成员名；剥注释、保留字符串。

    返回命中 dict 列表：path / line / snippet / strong（类型或限定引用位）/
    local（该名在本文件有局部 val/var/fun/形参绑定）。删除面判红只认强位或非局部命中；
    仅局部绑定支持的裸标识符命中交上层降为提示，避免同名局部变量造成 blocking 误报。
    """
    hits = []
    pat = re.compile(r'(?<![A-Za-z0-9_])' + re.escape(name) + r'(?![A-Za-z0-9_])')
    for root in roots:
        if not os.path.isdir(root):
            continue
        for f in _kt_files(root):
            try:
                text = open(f, encoding='utf-8', errors='ignore').read()
            except OSError:
                continue
            code = strip_comments(text)
            locals_ = _local_bindings(code)
            for ln, raw in enumerate(code.splitlines(), 1):
                for m in pat.finditer(raw):
                    hits.append({
                        'path': f, 'line': ln, 'snippet': raw.strip()[:80],
                        'strong': _strong_position(raw, m.start()),
                        'local': name in locals_,
                    })
    return hits


# ---- when 覆盖解析 -----------------------------------------------------------

def find_whens(text: str) -> list:
    """抽全部 when 块：subject / 是否有 else / 分支里出现的成员名集合 / 起始行号。

    subject 括号做配平，兼容 `when (val x: Bar = get(a, b))` 这类内层带括号的写法；
    裸 `when {` 记为 bare。
    """
    code = strip_comments(text)
    out = []
    for km in re.finditer(r'\bwhen\b', code):
        i = km.end()
        subj = ''
        # 跳过空白
        while i < len(code) and code[i] in ' \t\r\n':
            i += 1
        if i < len(code) and code[i] == '(':
            depth = 0
            j = i
            while j < len(code):
                c = code[j]
                if c == '(':
                    depth += 1
                elif c == ')':
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            if depth != 0:
                continue
            subj = code[i + 1:j].strip()
            i = j + 1
        while i < len(code) and code[i] in ' \t\r\n':
            i += 1
        if i >= len(code) or code[i] != '{':
            continue  # 不是 when 块（如英文单词 when 出现在串里）
        brace = i
        body = _balanced_block(code, brace)
        line = code.count('\n', 0, km.start()) + 1

        # 分支按行粗分：Kotlin 分支通常 `pattern -> body` 独占一行；多行模式属少数边界，
        # 静态闸只核 else 兜底与分支名候选，拿不到完整模式时不硬判。
        has_else = re.search(r'(?m)^\s*else\b', body) is not None
        branch_names = set()
        for bl in body.splitlines():
            if '->' in bl:
                prefix = bl.split('->', 1)[0]
                for ident in IDENT.finditer(prefix):
                    branch_names.add(ident.group(0))
        out.append({'line': line, 'subj': subj, 'else': has_else,
                    'branches': branch_names, 'bare': not subj})
    return out


def _parent_short(parent: str) -> str:
    return parent.split('.')[-1] if parent else ''


def check_added_members(added: list, roots: list) -> tuple:
    """新增面：对每个新成员，找归属父类型的 when 块，缺 else 且未覆盖则 WARN 点名。

    返回 (warns, infos, notes)：warns 为须点名的 WARN 缺口；infos 为 INFO 留痕
    （找不到归属 when 块）；notes 为配对留痕（有 else 兜底 / 已有候选分支）。三类分行，
    不混列。归属口径（静态闸拿不到接收者类型，拿不到就不硬判）：when 主体显式标注父类型，
    或所在文件掩码后（注释 / 字符串已抹除）仍提到父类型短名——注释或串里的同名提及不算消费方。
    """
    warns, infos, notes = [], [], []
    # 预扫文件：文件路径 → 掩码文 / when 列表（归属提及只认掩码后的代码位）
    files = []
    for root in roots:
        if not os.path.isdir(root):
            continue
        for f in _kt_files(root):
            try:
                text = open(f, encoding='utf-8', errors='ignore').read()
            except OSError:
                continue
            files.append((f, _mask_code(text), find_whens(text)))

    for kind, name, parent in added:
        if kind not in ('sub', 'enum'):
            continue
        pshort = _parent_short(parent)
        candidates = []
        for f, masked, whens in files:
            mentions = (not pshort) or (re.search(
                r'(?<![A-Za-z0-9_])' + re.escape(pshort) + r'(?![A-Za-z0-9_])', masked) is not None)
            for w in whens:
                typed = bool(pshort) and pshort in w['subj']
                if mentions or typed:
                    candidates.append((f, w))
        if not candidates:
            infos.append(f'INFO  [WHEN-COVER] 新成员 {parent}.{name}：未找到归属父类型 {parent} 的 when 块（无 else 可核，留人工复查）')
            continue
        for f, w in candidates:
            if w['else']:
                notes.append(f'PAIR  [WHEN-COVER] {f}:{w["line"]} when 有 else 兜底，新成员 {name}（父类型 {parent}）走默认分支，配对成立')
                continue  # 有 else 兜底，新成员走默认分支，配对成立
            if name in w['branches']:
                notes.append(f'PAIR  [WHEN-COVER] {f}:{w["line"]} when 已有候选分支覆盖新成员 {name}（父类型 {parent}），配对成立')
                continue  # 已有对应候选分支
            # 兼容限定写法 `Parent.Name`：分支集合已抽末段名，上面 name 命中即可
            warns.append(
                f'WARN  [WHEN-COVER] {f}:{w["line"]} when 无 else 且未覆盖新成员 {name}'
                f'（父类型 {parent}；bare={w["bare"]}）')
    return warns, infos, notes


# ---- 改动面采集 -------------------------------------------------------------

def git_kt_diff(base: str = 'origin/main') -> list:
    """相对 base 改动的 .kt 文件路径列表；git 不可用返回空集（空集不判 PASS，见主流程）。

    路径不传通配符：git pathspec 的 `*` 默认不跨目录，'*.kt' 会漏子目录命中；
    先全量取改动名再在 Python 侧按后缀过滤，与 g64 改动面口径一致。
    未跟踪新文件（??）一并纳入：新 sealed 子类若只落在新文件里，diff 不带它就漏判新增面。
    """
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


def git_show_old(path: str, base: str = 'origin/main') -> str:
    try:
        out = subprocess.run(['git', 'show', f'{base}:{path}'],
                              capture_output=True, text=True, timeout=30, cwd=ROOT)
        return out.stdout if out.returncode == 0 else ''
    except Exception:
        return ''


# ---- 主判定 -----------------------------------------------------------------

def evaluate(pairs: list, roots: list, audit: bool) -> tuple:
    """pairs: [(rel_path, old_text, new_text)]。

    返回 (fails, warns, infos, records, stats)：fails=DELETE/改名残留 blocking；
    warns=新增面 WARN 点名；infos=INFO 提示（局部遮蔽 / 无归属 when / 改名跳过）；
    records=配对留痕（REF-CHECK 零残留、PAIR 配对、RENAME 真实改名对）。
    """
    fails, warns, infos, records = [], [], [], []
    removed_all, added_all = [], []
    per_file = []

    for rel, old, new in pairs:
        d = diff_decls(old, new)
        if d['removed'] or d['added']:
            per_file.append((rel, d))
        removed_all.extend((rel, *v) for v in d['removed'])
        added_all.extend((rel, *v) for v in d['added'])

    # 1) 删除面：旧名残留 grep。强位（类型/限定引用）或非局部绑定命中才判红；
    #    仅同名局部变量 / 裸标识符支持的命中降为 INFO 提示，不进 blocking。
    for rel, kind, name, parent in removed_all:
        hits = grep_name(name, roots)
        blocking = [h for h in hits if h['strong'] or not h['local']]
        shadow = [h for h in hits if not h['strong'] and h['local']]
        if blocking:
            loc = '；'.join(f'{h["path"]}:{h["line"]}' for h in blocking[:5])
            more = f' 等 {len(blocking)} 处' if len(blocking) > 5 else ''
            fails.append(f'DELETE {rel} 删掉 {kind} 成员 `{name}`（{parent}）后仍有残留引用：{loc}{more}')
        elif shadow:
            h0 = shadow[0]
            infos.append(
                f'INFO  [LOCAL-SHADOW] {rel} 删除 `{name}`（{parent}）仅余 {len(shadow)} 处'
                f'同名局部变量/裸标识符命中（{h0["path"]}:{h0["line"]}），无包级/类级声明支持，降为提示不判红')
        else:
            records.append(f'REF-CHECK 删除成员 `{name}`（{parent}）全仓残留 0 处')

    # 3) 改名面：同类且同父类型短名才构成真实改名对，1:1 配对；不做旧×新笛卡尔积。
    for rel, d in per_file:
        by_kind_old, by_kind_new = {}, {}
        for kind, name, parent in d['removed']:
            by_kind_old.setdefault(kind, []).append((name, parent))
        for kind, name, parent in d['added']:
            by_kind_new.setdefault(kind, []).append((name, parent))
        for kind, olds in by_kind_old.items():
            news = by_kind_new.get(kind, [])
            used = set()
            for old_name, old_parent in olds:
                op = _parent_short(old_parent)
                match_idx = None
                for idx, (new_name, new_parent) in enumerate(news):
                    if idx in used:
                        continue
                    if _parent_short(new_parent) == op:
                        match_idx = idx
                        break
                if match_idx is not None:
                    new_name, new_parent = news[match_idx]
                    used.add(match_idx)
                    records.append(
                        f'RENAME 配对（{rel}）：`{old_name}`（{old_parent}）→ `{new_name}`（{new_parent}）')
                else:
                    infos.append(
                        f'INFO  [RENAME-SKIP] {rel}：`{old_name}`（{old_parent}）在同类同父类型'
                        f'新增面无配对新名，不记改名配对（旧名残留已在 DELETE 面核过）')

    # 2) 新增面：when 覆盖 WARN 点名 + INFO 留痕 + 配对留痕，三类分行。
    added_plain = [(kind, name, parent) for _, kind, name, parent in added_all]
    add_warns, add_infos, add_notes = check_added_members(added_plain, roots)
    warns.extend(add_warns)
    infos.extend(add_infos)
    records.extend(add_notes)

    stats = {'removed': len(removed_all), 'added': len(added_all),
             'files': len(per_file)}
    return fails, warns, infos, records, stats


def collect_roots() -> list:
    return [os.path.join(ROOT, r) for r in SOURCE_ROOTS]


def main(only_changed: bool = False, audit: bool = False) -> int:
    roots = collect_roots()

    if audit:
        # 全仓 audit 清单：只盘点不判红，存量债务不挡 push。
        n_enum = n_sub = n_const = n_when_noelse = 0
        for f in (x for r in roots if os.path.isdir(r) for x in _kt_files(r)):
            text = open(f, encoding='utf-8', errors='ignore').read()
            decl = parse_decls(text)
            n_enum += len(decl['enums'])
            n_sub += len(decl['subs'])
            n_const += len(decl['consts'])
            for w in find_whens(text):
                if not w['else'] and not w['bare']:
                    n_when_noelse += 1
        print(f'MEMBER-CONSUMERS AUDIT: 全仓清单（枚举条目 {n_enum} / 密封子类 {n_sub} / '
              f'伴生常量 {n_const} / 无 else 的 when {n_when_noelse}；存量只登记不判红）')
        return 0

    if only_changed:
        rels = git_kt_diff()
        pairs = []
        for rel in rels:
            old = git_show_old(rel)
            new = open(os.path.join(ROOT, rel), encoding='utf-8', errors='ignore').read()
            pairs.append((rel, old, new))
    else:
        # 未显式 --changed 且非 audit：按改动面跑（门禁接线口径），全仓盘点走 --audit。
        rels = git_kt_diff()
        pairs = []
        for rel in rels:
            old = git_show_old(rel)
            new = open(os.path.join(ROOT, rel), encoding='utf-8', errors='ignore').read()
            pairs.append((rel, old, new))

    fails, warns, infos, records, stats = evaluate(pairs, roots, audit=False)

    # 分类分行打：WARN 缺口、INFO 提示与配对留痕、FAIL 残留各占一段，不混列计数。
    for w in warns:
        print(w)
    for i in infos:
        print(i)
    for r in records:
        print(f'INFO  {r}')
    for f in fails:
        print(f'FAIL  {f}')

    n_info = len(infos) + len(records)
    if fails:
        print(f'MEMBER-CONSUMERS: FAIL（改动面 {stats["files"]} 文件；删除/改名残留 {len(fails)} 处；'
              f'新增面 WARN {len(warns)} 条；INFO 提示/配对 {n_info} 条）')
        return 1
    print(f'MEMBER-CONSUMERS: PASS（改动面 {stats["files"]} 文件；删除 {stats["removed"]} / '
          f'新增 {stats["added"]}，残留 0；新增面 WARN {len(warns)} 条；INFO 提示/配对 {n_info} 条）')
    return 0


# ---- 自测夹具 ---------------------------------------------------------------

def _write_kt(root, rel, body):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(body)
    return p


def _run_case(files: dict) -> tuple:
    """单样本独立跑：临时目录写夹具文件，直接走生产 evaluate()。

    返回 (rc, fails, warns, infos, records)；断言按类别（FAIL/WARN/INFO/PASS）+ 文件 + 行名核，
    不只比退出码。
    """
    d = tempfile.mkdtemp(prefix='member-consumers-')
    try:
        pairs = []
        for rel, (old, new) in files.items():
            _write_kt(d, rel, new)
            pairs.append((rel, old, new))
        fails, warns, infos, records, stats = evaluate(pairs, [d], audit=False)
        return (1 if fails else 0), fails, warns, infos, records
    finally:
        shutil.rmtree(d, ignore_errors=True)


def selftest() -> int:
    ok = True

    # 夹具一：删除后有残留必红。枚举 entry OLD_MODE 在另一文件 when 分支仍引用。
    bad_delete = {
        'app/src/main/java/a/Models.kt': (
            'package a\nenum class Mode { OLD_MODE, NEW_MODE }\n',
            'package a\nenum class Mode { NEW_MODE }\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nimport a.Mode\nfun f(m: Mode) = when (m) {\n'
            '    Mode.OLD_MODE -> 1\n    Mode.NEW_MODE -> 2\n}\n',
            'package a\nimport a.Mode\nfun f(m: Mode) = when (m) {\n'
            '    Mode.OLD_MODE -> 1\n    Mode.NEW_MODE -> 2\n}\n'),
    }
    rc, fails, warns, _, _ = _run_case(bad_delete)
    if rc != 1 or not any('OLD_MODE' in f for f in fails):
        ok = False
        print(f'  SELFTEST FAIL 删除残留样本应判红：rc={rc} fails={fails}')
    else:
        print('[selftest] 夹具一 删除后有残留 → 独立 FAIL OK：' + fails[0][:70])

    # 夹具二：删除后无残留必绿。子类 OldFoo 删除，仓内无任何引用。
    good_delete = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n    object NewFoo : Bar\n}\n',
            'package a\nsealed class Bar {\n    object NewFoo : Bar\n}\n'),
    }
    rc, fails, warns, _, records = _run_case(good_delete)
    if rc != 0 or fails:
        ok = False
        print(f'  SELFTEST FAIL 无残留样本应 PASS：rc={rc} fails={fails}')
    else:
        print('[selftest] 夹具二 删除后无残留 → 独立 PASS OK；残留 0 记录：' + records[-1][:60])

    # 夹具三：新增 sealed 子类，when 有 else → 不 WARN，配对放行。
    good_add = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n}\n',
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n    object NewFoo : Bar\n}\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f(b: Bar) = when (b) {\n    is Bar.OldFoo -> 1\n    else -> 0\n}\n',
            'package a\nfun f(b: Bar) = when (b) {\n    is Bar.OldFoo -> 1\n    else -> 0\n}\n'),
    }
    rc, fails, warns, _, records = _run_case(good_add)
    pair = [r for r in records if r.startswith('PAIR') and 'NewFoo' in r]
    if rc != 0 or any('NewFoo' in w for w in warns) or not pair:
        ok = False
        print(f'  SELFTEST FAIL 有 else 样本应放行并记配对：rc={rc} warns={warns} records={records}')
    else:
        print('[selftest] 夹具三 新增子类 + when 有 else → 放行并记配对 OK：' + pair[0][:60])

    # 夹具四：新增 sealed 子类，when 无 else 且无新成员分支 → 必须 WARN 点名 文件:行 + 缺谁。
    bad_add = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n}\n',
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n    object NewFoo : Bar\n}\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f(b: Bar) = when (b) {\n    is Bar.OldFoo -> 1\n}\n',
            'package a\nfun f(b: Bar) = when (b) {\n    is Bar.OldFoo -> 1\n}\n'),
    }
    rc, fails, warns, _, _ = _run_case(bad_add)
    if rc != 0 or not any('NewFoo' in w and 'Caller.kt:' in w for w in warns):
        ok = False
        print(f'  SELFTEST FAIL 无 else 样本应点名：rc={rc} warns={warns}')
    else:
        print('[selftest] 夹具四 新增子类 + when 无 else → WARN 点名 OK：' + warns[0][:70])

    # 夹具五：改名面，旧名残留 0 → PASS 并输出 RENAME 配对记录。
    good_rename = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n}\n',
            'package a\nsealed class Bar {\n    object RenamedFoo : Bar\n}\n'),
    }
    rc, fails, warns, _, records = _run_case(good_rename)
    rename_rec = [r for r in records if r.startswith('RENAME')]
    if rc != 0 or fails or not rename_rec:
        ok = False
        print(f'  SELFTEST FAIL 改名零残留应 PASS 且出配对：rc={rc} fails={fails} records={records}')
    else:
        print('[selftest] 夹具五 改名零残留 → PASS + RENAME 配对记录 OK：' + rename_rec[0][:70])

    # 夹具六：改名面，旧名有残留 → 红。
    bad_rename = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n}\n',
            'package a\nsealed class Bar {\n    object RenamedFoo : Bar\n}\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f(b: Bar) = b is Bar.OldFoo\n',
            'package a\nfun f(b: Bar) = b is Bar.OldFoo\n'),
    }
    rc, fails, _, _, _ = _run_case(bad_rename)
    if rc != 1 or not any('OldFoo' in f for f in fails):
        ok = False
        print(f'  SELFTEST FAIL 改名残留应判红：rc={rc} fails={fails}')
    else:
        print('[selftest] 夹具六 改名旧名残留 → 独立 FAIL OK：' + fails[0][:70])

    # 夹具七：sealed 嵌套子类 `object Foo : Bar.Baz` 形态可解析，新增面归属父类型。
    nested = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar {\n    sealed class Baz {\n        object One : Baz\n    }\n}\n',
            'package a\nsealed class Bar {\n    sealed class Baz {\n        object One : Baz\n        object Two : Baz\n    }\n}\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f(b: Bar.Baz) = when (b) {\n    Bar.Baz.One -> 1\n}\n',
            'package a\nfun f(b: Bar.Baz) = when (b) {\n    Bar.Baz.One -> 1\n}\n'),
    }
    rc, fails, warns, _, _ = _run_case(nested)
    if rc != 0 or not any('Two' in w for w in warns):
        ok = False
        print(f'  SELFTEST FAIL 嵌套子类 when 覆盖应点名 Two：rc={rc} warns={warns}')
    else:
        print('[selftest] 夹具七 嵌套子类 Bar.Baz.Two 无 else → WARN 点名 OK：' + warns[0][:70])

    # 夹具八：`when (val x: Bar = get(a, b))` 带内层括号的 val-subject 形态可解析，
    # 无 else 且未覆盖新成员仍须点名。
    val_subj = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n}\n',
            'package a\nsealed class Bar {\n    object OldFoo : Bar\n    object NewFoo : Bar\n}\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f() = when (val x: Bar = getBar()) {\n    is Bar.OldFoo -> 1\n}\n',
            'package a\nfun f() = when (val x: Bar = getBar()) {\n    is Bar.OldFoo -> 1\n}\n'),
    }
    parsed = find_whens('package a\nfun f() = when (val x: Bar = getBar()) {\n    is Bar.OldFoo -> 1\n}\n')
    rc, fails, warns, _, _ = _run_case(val_subj)
    if rc != 0 or len(parsed) != 1 or not parsed[0]['subj'].startswith('val x') \
            or not any('NewFoo' in w for w in warns):
        ok = False
        print(f'  SELFTEST FAIL val-subject 形态应解析并点名：rc={rc} parsed={parsed} warns={warns}')
    else:
        print('[selftest] 夹具八 when(val x = …) 内层括号解析 + 无 else 点名 OK：' + warns[0][:70])

    # 夹具九（修复1 · 函数类型 / 嵌套括号构造参数）：
    # 放行-解析登记：主构造参数含 `() -> Unit` 与嵌套括号的子类必须进子类集合。
    parse9 = parse_decls(
        'package a\ninternal sealed interface SheetOfflineAction {\n'
        '    data class Wired(val onAction: () -> Unit) : SheetOfflineAction\n'
        '    data object HintOnly : SheetOfflineAction\n}\n')
    subs9 = dict(parse9['subs'])
    if 'Wired' not in subs9 or subs9['Wired'] != 'SheetOfflineAction':
        ok = False
        print(f'  SELFTEST FAIL 夹具九 解析登记：函数类型构造参数子类未入集合：subs={parse9["subs"]}')
    else:
        print('[selftest] 夹具九 解析登记 OK：Wired（SheetOfflineAction）函数类型/嵌套括号已配平入表')

    # 守护：字符串字面量里写着 `object Foo : Bar` 不得被全局扫描误登记为子类。
    parse_guard = parse_decls('package a\nval label = "object Foo : Bar"\n')
    if 'Foo' in dict(parse_guard['subs']):
        ok = False
        print(f'  SELFTEST FAIL 夹具九 串内声明幻影：subs={parse_guard["subs"]}')
    else:
        print('[selftest] 夹具九 串内词守护 OK：字符串里的 object/Parent 未误登记')

    # 必失败-删除面：Wired 被删后另一文件限定引用 `SheetOfflineAction.Wired` 必须判红。
    fix1_delete = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed interface SheetOfflineAction {\n'
            '    data class Wired(val onAction: () -> Unit) : SheetOfflineAction\n'
            '    data object HintOnly : SheetOfflineAction\n}\n',
            'package a\nsealed interface SheetOfflineAction {\n'
            '    data object HintOnly : SheetOfflineAction\n}\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun act() = SheetOfflineAction.Wired { }\n',
            'package a\nfun act() = SheetOfflineAction.Wired { }\n'),
    }
    rc, fails, warns, _, _ = _run_case(fix1_delete)
    if rc != 1 or not any('Wired' in f and 'Caller.kt:' in f for f in fails):
        ok = False
        print(f'  SELFTEST FAIL 夹具九 删除残留应判红点名 Wired/Caller.kt：rc={rc} fails={fails}')
    else:
        print('[selftest] 夹具九 删除面必失败 OK：' + fails[0][:70])

    # 夹具十（修复2 · 密封头与子类同行声明）：
    # 放行-解析登记：单行内联花括号排布的两个子类都要建表。
    parse10 = parse_decls(
        'package a\nsealed interface SheetOfflineAction { '
        'data class Wired(val onAction: () -> Unit) : SheetOfflineAction; '
        'data object HintOnly : SheetOfflineAction }\n')
    subs10 = dict(parse10['subs'])
    if 'Wired' not in subs10 or 'HintOnly' not in subs10:
        ok = False
        print(f'  SELFTEST FAIL 夹具十 同行声明两子类未全入表：subs={parse10["subs"]}')
    else:
        print('[selftest] 夹具十 同行声明解析登记 OK：Wired / HintOnly 均入表')

    # 必失败-删除面：同行行内删掉 Wired，另一文件残留引用必须判红。
    fix2_delete = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed interface A { object X : A; object Y : A }\n',
            'package a\nsealed interface A { object Y : A }\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun act() = A.X\n',
            'package a\nfun act() = A.X\n'),
    }
    rc, fails, warns, _, _ = _run_case(fix2_delete)
    if rc != 1 or not any('X' in f and 'Caller.kt:' in f for f in fails):
        ok = False
        print(f'  SELFTEST FAIL 夹具十 同行删除残留应判红：rc={rc} fails={fails}')
    else:
        print('[selftest] 夹具十 删除面必失败 OK：' + fails[0][:70])

    # 夹具十一（修复3 · 注释 / 字符串提及不算归属消费方）：
    # 放行-不误报：父类型名只出现在行注释与字符串字面量里，掩码后不应纳入 when 覆盖候选，
    # 不得对该文件打 WARN 点名。
    fix3_comment = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar { object OldFoo : Bar }\n',
            'package a\nsealed class Bar { object OldFoo : Bar; object NewFoo : Bar }\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\n// 注释里提到 Bar，不算消费方\n'
            'val tag = "Bar"\nfun f() = 1\n',
            'package a\n// 注释里提到 Bar，不算消费方\n'
            'val tag = "Bar"\nfun f() = 1\n'),
    }
    rc, fails, warns, infos, _ = _run_case(fix3_comment)
    if rc != 0 or any('Caller.kt:' in w for w in warns):
        ok = False
        print(f'  SELFTEST FAIL 夹具十一 注释/串提及不应误报 WARN：rc={rc} warns={warns}')
    else:
        print('[selftest] 夹具十一 注释/串提及放行 OK：未对 Caller.kt 打 WARN')

    # 对照-真提及仍纳入：代码位真实出现父类型 Bar、when 无 else，仍须 WARN 点名 NewFoo。
    fix3_real = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar { object OldFoo : Bar }\n',
            'package a\nsealed class Bar { object OldFoo : Bar; object NewFoo : Bar }\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f(b: Bar) = when (b) { is Bar.OldFoo -> 1 }\n',
            'package a\nfun f(b: Bar) = when (b) { is Bar.OldFoo -> 1 }\n'),
    }
    rc, fails, warns, _, _ = _run_case(fix3_real)
    if rc != 0 or not any('NewFoo' in w and 'Caller.kt:' in w for w in warns):
        ok = False
        print(f'  SELFTEST FAIL 夹具十一 真提及应照常 WARN 点名：rc={rc} warns={warns}')
    else:
        print('[selftest] 夹具十一 真提及对照 OK：代码位 Bar 仍纳入覆盖判定')

    # 夹具十二（修复4 · 同名局部变量降为提示，不强堵）：
    # 红样本：限定 `.OldFoo` 强位残留仍判红。
    fix4_strong = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar { object OldFoo : Bar }\n',
            'package a\nsealed class Bar { }\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f(b: Bar) = Bar.OldFoo\n',
            'package a\nfun f(b: Bar) = Bar.OldFoo\n'),
    }
    rc, fails, _, _, _ = _run_case(fix4_strong)
    if rc != 1 or not any('OldFoo' in f for f in fails):
        ok = False
        print(f'  SELFTEST FAIL 夹具十二 强位残留应判红：rc={rc} fails={fails}')
    else:
        print('[selftest] 夹具十二 强位残留必失败 OK：' + fails[0][:70])

    # 提示放行样本：同名局部 `val OldFoo = …` 裸绑定，无包级/类级声明支持，降 INFO 不判红。
    fix4_local = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar { object OldFoo : Bar }\n',
            'package a\nsealed class Bar { }\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f() { val OldFoo = localThing(); return OldFoo }\n',
            'package a\nfun f() { val OldFoo = localThing(); return OldFoo }\n'),
    }
    rc, fails, warns, infos, _ = _run_case(fix4_local)
    shadow = [i for i in infos if 'LOCAL-SHADOW' in i and 'OldFoo' in i and 'Caller.kt' in i]
    if rc != 0 or fails or not shadow:
        ok = False
        print(f'  SELFTEST FAIL 夹具十二 同名局部应降 INFO 不判红：rc={rc} fails={fails} infos={infos}')
    else:
        print('[selftest] 夹具十二 局部遮蔽提示放行 OK：' + shadow[0][:70])

    # 夹具十三（修复5 · 分类分行 + 改名只配真实同父对）：
    # 分类样本：构造一个 WARN 与一个 INFO 并存的场景，断言两类不混列。
    fix5_class = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar { object OldFoo : Bar }\n',
            'package a\nsealed class Bar { object OldFoo : Bar; object NewFoo : Bar }\n'),
        'app/src/main/java/a/Caller.kt': (
            'package a\nfun f(b: Bar) = when (b) { is Bar.OldFoo -> 1 }\n',
            'package a\nfun f(b: Bar) = when (b) { is Bar.OldFoo -> 1 }\n'),
        'app/src/main/java/a/Other.kt': (
            'package a\n// 另一个文件只在注释提 Bar\nval x = "Bar"\n',
            'package a\n// 另一个文件只在注释提 Bar\nval x = "Bar"\n'),
    }
    rc, fails, warns, infos, _ = _run_case(fix5_class)
    if rc != 0 or any(not w.startswith('WARN') for w in warns) \
            or any(not i.startswith('INFO') for i in infos):
        ok = False
        print(f'  SELFTEST FAIL 夹具十三 WARN/INFO 应分类分行：warns={warns} infos={infos}')
    else:
        print('[selftest] 夹具十三 分类分行 OK：WARN 与 INFO 各自成段不混列')

    # 改名语义样本：同父类型一删一增 1:1 配对，只出 2 条 RENAME，不出笛卡尔积 4 条。
    fix5_rename = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Bar { object A : Bar; object B : Bar }\n',
            'package a\nsealed class Bar { object C : Bar; object D : Bar }\n'),
    }
    rc, fails, _, _, records = _run_case(fix5_rename)
    ren = [r for r in records if r.startswith('RENAME')]
    if rc != 0 or len(ren) != 2:
        ok = False
        print(f'  SELFTEST FAIL 夹具十三 同父改名应 1:1 两条配对：rc={rc} ren={ren}')
    else:
        print('[selftest] 夹具十三 同父改名 1:1 配对 OK：' + '；'.join(r[:40] for r in ren))

    # 跨父类型：旧名父 Foo、新名父 Bar，不构成真实改名对，不落 RENAME，落 RENAME-SKIP INFO。
    fix5_cross = {
        'app/src/main/java/a/Models.kt': (
            'package a\nsealed class Foo { object X : Foo }\n'
            'sealed class Bar { }\n',
            'package a\nsealed class Foo { }\n'
            'sealed class Bar { object Y : Bar }\n'),
    }
    rc, fails, _, infos, records = _run_case(fix5_cross)
    cross_ren = [r for r in records if r.startswith('RENAME')]
    skip = [i for i in infos if 'RENAME-SKIP' in i and 'X' in i]
    if rc != 0 or cross_ren or not skip:
        ok = False
        print(f'  SELFTEST FAIL 夹具十三 跨父改名不应配对：rc={rc} ren={cross_ren} infos={infos}')
    else:
        print('[selftest] 夹具十三 跨父改名跳过 OK：' + skip[0][:70])

    if not ok:
        print('SELFTEST: FAIL（存在未通过的夹具）')
        return 1
    print('[selftest] 夹具已自删，双向自测通过（删除红/绿、新增 else 放行/无 else 点名、改名配对/残留红、嵌套子类、val-subject、'
          '函数类型构造配平、同行声明、注释串掩码、局部遮蔽降级、分类分行与改名语义配对）。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    only_changed = '--changed' in sys.argv
    audit = '--audit' in sys.argv
    sys.exit(main(only_changed=only_changed, audit=audit))
