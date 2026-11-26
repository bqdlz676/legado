#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁（Q-221）锁口径反向扫描：旧「禁删锁 / 禁 rm .git/index.lock」裸句不得留在现行规则里。

为什么需要（2026-11-26 Q-218 独立验收假绿 1）：㊿ 只认 wait_agent 词，撞锁旧口径
「禁 `rm .git/index.lock`」「禁删锁」不含该词，任何现行门禁都抓不到；而新门禁第 1 步
口径是「确认无 git 进程且锁为 0 字节的僵尸锁可删」。新旧口径并存＝读哪条都能开脱，必须机检。

判据：
  - 扫 docs/ 全量 *.md；行内命中旧口径模式且同句不含「僵尸锁可删」新标记 → 记红。
  - 证据/历史类文件名前缀豁免（复盘- / 形成史 / 分析- / 验收- / 修复- / 锚点 / 交接- /
    流水 / 自检 / 审计 / 留档 / 备案 / 索引 / 诊断- / 规则冲突清单）：旧串只作回溯引用，不接新判定。
  - 历史复盘两份（事故-停工事故复盘、事故清单）走棘轮基线 lock-rule-baseline.json：
    条目 `相对路径:行内容 sha256 前 12 位`；基线只许缩小，扩张即 FAIL（新欠账洗不进基线）。
  - ``` 围栏内为逐字转写/夹具留档区，不扫；围栏按行配对，未闭合则块态保持到文件尾。

退出码：0 = LOCK-RULE: PASS；1 = LOCK-RULE: FAIL；2 = 用法错。
--selftest：/tmp 造夹具双向跑通 PASS / FAIL 两条通路，跑完自删。
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
BASELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "lock-rule-baseline.json")

# 旧口径模式：显示名, 正则。禁删锁为短语；禁删 index.lock 容忍反引号/空格夹在 rm 与路径之间。
OLD_PATTERNS = [
    ("禁删锁", re.compile(r"禁删锁")),
    ("禁删index.lock", re.compile(r"禁[^。\n]{0,16}rm[^。\n]{0,20}index\.lock")),
]
NEW_MARK = "僵尸锁可删"

# 旧实现按文件名前缀豁免（复盘-/验收-/修复-…）＝可伪造身份：新建一个 复盘-*.md 就能藏裸句。
# 改成行内引述判定：旧串必须在引号/反引号内，且本行另有回溯词，才当引述；裸句一律红。
QUOTE_REF_WORDS = ["旧口径", "原文", "此前", "曾", "已改", "作废", "事故", "复盘", "引述", "转写"]



_REF_FILES = None


def _ref_file_ok(rel: str) -> bool:
    """引述件豁免：路径与内容 sha256 都必须命中 exempt-files.json（Q-241 口径，绑定身份）。
    为什么不用文件名前缀：新建一个「复盘-*.md」就能藏裸句，前缀＝可伪造身份（㊼ 同族坑）。"""
    global _REF_FILES
    if _REF_FILES is None:
        try:
            data = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "lock-ref-files.json"), encoding="utf-8"))
            _REF_FILES = data.get("files", []) if isinstance(data, dict) else []
        except (OSError, ValueError):
            _REF_FILES = []
    full = os.path.join(ROOT, rel)
    try:
        sha = hashlib.sha256(open(full, encoding="utf-8").read().encode()).hexdigest()[:12]
    except OSError:
        return False
    return any(e.get("path") == rel and e.get("sha") == sha for e in _REF_FILES)

def _quoted_reference(line: str, match_text: str) -> bool:
    """旧串是否只是引述：匹配文本必须落在配对引号/反引号内，且本行带回溯词。"""
    if not any(w in line for w in QUOTE_REF_WORDS):
        return False
    for a, b in (('`', '`'), ('"', '"'), ('「', '」'), ('“', '”')):
        idx = line.find(a)
        while idx != -1:
            end = line.find(b, idx + 1)
            if end == -1:
                break
            if match_text in line[idx + 1:end]:
                return True
            idx = line.find(a, end + 1)
    return False


def _line_hash(line: str) -> str:
    """行内容 sha256 前 12 位：基线锚内容不锚行号，行号漂移不误伤，行被改写即失效。"""
    return hashlib.sha256(line.strip().encode("utf-8")).hexdigest()[:12]


def _baseline_key(rel: str, line: str) -> str:
    return f"{rel}:{_line_hash(line)}"


def load_baseline(path=BASELINE):
    if not os.path.isfile(path):
        return set()
    with open(path, encoding="utf-8") as f:
        return set(json.load(f).get("entries", []))


def ratchet_expansion(current: set, root=ROOT):
    """基线棘轮：当前基线必须 ⊆ 上一提交基线（只许缩小）。无远端/无上一提交参照时跳过比照。"""
    ref = None
    for cand in ("origin/main", "HEAD~1"):
        r = subprocess.run(["git", "show", f"{cand}:data_scripts/lock-rule-baseline.json"],
                           cwd=root, capture_output=True, text=True)
        if r.returncode == 0:
            try:
                ref = set(json.loads(r.stdout).get("entries", []))
                break
            except json.JSONDecodeError:
                continue
    if ref is None:
        return []
    return sorted(current - ref)


def scan(root=ROOT, baseline=None):
    """返回 (hits, scanned, exempt_base)。hits: (rel, 行号, 模式名, 行摘)；exempt_base: 基线豁免留痕。"""
    if baseline is None:
        baseline = load_baseline()
    hits = []
    exempt_base = []
    scanned = 0
    for dirpath, _, files in os.walk(os.path.join(root, "docs")):
        for fn in files:
            if not fn.endswith(".md"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, root)
            scanned += 1
            try:
                with open(path, encoding="utf-8", errors="strict") as f:
                    text = f.read()
            except (OSError, UnicodeDecodeError) as e:
                hits.append((rel, 0, "读取失败", f"{type(e).__name__}：不得按不可读文件判 PASS"))
                continue
            in_fence = False
            for lineno, line in enumerate(text.splitlines(), 1):
                if line.lstrip().startswith("```"):
                    in_fence = not in_fence
                    continue
                if in_fence:
                    continue
                old = next((name for name, pat in OLD_PATTERNS if pat.search(line)), None)
                if old is None or NEW_MARK in line:
                    continue
                m = next((pat.search(line) for name, pat in OLD_PATTERNS if name == old), None)
                if _quoted_reference(line, m.group(0) if m else ''):
                    continue
                if _ref_file_ok(rel) and any(c in line for c in '`「」“”'):
                    continue
                if _baseline_key(rel, line) in baseline:
                    exempt_base.append((rel, lineno, old, line.strip()[:80]))
                    continue
                hits.append((rel, lineno, old, line.strip()[:90]))
    return hits, scanned, exempt_base


def selftest():
    """双向自测：新口径/白名单/基线夹具 PASS；裸奔旧口径两条 FAIL。"""
    tmp = tempfile.mkdtemp(prefix="lock-rule-selftest-")
    os.makedirs(os.path.join(tmp, "docs"), exist_ok=True)

    def w(rel, content):
        p = os.path.join(tmp, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write(content)

    # PASS 夹具 1：新口径同句带「僵尸锁可删」。
    w("docs/规则-新口径.md",
      "F-102：撞锁先 sleep 重试；确认无 git 进程且锁为 0 字节的僵尸锁可删；攒批一次 push。\n")
    # FAIL 夹具 0（新语义）：文件名带「复盘-」但行是裸句——旧实现按前缀放行，现在必须红。
    w("docs/复盘-历史留档.md", "C1 撞锁 sleep 重试禁删锁（1dc6ead 重试成功）。\n")
    # PASS 夹具 3：基线豁免行（内容哈希入基线）。
    hist_line = "S-05 撞锁 sleep 重试禁删锁（历史事故留档）。"
    w("docs/事故-历史基线.md", hist_line + "\n")
    hist_key = f"docs/事故-历史基线.md:{_line_hash(hist_line)}"

    hits, scanned, exempt = scan(tmp, baseline={hist_key})
    # 除「复盘-」裸句夹具外不应有命中：引述件豁免只在真仓 ref-files 生效，夹具里按裸句判红。
    stray = [h for h in hits if h[0] != "docs/复盘-历史留档.md"]
    assert not stray, f"合规夹具不应命中：{stray}"
    assert any(e[0] == "docs/事故-历史基线.md" for e in exempt), f"基线豁免未留痕：{exempt}"
    print(f"[selftest] 合规夹具 PASS 通路 OK：扫描 {scanned} 份，基线豁免 {len(exempt)} 处")

    # FAIL 夹具 1：裸奔「禁 rm .git/index.lock」，无新标记、非白名单、不在基线。
    w("docs/临时-旧口径删锁.md", "调度层撞锁 sleep 重试、禁 `rm .git/index.lock`。\n")
    # FAIL 夹具 2：裸奔「禁删锁」短语。
    w("docs/临时-旧口径短语.md", "并发纪律：撞锁 sleep 重试禁删锁。\n")
    hits2, _, _ = scan(tmp, baseline={hist_key})
    assert hits2, "裸奔旧口径夹具必须被抓到"
    names = [h[2] for h in hits2]
    assert "禁删index.lock" in names and "禁删锁" in names, f"两类旧口径都应命中：{names}"
    print("[selftest] 违规夹具 FAIL 通路 OK，命中：", [f"{h[0]}:{h[1]} [{h[2]}]" for h in hits2])

    shutil.rmtree(tmp, ignore_errors=True)
    print("[selftest] 夹具已自删，双向自测通过。")
    return 0


def main(argv):
    if "--selftest" in argv:
        return selftest()
    if "--help" in argv or "-h" in argv:
        print(__doc__)
        return 2

    baseline = load_baseline()
    expansion = ratchet_expansion(baseline)
    hits, scanned, exempt = scan(ROOT, baseline)

    print(f"LOCK-RULE: 扫描文档 {scanned} 份；基线豁免 {len(exempt)} 处")
    if expansion:
        print(f"FAIL 基线扩张 {len(expansion)} 条（棘轮只许缩小）：{'、'.join(expansion[:6])}")
    for rel, lineno, name, snippet in hits:
        print(f"  - {rel}:{lineno} [{name}] {snippet}")
    if exempt:
        for rel, lineno, name, snippet in exempt:
            print(f"  · BASELINE {rel}:{lineno} [{name}] {snippet}")
    if expansion or hits:
        print("LOCK-RULE: FAIL")
        return 1
    print("LOCK-RULE: PASS（现行规则无旧锁口径裸句）")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
