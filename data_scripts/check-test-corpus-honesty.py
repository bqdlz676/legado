#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""g67 语料断言诚实性闸：拦住「把语料/种子数据现状当成代码层固定事实」的单测断言。

事故下限（2026-11-27 CI run 45946032048，HEAD 259482c2，shard 3/4 红）：
`InkBubbleRouteResolverTest` 两条用例把「本地词库恰好收录 / 未收录某词」当成固定
事实断言：`resolve("春秋", 99)` 期望 kind=MISSING、token=`秋`（实际词槽截胡整词，
kind=WORD、token=`春秋`）；`resolve("魑魅魍魉", 0)` 期望 token=`魑魅魍魉`（实际兜底
回守首字 `魑`）。断言期望值随 `WORD_TSV` / `CHAR_TSV` 种子漂移，代码层真正稳定的
不变量只有「不崩、不空白、结构完整、越界先夹末位再按正常链解析」。修法见
`docs/CI核验-InkBubbleRouteResolverTest修红.md`，修红提交 558273f：样例换成不在
汉字区间的 `※`（U+203B），夹取用例改为两次调用结果的一致性对比。

既有 g64 只管「空断言 / 整文件 contains / 反射取成员」，管不到「断言期望值来自语料
数据」这一类；本闸把该形态静态拦下。

四类判据（每条可单条命令判真假）：

1) LITERAL-ENTRY：断言里直接出现语料种子表的字面条目。期望值（assertEquals 首参或
   断言体内的字面量）是汉字串，且精确命中种子词目 / 字头 / 典故词目 / 人物名或别名 /
   地名或别名 / 名物名或别名。种子表以仓内 `DictSeedData.kt` 的 CHAR_TSV / WORD_TSV
   为主，其余内存种子（AllusionSeed / PersonDirectory / PlaceSeedData /
   ArtifactSeedTable）的名字段一并入表。
2) LITERAL-TSV：断言字面量是制表符分隔的语料行形态（字头 \\t 拼音 \\t 部首 \\t 笔画，
   或词目 \\t 训释 \\t 标签），即把 TSV 行整行搬进断言。
3) API-EXPECTED：断言期望值取自语料查询 API 的返回值再与之比较。直接形态：比较型
   断言体内出现 `ChineseDict.wordAt(...)` / `getChar(...)` / `getShuowen(...)` /
   `getUsage(...)` / `counts()` / `queryToken(...)` / `.shu(...)` / `.collate(...)`
   之类调用；绑定形态：`val expected = <语料 API 调用>` 后该绑定名进了比较型断言。
4) CORPUS-WORDING：断言带「某词必须命中 / 必须不命中 / 应在种子内 / 未收录」之类
   数据依赖字样，且同文件引用了语料常量或语料 API。

放行（不得误伤，各有独立夹具打 SKIP 标签计数）：

- SKIP:NONHAN：字面量不在汉字区间（`※` U+203B、ASCII、标点、数字、表情等），机制上
  不可能进任何汉字种子索引；
- SKIP:ECHO：该字面量同时是同一用例里被测函数调用的入参（输入回显，断言的是透传
  一致性，不是种子收录现状）；
- SKIP:SELF：断言两边都是被测函数自身输出的派生表达式（如
  `assertEquals(atLast.token, clamped.token)`），无字面量参与；
- SKIP:STRUCT：非空 / 非崩 / 不空白 / 列表空这类结构完整性断言（assertNotNull、
  assertTrue(body.isNotBlank()) 等）；
- SKIP:INVARIANT：纯函数不变量断言（数值上下界、长度区间、下标夹取范围等），与种子
  收录与否无关。

口径：默认 `--changed` 只判相对 `origin/main` 的改动面（历史存量不判红）；`--audit`
全仓只登记计数与清单，不从 0 变红墙。空集（无待检测试文件）报 EMPTY，退出码 2，
不表述为 PASS（同 ㊾ / check-triple-anchor / check-report-fields 空集口径）。
退出码：0 = 改动面无命中；1 = 有红；2 = 空集。
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEST_ROOTS = ['app/src/test', 'screenshot/src/test']

# ---- 语料种子事实源（仓内内存种子；assets 下 txt/bin 不进 JVM 单测链路，不入表）----
SEED_FILES = {
    'dict': 'app/src/main/java/io/legado/app/help/dict/DictSeedData.kt',
    'allusion': 'app/src/main/java/io/legado/app/help/allusion/AllusionSeed.kt',
    'person': 'app/src/main/java/io/legado/app/help/person/PersonDirectory.kt',
    'place': 'app/src/main/java/io/legado/app/help/place/PlaceSeedData.kt',
    'artifact': 'app/src/main/java/io/legado/app/help/artifact/seed/ArtifactSeedTable.kt',
}

# 语料查询 API 调用名（仓内真实存在的词槽 / 字槽入口；shu/collate 为泛化名预留）。
API_CALL_RE = re.compile(
    r'(ChineseDict\s*\.\s*(?:wordAt|getChar|getShuowen|getUsage|getPinyin|'
    r'wordsStartingWith|pinyinOfWord|counts)'
    r'|\b(?:wordAt|getChar|getShuowen|getUsage|counts|queryToken)\s*\('
    r'|\.\s*(?:shu|collate)\s*\()'
)

# 「必须命中 / 必须不命中」数据依赖字样（出现在断言消息或断言体内）。
WORDING_RE = re.compile(
    r'必须在种子|应命中|未收录|不应命中|必须收录|种子内|未收|应收录|必须在词库|'
    r'未在种子|不在种子|未被收录|seeded|未收录字'
)

# 同文件引用语料常量 / API（C3 的第二条件）。
CORPUS_REF_RE = re.compile(
    r'ChineseDict|DictSeedData|WORD_TSV|CHAR_TSV|AllusionSeed|PersonDirectory|'
    r'PlaceSeedData|ArtifactSeedTable|queryToken|\bwordAt\b|\bgetChar\b|'
    r'\bgetShuowen\b|\bgetUsage\b|\bseed\b'
)

ASSERT_NAMES = ('assertEquals', 'assertNotEquals', 'assertTrue', 'assertFalse',
                'assertNull', 'assertNotNull', 'assertThat', 'assertArrayEquals')
ASSERT_RE = re.compile(r'\b(' + '|'.join(ASSERT_NAMES) + r')\s*\(')
# 比较型断言：API 返回值进这类断言才算「取返回值当 expected 再比较」。
COMPARE_RE = re.compile(r'\b(?:assertEquals|assertNotEquals|assertThat)\s*\(')

FUN_RE = re.compile(r'\bfun\s+(?:`[^`]+`|[A-Za-z_]\w*)\s*\(')

HAN_CHAR_RE = re.compile(r'[㐀-䶿一-鿿豈-﫿]')
STRING_RE = re.compile(r'"((?:\\.|[^"\\])*)"')
CHAR_RE = re.compile(r"'((?:\\.|[^'\\])*)'")

# 结构完整性断言体内允许的形态：非空 / 非空白 / 列表空 等，与种子收录无关。
STRUCT_BODY_RE = re.compile(
    r'(?:isNotBlank|isBlank|isNotEmpty|isEmpty|nonNull|isNull|isNotNull)\s*\('
)


def strip_comments(text: str) -> str:
    """剥 // 行注释与 /* */ 块注释；字符串（双引号 / 字符 / 三引号）原样保留。

    为什么保留字符串：断言消息里写「未收录字」只是被断言的文案，不是真判据；注释面
    剥掉后再判代码面，口径同 g64/g66，避免两套注释解析出两套行号。
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
                state = None
                i += 2
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


def line_of(text: str, idx: int) -> int:
    return text.count('\n', 0, idx) + 1


def han_count(s: str) -> int:
    return len(HAN_CHAR_RE.findall(s))


def is_han_literal(s: str) -> bool:
    """汉字串判定：至少含 1 个汉字，且汉字占比不低于一半（防「《孟子·离娄下》」整串误判）。"""
    h = han_count(s)
    return h >= 1 and h * 2 >= len(s)


def unescape(s: str) -> str:
    """把源码字面量里的 \\t \\n \\\\ 等转义还原成实义字符（供 TSV 形态判定）。"""
    return (s.replace('\\t', '\t').replace('\\n', '\n')
             .replace('\\"', '"').replace("\\'", "'").replace('\\\\', '\\'))


def parse_dict_seeds(text: str) -> tuple:
    """从 DictSeedData.kt 抽 CHAR_TSV / WORD_TSV 的字面条目。

    返回 (heads, pinyins, radicals)：heads 收字头与词目；pinyins / radicals 供 TSV
    行形态与字段比对使用。TSV 行按 \\t 分列，缺项留空跳过空字段。
    """
    heads, pinyins, radicals = set(), set(), set()
    for tsv_name in ('CHAR_TSV', 'WORD_TSV'):
        m = re.search(re.escape(tsv_name) + r'\s*:\s*String\s*=\s*"""', text)
        if not m:
            continue
        rest = text[m.end():]
        close = rest.find('"""')
        block = rest[:close] if close >= 0 else rest
        for row in block.splitlines():
            row = row.strip()
            if not row:
                continue
            cols = row.split('\t')
            if not cols or not cols[0].strip():
                continue
            head = cols[0].strip()
            if is_han_literal(head):
                heads.add(head)
            if tsv_name == 'CHAR_TSV':
                if len(cols) > 1 and cols[1].strip():
                    pinyins.add(cols[1].strip())
                if len(cols) > 2 and cols[2].strip():
                    radicals.add(cols[2].strip())
    return heads, pinyins, radicals


def parse_other_seeds(text: str) -> set:
    """从典故 / 人物 / 地名 / 名物种子抽名字段字面量（word/name/canonicalName/preferred/
    courtesy/style/aliases）。只收汉字串，长度 1~5，整串精确匹配。"""
    heads = set()
    field_re = re.compile(
        r'(?:word|name|canonicalName|preferred|courtesy|style)\s*=\s*"([^"]+)"')
    for m in field_re.finditer(text):
        val = m.group(1).strip()
        if is_han_literal(val) and 1 <= len(val) <= 5:
            heads.add(val)
    for m in re.finditer(r'aliases\s*=\s*listOf\(([^)]*)\)', text):
        for lit in STRING_RE.finditer(m.group(1)):
            val = lit.group(1).strip()
            if is_han_literal(val) and 1 <= len(val) <= 5:
                heads.add(val)
    return heads


def load_corpus() -> dict:
    """读仓内种子文件建语料字面量表；种子文件缺失时降级为空表（不误报、不崩）。"""
    heads, pinyins, radicals = set(), set(), set()
    dict_path = os.path.join(ROOT, SEED_FILES['dict'])
    if os.path.isfile(dict_path):
        heads, pinyins, radicals = parse_dict_seeds(
            open(dict_path, encoding='utf-8', errors='ignore').read())
    for key in ('allusion', 'person', 'place', 'artifact'):
        p = os.path.join(ROOT, SEED_FILES[key])
        if os.path.isfile(p):
            heads |= parse_other_seeds(
                open(p, encoding='utf-8', errors='ignore').read())
    return {'heads': heads, 'pinyins': pinyins, 'radicals': radicals}


def _balanced_paren(code: str, start: int) -> str:
    """从 code[start] 的 `(` 起字符串感知配平，返回断言实参截面（不含最外层括号）。"""
    depth, state, j = 0, None, start
    n = len(code)
    while j < n:
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
                    return code[start + 1:j]
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
    return code[start + 1:]


def _literals_in(body: str) -> list:
    """截面上的字符串 / 字符字面量，按出现位置排序返回 [(值, 是否字符串)]。"""
    out = []
    for m in STRING_RE.finditer(body):
        out.append((m.start(), m.group(1), True))
    for m in CHAR_RE.finditer(body):
        out.append((m.start(), m.group(1), False))
    out.sort(key=lambda x: x[0])
    return [(v, is_str) for _, v, is_str in out]


def _method_spans(code: str) -> list:
    """按 fun 头切方法体；返回 [(header_idx, body_text, body_start)]。括号字符串感知配平。"""
    spans = []
    for m in FUN_RE.finditer(code):
        brace = code.find('{', m.end())
        if brace < 0:
            continue
        depth, state, j = 0, None, brace
        n = len(code)
        while j < n:
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
                if c == '{':
                    depth += 1
                elif c == '}':
                    depth -= 1
                    if depth == 0:
                        spans.append((m.start(), code[brace + 1:j], brace + 1))
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
    return spans


def analyze_file(path: str, corpus: dict) -> tuple:
    """单文件判定，返回 (issues, skip_counts)。

    issue: (kind, line, message)；skip_counts: SKIP 标签 → 次数，放行可审计。
    """
    raw = open(path, encoding='utf-8', errors='ignore').read()
    code = strip_comments(raw)
    heads = corpus['heads']
    issues = []
    skips = {'NONHAN': 0, 'ECHO': 0, 'SELF': 0, 'STRUCT': 0, 'INVARIANT': 0}

    file_has_corpus_ref = bool(CORPUS_REF_RE.search(code))

    # 绑定形态：val expected = <语料 API 调用>，记录绑定名 → 声明行。
    bindings = {}
    for m in re.finditer(r'\b(?:val|var)\s+(\w+)\s*=\s*([^\n]*)', code):
        name, rhs = m.group(1), m.group(2)
        if API_CALL_RE.search(rhs):
            bindings[name] = line_of(code, m.start())

    for _, body, body_start in _method_spans(code):
        # 用例内输入字面量集合：断言截面之外的字面量视为被测函数入参（回显放行依据）。
        echo_literals = set()
        assertion_spans = []
        for am in ASSERT_RE.finditer(body):
            paren = body.find('(', am.end() - 1)
            if paren < 0:
                continue
            seg = _balanced_paren(body, paren)
            assertion_spans.append((am.start(), am.group(1), seg))
        # 非断言区字面量收集（输入回显）。
        masked = body
        for _, _, seg in assertion_spans:
            masked = masked.replace(seg, ' ' * len(seg))
        for lit, _ in _literals_in(masked):
            echo_literals.add(lit)

        for a_start, name, seg in assertion_spans:
            abs_line = line_of(code, body_start + a_start)
            is_compare = bool(COMPARE_RE.match(name + '('))

            # 结构完整性断言：assertNotNull/assertNull 且体内无汉字字面量 → 放行。
            if name in ('assertNotNull', 'assertNull') and not any(
                    is_han_literal(v) for v, _ in _literals_in(seg)):
                skips['STRUCT'] += 1
                continue
            # 非空 / 不空白 / 列表空形态 → 结构放行。
            if name in ('assertTrue', 'assertFalse') and STRUCT_BODY_RE.search(seg) \
                    and not any(is_han_literal(v) for v, _ in _literals_in(seg)):
                skips['STRUCT'] += 1
                continue

            lits = _literals_in(seg)
            # 自洽对比：比较型断言两边都无字面量（两边都是输出派生表达式）。
            if is_compare and not lits:
                # 但若是 API 绑定名进比较，仍归 API-EXPECTED。
                if not any(b in bindings and re.search(r'\b' + re.escape(b) + r'\b', seg)
                           for b in bindings):
                    skips['SELF'] += 1
                    continue

            hit_kinds = []
            for val, is_str in lits:
                plain = val.strip()
                if not plain:
                    continue
                if not is_han_literal(plain):
                    # 非汉字符号（※/ASCII/标点/数字）机制上不可能进汉字种子，放行。
                    skips['NONHAN'] += 1
                    continue
                if plain in echo_literals:
                    # 输入回显：断言的是入参透传一致性，不是种子收录现状。
                    skips['ECHO'] += 1
                    continue
                # TSV 行形态：还原转义后含真实制表符且 ≥3 列、首列汉字。
                real = unescape(val)
                if '\t' in real and len(real.split('\t')) >= 3 \
                        and is_han_literal(real.split('\t')[0]):
                    hit_kinds.append(('LITERAL-TSV',
                                      f'断言字面量是语料 TSV 行形态：`{plain[:40]}`'))
                    continue
                if plain in heads:
                    hit_kinds.append(('LITERAL-ENTRY',
                                      f'断言期望值命中种子字面条目 `{plain}`（词目/字头/专名），'
                                      f'该值随种子数据漂移，不得当成代码层固定事实'))
                    continue
                # 汉字串既不在表内、也非结构断言：纯不变量（长度/上下界）放行计数。
                skips['INVARIANT'] += 1

            # API-EXPECTED：比较型断言体内直接出现语料 API 调用。
            if is_compare and API_CALL_RE.search(seg):
                hit_kinds.append(('API-EXPECTED',
                                  '比较型断言直接拿语料查询 API 返回值参与比较；'
                                  '期望值应来自纯函数不变量，不得取种子查询结果'))
            # 绑定形态：API 绑定名进了比较型断言。
            for b, decl_line in bindings.items():
                if is_compare and re.search(r'\b' + re.escape(b) + r'\b', seg):
                    hit_kinds.append(('API-EXPECTED',
                                      f'绑定 `{b}`（第 {decl_line} 行，取自语料 API 返回）'
                                      f'进入比较型断言当期望值'))

            # CORPUS-WORDING：数据依赖字样 + 同文件引用语料常量。
            if file_has_corpus_ref and WORDING_RE.search(seg):
                hit_kinds.append(('CORPUS-WORDING',
                                  '断言带「必须命中/未收录」类数据依赖字样，且同文件引用语料常量；'
                                  '收录现状随种子漂移，应改成非汉字样例或一致性对比'))

            for kind, msg in hit_kinds:
                issues.append((kind, abs_line, msg))

    return issues, skips


def changed_files() -> list:
    """相对 origin/main 改动面的测试文件：git diff + 未跟踪 .kt（口径同 g65/g66）。"""
    names = []
    try:
        out = subprocess.run(
            ['git', '-c', 'core.quotepath=false', 'diff', '--name-only', 'origin/main'],
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
    keep = []
    for n in names:
        if not n.endswith('.kt'):
            continue
        if any(n.startswith(r + '/') for r in TEST_ROOTS) and os.path.isfile(os.path.join(ROOT, n)):
            keep.append(n)
    return keep


def collect_all() -> list:
    files = []
    for root in TEST_ROOTS:
        base = os.path.join(ROOT, root)
        if not os.path.isdir(base):
            continue
        for dirpath, _, names in os.walk(base):
            for nm in sorted(names):
                if nm.endswith('.kt'):
                    files.append(os.path.join(dirpath, nm))
    return sorted(files)


def main(only_changed: bool = False, audit: bool = False,
         files: list | None = None) -> int:
    corpus = load_corpus()
    if files is None:
        files = changed_files() if only_changed else collect_all()
    if not files:
        # 空集不判 PASS（㊾）：没检出待检测试文件＝没有可核验对象，不是「合规」。
        print('EMPTY: 未检出待检测试文件（空集不判 PASS；--changed 口径下本轮无测试改动）')
        return 2

    all_issues = []
    total_skips = {}
    for f in files:
        rel = os.path.relpath(f, ROOT) if os.path.isabs(f) else f
        issues, skips = analyze_file(f, corpus)
        for kind, line, msg in issues:
            all_issues.append((rel, kind, line, msg))
        for k, v in skips.items():
            total_skips[k] = total_skips.get(k, 0) + v

    skip_txt = ' / '.join(f'SKIP:{k}={v}' for k, v in sorted(total_skips.items()) if v)

    if audit:
        print(f'TEST-CORPUS-HONESTY AUDIT: 全仓清单（测试文件 {len(files)} 个；'
              f'候选命中 {len(all_issues)} 处；种子条目 {len(corpus["heads"])} 条；'
              f'存量只登记不判红）')
        for rel, kind, line, msg in all_issues[:20]:
            print(f'  INFO  [AUDIT] {rel}:{line} [{kind}] {msg}')
        return 0

    if all_issues:
        for rel, kind, line, msg in all_issues:
            print(f'FAIL {rel}:{line} [{kind}] {msg}')
        print(f'TEST-CORPUS-HONESTY: FAIL（改动面 {len(files)} 个测试文件；'
              f'命中 {len(all_issues)} 处；放行 {skip_txt or "无"}）')
        return 1
    print(f'TEST-CORPUS-HONESTY: PASS（扫描 {len(files)} 个测试文件，'
          f'LITERAL-ENTRY/LITERAL-TSV/API-EXPECTED/CORPUS-WORDING 0 命中；'
          f'放行 {skip_txt or "无"}）')
    return 0


# ---- 自测夹具 ----------------------------------------------------------------

def _write_kt(root, rel, body):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(body)
    return p


def _run_case(bodies: dict) -> tuple:
    """单组夹具独立跑：临时目录写夹具，直接走生产 analyze_file，返回 (rc, issues, skips)。"""
    d = tempfile.mkdtemp(prefix='corpus-honesty-')
    try:
        corpus = load_corpus()
        issues, skips = [], {}
        for rel, body in bodies.items():
            p = _write_kt(d, rel, body)
            f_issues, f_skips = analyze_file(p, corpus)
            issues.extend((rel,) + i for i in f_issues)
            for k, v in f_skips.items():
                skips[k] = skips.get(k, 0) + v
        rc = 1 if issues else 0
        return rc, issues, skips
    finally:
        shutil.rmtree(d, ignore_errors=True)


def selftest() -> int:
    ok = True

    # 坏样本一（事故镜像，259482c 原形态）：resolve("春秋", 99) 期望 MISSING / token 秋。
    # 「秋」在 CHAR_TSV 字头表内，且不是断言外的输入字面量 → 必须 LITERAL-ENTRY 红。
    bad_mirror = {
        'app/src/test/ink/BadMirrorTest.kt': (
            'package ink\n'
            'class BadMirrorTest {\n'
            '    @Test fun `春秋越界期望 Missing 兜底`() {\n'
            '        val bubble = BubbleResolver.resolve("春秋", 99)\n'
            '        assertEquals(BubbleKind.MISSING, bubble!!.kind)\n'
            '        assertEquals("秋", bubble.token)\n'
            '    }\n}\n'),
    }
    rc, issues, _ = _run_case(bad_mirror)
    kinds = [i[1] for i in issues]
    if rc != 1 or 'LITERAL-ENTRY' not in kinds:
        ok = False
        print(f'  SELFTEST FAIL 事故镜像应 LITERAL-ENTRY 红：rc={rc} issues={issues}')
    else:
        print(f'[selftest] 坏样本一 事故镜像 → 独立 FAIL OK：{issues[0][1]} '
              f'{issues[0][2]} 行 `{issues[0][3][:30]}`')

    # 坏样本二（API-EXPECTED 直接形态）：比较型断言体内直接调 wordAt 当 expected。
    bad_api = {
        'app/src/test/dict/BadApiTest.kt': (
            'package dict\n'
            'class BadApiTest {\n'
            '    @Test fun `拿种子查询结果当期望`() {\n'
            '        val hit = ChineseDict.wordAt(text, 0)\n'
            '        assertEquals(ChineseDict.wordAt(text, 0).word, hit!!.word)\n'
            '    }\n}\n'),
    }
    rc, issues, _ = _run_case(bad_api)
    if rc != 1 or not any(i[1] == 'API-EXPECTED' for i in issues):
        ok = False
        print(f'  SELFTEST FAIL API 直接形态应红：rc={rc} issues={issues}')
    else:
        print('[selftest] 坏样本二 API 返回值直接比较 → API-EXPECTED FAIL OK')

    # 坏样本三（API-EXPECTED 绑定形态）：val expected = 语料 API 调用，绑定名进断言。
    bad_bind = {
        'app/src/test/dict/BadBindTest.kt': (
            'package dict\n'
            'class BadBindTest {\n'
            '    @Test fun `绑定种子结果当期望`() {\n'
            '        val expected = ChineseDict.getChar("天")\n'
            '        val actual = ChineseDict.getChar("天")\n'
            '        assertEquals(expected, actual)\n'
            '    }\n}\n'),
    }
    rc, issues, _ = _run_case(bad_bind)
    if rc != 1 or not any(i[1] == 'API-EXPECTED' for i in issues):
        ok = False
        print(f'  SELFTEST FAIL API 绑定形态应红：rc={rc} issues={issues}')
    else:
        print('[selftest] 坏样本三 API 绑定形态 → API-EXPECTED FAIL OK')

    # 坏样本四（CORPUS-WORDING）：「应在种子内」字样 + 文件引用 ChineseDict 常量。
    bad_word = {
        'app/src/test/dict/BadWordTest.kt': (
            'package dict\n'
            'class BadWordTest {\n'
            '    @Test fun `抽查字应在种子内`() {\n'
            '        val entry = ChineseDict.getChar("秋")\n'
            '        assertNotNull("抽查字 秋 应在种子内", entry)\n'
            '    }\n}\n'),
    }
    rc, issues, _ = _run_case(bad_word)
    if rc != 1 or not any(i[1] == 'CORPUS-WORDING' for i in issues):
        ok = False
        print(f'  SELFTEST FAIL 数据依赖字样应红：rc={rc} issues={issues}')
    else:
        print('[selftest] 坏样本四 必须收录字样 + 语料引用 → CORPUS-WORDING FAIL OK')

    # 放行样本一（※ 非汉字符号）：机制上不可能进汉字种子，不得假红。
    good_nohan = {
        'app/src/test/ink/GoodNohanTest.kt': (
            'package ink\n'
            'class GoodNohanTest {\n'
            '    @Test fun `未知符号兜底`() {\n'
            '        val bubble = BubbleResolver.resolve("※", 0)\n'
            '        assertNotNull(bubble)\n'
            '        assertEquals("※", bubble.token)\n'
            '    }\n}\n'),
    }
    rc, issues, skips = _run_case(good_nohan)
    if rc != 0 or issues or skips.get('NONHAN', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL ※ 样本应 PASS 且记 NONHAN：rc={rc} issues={issues} skips={skips}')
    else:
        print('[selftest] 放行样本一 ※ 非汉字符号 → PASS OK（SKIP:NONHAN）')

    # 放行样本二（夹取一致性）：两次调用输出互比，无字面量参与 → SKIP:SELF。
    good_self = {
        'app/src/test/ink/GoodSelfTest.kt': (
            'package ink\n'
            'class GoodSelfTest {\n'
            '    @Test fun `越界夹取与原位一致`() {\n'
            '        val paragraph = "春秋"\n'
            '        val clamped = BubbleResolver.resolve(paragraph, 99)\n'
            '        val atLast = BubbleResolver.resolve(paragraph, paragraph.lastIndex)\n'
            '        assertEquals(atLast!!.kind, clamped!!.kind)\n'
            '        assertEquals(atLast.token, clamped.token)\n'
            '    }\n}\n'),
    }
    rc, issues, skips = _run_case(good_self)
    if rc != 0 or issues or skips.get('SELF', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL 一致性对比应 PASS：rc={rc} issues={issues} skips={skips}')
    else:
        print('[selftest] 放行样本二 夹取一致性对比 → PASS OK（SKIP:SELF）')

    # 放行样本三（结构完整性）：非空 / 不空白 / 列表空，与种子收录无关。
    good_struct = {
        'app/src/test/ink/GoodStructTest.kt': (
            'package ink\n'
            'class GoodStructTest {\n'
            '    @Test fun `兜底结构完整`() {\n'
            '        val bubble = BubbleResolver.resolve("   ", 0)\n'
            '        assertNotNull(bubble)\n'
            '        assertTrue(bubble!!.body.isNotBlank())\n'
            '        assertTrue(bubble.actions.isEmpty())\n'
            '    }\n}\n'),
    }
    rc, issues, skips = _run_case(good_struct)
    if rc != 0 or issues or skips.get('STRUCT', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL 结构断言应 PASS：rc={rc} issues={issues} skips={skips}')
    else:
        print('[selftest] 放行样本三 非空/不空白/列表空 → PASS OK（SKIP:STRUCT）')

    # 放行样本四（输入回显）：同一字面量既是入参又是断言期望，断言的是透传一致性。
    good_echo = {
        'app/src/test/ink/GoodEchoTest.kt': (
            'package ink\n'
            'class GoodEchoTest {\n'
            '    @Test fun `人名透传回显`() {\n'
            '        val route = InkActionDispatcher.routeContract(\n'
            '            ContractAction.OpenPerson("孔子"), ContextAnchor)\n'
            '        assertEquals("孔子", route.name)\n'
            '    }\n}\n'),
    }
    rc, issues, skips = _run_case(good_echo)
    if rc != 0 or issues or skips.get('ECHO', 0) < 1:
        ok = False
        print(f'  SELFTEST FAIL 回显样本应 PASS：rc={rc} issues={issues} skips={skips}')
    else:
        print('[selftest] 放行样本四 输入回显 → PASS OK（SKIP:ECHO）')

    # 空集边界：临时空目录无 .kt → EMPTY rc=2，不判 PASS。
    d = tempfile.mkdtemp(prefix='corpus-honesty-empty-')
    try:
        saved_root = ROOT
        globals()['ROOT'] = d
        rc_empty = main(files=[])
        if rc_empty != 2:
            ok = False
            print(f'  SELFTEST FAIL 空集应 rc=2，实得 {rc_empty}')
        else:
            print('[selftest] 空集边界 → EMPTY rc=2 OK（不判 PASS）')
        globals()['ROOT'] = saved_root
    finally:
        shutil.rmtree(d, ignore_errors=True)

    if not ok:
        print('SELFTEST: FAIL（存在未通过的夹具）')
        return 1
    print('[selftest] 夹具已自删，双向自测通过（事故镜像/API 直接/API 绑定/数据依赖字样四红；'
          'NONHAN/SELF/STRUCT/ECHO 四类放行；空集 EMPTY rc=2）。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    audit = '--audit' in sys.argv
    # 默认口径 = --changed（只判改动面，存量不判红）；显式 --audit 才走全仓清单。
    only_changed = (not audit) or ('--changed' in sys.argv)
    sys.exit(main(only_changed=only_changed, audit=audit))
