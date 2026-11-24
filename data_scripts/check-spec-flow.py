#!/usr/bin/env python3
"""SPEC-FLOW 四向对齐巡检（F-88，2026-11-24 用户令「不断检查完整性 + 后续新规格持续填入」）。

为什么必须机械巡检：规格从「用户原话 → 切片 → 登记册 → 缺陷号/档位 → 契约索引 → 派单台账/队列」
是一条单向链；任何一环漏登，后面的写码/验收都无锚点可回源。旧 `spec-check.sh` 只做五向中的四向，
且不反向校验「切片文件 ↔ 登记册行」，切片 21~25 漏登记长期无人发现。本脚本把四个方向的双向
对齐都跑一遍，任一断裂判 FAIL 并逐条打印漏项。

四个方向（每向都是双向）：
  ① 切片文件 ↔ 登记册行：每片切片必须有对应 S 登记行；每个非否决 S 行的切片文件必须存在。
  ② 登记册行 ↔ 问题清单缺陷号：[已立项] 行的缺陷号必须能在问题清单找到（防编造/漏立号）。
  ③ 登记册行 ↔ 契约索引：模块级缺陷（D14 及以上）必须在契约索引有模块条目，或行内显式
     标注「未接线/归档/吸收/复用/待拍板」；子项级旧缺陷（B*/D8~D13/K3/F13/D5~D7/U12）
     归属其所属模块行，不单独要求索引条目。
  ④ 登记册行 ↔ 派单踪迹：[已立项] 行的缺陷号必须在 问题清单 / 派单台账 / 派单队列 三处之一
     留有踪迹；三处均无 = 「悬空立项」FAIL。在问题清单在册的 🔴/🟡/🟢 积压不算悬空
     （积压由清单统一排队，队列只装调度批次）。

豁免口径（行内出现以下标记即不参与 ②③④ 校验）：
  - 治理项（不立缺陷号，落地物 = 文档/脚本 + commit sha）；
  - 新立（立项规则 §4 允许的内部自编号，写入前已 grep 清单 0 命中，属在册约定）；
  - 归档/吸收（已被 D17 等既有模块吸收，不再独立派单）；
  - 已否决 / 已验收（闭环态，不再要求活踪迹）。

用法：
  python3 data_scripts/check-spec-flow.py            # 巡检仓库现状
  python3 data_scripts/check-spec-flow.py --selftest # 造夹具跑 FAIL/PASS 两条路径后自删
退出码：0 = SPEC-FLOW: PASS；1 = 存在断裂。
"""
import argparse
import glob
import os
import re
import sys
import tempfile

# 缺陷号：字母分区 + 数字（可带 R 修订后缀，如 B10R1）；前后不得再接字母数字，防误切 hex/版本号
DEFECT_RE = re.compile(r"(?<![A-Za-z0-9])([A-Z])(\d+R?\d*)(?![0-9])")
# 非缺陷号前缀：L=验收级别 L0~L4；S/Y/Q/N=登记册内部编号（跨行互引，不是问题清单缺陷号）
NON_DEFECT_PREFIX = {"L", "S", "Y", "Q", "N"}
SLICE_RE = re.compile(r"切片-(\d+)")
ROW_ID_RE = re.compile(r"^(S|Y|Q|N)-?(\d+)$")

# 子项级旧缺陷前缀/号段：归属所属模块的索引行，不单独要求索引条目（方向③豁免）
SUBDEFECT_PREFIX = ("B", "F", "K", "U")
SUBDEFECT_D = {f"D{n}" for n in range(5, 14)}  # D5~D13 为搜索族/缓存/字段等子项


def repo_paths(root: str) -> dict:
    return {
        "reg": os.path.join(root, "docs", "规格登记册.md"),
        "spec_dir": os.path.join(root, "docs", "落文-规格切片"),
        "ql": os.path.join(root, "docs", "问题清单.md"),
        "idx": os.path.join(root, "docs", "落文-模块契约索引.md"),
        "ledger": os.path.join(root, "docs", "派单台账.md"),
        "queue": os.path.join(root, "docs", "派单队列.md"),
    }


def read_text(path: str) -> str:
    if not os.path.isfile(path):
        return ""
    with open(path, encoding="utf-8") as f:
        return f.read()


def parse_register(text: str) -> list:
    """解析登记册表行。返回 dict 列表：id / prefix / num / row / cells / status。"""
    rows = []
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 2:
            continue
        m = ROW_ID_RE.match(cells[0])
        if not m:
            continue
        status = None
        for tag in ("[已立项]", "[待立项]", "[已验收]", "[已否决]"):
            if tag in line:
                status = tag
                break
        if status is None and "已否决" in line:
            status = "[已否决]"  # §五合并表用纯文本「已否决」，无方括号
        rows.append({
            "id": cells[0],
            "prefix": m.group(1),
            "num": int(m.group(2)),
            "row": line,
            "cells": cells,
            "status": status,
        })
    return rows


def defect_tokens(row: str) -> list:
    """提取行内全部缺陷号（去重保序）；剔除验收级别与登记册内部编号。"""
    out, seen = [], set()
    for m in DEFECT_RE.finditer(row):
        if m.group(1) in NON_DEFECT_PREFIX:
            continue
        tok = m.group(1) + m.group(2)
        if tok not in seen:
            seen.add(tok)
            out.append(tok)
    return out


def is_exempt(row: dict) -> tuple[bool, str]:
    """返回 (是否豁免, 豁免理由)。豁免行不参与 ②③④ 活踪迹校验。"""
    line = row["row"]
    if row["status"] == "[已否决]":
        return True, "已否决"
    if row["status"] == "[已验收]":
        return True, "已验收闭环"
    if "治理项" in line:
        return True, "治理项（不立缺陷号）"
    if "新立" in line:
        return True, "内部新立编号（立项规则 §4 在册约定）"
    if "归档" in line or "吸收" in line:
        return True, "已归档/被既有模块吸收"
    if re.search(r"^\s*—\s*$", row["cells"][2] if len(row["cells"]) > 2 else ""):
        return True, "缺陷号列为 —"
    return False, ""


def is_module_level(tok: str) -> bool:
    """模块级缺陷才要求索引条目；子项级旧缺陷归属其模块行。"""
    if tok in SUBDEFECT_D:
        return False
    if tok[:1] in SUBDEFECT_PREFIX:
        return False
    if tok.startswith("D") and tok[1:].isdigit() and int(tok[1:]) >= 14:
        return True
    return False


def has_token(tok: str, text: str) -> bool:
    return re.search(r"(?<![A-Za-z0-9])" + re.escape(tok) + r"(?![0-9])", text) is not None


def run_checks(root: str) -> tuple[list, dict]:
    p = repo_paths(root)
    reg_text = read_text(p["reg"])
    ql_text = read_text(p["ql"])
    idx_text = read_text(p["idx"])
    ledger_text = read_text(p["ledger"])
    queue_text = read_text(p["queue"])
    rows = parse_register(reg_text)

    fails = {"①切片↔登记册": [], "②登记册↔问题清单": [], "③登记册↔契约索引": [], "④登记册↔派单踪迹": []}

    # ① 双向：切片文件 ↔ S 登记行
    slice_files = {}
    for fp in glob.glob(os.path.join(p["spec_dir"], "切片-*.md")):
        m = SLICE_RE.search(os.path.basename(fp))
        if m:
            slice_files[int(m.group(1))] = os.path.basename(fp)
    s_rows = {r["num"]: r for r in rows if r["prefix"] == "S"}

    for num, fname in sorted(slice_files.items()):
        if num not in s_rows:
            fails["①切片↔登记册"].append(
                f"切片文件 `{fname}` 无对应 S-{num:02d} 登记行（漏登记）")
    for num, r in sorted(s_rows.items()):
        if r["status"] == "[已否决]":
            continue  # 跳号预留/否决切片不要求文件存在
        if num not in slice_files:
            fails["①切片↔登记册"].append(
                f"登记行 {r['id']} 指向的切片 `切片-{num:02d}` 文件不存在（悬空引用）")

    # ②③④ 逐行校验
    for r in rows:
        if r["status"] != "[已立项]":
            continue
        exempt, reason = is_exempt(r)
        tokens = [t for t in defect_tokens(r["row"]) if t != r["id"].replace("-", "")]
        if not tokens:
            if not exempt:
                fails["②登记册↔问题清单"].append(
                    f"{r['id']} [已立项] 行无缺陷号且未标治理/新立/豁免")
            continue
        if exempt:
            continue

        for tok in tokens:
            # ② 问题清单必须找得到
            if not has_token(tok, ql_text):
                fails["②登记册↔问题清单"].append(
                    f"{r['id']} 缺陷号 {tok} 在问题清单中不存在（编造号或漏立号）")
            # ③ 模块级缺陷必须在契约索引有条目（或行内有豁免标记）
            if is_module_level(tok) and not has_token(tok, idx_text):
                if not any(k in r["row"] for k in ("未接线", "归档", "吸收", "并入", "待拍板")):
                    fails["③登记册↔契约索引"].append(
                        f"{r['id']} 模块缺陷 {tok} 在契约索引无模块条目，且行内未标「未接线」")
            # ④ 三处派单踪迹至少留一处；三处皆无 = 悬空立项
            traced = has_token(tok, ql_text) or has_token(tok, ledger_text) or has_token(tok, queue_text)
            if not traced:
                fails["④登记册↔派单踪迹"].append(
                    f"{r['id']} 缺陷号 {tok} 在问题清单/台账/队列三处均无踪迹（悬空立项）")

    return rows, fails


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--selftest", action="store_true", help="造夹具跑 FAIL/PASS 两条路径后自删")
    ap.add_argument("--root", default=None, help="覆盖仓库根目录（自检用）")
    args = ap.parse_args()

    if args.selftest:
        return selftest()

    root = args.root or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    rows, fails = run_checks(root)

    print(f"SPEC-FLOW 四向对齐巡检（共登记 {len(rows)} 行）")
    total = 0
    for name, items in fails.items():
        total += len(items)
        if items:
            print(f"✗ {name}：{len(items)} 条断裂")
            for it in items:
                print(f"    - {it}")
        else:
            print(f"✓ {name}：对齐")
    print(f"四类漏项计数：切片↔登记册 {len(fails['①切片↔登记册'])} / "
          f"清单 {len(fails['②登记册↔问题清单'])} / "
          f"索引 {len(fails['③登记册↔契约索引'])} / "
          f"派单踪迹 {len(fails['④登记册↔派单踪迹'])}")
    if total:
        print("SPEC-FLOW: FAIL")
        return 1
    print("SPEC-FLOW: PASS")
    return 0


# ---------- 自检夹具：FAIL 路径与 PASS 路径各跑一遍，结束后自删 ----------

FIXTURE_REG_PASS = """# 登记册（夹具）

| 编号 | 规格 | 立项缺陷号 | 状态 | 验收档位 |
|---|---|---|---|---|
| S-21 | 架构层 | D24（复用） | [已立项] | C 档 |
| N-100 | 夹具模块 | D99（复用） | [已立项] | C 档 |
"""
FIXTURE_QL = """# 问题清单（夹具）
| D24 | 架构层 | 🔴 |
| D99 | 夹具模块 | 🔴 |
"""
FIXTURE_IDX = """# 契约索引（夹具）
| D24 架构层 | core/data/ | 切片-21 |
| D99 夹具模块 | app/ | N-100 |
"""
FIXTURE_QUEUE = "# 派单队列（夹具）\n"
FIXTURE_LEDGER = "# 派单台账（夹具）\n"


def write_fixture(root: str, variant: str) -> None:
    os.makedirs(os.path.join(root, "docs", "落文-规格切片"), exist_ok=True)
    if variant == "pass":
        reg = FIXTURE_REG_PASS
        ql, idx = FIXTURE_QL, FIXTURE_IDX
        open(os.path.join(root, "docs", "落文-规格切片", "切片-21-架构层.md"), "w", encoding="utf-8").write("# 夹具\n")
    else:
        # FAIL 夹具：① 切片-90 漏登记；② 行内缺陷 Z98 清单不存在；
        # ③ 模块缺陷 D97 索引无条目；④ 缺陷 X96 三处踪迹皆无（悬空）。
        reg = """# 登记册（夹具 FAIL）

| 编号 | 规格 | 立项缺陷号 | 状态 | 验收档位 |
|---|---|---|---|---|
| S-21 | 架构层 | D24（复用） | [已立项] | C 档 |
| N-101 | 夹具清单缺失 | Z98（复用） | [已立项] | C 档 |
| N-102 | 夹具索引缺失 | D97（复用） | [已立项] | C 档 |
| N-103 | 夹具悬空 | X96（复用） | [已立项] | C 档 |
"""
        ql = "# 问题清单（夹具）\n| D24 | 架构层 | 🔴 |\n| D97 | 夹具索引缺失 | 🔴 |\n"
        idx = "# 契约索引（夹具）\n| D24 架构层 | core/data/ |\n"
        open(os.path.join(root, "docs", "落文-规格切片", "切片-21-架构层.md"), "w", encoding="utf-8").write("# 夹具\n")
        open(os.path.join(root, "docs", "落文-规格切片", "切片-90-漏登记.md"), "w", encoding="utf-8").write("# 夹具\n")
    for name, text in (("规格登记册.md", reg), ("问题清单.md", ql),
                       ("落文-模块契约索引.md", idx), ("派单队列.md", FIXTURE_QUEUE),
                       ("派单台账.md", FIXTURE_LEDGER)):
        open(os.path.join(root, "docs", name), "w", encoding="utf-8").write(text)


def selftest() -> int:
    failures = []
    for variant, expect_rc in (("fail", 1), ("pass", 0)):
        tmp = tempfile.mkdtemp(prefix="spec-flow-selftest-")
        try:
            write_fixture(tmp, variant)
            _, fails = run_checks(tmp)
            rc = 0 if not any(fails.values()) else 1
            if rc != expect_rc:
                failures.append(f"{variant} 路径退出码 {rc}，期望 {expect_rc}")
            if variant == "fail":
                flat = [x for v in fails.values() for x in v]
                if len(flat) < 4:
                    failures.append(f"FAIL 路径仅抓到 {len(flat)} 条，期望 ≥4 条四向断裂")
                joined = "\n".join(flat)
                for probe in ("切片-90", "Z98", "D97", "X96"):
                    if probe not in joined:
                        failures.append(f"FAIL 路径漏报 {probe}")
            print(f"[selftest] {variant} 路径：rc={rc}（期望 {expect_rc}），断裂 {sum(len(v) for v in fails.values())} 条")
        finally:
            import shutil
            shutil.rmtree(tmp, ignore_errors=True)

    if failures:
        print("[selftest] FAIL：")
        for f in failures:
            print("  - " + f)
        return 1
    print("[selftest] PASS：FAIL/PASS 两条路径均符合预期，夹具已自删")
    return 0


if __name__ == "__main__":
    sys.exit(main())
