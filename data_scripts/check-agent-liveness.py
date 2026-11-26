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
import shutil
import sys
import tempfile
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


def _make_agent(tmp: str, agent_id: str, rows: list) -> str:
    """在临时目录造一个代理轨迹夹具：<tmp>/agents/<id>/system/trajectory.jsonl。

    为什么列结构照真轨迹：read_trajectory 逐行 json.loads、first_of 按 role 取首条、
    tool_count 数 role=tool；夹具行缺 role/content 会被静默跳过，坏样本就打不到判据。"""
    agents_root = os.path.join(tmp, 'agents')
    d = os.path.join(agents_root, agent_id, 'system')
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, 'trajectory.jsonl'), 'w', encoding='utf-8') as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + '\n')
    return agents_root


def selftest() -> int:
    """双向自测：有回执有工具调用 → PASS；零工具调用未登记 → FAIL（无回执/有回执两形态）；
    已登记台账 → 不计 FAIL；缺台账 → FAIL；空会话集 → 零扫描 PASS。
    做法：monkeypatch 模块级 session_agent_dirs / ledger_ids 指向临时目录与夹具集合，
    判定全走生产 main()，不另写轨迹比对；夹具 tempfile.mkdtemp + rmtree 自删。"""
    saved_dirs, saved_ledger = session_agent_dirs, ledger_ids

    def _run(agents_root: list, registered: set) -> int:
        globals()['session_agent_dirs'] = lambda: agents_root
        globals()['ledger_ids'] = lambda: registered
        return main()

    try:
        # 好样本：首条 assistant 带 ANCHOR-ACK，2 次工具调用，未登记也不命中 FAIL/WARN
        good = tempfile.mkdtemp(prefix='agent-liveness-selftest-')
        try:
            root_good = _make_agent(good, 's_good1', [
                {'role': 'assistant', 'content': 'ANCHOR-ACK 三行已回执'},
                {'role': 'tool', 'content': 'grep 回证'},
                {'role': 'tool', 'content': '读取切片'},
            ])
            rc_good = _run([root_good], {'s_dummy'})
            assert rc_good == 0, f"有回执有工具调用应 PASS，rc={rc_good}"
            print('[selftest] 好样本（ANCHOR-ACK + 2 次工具调用）PASS 通路 OK')
        finally:
            shutil.rmtree(good, ignore_errors=True)

        # 坏样本一：首条 assistant 无 ANCHOR-ACK、零工具调用、未登记 → 疑似跑题 FAIL
        bad1 = tempfile.mkdtemp(prefix='agent-liveness-selftest-')
        try:
            root_b1 = _make_agent(bad1, 's_bad1', [
                {'role': 'assistant', 'content': '小红书风格文案，与本单无关'},
            ])
            rc_b1 = _run([root_b1], {'s_dummy'})
            assert rc_b1 == 1, f"零工具调用且无回执应 FAIL，rc={rc_b1}"
            print('[selftest] 坏样本一（零工具调用 + 无 ANCHOR-ACK）FAIL 通路 OK')
        finally:
            shutil.rmtree(bad1, ignore_errors=True)

        # 坏样本二：首条 assistant 带 ANCHOR-ACK 但零工具调用、未登记 → 回执空转 FAIL
        bad2 = tempfile.mkdtemp(prefix='agent-liveness-selftest-')
        try:
            root_b2 = _make_agent(bad2, 's_bad2', [
                {'role': 'assistant', 'content': 'ANCHOR-ACK 已写，但没有后续动作'},
            ])
            rc_b2 = _run([root_b2], {'s_dummy'})
            assert rc_b2 == 1, f"有回执但零工具调用应 FAIL，rc={rc_b2}"
            print('[selftest] 坏样本二（ANCHOR-ACK 但零工具调用）FAIL 通路 OK')
        finally:
            shutil.rmtree(bad2, ignore_errors=True)

        # 边界·已登记台账：零工具调用但 id 在判废白名单内 → 不计 FAIL
        reg = tempfile.mkdtemp(prefix='agent-liveness-selftest-')
        try:
            root_reg = _make_agent(reg, 's_reg1', [
                {'role': 'assistant', 'content': '通用寒暄'},
            ])
            rc_reg = _run([root_reg], {'s_reg1'})
            assert rc_reg == 0, f"已登记台账的判废代理应 PASS，rc={rc_reg}"
            print('[selftest] 边界·已登记（判废白名单内零工具调用）按设计放行 OK')
        finally:
            shutil.rmtree(reg, ignore_errors=True)

        # 边界·缺台账：ledger_ids 返回空集 → 守卫无白名单，FAIL 通路（空集不许静默放行）
        rc_noledger = _run([], set())
        assert rc_noledger == 1, f"缺台账应 FAIL，rc={rc_noledger}"
        print('[selftest] 边界·缺台账（守卫无白名单）FAIL 通路 OK')

        # 边界·空会话集：无活动会话目录，扫描 0 个代理 → 现有设计 PASS
        rc_empty = _run([], {'s_other'})
        assert rc_empty == 0, f"空会话集现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（无活动会话目录）按现有设计放行，已记录口径')

        # WARN 通路：1 次工具调用、轨迹 ≥30 行 → 读多写少 WARN，退出码仍 0
        warn = tempfile.mkdtemp(prefix='agent-liveness-selftest-')
        try:
            rows = [{'role': 'assistant', 'content': 'ANCHOR-ACK 回执'}]
            rows += [{'role': 'user', 'content': f'背景片段 {i}'} for i in range(28)]
            rows.append({'role': 'tool', 'content': '一次调用'})
            root_warn = _make_agent(warn, 's_warn1', rows)
            rc_warn = _run([root_warn], {'s_dummy'})
            assert rc_warn == 0, f"读多写少仅 WARN 应 rc=0，rc={rc_warn}"
            print('[selftest] WARN 通路（轨迹 30 行仅 1 次工具调用）提示通路 OK')
        finally:
            shutil.rmtree(warn, ignore_errors=True)
    finally:
        # 模块函数必须还原：断言失败也不能把生产轨迹根接管留在临时目录上
        globals()['session_agent_dirs'] = saved_dirs
        globals()['ledger_ids'] = saved_ledger

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == "__main__":
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main())
