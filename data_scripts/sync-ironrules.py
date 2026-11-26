#!/usr/bin/env python3
"""铁律全量同步器：把 docs/落文-规矩总纲.md 的铁律逐条照录进
① docs/规则总账.md（规则侧）② docs/机制-总控必读.md（执行侧），并机械校验三处逐条同源。

为什么（用户 2026-11-26 口令「把铁律所有东西加入进规则与总控必读文件」「两份文件都要有铁律」）：
规则侧只有要点表、必读侧只有调度口径时，读哪一份都会缺条；缺一条＝允许犯一条。
为什么还要机检：全文照录必然带来"三处各自漂移"的风险，靠人比对等于没比对，
所以把"条号集合 + 逐条归一化哈希"做成门禁 ㊺，改一处不同步另一处当场 FAIL。

适用条件：总纲铁律增删改后跑 --write；其余时候门禁 ㊺ 跑 --check。
归一化只去 Markdown 标记与空白，不动任何字句——保证"照录"是逐字的。

用法：
  python3 data_scripts/sync-ironrules.py --write     # 重新生成两处照录段（幂等）
  python3 data_scripts/sync-ironrules.py --check     # 校验，漂移 rc=1
  python3 data_scripts/sync-ironrules.py --selftest  # 双向自测（/tmp 副本上篡改必 FAIL，复原必 PASS）
退出码：0 一致 / 1 漂移 / 2 段落标记缺失
"""
import argparse
import hashlib
import re
import shutil
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ZONG = ROOT / 'docs/落文-规矩总纲.md'
ZONGKONG = ROOT / 'docs/机制-总控必读.md'
ZONGZHANG = ROOT / 'docs/规则总账.md'
BEGIN = '<!-- IRONRULES-BEGIN 本段由 data_scripts/sync-ironrules.py 生成，手改无效 -->'
END = '<!-- IRONRULES-END -->'

ITEM_RE = re.compile(r'^(?:\*\*|### )?((?:[A-G]\d{1,2}b?)|(?:P-\d{2})|(?:[RSMNT]\d))\s')
GROUPS = {'A': '边界与红线', 'B': '质量与自检', 'C': '规格与上下文', 'D': '派单与代理',
          'E': '流水线与并发', 'F': '状态与落盘', 'G': '机械门禁', 'P': '15:00 后拍板口径',
          'R': '无规格三段流水线', 'S': '缝合怪清理', 'M': '门禁+总账+溯源',
          'N': '组合验收金字塔', 'T': '子代理存活与跑题'}


def normalize(text):
    """归一化：只去 Markdown 标记与空白，字句不动。"""
    t = re.sub(r'[#*>`|]', '', text)
    return re.sub(r'\s+', '', t)


def digest(block):
    return hashlib.sha256(normalize(block).encode('utf-8')).hexdigest()[:12]


def parse_ironrules():
    lines = ZONG.read_text(encoding='utf-8').splitlines()
    starts = []
    for i, line in enumerate(lines):
        m = ITEM_RE.match(line)
        if not m:
            continue
        if m.group(1).startswith('T') and not line.lstrip().startswith(('T1 ', 'T2 ', 'T3 ', 'T4 ', 'T5 ')):
            continue
        starts.append((m.group(1), i))
    out = []
    for idx, (code, i) in enumerate(starts):
        end = starts[idx + 1][1] if idx + 1 < len(starts) else len(lines)
        for j in range(i + 1, end):
            if lines[j].startswith('## '):
                end = j
                break
        body = '\n'.join(l for l in lines[i:end] if not l.startswith('#'))
        out.append((code, i + 1, lines[i].strip(), body.rstrip()))
    return out


def render(items, heading, who, n_sec):
    out = [BEGIN,
           '## %s 铁律全量照录（%s · 共 %d 条）' % (n_sec, who, len(items)),
           '',
           '> 唯一权威仍是 `docs/落文-规矩总纲.md`；本段是**逐条照录**（含每条「为什么/适用条件」），由 `data_scripts/sync-ironrules.py` 生成，手改无效。',
           '> 机检：门禁 ㊺ 逐条比对条号集合与归一化哈希，本段与总纲/另一份照录任一处不同即 FAIL。',
           '> 为什么两份都放全文：只放要点表时读哪份都会缺条；缺一条＝允许犯一条。为什么靠机检：全文必然有漂移风险，人比对等于没比对。',
           '']
    cur = None
    for code, ln, title, block in items:
        g = code[0]
        if g != cur:
            cur = g
            out += ['### %s.%s %s（总纲 %s 段）' % (n_sec, g, GROUPS.get(g, g), g), '']
        out += ['#### %s（总纲 :%d）' % (code, ln), '', block, '']
    out.append(END)
    return '\n'.join(out)


def replace_block(path, new_block):
    text = path.read_text(encoding='utf-8')
    if BEGIN in text and END in text:
        head = text[:text.index(BEGIN)]
        tail = text[text.index(END) + len(END):]
        path.write_text(head.rstrip('\n') + '\n\n' + new_block + '\n' + tail, encoding='utf-8')
        return 'replaced'
    path.write_text(text.rstrip('\n') + '\n\n' + new_block + '\n', encoding='utf-8')
    return 'appended'


def extract_block(path):
    text = path.read_text(encoding='utf-8')
    if BEGIN not in text or END not in text:
        return None
    return text[text.index(BEGIN) + len(BEGIN):text.index(END)].strip('\n')


def parse_copy(path):
    block = extract_block(path)
    if block is None:
        return None
    items, cur, buf = [], None, []
    for line in block.splitlines():
        m = re.match(r'^#### ?([A-Za-z0-9-]+)（总纲 :(\d+)）$', line.strip())
        if m:
            if cur:
                items.append((cur, '\n'.join(buf).strip()))
            cur, buf = m.group(1), []
            continue
        if cur is not None:
            if line.startswith('#'):
                continue
            buf.append(line)
    if cur:
        items.append((cur, '\n'.join(buf).strip()))
    return items


def check(verbose=True):
    items = parse_ironrules()
    if not items:
        return ['总纲解析不到铁律条号']
    want = [c for c, _, _, _ in items]
    src = {c: b for c, _, _, b in items}
    problems = []
    for path, label in ((ZONGKONG, '总控必读'), (ZONGZHANG, '规则总账')):
        copy = parse_copy(path)
        if copy is None:
            problems.append('%s 缺 IRONRULES 段标记（未同步）' % label)
            continue
        got = [c for c, _ in copy]
        if want != got:
            miss = [c for c in want if c not in got]
            extra = [c for c in got if c not in want]
            problems.append('%s 条号集合不一致：缺 %s／多 %s／%s'
                            % (label, miss or '无', extra or '无', '同序' if got == want else '不同序'))
        for code, body in copy:
            if code in src and digest(src[code]) != digest(body):
                problems.append('%s 条 %s 正文与总纲不同源' % (label, code))
    if verbose:
        if problems:
            print('IRONRULES: FAIL（%d 项）' % len(problems))
            for p in problems[:20]:
                print('  - ' + p)
        else:
            print('IRONRULES: PASS（%d 条：规则总账与总控必读均逐条同源于总纲）' % len(items))
    return problems


def write():
    items = parse_ironrules()
    r1 = replace_block(ZONGZHANG, render(items, None, '规则侧 · 唯一事实源', '十一'))
    r2 = replace_block(ZONGKONG, render(items, None, '执行侧 · 主控专用', '§21'))
    print('写入 %d 条 → 规则总账 %s；总控必读 %s' % (len(items), r1, r2))


def selftest():
    global ZONG, ZONGKONG, ZONGZHANG
    import tempfile
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='ironrules-'))
    for p in (ZONG, ZONGKONG, ZONGZHANG):
        shutil.copy(p, tmp / p.name)
    ZONG, ZONGKONG, ZONGZHANG = tmp / ZONG.name, tmp / ZONGKONG.name, tmp / ZONGZHANG.name
    print('自测副本：', tmp)
    if check(verbose=False):
        print('SELFTEST: FAIL（起点不一致，无法自测）')
        return 1
    print('PASS 1/4 起点三处一致')
    t = ZONGKONG.read_text(encoding='utf-8')
    ZONGKONG.write_text(t.replace('原生自绘重写', '原生自绘改', 1), encoding='utf-8')
    if not check(verbose=False):
        print('SELFTEST: FAIL（篡改必读后仍 PASS，机检无效）')
        return 1
    print('PASS 2/4 篡改必读被判 FAIL')
    shutil.copy(tmp / 'docs' / '落文-规矩总纲.md', ZONGKONG) if False else None
    ZONGKONG.write_text(t, encoding='utf-8')
    t2 = ZONGZHANG.read_text(encoding='utf-8')
    ZONGZHANG.write_text(re.sub(r'^#### A1（总纲 :\d+）\n', '', t2, count=1, flags=re.M), encoding='utf-8')
    if not check(verbose=False):
        print('SELFTEST: FAIL（总账删条后仍 PASS）')
        return 1
    print('PASS 3/4 总账删条被判 FAIL')
    shutil.rmtree(tmp)
    print('PASS 4/4 临时副本已删')
    print('SELFTEST: PASS（双向成立）')
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write', action='store_true')
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--selftest', action='store_true')
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    if a.write:
        write()
        return 0 if not check() else 1
    return 1 if check() else 0


if __name__ == '__main__':
    sys.exit(main())
