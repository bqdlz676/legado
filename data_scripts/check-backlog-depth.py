#!/usr/bin/env python3
"""门禁 ㊸ 验收积压深度守卫（M-09 收口）。

为什么需要这条：写码单不断交、验收槽只有 1 个（档1 = 4 写 + 1 查，F-02/F-84），
"已修但未验收"的单子会无限堆积，CI 单链长期挂"未验证"，最后分不清哪些真验过。
旧门禁没有积压深度判据，也没有"待验收超期"判据，本项把两件事一起机械兜住。

判据（双阈值，任一超限即 FAIL；退出码 1）：
  D1 积压行数：当前"可立即送验收"的待验单 > MAX_PENDING 条 → FAIL。
  D2 最老时长：可立即送验收的单子里，最老一条存活 > MAX_AGE_HOURS 小时 → FAIL。

计数口径（只数"已修·待验证 / 已判·待验证"族，不数未开工/在飞/已关闭/作废）：
  - `docs/问题清单.md`：状态单元格含 🟢 且带待验关键词（待验证/待验/待派验收/
    待独立验收/已判·待验证）；✅ 已关闭、🟡 在飞、🔴 未派、⛔ 作废不计。
  - `docs/派单台账.md`：状态单元格含 🟢 或"已判·待验证"字样；状态格已带 ✅ 的不计。
  - 两源按归一化编号去重，同一号只计一次。
  - 外部阻塞单列：状态/归属/级别格含"待 CI / CI 待绿 / 待云端 CI / L2 待 CI"等字样的，
    属 CI 阻塞而非验收槽积压，不计入 D1/D2，只在报告里单列条数——否则把等 CI 的单
    算成验收欠账，会误逼调度层去抢验收槽。

阈值与理由（依据 F-02/F-84 框架硬顶 5 槽、Q-22 单代理时限）：
  - MAX_PENDING = 10：档1 验收槽仅 1 个；按一单验收 ≤60 次工具预算、约 30~60 分钟
    一轮估算，10 单 ≈ 一个工作轮次（4~5 小时）的验收槽周转量；超过即说明写码在堆、
    验收槽被挤占，调度层应先清验收再发新写码单。升档到档2/档3（2~3 查槽）后，
    阈值可按查槽数同比上调，改这里常量即可。
  - MAX_AGE_HOURS = 48：约两个完整轮次；一条待验单挂过 48h 还没排上验收，基本是被
    遗忘的僵尸积压，必须亮出红牌，而不是继续混在待验堆里。

误判风险与兜底：
  - 清单行无行内日期：年龄回退到该行关联 `docs/...md` 报告文件的 mtime；取不到日期
    的单计入行数但不参与最老时长判定（unknown 单列），不因缺日期误红。
  - 台账日期仅 `MM-DD HH:mm`（分钟可能是 x）：按当前年补全，分钟缺失按 0 分；解析出
    未来时间则回退一年。
  - 聚合行（如 C1/C2 无单缺陷号）无法归一编号：计入"未编号"桶只提示，不参与年龄判定。
"""
import datetime
import os
import re
import sys
import tempfile
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ISSUE = os.path.join(ROOT, "docs", "问题清单.md")
LEDGER = os.path.join(ROOT, "docs", "派单台账.md")

# 阈值（理由见模块 docstring；升档扩查槽后同比上调）。
MAX_PENDING = 10
MAX_AGE_HOURS = 48
# 返工桶阈值（M-16）：已验收但判「判低/部分 PASS」的单据属**返工积压**，不是"还没人验过"的
# 验收槽积压。两类欠账性质不同：前者已经付过一次验收成本、额度已被消耗过；后者是纯欠账。
# 混在一个阈值里会让"判低 13 条"直接锁死全仓提交，反而逼调度层放弃返工去追新单——
# 与本判据"先清验收"的初衷相反。故拆桶：验收槽欠账 10 条/48h，返工欠账 20 条/168h。
MAX_REWORK = 20
MAX_REWORK_AGE_HOURS = 168
REWORK_WORDS = ("判低返工",)

FAM = r"(?:A|B|C|D|E|F|N|P|Q|S|U|V)"
ID_RE = re.compile(rf"(?<![A-Za-z0-9\-])({FAM}-?\d+(?:R\d+)?(?:-[a-z]\d+)?)")
DATE_FULL_RE = re.compile(r"(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})")
DATE_MD_RE = re.compile(r"(\d{2})-(\d{2})\s+(\d{2}):(\d{2})")
DOC_RE = re.compile(r"(docs/[^\s）)」`'\|]+\.md)")

PENDING_WORDS = ("待验证", "待验", "待派验收", "待独立验收", "已判·待验证", "已判待验证")
CLOSED_WORDS = ("已验·关闭", "已验关闭", "已收口", "已落地", "功能全补救")
CI_BLOCK_WORDS = ("待 CI", "CI 待绿", "CI待绿", "待云端 CI", "L2 待 CI", "待 CI 转绿", "CI 待")
EMOJI_ALL = "🔴🟡🟢✅⛔"


def norm_id(iid: str) -> str:
    return iid.replace("-", "").upper()


def parse_date(text: str, now: datetime.datetime):
    """从文本解析日期；返回 datetime 或 None。缺分钟按 0 分；未来时间回退一年。"""
    m = DATE_FULL_RE.search(text)
    if m:
        y, mo, d, h, mi = (int(x) for x in m.groups())
        try:
            ts = datetime.datetime(y, mo, d, h, mi)
        except ValueError:
            return None
    else:
        m = DATE_MD_RE.search(text)
        if not m:
            return None
        mo, d, h, mi = (int(x.replace("x", "0")) for x in m.groups())
        try:
            ts = datetime.datetime(now.year, mo, d, h, mi)
        except ValueError:
            return None
        if ts > now + datetime.timedelta(days=1):
            ts = ts.replace(year=now.year - 1)
    return ts


def status_cell(cells: list) -> str:
    for c in cells:
        if any(e in c for e in EMOJI_ALL):
            return c
    return ""


def is_pending(cell: str) -> bool:
    """待验族：🟢 且带待验关键词；或显式"已判·待验证"。✅/收口/落地等关闭态不计。"""
    if "✅" in cell:
        return False
    if any(w in cell for w in CLOSED_WORDS):
        return False
    if "已判·待验证" in cell or "已判待验证" in cell:
        return True
    return "🟢" in cell and any(w in cell for w in PENDING_WORDS)


def is_ci_blocked(cells: list) -> bool:
    blob = " ".join(cells)
    return any(w in blob for w in CI_BLOCK_WORDS)


def report_mtime(doc_dir: str, cell: str):
    """清单行关联报告文件的 mtime 回退年龄；找不到返回 None。
    优先取写码/清理报告（修复-/清理-/实现-），其次验收报告；路径已含 docs/ 前缀，
    拼 doc_dir 前先剥掉，避免 docs/docs 双前缀。"""
    matches = DOC_RE.findall(cell)
    ordered = sorted(matches, key=lambda p: (not p.startswith(("docs/修复-", "docs/清理-", "docs/实现-")),))
    for rel in ordered:
        if rel.startswith("docs/"):
            rel = rel[5:]
        p = os.path.join(doc_dir, rel)
        if os.path.isfile(p):
            return datetime.datetime.fromtimestamp(os.path.getmtime(p))
    return None


def collect(issue_path: str, ledger_path: str, docs_dir: str, now: datetime.datetime):
    """返回 (reviewable, rework, blocked, unknown_id) 四组积压条目。
    reviewable=从未验收过的欠账（验收槽积压）；rework=已验收但判低待返工（返工积压）。"""
    reviewable: dict[str, dict] = {}
    rework: dict[str, dict] = {}
    blocked: list[dict] = []
    unknown: list[dict] = []

    def consider(iid, cells, source, date_hint):
        cell = status_cell(cells)
        if not is_pending(cell):
            return
        entry = {"id": iid or "未编号", "source": source, "cells": cells, "date": None}
        if iid is None:
            unknown.append(entry)
            return
        if is_ci_blocked(cells):
            blocked.append(entry)
            return
        ts = date_hint or parse_date(" ".join(cells), now)
        if ts is None and source.endswith("问题清单.md"):
            ts = report_mtime(docs_dir, " ".join(cells))
        entry["date"] = ts
        key = norm_id(iid)
        # 返工桶分流：状态格显式带"判低返工"字样才进返工桶（人工标注，不猜）
        if any(w in cell for w in REWORK_WORDS):
            if key in rework:
                oldr = rework[key]
                if ts and (oldr["date"] is None or ts < oldr["date"]):
                    oldr["date"] = ts
            else:
                rework[key] = entry
            return
        # 去重：同号已有更早日期则保留更早的。
        if key in reviewable:
            old = reviewable[key]
            if ts and (old["date"] is None or ts < old["date"]):
                old["date"] = ts
        else:
            reviewable[key] = entry

    # 来源一：问题清单
    if os.path.isfile(issue_path):
        for line in open(issue_path, encoding="utf-8", errors="ignore"):
            if not line.startswith("|"):
                continue
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 2:
                continue
            m = ID_RE.search(cells[0])
            iid = m.group(1) if m else None
            consider(iid, cells, os.path.basename(issue_path), None)

    # 来源二：派单台账（状态格含 🟢 / 已判·待验证）
    # 台账首格形如 `11-26 00:1x Q-70 B17 标题`：Q-xx 是派单号，缺陷号是 B17；
    # 去重按缺陷号，故优先取非 Q 族编号，取不到再退回 Q 号。
    if os.path.isfile(ledger_path):
        for line in open(ledger_path, encoding="utf-8", errors="ignore"):
            if not line.startswith("|"):
                continue
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 2:
                continue
            ids = ID_RE.findall(cells[0])
            iid = next((x for x in ids if not x.upper().startswith("Q")), ids[0] if ids else None)
            ts = parse_date(cells[0], now)
            consider(iid, cells, os.path.basename(ledger_path), ts)

    return reviewable, rework, blocked, unknown


def evaluate(reviewable: dict, rework: dict, blocked: list, unknown: list, now: datetime.datetime):
    fails: list[str] = []
    n = len(reviewable)
    if n > MAX_PENDING:
        fails.append(f"可立即送验收的积压 {n} 条 > 阈值 {MAX_PENDING}，先清验收再发新写码单")

    ages = [(e["id"], (now - e["date"]).total_seconds() / 3600.0)
            for e in reviewable.values() if e["date"]]
    oldest_id, oldest_age = (None, None)
    if ages:
        oldest_id, oldest_age = max(ages, key=lambda x: x[1])
        if oldest_age > MAX_AGE_HOURS:
            fails.append(f"最老待验单 {oldest_id} 已挂 {oldest_age:.1f}h > 阈值 {MAX_AGE_HOURS}h，疑似僵尸积压")

    # 返工桶：单独阈值，超限同样 FAIL，但文案指明"返工"以免与验收欠账混淆
    rn = len(rework)
    if rn > MAX_REWORK:
        fails.append(f"判低返工积压 {rn} 条 > 阈值 {MAX_REWORK}，返工欠账已挤占新单推进")
    rages = [(e["id"], (now - e["date"]).total_seconds() / 3600.0)
             for e in rework.values() if e["date"]]
    roldest_id, roldest_age = (None, None)
    if rages:
        roldest_id, roldest_age = max(rages, key=lambda x: x[1])
        if roldest_age > MAX_REWORK_AGE_HOURS:
            fails.append(f"最老返工单 {roldest_id} 已挂 {roldest_age:.1f}h > 阈值 {MAX_REWORK_AGE_HOURS}h")

    return fails, n, len(blocked), len(unknown), oldest_id, oldest_age, rn, roldest_id, roldest_age


def main() -> int:
    if "--selftest" in sys.argv:
        return selftest()

    now = datetime.datetime.now()
    reviewable, rework, blocked, unknown = collect(ISSUE, LEDGER, os.path.join(ROOT, "docs"), now)
    fails, n, nb, nu, oldest_id, oldest_age, rn, roldest_id, roldest_age = evaluate(reviewable, rework, blocked, unknown, now)

    for f in fails:
        print(f"FAIL  {f}")
    for e in rework.values():
        print(f"INFO  [判低返工，不计验收槽积压] {e['id']}（{e['source']}）")
    for e in blocked:
        print(f"INFO  [CI 阻塞，不计验收积压] {e['id']}（{e['source']}）")
    for e in unknown:
        print(f"WARN  [未编号聚合行，不计年龄判定] {e['cells'][0][:30]}")

    age_txt = "日期未知"
    if oldest_age is not None:
        age_txt = f"最老 {oldest_id} {oldest_age:.1f}h"
    rtxt = f"返工 {rn} 条" + (f"（最老 {roldest_id} {roldest_age:.1f}h）" if roldest_age is not None else "")
    if fails:
        print(f"BACKLOG-DEPTH: FAIL（可验收积压 {n} 条 / {rtxt} / CI 阻塞 {nb} 条 / 未编号 {nu} 条；{age_txt}；阈值 验收 {MAX_PENDING} 条或 {MAX_AGE_HOURS}h、返工 {MAX_REWORK} 条或 {MAX_REWORK_AGE_HOURS}h）")
        return 1
    print(f"BACKLOG-DEPTH: PASS（可验收积压 {n} 条 / {rtxt} / CI 阻塞 {nb} 条 / 未编号 {nu} 条；{age_txt}；阈值 验收 {MAX_PENDING} 条或 {MAX_AGE_HOURS}h、返工 {MAX_REWORK} 条或 {MAX_REWORK_AGE_HOURS}h）")
    return 0


def _make_fixture(tmp: str, issue_rows: list[str], ledger_rows: list[str]):
    docs = os.path.join(tmp, "docs")
    os.makedirs(docs, exist_ok=True)
    issue = "# 问题清单\n\n| 编号 | 问题 | 证据 | 状态 | 归属 |\n|---|---|---|---|---|\n" + "\n".join(issue_rows) + "\n"
    open(os.path.join(docs, "问题清单.md"), "w", encoding="utf-8").write(issue)
    ledger = "# 派单台账\n\n| 缺陷/条目 | 写码代理 | 文件域 | 状态 | 验收代理 | 验收产物 | 级别 |\n|---|---|---|---|---|---|---|\n" + "\n".join(ledger_rows) + "\n"
    open(os.path.join(docs, "派单台账.md"), "w", encoding="utf-8").write(ledger)
    return docs


def selftest() -> int:
    """双向自测：小批量新鲜夹具 PASS；超行数 FAIL；超龄 FAIL；CI 阻塞与关闭态不计。"""
    now = datetime.datetime.now()
    today = now.strftime("%m-%d %H:%M")
    old = (now - datetime.timedelta(hours=72)).strftime("%m-%d %H:%M")

    # 合规：2 条可验收（新鲜）+ 1 条 CI 阻塞 + 1 条已关闭，应 PASS。
    tmp = tempfile.mkdtemp(prefix="backlog-selftest-")
    docs = _make_fixture(tmp,
        [
            "| B1 | x | e | 🟢已修·待验证（报告锚：docs/修复-B1-夹具.md） | 待验收 |",
            "| B2 | x | e | 🟢已修·待验证 | 待验收 |",
            "| B3 | x | e | 🟢已修·待验证；L2 待 CI | 等 CI |",
            "| B4 | x | e | ✅已验·关闭 | 已验 |",
        ],
        [
            f"| {today} Q-1 B1 夹具 | agent | f | 🟢 已修·待验证 | 待派 | docs/修复-B1-夹具.md | L2 |",
        ])
    open(os.path.join(docs, "修复-B1-夹具.md"), "w", encoding="utf-8").write("# x\n")
    rev, rew, blk, unk = collect(os.path.join(docs, "问题清单.md"), os.path.join(docs, "派单台账.md"), docs, now)
    fails, n, nb, nu, _, _, rn, _, _ = evaluate(rev, rew, blk, unk, now)
    assert not fails and n == 2 and nb == 1, f"合规夹具应 PASS：n={n} blocked={nb} fails={fails}"
    print(f"[selftest] 合规夹具 PASS 通路 OK：可验收 {n} / CI 阻塞 {nb} / 未编号 {nu}")

    # 违规 1：超行数（11 条可验收）→ FAIL。
    rows = [f"| B{10+i} | x | e | 🟢已修·待验证 | 待验收 |" for i in range(11)]
    tmp2 = tempfile.mkdtemp(prefix="backlog-selftest-")
    _make_fixture(tmp2, rows, [])
    docs2 = os.path.join(tmp2, "docs")
    rev2, rew2, blk2, unk2 = collect(os.path.join(docs2, "问题清单.md"), os.path.join(docs2, "派单台账.md"), docs2, now)
    fails2, n2, _, _, _, _, _, _, _ = evaluate(rev2, rew2, blk2, unk2, now)
    assert n2 > MAX_PENDING and any("积压" in f for f in fails2), f"超行数应 FAIL：n={n2} fails={fails2}"
    print("[selftest] 超行数 FAIL 通路 OK：", fails2[0])

    # 违规 2：单条超龄（72h）→ FAIL 时长阈值。
    tmp3 = tempfile.mkdtemp(prefix="backlog-selftest-")
    _make_fixture(tmp3, [], [f"| {old} Q-9 B9 老单 | agent | f | 🟢 已修·待验证 | 待派 | — | L2 |"])
    docs3 = os.path.join(tmp3, "docs")
    rev3, rew3, blk3, unk3 = collect(os.path.join(docs3, "问题清单.md"), os.path.join(docs3, "派单台账.md"), docs3, now)
    fails3, _, _, _, _, age3, _, _, _ = evaluate(rev3, rew3, blk3, unk3, now)
    assert any("僵尸积压" in f for f in fails3), f"超龄应 FAIL：{fails3} age={age3}"
    print("[selftest] 超龄 FAIL 通路 OK：", fails3[0])

    # 违规 3（M-16 新增）：返工桶超限（21 条「判低返工」）→ FAIL，且必须与验收桶分开计。
    tmp4 = tempfile.mkdtemp(prefix="backlog-selftest-")
    rrows = [f"| B{20+i} | x | e | 🟢已修·待验证（判低返工） | 待返工 |" for i in range(MAX_REWORK + 1)]
    docs4 = _make_fixture(tmp4, rrows, [])
    rev4, rew4, blk4, unk4 = collect(os.path.join(docs4, "问题清单.md"), os.path.join(docs4, "派单台账.md"), docs4, now)
    fails4, n4, _, _, _, _, rn4, _, _ = evaluate(rev4, rew4, blk4, unk4, now)
    assert n4 == 0 and rn4 > MAX_REWORK and any("返工积压" in f for f in fails4), \
        f"返工桶超限应 FAIL 且不计入验收桶：n={n4} rework={rn4} fails={fails4}"
    print("[selftest] 返工桶超限 FAIL 通路 OK：", [f for f in fails4 if "返工" in f][0])
    shutil.rmtree(tmp4, ignore_errors=True)

    shutil.rmtree(tmp, ignore_errors=True)
    shutil.rmtree(tmp2, ignore_errors=True)
    shutil.rmtree(tmp3, ignore_errors=True)
    print("[selftest] 夹具已自删，双向自测通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
