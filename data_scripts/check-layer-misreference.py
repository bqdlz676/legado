#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 g73 · 层属错置门（G-11 / F-119 规则文件四层分级）。

为什么立这条（见 docs/台账-三件缺项.md G-11、docs/规则总账.md 第十节、
docs/规则冲突清单.md C-08/C-09）：
  2026-11-26 实测 docs/ 下 22 份规则文档 + 3 份卡表，同一事实（槽位切分、规则条数、
  门禁项数）在三处以上各写一套；草案层卡表（写码卡/验收卡/任务卡/卡规则优化/
  卡片相关规则对照/链条图）三件套缺两件，从未进调度，却可能被新派单或验收报告
  当成权威依据引用。此前全仓无层属扫描脚本，草案层被引用进派单/验收只能人工复核，
  有误把草案当权威放行的风险。

判据：
  - 草案层 / 权威层清单不从本闸自拍板：机械解析 `docs/规则总账.md` §10.1 权威层、
    §10.3 草案层两小节登记的反引号路径（F-119 台账条目本身是唯一事实源）。
  - 扫描调度/验收消费方（派单队列、派单台账、问题清单、修复/验收/实现/交付报告），
    出现草案层文件的路径引用（`docs/xxx.md` / 反引号 `xxx.md` / `xxx.html`）即命中；
    命中行带草案/作废/降级/留档/冲突登记/旧稿/语料等语境词，或命中文件本身是
    讨论分层的台账/清单/复盘/清点文档 → 豁免（那是在登记草案身份，不是引用它干活）。
  - 命中给引用行（文件:行 + 摘录），不猜语义。

适用边界 / 误判面 / 不能替代什么：
  - 只判"草案层被当依据引用"；派生层快照数字的引用规范归 g72 计数漂移门，不重复。
  - 裸词"验收卡/任务卡"不匹配（那可能指独立验收锚点卡）；必须带文件名扩展名或
    反引号路径才认定为卡表文件引用，避免把"无独立验收卡"这类话术误判红。
  - 层属变更（草案→权威）必须先在总账登记 §10.1 并补三件套（F-119 §10.5 第 3 条），
    本闸只按总账现行清单判，不判断某草案是否"本该升格"。
  - 本闸不替代冲突清单 C-xx 裁决检查（㉓ check-rule-ledger.py C 组）。

退出码：0=PASS | 1=FAIL | 2=参数错
用法：
  python3 data_scripts/check-layer-misreference.py
  python3 data_scripts/check-layer-misreference.py --selftest
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / 'docs'
LEDGER = DOCS / '规则总账.md'

# 讨论分层本身的文档：引用草案文件是在登记/复盘其身份，不算错置。
EXEMPT_FILE_RE = re.compile(
    r'规则总账|规则冲突清单|台账-|复盘|全集|逻辑链|全部规则清单|形成史|更新史|'
    r'治理报告|清点|索引|必读|口径冻结|落文-规矩总纲|机制-规则'
)
# 命中行内的豁免语境词：该行在说草案作废/留档/冲突，不是拿它当依据。
EXEMPT_WORDS = re.compile(r'草案|作废|降级|留档|冲突|未接线|旧稿|语料|历史稿|禁作派单|三件套缺')
PATH_TOKEN_RE = re.compile(r'`?(docs/[\w\-/\\.]+\.(?:md|html))`?|`?([\w\-\\.]+\.(?:md|html))`?')
CONSUMER_GLOBS = [
    '派单队列.md', '派单台账.md', '问题清单.md',
]
REPORT_RE = re.compile(r'(修复|验收|实现|交付)-.*\.md$')


def parse_layer_list(ledger_text: str, start: str, end: str,
                     first_bullet_only: bool = False) -> list[str]:
    """从总账小节机械抽取反引号登记的文件路径（清单来源＝台账条目，不自拍板）。

    first_bullet_only：草案层小节的后续 bullet 是"事实依据/启用条件"散文，里面回指
    队列/台账/档位等文件属证据叙述，不是草案登记；只取首条登记 bullet，避免把
    证据散文里的权威消费方误并进草案清单。
    """
    si = ledger_text.find(start)
    if si < 0:
        return []
    ei = ledger_text.find(end, si)
    block = ledger_text[si:ei if ei > 0 else len(ledger_text)]
    bullets = [ln for ln in block.splitlines() if ln.strip().startswith('- ')]
    if first_bullet_only and bullets:
        bullets = bullets[:1]
    out: list[str] = []
    for ln in bullets:
        out.extend(re.findall(r'`([\w\-/\\.]+\.(?:md|html))`', ln))
    return out


def draft_files(ledger_text: str) -> set[str]:
    return {pathlib.Path(p).name for p in
            parse_layer_list(ledger_text, '### 10.3', '### 10.4', first_bullet_only=True)}


def authority_files(ledger_text: str) -> set[str]:
    return {pathlib.Path(p).name for p in parse_layer_list(ledger_text, '### 10.1', '### 10.2')}


def consumer_files(root: pathlib.Path) -> list[pathlib.Path]:
    out: list[pathlib.Path] = []
    docs = root / 'docs'
    for name in CONSUMER_GLOBS:
        p = docs / name
        if p.is_file():
            out.append(p)
    for p in docs.glob('*.md'):
        if REPORT_RE.match(p.name):
            out.append(p)
    return sorted(set(out))


def scan(root: pathlib.Path) -> tuple[int, list[str], list[str]]:
    ledger = root / 'docs' / '规则总账.md'
    if not ledger.is_file():
        return 3, [], ['缺 docs/规则总账.md（层属清单无来源，UNVERIFIED）']
    ledger_text = ledger.read_text(encoding='utf-8', errors='replace')
    drafts = draft_files(ledger_text)
    auth = authority_files(ledger_text)
    fails: list[str] = []
    notes: list[str] = []
    if not drafts:
        return 3, [], ['总账 §10.3 未登记草案层清单（UNVERIFIED）']
    for f in consumer_files(root):
        if EXEMPT_FILE_RE.search(f.name):
            continue
        text = f.read_text(encoding='utf-8', errors='replace')
        for lineno, line in enumerate(text.splitlines(), 1):
            if EXEMPT_WORDS.search(line):
                continue
            for m in PATH_TOKEN_RE.finditer(line):
                token = pathlib.Path(m.group(1) or m.group(2)).name
                if token in drafts:
                    rel = f.relative_to(root)
                    fails.append('%s:%s 草案层文件 `%s` 被消费方引用（未带草案/作废语境）'
                                 % (rel, lineno, token))
                    break
    notes.append('权威层 %d 份 / 草案层 %d 份（清单解析自规则总账.md 第十节）'
                 % (len(auth), len(drafts)))
    return (1 if fails else 0), fails, notes


def selftest() -> int:
    """双向自测：草案被当依据引用 → FAIL；带留档语境 → 豁免；权威文件引用 → PASS。"""
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='layer-misref-selftest-'))
    docs = tmp / 'docs'
    docs.mkdir(parents=True)
    ledger = (
        '# 规则总账\n\n### 10.1 权威层\n'
        '- `docs/并发档位.md`：槽位事实源。\n\n'
        '### 10.2 派生层\n- x。\n\n'
        '### 10.3 草案层\n'
        '- `docs/写码卡.md`、`docs/验收卡.md`、`docs/任务卡.md`。\n\n'
        '### 10.4 细则层\n- y。\n'
    )
    (docs / '规则总账.md').write_text(ledger, encoding='utf-8')
    # 坏样本：派单队列把草案写码卡当依据。
    (docs / '派单队列.md').write_text(
        '| Q-1 | 单 | 2026-11-28 | RUNNING | 无 | 依据 `docs/写码卡.md` 取卡 |\n',
        encoding='utf-8')
    code, fails, _ = scan(tmp)
    assert code == 1 and any('写码卡.md' in f for f in fails), f'草案被引用应 FAIL：{fails}'
    # 边界样本：同行带"草案层·未接线"留档语境，豁免。
    (docs / '派单队列.md').write_text(
        '| Q-2 | 单 | 2026-11-28 | DONE | 无 | `docs/写码卡.md` 系草案层·未接线，禁作派单依据 |\n',
        encoding='utf-8')
    code, fails, _ = scan(tmp)
    assert code == 0, f'留档语境应豁免：{fails}'
    # 好样本：引用权威层并发档位，不命中。
    (docs / '派单队列.md').write_text(
        '| Q-3 | 单 | 2026-11-28 | RUNNING | 无 | 槽位按 `docs/并发档位.md` |\n',
        encoding='utf-8')
    code, fails, _ = scan(tmp)
    assert code == 0 and not fails, f'权威引用应 PASS：{fails}'
    print('SELFTEST: PASS（草案当依据判红、留档语境豁免、权威引用放行）')
    import shutil
    shutil.rmtree(tmp)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description='层属错置门（G-11 / F-119）')
    ap.add_argument('--selftest', action='store_true')
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    code, fails, notes = scan(ROOT)
    print('== 层属错置门（G-11）==')
    for n in notes:
        print('   · ' + n)
    if code == 3:
        print('LAYER-MISREF: UNVERIFIED（%s）' % '；'.join(fails))
        return 3
    for f in fails:
        print('   ✗ ' + f)
    print('LAYER-MISREF: PASS' if code == 0 else 'LAYER-MISREF: FAIL')
    return code


if __name__ == '__main__':
    sys.exit(main())
