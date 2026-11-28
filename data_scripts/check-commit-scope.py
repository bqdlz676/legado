#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 g75 · 提交域纯度门（G-13 共享索引污染缺口）。

为什么立这条（见 docs/台账-三件缺项.md G-13、docs/截图-星空八态帧证据.md「污染事件」段）：
  2026-11-28 提交 `d42514c` 自称纯文档（messages 前缀 `docs(screenshot):`），实际带入
  12 个文件、其中 11 个属于兄弟代理在飞写码单（host/astro/screenshot 生产与测试件）。
  根因不是某个代理写错，而是所有代理共用同一个 `.git/index`：A 先用 `git add` 暂存自有
  文件，B 随后 `git commit` 会把索引里的全部已暂存内容一并提交。这类污染的代价是
  归属难查、回滚误伤、以及"文档提交却动了生产代码"的审计盲区。

判据（机械，只判提交信息与实际改动文件集合的一致性）：
  D1 SUBJECT-DOCS-ONLY：subject 以 `docs(` 开头的提交，改动路径必须全在 `docs/`
     或 `*.md`；出现任何生产/测试路径 → FAIL。
  D2 TRAILER-DOCS-ONLY：提交正文声明 `Docs-only` 尾注的提交，同 D1 判据。
  D3 TRAILER-TEST-ONLY：正文声明 `Test-only` 尾注的提交，改动路径必须全在测试源集、
     `screenshot/` 或 `docs/`（不得改 `app/src/main/`）。
  两条都只在"提交自己声明了范围"时生效——不猜代理意图，只查声明与事实是否一致。

与既有门禁的分工（不重复判定）：
  - g68 判新公开入口有没有调用方；g74 判锚点写法；g72 判计数漂移。
  - 本闸 g75 只判"一批改动有没有越出自己在提交信息里声明的域"。

适用边界 / 误判面 / 不能替代什么：
  - 基线之前的提交不追改（`d42514c` 这类历史污染由 G-13 补齐记录留痕，不在门禁里刷红），
    基线写在 `data_scripts/commit-scope-baseline.json`。
  - 判不出"两条单的产物被同一个提交带走"这件事本身（合并提交在信息层面与合法混合提交
    无法区分）——那要靠提交前 `git diff --cached --name-only` 人判，本闸只兜它漏掉之后
    的声明不一致。
  - 不判提交信息写得好不好、不判文件域归属权限。

退出码：0=PASS | 1=FAIL | 2=参数错 | 3=UNVERIFIED（git 不可用 / 基线之后无提交）
用法：
  python3 data_scripts/check-commit-scope.py
  python3 data_scripts/check-commit-scope.py --baseline <sha> --repo <dir>
  python3 data_scripts/check-commit-scope.py --selftest
"""
from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys

BASELINE_FILE = pathlib.Path(__file__).with_name("commit-scope-baseline.json")
DOC_EXT = (".md", ".txt", ".csv")


def is_doc_path(p: str) -> bool:
    """文档与工具域：docs/ 下任何文件、任何位置的 Markdown/纯文本/CSV、以及 data_scripts/
    下的门禁脚本（制度门单习惯把「规则文档 + 判据脚本」放在同一提交，属同一域）。"""
    return p.startswith("docs/") or p.startswith("data_scripts/") or p.endswith(DOC_EXT)


def is_test_path(p: str) -> bool:
    """测试源集：src/test、screenshot 模块、docs。"""
    return ("src/test/" in p) or p.startswith("screenshot/") or p.startswith("docs/")


def declares_docs_only(subject: str, body: str) -> bool:
    return subject.startswith("docs(") or "Docs-only" in body


def declares_test_only(subject: str, body: str) -> bool:
    return subject.startswith("test(") or "Test-only" in body


def audit(commits):
    """commits: [(sha, subject, body, [paths])] -> (violations, checked)"""
    violations = []
    for sha, subject, body, paths in commits:
        if declares_docs_only(subject, body):
            bad = [p for p in paths if not is_doc_path(p)]
            if bad:
                violations.append((sha, subject, "D1/D2 声明纯文档却动了非文档路径", bad))
        elif declares_test_only(subject, body):
            bad = [p for p in paths if not is_test_path(p)]
            if bad:
                violations.append((sha, subject, "D3 声明仅测试却动了生产路径", bad))
    return violations, len(commits)


def git(args, repo):
    # core.quotepath=false：否则非 ASCII 路径被转义成 "docs/\344\277..."，域判断全失效
    return subprocess.run(
        ["git", "-C", str(repo), "-c", "core.quotepath=false"] + args,
        capture_output=True, text=True)


def collect(repo: str, baseline: str | None):
    """返回 (commits, range_desc)；git 不可用抛 RuntimeError。"""
    head = git(["rev-parse", "HEAD"], repo)
    if head.returncode != 0:
        raise RuntimeError("git rev-parse HEAD 失败：" + head.stderr.strip()[:120])
    if baseline:
        rng = f"{baseline}..HEAD"
    else:
        rng = "HEAD~20..HEAD"
    log = git(["log", "--format=%H%x1f%s%x1f%b%x1e", rng], repo)
    if log.returncode != 0:
        raise RuntimeError("git log 失败：" + log.stderr.strip()[:120])
    commits = []
    for rec in log.stdout.split("\x1e"):
        rec = rec.strip("\n")
        if not rec:
            continue
        parts = rec.split("\x1f")
        if len(parts) < 3:
            continue
        sha, subject, body = parts[0].strip(), parts[1].strip(), parts[2]
        names = git(["show", "--name-only", "--format=", sha], repo)
        paths = [p for p in names.stdout.splitlines() if p.strip()]
        commits.append((sha, subject, body, paths))
    shown_base = baseline or "HEAD~20"
    return commits, f"{shown_base}..HEAD"


FIXTURES = [
    (["abc1234", "docs(index): 索引升版", "", ["docs/落文-模块契约索引.md"]], []),
    (["def5678", "docs(screenshot): 回填 run id", "", ["docs/x.md", "app/src/main/java/Foo.kt"]],
     ["def5678"]),
    (["cafe123", "feat(astro): 触发链接线", "Docs-only\n", ["app/src/main/java/Bar.kt"]],
     ["cafe123"]),
    (["bee4567", "test(astro): 补用例", "", ["app/src/test/java/BazTest.kt", "docs/y.md"]], []),
    (["f00ba11", "test(astro): 补用例", "Test-only\n", ["app/src/test/java/Q.kt", "app/src/main/java/Q.kt"]],
     ["f00ba11"]),
    (["dead000", "keine docs 声明", "", ["app/src/main/java/R.kt"]], []),
]


def selftest() -> int:
    ok = True
    for commit, expect in FIXTURES:
        violations, _ = audit([tuple(commit)])
        got = [v[0] for v in violations]
        status = "PASS" if got == expect else "FAIL"
        if status == "FAIL":
            ok = False
        print(f"  SELFTEST {status} {commit[0]} {commit[1][:28]!r} expect={expect} got={got}")
    print("SELFTEST: " + ("PASS（声明纯文档却动生产判红 / 声明仅测试却动生产判红 / 合法放行）" if ok else "FAIL"))
    return 0 if ok else 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=str(pathlib.Path(__file__).resolve().parents[1]))
    ap.add_argument("--baseline", default=None)
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    baseline = args.baseline
    if baseline is None:
        try:
            baseline = json.loads(BASELINE_FILE.read_text(encoding="utf-8"))["baseline"]
        except Exception:
            baseline = None
    try:
        commits, rng = collect(args.repo, baseline)
    except RuntimeError as e:
        print(f"COMMIT-SCOPE: UNVERIFIED（{e}）")
        return 3
    if not commits:
        # 空集不判 PASS（㊾ 口径）：没有可比对的提交就不是"合规"，只算没验
        print(f"COMMIT-SCOPE: UNVERIFIED（{rng} 无可比对提交）")
        return 3
    violations, n = audit(commits)
    if not violations:
        print(f"COMMIT-SCOPE: PASS（{rng} 共 {n} 条提交，声明域与改动集合一致）")
        return 0
    for sha, subject, why, bad in violations:
        print(f"  ✗ {sha[:8]} {subject[:40]} — {why}")
        for p in bad[:6]:
            print(f"      {p}")
        if len(bad) > 6:
            print(f"      … 另有 {len(bad) - 6} 个越界路径")
    print(f"COMMIT-SCOPE: FAIL（{len(violations)}/{n} 条提交越出自身声明的文件域）")
    return 1


if __name__ == "__main__":
    sys.exit(main())
