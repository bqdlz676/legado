# 设计·Ink 阅读页接入 Legado 排版引擎

> 锚点卡：本文件是设计件，结论回源到下列锚点：
> - `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt::chapterBodyText`
> - `app/src/main/java/io/legado/app/ui/book/read/page/entities/TextPage.kt::draw`
> - `app/src/main/java/io/legado/app/ui/book/read/page/provider/TextPageFactory.kt::moveToNext`
> - `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt::setContent`
>
> 守则回执：见文末。

## 一、问题定性（先看事实，不看愿景）

规格第一句是"只保留 Legado 的排版引擎，其余全部按新架构重写"。现状与此相反：

1. `InkReadActivity.kt:161` 只有一句空实现 `override fun onLayoutPageCompleted(index: Int, page: TextPage) = Unit`，引擎算完页没人接。
2. `InkReadActivity.kt:442 chapterBodyText(chapter)` 返回**整章纯文本**，Ink 页面拿它自己上屏 —— 分章、分页、断行、字距、缩进、段间距全部没走引擎。
3. `ReadBookActivity.kt:216` 仍实现 `ReadView.CallBack`，`:397` 仍持 `ParagraphReadView` —— 旧阅读页与 Ink 页**两套并存**，用户在跑哪套取决于入口。
4. 引擎本体完好：`TextPageFactory`(171 行，moveToNext/moveToPrev/hasNext/hasPrev)、`TextPage`、`TextLine`、`ChapterProvider` 都在。

所以不是"引擎坏了"，是"引擎没接线"，而且外面还挂着一套旧皮。这一条不补，后面所有翻页手感、墨爆粒子、暖金辉光都建在纯文本假页上。

## 二、三个可选路线（先假设最顺的那条是错的）

### 路线 A：把 `TextPage.draw` 的宿主换成接口（最干净，风险最高）
`TextPage.draw(view: ContentTextView, canvas, relativeOffset)` 现在强依赖 `ContentTextView` 这个 View。
改成 `draw(host: DrawHost, canvas: Canvas, relativeOffset: Float)`，`ContentTextView` 实现 `DrawHost`。
- 好处：引擎与视图彻底解耦，Compose 直接 `drawIntoCanvas { it.nativeCanvas }` 画页，零多余 View。
- 坏处：`TextPage`/`TextLine` 内部读 `view.xxx` 的地方多（高亮、注引、下划线、选中、缩放），改签名会一次动到引擎多个文件；**沙箱不能本地编译**，这种跨文件签名改动在 CI 才能验，一次红就是几十条编译错 —— 正是 `88e62bc` 那种事故的重演。

### 路线 B：离屏 ContentTextView 当绘制代理（可行，别扭）
在 Ink 里 `remember { ContentTextView(context) }` 一个永不 attach 的实例，只作为 `draw()` 的宿主传进去，Compose 用 `drawIntoCanvas` 出页。
- 好处：引擎零改动。
- 坏处：引擎内部会读 `view.width/height/padding/context`，离屏实例这些值必须手工灌成与 Compose 布局一致，否则断行宽高对不上；且这是个常驻不可见 View，日后必被当成"历史包袱"。

### 路线 C：引擎层=真 View，特效层=Compose（风险最低，先落这条）
Compose 里用 `AndroidView { ContentTextView(it) }` 作为**文字面**，它按引擎算好的页绘制；上面再叠 Compose 图层（墨爆粒子、暖金辉光、翻页位移、毛玻璃边栏）。
- 好处：引擎 100% 原样跑，零签名改动、零离屏灌值；外面所有观感仍由 Compose 重画 —— 满足"外面全部重做"，只是文字面这一层保留原生 View。
- 坏处：一个 AndroidView 嵌在 Compose 里，跨层动画（翻页时文字跟着手指走）需要把位移从 Compose 传给 View（`translationX` 或 `graphicsLayer`），双击缩放、长按选字要额外桥接；长期看仍是过渡态。

**选择：先 C，后 A。** 理由：CI 现在对最新代码仍是红（`88e62bc` Verify/Screenshot 双红），此时上 A 等于在红的基线上再做一次跨文件签名手术，红了分不清谁的责任。C 把"引擎接线"这件事先做成可用真值，等 CI 连绿 10 次、`acceptance-evidence.py` 有基线之后，再把 C->A 的解耦当独立一单做，那时有编译面证据兜底。

反例检验（假设 C 是错的）：C 最可能死的点是"翻页跟手"。如果 Ink 的翻页是要文字随手指位移、页面卷曲、露边，AndroidView 里的文字面跟不上 —— 这时 C 就退化成"只能做淡入淡出翻页"。应对：C 阶段翻页用 Compose 层做整页位移动画（把 AndroidView 整体 `graphicsLayer.translationX` 平移），文字面不参与内部重绘；只要一页是一张位图级平移，跟手是够的。真正做不到的只有"页内文字逐行滚动"，那本来就是 A 的活，记在 A 阶段。

## 三、接线契约（C 阶段的接口定义）

### 1. 状态来源
`ReadBook` 是引擎与 UI 的唯一状态源（书、章、进度、样式）。Ink 页不得自己维护"当前章纯文本"。
- 进章：`ReadBook.openChapter(index)`（旧 `ReadBookActivity` 用的同一入口），完成回调里拿 `TextPage`。
- 翻页：`ReadBook.moveToNext()` / `moveToPrev()`，翻页结果通过 `ReadView.CallBack`（或其等价回调）回灌。
- 出页：`TextPageFactory` 产 `TextPage`；`ContentTextView.setContent(textPage)` 上屏。

### 2. 图层顺序（从下到上）
1. 纸面底色（Compose `drawBehind`，暖金绢色渐变 + 微噪点）
2. 引擎文字面（`AndroidView(ContentTextView)`，透明背景，只画字）
3. 高亮/注引（引擎自绘，随 2 走）
4. 墨爆粒子、辉光、磨砂层（Compose）
5. 翻页位移/卷曲（对 1~3 整体 `graphicsLayer`）
6. 交互层（点击区、手势、菜单）

### 3. 必做的桥
- 尺寸：`AndroidView` 的 `update` 里把 `layoutParams` 宽高设为 Compose 约束尺寸，并把 `ContentTextView.padding` 设为 `ChapterProvider.paddingTop` 等引擎期望值 —— 断行宽高必须与引擎一致，否则每行字数对不上。
- 翻页跟手：拖拽中只对图层 1~3 平移，不调 `moveToNext`；松手过阈值才调一次引擎翻页并复位位移。这样引擎重排与动画解耦，不会每帧重排。
- 双击缩放：交给引擎自己的缩放路径（`ContentTextView` 已支持），Compose 只做按钮/手势识别。
- 选字/长按：引擎自绘选区，Compose 层透传长按坐标。

## 四、落地顺序（每步可独立验收）

1. **C-1 文字面上屏**：Ink 页加 `AndroidView(ContentTextView)`，`ReadBook` 打开第一章，`setContent` 出页；删掉 `chapterBodyText` 的整章纯文本上屏路径。验收点：翻页能翻、行断与旧版一致、进度百分数一致。
2. **C-2 图层叠特效**：纸面底色 + 粒子 + 辉光套上，翻页用 `graphicsLayer` 位移动画。验收点：翻页跟手、无每帧重排（打点看 `TextPageFactory` 调用次数）。
3. **C-3 旧皮下线**：`ReadBookActivity` 与 Ink 二选一 —— 保留 Ink，旧阅读页入口全部改指 Ink；旧 `ReadView.CallBack` 的实现只留 Ink 一处。**新逻辑先接好再删旧**，删旧单独一笔提交。
4. **A（延后）**：CI 连绿 10 次后，把 `TextPage.draw` 宿主换成 `DrawHost` 接口，去掉 AndroidView，换成纯 Compose `drawIntoCanvas`。验收点：像素级比对 C 阶段截图（`screenshot.yml` 的 Paparazzi 帧）差异为 0。

## 五、风险与规避

- **风险 1：断行宽高对不上 → 每行字数错位。** 规避：C-1 验收必须比对"同一章同一字号下，旧版与新版的每页首行文字、每页行数"完全一致，用测试固化。
- **风险 2：AndroidView 吞手势。** 规避：`AndroidView` 只做绘制不做触摸（`isClickable=false`、`onTouchEvent` 返回 false），所有手势在 Compose 层判定后转发。
- **风险 3：两套阅读页并存导致状态分叉。** 规避：`ReadBook` 单例是唯一状态源，切换入口不改状态；C-3 必须删掉另一套，不留兼容层。
- **风险 4：沙箱不能编译，写了不验。** 规避：每一步落在独立小提交，推上去立刻看 CI；`kotlin-syntax-lint.py` 的 S5/S7/S8 与 `acceptance-evidence.py` 先行跑一遍再推。CI 红时不发新步骤，先修红。
- **风险 5：动画每帧重排卡顿。** 规避：拖拽期只平移不重排；打点验证 `TextPageFactory.moveToNext` 调用次数 ≤ 松手次数。

## 六、验收标准（本设计交出去的门槛）

- C-1：翻页可用、行断与旧版逐页一致、无整章纯文本上屏路径残留（`grep chapterBodyText` 只剩历史注释或空）。
- C-2：翻页跟手、粒子与辉光不掉帧、重排次数可数。
- C-3：`grep ReadView.CallBack` 只剩 Ink 一处实现；旧 `ReadBookActivity` 入口改指或删除。
- A：与 C 阶段截图像素差异 0。

## 守则回执

- 指纹：docs/机制-总控必读.md=5a2c0c3d9f54；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「**任务分型 + DoD + 止损（铁律 E12）**：派单前先判类型」 —— docs/机制-总控必读.md:44
- 逐字引用 2：「**锚点强制**：每条结论必须带 `文件:行` 或 `commit sha`；下次要用直接 `sed -n 'X,Yp'` / `git show sha` 回源」 —— docs/机制-总控必读.md:108
- 逐字引用 3：「**禁本地编译**：不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。」 —— docs/机制-子代理必读.md:12
- 适用条款：总控必读 :44（本件按"探索型"交货：三路线 + 反例检验 + 止损点；实现另开"执行型"写码单）、:108（本文结论全部带 `文件::符号` 锚）；子代理必读 :12（沙箱禁编译，故先选零签名改动的路线 C）、:71（后续写码须先跑 `kotlin-syntax-lint.py` 且 PASS 才推）。

## 复核-2026-11-26-C1 事实核查（总控派单）

> 角色：只读事实核查代理。本单全程只读，未跑 git 写操作、未编译、未改 `app/` 源码与 `data_scripts/` 脚本；本节为文末追加，既有段落未改动。

守则回执：
- 指纹：docs/机制-总控必读.md=9da6a3ed0ccc；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「5. **锚点强制**：每条结论必须带 `文件:行` 或 `commit sha`；下次要用直接 `sed -n 'X,Yp'` / `git show sha` 回源（回源成本 ≪ 记忆成本）。」 —— docs/机制-总控必读.md:108
- 逐字引用 2：「- **禁 git 写操作**：不许 add/commit/push/revert/checkout——提交由总控统一做。」 —— docs/机制-子代理必读.md:11
- 逐字引用 3：「> 你是**独立验收 AI**，不是你验收对象的朋友。开工顺序：① 读完本文件；② 读任务书点名的模块报告与被验收代码；③ 读该模块对应的规格原文/切片/契约（**逐字**，不许读二手摘要）；④ 自己写校验脚本、自己取数。」 —— docs/机制-验收代理必读.md:3
- 适用条款：总控必读 :108（每条结论带 `文件:行` 锚点，可回源）；子代理必读 :11（本单无任何 git 写操作）、:12（禁本地编译，本单未跑 gradle/kotlinc/assemble）；验收代理必读 :3（逐条回源取数，不读二手摘要）；文档子代理必读 :3.3（核查后以 grep 回证，不凭印象报结论）。

### 核查 1：`onLayoutPageCompleted` 是否仍是空实现

- 命令：`grep -n "onLayoutPageCompleted" app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt`
- 输出片段：`161:        override fun onLayoutPageCompleted(index: Int, page: io.legado.app.ui.book.read.page.entities.TextPage) = Unit`
- 结论：仍是 `= Unit` 空实现，引擎算完的 `TextPage` 无人接收。同块旁证：`:157` `override fun sureNewProgress(progress: BookProgress) = Unit`、`:159` `override fun cancelSelect() = Unit` 同为空实现。
- 锚点：`app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:161`。

### 核查 2：`:442` 是否仍取整章纯文本自绘

- 命令：`grep -n "chapterBodyText" app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt`
- 输出片段：
  - `221:                        body = chapterBodyText(prev),`
  - `228:                        body = chapterBodyText(next),`
  - `435:        chapterBody = chapter?.let(::chapterBodyText).orEmpty()`
  - `442:    private fun chapterBodyText(chapter: TextChapter): String {`
- 函数体（`:443-450`）：段落项非空时 `items.joinToString("\n") { it.text.toString().trim() }` 再过滤空行；段落项未就绪时 `return chapter.pages.joinToString("\n") { it.text }` —— 出口是整章纯文本字符串，交 Ink 壳层自绘。
- 结论：整章纯文本上屏路径仍在；分章、分页、断行、字距、缩进、段间距均未走引擎。
- 锚点：`InkReadActivity.kt:435`、`InkReadActivity.kt:442`（函数体 `:443-450`）。

### 核查 3：`PageFactory` / `TextPageFactory` 引用面

- 命令：`grep -rn "PageFactory\|TextPageFactory" app/src/main/java/`
- 输出片段（6 命中，全部落在旧阅读包 `ui/book/read/page/` 内，属引擎本体与旧 View）：
  - `page/ContentTextView.kt:55` import `TextPageFactory`；`:1923` `val pageFactory: TextPageFactory`
  - `page/ReadView.kt:45` import `TextPageFactory`；`:139` `var pageFactory: TextPageFactory = TextPageFactory(this)`
  - `page/api/PageFactory.kt:3` `abstract class PageFactory<DATA>(protected val dataSource: DataSource)`
  - `page/provider/TextPageFactory.kt:6` import `PageFactory`；`:10` `class TextPageFactory(dataSource: DataSource) : PageFactory<TextPage>(dataSource)`
- 反向核查：`grep -rn "PageFactory\|TextPageFactory" app/src/main/java/io/legado/app/ui/book/read/ink/` 退出码 1，**0 命中**。
- 结论：引擎工厂类本体完好，但 Ink 壳一侧零引用，尚未接线。
- 锚点：`app/src/main/java/io/legado/app/ui/book/read/page/ReadView.kt:139`、`page/provider/TextPageFactory.kt:10`；Ink 目录 0 命中（grep exit=1）。

### 核查 4：旧阅读页是否仍在跑

- 命令：`grep -n "ReadView.CallBack\|ParagraphReadView" app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
- 输出片段：
  - `139:import io.legado.app.ui.book.read.paragraph.ParagraphReadView`
  - `216:    ReadView.CallBack,`
  - `397:    private var pausedParagraphViewport: ParagraphReadView.Viewport? = null`
  - `771:                    val viewport = ParagraphReadView.Viewport(location.bookUrl, location.chapterUrl, location.itemId, 0)`
- 结论：旧阅读页的 `ReadView.CallBack` 实现与 `ParagraphReadView` 状态仍存活，旧页与 Ink 页两套并存。
- 锚点：`app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt:216`、`ReadBookActivity.kt:397`（`:139`、`:771` 旁证）。

### 核查 5：设计件 C-1 / C-2 / C-3 与验收判据逐字摘录（只摘不改写）

- C-1（本文件 :68）：「1. **C-1 文字面上屏**：Ink 页加 `AndroidView(ContentTextView)`，`ReadBook` 打开第一章，`setContent` 出页；删掉 `chapterBodyText` 的整章纯文本上屏路径。验收点：翻页能翻、行断与旧版一致、进度百分数一致。」
- C-2（本文件 :69）：「2. **C-2 图层叠特效**：纸面底色 + 粒子 + 辉光套上，翻页用 `graphicsLayer` 位移动画。验收点：翻页跟手、无每帧重排（打点看 `TextPageFactory` 调用次数）。」
- C-3（本文件 :70）：「3. **C-3 旧皮下线**：`ReadBookActivity` 与 Ink 二选一 —— 保留 Ink，旧阅读页入口全部改指 Ink；旧 `ReadView.CallBack` 的实现只留 Ink 一处。**新逻辑先接好再删旧**，删旧单独一笔提交。」
- 风险 1 规避（本文件 :75）：「- **风险 1：断行宽高对不上 → 每行字数错位。** 规避：C-1 验收必须比对"同一章同一字号下，旧版与新版的每页首行文字、每页行数"完全一致，用测试固化。」
- 验收标准（本文件 :83-86）：
  - :83「- C-1：翻页可用、行断与旧版逐页一致、无整章纯文本上屏路径残留（`grep chapterBodyText` 只剩历史注释或空）。」
  - :84「- C-2：翻页跟手、粒子与辉光不掉帧、重排次数可数。」
  - :85「- C-3：`grep ReadView.CallBack` 只剩 Ink 一处实现；旧 `ReadBookActivity` 入口改指或删除。」
  - :86「- A：与 C 阶段截图像素差异 0。」

### 核查 6：C-1 现状可验证性判定

**C-1 现状：不可验证。**

原因（逐条落到 文件:行）：
1. 引擎页产物无人接：`InkReadActivity.kt:161` `onLayoutPageCompleted(...) = Unit`，引擎分页结果不进 Ink 页面，C-1 要求的 `setContent` 出页链路不存在。
2. Ink 文字面仍是整章纯文本：`InkReadActivity.kt:435` / `:442` 走 `chapterBodyText` 自绘；C-1 要求的 `AndroidView(ContentTextView)` 文字面未落地，ink 目录对 `PageFactory`/`TextPageFactory` 零引用（核查 3）。
3. 对照基线无法构造：`ReadBookActivity.kt:216` / `:397` 旧页仍在跑，新旧两套并存；要比对的"新版每页首行文字、每页行数"尚不存在，旧版每页首行 + 行数也未固化成快照，设计 :75 要求的新旧逐页比对两侧都缺输入。

要实现 C-1 比对的最小可执行手段（不依赖真机，均在 CI 侧承载）：
1. **JVM 单测承载（首选）**：在 `app/src/test/java/io/legado/app/ui/book/read/page/provider/`（既有悬挂标点/行宽规则测试同目录，如 `HangingLineWidthTest.kt`）新增分页快照测试：固定同一 fixture 章节文本、同一字号/行距/页宽高，驱动 `TextPageFactory` 分页，逐页导出「首行文字 + 行数」序列；先落旧版基线快照，C-1 接线后新路径同参跑出序列逐页 diff。现状前提已核：provider 测试目录下没有任何驱动 `TextPageFactory` 分页出页的用例（现有用例只覆盖标点悬挂、行宽、几何规则）。
2. **Paparazzi 截图承载（备选/复核）**：`:screenshot` 模块（`.github/workflows/screenshot.yml`，路径 A，commit `07615c1`）新增阅读页帧：同一 fixture 章节分别渲染旧 `ReadView` 路径与接线后 Ink 文字面，帧图比对首行与行数。现状已核：`:screenshot` 现有帧仅 `BasicComponentsSnapshotTest` / `BookshelfSnapshotTest` / `CardFoundationSnapshotTest` / `CatalogCardsSnapshotTest`，无阅读页帧。
3. **落地顺序建议**：先在纯旧路径上固化旧版每页首行 + 行数基线快照（不依赖 C-1 落地）；待 C-1 按本文件 :68 接线完成后，再跑新旧逐页 diff；比对口径即本文件 :75「同一章同一字号下，旧版与新版的每页首行文字、每页行数完全一致」与 :83「行断与旧版逐页一致」。

### 未验证项

- 未核真机渲染像素级一致性（本单红线：禁编译、禁真机，只做静态回源）。
- 未核旧版分页在真实屏幕尺寸 / 字号档位下的实际行数，需上述 CI 单测或截图帧落地后取数。
- `chapterBodyText` 之外是否还存在其他纯文本上屏残留路径：本单仅按交接单第 12 条点名符号核查，未全量遍历 Ink 渲染调用链。
