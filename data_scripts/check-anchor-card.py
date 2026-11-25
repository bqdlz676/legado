#!/usr/bin/env python3
"""门禁 ⑲ 锚点卡守卫（F-85 前置设计费）+ M-08 编号↔锚点卡反查。

事故：12 类卡片中 BookCard / TopicCard 两张返工，根因＝执行代理凭记忆转述规格
（分享卡三背景 lerp 0.35/0.12/0.10 记岔）；不是能力问题，是派单没给可逐字
引用的原文锚点。前置设计费＝写码单前先落 ≤30 行锚点卡（逐字引文 + 参数表 +
可测判据），用小钱买断返工。

【既有判据，原样保留，不许改弱】（只校验本次 git status 新增/修改的报告，
历史留档不追改，同 ⑭/⑮ 口径）：
  写码类回执（`docs/修复-*.md`、`docs/实现-*.md`）：
    ① 正文含 `锚点卡：docs/锚点/<缺陷号>-<主题>.md`；② 该锚点卡文件真实存在。
  验收类报告（`docs/验收-*.md`）：
    ① 正文含锚点卡路径；② 正文有逐字引文段（`「…」` 引文 或 `文件:行` 定位串），
       禁止"按记忆里的规格验收"。
  豁免：报告显式写明"机械改动"（改一行 import / 补一个 strokeLineJoin 这类），
  或写明"无规格→已按 F-73 退回设计"，跳过锚点卡要求。

【M-08 新增：编号↔锚点卡反查】
  R1 主对象反查（FAIL）：报告主对象缺陷号必须能在 `docs/锚点/` 找到对应卡
     （按文件名前缀编号匹配），否则判红——有报告无卡，等于凭记忆开工，F-85 被架空。
     主对象口径：先取报告文件名里的首个缺陷号；文件名无号则取正文首个非引用、
     非标题段落的首个缺陷号；都取不到则本项跳过（报告本身无编号可反查）。
  R2 次要提及（仅提示，不判红）：正文其余缺陷号缺卡只打 WARN。为什么降级：
     次要提及的号可能是"已收口并入别号"的旧号（如 Q-82 并入 Q-89、Q-87 并入 Q-90），
     旧号不再单独立卡，硬反查会误红；行内带"已并入/并入/作废/收口"字样的号直接豁免。
  R3 孤儿卡扫描（仅提示，不判红）：`docs/锚点/` 下未被任何报告 / 派单队列 /
     派单台账引用的卡 → WARN。为什么不判红：新卡常先落卡、后写报告（前置设计费
     本来就要求卡先于写码单），判红会逼代理先补报告引用再落卡，顺序被倒过来。

误判风险与兜底：
  - 编号带 R 后缀（B8R2）/ 横线（N-017、S-01）：归一化去横线、大写后按文件名前缀比对，
    防写法差异误判；B2 不得命中 B12（token 边界 + 整号相等）。
  - 锚点卡路径被反引号/引号包裹：捕获后剥掉首尾 ` ' " 再判文件存在。
  - 设计类卡（S-xx）由队列/台账引用而非报告：孤儿扫描同时扫队列与台账。

退出码：全部 PASS=0；任一 FAIL=1。--strict 只打印 FAIL 明细；--orphan 只跑
仓库级孤儿卡扫描（WARN，退出码恒 0）。
"""
import argparse
import os
import re
import sys
import tempfile
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANCHOR_DIR = os.path.join(ROOT, "docs", "锚点")

ANCHOR_RE = re.compile(r"锚点卡[：:]\s*[`'\"]?(docs/锚点/[^\s）)」`'\"]+\.md)[`'\"]?")
LOC_RE = re.compile(r"[\w\-/\\.]+\.[A-Za-z]+:[0-9]+")
QUOTE_RE = re.compile(r"「[^」]{4,}」")
EXEMPT_RE = re.compile(r"机械改动|无规格→已按 F-73 退回设计|F-73 退回设计")

# 缺陷号：在册字母族 + 数字（可带横线 / R 轮次后缀 / 小写子号），前后留 token 边界防 B2 命中 B12。
# 族名写死为在册缺陷族：JDK21 这类 3 字母技术词不命中；`§八-D1` 这类章节引用号前接横线，lookbehind 排除。
_FAM = r"(?:A|B|C|D|E|F|N|P|Q|S|U|V)"
ID_RE = re.compile(rf"(?<![A-Za-z0-9\-])({_FAM}-?\d+(?:R\d+)?(?:-[a-z]\d+)?)(?![0-9A-Za-z])")
# 合并/收口沿革字样：同行出现即视为旧号，不再要求独立锚点卡。
MERGED_RE = re.compile(r"已并入|并入\s*[A-Z]|已合并|作废|已收口|并入别号")

REPORT_PREFIXES = ("修复-", "验收-", "实现-", "清理-")


def classify(path: str) -> str:
    name = os.path.basename(path)
    if name.startswith(("修复-", "实现-")):
        return "write"
    if name.startswith("验收-选型-"):
        return "f73"  # F-73 三段流水线第二段：候选规格本身即锚点，不重复付前置设计费
    if name.startswith("验收-"):
        return "review"
    return "other"


def norm_id(iid: str) -> str:
    """编号归一化：去横线、大写，便于 B-2 / B2、S-01 / S01 等价比对。"""
    return iid.replace("-", "").upper()


def extract_ids(text: str) -> list:
    """按出现顺序抽取去重编号（保留首次出现次序）。"""
    seen, out = set(), []
    for m in ID_RE.finditer(text or ""):
        iid = m.group(1)
        n = norm_id(iid)
        if n not in seen:
            seen.add(n)
            out.append(iid)
    return out


def card_index(anchor_dir: str) -> dict:
    """文件名前缀编号 → 文件名列表；前缀取文件名开头的编号段（支持 S-01 这类带横线号）。"""
    idx: dict[str, list] = {}
    if not os.path.isdir(anchor_dir):
        return idx
    for fn in os.listdir(anchor_dir):
        if not fn.endswith(".md"):
            continue
        stem = fn[:-3]
        m = re.match(r"^([A-Z]{1,3}-?\d+(?:R\d+)?)", stem)
        key = norm_id(m.group(1)) if m else norm_id(stem.split("-")[0])
        idx.setdefault(key, []).append(fn)
    return idx


def main_object_id(filename: str, text: str):
    """主对象口径：文件名首个缺陷号 → 正文首个非引用/非标题段落首个缺陷号。
    文件名先剥掉 修复-/验收- 等中文前缀，避免前缀后的横线把编号 lookbehind 挡掉。"""
    stem = os.path.basename(filename)
    for pre in REPORT_PREFIXES:
        stem = stem.replace(pre, "")
    ids = extract_ids(stem)
    if ids:
        return ids[0]
    for line in text.splitlines():
        s = line.strip()
        if not s or s.startswith(">") or s.startswith("#"):
            continue
        ids = extract_ids(s)
        if ids:
            return ids[0]
    return None


def check_file(path: str, anchor_idx: dict):
    if not os.path.isfile(path):
        return {"path": path, "ok": False, "missing": ["文件不存在"], "warns": []}
    text = open(path, encoding="utf-8", errors="replace").read()
    kind = classify(path)
    if kind in ("other", "f73") or EXEMPT_RE.search(text):
        return {"path": path, "ok": True, "missing": [], "warns": [], "exempt": True}

    missing: list[str] = []
    warns: list[str] = []

    # 既有判据①：锚点卡路径存在且文件真实存在（原样保留）；路径两侧允许反引号/引号包裹。
    m = ANCHOR_RE.search(text)
    anchor = m.group(1).strip(" `'\"") if m else None
    if not anchor:
        missing.append("缺锚点卡路径")
    else:
        anchor_abs = os.path.join(ROOT, anchor)
        if not os.path.isfile(anchor_abs):
            missing.append(f"锚点卡不存在 {anchor}")

    # 既有判据②：验收报告须有逐字引文段（原样保留）。
    if kind == "review":
        if not (LOC_RE.search(text) or QUOTE_RE.search(text)):
            missing.append("验收报告缺逐字引文段（「…」或 文件:行）")

    # M-08 R1/R2：编号↔卡反查。
    # R1 只对写码类报告判红：验收报告的锚点卡指向催生该缺陷的上游卡（如 Q53），
    # 被验缺陷号本身未必单独建卡，硬判红会误杀既有验收回执。
    main_id = main_object_id(path, text)
    if main_id is not None:
        if norm_id(main_id) not in anchor_idx:
            msg = f"主对象 {main_id} 无对应锚点卡（docs/锚点/ 按前缀编号匹配）"
            if kind == "review":
                warns.append(msg + "（验收报告降级提示）")
            else:
                missing.append(msg)
        # 次要提及：逐行判定合并沿革，缺卡仅提示；同文件去重，单文件最多列 5 条防刷屏。
        seen_w: set[str] = set()
        for line in text.splitlines():
            for sid in extract_ids(line):
                if norm_id(sid) == norm_id(main_id):
                    continue
                if norm_id(sid) in anchor_idx:
                    continue
                if MERGED_RE.search(line):
                    continue  # 已并入别号/作废的旧号，不要求独立卡
                tag = f"次要提及 {sid} 无锚点卡（若为已收口并入号可忽略）"
                if tag not in seen_w:
                    seen_w.add(tag)
                    if len(seen_w) <= 5:
                        warns.append(tag)

    return {"path": path, "ok": not missing, "missing": missing, "warns": warns}


def scan_orphans(anchor_dir: str, docs_dir: str) -> list:
    """R3：孤儿卡扫描。引用来源＝报告正文/文件名 + 派单队列 + 派单台账。"""
    idx = card_index(anchor_dir)
    referenced: set[str] = set()
    sources = []
    if os.path.isdir(docs_dir):
        for fn in os.listdir(docs_dir):
            if fn.endswith(".md") and fn.startswith(REPORT_PREFIXES):
                sources.append(os.path.join(docs_dir, fn))
    for ledger in ("派单队列.md", "派单台账.md"):
        p = os.path.join(docs_dir, ledger)
        if os.path.isfile(p):
            sources.append(p)
    for src in sources:
        try:
            txt = open(src, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        for iid in extract_ids(os.path.basename(src)) + extract_ids(txt):
            referenced.add(norm_id(iid))
    orphans = []
    for key, files in sorted(idx.items()):
        if key not in referenced:
            orphans.extend(files)
    return orphans


def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--orphan", action="store_true", help="只跑仓库级孤儿卡扫描（WARN）")
    parser.add_argument("--selftest", action="store_true")
    parser.add_argument("reports", nargs="*")
    args = parser.parse_args(argv)

    if args.selftest:
        return selftest()

    anchor_idx = card_index(ANCHOR_DIR)

    if args.orphan:
        orphans = scan_orphans(ANCHOR_DIR, os.path.join(ROOT, "docs"))
        if orphans:
            for fn in orphans:
                print(f"WARN  [孤儿卡] {fn} 未被报告/队列/台账引用（新卡先落卡后写单，不判红）")
            print(f"ANCHOR-ORPHAN: WARN（孤儿卡 {len(orphans)} 张，不影响 GATE 结论）")
        else:
            print("ANCHOR-ORPHAN: PASS（在册卡均有引用）")
        return 0

    files = args.reports
    if not files:
        print("（无待检报告）")
        return 0

    passed = failed = 0
    for path in files:
        res = check_file(path, anchor_idx)
        for w in res.get("warns", []):
            print(f"WARN  {os.path.basename(path)}：{w}")
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


def selftest() -> int:
    """双向自测：合规夹具 PASS；主对象缺卡 FAIL；次要提及缺卡仅 WARN；孤儿卡 WARN；夹具自删。"""
    tmp = tempfile.mkdtemp(prefix="anchor-card-selftest-")
    docs = os.path.join(tmp, "docs")
    anc = os.path.join(docs, "锚点")
    os.makedirs(anc, exist_ok=True)

    # 合规夹具：B20 主对象有卡；次要提及 B1（历史沿革行带"并入"字样）豁免。
    open(os.path.join(anc, "B20-点字连点竞态.md"), "w", encoding="utf-8").write("# B20 卡\n")
    good = (
        "# 修复-B20：连点无闸收口\n\n"
        "> 锚点卡：`docs/锚点/B20-点字连点竞态.md`\n\n"
        "正文对照 B20；旧号 B1 已并入 B20 收口，不再单独立卡。\n"
    )
    good_path = os.path.join(docs, "修复-B20-夹具.md")
    open(good_path, "w", encoding="utf-8").write(good)

    idx = card_index(anc)
    r = check_file(good_path, idx)
    assert r["ok"], f"合规夹具应 PASS，实际缺：{r['missing']}；提示：{r['warns']}"
    print("[selftest] 合规夹具 PASS 通路 OK（主对象有卡 / 并入旧号豁免）")

    # 违规 1：主对象 B99 无卡 → FAIL。
    bad1 = good.replace("B20", "B99").replace("B1 已并入 B20", "B1 已并入 B99")
    bad1_path = os.path.join(docs, "修复-B99-夹具.md")
    open(bad1_path, "w", encoding="utf-8").write(bad1)
    r1 = check_file(bad1_path, idx)
    assert not r1["ok"] and any("B99" in x for x in r1["missing"]), f"主对象缺卡应 FAIL，实际：{r1}"
    print("[selftest] 主对象缺卡 FAIL 通路 OK，命中：", r1["missing"])

    # 违规 2：次要提及 B77 无卡且无并入字样 → 仅 WARN，不 FAIL。
    bad2 = good.replace("旧号 B1 已并入 B20 收口，不再单独立卡。", "另见 B77 的边界段。")
    bad2_path = os.path.join(docs, "修复-B20-次要夹具.md")
    open(bad2_path, "w", encoding="utf-8").write(bad2)
    r2 = check_file(bad2_path, idx)
    assert r2["ok"], f"次要提及缺卡不应 FAIL，实际：{r2['missing']}"
    assert any("B77" in w for w in r2["warns"]), f"次要提及应出 WARN，实际：{r2['warns']}"
    print("[selftest] 次要提及缺卡仅 WARN 通路 OK，提示：", r2["warns"])

    # 违规 3：孤儿卡扫描 → WARN 列出未被引用的卡。
    open(os.path.join(anc, "B88-孤儿卡.md"), "w", encoding="utf-8").write("# B88\n")
    orphans = scan_orphans(anc, docs)
    assert any("B88" in o for o in orphans), f"孤儿卡应被扫出，实际：{orphans}"
    print("[selftest] 孤儿卡 WARN 通路 OK，命中：", orphans)

    shutil.rmtree(tmp, ignore_errors=True)
    print("[selftest] 夹具已自删，双向自测通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
