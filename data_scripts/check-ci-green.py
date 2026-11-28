#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CI 三同时门（G-02 补齐）：对给定 commit/run 机械断言三件事同时成立。

为什么立这条：`docs/台账-CI转绿登记.md` 的记录此前靠人手动 curl 看一眼 conclusion 就写
"转绿"，conclusion=success 只说明 workflow 整体没崩，说明不了"编译零错 + 测试零失败"。
一次"测试任务被跳过/被取消但 conclusion 仍 success"的假绿，足以让 L2 验收拿错证据。
故本门禁把「BUILD SUCCESSFUL / e: 零条 / tests failed 零条」三件事做成机判。

适用边界（不能替代什么）：
- 只读 GitHub Actions 的结论与日志文本，不重跑、不本地编译（沙箱禁编译）。
- 网络不可达时判 UNVERIFIED（退出码 3），**不是** PASS：未验证不得写进转绿登记。
- 只覆盖"结论 + 日志关键字"，判不到"测试被 skip 了但没打印失败"这种口径问题；
  那种仍需人看 job 步骤表。
- cancelled 一律不算绿（连推互相顶替导致的取消不能当通过）。

退出码：0=PASS（三同时成立）| 1=FAIL（有红项）| 2=参数错 | 3=UNVERIFIED（网络/凭据不可用）

用法：
  python3 data_scripts/check-ci-green.py --commit 7847170b
  python3 data_scripts/check-ci-green.py --run 46098591092
  python3 data_scripts/check-ci-green.py --selftest
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "bqdlz676/legado"
API = "https://api.github.com/repos/%s" % REPO
# 三条必须全绿的车道（名字前缀匹配，容忍 workflow 名微调）
REQUIRED_LANES = ("Verify", "Screenshot", "Package")


def git_credentials_token() -> str | None:
    """从 ~/.git-credentials 取 github.com 那一行的 token。

    为什么只取 github.com 行：本机还有代理网关凭据，全局 sed 会把网关密码当 token，
    结果永远是 Bad credentials。
    """
    path = os.path.expanduser("~/.git-credentials")
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            if "@github.com" not in line:
                continue
            m = re.match(r"https://[^:]+:([^@]+)@github\.com", line.strip())
            if m:
                return m.group(1)
    return None


def token() -> str | None:
    env = os.environ.get("GITHUB_TOKEN")
    if env:
        return env
    return git_credentials_token()


class Unverified(Exception):
    """网络/凭据不可用：必须与 FAIL 区分，避免"没查到"被当成"通过"。"""


def api_get(path: str) -> tuple[int, str]:
    tok = token()
    if not tok:
        raise Unverified("无 GitHub token（~/.git-credentials 无 github.com 行，且未设 GITHUB_TOKEN）")
    req = urllib.request.Request(API + path)
    req.add_header("Authorization", "token " + tok)
    req.add_header("Accept", "application/vnd.github+json")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        # 4xx/5xx 是"查到了但服务端拒绝"，属 FAIL 面而不是未验证
        return exc.code, exc.read().decode("utf-8", "replace")
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise Unverified("网络不可达：%s" % exc)


def find_runs(commit: str | None = None, run_id: str | None = None) -> list[dict]:
    if run_id:
        code, body = api_get("/actions/runs/%s" % run_id)
        if code != 200:
            raise SystemExit("FAIL：run %s 查询失败 HTTP %s" % (run_id, code))
        return [json.loads(body)]
    code, body = api_get("/actions/runs?per_page=30")
    if code != 200:
        raise SystemExit("FAIL：runs 列表查询失败 HTTP %s" % code)
    runs = json.loads(body)["workflow_runs"]
    if commit:
        runs = [r for r in runs if r["head_sha"].startswith(commit)]
    if not runs:
        raise SystemExit("FAIL：找不到 commit %s 的 workflow run（可能还没触发或被取消）" % commit)
    return runs


def jobs_of(run_id: int) -> list[dict]:
    code, body = api_get("/actions/runs/%s/jobs" % run_id)
    if code != 200:
        raise Unverified("jobs 查询失败 HTTP %s（run %s）" % (code, run_id))
    return json.loads(body)["jobs"]


def job_log(job_id: int) -> str:
    """取 job 日志纯文本。

    为什么用 curl -L 而不是 urllib：GitHub 的 logs 端点先 302 到签名地址，签名地址不带
    Authorization 才可用；urllib 会把 Authorization 头跟着跳到签名 URL，导致 403/空body。
    curl -L 跟随后再带一次头即可稳定拿到纯文本（不是 zip）。
    """
    tok = token()
    if not tok:
        raise Unverified("无 GitHub token，无法取 job 日志")
    url = "%s/actions/jobs/%s/logs" % (API, job_id)
    try:
        out = subprocess.run(
            ["curl", "-sL", "--max-time", "60", "-H", "Authorization: token " + tok, url],
            capture_output=True, text=True, timeout=90,
        )
    except (subprocess.TimeoutExpired, OSError) as exc:
        raise Unverified("拉日志失败：%s" % exc)
    body = out.stdout or ""
    if "Not Found" in body[:200] or '"message"' in body[:200]:
        return ""
    return body


def scan_log(log: str) -> tuple[int, int, bool]:
    """返回 (编译错误条数, 测试失败条数, 是否见到 BUILD SUCCESSFUL/日志为空)。"""
    errors = len(re.findall(r"(?m)^\S*\s*e: ", log))
    # 同时兼容 "tests failed"、"FAILED" 任务行、以及 Gradle 的 "> Task ... FAILED"
    failed = len(re.findall(r"(?mi)\btests? failed\b|^.*> Task .*FAILED", log))
    return errors, failed, bool(re.search(r"BUILD SUCCESSFUL", log))


def judge(runs: list[dict]) -> tuple[int, list[str], dict]:
    """对一组 run 判三同时：lane 结论全绿 + 编译零错 + 测试零失败。"""
    notes: list[str] = []
    stats = {"lanes": {}, "compile_errors": 0, "test_failed": 0, "build_successful": False}
    seen_lane: set[str] = set()
    ok = True
    for run in runs:
        name = run["name"]
        lane = next((l for l in REQUIRED_LANES if l.lower() in name.lower()), None)
        if lane is None:
            continue
        # 同一车道多次触发时取最新一条（列表按时间倒序，第一次见到即最新）
        if lane in seen_lane:
            continue
        seen_lane.add(lane)
        concl = run.get("conclusion")
        stats["lanes"][lane] = concl
        if concl != "success":
            ok = False
            notes.append("FAIL %s 车道结论=%s（run %s）" % (lane, concl, run["id"]))
            continue
        for job in jobs_of(run["id"]):
            if job.get("conclusion") not in ("success", "skipped"):
                ok = False
                notes.append("FAIL %s 车道 job 未绿：%s / %s"
                             % (lane, job.get("name"), job.get("conclusion")))
            log = job_log(job["id"])
            if not log:
                notes.append("WARN %s / %s 日志为空，无法机判编译与测试计数" % (lane, job.get("name")))
                continue
            errors, failed, build_ok = scan_log(log)
            stats["compile_errors"] += errors
            stats["test_failed"] += failed
            stats["build_successful"] = stats["build_successful"] or build_ok
            if errors:
                ok = False
                notes.append("FAIL %s / %s 编译错误 %d 条" % (lane, job.get("name"), errors))
            if failed:
                ok = False
                notes.append("FAIL %s / %s 测试失败行 %d 条" % (lane, job.get("name"), failed))
    missing = [l for l in REQUIRED_LANES if l not in seen_lane]
    if missing:
        ok = False
        notes.append("FAIL 缺车道：%s（可能是连推被取消，取消不算绿）" % "、".join(missing))
    return (0 if ok else 1), notes, stats


def selftest() -> int:
    """双向自测：伪造日志文本，确认违规样本判红、合规样本放行。"""
    bad = "2026-11-28T02:27:09.3642925Z e: file:///x/Sky.kt:137:1 Syntax error: Unclosed comment.\n"
    bad2 = "> Task :screenshot:compileDebugUnitTestKotlin FAILED\n"
    good = "> Task :app:compileDebugKotlin\nBUILD SUCCESSFUL in 3m 12s\n"
    errs, fails, build_ok = scan_log(bad)
    assert errs == 1 and fails == 0 and not build_ok, "坏样本(编译错)未判红"
    errs2, fails2, _ = scan_log(bad2)
    assert fails2 >= 1, "坏样本(任务 FAILED)未判红"
    errs3, fails3, build3 = scan_log(good)
    assert errs3 == 0 and fails3 == 0 and build3, "好样本被误判"
    print("SELFTEST: PASS（编译错判红、任务 FAILED 判红、干净 BUILD SUCCESSFUL 放行）")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="CI 三同时门（G-02）")
    ap.add_argument("--commit", help="按 commit sha 前缀判")
    ap.add_argument("--run", help="按单次 workflow run id 判")
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("--json", action="store_true", help="输出机读 JSON")
    args = ap.parse_args()

    if args.selftest:
        return selftest()
    if not args.commit and not args.run:
        print("CI-TRIPLE: 参数错（需要 --commit 或 --run）", file=sys.stderr)
        return 2
    try:
        runs = find_runs(args.commit, args.run)
        code, notes, stats = judge(runs)
    except Unverified as exc:
        print("CI-TRIPLE: UNVERIFIED（%s）" % exc)
        return 3
    if args.json:
        print(json.dumps({"code": code, "notes": notes, "stats": stats},
                         ensure_ascii=False, indent=2))
    else:
        head = "CI-TRIPLE: PASS" if code == 0 else "CI-TRIPLE: FAIL"
        lane_txt = " ".join("%s=%s" % (k, v) for k, v in stats["lanes"].items())
        print("%s（车道 %s｜编译错 %d｜测试失败行 %d｜BUILD SUCCESSFUL %s）"
              % (head, lane_txt, stats["compile_errors"], stats["test_failed"],
                 "在位" if stats["build_successful"] else "未见"))
        for n in notes:
            print("  · " + n)
    return code


if __name__ == "__main__":
    sys.exit(main())
