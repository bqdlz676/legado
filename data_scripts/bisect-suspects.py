#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""bisect-suspects.py — CI 分片静默挂死嫌疑类二分兜底（Q-273）。

为什么有它：`find-hang-culprit.py` 只能从已落地的 job 日志里按 STARTED/终态配对，
点名「最后几条嫌疑」；挂死若发生在 job 启动 / 类静态初始化阶段，一行 STARTED 都没有，
取证器彻底瞎。本脚本在日志取证之外提供第二条通路：把嫌疑类清单净化后切成 a/b 两批，
每批单独一个 8 分钟熔断的 workflow job，哪批挂死就把嫌疑集折半，逐轮收敛。

口径（净化复用 `emit-shard-lists.py` 的同一套判据，不在本脚本另造过滤器）：
  - keep：普通 class 且源码含 `@Test`（含全限定名）/ 继承已知测试基类 → 进二分批。
  - category-tagged：打了 `@Category(...)`，由 rhino-eval-tests / slow-bench-tests 独立
    job 执行，不进四分片也不进二分批（挂死发生在分片 job 内，独立 job 的类不是嫌疑）。
  - drop：missing-source / non-class-decl / no-test-annotation / facade-no-test，
    与 emit 脚本五桶口径一致；无源残留不得带进二分批（否则空 `--tests` 匹配不到任何类，
    job 假绿，二分结论被污染）。

用法：
  python3 data_scripts/bisect-suspects.py                         # 默认读 /tmp/cl2/shard-2.txt
  python3 data_scripts/bisect-suspects.py --in /tmp/cl2/shard-1.txt --out-dir /tmp/b1
  python3 data_scripts/bisect-suspects.py --in-classes a.b.C1,a.b.C2
  python3 data_scripts/bisect-suspects.py --emit-workflow docs/ci-bisect-片段.yml
  python3 data_scripts/bisect-suspects.py --selftest

边界：
  - 输入空清单 / 净化后空集：明确报错退出（退出码 1），不生成空 job 片段——空 job 会假绿。
  - 嫌疑集 < 4 条：不再二分，全量写进 suspect-a.txt，提示「已收敛到小集合，读 STARTED 日志即可」。
  - 片段只写到 `--emit-workflow` 指定文件；本脚本绝不改 `.github/workflows/verify.yml`。

退出码：0 成功；1 空集拒绝出批；2 参数 / 输入文件异常。
"""
import argparse
import importlib.util
import math
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HERE = Path(__file__).resolve().parent

# 复用分片清单净化逻辑：同名文件带连字符，不能直接 import，按路径加载模块。
_spec = importlib.util.spec_from_file_location(
    'emit_shard_lists', HERE / 'emit-shard-lists.py')
emit = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(emit)

# 小于该条数不再二分：小集合全量跑一轮，直接读 STARTED 日志比再折半更快。
SMALL_SET = 4
DEFAULT_IN = '/tmp/cl2/shard-2.txt'
DEFAULT_FRAGMENT = ROOT / 'docs' / 'ci-bisect-片段.yml'


def read_classes(path: str):
    """读清单文件：去空行 / 去注释行 / 去重保序；文件不存在抛 SystemExit(2)。"""
    p = Path(path)
    if not p.is_file():
        raise SystemExit(f'::error::输入清单不存在: {path}')
    out = []
    seen = set()
    for line in p.read_text(encoding='utf-8', errors='replace').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if line not in seen:
            seen.add(line)
            out.append(line)
    return out


def parse_inline(text: str):
    """--in-classes 逗号分隔入参；空白与空段丢弃。"""
    out, seen = [], set()
    for part in text.split(','):
        part = part.strip()
        if part and part not in seen:
            seen.add(part)
            out.append(part)
    return out


def purify(classes):
    """逐条过 emit 净化判据。

    返回 (kept, report)：
      kept  — 进二分批的 FQCN 排序列表；
      report — dict：before / kept / category / dropped[{class,reason}] / reasons 计数。
    """
    kept, category, dropped = [], [], []
    for fqcn in classes:
        status, reason, _source = emit.classify(fqcn)
        if status == 'keep':
            kept.append(fqcn)
        elif status == 'category':
            category.append(fqcn)
        else:
            dropped.append({'class': fqcn, 'reason': reason})
    kept.sort()
    category.sort()
    dropped.sort(key=lambda d: d['class'])
    reasons = {}
    for d in dropped:
        reasons[d['reason']] = reasons.get(d['reason'], 0) + 1
    return kept, {
        'before': len(classes),
        'kept': len(kept),
        'category': len(category),
        'category_classes': category,
        'dropped': dropped,
        'dropped_by_reason': reasons,
    }


def split_bisect(kept):
    """二分两批。返回 (mode, a, b)：mode ∈ bisect / small。

    bisect：排序后前半 a（ceil）、后半 b（floor），并集守恒、两两不重叠；
    small：条数 < SMALL_SET，不折半，全部进 a，b 为空。
    """
    n = len(kept)
    if n < SMALL_SET:
        return 'small', list(kept), []
    half = math.ceil(n / 2)
    return 'bisect', kept[:half], kept[half:]


def write_list(path: Path, classes):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join(classes) + ('\n' if classes else ''), encoding='utf-8')


def render_fragment(mode, n_a, n_b):
    """生成可直接贴进 GitHub Actions 的 YAML 片段文本（不动 verify.yml）。

    单轮 8 分钟熔断（timeout-minutes: 8）；matrix side: [a, b] 两批并发各跑各的；
    --tests 只包含本批清单内的类；空清单步骤内显式报错，拒绝空跑假绿。
    """
    if mode == 'small':
        return f"""\
# 挂死二分取证片段（bisect-suspects.py 生成）——小集合模式：{n_a} 条不再折半，全量跑一轮读 STARTED 日志。
# 用法：并入某个 workflow 的 jobs: 下；suspect-a.txt 随 artifact 上传（名 bisect-suspects）。
# 注意：本片段是取证兜底，定位到 culprit 后撤下；不要长期留在 verify.yml。
on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  bisect-small:
    name: Bisect small suspect set ({n_a} classes)
    runs-on: ubuntu-latest
    timeout-minutes: 8
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      # 与 verify.yml 分片 job 同口径：runner 镜像预装 Temurin 21，本地取、零网络；
      # 片段不内置下载兜底（取证车道保持轻量），镜像缺 21 时直接红灯排环境。
      - name: Set up JDK 21 (preinstalled)
        run: |
          set -euo pipefail
          for H in "${{JAVA_HOME_21_X64:-}}" "${{JAVA_HOME_21_ARM64:-}}"; do
            if [ -n "$H" ] && [ -x "$H/bin/java" ]; then
              echo "JAVA_HOME=$H" >> "$GITHUB_ENV"
              echo "$H/bin" >> "$GITHUB_PATH"
              exit 0
            fi
          done
          echo "::error::runner 未预装 JDK21，二分片段未内置下载兜底"; exit 1

      - name: Download suspect list
        uses: actions/download-artifact@v4
        with:
          name: bisect-suspects
          path: class-lists

      - name: Run suspect batch (full small set)
        run: |
          set -uo pipefail
          mapfile -t CLASSES < "class-lists/suspect-a.txt"
          if [ ${{#CLASSES[@]}} -eq 0 ]; then
            echo "::error::嫌疑小集合清单为空，拒绝空跑（空 job 会假绿）"; exit 1
          fi
          ARGS=()
          for c in "${{CLASSES[@]}}"; do ARGS+=(--tests "$c"); done
          echo "small set: ${{#CLASSES[@]}} classes"
          ./gradlew :app:testAppDebugUnitTest -PexcludeRhino -PexcludeSlowBench \\
            --no-daemon --build-cache --stacktrace "${{ARGS[@]}}" 2>&1 | tee gradle-bisect-small.log
          exit "${{PIPESTATUS[0]}}"
"""

    return f"""\
# 挂死二分取证片段（bisect-suspects.py 生成）——二分模式：a/b 两批并发，8 分钟熔断。
# 用法：并入某个 workflow 的 jobs: 下；suspect-a.txt / suspect-b.txt 随 artifact 上传（名 bisect-suspects）。
# 判定：哪一批在 8 分钟内无 XML / 无终态被取消，culprit 就在哪一批；下一轮以该批清单重跑本脚本。
# 注意：本片段是取证兜底，定位到 culprit 后撤下；不要长期留在 verify.yml。
on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  bisect:
    name: Bisect side ${{{{ matrix.side }}}}
    runs-on: ubuntu-latest
    timeout-minutes: 8
    strategy:
      fail-fast: false
      matrix:
        side: [a, b]
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      # 与 verify.yml 分片 job 同口径：runner 镜像预装 Temurin 21，本地取、零网络；
      # 片段不内置下载兜底（取证车道保持轻量），镜像缺 21 时直接红灯排环境。
      - name: Set up JDK 21 (preinstalled)
        run: |
          set -euo pipefail
          for H in "${{JAVA_HOME_21_X64:-}}" "${{JAVA_HOME_21_ARM64:-}}"; do
            if [ -n "$H" ] && [ -x "$H/bin/java" ]; then
              echo "JAVA_HOME=$H" >> "$GITHUB_ENV"
              echo "$H/bin" >> "$GITHUB_PATH"
              exit 0
            fi
          done
          echo "::error::runner 未预装 JDK21，二分片段未内置下载兜底"; exit 1

      - name: Download suspect lists
        uses: actions/download-artifact@v4
        with:
          name: bisect-suspects
          path: class-lists

      - name: Run suspect batch
        run: |
          set -uo pipefail
          mapfile -t CLASSES < "class-lists/suspect-${{{{ matrix.side }}}}.txt"
          if [ ${{{{#CLASSES[@]}}}} -eq 0 ]; then
            echo "::error::嫌疑批 ${{{{ matrix.side }}}} 清单为空，拒绝空跑（空 job 会假绿）"; exit 1
          fi
          ARGS=()
          for c in "${{CLASSES[@]}}"; do ARGS+=(--tests "$c"); done
          echo "side ${{{{ matrix.side }}}}: ${{{{#CLASSES[@]}}}} classes"
          ./gradlew :app:testAppDebugUnitTest -PexcludeRhino -PexcludeSlowBench \\
            --no-daemon --build-cache --stacktrace "${{ARGS[@]}}" 2>&1 | tee "gradle-bisect-${{{{ matrix.side }}}}.log"
          exit "${{PIPESTATUS[0]}}"
"""


def run(classes, out_dir: Path, fragment_path: Path):
    """净化 → 二分 → 落盘清单与片段；返回统计 dict。空集抛 SystemExit(1)。"""
    if not classes:
        # 空输入直接拒绝：不生成任何清单 / 片段，避免下游拼出空 --tests 的假绿 job。
        raise SystemExit('::error::嫌疑清单为空，拒绝生成空二分 job（空 job 会假绿）')

    kept, report = purify(classes)
    if not kept:
        dropped_brief = ', '.join(
            f"{d['class']}({d['reason']})" for d in report['dropped'][:5])
        raise SystemExit(
            f'::error::净化后无可执行测试类（原始 {report["before"]} 条全部落桶：'
            f"{report['dropped_by_reason']}；样例 {dropped_brief}），拒绝生成空二分 job")

    mode, a, b = split_bisect(kept)
    out_dir.mkdir(parents=True, exist_ok=True)
    write_list(out_dir / 'suspect-a.txt', a)
    if mode == 'bisect':
        write_list(out_dir / 'suspect-b.txt', b)
    else:
        # 小集合模式不产 b 批：清掉可能存在的旧 suspect-b.txt，防止上一轮残留被误当本批。
        old_b = out_dir / 'suspect-b.txt'
        if old_b.exists():
            old_b.unlink()

    fragment_path.parent.mkdir(parents=True, exist_ok=True)
    fragment_path.write_text(render_fragment(mode, len(a), len(b)), encoding='utf-8')

    # 守恒断言：两批并集 == 净化全集、两两不重叠（与 emit 分片同口径，防折半丢类 / 串批）。
    if mode == 'bisect':
        if set(a) | set(b) != set(kept):
            raise SystemExit('::error::二分并集 != 净化后全集')
        if set(a) & set(b):
            raise SystemExit(f"::error::a/b 批存在重叠: {sorted(set(a) & set(b))[:5]}")

    return {
        'mode': mode,
        'before': report['before'],
        'kept': report['kept'],
        'category': report['category'],
        'dropped': report['dropped'],
        'dropped_by_reason': report['dropped_by_reason'],
        'n_a': len(a),
        'n_b': len(b),
        'out_dir': str(out_dir),
        'fragment': str(fragment_path),
    }


# ---------- 自测夹具 ----------

_FIXTURE_KT = (
    'package io.legado.app.fixture\n\nimport org.junit.Test\n\n'
    'class {simple} {{\n    @Test fun ok() = Unit\n}}\n'
)


def _make_fixture_root(tmp: Path, names):
    """在临时 TEST_ROOT 下为 names 写真测试源文件；返回 FQCN 列表。"""
    emit.TEST_ROOT = tmp / 'java'
    fqcns = []
    for i, simple in enumerate(names):
        fqcn = f'io.legado.app.fixture.{simple}'
        p = emit.TEST_ROOT / fqcn.replace('.', '/')
        p.parent.mkdir(parents=True, exist_ok=True)
        p.with_suffix('.kt').write_text(
            _FIXTURE_KT.format(simple=simple), encoding='utf-8')
        fqcns.append(fqcn)
    return fqcns


def selftest():
    """三组 /tmp 夹具：161 条均分 / 3 条小集合 / 含 1 条无源残留；另加空集报错断言。跑完自删。"""
    failures = []
    old_root = emit.TEST_ROOT
    tmp = Path(tempfile.mkdtemp(prefix='bisect-selftest-'))
    try:
        # 夹具 1：161 条全部有源 → 均分 81 / 80，守恒无重叠。
        names1 = [f'Good{i:03d}Test' for i in range(161)]
        classes1 = _make_fixture_root(tmp / 'f1', names1)
        out1 = tmp / 'out1'
        frag1 = tmp / 'frag1.yml'
        s1 = run(classes1, out1, frag1)
        if s1['mode'] != 'bisect' or s1['n_a'] != 81 or s1['n_b'] != 80:
            failures.append(f"夹具1 分批条数错误: {s1['n_a']}/{s1['n_b']} mode={s1['mode']}")
        a1 = (out1 / 'suspect-a.txt').read_text().splitlines()
        b1 = (out1 / 'suspect-b.txt').read_text().splitlines()
        if len(a1) != 81 or len(b1) != 80:
            failures.append(f"夹具1 落盘条数错误: a={len(a1)} b={len(b1)}")
        if set(a1) & set(b1) or set(a1) | set(b1) != set(classes1):
            failures.append('夹具1 二分并集/重叠守恒断言失败')
        if 'side: [a, b]' not in frag1.read_text() or 'timeout-minutes: 8' not in frag1.read_text():
            failures.append('夹具1 片段缺 matrix side 或 8 分钟熔断')

        # 夹具 2：3 条小集合 → 不折半，全量进 a，b 文件不产出。
        classes2 = _make_fixture_root(tmp / 'f2', ['SmallOneTest', 'SmallTwoTest', 'SmallThreeTest'])
        out2 = tmp / 'out2'
        frag2 = tmp / 'frag2.yml'
        s2 = run(classes2, out2, frag2)
        if s2['mode'] != 'small' or s2['n_a'] != 3 or s2['n_b'] != 0:
            failures.append(f"夹具2 小集合判定错误: mode={s2['mode']} a={s2['n_a']} b={s2['n_b']}")
        if (out2 / 'suspect-b.txt').exists():
            failures.append('夹具2 小集合模式不应产出 suspect-b.txt')
        if 'bisect-small' not in frag2.read_text():
            failures.append('夹具2 片段应为 small 单 job 形态')

        # 夹具 3：5 条里 1 条无源残留 → 剔除；剩 4 条均分 2 / 2；残留不得进任何一批。
        names3 = ['KeepA01Test', 'KeepA02Test', 'KeepB01Test', 'KeepB02Test']
        classes3 = _make_fixture_root(tmp / 'f3', names3)
        ghost = 'io.legado.app.fixture.GhostMissingTest'
        classes3.append(ghost)
        out3 = tmp / 'out3'
        frag3 = tmp / 'frag3.yml'
        s3 = run(classes3, out3, frag3)
        if s3['kept'] != 4 or s3['n_a'] != 2 or s3['n_b'] != 2:
            failures.append(f"夹具3 净化/分批错误: kept={s3['kept']} a={s3['n_a']} b={s3['n_b']}")
        if s3['dropped_by_reason'].get('missing-source') != 1:
            failures.append(f"夹具3 无源残留计数错误: {s3['dropped_by_reason']}")
        all3 = (out3 / 'suspect-a.txt').read_text() + (out3 / 'suspect-b.txt').read_text()
        if ghost in all3:
            failures.append('夹具3 无源残留泄漏进二分批')

        # 附加反向断言：空输入必须报错退出，且不落任何清单 / 片段。
        out4 = tmp / 'out4'
        try:
            run([], out4, tmp / 'frag4.yml')
            failures.append('空清单未报错退出')
        except SystemExit as e:
            if '空' not in str(e):
                failures.append(f'空清单退出信息未点明空集: {e}')
        if out4.exists() and any(out4.iterdir()):
            failures.append('空清单报错后仍产出了文件')

    finally:
        emit.TEST_ROOT = old_root
        shutil.rmtree(tmp, ignore_errors=True)

    if failures:
        print('SELFTEST FAIL:')
        for f in failures:
            print(f'  - {f}')
        return 1
    print('PASS 3/3 selftest 夹具全判定正确：161 条均分 81/80 守恒 / 3 条小集合不折半 / '
          '无源残留剔除且不泄漏；空清单报错退出不落空 job；夹具已自删。')
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description='挂死嫌疑类二分兜底（Q-273）')
    src = ap.add_mutually_exclusive_group()
    src.add_argument('--in', dest='inp', default=DEFAULT_IN,
                     help=f'嫌疑类清单文件（默认 {DEFAULT_IN}）')
    src.add_argument('--in-classes', help='逗号分隔的 FQCN 内联清单')
    ap.add_argument('--out-dir', default='/tmp/bisect', help='两批清单输出目录')
    ap.add_argument('--emit-workflow', default=str(DEFAULT_FRAGMENT),
                    help='GitHub Actions 片段文件输出路径（只写文件，不改 verify.yml）')
    ap.add_argument('--selftest', action='store_true', help='跑内置 /tmp 夹具自测，跑完自删')
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()

    if args.in_classes:
        classes = parse_inline(args.in_classes)
    else:
        classes = read_classes(args.inp)

    stats = run(classes, Path(args.out_dir), Path(args.emit_workflow))

    print(f"原始嫌疑={stats['before']}  净化保留={stats['kept']}  "
          f"分类打标(独立job, 排除)={stats['category']}  剔除={len(stats['dropped'])}")
    if stats['dropped_by_reason']:
        print(f"剔除分因={stats['dropped_by_reason']}")
    if stats['mode'] == 'small':
        print(f"已收敛到小集合（< {SMALL_SET} 条）：全量 {stats['n_a']} 条进 suspect-a.txt；"
              '不再折半，直接跑一轮读 STARTED 日志即可。')
    else:
        print(f"二分两批：suspect-a.txt={stats['n_a']} 条，suspect-b.txt={stats['n_b']} 条")
    print(f"清单目录={stats['out_dir']}  片段文件={stats['fragment']}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
