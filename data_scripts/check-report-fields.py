#!/usr/bin/env python3
"""报告字段门禁（把 L1「改坏推演」从口号变成可机检，2026-11-26）。

背景：pyramid 实测 211 份修复/验收报告里只有 17 份写了改坏推演或自查反例。
L1 是返工的最大来源，却完全没有机检——于是"要有自查反例"只是我在派单时口头强调。

判据（只查本轮新增/改动的报告，历史留档不追改，避免一次判红 200 份）：
  A 首行锚点卡（`锚点卡：docs/锚点/...md`），且该文件真实存在。
  B 有结论标记（总结论/PASS/有条件通过/FAIL 任一）。
  C 修复类报告必须有「自查反例」或「改坏」节（验收类必须有「改坏」推演段）。
  D 至少 5 处 `文件:行` 形式的证据锚点（防止空口结论）。

用法：python3 data_scripts/check-report-fields.py [报告路径...]
  不给参数则自动取 `git status` 里 docs/(修复|验收)-*.md 的改动/新增文件。
退出码：任一不符 = 1；空集（无待检报告）= 2（空集不判 PASS，同 ⑲/55 口径）。
"""

import io
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def only_backfill(path):
    """本轮改动是否为"纯回执补录/留档标记"：只有新增行、且新增行全是回执留痕。

    为什么放行：Q-199/200 这类存量补录会触碰 87 份老报告头部，老报告成文早于
    锚点卡/证据三件套规则，拿新规回头判死它们等于让补录永远推不上去。判据要严：
    必须是 +N/-0（纯插入）且每行都含「回执补录」或「回执缺失（规则上线前交付」。
    """
    out = subprocess.run(["git", "diff", "--numstat", "--", path], cwd=ROOT,
                         capture_output=True, text=True,
                         env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}).stdout.strip()
    if not out:
        return False
    added, deleted = out.split("\t")[:2]
    if deleted != "0":
        return False
    diff = subprocess.run(["git", "diff", "-U0", "--", path], cwd=ROOT,
                          capture_output=True, text=True,
                          env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}).stdout
    ins = [l[1:].strip() for l in diff.splitlines() if l.startswith("+") and not l.startswith("+++")]
    ins = [l for l in ins if l]
    return bool(ins) and all(("回执补录" in l) or ("回执缺失" in l and "留档" in l) for l in ins)


def is_fixture(path):
    # 回归夹具（data_scripts/fixtures/ 下、.fixture 后缀）是测试资产，不按真报告判（2026-11-26 追加）。
    return "data_scripts/fixtures" in path or path.endswith(".fixture")


def is_anchor_card(path):
    # docs/锚点/ 下是前置设计卡，本身不是报告；聚合前缀正则本就取不到，显式直传时同样跳过，
    # 不按报告四件套判（口径同 check-anchor-card.classify 的 other 分支）。
    norm = path.replace("\\", "/")
    return "/锚点/" in norm or norm.startswith("docs/锚点/")


def _porcelain_path(line: str) -> str:
    """解析 `git status --porcelain` 行的路径。

    为什么：① 重命名行是 `R  old -> new`，按行首切片会取到旧路径，必须取箭头右侧新路径；
    ② porcelain 对含空格/特殊字符的路径会加成对双引号，非 ASCII 路径在未放开 quotepath 时
       还会被转成八进制串；引号剥净后再交上层做前缀正则匹配。
    """
    body = line[3:].strip() if len(line) > 3 else ""
    if " -> " in body:
        body = body.split(" -> ", 1)[1].strip()
    return body.strip('"').strip("'")


def changed_reports():
    # 必须带 `-c core.quotepath=false`：git 默认把中文路径转义成八进制串（如 `"docs/\344\276..."`），
    # 前缀正则匹配不到真实路径，聚合会把中文未跟踪/修改报告整批漏掉，误报"本轮无报告改动"假绿。
    # `-uall`：未跟踪目录默认会被折叠成 `?? docs/`，文件级条目取不到；逐文件列出才能做前缀匹配（同 55/g65 口径）。
    out = subprocess.run(["git", "-c", "core.quotepath=false", "status", "--porcelain", "-uall"],
                         cwd=ROOT,
                         capture_output=True, text=True,
                         env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}).stdout
    paths = []
    for line in out.splitlines():
        p = _porcelain_path(line)
        # 路径归一：反斜杠转正斜杠、规范化 `.`/`..` 段后再判前缀；归一前后都不改动判据本身。
        p = os.path.normpath(p.replace("\\", "/")) if p else p
        if is_fixture(p):
            continue
        if re.match(r"docs/(修复|验收|组装|分析)-.*\.md$", p) and not only_backfill(p):
            paths.append(p)
    return paths


def check(path):
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        return [f"{path}: 文件不存在"]
    body = open(full, encoding="utf-8", errors="ignore").read()
    fails = []
    head = body.splitlines()[:3]
    anchor = next((l for l in head if l.strip().startswith("锚点卡：")), None)
    if not anchor:
        fails.append("A 首行缺锚点卡")
    else:
        m = re.search(r"docs/锚点/[^\s`]+\.md", anchor)
        if not m or not os.path.isfile(os.path.join(ROOT, m.group(0))):
            fails.append("A 锚点卡指向的文件不存在")
    if not re.search(r"(总结论|结论)[：:]|PASS|有条件通过|FAIL", body):
        fails.append("B 缺结论标记")
    is_fix = os.path.basename(path).startswith(("修复-", "组装-"))
    if is_fix and "自查反例" not in body and "改坏" not in body:
        fails.append("C 修复/组装类报告缺「自查反例」或「改坏」节")
    if not is_fix and "改坏" not in body and "推演" not in body:
        fails.append("C 验收类报告缺「改坏推演」段")
    anchors = len(re.findall(r"[\w./]+\.(?:kt|py|sh|xml|md):\d+", body))
    if anchors < 5:
        fails.append(f"D 证据锚点仅 {anchors} 处（<5）")
    return fails


def selftest() -> int:
    """双向自测：四件套齐全的修复类报告 → check() 返回空列表 PASS；缺锚点卡/结论/反例/锚点 → FAIL。
    另含聚合口径夹具（临时迷你 git 仓）：中文/英文未跟踪报告被 changed_reports() 识别、非报告文件不混入、
    显式路径直判、空集报 EMPTY 退出码 2。做法：临时目录接管 ROOT 后直接调生产 check()/main()，
    A/B/C/D 判据全走原函数，不另写字段比对。"""
    saved_root = ROOT

    good = tempfile.mkdtemp(prefix='report-fields-selftest-')
    try:
        gdocs = os.path.join(good, 'docs', '锚点')
        os.makedirs(gdocs, exist_ok=True)
        open(os.path.join(gdocs, 'selftest-good.md'), 'w', encoding='utf-8').write('# 锚点卡\n')
        anchors = '\n'.join([
            'data_scripts/check-no-idle.py:23',
            'data_scripts/check-queue-running.py:15',
            'data_scripts/check-dispatch-queue.py:35',
            'data_scripts/check-report-fields.py:69',
            'data_scripts/check-onboarding.py:77',
        ])
        body = ("锚点卡：docs/锚点/selftest-good.md\n\n"
                "总结论：PASS\n\n"
                "## 自查反例\n反例推演：若放宽锚点计数，空口结论会蒙混过关。\n\n"
                "证据锚点：\n" + anchors + "\n")
        rel_good = 'docs/修复-selftest-good.md'
        open(os.path.join(good, rel_good), 'w', encoding='utf-8').write(body)
        globals()['ROOT'] = good
        fails_good = check(rel_good)
        assert fails_good == [], f"四件套齐全的报告应 PASS，fails={fails_good}"
        print('[selftest] 好样本（锚点卡+结论+反例+5 处锚点）PASS 通路 OK')
    finally:
        globals()['ROOT'] = saved_root
        shutil.rmtree(good, ignore_errors=True)

    bad = tempfile.mkdtemp(prefix='report-fields-selftest-')
    try:
        os.makedirs(os.path.join(bad, 'docs'), exist_ok=True)
        rel_bad = 'docs/修复-selftest-bad.md'
        # 坏样本：首行无锚点卡、无结论标记、无「自查反例/改坏」、0 处文件:行锚点，A/B/C/D 全中
        open(os.path.join(bad, rel_bad), 'w', encoding='utf-8').write(
            '# 修复-selftest-bad\n只有一段正文，缺锚点卡、缺结论、缺反例推演、缺证据锚点。\n')
        globals()['ROOT'] = bad
        fails_bad = check(rel_bad)
        assert fails_bad, f"字段全缺的报告应 FAIL，fails={fails_bad}"
        print('[selftest] 坏样本（四件套全缺）FAIL 通路 OK：' + '；'.join(fails_bad))
    finally:
        globals()['ROOT'] = saved_root
        shutil.rmtree(bad, ignore_errors=True)

    # 解析单元：重命名行取箭头右侧、成对引号剥离、普通状态行原样（不依赖 git 仓，纯函数断言）。
    assert _porcelain_path('R  docs/old-名字.md -> docs/new-名字.md') == 'docs/new-名字.md'
    assert _porcelain_path('?? "docs/quoted b.md"') == 'docs/quoted b.md'
    assert _porcelain_path(' M docs/修复-x.md') == 'docs/修复-x.md'
    print('[selftest] 路径解析单元：重命名右侧 / 引号剥离 / 普通行 OK')

    # 聚合口径夹具：临时迷你 git 仓。验 quotepath 放开后中文未跟踪报告与英文路径报告都能被
    # changed_reports() 识别；非报告文件不混入；显式路径绕开聚合直判；空仓聚合为空集并报 EMPTY。
    repo = tempfile.mkdtemp(prefix='report-fields-selftest-repo-')
    try:
        subprocess.run(['git', 'init', '-q'], cwd=repo, capture_output=True, text=True)
        subprocess.run(['git', 'config', 'user.email', 'selftest@example.com'], cwd=repo, capture_output=True)
        subprocess.run(['git', 'config', 'user.name', 'selftest'], cwd=repo, capture_output=True)
        os.makedirs(os.path.join(repo, 'docs'), exist_ok=True)
        os.makedirs(os.path.join(repo, 'data_scripts'), exist_ok=True)
        cn = 'docs/修复-中文闸quotepath夹具.md'
        en = 'docs/修复-selftest-en.md'
        other = 'docs/机制-说明.md'
        code = 'data_scripts/foo.py'
        for rel in (cn, en, other, code):
            open(os.path.join(repo, rel), 'w', encoding='utf-8').write('# 夹具\n')
        globals()['ROOT'] = repo
        found = changed_reports()
        assert set(found) == {cn, en}, f"聚合应识别中文+英文两份报告，实得：{found}"
        print('[selftest] 聚合口径：中文未跟踪报告、英文路径报告均识别 OK；非报告文件未混入')

        # 显式路径调用：绕开聚合直接判传入路径（放行通路，行为不变）。英文路径报告补齐四件套后应 PASS。
        gdocs = os.path.join(repo, 'docs', '锚点')
        os.makedirs(gdocs, exist_ok=True)
        open(os.path.join(gdocs, 'selftest-en.md'), 'w', encoding='utf-8').write('# 锚点卡\n')
        anchors = '\n'.join([
            'data_scripts/check-no-idle.py:23',
            'data_scripts/check-queue-running.py:15',
            'data_scripts/check-dispatch-queue.py:35',
            'data_scripts/check-report-fields.py:69',
            'data_scripts/check-onboarding.py:77',
        ])
        en_body = ("锚点卡：docs/锚点/selftest-en.md\n\n"
                   "总结论：PASS\n\n"
                   "## 自查反例\n反例推演：若路径不归一，转义串会漏判。\n\n"
                   "证据锚点：\n" + anchors + "\n")
        open(os.path.join(repo, en), 'w', encoding='utf-8').write(en_body)
        saved_argv = sys.argv
        sys.argv = ['check-report-fields.py', en]
        try:
            rc_explicit = main()
        finally:
            sys.argv = saved_argv
        assert rc_explicit == 0, f"显式路径传入合规报告应 PASS，rc={rc_explicit}"
        print('[selftest] 显式路径调用通路 OK（绕开聚合，直接判传入文件）')
    finally:
        globals()['ROOT'] = saved_root
        shutil.rmtree(repo, ignore_errors=True)

    empty = tempfile.mkdtemp(prefix='report-fields-selftest-empty-')
    try:
        subprocess.run(['git', 'init', '-q'], cwd=empty, capture_output=True, text=True)
        globals()['ROOT'] = empty
        assert changed_reports() == [], "空仓聚合应返回空集"
        saved_argv = sys.argv
        sys.argv = ['check-report-fields.py']
        buf = io.StringIO()
        old_stdout = sys.stdout
        sys.stdout = buf
        try:
            rc_empty = main()
        finally:
            sys.stdout = old_stdout
            sys.argv = saved_argv
        assert rc_empty == 2, f"空集应退出码 2，实得 {rc_empty}"
        assert 'EMPTY' in buf.getvalue(), f"空集应报 EMPTY 不判 PASS，实得：{buf.getvalue()!r}"
        print('[selftest] 空集通路 OK：报 EMPTY、退出码 2，不判 PASS')
    finally:
        globals()['ROOT'] = saved_root
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


def main():
    if '--selftest' in sys.argv:
        return selftest()
    paths = sys.argv[1:] or changed_reports()
    if not paths:
        # 空集不判 PASS（㊾）：没检出待检报告＝没有可核验对象，不是"合规"；同 ⑲/55 口径报 EMPTY，退出码 2。
        print("EMPTY: 未提供待检报告（空集不判 PASS；聚合口径走 changed_reports）")
        return 2
    bad = 0
    for p in paths:
        if is_fixture(p) or is_anchor_card(p):
            continue
        fails = check(p)
        if fails:
            bad += 1
            print(f"FAIL {p}")
            for f in fails:
                print(f"    {f}")
        else:
            print(f"PASS {p}")
    print(f"REPORT-FIELDS: {'PASS' if bad == 0 else 'FAIL'}（{len(paths)} 份，{bad} 份不合格）")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
