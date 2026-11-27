> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

# 修复 · CI 其余包（book/source · about · replace · dict · file）

> **口径已失效（2026-11-26，Q-280）**：旧关于页 `AboutActivity` 已整体删除，详见 `docs/修复-删除关于页.md`；本段保留为历史记录，勿据以施工。

- 依据：CI run 45188065059 / job build；错误清单 `docs/CI日志-da571c9-错误清单.txt`（HEAD da571c9）。
- 范围：任务书点名 12 个文件 + 1 个同包配套文件（`CheckAppUpdate.kt`，为 AboutActivity 补 Activity 侧同位重载，属 `ui/about/**`）。
- 目标错误：64 条（清单内按文件名过滤实测 64 行，与任务书计数一致）。
- 硬约束遵守：只改上述五个包；未删功能、未写 `TODO()`、未加 `@Suppress`、未退回 XML/Binding；未动测试与 `build.gradle`；全程无 git 写操作。
- 编译状态：**本机无 SDK、禁 gradle/kotlinc，未真机编译**；以下结论均为静态自证，判定以下一轮云端 CI 为准（见「未验证项」）。

## 一、错误 → 改法对照表

### 1. `ui/book/source/manage/SourceManageSheets.kt`（10 条，全为 `@Composable invocations can only happen from the context of a @Composable function`）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 86 / 146 / 168 / 203 / 297 / 338 / 375 / 423 / 460 / 490:9 | `InkHalfSheet` 末位形参是 `onLinkSelected: (String) -> Unit`，尾随 lambda 被 Kotlin 绑定到这个**非 Composable** 形参；`content: @Composable () -> Unit` 走了默认空槽，于是槽内 `Column { ... }` 落在非 Composable 上下文。 | 10 处调用全部把尾随 lambda 改为具名 `content = { ... }` 绑定（与已通过编译的 `InkBubble.kt` 调用点同形）。 |

回证（`content = {` 共 10 处）：

```
SourceManageSheets.kt:85   InkHalfSheet(..., title = stringResource(R.string.book_source_manage_title), content = {
SourceManageSheets.kt:145  InkHalfSheet(..., title = stringResource(R.string.sort), content = {
SourceManageSheets.kt:167  InkHalfSheet(..., title = stringResource(R.string.menu_action_group), content = {
SourceManageSheets.kt:202  InkHalfSheet(..., title = stringResource(R.string.group_manage), content = {
SourceManageSheets.kt:296  InkHalfSheet(..., title = stringResource(R.string.import_on_line), content = {
SourceManageSheets.kt:337  InkHalfSheet(..., title = stringResource(R.string.check_select_source), content = {
SourceManageSheets.kt:374  InkHalfSheet(..., title = source.bookSourceName, content = {
SourceManageSheets.kt:422  InkHalfSheet(..., title = stringResource(R.string.source_selection_count, selection.size), content = {
SourceManageSheets.kt:459  InkHalfSheet(..., title = title, content = {
SourceManageSheets.kt:489  InkHalfSheet(..., title = stringResource(R.string.source_export_title), content = {
```

### 2. `ui/book/source/edit/SourceEditViewModel.kt`（6 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 98:23 `Unresolved reference 'field'` | `field(...)` 是 `SourceEditUiState` 的成员函数，VM 里漏了接收者。 | 改为 `_uiState.value.field(tab, key)`。 |
| 104:16 `Unresolved reference 'field'`；104:33/37/43 三处类型参数推断失败（R/T/T/B） | 同上；推断失败是 `field` 未解析后的级联报错，接收者补上即清零。 | 改为 `_uiState.value.field(tab, key)?.let { tab to it }`。 |

回证：

```
SourceEditViewModel.kt:98   val current = _uiState.value.field(tab, key)?.value.orEmpty()
SourceEditViewModel.kt:104  return _uiState.value.field(tab, key)?.let { tab to it }
```

### 3. `ui/book/source/debug/SourceDebugScreen.kt`（6 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 219:66 / 223:50 | `stringResource(...)` 是 Composable，却写进了 `onClick` 普通 lambda。 | 两个文案提升到 item 级局部变量 `mine` / `system`，lambda 内只引用变量。 |
| 298:14 缺 `indication`；298:14 缺 `interactionSource`；301:17 无 `onLongClick` 形参 | 误用 foundation `Modifier.clickable`（无长按、且必须传 interactionSource/indication）。 | 对齐 `ToolScaffold.ToolRow` 既有口径：有长按回调走 `combinedClickable(interactionSource, indication = null, enabled, onClick, onLongClick)`；无长按走 `rememberInkGesture(...).gestureModifier`（全站墨触，不用 Material ripple）。 |
| 407:9 Composable 上下文错位 | 同 1，`InkHalfSheet` 尾随 lambda 被 `onLinkSelected` 抢走。 | 改具名 `content = { ... }`。 |

回证：

```
SourceDebugScreen.kt:5    import androidx.compose.foundation.combinedClickable
SourceDebugScreen.kt:6    import androidx.compose.foundation.interaction.MutableInteractionSource
SourceDebugScreen.kt:43   import io.legado.app.ui.components.rememberInkGesture
SourceDebugScreen.kt:219  val mine = stringResource(R.string.debug_help_mine)
SourceDebugScreen.kt:220  val system = stringResource(R.string.debug_help_system)
SourceDebugScreen.kt:300  Modifier.combinedClickable(
SourceDebugScreen.kt:308  rememberInkGesture(enabled = enabled, onClick = onClick).gestureModifier
SourceDebugScreen.kt:401  InkHalfSheet(..., title = stringResource(R.string.source_debug_title), content = {
```

### 4. `ui/book/source/manage/BookSourceActivity.kt`（5 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 47:23 `Fragment.viewModels(...)` 接收者不匹配 | 本类是 `FragmentActivity`，却 import 了 fragment 的 `viewModels` 委托。 | 换 `androidx.activity.viewModels`（Activity 委托）。 |
| 74:23 `State<SourceManageUiState>` 不能作委托 | `by ...collectAsState()` 缺 `getValue` 导入。 | 补 `import androidx.compose.runtime.getValue`。 |
| 122:18 候选均不适用 / 缺 `input` | `QrCodeResult : ActivityResultContract<Unit?, String?>`，`launch()` 必须传入 `Unit?` 实参。 | 改为 `qrResult.launch(null)`（与 `ConfigActivity.kt:87` 既有口径一致）。 |
| 220:9 `Unresolved reference 'lifecycleScope'` | Activity 的 `lifecycleScope` 是 lifecycle-runtime-ktx 扩展，缺导入。 | 补 `import androidx.lifecycle.lifecycleScope`。 |

回证：

```
BookSourceActivity.kt:5    import androidx.activity.viewModels
BookSourceActivity.kt:8    import androidx.compose.runtime.getValue
BookSourceActivity.kt:12   import androidx.lifecycle.lifecycleScope
BookSourceActivity.kt:124  qrResult.launch(null)
BookSourceActivity.kt:222  lifecycleScope.launch {
```

### 5. `ui/about/AboutActivity.kt`（9 条）+ 配套 `CheckAppUpdate.kt`

| 清单行 | 根因 | 改法 |
|---|---|---|
| 222:5 缺 `enabled` 实参 | `SettingRowShell(onClick, enabled, modifier, content)` 的 `enabled` 无默认值。 | 调用点补 `enabled = true`。 |
| 234:25 / 238:22 / 239:25 / 240:28 `showMdFile` private 不可见；243:22 `saveLog` private；244:29 `createHeapDump` private | 行点击处理器被写成顶层扩展 `private fun AboutActivity.onRowClick`；Kotlin 类的 `private` 成员对同文件顶层声明不可见。 | 把 `onRowClick` 收进类体内作成员（`internal fun`），三个私有方法保持 `private` 不动，可见性不外扩。 |
| 235:27 / 236:32 `Fragment.checkAppUpdate` 接收者不匹配 | 检查更新只有 Fragment 侧扩展；关于页是 Compose 宿主 Activity。 | 在 `CheckAppUpdate.kt` 新增同位重载 `fun AppCompatActivity.checkAppUpdate(beta: Boolean = false)`：协程作用域用 Activity `lifecycleScope`，弹窗守卫换 `!isFinishing && !supportFragmentManager.isStateSaved`，走 Activity 侧 `showDialogFragment` 重载。 |

**新 API 形状（private 收口说明）**：
- `AboutActivity` 内部：`private fun showMdFile(...)` / `private fun saveLog()` / `private fun createHeapDump()` 维持私有；新增唯一对外入口 `internal fun onRowClick(key: String)`（模块内可见，仅同文件 `AboutScreen` 调用）。
- `CheckAppUpdate.kt`：新增顶层公开扩展 `fun AppCompatActivity.checkAppUpdate(beta: Boolean = false)`，与既有 `Fragment.checkAppUpdate` 按接收者重载分流；Fragment 侧（`MyFragment` / `MyInkFragment`）调用不受影响。

回证：

```
AboutActivity.kt:143  internal fun onRowClick(key: String) { 〔W-451 已废：同上：AboutActivity 已删，以 AboutInkActivity.kt 为准〕
AboutActivity.kt:239  SettingRowShell(onClick = onClick, enabled = true) { 〔W-451 已废：同上：AboutActivity 已删，以 AboutInkActivity.kt 为准〕
CheckAppUpdate.kt:28  fun AppCompatActivity.checkAppUpdate(beta: Boolean = false) {
```

### 6. `ui/replace/edit/ReplaceEditViewModel.kt`（6 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 84:22 `viewModelScope` 未解析 | 缺 `androidx.lifecycle.viewModelScope` 导入。 | 补导入。 |
| 113:47 `when` 不穷尽 | `ReplacePreviewException.Reason` 有 `TIMEOUT / CONTEXT_UNAVAILABLE / JS_EVALUATION` 三态，原分支只覆盖 `TIMEOUT`。 | 补 `CONTEXT_UNAVAILABLE → replace_preview_context_unavailable`、`JS_EVALUATION → replace_preview_js_error` 两个分支（文案均已在 `strings.xml` 存在）。 |
| 144:9 类型参数 T 推断失败；147:18 `fromJsonObject` 未解析；147:52 / 148:23 推断失败 | 缺 `io.legado.app.utils.fromJsonObject` 扩展导入；推断错误为级联。 | 补导入；`GSON.fromJsonObject<ReplaceRule>(text).getOrThrow()` 签名不动。 |

回证：

```
ReplaceEditViewModel.kt:5    import androidx.lifecycle.viewModelScope
ReplaceEditViewModel.kt:11   import io.legado.app.utils.fromJsonObject
ReplaceEditViewModel.kt:118  ReplacePreviewException.Reason.CONTEXT_UNAVAILABLE ->
ReplaceEditViewModel.kt:120  ReplacePreviewException.Reason.JS_EVALUATION ->
ReplaceEditViewModel.kt:153  GSON.fromJsonObject<ReplaceRule>(text).getOrThrow()
```

### 7. `ui/replace/edit/ReplaceEditActivity.kt`（4 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 23:15 / 101:13 每个类只允许一个 companion；101:23 声明冲突；91:34 `TRANSFER_THRESHOLD` 未解析 | 类里写了两个 companion（公开一个、`private companion object` 一个），后者整体不被识别，常量随之未解析。 | 合并为单个 `companion object`：`private const val TRANSFER_THRESHOLD = 4000` 移入；删除第二个 companion。 |

回证：

```
ReplaceEditActivity.kt:23  companion object {
ReplaceEditActivity.kt:24      private const val TRANSFER_THRESHOLD = 4000
ReplaceEditActivity.kt:93      if (current.length > TRANSFER_THRESHOLD) {
```

### 8. `ui/replace/ReplaceRuleScreen.kt`（3 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 199:13 / 500:9 Composable 上下文错位 | 同 1，`InkHalfSheet` 尾随 lambda 被 `onLinkSelected` 抢走。 | 两处改具名 `content = { ... }`。 |
| 436:5 缺 `onDismissRequest` | foundation `Dialog` 首参 `onDismissRequest` 无默认值；多选底栏被写成 Dialog 却没传关闭回调。 | `SelectionBar` 增 `onDismiss: () -> Unit = {}` 形参，`Dialog(onDismissRequest = onDismiss)`。默认空实现保编译；真实退出选择集的接线见「交接」。 |

回证：

```
ReplaceRuleScreen.kt:198  InkHalfSheet(..., title = "帮助", content = {
ReplaceRuleScreen.kt:435  onDismiss: () -> Unit = {},
ReplaceRuleScreen.kt:437  Dialog(onDismissRequest = onDismiss) {
ReplaceRuleScreen.kt:500  InkHalfSheet(..., title = "分组管理", content = {
```

### 9. `ui/replace/ReplaceRuleListViewModel.kt`（3 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 29:85 `asStateFlow` 未解析 | 缺 `kotlinx.coroutines.flow.asStateFlow` 导入。 | 补导入。 |
| 52:9 / 84:9 `viewModelScope` 未解析 | 缺 `androidx.lifecycle.viewModelScope` 导入。 | 补导入。 |

回证：

```
ReplaceRuleListViewModel.kt:4   import androidx.lifecycle.viewModelScope
ReplaceRuleListViewModel.kt:15  import kotlinx.coroutines.flow.asStateFlow
ReplaceRuleListViewModel.kt:31  val uiState: ... = _uiState.asStateFlow()
ReplaceRuleListViewModel.kt:54  viewModelScope.launch {
ReplaceRuleListViewModel.kt:86  viewModelScope.launch {
```

### 10. `ui/dict/rule/DictRuleListViewModel.kt`（6 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 27:82 `asStateFlow` 未解析 | 缺 flow 导入。 | 补 `kotlinx.coroutines.flow.asStateFlow`。 |
| 34:9 `viewModelScope` 未解析 | 缺 lifecycle 导入。 | 补 `androidx.lifecycle.viewModelScope`。 |
| 208:9 推断 T 失败；211:38 `fromJsonObject` 未解析；211:69 / 212:23 推断失败 | 缺 `io.legado.app.utils.fromJsonObject` 导入；级联推断错误。 | 补导入。 |

回证：

```
DictRuleListViewModel.kt:4   import androidx.lifecycle.viewModelScope
DictRuleListViewModel.kt:11  import io.legado.app.utils.fromJsonObject
DictRuleListViewModel.kt:15  import kotlinx.coroutines.flow.asStateFlow
DictRuleListViewModel.kt:30  val uiState: ... = _uiState.asStateFlow()
DictRuleListViewModel.kt:37  viewModelScope.launch {
DictRuleListViewModel.kt:214 io.legado.app.utils.GSON.fromJsonObject<DictRule>(text).getOrThrow()
```

### 11. `ui/dict/rule/DictRuleScreen.kt`（3 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 298:32 缺 `onDismissRequest` | 同 8，`Dialog` 未传关闭回调。 | `SelectionBar` 增 `onDismiss: () -> Unit = {}`，`Dialog(onDismissRequest = onDismiss)`；接线见「交接」。 |
| 377:9 / 416:9 Composable 上下文错位 | 同 1，`InkHalfSheet` 尾随 lambda 被抢。 | 两处改具名 `content = { ... }`。 |

回证：

```
DictRuleScreen.kt:297  onDismiss: () -> Unit = {},
DictRuleScreen.kt:299  androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
DictRuleScreen.kt:377  InkHalfSheet(..., "编辑词典规则", content = {
DictRuleScreen.kt:416  InkHalfSheet(..., title = "帮助", content = {
```

### 12. `ui/file/HandleFileViewModel.kt`（3 条）

| 清单行 | 根因 | 改法 |
|---|---|---|
| 42:36 / 70:32 `localizedMessage` 接收者是 `String?` | `_error.update { it.localizedMessage }` 里内层 `it` 把外层异常 `it` 遮蔽成了当前状态 `String?`。 | `onError { error -> }` 显式命名异常参数，更新 lambda 内引用 `error.localizedMessage`。 |
| 59:30 `writeBytes` 未解析（`DocumentFile`） | 缺 `io.legado.app.utils.writeBytes` 扩展导入（`DocumentFile.writeBytes(context, ByteArray)` 在 `FileDocExtensions.kt:387`）。 | 补导入；65 行 `File.writeBytes(bytes)` 走 Kotlin 标准库，不受影响、无重载歧义。 |

回证：

```
HandleFileViewModel.kt:13  import io.legado.app.utils.writeBytes
HandleFileViewModel.kt:40  .onError { error ->
HandleFileViewModel.kt:41      AppLog.put("上传文件失败\n${error.localizedMessage}", error)
HandleFileViewModel.kt:42      error.printOnDebug()
HandleFileViewModel.kt:43      _error.update { error.localizedMessage }
HandleFileViewModel.kt:60      newDoc!!.writeBytes(context, bytes)
HandleFileViewModel.kt:69  }.onError { error ->
HandleFileViewModel.kt:70      error.printOnDebug()
HandleFileViewModel.kt:71      _error.update { error.localizedMessage }
```

## 二、改动行数（`git diff --numstat`，仅本单 13 个文件）

| 文件 | 新增 | 删除 |
|---|---:|---:|
| ui/about/AboutActivity.kt | 18 | 19 |
| ui/about/CheckAppUpdate.kt | 16 | 0 |
| ui/book/source/debug/SourceDebugScreen.kt | 23 | 11 |
| ui/book/source/edit/SourceEditViewModel.kt | 2 | 2 |
| ui/book/source/manage/BookSourceActivity.kt | 4 | 2 |
| ui/book/source/manage/SourceManageSheets.kt | 20 | 20 |
| ui/dict/rule/DictRuleListViewModel.kt | 3 | 0 |
| ui/dict/rule/DictRuleScreen.kt | 6 | 5 |
| ui/file/HandleFileViewModel.kt | 8 | 7 |
| ui/replace/ReplaceRuleListViewModel.kt | 2 | 0 |
| ui/replace/ReplaceRuleScreen.kt | 6 | 5 |
| ui/replace/edit/ReplaceEditActivity.kt | 2 | 4 |
| ui/replace/edit/ReplaceEditViewModel.kt | 6 | 0 |
| **合计** | **116** | **75** |

## 三、二阶三阶影响推演（R59）

1. **直接效果**：64 条编译错按四类根因清零——Composable 上下文错位（15 处 `content =` 具名绑定）、private 跨文件访问（收口为类成员 + internal 入口）、扩展/委托缺导入（viewModelScope / asStateFlow / fromJsonObject / writeBytes / getValue / lifecycleScope）、签名咬合（Dialog 关闭回调、clickable→combinedClickable、when 穷尽、companion 合并、launch 实参）。
2. **连带影响（被改动接口 / 调用方）**：
   - `InkHalfSheet` 调用点：本单 15 处改为具名 `content`；组件签名未动，其它未改调用点行为不变。**注意**：组件末位 `onLinkSelected` 仍是尾随 lambda 陷阱，包外同类错误见「交接」。
   - `CheckAppUpdate`：新增 Activity 重载，不改 Fragment 重载；`MyFragment.kt:251-252`、`MyInkFragment.kt:109` 按 Fragment 接收者继续走旧重载，无歧义。
   - `SelectionBar`（replace / dict 两个 Screen 内私有组件）：新增带默认值的 `onDismiss` 形参，宿主 Activity 不传参也能编译；默认空实现期间，Dialog 外部点击 / 返回键不退出选择集（行为缺口，见交接）。
   - `SettingRowShell`：仅补实参，未改组件签名；`enabled = true` 与其它调用点口径一致。
   - `ReplacePreviewException.Reason` 三态全部映射到文案，预览报错不再落到 `when` 编译错；JS 求值失败走 `replace_preview_js_error`。
3. **二阶 / 三阶后果**：
   - 维护面：`content =` 具名写法与 `InkBubble` 既有通过点一致，后续新 Sheet 照此写即可；但只要组件形参顺序不调，每个新调用点仍可能再踩尾随 lambda 陷阱（建议组件层收口，见交接）。
   - 复杂度：未新增状态、未新增抽象层；About 页处理器从顶层扩展收回类内，职责更内聚。
   - 长期成本：本批根因集中在「Kotlin/Compose 签名咬合」（导入缺失、形参顺序、接收者类型、lambda 上下文），与历史 crossing 批同属一类，应沉淀为静态门禁（见 R65 复盘）。

## 四、最强反对者 · 反例与边界（R61）

**假设本单方案是错的，逐条否证：**

1. **尾随 lambda 会不会其实绑定到 `content`，报错另有原因？** 否证证据：`InkBubble.kt:304-313` 全部具名传参且不在错误清单；本单 15 处尾随 lambda 全部落在 `Column` 首条 Composable 语句上；组件末位形参确为 `onLinkSelected: (String) -> Unit`。Kotlin 尾随 lambda 绑定末位函数类型形参，故 lambda 进入非 Composable 上下文。结论：绑定判断成立；具名 `content =` 是最小改法。反例边界：若日后组件把 `content` 调回末位，具名写法依然合法，不会失效。
2. **`onDismiss = {}` 空实现是不是掩盖功能缺失？** 部分成立。选择集退出本应映射 `clearSelection()`；因 `ReplaceRuleActivity` / `DictRuleActivity` 不在本单文件域，未改宿主接线。此处**显式留口**（形参已就位、默认空实现），并在交接节写明接线动作；未用 `@Suppress`、未删按钮。边界：空数据（选择集本就空）时 Dialog 不渲染，不受影响。
3. **`combinedClickable` 长按分支会不会丢失墨触反馈？** 否证：`ToolRow` 既有同款分支（`ToolScaffold.kt:288-295`），`indication = null` 关闭 ripple，与全站禁 Material ripple 口径一致；无长按分支仍走 `rememberInkGesture` 朱砂按压反馈。边界：禁用态 `enabled = false` 时两个分支都不触发回调。
4. **导入补齐会不会与既有重载冲突？** 核查：`writeBytes` 在 utils 侧接收者为 `DocumentFile` / `Uri`，`File` 走标准库，无第三重载，无歧义；`fromJsonObject` 为 reified 扩展，`GSON` 接收者明确。边界：剪贴板空 / 非 JSON 仍由 `getOrThrow()` 抛错进 `onError` 文案兜底，路径未变。
5. **Activity 重载 `checkAppUpdate` 会不会与 Fragment 重载在同一调用点二义？** 否证：重载按接收者类型分流；AboutActivity 继承 `AppCompatActivity`，Fragment 调用点接收者为 `Fragment`，互不为候选。边界：Activity 销毁中（`isFinishing`）或 FragmentManager 已保存状态时不弹更新框，与 Fragment 侧 `isAdded && !isStateSaved` 语义对齐。
6. **异常参数改名 `error` 会不会遮蔽其它同名变量？** 两个 `onError` 块内无其它 `error` 局部变量；`AppLog.put` 第二参仍传异常本身，堆栈不丢。

## 五、跨领域对照（R60）

- **Compose 子组件槽位约定 ↔ React/Vue  children 插槽**：主流声明式 UI 都把 children 槽放在参数末位（React `children` 隐式末位、Vue 插槽默认槽）。`InkHalfSheet` 把回调 `onLinkSelected` 排在 `content` 之后，违背该约定，正是尾随 lambda 错位的制度性根源。本单在调用点侧绕开；组件侧调序建议列入交接。
- **异常分类 when ↔ 状态机穷举校验**：编译期穷尽检查等价于状态机全覆盖；补全 `CONTEXT_UNAVAILABLE / JS_EVALUATION` 两支后，新增 Reason 枚举值时编译器会再次强制补分支，比 `else` 吞掉更安全（故未用 `else` 兜底）。

## 六、历史包袱审计（R62）

`git log --follow` 核查：
- `SourceManageSheets.kt`（76e7db3 C3 书源域重写）、`AboutActivity.kt`（93e1946 设置/关于 Compose 重写）、`ReplaceEditActivity.kt`（e012b36 词典/替换/自动任务/文件四域重写）均为本轮 Compose 重写新文件，**无旧 Legado 界面代码残留**。
- 结论分类：三类问题均为**重写期契约漂移**（新壳层与既有组件 / 工具扩展签名咬合不上），不是「为已不存在问题引入的旧代码」；本单未发现可安全删除的历史包袱。
- 保留项：`CheckAppUpdate` 的 Fragment 重载仍被 My 页两个 Fragment 使用，保留不动。

## 七、认知成本（R63）

- 本单未新增概念、未加新抽象层；13 个文件职责不变。
- 命名澄清点：`InkHalfSheet` 的 `content` 与 `host` 两个 Composable 槽容易混；后续接手人读调用点时需知道「尾随 lambda 不绑 content」。建议组件层把 `content` 调到末位（交接），消除这一认知坑。
- 接手成本估算：各文件改动均为局部（导入补行 / 单 lambda 改具名 / 单分支补全），无需跨文件上下文即可复核；`CheckAppUpdate.kt` 是唯一跨接收者类型的改动，需对照 Fragment 重载阅读，约 1 个文件上下文。
- 未发现 >1000 行文件，本单不触发拆分。

## 八、最慢环节（R64）

本单为静态修复，不涉及运行路径。三句结论：
- 慢点仍在云端 clean 全量编译（本地禁编译）；本批 64 条中约半数是「缺一条 import」，编译器逐条报未解析，CI 回环成本高于修复本身。
- 建议：CI 增加轻量静态扫描（缺 `viewModelScope / asStateFlow / fromJsonObject` 等常见导入、`Dialog(` 裸调用、`InkHalfSheet(` 尾随 lambda），在编译前先一轮 grep 门禁，缩短红→绿回环。
- 未把慢点归因于机器。

## 九、返工复盘沉淀（R65）

本批根因聚簇：

| 根因类别 | 典型表现 | 预防手段（建议沉淀规则 / 门禁） |
|---|---|---|
| 契约漂移（导入缺失） | `viewModelScope` / `asStateFlow` / `fromJsonObject` / `writeBytes` / `getValue` / `lifecycleScope` 未解析 | 模板 / 清单：新建 VM 必带这六个导入；CI grep 未解析符号预检。 |
| 签名咬合（槽位顺序） | 尾随 lambda 绑到末位回调形参，Composable 上下文错位 | 组件约定：children/`content` 槽必须排参数末位；代码评审检查 `InkHalfSheet(...) {` 尾随写法。 |
| 签名咬合（接收者类型） | Fragment 扩展用到 Activity 上；`Fragment.viewModels` 用到 Activity 上 | 同名扩展按接收者成对提供（本单 `checkAppUpdate` 已补 Activity 侧）；宿主基类与委托导入对齐。 |
| 作用域遮蔽 | `onError` 内层 `it` 遮蔽异常 `it`，误取状态 `String?` | 代码规范：`onError { }` 一律显式命名 `error ->`。 |
| 穷尽性 | `when(enum)` 漏分支 | 禁止用 `else` 吞枚举分支；补全所有 Reason 态。 |

## 十、59–66 逐条结论

- **59 二阶三阶推演**：已写第三节，含直接效果 / 连带文件与接口 / 长期维护后果三层。
- **60 跨领域对照**：已写第五节，对照 React/Vue 插槽约定与状态机穷举校验，说明采纳 / 不采纳理由。
- **61 自我否证**：已写第六节，6 条反例边界（lambda 绑定判断、空 dismiss、长按反馈、重载歧义、异常遮蔽、Activity 销毁态）并给出否证后是否成立的结论。
- **62 历史包袱审计**：已写第七节，三文件 git 历史核查结论为重写期契约漂移，无可删旧码；Fragment 重载保留理由已注。
- **63 认知成本**：已写第八节，改动局部、无新抽象；唯一认知坑是双槽组件形参顺序，建议组件层收口。
- **64 最慢环节**：已写第八节，慢点在云端编译回环；建议导入 / 裸 Dialog / 尾随 lambda 静态预检门禁。
- **65 返工复盘**：已写第九节，五类根因聚簇 + 每类预防手段，建议沉淀为规则 / CI 预检。
- **66 开工前必读**：已读 `docs/机制-子代理必读.md` 与总纲 59–66 全文；本报告含影响 / 反例 / 认知成本三节，前置阅读齐备。

## 十一、未验证项（必须明说）

1. **未编译**：本机无 Android SDK，按红线禁跑 gradle / kotlinc / assemble；所有结论为静态阅读 + 括号配平 + grep 回证，**编译能否通过以下一轮云端 CI 为准**。
2. **未跑单测 / 未真机**：`ReplacePreview` 相关单测、导入粘贴路径、文件落盘路径均未执行。
3. **括号配平**仅为文本层 `{}`/`()` 计数为 0，不代表类型推断全部通过；级联报错（如 104 行三处推断、147/148 推断）在根因消除后应清零，但未经编译器确认。
4. `SelectionBar` 的 `onDismiss` 目前默认空实现，外部点击 / 返回键退出选择集的行为**尚未接线验证**。
5. Activity 侧 `checkAppUpdate` 弹窗生命周期守卫（`isFinishing` / `isStateSaved`）为语义对齐推断，未在销毁场景实测。

## 十二、交接（本单未动手，按域转给对应代理）

### A. `ui/config/**` —— 另有代理负责，本单未读未改。

### B. 同五包内、不在本单 13 文件清单的剩余错误（清单原文，建议按根因分派）

**ui/about/**
- `AboutInkActivity.kt:103:15` State<Float> 委托缺 `getValue`；`:121:33/49` `graphicsLayer`/`translationX` 未解析；`:83:43` `shape` 未解析。
- `AppLogDialog.kt:130:21` 实参重复；`:147:37` `clickable` 未解析。
- `CrashLogsDialog.kt:138:57` `clickable` 未解析。
- `ReadRecordActivity.kt:100:56` 缺 `p1`；`:99:63` `putInt` 未解析。
- `UpdateDialog.kt:124:9` `toastOnUi` 未解析。

**ui/book/source/**
- `debug/BookSourceDebugActivity.kt:7:23` State 委托缺 `getValue`；`:52:38` 候选不适用 / 缺 `input`（同 `launch(null)` 口径）。
- `debug/SourceDebugViewModel.kt:59:50` `localizedMessage` 接收者为 `SourceDebugUiState`（同 `HandleFileViewModel` 的 `it` 遮蔽根因）。
- `edit/BookSourceEditActivity.kt:143:51` `() -> Unit` 传给 `OnBackPressedCallback`；`:207:40` 候选不适用 / 缺 `input`。
- `edit/JsSourceEditActivity.kt:92:22` `fillMaxSize` 未解析（缺导入）。
- `edit/SourceEditScreen.kt:427:9` Composable 上下文错位（`InkHalfSheet` 尾随 lambda 同根因）。
- `edit/SourceEditState.kt:23:23` 递归类型推断，需显式标注声明类型。 〔W-451 回源有效〕
- `manage/SourceManageViewModel.kt:96/138/148:23` `viewModelScope` 未解析（缺 `androidx.lifecycle.viewModelScope` 导入，与本单 VM 同修法）。

**ui/dict/**
- `DictDialog.kt:77:29` StateFlow `collectAsState` 接收者不匹配 / 推断 T 失败。

**ui/file/**
- `FilePickerDialog.kt:144:34` `collectAsState` 未解析；`:96:47` `collectAsStateValue` 未解析。
- `HandleFileActivity.kt:100:25` Composable 上下文错位；`:96:18` 调 Composable 的函数缺 `@Composable` 注解。

**ui/replace/**
- `edit/ReplaceEditScreen.kt:182:13` Composable 上下文错位（`InkHalfSheet` 尾随 lambda 同根因）。
- `ReplaceRuleActivity.kt:166:9` `lifecycleScope` 未解析（缺导入）。

### C. 组件层根因建议（跨域，需组件代理决策）

- `ui/components/InkHalfSheet.kt:86-98`：建议把 `onLinkSelected` 移到 `content` 之前，让 children 槽 `content` 回到参数末位。一次调序可消除全部同类尾随 lambda 错位（本单 15 处 + 交接 B 中 `SourceEditScreen:427`、`ReplaceEditScreen:182`、`HandleFileActivity:100`、`HandleFileSheet:49`、autoTask 三处等）。本单受文件域约束未改组件。

### D. 本单留口待接线

- `ReplaceRuleScreen.SelectionBar` / `DictRuleScreen.SelectionBar` 已备 `onDismiss` 形参（默认 `{}`）；建议宿主 `ReplaceRuleActivity` / `DictRuleActivity` 传入 `viewModel::clearSelection`（两个 VM 均已有 `clearSelection()`），使 Dialog 外部点击 / 返回键退出选择集。
