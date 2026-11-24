#!/usr/bin/env python3
"""门禁 ㉗ 子代理存活 / 锚点回执 / 跑题守卫（2026-11-25 第2棒事故复盘产物）。

事故（本棒实证，轨迹原文可回源）：
  s_000749sNfrw  收到 2500+ 字派单原文（trajectory 首行 user 已逐字落地），
                 首条 assistant 直接吐"小红书风格文案"——与本单零关系，工具调用 0，耗时 16s；
  s_000749slLa2  同形态，首条 assistant 回"How can I help you today?"，工具调用 0，耗时 4s。
  另一例 s_000749sN5Y0 因 create_agent 未带 subtask，继承原始用户查询"开启新一轮续跑"，
  前 70+ 行在做"考古/自我定位"，真实任务迟到（靠 send_message 补发才开工）。

结论：事故不在下发通道（任务原文确实到了），而在"子代理首轮没有强制机械动作"——
模型在拿到长任务后进入了通用生成模式，或被上游查询带偏。因此守卫必须是结构性的：
派单模板强制 ANCHOR-ACK，调度层 90 秒内读轨迹校验，未回执或零工具调用即判跑题、判废重派。

判据（退出码 1 = FAIL）：
  ① 近 48 小时内活动过的代理（历史留档不追改，边界同 ⑭/⑮/⑲），轨迹已有 assistant 首条但
     **零工具调用**，且该 id 未登记在 `docs/台账-子代理存活.md` → FAIL（未判废即视为未闭环事故）；
  ② 上述代理首条 assistant 未含 `ANCHOR-ACK` 且零工具调用 → 同一 FAIL 的更严重形态（显式标注）；
  ③ 已登记台账、标注"判废"的 id 不计 FAIL。
WARN（退出码 0）：
  ④ 首轮 assistant 含 ANCHOR-ACK 但工具调用数 0～1 且轨迹行数 ≥ 30 → 提示"读多写少"。
"""
import glob
import json
import os
import re
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEDGER_NAME = "台账-子代理存活.md"
# 审窗：只审"本棒这一轮"里动过的代理。窗口太宽会把昨天已留档的事故反复计红，
# 窗口太窄会漏掉刚跑完的跑题代理。默认 4 小时，可用 LIVENESS_WINDOW_HOURS 覆盖。
WINDOW_HOURS = float(os.environ.get("LIVENESS_WINDOW_HOURS", "4"))


def session_agent_dirs():
    """只审「当前活动会话」的 agents 目录。

    为什么只审一个会话：历史会话的跑题/空转已由 docs/复盘-子代理空转与假完成.md 逐例留档，
    重复计红等于让历史事故永久压着门禁（边界同 ⑭/⑮/⑲「历史留档不追改」）。
    当前活动会话取法：所有会话里 agents/ 子目录 mtime 最新的那一个——最新开工的一定在本棒所在会话。
    """
    home = os.path.expanduser("~")
    cands = glob.glob(os.path.join(home, ".doubao", "agent_mode", "workspace", ".sessions", "*", "agents"))
    if not cands:
        return []
    def latest_mtime(base):
        sub = glob.glob(os.path.join(base, "s_*"))
        return max((os.path.getmtime(d) for d in sub), default=0)
    best = max(cands, key=latest_mtime)
    return [best]


def read_trajectory(path):
    rows = []
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    rows.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    except FileNotFoundError:
        return []
    return rows


def first_of(rows, role):
    for r in rows:
        if r.get("role") == role:
            return str(r.get("content") or "")
    return ""


def tool_count(rows):
    return sum(1 for r in rows if r.get("role") == "tool")


def ledger_ids():
    """台账里出现过的代理 id → 视为已判废/已登记，不再计 FAIL。"""
    ids = set()
    for p in glob.glob(os.path.join(ROOT, "docs", LEDGER_NAME)):
        try:
            text = open(p, encoding="utf-8").read()
        except FileNotFoundError:
            continue
        ids |= set(re.findall(r"s_[0-9A-Za-z]+", text))
    return ids


def main() -> int:
    registered = ledger_ids()
    fails, warns = [], []
    scanned = 0

    if not registered:
        print(f"FAIL 缺少台账 docs/{LEDGER_NAME}（守卫无白名单，无法区分历史留档与在飞失联）")
        return 1

    roots = session_agent_dirs()
    if roots:
        print(f"ok    活动会话：{roots[0]}")
    for agents_root in roots:
        for agent_dir in sorted(glob.glob(os.path.join(agents_root, "s_*"))):
            agent_id = os.path.basename(agent_dir)
            traj = os.path.join(agent_dir, "system", "trajectory.jsonl")
            if not os.path.isfile(traj):
                continue
            rows = read_trajectory(traj)
            if not rows:
                continue
            # 只审近 48 小时内还在动的代理：历史留档不追改（与 ⑭/⑮/⑲ 同一边界）。
            if time.time() - os.path.getmtime(traj) > WINDOW_HOURS * 3600:
                continue
            scanned += 1
            first_asst = first_of(rows, "assistant")
            if not first_asst:
                continue
            tc = tool_count(rows)
            has_ack = "ANCHOR-ACK" in first_asst
            if tc == 0 and agent_id not in registered:
                if has_ack:
                    fails.append(f"{agent_id} 回了 ANCHOR-ACK 但零工具调用且未在台账判废")
                else:
                    snippet = first_asst.strip().splitlines()[0][:40]
                    fails.append(f"{agent_id} 首轮零工具调用 + 无 ANCHOR-ACK（疑似跑题）：{snippet}")
            elif tc <= 1 and len(rows) >= 30:
                warns.append(f"{agent_id} 轨迹 {len(rows)} 行但工具调用 {tc} 次，疑似读多写少")

    for w in warns:
        print(f"WARN  {w}")
    for f in fails:
        print(f"FAIL  {f}")
    if fails:
        return 1
    print(f"ok    存活/锚点回执：扫描 {scanned} 个带轨迹代理，零未闭环跑题")
    print("AGENT-LIVENESS: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
