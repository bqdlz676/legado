> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

# 修复 · CI config 单元测试：补齐 `configPreferenceMatches` 并接入设置列表过滤

> 对应问题清单 A9；CI run `45200701604`（commit `89a0ad4`）`:app:compileAppDebugUnitTestKotlin`
> 5 条 `Unresolved reference 'configPreferenceMatches'`。测试源集 `PreferenceSearchTest.kt` 为规格，未改动。

## ① 任务与范围

- 白名单：仅 `app/src/main/java/io/legado/app/ui/config/**`（含 `settings/`）。
- 禁区：`app/src/test/**`、`ui/book/**`、`ui/map/crossing/**`、`ui/main/search/**`、`res/**` —— 未触碰。
- 红线：无 git 写操作（未 add/commit/push）；未跑 gradle / kotlinc；改后 grep 回证。

### 规格（测试 5 条断言逐字推出的语义）

| 断言 | 语义 |
| --- | --- |
| `("THEME", "Theme settings", null, [])` → true | 大小写不敏感，title 子串包含 |
| `("network", "Other", "Network behavior", [])` → true | summary 子串包含 |
| `("night", "Accent", null, ["Night colors"])` → true | keywords 任一子串包含 |
| `(" ", "Theme", "Interface colors", [])` → false | query 去空白后为空 → false |
| `("cache", "Theme", "Interface colors", [])` → false | 三字段均不包含 → false |

## ② 改动清单（文件:行 + before/after）

### 1. 新增 `app/src/main/java/io/legado/app/ui/config/PreferenceSearch.kt`（+29/-0）

包名 `io.legado.app.ui.config`（与测试同包，无 import）。顶层纯 JVM 函数：

```kotlin
fun configPreferenceMatches(
    query: String,
    title: String,
    summary: String?,
    keywords: List<String>,
): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) return false

    fun fieldHit(field: String?): Boolean =
        !field.isNullOrBlank() && field.lowercase().contains(normalizedQuery)

    if (fieldHit(title)) return true
    if (fieldHit(summary)) return true
    return keywords.any { fieldHit(it) }
}
```

### 2. `settings/SettingModels.kt`（+8/-0）

四种行密封类型尾部各加 `keywords: List<String> = emptyList()`（带默认值，既有分区构造调用零改动）：
`SwitchRow`、`ValueRow`、`ListRow`、`ColorSwatchRow`。

### 3. `settings/SettingControls.kt`（+116/-2）

- 新增 `LocalSettingSearchQuery = compositionLocalOf { "" }`：骨架把搜索词下发到列表层，缺省空串。
- `SettingScaffold` 新增三个带默认值的参数：`showSearch: Boolean = false`、`searchQuery: String = ""`、
  `onSearchQueryChange: (String) -> Unit = {}`；`showSearch` 为真时在顶栏下方渲染 `InkSearchField`，
  内容槽包进 `CompositionLocalProvider(LocalSettingSearchQuery provides searchQuery)`。
- 新增自绘 `InkSearchField`：foundation `BasicTextField` + Canvas 放大镜 / 清除叉；
  颜色全部取 `ComponentTokens`（bgSurface / textPrimary / textSecondary / textTertiary / buttonRadius）；
  容器 44dp 高、水平页边距 20dp、内边距 12dp；无 Material 控件、无硬编码色值、无渐变。
- 既有 `BottomBarSkinActivity` / `BottomBarSkinAssignActivity` 仍只传 `title/onBack`，走默认值，不出现搜索条。

### 4. `settings/SettingList.kt`（+23/-3）

- 读 `LocalSettingSearchQuery.current`；空 query → `visibleGroups = groups`（零过滤，原分组 / 顺序 / 可见性不变）；
  非空 → 按行 `matchesSettingQuery` 裁剪，整组无命中则 `mapNotNull` 丢弃。
- 兜底文案：搜索中无命中显示「未找到匹配的设置项」，否则保留原「暂无设置项」。
- 私有扩展 `SettingRow.matchesSettingQuery`：五种行类型分别委托顶层 `configPreferenceMatches`，
  渲染层不另写大小写 / 空白规则。

### 5. `ConfigActivity.kt`（+13/-1）

- `setContent` 内持有 `searchQuery` 状态；`LaunchedEffect(tag)` 切换分区时清空残留词。
- `SettingScaffold(showSearch = true, searchQuery = …, onSearchQueryChange = …)` 接通唯一真实宿主。

## ③ grep 回证（真实输出）

### 函数定义

```
$ grep -n "fun configPreferenceMatches" app/src/main/java/io/legado/app/ui/config/PreferenceSearch.kt
14:fun configPreferenceMatches(
```

### 非测试调用点（main 源集）

```
$ grep -rn "configPreferenceMatches" app/src/main/java/
app/src/main/java/io/legado/app/ui/config/PreferenceSearch.kt:14:fun configPreferenceMatches(
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:17:import io.legado.app.ui.config.configPreferenceMatches
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:134: * 匹配口径完全委托顶层 [configPreferenceMatches]，渲染层不另写一套大小写 / 空白规则。
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:137:    is GroupHeader -> configPreferenceMatches(query, title, null, emptyList())
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:138:    is SwitchRow -> configPreferenceMatches(query, title, summary, keywords)
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:139:    is ValueRow -> configPreferenceMatches(query, title, summary, keywords)
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:140:    is ListRow -> configPreferenceMatches(query, title, summary, keywords)
app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt:141:    is ColorSwatchRow -> configPreferenceMatches(query, title, summary, keywords)
```

### 搜索接线

```
$ grep -n "showSearch\|searchQuery\|onSearchQueryChange\|LocalSettingSearchQuery\|InkSearchField" \
    app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt \
    app/src/main/java/io/legado/app/ui/config/settings/SettingControls.kt \
    app/src/main/java/io/legado/app/ui/config/settings/SettingList.kt
ConfigActivity.kt:94:  // 设置页搜索词：骨架搜索条输入后经 LocalSettingSearchQuery 下发到各分区 SettingList 过滤；
ConfigActivity.kt:96:  var searchQuery by remember { mutableStateOf("") }
ConfigActivity.kt:97:  LaunchedEffect(tag) { searchQuery = "" }
ConfigActivity.kt:111: showSearch = true,
ConfigActivity.kt:112: searchQuery = searchQuery,
ConfigActivity.kt:113: onSearchQueryChange = { searchQuery = it },
SettingControls.kt:62:val LocalSettingSearchQuery = compositionLocalOf { "" }
SettingControls.kt:70:    showSearch: Boolean = false,
SettingControls.kt:71:    searchQuery: String = "",
SettingControls.kt:72:    onSearchQueryChange: (String) -> Unit = {},
SettingControls.kt:100: if (showSearch) {
SettingControls.kt:101:     InkSearchField(
SettingControls.kt:102:     query = searchQuery,
SettingControls.kt:103:     onQueryChange = onSearchQueryChange,
SettingControls.kt:107: CompositionLocalProvider(LocalSettingSearchQuery provides searchQuery) {
SettingControls.kt:119:fun InkSearchField(
SettingList.kt:32:    val query = LocalSettingSearchQuery.current
```

### 禁区 / 违禁模式回证

- `git status --porcelain -- app/src/test/java/io/legado/app/ui/config/` 为空：测试文件未改。
- 新改 5 文件内 `0x……` 十六进制色值、`#000000/#FFFFFF`、`material3`、`linearGradient`、
  `OutlinedTextField` 均零命中（grep exit=1）。
- `git diff --numstat`（只读）：

```
13  1  ConfigActivity.kt
116 2  settings/SettingControls.kt
23  3  settings/SettingList.kt
8   0  settings/SettingModels.kt
??  PreferenceSearch.kt（新文件，29 行）
```

## ④ 三节

### 二阶 / 三阶影响

- 直接效果：顶层函数补齐，5 条单测的 Unresolved 引用消除；设置壳出现自绘搜索条，输入即过滤当前分区列表。
- 连带接口：`SettingScaffold` 签名向后兼容（三新参全默认），两个皮肤页调用不受影响；
  四种行模型新增 `keywords` 可选字段，113 处既有构造调用无需改动（默认空表）。
- 用户行为：搜索为单分区内过滤，不跨设置大类跳转；清空 / 切分区自动复位。
- 维护成本：匹配规则只有一份（顶层纯函数），列表层只做裁剪与兜底；新增行类型时 `matchesSettingQuery`
  的 `when` 需补分支，否则编译期密封类穷尽检查会拦下遗漏。

### 反例与边界（至少 3 条）

1. **纯空白 / 空 query**：`trim()` 后为空直接 `false`；列表层 `isNotBlank()` 短路为原列表，零变化。
2. **summary 为 null / keywords 含空白元素**：`fieldHit` 判 `isNullOrBlank`，null 与空白元素跳过不崩。
3. **搜索零命中**：分组整组丢弃后 `visibleGroups` 为空，落到「未找到匹配的设置项」兜底行，不留空白态。
4. **分组标题命中但行不命中**：当前实现只按行裁剪，标题单独命中的分组会被隐藏（边界取舍：避免只剩孤标题）。
5. **大小写 / 区域**：用 `lowercase()`（默认区域）；英文断言可过，土耳其语等 `I→ı` 极端区域未专门处理。
6. **切分区残留词**：`LaunchedEffect(tag)` 复位，避免上一页关键词把新页面滤空。

### 认知成本

- 新增概念 2 个：`LocalSettingSearchQuery`（词向下传递）、`InkSearchField`（自绘输入条）；
  匹配口径集中在顶层函数，命名与测试断言一致，接手者可从测试反推实现。
- 未引入新抽象层：未造搜索 ViewModel / 未建索引结构；keywords 为可选别名表，缺省空。

## ⑤ 未验证项（静态结论）

- **未经 CI 编译验证**：沙箱禁 gradle / kotlinc，未跑 `:app:compileAppDebugUnitTestKotlin`，5 条断言是否全绿待云端 CI。
- **未经真机运行**：搜索条布局、输入态、清除叉点击、过滤动画与兜底文案均未在设备 / 模拟器上目视走查。
- 行数统计来自只读 `git diff --numstat` 与 `wc -l`；新文件 29 行为磁盘实测。

## ⑥ 交接（发现但未改）

- `SettingList` 内 `val context = LocalContext.current` 在本函数体未直接使用（历史遗留，子项各自取 context）；
  不属本单范围，未动。
- 各分区行模型目前未填 `keywords` 别名表；英文偏好名 / 旧词兜底命中暂未启用，可由对应分区槽位后续补。
- 工作区 `ui/map/crossing/**`、`app/src/test/java/io/legado/app/ui/main/search/**` 等存在他人未提交改动，
  非本单产物，未触碰。
