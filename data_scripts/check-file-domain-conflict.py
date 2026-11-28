#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 g77 · 文件域互斥审计（从 W/V 卡表抽出的「文件域独占」能力，挂台账既有 `文件域` 列）。

为什么立它（2026-11-28 复盘共享 index 五次污染：d42514c / 15f9917 / ef687fb /
a11b9de / 62bf516；其中 62bf516 把在飞代码带走且已推送不可回滚）：
  所有代理共用同一个 `.git/index`，两条在飞单若声明的文件域相同或成父子目录重叠，
  后提交的一笔就可能把前一单未验收的改动一起带走。旧机制把「域独占」写在卡表纪律里，
  从未机检；本闸只读 `docs/派单台账.md` 既有 `文件域` 列，不新建第二份台账、不启用卡表。

判据（状态为 ACTIVE / RUNNING / 在飞 的行；终态行不参与）：
  D1 两条在飞行的文件域 token 完全相同 → DOMAIN-CONFLICT。
  D2 两条在飞行的文件域 token 成父子目录重叠（`ui/card/**` 与 `ui/card/BookCard.kt`）
     → DOMAIN-CONFLICT。
  D3 同一行内部多域（`、`/`；`/`,`/`+` 分隔）互不比对——多域是单内正常切分，不是跨单冲突。

豁免口径：
  - 域列为 `—` / `-` / `待拆` / `待定` / `待派` / `无` / 空，或纯散文 token（无路径、无扩展名）→ 跳过；
  - 白名单 `data_scripts/verdict-gate-allow.txt`（四列制表符：脚本名｜行号或缺陷号｜
    内容 sha256 前 12｜豁免理由）；身份绑台账行原文哈希，改名/换内容即失效（㊼ 形态）；
    白名单只许删不许加。

边界 / 误报面：
  - 只判台账声明的域，看不到工作树真实归属；声明写窄了（只写目录不写文件）本闸拦不住，
    那仍靠提交前 `git diff --cached --name-only` 人判与 g75/g40/g59 兜底。
  - 路径 token 含中文散文尾巴（如 `GlobeScreenHost.kt 最小接线`）按路径前缀归一，
    散文部分不参与比对；无法归出路径的 token 按豁免处理。
  - 终态词（✅/已交付/已收口/已完成/已提交/DONE/作废/已裁决/已判定/判不通过/待裁）
    出现在状态列即视为收口，不再当在飞行，避免「验收在飞」这类旁述把交付行误判成在飞。

退出码：0=PASS | 1=FAIL | 2=参数错 | 3=UNVERIFIED（台账不存在 / 在飞行空集，空集不判 PASS）
用法：
  python3 data_scripts/check-file-domain-conflict.py
  python3 data_scripts/check-file-domain-conflict.py --selftest
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

ACTIVE_RE = re.compile(r'RUNNING|在飞|ACTIVE', re.I)
# 终态词：状态列命中即视为已收口，不参与域互斥比对。
TERMINAL_RE = re.compile(r'✅|已交付|已收口|已完成|已提交|DONE|作废|已裁决|已判定|判不通过|待裁|HOLD|留待')
EXEMPT_TOKENS = {'—', '-', '待拆', '待定', '待派', '无', '同上', ''}
SPLIT_RE = re.compile(r'[、；;,]')
PATH_PREFIX = ('docs/', 'data_scripts/', 'app/', 'screenshot/', 'modules/', '.github/')
# 文件名扩展名（用于把无 `/` 的裸 token 判成路径或散文）。
EXT_RE = re.compile(r'\.(kt|py|md|sh|xml|json|yml|yaml|gradle|txt|js|ts|csv|toml)$')
ANCHOR_RE = re.compile(r':\d+(?:-\d+)?$')
AGENT_RE = re.compile(r's_[0-9A-Za-z]+')


# ---------- 台账解析 ----------

def parse_ledger(path: pathlib.Path) -> list[dict]:
    """返回行字典：lineno / defect / write_agent / domain / status / review / product / raw。"""
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


def is_active(row: dict) -> bool:
    return bool(ACTIVE_RE.search(row['status'])) and not TERMINAL_RE.search(row['status'])


# ---------- 文件域 token 归一 ----------

def _strip_prose(token: str) -> str:
    """去掉括注、行号后缀、`**`、`新建` 前缀；返回可比对的路径形态。"""
    t = token.strip()
    t = re.sub(r'（[^）]*）', '', t)
    t = re.sub(r'\([^)]*\)', '', t)
    t = re.sub(r'^新建\s*', '', t)
    t = ANCHOR_RE.sub('', t)
    t = t.rstrip('/').strip()
    return t


def norm_token(token: str) -> list[tuple[str, str, str]]:
    """归一为若干 (kind, dir, base)；kind ∈ path / bare。

    一个 `、` 分组里常以 `/` 拼接多个文件（`StarStore.kt/StarStoreProvider.kt/
    StarCatalog.kt`）：目录前缀＝首个带扩展名段之前的各段；其后的 `/名称.ext` 都是
    同父目录下的兄弟文件，不能拿整串当一个路径判父子重叠（否则兄弟文件会被误报成
    目录祖先）。无法归出路径的散文 token 返回空表。
    """
    t = _strip_prose(token)
    if not t or t in EXEMPT_TOKENS:
        return []
    if '/' not in t:
        base = _clean_seg(t)
        return [('bare', '', base)] if EXT_RE.search(base) else []
    parts = [p for p in t.split('/') if p]
    dir_parts: list[str] = []
    files: list[str] = []
    started = False
    subtree = False
    for raw in parts:
        seg = _clean_seg(raw)
        if not seg:
            continue
        if seg == '**':
            subtree = True
            continue
        if not started and EXT_RE.search(seg):
            started = True
        if started:
            files.append(seg)
        else:
            dir_parts.append(seg)
    dirname = '/'.join(dir_parts)
    out: list[tuple[str, str, str]] = []
    for base in files:
        if EXT_RE.search(base):
            out.append(('path' if dirname else 'bare', dirname, base))
    # 纯目录通配（`ui/card/**`）：记 subtree 标记，base=`**`，用于父子重叠判定。
    if subtree and not out:
        out.append(('path', dirname, '**'))
    return out


def split_domains(cell: str) -> list[str]:
    """按顿号/分号/逗号切分；括号内的分隔符属于散文，不切。"""
    out: list[str] = []
    buf = ''
    depth = 0
    for ch in cell:
        if ch in '（(':
            depth += 1
        elif ch in '）)':
            depth = max(0, depth - 1)
        if depth == 0 and SPLIT_RE.fullmatch(ch):
            out.append(buf)
            buf = ''
        else:
            buf += ch
    out.append(buf)
    return [t for t in out if t and t.strip()]


def _clean_seg(seg: str) -> str:
    """剥掉路径段后粘连的中文散文：只保留开头的路径字符（字母/数字/._-*）。"""
    m = re.match(r'^([A-Za-z0-9_.\-*]+)', seg.strip())
    return m.group(1) if m else ''


def _dir_ancestor(da: str, db: str) -> bool:
    return db.startswith(da + '/') or da.startswith(db + '/')


def conflict(a: tuple[str, str, str], b: tuple[str, str, str]) -> bool:
    """两归一 token 是否域重叠。"""
    ka, da, ba = a
    kb, db, bb = b
    # 通配子树：一方 `**` 覆盖另一方同目录或子目录下的文件即重叠。
    if ba == '**' or bb == '**':
        wild, other = (a, b) if ba == '**' else (b, a)
        _, dw, _ = wild
        _, do, _ = other
        if dw == do:
            return True
        return do.startswith(dw + '/') if dw else False
    if ka == 'bare' and kb == 'bare':
        return ba == bb
    if ka != kb:
        return False
    # 同为 path：完全相同，或目录互为前缀（父子重叠）。
    if da == db and ba == bb:
        return True
    if da == db:
        return False
    return _dir_ancestor(da, db)


# ---------- 白名单（内容哈希身份，㊼ 形态） ----------

def _digest(raw: str) -> str:
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()[:12]


def load_allow(path: pathlib.Path, gate: str) -> list[dict]:
    """返回 [{locator, digest, reason}]；身份哈希在命中时按台账行原文逐行复核。"""
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
    """命中条件：脚本匹配（加载期已滤）+ 定位键（行号或缺陷号）匹配 + 行原文哈希与登记一致。"""
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


# ---------- 主流程 ----------

def run(ledger: pathlib.Path = LEDGER, root: pathlib.Path = ROOT,
        allow_path: pathlib.Path = ALLOW, gate: str = 'g77') -> int:
    rows = parse_ledger(ledger)
    if not rows:
        print('FILE-DOMAIN-CONFLICT: UNVERIFIED（台账不存在或零行，空集不可判定）')
        return 3
    active = [r for r in rows if is_active(r)]
    if not active:
        print('FILE-DOMAIN-CONFLICT: UNVERIFIED（在飞行空集，空集不判 PASS）')
        return 3

    allow = load_allow(allow_path, gate)
    findings: list[dict] = []
    tokens: list[tuple[dict, tuple[str, str, str]]] = []
    for row in active:
        for tok in split_domains(row['domain']):
            for norm in norm_token(tok):
                tokens.append((row, norm))

    for i in range(len(tokens)):
        row_a, ta = tokens[i]
        for j in range(i + 1, len(tokens)):
            row_b, tb = tokens[j]
            if row_a['lineno'] == row_b['lineno']:
                continue  # D3 同一行多域不比对
            if conflict(ta, tb):
                findings.append({
                    'rows': (row_a['lineno'], row_b['lineno']),
                    'defects': (row_a['defect'], row_b['defect']),
                    'tokens': (row_a['domain'], row_b['domain']),
                })

    # 白名单逐行豁免（两条行各自复核身份哈希，两条都在册才豁免该对冲突）。
    kept: list[dict] = []
    for f in findings:
        r1 = next(r for r in active if r['lineno'] == f['rows'][0])
        r2 = next(r for r in active if r['lineno'] == f['rows'][1])
        if exempted(r1, allow) and exempted(r2, allow):
            continue
        kept.append(f)

    if not kept:
        print(f'FILE-DOMAIN-CONFLICT: PASS（在飞 {len(active)} 行，域重叠 0；白名单 {len(allow)} 条）')
        return 0
    print(f'FILE-DOMAIN-CONFLICT: FAIL（{len(kept)} 对在飞单文件域相同或父子重叠）')
    for f in kept:
        print(f"  ✗ 行 {f['rows'][0]}（{f['defects'][0][:24]}）↔ 行 {f['rows'][1]}（{f['defects'][1][:24]}）："
              f"{f['tokens'][0][:40]} ↔ {f['tokens'][1][:40]}")
    return 1


# ---------- 自测夹具 ----------

FIXTURE_LEDGER = """| 缺陷/条目 | 写码代理 | 文件域 | 状态 | 独立验收代理 | 验收产物 | 最高级别 |
|---|---|---|---|---|---|---|
| T-1 不重叠甲 | s_aaa | ui/card/BookCard.kt | 🟡 在飞（s_aaa，10:00 起） | 待派 | （待产出） | L1 |
| T-2 不重叠乙 | s_bbb | engine/CampaignController.kt | 🟡 在飞（s_bbb，10:01 起） | 待派 | （待产出） | L1 |
| T-3 完全相同 | s_ccc | ui/card/** | 🟡 在飞（s_ccc，10:02 起） | 待派 | （待产出） | L1 |
| T-4 父子重叠 | s_ddd | ui/card/BookCard.kt | 🟡 在飞（s_ddd，10:03 起） | 待派 | （待产出） | L1 |
| T-5 同单多域 | s_eee | host/SkyInputHandler.kt；astro/data/StarStore.kt | 🟡 在飞（s_eee，10:04 起） | 待派 | （待产出） | L1 |
"""


def _selftest_run(tmp: pathlib.Path, ledger_text: str) -> tuple[int, int]:
    led = tmp / '派单台账.md'
    led.write_text(ledger_text, encoding='utf-8')
    allow = tmp / 'allow.txt'
    allow.write_text('', encoding='utf-8')
    code = run(led, tmp, allow, gate='g77')
    rows = parse_ledger(led)
    active = [r for r in rows if is_active(r)]
    return code, len(active)


def selftest() -> int:
    ok = True
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='file-domain-selftest-'))
    try:
        # 夹具 1：正常不重叠（T-1/T-2 分离；T-5 同单多域自比）→ PASS
        code, n_active = _selftest_run(tmp, FIXTURE_LEDGER)
        # 正常集：T-1/T-2 不冲突；T-3 vs T-4 相同+父子两条冲突；T-5 不与自己冲突。
        # 完整夹具期望 FAIL（含 T-3/T-4 两对），这里分开构造纯不重叠集验 PASS。
        good_text = FIXTURE_LEDGER.replace('| T-3 完全相同 | s_ccc | ui/card/** |',
                                           '| T-3 完全相同 | s_ccc | engine/Other.kt |') \
                                  .replace('| T-4 父子重叠 | s_ddd | ui/card/BookCard.kt |',
                                           '| T-4 父子重叠 | s_ddd | engine/VictoryChecker.kt |')
        code_good, _ = _selftest_run(tmp, good_text)
        if code_good != 0:
            ok = False
            print('  SELFTEST FAIL 夹具1 不重叠集应 PASS，rc=%s' % code_good)
        else:
            print('  SELFTEST PASS 夹具1 不重叠 + 同单多域不误报')

        # 夹具 2：完全相同 → FAIL（只含一对完全相同的域）
        same_text = '\n'.join([
            FIXTURE_LEDGER.splitlines()[0], FIXTURE_LEDGER.splitlines()[1],
            '| T-3a 相同甲 | s_ccc | ui/card/** | 🟡 在飞（s_ccc，10:02 起） | 待派 | （待产出） | L1 |',
            '| T-3b 相同乙 | s_ccc | ui/card/** | 🟡 在飞（s_ccc，10:03 起） | 待派 | （待产出） | L1 |']) + '\n'
        code_same, _ = _selftest_run(tmp, same_text)
        if code_same != 1:
            ok = False
            print('  SELFTEST FAIL 夹具2 完全相同应 FAIL，rc=%s' % code_same)
        else:
            print('  SELFTEST PASS 夹具2 完全相同判红')

        # 夹具 3：父子重叠（ui/card/** vs ui/card/BookCard.kt）→ FAIL
        code_parent, _ = _selftest_run(tmp, FIXTURE_LEDGER)
        if code_parent != 1:
            ok = False
            print('  SELFTEST FAIL 夹具3 父子重叠应 FAIL，rc=%s' % code_parent)
        else:
            print('  SELFTEST PASS 夹具3 父子重叠判红')

        # 夹具 4：同单多域不误报（已含在夹具1 的 T-5 行；good 集 PASS 即证明）。
        print('  SELFTEST 夹具4 同单多域：随夹具1 一并验过（同行 token 互不比对）')

        # 空集 → UNVERIFIED rc=3
        empty = tmp / 'empty.md'
        empty.write_text('| 缺陷/条目 | 写码代理 | 文件域 | 状态 | 独立验收代理 | 验收产物 | 最高级别 |\n', encoding='utf-8')
        code_empty = run(empty, tmp, tmp / 'allow.txt')
        if code_empty != 3:
            ok = False
            print('  SELFTEST FAIL 空集应 UNVERIFIED rc=3，rc=%s' % code_empty)
        else:
            print('  SELFTEST PASS 空集 UNVERIFIED rc=3（不判假 PASS）')
    finally:
        import shutil
        shutil.rmtree(tmp, ignore_errors=True)
    print('SELFTEST: ' + ('PASS（不重叠放行 / 完全相同判红 / 父子重叠判红 / 同单多域不误报 / 空集 rc=3）'
                        if ok else 'FAIL'))
    return 0 if ok else 1


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description='g77 文件域互斥审计')
    ap.add_argument('--selftest', action='store_true')
    args = ap.parse_args(argv)
    if args.selftest:
        return selftest()
    return run()


if __name__ == '__main__':
    sys.exit(main())
