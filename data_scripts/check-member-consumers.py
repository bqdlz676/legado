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

# ---- 声明头正则 -------------------------------------------------------------

# sealed class / interface 头
SEALED_HEAD = re.compile(r'^\s*sealed\s+(?:class|interface)\s+(?P<name>[A-Za-z_]\w*)')
# enum class 头
ENUM_HEAD = re.compile(r'^\s*enum\s+class\s+(?P<name>[A-Za-z_]\w*)')
# 子类声明：`data class Foo(args...) : Bar.Baz(...)` / `object Foo : Bar()`，
# 主构造参数列表允许夹在名字与父类型之间；父类型可带泛型。
SUB_HEAD = re.compile(
    r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:data\s+)?(?:object|class)\s+(?P<name>[A-Za-z_]\w*)'
    r'(?:\([^)]*\))?'
    r'\s*(?::\s*(?P<parent>[A-Za-z_][\w.]*(?:<[^>]*>)?))?'
)
COMPANION_HEAD = re.compile(r'^\s*companion\s+object')
VAL_HEAD = re.compile(r'^\s*(?:const\s+)?val\s+(?P<name>[A-Za-z_]\w*)')
# when 块在 find_whens 里手工配平 subject 括号，兼容 `when (val x = f(a, b)) {` 内层括号。
IDENT = re.compile(r'[A-Za-z_]\w*')


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

        m_sub = SUB_HEAD.match(line)
        if m_sub and m_sub.group('parent'):
            parent = re.sub(r'<[^>]*>$', '', m_sub.group('parent'))
            subs.append((m_sub.group('name'), parent))
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


def grep_name(name: str, roots: list) -> list:
    """全仓 token 边界 grep 成员名；剥注释、保留字符串。返回 [(path, line, 片段)]。"""
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
            for ln, raw in enumerate(code.splitlines(), 1):
                if pat.search(raw):
                    snippet = raw.strip()[:80]
                    hits.append((f, ln, snippet))
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

    返回 (warns, notes)：warns 为须点名的缺口（含找不到 when 块的 INFO 留痕）；
    notes 为配对留痕（有 else 兜底 / 已有候选分支）。归属口径（静态闸拿不到接收者
    类型，拿不到就不硬判）：when 主体显式标注父类型，或所在文件文本提到父类型短名。
    """
    warns, notes = [], []
    # 预扫文件：文件路径 → 文本 / when 列表 / 是否提到父短名
    files = []
    for root in roots:
        if not os.path.isdir(root):
            continue
        for f in _kt_files(root):
            try:
                text = open(f, encoding='utf-8', errors='ignore').read()
            except OSError:
                continue
            files.append((f, text, find_whens(text)))

    for kind, name, parent in added:
        if kind not in ('sub', 'enum'):
            continue
        pshort = _parent_short(parent)
        candidates = []
        for f, text, whens in files:
            mentions = (not pshort) or (re.search(r'(?<![A-Za-z0-9_])' + re.escape(pshort) + r'(?![A-Za-z0-9_])', text) is not None)
            for w in whens:
                typed = bool(pshort) and pshort in w['subj']
                if mentions or typed:
                    candidates.append((f, w))
        if not candidates:
            warns.append(f'INFO  [WHEN-COVER] 新成员 {parent}.{name}：未找到归属父类型 {parent} 的 when 块（无 else 可核，留人工复查）')
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
    return warns, notes


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
    """pairs: [(rel_path, old_text, new_text)]。返回 (fail_lines, warn_lines, stats)。"""
    fails, warns, pairs_record = [], [], []
    removed_all, added_all = [], []
    per_file = []

    for rel, old, new in pairs:
        d = diff_decls(old, new)
        if d['removed'] or d['added']:
            per_file.append((rel, d))
        removed_all.extend((rel, *v) for v in d['removed'])
        added_all.extend((rel, *v) for v in d['added'])

    # 1) 删除面：旧名残留 grep，任一命中即红。
    for rel, kind, name, parent in removed_all:
        hits = grep_name(name, roots)
        # 排除声明文件自身在旧版本里的声明行——工作树中该声明已删，残留只看工作树现状。
        if hits:
            loc = '；'.join(f'{h[0]}:{h[1]}' for h in hits[:5])
            more = f' 等 {len(hits)} 处' if len(hits) > 5 else ''
            fails.append(f'DELETE {rel} 删掉 {kind} 成员 `{name}`（{parent}）后仍有残留引用：{loc}{more}')
        else:
            pairs_record.append(f'REF-CHECK 删除成员 `{name}`（{parent}）全仓残留 0 处')

    # 3) 改名面：同文件同类一删一增，配对记录；旧名残留已在上面 DELETE 面判过。
    for rel, d in per_file:
        by_kind_old = {}
        by_kind_new = {}
        for kind, name, parent in d['removed']:
            by_kind_old.setdefault(kind, []).append((name, parent))
        for kind, name, parent in d['added']:
            by_kind_new.setdefault(kind, []).append((name, parent))
        for kind, olds in by_kind_old.items():
            news = by_kind_new.get(kind, [])
            for old_name, old_parent in olds:
                for new_name, new_parent in news:
                    pairs_record.append(
                        f'RENAME 配对（{rel}）：`{old_name}`（{old_parent}）→ `{new_name}`（{new_parent}）')

    # 2) 新增面：when 覆盖 WARN 点名 + 配对留痕。
    added_plain = [(kind, name, parent) for _, kind, name, parent in added_all]
    add_warns, add_notes = check_added_members(added_plain, roots)
    warns.extend(add_warns)
    pairs_record.extend(add_notes)

    stats = {'removed': len(removed_all), 'added': len(added_all),
             'files': len(per_file)}
    return fails, warns, pairs_record, stats


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

    fails, warns, records, stats = evaluate(pairs, roots, audit=False)

    for w in warns:
        print(w)
    for r in records:
        print(f'INFO  {r}')
    for f in fails:
        print(f'FAIL  {f}')

    if fails:
        print(f'MEMBER-CONSUMERS: FAIL（改动面 {stats["files"]} 文件；删除/改名残留 {len(fails)} 处；'
              f'新增面 WARN {len(warns)} 条）')
        return 1
    print(f'MEMBER-CONSUMERS: PASS（改动面 {stats["files"]} 文件；删除 {stats["removed"]} / '
          f'新增 {stats["added"]}，残留 0；新增面 WARN {len(warns)} 条）')
    return 0


# ---- 自测夹具 ---------------------------------------------------------------

def _write_kt(root, rel, body):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(body)
    return p


def _run_case(files: dict) -> tuple:
    """单样本独立跑：临时目录写夹具文件，直接走生产 evaluate()，返回 (rc, fails, warns)。"""
    d = tempfile.mkdtemp(prefix='member-consumers-')
    try:
        pairs = []
        for rel, (old, new) in files.items():
            _write_kt(d, rel, new)
            pairs.append((rel, old, new))
        fails, warns, records, stats = evaluate(pairs, [d], audit=False)
        return (1 if fails else 0), fails, warns, records
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
    rc, fails, warns, _ = _run_case(bad_delete)
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
    rc, fails, warns, records = _run_case(good_delete)
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
    rc, fails, warns, records = _run_case(good_add)
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
    rc, fails, warns, _ = _run_case(bad_add)
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
    rc, fails, warns, records = _run_case(good_rename)
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
    rc, fails, _, _ = _run_case(bad_rename)
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
    rc, fails, warns, _ = _run_case(nested)
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
    rc, fails, warns, _ = _run_case(val_subj)
    if rc != 0 or len(parsed) != 1 or not parsed[0]['subj'].startswith('val x') \
            or not any('NewFoo' in w for w in warns):
        ok = False
        print(f'  SELFTEST FAIL val-subject 形态应解析并点名：rc={rc} parsed={parsed} warns={warns}')
    else:
        print('[selftest] 夹具八 when(val x = …) 内层括号解析 + 无 else 点名 OK：' + warns[0][:70])

    if not ok:
        print('SELFTEST: FAIL（存在未通过的夹具）')
        return 1
    print('[selftest] 夹具已自删，双向自测通过（删除红/绿、新增 else 放行/无 else 点名、改名配对/残留红、嵌套子类、val-subject）。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    only_changed = '--changed' in sys.argv
    audit = '--audit' in sys.argv
    sys.exit(main(only_changed=only_changed, audit=audit))
