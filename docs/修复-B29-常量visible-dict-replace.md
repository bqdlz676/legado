锚点卡：docs/锚点/Q463-B29dict-replace.md

# 修复-B29：dict 域 2 条 + replace 域 3 条常量 visible 收口核实与两段式接线复核（Q463）

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74（本收尾方现取 `python3 data_scripts/check-onboarding.py --emit` 同批指纹，与磁盘现行文件一致）。
- 逐字引用 1：「规则：清单外不许新写代码；新增问题必须先给编号进表。」（docs/机制-总控必读.md:32）
- 逐字引用 2：「**grep 回证**：每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。没贴 grep 的改动视为未改。」（docs/机制-子代理必读.md:18）
- 逐字引用 3：「**不采信写码方的自评**：它说"已验证"不算证据；它给的计数要你自己重算。」（docs/机制-验收代理必读.md:8）
- 逐字引用 4：「**溯源**：报告写清这条判据对应哪份原始事实的哪一行；锚点可回源。」（docs/机制-文档子代理必读.md:16）
- 适用条款：只核 `app/src/main/java/io/legado/app/ui/dict/` 与 `app/src/main/java/io/legado/app/ui/replace/`（含其 ViewModel / Screen / Activity 回调接线）；未碰 config / book·source / autoTask 三域；复用既有 B21/B26/B29/B30 阶段机链路，未造第二份枚举、第二份守卫、第二份派生口径；改动后先 grep 回证再下结论；每条判定带 文件:行 证据；未验证项如实披露，不把「报告里写了」当成已实现；禁 git 写、禁跑 gradlew / kotlinc / SDK，Kotlin 仅跑 `python3 data_scripts/kotlin-syntax-lint.py` 静态预检。

## 一、任务边界与判定口径

- 收口对象：`docs/问题清单.md:73` B29 行中 dict 域 2 条 + replace 域 3 条，清单出自 `docs/修复-B26-常量visible接线.md` §6.3 第 32–36 条（命名参数 `visible = true`，条件渲染宿主 + dismiss 即摘子树，`InkHalfSheet` 内 `visible=false` 离场分支被跳过）。
- 范围红线：只核两域目录下与这 5 处浮层相关文件；config / book·source / autoTask 三域零触碰；components 红线 `HandleFileSheet.kt` 不动；未新建第二套阶段机；两域内若发现同一缺陷蔓延到第 6 处，只登记不扩改。
- 判定口径（先假设「已改」结论错误，逐条回现役代码核实）：
  1. 「已改」判据：`visible` 不再恒传 true，而是来自阶段机派生（`exit.visible`）；关闭路径真的走 `beginExit()` → `HalfSheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树，不是 dismiss 即摘；打开点先过 `canOpen` 闸再 `open()`。
  2. 「未改」判据：现役代码仍恒传 true，或关闭路径仍直接摘子树。
  3. 基线对照：以 B29 落码提交 `9e43760^`（短哈希 `780ec90`，落码前快照）为基线，逐条 `git show` 取基线原文；现役行号以 HEAD `60545a9` 工作树为准。
- 现役提交链：`9e43760`「修B29:autoTask/dict/replace三域浮层两段式收口(HalfSheetExitHost复用config控制器,8断言)」→ `6cb0198` B30 抽纯 JVM 基类；`9e43760` 已在 HEAD `60545a9` 祖先链内（`git merge-base --is-ancestor 9e43760 HEAD` 回证 exit=0）。本收尾方未做任何 `git add / commit / push`；工作树 main 源集 Kotlin 文件零改动（`git status --short | grep -v docs/` 空命中）。

## 二、5 条逐条核实清单（一条不漏）

> 路径缩写：`DICT=app/src/main/java/io/legado/app/ui/dict/rule`；`REP=app/src/main/java/io/legado/app/ui/replace`；`RED=REP/edit`。基线原文取自 `780ec90`；现役行号取自 HEAD `60545a9`。5 条判定全部为 **已改**。

### 1. DictRuleScreen.kt 规则编辑 Sheet（B26 清单第 32 条）

- 基线：`DICT/DictRuleScreen.kt:377` 原文 `InkHalfSheet(visible = true, onDismiss = onDismiss, title = if (creating) "新建词典规则" else "编辑词典规则", content = {`（`git show 780ec90:...:377` 回源）。基线关闭经 `onDismiss` 直调 `dismissEdit` 清 `editTarget` 摘子树；基线 `DictRuleListViewModel.kt:185` `save(...)` 末尾直调 `dismissEdit()`，入库后即摘。
- 现役现状：**已改**。
  - 控制器：`DICT/DictRuleScreen.kt:88` `val editExit = rememberSheetExitController()`。
  - 打开闸：`:90-91` `LaunchedEffect(state.editTarget) { if (state.editTarget != null && editExit.canOpen) editExit.open() }`。
  - 收束宿主：`:150` `HalfSheetExitHost(editExit, onFinished = onDismissEdit)`（收束后才清 VM 槽位摘子树）。
  - 派生可见性：`:154` `visible = editExit.visible`。
  - 关闭路径：`:156` `onDismiss = { editExit.beginExit() }`；子树在离场段保留组合，`HalfSheetExitHost` 按遮罩 200ms + 下降 250ms 等效延时后 `finishExit()` 再回调 `onDismissEdit`。
  - 内层 `EditSheet`：`:383` 新增 `visible: Boolean` 形参；`:397` `InkHalfSheet(visible = visible, onDismiss = onDismiss, ...)` 透传派生可见性。
  - VM 侧配合：`DICT/DictRuleListViewModel.kt:172-187` `save(rule:)` 不再内部直清 `editTarget`（`:185-186` 注释明写「槽位摘除交 HalfSheetExitHost 离场收束回调」）；`:168-170` `dismissEdit()` 仅由宿主收束回调经 `DictRuleActivity.kt:86` `onDismissEdit = viewModel::dismissEdit` 调用。
  - 保存按钮序列：`DICT/DictRuleScreen.kt:405-410` 先 `onSave(...)` 执行入库副作用，再 `onDismiss()` 进离场段；副作用时机与旧版一致。

### 2. DictRuleScreen.kt 帮助页（B26 清单第 33 条）

- 基线：`DICT/DictRuleScreen.kt:416` 原文 `InkHalfSheet(visible = true, onDismiss = onDismiss, title = "帮助", content = {`。
- 现役现状：**已改**。控制器 `:89` `helpExit`；闸 `:93-94` `LaunchedEffect(state.helpText) { if (state.helpText != null && helpExit.canOpen) helpExit.open() }`；host `:176` `HalfSheetExitHost(helpExit, onFinished = onDismissHelp)`；派生 visible `:177` `visible = helpExit.visible`；dismiss `:177` `onDismiss = { helpExit.beginExit() }`；内层 `HelpSheet` 形参 `:436`、透传 `:437` `InkHalfSheet(visible = visible, ...)`。VM `DICT/DictRuleListViewModel.kt:223` `showHelp` / `:227-229` `dismissHelp`；Activity `DICT/DictRuleActivity.kt:89` `onDismissHelp = viewModel::dismissHelp`。

### 3. ReplaceRuleScreen.kt 帮助页（B26 清单第 34 条）

- 基线：`REP/ReplaceRuleScreen.kt:198` 原文 `InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {`。基线 dismiss 直调 `onDismissHelp` 清 `helpText` 摘子树。
- 现役现状：**已改**。控制器 `:108` `helpExit`；闸 `:112-113` `LaunchedEffect(state.helpText) { if (state.helpText != null && helpExit.canOpen) helpExit.open() }`；host `:214` `HalfSheetExitHost(helpExit, onFinished = { onDismissHelp() })`；`InkHalfSheet` 调用 `:215`；派生 visible `:216` `visible = helpExit.visible`；dismiss `:217` `onDismiss = { helpExit.beginExit() }`。VM `REP/ReplaceRuleListViewModel.kt:289` `showHelp` / `:290` `dismissHelp`；Activity `REP/ReplaceRuleActivity.kt:113` `onDismissHelp = viewModel::dismissHelp`。

### 4. ReplaceRuleScreen.kt 分组管理 Sheet（B26 清单第 35 条）

- 基线：`REP/ReplaceRuleScreen.kt:500` 原文 `InkHalfSheet(visible = true, onDismiss = onDismiss, title = "分组管理", content = {`；基线屏级 `:173` `onDismiss = onCloseGroupManage`，遮罩 / 下拉关闭即 `closeGroupManage()` 置 `groupManageOpen = false` 摘子树。
- 现役现状：**已改**。控制器 `:107` `groupExit`；闸 `:109-110` `LaunchedEffect(state.groupManageOpen) { if (state.groupManageOpen && groupExit.canOpen) groupExit.open() }`；host `:180` `HalfSheetExitHost(groupExit, onFinished = onCloseGroupManage)`；派生 visible `:183` `visible = groupExit.visible`；dismiss `:188` `onDismiss = { groupExit.beginExit() }`；内层 `GroupManageSheet` 形参 `:518`、透传 `:526` `InkHalfSheet(visible = visible, ...)`。VM `REP/ReplaceRuleListViewModel.kt:255` `openGroupManage` / `:256` `closeGroupManage`；Activity `REP/ReplaceRuleActivity.kt:105-106` 回调接线。
- 组内动作口径：`REP/ReplaceRuleListViewModel.kt:258` `addGroup` / `:266` `renameGroup` / `:277` `deleteGroup` 均不内部关闭 Sheet（列表原地刷新，旧口径保留）；遮罩 / 下拉 / 返回关闭走两段式。

### 5. ReplaceEditScreen.kt 帮助页（B26 清单第 36 条）

- 基线：`RED/ReplaceEditScreen.kt:181` 原文 `InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {`。
- 现役现状：**已改**。控制器 `:61` `helpExit`；闸 `:62-63` `LaunchedEffect(state.helpText) { if (state.helpText != null && helpExit.canOpen) helpExit.open() }`；host `:189` `HalfSheetExitHost(helpExit, onFinished = { onDismissHelp() })`；`InkHalfSheet` 调用 `:190`；派生 visible `:191` `visible = helpExit.visible`；dismiss `:192` `onDismiss = { helpExit.beginExit() }`。VM `RED/ReplaceEditViewModel.kt:176` `showHelp` / `:177` `dismissHelp`；Activity `RED/ReplaceEditActivity.kt:83` `onDismissHelp = viewModel::dismissHelp`。

### 5 条汇总

- 已改：5 条；未改：0 条；本收尾方本轮未补改任何 main 源集代码（现役代码已在 `9e43760` 落码、`6cb0198` 完成 B30 单源收敛）。
- 5 条关闭路径全部为 `onDismiss = { xxxExit.beginExit() }` → `HalfSheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树；无一处 dismiss 即摘子树。
- 控制器实例化 5 份（dict 编辑 / dict 帮助 / replace 分组 / replace 帮助 / replace 编辑帮助），每槽位各持独立控制器，未上提 VM、未跨 Sheet 共享阶段态。

## 三、阶段机复用与单源派生核查（无第二份口径）

### 3.1 复用链（只引既有，未造第二套）

- 阶段枚举三态 `OPEN / EXITING / REMOVED` 定义唯一处：`app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:12` `enum class ReaderSheetExitPhase {`。
- 派生口径唯一事实源（基类）：`SheetExitPhaseGate.kt:144-145` `visible = phase == ReaderSheetExitPhase.OPEN`；`:148-149` `isComposed = phase != ReaderSheetExitPhase.REMOVED`；`:152-153` `canOpen = phase != ReaderSheetExitPhase.EXITING`。
- B26 守卫继承该基类：`app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:19` `class NoteDialogExitGate : SheetExitPhaseGate()`，自身只补 `:22` `open() = enterOpen()`，未自写 `visible / isComposed / canOpen` 判断。
- 两域复用 config 域薄封装 `app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt:39-40`（`visible`）、`:43-44`（`isComposed`）、`:47-48`（`canOpen`）全部 `get() = gate.xxx` 委托基类派生；同文件 `:55` `open()`、`:58-61` `beginExit()`、`:64-67` `finishExit()` 转发守卫；`:74-77` `rememberSheetExitController()` 工厂。控制器内无自写阶段比较、无第二份迁移规则；同文件 `:51-52` `isExiting` 只是 Compose 离场位镜像，不参与派生判断。
- 两域共用收束宿主：`app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt:29` `HalfSheetExitHost(...)`；`:34` `LaunchedEffect(controller.isExiting)`；`:36` `delay(HalfSheetExitDurations.exitMs)`；`:37` `controller.finishExit()`；`:38` `onFinished()`；`:49-52` `HalfSheetExitDurations.exitMs` 两段常数相加。

### 3.2 第二份阶段机排查（grep 回证）

- 命令：`grep -rnE "enum class|sealed (class|interface)|ExitGate|ExitPhase|Stage" app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt`。
- 命中枚举 / sealed 类型逐一核对均非离场阶段机：`dict/DictRichContent.kt:59` `sealed interface DictRichBlock`（富文本块模型）；`replace/ReplaceRuleModels.kt:60` `sealed class ReplaceFilter`（筛选条件模型）；`replace/ReplaceRuleScreen.kt:237` `private enum class BatchGroup { ADD, REMOVE }`（批量入组 / 移组动作）；`replace/edit/ReplacePreview.kt:239` `enum class Reason`（预览错误分类）。
- 两域内无第二份离场守卫类、无第二份阶段枚举、无第二份 OPEN→EXITING→REMOVED 迁移规则；除经 `SheetExitController` 间接复用 `NoteDialogExitGate` 外，无其他离场闸 import。

### 3.3 时长同源（延时引组件常数，无魔法数）

- 遮罩时长：`app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt:53` `MASK_DURATION_MS: Int = 200`；`app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt:94` `DURATION_MASK: Int = 200`（同值两处持有；`ComponentPalette.DURATION_MASK` 供 config `InkBottomSheet` 单段 tween，本批 5 处为 `InkHalfSheet`，取 `HalfSheetAnchors` 两段口径）。
- 下降时长：`HalfSheetModel.kt:50` `RISE_DURATION_MS: Int = 250`。
- 收束延时：`HalfSheetExitHost.kt:36` 引 `HalfSheetExitDurations.exitMs`（`:52` = `MASK_DURATION_MS + RISE_DURATION_MS`，200 + 250 = 450ms），与 B26 `HighlightNoteDialog.kt:110` 同一口径；宿主 / 控制器内无 200 / 250 / 450 硬编码。
- 组件离场分支前提：`InkHalfSheet.kt:110` `LaunchedEffect(visible)`；`:121-130` `visible=false` 分支依次遮罩回亮 200ms、Sheet 下降 250ms；`:133` `if (progress.value == 0f && !visible) return` 收束条件；派生 `visible=false` 确能播组件内离场动画。

## 四、grep 回证前后对照（命令 + 计数 + 行号）

### 4.1 旧常量传法命中数：前 vs 后

- 基线（`780ec90`，B29 落码前）dict + replace 两域：命名参数 `visible = true` 共 5 条（dict 2、replace 3，逐条基线原文见 §二）；位置参数 `Sheet(true, ...)` 0 条；多行 `true,` 首参 0 条；`HalfSheetExitHost.kt` 在基线不存在。
- 现役（HEAD `60545a9`）两域命名参数命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`（命名参数残留 0，`wc -l` = 0）。

- 现役两域位置参数命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`（位置参数残留 0，`wc -l` = 0）。

- 现役两域多行 `true,` 兜底：

```
grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`（多行首参残留 0，`wc -l` = 0）。

### 4.2 新派生写法命中行号（现役）

- 5 处 `InkHalfSheet` 调用点全部传派生 visible，逐条行号：
  - `DICT/DictRuleScreen.kt:397`（内层透传形参 `visible`；外层派生 `:154` `editExit.visible`，规则编辑 Sheet）。
  - `DICT/DictRuleScreen.kt:437`（内层透传形参 `visible`；外层派生 `:177` `helpExit.visible`，帮助页）。
  - `REP/ReplaceRuleScreen.kt:215-216`（`helpExit.visible`，帮助页）。
  - `REP/ReplaceRuleScreen.kt:526`（内层透传形参 `visible`；外层派生 `:183` `groupExit.visible`，分组管理 Sheet）。
  - `RED/ReplaceEditScreen.kt:190-191`（`helpExit.visible`，帮助页）。
- 闸位与打开点：控制器实例化 5 处（`DICT/DictRuleScreen.kt:88` / `:89`；`REP/ReplaceRuleScreen.kt:107` / `:108`；`RED/ReplaceEditScreen.kt:61`）；打开点 5 个全部先过 `canOpen` 再 `open()`（5 处 `LaunchedEffect` 程序化登记）；`HalfSheetExitHost` 实例化 5 处（`DICT/DictRuleScreen.kt:150` / `:176`；`REP/ReplaceRuleScreen.kt:180` / `:214`；`RED/ReplaceEditScreen.kt:189`）；`beginExit()` 业务调用 5 处（`DICT/DictRuleScreen.kt:156` / `:177`；`REP/ReplaceRuleScreen.kt:188` / `:217`；`RED/ReplaceEditScreen.kt:192`），另控制器内部转发 1 处。
- 全仓 `visible = true` 现役分类（非 dict / replace 域，不计本单 5 条口径）：`book/read/ink/InkContentEditState.kt:93`、`:232`（状态持有类内部 `this.visible = true` 赋值，非浮层传参，reader 域外）；`components/HandleFileSheet.kt:47`（components 红线，契约冻结）；`config/b3/B3SettingScreen.kt:151` / `:164` / `:180` / `:193`（B3 批次新增，config 域 F-1 已在 `docs/收尾-B29config域13条.md` §七登记）；`main/bookshelf/InkShelfFragment.kt:286`（注释文字，非代码）。autoTask / book·source / config 三域现役命名参数残留均为 0（沿用各自分域单验收结论）。

### 4.3 静态预检

- 命令：`python3 data_scripts/kotlin-syntax-lint.py` 传入本批 17 个相关文件（dict / replace 两域 15 个 main 源集 kt + `ui/sheet/HalfSheetExitHost.kt` + `ui/sheet/HalfSheetExitHostTest.kt`）。
- 原样输出：`KOTLIN-LINT: PASS（17 个文件）`，`exit=0`。括号配平与 if/while 条件尾逗号两项硬错零命中；未跑 `./gradlew` / `kotlinc`，编译与单测结果以云端 CI 为准。

## 五、判定不改 / 留档条目（逐条读过，非静默跳过）

- N1. `dict/DictDialog.kt:213-215` 大图查看器——**不改**。证据：`visible = viewerSrc != null` 为派生布尔（图片查看器槽位非空判定），非常量 true；Shell 壳在 `:213` 无条件保留组合，内层内容 `:218` `viewerSrc?.let { DictImageViewer(it) }` 随 null 立即清空，离场路径由派生可见性驱动，与 B29 §6.3 N1 已认可的派生 visible 口径一致。内层内容槽位即时清空、Shell 壳保留至组件收束条件 `InkHalfSheet.kt:133`，是否需要把内容槽位也纳入两段式保留，登记 §七 F-2。
- N2. 两域纯 `Dialog` 弹窗群——**不改**。逐条核对调用点：`DICT/DictRuleScreen.kt:318` 选择栏弹窗、`:351` 删除确认（`ConfirmDialog`）、`:422` 在线导入 URL（`ImportUrlDialog`）；`REP/ReplaceRuleScreen.kt:462` 选择栏弹窗、`:496` 删除确认、`:590` 分组名输入（`GroupNameDialog`）、`:613` 在线导入 URL；`RED/ReplaceEditScreen.kt:173` 退出确认。这些弹窗直接挂 `androidx.compose.ui.window.Dialog`，未包 `InkHalfSheet` / `InkBottomSheet`，无遮罩回亮 / 下降动画组件，两段式无挂载点。若日后包进模态 host，需另派单补离场闸。
- N3. `components/HandleFileSheet.kt:47`——**不改（红线）**。components 契约冻结，本单零改动。
- N4. 两域业务模型枚举 / sealed——**不改**。`DictRichBlock` / `ReplaceFilter` / `BatchGroup` / `Reason` 均为业务模型，非离场阶段机，见 §3.2 排查。
- N5. `ui/association/ImportDictRuleDialog.kt` / `ui/association/ImportReplaceRuleDialog.kt`——**不改（域外 + 壳层）**。两文件属 `ui/association/` 包，为旧 View-binding DialogFragment 壳，无 `InkHalfSheet` 调用；不在 dict / replace 目录范围内，壳层整窗销毁路径对照 B26 §7.1 外壳先例保持旧直摘口径。

## 六、自查反例（先假设「已改」结论错误，逐条找推翻证据并排除）

- 反例 1：派生 visible 看似来自控制器，但控制器若绕过基类自写阶段判断，单源口径即破。核查：`NoteDialogExitGate` 全文仅 `open()` 转发 `enterOpen()`，无自写 `visible / isComposed / canOpen`；`SheetExitController.kt:39` / `:43` / `:47` 三处均为 `get() = gate.xxx` 委托；基类派生唯一处在 `SheetExitPhaseGate.kt:144-153`。排除：无第二份派生表达式。
- 反例 2：关闭路径看似两段式，实际 onDismiss 仍直清槽位、子树瞬摘，`beginExit()` 是空挂。核查：§二 5 条 onDismiss 全部为 `{ xxxExit.beginExit() }`；槽位清空只出现在 `HalfSheetExitHost(onFinished = ...)` 回调内（延时收束后），宿主槽位状态本身不在 dismiss 出口被直接置空；VM 侧 `dismissEdit` / `dismissHelp` / `closeGroupManage` 仅由 host 收束回调经 Activity 接线调用。另核 `DICT/DictRuleListViewModel.kt:185`（基线）内部直调 `dismissEdit()`，现役 `:172-187` 已移除该直清（§二.1 VM 侧配合）。排除：无 dismiss 即摘。
- 反例 3：离场段重复打开是否被拦。核查：`canOpen` 转发基类 `phase != EXITING`；5 个打开点全部先过闸再 `open()`；离场段重复 `beginExit()` 由守卫空操作幂等处理（测试 `HalfSheetExitHostTest.kt:68-78` 覆盖离场段拒收重复打开）。排除：离场段可叠新弹层的竞态不成立。
- 反例 4：两域是否另造了本地阶段枚举 / 布尔状态机。核查：§3.2 grep 命中的枚举 / sealed 全是业务模型（富文本块 / 筛选条件 / 批量动作 / 预览错误分类），无离场阶段枚举；无第二份守卫类与迁移规则。排除：无第二套阶段机。
- 反例 5：未打开 / 已摘除时 visible 是否仍恒显。核查：基类 REMOVED 段 `visible=false`；控制器初始 `exitingState=false`、守卫初始 REMOVED；测试 `HalfSheetExitHostTest.kt:33-40` 覆盖初始摘除态不可见。排除：无恒显。
- 反例 6：5 条之外是否漏算 dict / replace 域常量 true 调用点。核查：两域三类写法（命名参数 / 位置参数 / 多行 `true,`）grep 全部 `exit=1` 零命中；`InkHalfSheet(` 调用点全量扫描共 6 处——5 处本清单 + `DictDialog.kt:213` 派生可见性 N1，无第 7 个浮层调用点。排除：无漏算、无多改。
- 反例 7：报告自述「已落码」是否与现役代码一致。核查：未采信旧报告（`docs/修复-B29-常量visible-其余三域.md`）自述，逐条 `git show 780ec90` 取基线 + 现役源码回读；5 条基线行均为 `visible = true`，现役均已派生；落码提交 `9e43760` 已在 HEAD `60545a9` 祖先链内。排除：自述与代码一致，无「报告写了、代码没变」。
- 反例 8：Compose 侧是否新引入了依赖 Application 初始化的配置读取（Paparazzi 截图环境无 Application，曾炸四个书架帧）。核查：两屏 + 宿主 + 控制器 Compose 代码零 `AppConfig` / `appCtx` / `SharedPreferences` / `Application` 引用（grep 空命中，exit=1）；VM 内 DAO / AppConfig 读写属既有历史代码，非本单两段式改动引入。排除：本单未引入新的 Application 依赖读取。
- 反例 9：保存 / 分组动作的副作用是否被延后到动画收束才执行。核查：dict 保存按钮 `DICT/DictRuleScreen.kt:405-410` 先 `onSave(...)` 入库再 `onDismiss()`；replace 分组增删改 `REP/ReplaceRuleScreen.kt:184-187` 回调直进 VM 动作函数、不关 Sheet；副作用均在决策时刻执行，与旧版时序一致，延后的只是槽位摘除。排除：无副作用时序回退。

## 七、新发现问题（每条带处置状态词）

- F-1（待裁 · 阶段账不闭环，沿用 source F-2 同型边界）：宿主级离场闸未接——两域程序化入口由 VM 先置槽位（`openEdit` / `openCreate` / `openGroupManage` / `showHelp`），Screen `LaunchedEffect` 观察槽位再过 `canOpen` 登记；离场 ~450ms 内若列表点击触发新打开，`canOpen` 拒收新会话但 VM 槽位已翻，旧槽收束回调会把新请求一并清掉。离场段触摸由浮层窗口截获兜底。处置状态：**待裁**；待另派单把 `canOpen` 闸上提到 VM / Activity 入口（对照 source 域 F-2），本单按范围红线未改。
- F-2（判废 · 派生可见性内层内容槽位即时清空）：`dict/DictDialog.kt:218` `content = { viewerSrc?.let { DictImageViewer(it) } }`，dismiss 置 `viewerSrc = null` 后内层图片内容立即摘空，仅 Shell 壳保留组合至 `InkHalfSheet.kt:133` 收束条件；与 5 条两段式保留整棵子树的口径不完全一致。处置状态：**判废**为非 B29 口径缺陷（派生可见性非常量 true，清单外），本单不改；若验收要求大图查看器也保留内容帧到下降动画结束，需另派单接控制器。
- F-3（待裁 · 覆盖边界，沿用 B29 验收 F-4）：`app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt:30` 使用 `mutableStateOf` 构造控制器；`mutableStateOf` 属 compose-runtime 普通工厂函数，JVM 下可实例化本身成立，但 8 例单测能否在单测 classpath 跑绿以云端 CI 为准。处置状态：**待裁**，CI 出包后以仪器化 / 真机走查为准再闭环。
- F-4（判废 · 既有边界，本单不解决）：控制器由 `remember` 持有，旋转 / 进程重建随 Composable 生命周期；离场中重建则阶段与离场位不跨重建存活（与 B20 / B21 / B26 / config / source 同一边界）。处置状态：**判废**为本单范围外既有边界挂账。
- F-5（已入队 · 架构债，沿用 B26 §6.4 与 config 收口 §10.1）：仓内并存 B21 `ReaderSheetExitGate`、B26 `NoteDialogExitGate`、config `SheetExitController`、source `SourceSheetExitController` 四份相近包装，另有 `ui/sheet/HalfSheetExitHost.kt` 收束宿主；建议后续合并为泛型纯 JVM 单一守卫 + 复用 Compose 薄封装，消除第三 / 四份包装复制。处置状态：**已入队**，随第四个消费方出现时一并下沉合并。
- F-6（已入队 · 跨域残留，本单不扩改）：`config/b3/B3SettingScreen.kt:151` / `:164` / `:180` / `:193` 四条 `visible = true` 同形态残留，属 config 域 B3 批次新增代码，已由 config 收口单 §七 F-1 登记跟踪；`components/HandleFileSheet.kt:47` 契约冻结红线。本单仅复扫确认 dict / replace 域无同类蔓延，未改域外任何文件。处置状态：**已入队**转对应域 / 对应单处置，dict / replace 域内无蔓延项需本单闭环。

## 八、未验证项与边界

- 编译与单测：沙箱无 gradle / JDK21 / SDK，未跑 `./gradlew` / `kotlinc`；`HalfSheetExitHostTest` 8 例是否全绿、5 处私有 Sheet `visible` 形参变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段窗口触摸截获范围，未真机走查；观感由既有 `InkHalfSheet` 组件保证，本收尾方仅静态接线口径核实。
- 动画时序：摘子树时机为代码静态推断（延时依据 = `MASK_DURATION_MS` 200ms + `RISE_DURATION_MS` 250ms），实际收束帧是否干净待 CI 出包后真机确认。
- 等效延时而非完成回调：`InkHalfSheet` 契约未暴露动画结束回调，沿用 B21 / B26 同一口径；日后组件补完成回调，可把 `HalfSheetExitHost.kt:36` 的 `delay` 替换为回调触发，守卫阶段口径不变。
- 字典规则保存失败分支：现役保存按钮无论入库成败都经 `onDismiss()` 进离场段，失败 toast 口径不变，失败后表单草稿保留到收束帧；与基线「失败也 `dismissEdit()` 直关」时序等价，真机观感待确认。
- 域外四域（autoTask 4 / config 13 / book·source 12）已由各自分域单验收 / 收口；本单未复核其实机表现。

## 九、收口结论

- **dict 2 + replace 3 共 5 条已处置完毕（静态层；编译 / 单测等 CI）。**
- 每条证据：
  1. dict 规则编辑 Sheet（`DICT/DictRuleScreen.kt:377` 基线）：现役 `:88` 控制器、`:91` 过闸 `open()`、`:150` host 收束清 `editTarget`、`:154` 派生 `editExit.visible`、`:156` `beginExit()`；内层 `:383` 形参 / `:397` 透传；VM `save` 不再直清槽位（`DictRuleListViewModel.kt:172-187`）。**已改**。
  2. dict 帮助页（`DICT/DictRuleScreen.kt:416` 基线）：现役 `:89` 控制器、`:94` 过闸、`:176` host、`:177` 派生 `helpExit.visible` + `beginExit()`；内层 `:436` 形参 / `:437` 透传。**已改**。
  3. replace 帮助页（`REP/ReplaceRuleScreen.kt:198` 基线）：现役 `:108` 控制器、`:113` 过闸、`:214` host、`:216` 派生 `helpExit.visible`、`:217` `beginExit()`。**已改**。
  4. replace 分组管理 Sheet（`REP/ReplaceRuleScreen.kt:500` 基线）：现役 `:107` 控制器、`:110` 过闸、`:180` host 收束清 `groupManageOpen`、`:183` 派生 `groupExit.visible`、`:188` `beginExit()`；内层 `:518` 形参 / `:526` 透传。**已改**。
  5. replace 编辑页帮助（`RED/ReplaceEditScreen.kt:181` 基线）：现役 `:61` 控制器、`:63` 过闸、`:189` host、`:191` 派生 `helpExit.visible`、`:192` `beginExit()`。**已改**。
- 依据：5 条逐条核实为「已改」、0 条「未改」；现役 `visible` 全部来自 `SheetExitController` 派生（转发 B26 `NoteDialogExitGate`，继承 B30 `SheetExitPhaseGate` 基类单源派生），无一处恒传 true；关闭路径全部走 `beginExit()` → `HalfSheetExitHost` 450ms 等效延时收束 → `finishExit()` + 回调清槽位摘子树，无 dismiss 即摘；阶段机复用既有链路，两域无第二份枚举、无第二份守卫类、无第二份派生表达式（§3.2 grep 回证）；5 个打开点全部过 `canOpen` 闸；5 份独立控制器不串阶段；收束延时只引组件时长常数，无魔法数；静态预检 17 个相关文件 `KOTLIN-LINT: PASS`；8 例纯 JVM 断言齐全，测试名无禁字符。
- 附条件：F-1（宿主级闸未上提）、F-2（大图查看器内层内容槽位口径）为清单外登记项，不在 B29 5 条缺陷口径内；在 F-1 收口前，「两域入口级双闸闭环」的绝对结论不成立，本结论仅覆盖 B26 清单 dict 域 2 条 + replace 域 3 条。
