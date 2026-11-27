#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""机制层 RAG 查询层（结构路 + 词法路 → 合并去重排序 → 回源片段）。

规格：docs/机制-RAG检索层.md。配套建库：data_scripts/rag-index.py。

两路召回口径（规格 §二）：
  - 结构路（权重最高）：路径 / 小节标题 / 符号名精确或包含命中。规格排序口径
    「结构命中 > 词法命中 > 长度惩罚」，结构路给固定高分档，词法路吃 BM25 分。
  - 词法路：FTS5 trigram MATCH，bm25() 排序。中文无空格必须用 trigram
    （unicode61 会把整句当一个词）；trigram 要求词长 ≥3，短词不支持时显式声明，
    不许静默退化成全表 LIKE（同索引器 R5 口径）。

回源口径（R1）：每条结果必须带 `文件:行号区间` + 字符偏移 + 原文片段；
检索结果只作线索，写进报告必须回原文重读并给锚点（R7）。

过滤口径（R3）：--type/--domain/--status/--since 先缩范围再召回；
未施加的过滤在头部打印实际生效集合，不藏在默认值里。

空结果口径（R8）：显式打印「未命中」并返回非零码；不许把「没找到」静默当「不存在」。

零网络零模型（R5）：只允许 Python 标准库；不 import socket/http/urllib，
不 spawn 任何外部进程（连 rg 都不调，第二层落点由人或后续工具接手）。

用法：
  python3 data_scripts/rag-query.py "关键词" [--type 修复] [--domain 陀螺仪]
      [--status 在飞] [--since 2026-11-20] [--top 20]
"""
import argparse
import datetime as _dt
import json
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_REL = 'data_scripts/_cache/rag-index.sqlite'
SYN_REL = 'data_scripts/rag-synonyms.json'

# 结构路权重档：标题精确 > 路径包含 > 标题包含。为什么分档：标题/符号名是作者
# 显式命过名的结构锚点，比正文词频更可靠（规格 §二「结构路权重最高」）。
W_HEADING_EXACT = 100.0
W_PATH_HIT = 80.0
W_HEADING_HIT = 60.0
# 长度惩罚：块过长通常是上下文堆叠，相关性被稀释；罚分很轻，只在并列时倾斜。
LEN_PENALTY_PER_CHAR = 1.0 / 20000.0

SNIPPET_CHARS = 160


def load_synonyms():
    """读同义词表；表缺失或损坏时降级为空表并显式提示，不联网下载、不自动学习。"""
    path = os.path.join(ROOT, SYN_REL)
    if not os.path.isfile(path):
        print('WARN 同义词表缺失（data_scripts/rag-synonyms.json），仅按原词召回')
        return []
    try:
        data = json.load(open(path, encoding='utf-8'))
        return data.get('groups', [])
    except (OSError, ValueError) as exc:
        print(f'WARN 同义词表解析失败（{exc}），仅按原词召回')
        return []


def expand_terms(query, groups):
    """原词 + 同义词族扩召。命中任一同族词即把整族并入；去重保序。"""
    q = query.lower()
    terms = [query.strip()]
    for g in groups:
        family = g.get('terms', [])
        if any(t.lower() in q for t in family):
            terms.extend(family)
    seen, out = set(), []
    for t in terms:
        t = t.strip()
        if t and t.lower() not in seen:
            seen.add(t.lower())
            out.append(t)
    return out


def fts_quote(term):
    """把词包成 FTS5 双引号字符串；内部双引号翻倍转义，防查询串注入 MATCH 语法。"""
    return '"' + term.replace('"', '""') + '"'


def split_tokens(query):
    """按空白/标点切词；trigram 只收 ≥3 字的片段，短片段交给结构路。"""
    raw = re.split(r'[\s,，。；;、/\\()（）:：]+', query)
    return [t for t in (x.strip() for x in raw) if len(t) >= 3]


def parse_since(text):
    """--since 接受 YYYY-MM-DD；返回当日 00:00 的 epoch 秒。格式错直接报错退出，不放宽。"""
    try:
        d = _dt.datetime.strptime(text, '%Y-%m-%d')
        return d.replace(tzinfo=_dt.timezone(_dt.timedelta(hours=8))).timestamp()
    except ValueError:
        print(f'RAG-QUERY: FAIL（--since 格式应为 YYYY-MM-DD，收到 {text!r}）')
        sys.exit(2)


def apply_filters(args):
    """先缩范围再召回（R3）。返回 (条件列表, 参数, 生效过滤字典)，条件由两路各自拼 WHERE。"""
    clauses, params, active = [], [], {}
    if args.type:
        clauses.append('type = ?')
        params.append(args.type)
        active['type'] = args.type
    if args.domain:
        clauses.append('domain LIKE ?')
        params.append(f'%{args.domain}%')
        active['domain'] = args.domain
    if args.status:
        clauses.append('status = ?')
        params.append(args.status)
        active['status'] = args.status
    if args.since:
        clauses.append('mtime >= ?')
        params.append(parse_since(args.since))
        active['since'] = args.since
    return clauses, params, active


def _where(clauses, prefix=''):
    """拼 WHERE 子句；prefix 给 JOIN 后的表别名（c.）。"""
    if not clauses:
        return ''
    pre = prefix
    out = [pre + c if pre else c for c in clauses]
    return 'WHERE ' + ' AND '.join(out)


def structure_route(conn, clauses, params, tokens):
    """结构路：标题精确 / 路径包含 / 标题包含。返回 {chunk_id: (score, kind)}。"""
    hits = {}
    for tok in tokens:
        # 标题精确命中：作者命名的小节/符号名，权重最高档
        conds = clauses + ['heading = ?']
        sql = f"SELECT id FROM chunks WHERE {' AND '.join(conds)}"
        for (cid,) in conn.execute(sql, params + [tok]):
            hits[cid] = max(hits.get(cid, (0, ''))[0], W_HEADING_EXACT), '标题精确'
        # 路径包含命中
        conds = clauses + ['path LIKE ?']
        sql = f"SELECT id FROM chunks WHERE {' AND '.join(conds)}"
        for (cid,) in conn.execute(sql, params + [f'%{tok}%']):
            sc = W_PATH_HIT
            if cid not in hits or sc > hits[cid][0]:
                hits[cid] = sc, '路径命中'
        # 标题包含命中（符号名/小节词）
        conds = clauses + ['heading LIKE ?']
        sql = f"SELECT id FROM chunks WHERE {' AND '.join(conds)}"
        for (cid,) in conn.execute(sql, params + [f'%{tok}%']):
            sc = W_HEADING_HIT
            if cid not in hits or sc > hits[cid][0]:
                hits[cid] = sc, '标题包含'
    return hits


def lexical_route(conn, clauses, params, terms):
    """词法路：FTS5 MATCH + bm25 排序。返回 {chunk_id: bm25 分（-bm25，越大越相关）}。"""
    match_terms = [t for t in terms if len(t) >= 3]
    if not match_terms:
        print('词法路：无 ≥3 字片段，trigram 不支持；仅走结构路（非静默降级，已显式声明）')
        return {}
    expr = ' OR '.join(fts_quote(t) for t in match_terms)
    where_alias = _where(clauses, prefix='c.')
    sql = f'''SELECT c.id, bm25(chunks_fts) FROM chunks_fts
              JOIN chunks c ON c.id = chunks_fts.rowid
              {where_alias}
              AND chunks_fts MATCH ?'''
    try:
        rows = conn.execute(sql, params + [expr]).fetchall()
    except sqlite3.OperationalError as exc:
        # MATCH 语法被词形打坏时显式退出；不许吞掉错误退回全表扫。
        print(f'RAG-QUERY: FAIL（FTS5 MATCH 失败：{exc}）')
        sys.exit(2)
    return {cid: -bm for cid, bm in rows}


def snippet(body, terms):
    """取首个命中词附近的片段；无命中词时取块首。R1：片段必须能对回原文。"""
    low = body.lower()
    pos = -1
    for t in terms:
        pos = low.find(t.lower())
        if pos >= 0:
            break
    if pos < 0:
        return body[:SNIPPET_CHARS].replace('\n', ' / ')
    start = max(0, pos - 40)
    end = min(len(body), pos + SNIPPET_CHARS - 40)
    return ('…' if start > 0 else '') + body[start:end].replace('\n', ' / ') + ('…' if end < len(body) else '')


def main():
    ap = argparse.ArgumentParser(description='机制层 RAG 查询（结构路 + 词法路）')
    ap.add_argument('query', help='问题或关键词')
    ap.add_argument('--type', help='按文档类型缩范围（修复/验收/机制/源码/脚本…）')
    ap.add_argument('--domain', help='按域缩范围（包名片段或 docs 主题词）')
    ap.add_argument('--status', help='按状态缩范围（DONE/在飞/作废/待裁…）')
    ap.add_argument('--since', help='只查 mtime 不早于该日期（YYYY-MM-DD）')
    ap.add_argument('--top', type=int, default=10, help='返回条数上限（默认 10）')
    args = ap.parse_args()

    db_path = os.path.join(ROOT, DB_REL)
    if not os.path.isfile(db_path):
        print(f'RAG-QUERY: FAIL（索引库不存在：{DB_REL}，先跑 rag-index.py --rebuild）')
        return 2
    conn = sqlite3.connect(db_path)
    try:
        conn.execute('SELECT 1 FROM chunks_fts LIMIT 1')
    except sqlite3.OperationalError as exc:
        # 同索引器：FTS5 不可用显式退出，禁止静默退化成 LIKE 全表扫。
        print(f'RAG-QUERY: FAIL（本机 sqlite3 无 FTS5：{exc}）')
        return 2

    clauses, params, active = apply_filters(args)
    if active:
        print('生效过滤：' + '，'.join(f'{k}={v}' for k, v in active.items()))
    else:
        print('生效过滤：未施加元数据过滤（全库召回，R3 允许但不推荐裸查）')

    groups = load_synonyms()
    terms = expand_terms(args.query, groups)
    tokens = split_tokens(args.query) or [args.query.strip()]

    struct = structure_route(conn, clauses, params, tokens)
    lexical = lexical_route(conn, clauses, params, terms)

    merged = {}
    for cid, (sc, kind) in struct.items():
        merged[cid] = {'score': sc, 'route': kind}
    for cid, lsc in lexical.items():
        if cid in merged:
            merged[cid]['score'] += lsc
            merged[cid]['route'] += '+词法'
        else:
            merged[cid] = {'score': lsc, 'route': '词法'}

    if not merged:
        # R8：空结果显式「未命中」+ 非零码；不许静默返回空列表当"不存在"。
        print(f'未命中：查询 {args.query!r} 在当前过滤集合下无任何召回（结构路 0 / 词法路 0）。')
        print('提示：可放宽 --type/--domain/--status/--since，或走第二层 rg/Glob 精确落点（R8）。')
        return 3

    rows = conn.execute(
        f'SELECT id, path, heading, line_start, line_end, off_start, off_end, '
        f'body, type, domain, status FROM chunks WHERE id IN '
        f'({",".join("?" for _ in merged)})', list(merged.keys())).fetchall()

    scored = []
    for cid, path, heading, ls, le, o0, o1, body, ctype, domain, status in rows:
        meta = merged[cid]
        penalty = len(body) * LEN_PENALTY_PER_CHAR
        score = meta['score'] - penalty
        scored.append((score, meta['route'], path, heading, ls, le, o0, o1,
                       body, ctype, domain, status))
    scored.sort(key=lambda r: -r[0])
    scored = scored[:args.top]

    print(f'召回：结构路 {len(struct)} 块 / 词法路 {len(lexical)} 块 / 合并去重 {len(merged)} 块，展示 {len(scored)} 条')
    for i, (score, route, path, heading, ls, le, o0, o1, body, ctype, domain, status) in enumerate(scored, 1):
        loc = f'{path}:{ls}-{le}'
        off = f'偏移 {o0}-{o1}' if o0 is not None else '偏移缺失'
        print(f'[{i}] {loc} 〔{ctype} / {domain} / {status}〕 {off} 路由={route}')
        print(f'    小节：{heading}')
        print(f'    片段：{snippet(body, terms)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
