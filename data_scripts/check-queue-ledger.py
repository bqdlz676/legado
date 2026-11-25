#!/usr/bin/env python3
"""门禁 ㊶ 队列↔台账结构与状态一致性守卫（M-01/M-02/M-04 收口，Q-68）。

为什么需要这条：队列曾出现 4/6/7/8 列四种行混排（按列解析必然失真）；RUNNING 行
曾长期无 agent id / 起始时间 / 文件域 / 判据锚，僵尸单靠人工提交擦过一次；队列与台账
同 ID 状态两处手写、无主从校验。本条把这三类腐化全部变成机械判据。

判据（任一 FAIL 退出码 1；本项只许 FAIL 不许 WARN，提示信息也计入失败线索）：
  C1 结构：主表每个派单行列数与表头一致（6 列）；「历史追加账」小节不参与解析。
  C2 状态白名单：状态列归一化后只许 PENDING / RUNNING / DONE / BLOCKED / 作废；
     BLOCKED 必须同时写明阻塞原因与解除条件，缺一 FAIL。
  C3 RUNNING 四要素：agent id（`s_xxx` 或「调度层」）、起始时间、文件域、判据锚，
     缺任一 FAIL——防"挂着在飞、实际已死/无人接"的僵尸 RUNNING。
  C4 双事实源一致：队列与台账同 Q-xx 状态不得互相矛盾（RUNNING↔已收口/作废、
     DONE↔作废、作废↔在飞、PENDING↔已收口 均判 FAIL）。

边界：历史追加账小节的 4 列留档行不进状态机；台账行若未登记该 Q-xx，C4 跳过
（不反向要求台账补登，那是 ⑯ 新鲜度门禁的职责）。
"""
import os
import re
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QUEUE = os.path.join(ROOT, "docs", "派单队列.md")
LEDGER = os.path.join(ROOT, "docs", "派单台账.md")

HEADER_COLS = 6
QUEUE_STATES = ("PENDING", "RUNNING", "DONE", "BLOCKED", "作废")
AGENT_RE = re.compile(r"s_[A-Za-z0-9]{6,}|调度层")
TIME_RE = re.compile(r"\d{4}-\d{2}-\d{2}")
QID_RE = re.compile(r"Q-\d+")


def norm_queue_status(text: str) -> str:
    """把状态单元格归一化到五态之一；识别不出返回 OTHER。"""
    t = text.strip()
    upper = t.upper()
    for w in ("PENDING", "RUNNING", "DONE", "BLOCKED"):
        if upper.startswith(w):
            return w
    if "作废" in t or upper.startswith("VOID"):
        return "作废"
    return "OTHER"


def norm_ledger_status(text: str) -> str:
    """台账状态词归一化到队列五态；识别不出返回 OTHER（不强行猜）。"""
    t = text
    if "在飞" in t or "RUNNING" in t.upper():
        return "RUNNING"
    if "作废" in t:
        return "作废"
    if "已收口" in t or "已完成" in t or "已落地" in t or "DONE" in t.upper() or "已提交" in t:
        return "DONE"
    if "待派" in t or "待核查" in t or "待验" in t or "PENDING" in t.upper():
        return "PENDING"
    return "OTHER"


def parse_queue(path: str):
    """解析主表派单行；遇到「历史追加账」标题即停止（该小节不参与状态机解析）。"""
    rows = []
    in_history = False
    header_cols = HEADER_COLS
    for line in open(path, encoding="utf-8", errors="ignore").read().splitlines():
        if line.startswith("##") and "历史追加账" in line:
            in_history = True
            continue
        if in_history or not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if not cells or not QID_RE.match(cells[0]):
            continue
        rows.append({
            "id": cells[0],
            "title": cells[1] if len(cells) > 1 else "",
            "created": cells[2] if len(cells) > 2 else "",
            "status": cells[3] if len(cells) > 3 else "",
            "dep": cells[4] if len(cells) > 4 else "",
            "domain": cells[5] if len(cells) > 5 else "",
            "ncols": len(cells),
        })
    return rows, header_cols


def parse_ledger(path: str) -> dict:
    """台账按行抽 Q-xx 与归一化状态；状态取含状态词的单元格（兼容 6/7 列混排）。"""
    out = {}
    if not os.path.isfile(path):
        return out
    for line in open(path, encoding="utf-8", errors="ignore").read().splitlines():
        if not line.startswith("|"):
            continue
        m = QID_RE.search(line)
        if not m:
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        # 状态单元格：优先第 4 列；否则全行扫状态词
        status_cell = cells[3] if len(cells) > 3 else " ".join(cells)
        out[m.group(0)] = norm_ledger_status(status_cell)
    return out


def check(repo: str):
    fails = []
    qpath = os.path.join(repo, "docs", "派单队列.md")
    lpath = os.path.join(repo, "docs", "派单台账.md")

    if not os.path.isfile(qpath):
        return [f"缺队列 docs/派单队列.md（M-01 无解析对象）"], {"rows": 0}

    rows, header_cols = parse_queue(qpath)
    ledger = parse_ledger(lpath)

    for r in rows:
        # C1 列数一致
        if r["ncols"] != header_cols:
            fails.append(f"{r['id']} 列数 {r['ncols']} ≠ 表头 {header_cols}（M-01 结构漂移）")
            continue  # 列数错时后续按列判据会错位，跳过本行其余检查

        state = norm_queue_status(r["status"])

        # C2 状态白名单
        if state == "OTHER":
            fails.append(f"{r['id']} 状态「{r['status'][:24]}」不在白名单 {QUEUE_STATES}（M-07 口径违规）")
        elif state == "BLOCKED":
            if not re.search(r"原因|阻塞|卡在|依赖", r["status"]):
                fails.append(f"{r['id']} BLOCKED 缺阻塞原因")
            if not re.search(r"解除|条件|解除条件|待.*DONE|依赖", r["status"] + r["dep"]):
                fails.append(f"{r['id']} BLOCKED 缺解除条件")

        # C3 RUNNING 四要素
        if state == "RUNNING":
            if not AGENT_RE.search(r["status"]):
                fails.append(f"{r['id']} RUNNING 缺 agent id（s_xxx 或调度层）")
            if not TIME_RE.search(r["created"]) and not TIME_RE.search(r["status"]):
                fails.append(f"{r['id']} RUNNING 缺起始时间")
            if not r["domain"] or r["domain"] == "无":
                fails.append(f"{r['id']} RUNNING 缺文件域")
            anchor_hint = r["dep"] + " " + r["domain"]
            if not re.search(r"docs/|锚点|矩阵|拍板|\.md|§|M-\d+", anchor_hint):
                fails.append(f"{r['id']} RUNNING 缺判据锚")

        # C4 队列↔台账一致
        if r["id"] in ledger:
            lstate = ledger[r["id"]]
            contradictions = {
                ("RUNNING", "DONE"): "台账已收口/已完成，队列仍 RUNNING（僵尸在飞）",
                ("RUNNING", "作废"): "台账已作废，队列仍 RUNNING",
                ("DONE", "作废"): "台账已作废，队列标 DONE",
                ("作废", "RUNNING"): "台账在飞，队列标作废",
                ("PENDING", "DONE"): "台账已收口，队列仍 PENDING",
            }
            if (state, lstate) in contradictions:
                fails.append(f"{r['id']} 双事实源漂移：队列 {state} ↔ 台账 {lstate}（{contradictions[(state, lstate)]}）")

    stats = {"rows": len(rows), "ledger": len(ledger)}
    return fails, stats


def selftest() -> int:
    """双向自测：合规夹具 PASS；四类违规夹具各 FAIL；夹具用完自删。"""
    tmp = tempfile.mkdtemp(prefix="queue-ledger-selftest-")
    os.makedirs(os.path.join(tmp, "docs"), exist_ok=True)

    good_queue = """# 派单队列（自测夹具）

> 本文件只有一张派单表参与门禁解析。

| 序号 | 派单标题 | 创建时间 | 状态 | 依赖 | 文件域/判据锚 |
|---|---|---|---|---|---|
| Q-01 | 合规在飞单 | 2026-11-25 23:5x | RUNNING（s_000abc123，起始 2026-11-25 23:5x） | docs/锚点/X.md | ui/book/read/** |
| Q-02 | 合规完成单 | 2026-11-25 22:00 | DONE（commit `abc1234`） | 无 | docs/验收-X.md |
| Q-03 | 合规阻塞单 | 2026-11-25 22:10 | BLOCKED（原因：依赖 Q-02 验收；解除条件：Q-02 DONE 后重派） | Q-02 | ui/main/** |

## 历史追加账（不参与状态机解析）

| 序号 | 条目 | 标题/范围 | 状态（留档原文） |
|---|---|---|---|
| Q-99 | 旧单 | 留档 | DONE x |
"""
    good_ledger = """# 派单台账（自测夹具）

| 缺陷/条目 | 写码代理 | 文件域 | 状态 | 独立验收代理 | 验收产物 | 最高级别 |
|---|---|---|---|---|---|---|
| Q-01 合规在飞单 | s_000abc123 | ui/book/read/** | 🟡 在飞 | 待派 | docs/锚点/X.md | L2 |
| Q-02 合规完成单 | s_000def456 | docs/验收-X.md | ✅ 已收口 | — | docs/验收-X.md | L1 |
| Q-03 合规阻塞单 | 调度层 | ui/main/** | 待派 | 待派 | 无 | L0 |
"""
    with open(os.path.join(tmp, "docs", "派单队列.md"), "w", encoding="utf-8") as f:
        f.write(good_queue)
    with open(os.path.join(tmp, "docs", "派单台账.md"), "w", encoding="utf-8") as f:
        f.write(good_ledger)

    fails, stats = check(tmp)
    assert not fails, f"合规夹具应 PASS，实际 FAIL：{fails}"
    print(f"[selftest] 合规夹具 PASS 通路 OK：{stats}")

    # 违规 1：列数漂移（4 列迷你行）
    bad1 = good_queue.replace(
        "| Q-03 | 合规阻塞单 | 2026-11-25 22:10 | BLOCKED（原因：依赖 Q-02 验收；解除条件：Q-02 DONE 后重派） | Q-02 | ui/main/** |",
        "| Q-03 | 旧单 | 迷你行 | DONE x |",
    )
    with open(os.path.join(tmp, "docs", "派单队列.md"), "w", encoding="utf-8") as f:
        f.write(bad1)
    fails1, _ = check(tmp)
    assert any("列数" in x for x in fails1), f"列数漂移应 FAIL，实际：{fails1}"
    print("[selftest] 列数漂移 FAIL 通路 OK，命中：", fails1)

    # 违规 2：非法状态词 + BLOCKED 缺原因/解除条件
    bad2 = good_queue.replace(
        "| Q-03 | 合规阻塞单 | 2026-11-25 22:10 | BLOCKED（原因：依赖 Q-02 验收；解除条件：Q-02 DONE 后重派） | Q-02 | ui/main/** |",
        "| Q-03 | 坏状态单 | 2026-11-25 22:10 | BLOCKED（等别人） | Q-02 | ui/main/** |",
    ).replace("DONE（commit `abc1234`）", "差不多好了")
    with open(os.path.join(tmp, "docs", "派单队列.md"), "w", encoding="utf-8") as f:
        f.write(bad2)
    fails2, _ = check(tmp)
    assert any("白名单" in x for x in fails2) and any("解除条件" in x or "原因" in x for x in fails2), \
        f"非法状态/BLOCKED 缺要素应 FAIL，实际：{fails2}"
    print("[selftest] 非法状态+BLOCKED 缺要素 FAIL 通路 OK，命中：", fails2)

    # 违规 3：RUNNING 缺 agent id / 起始时间 / 文件域 / 判据锚
    bad3 = good_queue.replace(
        "| Q-01 | 合规在飞单 | 2026-11-25 23:5x | RUNNING（s_000abc123，起始 2026-11-25 23:5x） | docs/锚点/X.md | ui/book/read/** |",
        "| Q-01 | 僵尸在飞单 |  | RUNNING | 无 | 无 |",
    )
    with open(os.path.join(tmp, "docs", "派单队列.md"), "w", encoding="utf-8") as f:
        f.write(bad3)
    fails3, _ = check(tmp)
    assert sum("RUNNING 缺" in x for x in fails3) >= 3, f"RUNNING 四要素缺失应 FAIL，实际：{fails3}"
    print("[selftest] RUNNING 四要素缺失 FAIL 通路 OK，命中：", fails3)

    # 违规 4：队列↔台账状态矛盾（队列 RUNNING，台账已收口）
    with open(os.path.join(tmp, "docs", "派单队列.md"), "w", encoding="utf-8") as f:
        f.write(good_queue)
    bad4 = good_ledger.replace("🟡 在飞", "✅ 已收口")
    with open(os.path.join(tmp, "docs", "派单台账.md"), "w", encoding="utf-8") as f:
        f.write(bad4)
    fails4, _ = check(tmp)
    assert any("双事实源漂移" in x for x in fails4), f"状态矛盾应 FAIL，实际：{fails4}"
    print("[selftest] 队列↔台账矛盾 FAIL 通路 OK，命中：", fails4)

    shutil.rmtree(tmp, ignore_errors=True)
    print("[selftest] 夹具已自删，双向自测通过。")
    return 0


def main() -> int:
    if "--selftest" in sys.argv:
        return selftest()
    fails, stats = check(ROOT)
    for f in fails:
        print(f"FAIL  {f}")
    if fails:
        print(f"QUEUE-LEDGER: FAIL（{len(fails)} 条；主表 {stats['rows']} 行 / 台账登记 {stats['ledger']} 条）")
        return 1
    print(f"QUEUE-LEDGER: PASS（主表 {stats['rows']} 行结构/状态/四要素/双源一致）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
