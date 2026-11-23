# 修复-B1：DetailSheet 贴皮拆除（Compose 统一半屏重做）

> 任务编号：B1　｜　仓库：`bqdlz676/legado`（main，开工 HEAD `da571c9`）
> 范围铁律：只改 `ui/main/DetailSheet.kt` + 新增 `ui/main/detail/**`；四个调用点语句零改动；不改排版页逻辑、不改测试；无任何 git 写操作。

## 1. 任务与范围

旧 `ui/main/DetailSheet.kt`（433 行）是 Material `BottomSheetDialogFragment` 壳 + 旧 XML 布局 `sheet_detail.xml` + 代码 `new TextView` 编排，属贴皮件。本单把内部承载换成 `ComposeView` + 统一半屏组件 `InkHalfSheet`，公开入口形状（四工厂 + `show(fm, tag)` + `dismiss()`）原样保留，四个既有调用点行为不变。

## 2. 改动清单（文件:行 + before/after）

### 2.1 改动文件

| 文件 | 改动 | 行数 |
|---|---|---|
| `app/src/main/java/io/legado/app/ui/main/DetailSheet.kt` | 整文件重写：基类 Material 底部弹窗 Fragment → 普通 `DialogFragment`；`inflate(R.layout.sheet_detail)` → `ComposeView` 承载 `InkHalfSheet`；数据采集下沉到 State 构建器 | 旧 433 → 新 177（`git diff --numstat`：+97 / −353） |
| `app/src/main/java/io/legado/app/ui/main/detail/DetailSheetState.kt` | 新增：纯 Kotlin 快照模型 + 四型数据采集构建器（不引 Compose / View 类型） | 382 |
| `app/src/main/java/io/legado/app/ui/main/detail/DetailSheetContent.kt` | 新增：Compose 内容槽渲染（分区标题/正文/副题/动作行/时间轴 AndroidView 桥） | 142 |

合计： tracked +97 / −353；新增 524 行（382 + 142）；三文件共 701 行。

### 2.2 关键 before/after

| 位置 | before（旧） | after（新） |
|---|---|---|
| 类声明（旧 :35） | `class DetailSheet : BottomSheetDialogFragment()` | `class DetailSheet : DialogFragment()`（`androidx.fragment.app`） |
| 视图创建（旧 :77） | `inflater.inflate(R.layout.sheet_detail, container, false)` | `ComposeView(ctx).apply { setContent { InkHalfSheet(...) } }` |
| 弹窗窗口（旧 :81-86） | `ColorDrawable(TRANSPARENT)` + `setDimAmount(0.4f)` + BottomSheet `peekHeight=0.6*屏高` / `isDraggable` | 窗口透明 + `setDimAmount(0f)`；暗化/拖拽/60% 高/回弹全部由 `InkHalfSheet` 茶玻层与拖拽手势统一收口 |
| 分区编排（旧 :401-415） | 代码 `new TextView` + `setTextAppearance(InkTextLabel/InkTextBody)` 塞进 `content` 线性容器 | `DetailSheetContent` 分区列表渲染，颜色/字号取 `ComponentTokens` |
| 数据采集（旧 :110-394 四个 render*） | Fragment 内直接查门面并写控件 | `DetailSheetStateBuilder.build(...)` 产出不可变 `DetailSheetState`，组合期只读不重算 |
| 销毁（旧 :104-107） | `MapWebHolder.detach()` | 保留 `MapWebHolder.detach()`，另清 `state` 引用 |
| 四工厂（旧 :43-70） | `char / person / year / place` | 签名、arguments key、`ContextAnchor.setYear` 副作用逐字保留 |

### 2.3 未改动证据

- `git diff --numstat -- app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`：**空**（调用点零改动）。
- 四个调用点现状（grep 原文）：
  - `ContentTextView.kt:586` `DetailSheet.year(y2).show(fm, "detail_year")`
  - `ContentTextView.kt:618` `DetailSheet.person(nm.name, nm.anchorYear, lineText).show(fm, "detail_person")`
  - `ContentTextView.kt:640` `DetailSheet.place(nm).show(fm, "detail_place")`
  - `ContentTextView.kt:1378` `DetailSheet.char(ch).show(fm, "detail_char")`
- 全仓 `grep -rn "DetailSheet" app/src/main/java --include=*.kt`：除本件与上述四点外，仅内部 `switchYear` 自调，无其它消费方；`app/src/test`、`app/src/androidTest` 无引用。

## 3. 字段覆盖表（旧字段/分区 → 新落点，零缺失核对）

### 3.1 字（char）

| 旧实现（旧行号） | 旧内容 | 新落点 |
|---|---|---|
| `tv_title`（:91） | 字本身 | `DetailSheetState.title` → `InkHalfSheet(title=…)` |
| `tv_sub`（:117-130） | 拼音（读音表优先，回落 `info.pinyin`）· 部首 · 笔画 | `DetailSheetState.subtitle` |
| 「说文」label+body（:132-135） | `《说文解字》：${sw.text}` | section `说文` |
| 「义项」（:136-139） | `meaning`，`\u0001` 换行 | section `义项` |
| 「书证」（:140-143） | `${usage.source}　${usage.sentence}` | section `书证` |
| 「词组」（:144-148） | `wordsStartingWith` 全角空格拼 | section `词组` |
| 「提示」（:149-152） | `此字未收，只按字形显示。`（info==null） | section `提示` |
| 全屏按钮 | 旧字型无按钮 | `action = null`，不渲染动作行 ✓ |

### 3.2 人（person）

| 旧实现（旧行号） | 旧内容 | 新落点 |
|---|---|---|
| `tv_title`（:91） | 人名 | `state.title` |
| `tv_sub`（:164-175） | 国别 · 身份 · 来源标签（空 → 未收录） | `state.subtitle` |
| 读音 body（:178-181） | `读音　$py`（专用异读门面优先，回落字库） | 无标题 section `读音　…`（排在「本段」之前，顺序同旧） |
| 「本段」- 句条（:187-193） | `sentencesAbout` 取 3 条 `· …`；空 → 无再现句提示 | section `本段` lines |
| 「本段」- 同场（:194-199） | `coPresent` 取 6，`人名〔关系〕` 顿号拼 | section `本段` lines |
| 「本段」- 未带片段（:200-202） | 未带入正文片段引导文案 | section `本段` lines |
| 「本段」- 年岁数（:203-207） | 有锚年 → `yearTitle · 岁数/未出生/已卒`；无锚年 → 不推算提示 | section `本段` lines |
| 「本书」（:210-219） | 当前章（空 → 未定位）/ 关键事迹 / 出场索引待扫 | section `本书` 三行 |
| 「档案」- 字号封号（:223-233） | 字/号/封/又称（≤4）/ 字号无载 | section `档案` 行 1 |
| 「档案」- 生卒（:234-243） | 生命周期表回落档案生卒；缺位 `?`；生卒无载 | section `档案` 行 2 |
| 「档案」- 在位（:245-246） | `reignOf` 回落 `reignStart/End`；`在位无载` 口径 | section `档案` 行 3 |
| 「档案」- 国别身份称呼（:247-254） | 国别无载/身份无载 + 其他称呼 ≤10 | section `档案` 行 4 |
| 同名候选（:257-260） | `others.size >= 2` 才出；标题 `还有 N 个同名`；≤8 行 `dupTag` | section `还有 N 个同名` |
| 来源（:263-266） | 有档 → `来源　${source.label}`；无档 → 未收录仅统计口径 | section `来源` |
| 全屏按钮（:268-274） | 跳 `PersonDetailActivity`，`name` + `appearances="[]"`，随后 dismiss | `action = DetailAction("全屏查看 →", PersonDetail(name))` |

### 3.3 年（year）

| 旧实现（旧行号） | 旧内容 | 新落点 |
|---|---|---|
| 标题（:301-302） | `year<=0` → `前${1-year}年`；否则 `${year}年` | `state.title` |
| `tv_sub`（:303） | `Chronology.yearTitle(year)` | `state.subtitle` |
| 同年对照（:305-314） | 年表空 → 未收录提示；否则 `同年各国（在位第几年）`，`州 · 中文数字年` | section `同年对照` / `同年各国（在位第几年）` |
| 「纪年」时间轴（:317-323） | `InkTimelineView(year)`，拖动回调 `switchYear` | section `纪年`（`TIMELINE` 型）+ `TimelineBridge`（AndroidView 桥），回调 `onTimelineYear` |
| 「说明」（:325-326） | 全屏地图切朝代图层引导文案 | section `说明` |
| 全屏按钮（:328-332） | 跳 `PlaceMapActivity`，`year` extra | `action = PlaceMap(name="", year=year, newTask=false)` |
| `switchYear`（:335-339） | 取 `supportFragmentManager` → dismiss → `DetailSheet.year(y).show(fm, "detail_year")` | `DetailSheet.switchYear` 逐字保留 |

### 3.4 地（place）

| 旧实现（旧行号） | 旧内容 | 新落点 |
|---|---|---|
| `tv_title`（:91） | 地名 | `state.title` |
| `tv_sub`（:348） | 沿革首行 | `state.subtitle` |
| 「沿革」（:350-351） | 去首行拼接；空 → `暂无可考` | section `沿革` |
| 同名候选（:353-356） | `还有 N 个同名`，逐行 | section `还有 N 个同名` |
| 位置-点（:365-371） | 标题 `位置（年代·层级）`；`名 · 备注/坐标已收录` + 另开窗口提示 | section `位置（…）` |
| 位置-面（:372-375） | `位置（年代·辖区）`；治所落在某城提示 | section `位置（…·辖区）` |
| 位置-未收录（:376-379） | 坐标未收录、三级推定也不给点 | section `位置` |
| 当前锚点（:382-386） | 锚年非空 → `yearTitle` | section `当前锚点` |
| 全屏按钮（:388-393） | `在地图窗口中打开 →`；`PlaceMapActivity` + `name` + `FLAG_ACTIVITY_NEW_TASK` | `action = PlaceMap(name, year=null, newTask=true)` |

### 3.5 生命周期/副作用核对

- `year` 工厂内 `ContextAnchor.setYear(year)` 最高优先级锚定副作用：**保留**（工厂体内逐字未动）。
- `onDestroyView` → `MapWebHolder.detach()`：**保留**。
- arguments key：`type / name / detail / extra` 四个键名与旧版一致；`excerpt.take(4000)` 截断保留。

## 4. grep 回证（改后真实输出）

```
$ F=app/src/main/java/io/legado/app/ui/main/DetailSheet.kt
$ grep -c "BottomSheetDialogFragment" $F
0
$ grep -c "InkHalfSheet" $F
4
$ grep -cE 'Binding|R\.layout|com\.google\.android\.material' $F app/src/main/java/io/legado/app/ui/main/detail/*.kt
0
$ git diff --numstat -- app/src/main/java/io/legado/app/ui/main/DetailSheet.kt
97	353	app/src/main/java/io/legado/app/ui/main/DetailSheet.kt
$ git diff --numstat -- app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt
（空，调用点未改）
```

- `BottomSheetDialogFragment` 命中 **0** ✓
- `InkHalfSheet` 命中 **4**（≥1）✓
- `*Binding` / `R.layout` / material 包引用命中 **0** ✓
- 新增目录：`app/src/main/java/io/legado/app/ui/main/detail/`（`DetailSheetState.kt` 382、`DetailSheetContent.kt` 142）

## 5. 自检脚本输出原文

```
$ bash data_scripts/selfcheck-path.sh
════ 关键路径接线自检 v2 ════
  ✓ 进书         入口   书架→阅读 → ui/main/bookshelf/InkShelfFragment.kt
  ✓ 进书         分发   阅读壳启动 → ui/book/read/ink/InkReadActivity.kt
  ✓ 进书         数据   书对象 → model/ReadBook.kt
  ✓ 进书         渲染   竖排页视图 → ui/book/read/ink/InkReadController.kt
  ✓ 进书         兜底   空章兜底 → ui/book/read/ink/BubblePlacement.kt
  ✓ 竖排排版   引擎   VerticalLayoutEngine.layout → ui/book/read/vertical/VerticalLayoutEngine.kt
  ✓ 竖排排版   调用   VerticalLayoutEngine ×9
  ✓ 竖排排版   缓存   invalidateCache → ui/book/read/vertical/VerticalLayoutEngine.kt
  ✓ 点字命中   命中   几何命中 → ui/book/read/vertical/VerticalLayoutEngine.kt
  ✓ 点字命中   分发   probeChar → ui/book/read/ink/GestureRouter.kt
  ✓ 气泡解析   契约   BubbleResolver.resolve → help/bubble/BubbleResolver.kt
  ✓ 气泡解析   调用   BubbleResolver ×6
  ✓ 气泡解析   渲染   InkBubble → ui/book/read/ink/InkBubble.kt
  ✓ 查人         数据   help/person → help/person/PersonAssets.kt
  ✓ 查人         接线   气泡→人物 → help/bubble/BubbleResolver.kt
  ✓ 纪年         锚定   ContextAnchor → help/chrono/ChronoPlaces.kt
  ✓ 纪年         干支   Chronology 干支 → help/chrono/Chronology.kt
  ✓ 半屏         组件   InkHalfSheet 定义 → ui/components/InkHalfSheet.kt
  ✓ 半屏         调用   InkHalfSheet( ×26
  ✓ 半屏         模型   HalfSheetSection → ui/components/HalfSheetModel.kt
  ✓ 退书         手势   GestureRouter → ui/book/read/ink/GestureRouter.kt
  ✓ 退书         返回   新壳返回键处理 → ui/book/read/ink/InkReadActivity.kt

结果: 通过 22 环 / 缺 0 环
```

半屏两环（组件定义 ✓、调用 ×26 ✓）均 PASS；全量 22/22，0 FAIL。

## 6. 二阶三阶影响推演（R59）

**① 直接效果**：点字/点人/点地名/点年号弹出的详情层，壳与排版换成统一 `InkHalfSheet`；视觉（纸白金框、金杠、茶玻 40% 遮罩、60% 上限高、下拉回弹）与 `InkBubble` 详情半屏、`DictDialog` 大图半屏一致。

**② 连带影响（点名文件/接口）**：
- `ui/book/read/page/ContentTextView.kt` 四个调用点：只持有工厂返回的 `DetailSheet` 句柄调 `.show(fm, tag)`；本件保留 `DialogFragment.show(FragmentManager, String)` 继承口径，签名零改动，排版/点字/搜索链路不感知内部换壳。
- `ui/book/read/ink/InkBubble.kt`、`ui/dict/DictDialog.kt`：同组件复用方，不受影响；本件未改 `InkHalfSheet.kt` / `HalfSheetModel.kt`，组件契约无新增兼容扩展需要（四型均走 `content` 自定义槽，未强套 `HalfSheetContent` 固定分区顺序）。
- 门面层 `ChineseDict / PersonProfile / PersonProfileWiring / PersonGraphExtractor / Chronology / PlaceTimeDict / ContextAnchor / NameResolver`：调用签名与旧版逐条一致，纯数据出口不改。
- 出口 Activity：`PersonDetailActivity`、`PlaceMapActivity` 在同包，Intent extra 口径（`name` / `appearances="[]"` / `year` / `name` + `NEW_TASK`）不变。

**③ 二阶三阶后果**：
- 阅读页状态：`dialog=true` 独立窗口覆盖层，组合内不读写宿主页码/偏移/高亮集合（同 §5.4 铁律），开合前后阅读位置与下划线状态应一字不差——此项未真机验证，列未验证项。
- 复杂度：详情层新增「State 快照 / Content 渲染 / 宿主壳」三段式；后续新增第五种详情形态可照此模板，不必再碰 Fragment 编排。
- 维护成本：旧 XML + 两个字体样式 + 旧抓取器失去最后消费者（见交接节），若不另单清理会成为静态死代码；本单文件域不允许删，已记录。
- 风险面：`InkTimelineView` 经 `AndroidView` 桥接，其帧循环（`postInvalidateDelayed`）生命周期依赖 View detach；旧版挂在 ScrollView 内同口径，未引入新的泄漏路径，但需 CI/真机观察时间轴关闭后是否继续刷帧。

## 7. 反例与边界（R61，先自我否证）

**最强反对者假设：InkHalfSheet 能力盖不住旧 DetailSheet，重做会丢内容。** 逐项否证：

| 边界 | 旧版行为 | 本实现兜底 | 是否成立 |
|---|---|---|---|
| 空数据（字典未收 / 无候选 / 年表空 / 沿革空） | 各型自带「无载/未收录/暂无可考」文案 | 文案逐条搬进 State；subtitle 空串不渲染；分区不出空白标题行 | 成立，不丢兜底 |
| 采集异常（门面抛错/资源缺失） | 旧版无兜底，异常可崩在 onViewCreated | `runCatching { build(...) }.getOrElse { fallbackState(name) }`：出「内容加载失败，请返回后重试。」提示节，不出空白态、不崩 | 成立，新增兜底 |
| 半屏重复打开 | 每次工厂 new 实例，同 tag 交 FragmentManager 处理 | 口径不变；不做去重（旧版也没有），避免擅自改交互 | 成立，行为对齐 |
| 快速连点动作行 | 旧 TextView 单击单次回调 | 墨触手势 `rememberInkGesture` 单次 release 回调；动作后 `dismissAllowingStateLoss()` | 成立 |
| 宿主 Activity 已销毁 | 旧版 `requireContext()` 有崩风险 | `runAction` 先查 `activity?.isFinishing/isDestroyed`，已销毁则不启 Activity 只收口；`switchYear` 保留 fm 空守卫；关闭一律 `dismissAllowingStateLoss` 防状态丢失异常 | 成立，比旧版更稳 |
| 长文本超屏 | ScrollView 竖滑 | `InkHalfSheet` 内容区 `verticalScroll`；`BasicText` 自动换行；`SelectionContainer` 保留长按复制 | 成立 |
| 深色态 | 旧 XML 固定墨色 | 仓内组件 token 为固定墨色板（`ComponentPalette` 无深色变体），与 `InkBubble`/`DictDialog` 同一口径；未另造色值 | 口径一致；深色适配整体缺位，列未验证 |
| 断网 | 本屏全本地资产查询，无网络调用 | 不发网络请求，口径不变 | 成立 |
| 超长片段 / 极端字号 | 工厂侧 `excerpt.take(4000)` | 截断保留；长行换行不截断丢字 | 成立 |
| 时间轴拖出越界年份 | `switchYear` 重开 year 屏并回写锚年 | 桥接回调原样透传 `onYearChanged → switchYear` | 成立 |

**否证结论**：`InkHalfSheet` 的自定义 `content` 槽 + 内部竖滑足以覆盖四型内容；唯一非文本件是 `InkTimelineView`，以 `AndroidView` 桥接（仓内 `InkReaderShell` 已有同型先例），未给组件强加新 section 类型，`InkHalfSheet.kt` / `HalfSheetModel.kt` 零改动，向后兼容不受影响。

## 8. 认知成本（R63）

**改动前**：单文件 433 行，数据采集、控件编排、跳转副作用耦合在一个 Fragment；接手需同时读本文件、`sheet_detail.xml`、`styles_ink_type.xml` 三处才能改一个字段文案。

**改动后**：三文件 701 行，职责收拢：
- `DetailSheet.kt`（177 行）：只留 Fragment 生命周期、四工厂 arguments、窗口透明、跳转收口；
- `DetailSheetState.kt`（382 行）：纯 Kotlin 快照 + 门面采集，不依赖 Compose/View，可直接做 JVM 单测靶子；
- `DetailSheetContent.kt`（142 行）：纯渲染，颜色字号全走 token。

**新增概念**（5 个，命名自解释）：`DetailSheetState`、`DetailSection`、`DetailSectionKind`、`DetailTarget`（sealed）、`DetailAction`。无第二层抽象、无并行模型；复用既有 `InkHalfSheet / ComponentTokens / rememberInkGesture`。
**接手路径**：改文案/字段口径 → 只读 State 文件；改视觉 → 只读 Content 文件；宿主接入方仍只见 `DetailSheet` 四工厂，不需读两个新文件。
**简化动作**：未新建 ViewModel（旧版也没有）；未引入 Compose 状态提升到 Fragment 外；快照在 `onCreateView` 一次性构建，组合期不重算，避免重组时重复查字典/图谱。

## 9. 历史包袱审计（R62，`git log --follow` 结论）

| 提交 | 引入内容 | 定性 | 本单处置 |
|---|---|---|---|
| `1722e10` | 旧半屏 Sheet 纸白金框 + 金杠 + 60% 高茶玻，BottomSheet 壳 + XML | 历史遗留：视觉规格被 `InkHalfSheet` 统一收口，壳层淘汰 | 壳拆除；视觉规格由组件继承 |
| `9c3feea` | 地图移出 Sheet 改独立窗口打开 | 有意为之（避免 WebView 堆在半屏内） | 保留：动作行仍跳 `PlaceMapActivity` |
| `36bff17` | `InkTimelineView` 自绘九格粒子环时间轴 | 有意为之（引擎件） | 保留，AndroidView 桥接进 Compose |
| `403bd7d` | 旧字体样式 `InkTextLabel/InkTextBody` 接入 | 历史遗留：排版职责转 Compose token | 本件不再引用；样式资源失管见交接 |
| `53e91cd` | 删旧 `CharDetail/AllusionDetail` 死页面 | 已完成的清理 | 无回潮 |
| `eeebe14` | 拆旧 GLES 地球仪与旧 relation 扁平栈 | 无关本件 | 不动 |
| `78f4293` | D2 人物下游接线（Profile/Source/候选） | 有意为之（冻结门面） | 调用原样保留 |

**为已不存在问题而引入**：旧 BottomSheet 的 `peekHeight` 拖拽态/默认材质弹层，已被组件内拖拽回弹取代，随壳一并拆除，不留兼容分支。

## 10. 跨领域对照（R60）

- **对照 1：Web 前端模态 overlay（portal 全屏遮罩 + 底部面板）**：独立窗口层级承载面板，宿主文档不重排。本件 `dialog=true` 同构——遮罩与面板在 Compose `Dialog` 内，宿主阅读页只被暗化不被重排。
- **对照 2：编辑器侧边释义面板（浮动引用层，不打断主文档光标上下文）**：释义以浮动层呈现，关闭后主上下文无损。对应 §5.4「开合前后阅读位置/高亮一字不差」，本件不读写宿主状态。
- **不采纳**：官方 Material BottomSheet 组件（材质主题依赖、视觉不符规格、拖拽口径不自绘）；旧 WebView 内嵌地图（`9c3feea` 已判独立窗口，不回潮）。

## 11. 最慢环节（R64）

1. **慢点**：人物段 `PersonGraphExtractor.sentencesAbout / coPresent` 对 excerpt（≤4000 字）分句扫描，在 `onCreateView` 主线程同步跑。
2. **原因**：旧版同口径同步采集；本单范围不含异步化，且 Fragment 重建时重复查询的问题旧版同样存在。
3. **优化建议**：若 CI bench / 真机观察到弹屏掉帧，再按「摘录文本 → 图谱结果」加进程内缓存，或把采集挪到 `LaunchedEffect` + 加载态；本单不提前引入协程生命周期复杂度。
4. **编译慢点**：本机不编译；以 CI clean assemble 为准（见未验证项）。

## 12. 返工复盘沉淀（R65）

| 根因类别 | 本单对应风险 | 预防手段（已落地/建议） |
|---|---|---|
| 契约漂移 | 四工厂签名、arguments key、Intent extra 与调用方咬合 | 工厂体逐字保留；grep 回证调用点零改动；extra 口径在覆盖表逐行核对 |
| 贴皮 | 复用旧 XML/绑定/适配编排 | 禁符 grep 归零门禁（`BottomSheetDialogFragment`/`Binding`/`R.layout`/material 包 = 0） |
| 边界缺失 | 空数据、宿主销毁、采集异常、长文 | State 内逐条旧兜底文案 + `fallbackState` 异常节 + 销毁守卫 + 组件竖滑 |
| 验收放水 | 编译通过≠功能可用 | 未验证项显式声明；以下一轮 CI + L3 契约验收为准 |

沉淀规则：后续半屏/详情层重做一律走「State 纯模型 → Content 渲染 → 薄宿主壳」三段式；宿主壳只保留入口形状与生命周期，不写控件编排。

## 13. 铁律 59–66 逐条结论

- **59 二阶三阶推演**：见第 6 节，三层（直接效果 / 连带文件接口 / 用户行为与维护成本）齐备。
- **60 跨领域对照**：见第 10 节，Web overlay、编辑器释义面板两域对照，不采纳项写明理由。
- **61 先自我否证**：见第 7 节，10 项边界逐条给兜底与结论；最强反证（组件能力盖不住）已否证。
- **62 历史包袱审计**：见第 9 节，七笔历史分有意为之 / 历史遗留 / 已淘汰三类。
- **63 认知成本**：见第 8 节，三文件职责拆分、5 个新概念清单、接手路径说明。
- **64 最慢环节**：见第 11 节，慢点定位、原因、后续优化三句结论。
- **65 返工复盘沉淀**：见第 12 节，四类根因聚簇 + 预防手段 + 三段式规则沉淀。
- **66 开工前必读**：开工先读 `机制-子代理必读.md` 与总纲铁律 56、59–66；本报告含影响推演 / 反例边界 / 认知成本三节，前置阅读已完成。
- **56 贴皮禁令**：未复用旧 XML、视图绑定、列表适配、旧 Fragment 编排；未把旧 XML 结构逐条翻译成 Compose；复用仅限底层门面（字典/图谱/纪年/时空/偏好总线）。

## 14. 未验证项（如实声明）

- 本机无 Android SDK，**未跑 gradle / kotlinc / assemble，未编译、未跑单测、未真机安装**；语法与 API 咬合仅经静态通读与 grep 核对，编译结果与运行行为以下一轮云端 CI（clean assemble + 全量单测）为准。
- 未真机验证：弹屏动画/下拉回弹手感、时间轴桥接手势与关屏后帧循环、长文滚动、文字长按复制、深色态表现、开合后阅读位置/高亮是否逐字不差。
- `InkHalfSheet` 在 `dialog=true` 下由本 Fragment 透明窗承载的双窗口层级，参照 `DictDialog` 既有接法；层级表现未真机验证。

## 15. 交接（发现但本单未改，文件域之外）

1. **失管静态资源，建议另派清理单**：
   - `app/src/main/res/layout/sheet_detail.xml`：kt 引用已归零；
   - `app/src/main/java/io/legado/app/ui/widget/InkGrabberView.kt`：仅剩旧 XML 引用，金杠已由 `InkHalfSheet.HalfSheetGrabber` Canvas 自绘取代；
   - `app/src/main/res/values/styles_ink_type.xml` 中 `InkTextLabel` / `InkTextBody`：最后消费者随本件移除（`InkTextTitle/InkTextNumber` 需另行核对是否还有引用）。
2. **可测靶子**：`DetailSheetStateBuilder` 为纯 Kotlin（仅 `Context` 入参），建议后续补 JVM 单测覆盖四型兜底分支；当前无测试引用本件。
3. **既有遗留（非本单引入）**：整本出场首次/末次段落持久化表（旧 `PersonChapterEvents` 等价物）D2 仍未接，旧注释已标注「待接」；本件文案口径原样保留。
4. **跨模块观察项**：若后续 `ContentTextView` 点字链路改为 Compose 宿主，`DetailSheet` 的 Fragment 壳可进一步瘦身为纯 Compose 弹层；本单不预判该改动。
