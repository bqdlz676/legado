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

Q-227 基线内容指纹收口（对照 Q-222 盲区一/二）：
  ① 基线键由"路径-only"改为 `路径 + 正文 sha256[:12]`：在册文件内容被改坏即指纹不符、不再豁免、必须重判；
     基线结构为 `[{"path":..., "sha":...}]`，旧字符串数组由 `_normalize_baseline` 自动迁移、sha 按当前内容补齐；
  ② 条目计数正则已含 `*`/`+` 星号加号列表，补夹具确认 `* **问题** —— 已修` 加粗星号行算 1 条。

用法：
  python3 data_scripts/check-problem-finding.py            # 扫 origin/main..HEAD 新增 + 工作区未提交的报告
  python3 data_scripts/check-problem-finding.py <file...>  # 只判指定文件
  python3 data_scripts/check-problem-finding.py --selftest # 双向自测
"""
import hashlib
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


def _sha_bytes(data: bytes) -> str:
    """正文指纹：sha256 前 12 位；锚内容不锚行号，改一字即变。"""
    return hashlib.sha256(data).hexdigest()[:12]


def _normalize_baseline(raw, root=ROOT):
    """归一化基线为 `{路径: sha256[:12]}`。

    兼容两种结构：新结构 `[{"path":..., "sha":...}]`；旧结构为路径字符串数组，
    自动迁移——sha 按当前文件内容补齐（migrated=True）。幽灵路径（文件不存在）剔除并列出。
    返回 (entries, ghosts, migrated)。
    """
    entries, ghosts, migrated = {}, [], False
    for item in raw or []:
        if isinstance(item, str):
            migrated = True
            rp, sha = item, None
        elif isinstance(item, dict):
            rp, sha = item.get("path"), item.get("sha", "")
        else:
            continue
        if not rp:
            continue
        p = root / rp
        if not p.is_file():
            ghosts.append(rp)
            continue
        entries[rp] = sha if sha is not None else _sha_bytes(p.read_bytes())
    return entries, sorted(ghosts), migrated


def _baseline_exempt(rel: str, cur_sha: str, entries: dict) -> bool:
    """豁免双条件：路径在册 且 正文指纹一致；内容被改坏则指纹不符，必须重判。"""
    return rel in entries and entries[rel] == cur_sha


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

            # Q-227 盲区一：旧字符串数组自动迁移补指纹；幽灵路径剔除；指纹一致才豁免、改坏即失效。
            entries, ghosts, migrated = _normalize_baseline(
                ['docs/keep.md', 'docs/ghost-不存在.md'], root=pathlib.Path(d))
            keep_bytes = (pathlib.Path(d) / 'docs' / 'keep.md').read_bytes()
            if not migrated or ghosts != ['docs/ghost-不存在.md']:
                print(f'  SELFTEST FAIL 旧基线迁移：entries={entries} ghosts={ghosts} migrated={migrated}')
                ok = False
            if entries.get('docs/keep.md') != _sha_bytes(keep_bytes):
                print('  SELFTEST FAIL 迁移指纹未按当前正文补齐')
                ok = False
            if not _baseline_exempt('docs/keep.md', _sha_bytes(keep_bytes), entries):
                print('  SELFTEST FAIL 指纹一致应豁免')
                ok = False
            if _baseline_exempt('docs/keep.md', '0' * 12, entries):
                print('  SELFTEST FAIL 指纹不符不应豁免（内容改坏必须重判）')
                ok = False

            # Q-227 盲区二：加粗星号条目 `* **问题** —— 已修` 计 1 条并判 PASS。
            star_text = '# 修复-T\n\n## 新发现问题\n* **问题** —— 已修\n'
            star_ok, star_why = judge(star_text)
            if not star_ok or '1 条' not in star_why:
                print(f'  SELFTEST FAIL 星号加粗条目未计 1 条：{star_why}')
                ok = False

            print('SELFTEST: ' + ('PASS（好样本 1 通过，坏样本 2 判红；相对路径/R 行/基线迁移指纹/星号条目夹具 OK）' if ok else 'FAIL'))
            return 0 if ok else 1
    targets = [_resolve_target(a) for a in argv if a.endswith('.md')] or report_paths()
    base_raw = json.loads(BASELINE.read_text(encoding='utf-8')) if BASELINE.is_file() else []
    base, ghosts, migrated = _normalize_baseline(base_raw)
    if ghosts:
        print(f'INFO 基线幽灵路径 {len(ghosts)} 条已自动剔除（只许缩小）：' + '、'.join(h.split("/")[-1] for h in ghosts))
    if migrated:
        print('INFO 基线为旧字符串数组，已按当前正文自动补齐内容指纹（内存迁移，未回写文件）')
    if not targets:
        # 空集不许判 PASS（门禁 ㊾）：无报告可判=没东西被审，不是"合规"。
        print('EMPTY: 本轮无新增报告可判（空集不判 PASS）')
        return 2
    fails = []
    rehashed = []
    for p in targets:
        try:
            data = p.read_bytes()
            txt = data.decode(encoding='utf-8')
        except (OSError, UnicodeDecodeError) as e:
            fails.append(f'{_rel(p)}：不可读（{type(e).__name__}）')
            continue
        ok, why = judge(txt)
        rel = _rel(p)
        cur_sha = _sha_bytes(data)
        if not ok and _baseline_exempt(rel, cur_sha, base):
            continue
        if not ok and rel in base:
            # 在册但正文指纹不符：内容被改坏，豁免失效，必须重判。
            rehashed.append(rel)
        if not ok:
            fails.append(f'{_rel(p)}：{why}')
    if rehashed:
        print('INFO 基线在册但内容已变、豁免已失效，转入重判：' + '、'.join(r.split("/")[-1] for r in rehashed))
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
