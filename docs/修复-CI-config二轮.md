# 修复 · CI `ui/config/**` 二轮残余编译错（run 45192886781，基线 HEAD 54bd363）

> 任务范围：只清 `app/src/main/java/io/legado/app/ui/config/**` 内二轮暴露的 **25 条**编译错——
> `sections/OtherSection.kt` 22 条、`BottomBarSkinAssignActivity.kt` 3 条。
> 本机无 Android SDK，**禁跑 gradle / kotlinc / assemble**；本轮全部为静态阅读 + grep + 签名比对，能否在云端编译通过**以下一轮 CI 为准**。
> 一手证据：`docs/CI日志-54bd363-错误清单.txt`（全量 79 条去重）；云端原始日志已用 API 取回核对（job 137755592411）。

## 1. 任务与范围

### 1.1 清单对账（前缀 `ui/config/` 共 28 条，本单 25 条，其余 3 条见第 7 节交接）

| 文件 | CI 条数 | 归属 |
|---|---|---|
| `sections/OtherSection.kt` | 22 | 本单 |
| `BottomBarSkinAssignActivity.kt` | 3 | 本单 |
| `BottomBarSkinActivity.kt:261:54` | 1 | 交接（FQN 扩展 unresolved） |
| `sections/BackupSection.kt:291:20` | 1 | 交接（与本单同名互撞，预计随本单改名间接受益） |
| `sections/ThemeSection.kt:202:46` | 1 | 交接（FQN 扩展 unresolved） |

### 1.2 两条根因（开工前按任务书假设逐条核实，云端日志佐证）

**根因 A：同包两个顶层 `private` 同名类 → Redeclaration，连带跨文件私有访问 + 构造器签名错位级联（OtherSection 22 条）。**

第一轮（commit 54bd363，`docs/修复-CI-config包.md` §2.6/§2.8）把两个函数体内的局部 `data class TextTarget` 提升到文件级 `private`：

- `BackupSection.kt:291`：`private data class TextTarget(key: String, titleRes: Int, initial: String?, password: Boolean)`（按偏好键回写型）。
- `OtherSection.kt:199`（改前）：`private data class TextTarget(titleRes: Int, initial: String?, apply: (String) -> Unit)`（回调回写型）。

二者同在包 `io.legado.app.ui.config.sections`。Kotlin 规则：**可见性管「能否访问」，身份管「能否重名」**——同包两个顶层声明即便都标 `private`，同名仍按 Redeclaration 冲突（Kotlin 官方讨论 "Redeclaration clash with private classes" 与同型工程 PR 均证实此行为）。冲突后编译器消掉其中一份声明，OtherSection 内的引用回落到 BackupSection 的那份 `private` 类上，于是：

- `Cannot access 'data class TextTarget': it is private in file`（跨文件访问 file-private）；
- 实参被按 BackupSection 的 4 参构造器 `(key: String, titleRes: Int, initial: String?, password: Boolean)` 匹配 → `No value passed for parameter 'initial'`、Int↔String 互错、尾随 lambda 被绑到 `password: Boolean`、`v` 类型推不出；
- `target.apply` 在没有 `apply` 属性的那份类型上回落到 `Any.apply` 扩展 → `Function invocation 'apply(...)' expected` / `No value passed for parameter 'block'`。

**根因 B：扩展函数用全限定名（FQN）调用，扩展不进候选集（AssignActivity 3 条）。**

第一轮报告 §2.14 把网格列表写成全限定 `androidx.compose.foundation.lazy.grid.items(palette)`，以为 FQN 可绕开导入。按 Kotlin 重载解析规范：**全限定调用（完整包路径）的候选集只含该包下的普通顶层 callable；扩展 callable 必须先 import 进作用域、再按接收者匹配**。`grid.items` 是 `LazyGridScope` 的扩展，FQN 写法不携带扩展接收者匹配，故 `Unresolved reference 'items'`；`items` 一旦 unresolved，尾随 lambda 的 `file` 推不出类型，lambda 也不再被当作 `@Composable` 槽位 → 后两条级联。

## 2. 改动清单（文件 : 行 + before / after）

### 2.1 `sections/OtherSection.kt`（+12 / -7）

| 改后行号 | before | after | 治的 CI 错 |
|---|---|---|---|
| 39 | `var textTarget by remember { mutableStateOf<TextTarget?>(null) }` | `var textTarget by remember { mutableStateOf<TextEditTarget?>(null) }` | `:39:49 Cannot access ... private in file` |
| 91 | `textTarget = TextTarget(R.string.user_agent, AppConfig.userAgent) { v ->` | `textTarget = TextEditTarget(R.string.user_agent, AppConfig.userAgent) { v ->` | `:91:34` 缺 `initial`；`:91:45` Int→String；`:91:66` String→Int；`:91:87` lambda→Boolean；`:91:89` 推不出 `v` |
| 96 | `textTarget = TextTarget(R.string.custom_hosts, AppConfig.customHosts) { v ->` | `textTarget = TextEditTarget(R.string.custom_hosts, AppConfig.customHosts) { v ->` | `:96:34` 缺 `initial`；`:96:45` Int→String；`:96:68` String?→Int；`:96:91` lambda→Boolean；`:96:93` 推不出 `v` |
| 152 | `textTarget = TextTarget(R.string.js_source_api_token_title, context.getPrefString(PreferKey.jsSourceApiToken)) { v ->` | `textTarget = TextEditTarget(R.string.js_source_api_token_title, context.getPrefString(PreferKey.jsSourceApiToken)) { v ->` | `:152:34` 缺 `initial`；`:152:45` Int→String；`:152:81` String?→Int；`:152:132` lambda→Boolean；`:152:134` 推不出 `v` |
| 186 / 187 / 189 | 弹层消费 `target.titleRes` / `target.initial` / `target.apply`（类型回落到 BackupSection 私有类） | 类型解析回本分区 `TextEditTarget`，三处属性访问随之恢复 | `:186:38`、`:187:30` private in file；`:189:25` TextTarget 但 `(String) -> Unit`；`:189:32` apply 调用期望 / 缺 `block` |
| 199（改后 198–204） | `private data class TextTarget(val titleRes: Int, val initial: String?, val apply: (String) -> Unit)` | `private data class TextEditTarget(val titleRes: Int, val initial: String?, val apply: (String) -> Unit)` + 命名避让注释 | `:199:20 Redeclaration` |

**选型说明（为何改名而不是收拢成一个共享 internal 类）：**

- 两类形态职责不同：Backup 型 `(key, titleRes, initial, password)` 按偏好键回写 + 密码掩码；本分区型 `(titleRes, initial, apply)` 回调回写。收拢成一个带 `key?/apply?/password` 可空字段的宽类，是把两套职责压进一个模型，违背 R63 单一职责。
- 本单文件域不含 `BackupSection.kt`（见第 7 节），不越界改它；改名只动 OtherSection 一个文件，爆炸半径最小。
- 可见性维持 `private`（文件级），**没有把任何类型改成 public/internal 扩大暴露面**；全仓消费点见 §3.1。

### 2.2 `BottomBarSkinAssignActivity.kt`（+2 / -1）

| 改后行号 | before | after | 治的 CI 错 |
|---|---|---|---|
| 22 | （无 grid 扩展导入；调用处写 FQN） | 增 `import androidx.compose.foundation.lazy.grid.items as gridItems` | `:217:59 Unresolved reference 'items'` |
| 218 | `androidx.compose.foundation.lazy.grid.items(palette) { file ->` | `gridItems(palette) { file ->` | `:217:76` 推不出 `file`；`:218:25` @Composable 上下文级联 |

**为何用别名 `gridItems` 而不是裸导入 `grid.items`：** 同文件 172 行已有列表作用域的 `androidx.compose.foundation.lazy.items`（`items(rows.size)`，LazyColumn）。两个同名扩展分属 `LazyListScope` / `LazyGridScope`，靠接收者重载区分；为消除任何同名重载 / 冲突导入的二义性（本地不能编译验证，取最稳写法），grid 侧用 `as gridItems` 别名导入。172 行列表 `items` 不动。

## 3. grep 回证（改后真实输出）

### 3.1 `TextTarget` 全仓消费点清单 + 最终可见性

```
$ grep -rn "TextTarget" app/src/main/java/
app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt:57:    var textTarget by remember { mutableStateOf<TextTarget?>(null) }
app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt:72:                    textTarget = TextTarget(PreferKey.webDavUrl, R.string.web_dav_url, context.getPrefString(PreferKey.webDavUrl), false)
app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt:75:                    textTarget = TextTarget(PreferKey.webDavAccount, R.string.web_dav_account, context.getPrefString(PreferKey.webDavAccount), false)
app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt:78:                    textTarget = TextTarget(PreferKey.webDavPassword, R.string.web_dav_pw, context.getPrefString(PreferKey.webDavPassword), true)
app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt:81:                    textTarget = TextTarget(PreferKey.webDavDir, R.string.sub_dir, AppConfig.webDavDir, false)
app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt:84:                    textTarget = TextTarget(PreferKey.webDavDeviceName, R.string.webdav_device_name, AppConfig.webDavDeviceName, false)
app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt:291:private data class TextTarget(
app/src/main/java/io/legado/app/ui/config/sections/OtherSection.kt:38:    // 文本编辑弹层目标（回调回写型；与 BackupSection 按偏好键回写的 TextTarget 同名会在包级 Redeclaration，故本分区用独立名）
app/src/main/java/io/legado/app/ui/config/sections/OtherSection.kt:201: * 命名避开同包 BackupSection 内按偏好键回写的 `TextTarget`：
```

- 最终可见性：`BackupSection.TextTarget` 仍为文件级 `private`（本单未改）；本分区新类 `OtherSection.TextEditTarget`（204 行）文件级 `private`。**两类型均未跨文件暴露，无 public/internal 新增。**
- 除上述两个文件外，`app/src/main`、`app/src/test`、`app/src/androidTest` 无任何 `TextTarget` 引用（已全仓 grep，非 main 源码集零命中）。
- 本分区 `TextEditTarget` 落点（改名后无旧名残留）：

```
$ grep -n "TextEditTarget\|TextTarget" sections/OtherSection.kt
38:    // ...与 BackupSection ... TextTarget 同名...（注释）
39:    var textTarget by remember { mutableStateOf<TextEditTarget?>(null) }
91:                    textTarget = TextEditTarget(R.string.user_agent, AppConfig.userAgent) { v ->
96:                    textTarget = TextEditTarget(R.string.custom_hosts, AppConfig.customHosts) { v ->
152:                    textTarget = TextEditTarget(R.string.js_source_api_token_title, context.getPrefString(PreferKey.jsSourceApiToken)) { v ->
201: * 命名避开同包 BackupSection ... `TextTarget`：（注释）
204:private data class TextEditTarget(val titleRes: Int, val initial: String?, val apply: (String) -> Unit)
```

### 3.2 被调组件真实签名摘录（settings 基础层，未改组件签名去迁就调用）

`settings/SettingSheets.kt`（改前改后均以此为准，本轮未触碰该文件）：

```
fun NumberPickerSheet(
    visible: Boolean,
    title: String,
    min: Int,
    max: Int,
    current: Int,
    unit: String? = null,
    defaultLabel: String? = null,
    onDefault: (() -> Unit)? = null,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
)                                            // SettingSheets.kt:40-51

fun TextEditSheet(
    visible: Boolean,
    title: String,
    initial: String?,
    hint: String,
    password: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
)                                            // SettingSheets.kt:98-106
```

改后本分区调用行号与实参咬合：

```
OtherSection.kt:184  TextEditSheet(
OtherSection.kt:185      visible = true,
OtherSection.kt:186      title = stringRes(target.titleRes),
OtherSection.kt:187      initial = target.initial,
OtherSection.kt:188      hint = "",
OtherSection.kt:189      onConfirm = target.apply,        // TextEditTarget.apply: (String) -> Unit
OtherSection.kt:190      onDismiss = { textTarget = null },
```

- `target.apply` 解析为 `TextEditTarget` 的成员属性（非 `Any.apply` 扩展）；同文件 179 行 `NumberTarget.apply` 是同款写法，上一轮 CI 未对该行报错，作为成员属性压过扩展的先例。
- 网格扩展签名（Compose foundation `LazyGridScope.items(items: List<T>, ..., itemContent)`）：本仓 `BottomBarSkinActivity.kt:208` 已有 `items(names) { name -> }` 的同款 List 重载在用且 CI 未报 items 错，作为签名存在性的仓内先例；未反编译 jar 逐形参核对（见 §6 未验证项）。

### 3.3 本单改动行数（`git diff --numstat`，仅本单两个文件）

```
2      1  app/src/main/java/io/legado/app/ui/config/BottomBarSkinAssignActivity.kt
12     7  app/src/main/java/io/legado/app/ui/config/sections/OtherSection.kt
```

合计 **+14 / -8**，涉及 2 个文件；无新增文件。

### 3.4 禁令自查

```
$ grep -n "TODO()\|@Suppress" OtherSection.kt BottomBarSkinAssignActivity.kt
（无输出）
```

未删功能、未留 `TODO()`、未用 `@Suppress` 掩盖、未回退 XML；未改测试、未动 `build.gradle`；未做任何 git 写操作（无 add / commit / push / revert / stash / checkout）。

## 4. 二阶三阶影响推演（R59）

### 4.1 直接效果
- OtherSection 内文本盘目标类型解析回本分区私有类，22 条 private 访问 / 构造器错位 / lambda 类型级联全部消除。
- AssignActivity 网格素材列表的 `items` 扩展进入 `LazyGridScope` 候选集，3 条 unresolved / 类型推断 / Composable 上下文级联消除。

### 4.2 连带影响（点名文件 / 接口）
- **`BackupSection.kt`（未改文件）**：其 `TextTarget` 是撞名对端；本单把 OtherSection 侧改名后，同包不再有同名顶层声明，`BackupSection.kt:291:20 Redeclaration` 预计随之消失。该结论为静态推演，**需下一轮 CI 复验**；Backup 型 `(key, titleRes, initial, password)` 与本分区型字段互不引用。
- **`settings/SettingSheets.kt`**：`TextEditSheet` / `NumberPickerSheet` 签名未动；本单只是调用方按既有签名对齐。
- **`settings/SheetTargets.kt`**：共享 `NumberTarget` 不动；本单未把文本盘目标上收共享（理由见 §2.1）。
- **`BottomBarSkinManager` / `BottomBarSkinAssignViewModel`**：方法签名（`stagingImages/previewBitmap/buildPrefill/discardSession/SlotAssign`、`saveResult/isSaving/consume/save`）已逐个 grep 核对存在且咬合，本轮未改。
- **包外消费**：两个文件均为 `private` 顶层类 / `private` Composable，无包外引用面；改名不影响任何外部调用方。

### 4.3 二阶 / 三阶后果
- **维护成本**：包内私有包装类从此需要「包内唯一命名」这条约定（见 §5 边界 2 与 §8 沉淀规则）；新增分区目标类时，要么进 `SheetTargets.kt` 做 `internal` 共享，要么带分区前缀，不能再直接复用 `TextTarget` 这类通名。
- **用户行为**：三个文本盘入口（User-Agent、自定义 hosts、JS 源 API Token）打开弹层、回写偏好键的行为与第一轮设计一致；网格图集选择交互不变。
- **长期复杂度**：没有引入新抽象层；`TextEditTarget` 只是改了名的文件级包装类。若后续第三个分区再出现「回调型文本盘」，可考虑把 `titleRes/initial/apply` 三件套上收 `SheetTargets.kt` 与 `NumberTarget` 并列，目前不复刻合并。

## 5. 最强反对者 · 反例与边界（R61）

1. **「改名是绕开问题，正解应是收拢共享 internal 类」**：否证检验——两类字段不同构（key+password vs apply 回调），强行合并会引入可空可选字段与分支判断；且收拢必须改 `BackupSection.kt`，超出本单文件域。结论：改名在本域约束下成立；共享收拢留给后续统一整理单评估。
2. **「同包 private 同名到底算不算冲突？万一编译器其实允许，改名是多余动作」**：反例证据——云端 CI 原文两条 `Redeclaration: data class TextTarget : Any`（BackupSection:291:20、OtherSection:199:20）互相指向；Kotlin 官方讨论区与同型工程 PR 均记录此行为。改名消除重名身份，两种编译器口径下都不会更差。
3. **别名导入 `gridItems` 会不会与列表 `items` 在其它调用点产生新歧义？**：别名只在 218 行网格槽位使用；172 行列表槽位仍用裸 `items`。两个作用域（`LazyListScope` / `LazyGridScope`）物理隔离，别名不参与列表侧解析。边界：若本文件将来再用 `grid.itemsIndexed`，需另起别名，不要把裸 `grid.items` 与列表 `items` 同时导入。
4. **空素材清单 / 空值**：`palette` 为空时 `gridItems(emptyList())` 渲染空网格，不崩（与改前同一空态路径）；`initial` 为 `String?`，`TextEditSheet` 内 `remember(initial) { mutableStateOf(initial ?: "") }` 已兜空串；User-Agent 空白提交走 `if (v.isBlank()) removePref(...)` 分支，hosts / token 提交空串按既有回写路径。
5. **同名消歧失败 / 资源缺失**：本单未新增 `R.string.*` / `R.array.*` 引用；沿用第一轮已核对的既有资源。
6. **最强反例假设**：「`target.apply` 仍可能被解析成 `Any.apply` 扩展」——成员属性优先级高于扩展函数；同文件 179 行 `NumberTarget.apply` 同款写法上一轮 CI 未报错，可作仓内先例。若下一轮 CI 仍报 apply 相关错，再把字段改名为 `onConfirm` / `onApply` 消歧（本轮未预先改，避免过度调整）。
7. **未验证边界**：以上均为静态推演；空清单、断网、超长文本、图集解码失败等运行态行为未真机 / 未编译，以下一轮 CI + 后续真机为准。

## 6. 未验证项（明示，不以「应该没问题」代替）

1. **未编译**：本机无 Android SDK，未跑 gradle / kotlinc / assemble；本轮为静态阅读 + grep + 签名比对 + 括号配平自查。能否在云端编译通过，**以下一轮 CI run 为准**。
2. **未跑单测 / 真机**：三个文本盘开合、回写、图集网格选择、保存流程均未运行验证。
3. **网格扩展形参未逐形参反编译核对**：`LazyGridScope.items(List<T>, ...)` 签名按仓内 `BottomBarSkinActivity:208` 先例与 Compose 公开 API 比对；未在本地 foundation jar 中逐形参确认默认参数。
4. **`BackupSection.kt:291` Redeclaration 的消除是静态推断**：本单未改该文件；需下一轮 CI 确认其报错随撞名对端消失。
5. **工作区存在他人并发改动**：开工时 `git status` 与本单收尾时，均有 `ui/about`、`ui/autoTask`、`ui/book/source` 等包及其它 main 源码文件的未提交改动（非本单产物），本轮未读未改未提交；本单 numstat 仅统计 §3.3 两个文件。

## 7. 交接（发现但未改，越界不动）

### 7.1 同包其余 3 条 config 残余（建议另派 / 并入相邻切片）

| 位置 | CI 原文 | 静态根因 | 建议改法（未代改） |
|---|---|---|---|
| `BottomBarSkinActivity.kt:261:54` | `Unresolved reference 'detectTapGestures'` | 261 行在 `pointerInput(Unit) { }` 内用 FQN `androidx.compose.foundation.gestures.detectTapGestures(...)` 调用；该符号是 `PointerInputScope` 扩展，FQN 不进扩展候选集。文件第 10 行已有短名导入 | 把 261 行 FQN 改为短名 `detectTapGestures(...)`，复用既有导入 |
| `ThemeSection.kt:202:46` | `Unresolved reference 'detectTapGestures'` | 同上：`private fun Modifier.pointerTap` 内 FQN 调用；第 5 行已有短名导入 | 202 行改短名 `detectTapGestures(onTap = { onTap() })` |
| `BackupSection.kt:291:20` | `Redeclaration: data class TextTarget : Any` | 与本单改名前的 OtherSection 同名互撞 | 预计随本单改名间接受益；下一轮 CI 若仍报，再评估把 Backup 型目标改名或上收 `SheetTargets.kt` |

### 7.2 跨包耦合提示

- 本包 settings 基础层（`SettingRowShell` / `RowTitleBlock` / 行模型 / 弹层）被 `ui/about` 等包复用；本轮未改任何 settings 基础层签名，无连带改动。
- 第一轮遗留提示仍有效：`AppConfig.mediaButtonOnExit` / `autoUpdateVariant` 读取串若将来变更，`OtherSection.kt:133/138` 两处字面量键需同步。

### 7.3 禁令遵守

未改测试与 `build.gradle`；未做 git 写操作；未删功能、未留 `TODO()`、未加 `@Suppress`、未把 Compose 换回 XML。

## 8. 返工复盘与沉淀（R65）

### 8.1 第一轮为何铸成这 25 条（认知成本重点段）

第一轮的处置思路是「局部类非法 → 提升为文件级 private」，方向对，但犯了两个**作用域 / 身份概念错位**：

1. **把「可见性」当成了「命名空间隔离」**。写码代理看到 `private` 就以为「只在本文件可见 = 本文件可以自由起名，与别的文件无关」，于是两个文件各自保留了旧本地类的原名 `TextTarget`。实际上 Kotlin 顶层声明先按**模块内包级身份**查重名（Redeclaration），`private` 只决定访问边界，不授予独立命名空间。这是认知成本最高的一处：接手者必须知道「private 不等于名字私有」。
2. **把「全限定名」当成了「免导入」**。写码代理为绕开列表 / 网格两个 `items` 重载，改用 FQN 写 `androidx.compose.foundation.lazy.grid.items(...)`，以为路径写全就不需要 import；但扩展 callable 只在导入作用域内按接收者参与解析，FQN 候选集不含扩展。同类 FQN 误用还留在 `BottomBarSkinActivity:261`、`ThemeSection:202` 两处（见 §7.1）。
3. **级联遮蔽导致误判**：类型解析一旦回落到错误的私有类，后面的 Int/String/Boolean/lambda 报错全是派生症状；第一轮若只看单条「类型不匹配」容易去改调用实参，而根因在类型身份。本轮先回溯构造器形参表反推被错绑的类型，再修根上的命名冲突。

### 8.2 根因聚簇与预防手段

| 根因类别 | 本轮条目 | 预防手段（建议落 CI 门禁 / Skill） |
|---|---|---|
| 契约漂移 · 命名空间 | 同名 private 顶层类 Redeclaration | 新增文件级包装类前，`grep -rn "<类名>" app/src/main` 列全仓；同包同名一律 unique 命名或上收 `internal` 共享文件 |
| 契约漂移 · 扩展导入 | FQN 调扩展 unresolved | 提交前静态扫描：调用点出现完整 `androidx.compose.foundation.*.items/ detectTapGestures/pointerInput` 等扩展路径时告警，要求改导入短名 |
| 签名咬合 | 实参顺序 / 类型错绑到错类型 | 报 `No value passed for parameter` + 多形参类型互错时，先查类型是否解析到了错的同名声明，不要先调实参 |

### 8.3 沉淀规则条目（交总控评估是否落成脚本 / 门禁）

- **R-配置层包装类命名**：`ui/config/**` 内文件级 `private` 数据类，类名必须在 `ui.config` 包内唯一；跨分区复用的目标包装类统一进 `settings/SheetTargets.kt` 标 `internal`。
- **R-扩展调用写法**：foundation/runtime/ui 的扩展函数一律导入后短名调用；禁止 FQN 接收者式调用扩展；同名扩展共存时用 `as` 别名。

## 9. 铁律 59–66 逐条结论

- **R59 二阶三阶推演**：已写第 4 节，三层齐备（直接效果 / 连带文件接口 / 用户行为与长期维护），点名 `BackupSection`、`SettingSheets`、`SheetTargets`、`BottomBarSkinManager`、`BottomBarSkinAssignViewModel`。
- **R60 跨领域对照**：① 命名空间 ↔ Java 包内顶层类名唯一性：`private` 像文件级访问控制，但类的全限定身份仍在包级查重，与 Java 「package-private 同名类仍冲突」同构；② 扩展导入 ↔ C# `this` 扩展方法 / Vue 组合式函数必须 `import` 进作用域，全限定路径不会自动带接收者解析。本项目采纳：唯一命名 + 导入别名，不引入 Material 组件或 XML 偏好树。
- **R61 先自我否证**：已写第 5 节，列 7 条边界（共享收拢反例、private 同名口径、别名歧义、空清单 / 空值、资源缺失、apply 回落扩展、未验证边界），每条给否证结论。
- **R62 历史包袱审计**：`git show 54bd363` 核对，两个 `TextTarget` 均为第一轮重写时新提升的包装类，非 Legado 旧界面遗留；本轮不删功能路径，仅改名 / 修导入。无贴皮层、无旧 Binding 引用残留于本单两文件。
- **R63 新人认知成本**：见 §8.1 重点段；改动后接手者需知两条约定：private 顶层类名包内唯一、扩展必须导入短名调用。未新增抽象层；`TextEditTarget` 与 `NumberTarget` / `ColorTarget` / `SizeTarget` 命名风格一致。仍偏高的点：各分区私有目标类分散在 4 个文件，建议后续把同构三件套上收 `SheetTargets.kt`。
- **R64 最慢环节**：本轮不跑构建，无本机时长数据；静态核对最慢一步是从 22 条级联报错反推被错绑的构造器形参表（91/96/152 三组 Int↔String↔Boolean 错位逐条对回 BackupSection 4 参构造器）。可自动化：CI 增加「同包 private 顶层类重名」「FQN 扩展调用」两道静态扫描（§8.3）。
- **R65 返工复盘沉淀**：已写第 8 节，按命名空间 / 扩展导入 / 签名咬合三簇聚簇，各给预防手段与两条建议门禁规则。
- **R66 开工前必读**：已读 `docs/机制-子代理必读.md` 与总纲 R48–R74；报告含二阶三阶影响 / 反例边界 / 认知成本三节，前置阅读齐备。
