锚点卡：docs/锚点/Q474-androidTest.md

# 修复 · androidTest 悬空 ViewBinding（Q-474）

- 被改文件：`app/src/androidTest/` 删除 2 份（`BookSourceCheckUiTest.kt`、`SourceDragOrderUiTest.kt`），新增 1 份（`BookSourceCheckSessionTest.kt`）；本报告与锚点卡 2 份 docs。未碰 `app/src/main/`、`app/src/test/`、`data_scripts/`、`.github/`。
- 判据：旧符号 `ItemBookSourceBinding` 8 → 0、`cbBookSource` 2 → 0、`tvBookshelfCount` 2 → 0、`ivDebugText` 2 → 0、`BookSourceAdapter` 3 → 0；salvage 新文件 4 条自有 import（`appDb` / `BookSource` / `CheckSource` / `Debug`）在 main 解析命中，其余为 android/kotlinx/junit/java 框架第三方跳过。
- 真跑门禁：`python3 data_scripts/kotlin-syntax-lint.py` 新文件 `KOTLIN-LINT: PASS（1 个文件）`，rc=0；`python3 data_scripts/check-onboarding.py` 回执 `ONBOARD: PASS`；`check-anchor-card.py` 本报告 + 卡 `汇总: PASS=2 FAIL=0`；未跑 `./gradlew`、未上机 instrumentation。
- 未验证项：① androidTest 整车道编译结果；② 两条 salvage 用例在 emulator/真机的运行结果；③ 他批次残留悬空清单的 CI 定性。共 3 项，均未验证，不替 CI 下结论。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「**禁 git 写操作**：不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 逐字引用 2：「**grep 回证**：每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。没贴 grep 的改动视为未改。」（docs/机制-子代理必读.md:18）
- 逐字引用 3：「**文件域独占**：只碰任务书白名单里的文件；工作区里别人未提交的改动（`git status` 里不是你的）**不许删、不许改、不许提交**。」（docs/机制-子代理必读.md:13）
- 适用条款：R57 一条缺陷一单（他批次悬空只记录不动手）；R48 一次性；禁 git 写；禁本地编译；文件域白名单仅限 `app/src/androidTest/`；grep 回证先行；每条规则注释写明为什么 / 适用条件；未验证项显式声明。

## 一、任务与边界

- 任务出处：Q-468 验收 N3（`docs/验收-复核-Q465-F1-SourceManage.md:86`）；派单 `docs/派单队列.md:473`、台账 `docs/派单台账.md:476`。
- 缺陷一句话：Compose 重写拆掉了书源管理页旧 View 栈（布局 / Adapter / 行 ViewBinding / 旧菜单），两份 androidTest 仍强引用已不可生成的 `ItemBookSourceBinding`，构成 androidTest 车道编译红隐患。
- 范围硬约束：只许动 `app/src/androidTest/`；不许碰 `app/src/main/`；禁 git 写；禁跑 gradlew；Kotlin 语法预检只用 `python3 data_scripts/kotlin-syntax-lint.py`。
- 既有结论沿用：`docs/修复-androidTest可修五件.md`（Q-297）已把这两份判为「待裁」，本单按 Q-474 口令落地处置：能等价改写的改写，无等价入口的写明作废依据并清掉，不留空跑。

## 二、取证（改前全量 grep，文件:行）

### 2.1 悬空绑定类命中（改前 8 处，旧文件已删除，行号为拆除前取证记录）

- 旧文件 `BookSourceCheckUiTest.kt`（原路径 `app/src/androidTest/java/io/legado/app/ui/book/source/`）：第 33 行 import `io.legado.app.databinding.ItemBookSourceBinding`；第 138 行 `ItemBookSourceBinding.bind(...).cbBookSource.performClick()`；第 178 行 `ItemBookSourceBinding.bind(row).tvBookshelfCount.text.toString()`；第 260 行 `ItemBookSourceBinding.bind(...).ivDebugText.visibility == View.VISIBLE`；第 307 行 `ItemBookSourceBinding.bind(...).ivDebugText.visibility`；第 384 行 `ItemBookSourceBinding.bind(child).cbBookSource.text.toString()`。
- 旧文件 `SourceDragOrderUiTest.kt`（同目录）：第 19 行 import 同一 binding 类；第 116 行 `ItemBookSourceBinding.bind(row).tvBookshelfCount.text.toString()`。
- 拆除前逐行清单的存活回源：`docs/验收-复核-Q465-F1-SourceManage.md:86`（N3 记录上述行号）与 `docs/修复-androidTest可修五件.md` §三-B。

### 2.2 旧控件字段命中（改前）

- `cbBookSource`：2 处，位于旧 `BookSourceCheckUiTest.kt` 第 138、384 行。
- `tvBookshelfCount`：2 处，位于旧 `BookSourceCheckUiTest.kt` 第 178 行、旧 `SourceDragOrderUiTest.kt` 第 116 行。
- `ivDebugText`：2 处，位于旧 `BookSourceCheckUiTest.kt` 第 260、307 行。

### 2.3 main 侧缺席证据（binding 类确实产不出来）

- 布局源：`find app/src/main modules -name "item_book_source*"` 空；`app/src/main/res/layout/` 下 `ls | grep -i book_source` 空；HEAD 对象 `app/src/main/res/layout/item_book_source.xml` 不存在（`git cat-file -e HEAD:...` fatal: Not a valid object name，只读核验）。
- 绑定包：`find app/src/main modules -type d -name databinding` 空；仓内无手写 `ItemBookSourceBinding` 类声明。
- 历史沿革：该布局在 C3 书源域重写提交 `76e7db3` 随旧 8 kt + 7 资源一并删除；旧 `BookSourceAdapter` 类同样零声明，仅以 KDoc 残留于 `BookSourceActivity.kt:46`、`SourceManageRow.kt:33`、`SourceManageScreen.kt:70`。
- 附加 R 成员缺口（Q-297 B 段已记）：`check_status_filter`、`menu_show_source_check_status`、`menu_show_source_bookshelf_count`、`menu_block_source_navigation`、`menu_sort_desc` 在 main 的 res/ 与 kt 全量零命中；即使补回 binding，这些引用仍编不过。

## 三、旧测试原本守的回归点（逐条文字，不删点）

### 3.1 `BookSourceCheckUiTest.kt`（5 个 @Test）

1. `bookshelfCountsTrackCurrentSourcesAndShelfChangesWithoutLosingSelection` —— 书架计数随架上书增减变化；`notShelf` / `local` 类型不计入；书归属源迁移后计数改挂；删书回落；分组筛选后勾选选中态保留；`ActivityScenario.recreate()` 后计数与选中态存活。
2. `statusFilterIntersectsGroupAndSurvivesRecreation` —— 校验状态下拉与分组筛选取交集；关闭状态显示后状态行与筛选 Spinner 隐藏；recreate 后筛选结果与可见性存活。
3. `defaultOffStatusAndNavigationTogglesAreIncludedInRealSettingsBackup` —— 三偏好默认 false；主菜单条目顺序与导航项图标存在；拦截导航开关写入 `AppConfig`；状态开关打开后调试文本可见；真实备份 `config.xml` 含 `showSourceCheckStatus`、`blockSourceNavigation` 两键；恢复后两键回 true；重建后菜单勾选与 Spinner 可见。
4. `realServicePersistsFailureAndSuccessWithoutChangingGroups` —— 真实校验会话：缺搜索规则判 FAILED（不发网络请求）；关掉全部规则后判 PASSED；校验不改写书源分组与注释。**执行层，salvage 承接。**
5. `nonHttpDomainIsUncheckableAfterOtherChecksPass` —— `local://` 非 http 域判 UNCHECKABLE；mainJs 搜索返回空列表后改判 FAILED；分组与注释保留。**执行层，salvage 承接。**

### 3.2 `SourceDragOrderUiTest.kt`（5 个 @Test，书源两条合并为第 1 条陈述）

1. `filteredBookDragPreservesHiddenOrder` / `descendingBookDragPreservesHiddenOrder` —— 书源过滤态 / 降序态拖拽，隐藏行顺序不被重排；拖拽不落悬不写持久序；释放后 `customOrder` 持久化；重开列表顺序存活。
2. `filteredRssDragPreservesHiddenOrder` —— RSS 源同型拖拽与隐藏序保护。
3. `filteredReplaceDragPreservesHiddenOrder` —— 替换规则同型拖拽与隐藏序保护。
4. `heldBookDragWithDeletedTargetDoesNotMoveAnotherSource` —— 按住拖拽中途库内失效刷新 / 删除目标源，不挪走其他条目；注释刷新不改持久元数据。

## 四、新实现等价入口核查

### 4.1 书源管理页已是 Compose 宿主

- `BookSourceActivity.kt:78` `setContent { ... SourceManageScreen(state = state, vm = vm, ...) }`；`:52` `private val vm by viewModels<SourceManageViewModel>()`——VM 在宿主私有，instrumentation 线程无公开出口可取实例。
- 新行卡片 `SourceManageRow.kt:33` KDoc 自述「Compose 自绘，替代旧 BookSourceAdapter / item_book_source.xml」；书架计数渲染于 `SourceManageRow.kt:120`（`badges += stringResource(R.string.source_bookshelf_count, bookshelfCount)`），调试文案渲染于 `SourceManageRow.kt:152`（`Debug.debugMessageMap[source.bookSourceUrl]`）。
- 可定位钩子核查：`manage/` 全包 `testTag` / `semantics` / `TestTag` 零命中；`app/src/androidTest/` 与 `app/build.gradle` 内无 `createComposeRule` / `AndroidComposeTestRule` / `testTagsAsResourceId`——仓内尚无 Compose UI 测试基建。

### 4.2 新状态出口（存在，但仅 JVM 可达）

- 状态模型：`SourceManageState.kt:96` `showCheckStatus`、`:97` `showBookshelfCount`、`:99` `blockSourceNavigation`、`:100` `checkStatusFilter`、`:103` `bookshelfCounts`、`:104` `selected`。
- VM 动作：`SourceManageViewModel.kt:214` `setCheckStatusFilter`、`:218` `toggleShowCheckStatus`、`:229` `toggleShowBookshelfCount`、`:235` `toggleBlockSourceNavigation`、`:244` `toggleSelect`；`:59` `val uiState: StateFlow<SourceManageUiState>`。
- 不等价事实：这些出口挂在私有 VM 上，Compose 节点无 testTag；不改 main（本单禁碰）的前提下，androidTest 既定位不到行节点，也读不到 VM 状态。旧 3.1-1/2/3 与 3.2 各用例的 View 层断言无可等价改写的入口。

### 4.3 既有测试接管清单

- 书源拖拽纯顺序（跨隐藏行重定位、降序语义、缺键不动）：`app/src/test/java/io/legado/app/ui/book/source/manage/BookSourceManageOrderTest.kt:8`。
- 校验状态三态持久往返、状态不污染源与排序：`app/src/androidTest/java/io/legado/app/data/BookSourceCheckStateTest.kt:44`（UNCHECKABLE 落库）、`:76`（`statusDoesNotPolluteSourceAndRankingDoesNotInvalidateRules`）。
- CheckSource 只写归属字段、会话停止与完成释放：`app/src/test/java/io/legado/app/service/CheckSourcePersistenceContractTest.kt:10`。
- 导航拦截偏好行为（opt-in 拦截 HTTP / 交互详情保留）：`app/src/androidTest/java/io/legado/app/help/source/SourceNavigationUiTest.kt:77`。
- 设置项归档 / 还原通用通道（含 `config.xml` 读取断言）：`app/src/androidTest/java/io/legado/app/help/storage/BackupOptionsTest.kt:323`（显式设置还原）、`:346`（阅读器控件显式 false 保留）、`:367`（zip 内 `config.xml` 文本断言）。
- 三偏好默认 false：`app/src/main/java/io/legado/app/help/config/AppConfig.kt:41`（`showSourceCheckStatus`）、`:44`（`showSourceBookshelfCount`）、`:47`（`blockSourceNavigation`），getter 默认值均为 `false`。

## 五、处置动作

### 5.1 新增 salvage 文件（执行层两条，不带任何 View 符号）

- 路径：`app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt`。
- 承接用例：`realServicePersistsFailureAndSuccessWithoutChangingGroups`（新文件 `:60`）、`nonHttpDomainIsUncheckableAfterOtherChecksPass`（新文件 `:82`）。
- 拆掉的旧脚手架：`ActivityScenario`、`SearchView` / `TitleBar` / `Spinner` / `RecyclerView` / `FastScrollRecyclerView` 查找、菜单 id、Preference 快照、Backup/Restore 归档段、截图段；只留 DAO fixture 插入/清理、CheckSource 标志位快照还原、Debug 会话启停、15s `waitUntil` 轮询。
- 注释口径：文件头注释写明本文件由来与拆除依据；标志位快照注释写明「进程内可变静态，不还原会污染同进程后续校验用例」。

### 5.2 删除两份无等价入口的测试文件

- 删除 `app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckUiTest.kt`：3 条 View 层用例（§3.1-1/2/3）判作废，回归点挂 §4.3 接管或记入 §九 缺口；2 条执行层用例已 salvage（§5.1）。
- 删除 `app/src/androidTest/java/io/legado/app/ui/book/source/SourceDragOrderUiTest.kt`：拖拽手势整链路依附 RecyclerView / `RecyclerAdapter` / `ItemTouchHelper` / 旧菜单；书源顺序挂 `BookSourceManageOrderTest.kt:8`，RSS / 替换规则分支缺口记 §九。
- 删除方式：工作树 `rm`，未做 git add/commit（禁 git 写）；旧文件原行号在本报告 §2.1/§3 仅作取证陈述，回源锚点一律指向存活文件。

## 六、grep 回证（改前 / 后实测）

### 6.1 命中计数对照

| 符号 | 改前命中 | 改后命中 |
|---|---|---|
| `ItemBookSourceBinding` | 8 | 0 |
| `cbBookSource` | 2 | 0 |
| `tvBookshelfCount` | 2 | 0 |
| `ivDebugText` | 2 | 0 |
| `BookSourceAdapter` | 3（旧 import 1 + 强转 2，随文件删除） | 0 |

### 6.2 改后回证命令与结果

- `grep -rn "ItemBookSourceBinding" app/src/androidTest/` → 无输出，命中 0。
- `grep -rn "cbBookSource\|tvBookshelfCount\|ivDebugText" app/src/androidTest/` → 无输出，各 0。
- `grep -rn "BookSourceAdapter" app/src/androidTest/` → 无输出，命中 0。
- 新文件入口：`BookSourceCheckSessionTest.kt:60` `@Test fun realServicePersistsFailureAndSuccessWithoutChangingGroups()`；`:82` `@Test fun nonHttpDomainIsUncheckableAfterOtherChecksPass()`；两条用例内 `CheckSource.start` 落点 `:70`、`:77`、`:100`、`:108`；状态断言 `:72`（FAILED）、`:79`（PASSED）、`:102`（UNCHECKABLE）、`:110`（FAILED）。
- 语法预检：`python3 data_scripts/kotlin-syntax-lint.py app/src/androidTest/java/io/legado/app/ui/book/source/BookSourceCheckSessionTest.kt` → `KOTLIN-LINT: PASS（1 个文件）`，rc=0。

## 七、静态可编译结论与依据（不声称编译通过）

- 本单改动范围内，静态结论：**可编译风险已清到本单边界内**。依据：① 新文件 4 条 import（`appDb`、`BookSource`、`CheckSource`、`Debug`）与调用的 DAO 方法（`insert`/`delete`/`update`/`getBookSourcePart`/`getBookSource`/`getCheckState`）、模型静态（`checkXxx`/`start`/`stop`）、会话 API（`tryStartCheckSession`/`isChecking`/`currentCheckSession`）均在 main 解析命中；② 旧两份文件删除后，§6.1 五个旧符号在 androidTest 零命中；③ 括号配平 / 条件尾逗号 lint PASS。
- 不声称整车道编译通过：① 他批次残留悬空尚未清零（§九-4），那些文件仍可能编不过；② 沙箱无 JDK / Android 编译链，未跑 `./gradlew`、未跑 instrumentation，Compose 测试基建是否齐备也未上机验证；③ 本结论是 L0 静态层（符号存在性 + 词法），不等价于 L2 编译 + 单测全绿。

## 八、自查反例（先假设本方案错误，再找边界推翻）

- 反例 1：「整类删掉最省事，两条会话用例也一起废」。推翻：那两条不触 View 栈，`CheckSource.start` 端到端归类（缺规则 FAILED / 全关 PASSED / 非 http UNCHECKABLE）在仓内无其他测试驱动（全仓 grep 仅旧文件调 `CheckSource.start`）；整删会在执行层留回归空洞。处置：salvage 到纯执行层新文件。
- 反例 2：「把旧断言改写成读 VM uiState 就行」。推翻：VM 是宿主私有属性（`BookSourceActivity.kt:52`），屏上无 testTag/semantics，androidTest 既拿不到 VM 实例也定位不到行节点；要接只能反射或改 main，两条都被本单边界禁掉。处置：这部分判无等价入口，作废并挂接管/记缺口。
- 反例 3：「删掉 import 留个空测试类，编译先过再说」。推翻：空跑测试不守门，违反「不留空跑」；且旧类内残留的菜单 id / Spinner / RecyclerView 强转照样编不过。处置：整文件拆除，回归点逐条落账。
- 反例 4：离线 / 服务起不来时 salvage 用例会怎样。边界：`CheckSource.start` 拉起 `CheckSourceService` 异步会话，`waitUntil` 15s 超时断言失败；与旧用例同口径，本单未放宽超时。若 CI 环境服务不可用，属运行期问题，须在 CI 定性而非本地降级。

## 九、新发现问题（每条带状态词）

1. **【待补】RSS 源拖拽排序持久化无测试承接**：`RssSourceActivity.kt:188` 仍走 `initRecyclerView()` + `ItemTouchHelper`（View 栈存活），删 `SourceDragOrderUiTest.kt` 后 RSS 分支的隐藏序保护、重开顺序存活无回归守护。
2. **【待补】替换规则拖拽 / 选择无测试承接**：`ReplaceRuleActivity.kt:64` 已 Compose，无 testTag；既有 `ReplaceRuleViewModelGroupContractTest` 只覆盖分组委派，不护拖拽顺序与批量选择。
3. **【待补】书源管理页计数与多选 instrumentation 层无断言入口**：`bookshelfCounts` / `selected` 只在私有 VM 状态里，新屏无定位钩子；需要补 Compose testTag 或 VM 测试出口后才能重写 UI 断言。
4. **【待裁 · 他批次，本单不动】**：`RuleSelectionShareTest.kt`（`DictRuleAdapter`、`ReplaceRuleAdapter`）、`SourceOrderMetadataUpdateTest.kt`（`ReplaceRuleAdapter`、`ReplaceRuleViewModel`）、`CodeSelectionUiTest.kt`（`BookSourceEditAdapter`、`BookSourceEditViewModel`）、`CoverStylePreviewUiTest.kt`（`CoverFontConfigFragment`）、`CoverTitleAdaptiveUiTest.kt`（`CoverConfigFragment`）仍引用已删除类；`ReadRecordHistoryTest.kt` 的 `ActivityReadRecordBinding` / `ItemReadRecordDisplayBinding` 对应布局缺位。沿用 Q-297 待裁口径，交对应批次收口。
5. **【观察】选中态跨重建持久化口径变化**：旧测试守「勾选选中态 recreate 后存活」；新 `selected` 为内存态集合，进程重建 / 配置变更后的存活口径需规格确认，当前无断言。
6. **【观察】三个书源偏好键无显式归档断言**：既有备份测试走通用设置通道（`BackupOptionsTest.kt:323`/`:346`），未逐条断言 `showSourceCheckStatus` / `showSourceBookshelfCount` / `blockSourceNavigation` 三键各自的归档往返；旧用例只显式断言前两键，`showSourceBookshelfCount` 备份段随删除一并移除。

## 十、二阶三阶影响与认知成本

- 二阶影响：删除两份 UI 测试后，书源管理页的手工回归依赖转由 §4.3 既有 JVM / DAO 测试与后续 Compose 测试钩子覆盖；CI 若当前不编 androidTest，本单不改变 CI 绿红，只是拆掉一处将来编到就红的断链。
- 三阶影响：salvage 新文件与 `BookSourceCheckStateTest.kt` / `CheckSourcePersistenceContractTest.kt` 同属执行层守护，后续若 `CheckSource` 会话签名调整，三处需同步；新增文件无新抽象、新概念，接手人按包路径即可定位。
- 认知成本：报告把「作废」与「接管」逐条挂钩，避免下一棒误以为书源页回归已全覆盖；缺口（§九-1/2/3）显式标待补，不算已守。
