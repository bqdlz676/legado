> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=6ab072c0e6de；文档=6dd8114a6a74）并复核 3 条结论，漂移 1 条（见 回执补录-批3-阅读器贴皮 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-B26：浮层可见性传常量 true 导致离场动画被跳过（两段式关闭收口）

锚点卡：docs/锚点/B26-常量visible接线.md

> 日期：2026-11-26 ｜ 执行代理：B26 写码专单（Q-97）｜ 基线：本单未执行 git add / commit / push（沙箱禁提交）。
> 锚点卡：`docs/锚点/B26-常量visible接线.md`（五条判据与边界以它为准）。
> 缺陷条目：`docs/问题清单.md:72` B26 行。
> 标准来源：`docs/修复-B21-半屏离场动画.md`（两段式关闭实现范式）+ `docs/验收-B21-半屏离场动画.md`。
> E3 结果：✓ 已落码待 CI——grep 回证每处改动真写入；本地禁编译 / 禁跑测（沙箱无 gradle / JDK21 / SDK），编译与单测结果一律标「未验证，等 CI」。
> 仓库：`bqdlz676/legado`；工作树改动，未做任何 `git add / commit / push`。

## 1. 缺陷原貌与本单结论

缺陷原貌（锚点卡 §一 两条事实锚点，已逐条复核现役代码）：

- `ui/book/read/HighlightNoteDialog.kt`（派单书写 `ui/note/`，现役实际路径 `ui/book/read/`，B21 验收 §九.1 已记行号漂移）旧 :86：`InkHalfSheet(visible = true, ...)`，可见性恒真。
- 单点出口 `dismissOnce` 旧实现直接 `dismissAllowingStateLoss()`：DialogFragment 窗口与 ComposeView 一并销毁，子树立即摘除；`InkHalfSheet.kt:121-130` 的 `visible=false` 离场分支（遮罩回亮 + Sheet 下降）永不执行，与 B21 修复前 `ReaderSheetOverlay` 同形态。

本单处置口径（对齐 B21 两段式关闭）：

1. 关闭不直摘：`dismissOnce` 先把阶段交离场守卫进入离场段（`beginExit`、`exitingState` 翻 true、派生 `visible=false`），弹窗子树保留组合，播组件内离场动画；写库副作用仍在决策时刻执行，时机与旧版一致。
2. 收束再摘：离场动画收束后（等效延时对齐组件自有遮罩 + Sheet 两段时长），`LaunchedEffect` 回调 `finishExit()` 清阶段，再 `dismissAllowingStateLoss()` 摘 Fragment。摘除时机有明确依据，不写「立即摘」。
3. 离场段拒收：既有 `submitted` 单点出口闸保留；离场守卫 `canOpen` 在 EXITING 段为 false，不叠新弹窗。
4. 阶段迁移规则下沉纯 JVM 守卫 `NoteDialogExitGate`，普通单测可断言「关闭时子树仍在、visible 已 false」。

编译结论：**未验证，等 CI**。沙箱无 gradle / JDK21 / SDK，本地未跑 `./gradlew` / `kotlinc`；新增 7 条守卫断言是否全绿以云端 CI 为准。

## 2. 改动清单（绝对路径）

### 2.1 新增文件

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt`
  - 纯 JVM 离场两段式守卫（不引用 Android / Compose 类型）。
  - 阶段枚举直接复用 B21 `ReaderSheetExitPhase`（OPEN / EXITING / REMOVED，`ui/book/read/ink/ReaderSheetExitGate.kt:14-18`），迁移口径与 B21 `ReaderSheetExitGate` 一致：`open()` 回在场段；`beginExit()` 仅 OPEN → EXITING，非在场段空操作；`finishExit()` 仅 EXITING → REMOVED，非离场段空操作。
  - 派生只读：`visible`（仅 OPEN 段 true）、`isComposed`（OPEN / EXITING 在场，REMOVED 摘除）、`canOpen`（EXITING 段拒收）。
  - 与 B21 守卫的差异：不持型缓存请求。B21 守卫缓存 `ReaderDetailRequest` 供阅读点字半屏离场段继续组合；本弹窗 highlight 参数在构造时固定，无会话请求需要缓存，故无 retainedRequest 槽位。泛化 B21 守卫属 reader 域冻结范围（B20/B21 已落），本单不动。

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/test/java/io/legado/app/ui/book/read/NoteDialogExitGateTest.kt`
  - 7 条纯 JVM 断言：初始态、在场态、关闭瞬间子树保留且 visible=false、离场段拒收、收束摘除、空操作兜底、收束后重开。

### 2.2 修改文件

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt`
  - 新增字段 `exitGate`（纯 JVM 守卫）与 `exitingState`（Compose 可观察离场位，Fragment 字段，理由同既有 `submitted`：返回键监听在 Dialog 窗口层，需在 Compose 作用域外可写）。
  - `onCreateView`：视图组装前 `exitGate.open()` 登记在场段；新增 `LaunchedEffect(exitingState.value)`，离场段按 `MASK_DURATION_MS + RISE_DURATION_MS` 等效延时后 `finishExit()` 再摘 Fragment（`isAdded` 守卫防延时窗口内 Fragment 已分离）。
  - `InkHalfSheet` 可见性由常量 `true` 改为派生 `visible = !exitingState.value`；标题 / 正文槽 / 按钮回调 / `dialog` 接线不变。
  - `dismissOnce`：移除直接 `dismissAllowingStateLoss()`；改为 `exitGate.beginExit()` + `exitingState.value = true` 进离场段；`submitted` 闸与写库副作用口径原样保留。
  - 新增 import：`LaunchedEffect`、`HalfSheetAnchors`、`kotlinx.coroutines.delay`。

### 2.3 未触碰（范围红线）

- 未碰 `ui/book/read/ink/` 任何 B20/B21 已落文件：`ReaderSheetExitGate.kt` / `ReaderSheetState.kt` / `ReaderSheetOverlay.kt` / `ReaderSheetGuard.kt` / `ReadBookActivity.kt` 阅读半屏链路零改动。
- 未碰 `ui/components/` 契约：`InkHalfSheet.kt` / `InkBottomSheet.kt` 离场分支、保留帧条件、时长常量原样未动。
- 未碰地图域、名物库（help/artifact）、help 其余包、autoTask / source / dict / replace / config 域外屏幕。
- 两个外部打开点 `ReadBookActivity.kt:1523`、`HighlightFragment.kt:163` 构造签名未变，零改动。
- 关联留档：B27 `MapWebHolder.kt` 清剿与 `DesignTokenGuardTest.kt` 基线摘除属另一在飞单（已入库 HEAD `1dc31aa`），非本单产物，验收勿并入 B26 口径。

## 3. E1 grep 回证（命令 + 原样输出）

### 3.1 全仓常量 visible 命中（修复后残留 35 条 = components 红线 1 + 域外 34）

命令：

```
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt | wc -l
```

原样输出：

```
35
```

命令：

```
grep -rn "visible=true" app/src/main --include=*.kt; echo "exit=$?"
```

原样输出：空；`exit=1`（无空格写法零命中）。

### 3.2 目标文件常量残留排查（应为空）

命令：

```
grep -n "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt; echo "exit=$?"
```

原样输出：空；`exit=1`。旧 :86 恒真 `true` 已清零，现役传参为派生值（见 3.5）。

### 3.3 纯守卫符号落位

命令：

```
grep -n "var phase\|fun open\|fun beginExit\|fun finishExit\|val visible\|val isComposed\|val canOpen" app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt
```

原样输出：

```
22:    var phase: ReaderSheetExitPhase = ReaderSheetExitPhase.REMOVED
26:    fun open() {
34:    fun beginExit() {
43:    fun finishExit() {
49:    val visible: Boolean
53:    val isComposed: Boolean
60:    val canOpen: Boolean
```

### 3.4 弹窗两段式接线落位

命令：

```
grep -n "exitGate\|exitingState\|LaunchedEffect\|HalfSheetAnchors\|visible = " app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt
```

原样输出：

```
16:import androidx.compose.runtime.LaunchedEffect
33:import io.legado.app.ui.components.HalfSheetAnchors
65:    private val exitGate = NoteDialogExitGate()
72:    private val exitingState = mutableStateOf(false)
94:            exitGate.open()
106:                LaunchedEffect(exitingState.value) {
107:                    if (exitingState.value) {
108:                        delay((HalfSheetAnchors.MASK_DURATION_MS + HalfSheetAnchors.RISE_DURATION_MS).toLong())
109:                        exitGate.finishExit()
115:                    visible = !exitingState.value,
213:     * （[exitGate.beginExit] + [exitingState] 翻 true）播组件内离场动画；Fragment 的
221:        // LaunchedEffect(exitingState)。离场段 [exitGate.canOpen] 为 false，不叠新弹窗。
222:        exitGate.beginExit()
223:        exitingState.value = true
```

### 3.5 测试断言条数

命令：

```
grep -c "@Test" app/src/test/java/io/legado/app/ui/book/read/NoteDialogExitGateTest.kt
```

原样输出：

```
7
```

测试反引号函数名禁字符复核（`. : [ ] / ; < >`）：命令 `grep -n '`' NoteDialogExitGateTest.kt | grep -E '[.:\[\]/;<>]'` 输出空，`exit=1`。

### 3.6 红线文件零改动

命令：

```
git diff --stat -- app/src/main/java/io/legado/app/ui/book/read/ink/ app/src/main/java/io/legado/app/ui/components/ app/src/main/java/io/legado/app/ui/map/ app/src/main/java/io/legado/app/help/artifact/
```

原样输出：空（diff 无命中）。B20/B21 阅读半屏链路、组件契约、地图、名物库均未改。

### 3.7 外部打开点零改动

命令：

```
grep -n "HighlightNoteDialog(" app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt app/src/main/java/io/legado/app/ui/book/toc/HighlightFragment.kt
```

原样输出：

```
app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt:1523:                ACTION_HIGHLIGHT_NOTE -> showDialogFragment(HighlightNoteDialog(highlight))
app/src/main/java/io/legado/app/ui/book/toc/HighlightFragment.kt:163:        showDialogFragment(HighlightNoteDialog(highlight))
```

### 3.8 本单改动文件清单

命令：

```
git status --short | grep -v "docs/"
```

原样输出：

```
 M app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt
?? app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt
?? app/src/test/java/io/legado/app/ui/book/read/NoteDialogExitGateTest.kt
```

工作区无其他非 docs 改动；B27 `MapWebHolder.kt` 清剿与 `DesignTokenGuardTest.kt` 基线摘除已由在飞单提交入库（HEAD `1dc31aa`），不属本单产物。

## 4. E2 文件:行 索引（改动后现役行号）

- `NoteDialogExitGate.kt`
  - :22 `phase`（私有写，初始 REMOVED）
  - :26 `open()`；:34 `beginExit()`；:43 `finishExit()`
  - :49 `visible`；:53 `isComposed`；:60 `canOpen`
- `HighlightNoteDialog.kt`
  - :65 `exitGate` 实例；:72 `exitingState` 可观察离场位
  - :94 `exitGate.open()` 在场段登记
  - :106-112 `LaunchedEffect(exitingState.value)` 等效延时收束回调（:108 延时表达式，:109 `finishExit`，:111 `isAdded` 守卫后 `dismissAllowingStateLoss`）
  - :115 `visible = !exitingState.value`
  - :215-223 `dismissOnce`：:217 `submitted` 闸，:218 写库副作用，:221 `beginExit`，:222-223 离场位翻 true
- `NoteDialogExitGateTest.kt`
  - :21 初始态；:31 在场态；:43 关闭瞬间子树保留 + visible=false；:56 离场段拒收；:69 收束摘除；:83 空操作兜底；:97 收束后重开。

## 5. 五条判据逐条自检

判据 1（先出全仓调用点清单：grep `visible = true` 与 `visible=true`，逐条判定浮层可见性常量 / 无关布尔，清单进报告）——**PASS（代码层，待 CI）**

- 全仓两种写法穷举：`visible\s*=\s*true` 修复后残留 35 条（E1.1）；`visible=true` 无空格写法零命中（E1.1 `exit=1`）。
- 35 条逐条上下文核对（清单见 §6）：全部为 `InkHalfSheet` / `InkBottomSheet` 或其薄封装 Sheet 的可见性形参传常量；**无关布尔命中 0 条**（无卡片显隐、字段可见性等其他语义混入）。
- 清单含每条文件:行、被调组件、宿主条件渲染形态与处置结论，未只改目标一处即报清完。

判据 2（浮层类调用点改两段式；复用 B21 状态机语义，禁止第二份实现）——**PASS（本单文件域内，代码层，待 CI）；域外 34 条登记交接，未在本单改动**

- 本单文件域内唯一浮层调用点 `HighlightNoteDialog.kt:115` 已改两段式：第一段 `beginExit` + `visible=false` 播离场，第二段延时收束 `finishExit` 后摘 Fragment。
- 状态机复用口径：阶段枚举 `ReaderSheetExitPhase` 直接引用 B21 `ReaderSheetExitGate.kt:14-18`；迁移规则（OPEN→EXITING→REMOVED、非在场 beginExit 空操作、非离场 finishExit 空操作、visible 仅 OPEN 为真、canOpen 仅 EXITING 拒收）与 B21 守卫逐条同口径，未另造第二套阶段枚举。
- 未复用 B21 持型守卫的原因：其 `retainedRequest` 类型绑定 `ReaderDetailRequest`（阅读点字半屏域），本弹窗无会话请求缓存需求；泛化该守卫需改 B21 已落文件，属红线。差异已在守卫 KDoc 写明（§2.1）。
- 域外 34 条同形态项的处置依据见 §6 末段：派单台账 Q-97 文件域仅 `HighlightNoteDialog.kt`；D1 一问题一代理；B21 验收 §八.4 已将这些屏幕留档为非 B21 范围；各屏状态宿主（ViewModel / local remember / DialogFragment）不同，需按域另立条目收口，本单不跨域打包改动。

判据 3（离场期间不可重复触发打开；与既有闸口径一致）——**PASS（代码层，待 CI）**

- 第一道：既有 `submitted` 单点出口闸（:217）原样保留，所有离开路径（确认 / 删除 / 取消 / 下滑 / 遮罩点击 / 返回键）二次触发整段 no-op，不写库、不重复 dismiss。
- 第二道：`exitGate.canOpen` 在 EXITING 段为 false（守卫 :60）；离场段重复 `beginExit` 不改阶段（测试 :56-66）。
- 边界说明：本弹窗为 DialogFragment 独立窗口，离场 ~450ms 内宿主触摸被窗口截获；两个外部打开点每次新建 Fragment 实例，不存在实例内重开路径。Activity 级离场入口闸（对照 B21 `ReadBookActivity.kt:1555` 的 `isExiting` 闸）未接线——接入需改 `ReadBookActivity`，属 reader 域红线，本单不动；该缺口记入 §8 边界。

判据 4（进入动画不受影响）——**PASS（静态层，待 CI）**

- 新会话：`onCreateView` :94 `exitGate.open()` 置 OPEN 段；`exitingState` 初始 false，:115 派生 `visible=true`，与旧恒真在场态等价。
- 组件进入分支未改：`InkHalfSheet.kt:105-106` 初始 `Animatable(if (visible) 1f else 0f)`、:111-120 进入分支原样保留；进入时长 / 缓动仍由 `HalfSheetAnchors.RISE_DURATION_MS` / `MASK_DURATION_MS` 与 `ComponentTokens` 持有，本单未调整。

判据 5（可断言：状态逻辑落纯 JVM 层，至少一条断言覆盖「关闭时子树仍在、visible 已 false」）——**PASS（纯 JVM，待 CI 跑绿）**

- 阶段迁移抽为纯 JVM 守卫 `NoteDialogExitGate`，无 Compose / Android import；唯一外部引用是 B21 阶段枚举 `ReaderSheetExitPhase`（其所在 `ReaderSheetExitGate.kt` 仅 import 纯 Kotlin 模型 `ReaderDetailRequest`，见 B21 验收 §六 纯 JVM 边界复核）。
- 核心断言 `NoteDialogExitGateTest.kt:43-53`：`beginExit` 后 `phase=EXITING`、`isComposed=true`（子树仍在）、`visible=false`。
- 延时等待留在 Compose 层（:106-112），不进入守卫断言；与 B21 同一边界划分。

## 6. 全仓调用点清单（逐条判定结论）

判定口径：被调组件为 `InkHalfSheet` / `InkBottomSheet` 或其薄封装 Sheet，首参 `visible` 传常量 `true`，且宿主以条件渲染（`?.let` / `if (state.xxx)` / `when` 分支 / 封装内 `if (!visible) return`）承载——dismiss 清状态即摘子树，组件 `visible=false` 离场分支不执行，记「浮层可见性常量·同形态」。无关布尔：0 条。

### 6.1 本单已修（1 条）

1. `app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt:86`（现役 :115）——`InkHalfSheet`，DialogFragment 壳；**已改两段式**（§2.2）。

### 6.2 红线内不动（1 条）

2. `app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt:48`——`InkHalfSheet`；**ui/components 契约冻结，B26 红线不动**；同形态观察项留档，组件契约改动须随组件单独立项。

### 6.3 域外同形态观察项（34 条，本单不改，按域交接）

autoTask 域（4 条，宿主 `jsonTarget?.let` / `state.helpText?.let` / local Sheet 回调）：

3. `app/src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt:151`——`InkHalfSheet` 帮助页。
4. `app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt:168`——`InkHalfSheet` 帮助页。
5. `app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt:403`——`InkHalfSheet` 运行日志 Sheet（`LogSheet`）。
6. `app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt:98`——`InkHalfSheet`，DialogFragment 壳内 `jsonTarget?.let`，形态与目标弹窗最接近。

book/source 域（12 条，宿主 `when(state.sheet)` / `state.xxx?.let`，`vm.closeSheet()` 置 `ManageSheet.None` 摘子树）：

7. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:85`——`InkHalfSheet` 主选项 Sheet。
8. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:145`——`InkHalfSheet` 排序 Sheet。
9. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:167`——`InkHalfSheet` 分组 Sheet。
10. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:202`——`InkHalfSheet` 分组管理 Sheet。
11. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:296`——`InkHalfSheet` 在线导入 Sheet。
12. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:337`——`InkHalfSheet` 来源勾选 Sheet。
13. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:374`——`InkHalfSheet` 单来源菜单 Sheet。
14. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:422`——`InkHalfSheet` 多选统计 Sheet。
15. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:459`——`InkHalfSheet` 通用标题 Sheet。
16. `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:489`——`InkHalfSheet` 导出 Sheet。
17. `app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt:426`——`InkHalfSheet` 溢出菜单 Sheet（`OverflowSheet`）。
18. `app/src/main/java/io/legado/app/ui/book/source/debug/SourceDebugScreen.kt:401`——`InkHalfSheet` 溢出菜单 Sheet（`OverflowSheet`）。

config 域（13 条；外层为薄封装 Sheet 调用，内层 `SettingSheets.kt` / `SettingSheetHost.kt` 包 `InkBottomSheet`）：

19. `app/src/main/java/io/legado/app/ui/config/ThemeListDialog.kt:102`——`ConfirmSheet` 封装。
20. `app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt:129`——`ListPickSheet` 封装。
21. `app/src/main/java/io/legado/app/ui/config/BottomBarSkinActivity.kt:237`——`ConfirmSheet` 封装。
22. `app/src/main/java/io/legado/app/ui/config/sections/WelcomeSection.kt:68`——`NumberPickerSheet` 封装。
23. `app/src/main/java/io/legado/app/ui/config/sections/OtherSection.kt:174`——`NumberPickerSheet` 封装。
24. `app/src/main/java/io/legado/app/ui/config/sections/OtherSection.kt:185`——`TextEditSheet` 封装。
25. `app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt:75`——`NumberPickerSheet` 封装。
26. `app/src/main/java/io/legado/app/ui/config/settings/SettingSheetHost.kt:16`——`InkBottomSheet` 薄封装 `InkBottomSheetHost`。
27. `app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt:54`——`InkBottomSheet`（`NumberPickerSheet` 内层）。
28. `app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt:109`——`InkBottomSheet`（`TextEditSheet` 内层）。
29. `app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt:154`——`InkBottomSheet`（`ListPickSheet` 内层）。
30. `app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt:194`——`InkBottomSheet`（`MultiChoiceSheet` 内层）。
31. `app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt:235`——`InkBottomSheet`（`ConfirmSheet` 内层）。

dict 域（2 条）：

32. `app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt:377`——`InkHalfSheet` 规则编辑 Sheet（`state.editTarget?.let` 宿主）。
33. `app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt:416`——`InkHalfSheet` 帮助页（`state.helpText?.let` 宿主）。

replace 域（3 条）：

34. `app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt:198`——`InkHalfSheet` 帮助页（`state.helpText?.let` 宿主）。
35. `app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt:500`——`InkHalfSheet` 分组管理 Sheet（`state.groupManageOpen` 宿主，`GroupManageSheet`）。
36. `app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt:181`——`InkHalfSheet` 帮助页（`state.helpText?.let` 宿主）。

### 6.4 域外项处置理由（逐条看过，非没看就跳过）

- 上述 34 条均为同形态浮层常量接线，**不属于**「一次性、无离场动画需求」而豁免：组件内离场分支客观存在，只是被子树立即摘除跳过；帮助页 / 菜单 Sheet 同样走遮罩回亮 + 下降动画。
- 不改的唯一依据是文件域边界：B26 派单台账 Q-97 文件域仅 `HighlightNoteDialog.kt`；规矩 D1 一问题一代理、E9 文件域互斥；B21 验收 §八.4 已将这些屏幕登记为留档观察项。各屏状态宿主分属 ViewModel 与 local remember，收口需逐屏接保留请求 + 离场闸 + 收束回调，建议按 autoTask / source / config / dict / replace 五个域各立条目。
- 复用提示：将来第二个、第三个消费域落地两段式时，应回头把 B21 持型守卫与本单无负载守卫合并为一个泛型纯 JVM 守卫（`<T>` 或无负载两档），避免阶段机随域复制第三份；该合并属 reader 域重构，不在本单范围。

## 7. 二阶影响（全仓消费点逐个说明）

### 7.1 目标弹窗内部

- 写库副作用时机不变：`commitAction` 仍在 `dismissOnce` 决策时刻执行（:218），不延后到动画收束；`ReadBook.updateHighlight` / `ReadBook.removeHighlight` 调用口径与旧版一致。
- 表单状态 `bookText` / `note` 为 `remember` 持有，离场 ~450ms 内子树保留，输入内容不丢；收束摘 Fragment 后随窗口销毁，无残留。
- 缺参数防御路径（:87-90）保持直接 `dismissAllowingStateLoss()`：该路径不渲染表单子树，无动画可播，不套两段式。

### 7.2 与既有闸的交互

- `submitted` 闸语义未改：仍是所有离开路径的单点出口；两段式只把「闸置位后的实际摘窗」从即时改为收束后。离场段二次触发仍被 `submitted` 挡住，不会二次写库。
- `exitGate.canOpen` 只拦离场段；在场段重复触发的判重仍归 `submitted` 闸，两道闸串联不替代。
- 返回键监听（`onStart` :192-199）口径不变：ACTION_UP 交 `dismissOnce`；离场段再按返回键为 no-op。

### 7.3 外部消费点排查

- `ReadBookActivity.kt:1523`、`HighlightFragment.kt:163` 两处 `showDialogFragment(HighlightNoteDialog(highlight))` 构造签名未变，无需改动。
- 弹窗无 ViewModel 依赖，不接阅读页快照 / 高亮集合；`ReadBook` 模型调用未改。
- 未新增任何跨域引用；守卫仅反向引用 B21 阶段枚举，不向 reader 半屏链路回传状态。

### 7.4 二阶 / 三阶推演

- 二阶：离场段 Dialog 窗口仍在组合（子树保留），继续截获宿主触摸；宿主列表滚动 / 长按在离场 ~450ms 内不与 Sheet 手势抢通道，与 B21 验收 §7.2 结论一致。
- 二阶：离场段新打开被双闸（`submitted` + `canOpen`）拒收，不会出现「旧 Sheet 下降中叠起新弹窗」的双层竞态。
- 三阶：将来若在阅读页入口补 Activity 级离场闸（对照 B21 `isExiting` 闸），需把本弹窗离场位暴露给宿主；当前 Fragment 自持阶段，宿主不感知，接线时不得在离场段直接新建实例覆盖旧窗口。
- 三阶：延时时长与组件动画常量同源（`MASK_DURATION_MS` 200 + `RISE_DURATION_MS` 250 = 450ms，顺序播放）；日后组件调整时长，摘窗等待时长随之同步，不会出现时长漂移。

## 8. 边界（锚点卡 §三，如实写明现状）

- 等效延时而非完成回调：`InkHalfSheet` 未暴露动画结束回调，本单按锚点卡允许的「完成回调或等效延时」口径收口；若组件日后补完成回调，可把 :106-112 的 `delay` 替换为回调触发，守卫阶段口径不变。
- 离场段配置重建：`exitGate` / `exitingState` 为 Fragment 字段，旋转 / 重建随 Fragment 生命周期；离场中重建则离场位与阶段不跨重建存活（与 B20/B21 同一边界，本单不解决）。
- Activity 级离场入口闸未接：弹窗宿主侧无 `isExiting` 等价闸；离场 ~450ms 内依赖独立窗口截获触摸兜底。若验收认为需宿主级双闸，需另派单改 `ReadBookActivity` / `HighlightFragment`（reader 域红线，本单未动）。
- 域外 34 条同形态项 + components 1 条留档（§6.2 / §6.3），不在本单收口。
- 范围外遗留（沿用 B21 验收清单，本单不收口）：D3 `selectedSegments` 恒空集、D4 候选链接未接线、D5 固定分区模型未落地。

## 9. 未验证项

- 编译与单测：本地禁编译（无 gradle / JDK21 / SDK），未跑 `./gradlew` / `kotlinc`；`NoteDialogExitGateTest` 7 例是否全绿以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段窗口触摸截获，未在真机走查；观感由既有 `InkHalfSheet` 组件保证，本单仅接线状态口径。
- 动画时序：两段式摘窗时机为代码静态推断（延时依据 = 组件两段顺序动画时长之和 200+250ms），实际收束帧是否干净待 CI 出包后真机确认。
- 域外 34 条同形态项的实际影响面未逐条真机核验；清单判定基于源码条件渲染形态静态推断。

## 10. 交接备注

- 本单改动未 `git add / commit / push`；B27 清剿产物已由其在飞单提交入库（HEAD `1dc31aa`），与本单无关。
- 后续若改 `InkHalfSheet` 离场时长常量，:108 延时表达式自动跟随；若组件改为暴露 `onExitFinished` 回调，优先换回调、删等效延时分支。
- 域外屏幕收口时优先复用本单守卫口径；出现第三个消费域前，评估合并 B21 / B26 两份守卫为泛型纯 JVM 单一实现。
