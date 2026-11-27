# 锚点卡 · Q459 B29 config 域 13 条收口核实

> 用途：B29 config 域 13 条常量 visible 收口收尾核实依据；被核报告 `docs/收尾-B29config域13条.md`。
> 缺陷出自 `docs/问题清单.md:73` B29 行；清单来源 `docs/修复-B26-常量visible接线.md` §6.3 第 19–31 条；范式来源 `docs/修复-B21-半屏离场动画.md` + `docs/修复-B26-常量visible接线.md`；B30 单源收敛基线 `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:12-153`。
> 逐字引文带 文件:行，禁转述；锚点禁裸 `:NNN`。

## 一、逐字引文（带 文件:行）

- `docs/问题清单.md:73`：「**域外 34 条常量 visible 同形态**：autoTask 4 / book·source 12 / config 13 / dict 2 / replace 3，均为条件渲染宿主 + dismiss 即摘子树，离场动画被跳过」。
- `docs/修复-B26-常量visible接线.md:284`：「config 域（13 条；外层为薄封装 Sheet 调用，内层 `SettingSheets.kt` / `SettingSheetHost.kt` 包 `InkBottomSheet`）」。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:144-145`：`val visible: Boolean`；`get() = phase == ReaderSheetExitPhase.OPEN`（可见性派生唯一事实源）。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:148-149`：`val isComposed: Boolean`；`get() = phase != ReaderSheetExitPhase.REMOVED`（子树保留口径唯一处）。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:152-153`：`val canOpen: Boolean`；`get() = phase != ReaderSheetExitPhase.EXITING`（离场拒收口径唯一处）。
- `app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:11`：`class NoteDialogExitGate : SheetExitPhaseGate()`（B26 守卫继承 B30 基类，未自写派生判断）。
- `app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt:39-40`：`val visible: Boolean` / `get() = gate.visible`；`:43-44` `isComposed`；`:47-48` `canOpen`（config 薄封装全部委托基类派生）。
- `app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt:94`：`const val DURATION_MASK: Int = 200`（`SheetExitHost` 收束延时唯一时长依据，不另造魔法数）。
- `app/src/main/java/io/legado/app/ui/components/InkBottomSheet.kt:129`：`if (progress.value == 0f && !visible) return`（组件内部保留帧 / 收束条件，离场段子树在此之前保留组合）。

## 二、13 条核对锚点（基线 `14f646a^` → 现役 HEAD `267f775`）

> 缩写：`CFG=app/src/main/java/io/legado/app/ui/config`；`SET=CFG/settings`。

1. `CFG/ThemeListDialog.kt` 删除确认弹层：基线 `:102` `visible = true,`；现役 `:59` 控制器、`:97` 闸、`:99` `open()`、`:108` `SheetExitHost`、`:110` `visible = exit.visible,`、`:118` `beginExit()`。
2. `CFG/ConfigActivity.kt` 图标选择弹层：基线 `:129` `visible = true,`；现役 `:55` 字段控制器、`:95` 闸、`:97` `open()`、`:148` `SheetExitHost`、`:150` `visible = iconExit.visible,`、`:156` `beginExit()`。
3. `CFG/BottomBarSkinActivity.kt` 删除确认弹层：基线 `:237` `visible = true,`；现役 `:182` `confirmExit`、`:245` 闸、`:247` `open()`、`:256` `SheetExitHost`、`:258` `visible = confirmExit.visible,`、`:263` `beginExit()`；菜单 host 接 `menuExit`（`:181`、`:225`、`:227`、`:235`、`:236`）。
4. `CFG/sections/WelcomeSection.kt` 时间数字弹层：基线 `:68` `visible = true,`；现役 `:36` `timeExit`、`:44` 闸、`:46` `open()`、`:81` `SheetExitHost`、`:83` `visible = timeExit.visible,`、`:90` `beginExit()`；配图 host 接 `imageExit`（`:37`、`:56`、`:58`、`:68`、`:70`、`:96`、`:98`、`:100`）。
5. `CFG/sections/OtherSection.kt` 数字弹层：基线 `:174` `visible = true,`；现役 `:41` `numberExit`、闸 `:117/:133/:141/:149/:169/:175/:193`、`open()` `:121/:137/:145/:153/:171/:177/:195`、`:223` `SheetExitHost`、`:225` `visible = numberExit.visible,`、`:231` `beginExit()`。
6. `CFG/sections/OtherSection.kt` 文本弹层：基线 `:185` `visible = true,`；现役 `:42` `textExit`、闸 `:97/:105/:182`、`open()` `:101/:109/:186`、`:236` `SheetExitHost`、`:238` `visible = textExit.visible,`、`:243` `beginExit()`。
7. `CFG/sections/CoverFontSection.kt` 字号数字弹层：基线 `:75` `visible = true,`；现役 `:46` `sizeExit`、`:68` 闸、`:70` `open()`、`:81` `SheetExitHost`、`:83` `visible = sizeExit.visible,`、`:89` `beginExit()`。
8. `SET/SettingSheetHost.kt` 薄封装：基线 `:16` `InkBottomSheet(visible = true, ...)`；现役 `:15` `visible: Boolean,` 形参、`:20` `InkBottomSheet(visible = visible, ...)` 透传。
9. `SET/SettingSheets.kt` NumberPickerSheet 内层：基线 `:54` `InkBottomSheet(visible = true, ...)`、早退 `:52`；现役 `:41` 形参、`:55` `InkBottomSheet(visible = visible, ...)`，早退已删。
10. `SET/SettingSheets.kt` TextEditSheet 内层：基线 `:109` `InkBottomSheet(visible = true, ...)`、早退 `:107`；现役 `:100` 形参、`:110` 透传，早退已删。
11. `SET/SettingSheets.kt` ListPickSheet 内层：基线 `:154` `InkBottomSheet(visible = true, ...)`、早退 `:153`；现役 `:146` 形参、`:155` 透传，早退已删。
12. `SET/SettingSheets.kt` MultiChoiceSheet 内层：基线 `:194` `InkBottomSheet(visible = true, ...)`、早退 `:192`；现役 `:186` 形参、`:195` 透传，早退已删。
13. `SET/SettingSheets.kt` ConfirmSheet 内层：基线 `:235` `InkBottomSheet(visible = true, ...)`、早退 `:234`；现役 `:227` 形参、`:236` 透传，早退已删。

## 三、口径锁（不许改）

- 阶段枚举三态 `OPEN / EXITING / REMOVED` 定义唯一处 `SheetExitPhaseGate.kt:12`；禁造第二份阶段机、第二份守卫类、第二份迁移规则。
- 派生 `visible / isComposed / canOpen` 只许来自 B30 基类一处；config 薄封装只做委托转发，不内联阶段比较。
- 两段式关闭：dismiss 先走 `beginExit()` 进离场段（派生 visible=false、子树保留），`SheetExitHost` 按 `DURATION_MASK` 等效延时后 `finishExit()` 并回调宿主清槽位摘子树；禁 dismiss 即摘子树。
- 离场段拒收新打开：打开点先过 `canOpen` 闸再 `open()`；在场段判重仍归宿主既有槽位状态，两道闸串联不替代。
- 未打开 / 已摘除段 visible 必须为 false；离场段子树保留至收束帧，组件内部条件见 `InkBottomSheet.kt:129`。

## 四、可测判据

1. `grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/config/ --include=*.kt`：B26 清单 13 条口径残留 0；现役 4 条命中全在 `CFG/b3/B3SettingScreen.kt:151/:164/:180/:193`，属清单后新增代码，登记 F-1 另单。
2. `grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/config/ --include=*.kt`：空命中，exit=1。
3. `grep -rnE "enum class|sealed (class|interface)|ExitGate|ExitPhase|Stage" app/src/main/java/io/legado/app/ui/config/ --include=*.kt`：sealed 命中仅行模型 / 槽位状态（`B3Row` / `B3SheetTarget` / `LanState` / `SettingRow`），无离场阶段机。
4. 派生 visible 现役 22 处，行号清单见 `docs/收尾-B29config域13条.md` §4.2；`.open()` 47 处（不含控制器定义），46 处过闸，未过闸 1 处为 `BackupSection.kt:301` 程序化登记。
5. `python3 data_scripts/kotlin-syntax-lint.py` 本批 13 文件 PASS；禁跑 gradlew / kotlinc，编译与单测全绿归云端 CI。

## 五、兜底边界与交接项

- F-1（待另派单）：`CFG/b3/B3SettingScreen.kt:151/:164/:180/:193` + `:105` 直摘宿主，复用 `SheetExitController` / `SheetExitHost` 接线；本单未改。
- F-2（留观低风险）：`CFG/sections/BackupSection.kt:301` `qrExit.open()` 程序化登记未过 `canOpen` 闸；补闸或登记状态机入口例外。
- F-3（留观）：`CFG/sections/BackupSection.kt:283/:290` 导航切走未 `beginExit()`，阶段停留 OPEN；阶段账闭环另单评估。
- F-4（待 CI）：`SheetExitControllerTest.kt` 7 例 `mutableStateOf` JVM 实例化仓内无同型先例，跑绿以 CI 为准。
- F-5（既有边界）：`CFG/ConfigActivity.kt:55` Activity 字段控制器不跨配置变更 / 重建存活。
- F-6（留观待核）：`CFG/sections/ThemeSection.kt:246/:247` 背景图 Sheet 动作回调未自带 onDismiss，B3 批次一并核对。
- 域外 autoTask / book·source / dict / replace 四域已由各自分域单验收 PASS；components 红线 `HandleFileSheet.kt:47` 契约冻结不动。
