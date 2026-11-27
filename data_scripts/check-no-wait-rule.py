#!/usr/bin/env python3
"""门禁 ㊿：规则文本里不许再出现"用 wait_agent 挂起"的有效条款（F-124）。

为什么（2026-11-26 用户口令「把wait agent删了」）：规则自己写着"用 wait_agent 阻塞等"，
主控照着做就是空等，用户已将整档"等"删掉。文本不一致＝读哪条都能开脱，必须机检。
判据一（文本面）：扫 docs/机制-总控必读.md 与 docs/落文-规矩总纲.md，凡是出现 wait_agent 的行，
必须带"作废/不许/删除/禁止"之一，否则 FAIL 并点名 文件:行。
判据二（行为面，2026-11-27 用户口令「主控禁等门禁没有作用」后补）：文本面只能证明规则写得对，
证明不了调度层真没这么干——实测主控仍在 wait_agent 里空等 35 次而本门禁照样 PASS。
所以补行为面：读主控自己的 trajectory.jsonl，只认 assistant.tool_calls 里 function.name == wait_agent
的真实调用；以 docs/.no-wait-since 的「<agent_id>:<行偏移>」为锚，偏移之后出现即 FAIL。
为什么用行偏移而不是时间戳：本仓轨迹多数无 ts 字段，按时间过滤会全漏或全误，行偏移是唯一可复算的锚。
用法：python3 data_scripts/check-no-wait-rule.py [--selftest]
"""
import json
import pathlib
import sys
import tempfile
import shutil

ROOT = pathlib.Path(__file__).resolve().parent.parent
TARGETS = ['docs/机制-总控必读.md', 'docs/落文-规矩总纲.md',
           'docs/机制-防限流彻底方案.md', 'docs/派单队列.md',
           'docs/规则总账.md', 'docs/规则全集.md', 'docs/规则逻辑链.md', 'docs/接手/00-入口.md']
OKWORDS = ('作废', '不许', '删除', '禁止', '禁用', '不使用')


def scan(root=ROOT):
    bad = []
    for rel in TARGETS:
        p = root / rel
        if not p.is_file():
            continue
        for i, line in enumerate(p.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
            if 'wait_agent' in line and not any(w in line for w in OKWORDS):
                bad.append('%s:%d %s' % (rel, i, line.strip()[:90]))
    return bad


TRAJ_ROOT = pathlib.Path('/home/user/.doubao/agent_mode/workspace/.sessions')
MARKER = ROOT / 'docs' / '.no-wait-since'


def read_marker():
    if not MARKER.is_file():
        return None, 0
    raw = MARKER.read_text(encoding='utf-8').strip()
    if not raw:
        return None, 0
    if ':' in raw:
        aid, off = raw.split(':', 1)
        try:
            return aid.strip() or None, int(off.strip())
        except ValueError:
            return raw, 0
    return raw, 0


def behavior_hits(session=None, agent=None, root=None):
    """返回 marker 之后主控真实 wait_agent 调用 [(aid, ts, snippet)]。"""
    aid_marker, skip = read_marker()
    out = []
    troot = pathlib.Path(root) if root else TRAJ_ROOT
    for tj in troot.glob('*/agents/*/system/trajectory.jsonl'):
        aid = tj.parts[-3]
        sid = tj.parts[-5]
        if session and sid != session:
            continue
        if agent and aid != agent:
            continue
        if aid_marker and aid != aid_marker and not agent:
            continue
        with open(tj, encoding='utf-8', errors='replace') as f:
            for idx, line in enumerate(f, 1):
                if idx <= (0 if agent else skip) or 'wait_agent' not in line:
                    continue
                try:
                    rec = json.loads(line)
                except Exception:
                    continue
                if rec.get('role') != 'assistant':
                    continue
                for call in rec.get('tool_calls') or []:
                    fn = (call.get('function') or {}).get('name') or ''
                    if fn == 'wait_agent':
                        out.append((aid, rec.get('ts') or '(no-ts)',
                                    json.dumps(call.get('function') or {}, ensure_ascii=False)[:100]))
    return out


def main():
    if '--selftest' in sys.argv:
        tmp = pathlib.Path(tempfile.mkdtemp(prefix='nowait-'))
        try:
            (tmp / 'docs').mkdir()
            (tmp / 'docs' / '机制-总控必读.md').write_text('用 wait_agent 阻塞等结果\n', encoding='utf-8')
            (tmp / 'docs' / '落文-规矩总纲.md').write_text('wait_agent 已删除（2026-11-26）\n', encoding='utf-8')
            hit = scan(tmp)
            miss = scan(ROOT)
            # 行为面必失败样本：造一条含真实 wait_agent 调用的轨迹，门禁必须抓到
            trj = tmp / 's1' / 'agents' / 'o_test' / 'system'
            trj.mkdir(parents=True)
            (trj / 'trajectory.jsonl').write_text(
                '{"role": "assistant", "tool_calls": [{"type": "function", "function": {"name": "wait_agent", "arguments": "{}"}}]}\n',
                encoding='utf-8')
            bh = behavior_hits(agent='o_test', root=str(tmp))
            ok = bool(hit) and not miss and bool(bh)
            print('SELFTEST: %s（文本坏样本 %d 命中 / 真仓 %d 命中 / 行为坏样本 %d 命中）'
                  % ('PASS' if ok else 'FAIL', len(hit), len(miss), len(bh)))
            return 0 if ok else 1
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    bad = scan()
    hits = behavior_hits()
    if hits:
        print('NO-WAIT-RULE: FAIL（marker 之后主控真实 wait_agent 调用 %d 次，调度层仍在空等）' % len(hits))
        for aid, ts, snip in hits[:5]:
            print('  - %s %s %s' % (aid[:12], ts, snip))
        return 1
    if bad:
        print('NO-WAIT-RULE: FAIL（%d 行仍在教人挂起）' % len(bad))
        for b in bad[:6]:
            print('  - ' + b)
        return 1
    print('NO-WAIT-RULE: PASS（文本面无挂起条款；行为面 marker=%s:%d 之后 wait_agent 0 次）' % (read_marker()[0] or '(无)', read_marker()[1]))
    return 0


if __name__ == '__main__':
    sys.exit(main())
