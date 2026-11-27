#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""机制层 RAG 索引器（切片 → 落 FTS5）。规格：docs/机制-RAG检索层.md（R1–R8）。

为什么用 FTS5 而不是向量库：本层的硬要求是「每条结论都能回到 文件:行 的原文」（R1）。
向量只回答「意思像不像」，不回答「原文在哪」，把疑似相关当已核实就是造假；
且全仓已有离线符号索引 data_scripts/dep-symbols.json，再引入模型/显存是纯负担。

切片口径（规格 §二）：按 Markdown 标题层级切小节；单节超过 500 字二次切分，
相邻块保留 50 字重叠——目的只有一个：不许把一条判据切成两半（R2）。

行号/偏移口径（本版修复点）：每块同时存字符偏移 off_start/off_end 与行号区间。
行号给人看，偏移给 --verify 做逐字比对（R6）；只存行号时，跨行进切的块末行是
截断行，整行回读必然对不上，曾导致 20 抽 12 块 FAIL。

用法：
  python3 data_scripts/rag-index.py --stats      # 只统计已建索引
  python3 data_scripts/rag-index.py --verify     # 抽样回读原文逐字比对
  python3 data_scripts/rag-index.py --rebuild    # 整库重建
  python3 data_scripts/rag-index.py              # 增量更新（按 mtime+sha）
"""
import argparse
import bisect
import hashlib
import random
import json
import os
import re
import sqlite3
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE_DIR = os.path.join(ROOT, 'data_scripts', '_cache')
DB_REL = 'data_scripts/_cache/rag-index.sqlite'

CHUNK_CHARS = 500      # 规格 §二：500 字二次切分
OVERLAP_CHARS = 50     # 规格 §二：相邻块 50 字重叠

DOCS_DIR = 'docs'
EXTRA_DIRS = ('data_scripts', 'app/src/main/java/io/legado')
EXT_DOC = ('.md', '.txt')
EXT_CODE = ('.kt', '.py', '.yml', '.yaml', '.json', '.js', '.html', '.sh')

TYPE_OF_PREFIX = (
    ('docs/修复-', '修复'), ('docs/验收-', '验收'), ('docs/机制-', '机制'),
    ('docs/实现-', '实现'), ('docs/收尾-', '收尾'), ('docs/重判-', '重判'),
    ('docs/锚点/', '锚点卡'), ('docs/问题清单', '清单'), ('docs/索引-', '索引'),
    ('docs/派单', '调度'), ('docs/落文-规矩总纲', '总纲'),
)
# 状态词取文件前 40 行第一个命中；未命中记 none，查询层 --status 不会误收。
STATUS_RE = re.compile(r'(DONE|在飞|作废|待裁|已收口|已修|待派|BLOCKED|RUNNING)')
HEADING_RE = re.compile(r'^#{1,3}\s+(.+)$')


def init_db(db_path):
    conn = sqlite3.connect(db_path)
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('''CREATE TABLE IF NOT EXISTS meta (
        key TEXT PRIMARY KEY, value TEXT)''')
    conn.execute('''CREATE TABLE IF NOT EXISTS chunks (
        id INTEGER PRIMARY KEY, path TEXT, heading TEXT,
        line_start INTEGER, line_end INTEGER,
        off_start INTEGER, off_end INTEGER,
        body TEXT, type TEXT, domain TEXT, status TEXT, mtime REAL, sha TEXT)''')
    # 旧库无偏移列时补齐：索引是可删可重建的缓存（R4），这里只做轻量迁移，不兼容旧正文。
    cols = {r[1] for r in conn.execute('PRAGMA table_info(chunks)')}
    if 'off_start' not in cols:
        conn.execute('ALTER TABLE chunks ADD COLUMN off_start INTEGER')
    if 'off_end' not in cols:
        conn.execute('ALTER TABLE chunks ADD COLUMN off_end INTEGER')
    # trigram 分词：中文没有空格，unicode61 会把整句当一个词；trigram 支持「任意子串」召回（≥3 字）。
    # 外部内容模式 content='chunks'：正文只存 chunks 一份，FTS 倒排按 rowid 回链；
    # 为什么不用 content=''（纯无内容）：contentless 表禁止 DELETE，增量更新时旧块倒排删不掉，
    # 会在 FTS 里留下幽灵命中；外部内容表 DELETE 时回读 chunks 旧值即可正确移除。
    conn.execute('''CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts
        USING fts5(body, heading, path, tokenize="trigram",
                   content='chunks', content_rowid='id')''')
    return conn


def classify(path):
    for prefix, kind in TYPE_OF_PREFIX:
        if path.startswith(prefix):
            return kind
    if path.startswith('docs/锚点'):
        return '锚点卡'
    if path.endswith('.kt'):
        return '源码'
    if path.startswith('data_scripts/'):
        return '脚本'
    return '其它'


def domain_of(path):
    """域 = 代码包名末两段 / docs 主题词：用于 --domain 先缩范围（R3）。"""
    if path.startswith('app/src/main/java/'):
        parts = path.split('/')
        pkg = parts[parts.index('io'):-1]
        return '.'.join(pkg[:4]) if len(pkg) >= 4 else '.'.join(pkg)
    tail = os.path.basename(path)
    tail = re.sub(r'^docs-', '', tail)
    tail = re.sub(r'\.(md|kt|py|json|yml)$', '', tail)
    return tail.split('-')[0] if tail else 'root'


def line_starts(text):
    """每行首字符的偏移表；line_at 用二分查偏移落在哪一行（1 起）。"""
    starts = [0]
    for i, ch in enumerate(text):
        if ch == '\n':
            starts.append(i + 1)
    return starts


def line_at(starts, off):
    return bisect.bisect_right(starts, off)


def split_md(text, starts):
    """按 #/##/### 切小节；返回 [(heading, start_line, start_off, body)]。"""
    lines = text.split('\n')
    blocks = []
    cur_head = '(文首)'
    cur_start = 1
    cur_off = 0
    buf = []
    for idx, line in enumerate(lines, 1):
        m = HEADING_RE.match(line)
        if m and buf:
            body = '\n'.join(buf)
            blocks.append((cur_head, cur_start, cur_off, body))
            cur_head = m.group(1).strip()
            cur_start = idx
            cur_off = starts[idx - 1]
            buf = [line]
        elif m:
            cur_head = m.group(1).strip()
            cur_start = idx
            cur_off = starts[idx - 1]
            buf = [line]
        else:
            buf.append(line)
    if buf:
        blocks.append((cur_head, cur_start, cur_off, '\n'.join(buf)))
    return blocks


def window_blocks(heading, start_line, start_off, body, starts):
    """超 500 字的节再切，相邻块重叠 50 字；行号与字符偏移都按位置反推。

    为什么存偏移：行号区间只覆盖整行，跨行进切的末行是截断行，整行回读对不上；
    偏移切片与 body 逐字相等，--verify 直接比子串，不再靠行号猜边界。
    """
    if len(body) <= CHUNK_CHARS:
        return [(heading, start_line, start_line + body.count('\n'),
                 start_off, start_off + len(body), body)]
    out = []
    step = CHUNK_CHARS - OVERLAP_CHARS
    pos = 0
    while pos < len(body):
        piece = body[pos:pos + CHUNK_CHARS]
        off0 = start_off + pos
        off1 = off0 + len(piece)
        ls = line_at(starts, off0)
        le = line_at(starts, max(off1 - 1, off0))
        out.append((heading, ls, le, off0, off1, piece))
        if pos + CHUNK_CHARS >= len(body):
            break
        pos += step
    return out


def split_code(text, starts):
    """源码按顶层声明切块，标题取最近声明行；二次切分统一交 window_blocks。

    修复点：旧版在这里先切了一次窗，build() 又切第二次，代码块被重复切片、
    行号整体错位；现在本函数只按声明切段，切窗只发生在 window_blocks 一处。
    """
    lines = text.split('\n')
    decl_re = re.compile(r'^(fun |class |object |interface |private fun |internal fun |@Composable)', re.I)
    out = []
    body = []
    head = '(文件头)'
    start = 1
    off = 0
    for idx, line in enumerate(lines, 1):
        if decl_re.match(line.strip()):
            if body:
                out.append((head, start, off, '\n'.join(body)))
            head = line.strip()[:80]
            start = idx
            off = starts[idx - 1]
            body = [line]
        else:
            body.append(line)
    if body:
        out.append((head, start, off, '\n'.join(body)))
    return out


def iter_files():
    for dirpath, dirnames, filenames in os.walk(os.path.join(ROOT, DOCS_DIR)):
        dirnames[:] = [d for d in dirnames if d not in ('..',)]
        for name in filenames:
            if name.endswith(EXT_DOC):
                yield os.path.relpath(os.path.join(dirpath, name), ROOT)
    for extra in EXTRA_DIRS:
        base = os.path.join(ROOT, extra)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in ('_cache', 'build', '__pycache__')]
            for name in filenames:
                if name.endswith(EXT_CODE):
                    yield os.path.relpath(os.path.join(dirpath, name), ROOT)


def build(conn, rebuild=False):
    if rebuild:
        conn.execute('DELETE FROM chunks')
        conn.execute('DELETE FROM chunks_fts')
        conn.execute('DELETE FROM meta')
    known = {}
    for cid, path, mtime, sha in conn.execute('SELECT id, path, mtime, sha FROM chunks'):
        known.setdefault(path, []).append((mtime, sha))
    seen = set()
    rebuilt_files = 0
    for rel in iter_files():
        seen.add(rel)
        full = os.path.join(ROOT, rel)
        try:
            mtime = os.path.getmtime(full)
            text = open(full, encoding='utf-8', errors='replace').read()
        except OSError:
            continue
        sha = hashlib.sha256(text.encode('utf-8', 'replace')).hexdigest()[:16]
        rows = known.get(rel)
        # 增量：mtime 与 sha 都不变才跳过（mtime 精度不足时 sha 兜底，R4）
        if rows and all(m == mtime and s == sha for m, s in rows):
            continue
        conn.execute('DELETE FROM chunks_fts WHERE rowid IN (SELECT id FROM chunks WHERE path=?)', (rel,))
        conn.execute('DELETE FROM chunks WHERE path=?', (rel,))
        starts = line_starts(text)
        if rel.endswith('.md') or rel.endswith('.txt'):
            blocks = split_md(text, starts)
        else:
            blocks = split_code(text, starts)
        status = 'none'
        for line in text.split('\n')[:40]:
            m = STATUS_RE.search(line)
            if m:
                status = m.group(1)
                break
        for heading, ls, loff, body in blocks:
            for h2, s2, e2, o0, o1, b2 in window_blocks(heading, ls, loff, body, starts):
                cur = conn.execute(
                    'INSERT INTO chunks(path,heading,line_start,line_end,off_start,off_end,'
                    'body,type,domain,status,mtime,sha) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
                    (rel, h2, s2, e2, o0, o1, b2, classify(rel), domain_of(rel),
                     status, mtime, sha))
                conn.execute('INSERT INTO chunks_fts(rowid, body, heading, path) VALUES (?,?,?,?)',
                             (cur.lastrowid, b2, h2, rel))
        rebuilt_files += 1
    conn.execute("INSERT OR REPLACE INTO meta(key,value) VALUES ('built_at',?)",
                 (str(int(time.time())),))
    conn.commit()
    return rebuilt_files, len(seen)


def stats(conn):
    docs, chunks, avg = conn.execute(
        'SELECT COUNT(DISTINCT path), COUNT(*), AVG(LENGTH(body)) FROM chunks').fetchone()
    print(f'RAG-INDEX: 文档 {docs} 份 / 块 {chunks} 个 / 平均块长 {int(avg or 0)} 字')
    for label, sql in (
        ('类型', 'SELECT type, COUNT(*) FROM chunks GROUP BY type ORDER BY 2 DESC'),
        ('状态', 'SELECT status, COUNT(*) FROM chunks GROUP BY status ORDER BY 2 DESC'),
        ('域前八', 'SELECT domain, COUNT(*) FROM chunks GROUP BY domain ORDER BY 2 DESC LIMIT 8'),
    ):
        rows = conn.execute(sql).fetchall()
        print(f'  {label}：' + '，'.join(f'{a}={b}' for a, b in rows))


def verify(conn, sample=20):
    rows = conn.execute(
        'SELECT path, line_start, line_end, off_start, off_end, body FROM chunks').fetchall()
    if len(rows) < 1:
        print('RAG-VERIFY: FAIL（索引为空，先跑 --rebuild）')
        return 1
    bad = 0
    for path, ls, le, o0, o1, body in random.sample(rows, min(sample, len(rows))):
        try:
            text = open(os.path.join(ROOT, path), encoding='utf-8', errors='replace').read()
        except OSError:
            bad += 1
            print(f'  FAIL 文件打不开：{path}')
            continue
        # 优先按字符偏移逐字比对；旧库缺偏移时退回行区间容错比对（边界行允许截断）。
        if o0 is not None and o1 is not None:
            ok = text[o0:o1] == body
            why = f'{path} 第 {ls}-{le} 行（偏移 {o0}-{o1}）'
        else:
            lines = text.split('\n')
            seg = lines[max(0, ls - 1):le]
            bl = body.split('\n')
            ok = len(seg) == len(bl) and all(a.strip() == b.strip() for a, b in zip(seg, bl))
            why = f'{path} 第 {ls}-{le} 行'
        if not ok:
            bad += 1
            print(f'  FAIL 锚点对不上原文：{why}')
    if bad:
        print(f'RAG-VERIFY: FAIL（抽样 {sample} 块，{bad} 块锚点对不上原文）')
        return 1
    print(f'RAG-VERIFY: PASS（抽样 {sample} 块，逐字回读一致）')
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rebuild', action='store_true')
    ap.add_argument('--stats', action='store_true')
    ap.add_argument('--verify', action='store_true')
    args = ap.parse_args()
    os.makedirs(CACHE_DIR, exist_ok=True)
    db_path = os.path.join(ROOT, DB_REL)
    conn = init_db(db_path)
    try:
        conn.execute('SELECT 1 FROM chunks_fts LIMIT 1')
    except sqlite3.OperationalError as exc:
        # FTS5 不可用时显式退出：静默退化成全表 LIKE 会把「索引坏了」变成「查得慢」，掩盖问题
        print(f'RAG-INDEX: FAIL（本机 sqlite3 无 FTS5：{exc}）')
        return 2
    if args.stats:
        stats(conn)
        return 0
    if args.verify:
        return verify(conn)
    changed, total = build(conn, rebuild=args.rebuild)
    stats(conn)
    print(f'RAG-INDEX: PASS（增量重建 {changed} 份，扫描 {total} 份）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
