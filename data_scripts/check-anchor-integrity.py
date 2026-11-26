#!/usr/bin/env python3
"""门禁 ㊱ 处置锚点防洗白（O4，修 C7）。

为什么：`docs/台账-本地编译违规.md` 里 `处置锚点 epoch=<秒>` 一改，之前所有 gradle 痕迹就失效，
等于给了自己一个免检开关（C7）。锚点必须"处置一次、推一次"，且之后不许再冒违规。
判据：锚点行之后若出现新的违规数据行（`|` 开头且非表头/分隔线）→ FAIL（说明有未处置违规）；
      同一台账里出现 ≥2 个锚点行 → FAIL（锚点即处置，一次只能一个当前值，多写=掩盖历史）。
"""
import re
import sys
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


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
