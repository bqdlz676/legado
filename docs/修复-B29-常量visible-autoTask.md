锚点卡：docs/锚点/Q464-B29autoTask.md

# 修复-B29：autoTask 域 4 条常量 visible 收口核实与两段式接线复核（Q464）

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74（本收尾方现取 `python3 data_scripts/check-onboarding.py --emit` 同批指纹，与磁盘现行文件一致）。
- 逐字引用 1：「规则：清单外不许新写代码；新增问题必须先给编号进表。」（docs/机制-总控必读.md:32）
- 逐字引用 2：「**grep 回证**：每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。没贴 grep 的改动视为未改。」（docs/机制-子代理必读.md:18）
- 逐字引用 3：「**不采信写码方的自评**：它说"已验证"不算证据；它给的计数要你自己重算。」（docs/机制-验收代理必读.md:8）
- 逐字引用 4：「**溯源**：报告写清这条判据对应哪份原始事实的哪一行；锚点可回源。」（docs/机制-文档子代理必读.md:16）
- 适用条款：只核 `app/src/main/java/io/legado/app/ui/autoTask/`（含其 ViewModel / Screen）；未碰 config / book·source / dict / replace 四域；复用既有 B21/B26/B29/B30 阶段机链路，未造第二份枚举、第二份守卫、第二份派生口径；改动后先 grep 回证再下结论；每条判定带 文件:行 证据；未验证项如实披露，不把「报告里写了」当成已实现；禁 git 写、禁跑 gradlew / kotlinc / SDK，Kotlin 仅跑 `python3 data_scripts/kotlin-syntax-lint.py` 静态预检。

## 一、任务边界与判定口径

- 收口对象：`docs/问题清单.md:73` B29 行中 autoTask 域 4 条，清单出自 `docs/修复-B26-常量visible接线.md` §6.3 第 264–267 条（命名参数 `visible = true`，条件渲染宿主 + dismiss 即摘子树，`InkHalfSheet` 内 `visible=false` 离场分支被跳过，200ms 遮罩回亮 + 250ms 下降动画不播）。
- 范围红线：只许动 `app/src/main/java/io/legado/app/ui/autoTask/` 内与这 4 处浮层相关文件；config / book·source / dict / replace 四域零触碰；components 红线 `HandleFileSheet.kt` 不动；未另造阶段机；未留旧格式兼容层。
- 判定口径（先假设「已改」结论错误，逐条回现役代码核实）：
  1. 「已改」判据：`visible` 不再恒传 true，而来自阶段机派生（控制器 `.visible` 转发守卫转发基类）；关闭路径真的走 `beginExit()` → `HalfSheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树，不是 dismiss 即摘；打开点先过 `canOpen` 闸再 `open()`。
  2. 「未改」判据：现役代码仍恒传 true，或关闭路径仍直接摘子树。
  3. 基线对照：以 B29 三域收口提交 `9e43760^`（即 `780ec90`，落码前快照）为基线，逐条 `git show` 取基线原文；现役行号以 HEAD `fcb0116` 工作树为准。
- 现役提交链：`9e43760`「修B29:autoTask/dict/replace三域浮层两段式收口(HalfSheetExitHost复用config控制器,8断言)」→ `4c3f7e6`（B1 基座接线，diff 仅给 autoTask 两屏补 `ComponentVisualState` 态参，未触离场接线，回证见 §四 4.4）。本收尾方未做任何 `git add / commit / push`；`9e43760` 已在 HEAD `fcb0116` 祖先链内（`git merge-base --is-ancestor 9e43760 HEAD` 回证）。

## 二、4 条逐条核实清单（一条不漏）

> 缩写：`AT=app/src/main/java/io/legado/app/ui/autoTask`。基线原文取自 `git show 780ec90:<文件>`；现役行号取自 HEAD `fcb0116` 工作树。4 条判定全部为 **已改**。

### 1. AutoTaskEditScreen.kt 帮助半屏（B26 清单第 3 条）

- 基线：`AT/AutoTaskEditScreen.kt:151` 原文 `InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {`（`git show 780ec90:.../AutoTaskEditScreen.kt:151` 回源）。基线 dismiss 直调 `onDismissHelp` 清 VM 槽位摘子树。
- 现役现状：**已改**。
  - 控制器：`AT/AutoTaskEditScreen.kt:64` `val helpExit = rememberSheetExitController()`。
  - 打开闸：`:65-67` `LaunchedEffect(state.helpText) { if (state.helpText != null && helpExit.canOpen) helpExit.open() }`，先过 `canOpen` 再 `open()`。
  - 收束宿主：`:159` `HalfSheetExitHost(helpExit, onFinished = { onDismissHelp() })`，收束后才清 VM 槽位摘子树。
  - 派生可见性：`:161` `visible = helpExit.visible`。
  - 关闭路径：`:162` `onDismiss = { helpExit.beginExit() }`；子树在离场段保留组合，`HalfSheetExitHost` 按遮罩 200ms + 下降 250ms 等效延时后 `finishExit()` 再回调 `onDismissHelp()`。

### 2. AutoTaskScreen.kt 帮助半屏（B26 清单第 4 条）

- 基线：`AT/AutoTaskScreen.kt:168` 原文 `InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {`。
- 现役现状：**已改**。
  - 控制器：`AT/AutoTaskScreen.kt:96` `val helpExit = rememberSheetExitController()`。
  - 打开闸：`:98-100` `LaunchedEffect(state.helpText) { if (state.helpText != null && helpExit.canOpen) helpExit.open() }`。
  - 收束宿主：`:184` `HalfSheetExitHost(helpExit, onFinished = { onDismissHelp() })`。
  - 派生可见性：`:186` `visible = helpExit.visible`。
  - 关闭路径：`:187` `onDismiss = { helpExit.beginExit() }`。

### 3. AutoTaskScreen.kt 运行日志半屏（B26 清单第 5 条，私有 `LogSheet`）

- 基线：`AT/AutoTaskScreen.kt:403` 原文 `InkHalfSheet(visible = true, onDismiss = onDismiss, title = task.name, content = {`（私有 `LogSheet` 内，宿主 `state.logTarget?.let`）。
- 现役现状：**已改**。
  - 控制器：`AT/AutoTaskScreen.kt:97` `val logExit = rememberSheetExitController()`。
  - 打开闸：`:101-103` `LaunchedEffect(state.logTarget) { if (state.logTarget != null && logExit.canOpen) logExit.open() }`。
  - 收束宿主：`:159` `HalfSheetExitHost(logExit, onFinished = onDismissLog)`，收束后回调 VM `dismissLog()` 清槽。
  - 派生可见性：`:162` `visible = logExit.visible`；关闭路径：`:164` `onDismiss = { logExit.beginExit() }`。
  - 私有 `LogSheet`：`:428` 新增 `visible: Boolean` 形参；`:433` `InkHalfSheet(visible = visible, onDismiss = onDismiss, title = task.name, content = {` 透传派生可见性。
  - 「清除日志」按钮：`:445` `InkButton("清除日志", { onClear(); onDismiss() }, ...)`；清库副作用 `onClear()`（VM `clearLog`）在决策时刻执行，随后 `onDismiss()` 进离场段。

### 4. ImportAutoTaskDialog.kt JSON 预览半屏（B26 清单第 6 条）

- 基线：`AT/ImportAutoTaskDialog.kt:98` 原文 `InkHalfSheet(visible = true, onDismiss = { jsonTarget = null }, title = task.name, content = {`（DialogFragment 壳内 local `jsonTarget?.let`）。
- 现役现状：**已改**。
  - 控制器：`AT/ImportAutoTaskDialog.kt:86` `val jsonExit = rememberSheetExitController()`。
  - 打开闸：`:87-89` `LaunchedEffect(jsonTarget) { if (jsonTarget != null && jsonExit.canOpen) jsonExit.open() }`。
  - 收束宿主：`:107` `HalfSheetExitHost(jsonExit, onFinished = { jsonTarget = null })`，收束后才清 local 槽位摘子树。
  - 派生可见性：`:109` `visible = jsonExit.visible`。
  - 关闭路径：`:110` `onDismiss = { jsonExit.beginExit() }`。
  - 壳层整体关闭（`:101-104` `onDismiss` / `:95-99` 导入成功回调的 `dismissAllowingStateLoss()`）为 DialogFragment 整窗销毁路径，非 JSON 预览半屏自身离场，保持旧直摘口径（§五 N2）。

### 4 条汇总

- 已改：4 条；未改：0 条；本收尾方本轮未补改任何 main 源集代码（现役代码已在 `9e43760` 落码，`4c3f7e6` 未触离场接线）。
- 4 条关闭路径全部为 `onDismiss = { xxxExit.beginExit() }` → `HalfSheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树；无一处 dismiss 即摘。
- 控制器实例化 4 份（编辑帮助 1 + 列表帮助 1 + 列表日志 1 + 导入 JSON 预览 1），每槽位各持独立控制器，未上提 VM、未跨 Sheet 共享阶段态。

## 三、阶段机复用与单源派生核查（无第二份口径）

### 3.1 复用链（只引既有，未造第二套）

- 阶段枚举三态 `OPEN / EXITING / REMOVED` 定义唯一处：`app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:12` `enum class ReaderSheetExitPhase {`。
- 派生口径唯一事实源（基类）：`SheetExitPhaseGate.kt:144-145` `visible = phase == ReaderSheetExitPhase.OPEN`；`:148-149` `isComposed = phase != ReaderSheetExitPhase.REMOVED`；`:152-153` `canOpen = phase != ReaderSheetExitPhase.EXITING`。
- B26 守卫继承该基类：`app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:19` `class NoteDialogExitGate : SheetExitPhaseGate()`，自身只补 `open() = enterOpen()`，未自写 `visible / isComposed / canOpen` 判断。
- B29 config 控制器：`app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt:39` `visible get() = gate.visible`；`:43` `isComposed get() = gate.isComposed`；`:47` `canOpen get() = gate.canOpen`，全部委托基类派生；`:55` `open()` / `:58` `beginExit()` / `:64` `finishExit()` 转发守卫；`:74` `rememberSheetExitController()` 工厂。
- autoTask 域收束宿主：`app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt:29-39`，只做 `LaunchedEffect(controller.isExiting)` 延时收束与回调，不新增阶段枚举、不复制迁移规则；控制器类型直接复用 config 域 `SheetExitController`，域内未另立包装控制器（与 source 域 `SourceSheetExitController` 同属全委托转发形态，此处无域内特有负载需缓存，故不重复造薄封装）。
- 控制器内无自写阶段比较、无第二份迁移规则；`isExiting`（`SheetExitController.kt:51`）只是 Compose 离场位镜像，不参与派生判断。

### 3.2 第二份阶段机排查（grep 回证）

- 命令：`grep -rnE "enum class|sealed (class|interface)|ExitGate|ExitPhase|Stage" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt`。
- 唯一命中：`AT/AutoTaskEditScreen.kt:36` `enum class AutoTaskField { SCRIPT, HEADER, JSLIB, LOGIN_URL, LOGIN_UI, LOGIN_CHECK_JS }`（全屏编辑目标字段选择器，业务模型枚举，非离场阶段机）。
- autoTask 域内无第二份离场守卫类、无第二份阶段枚举、无第二份 OPEN→EXITING→REMOVED 迁移规则；除 `NoteDialogExitGate` 链路外无其他离场闸 import。

### 3.3 时长同源（延时引组件常数，无魔法数）

- 遮罩时长：`app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt:53` `MASK_DURATION_MS: Int = 200`；下降时长：`:50` `RISE_DURATION_MS: Int = 250`。
- 收束延时：`app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt:39` `HalfSheetExitDurations.exitMs` = `(MASK_DURATION_MS + RISE_DURATION_MS).toLong()`，两段常数相加（200 + 250 = 450ms），与 B26 `HighlightNoteDialog.kt` 同一口径；autoTask / sheet 生产代码内无 200 / 250 / 450 硬编码延时字面量。
- 组件离场分支前提：`app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt:121-130` `visible=false` 分支依次遮罩回亮 200ms、Sheet 下降 250ms；`:133` `if (progress.value == 0f && !visible) return` 收束条件；派生 `visible=false` 确能播组件内离场动画。

## 四、grep 回证前后对照（命令 + 计数 + 行号）

### 4.1 旧常量传法命中数：前 vs 后

- 基线（`780ec90`，B29 落码前）autoTask 域：命名参数 `visible = true` 4 条（逐条基线原文见 §二）；位置参数 `Sheet(true, ...)` 0 条；多行 `true,` 首参 0 条。
- 现役（HEAD `fcb0116`）命名参数命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt
```

原样输出：空；`exit=1`（命名参数残留 0）。

- 现役位置参数命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt
```

原样输出：空；`exit=1`（位置参数残留 0）。

- 现役多行 `true,` 兜底命令：

```
grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt
```

原样输出：空；`exit=1`（多行首参残留 0）。

### 4.2 新派生写法命中行号（现役）

- 4 处 `InkHalfSheet` 调用点全部传派生可见性，逐条行号：
  - `AT/AutoTaskEditScreen.kt:161`（`visible = helpExit.visible`，编辑帮助）。
  - `AT/AutoTaskScreen.kt:186`（`visible = helpExit.visible`，列表帮助）。
  - `AT/AutoTaskScreen.kt:162`（外层 `visible = logExit.visible`；私有 `LogSheet` 内层 `:433` 透传形参 `visible`）。
  - `AT/ImportAutoTaskDialog.kt:109`（`visible = jsonExit.visible`，JSON 预览）。
- 闸位与打开点：控制器实例化 4 处（`AutoTaskEditScreen.kt:64`、`AutoTaskScreen.kt:96` / `:97`、`ImportAutoTaskDialog.kt:86`）；打开点 4 个全部先过 `canOpen` 再 `open()`（`AutoTaskEditScreen.kt:66`、`AutoTaskScreen.kt:99` / `:102`、`ImportAutoTaskDialog.kt:88`）；`HalfSheetExitHost` 实例化 4 处（`AutoTaskEditScreen.kt:159`、`AutoTaskScreen.kt:159` / `:184`、`ImportAutoTaskDialog.kt:107`）；`beginExit()` 业务调用 5 处（4 个 dismiss 入口 + 日志「清除日志」动作行 `AutoTaskScreen.kt:445` 经 `onDismiss()` 转发），另控制器内部转发 1 处。
- VM 侧槽位责任：`AT/AutoTaskListViewModel.kt:108` `dismissLog()` 置 `logTarget = null`，仅由宿主收束回调 `onDismissLog` 引用（`AutoTaskActivity.kt:97`）；`AT/AutoTaskListViewModel.kt:112-114` `clearLog(id)` 只执行清库副作用 `AutoTask.clearRunLog(id)`，不再内部直清 `logTarget`（基线 `780ec90` 该方法内含 `_uiState.update { it.copy(logTarget = null) }`，已移除）。

### 4.3 Compose 组合期直读配置排查（Paparazzi 风险口径）

- 命令：`grep -rn "AppConfig\|appCtx" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt`。
- 命中仅 1 处 VM 层：`AT/ImportAutoTaskViewModel.kt:29` `import splitties.init.appCtx`；`:138` `text.isUri() -> importSourceAwait(text.toUri().readText(appCtx))`，为 VM 运行期方法内读取 URI 文本，非 Compose 组合期、非 `AppConfig` 偏好 getter（`app/src/main/java/io/legado/app/help/config/AppConfig.kt:33-47` 各 getter 才是 `appCtx.getPrefBoolean(...)` 类初始化即读 appCtx 的风险面）。4 个 Compose 屏文件零 `AppConfig` / `appCtx` 引用；配置项均由 VM 读入 `uiState` 后透传（`AutoTaskModels.kt:97` `logTarget`、`:100` `helpText`）。

### 4.4 基线后改动面回证

- `git log --oneline 9e43760..HEAD -- app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/sheet/` 仅命中 `4c3f7e6`；`git show 4c3f7e6 -- app/src/main/java/io/legado/app/ui/autoTask/` diff 显示该提交只给 `AutoTaskScreen.kt:257` / `:268` 与 `ImportAutoTaskDialog.kt:171` 三处空态 / 错误态补 `state = ComponentVisualState.*` 态参及对应 import，未改控制器、闸、宿主、派生可见性与 dismiss 路径。

### 4.5 静态预检

- 命令：`python3 data_scripts/kotlin-syntax-lint.py` 传入本批 5 个相关文件（3 个屏 + 1 个 VM + 1 个收束宿主）。
- 原样输出：`KOTLIN-LINT: PASS（5 个文件）`，`exit=0`。括号配平与 if/while 条件尾逗号两项硬错零命中；未跑 `./gradlew` / `kotlinc`，编译与单测结果以云端 CI 为准。

## 五、判定不改 / 留档条目（逐条读过，非静默跳过）

- N1. 域内纯 `Dialog` 弹窗群——**不改**。逐条核对：`AutoTaskEditScreen.kt:146` 退出确认；`AutoTaskScreen.kt:378` 选择栏确认壳、`:406` 删除确认、`:454` 批量定时、`:470` 在线导入 URL。这些弹窗直接挂 `androidx.compose.ui.window.Dialog`，未包 `InkHalfSheet` / `InkBottomSheet`，无遮罩回亮 / 下降动画组件可挂，两段式无挂载点；基线即如此，本单未改动其交互。若日后包进模态 host，需另派单补离场闸。
- N2. `ImportAutoTaskDialog.kt` 壳层整窗销毁路径——**不改**。`:95-99` 导入成功回调与 `:101-104` 整体 `onDismiss` 的 `dismissAllowingStateLoss()`（含 `finishOnDismiss` 时 `activity?.finish()`）是 DialogFragment 整窗销毁，非 JSON 预览半屏自身离场；对照 B26 §7.1 外壳防御 / 导航关闭先例，整窗销毁无可播的组件内离场动画，保持旧直摘口径。
- N3. `AT/AutoTaskEditScreen.kt:36` `AutoTaskField` 业务枚举——**不改**。编辑目标字段选择器，非离场阶段机，见 §3.2 排查。
- N4. components 红线 `app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt:47`——**不改（红线）**。components 契约冻结，本单零改动。

## 六、自查反例（先假设「已改」结论错误，逐条找推翻证据并排除）

- 反例 1：派生 visible 看似来自控制器，但控制器若绕过基类自写阶段判断，单源口径即破。核查：`SheetExitController.kt:39` / `:43` / `:47` 三处均为 `get() = gate.xxx` 委托；`NoteDialogExitGate` 全文仅 `open()` 转发 `enterOpen()`，无自写派生判断；基类派生唯一处在 `SheetExitPhaseGate.kt:144-153`。排除：无第二份派生表达式。
- 反例 2：关闭路径看似两段式，实际 onDismiss 仍直清槽位、子树瞬摘，`beginExit()` 是空挂。核查：§二 4 条 onDismiss 全部为 `{ xxxExit.beginExit() }`；槽位清空只出现在 `HalfSheetExitHost(onFinished = ...)` 回调内；`AutoTaskActivity.kt:95-97` 的 `onDismissHelp` / `onDismissLog` 接线仅被宿主收束回调引用，dismiss 出口不直接置空槽位。排除：无 dismiss 即摘。
- 反例 3：离场段重复打开是否被拦。核查：`canOpen` 转发基类 `phase != EXITING`；4 个打开点全部先过闸再 `open()`；离场段重复 `beginExit()` 由守卫空操作幂等处理（测试 `HalfSheetExitHostTest.kt:68` 覆盖离场段拒收重复打开）。排除：离场段可叠新弹层的竞态不成立。
- 反例 4：autoTask 域是否另造了本地阶段枚举 / 布尔状态机。核查：§3.2 grep 命中的唯一枚举 `AutoTaskField` 是字段选择器，无离场阶段枚举；无第二份守卫类与迁移规则。排除：无第二套阶段机。
- 反例 5：未打开 / 已摘除时 visible 是否仍恒显。核查：基类 REMOVED 段 `visible=false`；控制器初始守卫即 REMOVED；测试 `HalfSheetExitHostTest.kt:33` 覆盖初始摘除态不可见、子树不在、可接受新打开。排除：无恒显。
- 反例 6：4 条之外是否漏算 autoTask 域常量 true 调用点。核查：autoTask 域三类写法（命名参数 / 位置参数 / 多行 `true,`）grep 全部 `exit=1` 零命中；`InkHalfSheet(` 全量扫描共 4 处，与清单 4 条一一对应；`InkBottomSheet(` 零命中。排除：无漏算、无多改。
- 反例 7：报告自述「已落码」是否与现役代码一致。核查：未采信旧报告自述，逐条 `git show 780ec90` 取基线 + 现役源码回读；4 条基线行均为 `visible = true`，现役均已派生；落码提交 `9e43760` 已在 HEAD `fcb0116` 祖先链内；其后 `4c3f7e6` diff 未触离场接线（§4.4）。排除：自述与代码一致，无「报告写了、代码没变」。
- 反例 8：Compose 侧是否新引入依赖 Application 初始化的配置读取（Paparazzi 截图环境无 Application，曾炸四个书架帧）。核查：4 个 Compose 屏文件零 `AppConfig` / `appCtx`；唯一 `appCtx` 在 `ImportAutoTaskViewModel.kt:138` VM 运行期 URI 读取方法内，不参与组合期类初始化。排除：本域未引入新的 Application 依赖读取。

## 七、新发现问题（每条带处置状态词）

- F-1（已入队 · 架构耦合，沿用 B29 验收 F1，不在本单扩改）：`app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt:6` 作为泛化半屏宿主包，反向 import `ui/config/settings.SheetExitController`；通用宿主层绑定特性包类型，是日后拆模块 / 抽公共层时需下沉的耦合点。处置状态：**已入队**观察；建议随第四个消费方出现，与控制器 / 守卫一并下沉 common 包并泛型合并（沿用 F-6 架构债议题）。
- F-2（已入队 · 守卫不对称，生产路径不可达，沿用 B29 验收 F2）：`SheetExitController.beginExit()`（`SheetExitController.kt:58-61`）无条件置 `exitingState.value = true`，而包装的 `NoteDialogExitGate.beginExit()` 在非 OPEN 段为空操作、不迁移阶段；若 REMOVED 段误调 `beginExit`，守卫阶段不变但 `exitingState` 仍被置 true，宿主会延时后走 `finishExit()`（守卫空操作）并回调 `onFinished` 清槽。生产 dismiss 仅在在场 / 离场段可达，该分支不可触发。处置状态：**已入队**；建议后续按守卫返回值决定是否置位 `exitingState`，或补一条控制器侧空操作断言。
- F-3（判废 · 测试 oracle 字面量，沿用 B29 验收 F3）：`app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt:128` `assertEquals(450L, HalfSheetExitDurations.exitMs)` 硬编码期望字面量；同例 `:126` 已做常量派生断言，`:128` 属锁定期望值的 oracle 字面量，组件两段时长常量调整时该断言会同步失败（期望行为）；生产宿主代码无魔法数。处置状态：**判废**为非缺陷，不改。
- F-4（待裁 · 覆盖边界，沿用 B29 验收 F4）：宿主 `LaunchedEffect` 延时收束 → `finishExit` → `onFinished` 清槽位这条 Compose 链路无纯 JVM 单测；本报告对该链路的确认来自静态走查。处置状态：**待裁**，CI 出包后以仪器化 / 真机走查为准再闭环。
- F-5（判废 · 既有边界，本单不解决，沿用 B29 验收 F5）：离场约 450ms 内发生旋转 / 进程重建，控制器阶段与离场位不跨重建存活；VM 槽位仍在，重组时 `LaunchedEffect` 会再次 `open()` 重开 Sheet。与 B20 / B21 / B26 / B29 同一边界，非本单引入。处置状态：**判废**为本单范围外既有边界挂账。
- F-6（已闭环 · 第五处同类缺陷排查为空）：穷举 autoTask 目录三类旧写法 + `InkHalfSheet(` / `InkBottomSheet(` 全调用点，除清单 4 条外无第 5 处同形态浮层；纯 `Dialog` 群无组件动画挂载点（§五 N1）。处置状态：**已闭环**，无需补改。
- F-7（已入队 · 跨域残留，不在本域处置）：全仓现役 `visible = true` 命中分类——`components/HandleFileSheet.kt:47`（components 红线）；`config/b3/B3SettingScreen.kt:151` / `:164` / `:180` / `:193`（config 域，随 Q459 config 单口径处置）；`book/read/ink/InkContentEditState.kt:93` / `:232`（reader 状态类内部 `this.visible = true` 赋值，非浮层传参）；`main/bookshelf/InkShelfFragment.kt:286`（注释文字）。autoTask 域 0 命中。处置状态：**已入队**转对应域 / 对应单，本单不扩改。

## 八、未验证项与边界

- 编译与单测：沙箱无 gradle / JDK21 / SDK，未跑 `./gradlew` / `kotlinc`；`HalfSheetExitHostTest` 8 例纯 JVM 断言是否全绿、既有 import 与私有 Sheet `visible` 形参变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段窗口触摸截获范围，未真机走查；观感由既有 `InkHalfSheet` 组件保证，本收尾方仅静态接线口径核实。
- 动画时序：摘子树时机为代码静态推断（延时依据 = `MASK_DURATION_MS` 200ms + `RISE_DURATION_MS` 250ms），实际收束帧是否干净待 CI 出包后真机确认。
- 等效延时而非完成回调：`InkHalfSheet` 契约未暴露动画结束回调，沿用 B21 / B26 / B29 同一口径；日后组件补完成回调，可把 `HalfSheetExitHost.kt` 内 `delay` 替换为回调触发，守卫阶段口径不变。
- 域外四域（book·source 12 / config 13 / dict 2 / replace 3）已由各自分域单核实 / 在飞；本单未复核其实机表现。
- 工作树无本单范围外 autoTask / sheet 路径未提交改动（`git status --short` 过滤 `autoTask` / `sheet` 为空）。

## 九、二阶三阶影响与认知成本

- 直接效果：4 处帮助 / 日志 / 预览半屏关闭时先播组件内离场动画，收束后再摘子树；清库副作用（`clearLog`）时机不变，仍在决策时刻执行。
- 二阶连带：离场 ~450ms 内弹层子树仍在组合并继续截获宿主触摸，宿主列表滚动 / 点击不与 Sheet 手势抢通道（与 B21 验收 §7.2 结论一致）；同屏两槽位（帮助 + 日志）各持独立控制器，互不串阶段。
- 三阶维护：autoTask 复用 config 控制器类型 + `ui/sheet` 泛化宿主，未在域内复制守卫 / 枚举；新增浮层槽位只需 `rememberSheetExitController()` + `HalfSheetExitHost` 接线，认知成本低；代价是 F-1 记录的跨包耦合，待公共层下沉时一并收口。
- 认知成本评估：未新增概念 / 新抽象；接入方需理解的唯一口径是「控制器 `.visible` 派生、dismiss 走 `beginExit()`、摘子树归 host 收束回调」，与 source / config / dict / replace 各域同一套措辞。

## 十、收口结论

- **autoTask 域 4 条已处置完毕（静态层；编译 / 单测等 CI）。已改 4 / 未改 0。**
- 依据：
  1. 4 条逐条核实为「已改」、0 条「未改」：现役 `visible` 全部来自控制器派生（转发 B26 `NoteDialogExitGate`，继承 B30 `SheetExitPhaseGate` 基类单源派生），无一处恒传 true；关闭路径全部走 `beginExit()` → `HalfSheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树，无 dismiss 即摘。
  2. 阶段机复用既有链路，autoTask 域无第二份枚举、无第二份守卫类、无第二份派生表达式（§3.2 grep 回证）。
  3. 4 个打开点全部过 `canOpen` 闸；4 份独立控制器不串阶段；收束延时只引组件时长常数（200 + 250 = 450ms），无魔法数。
  4. 静态预检 5 个相关文件 `KOTLIN-LINT: PASS`；8 例纯 JVM 断言齐全（`HalfSheetExitHostTest.kt:33` / `:43` / `:55` / `:68` / `:81` / `:95` / `:109` / `:123`）。
- 附条件：F-1（跨包耦合）、F-2（控制器侧不对称）、F-4（Compose 链路无纯 JVM 断言）、F-5（重建不跨存活）为清单外登记项，不在 B29 autoTask 4 条缺陷口径内；在 F-1 / F-4 收口前，「autoTask 域跨包零耦合」「宿主收束链路纯 JVM 覆盖闭环」的绝对结论不成立，本结论仅覆盖 B26 清单 autoTask 域 4 条。
