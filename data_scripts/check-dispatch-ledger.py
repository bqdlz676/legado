#!/usr/bin/env python3
"""门禁 ⑯ 台账新鲜度守卫（2026-11-24 并发机制空转事故复盘产物）。

背景：规则停在"文档 + 脚本"层，没有接进"派单动作"链路——create_agent 之后
没人登记台账，门禁又只读台账，于是 ⑥/⑨ 每次必过，并发约束被架空。
本脚本把"派单即登记"变成机械判据：

  FAIL 条件（退出码 1）：
    ① HEAD 提交时间比台账 mtime 新 > 60 分钟 → 有提交/派单但台账未登记；
    ② 台账 `在飞` 行数 > 5 → 超并发硬上限（硬顶 8 档也只允许 ≤8，这里按
       满编 5 基线判，超限先红，档位放宽由 ⑨ 读 `docs/并发档位.md` 复核）。
  WARN 条件（退出码 0，仅提示）：
    ③ 台账里没有任何今年今日行 → 当日尚未派单/登记，提示核对。

为什么新鲜度用 60 分钟：派单与台账回写必须在同一轮内完成；超过 1 小时
仍未登记，基本可判定登记动作被当成"事后动作"漏掉了（事故当晚台账冻结
8h45m，20+ 单零登记）。
"""
import datetime
import os
import json
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEDGER = os.path.join(ROOT, "docs", "派单台账.md")
FRESHNESS_MINUTES = 60
HARD_CAP = 5  # 基线满编 5；档位放宽由 ⑨ 读并发档位文件复核，此处只兜底硬上限


def head_commit_ts() -> float:
    try:
        out = subprocess.run(
            ["git", "log", "-1", "--format=%ct"],
            cwd=ROOT, capture_output=True, text=True, timeout=10,
        )
        return float(out.stdout.strip() or 0)
    except Exception:
        return 0.0


def main() -> int:
    fails: list[str] = []
    warns: list[str] = []

    if not os.path.isfile(LEDGER):
        print(f"FAIL 缺台账 docs/派单台账.md")
        return 1

    text = open(LEDGER, encoding="utf-8").read()

    # ① 新鲜度：HEAD 比台账新 >60 分钟 → FAIL
    ledger_mtime = os.path.getmtime(LEDGER)
    head_ts = head_commit_ts()
    if head_ts > 0:
        gap_min = (head_ts - ledger_mtime) / 60.0
        if gap_min > FRESHNESS_MINUTES:
            fails.append(
                f"HEAD 比台账新 {gap_min:.0f} 分钟（> {FRESHNESS_MINUTES}），有提交/派单未登记台账"
            )
        else:
            print(f"ok    台账新鲜度：台账 mtime 距 HEAD {gap_min:.0f} 分钟内")

    # ①b 内容对账（M-33/M-44）：mtime 只能证明"文件被动过"，证明不了"这一单登记了"。
    #     台账可被 touch 刷新而内容缺行（拿位置/时间当内容），也可内容齐全而 mtime 陈旧。
    #     判据改为身份对账：派单队列里的每个 Q 号必须在台账文本中出现。
    queue_path = os.path.join(os.path.dirname(LEDGER), '派单队列.md')
    if os.path.isfile(queue_path):
        qids = sorted(set(re.findall(r'Q-\d+', open(queue_path, encoding='utf-8').read())))
        missing = [q for q in qids if q not in text]
        # 历史欠账走棘轮基线（只在变小方向更新）：基线里的号允许暂缺，基线外缺号一律 FAIL。
        # 为什么不用"Q 号 >= 200"这类位置口径：编号是身份，阈值是位置，身份才不可伪造。
        base_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dispatch-ledger-baseline.json')
        base = set()
        if os.path.isfile(base_path):
            base = set(json.load(open(base_path, encoding='utf-8')).get('missing', []))
            # 基线自查（M-46）：基线文件自己也能被改——若允许扩张，"新欠账"写进基线就能洗白。
            # 判据：HEAD 基线必须 ⊆ origin/main 基线（棘轮只许缩小）；无远端引用时退回上一条提交比较。
            import subprocess
            ref = None
            for cand in ('origin/main', 'HEAD~1'):
                r = subprocess.run(['git', 'show', '%s:data_scripts/dispatch-ledger-baseline.json' % cand],
                                   cwd=os.path.dirname(os.path.abspath(__file__)) + '/..',
                                   capture_output=True, text=True)
                if r.returncode == 0:
                    ref = set(json.loads(r.stdout).get('missing', [])); break
            if ref is not None and base - ref:
                fails.append('台账基线扩张 %d 条（棘轮只许缩小）：%s'
                             % (len(base - ref), '、'.join(sorted(base - ref)[:6])))
        new_missing = [q for q in missing if q not in base]
        if new_missing:
            fails.append('台账缺 %d 个队列已登记的派单号（基线外新增欠账）：%s'
                         % (len(new_missing), '、'.join(new_missing[:8])))
        else:
            print('ok    台账内容对账：队列 %d 个 Q 号均在台账内或已在历史基线（基线 %d 条待销）'
                  % (len(qids), len(base)))

    # ①c 幻影路径（M-31 同族）：台账里点名的报告路径必须真实存在，否则等于"记账抵了交付"。
    phantom = []
    for line in text.splitlines():
        if not line.startswith('|') or 'docs/' not in line:
            continue
        # 在飞行点名的"未来报告"尚不存在属正常（DoD 未完），不判幻影。
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if any(c.startswith(('🟡 在飞', '在飞')) for c in cells):
            continue
        # 标了"（预定）"的是计划产物、尚未落盘，属占位不是幻影。
        if '（预定）' in line:
            continue
        for rel in re.findall(r'docs/[^\s|），]+?\.md', line):
            if not os.path.isfile(os.path.join(os.path.dirname(LEDGER), '..', rel)):
                phantom.append(rel)
    if phantom:
        fails.append('台账点名 %d 个不存在的报告路径：%s' % (len(phantom), '、'.join(sorted(set(phantom))[:5])))
    else:
        print('ok    台账路径存在性：点名的报告均实存')

    # ② 在草稿数超硬上限 → FAIL
    # 旧判据只数台账里带"在飞"标记的行：台账不写就没有在飞，等于自己给自己开脱（M-45）。
    # 现判据以队列 RUNNING 行数为真值，台账标记数必须与之相等。
    # "在飞"判定：状态格里出现 在飞，且未终结（有"已交付/作废/DONE"的不算）。旧写死"| 在飞"会漏 🟡 前缀。
    # 只认"状态格以 在飞 打头"的行：备注里提到"在飞"的历史行不算（否则 31 条噪声）。
    ledger_marked = 0
    for line in text.splitlines():
        if not line.startswith('|'):
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if any(c.startswith(('🟡 在飞', '在飞')) for c in cells):
            ledger_marked += 1
    queue_running = 0
    if os.path.isfile(queue_path):
        for line in open(queue_path, encoding='utf-8').read().splitlines():
            if re.match(r'^\s*\|?\s*Q-\d+', line) and re.search(r'\|\s*RUNNING', line):
                queue_running += 1
    if ledger_marked != queue_running:
        fails.append('在飞数不一致：台账标记 %d 条 / 队列 RUNNING %d 行（台账必须如实反映在飞）'
                     % (ledger_marked, queue_running))
    open_count = queue_running
    if open_count > HARD_CAP:
        fails.append(f"在飞 {open_count} 条，超硬上限 {HARD_CAP}")
    else:
        print(f"ok    在飞计数：{open_count} 条（硬上限 {HARD_CAP}）")

    # ③ 当日行缺失 → WARN 不 FAIL
    today = datetime.date.today()
    today_tokens = (today.strftime("%Y-%m-%d"), today.strftime("%m-%d"))
    if not any(tok in text for tok in today_tokens):
        warns.append(f"台账无 {today_tokens[0]} / {today_tokens[1]} 当日行（今日尚未登记派单）")

    for w in warns:
        print(f"WARN  {w}")
    for f in fails:
        print(f"FAIL  {f}")

    if fails:
        return 1
    print("PASS  台账新鲜度与在飞计数合格")
    return 0


if __name__ == "__main__":
    sys.exit(main())
