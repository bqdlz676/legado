# 锚点卡 · Q461 B29 book·source 域 12 条收口核实

> 用途：B29 book·source 域 12 条常量 visible 收口收尾核实依据；被核报告 `docs/修复-B29-常量visible-book-source.md`。
> 缺陷出自 `docs/问题清单.md:73` B29 行；清单来源 `docs/修复-B26-常量visible接线.md` §6.3 第 7–18 条；范式来源 `docs/修复-B21-半屏离场动画.md` + `docs/修复-B26-常量visible接线.md`；B30 单源收敛基线 `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:12-153`。
> 逐字引文带 文件:行，禁转述；锚点禁裸 `:NNN`。

## 一、逐字引文（带 文件:行）

- `docs/问题清单.md:73`：「**域外 34 条常量 visible 同形态**：autoTask 4 / book·source 12 / config 13 / dict 2 / replace 3，均为条件渲染宿主 + dismiss 即摘子树，离场动画被跳过」。
- `docs/修复-B26-常量visible接线.md:269`：「book/source 域（12 条，宿主 `when(state.sheet)` / `state.xxx?.let`，`vm.closeSheet()` 置 `ManageSheet.None` 摘子树）」。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:144-145`：`val visible: Boolean`；`get() = phase == ReaderSheetExitPhase.OPEN`（可见性派生唯一事实源）。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:148-149`：`val isComposed: Boolean`；`get() = phase != ReaderSheetExitPhase.REMOVED`（子树保留口径唯一处）。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:152-153`：`val canOpen: Boolean`；`get() = phase != ReaderSheetExitPhase.EXITING`（离场拒收口径唯一处）。
- `app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:19`：`class NoteDialogExitGate : SheetExitPhaseGate()`（B26 守卫继承 B30 基类，未自写派生判断）。
- `app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt:39`：`val visible: Boolean get() = gate.visible`；`:43` `isComposed`；`:47` `canOpen`（source 薄封装全部委托基类派生）。
- `app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt:94`：`const val DURATION_MASK: Int = 200`（遮罩离场时长依据；`HalfSheetModel.kt:53` `MASK_DURATION_MS: Int = 200` 同值持有）。
- `app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt:133`：`if (progress.value == 0f && !visible) return`（组件内部保留帧 / 收束条件，离场段子树在此之前保留组合）。

## 二、12 条核对锚点（基线 `739f073^`（`aee8f48`）→ 现役 HEAD `f896179`）

> 缩写：`SRC=app/src/main/java/io/legado/app/ui/book/source`；`MGT=SRC/manage`。

1. `MGT/SourceManageSheets.kt` 主选项 Sheet：基线 `:85` `visible = true,`；现役 `:88` 控制器、`:89` 闸 + `open()`、`:90` `SourceSheetExitHost`（`onFinished = vm::closeSheet`）、`:91` `visible = exit.visible` + `beginExit()`。
2. `MGT/SourceManageSheets.kt` 排序 Sheet：基线 `:145` `visible = true,`；现役 `:144` 控制器、`:145` 闸、`:155` host、`:156` 派生 visible + dismiss；选中离场 `:162`。
3. `MGT/SourceManageSheets.kt` 分组筛选 Sheet：基线 `:167` `visible = true,`；现役 `:179` 控制器、`:180` 闸、`:181` host、`:182` 派生 visible + dismiss；离场 `:196` / `:203`。
4. `MGT/SourceManageSheets.kt` 分组管理 Sheet：基线 `:202` `visible = true,`；现役 `:218` 控制器、`:219` 闸、`:220` host、`:221` 派生 visible + dismiss。
5. `MGT/SourceManageSheets.kt` 在线导入 Sheet：基线 `:296` `visible = true,`；现役 `:316` 控制器、`:317` 闸、`:318` host、`:319` 派生 visible + dismiss；确认离场 `:347`。
6. `MGT/SourceManageSheets.kt` 校验来源 Sheet：基线 `:337` `visible = true,`；现役 `:361` 控制器、`:362` 闸、`:363` host、`:364` 派生 visible + dismiss；触发离场 `:378`。
7. `MGT/SourceManageSheets.kt` 单来源菜单 Sheet：基线 `:374` `visible = true,`；现役 `:402` 控制器、`:403` 闸、`:404` host、`:405` 派生 visible + dismiss；动作离场 `:410` / `:415` / `:420` / `:422` / `:423` / `:433` / `:439`。
8. `MGT/SourceManageSheets.kt` 多选统计 Sheet：基线 `:422` `visible = true,`；现役 `:454` 控制器、`:455` 闸、`:456` host、`:457` 派生 visible + dismiss；动作离场 `:459` / `:460` / `:461` / `:462` / `:463` / `:473` / `:474` / `:475` / `:476` / `:480`。
9. `MGT/SourceManageSheets.kt` 分组输入 Sheet：基线 `:459` `visible = true,`；现役 `:496` 控制器、`:497` 闸、`:498` host（`onFinished = onDismiss`）、`:499` 派生 visible + dismiss；确认离场 `:513`。
10. `MGT/SourceManageSheets.kt` 导出结果 Sheet：基线 `:489` `visible = true,`；现役 `:530` 控制器、`:531` 闸、`:532` host（`onFinished = onDismiss`）、`:533` 派生 visible + dismiss。
11. `SRC/edit/SourceEditScreen.kt` 溢出菜单 Sheet：基线 `:426` `visible = true,`；现役 `:89` `overflowExit` 控制器、`:101` 闸、`:103` `open()`、`:155` host（收束清 `overflowOpen`）、`:160` 派生 visible、`:161` `beginExit()`；内层 `:435` 形参、`:438` 透传。
12. `SRC/debug/SourceDebugScreen.kt` 溢出菜单 Sheet：基线 `:401` `visible = true,`；现役 `:79` `overflowExit` 控制器、`:93` 闸、`:95` `open()`、`:118` host（收束清 `overflowOpen`）、`:121` 派生 visible、`:122` `beginExit()`；内层 `:409` 形参、`:413` 透传。

## 三、口径锁（不许改）

- 阶段枚举三态 `OPEN / EXITING / REMOVED` 定义唯一处 `SheetExitPhaseGate.kt:12`；禁造第二份阶段机、第二份守卫类、第二份迁移规则。
- 派生 `visible / isComposed / canOpen` 只许来自 B30 基类一处；source 薄封装 `SourceSheetExitController` 只做委托转发，不内联阶段比较。
- 两段式关闭：dismiss 先走 `beginExit()` 进离场段（派生 visible=false、子树保留），`SourceSheetExitHost` 按遮罩 200ms + 下降 250ms 等效延时后 `finishExit()` 并回调宿主清槽位摘子树；禁 dismiss 即摘子树。
- 离场段拒收新打开：打开点先过 `canOpen` 闸再 `open()`；在场段判重仍归宿主既有槽位状态（VM `state.sheet` 单槽位 / local `overflowOpen`），两道闸串联不替代。
- 未打开 / 已摘除段 visible 必须为 false；离场段子树保留至收束帧，组件内部条件见 `InkHalfSheet.kt:133`。
- Compose 侧禁引入依赖 Application 初始化的配置读取（Paparazzi 截图环境无 Application）；既有直读项见 §五 F-1，未在本单扩改。

## 四、可测判据

1. `grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt`：B26 清单 12 条口径残留 0，`exit=1`。
2. `grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt`：空命中，`exit=1`。
3. `grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt`：空命中，`exit=1`。
4. 派生 visible 现役 12 处，行号清单见 `docs/修复-B29-常量visible-book-source.md` §4.2；控制器实例化 12 份；`.open()` 12 处业务调用全部过闸；`SourceSheetExitHost` 实例化 12 处。
5. `grep -rnE "enum class|sealed (class|interface)|ExitGate|ExitPhase|Stage" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt`：命中全为业务模型枚举 / sealed，无离场阶段机。
6. `python3 data_scripts/kotlin-syntax-lint.py` 本批 5 个相关文件 PASS；禁跑 gradlew / kotlinc，编译与单测全绿归云端 CI。

## 五、兜底边界与交接项

- F-1（待另派单 · Paparazzi 风险同类）：`MGT/SourceManageSheets.kt:131` Compose 组合期直读 `AppConfig.blockSourceNavigation`（getter 走 `appCtx`，`app/help/config/AppConfig.kt:47-49`）；建议补入 `SourceManageState` 由 VM 读入透传，对照同屏 `showCheckStatus` / `showBookshelfCount` 既有口径。
- F-2（留观 · 宿主级闸未接）：`SourceManageViewModel.kt:460` `openSheet` 与 `SourceManageScreen.kt:98` / `:378` / `:460`、`BookSourceActivity.kt:67` 程序化入口未串联 `canOpen`；离场 ~450ms 由浮层窗口截获触摸兜底，需另派单改 VM / Activity。
- F-3（留观 · 交互缺口）：调试页溢出 `SourceDebugScreen.kt:415` / `:417` / `:420` / `:423` / `:426` / `:428` / `:429`、编辑页开关 `SourceEditScreen.kt:448`、分组管理 `SourceManageSheets.kt:257` / `:277` / `:300`、导出结果 `:557` / `:564` 动作行不自动关层；基线即如此，是否补离场动画另单评估。
- F-4（待 CI）：`SourceSheetExitControllerTest.kt` 7 例 `mutableStateOf` JVM 实例化，跑绿以 CI 为准。
- F-5（既有边界）：控制器由 `remember` 持有，阶段与离场位不跨配置变更 / 进程重建存活。
- F-6（架构债）：B21 / B26 / config / source 四份相近包装并存，建议后续合并为泛型纯 JVM 单一守卫 + 复用 Compose 薄封装。
- 域外 autoTask / config / dict / replace 四域已由各自分域单收口；components 红线 `HandleFileSheet.kt:47` 契约冻结不动。
