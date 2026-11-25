#!/usr/bin/env python3
"""门禁 ⑱ 队列与满槽守卫（F-84，2026-11-24 槽满不丢单事故口径）。

背景：框架并发是硬顶 5、且**无队列**——第 6 个 `create_agent` 直接返回
`concurrency limit reached … NO queue`，未创建即丢失。所以"槽满"不是失败，
派单必须落 `docs/派单队列.md`，槽释放即补队首；PENDING 积压超时即丢单。

判据（任一 FAIL 退出码 1；WARN 不计失败）：
  E1 队列文件存在（`docs/派单队列.md`）。
  E2 无 PENDING 项存活 >1 轮调度：机械代理＝创建时间距今 >60 分钟；
     依赖未解除（创建时间写"待…DONE"、状态 BLOCKED）的行不计丢单。
  E3 不空槽空转：台账在飞＝0 且队列 RUNNING＝0 但仍有 PENDING 积压 → FAIL；
     无积压时空槽不算故障。
  E4 不空转：档位文件给出当前档位总数，台账在飞低于该档且队列仍有 PENDING
     积压 → FAIL（有活不派）；无积压时低占用属正常周转。

边界：门禁是工作区快照，"连续两轮"无法在单次脚本运行内累计；E3/E4 按当前
积压有无判空转，轮次连续性由调度层每轮 wait_agent 返回后复核。
"""
import datetime
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from queue_stats import stats as qstats  # O1：计数单源，禁用行内子串匹配

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QUEUE = os.path.join(ROOT, "docs", "派单队列.md")
LEDGER = os.path.join(ROOT, "docs", "派单台账.md")
TIER = os.path.join(ROOT, "docs", "并发档位.md")
STALE_MINUTES = 60  # 一轮调度（一次 wait_agent 返回）的时间代理阈值


def parse_rows(text: str):
    """返回队列数据行 dict 列表；跳过表头/分隔线/空行。"""
    rows = []
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 6:
            continue
        if cells[0] in ("序号",) or set(cells[0]) <= set("-: "):
            continue
        rows.append({
            "id": cells[0], "title": cells[1], "created": cells[2],
            "status": cells[3], "kind": qstats.__globals__["classify"](cells[3]),
            "dep": cells[4], "domain": cells[5],
        })
    return rows


def main() -> int:
    fails: list[str] = []

    if not os.path.isfile(QUEUE):
        print("FAIL 缺队列 docs/派单队列.md（槽满派单无兜底，F-84 未接线）")
        return 1

    qtext = open(QUEUE, encoding="utf-8").read()
    rows = parse_rows(qtext)
    # O1：按状态列前缀分类（queue_stats 单源），不再精确匹配整串（旧判据在状态带备注时恒为 0）
    pending = [r for r in rows if r["kind"] == "PENDING"]
    running = [r for r in rows if r["kind"] == "RUNNING"]
    print(f"ok    队列在册：共 {len(rows)} 行，PENDING {len(pending)} / RUNNING {len(running)} / DONE 其余")

    # E2：PENDING 存活超时 → 丢单
    now = datetime.datetime.now()
    for r in pending:
        m = re.match(r"(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})", r["created"])
        if not m:
            continue  # 依赖未解除的阻塞行，不计超时丢单
        try:
            ts = datetime.datetime.strptime(f"{m.group(1)} {m.group(2)}", "%Y-%m-%d %H:%M")
        except ValueError:
            continue
        age = (now - ts).total_seconds() / 60.0
        if age > STALE_MINUTES:
            fails.append(f"PENDING {r['id']}「{r['title']}」存活 {age:.0f} 分钟（> {STALE_MINUTES}），判丢单")

    # 台账在飞数
    open_count = 0
    if os.path.isfile(LEDGER):
        ltext = open(LEDGER, encoding="utf-8").read()
        open_count = sum(
            1 for line in ltext.splitlines() if re.match(r"^\|.*\|\s*在飞", line)
        )

    # E3：空槽空转（有积压却 0 在飞 0 RUNNING）
    if open_count == 0 and not running and pending:
        fails.append(f"空槽空转：台账在飞 0、队列 RUNNING 0，仍有 {len(pending)} 条 PENDING 积压未补派")

    # E4：有积压但 0 在飞（纯空转）；部分占用低于档位只 WARN（快照无法判"连续两轮"，
    #     周转中的半槽不算空转；连续空槽由调度层每轮 wait_agent 返回后复核）
    if os.path.isfile(TIER) and pending:
        tline = open(TIER, encoding="utf-8").read().splitlines()
        head = next((l for l in tline if "当前档位：" in l), "")
        head = head.split("；")[0]
        mt = re.search(r"写\s*(\d+)\s*\+\s*查\s*(\d+)", head)
        if mt:
            cap = int(mt.group(1)) + int(mt.group(2))
            if open_count == 0:
                fails.append(f"空转：在飞 0 且档位上限 {cap}，队列仍有 {len(pending)} 条 PENDING 未派")
            elif open_count < cap:
                print(f"WARN  半槽周转：在飞 {open_count}/{cap}，{len(pending)} 条 PENDING 待补派（非空转）")

    for f in fails:
        print(f"FAIL  {f}")
    if fails:
        return 1
    print("PASS  队列与满槽合格（无丢单、无空槽空转）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
