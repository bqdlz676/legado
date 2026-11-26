#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 55 永远找问题（F8 机检）——只汇报"已完成"=把审计停在自己手里。

为什么需要它：用户三次追问「继续找机制的问题」「你踏马没找全」「永远找问题解决问题提出问题呢」，
说明"找问题"写在文档里靠自觉必然漂。判据必须落在报告正文上。
判据（三选一不成立即红）：
  ① 报告内有「新发现问题」小节（标题含"新发现问题"）；
  ② 该节至少 1 条带编号的问题；
  ③ 每条带处置状态词（已修／已入队／判废／待裁／已闭环）。
空旷豁免：写明"已穷举"且列出面清单（"穷举面：" 后非空）即 PASS，禁"未发现问题"一句话了事。

Q-221 后三轮缺陷收口（Q-223）：
  ① 命令行传相对路径时 `relative_to(ROOT)` 抛 ValueError 崩溃——路径先 resolve，落盘打印失败则原样输出；
  ② `git status` 重命名行 `R  old -> new` 原按 `line[3:]` 取到旧路径，改按 `->` 右侧取；
  ③ 基线里已不存在的历史路径属幽灵条目，打印 INFO 并在本轮判定集中自动剔除（基线只许缩小）。

用法：
  python3 data_scripts/check-problem-finding.py            # 扫 origin/main..HEAD 新增 + 工作区未提交的报告
  python3 data_scripts/check-problem-finding.py <file...>  # 只判指定文件
  python3 data_scripts/check-problem-finding.py --selftest # 双向自测
"""
import json
import re
import subprocess
import sys
import tempfile
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
NAME_RE = re.compile(r'docs/(修复|验收|诊断|分析|门禁全量|CI核验|复扫|复盘)-[^/]*\.md$')
STATE_WORDS = ('已修', '已入队', '判废', '待裁', '已闭环', '已处置')
BASELINE = pathlib.Path(__file__).resolve().parent / 'problem-finding-baseline.json'
EMPTY_OK = ('穷举面', '已穷举')


def _resolve_target(arg: str) -> pathlib.Path:
    """命令行传入路径先 resolve 成绝对路径；相对路径也能正常 read，不再靠 relative_to 反推。"""
    return pathlib.Path(arg).resolve()


def _rel(p: pathlib.Path) -> str:
    """相对 ROOT 打印；不在 ROOT 子树（外部绝对路径）时原样返回字符串，不抛 ValueError。"""
    try:
        return str(p.relative_to(ROOT))
    except ValueError:
        return str(p)


def _status_path(line: str) -> str:
    """解析 porcelain 行路径。重命名行 `R  old -> new` 取箭头右侧新路径；
    其余状态取第 3 列后内容；去掉 porcelain 给特殊字符路径加的成对引号。"""
    body = line[3:].strip() if len(line) > 3 else ''
    if ' -> ' in body:
        body = body.split(' -> ', 1)[1].strip()
    return body.strip('"').strip("'")


def _prune_baseline(base: set, root=ROOT):
    """剔除基线里已不存在的幽灵路径（只许缩小）。返回 (保留排序表, 幽灵排序表)。"""
    ghosts = sorted(e for e in base if not (root / e).is_file())
    kept = sorted(e for e in base if (root / e).is_file())
    return kept, ghosts


def report_paths() -> list[pathlib.Path]:
    out = []
    try:
        r = subprocess.run(['git', 'rev-list', '--objects', 'origin/main..HEAD'],
                           cwd=ROOT, capture_output=True, text=True)
        for line in r.stdout.splitlines():
            parts = line.split(' ', 1)
            if len(parts) == 2 and NAME_RE.match(parts[1]):
                p = ROOT / parts[1]
                if p.is_file():
                    out.append(p)
    except Exception:
        pass
    r2 = subprocess.run(['git', '-c', 'core.quotepath=false', 'status', '--porcelain', '-uall'], cwd=ROOT, capture_output=True, text=True)
    for line in r2.stdout.splitlines():
        p = _status_path(line)
        if NAME_RE.match(p) and (ROOT / p).is_file():
            out.append(ROOT / p)
    return sorted(set(out))


def judge(text: str) -> tuple[bool, str]:
    m = re.search(r'(?m)^#+\s*.*新发现问题.*$', text)
    if not m:
        if any(k in text for k in EMPTY_OK):
            return True, '空旷豁免：已穷举面清单'
        return False, '缺「新发现问题」小节'
    sec = text[m.end():]
    nxt = re.search(r'(?m)^#+\s', sec)
    body = sec[:nxt.start()] if nxt else sec
    items = re.findall(r'(?m)^\s*(?:-|\*|\+|\d+\.|\|)\s*', body)
    if len(items) < 1:
        if any(k in body for k in EMPTY_OK):
            return True, '空旷豁免：已穷举面清单'
        return False, '「新发现问题」节内 0 条'
    if not any(w in body for w in STATE_WORDS):
        return False, '问题无处置状态（已修/已入队/判废/待裁/已闭环）'
    return True, f'问题 {len(items)} 条，含处置状态'


def main(argv: list[str]) -> int:
    if '--selftest' in argv:
        good = '# 修复-X\n\n## 新发现问题\n1. 门禁 52 从未接线 —— 已修（rule-gate.sh:480）\n'
        bad = '# 修复-Y\n\n## 结论\n一切正常，未发现问题。\n'
        bad2 = '# 修复-Z\n\n## 新发现问题\n1. 某个问题\n'
        with tempfile.TemporaryDirectory() as d:
            ok = True
            for name, content, want in [('a.md', good, True), ('b.md', bad, False), ('c.md', bad2, False)]:
                p = pathlib.Path(d) / name
                p.write_text(content, encoding='utf-8')
                got, why = judge(p.read_text(encoding='utf-8'))
                if got != want:
                    print(f'  SELFTEST FAIL {name}: expected {want} got {got} ({why})')
                    ok = False

            # 缺陷①：相对路径参数先 resolve，read 不崩、_rel 兜底不抛 ValueError。
            rel_p = pathlib.Path(d) / 'docs' / 'keep.md'
            rel_p.parent.mkdir(parents=True, exist_ok=True)
            rel_p.write_text(good, encoding='utf-8')
            resolved = _resolve_target(str(rel_p))
            if not resolved.is_absolute() or not resolved.is_file():
                print('  SELFTEST FAIL 相对路径未 resolve 成可读绝对路径')
                ok = False
            if judge(resolved.read_text(encoding='utf-8'))[0] is not True:
                print('  SELFTEST FAIL 相对路径夹具未判 PASS')
                ok = False
            try:
                _rel(resolved)
            except ValueError:
                print('  SELFTEST FAIL _rel 仍抛 ValueError')
                ok = False

            # 缺陷②：porcelain 重命名行取 `->` 右侧；普通状态行取第 3 列；引号被剥掉。
            r_new = _status_path('R  docs/old-名字.md -> docs/new-名字.md')
            if r_new != 'docs/new-名字.md':
                print(f'  SELFTEST FAIL R 行解析：{r_new}')
                ok = False
            if _status_path('?? docs/a.md') != 'docs/a.md':
                print('  SELFTEST FAIL 未跟踪行解析')
                ok = False
            if _status_path(' M "docs/quoted b.md"') != 'docs/quoted b.md':
                print('  SELFTEST FAIL 引号路径剥离')
                ok = False

            # 缺陷③：基线幽灵路径本轮自动剔除，现存条目保留。
            kept, ghosts = _prune_baseline({'docs/keep.md', 'docs/ghost-不存在.md'}, root=pathlib.Path(d))
            if kept != ['docs/keep.md'] or ghosts != ['docs/ghost-不存在.md']:
                print(f'  SELFTEST FAIL 基线剔除：kept={kept} ghosts={ghosts}')
                ok = False

            print('SELFTEST: ' + ('PASS（好样本 1 通过，坏样本 2 判红；相对路径/R 行/基线剔除三夹具 OK）' if ok else 'FAIL'))
            return 0 if ok else 1
    targets = [_resolve_target(a) for a in argv if a.endswith('.md')] or report_paths()
    base_raw = set(json.loads(BASELINE.read_text(encoding='utf-8'))) if BASELINE.is_file() else set()
    base, ghosts = _prune_baseline(base_raw)
    if ghosts:
        print(f'INFO 基线幽灵路径 {len(ghosts)} 条已自动剔除（只许缩小）：' + '、'.join(h.split("/")[-1] for h in ghosts))
    if not targets:
        # 空集不许判 PASS（门禁 ㊾）：无报告可判=没东西被审，不是"合规"。
        print('EMPTY: 本轮无新增报告可判（空集不判 PASS）')
        return 2
    fails = []
    for p in targets:
        try:
            txt = p.read_text(encoding='utf-8')
        except (OSError, UnicodeDecodeError) as e:
            fails.append(f'{_rel(p)}：不可读（{type(e).__name__}）')
            continue
        ok, why = judge(txt)
        rel = _rel(p)
        if not ok and rel in base:
            continue
        if not ok:
            fails.append(f'{_rel(p)}：{why}')
    if fails:
        print('PROBLEM-FINDING: FAIL')
        for f in fails:
            print(f'  - {f}')
        return 1
    hist = sorted(base)
    if hist:
        print(f'INFO 历史欠账 {len(hist)} 份走基线（只许缩小）：' + '、'.join(h.split("/")[-1] for h in hist))
    print(f'PROBLEM-FINDING: PASS（{len(targets)} 份报告均带新发现问题与处置状态）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
