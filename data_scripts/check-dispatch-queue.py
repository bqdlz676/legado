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
积压有无判空转，轮次连续性由调度层每次调度快照（工具调用顺带读取）复核，禁止挂起。
"""
import datetime
import os
import re
import shutil
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from queue_stats import stats as qstats  # O1：计数单源，禁用行内子串匹配

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QUEUE = os.path.join(ROOT, "docs", "派单队列.md")
LEDGER = os.path.join(ROOT, "docs", "派单台账.md")
TIER = os.path.join(ROOT, "docs", "并发档位.md")
STALE_MINUTES = 60  # 一轮调度（一次调度快照）的时间代理阈值


def parse_rows(text: str):
    """返回队列数据行 dict 列表；跳过表头/分隔线/空行。"""
    rows = []
    # 列数不许硬门槛（M-50，2026-11-26 实测）：旧口径 `len(cells) < 6: continue` 把 4 列行整行丢弃，
    # Q-211~216 六条 RUNNING 因此对 ⑱ 完全不可见，㉚ 打印"RUNNING 0"却实际六个代理在飞。
    # 现口径：≥3 列即入册，状态格按"含状态词的那一格"定位（与 ㊹ 同一套判定）。
    statuses = ("PENDING", "RUNNING", "DONE", "BLOCKED")
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 3:
            continue
        if cells[0] in ("序号",) or set(cells[0]) <= set("-: "):
            continue
        st = next((c for c in cells if re.match(r"^(%s)\b" % "|".join(statuses), c.upper())), "")
        if not st:
            continue  # 无状态格 = 说明行/正文行，不冒充派单行
        idx = cells.index(st)
        rows.append({
            "id": cells[0], "title": cells[1] if len(cells) > 1 else "",
            "created": cells[2] if idx > 2 else "",
            "status": st, "kind": qstats.__globals__["classify"](st),
            "dep": cells[idx + 1] if len(cells) > idx + 1 else "",
            "domain": cells[idx + 2] if len(cells) > idx + 2 else "",
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
            1 for line in ltext.splitlines() if re.match(r"^\|.*\|\s*([🟡🔴🟢✅⛔]\s*)?在飞", line)
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


def _make_fixture(tmp: str, rows: list[str], ledger_rows: list[str] | None = None,
                  tier_text: str | None = None) -> None:
    """在临时目录造 docs/派单队列.md（可选台账/档位文件）。
    为什么列结构照真队列：parse_rows 按 ≥3 列入册、状态格按前缀定位，夹具列数不齐会被静默丢弃，坏样本就打不到判据。"""
    docs = os.path.join(tmp, 'docs')
    os.makedirs(docs, exist_ok=True)
    head = ("| 序号 | 标题 | 创建时间 | 状态 | 依赖 | 域 |\n"
            "|---|---|---|---|---|---|\n")
    open(os.path.join(docs, '派单队列.md'), 'w', encoding='utf-8').write(head + '\n'.join(rows) + '\n')
    if ledger_rows is not None:
        open(os.path.join(docs, '派单台账.md'), 'w', encoding='utf-8').write('\n'.join(ledger_rows) + '\n')
    if tier_text is not None:
        open(os.path.join(docs, '并发档位.md'), 'w', encoding='utf-8').write(tier_text)


def selftest() -> int:
    """双向自测：新鲜在飞单 → PASS；PENDING 存活 3h → E2 判丢单；有 PENDING 却 0 在飞 0 RUNNING → E3 空槽空转。
    做法：临时目录接管 QUEUE/LEDGER/TIER 三个路径常量后直接调 main()，E2/E3/E4 判定全走生产代码，不另写匹配。"""
    now = datetime.datetime.now()
    fresh = now.strftime("%Y-%m-%d %H:%M")
    old = (now - datetime.timedelta(hours=3)).strftime("%Y-%m-%d %H:%M")
    saved = (QUEUE, LEDGER, TIER)
    try:
        good = tempfile.mkdtemp(prefix='dispatch-queue-selftest-')
        try:
            _make_fixture(good, [f"| Q1 | 在飞单 | {fresh} | RUNNING | 无 | 写码 |"])
            globals()['QUEUE'] = os.path.join(good, 'docs', '派单队列.md')
            globals()['LEDGER'] = os.path.join(good, 'docs', '派单台账.md')
            globals()['TIER'] = os.path.join(good, 'docs', '并发档位.md')
            rc_good = main()
            assert rc_good == 0, f"新鲜 RUNNING 单应 PASS，rc={rc_good}"
            print('[selftest] 好样本（新鲜在飞单）PASS 通路 OK')
        finally:
            shutil.rmtree(good, ignore_errors=True)

        bad_stale = tempfile.mkdtemp(prefix='dispatch-queue-selftest-')
        try:
            _make_fixture(bad_stale, [f"| Q2 | 僵尸单 | {old} | PENDING | 无 | 验收 |"])
            globals()['QUEUE'] = os.path.join(bad_stale, 'docs', '派单队列.md')
            globals()['LEDGER'] = os.path.join(bad_stale, 'docs', '派单台账.md')
            globals()['TIER'] = os.path.join(bad_stale, 'docs', '并发档位.md')
            rc_stale = main()
            assert rc_stale == 1, f"PENDING 存活 3h 应 E2 判丢单，rc={rc_stale}"
            print('[selftest] 坏样本（PENDING 超 60 分钟存活）E2 FAIL 通路 OK')
        finally:
            shutil.rmtree(bad_stale, ignore_errors=True)

        bad_idle = tempfile.mkdtemp(prefix='dispatch-queue-selftest-')
        try:
            _make_fixture(bad_idle, [f"| Q3 | 积压单 | {fresh} | PENDING | 无 | 验收 |"])
            globals()['QUEUE'] = os.path.join(bad_idle, 'docs', '派单队列.md')
            globals()['LEDGER'] = os.path.join(bad_idle, 'docs', '派单台账.md')
            globals()['TIER'] = os.path.join(bad_idle, 'docs', '并发档位.md')
            rc_idle = main()
            assert rc_idle == 1, f"0 在飞 0 RUNNING 仍有 PENDING 应 E3 空槽空转，rc={rc_idle}"
            print('[selftest] 坏样本（空槽空转）E3 FAIL 通路 OK')
        finally:
            shutil.rmtree(bad_idle, ignore_errors=True)
    finally:
        # 路径常量必须还原：断言失败也不能把生产 ROOT 留在临时目录上（同进程后续门禁复用本模块）
        globals()['QUEUE'], globals()['LEDGER'], globals()['TIER'] = saved

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        sys.exit(selftest())
    sys.exit(main())
