#!/usr/bin/env python3
"""接手快照段自动重写（F-91 接手链，Q-21 落地）。

为什么需要：入口文件的【快照段】是给下一棒 AI 的开机状态；靠人手刷新必然过期
（2026-11-25 凌晨实证：快照停在上一棒 HEAD/CI，下一棒在过期状态上开工）。本脚本
每棒收口时跑一次，只重写 §6【快照段】机器可生成的状态字段，§7 下一步三项与所有
稳定段逐字保留。

重写内容（§6 快照段）：
  - 生成时间（CST）
  - HEAD：短 sha + subject（git 取）
  - CI：GitHub Actions 最近三 run 的 status/conclusion/head sha（token 读
    `~/.git-credentials` 的 `ghp_` 串；API 不可达时降级注明，不崩）
  - 在飞子代理：`docs/派单台账.md` 状态列 = 在飞 的条目
  - 队列 PENDING 队首三项：`docs/派单队列.md` 前三个 PENDING 行
  - STEP 0 三命令结论：rule-gate.sh / check-spec-flow.py / check-rule-ledger.py

边界：只动 §6 快照段；改前把稳定段 + §7 文本切块留存，写回前断言两块逐字一致
（备份断言），不一致则中止不写。不解析、不改写任何业务语义。
"""
import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENTRY = os.path.join(ROOT, "docs", "接手", "00-入口.md")
LEDGER = os.path.join(ROOT, "docs", "派单台账.md")
QUEUE = os.path.join(ROOT, "docs", "派单队列.md")
CST = datetime.timezone(datetime.timedelta(hours=8))

START_RE = re.compile(r"^## 6\. 【快照段】.*$", re.M)
END_RE = re.compile(r"^## 7\. 【快照段】.*$", re.M)

# STEP 0 三条自检命令（与入口 §0 逐字一致；结论取输出中的 PASS 标记串）
STEP0 = [
    ("rule-gate.sh", ["bash", "data_scripts/rule-gate.sh"], "GATE: PASS"),
    ("check-spec-flow.py", ["python3", "data_scripts/check-spec-flow.py"], "SPEC-FLOW: PASS"),
    ("check-rule-ledger.py", ["python3", "data_scripts/check-rule-ledger.py"], "RULE-LEDGER: PASS"),
]


def git_head():
    """返回 (短 sha, subject)；非 git 仓库时返回 (未知, 未知)。"""
    try:
        sha = subprocess.run(["git", "rev-parse", "--short=7", "HEAD"],
                             cwd=ROOT, capture_output=True, text=True, timeout=10)
        subj = subprocess.run(["git", "log", "-1", "--format=%s"],
                              cwd=ROOT, capture_output=True, text=True, timeout=10)
        return sha.stdout.strip() or "未知", subj.stdout.strip() or "未知"
    except Exception:
        return "未知", "未知"


def read_token():
    """从 ~/.git-credentials 取 github.com token；取不到返回 None（不回显）。"""
    cred = os.path.expanduser("~/.git-credentials")
    try:
        for line in open(cred, encoding="utf-8"):
            m = re.match(r"https://[^:]+:([^@]+)@github\.com", line.strip())
            if m:
                return m.group(1)
    except FileNotFoundError:
        pass
    return None


def ci_runs():
    """最近三 main 分支 run：[(sha7, status, conclusion)]；失败/不可达返回 []。"""
    token = read_token()
    if not token:
        return [], "无 token"
    repo = subprocess.run(["git", "remote", "get-url", "origin"],
                          cwd=ROOT, capture_output=True, text=True, timeout=10)
    slug = re.sub(r"\.git$", "", repo.stdout.strip().removeprefix("https://github.com/"))
    url = f"https://api.github.com/repos/{slug}/actions/runs?branch=main&per_page=3"
    req = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "handover-snapshot",
    })
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.load(resp)
        out = []
        for run in data.get("workflow_runs", [])[:3]:
            sha = (run.get("head_sha") or "")[:7]
            out.append((sha, run.get("status") or "?", run.get("conclusion") or "?"))
        return out, None
    except Exception as exc:  # 网络/鉴权/限流都降级，不让快照脚本崩
        return [], str(exc)[:80]


def flying_items():
    """台账在飞条目：[(标题, 代理)]；台账缺失返回 []。"""
    if not os.path.isfile(LEDGER):
        return []
    items = []
    for line in open(LEDGER, encoding="utf-8"):
        if not line.startswith("|") or "在飞" not in line:
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) >= 2 and cells[0] not in ("缺陷/条目",) and not set(cells[0]) <= set("-: "):
            items.append((cells[0], cells[1] if len(cells) > 1 else ""))
    return items


def queue_pending():
    """队列前三个 PENDING 行：[Q-id 标题]；队列缺失返回 []。"""
    if not os.path.isfile(QUEUE):
        return []
    rows = []
    for line in open(QUEUE, encoding="utf-8"):
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 4 or cells[0] == "序号" or set(cells[0]) <= set("-: "):
            continue
        if "PENDING" in cells[3]:
            rows.append(f"{cells[0]} {cells[1]}")
        if len(rows) == 3:
            break
    return rows


def step0_conclusions():
    """跑 STEP 0 三命令，返回 [(名称, 结论)]；结论 PASS/FAIL/不可达。"""
    out = []
    for name, cmd, marker in STEP0:
        try:
            proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=120)
            text = proc.stdout + proc.stderr
            out.append((name, "PASS" if marker in text else "FAIL"))
        except Exception:
            out.append((name, "不可达"))
    return out


def build_snapshot():
    """生成 §6 快照段正文（不含 §7）。"""
    now = datetime.datetime.now(CST).strftime("%Y-%m-%d %H:%M")
    sha, subject = git_head()
    runs, ci_err = ci_runs()
    fly = flying_items()
    pend = queue_pending()
    step0 = step0_conclusions()

    lines = ["## 6. 【快照段】当前状态（本段由 `data_scripts/update-handover.py` 自动重写，手改无效）", ""]
    lines.append(f"- 生成时间：{now}（CST）")
    lines.append(f"- HEAD：`{sha}`（{subject}）")
    if runs:
        ci_txt = "；".join(f"`{s}` {st}/{co}" for s, st, co in runs)
        lines.append(f"- CI：最近三 main run——{ci_txt}")
    else:
        lines.append(f"- CI：API 不可达（{ci_err or '无数据'}），以 STEP 0 rule-gate 结论为准")
    fly_txt = "、".join(t for t, _ in fly[:5]) or "无"
    if len(fly) > 5:
        fly_txt += " 等"
    lines.append(f"- 在飞 {len(fly)} 槽：{fly_txt}")
    lines.append("- 队列队首（PENDING 按序，前三项）：" + (" → ".join(pend) if pend else "无 PENDING"))
    lines.append("- STEP 0 结论：" + "；".join(f"{n} {c}" for n, c in step0))
    lines.append("")
    lines.append("---")
    return "\n".join(lines) + "\n\n"


def rewrite(dry_run: bool) -> int:
    if not os.path.isfile(ENTRY):
        print(f"FAIL 缺入口 {ENTRY}")
        return 1
    text = open(ENTRY, encoding="utf-8").read()
    m_start = START_RE.search(text)
    m_end = END_RE.search(text)
    if not m_start or not m_end:
        print("FAIL 快照段标记行缺失（## 6. / ## 7.【快照段】），中止不写")
        return 1

    # 改前备份：切块留存稳定段（pre）与 §7 及之后（post），写回前断言逐字一致
    pre, post = text[:m_start.start()], text[m_end.start():]
    snapshot = build_snapshot()
    new_text = pre + snapshot + post

    # 备份断言：稳定段与 §7 必须逐字保留，任一漂移即中止
    if not new_text.startswith(pre) or not new_text.endswith(post):
        print("FAIL 备份断言失败：稳定段/§7 被改动，中止不写")
        return 1

    if dry_run:
        print("[dry-run] 快照段预览：")
        print(snapshot)
        return 0

    with open(ENTRY, "w", encoding="utf-8") as f:
        f.write(new_text)
    print(f"OK 快照段已重写：{ENTRY}（稳定段/§7 逐字保留断言通过）")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只预览新快照，不写文件")
    args = ap.parse_args()
    sys.exit(rewrite(args.dry_run))


if __name__ == "__main__":
    main()
