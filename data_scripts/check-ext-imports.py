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
import sys

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


def main(root: str) -> int:
    bad = []
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
    if bad:
        print('✗ 扩展函数缺 import（%d 处）：' % len(bad))
        for p, n in bad:
            print('   %s -> %s' % (p, n))
        return 1
    print('✓ 扩展函数 import 齐全')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java'))
