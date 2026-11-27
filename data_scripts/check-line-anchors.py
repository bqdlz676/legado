#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 62 文件:行 锚点漂移守卫（2026-11-27 本轮 5 份验收返工同根因产物）。

为什么需要它：本轮 8 份验收里 5 份返工项全是同一件事——报告写的 `文件:行`
锚点行号漂了（B5 任务书 D1~D8、B6 方案 D2/D7、帧层 D-1、B4 定界 D-3，真实漂移
对照见 docs/修复-漂移与符号门禁.md 第三节）。锚点漂了之后，验收代理按行号回源
回不到原句，证据链等于断了；这类错误纯文本层面合法，既有回执闸（E1/E2/E3）只数
锚点条数、不核行号是否落在真实内容上，故单独成闸。

判据（扫 `docs/**/*.md`；排除 `docs/落文-规格切片/` 原始切片、`docs/锚点/` 锚点卡、
`docs/落文-原文/` 原文转储——后两者按既有惯例不参与报告锚点判定）：

  DEADFILE（FAIL）：锚点指向的文件按仓根相对路径 / 唯一基名解析后仍不存在。
  OUTOFRANGE（FAIL）：文件存在，但行号超出该文件总行数。
  SUSPECT（WARN，不计退出码）：行号存在，但该行是空白 / 纯 `}` / 纯注释 / 分隔线——
    多半是漂移后的落点（真内容行不会长这样），列出来供责任人回修，不直接判死。

锚点形态：`path.kt:NN`、`path.md:NN`、`path.kt:NN-MM` 区间；同行或上文已点名文件
时的裸区间 `:NN` / `:NN-MM` 按最近一次完整锚点的文件归属解析。解析不到文件上下文的
裸锚点跳过（不猜归属，避免误报）。

白名单：`docs/.line-anchors-allow.txt`，制表符四列
`引用文档<TAB>锚点串<TAB>理由<TAB>登记单`。为什么用制表符而非冒号：锚点串本身含冒号，
冒号分列会把 `Foo.kt:12` 切成两半。理由列缺失即 FAIL（BADALLOW）——豁免必须写明
为什么这条锚点可以不回源，防位置型豁免后门。

退出码：0 = 无 DEADFILE / OUTOFRANGE（SUSPECT 仅 WARN）；1 = 有 FAIL 项或白名单缺理由。
用法：python3 data_scripts/check-line-anchors.py [--selftest]
"""
import os
import re
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS_DIR = 'docs'
ALLOW_REL = 'docs/.line-anchors-allow.txt'
# 按既有惯例排除：原始切片（规格来源不是报告）、锚点卡（设计件）、原文转储（逐字底稿）。
EXCLUDE_DIRS = {'落文-规格切片', '锚点', '落文-原文'}
EXTS = r'(?:kt|md|py|sh|xml|json|yml|yaml|gradle|txt)'

FULL_ANCHOR_RE = re.compile(
    r'(?P<path>[A-Za-z0-9_./\-一-龥]+\.' + EXTS + r')'
    r':(?P<start>\d+)(?:-(?P<end>\d+))?'
)
# 裸锚点：前面不是单词字符/数字（避开时间戳 12:30 与完整锚点本体）。
BARE_ANCHOR_RE = re.compile(
    r'(?<![0-9A-Za-z一-龥]):(?P<start>\d+)(?:-(?P<end>\d+))?'
)
COMMENT_LINE = re.compile(r'^(//|/\*|\*|\*/)')
HTML_COMMENT = re.compile(r'^<!--.*-->$')
DIVIDER = re.compile(r'^(-{3,}|_{3,}|\*{3,})$')

# 参与基名索引的源码/文档根（与 check-drift.py 的解析口径同族：短名先按唯一基名找全路径）。
# .github/workflows 入索引：CI 核验报告大量引用 verify.yml / package.yml 等 workflow 短名。
INDEX_ROOTS = ['app/src', 'screenshot/src', 'data_scripts', 'docs', 'modules', '.github/workflows']


def build_base_index(root: str) -> dict:
    """基名 -> 全路径列表；重名时 resolve 再按 endswith 收窄。"""
    idx: dict[str, list] = {}
    for rel in INDEX_ROOTS:
        base = os.path.join(root, rel)
        if not os.path.isdir(base):
            continue
        for dirpath, _, files in os.walk(base):
            for fn in files:
                idx.setdefault(fn, []).append(os.path.join(dirpath, fn))
    return idx


def resolve(ref: str, root: str, base_index: dict):
    """相对路径直取；取不到按唯一基名解析；重名按路径后缀收窄；都不中返回 None。"""
    cand = os.path.join(root, ref)
    if os.path.isfile(cand):
        return cand
    hits = base_index.get(os.path.basename(ref), [])
    if len(hits) == 1:
        return hits[0]
    narrowed = [h for h in hits if h.endswith(ref)]
    if len(narrowed) == 1:
        return narrowed[0]
    return None


def suspect_reason(line: str) -> str | None:
    """落点行是否像漂移残骸：空行 / 纯花括号 / 纯注释分隔线 / Markdown 分隔线。

    注意：普通注释与 KDoc 行不是 SUSPECT——锚点指向函数上方的文档注释是合法落点；
    初版把所有 `//` 开头行都报 SUSPECT，一份验收报告能刷出几十条假红，门禁会失去可信度。
    只有剥掉注释符后内容只剩分隔符（- = * _ ～）的纯分隔线才判残骸。
    """
    s = line.strip()
    if not s:
        return '空白行'
    if s in ('}', '};'):
        return '纯花括号收尾行'
    if HTML_COMMENT.match(s):
        inner = re.sub(r'^<!--|-->$', '', s).strip()
        if re.fullmatch(r'[-=*_#~·\s]+', inner):
            return 'HTML 注释分隔线'
        return None
    if DIVIDER.match(s):
        return 'Markdown 分隔线'
    if COMMENT_LINE.match(s):
        body = re.sub(r'^(//|/\*|\*|#)', '', s).strip()
        if re.fullmatch(r'[-=*_#~·\s]+', body):
            return '注释分隔线'
    return None


def load_allow(root: str, allow_rel: str):
    """读白名单。返回 (entries, malformed)；entries=[(doc, anchor, reason, ticket)]。

    四列制表符分列；理由列空 → malformed 记一条 BADALLOW（豁免不许无理由）。
    文件不存在时返回 ([], [])（首装门禁、尚无豁免是合法空集）。
    """
    path = os.path.join(root, allow_rel)
    entries, malformed = [], []
    if not os.path.isfile(path):
        return entries, malformed
    with open(path, encoding='utf-8', errors='replace') as fh:
        for lineno, raw in enumerate(fh, 1):
            line = raw.rstrip('\n')
            if not line.strip() or line.lstrip().startswith('#'):
                continue
            parts = line.split('\t')
            doc = parts[0].strip() if len(parts) > 0 else ''
            anchor = parts[1].strip() if len(parts) > 1 else ''
            reason = parts[2].strip() if len(parts) > 2 else ''
            ticket = parts[3].strip() if len(parts) > 3 else ''
            if not reason:
                malformed.append(f'{allow_rel}:{lineno} 缺理由列（豁免必须写明为什么不回源）')
                continue
            entries.append((doc, anchor, reason, ticket))
    return entries, malformed


def exempt(rel_doc: str, anchor_written: str, entries: list) -> bool:
    for doc, anchor, _reason, _ticket in entries:
        if doc == rel_doc and anchor == anchor_written:
            return True
    return False


def iter_anchors(line: str):
    """产出 (kind, path_ref, start, end, written)；full 先吃，bare 在剩余片段里找。"""
    pos = 0
    for m in FULL_ANCHOR_RE.finditer(line):
        pre = line[pos:m.start()]
        for bm in BARE_ANCHOR_RE.finditer(pre):
            yield ('bare', None, int(bm.group('start')),
                   int(bm.group('end')) if bm.group('end') else None, bm.group(0))
        yield ('full', m.group('path'), int(m.group('start')),
               int(m.group('end')) if m.group('end') else None, m.group(0))
        pos = m.end()
    for bm in BARE_ANCHOR_RE.finditer(line[pos:]):
        yield ('bare', None, int(bm.group('start')),
               int(bm.group('end')) if bm.group('end') else None, bm.group(0))


def scan_doc(rel: str, root: str, base_index: dict, entries: list) -> tuple[list, int]:
    """单份文档判定。返回 (findings, anchor_count)；
    findings=[(severity, line_no, anchor_written, target, msg)]，anchor_count=解析到的锚点总数。"""
    findings = []
    anchor_count = 0
    full = os.path.join(root, rel)
    try:
        with open(full, encoding='utf-8', errors='replace') as fh:
            lines = fh.readlines()
    except OSError:
        return findings, 0
    last_path: str | None = None
    for lineno, line in enumerate(lines, 1):
        for kind, path_ref, start, end, written in iter_anchors(line):
            if kind == 'full':
                last_path = path_ref
            else:
                path_ref = last_path  # 裸锚点归属上文最近一次完整锚点
            if path_ref is None:
                continue  # 无文件上下文的裸锚点不猜归属
            anchor_count += 1
            if exempt(rel, written, entries):
                continue
            target = resolve(path_ref, root, base_index)
            if target is None:
                findings.append(('DEADFILE', lineno, written, path_ref, '文件不存在（相对路径与唯一基名均解析不到）'))
                continue
            try:
                target_lines = open(target, encoding='utf-8', errors='replace').readlines()
            except OSError:
                findings.append(('DEADFILE', lineno, written, path_ref, '目标文件不可读'))
                continue
            total = len(target_lines)
            out_of_range = [n for n in (start, end) if n is not None and n > total]
            if out_of_range:
                findings.append(('OUTOFRANGE', lineno, written, path_ref,
                                 '行号 %s 超出文件总行数 %d' % ('/'.join(map(str, out_of_range)), total)))
                continue
            # 区间起点/终点任一像残骸即标 SUSPECT；取最重的描述。行内容取自目标文件，
            # 不能用报告自身行数组（初版误用导致把 fun 行误判成空白行）。
            suspects = []
            for n in (start, end):
                if n is None:
                    continue
                reason = suspect_reason(target_lines[n - 1] if n - 1 < len(target_lines) else '')
                if reason:
                    suspects.append(f':{n} {reason}')
            if suspects:
                findings.append(('SUSPECT', lineno, written, path_ref, '；'.join(suspects)))
    return findings, anchor_count


def collect_docs(root: str) -> list:
    docs_dir = os.path.join(root, DOCS_DIR)
    out = []
    if not os.path.isdir(docs_dir):
        return out
    for dirpath, dirs, files in os.walk(docs_dir):
        dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
        for fn in files:
            if fn.endswith('.md'):
                rel = os.path.relpath(os.path.join(dirpath, fn), root).replace(os.sep, '/')
                out.append(rel)
    return sorted(out)


def main(root: str = ROOT, allow_rel: str = ALLOW_REL) -> int:
    entries, malformed = load_allow(root, allow_rel)
    docs = collect_docs(root)
    base_index = build_base_index(root)
    dead = oob = suspect = 0
    total_anchors = 0
    all_findings: list = []
    for rel in docs:
        # 锚点总数按解析到的全部条目计（含放行的合法锚点），findings 只记命中三类问题的。
        doc_findings, doc_anchor_count = scan_doc(rel, root, base_index, entries)
        total_anchors += doc_anchor_count
        for sev, lineno, written, target, msg in doc_findings:
            all_findings.append((sev, rel, lineno, written, target, msg))
            if sev == 'DEADFILE':
                dead += 1
            elif sev == 'OUTOFRANGE':
                oob += 1
            else:
                suspect += 1
    # 白名单自身缺理由先列 FAIL，再列锚点判定结果。
    for m in malformed:
        print('BADALLOW  %s' % m)
    for sev, rel, lineno, written, target, msg in all_findings:
        print('  %s %s:%d %s → %s（%s）' % (sev, rel, lineno, written, target, msg))
    fail = dead + oob + len(malformed)
    verdict = 'FAIL' if fail else 'PASS'
    print('LINE-ANCHORS: %s（扫描 %d 份，锚点 %d 条，DEADFILE %d / OUTOFRANGE %d / SUSPECT %d）'
          % (verdict, len(docs), total_anchors, dead, oob, suspect))
    return 1 if fail else 0


def _write(root: str, rel: str, src: str) -> str:
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(src)
    return p


def selftest() -> int:
    """双向自测：合法锚点 PASS；DEADFILE / OUTOFRANGE / 白名单缺理由 rc=1；
    SUSPECT WARN rc=0；白名单带理由可豁免死锚点。夹具 tempfile 自删，判定回调生产 main()。"""
    saved_root = ROOT

    # 好样本：引用真实存在、有内容的行。
    good = tempfile.mkdtemp(prefix='line-anchors-selftest-')
    try:
        _write(good, 'src/Good.kt', 'package x\nfun good() = 1\n')
        _write(good, 'docs/报告-good.md', '见 src/Good.kt:2\n')
        globals()['ROOT'] = good
        rc = main(good)
        assert rc == 0, f"合法锚点应 PASS，rc={rc}"
        print('[selftest] 好样本（真实文件+内容行）PASS 通路 OK')
    finally:
        globals()['ROOT'] = saved_root
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一 DEADFILE：文件不存在 → rc=1。
    bad_dead = tempfile.mkdtemp(prefix='line-anchors-selftest-')
    try:
        _write(bad_dead, 'docs/报告-dead.md', '见 app/NoSuch.kt:5\n')
        rc = main(bad_dead)
        assert rc == 1, f"死文件锚点应 FAIL，rc={rc}"
        print('[selftest] 坏样本一（DEADFILE 文件不存在）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad_dead, ignore_errors=True)

    # 坏样本二 OUTOFRANGE：文件仅 2 行，锚点 :99 → rc=1。
    bad_oob = tempfile.mkdtemp(prefix='line-anchors-selftest-')
    try:
        _write(bad_oob, 'src/Short.kt', 'package x\nfun short() = 2\n')
        _write(bad_oob, 'docs/报告-oob.md', '见 src/Short.kt:99\n')
        rc = main(bad_oob)
        assert rc == 1, f"越界锚点应 FAIL，rc={rc}"
        print('[selftest] 坏样本二（OUTOFRANGE 行号越界）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad_oob, ignore_errors=True)

    # WARN 通路：锚点落在空行 / 纯 `}` 行 → SUSPECT，rc=0。
    warn = tempfile.mkdtemp(prefix='line-anchors-selftest-')
    try:
        _write(warn, 'src/Blank.kt', '\n}\n')
        _write(warn, 'docs/报告-warn.md', '见 src/Blank.kt:1 与 src/Blank.kt:2\n')
        rc = main(warn)
        assert rc == 0, f"SUSPECT 仅 WARN 应 rc=0，rc={rc}"
        print('[selftest] WARN 通路（空行/纯花括号落点）提示通路 OK')
    finally:
        shutil.rmtree(warn, ignore_errors=True)

    # 坏样本三 BADALLOW：白名单行缺理由列 → rc=1。
    bad_allow = tempfile.mkdtemp(prefix='line-anchors-selftest-')
    try:
        _write(bad_allow, 'docs/报告-allow.md', '见 app/Whatever.kt:1\n')
        _write(bad_allow, ALLOW_REL, 'docs/报告-allow.md\tapp/Whatever.kt:1\t\tQ-366\n')
        rc = main(bad_allow)
        assert rc == 1, f"白名单缺理由应 FAIL，rc={rc}"
        print('[selftest] 坏样本三（白名单缺理由）BADALLOW 通路 OK')
    finally:
        shutil.rmtree(bad_allow, ignore_errors=True)

    # 豁免通路：白名单带理由 + 登记单，死锚点被放行 → rc=0。
    ex = tempfile.mkdtemp(prefix='line-anchors-selftest-')
    try:
        _write(ex, 'docs/报告-exempt.md', '见 app/Gone.kt:7\n')
        _write(ex, ALLOW_REL,
                'docs/报告-exempt.md\tapp/Gone.kt:7\t历史留档引用，源码已随旧栈删除\tQ-366\n')
        rc = main(ex)
        assert rc == 0, f"带理由豁免应放行，rc={rc}"
        print('[selftest] 豁免通路（带理由+登记单的白名单条目）放行 OK')
    finally:
        shutil.rmtree(ex, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main())
