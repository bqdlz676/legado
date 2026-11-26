> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

# 修复 · CI `ui/config/**` 包编译错（run 45188065059 / job build，基线 HEAD da571c9）

> 任务范围：只修 `app/src/main/java/io/legado/app/ui/config/**` 内的编译错误；其它包 111 条错误一律未动（见末尾交接）。
> 本机无 Android SDK、禁跑 gradle / kotlinc / assemble，本轮全部为**静态自证**；是否真正编译通过，以下一轮云端 CI 为准。

> **口径已失效（2026-11-26，Q-280）**：旧关于页 `AboutActivity` 已整体删除，详见 `docs/修复-删除关于页.md`；本段保留为历史记录，勿据以施工。

## 1. 任务与范围

- 一手证据：`docs/CI日志-da571c9-错误清单.txt`（273 条去重）。其中前缀为 `app/src/main/java/io/legado/app/ui/config/` 的 **162 条**，分布在 15 个既有文件 + 本轮新增 1 个文件。
- 根因聚类（开工前按任务书三条假设逐条核实，结论如下）：
  1. **臆造 / 局部非法的包装类型**：`TextTarget` / `NumberTarget` / `ColorTarget` / `LanState` 被声明在 Composable 函数体内；Kotlin 局部类不允许 `sealed` / 局部 `object`，且局部类在 `remember { mutableStateOf<X?>() }` 之前前向引用解析不到 → 连带 `mutableStateOf` 类型参数推不出、`null` 不能赋给非空 `T`、后续 `.titleRes/.min/.max/.current` 全部级联报错。
  2. **作用域成员用错接收者**：`Modifier.matchParentSize()` 是 `BoxScope` 成员、`Modifier.weight()` 是 `RowScope/ColumnScope` 成员；调用点在 `Row` / 普通 `@Composable` lambda 内时接收者不对 → unresolved。
  3. **具名参数 / 尾随 lambda 咬合失败**：`InkSwitch(checked, enabled, onCheckedChange, modifier)` 的尾随 lambda 被绑到最后一个形参 `modifier`（`Modifier`），导致 `onCheckedChange` 缺失、`it` 推不出、`() -> Unit` 与 `Modifier` 类型错配；`MultiChoiceSheet` 同理，尾随 lambda 绑到了最后的 `onDismiss`。
  4. **偏好扩展缺 Context 接收者**：`putPrefBoolean/Int/String`、`removePref` 都是 `Context` 扩展，分区内裸调 → unresolved。
  5. **缺 import / 缺注解**：`@Composable`、`pointerInput`、`detectTapGestures`、`Box`、`dp` 等漏导；`detectTapGestures` 根本没有 `enabled` 形参（误传具名参数）。
  6. **其它**：VM 把 `internal` 类型 `LanBackupSession` 暴露进 public 返回值；`File.toUri()` 缺 `androidx.core.net.toUri` 导入；两个偏好键在 `PreferKey` 常量类里不存在（真实键是 `AppConfig` 读取的字面量串）；网格列表误用了 `LazyColumn.items(count: Int)` 重载；`R.string.save` 资源不存在。

## 2. 改动清单（文件 : 行 + before / after）

> 行号为本轮改后文件行号；before 引用 CI 基线（da571c9）原文。

### 2.1 `settings/SheetTargets.kt`（新增，23 行）

- 三处分区（备份 / 主题 / 其它）的数字盘待决目标字段完全同构，收拢为一处 `internal data class NumberTarget(titleRes, min, max, current, apply)`。
- `TextTarget` / `ColorTarget` / `LanState` 字段形态各不相同，未强行合并，分别留在各自文件做文件级 `private`（见 2.3 / 2.4 / 2.5）。

### 2.2 `settings/SettingControls.kt`（+31 / -12）

| 位置 | before | after | 治的错 |
|---|---|---|---|
| import L15 | 无 `RowScope` | 增 `import androidx.compose.foundation.layout.RowScope` | `weight` 作用域 |
| `SettingRowShell` L147-151 | `content: @Composable () -> Unit` | `content: @Composable RowScope.() -> Unit` | 槽内 `RowScope.weight` 可解析 |
| 行容器遮罩 Box L168 | `.matchParentSize()` | `.fillMaxSize()` | `matchParentSize` 非 `BoxScope` 成员，Row 内 unresolved |
| `RowTitleBlock` L177-183 | `Column(Modifier.weight(1f)…)`（函数无 RowScope 接收者） | 新增 `modifier: Modifier = Modifier` 形参，`Column(modifier = modifier.padding(end = 12.dp))`；weight 由调用方在 RowScope 传入 | 176:32 `Unresolved reference 'weight'` |
| 新增 `ColorSwatchBlock` L208-215 | 无 | 24dp 圆形色块样片（复用 `Color` / `RoundedCornerShape` / `clip`） | 主题颜色行右侧色块渲染 |
| `InkSwitch` 手势 L281 | `detectTapGestures(enabled = enabled) { onCheckedChange(!checked) }` | `detectTapGestures { if (enabled) onCheckedChange(!checked) }` | 263:35 `No parameter with name 'enabled'` |
| `rememberInkPress` 手势 L323-333 | `detectTapGestures(enabled = enabled, onPress = {…})` | 去掉 `enabled = enabled`；`onPress` 内 `if (enabled) { …tryAwaitRelease()… }` | 307:13 `No parameter with name 'enabled'` |

### 2.3 `settings/SettingList.kt`（+24 / -7）

| 位置 | before | after | 治的错 |
|---|---|---|---|
| import L16 | 无 `dp` | 增 `import androidx.compose.ui.unit.dp` | 57:77 `Unresolved reference 'dp'` |
| `SwitchRowItem` L72-79 | `InkSwitch(checked, enabled) { next -> … }`（尾随 lambda 绑到 `modifier`） | `InkSwitch(checked, enabled, onCheckedChange = { next -> … })` | 68:9 缺 `onCheckedChange`；68:61 `() -> Unit` 但 `Modifier` 期望；68:63 `next` 推不出；it unresolved |
| 各行 `RowTitleBlock` L71/86/94/107 | 未传 weight | 补 `modifier = Modifier.weight(1f)` | 标题列等分 |
| 新增 `ColorSwatchRowItem` L92-98 | 无 | 渲染 `ColorSwatchRow`：标题列 + 12dp 间隔 + 色块 | 主题颜色行接渲染器 |
| `when` 分支 L49/58 | 仅 4 种行 | 补 `ColorSwatchRow` 的 key 分支与渲染分支 | 密封 `when` 穷尽 |

### 2.4 `settings/SettingModels.kt`（+9 / -0）

- 新增 `data class ColorSwatchRow(key, title, summary, color: Int, onOpen: (String) -> Unit) : SettingRow`。
- 动机：主题分区原来把一个 `@Composable ColorRow{…}` 直接塞进 `listOf(...)`，得到 `List<Any>` 而非 `List<SettingRow>`（100:13 / 112:13）；行模型层没有色块槽位。新增密封子类型，色块仍由自绘 `ColorSwatchBlock` 渲染，未引 Material 组件、未砍色块功能。

### 2.5 `settings/SettingSheets.kt`（+1 / -1）

| 位置 | before | after | 治的错 |
|---|---|---|---|
| L208 | `InkSwitch(checked = state[index], enabled = true) { state[index] = it }` | `InkSwitch(…, onCheckedChange = { state[index] = it })` | 208:21 缺 `onCheckedChange`；208:71 `() -> Unit` 但 `Modifier`；208:88 `it` unresolved |

### 2.6 `sections/BackupSection.kt`（+63 / -28）

| 位置 | before | after | 治的错 |
|---|---|---|---|
| L57-58 / L291-302 | 函数体内 `data class TextTarget/NumberTarget`、`sealed class LanState`（局部 sealed / 局部 object 非法；前向引用 unresolved） | `NumberTarget` 改用 settings 共享类型；`TextTarget`、`LanState` 提升到文件级 `private`（`sealed interface` + `object` + `data class Qr`） | 56:49 / 57:51 `TextTarget/NumberTarget` unresolved；63:21/32/57 `LanState`；68:5 局部 sealed；69/70 局部 object；连带 `mutableStateOf` T 推不出、`null` 非空、`.key/.titleRes/.initial/.password/.min/.max/.current` 级联 |
| L87 `putPrefInt` | 裸调 | `context.putPrefInt(...)` | 95:25 `putPrefInt` unresolved |
| L106 `putPrefString` | 裸调 | `context.putPrefString(...)` | 109:66 `putPrefString` |
| L126 `putPrefString` | 裸调 | `context.putPrefString(...)` | 133:13 |
| L137-142 多选「备份内容」 | `MultiChoiceSheet(..., checked) { result -> … }`（尾随 lambda 绑到 `onDismiss`） | 显式 `onConfirm = { result -> … }`、`onDismiss = { contentMulti = false }` | 144:9 缺 `onConfirm`；144:112/116 lambda 类型错配；145:98/99 `!result[i]` 与 V? 推断 |
| L146-151 多选「恢复忽略」 | 同上 | 同上显式 `onConfirm/onDismiss`；取值 `?: false` 保持 Boolean | 153:9/112/114；154:97 V? 与 Boolean |
| L211 `lanPrepare` 回调 | `onSuccess { (_, bitmap) -> … }`（解构 internal 会话） | `onSuccess { bitmap -> … }`（VM 已只回吐 Bitmap） | 配合 VM 返回类型收窄 |
| `AutoBackupSheet` L237-273 | 无 `context`；`RowTitleBlock` 无 weight；`InkSwitch` 尾随 lambda；三个 `putPref*` 裸调 | 补 `val context = LocalContext.current`；`RowTitleBlock(..., modifier = Modifier.weight(1f))`；`InkSwitch(..., onCheckedChange = { enabled = it })`；`context.putPrefBoolean/Int(...)` | 246:46/91/103；262/263/264 候选不适用 |

### 2.7 `sections/ThemeSection.kt`（+17 / -40）

| 位置 | before | after | 治的错 |
|---|---|---|---|
| import L7/L29 | 缺 `Box`；无 `ColorSwatchRow` | 补 `androidx.compose.foundation.layout.Box`；导入 `settings.ColorSwatchRow`、共享 `NumberTarget` | 171:9 / 195:21 `Box` unresolved |
| L51-52 / L156 | 局部 `NumberTarget/ColorTarget` | `NumberTarget` 用共享类型；`ColorTarget` 提文件级 `private` | 51:51 / 52:50 unresolved；51:66 / 52:64 null 非空；连带 let/`titleRes/key/title` 级联 |
| 8 个颜色行 L98-101 / L110-113 | `ColorRow(...) { colorTarget = … }`（Composable 混进行模型列表） | `ColorSwatchRow(key, title, summary, context.getPrefInt(key, BLACK)) { colorTarget = … }` | 100:13 / 112:13 `List<Any>` 不能当 `List<SettingRow>` |
| 删除私有 `ColorRow`  Composable（原 159-178） | 自绘色块行 | 逻辑并入行模型 + `ColorSwatchBlock` 渲染；删去对 `SettingRowShell/Spacer/width` 的无用导入 | 同上；R63 去重 |
| L79 `putPrefInt` | 裸调 | `context.putPrefInt(...)` | 82:86 |
| L132 `putPrefInt` | 裸调 | `context.putPrefInt(...)` | 135:24 |
| L148-149 背景图 | 裸 `putPrefString/removePref`；`BackgroundImageSheet` 多收无用 `key` 形参 | `context.putPrefString/removePref`；删掉未用的 `key` 形参，回调闭包捕获 key | 152:67 / 153:25 |

### 2.8 `sections/OtherSection.kt`（+9 / -11）

| 位置 | before | after | 治的错 |
|---|---|---|---|
| L36 / L199 | 局部 `NumberTarget/TextTarget` | `NumberTarget` 用共享类型；`TextTarget` 提文件级 `private` | 局部类前向引用 |
| L92 | 裸 `removePref/putPrefString` | `context.removePref(...)` / `context.putPrefString(...)` | 97:42 / 97:79 |
| L97 / L153 | 裸 `putPrefString` | `context.putPrefString(...)` | 102:25 / 158:25 |
| L133 | `PreferKey.mediaButtonOnExit`（常量不存在） | 字面量 `"mediaButtonOnExit"`（与 `AppConfig.mediaButtonOnExit` 读取串一致） | 138:37 unresolved |
| L138 | `PreferKey.autoUpdateVariant`（常量不存在） | 字面量 `"autoUpdateVariant"`（与 `AppConfig.autoUpdateVariant` 一致） | 143:37 unresolved |

### 2.9 `sections/WelcomeSection.kt`（+3 / -3）

- L74 `putPrefInt` → `context.putPrefInt`；L86 `putPrefString` → `context.putPrefString`；L90 `putPrefString(key, null)` → `context.putPrefString(...)`。
- 治 74:27 `putPrefInt`；86:65 / 90:21 候选不适用（裸扩展无接收者导致）。

### 2.10 `sections/CoverSection.kt`（+1 / -8）

- L30 `putPrefString` → `context.putPrefString`（36:17）。
- 删掉只写不读的 `pendingImageKey` 状态及其赋值；清掉随之失效的 `getValue/mutableStateOf/remember/setValue/removePref` 导入。

### 2.11 `sections/CoverFontSection.kt`（+4 / -2）

- L81 `putPrefInt` → `context.putPrefInt`（81:27）。
- 局部 `data class SizeTarget` 从 Composable 函数体内提升到文件级 `private` 声明（局部 data class 在函数作用域内不被允许，且与其它分区的目标类型处置保持一致）；字段 `key/titleRes/current` 不变。

### 2.12 `ConfigViewModel.kt`（+12 / -10）

| 位置 | before | after | 治的错 |
|---|---|---|---|
| import L6 | 无 | 增 `import androidx.core.net.toUri` | 127:58 `File.toUri()` unresolved |
| `backup()` L75-85 | `runCatching { … }.getOrElse { … }`，块内 `backupLocked` 返回 Unit，推断成 `Any!` | `runCatching<Unit> { … }.fold(onSuccess = { null }, onFailure = { … })`，返回 `String?` | 75:9 返回类型 `String?` 但实参 `Any!` |
| `lanPrepare()` L113-118 | `Result<Pair<LanBackupSession, Bitmap>>`，public 函数泄漏 `internal class LanBackupSession` | 改为 `Result<Bitmap>`，会话仅在函数内部使用，二维码位图回吐 | 110:17 public 暴露 internal 返回类型实参 |
| 移除 `LanBackupSession` 导入 | — | 不再引用 | 同上 |

### 2.13 `BottomBarSkinActivity.kt`（+3 / -0）

- 补 `import androidx.compose.runtime.Composable`（160:2 unresolved；161:13 / 168-176 / 244-245 等一串「@Composable 调用必须在 @Composable 上下文」级联）。
- 补 `import androidx.compose.foundation.gestures.detectTapGestures`、`import androidx.compose.ui.input.pointer.pointerInput`（257:14 `pointerInput` unresolved；258:54 `detectTapGestures` unresolved）。

### 2.14 `BottomBarSkinAssignActivity.kt`（+10 / -8）

| 位置 | before | after | 治的错 |
|---|---|---|---|
| imports | 缺 `Composable` / `detectTapGestures` | 补齐 | 103:2、111-117、126、144-155、190、199、245-246、264-265 一串 Composable 上下文级联 |
| L133 | `io.legado.app.utils.appCtx.getString(...)`（`appCtx` 不在 utils 包） | `context.getString(...)` | 133:43 `appCtx` unresolved |
| L178 | `Thumb(row.selected) { … }` 缺 `enabled` 实参 | `Thumb(row.selected, enabled = true) { … }` | 178:17 缺 `enabled` |
| L193 / L149 / L135 | 非 Composable 回调里裸调 `toastOnUi`（Context 扩展，无接收者） | `context.toastOnUi(...)`（L135 图片素材为空提示、L149 保存结果回调、L192 未选槽位提示） | 190:21 / 149:54 / 135:38 候选不适用 |
| L217 网格 | `items(palette)` 解析到 `LazyListScope.items(count: Int)` | 全限定 `androidx.compose.foundation.lazy.grid.items(palette)` | 215:27 `List<File>` 但 `Int` 期望；225:68 / 227:68 `Int` 赋给 `File?`；233:29 键 `K` 推断失败 |
| L224 / L255 | 全限定 `androidx.compose.foundation.gestures.detectTapGestures` | 改用导入的短名 | 222:74 / 253:67 unresolved |
| L187 | `R.string.save`（资源不存在） | `R.string.bottom_bar_skin_save`（同为「Save」，域内既有资源） | 184:78 `save` unresolved |

### 2.15 `CoverRuleConfigDialog.kt` / `DirectLinkUploadConfig.kt`（各 +1 / -1）

- 两处 `InkSwitch(checked, enabled) { x = it }` 尾随 lambda 改为 `onCheckedChange = { x = it }`。
- 治 64:17/61/72 与 90:17/63/76 的「缺 `onCheckedChange` / `() -> Unit` 但 `Modifier` / `it` unresolved」三条一组。

## 3. grep 回证（改后真实输出）

### 3.1 共享数字盘目标类型

```
$ grep -n "NumberTarget" settings/SheetTargets.kt sections/OtherSection.kt
settings/SheetTargets.kt:12:internal data class NumberTarget(
sections/OtherSection.kt:14:import io.legado.app.ui.config.settings.NumberTarget
sections/OtherSection.kt:36:    var numberTarget by remember { mutableStateOf<NumberTarget?>(null) }
```

### 3.2 偏好扩展全部带 Context 接收者，无裸调残留

```
$ grep -rn "context\.putPref\|context\.removePref" sections/ | wc -l
18
$ grep -rn "[^.a-zA-Z]putPref\|[^.a-zA-Z]removePref" sections/ *.kt | grep -v import | grep -v "context\."
NONE
```

### 3.3 InkSwitch 全部走具名 `onCheckedChange`

```
$ grep -rn "onCheckedChange" .
CheckSourceConfig.kt:125:        InkSwitch(checked = checked, enabled = enabled, onCheckedChange = onChange)
CoverRuleConfigDialog.kt:64:                InkSwitch(checked = enable, enabled = true, onCheckedChange = { enable = it })
DirectLinkUploadConfig.kt:90:                InkSwitch(checked = compress, enabled = true, onCheckedChange = { compress = it })
sections/BackupSection.kt:261:                onCheckedChange = { enabled = it },
settings/SettingControls.kt:256:    onCheckedChange: (Boolean) -> Unit,
settings/SettingControls.kt:281:                detectTapGestures { if (enabled) onCheckedChange(!checked) }
settings/SettingList.kt:75:            onCheckedChange = { next ->
settings/SettingSheets.kt:208:                    InkSwitch(checked = state[index], enabled = true, onCheckedChange = { state[index] = it })
```

### 3.4 作用域修正：RowScope 接收者 / 遮罩 fillMaxSize / 残留 matchParentSize 仅存于 Box 内

```
$ grep -n "RowScope" settings/SettingControls.kt
15:import androidx.compose.foundation.layout.RowScope
145: *  content 带 [RowScope] 接收者，槽内才能用 [RowScope.weight] 等分标题列与右侧控件。 */
151:    content: @Composable RowScope.() -> Unit,
$ grep -n "matchParentSize\|fillMaxSize" settings/SettingControls.kt
17:import androidx.compose.foundation.layout.fillMaxSize
64:            .fillMaxSize()
87:        Box(modifier = Modifier.fillMaxSize()) { content() }
120:                    .matchParentSize()          // BackButton 内，BoxScope 合法，保留
168:                    .fillMaxSize()              // 行容器遮罩，Row 内
```

### 3.5 误传的 `enabled` 形参已从 `detectTapGestures` 清除

```
$ grep -rn "detectTapGestures(enabled" .
（无输出）
```

### 3.6 两个不存在的 PreferKey 常量已改为既有字面量键

```
$ grep -n "mediaButtonOnExit\|autoUpdateVariant" sections/OtherSection.kt
133:                SwitchRow("mediaButtonOnExit", …, true),
138:                SwitchRow("autoUpdateVariant", …, true),
```
对照 `AppConfig.kt:763` `getPrefBoolean("mediaButtonOnExit", true)`、`AppConfig.kt:1128` `getPrefBoolean("autoUpdateVariant", true)`，落盘串一致。

### 3.7 ViewModel 收窄

```
$ grep -n "runCatching<Unit>\|Result<Bitmap>\|toUri\|LanBackupSession" ConfigViewModel.kt
6:import androidx.core.net.toUri
75:        runCatching<Unit> {
113:    suspend fun lanPrepare(): Result<Bitmap> = runCatching {
129:            Restore.restoreOrThrow(appCtx, received.file.toUri(), lanTransfer = true)
（LanBackupSession 已无引用）
```

### 3.8 颜色行模型与渲染接线

```
$ grep -n "ColorSwatchRow\|ColorSwatchBlock" settings/SettingModels.kt settings/SettingList.kt
SettingModels.kt:52:data class ColorSwatchRow(
SettingList.kt:49:                    is ColorSwatchRow -> "color-${row.key}"
SettingList.kt:58:                    is ColorSwatchRow -> ColorSwatchRowItem(row)
SettingList.kt:92:private fun ColorSwatchRowItem(row: ColorSwatchRow) {
SettingList.kt:96:        ColorSwatchBlock(row.color)
```

### 3.9 本包改动行数（`git diff --numstat`，仅 `ui/config/**`）

```
3      0  ui/config/BottomBarSkinActivity.kt
10     8  ui/config/BottomBarSkinAssignActivity.kt
12    10  ui/config/ConfigViewModel.kt
1      1  ui/config/CoverRuleConfigDialog.kt
1      1  ui/config/DirectLinkUploadConfig.kt
63    28  ui/config/sections/BackupSection.kt
4      2  ui/config/sections/CoverFontSection.kt
1      8  ui/config/sections/CoverSection.kt
9     11  ui/config/sections/OtherSection.kt
17    40  ui/config/sections/ThemeSection.kt
3      3  ui/config/sections/WelcomeSection.kt
31    12  ui/config/settings/SettingControls.kt
24     7  ui/config/settings/SettingList.kt
9      0  ui/config/settings/SettingModels.kt
1      1  ui/config/settings/SettingSheets.kt
新增   ui/config/settings/SheetTargets.kt（23 行，untracked）
```
合计既有文件 **+189 / -132**，新增文件 23 行。

## 4. 二阶三阶影响推演（R59）

### 4.1 直接效果
- 本包 162 条编译错按根因消除：类型解析、作用域成员、具名参数、Context 接收者、缺导入、internal 泄漏六条线全部对齐到 settings 基础层与既有 utils 扩展的真实签名。

### 4.2 连带影响（点名文件 / 接口）
- **`SettingRowShell` 内容 lambda 改为 `RowScope.() -> Unit`**：唯一包外调用方是 `ui/about/AboutActivity.kt:239`。lambda 不显式声明接收者时可以忽略接收者，源码兼容；但其槽内若用到 `Modifier.weight`，改后才能解析（该处现状未用 weight，不破坏）。`RowTitleBlock` 新增的是末尾可选 `modifier` 形参，既有位置实参调用不受影响。
- **新增 `ColorSwatchRow` 密封子类型**：`SettingList` 的两处 `when` 已补齐分支；未来若再增行类型，必须同步 key 分支与渲染分支（已在模型类注释点明）。
- **`ConfigViewModel.lanPrepare` 返回类型由 `Pair<LanBackupSession, Bitmap>` 收窄为 `Bitmap`**：包内唯一调用方是 `BackupSection` 局域网发送回调，已同步改为单值解构；`LanBackupSession` 仍是 `internal`，只在 VM 内部与 `LanBackupTransfer` 之间流转，不再跨 public 边界。
- **`backup()` 改 `fold` 写法**：对外签名 `suspend fun backup(path: String?, upload: Boolean): String?` 不变，`BackupSection` 两处调用（仅本地 / 本地+WebDAV）不受影响。
- **两个偏好键改字面量串**：与 `AppConfig` 读取串逐字一致；`SwitchRow` 渲染层按 key 读写默认 prefs，旧已落盘的同名键值继续生效，不存在新旧键漂移。
- **`NumberTarget` 收拢到 settings**：`BackupSection / ThemeSection / OtherSection` 三处引用同一 `internal` 类型；字段未改，回调签名 `(Int) -> Unit` 不变。

### 4.3 二阶 / 三阶后果
- **维护成本**：行模型多一个子类型，渲染器多一个分支；换来的是颜色行不再把 Composable 混进数据列表，数据 / 视图分层更清楚。
- **用户行为**：颜色行仍显示 24dp 色块、点击仍开颜色盘；开关、多选、数字盘、文本盘的落盘键与旧版一致，无功能回退。
- **长期复杂度**：共享 `NumberTarget` 避免三份同构包装类复制粘贴；若后续再增「文本盘目标」「颜色盘目标」的跨分区复用，可继续往 `SheetTargets.kt` 收拢，而不是各写一份局部类。
- **潜在耦合**：settings 基础层（模型 / 渲染 / 控件）被 about 包复用，改其签名必须同步回看 `AboutActivity`；本轮只做了向后兼容的形参追加与接收者收窄，未破坏既有调用。

## 5. 跨领域对照（R60）

- **UI 框架作用域 receiver（本项目）** ↔ **React 的 Context / Vue 的 provide-inject**：`RowScope.weight` 只能在 Row 布局作用域内取到，类似子组件必须处在对应 Provider 子树才能消费 context。采纳：把作用域显式写成 lambda 接收者 `RowScope.() -> Unit`，而不是在普通 lambda 里硬调 `weight`；这与 Compose 官方 `RowScope` 设计一致。
- **密封行模型 ↔ **表格 / 表单引擎的列描述符**：每一行是一个不可变数据描述，渲染器按类型分发；新增行类型等价于注册一个新列描述符。采纳：颜色行建模为 `ColorSwatchRow` 而非在列表里塞 Composable，保持「描述数据 → 统一渲染」单向流。
- **不采纳的外域方案**：未引入 Material `Switch/ListItem/Card`（贴皮禁令），也未用 XML Preference 树；色块、开关仍走 foundation 原语自绘。

## 6. 最强反对者 · 反例与边界（R61）

1. **空值 / 缺省**：
   - 颜色键读不到时回退 `Color.BLACK`（`context.getPrefInt(key, Color.BLACK)`）；`getPrefString` 缺省走各分区既有 `?: 缺省文案`；数字盘 `current.coerceIn(min, max)` 在 `NumberPickerSheet` 内兜底。
   - 否证检验：若某颜色键从未写入，色块显示黑色样片、点开仍可重选，不崩。
2. **偏好键缺失 / 同名消歧**：`mediaButtonOnExit`、`autoUpdateVariant` 在 `PreferKey` 无常量，已核对 `AppConfig` 读取的字面量串逐字一致；若将来 `AppConfig` 改串，这里要同步（已在交接注明）。
3. **多选回传空数组 / 条目数不齐**：`MultiChoiceSheet` 回 `BooleanArray`，`contentKeys/ignoreKeys` 与标题数组一一对应（`BackupConfig` 内同序）；`forEachIndexed` 按下标写回，缺项取 `false` 兜底。
4. **局域网二维码生成失败**：`lanPrepare` 内 `QRCodeUtils.createQRCode(...) ?: throw IllegalStateException`，失败走 `onFailure` → `actions.toast`，UI 回到菜单态，不卡在空二维码。
5. **图片选择取消**：`pickImage/pickFolder` 回调 `uri == null` 时不写 prefs；文本盘空白提交走 `ifBlank { null }` / 清空分支。
6. **最强反例假设**：「把 `SettingRowShell` 内容改成 RowScope 会不会让非 Row 场景编译失败？」——本包所有 `SettingRowShell` 调用点的宿主都是 `Row`（行容器内部就是 Row），包外 `AboutActivity` 同理；接收者收窄不要求 lambda 声明接收者，普通 lambda 仍可传入。若未来有 Column 宿主复用 `SettingRowShell`，weight 仍需在 ColumnScope 下评估，届时再分拆。
7. **未验证边界**：以上边界均为静态推演，未真机 / 未跑编译；空数组、断网、超大清单等运行态行为以下一轮 CI + 真机为准。

## 7. 历史包袱审计（R62）

- `git log --follow`：本包 settings 与 sections 文件均来自单次重写提交 `93e1946 feat(config): 设置13+关于7 页 Compose 原生重写`，不是 Legado 旧界面遗留；本轮错误是**首写签名咬合偏差**，不是为已不存在的问题保留的旧路径。
- **有意为之（保留）**：偏好落盘仍走既有 `putPref*` / `AppConfig` / `BackupConfig` 数据层；弹层复用 `InkBottomSheetHost`；帮助类（`Backup/AppWebDav/LanBackupTransfer/ThemeConfig/BookCover`）不动。
- **历史遗留 / 可清理**：`CoverSection` 只写不读的 `pendingImageKey` 状态已删；`ThemeSection` 未用的 `key` 形参与失效导入已清。
- **未发现**为兼容旧 XML / 旧 Binding 而留的贴皮层；本包无旧 `*Binding` / Adapter 引用。

## 8. 新人认知成本（R63）

- 改动后接手者需要知道的概念：① 行模型 5 个子类型（Switch/Value/List/ColorSwatch/GroupHeader）在 `SettingModels.kt`；② 待决弹层目标 `NumberTarget` 在 `settings/SheetTargets.kt`，分区私有目标（Text/Color/Lan/Size）各自文件级；③ 行容器内容带 `RowScope`，weight 必须在 Row 调用点传 modifier；④ 偏好写盘必须带 `context` 接收者。
- 复杂度控制：未新增第二层抽象；`NumberTarget` 是三份同构类的收拢，不是新机制。`RowTitleBlock` 的 modifier 形参是 Compose 通行写法。
- 仍偏高的点：`BackupSection` 271 行、`ThemeSection` 207 行，状态目标 + 弹层编排挤在一个 Composable；本轮不做结构重构（R57 一单缺陷一单），建议后续把「弹层待决状态」抽成分区级 hook。

## 9. 最慢环节（R64）

- 本轮不跑构建，无本机时长数据；按既有结论：CI 单次 clean 全量编译占大头，设置包属 app module 主源码，随整包编译。
- 静态自检中最慢的一步是逐行核对 162 条报错与真实签名（settings 基础层 + utils 扩展 + AppConfig 键串）；可自动化项：在 CI 加一道「Compose 控件调用形参名 / 接收者」静态校验（见 10）。

## 10. 返工复盘与沉淀（R65）

- 本轮根因聚簇：
  - **契约漂移 / API 存在性**：臆造 `TextTarget/NumberTarget/ColorTarget`、误传 `detectTapGestures(enabled=…)`、引用不存在的 `PreferKey.mediaButtonOnExit/autoUpdateVariant`、`R.string.save`。预防：新增包装类型前先查 settings 模型层；调手势 / 控件前先对一遍真实签名。
  - **作用域接收者错配**：`matchParentSize/weight` 用错 scope。预防：布局修饰符按所属 scope 归类 review。
  - **尾随 lambda 绑错形参**：`InkSwitch/MultiChoiceSheet` 末位形参不是回调导致 lambda 绑到 Modifier。预防：多形参 Composable 回调一律具名传参。
  - **Context 扩展裸调**：`putPref*/removePref` 漏接收者。预防：utils 扩展调用 review 清单固定一条「是否 Context/View 扩展」。
- 沉淀建议（交总控评估是否落成脚本 / Skill）：
  1. 编译期规则：Composable 函数最后一个非 Modifier 形参为回调时，调用处尾随 lambda 给警告；
  2. grep 门禁：`detectTapGestures(enabled`、裸 `putPref`、`PreferKey.` 后接不存在常量名，提交前静态扫描；
  3. 行模型新增子类型时，强制同步 `SettingList` 两处 `when`。

## 11. 铁律 59–66 逐条结论

- **R59 二阶三阶推演**：已写第 4 节，三层（直接效果 / 连带文件接口 / 用户行为与长期维护）齐备，点名了 `AboutActivity`、`SettingList`、`BackupSection`、`AppConfig` 等受影响方。
- **R60 跨领域对照**：已写第 5 节，对照作用域 receiver↔Context、行模型↔列描述符两个外域方案，并说明不采纳 Material/XML 的理由。
- **R61 先自我否证**：已写第 6 节，列 7 条边界（空值、缺键、多选空数组、二维码失败、选图取消、RowScope 反例、未验证边界），每条给兜底与否证结论。
- **R62 历史包袱审计**：已写第 7 节，`git log` 定性为单次重写首写偏差；分有意保留 / 已清理 / 无贴皮层三类。
- **R63 认知成本**：已写第 8 节，列出接手者需知概念、新增抽象数、仍偏高的两个长文件及后续建议。
- **R64 最慢环节**：已写第 9 节，给出 CI 编译占大头、静态核对最慢两步，可自动化项一条。
- **R65 返工复盘沉淀**：已写第 10 节，按契约漂移 / 作用域 / lambda 绑定 / 扩展接收者四类聚簇，各给预防手段与三条建议门禁。
- **R66 开工前必读**：已读 `docs/机制-子代理必读.md` 与总纲 R59–66 全文；报告含影响 / 边界 / 认知成本三节，前置阅读齐备。

## 12. 未验证项（明示，不得以「应该没问题」代替）

1. **未编译**：本机无 Android SDK，未跑 gradle / kotlinc / assemble；以上全部为静态阅读 + grep + 括号配平自检（15 个改动文件花括号 / 圆括号均配平）。能否在云端编译通过，**以下一轮 CI run 为准**。
2. **未跑单测 / 真机**：设置页交互、弹层开合、落盘读写、局域网二维码流程均未运行验证。
3. **未能核对的签名**：`BottomBarSkinManager` 各方法、`AppWebDav/Restore/ImportOldData` 等帮助类签名本轮只按既有引用比对，未逐个反编译确认；若下一轮 CI 仍报这些符号，属本包调用方与帮助类之间的新一轮咬合问题。
4. **资源 / 数组**：引用的 `R.array.*`、`R.string.*` 已抽样核对存在；未逐字符串穷举。
5. **`AutoBackupSheet` 天数步进钮**：现状点击仅 `coerceIn(1,365)` 不改变数值（旧稿如此，非本轮引入的编译错），本轮未改其交互逻辑，留待后续交互修正单。

## 13. 交接（发现但未改，越界不动）

- **其它包 111 条编译错**（不在 `ui/config/**`），按 CI 清单聚类（已逐条对账，合计 111）：
  - `ui/book/source` 40 条。
  - `ui/replace` 18 条：`edit` 11、`ReplaceRuleScreen` 3、`ReplaceRuleListViewModel` 3、`ReplaceRuleActivity` 1。
  - `ui/dict` 11 条：`rule` 9、`DictDialog` 2。
  - `ui/about` 19 条：`AboutActivity` 9、`AboutInkActivity` 4、`ReadRecordActivity` 2、`AppLogDialog` 2、`UpdateDialog` 1、`CrashLogsDialog` 1。
  - `ui/file` 7 条：`HandleFileViewModel` 3、`HandleFileActivity` 2、`FilePickerDialog` 2。
  - `ui/autoTask` 12 条：`ImportAutoTaskDialog` 3、`AutoTaskScreen` 3、`AutoTaskListViewModel` 2、`AutoTaskDebugActivity` 2、`ImportAutoTaskViewModel` 1、`AutoTaskEditScreen` 1。
  - `ui/components` 3 条：`ToolScaffold` 2、`HandleFileSheet` 1。
  - `ui/book/read` 1 条。
  - 建议按包各派一单；其中 `ui/components/ToolScaffold.kt`（2 条）被 config 包间接复用，若其 `InkSwitch` 签名变动，需回看本包调用。
- **跨包耦合提示**：
  - `AboutActivity.kt:239` 复用 `SettingRowShell/RowTitleBlock`，本轮签名向后兼容；若 about 包修复时改其槽内布局，注意 RowScope 接收者。
  - `AppConfig.mediaButtonOnExit` / `autoUpdateVariant` 若将来改读取的字面量键，`OtherSection.kt:133/138` 两处需同步。
- **工作区既有未提交改动**：`ui/about`、`ui/book/source`、`ui/main/DetailSheet.kt`、`app/src/test/**` 等文件在本轮开工前已有他人改动，本轮未读未改未提交。
- **禁令遵守**：未改测试、未动 `build.gradle`、未做任何 git 写操作（无 commit / push / revert / stash / checkout）；未删功能、未留 `TODO()`、未用 `@Suppress` 掩盖、未把 Compose 换回 XML。
