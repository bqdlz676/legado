#!/usr/bin/env python3
"""验收证据工作台（2026-11-26 红率复盘产物）。

为什么要有它：113 份验收报告里 99 份写着"未本地编译"，96 份照样判 PASS——
验收判据里没有编译面，验收代理只能看文档口径。本脚本把"改动面 + 既有门禁机检
+ 编译面风险清单 + 三值建议"压成一份可直接贴进验收报告的证据块，让验收代理
不再靠嘴验。

边界（必须写进报告，不许越权承诺）：
  - 纯标准库、不联网、不编译；静态启发式只能抓"文本层面看得出的编译风险"，
    真类型推断、重载解析、泛型约束、kotlinx 协程契约这些只有编译器能判。
  - 机检 FAIL 直接判 FAIL；其余编译面风险最高只能到「有条件通过（编译面待 CI）」。

用法：
  python3 data_scripts/acceptance-evidence.py [--paths a.kt b.kt ...]
      [--rev <sha>] [--report docs/验收-xxx.md] [--json]

  --paths   显式指定本单改动文件；不给则取 git status --porcelain 全部改动。
  --rev     从指定提交取文件快照（含其父提交做 diff），用于历史单回归实测；
            快照落临时目录后再跑 lint，不碰工作区。
  --report  本单报告路径：传给 check-anchor-card.py --strict；onboarding 仍按
            其自有口径扫本轮改动报告。
  --json    输出机器可读 JSON；默认输出可贴进报告的 Markdown 块。

退出码：0 = PASS / 有条件通过；1 = 机检 FAIL。
"""
import argparse
import fnmatch
import glob
import json
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTS = os.path.join(ROOT, "data_scripts")

LINE_LIMIT = 600  # 与 kotlin-syntax-lint.py LINE_WARN / check-frankenstein.py 同口径

# Kotlin/JDK 常见内置类型：不出现在仓库声明表里也不算漏 import。
STDLIB = set('''String Int Long Float Double Boolean Char Byte Short Any Unit Nothing Array
List MutableList Map MutableMap Set MutableSet Pair Triple Sequence Iterator Collection
Exception Throwable Error RegexBuilder StringBuilder IntArray BooleanArray FloatArray ByteArray
CharArray DoubleArray LongArray ShortArray Comparable Number CharSequence Runnable
Deprecated Suppress JvmStatic JvmField JvmName JvmOverloads Volatile Synchronized
IllegalArgumentException IllegalStateException UnsupportedOperationException
NullPointerException IndexOutOfBoundsException InterruptedException ArithmeticException
Override Suppress JvmInline OptIn ExperimentalStdlibApi Class Enum Annotation'''.split())

DECL_RE = re.compile(
    r'\b(?:class|object|interface|fun|val|var|typealias|enum\s+class|annotation\s+class)\s+'
    r'([A-Z][A-Za-z0-9_]*)')
USE_RE = re.compile(r'(?<![\w.])([A-Z][A-Za-z0-9_]{2,})')
PKG_RE = re.compile(r'^package\s+([\w.]+)', re.M)
IMPORT_RE = re.compile(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?')
FUN_RE = re.compile(
    r'(?m)^[ \t]*(?:@[\w.]+(?:<[^>]*>)?[ \t]+)*'
    r'(?:(?:internal|private|public|protected|open|abstract|suspend|override|'
    r'data|sealed|operator|infix|tailrec|external|expect|actual)[ \t]+)*'
    r'fun\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*(?::\s*([^={\n]+))?')


def git(*args, cwd=ROOT):
    """只跑只读 git 查询；本脚本禁止任何 git 写操作。"""
    p = subprocess.run(['git', '-c', 'core.quotepath=false', *args],
                       cwd=cwd, capture_output=True, text=True)
    return p.returncode, p.stdout, p.stderr


def git_show(rev, path):
    rc, out, _ = git('show', '%s:%s' % (rev, path))
    return out if rc == 0 else None


def changed_status():
    """git status --porcelain → [(status, path)]，status 取两列首字符（A/M/??/D）。"""
    _, out, _ = git('status', '--porcelain')
    rows = []
    for line in out.splitlines():
        if len(line) < 4:
            continue
        status = line[:2].strip() or '?'
        path = line[3:].strip().strip('"')
        if path:
            rows.append((status[0], path))
    return rows


def rev_status(rev):
    """--rev 模式的改动集合：`git diff --name-status <rev>^ <rev>`。

    为什么必须单独实现：早前版本在 --rev 下仍调 changed_status()，
    结果 --rev 89bac0c 报的是工作区、判定"无代码改动 PASS"——
    那笔提交正是 24 条编译错的那单，等于验收工具自己假绿。
    适用条件：只读，根提交（无父）时退回 `git show --name-status`。
    """
    rc, out, err = git('diff', '--name-status', '%s^' % rev, rev)
    if rc != 0:
        rc, out, err = git('show', '--name-status', '--format=', rev)
        if rc != 0:
            return [], err
    rows = []
    for line in out.splitlines():
        parts = line.split('\t')
        if len(parts) < 2:
            continue
        status = parts[0][:1]
        path = parts[-1].strip()
        if path:
            rows.append((status, path))
    return rows, ''


def strip_noise(src):
    """去注释/字符串内容，保留换行与引号占位，避免括号/标识符误配。"""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n':
                i += 1
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            i += 2
            while i + 1 < n and not (src[i] == '*' and src[i + 1] == '/'):
                out.append('\n' if src[i] == '\n' else ' ')
                i += 1
            i += 2
        elif c == '"':
            triple = src[i:i + 3] == '"""'
            out.append('""' if not triple else '"""')
            i += 3 if triple else 1
            while i < n:
                if triple:
                    run = 0
                    while i + run < n and src[i + run] == '"':
                        run += 1
                    if run >= 3:
                        i += run
                        break
                    out.append(' ')
                    i += 1
                    continue
                if src[i] == '"':
                    i += 1
                    break
                out.append(' ')
                i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def scan_repo_maps():
    """扫工作树 app/src：typeName → package；internal/private typeName → package。

    为什么扫工作树而非 HEAD：证据包面向本轮改动，参照系是"当前仓里都声明了什么"；
    内部类型的可见性修饰符在声明处，工作树即声明处的现状。
    """
    decl_map, internal_map = {}, {}
    for f in glob.glob(os.path.join(ROOT, 'app', 'src', '**', '*.kt'), recursive=True):
        try:
            src = open(f, encoding='utf-8', errors='ignore').read()
        except OSError:
            continue
        clean = strip_noise(src)
        pm = PKG_RE.search(clean)
        pkg = pm.group(1) if pm else ''
        for name in DECL_RE.findall(clean):
            decl_map.setdefault(name, pkg)
        for m in re.finditer(
                r'\b(internal|private)\s+(?:data\s+)?(?:sealed\s+)?'
                r'(?:class|object|interface|typealias)\s+([A-Z][A-Za-z0-9_]*)', clean):
            internal_map[m.group(2)] = (pkg, m.group(1))
    return decl_map, internal_map


def parse_imports(src):
    """返回 [(line_no, full, short, pkg)]；星号导入 short='*'。"""
    out = []
    for i, line in enumerate(src.splitlines(), 1):
        t = line.strip()
        m = IMPORT_RE.match(t)
        if not m:
            continue
        full = m.group(1)
        short = m.group(2) or full.rsplit('.', 1)[-1]
        pkg = full.rsplit('.', 1)[0] if '.' in full else ''
        out.append((i, full, short, pkg))
    return out


def fun_vis_map(clean):
    """函数名 → 可见性（internal/private/public，默认 public）；供 base/current 漂移比对。"""
    lines = clean.splitlines()
    out = {}
    for m in FUN_RE.finditer(clean):
        ln = clean.count('\n', 0, m.start())
        prev = ' '.join(lines[max(0, ln - 2):ln + 1])
        if re.search(r'\binternal\b', prev):
            vis = 'internal'
        elif re.search(r'\bprivate\b', prev):
            vis = 'private'
        else:
            vis = 'public'
        out[m.group(1)] = vis
    return out


def enclosing_visibility(clean, line_idx):
    """向上找最近的类/对象声明；若其为 internal/private，成员函数按内部可见性处理。"""
    lines = clean.splitlines()
    for j in range(line_idx, -1, -1):
        m = re.search(
            r'\b(internal|private)\s+(?:data\s+)?(?:sealed\s+)?'
            r'(?:class|object|interface)\s+([A-Z]\w*)', lines[j])
        if m:
            return m.group(1)
        if re.search(r'\b(?:class|object|interface)\s+[A-Z]\w*', lines[j]):
            return 'public'
    return 'public'


def analyze_file(path, status, current, base, decl_map, internal_map):
    """单文件编译面风险推导。返回 dict（含 risks 列表）。"""
    clean = strip_noise(current)
    pm = PKG_RE.search(clean)
    own_pkg = pm.group(1) if pm else ''
    cur_imports = parse_imports(current)
    base_imports = parse_imports(base) if base else []
    base_full = {x[1] for x in base_imports}
    cur_full = {x[1] for x in cur_imports}

    risks = []
    kt = path.endswith('.kt')

    # ① 新增文件：base（HEAD 或 rev^）里不存在。
    is_new = base is None
    if is_new:
        risks.append({'category': '新增文件', 'line': 1,
                      'detail': '本文件为本轮新增（%s 中不存在同名路径）' %
                                ('HEAD' if not REV else REV + '^')})

    # ② import 改动：增/删的 import 行原样列出。
    added_imports = [x for x in cur_imports if x[1] not in base_full]
    removed_imports = [x for x in base_imports if x[1] not in cur_full]
    if added_imports:
        risks.append({'category': 'import 改动', 'line': added_imports[0][0],
                      'detail': '新增 import：' + '；'.join(x[1] for x in added_imports)})
    if removed_imports:
        risks.append({'category': 'import 改动', 'line': '-',
                      'detail': '移除 import：' + '；'.join(x[1] for x in removed_imports)})

    # ③ 跨包引用：import 包 ≠ 本文件包（信息项，供验收逐个核对符号归属）。
    cross = [x[1] for x in cur_imports if x[3] and x[3] != own_pkg]
    if cross:
        risks.append({'category': '跨包引用', 'line': '-',
                      'detail': '跨包 import %d 条：' % len(cross) + '；'.join(cross[:12])
                                + ('……' if len(cross) > 12 else '')})

    # ④ 行数对比 600 上限。
    line_count = len(current.splitlines())
    if line_count >= LINE_LIMIT:
        risks.append({'category': '行数超限', 'line': line_count,
                      'detail': '%d 行 ≥ %d 行上限（债务棘轮口径，先拆再交）'
                                % (line_count, LINE_LIMIT)})

    if not kt:
        return {'path': path, 'status': status, 'lines': line_count, 'is_new': is_new,
                'added_imports': [x[1] for x in added_imports],
                'removed_imports': [x[1] for x in removed_imports],
                'cross_package_imports': cross, 'risks': risks}

    # ⑤ public 签名暴露 internal 类型。
    cur_lines = clean.splitlines()
    base_clean = strip_noise(base) if base else ''
    base_sigs = set(re.findall(r'fun\s+(\w+)\s*\(([^)]*)\)', base_clean))
    cur_sigs = []
    declared = set(DECL_RE.findall(clean))
    imported_short = {x[2] for x in cur_imports}

    for m in FUN_RE.finditer(clean):
        ln = clean.count('\n', 0, m.start())
        prev = ' '.join(cur_lines[max(0, ln - 2):ln + 1])
        if re.search(r'\b(internal|private)\b', prev):
            continue
        if enclosing_visibility(clean, ln) in ('internal', 'private'):
            continue
        fname, params, ret = m.group(1), m.group(2), m.group(3) or ''
        types = set(re.findall(r'\b([A-Z][A-Za-z0-9_]*)\b', params))
        types |= set(re.findall(r'\b([A-Z][A-Za-z0-9_]*)\b', ret))
        exposed = sorted(t for t in types if t in internal_map)
        if exposed:
            pkg, vis = internal_map[exposed[0]]
            risks.append({'category': 'public 暴露 internal', 'line': ln + 1,
                          'detail': 'public fun %s 签名引用 %s 类型 %s（声明于 %s，%s）'
                                    % (fname, vis, '、'.join(exposed), pkg,
                                       '降 internal 或提升类型可见性')})
        cur_sigs.append((fname, params.strip()))

    # ⑥ 签名/可见性改动：参数列表或可见性与 base 不一致即标记（编译面敏感）。
    base_sig_set = {(n, p.strip()) for n, p in base_sigs}
    changed_sigs = ['%s(%s)' % (n, p) for n, p in cur_sigs if (n, p) not in base_sig_set]
    base_vis = fun_vis_map(base_clean) if base else {}
    cur_vis = fun_vis_map(clean)
    vis_drift = ['%s：%s → %s' % (n, base_vis[n], cur_vis[n])
                 for n in cur_vis if n in base_vis and base_vis[n] != cur_vis[n]]
    if base and (changed_sigs or vis_drift):
        parts = []
        if changed_sigs:
            parts.append('签名变化：' + '；'.join(changed_sigs[:8]))
        if vis_drift:
            parts.append('可见性漂移：' + '；'.join(vis_drift[:8]))
        risks.append({'category': '签名/可见性改动', 'line': '-',
                      'detail': '与 %s 相比%s' %
                                ('HEAD' if not REV else REV + '^', '；'.join(parts))})

    # ⑦ 疑似漏 import：大写符号在仓库他包有声明，本文件既未导入也未本地声明。
    used = USE_RE.findall(clean)
    missing = []
    for name in sorted(set(used)):
        if name in imported_short or name in declared or name in STDLIB:
            continue
        if name in decl_map and decl_map[name] != own_pkg:
            missing.append('%s（定义于 %s）' % (name, decl_map[name]))
    if missing:
        risks.append({'category': '疑似漏 import', 'line': '-',
                      'detail': '仓库内有出处但本文件未导入：' + '；'.join(missing[:10])})

    # ⑧ Compose 特例：用到 .dp 却未导入 unit.dp（小写扩展属性，大写扫不到）。
    if re.search(r'\.\s*dp\b', clean) and not any(x[1].endswith('.dp') for x in cur_imports):
        risks.append({'category': '疑似漏 import', 'line': '-',
                      'detail': '源码使用 .dp 扩展属性，但未 import androidx.compose.ui.unit.dp'})

    return {'path': path, 'status': status, 'lines': line_count, 'is_new': is_new,
            'added_imports': [x[1] for x in added_imports],
            'removed_imports': [x[1] for x in removed_imports],
            'cross_package_imports': cross, 'cur_symbols': set(declared),
            'base_symbols': set(DECL_RE.findall(base_clean)) if base else set(),
            'risks': risks}


def relocation_pass(analyzed):
    """搬迁候选：本轮新文件里出现的符号，在 base 的别文件里有、当前别文件里消失。"""
    by_path = {a['path']: a for a in analyzed}
    for a in analyzed:
        new_syms = a.get('cur_symbols', set()) - a.get('base_symbols', set())
        for other in analyzed:
            if other['path'] == a['path']:
                continue
            gone = other.get('base_symbols', set()) - other.get('cur_symbols', set())
            moved = sorted(new_syms & gone)
            if moved:
                a['risks'].append({
                    'category': '搬迁候选', 'line': '-',
                    'detail': '符号 %s 自 %s 迁入（base 有、当前已消失），核对 import 是否随迁'
                              % ('、'.join(moved[:6]), os.path.basename(other['path']))})


def run_script(script, *args):
    """subprocess 调既有门禁；原样收回 exit code 与输出，不做二次解释。"""
    p = subprocess.run([sys.executable, os.path.join(SCRIPTS, script), *args],
                       cwd=ROOT, capture_output=True, text=True)
    return {'script': script, 'args': list(args), 'rc': p.returncode,
            'stdout': p.stdout.strip(), 'stderr': p.stderr.strip()}


REV = None  # 模块级，供风险文案引用基线名


def main():
    global REV
    ap = argparse.ArgumentParser()
    ap.add_argument('--paths', nargs='*', default=[])
    ap.add_argument('--rev', default=None)
    ap.add_argument('--report', default=None)
    ap.add_argument('--json', action='store_true')
    args = ap.parse_args()
    REV = args.rev

    # 1. 确定改动文件集合。
    if args.paths:
        wanted = set(args.paths)
        rows = [(('A' if REV else '??'), p) for p in args.paths]
        if REV:
            # D1（M-34）：--paths 必须真属于 --rev 的改动集合。旧实现照单全收，幻影路径
            # 后来落到"读取失败"软类别 → 结论仍 PASS、rc=0（2026-11-26 实测复现）。
            # 为什么硬失败：路径都不在本单 diff 里，这份"证据"证明不了本单的任何事。
            rev_rows, rev_err = rev_status(REV)
            if not rev_rows:
                print('REV-DIFF: FAIL %s 取不到改动集合：%s' % (REV, rev_err.strip()[:200]))
                return 2
            rev_set = {p.lstrip('./') for _, p in rev_rows}
            phantom = [p for p in (q.lstrip('./') for q in args.paths) if p not in rev_set]
            if phantom:
                print('REV-PATHS: FAIL 以下路径不在 %s 的改动集合内（幻影路径，证据无效）：' % REV)
                for p in phantom:
                    print('  - %s' % p)
                return 2
        else:
            stat_map = {p: s for s, p in changed_status()}
            rows = [(stat_map.get(p, '??'), p) for p in args.paths]
    else:
        if REV:
            rows, err = rev_status(REV)
            if not rows:
                print('REV-DIFF: FAIL %s 取不到改动集合：%s' % (REV, err.strip()[:200]))
                return 2
        else:
            rows = changed_status()
        wanted = None

    kt_files, md_files = [], []
    for status, path in rows:
        if path.endswith('.kt'):
            kt_files.append((status, path))
        elif path.endswith('.md'):
            md_files.append((status, path))

    # 2. 取源码：工作区或 rev 快照；rev 模式落临时目录供 lint 跑。
    tmpdir = None
    analyzed = []
    decl_map, internal_map = scan_repo_maps()

    def read_current(path):
        if REV:
            return git_show(REV, path)
        full = os.path.join(ROOT, path)
        if os.path.isfile(full):
            return open(full, encoding='utf-8', errors='ignore').read()
        return None

    def read_base(path):
        base_rev = (REV + '^') if REV else 'HEAD'
        return git_show(base_rev, path)

    lint_jobs = []  # (status, path, lint 目标路径)；读取失败的文件不进 lint
    for status, path in kt_files:
        current = read_current(path)
        if current is None:
            analyzed.append({'path': path, 'status': status, 'lines': 0, 'is_new': False,
                             'risks': [{'category': '读取失败', 'line': '-',
                                        'detail': '工作区与 %s 均取不到该文件' %
                                                  ((REV + '^') if REV else 'HEAD')}]})
            continue
        base = read_base(path)
        if REV:
            status = 'A' if base is None else 'M'
        a = analyze_file(path, status, current, base, decl_map, internal_map)
        analyzed.append(a)
        if REV:
            if tmpdir is None:
                tmpdir = tempfile.mkdtemp(prefix='evidence-rev-')
            dst = os.path.join(tmpdir, path)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, 'w', encoding='utf-8') as fh:
                fh.write(current)
            lint_jobs.append((status, path, dst))
        else:
            lint_jobs.append((status, path, os.path.join(ROOT, path)))

    relocation_pass(analyzed)

    # 3. 机检汇总：kotlin-syntax-lint 逐文件；anchor / onboarding 对报告。
    machine = []
    lint_per_file = []
    for status, path, target in lint_jobs:
        if not os.path.exists(target):
            continue
        res = run_script('kotlin-syntax-lint.py', target)
        fail_lines = [ln for ln in res['stdout'].splitlines() if ln.startswith('FAIL')]
        lint_per_file.append({'path': path, 'rc': res['rc'],
                              'verdict': 'FAIL' if res['rc'] != 0 else 'PASS',
                              'fail_lines': fail_lines, 'raw': res['stdout']})
        machine.append({'check': 'kotlin-syntax-lint.py', 'path': path, 'rc': res['rc'],
                        'verdict': 'FAIL' if res['rc'] != 0 else 'PASS',
                        'fail_lines': fail_lines})

    if not REV:
        if args.report:
            anchor = run_script('check-anchor-card.py', '--strict', args.report)
            machine.append({'check': 'check-anchor-card.py', 'path': args.report,
                            'rc': anchor['rc'],
                            'verdict': 'FAIL' if anchor['rc'] != 0 else 'PASS',
                            'fail_lines': [ln for ln in anchor['stdout'].splitlines()
                                           if 'FAIL' in ln],
                            'raw': anchor['stdout']})
        onboard = run_script('check-onboarding.py')
        machine.append({'check': 'check-onboarding.py', 'path': '(本轮改动报告)',
                        'rc': onboard['rc'],
                        'verdict': 'FAIL' if onboard['rc'] != 0 else 'PASS',
                        'fail_lines': [ln for ln in onboard['stdout'].splitlines()
                                       if ln.startswith('FAIL')],
                        'raw': onboard['stdout']})
    else:
        machine.append({'check': 'check-anchor-card.py / check-onboarding.py',
                        'path': '(rev 快照模式，报告门禁跳过)', 'rc': 0,
                        'verdict': 'SKIP', 'fail_lines': [], 'raw': ''})

    # 4. 三值建议。
    machine_fail = any(m['rc'] != 0 for m in machine)
    soft_categories = {'新增文件', 'import 改动', '签名/可见性改动', '搬迁候选',
                       'public 暴露 internal', '行数超限', '疑似漏 import'}
    soft_hits = sorted({r['category'] for a in analyzed for r in a['risks']
                        if r['category'] in soft_categories})
    # D2（M-34）：读取失败是"取不到内容"，不是"没问题"。旧实现把它排除在 soft_categories
    # 之外 → 无 soft_hits → 直接 PASS，属于假绿；这里单列硬 FAIL。
    read_fail = [a['path'] for a in analyzed
                 if any(r['category'] == '读取失败' for r in a['risks'])]
    if machine_fail:
        verdict = 'FAIL'
        reasons = ['存在机检 FAIL，按门禁直接判 FAIL，不得写有条件通过或 PASS']
    elif read_fail:
        verdict = 'FAIL'
        reasons = ['存在读取失败文件（%s）：快照与工作区都取不到内容，无法判定，按 FAIL 处理'
                   % '、'.join(read_fail)]
    elif soft_hits:
        verdict = '有条件通过（编译面待 CI）'
        reasons = ['存在编译面敏感改动：' + '、'.join(soft_hits),
                   '验收结论不得写成 PASS；编译面以云端 clean 编译 + 单测全绿为准']
    else:
        verdict = 'PASS'
        reasons = ['无新增文件、无 import 改动、无签名/可见性改动，机检全绿']

    report = {
        'meta': {'rev': REV or 'worktree', 'root': ROOT,
                 'line_limit': LINE_LIMIT,
                 'lint': 'data_scripts/kotlin-syntax-lint.py',
                 'note': '静态启发式，不替代编译器；类型推断/重载解析/泛型约束只有编译器能判'},
        'files': [{'path': a['path'], 'status': a['status'], 'lines': a['lines'],
                   'is_new': a['is_new'],
                   'added_imports': a.get('added_imports', []),
                   'removed_imports': a.get('removed_imports', []),
                   'cross_package_imports': a.get('cross_package_imports', []),
                   'risks': a['risks']} for a in analyzed],
        'machine_checks': machine,
        'verdict': verdict,
        'reasons': reasons,
        'soft_categories': soft_hits,
    }

    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 1 if verdict == 'FAIL' else 0

    # 5. Markdown 证据块。
    L = []
    L.append('## 验收证据包（acceptance-evidence.py%s）' % (' --rev ' + REV if REV else ''))
    L.append('')
    L.append('### 1. 改动面')
    if analyzed:
        for a in analyzed:
            L.append('- `%s`（状态 %s，%d 行%s）' % (
                a['path'], a['status'], a['lines'],
                '，本轮新增' if a['is_new'] else ''))
    else:
        L.append('- 本单无代码改动文件')
    for status, path in md_files:
        L.append('- `%s`（状态 %s，报告/文档）' % (path, status))
    L.append('')
    L.append('### 2. 机检汇总')
    for m in machine:
        L.append('- **%s**：%s（rc=%d）`%s`' % (m['check'], m['verdict'], m['rc'], m['path']))
        for ln in m['fail_lines']:
            L.append('  - %s' % ln)
        if m.get('raw'):
            for ln in m['raw'].splitlines():
                if ln and not ln.startswith('FAIL'):
                    L.append('  - 输出：%s' % ln)
    L.append('')
    L.append('### 3. 编译面风险清单')
    any_risk = False
    for a in analyzed:
        for r in a['risks']:
            any_risk = True
            L.append('- [%s] `%s:%s`：%s' % (r['category'], a['path'], r['line'], r['detail']))
    if not any_risk:
        L.append('- 未推导出编译面静态风险（不代表编译通过，类型推断等仍交 CI）')
    L.append('')
    L.append('### 4. 三值建议')
    L.append('- **结论：%s**' % verdict)
    for why in report['reasons']:
        L.append('- 依据：%s' % why)
    L.append('- 边界：真类型推断、重载解析、泛型约束、协程契约只有编译器能判；本包不替代 clean 编译。')
    print('\n'.join(L))
    return 1 if verdict == 'FAIL' else 0


if __name__ == '__main__':
    sys.exit(main())
