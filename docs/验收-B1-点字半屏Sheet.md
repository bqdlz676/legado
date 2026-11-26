> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=6ab072c0e6de；文档=6dd8114a6a74）并复核 3 条结论，漂移 2 条（见 回执补录-批3-阅读器贴皮 §三）
> 回执缺失（规则上线前交付，留档）

# 验收 · B1 点字详情改走 InkHalfSheet（独立验收代理，只查不改）

锚点卡：docs/锚点/B1-点字半屏Sheet.md

- 锚点卡：`docs/锚点/B1-点字半屏Sheet.md`
- 被验修复报告：`docs/修复-B1-点字半屏Sheet.md`
- 问题清单 B1 行：`docs/问题清单.md:50` 「**B1** ｜ **点字弹出的仍是旧 Material `BottomSheetDialogFragment`（`DetailSheet`），不是统一 `InkHalfSheet`** ｜ `ui/book/read/page/ContentTextView.kt:586 / 618 / 640 / 1378`；`ui/main/DetailSheet.kt:35` ｜ 🔴 ｜ 阅读链路主贴皮，规格「界面全部原生自绘」；气泡/人物/纪年三入口必须走 `InkHalfSheet` + `InkBubble`，旧类删除」

## 〇、验收口径与基线

- 被验提交：`69ef748`（"修B1:点字三入口改走InkHalfSheet覆盖层,阅读域旧DetailSheet清零"）。验收时现役 HEAD 为 `4bee332`；`git merge-base --is-ancestor 69ef748 HEAD` 返回 ancestor=yes；`git diff 69ef748 HEAD --stat -- app/src/main/java/io/legado/app/ui/book/read/ app/src/test/java/io/legado/app/ui/book/read/` 输出为空，即被验代码与现役树一致。
- 红线遵守：本次验收只读代码与文档，未改源码 / 测试 / 门禁 / 已有报告；未做 `git add/commit/push`；本地未编译、未跑测（沙箱无 gradle / JDK21 / SDK），不假设 `a574f3b` 的 Verify 已绿；编译与单测结果一律标「未验证，等 CI」。
- 被验方自述仅作待验主张，下列结论全部以现行代码 `文件:行` 与 grep 回证为准。

## 总结论：判低（PARTIAL，含 1 条 FAIL）

- 判据 1（半屏真实接管）：PASS。
- 判据 2（关闭与返回正确）：代码层 PASS，真机交互未验证；遗留通道选中段快照恒空，记缺陷 D3。
- 判据 3（唯一入口守卫 / 重复弹层收敛）：FAIL。守卫类只做快照比对，不做开合去重；`open` 路径无任何「已开则拒」闸。
- 判据 4（状态单一）：PASS。
- 判据 5（与既有 InkHalfSheet 契约一致）：PARTIAL。组件确为复用，但存在三处偏离（离场动画被砍、固定分区模型未接、候选链接未接线），其中第一处判为缺陷 D2。
- 判据 6（单测对应）：PARTIAL。9 条断言全部映射到判据 2 的快照比对；判据 1 / 3 / 4 / 5 无任何 JVM 覆盖。
- 判据 7（二阶影响）：静态 grep 层面 PASS，无遗漏旧路径调用点；真机手势抢占未验证。

## 一、判据 1：半屏真实接管 —— PASS

- grep 回证命令：`grep -rn "DetailSheet" app/src/main/java/io/legado/app/ui/book/read/`
- 命令输出：空；`echo exit=$?` → `exit=1`（grep 无命中）。阅读域内旧 `DetailSheet` 类名命中数为 0。
- grep 回证命令：`grep -rln "InkHalfSheet" app/src/main/java/io/legado/app/ui/book/read/`
- 命令输出（7 个文件）：
  - `app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetOverlay.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetState.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/page/ReaderDetailRequest.kt`
- 三入口 + 地名分派点（grep `ReaderDetailKind\.`）：
  - 分派侧 `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetOverlay.kt:42`（PERSON）、`:43`（YEAR）、`:44`（CHAR）、`:45`（PLACE）。
  - View 侧请求构造：`ContentTextView.kt:586`（YEAR）、`:620`（PERSON）、`:633`（PLACE）、`:1362`（CHAR）。
- 旧路径拆除回证：`git show 69ef748 -- ContentTextView.kt` 中删除行含 `DetailSheet.year(y2)`、`DetailSheet.person(...)`、`DetailSheet.place(...)`、`DetailSheet.char(ch).show(fm, "detail_char")`，以及两处 `PersonDetailActivity::class.java` / `PlaceMapActivity::class.java` 的 `startActivity` 分支；同时删除 `android.app.Activity`、`android.content.Intent` 两个 import。
- 复核：现行 `ContentTextView.kt` 内残留的 `showDialogFragment` 仅 `:481`（PhotoDialog 图片）、`startActivity` 仅 `:525`（OpenUrlConfirmActivity 链接），均不属于点字详情路径。
- 判据结论：旧 Material Fragment 弹窗在阅读域已清零，四个入口全部改走 `InkHalfSheet`；锚点卡 §三-1 前半与后半均满足。

## 二、判据 2：关闭与返回正确 —— 代码层 PASS，真机未验证

### 2.1 三条关闭路径（代码行证据）

- 遮罩点击关闭：`app/src/main/java/io/legado/app/ui/components/InkHalfSheet.kt:217-221`，遮罩 Box 经 `rememberInkGesture(enabled = true, onClick = onDismiss).gestureModifier` 接收点击。
- 下拉手势关闭：`InkHalfSheet.kt:257-272`，金杠 `HalfSheetGrabber` 挂 `draggable(Orientation.Vertical)`，`onDragStopped` 内 `HalfSheetAnchors.shouldDismiss(fraction, velocity)` 命中即 `onDismiss()`，回弹走 `onResetDrag()`。
- 系统返回键：`InkHalfSheet.kt:157-163`，`Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false))`；`backDismissEnabled` 未被覆盖，按 Compose `DialogProperties` 默认开启返回键触发 `onDismissRequest`。该默认行为真机未走查，列未验证项。
- 覆盖层关闭回调接线：`app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt:1560-1566`，`onDismiss = { readerSheetState.dismiss(captureReaderSnapshot()) }`。

### 2.2 阅读进度 / 当前页状态保存在哪里、由谁只读

- 弹起前快照采集：`ReadBookActivity.kt:1582-1591` `captureReaderSnapshot()`，只读采集 `chapterIndex = ReadBook.durChapterIndex`、`segmentOffset = ReadBook.durChapterPos`、`scrollPosition = binding.readView.scrollY`、`underlineVisible = ReadBook.highlights.any { it.bookUrl == bookUrl }`、`selectedSegments = emptySet()`；函数体无任何对 `ReadBook` 字段 / 滚动 / 高亮集合的回写。
- 状态持有者：`app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetState.kt:36-45`，`open(request, before)` 与 `dismiss(after)` 只写自身 `request` 与守卫快照；KDoc `:41` 明示「宿主状态由宿主自身回写，本方法不改写任何阅读位置」。
- 守卫层：`app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetGuard.kt:27-35`，`open` / `dismiss` 仅记两份快照，不触碰阅读引擎。
- 比对口径：`app/src/main/java/io/legado/app/ui/components/ReaderSnapshot.kt:27-33` 五字段数据类；`:59-64` `isRestoredExactly` 逐字段全等才为 true。
- 覆盖层正文槽：`ReaderSheetOverlay.kt:126-175` `DetailBodyLines` 只渲染 `request` 内字符串，无滚动宿主、无选中 / 下划线写回调用。
- 判据结论：Sheet 为独立窗口覆盖层（dialog 模式，`InkHalfSheet.kt:81-83` 注释：宿主不被重排、不被重算 layout），阅读进度的唯一权威仍是宿主 `ReadBook` 与 `binding.readView`；Sheet 侧只读不改，代码层面满足切片-11 §5.4「关闭后阅读位置、滚动位置、下划线状态一字不差」。
- 缺口（记缺陷 D3）：`ReadBookActivity.kt:1590` `selectedSegments = emptySet()`，横排遗留通道未接选中态模型，选中段集合在真实路径上永远为空集，快照比对无法验证选中段是否还原；被验报告 §6 交接项 3 已自认。

## 三、判据 3：唯一入口守卫（重复弹层收敛）—— FAIL

### 3.1 逐行读守卫判据逻辑

- `ReaderSheetGuard.kt:62-48` 全类只有两个私有快照字段：`:20` `snapshotBefore: ReaderSnapshot?`、`:23` `snapshotAfter: ReaderSnapshot?`；方法仅 `open(before)`（`:27-30`）、`dismiss(after)`（`:33-35`）、`restoredExactly`（`:42-47`）。该类职责是开合前后快照比对，**没有任何可见性 / 弹层去重逻辑**，也不记录「当前是否已开」。
- `ReaderSheetState.kt:36-39` `open(request, before)`：先 `guard.open(before)`，再 `this.request = request`；函数体内无 `isOpen` 前置判断，不拒绝重复打开。`:32-33` 虽派生了 `isOpen: Boolean get() = request != null`，但全仓 grep `isOpen` 在阅读域仅命中该声明行本身，**零消费方**，没有任何路径用它做去重闸。
- `ReadBookActivity.kt:1549-1551` `override fun openReaderSheet(request)`：直接 `readerSheetState.open(request, captureReaderSnapshot())`，同样无「已开则忽略 / 排队」判断，无互斥锁。

### 3.2 非法 / 并发情形如何收敛

- 已开状态下第二次请求到达：`ReaderSheetState.open` 无条件覆盖 `request`；`ReaderSheetGuard.open`（`:27-30`）用新快照覆盖 `snapshotBefore` 并清空 `snapshotAfter`，第一次开合会话的弹起前快照丢失，`restoredExactly` 转而对照第二次会话的两份快照。
- 连点竞态：View 侧四个入口均在气泡回调内先 `dismissCharBubble()` 再 `openReaderSheet`（`ContentTextView.kt:583-591`、`:617-627`、`:630-638`、`:1359-1367`）。第一道闸是 Dialog 独立窗口截获宿主触摸（`InkHalfSheet.kt:157-163`），但窗口挂载与第二次触摸回调之间存在竞态窗口；代码内无 in-flight 布尔 / Mutex 兜底。
- 唯一客观收敛点：覆盖层只有一个组合槽位（`ReaderSheetOverlay.kt:40-46`），`InkHalfSheet` 内部 `Dialog` 单槽（`InkHalfSheet.kt:157-163`），同一时刻不会组合出第二个 Dialog；即「视觉上只开一层」依赖组件树单槽与窗口截获，而不是守卫层的显式去重。
- 判据结论：锚点卡与本单判据要求「拦住重复弹层，连点两次只开一层」并说明非法 / 并发收敛路径；现行实现无显式去重守卫，重复 `open` 会改写快照基线，竞态窗口无闸。**FAIL**。建议（交下一单，不在本验收改动）：`State.open` 入口加 `if (isOpen) return` 或替换请求并保留首份 `snapshotBefore`；补一条「打开中再 open 不产生第二层、before 快照不被覆盖」的 JVM 用例。

## 四、判据 4：状态单一 —— PASS

- 唯一可见性事实源：`ReaderSheetState.kt:29` `var request by mutableStateOf<ReaderDetailRequest?>(null)`，`null` 即关闭；`:32-33` `isOpen` 由 `request != null` 派生，不是第二份开关记录。
- 第二开关排查：grep `isSheetOpen|sheetVisible|showSheet|isReaderSheet|sheetShown`（阅读域 `app/src/main/java/io/legado/app/ui/book/read/`），`exit=1` 零命中。
- 守卫内 `snapshotBefore` / `snapshotAfter`（`ReaderSheetGuard.kt:20,23`）是开合快照记录，不是可见性 boolean；`restoredExactly`（`ReaderSheetState.kt:48-49`）是比对结果派生值，不参与开关。
- `ReadBookActivity.kt:318` 仅持一个 `readerSheetState` 实例；全仓 `ReaderSheetState` 实例化点仅此一处（grep 回证见判据 7）。
- 判据结论：半屏可见性只有 `request` 单一事实源，不存在第二个 boolean 记录开关。**PASS**。

## 五、判据 5：与既有 InkHalfSheet 契约一致 —— PARTIAL

### 5.1 复用既有组件（非另建一套）

- 四个 Sheet 体均调用共享组件 `io.legado.app.ui.components.InkHalfSheet`：`ReaderSheetOverlay.kt:56-64`（人物）、`:74-83`（纪年）、`:92-101`（字词）、`:110-118`（地名）。
- 被调签名与既有契约一致：`InkHalfSheet.kt:90-101` 形参表 `visible / onDismiss / modifier / state / title / source / model / dialog / host / content / onLinkSelected`；Overlay 侧传入的 `visible / onDismiss / dialog / title / source / content / onLinkSelected` 均为合法命名参数，未自创弹窗容器。
- dialog 模式复用正确：四处均 `dialog = true`（`ReaderSheetOverlay.kt:59,77,95,113`），与竖排主链路 `InkBubble.kt:304-311` 同一模式。
- 风格约束回证：grep `0x|#[0-9A-Fa-f]|MaterialTheme|ripple|elevation|Elevation` 四个新文件，仅命中 `ReaderSheetOverlay.kt:27-28` 的 KDoc 说明文字（「不套 MaterialTheme / 不用 ripple / elevation」），代码内无裸 hex、无 Material 主题 / ripple / elevation 调用；颜色取 `ComponentTokens.textPrimary / textSecondary / textTertiary`（`ReaderSheetOverlay.kt:143,153,166`）。

### 5.2 偏离点逐条列出

- 偏离 D2（判为缺陷）：`visible` 恒为 `true`，离场动画被砍。四处调用点 `visible = true`（`ReaderSheetOverlay.kt:57,75,93,111`）；关闭路径是 `dismiss()` 把 `request` 置 null（`ReaderSheetState.kt:42-45`），`ReaderSheetOverlay.kt:40` `val request = state.request ?: return` 直接摘除整个子树。`InkHalfSheet` 的离场动画依赖 `LaunchedEffect(visible)` 的 `visible=false` 分支（`InkHalfSheet.kt:119-128`）与 `:131` 的 `progress.value == 0f && !visible` 保留帧；子树被立即摘除后，Sheet 下降与遮罩回亮动画没有机会播放。对照竖排链路 `InkBubble.kt:305` 传 `visible = sheetOpen` 且组件保持组合、由内部动画收束——本单接线方式与该既有用法不一致。
- 偏离 D5（被验报告 §6 交接项 5 已自认）：未构造 `HalfSheetContent` 固定分区模型。四处调用 `model` 均缺省（null），正文走自定义槽 `DetailBodyLines`（`ReaderSheetOverlay.kt:126-175`）；锚点卡引文切片-11 §5.2（人物五段固定顺序）、§5.3（字词四段固定顺序）在横排遗留通道未落地。竖排链路由 `HalfSheetMapper.build(content)` 构造模型（`InkBubble.kt:310`），本单未重复建设，范围上可解释为遗留通道补强项，但与锚点「三入口必须走 InkHalfSheet + InkBubble」的完整口径存在差距。
- 偏离 D4：`onLinkSelected` 未接线。`ReadBookActivity.kt:1560-1566` 挂覆盖层时只传 `state` 与 `onDismiss`，未传 `onLinkSelected`；`ReaderSheetOverlay.kt:38` 形参默认 `{}`。地名同名候选行 `ReaderSheetOverlay.kt:161-173` 为纯 `BasicText`，未挂点击手势，候选点击在遗留通道为空操作。
- 非缺陷偏离：`host` 内嵌槽未使用。dialog 模式下契约本身不渲染 host 槽（`InkHalfSheet.kt:81-83`），宿主下沉 8dp 由内嵌模式承担，本场景不接 host 符合契约。

## 六、判据 6：单测对应（9 条断言逐条映射）—— PARTIAL

- 测试文件：`app/src/test/java/io/legado/app/ui/book/read/ReaderSheetGuardTest.kt`；`grep -c "@Test"` = 9。
- 用例 → 判据映射：
  1. `snapshotEqualAfterOpenDismissReturnsTrue`（`:27-33`）：打开→关闭五字段全等 → `restoredExactly = true`。映射判据 2 主路径。
  2. `snapshotMismatchScrollPositionReturnsFalse`（`:35-41`）：滚动位置 320→321 → false。映射判据 2 `scrollPosition` 字段。
  3. `snapshotMismatchChapterIndexReturnsFalse`（`:43-49`）：章序号 2→3 → false。映射判据 2 `chapterIndex` 字段。
  4. `selectedSegmentsMismatchReturnsFalse`（`:51-57`）：选中段集合少一项 → false。映射判据 2 `selectedSegments` 字段。
  5. `underlineVisibilityFlipReturnsFalse`（`:59-65`）：下划线可见性翻转 → false。映射判据 2 `underlineVisible` 字段。
  6. `undismissedReturnsNull`（`:67-72`）：只打开未关闭 → null（不可判定）。映射判据 2 / 判据 3 的不可判定边界。
  7. `neverOpenedReturnsNull`（`:74-78`）：未打开 → null。边界用例。
  8. `reopenClearsPreviousAfterSnapshot`（`:80-90`）：二次打开清上一轮 after 快照；未再关闭前 null；第二次关闭 `segmentOffset` 漂移 → false。部分触判据 3 的重开场景，但断言的是快照清理，**不测重复弹层去重**；`segmentOffset` 漂移仅在此例附带出现，无独立用例。
  9. `emptySnapshotRoundTripReturnsTrue`（`:92-98`）：`ReaderSnapshot.Empty` 往返 → true。空状态边界。
- 缺覆盖清单（对应判据）：
  - 判据 1（入口分派 / 旧路径清零）：无 JVM 测试，仅靠 grep 静态回证；四个 kind 的分派表（`ReaderSheetOverlay.kt:41-46`）无路由断言。
  - 判据 3（连点两次只开一层 / 打开中再 open 收敛）：零覆盖；缺陷 D1 无对应用例。
  - 判据 4（`request` 单源、无第二 boolean）：零覆盖。
  - 判据 5（InkHalfSheet 参数接线、dialog 模式、固定分区 / 候选链接）：零覆盖；Compose 层本就难做纯 JVM 断言，但至少应在可测的状态层补去重与路由用例。
  - `segmentOffset` 字段无独立漂移用例（仅用例 8 附带）。
- 判据结论：9 条断言全部服务于判据 2 的快照比对，判据 2 覆盖较全；其余判据缺覆盖，按验收口径判 **PARTIAL**。单测本身能否在 CI 全绿未验证（见未验证项）。

## 七、判据 7：二阶影响 —— 静态 PASS，真机手势未验证

### 7.1 全仓调用点排查（grep 回证）

- `ReaderDetailRequest` 引用点：`ReadBookActivity.kt:126`(import)、`:1549`；`ReaderSheetOverlay.kt:14,22,52,70,88,106,126`；`ReaderSheetState.kt:6,13,21,29,36`；`ContentTextView.kt:585,619,632,1361,1943`；定义文件 `ReaderDetailRequest.kt:42,42`。全部落在阅读域 `ui/book/read/` 内，域外无调用点。
- `ReaderSheetOverlay` 实例化点：全仓仅 `ReadBookActivity.kt:1560` 一处。
- `ReaderSheetState` 实例化点：全仓仅 `ReadBookActivity.kt:318` 一处。
- `openReaderSheet` 实现点：全仓仅 `ReadBookActivity.kt:1549`（`override fun`）；`ContentTextView.CallBack` 接口声明在 `ContentTextView.kt:1943`，接口实现类仅 `ReadBookActivity.kt:215`（`ContentTextView.CallBack`），无第二实现者。
- 旧路径残留：`grep -rln "DetailSheet" app/src/main/java app/src/test/java app/src/main/res` 命中三个文件——`ui/main/DetailSheet.kt:35`（DialogFragment 壳）、`ui/main/detail/DetailSheetContent.kt`、`ui/main/detail/DetailSheetState.kt`；在这三个文件之外再 grep `DetailSheetContent|DetailSheetState|DetailSheetStateBuilder|DetailSheet\.`，`exit=1` 零外部引用。即旧三文件当前已是死代码，但锚点卡 §三-2 要求全局 grep 为 0 才删 Fragment；本单文件域禁改 `ui/main`，保留待下一单清剿（缺陷 D6，被验报告 §6 交接项 1 已自认）。
- 阅读域内其余 Fragment / Activity 出口与本单无关：`HighlightStyleDialog.kt:29`（高亮样式，BottomSheetDialogFragment）、`ReadBookActivity.kt:765-770`（书源重导入）、`:1645`（高亮样式弹窗）、`:2465`；`InkActionDispatcher.kt:78,83` 竖排链路经 `OpenMap / OpenPerson` 跳 `PlaceMapActivity / PersonDetailActivity`，属竖排动作分派，未走旧 DetailSheet。

### 7.2 与滑动翻页 / 长按选字是否抢手势

- 打开态：`InkHalfSheet` dialog 模式起独立窗口（`InkHalfSheet.kt:157-163`），铺满全屏遮罩；宿主 `ContentTextView` 的翻页滑动与长按选字事件落在宿主窗口，Dialog 窗口在其上截获触摸，代码路径上不与宿主翻页 / 选字手势共用同一手势检测器。Sheet 内部：下拉关闭只挂在金杠 `HalfSheetGrabber`（`InkHalfSheet.kt:257-272`），正文区走 `verticalScroll`（`:290-293`），注释 `:256` 明示「下拉手势只走横杠，正文区交给竖滑滚动，二者不抢手势」。
- 关闭态：`ReaderSheetOverlay.kt:49` 在 `request == null` 时整体 `return`，组合树内无任何指针 Modifier；宿主翻页 / 选字不应被空覆盖层截获。但 `ComposeView` 经 `window.addContentView` 以 MATCH_PARENT 挂入（`ReadBookActivity.kt:1567-1574`），空 ComposeView 是否吞触摸事件属视图层行为，真机未验证，列未验证项。
- 遗留通道全屏动作出口未复刻：旧人物 / 地名分支在 fm 为空时跳 `PersonDetailActivity / PlaceMapActivity`；新覆盖层无动作行，该全屏出口在横排遗留通道缺失（被验报告 §6 交接项 2），竖排链路由 `InkActionDispatcher` 兜底，不受影响。

## 八、新发现缺陷清单

- D1（判据 3 FAIL）：重复弹层无显式守卫。`ReaderSheetState.open`（`ReaderSheetState.kt:36-39`）与 `ReadBookActivity.openReaderSheet`（`ReadBookActivity.kt:1549-1551`）均不判断 `isOpen`；打开中再次 `open` 会覆盖 `request` 与首份 `snapshotBefore`（`ReaderSheetGuard.kt:27-30`），连点竞态无布尔 / 互斥闸；`isOpen`（`ReaderSheetState.kt:32-33`）声明后零消费。
- D2（判据 5 偏离）：关闭离场动画被砍。`request` 置 null 后覆盖层子树被直接摘除（`ReaderSheetOverlay.kt:40`），`InkHalfSheet` 的 `visible=false` 离场动画分支（`InkHalfSheet.kt:90-128`）不会执行；对照 `InkBubble.kt:305` 保持组合并传 `visible = sheetOpen` 的既有写法。
- D3（判据 2 缺口）：遗留通道 `selectedSegments` 恒为空集（`ReadBookActivity.kt:1651`），选中段还原在真实横排路径上不可比对。
- D4（判据 5 偏离）：候选链接未接线。`onLinkSelected` 在 `attachReaderSheetOverlay`（`ReadBookActivity.kt:1560-1566`）未传，默认空操作；地名同名行（`ReaderSheetOverlay.kt:161-173`）点击无响应。
- D5（判据 5 偏离）：遗留通道未按切片-11 §5.2 / §5.3 固定分区模型渲染，人物 / 字词走自定义逐行槽（`ReaderSheetOverlay.kt:126-175`）。
- D6（二阶残留）：`ui/main/DetailSheet.kt`、`ui/main/detail/DetailSheetContent.kt`、`ui/main/detail/DetailSheetState.kt` 外部引用已为 0，属待清剿死代码；按锚点卡 §三-2 留待下一单，本单不动。

## 九、未验证项

- 编译与单测：本地禁编译（无 gradle / JDK21 / SDK），未跑 `./gradlew` / `kotlinc`；9 条 JVM 单测是否全绿以云端 CI 为准，不假设 `a574f3b` Verify 已绿。
- 真机交互：气泡定位、弹起下沉 8dp / 暗化 40% / 最大高 60% / 跟手下拉关闭、遮罩点击手感、系统返回键收 Sheet，均未在真机复核；这些观感由既有 `InkHalfSheet` 组件保证，本单仅接线。
- 横排遗留通道：`ReadBookActivity` 遗留横排通道的 Compose 覆盖层挂载路径（`ReadBookActivity.kt:1557-1575`）未在真机走查；空 ComposeView 是否拦截宿主触摸未验证。
- 动画时序：D2 所述离场动画被砍为代码静态推断（子树摘除时机 vs `LaunchedEffect` 动画分支），实际表现需 CI 出包后真机确认。

## 十、验收结论

- 接线主干成立：阅读域旧 `DetailSheet` 清零，四入口改走共享 `InkHalfSheet`，可见性状态单源，调用点无遗漏（判据 1 / 4 / 7 静态层面 PASS）。
- 不予通过项：判据 3 重复弹层守卫 FAIL（D1）；判据 5 存在 D2 / D4 / D5 三处偏离，其中 D2 影响关闭动效；判据 6 对判据 1 / 3 / 4 / 5 无测试覆盖。
- 处置建议：D1 先补状态层去重闸与对应用例；D2 改为保持组合、传 `visible = false` 走组件内离场动画；D3 / D4 / D5 随遗留通道补强单一并收口；D6 待全局 grep 清零后下一单清剿旧三文件。编译与真机验收待 CI 绿档后复验。
