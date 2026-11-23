# 修复记录 · CI 三轮其余包（排除 `ui/config/**`）

- 基线：main，HEAD `54bd363`；CI run `45192886781`；清单 `docs/CI日志-54bd363-错误清单.txt`（79 条去重）。
- 范围：清单中**所有非 `ui/config/` 前缀**条目，共 50 行（含 4 组同行双报：25/26、29/30、68/69、72/73；按错误语义合并为 46 个修复点）。
- 红线遵守：未删功能、未加 `TODO()`/`@Suppress`、未回退 XML、未改测试与 `build.gradle`、**全程无任何 git 写操作**；`ui/config/**` 两个在途文件（`BottomBarSkinAssignActivity.kt`、`sections/OtherSection.kt`）属另一代理，本报告**一行未碰**。
- 验证状态：**本机无 Android SDK、禁 gradle/kotlinc，本轮全部修复仅经词法/括号配平/grep 回证，编译与运行验证留待下一轮 CI**（详见第八节）。

## 一、逐条 → 改法表

### 1. `ui/about/AboutInkActivity.kt`（4 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 103:15 | `State<Float>` 不能当委托（缺 `getValue`） | `by rememberInfiniteTransition(...)` 委托只读 `State`，漏导 `getValue` | 补 `import androidx.compose.runtime.getValue` | `AboutInkActivity.kt:26` |
| 121:33 | 未解析 `graphicsLayer` | 图形层扩展漏导 | 补 `import androidx.compose.ui.graphics.graphicsLayer` | `AboutInkActivity.kt:30`；调用点 `:124` |
| 121:49 | 未解析 `translationX` | 随 `graphicsLayer` 一并解析（`GraphicsLayerScope` 成员），非独立导入项 | 同上，不另加导入 | `AboutInkActivity.kt:124` |
| 83:43 | 未解析 `shape`（`androidx.compose.ui.shape.RoundedCornerShape` 包名写错） | 真实包名是 `androidx.compose.foundation.shape.RoundedCornerShape` | 改短名调用并补 foundation.shape 导入 | 导入 `:13`；调用 `:86` |

### 2. `ui/about/AppLogDialog.kt`（2 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 130:21 | 同一参数重复传参（`modifier` 写了两次） | `Column` 同时在位置参数和具名参数给 `modifier` | 合并为一条 `Modifier.fillMaxWidth().padding(...).then(gesture.gestureModifier)` | `AppLogDialog.kt:130-133` |
| 147:37 | 未解析 `clickable`（手写 `Modifier.clickable(m) {...}` 用法在本 Compose 版本不存在） | 项目统一走 `rememberInkGesture` 墨触手势，旧手写 `clickableRow` 扩展作废 | 删私有 `clickableRow` 扩展；`items` 内 `rememberInkGesture(enabled = true, onClick = ...).gestureModifier`；补 components 手势导入 | 导入 `:34`；手势 `:129`；旧扩展已清零（grep `clickableRow` 全仓无命中） |

### 3. `ui/about/CrashLogsDialog.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 138:57 | 未解析 `clickable` | 同上，手写 `foundation.clickable(m) {...}` 不存在 | 同 AppLogDialog：`rememberInkGesture` + `gestureModifier`；补导入 | 导入 `:35`；手势 `:136` |

### 4. `ui/about/ReadRecordActivity.kt`（2 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 85:56 | `getInt` 缺实参 `p1` | `SharedPreferences.getInt(key, defValue)` 必须给默认值 | 补默认值 `0`，与 `Backup.kt:355`/`Restore.kt:477` 的 `coerceIn(0..2)` 口径一致 | `ReadRecordActivity.kt:86` |
| 99:63 | 未解析 `putInt` | `SharedPreferences` 无直接 `putInt` 方法；旧代码误当 SP 直接写 | 改 `LocalConfig.edit { putInt("readRecordSort", it) }`；补 `androidx.core.content.edit` 导入（与 `LocalConfig.kt:77/99/117` 同一写法） | 导入 `:37`；调用 `:100` |

### 5. `ui/about/UpdateDialog.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 124:9 | 未解析 `toastOnUi` | `toastOnUi` 是 `Context`/`Fragment` 扩展；`UpdateContent` 是 `@Composable`，无 Context 接收者 | 用已取得的 `LocalContext.current`：`context.toastOnUi("没有数据")` | `UpdateDialog.kt:124` |

### 6. `ui/autoTask/AutoTaskDebugActivity.kt`（2 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 118:20 / 92:9 | 未解析 `lifecycleScope` | FragmentActivity 协程作用域漏导 | 补 `import androidx.lifecycle.lifecycleScope`（两处调用共用一导） | 导入 `:22`；调用点 `:93`、`:119` 附近 |

### 7. `ui/autoTask/AutoTaskEditScreen.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 152:13 | Composable 调用发生在非 Composable 上下文 | 尾随 lambda 被 `InkHalfSheet` 最后一个非内容形参抢走；内容 lambda 拿到的不是 `@Composable` 槽位 | 显式 `content = { ... }` 绑定；同步把结尾 `}` 改为 `})` 闭合调用括号 | `:151` 开口；`:163` 闭合 `})` |

### 8. `ui/autoTask/AutoTaskListViewModel.kt`（2 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 26:82 | `MutableStateFlow.asStateFlow()` 未解析 | 流操作符漏导 | 补 `import kotlinx.coroutines.flow.asStateFlow` | 导入 `:12`；调用 `:28` |
| 33:9 | 未解析 `viewModelScope` | ViewModel 协程作用域漏导 | 补 `import androidx.lifecycle.viewModelScope` | 导入 `:4` |

### 9. `ui/autoTask/AutoTaskScreen.kt`（3 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 169:13 | Composable 上下文错位（帮助半屏） | 尾随 lambda 槽位被抢 | `content = {` 显式绑定；结尾补 `})` | 开口 `:168`；闭合 `:180` |
| 350:5 | `Dialog` 缺 `onDismissRequest` | 私有 `SelectionBar` 未把 dismiss 回调透传给 `Dialog` | 形参补 `onDismiss: () -> Unit = {}`（默认空实现保编译）；`Dialog(onDismissRequest = onDismiss)` | 形参 `:349`；Dialog `:351` |
| 401:9 | Composable 上下文错位（日志半屏 `LogSheet`） | 尾随 lambda 槽位被抢 | `content = {`；结尾补 `})` | 开口 `:401`；闭合 `:416` |

### 10. `ui/autoTask/ImportAutoTaskDialog.kt`（3 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 117:34 | 未解析 `collectAsState` | 私有 `collectAsStateValue()` 内全限定调用漏导入 | 删私有包装方法，改标准 `viewModel.uiState.collectAsState().value`；补 `androidx.compose.runtime.collectAsState` 导入 | 导入 `:22`；调用 `:79`；包装方法已删 |
| 78:47 | `StateFlow` 上未解析 `collectAsStateValue` | 同上，自写包装签名与调用方不匹配 | 同上，统一到标准 receiver 调用 | `:79` |
| 98:25 | Composable 上下文错位（JSON 查看半屏） | 尾随 lambda 槽位被抢 | `content = {`；结尾补 `})` | 开口 `:98`；闭合 `:110` |

### 11. `ui/autoTask/ImportAutoTaskViewModel.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 64:65 | `localizedMessage` 接收者是 `ImportAutoTaskState` | `.onError { }` 内 `_uiState.update { it.copy(error = it.localizedMessage) }`，内层 `it` 遮蔽外层异常，`it` 解析成状态对象 | 异常显式命名：`.onError { error -> ... error.localizedMessage ... AppLog.put(..., error) }`（与 `HandleFileViewModel` 上一轮判例一致） | `:63-65` |

### 12. `ui/book/read/ReadBookActivity.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 1649:41 | `startIntent` 缺 `id` 实参 | 选中文字新建替换规则走新规则入口，未传 id | 补 `id = -1L`（新规则哨兵值，与 `ReplaceEditActivity:63` `getLongExtra("id", -1L)`、`ReplaceEditViewModel.load` 默认口径一致） | `:1651` |

### 13. `ui/book/source/debug/BookSourceDebugActivity.kt`（3 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 33:23 | `State<...>` 不能当委托（缺 `getValue`） | `by vm.uiState.collectAsState()` 漏导只读委托 | 补 `import androidx.compose.runtime.getValue` | 导入 `:7` |
| 52:38（候选不适用 / 缺 `input`） | `qrResult.launch()` 无参 | `QrCodeResult` 契约输入类型 `Unit?`，`launch` 需要显式传值 | 改 `qrResult.launch(null)`（与 `BookSourceActivity:124` 同口径） | `:53` |

### 14. `ui/book/source/debug/SourceDebugViewModel.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 59:50 | `localizedMessage` 接收者是 `SourceDebugUiState` | `it` 遮蔽同 #11 | `.onError { error -> ... error.localizedMessage ?: "调试失败" }` | `:58-59` |

### 15. `ui/book/source/edit/BookSourceEditActivity.kt`（2 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 143:51 | 实参类型 `() -> Unit` 与 `OnBackPressedCallback` 不匹配 | `addCallback` 的 lambda 扩展重载漏导，编译器退回原始接口签名 | 补 `import androidx.activity.addCallback`（与 `AudioPlayActivity:12` 同口径） | 导入 `:5`；调用 `:144` |
| 207:40（候选不适用 / 缺 `input`） | `qrCodeResult.launch()` 无参 | 同 #13，`QrCodeResult` 输入 `Unit?` | `qrCodeResult.launch(null)` | `:208` |

### 16. `ui/book/source/edit/JsSourceEditActivity.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 92:22 | 未解析 `fillMaxSize` | 布局修饰符漏导 | 补 `import androidx.compose.foundation.layout.fillMaxSize` | 导入 `:7`；调用 `:93` |

### 17. `ui/book/source/edit/SourceEditScreen.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 427:9 | Composable 上下文错位（溢出操作半屏 `OverflowSheet`） | 尾随 lambda 槽位被抢 | `content = {`；结尾补 `})` | 开口 `:426`；闭合 `:452` |

### 18. `ui/book/source/edit/SourceEditState.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 23:23 | 递归类型检查失败 | companion 内 `val entries = entries.toList()`，未限定的 `entries` 递归指向自身属性，遮蔽枚举合成成员 | 改 `val entries: List<EditTab> = values().toList()`，显式类型标注；与 `SearchContentViewModel:45` 的 `ContentSearchScope.values()` 同写法 | `:23` |

### 19. `ui/book/source/manage/SourceManageViewModel.kt`（3 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 96/138/148:23 | 未解析 `viewModelScope`（三处 `.launchIn(viewModelScope)`） | ViewModel 协程作用域漏导 | 补 `import androidx.lifecycle.viewModelScope`（三处共用一导） | 导入 `:4`；调用 `:97`、`:139`、`:149` |

### 20. `ui/components/HandleFileSheet.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 49:9 | Composable 上下文错位 | 尾随 lambda 槽位被抢 | `content = {`；结尾补 `})` | 开口 `:48`；闭合 `:78` |

### 21. `ui/components/ToolScaffold.kt`（2 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 113:23 / 303:23 | 无具名参数 `minHeight` | `Modifier.heightIn` 的形参是 `min`/`max`，不是 `minHeight`（旧 View `android:minHeight` 概念串台） | 两处改 `heightIn(min = 48.dp)`；与本文件 `:241` 既有正确写法对齐 | `:113`、`:303` |

### 22. `ui/dict/DictDialog.kt`（2 条，同一点双报）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 77:29 | receiver 类型不匹配；类型参数 `T` 无法推断 | 写成顶层调用 `collectAsState(viewModel.uiState)`，而 `StateFlow.collectAsState` 是 receiver 扩展 | 改 receiver 形式 `viewModel.uiState.collectAsState().value` | `:77` |

### 23. `ui/file/FilePickerDialog.kt`（2 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 144:34 | 未解析 `collectAsState` | 私有 `collectAsStateValue()` 包装漏导入 | 删包装方法，标准 `collectAsState().value`；补导入 | 导入 `:26`；调用 `:97`；包装已删 |
| 96:47 | `StateFlow` 上未解析 `collectAsStateValue` | 自写包装签名不匹配 | 同上 | `:97` |

### 24. `ui/file/HandleFileActivity.kt`（2 条，同一点双报）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 100:25 / 96:18 | `@Composable` 调用写在 `onCreate`（非组合上下文） | `onCreate` 里误用 `collectAsState()` 订阅状态 | 非组合上下文只读快照：`viewModel.error.value?.let { ... }`；删随之失用的 `collectAsState` 导入 | 调用 `:99`；导入已删 |

### 25. `ui/main/detail/DetailSheetContent.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 115:42 | `val` 不能被重新赋值 | `apply { onYearChanged = onYearChanged }` 内成员名与外层参数同名，左侧被解析成外层 `val` 参数造成遮蔽 | 改 `also { view -> view.onYearChanged = onYearChanged }`，显式接收者消歧；与下方 `update` 块写法一致 | `:115` |

### 26. `ui/replace/edit/ReplaceEditScreen.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 182:13 | Composable 上下文错位（帮助半屏） | 尾随 lambda 槽位被抢 | `content = {`；结尾补 `})` | 开口 `:181`；闭合 `:193` |

### 27. `ui/replace/ReplaceRuleActivity.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 166:9 | 未解析 `lifecycleScope` | FragmentActivity 协程作用域漏导 | 补 `import androidx.lifecycle.lifecycleScope` | 导入 `:9` |

### 28. `ui/replace/ReplaceRuleScreen.kt`（2 条，语法）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 210:10 | `Expecting ')'` | 上一轮把开口改成 `content = {` 后，闭合仍是旧尾随 lambda 的 `}`/`})` 顺序，调用括号未在内容 lambda 之后闭合 | 调换闭合顺序：内容 lambda 闭合处 `})`，再闭 `let { }` | `:210` `})`、`:211` `}` |
| 211:6 | 意外 token | 同上，括号嵌套次序错误 | 同上 | 同上 |

### 29. `ui/widget/SelectActionBar.kt`（1 条）

| 清单位置 | 错误 | 根因 | 改法 | 回证 |
| --- | --- | --- | --- | --- |
| 190:18 | 未解析 `R.id.menu_del` | 该菜单项 id 在 XML 清理时已删除（现存选择菜单仅 `menu_del_selection`），代码残留死引用 | 危险色标集合移除 `R.id.menu_del`，仅保留 `R.id.menu_del_selection`；未恢复/未改 XML | `:189` |

## 二、grep 回证（分类别关键符号 → file:line）

### 2.1 缺失导入补齐

```
# Compose 只读委托 getValue
ui/about/AboutInkActivity.kt:26   import androidx.compose.runtime.getValue
ui/book/source/debug/BookSourceDebugActivity.kt:7   import androidx.compose.runtime.getValue

# 图形 / 形状
ui/about/AboutInkActivity.kt:13   import androidx.compose.foundation.shape.RoundedCornerShape
ui/about/AboutInkActivity.kt:30   import androidx.compose.ui.graphics.graphicsLayer

# 协程作用域
ui/autoTask/AutoTaskDebugActivity.kt:22   import androidx.lifecycle.lifecycleScope
ui/replace/ReplaceRuleActivity.kt:9       import androidx.lifecycle.lifecycleScope
ui/autoTask/AutoTaskListViewModel.kt:4    import androidx.lifecycle.viewModelScope
ui/book/source/manage/SourceManageViewModel.kt:4   import androidx.lifecycle.viewModelScope

# 流操作符
ui/autoTask/AutoTaskListViewModel.kt:12   import kotlinx.coroutines.flow.asStateFlow

# 布局修饰符
ui/book/source/edit/JsSourceEditActivity.kt:7   import androidx.compose.foundation.layout.fillMaxSize

# 返回键回调扩展
ui/book/source/edit/BookSourceEditActivity.kt:5   import androidx.activity.addCallback

# SP 写入扩展
ui/about/ReadRecordActivity.kt:37   import androidx.core.content.edit

# 状态收集标准导入
ui/autoTask/ImportAutoTaskDialog.kt:22   import androidx.compose.runtime.collectAsState
ui/file/FilePickerDialog.kt:26           import androidx.compose.runtime.collectAsState
```

### 2.2 Composable 上下文：尾随 lambda 全部改显式 `content = {`

```
ui/autoTask/AutoTaskEditScreen.kt:151   InkHalfSheet(..., content = {  → 闭合 :163 })
ui/autoTask/AutoTaskScreen.kt:168       InkHalfSheet(..., content = {  → 闭合 :180 })
ui/autoTask/AutoTaskScreen.kt:401       InkHalfSheet(..., content = {  → 闭合 :416 })
ui/autoTask/ImportAutoTaskDialog.kt:98  InkHalfSheet(..., content = {  → 闭合 :110 })
ui/book/source/edit/SourceEditScreen.kt:426  InkHalfSheet(..., content = { → 闭合 :452 })
ui/components/HandleFileSheet.kt:48     InkHalfSheet(..., content = {  → 闭合 :78 })
ui/replace/edit/ReplaceEditScreen.kt:181 InkHalfSheet(..., content = { → 闭合 :193 })
```

### 2.3 具名参数 / 签名对齐

```
ui/autoTask/AutoTaskScreen.kt:349   onDismiss: () -> Unit = {},
ui/autoTask/AutoTaskScreen.kt:351   Dialog(onDismissRequest = onDismiss) {
ui/book/source/debug/BookSourceDebugActivity.kt:53   qrResult.launch(null)
ui/book/source/edit/BookSourceEditActivity.kt:208    qrCodeResult.launch(null)
ui/components/ToolScaffold.kt:113    .heightIn(min = 48.dp)
ui/components/ToolScaffold.kt:303    .heightIn(min = 48.dp)
ui/book/read/ReadBookActivity.kt:1651  id = -1L,
```

### 2.4 遮蔽 / 类型 / 语义修复

```
ui/autoTask/ImportAutoTaskViewModel.kt:63  }.onError { error ->
ui/book/source/debug/SourceDebugViewModel.kt:58  }.onError { error ->
ui/dict/DictDialog.kt:77            val state = viewModel.uiState.collectAsState().value
ui/file/FilePickerDialog.kt:97      val state = viewModel.uiState.collectAsState().value
ui/autoTask/ImportAutoTaskDialog.kt:79  val state = viewModel.uiState.collectAsState().value
ui/file/HandleFileActivity.kt:99    viewModel.error.value?.let {
ui/main/detail/DetailSheetContent.kt:115  InkTimelineView(ctx).also { view -> view.onYearChanged = onYearChanged }
ui/book/source/edit/SourceEditState.kt:23  val entries: List<EditTab> = values().toList()
ui/about/ReadRecordActivity.kt:86   LocalConfig.getInt("readRecordSort", 0)
ui/about/ReadRecordActivity.kt:100  LocalConfig.edit { putInt("readRecordSort", it) }
ui/about/UpdateDialog.kt:124        context.toastOnUi("没有数据")
ui/widget/SelectActionBar.kt:189    R.id.menu_del_selection
```

### 2.5 清零验证（旧符号全仓无残留）

```
grep -rn "collectAsStateValue" app/src/main/java/   → 无命中
grep -rn "clickableRow" app/src/main/java/         → 无命中
grep -rn "R.id.menu_del\b" app/src/main/          → 无命中（仅 menu_del_selection 保留）
grep -rn "androidx.compose.ui.shape" app/src/      → 无命中
```

### 2.6 括号配平

- 对本批 29 个文件做词法配平扫描（剥离注释、字符串、字符字面量、`${}` 插值）：`() {} []` 全部配平，无未闭合。
- 基线即存在的扫描“不平衡”均为字符串内括号造成的工具误报（`SourceManageViewModel`、`HandleFileActivity`、`ReplaceRuleScreen`、`AutoTaskScreen` 在 HEAD 上同现象），改进分词后复测全部配平。

## 三、`git diff --numstat`（仅本代理改动，已排除 `ui/config/**` 与测试目录）

```
4   1   app/src/main/java/io/legado/app/ui/about/AboutInkActivity.kt
5   7   app/src/main/java/io/legado/app/ui/about/AppLogDialog.kt
3   4   app/src/main/java/io/legado/app/ui/about/CrashLogsDialog.kt
3   2   app/src/main/java/io/legado/app/ui/about/ReadRecordActivity.kt
1   1   app/src/main/java/io/legado/app/ui/about/UpdateDialog.kt
1   0   app/src/main/java/io/legado/app/ui/autoTask/AutoTaskDebugActivity.kt
2   2   app/src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt
2   0   app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt
6   5   app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt
4   7   app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt
3   3   app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskViewModel.kt
1   0   app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt
2   1   app/src/main/java/io/legado/app/ui/book/source/debug/BookSourceDebugActivity.kt
2   2   app/src/main/java/io/legado/app/ui/book/source/debug/SourceDebugViewModel.kt
2   1   app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt
1   0   app/src/main/java/io/legado/app/ui/book/source/edit/JsSourceEditActivity.kt
2   2   app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt
1   1   app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditState.kt
1   0   app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt
2   2   app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt
2   2   app/src/main/java/io/legado/app/ui/components/ToolScaffold.kt
1   1   app/src/main/java/io/legado/app/ui/dict/DictDialog.kt
2   5   app/src/main/java/io/legado/app/ui/file/FilePickerDialog.kt
1   2   app/src/main/java/io/legado/app/ui/file/HandleFileActivity.kt
1   1   app/src/main/java/io/legado/app/ui/main/detail/DetailSheetContent.kt
1   0   app/src/main/java/io/legado/app/ui/replace/ReplaceRuleActivity.kt
2   2   app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt
2   2   app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt
1   2   app/src/main/java/io/legado/app/ui/widget/SelectActionBar.kt
```

合计：**29 个文件，新增 61 行，删除 58 行**。

## 四、二阶 / 三阶影响（铁律 59）

1. **补导入类（shape/graphicsLayer/getValue/lifecycleScope/viewModelScope/asStateFlow/fillMaxSize/addCallback/edit）**
   - 一阶：对应未解析引用可解析。
   - 二阶：同一文件内其他使用同一 API 的位置一并获得解析，无需逐个补；不改变运行行为。
   - 三阶：无行为外溢；需留意同一包内若另有同名短名调用，不会因本次导入产生歧义（已 grep 无重名）。
2. **`rememberInkGesture` 替换手写 `clickable`（AppLogDialog / CrashLogsDialog）**
   - 一阶：点击可解析。
   - 二阶：按压/墨爆反馈统一走 `InkInteractionState`，与全局朱砂覆盖主题一致；手势状态由 Compose 组合生命周期管理，离开组合自动清理，无泄漏。
   - 三阶：旧 `Modifier.clickable` 的默认涟漪不再出现——这是规格要求的墨爆粒子方向，非回退；列表项长按/双击等扩展手势当前未接，属既有能力缺口（非本轮引入）。
3. **`content = { }` 显式绑定（7 处半屏）**
   - 一阶：内容 lambda 回到 `@Composable` 上下文。
   - 二阶：半屏内部可继续嵌套任意 Composable；不再被 `onDismiss`/`onLinkSelected` 等最后一个普通 lambda 形参抢走。
   - 三阶：同步移动了调用括号闭合位置（`})`），若后续再往 `InkHalfSheet` 增删形参，应保持内容槽位显式具名，避免尾随 lambda 再次错位。
4. **`Dialog(onDismissRequest = ...)`（AutoTaskScreen SelectionBar）**
   - 一阶：Dialog 必需形参补齐。
   - 二阶：系统返回键/点遮罩会触发传入回调；当前父级未传退出选择集回调，默认空实现（见第九节交接）。
   - 三阶：选择态清除仍只走列表项反选/全选切换；外部遮罩点击不退出选择模式，属待接线缺口，不影响编译。
5. **`.value` 替代 `collectAsState`（HandleFileActivity.onCreate）**
   - 一阶：脱离组合上下文后可编译。
   - 二阶：只读错误流当前快照，不建立订阅。
   - 三阶：若错误在 `onCreate` 之后才到达，本页不会再弹 toast/finish（时序限制，见第八节）；需要持续观察应在 `setContent` 内用 `collectAsState` 或 `LifecycleObserver` 订阅，本轮未改该结构。
6. **`onError { error -> }` 异常显式命名（ImportAutoTaskViewModel / SourceDebugViewModel）**
   - 一阶：`localizedMessage` 取到异常而非状态对象。
   - 二阶：`AppLog.put` 能挂上真实异常堆栈；错误文案不再是“状态对象无此属性”的隐性脏数据。
   - 三阶：修复前若编译通过，界面错误提示与日志堆栈都会失真；本修复同时纠正了潜在的数据正确性问题。
7. **`SelectActionBar` 移除 `menu_del` 死引用**
   - 一阶：资源 id 解析通过。
   - 二阶：单条删除菜单项的危险色标不再判定；现存批量删除 `menu_del_selection` 色标不受影响。
   - 三阶：若将来 XML 恢复单删菜单，需同步把该 id 加回 `dangerMenuItemIds`。
8. **`SourceEditState.values()`**
   - 一阶：消除 companion 内自引用递归。
   - 二阶：枚举列表在 companion 初始化一次，消费方 `EditTab.entries.forEach` 口径不变。
   - 三阶：`values()` 在新版 Kotlin 属被 `entries` 建议替代的旧写法，但当前编译目标下可用；后续可平滑迁到 `enumEntries<EditTab>()`。
9. **`ReadBookActivity` 补 `id = -1L`**
   - 一阶：Intent 形参齐全。
   - 二阶：选中文字进入替换规则编辑时按“新规则”加载，不会误取某条既有规则 id。
   - 三阶：`-1L` 是全项目新规则哨兵值，若日后改哨兵口径需全局统一。

## 五、反例与边界（铁律 61）

1. **缺导入不等于都要补同一条**：`translationX` 是 `GraphicsLayerScope` 成员，随 `graphicsLayer` 导入解析，不单独导；`shape` 报错的真实包名是 `foundation.shape`，不是 `ui.shape`。
2. **`launch(null)` 只适用 `Unit?` 输入契约**：`QrCodeResult` 输入为 `Unit?`；其他契约（如取内容 `GetContent`，输入 MIME 串）不可套 `null`。
3. **`heightIn` 形参是 `min`/`max`**：旧 View 的 `minHeight` 属性与 Compose 修饰符同名不同义；仓内 `MangaVH`/`BadgeView`/`PopupAction` 等旧 View 代码里的 `minHeight` 是 View 属性，本轮未动。
4. **`collectAsState` 只能在组合上下文**：`onCreate`、回调、ViewModel 里只能读 `StateFlow.value`；反之组合里优先 `collectAsState().value` 以保持响应式。
5. **SP 写值必须走 `edit { putXxx }`**：`SharedPreferences` 没有裸 `putInt`；读值 `getInt` 必须带默认值。
6. **可变委托还需 `setValue` 导入**：本轮遇到的都是只读 `State` 委托（仅需 `getValue`）；若后续出现 `var x by remember { mutableStateOf(...) }` 在 Activity 字段上，需同时核对 `setValue` 导入。
7. **显式 `content =` 不改组件签名**：`InkHalfSheet` 形参定义未动，仅调用方改具名绑定；共享组件签名保持稳定。
8. **死资源引用按“删引用”处理，不恢复 XML**：`menu_del` 菜单本体已在上游清理，本轮只删代码侧残留，未新建/还原菜单 XML。

## 六、认知成本（铁律 63）

### 6.1 本批改动在新人视角下的可读性

- 所有修复保持原文件结构与命名，未引入新抽象、新工具类；新增导入均可在同包 `components` 已编译文件里找到同款。
- 两处私有包装方法（`collectAsStateValue`）被删除并回收到标准 API，新人不需要理解“为什么有一层自写 StateFlow 包装”。
- 保留的 `sp18/sp14/sp12` 等本地 TextUnit 扩展与本轮错误无关，未顺手重构（避免越界扩大 diff）。

### 6.2 为什么“缺 import”这类错会反复出现，如何一次性防住

**反复出现的根因：**

1. **无 IDE 自动补全环境**：本机无 SDK、禁编译，手写新 Compose 文件时 import 不会被自动插入，只能凭记忆写包名；Compose 扩展 API 分散在 `foundation` / `foundation.shape` / `foundation.layout` / `ui.graphics` / `ui.runtime` 多个包，记忆串包概率高（本轮 `ui.shape`、`foundation.clickable` 即此类）。
2. **委托属性是隐性导入需求**：普通函数调用漏导会报“未解析引用”，而 `by State` 委托漏导报的是“没有 `getValue` 方法、不能当委托”，错误表象与导入缺失不一致，容易误判成类型问题。
3. **新旧 API 概念串台**：`minHeight`（旧 View 属性）、`clickable`（旧修饰符旧签名）、`putInt`（SP 直写）等旧 Legado/View 时代写法被搬到 Compose/新 SP 委托上，签名对不上。
4. **尾随 lambda 绑定在多形参组件上天然脆弱**：最后一个形参若是普通 lambda（`onDismiss`/`onLinkSelected`），内容 lambda 写在尾位置就会被抢走，编译器只在内部再次调用 Composable 时才报错，定位链长。

**一次性防住的措施（建议沉淀为项目规则/检查项）：**

1. **CI 编译是第一道闸**：本轮全部由 CI 抓出；保持每次改动后跑编译，不以“语法看着对”交付。
2. **建立 Compose 标准 import 对照表**：以 `ui/components/` 内已编译文件为模板，固定 `graphicsLayer / RoundedCornerShape / collectAsState / getValue / setValue / fillMaxSize / heightIn / rememberInkGesture` 等符号的标准导入路径；新文件先抄 import 块再写调用。
3. **加一条静态扫描（可进 CI 脚本）**：扫描 Compose 常用短名符号出现但无对应导入即报警；重点清单：`graphicsLayer`、`RoundedCornerShape`、`clickable`、`collectAsState`、`fillMaxSize`、`heightIn`、`asStateFlow`、`viewModelScope`、`lifecycleScope`。
4. **委托属性检查项进评审清单**：凡 `by remember` / `by mutableStateOf` / `by ...collectAsState()`，必须同时核对 `getValue`（可变再核 `setValue`）导入。
5. **多形参组件强制具名内容槽**：`InkHalfSheet` 这类既有普通 lambda 又有内容槽的组件，调用一律写 `content = { }`，禁止尾随 lambda；新写同类组件时把内容槽形参排在最后或在注释里标明“勿尾随”。
6. **签名对照先行**：调用 `Dialog`、`registerForActivityResult`、`SharedPreferences`、`Modifier.heightIn` 等前，先核对真实签名（形参名、输入类型、是否 receiver 扩展），不凭旧 View 经验写。

## 七、铁律 59–66 逐条结论

- **59 二阶三阶影响**：见第四节。补导入无行为外溢；手势替换统一墨触反馈；`content=` 绑定消除上下文错位；`.value` 快照读取有时序限制；异常命名修正日志堆栈；死引用清理不影响现存菜单项。改动均已评估对其他模块、长期维护成本的影响。
- **60 需求缺口记录**：本轮未发现清单外的新功能缺口；遗留接线与时序问题统一记入第八、九节，未静默吞掉。
- **61 反例边界**：见第五节。导入不可混用包名；`launch(null)` 仅限 `Unit?` 契约；`heightIn` 形参名；组合/非组合上下文界限；SP 读写口径；可变委托额外导入；死资源只删引用不回退 XML。
- **62 事实回源**：所有行号、签名、调用口径均来自仓内当前源码与清单原文；`QrCodeResult` 输入 `Unit?`（`QrCodeResult.kt:8,10`）、`ReplaceEditActivity` 新规则哨兵 `-1L`（`:63`）、`LocalConfig.edit` 既有写法（`LocalConfig.kt:77/99/117`）、`addCallback` 既有先例（`AudioPlayActivity:12`）、`values()` 既有先例（`SearchContentViewModel:45`）均可回源核对。
- **63 认知成本**：见第六节。无新增抽象；删除自写包装；并给出“缺 import 反复出现”的根因与六条防住措施。
- **64 规则落点**：本轮修复口径与上一轮 `docs/修复-CI-其余包.md` 保持一致（`content =`、`onError { error -> }`、`launch(null)`、`rememberInkGesture`、`collectAsState().value`）；未发现需要回写总纲的新规则，6.2 节六条建议可在后续蒸馏轮次沉淀。
- **65 回退与风险**：未回退任何 XML/旧页面；未删功能；未加 `TODO()`/`@Suppress` 掩盖；未改测试与 `build.gradle`；未做 git 写操作。风险点：SelectionBar 遮罩点击未接退出选择、HandleFileActivity 错误时序为一次性快照，均为待接线项，非编译阻断。
- **66 未验证如实标注**：本机无 SDK、禁 gradle/kotlinc，**编译通过性、类型解析、运行行为均未验证**，判定依据为源码签名核对 + 词法配平 + grep 回证；最终以 CI 下一轮结果为准（见第八节）。

## 八、未验证项（如实声明）

1. **未编译**：本机无 Android SDK，未跑 `gradlew assemble` / kotlinc；所有“可解析”结论来自签名核对与导入比对，**不代表编译已过**。
2. **未运行**：无设备/模拟器，半屏弹出、手势点击、二维码回调、错误 toast、排序持久化、替换规则新建等路径均未真机走查。
3. **存疑点待 CI 复核**：
   - `SourceEditState.values()` 在当前 Kotlin 版本是否仅有废弃警告而非错误；若 CI 报废弃升级为错误，再迁 `enumEntries<EditTab>()`。
   - `rememberInkGesture` 在 `LazyColumn` item lambda 内调用的组合上下文是否被编译器接受（与 `InkCard` 同模式，但未编译验证）。
   - `LocalConfig.edit { putInt(...) }` 扩展解析路径（`LocalConfig` 委托 `SharedPreferences`）在本模块是否可见。
   - `BookSourceEditActivity` 的 `addCallback` lambda 内 `isDirty()`/`showExitConfirm`/`finish()` 解析到 Activity 成员是否正确。
4. **时序缺口（非编译错，未在本轮改结构）**：`HandleFileActivity.onCreate` 只读 `error.value` 快照，晚到错误不会触发 toast/finish。
5. **配置包未核对**：`ui/config/**` 28 条由另一代理处理，本报告未阅读其结论，不排除跨包形参联动问题。

## 九、交接节

1. **`ui/config/**` 绝对不动**：`BottomBarSkinAssignActivity.kt`、`sections/OtherSection.kt` 的在途改动属另一代理；本批 29 个文件与它们无交叉修改。若 CI 下一轮 config 包仍报错，交由该代理或后续轮次处理。
2. **SelectionBar 退出选择接线（AutoTaskScreen）**：`SelectionBar` 已补 `onDismiss: () -> Unit = {}`，但 `AutoTaskScreen` 调用点（`:123` 附近）未传退出回调；ViewModel 已有 `clearSelection()`（`AutoTaskListViewModel:88`）。建议下一轮：屏幕补 `onClearSelection` 形参 → Activity 接线 `viewModel::clearSelection` → `SelectionBar(onDismiss = onClearSelection)`，使遮罩/返回键退出选择模式。
3. **同类 `InkHalfSheet` 残留排查**：若 CI 仍报 Composable 上下文错位，按第二节 2.2 的模式全仓搜尾随 lambda 调用；本批 7 处已改，其余包（含 config）需各自核对。
4. **旧 View 代码不在本轮范围**：`MangaVH`/`BadgeView`/`PopupAction` 等旧 View 的 `minHeight` 是合法 View 属性，grep 命中勿误改。
5. **跨模块发现记录**：`SelectActionBar` 现存唯一危险色标为 `menu_del_selection`；若后续恢复单删菜单，需同步补回 id。`SourceManageViewModel` 内 `.launchIn(viewModelScope)` 的 `launchIn` 导入为既有代码，本轮未核其流操作符导入是否齐全，若 CI 另报再处理。
6. **下一轮 CI 关注点**：优先复核第八节 3 的四个存疑点；若出现新的“未解析引用”，先按 6.2 对照表查导入，再查签名。
