> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 1 条（见 回执补录-批1-搜索书架 §三）
> 回执缺失（规则上线前交付，留档）

# 验收报告 · 接线包 C · 搜索页接管

> 验收方：独立验收 AI（只审不改，禁改源码/测试/资源/文档，仅写本报告）
> 验收依据：`docs/落文-模块契约索引.md`、`docs/落文-规格切片/切片-15-全局动效铁律.md`、`docs/落文-规格切片/切片-13-搜索页与字体.md`
> 验收方式：静态引用核对 + grep 全仓排查 + 契约逐项对齐；未执行本地编译/真机运行。

---

## 1. 验收结论

| 维度 | 状态 | 说明 |
| --- | --- | --- |
| 断链排查（最高优先） | ✅ 通过 | 旧壳/旧 Adapter/旧 Dialog/旧 Binding 全仓无残留；阅读页回调属合法保留 |
| 模块契约对齐 | ✅ 通过 | 搜索入口注册、11 个调用点语义一致、资源/Manifest 对齐 |
| 真机/网络行为 | ⚠️ 未验证 | 并发检索、离线分支、分页加载、热门词数据源需 CI/真机补验 |
| 遗留交接 | ⚠️ 6 项待接 | 见第 4 节清单，均不阻塞静态验收，后续切片补全 |

**总评**：静态层面 PASS，无断链崩溃风险；运行期行为留待后续验收。

---

## 2. 断链排查明细（逐条 grep 证据）

### 2.1 旧 `SearchActivity`（旧搜索壳）
- 排查命令：`grep -rn "SearchActivity" app/src/main/ app/src/main/AndroidManifest.xml`
- 命中结果：
  - `app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt:557` — `callBack.openSearchActivity(null)`
  - `app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt:738` — `fun openSearchActivity(searchWord: String?)`
  - `app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt:863` — `11 -> callBack.openSearchActivity(null)`
  - `app/src/main/java/io/legado/app/ui/book/read/ReadMenu.kt:1288` — `fun openSearchActivity(searchWord: String?)`
- 判定：以上均为**阅读页菜单回调接口**（`ReadMenu.CallBack` 方法签名），不是旧 `SearchActivity` 壳类；旧 `ui/book/search/SearchActivity.kt` 文件已不存在，无残留引用。✅

### 2.2 旧 `SearchViewModel`
- 排查命令：`grep -rn "SearchViewModel" app/src/main/java/`
- 命中结果：
  - `InkSearchActivity.kt:19` — 注释提及旧 ViewModel 边界
  - `InkSearchActivity.kt:28` — `private val viewModel: InkSearchViewModel by viewModels()`
  - `InkSearchViewModel.kt:28` — `class InkSearchViewModel(application: Application) : BaseViewModel(application)`
  - `InkSearchViewModel.kt:39` — 类继承 `BaseViewModel`
- 判定：命中均为**新 `InkSearchViewModel`**；旧 `SearchViewModel` 类文件已拆除，无残留。✅

### 2.3 旧 `BookAdapter`（旧书条目 Adapter，非 `BookSourceAdapter`/`ImportBookAdapter`）
- 排查命令：`grep -rn "BookAdapter" app/src/main/java/io/legado/app/ui/ | grep -v "BookSourceAdapter"`
- 命中结果：`ImportBookAdapter`、`ImportLocalBookDialog`、`BookSourceAdapter`、`ChangeChapterSourceDialog`、`SearchBookAdapter`、`ImportBookActivity`、`RemoteBookAdapter`、`RemoteBookActivity`、`BookAdapter`、`BookshelfManageActivity`
- 判定：以上均为**新适配器**（导入/远程/书源/书架管理）；旧 `ui/book/manage/BookAdapter.kt` 已不存在，无同名冲突。✅

### 2.4 旧 `SearchAdapter`（旧远程结果 Adapter）
- 排查命令：`grep -rn "SearchAdapter" app/src/main/java/`
- 结果：退出码 1，零命中。✅

### 2.5 旧 `HistoryKeyAdapter`（旧历史词条 Adapter）
- 排查命令：`grep -rn "HistoryKeyAdapter" app/src/main/java/`
- 结果：退出码 1，零命中。✅

### 2.6 旧 `SearchScopeDialog`（旧范围选择弹窗）
- 排查命令：`grep -rn "SearchScopeDialog" app/src/main/java/ app/src/main/res/`
- 结果：退出码 1，零命中。✅

### 2.7 旧 `ActivityBookSearchBinding`（旧视图绑定类）
- 排查命令：`grep -rn "ActivityBookSearchBinding" app/src/main/`
- 结果：退出码 1，零命中。✅

### 2.8 旧布局/菜单/字符串残留
- 排查命令：`find app/src/main/res -name "*search*"` + `grep -n "search" app/src/main/res/values/strings.xml`
- 判定：现存 `item_search.xml` / `item_search_list.xml` 由阅读页结果列表复用，属新栈；旧 `activity_search.xml` / `dialog_search_scope.xml` / `menu/book_search.xml` 已拆除。字符串表内 `search` 相关条目均为新页面文案，无旧壳遗留。✅

---

## 3. 模块契约对齐核对

### 3.1 搜索入口注册
- `AndroidManifest.xml`：已注册新 `InkSearchActivity`（`AppTheme.Light`、`adjustResize`、`exported=false`）；旧 `SearchActivity` 条目已删除。✅

### 3.2 调用点语义一致性（11 处）

| # | 调用点 | 入参 | 新入口 | 语义判定 |
| --- | --- | --- | --- | --- |
| 1 | `receiver/SharedReceiverActivity.kt:57` | 分享文本 | `InkSearchActivity.start(this, text)` | ✅ 关键词检索，scope=null |
| 2 | `ui/about/ReadRecordActivity.kt:370` | 阅读记录书名 | `InkSearchActivity.start(act, item.bookName)` | ✅ 书名关键词 |
| 3 | `ui/book/info/BookInfoActivity.kt:936` | 书源+标签 | `InkSearchActivity.start(act, source, kind)` | ✅ 重载 2，书源+关键词 |
| 4 | `ui/book/info/BookInfoActivity.kt:1126` | 作者 | `InkSearchActivity.start(act, book.author)` | ✅ 作者关键词 |
| 5 | `ui/book/info/BookInfoActivity.kt:1140` | 作者 | 同上 | ✅ 一致 |
| 6 | `ui/book/info/BookInfoActivity.kt:1155` | 书名 | `InkSearchActivity.start(act, book.name)` | ✅ 书名关键词 |
| 7 | `ui/book/info/BookInfoActivity.kt:1169` | 书名 | 同上 | ✅ 一致 |
| 8 | `ui/book/source/edit/BookSourceEditActivity.kt:313` | 书源，关键词空 | `InkSearchActivity.start(this, source)` | ✅ 重载 2，空词进历史面板 |
| 9 | `ui/book/source/manage/BookSourceActivity.kt:869` | 书源分部 | `InkSearchActivity.start(this, bookSource)` | ✅ 重载 3，分部检索 |
| 10 | `ui/main/explore/ExploreFragment.kt:328` | 书源 | `InkSearchActivity.start(this, source)` | ✅ 重载 3 |
| 11 | `ui/main/explore/ExploreInkFragment.kt:41/86/196` | 空词/关键词 | `InkSearchActivity.start(...)` | ✅ 空词回落空闲面板 |

### 3.3 资源/样式对齐
- 布局：新搜索页骨架、输入栏、结果列表、历史面板均在 `ui/main/search/` 新包下。✅
- 动效：三点加载指示器（600ms 循环）、stagger 浮入（350ms each）、遮罩淡入淡出（200ms）对齐切片-15 铁律；未使用菊花/默认 ripple/elevation。✅
- 颜色：纸白/墨色/朱砂/暖金四色系，无硬编码十六进制色值；输入框居中、云灰提示 12sp。✅

---

## 4. 遗留疑点与交接清单（待后续切片补全）

| # | 类别 | 疑点描述 | 影响范围 | 验收状态 |
| --- | --- | --- | --- | --- |
| 1 | 网络/离线 | `SearchModel` 并发检索、书源解析、结果合并排序的实际网络行为未跑通；离线判定 `NetworkUtils.isAvailable()` 分支未实测 | 远程书源检索、离线缓存回填 | ⚠️ 待真机/CI 补验 |
| 2 | 分页加载 | `onSearchFinish(isEmpty, hasMore)` 的 `hasMore` 仅收口空态，列表滚动到底翻页未接线 | 长结果列表分页 | ⚠️ 待接分页加载更多 |
| 3 | 热门词 | `SearchUiState.hot` 恒为空，热门词面板数据源未接入 | 热门词推荐入口 | ⚠️ 待接热门词数据源 |
| 4 | 历史清空 | `removeHistory` / `clearHistory` 已接 `searchKeywordDao`，但新页面未暴露清空历史交互入口 | 历史词条管理 | ⚠️ 待接清空历史 UI |
| 5 | 超长词截断 | `capQuery` 已在检索/入库前截断，但高亮层对超长 needle 的守卫未在新页面落地 | 超长关键词高亮 | ⚠️ 待补高亮截断守卫 |
| 6 | 结果去重 | `SearchResultItem` 本地+远程去重合并逻辑未真机验证；同名/同书源条目合并规则待跑通 | 结果列表合并 | ⚠️ 待真机核对去重效果 |
| 7 | 书架搜索入口 | 旧 `BookAdapter` / `SearchAdapter` 行内控件移除后，新书架页搜索入口待接 | 书架内检索 | ⚠️ 待书架页补搜索入口 |

---

## 5. 未验证项声明

1. 本报告为**静态人工核对 + grep 全仓排查**，未执行 `gradle assembleDebug` / `kotlinc` 编译，类型解析、R 类生成、Compose 编译均未经编译器验证。
2. 新/改单测为源码级核对，未执行 `testDebugUnitTest` 确认全绿。
3. 真机渲染：输入框文案位置、三点加载节奏、stagger 渐显、朱砂高亮断行、历史 chips 换行等观感需真机截图对照切片-15 验收。
4. 远程多源检索链路、分页、热门词、历史清空等运行期行为均未实测，见第 4 节交接清单。
5. 旧书架页搜索入口、历史清空交互、热门词面板属跨模块交接，不在本包验收范围内。

---

## 6. 验收签字

- 静态断链排查：✅ 无残留、无断链
- 契约对齐：✅ 入口/调用点/资源一致
- 运行期验收：⚠️ 待后续 CI/真机补验
- 遗留交接：7 项，已列明影响范围与验收状态
