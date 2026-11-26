#!/usr/bin/env python3
"""门禁 ㊲ 「验收通过」字样守卫（O6，修 C4）。

为什么：⑦ 曾报 16 个 docs 含「验收通过」，而独立验收刚判 6 单里 5 单不过——自报不可信，
因为没有任何守卫规定这个字样必须带判据证据（C4）。
判据：docs/ 下每个含「验收通过」的文件，必须同时含判据锚（`判据` / `逐字` / `PASS` / `行号` 任一），
      否则 FAIL（空口自称通过）。文件名以 `验收-` 开头的豁免判据检查之外还须含上述锚。
"""
import sys
from pathlib import Path

ANCHORS = ('判据', '逐字', 'PASS', '行号', 'commit')


def main(repo: str) -> int:
    base = Path(repo) / 'docs'
    bad = []
    hit = 0
    for f in base.rglob('*.md'):
        try:
            txt = f.read_text(encoding='utf-8', errors='strict')
        except OSError:
            continue
        except UnicodeDecodeError as e:
            print(f'FAIL {f.relative_to(base)}：非法 UTF-8（第 {e.start} 字节起），不得按可读文件判 PASS')
            return 1
        if '验收通过' not in txt:
            continue
        hit += 1
        if not any(a in txt for a in ANCHORS):
            bad.append(str(f.relative_to(base)))
    if bad:
        print(f'ACCEPT-CLAIM: FAIL（{len(bad)}/{hit} 个文件自称「验收通过」却无任何判据锚）')
        for b in bad[:8]:
            print('  ' + b)
        return 1
    print(f'ACCEPT-CLAIM: PASS（{hit} 个含「验收通过」的文件均带判据锚）')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
