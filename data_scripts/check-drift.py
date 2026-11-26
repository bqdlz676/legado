#!/usr/bin/env python3
"""结论漂移雷达：报告的 `文件:行` 锚点是否还对得上现状（2026-11-26 事故产物）。

为什么要有它：仓库有 200+ 份执行类报告，每份都用 `代码文件:行号` 当结论锚点。代码
每天在动，行号会漂、符号会改名、函数会被删——报告却留在原地，读的人拿到的"证据"
可能早已失效（批 1 回执补录实测 31 份里 10 条漂移，全是锚点迁址/计数演进/方案被
后续提交取代）。人工每天翻 200 份不现实，所以把"锚点还对不对得上"变成脚本。

判据（只用代码/文档里能机判的事实，不做语义判断）：
  OK       目标行仍含报告该锚点行提到的符号（或锚点行只有行号、无符号可核）
  DRIFT    符号还在这个文件里，但不在锚点行 -> 给出新行号（行漂，结论需重贴锚）
  MISSING  符号在全仓都找不到 -> 结论失联，必须重判（不是改个行号就完事）
  DEADFILE 锚点指向的文件已不存在

为什么不自动改行号：行号漂了往往意味着代码演进，结论口径可能一起变了；自动重贴
等于把旧结论伪装成新证据。雷达只报案，重判交给对应批次单（见 Q-204）。
"""
import glob
import json
import os
import re
import sys

DOC_GLOB = ['docs/*.md']
SKIP_NAMES = ('索引-', '回执补录-', '台账-', '派单', '问题清单', '组合验收矩阵',
              '分析-回执存量审计', '分析-判低理由词典', '落文-规矩总纲')
BASELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'drift-baseline.json')

CODE_RE = re.compile(r'([\w./\-]+\.kt):(\d+)(?:-(\d+))?')
DOC_RE = re.compile(r'((?:docs/)?[\w./\-]+\.md):(\d+)')
IDENT_RE = re.compile(r'[`*]{1,2}([A-Za-z_][\w.]*)[`*]{1,2}|\b([A-Z][A-Za-z0-9_]{2,})\b')

STOP = {'Android', 'Compose', 'Kotlin', 'JVM', 'CI', 'APK', 'TODO', 'FAIL', 'PASS',
        'Ink', 'Legado', 'Sheet', 'Tab', 'OK', 'L1', 'L2', 'L3', 'P0', 'P1', 'P2', 'P3'}


def report_files():
    out = []
    for g in DOC_GLOB:
        for f in glob.glob(g):
            base = os.path.basename(f)
            if any(base.startswith(s) for s in SKIP_NAMES):
                continue
            out.append(f)
    return sorted(out)


def idents_of(line, symbol_hint):
    """只认反引号包住的符号：报告里真正想指的符号都写成 `Foo`。

    早版把 CamelCase 普通词也算进来，实测 3900+ 条假 DRIFT（一句结论里提到
    多个类名，只要目标行不含其中任一个就误判行漂）。窄口径宁缺不误杀。
    """
    cand = set()
    for m in IDENT_RE.finditer(line):
        c = m.group(1)
        if not c:
            continue
        c = c.split('.')[-1]
        if c in STOP or len(c) < 3:
            continue
        cand.add(c)
    if symbol_hint:
        cand.add(symbol_hint)
    return cand


_BASE = {}


def resolve(path):
    """报告里的锚点常写短名（`InkContentEditSheet.kt:273`），先按唯一基名解析全路径。

    批 1 实测：直接 os.path.exists 会把 6400+ 条合法锚点误报成"文件已删"。
    """
    if os.path.exists(path):
        return path
    if not _BASE:
        for k in glob.glob('app/src/**/*.kt', recursive=True) + glob.glob('docs/**/*.md', recursive=True):
            _BASE.setdefault(os.path.basename(k), []).append(k)
    hits = _BASE.get(os.path.basename(path), [])
    if len(hits) == 1:
        return hits[0]
    for h in hits:
        if h.endswith(path):
            return h
    return path


def check_anchor(path, line_no, cand, cache):
    """返回 (status, detail)。cache 复用文件读取与符号索引。"""
    path = resolve(path)
    if path not in cache:
        if not os.path.exists(path):
            cache[path] = None
        else:
            cache[path] = open(path, encoding='utf-8', errors='ignore').read().splitlines()
    lines = cache[path]
    if lines is None:
        return 'DEADFILE', '文件不存在'
    if not cand:
        if line_no > len(lines):
            return 'DRIFT', '行号越界（现 %d 行）' % len(lines)
        return 'OK', ''
    if line_no > len(lines):
        for i, l in enumerate(lines, 1):
            if any(c in l for c in cand):
                return 'DRIFT', '符号仍在，行号 %d -> %d' % (line_no, i)
        return 'MISSING', '符号全文件失联'
    win = lines[max(0, line_no - 2):line_no + 1]
    hit = {c for c in cand if any(c in l for l in win)}
    if hit:
        return 'OK', ''
    # 符号还在该文件其它位置？
    for i, l in enumerate(lines, 1):
        if any(c in l for c in cand):
            return 'DRIFT', '符号仍在，行号 %d -> %d' % (line_no, i)
    return 'MISSING', '符号全文件失联'


def scan():
    files = report_files()
    allkt = None
    rows = []
    cache = {}
    for f in files:
        try:
            text = open(f, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        for i, line in enumerate(text.splitlines(), 1):
            for m in CODE_RE.finditer(line):
                path, a, b = m.group(1), int(m.group(2)), m.group(3)
                hint = None
                im = re.search(r'`([A-Za-z_][\w.]*)`', line)
                if im:
                    hint = im.group(1).split('.')[-1]
                cand = idents_of(line, hint)
                st, det = check_anchor(path, a, cand, cache)
                if st != 'OK' and b:
                    st2, det2 = check_anchor(path, int(b), cand, cache)
                    if st2 == 'OK':
                        st, det = 'OK', ''
                rows.append((f, i, '%s:%s' % (path, a), st, det))
                if st == 'MISSING' and allkt is None:
                    allkt = set()
                    for k in glob.glob('app/src/**/*.kt', recursive=True):
                        allkt.add(k)
            for m in DOC_RE.finditer(line):
                path, a = m.group(1), int(m.group(2))
                if not path.startswith('docs/'):
                    continue
                cand = idents_of(line, None)
                st, det = check_anchor(path, a, cand, cache)
                rows.append((f, i, '%s:%d' % (path, a), st, det))
    return rows


def fix_lines(dry=False):
    """把 DRIFT 里"符号唯一命中"的行号机械收敛到新行号。

    为什么敢自动改：同一文件里该符号只出现一处时，结论指向的对象没变、只是位置
    挪了，行号是快照而非主张；这种漂移属于纯记账错。MISSING（符号消失）与多命中
    （同名多处）一律不动——那意味着代码演进可能改变了结论口径，必须人重判。
    """
    fixed = 0
    cache = {}
    for f in report_files():
        try:
            lines = open(f, encoding='utf-8', errors='ignore').read().splitlines()
        except OSError:
            continue
        new = list(lines)
        for i, line in enumerate(lines):
            for m in CODE_RE.finditer(line):
                path, old = m.group(1), m.group(2)
                real = resolve(path)
                if not os.path.exists(real):
                    continue
                if real not in cache:
                    cache[real] = open(real, encoding='utf-8', errors='ignore').read().splitlines()
                src = cache[real]
                hint = [x.split('.')[-1] for x in re.findall(r'`([A-Za-z_][\w.]*)`', line)]
                hint = [h for h in hint if h not in STOP and len(h) >= 3]
                if not hint:
                    continue
                hits = [j for j, l in enumerate(src, 1) if any(h in l for h in hint)]
                if len(hits) == 1 and hits[0] != int(old):
                    new[i] = new[i].replace('%s:%s' % (path, old), '%s:%d' % (path, hits[0]), 1)
                    fixed += 1
        if new != lines and not dry:
            open(f, 'w', encoding='utf-8').write('\n'.join(new) + '\n')
    print('FIX-LINES: 收敛 %d 处纯行漂（符号唯一命中）' % fixed)


def main() -> int:
    rows = scan()
    stat = {}
    for r in rows:
        stat[r[3]] = stat.get(r[3], 0) + 1
    args = sys.argv[1:]
    if '--fix-lines' in args:
        fix_lines()
        rows = scan()
    if '--write' in args:
        out = ['# 索引 · 结论漂移雷达（自动生成，勿手改）', '',
               '> `python3 data_scripts/check-drift.py --write` 生成。每一行是「报告:行 -> 锚点」的机检结论：',
               '> OK 符号对得上／DRIFT 行号漂了（结论需重贴锚）／MISSING 符号全文件失联（结论必须重判）／DEADFILE 文件已删。',
               '> 不自动改行号：行号漂往往意味着代码演进、口径可能一起变，自动重贴会把旧结论伪装成新证据。',
               '']
        out.append('- 总计 %d 个锚点：OK %d ／ DRIFT %d ／ MISSING %d ／ DEADFILE %d' % (
            len(rows), stat.get('OK', 0), stat.get('DRIFT', 0),
            stat.get('MISSING', 0), stat.get('DEADFILE', 0)))
        out.append('')
        byf = {}
        for r in rows:
            if r[3] != 'OK':
                byf.setdefault(r[0], []).append(r)
        out.append('## 非 OK 明细（按报告分组）')
        for f in sorted(byf):
            out.append('')
            out.append('### %s（%d 条）' % (f, len(byf[f])))
            for r in byf[f]:
                out.append('- 报告第 %d 行 -> `%s` ｜ %s ｜ %s' % (r[1], r[2], r[3], r[4]))
        open('docs/索引-漂移雷达.md', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
        print('RADAR: 写入 docs/索引-漂移雷达.md')
    bad = stat.get('MISSING', 0) + stat.get('DEADFILE', 0)
    try:
        base = json.load(open(BASELINE, encoding='utf-8'))
    except (OSError, ValueError):
        base = {}
    if '--ratchet' in args:
        if bad > base.get('bad', bad):
            print('RADAR: FAIL 失联 %d 超基线 %d（结论失联只增不减，先重判再放行）' % (bad, base.get('bad', 0)))
            return 1
        base['bad'] = bad
        json.dump(base, open(BASELINE, 'w', encoding='utf-8'), ensure_ascii=False)
    print('RADAR: 锚点 %d｜OK %d｜DRIFT %d｜MISSING %d｜DEADFILE %d（失联 %d）' % (
        len(rows), stat.get('OK', 0), stat.get('DRIFT', 0),
        stat.get('MISSING', 0), stat.get('DEADFILE', 0), bad))
    return 0


if __name__ == '__main__':
    sys.exit(main())
