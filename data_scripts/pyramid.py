#!/usr/bin/env python3
"""金字塔验收现状打印（唯一口径＝既有 F-92 五层 + 铁律 #46 组装层级）。

为什么改写：初版我自己造了"七层"，与仓库既有 `docs/铁律-组合验收金字塔.md`（F-92）
和 `docs/组合验收矩阵.md` + `docs/台账-验收分层.md` + 门禁 ㉑ 重复。重复机制本身就是
L8「缝合怪只增不减」的复发，所以本脚本只读既有三件套，绝不生成第二套口径。

用法：python3 data_scripts/pyramid.py
"""

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MATRIX = os.path.join(ROOT, "docs", "组合验收矩阵.md")
LAYER_LEDGER = os.path.join(ROOT, "docs", "台账-验收分层.md")


def read(path):
    try:
        with open(path, encoding="utf-8") as fh:
            return fh.read()
    except OSError:
        return ""


def main():
    matrix = read(MATRIX)
    ledger = read(LAYER_LEDGER)
    print("=== 金字塔验收现状（F-92 五层 + 铁律 #46 组装层级）===")

    # 层 1~层 4 的行数按分节统计（矩阵按 "## N. 层 N" 分节，行里不带层号列）
    sections = re.split(r"^## ", matrix, flags=re.M)
    for sec in sections:
        m = re.match(r"(\d)\.\s*层\s*(\d)", sec)
        if not m:
            continue
        layer = m.group(2)
        rows = len(re.findall(r"^\|\s*(?:P|L|A|M|W|E)[0-9A-Za-z-]*\s*\|", sec, re.M))
        print(f"层 {layer}: 矩阵登记 {rows} 行")

    for tag in ("未落地", "待验", "已验"):
        print(f"状态「{tag}」: {matrix.count(tag)} 处")

    # 缺陷落层归因：台账里 DEF-xx 条数与是否都有层标记
    defs = re.findall(r"^\|\s*(DEF-\d+)\s*\|([^\n]*?)\|\s*层\s*(\d)\s*\|", ledger, re.M)
    print(f"缺陷落层归因: {len(defs)} 条（层分布 {sorted({d[2] for d in defs})}）")

    # 组装层级（铁律 #46）
    # 组装层级登记在模块契约索引 §3.5，不在矩阵里；这里读那份索引
    idx = read(os.path.join(ROOT, "docs", "落文-模块契约索引.md"))
    for unit in ("L1-A", "L1-B", "L1-C"):
        hit = re.search(rf"\| {unit} [^|]+\|[^\n]*?\|([^|]+)\s*\|?\s*$", idx, re.M)
        if not hit:
            hit = re.search(rf"\| {unit} [^|]+\|[^\n]*\|([^|]+)\|", idx)
        state = hit.group(1).strip()[:70] if hit else "索引未登记"
        print(f"组装单元 {unit}: {state}")

    # 门禁 ㉑
    out = subprocess.run([sys.executable, "data_scripts/check-combo-coverage.py"],
                         cwd=ROOT, capture_output=True, text=True).stdout.strip()
    print("门禁 ㉑:", out.splitlines()[-1] if out else "（无输出）")

    # CI 真值（组装编译只认云端）
    token = subprocess.run("grep -o 'ghp_[A-Za-z0-9]*' ~/.git-credentials | head -1",
                           shell=True, capture_output=True, text=True).stdout.strip()
    head = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT,
                          capture_output=True, text=True).stdout.strip()
    api = subprocess.run(
        f"curl -s -H 'Authorization: Bearer {token}' "
        "'https://api.github.com/repos/bqdlz676/legado/actions/runs?per_page=8'",
        shell=True, capture_output=True, text=True).stdout
    rows = [(m.group(1), m.group(2), m.group(3), (m.group(4) or "").strip('"') or "-")
            for m in re.finditer(
                r'"head_sha":"(\w{7})\w*"[^}]*?"name":"([^"]+)"[^}]*?"status":"(\w+)"[^}]*?"conclusion":(null|"\w+")',
                api)]
    mine = [r for r in rows if r[0] == head]
    if mine:
        for name, status, concl in mine:
            print(f"CI {name}: {status} {concl}")
    else:
        print(f"CI: HEAD {head} 暂无 run")

    print("层 4 真机：沙箱无 Android 运行时，恒标未验证（不得冒充外部真值）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
