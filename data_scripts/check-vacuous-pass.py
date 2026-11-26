#!/usr/bin/env python3
"""空集即 PASS 假绿检测器（编号待总控在 rule-gate.sh 登记接线）。

为什么要有它（2026-11-26 实例：check-anchor-card.py 空集分支 return 0 放行）：
门禁拿到的待检目标集合为空（没传报告 / 没检出改动 / 队列无行），本应报"无可检对象"，
却在空集合分支里直接 `return 0` 甚至打印 PASS——机检一个都没验，结论却是绿的。
这类"空集假绿"最容易在 CI 接线漏传参数、diff 为空、队列文件缺失时蒙混过关。

判据（窄）：
  1) 条件是空集合/空列表判断之一：
       `if not <变量>:`、`if len(<变量>) == 0:`、`if len(<变量>) < 1:`；
  2) 该分支之后 3 行内（同缩进体内）出现裸 `return 0`
     （排除 `return 0 if ... else 1` 三态表达式）；
  3) 文案分流：分支体出现 PASS / 无待检 / 干净 字样 → 判"空集即 PASS"候选；
     只出现 WARN / INFO → 空集即 WARN，没有把空集说成合规，不入 FAIL，只登记豁免；
  4) 排除存在性 / 字符串 / 正则谓词：`p.exists()`、`os.path.isfile/isdir`、
     `line.startswith/endswith/strip`、`re.search/match` 等——它们判的是文件或文本，
     不是空集合，判它们只会满屏假红，假红多了门禁就会被人绕开；
  5) 排除结论累加器（`bad`/`problems`/`fails` 等）：其为空表示"零违规"，是真绿，
     不是"空输入没验"。

豁免锚定（ALLOWLIST）：键 = `(文件相对路径, 变量名, 分支代码块 sha256 前 12 位)`。
为什么不用行号：行号随任何一次上下增删行就漂，豁免会静默失配或错配；代码块哈希在
代码不变时稳定，代码一改哈希即变，豁免自动失效、重新进入审查——豁免必须随代码重审。
不允许按文件名前缀整段豁免（前缀可被改名伪造，见 ㊼ 豁免身份化教训）。

用法：
  python3 data_scripts/check-vacuous-pass.py            # 扫 data_scripts/check-*.py
  python3 data_scripts/check-vacuous-pass.py --selftest # 双向夹具自测，跑完删临时目录
退出码：0 = 无未豁免命中；1 = 有空集假绿；2 = 用法错。
"""
import argparse
import hashlib
import pathlib
import re
import shutil
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCAN_DIR = ROOT / 'data_scripts'
SELF_NAME = pathlib.Path(__file__).name
WINDOW = 3  # 空集条件之后往下看 3 行：假绿分支通常一行 print 一行 return

# `if not <名字>:`；名字允许属性（args.reports）与下标（st['ROWS']）
COND_NOT = re.compile(r'^\s*if\s+not\s+([A-Za-z_][\w.]*(?:\[[^\]]*\])?)\s*:')
# `if len(<名字>) == 0:` / `< 1`
COND_LEN = re.compile(r'^\s*if\s+len\(\s*([A-Za-z_][\w.]*)\s*\)\s*(?:==\s*0|<\s*1)\s*:')
# 裸 return 0（排除 `return 0 if ... else 1` 这类三态表达式）
RET_ZERO = re.compile(r'^\s*return\s+0\s*(#.*)?$')
PRINT_CALL = re.compile(r'print\(([^\n)]*)')

# 绿色判词：出现即表示分支把空集表述成"合规/干净"。
GREEN_WORDS = ('PASS', '无待检', '干净')
# 提示判词：只说明状态、不表述合规；空集即 WARN 不算假绿。
WARN_WORDS = ('WARN', 'INFO')

# 存在性 / 字符串 / 正则 / 方法调用谓词：出现即排除，不是空集合判断。
# 为什么收窄：这些谓词的真假取决于文件在不在、文本长什么样，与"待检集合是否为空"无关。
EXCLUDE_PRED = [
    r'\.exists\(', r'\.exists\b', r'isfile', r'isdir', r'\.is_file\(',
    r'\.startswith\(', r'\.endswith\(', r'\.strip\(', r'\.upper\(', r'\.lower\(',
    r'\.read\(', r'\.group\(', r'\.search\(', r'\.match\(', r'\.fullmatch\(',
    r'\bre\.', r'returncode', r'isabs', r'\.split\(', r'os\.path',
]
EXCLUDE_RE = re.compile('|'.join(EXCLUDE_PRED))

# 标量 / 布尔 / 结论累加器：空表示"无违规 / 无匹配"，是真绿，不是空输入假绿。
# 为什么单列：bad/problems/fails 这类是验完之后攒结论的清单，清单为空 = 验过且没问题；
# 与 files/commits/shas 这种"待检目标"性质相反，混为一谈会把正常 PASS 判红。
SCALAR_NAMES = {
    'ok', 'm', 'anchor', 'hint', 'tok', 'f', 'c', 'cur', 'out', 'inner',
    'verdict', 'landing', 'exempt', 'traced', 'has_named', 'first_asst',
    'registered', 'status_cell', 'agent_id', 'position_type', 'missing',
    'bad', 'problems', 'fails', 'warns', 'errors', 'violations', 'issues',
}

# 精确豁免：键 = (文件相对路径, 变量名, 分支块 sha256[:12])，值 = 为什么可接受。
# 代码块 = if 条件行 + 其后分支体行（去行尾空白后拼接）；改动任一行哈希即变，豁免自动失效。
ALLOWLIST = {
    # diff 型门禁：待检集合来自 git 改动面，本轮无改动 = 没有需要核验的对象，
    # 空集是真实状态而非接线漏传；与 CLI 逐报告传入的 files 性质不同。
    ('data_scripts/check-onboarding.py', 'files', 'eb1ece3c6b08'):
        'diff 型门禁：files 来自 git status 本轮改动报告，无改动即无可核验对象；'
        '非 CLI 漏传，空集为真实干净状态。',
    ('data_scripts/check-report-fields.py', 'paths', '53fabe213b5e'):
        'diff 型门禁：paths 为 changed_reports() 算出的本轮报告，空集 = 本轮无报告改动；'
        '字段核验无对象可验，不构成假绿。',
    ('data_scripts/check-dependency-symbols.py', 'files', 'cc556ab09ea1'):
        'diff 型门禁：files 为 args 或 changed_kt_files()，本轮无 kt 改动时无源文件可扫；'
        '索引缺失已在上方 return 1 单独兜底，此处空集不掩盖索引故障。',
    # 提交区间型门禁：区间内无提交 = 工作树干净 / 无待推提交，是真实状态；
    # 判的是"待推 commit 是否合规"，空区间不是漏检。
    ('data_scripts/check-commit-trailer.py', 'commits', '4153e96c2c73'):
        '区间型 WARN 门禁：commits 为未推提交清单，空集 = 无待推提交（干净树）；'
        'git log 不可用已在上方 return 1 兜底，空集分支打印 INFO 非 PASS，未把空集说成合规；'
        '脚本整体 WARN 型、退出码恒 0，不阻断 push（Q-257 收紧后块哈希更新）。',
    ('data_scripts/check-push-track.py', 'shas', '4760bf4009dc'):
        '区间型门禁：shas 为 rev-list 算出的待推提交，空集 = 指定 range 内无提交；'
        '轨型分类无对象可分，打印"干净"与空集一致，非漏检。',
    ('data_scripts/check-commit-split.py', 'commits', '040b63a248fc'):
        '历史型门禁：commits 取自 git log，空集仅发生在零提交仓库；CI 必有历史提交，'
        '且 git log 不可用已在上方 return 1 兜底，此空集分支在常规环境不可达。',
    ('data_scripts/check-commit-provenance.py', 'commits', '1508651e032a'):
        '历史型门禁：commits 取自 git log，空集仅发生在零提交仓库；CI 必有历史提交，'
        '且 git log 不可用已在上方 return 1 兜底；分支打印 INFO 非 PASS，未把空集说成合规。',
    # WARN 型分支：只提示状态、未把空集说成合规，不入 FAIL；登记理由备查。
    ('data_scripts/check-anchor-integrity.py', 'anchors', '5e5944523212'):
        '空锚点分支打印 WARN（非 PASS）即 return 0；无处置锚点表示台账未到处置阶段，'
        '下游守卫另有时间窗判定兜底，未把空集表述为合规。',
    ('data_scripts/check-visual-baseline.py', 'entries', 'fca05d5ddc35'):
        '空登记项分支打印 WARN 并明示"工序尚未冻结，不算 FAIL"；基线冻结侧 files 为空 '
        '已 return 1，此处仅为工序未启动的透明降级，未把空集表述为合规通过。',
}


def _is_excluded(var, cond_text):
    """存在性/字符串/正则谓词，或标量/结论累加器 → 不算空集合假绿。"""
    if EXCLUDE_RE.search(cond_text):
        return True
    base = var.split('.')[-1].split('[')[0]
    if base in SCALAR_NAMES:
        return True
    return False


def _body_window(lines, i, cond_indent):
    """取条件行之后 WINDOW 行内、缩进更深的分支体行。"""
    body = []
    for j in range(i + 1, min(i + 1 + WINDOW, len(lines))):
        raw = lines[j]
        if not raw.strip():
            continue
        indent = len(raw) - len(raw.lstrip())
        if indent <= cond_indent:
            break  # 分支体结束（回到同级或更浅缩进）
        body.append(raw)
    return body


def _block_hash(if_line, body):
    """分支代码块指纹：if 条件行 + 分支体行，去行尾空白后 sha256 前 12 位。"""
    block = '\n'.join(x.rstrip() for x in [if_line] + body)
    return hashlib.sha256(block.encode('utf-8')).hexdigest()[:12]


def _verdict(body_text):
    """pass=把空集说成合规；warn=仅提示状态；silent=无判词裸 return 0。"""
    if any(w in body_text for w in GREEN_WORDS):
        return 'pass'
    if any(w in body_text for w in WARN_WORDS):
        return 'warn'
    return 'silent'


def scan(scan_dir=SCAN_DIR, pattern='check-*.py'):
    """返回命中列表：dict(file, line, var, print, verdict, allow, reason, hash)。

    pattern 可换：真仓只扫 check-*.py；selftest 在 /tmp 用 *.py 夹具。
    """
    hits = []
    for p in sorted(pathlib.Path(scan_dir).glob(pattern)):
        if p.name == SELF_NAME:
            continue  # 不扫自己：本脚本谈模式但自身不实现空集放行
        text = p.read_text(encoding='utf-8', errors='ignore')
        lines = text.splitlines()
        rel = 'data_scripts/' + p.name
        for i, line in enumerate(lines):
            m = COND_NOT.match(line) or COND_LEN.match(line)
            if not m:
                continue
            var = m.group(1)
            if _is_excluded(var, line):
                continue
            cond_indent = len(line) - len(line.lstrip())
            body = _body_window(lines, i, cond_indent)
            body_text = '\n'.join(body)
            if not any(RET_ZERO.match(b) for b in body):
                continue  # 空集分支没有 return 0：continue / 报错返回 / 攒结论都不算假绿
            pm = PRINT_CALL.search(body_text)
            print_text = pm.group(1).strip() if pm else ''
            digest = _block_hash(line, body)
            key = (rel, var, digest)
            hits.append({
                'file': rel,
                'line': i + 1,
                'var': var,
                'print': print_text,
                'verdict': _verdict(body_text),
                'hash': digest,
                'allow': key in ALLOWLIST,
                'reason': ALLOWLIST.get(key, ''),
            })
    return hits


def report(hits):
    bad = [h for h in hits if h['verdict'] != 'warn' and not h['allow']]
    allowed = [h for h in hits if h['allow']]
    warns = [h for h in hits if h['verdict'] == 'warn' and not h['allow']]
    if bad:
        print('VACUOUS-PASS: FAIL（%d 处空集即 return 0 假绿结构）' % len(bad))
        for h in bad:
            print('  - %s:%d %s（分支打印：%s；块指纹 %s）'
                  % (h['file'], h['line'], h['var'], h['print'] or '无 print', h['hash']))
        if allowed:
            print('  已豁免 %d 处（哈希锚定登记，理由见下）：' % len(allowed))
            for h in allowed:
                print('  - [已豁免] %s:%d %s（%s）：%s'
                      % (h['file'], h['line'], h['var'], h['hash'], h['reason']))
        if warns:
            print('  WARN 分支 %d 处未登记理由（不入 FAIL，建议补登记）：' % len(warns))
            for h in warns:
                print('  - [WARN] %s:%d %s（%s）：%s'
                      % (h['file'], h['line'], h['var'], h['hash'], h['print']))
        return 1
    print('VACUOUS-PASS: PASS（未发现未豁免的空集假绿结构；%d 处已按哈希锚定登记豁免）'
          % len(allowed))
    for h in allowed:
        print('  - [已豁免] %s:%d %s（%s）：%s'
              % (h['file'], h['line'], h['var'], h['hash'], h['reason']))
    for h in warns:
        print('  - [WARN 未登记] %s:%d %s（%s）：%s'
              % (h['file'], h['line'], h['var'], h['hash'], h['print']))
    return 0


def selftest():
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='vacuous-pass-selftest-'))
    try:
        # 样本 1（必须被抓）：空集 + PASS 判词 + return 0，真假绿。
        (tmp / 'bad_pass.py').write_text(
            "import sys\n"
            "def main():\n"
            "    files = sys.argv[1:]\n"
            "    if not files:\n"
            "        print('PASS（空）')\n"
            "        return 0\n"
            "    return 1\n",
            encoding='utf-8')
        # 样本 2（不抓）：空集 + WARN 判词 + return 0，只提示未表述合规。
        (tmp / 'good_warn.py').write_text(
            "def main(rows):\n"
            "    if not rows:\n"
            "        print('WARN（无有效行，按空队列判定）')\n"
            "        return 0\n"
            "    return 1\n",
            encoding='utf-8')
        # 样本 3（不抓）：exists() / startswith() 存在性与字符串判断。
        (tmp / 'good_exists.py').write_text(
            "from pathlib import Path\n"
            "def main(p, line):\n"
            "    if not p.exists():\n"
            "        return 1\n"
            "    if not line.startswith('|'):\n"
            "        return 1\n"
            "    return 0\n",
            encoding='utf-8')

        hits = scan(tmp, '*.py')
        bad = [h for h in hits if h['verdict'] != 'warn' and not h['allow']]
        bad_names = {h['file'].split('/')[-1] for h in bad}
        if bad_names != {'bad_pass.py'}:
            print('SELFTEST: FAIL（空集 PASS 样本未按预期被抓：%s）' % bad_names)
            return 1
        print('PASS 1/3 空集 PASS 样本被点名（%s）'
              % ', '.join('%s:%d %s' % (h['file'].split('/')[-1], h['line'], h['var'])
                          for h in bad))

        warn_names = {h['file'].split('/')[-1] for h in hits if h['verdict'] == 'warn'}
        if 'good_warn.py' not in warn_names:
            print('SELFTEST: FAIL（空集 WARN 分支未被分流到 WARN 桶）')
            return 1
        if any(h['file'].endswith('good_warn.py') for h in bad):
            print('SELFTEST: FAIL（空集 WARN 被误判为假绿 FAIL）')
            return 1
        print('PASS 2/3 空集 WARN 分支不入 FAIL，仅登记 WARN 桶')

        names = {h['file'].split('/')[-1] for h in hits}
        if 'good_exists.py' in names:
            print('SELFTEST: FAIL（exists()/startswith() 判断被误抓：%s）' % names)
            return 1
        print('PASS 3/3 exists()/startswith() 存在性与字符串判断未被误抓')

        # 反向加固：PASS 样本的 return 0 改成 return 1，机检必须随之放行。
        (tmp / 'bad_pass.py').write_text(
            "import sys\n"
            "def main():\n"
            "    files = sys.argv[1:]\n"
            "    if not files:\n"
            "        print('PASS（空）')\n"
            "        return 1\n"
            "    return 0\n",
            encoding='utf-8')
        if any(h['file'].endswith('bad_pass.py')
               for h in scan(tmp, '*.py') if h['verdict'] != 'warn'):
            print('SELFTEST: FAIL（return 1 后仍判红，判据未盯死 return 0）')
            return 1
        print('PASS 4/4 分支改 return 1 后即放行，判据只盯空集 + return 0 组合')
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser(description='空集即 PASS 假绿检测器')
    ap.add_argument('--selftest', action='store_true', help='跑双向夹具自测后删临时目录')
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    return report(scan())


if __name__ == '__main__':
    sys.exit(main())
