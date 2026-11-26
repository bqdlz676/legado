> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B29-域外常量visible-config.md

# 修复-B29：autoTask / dict / replace 三域浮层可见性传常量 true 导致离场动画被跳过（两段式关闭收口）

> 日期：2026-11-26 ｜ 执行代理：B29 写码专单（其余三域：autoTask 4 / dict 2 / replace 3）｜ 基线：本单未执行 git add / commit / push（沙箱禁提交）。
> 清单来源：`docs/修复-B26-常量visible接线.md` §6.3「autoTask 域 4 条 / dict 域 2 条 / replace 域 3 条」；`docs/修复-B29-常量visible-config.md` §3.1 残留统计段。
> 范式来源：B21 半屏离场两段式关闭；B26 `NoteDialogExitGate`；B29 config 域 `SheetExitController` / `rememberSheetExitController()` / `SheetExitHost`。
> E3 结果：✓ 已落码待 CI——grep 回证每处改动真写入；本地禁编译 / 禁跑测（沙箱无 gradle / JDK21 / SDK），编译与单测结果一律标「未验证，等 CI」。
> 仓库：`bqdlz676/legado`；工作树改动，未做任何 `git add / commit / push`。

## 1. 缺陷原貌与本单结论

缺陷原貌（对照 HEAD 基线逐条复核现役代码）：

- 三域 9 处 `InkHalfSheet(visible = true, ...)` 恒真；宿主以条件渲染（`state.helpText?.let` / `state.logTarget?.let` / `state.editTarget?.let` / `state.groupManageOpen` / local `jsonTarget?.let`）承载，dismiss 清槽位即摘子树，`InkHalfSheet.kt:124-128` 的 `visible=false` 离场分支（遮罩回亮 200ms + Sheet 下降 250ms）永不执行。
- 本单另实测位置参数形式 `Sheet(true, ...)`、多行 `true,` 首参、薄封装 host 内部写死：三域零命中（回证 §7.2 / §7.3）；`ui/dict/DictDialog.kt:210-211` 为派生 `visible = viewerSrc != null`，非常量，不计缺陷（§6.3 N1）。

本单处置口径（对齐 B21 / B26 / B29 两段式关闭）：

1. 关闭不直摘：每个弹层槽位各持一份离场控制器；dismiss 先走 `beginExit()` 进离场段，派生 `visible=false`，弹层子树保留组合，播组件内离场动画；写库 / 副作用仍在决策时刻执行，时机与旧版一致。
2. 收束再摘：`HalfSheetExitHost` 在离场段按 HalfSheet 两段时长（遮罩 `MASK_DURATION_MS` 200ms + 下降 `RISE_DURATION_MS` 250ms，合计 450ms）等效延时收束，`finishExit()` 后回调宿主清空槽位状态、摘除子树。摘除时机有明确依据，不写「立即摘」。
3. 离场段拒收：控制器 `canOpen` 在 EXITING 段为 false；每个打开点（含 `LaunchedEffect` 程序化入口）先过此闸再 `open()`，离场动画播放中不叠新弹层。
4. 阶段机复用：枚举 `ReaderSheetExitPhase` 与迁移规则复用 B21 `ReaderSheetExitGate` → B26 `NoteDialogExitGate` → B29 `SheetExitController` 既有链路；本单未造第二份枚举、未复制第三份守卫迁移规则。

编译结论：**未验证，等 CI**。沙箱无 gradle / JDK21 / SDK，本地未跑 `./gradlew` / `kotlinc`；新增 8 条纯 JVM 断言是否全绿以云端 CI 为准。

## 2. 改动清单（绝对路径）

### 2.1 新增文件

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt`
  - `HalfSheetExitHost(controller, onFinished, content)`：HalfSheet 版离场收束宿主；离场段按 `HalfSheetAnchors.MASK_DURATION_MS + RISE_DURATION_MS` 等效延时后 `finishExit()` 并回调 `onFinished` 清槽位摘子树；非离场段不动作。控制器类型直接复用 B29 config 域 `SheetExitController`，不另造 Compose 包装。
  - `HalfSheetExitDurations.exitMs`：离场两段时长之和（纯 JVM 可断言），数值全部取自组件 token，不另造魔法数。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt`
  - 8 例纯 JVM 断言：初始摘除态、open 后在场态、关闭瞬间子树保留且 visible=false、离场段拒收重复打开、收束后摘除、空操作兜底、收束后重开、离场时长 = 200 + 250 = 450ms。

### 2.2 修改文件（main 源集，均未提交）

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt`
  - 帮助半屏：`helpExit` 控制器；`LaunchedEffect(state.helpText)` 过闸登记；`HalfSheetExitHost` 收束清 `helpText`；派生 `helpExit.visible`，dismiss 走 `beginExit()`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt`
  - 帮助半屏 `helpExit`、日志半屏 `logExit`；两处 `LaunchedEffect` 过闸登记；`HalfSheetExitHost` 分别收束；私有 `LogSheet` 新增 `visible` 形参并透传；「清除日志」按钮 `onClear()` 后接 `onDismiss()`（beginExit）。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt`
  - `clearLog(id)` 移除内部 `logTarget = null` 直清；清日志副作用在决策时刻执行，槽位摘除交宿主收束回调 `dismissLog()`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt`
  - DialogFragment 壳内 JSON 预览半屏：`jsonExit` 控制器；`LaunchedEffect(jsonTarget)` 过闸登记；`HalfSheetExitHost` 收束清 `jsonTarget`；派生 `jsonExit.visible`。壳层整体 `onDismiss` / 导入成功后的 `dismissAllowingStateLoss()` 直摘口径原样保留（B26 外壳先例，见 §6.3 N2）。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt`
  - 编辑半屏 `editExit`、帮助半屏 `helpExit`；两处 `LaunchedEffect` 过闸登记；`HalfSheetExitHost` 分别收束；私有 `EditSheet` / `HelpSheet` 新增 `visible` 形参并透传；保存按钮 `onSave(...)` 后接 `onDismiss()`（beginExit）。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/dict/rule/DictRuleListViewModel.kt`
  - `save(rule)` 移除内部 `dismissEdit()` 直清；入库副作用在决策时刻执行，槽位摘除交宿主收束回调 `dismissEdit()`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt`
  - 分组管理半屏 `groupExit`、帮助半屏 `helpExit`；两处 `LaunchedEffect` 过闸登记；`HalfSheetExitHost` 分别收束；私有 `GroupManageSheet` 新增 `visible` 形参并透传。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt`
  - 帮助半屏：`helpExit` 控制器；`LaunchedEffect(state.helpText)` 过闸登记；`HalfSheetExitHost` 收束清 `helpText`；派生 `helpExit.visible`。

### 2.3 未触碰（范围红线）

- `ui/components/HandleFileSheet.kt:48`（components 契约冻结，红线 1 条，本单零改动，§7.8 佐证）。
- reader 域（`ui/book/read/**`）、config 域、`ui/book/source/**`（source 域由并行代理收口）、ui/card、ui/main/detail、地图、名物库（help/artifact）：均未改。
- 三域内纯 `Dialog` 弹窗（删除确认 / 批量定时 / 在线导入 URL / 退出确认 / 选择栏等）未包 `InkHalfSheet` / `InkBottomSheet`，无离场动画组件可挂，保持原状（§6.3 N3）。

## 3. 清单对账（B26 基线 9 条逐条行号）

基线行号出自 `docs/修复-B26-常量visible接线.md` §6.3；现役行号为本单落码后 grep 实测。

1. autoTask · `app/src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt`
   - 基线 :151（`InkHalfSheet` 帮助页，宿主 `state.helpText?.let`）。
   - 现役 :160 `InkHalfSheet(`；控制器 :64；闸 :66；收束宿主 :159；派生 visible :161；beginExit :162。判定：改，两段式。
2. autoTask · `app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt`（帮助页）
   - 基线 :168（`InkHalfSheet` 帮助页，宿主 `state.helpText?.let`）。
   - 现役 :184 `InkHalfSheet(`；控制器 :95；闸 :98；收束宿主 :183；派生 visible :185；beginExit :186。判定：改，两段式。
3. autoTask · `app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt`（运行日志）
   - 基线 :403（`LogSheet` 内 `InkHalfSheet`，宿主 `state.logTarget?.let`）。
   - 现役 :430 `InkHalfSheet(visible = visible, ...)`；控制器 :96；闸 :101；收束宿主 :158；私有 `LogSheet` 签名 :422-428、可见形参 :425；派生 visible :430；beginExit :163。判定：改，两段式。
4. autoTask · `app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt`
   - 基线 :98（DialogFragment 壳内 JSON 预览，宿主 local `jsonTarget?.let`）。
   - 现役 :107 `InkHalfSheet(`；控制器 :85；闸 :87；收束宿主 :106；派生 visible :108；beginExit :109。判定：改，两段式。
5. dict · `app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt`（规则编辑）
   - 基线 :377（`EditSheet` 内 `InkHalfSheet`，宿主 `state.editTarget?.let`）。
   - 现役 :394 `InkHalfSheet(visible = visible, ...)`；控制器 :87；闸 :90；收束宿主 :149；私有 `EditSheet` 可见形参 :380；派生 visible :394；beginExit :155。判定：改，两段式。
6. dict · `app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt`（帮助页）
   - 基线 :416（`HelpSheet` 内 `InkHalfSheet`，宿主 `state.helpText?.let`）。
   - 现役 :434 `InkHalfSheet(visible = visible, ...)`；控制器 :88；闸 :93；收束宿主 :175；私有 `HelpSheet` 可见形参 :433；beginExit :176。判定：改，两段式。
7. replace · `app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt`（帮助页）
   - 基线 :198（`InkHalfSheet` 帮助页，宿主 `state.helpText?.let`）。
   - 现役 :214 `InkHalfSheet(`；控制器 :107；闸 :112；收束宿主 :213；派生 visible :215；beginExit :216。判定：改，两段式。
8. replace · `app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt`（分组管理）
   - 基线 :500（`GroupManageSheet` 内 `InkHalfSheet`，宿主 `state.groupManageOpen`）。
   - 现役 :523 `InkHalfSheet(visible = visible, ...)`；控制器 :106；闸 :109；收束宿主 :179；私有 `GroupManageSheet` 可见形参 :515；派生 visible :523；beginExit :187。判定：改，两段式。
9. replace · `app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt`
   - 基线 :181（`InkHalfSheet` 帮助页，宿主 `state.helpText?.let`）。
   - 现役 :190 `InkHalfSheet(`；控制器 :61；闸 :63；收束宿主 :189；派生 visible :191；beginExit :192。判定：改，两段式。

## 4. 实测差额段

- 命名参数 `visible = true`：三域实测命中 9 条，与 B26 清单 autoTask 4 / dict 2 / replace 3 完全对账，**无多出、无遗漏**。
- 位置参数形式 `Sheet(true, ...)` / `Host(true, ...)`：三域零命中（§7.2）。
- 多行位置参数形式（行首裸 `true,`）：三域零命中（§7.3）。
- 薄封装 host 内部写死 `visible = true`：三域无 config `InkBottomSheetHost` 式薄封装；9 处全部直调 `InkHalfSheet`，无第二层间接受害私有 Sheet。
- 额外排查非缺陷项：`ui/dict/DictDialog.kt:211` `visible = viewerSrc != null` 为派生状态（非常量），子树离场路径由派生可见性驱动，不属本单缺陷形态（§6.3 N1）。
- 差额结论：**实测差额 0 条**。

## 5. 阶段机复用链（禁第二份枚举证据）

- 枚举定义：`app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetExitGate.kt:14-17`（`ReaderSheetExitPhase` OPEN / EXITING / REMOVED）。
- B26 守卫：`app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:11` import 该枚举；:26 `open()` / :34 `beginExit()` / :43 `finishExit()` / :49 `visible` / :53 `isComposed` / :60 `canOpen`。
- B29 控制器：`app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt:9` import `NoteDialogExitGate`；:54 `open()` / :57 `beginExit()` / :63 `finishExit()`；:38 `visible` / :42 `isComposed` / :46 `canOpen` / :50 `isExiting`；:73 `rememberSheetExitController()`。
- 本单：`ui/sheet/HalfSheetExitHost.kt:6` import B29 `SheetExitController`；宿主只做延时收束与回调，未新增阶段枚举、未复制迁移规则。
- 时长同源：`HalfSheetAnchors.MASK_DURATION_MS`=200ms（`HalfSheetModel.kt:53`）、`RISE_DURATION_MS`=250ms（`HalfSheetModel.kt:50`），与 `InkHalfSheet.kt:124/128` 离场分支、B26 `HighlightNoteDialog.kt:108` 一致；未另造魔法数。

## 6. 逐条判定

### 6.1 本单已改（9 条，判定依据见 §3 行号）

1. `AutoTaskEditScreen.kt` 帮助半屏：宿主 `state.helpText?.let`；旧 dismiss 直调 `onDismissHelp` 清槽摘树。现役：`beginExit()` 播离场，`HalfSheetExitHost` 450ms 收束后 `onDismissHelp()` 清槽。判定：改。
2. `AutoTaskScreen.kt` 帮助半屏：形态同 1。判定：改。
3. `AutoTaskScreen.kt` `LogSheet` 日志半屏：宿主 `state.logTarget?.let`；私有 Sheet 新增 `visible` 形参透传；清除日志副作用留在按钮决策时刻，随后 `onDismiss()` 进离场；收束回调 `onDismissLog()` 清槽。判定：改。
4. `ImportAutoTaskDialog.kt` JSON 预览半屏：DialogFragment 壳内 local `jsonTarget` 槽位；`LaunchedEffect(jsonTarget)` 过闸登记；收束回调清 `jsonTarget`。判定：改。
5. `DictRuleScreen.kt` `EditSheet` 编辑半屏：宿主 `state.editTarget?.let`；保存副作用（入库）在决策时刻执行，随后 `onDismiss()` 进离场；收束回调 `onDismissEdit()` 清槽并复位 `creating`。判定：改。
6. `DictRuleScreen.kt` `HelpSheet` 帮助半屏：形态同 1。判定：改。
7. `ReplaceRuleScreen.kt` 帮助半屏：形态同 1。判定：改。
8. `ReplaceRuleScreen.kt` `GroupManageSheet` 分组管理半屏：宿主 `state.groupManageOpen`；新建 / 重命名 / 删除动作不关闭 Sheet（列表原地刷新，旧口径保留）；遮罩 / 下拉 / 返回关闭走两段式。判定：改。
9. `ReplaceEditScreen.kt` 帮助半屏：形态同 1。判定：改。

### 6.2 宿主侧配合改动（随两段式收口，非新缺陷条目）

- `AutoTaskListViewModel.clearLog`：移除内部 `logTarget = null`。理由：旧实现点「清除日志」即直摘子树，跳过离场动画；现副作用（清库日志）时机不变，槽位摘除延后到宿主收束回调 `dismissLog()`。
- `DictRuleListViewModel.save`：移除内部 `dismissEdit()`。理由：旧实现点「保存」即直摘子树；现入库副作用时机不变，槽位摘除延后到宿主收束回调 `dismissEdit()`。`creating` 复位随 `dismissEdit()` 一并在收束后发生。
- 两处 VM 改动均在本单三域目录内；未改其它 VM 方法签名。

### 6.3 判定不改 / 留档条目（逐条读过，非静默跳过）

N1. `ui/dict/DictDialog.kt:210-211`——**不改**。证据：`visible = viewerSrc != null` 为派生布尔（图片查看器槽位非空判定），非常量 true；子树离场路径由派生可见性驱动，`InkHalfSheet` 内部保留帧条件（`progress.value == 0f && !visible`）收束，与 B29 §6.4 N2 已认可的派生 visible 口径一致。

N2. `ImportAutoTaskDialog.kt` 壳层关闭（:94-103 `onImport` 成功回调 / 整体 `onDismiss` 的 `dismissAllowingStateLoss()`）——**不改**。证据：这是 DialogFragment 整窗销毁路径，非 JSON 预览半屏自身的离场；对照 B26 §7.1 外壳防御 / 导航关闭先例，整窗销毁无可播的组件内离场动画，保持旧直摘口径。

N3. 三域内纯 `Dialog` 弹窗——**不改**。逐条核对（调用点行号，私有壳内部 `Dialog` 行号括注）：`AutoTaskEditScreen.kt:146` 退出确认；`AutoTaskScreen.kt:375` 选择栏弹窗、`:149` 删除确认（壳 :397 / 内部 :403）、`:169` 批量定时（壳 :449 / 内部 :451）、`:176` 在线导入 URL（壳 :465 / 内部 :467）；`DictRuleScreen.kt:315` 选择栏弹窗、`:139` 删除确认（壳 :342 / 内部 :348）、`:164` 在线导入 URL（壳 :414 / 内部 :419）；`ReplaceRuleScreen.kt:459` 选择栏弹窗、`:170` 删除确认（壳 :487 / 内部 :493）、`:193` 分组名输入（壳 :580 / 内部 :587）、`:206` 在线导入 URL（壳 :605 / 内部 :610）；`ReplaceEditScreen.kt:173` 退出确认。这些弹窗直接挂 `androidx.compose.ui.window.Dialog`，未包 `InkHalfSheet` / `InkBottomSheet`，无遮罩回亮 / 下降动画组件，两段式无挂载点。若日后包进模态 host，需另派单补离场闸。

N4. `ui/components/HandleFileSheet.kt:48`——**不改（红线）**。components 契约冻结；`InkHalfSheet(visible = true, ...)` 原样保留，§7.8 佐证红线目录零改动。

## 7. E1 grep 回证（命令 + 原样输出）

### 7.1 三域命名参数 `visible = true` 残留（应为空）

命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`。

### 7.2 三域位置参数 `Sheet(true, ...)` / `Host(true, ...)` 残留（应为空）

命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`。

### 7.3 三域多行位置参数（行首裸 `true,`）残留（应为空）

命令：

```
grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/autoTask/ app/src/main/java/io/legado/app/ui/dict/ app/src/main/java/io/legado/app/ui/replace/ --include=*.kt
```

原样输出：空；`exit=1`。

### 7.4 全仓命名参数残留统计

命令：

```
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt | wc -l
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt
```

原样输出：计数 `1`；唯一命中：

```
app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt:48:    InkHalfSheet(visible = true, onDismiss = onCancel, title = title, content = {
```

全仓无空格写法 `visible=true`：命令 `grep -rn "visible=true" app/src/main --include=*.kt` 输出空，`exit=1`。

口径：本单三域残留 0；全仓残留 1 条，即 components 红线 `HandleFileSheet.kt:48`。`ui/book/source/**` 由并行代理收口，其残留以该单回证为准；本单未改 source 域任何文件。

### 7.5 两段式接线落位（控制器 / 闸 / 收束宿主）

命令：

```
grep -n "rememberSheetExitController\|canOpen\|beginExit\|HalfSheetExitHost\|finishExit" \
  app/src/main/java/io/legado/app/ui/autoTask/*.kt \
  app/src/main/java/io/legado/app/ui/dict/rule/*.kt \
  app/src/main/java/io/legado/app/ui/replace/*.kt \
  app/src/main/java/io/legado/app/ui/replace/edit/*.kt
```

关键落位（改动后现役行号，完整索引见 §3）：

- `AutoTaskEditScreen.kt`：:24-25 import；:64 `helpExit`；:66 闸；:159 宿主；:161 派生 visible；:162 beginExit。
- `AutoTaskScreen.kt`：:46-47 import；:95 `helpExit`；:96 `logExit`；:98 / :101 闸；:158 / :183 宿主；:163 / :186 beginExit。
- `ImportAutoTaskDialog.kt`：:45-46 import；:85 `jsonExit`；:87 闸；:106 宿主；:108 派生 visible；:109 beginExit。
- `DictRuleScreen.kt`：:42-43 import；:87 `editExit`；:88 `helpExit`；:90 / :93 闸；:149 / :175 宿主；:155 / :176 beginExit。
- `ReplaceRuleScreen.kt`：:48-49 import；:106 `groupExit`；:107 `helpExit`；:109 / :112 闸；:179 / :213 宿主；:187 / :216 beginExit。
- `ReplaceEditScreen.kt`：:33-34 import；:61 `helpExit`；:63 闸；:189 宿主；:191 派生 visible；:192 beginExit。

### 7.6 阶段机复用符号落位

命令：

```
grep -n "import io.legado.app.ui.book.read.NoteDialogExitGate" \
  app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt
grep -n "import io.legado.app.ui.book.read.ink.ReaderSheetExitPhase" \
  app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt
grep -n "import io.legado.app.ui.config.settings.SheetExitController" \
  app/src/main/java/io/legado/app/ui/sheet/HalfSheetExitHost.kt
```

原样输出：

```
SheetExitController.kt:9:import io.legado.app.ui.book.read.NoteDialogExitGate
NoteDialogExitGate.kt:3:import io.legado.app.ui.book.read.ink.ReaderSheetExitPhase
HalfSheetExitHost.kt:6:import io.legado.app.ui.config.settings.SheetExitController
```

### 7.7 测试断言条数与测试名禁字符复核

命令：

```
grep -c "@Test" app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt
grep -nE '^\s*fun `[^`]*[.:\[\]/;<>][^`]*`' app/src/test/java/io/legado/app/ui/sheet/HalfSheetExitHostTest.kt
```

原样输出：`8`；第二条空，`exit=1`（8 个反引号测试名内不含 `. : [ ] / ; < >`，门禁 ㉘ 不会拦）。

### 7.8 红线目录零改动

命令：

```
git diff --stat -- app/src/main/java/io/legado/app/ui/components/ \
  app/src/main/java/io/legado/app/ui/book/read/ \
  app/src/main/java/io/legado/app/ui/config/ \
  app/src/main/java/io/legado/app/ui/card/
```

原样输出：main 源集四个红线目录均无 diff 行（空）。`HandleFileSheet.kt:48` 原样保留常量 true（红线）。另：`app/src/test/java/io/legado/app/ui/card/B18EmptyCardStateTest.kt` 存在 B28 预留面锁断言的并行 WIP（+18 行），非本单产物，本单未触碰 card 域任何源码与测试。

### 7.9 本单改动文件清单

命令：`git status --short | grep -v "docs/"`

本单产物：

- ` M app/src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt`
- ` M app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt`
- ` M app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt`
- ` M app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt`
- ` M app/src/main/java/io/legado/app/ui/dict/rule/DictRuleListViewModel.kt`
- ` M app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt`
- ` M app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt`
- ` M app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt`
- `?? app/src/main/java/io/legado/app/ui/sheet/`（`HalfSheetExitHost.kt`）
- `?? app/src/test/java/io/legado/app/ui/sheet/`（`HalfSheetExitHostTest.kt`）

工作树内另有非本单产物（并行代理 / 既存 WIP，未并入本单口径）：`ui/main/bookshelf/BookshelfViewModel.kt` 删除改动；`ui/card/B18EmptyCardStateTest.kt` 新增断言改动。`ui/book/source/**` 由并行代理收口，其产物以该单回证为准。本单未触碰这些路径。

### 7.10 括号配平粗检

命令：python3 剥离字符串 / 注释后逐文件计数 `{` / `}` 与 `(` / `)`。

输出：本单 9 个 main 文件（8 修改 + 1 新增宿主）与 1 个测试文件括号全部配平，无失衡（main 文件 `{}` / `()` 均相等；测试文件 9 `{` 9 `}`、64 `(` 64 `)`）。

## 8. 断言清单（HalfSheetExitHostTest.kt，8 例纯 JVM）

1. `初始为摘除态 不可见 子树不在 可接受新打开`：初始 `visible=false` / `isComposed=false` / `canOpen=true` / `isExiting=false`。
2. `open 后在场态 可见 子树保留 可开`：open 后 `visible=true` / `isComposed=true` / `canOpen=true`。
3. `关闭瞬间子树仍在且可见性已翻 false`：beginExit 后 `isComposed=true`（子树保留）、`visible=false`、`isExiting=true`。
4. `离场段拒收重复打开`：EXITING 段 `canOpen=false`；重复 beginExit 不改阶段，子树仍在。
5. `动画收束后摘除子树`：finishExit 后 `isComposed=false` / `visible=false` / `isExiting=false` / `canOpen=true`。
6. `空操作兜底 非在场开始离场不动状态 非离场收尾不迁移`：REMOVED 段 beginExit 空操作；OPEN 段 finishExit 空操作。
7. `收束后重开回到在场态并可见`：REMOVED 后再次 open 回到在场态。
8. `半屏离场时长为遮罩与下降两段之和`：`exitMs` == `MASK_DURATION_MS + RISE_DURATION_MS` == 450L。

覆盖分支：初始态 / 在场态 / 离场段 / 拒收闸 / 收束摘除 / 空操作兜底 / 重开 / 时长派生，每判定分支至少 1 条。

## 9. 二阶影响（全仓消费点逐个说明）

### 9.1 弹层内部

- 写库 / 配置副作用时机不变：`save` 入库、`clearLog` 清日志、分组增删改、帮助文案回显均在决策时刻执行，不延后到动画收束；与旧版时序一致。
- 表单 / 选项状态由既有 `remember` 或槽位状态持有，离场 ~450ms 内子树保留组合，编辑中输入、勾选、草稿不丢；收束摘子树后随宿主槽位清空，无残留状态。
- `GroupManageSheet` 新建分组后清空输入框、重命名 / 删除后列表原地刷新，旧口径保留，不关闭 Sheet。

### 9.2 与既有闸的交互

- 宿主槽位状态（`helpText` / `logTarget` / `editTarget` / `groupManageOpen` / `jsonTarget`）仍是在场段判重的唯一来源；控制器 `canOpen` 只拦离场段，两道闸串联不替代，口径同 B26 / B29。
- 离场段重复 dismiss 为守卫空操作，不会二次迁移阶段；离场段重复打开被 `canOpen` 拒收，不会出现「旧 Sheet 下降中叠起新弹窗」的双层竞态。
- 同屏多槽位（AutoTaskScreen 帮助 + 日志、DictRuleScreen 编辑 + 帮助、ReplaceRuleScreen 分组 + 帮助）各持独立控制器，互不串阶段。

### 9.3 外部消费点排查

- 各 Screen 公共签名未变：`AutoTaskEditScreen` / `AutoTaskScreen` / `DictRuleScreen` / `ReplaceRuleScreen` / `ReplaceEditScreen` 回调形参原样；Activity 接线（`AutoTaskActivity` / `AutoTaskEditActivity` / `DictRuleActivity` / 替换规则 Activity / 替换编辑 Activity）无需改动。
- 私有 Sheet（`LogSheet` / `EditSheet` / `HelpSheet` / `GroupManageSheet`）仅本文件内调用，新增 `visible` 形参不产生域外消费方。
- `ImportAutoTaskDialog` 构造签名与外部 `showDialogFragment(ImportAutoTaskDialog(...))` 调用点未变。
- 未新增跨域状态回传：控制器只反向引用 B29 / B26 / B21 既有守卫，不向阅读半屏链路 / ViewModel 回传离场位。

### 9.4 二阶 / 三阶推演

- 二阶：离场段弹层子树仍在组合，继续截获宿主触摸；宿主列表滚动 / 点击在离场 ~450ms 内不与 Sheet 手势抢通道，与 B21 验收 §7.2 结论一致。
- 二阶：等效延时只引组件自有时长常数 `MASK_DURATION_MS` + `RISE_DURATION_MS`；`InkHalfSheet` 离场为遮罩 200ms + 下降 250ms 两段，顺序播放口径对齐 B26 批注弹窗，不另造魔法数。
- 三阶：日后组件若补 `onExitFinished` 完成回调，可把 `HalfSheetExitHost` 内 `delay` 替换为回调触发，守卫阶段口径不变；延时常数跟随组件时长调整，不会漂移。
- 三阶：本单宿主复用 B29 `SheetExitController` 类型；source 域并行代理另立了 `SourceSheetExitController` 包装。出现第四个消费方前，应评估把 B21 / B26 / B29 / source 多份 Compose 包装合并为泛型单一实现（届时再议，本单不提前抽象）。

## 10. 边界（如实写明现状）

- 等效延时而非完成回调：`InkHalfSheet` 未暴露动画结束回调，沿用 B21 / B26 / B29 同一口径；延时依据 = 遮罩 200ms + 下降 250ms = 450ms。
- 离场段配置重建：控制器由 `remember` 持有，旋转 / 进程重建随 Composable 生命周期；离场中重建则阶段与离场位不跨重建存活（与 B20/B21/B26/B29 同一边界，本单不解决）。
- DialogFragment 壳（`ImportAutoTaskDialog`）：JSON 预览子 Sheet 两段式；整窗销毁路径（导入成功 / 整体关闭）仍直摘，见 §6.3 N2。
- 纯 `Dialog` 弹窗群未接离场动画（§6.3 N3）；这是组件形态决定的现状，不是遗漏。
- 范围外遗留沿用既有台账，本单不收口。

## 11. 未验证项

- 编译与单测：本地禁编译（无 gradle / JDK21 / SDK），未跑 `./gradlew` / `kotlinc`；`HalfSheetExitHostTest` 8 例是否全绿、新增 import 与私有 Sheet 形参变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段窗口触摸截获，未在真机走查；观感由既有 `InkHalfSheet` 组件保证，本单仅接线状态口径。
- 动画时序：摘子树时机为代码静态推断（延时依据 = 组件两段顺序动画时长之和 200+250ms），实际收束帧是否干净待 CI 出包后真机确认。
- `DictRuleListViewModel.save` 入库失败分支：旧实现失败也直关 Sheet，新实现失败后随离场动画收束再关；错误 toast 口径不变，失败后表单草稿保留到收束，真机观感待确认。
- 域外同形态项（source 域、components 红线）的实际影响面未在本单核验；清单判定基于源码条件渲染形态静态推断。

## 12. 需另单

- `ui/components/HandleFileSheet.kt:48` 红线 1 条：components 契约冻结，本单不动；若验收要求统一两段式，需随组件契约单独立项。
- 三域纯 `Dialog` 弹窗群（§6.3 N3）：若要求遮罩 / 下降离场动画，需先包进模态 host 再补离场闸，另派单评估。
- 多份 Compose 离场包装（B29 `SheetExitController` / source `SourceSheetExitController` / 本单 `HalfSheetExitHost`）的泛型合并：待第四个消费方出现后统一收口，本单不提前抽象。
- 跨模块观察项：`ui/main/bookshelf/BookshelfViewModel.kt` 在工作树存在大段删除改动、`ui/card/B18EmptyCardStateTest.kt` 存在新增断言改动，均非本单产物；归属与正确性请对应代理 / 验收单核对，本单未读其业务口径。

## 13. 交接备注

- 本单改动未 `git add / commit / push`；工作树改动待 CI 验收。
- 后续若改 `InkHalfSheet` 离场时长常量，`HalfSheetExitDurations.exitMs` 延时表达式自动跟随；若组件补完成回调，优先换回调、删等效延时分支。
- 复用提示：后续新消费域直接 import B29 `rememberSheetExitController()` + 本单 `HalfSheetExitHost`；HalfSheet 用 450ms 收束，BottomSheet 仍用 config 域 `SheetExitHost` 200ms 收束，两者时长口径勿混。
