锚点卡：docs/锚点/Q459-B29config收口.md

# 收尾 · B29 config 域 13 条常量 visible 收口核实与对账（Q459）

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74（本收尾方现取 sha256 前 12 位，与磁盘现行文件一致）。
- 逐字引用 1：「清单外不许新写代码；新增问题必须先给编号进表。」（docs/机制-总控必读.md:32）
- 逐字引用 2：「证明代码真的写入、证明旧符号真的清零」（docs/机制-子代理必读.md:18）
- 逐字引用 3：「不采信写码方的自评」（docs/机制-验收代理必读.md:8）
- 逐字引用 4：「这条判据对应哪份原始事实的哪一行」（docs/机制-文档子代理必读.md:16）
- 适用条款：只读核实为主，未改 autoTask / book·source / dict / replace 四域；复用既有 B26/B30 阶段机，未造第二份枚举与第二份派生口径；改动后先 grep 回证再下结论；每条判定带 文件:行 证据；未验证项如实披露，不把「报告里写了」当成已实现；禁 git 写、禁跑 gradlew / kotlinc / SDK，Kotlin 仅跑 `python3 data_scripts/kotlin-syntax-lint.py` 静态预检。

## 一、任务边界与判定口径

- 收口对象：`docs/问题清单.md:73` B29 行中 config 域 13 条，清单出自 `docs/修复-B26-常量visible接线.md` §6.3 第 19–31 条（命名参数 `visible = true`，条件渲染宿主 + dismiss 即摘子树，离场动画被跳过）。
- 范围红线：只核 `app/src/main/java/io/legado/app/ui/config/` 下与这 13 处浮层相关文件；autoTask / book·source / dict / replace 四域零触碰；components 红线 `HandleFileSheet.kt` 不动；未新建第二套阶段机。
- 判定口径（先假设「报告自述已落码」是错的，逐条回现役代码核实）：
  1. 「已改」判据：`visible` 不再恒传 true，而是来自阶段机派生（`xxxExit.visible`）；关闭路径真的走 `beginExit()` → `SheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树，不是 dismiss 即摘；打开点先过 `canOpen` 闸再 `open()`。
  2. 「未改」判据：现役代码仍恒传 true，或关闭路径仍直接摘子树。
  3. 基线对照：以 B29 收口提交 `14f646a^`（即 `780ec90` 之后、B29 落码前）为基线快照，逐条 `git show` 取基线原文；现役行号以核验时点 HEAD `267f775` 工作树为准（config 域自 `f896179` 起零改动，`git diff --name-only f896179..HEAD -- app/src/main/java/io/legado/app/ui/config/` 空命中）。
- 现役提交链：`14f646a`「修B29:config域浮层两段式收口(13命名+8位置参+8间接受害,SheetExitController复用B26闸,7断言)」→ `6cb0198`「修B30:抽SheetExitPhaseGate纯JVM基类(枚举归位/isComposed单源/tryOpen宿主闸)」。本收尾方未做任何 `git add / commit / push`。

## 二、13 条逐条核实清单（一条不漏）

> 路径缩写：`CFG=app/src/main/java/io/legado/app/ui/config`；`SET=CFG/settings`。基线原文取自 `14f646a^`；现役行号取自 HEAD `267f775`。

### 1. ThemeListDialog.kt 删除确认弹层（B26 清单第 19 条）

- 基线快照（`14f646a^`，ThemeListDialog.kt 第 102 行）原文 `visible = true,`；`git show 14f646a^:<仓内 ThemeListDialog.kt 路径>` 回源。
- 现役现状：**已改**。
  - 控制器：`CFG/ThemeListDialog.kt:59` `val exit = rememberSheetExitController()`。
  - 打开闸：`CFG/ThemeListDialog.kt:97` `if (exit.canOpen) {`；`CFG/ThemeListDialog.kt:99` `exit.open()`。
  - 收束宿主：`CFG/ThemeListDialog.kt:108` `SheetExitHost(exit, onFinished = { deleteIndex = null }) {`。
  - 派生可见性：`CFG/ThemeListDialog.kt:110` `visible = exit.visible,`。
  - 关闭路径：`CFG/ThemeListDialog.kt:118` `onDismiss = { exit.beginExit() },`；子树在离场段保留组合，`SheetExitHost` 按 `ComponentPalette.DURATION_MASK`（200ms，`app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt:94`）等效延时后 `finishExit()` 并回调清 `deleteIndex` 摘子树。
- 处置：无需补改；两段式链路完整。

### 2. ConfigActivity.kt 图标选择弹层（B26 清单第 20 条）

- 基线快照（`14f646a^`，ConfigActivity.kt 第 129 行）原文 `visible = true,`。
- 现役现状：**已改**。
  - 控制器（Activity 字段，跨动作回调桥接）：`CFG/ConfigActivity.kt:55` `private val iconExit = SheetExitController(NoteDialogExitGate(), mutableStateOf(false))`。
  - 打开闸：`CFG/ConfigActivity.kt:95` `if (iconExit.canOpen) {`；`CFG/ConfigActivity.kt:97` `iconExit.open()`。
  - 收束宿主：`CFG/ConfigActivity.kt:148` `SheetExitHost(iconExit, onFinished = { iconPickerState = null }) {`。
  - 派生可见性：`CFG/ConfigActivity.kt:150` `visible = iconExit.visible,`。
  - 关闭路径：`CFG/ConfigActivity.kt:156` `onDismiss = { iconExit.beginExit() },`。
- 处置：无需补改。观察项：Activity 字段控制器阶段与离场位不跨配置变更 / 进程重建存活（见 §七 F-5，沿用 B20/B21/B26 同一边界）。

### 3. BottomBarSkinActivity.kt 皮肤删除确认弹层（B26 清单第 21 条）

- 基线快照（`14f646a^`，BottomBarSkinActivity.kt 第 237 行）原文 `visible = true,`。
- 现役现状：**已改**。
  - 控制器：`CFG/BottomBarSkinActivity.kt:182` `val confirmExit = rememberSheetExitController()`。
  - 打开闸：`CFG/BottomBarSkinActivity.kt:245` `if (confirmExit.canOpen) {`；`CFG/BottomBarSkinActivity.kt:247` `confirmExit.open()`。
  - 收束宿主：`CFG/BottomBarSkinActivity.kt:256` `SheetExitHost(confirmExit, onFinished = { confirmDelete = null }) {`。
  - 派生可见性：`CFG/BottomBarSkinActivity.kt:258` `visible = confirmExit.visible,`。
  - 关闭路径：`CFG/BottomBarSkinActivity.kt:263` `onDismiss = { confirmExit.beginExit() },`。
- 处置：无需补改。同屏菜单 host 另接 `menuExit`（`CFG/BottomBarSkinActivity.kt:181`、`:225`、`:227`、`:235`、`:236`），随本批一并两段式；菜单内 edit / export / share / delete 为同屏导航切换，保留旧直摘口径（`CFG/BottomBarSkinActivity.kt:240-244`，理由见 §五 N3 口径留档）。

### 4. WelcomeSection.kt 欢迎页时间数字弹层（B26 清单第 22 条）

- 基线快照（`14f646a^`，WelcomeSection.kt 第 68 行）原文 `visible = true,`。
- 现役现状：**已改**。
  - 控制器：`CFG/sections/WelcomeSection.kt:36` `val timeExit = rememberSheetExitController()`。
  - 打开闸：`CFG/sections/WelcomeSection.kt:44` `if (timeExit.canOpen) {`；`CFG/sections/WelcomeSection.kt:46` `timeExit.open()`。
  - 收束宿主：`CFG/sections/WelcomeSection.kt:81` `SheetExitHost(timeExit, onFinished = { timeTarget = false }) {`。
  - 派生可见性：`CFG/sections/WelcomeSection.kt:83` `visible = timeExit.visible,`。
  - 关闭路径：`CFG/sections/WelcomeSection.kt:90` `onDismiss = { timeExit.beginExit() },`。
- 处置：无需补改。配图 host 另接 `imageExit`（`CFG/sections/WelcomeSection.kt:37`、`:56`、`:58`、`:68`、`:70`、`:96`、`:98`、`:100`）。

### 5. OtherSection.kt 其他页数字弹层（B26 清单第 23 条）

- 基线快照（`14f646a^`，OtherSection.kt 第 174 行）原文 `visible = true,`。
- 现役现状：**已改**。
  - 控制器：`CFG/sections/OtherSection.kt:41` `val numberExit = rememberSheetExitController()`。
  - 打开闸：`CFG/sections/OtherSection.kt:117`、`:133`、`:141`、`:149`、`:169`、`:175`、`:193` 共 7 处 `if (numberExit.canOpen) {`，对应 `:121`、`:137`、`:145`、`:153`、`:171`、`:177`、`:195` 共 7 处 `numberExit.open()`。
  - 收束宿主：`CFG/sections/OtherSection.kt:223` `SheetExitHost(numberExit, onFinished = { numberTarget = null }) {`。
  - 派生可见性：`CFG/sections/OtherSection.kt:225` `visible = numberExit.visible,`。
  - 关闭路径：`CFG/sections/OtherSection.kt:231` `onDismiss = { numberExit.beginExit() },`。
- 处置：无需补改。

### 6. OtherSection.kt 其他页文本弹层（B26 清单第 24 条）

- 基线快照（`14f646a^`，OtherSection.kt 第 185 行）原文 `visible = true,`。
- 现役现状：**已改**。
  - 控制器：`CFG/sections/OtherSection.kt:42` `val textExit = rememberSheetExitController()`。
  - 打开闸：`CFG/sections/OtherSection.kt:97`、`:105`、`:182` 共 3 处 `if (textExit.canOpen) {`，对应 `:101`、`:109`、`:186` 共 3 处 `textExit.open()`。
  - 收束宿主：`CFG/sections/OtherSection.kt:236` `SheetExitHost(textExit, onFinished = { textTarget = null }) {`。
  - 派生可见性：`CFG/sections/OtherSection.kt:238` `visible = textExit.visible,`。
  - 关闭路径：`CFG/sections/OtherSection.kt:243` `onDismiss = { textExit.beginExit() },`。
- 处置：无需补改。

### 7. CoverFontSection.kt 封面字号数字弹层（B26 清单第 25 条）

- 基线快照（`14f646a^`，CoverFontSection.kt 第 75 行）原文 `visible = true,`。
- 现役现状：**已改**。
  - 控制器：`CFG/sections/CoverFontSection.kt:46` `val sizeExit = rememberSheetExitController()`。
  - 打开闸：`CFG/sections/CoverFontSection.kt:68` `if (sizeExit.canOpen) {`；`CFG/sections/CoverFontSection.kt:70` `sizeExit.open()`。
  - 收束宿主：`CFG/sections/CoverFontSection.kt:81` `SheetExitHost(sizeExit, onFinished = { sizeTarget = null }) {`。
  - 派生可见性：`CFG/sections/CoverFontSection.kt:83` `visible = sizeExit.visible,`。
  - 关闭路径：`CFG/sections/CoverFontSection.kt:89` `onDismiss = { sizeExit.beginExit() },`。
- 处置：无需补改。

### 8. SettingSheetHost.kt 薄封装 InkBottomSheetHost（B26 清单第 26 条）

- 基线快照（`14f646a^`，SettingSheetHost.kt 第 16 行）原文 `InkBottomSheet(visible = true, onDismiss = onDismiss, title = title, content = content)`（函数无 `visible` 形参，内部写死 true）。
- 现役现状：**已改**。
  - 形参：`SET/SettingSheetHost.kt:15` `visible: Boolean,`。
  - 透传：`SET/SettingSheetHost.kt:20` `InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title, content = content)`。
- 处置：无需补改。Host 是其余私有 Sheet（ColorPickerSheet / BackgroundImageSheet / LanMenuSheet / 菜单 host / 配色 host 等）间接受害的根因；形参改派生后，这一批随 Host 一并获得离场路径（证据见 §三 附条）。

### 9. SettingSheets.kt NumberPickerSheet 内层 InkBottomSheet（B26 清单第 27 条）

- 基线快照（`14f646a^`，SettingSheets.kt 第 54 行）原文 `InkBottomSheet(visible = true, onDismiss = onDismiss, title = title) {`；同快照第 52 行另有早退 `if (!visible) return`。
- 现役现状：**已改**。
  - 形参：`SET/SettingSheets.kt:41` `visible: Boolean,`。
  - 透传：`SET/SettingSheets.kt:55` `InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {`。
  - 早退已删：现役函数体首行直接进 `var value by remember(...)`（`SET/SettingSheets.kt:54`），无 `if (!visible) return`；离场段子树保留组合，收束帧条件由 `InkBottomSheet` 内部承担（`app/src/main/java/io/legado/app/ui/components/InkBottomSheet.kt:129` `if (progress.value == 0f && !visible) return`）。
- 处置：无需补改。

### 10. SettingSheets.kt TextEditSheet 内层 InkBottomSheet（B26 清单第 28 条）

- 基线快照（`14f646a^`，SettingSheets.kt 第 109 行）原文 `InkBottomSheet(visible = true, onDismiss = onDismiss, title = title) {`；同快照第 107 行为早退 `if (!visible) return`。
- 现役现状：**已改**。形参 `SET/SettingSheets.kt:100` `visible: Boolean,`；透传 `SET/SettingSheets.kt:110` `InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {`；早退已删（现役 `SET/SettingSheets.kt:109` 为 B29 口径注释，无 return）。
- 处置：无需补改。

### 11. SettingSheets.kt ListPickSheet 内层 InkBottomSheet（B26 清单第 29 条）

- 基线快照（`14f646a^`，SettingSheets.kt 第 154 行）原文 `InkBottomSheet(visible = true, onDismiss = onDismiss, title = title) {`；同快照第 153 行为早退 `if (!visible) return`。
- 现役现状：**已改**。形参 `SET/SettingSheets.kt:146` `visible: Boolean,`；透传 `SET/SettingSheets.kt:155` `InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {`；早退已删。
- 处置：无需补改。

### 12. SettingSheets.kt MultiChoiceSheet 内层 InkBottomSheet（B26 清单第 30 条）

- 基线快照（`14f646a^`，SettingSheets.kt 第 194 行）原文 `InkBottomSheet(visible = true, onDismiss = onDismiss, title = title) {`；同快照第 192 行为早退 `if (!visible) return`。
- 现役现状：**已改**。形参 `SET/SettingSheets.kt:186` `visible: Boolean,`；透传 `SET/SettingSheets.kt:195` `InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {`；早退已删。
- 处置：无需补改。

### 13. SettingSheets.kt ConfirmSheet 内层 InkBottomSheet（B26 清单第 31 条）

- 基线快照（`14f646a^`，SettingSheets.kt 第 235 行）原文 `InkBottomSheet(visible = true, onDismiss = onDismiss, title = title) {`；同快照第 234 行为早退 `if (!visible) return`。
- 现役现状：**已改**。形参 `SET/SettingSheets.kt:227` `visible: Boolean,`；透传 `SET/SettingSheets.kt:236` `InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title) {`；早退已删。
- 处置：无需补改。

### 13 条汇总

- 已改：13 条；未改：0 条；本收尾方本轮未补改任何 main 源集代码（现役代码已在 `14f646a` 落码、`6cb0198` 完成 B30 单源收敛）。
- 13 条关闭路径全部为 `onDismiss = { xxxExit.beginExit() }` → `SheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树；无一处 dismiss 即摘子树。

## 三、阶段机复用与单源派生核查（无第二份口径）

### 3.1 复用链（只引既有，未造第二套）

- 阶段枚举三态 `OPEN / EXITING / REMOVED` 定义唯一处：`app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:12` `enum class ReaderSheetExitPhase {`。
- 派生口径唯一事实源（基类）：`SheetExitPhaseGate.kt:144-145` `visible = phase == ReaderSheetExitPhase.OPEN`；`SheetExitPhaseGate.kt:148-149` `isComposed = phase != ReaderSheetExitPhase.REMOVED`；`SheetExitPhaseGate.kt:152-153` `canOpen = phase != ReaderSheetExitPhase.EXITING`。
- B26 守卫 `NoteDialogExitGate` 继承该基类：`app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:11` `class NoteDialogExitGate : SheetExitPhaseGate()`，自身只补 `app/src/main/java/io/legado/app/ui/book/read/NoteDialogExitGate.kt:22` `open() = enterOpen()`，未自写 `visible / isComposed / canOpen` 判断。
- config 域薄封装 `SET/SheetExitController.kt`：`SET/SheetExitController.kt:9` `import io.legado.app.ui.book.read.NoteDialogExitGate`；`SET/SheetExitController.kt:39-40` `visible`、`SET/SheetExitController.kt:43-44` `isComposed`、`SET/SheetExitController.kt:47-48` `canOpen` 全部 `get() = gate.xxx` 委托基类派生；`SET/SheetExitController.kt:55` `open()`、`SET/SheetExitController.kt:58` `beginExit()`、`SET/SheetExitController.kt:64` `finishExit()` 转发守卫；`SET/SheetExitController.kt:74` `rememberSheetExitController()` 工厂；`SET/SheetExitController.kt:89` `SheetExitHost` 收束宿主。
- 控制器实例化两处：`SET/SheetExitController.kt:75`（工厂内 `remember { NoteDialogExitGate() }`）；`CFG/ConfigActivity.kt:25` import + `CFG/ConfigActivity.kt:55` Activity 字段直构。

### 3.2 第二份阶段机排查（grep 回证）

- 命令：`grep -rnE "enum class|sealed (class|interface)|ExitGate|ExitPhase|Stage" app/src/main/java/io/legado/app/ui/config/ --include=*.kt`。
- 命中的 sealed 类型共 4 个，全部是行模型 / 槽位状态，不是离场阶段机：`CFG/b3/B3SettingModels.kt:18` `sealed interface B3Row`；`CFG/b3/B3SheetTargets.kt:12` `internal sealed class B3SheetTarget`；`CFG/sections/BackupSection.kt:384` `private sealed interface LanState`；`SET/SettingModels.kt:14` `sealed interface SettingRow`。
- config 域内无第二份离场守卫类、无第二份阶段枚举、无第二份 OPEN→EXITING→REMOVED 迁移规则；除 `NoteDialogExitGate` 外无其他离场闸 import。

### 3.3 附条：位置参数 8 条 + Host 间接受害 8 处（随本批一并接线，已改证据）

- 位置参数形式（B26 grep 只匹配命名参数而漏计，B29 实测补接）：
  1. `CFG/sections/BackupSection.kt:185` `TextEditSheet(textExit.visible, ...)`（文本配置弹层）。
  2. `CFG/sections/BackupSection.kt:194` `NumberPickerSheet(numberExit.visible, ...)`（数字配置弹层）。
  3. `CFG/sections/BackupSection.kt:202` `contentExit.visible,`（备份内容多选）。
  4. `CFG/sections/BackupSection.kt:219` `ignoreExit.visible,`（恢复忽略多选）。
  5. `CFG/sections/BackupSection.kt:262` `ListPickSheet(restoreExit.visible, ...)`（恢复文件清单）。
  6. `CFG/sections/BackupSection.kt:272` `TextEditSheet(pwExit.visible, ...)`（本地密码）。
  7. `CFG/sections/ThemeSection.kt:165` `NumberPickerSheet(numberExit.visible, ...)`（主题数字弹层）。
  8. `CFG/sections/ThemeSection.kt:184` `TextEditSheet(nameExit.visible, ...)`（主题命名弹层）。
- Host 间接受害（随第 8 条 Host 形参改造获得派生 visible）：
  1. `CFG/sections/ThemeSection.kt:220` 私有 `ColorPickerSheet` 内 `InkBottomSheetHost(visible = visible, ...)`；形参 `:210`；颜色槽位接 `colorExit`（`CFG/sections/ThemeSection.kt:59`）。
  2. `CFG/sections/ThemeSection.kt:244` 私有 `BackgroundImageSheet` 内 `InkBottomSheetHost(visible = visible, ...)`；形参 `:239`；背景图槽位接 `bgExit`（`CFG/sections/ThemeSection.kt:61`）。
  3. `CFG/sections/BackupSection.kt:317` 私有 `LanMenuSheet` 内 `InkBottomSheetHost(visible = visible, ...)`；形参 `:316`；局域网菜单槽位接 `lanExit`（`CFG/sections/BackupSection.kt:77`）。
  4. `CFG/sections/BackupSection.kt:303` Qr 弹层 host `InkBottomSheetHost(visible = qrExit.visible, ...)`；`qrExit` 见 `:78`。
  5. `CFG/BottomBarSkinActivity.kt:236` 菜单 host `InkBottomSheetHost(visible = menuExit.visible, ...)`；`menuExit` 见 `:181`。
  6. `CFG/sections/BackupSection.kt:238` 备份模式 host `InkBottomSheetHost(visible = modeExit.visible, ...)`；`modeExit` 见 `:74`。
  7. `CFG/BottomBarSkinAssignActivity.kt:211` 配色 host `InkBottomSheetHost(visible = paletteExit.visible, ...)`；`paletteExit` 见 `:124`。
  8. `CFG/sections/WelcomeSection.kt:98` 配图 host `InkBottomSheetHost(visible = imageExit.visible, ...)`；`imageExit` 见 `:37`。

## 四、grep 回证前后对照（命令 + 计数 + 行号）

### 4.1 旧常量传法命中数：前 vs 后

- 基线（`14f646a^`，B29 落码前）config 域：命名参数 `visible = true` 13 条（B26 清单第 19–31 条，逐条基线原文见 §二）；位置参数 `Sheet(true, ...)` 8 条；Host 内部写死 true 1 处。
- 现役（HEAD `267f775`）config 域命名参数残留命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/config/ --include=*.kt
```

现役命中 4 条：`CFG/b3/B3SettingScreen.kt:151`、`CFG/b3/B3SettingScreen.kt:164`、`CFG/b3/B3SettingScreen.kt:180`、`CFG/b3/B3SettingScreen.kt:193`，属 B26 清单之后由 B3 批次新写代码引入，不在本单 13 条口径内（处置见 §七 F-1）。按 B26 清单 13 条口径：残留 0。

- 现役 config 域位置参数命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/config/ --include=*.kt
```

原样输出：空，`exit=1`（位置参数残留 0）。多行 `true,` 首参兜底：`grep -rnE "^\s*true,\s*$" app/src/main/java/io/legado/app/ui/config/ --include=*.kt` 仅 `CFG/b3/B3SettingActions.kt:81` 一处，系 `FileUtils.delete(..., true)` 递归布尔实参，非 Sheet 首参。

### 4.2 新派生写法命中行号（现役）

- 派生 `visible = xxxExit.visible` / `xxxExit.visible` 位置参数共 22 处，逐条行号：
  - `CFG/BottomBarSkinActivity.kt:236`（menuExit.visible）、`:258`（confirmExit.visible）。
  - `CFG/BottomBarSkinAssignActivity.kt:211`（paletteExit.visible）。
  - `CFG/ConfigActivity.kt:150`（iconExit.visible）。
  - `CFG/sections/BackupSection.kt:185`（textExit.visible）、`:194`（numberExit.visible）、`:202`（contentExit.visible）、`:219`（ignoreExit.visible）、`:238`（modeExit.visible）、`:262`（restoreExit.visible）、`:272`（pwExit.visible）、`:281`（lanExit.visible）、`:303`（qrExit.visible）。
  - `CFG/sections/CoverFontSection.kt:83`（sizeExit.visible）。
  - `CFG/sections/OtherSection.kt:225`（numberExit.visible）、`:238`（textExit.visible）。
  - `CFG/sections/ThemeSection.kt:165`（numberExit.visible）、`:173`（colorExit.visible）、`:184`（nameExit.visible）、`:196`（bgExit.visible）。
  - `CFG/sections/WelcomeSection.kt:83`（timeExit.visible）、`:98`（imageExit.visible）。
- 闸位与打开点：`.open()` 调用共 47 处（不含控制器自身定义 `SET/SheetExitController.kt:55`）；其中 46 处位于 `if (...Exit.canOpen)` 块内；未过闸 1 处为 `CFG/sections/BackupSection.kt:301` 程序化登记（O-1，见 §七 F-2）。`beginExit()` 调用 29 处。
- 全仓 `visible = true` 现役总计 8 条，分类：`CFG/b3/B3SettingScreen.kt` 4（本单 §七 F-1）；`app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditState.kt:93`、`:232`（状态持有类内部 `this.visible = true` 赋值，非浮层传参，reader 域外）；`app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt:47`（components 红线，契约冻结）；`app/src/main/java/io/legado/app/ui/main/bookshelf/InkShelfFragment.kt:286`（注释文字，非代码）。autoTask / book·source / dict / replace 四域现役命名参数残留均为 0（逐域 `wc -l` 回证：autoTask 0、book/source 0、dict 0、replace 0）。

### 4.3 静态预检

- 命令：`python3 data_scripts/kotlin-syntax-lint.py` 传入本批 13 个相关文件（11 个 main 源集 + 控制器 + 测试）。
- 原样输出：`KOTLIN-LINT: PASS（13 个文件）`。括号配平与 if/while 条件尾逗号两项硬错零命中；未跑 `./gradlew` / `kotlinc`，编译与单测结果以云端 CI 为准。

## 五、判定不改 / 留档条目（逐条读过，非静默跳过）

- N1. `CFG/sections/BackupSection.kt:232-233` `AutoBackupSheet`（定义 `:326`）——**不改**。宿主 `if (autoBackupSheet)` 直挂内联 `Column`（`:331`），未包 `InkBottomSheetHost` / `InkBottomSheet` / `InkHalfSheet`，无 `visible` 形参、无模态遮罩与离场动画组件；它是条件渲染的内联配置区块，不是浮层，两段式无挂载点。若日后包进模态 host，另派单补离场闸。
- N2. `SET/SettingList.kt:119` `visible = sheetVisible,`——**不改**。`:112` `var sheetVisible by remember { mutableStateOf(false) }`，`:114` 打开置 true，`:128` dismiss 置 false；传入的是派生状态而非常量 true，与 B26 已认可的派生 visible 口径一致。
- N3. 同屏导航 / 任务触发直摘按钮群——**保留旧直摘口径，不改**：`CFG/BottomBarSkinActivity.kt:240-244`（edit / export / share / delete 跳转编辑 / 导出 / 分享 / 确认框）；`CFG/sections/BackupSection.kt:242`、`:249`（备份任务异步触发后直摘）；`CFG/sections/BackupSection.kt:283`、`:290`（局域网菜单 → Qr 弹层 / 扫码页导航切换）。遮罩 / 拖拽关闭仍走各自两段式控制器。副作用时机与旧版一致；若验收要求导航切换也播下降动画，另派单评估「先离场再跳转」时序。
- N4. `app/src/main/java/io/legado/app/ui/components/HandleFileSheet.kt:47`——**不改（红线）**。components 契约冻结，本单零改动。

## 六、自查反例（先假设「已改」结论错误，逐条找推翻证据并排除）

- 反例 1：派生 visible 看似来自控制器，但控制器若绕过基类自写阶段判断，单源口径即破。核查：`NoteDialogExitGate` 全文仅 `open()` 转发 `enterOpen()`，无自写 `visible / isComposed / canOpen`；`SET/SheetExitController.kt:39-40/:43-44/:47-48` 三处均为 `get() = gate.xxx` 委托；基类派生唯一处在 `SheetExitPhaseGate.kt:144-153`。排除：无第二份派生表达式。
- 反例 2：关闭路径看似两段式，实际 onDismiss 仍直清槽位、子树瞬摘，`beginExit()` 是空挂。核查：§二 13 条 onDismiss 全部为 `{ xxxExit.beginExit() }`；槽位清空只出现在 `SheetExitHost(onFinished = ...)` 回调内（延时收束后），宿主槽位状态本身不在 dismiss 出口被直接置空。排除：无 dismiss 即摘。
- 反例 3：离场段重复打开是否被拦。核查：`canOpen` 转发基类 `phase != EXITING`；46 处用户触发入口先过闸再 `open()`；唯一未过闸的 `BackupSection.kt:301` 是状态机程序化登记，竞态窗口窄（离场段菜单不可达，无法再次发起 send），登记为观察项 F-2，不阻断收口。
- 反例 4：config 域是否另造了本地阶段枚举 / 布尔状态机。核查：§3.2 grep 命中的 sealed 类型全是行模型 / 槽位状态（B3Row / B3SheetTarget / LanState / SettingRow），无离场阶段枚举；无第二份守卫类与迁移规则。排除：无第二套阶段机。
- 反例 5：未打开 / 已摘除时 visible 是否仍恒显。核查：基类 REMOVED 段 `visible=false`；控制器初始 `exitingState=false`、守卫初始 REMOVED；测试 `SET/.../SheetExitControllerTest.kt:27` `初始为摘除态 不可见 子树不在 可接受新打开` 覆盖初始摘除态不可见。排除：无恒显。
- 反例 6：B3 的 4 条常量 true 是否应计入本单 13 条。核查：B26 §6.3 清单第 19–31 条与锚点卡 `docs/锚点/B29-域外常量visible-config.md` §一均无 `b3/` 文件；B3 设置面由提交 `90dfc04` 后于 B29 验收入库，属清单后新增代码。不计入 13 条验收口径，但按新发现问题登记（§七 F-1），不静默跳过。
- 反例 7：报告自述「E3 已落码待 CI」是否与现役代码一致。核查：未采信自述，逐条 `git show 14f646a^` 取基线 + 现役源码回读；13 条基线行均为 `visible = true`，现役均已派生；落码提交 `14f646a` 已在 HEAD `267f775` 祖先链内（`git merge-base --is-ancestor` 回证）。排除：自述与代码一致，无「报告写了、代码没变」。

## 七、新发现问题（每条带处置状态词）

- F-1（待另派单 · 登记跟踪）：`CFG/b3/B3SettingScreen.kt:151`、`:164`、`:180`、`:193` 四条 `visible = true` 常量传法；宿主 `CFG/b3/B3SettingScreen.kt:105` `B3SheetHost(target = sheetTarget, onDismiss = { sheetTarget = null })` dismiss 直清槽位摘子树；`b3/` 包内零控制器、零 `canOpen` 闸、零 `SheetExitHost`（grep `Exit|canOpen|beginExit|SheetExitHost` 空命中，exit=1）。内层四个 Sheet（TextEditSheet / NumberPickerSheet / ListPickSheet / ConfirmSheet）已收派生 visible 形参，仅 B3 调用点仍传常量 true，同形态缺陷。处置：B3 设置面收口批次复用 `SheetExitController` / `SheetExitHost` 两段式接线（每槽位一份控制器、打开点过 `canOpen` 闸、收束回调清槽位）；本单按范围红线未改。
- F-2（留观 · 低风险，沿用 B29 验收 O-1）：`CFG/sections/BackupSection.kt:301` `LaunchedEffect(lanSheet) { qrExit.open() }` 程序化登记未过 `canOpen` 闸。建议后续在 effect 内补 `if (qrExit.canOpen) qrExit.open()`，或在守卫口径内登记「状态机入口例外」；当前静态判断竞态窗口窄，不阻断本单。
- F-3（留观 · 阶段账不闭环，沿用 B29 验收 O-4）：`CFG/sections/BackupSection.kt:283`、`:290` 菜单 → Qr 导航时 `lanExit` 未经 `beginExit()`，阶段停留 OPEN。OPEN 段 `canOpen` 仍为 true，不会误拒重开，槽位状态承担判重，行为无可见缺陷；建议后续导航切走时补一次 `beginExit()` 或登记「导航离场不迁阶段」例外。
- F-4（待 CI 复核 · 沿用 B29 验收 O-2）：`SET/.../SheetExitControllerTest.kt` 使用 `mutableStateOf`，仓内既无同型测试方法体先例背书（`ReaderSheetStateTest.kt:15` 为注释文字）；`mutableStateOf` 属 compose-runtime 普通工厂函数，JVM 下可实例化本身成立，但 7 例单测能否在单测 classpath 跑绿以云端 CI 为准。
- F-5（既有边界 · 本单不解决，沿用 B29 验收 O-3）：`CFG/ConfigActivity.kt:55` `iconExit` 为 Activity 字段，控制器阶段与离场位不跨配置变更 / 进程重建存活；与 B20 / B21 / B26 同一边界。
- F-6（留观待核 · 交互缺口，非常量 visible 缺陷本身）：`CFG/sections/ThemeSection.kt:246`、`:247` `BackgroundImageSheet` 选图 / 清除按钮回调未自带 `onDismiss`（对照同文件 `:229` `ColorPickerSheet` 选色后 `onDismiss()`）；动作完成后弹层不自行关闭。建议 B3 批次一并核对是否应在动作回调后接 `bgExit.beginExit()`。

## 八、未验证项与边界

- 编译与单测：沙箱无 gradle / JDK21 / SDK，未跑 `./gradlew` / `kotlinc`；`SheetExitControllerTest` 7 例是否全绿、新增 import 与签名变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、200ms 延时与组件动画帧对齐、离场段触摸截获范围，未真机走查；观感由既有 `InkBottomSheet` 组件保证，本收尾方仅静态接线口径核实。
- 动画时序：摘子树时机为代码静态推断（延时依据 = `DURATION_MASK` 200ms，`ComponentPalette.kt:94`），实际收束帧是否干净待 CI 出包后真机确认。
- 域外四域（autoTask 4 / book·source 12 / dict 2 / replace 3）已由各自分域单验收 PASS（source 见 `docs/验收-B29-常量visible-source.md`，三域见 `docs/验收-B29-常量visible-其余三域.md`）；本单未复核其实机表现。
- 核验时点 `app/` 工作树无本单范围内未提交改动；bookshelf 域并发改动已由后续提交 `267f775` 收口，本收尾方未读其 diff、未触碰。

## 九、收口结论

- **B29 config 域按 B26 清单口径 13 条可判收口（静态层；编译 / 单测等 CI）。**
- 依据：
  1. 13 条逐条核实为「已改」、0 条「未改」：现役 `visible` 全部来自 `SheetExitController` 派生（转发 B26 `NoteDialogExitGate`，继承 B30 `SheetExitPhaseGate` 基类单源派生），无一处恒传 true；关闭路径全部走 `beginExit()` → `SheetExitHost` 延时收束 → `finishExit()` + 回调清槽位摘子树，无 dismiss 即摘。
  2. 阶段机复用既有链路，config 域无第二份枚举、无第二份守卫类、无第二份派生表达式（§3.2 grep 回证）。
  3. 打开点 46/47 过 `canOpen` 闸；未过闸 1 处为状态机程序化登记（F-2，低风险留观）。
  4. 静态预检 13 个相关文件 `KOTLIN-LINT: PASS`；7 例纯 JVM 断言齐全，测试名无禁字符。
  5. 位置参数 8 条 + Host 间接受害 8 处随本批一并接线，证据见 §3.3。
- 附条件：config 域内 B3 设置面新增 4 条同形态残留（F-1）不在 B26 清单 13 条口径内，已登记待另派单；在 F-1 收口前，「config 域全域零常量 visible 残留」的绝对结论不成立，本结论仅覆盖 B29 清单 config 域 13 条。
