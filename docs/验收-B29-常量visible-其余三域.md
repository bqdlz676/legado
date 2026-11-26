> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 1 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B29-域外常量visible-config.md

# 验收-B29：autoTask / dict / replace 三域浮层可见性常量导致离场动画跳过（独立验收）

> **口径已失效（2026-11-26，Q-280）**：旧关于页 `AboutActivity` 已整体删除，详见 `docs/修复-删除关于页.md`；本段保留为历史记录，勿据以施工。

## 〇、验收元信息

- 验收对象：`docs/修复-B29-常量visible-其余三域.md`（被验方自述，已逐字通读，重点核对 §6.3「纯 Dialog 群留档不改」N1–N4、§12 并行 WIP 说明、§11 未验证项）。
- 清单来源：`docs/修复-B26-常量visible接线.md` §6.3 第 259–306 行，autoTask 4 条 / dict 2 条 / replace 3 条，合计 9 条。
- 被验提交：`9e43760`（代码，"修B29:autoTask/dict/replace三域浮层两段式收口(HalfSheetExitHost复用config控制器,8断言)"）；报告入库提交 `6f8b054`。验收基线：`9e43760^`（即 `780ec90`）。HEAD `f3136a1` 已含 `9e43760`（ancestor=yes），且该提交之后无任何提交再改动三域 / `ui/sheet` 路径，现役行号证据有效。
- 先例对照：B29 config 域 `ui/config/settings/SheetExitController.kt`；B26 守卫 `ui/book/read/NoteDialogExitGate.kt`；B21 阶段枚举 `ui/book/read/ink/ReaderSheetExitGate.kt`（`ReaderSheetExitPhase`）。
- 验收方式：只读代码、测试与 git 历史，静态逐条回证；每条结论附「文件:行」证据与本验收真跑的 grep 命令及原样输出。未改源码 / 测试 / 既有报告 / 门禁脚本；未执行 git add / commit / push；未本地编译、未跑单测（沙箱无 gradle / JDK21 / SDK，编译与 CI 结果标「未验证，等 CI」）。
- 范围隔离：工作树当前干净（`git status --short` 无输出）；`ui/book/source/**` 由并行代理收口、`ui/card` 与 bookshelf 既有 WIP 不属本单，不计入本单问题，本报告也未改动。
- 总体结论：**静态层九条判据全部 PASS，无 FAIL 项，判「通过」。清单 9=9 一一对应，差额 0；三域三类常量残留均为 0，全仓命名参数仅剩红线 `HandleFileSheet.kt:48` 一条；`HalfSheetExitHost` 复用 config 控制器，未造第二份阶段枚举，无循环依赖；离场时长同源组件 token，生产代码无魔法数；抽查 4 个 Sheet 的 dismiss 均走 `beginExit()`、可见性派生、收束回调清槽位；两个 ViewModel 移除直清后确有宿主收束回调负责清槽位，未发现槽位泄漏；9 个打开点全部先过 `canOpen` 闸再 `open()`；8 例断言覆盖控制器全部分支；提交边界只触达 autoTask / dict / replace / `ui/sheet` 与其测试。附 3 项低风险 / 观察（F1 共享宿主包反向引用 config 控制器、F2 控制器 `beginExit` 的 exitingState 置位与守卫空操作不完全对称、F3 测试期望字面量 450L）与 2 项报告已自行登记的边界（F4 宿主 Compose 链路无纯 JVM 断言、F5 离场段重建不跨存活）。编译是否通过、8 例单测是否全绿、真机帧对齐与离场段触摸截获，未验证，等云端 CI 出包后复验。**

## 一、判据 1：清单对账（B26 三域 9 条与改后位置一一对应，9=9，差额 0）—— PASS

判据要求：自己 grep 核对 B26 清单三域 9 条基线行号与改后现役位置一一对应，无遗漏无多改；`DictDialog.kt:211` 派生 visible 非常量，不计缺陷。

1. B26 清单原文（`docs/修复-B26-常量visible接线.md` §6.3）共 9 条：autoTask 域第 261–264 行 4 条，dict 域第 299–300 行 2 条，replace 域第 304–306 行 3 条。

2. 基线复核（验收基线 `780ec90`，确认 9 条基线行确为 `InkHalfSheet(visible = true, ...)`）。命令：

```
for spec in "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt:151" "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt:168" "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt:403" "app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt:98" "app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt:377" "app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt:416" "app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt:198" "app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt:500" "app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt:181"; do f=$(echo "$spec"|cut -d: -f1); n=$(echo "$spec"|cut -d: -f2); echo -n "BASE $spec => "; git show "780ec90:$f" | sed -n "${n}p" | sed 's/^ *//'; done
```

原样输出（9 行，均为 `visible = true`）：

```
BASE .../autoTask/AutoTaskEditScreen.kt:151 => InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {
BASE .../autoTask/AutoTaskScreen.kt:168 => InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {
BASE .../autoTask/AutoTaskScreen.kt:403 => InkHalfSheet(visible = true, onDismiss = onDismiss, title = task.name, content = {
BASE .../autoTask/ImportAutoTaskDialog.kt:98 => InkHalfSheet(visible = true, onDismiss = { jsonTarget = null }, title = task.name, content = {
BASE .../dict/rule/DictRuleScreen.kt:377 => InkHalfSheet(visible = true, onDismiss = onDismiss, title = if (creating) "新建词典规则" else "编辑词典规则", content = {
BASE .../dict/rule/DictRuleScreen.kt:416 => InkHalfSheet(visible = true, onDismiss = onDismiss, title = "帮助", content = {
BASE .../replace/ReplaceRuleScreen.kt:198 => InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {
BASE .../replace/ReplaceRuleScreen.kt:500 => InkHalfSheet(visible = true, onDismiss = onDismiss, title = "分组管理", content = {
BASE .../replace/edit/ReplaceEditScreen.kt:181 => InkHalfSheet(visible = true, onDismiss = onDismissHelp, title = "帮助", content = {
```

3. 现役三域 `InkHalfSheet(` 调用点 grep。命令：

```
grep -rn "InkHalfSheet(" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出（10 行，9 条本单缺陷位 + 1 条派生位）：

```
.../autoTask/AutoTaskEditScreen.kt:160:            InkHalfSheet(
.../autoTask/AutoTaskScreen.kt:184:            InkHalfSheet(
.../autoTask/AutoTaskScreen.kt:430:    InkHalfSheet(visible = visible, onDismiss = onDismiss, title = task.name, content = {
.../autoTask/ImportAutoTaskDialog.kt:107:                        InkHalfSheet(
.../dict/DictDialog.kt:210:        InkHalfSheet(
.../dict/rule/DictRuleScreen.kt:394:    InkHalfSheet(visible = visible, onDismiss = onDismiss, title = if (creating) "新建词典规则" else "编辑词典规则", content = {
.../dict/rule/DictRuleScreen.kt:434:    InkHalfSheet(visible = visible, onDismiss = onDismiss, title = "帮助", content = {
.../replace/ReplaceRuleScreen.kt:214:            InkHalfSheet(
.../replace/ReplaceRuleScreen.kt:523:    InkHalfSheet(visible = visible, onDismiss = onDismiss, title = "分组管理", content = {
.../replace/edit/ReplaceEditScreen.kt:190:            InkHalfSheet(
```

4. 逐条对账（基线 → 现役，文件一一对应；行号随两段式接线漂移，属预期）：

- 第 1 条：`AutoTaskEditScreen.kt` 基线 :151 → 现役 :160（帮助半屏）。
- 第 2 条：`AutoTaskScreen.kt` 基线 :168 → 现役 :184（帮助半屏）。
- 第 3 条：`AutoTaskScreen.kt` 基线 :403 → 现役 :430（`LogSheet` 日志半屏）。
- 第 4 条：`ImportAutoTaskDialog.kt` 基线 :98 → 现役 :107（DialogFragment 壳内 JSON 预览）。
- 第 5 条：`DictRuleScreen.kt` 基线 :377 → 现役 :394（`EditSheet` 编辑半屏）。
- 第 6 条：`DictRuleScreen.kt` 基线 :416 → 现役 :434（`HelpSheet` 帮助半屏）。
- 第 7 条：`ReplaceRuleScreen.kt` 基线 :198 → 现役 :214（帮助半屏）。
- 第 8 条：`ReplaceRuleScreen.kt` 基线 :500 → 现役 :523（`GroupManageSheet` 分组管理）。
- 第 9 条：`ReplaceEditScreen.kt` 基线 :181 → 现役 :190（帮助半屏）。

5. 派生非常量位留档：`ui/dict/DictDialog.kt:210-212`。命令 `sed -n '208,213p' .../dict/DictDialog.kt` 原样输出：

```
        // 大图查看：复用统一半屏 Sheet（独立窗口模式），不新造弹层。
        InkHalfSheet(
            visible = viewerSrc != null,
            onDismiss = { viewerSrc = null },
            dialog = true,
```

`visible = viewerSrc != null` 为派生布尔（图片查看器槽位非空判定），非常量 true；子树离场路径由派生可见性驱动，与报告 §6.3 N1 口径一致，不计缺陷。

6. 对账结论：9 条基线全部在现役找到对应改造位，无多出、无遗漏；差额 0 条。

## 二、判据 2：三域常量残留清零（命名参数 / 位置参数 / 多行裸 `true,` 均 0；全仓仅红线 1 条）—— PASS

判据要求：`visible = true`、`Sheet(true, …)`、多行裸 `true,` 在三域均为 0；全仓残留仅红线 `ui/components/HandleFileSheet.kt:48`。

1. 三域命名参数 `visible = true`。命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`。

2. 三域位置参数 `Sheet(true, ...)` / `Host(true, ...)`。命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`。

3. 三域多行位置参数（行首裸 `true,`）。命令：

```
grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`。

4. 全仓命名参数残留。命令：

```
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt
```

原样输出（唯一命中，即 components 红线）：

```
app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt:48:    InkHalfSheet(visible = true, onDismiss = onCancel, title = title, content = {
```

5. 全仓无空格写法 `visible=true`。命令 `grep -rn "visible=true" app/src/main --include=*.kt`：空，`exit=1`。

6. 结论：三域三类形式残留均为 0；全仓残留 1 条，即红线 `HandleFileSheet.kt:48`，与报告 §7.4 一致。该文件属 components 契约冻结范围，本单零改动（边界见判据 9）。

## 三、判据 3：宿主无第二源（复用 `SheetExitController`，依赖方向无循环，倒挂风险低）—— PASS（附低风险架构观察 F1）

判据要求：`HalfSheetExitHost.kt` 是否 import 复用 `SheetExitController`（import 行号 `:6`），无第二份阶段枚举；判断 config→ui/sheet 依赖方向是否倒挂。

1. 宿主 import 实读（`ui/sheet/HalfSheetExitHost.kt:1-7`）：

```
1  package io.legado.app.ui.sheet
3  import androidx.compose.runtime.Composable
4  import androidx.compose.runtime.LaunchedEffect
5  import io.legado.app.ui.components.HalfSheetAnchors
6  import io.legado.app.ui.config.settings.SheetExitController
7  import kotlinx.coroutines.delay
```

控制器类型直接复用 B29 config 域 `SheetExitController`，`:6` 行号与报告 §5 一致。

2. 第二份枚举排查。命令：

```
grep -rn "enum class\|Phase\|Phase(" app/src/main/java/io/legado/app/ui/sheet/
```

原样输出仅 1 行注释提及：

```
.../ui/sheet/HalfSheetExitHost.kt:14: * 该守卫复用 B21 `ReaderSheetExitPhase`（OPEN / EXITING / REMOVED）；本宿主不另造枚举、
```

`ui/sheet` 目录仅 `HalfSheetExitHost.kt` 一个文件，无 `enum class` 定义，未复制迁移规则。阶段机链路保持 B21 `ReaderSheetExitPhase` → B26 `NoteDialogExitGate` → B29 `SheetExitController` 单线复用。

3. 循环依赖排查。命令：

```
grep -rn "ui.sheet" app/src/main/java/io/legado/app/ui/config/
```

原样输出：空；`exit=1`。config 域不反向 import `ui/sheet`，包级无循环依赖。

4. 依赖方向评估。现役方向为 `ui/sheet`（本单新建的泛化宿主包）→ `ui/config/settings`（控制器 Compose 包装所在包），即共享形态的宿主包引用了特性域包内的控制器类型。判读：

- 本仓库为单一 `app` Gradle 模块，包间无编译期层强制，该引用不会造成编译断裂；且 config 不反向依赖，无环。
- 跨域复用 config 控制器在此仓库已有先例：命令 `grep -rln "import io.legado.app.ui.config.settings" app/src/main/java/io/legado/app/ui/` 显示 `ui/about/AboutActivity.kt`、`ui/autoTask/*` 三屏、`ui/dict/rule/DictRuleScreen.kt`、`ui/replace/*` 两屏等既有屏已直接 import config 的 `rememberSheetExitController()`，本单沿用既有约定，未新开一种反向接法。
- 残留 smell：`ui/sheet` 命名上是通用半屏宿主层，却把控制器类型绑定在 config 特性包；若日后拆分 Gradle 模块或抽出公共 sheet 层，该引用会成为需下沉的耦合点。风险等级：**低**（单模块内无编译风险、无循环、有先例）。处置建议：与报告 §9.4 / §12 第三条一致，待第四个消费方出现时把控制器 / 守卫下沉到 common 包并做泛型合并，本单不提前抽象。

## 四、判据 4：时长同源（`MASK_DURATION_MS` 200 / `RISE_DURATION_MS` 250 真实引用，生产代码无魔法数字）—— PASS（附观察 F3）

判据要求：两段时长常量定义行与引用行可回源，无魔法数字。

1. 常量定义。命令 `grep -rn "object HalfSheetAnchors\|MASK_DURATION_MS\|RISE_DURATION_MS" app/src/main/java/io/legado/app/ui/components/` 原样输出：

```
.../components/HalfSheetModel.kt:20:object HalfSheetAnchors {
.../components/HalfSheetModel.kt:50:    const val RISE_DURATION_MS: Int = 250
.../components/HalfSheetModel.kt:53:    const val MASK_DURATION_MS: Int = 200
```

2. 宿主引用。`HalfSheetExitHost.kt:52`：

```
val exitMs: Long = (HalfSheetAnchors.MASK_DURATION_MS + HalfSheetAnchors.RISE_DURATION_MS).toLong()
```

延时调用在 `HalfSheetExitHost.kt:36`：`delay(HalfSheetExitDurations.exitMs)`，取值经 `exitMs` 派生，未在宿主内写死毫秒数。

3. 组件离场分支同源对照。`InkHalfSheet.kt:120-129` 离场 `else` 分支：

```
124  tween(HalfSheetAnchors.MASK_DURATION_MS, easing = ComponentTokens.maskEasing),
128  tween(HalfSheetAnchors.RISE_DURATION_MS, easing = ComponentTokens.cardEnterEasing),
```

遮罩回亮 200ms、Sheet 下降 250ms，与宿主延时取值同一对 token；开场分支 :115/:119 亦引用同一常量。

4. 魔法数字排查。命令：

```
grep -nE "\b(200|250|450)\b" app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt
```

生产宿主文件无命中；测试文件仅 :128 一处期望字面量 `assertEquals(450L, HalfSheetExitDurations.exitMs)`（详见观察 F3），生产代码无魔法数。

## 五、判据 5：离场链路抽查 4 个 Sheet（dismiss 走 `beginExit()`、可见性派生、收束回调清槽位）—— PASS

判据要求：抽查 autoTask 帮助 / 日志、dict 编辑、replace 分组管理 4 个 Sheet，确认三段链路完整。

1. autoTask 帮助半屏（`AutoTaskScreen.kt`）：宿主 `state.helpText?.let` :182；收束宿主 :183 `HalfSheetExitHost(helpExit, onFinished = { onDismissHelp() })`；派生可见性 :185 `visible = helpExit.visible`；dismiss :186 `onDismiss = { helpExit.beginExit() }`。链路完整。

2. autoTask 日志半屏（`AutoTaskScreen.kt` + 私有 `LogSheet`）：宿主 `state.logTarget?.let` :157；收束宿主 :158 `HalfSheetExitHost(logExit, onFinished = onDismissLog)`；派生可见性 :161 `visible = logExit.visible`；dismiss :163 `onDismiss = { logExit.beginExit() }`。`LogSheet` 签名 :423-428 新增 `visible: Boolean` 形参（:425），:430 `InkHalfSheet(visible = visible, ...)` 透传。「清除日志」按钮 :442 `{ onClear(); onDismiss() }`：清库副作用在决策时刻，随后 `onDismiss()` 进离场段。链路完整。

3. dict 编辑半屏（`DictRuleScreen.kt` + 私有 `EditSheet`）：宿主 `state.editTarget?.let` :148；收束宿主 :149 `HalfSheetExitHost(editExit, onFinished = onDismissEdit)`；派生可见性 :153 `visible = editExit.visible`；dismiss :155 `onDismiss = { editExit.beginExit() }`。`EditSheet` 签名 :377-384 新增 `visible: Boolean`（:380），:394 `InkHalfSheet(visible = visible, ...)`。保存按钮 :402-407 先 `onSave(...)` 入库、再 `onDismiss()` 进离场。链路完整。

4. replace 分组管理半屏（`ReplaceRuleScreen.kt` + 私有 `GroupManageSheet`）：宿主 `if (state.groupManageOpen)` :178；收束宿主 :179 `HalfSheetExitHost(groupExit, onFinished = onCloseGroupManage)`；派生可见性 :182 `visible = groupExit.visible`；dismiss :187 `onDismiss = { groupExit.beginExit() }`。`GroupManageSheet` 签名 :513-519 新增 `visible: Boolean`（:515），:523 `InkHalfSheet(visible = visible, ...)`。新建 / 重命名 / 删除动作不关闭 Sheet（列表原地刷新，旧口径保留），遮罩 / 下拉 / 返回关闭走两段式。链路完整。

5. 其余 5 个帮助 / 预览半屏同形态回证：`AutoTaskEditScreen.kt` :159 宿主 / :161 派生 / :162 beginExit；`ImportAutoTaskDialog.kt` :106 宿主 `onFinished = { jsonTarget = null }` / :108 派生 / :109 beginExit；`DictRuleScreen.kt` 帮助 :175 宿主 / :176 派生 + beginExit；`ReplaceRuleScreen.kt` 帮助 :213 宿主 / :215 派生 / :216 beginExit；`ReplaceEditScreen.kt` :189 宿主 / :191 派生 / :192 beginExit。

## 六、判据 6：槽位泄漏（高风险）—— PASS，未发现泄漏

判据要求：逐条读两个 ViewModel 改动，确认移除内部直清后确有宿主收束回调清槽位；若无人清则判 FAIL。

1. `AutoTaskListViewModel.kt` 日志槽位。现役实读：

- :107 `fun showLog(rule: AutoTaskUi) = _uiState.update { it.copy(logTarget = rule) }`（开槽）。
- :108 `fun dismissLog() = _uiState.update { it.copy(logTarget = null) }`（清槽，仅供收束回调调用）。
- :112-114 `fun clearLog(id: String) { execute { AutoTask.clearRunLog(id) } }`，函数体已无 `logTarget = null`。

提交 diff（`git show 9e43760 -- .../AutoTaskListViewModel.kt`）原样关键段：

```
     fun clearLog(id: String) {
         execute { AutoTask.clearRunLog(id) }
-        _uiState.update { it.copy(logTarget = null) }
     }
```

2. 日志槽位收束责任链回证：`AutoTaskScreen.kt:158` 宿主 `onFinished = onDismissLog`；`AutoTaskActivity.kt:97` `onDismissLog = viewModel::dismissLog`。即离场收束 → Activity 回调 → `vm.dismissLog()` 清 `logTarget`，槽位有人负责摘除。

3. `DictRuleListViewModel.kt` 编辑槽位。现役实读：

- :158-162 `openCreate()` 置 `editTarget` + `creating=true`；:164-166 `openEdit(rule)` 置槽。
- :168-170 `fun dismissEdit() { _uiState.update { it.copy(editTarget = null, creating = false) } }`（清槽 + 复位 creating）。
- :172-187 `save(rule)`：入库 `appDb.dictRuleDao.insert(...)` 与失败 toast，函数体已无 `dismissEdit()` 调用。

提交 diff 原样关键段：

```
         }.onError { error ->
             context.toastOnUi("保存字典规则出错\n${error.localizedMessage}")
         }
-        dismissEdit()
+        // B29：入库副作用在决策时刻执行；槽位摘除交 HalfSheetExitHost 离场收束回调
+        // （dismissEdit），不再在此直清 editTarget 跳过离场动画。
```

4. 编辑槽位收束责任链回证：`DictRuleScreen.kt:149` 宿主 `onFinished = onDismissEdit`；`DictRuleActivity.kt:86` `onDismissEdit = viewModel::dismissEdit`。收束后 `dismissEdit()` 清 `editTarget` 并复位 `creating`，槽位有人负责摘除。

5. 清槽入口唯一性排查。命令：

```
grep -rn "logTarget = null\|editTarget = null\|dismissEdit()\|dismissLog()" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/rule/ --include=*.kt
```

原样输出：

```
.../autoTask/AutoTaskListViewModel.kt:108:    fun dismissLog() = _uiState.update { it.copy(logTarget = null) }
.../dict/rule/DictRuleListViewModel.kt:168:    fun dismissEdit() {
.../dict/rule/DictRuleListViewModel.kt:169:        _uiState.update { it.copy(editTarget = null, creating = false) }
```

直清只存在于 `dismissLog()` / `dismissEdit()` 两个收束回调方法内；`clearLog` / `save` 不再触槽。命令 `grep -n "groupManageOpen\|fun addGroup\|fun renameGroup\|fun deleteGroup\|fun closeGroupManage" .../replace/ReplaceRuleListViewModel.kt` 显示分组增 / 改 / 删方法均不触碰 `groupManageOpen`，仅 `closeGroupManage()`（:256）清槽，且只接 `ReplaceRuleActivity.kt:106` 宿主 `onCloseGroupManage`。

6. 结论：两处 VM 移除直清后，槽位摘除均由 `HalfSheetExitHost` 收束回调经 Activity 转发到 VM 清槽方法闭环；未发现「无人清槽」导致的槽位泄漏或下次打不开问题。

## 七、判据 7：打开闸（每个打开点先过 `canOpen` 再 `open()`；程序化入口同样过闸）—— PASS

判据要求：每个打开点先过 `canOpen` 再 `open()`，给至少 5 处行号；程序化入口同样过闸。

1. 全部 `.open()` 调用点排查。命令：

```
grep -rnE "\.open\(\)" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/rule/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出 9 行，每行均在同一 `if` 条件内先判 `canOpen`：

```
.../autoTask/AutoTaskEditScreen.kt:66:        if (state.helpText != null && helpExit.canOpen) helpExit.open()
.../autoTask/AutoTaskScreen.kt:98:        if (state.helpText != null && helpExit.canOpen) helpExit.open()
.../autoTask/AutoTaskScreen.kt:101:        if (state.logTarget != null && logExit.canOpen) logExit.open()
.../autoTask/ImportAutoTaskDialog.kt:87:                    if (jsonTarget != null && jsonExit.canOpen) jsonExit.open()
.../dict/rule/DictRuleScreen.kt:90:        if (state.editTarget != null && editExit.canOpen) editExit.open()
.../dict/rule/DictRuleScreen.kt:93:        if (state.helpText != null && helpExit.canOpen) helpExit.open()
.../replace/ReplaceRuleScreen.kt:109:        if (state.groupManageOpen && groupExit.canOpen) groupExit.open()
.../replace/ReplaceRuleScreen.kt:112:        if (state.helpText != null && helpExit.canOpen) helpExit.open()
.../replace/edit/ReplaceEditScreen.kt:63:        if (state.helpText != null && helpExit.canOpen) helpExit.open()
```

2. 9 处全部位于 `LaunchedEffect` 程序化入口（:65-67 / :97-102 / :86-88 / :89-94 / :108-113 / :62-64），闸位写法统一为「槽位非空 `&&` 控制器 `canOpen`」后 `open()`；命令 `grep -rn "canOpen" ...` 复核，不存在未过闸的裸 `open()`。

3. 闸语义回源：`NoteDialogExitGate.kt:60-61` `canOpen = phase != EXITING`，离场段拒收；在场段重复打开的判重仍归宿主既有槽位状态，两道闸串联，口径同 B26 / B29。

4. 结论：9 个打开点（≥5）全部先过闸再打开，程序化 `LaunchedEffect` 入口同样过闸，无旁路。

## 八、判据 8：断言映射（8 例逐条对应判据，缺覆盖判 FAIL）—— PASS（附边界 F4）

判据要求：8 例逐条对应判据，给测试文件行号；缺覆盖判 FAIL。

1. 断言计数。命令 `grep -c "@Test" app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt` 原样输出：`8`。

2. 测试名禁字符复核。命令：

```
grep -nE '^\s*fun `[^`]*[.:\[\]/;<>][^`]*`' app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt
```

原样输出：空；`exit=1`（8 个反引号测试名内不含 `. : [ ] / ; < >`）。

3. 8 例与控制器分支逐条映射（`HalfSheetExitHostTest.kt`）：

- 例 1 :32-40 `初始为摘除态 不可见 子树不在 可接受新打开`：初始 `visible=false`(:36) / `isComposed=false`(:37) / `canOpen=true`(:38) / `isExiting=false`(:39)。对应 REMOVED 初始态。
- 例 2 :42-52 `open 后在场态 可见 子树保留 可开`：`open()` 后 `visible=true`(:48) / `isComposed=true`(:49) / `canOpen=true`(:50) / `isExiting=false`(:51)。对应 OPEN 在场态。
- 例 3 :54-65 `关闭瞬间子树仍在且可见性已翻 false`：`beginExit()` 后 `isComposed=true`(:62) / `visible=false`(:63) / `isExiting=true`(:64)。对应 EXITING 子树保留、可见性翻 false。
- 例 4 :67-78 `离场段拒收重复打开`：EXITING 段 `canOpen=false`(:73)；重复 `beginExit()` 不改阶段，`isComposed=true`(:76) / `visible=false`(:77)。对应离场拒收闸与幂等 beginExit。
- 例 5 :80-92 `动画收束后摘除子树`：`finishExit()` 后 `isComposed=false`(:88) / `visible=false`(:89) / `isExiting=false`(:90) / `canOpen=true`(:91)。对应 REMOVED 收束摘除。
- 例 6 :94-106 `空操作兜底 非在场开始离场不动状态 非离场收尾不迁移`：REMOVED 段 `beginExit()` 空操作(:98-100)；OPEN 段 `finishExit()` 空操作(:102-105)。对应守卫非目标阶段空操作分支。
- 例 7 :108-120 `收束后重开回到在场态并可见`：REMOVED 后再次 `open()` 回到 `visible=true`(:117) / `canOpen=true`(:118) / `isComposed=true`(:119)。对应收束后重开。
- 例 8 :122-129 `半屏离场时长为遮罩与下降两段之和`：`:125` 断言 `exitMs == MASK_DURATION_MS + RISE_DURATION_MS`；`:128` 断言 `exitMs == 450L`。对应时长派生同源。

4. 分支覆盖核对：初始态 / 在场态 / 离场段 / 拒收闸 / 收束摘除 / 空操作兜底 / 重开 / 时长派生，控制器每条迁移分支至少 1 例；与报告 §8 清单一致，无缺覆盖。

5. 覆盖边界（已在报告 §11 披露，记录为 F4）：8 例全部落在 `SheetExitController` / `NoteDialogExitGate` 状态机；`HalfSheetExitHost` 内 `LaunchedEffect(isExiting) → delay(exitMs) → finishExit() → onFinished()` 这条 Compose 收束链路无纯 JVM 断言，宿主回调清槽位仅靠本报告判据 5 / 6 的静态走查确认。

## 九、判据 9：边界未越（`9e43760` 只含三域 + `ui/sheet` 与其测试；红线目录零 diff）—— PASS

判据要求：`9e43760` 只含 autoTask / dict / replace / `ui/sheet` 与其测试；`ui/book` source 域、config、components、reader、card 零 diff，给 `git show --stat` 输出。

1. 提交统计。命令 `git show --stat 9e43760` 原样输出：

```
 .../legado/app/ui/autoTask/AutoTaskEditScreen.kt   |  41 ++++---
 .../app/ui/autoTask/AutoTaskListViewModel.kt       |   3 +-
 .../io/legado/app/ui/autoTask/AutoTaskScreen.kt    |  69 +++++++----
 .../legado/app/ui/autoTask/ImportAutoTaskDialog.kt |  41 ++++---
 .../app/ui/dict/rule/DictRuleListViewModel.kt      |   3 +-
 .../io/legado/app/ui/dict/rule/DictRuleScreen.kt   |  42 +++++--
 .../app/ui/replace/ReplaceRuleScreen.kt            |  67 +++++++----
 .../app/ui/replace/edit/ReplaceEditScreen.kt       |  41 ++++---
 .../io/legado/app/ui/sheet/HalfSheetExitHost.kt    |  53 +++++++++
 .../legado/app/ui/sheet/HalfSheetExitHostTest.kt   | 130 +++++++++++++++++++++
 10 files changed, 394 insertions(+), 96 deletions(-)
```

2. 全量文件清单。命令 `git show --name-only --format="" 9e43760` 原样输出 10 行：

```
app/src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt
app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt
app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt
app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt
app/src/main/java/io/legado/app/ui/dict/rule/DictRuleListViewModel.kt
app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt
app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt
app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt
app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt
app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt
```

3. 红线目录 diff 排查。命令：

```
git show --name-only --format="" 9e43760 | grep -E "ui/book/|ui/config/|ui/components/|ui/card/|reader|ui/main/|ui/book/source/"
```

原样输出：空；`redline-exit=1`。`ui/book/source/**`、`ui/config/**`、`ui/components/**`、`ui/card/**`、reader 域、`ui/main/**` 在本提交零 diff。

4. 报告提交 `6f8b054` 仅含本单自述文档一个文件（`git show --name-only --format="" 6f8b054` 输出一条 `docs/修复-B29-常量visible-其余三域.md`），未夹带源码或测试改动。

5. 括号配平粗检（10 个文件，剥离字符串 / 注释后计数）：`{}` 与 `()` 全部相等；测试文件 9 `{` 9 `}`、64 `(` 64 `)`，与报告 §7.10 一致。三域无 `InkBottomSheet` 残留（`grep -rn "InkBottomSheet" .../autoTask/ .../dict/ .../replace/` 空，exit=1）。

## 十、新发现缺陷 / 观察项（逐条编号）

- F1（低风险，架构观察，非 FAIL）：`ui/sheet/HalfSheetExitHost.kt:6` 作为泛化半屏宿主包，反向 import `ui/config/settings.SheetExitController`。单模块内无编译断裂、config 不反向依赖、且 autoTask / dict / replace / about 既有屏已有跨域 import config 控制器的先例，故不判倒挂缺陷；但通用宿主层绑定特性包类型是未来拆模块 / 抽公共层时需下沉的耦合点。建议随第四个消费方出现，与控制器 / 守卫一并下沉 common 包并泛型合并（报告 §9.4 / §12 已登记同方向议题）。

- F2（低风险，守卫不对称观察，生产路径不可达）：`SheetExitController.beginExit()`（`SheetExitController.kt:57-60`）无条件置 `exitingState.value = true`，而其包装的 `NoteDialogExitGate.beginExit()`（`NoteDialogExitGate.kt:34-37`）在非 OPEN 段为空操作、不迁移阶段。若在 REMOVED 段误调 `beginExit`，守卫阶段不变但 `exitingState` 仍被置 true，宿主会延时后走 `finishExit()`（守卫空操作）并回调 `onFinished` 清槽。生产 dismiss 仅在在场 / 离场段可达，该分支不可触发；测试例 6 只覆盖了守卫侧空操作，未覆盖控制器侧 `exitingState` 残留。建议后续在 `beginExit` 内按守卫阶段结果决定是否置 `exitingState`，或补一条对应断言。

- F3（观察，非缺陷）：`HalfSheetExitHostTest.kt:128` 硬编码期望字面量 `450L`。同例 :125 已做常量派生断言，:128 属锁定期望值的 oracle 字面量；若组件两段时长常量调整，该断言会同步失败（期望行为）。生产宿主代码无魔法数。

- F4（覆盖边界，报告 §11 已披露）：宿主 `LaunchedEffect` 延时收束 → `finishExit` → `onFinished` 清槽位这条 Compose 链路无纯 JVM 单测；本报告对该链路的确认来自静态走查（判据 5 / 6），CI 出包后需真机 / 仪器化复验。

- F5（既有边界，报告 §10 已披露）：离场约 450ms 内发生旋转 / 进程重建，控制器阶段与离场位不跨重建存活；VM 槽位仍在，重组时 `LaunchedEffect` 会再次 `open()` 重开 Sheet。与 B20 / B21 / B26 / B29 同一边界，非本单引入。

## 十一、未验证项（等云端 CI / 真机）

- 编译与单测：本地禁编译（沙箱无 gradle / JDK21 / SDK），未跑 `./gradlew` / `kotlinc`；8 例断言是否全绿、新增 import 与私有 Sheet `visible` 形参变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段窗口触摸截获，未在真机走查。
- 动画时序：摘子树时机为代码静态推断（延时依据 = 组件两段顺序动画时长 200+250ms），实际收束帧是否干净待 CI 出包后真机确认。
- `DictRuleListViewModel.save` 入库失败分支：失败后仍随离场动画收束再关，错误 toast 口径不变，失败后表单草稿保留到收束，真机观感待确认（报告 §11 已登记）。
- 域外同形态项：source 域、components 红线 `HandleFileSheet.kt:48` 的实际影响面不在本单核验范围；本单清单判定基于源码条件渲染形态静态推断。
- 纯 `Dialog` 弹窗群留档（报告 §6.3 N3）：逐条复核 `AutoTaskEditScreen.kt:146`、`AutoTaskScreen.kt:375/:403/:451/:467`、`DictRuleScreen.kt:315/:348/:419`、`ReplaceRuleScreen.kt:459/:493/:587/:610`、`ReplaceEditScreen.kt:173`，均直挂 `androidx.compose.ui.window.Dialog`，未包 `InkHalfSheet` / `InkBottomSheet`，无组件内离场动画可挂，保持旧直摘口径；若日后包进模态 host，需另派单补离场闸。
- DialogFragment 壳整窗销毁路径（`ImportAutoTaskDialog.kt:94-103` 导入成功 / 整体关闭的 `dismissAllowingStateLoss()`）为整窗销毁，非 JSON 预览半屏自身离场，保持旧口径（报告 §6.3 N2），本验收认可该留档。

## 十二、验收结论

- 九条判据：1 清单对账 PASS；2 三域清零 PASS；3 宿主复用与依赖方向 PASS（附 F1）；4 时长同源 PASS（附 F3）；5 离场链路抽查 PASS；6 槽位泄漏核查 PASS；7 打开闸 PASS；8 断言映射 PASS（附 F4）；9 边界未越 PASS。
- 无 FAIL 级缺陷；高风险项（两 VM 移除直清后的槽位责任、跨包依赖循环风险、pure Dialog 挂点）三项均已回证闭环。
- 总体判定：**通过（静态层）**。编译结果、8 例单测全绿与否、真机帧对齐与触摸截获，留待云端 CI 出包后复验；复验前不视为最终出包验收通过。
