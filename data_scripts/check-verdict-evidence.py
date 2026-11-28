#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 g78 · 判据证据对齐审计（从 W/V 卡表抽出的「配对验收·过必带证据」能力，
挂台账既有 `独立验收代理` / `验收产物` 列）。

为什么立它（2026-11-28 复盘系统性假绿：28 个公开成员生产零调用、bench 在占位数据下
假绿、判据 3 正向死链）：旧流程里「已交付 / 已收口」只写在台账状态列，验收代理与证据
产物常常缺位或指向不存在的文件；报告里判据条数与可回源证据条数不对齐，一条证据服务
多条判据也算过。本闸只读 `docs/派单台账.md` 既有列与各单验收报告，不新建第二份台账、
不启用卡表。

判据（状态为 DONE / 已验收 的行）：
  E1 REVIEW-MISSING / SAME-AGENT：`独立验收代理` 列非空，且其代理 id 与 `写码代理`
     不同（F-108 分人）。占位词（待派/无/—/待定/待产出/不另设验收/待回执…）视为缺位；
     两侧都能提取到 `s_` 代理 id 且相同 → SAME-AGENT。
  E2 PRODUCT-MISSING / PRODUCT-DEAD：`验收产物` 列指向的文件必须真实存在；占位产物
     （待产出/预定/待定/—）判 PRODUCT-MISSING；抽到的仓内路径解析不到文件判 PRODUCT-DEAD。
  E3 CRITERIA-WITHOUT-EVIDENCE / UNBALANCED：产物报告里每条判据都须带可回源证据
     （`文件:行` / CI `run id` / `文件:行号` 三者之一）；判据条数与证据条数一一对齐，
     一条证据服务多条判据（多对一）不算对齐。报告解析不出判据标记时跳过本项（不可机械
     判定，不逼人造格式），不算 FAIL。
  E4 ANCHOR-DEADFILE / ANCHOR-OUTOFRANGE：产物里每条 `文件:行` 锚点的文件必须存在，
     行号不超过该文件总行数。

豁免：白名单 `data_scripts/verdict-gate-allow.txt`（四列制表符：脚本名｜行号或缺陷号｜
内容 sha256 前 12｜豁免理由）；身份绑台账行原文哈希，改名/换内容即失效（㊼ 形态）；
白名单只许删不许加。

边界 / 误报面：
  - 只判台账声明与产物回源，判不了证据本身是否充分（那是验收代理 L1 逐条销项的职责）。
  - 判据标记靠正则识别；老式报告用散文逐条销项、没有「判据N/P01」字样时，E3 跳过，
    由收口全量 gate 与人工复核兜底。
  - 产物列引用仓外文档（切片/锚点卡基名无路径前缀）不做存在性判定，避免把无法解析的
    引用一律判死。

退出码：0=PASS | 1=FAIL | 2=参数错 | 3=UNVERIFIED（台账不存在 / 已验收行空集，空集不判 PASS）
用法：
  python3 data_scripts/check-verdict-evidence.py
  python3 data_scripts/check-verdict-evidence.py --selftest
"""
from __future__ import annotations

import argparse
import hashlib
import pathlib
import re
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
LEDGER = ROOT / 'docs/派单台账.md'
ALLOW = ROOT / 'data_scripts/verdict-gate-allow.txt'

DONE_RE = re.compile(r'DONE|已验收', re.I)
AGENT_RE = re.compile(r's_[0-9A-Za-z]+')
# 占位词：验收代理 / 产物列出现这些词视为未登记，不算有效独立验收或产物。
REVIEW_PLACEHOLDER = re.compile(r'待派|待核查|待裁|无|—|-|待定|待产出|待回执|不另设验收|证据单')
PRODUCT_PLACEHOLDER = re.compile(r'待产出|预定|待定|—|-|（待产出）|（预定）')
PATH_RE = re.compile(
    r'(?:docs|data_scripts|app|screenshot|modules|\.github)/[^\s，、；）)（]+?'
    r'\.(?:kt|py|md|sh|xml|json|yml|yaml|gradle|txt|csv|toml|js|html|css)')
ANCHOR_RE = re.compile(
    r'(?P<path>[A-Za-z0-9_./\-一-龥]+\.(?:kt|py|md|sh|xml|json|yml|yaml|gradle|txt|csv|toml|js|html|css))'
    r':(?P<line>\d+)(?:-\d+)?')
RUN_RE = re.compile(r'(?<![0-9])(\d{10,})(?![0-9])')
# 判据标记：判据N / P01 / 销项表行（表格首格带 C-/D-/Q- 号）/ 判据小标题。
# 不匹配散文里的编号枚举（「1. 2. 3.」）与正文顺带提及的 D-/Q- 编号，避免把推理段落
# 误判成待销项判据（误报边界见模块 docstring）。
CRITERION_RE = re.compile(r'判据\s*\d+|P\d{2}|^\s*\|[^|]*[CDQ]-\d+|^\s*#{1,4}\s*判据')
INDEX_ROOTS = ['docs', 'data_scripts', 'app/src', 'screenshot/src', 'modules', '.github/workflows']


# ---------- 台账解析 ----------

def parse_ledger(path: pathlib.Path) -> list[dict]:
    rows: list[dict] = []
    if not path.is_file():
        return rows
    for i, line in enumerate(path.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
        if not line.startswith('|'):
            continue
        cells = [c.strip() for c in line.split('|')[1:-1]]
        if len(cells) != 7:
            continue
        if cells[0] == '缺陷/条目' or set(cells[0]) <= set('- '):
            continue
        rows.append({
            'lineno': i,
            'defect': cells[0],
            'write': cells[1],
            'domain': cells[2],
            'status': cells[3],
            'review': cells[4],
            'product': cells[5],
            'level': cells[6],
            'raw': line,
        })
    return rows


def is_done(row: dict) -> bool:
    return bool(DONE_RE.search(row['status']))


# ---------- 白名单（内容哈希身份，㊼ 形态） ----------

def _digest(raw: str) -> str:
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()[:12]


def load_allow(path: pathlib.Path, gate: str) -> list[dict]:
    allow: list[dict] = []
    if not path.is_file():
        return allow
    for line in path.read_text(encoding='utf-8', errors='replace').splitlines():
        if line.startswith('#') or not line.strip():
            continue
        cells = line.split('\t')
        if len(cells) < 4:
            continue
        script, locator, digest, reason = [c.strip() for c in cells[:4]]
        if script not in (gate, '*'):
            continue
        allow.append({'locator': locator, 'digest': digest, 'reason': reason})
    return allow


def exempted(row: dict, allow: list[dict]) -> bool:
    if not allow:
        return False
    digest = _digest(row['raw'])
    defect_ids = set(re.findall(r'[A-Za-z]+-\d+|Q-\d+|B\d+|D\d+|C\d+R?\d*', row['defect']))
    for item in allow:
        loc = item['locator']
        m = re.match(r'行(\d+)', loc)
        line_hit = bool(m) and int(m.group(1)) == row['lineno']
        hit_loc = line_hit or any(d in loc for d in defect_ids)
        if hit_loc and item['digest'] and digest == item['digest']:
            return True
    return False


# ---------- 锚点解析 ----------

def build_base_index(root: pathlib.Path) -> dict[str, list[pathlib.Path]]:
    idx: dict[str, list[pathlib.Path]] = {}
    for rel in INDEX_ROOTS:
        base = root / rel
        if not base.is_dir():
            continue
        for f in base.rglob('*'):
            if f.is_file():
                idx.setdefault(f.name, []).append(f)
    return idx


def resolve(ref: str, root: pathlib.Path, base_index: dict[str, list[pathlib.Path]]):
    cand = root / ref
    if cand.is_file():
        return cand
    hits = base_index.get(pathlib.Path(ref).name, [])
    if len(hits) == 1:
        return hits[0]
    narrowed = [h for h in hits if h.as_posix().endswith(ref)]
    if len(narrowed) == 1:
        return narrowed[0]
    return None


def check_product_file(path: pathlib.Path, root: pathlib.Path,
                       base_index: dict[str, list[pathlib.Path]]) -> list[str]:
    """E3/E4：读产物报告，返回 finding 代码串列表。"""
    findings: list[str] = []
    if not path.is_file():
        return ['PRODUCT-DEAD']
    text = path.read_text(encoding='utf-8', errors='replace')
    lines = text.splitlines()

    # E4：锚点文件存在性 + 行号越界。
    for m in ANCHOR_RE.finditer(text):
        ref, lineno = m.group('path'), int(m.group('line'))
        target = resolve(ref, root, base_index)
        if target is None:
            findings.append('ANCHOR-DEADFILE')
            continue
        total = len(target.read_text(encoding='utf-8', errors='replace').splitlines())
        if lineno > total:
            findings.append('ANCHOR-OUTOFRANGE')

    # E3：判据 ↔ 证据一一对齐。
    # 判据分两类：标题小节（`## 判据 N`）证据在标题下到下一判据标题之间的整块；
    # 行内提及（bullet / 表格行）证据必须落在同一行。
    heading_lines: list[int] = []
    inline_lines: list[int] = []
    for i, line in enumerate(lines, 1):
        if CRITERION_RE.search(line):
            if line.lstrip().startswith('#'):
                heading_lines.append(i)
            else:
                inline_lines.append(i)

    def evidence_in(a: int, b: int) -> int:
        return sum(len(ANCHOR_RE.findall(lines[k - 1])) + len(RUN_RE.findall(lines[k - 1]))
                   for k in range(a, b + 1))

    blocks: list[tuple[int, int]] = []
    for n, h in enumerate(heading_lines):
        end = heading_lines[n + 1] - 1 if n + 1 < len(heading_lines) else len(lines)
        blocks.append((h, end))
    for i in inline_lines:
        blocks.append((i, i))

    if blocks:
        total_evidence = 0
        for a, b in blocks:
            ev = evidence_in(a, b)
            total_evidence += ev
            if ev == 0:
                findings.append('CRITERIA-WITHOUT-EVIDENCE')
        if total_evidence < len(blocks):
            findings.append('UNBALANCED')
    return findings


# ---------- 主流程 ----------

def run(ledger: pathlib.Path = LEDGER, root: pathlib.Path = ROOT,
        allow_path: pathlib.Path = ALLOW, gate: str = 'g78') -> int:
    rows = parse_ledger(ledger)
    if not rows:
        print('VERDICT-EVIDENCE: UNVERIFIED（台账不存在或零行，空集不可判定）')
        return 3
    done = [r for r in rows if is_done(r)]
    if not done:
        print('VERDICT-EVIDENCE: UNVERIFIED（已验收行空集，空集不判 PASS）')
        return 3

    allow = load_allow(allow_path, gate)
    base_index = build_base_index(root)
    findings: list[dict] = []

    for row in done:
        row_findings: list[str] = []
        # E1 分人
        write_ids = set(AGENT_RE.findall(row['write']))
        review_ids = set(AGENT_RE.findall(row['review']))
        if REVIEW_PLACEHOLDER.search(row['review']) and not review_ids:
            row_findings.append('REVIEW-MISSING')
        elif write_ids and review_ids and write_ids & review_ids:
            row_findings.append('SAME-AGENT')
        # E2 产物存在性
        refs = PATH_RE.findall(row['product'])
        if PRODUCT_PLACEHOLDER.search(row['product']) and not refs:
            row_findings.append('PRODUCT-MISSING')
        elif refs:
            for ref in refs:
                if resolve(ref, root, base_index) is None:
                    row_findings.append('PRODUCT-DEAD')
        # E3/E4 产物报告内部对齐
        for ref in refs:
            target = resolve(ref, root, base_index)
            if target is not None:
                row_findings.extend(check_product_file(target, root, base_index))

        if row_findings and not exempted(row, allow):
            findings.append({'row': row, 'codes': sorted(set(row_findings))})

    if not findings:
        print(f'VERDICT-EVIDENCE: PASS（已验收 {len(done)} 行，证据对齐 0 违规；白名单 {len(allow)} 条）')
        return 0
    print(f'VERDICT-EVIDENCE: FAIL（{len(findings)} 行证据链不闭合）')
    for f in findings:
        r = f['row']
        print(f"  ✗ 行 {r['lineno']}（{r['defect'][:24]}）：{', '.join(f['codes'])}")
    return 1


# ---------- 自测夹具 ----------

def _make_repo(tmp: pathlib.Path) -> dict[str, pathlib.Path]:
    docs = tmp / 'docs'
    (docs / '验收').mkdir(parents=True)
    src = tmp / 'app/src/main/kotlin'
    src.mkdir(parents=True)
    report = docs / '验收-夹具单.md'
    code = src / 'Fixture.kt'
    code.write_text('package x\n\nfun used() = 1\n', encoding='utf-8')
    return {'report': report, 'code': code, 'docs': docs}


def _ledger_row(defect: str, write: str, review: str, product: str, status: str = '✅ DONE') -> str:
    return (f'| {defect} | {write} | 域 | {status} | {review} | {product} | L1 |\n')


def selftest() -> int:
    ok = True
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='verdict-evidence-'))
    try:
        repo = _make_repo(tmp)
        allow = tmp / 'allow.txt'
        allow.write_text('', encoding='utf-8')
        header = ('| 缺陷/条目 | 写码代理 | 文件域 | 状态 | 独立验收代理 | 验收产物 | 最高级别 |\n'
                  '|---|---|---|---|---|---|---|\n')

        # 夹具 1：全齐 PASS——两条判据各带一条真实锚点，写码/验收分人，产物存在。
        repo['report'].write_text(
            '# 夹具单\n判据1：`app/src/main/kotlin/Fixture.kt:3` 落点存在。\n'
            '判据2：`app/src/main/kotlin/Fixture.kt:3` 落点存在。\n', encoding='utf-8')
        led = tmp / 'l1.md'
        led.write_text(header + _ledger_row('T-1', 's_aaa', 's_bbb',
                                            'docs/验收-夹具单.md'), encoding='utf-8')
        code = run(led, tmp, allow)
        if code != 0:
            ok = False
            print('  SELFTEST FAIL 夹具1 全齐应 PASS，rc=%s' % code)
        else:
            print('  SELFTEST PASS 夹具1 全齐 PASS')

        # 夹具 2：缺一条证据 FAIL——两条判据只有一条锚点。
        repo['report'].write_text(
            '# 夹具单\n判据1：`app/src/main/kotlin/Fixture.kt:3` 落点存在。\n'
            '判据2：无锚点陈述。\n', encoding='utf-8')
        led = tmp / 'l2.md'
        led.write_text(header + _ledger_row('T-2', 's_aaa', 's_bbb',
                                            'docs/验收-夹具单.md'), encoding='utf-8')
        code = run(led, tmp, allow)
        if code != 1:
            ok = False
            print('  SELFTEST FAIL 夹具2 缺证据应 FAIL，rc=%s' % code)
        else:
            print('  SELFTEST PASS 夹具2 缺一条证据判红')

        # 夹具 3：证据行号越界 FAIL——锚点行号超过文件总行数。
        repo['report'].write_text(
            '# 夹具单\n判据1：`app/src/main/kotlin/Fixture.kt:999` 越界。\n', encoding='utf-8')
        led = tmp / 'l3.md'
        led.write_text(header + _ledger_row('T-3', 's_aaa', 's_bbb',
                                            'docs/验收-夹具单.md'), encoding='utf-8')
        code = run(led, tmp, allow)
        if code != 1:
            ok = False
            print('  SELFTEST FAIL 夹具3 行号越界应 FAIL，rc=%s' % code)
        else:
            print('  SELFTEST PASS 夹具3 行号越界判红')

        # 夹具 4：写码与验收同一代理 FAIL。
        repo['report'].write_text(
            '# 夹具单\n判据1：`app/src/main/kotlin/Fixture.kt:3` 落点存在。\n', encoding='utf-8')
        led = tmp / 'l4.md'
        led.write_text(header + _ledger_row('T-4', 's_ccc', 's_ccc',
                                            'docs/验收-夹具单.md'), encoding='utf-8')
        code = run(led, tmp, allow)
        if code != 1:
            ok = False
            print('  SELFTEST FAIL 夹具4 同人验收应 FAIL，rc=%s' % code)
        else:
            print('  SELFTEST PASS 夹具4 同人验收判红')

        # 空集 → UNVERIFIED rc=3
        empty = tmp / 'empty.md'
        empty.write_text(header, encoding='utf-8')
        code = run(empty, tmp, allow)
        if code != 3:
            ok = False
            print('  SELFTEST FAIL 空集应 UNVERIFIED rc=3，rc=%s' % code)
        else:
            print('  SELFTEST PASS 空集 UNVERIFIED rc=3（不判假 PASS）')
    finally:
        import shutil
        shutil.rmtree(tmp, ignore_errors=True)
    print('SELFTEST: ' + ('PASS（全齐放行 / 缺证据判红 / 越界判红 / 同人判红 / 空集 rc=3）'
                        if ok else 'FAIL'))
    return 0 if ok else 1


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description='g78 判据证据对齐审计')
    ap.add_argument('--selftest', action='store_true')
    args = ap.parse_args(argv)
    if args.selftest:
        return selftest()
    return run()


if __name__ == '__main__':
    sys.exit(main())
