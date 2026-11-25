# 锚点卡 · B26 同类常量 visible 接线

> 用途：B26 写码单开工依据。出自 `docs/修复-B21-半屏离场动画.md` 的边界段（B21 验收同时确认 :86 行号）。

## 一、事实锚点
- `ui/note/HighlightNoteDialog.kt:86`：`visible` 传常量 `true`，关闭时直接摘子树，与 B21 修复前的 `ReaderSheetOverlay` 同形态。
- B21 已立标准：两段式关闭（先 `visible=false` 播离场，收束后再摘），纯 JVM 离场状态机 `ReaderSheetExitGate` 可复用。

## 二、判据
1. 先出全仓调用点清单：grep `visible = true` 与 `visible=true`，判断哪些是「浮层可见性传常量」、哪些是无关布尔；清单进报告，不许只改 `HighlightNoteDialog` 一处就说清完。
2. 浮层类调用点一律改两段式；复用 B21 的状态机而不是再造一份（禁止第二源）。
3. 离场期间不可重复触发打开（与既有闸口径一致）。
4. 进入动画不受影响。
5. 可断言：改动的状态逻辑落纯 JVM 层，至少一条断言。

## 三、边界
- 只动 `ui/note/HighlightNoteDialog.kt` 及清单里确认的浮层类调用点；不动 reader 域（B20/B21 已落）、不动 ui/components 契约、不动地图与名物库。
- 若某调用点是「一次性 Dialog 无需离场动画」，写明理由后可不改，但不许「没看就跳过」。

## 四、验收
独立验收代理逐条对照；CI 唯一编译手段。
