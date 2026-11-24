#!/usr/bin/env python3
"""门禁 ⑲ 锚点卡守卫（F-85 前置设计费，2026-11-24 凭记忆转述规格返工复盘产物）。

事故：12 类卡片中 BookCard / TopicCard 两张返工，根因＝执行代理凭记忆转述规格
（分享卡三背景 lerp 0.35/0.12/0.10 记岔）；不是能力问题，是派单没给可逐字
引用的原文锚点。前置设计费＝写码单前先落 ≤30 行锚点卡（逐字引文 + 参数表 +
可测判据），用小钱买断返工。

判据（只校验本次 git status 新增/修改的报告，历史留档不追改，同 ⑭/⑮ 口径）：
  写码类回执（`docs/修复-*.md`、`docs/实现-*.md`）：
    ① 正文含 `锚点卡：docs/锚点/<缺陷号>-<主题>.md`；② 该锚点卡文件真实存在。
  验收类报告（`docs/验收-*.md`）：
    ① 正文含锚点卡路径；② 正文有逐字引文段（`「…」` 引文 或 `文件:行` 定位串），
       禁止"按记忆里的规格验收"。
  豁免：报告显式写明"机械改动"（改一行 import / 补一个 strokeLineJoin 这类），
  或写明"无规格→已按 F-73 退回设计"，跳过锚点卡要求。

退出码：全部 PASS=0；任一 FAIL=1。--strict 只打印 FAIL 明细。
"""
import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ANCHOR_RE = re.compile(r"锚点卡[：:]\s*(docs/锚点/[^\s）)」]+\.md)")
LOC_RE = re.compile(r"[\w\-/\\.]+\.[A-Za-z]+:[0-9]+")
QUOTE_RE = re.compile(r"「[^」]{4,}」")
EXEMPT_RE = re.compile(r"机械改动|无规格→已按 F-73 退回设计|F-73 退回设计")


def classify(path: str) -> str:
    name = os.path.basename(path)
    if name.startswith(("修复-", "实现-")):
        return "write"
    if name.startswith("验收-选型-"):
        return "f73"  # F-73 三段流水线第二段：候选规格本身即锚点，不重复付前置设计费
    if name.startswith("验收-"):
        return "review"
    return "other"


def check_file(path: str):
    if not os.path.isfile(path):
        return {"path": path, "ok": False, "missing": ["文件不存在"], "counts": (0, 0, 0)}
    text = open(path, encoding="utf-8", errors="replace").read()
    kind = classify(path)
    if kind in ("other", "f73") or EXEMPT_RE.search(text):
        return {"path": path, "ok": True, "missing": [], "counts": (0, 0, 0), "exempt": True}

    missing: list[str] = []
    m = ANCHOR_RE.search(text)
    anchor = m.group(1) if m else None
    if not anchor:
        missing.append("缺锚点卡路径")
    else:
        anchor_abs = os.path.join(ROOT, anchor)
        if not os.path.isfile(anchor_abs):
            missing.append(f"锚点卡不存在 {anchor}")

    if kind == "review":
        if not (LOC_RE.search(text) or QUOTE_RE.search(text)):
            missing.append("验收报告缺逐字引文段（「…」或 文件:行）")

    return {"path": path, "ok": not missing, "missing": missing,
            "counts": (1 if anchor else 0, 1 if LOC_RE.search(text) else 0,
                       1 if QUOTE_RE.search(text) else 0)}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("reports", nargs="*")
    args = parser.parse_args(argv)

    files = args.reports
    if not files:
        print("（无待检报告）")
        return 0

    passed = failed = 0
    for path in files:
        res = check_file(path)
        if res["ok"]:
            passed += 1
            if not args.strict:
                tag = "豁免" if res.get("exempt") else "PASS"
                print(f"── {path}  → {tag}")
        else:
            failed += 1
            print(f"── {path}  → FAIL（{'、'.join(res['missing'])}）")

    print(f"汇总: PASS={passed} FAIL={failed}（共 {passed + failed} 份）")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
