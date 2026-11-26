#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""规则总账门禁（F-89 工作模式三件套：总账 + 门禁 + 溯源）。

为什么需要：规则靠"记得"必然漏（2026-11-24 晚已三次实证：三档配比只落最低档、
台账冻结 8h45m 在飞计数恒 0、切片 21~25 漏登记长期无人发现）。本脚本把"规则是否
在册、冲突是否裁决、缺项是否逾期、旧口径是否还裸奔"变成机械判据，不靠记忆兜底。

五类判据：
  A. docs/规则总账.md 存在；每条登记行至少含「原话锚点」与「落点」两字段。
  B. 总账 F 编号覆盖：口径冻结里出现的 F-xx，总账编号列必须在册；
     允许缺条阈值 = 2（依据：F-83 为拍板指针条目、F-87 为旧铁律号，均未单立 F 条目，
     属已知在册指针；超出 2 条即真漏登）。
  C. docs/规则冲突清单.md：每条冲突必须写了「裁决」与「替代指向」（以 <条号/文件:行> 为准）。
  D. docs/台账-三件缺项.md：无超期项（期限早于今日且状态非"已补齐"即 FAIL）。
  E. 被否决串扫描：三布尔含 APK / ≤3 并发 / 竖排引擎 / 印章圆形 / 副图标 ic_launcher_small；
     白名单（流水留档、复检/审计/复盘/验收/候选/切片/索引/备案等证据性文档）与
     行内否定/作废标记豁免后，仍裸奔命中即报。

退出码：0 = RULE-LEDGER: PASS；1 = RULE-LEDGER: FAIL。
--selftest：造夹具双向跑通 PASS / FAIL 两条通路，跑完自删。
"""

import os
import subprocess
import re
import sys
import tempfile
import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
LEDGER = os.path.join(DOCS, "规则总账.md")
CONFLICT = os.path.join(DOCS, "规则冲突清单.md")
GAP = os.path.join(DOCS, "台账-三件缺项.md")
FREEZE = os.path.join(DOCS, "口径冻结-2026-11-24-15点后.md")

# 被否决串表（短语小写匹配；每条形如：显示名, 正则）
REJECTED = [
    ("三布尔含APK", re.compile(r"三布尔|有无\s*APK|APK\s*布尔", re.I)),
    ("≤3并发/三槽旧口径", re.compile(r"满编\s*3|满编三|最多三个并发|≤\s*3\s*并发|两个写一个查|3\s*槽", re.I)),
    ("竖排引擎旧叫法", re.compile(r"竖排引擎")),
    ("印章圆形/墨印正圆", re.compile(r"印章圆形|墨印正圆|印章类圆形|墨印图标", re.I)),
    ("副图标ic_launcher_small", re.compile(r"ic_launcher_small")),
]

# 行内豁免词：该行是否定句 / 作废说明 / 证据引述，不算裸奔
EXEMPT_WORDS = ["作废", "否决", "废弃", "弃", "旧称", "历史", "留档", "否定", "误判",
                "裁决", "旧口径", "被否决", "不再", "不许", "禁", "纠正", "修正",
                "取代", "弃用", "删除", "不制作", "取消", "驳回", "有意缺席",
                "分页引擎", "不成立", "误读", "澄清"]

# 文件级白名单：证据性 / 留档性文档，旧串只作回溯引用，不接新判定
WHITELIST_FILE = [
    "流水", "复检", "审计", "复盘", "验收-", "规格候选", "切片-", "契约索引",
    "备案", "空转盘点", "规则冲突清单", "规则总账", "工作模式", "三件缺项",
    "icon", "图标单卡", "归并", "通读结论", "自检", "巡检", "留档", "归档",
]

TODAY = datetime.date.today()
F_MISS_TOLERANCE = 2  # 依据见模块 docstring B 条


def _read(path):
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def parse_table_rows(text):
    """取 markdown 表格数据行（跳过表头与分隔行）。"""
    rows = []
    for line in text.splitlines():
        if not line.strip().startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if not cells:
            continue
        if set("".join(cells)) <= set("-: "):
            continue
        if cells[0] in ("编号", "---"):
            continue
        rows.append(cells)
    return rows


def check_ledger_existence(ledger_text):
    """A. 总账存在且每条含锚点 + 落点。返回 (ok, problems, row_count)。"""
    problems = []
    if ledger_text is None:
        return False, ["缺 docs/规则总账.md"], 0
    rows = parse_table_rows(ledger_text)
    if not rows:
        return False, ["总账无登记行"], 0
    for i, cells in enumerate(rows, 1):
        if len(cells) < 6:
            problems.append(f"第 {i} 行列数不足 6：{cells[:2]}")
            continue
        anchor, landing = cells[1], cells[3]
        if not anchor or anchor in ("—", "-"):
            problems.append(f"第 {i} 条缺原话锚点：{cells[0]}")
        if not landing or landing in ("—", "-"):
            problems.append(f"第 {i} 条缺落点：{cells[0]}")
    return (len(problems) == 0), problems, len(rows)


def check_f_coverage(ledger_text, freeze_text):
    """B. F 编号覆盖，超阈值即 FAIL。返回 (ok, missing, source_n, ledger_n)。"""
    if freeze_text is None:
        return False, ["缺口径冻结文件"], 0, 0
    src_ids = set(re.findall(r"F-\d{2}", freeze_text))
    if ledger_text is None:
        return False, sorted(src_ids), len(src_ids), 0
    ledger_ids = set(re.findall(r"F-\d{2}", ledger_text))
    missing = sorted(src_ids - ledger_ids, key=lambda x: int(x[2:]))
    ok = len(missing) <= F_MISS_TOLERANCE
    return ok, missing, len(src_ids), len(ledger_ids)


def check_conflicts(conflict_text):
    """C. 每条冲突都有裁决 + 替代指向。返回 (ok, problems, conflict_n)。"""
    problems = []
    if conflict_text is None:
        return False, ["缺 docs/规则冲突清单.md"], 0
    rows = parse_table_rows(conflict_text)
    if not rows:
        return False, ["冲突清单无登记行"], 0
    n = 0
    for i, cells in enumerate(rows, 1):
        # 表头若以「冲突编号」开头已被跳过；数据行第一列应为 C-xx
        if not re.match(r"C-\d+", cells[0]):
            continue
        n += 1
        verdict = cells[3] if len(cells) > 3 else ""
        repl = cells[4] if len(cells) > 4 else ""
        if not verdict:
            problems.append(f"{cells[0]} 缺裁决结论")
        if not re.search(r"以.{2,40}为准|F-?\d+|docs/|\S+\.kt:\d+|\S+\.md:\d+", repl + " " + verdict):
            problems.append(f"{cells[0]} 缺替代指向（以 X 为准）")
    return (len(problems) == 0), problems, n


def check_gap_overdue(gap_text):
    """D. 缺项台账无超期。返回 (ok, problems, gap_n)。"""
    problems = []
    if gap_text is None:
        return False, ["缺 docs/台账-三件缺项.md"], 0
    rows = parse_table_rows(gap_text)
    n = 0
    for cells in rows:
        if not re.match(r"G-\d+", cells[0]):
            continue
        n += 1
        # 列：编号 | 缺件 | 责任 | 期限 | 补齐前效力限制 | 状态
        if len(cells) < 6:
            problems.append(f"{cells[0]} 列数不足")
            continue
        deadline, status = cells[3], cells[5]
        m = re.search(r"(\d{4}-\d{2}-\d{2})", deadline)
        if not m:
            problems.append(f"{cells[0]} 期限无可解析日期：{deadline}")
            continue
        if "已补齐" in status:
            continue
        d = datetime.date.fromisoformat(m.group(1))
        if d < TODAY:
            problems.append(f"{cells[0]} 已超期（期限 {m.group(1)}，状态 {status}）")
    return (len(problems) == 0), problems, n


def scan_rejected(root=ROOT):
    """E. 否决串裸奔扫描。返回 (hits, total_scanned)。"""
    hits = []
    scanned = 0
    docs_dir = os.path.join(root, "docs")
    for dirpath, _, files in os.walk(docs_dir):
        for fn in files:
            if not fn.endswith(".md"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, root)
            scanned += 1
            if any(w in rel for w in WHITELIST_FILE):
                # 位置型豁免必须绑定"git 已跟踪"身份：未跟踪的新文件即使名字像白名单也得扫（F-122/㊼）
                if rel in _tracked_paths(root):
                    continue
            try:
                text = open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            for lineno, line in enumerate(text.splitlines(), 1):
                if any(w in line for w in EXEMPT_WORDS):
                    continue
                for name, pat in REJECTED:
                    if pat.search(line):
                        hits.append((rel, lineno, name, line.strip()[:80]))
    return hits, scanned


def run_checks(root=ROOT, groups=None):
    # groups: None=全部；"ledger"=A/B/C/E；"gap"=D
    ledger_text = _read(os.path.join(root, "docs", "规则总账.md"))
    freeze_text = _read(os.path.join(root, "docs", "口径冻结-2026-11-24-15点后.md"))
    conflict_text = _read(os.path.join(root, "docs", "规则冲突清单.md"))
    gap_text = _read(os.path.join(root, "docs", "台账-三件缺项.md"))

    a_ok, a_prob, n_rows = check_ledger_existence(ledger_text)
    b_ok, b_miss, n_src, n_led = check_f_coverage(ledger_text, freeze_text)
    c_ok, c_prob, n_conf = check_conflicts(conflict_text)
    d_ok, d_prob, n_gap = check_gap_overdue(gap_text)
    hits, scanned = scan_rejected(root)
    e_ok = len(hits) == 0

    counts = {
        "总账条目": n_rows,
        "冲突条数": n_conf,
        "缺项条数": n_gap,
        "否决串命中": len(hits),
    }
    problems = {
        "A总账结构": a_prob,
        "B编号覆盖": [f"缺登 {', '.join(b_miss)}" if b_miss else "无"] if not b_ok else [],
        "C冲突裁决": c_prob,
        "D缺项逾期": d_prob,
        "E否决串裸奔": [f"{h[0]}:{h[1]} [{h[2]}] {h[3]}" for h in hits],
    }
    results = {"A": a_ok, "B": b_ok, "C": c_ok, "D": d_ok, "E": e_ok}
    if groups == "ledger":
        active = ["A", "B", "C", "E"]
    elif groups == "gap":
        active = ["D"]
    else:
        active = ["A", "B", "C", "D", "E"]
    all_ok = all(results[k] for k in active)
    return all_ok, counts, problems, scanned


def selftest():
    """双向自测：合规夹具必须 PASS，缺锚点/超期/裸奔夹具必须 FAIL。"""
    tmp = tempfile.mkdtemp(prefix="rule-ledger-selftest-")
    os.makedirs(os.path.join(tmp, "docs"), exist_ok=True)
    os.makedirs(os.path.join(tmp, "data_scripts"), exist_ok=True)

    def w(rel, content):
        p = os.path.join(tmp, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write(content)

    freeze = "# 冻结\n- **F-01 x**\n- **F-02 y**\n"
    ledger_ok = (
        "# 总账\n\n"
        "| 编号 | 原话锚点 | 规则一句话 | 落点 | 状态 | 缺失项 |\n"
        "|---|---|---|---|---|---|\n"
        "| F-01 | 口径冻结-...md:8 | 进度两布尔 | 总纲:198 | ① | 无 |\n"
        "| F-02 | 口径冻结-...md:9 | 满编5槽 | rule-gate.sh:75 | ① | 无 |\n"
    )
    conflict_ok = (
        "# 冲突\n\n"
        "| 编号 | 冲突双方 | 冲突点 | 裁决 | 替代指向/需标记位置 |\n"
        "|---|---|---|---|---|\n"
        "| C-01 | a:1 vs b:2 | 口径对冲 | 以后令为准 | 以 F-01（docs/口径冻结-2026-11-24-15点后.md:8）为准 |\n"
    )
    gap_ok = (
        "# 缺项\n\n"
        "| 编号 | 缺件 | 责任 | 期限 | 补齐前效力限制 | 状态 |\n"
        "|---|---|---|---|---|---|\n"
        "| G-01 | 缺门禁 | 总控 | 2026-11-26 | 不得用于验收判定 | 登记中 |\n"
    )
    w("docs/口径冻结-2026-11-24-15点后.md", freeze)
    w("docs/规则总账.md", ledger_ok)
    w("docs/规则冲突清单.md", conflict_ok)
    w("docs/台账-三件缺项.md", gap_ok)

    ok, counts, problems, scanned = run_checks(tmp)
    assert ok, f"合规夹具应 PASS，实际 FAIL：{problems}"
    print("[selftest] 合规夹具 PASS 通路 OK：", counts)

    # FAIL 夹具：总账缺锚点列值、冲突缺裁决、缺项超期、否决串裸奔
    w("docs/规则总账.md", ledger_ok.replace("总纲:198", ""))
    w("docs/规则冲突清单.md", conflict_ok.replace("以后令为准", "待定"))
    w("docs/台账-三件缺项.md", gap_ok.replace("2026-11-26", "2026-11-20"))
    w("docs/临时-裸奔.md", "# 留档\n进度只报三布尔含 APK。\n")
    ok2, _, problems2, _ = run_checks(tmp)
    assert not ok2, "违规夹具应 FAIL，实际 PASS"
    fail_groups = [k for k, v in problems2.items() if v]
    print("[selftest] 违规夹具 FAIL 通路 OK，命中组：", fail_groups)
    assert set(["A总账结构", "B编号覆盖", "C冲突裁决", "D缺项逾期", "E否决串裸奔"]) & set(fail_groups)

    import shutil
    shutil.rmtree(tmp)
    print("[selftest] 夹具已自删，双向自测通过。")
    return 0


_TRACKED_CACHE = {}


def _tracked_paths(root='.'):
    """git 已跟踪集合（缓存）。位置型豁免必须绑定该身份，否则新建同名文件即可免检（F-122/㊼）。"""
    key = str(root)
    if key not in _TRACKED_CACHE:
        r = subprocess.run(['git', '-c', 'core.quotepath=false', 'ls-files'], cwd=root,
                           capture_output=True, text=True)
        _TRACKED_CACHE[key] = {x.strip() for x in r.stdout.splitlines() if x.strip()}
    return _TRACKED_CACHE[key]


def main():
    if "--selftest" in sys.argv:
        return selftest()
    groups = None
    if "--check" in sys.argv:
        groups = sys.argv[sys.argv.index("--check") + 1]
    all_ok, counts, problems, scanned = run_checks(groups=groups)
    active = {"ledger": ["A", "B", "C", "E"], "gap": ["D"]}.get(groups, ["A", "B", "C", "D", "E"])
    print("== 规则总账门禁（F-89 三件套）==")
    print(f"扫描文档 {scanned} 份；四类计数：总账条目 {counts['总账条目']} / "
          f"冲突 {counts['冲突条数']} / 缺项 {counts['缺项条数']} / "
          f"否决串命中 {counts['否决串命中']}")
    label_map = {"A": "A总账结构", "B": "B编号覆盖", "C": "C冲突裁决",
                 "D": "D缺项逾期", "E": "E否决串裸奔"}
    for key in active:
        group = label_map[key]
        items = problems[group]
        if items:
            print(f"-- {group}：")
            for it in items:
                print(f"   · {it}")
        else:
            print(f"-- {group}：无")
    print()
    if all_ok:
        print("RULE-LEDGER: PASS")
        return 0
    print("RULE-LEDGER: FAIL")
    return 1


if __name__ == "__main__":
    sys.exit(main())
