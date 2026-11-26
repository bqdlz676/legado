#!/usr/bin/env python3
"""接手快照段自动重写（F-91 接手链，Q-21 落地；M-05 收口）。

为什么需要：入口文件的【快照段】是给下一棒 AI 的开机状态；靠人手刷新必然过期
（2026-11-25 凌晨实证：快照停在上一棒 HEAD/CI，下一棒在过期状态上开工）。本脚本
每棒收口时跑一次，重写 §6【快照段】与 §7【下一步三项】两段机器可生成的内容，
稳定段（§0~§5、§8~§10）逐字保留。

M-05 根因与本版改动：旧版只重写 §6，并在写回前断言「§7 逐字保留」——等于把手写的
§7「下一步三项」永久固化；当日已完成的指引（盯旧 CI、派已结单的 Q-22/Q-21）被时间
打成谎言，下一棒照做就是空转。本版起 §7 也由脚本从两份事实源机械生成：
  ① `docs/派单队列.md` 中 RUNNING / PENDING 条目（标题 + 判据锚 + 行号）；
  ② `docs/问题清单.md` B 段（P0）仍标 🔴 的条目（编号 + 一行摘要 + 行号）。
合并排序：在飞 RUNNING 优先，其次 P0 🔴，最后 PENDING；取前三项。每条必须带
「为什么是它」与事实源行号锚点，禁空泛；两份事实源都取不到时写收口口径，不留空。

为什么删掉「§7 逐字保留」断言：§7 本来就是每棒都会过期的状态段，逐字保留断言只会
把过期指引锁死；稳定段断言收窄为只校验 §1~§5 与 §8/§9/§10（即 §6 之前与 §8
之后的文本）逐字不变，§6/§7 允许被脚本重写。

防御（Q-68 并发重构队列，列数会变）：先读队列表头按列名定位「标题」「状态」「序号」
「依赖/判据」列；表头定位不到就退化为「只从问题清单 B 段取 🔴 前三项」，并在 §7
写明退化原因，不写空、不抛异常中断。

网络降级：CI 走 GitHub API；token 缺失 / API 不可达时降级注明「CI 未知」，不中断。
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
ISSUES = os.path.join(ROOT, "docs", "问题清单.md")
CST = datetime.timezone(datetime.timedelta(hours=8))

START_RE = re.compile(r"^## 6\. .*$", re.M)
EIGHT_RE = re.compile(r"^## 8\. .*$", re.M)

# STEP 0 三条自检命令（与入口 §0 逐字一致；结论取输出中的 PASS 标记串）
STEP0 = [
    ("rule-gate.sh", ["bash", "data_scripts/rule-gate.sh"], "GATE: PASS"),
    ("check-spec-flow.py", ["python3", "data_scripts/check-spec-flow.py"], "SPEC-FLOW: PASS"),
    ("check-rule-ledger.py", ["python3", "data_scripts/check-rule-ledger.py"], "RULE-LEDGER: PASS"),
]

# §7 合并排序口径：在飞 > P0 🔴 > 待派 PENDING（数字越小越靠前）
PRIORITY = {"RUNNING": 0, "P0": 1, "PENDING": 2}
KIND_LABEL = {"RUNNING": "在飞", "P0": "P0🔴", "PENDING": "待派"}


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


def clean_text(raw: str) -> str:
    """剥掉 markdown 加粗/反引号并压空白，供摘要与标题回写。"""
    txt = raw.replace("**", "").replace("`", "").strip()
    return re.sub(r"\s+", " ", txt)


def _split_row(line: str) -> list[str]:
    return [c.strip() for c in line.strip().strip("|").split("|")]


def _is_separator(cells: list[str]) -> bool:
    return bool(cells) and set("".join(cells)) <= set("-: ")


def parse_queue():
    """解析派单队列：返回 (items, degrade)。

    items: [{"kind": RUNNING/PENDING, "qid", "title", "anchor", "line"}]，按文件顺序。
    degrade: 字符串，表头解析失败时说明原因；成功为 None。
    防御：先按表头列名定位列，列数漂移（Q-68 重构）时不按死列号硬取。
    """
    if not os.path.isfile(QUEUE):
        return [], "派单队列.md 不存在"
    lines = open(QUEUE, encoding="utf-8").read().splitlines()
    col: dict[str, int] = {}
    header_idx = None
    for i, line in enumerate(lines):
        if not line.startswith("|"):
            continue
        cells = _split_row(line)
        if any("标题" in c for c in cells) and any("状态" in c for c in cells):
            header_idx = i
            for j, c in enumerate(cells):
                if "标题" in c:
                    col["title"] = j
                elif "状态" in c:
                    col["status"] = j
                elif "序号" in c or "编号" in c:
                    col["id"] = j
                elif "依赖" in c or "判据" in c:
                    col.setdefault("anchor", j)
            break
    if header_idx is None or "status" not in col or "title" not in col:
        return [], "队列表头未定位到「标题」「状态」列（Q-68 重构中列数可能漂移），队列段退化"

    items = []
    for offset, line in enumerate(lines[header_idx + 1:], start=header_idx + 2):
        # 主表止于下一个小节标题；「历史追加账」留档行不参与状态机解析（队列结构声明）
        if re.match(r"^##\s", line):
            break
        if not line.startswith("|"):
            continue
        cells = _split_row(line)
        if _is_separator(cells) or len(cells) <= col["status"]:
            continue
        status = cells[col["status"]]
        if "RUNNING" in status:
            kind = "RUNNING"
        elif "PENDING" in status:
            kind = "PENDING"
        else:
            continue
        qid = clean_text(cells[col["id"]]) if "id" in col and len(cells) > col["id"] else "?"
        title = clean_text(cells[col["title"]]) if len(cells) > col["title"] else ""
        anchor = clean_text(cells[col["anchor"]]) if "anchor" in col and len(cells) > col["anchor"] else ""
        items.append({"kind": kind, "qid": qid, "title": title,
                      "anchor": anchor, "line": offset})
    return items, None


def parse_issues():
    """解析问题清单 B 段（P0）仍 🔴 的条目：返回 (items, degrade)。

    items: [{"id", "summary", "line"}]，按文件顺序；degrade 为失败原因或 None。
    只取 B 段；C 段及以后的 P1/P2 不进下一步三项。
    """
    if not os.path.isfile(ISSUES):
        return [], "问题清单.md 不存在"
    lines = open(ISSUES, encoding="utf-8").read().splitlines()
    in_b = False
    col: dict[str, int] = {}
    items = []
    for offset, line in enumerate(lines, start=1):
        if re.match(r"^##\s*B[\.、\s]", line):
            in_b = True
            continue
        if in_b and re.match(r"^##\s", line):
            break
        if not in_b or not line.startswith("|"):
            continue
        cells = _split_row(line)
        if not col:
            if any("编号" in c for c in cells) and any("状态" in c for c in cells):
                for j, c in enumerate(cells):
                    if "编号" in c:
                        col["id"] = j
                    elif "问题" in c:
                        col["problem"] = j
                    elif "状态" in c:
                        col["status"] = j
                continue
        if _is_separator(cells) or "status" not in col or len(cells) <= col["status"]:
            continue
        if "🔴" not in cells[col["status"]]:
            continue
        issue_id = clean_text(cells[col["id"]]) if "id" in col and len(cells) > col["id"] else "?"
        summary = clean_text(cells[col["problem"]])[:50] if "problem" in col and len(cells) > col["problem"] else ""
        items.append({"id": issue_id, "summary": summary, "line": offset})
    return items, None


def next_actions(queue_items: list[dict], issues: list[dict]) -> list[dict]:
    """合并两份事实源并排序：RUNNING > P0 🔴 > PENDING；同类内按行号先后；取前三。"""
    merged: list[dict] = []
    for it in queue_items:
        merged.append({"priority": PRIORITY[it["kind"]], "src": "queue", **it})
    for it in issues:
        merged.append({"priority": PRIORITY["P0"], "kind": "P0", "src": "issue", **it})
    merged.sort(key=lambda x: (x["priority"], x["line"]))
    return merged[:3]


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


def build_snapshot(queue_items: list[dict]) -> str:
    """生成 §6 快照段正文。"""
    now = datetime.datetime.now(CST).strftime("%Y-%m-%d %H:%M")
    sha, subject = git_head()
    runs, ci_err = ci_runs()
    fly = flying_items()
    pend = [f"{x['qid']} {x['title']}" for x in queue_items if x["kind"] == "PENDING"]
    pend += [f"{x['qid']} {x['title']}" for x in queue_items if x["kind"] == "RUNNING"]
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
    lines.append(f"- 在飞 {len(fly)} 个派单：{fly_txt}")
    lines.append("- 队列队首（PENDING 按序，其次 RUNNING，前三项）：" + (" → ".join(pend[:3]) if pend else "无 PENDING/RUNNING"))
    lines.append("- STEP 0 结论：" + "；".join(f"{n} {c}" for n, c in step0))
    lines.append("")
    lines.append("---")
    return "\n".join(lines) + "\n\n"


def build_next(queue_items: list[dict], queue_degrade: str | None, issues: list[dict]) -> str:
    """生成 §7 下一步三项：只从事实源机械取，每条带行号锚点；全空写收口口径。"""
    lines = ["## 7. 【快照段】下一步三项（本段由 `data_scripts/update-handover.py` 按事实源机械生成，手改无效）", ""]
    lines.append("> 排序口径：在飞（RUNNING）优先，其次问题清单 B 段 P0 🔴，最后 PENDING；每条附事实源行号锚点，可回源，不写空泛指引。")
    lines.append("")
    if queue_degrade:
        lines.append(f"> 队列退化：{queue_degrade}；本棒下一步仅取自问题清单 B 段 🔴。")
        lines.append("")

    actions = next_actions(queue_items, issues)
    if not actions:
        lines.append("本棒无待办，按 §9 交接义务收口并交付。")
    else:
        for n, a in enumerate(actions, 1):
            if a["src"] == "queue":
                why = f"`docs/派单队列.md:{a['line']}` 状态 {a['kind']}"
                if a.get("anchor"):
                    why += f"；判据锚 {a['anchor']}"
                head = f"[{KIND_LABEL[a['kind']]}] {a['qid']} {a['title']}"
            else:
                why = f"`docs/问题清单.md:{a['line']}` B 段状态 🔴 未收口"
                head = f"[P0🔴] {a['id']} {a['summary']}"
            lines.append(f"{n}. **{head}** — 为什么是它：{why}。")
    lines.append("")
    lines.append("---")
    return "\n".join(lines).rstrip("\n") + "\n\n"


def rewrite(dry_run: bool) -> int:
    if not os.path.isfile(ENTRY):
        print(f"FAIL 缺入口 {ENTRY}")
        return 1
    text = open(ENTRY, encoding="utf-8").read()
    m_start = START_RE.search(text)
    m_end8 = EIGHT_RE.search(text)
    if not m_start or not m_end8:
        print("FAIL 快照段标记行缺失（## 6. / ## 8.），中止不写")
        return 1

    # 改前备份：pre = §6 之前的稳定段（§0~§5），post = §8 起的稳定段（§8/§9/§10）；
    # §6/§7 属机器生成段，不在逐字保留断言内（M-05：旧版把 §7 固化导致过期指引锁死）。
    pre, post = text[:m_start.start()], text[m_end8.start():]

    queue_items, queue_degrade = parse_queue()
    issues, _ = parse_issues()
    snapshot = build_snapshot(queue_items)
    nxt = build_next(queue_items, queue_degrade, issues)
    new_text = pre + snapshot + nxt + post

    # 备份断言：稳定段必须逐字保留，任一漂移即中止；§6/§7 允许被脚本重写。
    if not new_text.startswith(pre) or not new_text.endswith(post):
        print("FAIL 备份断言失败：稳定段（§0~§5 / §8~§10）被改动，中止不写")
        return 1

    if dry_run:
        print("[dry-run] §6 快照段预览：")
        print(snapshot)
        print("[dry-run] §7 下一步三项预览：")
        print(nxt)
        return 0

    with open(ENTRY, "w", encoding="utf-8") as f:
        f.write(new_text)
    print(f"OK §6/§7 已由事实源重写：{ENTRY}（稳定段逐字保留断言通过；§7 不再固化手写指引）")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只预览新快照与下一步，不写文件")
    args = ap.parse_args()
    sys.exit(rewrite(args.dry_run))


if __name__ == "__main__":
    main()
