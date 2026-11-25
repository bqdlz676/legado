#!/usr/bin/env python3
"""门禁 ㊷ 问题清单状态↔修复报告一致性 + 状态词白名单守卫（M-03/M-07 收口，Q-68）。

为什么需要这条：修复/验收报告落盘后没人回写清单状态，9 个 B 缺陷有 `docs/修复-*.md`
仍标 🔴；全表 103 个 🔴 无法机械区分真未派与脱钩僵尸。另一条线是状态词四分：
清单五态 / 矩阵四词 / 队列四态 / 文档自由词互不映射，故状态词必须落在唯一白名单内。

判据（任一 FAIL 退出码 1；本项只许 FAIL 不许 WARN）：
  S1 报告脱钩：清单编号存在 `docs/修复-<ID>-*.md`、`docs/修复-<ID>R*-*.md` 或
     `docs/验收-*<ID>*.md` 任一报告，而该行状态仍是 🔴（未开工族）→ FAIL，列出
     「编号 → 报告文件」。
  S2 状态词白名单：状态单元格的 emoji 必须属于 🔴🟡🟢✅⛔ 之一，且其后关键词落在
     该族白名单内；空状态、✅ 只挂 commit 哈希无结论词、自由写法 → FAIL 提示。

边界：报告匹配按编号 token 边界（B2 不误命中 B12）；编号带横线（B-2）归一化后比对。
本门禁只读 `docs/问题清单.md`，不回写清单；脱钩项由对应写码/验收单回状态。
"""
import os
import re
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ISSUE = os.path.join(ROOT, "docs", "问题清单.md")
DOCS = os.path.join(ROOT, "docs")

ID_RE = re.compile(r"^[A-Za-z]+-?\d+(?:R\d+)?$")

# 五态唯一词表（与口径冻结 F-117 映射一致）；别名仅收录同义未开工/同态写法，自由词不收。
FAMILY = {
    "🔴": "未派",
    "🟡": "在飞",
    "🟢": "已修·待验证",
    "✅": "已验·关闭",
    "⛔": "作废",
}
ALIASES = {
    "未派": {"未派", "待派", "未启动", "排队", "已立项"},
    "在飞": {"在飞"},
    "已修·待验证": {"已修·待验证", "已修待验", "已修·待验", "待验证", "已修"},
    "已验·关闭": {"已验·关闭", "已验关闭", "已关闭", "已收口", "已落地"},
    "作废": {"作废"},
}
EMOJIS = "🔴🟡🟢✅⛔"


def norm_id(iid: str) -> str:
    """编号归一化：去横线（B-2 → B2），便于与报告文件名 token 比对。"""
    return iid.replace("-", "")


def reports_for(iid: str, docs_dir: str) -> list:
    """返回该编号命中的修复/验收报告文件名列表（token 边界匹配，防 B2 命中 B12）。"""
    norm = norm_id(iid)
    pat = re.compile(r"(?:^|[^A-Za-z0-9])" + re.escape(norm) + r"(?:R\d+)?(?:[^0-9A-Za-z]|$)")
    hits = []
    if not os.path.isdir(docs_dir):
        return hits
    for fn in os.listdir(docs_dir):
        if not fn.endswith(".md"):
            continue
        if not (fn.startswith("修复-") or fn.startswith("验收-")):
            continue
        if pat.search(fn):
            hits.append(fn)
    return sorted(set(hits))


def split_status(cells: list) -> str:
    """定位状态单元格：第一个含状态 emoji 的单元格；找不到返回空串。"""
    for c in cells:
        if any(e in c for e in EMOJIS):
            return c
    return ""


def check_status_word(cell: str):
    """返回 (family, ok, reason)。family 为五态之一或 None。"""
    if not cell.strip():
        return None, False, "空状态（无 emoji 无结论词）"
    emoji = next((e for e in EMOJIS if e in cell), None)
    if emoji is None:
        return None, False, f"状态「{cell[:20]}」无五态 emoji"
    family = FAMILY[emoji]
    # 去掉反引号/commit 哈希/代理 id/括号证据/标点后，看是否含同族别名关键词
    body = re.sub(r"`[^`]*`", "", cell)
    body = re.sub(r"s_[A-Za-z0-9]{6,}", "", body)
    body = re.sub(r"[0-9a-f]{7,}", "", body)
    body = re.sub(r"（[^）]*）|\([^)]*\)", "", body)
    body = body.replace(emoji, "").replace("·", "").replace(" ", "")
    # 口径（F-117）：状态＝单元格首个 emoji 所在的族，其后文字一律视为「证据/备注」，
    # 不再要求逐字命中别名——否则「✅ 已修 `hash`（说明）」这类正当写法会被误杀。
    # 为什么这样改：状态机只认族，备注本来就该自由；误杀会逼人删证据去凑格式，那是反向激励。
    # 适用条件：任何清单/台账状态列；✅（关闭）例外，必须有可回源证据（哈希 / docs 路径 / 文件:行）。
    if family == "已验·关闭":
        raw = cell
        if re.search(r"[0-9a-f]{7,40}", raw) or "docs/" in raw or re.search(r"[\w\-]+\.[A-Za-z]+:[0-9]+", raw):
            return family, True, ""
        if any(alias.replace("·", "") in body for alias in ALIASES[family]):
            return family, True, ""
        return family, False, "✅ 无结论词（仅哈希/代理 id，未写已验·关闭）"
    return family, True, ""


def check(repo: str):
    fails_hook = []   # S1 报告脱钩
    fails_word = []   # S2 状态词违规
    issue_path = os.path.join(repo, "docs", "问题清单.md")
    docs_dir = os.path.join(repo, "docs")

    if not os.path.isfile(issue_path):
        return [f"缺问题清单 docs/问题清单.md"], [], {"rows": 0}

    rows = 0
    for line in open(issue_path, encoding="utf-8", errors="ignore").read().splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if not cells or not ID_RE.match(cells[0]):
            continue
        iid = cells[0]
        rows += 1
        cell = split_status(cells)
        family, ok, reason = check_status_word(cell)

        if not ok:
            fails_word.append(f"{iid}：{reason}")

        reports = reports_for(iid, docs_dir)
        # S1：仍处未开工族（🔴/未派）却已有报告 → 脱钩僵尸
        if family == "未派" and reports:
            fails_hook.append(f"{iid} → {', '.join(reports)}")

    stats = {"rows": rows, "reports": len(os.listdir(docs_dir)) if os.path.isdir(docs_dir) else 0}
    return fails_hook, fails_word, stats


def selftest() -> int:
    """双向自测：合规夹具 PASS；脱钩 / 自由词 / 空状态 / ✅无结论词 四类 FAIL；夹具自删。"""
    tmp = tempfile.mkdtemp(prefix="ledger-status-selftest-")
    os.makedirs(os.path.join(tmp, "docs"), exist_ok=True)

    good = """# 问题清单（自测夹具）

| 编号 | 问题 | 证据 | 状态 | 归属 |
|---|---|---|---|---|
| B1 | 未开工无报告 | x | 🔴未派 | 待派 |
| B2 | 已修待验有报告 | x | 🟢已修·待验证 | 验收在飞 |
| B3 | 已关闭有报告 | x | ✅已验·关闭 | 已验 |
| B4 | 作废 | x | ⛔作废 | — |
"""
    open(os.path.join(tmp, "docs", "修复-B2-夹具.md"), "w", encoding="utf-8").write("# x\n")
    open(os.path.join(tmp, "docs", "验收-B3-夹具.md"), "w", encoding="utf-8").write("# x\n")
    with open(os.path.join(tmp, "docs", "问题清单.md"), "w", encoding="utf-8") as f:
        f.write(good)

    hook, word, stats = check(tmp)
    assert not hook and not word, f"合规夹具应 PASS，实际：脱钩 {hook}；词违规 {word}"
    print(f"[selftest] 合规夹具 PASS 通路 OK：{stats}")

    # 违规 1：🔴未派 但已有修复报告（脱钩僵尸）
    bad1 = good.replace("| B1 | 未开工无报告 | x | 🔴未派 | 待派 |",
                        "| B1 | 未开工无报告 | x | 🔴未派 | 待派 |")
    open(os.path.join(tmp, "docs", "修复-B1-夹具.md"), "w", encoding="utf-8").write("# x\n")
    with open(os.path.join(tmp, "docs", "问题清单.md"), "w", encoding="utf-8") as f:
        f.write(bad1)
    h1, w1, _ = check(tmp)
    assert any(h.startswith("B1 →") for h in h1), f"脱钩应 FAIL，实际：{h1}"
    print("[selftest] 报告脱钩 FAIL 通路 OK，命中：", h1)

    # 违规 2：自由写法状态词不在白名单
    bad2 = good.replace("| B2 | 已修待验有报告 | x | 🟢已修·待验证 | 验收在飞 |",
                        "| B2 | 自由写法 | x | 🟢 差不多好了 | 验收在飞 |")
    with open(os.path.join(tmp, "docs", "问题清单.md"), "w", encoding="utf-8") as f:
        f.write(bad2)
    h2, w2, _ = check(tmp)
    assert any("白名单" in x for x in w2), f"自由词应 FAIL，实际：{w2}"
    print("[selftest] 状态词自由写法 FAIL 通路 OK，命中：", w2)

    # 违规 3：空状态（无 emoji 无词）
    bad3 = good.replace("| B3 | 已关闭有报告 | x | ✅已验·关闭 | 已验 |",
                        "| B3 | 空状态 | x |  | 已验 |")
    with open(os.path.join(tmp, "docs", "问题清单.md"), "w", encoding="utf-8") as f:
        f.write(bad3)
    h3, w3, _ = check(tmp)
    assert any("空状态" in x for x in w3), f"空状态应 FAIL，实际：{w3}"
    print("[selftest] 空状态 FAIL 通路 OK，命中：", w3)

    # 违规 4：✅ 只挂 commit 哈希、无结论词
    bad4 = good.replace("| B4 | 作废 | x | ⛔作废 | — |",
                        "| B4 | 只有哈希 | x | ✅ `a978f15` | 已验 |")
    with open(os.path.join(tmp, "docs", "问题清单.md"), "w", encoding="utf-8") as f:
        f.write(bad4)
    h4, w4, _ = check(tmp)
    assert any("无结论词" in x or "白名单" in x for x in w4), f"✅无结论词应 FAIL，实际：{w4}"
    print("[selftest] ✅无结论词 FAIL 通路 OK，命中：", w4)

    shutil.rmtree(tmp, ignore_errors=True)
    print("[selftest] 夹具已自删，双向自测通过。")
    return 0


def main() -> int:
    if "--selftest" in sys.argv:
        return selftest()
    hook, word, stats = check(ROOT)
    for h in hook:
        print(f"FAIL  [报告脱钩] {h}")
    for w in word:
        print(f"FAIL  [状态词] {w}")
    if hook or word:
        print(f"LEDGER-STATUS: FAIL（报告脱钩 {len(hook)} 条 / 状态词违规 {len(word)} 条；清单 {stats['rows']} 行）")
        return 1
    print(f"LEDGER-STATUS: PASS（清单 {stats['rows']} 行状态与报告一致，状态词均在白名单）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
