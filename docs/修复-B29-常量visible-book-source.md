锚点卡：docs/锚点/Q461-B29book-source.md

# 修复-B29：book·source 域 12 条常量 visible 收口核实与两段式接线复核（Q461）

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74（本收尾方现取 sha256 前 12 位，与磁盘现行文件一致）。
- 逐字引用 1：「清单外不许新写代码；新增问题必须先给编号进表。」（docs/机制-总控必读.md:32）
- 逐字引用 2：「证明代码真的写入、证明旧符号真的清零」（docs/机制-子代理必读.md:18）
- 逐字引用 3：「不采信写码方的自评」（docs/机制-验收代理必读.md:8）
- 逐字引用 4：「报告写清这条判据对应哪份原始事实的哪一行；锚点可回源。」（docs/机制-文档子代理必读.md:16）
- 适用条款：只读核实为主，未改 config / autoTask / dict / replace 四域；复用既有 B26/B30 阶段机，未造第二份枚举与第二份派生口径；改动后先 grep 回证再下结论；每条判定带 文件:行 证据；未验证项如实披露，不把「报告里写了」当成已实现；禁 git 写、禁跑 gradlew / kotlinc / SDK，Kotlin 仅跑 `python3 data_scripts/kotlin-syntax-lint.py` 静态预检。

## 一、任务边界与判定口径

- 收口对象：`docs/问题清单.md:73` B29 行中 book·source 域 12 条，清单出自 `docs/修复-B26-常量visible接线.md` §6.3 第 7–18 条（命名参数 `visible = true`，条件渲染宿主 + dismiss 即摘子树，`InkHalfSheet` 内 `visible=false` 离场分支被跳过）。
- 范围红线：只核 `app/src/main/java/io/legado/app/ui/book/source/` 下与这 12 处浮层相关文件（本单允许动域为 `ui/book/` 与 `ui/source/`，现役仓内不存在 `ui/source/` 目录）；config / autoTask / dict / replace 四域零触碰；components 红线 `HandleFileSheet.kt` 不动；未新建第二套阶段机。
- 判定口径（先假设「已改」结论错误，逐条回现役代码核实）：
  1. 「已改」判据：`visible` 不再恒传 true，而是来自阶段机派生（`exit.visible` / `overflowExit.visible`）；关闭路径真的走 `beginExit()` → `SourceSheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树，不是 dismiss 即摘；打开点先过 `canOpen` 闸再 `open()`。
  2. 「未改」判据：现役代码仍恒传 true，或关闭路径仍直接摘子树。
  3. 基线对照：以 B29 source 收口提交 `739f073^`（即 `aee8f48`，落码前快照）为基线，逐条 `git show` 取基线原文；现役行号以 HEAD `f896179` 工作树为准。
- 现役提交链：`739f073`「修B29:source域浮层两段式收口(12条命名参数,SourceSheetExitController复用B26闸,7断言)」→ `6cb0198`「修B30:抽SheetExitPhaseGate纯JVM基类(枚举归位/isComposed单源/tryOpen宿主闸)」。本收尾方未做任何 `git add / commit / push`；`739f073` 已在 HEAD `f896179` 祖先链内（`git merge-base --is-ancestor 739f073 HEAD` 回证）。

## 二、12 条逐条核实清单（一条不漏）

> 路径缩写：`SRC=app/src/main/java/io/legado/app/ui/book/source`；`MGT=SRC/manage`。基线原文取自 `739f073^`；现役行号取自 HEAD `f896179`。12 条判定全部为 **已改**。

### 1. SourceManageSheets.kt 主选项 Sheet（B26 清单第 7 条）

- 基线：`MGT/SourceManageSheets.kt:85` 原文 `InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.book_source_manage_title), content = {`（`git show 739f073^:...:85` 回源）。基线关闭即 `vm::closeSheet` 清 VM 槽位摘子树。
- 现役现状：**已改**。
  - 控制器：`MGT/SourceManageSheets.kt:88` `val exit = rememberSourceSheetExitController()`。
  - 打开闸：`:89` `LaunchedEffect(Unit) { if (exit.canOpen) exit.open() }`。
  - 收束宿主：`:90` `SourceSheetExitHost(controller = exit, onFinished = vm::closeSheet)`（收束后才清 VM 槽位摘子树）。
  - 派生可见性：`:91` `visible = exit.visible`。
  - 关闭路径：`:91` `onDismiss = { exit.beginExit() }`；子树在离场段保留组合，`SourceSheetExitHost` 按遮罩 200ms + 下降 250ms 等效延时后 `finishExit()` 再回调 `vm::closeSheet`。

### 2. SourceManageSheets.kt 排序 Sheet（B26 清单第 8 条）

- 基线：`MGT/SourceManageSheets.kt:145` 原文 `InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.sort), content = {`。
- 现役现状：**已改**。控制器 `:144`；闸 `:145`；host `:155`（`onFinished = vm::closeSheet`）；派生 visible `:156`；dismiss `:156` `beginExit()`；选中动作 `:162` `{ vm.setSort(sort); exit.beginExit() }`（决策副作用先执行，再进离场段）。

### 3. SourceManageSheets.kt 分组筛选 Sheet（B26 清单第 9 条）

- 基线：`MGT/SourceManageSheets.kt:167` 原文 `InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.menu_action_group), content = {`。
- 现役现状：**已改**。控制器 `:179`；闸 `:180`；host `:181`；派生 visible `:182`；dismiss `:182`；预设选中离场 `:196`、分组选中离场 `:203`。

### 4. SourceManageSheets.kt 分组管理 Sheet（B26 清单第 10 条）

- 基线：`MGT/SourceManageSheets.kt:202` 原文 `InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.group_manage), content = {`。
- 现役现状：**已改**。控制器 `:218`；闸 `:219`；host `:220`；派生 visible `:221`；dismiss `:221`。组内删除 / 重命名 / 加组动作行未自带离场调用，属既有交互口径，登记 §九 F-3。

### 5. SourceManageSheets.kt 在线导入 Sheet（B26 清单第 11 条）

- 基线：`MGT/SourceManageSheets.kt:296` 原文 `InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.import_on_line), content = {`。
- 现役现状：**已改**。控制器 `:316`；闸 `:317`；host `:318`；派生 visible `:319`；dismiss `:319`；导入确认离场 `:347`（先 `vm.rememberImportUrl` + `actions.onImportOnline`，再 `beginExit()`）。

### 6. SourceManageSheets.kt 校验来源 Sheet（B26 清单第 12 条）

- 基线：`MGT/SourceManageSheets.kt:337` 原文 `InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.check_select_source), content = {`。
- 现役现状：**已改**。控制器 `:361`；闸 `:362`；host `:363`；派生 visible `:364`；dismiss `:364`；校验触发离场 `:378`（先 `actions.onCheckSource`，再 `beginExit()`）。

### 7. SourceManageSheets.kt 单来源菜单 Sheet（B26 清单第 13 条）

- 基线：`MGT/SourceManageSheets.kt:374` 原文 `InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = source.bookSourceName, content = {`。
- 现役现状：**已改**。控制器 `:402`；闸 `:403`；host `:404`；派生 visible `:405`；dismiss `:405`；动作行离场 `:410`（置顶）、`:415`（置底）、`:420`（登录）、`:422`（搜索）、`:423`（调试）、`:433`（启停发现）、`:439`（删源），副作用均先执行再 `beginExit()`。

### 8. SourceManageSheets.kt 多选统计 Sheet（B26 清单第 14 条）

- 基线：`MGT/SourceManageSheets.kt:422` 原文 `InkHalfSheet(visible = true, onDismiss = vm::closeSheet, title = stringResource(R.string.source_selection_count, selection.size), content = {`。
- 现役现状：**已改**。控制器 `:454`；闸 `:455`；host `:456`；派生 visible `:457`；dismiss `:457`；动作行离场 `:459`（启用）、`:460`（停用）、`:461`（还原）、`:462`（启用发现）、`:463`（停用发现）、`:473`（置顶）、`:474`（置底）、`:475`（导出）、`:476`（分享）、`:480`（校验间隔）。加组 / 移组 / 校验来源三行为跨 Sheet 导航切换，保留旧直摘，见 §七 N1。

### 9. SourceManageSheets.kt 分组输入 Sheet（B26 清单第 15 条）

- 基线：`MGT/SourceManageSheets.kt:459` 原文 `InkHalfSheet(visible = true, onDismiss = onDismiss, title = title, content = {`。
- 现役现状：**已改**。控制器 `:496`；闸 `:497`；host `:498`（`onFinished = onDismiss`，由 `ManageSheets` 分发处 `:68` / `:74` 转发 `vm::closeSheet`）；派生 visible `:499`；dismiss `:499`；分组确认离场 `:513`。`AddToGroup` / `RemoveFromGroup` 两槽复用本 Sheet，每次组合经 `:496` `remember` 新建控制器，不串阶段。

### 10. SourceManageSheets.kt 导出结果 Sheet（B26 清单第 16 条）

- 基线：`MGT/SourceManageSheets.kt:489` 原文 `InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_export_title), content = {`。
- 现役现状：**已改**。控制器 `:530`；闸 `:531`；host `:532`（`onFinished = onDismiss`，由 `ManageSheets` 分发处 `:76` 转发 `vm::closeSheet`）；派生 visible `:533`；dismiss `:533`。复制路径 / 口令分享动作行未自带离场调用，登记 §九 F-3。

### 11. SourceEditScreen.kt 溢出菜单 Sheet（B26 清单第 17 条）

- 基线：`SRC/edit/SourceEditScreen.kt:426` 原文 `InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_edit_title), content = {`。
- 现役现状：**已改**。
  - 控制器：`SRC/edit/SourceEditScreen.kt:89` `val overflowExit = rememberSourceSheetExitController()`。
  - 打开闸：`:101` `if (overflowExit.canOpen) {` → `:102` `overflowOpen = true` → `:103` `overflowExit.open()`。
  - 收束宿主：`:155` `SourceSheetExitHost(overflowExit, onFinished = { overflowOpen = false })`；宿主条件渲染 `:154` `if (overflowOpen) {`，收束前子树保留。
  - 派生可见性：`:160` `visible = overflowExit.visible`；dismiss `:161` `onDismiss = { overflowExit.beginExit() }`。
  - 内层 `OverflowSheet`：`:435` 新增 `visible: Boolean` 形参；`:438` `InkHalfSheet(visible = visible, onDismiss = onDismiss, ...)` 透传派生可见性。自动补全开关行不自动关层，登记 §九 F-3。

### 12. SourceDebugScreen.kt 溢出菜单 Sheet（B26 清单第 18 条）

- 基线：`SRC/debug/SourceDebugScreen.kt:401` 原文 `InkHalfSheet(visible = true, onDismiss = onDismiss, title = stringResource(R.string.source_debug_title), content = {`。
- 现役现状：**已改**。
  - 控制器：`SRC/debug/SourceDebugScreen.kt:79` `val overflowExit = rememberSourceSheetExitController()`。
  - 打开闸：`:93` `if (overflowExit.canOpen) {` → `:94` `overflowOpen = true` → `:95` `overflowExit.open()`。
  - 收束宿主：`:118` `SourceSheetExitHost(overflowExit, onFinished = { overflowOpen = false })`；宿主条件渲染 `:117` `if (overflowOpen) {`。
  - 派生可见性：`:121` `visible = overflowExit.visible`；dismiss `:122` `onDismiss = { overflowExit.beginExit() }`。
  - 内层 `OverflowSheet`：`:409` `visible: Boolean` 形参；`:413` `InkHalfSheet(visible = visible, onDismiss = onDismiss, ...)` 透传。扫码 / 查看响应 / 刷新发现 / 帮助等动作行不自动关层，登记 §九 F-3。

### 12 条汇总

- 已改：12 条；未改：0 条；本收尾方本轮未补改任何 main 源集代码（现役代码已在 `739f073` 落码、`6cb0198` 完成 B30 单源收敛）。
- 12 条关闭路径全部为 `onDismiss = { xxxExit.beginExit() }` → `SourceSheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树；无一处 dismiss 即摘子树。
- 控制器实例化 12 份（manage 10 + edit 1 + debug 1），每槽位 / 每屏溢出菜单各持独立控制器，未上提 VM、未跨 Sheet 共享阶段态。

## 三、阶段机复用与单源派生核查（无第二份口径）

### 3.1 复用链（只引既有，未造第二套）

- 阶段枚举三态 `OPEN / EXITING / REMOVED` 定义唯一处：`app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:12` `enum class ReaderSheetExitPhase {`（成员 `:15 OPEN`、`:16 EXITING`、`:17 REMOVED`）。
- 派生口径唯一事实源（基类）：`SheetExitPhaseGate.kt:144-145` `visible = phase == ReaderSheetExitPhase.OPEN`；`:148-149` `isComposed = phase != ReaderSheetExitPhase.REMOVED`；`:152-153` `canOpen = phase != ReaderSheetExitPhase.EXITING`。
- B26 守卫继承该基类：`app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:19` `class NoteDialogExitGate : SheetExitPhaseGate()`，自身只补 `:22` `open() = enterOpen()`，未自写 `visible / isComposed / canOpen` 判断。
- source 域薄封装 `SRC/SourceSheetExitController.kt`：`:8` `import io.legado.app.ui.book.read.NoteDialogExitGate`；`:39` `visible`、`:43` `isComposed`、`:47` `canOpen` 全部 `get() = gate.xxx` 委托基类派生；`:55` `open()`、`:58` `beginExit()`、`:64` `finishExit()` 转发守卫；`:74` `rememberSourceSheetExitController()` 工厂；`:89` `SourceSheetExitHost` 收束宿主。
- 控制器内无自写阶段比较、无第二份迁移规则；`isExiting`（`:51`）只是 Compose 离场位镜像，不参与派生判断。

### 3.2 第二份阶段机排查（grep 回证）

- 命令：`grep -rnE "enum class|sealed (class|interface)|ExitGate|ExitPhase|Stage" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt`。
- 命中枚举 / sealed 类型逐一核对均非离场阶段机：`debug/SourceDebugScreen.kt:48` `DebugSrcKind`（调试响应类别）；`edit/JsSourceEditFlow.kt:3` `JsSourceEditStage`、`:15` `JsSourceEditRestoreAction`（JS 源编辑流程步骤 / 恢复动作）；`edit/SourceEditState.kt:13` `EditTab`（编辑页标签）；`manage/SourceManageState.kt:15` `BookSourceSort`、`:20` `SourcePreset`、`:42` `sealed interface ManageSheet`（槽位模型）；`manage/BookSourceActivity.kt:278` `ExportStage`（导出结果数据类）。
- source 域内无第二份离场守卫类、无第二份阶段枚举、无第二份 OPEN→EXITING→REMOVED 迁移规则；除 `NoteDialogExitGate` 外无其他离场闸 import。

### 3.3 时长同源（延时引组件常数，无魔法数）

- 遮罩时长：`app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt:94` `const val DURATION_MASK: Int = 200`；`app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt:53` `MASK_DURATION_MS: Int = 200`（同值两处持有，`InkHalfSheet.kt:119` / `:124` 遮罩 tween 引用 `HalfSheetAnchors` 版）。
- 下降时长：`HalfSheetModel.kt:50` `RISE_DURATION_MS: Int = 250`。
- 收束延时：`SRC/SourceSheetExitController.kt:96` `delay((HalfSheetAnchors.MASK_DURATION_MS + HalfSheetAnchors.RISE_DURATION_MS).toLong())`，两段常数相加（200 + 250 = 450ms），与 B26 `HighlightNoteDialog.kt:108` 同一口径；控制器内无 200 / 250 / 450 硬编码（grep `\b(200|250|450)\b` 空命中）。
- 组件离场分支前提：`InkHalfSheet.kt:121-130` `visible=false` 分支依次遮罩回亮 200ms、Sheet 下降 250ms；`:133` `if (progress.value == 0f && !visible) return` 收束条件；派生 `visible=false` 确能播组件内离场动画。

## 四、grep 回证前后对照（命令 + 计数 + 行号）

### 4.1 旧常量传法命中数：前 vs 后

- 基线（`739f073^`，B29 落码前）source 域：命名参数 `visible = true` 12 条（manage 10、edit 1、debug 1，逐条基线原文见 §二）；位置参数 `Sheet(true, ...)` 0 条；多行 `true,` 首参 0 条；`SourceSheetExitController.kt` 在基线不存在（`git show 739f073^:.../SourceSheetExitController.kt` fatal: path exists on disk, but not in commit）。
- 现役（HEAD `f896179`）source 域命名参数命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt
```

原样输出：空；`exit=1`（命名参数残留 0）。

- 现役 source 域位置参数命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt
```

原样输出：空；`exit=1`（位置参数残留 0）。

- 现役 source 域多行 `true,` 兜底：

```
grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt
```

原样输出：空；`exit=1`（多行首参残留 0）。

### 4.2 新派生写法命中行号（现役）

- 12 处 `InkHalfSheet` 调用点全部传派生 visible，逐条行号：
  - `MGT/SourceManageSheets.kt:91`（`exit.visible`，主选项）
  - `MGT/SourceManageSheets.kt:156`（`exit.visible`，排序）
  - `MGT/SourceManageSheets.kt:182`（`exit.visible`，分组筛选）
  - `MGT/SourceManageSheets.kt:221`（`exit.visible`，分组管理）
  - `MGT/SourceManageSheets.kt:319`（`exit.visible`，在线导入）
  - `MGT/SourceManageSheets.kt:364`（`exit.visible`，校验来源）
  - `MGT/SourceManageSheets.kt:405`（`exit.visible`，单来源菜单）
  - `MGT/SourceManageSheets.kt:457`（`exit.visible`，多选统计）
  - `MGT/SourceManageSheets.kt:499`（`exit.visible`，分组输入）
  - `MGT/SourceManageSheets.kt:533`（`exit.visible`，导出结果）
  - `SRC/edit/SourceEditScreen.kt:438`（内层透传形参 `visible`；外层派生 `:160` `overflowExit.visible`）
  - `SRC/debug/SourceDebugScreen.kt:413`（内层透传形参 `visible`；外层派生 `:121` `overflowExit.visible`）
- 闸位与打开点：控制器实例化 12 处（manage `:88` / `:144` / `:179` / `:218` / `:316` / `:361` / `:402` / `:454` / `:496` / `:530`；edit `:89`；debug `:79`）；打开点 12 个全部先过 `canOpen` 再 `open()`（manage 10 处 `LaunchedEffect(Unit)` 程序化登记；edit `:101-103`、debug `:93-95` 回调入口）；`SourceSheetExitHost` 实例化 12 处；`beginExit()` 业务调用 35 处（12 个 dismiss 入口 + 23 个动作行），另控制器内部转发 1 处。
- 全仓 `visible = true` 现役分类（非 source 域，不计本单 12 条口径）：`book/read/ink/InkContentEditState.kt:93`、`:232`（状态持有类内部 `this.visible = true` 赋值，非浮层传参，reader 域外）；`components/HandleFileSheet.kt:47`（components 红线，契约冻结）；`config/b3/B3SettingScreen.kt:151` / `:164` / `:180` / `:193`（B3 批次新增，config 域 F-1 已在 `docs/收尾-B29config域13条.md` §七登记）；`main/bookshelf/InkShelfFragment.kt:286`（注释文字，非代码）。autoTask / dict / replace 三域现役命名参数残留均为 0（沿用各自分域单验收结论）。

### 4.3 静态预检

- 命令：`python3 data_scripts/kotlin-syntax-lint.py` 传入本批 5 个相关文件（4 个 main 源集 + 1 个测试）。
- 原样输出：`KOTLIN-LINT: PASS（5 个文件）`，`exit=0`。括号配平与 if/while 条件尾逗号两项硬错零命中；未跑 `./gradlew` / `kotlinc`，编译与单测结果以云端 CI 为准。

## 五、判定不改 / 留档条目（逐条读过，非静默跳过）

- N1. 同屏导航切换直摘按钮群——**保留旧直摘口径，不改**，共 7 处：
  - `MGT/SourceManageSheets.kt:97-100` 在线导入：`vm.closeSheet()` → `vm.openSheet(ManageSheet.OnlineImport)`，进下一屏 Sheet。
  - `:102-105` 分组管理：进 `GroupManageSheet`。
  - `:106-109` 排序：进 `SortSheet`。
  - `:110-113` 分组筛选：进 `GroupFiltersSheet`。
  - `:467` 加入分组：`vm.openSheet(ManageSheet.AddToGroup(selection))`，进 `GroupInputSheet`。
  - `:471` 移出分组：进 `GroupInputSheet`（移组槽）。
  - `:478` 校验来源：进 `CheckSourceSheet`。
  - 7 处全部为跨 Sheet 导航（打开下一级浮层），无一是「同一浮层关闭」；旧直摘与导航切换同期发生，沿用 config 域 N3 先例。遮罩 / 拖拽关闭仍走各自两段式控制器。若验收要求跳转前先播完离场动画，需另派单设计「旧槽离场保留 + 新槽待起」双槽位时序。
- N2. `components/HandleFileSheet.kt:47`——**不改（红线）**。components 契约冻结，本单零改动。
- N3. `debug/SourceDebugScreen.kt:48` `DebugSrcKind`、`edit/JsSourceEditFlow.kt` 两处流程枚举、`edit/SourceEditState.kt:13` `EditTab`、`manage/SourceManageState.kt` 排序 / 预设 / 槽位 sealed——**不改**。均为业务模型枚举，非离场阶段机，见 §3.2 排查。

## 六、自查反例（先假设「已改」结论错误，逐条找推翻证据并排除）

- 反例 1：派生 visible 看似来自控制器，但控制器若绕过基类自写阶段判断，单源口径即破。核查：`NoteDialogExitGate` 全文仅 `open()` 转发 `enterOpen()`，无自写 `visible / isComposed / canOpen`；`SourceSheetExitController.kt:39` / `:43` / `:47` 三处均为 `get() = gate.xxx` 委托；基类派生唯一处在 `SheetExitPhaseGate.kt:144-153`。排除：无第二份派生表达式。
- 反例 2：关闭路径看似两段式，实际 onDismiss 仍直清槽位、子树瞬摘，`beginExit()` 是空挂。核查：§二 12 条 onDismiss 全部为 `{ xxxExit.beginExit() }`；槽位清空只出现在 `SourceSheetExitHost(onFinished = ...)` 回调内（`:98` 延时收束后），宿主槽位状态本身不在 dismiss 出口被直接置空；manage 槽位 `vm::closeSheet` 仅在 host 收束回调与导航切换行出现。排除：无 dismiss 即摘。
- 反例 3：离场段重复打开是否被拦。核查：`canOpen` 转发基类 `phase != EXITING`；12 个打开点全部先过闸再 `open()`；离场段重复 `beginExit()` 由守卫空操作幂等处理（测试 `SourceSheetExitControllerTest.kt:63-73` 覆盖）。排除：离场段可叠新弹层的竞态不成立。
- 反例 4：source 域是否另造了本地阶段枚举 / 布尔状态机。核查：§3.2 grep 命中的枚举 / sealed 全是业务模型（响应类别 / 编辑流程 / 标签页 / 排序键 / 槽位模型 / 导出数据类），无离场阶段枚举；无第二份守卫类与迁移规则。排除：无第二套阶段机。
- 反例 5：未打开 / 已摘除时 visible 是否仍恒显。核查：基类 REMOVED 段 `visible=false`；控制器初始 `exitingState=false`、守卫初始 REMOVED；测试 `SourceSheetExitControllerTest.kt:27-35` 覆盖初始摘除态不可见。排除：无恒显。
- 反例 6：12 条之外是否漏算 source 域常量 true 调用点。核查：source 域三类写法（命名参数 / 位置参数 / 多行 `true,`）grep 全部 `exit=1` 零命中；`InkHalfSheet(` 调用点全量扫描共 12 处，与清单 12 条一一对应，无第 13 个浮层调用点。排除：无漏算、无多改。
- 反例 7：报告自述「已落码」是否与现役代码一致。核查：未采信旧报告自述，逐条 `git show 739f073^` 取基线 + 现役源码回读；12 条基线行均为 `visible = true`，现役均已派生；落码提交 `739f073` 已在 HEAD `f896179` 祖先链内。排除：自述与代码一致，无「报告写了、代码没变」。
- 反例 8：Compose 侧是否新引入了依赖 Application 初始化的配置读取（Paparazzi 截图环境无 Application，曾炸四个书架帧）。核查：控制器与 edit / debug 两屏 Compose 代码零 `AppConfig` / `appCtx` / `SharedPreferences` 引用；manage 屏仅 `:131` 一处既有 `AppConfig.blockSourceNavigation` 读取，属清单前历史代码、非本单两段式改动引入，登记 §九 F-1，不在本单顺手扩改。排除：本单未引入新的 Application 依赖读取。

## 七、新发现问题（每条带处置状态词）

- F-1（待另派单 · 登记跟踪，Paparazzi 风险同类）：`MGT/SourceManageSheets.kt:131` `checked = io.legado.app.help.config.AppConfig.blockSourceNavigation` 在 Compose 组合期直读 `AppConfig` 静态属性；该属性 getter 走 `appCtx.getPrefBoolean(...)`（`app/help/config/AppConfig.kt:47-49`），`appCtx` 为 splitties Application 上下文，截图环境无 Application 时类初始化即炸，与 4738fb4 修复的四个书架帧同形态。同屏另外两个偏好（`showCheckStatus` / `showBookshelfCount`）已由 VM 在 `SourceManageViewModel.kt:68-69` 读入 state 透传，`blockSourceNavigation` 未入 state；VM 翻转在 `SourceManageViewModel.kt:235` 直写 `AppConfig`。处置建议：把 `blockSourceNavigation` 补进 `SourceManageState`（`SourceManageState.kt:94-96` 同型字段），VM 初始化读入、翻转时更新 state，Compose 改读 `state.xxx`；本单按范围红线未改。
- F-2（留观 · 阶段账不闭环，沿用 B29 验收 F-3）：宿主级离场闸未接——`SourceManageViewModel.openSheet`（`SourceManageViewModel.kt:460`）与 `SourceManageScreen.kt:98` / `:378` / `:460`、`BookSourceActivity.kt:67` 四个程序化打开点未串联 `canOpen`；离场 ~450ms 内宿主触摸由浮层窗口截获兜底。与 reader 域 B30 同类边界，需另派单改 VM / Activity 入口闸。
- F-3（留观 · 交互缺口，非常量 visible 缺陷本身，沿用 B29 验收 F-2）：以下动作行点击后不调用 `onDismiss` / `beginExit`，弹层不自动关闭，基线 `739f073^` 即如此，本单未改动其交互：
  - 调试页溢出菜单 `SRC/debug/SourceDebugScreen.kt:415`（扫码）、`:417`（查看搜索响应）、`:420`（查看详情响应）、`:423`（查看目录响应）、`:426`（查看正文响应）、`:428`（刷新发现）、`:429`（帮助）。
  - 编辑页自动补全开关 `SRC/edit/SourceEditScreen.kt:448` `vm.toggleAutoComplete()`（菜单保持展开，基线同）。
  - 分组管理 `MGT/SourceManageSheets.kt:257` 删组、`:277` 重命名确认、`:300` 加组确认；导出结果 `:557` 复制路径、`:564` 口令分享。
  - 上述是否需要动作后补离场动画，属既有交互口径决策，建议随 §五 N1 双槽位时序议题一并另单评估。
- F-4（待 CI 复核 · 沿用 B29 验收 F-4）：`SRC/test/.../SourceSheetExitControllerTest.kt:3` / `:25` 使用 `mutableStateOf` 构造控制器；`mutableStateOf` 属 compose-runtime 普通工厂函数，JVM 下可实例化本身成立，但 7 例单测能否在单测 classpath 跑绿以云端 CI 为准。
- F-5（既有边界 · 本单不解决）：控制器由 `remember` 持有，旋转 / 进程重建随 Composable 生命周期；离场中重建则阶段与离场位不跨重建存活（与 B20 / B21 / B26 / config 同一边界）。
- F-6（架构债 · 登记，沿用 B26 §6.4 与 config 收口 §10.1）：仓内并存 B21 `ReaderSheetExitGate`、B26 `NoteDialogExitGate`、config `SheetExitController`、本域 `SourceSheetExitController` 四份相近包装，另有并发单 `ui/sheet/HalfSheetExitHost.kt` 收束宿主；建议后续合并为泛型纯 JVM 单一守卫 + 复用 Compose 薄封装，消除第三 / 四份包装复制。

## 八、未验证项与边界

- 编译与单测：沙箱无 gradle / JDK21 / SDK，未跑 `./gradlew` / `kotlinc`；`SourceSheetExitControllerTest` 7 例是否全绿、两个 `OverflowSheet` 的 `visible` 形参变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段窗口触摸截获范围，未真机走查；观感由既有 `InkHalfSheet` 组件保证，本收尾方仅静态接线口径核实。
- 动画时序：摘子树时机为代码静态推断（延时依据 = `MASK_DURATION_MS` 200ms + `RISE_DURATION_MS` 250ms），实际收束帧是否干净待 CI 出包后真机确认。
- 等效延时而非完成回调：`InkHalfSheet` 契约未暴露动画结束回调，沿用 B21 / B26 同一口径；日后组件补完成回调，可把 `SourceSheetExitHost.kt:96` 的 `delay` 替换为回调触发，守卫阶段口径不变。
- 域外四域（autoTask 4 / config 13 / dict 2 / replace 3）已由各自分域单验收 / 收口；本单未复核其实机表现。
- 工作树存在本单范围外并发 WIP：`app/src/main/java/io/legado/app/ui/main/bookshelf/InkShelfFragment.kt` 有未提交改动（bookshelf 域），本收尾方未读其 diff、未触碰。

## 九、收口结论

- **book·source 域 12 条已处置完毕（静态层；编译 / 单测等 CI）。**
- 依据：
  1. 12 条逐条核实为「已改」、0 条「未改」：现役 `visible` 全部来自 `SourceSheetExitController` 派生（转发 B26 `NoteDialogExitGate`，继承 B30 `SheetExitPhaseGate` 基类单源派生），无一处恒传 true；关闭路径全部走 `beginExit()` → `SourceSheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树，无 dismiss 即摘。
  2. 阶段机复用既有链路，source 域无第二份枚举、无第二份守卫类、无第二份派生表达式（§3.2 grep 回证）。
  3. 12 个打开点全部过 `canOpen` 闸；12 份独立控制器不串阶段；收束延时只引组件时长常数，无魔法数。
  4. 静态预检 5 个相关文件 `KOTLIN-LINT: PASS`；7 例纯 JVM 断言齐全，测试名无禁字符。
- 附条件：F-1（Compose 直读 AppConfig）、F-2（宿主级闸未接）、F-3（部分动作行不自动关层）为清单外登记项，不在 B29 12 条缺陷口径内；在 F-1 / F-2 收口前，「source 域全域零 Application 依赖读取」「宿主级双闸闭环」的绝对结论不成立，本结论仅覆盖 B26 清单 book·source 域 12 条。
