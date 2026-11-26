> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 1 条（见 回执补录-批1-搜索书架 §三）
> 回执缺失（规则上线前交付，留档）

# 验收 · 接线包 C · 搜索页接管

> 范围：Legado 搜索入口由旧 XML 壳 `ui/book/search/SearchActivity` 切换为 Compose 自绘壳
> `ui/main/search/InkSearchActivity`；旧 UI 层拆除，检索仓储与既有远程检索栈保留。
> 规格依据：`docs/落文-规格切片/切片-13-搜索页与字体.md`、`docs/落文-模块契约索引.md` 1.5 节、
> `docs/落文-规格切片/切片-15-全局动效铁律.md`。
>
> 编译状态：**未本地编译**（任务红线：本地禁 gradle/kotlinc）。以下结论均为静态人工核对 +
> 纯 JVM 单测源码核对；APK 编译、Compose 真机渲染、远程多源检索链路均需 CI / 真机验证，
> 见文末「未验证项」。

---

## 1. 结论

- 全局搜索唯一入口为 `InkSearchActivity`（ComponentActivity 壳 + Compose 承载 `SearchScreen`，
  编排归 `InkSearchViewModel`）。
- 旧 `ui/book/search/` 的 Activity / ViewModel / 三个 Adapter / 范围弹窗 UI 壳已删除；
  仅保留被远程检索栈、WebSocket、新入口共用的范围仓储类 `SearchScope.kt`。
- 全部现存全局搜索调用点已切到新入口；三类传参（纯关键词 / 书源+关键词 / 关键词+范围串）
  逐点核对，语义与旧入口一致（见第 4 节）。
- 书页内正文检索（`SearchContentActivity` 及其回调 `openSearchActivity`）属于另一模块，
  未改动、未误切。
- 纯 JVM 单测已补齐：映射合并、命令闸门、接线契约；旧壳相关单测已迁移或拆除。

## 2. 文件清单（绝对路径）

仓库根：`/home/user/Doubao/chats/38454122521460994/legado`

### 2.1 新搜索子域（`app/src/main/java/io/legado/app/ui/main/search/`）

| 文件 | 状态 | 说明 |
| --- | --- | --- |
| `InkSearchActivity.kt` | 接完（沿用半成品壳，补齐核对） | 唯一入口壳；三个入口重载；跳转书籍详情 |
| `InkSearchViewModel.kt` | 重写收口 | 检索编排、历史 / 本地书架仓储接线、命令闸门、离线 / 失败兜底、超长词截断 |
| `SearchMappers.kt` | 沿用（半成品壳，核对通过） | `SearchBook` / `Book` → `SearchResultItem`；本地+远程去重合并 |
| `SearchScreen.kt` | 既有 Compose 屏 | 页面骨架、状态分发接线 |
| `SearchInputBar.kt` | 既有 | 居中输入框、无搜索按钮、下方云灰 12sp 引导句 |
| `SearchResultList.kt` | 既有 | 纯文本结果列表，无封面图 |
| `SearchHistoryPanel.kt` | 既有 | 历史词条面板 |
| `SearchModels.kt` | 既有 | UI 模型 / 加载态 |
| `SearchReducer.kt` | 既有 | 纯状态归约 |
| `SearchMatcher.kt` | 既有 | 归一化 / 匹配 |
| `SearchTokens.kt` | 既有，本次微调 | 颜色 / 动效 token；`QUERY_MAX_LEN` 截断上限；`DURATION_CARD` 对齐切片-15 的 350ms |
| `SearchMotion.kt` | 既有 | 渐显 / 手势动效 |
| `SearchFallback.kt` | 既有 | 空 / 错 / 离线 / 加载兜底态 |
| `SearchHistory.kt` | 既有 | 历史清洗 |
| `SearchHighlight.kt` | 既有 | 朱砂高亮分段 |

### 2.2 改动的调用点（`app/src/main/java/`）

| 文件（绝对路径前缀 `…/io/legado/app/`） | 改动 |
| --- | --- |
| `receiver/SharedReceiverActivity.kt` | 外部分享文本 → 新入口（纯关键词） |
| `ui/about/ReadRecordActivity.kt` | 阅读记录条目书名 → 新入口（纯关键词） |
| `ui/book/info/BookInfoActivity.kt` | 书源+标签、作者、书名 5 处 → 新入口 |
| `ui/book/source/edit/BookSourceEditActivity.kt` | 书源编辑测试 → 新入口（书源重载，关键词空） |
| `ui/book/source/manage/BookSourceActivity.kt` | 书源管理搜索 → 新入口（`BookSourcePart` 重载）；顺带移除未使用的 `SearchScope` import |
| `ui/main/explore/ExploreFragment.kt` | 发现页书源 → 新入口（`BookSourcePart` 重载） |
| `ui/main/explore/ExploreInkFragment.kt` | 发现 Ink 页 3 处（空词 / 词条名 ×2）→ 新入口 |
| `ui/rss/read/RssJsExtensions.kt` | JS 桥两个检索方法 → 新入口（关键词+范围串；`BookSource`+关键词） |

### 2.3 改动的资源 / 清单

| 文件 | 改动 |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | 删除旧 `ui.book.search.SearchActivity` 条目；注册 `.ui.main.search.InkSearchActivity`（`AppTheme.Light`、`adjustResize`、`exported=false`、standard） |
| `app/src/main/res/values/ids.xml` | 迁入 `menu_show_read_record`（原仅在已删的 `menu/book_search.xml` 声明，`ExploreShowActivity` 仍编程式引用，避免 R.id 断引用） |
| `app/src/main/res/layout/activity_explore_show.xml` | `tools:context` 由已删的旧 `SearchActivity` 改为 `ExploreShowActivity`（仅设计期引用） |

### 2.4 单测 / 仪表测试

| 文件 | 状态 |
| --- | --- |
| `app/src/test/java/io/legado/app/ui/main/search/SearchMappersTest.kt` | 新增：实体映射兜底、合并去重、脏标题剔除、空入参安全 |
| `app/src/test/java/io/legado/app/ui/main/search/SearchCommandGateTest.kt` | 由旧包迁移至新包：命令作废 / 运行中命令放行并发闸门 |
| `app/src/test/java/io/legado/app/ui/main/search/SearchContractTest.kt` | 扩充：新三文件纳入禁 hex / Material 扫描；Manifest 注册与旧条目移除；三重载与 extra 键；旧壳绑定 / Adapter 残留扫描；截断 token；渐显曲线 / 350ms / overlay token 契约 |
| `app/src/test/java/io/legado/app/BundlePayloadContractTest.kt` | 清单移除已删旧 `SearchActivity.kt` 条目 |
| `app/src/test/java/io/legado/app/base/PredictiveBackTest.kt` | 路径由旧 `SearchActivity.kt` 换为 `InkSearchActivity.kt` |
| `app/src/test/java/io/legado/app/ui/DialogViewLifecycleContractTest.kt` | 移除对已删 `SearchScopeDialog.kt` 的段读取断言，保留封面规则弹窗断言 |
| `app/src/androidTest/java/io/legado/app/ui/book/explore/ExploreCategoriesTest.kt` | 剥离依赖旧搜索壳的仪表半段（搜索 Activity / Adapter / 绑定扩展），保留发现页阅读记录指示断言；测试更名为 `returningToDiscoverRefreshesSharedReadRecordIndicators` |
| `app/src/test/java/io/legado/app/ui/book/search/SearchResultLayoutTest.kt` | 保留：只读共享布局 `item_search.xml`，不引用已删类 |

## 3. 删除清单

### 3.1 Kotlin 主源码（`app/src/main/java/io/legado/app/ui/book/search/`）

- `SearchActivity.kt`（旧 Activity 壳，VMBaseActivity + `ActivityBookSearchBinding`）
- `SearchViewModel.kt`（旧 UI 壳 ViewModel；其仓储访问为 DAO / 实体直连，仓储本体在 `data/` 层未动；在架 / 阅读记录角标逻辑随旧 UI 一并移除）
- `BookAdapter.kt`（旧在架书条目 Adapter，注意与 `ui/book/manage/BookAdapter.kt` 不同包、不同类）
- `SearchAdapter.kt`（旧远程结果 Adapter）
- `HistoryKeyAdapter.kt`（旧历史词条 Adapter）
- `SearchScopeDialog.kt`（旧范围选择弹窗 UI 壳）

**保留**：`SearchScope.kt`（范围仓储 / 解析模型，被 `model/webBook/SearchModel.kt`、
`web/socket/BookSearchWebSocket.kt`、新 `InkSearchActivity` / `InkSearchViewModel` 共用，不得断引用）。

### 3.2 资源

- `app/src/main/res/layout/activity_book_search.xml`（旧搜索页布局，仅旧 Activity 绑定）
- `app/src/main/res/layout/dialog_search_scope.xml`（旧范围弹窗，仅旧 Dialog 绑定）
- `app/src/main/res/menu/book_search.xml`（旧搜索页选项菜单）
- `app/src/main/res/menu/book_search_scope.xml`（旧范围菜单）

### 3.3 单测

- `app/src/test/java/io/legado/app/ui/book/search/SearchCommandGateTest.kt`（迁移至新包，旧路径删除）
- `app/src/test/java/io/legado/app/ui/book/search/SearchReadRecordContractTest.kt`（断言旧 VM / 旧 Activity / 旧菜单，随壳删除）
- `app/src/test/java/io/legado/app/ui/book/search/SearchResultFilterTest.kt`（断言旧私有 `filterSearchResults` 排除词过滤，随壳删除）

### 3.4 保留但未删的共享资源（核对过仍有消费者）

- `res/layout/item_search.xml`：`ui/book/explore/ExploreShowAdapter.kt` 仍用 `ItemSearchBinding`。
- `res/layout/item_search_list.xml`：书页正文检索 `SearchContentAdapter` 使用。
- `res/layout/view_search.xml`：目录规则 / 正则弹窗复用。
- `res/layout/view_search_menu.xml`、`res/menu/content_search.xml`、`res/menu/search_view.xml`：正文检索 / 配置页使用。

## 4. 传参逐点核对表

新入口三个重载（`InkSearchActivity` companion）：

1. `start(context, key: String?, searchScope: String? = null)` — 关键词 + 可选范围串；
2. `start(context, source: BookSource, key: String? = null)` — 单书源，范围串由 `SearchScope(source).toString()` 生成；
3. `start(context, source: BookSourcePart, key: String? = null)` — 同上，书源分部实体。

Intent extra 键沿用旧契约：`key`、`searchScope`；详情页跳转键 `name` / `author` / `bookUrl` 与旧页一致。

| # | 调用点（文件:行） | 旧调用形态 | 入参类别 | 新调用 | 语义核对 |
| --- | --- | --- | --- | --- | --- |
| 1 | `receiver/SharedReceiverActivity.kt:57` | `start(this, text)` | 关键词（分享文本） | `InkSearchActivity.start(this, text)` | key=text，scope=null → 全部启用书源；✓ 一致 |
| 2 | `ui/about/ReadRecordActivity.kt:370` | `start(act, item.bookName)` | 关键词（书名） | `InkSearchActivity.start(act, item.bookName)` | key=书名；✓ 一致 |
| 3 | `ui/book/info/BookInfoActivity.kt:936` | `start(act, source, kind)` | 书源 + 关键词（标签） | `InkSearchActivity.start(act, source, kind)` | 重载 2，source=`BookSource`，key=kind；✓ 一致 |
| 4 | `ui/book/info/BookInfoActivity.kt:1126` | `start(act, book.author)` | 关键词（作者） | `InkSearchActivity.start(act, book.author)` | ✓ 一致 |
| 5 | `ui/book/info/BookInfoActivity.kt:1140` | `start(act, book.author)` | 关键词（作者） | 同上 | ✓ 一致 |
| 6 | `ui/book/info/BookInfoActivity.kt:1155` | `start(act, book.name)` | 关键词（书名） | `InkSearchActivity.start(act, book.name)` | ✓ 一致 |
| 7 | `ui/book/info/BookInfoActivity.kt:1169` | `start(act, book.name)` | 关键词（书名） | 同上 | ✓ 一致 |
| 8 | `ui/book/source/edit/BookSourceEditActivity.kt:313` | `start(this, source)` | 书源，关键词空 | `InkSearchActivity.start(this, source)` | 重载 2，key=null → 进入该源空词历史面板；✓ 一致 |
| 9 | `ui/book/source/manage/BookSourceActivity.kt:869` | `start(this, bookSource)` | 书源分部 | `InkSearchActivity.start(this, bookSource)` | 重载 3，`BookSourcePart`；✓ 一致 |
| 10 | `ui/main/explore/ExploreFragment.kt:328` | `start(ctx, bookSource)` | 书源分部 | `InkSearchActivity.start(ctx, bookSource)` | 重载 3；✓ 一致 |
| 11 | `ui/main/explore/ExploreInkFragment.kt:41` | `start(ctx, "")` | 空关键词 | `InkSearchActivity.start(ctx, "")` | 归一化为空 → 历史空闲面板；✓ 一致 |
| 12 | `ui/main/explore/ExploreInkFragment.kt:86` | `start(ctx, name)` | 关键词 | `InkSearchActivity.start(ctx, name)` | ✓ 一致 |
| 13 | `ui/main/explore/ExploreInkFragment.kt:196` | `start(ctx, name)` | 关键词 | 同上 | ✓ 一致 |
| 14 | `ui/rss/read/RssJsExtensions.kt:77` | `start(it, key, searchScope)` | 关键词 + 范围串 | `InkSearchActivity.start(it, key, searchScope)` | 重载 1，scope 透传；✓ 一致 |
| 15 | `ui/rss/read/RssJsExtensions.kt:83` | `start(it, source, key)` | 书源 + 关键词 | `InkSearchActivity.start(it, source, key)` | 重载 2，`BookSource`；✓ 一致 |

### 4.1 不属于本次切换的同名回调（已核对，未改动）

`ReadBookActivity.openSearchActivity`、`ReadMenu` / `SearchMenu` / `ReadView` 接口中的
`openSearchActivity(searchWord)` 是**书页内正文检索**回调，内部启动
`ui/book/searchContent/SearchContentActivity`（见 `ReadBookActivity.kt:2172` `searchContentActivity.launch`），
与全局书源搜索是两条链路，本次未切、未改。

### 4.2 初查时出现、实际已不在工作树的旧书架碎片

初轮检索曾命中 `BaseBookshelfFragment` / `BookshelfFragment1` / `BookshelfFragment2`，
复核时这些旧 XML 书架 Fragment 已不存在（书架 Compose 重写进行中），工作树内无残留调用；
新书架屏目前也尚未挂搜索入口（见第 7 节跨模块遗留）。

## 5. grep 证据（验收判据）

执行目录：仓库根。命令与结果：

### 5.1 全局搜索入口只指向新入口

```
grep -rn "SearchActivity" app/src/main --include=*.kt
```

结果中 `SearchActivity` 子串仅出现在：
- `…/ui/main/search/InkSearchActivity.kt`（新入口类名 / 泛型）；
- 8 个外部调用点文件共 15 处 `InkSearchActivity.start(...)`；
- `ReadBookActivity.kt` / `ReadMenu.kt` / `SearchMenu.kt` / `ReadView.kt` 的
  `openSearchActivity` 方法名（书页正文检索，见 4.1）。

旧包 `io.legado.app.ui.book.search.SearchActivity` 在主源码中 **0 命中**。

### 5.2 旧绑定 / 旧 Adapter 清零

```
grep -rn "ActivityBookSearchBinding\|HistoryKeyAdapter" app/src/main --include=*.kt
# exit=1（无命中）
```

旧目录现存文件：

```
ls app/src/main/java/io/legado/app/ui/book/search/
# SearchScope.kt
```

### 5.3 禁色值 / Material 默认 / 线性缓动 / 菊花

```
grep -rnE "0x[0-9A-Fa-f]{6,8}" app/src/main/java/io/legado/app/ui/main/search/ | grep -v SearchTokens.kt
# exit=1（hex 仅集中在 SearchTokens.kt 颜色表）

grep -rnE "androidx.compose.material|rememberRipple|LinearEasing|LinearProgressIndicator|CircularProgressIndicator" \
  app/src/main/java/io/legado/app/ui/main/search/
# exit=1（Material / 线性指示符 0 命中；"菊花" 仅出现在注释文案中）
```

### 5.4 动效契约（切片-15 / 索引 1.5）

- 卡片浮入曲线：`SearchTokens.CARD_REVEAL = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)`；
- 卡片浮入时长：`DURATION_CARD = 350L`（本次由 250ms 对齐切片-15「卡片浮入 stagger 350ms each」）；
- 页面转场：`DURATION_PAGE = 350L`；
- 遮罩色：`BG_OVERLAY = Color(0x731A1410)`（bg-overlay 毛玻璃 token 已收口；本页为全屏页，
  无弹层遮罩实例，token 保留供后续 Sheet / 弹窗复用）。

### 5.5 Manifest

```
grep -n "search" app/src/main/AndroidManifest.xml
# .ui.main.search.InkSearchActivity   （新注册）
# .ui.book.searchContent.SearchContentActivity（正文检索，保留）
```

## 6. 规格契约对照（切片-13 / 索引 1.5）

| 契约项 | 落实位置 | 状态 |
| --- | --- | --- |
| 居中输入框，无搜索按钮 | `SearchInputBar.kt`（`BasicTextField` + IME Search 动作，无提交按钮） | 代码在位；真机观感未验证 |
| 输入框下方云灰 12sp 提示句 | `SearchInputBar.kt:109-115`（`fontSize = 12.sp`，云灰 token） | 代码在位 |
| 渐显结果 | `SearchMotion.kt`（stagger 浮入，`CARD_REVEAL` + `DURATION_CARD`） | 代码在位 |
| 朱砂高亮命中词 | `SearchHighlight.kt` / `SearchTokens.TEXT_ACCENT` | 代码在位 |
| 纯文本结果，无图 | `SearchResultList.kt` 无 `Image(` / `AsyncImage`；契约测试断言 | 代码在位 |
| 纸白输入框 + 暖金细边 | `SearchTokens.BG_SURFACE` / `BRAND_GOLD`，`InputCanvas` 自绘 | 代码在位 |
| bg-overlay 毛玻璃 | `SearchTokens.BG_OVERLAY` | token 在位；本页无弹层实例 |
| 空输入兜底 | reducer `Idle` + 历史 / 引导面板；空词不检索、不写历史 | 代码在位 |
| 无结果兜底 | `SearchLoadState.Empty` + 可重试空态 | 代码在位 |
| 无网兜底（仍可搜本地） | VM `runSearch` 离线分支：本地书架检索回填 `Offline` 缓存，可重试 | 代码在位 |
| 历史为空兜底 | 历史空时回落引导句（`HINT_GUIDE`） | 代码在位 |
| 超长词截断 | `SearchTokens.QUERY_MAX_LEN = 50`；VM `capQuery` 在检索 / 入库前截断；高亮层对超长 needle 另有守卫 | 代码在位 |
| 历史增删清空 | VM `removeHistory` / `clearHistory` 接 `searchKeywordDao` | 代码在位 |

## 7. 未验证项（需 CI / 真机）

1. **未编译**：红线禁止本地 gradle / kotlinc；Kotlin 语法、类型解析、R 类生成、Compose 编译
   均未经编译器验证，仅做了静态引用核对。需 CI 执行全量 `clean assembleDebug`（JDK17）确认。
2. **单测未本地执行**：新 / 改单测为源码级核对，需 CI 跑 `testDebugUnitTest` 确认全绿。
3. **Compose 渲染与动效未真机验证**：输入框居中文案位置、三点加载节奏、stagger 渐显、
   朱砂高亮断行、历史 chips 换行等观感需真机截图对照索引 1.5 节。
4. **远程多源检索链路未跑通**：`SearchModel` 并发检索、范围书源解析、结果合并排序的实际
   网络行为未验证；离线判定 `NetworkUtils.isAvailable()` 分支未实测。
5. **详情页跳转参数**：`BookInfoActivity` 对 `name` / `author` / `bookUrl` 缺省组合的
   容错未真机走查。
6. **仪表测试**：`ExploreCategoriesTest` 已剥离搜索半段；搜索侧 Compose 仪表覆盖待补，
   本次未新增 androidTest。
7. **陈旧回调竞态**：旧命令被新命令作废后，迟到的远程成功回调未做 searchId 二次比对
   （与旧实现同级风险；`SearchModel.cancelSearch` 内部收口为主防线）。建议 CI 压测时观察
   快速连续检索是否出现结果回跳。

## 8. 跨模块问题与遗留交接

1. **新 Compose 书架屏缺搜索入口**：`ui/main/bookshelf/InkShelfFragment.kt` /
   `BookshelfScreen.kt` 当前无搜索动作；旧书架 Fragment 的搜索入口随旧 XML 书架一并消失。
   交接书架模块：在书架标题栏搜索动作处接 `InkSearchActivity.start(requireContext())`。
2. **范围切换弹窗未在新页内接线**：旧 `SearchScopeDialog` 提供页内切书源 / 分组；新搜索页
   只接受入口透传的范围，默认全部启用书源。页内范围切换入口待产品确认后补 Compose Sheet。
3. **分页加载更多未接线**：`onSearchFinish(isEmpty, hasMore)` 的 `hasMore` 暂只收口空态，
   列表滚动到底翻页未接；`searchID` 保留用于后续陈旧回调守卫。
4. **热门词列表未接入**：`SearchUiState.hot` 恒为空，面板在历史为空时回落引导句；
   热门词数据源待接。
5. **结果行角标随纯文本列表移除**：在架标记、阅读记录标记、来源数角标（旧
   `BookAdapter` / `SearchAdapter` 行内控件）不再渲染；阅读记录开关配置
   `AppConfig.showSearchReadRecord` 仍被发现页 `ExploreShowActivity` 使用，配置项未删。
6. **排除词过滤菜单移除**：旧 `filterSearchResults` 排除词对话框随壳删除；`PreferKey`
   下精度搜索开关仍被 `SearchModel` 内部使用，配置未动。
7. **`SearchScope.kt` 暂留旧包**：被检索栈 / WebSocket / 新入口三方共用，本次不搬迁包名，
   避免跨包大改；后续仓储层整理时再迁。
8. **残留未引用字符串**：`search_result_filter`、`search_result_filter_hint`、
   `sure_clear_search_history`、`search_scope` 在主源码已无引用；暂留 `strings.xml`
   未删（避免翻译文件联动漂移），可在下一轮资源清理中统一处理。
9. **`item_search.xml` 共享行布局**：旧搜索 Adapter 删除后，该布局仅服务发现页
   `ExploreShowAdapter`；`SearchResultLayoutTest` 仍按旧行约束断言该布局，属共享布局契约，
   未迁移到发现页测试包。
10. **未触碰 `ui/main/MainActivity.kt`**：本任务红线要求不改；核对其无搜索引用，无需改动。
