锚点卡：docs/锚点/B29-域外常量visible-config.md

# 修复-B29：source 域浮层可见性传常量 true 导致离场动画被跳过（两段式关闭收口）

> 日期：2026-11-26 ｜ 执行代理：B29 写码专单（source 域）｜ 基线：本单未执行 git add / commit / push（沙箱禁提交）。
> 清单来源：`docs/修复-B26-常量visible接线.md` §6.3「book/source 域（12 条）」。
> 范式来源：`docs/修复-B21-半屏离场动画.md`、`docs/修复-B26-常量visible接线.md`、`docs/修复-B29-常量visible-config.md`（config 域先例）。
> E3 结果：✓ 已落码待 CI——grep 回证每处改动真写入；本地禁编译 / 禁跑测（沙箱无 gradle / JDK21 / SDK），编译与单测结果一律标「未验证，等 CI」。
> 仓库：`bqdlz676/legado`；工作树改动，未做任何 `git add / commit / push`。

## 1. 缺陷原貌与本单结论

缺陷原貌（对照 HEAD 基线逐条复核现役代码）：

- B26 清单 source 域 12 条，宿主分两类：管理页 VM 槽位 `state.sheet`（`when` 分发 10 个私有 Sheet，`vm.closeSheet()` 置 `ManageSheet.None` 摘子树）；编辑页 / 调试页 local `overflowOpen` 布尔（`if (overflowOpen) { OverflowSheet(...) }`，dismiss 置 false 摘子树）。两类宿主都是 dismiss 即摘子树，`visible = true` 恒真，`InkHalfSheet.kt:121-130` 的 `visible=false` 离场分支（遮罩回亮 200ms + Sheet 下降 250ms）永不执行。

本单处置口径（对齐 B21 / B26 / B29-config 两段式关闭）：

1. 关闭不直摘：每个弹层槽位各持一份离场控制器；dismiss 先走 `beginExit()` 进离场段，派生 `visible=false`，弹层子树保留组合，播组件内离场动画；写库 / 副作用仍在决策时刻执行，时机与旧版一致。
2. 收束再摘：`SourceSheetExitHost` 在离场段按 `HalfSheetAnchors.MASK_DURATION_MS`（200ms）+ `RISE_DURATION_MS`（250ms）等效延时收束，`finishExit()` 后回调宿主清空槽位状态、摘除子树。摘除时机有明确依据，不写「立即摘」。
3. 离场段拒收：控制器 `canOpen` 在 EXITING 段为 false；每个打开点先过此闸再 `open()`，离场动画播放中不叠新弹层。
4. 阶段机复用 B26 `NoteDialogExitGate`（其内部复用 B21 `ReaderSheetExitPhase`），未造第二份枚举、未复制迁移规则；新增 `SourceSheetExitController` 只是 source 域 Compose 层薄封装，结构照 config 域 `SheetExitController.kt` 先例。

编译结论：**未验证，等 CI**。沙箱无 gradle / JDK21 / SDK，本地未跑 `./gradlew` / `kotlinc`；新增 7 条纯 JVM 断言是否全绿以云端 CI 为准。

## 2. 改动清单（绝对路径）

### 2.1 新增文件

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt`
  - `SourceSheetExitController`：internal 构造，包装 B26 `NoteDialogExitGate` + 一份 `MutableState<Boolean>` 离场位；派生只读 `visible` / `isComposed` / `canOpen` / `isExiting`；`open()` / `beginExit()` / `finishExit()` 转发守卫并同步离场位。
  - `rememberSourceSheetExitController()`：Compose 工厂，每个槽位各持一份控制器，互不串阶段。
  - `SourceSheetExitHost(controller, onFinished, content)`：离场段按 HalfSheet 两段时长（200ms + 250ms）等效延时后 `finishExit()` 并回调宿主清槽位摘子树；非离场段不动作。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/test/java/io/legado/app/ui/book/source/SourceSheetExitControllerTest.kt`
  - 7 例纯 JVM 断言：初始摘除态、open 后在场态、关闭瞬间子树仍在且 visible 已 false、离场段拒收重复打开、收束后摘除、空操作兜底、收束后重开回到在场态。

### 2.2 修改文件（main 源集，均未提交）

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt`
  - 10 个私有 Sheet 各持一份 `exit` 控制器；组合时 `LaunchedEffect(Unit) { if (exit.canOpen) exit.open() }` 过闸登记在场段；`SourceSheetExitHost` 收束回调 `vm::closeSheet`（`GroupInputSheet` / `ExportResultSheet` 转发宿主传入的 `onDismiss`）；`InkHalfSheet` 可见性改派生 `exit.visible`，遮罩 / 拖拽 dismiss 改 `exit.beginExit()`；决策时刻按钮副作用先执行，再 `beginExit()`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt`
  - 宿主持 `overflowExit` 控制器；顶栏溢出入口先过 `canOpen` 闸再置 `overflowOpen = true` 并 `open()`；`SourceSheetExitHost` 收束清 `overflowOpen`；`OverflowSheet` 新增 `visible` 形参透传派生可见性，`onDismiss` 接 `beginExit()`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/debug/SourceDebugScreen.kt`
  - 同编辑页形态：`overflowExit` 控制器、打开过闸、`SourceSheetExitHost` 收束、`OverflowSheet` 新增 `visible` 形参透传。

### 2.3 未触碰（范围红线）

- `ui/components/HandleFileSheet.kt:48`（components 契约冻结，红线 1 条，本单零改动，E1.7 佐证）。
- reader 域（`ui/book/read/**`）、地图、名物库（help/artifact）、autoTask / dict / replace / config 域外屏幕：均未改其代码。工作树中这些目录的既有 WIP 属其他在飞单产物，本单未并入、未回写。
- `SourceManageViewModel.kt` / `SourceManageState.kt` / `SourceManageScreen.kt` / `BookSourceActivity.kt` / 两个 Edit / Debug 的 ViewModel 与 State：零改动；VM 槽位 `openSheet` / `closeSheet` 签名未变。

## 3. E1 grep 回证（命令 + 原样输出）

### 3.1 source 域命名参数 `visible = true` 残留（应为空）

命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt; echo "exit=$?"
```

输出：空；`exit=1`。

### 3.2 source 域位置参数 `Sheet(true, ...)` 残留（应为空）

命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt; echo "exit=$?"
```

输出：空；`exit=1`。

### 3.3 source 域多行 `true,` 残留（应为空）

命令：

```
grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/book/source/ --include=*.kt; echo "exit=$?"
```

输出：空；`exit=1`。

### 3.4 全仓命名参数 `visible = true` 残留

命令：

```
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt | wc -l
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt
```

输出：`1`；唯一命中 `app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt:48`（红线，契约冻结原样保留）。

口径快照：本单验收时刻工作树内 autoTask / dict / replace 三域常量已被各自在飞单 WIP 清零，故全仓仅剩红线 1 条；各域最终残留数以其单独立项验收为准，本单不代其结论。

### 3.5 全仓位置参数形式

命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main --include=*.kt; echo "exit=$?"
```

输出：空；`exit=1`。

### 3.6 全仓多行 `true,` 兜底扫描（逐条核对非 Sheet 首参）

命令：

```
grep -rnE "^\s*true,\s*$" app/src/main --include=*.kt
```

输出 14 处，逐条上下文核对均为无关布尔实参：

- `help/BottomBarSkinManager.kt:405`——位图缩放尺寸回调布尔标志。
- `help/source/SourceVerificationHelp.kt:309`——校验结果回调布尔标志。
- `book/changesource/ChangeBookSourceDialog.kt:262`、`ChangeChapterSourceDialog.kt:373`、`ChangeChapterSourceDialog.kt:597`——错误提示回调布尔标志。
- `book/read/HighlightStyleDialog.kt:155`、`:252`——高亮样式项选中标志。
- `code/CodeEditViewModel.kt:233`——错误回调布尔标志。
- `components/inkslate/contract/InkPalette.kt:33`、`:45`、`:57`——调色板色项选中标志。
- `main/rss/RssFragment.kt:188`、`:205`——`ReadRssActivity.start` 启动布尔实参。
- `main/search/SearchHighlight.kt:45`——匹配段布尔标志。

以上 14 处均非 `InkHalfSheet` / `InkBottomSheet` / 其薄封装的首参 `visible`，不计缺陷。

### 3.7 阶段机复用符号落位

命令：

```
grep -n "import io.legado.app.ui.book.read.NoteDialogExitGate" \
  app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt
```

输出：

```
app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt:3
```

阶段枚举复用链：`ReaderSheetExitPhase`（OPEN / EXITING / REMOVED）定义于 `ui/book/read/ink/ReaderSheetExitGate.kt:14-17`；B26 `NoteDialogExitGate.kt:3` import 该枚举；本单 `SourceSheetExitController` 只包装该守卫，未另造枚举、未另写迁移规则。

### 3.8 两段式接线落位（控制器 / 闸 / 收束宿主）

命令：

```
grep -n "rememberSourceSheetExitController\|LaunchedEffect(Unit)\|SourceSheetExitHost(\|canOpen\|\.open()\|beginExit" \
  app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt \
  app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt \
  app/src/main/java/io/legado/app/ui/book/source/debug/SourceDebugScreen.kt
```

关键落位见 §4 索引。

### 3.9 测试断言条数与测试名禁字符复核

命令：

```
grep -c "@Test" app/src/test/java/io/legado/app/ui/book/source/SourceSheetExitControllerTest.kt
grep -nE '^\s*fun `[^`]*[.:\[\]/;<>][^`]*`' app/src/test/java/io/legado/app/ui/book/source/SourceSheetExitControllerTest.kt; echo "exit=$?"
```

输出：`7`；第二条空，`exit=1`（7 个反引号测试名内不含 `. : [ ] / ; < >`）。

### 3.10 红线目录与域外零改动

命令：

```
git diff --stat -- app/src/main/java/io/legado/app/ui/components/ \
  app/src/main/java/io/legado/app/ui/book/read/ \
  app/src/main/java/io/legado/app/ui/config/
```

输出：空，`exit=0`（本单口径；工作树中 autoTask / dict / replace 改动属其他在飞单，不在本单 diff 口径内）。

红线文件原样复核：

```
sed -n '48p' app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt
```

输出：`InkHalfSheet(visible = true, onDismiss = onCancel, title = title, content = {`（未改）。

### 3.11 括号配平粗检

命令：python3 剥离字符串 / 注释后逐文件计数 `{` / `}` / `(` / `)`。

输出：本单 5 个 main 文件 + 1 个测试文件全部配平：

- `SourceSheetExitController.kt`：`{` 10 == `}` 10；`(` 27 == `)` 27。
- `SourceManageSheets.kt`：`{` 156 == `}` 156；`(` 430 == `)` 430。
- `SourceEditScreen.kt`：`{` 98 == `}` 98；`(` 333 == `)` 333。
- `SourceDebugScreen.kt`：`{` 78 == `}` 78；`(` 224 == `)` 224。

### 3.12 本单改动文件清单

命令：`git status --short | grep book/source`

输出：

- ` M app/src/main/java/io/legado/app/ui/book/source/debug/SourceDebugScreen.kt`
- ` M app/src/main/java/io/legado/app/ui/book/source/edit/SourceEditScreen.kt`
- ` M app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt`
- `?? app/src/main/java/io/legado/app/ui/book/source/SourceSheetExitController.kt`
- `?? app/src/test/java/io/legado/app/ui/book/source/SourceSheetExitControllerTest.kt`

## 4. E2 文件:行 索引（改动后现役行号）

### 4.1 新增控制器与测试

- `SourceSheetExitController.kt`
  - :3 import `NoteDialogExitGate`；:32 控制器构造；:38 `visible`；:42 `isComposed`；:46 `canOpen`；:50 `isExiting`；:54 `open()`；:57 `beginExit()`；:63 `finishExit()`；:73 `rememberSourceSheetExitController()`；:88 `SourceSheetExitHost`；:95 两段时长延时表达式。
- `SourceSheetExitControllerTest.kt`
  - :28 初始摘除态；:38 在场态；:50 关闭瞬间子树保留且 visible=false；:63 离场段拒收；:76 收束摘除；:90 空操作兜底；:104 收束后重开。

### 4.2 SourceManageSheets.kt（10 槽位）

- `MainOptionsSheet`：:88 控制器；:89 过闸 `open()`；:90 `SourceSheetExitHost`（onFinished `vm::closeSheet`）；:91 派生 `exit.visible` + `beginExit()`。
- `SortSheet`：:144 控制器；:145 过闸；:155 host；:156 派生 visible；:162 选中后 `beginExit()`。
- `GroupFiltersSheet`：:179 控制器；:180 过闸；:181 host；:182 派生 visible；:196 / :203 选中后 `beginExit()`。
- `GroupManageSheet`：:218 控制器；:219 过闸；:220 host；:221 派生 visible。
- `OnlineImportSheet`：:316 控制器；:317 过闸；:318 host；:319 派生 visible；:347 导入确认后 `beginExit()`。
- `CheckSourceSheet`：:361 控制器；:362 过闸；:363 host；:364 派生 visible；:378 校验触发后 `beginExit()`。
- `ItemActionsSheet`：:402 控制器；:403 过闸；:404 host；:405 派生 visible；:410 / :415 / :420 / :422 / :423 / :433 / :439 各动作后 `beginExit()`。
- `SelectionActionsSheet`：:454 控制器；:455 过闸；:456 host；:457 派生 visible；:459 / :460 / :461 / :462 / :463 / :473 / :474 / :475 / :476 / :480 各动作后 `beginExit()`。
- `GroupInputSheet`：:496 控制器；:497 过闸；:498 host（onFinished 转发宿主 `onDismiss`）；:499 派生 visible；:513 分组确认后 `beginExit()`。
- `ExportResultSheet`：:530 控制器；:531 过闸；:532 host（onFinished 转发宿主 `onDismiss`）；:533 派生 visible。

### 4.3 SourceEditScreen.kt

- :38 / :39 import；:89 `overflowExit`；:101 打开闸 `canOpen`；:103 `open()`；:155 `SourceSheetExitHost`（onFinished 清 `overflowOpen`）；:160 派生 `overflowExit.visible`；:161 `beginExit()`；:438 `OverflowSheet` 内层 `InkHalfSheet(visible = visible, ...)`。

### 4.4 SourceDebugScreen.kt

- :39 / :40 import；:79 `overflowExit`；:93 打开闸；:95 `open()`；:118 `SourceSheetExitHost`；:121 派生 visible；:122 `beginExit()`；:413 `OverflowSheet` 内层 `InkHalfSheet(visible = visible, ...)`。

## 5. 清单对账（B26 基线 12 条逐条行号）

HEAD 基线行号与 B26 清单零漂移；「现役行号」为本单改动后位置。

1. `manage/SourceManageSheets.kt:85`（现役 :91）——`MainOptionsSheet` 主选项 Sheet。判定：改。
2. `manage/SourceManageSheets.kt:145`（现役 :156）——`SortSheet` 排序 Sheet。判定：改。
3. `manage/SourceManageSheets.kt:167`（现役 :182）——`GroupFiltersSheet` 分组筛选 Sheet。判定：改。
4. `manage/SourceManageSheets.kt:202`（现役 :221）——`GroupManageSheet` 分组管理 Sheet。判定：改。
5. `manage/SourceManageSheets.kt:296`（现役 :319）——`OnlineImportSheet` 在线导入 Sheet。判定：改。
6. `manage/SourceManageSheets.kt:337`（现役 :364）——`CheckSourceSheet` 校验 Sheet。判定：改。
7. `manage/SourceManageSheets.kt:374`（现役 :405）——`ItemActionsSheet` 单来源菜单 Sheet。判定：改。
8. `manage/SourceManageSheets.kt:422`（现役 :457）——`SelectionActionsSheet` 多选统计 Sheet。判定：改。
9. `manage/SourceManageSheets.kt:459`（现役 :499）——`GroupInputSheet` 通用标题 / 分组输入 Sheet（`AddToGroup` / `RemoveFromGroup` 两槽共用）。判定：改。
10. `manage/SourceManageSheets.kt:489`（现役 :533）——`ExportResultSheet` 导出结果 Sheet。判定：改。
11. `edit/SourceEditScreen.kt:426`（现役 :438）——`OverflowSheet` 溢出菜单 Sheet。判定：改。
12. `debug/SourceDebugScreen.kt:401`（现役 :413）——`OverflowSheet` 溢出菜单 Sheet。判定：改。

## 6. 实测差额段

- 命名参数 `visible = true`：source 域实测 12 条，与 B26 清单 12 条一一对应，**差额 0**；HEAD 基线行号复核零漂移（E1.1 / §5）。
- 位置参数 `Sheet(true, ...)` / `Host(true, ...)`：source 域 0 条，全仓 0 条（E1.2 / E1.5）。
- 多行 `true,`：source 域 0 条；全仓 14 处，逐条上下文核对均为无关布尔实参（E1.6），非 Sheet 首参。
- 薄封装 host 内部写死：source 域无独立 host 封装；10 个私有 Sheet 与 2 个 `OverflowSheet` 均直接调用 `InkHalfSheet`，不存在第二层内部写死 `visible = true` 的私有包装（对照 config 域 `InkBottomSheetHost` 间接受害形态，本域无同类项）。
- 无关布尔命中：0 条。grep `visible` 全量复核 source 域，除上述 12 个组件形参外无卡片显隐 / 字段可见性等其他语义混入。

## 7. 逐条判定（改 / 不改及理由）

### 7.1 判定改（12 条，两段式接线）

- 12 条均为模态浮层（包 `InkHalfSheet`），宿主条件渲染摘子树，同形态缺陷；处置见 §2.2 与 §4 索引。证据：基线 `visible = true` 写死；现役各槽位接 `exit` 控制器，派生 `exit.visible`，`SourceSheetExitHost` 收束清槽位。
- 决策时刻副作用时机不变：排序 / 筛选 / 导入 / 校验 / 置顶置底 / 启停 / 删源 / 分组增删改 / 多选操作 / 分享导出等 `vm.*` 与 `actions.*` 调用仍在 `beginExit()` 之前执行，不延后到动画收束。

### 7.2 判定不改（同屏导航切换直摘按钮群，保留旧口径）

- `MainOptionsSheet` 现役 :98-99（在线导入）、:103-104（分组管理）、:107-108（排序）、:111-112（分组筛选）：动作后果是关闭本层后直接 `vm.openSheet` 打开下一级 Sheet，属同屏导航切换；旧直摘与导航切换同期发生，不存在「停留在原屏播离场」的交互诉求。沿用 config 域 N3 先例保留直摘；遮罩 / 拖拽关闭仍走 `exit` 两段式。
- `SelectionActionsSheet` 现役 :467（加入分组）、:471（移出分组）、:478（校验来源）：同为打开下一级 Sheet 的导航切换，保留直摘；其余批量动作行已接 `beginExit()`。
- 若验收要求导航切换也播下降动画，需另派单评估「先离场再跳转」的双槽位时序（见 §10 需另单）。

### 7.3 判定不改（红线）

- `ui/components/HandleFileSheet.kt:48`：components 契约冻结，`InkHalfSheet(visible = true, ...)` 原样保留（E1.10 佐证）。

### 7.4 域内其他成分判定

- `SourceManageRow.kt` / `BookSourceGroupFilter.kt` / 各 ViewModel / State / Activity：无浮层 `visible` 形参，不属本缺陷形态，未改。
- `SourceDebugScreen` 帮助面板 `state.helpOpen`、日志体、错误横幅：非模态 Sheet 浮层，无离场动画组件挂载点，未改。

## 8. 二阶影响（全仓消费点逐个说明）

### 8.1 弹层内部

- 写库 / 配置副作用时机不变：各 `onConfirm` / `onPick` 仍在决策时刻执行（排序落库、分组增删改、启停 / 启停发现、删源、导出序列化、在线导入历史缓存等），不延后到动画收束。
- 表单 / 输入状态由既有 `remember` 持有（重命名文本、新分组名、在线导入 url、校验关键词、分组输入名），离场 ~450ms 内子树保留组合，输入内容不丢；收束摘子树后随宿主槽位清空，无残留状态。
- 各私有 Sheet 初始未打开时不进入组合（VM 槽位 / local 布尔条件渲染），不存在常驻组合开销；组件内部保留帧条件 `progress.value == 0f && !visible` 负责不可见时不渲染遮罩内容。

### 8.2 与既有闸的交互

- 宿主槽位状态（VM `state.sheet` 单槽位 / local `overflowOpen`）仍是在场段判重的唯一来源；控制器 `canOpen` 只拦离场段，两道闸串联不替代，口径同 B26 / config。
- 离场段重复 dismiss 为守卫空操作，不二次迁移阶段；离场段重复打开被 `canOpen` 拒收，不会出现「旧 Sheet 下降中叠起新弹窗」的双层竞态。
- 管理页 10 个槽位各持独立控制器，Sheet 之间切换（导航直摘路径）不串阶段；`GroupInputSheet` 被两个槽位复用，每次组合各持一份新控制器。

### 8.3 外部消费点排查

- `OverflowSheet` 签名各新增一个 `visible: Boolean` 形参；二者均为 `private` 函数，调用点唯一（宿主板内），无域外消费方。
- `SourceManageViewModel.openSheet` / `closeSheet` 签名未变；`BookSourceActivity` 四个打开点（:66 导出结果回调、`SourceManageScreen` :98 / :378 / :460）构造调用零改动。
- 未新增跨域状态回传：控制器只反向引用 B26 守卫，不向 reader 半屏链路 / ViewModel 回传离场位。

### 8.4 二阶 / 三阶推演

- 二阶：离场段弹层子树仍在组合，继续截获宿主触摸；宿主列表滚动 / 点击在离场 ~450ms 内不与 Sheet 手势抢通道，与 B21 验收 §7.2 结论一致。
- 二阶：等效延时只引组件自有时长常数 `MASK_DURATION_MS` 200ms + `RISE_DURATION_MS` 250ms；`InkHalfSheet` 离场为遮罩 / 下降并行两段，延时取两段之和（同 B26 `HighlightNoteDialog.kt:108` 口径），不另造魔法数。
- 三阶：日后组件若补 `onExitFinished` 完成回调，可把 `SourceSheetExitHost` 内 `delay` 替换为回调触发，守卫阶段口径不变；延时常数跟随组件时长调整，不会漂移。
- 三阶：本单控制器为 source 域 Compose 层薄封装；连同 B21 持型守卫、B26 无负载守卫、config `SheetExitController`，仓内已有四份相近包装，建议后续合并为泛型纯 JVM 单一实现（见 §10）。

## 9. 断言清单（纯 JVM，待 CI 跑绿）

`SourceSheetExitControllerTest.kt` 7 例：

1. :28 `初始为摘除态 不可见 子树不在 可接受新打开`——初始 `visible=false`、`isComposed=false`、`canOpen=true`、`isExiting=false`。
2. :38 `open 后在场态 可见 子树保留 可开`——`open()` 后 `visible=true`、`isComposed=true`、`canOpen=true`、`isExiting=false`。
3. :50 `关闭瞬间子树仍在且可见性已翻 false`——`beginExit()` 后 `isComposed=true`（子树保留）、`visible=false`、`isExiting=true`。
4. :63 `离场段拒收重复打开`——EXITING 段 `canOpen=false`；重复 `beginExit()` 不改阶段，子树仍在、visible 仍 false。
5. :76 `动画收束后摘除子树`——`finishExit()` 后 `isComposed=false`、`visible=false`、`isExiting=false`、`canOpen=true`。
6. :90 `空操作兜底 非在场开始离场不动状态 非离场收尾不迁移`——REMOVED 段 `beginExit()` 空操作；OPEN 段 `finishExit()` 空操作，状态不迁移。
7. :104 `收束后重开回到在场态并可见`——REMOVED 后再次 `open()`，`visible=true`、`canOpen=true`、`isComposed=true`。

覆盖分支：初始态 / 在场态 / 离场段进入 / 离场段拒收 / 收束摘除 / 空操作兜底 / 收束后重开，每个判定分支至少 1 条。

## 10. 需另单（跨模块问题，本单未顺手改）

1. **控制器包装合并**：仓内并存 B21 持型守卫、B26 `NoteDialogExitGate`、config `SheetExitController`、本单 `SourceSheetExitController` 四份相近实现；另有并发单落在 `ui/sheet/HalfSheetExitHost.kt` 的 HalfSheet 收束宿主。建议后续立项合并为泛型纯 JVM 单一守卫 + 复用 Compose 薄封装，消除第三 / 四份包装复制（B26 §6.4、config §7.4 已立此议）。
2. **宿主级离场闸未接（B30 边界）**：管理页 VM `openSheet` 入口与 Activity 结果回调未串联 `canOpen`；离场 ~450ms 内宿主触摸由浮层窗口截获兜底，若需宿主级双闸，需另派单改 `SourceManageViewModel` / `BookSourceActivity`（reader 域同类缺口见 B30 条目）。
3. **导航切换双槽位时序**：§7.2 七处导航切换行保留直摘；若要求跳转前先播完离场动画，需设计双槽位（旧槽离场保留 + 新槽待起）时序，另派单评估。
4. **并发 WIP 口径**：工作树 autoTask / dict / replace 及 `ui/sheet/` 目录改动属其他在飞单；本单残留统计为验收时刻快照，各域最终结论以其独立验收为准。
5. **范围外遗留**：沿用既有台账（D3 `selectedSegments` 恒空集、D4 候选链接未接线、D5 固定分区模型未落地等），本单不收口。

## 11. 未验证项

- 编译与单测：本地禁编译（无 gradle / JDK21 / SDK），未跑 `./gradlew` / `kotlinc`；`SourceSheetExitControllerTest` 7 例是否全绿、新增 import 与 `OverflowSheet` 签名变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、450ms 延时与组件动画帧对齐、离场段窗口触摸截获，未在真机走查；观感由既有 `InkHalfSheet` 组件保证，本单仅接线状态口径。
- 动画时序：摘子树时机为代码静态推断（延时依据 = `MASK_DURATION_MS` 200ms + `RISE_DURATION_MS` 250ms），实际收束帧是否干净待 CI 出包后真机确认。
- 离场段配置重建：控制器由 `remember` 持有，旋转 / 进程重建随 Composable 生命周期；离场中重建则阶段与离场位不跨重建存活（与 B20 / B21 / B26 / config 同一边界，本单不解决）。
- 等效延时而非完成回调：`InkHalfSheet` 契约未暴露动画结束回调，沿用 B21 / B26 同一口径。

## 12. 交接备注

- 本单改动未 `git add / commit / push`；工作树改动待 CI 验收。
- 后续若改 `InkHalfSheet` 离场时长常量，`SourceSheetExitHost` 延时表达式自动跟随；若组件补完成回调，优先换回调、删等效延时分支。
- 其余域收口优先复用 B26 守卫口径；包装合并时机见 §10 第 1 条。
