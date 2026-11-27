锚点卡：docs/锚点/验收-Q474-androidTest.md

被验 commit：4565192672ff3ab9c007a0fd5954e5dd01366548（短号 4565192；HEAD 15779ec；本单编译面待云端 CI）
总结论：有条件通过（编译面待 CI）
判据：五条静态层 2 PASS / 3 部分 PASS（判据 1、3 PASS；判据 2、4、5 部分 PASS）
新缺陷：0 条 FAIL 级；新发现问题 5 条（4 条待裁沿用 / 1 条观察已入队）

> 复核对象：Q-474「清 androidTest 悬空 ViewBinding」。被验改动：删 `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt`、`app/src/androidTest/java/io/legado/app/ui/book/source/SourceDragOrderUiTest.kt`；新增 `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt`；写码方报告 `docs/修复-androidTest悬空ViewBinding.md`，写码方锚点卡 `docs/锚点/Q474-androidTest.md`。
> 边界：只读验收——未改 `app/src/androidTest/` 任何文件、未改写码方报告与旧卡、未执行 git 写操作（add/commit/push/revert/checkout）、未跑 gradlew/kotlinc/assemble、未上机 instrumentation。编译面、单测与运行期表现以云端 CI 为准；本报告不替 CI 背书。

## 一、复核方法与证据包

- 独立取数：写码方报告与旧卡仅当「待验主张清单」；计数、行号、符号存在性全部由验收方自己 `grep` / `git grep` / `Read` 重算，修前基线取 `git show 4565192^:<file>` 逐字对照。
- 证据包（§10 开工第一步）：`python3 data_scripts/acceptance-evidence.py --rev 4565192`，输出原样贴于 §六-1。证据包三值建议为 FAIL，依据是两份已删文件「工作区与 4565192^ 均取不到内容」；验收方已用 `git show 4565192^:<file>` 独立取回旧文件逐字核对，该 FAIL 属已删 D 文件的读取启发式，不构成对存活代码的静态否定（详见 §六-1 边界说明）。
- 机检：`kotlin-syntax-lint.py` 新文件 PASS；`check-report-fields.py`、`check-anchor-card.py`（含 M-08 编号反查）、`check-problem-finding.py` 末行原文见 §六-2。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「**不采信写码方的自评**：它说"已验证"不算证据；它给的计数要你自己重算。」（docs/机制-验收代理必读.md:8）
- 逐字引用 2：「数量类结论必须给"命令 + 真实输出"，不许只给结论数字。」（docs/机制-验收代理必读.md:26）
- 逐字引用 3：「硬规：凡是**新增文件 / 拆分文件 / 新增 import / 改函数签名 / 改可见性**的单，验收结论最高只能到「有条件通过（编译面待 CI）」，**禁止判 PASS**。」（docs/机制-验收代理必读.md:87）
- 适用条款：验收独立取数重算计数（§1 独立性）；每条销项附命令与真实输出（§3 自己取数）；新增文件 + 新 import 单结论封顶「有条件通过（编译面待 CI）」（§9）；只读不改被验源码与写码方文档；未验证项显式声明（§5）。

## 二、五条必判销项

### 判据 1 · 悬空清零：五符号 androidTest 现役命中数应为 0 —— PASS（静态层）

命令与真实输出（工作区，HEAD 15779ec）：

- `grep -rn "ItemBookSourceBinding" app/src/androidTest/` → 无输出，`hits=0`（rc=1）。
- `grep -rn "cbBookSource" app/src/androidTest/` → 无输出，`hits=0`。
- `grep -rn "tvBookshelfCount" app/src/androidTest/` → 无输出，`hits=0`。
- `grep -rn "ivDebugText" app/src/androidTest/` → 无输出，`hits=0`。
- `grep -rn "BookSourceAdapter" app/src/androidTest/` → 无输出，`hits=0`。

提交快照复核（`git grep -n <符号> 4565192 -- app/src/androidTest/`）：五符号均 0 命中。修前基线（`4565192^` 快照）独立重算：`ItemBookSourceBinding` 8、`cbBookSource` 2、`tvBookshelfCount` 2、`ivDebugText` 2、`BookSourceAdapter` 3，与写码方 §6.1 对照表一致。

main 侧缺席证据（验收方复核）：`git cat-file -e HEAD:app/src/main/res/layout/item_book_source.xml` → `fatal: Not a valid object name`；`find app/src/main modules -type d -name databinding` 空；main 零手写 `ItemBookSourceBinding` 类声明；`BookSourceAdapter` 在 main 无类声明，仅 KDoc 残留于 `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:46`、`SourceManageRow.kt:33`、`SourceManageScreen.kt:70`。

判定：本单边界内五处悬空绑定引用已清零，PASS。

### 判据 2 · 覆盖不偷降：被删用例逐条给「原本守什么 → 现在谁守 → 依据」—— 部分 PASS

旧 `BookSourceCheckUiTest.kt` 5 条 @Test、旧 `SourceDragOrderUiTest.kt` 5 条 @Test（`git grep -n "@Test fun" 4565192^` 逐字取名）。逐条映射如下，接管分三类：新文件 salvage / 既有测试接管 / 明确作废记缺口。

**A. 勾选计数用例** —— `bookshelfCountsTrackCurrentSourcesAndShelfChangesWithoutLosingSelection`（旧 `BookSourceCheckUiTest.kt` 第 117 行，文件已删）

- 原本守：书架计数随架上书增减变化；`notShelf` / `local` 类型不计入；书归属源迁移后计数改挂；删书回落；分组筛选后勾选选中态保留；`recreate()` 后计数与选中态存活。
- 现在谁守：无等价入口，判作废并记缺口。依据：计数与选中态数据源 `bookshelfCounts` / `selected` 在私有 VM 态模型内（`SourceManageState.kt:103`、`:104`）；宿主 VM 私有（`BookSourceActivity.kt:52` `private val vm by viewModels<SourceManageViewModel>()`）；manage 全包 `testTag`/`semantics`/`TestTag` 零命中；androidTest 与 `app/build.gradle` 零 `createComposeRule` / `AndroidComposeTestRule` / `testTagsAsResourceId`。全量 grep `app/src/test/`、`app/src/androidTest/` 无 `bookshelfCount` 计数数学断言。
- 缺口归属：写码方报告 §九-3【待补】，验收方复核属实。

**B. 状态筛选用例** —— `statusFilterIntersectsGroupAndSurvivesRecreation`（旧 `:185`）

- 原本守：校验状态下拉与分组筛选取交集；关闭状态显示后状态行与筛选 Spinner 隐藏；`recreate()` 后筛选结果与可见性存活。
- 现在谁守：库层三态持久往返由 `app/src/androidTest/java/io/legado/app/data/BookSourceCheckStateTest.kt:37` 接管（`:44` UNCHECKABLE 落库断言）；UI 层交集筛选、Spinner 可见性、重建存活无接管。出口 `checkStatusFilter`（`SourceManageState.kt:100`）与 `setCheckStatusFilter`（`SourceManageViewModel.kt:214`）存活，但挂私有 VM，无测试可取。
- 缺口归属：作废记缺口；与 A 同待补钩子。

**C. 菜单与备份 UI 用例** —— `defaultOffStatusAndNavigationTogglesAreIncludedInRealSettingsBackup`（旧 `:211`）

- 原本守：三偏好默认 false；主菜单条目顺序与导航项图标；拦截导航开关写入 `AppConfig`；状态开关打开后调试文本可见；真实备份 `config.xml` 含 `showSourceCheckStatus`、`blockSourceNavigation` 两键；恢复后两键回 true；重建后菜单勾选与 Spinner 可见。
- 现在谁守（已接管部分）：三偏好默认 false 由源码 getter 默认值在位佐证（`AppConfig.kt:41`、`:44`、`:47`，getter 默认值均 `false`）；导航拦截 opt-in 行为由 `app/src/androidTest/java/io/legado/app/help/source/SourceNavigationUiTest.kt:77` 接管（`AppConfig.blockSourceNavigation = true` 后拦截生效）；设置归档 / 还原通用通道由 `app/src/androidTest/java/io/legado/app/help/storage/BackupOptionsTest.kt:323`（显式设置还原）、`:346`（阅读器控件显式 false 保留）、`:367`（zip 内 `config.xml` 文本断言）接管。
- 未接管部分：菜单条目顺序 / 图标断言、调试文本行可见性、三键各自的显式归档往返断言（旧用例也只显式断言前两键，`showSourceBookshelfCount` 备份段随删除移除）。写码方报告 §九-6【观察】已记，验收方复核属实。
- 判定：部分接管；残留断言面记缺口，未静默丢失。

**D. 书源拖拽两条链路** —— `filteredBookDragPreservesHiddenOrder`（旧 `SourceDragOrderUiTest.kt` 第 47 行）、`descendingBookDragPreservesHiddenOrder`（旧第 48 行，同件已删）

- 原本守：书源过滤态 / 降序态拖拽隐藏行顺序不被重排；拖拽不落悬不写持久序；释放后 `customOrder` 持久化；重开列表顺序存活；按住期注释刷新不改持久元数据。
- 现在谁守：纯顺序算法由 `app/src/test/java/io/legado/app/ui/book/source/manage/BookSourceManageOrderTest.kt:8` 接管（跨隐藏行重定位、降序语义、缺键不动，JVM 纯逻辑 `moveRelativeTo`）。手势链路（ItemTouchHelper 真实拖动、Room 持久序、重开存活、按住期失效刷新）无接管。
- 判定：纯逻辑有主；手势 / 持久化 / 重开存活链路记缺口。

**E. RSS / 替换规则 / 按住删除目标三条** —— `filteredRssDragPreservesHiddenOrder`（旧第 49 行）、`filteredReplaceDragPreservesHiddenOrder`（旧第 50 行）、`heldBookDragWithDeletedTargetDoesNotMoveAnotherSource`（旧第 51 行；旧 androidTest 件，已删）

- 原本守：RSS 源 / 替换规则同型隐藏序保护与重开顺序存活；按住拖拽中途库内失效刷新 / 删除目标源，不挪走其他条目；注释刷新不改持久元数据。
- 现在谁守：无接管。RSS 宿主仍走 View 栈 + `ItemTouchHelper`（`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt:12` import、`:200` `ItemTouchHelper(...).attachToRecyclerView(binding.recyclerView)`）；替换规则宿主已 Compose（`app/src/main/java/io/legado/app/ui/replace/ReplaceRuleActivity.kt:64` `setContent {`），屏内无 testTag。既有 `ReplaceRuleViewModelGroupContractTest.kt` 覆盖分组委派与批量分组动作，不护拖拽顺序与行内持久序。
- 缺口归属：写码方报告 §九-1、§九-2【待补】，验收方复核属实。

**F. 两条执行层用例 salvage** —— `realServicePersistsFailureAndSuccessWithoutChangingGroups`（旧第 314 行 → 新 `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt:60`）、`nonHttpDomainIsUncheckableAfterOtherChecksPass`（旧第 336 行 → 新同文件第 82 行）

- 逐字比对：旧体与新体断言一致——缺搜索规则判 FAILED、全关规则判 PASSED、分组与注释不改；`local://` 非 http 域判 UNCHECKABLE、mainJs 返回空列表后改判 FAILED、分组注释保留。新文件补强了 `@Before` 插入、`@After` 清理与 `CheckSource` 六个标志位快照还原（新文件 `:39-42`、`:52-57`），属执行层脚手架防污染，未引入任何 View 符号。
- 调用面复核：全仓测试侧 `CheckSource.start` 调用仅新文件 4 处（`:70`/`:77`/`:100`/`:108`）；main 侧调用方为 `BookSourceCheckController.kt:69`、`BookSourceActivity.kt:227`、`McpToolServer.kt:756`， salvage 后执行层归类回归仍有测试驱动。

判定：2 条执行层用例等价承接；8 条 View / 手势用例中，纯顺序逻辑、默认值、导航拦截、通用归档已有主，计数多选 UI、状态筛选 UI、菜单图标、三键显式归档、RSS / 替换拖拽、按住删除目标手势等回归面当前无守护，但均在报告缺口节入账，无静默删除。部分 PASS。

### 判据 3 · 新文件可解析：import 与被调符号在 main 真实存在，无换壳保留 —— PASS（静态层）

新文件 `BookSourceCheckSessionTest.kt` 共 15 条 import；框架 / 第三方（`android.os.SystemClock`、`androidx.test.*`、`kotlinx.coroutines.runBlocking`、`org.junit.*`、`java.util.UUID`）按口径跳过。4 条自有 import 与全部被调符号 main 侧逐项命中（行号见锚点卡 §三）：

- `io.legado.app.data.appDb` → `AppDatabase.kt:70`（包名 `:1`）。
- `io.legado.app.data.entities.BookSource` → `BookSource.kt:34`/`:36`/`:38`/`:71`/`:99` 字段在位。
- `io.legado.app.model.CheckSource` → `CheckSource.kt:16` object；标志位 `:25`-`:30`；`start` `:33` 签名 `(Context, List<BookSourcePart>, Long)` 与调用 `CheckSource.start(context, listOf(dao.getBookSourcePart(url)!!), session)` 咬合；`stop` `:56` 签名 `(Context, Long)` 与 `:49` 调用咬合。
- `io.legado.app.model.Debug` → `Debug.kt:39` object；`isChecking` `:57`（无参属性）、`isChecking(sessionId)` `:193` 重载、`tryStartCheckSession()` `:168`、`currentCheckSession()` `:197`。
- DAO 方法：`BookSourceDao.kt:255` `getBookSource`、`:261` `getBookSourcePart`、`:286` `getCheckState`、`:289` `insert`、`:298` `update`、`:345` `delete`；状态字段 `BookSourceCheckState.kt:24` `status`。

换壳排查：`grep -nE "Binding|Adapter|RecyclerView|Spinner|Scenario|Menu|SearchView|TitleBar|FastScroll|screenshot|PixelCopy"` 新文件，仅文件头注释（`:4`-`:6`）出现这些词，代码行零引用；待删符号未换名保留。

边界：以上为 L0 静态符号存在性核验；真类型推断、重载解析、泛型约束、协程契约只有编译器能判，列入 §四未验证项。

### 判据 4 · 残留清单准确：6 份他批次悬空逐条核真伪 + 全量自扫 —— 部分 PASS

写码方 §九-4 列 6 份残留悬空，验收方逐条核 main 侧声明 / 布局：

- `RuleSelectionShareTest.kt`：`DictRuleAdapter`（旧第 24 行 import）、`ReplaceRuleAdapter`（旧第 26 行 import + 旧第 90 行强转）main 零声明，核真。
- `SourceOrderMetadataUpdateTest.kt`：`ReplaceRuleAdapter`（旧第 16 行）、`ReplaceRuleViewModel`（旧第 17 行 + 旧第 132 / 176 / 179 行）main 零声明，核真。
- `CodeSelectionUiTest.kt`：`BookSourceEditAdapter`（旧第 81 行 + 旧第 569 / 651 / 682 行）、`BookSourceEditViewModel`（旧第 82 行 + 旧第 684 行）main 零声明，核真。
- `CoverStylePreviewUiTest.kt`：`CoverFontConfigFragment`（旧第 26 行 + 旧第 182 行）main 零声明，核真。
- `CoverTitleAdaptiveUiTest.kt`：`CoverConfigFragment`（旧第 36 行 + 旧第 285 / 397 行）main 零声明，核真。
- `ReadRecordHistoryTest.kt`：`ActivityReadRecordBinding`（旧第 45 行）、`ItemReadRecordDisplayBinding`（旧第 46 行）对应布局 `activity_read_record.xml`、`item_read_record_display.xml` 均 absent（`git cat-file -e HEAD:...` 双 fatal），核真。

漏项点名：清单漏 1 份——`app/src/androidTest/java/io/legado/app/help/storage/BackupOptionsTest.kt:32` import `io.legado.app.ui.config.BackupConfigFragment`，main 零声明（仅 `sections/BackupSection.kt:48` KDoc 提及旧名）；`:152`/`:166`/`:384` 三处 `as BackupConfigFragment` 强转。该债在册于 `docs/审计-androidTest失修清点.md:187`（§3.1-1）与 `:280`（§4.3-G），并非新发现，但本单 6 份清单未列入，属清单不全。

全量 databinding 自扫（`grep -rn "io.legado.app.databinding" app/src/androidTest/`，共 6 条 import / 5 文件）：

- 布局缺位 2 条：`ReadRecordHistoryTest.kt:45`、`:46`（见上）。
- 布局在位 4 条：`app/src/androidTest/java/io/legado/app/ui/book/explore/ExploreCategoriesTest.kt:27` ↔ `activity_explore_show.xml` 在位；`MangaReadingDirectionTest.kt:35` ↔ `activity_manga.xml` 在位；`FontSelectionInflationTest.kt:10` ↔ `item_font.xml` 在位；`HighlightGroupUiTest.kt:47` ↔ `item_highlight_rule.xml` 在位。
- 类级悬空补充扫描：androidTest 内 Adapter 强转所指 io.legado 适配器，main 零声明者仅 `BookSourceEditAdapter`、`DictRuleAdapter`、`ReplaceRuleAdapter`（已在上述清单）；`PreferenceGroupAdapter` 为 `androidx.preference` 包，非悬空。

判定：所列 6 份全部核真，但漏 `BackupOptionsTest.kt` 1 份；全量映射结论与审计 §3.2/§3.3 一致。部分 PASS。

### 判据 5 · 报告一致性：自报事实逐字回源命中率 —— 部分 PASS

抽 12 处写码方自报事实（含改前命中数与 main 缺位证据），验收方独立回源：

1. 改前 `ItemBookSourceBinding` 8 处行号 `:33/:138/:178/:260/:307/:384` + `:19/:116` —— 命中（`git grep 4565192^` 逐行一致）。
2. 改前 `cbBookSource` 2 处（`:138`/`:384`）—— 命中。
3. 改前 `tvBookshelfCount` 2 处（`:178`/`:116`）—— 命中。
4. 改前 `ivDebugText` 2 处（`:260`/`:307`）—— 命中。
5. 改前 `BookSourceAdapter` 3 处（import `:42` + 强转 `:149`/`:375`）—— 命中。
6. 改后五符号 androidTest 全 0 —— 命中（工作区 + `4565192` 快照双取 0）。
7. main 侧 `item_book_source.xml` 全源集缺位、`databinding` 包不存在 —— 命中（find / git cat-file 双证）。
8. 新文件 4 条自有 import 在 main 解析命中 —— 命中（行号见判据 3）。
9. 新文件用例落点 `:60` / `:82`，`CheckSource.start` `:70`/`:77`/`:100`/`:108`，断言 `:72`/`:79`/`:102`/`:110` —— 命中（逐行 Read 一致）。
10. `BookSourceActivity.kt:52` 私有 VM、`:78` `setContent`；`SourceManageRow.kt:33` KDoc、`:120` 计数徽章、`:152` `Debug.debugMessageMap` —— 命中。
11. `SourceManageState.kt:96/97/99/100/103/104` 字段；`SourceManageViewModel.kt:59/214/218/229/235/244` 动作；`AppConfig.kt:41/44/47` 默认 false —— 命中。
12. 「他批次 6 份残留悬空」清单 —— 部分命中：6 份符号均真，但漏 `BackupOptionsTest.kt:32`（见判据 4）。

命中率：11/12 全命中，1/12 部分命中（清单完整性偏差）。另核：旧 R 成员 `check_status_filter`、`menu_show_source_check_status`、`menu_show_source_bookshelf_count`、`menu_block_source_navigation`、`menu_sort_desc` 在 main res/ 与 kt 全量零命中，与报告 §2.3 一致。部分 PASS。

## 三、改坏推演（验收方反向假设）

- 假设本单「整类删掉、两条会话用例一并废」：执行层 FAILED/PASSED/UNCHECKABLE 归类在仓内将无测试驱动（测试侧 `CheckSource.start` 仅新文件调用），属执行层回归空洞。验收方复核：salvage 两条断言与旧体逐字一致，未把执行层守护一并砍掉，推演风险已规避。
- 假设「改读 VM uiState 接管旧 UI 断言」：VM 为宿主私有属性，屏上无 testTag/semantics，androidTest 既拿不到 VM 实例也定位不到行节点；接入口只能反射或改 main，两者均被本单文件域禁掉。验收方复核：报告将这部分判无等价入口并挂缺口，处置成立。
- 假设「删 import 留空测试类保编译」：空跑测试不守门，且旧类内菜单 id / Spinner / RecyclerView 强转仍编不过；验收方复核：新文件无空跑类，旧文件整份拆除，回归点逐条落账。
- 二阶影响：删除两份 UI 测试后，书源管理页手工回归依赖转由既有 JVM / DAO 测试与后续 Compose 钩子覆盖；CI 若当前不编 androidTest，本单不改变 CI 绿红，只拆掉一处将来编到即红的断链。
- 三阶影响：salvage 新文件与 `BookSourceCheckStateTest.kt`、`CheckSourcePersistenceContractTest.kt` 同属执行层守护；后续 `CheckSource` 会话签名调整，三处需同步。

## 四、未验证项（显式，逐条）

1. androidTest 整车道 clean 编译结果（沙箱无 JDK / Android 编译链，未跑 `./gradlew assembleDebugAndroidTest`）。
2. 两条 salvage 用例在 emulator / 真机的运行结果（含 15s `waitUntil` 超时口径、服务可用性）。
3. 他批次残留悬空（含补漏的 `BackupOptionsTest.kt`）在 CI 的定性与清零时序。
4. Compose 测试基建（`createComposeRule` / testTagsAsResourceId）是否齐备、新屏补钩子后旧 UI 断言可否重写。
5. 真类型推断、重载解析、泛型约束、协程契约（仅编译器可判，静态层未覆盖）。

## 五、新发现问题（每条带状态词）

1. **【待裁】** 残留清单漏项：`BackupOptionsTest.kt:32` `BackupConfigFragment` 悬空未入本单 6 份清单；交对应批次按审计 §4.3-G 拆分——5 条纯备份 / 恢复 / WebDAV 逻辑用例抽出不依赖 Fragment，2 条 UI 用例待 Compose 钩子落地后判可修或判废。
2. **【待裁】** RSS 源拖拽隐藏序 / 重开顺序存活无测试承接：`RssSourceActivity.kt:12`/`:200` 仍 View + ItemTouchHelper。
3. **【待裁】** 替换规则拖拽 / 批量选择无测试承接：`ReplaceRuleActivity.kt:64` 已 Compose，屏内无 testTag；既有分组契约测试不护拖拽顺序。
4. **【待裁】** 管理页计数 / 多选 instrumentation 断言入口缺失：`bookshelfCounts` / `selected` 仅在私有 VM 态，manage 全包零定位钩子。
5. **【观察 · 已入队】** `selected` 内存态跨重建存活口径待规格确认；三偏好键无显式归档往返断言。沿用审计口径，非本单新引入，已入队待规格确认。

## 六、门禁机检记录（末行原文）

### 6.1 证据包

命令：`python3 data_scripts/acceptance-evidence.py --rev 4565192`

```
## 验收证据包（acceptance-evidence.py --rev 4565192）

### 1. 改动面
- `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt`（状态 A，124 行，本轮新增）
- `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt`（状态 D，0 行）
- `app/src/androidTest/java/io/legado/app/ui/book/source/SourceDragOrderUiTest.kt`（状态 D，0 行）
- `docs/修复-androidTest悬空ViewBinding.md`（状态 A，报告/文档）
- `docs/锚点/Q474-androidTest.md`（状态 A，报告/文档）

### 2. 机检汇总
- **kotlin-syntax-lint.py**：PASS（rc=0）`app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt`
- **check-anchor-card.py / check-onboarding.py**：SKIP（rc=0）`(rev 快照模式，报告门禁跳过)`

### 3. 编译面风险清单
- [新增文件] `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt:1`：本文件为本轮新增（4565192^ 中不存在同名路径）
- [import 改动] `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt:8`：新增 import：android.os.SystemClock；androidx.test.ext.junit.runners.AndroidJUnit4；androidx.test.platform.app.InstrumentationRegistry；io.legado.app.data.appDb；io.legado.app.data.entities.BookSource；io.legado.app.model.CheckSource；io.legado.app.model.Debug；kotlinx.coroutines.runBlocking；org.junit.After；org.junit.Assert.assertEquals；org.junit.Assert.assertTrue；org.junit.Before；org.junit.Test；org.junit.runner.RunWith；java.util.UUID
- [跨包引用] `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt:-`：跨包 import 15 条
- [读取失败] `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt:-`：工作区与 4565192^ 均取不到该文件
- [读取失败] `app/src/androidTest/java/io/legado/app/ui/book/source/SourceDragOrderUiTest.kt:-`：工作区与 4565192^ 均取不到该文件

### 4. 三值建议
- **结论：FAIL**
- 依据：存在读取失败文件（app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt、app/src/androidTest/java/io/legado/app/ui/book/source/SourceDragOrderUiTest.kt）：快照与工作区都取不到内容，无法判定，按 FAIL 处理
```

边界说明：两条 D 文件为本单删除对象，证据包按工作区 / 快照读文件内容失败属启发式限制；验收方已用 `git show 4565192^:<file>` 取回旧体完成逐字对照（判据 2），该 FAIL 不作为存活代码静态缺陷计入本单。

### 6.2 门禁闸（命令与末行原文，实跑记录）

- `python3 data_scripts/check-report-fields.py docs/验收-复核-Q474-androidTest.md docs/锚点/验收-Q474-androidTest.md` → 末行 `REPORT-FIELDS: PASS（2 份，0 份不合格）`，rc=0。
- `python3 data_scripts/check-anchor-card.py docs/验收-复核-Q474-androidTest.md docs/锚点/验收-Q474-androidTest.md` → 末行 `汇总: PASS=2 FAIL=0（共 2 份）`，rc=0（含 M-08 主对象反查：报告号 Q474 ↔ 卡 `docs/锚点/验收-Q474-androidTest.md` 在位）。
- `python3 data_scripts/check-problem-finding.py docs/验收-复核-Q474-androidTest.md` → 末行 `PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`，rc=0。
- `python3 data_scripts/check-onboarding.py`（聚合扫工作区改动报告）→ 本单报告单检 `check_report()` 无问题（PASS）；聚合末行 `ONBOARD: FAIL（2 份报告）`，判红项为他单 `docs/验收-复核-Q475-三处空态.md`「引用过短（<6 字），无法回源：它说」，非本单缺陷，本单未改该文件。
- 补充机检：`python3 data_scripts/cross-module-guard.py` → `GUARD: PASS（无代码改动）`；`python3 data_scripts/debt-ratchet.py` → `RATCHET: PASS（当前 {'dual': 8, 'compat': 2, 'oversized_warn': 50, 'deprecated': 1, 'wip': 2}，未超基线）`；`python3 data_scripts/check-combo-coverage.py` → `COMBO-COV: PASS（关键对 27 条，缺陷 9 条均已落层归因）`。

### 6.3 语法预检

命令：`python3 data_scripts/kotlin-syntax-lint.py app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt` → `KOTLIN-LINT: PASS（1 个文件）`，rc=0。仅语法层词法预检，不等价编译通过。

## 七、可复跑命令清单

- 改后清零复核：`grep -rn "ItemBookSourceBinding\|cbBookSource\|tvBookshelfCount\|ivDebugText\|BookSourceAdapter" app/src/androidTest/`
- 修前基线：`git grep -n "<符号>" 4565192^ -- app/src/androidTest/`
- 提交边界：`git show --name-only --format="" 4565192`
- databinding 全量映射：`grep -rn "io.legado.app.databinding" app/src/androidTest/`，逐条 `ls app/src/main/res/layout/<binding 对应名>.xml`
- 符号存在性：`grep -rn "class <Adapter/Fragment/ViewModel>" app/src/main/java/`
