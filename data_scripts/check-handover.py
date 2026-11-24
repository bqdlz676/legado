#!/usr/bin/env python3
"""门禁 ㉖ 接手链守卫（F-91 接手链，Q-21 落地）。

为什么存在：接手链＝总账（入口 + 接力棒）+ 门禁（本脚本）+ 溯源（快照与棒次行）。
用户原话两条依据：
  ①「约定一个什么东西，让我可以给下一轮 ai 一个启动钥匙，去读你留下的实时更新的备案」；
  ②「你要让下一个 ai 在读了文件之后也要明白它也只是一个接盘手，它还会有下一个
    接盘手，所以你做的事情，比如更新实时备案它也要做」。
语义层（入口 §0.5/§6/§7/§9、接力棒六段、.handover-key）由总控落好，本脚本只做五条
机械判据，不改语义、不改名。

五条判据（FAIL 条件，退出码 1）：
  a) 钥匙可解析：入口与 `.handover-key` 均存在，且钥匙内含入口相对路径；
  b) 快照新鲜度：快照生成时间距今 ≤ 24h，且快照 HEAD 与当前 HEAD 差距 ≤ 20 提交；
  c) 接力棒末行六段齐全：棒次 / 起止 / 收口 HEAD / 收口 CI / 未完成三项 / 下一棒第一步；
  d) 接力棒只增不改：上一提交版本是当前版本的前缀，行数单调不减；
  e) 当日流水：`docs/落文-对话流水-<今天>.md` 存在且非空。

`--selftest`：造夹具跑 PASS / FAIL 两条通路后自删。
"""
import datetime
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENTRY_REL = os.path.join("docs", "接手", "00-入口.md")
BATON_REL = os.path.join("docs", "接手", "接力棒.md")
KEY_NAME = ".handover-key"
MAX_SNAPSHOT_AGE_HOURS = 24
MAX_HEAD_GAP = 20

LABELS = ["棒次", "起止", "收口 HEAD", "收口 CI", "未完成三项", "下一棒第一步"]

TIME_RE = re.compile(r"生成时间：(\d{4}-\d{2}-\d{2} \d{2}:\d{2})")
HEAD_RE = re.compile(r"HEAD：`([0-9a-f]{7,40})`")
CST = datetime.timezone(datetime.timedelta(hours=8))


def git_head(root: str):
    try:
        sha = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root,
                             capture_output=True, text=True, timeout=10)
        return sha.stdout.strip() or None
    except Exception:
        return None


def git_prev_file(root: str, rel: str):
    """取上一提交版本全文；非 git 仓库或文件未跟踪返回 None。"""
    try:
        out = subprocess.run(["git", "show", f"HEAD~1:{rel}"], cwd=root,
                             capture_output=True, text=True, timeout=10)
        if out.returncode == 0:
            return out.stdout
    except Exception:
        pass
    return None


def head_gap(root: str, snap_sha: str):
    """快照 HEAD 到当前 HEAD 的提交差距；git 不可用返回 None（跳过距离判据）。"""
    cur = git_head(root)
    if not cur:
        return None
    if snap_sha == cur or cur.startswith(snap_sha):
        return 0
    try:
        out = subprocess.run(["git", "rev-list", "--count", f"{snap_sha}..HEAD"],
                             cwd=root, capture_output=True, text=True, timeout=10)
        if out.returncode != 0:
            return None  # 快照 sha 不在当前历史上，距离不可算
        return int(out.stdout.strip())
    except Exception:
        return None


def run_checks(root: str, prev_baton: str | None = None):
    """返回 (ok, counts, problems)；counts 记五条过/未过，problems 记 FAIL 原因。"""
    counts: dict[str, bool] = {}
    problems: list[str] = []

    entry = os.path.join(root, ENTRY_REL)
    key = os.path.join(root, KEY_NAME)
    baton = os.path.join(root, BATON_REL)

    # a) 钥匙可解析
    if os.path.isfile(entry) and os.path.isfile(key):
        key_text = open(key, encoding="utf-8").read()
        if ENTRY_REL.replace(os.sep, "/") in key_text:
            counts["a钥匙"] = True
        else:
            counts["a钥匙"] = False
            problems.append("a 钥匙未指向入口路径（.handover-key 内缺 docs/接手/00-入口.md）")
    else:
        counts["a钥匙"] = False
        missing = "入口" if not os.path.isfile(entry) else ".handover-key"
        problems.append(f"a 钥匙可解析：缺{missing}")

    # b) 快照新鲜度
    snap_time = None
    snap_sha = None
    if os.path.isfile(entry):
        text = open(entry, encoding="utf-8").read()
        mt = TIME_RE.search(text)
        mh = HEAD_RE.search(text)
        snap_time = mt.group(1) if mt else None
        snap_sha = mh.group(1) if mh else None

    fresh_ok = True
    if snap_time is None:
        fresh_ok = False
        problems.append("b 快照缺生成时间（## 6 快照段未刷新）")
    else:
        try:
            snap_dt = datetime.datetime.strptime(snap_time, "%Y-%m-%d %H:%M").replace(tzinfo=CST)
            age_h = (datetime.datetime.now(CST) - snap_dt).total_seconds() / 3600
            if age_h > MAX_SNAPSHOT_AGE_HOURS:
                fresh_ok = False
                problems.append(f"b 快照过期 {age_h:.1f}h（> {MAX_SNAPSHOT_AGE_HOURS}h），未随本棒收口刷新")
        except ValueError:
            fresh_ok = False
            problems.append(f"b 快照生成时间不可解析：{snap_time}")

    if snap_sha:
        gap = head_gap(root, snap_sha)
        if gap is not None and gap > MAX_HEAD_GAP:
            fresh_ok = False
            problems.append(f"b 快照 HEAD 落后 {gap} 提交（> {MAX_HEAD_GAP}），快照未跟上收口提交")
    elif os.path.isfile(entry):
        fresh_ok = False
        problems.append("b 快照缺 HEAD 行")
    counts["b新鲜度"] = fresh_ok

    # c) 接力棒末段六段齐全
    c_ok = False
    if os.path.isfile(baton):
        baton_text = open(baton, encoding="utf-8").read()
        # 末段 = 最后一个「## 第 N 棒」标题之后、「## 接力义务」之前
        blocks = re.split(r"^## 第 ", baton_text, flags=re.M)
        last = blocks[-1] if len(blocks) > 1 else baton_text
        last = re.split(r"^## ", last, flags=re.M)[0]
        missing = [lab for lab in LABELS if f"- {lab}：" not in last]
        if not missing:
            c_ok = True
        else:
            problems.append(f"c 接力棒末段缺段：{', '.join(missing)}")
    else:
        problems.append("c 缺 docs/接手/接力棒.md")
    counts["c棒次六段"] = c_ok

    # d) 接力棒只增不改：上一版本是当前前缀，行数单调不减
    d_ok = True
    if os.path.isfile(baton):
        prev = prev_baton if prev_baton is not None else git_prev_file(root, BATON_REL)
        cur_text = open(baton, encoding="utf-8").read()
        if prev is not None:
            prev_lines, cur_lines = prev.splitlines(), cur_text.splitlines()
            if len(cur_lines) < len(prev_lines):
                d_ok = False
                problems.append(f"d 接力棒行数缩减（{len(prev_lines)} → {len(cur_lines)}），疑似改写历史行")
            elif cur_text[:len(prev)] != prev:
                d_ok = False
                problems.append("d 接力棒历史前缀被改动（只增不改被破坏）")
    counts["d只增不改"] = d_ok

    # e) 当日流水存在且非空
    today = datetime.datetime.now(CST).strftime("%Y-%m-%d")
    flow = os.path.join(root, "docs", f"落文-对话流水-{today}.md")
    if os.path.isfile(flow) and os.path.getsize(flow) > 0:
        counts["e当日流水"] = True
    else:
        counts["e当日流水"] = False
        problems.append(f"e 当日流水 docs/落文-对话流水-{today}.md 缺失或为空")

    ok = all(counts.values())
    return ok, counts, problems


def selftest() -> int:
    """双向自测：合规夹具 PASS；五类违规夹具 FAIL。跑完自删。"""
    tmp = tempfile.mkdtemp(prefix="handover-selftest-")
    os.makedirs(os.path.join(tmp, "docs", "接手"), exist_ok=True)

    def w(rel, content):
        p = os.path.join(tmp, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write(content)

    now = datetime.datetime.now(CST).strftime("%Y-%m-%d %H:%M")
    today = datetime.datetime.now(CST).strftime("%Y-%m-%d")

    entry_ok = (
        "# 入口\n\n## 6. 【快照段】当前状态\n\n"
        f"- 生成时间：{now}（CST）\n- HEAD：`abc1234`（自检夹具）\n\n"
        "## 7. 【快照段】下一步三项\n- 无\n"
    )
    baton_ok = (
        "# 接力棒\n\n## 第 1 棒\n"
        "- 棒次：第 1 棒\n- 起止：2026-11-25 00:00 ~ 2026-11-25 01:00（CST）\n"
        "- 收口 HEAD：`abc1234`\n- 收口 CI：绿\n- 未完成三项：①②③\n"
        "- 下一棒第一步：跑 STEP 0 三条命令\n\n"
    )
    # 旧版必须是当前版的真前缀（只增不改：旧行原样保留在文件头部）
    prev_baton = baton_ok.split("- 未完成三项")[0]

    w(ENTRY_REL, entry_ok)
    w(KEY_NAME, f"落文接手钥匙：读 {ENTRY_REL}，先跑 STEP 0。\n")
    w(BATON_REL, baton_ok)
    w(os.path.join("docs", f"落文-对话流水-{today}.md"), "# 当日流水\n原话一句。\n")

    ok, counts, problems = run_checks(tmp, prev_baton=prev_baton)
    assert ok, f"合规夹具应 PASS，实际 FAIL：{problems}"
    print("[selftest] 合规夹具 PASS 通路 OK：", counts)

    # FAIL 通路：钥匙缺路径、快照过期 + 缺 HEAD、棒次缺段、历史被改、当日流水缺失
    w(KEY_NAME, "落文接手钥匙：没有路径。\n")
    old_time = (datetime.datetime.now(CST) - datetime.timedelta(days=2)).strftime("%Y-%m-%d %H:%M")
    w(ENTRY_REL, f"# 入口\n\n## 6. 【快照段】\n\n- 生成时间：{old_time}（CST）\n\n## 7. 下一步\n")
    w(BATON_REL, "# 接力棒\n\n## 第 1 棒\n- 棒次：第 1 棒\n- 起止：x\n\n")
    w(os.path.join("docs", f"落文-对话流水-{today}.md"), "")
    ok2, _, problems2 = run_checks(tmp, prev_baton=baton_ok)
    assert not ok2, "违规夹具应 FAIL，实际 PASS"
    print("[selftest] 违规夹具 FAIL 通路 OK，命中：", problems2)

    shutil.rmtree(tmp)
    print("[selftest] 夹具已自删，双向自测通过。")
    return 0


def main() -> int:
    if "--selftest" in sys.argv:
        return selftest()
    ok, counts, problems = run_checks(ROOT)
    for k, v in counts.items():
        print(f"{'ok  ' if v else 'FAIL'} ㉖ {k}：{'过' if v else '未过'}")
    for p in problems:
        print(f"FAIL  ㉖ {p}")
    if ok:
        print("HANDOVER: PASS")
        return 0
    print("HANDOVER: FAIL")
    return 1


if __name__ == "__main__":
    sys.exit(main())
