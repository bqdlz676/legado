# 修复-B1：点字详情改走统一 InkHalfSheet（阅读链路贴皮拆除）

> 锚点卡：docs/锚点/B1-点字半屏Sheet.md
> E3 结果：✓ 全量 grep 回证——阅读域 `DetailSheet` 命中 0、`openReaderSheet` 命中 5、纯 JVM 单测 9 条（未编译，等 CI）

> 任务编号：B1（第二单：阅读页调用点接线，非上一单 `修复-DetailSheet贴皮.md` 的 Fragment 内换皮）
> 仓库：`bqdlz676/legado`　｜　工作树改动，未做任何 git add / commit / push
> 范围铁律：只改 `ui/book/read/**`、`ui/main/DetailSheet.kt`（本单未删，残留清单见 §6）；未碰 `ui/components/InkHalfSheet.kt` 实现、`ui/main` 其它文件、`ui/shelf`、`crossing`、`globe`。

## 1. 任务与结论

旧路径：阅读页 `ContentTextView` 点字 → 取 `FragmentManager` 弹 Material `BottomSheetDialogFragment`（`ui/main/DetailSheet.kt`），人物 / 纪年 / 字词 / 地名四入口均走旧 Fragment。

本单接线：View 层不再持有任何 Dialog / Fragment 引用，只把命中结果打包成纯数据 `ReaderDetailRequest`，经 `ContentTextView.CallBack.openReaderSheet` 抛给宿主 Compose 层；宿主 `ReadBookActivity` 挂载 Compose 覆盖层 `ReaderSheetOverlay`，按入口类型分派四个独立调用点，各自渲染统一自绘 `InkHalfSheet`（`dialog = true` 跨窗口覆盖层模式）。竖排主链路（`ink/InkBubble.kt`）原本已走 `InkHalfSheet`，本单未改动其内部。

编译结论：**未验证，等 CI**（沙箱无 gradle / JDK21 / SDK，本地未跑 `./gradlew` / `kotlinc`）。

## 2. 改动清单（文件:行 + before/after）

### 2.1 新增文件

| 文件 | 行数 | 职责 |
|---|---|---|
| `app/src/main/java/io/legado/app/ui/book/read/page/ReaderDetailRequest.kt` | 50 | 纯数据请求模型：`ReaderDetailKind`（CHAR / PERSON / YEAR / PLACE）+ `ReaderDetailRequest`（token / year / excerpt / detail / source / placeOthers）。不引用任何 Android / Compose 类型。 |
| `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetGuard.kt` | 48 | 纯 JVM 开合快照比对守卫：记录弹起前 / 关闭后两份 `ReaderSnapshot`，`restoredExactly` 委托 `isRestoredExactly` 逐字段比对；未打开 / 未关闭返回 null。 |
| `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetState.kt` | 50 | Compose 可观察状态持有者：`request` 组合状态 + 委托 `ReaderSheetGuard`；`open(request, before)` / `dismiss(after)` / `restoredExactly`。 |
| `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetOverlay.kt` | 175 | 覆盖层 Composable：`ReaderSheetOverlay(state, onDismiss, onLinkSelected)`；按 kind 分派 `PersonSheetBody` / `YearSheetBody` / `CharSheetBody` / `PlaceSheetBody`，四个私有 Composable 各持一个 `InkHalfSheet` 调用点；正文槽 `DetailBodyLines` 逐行渲染，无载出 `HalfSheetFallback.MISSING_TEXT`。 |
| `app/src/test/java/io/legado/app/ui/book/read/ReaderSheetGuardTest.kt` | 99 | 纯 JVM 单测，9 个用例（见 §4）。 |

### 2.2 修改文件

#### `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`（+41 / −48）

- 接口 `ContentTextView.CallBack`（现 :1943）新增 `fun openReaderSheet(request: ReaderDetailRequest)`；全仓唯一实现方为 `ReadBookActivity`（已 grep 确认无第二实现者）。
- 纪年入口（旧 :586，现 :584-591）
  - before：`val fm = (context as? FragmentActivity)?.supportFragmentManager; if (fm != null) DetailSheet.year(y2).show(fm, "detail_year")`
  - after：先保留旧工厂内的 `ContextAnchor.setYear(y2)` 锚定副作用，再 `callBack.openReaderSheet(ReaderDetailRequest(YEAR, chronoHit.title, year=y2, detail=chronoHit.detail))`。
- 人物入口（旧 :618，现 :618-627）
  - before：fm 分支 `DetailSheet.person(nm.name, nm.anchorYear, lineText).show(...)`；fm 为空时 `startActivity(PersonDetailActivity)`。
  - after：`callBack.openReaderSheet(ReaderDetailRequest(PERSON, nm.name, year=nm.anchorYear, excerpt=lineText, detail=body))`。
- 地名入口（旧 :640，现 :631-638）
  - before：fm 分支 `DetailSheet.place(nm).show(...)`；fm 为空时 `startActivity(PlaceMapActivity)`。
  - after：`callBack.openReaderSheet(ReaderDetailRequest(PLACE, nm.name, detail=nm.detail, placeOthers=nm.others))`。
- 字词入口（旧 :1378，现 :1358-1367）
  - before：`DetailSheet.char(ch).show(fm, "detail_char")`。
  - after：`callBack.openReaderSheet(ReaderDetailRequest(CHAR, ch, detail=meaningFinal))`。
- 清理：移除不再使用的 `android.app.Activity`、`android.content.Intent` 两个 import。

#### `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`（+62 / −0）

- import：`android.view.ViewGroup`、`androidx.compose.ui.platform.ComposeView`、`ReaderDetailRequest`、`ReaderSheetOverlay`、`ReaderSheetState`、`ReaderSnapshot`。
- 字段（:318）：`private val readerSheetState = ReaderSheetState()`。
- `onActivityCreated`（:537）：`attachReaderSheetOverlay()`。
- `override fun openReaderSheet(request)`（:1549-1551）：采弹起前快照 → `readerSheetState.open(request, before)`。
- `attachReaderSheetOverlay()`（:1557-1575）：`ComposeView` 经 `window.addContentView` 挂 MATCH_PARENT 覆盖层；`setContent { ReaderSheetOverlay(state, onDismiss = { readerSheetState.dismiss(captureReaderSnapshot()) }) }`。覆盖层自身不画背景，遮罩与 Sheet 由 `InkHalfSheet` dialog 模式独立窗口承载。
- `captureReaderSnapshot()`（:1582-1591）：只读采集 `ReaderSnapshot`（chapterIndex=`ReadBook.durChapterIndex`、segmentOffset=`ReadBook.durChapterPos`、scrollPosition=`binding.readView.scrollY`、underlineVisible=`ReadBook.highlights.any { it.bookUrl == bookUrl }`、selectedSegments=空集）；采集过程不回写任何阅读状态。

## 3. grep 回证（命令 + 输出原文）

### 3.1 阅读域旧类名命中 0（判据 1 前半）

命令：`grep -rn "DetailSheet" app/src/main/java/io/legado/app/ui/book/read/`

输出：（空）；`echo exit=$?` → `exit=1`（grep 无命中）。

### 3.2 阅读域 InkHalfSheet 命中 ≥1（判据 1 后半）

命令：`grep -rln "InkHalfSheet" app/src/main/java/io/legado/app/ui/book/read/`

输出：

```
app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt
app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt
app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt
app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetOverlay.kt
app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetState.kt
app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt
app/src/main/java/io/legado/app/ui/book/read/page/ReaderDetailRequest.kt
```

### 3.3 三入口调用点（人物 / 纪年 / 字词，另加地名）

命令：`grep -rn "ReaderDetailKind\." app/src/main/java/io/legado/app/ui/book/read/`

输出：

```
app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetOverlay.kt:42:        ReaderDetailKind.PERSON -> PersonSheetBody(request, onDismiss, onLinkSelected)
app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetOverlay.kt:43:        ReaderDetailKind.YEAR -> YearSheetBody(request, onDismiss, onLinkSelected)
app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetOverlay.kt:44:        ReaderDetailKind.CHAR -> CharSheetBody(request, onDismiss, onLinkSelected)
app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetOverlay.kt:45:        ReaderDetailKind.PLACE -> PlaceSheetBody(request, onDismiss, onLinkSelected)
app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt:586:                                                    kind = ReaderDetailKind.YEAR,
app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt:620:                                                            kind = ReaderDetailKind.PERSON,
app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt:633:                                                            kind = ReaderDetailKind.PLACE,
app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt:1362:                        kind = ReaderDetailKind.CHAR,
```

### 3.4 全局旧类残留（判据 2：未达 grep=0 删类条件）

命令：`grep -rln "DetailSheet" app/src/main/java`

输出：

```
app/src/main/java/io/legado/app/ui/main/DetailSheet.kt
app/src/main/java/io/legado/app/ui/main/detail/DetailSheetContent.kt
app/src/main/java/io/legado/app/ui/main/detail/DetailSheetState.kt
```

`app/src/main/res/`、`app/src/test/java` 下无 `DetailSheet` 引用（grep exit=1）。

### 3.5 风格约束回证

命令：`grep -n "0x\|#[0-9A-Fa-f]" app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheet*.kt app/src/main/java/io/legado/app/ui/book/read/page/ReaderDetailRequest.kt` → 空（无新增裸 hex）。

命令：`grep -n "MaterialTheme\|ripple\|elevation\|Elevation" ReaderSheetOverlay.kt` → 仅 KDoc 说明文字（「不套 MaterialTheme / 不用 ripple / elevation」），代码内无 Material 主题 / ripple / elevation 调用；颜色只取 `ComponentTokens.textPrimary / textSecondary / textTertiary`。

## 4. 单测清单

文件：`app/src/test/java/io/legado/app/ui/book/read/ReaderSheetGuardTest.kt`（纯 JVM，org.junit，不引用 Android / Compose 类型；函数名均为合法 camelCase，无反引号、无 `. : /` 等非法字符）。

| 测试函数 | 覆盖口径 |
|---|---|
| `snapshotEqualAfterOpenDismissReturnsTrue` | 打开 → 关闭，五项字段全等 → `restoredExactly = true`（主路径） |
| `snapshotMismatchScrollPositionReturnsFalse` | 滚动位置差 1px → false |
| `snapshotMismatchChapterIndexReturnsFalse` | 章序号差一 → false |
| `selectedSegmentsMismatchReturnsFalse` | 已选段集合少一项 → false |
| `underlineVisibilityFlipReturnsFalse` | 下划线可见性翻转 → false |
| `undismissedReturnsNull` | 只打开未关闭 → null（不可判定） |
| `neverOpenedReturnsNull` | 未打开 → null |
| `reopenClearsPreviousAfterSnapshot` | 二次打开清掉上一轮关闭后快照；未再次关闭前 null；第二次关闭偏移漂移 → false |
| `emptySnapshotRoundTripReturnsTrue` | `ReaderSnapshot.Empty` 往返 → true |

## 5. 未验证项

1. 编译：**未验证，等 CI**。沙箱禁本地编译（无 gradle / JDK21 / SDK），未跑 `./gradlew`、未跑 `kotlinc`；单测是否全绿以 CI 为准。
2. 真机交互：气泡定位、Sheet 弹起下沉 8dp / 暗化 40% / 60% 最大高 / 下拉关闭等观感由既有 `InkHalfSheet` 组件保证，本单未在真机复核。
3. 遗留横排通道（`ReadBookActivity`，manifest 标注「Legacy 横排通道，降级备选保留」）当前无入口启动（书架 / 详情页均落 `InkReadActivity`），其 Compose 覆盖层挂载路径未在真机走查。

## 6. 交接项（发现但不在本单范围，归属模块列明）

1. **旧类残留待清剿（归属 `ui/main`，跨模块下一单）**：`ui/main/DetailSheet.kt`（DialogFragment 壳，阅读链路已零调用）与 `ui/main/detail/DetailSheetContent.kt`、`DetailSheetState.kt`（含 `DetailSheetStateBuilder` 四型采集门面）。门禁要求全局 `grep DetailSheet` 为 0 才删 Fragment；本单文件域禁改 `ui/main` 其它文件，故保留。下一单需：先确认 `DetailSheetStateBuilder / DetailSheetContent / DetailSheetState` 无其它引用 → 整体清剿 `detail/` 包 → 再删 `DetailSheet.kt` 及其专属 fragment 文件。
2. **遗留通道全屏动作出口未接（归属 `ui/book/read/ink` 动作分派 + `ui/main` 全屏页）**：旧人物 / 地名分支在 fm 为空时跳 `PersonDetailActivity` / `PlaceMapActivity`；新半屏覆盖层无动作行，该全屏出口在遗留横排通道未复刻。竖排主链路由 `InkActionDispatcher` 经 `OpenPerson / OpenMap` 分发全屏页，不受影响。
3. **遗留通道快照 `selectedSegments` 置空集（归属 `ReadBookActivity` 选中态）**：横排文字选中模型未接入 `ReaderSnapshot.selectedSegments`；下划线可见性已采，选中段集合待选中态模型补齐。
4. **纪年时间轴拖年份重开未复刻（归属 ink 壳 / 纪年模块）**：旧 `DetailSheet.switchYear` 可在时间轴拖到新十年后按新年份重开同型 Sheet；遗留覆盖层当前只渲染既有对照正文。竖排链路纪年动作走 `OpenTimeline`。
5. **遗留通道未走 §5.2 / §5.3 固定分区模型（归属遗留通道补强）**：人物 / 字词在遗留覆盖层走自定义正文槽（摘录 + 释义逐行），未构造 `HalfSheetContent` 分区列表；竖排 `InkBubble` 链路已由 `HalfSheetMapper` 按 kind 分派两套固定顺序，本单不重复建设。
6. **`MapWebHolder.detach()` 生命周期钩子未迁移**：旧 Fragment `onDestroyView` 调 `MapWebHolder.detach()`；新覆盖层 dialog 模式下暂无对应 detach 钩子，待地图 Web 持有方接入覆盖层生命周期时补齐（归属地图 / 沙盘模块）。

## 7. DoD 逐条自检

1. **阅读域 DetailSheet 命中 0、InkHalfSheet 命中 ≥1、三入口各有调用点**：满足。§3.1 grep 阅读域 `DetailSheet` 空命中（exit=1）；§3.2 `InkHalfSheet` 7 文件命中；§3.3 PERSON / YEAR / CHAR 三入口 + PLACE 各有分派调用点与 View 侧请求构造点。
2. **关闭 Sheet 后阅读位置 / 滚动 / 下划线状态不变**：满足（设计口径）。Sheet 为独立窗口覆盖层，不读写宿主滚动 / 选中 / 高亮；快照由宿主在开合前后只读采集，比对走 `ReaderSheetGuard` + `isRestoredExactly`；遗留通道 `selectedSegments` 暂置空集（交接项 3）。
3. **纯 JVM 单测覆盖「打开→关闭→状态字段相等」，函数名合法**：满足。§4 主路径用例 `snapshotEqualAfterOpenDismissReturnsTrue` 断言全等为真，另含 8 例漂移 / 边界；函数名 camelCase，无反引号非法字符。
4. **报告文档**：本文件即 `docs/修复-B1-点字半屏Sheet.md`，含改动清单、grep 回证、单测清单、未验证项、交接项。
5. **风格约束**：满足。无新增裸 hex；无 Material 主题 / ripple / elevation；颜色只引 `ComponentTokens`；未改 `InkHalfSheet.kt` 实现；未碰 `ui/main` 其它文件、`ui/shelf`、`crossing`、`globe`；本地未编译；未做 git 写操作。
