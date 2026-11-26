#!/usr/bin/env python3
"""门禁 ㉛ 事故清单完整性守卫。

为什么需要：事故之前散落在 复盘-*.md / CI归因-*.md / 台账-子代理存活.md 里，
"发生过什么"只能靠人翻；一旦某条事故没进唯一清单，下一棒就会重犯。
判据：①docs/CI归因-*.md 中 conclusion=failure 的每个 run id 必须出现在 docs/事故清单.md；
     ②台账-子代理存活.md 中每个"判废"的代理 id 必须出现在清单；
     ③清单必须存在且非空。
"""
import re
import shutil
import sys
import tempfile
from pathlib import Path

REG = 'docs/事故清单.md'


def main(repo: str) -> int:
    base = Path(repo)
    reg = base / REG
    if not reg.exists() or len(reg.read_text(encoding='utf-8')) < 500:
        print(f'INCIDENT-REG: FAIL（{REG} 缺失或内容过少）')
        return 1
    reg_text = reg.read_text(encoding='utf-8')

    missing_runs = []
    for f in sorted((base / 'docs').glob('CI归因-*.md')):
        txt = f.read_text(encoding='utf-8', errors='ignore')
        m = re.search(r'\| conclusion \| (\w+) \|', txt)
        if not m or m.group(1) != 'failure':
            continue
        run = f.stem.replace('CI归因-', '')
        if run not in reg_text:
            missing_runs.append(run)

    missing_agents = []
    live = base / 'docs' / '台账-子代理存活.md'
    if live.exists():
        for ln in live.read_text(encoding='utf-8', errors='ignore').splitlines():
            if not ln.startswith('| s_'):
                continue
            if '判废' not in ln:
                continue
            aid = ln.split('|')[1].strip()
            if aid and aid not in reg_text:
                missing_agents.append(aid)

    if missing_runs or missing_agents:
        print('INCIDENT-REG: FAIL')
        for r in missing_runs:
            print(f'  未登记 CI 失败 run：{r}')
        for a in missing_agents:
            print(f'  未登记判废代理：{a}')
        return 1
    print(f'INCIDENT-REG: PASS（清单含 {len(re.findall(r"- \*\*[KASD]-", reg_text))} 条事故，CI 失败与判废代理均已登记）')
    return 0


def _make_repo(files: dict) -> str:
    """临时目录构造夹具仓库：files 为 {相对仓库根路径: 内容}（键含 docs/ 前缀）。"""
    repo = tempfile.mkdtemp(prefix='incident-register-selftest-')
    for rel, content in files.items():
        p = Path(repo) / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding='utf-8')
    return repo


_FILLER = '夹具填充行，保证清单超过 500 字符阈值；本行为自测占位，不进入真实台账。\n' * 30


def _registry(run: str, agent: str) -> str:
    return ('# 事故清单（夹具）\n\n'
            f'- **K-001** CI 失败 run {run} 已登记。\n'
            f'- **K-002** 判废代理 {agent} 已登记。\n\n' + _FILLER)


def _ci_attribution(run: str, conclusion: str) -> str:
    return (f'# CI归因-{run}\n\n'
            '| 字段 | 值 |\n'
            f'| conclusion | {conclusion} |\n')


def _ledger(agent: str, verdict: str) -> str:
    return ('# 台账-子代理存活（夹具）\n\n'
            '| 代理 | 轮次 | 处置 | 备注 |\n'
            f'| {agent} | 1 | {verdict} | 自测夹具 |\n')


def selftest() -> int:
    """双向自测：失败 run 与判废代理均已登记 → main() rc=0；缺清单/漏登记 run/漏登记代理 → rc=1。
    夹具 tempfile.mkdtemp + rmtree 自删，判据回调生产 main(repo)。"""
    RUN, AGENT = '45783964800', 's_abc123'
    # 好样本：清单 ≥500 字符且同时收录失败 run 与判废代理
    good = _make_repo({
        REG: _registry(RUN, AGENT),
        f'docs/CI归因-{RUN}.md': _ci_attribution(RUN, 'failure'),
        'docs/台账-子代理存活.md': _ledger(AGENT, '判废'),
    })
    try:
        rc_good = main(good)
        assert rc_good == 0, f"全部登记应 PASS，rc={rc_good}"
        print('[selftest] 好样本（失败 run 与判废代理均已登记）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一：CI 归因文件 conclusion=failure，但其 run 未进清单 → 命中漏登记 run 判据
    bad1 = _make_repo({
        REG: _registry(RUN, AGENT),
        'docs/CI归因-45783964811.md': _ci_attribution('45783964811', 'failure'),
        'docs/台账-子代理存活.md': _ledger(AGENT, '判废'),
    })
    try:
        rc_b1 = main(bad1)
        assert rc_b1 == 1, f"漏登记 run 应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（failure run 未登记进清单）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad1, ignore_errors=True)

    # 坏样本二：台账判废代理 id 未进清单 → 命中漏登记代理判据
    bad2 = _make_repo({
        REG: _registry(RUN, AGENT),
        f'docs/CI归因-{RUN}.md': _ci_attribution(RUN, 'failure'),
        'docs/台账-子代理存活.md': _ledger('s_xyz789', '判废'),
    })
    try:
        rc_b2 = main(bad2)
        assert rc_b2 == 1, f"漏登记判废代理应 FAIL，rc={rc_b2}"
        print('[selftest] 坏样本二（判废代理未登记进清单）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad2, ignore_errors=True)

    # 边界·缺字段：清单存在但内容不足 500 字符 → 命中「缺失或内容过少」判据，判红不放行
    short = _make_repo({REG: '# 事故清单\n太短。\n'})
    try:
        rc_short = main(short)
        assert rc_short == 1, f"过短清单应 FAIL，rc={rc_short}"
        print('[selftest] 边界·缺字段（清单过短 <500 字符）判红通路 OK')
    finally:
        shutil.rmtree(short, ignore_errors=True)

    # 边界·缺文件：清单整体缺失 → 同判据判红（空集不静默 PASS）
    missing = tempfile.mkdtemp(prefix='incident-register-selftest-miss-')
    try:
        rc_miss = main(missing)
        assert rc_miss == 1, f"清单缺失应 FAIL，rc={rc_miss}"
        print('[selftest] 边界·缺文件（事故清单不存在）判红通路 OK')
    finally:
        shutil.rmtree(missing, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
