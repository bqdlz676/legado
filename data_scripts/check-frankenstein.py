#!/usr/bin/env python3
"""门禁 ⑳ 缝合怪清理守卫（F-86，2026-11-24「把清理缝合怪的工作也要写进铁律机制」）。

为什么立这道闸：缝合怪（同一能力拼两套实现/技术栈，或规格要 A 代码是 B）是
CI 红灯、口径漂移、返工的三重源头；它还让"规格逐字对照"失效——代码里根本
没有规格那套实现，验收无从回源。能机械判的形态在这里拦，判不了的（形态 2/5）
由台账人工定级，本闸反查台账是否在册。

扫描范围（硬边界）：`app/src/main/java`、`app/src/main/res`；`app/src/test/**`
属截图基建代理域，不扫；`docs/`、`data_scripts/` 不在本闸范围。

五类判据：
  [1] 双轨并存：同能力新旧两套实现都在主源码树（包对表 + 关键词对表）。
  [2] 兼容层残留：`*Compat/*Legacy/*Old/*V2/*_old*` 命名且主链路有调用点。
  [3] 职责混装（仅提示，不单独 FAIL）：单文件行数超阈值。
      阈值依据（2026-11-24 普查 1368 个 kt 文件）：p50=100、p90=430、
      p95=565、p99=1196；≥600 行（≈p95）列候选，≥1200 行（≈p99）列重点。
      行数是弱信号，跨域数定性仍需人工在台账定级。
  [4] @Deprecated 且主源码仍有调用点；框架 API 自带的
      `Deprecated in Java/Drawable` 弃用注解不算本形态。
  [5] 半成品分支：`wip/*` 分支存在且台账未登记。

豁免反查（防自己给自己豁免）：命中条目必须能在 `docs/台账-缝合怪.md` 找到
对应行（位置路径子串匹配）；台账标"留档/已清"的条目豁免 FAIL，"待处理/
处理中"记为在册债务仍列出；代码注释自报豁免而台账无条目者，判 FAIL。

退出码：全部 PASS=0；任一硬 FAIL=1。`--selftest` 造夹具跑通 FAIL/PASS 两条
通路后自删夹具，不污染仓库。
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_DIR = os.path.join("app", "src", "main", "java")
RES_DIR = os.path.join("app", "src", "main", "res")
LEDGER = os.path.join("docs", "台账-缝合怪.md")

# 行数阈值（依据文件头普查分位数；样本变化后须重测再调，不许拍脑袋改）
LINE_WARN = 600    # ≈p95=565：候选级
LINE_HARD = 1200   # ≈p99=1196：重点级

# [1] 包对表：旧包 vs 新包，两侧目录均非空即判双轨
PKG_PAIRS = [
    ("推演引擎双轨（help/sim 旧引擎 vs crossing/engine 新引擎）",
     os.path.join("help", "sim"),
     os.path.join("ui", "map", "crossing", "engine")),
]

# [1] 关键词对表：旧侧任一词命中 + 路径落在能力域 scope 内才算"同能力"；
#     新侧词只用于标注新轨是否已存在。scope 必带，否则通用 WebView 栈（浏览器/
#     登录/RSS/书源引擎）会被误判成关系图双轨——那是 Legado 保留的解析/容器层。
KEYWORD_PAIRS = [
    {"label": "关系图 cytoscape 力导向 vs 自绘 Canvas",
     "old": ["cytoscape"],
     "new": ["Canvas"],
     "scope": r"relation|graph|person|Relation"},
]

# [2] 兼容层命名模式
COMPAT_RE = re.compile(r"(Compat|Legacy|Old|V2)\.kt$|_old", re.IGNORECASE)

# [4] 框架 API 弃用注解（不算内部兼容层残留）
FRAMEWORK_DEPRECATED_RE = re.compile(r"Deprecated in (Java|Drawable|API|Android)")

# 代码注释里自报豁免的写法（必须能在台账找到对应条目）
INLINE_EXEMPT_RE = re.compile(r"缝合怪豁免|留档豁免|frankenstein[ _-]?exempt", re.IGNORECASE)

DECL_RE = re.compile(r"^\s*(?:override\s+)?(?:fun|val|object|class|interface)\s+([A-Za-z_]\w*)")


def rel(path: str, root: str = ROOT) -> str:
    return os.path.relpath(path, root)


def list_source_files(root: str):
    """枚举扫描范围内全部源码/资源文件。"""
    out = []
    for base in (JAVA_DIR, RES_DIR):
        base_abs = os.path.join(root, base)
        if not os.path.isdir(base_abs):
            continue
        for dirpath, _, names in os.walk(base_abs):
            for n in names:
                if n.endswith((".kt", ".xml")):
                    out.append(os.path.join(dirpath, n))
    return out


def read_text(path: str) -> str:
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def load_ledger(root: str):
    """读台账在册行；返回 (行文本列表, 台账全文)。"""
    ledger_abs = os.path.join(root, LEDGER)
    if not os.path.isfile(ledger_abs):
        return [], ""
    text = read_text(ledger_abs)
    rows = [ln for ln in text.splitlines() if ln.strip().startswith("| F-86-")]
    return rows, text


def exempted(rows, hit_rel: str) -> bool:
    """命中路径是否被台账在册行覆盖（路径子串匹配；留档/已清/待处理均算在册）。"""
    base = os.path.basename(hit_rel)
    for row in rows:
        if hit_rel in row or base in row:
            return True
    return False


def check_dual_track(files, root):
    """[1] 双轨并存：返回命中列表 [(rel_path, 说明, 首行行号)]。"""
    hits = []
    text_by_file = {p: read_text(p) for p in files}

    for label, old_pkg, new_pkg in PKG_PAIRS:
        old_files = [p for p in files if old_pkg.replace("/", os.sep) in p]
        new_files = [p for p in files if new_pkg.replace("/", os.sep) in p]
        if old_files and new_files:
            for p in old_files:
                hits.append((rel(p, root), label, 1))

    for pair in KEYWORD_PAIRS:
        scope = re.compile(pair["scope"], re.IGNORECASE)
        old_set, new_set = set(), set()
        for p, t in text_by_file.items():
            if not scope.search(p):
                continue
            if any(term in t for term in pair["old"]):
                old_set.add(p)
            if any(term in t for term in pair["new"]):
                new_set.add(p)
        note = pair["label"] + ("；新轨已存在" if new_set else "；新轨未见（规格偏离候选）")
        for p in sorted(old_set):
            line_no = next((i + 1 for i, ln in enumerate(text_by_file[p].splitlines())
                            if any(term in ln for term in pair["old"])), 1)
            hits.append((rel(p, root), note, line_no))
    return hits


def check_compat(files, root):
    """[2] 兼容层类：命名模式 + 自身文件之外有调用点。"""
    hits = []
    kt_files = [p for p in files if p.endswith(".kt")]
    all_text = {p: read_text(p) for p in kt_files}
    for p in kt_files:
        name = os.path.basename(p)
        if not COMPAT_RE.search(name):
            continue
        symbol = name[:-3]  # 去 .kt
        caller = None
        for other, text in all_text.items():
            if other == p:
                continue
            if re.search(r"\b" + re.escape(symbol) + r"\b", text):
                caller = other
                break
        if caller:
            hits.append((rel(p, root), f"兼容层类 `{symbol}`，调用点 {rel(caller, root)}", 1))
    return hits


def check_oversized(files, root):
    """[3] 职责混装（提示级）：返回 (warn 列表, hard 列表)，元素 (rel, 行数)。"""
    warn, hard = [], []
    for p in files:
        if not p.endswith(".kt"):
            continue
        n = len(read_text(p).splitlines())
        if n >= LINE_HARD:
            hard.append((rel(p, root), n))
        elif n >= LINE_WARN:
            warn.append((rel(p, root), n))
    return warn, hard


def check_deprecated(files, root):
    """[4] @Deprecated 且主源码仍有外部调用点；框架 API 弃用排除。"""
    hits = []
    kt_files = [p for p in files if p.endswith(".kt")]
    all_text = {p: read_text(p) for p in kt_files}
    for p in kt_files:
        lines = all_text[p].splitlines()
        i = 0
        while i < len(lines):
            if "@Deprecated" not in lines[i]:
                i += 1
                continue
            # 收集注解块（多行参数）与消息串
            block = "\n".join(lines[i:i + 4])
            if FRAMEWORK_DEPRECATED_RE.search(block):
                i += 1
                continue
            m_name = None
            for ln in lines[i:i + 6]:
                md = DECL_RE.match(ln)
                if md:
                    m_name = md.group(1)
                    break
            if not m_name:
                i += 1
                continue
            caller = None
            for other, text in all_text.items():
                if other == p:
                    continue
                if re.search(r"\b" + re.escape(m_name) + r"\s*\(", text):
                    caller = other
                    break
            if caller:
                hits.append((rel(p, root), f"@Deprecated `{m_name}` 仍被 {rel(caller, root)} 调用", i + 1))
            i += 1
    return hits


def list_wip_branches(root: str):
    """[5] 列未合并的 wip/* 分支；沙箱外调用 git，失败返回空列表（不阻断）。"""
    try:
        out = subprocess.run(
            ["git", "-C", root, "branch", "--list", "wip/*"],
            capture_output=True, text=True, check=True)
        return [ln.strip().lstrip("* ").strip() for ln in out.stdout.splitlines() if ln.strip()]
    except Exception:
        return []


def check_wip(branches, ledger_text: str):
    """[5] 列全部 wip 分支；返回 (全部, 未在册)，未在册才判 FAIL。"""
    all_hits, unregistered = [], []
    for br in branches:
        if br in ledger_text:
            all_hits.append((br, "在册（留档/已清）", 0, True))
        else:
            all_hits.append((br, "半成品 wip 分支未在台账登记", 0, False))
            unregistered.append((br, "半成品 wip 分支未在台账登记", 0))
    return all_hits, unregistered


def check_inline_exempt(files, rows):
    """代码注释自报豁免但台账无条目 → FAIL（防自己给自己豁免）。"""
    hits = []
    for p in files:
        for i, ln in enumerate(read_text(p).splitlines(), 1):
            if INLINE_EXEMPT_RE.search(ln) and not exempted(rows, rel(p, root)):
                hits.append((rel(p, root), "注释自报豁免但台账无对应条目", i))
    return hits


def run(root: str, wip_branches=None):
    files = list_source_files(root)
    rows, ledger_text = load_ledger(root)

    dual = check_dual_track(files, root)
    compat = check_compat(files, root)
    oversized_warn, oversized_hard = check_oversized(files, root)
    deprecated = check_deprecated(files, root)
    branches = wip_branches if wip_branches is not None else list_wip_branches(root)
    wip_all, fail_wip = check_wip(branches, ledger_text)
    inline = check_inline_exempt(files, rows)

    # 硬 FAIL 集合：未在台账在册的双轨/兼容层/废弃调用/半成品分支/自报豁免
    def hard(hits):
        out = []
        for h in hits:
            if not exempted(rows, h[0]):
                out.append(h)
        return out

    fail_dual, fail_compat, fail_dep, fail_inline = (
        hard(dual), hard(compat), hard(deprecated), inline)

    print("⑳ 缝合怪守卫（F-86）")
    print(f"[1] 双轨并存: {len(dual)} 命中（在册 {len(dual) - len(fail_dual)}，未在册 {len(fail_dual)}）")
    for path, note, ln in dual:
        tag = "在册" if exempted(rows, path) else "未在册"
        print(f"    {path}:{ln} [{tag}] {note}")
    print(f"[2] 兼容层残留: {len(compat)} 命中（在册 {len(compat) - len(fail_compat)}，未在册 {len(fail_compat)}）")
    for path, note, ln in compat:
        tag = "在册" if exempted(rows, path) else "未在册"
        print(f"    {path}:{ln} [{tag}] {note}")
    print(f"[3] 职责混装(提示,不单独FAIL): ≥{LINE_WARN} 行 {len(oversized_warn)}，≥{LINE_HARD} 行 {len(oversized_hard)}")
    for path, n in oversized_hard:
        print(f"    {path} ({n} 行) [重点]")
    for path, n in oversized_warn:
        print(f"    {path} ({n} 行) [候选]")
    print(f"[4] @Deprecated 仍被调用: {len(deprecated)} 命中（在册 {len(deprecated) - len(fail_dep)}，未在册 {len(fail_dep)}）")
    for path, note, ln in deprecated:
        tag = "在册" if exempted(rows, path) else "未在册"
        print(f"    {path}:{ln} [{tag}] {note}")
    print(f"[5] 半成品 wip 分支: {len(wip_all)} 分支（未在册 {len(fail_wip)}）")
    for path, note, ln, registered in wip_all:
        tag = "在册" if registered else "未在册"
        print(f"    {path} [{tag}] {note}")
    if inline:
        print(f"[+] 自报豁免无台账: {len(inline)}")
        for path, note, ln in inline:
            print(f"    {path}:{ln} {note}")

    failures = fail_dual + fail_compat + fail_dep + fail_wip + fail_inline
    if failures:
        print(f"FRANKENSTEIN: FAIL（{len(failures)} 处未在册缝合怪，先登记台账或按四步工序清理）")
        return 1
    print("FRANKENSTEIN: PASS")
    return 0


def selftest():
    """造夹具跑 FAIL 通路与 PASS 通路，结束后自删。"""
    import shutil
    tmp = tempfile.mkdtemp(prefix="frankenstein-selftest-")
    try:
        # ---- FAIL 通路：五类硬命中 + 空台账 ----
        app_java = os.path.join(tmp, JAVA_DIR, "io", "legado", "app")
        os.makedirs(os.path.join(app_java, "oldpkg"))
        os.makedirs(os.path.join(app_java, "newpkg"))
        os.makedirs(os.path.join(tmp, "docs"))
        # 双轨：旧侧 cytoscape/WebView，新侧 Canvas
        with open(os.path.join(app_java, "oldpkg", "OldGraph.kt"), "w") as f:
            f.write("import android.webkit.WebView\n// cytoscape 力导向\nclass OldGraph { val wv = WebView(null) }\n")
        with open(os.path.join(app_java, "newpkg", "NewGraph.kt"), "w") as f:
            f.write("class NewGraph { fun draw(c: Canvas) {} }\n")
        # 兼容层 + 调用点
        with open(os.path.join(app_java, "OldCompat.kt"), "w") as f:
            f.write("object OldCompat { fun doIt() = 1 }\n")
        with open(os.path.join(app_java, "Caller.kt"), "w") as f:
            f.write("class Caller { fun x() = OldCompat.doIt() }\n")
        # 超大文件（[3] 提示级）
        with open(os.path.join(app_java, "Big.kt"), "w") as f:
            f.write("class Big\n" + "\n".join("    val p%d = %d" % (i, i) for i in range(LINE_HARD + 10)))
        # Deprecated 仍被调用
        with open(os.path.join(app_java, "Legacy.kt"), "w") as f:
            f.write('@Deprecated("旧版弃用", ReplaceWith("newThing()"))\nfun oldThing() = 1\n')
        with open(os.path.join(app_java, "LegacyCaller.kt"), "w") as f:
            f.write("class L { fun x() = oldThing() }\n")
        # 空台账
        open(os.path.join(tmp, LEDGER), "w").close()

        rc_fail = run(tmp, wip_branches=["wip/selftest-unregistered"])
        if rc_fail != 1:
            print("SELFTEST FAIL：空台账下应判 FAIL")
            return 1

        # ---- PASS 通路：台账逐条豁免 ----
        ledger = os.path.join(tmp, LEDGER)
        with open(ledger, "w") as f:
            f.write(
                "| 编号 | 位置 | 形态 | 成分 | 风险 | 处置 | 状态 |\n"
                "|---|---|---|---|---|---|---|\n"
                "| F-86-T1 | `oldpkg/OldGraph.kt` | 形态1 | 双轨 | 风险 | 留+理由：测试夹具 | 留档 |\n"
                "| F-86-T2 | `OldCompat.kt` | 形态3 | 兼容层 | 风险 | 留+理由：测试夹具 | 留档 |\n"
                "| F-86-T3 | `Legacy.kt` | 形态6 | 废弃调用 | 风险 | 留+理由：测试夹具 | 留档 |\n"
                "| F-86-T4 | `wip/selftest-unregistered` | 形态6 | 半成品分支 | 风险 | 留+理由：测试夹具 | 留档 |\n"
            )
        rc_pass = run(tmp, wip_branches=["wip/selftest-unregistered"])
        if rc_pass != 0:
            print("SELFTEST FAIL：台账豁免后应判 PASS")
            return 1

        print("SELFTEST: PASS（FAIL/PASS 两条通路均符合预期，夹具已自删）")
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--selftest", action="store_true", help="造夹具自测两条通路后自删")
    args = ap.parse_args()
    if args.selftest:
        sys.exit(selftest())
    sys.exit(run(ROOT))


if __name__ == "__main__":
    main()
