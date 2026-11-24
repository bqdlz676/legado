#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 ㉑ 组合验收覆盖守卫（F-92 组合验收金字塔，Q-17 落地）。

为什么存在：单模块自测全绿不等于拼在一起能用；历史返工多发生在咬合面
（参数/单位/坐标系/空值/时序）。本脚本把"组合矩阵是否入册、关键对是否 100%
覆盖、缺陷是否落层归因"变成机械判据，不靠记忆兜底。口径见
`docs/铁律-组合验收金字塔.md`；总账＝`docs/组合验收矩阵.md` +
`docs/台账-验收分层.md`。

FAIL 条件（退出码 1，本项只许 FAIL 不许 WARN）：
  A. 矩阵 / 台账文件缺失或不可解析；
  B. 注册表为空，或矩阵行引用了注册表外的模块 ID；
  C. 层 1 关键对：对 ID 重复、状态不在词表、已验行无锚点、待验/未落地行无验收代理；
  D. 台账 DEF 行：缺落层（层0~层4）、缺状态、缺锚点、引用了矩阵里不存在的模块/对；
  E. 台账引用的关键对（Pxx）在矩阵层 1 中无行（关键对未覆盖）。

状态词表：已验-PASS / 已验-有缺陷 / 待验 / 未落地（缺陷行另允许 返工中/阻塞/待重写/待真机）。
`--selftest`：造夹具跑 PASS / FAIL 两条通路后自删。
"""

import os
import re
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MATRIX = os.path.join(ROOT, "docs", "组合验收矩阵.md")
LEDGER = os.path.join(ROOT, "docs", "台账-验收分层.md")

OK_STATUS = {"已验-PASS", "已验-有缺陷", "待验", "未落地"}
DEF_STATUS_OK = OK_STATUS | {"返工中", "阻塞", "待重写", "待真机"}
LAYER_RE = re.compile(r"层\s*[0-4]")
MODULE_RE = re.compile(r"\b([A-Z]{1,3}\d{1,2}|MDL|WEB)\b")
PAIR_RE = re.compile(r"\b(P\d{2})\b")
# 域/链路 ID（L2-/L3-/L4-）在台账"涉及模块"列允许出现，不算未注册模块
DOMAIN_RE = re.compile(r"^L[234]-")


def _read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError:
        return None


def _split_row(line):
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    return cells


def _is_sep_row(cells):
    """markdown 表头分隔行 |---|---| 全由冒号/横线组成，不是数据行。"""
    return all(re.fullmatch(r":?-{2,}:?", c or "---") for c in cells)


def _parse_tables(text):
    """按 `## ` 小节切分，返回 {小节标题: [表头cells, 行cells...]}。"""
    sections = {}
    current = "__root__"
    for line in text.splitlines():
        if line.startswith("## "):
            current = line.strip("# ").strip()
            sections.setdefault(current, [])
            continue
        if line.strip().startswith("|"):
            cells = _split_row(line)
            if _is_sep_row(cells):
                continue
            sections.setdefault(current, []).append(cells)
    return sections


def _col(header, name):
    for i, h in enumerate(header):
        if name in h:
            return i
    return -1


def check(root=ROOT):
    """返回 (ok, problems, stats)。"""
    problems = []
    stats = {"pairs": 0, "landed": 0, "defects": 0}

    matrix_text = _read(os.path.join(root, "docs", "组合验收矩阵.md"))
    ledger_text = _read(os.path.join(root, "docs", "台账-验收分层.md"))
    if matrix_text is None:
        problems.append("缺 docs/组合验收矩阵.md（矩阵总账缺失）")
        return False, problems, stats
    if ledger_text is None:
        problems.append("缺 docs/台账-验收分层.md（缺陷归因台账缺失）")
        return False, problems, stats

    sections = _parse_tables(matrix_text)

    # --- 0. 模块注册表 ---
    registry = {}
    reg_rows = []
    for title, rows in sections.items():
        if "模块注册表" in title:
            reg_rows = rows
            break
    if not reg_rows:
        problems.append("矩阵 §0 模块注册表为空或不可解析")
    else:
        header = reg_rows[0]
        i_id, i_land = _col(header, "模块ID"), _col(header, "落地状态")
        for row in reg_rows[1:]:
            if len(row) <= max(i_id, i_land) or not row[i_id]:
                continue
            registry[row[i_id]] = row[i_land]
    if not registry:
        problems.append("注册表无有效模块行")

    # --- 1. 层 1 关键对 ---
    pair_ids = {}
    for title, rows in sections.items():
        if "层 1" not in title and "两两契约" not in title:
            continue
        if len(rows) < 2:
            continue
        header = rows[0]
        i_a = _col(header, "模块A")
        i_b = _col(header, "模块B")
        i_agent = _col(header, "验收代理")
        i_st = _col(header, "状态")
        i_anchor = _col(header, "产物")
        for row in rows[1:]:
            if len(row) < len(header) or not row[0]:
                continue
            pid = row[0]
            if pid in pair_ids:
                problems.append(f"关键对 {pid} 重复登记")
            pair_ids[pid] = row
            stats["pairs"] += 1
            # 模块必须在册
            for idx in (i_a, i_b):
                if idx >= 0 and row[idx] not in registry:
                    problems.append(f"{pid} 引用注册表外模块 {row[idx]}")
            # 状态词表
            st = row[i_st] if i_st >= 0 else ""
            if st not in OK_STATUS:
                problems.append(f"{pid} 状态非法：'{st}'（不在词表 {sorted(OK_STATUS)}）")
            # 已验行必须有锚点；待验/未落地必须有验收代理
            anchor = row[i_anchor] if i_anchor >= 0 else ""
            agent = row[i_agent] if i_agent >= 0 else ""
            if st in ("已验-PASS", "已验-有缺陷") and not anchor:
                problems.append(f"{pid} 已验但缺产物/锚点")
            if st in ("待验", "未落地") and not agent:
                problems.append(f"{pid} {st} 但缺验收代理（不许留空当放行）")

    if stats["pairs"] == 0:
        problems.append("层 1 关键对表为空：关键对 0 覆盖")

    # --- 2. 其余层表（链路/域/整机）状态与锚点通用校验 ---
    for title, rows in sections.items():
        if "层 1" in title or "两两契约" in title or "注册表" in title or "覆盖统计" in title:
            continue
        if len(rows) < 2:
            continue
        header = rows[0]
        i_st = _col(header, "状态")
        i_anchor = _col(header, "产物")
        for row in rows[1:]:
            if len(row) < len(header) or not row[0]:
                continue
            st = row[i_st] if i_st >= 0 else ""
            if st not in OK_STATUS:
                problems.append(f"{title} 行 {row[0]} 状态非法：'{st}'")
            anchor = row[i_anchor] if i_anchor >= 0 else ""
            if st in ("已验-PASS", "已验-有缺陷") and not anchor:
                problems.append(f"{row[0]} 已验但缺产物/锚点")

    # --- 3. 台账 DEF 归因 ---
    led_sections = _parse_tables(ledger_text)
    for title, rows in led_sections.items():
        if "归因表" not in title and "DEF" not in title:
            continue
        if len(rows) < 2:
            continue
        header = rows[0]
        i_layer = _col(header, "落层")
        i_mod = _col(header, "涉及模块")
        i_st = _col(header, "状态")
        i_anchor = _col(header, "锚点")
        for row in rows[1:]:
            if len(row) < len(header) or not row[0].startswith("DEF"):
                continue
            stats["defects"] += 1
            did = row[0]
            layer = row[i_layer] if i_layer >= 0 else ""
            if not LAYER_RE.search(layer):
                problems.append(f"{did} 未落层归因（落层列空或不在 层0~层4）")
            st = row[i_st] if i_st >= 0 else ""
            if st not in DEF_STATUS_OK:
                problems.append(f"{did} 状态非法：'{st}'")
            anchor = row[i_anchor] if i_anchor >= 0 else ""
            if not anchor:
                problems.append(f"{did} 缺产物路径/证据锚点")
            # 引用的模块必须在册；引用的关键对必须在矩阵层 1 有行；域 ID 放行
            mods = row[i_mod] if i_mod >= 0 else ""
            for tok in re.split(r"[·,，、\s/]+", mods):
                tok = tok.strip()
                if not tok:
                    continue
                if PAIR_RE.fullmatch(tok):
                    if tok not in pair_ids:
                        problems.append(f"{did} 引用关键对 {tok}，但矩阵层 1 无该行（关键对未覆盖）")
                    continue
                if DOMAIN_RE.match(tok):
                    continue
                m = MODULE_RE.fullmatch(tok)
                if m and m.group(1) not in registry:
                    problems.append(f"{did} 引用注册表外模块 {tok}")

    return (len(problems) == 0), problems, stats


def selftest():
    """双向自测：合规夹具 PASS；缺关键对 / 未归因缺陷 / 非法状态 / 缺锚点夹具 FAIL。"""
    tmp = tempfile.mkdtemp(prefix="combo-cov-selftest-")
    os.makedirs(os.path.join(tmp, "docs"), exist_ok=True)

    good_matrix = """# 组合验收矩阵（自测夹具）

## 0. 模块注册表
| 模块ID | 名称 | 文件域 | 落地状态 |
|---|---|---|---|
| D1 | 字典 | help/dict/ | 已落地 |
| C2 | 气泡解析 | help/bubble/ | 已落地 |
| U5 | 阅读页 | ui/book/read/ink/ | 已落地 |

## 1. 层 1 · 两两契约关键对
| 对ID | 模块A | 模块B | 组合场景与期望结果 | 契约维度 | 验收代理 | 状态 | 产物/锚点 |
|---|---|---|---|---|---|---|---|
| P01 | D1 | C2 | 字典喂气泡 | 接口参数 | 待派独立验收代理 | 待验 | help/dict/ |
| P02 | C2 | U5 | 气泡模型消费 | 类型唯一 | 静态验收代理 | 已验-有缺陷 | ui/book/read/ink/InkBubble.kt |

## 2. 层 2 · 业务链路
| 链路ID | 成员模块 | 链路场景与期望结果 | 验收代理 | 状态 | 产物/锚点 |
|---|---|---|---|---|---|
| L2-A | U5 | 阅读闭环 | 待派独立验收代理 | 待验 | ui/book/read/ink/ |
"""
    good_ledger = """# 台账（自测夹具）

## 1. 缺陷 / 阻塞项归因表
| DEF | 标题 | 落层 | 涉及模块(矩阵ID) | 状态 | 验收代理 id | 产物路径 / 证据锚点 | 退回层级与下一步 |
|---|---|---|---|---|---|---|---|
| DEF-01 | 气泡链路缺陷 | 层1 | C2 · U5 · P02 | 已验-有缺陷 | 静态验收代理 | ui/book/read/ink/ | 回层1 复验 |
"""
    with open(os.path.join(tmp, "docs", "组合验收矩阵.md"), "w", encoding="utf-8") as f:
        f.write(good_matrix)
    with open(os.path.join(tmp, "docs", "台账-验收分层.md"), "w", encoding="utf-8") as f:
        f.write(good_ledger)

    ok, problems, stats = check(tmp)
    assert ok, f"合规夹具应 PASS，实际 FAIL：{problems}"
    print(f"[selftest] 合规夹具 PASS 通路 OK：{stats}")

    # FAIL 通路 1：台账引用了矩阵不存在的关键对 P99（关键对未覆盖）
    bad_ledger = good_ledger.replace("P02", "P99")
    with open(os.path.join(tmp, "docs", "台账-验收分层.md"), "w", encoding="utf-8") as f:
        f.write(bad_ledger)
    ok2, problems2, _ = check(tmp)
    assert not ok2, "缺关键对样本应 FAIL，实际 PASS"
    print("[selftest] 缺关键对 FAIL 通路 OK，命中：", problems2)

    # FAIL 通路 2：缺陷未落层归因
    bad_ledger2 = good_ledger.replace("层1", "")
    with open(os.path.join(tmp, "docs", "台账-验收分层.md"), "w", encoding="utf-8") as f:
        f.write(bad_ledger2)
    ok3, problems3, _ = check(tmp)
    assert not ok3, "未归因缺陷应 FAIL，实际 PASS"
    print("[selftest] 未归因缺陷 FAIL 通路 OK，命中：", problems3)

    # FAIL 通路 3：非法状态词 + 已验行缺锚点
    bad_matrix = good_matrix.replace("已验-有缺陷 | ui/book/read/ink/InkBubble.kt", "差不多 | ")
    with open(os.path.join(tmp, "docs", "组合验收矩阵.md"), "w", encoding="utf-8") as f:
        f.write(bad_matrix)
    with open(os.path.join(tmp, "docs", "台账-验收分层.md"), "w", encoding="utf-8") as f:
        f.write(good_ledger)
    ok4, problems4, _ = check(tmp)
    assert not ok4, "非法状态/缺锚点样本应 FAIL，实际 PASS"
    print("[selftest] 非法状态+缺锚点 FAIL 通路 OK，命中：", problems4)

    import shutil
    shutil.rmtree(tmp, ignore_errors=True)
    print("[selftest] 夹具已自删，双向自测通过。")
    return 0


def main():
    if "--selftest" in sys.argv:
        return selftest()
    ok, problems, stats = check()
    if ok:
        print(f"COMBO-COV: PASS（关键对 {stats['pairs']} 条，缺陷 {stats['defects']} 条均已落层归因）")
        return 0
    print("COMBO-COV: FAIL")
    for p in problems:
        print("  · " + p)
    return 1


if __name__ == "__main__":
    sys.exit(main())
