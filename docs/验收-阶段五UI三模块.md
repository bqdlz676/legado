# 验收报告 · 阶段五 UI 三模块静态审查（U9 字体 / U6+U7 半屏 Sheet+画板 / U8 搜索页）

- 验收日期：2026-11-23
- 仓库根：`/home/user/Doubao/chats/38454122521460994/legado`
- 审查性质：**只读静态验收**。不改代码、不编译、不跑单测、不 commit、不 push。
- 对应提交：U9 = `a4a2804`；U6+U7 = `6c3fe14`；U8 = `d5613e4`（三代理均自报「未编译、未验证」）。
- 审查方式：嵌套感知词法扫描脚本（剥离字符串 / 字符字面量 / 行注释，块注释按 Kotlin 嵌套规则计数）+ 全仓 package/import 索引核对 + 逐文件人工复核。扫描覆盖 63 个 `.kt` 文件（main 36 + test 27）。

> 结论先行：三个模块四类硬性编译阻断 **全部为 0**，契约代码级违规 **0**。编译静态结论：U9 **PASS**、U6+U7 **PASS**、U8 **静态编译 PASS**。
> 另：U8 发现 1 项静态可读的运行期逻辑缺陷 **D1**（`SearchMatcher.rank` 截断下限写错，会使对应单测跑红），不阻断编译，但功能验收应判不通过，详见 4A 节。
> 本报告只代表「静态扫描 + 人工读码未发现编译阻断」，**不代表已编译通过、不代表单测 / 真机可跑**；残留风险见第 5 节。

---

## 1. 四类硬性编译阻断扫描结果（汇总）

| 模块 | 文件数(main/test) | ① 注释闭合 | ② 单类多 companion | ③ import↔package 一致 | ④ Float/Double 不匹配 | 阻断数 | 结论 |
|---|---|---|---|---|---|---|---|
| U9 字体系统 | 13 (9/4) | 0 | 0 | 0 | 0 | **0** | **PASS** |
| U6+U7 组件 / 画板 | 30 (15/15) | 0 | 0 | 0 | 0 | **0** | **PASS** |
| U8 搜索页 | 20 (12/8) | 0 | 0 | 0 | 0 | **0** | **PASS** |

### 1.1 注释闭合（嵌套感知）

- 扫描器以 Kotlin 词法状态机遍历：`//` 行注释、`/* */` 块注释（**按嵌套计数**）、`"..."` / `"""..."` / `'...'` 字面量整体跳过。
- 结果：63 个文件 EOF 块注释深度均为 0，无未闭合 `/*`、无多余 `*/`、无嵌套深度 > 1 的异常块；注释正文中未发现 `*.json` / `map/*.js` / `a/*/b` 这类会触发嵌套连写的路径或 glob。

### 1.2 单个 class/object/data class 至多一个 companion object

- 按花括号配对归属到所属类，逐类计数。含 `companion object` 的文件及归属：
  - `font/FontRole.kt:136` —— 枚举类 `FontRole` 内 1 个（第 3 行的 `companion object` 仅为 KDoc 文字，非声明）。
  - `components/HalfSheetModel.kt:119`（`HalfSheetSectionKind`）、`:190`（`HalfSheetContent`）—— 分属两个不同类，各 1 个。
  - `components/inkslate/InkSlateModel.kt:41`（`InkTool`）、`:63`（`InkPreset`）、`:307`（`InkSlateState`）—— 分属三个不同类，各 1 个。
  - `components/ComponentState.kt`、`InkToast.kt`、`Timeline.kt`、`search/SearchModels.kt` 及各测试文件：各 1 个。
- 未发现同一类内出现 2 个及以上 `companion object`。

### 1.3 import 路径与实际 package 一致

- 全仓建立 `.kt` / `.java` 索引（类型、顶层函数、扩展属性），逐条核对 `io.legado.*` import：
  - 生成类 `io.legado.app.R`、`io.legado.app.databinding.*` 按生成代码豁免；通配 import `io.legado.app.utils.*` 按合法通配豁免。
  - 结果：**0 条 unresolved，0 条 package 错配**。重点复核的跨子包引用（历史漏过的 `globe.replay.power` 类错配）均指向真实 package：
    - `ui.components.rememberInkGesture` ← `ui.components.InkInteractionState.kt`；
    - `utils.FileDoc / FileUtils / RealPathUtil / FileDocExtensions / FileExtensions / StringExtensions / ContextExtensions / DialogExtensions / ToastUtils / UriExtensions` 全部为 `io.legado.app.utils`；
    - `lib.theme.accentColor / primaryColor` ← `lib.theme.MaterialValueHelper.kt`；
    - `lib.dialogs.alert / SelectItem` ← `lib.dialogs`；`lib.permission.Permissions / PermissionsCompat` ← `lib.permission`；`ui.file.HandleFileContract` ← `ui.file`。

### 1.4 Float / Double 类型不匹配

- 模型层（手势 / 画板 / 动画位移）统一使用 **Float**；bench 采样耗时使用 **Double** ms；两套类型未在同一表达式里混用。
- 字面量后缀扫描：未发现 `val x: Float = <Double 字面量>` 或 `val x: Double = <f 后缀字面量>`。
- 跨类型比较仅存在于 bench 断言：`p95Ms: Double <= budgetMs: Long/Double`。Kotlin `Double.compareTo(Long/Int)` 为标准运算符，可编译；属跨子域类型不统一（见观察项 O3），**不构成编译阻断**。
- 人工复核的手势 / 绘图边界：`awaitPointerEvent().change.pressure`(Float)、`offset`(Float)、`velocity`(Float) 与模型 `Float` 形参一致；`head * kotlin.math.cos(...).toFloat()` 等 Double→Float 处均已显式 `.toFloat()`；`Long` ARGB 走 `Color(Long)` 重载（与 `ComponentTokens` 既有写法一致）。

---

## 2. 契约违规扫描结果

| 契约项 | 结果 | 说明 |
|---|---|---|
| `import androidx.compose.material` | 0 代码命中 | 仅 KDoc 文字提及，已按规则豁免 |
| `import com.google.android.material` | 0 代码命中 | 仅测试断言字符串，豁免 |
| `MaterialTheme` | 0 代码命中 | 命中行均为 KDoc 说明或测试断言名 / 字符串 |
| `rememberRipple` | 0 命中 | 测试断言字符串除外 |
| `ripple(` | 0 命中 | — |
| 非 token 文件内联 `Color(0x…)` hex | 0 | 色值只出现在 token / palette 文件 |
| 新文件 package 声明 ↔ 目录路径一致 | 0 错配 | 63 个文件逐一比对通过 |

### 2.1 色值落点复核

- `components/ComponentPalette.kt`：12 色为 `const val … : Long` ARGB 常数（模型层允许，不算违规）；`ComponentTokens.kt` 负责把 Long 包成 `Color`（两层收口）。
- `components/inkslate/InkSlateModel.kt:74,77`：`HUA_QING` / `TENG_HUANG` 为模型层 `Long` ARGB 常数，按规则豁免。
- `search/SearchTokens.kt:20-53`：12 个 `Color(0x…)` 写在 token 对象内（信息项，见 O2）；搜索域其余 11 个 main 文件无任何内联 hex。
- `Color.Transparent`（`InkButton.kt:121`）为系统常量，非 hex 字面量，不违规。

---

## 3. 逐模块结论

### 3.1 U9 字体系统 —— **PASS**（阻断 0）

覆盖（13）：main `AndroidFontSource.kt / FontAdapter.kt / FontAssetSource.kt / FontImport.kt / FontLoadResult.kt / FontRegistry.kt / FontRole.kt / FontSelectDialog.kt / InkFontLoader.kt`；test `FontModuleContractTest.kt / FontRegistryBenchTest.kt / FontRegistryTest.kt / FontSelectionStyleTest.kt`。

四类阻断：注释 0、双 companion 0、import 错配 0、Float/Double 0。契约：Material 系 0、非 token hex 0、package/目录一致。

遗留 Legado 依赖人工核对均真实存在：
- 基类 / 适配器：`BaseDialogFragment(@LayoutRes)`、`RecyclerAdapter<ITEM, VB>(Context)`、`ItemViewHolder`；抽象方法 `getViewBinding / convert(holder, binding, item, payloads) / registerListener` 签名与 `FontAdapter` override 一致。
- 扩展 / 工具：`FileDoc.list / listFileDocs / openInputStream / fromDocumentFile / fromUri / fromFile`、`FileUtils.getPath(File, vararg)`、`RealPathUtil`、`Context.externalFiles`、`getPrefString / putPrefString`、`isContentScheme`、`dpToPx`、`View.visible()`、`Throwable.printOnDebug()`、`Context/Fragment.accentColor / primaryColor`、`setLayout`、`toastOnUi`、`viewBinding` 委托、`PermissionsCompat.Builder` 链、`alert(titleResource, init)` + `items(...)` 选择器 DSL。
- 资源：`layout/dialog_font_select.xml`（`toolBar / recyclerView`）、`layout/item_font.xml`（`root_card / tv_font / iv_checked`）、`menu/font_select.xml`（`menu_default / menu_other / menu_import` + `@drawable/ic_import`）、字符串 `select_font / system_typeface / import_str / success / wrong_format / error_read_file / tip_perm_request_storage / default_font / other_folder / font_item_private / font_item_external`、`array/system_typefaces` 均存在。
- 资产契约：`assets/fonts/manifest.json`、`chars/README.md`、`README.md`、`subset_generate.py` 中测试断言的关键标记（`fontTools`、`BODY_BUDGET_BYTES = 200 * 1024`、`NUMBER_BUDGET_BYTES = 20 * 1024`、`2500`、`子集未实际生成` 等）逐条命中。

### 3.2 U6 半屏 Sheet + U7 画板 —— **PASS**（阻断 0）

覆盖（30）：main `ComponentPalette.kt / ComponentState.kt / ComponentTokens.kt / HalfSheetModel.kt / InkBottomSheet.kt / InkButton.kt / InkCard.kt / InkEmptyState.kt / InkHalfSheet.kt / InkInteractionState.kt / InkToast.kt / Timeline.kt / inkslate/InkSlateCanvas.kt / inkslate/InkSlateModel.kt / inkslate/InkSlateToolbar.kt`；test 15 个（bench / 模型 / 状态 / 吸附 / 时间轴 / 色板 / 画板 SVG 与模型）。

四类阻断：注释 0、双 companion 0、import 错配 0、Float/Double 0。契约：Material 系 0、非 token hex 0、package/目录一致。

跨文件符号解析复核：`ComponentTokens.*` / `ComponentPalette.*` / `rememberInkGesture` / `InkInteraction` / `ComponentVisualState` / `HalfSheetAnchors` / `SheetDetent` / `HalfSheetSection` / `HalfSheetSectionKind` / `HalfSheetContent` / `HalfSheetKind` / `TimelineModel` / `TimelineNode` / `RegimeRow` / `ToastPalette` / `ToastMessage` / `ToastKind` / `InkSlateState` / `InkTool` / `InkPreset` / `InkSlatePalette` / `InkPressure` / `InkSlateSvg` 均在本包内有声明；全仓无同名重复声明。

### 3.3 U8 搜索页 —— **静态编译 PASS**（编译阻断 0；附运行期缺陷 D1）

覆盖（20）：main `SearchFallback.kt / SearchHighlight.kt / SearchHistory.kt / SearchHistoryPanel.kt / SearchInputBar.kt / SearchMatcher.kt / SearchModels.kt / SearchMotion.kt / SearchReducer.kt / SearchResultList.kt / SearchScreen.kt / SearchTokens.kt`；test 8 个。

四类阻断：注释 0、双 companion 0、import 错配 0、Float/Double 0。契约：Material 系 0、非 token hex 0、package/目录一致。

**注意**：存在 1 项运行期逻辑缺陷 **D1**（`SearchMatcher.rank` 截断下限写错），会使 `SearchMatcherTest` 截断用例跑红；不阻断编译，但功能验收应判不通过，修复并复测后再交付。

模型 / 归约复核：`SearchUiState`、`SearchLoadState`（`Idle/Loading/Content/Empty/Error/Offline` 六子类型）、`SearchIntent`（12 类）、`SearchResultItem`、`MatchSegment`、`SearchField.safeOf(Int?)`、`SearchMatcher`（`normalize / matches / score / rank`）、`SearchHighlight.split`、`SearchHistory`（`normalizeKey / clean / add / remove / clear`）、`SearchReducer.reduce` 签名与单测调用逐一吻合；`when` 对 sealed 接口穷尽，无缺分支。

---

## 4A. 运行期 / 单测逻辑缺陷（静态可读，**非 Kotlin 编译阻断**）

> 以下问题不影响语法编译，但按现有代码推演会使对应单测在 JVM 跑红；属功能正确性缺陷，建议在编译前一并修复。

| 编号 | 文件:行号 | 现象 | 根因 | 建议修法 | 受影响单测 |
|---|---|---|---|---|---|
| D1 | `search/SearchMatcher.kt:68`（`rank`，`:74` `take(cap)`） | `val cap = limit.coerceAtLeast(SearchTokens.RESULT_PAGE_LIMIT)` 把**任何小于 100 的 limit 都抬到 100**；传入 `limit = 3` 时实际 `take(100)`，5 条候选全量返回，得不到截断后的 3 条 | 注释（`:58`）写明「`limit ≤ 0` 时回落到 `RESULT_PAGE_LIMIT`」，但代码用 `coerceAtLeast(100)` 把正常的小 limit 也顶到页上限；边界条件实现与契约 / 测试预期相反 | 改为仅在非正时回落：`val cap = if (limit > 0) limit else SearchTokens.RESULT_PAGE_LIMIT`；其余 `coerceAtLeast(0)` 形式（`SearchHistory.kt:20,35,49`）夹紧下限 0 是正确的，无需改 | `SearchMatcherTest.kt:77-80`「rank truncates to limit」：5 条候选、`limit = 3` 断言 `size == 3`，现实现返回 5，预计断言失败；同文件 `:83-89` 负 limit 回落页上限的用例不受影响 |

---

## 4. 非阻断观察项（不构成本次 FAIL，建议后续跟进）

| 编号 | 文件:行号 | 现象 | 根因 / 评估 | 建议修法 |
|---|---|---|---|---|
| O1 | `font/FontRole.kt:4-6` ↔ `:138-150` | 体积 / 字号预算存在两份字面量副本：枚举构造引用的是顶层 `private const val`（`BODY_SUBSET_BUDGET / NUMBER_SUBSET_BUDGET / LABEL_SANS_SIZE`，`:4-6`）；companion 又对外暴露一套同名镜像常量（`BODY_SUBSET_BUDGET_BYTES / NUMBER_SUBSET_BUDGET_BYTES / LABEL_SANS_SIZE_SP / BODY_SUBSET_COMMON_CHARS / ASSET_DIR`，`:138-150`），供注册表与测试使用。KDoc（`:3`）所述「被 enum 引用的常量放顶层」与实现一致，注释无误 | 两套均为 `const val` 编译期常量，枚举参数处内联，**可编译、无初始化顺序问题**；仅属同一数值在两处各写一遍字面量，后续调预算时存在改一处漏一处的漂移风险，不是阻断 | 可选去重：让 companion 的公共常量引用顶层常量（`const val BODY_SUBSET_BUDGET_BYTES = BODY_SUBSET_BUDGET`），或反过来枚举引用 companion；保留单一真值来源 |
| O2 | `search/SearchTokens.kt:20-53` | token 对象内直接持有 `Color(0x…)`，未走 components 域「Palette 存 Long ARGB → Tokens 包 Color」两层拆分 | 与既有 `read/ink/InkTokens.kt` 的单子域单层写法一致，同域先例可接受；但与 `ComponentPalette`/`ComponentTokens` 两层收口模式不同。测试 `SearchContractTest` 也允许 hex 仅出现在本文件 | 保持现状即可；若要统一架构，可在搜索域补一个 Long palette 层，Tokens 只做 Color 包装。本次不计违规 |
| O3 | `components/ComponentPalette.kt:172,175`（`Long`）↔ `search/SearchTokens.kt:150,153`（`Double`） | 同名预算常量跨子域类型不一致：组件域 `FRAME_BUDGET_MS / MODEL_BUDGET_MS` 为 `Long`，搜索域为 `Double` | bench 断言 `Double <= Long` 靠标准 `compareTo` 编译通过；不阻断，但后续跨域复用预算口径时易踩隐式 widened 比较 | 统一为同一数值类型（建议模型 / 预算层一律 `Double` 或一律 `Long`），bench 层再做毫秒换算 |
| O4 | `font/FontSelectDialog.kt`、`font/FontAdapter.kt` | U9 仍为遗留 View 体系（`DialogFragment` / `ViewBinding` / `RecyclerView` / 系统菜单），与 U6/U7/U8 的 Compose 自绘体系并存 | 复用了 Legado 既有基类与扩展，静态依赖均能解析；属架构风格混合，非编译问题 | 后续阶段若要求 UI 全部 Compose 自绘，需把字体选择弹窗迁移为 Compose Sheet；本次范围内不动 |
| O5 | 三模块 bench（`ComponentBenchmark / HalfSheetBenchmark / InkSlateBenchmark / SearchBenchmark` 及对应 `*BenchTest`） | 耗时数字在宿主 JVM 测得，非荣耀 WIN 真机 | 测试注释已自行声明「JVM 数字不等于真机结论」；静态审查无法验证帧率 / 重组 / 绘制耗时 | 出 APK 后在真机跑 bench 与单测复核；本次 PASS 不含性能达标结论 |

---

## 5. 残留风险与未验证声明

1. **未编译**：本次仅静态读码与机械扫描，未执行 Gradle 编译；Kotlin 编译器插件（Compose 编译期校验、类型推导泛型实参、sealed 穷尽性的最终判定）未实际运行。编译通过≠功能可用，反之静态 PASS≠编译必过。
2. **资源链接未经 aapt 校验**：layout / menu / drawable / string / array 的存在性已人工核对，但最终资源合并与 ID 生成需构建期确认。
3. **单测 / bench 未运行**：测试能否在 JVM 通过、bench 是否落在预算内、空态 / 无网 / 同名消歧等兜底路径行为是否符合规格，均未执行验证。
4. **Compose 手势 / 重组 / 绘制**：`pointerInput`、`animateDpAsState`、`Crossfade`、`FlowRow`、`BasicTextField` 等 API 签名按当前 BOM（`compose-bom 2026.06.01`、Kotlin 2.4.20）核对，最终以编译器解析为准。
5. 静态扫描无法覆盖：运行期空指针、文件 / 资产读取失败、字体子集生成脚本（Python）执行结果、真机内存与帧率。

## 6. 扫描覆盖文件清单（63）

### U9 font（13）
- main：`app/src/main/java/io/legado/app/ui/font/AndroidFontSource.kt`、`FontAdapter.kt`、`FontAssetSource.kt`、`FontImport.kt`、`FontLoadResult.kt`、`FontRegistry.kt`、`FontRole.kt`、`FontSelectDialog.kt`、`InkFontLoader.kt`
- test：`app/src/test/java/io/legado/app/ui/font/FontModuleContractTest.kt`、`FontRegistryBenchTest.kt`、`FontRegistryTest.kt`、`FontSelectionStyleTest.kt`

### U6+U7 components（30）
- main：`app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt`、`ComponentState.kt`、`ComponentTokens.kt`、`HalfSheetModel.kt`、`InkBottomSheet.kt`、`InkButton.kt`、`InkCard.kt`、`InkEmptyState.kt`、`InkHalfSheet.kt`、`InkInteractionState.kt`、`InkToast.kt`、`Timeline.kt`、`inkslate/InkSlateCanvas.kt`、`inkslate/InkSlateModel.kt`、`inkslate/InkSlateToolbar.kt`
- test：`app/src/test/java/io/legado/app/ui/components/ComponentBenchTest.kt`、`ComponentBenchmark.kt`、`ComponentPaletteTest.kt`、`ComponentStateTest.kt`、`HalfSheetAnchorsTest.kt`、`HalfSheetBenchTest.kt`、`HalfSheetBenchmark.kt`、`HalfSheetContentTest.kt`、`SheetDetentTest.kt`、`TimelineModelTest.kt`、`ToastPaletteTest.kt`、`inkslate/InkSlateBenchTest.kt`、`inkslate/InkSlateBenchmark.kt`、`inkslate/InkSlateModelTest.kt`、`inkslate/InkSlateSvgTest.kt`

### U8 search（20）
- main：`app/src/main/java/io/legado/app/ui/main/search/SearchFallback.kt`、`SearchHighlight.kt`、`SearchHistory.kt`、`SearchHistoryPanel.kt`、`SearchInputBar.kt`、`SearchMatcher.kt`、`SearchModels.kt`、`SearchMotion.kt`、`SearchReducer.kt`、`SearchResultList.kt`、`SearchScreen.kt`、`SearchTokens.kt`
- test：`app/src/test/java/io/legado/app/ui/main/search/SearchBenchTest.kt`、`SearchBenchmark.kt`、`SearchContractTest.kt`、`SearchHighlightTest.kt`、`SearchHistoryTest.kt`、`SearchMatcherTest.kt`、`SearchModelsTest.kt`、`SearchReducerTest.kt`
