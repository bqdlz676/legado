# 验收 · 复核 CI 修红三单（A9 / A10 / A11）

> 验收方：独立验收 AI（只判不改）。验收时间：2026-11-23。
> 验收对象：main 上三个 commit —— A9 `d99486d`、A10 `876fc59`、A11 `741b1e1`。
> 方法：测试即规格，逐条对照断言；规模 / 调用点 / 禁区扫描全部自取数，不采信写码方自评。
> 红线遵守：未执行任何 git 写操作；未跑 gradle / kotlinc；未改 `app/src/**`；本文件为唯一新增写入。

## 1. 总结论

**部分 PASS。**

一句话理由：三单在静态层（L0 词法 / L1 逐条销项）全部对上测试断言与等价性判据，未发现死代码、禁区命中或签名错配；但三单均**未经云端 CI clean 编译、未经单元测试实跑、未经真机运行**，按机制 §4 不能判全 PASS。

| 单号 | 静态判定 | 说明 |
|---|---|---|
| A9 `d99486d` | **PASS（静态）** | 5 条断言逐条对上；main 源集存在真实调用链；空 query 零过滤；禁区零命中；token 字段齐全。 |
| A10 `876fc59` | **PASS（静态）** | `Sequence.none()` 与原「违例集合为空」语义等价，断言未削弱。 |
| A11 `741b1e1` | **PASS（静态）** | 纯函数 5 条断言对上；调用点与改前行为等价；`groupName` 原样保留。 |

## 2. 销项表

### 2.1 A9 —— 设置页偏好搜索（`d99486d`）

| 编号 | 判据原句（测试即规格） | 落点 文件:行 | 判定 | 自取证据 |
|---|---|---|---|---|
| A9-1 | 大小写不敏感：`configPreferenceMatches("THEME", "Theme settings", null, emptyList())` → true | `app/src/main/java/io/legado/app/ui/config/PreferenceSearch.kt:20,26`；测试 `PreferenceSearchTest.kt:11` | PASS | 实现：`normalizedQuery = query.trim().lowercase()`，`fieldHit` 内 `field.lowercase().contains(normalizedQuery)`；`"theme" in "theme settings"` 成立。 |
| A9-2 | summary 子串命中：`configPreferenceMatches("network", "Other", "Network behavior", emptyList())` → true | `PreferenceSearch.kt:27`；测试 `PreferenceSearchTest.kt:12` | PASS | summary 非空时 `fieldHit(summary)` 命中；`"network" in "network behavior"` 成立。 |
| A9-3 | keywords 子串命中：`configPreferenceMatches("night", "Accent", null, listOf("Night colors"))` → true | `PreferenceSearch.kt:28`；测试 `PreferenceSearchTest.kt:13` | PASS | `keywords.any { fieldHit(it) }`；`"night" in "night colors"` 成立；summary 为 null 由 `isNullOrBlank()` 跳过。 |
| A9-4 | 纯空白 query → false：`configPreferenceMatches(" ", "Theme", "Interface colors", emptyList())` → false | `PreferenceSearch.kt:20-21`；测试 `PreferenceSearchTest.kt:14` | PASS | `" ".trim()` 为空串，第 21 行 `if (normalizedQuery.isEmpty()) return false` 提前返回。 |
| A9-5 | 无任一子串命中 → false：`configPreferenceMatches("cache", "Theme", "Interface colors", emptyList())` → false | `PreferenceSearch.kt:26-28`；测试 `PreferenceSearchTest.kt:15` | PASS | title / summary / keywords 三路均不包含 `"cache"`，落到 `any` 结果 false。 |
| A9-6 | 函数必须有 main 源集非测试调用点（零调用点=死代码） | 定义 `PreferenceSearch.kt:14`；调用 `settings/SettingList.kt:137-141`（5 个分支）；导入 `SettingList.kt:17` | PASS | 自取 grep：main 源集命中 1 处定义 + 1 处导入 + 5 处调用；调用链 ConfigActivity → SettingScaffold(Local) → 6 个分区 SettingList → `matchesSettingQuery` → 本函数，非死代码。 |
| A9-7 | 空 query 时设置列表零变化 | `settings/SettingList.kt:32-38` | PASS | `searching = query.isNotBlank()`；`if (!searching) groups else …` —— 非搜索态直接返回原 `groups` 引用，分组 / 顺序 / 可见性不动；`LocalSettingSearchQuery` 缺省 `""`（`SettingControls.kt:62`）。 |
| A9-8 | 禁区零命中：`#000000` / `#FFFFFF` / `material3` / `linearGradient` / `OutlinedTextField` 在 A9 改动文件内 | 5 个 A9 main 文件 | PASS | 自取 grep（含大小写不敏感与 hex 色值 `0x…` 扫描）：0 命中，详见 §6 命令输出。 |
| A9-9 | `ComponentTokens` 确有 `buttonRadius / bgSurface / textPrimary / textSecondary / textTertiary` | `ui/components/ComponentTokens.kt:22,24,25,26,85` | PASS | 自取 grep：5 个字段全部存在（`bgSurface:22`、`textPrimary:24`、`textSecondary:25`、`textTertiary:26`、`buttonRadius:85`）；`SettingControls.kt` 新增代码另用 `bgPage:21`，亦存在。 |
| A9-10 | `androidx.compose.ui.graphics.drawscope.Stroke` 导入正确 | `settings/SettingControls.kt:40` | PASS | 导入路径与 Compose 标准包一致；仓内 `InkEmptyState.kt`、`InkSlateCanvas.kt` 同路径先例；`Stroke(width = 1.6.dp.toPx())` 形参 `width: Float` 类型吻合。 |
| A9-11 | `SettingScaffold` 三个新形参均有默认值；其余调用点仍可编译 | `settings/SettingControls.kt:70-72`；调用点 `ConfigActivity.kt:108`、`BottomBarSkinActivity.kt:104`、`BottomBarSkinAssignActivity.kt:74` | PASS（静态） | 默认值：`showSearch = false`、`searchQuery = ""`、`onSearchQueryChange = {}`；皮肤两页仅传 `title / onBack`（+ modifier），静态签名兼容。 |

### 2.2 A10 —— `Sequence.isEmpty()` 改 `none()`（`876fc59`）

| 编号 | 判据原句 | 落点 文件:行 | 判定 | 自取证据 |
|---|---|---|---|---|
| A10-1 | 改后语义必须与原「违例集合为空」完全等价，不得削弱断言 | `app/src/test/java/io/legado/app/ui/main/search/SearchContractTest.kt:175` | PASS（静态） | `violations` 静态类型：`mainRoot.walkTopDown()`（`FileTreeWalk: Sequence<File>`）→ `filter` → `map { readText() }` → `filter`，终点为 `Sequence<String>`。`Sequence.none()` 实现为 `!any()`，即序列无元素，与「集合为空」同真同假；断言消息 `"旧搜索壳残留引用: $violations"` 未改，断言强度未降。 |
| A10-2 | 测试源集写法修复：`Sequence` 无 `isEmpty()`，改 `none()` / `toList().isEmpty()` 之一 | 同上行 | PASS | 全文件 grep：仅剩 1 处 `none()`（175 行），无残留 `.isEmpty()` 作用于该序列。 |

### 2.3 A11 —— 分组回落纯函数（`741b1e1`）

| 编号 | 判据原句（测试即规格） | 落点 文件:行 | 判定 | 自取证据 |
|---|---|---|---|---|
| A11-1 | null → false：`isMissingBookSourceGroupFilter(null, groups)` | `BookSourceGroupFilter.kt:12`；测试 `BookSourceGroupFilterStateTest.kt:13` | PASS | `if (filter == null) return false`。 |
| A11-2 | 普通关键词 → false：`"keyword"` | `BookSourceGroupFilter.kt:13`；测试 `:14` | PASS | `!filter.startsWith("group:")` → false。 |
| A11-3 | `group:novel`（novel 在集合）→ false | `BookSourceGroupFilter.kt:14-15`；测试 `:15` | PASS | `removePrefix("group:")` 得 `"novel"`，`"novel" in {"novel","comic"}` → false。 |
| A11-4 | `group:deleted`（deleted 不在集合）→ true | `BookSourceGroupFilter.kt:15`；测试 `:16` | PASS | `"deleted" !in groups` → true。 |
| A11-5 | `group:` + 空集 → true | `BookSourceGroupFilter.kt:14-15`；测试 `:17` | PASS | 冒号后得 `""`，`"" !in emptySet()` → true。 |
| A11-6 | 调用点与改前行为等价：`state.preset==GROUP && groupName!=null && groupName !in groups` 才回落 ALL；`groupName` 原样保留 | `SourceManageViewModel.kt:133-137` | PASS（静态） | 改前（diff 父版本）：`preset==GROUP && groupName!=null && groupName !in groups` → ALL。改后：前两个守卫原样保留，第三段委托 `isMissingBookSourceGroupFilter("group:${state.groupName}", groups.toSet())`；函数内对该前缀恒返回 `groupName !in groups`，条件真值表逐项等价。`state.copy(groups = groups, preset = preset, groupName = state.groupName)` 未改，`groupName` 原样保留。 |
| A11-7 | 纯函数、不持状态；书源域其余文件不被触碰 | `BookSourceGroupFilter.kt:11-16`；numstat | PASS | 文件 16 行，仅顶层函数，无 Android / Compose 依赖；numstat 显示本单仅动 `BookSourceGroupFilter.kt`(+16) 与 `SourceManageViewModel.kt`(+5/-4)，未涉书源域其他文件。 |

## 3. 返工要求表

本轮静态销项**未发现必须返工的缺陷**。下列条目为「待 CI 确认」的残余风险，若云端编译 / 单测报红，按对应改法复验：

| 编号 | 缺陷（静态观察） | 判据 | 可执行改法（可测、可复验） |
|---|---|---|---|
| R1 | 4 个 row 数据类（`SwitchRow / ValueRow / ListRow / ColorSwatchRow`）在中部插入默认参数 `keywords`；若存在以**非尾 lambda 的位置实参**传 `onChange / onClick / onOpen` 的调用点，会把 lambda 错绑到 `keywords` 而编译失败。静态扫描 7 个调用文件均为命名实参或尾 lambda，未见错绑；未经编译器确认。 | A9-6 / A9-11 签名咬合 | 云端 `compileAppDebugKotlin` 为准；若报类型错，把对应构造点改为命名实参（`onChange = { … }` / `onClick = { … }` / `onOpen = { … }`），复验编译绿。 |
| R2 | A10 `none()` 依赖 `kotlin.sequences` 默认导入；静态判定成立，未经编译器确认。 | A10-1 语义等价 | 若编译报未解析引用，补 `import kotlin.sequences.none` 或改 `toList().isEmpty()`；二者与原断言等价。 |
| R3 | 三单新增 Compose 接线（`CompositionLocalProvider`、`BasicTextField.decorationBox`、`Stroke`）在仓内均有先例，但整包未经 clean 编译。 | §4 L2 | 跑云端 `compileAppDebugKotlin` + `testAppDebugUnitTest`；报哪条解引用失败补哪条导入，不扩大改动。 |

## 4. 二阶 / 三阶影响分析

### A9（设置搜索）

- **二阶**：过滤只发生在渲染层 `SettingList`，不写 SharedPreferences、不改分区状态；搜索词存于 `ConfigActivity` 局部 `remember`，切分区由 `LaunchedEffect(tag)` 清空（`ConfigActivity.kt:97`），不会把新页面滤空。`LocalSettingSearchQuery` 经 `CompositionLocalProvider` 下发，皮肤分配等不走搜索条的页面取缺省 `""`，列表零过滤。
- **三阶**：过滤后分组经 `group.copy(rows = rows)` 裁剪，LazyColumn 的 item key（`sw-/val-/list-/color-/h-` 前缀）保持不变，不会因裁剪导致 key 漂移；搜索无命中时落到「未找到匹配的设置项」兜底文案，不留空白态。keywords 字段全部缺省 `emptyList()`，既有分区构造点无需回填。
- **潜在反推**：若后续新增 `SettingRow` 密封实现者，`SettingList.kt:53-59 / 61-67 / 136-141` 三处 `when` 会因穷尽性检查编译报错——这是期望中的保护，不是缺陷；新增行类型时三处必须同补。

### A10（测试谓词）

- **二阶**：仅测试源集一行谓词替换，不触 main 源集；`none()` 消费序列一次，断言仅取用一次，无重复消费问题。
- **三阶**：谓词等价性不影响生产行为；若将来 `violations` 改收集为 `List`，`none()` 对 `Iterable` 同样成立，无需再改。

### A11（分组回落）

- **二阶**：回落条件从内联表达式抽为纯函数，真值表不变；`groups: List<String>` 经 `toSet()` 转 `Set<String>` 喂给函数，成员判定语义与 `List.contains` 一致。
- **三阶**：分组被删后仍回落 `ALL`，`groupName` 保留不置空（与旧口径一致），因此 `flowGroupSearch(groupName ?: "")` 不会在回落瞬间被误调；纯函数同包放置，`ViewModel` 无需新增导入。

## 5. 反例 / 边界逐条

| 边界 / 反例 | 实现表现 | 判定 |
|---|---|---|
| query 为纯空格 / 空串 | `trim()` 后为空 → 一律 false（A9-4） | 兜住 |
| query 大小写混合、字段大小写混合 | 双侧 `lowercase()` 后子串包含（A9-1/2/3） | 兜住 |
| summary 为 null | `isNullOrBlank()` 跳过该字段，不 NPE | 兜住 |
| keywords 元素为空白 / null 防御 | 元素走同一 `fieldHit`，空白跳过（注：当前 `List<String>` 元素类型非空，null 仅为注释级防御） | 兜住 |
| query 含内部空格（如 `"th eme"`） | 不做内部归一，子串不命中即 false；规格未要求分词 | 规格外，可接受 |
| filter 为 null / 非 `group:` 前缀 | 直接 false（A11-1/2） | 兜住 |
| 分组名含冒号（如 `a:b`） | `removePrefix` 只剥前缀，成员判定与旧内联一致 | 等价 |
| `group:`（空分组名）+ 非空分组集 | `"" !in groups` → true；与旧 `groupName !in groups` 同口径 | 等价 |
| 搜索态整组无命中 | `mapNotNull` 剔除空组；全空时显示无命中兜底文案 | 兜住 |
| 非设置页（皮肤两页）未传搜索词 | Local 缺省 `""`，`searching=false`，原列表直出 | 兜住 |

## 6. 认知成本评估

- **A9**：匹配口径分两层——纯判定在 `PreferenceSearch.kt`（无 Android/Compose 依赖，可直测），渲染裁剪在 `SettingList.matchesSettingQuery` 私有扩展。两层职责单一，但接手者需读两个文件才能拼出完整搜索链路；函数名 `configPreferenceMatches` 与扩展名 `matchesSettingQuery` 命名相近，建议后续统一词表（非本轮返工项）。
- **A10**：一行谓词替换，认知成本最低。
- **A11**：内联判断抽为同包顶层纯函数，命名 `isMissingBookSourceGroupFilter` 与测试名一致；调用点保留两个前置守卫，读者可直接对照旧条件，认知成本可控。
- 三处新公共符号（`LocalSettingSearchQuery`、`InkSearchField`、`isMissingBookSourceGroupFilter`、`configPreferenceMatches`）均有 KDoc 说明适用条件，未发现抽象过度或职责不清。

## 7. 未验证项（显式）

1. **未经云端 CI 编译验证**：三单所在 main 源集与测试源集均未跑 `compileAppDebugKotlin` / `compileAppDebugUnitTestKotlin`；当前状态文档记录 CI 仍为待复跑（`docs/当前状态.md`：A9/A10 待 CI 复跑，A11 更晚落地）。
2. **未经单元测试实跑**：`PreferenceSearchTest`（5 断言）、`BookSourceGroupFilterStateTest`（5 断言）、`SearchContractTest` 均未在本机 / 云端执行通过；本验收只做静态逐条对照。
3. **未经真机运行**：搜索条交互（输入 / 清空叉 / 放大镜矢量绘制 / 键盘弹起）、分组回落的实际页面表现未上真机；观感、性能、动画一律未验证。
4. **L3 跨包咬合未验证**：分区页面 → `SettingList` → Local 的完整组装链路仅静态 grep 确认，未经打包运行。
5. 工作树存在其他代理未提交的测试 / 文档改动（与本三单无关），本验收未对其下结论。

## 8. 我用的命令与真实输出（可复跑）

### 8.1 三单规模（`git show --numstat`，自取，未照抄写码方数字）

```
$ git show --numstat --format="%h %s" d99486d
d99486d 修CI A9：补 configPreferenceMatches 并接设置页搜索（自绘搜索条→Local→SettingList 过滤，零死代码）
13	1	app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt
29	0	app/src/main/java/io/legado/app/ui/config/PreferenceSearch.kt
116	2	app/src/main/java/io/legado/app/ui/config/settings/SettingControls.kt
23	3	app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt
8	0	app/src/main/java/io/legado/app/ui/config/settings/SettingModels.kt
178	0	"docs/修复-CI-config搜索匹配.md"

$ git show --numstat --format="%h %s" 876fc59
876fc59 修CI A10：SearchContractTest 对 Sequence 调 isEmpty 改为 none()（语义等价，断言未削弱）
1	1	app/src/test/java/io/legado/app/ui/main/search/SearchContractTest.kt

$ git show --numstat --format="%h %s" 741b1e1
741b1e1 修CI A11：补 isMissingBookSourceGroupFilter 纯函数并等价替换分组回落判断（测试即规格，未改断言）
16	0	app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceGroupFilter.kt
5	4	app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt
```

### 8.2 A9 main 调用点（死代码核查）

```
$ grep -rn "configPreferenceMatches(" app/src/main --include=*.kt
app/src/main/java/io/legado/app/ui/config/PreferenceSearch.kt:14:fun configPreferenceMatches(
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:137:    is GroupHeader -> configPreferenceMatches(query, title, null, emptyList())
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:138:    is SwitchRow -> configPreferenceMatches(query, title, summary, keywords)
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:139:    is ValueRow -> configPreferenceMatches(query, title, summary, keywords)
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:140:    is ListRow -> configPreferenceMatches(query, title, summary, keywords)
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:141:    is ColorSwatchRow -> configPreferenceMatches(query, title, summary, keywords)
```

### 8.3 禁区扫描（A9 五个 main 文件，大小写不敏感 + hex 色值）

```
$ grep -niE "#000000|#FFFFFF|material3|linearGradient|OutlinedTextField" <5 files>
0 hit（逐文件均 0 命中）
$ grep -nE "0x[0-9A-Fa-f]{6,8}|Color\(0x|#" <5 files>
no hex color
```

### 8.4 ComponentTokens 字段核查

```
$ grep -n "val " app/src/main/java/io/legado/app/ui/components/ComponentTokens.kt
21:    val bgPage: Color = Color(ComponentPalette.BG_PAGE)
22:    val bgSurface: Color = Color(ComponentPalette.BG_SURFACE)
24:    val textPrimary: Color = Color(ComponentPalette.TEXT_PRIMARY)
25:    val textSecondary: Color = Color(ComponentPalette.TEXT_SECONDARY)
26:    val textTertiary: Color = Color(ComponentPalette.TEXT_TERTIARY)
85:    val buttonRadius: Dp = ComponentPalette.BUTTON_RADIUS_DP.dp
```

### 8.5 密封实现者穷尽性核查

```
$ grep -rn ": SettingRow" app/src/main
SettingModels.kt:17  GroupHeader
SettingModels.kt:30  SwitchRow
SettingModels.kt:42  ValueRow
SettingModels.kt:55  ListRow
SettingModels.kt:66  ColorSwatchRow
（共 5 个，SettingList 三处 when 全覆盖）
```

### 8.6 SettingScaffold 调用点

```
$ grep -rn "SettingScaffold(" app/src/main
BottomBarSkinActivity.kt:104:  SettingScaffold(title = …, onBack = { finish() }) { … }
BottomBarSkinAssignActivity.kt:74: SettingScaffold(title = …, onBack = { … }) { … }
ConfigActivity.kt:108:         SettingScaffold(title = …, onBack = …, showSearch = true, searchQuery = …, onSearchQueryChange = …) { … }
SettingControls.kt:66:         fun SettingScaffold( … )
```

### 8.7 三单均在 main 上、目标文件工作树与 HEAD 一致

```
$ git merge-base --is-ancestor d99486d HEAD && echo OK
$ git merge-base --is-ancestor 876fc59 HEAD && echo OK
$ git merge-base --is-ancestor 741b1e1 HEAD && echo OK
（三个均输出 OK）
$ git diff HEAD --stat -- <config 与 source/manage 目标文件>
（空输出：目标 main 源集工作树与 HEAD 无差异）
```

### 8.8 A11 调用点与改前等价性

```
$ git show 741b1e1 -- SourceManageViewModel.kt
-  val preset = if (state.preset == SourcePreset.GROUP &&
-      state.groupName != null && state.groupName !in groups
-  ) SourcePreset.ALL else state.preset
+  val missingGroup = state.preset == SourcePreset.GROUP &&
+      state.groupName != null &&
+      isMissingBookSourceGroupFilter("group:${state.groupName}", groups.toSet())
+  val preset = if (missingGroup) SourcePreset.ALL else state.preset
   state.copy(groups = groups, preset = preset, groupName = state.groupName)
```

### 8.9 行构造点非尾 lambda 排查

```
$ grep -rnE "(SwitchRow|ValueRow|ListRow|ColorSwatchRow)\([^)]*, *\{" app/src/main --include=*.kt
no non-trailing positional lambda
```

## 9. 静态编译隐患逐条清单

| # | 隐患类型 | 位置 | 静态结论 |
|---|---|---|---|
| H1 | 中部默认参数的位置实参绑定（Kotlin 签名） | `SettingModels.kt` 四个 row 数据类 | **残余低风险**：7 个调用文件静态扫描均命名实参 / 尾 lambda，未见错绑；待 CI 编译器确认（返工表 R1）。 |
| H2 | 密封类穷尽性 | `SettingList.kt:53-59 / 61-67 / 136-141` | **已排除**：5 个实现者全覆盖，无遗漏分支。 |
| H3 | 可空智能转换 | `SettingList.kt:50` `GroupHeader(group.title)` | **已排除**：`group.title` 为 data class `val`，`isNullOrBlank()` 守卫后可智能转换为 `String`。 |
| H4 | 跨包符号解析 | `PreferenceSearch.kt`（包 `ui.config`）被子包 `settings` 导入 | **已排除**：`SettingList.kt:17` 显式导入顶层函数；A11 函数与 ViewModel 同包，无需导入。 |
| H5 | 集合类型匹配 | `SourceManageViewModel.kt:135` `groups.toSet()` | **已排除**：`groups: List<String>`（`SourceManageState.kt:99`）→ `Set<String>`，与函数形参吻合。 |
| H6 | 测试谓词解析 | `SearchContractTest.kt:175` `Sequence.none()` | **残余低风险**：`kotlin.sequences.none()` 属默认导入；待编译器确认（返工表 R2）。 |

**静态可能编译不过的隐患条数：2 条残余低风险（H1、H6），4 项已静态排除（H2–H5）。**
