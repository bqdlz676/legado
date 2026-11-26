#!/usr/bin/env python3
"""门禁 ㊱ 处置锚点防洗白（O4，修 C7）。

为什么：`docs/台账-本地编译违规.md` 里 `处置锚点 epoch=<秒>` 一改，之前所有 gradle 痕迹就失效，
等于给了自己一个免检开关（C7）。锚点必须"处置一次、推一次"，且之后不许再冒违规。
判据：锚点行之后若出现新的违规数据行（`|` 开头且非表头/分隔线）→ FAIL（说明有未处置违规）；
      同一台账里出现 ≥2 个锚点行 → FAIL（锚点即处置，一次只能一个当前值，多写=掩盖历史）。
"""
import re
import shutil
import sys
import tempfile
from pathlib import Path


def main(repo: str) -> int:
    p = Path(repo) / 'docs' / '台账-本地编译违规.md'
    if not p.exists():
        print('ANCHOR-INTEGRITY: PASS（无台账）')
        return 0
    try:
        raw = p.read_text(encoding='utf-8', errors='strict')
    except UnicodeDecodeError as e:
        print(f'FAIL {p.name}：非法 UTF-8（第 {e.start} 字节起），不得按可读文件判 PASS')
        return 1
    lines = raw.splitlines()
    anchors = [i for i, ln in enumerate(lines) if re.search(r'处置锚点\s*epoch=\d+', ln)]
    if len(anchors) > 1:
        print(f'ANCHOR-INTEGRITY: FAIL（台账里有 {len(anchors)} 个处置锚点，一次只许一个当前值，多写=掩盖历史）')
        return 1
    if not anchors:
        print('ANCHOR-INTEGRITY: WARN（无处置锚点，㉝ 将按时间窗判定）')
        return 0
    a = anchors[0]
    late = []
    for i in range(a + 1, len(lines)):
        ln = lines[i].strip()
        if not ln.startswith('|'):
            continue
        cells = [c.strip() for c in ln.strip('|').split('|')]
        if not cells or set(''.join(cells)) <= set('-: '):
            continue
        if any(k in ''.join(cells) for k in ('违规', '代理', '路径')):  # 表头
            continue
        late.append(f'{p.name}:{i + 1}: {ln[:70]}')
    if late:
        print(f'ANCHOR-INTEGRITY: FAIL（锚点之后仍有 {len(late)} 条未处置违规记录，锚点=洗白）')
        for x in late[:5]:
            print('  ' + x)
        return 1
    print('ANCHOR-INTEGRITY: PASS（锚点是最后一条处置，之后无新违规）')
    return 0


def _make_repo(ledger: str | None) -> str:
    """临时目录构造夹具仓库；ledger 为 None 时不建台账（缺文件边界）。"""
    repo = tempfile.mkdtemp(prefix='anchor-integrity-selftest-')
    if ledger is not None:
        d = Path(repo) / 'docs'
        d.mkdir(parents=True, exist_ok=True)
        (d / '台账-本地编译违规.md').write_text(ledger, encoding='utf-8')
    return repo


LEDGER_HEADER = ('# 台账-本地编译违规\n\n'
                 '| 日期 | 代理 | 路径 | 备注 |\n'
                 '| --- | --- | --- | --- |\n')


def selftest() -> int:
    """双向自测：锚点为最后一条处置 → main() rc=0；多锚点 / 锚点后仍有数据行 → rc=1。
    夹具 tempfile.mkdtemp + rmtree 自删，判据回调生产 main(repo)。"""
    # 好样本：单锚点且其后无数据行
    good = _make_repo(LEDGER_HEADER +
                      '| 2026-11-26 | s_old | docs/旧记录.md | 历史留档 |\n\n'
                      '处置锚点 epoch=1764200000\n')
    try:
        rc_good = main(good)
        assert rc_good == 0, f"锚点收尾的台账应 PASS，rc={rc_good}"
        print('[selftest] 好样本（单锚点且其后无新违规）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一：台账里出现 ≥2 个处置锚点 → 命中「多写=掩盖历史」判据
    bad1 = _make_repo(LEDGER_HEADER +
                      '处置锚点 epoch=1764200000\n'
                      '处置锚点 epoch=1764299999\n')
    try:
        rc_b1 = main(bad1)
        assert rc_b1 == 1, f"双锚点应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（两个处置锚点）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad1, ignore_errors=True)

    # 坏样本二：单锚点之后仍有一条未处置数据行 → 命中「锚点=洗白」判据
    bad2 = _make_repo(LEDGER_HEADER +
                      '处置锚点 epoch=1764200000\n'
                      '| 2026-11-27 | s_new | 动作记录 | 未处置 |\n')
    try:
        rc_b2 = main(bad2)
        assert rc_b2 == 1, f"锚点后仍有数据行应 FAIL，rc={rc_b2}"
        print('[selftest] 坏样本二（锚点后仍有未处置数据行）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad2, ignore_errors=True)

    # 边界·缺字段：台账存在但无处置锚点 → 生产设计打 WARN 且 rc=0（下游时间窗守卫兜底），
    # 与 check-vacuous-pass 在册豁免口径一致；此处只记录该边界行为，不判红。
    no_anchor = _make_repo(LEDGER_HEADER +
                           '| 2026-11-27 | s_new | docs/x.md | 待处置 |\n')
    try:
        rc_na = main(no_anchor)
        assert rc_na == 0, f"无锚点现有设计应 WARN rc=0，rc={rc_na}"
        print('[selftest] 边界·缺锚点（WARN 放行，下游时间窗兜底）按现有设计记录')
    finally:
        shutil.rmtree(no_anchor, ignore_errors=True)

    # 边界·缺文件：台账不存在 → 生产设计 rc=0（存在性谓词，非空集假绿）。
    missing = _make_repo(None)
    try:
        rc_miss = main(missing)
        assert rc_miss == 0, f"无台账现有设计应 rc=0，rc={rc_miss}"
        print('[selftest] 边界·缺文件（无台账）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(missing, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
