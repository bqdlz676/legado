> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批1-搜索书架 §三）
> 回执缺失（规则上线前交付，留档）

# 验收报告 · 接线包 A — 书架单路与画板自绘

- 基线：branch `main`，HEAD `98ee6d0`。
- 验收方式：**只读静态验收 + 纯 JVM 推演核对**；沙箱内**未编译、未跑单测/bench、未上真机**（硬红线 1：本地禁 gradle/kotlinc 编译，编译与测试真跑走云端 CI；硬红线 2：未执行 git add/commit/push）。
- 文件域：`ui/main/MainActivity.kt`、`ui/main/bookshelf/**`、`ui/main/PaintActivity.kt`、`ui/main/InkSlatePreloader.kt`、`ui/components/inkslate/**`、`app/src/main/assets` 下 Excalidraw 资源、`app/src/test/java/io/legado/app/ui/main/bookshelf` 与 `components/inkslate`、`AndroidManifest.xml` 中 PaintActivity 条目；**未触碰 Legado 排版引擎**。
- 规格依据：`docs/落文-规格切片/切片-10-书架页.md`、`docs/落文-规格切片/切片-12-画板InkSlate.md`、`docs/落文-模块契约索引.md` 1.5 节。

## 一、最终结论

**静态接线完成：书柜 tab 单路只挂 `InkShelfFragment`，旧 style1/style2/Base 双路 RecyclerView 实现、`bookGroupStyle` 分流偏好、WebView 画板壳、预热器与 Excalidraw 网页包全部拆除；`PaintActivity` 改为 Compose 自绘宿主，承载既有 `InkSlateCanvas` / `InkSlateTopBar` / `InkSlateBottomBar`，PNG 走原生 `InkSlateBitmap` 渲染、SVG 走 `InkSlateSvg` 拼装，批注元信息收口到模型层纯函数并配 JVM 单测。**

编译结果、单测通过情况、真机观感与导出落盘/分享行为**均未验证**，待云端 CI 与真机复验（见第六节）。违规点（FAIL 级）：本批文件域内静态扫描未发现 Material 默认组件 / ripple / elevation / 菊花 / linear 加载 / 12 token 之外的散落 hex。

## 二、文件清单（绝对路径前缀 `/home/user/Doubao/chats/38454122521460994/legado/`）

### 新增（main）

| 文件 | 职责 |
|---|---|
| `app/src/main/java/io/legado/app/ui/components/inkslate/InkSlateBitmap.kt` | 原生 PNG 位图渲染器：纸白底 → 批注底图 → 底图层笔迹 → 墨迹层笔迹 → 底部附注；与 Compose 画布消费同一份 `InkSlateState`，几何口径逐点对齐（压感变宽、箭头锋芒、矩形/圆描边、文字落点、橡皮不出几何）。仅持 `android.graphics`，不含 Compose。 |

### 修改（main）

| 文件 | 改动 |
|---|---|
| `app/src/main/java/io/legado/app/ui/main/PaintActivity.kt` | 整页重写为 `ComponentActivity` + `setContent` Compose 宿主：接线批注 extras、底图异步解码、题字输入对话框、PNG/SVG 导出落盘与系统分享、朱砂失败提示；保留旧启动 extras 键与转场动画。 |
| `app/src/main/java/io/legado/app/ui/main/MainActivity.kt` | 核对书柜单路 adapter 接线（`idBookshelf -> InkShelfFragment`）；返回键 / 双击回顶 / reshow 重排分别接到 `back()` / `gotoTop()` / `upSort()`；移除未使用的 `MyFragment` 导入。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/InkShelfFragment.kt` | 本批核对上一代理接线：`back()`（单组平铺不拦截）、`gotoTop()`（信号自增滚回首项）、`upSort()`（重订阅书籍流）齐备，`MainFragmentInterface` 契约满足。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfScreen.kt` | 加载指示器三点色由 textTertiary 改为 brandGold（对齐契约 1.5-C「三个金点依次亮灭」）。 |
| `app/src/main/java/io/legado/app/ui/components/inkslate/InkSlateModel.kt` | 新增 `AnnotationMeta.fromReadContext(...)` 纯工厂：书名空回落自由画板（null）、章名空只落书名、页码 0 起换算「第 N 页」；供 Activity 接线与 JVM 单测共用。 |
| `app/src/main/java/io/legado/app/ui/components/inkslate/InkSlateCanvas.kt` | 注释口径修正：PNG 编码归属 `InkSlateBitmap` 原生渲染，不再提及已删的 WebView 预热池。 |
| `app/src/main/java/io/legado/app/help/config/AppConfig.kt` | 删除死属性 `bookGroupStyle` 读写；留注释说明旧 SharedPreferences 键为无害死键、不做迁移。 |
| `app/src/main/java/io/legado/app/constant/PreferKey.kt` | 删除死常量 `bookGroupStyle`。 |

> 说明：`AppConfig.kt` / `PreferKey.kt` 不在标称的 `ui/` 文件域内，但验收判据要求 `bookGroupStyle` 源码只剩注释；该偏好经全仓 grep 无设置页 / 业务消费者（res/xml 无键引用），属纯死代码，故一并收口删除。排版引擎与其他业务模块未触碰。

### 删除（main）

| 文件 / 目录 | 说明 |
|---|---|
| `app/src/main/java/io/legado/app/ui/main/InkSlatePreloader.kt` | WebView 预热器；全仓 grep 无第二个消费者，随 WebView 路径一并拆除。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt` | 旧双路基座 Fragment（分组 / 配置对话框 / 快滑条回调）。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfReadProgress.kt` | 旧 Material `LinearProgressIndicator` 扩展；消费者仅随拆的旧适配器，新 Compose 卡片自绘朱砂进度线。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style1/BookshelfFragment1.kt` | 旧样式一 Fragment。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BaseBooksAdapter.kt` | 旧样式一基座适配器。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BooksAdapterGrid.kt` | 旧样式一网格适配器。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BooksAdapterList.kt` | 旧样式一列表适配器。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BooksAdapterList2.kt` | 旧样式一列表二适配器。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BooksFragment.kt` | 旧样式一书籍子 Fragment。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style2/BaseBooksAdapter.kt` | 旧样式二基座适配器。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style2/BooksAdapterGrid.kt` | 旧样式二网格适配器。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style2/BooksAdapterList.kt` | 旧样式二列表适配器。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfFragment2.kt` | 旧样式二 Fragment。 |
| `app/src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfGroupItem.kt` | 旧样式二分组项构建器（含 `adjacentBookshelfGroupId` 内部函数）。 |
| `app/src/main/assets/www/`（整目录，`index.html` + `assets/` 散列 JS/CSS/woff2 共 128 条） | Excalidraw / Mermaid 网页画板包；唯一加载方为旧 `PaintActivity`，已随自绘重写清除。 |

### 保留未删（经 grep 确认仍有消费者）

| 文件 | 原因 |
|---|---|
| `app/src/main/java/io/legado/app/ui/main/bookshelf/UrlAddBookGroup.kt` | `mergeBookGroupForUrlAdd` / `migrateBookForUrlAdd` 仍被 `BookshelfViewModel`（61 / 114 / 117 行）调用，是加书分组位掩码合并口径，非旧界面代码。 |
| `app/src/main/assets/relation/` | 关系图模块自有 cytoscape 包，与画板无关。 |
| `app/src/main/assets/web/` | Legado 自带帮助页 / Vue 资源，与画板无关。 |

### 修改 / 删除（test，纯 JVM 域内）

| 文件 | 改动 |
|---|---|
| `app/src/test/java/io/legado/app/ui/components/inkslate/InkSlateModelTest.kt` | 新增 3 个用例：阅读上下文拼名章页码附注、缺章名只落书名且页码从 1 起算、书名为空回落自由画板。 |
| `app/src/test/java/io/legado/app/ui/main/bookshelf/BookshelfReadProgressTest.kt` | 裁掉对已删旧 Fragment / 旧布局 / 旧 Material 扩展的结构断言；保留 `Book.readProgress()` 换算、`BookshelfReadProgressMode` 模式收口、BookDao 查询口径等纯模型 / 数据层断言。 |
| `app/src/test/java/io/legado/app/ui/main/bookshelf/style2/BookshelfGroupItemTest.kt` | 随 `BookshelfGroupItem.kt` 删除。 |
| `app/src/test/java/io/legado/app/ui/main/bookshelf/style2/BookshelfGroupSwipeTest.kt` | 随旧样式二 Fragment 删除。 |
| `app/src/test/java/io/legado/app/ui/main/bookshelf/BookshelfItemAnimatorTest.kt` | 旧动画器契约测试，随旧适配器删除。 |
| `app/src/test/java/io/legado/app/ui/main/bookshelf/UrlAddBookGroupTest.kt` | **保留**：源文件仍在，7 个位掩码用例继续有效。 |

## 三、接线口径

### A. 书架单路

- `MainActivity` adapter `getItem`：`idBookshelf -> InkShelfFragment(position)`，无第二 id、无 `bookGroupStyle` 分支；`fragmentMap` 仅登记 `InkShelfFragment` 实例。
- 返回键：`onBackPressedDispatcher` 回调在书柜页调用 `InkShelfFragment.back()`；新书架单组平铺无子分组层级，`back()` 不拦截，交 Activity 走双击退出 / 退后台（与旧版基座「子分组退回根分组」等价能力的新版口径）。
- 双击书柜 tab：`onNavigationItemReselected` → `gotoTop()` → `scrollTopSignal++`，画面层观察信号滚回首项。
- reshow / 配置变更：`recreate()` → `upSort()` → 重新订阅 `bookDao.flowRoot()` 并按最近阅读时间排序。
- 删除 `bookGroupStyle`：全仓 grep 确认无 res/xml 偏好键、无其他业务读取；`AppConfig` 属性与 `PreferKey` 常量一并移除，旧数据里的同名 SharedPreferences 键不迁移、不清理，属无害死键。

### B. 画板自绘（切片-12 §8.1 / §8.2 / §8.4）

- 工具集（§8.1）：画笔 · 直线 · 箭头 · 矩形 · 圆 · 文字 · 橡皮 · 撤销 / 重做 · 图层（底图 / 墨迹两层）· PNG/SVG 导出，由既有 `InkSlateTopBar` / `InkSlateBottomBar` / `InkSlateCanvas` 承载；双击画布撤销在 `InkSlateCanvas` 内（`DOUBLE_TAP_MS` 阈值）。
- 工具栏（§8.2）：上行七工具全露出；下行六色圆点（朱砂 · 墨灰 · 花青 · 藤黄 · 暖金 · 石青）· 线宽三档 · 撤销 · 重做 · 图层 · 导出；选中态暖金底线 / 暖金圆点，反馈走墨触手势，无 Material ripple。
- 批注模式（§8.4）：extras 键与旧版逐点对齐 `bookName` / `chapterName` / `pageIndex` / `pageText` / `bgImage`；`AnnotationMeta.fromReadContext` 拼章节名（`书名·章名`，章名空只落书名）、页码「第 N 页」、正文纯文本（截断 2000 字）；导出附注 `《左传·隐公元年》第 3 页批注` 口径由模型 `footnote()` 收口，PNG 底部与 SVG `<text>` 同口径输出。
- 底图：`bgImage` 先按绝对路径解码，回落旧 `filesDir/annot_bg/` 目录；IO 线程解码，失败留无底图不崩；底图绘于笔迹之下。
- 导出：
  - PNG：`InkSlateBitmap.render(state, bgBitmap, density)` 原生 `android.graphics` 位图 → `getExternalFilesDir("paint")/ink-yyyyMMdd-HHmmss.png` → FileProvider 分享（`image/png`）；编码失败抛错进 `runCatching`。
  - SVG：`InkSlateSvg.build(state).svg` 写 `.svg`（`image/svg+xml`）→ FileProvider 分享。
  - 失败：朱砂条内联提示 + `toastOnUi` 文案，3 秒自动消退；不崩、不留空按钮、不降级到空白导出。
- 返回结果契约：旧版 `PaintActivity` 未 `setResult`，全仓无 `registerForActivityResult` 指向画板；新版保持同一契约（默认 finish，不写结果），启动方 `MainActivity` 无改动。
- Manifest 条目保持不变：`exported=false`、`AppTheme.Light`、`adjustResize`、`sensorLandscape`、`hardwareAccelerated=true`。

### C. WebView 路径清理

- `InkSlatePreloader` 全仓唯一消费者是旧 `PaintActivity`；已删，无预热 / 复用残留。
- `assets/www/` 整包清除；`assets/relation/`、`assets/web/` 经 grep 确认与画板无关，保留。
- 规格 §8.3 的 WebView 预热池条款被本批任务 C 覆盖：原生 Compose 画板无需网页预热，预热器职责不再存在；双击撤销等交互保留在原生画布内。

## 四、grep 证据（命令 + 命中）

### 1. 旧书架符号只剩注释 / 跨模块待交接测试

```
$ grep -rn "BookshelfFragment1|BookshelfFragment2|BaseBookshelfFragment|bookGroupStyle" app/src/main app/src/test
app/src/main/java/io/legado/app/help/config/AppConfig.kt:336:    // 旧版书柜分组样式双路偏好（bookGroupStyle）随书架单路改造拆除，不再读取 / 写入；
app/src/test/java/io/legado/app/ui/main/HomeFastScrollerContractTest.kt:27:            "src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfFragment2.kt",
app/src/test/java/io/legado/app/ui/main/bookshelf/BookshelfReadProgressTest.kt:16: * 旧版 RecyclerView 书架（style1/style2 / BaseBookshelfFragment / Material
```

主源码命中仅 `AppConfig.kt` 一行注释；`BookshelfReadProgressTest.kt` 命中为注释；`HomeFastScrollerContractTest.kt` 属跨模块待交接（见第七节）。

### 2. `PaintActivity.kt` 无旧网页画板壳残留

```
$ grep -n "WebView|Excalidraw|InkSlatePreloader|JavascriptInterface" app/src/main/java/io/legado/app/ui/main/PaintActivity.kt
（无输出）
```

### 3. 旧书架包导入 / 绑定 / 适配器无残留

```
$ grep -rn "bookshelf.style|bookshelf.BaseBookshelf|bookshelf.BooksAdapter|bookshelf.BookshelfFragment|bookshelf.BookshelfGroupItem" app/src/main/java
（无输出）

$ grep -rln "FragmentBookshelf1Binding|FragmentBookshelf2Binding|ItemBookshelfListBinding|ItemBookshelfList2Binding|ItemBookshelfGridBinding|ItemBookshelfGrid2Binding|ItemBookshelfListGroupBinding|ItemBookshelfGridGroupBinding|ItemBookshelfGridGroup2Binding|ViewBookshelfHeaderBinding|DialogBookshelfConfigBinding" app/src/main/java
（无输出）
```

### 4. 预热器无第二个消费者

```
$ grep -rn "InkSlatePreloader" app/src/main/java
（无输出）
```

### 5. 无 Material 默认组件 / ripple / elevation / 菊花 / linear 加载（文件域内）

```
$ grep -rn "import androidx.compose.material|com.google.android.material|MaterialTheme" app/src/main/java/io/legado/app/ui/main/bookshelf app/src/main/java/io/legado/app/ui/components/inkslate app/src/main/java/io/legado/app/ui/main/PaintActivity.kt
app/src/main/java/io/legado/app/ui/components/inkslate/InkSlateCanvas.kt:33: * - 不包裹 MaterialTheme、不用 Material ripple、颜色只走 [ComponentTokens] / [InkPreset]。

$ grep -rni "ripple|elevation|circularprogress|linearprogress|progressbar|swiperefresh" ...（同域三路径）
（命中均为「不用 Material ripple / 不用 elevation」的注释，无实际调用）
```

### 6. hex 硬编码收口

```
$ grep -rn "0x[0-9A-Fa-f]{6,8}" app/src/main/java/io/legado/app/ui/main/bookshelf app/src/main/java/io/legado/app/ui/components/inkslate app/src/main/java/io/legado/app/ui/main/PaintActivity.kt | grep -v "ShelfTokens.kt|InkSlateModel.kt"
（无输出）
```

- `ShelfTokens.kt`：12 色语义 token 唯一收口位。
- `InkSlateModel.kt`：`HUA_QING` / `TENG_HUANG` 为 §8.2 六色预设中花青 / 藤黄两色的模型层常数收口；`0x00FFFFFF` 为位掩码非颜色。
- `PaintActivity.kt`、`InkSlateBitmap.kt`：hex 计数 0。

### 7. 观感落点（契约 1.5）

- 书架：卡片微光 `colorGlow` / 暖金核心 `brandGold`（`ShelfBookCard.kt:142`、`GoldParticleField.kt`）；进度竖线与底部细线均为朱砂 `textAccent`（`ShelfBookCard.kt:306`、`:360`）；加载三点改 `brandGold`（`BookshelfScreen.kt:343`）。
- 画板：画布纸白底 `ComponentTokens.bgPage`；工具选中指示为暖金 `brandGold` 底线 / 圆点（`InkSlateToolbar.kt:73-87`、`:144`）；遮罩 `bgOverlay` 茶玻色。

## 五、单测清单（纯 JVM，未本地执行）

### `InkSlateModelTest.kt`（本批新增 3 例）

| # | 用例 | 核对点 | 期望 |
|---|---|---|---|
| 1 | `阅读上下文拼出名章页码附注` | 书名「左传」+ 章名「隐公元年」+ pageIndex 2 | 附注 `《左传·隐公元年》第 3 页批注`，正文原样携带 |
| 2 | `缺章名时附注只落书名 页码从一起算` | 章名空、pageIndex 0 | `《史记》第 1 页批注` |
| 3 | `书名为空回落自由画板 不带批注` | 书名纯空白 | `fromReadContext` 返回 null |

既有模型 / SVG / bench 用例未改：七工具六色板、起落笔、撤销重做往返、橡皮命中、压感模拟、图层隔离、自检拦截、落点夹紧、SVG 拼装与附注转义。

### `BookshelfReadProgressTest.kt`（保留段）

- `Book.readProgress()`：未读 null、单章起读归 1、章序进度夹取 0..1。
- `BookshelfReadProgressMode`：三档 resolve / 线宽收口；`AppConfig` / `Restore` 偏好键存在性断言。
- BookDao：继续阅读查询含全部书架媒体类型；在读计数查询只计已起读书籍。

### 未覆盖的可测逻辑

- `InkSlateBitmap` 原生渲染器依赖 `android.graphics`，不在普通 JVM 单测范围；其几何口径与 `InkSlateCanvas` 静态逐点对照，真机导出图比对列入未验证项。
- Activity 层 extras 解析 / 文件落盘 / 分享 Intent 属 Android 壳层，未单测；可下沉的纯口径已收进 `AnnotationMeta.fromReadContext`。

## 六、未验证项（静态接线，均未真跑）

1. **编译未验证**：未执行 gradle / kotlinc（硬红线）。Compose 重载解析、`BasicTextField` / `BitmapPainter(asImageBitmap())` / `windowInsetsPadding` 调用形式、internal 组件跨包可见性，待云端 CI 编译确认。
2. **单测未真跑**：新增 3 例模型用例与保留段断言的通过情况未在本地执行；期望值为静态推演。
3. **真机观感未目检**：书架暖金微光溢出、朱砂进度线、金点加载；画板纸白底 + 金线工具条、六色圆点、批注底图叠加层级、题字排版，均未上设备核对。
4. **导出链路未实测**：PNG 位图尺寸 / 压感线宽 / 箭头锋芒 / 附注落位，SVG 在外部查看器中的字形与坐标，FileProvider 授权与分享 chooser，异常目录 / 编码失败的朱砂提示路径，均未在设备或模拟器跑过。
5. **批注入口未接线**：阅读页当前仍走其内联 `inkView`（`ReadBookActivity.startHandAnnotation`），尚无把章节名 / 正文 / 截图传入 `PaintActivity` 的启动方；Activity 侧 extras 契约已备好，接入链路属阅读页批次。
6. **原生渲染器一致性**：`InkSlateBitmap` 与 Compose 画布两套绘制路径的几何一致性仅静态对照，未做导出图与画布截图逐像素比对。
7. **降级边界**：底图解码失败留无底图、空画布导出白纸 / 空 SVG、超大笔迹集合的内存与帧预算，未在异常环境实测。

## 七、跨模块问题与交接

### 1. 阻塞：`BundlePayloadContractTest.kt` 仍引用两条已删适配器路径（文件域外）

- 路径：`app/src/test/java/io/legado/app/BundlePayloadContractTest.kt:48-49`
- 问题：`migratedPayloadSources` 仍含 `bookshelf/style1/books/BaseBooksAdapter.kt`、`bookshelf/style2/BaseBooksAdapter.kt`；`file.readText()` 对已删文件抛 `NoSuchFileException`，该契约测试必红。
- 交接建议：从列表删除这两条；其余条目（ChangeBookSource / ExploreShow / BookAdapter / BookSource / TxtTocRule / DictRule / ReplaceRule / RssSource）保留。

### 2. 阻塞：`HomeFastScrollerContractTest.kt` 书架段随旧实现失效（文件域外）

- 路径：`app/src/test/java/io/legado/app/ui/main/HomeFastScrollerContractTest.kt`
- 问题：15-19 行解析已孤儿的 `fragment_bookshelf2.xml`；26-55 行读取已删 `style2/BookshelfFragment2.kt` 源码做 `initRecyclerView` / `upFastScrollerBar` / 解绑顺序断言。
- 交接建议：移除书架段断言与布局解析；探索段（`ExploreFragment` / `rv_find` / `showDiscoveryFastScroller`）仍有效，保留。新 Compose 书架若需要快滑条契约，应由书架批次另立 Compose 层契约测试。

### 3. 未使用导入：`ReadBookActivity.kt:107`

- `import io.legado.app.ui.main.PaintActivity` 无任何构造 / 引用（阅读页批注走内联 `inkView`）。建议阅读页批次清理；本批文件域未改该文件。

### 4. 孤儿资源（res 域，本批未删，登记给资源收口批次）

- 布局：`fragment_bookshelf1.xml`、`fragment_bookshelf2.xml`、`item_bookshelf_list.xml`、`item_bookshelf_list2.xml`、`item_bookshelf_grid.xml`、`item_bookshelf_grid2.xml`、`item_bookshelf_list_group.xml`、`item_bookshelf_grid_group.xml`、`item_bookshelf_grid_group2.xml`、`view_bookshelf_header.xml`、`dialog_bookshelf_config.xml`。
- 菜单：`res/menu/main_bookshelf.xml`。
- grep 确认主源码已无上述布局 / 菜单的 Kotlin 引用；对应 ViewBinding 类不再生成，删除 XML 不影响编译，但需资源批次核对字符串 / 尺寸引用后再清。

### 5. 残留死偏好（设置页收口批次）

- `AppConfig.bookshelfLayout`、`AppConfig.showBooknameLayout`、`AppConfig.showBookshelfFastScroller` 及对应 `PreferKey` 键、`pref_config_other.xml` 中可能对应的开关，其 UI 消费者已随旧 Fragment 删除；当前为只读 / 可写的死存储。本批仅拆除 `bookGroupStyle`，其余死偏好待设置页批次统一收口（含旧 SharedPreferences 键迁移口径）。

### 6. 其他批次并行改动提示

- 工作区内搜索模块（`ui/book/search`、`ui/main/search`）、探索页、地图 crossing 等存在其他代理的未提交改动，本批未读取、未引用、未回退；验收时请按各自接线包分别核对，勿与本包文件清单混淆。
