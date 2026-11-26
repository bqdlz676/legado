#!/usr/bin/env python3
"""门禁 ㊳ 子代理熔断（Q-22 结单，用户原话「不能超过15分钟」）。

为什么：条文一直在册（15 分钟无产物即判废、写≤40/验收≤60 预算），但从未接门禁，
派单也没写这两字段——所以它只是"记得"。这条把它变成机械判据。
判据：读 `docs/派单队列.md` 的 RUNNING 行，按创建时间算已存活分钟：
      >60 → FAIL（明显超预算，须 kill 或补产物）；>15 → WARN（熔断线，看有没有产物回执）。
边界：队列来自人工登记，无回执时间就无法判"无产物"；因此本项只判时长，产物由 ㉚/⑳ 覆盖。
"""
import contextlib
import datetime
import io
import re
import shutil
import sys
import tempfile
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


def _make_repo(rows: list[str] | None) -> str:
    """临时目录构造夹具仓库；rows 为 None 时不建队列文件（空集边界）。"""
    repo = tempfile.mkdtemp(prefix='agent-timeout-selftest-')
    if rows is not None:
        d = Path(repo) / 'docs'
        d.mkdir(parents=True, exist_ok=True)
        header = ('| 序号 | 标题 | 创建时间 | 状态 | 依赖 | 文件域 |\n'
                  '| --- | --- | --- | --- | --- | --- |\n')
        (d / '派单队列.md').write_text(header + '\n'.join(rows) + '\n', encoding='utf-8')
    return repo


def _row(qid: str, created: str, status: str) -> str:
    return f'| {qid} | 自测单 | {created} | {status} | - | docs/x.md |'


def selftest() -> int:
    """双向自测：新鲜 RUNNING → rc=0；超 60 分钟 RUNNING → rc=1；15~60 分钟 → WARN rc=0。
    夹具 tempfile.mkdtemp + rmtree 自删，判据回调生产 main(repo)。"""
    now = datetime.datetime.now()
    fresh = now.strftime('%Y-%m-%d %H:%M')
    old = (now - datetime.timedelta(minutes=120)).strftime('%Y-%m-%d %H:%M')
    warm = (now - datetime.timedelta(minutes=30)).strftime('%Y-%m-%d %H:%M')

    # 好样本：新鲜 RUNNING，未越熔断线
    good = _make_repo([_row('Q-T1', fresh, 'RUNNING')])
    try:
        rc_good = main(good)
        assert rc_good == 0, f"新鲜 RUNNING 应 PASS，rc={rc_good}"
        print('[selftest] 好样本（新鲜 RUNNING）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本：RUNNING 已存活 120 分钟（>60 熔断 FAIL 线）→ 命中 FAIL 判据
    bad = _make_repo([_row('Q-T2', old, 'RUNNING')])
    try:
        rc_bad = main(bad)
        assert rc_bad == 1, f"超 60 分钟 RUNNING 应 FAIL，rc={rc_bad}"
        print('[selftest] 坏样本（RUNNING 存活 120 分钟 > 60）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad, ignore_errors=True)

    # WARN 通路：RUNNING 存活 30 分钟（>15 熔断提示线、≤60 FAIL 线）→ 打 WARN 但 rc=0
    warn = _make_repo([_row('Q-T3', warm, 'RUNNING')])
    try:
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc_warn = main(warn)
        out_warn = buf.getvalue()
        assert rc_warn == 0, f"30 分钟 RUNNING 应 WARN rc=0，rc={rc_warn}"
        assert 'WARN' in out_warn, f"应打 WARN 提示，输出：{out_warn}"
        print('[selftest] WARN 通路（RUNNING 30 分钟 >15 提示线）提示通路 OK')
    finally:
        shutil.rmtree(warn, ignore_errors=True)

    # 边界·缺字段：RUNNING 行创建时间不可解析 → 生产设计跳过该行（无法判时长），rc=0
    malformed = _make_repo([_row('Q-T4', '待补', 'RUNNING')])
    try:
        rc_mf = main(malformed)
        assert rc_mf == 0, f"时间戳缺字段应跳过放行，rc={rc_mf}"
        print('[selftest] 边界·缺字段（创建时间不可解析，跳过该行）按现有设计记录')
    finally:
        shutil.rmtree(malformed, ignore_errors=True)

    # 边界·空集：队列文件缺失 → stats 返回空行集，无 RUNNING 可判，现有设计 rc=0。
    # 空集只发生在夹具/异常仓库；真实队列缺失由 dispatch-queue 等队列门禁兜底。
    empty = _make_repo(None)
    try:
        rc_empty = main(empty)
        assert rc_empty == 0, f"无队列文件现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（无派单队列）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
