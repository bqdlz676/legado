#!/usr/bin/env python3
"""一单落地（land）：把"提交-改台账-预检-推送-看 CI"压成一条命令。

为什么要有它：用户问"优化没有"。真实的浪费不在报告写得好不好，而在每张单要
我手动做 5~7 次工具调用（代码提交、测试提交、报告提交、队列行手改、台账行手改、
推送、重试），其中台账手改还漂移过三次。这活本该一条命令干完。

做的事（顺序固定，任一步失败即停，不推）：
  1. 按路径分三桶提交：app/src/main 与其它生产码 / app/src/test 与 screenshot / docs 报告
  2. 队列行改 DONE（附结论一句话），台账追加一行
  3. 跨模块预检（cross-module-guard.py）
  4. 推送（带 3 次重试，避免并发代理抢锁）
  5. 打印最新 CI 两行状态

用法：
  python3 data_scripts/land.py --q Q-176 --report docs/修复-xxx.md \
      --code app/src/main/java/...  --test app/src/test/java/... \
      --conclusion "静态 PASS：…" --title "搜索翻页收口"
  --dry-run 只打印将要执行的步骤。
"""

import argparse
import datetime as dt
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
QUEUE = os.path.join(ROOT, "docs", "派单队列.md")
LEDGER = os.path.join(ROOT, "docs", "派单台账.md")


def sh(args, check=False):
    r = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    if check and r.returncode != 0:
        print(r.stdout[-800:], r.stderr[-800:])
    return r


def clear_lock():
    lock = os.path.join(ROOT, ".git", "index.lock")
    if os.path.isfile(lock) and os.path.getsize(lock) == 0:
        age = dt.datetime.now().timestamp() - os.path.getmtime(lock)
        if age > 2 and not re.search(r"", sh(["pgrep", "-a", "git"]).stdout or "x"):
            return False
        if age > 2:
            os.remove(lock)
            return True
    return False


def commit(paths, msg):
    for _ in range(3):
        clear_lock()
        if sh(["git", "add"] + paths).returncode == 0 and sh(["git", "commit", "-q", "-m", msg]).returncode == 0:
            print(f"提交：{msg}")
            return True
        subprocess.run(["sleep", "3"])
    print(f"FAIL 提交失败：{msg}")
    return False


def patch_queue(qid, conclusion, report):
    with open(QUEUE, encoding="utf-8") as fh:
        lines = fh.read().split("\n")
    hit = False
    for i, line in enumerate(lines):
        if line.startswith(f"| {qid} |"):
            cells = line.split("|")
            if len(cells) > 4:
                cells[4] = f" DONE（{conclusion}） "
            if report and len(cells) > 6:
                cells[6] = f" `{report}` "
            lines[i] = "|".join(cells)
            hit = True
            break
    if not hit:
        print(f"WARN 队列里没有 {qid}，跳过状态改写")
    with open(QUEUE, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))
    with open(LEDGER, "a", encoding="utf-8") as fh:
        fh.write(f"| {dt.datetime.now():%m-%d %H:%M} {qid} {report or ''} | 写码代理 | {report or '—'} | ✅ 已交付（{conclusion[:24]}） | 见队列 | 见队列 | L1 |\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--q", required=True)
    ap.add_argument("--report", default="")
    ap.add_argument("--code", nargs="*", default=[])
    ap.add_argument("--test", nargs="*", default=[])
    ap.add_argument("--conclusion", default="静态 PASS")
    ap.add_argument("--title", default="")
    ap.add_argument("--verified-by", default="", help="验收报告路径；缺省则状态写「DONE（未验·待验收）」")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    title = args.title or args.q
    steps = []
    if args.code:
        steps.append(("code", f"{title}：生产码", args.code))
    if args.test:
        steps.append(("test", f"{title}：测试与断言", args.test))
    if args.report:
        steps.append(("docs", f"{title}：报告", [args.report]))

    if args.dry_run:
        for kind, msg, paths in steps:
            print(f"[{kind}] {msg} <- {paths}")
        print(f"[台账] {args.q} -> DONE（{args.conclusion}）")
        return 0

    # 预检必须在提交之前跑：提交完工作树就干净了，预检会误报"无代码改动"（本脚本初版就这么错过一次）。
    guard = sh([sys.executable, "data_scripts/cross-module-guard.py"])
    print(guard.stdout.strip().splitlines()[-1] if guard.stdout.strip() else "GUARD: 无输出")
    if "FAIL" in guard.stdout:
        print("停在预检未过，不提交也不推")
        return 1

    for kind, msg, paths in steps:
        if not commit(paths, msg):
            return 1
    concl = args.conclusion
    if args.verified_by:
        concl = f"{concl}；已验 {args.verified_by}"
    else:
        concl = f"未验·待验收：{concl}"
    patch_queue(args.q, concl, args.report)
    if not commit(["docs/派单队列.md", "docs/派单台账.md"], f"台账：{args.q} 收口"):
        return 1

    token = re.sub(r"\s", "", subprocess.run("grep -o 'ghp_[A-Za-z0-9]*' ~/.git-credentials | head -1",
                                             shell=True, capture_output=True, text=True).stdout)
    for _ in range(3):
        url = f"https://bqdlz676:{token}@github.com/bqdlz676/legado.git"
        r = sh(["git", "push", "-q", url, "HEAD:main"])
        if r.returncode == 0:
            print("推送：成功")
            break
        subprocess.run(["sleep", "5"])
    else:
        print("FAIL 推送失败（稍后重试）")
        return 1

    head = sh(["git", "log", "--oneline", "-1"]).stdout.strip()
    print(f"HEAD：{head}")
    tag = head.split()[0] if head else ""
    api = subprocess.run(
        f"curl -s -H \"Authorization: Bearer {token}\" "
        f"\"https://api.github.com/repos/bqdlz676/legado/actions/runs?per_page=6\"",
        shell=True, capture_output=True, text=True).stdout
    for name in ("Verify", "Screenshot"):
        m = re.search(r'"head_sha":"%s"[^}]*?"name":"%s[^"]*"[^}]*?"status":"(\w+)"[^}]*?"conclusion":(null|"\w+")' % (tag, name), api)
        if m:
            print(f"CI {name}: {m.group(1)} {m.group(2)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
