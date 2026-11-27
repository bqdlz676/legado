# 锚点卡 · Q464 B29 autoTask 域 4 条收口核实

> 用途：B29 autoTask 域 4 条常量 visible 收口收尾核实依据；被核报告 `docs/修复-B29-常量visible-autoTask.md`。
> 缺陷出自 `docs/问题清单.md:73` B29 行；清单来源 `docs/修复-B26-常量visible接线.md` §6.3 第 264–267 条；范式来源 `docs/修复-B21-半屏离场动画.md` + `docs/修复-B26-常量visible接线.md`；B30 单源收敛基线 `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:12-153`。
> 逐字引文带 文件:行，禁转述；锚点禁裸 `:NNN`。

## 一、逐字引文（带 文件:行）

- `docs/问题清单.md:73`：「**域外 34 条常量 visible 同形态**：autoTask 4 / book·source 12 / config 13 / dict 2 / replace 3，均为条件渲染宿主 + dismiss 即摘子树，离场动画被跳过」。
- `docs/修复-B26-常量visible接线.md:262`：「autoTask 域（4 条，宿主 `jsonTarget?.let` / `state.helpText?.let` / local Sheet 回调）」。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:144-145`：`val visible: Boolean`；`get() = phase == ReaderSheetExitPhase.OPEN`（可见性派生唯一事实源）。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:148-149`：`val isComposed: Boolean`；`get() = phase != ReaderSheetExitPhase.REMOVED`（子树保留口径唯一处）。
- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:152-153`：`val canOpen: Boolean`；`get() = phase != ReaderSheetExitPhase.EXITING`（离场拒收口径唯一处）。
- `app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:19`：`class NoteDialogExitGate : SheetExitPhaseGate()`（B26 守卫继承 B30 基类，未自写派生判断）。
- `app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt:39`：`val visible: Boolean get() = gate.visible`；`:43` `isComposed`；`:47` `canOpen`（config 薄封装全部委托基类派生）。
- `app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt:53`：`const val MASK_DURATION_MS: Int = 200`；`:50` `const val RISE_DURATION_MS: Int = 250`（半屏离场两段时长同源常数）。
- `app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt:133`：`if (progress.value == 0f && !visible) return`（组件内部保留帧 / 收束条件，离场段子树在此之前保留组合）。

## 二、4 条核对锚点（基线 `780ec90`（`9e43760^`）→ 现役 HEAD `fcb0116`）

> 缩写：`AT=app/src/main/java/io/legado/app/ui/autoTask`。

1. `AT/AutoTaskEditScreen.kt` 帮助半屏：基线 `:151` `InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {`；现役 `:64` 控制器 `helpExit`、`:66` 闸 + `open()`、`:159` `HalfSheetExitHost`（`onFinished = { onDismissHelp() }`）、`:161` `visible = helpExit.visible`、`:162` `beginExit()`。
2. `AT/AutoTaskScreen.kt` 帮助半屏：基线 `:168` `InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {`；现役 `:96` 控制器 `helpExit`、`:99` 闸、`:184` host（`onFinished = { onDismissHelp() }`）、`:186` 派生 visible、`:187` `beginExit()`。
3. `AT/AutoTaskScreen.kt` 运行日志半屏（私有 `LogSheet`）：基线 `:403` `InkHalfSheet(visible = true, onDismiss = onDismiss, title = task.name, content = {`；现役 `:97` 控制器 `logExit`、`:102` 闸、`:159` host（`onFinished = onDismissLog`）、`:162` 派生 visible、`:164` `beginExit()`；`LogSheet` `:428` 可见形参、`:433` 透传 `visible = visible`；清除日志按钮 `:445` `{ onClear(); onDismiss() }`。
4. `AT/ImportAutoTaskDialog.kt` JSON 预览半屏：基线 `:98` `InkHalfSheet(visible = true, onDismiss = { jsonTarget = null }, title = task.name, content = {`；现役 `:86` 控制器 `jsonExit`、`:88` 闸、`:107` host（`onFinished = { jsonTarget = null }`）、`:109` 派生 visible、`:110` `beginExit()`。

## 三、口径锁（不许改）

- 阶段枚举三态 `OPEN / EXITING / REMOVED` 定义唯一处 `SheetExitPhaseGate.kt:12`；禁造第二份阶段机、第二份守卫类、第二份迁移规则。
- 派生 `visible / isComposed / canOpen` 只许来自 B30 基类一处；config `SheetExitController` 与 autoTask 复用链路只做委托转发，不内联阶段比较。
- 两段式关闭：dismiss 先走 `beginExit()` 进离场段（派生 visible=false、子树保留），`HalfSheetExitHost` 按遮罩 200ms + 下降 250ms 等效延时后 `finishExit()` 并回调宿主清槽位摘子树；禁 dismiss 即摘子树。
- 离场段拒收新打开：打开点先过 `canOpen` 闸再 `open()`；在场段判重仍归宿主既有槽位状态（VM `state.helpText` / `state.logTarget`、local `jsonTarget`），两道闸串联不替代。
- 未打开 / 已摘除段 visible 必须为 false；离场段子树保留至收束帧，组件内部条件见 `InkHalfSheet.kt:133`。
- Compose 侧禁引入依赖 Application 初始化的配置读取（Paparazzi 截图环境无 Application）；配置项由 VM 读入 `uiState` 后透传，不在组合期直读 `AppConfig`。
- 延时只引 `HalfSheetAnchors` / `ComponentPalette` 现成常数，禁 200 / 250 / 450 魔法数硬编码。

## 四、可测判据

1. `grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt`：B26 清单 4 条口径残留 0，`exit=1`。
2. `grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt`：空命中，`exit=1`。
3. `grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt`：空命中，`exit=1`。
4. 派生 visible 现役 4 处，行号清单见 `docs/修复-B29-常量visible-autoTask.md` §4.2；控制器实例化 4 份；`.open()` 4 处业务调用全部过闸；`HalfSheetExitHost` 实例化 4 处。
5. `grep -rnE "enum class|sealed (class|interface)|ExitGate|ExitPhase|Stage" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt`：唯一命中 `AutoTaskField` 业务枚举，无离场阶段机。
6. `grep -rn "AppConfig\|appCtx" app/src/main/java/io/legado/app/ui/autoTask/ --include=*.kt`：仅 `ImportAutoTaskViewModel.kt:29` / `:138` VM 运行期 URI 读取，Compose 屏文件零命中。
7. `python3 data_scripts/kotlin-syntax-lint.py` 本批 5 个相关文件 PASS；禁跑 gradlew / kotlinc，编译与单测全绿归云端 CI。

## 五、兜底边界与交接项

- N1（不改 · 无挂载点）：域内纯 `Dialog` 弹窗群——`AutoTaskEditScreen.kt:146` 退出确认；`AutoTaskScreen.kt:378` / `:406` / `:454` / `:470` 选择栏 / 删除确认 / 批量定时 / 在线导入 URL；直挂 `androidx.compose.ui.window.Dialog`，无遮罩 / 下降动画组件可挂，保持旧直摘口径；日后包进模态 host 再补离场闸。
- N2（不改 · 整窗销毁）：`ImportAutoTaskDialog.kt:95-104` 导入成功 / 整体关闭的 `dismissAllowingStateLoss()` 为 DialogFragment 整窗销毁路径，非 JSON 预览半屏自身离场。
- F-1（已入队 · 架构耦合）：`ui/sheet/HalfSheetExitHost.kt:6` 反向 import config `SheetExitController`；待公共层下沉时与控制器 / 守卫一并泛型合并。
- F-2（已入队 · 守卫不对称）：`SheetExitController.beginExit()` 无条件置 `exitingState=true`，守卫非 OPEN 段为空操作；生产路径不可达，建议按守卫返回值置位或补断言。
- F-3（判废 · 测试 oracle 字面量，非缺陷）：`HalfSheetExitHostTest.kt:128` 期望 `450L` 字面量；生产代码无魔法数。
- F-4（待裁 · 覆盖边界）：宿主 `LaunchedEffect` 收束 → `finishExit` → 清槽位 Compose 链路无纯 JVM 单测，待 CI 出包后仪器化 / 真机复验。
- F-5（判废 · 既有边界，本单不解决）：控制器由 `remember` 持有，阶段与离场位不跨配置变更 / 进程重建存活。
- F-7（已入队 · 转对应域）：全仓其余 `visible = true` 残留归 components 红线 / config 域 Q459 / reader 状态类内部赋值 / 注释，均不在 autoTask 本单口径内。
