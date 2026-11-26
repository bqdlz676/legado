#!/usr/bin/env python3
"""门禁 ㊲ 「验收通过」字样守卫（O6，修 C4）。

为什么：⑦ 曾报 16 个 docs 含「验收通过」，而独立验收刚判 6 单里 5 单不过——自报不可信，
因为没有任何守卫规定这个字样必须带判据证据（C4）。
判据：docs/ 下每个含「验收通过」的文件，必须同时含判据锚（`判据` / `逐字` / `PASS` / `行号` 任一），
      否则 FAIL（空口自称通过）。文件名以 `验收-` 开头的豁免判据检查之外还须含上述锚。
"""
import shutil
import sys
import tempfile
from pathlib import Path

ANCHORS = ('判据', '逐字', 'PASS', '行号', 'commit')


def main(repo: str) -> int:
    base = Path(repo) / 'docs'
    bad = []
    hit = 0
    for f in base.rglob('*.md'):
        try:
            txt = f.read_text(encoding='utf-8', errors='strict')
        except OSError as e:
            # 读不了的文件不许静默跳过（M-49）：跳过＝判 PASS，而"读不到"恰恰最该红。
            print(f'FAIL {f.relative_to(base)}：读取失败（{type(e).__name__}: {e}），不得按不可读文件判 PASS')
            return 1
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


def _make_repo(files: dict) -> str:
    """临时目录构造夹具仓库：files 为 {docs 下相对路径: str 或 bytes}。"""
    repo = tempfile.mkdtemp(prefix='accept-claim-selftest-')
    d = Path(repo) / 'docs'
    d.mkdir(parents=True, exist_ok=True)
    for rel, content in files.items():
        p = d / rel
        if isinstance(content, bytes):
            p.write_bytes(content)
        else:
            p.write_text(content, encoding='utf-8')
    return repo


def selftest() -> int:
    """双向自测：自称验收通过且带判据锚 → main() rc=0；无锚 / 非法 UTF-8 → rc=1。
    夹具 tempfile.mkdtemp + rmtree 自删，判据回调生产 main(repo)。"""
    # 好样本：含「验收通过」且带 PASS 锚；另有一份不含该字样的笔记应被跳过
    good = _make_repo({
        'good.md': '# 修复-x\n本单验收通过，PASS 依据见 data_scripts/check-accept-claim.py:20。\n',
        'plain.md': '# 笔记\n本文不出现结论通过字样，应被跳过。\n',
    })
    try:
        rc_good = main(good)
        assert rc_good == 0, f"带判据锚的自报应 PASS，rc={rc_good}"
        print('[selftest] 好样本（验收通过 + PASS 锚）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一：自称验收通过但无任何判据锚 → 命中空口自称判据
    bad1 = _make_repo({'bad1.md': '# 自报\n本单验收通过，效果很好，无需证据。\n'})
    try:
        rc_b1 = main(bad1)
        assert rc_b1 == 1, f"无判据锚应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（验收通过却无判据锚）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad1, ignore_errors=True)

    # 坏样本二：文档为非法 UTF-8 字节 → 命中「读不了不许静默跳过」判据
    bad2 = _make_repo({'bad2.md': b'# bad\n\xff\xfe ' + '验收通过'.encode('utf-8') + b'\n'})
    try:
        rc_b2 = main(bad2)
        assert rc_b2 == 1, f"非法 UTF-8 应 FAIL，rc={rc_b2}"
        print('[selftest] 坏样本二（非法 UTF-8 文档）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad2, ignore_errors=True)

    # 边界·空集：docs 下无任何 .md → 现有设计按 0 个含字样文件放行 rc=0。
    # 空集只发生在夹具/异常仓库；真实 docs 恒有存量报告，接线漏传由其余 diff 型门禁兜底。
    empty = tempfile.mkdtemp(prefix='accept-claim-selftest-empty-')
    (Path(empty) / 'docs').mkdir(parents=True, exist_ok=True)
    try:
        rc_empty = main(empty)
        assert rc_empty == 0, f"空 docs 现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（docs 无 .md）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else '.'))
