#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""归因机械验证 dry-run（F-113 / F-114 / F-115）。

职责（只做机械判定，链条上无人工环节）：
  1. 读 docs/归因/批次-B-001.md、批次-B-002.md，解析卡号与 commit 列表；
  2. 对每个 commit 执行只读命令 `git show --name-only --pretty=%s <sha>`，
     仅按改动文件路径前缀机械判定：
       - 同时含 docs/ 与 app/src/  -> FAIL|结单与代码混合
       - 只含 app/src/ 一侧        -> PASS|仅动 app/src/（N 文件）
       - 只含 docs/ 一侧           -> PASS|仅动 docs/（N 文件）
       - 两侧均无                  -> FAIL|既非 docs/ 亦非 app/src/
  3. 按 `批次|卡号|结论|依据` 输出到 stdout；--check 时与对应 期望-*.txt
     逐行 diff，全一致退出码 0，有差异退出码 1。

纯标准库，无第三方依赖；只调用只读 git 命令（show），不做任何写操作。
"""

import argparse
import difflib
import pathlib
import re
import subprocess
import sys

# 仓库根目录：本脚本位于 data_scripts/ 下，上一级即仓库根
ROOT = pathlib.Path(__file__).resolve().parent.parent
BATCH_DIR = ROOT / "docs" / "归因"

CARD_RE = re.compile(r"卡号[:：]\s*([A-Z]+-\d+)")
SHA_RE = re.compile(r"\b[0-9a-f]{7,40}\b")

BATCH_IDS = ("B-001", "B-002")


def git_show_files(sha: str) -> list:
    """只读：取该 commit 改动的文件列表（去掉首行 subject 与空行）。"""
    proc = subprocess.run(
        ["git", "-c", "core.quotepath=false", "show",
         "--name-only", "--pretty=%s", sha],
        cwd=ROOT, capture_output=True, text=True, check=True,
    )
    lines = proc.stdout.splitlines()
    # 第一行是提交信息 subject，其余非空行是文件名
    files = [ln.strip() for ln in lines[1:] if ln.strip()]
    # 去重并保持顺序
    return list(dict.fromkeys(files))


def classify(files: list):
    """按文件路径前缀机械分类，返回 (结论, 依据)。不读备注、不做启发式猜测。"""
    has_docs = any(f.startswith("docs/") for f in files)
    has_src = any(f.startswith("app/src/") for f in files)
    if has_docs and has_src:
        return "FAIL", "结单与代码混合"
    if has_src:
        return "PASS", "仅动 app/src/（%d 文件）" % len(files)
    if has_docs:
        return "PASS", "仅动 docs/（%d 文件）" % len(files)
    return "FAIL", "既非 docs/ 亦非 app/src/"


def parse_batch(batch_id: str):
    """解析批次 md，返回 [(卡号, [commit sha, ...]), ...]。

    卡号行内写出的 commits 优先；行内无 sha 时回退到批次级 sha 列表。
    """
    batch_path = BATCH_DIR / ("批次-%s.md" % batch_id)
    text = batch_path.read_text(encoding="utf-8")
    batch_shas = SHA_RE.findall(text)
    cards = []
    for line in text.splitlines():
        m = CARD_RE.search(line)
        if not m:
            continue
        card = m.group(1)
        shas = SHA_RE.findall(line)
        if not shas:
            shas = batch_shas
        cards.append((card, shas))
    return cards


def attribute(batch_id: str) -> list:
    """对批次内每张卡汇总其全部 commit 的改动文件，机械判定并输出行。"""
    rows = []
    for card, shas in parse_batch(batch_id):
        merged_files = []
        for sha in shas:
            merged_files.extend(git_show_files(sha))
        merged_files = list(dict.fromkeys(merged_files))
        conclusion, reason = classify(merged_files)
        rows.append("%s|%s|%s|%s" % (batch_id, card, conclusion, reason))
    return rows


def read_expected(batch_id: str) -> list:
    expected_path = BATCH_DIR / ("期望-%s.txt" % batch_id)
    return [ln.strip() for ln in expected_path.read_text(encoding="utf-8")
            .splitlines() if ln.strip()]


def run_check(batch_id: str) -> int:
    actual = attribute(batch_id)
    expected = read_expected(batch_id)
    if actual == expected:
        print("[CHECK] %s: 一致（%d 行，全过）" % (batch_id, len(actual)))
        return 0
    print("[CHECK] %s: 有差异（期望 %d 行 / 实际 %d 行）"
          % (batch_id, len(expected), len(actual)))
    for line in difflib.unified_diff(
        expected, actual,
        fromfile="期望-%s.txt" % batch_id,
        tofile="dryrun-%s" % batch_id, lineterm="",
    ):
        print(line)
    return 1


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="归因机械验证 dry-run 双批 diff")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--batch", choices=BATCH_IDS, help="单跑指定批次")
    group.add_argument("--all", action="store_true", help="跑 B-001、B-002 两批")
    parser.add_argument("--check", action="store_true",
                        help="与对应期望文件逐行 diff；全一致退出 0，有差异退出 1")
    args = parser.parse_args(argv)

    batch_ids = [args.batch] if args.batch else list(BATCH_IDS)

    if args.check:
        rc = 0
        for bid in batch_ids:
            rc |= run_check(bid)
        return rc

    for bid in batch_ids:
        for row in attribute(bid):
            print(row)
    return 0


if __name__ == "__main__":
    sys.exit(main())
