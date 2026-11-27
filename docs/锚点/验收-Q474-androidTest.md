# 锚点卡 · 验收 Q-474 androidTest 悬空 ViewBinding（独立复核）

> 用途：验收报告 `docs/验收-复核-Q474-androidTest.md` 的回源锚点集。被验提交 `4565192672ff3ab9c007a0fd5954e5dd01366548`（短号 `4565192`），HEAD `15779ec`。
> 逐字引文带 文件:行，禁转述；锚点禁裸 `:NNN`。本卡为验收方独立取数，不采信写码方自评。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「**不采信写码方的自评**：它说"已验证"不算证据；它给的计数要你自己重算。」（docs/机制-验收代理必读.md:8）
- 逐字引用 2：「数量类结论必须给"命令 + 真实输出"，不许只给结论数字。」（docs/机制-验收代理必读.md:26）
- 逐字引用 3：「硬规：凡是**新增文件 / 拆分文件 / 新增 import / 改函数签名 / 改可见性**的单，验收结论最高只能到「有条件通过（编译面待 CI）」，**禁止判 PASS**。」（docs/机制-验收代理必读.md:87）
- 适用条款：验收独立取数重算计数；每条销项附命令与真实输出；新增文件 + 新 import 单结论封顶「有条件通过（编译面待 CI）」；只读不改被验源码与写码方报告；未验证项显式声明，不替 CI 背书。

## 一、提交边界锚点（可回源）

- `4565192` 改动集（`git show --name-only --format="" 4565192`）：仅 3 份 androidTest kt（增 `BookSourceCheckSessionTest.kt`、删 `BookSourceCheckUiTest.kt`、删 `SourceDragOrderUiTest.kt`）+ 2 份 docs（`docs/修复-androidTest悬空ViewBinding.md`、`docs/锚点/Q474-androidTest.md`）；`app/src/main/`、`app/src/test/`、`data_scripts/`、`.github/` 零改动。
- 工作区 `git status --porcelain app/src/androidTest/` 空输出；`git diff --stat 4565192 HEAD -- app/src/androidTest/` 空输出：被验提交之后 androidTest 无漂移。
- 缺陷出处：`docs/验收-复核-Q465-F1-SourceManage.md:86`（N3 记录旧 `BookSourceCheckUiTest.kt` 第 33/138/178/260 行与 `SourceDragOrderUiTest.kt` 第 19/116 行引用 `ItemBookSourceBinding`，`item_book_source.xml` 及 `cbBookSource`/`ivDebugText`/`tvBookshelfCount` 缺位）；派单 `docs/派单队列.md:473` Q-474 DONE。

## 二、悬空清零回证锚点（改前 → 改后，独立重算）

- 改前（`4565192^` 快照，`git grep -n <符号> 4565192^ -- app/src/androidTest/`）：`ItemBookSourceBinding` 8、`cbBookSource` 2、`tvBookshelfCount` 2、`ivDebugText` 2、`BookSourceAdapter` 3。
- 改后（工作区与 `4565192` 快照双取）：五符号在 `app/src/androidTest/` 均 0 命中。
- 改前行号逐字：`BookSourceCheckUiTest.kt:33` import binding；`:138` `cbBookSource.performClick()`；`:178` `tvBookshelfCount`；`:260` `ivDebugText.visibility`；`:307` `ivDebugText.visibility`；`:384` `cbBookSource.text`；`SourceDragOrderUiTest.kt:19` import binding；`:116` `tvBookshelfCount`。`BookSourceAdapter` 三处：`BookSourceCheckUiTest.kt:42` import、`:149` 强转、`:375` 强转。
- main 侧缺席：`app/src/main/res/layout/item_book_source.xml` 不存在（`git cat-file -e HEAD:app/src/main/res/layout/item_book_source.xml` fatal: Not a valid object name）；`find app/src/main modules -type d -name databinding` 空；main 零手写 `ItemBookSourceBinding` 类声明。

## 三、新文件符号解析锚点（main 侧真实存在，逐符号行号）

- `io.legado.app.data.appDb`：`app/src/main/java/io/legado/app/data/AppDatabase.kt:70` `val appDb by lazy {`；包名 `app/src/main/java/io/legado/app/data/AppDatabase.kt:1` `package io.legado.app.data`。
- `io.legado.app.data.entities.BookSource`：`app/src/main/java/io/legado/app/data/entities/BookSource.kt:34` `bookSourceUrl`、`:36` `bookSourceName`、`:38` `bookSourceGroup`、`:71` `bookSourceComment`、`:99` `mainJs`。
- `io.legado.app.model.CheckSource`：`app/src/main/java/io/legado/app/model/CheckSource.kt:16` `object CheckSource`；标志位 `:25` `checkDomain`、`:26` `checkSearch`、`:27` `checkDiscovery`、`:28` `checkInfo`、`:29` `checkCategory`、`:30` `checkContent`；`:33` `suspend fun start(context: Context, sources: List<BookSourcePart>, sessionId: Long)`；`:56` `fun stop(context: Context, sessionId: Long)`。
- `io.legado.app.model.Debug`：`app/src/main/java/io/legado/app/model/Debug.kt:39` `object Debug`；`:57` `var isChecking: Boolean`；`:168` `fun tryStartCheckSession(): Long?`；`:193` `fun isChecking(sessionId: Long): Boolean`；`:197` `fun currentCheckSession(): Long?`。
- DAO：`app/src/main/java/io/legado/app/data/dao/BookSourceDao.kt:255` `getBookSource`、`:261` `getBookSourcePart`、`:286` `getCheckState`、`:289` `insert`、`:298` `update`、`:345` `delete`；状态实体 `app/src/main/java/io/legado/app/data/entities/BookSourceCheckState.kt:24` `val status: String`。
- 新文件落点：`app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt:60` `realServicePersistsFailureAndSuccessWithoutChangingGroups`；`:82` `nonHttpDomainIsUncheckableAfterOtherChecksPass`；`CheckSource.start` `:70`/`:77`/`:100`/`:108`；状态断言 `:72` FAILED、`:79` PASSED、`:102` UNCHECKABLE、`:110` FAILED。

## 四、回归点接管锚点

- 书源拖拽纯顺序（跨隐藏行重定位、降序语义、缺键不动）：`app/src/test/java/io/legado/app/ui/book/source/manage/BookSourceManageOrderTest.kt:8`（JVM 纯逻辑 `moveRelativeTo`）。
- 校验三态落库往返：`app/src/androidTest/java/io/legado/app/data/BookSourceCheckStateTest.kt:37`（`completedAndInterruptedEntriesSurviveDatabaseReopen`，`:44` UNCHECKABLE 落库）；状态不污染源与排序：`:76` `statusDoesNotPolluteSourceAndRankingDoesNotInvalidateRules`。
- 归属字段只写 / 会话停止释放：`app/src/test/java/io/legado/app/service/CheckSourcePersistenceContractTest.kt:11`。
- 导航拦截偏好行为：`app/src/androidTest/java/io/legado/app/help/source/SourceNavigationUiTest.kt:77`（`AppConfig.blockSourceNavigation = true` 后拦截）。
- 设置归档通用通道：`app/src/androidTest/java/io/legado/app/help/storage/BackupOptionsTest.kt:323` 显式设置还原、`:346` 阅读器控件显式 false 保留、`:367` zip 内 `config.xml` 文本断言。
- 三偏好默认 false：`app/src/main/java/io/legado/app/help/config/AppConfig.kt:41` `showSourceCheckStatus`、`:44` `showSourceBookshelfCount`、`:47` `blockSourceNavigation`，getter 默认值均 `false`。
- 新栈出口（私有 VM / 无钩子）：`app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:52` `private val vm by viewModels<SourceManageViewModel>()`、`:78` `setContent {`；`SourceManageState.kt:96` `showCheckStatus`、`:97` `showBookshelfCount`、`:99` `blockSourceNavigation`、`:100` `checkStatusFilter`、`:103` `bookshelfCounts`、`:104` `selected`；`SourceManageViewModel.kt:59` `uiState`、`:214` `setCheckStatusFilter`、`:218` `toggleShowCheckStatus`、`:229` `toggleShowBookshelfCount`、`:235` `toggleBlockSourceNavigation`、`:244` `toggleSelect`。

## 五、他批次残留悬空复核锚点

- 写码方 §九-4 列 6 份：`RuleSelectionShareTest.kt`、`SourceOrderMetadataUpdateTest.kt`、`CodeSelectionUiTest.kt`、`CoverStylePreviewUiTest.kt`、`CoverTitleAdaptiveUiTest.kt`、`ReadRecordHistoryTest.kt`。验收独立复核：所列符号在 main 均零声明 / 布局缺位，核真。
- 验收补漏 1 份：`app/src/androidTest/java/io/legado/app/help/storage/BackupOptionsTest.kt:32` import `io.legado.app.ui.config.BackupConfigFragment`，main 零声明（仅 `app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt:48` KDoc 提及旧名）；该债已在册于 `docs/审计-androidTest失修清点.md:187`（§3.1-1），但写码方本单 6 份清单未列入。
- 全量 databinding 映射（6 条 import）：缺位 2 条在 `ReadRecordHistoryTest.kt:45`/`:46`（`activity_read_record.xml`、`item_read_record_display.xml` 均 absent）；在位 4 条：`ExploreCategoriesTest.kt:27` ↔ `activity_explore_show.xml`、`MangaReadingDirectionTest.kt:35` ↔ `activity_manga.xml`、`FontSelectionInflationTest.kt:10` ↔ `item_font.xml`、`HighlightGroupUiTest.kt:47` ↔ `item_highlight_rule.xml`。

## 六、新发现问题（带状态词）

- **【待裁】** 清单漏项：`BackupOptionsTest.kt:32` `BackupConfigFragment` 悬空未入本单 6 份残留清单；交对应批次按审计 §4.3-G 拆分（5 条纯逻辑用例 / 2 条 UI 用例待 Compose 钩子）。
- **【待裁】** RSS 源拖拽隐藏序 / 重开顺序存活无测试承接：`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt:12` import `ItemTouchHelper`、`:200` `attachToRecyclerView`，View 栈存活。
- **【待裁】** 替换规则拖拽 / 批量选择无测试承接：`app/src/main/java/io/legado/app/ui/replace/ReplaceRuleActivity.kt:64` 已 `setContent` Compose，屏内无 testTag。
- **【待裁】** 管理页计数 / 多选 instrumentation 断言入口缺失：`bookshelfCounts` / `selected` 仅在私有 VM 态，manage 全包 `testTag`/`semantics`/`TestTag` 零命中，androidTest 与 `app/build.gradle` 零 Compose 测试规则。
- **【观察 · 已入队】** `selected` 内存态跨重建存活口径待规格确认；三偏好键无显式归档往返断言（沿用审计口径，非本单新引入，已入队待规格确认）。

## 七、复核条件

- 编译面以云端 `assembleDebugAndroidTest` + 单测全绿为准；本单未跑 gradlew、未上机 instrumentation，不代 CI 下结论。
- 整车道编上前，须先清零 §五 全部残留悬空（含补漏的 `BackupOptionsTest.kt`）。
- 新 Compose 屏补 testTag / VM 公开出口后，回头重写本单作废的 View 层断言；RSS / 替换拖拽缺口在对应域补测试或判规格取消。
