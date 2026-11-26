> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 1 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B29-域外常量visible-config.md

# 验收-B29：config 域浮层可见性常量导致离场动画跳过（独立验收）

## 〇、验收元信息

- 验收对象：`docs/修复-B29-常量visible-config.md`（被验方自述，已逐字通读，含 §5 六条自检、§6 逐条判定 N1–N4、§7 二阶影响、§9 未验证项）。
- 锚点卡：`docs/锚点/B29-域外常量visible-config.md`（§一 事实锚点、§二 六条判据、§三 边界）。
- 清单来源：`docs/修复-B26-常量visible接线.md` §6.3 结论段第 19–31 条（config 域 13 条命名参数）。
- 被验提交：`14f646a`（"修B29:config域浮层两段式收口(13命名+8位置参+8间接受害,SheetExitController复用B26闸,7断言)"）；文档入库提交 `eaebb78`。验收基线：`14f646a~1`。
- 验收方式：只读代码、测试与 git 历史，静态逐条回证；每条结论附「文件:行」证据与本验收真跑的 grep 输出。未改源码 / 测试 / 门禁 / 既有报告；未执行 git add / commit / push；未本地编译、未跑单测（编译与 CI 结果标「未验证，等 CI」）。
- 总体结论：**九条判据静态层全部 PASS（判据 5、判据 8 各附 1 项观察项；判据 5 一处程序化 open 未过闸，风险低），无 FAIL。清单复核：基线 config 域命名参数 13 条与 B26 清单逐条对齐；位置参数 8 条（BackupSection 6、ThemeSection 2）属实且已全部改接派生 visible；全仓残留 22 条全部位于 config 域外，分类计数与自述一致。编译 / 7 例单测是否全绿、真机帧对齐与离场段触摸截获，未验证，等 CI 出包后复验。**

## 一、判据 1：config 域常量 visible 清零，全仓残留 22 条且全部在域外 —— PASS

判据要求：自己 grep `visible = true` 与位置参数形式，config 域命中应为 0；全仓残留应为 22 条且全部在 autoTask / source / dict / replace / HandleFileSheet 红线内。

1. 全仓命名参数计数（本验收真跑）：

```
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt | wc -l
```

原样输出：

```
22
```

2. config 域命名参数命中：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/config/ --include=*.kt
```

原样输出：空，`exit=1`（config 域零命中）。

3. config 域位置参数单行形式：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/config/ --include=*.kt
```

原样输出：空，`exit=1`。

4. config 域多行位置参数兜底：

```
grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/config/ --include=*.kt
```

原样输出：空，`exit=1`。

5. 22 条残留按目录归类（本验收真跑 `grep -rn ... | sed 's/:.*//' | sort | uniq -c`）：

```
      1 app/src/main/java/io/legado/app/ui/autoTask/AutoTaskEditScreen.kt
      2 app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt
      1 app/src/main/java/io/legado/app/ui/autoTask/ImportAutoTaskDialog.kt
      1 app/src/main/java/io/legado/app/ui/book/source/debug/SourceDebugScreen.kt
      1 app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt
     10 app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt
      1 app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt
      2 app/src/main/java/io/legado/app/ui/dict/rule/DictRuleScreen.kt
      1 app/src/main/java/io/legado/app/ui/replace/edit/ReplaceEditScreen.kt
      2 app/src/main/java/io/legado/app/ui/replace/ReplaceRuleScreen.kt
```

归类复核：autoTask 4、book/source 12（debug 1 + edit 1 + manage 10）、dict 2、replace 3（RuleScreen 2 + EditScreen 1）、components 红线 1，合计 22，与自述 §3.1 完全一致；22 条无一位于 `ui/config/`。算术复核：基线全仓 36 条 = B26 已修 1（HighlightNoteDialog，现 `HighlightNoteDialog.kt:115` 为 `visible = !exitingState.value` 派生）+ B29 收口 config 13 + 域外残留 22，账平。

6. 基线复核（`14f646a~1`）config 域命名参数 13 条，与 B26 清单第 19–31 条逐条对齐，行号零漂移：

```
git grep -n "visible\s*=\s*true" 14f646a~1 -- 'app/src/main/java/io/legado/app/ui/config/'
```

原样输出 13 行：ThemeListDialog.kt:102、ConfigActivity.kt:129、BottomBarSkinActivity.kt:237、WelcomeSection.kt:68、OtherSection.kt:174、OtherSection.kt:185、CoverFontSection.kt:75、SettingSheetHost.kt:16、SettingSheets.kt:54/:109/:154/:194/:235。与 B26 报告 §6.3 第 19–31 条文件:行一一对应，无虚构条目、无遗漏。

判定：PASS。

## 二、判据 2：位置参数 8 条覆盖属实，非「改名不改义」 —— PASS

判据要求：逐处核对 8 条位置参数（BackupSection 6、ThemeSection 2）改后行号，确认可见性来源真改为控制器派生，关闭路径真改为两段式，而不是参数换名、语义仍为常量直摘。

1. 基线位置参数实测（本验收真跑）：

```
git grep -nE "(Sheet|Host)\(true" 14f646a~1 -- 'app/src/main/java/io/legado/app/ui/config/'
```

单行命中 6 行：BackupSection.kt:125、:132、:193、:201，ThemeSection.kt:127、:141。另有 2 条多行位置参数（`MultiChoiceSheet(` 换行首参 `true,`），基线 `BackupSection.kt:138`、:154（`git show 14f646a~1:.../BackupSection.kt | sed -n '130,160p'` 可见两处 `true,` 首参）。合计 8 条，与自述 §6.2 一致。

2. 改后现役 8 条逐条核对（可见性实参均为控制器派生位，非常量）：

- BackupSection.kt:185 `TextEditSheet(textExit.visible, ...)`，基线 :125 `TextEditSheet(true, ...)`；onDismiss 基线 `{ textTarget = null }` 直摘，现役 :188 `{ textExit.beginExit() }`，槽位清空改由 :184 `SheetExitHost(textExit, onFinished = { textTarget = null })` 收束回调执行。
- BackupSection.kt:194 `NumberPickerSheet(numberExit.visible, ...)`，基线 :132；onDismiss 现役 :194 `{ numberExit.beginExit() }`，:193 收束清 `numberTarget`。
- BackupSection.kt:201-202 `MultiChoiceSheet(contentExit.visible, ...)`，基线 :138 多行 `true,`；onDismiss 现役 :210 `{ contentExit.beginExit() }`，:200 收束清 `contentMulti`。
- BackupSection.kt:218-219 `MultiChoiceSheet(ignoreExit.visible, ...)`，基线 :154；onDismiss 现役 :227 `{ ignoreExit.beginExit() }`，:217 收束清 `ignoreMulti`。
- BackupSection.kt:262 `ListPickSheet(restoreExit.visible, ...)`，基线 :193；onDismiss 现役 :266 `{ restoreExit.beginExit() }`，:261 收束清 `restoreNames`。
- BackupSection.kt:272 `TextEditSheet(pwExit.visible, ...)`，基线 :201；onDismiss 现役 :274 `{ pwExit.beginExit() }`，:271 收束清 `confirmLocalPw`。
- ThemeSection.kt:165 `NumberPickerSheet(numberExit.visible, ...)`，基线 :127；onDismiss 现役 :166 `{ numberExit.beginExit() }`，:164 收束清 `numberTarget`。
- ThemeSection.kt:184 `TextEditSheet(nameExit.visible, ...)`，基线 :141；onDismiss 现役 :189 `{ nameExit.beginExit() }`，:183 收束清 `themeNameTarget`。

3. 语义变更复核：基线 8 处 onDismiss 均为直接把槽位置 null / false（子树当场摘除，`InkBottomSheet` 离场分支永不执行）；现役 onDismiss 一律先 `beginExit()` 进离场段，槽位状态只在 `SheetExitHost` 收束回调里清空。可见性实参由字面量 `true` 改为 `xxxExit.visible`（控制器派生，OPEN 段 true / EXITING·REMOVED 段 false）。不是参数改名，两段式语义真实落地。

判定：PASS。

## 三、判据 3：Host 层写死 true 已改派生，下游私有 Sheet 实际受益 —— PASS

判据要求：`InkBottomSheetHost`（等价 host）内部不再写死 true，形参改为派生；下游 8 处私有 Sheet / host 有实际受益证据。

1. Host 薄封装改造：

- 基线 `SettingSheetHost.kt`（`git show 14f646a~1:.../SettingSheetHost.kt`）：函数签名无 `visible` 形参，函数体 `InkBottomSheet(visible = true, ...)` 内部写死。
- 现役 `SettingSheetHost.kt:15` 新增首形参 `visible: Boolean`；:20 `InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title, content = content)` 透传派生可见性，内部不再出现常量 true。

2. 下游 8 处间接受害点逐一回证（均在现役代码中把控制器 visible 透到 host / 私有 Sheet）：

- ThemeSection.kt:210 私有 `ColorPickerSheet` 收 `visible: Boolean`，:220 `InkBottomSheetHost(visible = visible, ...)`；颜色槽位 :171 接 `colorExit`，:173 传 `colorExit.visible`。
- ThemeSection.kt:239 私有 `BackgroundImageSheet` 收 `visible: Boolean`，:244 `InkBottomSheetHost(visible = visible, ...)`；背景图槽位 :194 接 `bgExit`，:196 传 `bgExit.visible`。
- BackupSection.kt:316 私有 `LanMenuSheet` 收 `visible: Boolean`，:317 `InkBottomSheetHost(visible = visible, ...)`；局域网菜单槽位 :279 接 `lanExit`，:281 传 `lanExit.visible`。
- BackupSection.kt:303 Qr 弹层 host：`InkBottomSheetHost(visible = qrExit.visible, ...)`，独立 `qrExit` 控制器。
- BottomBarSkinActivity.kt:236 菜单 host：`InkBottomSheetHost(visible = menuExit.visible, ...)`，接 `menuExit`。
- BackupSection.kt:238 备份模式 host：`InkBottomSheetHost(visible = modeExit.visible, ...)`，接 `modeExit`。
- BottomBarSkinAssignActivity.kt:211 配色 host：`InkBottomSheetHost(visible = paletteExit.visible, ...)`，接 `paletteExit`。
- WelcomeSection.kt:97-98 配图 host：`InkBottomSheetHost(visible = imageExit.visible, ...)`，接 `imageExit`。

3. 受益链证据：基线这 8 处经 `InkBottomSheetHost` 默认走内部写死 true，离场分支不可达；现役 host 形参由各槽位控制器派生，离场段 visible=false，`InkBottomSheet` 内部 `LaunchedEffect(visible)` 离场 tween 得以执行。全仓 `InkBottomSheetHost` 调用点 grep 复核：除定义处 `SettingSheetHost.kt:14` 外，8 处调用全部在 `ui/config/` 内且均传派生 visible，config 域外无该薄封装消费方（自述 §7.3 成立）。

判定：PASS。

## 四、判据 4：阶段机复用 B26 守卫，config 域无第二份枚举 —— PASS

判据要求：`SheetExitController` import 复用 `ReaderSheetExitPhase` / B26 闸；config 域内无第二份枚举定义、无第二套迁移规则。

1. 复用 import 行（本验收真跑）：

```
grep -n "import io.legado.app.ui.book.read.NoteDialogExitGate" \
  app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt \
  app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt
```

原样输出：

```
app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt:9:import io.legado.app.ui.book.read.NoteDialogExitGate
app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt:25:import io.legado.app.ui.book.read.NoteDialogExitGate
```

2. 枚举单源：`ReaderSheetExitPhase`（OPEN / EXITING / REMOVED）定义于 `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetExitGate.kt:14-17`；`NoteDialogExitGate.kt:3` import 该枚举，:22 / :27 / :35-36 / :44-45 / :50 / :54 / :61 全程引用其迁移规则。

3. config 域第二源排查（本验收真跑）：

```
grep -rn "enum class\|ReaderSheetExitPhase" app/src/main/java/io/legado/app/ui/config/ --include=*.kt
```

原样输出仅两条 `NoteDialogExitGate` 引用（ConfigActivity.kt:25、SheetExitController.kt:9）与注释提及，config 域内无 `enum class` 定义、无第二套 OPEN→EXITING→REMOVED 迁移规则。`SheetExitController.kt:32-67` 只做包装：:39 `visible`、:43 `isComposed`、:47 `canOpen`、:54 `open()`、:57 `beginExit()`、:63 `finishExit()` 全部转发守卫。

判定：PASS。

## 五、判据 5：离场段拒收重复打开 —— PASS（附观察项 O-1）

判据要求：每个打开点先过 `canOpen` 再 `open()`；给至少 5 处行号证据。

1. 打开闸证据（用户触发入口，先 `canOpen` 后 `open()`，本验收逐处读过上下文）：

- ConfigActivity.kt:90 `if (iconExit.canOpen) {` → :92 `iconExit.open()`。
- ThemeListDialog.kt:97 `if (exit.canOpen) {` → :99 `exit.open()`。
- BottomBarSkinActivity.kt:225 `if (menuExit.canOpen) {` → :227 `menuExit.open()`；:245 `if (confirmExit.canOpen) {` → :247 `confirmExit.open()`。
- BottomBarSkinAssignActivity.kt:186 `if (paletteExit.canOpen) { ...; paletteExit.open() }`；:190 同型第二处。
- BackupSection.kt:85 `if (textExit.canOpen) {` → :87 `textExit.open()`（:91/:93、:97/:99、:103/:105、:109/:111 同型；:115/:119 numberExit；:132/:134 pwExit；:141/:143 contentExit；:147/:149 lanExit；:153/:155 modeExit；:161/:163 restoreExit；:169/:171 ignoreExit）。
- OtherSection.kt:96 `if (textExit.canOpen) {` → :100 `textExit.open()`（:104/:108、:181/:185 同型；numberExit :116/:120、:132/:136、:140/:144、:148/:152、:168/:170、:174/:176、:192/:194）。
- ThemeSection.kt:115 `if (colorExit.canOpen) { ...; colorExit.open() }`（:118、:121、:124、:139、:142、:145、:148 同型；bgExit :127、:151；nameExit :131、:155；numberExit :85/:87、:92/:94）。
- WelcomeSection.kt:44 `if (timeExit.canOpen) {` → :46 `timeExit.open()`；:56/:58、:68/:70 imageExit。
- CoverFontSection.kt:68 `if (sizeExit.canOpen) {` → :70 `sizeExit.open()`。

2. 闸位语义回证：`NoteDialogExitGate.kt` `canOpen` 为 `phase != EXITING`（EXITING 段拒收，OPEN / REMOVED 放行）；`SheetExitController.kt:46-47` 透传。宿主槽位状态仍是在场段判重来源，两道闸串联不替代，口径同 B26。

3. 计数复核：config 域 `.open()` 调用共 47 处（不含控制器自身定义 `SheetExitController.kt:54`），其中 46 处位于 `if (xxxExit.canOpen)` 块内；剩余 1 处为观察项 O-1。

4. 观察项 O-1（不判 FAIL，记录待跟踪）：BackupSection.kt:301 `LaunchedEffect(lanSheet) { qrExit.open() }` 未过 `canOpen` 闸。该入口是状态机驱动的程序化登记（lanSheet 由 Closed/Menu 迁到 Qr 时触发），不是用户点击入口；复跑竞态窗口很窄——Qr 离场段内菜单不可达，无法再次发起 send。静态判断风险低，但严格按「每个打开点先过 canOpen」口径，此处闸位缺失。建议后续要么在 effect 内补 `if (qrExit.canOpen) qrExit.open()`，要么在报告中明确登记为状态机入口例外。被验方 §4.9 已如实写出该行，未谎称过闸。

判定：PASS（46/47 用户触发入口过闸；1 处程序化入口未过闸，见 O-1）。

## 六、判据 6：收束延时与组件离场时长同源 —— PASS

判据要求：`SheetExitHost` 的 200ms 等效延时收束须与组件离场时长常量同源；核对 HalfSheetModel 的 MASK / RISE 值，不一致判 FAIL 并给真实值。

1. 收束延时表达式：`SheetExitController.kt:95` `delay(ComponentPalette.DURATION_MASK.toLong())`，:96 `controller.finishExit()`，:97 `onFinished()` 回调宿主清槽位。

2. 常量同源核对：

- `ComponentPalette.kt:94` `const val DURATION_MASK: Int = 200`（注释「遮罩淡入淡出 200ms」）。
- `InkBottomSheet.kt:117-126`：离场分支 `progress.animateTo(0f, tween(ComponentPalette.DURATION_MASK, easing = ComponentTokens.maskEasing))`——config 域 host 包的就是 `InkBottomSheet`，其离场 tween 时长与收束延时引同一个 `DURATION_MASK` 常量，非魔法数。

3. HalfSheetModel（reader 域 `InkHalfSheet` 模型层）常量核对：`HalfSheetModel.kt:50` `RISE_DURATION_MS: Int = 250`（弹起入场 250ms）；:53 `MASK_DURATION_MS: Int = 200`（遮罩淡入淡出 200ms）。RISE 250ms 是入场升起时长，不属于离场段；config 域弹层全部走 `InkBottomSheet`（本验收 grep `InkHalfSheet` 在 config 域 `exit=1` 零命中），不消费 HalfSheetModel 常量。`InkBottomSheet` 离场为单段 tween 200ms，与 `DURATION_MASK` 一致；入场用 `ComponentTokens.sheetSpring()`（:119），不参与收束延时推算。

4. 口径边界：`InkBottomSheet` 未暴露动画结束回调，延时属等效估算，沿用 B21 / B26 同一口径；实际帧对齐待真机 / CI 出包复验（自述 §8、§9 已披露）。

判定：PASS。无时长不一致。

## 七、判据 7：不改项 N1–N4 合理性 —— PASS

判据要求：逐条读代码判断 N1（BackupSection:232-233 内联 Column）、N2（SettingList.kt:118 派生可见）、N3（三组同屏导航 / 任务直摘）、N4（HandleFileSheet:48 红线）是否站得住；N3 若实为「同一浮层的关闭按钮」则判 FAIL。

1. N1：`BackupSection.kt:232-233` `if (autoBackupSheet) { AutoBackupSheet(onDismiss = { autoBackupSheet = false }) }`。私有函数 `AutoBackupSheet` 定义于 :326；函数体首行 :331 即 `Column(...)`，全程未包 `InkBottomSheetHost` / `InkBottomSheet` / `InkHalfSheet`，无 `visible` 形参、无模态遮罩与拖拽手势、无可播的离场动画组件。基线复核（`git show 14f646a~1:.../BackupSection.kt:246/:251`）同为裸 Column 内联区块。判定：条件渲染的内联配置区块，不是浮层，两段式无挂载点，不改站得住。

2. N2：`SettingList.kt:112` `var sheetVisible by remember { mutableStateOf(false) }`；:114 行点击 `sheetVisible = true`；:118-119 `ListPickSheet(visible = sheetVisible, ...)`；:128 onDismiss `sheetVisible = false`。传入的是派生状态而非常量 true；子树在 `ListRowItem` 内常驻组合，离场路径由派生可见性驱动，`InkBottomSheet.kt:128` 保留帧条件 `progress.value == 0f && !visible` 负责收束。与 B26 已认可的派生 visible 口径一致，不属本单缺陷形态。判定：不改站得住。

3. N3：三组直摘按钮群逐条读上下文，均为「带副作用的动作 / 导航触发」，不是浮层自身的关闭按钮：

- BottomBarSkinActivity.kt:240 edit：`menuName = null; onEdit(name)`，随后 :151-156 `startActivity` 跳分配页；:241 export：`menuName = null; onExport(name)`，随后 :122-125 拉起导出契约；:242 share：`menuName = null; onShare(name)`，随后 :131 调系统分享；:243-244 delete：`menuName = null` 后 :245-247 打开 `confirmExit` 确认框。四者后果是关菜单后跳转下一级页面 / 系统能力 / 确认弹层，属同屏导航切换；菜单遮罩点击与拖拽关闭仍走 :236 `menuExit` 两段式。
- BackupSection.kt:242 本地备份、:249 WebDAV 备份：先 `backupModeSheet = false` 直摘，再 :243-246 / :250-253 `scope.launch` 异步执行备份任务；弹层不承担离场后保留内容的职责。遮罩 / 拖拽关闭走 :237 `modeExit` 两段式。
- BackupSection.kt:283 onSend：`lanSheet = LanState.Closed` 后 :284-286 异步 `lanPrepare` 成功迁 `LanState.Qr`；:290 onReceive：`lanSheet = Closed` 后 :291 调 `actions.scanQr` 跳扫码页。菜单→Qr / 扫码页属导航切换；菜单遮罩 / 拖拽关闭走 :279 `lanExit` 两段式。

复核结论：这些按钮各自携带明确下游动作（跳页 / 任务 / 下级弹层 / 系统能力），不存在「仅关闭本浮层」的纯关闭按钮语义；旧直摘与导航切换同期发生，没有停留在原屏播离场的交互诉求。N3 不构成「同一浮层关闭按钮被误判为导航」，不改站得住。附注：delete 分支是菜单→确认框的弹层间跳转，菜单自身未播离场动画，属有意保留的旧口径，若产品要求导航切换也播下降动画，需另派单评估「先离场再跳转」时序（自述 §6.4 末段已写明）。

4. N4：`ui/components/HandleFileSheet.kt:48` `InkHalfSheet(visible = true, onDismiss = onCancel, ...)`。基线与现役 :48 逐字相同（本验收 `git show 14f646a~1:.../HandleFileSheet.kt | sed -n '48p'` 与现役 `sed -n '48p'` 输出一致）；components 契约冻结红线，commit `14f646a` 对 components 目录零 diff（见判据 9）。判定：不改站得住。

判定：PASS。N1 / N2 / N3 / N4 四条均成立。

## 八、判据 8：7 例断言与判据映射 —— PASS（附观察项 O-2）

判据要求：7 例逐条对应判据；缺覆盖判 FAIL；反引号测试名禁含 `. : [ ] / ; < >`。

1. 测试计数与命名复核（本验收真跑）：

```
grep -c "@Test" app/src/test/java/io/legado/app/ui/config/settings/SheetExitControllerTest.kt
grep -nE '^\s*fun `[^`]*[.:\[\]/;<>][^`]*`' app/src/test/java/io/legado/app/ui/config/settings/SheetExitControllerTest.kt
```

原样输出：第一行 `7`；第二行空，`exit=1`（7 个测试名无门禁 ㉘ 禁字符）。

2. 7 例逐条映射（`SheetExitControllerTest.kt`）：

- :26 `初始为摘除态 不可见 子树不在 可接受新打开`：断言 `visible=false`、`isComposed=false`、`canOpen=true`、`isExiting=false`——覆盖 REMOVED 初始态。
- :36 `open 后在场态 可见 子树保留 可开`：`open()` 后 `visible=true`、`isComposed=true`、`canOpen=true`——覆盖 OPEN 在场态。
- :48 `关闭瞬间子树仍在且可见性已翻 false`：`beginExit()` 后 `isComposed=true`、`visible=false`、`isExiting=true`——覆盖两段式第一段（判据 2 / 离场段子树保留）。
- :61 `离场段拒收重复打开`：EXITING 段 `canOpen=false`；重复 `beginExit()` 空操作，子树仍在、visible 仍 false——覆盖判据 5 离场拒收与守卫幂等。
- :74 `动画收束后摘除子树`：`finishExit()` 后 `isComposed=false`、`visible=false`、`isExiting=false`、`canOpen=true`——覆盖两段式第二段（收束摘除）。
- :88 `空操作兜底 非在场开始离场不动状态 非离场收尾不迁移`：REMOVED 态 `beginExit()` 空操作；OPEN 态 `finishExit()` 空操作，状态不被错误迁移——覆盖守卫边界空操作。
- :102 `收束后重开回到在场态并可见`：REMOVED 后再次 `open()`，`visible=true`、`canOpen=true`、`isComposed=true`——覆盖收束后可重开会话。

3. 纯 JVM 复核：测试 import 仅 `mutableStateOf`、`NoteDialogExitGate`、JUnit Assert / `@Test`，无 Android / Compose UI 类型引用；被测 `SheetExitController` 包装的 `NoteDialogExitGate` 本身为纯 JVM 守卫（无 Android 引用）。

4. 观察项 O-2（不判 FAIL，记录待 CI 复核）：自述 §5 判据 5 称「`mutableStateOf` 在普通 JVM 单测可实例化，先例 `ReaderSheetStateTest.kt:15`」。本验收复核：`ReaderSheetStateTest.kt:15` 是注释文字，该测试体并未调用 `mutableStateOf()`；`ReaderShellStateTest.kt` 仅 import `androidx.compose.ui.geometry.Rect`；全仓 `app/src/test` 下没有既有的测试方法体真正调用 `mutableStateOf()`（`BookSourceEditLayoutTest.kt:45` 只是对源码字符串做断言）。`mutableStateOf` 属 compose-runtime 普通工厂函数，JVM 下可实例化这一点本身成立，但仓内缺同型先例背书；测试能否在单测 classpath 实例化成功，以云端 CI 为准。

判定：PASS。7 例齐全、映射清晰、命名合规；纯 JVM 先例引用不精确，见 O-2，编译 / 跑绿状态未验证。

## 九、判据 9：改动边界未越 —— PASS

判据要求：commit `14f646a` 只含 ui/config 与其测试；components / reader / 地图 / 名物库零改动。

1. 提交文件清单（本验收真跑 `git show --name-only --format= 14f646a`），共 13 个文件：

- main 12 个：`ui/config/BottomBarSkinActivity.kt`、`BottomBarSkinAssignActivity.kt`、`ConfigActivity.kt`、`ThemeListDialog.kt`、`sections/BackupSection.kt`、`sections/CoverFontSection.kt`、`sections/OtherSection.kt`、`sections/ThemeSection.kt`、`sections/WelcomeSection.kt`、`settings/SettingSheetHost.kt`、`settings/SettingSheets.kt`、`settings/SheetExitController.kt`。
- test 1 个：`app/src/test/java/io/legado/app/ui/config/settings/SheetExitControllerTest.kt`。

（说明：任务书表述「13 main 文件 + 2 文件」与提交实况略有出入；实况为 11 个 main 修改 + 1 个 main 新增控制器 + 1 个测试，共 13 文件。被验方自述 §3.9「13 个改动文件（11 main + 1 控制器 + 1 测试）」与实况一致。）

2. 红线目录 diff 复核：

```
git show --stat 14f646a -- app/src/main/java/io/legado/app/ui/components/ \
  app/src/main/java/io/legado/app/ui/book/ \
  app/src/main/java/io/legado/app/help/artifact/ \
  app/src/main/java/io/legado/app/ui/card/
```

原样输出：空（这些目录在提交内零改动）。`eaebb78` 仅含 docs 三个文件（修复报告、问题清单、问题清单附录），main 源集在 `14f646a` 之后无改动（`git diff --stat 14f646a HEAD -- app/src/main/` 输出空）。

3. 工作树复核：`git status --short | grep -v docs/` 输出 0 行，无未提交 main / test 残留改动。

判定：PASS。

## 十、新发现缺陷与观察项

- O-1（判据 5 附项，低风险）：`BackupSection.kt:301` `LaunchedEffect(lanSheet) { qrExit.open() }` 程序化登记未过 `canOpen` 闸。建议补闸或登记为状态机入口例外；当前静态判断竞态窗口窄，不阻塞本单。
- O-2（判据 8 附项，文档精度）：测试 `mutableStateOf` 的仓内先例引用不精确，`ReaderSheetStateTest.kt:15` 为注释而非方法体调用；无既有测试方法体同型背书。CI 跑绿前不作为已验证先例。
- O-3（既有边界，自述 §8 已披露）：`ConfigActivity.kt:50` `iconExit` 为 Activity 字段，控制器阶段与离场位不跨配置变更 / 进程重建存活；与 B20 / B21 / B26 同一边界，本单不解决。
- O-4（轻微，台账留观）：菜单→Qr 导航时 `lanExit` 未经 `beginExit()`，阶段停留在 OPEN（`BackupSection.kt:283` / :290 直接置 `lanSheet = Closed`）。OPEN 段 `canOpen` 仍为 true，不会误拒重开；槽位状态承担判重，行为无可见缺陷，但守卫阶段账不闭环。建议后续导航切走时补一次 `beginExit()` 或在守卫口径内登记「导航离场不迁阶段」为例外。

## 十一、未验证项（静态验收范围之外）

- 编译：沙箱无 gradle / JDK21 / SDK，未跑 `./gradlew` / `kotlinc`；新增 import、`InkBottomSheetHost` 首参签名变更、五个内层 Sheet 形参变更能否通过编译，以云端 CI 为准。
- 单测：`SheetExitControllerTest` 7 例是否全绿、`mutableStateOf` 能否在单测 classpath 实例化，未本地跑测，等 CI。
- 真机交互：离场下降 / 遮罩回亮实际手感、200ms 延时与组件动画帧对齐、离场段触摸截获范围，未真机走查。
- 动画时序：摘子树时机为代码静态推断（延时依据 `DURATION_MASK` 200ms），收束帧是否干净待 CI 出包后真机确认。
- 域外 22 条同形态项（autoTask 4 / source 12 / dict 2 / replace 3 / components 红线 1）影响面未逐条真机核验，按 B29 分域计划由其他单子收口，本单不动。

## 十二、验收结论

九条判据：判据 1 PASS、判据 2 PASS、判据 3 PASS、判据 4 PASS、判据 5 PASS（附 O-1）、判据 6 PASS、判据 7 PASS、判据 8 PASS（附 O-2）、判据 9 PASS。无 FAIL 项。代码静态层与被验方自述一致：13 条命名参数 + 8 条位置参数全部改接控制器派生 visible，Host 写死 true 已改透传，阶段机复用 B26 守卫无第二源，23 个槽位各持控制器并由 `SheetExitHost` 收束，7 例断言齐全，改动边界限于 config 域及其测试。编译与单测结果等 CI；O-1 / O-4 建议在后续单子或台账中跟踪收口。
