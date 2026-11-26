#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 55 永远找问题（F8 机检）——只汇报"已完成"=把审计停在自己手里。

为什么需要它：用户三次追问「继续找机制的问题」「你踏马没找全」「永远找问题解决问题提出问题呢」，
说明"找问题"写在文档里靠自觉必然漂。判据必须落在报告正文上。
判据（三条不成立即红）：
  ① 报告内有「新发现问题」小节（标题去掉章节编号后，标题前部为"新发现问题"；
     标题正文里引用该词不算节，如「样本 a：『新发现问题』…」）；
  ② 该节至少 1 条带列表前缀的问题（`- `/`* `/`+ `/`1. `/`1- `/`B1- `/`| `）；
  ③ 逐条判定：每一条正文自身必须含处置状态词（已修／已入队／判废／待裁／已闭环／已处置），
     任一条缺状态词即红——不再允许一条状态词给全节空条目兜底（Q-257 收 Q-256 实测漏洞 a）。
空旷豁免：写明"已穷举"且列出面清单（"穷举面：" 后非空）即 PASS，禁"未发现问题"一句话了事。

历史口径（Q-262）：判红只落在规则生效之后进入仓库的报告上。
  分界提交 = F8 落地提交 2d5dca65e2f43f9d5a79834109d169e527e1442a（ct 1795685975，
  2026-11-26 17:39:35 +0800，见 `git log --oneline -- docs/落文-规矩总纲.md`）。
  首现 committer time 早于该提交的报告属规则前存量，整包 F8 判据豁免（历史豁免，不要求「新发现问题」节）；
  首现时间不可判（未跟踪 / git 不可用 / R100 改名链断）与分界之后的报告一律按上面三条现行判据，一条不放宽。
  基线（路径+sha256[:12] 双匹配）只兜时间不可判 / R100 改名两类边界，不再为规则前存量批量洗白。

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

# F8 规则落地提交（`git log --oneline -- docs/落文-规矩总纲.md` 定位的含 F8 那条）。
# 为什么锚 committer time（%ct）：author time 可被提交者本地时钟漂移 / rebase 改写，
# committer time 记录对象入库时刻；门禁要判的是"报告在规则生效后进入仓库"这一事实。
F8_COMMIT = '2d5dca65e2f43f9d5a79834109d169e527e1442a'
# 快照回落值：2026-11-26 17:39:35 +0800；git 不可用或提交找不到时才用它，正常启动时用 git 复核。
F8_COMMIT_CT = 1795685975


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


def _f8_boundary_ct() -> int:
    """取 F8 落地提交的 committer time 作分界；git 不可用 / 提交缺失时回落快照常量。"""
    try:
        r = subprocess.run(['git', 'show', '-s', '--format=%ct', F8_COMMIT],
                           cwd=ROOT, capture_output=True, text=True)
        out = [l for l in r.stdout.strip().splitlines() if l.isdigit()]
        if out:
            return int(out[0])
    except Exception:
        pass
    return F8_COMMIT_CT


def _bulk_add_ct() -> dict:
    """一次性扫全史 A 事件：路径 -> 最早一次 add 的 committer time。
    为什么批量：逐文件 `git log` 在 280 份报告上要拉起数百次子进程，门禁耗时不可接受；
    必须带 `-c core.quotepath=false`，否则中文路径被转成八进制转义串，键匹配不上真实路径。"""
    first: dict[str, int] = {}
    try:
        r = subprocess.run(['git', '-c', 'core.quotepath=false', 'log',
                            '--diff-filter=A', '--format=%ct', '--name-only'],
                           cwd=ROOT, capture_output=True, text=True)
        cur = None
        for line in r.stdout.splitlines():
            if line.isdigit():
                cur = int(line)
            elif line.strip() and cur is not None:
                p = line.strip().strip('"').strip("'")
                if p not in first or cur < first[p]:
                    first[p] = cur
    except Exception:
        pass
    return first


def _first_add_ct(rel: str, bulk: dict) -> int | None:
    """报告首现 committer time。批量表先查；未命中（R100 改名链 / 删后重建）再 `--follow` 单查。
    返回 None = 时间不可判（未跟踪、git 不可用、改名链断），交基线兜底，判定时默认从严。"""
    if rel in bulk:
        return bulk[rel]
    try:
        r = subprocess.run(['git', '-c', 'core.quotepath=false', 'log', '--follow',
                            '--diff-filter=A', '--format=%ct', '-1', '--', rel],
                           cwd=ROOT, capture_output=True, text=True)
        out = [l for l in r.stdout.strip().splitlines() if l.isdigit()]
        return int(out[0]) if out else None
    except Exception:
        return None


def _time_bucket(rel: str, bulk: dict, boundary: int) -> tuple[str, int | None]:
    """分桶：pre=分界前首现（历史豁免）；post=分界时及之后首现（现行判据）；unknown=时间不可判（从严+基线兜底）。
    边界相等（首现就在 F8 落地提交本身）归 post：规则随该提交入库，同批产物按现行判据要求。"""
    ct = _first_add_ct(rel, bulk)
    if ct is None:
        return 'unknown', None
    return ('pre' if ct < boundary else 'post'), ct


def _decide(rel: str, txt: str, data: bytes, base: dict, bulk: dict, boundary: int) -> tuple[str, str]:
    """单份报告时间口径判定。返回 (tag, why)：
      hist  规则前首现，历史豁免；
      base  时间不可判 / 改名边界，基线路径+sha 双匹配豁免；
      pass  现行判据通过；
      rehash 在册但正文指纹不符（豁免失效，仍按 fail 处理，由调用方单列提示）；
      fail  现行判据不通过。"""
    bucket, ct = _time_bucket(rel, bulk, boundary)
    if bucket == 'pre':
        return 'hist', f'历史豁免：首现 ct={ct} 早于 F8 分界 ct={boundary}'
    ok, why = judge(txt)
    sha = _sha_bytes(data)
    if not ok and _baseline_exempt(rel, sha, base):
        return 'base', why
    if not ok:
        return ('rehash' if rel in base else 'fail'), why
    return 'pass', why


def report_paths() -> list[pathlib.Path]:
    out = []
    try:
        r = subprocess.run(['git', '-c', 'core.quotepath=false', 'rev-list', '--objects', 'origin/main..HEAD'],
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
    # 回退窗口：工作区已全部提交并推送后，"origin/main..HEAD + 工作树"会空集，
    # 而空集按 ㊾ 不许判 PASS，会让每次 push 之后 55 必红（假红比漏检更贵：会把真红淹没）。
    # 故空集时从最近 30 条提交里回溯"最近一次真的新增/改动报告"的那一棒，覆盖面只增不减。
    if not out:
        try:
            c = subprocess.run(['git', '-c', 'core.quotepath=false', 'log', '-30', '--format=%H', '--', 'docs/'],
                               cwd=ROOT, capture_output=True, text=True)
            for sha in [s.strip() for s in c.stdout.splitlines() if s.strip()]:
                r3 = subprocess.run(['git', '-c', 'core.quotepath=false', 'show', '--pretty=format:', '--name-only', sha],
                                    cwd=ROOT, capture_output=True, text=True)
                hits = [n.strip() for n in r3.stdout.splitlines()
                        if NAME_RE.match(n.strip()) and (ROOT / n.strip()).is_file()]
                if hits:
                    out.extend(ROOT / n for n in hits)
                    break
        except Exception:
            pass
    return sorted(set(out))


# 条目起始前缀：无序列表、有序 `1.`、`1-`、缺陷号前缀 `B1-`/`Q257-`、表格行。
ITEM_START = re.compile(r'^\s*(?:[-*+]|\d+\.|\d+-|[A-Za-z]\d+(?:R\d+)?-|\|)\s+')
# 章节编号前缀：`一、` `1.` `1）` 等，剥离后再判标题主体。
SECTION_NOISE = re.compile(r'^[一二三四五六七八九十百\d]+[、.）):]\s*')


def _section_title(line: str) -> str:
    """去掉 `## ` 与章节编号，返回标题主体文本。"""
    s = re.sub(r'^#+\s*', '', line).strip()
    return SECTION_NOISE.sub('', s).strip()


def _find_section(text: str):
    """定位「新发现问题」节标题；标题正文引用该词（词在引号/句中）不算节。"""
    for m in re.finditer(r'(?m)^#+\s*.*$', text):
        title = _section_title(m.group(0))
        idx = title.find('新发现问题')
        if idx == 0 or (0 < idx <= 6 and title[:idx].rstrip('：: 「『(') == ''):
            return m
    return None


def _split_items(body: str) -> list[str]:
    """把节正文按条目起始前缀切分；续行（缩进行/非空前缀行）并入当前条目。"""
    items: list[str] = []
    cur: str | None = None
    for line in body.splitlines():
        if ITEM_START.match(line):
            if cur is not None:
                items.append(cur)
            cur = line
        elif cur is not None and line.strip():
            cur += '\n' + line
    if cur is not None:
        items.append(cur)
    return items


def judge(text: str) -> tuple[bool, str]:
    m = _find_section(text)
    if not m:
        if any(k in text for k in EMPTY_OK):
            return True, '空旷豁免：已穷举面清单'
        return False, '缺「新发现问题」小节'
    sec = text[m.end():]
    nxt = re.search(r'(?m)^#+\s', sec)
    body = sec[:nxt.start()] if nxt else sec
    items = _split_items(body)
    if len(items) < 1:
        if any(k in body for k in EMPTY_OK):
            return True, '空旷豁免：已穷举面清单'
        return False, '「新发现问题」节内 0 条'
    missing = [it for it in items if not any(w in it for w in STATE_WORDS)]
    if missing:
        first = missing[0].splitlines()[0].strip()[:40]
        return False, f'{len(missing)}/{len(items)} 条缺处置状态（如：{first}…）'
    return True, f'问题 {len(items)} 条，逐条含处置状态'


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

            # Q-257 逐条判定三夹具：全条有状态 PASS；一条兜底其余空 FAIL；节内 0 条 FAIL。
            all_state = '# 修复-U\n\n## 新发现问题\n1. 甲问题 —— 已修\n2. 乙问题 —— 待裁\n'
            if not judge(all_state)[0]:
                print('  SELFTEST FAIL 全条带状态样本应 PASS：', judge(all_state)[1])
                ok = False
            carry_one = '# 修复-V\n\n## 新发现问题\n1. 甲问题 —— 待裁\n2. 乙问题无状态词\n'
            carry_ok, carry_why = judge(carry_one)
            if carry_ok or '1/2' not in carry_why:
                print(f'  SELFTEST FAIL 单条兜底样本应逐条判红：{carry_ok} {carry_why}')
                ok = False
            empty_sec = '# 修复-W\n\n## 新发现问题\n（本节未列条目）\n'
            if judge(empty_sec)[0]:
                print('  SELFTEST FAIL 节内 0 条应判红')
                ok = False

            # Q-262 历史口径四夹具：规则后缺节红；规则前存量缺节走历史豁免；规则后条目无状态词红；unknown+基线双匹配豁免。
            boundary = F8_COMMIT_CT
            bulk = {
                'docs/after-new.md': boundary + 3600,    # 规则落地后首现
                'docs/legacy-old.md': boundary - 3600,   # 规则落地前首现
                'docs/after-item.md': boundary + 3600,   # 规则落地后首现
            }
            fx = {
                'docs/after-new.md': bad,
                'docs/legacy-old.md': bad,
                'docs/after-item.md': bad2,
                'docs/unknown-rename.md': bad,
            }
            fx_base = {'docs/unknown-rename.md': _sha_bytes(fx['docs/unknown-rename.md'].encode('utf-8'))}
            tags: dict[str, str] = {}
            for rel, content in fx.items():
                tags[rel], _ = _decide(rel, content, content.encode('utf-8'), fx_base, bulk, boundary)
            if tags['docs/after-new.md'] != 'fail':
                print(f"  SELFTEST FAIL 规则后新报告缺节应判红：{tags['docs/after-new.md']}")
                ok = False
            if tags['docs/legacy-old.md'] != 'hist':
                print(f"  SELFTEST FAIL 规则前存量缺节应历史豁免：{tags['docs/legacy-old.md']}")
                ok = False
            if tags['docs/after-item.md'] != 'fail':
                print(f"  SELFTEST FAIL 规则后条目无状态词应判红：{tags['docs/after-item.md']}")
                ok = False
            if tags['docs/unknown-rename.md'] != 'base':
                print(f"  SELFTEST FAIL 时间不可判 + 基线双匹配应豁免：{tags['docs/unknown-rename.md']}")
                ok = False
            hist_n = sum(1 for t in tags.values() if t == 'hist')
            if hist_n != 1:
                print(f'  SELFTEST FAIL 历史豁免计数应为 1，实得 {hist_n}')
                ok = False

            print('SELFTEST: ' + ('PASS（好样本 1 通过，坏样本 2 判红；相对路径/R 行/基线迁移指纹/星号条目/逐条判定/历史口径夹具 OK）' if ok else 'FAIL'))
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
    bulk = _bulk_add_ct()
    boundary = _f8_boundary_ct()
    hist: list[str] = []       # 规则前首现，历史豁免
    base_ex: list[str] = []    # 时间不可判 / R100 改名，基线双匹配豁免
    passed: list[str] = []     # 现行判据通过
    fails: list[str] = []
    rehashed: list[str] = []
    for p in targets:
        try:
            data = p.read_bytes()
            txt = data.decode(encoding='utf-8')
        except (OSError, UnicodeDecodeError) as e:
            fails.append(f'{_rel(p)}：不可读（{type(e).__name__}）')
            continue
        rel = _rel(p)
        tag, why = _decide(rel, txt, data, base, bulk, boundary)
        if tag == 'hist':
            hist.append(rel)
        elif tag == 'base':
            base_ex.append(rel)
        elif tag == 'rehash':
            # 在册但正文指纹不符：内容被改坏，豁免失效，必须重判。
            rehashed.append(rel)
            fails.append(f'{rel}：{why}')
        elif tag == 'fail':
            fails.append(f'{rel}：{why}')
        else:
            passed.append(rel)
    if hist:
        print(f'INFO 历史豁免 {len(hist)} 份（F8 落地前首现，不要求「新发现问题」节）：'
              + '、'.join(h.split("/")[-1] for h in hist))
    if base_ex:
        print(f'INFO 基线豁免 {len(base_ex)} 份（首现时间不可判 / R100 改名边界，路径+sha 双匹配）：'
              + '、'.join(b.split("/")[-1] for b in base_ex))
    if rehashed:
        print('INFO 基线在册但内容已变、豁免已失效，转入重判：' + '、'.join(r.split("/")[-1] for r in rehashed))
    if fails:
        print(f'PROBLEM-FINDING: FAIL（判红 {len(fails)} 份；现行通过 {len(passed)} 份 / 历史豁免 {len(hist)} 份 / 基线豁免 {len(base_ex)} 份）')
        for f in fails:
            print(f'  - {f}')
        return 1
    print(f'PROBLEM-FINDING: PASS（现行通过 {len(passed)} 份 / 历史豁免 {len(hist)} 份 / 基线豁免 {len(base_ex)} 份 / 判红 0 份）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
