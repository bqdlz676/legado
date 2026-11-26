#!/usr/bin/env python3
"""门禁 ㊻：第一读顺序合规（用户 2026-11-26 口令「把总控必读定为以后第一读」）。

为什么做成机检：阅读顺序是"人是否真的先读到全部纪律"的唯一开关，写在哪一份文件里都能被下一次改版悄悄改回去；
只有机检能保证"必读仍是第一读"这件事不被漂移。
判据（三处声明必须同时成立，缺一即 FAIL）：
  ① `docs/机制-总控必读.md` 头部声明「开工第一读就是本文件」；
  ② `docs/落文-规矩总纲.md` 声明「第一读＝docs/机制-总控必读.md」；
  ③ `docs/接手/00-入口.md` 有「第一读顺序」条目。
适用条件：任何改这三份文件头部/入口的轮次；平时由门禁 ㊻ 每次推送体检时跑。
退出码：0 合规 / 1 违规
"""
import pathlib
import re
import shutil
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
FILES = {
    '总控必读': ('docs/机制-总控必读.md', '开工第一读就是本文件'),
    '规矩总纲': ('docs/落文-规矩总纲.md', '第一读＝`docs/机制-总控必读.md`'),
    '接手入口': ('docs/接手/00-入口.md', '第一读顺序'),
}
HEAD_LINES = 20  # 只看文件头部，防止正文里的举例被当成声明（总纲声明在 §一 最高优先级行 :13）


def scan(root, head_only=True):
    miss = []
    for label, (rel, needle) in FILES.items():
        p = root / rel
        if not p.exists():
            miss.append('%s 文件不存在（%s）' % (label, rel))
            continue
        text = p.read_text(encoding='utf-8')
        if head_only and label != '接手入口':
            text = '\n'.join(text.splitlines()[:HEAD_LINES])
        if needle not in text:
            miss.append('%s 缺第一读声明：未找到「%s」' % (label, needle))
    return miss


def report(miss):
    if miss:
        print('FIRST-READ: FAIL（%d 项）' % len(miss))
        for m in miss:
            print('  - ' + m)
        return 1
    print('FIRST-READ: PASS（必读第一读 / 总纲第二读 / 入口顺序 三处声明一致）')
    return 0


def selftest():
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='firstread-'))
    for rel, _ in FILES.values():
        src = ROOT / rel
        dst = tmp / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(src, dst)
    if scan(tmp):
        print('SELFTEST: FAIL（副本起点就不合规）')
        return 1
    print('PASS 1/4 副本起点合规')
    p = tmp / FILES['总控必读'][0]
    p.write_text(p.read_text(encoding='utf-8').replace('开工第一读就是本文件', '开工先读总纲', 1), encoding='utf-8')
    if not scan(tmp):
        print('SELFTEST: FAIL（改坏必读后仍 PASS，机检无效）')
        return 1
    print('PASS 2/4 改坏必读被判 FAIL')
    shutil.copy(ROOT / FILES['总控必读'][0], p)
    q = tmp / FILES['规矩总纲'][0]
    q.write_text(q.read_text(encoding='utf-8').replace('第一读＝`docs/机制-总控必读.md`', '第一读＝总纲', 1), encoding='utf-8')
    if not scan(tmp):
        print('SELFTEST: FAIL（总纲顺序声明被改后仍 PASS）')
        return 1
    print('PASS 3/4 总纲顺序被改被判 FAIL')
    shutil.rmtree(tmp)
    print('PASS 4/4 临时副本已删')
    print('SELFTEST: PASS（双向成立）')
    return 0


def main():
    if '--selftest' in sys.argv:
        return selftest()
    return report(scan(ROOT))


if __name__ == '__main__':
    sys.exit(main())
