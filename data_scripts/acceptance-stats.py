#!/usr/bin/env python3
"""验收统计（验收机制优化的度量口，2026-11-26）。

为什么：验收结论一直只写在报告正文里，谁判低、哪个域判低率高、返工链多长，
全都只能靠翻报告。于是"验收机制好不好"无法验证。

做法：解析全部 `docs/验收-*.md`，抽四样东西——总结论（PASS / 有条件通过 / FAIL）、
被验 commit 或 run、余项条数、报告所在域（按标题关键词归类）。输出比例与域分布。

只读。用法：python3 data_scripts/acceptance-stats.py
"""

import collections
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")

DOMAINS = [
    ("搜索/书架", ("浮层", "搜索", "书架", "导航三")),
    ("战役/回放", ("战役", "回放", "侧翼", "暴露", "敌情", "存档", "格距", "事件动作", "情报")),
    ("名物/气泡", ("链序", "仲裁", "名物", "气泡")),
    ("脚本/文档", ("原文", "校验", "重排", "manifest", "可移植")),
    ("资源/清剿", ("清剿", "残留", "缝合")),
]


def domain_of(name):
    for d, keys in DOMAINS:
        if any(k in name for k in keys):
            return d
    return "其它"


def verdict(body):
    # 报告写法有三种：`总结论：xxx` 同行、`**总结论**：xxx`、以及 `## 一、总结论` 标题后另起几行。
    # 所以先找"总结论"出现的位置，取其后 6 行当判据块，实在找不到再扫全文。
    flat = body.replace("*", "")
    idx = flat.find("总结论")
    block = flat[idx: idx + 400] if idx >= 0 else flat[:400]
    line = block
    if re.search(r"(^|\n)\s*(结论：)?\s*FAIL", line) or ("FAIL" in line and "无 FAIL" not in line and "无阻断级 FAIL" not in line and "无 FAIL" not in line):
        return "FAIL"
    if "有条件通过" in line:
        return "有条件通过"
    if "PASS" in line:
        return "PASS"
    return "未定"


def main():
    files = sorted(f for f in os.listdir(DOCS) if f.startswith("验收-") and f.endswith(".md"))
    counter = collections.Counter()
    by_domain = collections.defaultdict(collections.Counter)
    residuals = []
    for f in files:
        body = open(os.path.join(DOCS, f), encoding="utf-8", errors="ignore").read()
        v = verdict(body)
        counter[v] += 1
        by_domain[domain_of(f)][v] += 1
        # 余项：报告里 N1/N2/D1/D2 这类挂账条目的粗略计数
        n = len(re.findall(r"\b(?:N|D|R)\d+\b", body))
        if v != "PASS" and n:
            residuals.append((f, n))
    total = len(files)
    print(f"验收报告 {total} 份：PASS {counter['PASS']} / 有条件通过 {counter['有条件通过']} / FAIL {counter['FAIL']} / 未定 {counter['未定']}")
    print("按域：")
    for d in sorted(by_domain):
        c = by_domain[d]
        print(f"  {d}: 共 {sum(c.values())} 份 —— " + " ".join(f"{k} {v}" for k, v in c.items()))
    print(f"带余项标记的非 PASS 报告 {len(residuals)} 份；余项标记总数 {sum(n for _, n in residuals)}")
    worst = sorted(residuals, key=lambda x: -x[1])[:5]
    if worst:
        print("余项最多的五份：")
        for f, n in worst:
            print(f"  docs/{f} —— {n} 处 N/D/R 标记")
    return 0


if __name__ == "__main__":
    sys.exit(main())
