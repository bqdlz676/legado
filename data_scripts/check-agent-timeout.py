#!/usr/bin/env python3
"""门禁 ㊳ 子代理熔断（Q-22 结单，用户原话「不能超过15分钟」）。

为什么：条文一直在册（15 分钟无产物即判废、写≤40/验收≤60 预算），但从未接门禁，
派单也没写这两字段——所以它只是"记得"。这条把它变成机械判据。
判据：读 `docs/派单队列.md` 的 RUNNING 行，按创建时间算已存活分钟：
      >60 → FAIL（明显超预算，须 kill 或补产物）；>15 → WARN（熔断线，看有没有产物回执）。
边界：队列来自人工登记，无回执时间就无法判"无产物"；因此本项只判时长，产物由 ㉚/⑳ 覆盖。
"""
import datetime
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from queue_stats import stats  # noqa: E402

FAIL_MIN, WARN_MIN = 60, 15


def main(repo: str) -> int:
    st = stats(str(Path(repo) / 'docs' / '派单队列.md'))
    now = datetime.datetime.now()
    fails, warns = [], []
    for r in st['rows']:
        if r['kind'] != 'RUNNING':
            continue
        m = re.match(r'(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})', r['created'])
        if not m:
            continue
        ts = datetime.datetime.strptime(f'{m.group(1)} {m.group(2)}', '%Y-%m-%d %H:%M')
        age = (now - ts).total_seconds() / 60.0
        if age > FAIL_MIN:
            fails.append(f"{r['id']} 已跑 {age:.0f} 分钟（> {FAIL_MIN}），须 kill 或补产物")
        elif age > WARN_MIN:
            warns.append(f"{r['id']} 已跑 {age:.0f} 分钟（> {WARN_MIN} 熔断线）")
    for w in warns:
        print('WARN  ' + w)
    for f in fails:
        print('FAIL  ' + f)
    if fails:
        return 1
    print(f'AGENT-TIMEOUT: PASS（RUNNING {len(warns)} 条在熔断线内或已提示）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
