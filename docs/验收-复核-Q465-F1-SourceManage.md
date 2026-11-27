锚点卡：docs/锚点/验收-Q465-F1-SourceManage.md

被验 commit：46a8852（已入库 push；在 09b2150 之后；本单编译面待云端 CI）
总结论：有条件通过（编译面待 CI）
判据：五条静态层 4 PASS / 1 部分 PASS（部分 PASS 项为静态可编译性，clean 编译与单测以云端 CI 为准）
新缺陷：0 条 FAIL；新发现问题 4 条（3 条待裁沿用 / 1 条已入队在册）

> 复核对象：Q-465 F1 书源管理组合期直读 AppConfig——被验三文件 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt`、`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt`、`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt`；被验文档 `docs/修复-Q465-F1-SourceManage组合期AppConfig.md`；写码方锚点卡 `docs/锚点/Q465-F1-SourceManage.md`；缺陷回源 `docs/锚点/Q461-B29book-source.md` §五 F-1（`:56`）。
> 边界：只读验收——未改三个被验源码与修复报告正文、未执行任何 git 写操作（add/commit/push/revert/checkout）、未本地编译、未跑 gradlew/kotlinc/assemble。编译面、单测与截图帧表现以云端 CI 及上机为准。

## 一、复核方法

- 逐条回源码取数：五条判据逐项 `grep -n` / 逐行 Read 取证，行号以 HEAD `46a8852` 快照为准；不采信写码方自评，修复报告与写码方锚点卡仅当待验主张清单。
- 修前基线取 `git show 46a8852^:<file>` 逐字对照；机检：kotlin-syntax-lint + acceptance-evidence `--rev 46a8852` + cross-module-guard + debt-ratchet + check-combo-coverage，末行见 §八。
- 本单为提交验收，证据包走 `--rev 46a8852` 快照模式；被验三文件与 HEAD `46a8852` 逐字一致（`git status` 对该三文件零改动），工作区另有其他在飞代理的改动，不在本单只读范围内。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「B2 改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「C3 规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 适用条款：B2 改动后 grep 回证代码真写入（本单 §二每条判据附命令与命中行号）；C3 偏好读取路径在截图环境无 Application 时不留崩溃口，状态字段带默认值兜底；子代理禁 git 写操作（本单未 add/commit/push/revert/checkout）；禁本地编译（未跑 gradle/kotlinc/assemble，docs/机制-子代理必读.md:12）；验收方独立取数、不采信写码方计数。

## 二、五条判据销项

### 判据 1 · 组合期零触碰：整条 Compose 链逐一定级 —— PASS（静态层）

链路自渲染点向上追溯四层，每层 `AppConfig` / `appCtx` / `help.config` / `PreferKey` / `getPrefBoolean` 逐一 grep 定级：

- 渲染点 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:131`：`checked = state.blockSourceNavigation,`；`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:132` `onToggle = { vm.toggleBlockSourceNavigation() }`（回调引用，不在组合期求值）。命令 `grep -n "AppConfig\|appCtx\|help.config\|PreferKey\|getPrefBoolean" app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt` 实测 exit=1、0 命中。
- 分发层 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:55`：`MainOptionsSheet(state, vm, actions)`；`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:48-52` `ManageSheets(state, vm, actions)` 形参只接 state/vm/actions。
- 屏层 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageScreen.kt:138`：`ManageSheets(state = state, vm = vm, actions = actions)`；`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageScreen.kt:77-80` 形参 `state: SourceManageUiState, vm: SourceManageViewModel, actions: SourceManageActions`。命令 `grep -n "AppConfig\|appCtx\|help.config\|PreferKey\|getPrefBoolean" app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageScreen.kt` 实测 0 命中。屏内两处 `remember`：`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageScreen.kt:83` `remember { mutableStateOf(false) }`、`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageScreen.kt:353` `remember { mutableStateOf<String?>(null) }`，均为本地局部状态，无配置补读。
- 宿主层 `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:78-81`：`setContent { val state by vm.uiState.collectAsState(); SourceManageScreen(state = state, vm = vm, actions = this@BookSourceActivity) }`，组合 lambda 内只有状态订阅与屏调用，无配置读取。`app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:52` `private val vm by viewModels<SourceManageViewModel>()`。
- 宿主体内另一处配置引用：`app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:20` import `LocalConfig`；`app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:83` `if (!LocalConfig.bookSourcesHelpVersionIsLast)`。该语句位于 `onCreate` 方法体、`setContent` 闭包之外（`:77` super、`:78-81` setContent、`:82` observeCheckBus、`:83-85` 帮助版本判断），属宿主生命周期阶段，非 Compose 组合期；且为修前存量、本单未改。
- VM 侧命中逐条定级（非组合期）：`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:10` import；`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:68` init 读 `AppConfig.showSourceCheckStatus`；`:69` init 读 `AppConfig.showSourceBookshelfCount`；`:70` init 读 `AppConfig.blockSourceNavigation`；`:220` 事件回调写 `AppConfig.showSourceCheckStatus`；`:231` 事件回调写 `AppConfig.showBookshelfCount`；`:238` 事件回调写 `AppConfig.blockSourceNavigation`。VM 由宿主 `viewModels()` 在 `onCreate` 构造，init 跑在宿主侧、真实 Application 已就绪；回调由点击触发，均不在组合期。
- State 文件：`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt:74` KDoc、`:98` 行注释提及 AppConfig，均为文本，无代码读取。
- 同目录其余文件与兄弟子域复核：`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt`、`app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceGroupFilter.kt`、`app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt` grep `AppConfig|appCtx|Config` 实测 exit=1、0 命中；`app/src/main/java/io/legado/app/ui/book/source/debug/` 全目录 0 命中。遗留两条非本域命中：`app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditViewModel.kt:301` `SourceConfig.removeSource(...)`（VM 数据路径）、`app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt:163` `LocalConfig.ruleHelpVersionIsLast`（Activity 生命周期体），均非本单文件域、非组合期。
- remember 补读掩盖排查：`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:215` 全部 `remember` 命中第 216 / 217 / 314 / 360 / 495 行及 `rememberSourceSheetExitController()` 各调用点；除第 360 行读 `CheckSource.keyword`（见新发现问题 N1）外，其余均为 `mutableStateOf` 局部初值，无一处 `remember { AppConfig... }` 或偏好回读。

### 判据 2 · 语义等价：默认值 / 取反 / 写回时机逐字对照 —— PASS（静态层）

修前基线取自 `git show 46a8852^`，逐字对照三处：

- 渲染读值（修前 → 修后）：修前 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:131`（旧快照）`checked = io.legado.app.help.config.AppConfig.blockSourceNavigation,`；修后 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:131` `checked = state.blockSourceNavigation,`。标签 `R.string.block_source_navigation`、`onToggle` 接线未动。
- 偏好默认值：`app/src/main/java/io/legado/app/help/config/AppConfig.kt:48` `get() = appCtx.getPrefBoolean(PreferKey.blockSourceNavigation, false)`，默认 `false`；`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt:99` `val blockSourceNavigation: Boolean = false,`，默认 `false`，逐字对齐。修前 State 无该字段（`git show 46a8852^:...SourceManageState.kt | grep blockSourceNavigation` exit=1）。
- 取反与写回（修前 → 修后）：修前 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:235`（旧快照）`AppConfig.blockSourceNavigation = !AppConfig.blockSourceNavigation`，直接读偏好取反再落偏好；修后 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:237` `val next = !_uiState.value.blockSourceNavigation`、`:238` `AppConfig.blockSourceNavigation = next`、`:239` `_uiState.update { it.copy(blockSourceNavigation = next) }`，改以 state 镜像取反，落偏好并回写镜像。
- 等价前提取证：全仓 `grep -rn "blockSourceNavigation" app/src/main` 写方仅 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:238` 一处；`app/src/main/java/io/legado/app/help/config/AppConfig.kt:49` 为属性 setter 定义；`app/src/main/java/io/legado/app/constant/PreferKey.kt:23` 为键常量。无第二处写偏好路径，镜像与偏好恒等，取反结果与旧实现等价。
- 写回时机：新旧实现都在开关点击回调内同步落偏好；旧实现无镜像回写（下次组合直读偏好），新实现多一步镜像回写。回调触发点不变（`SourceManageSheets.kt:132` → `vm.toggleBlockSourceNavigation()`），用户感知时机零变化。
- 已核对的细微差异（不构成缺陷，登记口径）：事实源由「偏好单源」变为「偏好 + VM 镜像双份同步」；若未来出现第二处偏好写方，镜像需另设同步机制。当前全仓无第二写方，该差异不产生实际行为分歧。

### 判据 3 · 二阶影响：运行期只读路径不打架，重建后勾选态可复原 —— PASS（静态层）

- 共享偏好键的两只运行期读方：`app/src/main/java/io/legado/app/help/source/SourceNavigationBridge.kt:15` `private fun allowed() = !shouldSuppressSourceNavigation(AppConfig.blockSourceNavigation, context)`；`app/src/main/java/io/legado/app/help/JsExtensions.kt:1300` `if (!shouldSuppressSourceNavigation(AppConfig.blockSourceNavigation, context)) return true`。两处均为 WebView 桥 / Rhino JS 扩展运行期路径，调用时带真实 `context`，直读偏好、不写偏好；不经本屏 state 镜像，也不订阅镜像。本单写入偏好的值与旧实现逐字相同，桥与 JS 侧读到的结果不变，无状态镜像打架路径。
- 配置变更复原推演：旋转（配置变更）场景，`by viewModels()` 保留 ViewModel 实例，镜像随 VM 存活；镜像恒等于偏好，勾选态正确。进程重建 / 冷启动场景，Activity 重新 `onCreate` 构造新 VM，`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:66-72` init 块重新从 `AppConfig.blockSourceNavigation` 读入镜像，勾选态按偏好复原。VM 继承链 `app/src/main/java/io/legado/app/base/BaseViewModel.kt:17` `BaseViewModel(application) : AndroidViewModel(application)`，init 执行时 Application 已就绪。
- 数据流面：`uiState` Flow 形态未变，`collectAsState` 订阅链未变；未引入新线程、新持久化键、新依赖。偏好键 `PreferKey.blockSourceNavigation`（`app/src/main/java/io/legado/app/constant/PreferKey.kt:23`）未改名，备份 / 恢复侧既有用法不受影响。

### 判据 4 · 测试面：新增字段默认值不破坏既有测试 —— PASS（静态层）

- 全仓构造点：`grep -rn "SourceManageUiState(" app/src/` 实测仅两处命中——`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt:89` data class 声明、`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:58` `MutableStateFlow(SourceManageUiState())` 默认构造。`app/src/test/` 与 `app/src/androidTest/` 无任何 `SourceManageUiState(` 构造点。新字段带默认 `false`，默认构造与既有命名参 `copy(...)` 调用均向后兼容，不产生编译红。
- JVM 契约测试复核：`app/src/test/java/io/legado/app/SelectionIntervalGuardTest.kt:27`、`:32`、`:33` 按文件路径与方法名 `selectCheckedInterval` 做文本匹配；`app/src/test/java/io/legado/app/ui/book/source/manage/BookSourceViewModelGroupContractTest.kt:17`、`app/src/test/java/io/legado/app/ui/menu/Issue1008MenuContractTest.kt:74`、`app/src/test/java/io/legado/app/ui/menu/SelectActionBarPopupActionMigrationTest.kt:137`、`app/src/test/java/io/legado/app/ui/menu/SourcePopupActionMigrationTest.kt:190` 均只引用源码路径常量或符号名，不断言被删除的旧直读表达式，也不依赖行号；本单改动不触及这些方法名与路径。
- androidTest 复核：`app/src/androidTest/java/io/legado/app/help/source/SourceNavigationUiTest.kt:60`、第 77 / 102 / 129 行直读直写 `AppConfig.blockSourceNavigation` 偏好；另已删 androidTest 件 `BookSourceCheckUiTest.kt` 旧第 218 / 250 / 277 / 280 / 283 行同项断言（仓内已无此文件）；偏好键名与读写语义未变，这些断言不受镜像改动影响。遗留漂移见新发现问题 N3（非本单引入）。

### 判据 5 · 静态可编译性：语法 lint 过，类型贯通为静态推理 —— 部分 PASS

- 语法级：命令 `python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt` 实测 `KOTLIN-LINT: PASS（3 个文件）`，退出码 0；括号配平、条件尾逗号、符号出处等硬错无命中。
- 类型贯通静态推理（未跑编译器，列未验证项）：`SourceManageState.kt:99` 字段类型 `Boolean`；`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:70` 命名参 `blockSourceNavigation = AppConfig.blockSourceNavigation` 类型 Boolean 对齐；同文件第 237 行 `next` 由 `!_uiState.value.blockSourceNavigation` 推得 Boolean；第 238 行 setter 接收 Boolean；第 239 行 `copy(blockSourceNavigation = next)` 命名参与字段名逐字一致；`SourceManageSheets.kt:131` 传入 `SheetToggle` 形参 `checked: Boolean`（`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:616`）。
- 改动面 import 复核：`git show 46a8852` diff 三生产文件无 import 增删；无新文件、无函数签名改动、无可见度改动。跨文件调用链 `BookSourceActivity.kt:80` → `SourceManageScreen.kt:77-80` → `SourceManageScreen.kt:138` → `SourceManageSheets.kt:55` 签名均未变。
- 编译面风险清单：若 CI 红，最可能落点为 `SourceManageState.kt:99` 字段名 / 类型与 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:70`、同文件第 239 行命名参拼写不一致，或 `SourceManageSheets.kt:131` state 上解析不到 `blockSourceNavigation`；静态逐字比对三处名字一致。证据包快照模式两条「疑似漏 import」为种子表误报：`SourceManageState.kt` 的 `ALL` / `Default` / `Url` 是同文件内枚举 `SourcePreset.ALL`（`:21`）、`BookSourceSort.Default` / `Url`（`:16`）；`ViewModel.kt` 的 `HashMap` 走 Kotlin 默认 `java.util.*` 导入（`:61`），无需显式 import。
- 受限说明：真类型推断、重载解析、泛型约束、协程契约只有 clean 编译能判；本沙箱无 gradle/JDK/SDK，未编译未跑单测，按验收口径编译面最高只到「有条件通过（编译面待 CI）」。

## 三、改坏推演（反例自查）

- 反例一：若 `MainOptionsSheet` 用 `remember { AppConfig.blockSourceNavigation }` 包一层。`remember` 块仍在组合期执行，照样触发 `AppConfig` 类初始化，截图环境同一 NPE 路径；实测本单 `remember` 块内无此写法（判据 1 已逐条列命中行）。
- 反例二：若只把写回挪 VM、读值留在 Sheet。组合期实参仍直读偏好，崩因未除；本单读、写两侧都收口到 VM，屏内零 `AppConfig` 命名。
- 反例三：若 state 字段默认取 `true` 或省略默认。与偏好默认 `false`（`AppConfig.kt:48`）语义不等价，首次进界面开关会跳变；现默认 `false` 逐字对齐。
- 反例四：若取反仍从偏好读（`next = !AppConfig.blockSourceNavigation`）。组合路径之外留偏好回读，且与镜像可能错位；现以镜像为准取反，单一事实源在 state，写回双向同步。
- 反例五（二阶）：若后续有人绕过本 VM 直接写偏好键。当前 grep 证无第二写方；一旦出现，镜像需补监听 / 重读机制，此为未来扩展点，非本单缺陷。
- 反例六（三阶）：若 VM init 读偏好抛异常中断构造。init 回填走 `_uiState.update`，字段自带默认 `false`；既有 `bindSources` / `bindGroups` / `bindBookshelfCounts` 链路 `.catch { AppLogPut(...) }` 兜底口径不变。

## 四、新发现问题

- N1（待裁 · 留观）：`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:360` `remember { mutableStateOf(CheckSource.keyword) }` 与 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:377` `keyword.ifBlank { CheckSource.keyword }` 在组合期读 `CheckSource.keyword`。核查 `app/src/main/java/io/legado/app/model/CheckSource.kt:20` 为内存 `var keyword`，不碰 `appCtx` / 偏好，当前无截图崩溃风险；结构上同属「组合期读静态单例」家族。非本单 AppConfig 文件域，未改动；若后续 `keyword` 接入持久化或 Context，需按本单同口径上提 VM-state。
- N2（已登记待裁）：`app/src/main/java/io/legado/app/help/source/SourceNavigationBridge.kt:15` 与 `app/src/main/java/io/legado/app/help/JsExtensions.kt:1300` 运行期直读 `AppConfig.blockSourceNavigation`。两处属 help 域业务 / 桥路径，调用时有真实 context，非截图组合期；与本开关共享同一偏好键，后续全局 AppConfig 组合期巡检需一并登记。本单文件域不含 help 域，不动手，交 help 域派单评估。
- N3（待裁 · 既有漂移，非本单引入）：已删 androidTest 件 `BookSourceCheckUiTest.kt` 旧第 33 / 138 / 178 / 260 行与 `SourceDragOrderUiTest.kt` 旧第 19 / 116 行仍引用 `ItemBookSourceBinding` 及 legacy `R.id.recycler_view` / `R.id.search_view` / `R.id.title_bar`；`app/src/main/res/layout/` 下 `item_book_source.xml` 及其字段 `cbBookSource` / `ivDebugText` / `tvBookshelfCount` 在 `46a8852^` 与 HEAD 均缺位（`find app/src/main/res -name "*item_book_source*"` 空、`grep -rln` 三字段 exit=1）。该漂移早于本单提交，`46a8852` 未触碰 androidTest 源集；交 androidTest 域核 CI 编译状态，本单不代收口。
- N4（已入队 · 在册历史包袱）：`app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt` 649 行 ≥ 600 行棘轮线；修前快照即 649 行（`git show 46a8852^:...SourceManageSheets.kt | wc -l` = 649），本单仅替换 1 行、净增 0 行。debt-ratchet 实测 `oversized_warn: 50` 未超基线，属在册历史包袱；拆分已入队另单，不在本单收口范围。

## 五、未验证项（显式）

- 未编译、未跑单测：沙箱无 gradle/JDK/SDK；Kotlin 类型贯通、state 字段在 Flow 拷贝中的传递、回调签名匹配均为静态推断，待云端 CI `Verify` 编译与单测终验。
- 截图帧回归：`screenshot/src/test/java/io/legado/app/ci/screenshot/` 源集 grep 无 `SourceManage` / `BookSourceActivity` / `source.manage` 命中，当前无本屏 Paparazzi 帧；帧级绿红以后续 CI Screenshot 任务为准，本单静态结论为组合路径已不触达 `AppConfig`。
- 真机交互：开关取反后偏好持久化、回后台再返回的镜像一致性未上机观察；§二镜像恒等推演基于「全仓仅此一处写入方」的 grep 事实。
- N3 遗留 androidTest 漂移的 CI 编译红 / 绿未在本沙箱复核，交 androidTest 域与 CI 结果定性。

## 六、变更清单回核（绝对路径）

- 生产改动（3 个，均在白名单内）：`/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt`、`/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt`、`/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt`。
- 文档改动（2 个，随提交入库）：`/home/user/Doubao/chats/38454122521460994/legado/docs/修复-Q465-F1-SourceManage组合期AppConfig.md`、`/home/user/Doubao/chats/38454122521460994/legado/docs/锚点/Q465-F1-SourceManage.md`。
- 未触碰：`edit/`、`debug/` 子域；bookshelf 已验证实现；config / autoTask / dict / replace 四域；`app/src/test/**`；`app/src/androidTest/**`；`app/src/main/assets/**`；`data_scripts/**`；既有 docs 报告。未做 git 写操作。

## 七、复核脚本与真实输出（可复跑）

- 组合期零触碰：`grep -n "AppConfig\|appCtx\|help.config\|PreferKey\|getPrefBoolean" app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt` → exit=1，0 命中；`grep -n "AppConfig\|appCtx\|help.config\|PreferKey\|getPrefBoolean" app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageScreen.kt` → 0 命中。
- 写方唯一性：`grep -rn "blockSourceNavigation" app/src/main` → 命中清单见 §二判据 2，写方仅 `SourceManageViewModel.kt:238`。
- 测试构造点：`grep -rn "SourceManageUiState(" app/src/` → 仅 `SourceManageState.kt:89`、`SourceManageViewModel.kt:58`。
- 语法 lint：`python3 data_scripts/kotlin-syntax-lint.py <三文件>` → `KOTLIN-LINT: PASS（3 个文件）`，rc=0。
- 证据包：`python3 data_scripts/acceptance-evidence.py --rev 46a8852` → 三生产文件 + 两文档；快照 lint 唯一 FAIL 为 S4 行数棘轮（Sheets 649 行，修前即 649，详见 N4），余两条「疑似漏 import」为种子表误报（详见判据 5）。
- 机检：`cross-module-guard.py` → `GUARD: PASS（含 WARN，需人工核）`（WARN 属工作区在飞的 B3SettingScreen.kt，非本单）；`debt-ratchet.py` → `RATCHET: PASS`；`check-combo-coverage.py` → `COMBO-COV: PASS（关键对 27 条，缺陷 9 条均已落层归因）`。
- 交付门禁闸（末行原文）：
  - `python3 data_scripts/check-report-fields.py docs/验收-复核-Q465-F1-SourceManage.md` → `REPORT-FIELDS: PASS（1 份，0 份不合格）`，rc=0。
  - `python3 data_scripts/check-anchor-card.py docs/验收-复核-Q465-F1-SourceManage.md docs/锚点/验收-Q465-F1-SourceManage.md` → `汇总: PASS=2 FAIL=0（共 2 份）`，rc=0（次要提及 F1/C3/N1/N2/N3 的 R2 WARN 不判红）。
  - `python3 data_scripts/check-problem-finding.py docs/验收-复核-Q465-F1-SourceManage.md` → `PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`，rc=0。

## 八、结论

五条判据静态层 4 PASS / 1 部分 PASS：组合期零触碰、语义等价、二阶影响、测试面四项静态成立；静态可编译性语法 lint 通过，类型贯通待云端 clean 编译与单测终验。综合判定 **有条件通过（编译面待 CI）**——本单未新增文件 / import / 签名 / 可见性改动，编译面风险面小，但按验收口径不替 CI 背书。编译、单测、截图帧与真机交互以云端 CI 及上机为准。
