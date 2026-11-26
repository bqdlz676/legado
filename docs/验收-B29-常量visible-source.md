> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 1 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B29-域外常量visible-config.md

# 验收-B29：source 域浮层可见性常量导致离场动画跳过（独立验收）

## 〇、验收元信息

- 验收对象：`docs/修复-B29-常量visible-source.md`（被验方自述，已逐字通读，含 §5 清单对账、§6 实测差额、§7.2 保留旧直摘口径、§10 需另单）。
- 清单来源：`docs/修复-B26-常量visible接线.md` §6.3 第 7–18 条（book/source 域 12 条命名参数）。
- 被验提交：`739f073`（代码，"修B29:source域浮层两段式收口(12条命名参数,SourceSheetExitController复用B26闸,7断言)"）；文档入库提交 `7f2fb55`。验收基线：`739f073^`（即 `aee8f48`）。
- 先例对照：config 域 `ui/config/settings/SheetExitController.kt`；通用阶段机 `ui/book/read/ink/ReaderSheetExitGate.kt`（枚举 `ReaderSheetExitPhase`）；B26 守卫 `ui/book/read/NoteDialogExitGate.kt`。
- 验收方式：只读代码、测试与 git 历史，静态逐条回证；每条结论附「文件:行」证据与本验收真跑的 grep 命令及原样输出。未改源码 / 测试 / 既有报告 / 门禁脚本；未执行 git add / commit / push；未本地编译、未跑单测（沙箱无 gradle / JDK21 / SDK，编译与 CI 结果标「未验证，等 CI」）。
- 范围隔离：工作树中 autoTask / dict / replace / bookshelf / `ui/sheet/` 等在飞单 WIP 不属于本单，本报告不将其计入本单问题，也未改动。
- 总体结论：**九条判据静态层全部 PASS，无 FAIL 项。清单 12=12 一一对应；source 域三类常量残留均为 0，全仓命名参数仅剩红线 `HandleFileSheet.kt:48` 一条；阶段机复用 B26 守卫，未造第二份枚举；延时引组件时长常数无魔法数；12 个打开点全部过闸；7 处保留直摘逐条判定为跨 Sheet 导航；7 例断言覆盖控制器全部分支；提交边界只触达 `ui/book/source` 与其测试。附 4 项观察项（1 处自述行号小偏差、1 组预存不关层行为、2 项报告已自行登记的跨模块议题）。编译是否通过、7 例单测是否全绿、真机帧对齐与离场段触摸截获，未验证，等云端 CI 出包后复验。**

## 一、判据 1：清单对账（B26 source 12 条与现已改位置一一对应，12=12）—— PASS

判据要求：自己 grep 核对 B26 的 source 12 条行号与现已改位置一一对应，无遗漏无多改。

1. B26 清单原文（`docs/修复-B26-常量visible接线.md` §6.3，第 266–279 行）共 12 条，文件分布：`manage/SourceManageSheets.kt` 10 条、`edit/SourceEditScreen.kt` 1 条、`debug/SourceDebugScreen.kt` 1 条。

2. 基线复核（验收基线 `aee8f48`，确认 12 条基线行确为 `InkHalfSheet(visible = true, ...)`，行号零漂移）。命令：

```
for f in "app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:85 145 167 202 296 337 374 422 459 489" "app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt:426" "app/src/main/java/io/legado/app/ui/book/source/debug/SourceDebugScreen.kt:401"; do file=$(echo "$f" | cut -d: -f1); lines=$(echo "$f" | cut -d: -f2); for n in $lines; do echo -n "BASE $file:$n => "; git show "aee8f48:$file" | sed -n "${n}p"; done; done
```

原样输出（12 行，均为 `visible = true`）：

```
BASE .../manage/SourceManageSheets.kt:85 =>     InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.book_source_manage_title), content = {
BASE .../manage/SourceManageSheets.kt:145 =>    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.sort), content = {
BASE .../manage/SourceManageSheets.kt:167 =>    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.menu_action_group), content = {
BASE .../manage/SourceManageSheets.kt:202 =>    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.group_manage), content = {
BASE .../manage/SourceManageSheets.kt:296 =>    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.import_on_line), content = {
BASE .../manage/SourceManageSheets.kt:337 =>    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.check_select_source), content = {
BASE .../manage/SourceManageSheets.kt:374 =>    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = source.bookSourceName, content = {
BASE .../manage/SourceManageSheets.kt:422 =>    InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.source_selection_count, selection.size), content = {
BASE .../manage/SourceManageSheets.kt:459 =>    InkHalfSheet(visible = true, onDismiss = onDismiss, title = title, content = {
BASE .../manage/SourceManageSheets.kt:489 =>    InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_export_title), content = {
BASE .../edit/SourceEditScreen.kt:426 =>       InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_edit_title), content = {
BASE .../debug/SourceDebugScreen.kt:401 =>      InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_debug_title), content = {
```

3. 现役调用点扫描。命令：

```
grep -rn "InkHalfSheet(" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt
```

原样输出（12 处，10 manage + 1 edit + 1 debug，全部为派生 `visible`，无 `true` 常量）：

```
.../debug/SourceDebugScreen.kt:413:    InkHalfSheet(visible = visible, onDismiss = onDismiss, ...)
.../edit/SourceEditScreen.kt:438:    InkHalfSheet(visible = visible, onDismiss = onDismiss, ...)
.../manage/SourceManageSheets.kt:91:  InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:156: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:182: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:221: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:319: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:364: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:405: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:457: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:499: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
.../manage/SourceManageSheets.kt:533: InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)
```

4. 逐条对账（基线行 → 现役行 → 组件归属）：

- B26 第 7 条 `SourceManageSheets.kt:85` → 现役 `:91`，`MainOptionsSheet` 主选项 Sheet。
- B26 第 8 条 `:145` → 现役 `:156`，`SortSheet` 排序 Sheet。
- B26 第 9 条 `:167` → 现役 `:182`，`GroupFiltersSheet` 分组筛选 Sheet。
- B26 第 10 条 `:202` → 现役 `:221`，`GroupManageSheet` 分组管理 Sheet。
- B26 第 11 条 `:296` → 现役 `:319`，`OnlineImportSheet` 在线导入 Sheet。
- B26 第 12 条 `:337` → 现役 `:364`，`CheckSourceSheet` 校验 Sheet。
- B26 第 13 条 `:374` → 现役 `:405`，`ItemActionsSheet` 单来源菜单 Sheet。
- B26 第 14 条 `:422` → 现役 `:457`，`SelectionActionsSheet` 多选统计 Sheet。
- B26 第 15 条 `:459` → 现役 `:499`，`GroupInputSheet` 通用标题 / 分组输入 Sheet（`AddToGroup` / `RemoveFromGroup` 两槽复用）。
- B26 第 16 条 `:489` → 现役 `:533`，`ExportResultSheet` 导出结果 Sheet。
- B26 第 17 条 `SourceEditScreen.kt:426` → 现役 `:438`，编辑页 `OverflowSheet`。
- B26 第 18 条 `SourceDebugScreen.kt:401` → 现役 `:413`，调试页 `OverflowSheet`。

5. 计数复核：manage 10 + edit 1 + debug 1 = 12，与清单 12 条一一对应；现役 source 域无第 13 个 `InkHalfSheet` 调用点（多改扫描见判据 2 命令 A 与本判据命令 3）。无遗漏、无多改。**判据 1 PASS。**

## 二、判据 2：域清零（source 域三类残留为 0；全仓仅剩红线一条命名参数）—— PASS

判据要求：`ui/book/source` 内命名参数 `visible = true`、位置参数 `Sheet(true, …)`、多行 `true,` 均为 0；全仓残留应仅剩红线 `ui/components/HandleFileSheet.kt:48` 一条命名参数。

1. source 域命名参数 `visible = true`。命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt; echo "exit=$?"
```

原样输出：空；`exit=1`（零命中）。

2. source 域位置参数 `Sheet(true, …)` / `Host(true, …)`。命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt; echo "exit=$?"
```

原样输出：空；`exit=1`（零命中）。

3. source 域多行 `true,`。命令：

```
grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt; echo "exit=$?"
```

原样输出：空；`exit=1`（零命中）。

4. 全仓命名参数 `visible = true`。命令：

```
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt
```

原样输出（仅 1 条，红线文件）：

```
app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt:48:    InkHalfSheet(visible = true, onDismiss = onCancel, title = title, content = {
```

红线复核：`sed -n '48p' app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt` 输出与上一致，本单未改动该文件（判据 9 提交边界复核）。

5. 全仓位置参数形式。命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main --include=*.kt; echo "exit=$?"
```

原样输出：空；`exit=1`（全仓零命中）。

6. 全仓多行 `true,` 兜底扫描（逐条核对非 Sheet 首参）。命令：

```
grep -rnE "^\s*true,\s*$" app/src/main --include=*.kt
```

原样输出 14 处，逐条读上下文后分类如下，均非 `InkHalfSheet` / `InkBottomSheet` / 其薄封装的首参 `visible`：

- `help/BottomBarSkinManager.kt:405`——位图宽高之后的回调布尔标志（`coerceAtLeast(1)` 尺寸参数序列）。
- `help/source/SourceVerificationHelp.kt:309`——url / title 之后的回调布尔标志。
- `book/changesource/ChangeBookSourceDialog.kt:262`——错误消息 / throwable 之后的错误提示回调布尔。
- `book/changesource/ChangeChapterSourceDialog.kt:373`、`:597`——同类错误提示回调布尔。
- `book/read/HighlightStyleDialog.kt:155`、`:252`——高亮样式项选中标志。
- `code/CodeEditViewModel.kt:233`——错误消息 / throwable 之后的错误回调布尔。
- `components/inkslate/contract/InkPalette.kt:33`、`:45`、`:57`——调色板色项选中标志。
- `main/rss/RssFragment.kt:188`、`:205`——`ReadRssActivity.start` 启动布尔实参。
- `main/search/SearchHighlight.kt:45`——`MatchSegment` 匹配段布尔标志。

以上 14 处均与浮层可见性无关，不计缺陷。**判据 2 PASS。**

## 三、判据 3：阶段机无第二源（复用 B26 守卫 / B21 枚举，source 域无第二份阶段枚举）—— PASS

判据要求：`SourceSheetExitController.kt` 是否 import 复用 `NoteDialogExitGate` / `ReaderSheetExitPhase`（给 import 行号）；source 域内无第二份 `enum class` 阶段枚举。

1. 控制器 import 行号。命令：

```
grep -n "^import" app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt
```

原样输出：

```
3:import androidx.compose.runtime.Composable
4:import androidx.compose.runtime.LaunchedEffect
5:import androidx.compose.runtime.MutableState
6:import androidx.compose.runtime.mutableStateOf
7:import androidx.compose.runtime.remember
8:import io.legado.app.ui.book.read.NoteDialogExitGate
9:import io.legado.app.ui.components.HalfSheetAnchors
10:import kotlinx.coroutines.delay
```

复用链：控制器 `:8` import B26 守卫 `NoteDialogExitGate`；该守卫 `app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:3` import B21 枚举 `io.legado.app.ui.book.read.ink.ReaderSheetExitPhase`；枚举定义于 `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetExitGate.kt:14`（`enum class ReaderSheetExitPhase {`，成员 `:15 OPEN`、`:16 EXITING`、`:17 REMOVED`）。控制器构造见 `SourceSheetExitController.kt:32-35`，包装 `gate: NoteDialogExitGate`，未自持有阶段字段。

2. source 域全部 `enum class` 排查。命令：

```
grep -rn "enum class" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt
```

原样输出 6 个枚举，逐一核对均非浮层离场阶段枚举：

- `debug/SourceDebugScreen.kt:48` `DebugSrcKind { SEARCH, BOOK, TOC, CONTENT }`——调试响应类别。
- `edit/JsSourceEditFlow.kt:3` `JsSourceEditStage`、`:15` `JsSourceEditRestoreAction`——JS 源编辑流程步骤 / 恢复动作。
- `edit/SourceEditState.kt:13` `EditTab`——编辑页标签页。
- `manage/SourceManageState.kt:15` `BookSourceSort`、`:20` `SourcePreset`——排序键 / 筛选预设。

3. 控制器内对阶段机符号的直接引用排查。命令：

```
grep -rn "ReaderSheetExitPhase\|NoteDialogExitGate\|ExitGate\|ExitPhase" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt
```

原样输出：除控制器 `:8` import、`:13/:16/:17` 文档注释、`:33` 构造形参、`:74` 实例化 `NoteDialogExitGate()` 外，无第二份守卫类、无第二份迁移规则。**判据 3 PASS。**

## 四、判据 4：时长同源（延时引用 HalfSheet 两段时长常量，无硬编码数字）—— PASS

判据要求：收束延时是否引用 `HalfSheetAnchors.MASK_DURATION_MS`(200) + `RISE_DURATION_MS`(250) 常量的真实值（给常量定义行号与引用行号）；若硬编码数字则 FAIL。

1. 常量定义。命令：

```
grep -n "MASK_DURATION_MS\|RISE_DURATION_MS" app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt
```

原样输出：

```
50:    const val RISE_DURATION_MS: Int = 250
53:    const val MASK_DURATION_MS: Int = 200
```

所属对象：`HalfSheetModel.kt:20` `object HalfSheetAnchors`；与 `InkHalfSheet.kt:115`（`RISE_DURATION_MS` 弹起 tween）、`:119` / `:124`（`MASK_DURATION_MS` 遮罩 tween）引用的是同一份常数。

2. 控制器引用。命令：

```
grep -n "MASK_DURATION_MS\|RISE_DURATION_MS\|delay(" app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt
```

原样输出：

```
95:            delay((HalfSheetAnchors.MASK_DURATION_MS + HalfSheetAnchors.RISE_DURATION_MS).toLong())
```

3. 魔法数字排查。命令：

```
grep -nE "\b(200|250|450)\b" app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt; echo "exit=$?"
```

原样输出：空；`exit=1`。控制器内无 200 / 250 / 450 硬编码，延时表达式 `:95` 两段常数相加（200 + 250 = 450ms），与 B26 `HighlightNoteDialog.kt:108`、B21 `ReaderSheetOverlay.kt:57` 同一口径。**判据 4 PASS。**

## 五、判据 5：离场链路真实（抽查 4 个以上 Sheet：dismiss 走 beginExit、可见性派生、收束回调清槽位摘子树）—— PASS

判据要求：抽查至少 4 个 Sheet（含 `SourceManageSheets` 2 个、`SourceEditScreen` 溢出菜单 1 个、`SourceDebugScreen` 1 个），逐条确认 dismiss 走 `beginExit()`、可见性来自派生位、收束回调里清槽位摘子树；给行号。

组件离场分支前提核对：`app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt:113` `LaunchedEffect(visible)`；`:121-129` `visible=false` 分支依次把 `maskProgress` → 0（200ms）、`progress` → 0（250ms）；`:131` `if (progress.value == 0f && !visible) return` 不可见时不渲染遮罩内容。派生 `visible=false` 确能播组件内离场动画，链路前提成立。

抽查 1 —— `MainOptionsSheet`（manage，`SourceManageSheets.kt`）：

- `:88` `val exit = rememberSourceSheetExitController()`（本槽独立控制器）。
- `:89` `LaunchedEffect(Unit) { if (exit.canOpen) exit.open() }`（在场登记过闸）。
- `:90` `SourceSheetExitHost(controller = exit, onFinished = vm::closeSheet)`（收束回调清 VM 槽位摘子树）。
- `:91` `InkHalfSheet(visible = exit.visible, onDismiss = { exit.beginExit() }, ...)`（可见性派生；遮罩 / 拖拽 dismiss 走 `beginExit()`）。

抽查 2 —— `SortSheet`（manage，`SourceManageSheets.kt`）：

- `:144` 控制器；`:145` 过闸 `open()`；`:155` host 收束 `vm::closeSheet`；`:156` `visible = exit.visible` + `onDismiss = { exit.beginExit() }`；`:162` 选中动作 `{ vm.setSort(sort); exit.beginExit() }`（决策副作用先执行，再进离场段）。

抽查 3 —— 编辑页 `OverflowSheet`（`SourceEditScreen.kt`）：

- `:89` `val overflowExit = rememberSourceSheetExitController()`。
- `:101-104` 打开回调 `if (overflowExit.canOpen) { overflowOpen = true; overflowExit.open() }`。
- `:154` `if (overflowOpen) {`（宿主条件渲染，收束前子树保留）；`:155` `SourceSheetExitHost(overflowExit, onFinished = { overflowOpen = false })`（收束清 local 槽位摘子树）；`:160` `visible = overflowExit.visible`；`:161` `onDismiss = { overflowExit.beginExit() }`。
- `:435` `visible: Boolean` 形参；`:438` 内层 `InkHalfSheet(visible = visible, onDismiss = onDismiss, ...)` 透传派生可见性。

抽查 4 —— 调试页 `OverflowSheet`（`SourceDebugScreen.kt`）：

- `:79` `val overflowExit = rememberSourceSheetExitController()`。
- `:93-95` 打开回调 `if (overflowExit.canOpen) { overflowOpen = true; overflowExit.open() }`。
- `:117` `if (overflowOpen) {`；`:118` `SourceSheetExitHost(overflowExit, onFinished = { overflowOpen = false })`；`:121` `visible = overflowExit.visible`；`:122` `onDismiss = { overflowExit.beginExit() }`。
- `:409` `visible: Boolean` 形参；`:413` 内层 `InkHalfSheet(visible = visible, onDismiss = onDismiss, ...)`。

其余 6 个 manage Sheet 同构接线，行号复核如下（控制器 / 过闸 open / host 收束 / 派生 visible+dismiss）：

- `GroupFiltersSheet`：`:179` / `:180` / `:181` / `:182`；动作离场 `:196`、`:203`。
- `GroupManageSheet`：`:218` / `:219` / `:220` / `:221`。
- `OnlineImportSheet`：`:316` / `:317` / `:318` / `:319`；动作离场 `:347`。
- `CheckSourceSheet`：`:361` / `:362` / `:363` / `:364`；动作离场 `:378`。
- `ItemActionsSheet`：`:402` / `:403` / `:404` / `:405`；动作离场 `:410`、`:415`、`:420`、`:422`、`:423`、`:433`、`:439`。
- `SelectionActionsSheet`：`:454` / `:455` / `:456` / `:457`；动作离场 `:459`、`:460`、`:461`、`:462`、`:463`、`:473`、`:474`、`:475`、`:476`、`:480`。
- `GroupInputSheet`：`:496` / `:497` / `:498`（`onFinished = onDismiss`，由宿主 `:68` / `:74` 转发 `vm::closeSheet`）/ `:499`；动作离场 `:513`。
- `ExportResultSheet`：`:530` / `:531` / `:532`（`onFinished = onDismiss`，由宿主 `:76` 转发 `vm::closeSheet`）/ `:533`。

收束宿主实现：`SourceSheetExitController.kt:88-101`，`:93` `LaunchedEffect(controller.isExiting)`，`:94-98` 离场段延时后 `finishExit()` 再 `onFinished()`；`:100` `content()` 保留子树直到宿主槽位清空。**判据 5 PASS（观察项见 §十 F-2：部分动作行基线即不自动关层，非本单回归）。**

## 六、判据 6：打开闸（每个打开点先过 canOpen 再 open；程序化入口同样过闸；无共享控制器串态）—— PASS

判据要求：每个打开点先过 `canOpen` 再 `open()`；程序化入口（LaunchedEffect / 副作用里 open）同样过闸；给至少 5 处行号；若 10 个 Sheet 只接 1 份共享控制器，需确认多 Sheet 共用一个阶段态的串态风险。

1. 全部打开点与闸位排查。命令：

```
grep -rn "\.open()\|canOpen\|LaunchedEffect" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt
```

2. manage 10 个程序化打开点（`LaunchedEffect(Unit)` 内先判 `canOpen` 再 `open()`），行号：

- `SourceManageSheets.kt:89` `LaunchedEffect(Unit) { if (exit.canOpen) exit.open() }`
- `:145`、`:180`、`:219`、`:317`、`:362`、`:403`、`:455`、`:497`、`:531`（同一模式，共 10 处）。

3. 编辑 / 调试 2 个回调打开点（顶栏溢出按钮，先判 `canOpen` 再置槽位 + `open()`）：

- `SourceEditScreen.kt:101` `if (overflowExit.canOpen) {` → `:102` `overflowOpen = true` → `:103` `overflowExit.open()`。
- `SourceDebugScreen.kt:93` `if (overflowExit.canOpen) {` → `:94` `overflowOpen = true` → `:95` `overflowExit.open()`。

合计 12 个打开点，全部先过闸；无裸 `open()` 调用（控制器 `:54` `fun open() = gate.open()` 定义除外）。

4. 共享控制器 / 串态排查。命令：

```
grep -rn "rememberSourceSheetExitController()" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt
```

原样输出实例化点 12 处（不含工厂定义 `:73`）：manage `:88`、`:144`、`:179`、`:218`、`:316`、`:361`、`:402`、`:454`、`:496`、`:530`；edit `:89`；debug `:79`。每个私有 Sheet / 每屏溢出菜单各持一份独立控制器，控制器未上提到 `ManageSheets` 分发函数、未传入 ViewModel；`when(state.sheet)` 同一时刻只组合一个分支（`SourceManageSheets.kt:53-77`），`GroupInputSheet` 虽被 `:64-69` 与 `:70-75` 两个槽位复用，但每次组合经 `:496` `remember` 新建控制器。不存在多 Sheet 共用一个阶段态的串态风险。

5. 宿主级闸边界（已在报告 §10.2 登记，非本单漏接）：VM `openSheet` / `closeSheet` 槽位语义见 `SourceManageViewModel.kt:460` `fun openSheet(sheet: ManageSheet) = _uiState.update { it.copy(sheet = sheet) }`、`:461` `fun closeSheet() = _uiState.update { it.copy(sheet = ManageSheet.None) }`；VM 入口与 Activity 结果回调未串联 `canOpen`，离场 ~450ms 内由浮层窗口截获触摸兜底。此为 B30 同类边界，报告 §10.2 已写明需另派单改 VM / Activity，不计本单 FAIL。**判据 6 PASS。**

## 七、判据 7：「保留旧直摘」7 处判定复核（逐条区分跨屏导航与同层关闭）—— PASS

判据要求：逐条读代码判断是「离开本浮层进下一屏」还是「同一浮层关闭」；若是后者判 FAIL。

报告 §7.2 列 7 处保留旧直摘，逐条复核现役代码：

1. `SourceManageSheets.kt:97-100` 在线导入：`onClick = { vm.closeSheet(); vm.openSheet(ManageSheet.OnlineImport) }`——先清当前主选项槽位，再打开 `OnlineImport` Sheet，属离开本层进下一屏。导航切换，判定成立。
2. `:102-105` 分组管理：`vm.closeSheet(); vm.openSheet(ManageSheet.GroupManage)`——进 `GroupManageSheet`。导航切换，判定成立。
3. `:106-109` 排序：`vm.closeSheet(); vm.openSheet(ManageSheet.SortOptions)`——进 `SortSheet`。导航切换，判定成立。
4. `:110-113` 分组筛选：`vm.closeSheet(); vm.openSheet(ManageSheet.GroupFilters)`——进 `GroupFiltersSheet`。导航切换，判定成立。
5. `:465-468` 加入分组：`onClick = { vm.openSheet(ManageSheet.AddToGroup(selection)) }`——`openSheet` 替换当前槽位（VM `:460` `copy(sheet = sheet)`），进 `GroupInputSheet`（加组槽）。导航切换，判定成立。
6. `:469-472` 移出分组：`vm.openSheet(ManageSheet.RemoveFromGroup(selection))`——进 `GroupInputSheet`（移组槽）。导航切换，判定成立。
7. `:477-479` 校验来源：`vm.openSheet(ManageSheet.CheckSource)`——进 `CheckSourceSheet`。导航切换，判定成立。

7 处全部为跨 Sheet 导航（打开下一级浮层），无一处是「同一浮层关闭」。补充核对：manage 文件内其余 `closeSheet` 调用仅出现在 host 收束回调（`:90`、`:155`、`:181`、`:220`、`:318`、`:363`、`:404`、`:456`）与 `GroupInput` / `ExportResult` 宿主转发形参（`:68`、`:74`、`:76`）；edit / debug 内 `overflowOpen = false` 仅出现在 host `onFinished`（`SourceEditScreen.kt:155`、`SourceDebugScreen.kt:118`），无遗漏的同层直摘。**判据 7 PASS。**

## 八、判据 8：断言映射（7 例逐条对应判据，缺覆盖判 FAIL）—— PASS

判据要求：7 例逐条对应判据（给测试文件行号）；缺覆盖判 FAIL。

测试文件：`app/src/test/java/io/legado/app/ui/book/source/SourceSheetExitControllerTest.kt`；包名 `:1` `io.legado.app.ui.book.source`，与控制器 `internal` 构造同包可访问；`:4` import `NoteDialogExitGate`。

1. `:28` `初始为摘除态 不可见 子树不在 可接受新打开`——`:31` `assertFalse(controller.visible)`、`:32` `assertFalse(controller.isComposed)`、`:33` `assertTrue(controller.canOpen)`、`:34` `assertFalse(controller.isExiting)`。对应初始 REMOVED 态分支。
2. `:38` `open 后在场态 可见 子树保留 可开`——`:41` `open()`；`:43` visible=true、`:44` isComposed=true、`:45` canOpen=true、`:46` isExiting=false。对应 OPEN 在场态分支。
3. `:50` `关闭瞬间子树仍在且可见性已翻 false`——`:54` `beginExit()`；`:57` isComposed=true（子树保留）、`:58` visible=false、`:59` isExiting=true。对应判据 5「关闭先进离场段、子树保留、可见性派生 false」。
4. `:63` `离场段拒收重复打开`——`:68` 离场段 `canOpen=false`；`:70` 重复 `beginExit()` 不改阶段；`:71` isComposed=true、`:72` visible=false。对应判据 6「离场段拒收」与守卫幂等。
5. `:76` `动画收束后摘除子树`——`:81` `finishExit()`；`:83` isComposed=false、`:84` visible=false、`:85` isExiting=false、`:86` canOpen=true。对应收束 REMOVED 分支与收束后可再开。
6. `:90` `空操作兜底 非在场开始离场不动状态 非离场收尾不迁移`——`:93` REMOVED 段 `beginExit()` 空操作（`:94` / `:95` 状态不迁移）；`:97` `open()` 后 `:98` OPEN 段 `finishExit()` 空操作（`:99` / `:100` 仍在场）。对应守卫两条非目标段空操作分支。
7. `:104` `收束后重开回到在场态并可见`——`:106-108` open → beginExit → finishExit；`:110` 再次 `open()`；`:112` visible=true、`:113` canOpen=true、`:114` isComposed=true。对应收束后重开回路。

覆盖分支对照：初始态（例 1）/ 在场态（例 2）/ 离场段进入（例 3）/ 离场段拒收与幂等（例 4）/ 收束摘除（例 5）/ 空操作兜底（例 6）/ 收束后重开（例 7），控制器全部公开方法与四个派生属性均有断言。

计数与命名复核。命令：

```
grep -c "@Test" app/src/test/java/io/legado/app/ui/book/source/SourceSheetExitControllerTest.kt
grep -nE '^\s*fun `[^`]*[.:\[\]/;<>][^`]*`' app/src/test/java/io/legado/app/ui/book/source/SourceSheetExitControllerTest.kt; echo "exit=$?"
```

原样输出：`7`；第二条空，`exit=1`（7 个反引号测试名不含 `. : [ ] / ; < >`）。

覆盖缺口（如实登记，不判 FAIL）：Compose 层 `SourceSheetExitHost` 的等效延时与 `onFinished` 回调接线无 JVM 断言（延时留在 Compose 层，测试文件 `:14-15` 注释已说明「动画延时留在 Compose 层，不进入本断言」）；12 个 UI 接线点本身未逐点写 Compose UI 测试。此与 config 域先例同口径，真机 / 帧对齐部分见 §十一未验证项。**判据 8 PASS。**

## 九、判据 9：边界未越（739f073 只含 ui/book/source 与其测试；域外零 diff）—— PASS

判据要求：`739f073` 只含 `ui/book/source` 与其测试；config / reader / 组件 / 地图 / 名物库零 diff；给 `git show --stat` 输出。

1. 提交文件统计。命令：

```
git show --stat --oneline 739f073
```

原样输出：

```
739f073 修B29:source域浮层两段式收口(12条命名参数,SourceSheetExitController复用B26闸,7断言)
 .../ui/book/source/SourceSheetExitController.kt    | 101 ++++++++
 .../app/ui/book/source/debug/SourceDebugScreen.kt  |  26 +-
 .../app/ui/book/source/edit/SourceEditScreen.kt    |  28 ++-
 .../ui/book/source/manage/SourceManageSheets.kt    | 279 ++++++++++++---------
 .../book/source/SourceSheetExitControllerTest.kt   | 116 +++++++++
 5 files changed, 418 insertions(+), 132 deletions(-)
```

2. 域外文件清零复核。命令：

```
git show --name-only --format="" 739f073 | grep -vE "ui/book/source/"; echo "域外exit=$?"
```

原样输出：空；`域外exit=1`（5 个文件全部位于 `ui/book/source/`，无 config / reader / components / 地图 / 名物库 / help 改动）。

3. 文档提交 `7f2fb55` 复核：命令 `git show --name-only --format="" 7f2fb55` 原样输出仅 `docs/修复-B29-常量visible-source.md` 一个文档文件。

4. 工作树隔离复核：`git status --short | grep -i source` 无输出（source 域改动已全部落入 `739f073`，工作树无残留未提交 source 改动）；`git diff --stat -- app/src/main/java/io/legado/app/ui/book/source/ app/src/test/java/io/legado/app/ui/book/source/` 输出为空。工作树中 autoTask / dict / replace / bookshelf / `ui/sheet/` 等 WIP 未计入本单。**判据 9 PASS。**

## 十、新发现缺陷 / 观察项

- F-1（低，自述文档行号偏差，非代码缺陷）：被验报告 §3.7 与 §4.1 称 `NoteDialogExitGate` import 在 `SourceSheetExitController.kt:3`；实测该 import 在 `:8`（`:3` 实为 `import androidx.compose.runtime.Composable`）。代码复用关系正确，仅自述行号漂移，不影响功能判定。另：报告 §1 / §12 称「本单未执行 git add / commit / push」，但代码实际已入库为 `739f073`、报告已入库为 `7f2fb55`；属交接备注措辞滞后，代码状态以 git 历史为准。
- F-2（预存行为，非本单回归；建议另单评估）：以下动作行点击后不调用 `onDismiss` / `beginExit`，弹层不自动关闭，基线 `aee8f48` 即如此，本单未改动其交互：
  - 调试页溢出菜单 `SourceDebugScreen.kt:415`、`:417`、`:420`、`:423`、`:426`、`:428`、`:429`（扫码 / 查看响应 / 刷新发现 / 帮助等动作行均无 `onDismiss`）。
  - 编辑页自动补全开关 `SourceEditScreen.kt:448` `vm.toggleAutoComplete()`（开关保持菜单展开，基线同）。
  - 分组管理 `SourceManageSheets.kt:257` 删组、`:277` 重命名确认、`:300` 加组确认；导出结果 `:557` 复制路径、`:564` 口令分享。
  - 上述是否需要动作后补离场动画，属既有交互口径决策，建议随导航切换双槽位时序议题（报告 §10.3）一并另单评估。
- F-3（边界，报告 §10.2 已自行登记）：宿主级离场闸未接——`SourceManageViewModel.openSheet`（`:460`）与 Activity 结果回调未串联 `canOpen`；离场 ~450ms 内宿主触摸由浮层窗口截获兜底。与 reader 域 B30 同类，需另派单改 VM / Activity。
- F-4（架构债，报告 §10.1 已自行登记）：仓内并存 B21 `ReaderSheetExitGate`、B26 `NoteDialogExitGate`、config `SheetExitController`、本单 `SourceSheetExitController` 四份相近包装，另有并发单 `ui/sheet/HalfSheetExitHost.kt` 收束宿主；建议后续合并为泛型纯 JVM 单一守卫 + 复用 Compose 薄封装。

## 十一、未验证项

- 编译与单测：本地禁编译（沙箱无 gradle / JDK21 / SDK），未跑 `./gradlew` / `kotlinc`；`SourceSheetExitControllerTest` 7 例是否全绿、新增 import 与两个 `OverflowSheet` 的 `visible` 形参变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段窗口触摸截获，未在真机走查；观感由既有 `InkHalfSheet` 组件保证，本单仅接线状态口径。
- 动画时序：摘子树时机为代码静态推断（延时依据 = `MASK_DURATION_MS` 200ms + `RISE_DURATION_MS` 250ms），实际收束帧是否干净待 CI 出包后真机确认。
- 离场段配置重建：控制器由 `remember` 持有，旋转 / 进程重建随 Composable 生命周期；离场中重建则阶段与离场位不跨重建存活（与 B20 / B21 / B26 / config 同一边界，本单不解决）。
- 等效延时而非完成回调：`InkHalfSheet` 契约未暴露动画结束回调，沿用 B21 / B26 同一口径。
- 括号配平粗检（本验收本地 python3 剥离字符串 / 注释后计数）：5 个 main 文件 + 1 个测试文件 `{}` / `()` 全部配平；配平不等于编译通过，编译仍以 CI 为准。

## 十二、验收命令留痕汇总

- 清单基线：`git show "aee8f48:<file>" | sed -n "<line>p"`（12 条，见判据 1 命令 2）。
- 现役调用点：`grep -rn "InkHalfSheet(" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt`（12 处，见判据 1 命令 3）。
- 域内残留：判据 2 命令 1 / 2 / 3，source 域三类均 `exit=1` 零命中。
- 全仓残留：判据 2 命令 4（1 条红线）/ 命令 5（位置参数 `exit=1`）/ 命令 6（14 处无关布尔，逐条分类）。
- 复用链：判据 3 命令 1（控制器 import `:8`）；`NoteDialogExitGate.kt:3`；`ReaderSheetExitGate.kt:14-17`。
- 时长常数：判据 4 命令 1（`HalfSheetModel.kt:50` / `:53`）/ 命令 2（控制器 `:95`）/ 命令 3（魔法数字 `exit=1`）。
- 打开闸与控制器份数：判据 6 命令 1 / 命令 4（12 份独立控制器）。
- 测试：判据 8 命令（`@Test` 计数 7；测试名禁字符 `exit=1`）。
- 提交边界：判据 9 命令 1 / 2 / 3（`739f073` 5 文件全在 source 域；`7f2fb55` 仅文档）。
