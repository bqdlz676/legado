#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 g72 · 计数漂移门（G-10 / F-118 规则计数单点）。

为什么立这条（见 docs/台账-三件缺项.md G-10、docs/规则总账.md F-118、
docs/规则冲突清单.md C-09）：
  同一事实"规则有多少条"曾在四处各写一套：`docs/规则全集.md:5` 269、
  `docs/规则治理报告-2026-11-25.md:85` 265、`docs/落文-规矩总纲.md:5` 计数行 63/107、
  `docs/规则总账.md` F 编号累计。此前没有脚本扫"多少条"陈述与总账实算对账，派生件
  条数漂移无门禁拦截。F-118 立的口径：现行计数只在总纲:5 计数行声明一次，其余文档
  一律引用；派生件数字必须带「派生·只读 / 快照」限定，且不得当现行数引用。

判据（四处抽数 + 可实算处对账；不一致逐处打印 `文件:行 数值`）：
  A. 总纲:5 内部算术：A8+B14+C10+D8+E14+F7+G2 之和必须等于行内声明的 63；
     63 + P-20 + 段内 24 必须等于行内声明的 107。权威层算术自相矛盾 → FAIL。
  B. 总账 F 实算：§一 表在册 F-xx 去重计数；与派生文档声明的 F 累计数对账
     （如 `docs/全部规则清单.md:123`「（112 条）」、治理报告「112 总账」）。
     声明数 ≠ 实算数，且该派生行未带快照/派生·只读/历史/定格限定词 → FAIL
     （无限定的漂移会被当成现行数引用）；带限定词的历史快照记 INFO 留痕不判红。
  C. 治理报告分解式：63+20+24+112+39+7 必须等于行内定格总数 265。
  D. 四处抽数表始终打印（文件:行 数值），供人工对账；本闸只读不写，
     【禁止自动改数字修平】——发现漂移只报不改。

适用边界 / 误判面 / 不能替代什么：
  - C-09 已裁决 89/269/265/112 四说是不同口径的历史快照，本闸不强行要求四个总数
    相等；只判权威层算术、实算对账、限定词缺失三类硬漂移。
  - 只对账 F 编号累计与 A~G/P/段内分解；门禁项数、载体数等其他口径不在本闸范围。
  - 快照限定词靠关键词匹配，措辞翻新可能漏标；漏标由收口全量 gate 与人工复核兜底。
  - 本闸不替代 ㉓ 总账结构与编号覆盖检查（check-rule-ledger.py B 组）。

退出码：0=PASS | 1=FAIL | 2=参数错
用法：
  python3 data_scripts/check-count-drift.py
  python3 data_scripts/check-count-drift.py --selftest
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / 'docs'
ZONGGANG = DOCS / '落文-规矩总纲.md'
QUANJI = DOCS / '规则全集.md'
ZHILI = DOCS / '规则治理报告-2026-11-25.md'
ZHANG = DOCS / '规则总账.md'
QUANBU = DOCS / '全部规则清单.md'

SNAPSHOT_WORD_RE = re.compile(r'派生|只读|快照|历史|定格|留档')


def find_line(path: pathlib.Path, pattern: str) -> tuple[int, str]:
    """按内容定位计数行，返回 (行号, 行文本)；找不到返回 (0, '')。

    为什么不写死行号：计数行随插入漂移（M-33 同类问题），按标记词定位比按行号稳；
    打印时仍回带真实行号供回源。
    """
    if not path.is_file():
        return 0, ''
    rx = re.compile(pattern)
    for i, line in enumerate(path.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
        if rx.search(line):
            return i, line
    return 0, ''


def zonggang_segments(text: str) -> dict:
    """解析总纲:5 的 A8/B14/... 段内条数与声明总数。

    段内表按斜杠切 token 后取每个 token 开头的「字母+数字」；不能在整行上裸扫
    字母加数字——括号补充语里的 E1~E13 / E10b 会被误当成段内条数。
    全量分解：63（A~G）+ 20（P-01~P-20）+ 24（R/S/M/N/T 段内）= 107。
    """
    seg: dict[str, int] = {}
    for tok in text.split('/'):
        m = re.search(r'(?<![A-Za-z])([A-G])(\d+)', tok)
        if m:
            seg[m.group(1)] = int(m.group(2))
    total = re.search(r'A~G 共\s*(\d+)\s*条', text)
    iron = re.search(r'共\s*(\d+)\s*条＝\s*(\d+)\s*条\s*\+\s*P-01~P-20\s*\+\s*R/S/M/N/T\s*(\d+)\s*条', text)
    p_count = re.search(r'P-01~P-(\d+)', text)
    return {
        'segments': seg,
        'declared_total': int(total.group(1)) if total else None,
        'iron_total': int(iron.group(1)) if iron else None,
        'iron_base': int(iron.group(2)) if iron else None,
        'iron_p': int(p_count.group(1)) if p_count else None,
        'iron_extra': int(iron.group(3)) if iron else None,
    }


def ledger_f_count() -> int:
    """总账 §一 表在册 F-xx 去重计数（实算值）。"""
    if not ZHANG.is_file():
        return 0
    in_sec = False
    ids: set[str] = set()
    for line in ZHANG.read_text(encoding='utf-8', errors='replace').splitlines():
        if line.startswith('## 一、'):
            in_sec = True
            continue
        if line.startswith('## 二、'):
            in_sec = False
        if in_sec and line.startswith('| F-'):
            ids |= set(re.findall(r'F-\d+', line))
    return len(ids)


def zhilin_decomp(text: str) -> dict:
    """治理报告分解式：63 铁律 + 20 拍板 + 24 段内 + 112 总账 + 39 门禁 + 7 载体 = 265。"""
    nums = re.findall(r'(\d+)\s*(?:铁律|拍板|段内|总账|门禁|载体)', text)
    total = re.search(r'=\s*(\d+)', text)
    return {'parts': [int(x) for x in nums],
            'declared_total': int(total.group(1)) if total else None}


def declared_f_in_quanbu() -> list[tuple[int, int, str]]:
    """全部规则清单里声明的 F 累计数 → [(行号, 数值, 行文本)]。"""
    out: list[tuple[int, int, str]] = []
    if not QUANBU.is_file():
        return out
    for i, line in enumerate(QUANBU.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
        m = re.search(r'F 条（\s*(\d+)\s*条）', line)
        if m:
            out.append((i, int(m.group(1)), line.strip()))
    return out


def run(root: pathlib.Path) -> tuple[int, list[str], list[str]]:
    """返回 (退出码, FAIL 项, INFO 留痕)。"""
    global ZONGGANG, QUANJI, ZHILI, ZHANG, QUANBU
    ZONGGANG = root / 'docs/落文-规矩总纲.md'
    QUANJI = root / 'docs/规则全集.md'
    ZHILI = root / 'docs/规则治理报告-2026-11-25.md'
    ZHANG = root / 'docs/规则总账.md'
    QUANBU = root / 'docs/全部规则清单.md'

    fails: list[str] = []
    infos: list[str] = []

    # 四处抽数表（按标记词定位行，行号随实读回带）
    zg_no, zg_line = find_line(ZONGGANG, r'A~G 共\s*\d+\s*条')
    qj_no, qj_line = find_line(QUANJI, r'\d+\s*条规则')
    zl_no, zl_line = find_line(ZHILI, r'铁律\s*\+')
    qj_m = re.search(r'(\d+)\s*条规则', qj_line)
    infos.append('抽数 规则全集.md:%s 数值=%s' % (qj_no or '?', qj_m.group(1) if qj_m else '未抽到'))

    # A. 总纲权威层算术
    zg = zonggang_segments(zg_line)
    seg_sum = sum(zg['segments'].values())
    if zg['declared_total'] is not None:
        infos.append('抽数 落文-规矩总纲.md:%s A~G 声明=%s 实算分段和=%s'
                      % (zg_no or '?', zg['declared_total'], seg_sum))
        if seg_sum != zg['declared_total']:
            fails.append('落文-规矩总纲.md:%s 分段和 %s ≠ 声明总数 %s（%s）'
                         % (zg_no or '?', seg_sum, zg['declared_total'], zg['segments']))
    if zg['iron_total'] is not None:
        iron_sum = (zg['iron_base'] or 0) + (zg['iron_p'] or 0) + (zg['iron_extra'] or 0)
        infos.append('抽数 落文-规矩总纲.md:%s 铁律全量声明=%s 实算=%s' % (zg_no or '?', zg['iron_total'], iron_sum))
        if iron_sum != zg['iron_total']:
            fails.append('落文-规矩总纲.md:%s 全量 %s ≠ 分解 %s+%s+%s=%s'
                         % (zg_no or '?', zg['iron_total'], zg['iron_base'], zg['iron_p'], zg['iron_extra'], iron_sum))

    # C. 治理报告分解式
    zl = zhilin_decomp(zl_line)
    if zl['declared_total'] is not None and zl['parts']:
        parts_sum = sum(zl['parts'])
        infos.append('抽数 规则治理报告-2026-11-25.md:%s 声明=%s 实算分解和=%s'
                      % (zl_no or '?', zl['declared_total'], parts_sum))
        if parts_sum != zl['declared_total']:
            fails.append('规则治理报告-2026-11-25.md:%s 分解和 %s ≠ 定格总数 %s'
                         % (zl_no or '?', parts_sum, zl['declared_total']))

    # B. 总账 F 实算 vs 派生声明
    actual_f = ledger_f_count()
    infos.append('抽数 规则总账.md §一 F 编号实算=%s' % actual_f)
    for lineno, declared, text in declared_f_in_quanbu():
        tagged = bool(SNAPSHOT_WORD_RE.search(text))
        infos.append('抽数 全部规则清单.md:%s 声明 F=%s（快照限定词：%s）'
                     % (lineno, declared, '有' if tagged else '无'))
        if declared != actual_f:
            if tagged:
                infos.append('INFO 全部规则清单.md:%s 声明 %s 与实算 %s 不符，已带快照限定，按历史快照留痕'
                              % (lineno, declared, actual_f))
            else:
                fails.append('全部规则清单.md:%s 声明 F 累计 %s ≠ 总账实算 %s，且行内无快照/派生·只读限定词'
                             % (lineno, declared, actual_f))

    return (1 if fails else 0), fails, infos


def selftest() -> int:
    """双向自测：一致夹具 PASS；总纲分段和对不上 / 派生无限定漂移必须 FAIL。"""
    import shutil
    tmp = pathlib.Path(__import__('tempfile').mkdtemp(prefix='count-drift-selftest-'))
    (tmp / 'docs').mkdir(parents=True)

    good_zg = ('> 现行计数：A~G 共 63 条（A8 / B14 / C10 / D8 / E14 / F7 / G2）；'
               '共 107 条＝63 条 + P-01~P-20 + R/S/M/N/T 24 条。\n')
    good_zl = '规则总数 265（63 铁律 + 20 拍板 + 24 段内 + 112 总账 + 39 门禁 + 7 载体 = 265）。\n'
    good_ledger = '## 一、口径冻结 F 条\n| F-01 | x |\n| F-02 | y |\n## 二、其他\n'
    good_quanbu = '## 四、规则总账 F 条（2 条）（派生·只读快照）\n'
    good_qj = '以及 269 条规则的逐条迁移。\n'

    def write(name: str, text: str) -> None:
        (tmp / 'docs' / name).write_text(text, encoding='utf-8')

    write('落文-规矩总纲.md', good_zg)
    write('规则全集.md', good_qj)
    write('规则治理报告-2026-11-25.md', good_zl)
    write('规则总账.md', good_ledger)
    write('全部规则清单.md', good_quanbu)

    code, fails, _ = run(tmp)
    assert code == 0, f'一致夹具应 PASS，实际 FAIL：{fails}'

    # 坏样本 1：总纲分段和 8+14+10+8+14+7+2=63 改成声明 64。
    write('落文-规矩总纲.md', good_zg.replace('共 63 条', '共 64 条'))
    code, fails, _ = run(tmp)
    assert code == 1 and any('分段和' in f for f in fails), f'总纲算术漂移应 FAIL：{fails}'
    # 坏样本 2：派生声明 99 且无快照限定词，实算 2 → FAIL。
    write('落文-规矩总纲.md', good_zg)
    write('全部规则清单.md', '## 四、规则总账 F 条（99 条）\n')
    code, fails, _ = run(tmp)
    assert code == 1 and any('无快照' in f for f in fails), f'无限定漂移应 FAIL：{fails}'
    print('SELFTEST: PASS（一致夹具 PASS；总纲算术漂移、派生无限定漂移两类坏样本均判红）')
    shutil.rmtree(tmp)
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description='计数漂移门（G-10 / F-118）')
    ap.add_argument('--selftest', action='store_true')
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    code, fails, infos = run(ROOT)
    print('== 计数漂移门（G-10）==')
    for i in infos:
        print('   · ' + i)
    for f in fails:
        print('   ✗ ' + f)
    print('COUNT-DRIFT: PASS' if code == 0 else 'COUNT-DRIFT: FAIL')
    return code


if __name__ == '__main__':
    sys.exit(main())
