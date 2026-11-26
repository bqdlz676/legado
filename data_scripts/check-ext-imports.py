#!/usr/bin/env python3
"""扩展函数 import 守卫。

为什么需要这个脚本：Compose 里大量能力是**扩展函数**（`InfiniteTransition.animateFloat`、
`Modifier.clip`、`Modifier.drawBehind`、`Modifier.offset` …），它们的可见性完全依赖
`import androidx.compose.…`。IDE 补全会自动插入 import，但**手写/子代理写字时漏了 import
在语法上依然"看起来正常"**，只有 CI 编译才报 `Unresolved reference 'x' on receiver of type`。
本轮 CI 连红两次都是这一类（CardBase 的 `animateFloat`、TopicCard 的 `clip`），故落成机械检查。

判法（保守，宁少报不多报）：
- 只查"点调用"形态：`.name(` 或 `receiver.name(`；
- 只查下面 KEXT 白名单里的名字（白名单＝我们确实用到的 Compose 扩展），命中即要求
  文件里存在 `import <包>.<name>`；
- 先剔除行注释、块注释、字符串字面量，避免"注释里提了一嘴"造成假报（`CardTokens.kt` 的
  `drawBehind` 就只在 KDoc 里出现过，误报会让守卫失去可信度）；
- 同一文件里若已 `import … as 别名`，按别名认。

使用条件：跑在仓库根；仅适用于 Kotlin 源码；不替代编译，只是把这类"必然编译红"提前抓出来。
退出码：0＝无缺失；1＝有缺失（供 rule-gate 直接当硬门禁）。
"""
import os
import re
import shutil
import sys
import tempfile

# 名字 -> 可能的完整 import 路径（任一命中即视为已导入）
KEXT = {
    "animateFloat": ["androidx.compose.animation.core.animateFloat"],
    "animateDp": ["androidx.compose.animation.core.animateDp"],
    "animateDpAsState": ["androidx.compose.animation.core.animateDpAsState"],
    "animateColorAsState": ["androidx.compose.animation.core.animateColorAsState"],
    "animateFloatAsState": ["androidx.compose.animation.core.animateFloatAsState"],
    "animateValueAsState": ["androidx.compose.animation.core.animateValueAsState"],
    "clip": ["androidx.compose.ui.draw.clip"],
    "drawBehind": ["androidx.compose.ui.draw.drawBehind", "androidx.compose.ui.draw.drawWithContent"],
    "drawWithContent": ["androidx.compose.ui.draw.drawWithContent"],
    "alpha": ["androidx.compose.ui.draw.alpha"],
    "shadow": ["androidx.compose.ui.draw.shadow"],
    "rotate": ["androidx.compose.ui.draw.rotate", "androidx.compose.ui.draw.rotateX", "androidx.compose.ui.graphics.drawscope.rotate"],
    "blur": ["androidx.compose.ui.draw.blur"],
    "offset": ["androidx.compose.foundation.layout.offset"],
    "padding": ["androidx.compose.foundation.layout.padding"],
    "size": ["androidx.compose.foundation.layout.size"],
    "heightIn": ["androidx.compose.foundation.layout.heightIn"],
    "widthIn": ["androidx.compose.foundation.layout.widthIn"],
    "fillMaxWidth": ["androidx.compose.foundation.layout.fillMaxWidth"],
    "fillMaxHeight": ["androidx.compose.foundation.layout.fillMaxHeight"],
    "background": ["androidx.compose.foundation.background"],
    "border": ["androidx.compose.foundation.border"],
    "clickable": ["androidx.compose.foundation.clickable"],
    "lerp": ["androidx.compose.ui.graphics.lerp", "androidx.compose.animation.core.lerp"],
    "graphicsLayer": ["androidx.compose.ui.graphics.graphicsLayer"],
    "semantics": ["androidx.compose.ui.semantics.semantics"],
    "pointerInput": ["androidx.compose.ui.input.pointer.pointerInput"],
}


def strip_noise(src: str) -> str:
    """去掉注释与字符串，只留代码骨架，避免注释/文案里的词造成假报。"""
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
                i += 1
            i += 2
        elif c == '"':
            # 三引号与普通字符串都跳过
            i += 3 if src[i:i + 3] == '"""' else 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == '"':
                    i += 1
                    break
                i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)


# 错包 import 名单：这些名字在 Kotlin/Android 生态里"长得对、包不对"，写错只有编译才报。
# 来源：2026-11-24 CI 抓到 `android.annotation.RequiresApi`（实际在 androidx.annotation）。
WRONG_IMPORT = {
    'import android.annotation.RequiresApi': '改用 androidx.annotation.RequiresApi',
    'import androidx.compose.ui.text.TextUnit': '改用 androidx.compose.ui.unit.TextUnit',
    'import androidx.compose.ui.geometry.Dp': '改用 androidx.compose.ui.unit.Dp',
}


def scan_hard_rules(src: str):
    """三条"必然编译红 / 必然静默出错"的硬规则，来源都是本轮真实事故：

    1) 块注释里出现 `/*`：Kotlin 块注释**可嵌套**，KDoc 正文里写一个 `/*`（例如路径 `model/**`）
       会让 Kotlin 多开一层，文件末尾的 `*/` 只回退一层 → 整段代码被吞、报
       `Syntax error: Unclosed comment`。这类错误肉眼极难发现（CampaignController 就这么挂的）。
    2) `const val` 初始化用了 `.dp` / `.sp` / `Color(...)`：const 只接受编译期常量字面量 →
       `Const 'val' initializer must be a constant value`（HighlightNoteDialog 的事故）。
    3) 十六进制字面量**16 位**（即 ≥2^63）且无 `L`/`u` 后缀：Kotlin 遇 8~15 位会自动升为 Long，
       但 16 位已超出 Long 的带符号范围 → `Value out of range`
       （HeightMapDecoder 的 FNV-1a 常数事故）。8 位色值 `0xFFF7F1E6` 属正常 Long，不报。
    """
    issues = []
    n = len(src)
    i = 0
    while i < n - 1:
        if src[i] == '/' and src[i + 1] == '*':
            j = i + 2
            depth = 1
            body_start = j
            while j < n - 1 and depth > 0:
                if src[j] == '/' and src[j + 1] == '*':
                    depth += 1
                    j += 2
                    continue
                if src[j] == '*' and src[j + 1] == '/':
                    depth -= 1
                    j += 2
                    continue
                j += 1
            if depth > 1:
                issues.append('块注释内嵌 `/*`（Kotlin 嵌套注释会吞掉后续代码）')
            i = j
            continue
        i += 1
    for m in re.finditer(r'const\s+val\s+[\w]+[^=\n]*=[^\n]*', src):
        txt = m.group(0)
        if re.search(r'\.\s*(dp|sp)\b', txt) or 'Color(' in txt or 'listOf' in txt:
            line = src[:m.start()].count('\n') + 1
            issues.append('L%d: const val 用了非编译期常量（.%s / Color / listOf）' %
                          (line, 'dp' if '.dp' in txt else 'sp'))
    for m in re.finditer(r'0x([0-9A-Fa-f]{16})([^lLuU0-9A-Fa-f]|$)', src):
        line = src[:m.start()].count('\n') + 1
        issues.append('L%d: 十六进制字面量 0x%s 超 Int 范围但缺 L 后缀' % (line, m.group(1)))
    return issues


def main(root: str) -> int:
    bad = []
    wrong = []
    hard = []
    for base, _, files in os.walk(root):
        for fn in files:
            if not fn.endswith('.kt'):
                continue
            p = os.path.join(base, fn)
            src = open(p, encoding='utf-8', errors='ignore').read()
            # 只看 Compose 文件：`matchParentSize` 是 BoxScope 成员（无需 import），
            # `alpha/clip/rotate/shadow` 与旧 View API（ViewPropertyAnimator、android.view.View）同名。
            # 若文件里一个 androidx.compose import 都没有，它就是旧 View 世界，按 Compose 口径判必假报。
            if 'import androidx.compose.' not in src:
                continue
            for msg in scan_hard_rules(src):
                hard.append((p, msg))
            for w, hint in WRONG_IMPORT.items():
                if w in src:
                    wrong.append((p, w, hint))
            imports = set(re.findall(r'^import\s+([\w.]+)', src, re.M))
            alias = set(re.findall(r'^import\s+[\w.]+\s+as\s+([\w]+)', src, re.M))
            code = strip_noise(src)
            for name, paths in KEXT.items():
                # 点调用形态：`.name(`，允许中间有空格
                if not re.search(r'\s\.\s*%s\s*\(' % re.escape(name), code):
                    continue
                if any(x in imports for x in paths):
                    continue
                if name in alias:
                    continue
                bad.append((p, name))
    if hard:
        print('✗ 硬规则违规（%d 处）：' % len(hard))
        for p, msg in hard:
            print('   %s %s' % (p, msg))
        return 1
    if wrong:
        print('✗ 错包 import（%d 处）：' % len(wrong))
        for p, w, hint in wrong:
            print('   %s -> %s（%s）' % (p, w, hint))
        return 1
    if bad:
        print('✗ 扩展函数缺 import（%d 处）：' % len(bad))
        for p, n in bad:
            print('   %s -> %s' % (p, n))
        return 1
    print('✓ 扩展函数 import 齐全')
    return 0


def _write_kt(root: str, name: str, src: str) -> str:
    d = os.path.join(root, 'src')
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, name)
    open(p, 'w', encoding='utf-8').write(src)
    return p


def selftest() -> int:
    """双向自测：扩展函数带 import → PASS；缺 import / 错包 import → FAIL rc=1。
    夹具 tempfile.mkdtemp + rmtree 自删；判定回调生产 main(root)，不另写 import 比对。"""
    # 好样本：padding 点调用与 import 成对出现 → 无缺失
    good = tempfile.mkdtemp(prefix='ext-imports-selftest-')
    try:
        _write_kt(good, 'Good.kt',
                  'import androidx.compose.foundation.layout.padding\n'
                  'import androidx.compose.runtime.Composable\n\n'
                  '@Composable\nfun Box(modifier: Modifier) {\n'
                  '    modifier\n'
                  '        .padding(8.dp)\n}\n')
        rc_good = main(good)
        assert rc_good == 0, f"import 齐全应 PASS，rc={rc_good}"
        print('[selftest] 好样本（扩展函数 import 成对）PASS 通路 OK')
    finally:
        shutil.rmtree(good, ignore_errors=True)

    # 坏样本一：.clip( 点调用但无 clip import → 扩展函数缺 import FAIL
    bad1 = tempfile.mkdtemp(prefix='ext-imports-selftest-')
    try:
        _write_kt(bad1, 'BadClip.kt',
                  'import androidx.compose.runtime.Composable\n\n'
                  '@Composable\nfun Box(modifier: Modifier) {\n'
                  '    modifier\n'
                  '        .clip(RoundedCornerShape(4.dp))\n}\n')
        rc_b1 = main(bad1)
        assert rc_b1 == 1, f"缺 clip import 应 FAIL，rc={rc_b1}"
        print('[selftest] 坏样本一（.clip( 缺对应 import）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad1, ignore_errors=True)

    # 坏样本二：错包 import android.annotation.RequiresApi → 错包 import FAIL
    bad2 = tempfile.mkdtemp(prefix='ext-imports-selftest-')
    try:
        _write_kt(bad2, 'BadPkg.kt',
                  'import android.annotation.RequiresApi\n'
                  'import androidx.compose.runtime.Composable\n\n'
                  '@Composable\nfun Box() {}\n')
        rc_b2 = main(bad2)
        assert rc_b2 == 1, f"错包 import 应 FAIL，rc={rc_b2}"
        print('[selftest] 坏样本二（android.annotation 错包 import）FAIL 通路 OK')
    finally:
        shutil.rmtree(bad2, ignore_errors=True)

    # 边界·空集：目录内无 .kt 文件 → 零违规真绿，现有设计 PASS
    empty = tempfile.mkdtemp(prefix='ext-imports-selftest-empty-')
    try:
        rc_empty = main(empty)
        assert rc_empty == 0, f"无 kt 文件现有设计应 rc=0，rc={rc_empty}"
        print('[selftest] 边界·空集（无 .kt 源码）按现有设计放行，已记录口径')
    finally:
        shutil.rmtree(empty, ignore_errors=True)

    print('[selftest] 夹具已自删，双向自测通过。')
    return 0


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(selftest())
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java'))
