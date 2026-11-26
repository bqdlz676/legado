> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=6ab072c0e6de；文档=6dd8114a6a74）并复核 3 条结论，漂移 1 条（见 回执补录-批3-阅读器贴皮 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B26-常量visible接线.md

# 验收-B26：浮层可见性传常量 true 导致离场动画被跳过（独立验收）

## 〇、验收元信息

- 验收对象：docs/修复-B26-常量visible接线.md（被验方自述，已逐字读，含 §6 共 36 条清单结论）。
- 锚点卡：docs/锚点/B26-常量visible接线.md（§一 事实锚点、§二 五条判据、§三 边界）。
- 标准来源：docs/修复-B21-半屏离场动画.md、docs/验收-B21-半屏离场动画.md（两段式关闭范式、纯 JVM 守卫口径、双闸边界）。
- 被验提交：`a4a6c4d`（"修B26:笔记浮层改两段式关闭(复用B21阶段机,7断言)"）；文档入库提交 `46e7036`。验收基准：`a4a6c4d~1`。
- 验收方式：只读代码、测试与 git 历史，静态逐条回证；每条结论附「文件:行」证据与本验收真跑的 grep 输出。未改源码 / 测试 / 门禁 / 既有报告；未执行 git add / commit / push；未本地编译、未跑单测（沙箱禁编译，编译与 CI 结果标「未验证」）。
- 总体结论：**八条判据静态层 8 PASS（判据 3 附 1 项已披露边界：宿主级入口离场闸未接线），无 FAIL 项。清单 36 条复核计数与分类全部属实，域外 34 条按域列齐且每条带文件:行。编译 / 单测是否全绿、真机帧对齐与触摸截获，未验证，等 CI 出包后复验。**

## 一、判据 1：两段式关闭真实成立——先 visible=false、子树保留，收束后才摘 —— PASS

判据要求：关闭时可见性先翻 false、弹窗子树保留组合，离场动画收束后才摘 Fragment；不允许保留「立即摘」残留分支。

1. 改前基线（本验收真跑，`a4a6c4d~1`）：

```
git show a4a6c4d~1:app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt | grep -n "visible = true\|dismissAllowingStateLoss\|InkHalfSheet("
```

原样输出：

```
73:            dismissAllowingStateLoss()
85:                InkHalfSheet(
86:                    visible = true,
187:        dismissAllowingStateLoss()
```

旧 :86 `visible = true` 恒真；旧 :187 在 `dismissOnce` 内直接 `dismissAllowingStateLoss()`，窗口与 ComposeView 一并销毁，子树立即摘除。

2. 改后现役第一段（保留子树 + visible=false）：`HighlightNoteDialog.kt:216-223`
   - :217 `if (submitted) return`（既有单点出口闸，原样保留）；:218 `submitted = true`；:219 `if (commit) commitAction?.invoke()`（写库副作用仍在决策时刻执行，时机与旧版一致）；:222 `exitGate.beginExit()`；:223 `exitingState.value = true`。
   - `NoteDialogExitGate.kt:34-37`：`beginExit` 仅在 `phase == OPEN` 时迁 `EXITING`，非在场段空操作（:35）；:49-50 `visible` 仅 OPEN 段 true，EXITING 派生 false。
   - `HighlightNoteDialog.kt:115`：`visible = !exitingState.value`，离场段传 false 给 `InkHalfSheet`，组件内 `LaunchedEffect(visible)` 离场分支（遮罩回亮 + Sheet 下降）得以执行；`setContent` 子树在收束前未被摘除。

3. 改后现役第二段（收束再摘）：`HighlightNoteDialog.kt:106-113`
   - :106 `LaunchedEffect(exitingState.value)`；:107 `if (exitingState.value)`；:108 `delay((HalfSheetAnchors.MASK_DURATION_MS + HalfSheetAnchors.RISE_DURATION_MS).toLong())`；:109 `exitGate.finishExit()`；:111 `if (isAdded) dismissAllowingStateLoss()`。
   - `NoteDialogExitGate.kt:43-46`：`finishExit` 仅 EXITING → REMOVED，非离场段空操作（:44），不会清掉下一轮会话。
   - 时长锚点复核：`HalfSheetModel.kt:20` `object HalfSheetAnchors`；:50 `RISE_DURATION_MS = 250`；:53 `MASK_DURATION_MS = 200`；延时 = 200+250=450ms，与 B21 验收 §五 对齐口径一致，未另造魔法数。

4. 「立即摘」残留排查（本验收真跑）：

```
grep -n "dismissAllowingStateLoss" app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt
```

原样输出：

```
89:            dismissAllowingStateLoss()
111:                        if (isAdded) dismissAllowingStateLoss()
212:     * B26 两段式：本方法不再直接 `dismissAllowingStateLoss()`，而是先进离场段
```

- :89 为缺参数防御路径（`highlight == null`，不渲染表单子树，无动画可播，报告 §7.1 已说明，不套两段式，合理保留）。
- :111 为收束延时回调内摘除，即第二段出口。
- `dismissOnce` 方法体内已无直接 `dismissAllowingStateLoss()`；提交 diff 中旧 :187 直摘行被删除（见 `git show a4a6c4d` diff `-        dismissAllowingStateLoss()`）。

5. 单测回证：`NoteDialogExitGateTest.kt:42-53`（beginExit 后 `phase=EXITING`、`isComposed=true`、`visible=false`）；:68-80（finishExit 后 REMOVED、`isComposed=false`、`visible=false`）。

## 二、判据 2：无第二源——阶段枚举复用 B21 `ReaderSheetExitPhase`，未复制同名枚举 —— PASS

判据要求：复用 B21 状态机语义与阶段枚举，禁止另造第二份阶段枚举；若守卫内自定义同名枚举判 FAIL。

1. 枚举定义唯一（本验收真跑）：

```
grep -rn "enum class.*Phase\|enum.*EXITING" app/src/main/java/io/legado/app/ui/book/read/
```

原样输出：

```
app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetExitGate.kt:14:enum class ReaderSheetExitPhase {
```

全 reader 域仅一处枚举定义：`ReaderSheetExitGate.kt:14-18` `enum class ReaderSheetExitPhase { OPEN, EXITING, REMOVED }`，即 B21 现役定义。

2. B26 守卫为 import 复用（本验收真跑）：

```
grep -n "^import" app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt
```

原样输出：

```
3:import io.legado.app.ui.book.read.ink.ReaderSheetExitPhase
```

`NoteDialogExitGate.kt:22` `var phase: ReaderSheetExitPhase = ReaderSheetExitPhase.REMOVED`；:27 / :36 / :45 / :50 / :54 / :61 全部引用该枚举，守卫文件内无任何 `enum class` 声明。

3. 迁移规则逐条对齐 B21：
   - `open()`：`NoteDialogExitGate.kt:26-28` 置 OPEN；对照 B21 `ReaderSheetExitGate.kt:48-51`。
   - `beginExit()`：:34-37 仅 OPEN → EXITING，非在场段 return；对照 B21 :57-62。
   - `finishExit()`：:43-46 仅 EXITING → REMOVED，非离场段 return；对照 B21 :68-72。
   - 派生口径：`visible`（:49-50，仅 OPEN true）与 B21 :75-76 一致；`canOpen`（:60-61，仅 EXITING false）与 B21 :86-87 一致。
4. 有意差异（非第二源）：B26 守卫无 `retainedRequest` 槽位——本弹窗 highlight 参数构造时固定，无会话请求缓存需求；`isComposed` 改由 `phase != REMOVED` 派生（:53-54），B21 由 `retainedRequest != null` 派生（:79-80）。差异已写入守卫 KDoc :11-17 与报告 §2.1，属设计分叉而非枚举第二源。测试侧同样只 import 该枚举：`NoteDialogExitGateTest.kt:3`。

## 三、判据 3：离场拒收——离场段重复触发被闸拦截；宿主级入口闸未接（已披露边界）—— PASS（附边界）

判据要求：离场期间不可重复触发打开，与既有闸口径一致。

1. 第一道（既有单点出口闸，原样保留）：`HighlightNoteDialog.kt:217` `if (submitted) return`；:218 置位。所有离开路径（确认 :174、删除 :161、取消 :166、下滑 / 遮罩点击 :118、返回键 :195）统一经 `dismissOnce`，闸后置位二次触发整段 no-op，不写库、不重复 dismiss。
2. 第二道（守卫级离场闸）：`NoteDialogExitGate.kt:11-61` `canOpen` 仅 EXITING 段 false；:34-37 离场段重复 `beginExit` 不改阶段。单测 `NoteDialogExitGateTest.kt:55-66`：:61 `assertFalse(gate.canOpen)`；:63 重复 `beginExit()`；:64 阶段仍 EXITING；:65 `isComposed` 仍 true。
3. 边界记录（本验收确认报告 §8 如实披露，未隐瞒）：守卫 `canOpen` 在生产弹窗内**未被任何 open 入口调用**——本验收真跑：

```
grep -n "canOpen" app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt
```

原样输出：

```
221:        // LaunchedEffect(exitingState)。离场段 [exitGate.canOpen] 为 false，不叠新弹窗。
```

仅 :221 注释提及，无生产调用点。对照 B21 双闸：B21 有 Activity 入口闸 `ReadBookActivity.kt:1555` `isOpen || isExiting` + State 闸 `ReaderSheetState.kt:75`；B26 弹窗为 DialogFragment 独立窗口，实例内不存在重开路径，两个外部打开点（`ReadBookActivity.kt:1523`、`HighlightFragment.kt:52`）每次新建 Fragment 实例；宿主侧无 `isExiting` 等价闸，离场 ~450ms 内若宿主再次发起打开，新实例不经过本守卫 `canOpen`。报告 §8 已写明该缺口需另派单改 `ReadBookActivity` / `HighlightFragment`（reader 域红线，本单不动）。
4. 判定：守卫级闸存在且有测试断言，既有 `submitted` 闸兜底实例内重复触发；宿主级入口闸缺失属已披露边界，不构成本单域内 FAIL。后续若按 B21 口径补齐宿主双闸，需新派单接线。

## 四、判据 4：进入不受影响——打开后 visible 为 true，进入分支未被改 —— PASS

1. 新会话登记在场段：`HighlightNoteDialog.kt:94` `exitGate.open()`（`onCreateView` 视图组装前）；`exitingState` 初始值 `mutableStateOf(false)`（:72）；:115 `visible = !exitingState.value` 初始派生 true，与旧恒真在场态等价。
2. 守卫在场态：`NoteDialogExitGate.kt:11-28` `open()` 置 OPEN；:49-50 `visible` OPEN 段 true；单测 `NoteDialogExitGateTest.kt:30-40`：:36 OPEN、:37 `isComposed` true、:38 `visible` true、:39 `canOpen` true。
3. 组件进入分支未改：提交 `a4a6c4d` 文件清单仅三个文件（见判据 7），`ui/components/` 零 diff；`InkHalfSheet` 初始 `Animatable(if (visible) 1f else 0f)` 与进入分支沿用 B21 验收 §四.3 现役口径，本单未调整进入时长 / 缓动。
4. 收束后重开闭环：`NoteDialogExitGateTest.kt:96-109`：:103 `gate.open()`；:105 OPEN、:106 `visible` true、:107 `canOpen` true、:108 `isComposed` true。

## 五、判据 5：断言映射——7 条逐条对应判据，核心「关闭瞬间子树仍在、visible 已 false」真断言 —— PASS

1. 断言条数（本验收真跑）：

```
grep -c "@Test" app/src/test/java/io/legado/app/ui/book/read/NoteDialogExitGateTest.kt
```

原样输出：

```
7
```

2. 7 条逐条映射（行号为本验收读现役测试文件核对）：
   - T1 `NoteDialogExitGateTest.kt:20-28`：初始 REMOVED / `isComposed=false` / `visible=false` / `canOpen=true` → 映射判据 4（进入前提）与初始边界。
   - T2 :30-40：open 后 OPEN / `isComposed=true` / `visible=true` / `canOpen=true` → 映射判据 4。
   - T3 :42-53：`beginExit()` 后 :49 `phase=EXITING`、:51 `assertTrue(gate.isComposed)`（子树仍在）、:52 `assertFalse(gate.visible)`（可见性已 false）→ 映射判据 1 与判据 5，即锚点卡要求的核心断言，确为真实断言而非注释口号。
   - T4 :55-66：离场段 `canOpen=false`；重复 `beginExit` 不改阶段、子树仍在 → 映射判据 3。
   - T5 :68-80：`finishExit()` 后 REMOVED / `isComposed=false` / `visible=false` / `canOpen=true` → 映射判据 1（收束摘除）。
   - T6 :82-94：非在场 `beginExit` 空操作、在场段 `finishExit` 空操作不清阶段 → 映射判据 1 健壮性边界。
   - T7 :96-109：收束后重开回 OPEN 且可见、可开、子树保留 → 映射判据 4 与生命周期闭环。
3. 纯 JVM 边界（本验收真跑）：测试 import 仅 `ReaderSheetExitPhase` 与 org.junit 断言：

```
grep -n "^import" app/src/test/java/io/legado/app/ui/book/read/NoteDialogExitGateTest.kt
```

原样输出：

```
3:import io.legado.app.ui.book.read.ink.ReaderSheetExitPhase
4:import org.junit.Assert.assertEquals
5:import org.junit.Assert.assertFalse
6:import org.junit.Assert.assertTrue
7:import org.junit.Test
```

守卫 `NoteDialogExitGate.kt` 无 androidx.compose / android / view 任何 import，唯一外部依赖为 B21 纯 Kotlin 枚举；延时等待留在 Compose 层（:106-113），不进入守卫断言，与 B21 同一边界划分。
4. 测试反引号函数名禁字符复核（本验收真跑）：

```
grep -n '`' app/src/test/java/io/legado/app/ui/book/read/NoteDialogExitGateTest.kt | grep -E '[.:\[\]/;<>]'; echo "exit=$?"
```

输出空；`exit=1`，函数名无禁字符。

## 六、判据 6：清单真实性——36 条计数与分类复核属实，无误判 —— PASS

1. 全仓两种写法穷举（本验收真跑，`app/src/main`）：

```
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt | wc -l
35

grep -rn "visible=true" app/src/main --include=*.kt; echo "exit=$?"
exit=1

grep -n "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt; echo "exit=$?"
exit=1
```

修复后残留 35 条；无空格写法零命中；目标文件恒真常量零残留。35 + 本单已修 1 = 清单 36 条，与自述计数一致。

2. 分类复核（本验收逐条读 35 条命中行上下文）：
   - 已修 1 条：`HighlightNoteDialog.kt` 旧 :86 → 现役 :115（基线回证见判据 1）。
   - 红线内不动 1 条：`ui/components/HandleFileSheet.kt:48` `InkHalfSheet(visible = true, ...)`，components 契约冻结，分类正确。
   - 域外 34 条：4 autoTask + 12 book/source + 13 config + 2 dict + 3 replace，逐条行号与报告 §6.3 清单吻合（明细见判据 8）。
3. 误判排查：35 条命中行被调对象逐行核对为 `InkHalfSheet` / `InkBottomSheet` 或其薄封装（`ConfirmSheet` / `ListPickSheet` / `NumberPickerSheet` / `TextEditSheet` / `MultiChoiceSheet` / `InkBottomSheetHost`）。config 薄封装内层均确为 `InkBottomSheet`（本验收真跑 `SettingSheets.kt:40/98/144/184/225` 五个封装函数体内各一处 `InkBottomSheet(visible = true, ...)`；`SettingSheetHost.kt:16` `InkBottomSheetHost` 包 `InkBottomSheet`）。未发现卡片显隐、字段可见性等无关布尔混入；自述「无关布尔 0 条」成立。

## 七、判据 7：边界未越——commit 仅三文件，红线目录与宿主文件零改动 —— PASS

1. 提交文件清单（本验收真跑）：

```
git show --name-status --format= a4a6c4d
```

原样输出：

```
M	app/src/main/java/io/legado/app/ui/book/read/HighlightNoteDialog.kt
A	app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt
A	app/src/test/java/io/legado/app/ui/book/read/NoteDialogExitGateTest.kt
```

`git show --stat --format= a4a6c4d`：三文件，+210 / -2，无第四个文件。

2. 红线目录零 diff（本验收真跑）：

```
git show a4a6c4d --stat --format= -- app/src/main/java/io/legado/app/ui/components/ app/src/main/java/io/legado/app/ui/book/read/ink/ app/src/main/java/io/legado/app/ui/map/ app/src/main/java/io/legado/app/help/artifact/
```

输出空。B20/B21 阅读半屏链路（`ReaderSheetExitGate.kt` / `ReaderSheetState.kt` / `ReaderSheetOverlay.kt` / `ReaderSheetGuard.kt`）、`ui/components/` 契约、地图域、名物库均未进入本单 delta。
3. 宿主与域外屏幕零改动：

```
git show a4a6c4d --format= -- app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt app/src/main/java/io/legado/app/ui/book/toc/HighlightFragment.kt | wc -l
0
```

两个外部打开点现役：`ReadBookActivity.kt:1523` `showDialogFragment(HighlightNoteDialog(highlight))`；`HighlightFragment.kt:163` 同签名构造，未改。域外 `autoTask/`、`book/source/`、`config/`、`dict/`、`replace/` 目录本单 delta 为空。
4. 工作树状态：`git status --short | grep -v "docs/"` 无输出（exit=1），无未提交夹带；B27 `MapWebHolder.kt` 清剿与 `DesignTokenGuardTest.kt` 基线摘除属提交 `197d2e7` / `1dc31aa` 另一在飞单，未并入 B26 口径。

## 八、判据 8：域外交接完整性——34 条按域列出，每条带文件:行，无遗漏 —— PASS

本验收以全仓 grep 35 条残留逐条比对报告 §6.3 清单，五域计数与行号全部吻合：

1. autoTask 域 4 条：
   - `autoTask/AutoTaskEditScreen.kt:151`（帮助页 InkHalfSheet）。
   - `autoTask/AutoTaskScreen.kt:168`（帮助页）。
   - `autoTask/AutoTaskScreen.kt:403`（运行日志 Sheet）。
   - `autoTask/ImportAutoTaskDialog.kt:98`（DialogFragment 壳内 `jsonTarget?.let`）。
2. book/source 域 12 条：
   - `book/source/manage/SourceManageSheets.kt:85`、`:145`、`:167`、`:202`、`:296`、`:337`、`:374`、`:422`、`:459`、`:489`（十处 InkHalfSheet，`vm::closeSheet` 置 None 摘子树）。
   - `book/source/edit/SourceEditScreen.kt:426`（溢出菜单 Sheet）。
   - `book/source/debug/SourceDebugScreen.kt:401`（溢出菜单 Sheet）。
3. config 域 13 条：
   - `config/ThemeListDialog.kt:102`（ConfirmSheet 封装）。
   - `config/ConfigActivity.kt:129`（ListPickSheet 封装）。
   - `config/BottomBarSkinActivity.kt:237`（ConfirmSheet 封装）。
   - `config/sections/WelcomeSection.kt:68`（NumberPickerSheet 封装）。
   - `config/sections/OtherSection.kt:174`（NumberPickerSheet 封装）、`:185`（TextEditSheet 封装）。
   - `config/sections/CoverFontSection.kt:75`（NumberPickerSheet 封装）。
   - `config/settings/SettingSheetHost.kt:14`（InkBottomSheet 薄封装 `InkBottomSheetHost`）。
   - `config/settings/SettingSheets.kt:54`、`:109`、`:154`、`:194`、`:235`（五个内层 InkBottomSheet）。
4. dict 域 2 条：`dict/rule/DictRuleScreen.kt:377`（规则编辑 Sheet）、`:416`（帮助页）。
5. replace 域 3 条：`replace/ReplaceRuleScreen.kt:198`（帮助页）、`:500`（分组管理 Sheet）、`replace/edit/ReplaceEditScreen.kt:181`（帮助页）。

计数核对：4+12+13+2+3 = 34，加红线 1 = 35 残留，与 grep 总数一致；34 条均带文件:行与宿主形态说明，无「另立」一句带过的条目。域外接续建议（按 autoTask / source / config / dict / replace 五域各立条目、将来合并泛型守卫）记录在报告 §6.4，本验收认可其边界划分。

## 九、新发现缺陷与观察项

1. 宿主级入口离场闸未接线（边界项，报告 §8 已披露，非隐瞒）：`exitGate.canOpen` 在生产弹窗内仅见于注释（`HighlightNoteDialog.kt:221`），无 open 入口消费；离场 ~450ms 内宿主若新建 Fragment 实例，不经过该闸。对照 B21 `ReadBookActivity.kt:1555` 的 `isOpen || isExiting` 双闸口径，本单仅有实例内 `submitted` 闸 + 守卫级 `canOpen`（测试覆盖）。若后续验收标准要求宿主级双闸对齐 B21，需另派单改 `ReadBookActivity` / `HighlightFragment`。
2. `isComposed` 派生口径与 B21 分叉（轻微，留待泛化合并且统一）：B21 `ReaderSheetExitGate.kt:79-80` 以 `retainedRequest != null` 派生；B26 `NoteDialogExitGate.kt:11-54` 以 `phase != REMOVED` 派生。无缓存设计下两者等价，但将来合并 B21/B26 两份守卫为泛型单一实现时，需先统一该派生口径。
3. 离场段状态不随重建复位（报告 §8 已披露）：`exitGate` / `exitingState` 为 Fragment 字段，旋转 / 配置重建后阶段与离场位不跨重建存活；与 B20/B21 同一边界，本单不解决。另注：`finishExit()` 后 `exitingState` 仍保持 true，仅靠随后 :111 `dismissAllowingStateLoss()` 摘窗收口；若延时窗口内 Fragment 被系统回收导致 :111 因 `isAdded=false` 未执行，可见性停留 false 且阶段停在 EXITING，属同一重建边界内的残余形态。
4. 文档路径前缀漂移（轻微，非代码缺陷）：锚点卡 §一 写 `ui/note/HighlightNoteDialog.kt:86`，现役实际路径 `ui/book/read/HighlightNoteDialog.kt`（旧 :86 恒真行，基线回证属实）；报告 §1 已记该路径漂移。
5. 等效延时帧对齐风险（沿用 B21 验收 §九.2，报告 §9 已自述）：摘窗时机依赖 450ms 等效延时而非组件完成回调；组件 `LaunchedEffect(visible)` 与弹窗 `LaunchedEffect(exitingState.value)` 启动于相邻重组帧，极端掉帧下可能动画未净即摘或多留一帧，静态层不可判定，待真机走查。

## 十、未验证项

1. 编译与单测：本地禁编译（沙箱无 gradle / JDK21 / SDK），未跑 `./gradlew` / `kotlinc`；`NoteDialogExitGateTest` 7 例是否全绿以云端 CI 为准，本验收不假设既有 Verify 已绿。
2. 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段 Dialog 窗口触摸截获，未在真机走查。
3. 动画时序：两段式摘除时机为代码静态推断（延时依据 = 组件两段顺序动画时长之和 200+250ms），实际收束帧是否干净待 CI 出包后真机确认。
4. 域外 34 条同形态项的实际影响面未逐条真机核验；清单判定基于源码条件渲染形态静态推断。
5. 守卫 `canOpen` 宿主级接线效果未验证（见判据 3 边界与 §九.1）。

## 十一、验收结论

- 八条判据静态层全部 PASS：两段式关闭成立且无「立即摘」残留（仅缺参数防御路径保留直摘，合理）；阶段枚举复用 B21 `ReaderSheetExitPhase`，全 reader 域无第二份同名枚举；离场段守卫级拒收 + 既有 submitted 闸兜底，宿主级入口闸缺失已如实披露为边界；在场态 visible=true、组件进入分支未改；7 条断言逐条映射判据，核心「关闭瞬间子树仍在、visible 已 false」为真实断言；36 条清单计数（已修 1 / 红线 1 / 域外 34）与分类经独立 grep 复核属实，无误判；commit `a4a6c4d` 仅三文件，components / ink / 地图 / 名物库 / 宿主文件零 diff；域外 34 条按五域列齐且每条带文件:行。
- 阻断项：无 FAIL。
- 放行条件：待 CI 编译与 7 条单测全绿、出包后真机复验离场帧对齐与触摸截获；宿主级离场闸是否补齐留待 reader 域后续派单。在此之前本单结论为「静态层 PASS，运行时未验证」。
