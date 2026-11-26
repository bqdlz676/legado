#!/usr/bin/env python3
"""陈旧口径门禁（audit 级，Q-285，2026-11-26）。

为什么需要它：
  仓内已有若干符号被封死/整体删除（跨 App ContentProvider `ReaderProvider`、旧关于页
  `AboutActivity`），但历史回执、审计、锚点、静态清单里仍按旧口径描述它们。接手者若照
  旧文档去"修"一个已经不存在的东西，会浪费一整棒。本脚本把"已删除符号登记表"
  （`data_scripts/stale-symbols.json`）落成可机械扫描的门禁，docs/ 里仍按旧口径出现
  这些符号、且同行没有失效标记词的行，逐条列 `STALE-CLAIM` 提示，供人工回写。

级别：audit 级，**退出码恒为 0**，不拦 push；只把噪音摊开成可逐条回写的清单。

三类防假绿（对应任务书 §5）：
  a) 符号在代码里仍有命中（说明没真删 / 登记表过期）→ 先 `grep -rn <symbol> app/src/`
     校验；命中落在 `code_residual` 白名单（如正向安全断言测试）之外，直接报
     `REGISTRY-STALE`，不再提示文档，避免"文档全绿但代码里东西还在"。
  b) 登记表清空后静默失效 → 登记表为空时只报 `REGISTRY-EMPTY` INFO，不打 PASS；
     `--selftest` 专门覆盖这一形态。
  c) 失效标记词必须与符号**同一行**才算豁免；某段无关文字里顺带写了"历史"不会整文件豁免。

噪声抑制（任务书 §7，落到实现，不靠人工过滤）：
  - 头部失效标记豁免：文件前 12 行内存在"符号 + 标记词"同行的失效声明，整文件视为
    已标记历史报告；正文历史提及只汇总计数打 `HISTORY-MARKED`，不逐条刷行。
  - 同文件封顶：未被头部标记的文件，每个符号只报首个未标记命中，其后同类命中折成
    `+N more in same file`，防止一份长回执刷出几十行提示。
  - 标记词同行判定：豁免以行为单位，标记词与符号必须出现在同一行。

用法：
  python3 data_scripts/check-stale-claims.py            # 扫真实仓 docs/
  python3 data_scripts/check-stale-claims.py --selftest # /tmp 夹具自测，末行 PASS 3/3
  python3 data_scripts/check-stale-claims.py --root <目录>  # 自测/换根用
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REGISTRY = os.path.join(ROOT, "data_scripts", "stale-symbols.json")
DEFAULT_MARKERS = ["已删除", "已封死", "已清除", "失效", "判废", "历史", "勿据以施工"]
HEADER_WINDOW = 12  # 头部失效声明判定窗口（行）
DOC_EXTS = (".md", ".txt")


# ---------- 登记表 ----------

def load_registry(path):
    with open(path, encoding="utf-8") as fh:
        doc = json.load(fh)
    markers = doc.get("meta", {}).get("marker_words", DEFAULT_MARKERS)
    return doc.get("symbols", []), markers


# ---------- 代码侧校验（防假绿 a） ----------

def grep_code(symbol, code_root):
    """返回 [(rel_path, lineno, text)]；grep 不可用时退回 python 遍历。"""
    hits = []
    if not os.path.isdir(code_root):
        return hits
    try:
        proc = subprocess.run(
            ["grep", "-rn", "--", symbol, code_root],
            capture_output=True, text=True, env={**os.environ, "LC_ALL": "C"},
        )
        for row in proc.stdout.splitlines():
            m = re.match(r"^(.*?):(\d+):(.*)$", row)
            if m:
                hits.append((m.group(1), int(m.group(2)), m.group(3)))
    except FileNotFoundError:
        for dirpath, _, names in os.walk(code_root):
            for name in names:
                if not name.endswith((".kt", ".java", ".xml")):
                    continue
                fp = os.path.join(dirpath, name)
                with open(fp, encoding="utf-8", errors="replace") as fh:
                    for i, line in enumerate(fh, 1):
                        if symbol in line:
                            hits.append((fp, i, line.rstrip()))
    return hits


def is_residual(rel_path, residuals):
    """白名单前缀匹配：rel_path 以任一 residual 条目开头即视为允许残留。"""
    norm = rel_path.replace(os.sep, "/")
    for item in residuals or []:
        item = item.replace(os.sep, "/")
        if norm == item or norm.startswith(item + "/"):
            return True
    return False


# ---------- 文档侧扫描 ----------

def symbols_of(entry):
    return [entry["symbol"]] + list(entry.get("aliases", []))


def line_exempt(line, markers):
    return any(mk in line for mk in markers)


def header_marked(lines, needles, markers):
    """前 HEADER_WINDOW 行内：任一符号与任一标记词同行 → 文件级历史标记成立。"""
    for line in lines[:HEADER_WINDOW]:
        if any(n in line for n in needles) and line_exempt(line, markers):
            return True
    return False


def scan_docs(entry, roots, markers):
    """返回 (claims, history_files)。
    claims: dict[rel_file] -> {"first": (lineno, text), "more": int, "symbol": str}
    history_files: dict[rel_file] -> 历史提及总行数（头部已标记）
    """
    needles = symbols_of(entry)
    claims = {}
    history = {}
    for root in roots:
        if not os.path.isdir(root):
            continue
        for dirpath, _, names in os.walk(root):
            for name in sorted(names):
                if not name.endswith(DOC_EXTS):
                    continue
                fp = os.path.join(dirpath, name)
                rel = os.path.relpath(fp, ROOT)
                with open(fp, encoding="utf-8", errors="replace") as fh:
                    lines = fh.read().splitlines()
                marked = header_marked(lines, needles, markers)
                unmarked = []
                for i, line in enumerate(lines, 1):
                    if not any(n in line for n in needles):
                        continue
                    if line_exempt(line, markers):
                        continue
                    unmarked.append((i, line.strip()))
                if not unmarked:
                    continue
                if marked:
                    history[rel] = history.get(rel, 0) + len(unmarked)
                else:
                    first = unmarked[0]
                    claims[rel] = {
                        "first": first,
                        "more": len(unmarked) - 1,
                        "symbol": entry["symbol"],
                    }
    return claims, history


# ---------- 主流程 ----------

def run(registry_path, root, out):
    symbols, markers = load_registry(registry_path)
    code_root = os.path.join(root, "app", "src")

    if not symbols:
        out.append("REGISTRY-EMPTY 登记表为空：未登记任何已删除符号，本次不扫描（不报 PASS，防静默失效）。")
        return 0, 0, 0

    stale_registry = 0
    stale_claims = 0
    history_files = 0

    for entry in symbols:
        needles = symbols_of(entry)
        residuals = entry.get("code_residual", [])

        # a) 代码侧校验：白名单外仍有命中 → REGISTRY-STALE，跳过文档提示
        registry_stale_hits = []
        for sym in needles:
            for path, lineno, text in grep_code(sym, code_root):
                rel = os.path.relpath(path, root)
                if not is_residual(rel, residuals):
                    registry_stale_hits.append((sym, rel, lineno, text.strip()))
        if registry_stale_hits:
            stale_registry += 1
            out.append(f"REGISTRY-STALE 符号 `{entry['symbol']}` 登记表过期：代码侧仍有命中（非残留白名单），"
                       f"请先核实删除是否落地，再谈文档口径；证据报告 {entry.get('report', '-')}")
            for sym, rel, lineno, text in registry_stale_hits[:10]:
                out.append(f"  CODE-HIT {rel}:{lineno} [{sym}] {text[:120]}")
            if len(registry_stale_hits) > 10:
                out.append(f"  CODE-HIT ... 其余 {len(registry_stale_hits) - 10} 条从略")
            continue

        roots = [os.path.join(root, r) for r in entry.get("scan_roots", ["docs/"])]
        claims, history = scan_docs(entry, roots, markers)

        for rel, info in sorted(claims.items()):
            lineno, text = info["first"]
            stale_claims += 1
            out.append(f"STALE-CLAIM {rel}:{lineno} [{info['symbol']}] {text[:140]}")
            if info["more"] > 0:
                out.append(f"  +{info['more']} more in same file（同文件封顶，未逐条展开）")
        for rel, count in sorted(history.items()):
            history_files += 1
            out.append(f"HISTORY-MARKED {rel}：{count} 处历史提及已被头部失效标记覆盖，保留为历史记录，勿据以施工。")

    out.append(f"SUMMARY registry-stale={stale_registry} stale-claim={stale_claims} history-marked-files={history_files} "
               f"registry-size={len(symbols)}（audit 级，退出码恒 0）")
    return stale_registry, stale_claims, history_files


# ---------- selftest ----------

def _write(fp, text):
    os.makedirs(os.path.dirname(fp), exist_ok=True)
    with open(fp, "w", encoding="utf-8") as fh:
        fh.write(text)


def selftest():
    """三组核心夹具 + 一组空登记表夹具；末行 PASS 3/3。夹具自删。"""
    tmp = tempfile.mkdtemp(prefix="stale-claims-selftest-")
    results = []
    try:
        # 夹具 1：代码零命中 + 文档含符号无标记 → 应出 STALE-CLAIM
        root1 = os.path.join(tmp, "case1")
        _write(os.path.join(root1, "app/src/main/kotlin/Thing.kt"), "class Thing\n")
        _write(os.path.join(root1, "docs/a.md"), "# 旧文档\n\nReaderProvider 还在对外互通。\n")
        reg1 = os.path.join(root1, "data_scripts/stale-symbols.json")
        _write(reg1, json.dumps({"meta": {"marker_words": DEFAULT_MARKERS}, "symbols": [
            {"symbol": "ReaderProvider", "aliases": [], "removed_at": "WORKTREE-T1",
             "report": "docs/x.md", "scan_roots": ["docs/"], "code_residual": []}
        ]}, ensure_ascii=False))
        out1 = []
        run(reg1, root1, out1)
        ok1 = any(l.startswith("STALE-CLAIM") for l in out1) and not any(l.startswith("REGISTRY-STALE") for l in out1)
        results.append(("code-zero/doc-unmarked -> STALE-CLAIM", ok1, out1))

        # 夹具 2：代码白名单外仍有命中 → 应出 REGISTRY-STALE，不报文档
        root2 = os.path.join(tmp, "case2")
        _write(os.path.join(root2, "app/src/main/kotlin/Live.kt"), "class ReaderProvider : ContentProvider()\n")
        _write(os.path.join(root2, "docs/b.md"), "ReaderProvider 旧口径。\n")
        reg2 = os.path.join(root2, "data_scripts/stale-symbols.json")
        _write(reg2, json.dumps({"meta": {"marker_words": DEFAULT_MARKERS}, "symbols": [
            {"symbol": "ReaderProvider", "aliases": [], "removed_at": "WORKTREE-T2",
             "report": "docs/x.md", "scan_roots": ["docs/"], "code_residual": []}
        ]}, ensure_ascii=False))
        out2 = []
        run(reg2, root2, out2)
        ok2 = any(l.startswith("REGISTRY-STALE") for l in out2) and not any(l.startswith("STALE-CLAIM") for l in out2)
        results.append(("code-alive -> REGISTRY-STALE", ok2, out2))

        # 夹具 3：文档同行带标记词 → 不提示
        root3 = os.path.join(tmp, "case3")
        _write(os.path.join(root3, "app/src/main/kotlin/Thing.kt"), "class Thing\n")
        _write(os.path.join(root3, "docs/c.md"), "# 存档\n\nReaderProvider 已清除，本段为历史记录。\n")
        reg3 = os.path.join(root3, "data_scripts/stale-symbols.json")
        _write(reg3, json.dumps({"meta": {"marker_words": DEFAULT_MARKERS}, "symbols": [
            {"symbol": "ReaderProvider", "aliases": [], "removed_at": "WORKTREE-T3",
             "report": "docs/x.md", "scan_roots": ["docs/"], "code_residual": []}
        ]}, ensure_ascii=False))
        out3 = []
        run(reg3, root3, out3)
        ok3 = not any(l.startswith("STALE-CLAIM") for l in out3)
        results.append(("same-line marker -> exempt", ok3, out3))

        # 辅助夹具 4：空登记表 → INFO，不报 PASS（防静默失效）
        root4 = os.path.join(tmp, "case4")
        _write(os.path.join(root4, "docs/d.md"), "nothing\n")
        reg4 = os.path.join(root4, "data_scripts/stale-symbols.json")
        _write(reg4, json.dumps({"meta": {"marker_words": DEFAULT_MARKERS}, "symbols": []}, ensure_ascii=False))
        out4 = []
        run(reg4, root4, out4)
        ok4 = any("REGISTRY-EMPTY" in l for l in out4) and not any(l.startswith("PASS") for l in out4)
        results.append(("empty registry -> INFO only", ok4, out4))

        for name, ok, out in results:
            print(f"[{'OK' if ok else 'FAIL'}] {name}")
            if not ok:
                for l in out:
                    print("   ", l)

        passed = sum(1 for _, ok, _ in results[:3] if ok)
        aux_ok = all(ok for _, ok, _ in results[3:])
        if passed == 3 and aux_ok:
            print(f"PASS 3/3 stale-claims selftest（辅助：空登记表 INFO 不报 PASS）")
            return 0
        print(f"FAIL {passed}/3 stale-claims selftest")
        return 1
    finally:
        subprocess.run(["rm", "-rf", tmp])


def main():
    ap = argparse.ArgumentParser(description="陈旧口径 audit 门禁")
    ap.add_argument("--selftest", action="store_true", help="/tmp 夹具自测")
    ap.add_argument("--root", default=ROOT, help="仓库根（自测用）")
    ap.add_argument("--registry", default=REGISTRY, help="登记表路径")
    args = ap.parse_args()

    if args.selftest:
        return selftest()

    out = []
    run(args.registry, args.root, out)
    for line in out:
        print(line)
    return 0  # audit 级：恒不拦 push


if __name__ == "__main__":
    sys.exit(main())
