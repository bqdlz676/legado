#!/usr/bin/env python3
"""守则回执机检（2026-11-26 事故五的产品）。

为什么要有它：守则写在文件里，只证明它存在；不证明任何人读过并照做。
本夜实测——三份必读早就有"写码方该跑哪些门禁""验收方该验哪些陷阱"，
而我同一天连犯四类错。用户原话："为什么即使写了也不是实际就读了"。

所以把"读过"变成可验事实：
  1) 报告头必须有「守则回执」块，含当前必读文件的 sha256 前 12 位；
     文件一改，旧指纹失效 → 逼重读，吃老本当场被抓。
  2) 至少三条「逐字引用」必须回源到该文件真实存在的整句（子串比对）。
  3) 写明实际照做的条款编号。

用法：
  python3 data_scripts/check-onboarding.py            # 查本轮新增/改动的报告
  python3 data_scripts/check-onboarding.py --emit     # 打印当前指纹块，派单时贴进任务书
  python3 data_scripts/check-onboarding.py --audit    # 扫历史报告，列老账（不判 FAIL）
退出码：0 = 合规或无需检查；1 = 有报告缺回执 / 引用回源不到 / 指纹过期。
"""
import fnmatch
import glob
import hashlib
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

RULES = {
    'total': 'docs/机制-总控必读.md',
    'writer': 'docs/机制-子代理必读.md',
    'verifier': 'docs/机制-验收代理必读.md',
    'doc': 'docs/机制-文档子代理必读.md',
}

# 只管「执行类」报告（写码/验收/实现/组装）：这些报告的结论会被拿去推代码或判收口。
# 机制/复盘/诊断/分析/规划属调度层自撰，本身就是规则来源，不要求给自己贴回执（防自指噪音）。
LEGACY_NOTE = '回执缺失（规则上线前交付，留档）'  # 旧报告豁免标记：必须在报告里明写并进台账，不许默免
REPORT_PATTERNS = ('docs/修复-*.md', 'docs/验收-*.md', 'docs/实现-*.md', 'docs/组装-*.md')

# 回归夹具目录/后缀：测试样本是资产，不与真报告混命名空间，门禁一律跳过（2026-11-26 追加）。
FIXTURE_DIR = 'data_scripts/fixtures'
FIXTURE_SUFFIX = '.fixture'


def is_fixture(path):
    return FIXTURE_DIR in path or path.endswith(FIXTURE_SUFFIX)


def sha12(path):
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        return None
    with open(full, 'rb') as fh:
        return hashlib.sha256(fh.read()).hexdigest()[:12]


def current_block():
    rows = ['守则回执：']
    fps = '；'.join('%s=%s' % (p, sha12(p)) for p in RULES.values() if sha12(p))
    rows.append('- 指纹：' + fps)
    rows.append('- 逐字引用 1：「…」（文件:行）')
    rows.append('- 逐字引用 2：「…」（文件:行）')
    rows.append('- 逐字引用 3：「…」（文件:行）')
    rows.append('- 适用条款：…（逐条写本单照做的条款）')
    return '\n'.join(rows)


def is_small_readonly_report(path):
    """只读/查证小单可只贴指纹：以报告首行或机器头声明为据。判据保守，拿不准就当需要完整回执。"""
    with open(path, encoding='utf-8', errors='ignore') as fh:
        head = ''.join(fh.readline() for _ in range(12))
    return ('查证' in head and '工具调用' in head and '≤10' in head)


def check_report(path):
    with open(path, encoding='utf-8', errors='ignore') as fh:
        text = fh.read()
    problems = []
    if LEGACY_NOTE in text:
        return []  # 旧报告留档豁免（报告内已明写并进台账）
    idx = text.find('守则回执')
    if idx < 0:
        if LEGACY_NOTE in text:
            return []  # 规则上线前交付的旧报告：留档豁免（但必须在台账里挂号）
        return ['缺「守则回执」块']
    block = '\n'.join(text[idx:].splitlines()[:20]).split('## ')[0]  # 回执块只到下一个二级标题止：否则 ANCHOR-ACK 里的编号行会被当成"逐字引用"误判回源失败

    # 指纹：至少一枚与当前磁盘一致
    fps = dict(re.findall(r'(docs/机制-[^\s；;=]+\.md)\s*=\s*([0-9a-f]{6,})', block))
    ok_fp = 0
    stale = []
    for p, h in fps.items():
        cur = sha12(p)
        if cur and (cur.startswith(h[:12]) or h.startswith(cur[:12])):
            ok_fp += 1
        elif cur:
            stale.append('%s 磁盘=%s 报告=%s' % (p, cur, h))
    if not fps:
        problems.append('指纹行缺少正确格式（docs/机制-xxx.md=<sha256 前 12 位>）')
    elif ok_fp == 0:
        # 判死只在"一枚都对不上"时发生。为什么改：主控每轮会修订必读，
        # 旧规则是"任一枚过期即判死"，等于主控一改书就打挂全仓在用报告，
        # 逼着大家别再改守则——制度无法进化。现在要求"至少一枚现行命中"，
        # 过期项降为提示，报告仍须在下次修订时刷新。
        problems.append('指纹全部过期（须重读守则并刷新）：' + '；'.join(stale))

    # 逐字引用：至少 3 条，且必须回源命中
    quotes = re.findall(r'(?:逐字引用\s*\d+：|^\s*\d+\.\s*)(.+?)$', block, flags=re.M)
    quotes = [q for q in quotes if q.strip()]
    if len(quotes) < 3 and not is_small_readonly_report(os.path.join(ROOT, path)):
        problems.append('逐字引用不足 3 条（实际 %d）' % len(quotes))
    srcs = {}
    for p in RULES.values():
        full = os.path.join(ROOT, p)
        if os.path.isfile(full):
            srcs[p] = open(full, encoding='utf-8', errors='ignore').read()
    hit = 0
    for q in quotes:
        inner = re.search(r'[「“"](.+?)[」”"]', q)
        if not inner:
            problems.append('引用格式错（须用「」包住逐字原句）：%s' % q.strip()[:30])
            continue
        frag = inner.group(1).strip()
        if len(frag) < 6:
            problems.append('引用过短（<6 字），无法回源：%s' % frag)
            continue
        if any(frag in s for s in srcs.values()):
            hit += 1
        else:
            problems.append('引用回源失败（守则里找不到这句）：%s' % frag[:40])
    if quotes and hit == 0:
        problems.append('三条引用全部回源失败')

    # 适用条款位置放宽到全文：它是"照做了哪些条"的登记项，位置不影响可核对性；
    # 早期实现只认回执块 20 行内，导致写在文末「总控补录」里的合规报告被判死（假红）。
    if '适用条款' not in text and not is_small_readonly_report(os.path.join(ROOT, path)):
        problems.append('缺「适用条款」行（须写出本单实际照做的条款）')
    return problems


def changed_reports():
    out = subprocess.run(['git', '-c', 'core.quotepath=false', 'status', '--porcelain'],
                         cwd=ROOT,
                         capture_output=True, text=True).stdout
    files = []
    for row in out.splitlines():
        status, path = row[:2], row[3:].strip().strip('"')
        if is_fixture(path):
            continue
        if status.strip() and status[0] in 'AM?' and any(
                fnmatch.fnmatch(path, pat) for pat in REPORT_PATTERNS):
            files.append(path)
    return files


def audit_all():
    files = []
    for pat in REPORT_PATTERNS:
        files.extend(glob.glob(os.path.join(ROOT, pat)))
    bad = []
    for f in sorted(set(files)):
        rel = os.path.relpath(f, ROOT)
        if is_fixture(rel):
            continue
        probs = check_report(f)
        if probs:
            bad.append((rel, probs[0]))
    print('ONBOARD-AUDIT: %d 份报告缺回执或不合规（历史留档可批量补，见 Q-199）' % len(bad))
    for rel, why in bad[:40]:
        print('  %s -> %s' % (rel, why))
    return 0


def main():
    args = sys.argv[1:]
    if '--emit' in args:
        print(current_block())
        return 0
    if '--audit' in args:
        return audit_all()
    files = changed_reports()
    if not files:
        print('ONBOARD: PASS（本轮无报告改动）')
        return 0
    bad = 0
    for rel in files:
        probs = check_report(os.path.join(ROOT, rel))
        for p in probs:
            print('FAIL %s：%s' % (rel, p))
            bad += 1
    print('ONBOARD: %s（%d 份报告）' % ('FAIL' if bad else 'PASS', len(files)))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
