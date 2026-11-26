# 修复-androidTest 可修五件（Q-297）

锚点卡：docs/锚点/Q297-androidTest五件.md
- 范围：`app/src/androidTest/**` 中审计（`docs/审计-androidTest失修清点.md`，Q-292）判为「可修」的 5 个文件，逐条复核未解析 import，判定删除/改名/搬迁，按「最小改写」边界处置。
- 边界：只允许改这 5 个 androidTest 文件 + 本两份文档；未改 `app/src/main/**`、`app/src/test/**`、`data_scripts/**`、`.github/**`、`app/build.gradle`；未做 git 写操作；沙箱无 JDK，未跑 `./gradlew`、kotlinc、编译。
- 日期：2026-11-27。
- 结论先行：15 条硬错误中归属本批 5 文件的 10 条，**本单改掉 0 条**；5 个文件全部从「可修」**改判为「待裁」**。理由逐条见 §三：旧 Adapter / Fragment / ViewBinding 在 main 侧已删除，幸存的新 VM / Compose 分区与测试调用点签名或形态不等价，最小换 import 无法编过；无等价 API 承接，按口令不许编造目标。

## 一、复核口径（与 Q-292 审计一致）

- 声明索引根：`app/src/main/java`、`app/src/debug/java`、`app/src/cronetRuntime/java`、`app/src/pdfRuntime/java`、`modules/book/src/main/java`、`modules/rhino/src/main/java`。
- 声明形态：`class` / `object` / `interface` / `enum class` / `annotation class` / `typealias` / 顶层 `fun` / `val` / `var`（含扩展 receiver）。
- 第三方前缀跳过：`android.*`、`androidx.*`、`java.*`、`javax.*`、`kotlin.*`、`kotlinx.*`、`org.junit`、`org.hamcrest`、`okhttp3`、`okio.*`、`com.google.*`、`io.github.*` 等；`R` 生成类跳过；`databinding.*` 按 `res/layout` XML 存在性判定。
- 复核脚本为仓外 scratch（`../scratch/import_resolve.py`，不在仓内、不改 `data_scripts/**`），口径复刻审计 §三。
- 本批 5 文件 import 语句合计 261 条；其中 2 条通配第三方导入（`org.junit.Assert.*`，B L48、D L87）按口径跳过；逐条比对 259 条，机检未解析 10 条，与 Q-292 审计清单逐条吻合（其余 249 条在 main 侧或已知第三方包解析命中）。

## 二、5 文件逐条未解析清单（改前实测，文件:行号 + 缺失符号）

### A. `ui/association/RuleSelectionShareTest.kt`（4 @Test，37 imports，2 未解析）

1. L24 `io.legado.app.ui.dict.rule.DictRuleAdapter` —— **类已删除**。main 全仓 `class/object/interface DictRuleAdapter` 零声明；残留仅 KDoc 提及：`app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt:48`、`app/src/test/java/io/legado/app/BundlePayloadContractTest.kt:97`。
2. L26 `io.legado.app.ui.replace.ReplaceRuleAdapter` —— **类已删除**。main 全仓零声明；残留仅 KDoc：`app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt:54`。

### B. `ui/book/source/BookSourceCheckUiTest.kt`（5 @Test，53 imports，2 未解析）

3. L33 `io.legado.app.databinding.ItemBookSourceBinding` —— **布局缺失，binding 无法生成**。`app/src/main/res/layout/` 无 `item_book_source.xml`；行渲染已收口 Compose（`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt:33` 注释自述替代旧 Adapter + 旧 XML）。
4. L42 `io.legado.app.ui.book.source.manage.BookSourceAdapter` —— **类已删除**。main 全仓零声明；`app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:46` KDoc 自述旧编排已拆。

### C. `ui/book/source/SourceOrderMetadataUpdateTest.kt`（2 @Test，25 imports，2 未解析）

5. L16 `io.legado.app.ui.replace.ReplaceRuleAdapter` —— **类已删除**（同 A-2）。
6. L17 `io.legado.app.ui.replace.ReplaceRuleViewModel` —— **改名存活**为 `ReplaceRuleListViewModel`（`app/src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt:28`），但测试调用点签名不等价，见 §三-C。

### D. `ui/code/CodeSelectionUiTest.kt`（21 @Test，93 imports，2 未解析）

7. L81 `io.legado.app.ui.book.source.edit.BookSourceEditAdapter` —— **类已删除**。main 全仓零声明；`app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt:73` KDoc 自述替代旧 Adapter + TabLayout。
8. L82 `io.legado.app.ui.book.source.edit.BookSourceEditViewModel` —— **改名存活**为 `SourceEditViewModel`（`app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditViewModel.kt:51`），但属性/成员形态不等价，见 §三-D。

### E. `ui/widget/image/CoverTitleAdaptiveUiTest.kt`（8 @Test，51 imports，2 未解析）

9. L36 `io.legado.app.ui.config.CoverConfigFragment` —— **类已删除**。main 全仓零声明；`app/src/main/java/io/legado/app/ui/config/sections/CoverSection.kt:18` KDoc 自述对应旧 Fragment。
10. L37 `io.legado.app.ui.config.CoverFontConfigFragment` —— **类已删除**。main 全仓零声明；`app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt:36` KDoc 自述对应旧 Fragment。

## 三、旧 → 新映射（逐条文字）与不等价证据

### A. RuleSelectionShareTest.kt

- 旧 `recycler.adapter as ReplaceRuleAdapter`（L90）→ 候选新主控 `ReplaceRuleListViewModel`（`app/src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt:28`）；旧 `as DictRuleAdapter`（L154）→ 候选 `DictRuleListViewModel`（`app/src/main/java/io/legado/app/ui/dict/rule/DictRuleListViewModel.kt:27`）。
- 不等价事实：
  - 两个宿主 Activity 均已 Compose 化：`app/src/main/java/io/legado/app/ui/replace/ReplaceRuleActivity.kt:64`、`app/src/main/java/io/legado/app/ui/dict/rule/DictRuleActivity.kt:54` 调 `setContent`；两文件内 `RecyclerView` / `recycler_view` 零引用，测试 `findViewById<RecyclerView>(R.id.recycler_view)` 在新层级取不到视图。
  - 测试经 Adapter 成员读状态：`getItems()`（L91/L92/L155）、`dragSelectCallback.onSelectChange(index, true)`（L93/L157）、`selection`（L94/L158）。新 VM 选择态在 `uiState.value.selection`，操作入口为 `toggleSelect` / `selectAll` / `revertSelection` / `clearSelection`（app/src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt:116-136；app/src/main/java/io/legado/app/ui/dict/rule/DictRuleListViewModel.kt:65-85），但 main 侧**不存在**任何承载 `getItems/selection/dragSelectCallback` 的 Adapter 类型可供强转承接。
  - 两个新 Screen 全文件 grep `testTag` / `TestTag` / `semantics` 零命中，Compose 节点无可定位钩子。
  - 附加资源缺口：L153 `R.menu.dict_rule_sel`，`res/menu/` 无 `dict_rule_sel.xml`（全仓零命中），旧字典选择分享菜单随栈拆除未补等价入口。
- 不阻塞分支：highlight 条（L59 `HighlightRuleAdapter`，`app/src/main/java/io/legado/app/ui/highlight/HighlightRuleAdapter.kt:18` 存活）、toc 条（L127 `TxtTocRuleAdapter`，`app/src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt:20` 存活）走旧 RecyclerView 栈，import 级可解析。
- **判定：待裁。** 缺什么：① 两个新 Screen 的 Compose 测试定位钩子（testTag/语义）；② 可由 instrumentation 线程读取的 VM 规则列表与选中集合出口；③ 字典分支分享菜单在新屏的等价触发路径。

### B. BookSourceCheckUiTest.kt

- 旧 `recycler.adapter as BookSourceAdapter`（L149/L375）→ 候选新主控 `SourceManageViewModel`（`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:56`）；旧 `ItemBookSourceBinding.bind(row)`（L138/L178/L260/L307/L384）→ 候选 Compose 行 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt`。
- 不等价事实：
  - `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:78` 已 `setContent`；旧行 ViewBinding 随 `item_book_source.xml` 一并移除，main 侧无等价 binding 类可生成。
  - 测试读取的三个行字段 `cbBookSource`、`tvBookshelfCount`、`ivDebugText` 在 main 全仓（kt + xml）零命中；新行以 Compose 文本/徽章表达书架计数（`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt:108-120` `bookshelfCount`）与调试文案（L152 `Debug.debugMessageMap`），无 ViewBinding 字段可取。
  - 附加资源缺口（import 机检之外的编译级 R 成员风险）：`R.id.check_status_filter`、`R.id.menu_show_source_bookshelf_count`、`R.id.menu_block_source_navigation`、`R.id.menu_show_source_check_status` 在 main `res/` 与 kt 全量零命中；即使 binding/adapter 修好，这四个 R 成员引用仍会编译失败。
- 不阻塞分支：两条纯 DAO / `CheckSource` 逻辑用例（`realServicePersistsFailureAndSuccessWithoutChangingGroups` L314、`nonHttpDomainIsUncheckableAfterOtherChecksPass` L336）不触 binding/adapter。
- **判定：待裁。** 缺什么：① 行级 Compose 节点定位钩子（勾选框、书架计数文案、调试文案可见性）；② VM uiState 暴露当前过滤列表与选中集合供 instrumentation 断言；③ 四个旧菜单/筛选项在新屏的等价控件与 id/语义出口。

### C. SourceOrderMetadataUpdateTest.kt

- 旧 `ReplaceRuleViewModel`（L132/L176/L179）→ 候选 `ReplaceRuleListViewModel`（改名存活）；旧 `as ReplaceRuleAdapter`（L156/L165）→ 新栈无 Adapter。
- 签名不等价事实（换 import 后仍编不过）：
  - 旧 `vm.move(stale[0].id, stale[2].id, true)`（L132，三参：两个实体 id + after）；新 `fun move(rule: ReplaceRuleUi, down: Boolean)`（`app/src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt:182`，Ui 模型 + 布尔方向），参数形态不兼容。
  - 旧 `vm.enableSelection(stale)` / `disableSelection(stale)`（L176/L179，收实体列表）；新 `enableSelection()` / `disableSelection()`（L212/L216）无参，选中集合取自 uiState，参数形态不兼容。
  - 旧 Adapter 行检视 `getItems()`（L157/L158/L166）、`setItem(position, stale[0])`（L168）在新栈无承接类型。
- 不阻塞分支：RSS 条（`staleRssSwitchAndSelectionPreserveCommittedOrderAndMetadata`）全程 `RssSourceAdapter`（`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt:22`）+ `RssSourceViewModel`（`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt:24`），签名匹配：`move(sourceUrl, targetUrl, after, onFinally)`（`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt:62`）、`enableSelection(sources: List<RssSource>)`（`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt:75`）、`disableSelection(sources)`（`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt:81`）。
- **判定：待裁。** 缺什么：① 新 VM 暴露按实体 id 的相邻移动、按列表批量启用/停用的等价方法（或确认测试改走 Ui 模型 + 无参批量 API 的改写方案）；② Compose 行内开关的可定位钩子；③ Replace 分支列表顺序与陈旧快照的可读出口。

### D. CodeSelectionUiTest.kt

- 旧 `as BookSourceEditAdapter`（L569/L651/L682）→ 新栈 `SourceEditScreen.kt`（Compose）；旧 `ViewModelProvider(...)[BookSourceEditViewModel::class.java]`（L684）→ 候选 `SourceEditViewModel`（改名存活）。
- 不等价事实：
  - 旧 Adapter `editEntities`（L570/L653/L683）在新栈不存在；新状态为 `uiState: SourceEditUiState`，字段集 `fields: Map<EditTab, List<EditField>>`（`app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditState.kt:46-48`），无 `editEntities` 列表属性。
  - 旧清理块 `vm.bookSource?.jsLib = it.value`（L684，可变属性直写）；`SourceEditViewModel` 无公开可变 `bookSource` 属性，字段写入走 `updateField(tab, key, value)`（L78），源快照在 `state.source`（L127）。
  - `app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt:133` 已 `setContent`，`recycler_view` 旧列表层级不存在。
- 耦合面：21 条中仅 `oversizedJsLibOpensFromItsSourceFieldAndSavesWithoutLosingText` 一条 + 其 finally 清理块触这两个符号；其余 20 条经 `CodeEditActivity` / `CodeEditViewModel`（`ui/code/` 存活）。反射点复核存活：`CodeEditActivity.search()`（`app/src/main/java/io/legado/app/ui/code/CodeEditActivity.kt:795`）、`searchTxt(txt)`（`app/src/main/java/io/legado/app/ui/code/CodeEditActivity.kt:886`）。
- 附加运行期风险（import 级可解析，不计入本单硬错误）：第二长用例 `sourcePreviewEditsReturnToTheSameBookAndRssCandidateWithoutLosingLongCode` 仍 `findViewById<RecyclerView>(R.id.recycler_view)`（L736/L742/L743），L1464/L1465 另有 `withId(R.id.recycler_view)` 层级断言；视图是否在新层级存活待运行期复核。
- **判定：待裁。** 缺什么：① 编辑页 Compose 字段行 testTag 与字段值读取出口；② VM 暴露当前源字段集（jsLib 等值）供断言；③ 清理块回写 jsLib 的等价 VM 方法。

### E. CoverTitleAdaptiveUiTest.kt

- 旧 `findFragmentByTag(...) as CoverConfigFragment`（L285/L397）→ 候选 Composable `CoverSection(actions)`（`app/src/main/java/io/legado/app/ui/config/sections/CoverSection.kt:24`）；旧 `as CoverFontConfigFragment`（L316 `is`、L445 强转）→ 候选 `CoverFontSection(actions)`（`app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt:42`）。
- 不等价事实：
  - `app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt:109` `setContent`，L136-137 按 `ConfigTag` 分发到 Compose Section；FragmentManager 中不存在按 `COVER_CONFIG` / `COVER_FONT_CONFIG` tag 挂载的 Fragment，`findFragmentByTag` 返回 null。
  - 旧 Preference 树 API `scrollToPreference(key)`（L286/L398/L445）在 main 全仓零命中；两个新 Section 为 Compose 自绘，无 Preference 屏可滚动定位。
  - 两个新 Section grep `testTag` / `semantics` 零命中。
- 不阻塞分支：5 条位图直测（`adaptiveToggleChangesTheActualRenderedCover`、`customSizesChangeActualPixelsAndDisablingRestoresBothOriginalStyles`、`eachTitleAndAuthorSizeReachesHorizontalAndVerticalRendering`、`equalCustomNumbersGiveEqualGlyphGeometryInEveryLayout`、`selectedFontChangesTitleAndAuthorPixelsAndDefaultRestoresEveryLayout`）不触 Fragment；反射点 `CoverImageView.currentNameBitmap` 存活（`app/src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt:104`）。
- 阻塞分支：3 条设置链路——`fontSizeMenuPersistsAllFourPickersAcrossRecreation`（L275）、`recordCoverPreferencesCopyBothSelectedImagesThroughTheActivityResult`（L360）、`coverSettingsToggleUpdatesRuntimeImmediately`（L423，经辅助函数 `scrollCoverStylePreference` L440-448）。
- **判定：待裁。** 缺什么：① Compose Section 内偏好行 testTag 与滚动/定位出口；② 字号盘与封面图选择入口的 Compose 测试可触达路径；③ 重建后 Section 状态持久化的可断言出口。

## 四、改动文件清单与断言零变化回证

- 本单**未修改**这 5 个 kt 文件中的任何一行；`git diff -U0` 删/增行集合如下（仅含工作树中他人先留的未提交改动，非本单产出）：
  - `CoverTitleAdaptiveUiTest.kt` 三 hunk：`import ...AboutActivity` → `...AboutInkActivity`；字段 `ActivityScenario<AboutActivity>?` → `<AboutInkActivity>?`；`launch(AboutActivity::class.java)` → `launch(AboutInkActivity::class.java)`。三行分别是 import、字段类型声明、启动调用，**无一条是断言行**；用例名、断言表达式、测试意图均未被本单触碰。
  - 其余 4 个文件 `git diff` 为空。
- 断言零变化回证：本单无写入，故不存在本单引入的断言行增删；上述三 hunk 的删/增行逐条核对，均不命中 `assert` / `assertEquals` / `assertTrue` / `assertFalse` / `assertNull` / `assertArrayEquals` / `assertThat` 形态。
- 未执行任何 git 写操作。

## 五、自证结果

### 5.1 import 全解析复核（仓外 scratch 脚本，口径见 §一）

- 原始 import 语句 261 条；跳过 2 条通配第三方后逐条比对 259 条，未解析 10 条（即 §二 清单），其余 249 条解析命中；逐条 UNRESOLVED 输出：

```
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/association/RuleSelectionShareTest.kt:24 io.legado.app.ui.dict.rule.DictRuleAdapter (本地声明索引零命中)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/association/RuleSelectionShareTest.kt:26 io.legado.app.ui.replace.ReplaceRuleAdapter (本地声明索引零命中)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt:33 io.legado.app.databinding.ItemBookSourceBinding (databinding 按布局 XML 判定)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt:42 io.legado.app.ui.book.source.manage.BookSourceAdapter (本地声明索引零命中)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/book/source/SourceOrderMetadataUpdateTest.kt:16 io.legado.app.ui.replace.ReplaceRuleAdapter (本地声明索引零命中)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/book/source/SourceOrderMetadataUpdateTest.kt:17 io.legado.app.ui.replace.ReplaceRuleViewModel (本地声明索引零命中)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/code/CodeSelectionUiTest.kt:81 io.legado.app.ui.book.source.edit.BookSourceEditAdapter (本地声明索引零命中)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/code/CodeSelectionUiTest.kt:82 io.legado.app.ui.book.source.edit.BookSourceEditViewModel (本地声明索引零命中)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/widget/image/CoverTitleAdaptiveUiTest.kt:36 io.legado.app.ui.config.CoverConfigFragment (本地声明索引零命中)
UNRESOLVED app/src/androidTest/java/io/legado/app/ui/widget/image/CoverTitleAdaptiveUiTest.kt:37 io.legado.app.ui.config.CoverFontConfigFragment (本地声明索引零命中)
IMPORT-CHECK: 259 imports, 10 unresolved
```

- 分文件计数（逐条比对口径，通配跳过）：RuleSelectionShareTest 37/2 未解析；BookSourceCheckUiTest 53/2（原始 54，通配 1）；SourceOrderMetadataUpdateTest 25/2；CodeSelectionUiTest 93/2（原始 94，通配 1）；CoverTitleAdaptiveUiTest 51/2。

### 5.2 `kotlin-syntax-lint.py` 逐文件末行（5 次）

```
=== RuleSelectionShareTest.kt
KOTLIN-LINT: PASS（1 个文件）
=== BookSourceCheckUiTest.kt
KOTLIN-LINT: PASS（1 个文件）
=== SourceOrderMetadataUpdateTest.kt
KOTLIN-LINT: PASS（1 个文件）
=== CodeSelectionUiTest.kt
KOTLIN-LINT: PASS（1 个文件）
=== CoverTitleAdaptiveUiTest.kt
KOTLIN-LINT: PASS（1 个文件）
```

口径提醒：该 lint 只做括号配平、条件尾逗号、fontFamily 误传、新文件超行、种子表包名、符号出处（含 HEAD 存量容忍集）、`.dp/.sp` 扩展属性、可见性传递；**不证明 unresolved import 能编译**，存量容忍集会吸收 HEAD 已存在的缺失符号。

### 5.3 候选新类型 main 侧声明存在性（grep 回证）

- `app/src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt:28`：`class ReplaceRuleListViewModel(application: Application) : BaseViewModel(application) {`
- `app/src/main/java/io/legado/app/ui/dict/rule/DictRuleListViewModel.kt:27`：`class DictRuleListViewModel(application: Application) : BaseViewModel(application) {`
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:56`：`class SourceManageViewModel(application: Application) : BaseViewModel(application) {`
- `app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditViewModel.kt:51`：`class SourceEditViewModel(application: Application) : BaseViewModel(application) {`
- `app/src/main/java/io/legado/app/ui/config/sections/CoverSection.kt:24`：`fun CoverSection(actions: SectionActions) {`
- `app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt:42`：`fun CoverFontSection(actions: SectionActions) {`

说明：这 6 个候选符号在 main 侧确实存在，但 §三 已逐条证明其形态/签名与测试调用点不等价；存在性不等于可承接旧调用，故不改判可修。

### 5.4 E1 命令证据（47 处定位逐条核对，命令可在当前 HEAD 复跑）

核对口径：在仓根执行；每条定位先解析文件是否存在，再 `sed -n 'Np'` 回读该行，比对行内容与引用主张（类声明 / KDoc 自述 / `setContent` / 方法签名 / 字段定义 / 反射成员）是否吻合。以下输出为关键行节选，命令原样可复跑。

#### 5.4.1 被删符号 main 侧声明零命中（对应 §二 1/2/4/7/9/10 与 §二-6/8）

```
$ grep -rn "class DictRuleAdapter\|object DictRuleAdapter\|interface DictRuleAdapter\|class ReplaceRuleAdapter\|object ReplaceRuleAdapter\|interface ReplaceRuleAdapter\|class BookSourceAdapter\|class BookSourceEditAdapter\|class CoverConfigFragment\|class CoverFontConfigFragment\|class ReplaceRuleViewModel\|class BookSourceEditViewModel" app/src/main app/src/debug app/src/cronetRuntime app/src/pdfRuntime modules/book/src/main/java modules/rhino/src/main/java
（exit=1，零命中）
```

#### 5.4.2 main 侧锚点逐行 `sed -n` 回读（节选真实输出）

```
$ sed -n '48p' app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt
 * 功能对照旧 `DictRuleActivity` + `DictRuleAdapter`：

$ sed -n '97p' app/src/test/java/io/legado/app/BundlePayloadContractTest.kt
         * 列表壳已 Compose 化的入口：旧 BookSourceAdapter / DictRuleAdapter / ReplaceRuleAdapter

$ sed -n '54p' app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt
 * 功能对照旧 `ReplaceRuleActivity` + `ReplaceRuleAdapter` + `GroupManageDialog`：

$ sed -n '33p' app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt
 * 落文 · 书源管理列表行（Compose 自绘，替代旧 BookSourceAdapter / item_book_source.xml）。

$ sed -n '46p' app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt
 * 旧编排：ActivityBookSourceBinding + BookSourceAdapter + 旧菜单 + Snackbar + 多个 AlertDialog，

$ sed -n '28p' app/src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt
class ReplaceRuleListViewModel(application: Application) : BaseViewModel(application) {

$ sed -n '73p' app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt
 * 落文 · 书源编辑页（Compose 自绘，替代旧 BookSourceEditActivity + BookSourceEditAdapter + TabLayout）。

$ sed -n '51p' app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditViewModel.kt
class SourceEditViewModel(application: Application) : BaseViewModel(application) {

$ sed -n '18p' app/src/main/java/io/legado/app/ui/config/sections/CoverSection.kt
 * 封面分区（对应旧 pref_config_cover.xml + CoverConfigFragment）。

$ sed -n '36p' app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt
 * 封面字体分区（对应旧 pref_config_cover_font.xml + CoverFontConfigFragment）。
```

```
$ sed -n '27p' app/src/main/java/io/legado/app/ui/dict/rule/DictRuleListViewModel.kt
class DictRuleListViewModel(application: Application) : BaseViewModel(application) {

$ sed -n '64p' app/src/main/java/io/legado/app/ui/replace/ReplaceRuleActivity.kt
        setContent {

$ sed -n '54p' app/src/main/java/io/legado/app/ui/dict/rule/DictRuleActivity.kt
        setContent {

$ sed -n '116p' app/src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt
    fun toggleSelect(id: Long) {

$ sed -n '65p' app/src/main/java/io/legado/app/ui/dict/rule/DictRuleListViewModel.kt
    fun toggleSelect(name: String) {

$ sed -n '18p' app/src/main/java/io/legado/app/ui/highlight/HighlightRuleAdapter.kt
class HighlightRuleAdapter(context: Context, private val callBack: CallBack) :

$ sed -n '20p' app/src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt
class TxtTocRuleAdapter(context: Context, private val callBack: CallBack) :

$ sed -n '56p' app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt
class SourceManageViewModel(application: Application) : BaseViewModel(application) {

$ sed -n '78p' app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt
        setContent {

$ sed -n '108p' app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt
    bookshelfCount: Int,

$ sed -n '182p' app/src/main/java/io/legado/app/ui/replace/ReplaceRuleListViewModel.kt
    fun move(rule: ReplaceRuleUi, down: Boolean) {

$ sed -n '22p' app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt
class RssSourceAdapter(context: Context, val callBack: CallBack) :

$ sed -n '46p' app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditState.kt
data class SourceEditUiState(

$ sed -n '133p' app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt
        setContent {

$ sed -n '24p' app/src/main/java/io/legado/app/ui/config/sections/CoverSection.kt
fun CoverSection(actions: SectionActions) {

$ sed -n '42p' app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt
fun CoverFontSection(actions: SectionActions) {

$ sed -n '109p' app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt
        setContent {

$ sed -n '104p' app/src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt
    private var currentNameBitmap: Pair<String, Bitmap>? = null
```

#### 5.4.3 补充存活锚点（RSS VM / 编辑 VM / 分区分发 / 调试文案 / 扩展函数 / 反射点 / 状态字段）

```
$ sed -n '24p' app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt
class RssSourceViewModel(application: Application) : BaseViewModel(application) {
$ sed -n '62p' app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt
    fun move(sourceUrl: String, targetUrl: String, after: Boolean, onFinally: () -> Unit = {}) {
$ sed -n '75p' app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt
    fun enableSelection(sources: List<RssSource>) {
$ sed -n '81p' app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceViewModel.kt
    fun disableSelection(sources: List<RssSource>) {

$ sed -n '78p' app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditViewModel.kt
    fun updateField(tab: EditTab, key: String, value: String?) {
$ sed -n '127p' app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditViewModel.kt
        val source = state.source?.copy() ?: BookSource()

$ sed -n '136p' app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt
                    ConfigTag.COVER_CONFIG -> CoverSection(actions)
$ sed -n '137p' app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt
                    ConfigTag.COVER_FONT_CONFIG -> CoverFontSection(actions)

$ sed -n '152p' app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt
    val liveMsg = if (Debug.isChecking) Debug.debugMessageMap[source.bookSourceUrl].orEmpty() else ""

$ sed -n '177p' app/src/main/java/io/legado/app/utils/ContextExtensions.kt
val Context.defaultSharedPreferences: SharedPreferences
$ sed -n '365p' app/src/main/java/io/legado/app/utils/ContextExtensions.kt
val Context.externalFiles: File
$ sed -n '75p' app/src/main/java/io/legado/app/utils/GsonExtensions.kt
inline fun <reified T> Gson.fromJsonArray(json: String?): Result<List<T>> {

$ sed -n '795p' app/src/main/java/io/legado/app/ui/code/CodeEditActivity.kt
    private fun search() {
$ sed -n '886p' app/src/main/java/io/legado/app/ui/code/CodeEditActivity.kt
    private fun searchTxt(txt: String) {

$ sed -n '47p' app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditState.kt
    val source: BookSource? = null,
$ sed -n '48p' app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditState.kt
    val fields: Map<EditTab, List<EditField>> = emptyMap(),
```

#### 5.4.4 androidTest 10 条未解析 import 实测行

```
$ grep -n "DictRuleAdapter\|ReplaceRuleAdapter\|ItemBookSourceBinding\|BookSourceAdapter\|ReplaceRuleViewModel\|BookSourceEditAdapter\|BookSourceEditViewModel\|CoverConfigFragment\|CoverFontConfigFragment" \
  app/src/androidTest/java/io/legado/app/ui/association/RuleSelectionShareTest.kt \
  app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt \
  app/src/androidTest/java/io/legado/app/ui/book/source/SourceOrderMetadataUpdateTest.kt \
  app/src/androidTest/java/io/legado/app/ui/code/CodeSelectionUiTest.kt \
  app/src/androidTest/java/io/legado/app/ui/widget/image/CoverTitleAdaptiveUiTest.kt | grep import
app/src/androidTest/java/io/legado/app/ui/association/RuleSelectionShareTest.kt:24:import io.legado.app.ui.dict.rule.DictRuleAdapter
app/src/androidTest/java/io/legado/app/ui/association/RuleSelectionShareTest.kt:26:import io.legado.app.ui.replace.ReplaceRuleAdapter
app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt:33:import io.legado.app.databinding.ItemBookSourceBinding
app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt:42:import io.legado.app.ui.book.source.manage.BookSourceAdapter
app/src/androidTest/java/io/legado/app/ui/book/source/SourceOrderMetadataUpdateTest.kt:16:import io.legado.app.ui.replace.ReplaceRuleAdapter
app/src/androidTest/java/io/legado/app/ui/book/source/SourceOrderMetadataUpdateTest.kt:17:import io.legado.app.ui.replace.ReplaceRuleViewModel
app/src/androidTest/java/io/legado/app/ui/code/CodeSelectionUiTest.kt:81:import io.legado.app.ui.book.source.edit.BookSourceEditAdapter
app/src/androidTest/java/io/legado/app/ui/code/CodeSelectionUiTest.kt:82:import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
app/src/androidTest/java/io/legado/app/ui/widget/image/CoverTitleAdaptiveUiTest.kt:36:import io.legado.app.ui.config.CoverConfigFragment
app/src/androidTest/java/io/legado/app/ui/widget/image/CoverTitleAdaptiveUiTest.kt:37:import io.legado.app.ui.config.CoverFontConfigFragment
```

#### 5.4.5 缺失布局 / 菜单 / R 成员零命中

```
$ ls app/src/main/res/layout/item_book_source.xml
ls: cannot access 'app/src/main/res/layout/item_book_source.xml': No such file or directory
$ ls app/src/main/res/menu/dict_rule_sel.xml
ls: cannot access 'app/src/main/res/menu/dict_rule_sel.xml': No such file or directory
$ grep -rn "check_status_filter\|menu_show_source_bookshelf_count\|menu_block_source_navigation\|menu_show_source_check_status" app/src/main/res app/src/main/java
（exit=1，零命中）
```

#### 5.4.6 行字段与 Compose 定位钩子零命中

```
$ grep -rn "cbBookSource\|tvBookshelfCount\|ivDebugText" app/src/main app/src/debug app/src/cronetRuntime app/src/pdfRuntime
（exit=1，零命中）
$ grep -c "testTag\|TestTag\|semantics" app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt \
  app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt \
  app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt \
  app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt \
  app/src/main/java/io/legado/app/ui/config/sections/CoverSection.kt \
  app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt
app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt:0
app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt:0
app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt:0
app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt:0
app/src/main/java/io/legado/app/ui/config/sections/CoverSection.kt:0
app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt:0
```

### 5.5 证据修正表（本单核对后改动的引用；结论与论证未改写）

- 37 处简写 / 省略号路径补全为全仓相对路径：门禁 ⑭ 按仓根、`app/`、`data_scripts/` 三个前缀解析文件，原报告写 `ui/.../X.kt:NN`、裸文件名 `X.kt:NN` 或 `app/src/test/.../Y.kt:NN`，解析不到文件被记幻影；实跑这 37 处文件均存在、行号在界内、行内容与主张吻合，仅路径形态不可机检。修正方式：路径补全为 `app/src/main/java/io/legado/app/...` / `app/src/test/java/...` 全仓相对路径，行号与结论不动。
- 省略号路径 1 处：原引用 `app/src/test/.../BundlePayloadContractTest.kt` 行 97（路径中段含 `...` 省略号，机检解析不到文件）→ 更正为 `app/src/test/java/io/legado/app/BundlePayloadContractTest.kt:97`（实跑文件 119 行，该行命中三旧 Adapter 提及）。
- 分流锚点 1 组：`fromJsonArray` 原随 `ContextExtensions.kt` 行 177 一并列举，实跑定义在 `app/src/main/java/io/legado/app/utils/GsonExtensions.kt:75`；`externalFiles` 实跑在 `app/src/main/java/io/legado/app/utils/ContextExtensions.kt:365`。已在 §6.1 拆开标注。
- 行号漂移 1 处（测试文件内部 `Lxx` 标注，不在 §二 47 处 `文件:行号` 集合内）：D 段旧 Adapter `editEntities` 实测 L570 / L653 / L683，原写 L570 / L652 / L684，已更正。
- 找不到任何对应文件的定位：0 处。

## 六、自查反例与改坏推演（必写三条）

### 6.1 改 import 漏改 `as X` / 全限定写法 / 伴生对象访问，会不会仍然编不过

- 本批先做了全量排查：5 文件无 `import ... as` 别名；正文无 `io.legado.*` 全限定写法；无 `.Companion.` 显式访问。
- 强转 / 类型判断点已全部定位，后续若做迁移换名，必须连同以下点位一起改，否则换了 import 仍编不过：
  - `as ReplaceRuleAdapter`：A L90、C L156/L165；`as DictRuleAdapter`：A L154。
  - `as BookSourceAdapter`：B L149/L375；`as BookSourceEditAdapter`：D L569/L651；`as? BookSourceEditAdapter`：D L682。
  - `as CoverConfigFragment`：E L285/L397；`is CoverFontConfigFragment`：E L316；`as CoverFontConfigFragment`：E L445。
  - VM 泛型查取：C L132/L176/L179 `ViewModelProvider(...)[ReplaceRuleViewModel::class.java]`；D L684 `[BookSourceEditViewModel::class.java]`。
  - `X::class.java` 传给 ActivityScenario：A L58/L89/L126/L153、B L86/L284、C L46/L126、E L85/L282/L392/L426；改名时 `::class.java` 表达式同样要换。
- 伴生对象 / 顶层扩展：本批未发现经伴生对象访问的旧符号；扩展函数 `defaultSharedPreferences`（`app/src/main/java/io/legado/app/utils/ContextExtensions.kt:177`）、`externalFiles`（`app/src/main/java/io/legado/app/utils/ContextExtensions.kt:365`）存活，`fromJsonArray` 在 `app/src/main/java/io/legado/app/utils/GsonExtensions.kt:75` 存活，均属 `utils/`，解析命中。

### 6.2 这些测试原本验证的是已删除的旧 UI，import 修好后语义是否还成立

- 不成立。本批 10 条缺失符号背后的被测对象是已拆除的旧编排：旧 RecyclerView + Adapter（A/B/C/D）、旧行 ViewBinding（B）、旧 PreferenceFragment + 偏好树（E）。新栈是 Compose Screen / Section + VM uiState。
- 即使把 import 换成候选新类型，运行期仍会踩：`findViewById<RecyclerView>(...)` 在 Compose 层级返回 null；Adapter 强转 `ClassCastException`；`findFragmentByTag` 返回 null；`scrollToPreference` 无对象可调。
- 结论：**本单未声称任何用例已修好**。import 止血（若后续另单实施）只解决编译期符号解析；UI 断言需另单按 Compose UI test（`createComposeRule` + testTag + VM state 断言）重写，用例意图（仅分享勾选项、书架计数跟踪、陈旧快照保序、长代码回显、封面设置即时回写）可保留，执行机制必须重做。
- 可独立保留的部分：A 的 highlight/toc 两条、B 的两条 DAO/校验逻辑、C 的 RSS 条、D 的其余 20 条选中/分享行为、E 的 5 条位图直测；这些是否下沉 JVM 单测或改 Compose 用例，另单裁决。

### 6.3 androidTest 无 CI 覆盖，「修好」如何被验证

- 本单自证仅三件静态判据：① lint 逐文件 PASS（§5.2，语法级）；② import 全解析复核（§5.1，249 命中 + 10 条仍未解析并列明）；③ 候选新类型在 main 侧有声明（§5.3，grep 行贴出）。
- **编译级未验证**：沙箱无 JDK，未跑 `./gradlew` / kotlinc / `assembleDebugAndroidTest`；CI 现有 Verify 只编 JVM 单测、Package 只 `assembleDebug`，androidTest 源集无任何编译 job（口径见 Q-292 §五）。
- 静态门禁覆盖不到的风险：反射成员失效（50 处量级，本批复活两点见 §七-4）、资源 id/字符串漂移、Compose 节点缺失、运行期视图层级不存在、方法签名漂移、Flow/StateFlow 时序。这些必须等 emulator/真机 instrumentation 或 `assembleDebugAndroidTest` 编译 job 落地后才能闭环。

### 6.4 证据定位本身的三条反例推演

- **a) 幻影定位直接删掉，是否比替换更干净。** 不能删。这 37 处经实跑文件都存在、行内容与主张吻合，被门禁记幻影只是简写路径不可机检；若直接删掉，「旧 Adapter/Fragment 在 main 侧已删除、新 VM/Section 形态不等价」这些结论就只剩断言、没有可回源的落点，下任代理无法复核结论从哪来，报告退化成空话。正确动作是把路径补全到可解析的全仓相对路径，保留原行号与主张；只有实跑确认文件与行内容都找不到时，才按 §5.5 格式标注「原引用未找到」并给替代证据，而不是抹掉证据位。
- **b) E1 贴「命令 + 节选输出」如何保证可复现，行号漂移怎么办。** 命令一律写成仓根相对路径的 `grep -n ... <path>` / `sed -n 'Np' <path>`，不依赖会话目录、不贴临时绝对路径；输出节选贴在对应命令下方，便于逐条比对。行号会随他人后续改动漂移，兜底口径有三条：① 锚点优先钉在符号声明行（`class XxxViewModel`、`fun move(...)`、字段定义），这类行比调用点稳定；② 命令可复跑——后来者在新 HEAD 上重跑同一 `grep -n`，若行号变动但符号仍在，按 grep 新输出回填行号即可，主张不依赖死行号；③ 本单在 §5.5 已登记一处实测漂移（D 段 `editEntities` 652→653、684→683），示范漂移后的修正方式，不把旧行号伪装成当前事实。
- **c) 把定位降级成「泛泛文件名」（只写 `XxxTest` 不写行号）为什么是降级。** 文件名只能证明符号在哪个文件，不能证明主张落在具体哪一行；同文件内可能既有存活调用点也有已删符号残留，不钉行号就无法区分。门禁 E2 要求 `文件:行号` 形态、⑭ 还要回源校验行号在文件长度内，正是为了堵「只给文件名、证据不可定位」的口子。降级成文件名会让不等价主张（三参 `move` vs 两参 `move`、有参 `enableSelection` vs 无参批量 API）无法被逐行复核，等于把证据阈值从「可逐字对照」降回「凭印象相信」，与本单「不编数字、行号实跑」的口径冲突，故不采用。

## 七、新发现问题（状态词：已修 / 已入队 / 判废 / 待裁 / 已闭环）

1. **待裁**：A L153 `R.menu.dict_rule_sel` 菜单 XML 在 main 零命中；旧字典选择分享菜单随栈拆除，新 `DictRuleScreen` 未补等价分享入口。import 机检按 `R` 跳过，本条为资源级新发现。
2. **待裁**：B 四个 R 成员 `check_status_filter`、`menu_show_source_bookshelf_count`、`menu_block_source_navigation`、`menu_show_source_check_status` 在 main `res/` 与 kt 零命中；binding/adapter 之外另有编译级 R 成员缺口，迁移时须一并落实新屏等价控件。
3. **待裁**：D 第二长用例仍按旧对话框 RecyclerView 取行（L736/L742/L743），L1464/L1465 另有 recycler_view 层级断言；import 级可解析，运行期视图存在性待复核。
4. **已入队**：审计 §七-3 反射清单本批复活两点——`CoverImageView.currentNameBitmap`（`app/src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt:104`）、`CodeEditActivity.search()` / `searchTxt()`（`app/src/main/java/io/legado/app/ui/code/CodeEditActivity.kt:795` / `:886`）；其余 androidTest 反射点仍待运行期复核，沿用 Q-292 待裁口径。
5. **待裁**：六个新 Compose 落点（`ReplaceRuleScreen`、`DictRuleScreen`、`SourceManageScreen`/`SourceManageRow`、`SourceEditScreen`、`CoverSection`、`CoverFontSection`）`testTag` / `semantics` 零命中，Compose UI 测试改写缺定位钩子。
6. **已闭环**：本单复核确认 Q-292 §三 归属这 5 文件的 10 条未解析清单与机检一致（259 imports / 10 unresolved / 249 命中），无漏报、无误报；另 4 个文件（`BackupOptionsTest`、`ReadRecordHistoryTest`、`SourceDragOrderUiTest`、`CoverStylePreviewUiTest`）不在本单范围，结论沿用审计分桶。

## 八、未验证项与边界

- 未编译、未跑真机/emulator；结论基于静态 grep 与 main 侧声明索引，不代表运行期通过。
- 本单未改任何 kt / main / test / 脚本 / workflow / 构建文件；仓外 scratch 脚本位于 `../scratch/import_resolve.py`，未入仓。
- 工作树开始前已有他人未提交改动（含 E 文件 AboutActivity→AboutInkActivity 三 hunk），本单未触碰、未回滚。
- 索引文档（`docs/索引-文档.md`）按本单边界未更新；锚点卡见 `docs/锚点/Q297-androidTest五件.md`。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：`docs/机制-子代理必读.md:18`：「每一处改动后用 `grep -n` 贴出真实输出」
- 逐字引用 2：`docs/机制-子代理必读.md:20`：「没编译、没跑测试、没真机 —— 一律显式写出来」
- 逐字引用 3：`docs/机制-子代理必读.md:33`：「不许编造 sha、行号、计数、测试结果」
- 适用条款：R57 一条缺陷一单（只复核点名的 5 个 androidTest 文件，其余 10 条硬错误不在本单范围、未动手改）；禁 git 写操作（未 add/commit/push/revert）；禁本地编译（沙箱无 JDK，未跑 gradle/kotlinc）；证据链 grep 回证（每条未解析与不等价均带 main 侧文件:行号，命令与节选输出见 §5.4）；未验证项显式声明（编译级 / 运行期结论均标注未验证）；不编数字（行号、计数均按实跑结果填写，漂移项已在 §5.5 证据修正表登记）。

- 最小改写边界：5 个测试文件零写入；无等价 API 的 10 条全部改判待裁，未编造目标类型。
- 取证可回源：每条未解析带 `文件:行号` 与符号名；每条不等价带 main 侧文件:行号 grep 证据。
- 编译级结论：未验证，等 CI androidTest 编译或真机 instrumentation。
