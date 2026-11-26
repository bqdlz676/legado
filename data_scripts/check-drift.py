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
import subprocess
import re
import sys

DOC_GLOB = ['docs/*.md']
SKIP_NAMES = ('索引-', '回执补录-', '台账-', '派单', '问题清单', '组合验收矩阵',
              '分析-回执存量审计', '分析-判低理由词典', '落文-规矩总纲')
BASELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'drift-baseline.json')

# 基名解析索引覆盖的源码根：app/src 为主证据域；screenshot/src（Paparazzi 快照测试）
# 与 modules/**（rhino 等独立 Gradle 模块）是域外栈，文件实存但不属仓内 app 证据链，
# 解析得到后记 SKIP-EXTERNAL，不计入失联（2026-11-26 单 F 扩范围）。
CODE_GLOBS = ['app/src/**/*.kt', 'screenshot/src/**/*.kt', 'modules/**/*.kt']
EXTERNAL_PREFIXES = ('screenshot/', 'modules/')

# 冒号兼容半角/全角：历史报告里 `X.kt：567` 全角写法曾被正则整体漏掉，解析时归一为半角。
CODE_RE = re.compile(r'([\w./\-]+\.kt)[：:](\d+)(?:-(\d+))?')
DOC_RE = re.compile(r'((?:docs/)?[\w./\-]+\.md)[：:](\d+)')
IDENT_RE = re.compile(r'[`*]{1,2}([A-Za-z_][\w.]*)[`*]{1,2}|\b([A-Z][A-Za-z0-9_]{2,})\b')

STOP = {'Android', 'Compose', 'Kotlin', 'JVM', 'CI', 'APK', 'TODO', 'FAIL', 'PASS',
        'Ink', 'Legado', 'Sheet', 'Tab', 'OK', 'L1', 'L2', 'L3', 'P0', 'P1', 'P2', 'P3'}


_TRACKED = None


def tracked_set(root='.'):
    """git 已跟踪文件集合（一次调用，全量缓存）。
    为什么：SKIP_NAMES 是"位置型豁免"（按文件名前缀跳过），位置谁都能造——
    新建/改名一个 `索引-xxx.md` 就能免检。加"必须已被 git 跟踪"这道身份约束后，
    想借豁免必须把文件提交进仓库，留痕可审计（F-122 / 门禁 ㊼）。"""
    global _TRACKED
    if _TRACKED is None:
        r = subprocess.run(['git', '-c', 'core.quotepath=false', 'ls-files'], cwd=root,
                           capture_output=True, text=True)
        _TRACKED = {x.strip() for x in r.stdout.splitlines() if x.strip()}
    return _TRACKED


def report_files():
    out = []
    for g in DOC_GLOB:
        for f in glob.glob(g):
            base = os.path.basename(f)
            rel = os.path.relpath(f)
            if any(base.startswith(s) for s in SKIP_NAMES):
                if not rel in tracked_set():
                    out.append(f)  # 未跟踪的"豁免名"文件不享受豁免：它是新造的，必须被扫
                    continue
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


def _build_base():
    """建基名 -> 全路径索引；build/ 产物目录一律排除，避免把编译输出当源码。"""
    for g in CODE_GLOBS:
        for k in glob.glob(g, recursive=True):
            if '/build/' in k or os.sep + 'build' in k:
                continue
            _BASE.setdefault(os.path.basename(k), []).append(k)
    for k in glob.glob('docs/**/*.md', recursive=True):
        _BASE.setdefault(os.path.basename(k), []).append(k)


def is_external(path):
    """域外引用：screenshot/ 快照测试栈与 modules/ 独立 Gradle 模块，不占 app 失联基线。"""
    p = (path or '').replace(os.sep, '/')
    return p.startswith(EXTERNAL_PREFIXES) or '/screenshot/' in p or '/modules/' in p


def resolve(path):
    """报告里的锚点常写短名（`InkContentEditSheet.kt:273`），先按唯一基名解析全路径。

    批 1 实测：直接 os.path.exists 会把 6400+ 条合法锚点误报成"文件已删"。
    """
    if os.path.exists(path):
        return path
    if not _BASE:
        _build_base()
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


# 漂移记录件头标：文件仅登记已失效锚点（历史快照/重判清单），不是现行主张，
# 整份跳过扫描，避免登记的死锚点把棘轮基线虚高。头标必须落在文件头 20 行内。
SKIP_RECORD_MARKER = '> 漂移记录件：本文件仅登记已失效锚点'
# 整份已废头标：重判后结论标注已废的报告，其锚点一律记 VOID，不计失联，但仍逐条列出可审计。
VOID_FILE_MARKER = '漂移重判：原锚已失效'
# 逐条豁免关键词：锚点所在行或紧邻下一行出现「已废」，该条记 VOID。
VOID_WORD = '已废'

# 留档豁免：规则上线前交付的存量报告，文件内明写留档声明即整份跳过，
# 与 check-evidence.py / check-onboarding.py 的留档豁免同一口径（须文件内声明，可审计）。
ARCHIVE_MARKERS = ('证据三件套留档', '留档豁免')

SKIPPED_RECORD = []
SKIPPED_VOID = []
SKIPPED_ARCHIVE = []


def scan():
    files = report_files()
    allkt = None
    rows = []
    cache = {}
    skipped = []
    void_files = []
    archive_files = []
    SKIPPED_RECORD.clear()
    SKIPPED_VOID.clear()
    SKIPPED_ARCHIVE.clear()
    for f in files:
        try:
            text = open(f, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        lines = text.splitlines()
        head = '\n'.join(lines[:20])
        if SKIP_RECORD_MARKER in head:
            skipped.append(f)
            continue
        if any(m in text for m in ARCHIVE_MARKERS):
            archive_files.append(f)
            continue
        void_file = VOID_FILE_MARKER in head
        if void_file:
            void_files.append(f)

        def anchor_void(i, line):
            # 逐条豁免：所在行或紧邻下一行含「已废」；整份豁免由 void_file 兜住。
            return void_file or (VOID_WORD in line) or (i < len(lines) and VOID_WORD in lines[i])

        for i, line in enumerate(lines, 1):
            for m in CODE_RE.finditer(line):
                path, a, b = m.group(1), int(m.group(2)), m.group(3)
                real = resolve(path)
                # 域外引用（screenshot 快照测试 / modules 独立模块）：不计入失联基线。
                if is_external(path) or is_external(real):
                    rows.append((f, i, path + ':' + str(a), 'SKIP-EXTERNAL', '域外引用，不计入失联'))
                    continue
                # 畸形区间：起行大于止行（前一轮 --fix-lines 把起点改成新行号、旧止行残留，
                # 如 `567-306`）。倒挂区间不做双端核对，退回单行锚按起点行判。
                inverted = bool(b and int(b) < a)
                if inverted:
                    b = None
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
                if anchor_void(i, line):
                    st, det = 'VOID', ('报告头已废标注，整份豁免' if void_file else '行内已废标注，逐条豁免')
                elif inverted and st != 'OK':
                    det = '区间倒挂已退回单行锚；' + det
                rows.append((f, i, path + ':' + str(a), st, det))
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
                if anchor_void(i, line):
                    st, det = 'VOID', ('报告头已废标注，整份豁免' if void_file else '行内已废标注，逐条豁免')
                rows.append((f, i, '%s:%d' % (path, a), st, det))
    SKIPPED_RECORD.extend(skipped)
    SKIPPED_VOID.extend(void_files)
    SKIPPED_ARCHIVE.extend(archive_files)
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
                path, old, rend = m.group(1), m.group(2), m.group(3)
                real = resolve(path)
                if not os.path.exists(real) or is_external(real):
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
                    colon = '：' if '：' in m.group(0) else ':'
                    if rend and hits[0] > int(rend):
                        # 新起点大于旧止行：保留区间会形成倒挂，退回单行锚。
                        new_anchor = path + colon + str(hits[0])
                    elif rend:
                        new_anchor = path + colon + '%d-%s' % (hits[0], rend)
                    else:
                        new_anchor = path + colon + str(hits[0])
                    new[i] = new[i].replace(m.group(0), new_anchor, 1)
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
    for tag, store, note in (
            ('SKIP-RECORD', SKIPPED_RECORD, '漂移记录件整份跳过'),
            ('SKIP-VOID', SKIPPED_VOID, '报告头已废标注整份豁免（锚点记 VOID，不计失联）'),
            ('SKIP-ARCHIVE', SKIPPED_ARCHIVE, '留档豁免整份跳过')):
        if store:
            print('RADAR: %s 聚合 %d 份%s' % (tag, len(store), note))
            for path in store:
                print('RADAR: %s %s' % (tag, path))
    if '--fix-lines' in args:
        fix_lines()
        rows = scan()
    if '--write' in args:
        out = ['# 索引 · 结论漂移雷达（自动生成，勿手改）', '',
               '> `python3 data_scripts/check-drift.py --write` 生成。每一行是「报告:行 -> 锚点」的机检结论：',
               '> OK 符号对得上／DRIFT 行号漂了（结论需重贴锚）／VOID 已废标注豁免（报告头或行内标已废，不计失联，仍逐条列出）／MISSING 符号全文件失联（结论必须重判）／DEADFILE 文件已删。',
               '> SKIP-EXTERNAL 域外引用（screenshot/ 快照测试、modules/ 独立模块），文件实存但不属 app 证据链，不计入失联。',
               '> 不自动改行号：行号漂往往意味着代码演进、口径可能一起变，自动重贴会把旧结论伪装成新证据。',
               '']
        out.append('- 总计 %d 个锚点：OK %d ／ DRIFT %d ／ VOID %d ／ MISSING %d ／ DEADFILE %d ／ SKIP-EXTERNAL %d' % (
            len(rows), stat.get('OK', 0), stat.get('DRIFT', 0), stat.get('VOID', 0),
            stat.get('MISSING', 0), stat.get('DEADFILE', 0), stat.get('SKIP-EXTERNAL', 0)))
        out.append('')
        byf = {}
        for r in rows:
            if r[3] != 'OK' and r[3] != 'SKIP-EXTERNAL':
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
        # 通过时不回写基线：脚本不得自动下调基线；下调只留总控手动入口，
        # 且须留痕批次号与前后数值。这里只打印比对结果，基线文件保持不动。
        print('RATCHET: PASS 当前值 %d 小于等于基线 %d，基线未动' % (bad, base.get('bad', bad)))
    print('RADAR: 锚点 %d｜OK %d｜DRIFT %d｜VOID %d｜MISSING %d｜DEADFILE %d｜SKIP-EXTERNAL %d（失联 %d）' % (
        len(rows), stat.get('OK', 0), stat.get('DRIFT', 0), stat.get('VOID', 0),
        stat.get('MISSING', 0), stat.get('DEADFILE', 0), stat.get('SKIP-EXTERNAL', 0), bad))
    return 0


if __name__ == '__main__':
    sys.exit(main())
