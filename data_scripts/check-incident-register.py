#!/usr/bin/env python3
"""门禁 ㉛ 事故清单完整性守卫。

为什么需要：事故之前散落在 复盘-*.md / CI归因-*.md / 台账-子代理存活.md 里，
"发生过什么"只能靠人翻；一旦某条事故没进唯一清单，下一棒就会重犯。
判据：①docs/CI归因-*.md 中 conclusion=failure 的每个 run id 必须出现在 docs/事故清单.md；
     ②台账-子代理存活.md 中每个"判废"的代理 id 必须出现在清单；
     ③清单必须存在且非空。
"""
import re
import sys
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


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
