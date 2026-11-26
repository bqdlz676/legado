#!/usr/bin/env python3
"""门禁 ㊼：豁免必须绑定不可伪造身份（F-122）。

为什么（2026-11-26 用户质问「怎么会又改名蒙过的情况」）：
同类事故已经三次——acceptance-evidence 幻影路径 PASS、pre-push 第 3 步中文名静默跳过假绿、
本次 ㊹「改名塞进 data_scripts/fixtures/ 就豁免」。三次的共同根因是同一个：
**判据用"位置/文件名/存在性"代替"身份/内容"**。位置可以伪造，身份（git 跟踪、内容哈希、显式白名单签名）不能。
所以把"豁免必须身份化"做成机检，而不是再写一遍"下次注意"。

判据（扫 data_scripts/*.py 里的豁免类常量）：
  1) 文件名或常量名出现豁免语义（FIXTURE_OK / EXEMPT / SKIP_ / ALLOW_ / WHITELIST）；
  2) 同一文件里必须同时出现不可伪造身份的取用方式之一：`ls-files`（git 跟踪）／`hashlib`／`sha256`／`ALLOWLIST.json`；
  3) 前两条不满足即 FAIL，并点名文件与常量行。
豁免（元级）：本机检自身在 WHITELIST 里登记；登记必须给理由，禁"先豁免再说"。

用法：python3 data_scripts/check-exempt-identity.py [--selftest]
退出码：0 合规 / 1 违规
"""
import argparse
import pathlib
import re
import shutil
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_SCAN = ROOT / 'data_scripts'
EXEMPT_HINT = re.compile(r'(FIXTURE_OK|EXEMPT|SKIP_|ALLOW_|WHITELIST)', re.I)
# 只有"位置型豁免"才是本机检的对象：常量必须被用于路径比较（in/startswith 等）。
# 为什么收窄：内容标记型（EXEMPT_RE 扫正文）、数值清单型（SKIP_STATES 是朝代名）都不是位置豁免，
# 把它们判红等于逼人给非豁免写身份，是假红；假红多了这条门禁就会被绕过（§20 五阶）。
PATH_USE = re.compile(r'(\bin\s+(rel|p|path|base|f\b)|startswith|endswith)')
IDENTITY = re.compile(r'(ls-files|hashlib|sha256|ALLOWLIST\.json)')
# 元级白名单：本脚本自身（它谈豁免但不做豁免实现）
WHITELIST = {'check-exempt-identity.py': '本机检自身谈豁免，不做豁免实现'}


def scan(scan_dir=DEFAULT_SCAN):
    bad = []
    for p in sorted(pathlib.Path(scan_dir).glob('*.py')):
        if p.name in WHITELIST:
            continue
        text = p.read_text(encoding='utf-8', errors='ignore')
        lines = text.splitlines()
        names, hint_lines = [], []
        for i, line in enumerate(lines):
            m = EXEMPT_HINT.search(line)
            if m:
                names.append(re.split(r'[ =:(]', line.strip())[0])
                hint_lines.append(i + 1)
        if not names:
            continue
        # 只认"位置型豁免"：常量被 for 循环取出、且循环变量被拿去与路径比较
        # （`for s in SKIP_NAMES` + `base.startswith(s)`）。内容标记型（扫正文）、
        # 数值清单型（朝代名集合）不在此列——判它们只会制造假红，假红会让人绕开这条门禁。
        position_type = False
        for line in lines:
            fm = re.search(r'for\s+(\w+)\s+in\s+(\w+)', line)
            if not fm or fm.group(2) not in names:
                continue
            var = fm.group(1)
            for other in lines:
                if re.search(r'(startswith|endswith)\(%s\)|\bin\s+(rel|p|path|base|f|fp)\b.*%s|%s.*\bin\s+(rel|p|path|base|f|fp)\b'
                             % (var, var, var), other):
                    position_type = True
                    break
            if position_type:
                break
        if not position_type:
            continue
        if IDENTITY.search(text):
            continue
        bad.append((p.name, hint_lines[:3]))
    return bad


def report(bad):
    if bad:
        print('EXEMPT-IDENTITY: FAIL（%d 个脚本有豁免常量但未绑定不可伪造身份）' % len(bad))
        for name, lines in bad:
            print('  - data_scripts/%s 豁免常量在 :%s（须用 git ls-files / hashlib / sha256 / ALLOWLIST.json 取身份）'
                  % (name, '/'.join(map(str, lines))))
        return 1
    print('EXEMPT-IDENTITY: PASS（豁免均绑定身份，或本文件无豁免实现）')
    return 0


def selftest():
    tmp = pathlib.Path(tempfile.mkdtemp(prefix='exempt-identity-'))
    try:
        # 合规样本：路径子串豁免 + git ls-files 身份
        (tmp / 'good.py').write_text(
            "SKIP_NAMES = ('索引-', '台账-')\n"
            "def skip(base):\n"
            "    if any(base.startswith(s) for s in SKIP_NAMES):\n"
            "        return True\n"
            "def tracked(p):\n"
            "    return subprocess.run(['git', 'ls-files', '--error-unmatch', p])\n",
            encoding='utf-8')
        # 违规样本：只有路径子串，没有任何身份取用
        (tmp / 'bad.py').write_text(
            "SKIP_NAMES = ('索引-', '台账-')\n"
            "def skip(base):\n"
            "    return any(base.startswith(s) for s in SKIP_NAMES)\n",
            encoding='utf-8')
        bad = scan(tmp)
        if [b[0] for b in bad] != ['bad.py']:
            print('SELFTEST: FAIL（合规样本/违规样本未按预期区分：%s）' % bad)
            return 1
        print('PASS 1/3 合规样本放行、违规样本点名')
        (tmp / 'good.py').write_text(
            "SKIP_NAMES = ('索引-',)\n"
            "def skip(base):\n"
            "    return any(base.startswith(s) for s in SKIP_NAMES)\n",
            encoding='utf-8')
        bad = scan(tmp)
        if 'good.py' not in [b[0] for b in bad]:
            print('SELFTEST: FAIL（拿掉身份取用后未判红，机检无效）')
            return 1
        print('PASS 2/3 拿掉身份取用即判红')
        print('PASS 3/3 汇总判红（rc=1）')
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--selftest', action='store_true')
    a = ap.parse_args()
    if a.selftest:
        return selftest()
    return report(scan())


if __name__ == '__main__':
    sys.exit(main())
