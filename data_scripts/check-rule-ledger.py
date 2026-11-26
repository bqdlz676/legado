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
     引述豁免（2026-11-26 ㉓ 假红收口；Q-217 收紧；Q-221 补回归语境）：命中串整段落在同行配对引号/反引号内
     （「」『』“”"`）且本行带位置回指词（未动/改述/回指/旧段落名/原文/引述/改名）或回归语境词
     （回归/selftest/夹具记录/夹具/旧口径引述），视为回溯引用或夹具留档，不报红；
     只有引号没有语境词、或引号外的同串，仍必须红。
     代码块豁免：``` 围栏内为逐字转写/夹具留档区，E 组不扫；围栏按行配对，未闭合则块态保持
     到文件尾；行内反引号不走围栏逻辑，仍按引号双条件判。

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
    # "3 槽"必须不是数字的一部分：否则"在飞 23 槽"里的"3 槽"会被抓成旧口径（实测假红）。
    ("≤3并发/三槽旧口径", re.compile(r"满编\s*3|满编三|最多三个并发|≤\s*3\s*并发|两个写一个查|(?<![0-9])3\s*槽", re.I)),
    ("竖排引擎旧叫法", re.compile(r"竖排引擎")),
    ("印章圆形/墨印正圆", re.compile(r"印章圆形|墨印正圆|印章类圆形|墨印图标", re.I)),
    ("副图标ic_launcher_small", re.compile(r"ic_launcher_small")),
]

# 行内豁免词：该行是否定句 / 作废说明 / 证据引述，不算裸奔
EXEMPT_WORDS = ["作废", "否决", "废弃", "弃", "旧称", "历史", "留档", "否定", "误判",
                "裁决", "旧口径", "被否决", "不再", "不许", "禁", "纠正", "修正",
                "取代", "弃用", "删除", "不制作", "取消", "驳回", "有意缺席",
                "分页引擎", "不成立", "误读", "澄清"]

# 引述豁免（2026-11-26 ㉓ 假红收口；Q-217 收紧为双条件）：
# 为什么加：实测两类假红——① 两位数计数「在飞 23 槽」被裸子串 3+槽 误抓（已由数字边界收口）；
#   ② 变更说明里用引号回指旧段落名（如「有无 APK」行一字未动），旧串在引号内、不是现行规则，
#   却被当成裸奔旧条文。
# 豁免双条件（缺一不可，防"含引号就放过"的后门）：
#   1) 命中串整段落在同行配对引号/反引号区间内；
#   2) 本行同时带位置回指词（未动 / 改述 / 回指 / 旧段落名 / 原文 / 引述 / 改名）。
# 边界在哪：
#   - 引号按当前行配对，不支持跨行、不支持嵌套；开引号后同行找不到闭引号 → 不成对，不豁免（fail-closed）。
#   - 只豁免命中串所在的那个区间，不豁免整行；同一行引号外再出现同串仍判红。
#   - 只有引号、没有回指词的行不豁免：把旧规则包进引号照样抓，防止挂引号绕过。
# 误判风险：回指词表若过宽会放过伪装引述；故词表只收"位置/沿革回指"语义，不收否定词
#   （否定语义归 EXEMPT_WORDS 管）。文件白名单与行内豁免词照旧叠加。
QUOTE_PAIRS = [("「", "」"), ("『", "』"), ("“", "”"), ('"', '"'), ("`", "`")]
# 位置回指词：本句在回指旧文/旧名/旧段落；必须与"命中串在引号内"同时成立才豁免。
# Q-221 增补回归语境词：selftest/回归/夹具记录/旧口径引述行是在记录"旧串曾被抓到"，
# 旧串本身在引号内作夹具样例，不是现行规则；仍守双条件（引号内 + 语境词），不得整行免检。
QUOTE_REF_WORDS = ["一字未动", "未动", "改述", "回指", "旧段落名", "原文", "引述", "改名",
                   "回归", "selftest", "夹具记录", "夹具", "旧口径引述"]


def _quote_spans(line):
    """行内配对引号的内容区间 [(start, end), ...]，左到右非重叠；不成对的引号忽略。"""
    spans = []
    i, n = 0, len(line)
    while i < n:
        opener = next((op for op, _ in QUOTE_PAIRS if line[i] == op), None)
        if opener is None:
            i += 1
            continue
        closer = dict(QUOTE_PAIRS)[opener]
        j = line.find(closer, i + 1)
        if j == -1:
            i += 1
            continue
        spans.append((i + 1, j))
        i = j + 1
    return spans


def _span_quoted(line, start, end):
    """命中区间 [start, end) 是否整段落在某对引号区间内。"""
    return any(s <= start and end <= e for s, e in _quote_spans(line))


def _is_quote_exempt(line, start, end):
    """引述豁免双条件：命中串在引号/反引号内，且本行带位置回指词；缺一不豁免。"""
    return _span_quoted(line, start, end) and any(w in line for w in QUOTE_REF_WORDS)

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
    """E. 否决串裸奔扫描。返回 (hits, total_scanned, exempts)。
    exempts：命中串满足"引号内 + 位置回指词"双条件被豁免的条目，仅留痕打印，不报红。"""
    hits = []
    exempts = []
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
            in_fence = False  # ``` 块内是逐字转写/夹具区，不代表现行规则（见模块注释 E 条）
            for lineno, line in enumerate(text.splitlines(), 1):
                if line.lstrip().startswith("```"):
                    in_fence = not in_fence
                    continue
                if in_fence:
                    continue
                if any(w in line for w in EXEMPT_WORDS):
                    continue
                for name, pat in REJECTED:
                    bare_hit = None
                    for m in pat.finditer(line):
                        if _is_quote_exempt(line, m.start(), m.end()):
                            # 双条件豁免：引号内 + 位置回指词，是回溯引用，留痕不报红
                            exempts.append((rel, lineno, name, line.strip()[:80]))
                            continue
                        bare_hit = m
                        break
                    if bare_hit is not None:
                        hits.append((rel, lineno, name, line.strip()[:80]))
                        break
    return hits, scanned, exempts


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
    hits, scanned, exempts = scan_rejected(root)
    e_ok = len(hits) == 0

    counts = {
        "总账条目": n_rows,
        "冲突条数": n_conf,
        "缺项条数": n_gap,
        "否决串命中": len(hits),
        "引述豁免": len(exempts),
    }
    problems = {
        "A总账结构": a_prob,
        "B编号覆盖": [f"缺登 {', '.join(b_miss)}" if b_miss else "无"] if not b_ok else [],
        "C冲突裁决": c_prob,
        "D缺项逾期": d_prob,
        "E否决串裸奔": [f"{h[0]}:{h[1]} [{h[2]}] {h[3]}" for h in hits],
        "E引述豁免": [f"{x[0]}:{x[1]} [{x[2]}] {x[3]}" for x in exempts],
    }
    results = {"A": a_ok, "B": b_ok, "C": c_ok, "D": d_ok, "E": e_ok}
    if groups in ("ledger", "rule"):
        active = ["A", "B", "C", "E"]
    elif groups == "gap":
        active = ["D"]
    elif groups == "vocab":
        active = ["E"]
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
    # 引述豁免回归夹具（2026-11-26 ㉓ 假红收口；Q-217 双条件收紧）：
    # (a) 两位数槽位计数「在飞 23 槽」不得被判成并发上限旧口径；
    # (b) 引号内旧段落名回指「有无 APK」且带回指词"一字未动"，不算裸奔。
    w("docs/自检-引述豁免.md",
      "# 引述豁免回归\n"
      "- 在飞 23 槽：Q-63 点字详情改走半屏。\n"
      "- 其余章节（含「有无 APK」行）一字未动。\n"
      "- [selftest] 回归 (c) 裸奔「满编 3 槽」FAIL 通路 OK。\n"
      "```\n"
      "进度满编 3 槽，两个写一个查。\n"
      "```\n")

    ok, counts, problems, scanned = run_checks(tmp)
    assert ok, f"合规夹具应 PASS，实际 FAIL：{problems}"
    assert not problems["E否决串裸奔"], f"引述/计数夹具不应命中 E 组：{problems['E否决串裸奔']}"
    assert any("满编 3 槽" in x and "selftest" in x for x in problems["E引述豁免"]), \
        f"回归语境+引号内夹具应走引述豁免留痕：{problems['E引述豁免']}"
    print("[selftest] 合规夹具 PASS 通路 OK：", counts)
    print("[selftest] 回归 (a)「在飞 23 槽」计数 PASS；(b) 引号内「有无 APK」+回指词引述 PASS；"
          "(f) 引号内「满编 3 槽」+selftest/回归语境 PASS；(e) 围栏内转写 PASS")

    # FAIL 夹具：总账缺锚点列值、冲突缺裁决、缺项超期、否决串裸奔
    w("docs/规则总账.md", ledger_ok.replace("总纲:198", ""))
    w("docs/规则冲突清单.md", conflict_ok.replace("以后令为准", "待定"))
    w("docs/台账-三件缺项.md", gap_ok.replace("2026-11-26", "2026-11-20"))
    w("docs/临时-裸奔.md", "# 留档\n进度只报三布尔含 APK。\n")
    # (c) 裸奔旧条文：引号外的「只许 3 槽」必须 FAIL，引述豁免不得放行。
    w("docs/自检-裸奔槽位.md", "进度只许 3 槽，两个写一个查。\n")
    # (d) 反后门夹具：旧串包在引号内但本行无位置回指词，仍必须 FAIL（不得"含引号就放过"）。
    w("docs/自检-引号无回指.md", "正文沿用「有无 APK」作为小节标题。\n")
    ok2, _, problems2, _ = run_checks(tmp)
    assert not ok2, "违规夹具应 FAIL，实际 PASS"
    fail_groups = [k for k, v in problems2.items() if v]
    print("[selftest] 违规夹具 FAIL 通路 OK，命中组：", fail_groups)
    assert set(["A总账结构", "B编号覆盖", "C冲突裁决", "D缺项逾期", "E否决串裸奔"]) & set(fail_groups)
    e_hits = problems2["E否决串裸奔"]
    assert any("只许 3 槽" in h for h in e_hits), f"裸奔槽位夹具必须被 E 组抓到：{e_hits}"
    assert any("有无 APK" in h for h in e_hits), f"引号内无回指词的反后门夹具必须被抓到：{e_hits}"
    print("[selftest] 回归 (c) 裸奔「只许 3 槽」FAIL 通路 OK；(d) 引号无回指词反后门 FAIL 通路 OK")

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
    active = {"ledger": ["A", "B", "C", "E"], "rule": ["A", "B", "C", "E"],
              "gap": ["D"], "vocab": ["E"]}.get(groups, ["A", "B", "C", "D", "E"])
    print("== 规则总账门禁（F-89 三件套）==")
    print(f"扫描文档 {scanned} 份；四类计数：总账条目 {counts['总账条目']} / "
          f"冲突 {counts['冲突条数']} / 缺项 {counts['缺项条数']} / "
          f"否决串命中 {counts['否决串命中']}（引述豁免 {counts['引述豁免']}）")
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
    if "E" in active and problems["E引述豁免"]:
        print(f"-- E引述豁免（引号内回指，不报红）：{len(problems['E引述豁免'])} 处")
        for it in problems["E引述豁免"]:
            print(f"   · EXEMPT {it}")
    print()
    if all_ok:
        print("RULE-LEDGER: PASS")
        return 0
    print("RULE-LEDGER: FAIL")
    return 1


if __name__ == "__main__":
    sys.exit(main())
