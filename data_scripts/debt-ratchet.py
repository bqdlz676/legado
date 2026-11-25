#!/usr/bin/env python3
"""债务棘轮（2026-11-26 事故复盘产物）。

两次事故的共同形态：局部补丁最省事，于是债务只增不减——
"缝合怪 160 页贴皮残留""回放栈第三份注册表""13 处旧搜索入口"都是这么长出来的。
门禁 ⑳ 只能拦"未在册的新缝合"，拦不住"在册债务继续变大"。

棘轮做法：把当前债务数存成基线，之后只许减、不许增；
要上调基线必须显式 `--upgrade-baseline`（会在输出里留痕，便于复盘时看见是谁松了口）。

指标来源直接用 ⑳ 的实跑结果，不另造口径，避免两套数各说各话。
用法：
  python3 data_scripts/debt-ratchet.py                 # 对比基线
  python3 data_scripts/debt-ratchet.py --upgrade-baseline  # 显式上调（慎用）
"""

import argparse
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASELINE = os.path.join(ROOT, "data_scripts", "debt-baseline.json")
GATE = os.path.join(ROOT, "data_scripts", "check-frankenstein.py")

# 只取"债务存量"几个数，不取"未在册命中"——后者本就是硬 FAIL，由门禁自己管。
PATTERNS = {
    "dual": r"\[1\] 双轨并存: (\d+) 命中",
    "compat": r"\[2\] 兼容层残留: (\d+) 命中",
    "oversized_warn": r"\[3\].*≥\d+ 行 (\d+)，",
    "deprecated": r"\[4\] @Deprecated 仍被调用: (\d+) 命中",
    "wip": r"\[5\] 半成品 wip 分支: (\d+) 分支",
}


def current():
    out = subprocess.run([sys.executable, GATE], cwd=ROOT, capture_output=True, text=True).stdout
    metrics = {}
    for key, pat in PATTERNS.items():
        m = re.search(pat, out)
        if m:
            metrics[key] = int(m.group(1))
    return metrics, out.strip().splitlines()[-1] if out.strip() else ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--upgrade-baseline", action="store_true")
    args = ap.parse_args()

    metrics, gate_tail = current()
    if not metrics:
        print("RATCHET: FAIL（读不到 ⑳ 的计数，先修门禁脚本）")
        return 1

    base = {}
    if os.path.isfile(BASELINE):
        with open(BASELINE, encoding="utf-8") as fh:
            base = json.load(fh).get("metrics", {})

    if args.upgrade_baseline or not base:
        with open(BASELINE, "w", encoding="utf-8") as fh:
            json.dump({"metrics": metrics, "note": "基线由 debt-ratchet.py 生成；上调须显式 --upgrade-baseline"}, fh,
                      ensure_ascii=False, indent=2)
        print(f"RATCHET: 基线已写入 {BASELINE}：{metrics}")
        return 0

    grew, shrank = [], []
    for k, v in metrics.items():
        b = base.get(k)
        if b is None:
            grew.append(f"{k} 基线缺失（现 {v}）")
        elif v > b:
            grew.append(f"{k} 由 {b} 增到 {v}")
        elif v < b:
            shrank.append(f"{k} 由 {b} 降到 {v}")

    for s in shrank:
        print(f"下降 {s}")
    if grew:
        for g in grew:
            print(f"FAIL 债务增长：{g}")
        print("RATCHET: FAIL（债务只许减不许增；确需上调跑 --upgrade-baseline 并写进事故复盘）")
        return 1
    print(f"RATCHET: PASS（当前 {metrics}，未超基线）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
