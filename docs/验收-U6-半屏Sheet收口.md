# 验收-U6 · 半屏 Sheet 收口（切片-11）

> 基线：main @ 98ee6d0。任务：接上一个被叫停代理留下的半成品 `InkHalfSheet.kt` / `HalfSheetModel.kt`，
> 将 `InkBubble` 私有详情 Sheet 收口到统一 `InkHalfSheet`，按 kind 分派人物 / 字词两套固定顺序。
> 硬红线：未本地编译、未执行任何 git 写操作；文件域仅限 ui/components 两件、InkBubble.kt 及对应测试。

## 一、交付文件清单

| 文件 | 变更 | 说明 |
| --- | --- | --- |
| `app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt` | 改 | 统一半屏 Sheet 容器：遮罩 / 拖拽 / 六态 / 分区列表 / 金杠 / 出处脚注 |
| `app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt` | 改 | 纯模型层：锚点常数、分区枚举、快照、兜底文案、`HalfSheetMapper` 协议映射器 |
| `app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt` | 改 | 删除私有 `InkBubbleDetailSheet`，改为调用统一 `InkHalfSheet`；新增非两套形态的原生槽 `BubbleRawDetail` |
| `app/src/test/java/io/legado/app/ui/components/HalfSheetMapperTest.kt` | 新增 | 映射器分派 / 顺序 / 上限 / 兜底 / 同名显隐 / §5.4 快照不变 JVM 单测 |

未改动红线文件：`InkReadActivity`、`InkReaderShell`、`VerticalPageView`、`help/bubble/*` 契约
（`git status` 上述路径无本包产出）。

## 二、5.1–5.4 落点行号

### 5.1 容器（下沉 8dp + 暗化 40% + 茶玻毛玻璃 + 纸白金框 + 金杠双点 + 60% 上限 + 跟手关闭）

| 规格条目 | 实现位置 |
| --- | --- |
| 弹起阅读页下沉 8dp | `HalfSheetModel.kt:29`（`HOST_SINK_DP=8`）；`HalfSheetModel.kt:67-68`（`hostSinkDp` 进度联动）；`InkHalfSheet.kt:185`（下沉量换算）、`InkHalfSheet.kt:189-193`（宿主槽整体 offset 下沉） |
| 暗化 40% | `HalfSheetModel.kt:26`（`DIM_ALPHA=0.40`）；`HalfSheetModel.kt:63-64`（`maskAlpha` 随升起进度）；`InkHalfSheet.kt:203-204`（遮罩取 `maskAlpha`） |
| 遮罩 bg-overlay rgba(26,20,16,.45) | 色值 token `ComponentPalette.kt:25`（`BG_OVERLAY=0x731A1410`，本包未改色板）；消费点 `InkHalfSheet.kt:203`（`ComponentTokens.bgOverlay`） |
| 模糊 12px | `HalfSheetModel.kt:47`（`MASK_BLUR_DP=12`）；`InkHalfSheet.kt:201`（`.blur(MASK_BLUR_DP.dp)`）。平台边界见第五节 |
| Sheet 纸白底 | `InkHalfSheet.kt:238`（`ComponentTokens.bgPage`） |
| 金色 1px 边框 | `InkHalfSheet.kt:239-246`（`1.dp` + `ComponentTokens.brandGold`，顶部圆角） |
| 顶部 4dp 金横杠（非圆角条）+ 两端金点 | 常数 `HalfSheetModel.kt:38`（杠高 4dp）、`:41`（杠宽）、`:44`（点半径）；Canvas 自绘 `InkHalfSheet.kt:345-373`：直边 `drawRect` `:360`，两端 `drawCircle` `:365`、`:370`；未使用圆角 Shape |
| 最大高度 = 屏高 × 60% | `HalfSheetModel.kt:23`（`MAX_HEIGHT_FRACTION=0.60`）；`InkHalfSheet.kt:218-219`（高度换算）、`:230`（`heightIn(max=...)`） |
| 跟手下拉关闭，阅读页同步复原 | 拖拽态 `InkHalfSheet.kt:221-224`；横杠拖拽 `:253-264`；关闭阈值 `HalfSheetModel.kt:75-79`；关闭时遮罩 / Sheet 反向回弹 `InkHalfSheet.kt:115-124`；下拉中遮罩同步减淡（`maskProgress` 回落 → 阅读页恢复亮度） |
| 弹起 250ms ease-out | `HalfSheetModel.kt:50`（`RISE_DURATION_MS=250`）；`InkHalfSheet.kt:109`、`:122`（`cardEnterEasing`） |
| 遮罩 200ms cubic-bezier(.4,0,.6,1) | `HalfSheetModel.kt:53`（`MASK_DURATION_MS=200`）；`InkHalfSheet.kt:113`、`:118`（`maskEasing`，曲线参数 `ComponentPalette.kt:111`） |

### 5.2 人物 Sheet（顺序固定）

映射入口 `HalfSheetModel.kt:285-335`（`buildPerson`）；分区枚举与固定顺序 `HalfSheetModel.kt:96-108`、`:130-135`。

| 规格条目 | 实现位置 |
| --- | --- |
| ① 本段行为（一句加粗） | `HalfSheetModel.kt:289-295`（`PERSON_ACTION`，`boldFirst=true`，首行 SemiBold 渲染 `InkHalfSheet.kt:450`） |
| 云灰 1px 线 | `InkHalfSheet.kt:380-387`（`HalfSheetDivider`，`colorDivider`）；分区之间插入 `InkHalfSheet.kt:436` |
| ② 同场人物（纯文本每行一人可点） | `HalfSheetModel.kt:298-302`（`PERSON_CAST`；协议缺口见第五节，当前兜底「无载」） |
| ③ 本书时间轴（首次出场→关键事迹→末次） | `HalfSheetModel.kt:305-313`（`PERSON_TIMELINE`：生卒行 + 事迹行） |
| ④ 档案（本名/字/号/封号/生卒/谱系 对齐） | `HalfSheetModel.kt:316-322`（`PERSON_ARCHIVE`：本名 / 字 / 号 / 别称 / 生卒 / 身份 / 谱系；小传、事迹不重复入档） |
| ⑤ 同名的其他 N 人 › | `HalfSheetModel.kt:325-332`（`PERSON_DOUBLETS`；`candidates` 为空整节隐藏；标题含计数；`links` 逐行对齐裸 query，点击回传 `InkBubble.kt:311-315`） |

### 5.3 字词 Sheet（顺序固定）

映射入口 `HalfSheetModel.kt:339-385`（`buildWord`）；分区枚举与固定顺序 `HalfSheetModel.kt:111-120`、`:137-140`。

| 规格条目 | 实现位置 |
| --- | --- |
| ① 拼音 + 部首 + 笔画 + 全义项 | `HalfSheetModel.kt:345-355`（`WORD_PRONUNCIATION`：phonetic + body 散行 +【义项】块 + 无标签纯文本释义，`:349`） |
| ② 说文释文 + 反切 | `HalfSheetModel.kt:358-363`（`WORD_SHUOWEN`：【说文】块；无载占位） |
| ③ 古籍用例 ≤ 3 条 | `HalfSheetModel.kt:366-374`（`WORD_QUOTATIONS`，上限 `HalfSheetModel.kt:145`）；空出引导 `:373` |
| ④ 该字起首常见词 ≤ 14 个 | `HalfSheetModel.kt:377-382`（`WORD_WORDS`，上限 `HalfSheetModel.kt:146`；协议缺口见第五节，当前出引导文案） |

### 5.4 关闭后阅读位置 / 滚动位置 / 下划线状态一字不差

| 规格条目 | 实现位置 |
| --- | --- |
| 只叠层不重排：组件不读写宿主页码 / 偏移 / 高亮集合 | 契约注释 `InkHalfSheet.kt:70-72`；宿主槽原样透传 `InkHalfSheet.kt:189-194`（`host()` 调用前后无状态改写）；独立窗口模式 `dialog=true` 不重排宿主 `InkHalfSheet.kt:77-79`、`:153-159` |
| 状态快照结构（页码 / 偏移 / 下划线键集合） | `HalfSheetModel.kt:194-207`（`HalfSheetHostSnapshot`）；全等判定 `:200-203`（`matches` 三项逐一比对） |
| 快照不变单测 | `HalfSheetMapperTest.kt:229-233`（开合前后全等）、`:236-241`（页码 / 偏移 / 下划线任一变化即不等） |
| 长义项可滚动不截断 | 正文区竖滑 `InkHalfSheet.kt:287-290`（`verticalScroll`），高度受 60% 上限约束，超出滚动而非截断 |

## 三、grep 证据

### 3.1 `InkHalfSheet` 存在真实调用方

```text
$ grep -rn "InkHalfSheet(" app/src/main --include=*.kt
app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt:304:    InkHalfSheet(
app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt:86:fun InkHalfSheet(
```

调用点接线：`InkBubble.kt:303`（`HalfSheetMapper.build(content)`）、`:304-317`（可见性 / 关闭 / 独立窗口 / 标题 / 出处 / 模型 / 链接回调 / 原生槽）。

### 3.2 `InkBubble` 不再自造 Sheet

```text
$ grep -n "Dialog\|InkBubbleDetailSheet\|private fun.*Sheet" app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt
（无命中，exit=1）
```

全仓扫描 `InkBubbleDetailSheet`：`app/src/main`、`app/src/test` 均无残留。

### 3.3 无 hex 硬编码（交付三文件 + 测试）

```text
$ grep -nE "0x[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{6}" \
    app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt \
    app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt \
    app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt
（无命中，exit=1）
```

颜色全部取自 `ComponentTokens` / `InkTokens`；尺寸 / 时长 / 曲线取自 `HalfSheetAnchors` / `ComponentPalette`。

### 3.4 禁用模式扫描（linear 缓动 / 菊花 / Material elevation / ripple）

三文件源码仅注释中提及禁令；无 `LinearEasing`、无 `CircularProgressIndicator`、无 `elevation`、无 `MaterialTheme`、无 ripple 实例。加载态为三金点 `InkHalfSheet.kt:395-419`（`repeat(3)` `:397`，keyframes `durationMillis=600` `:403-404`）。

### 3.5 单测覆盖

`HalfSheetMapperTest.kt` 用例：

| 行号 | 用例 |
| --- | --- |
| `:60` | 人物五节固定顺序且模型自检通过 |
| `:77` | 本段行为首行加粗、取 body 散行 |
| `:85` | 时间轴取生卒 + 事迹；档案剔除小传 / 事迹 |
| `:99` | 同名 0 人隐藏第五节；有名则标题计数、链接对齐 query |
| `:116` | 协议缺字段各分区显示「无载」，不留空白态 |
| `:129` | 字词四节固定顺序；音义汇总拼音 / 部首 / 笔画 / 义项 |
| `:165` | 普通多字词无标签释义归入音义节，不丢行 |
| `:184` | 古籍用例上限三条，超出截断 |
| `:200` | 用例空出引导；说文无载出占位；常见词出引导 |
| `:217` | 地名 / 未识等非两套形态返回 null，走自定义槽 |
| `:248-251` | §5.4 开合前后快照全等 |
| `:255-260` | §5.4 页码 / 偏移 / 下划线任一变化快照即不等 |

既有测试未被破坏：`HalfSheetAnchorsTest.kt`、`HalfSheetContentTest.kt`、`HalfSheetBenchTest.kt`、`HalfSheetBenchmark.kt` 保持原样；`InkBubbleBenchmarkTest.kt` 不引用被删私有 Sheet。

## 四、删除清单（`InkBubble.kt`）

1. 私有 Composable `InkBubbleDetailSheet`（原独立窗口自绘 Sheet：自有遮罩 / 拖拽 / 金杠 / 标题 / 正文 / 出处整段移除）。
2. Sheet 私有常数：`SHEET_MAX_HEIGHT_FRACTION`、`SHEET_DISMISS_PX`、`SHEET_FLING_VELOCITY`。
3. 随之失效的 import：`background`、`clickable`、`Orientation`、`detectTapGestures`、`draggable`、
   `rememberDraggableState`、`MutableInteractionSource`、`fillMaxSize`、`heightIn`、`width`、
   `CircleShape`、`Dialog`、`DialogProperties`、`pointerInput`、`rememberCoroutineScope`、
   `kotlinx.coroutines.launch`、`clip`。
4. 未保留旧格式兼容层：旧私有 Sheet 的视觉口径（绢帛磨砂底 / 圆角金杠）不再有第二份实现，
   人物 / 字词详情统一走 `InkHalfSheet` 纸白容器。

## 五、未验证项与协议缺口（未编译、未真机跑）

1. **未本地编译**：按红线未执行 Gradle 编译与单测。以上结论为静态自检（括号 / 花括号配平、
   符号引用回查、token 存在性核对）；编译通过与否、JVM 单测是否全绿需后续 CI / 本机复测，
   本报告不冒充编译结论。
2. **Compose 渲染与动效未真机验证**：250ms / 200ms 时长、600ms 金点、拖拽回弹手感、60% 高度
   实测占比、遮罩暗化观感，需荣耀 WIN 真机或模拟器复测。
3. **协议缺口（help.bubble 域，本包未改 Provider，仅在映射层迂回兜底）**：
   - §5.2②「同场人物」：`BubbleContent` 无同场人物字段，当前该节显示「无载」，未猜人名；
   - §5.2③「首次出场 / 末次」：协议仅有生卒与事迹，时间轴以生卒行 + 事迹行呈现，缺出场节点；
   - §5.3④「以该字起首的常见词」：协议未携带前缀词表，当前出引导文案「暂无常见词」；
     待 Provider 补字段后，`HalfSheetMapper.buildWord` 直接消费即可，渲染层零改动。
4. **backdrop blur 平台边界**：`Modifier.blur` / RenderEffect 模糊的是图层自身内容，并非截取背后
   背景的 backdrop blur；背景截获式毛玻璃需窗口层（window blur behind）统一 mediate，口径同
   `InkFrost.kt:23-24` 既有边界说明，未伪造 backdrop 采样。
5. **六态口径**：`ComponentVisualState` 七值（默认 / 悬浮 / 点击 / 禁用 / 加载 / 空 / 离线）均在
   `InkHalfSheet.kt:303-321` 分派；当前气泡链路同步产出的内容恒为默认态，加载 / 空 / 离线态的
   真实注入路径待宿主状态接线后复测。
6. **原生槽候选链接未接线**：地名 / 纪年等走 `BubbleRawDetail` 原生槽的形态，其 `candidates`
   （如同名地名）当前只在气泡卡片候选切换行暴露，原生槽内未渲染可点候选行；待该类形态补齐
   固定分区或原生槽链接回调后再接线，本包不擅自扩协议。

## 六、旧 `ui/main/DetailSheet.kt` 引用清单（本包不删、不改）

旧版 `BottomSheetDialogFragment` 详情页仍被竖排自绘旧路径引用，按任务要求本包不删除、不改造：

| 文件 | 行号 | 引用 |
| --- | --- | --- |
| `app/src/main/java/io/legado/app/ui/main/DetailSheet.kt` | `:35` | `class DetailSheet : BottomSheetDialogFragment()` |
| 同上 | `:43` | 工厂 `char(name)` |
| 同上 | `:47-48` | 工厂 `person(name, anchorYear, excerpt)` |
| 同上 | `:57` | 工厂 `year(year)` |
| 同上 | `:63` | 工厂 `place(hit)` |
| 同上 | `:338` | 内部 `switchYear` 调 `DetailSheet.year(y).show(...)` |
| `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt` | `:586` | `DetailSheet.year(y2).show(fm, "detail_year")` |
| 同上 | `:618` | `DetailSheet.person(...).show(fm, "detail_person")` |
| 同上 | `:640` | `DetailSheet.place(nm).show(fm, "detail_place")` |
| 同上 | `:1378` | `DetailSheet.char(ch).show(fm, "detail_char")` |

> 交接：旧 `DetailSheet` 收口到统一 `InkHalfSheet` 属于 `ContentTextView` / 旧竖排路径域，
> 不在本包文件域内；上述引用待对应模块改造时逐条迁移，本报告仅登记，不代为删除。
