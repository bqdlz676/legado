#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁 61 Compose 手写陷阱静态守卫（2026-11-27 编译面三连红复盘产物）。

为什么需要它：本仓沙箱禁本地编译（唯一可信编译源是 CI），而 `kotlin-syntax-lint.py`
只判括号配平 / 尾逗号 / 符号出处等文本层形态；下面三类错误在纯文本层面完全合法，
既有 lint 全 PASS，只有 kotlinc 在 CI 上才报 ERROR。每一类都已在本轮 B2/B3 真实烧过
一轮完整 CI，故压成可机械判定的前置闸，push 前拦住。

三类判据（全部 ERROR，计入失败；不设只报 WARN 的规则——永远绿的检查器是装饰）：

  A 顶层 import 了只存在于 scope 接口的 Compose 扩展符号。
    背景（ShellBottomBar.kt:14 事故）：`matchParentSize` 是 `BoxScope` 成员扩展，
    没有可 import 的顶层版本；写 `import androidx.compose.foundation.layout.matchParentSize`
    必报 `Unresolved reference 'matchParentSize'`。调用点在 Box 内容 lambda 内由
    BoxScope 隐式接收者直接解析，本来就不需要 import。
    名单 SCOPE_ONLY 均为 RowScope/ColumnScope/BoxScope 的成员扩展，判定依据写在表注里。

  B 非 Composable 事件 lambda 体内出现 Composable 调用标记。
    背景（B3SettingGroups.kt 11 处事故）：`stringResource` / `remember` 是 @Composable
    函数，写进 `onClick = { ... }` 这类事件 lambda（非 Composable 上下文）必报
    `@Composable invocations can only happen from the context of a @Composable function`。
    修法：标题/提示提到 composable 作用域取成 val，事件 lambda 只引用 val。
    判据只认事件回调形参名名单；普通 Composable 内容 lambda（`content`/尾随块）不在名单内。

  C 动画 spec 工厂裸类型参数且声明处无使用点推型。
    背景（MainShell.kt:93 事故）：`val fadeSpec = tween(durationMillis=…, easing=…)`
    声明处没有使用点可推类型参数 T，报 `Cannot infer type for type parameter 'T'.
    Specify it explicitly.`；修法 `tween<Float>(…)`。
    判据只抓「val/var 声明 RHS、无显式类型注解、工厂调用又无 `<T>`」的形态；
    作为外层调用实参（如 `animationSpec = tween(...)`）时 T 由外层形参类型推断，不报。

误报控制（反例白名单，详见复盘报告第四节）：
  - BoxScope 内合法调用 `Modifier.matchParentSize()`、RowScope 内 `.weight(1f)` 不查——
    规则 A 只查 import 行，不查调用点；
  - `stringResource`/`remember` 写在 @Composable 帮助函数体或 composable 作用域的
    val 初值里合法，规则 B 只进事件 lambda 花括号体内判定；
  - 泛型可由实参/外层形参推断时无需显式类型参，规则 C 只抓裸声明 RHS；
  - 同名本地辅助函数（测试里的 `private fun snap(...)`/`spring()`）与 Compose 工厂撞名，
    文件未 import `androidx.compose.animation.core` 时规则 C 整文件跳过，本地 fun 声明名
    再剔除一遍，避免把纯 JVM 测试辅助函数误判成 Compose 工厂。

用法：python3 data_scripts/check-compose-pitfalls.py [源码根 ...]
  不给根时默认扫 `app/src` 与 `screenshot/src`（多 module 全集，跨模块漏扫见
  docs/复盘-跨模块改名漏扫-2026-11-27.md）。
退出码：0 = 无 ERROR；1 = 有 ERROR（供 rule-gate 直接当硬门禁）。
"""
import os
import re
import shutil
import sys
import tempfile

# ---- 规则 A：scope 接口成员扩展名单（判定依据逐条写在值里）----
# 为什么用名单而非全量推导：Kotlin 的接收者作用域类型无法靠纯文本解析；名单只收本仓
# 事故与 Compose 常见成员扩展，宁可漏报也不把合法顶层 import 误杀。
SCOPE_ONLY = {
    # BoxScope 成员：仅 Box 内容 lambda 内由隐式 BoxScope 接收者解析，包内无顶层声明。
    'matchParentSize': 'BoxScope 成员扩展，无可导入顶层声明；调用点在 Box 内容 lambda 内直接解析',
    # RowScope/ColumnScope 成员：等宽权重只在 Row/Column 内容 lambda 内可用。
    'weight': 'RowScope/ColumnScope 成员扩展，无可导入顶层声明',
    # BoxScope/RowScope/ColumnScope 成员：随接收者作用域解析，不是顶层 Modifier 扩展。
    'align': 'BoxScope/RowScope/ColumnScope 成员扩展，无可导入顶层声明',
    'alignBy': 'RowScope/ColumnScope 成员扩展，无可导入顶层声明',
    'alignByBaseline': 'RowScope 成员扩展，无可导入顶层声明',
    'alignByOffset': 'RowScope 成员扩展，无可导入顶层声明',
}

# ---- 规则 B：非 Composable 事件 lambda 形参名名单 ----
# 为什么只列事件回调：这些形参类型是 `() -> Unit` / `(T) -> Unit` 等普通函数类型，
# 运行在非 Composable 上下文；Composable 内容 lambda（content/label/尾随块）语义相反，
# 不进名单，避免把合法的 `Box { remember { ... } }` 误杀。
EVENT_LAMBDAS = [
    'onClick', 'onValueChange', 'onDismissRequest', 'onChange', 'onCheckedChange',
    'onSelect', 'onConfirm', 'onCancel', 'onPick', 'onBackClick',
]
# 这些标记是 @Composable 函数，出现在事件 lambda 体内即编译期硬约束违规。
COMPOSABLE_MARKERS = [
    'stringResource(', 'remember(', 'rememberSaveable(', 'painterResource(',
]

# ---- 规则 C：Compose 动画 spec 工厂（均为泛型 `<T>`）----
ANIM_FACTORIES = ['tween', 'snap', 'spring', 'keyframes', 'repeatable', 'infiniteRepeatable']
ANIM_CORE_IMPORT = re.compile(r'^import\s+androidx\.compose\.animation\.core(\.\*)?', re.M)
LOCAL_FUN_RE = re.compile(r'\bfun\s+(' + '|'.join(ANIM_FACTORIES) + r')\s*\(')
FACTORY_CALL_RE = re.compile(
    r'\b(' + '|'.join(ANIM_FACTORIES) + r')\s*(<[^>]*>)?\s*\(')
# val/var 声明头：名字后到 = 之间允许显式类型注解 `: Type`。
DECL_RE = re.compile(r'\b(?:val|var)\s+([A-Za-z_]\w*)\s*(:\s*[^=\n]+)?=\s*')

DEFAULT_ROOTS = ['app/src', 'screenshot/src']


def strip_noise(src: str) -> str:
    """去注释与字符串字面量，保留换行与花括号配平，便于按作用域提取 lambda 体。

    为什么保留换行：报错行号要能回源到原文件；字符串/注释里的样例代码不得参与判定。
    """
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n':
                i += 1
        elif c == '/' and i + 1 < n and src[i + 1] == '*':
            i += 2
            while i + 1 < n and not (src[i] == '*' and src[i + 1] == '/'):
                out.append('\n' if src[i] == '\n' else ' ')
                i += 1
            i += 2
        elif c == '"':
            triple = src[i:i + 3] == '"""'
            i += 3 if triple else 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if triple and src[i] == '"':
                    run = 0
                    while i + run < n and src[i + run] == '"':
                        run += 1
                    if run >= 3:
                        i += run
                        break
                    i += 1
                    continue
                if not triple and src[i] == '"':
                    i += 1
                    break
                i += 1
        elif c == "'":
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == "'":
                    i += 1
                    break
                i += 1
        elif c == '`':
            i += 1
            while i < n and src[i] != '`':
                i += 1
            if i < n:
                i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def match_brace(src: str, open_idx: int) -> int:
    """从 `{` 位置配对到对应 `}`，返回右括号下标；配不平返回 -1。"""
    depth, i = 0, open_idx
    while i < len(src):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def line_of(src: str, idx: int) -> int:
    return src.count('\n', 0, idx) + 1


def scan_rule_a(clean: str, errors: list):
    """A：import 行末段命中 scope-only 成员扩展名单 → ERROR。"""
    for m in re.finditer(r'(?m)^import\s+([\w.]+?)(?:\s+as\s+(\w+))?\s*$', clean):
        fq, alias = m.group(1), m.group(2)
        name = alias or fq.rsplit('.', 1)[-1]
        if name in SCOPE_ONLY:
            errors.append((line_of(clean, m.start()), 'A',
                           '顶层 import 了 scope 成员扩展 `%s`（%s）；该符号由接收者作用域解析，'
                           '无可导入顶层声明，删掉此 import 即可' % (name, SCOPE_ONLY[name])))


def scan_rule_b(clean: str, errors: list):
    """B：事件 lambda 体内出现 Composable 调用标记 → ERROR。"""
    # 具名事件 lambda：`onClick = { ... }`
    for ev in EVENT_LAMBDAS:
        for m in re.finditer(r'\b' + ev + r'\s*=\s*\{', clean):
            body_start = m.end() - 1
            body_end = match_brace(clean, body_start)
            if body_end == -1:
                continue
            body = clean[body_start + 1:body_end]
            hit = [mk for mk in COMPOSABLE_MARKERS if mk in body]
            if hit:
                errors.append((line_of(clean, m.start()), 'B',
                               '事件 lambda `%s` 体内出现 Composable 调用 %s；'
                               '须把文案/状态提到 composable 作用域取成 val，lambda 内只引用 val'
                               % (ev, '、'.join(hit))))
    # pointerInput：形参本身是普通 lambda（接收者 PointerInputScope，非 Composable）。
    for m in re.finditer(r'\bpointerInput\s*\([^()]*\)\s*\{', clean):
        body_start = m.end() - 1
        body_end = match_brace(clean, body_start)
        if body_end == -1:
            continue
        body = clean[body_start + 1:body_end]
        hit = [mk for mk in COMPOSABLE_MARKERS if mk in body]
        if hit:
            errors.append((line_of(clean, m.start()), 'B',
                           '`pointerInput` lambda 体内出现 Composable 调用 %s；'
                           '手势回调不在 Composable 上下文，remember/stringResource 须移到外层'
                           % '、'.join(hit)))


def scan_rule_c(clean: str, errors: list):
    """C：动画工厂裸类型参且落在无类型注解的 val/var RHS → ERROR。"""
    if not ANIM_CORE_IMPORT.search(clean):
        return  # 未引入 Compose 动画包：撞名的是本地纯 JVM 辅助函数，整文件跳过
    local_funs = set(LOCAL_FUN_RE.findall(clean))
    for m in FACTORY_CALL_RE.finditer(clean):
        name, type_args = m.group(1), m.group(2)
        if name in local_funs:
            continue  # 同文件本地 fun 声明撞名，不是 Compose 工厂调用
        if type_args:
            continue  # 已显式 `<T>`，编译器可直接取型
        # 向前找最近的 val/var 声明头：仅当本调用是其 RHS 才可能缺推型来源。
        pre = clean[:m.start()]
        decl = None
        for dm in DECL_RE.finditer(pre):
            seg = pre[dm.end():m.start()]
            # 跨过未闭合的 `{`/`}` 视为进入新作用域，旧声明头不再覆盖本调用点。
            if '{' in seg or '}' in seg:
                continue
            # 声明 = 与调用点之间若夹着括号/逗号，说明本调用是外层调用的实参
            # （如 `animateFloatAsState(animationSpec = tween(...))`），T 由外层形参推，不报。
            if any(ch in seg for ch in '(),[]'):
                continue
            decl = dm
        if decl is None:
            continue  # 使用点实参形态：T 由外层形参类型推断，不报
        if decl.group(2):
            continue  # 声明带显式类型注解（如 `: AnimationSpec<Float>`），可推 T
        errors.append((line_of(clean, m.start()), 'C',
                       '动画工厂 `%s(...)` 裸类型参数且声明 `%s` 无类型注解，编译器无法推断 T；'
                       '写显式 `%s<Float>(...)` 或补声明类型' % (name, decl.group(1), name)))


def scan_file(path: str, src: str):
    """返回 errors：(行号, 规则, 消息)；规则 A/B/C 全部计入失败。"""
    clean = strip_noise(src)
    errors = []
    scan_rule_a(clean, errors)
    scan_rule_b(clean, errors)
    scan_rule_c(clean, errors)
    return errors


def main(roots) -> int:
    bad = []
    kt_count = 0
    for root in roots:
        if not os.path.isdir(root):
            continue
        for base, _, files in os.walk(root):
            for fn in files:
                if not fn.endswith('.kt'):
                    continue
                p = os.path.join(base, fn)
                kt_count += 1
                with open(p, encoding='utf-8', errors='ignore') as fh:
                    src = fh.read()
                for ln, rule, msg in scan_file(p, src):
                    bad.append((p, ln, rule, msg))
    if bad:
        print('COMPOSE-PITFALLS: FAIL（%d 处）' % len(bad))
        for p, ln, rule, msg in bad:
            print('  %s:%d [%s] %s' % (p, ln, rule, msg))
        return 1
    print('COMPOSE-PITFALLS: PASS（%d 个 Kotlin 文件，A/B/C 三类形态 0 命中）' % kt_count)
    return 0


def _write_kt(root: str, rel: str, src: str) -> str:
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w', encoding='utf-8').write(src)
    return p


def selftest() -> int:
    """双向自测：好样本（合法 scope 调用 / 作用域取 val / 显式类型参 / 使用点推型）PASS；
    三类坏样本各 FAIL；本地撞名辅助函数不误报。夹具 tempfile 自删，判定回调生产 main()。"""
    # 好样本：三类边界全部放行
    good = tempfile.mkdtemp(prefix='compose-pitfalls-selftest-')
    try:
        _write_kt(good, 'main/Good.kt',
                  'import androidx.compose.animation.core.tween\n'
                  'import androidx.compose.foundation.layout.Box\n'
                  'import androidx.compose.foundation.layout.Column\n'
                  'import androidx.compose.foundation.layout.Row\n'
                  'import androidx.compose.foundation.layout.fillMaxWidth\n'
                  'import androidx.compose.runtime.Composable\n'
                  'import androidx.compose.ui.Modifier\n'
                  'import androidx.compose.ui.res.stringResource\n\n'
                  '@Composable\nfun GoodRow(onClick: () -> Unit) {\n'
                  '    val title = stringResource(R.string.x)\n'  # composable 作用域取 val，合法
                  '    Row {\n'
                  '        Column {\n'
                  '            Box(modifier = Modifier.matchParentSize()) { }\n'  # scope 成员直接解析，合法
                  '            Modifier.weight(1f)\n'
                  '        }\n'
                  '        Button(onClick = { onClick() }) { }\n'  # 事件 lambda 无 Composable 标记，合法
                  '    }\n'
                  '    val spec: AnimationSpec<Float> = tween(200)\n'  # 声明类型注解可推 T，合法
                  '    Box(animationSpec = tween(200)) { }\n'  # 使用点实参由外层形参推 T，合法
                  '    val fadeSpec = tween<Float>(durationMillis = 300)\n'  # 显式类型参，合法
                  '}\n')
        rc_good = main([good])
        assert rc_good == 0, f"好样本应 PASS，rc={rc_good}"
        print('[selftest] 好样本（scope 成员裸调用 / 作用域取 val / 显式类型参 / 使用点推型）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本 A：顶层 import BoxScope 成员扩展 matchParentSize → FAIL
    bad_a = tempfile.mkdtemp(prefix='compose-pitfalls-selftest-')
    try:
        _write_kt(bad_a, 'main/BadA.kt',
                  'import androidx.compose.foundation.layout.matchParentSize\n'
                  'import androidx.compose.runtime.Composable\n\n'
                  '@Composable\nfun Bar() { }\n')
        rc = main([bad_a])
        assert rc == 1, f"scope-only 顶层 import 应 FAIL，rc={rc}"
        print('[selftest] 坏样本 A（顶层 import matchParentSize）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad_a, ignore_errors=True)

    # 坏样本 B：onClick lambda 体内调 stringResource → FAIL
    bad_b = tempfile.mkdtemp(prefix='compose-pitfalls-selftest-')
    try:
        _write_kt(bad_b, 'main/BadB.kt',
                  'import androidx.compose.runtime.Composable\n'
                  'import androidx.compose.ui.res.stringResource\n\n'
                  '@Composable\nfun Bar(onClick: () -> Unit) {\n'
                  '    Button(onClick = { stringResource(R.string.x) }) { }\n'
                  '}\n')
        rc = main([bad_b])
        assert rc == 1, f"事件 lambda 内 stringResource 应 FAIL，rc={rc}"
        print('[selftest] 坏样本 B（onClick 内 stringResource）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad_b, ignore_errors=True)

    # 坏样本 C：val RHS 裸 tween、无类型注解、无显式 <T> → FAIL
    bad_c = tempfile.mkdtemp(prefix='compose-pitfalls-selftest-')
    try:
        _write_kt(bad_c, 'main/BadC.kt',
                  'import androidx.compose.animation.core.tween\n'
                  'import androidx.compose.runtime.Composable\n\n'
                  '@Composable\nfun Bar() {\n'
                  '    val fadeSpec = tween(durationMillis = 300)\n'
                  '}\n')
        rc = main([bad_c])
        assert rc == 1, f"裸 tween 声明应 FAIL，rc={rc}"
        print('[selftest] 坏样本 C（val RHS 裸 tween 无类型参）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad_c, ignore_errors=True)

    # 边界样本：纯 JVM 测试里本地 fun snap/spring 撞名，未引动画包 → 必须 PASS，不误报
    edge = tempfile.mkdtemp(prefix='compose-pitfalls-selftest-')
    try:
        _write_kt(edge, 'test/Edge.kt',
                  'class Edge {\n'
                  '    private fun snap(base: Int, slot: Int): Int = base + slot\n'
                  '    private fun spring() = PeelSpring()\n'
                  '    fun case() {\n'
                  '        val gy = snap(1, 2)\n'
                  '        val s = spring()\n'
                  '    }\n'
                  '}\n')
        rc = main([edge])
        assert rc == 0, f"本地撞名辅助函数应 PASS，rc={rc}"
        print('[selftest] 边界样本（本地 fun snap/spring 撞名、无动画包 import）不误报通路 OK')
    finally:
        shutil.rmtree(edge, ignore_errors=True)

    # 边界·空集：目录内无 .kt 文件 → 零违规真绿，现有设计 PASS
    empty = tempfile.mkdtemp(prefix='compose-pitfalls-selftest-empty-')
    try:
        rc_empty = main([empty])
        assert rc_empty == 0, f"无 kt 文件现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（无 .kt 源码）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    roots = sys.argv[1:] or DEFAULT_ROOTS
    sys.exit(main(roots))
