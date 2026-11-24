# 修复-CI-阅读弹窗编译（仅编译，不改设计）

域：`HighlightNoteDialog.kt`、`ShadowEditDialog.kt`；依据 `docs/CI-编译错误-4508967.md` 第 7-12 行。沙箱禁编译、禁 git 写；未碰 `card/`、`map/crossing/`、`ui/main/`。

## A. HighlightNoteDialog.kt
- **205:1（原 203:52/59/72）** `TRUNCATED_HINT_FORMAT` 格式串内 `%2$d`、`%1$d` 的 `$d` 被 Kotlin 当字符串模板引用标识符 `d` → `Unresolved reference 'd'`，且初值非编译期常量 → `Const 'val' initializer must be a constant value`。
- 修法：`$` 转义为 `\$`（`"%2\$d"` / `"%1\$d"`），运行时串仍为 `%2$d`，`String.format` 口径不变；保留 `const val`，未降为普通 val，未改裸值。
- import：`dp`(25)、`sp`(26) 本就在表，无需新增。

## B. ShadowEditDialog.kt
- **281:42（原 278:42/47）** 拇指 `Modifier.offset { IntOffset(...) }` 的 lambda 接收者是 `Density`，无 `size` 属性；块内 `size.width` 被误解析为 `Modifier.size(...)` 调用 → `size(...)`/`width(...)` 期望函数调用、接收者类型不匹配。
- 修法：轨道宽在 offset 块外经 `onSizeChanged` 量成 px 捕获到 `trackWidthPx`（221 行状态、249 行挂载），offset 块内改用 `trackWidthPx`；`pointerInput` 手势块内的 `size.width`（255 行）接收者为 `PointerInputScope`，合法，未动。
- import：新增 `androidx.compose.ui.layout.onSizeChanged`(31)；`remember`/`mutableFloatStateOf`/`getValue`/`setValue`/`IntOffset`/`dp` 均已在表。

## 自检
- 括号配平：A `{}`35/35、`()`89/89、`[]`9/9；B `{}`38/38、`()`138/138、`[]`6/6，全 0 失衡。
- 改动仅限上述 2 文件；无 `@Suppress`、无删元素/删分支、无裸 float 尺寸。
- 边界：首帧 `trackWidthPx=0f`，布局回调后下一帧拇指归位，不崩；与 `InkBubble` 的 onSizeChanged 同口径。
