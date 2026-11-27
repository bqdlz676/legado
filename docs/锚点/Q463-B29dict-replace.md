# 锚点卡 · Q463 B29 dict 2 + replace 3 条收口核实

> 用途：B29 dict 域 2 条 + replace 域 3 条常量 visible 收口收尾核实依据；被核报告 `docs/修复-B29-常量visible-dict-replace.md`。
> 缺陷出自 `docs/问题清单.md:73` B29 行；清单来源 `docs/修复-B26-常量visible接线.md` §6.3 第 32–36 条；范式来源 `docs/修复-B21-半屏离场动画.md` + `docs/修复-B26-常量visible接线.md`；B30 单源收敛基线 `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:12-153`。
> 逐字引文带 文件:行，禁转述；锚点禁裸 `:NNN`。

## 一、逐字引文（带 文件:行）

- `docs/问题清单.md:73`：「**域外 34 条常量 visible 同形态**：autoTask 4 / book·source 12 / config 13 / dict 2 / replace 3，均为条件渲染宿主 + dismiss 即摘子树，离场动画被跳过」。
- `docs/修复-B26-常量visible接线.md:300-303`：「dict 域（2 条）：32. `app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt:377`——`InkHalfSheet` 规则编辑 Sheet（`state.editTarget?.let` 宿主）。33. `app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt:416`——`InkHalfSheet` 帮助页（`state.helpText?.let` 宿主）。」
- `docs/修复-B26-常量visible接线.md:305-309`：「replace 域（3 条）：34. `app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt:198`——`InkHalfSheet` 帮助页（`state.helpText?.let` 宿主）。35. `app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt:500`——`InkHalfSheet` 分组管理 Sheet（`state.groupManageOpen` 宿主，`GroupManageSheet`）。36. `app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt:181`——`InkHalfSheet` 帮助页（`state.helpText?.let` 宿主）。」
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:144-145`：`val visible: Boolean`；`get() = phase == ReaderSheetExitPhase.OPEN`（可见性派生唯一事实源）。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:148-149`：`val isComposed: Boolean`；`get() = phase != ReaderSheetExitPhase.REMOVED`（子树保留口径唯一处）。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:152-153`：`val canOpen: Boolean`；`get() = phase != ReaderSheetExitPhase.EXITING`（离场拒收口径唯一处）。
- `app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:19`：`class NoteDialogExitGate : SheetExitPhaseGate()`（B26 守卫继承 B30 基类，未自写派生判断）。
- `app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt:39-40`：`val visible: Boolean` / `get() = gate.visible`；`:43-44` `isComposed`；`:47-48` `canOpen`（config 薄封装全部委托基类派生；dict / replace 两域复用同一控制器类型）。
- `app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt:29`：`fun HalfSheetExitHost(controller: SheetExitController, onFinished: () -> Unit, content: @Composable () -> Unit)`（两域半屏共用收束宿主）；`:36` `delay(HalfSheetExitDurations.exitMs)`；`:37` `controller.finishExit()`；`:38` `onFinished()`；`:52` `exitMs = (MASK_DURATION_MS + RISE_DURATION_MS).toLong()`。
- `app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt:53`：`const val MASK_DURATION_MS: Int = 200`；`:50` `const val RISE_DURATION_MS: Int = 250`（半屏离场两段时长同源常数，合计 450ms）。
- `app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt:133`：`if (progress.value == 0f && !visible) return`（组件内部保留帧 / 收束条件，离场段子树在此之前保留组合）。

## 二、5 条核对锚点（基线 `780ec90`（`9e43760^`）→ 现役 HEAD `60545a9`）

> 缩写：`DICT=app/src/main/java/io/legado/app/ui/dict/rule`；`REP=app/src/main/java/io/legado/app/ui/replace`；`RED=REP/edit`。

1. dict · `DICT/DictRuleScreen.kt` 规则编辑 Sheet（B26 第 32 条，基线 `:377`）：现役 `:88` `editExit` 控制器、`:91` 闸 + `open()`、`:150` `HalfSheetExitHost`（`onFinished = onDismissEdit`）、`:154` `visible = editExit.visible`、`:156` `beginExit()`；内层 `EditSheet` 形参 `:383`、透传 `:397`。
2. dict · `DICT/DictRuleScreen.kt` 帮助页（B26 第 33 条，基线 `:416`）：现役 `:89` `helpExit` 控制器、`:94` 闸、`:176` host（`onFinished = onDismissHelp`）、`:177` `visible = helpExit.visible` + `beginExit()`；内层 `HelpSheet` 形参 `:436`、透传 `:437`。
3. replace · `REP/ReplaceRuleScreen.kt` 帮助页（B26 第 34 条，基线 `:198`）：现役 `:108` `helpExit` 控制器、`:113` 闸、`:214` host（`onFinished = { onDismissHelp() }`）、`:216` `visible = helpExit.visible`、`:217` `beginExit()`；`InkHalfSheet` 调用 `:215`。
4. replace · `REP/ReplaceRuleScreen.kt` 分组管理 Sheet（B26 第 35 条，基线 `:500`）：现役 `:107` `groupExit` 控制器、`:110` 闸、`:180` host（`onFinished = onCloseGroupManage`）、`:183` `visible = groupExit.visible`、`:188` `beginExit()`；内层 `GroupManageSheet` 形参 `:518`、透传 `:526`。
5. replace · `RED/ReplaceEditScreen.kt` 帮助页（B26 第 36 条，基线 `:181`）：现役 `:61` `helpExit` 控制器、`:63` 闸、`:189` host（`onFinished = { onDismissHelp() }`）、`:191` `visible = helpExit.visible`、`:192` `beginExit()`；`InkHalfSheet` 调用 `:190`。

## 三、口径锁（不许改）

- 阶段枚举三态 `OPEN / EXITING / REMOVED` 定义唯一处 `SheetExitPhaseGate.kt:12`；禁造第二份阶段机、第二份守卫类、第二份迁移规则。
- 派生 `visible / isComposed / canOpen` 只许来自 B30 基类一处；dict / replace 两域复用 config 域 `SheetExitController` 薄封装（委托 `NoteDialogExitGate`），不内联阶段比较、不另写派生判断。
- 两段式关闭：dismiss 先走 `beginExit()` 进离场段（派生 visible=false、子树保留），`HalfSheetExitHost` 按遮罩 200ms + 下降 250ms 等效延时后 `finishExit()` 并回调宿主清槽位摘子树；禁 dismiss 即摘子树。
- 离场段拒收新打开：打开点先过 `canOpen` 闸再 `open()`；在场段判重仍归宿主既有槽位状态（VM `editTarget` / `helpText` / `groupManageOpen`），两道闸串联不替代。
- 未打开 / 已摘除段 visible 必须为 false；离场段子树保留至收束帧，组件内部条件见 `InkHalfSheet.kt:133`。
- Compose 侧禁引入依赖 Application 初始化的配置读取（Paparazzi 截图环境无 Application）；本批两屏 / 宿主代码零 `AppConfig` / `appCtx` / `SharedPreferences` 引用。

## 四、可测判据

1. `grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt`：B26 清单 5 条口径残留 0，`exit=1`。
2. `grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt`：空命中，`exit=1`。
3. `grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt`：空命中，`exit=1`。
4. 派生 visible 现役 5 处，行号清单见 `docs/修复-B29-常量visible-dict-replace.md` §四.2；控制器实例化 5 份（dict 2 + replace 3）；`.open()` 5 处业务登记全部过闸；`HalfSheetExitHost` 实例化 5 处。
5. `grep -rnE "enum class|sealed (class|interface)|ExitGate|ExitPhase|Stage" app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt`：命中全为业务模型枚举 / sealed（`DictRichBlock` / `ReplaceFilter` / `BatchGroup` / `Reason`），无离场阶段机。
6. `python3 data_scripts/kotlin-syntax-lint.py` 本批 17 个相关文件 PASS；禁跑 gradlew / kotlinc，编译与单测全绿归云端 CI。

## 五、兜底边界与交接项

- F-1（待裁 · 宿主级闸未接，沿用 source F-2 同型边界）：两域程序化入口由 VM 先置槽位、Screen `LaunchedEffect` 观察槽位再过 `canOpen` 登记；离场 ~450ms 内宿主触摸由浮层窗口截获兜底。若要求 VM / Activity 入口串联 `canOpen`，需另派单改宿主层。
- F-2（判废 · 派生可见性，非常量缺陷）：`app/src/main/java/io/legado/app/ui/dict/DictDialog.kt:213-215` `visible = viewerSrc != null` 为派生布尔；内层内容 `viewerSrc?.let` 随 null 立即清空，Shell 壳保留组合供离场动画。不属 B29 常量 true 口径，本单不改。
- F-3（判废 · 无动画挂载点）：两域纯 `Dialog` 弹窗群（删除确认 / 选择栏 / 分组名输入 / 在线导入 URL / 退出确认）直接挂 `androidx.compose.ui.window.Dialog`，未包 `InkHalfSheet` / `InkBottomSheet`，无遮罩回亮 / 下降动画组件，两段式无挂载点；若日后包进模态 host，需另派单补离场闸。
- F-4（待裁 · 测试覆盖边界）：`app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt` 8 例 `mutableStateOf` JVM 实例化，跑绿以 CI 为准再闭环。
- F-5（判废 · 既有边界）：控制器由 `remember` 持有，阶段与离场位不跨配置变更 / 进程重建存活；本单范围外挂账。
- F-6（已入队 · 架构债）：B21 `ReaderSheetExitGate` / B26 `NoteDialogExitGate` / config `SheetExitController` / source `SourceSheetExitController` 多份相近包装并存，建议后续合并为泛型纯 JVM 单一守卫 + 复用 Compose 薄封装。
- 域外同形态残留（已入队转对应域，非本单口径，不扩改）：`config/b3/B3SettingScreen.kt:151` / `:164` / `:180` / `:193` 四条由 config 收口单 §七 F-1 登记；`components/HandleFileSheet.kt:47` 契约冻结红线；`book/read/ink/InkContentEditState.kt:93` / `:232` 为状态持有类内部赋值，非浮层传参。
