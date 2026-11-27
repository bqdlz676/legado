# 锚点卡 · Q-474 androidTest 悬空 ViewBinding 处置

> 用途：Q-474 修单收口依据。被修报告 `docs/修复-androidTest悬空ViewBinding.md`；缺陷出处 Q-468 验收 N3（`docs/验收-复核-Q465-F1-SourceManage.md:86`）；前置审计 `docs/审计-androidTest失修清点.md`、复核 `docs/修复-androidTest可修五件.md` §三-B。
> 逐字引文带 文件:行，禁转述；锚点禁裸 `:NNN`。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「**禁 git 写操作**：不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 逐字引用 2：「**grep 回证**：每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。没贴 grep 的改动视为未改。」（docs/机制-子代理必读.md:18）
- 逐字引用 3：「**文件域独占**：只碰任务书白名单里的文件；工作区里别人未提交的改动（`git status` 里不是你的）**不许删、不许改、不许提交**。」（docs/机制-子代理必读.md:13）
- 适用条款：R57 一条缺陷一单；R48 一次性；禁 git 写；禁本地编译；文件域仅限 `app/src/androidTest/`；grep 回证先行；未验证项显式声明。

## 一、事实锚点（可回源，≥5 处）

- `docs/验收-复核-Q465-F1-SourceManage.md:86`：N3 原文——两份 androidTest 仍引用 `ItemBookSourceBinding` 及 legacy `R.id.recycler_view` / `R.id.search_view` / `R.id.title_bar`；`item_book_source.xml` 与 `cbBookSource` / `ivDebugText` / `tvBookshelfCount` 在 `46a8852^` 与 HEAD 均缺位。
- `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:78`：`setContent { ... }`，管理页已是 Compose 宿主；`:52` `private val vm by viewModels<SourceManageViewModel>()`——VM 私有，instrumentation 无公开出口；`:46` KDoc 自述旧 `ActivityBookSourceBinding + BookSourceAdapter + 旧菜单` 全部拆除。
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt:33`：KDoc「Compose 自绘，替代旧 BookSourceAdapter / item_book_source.xml」；`:120` 书架计数徽章 `stringResource(R.string.source_bookshelf_count, bookshelfCount)`；`:152` 调试文案 `Debug.debugMessageMap[source.bookSourceUrl]`。
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt:103` `bookshelfCounts: Map<String, Int>`；`:104` `selected: Set<String>`；`:96` `showCheckStatus`；`:97` `showBookshelfCount`；`:99` `blockSourceNavigation`；`:100` `checkStatusFilter`——新状态全部在 VM 态模型内。
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:59` `val uiState: StateFlow<SourceManageUiState>`；`:244` `toggleSelect`；`:218` `toggleShowCheckStatus`；`:229` `toggleShowBookshelfCount`；`:235` `toggleBlockSourceNavigation`；`:214` `setCheckStatusFilter`——动作出口存活但仅私有 VM 可达。
- `app/src/main/java/io/legado/app/help/config/AppConfig.kt:41` `showSourceCheckStatus`、`:44` `showSourceBookshelfCount`、`:47` `blockSourceNavigation`，getter 默认值均 `false`；偏好键 `PreferKey.showSourceCheckStatus` / `showSourceBookshelfCount` / `blockSourceNavigation` 存活。
- `app/src/main/java/io/legado/app/service/CheckSourceService.kt:228` `completeCheck(...)` 落库；`:285` 失败置 FAILED；`:297` 域名未过置 UNCHECKABLE——执行层归类逻辑保留，salvage 用例守护点。
- 新承接测试：`app/src/test/java/io/legado/app/ui/book/source/manage/BookSourceManageOrderTest.kt:8`（跨隐藏行拖拽纯逻辑）；`app/src/androidTest/java/io/legado/app/data/BookSourceCheckStateTest.kt:44`（三态落库往返）、`:76`（状态不污染源）；`app/src/test/java/io/legado/app/service/CheckSourcePersistenceContractTest.kt:10`（只写归属字段）；`app/src/androidTest/java/io/legado/app/help/source/SourceNavigationUiTest.kt:77`（导航拦截行为）。

## 二、处置回证

- 删除：`BookSourceCheckUiTest.kt`（5 用例：3 View 层作废 + 2 执行层 salvage）、`SourceDragOrderUiTest.kt`（5 拖拽用例，整链路 View 栈，作废）。
- 新增：`app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt`——`:60` `realServicePersistsFailureAndSuccessWithoutChangingGroups`；`:82` `nonHttpDomainIsUncheckableAfterOtherChecksPass`；`CheckSource.start` 落点 `:70`/`:77`/`:100`/`:108`；状态断言 `:72` FAILED、`:79` PASSED、`:102` UNCHECKABLE、`:110` FAILED。
- 命中计数（改前 → 改后）：`ItemBookSourceBinding` 8 → 0；`cbBookSource` 2 → 0；`tvBookshelfCount` 2 → 0；`ivDebugText` 2 → 0；`BookSourceAdapter` 3 → 0。
- 门禁：`kotlin-syntax-lint.py` 新文件 `KOTLIN-LINT: PASS（1 个文件）`，rc=0；未跑 gradlew、未上机。

## 三、回归点接管对照

- 书源勾选 / 书架计数 / 调试文案可见性 / 状态下拉 / 菜单顺序：旧 View 断言无等价入口（屏零 testTag、VM 私有、无 Compose 测试基建），判作废；状态字段与 VM 动作锚点见 §一 State/VM 行，待补钩子后重写。
- 校验会话 FAILED/PASSED/UNCHECKABLE 归类 + 分组注释不被改写：salvage 进 `BookSourceCheckSessionTest.kt`；状态机持久与不污染源由 `BookSourceCheckStateTest.kt:44`/`:76` 接管；归属字段写入由 `CheckSourcePersistenceContractTest.kt:10` 接管。
- 书源拖拽隐藏序：`BookSourceManageOrderTest.kt:8` 接管纯顺序逻辑；RSS / 替换规则拖拽见 §四缺口。
- 导航拦截偏好：`SourceNavigationUiTest.kt:77` 接管；设置归档通用通道由 `BackupOptionsTest.kt:323`/`:346`/`:367` 接管。

## 四、新发现问题（带状态词）

- **【待补】** RSS 拖拽排序持久化无测试承接（`RssSourceActivity.kt:188` 仍 View + ItemTouchHelper）。
- **【待补】** 替换规则拖拽 / 选择无测试承接（Compose 屏无钩子，分组契约测试不护拖拽）。
- **【待补】** 管理页计数 / 多选缺 instrumentation 断言入口，待 testTag 或 VM 出口。
- **【待裁 · 他批次】** RuleSelectionShareTest / SourceOrderMetadataUpdateTest / CodeSelectionUiTest / CoverStylePreviewUiTest / CoverTitleAdaptiveUiTest / ReadRecordHistoryTest 残留悬空，沿用 Q-297 口径，本单不代收。
- **【观察】** `selected` 内存态跨重建存活口径待规格确认；三偏好键归档无显式往返断言。

## 五、自证与复核条件

- 自证：本单改动仅限 `app/src/androidTest/`（删 2 增 1）；main / test / data_scripts / .github 零改动；git 未 add/commit；lint PASS 仅语法层，编译与运行未验证。
- 复核条件：① CI 若开 `assembleDebugAndroidTest`，先清零 §四他批次残留悬空再谈整车道绿；② 新 Compose 屏补 testTag/VM 出口后，回头重写 §三作废的 View 层断言；③ RSS / 替换拖拽缺口须在对应域补测试或判规格取消。
- 判定报告：`docs/修复-androidTest悬空ViewBinding.md`。
