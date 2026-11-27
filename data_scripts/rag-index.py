#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""机制层 RAG 索引器（切片 → 落 FTS5）。规格：docs/机制-RAG检索层.md（R1–R8）。

为什么用 FTS5 而不是向量库：本层的硬要求是「每条结论都能回到 文件:行 的原文」（R1）。
向量只回答「意思像不像」，不回答「原文在哪」，把疑似相关当已核实就是造假；
且全仓已有离线符号索引 data_scripts/dep-symbols.json，再引入模型/显存是纯负担。

切片口径（规格 §二）：按 Markdown 标题层级切小节；单节超过 500 字二次切分，
相邻块保留 50 字重叠——目的只有一个：不许把一条判据切成两半（R2）。

用法：
  python3 data_scripts/rag-index.py --stats      # 只统计已建索引
  python3 data_scripts/rag-index.py --verify     # 抽样回读原文逐字比对
  python3 data_scripts/rag-index.py --rebuild    # 整库重建
  python3 data_scripts/rag-index.py              # 增量更新（按 mtime）
"""
import argparse
import hashlib
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
STATUS_RE = re.compile(r'(DONE|在飞|作废|待裁|已收口|已修|待派|BLOCKED|RUNNING)')
HEADING_RE = re.compile(r'^#{1,3}\s+(.+)$')


def init_db(db_path):
    conn = sqlite3.connect(db_path)
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('''CREATE TABLE IF NOT EXISTS meta (
        key TEXT PRIMARY KEY, value TEXT)''')
    conn.execute('''CREATE TABLE IF NOT EXISTS chunks (
        id INTEGER PRIMARY KEY, path TEXT, heading TEXT,
        line_start INTEGER, line_end INTEGER, body TEXT,
        type TEXT, domain TEXT, status TEXT, mtime REAL, sha TEXT)''')
    # trigram 分词：中文没有空格，unicode61 会把整句当一个词；trigram 支持「任意子串」召回（≥3 字）
    conn.execute('''CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts
        USING fts5(body, heading, path, tokenize="trigram", content='')''')
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


def split_md(text):
    """按 #/##/### 切小节；返回 [(heading, start_line, end_line, body)]。"""
    lines = text.split('\n')
    blocks = []
    cur_head = '(文首)'
    cur_start = 1
    buf = []
    for idx, line in enumerate(lines, 1):
        m = HEADING_RE.match(line)
        if m and buf:
            blocks.append((cur_head, cur_start, idx - 1, '\n'.join(buf)))
            cur_head = m.group(1).strip()
            cur_start = idx
            buf = [line]
        elif m:
            cur_head = m.group(1).strip()
            cur_start = idx
            buf = [line]
        else:
            buf.append(line)
    if buf:
        blocks.append((cur_head, cur_start, len(lines), '\n'.join(buf)))
    return blocks


def window_blocks(heading, start_line, end_line, body):
    """超 500 字的节再切，相邻块重叠 50 字；行号按字符位置反推，保证锚点仍可回源。"""
    if len(body) <= CHUNK_CHARS:
        return [(heading, start_line, end_line, body)]
    out = []
    step = CHUNK_CHARS - OVERLAP_CHARS
    pos = 0
    while pos < len(body):
        piece = body[pos:pos + CHUNK_CHARS]
        pre_lines = body[:pos].count('\n')
        piece_lines = piece.count('\n')
        out.append((heading, start_line + pre_lines, start_line + pre_lines + piece_lines, piece))
        if pos + CHUNK_CHARS >= len(body):
            break
        pos += step
    return out


def split_code(text):
    """源码按 500 字窗口切，标题取最近的顶层声明行，方便「结构路」按符号名命中。"""
    lines = text.split('\n')
    decl_re = re.compile(r'^(fun |class |object |interface |private fun |internal fun |@Composable)', re.I)
    out = []
    body = []
    head = '(文件头)'
    start = 1
    for idx, line in enumerate(lines, 1):
        if decl_re.match(line.strip()):
            if body:
                out.append((head, start, idx - 1, '\n'.join(body)))
            head = line.strip()[:80]
            start = idx
            body = [line]
        else:
            body.append(line)
    if body:
        out.append((head, start, len(lines), '\n'.join(body)))
    return [(h, s, e, b) for h, s, e, b in
            (x for blk in out for x in window_blocks(*blk))]


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
        # 增量：mtime 与 sha 都不变才跳过（mtime 精度不足时 sha 兜底）
        if rows and all(m == mtime and s == sha for m, s in rows):
            continue
        conn.execute('DELETE FROM chunks_fts WHERE rowid IN (SELECT id FROM chunks WHERE path=?)', (rel,))
        conn.execute('DELETE FROM chunks WHERE path=?', (rel,))
        if rel.endswith('.md'):
            blocks = split_md(text)
        else:
            blocks = split_code(text)
        status = 'none'
        for line in text.split('\n')[:40]:
            m = STATUS_RE.search(line)
            if m:
                status = m.group(1)
                break
        for heading, ls, le, body in blocks:
            for h2, s2, e2, b2 in window_blocks(heading, ls, le, body):
                cur = conn.execute(
                    'INSERT INTO chunks(path,heading,line_start,line_end,body,type,domain,status,mtime,sha) '
                    'VALUES (?,?,?,?,?,?,?,?,?,?)',
                    (rel, h2, s2, e2, b2, classify(rel), domain_of(rel), status, mtime, sha))
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
    import random
    rows = conn.execute('SELECT path, line_start, line_end, body FROM chunks').fetchall()
    if len(rows) < 1:
        print('RAG-VERIFY: FAIL（索引为空，先跑 --rebuild）')
        return 1
    bad = 0
    for path, ls, le, body in random.sample(rows, min(sample, len(rows))):
        try:
            lines = open(os.path.join(ROOT, path), encoding='utf-8', errors='replace').read().split('\n')
        except OSError:
            bad += 1
            print(f'  FAIL 文件打不开：{path}')
            continue
        target = '\n'.join(lines[max(0, ls - 1):le])
        if target.strip() != body.strip():
            bad += 1
            print(f'  FAIL 锚点对不上原文：{path} 第 {ls}-{le} 行')
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
