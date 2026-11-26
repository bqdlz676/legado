#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""emit-shard-lists.py — discover 阶段测试类清单净化与四分片（Q-268）。

为什么有它：旧 discover 只按编译产物 *.class 枚举，bench 引擎 `object`、报告 `data class`、
JUnit 分类标记 `interface`、Kotlin 文件 facade（`*Kt`）、TestRule 辅助类全部混进清单，
被原样拼成 `--tests`；这些类 JUnit 根本不执行，既拖慢 Gradle 类路径扫描，又让分片负载空转。
本脚本在分片之前按磁盘源码逐条净化，四片只保留真正会被 JUnit 执行的普通 `class`。

过滤判据（逐条短路，命中即落桶）：
  1. missing-source：FQCN 找不到对应 .kt/.java 源码（编译残留 / 改名未清产物）。
  2. non-class-decl：声明是 object / interface / data class / sealed / enum / abstract，
     不是可被 JUnit 实例化执行的普通 class。
  3. no-test-annotation：源码里既无 `@Test`（含全限定 `@org.junit.Test`），也不继承已知测试基类。
  4. facade-no-test：`*Kt` 文件 facade 对应源码里没有顶层 `@Test` 函数。
  5. category-tagged：打了 `@Category(...)`，由 rhino-eval-tests / slow-bench-tests 独立 job 执行，
     不进四分片清单；但仍写进 manifest.classes，供 aggregate 跨 job 归属核对。

误杀保护（反例推演见 docs/修复-分片清单净化.md §五）：
  - `@Test` 扫描整份文件文本：`companion object` 内的 `@Test` 不会被误杀；
  - 注解匹配同时接受短名 `@Test` 与全限定 `@org.junit.Test`；
  - 已知测试基类经 TEST_BASE_SUPERCLASSES 扩展（当前仓内无抽象测试基类，留空集合）。

用法：
  CI discover：python3 data_scripts/emit-shard-lists.py --from-build --out-dir .
  回放校验：  python3 data_scripts/emit-shard-lists.py --from-lists /tmp/cl2/shard-1.txt ... --out-dir /tmp/out
  自测：      python3 data_scripts/emit-shard-lists.py --selftest
退出码：0 成功；1 净化后空集（拒绝空分片）；2 参数/枚举异常。
"""
import argparse
import glob
import json
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# 单元测试源码根；CI 编译产物与之一一对应（androidTest 不进本清单）。
TEST_ROOT = ROOT / 'app' / 'src' / 'test' / 'java'

# 编译产物里恒定不存在测试方法的合成类（沿用旧 discover 口径，避免 R 类/模块信息进清单）。
SKIP_BASE = {'R.class', 'BuildConfig.class', 'module-info.class'}

# 已知可执行测试基类：子类即使自身文件没写 @Test，继承基类用例也会被 JUnit 执行。
# 当前仓内无抽象测试基类；后续若引入 JUnit4 基类（如带 @Before/helper 的 abstract class），在此登记。
TEST_BASE_SUPERCLASSES = set()

# 声明头：注解行（可多行）+ 修饰符 + 种类 + 类名。修饰符单独捕获，便于识别 data/sealed/enum/abstract。
DECL_HEAD = re.compile(
    r'^\s*((?:@\w+(?:\([^)]*\))?\s*)*)'
    r'((?:(?:public|internal|private|protected|abstract|open|final|sealed|data|enum|value)\s+)*)'
    r'(object|interface|class)\s+(\w+)'
)
# @Test 同时匹配短名与全限定名；\b 排除 @TestFactory / @TestParameterized 等同类前缀注解。
TEST_ANNOT = re.compile(r'@(?:org\.junit\.)?Test\b')
# 分类标记：括号可换行，只取注解起点即可。
CATEGORY_ANNOT = re.compile(r'@Category\s*\(')


def decl_matches(text: str, simple: str):
    """返回文件中与 simple 同名的声明列表：[(modifiers:set, kind:str, line:str), ...]。"""
    hits = []
    for line in text.splitlines():
        m = DECL_HEAD.match(line)
        if m and m.group(4) == simple:
            mods = set(m.group(2).split())
            hits.append((mods, m.group(3), line.strip()))
    return hits


def resolve_source(fqcn: str):
    """FQCN → (源码 Path, 来源种类)；找不到返回 (None, 'missing')。

    来源种类：kt / java 同名文件；facade（*Kt 剥尾）；nested（同名类与别的声明共享文件，
    按包目录扫描命中）；missing（无源残留）。
    """
    rel = fqcn.replace('.', '/')
    simple = fqcn.rsplit('.', 1)[-1]
    direct = [
        (TEST_ROOT / f'{rel}.kt', 'kt'),
        (TEST_ROOT / f'{rel}.java', 'java'),
    ]
    # Kotlin 文件 facade：顶层函数/属性编译为 `<FileName>Kt`，剥掉 Kt 后缀回找源文件。
    if simple.endswith('Kt'):
        direct.append((TEST_ROOT / f'{rel[:-2]}.kt', 'facade'))
    for path, kind in direct:
        if path.is_file():
            return path, kind
    # 顶层 data class / 辅助类与主声明同文件：在包目录内按声明名扫描兜底。
    pkg_dir = TEST_ROOT / (rel.rsplit('/', 1)[0] if '/' in rel else '')
    if pkg_dir.is_dir():
        for p in pkg_dir.rglob('*.kt'):
            text = p.read_text(encoding='utf-8', errors='ignore')
            if decl_matches(text, simple):
                return p, 'nested'
    return None, 'missing'


def _rel(path: Path) -> str:
    """相对 ROOT 显示；临时夹具在 /tmp 下无法 relative_to，回退绝对路径避免抛错。"""
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def classify(fqcn: str):
    """判定单个 FQCN。返回 (status, reason, source)；status ∈ keep / category / drop。"""
    path, kind = resolve_source(fqcn)
    if path is None:
        return 'drop', 'missing-source', ''
    text = path.read_text(encoding='utf-8', errors='ignore')
    simple = fqcn.rsplit('.', 1)[-1]

    # 分类打标类：由独立 job 按 includeCategories 执行，分片清单剔除（仍入 manifest）。
    if CATEGORY_ANNOT.search(text):
        return 'category', 'category-tagged', _rel(path)

    hits = decl_matches(text, simple)
    if kind == 'facade':
        # 文件 facade 没有同名 class 声明；可执行性只看文件里有无顶层 @Test 函数。
        if TEST_ANNOT.search(text):
            return 'keep', 'facade-with-top-level-test', _rel(path)
        return 'drop', 'facade-no-test', _rel(path)

    if not hits:
        # 同名声明不在任何可解析文件里：按无源残留处理，避免把孤儿编译产物带进清单。
        return 'drop', 'decl-not-found', _rel(path)

    mods, decl_kind, line = hits[0]
    # 不可被 JUnit 实例化执行的声明种类：object / interface / data / sealed / enum / abstract。
    if decl_kind in ('object', 'interface') or mods & {'data', 'sealed', 'enum', 'abstract'}:
        return 'drop', 'non-class-decl', f'{_rel(path)} :: {line}'

    has_test = TEST_ANNOT.search(text) is not None
    # 基类继承兜底：声明头冒号后的超类型集合命中已知测试基类即保留。
    inherits_base = any(base in line.split(':', 1)[-1] for base in TEST_BASE_SUPERCLASSES)
    if not has_test and not inherits_base:
        return 'drop', 'no-test-annotation', _rel(path)
    return 'keep', 'plain-class-with-test', _rel(path)


def enumerate_build_classes():
    """CI 模式：复刻旧 discover 的编译产物枚举（Kotlin + Java 两种变体落盘位置通配兜底）。"""
    roots = glob.glob(str(ROOT / 'app/build/tmp/kotlin-classes/*UnitTest*')) + \
            glob.glob(str(ROOT / 'app/build/intermediates/javac/*UnitTest*/classes'))
    classes = set()
    for root in roots:
        for path in glob.glob(f'{root}/**/*.class', recursive=True):
            rel = Path(path).relative_to(root)
            base = rel.name
            if '$' in base:  # 内部类按外层归属，不单独成条。
                continue
            if base in SKIP_BASE:
                continue
            classes.add(str(rel)[:-6].replace('/', '.'))
    return sorted(classes)


def read_list_files(files):
    """回放模式：从既有分片清单读 FQCN（去空行、去重、排序交由后续统一处理）。"""
    out = set()
    for f in files:
        for line in Path(f).read_text(encoding='utf-8').splitlines():
            line = line.strip()
            if line:
                out.add(line)
    return sorted(out)


def shard_round_robin(classes, n=4):
    """排序后 round-robin 切片，与旧 discover 分片语义一致；并集守恒、两两不重叠。"""
    cls = sorted(classes)
    shards = [sorted(cls[i::n]) for i in range(n)]
    union = set().union(*map(set, shards))
    if union != set(cls):
        raise SystemExit('::error::分片并集 != 净化后全量清单')
    for a in range(n):
        for b in range(a + 1, n):
            overlap = set(shards[a]) & set(shards[b])
            if overlap:
                raise SystemExit(f'::error::分片 {a + 1} 与 {b + 1} 存在重叠类: {sorted(overlap)[:5]}')
    return shards


def emit(classes, out_dir: Path):
    """净化 → 分片 → 落盘 shard-N.txt 与 manifest.json；返回统计字典。"""
    kept, category, dropped = [], [], []
    for fqcn in classes:
        status, reason, source = classify(fqcn)
        if status == 'keep':
            kept.append(fqcn)
        elif status == 'category':
            category.append(fqcn)
        else:
            dropped.append({'class': fqcn, 'reason': reason, 'source': source})
    kept.sort()
    category.sort()
    dropped.sort(key=lambda d: d['class'])

    if not kept:
        # 拒绝空分片：净化后一条可执行类都没有，说明源码树或解析口径坏了，必须红灯而非空跑。
        raise SystemExit('::error::净化后无可执行测试类，拒绝生成空分片清单')

    shards = shard_round_robin(kept)
    out_dir.mkdir(parents=True, exist_ok=True)
    for i, shard in enumerate(shards, start=1):
        (out_dir / f'shard-{i}.txt').write_text('\n'.join(shard) + '\n', encoding='utf-8')

    # manifest.classes = 分片类 + 分类打标类；aggregate 据此核对 XML 归属，分类类在独立 job 出报告。
    manifest = {
        'total': len(kept) + len(category),
        'classes': sorted(kept + category),
        'shard_classes': kept,
        'category_classes': category,
        'dropped': dropped,
    }
    (out_dir / 'manifest.json').write_text(
        json.dumps(manifest, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')

    reason_counts = {}
    for d in dropped:
        reason_counts[d['reason']] = reason_counts.get(d['reason'], 0) + 1
    return {
        'before': len(classes),
        'kept': len(kept),
        'category': len(category),
        'dropped': len(dropped),
        'dropped_by_reason': reason_counts,
        'shard_sizes': [len(s) for s in shards],
        'manifest_total': manifest['total'],
    }


def selftest():
    """临时源码树双向夹具：真测试保留、伪条目剔除、companion/全限定名不误杀。"""
    cases = {
        'io.legado.app.GoodTest': (
            'package io.legado.app\n\nimport org.junit.Test\n\n'
            'class GoodTest {\n    @Test fun ok() = Unit\n}\n'),
        'io.legado.app.CompanionTest': (
            'package io.legado.app\n\nimport org.junit.Test\n\n'
            'class CompanionTest {\n'
            '    companion object {\n        @Test fun staticOk() = Unit\n    }\n}\n'),
        'io.legado.app.QualifiedTest': (
            'package io.legado.app\n\n'
            'class QualifiedTest {\n    @org.junit.Test fun ok() = Unit\n}\n'),
        'io.legado.app.BenchEngine': (
            'package io.legado.app\n\nobject BenchEngine {\n    fun run() = Unit\n}\n'),
        'io.legado.app.ReportData': (
            'package io.legado.app\n\ndata class ReportData(val x: Int)\n'),
        'io.legado.app.MarkerIface': (
            'package io.legado.app\n\ninterface MarkerIface\n'),
        'io.legado.app.RuleHelper': (
            'package io.legado.app\n\nimport org.junit.rules.TestRule\n\n'
            'class RuleHelper : TestRule\n'),
        'io.legado.app.CatTest': (
            'package io.legado.app\n\nimport org.junit.Test\nimport org.junit.experimental.categories.Category\n'
            '@Category(MarkerIface::class)\nclass CatTest {\n    @Test fun ok() = Unit\n}\n'),
    }
    with tempfile.TemporaryDirectory() as tmp:
        global TEST_ROOT
        old_root = TEST_ROOT
        TEST_ROOT = Path(tmp) / 'java'
        try:
            for fqcn, text in cases.items():
                p = TEST_ROOT / fqcn.replace('.', '/')
                p.parent.mkdir(parents=True, exist_ok=True)
                p.with_suffix('.kt').write_text(text, encoding='utf-8')
            expect_keep = {'io.legado.app.GoodTest', 'io.legado.app.CompanionTest',
                           'io.legado.app.QualifiedTest'}
            expect_category = {'io.legado.app.CatTest'}
            expect_drop = {'io.legado.app.BenchEngine', 'io.legado.app.ReportData',
                           'io.legado.app.MarkerIface', 'io.legado.app.RuleHelper'}
            for fqcn in cases:
                status, reason, _ = classify(fqcn)
                want = 'keep' if fqcn in expect_keep else ('category' if fqcn in expect_category else 'drop')
                if status != want:
                    print(f'SELFTEST: FAIL {fqcn} 判定 {status}({reason})，期望 {want}')
                    return 1
            print('PASS 1/4 普通类/companion/全限定 @Test 保留')
            print('PASS 2/4 object/data class/interface/Rule 辅助类剔除')
            print('PASS 3/4 @Category 打标类移入独立桶')
            # 孤儿 FQCN：源码不存在必须落 missing-source，不得假保留。
            status, reason, _ = classify('io.legado.app.GhostTest')
            if status != 'drop' or reason != 'missing-source':
                print(f'SELFTEST: FAIL 孤儿类判定 {status}({reason})，期望 drop/missing-source')
                return 1
            print('PASS 4/4 无源残留判 missing-source')
            return 0
        finally:
            TEST_ROOT = old_root


def main(argv=None):
    ap = argparse.ArgumentParser(description='净化测试类清单并切四分片（Q-268）')
    src = ap.add_mutually_exclusive_group()
    src.add_argument('--from-build', action='store_true', help='从编译产物枚举类（CI discover）')
    src.add_argument('--from-lists', nargs='+', metavar='FILE', help='从既有分片清单回放过滤')
    ap.add_argument('--out-dir', default='.', help='分片清单与 manifest 输出目录')
    ap.add_argument('--selftest', action='store_true', help='跑内置双向夹具自测')
    args = ap.parse_args(argv)

    if args.selftest:
        return selftest()
    if not args.from_build and not args.from_lists:
        ap.error('必须二选一：--from-build 或 --from-lists')

    if args.from_build:
        classes = enumerate_build_classes()
        if not classes:
            print('::error::未枚举到任何测试类（编译产物目录缺失？）', file=sys.stderr)
            return 2
    else:
        classes = read_list_files(args.from_lists)

    stats = emit(classes, Path(args.out_dir))
    print(f"过滤前条数={stats['before']}  保留={stats['kept']}  "
          f"分类打标(独立job)={stats['category']}  剔除={stats['dropped']}")
    print(f"剔除分因={stats['dropped_by_reason']}")
    print(f"四片新条数={stats['shard_sizes']}  manifest总数={stats['manifest_total']}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
