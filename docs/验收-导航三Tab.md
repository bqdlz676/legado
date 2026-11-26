> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批1-搜索书架 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/D16-名物库.md

# 验收-D25 导航 3 Tab + 搜索下拉（独立验收 · 判低返工待验单）

- 验收对象：`docs/问题清单.md:157` D25「导航 3 Tab + 搜索下拉」，状态「判低返工已修·待验证」；修复报告 `docs/修复-D25-导航三Tab.md`；反思 `docs/反思-导航三Tab-D25.md`；锚点 `docs/锚点/D25-导航.md`。
- 仓库：`/home/user/Doubao/chats/38454122521460994/legado`；HEAD `e381a8d1706c675876adbe1f9c4b18dc690245b5`（与派单一致）。
- 验收方式：只读静态复核；未改 `app/**`、未改既有 docs 报告；未 git add/commit/push；未本地编译、未跑单测、未真机。工作区在飞脏文件（`docs/问题清单.md` 改动、未跟踪 `docs/修复-沙盘地形起伏.md`）不计入本单结论；本单全部结论以 HEAD 快照为准。
- 结论级别：A=静态可证（代码/XML/提交记录直接读出）；B=静态推断（路径推演，未运行）；C=运行时未证（需真机/CI/联网）。
- 行号口径：全部基于 HEAD 工作树文件内容；提交内旧快照行号与报告所述不一致处以 HEAD 为准。

## 总结论

**FAIL（阻塞，不予通过）**。八判据中：判据 1 PASS；判据 5 PASS（静态，附 1 条缺陷）；判据 3、4、6、7 有条件通过；判据 2 FAIL；判据 8 发现夹带（纯资源，无逻辑夹带）。

阻塞点集中在判据 2：主壳专用搜索入口（底栏搜索位、发现页头部搜索框）确已下线，旧 U8 `SearchActivity` 壳清零；但 `InkSearchActivity` 仍存在多条生产可达启动路径，其中两条就在主壳「发现」Tab 页面内（典籍卡、专题 Sheet 行），不满足 D25 DoD「搜索入口仅书架页下拉浮层、无独立搜索 Activity 入口」。修复报告 §6 已将跨域调用方交接 D1~D7 检索流，反思 §3 也自陈发现页两处可能属漏网；本次验收按派单判据「有任何生产可达路径即 FAIL」判定。

## 判据 1：底部 Tab 数量 = 3 —— PASS（级别 A）

1. 菜单定义处恰好 3 项：`app/src/main/res/menu/main_bnv.xml:7-18`，`menu_bookshelf`(8)、`menu_discovery`(12)、`menu_my_config`(16)；无 `menu_paint`、无第 4 个 `<item>`。
2. 导航槽位模型三常量：`app/src/main/java/io/legado/app/ui/main/MainActivity.kt:92-102`，`idBookshelf=0 / idExplore=1 / idSettings=2`，`realPositions` 为三元素数组（102），`bottomMenuCount = 3`（100）。
3. 选中监听三分支 + `else -> false`：`MainActivity.kt:192-199`；适配器 `getItem` 三分支 `InkShelfFragment / ExploreInkFragment / MyInkFragment`（497-502），`getItemPosition` 三类型校验（488-490），`getCount` 取 `bottomMenuCount`（505-507）。
4. 主壳布局只有一个底栏：`app/src/main/res/layout/activity_main.xml:16-29`，`ThemeBottomNavigationVIew` 挂 `@menu/main_bnv`；全仓 grep `BottomNavigationView` 仅命中主题视图、MainActivity、该布局与该菜单四处，无第二个底栏、无浮动搜索按钮。
5. 条件 Tab 穷举：`MainActivity.kt:327-342` `upBottomMenu()` 仅按 `AppConfig.showDiscovery` 控制「发现」项显隐（隐藏时计数降为 2），不存在「更多」「我的」之类隐藏第四槽；`realPositions` 动态回填的仍是书架/发现/设置三个槽位。
6. 图标默认表三槽：`app/src/main/java/io/legado/app/lib/theme/view/ThemeBottomNavigationVIew.kt:35-40`，分别指向 `ic_tab_bookshelf / ic_tab_discover / ic_tab_settings`。
7. 留档（非本判据阻塞）：第三槽菜单 id 仍名 `menu_my_config`，名实不符（实为设置槽）；反思 §5 已记，改名需同步皮肤槽位键 `MainActivity.kt:103-107`。

## 判据 2：旧独立搜索入口清剿 —— FAIL（级别 A，存在生产可达残留）

### 2.1 已清掉的部分（PASS 项）

1. 旧 U8 搜索壳 `io.legado.app.ui.book.search.SearchActivity`：全仓 grep 零引用；`app/src/main/java/io/legado/app/ui/book/search/` 目录下仅剩 `SearchScope.kt`。既有测试 `app/src/test/java/io/legado/app/ui/main/search/SearchContractTest.kt:166-178` 锁定旧包名/旧壳绑定/旧适配器零残留。
2. Manifest 注册：`app/src/main/AndroidManifest.xml:295-300`，`InkSearchActivity` `exported="false"`，无 LAUNCHER、无 deep-link intent-filter。
3. 快捷方式：`app/src/main/java/io/legado/app/api/ShortCuts.kt:21` 仅建 MainActivity 书架快捷方式，无搜索快捷方式。
4. 主壳底栏无搜索位（见判据 1）；发现页头部独立搜索入口已下线：`app/src/main/java/io/legado/app/ui/main/explore/ExploreInkFragment.kt:45` `binding.tvSearch.visibility = View.GONE`。

### 2.2 未清掉的生产可达路径（FAIL 项）

穷举 `InkSearchActivity.start(...)` 全部调用点，按可达性分类：

1. **主壳内生产可达（阻塞）**：`ExploreInkFragment.kt:31` 典籍卡点击 `InkSearchActivity.start(ctx, name)`；`ExploreInkFragment.kt:31` 专题 Sheet 行点击 `InkSearchActivity.start(ctx, name)`。`ExploreInkFragment` 由适配器实例化（`MainActivity.kt:500`），是「发现」Tab 现役页面，用户点卡片即进独立搜索 Activity。
2. **外部分享可达（阻塞）**：`app/src/main/java/io/legado/app/receiver/SharedReceiverActivity.kt:57` 非链接文本走 `InkSearchActivity.start(this, text)`；该 Activity 在 Manifest `:442-451` `exported="true"`，带 `PROCESS_TEXT` 与 `SEND` text/plain intent-filter，外部 App 选词/分享可直达。
3. **App 内页面生产可达**：`app/src/main/java/io/legado/app/ui/about/ReadRecordActivity.kt:150`（阅读记录缺书回退检索）；`app/src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt:936`（书源标签检索）、`:1126` 与 `:1140`（作者点按/长按）、`:1155` 与 `:1169`（书名点按/长按）；`app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt:197`；`app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:142`；`app/src/main/java/io/legado/app/ui/rss/read/RssJsExtensions.kt:77`、`:83`（RSS 阅读页 JS 桥）。
4. **死 Fragment 内不可达（注明，不计 FAIL）**：`app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt:328`；该 Fragment 未被适配器实例化（适配器仅 `MainActivity.kt:499-501` 三分支），同组 `MyFragment / RssFragment` 亦为死壳，属报告 §6「旧壳清理」交接项。

判定说明：派单判据为「有任何生产可达路径判 FAIL」。第 1 类位于主壳现役 Tab，直接对冲规格「搜索做书架页下拉浮层，禁独立搜索页」（`docs/问题清单.md:157`；锚点 `docs/锚点/D25-导航.md:30`）；第 2、3 类虽被报告 §6 定性为「检索流归 D1~D7」跨域调用，且部分场景是把 Activity 当结果页用书源/作者/书名检索，但 DoD 字面口径「无独立搜索 Activity 入口」当前未达成。反思 §3 已自陈「若 F-13 口径要求主壳零跳转，这两处仍属漏网」。

## 判据 3：书架页下拉浮层实现 —— 有条件通过（级别 A/B，附 1 条缺陷）

1. 浮层状态机：`app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfSearch.kt:15` 枚举 `CLOSED / OPENED`；`open` 保留关键词（39），`close` 清空关键词（42），`updateQuery` 落词（45）。
2. 打开触发：现役两条路径均为页眉放大镜点按——`BookshelfScreen.kt:217`（离线架）、`:231`（在线架）`onSearch = { searchState = ShelfSearch.open(searchState) }`；放大镜按钮 `:377-378`，手势为 `detectTapGestures(onTap)`（`:397-398`）。**未实现「滚动到顶下拉」或「顶部下滑」打开手势**：书架 `LazyVerticalGrid`（`:268`）未挂任何垂直拖拽打开手势；全文件 `detectVerticalDragGestures` 仅出现在浮层自身收起逻辑（`:819-828`）。规格「下拉浮层」按自顶而下落下的顶 Sheet 形态落地，打开方式为按钮点按；若规格要求下拉手势呼出，属缺口（B 级解读待裁）。
3. 收起路径：遮罩点按 `:809-811`；面板下滑超过 80dp 收起 `:819-827`；选中结果后收起 `:244-247`；清空词按钮 `:851-854`。
4. 状态恢复：`close()` 置 `CLOSED` 并清空 query（`ShelfSearch.kt:42`）；浮层按 `searchState.isOpen` 条件挂载（`BookshelfScreen.kt:236-249`），收起后从组合树移除；书架滚动状态由独立 `listState` 持有，浮层开合不重置书架滚动位置（A 级结构，C 级运行时未证）。
5. 刷新手势冲突：书架 Compose 层与 `InkShelfFragment` 均无下拉刷新手势（本包 grep 无 `SwipeRefresh / pullRefresh / onRefresh`）；`REFRESHING` 仅由状态机驱动指示器（`BookshelfScreen.kt:301-305`）。当前实现不存在「同一手势两个语义」的冲突；若后续接入下拉刷新，需与浮层下滑收起划区。
6. **缺陷 N2**：结果列容器 `:873-876` 为 `Column(heightIn(max = 260.dp))`，未挂 `verticalScroll`（全文件无 `verticalScroll` 引用）；每行 56dp（`:902`），命中超过约 4 条时多出条目被裁切且无法滚动查看。

## 判据 4：搜索能力不降级 —— 有条件通过（级别 A，能力缺口已挂账）

1. 浮层内现存能力：输入框 `:836-850`（`ImeAction.Search`）；本地书名/作者/正文片段三路命中，走纯函数 `searchBooks`（`ShelfSearch.kt:67-87`）；命中片段朱砂高亮 `:916 / :925`；结果行书名/作者/进度/「本地」标签 `:894-945`；空词引导态与无命中占位 `:870-872`。
2. 对照旧独立页，**未搬入浮层的能力**：搜索历史（旧 `SearchHistory / SearchHistoryPanel`，bookshelf 包内 grep 无任何引用）；热门词（旧 `SearchHotResolver / SearchHotBinder`，浮层未接）；联网书源结果；翻页加载更多。
3. 翻页现状对照 D1：D1 到底翻页接线落在旧 Activity 栈——`InkSearchViewModel.loadMore()`、`SearchResultList` 到底回调与底部三态脚注（见 `docs/验收-复核-D1.md` 条款 1）；书架浮层不消费该 ViewModel，结果仅来自本地书架投影（`ShelfSearch.kt:94-97`），两套检索栈当前并行。
4. 口径核对：锚点 `docs/锚点/D25-导航.md:10`、`docs/问题清单.md:157` 注明「搜索结果查询逻辑仍归 D1~D7，本行不重写」；反思 §3 明确「浮层只过滤本地书架，未接书源联网检索；历史未做，属有意挂账」。故能力缺口在 D25 口径下属交接项，不按本单 FAIL；但记二阶风险：D1~D7 检索流落地后，浮层需回接到同一检索状态，否则旧 Activity 栈长期残留（与判据 2 互相咬合）。

## 判据 5：空态与边界 —— PASS（级别 A，附缺陷 N2）

1. 空关键词：`ShelfSearch.filter` 空词/纯空白返回全量（`ShelfSearch.kt:15-53`），浮层落引导文案「输入书名或作者，查找本地书卷」（`BookshelfScreen.kt:871`）。
2. 无命中：返回空列表，浮层落「未找到相关书卷，换个词试试」，命中词为空时回落纯函数 hint（`BookshelfScreen.kt:872`；`ShelfSearch.kt:82-85`）；空书架 + 关键词不崩，单测锁定 `ShelfSearchD25Test.kt:104-107`。
3. 输入清空：叉按钮回调 `onQueryChange("")`（`BookshelfScreen.kt:853`），回到引导态。
4. 断网：离线态 `OfflineShelf`（`:606-622`）仍透传 `onSearch`，浮层只过滤本地缓存、不发网络请求（`ShelfSearch.kt:11` 注释与实现一致）。
5. 边界未证（C 级）：IME 弹起遮挡、横竖屏切换、小屏宽度、全角空格/大小写混合命中；纯函数层有单测覆盖（`ShelfSearchContractTest.kt`、`ShelfSearchOutcomeTest.kt`），画面层未真机走查（报告 §7 同记）。

## 判据 6：测试断言 —— 有条件通过（级别 A，缺两条必补断言）

1. 已有断言：
   - 菜单恰好 3 Tab、id 与标题、无 `menu_paint`：`ShelfSearchD25Test.kt:40-61`。
   - 浮层开合状态机：展开保留词、收起清空词、重开不带旧词：`:64-83`。
   - 本地过滤空态/大小写/空白兜底：`:86-108`。
   - 设置 Tab 幂等、不起 Activity、无 `menu_paint` 分支：`:111-123`（源码文本回证）。
   - 导航图标恰好 3 枚、旧 `ic_bottom_*` 清零、线宽与配色口径：`:127-152`。
   - 检索纯函数三路命中/排序/片段/去重投影：`ShelfSearchContractTest.kt:25-144`、`ShelfSearchOutcomeTest.kt:33-131`。
2. **必补断言 a**：「旧独立搜索入口零可达」当前无锁定。全仓测试未枚举 `InkSearchActivity.start` 调用点；`SearchContractTest.kt:86-153` 仅断言 Manifest 注册新 Activity、旧包名移除，`:166-178` 仅禁旧壳绑定引用。建议补一条源码扫描测试：主壳包（`ui/main` 现役 Fragment/Activity）内不得出现 `InkSearchActivity.start`；跨域白名单显式列名并标注归属 D1~D7。
3. **必补断言 b**：浮层开合目前只在纯 JVM 状态机层断言，无 Compose 层可见性/遮罩存在性断言；状态机 `open/close` 与画面挂载（`BookshelfScreen.kt:236`）之间缺一道接线锁。
4. 测试卫生：D25 测试文件无 `@Ignore`、无 `assertTrue(true)` 式空断言（grep 零命中）。

## 判据 7：二阶 / 三阶影响 —— 有条件通过（级别 A/B）

1. 书内搜索不共用旧搜索页：书内检索走 `ui/book/searchContent/SearchContentActivity`（`ReadBookActivity.kt:142 / :266`，`SearchMenu.kt:18` 用 `SearchResult` 本包类型）；词典走 `DictDialog`（`ReadBookActivity.kt:1789-1790`）。两者均不路由 `InkSearchActivity`，本单改动未波及其链路。PASS。
2. 主题/夜间模式：书架域配色全部取 `ShelfTokens` 固定浅色 hex（`ShelfTokens.kt:18-37`），bookshelf 包内无夜间/暗色分支；主壳仅电子墨水分支（`MainActivity.kt:240`）。浮层遮罩/面板/文字在暗色主题下的实际观感未验证（C 级）；D20 阅读暗色/纯黑主题与主壳配色是否同源，本单未串到。
3. 无障碍语义：`BookshelfScreen.kt` 全文件无 `semantics / contentDescription`；放大镜为自绘 Canvas（`:402-426`）无标签；输入框无 semantics label；浮层无返回语义绑定。报告 §5 已挂账 D24，本单记为缺口。
4. 首页偏好残留：`app/src/main/res/values/arrays.xml:160-174` 首页选项仍含 `rss` / `my` 两项；`MainActivity.kt:444-453` `upHomePage()` 无 `"rss"` 分支（死选项），`"my"` 映射到设置槽。跨域 C1 配置项，报告 §6 已交接。
5. 皮肤槽位：`menuIdToSlot` 键保持 `bookshelf / home / settings`（`MainActivity.kt:103-107`），第三槽改名不影响旧皮肤包取图（B 级推断，换肤路径未实机）。
6. 返回栈：设置 Tab 只切 ViewPager 槽位，不 `startActivity`，重复点击不重复入栈（`MainActivity.kt:205-211`）；死 Fragment 内行级 `startActivity` 栈不变。

## 判据 8：未披露项与提交夹带核对

1. 提交穷举：`git log --oneline --grep=D25` → `1de04d5 feat(D25): 底部导航收 3 Tab + 书架下拉搜索浮层 + ic_tab_* 三枚导航图标…`、`7b1bd1b docs: 15:00 后口径落盘…`；`git log --oneline --grep=导航` → `1de04d5`、`87f56e0 feat(globe): 我的页「舆图」入口…`、`c309d81 界面重写第6批…`。
2. `git show --stat 1de04d5`：38 文件。D25 本体文件为 `ThemeBottomNavigationVIew.kt`、`MainActivity.kt`、`BookshelfScreen.kt`、`ShelfSearch.kt`、`ExploreInkFragment.kt`、`menu/main_bnv.xml`、`ShelfSearchD25Test.kt` 与 3 枚 `ic_tab_*` selector/矢量。
3. **夹带（纯资源，无逻辑夹带）**：同提交并入约 28 枚与 D25 无关的图标资源——`ic_annotate / ic_bookmark / ic_color_*`(6) / `ic_music / ic_paint*`(13) / `ic_popup_*`(5) / `ic_read_aloud / ic_theme_swatch / ic_toggle_layout`，归属图标批次 D22/D28。建议台账备注归属，避免后续图标单回溯时误挂 D25。
4. HEAD 相对 D25 基线的后续演进（本验收按 HEAD 现状核，非 `1de04d5` 快照）：`f02b6be`（S-13 检索纯函数）、`c37000c`（纯函数接入浮层状态机）、`bfb0e22`（高亮改消费命中区间 + outcome/params 单测）、`964b844 / eda8277`（S-10 参数接线）、`5e9e70f`（B18 六态收敛）、`95639ce`（B31 清孤儿 ViewModel）。浮层现行为上述迭代替合后的状态。
5. 规格口径冲突留档：D25 行③与索引文字要求「设置齿轮」（`docs/问题清单.md:157`；`docs/锚点/D25-导航.md:23`），落地矢量为「两竖线 + 顶端圆点」（`ic_tab_settings_e.xml:1-12`，selector 注释 `ic_tab_settings.xml:2` 明示非齿轮）；锚点兜底 F-73 亦记「设置齿轮是否另入口未决」。图标几何与文字口径冲突，需规格侧裁决，本单不擅改。

## 新发现缺陷（按阻塞优先级）

- N1（阻塞）：全仓 `InkSearchActivity.start` 共 14 处调用点，其中 13 处生产可达（主壳发现页 2：`ExploreInkFragment.kt:100`、`:210`；外部分享 1：`SharedReceiverActivity.kt:57`；书详情 5、阅读记录 1、书源编辑 1、书源管理 1、RSS JS 桥 2），另 1 处 `ExploreFragment.kt:328` 在死 Fragment 内不可达；待 D1~D7 收口或改道浮层。
- N2（应修）：浮层结果列不可滚动，超约 4 条命中被裁切——`BookshelfScreen.kt:873-876` 缺 `verticalScroll`（或改 `LazyColumn` 限高）。
- N3（风险挂账）：浮层本地检索与旧 Activity 联网检索/历史/热门/翻页两套并行；D1~D7 落地后必须回接浮层，否则判据 2 无法闭环。
- N4（跨域）：`arrays.xml:160-174` 首页选项残留 `rss` / `my` 死项，`"rss"` 在 `upHomePage` 无分支。
- N5（命名债）：`menu_my_config` id 名实不符，改名需同步 `MainActivity.kt:103-107` 皮肤槽位键。
- N6（口径待裁）：设置图标非齿轮，与 D25 行③文字口径冲突。
- N7（无障碍缺口）：浮层/放大镜/输入框无 semantics 标签，挂账 D24。
- N8（未验证风险）：浮层配色固定浅色，夜间/暗色模式表现未验证。

## 未验证项（本单能力边界）

- 未编译、未跑单测、未真机（沙箱禁 gradle/kotlinc，沿用报告 §7 约束）；全部结论为静态复核，CI 绿红与 APK 状态不在本单判定范围。
- Compose 浮层手势、IME 弹起、横竖屏/小屏切换、换肤实机路径未走查。
- 跨域调用方是否引用已删图标/旧布局，未经编译期确认。
- 夜间/暗色主题下浮层观感未实测。
- 旧死 Fragment（`ExploreFragment / MyFragment / RssFragment`）及其菜单 `main_explore / main_my / main_rss`、旧布局未删，属报告 §6 旧壳清理交接项，本单不代判。

## 真跑过的命令与结论标记

- `git rev-parse HEAD` → `e381a8d1706c675876adbe1f9c4b18dc690245b5`，与派单 HEAD 一致。【A】
- `git status --short` → 仅 `docs/问题清单.md` 在飞改动 + 未跟踪 `docs/修复-沙盘地形起伏.md`；`app/` 工作树干净，脏文件未计入本单。【A】
- `grep -c "<item>" main_bnv.xml`（读文件直数）→ 3。【A】
- `grep -rn "InkSearchActivity" app/src/main` → 12 处启动/引用点，逐条核对可达性（见判据 2.2）。【A】
- `grep -rn "BottomNavigationView" app/src/main` → 4 处，主壳仅一个底栏。【A】
- `grep -rn "verticalScroll" BookshelfScreen.kt` → 空，结果列不可滚动（N2）。【A】
- `grep -rn "SearchHistory\|SearchHot" ui/main/bookshelf` → 空，浮层未接历史/热门。【A】
- `git log --oneline --grep=D25` / `--grep=导航`、`git show --stat 1de04d5` → 夹带资源清单见判据 8。【A】
- 结论标记：判据 1 PASS(A)；判据 2 FAIL(A)；判据 3 有条件通过(A/B)；判据 4 有条件通过(A)；判据 5 PASS(A)；判据 6 有条件通过(A)；判据 7 有条件通过(A/B)；判据 8 夹带已记录(A)。
