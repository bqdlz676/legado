#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁接线检查（54）——判据存在但从不执行＝假绿，2026-11-26 穷举审计发现两个孤儿门禁。

为什么需要它：`data_scripts/check-*.py` 里有脚本写了"为什么需要它"却不被任何编排器调用，
等于判据只存在于文件系统。人眼审计只会在被追问时偶然发现，必须机械化。
判据：每个 check-*.py 必须至少被 rule-gate.sh / pre-push-check.sh / .github/workflows/*.yml
之一以 `check-xxx.py` 字面调用；不满足者进基线（基线只许缩小，不允许新增孤儿）。
豁免口径：被其他 check-*.py import/复制执行的（如 check-onboarding 被 ㊽ 复用）算接线。
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / 'data_scripts'
BASELINE = SCRIPTS / 'wiring-baseline.json'
ORCHESTRATORS = [SCRIPTS / 'rule-gate.sh', SCRIPTS / 'pre-push-check.sh']
ORCHESTRATORS += sorted((ROOT / '.github' / 'workflows').glob('*.yml'))


def wired_text() -> str:
    parts = [p.read_text(encoding='utf-8', errors='strict') for p in ORCHESTRATORS if p.is_file()]
    # 间接接线：任何 check-*.py 里调用/复制别的 check 脚本，也算被接线（它会随外层一起跑）
    for p in SCRIPTS.glob('check-*.py'):
        parts.append(p.read_text(encoding='utf-8', errors='strict'))
    return '\n'.join(parts)


def main() -> int:
    names = {p.name for p in SCRIPTS.glob('check-*.py')}
    text = wired_text()
    orphans = set()
    for n in names:
        pat = re.compile(rf'(?<![A-Za-z0-9_-]){re.escape(n)}(?![A-Za-z0-9_-])')
        hits = [m for m in pat.finditer(text)]
        # 只在自己文件里出现＝没被任何编排器拉起
        self_only = True
        for h in hits:
            line_start = text.rfind('\n', 0, h.start()) + 1
            line_end = text.find('\n', h.end())
            line = text[line_start: line_end if line_end != -1 else len(text)]
            if not line.lstrip().startswith('#') and '---' not in line:
                self_only = False
                break
        if self_only:
            orphans.add(n)
    base = set(json.loads(BASELINE.read_text(encoding='utf-8'))) if BASELINE.is_file() else set()
    grown = sorted(orphans - base)
    fixed = sorted(base - orphans)
    if grown:
        print('GATE-WIRING: FAIL（孤儿门禁未接线且不在基线）')
        for n in grown:
            print(f'  - data_scripts/{n} 未出现在 rule-gate.sh / pre-push-check.sh / workflows')
        return 1
    if fixed:
        print(f'INFO 孤儿已减少 {len(fixed)} 个，请把基线改小：{fixed}')
        BASELINE.write_text(json.dumps(sorted(orphans), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(f'GATE-WIRING: PASS（基线已自动收缩到 {len(orphans)} 个）')
        return 0
    print(f'GATE-WIRING: PASS（{len(names)} 个门禁均接线；历史孤儿基线 {len(base)} 个）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
