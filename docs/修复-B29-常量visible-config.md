> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-B29：config 域浮层可见性传常量 true 导致离场动画被跳过（两段式关闭收口）

锚点卡：`docs/锚点/B29-域外常量visible-config.md`

> 日期：2026-11-26 ｜ 执行代理：B29 写码专单（config 域）｜ 基线：本单未执行 git add / commit / push（沙箱禁提交）。
> 锚点卡：`docs/锚点/B29-域外常量visible-config.md`（六条判据与边界以它为准）。
> 清单来源：`docs/修复-B26-常量visible接线.md` §6.3「config 域（13 条）」。
> 缺陷条目：`docs/问题清单.md:73` B29 行。
> 范式来源：`docs/修复-B21-半屏离场动画.md`（两段式关闭实现范式）+ `docs/修复-B26-常量visible接线.md`（阶段机 `ReaderSheetExitPhase` / `NoteDialogExitGate`）。
> E3 结果：✓ 已落码待 CI——grep 回证每处改动真写入；本地禁编译 / 禁跑测（沙箱无 gradle / JDK21 / SDK），编译与单测结果一律标「未验证，等 CI」。
> 仓库：`bqdlz676/legado`；工作树改动，未做任何 `git add / commit / push`。

## 1. 缺陷原貌与本单结论

缺陷原貌（锚点卡 §一 事实锚点，已对照 HEAD 基线逐条复核现役代码）：

- B26 清单 config 域 13 条，均为条件渲染宿主 + dismiss 即摘子树，`visible = true` 恒真；`InkBottomSheet.kt:123-126` 的 `visible=false` 离场分支（遮罩回亮 / Sheet 下降，tween 时长 `ComponentPalette.DURATION_MASK`=200ms，`ComponentPalette.kt:94`）永不执行。
- 本单另实测出 B26 grep 漏计的**位置参数形式** `Sheet(true, ...)` 8 条（BackupSection 6、ThemeSection 2），与命名参数同形态缺陷，已一并接线。
- 薄封装 `InkBottomSheetHost`（旧 `SettingSheetHost.kt:14`）内部把 `InkBottomSheet(visible = true, ...)` 写死，使未经命名参数传 visible 的私有 Sheet（ColorPickerSheet / BackgroundImageSheet / LanMenuSheet / 菜单 host / 配色 host 等）间接受害；Host 形参改为派生 visible 后，这一批一并获得离场路径。

本单处置口径（对齐 B21 / B26 两段式关闭）：

1. 关闭不直摘：每个弹层槽位各持一份离场控制器；dismiss 先走 `beginExit()` 进离场段，派生 `visible=false`，弹层子树保留组合，播组件内离场动画；写库 / 副作用仍在决策时刻执行，时机与旧版一致。
2. 收束再摘：`SheetExitHost` 在离场段按 `ComponentPalette.DURATION_MASK`（200ms）等效延时收束，`finishExit()` 后回调宿主清空槽位状态、摘除子树。摘除时机有明确依据，不写「立即摘」。
3. 离场段拒收：控制器 `canOpen` 在 EXITING 段为 false；每个打开点先过此闸再 `open()`，离场动画播放中不叠新弹层。
4. 阶段机复用 B26 `NoteDialogExitGate`（其内部复用 B21 `ReaderSheetExitPhase`），未造第二份枚举、未复制第三份迁移规则；新增 `SheetExitController` 只是 Compose 层薄封装。

编译结论：**未验证，等 CI**。沙箱无 gradle / JDK21 / SDK，本地未跑 `./gradlew` / `kotlinc`；新增 7 条纯 JVM 断言是否全绿以云端 CI 为准。

## 2. 改动清单（绝对路径）

### 2.1 新增文件

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt`
  - `SheetExitController`：internal 构造，包装 B26 `NoteDialogExitGate` + 一份 `MutableState<Boolean>` 离场位；派生只读 `visible` / `isComposed` / `canOpen` / `isExiting`；`open()` / `beginExit()` / `finishExit()` 转发守卫并同步离场位。
  - `rememberSheetExitController()`：Compose 工厂，每个槽位各持一份控制器，互不串阶段。
  - `SheetExitHost(controller, onFinished, content)`：离场段按 `DURATION_MASK` 等效延时后 `finishExit()` 并回调宿主清槽位摘子树；非离场段不动作。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/test/java/io/legado/app/ui/config/settings/SheetExitControllerTest.kt`
  - 7 例纯 JVM 断言：初始摘除态、open 后在场态、关闭瞬间子树仍在且 visible 已 false、离场段拒收重复打开、收束后摘除、空操作兜底、收束后重开回到在场态。

### 2.2 修改文件（main 源集，均未提交）

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt`
  - 五个内层 Sheet（NumberPickerSheet / TextEditSheet / ListPickSheet / MultiChoiceSheet / ConfirmSheet）删除 `if (!visible) return` 早退；`InkBottomSheet(visible = visible, ...)` 透传派生可见性。早退删除的理由：离场段子树必须保留组合，否则动画被摘子树砍掉；保留帧条件仍由 `InkBottomSheet` 内部 `progress.value == 0f && !visible` 收束。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/settings/SettingSheetHost.kt`
  - `InkBottomSheetHost` 新增 `visible: Boolean` 首形参，透传 `InkBottomSheet`；不再内部写死 `visible = true`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt`
  - 图标选择弹层：Activity 字段 `iconExit`（`SheetExitController(NoteDialogExitGate(), mutableStateOf(false))`）；打开点先过 `canOpen` 闸；`SheetExitHost` 收束清 `iconPickerState`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/ThemeListDialog.kt`
  - 删除确认弹层：`exit` 控制器；打开点 `canOpen` 闸；`SheetExitHost` 收束清 `deleteIndex`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/BottomBarSkinActivity.kt`
  - 菜单 host：`menuExit`；删除确认：`confirmExit`；两处打开点过闸；`SheetExitHost` 分别收束。菜单内 edit / export / share / delete 为同屏导航切换，保留旧直摘口径（见 §6.4 N3）。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/BottomBarSkinAssignActivity.kt`
  - 配色弹层：`paletteExit`；两处打开点过闸；`SheetExitHost` 收束清 `paletteFor`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/sections/WelcomeSection.kt`
  - 时间数字弹层：`timeExit`；配图 host：`imageExit`；打开点过闸；`SheetExitHost` 收束。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/sections/OtherSection.kt`
  - 数字弹层：`numberExit`；文本弹层：`textExit`；打开点过闸；`SheetExitHost` 收束。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt`
  - 字号数字弹层：`sizeExit`；打开点过闸；`SheetExitHost` 收束。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt`
  - 九个槽位控制器：`textExit` / `numberExit` / `contentExit` / `ignoreExit` / `modeExit` / `restoreExit` / `pwExit` / `lanExit` / `qrExit`；打开点过闸；`SheetExitHost` 收束。Qr 弹层用独立 `qrExit`，`LaunchedEffect(lanSheet) { qrExit.open() }` 进入时登记在场段。备份模式两个按钮、lan 菜单 onSend / onReceive 为导航 / 任务触发直摘，保留旧口径（见 §6.4 N3）。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/config/sections/ThemeSection.kt`
  - 四个槽位控制器：`numberExit` / `colorExit` / `nameExit` / `bgExit`；ColorPickerSheet / BackgroundImageSheet 两个私有 Sheet 随 Host 改造一并接收派生 visible；打开点过闸；`SheetExitHost` 收束。

### 2.3 未触碰（范围红线）

- `ui/components/HandleFileSheet.kt:48`（components 契约冻结，红线 1 条，本单零改动，E1.6 佐证）。
- reader 域（`ui/book/read/**`）、地图、名物库（help/artifact）、ui/card、help 其余包、autoTask / source / dict / replace 域外屏幕：均未改。
- `ui/config/**` 内非浮层宿主（`AutoBackupSheet`、`SettingList.kt:118` 派生 visible 调用点）判定不改，理由见 §6.4。

## 3. E1 grep 回证（命令 + 原样输出）

### 3.1 全仓命名参数 `visible = true` 残留（修复后）

命令：

```
grep -rn "visible\s*=\s*true" app/src/main --include=*.kt | wc -l
```

输出：`22`。

口径：B26 已修 1 条（HighlightNoteDialog）；本单 config 域命名参数 13 条清零；剩余 22 条全部在 config 域外——autoTask 4、book/source 12、dict 2、replace 3、components 红线 1，属其他分域单子范围，本单不动。

### 3.2 config 域命名参数残留（应为空）

命令：

```
grep -rn "visible\s*=\s*true" app/src/main/java/io/legado/app/ui/config/ --include=*.kt
```

输出：空，`exit=1`。

### 3.3 config 域位置参数 `Sheet(true, ...)` 残留（应为空）

命令：

```
grep -rnE "(Sheet|Host)\(true" app/src/main/java/io/legado/app/ui/config/ --include=*.kt
```

输出：空，`exit=1`。

另对多行位置参数形式兜底：`grep -rnE "^\s*true,\s*$" app/src/main --include=*.kt`，config 域零命中；全仓其余 `true,` 命中均为无关布尔实参（开关 / 高亮样式 / 调色板契约等），非 Sheet 首参。

### 3.4 阶段机复用符号落位

命令：

```
grep -n "import io.legado.app.ui.book.read.NoteDialogExitGate" \
  app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt \
  app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt
```

输出：

- `SheetExitController.kt:9:import io.legado.app.ui.book.read.NoteDialogExitGate`
- `ConfigActivity.kt:25:import io.legado.app.ui.book.read.NoteDialogExitGate`

阶段枚举复用链：`ReaderSheetExitPhase`（OPEN / EXITING / REMOVED）定义于 `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetExitGate.kt:14-17`；B26 `NoteDialogExitGate.kt:3` import 该枚举，:22/:27/:36/:45/:50/:54/:61 全程引用；本单 `SheetExitController` 只包装该守卫，未另造枚举、未另写迁移规则。

### 3.5 两段式接线落位（控制器 / 闸 / 收束宿主）

命令：

```
grep -nE "rememberSheetExitController|SheetExitController\(|canOpen|\.open\(\)|beginExit|SheetExitHost" \
  app/src/main/java/io/legado/app/ui/config/**/*.kt
```

关键落位（改动后现役行号，完整索引见 §4）：

- 控制器工厂声明：BackupSection.kt:70-78、OtherSection.kt:40-41、ThemeSection.kt:58-61、WelcomeSection.kt:36-37、CoverFontSection.kt:46、BottomBarSkinActivity.kt:181-182、BottomBarSkinAssignActivity.kt:124、ThemeListDialog.kt:59；Activity 字段 `ConfigActivity.kt:50`。
- 打开闸 `canOpen` + `open()`：各打开点行号见 §4。
- 收束宿主 `SheetExitHost`：各槽位行号见 §4。
- 内层 Sheet 透传：SettingSheets.kt:55 / :110 / :155 / :195 / :236；Host 透传：SettingSheetHost.kt:15 / :20。 〔W-451 回源有效〕

### 3.6 测试断言条数与测试名禁字符复核

命令：

```
grep -c "@Test" app/src/test/java/io/legado/app/ui/config/settings/SheetExitControllerTest.kt
grep -nE '^\s*fun `[^`]*[.:\[\]/;<>][^`]*`' app/src/test/java/io/legado/app/ui/config/settings/SheetExitControllerTest.kt
```

输出：`7`；第二条空，`exit=1`（7 个反引号测试名内不含 `. : [ ] / ; < >`，门禁 ㉘ 不会拦）。

### 3.7 红线目录零改动

命令：

```
git diff --stat -- app/src/main/java/io/legado/app/ui/components/ \
  app/src/main/java/io/legado/app/ui/book/ \
  app/src/main/java/io/legado/app/help/artifact/ \
  app/src/main/java/io/legado/app/ui/card/
```

输出：空，`exit=0`。`HandleFileSheet.kt:48` 原样保留 `InkHalfSheet(visible = true, ...)`（红线，契约冻结）。

### 3.8 本单改动文件清单

命令：`git status --short | grep -v docs/`

输出：

- ` M app/src/main/java/io/legado/app/ui/config/BottomBarSkinActivity.kt`
- ` M app/src/main/java/io/legado/app/ui/config/BottomBarSkinAssignActivity.kt`
- ` M app/src/main/java/io/legado/app/ui/config/ConfigActivity.kt`
- ` M app/src/main/java/io/legado/app/ui/config/ThemeListDialog.kt`
- ` M app/src/main/java/io/legado/app/ui/config/sections/BackupSection.kt`
- ` M app/src/main/java/io/legado/app/ui/config/sections/CoverFontSection.kt`
- ` M app/src/main/java/io/legado/app/ui/config/sections/OtherSection.kt`
- ` M app/src/main/java/io/legado/app/ui/config/sections/ThemeSection.kt`
- ` M app/src/main/java/io/legado/app/ui/config/sections/WelcomeSection.kt`
- ` M app/src/main/java/io/legado/app/ui/config/settings/SettingSheetHost.kt`
- ` M app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt`
- `?? app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt`
- `?? app/src/test/java/io/legado/app/ui/config/settings/`

### 3.9 括号配平粗检

命令：python3 剥离字符串 / 注释后逐文件计数 `{` / `}`。

输出：13 个改动文件（11 main + 1 控制器 + 1 测试）全部 `{` 数 == `}` 数，无配平失衡。

## 4. E2 文件:行 索引（改动后现役行号）

### 4.1 新增控制器与测试

- `app/src/main/java/io/legado/app/ui/config/settings/SheetExitController.kt`
  - :9 import `NoteDialogExitGate`；:32 控制器构造；:38 `visible`；:42 `isComposed`；:46 `canOpen`；:50 `isExiting`；:54 `open()`；:57 `beginExit()`；:63 `finishExit()`；:73 `rememberSheetExitController()`；:88 `SheetExitHost`；:93-99 离场段等效延时收束。
- `app/src/test/java/io/legado/app/ui/config/settings/SheetExitControllerTest.kt`
  - :27 / :34 / :42 / :50 / :59 / :67 / :77 共 7 个 `@Test`。

### 4.2 ConfigActivity.kt

- :25 import `NoteDialogExitGate`；:30 import `SheetExitHost`；:50 `iconExit` 字段；:90 `canOpen` 闸；:92 `open()`；:138 `SheetExitHost`；:140 派生 `iconExit.visible`；:146 `beginExit()`。

### 4.3 ThemeListDialog.kt

- :31-32 import；:59 `exit`；:97 `canOpen`；:99 `open()`；:108 `SheetExitHost`；:110 派生 `exit.visible`；:118 `beginExit()`。

### 4.4 BottomBarSkinActivity.kt

- :48-49 import；:181 `menuExit`；:182 `confirmExit`；:225 `menuExit.canOpen`；:227 `menuExit.open()`；:235 菜单 `SheetExitHost`；:236 派生 `menuExit.visible` + `beginExit()`；:245 `confirmExit.canOpen`；:247 `confirmExit.open()`；:256 确认 `SheetExitHost`；:258 派生 `confirmExit.visible`；:263 `beginExit()`。

### 4.5 BottomBarSkinAssignActivity.kt

- :47-48 import；:124 `paletteExit`；:186 / :190 两处 `canOpen` + `open()`；:210 `SheetExitHost`；:211 派生 `paletteExit.visible` + `beginExit()`；:219 / :242 选档后 `beginExit()`。

### 4.6 WelcomeSection.kt

- :17 / :20 import；:36 `timeExit`；:37 `imageExit`；:44 / :56 / :68 三处 `canOpen` + `open()`；:81 时间 `SheetExitHost`；:82 派生 `timeExit.visible`；:90 `beginExit()`；:96 配图 `SheetExitHost`；:97-100 派生 `imageExit.visible` + `beginExit()`；:105 / :109 选图 / 清除后 `beginExit()`。

### 4.7 OtherSection.kt

- :18 / :21 import；:40 `numberExit`；:41 `textExit`；:96 / :104 / :181 `textExit` 闸；:100 / :108 / :185 `textExit.open()`；:116 / :132 / :140 / :148 / :168 / :174 `numberExit` 闸；:120 / :136 / :144 / :152 / :170 / :176 `numberExit.open()`；:208 数字 `SheetExitHost`；:209 派生 `numberExit.visible`；:216 `beginExit()`；:221 文本 `SheetExitHost`；:222 派生 `textExit.visible`；:228 `beginExit()`。

### 4.8 CoverFontSection.kt

- :27 / :30 import；:46 `sizeExit`；:68 `canOpen`；:70 `open()`；:81 `SheetExitHost`；:82 派生 `sizeExit.visible`；:89 `beginExit()`。

### 4.9 BackupSection.kt

- :36 / :39 import；:70-78 九个控制器；:85 / :91 / :97 / :103 / :109 `textExit` 闸与 `open()`；:115 `numberExit` 闸；:119 `open()`；:132 `pwExit` 闸；:134 `open()`；:141 `contentExit` 闸；:143 `open()`；:147 `lanExit` 闸；:149 `open()`；:153 `modeExit` 闸；:155 `open()`；:161 `restoreExit` 闸；:163 `open()`；:169 `ignoreExit` 闸；:171 `open()`；:183 文本 `SheetExitHost`；:185 派生 `textExit.visible`；:188 `beginExit()`；:193 数字 `SheetExitHost`；:194 派生 `numberExit.visible`；:200 内容多选 `SheetExitHost`；:202 派生 `contentExit.visible`；:210 `beginExit()`；:217 忽略多选 `SheetExitHost`；:219 派生 `ignoreExit.visible`；:227 `beginExit()`；:237 备份模式 `SheetExitHost`；:238 派生 `modeExit.visible`；:261 恢复清单 `SheetExitHost`；:262 派生 `restoreExit.visible`；:266 `beginExit()`；:271 密码 `SheetExitHost`；:272 派生 `pwExit.visible`；:274 `beginExit()`；:279 菜单 `SheetExitHost(lanExit)`；:281 派生 `lanExit.visible`；:297 `beginExit()`；:301 `LaunchedEffect` 登记 `qrExit.open()`；:302 Qr `SheetExitHost`；:303 派生 `qrExit.visible`；:317 私有 `LanMenuSheet` 接收 `visible`。

### 4.10 ThemeSection.kt

- :33 / :36 import；:58 `numberExit`；:59 `colorExit`；:60 `nameExit`；:61 `bgExit`；:85 / :92 `numberExit` 闸与 `open()`；:115 / :118 / :121 / :124 / :139 / :142 / :145 / :148 `colorExit` 闸与 `open()`；:127 `bgExit` 闸与 `open()`；:131 / :155 `nameExit` 闸与 `open()`；:164 数字 `SheetExitHost`；:165 派生 `numberExit.visible`；:166 `beginExit()`；:171 颜色 `SheetExitHost`；:173 派生 `colorExit.visible`；:177 `beginExit()`；:183 命名 `SheetExitHost`；:184 派生 `nameExit.visible`；:189 `beginExit()`；:194 背景图 `SheetExitHost`；:196 派生 `bgExit.visible`；:199 `beginExit()`；:210 / :220 私有 `ColorPickerSheet` 接收并透传 `visible`；:239 / :244 私有 `BackgroundImageSheet` 接收并透传 `visible`。

### 4.11 settings 薄封装与内层 Sheet

- `SettingSheetHost.kt`：:15 `visible` 形参；:20 透传 `InkBottomSheet`。
- `SettingSheets.kt`：:41 / :55 NumberPickerSheet；:100 / :110 TextEditSheet；:146 / :155 ListPickSheet；:186 / :195 MultiChoiceSheet；:227 / :236 ConfirmSheet；:52-53 早退删除注释。

## 5. 六条判据逐条自检

判据 1（先复核：以 B26 清单为线索，自己 grep 出 config 域实际命中并逐条列文件:行；多出 / 少了写明）——**PASS（代码层，待 CI）**。

- 对照 HEAD 基线复核：B26 清单 13 条命名参数 `visible = true` 行号零漂移，全部在列（§6.1）。
- 实测多出位置参数形式 8 条（§6.2），B26 grep 只匹配命名参数写法而漏计；同形态缺陷，一并修复。
- 实测少了：B26 清单内无虚构条目；`SettingList.kt:118` 为派生 `sheetVisible`，非常量，不计缺陷（§6.4 N2）。

判据 2（逐条判定：浮层可见性常量改两段式；条件渲染宿主写明为何无需离场；判断带代码证据）——**PASS（代码层，待 CI）**。

- 13 条命名参数 + 8 条位置参数均为模态浮层（包 `InkBottomSheetHost` / 五个内层 Sheet），全部改两段式，证据行号见 §4。
- 非浮层 / 非常量宿主逐条读上下文后判定不改，理由与证据见 §6.4 N1 / N2；导航切换直摘按钮群见 N3。

判据 3（复用既有离场阶段机，禁造第二份枚举；给出 import 行号证明复用）——**PASS（代码层，待 CI）**。

- 复用 B26 `NoteDialogExitGate`：import 行 `SheetExitController.kt:9`、`ConfigActivity.kt:25`（E1.4）。
- 枚举 `ReaderSheetExitPhase` 经守卫间接复用，定义 `ReaderSheetExitGate.kt:14-17`；本单未新增任何阶段枚举，未复制 OPEN→EXITING→REMOVED 迁移规则。

判据 4（离场期间拒收重复打开）——**PASS（代码层，待 CI）**。

- 控制器 `canOpen` 转发守卫 `canOpen`（EXITING 段 false）；每个打开点先 `if (xxxExit.canOpen)` 再 `xxxExit.open()`，闸位行号见 §4 各文件。
- 离场段重复 `beginExit()` 为守卫空操作（测试 :50 例覆盖）；在场段重复触发的判重仍归宿主既有槽位状态，与 B26 口径一致，两道闸串联不替代。

判据 5（每条改动至少一条纯 JVM 断言；反引号测试名禁含 `. : [ ] / ; < >`）——**PASS（纯 JVM，待 CI 跑绿）**。

- 阶段逻辑下沉 `SheetExitController` / `NoteDialogExitGate`（不引用 Android / Compose 运行时；`mutableStateOf` 在普通 JVM 单测可实例化，先例 `ReaderSheetStateTest.kt:15`）。
- 7 例断言覆盖：初始态、在场态、关闭瞬间子树保留且 visible=false、离场段拒收、收束摘除、空操作兜底、收束后重开。
- 测试名禁字符复核：E1.6 第二条命令 `exit=1`，零命中。

判据 6（不改设置页既有交互与视觉，只补离场动画路径）——**PASS（静态层，待 CI）**。

- 仅改动可见性来源（常量 true → 控制器派生）与关闭收束时序；标题、选项、默认值、写库回调、按钮文案、遮罩 / 拖拽手势接线原样保留。
- 表单 / 列表状态由既有 `remember` / 槽位状态持有，离场 ~200ms 内子树保留，输入不丢；未新增任何视觉样式、尺寸、配色或动效参数。

## 6. 逐条判定

### 6.1 B26 清单 13 条（全部判定：改，两段式接线）

1. `ThemeListDialog.kt:102`（基线）——`ConfirmSheet` 删除确认弹层。证据：基线 `visible = true` 写死；现役 `ThemeListDialog.kt:108-118` 接 `exit` 控制器，派生 `exit.visible`，`SheetExitHost` 收束清 `deleteIndex`。判定：改。
2. `ConfigActivity.kt:129`（基线）——`ListPickSheet` 启动图标选择弹层。证据：基线 `visible = true`；现役 `ConfigActivity.kt:138-146` 接 Activity 字段 `iconExit`，派生 `iconExit.visible`。判定：改。
3. `BottomBarSkinActivity.kt:237`（基线）——`ConfirmSheet` 皮肤删除确认弹层。证据：基线 `visible = true`；现役 `BottomBarSkinActivity.kt:256-263` 接 `confirmExit`，派生 `confirmExit.visible`。判定：改。同屏菜单 host（基线 :225 经 Host 默认值恒真）接 `menuExit`，一并纳入两段式。
4. `WelcomeSection.kt:68`（基线）——`NumberPickerSheet` 欢迎页时间数字弹层。证据：基线 `visible = true`；现役 `WelcomeSection.kt:81-90` 接 `timeExit`，派生 `timeExit.visible`。判定：改。配图 host 接 `imageExit`（§6.3）。
5. `OtherSection.kt:174`（基线）——`NumberPickerSheet` 其他页数字弹层。证据：基线 `visible = true`；现役 `OtherSection.kt:208-216` 接 `numberExit`，派生 `numberExit.visible`。判定：改。
6. `OtherSection.kt:185`（基线）——`TextEditSheet` 其他页文本弹层。证据：基线 `visible = true`；现役 `OtherSection.kt:221-228` 接 `textExit`，派生 `textExit.visible`。判定：改。
7. `CoverFontSection.kt:75`（基线）——`NumberPickerSheet` 封面字号数字弹层。证据：基线 `visible = true`；现役 `CoverFontSection.kt:81-89` 接 `sizeExit`，派生 `sizeExit.visible`。判定：改。
8. `SettingSheetHost.kt:16`（基线）——薄封装 `InkBottomSheetHost` 内部 `InkBottomSheet(visible = true, ...)`。证据：基线函数无 `visible` 形参、内部写死 true；现役 `SettingSheetHost.kt:15` 新增 `visible` 形参并透传 :20。判定：改（Host 是其余私有 Sheet 间接受害的根因）。
9. `SettingSheets.kt:54`（基线）——`NumberPickerSheet` 内层 `InkBottomSheet`。证据：基线内层按 `if (!visible) return` 早退；现役 :41 收 `visible` 形参、:55 透传 `InkBottomSheet(visible = visible, ...)`，早退已删。判定：改。
10. `SettingSheets.kt:109`（基线）——`TextEditSheet` 内层 `InkBottomSheet`。现役 :100 / :110，同 9。判定：改。
11. `SettingSheets.kt:154`（基线）——`ListPickSheet` 内层 `InkBottomSheet`。现役 :146 / :155，同 9。判定：改。
12. `SettingSheets.kt:194`（基线）——`MultiChoiceSheet` 内层 `InkBottomSheet`。现役 :186 / :195，同 9。判定：改。
13. `SettingSheets.kt:235`（基线）——`ConfirmSheet` 内层 `InkBottomSheet`。现役 :227 / :236，同 9。判定：改。

### 6.2 实测差额：位置参数形式 8 条（B26 grep 漏计，同形态，一并改）

14. `BackupSection.kt:125`（基线）——`TextEditSheet(true, ...)` 文本配置弹层。现役 :185 派生 `textExit.visible`。判定：改。
15. `BackupSection.kt:132`（基线）——`NumberPickerSheet(true, ...)` 数字配置弹层。现役 :194 派生 `numberExit.visible`。判定：改。
16. `BackupSection.kt:138`（基线）——`MultiChoiceSheet(true, ...)` 备份内容多选弹层。现役 :201-202 派生 `contentExit.visible`。判定：改。
17. `BackupSection.kt:154`（基线）——`MultiChoiceSheet(true, ...)` 恢复忽略多选弹层。现役 :218-219 派生 `ignoreExit.visible`。判定：改。
18. `BackupSection.kt:193`（基线）——`ListPickSheet(true, ...)` 恢复文件清单弹层。现役 :262 派生 `restoreExit.visible`。判定：改。
19. `BackupSection.kt:201`（基线）——`TextEditSheet(true, ...)` 本地密码弹层。现役 :272 派生 `pwExit.visible`。判定：改。
20. `ThemeSection.kt:127`（基线）——`NumberPickerSheet(true, ...)` 主题数字弹层。现役 :165 派生 `numberExit.visible`。判定：改。
21. `ThemeSection.kt:141`（基线）——`TextEditSheet(true, ...)` 主题命名弹层。现役 :184 派生 `nameExit.visible`。判定：改。

### 6.3 经 Host 常量间接受害、随 §6.1 第 8 条一并接入派生 visible 的私有 Sheet

- `ThemeSection.kt:220` 私有 `ColorPickerSheet`：基线经 `InkBottomSheetHost` 默认恒真；现役 :210 收 `visible`、:220 透传，颜色槽位接 `colorExit`。判定：改。
- `ThemeSection.kt:244` 私有 `BackgroundImageSheet`：现役 :239 收 `visible`、:244 透传，背景图槽位接 `bgExit`。判定：改。
- `BackupSection.kt:317` 私有 `LanMenuSheet`：现役收 `visible`、:317 透传，局域网菜单槽位接 `lanExit`。判定：改。
- `BackupSection.kt:303` Qr 弹层 host：独立 `qrExit`，:303 派生 `qrExit.visible`。判定：改。
- `BottomBarSkinActivity.kt:236` 菜单 host：接 `menuExit`（见 §6.1 第 3 条备注）。判定：改。
- `BackupSection.kt:238` 备份模式 host：接 `modeExit`。判定：改。
- `BottomBarSkinAssignActivity.kt:211` 配色 host：接 `paletteExit`。判定：改。
- `WelcomeSection.kt:97` 配图 host：接 `imageExit`。判定：改。

### 6.4 判定不改 / 留档条目（逐条读过，非静默跳过）

N1. `BackupSection.kt:232-233`（定义 :326 `AutoBackupSheet`）——**不改**。证据：宿主 `if (autoBackupSheet)` 直挂 `AutoBackupSheet`；函数体首行即 `Column`（:330），未包 `InkBottomSheetHost` / `InkBottomSheet` / `InkHalfSheet`，无 `visible` 形参、无模态遮罩与离场动画组件。它是条件渲染的内联配置区块，不是浮层；组件层面不存在可播的离场动画，两段式无挂载点。若日后把它包进模态 host，需另派单补离场闸。

N2. `SettingList.kt:30` `ListPickSheet(visible = sheetVisible, ...)`——**不改**。证据：:113 `var sheetVisible by remember { mutableStateOf(false) }`，:115 打开置 true，:127 dismiss 置 false；传入的是派生状态而非常量 true。子树在 `SettingList` 内常驻组合，离场路径由派生可见性驱动，`InkBottomSheet` 内部保留帧条件（`progress.value == 0f && !visible`）负责收束，与 B26 已认可的派生 visible 口径一致，不属本单缺陷形态。

N3. 同屏导航 / 任务触发直摘按钮群——**保留旧直摘口径，不改**。证据与理由：
- `BottomBarSkinActivity.kt:240` edit、:241 export、:242 share、:243-244 delete：动作后果是关闭菜单后跳转编辑 / 导出 / 分享 / 下一级确认框，属同屏导航切换；旧直摘与导航切换同期发生，不存在「停留在原屏播离场」的交互诉求。菜单遮罩 / 拖拽关闭仍走 `menuExit` 两段式。
- `BackupSection.kt:242` / :249 备份模式两个按钮：触发备份任务后直摘，任务在 `scope.launch` 内异步执行；弹层不承担离场后保留内容的职责。遮罩 / 拖拽关闭走 `modeExit` 两段式。
- `BackupSection.kt:283` onSend / :290 onReceive：关闭局域网菜单后跳转 Qr 弹层 / 扫码页，属导航切换；菜单遮罩 / 拖拽关闭走 `lanExit` 两段式。
- 这些按钮的副作用时机与旧版一致，本单未改其导航语义；若验收要求导航切换也播下降动画，需另派单评估「先离场再跳转」的时序。

N4. `ui/components/HandleFileSheet.kt:48`——**不改（红线）**。components 契约冻结；`InkHalfSheet(visible = true, ...)` 原样保留，E1.7 佐证红线目录零改动。

## 7. 二阶影响（全仓消费点逐个说明）

### 7.1 弹层内部

- 写库 / 配置副作用时机不变：各 `onConfirm` / `onPick` 仍在决策时刻执行（写偏好、保存忽略配置、恢复 / 备份调用等），不延后到动画收束；与旧版时序一致。
- 表单 / 选项状态由既有 `remember` 或槽位状态持有，离场 ~200ms 内子树保留组合，输入内容与多选勾选不丢；收束摘子树后随宿主槽位清空，无残留状态。
- 五个内层 Sheet 删除早退后，初始未打开时子树仍可能被组合：其内部无可观察的重型初始化（选项列表来自参数 / 既有配置），`InkBottomSheet` 保留帧条件保证不可见时不渲染遮罩内容；性能影响与 B21 / B26 同类接线口径一致。

### 7.2 与既有闸的交互

- 宿主槽位状态（`*Target` / `*Multi` / `*Sheet` / `deleteIndex` 等）仍是在场段判重的唯一来源；控制器 `canOpen` 只拦离场段，两道闸串联不替代，口径同 B26。
- 离场段重复 dismiss 为守卫空操作，不会二次迁移阶段；离场段重复打开被 `canOpen` 拒收，不会出现「旧 Sheet 下降中叠起新弹窗」的双层竞态。
- Qr 弹层与局域网菜单各持独立控制器（`lanExit` / `qrExit`），菜单 → Qr 跳转不串阶段；Qr 离场收束后 `lanSheet` 置 `Closed`。

### 7.3 外部消费点排查

- 各 Sheet / Host 公共签名变化仅一处：`InkBottomSheetHost` 新增首参 `visible`；config 域全部调用点已在本单改为传派生 visible（E1.3 / §4.11 佐证）。config 域外无 `InkBottomSheetHost` 调用点（该薄封装定义于 config/settings 包）。
- 五个内层 Sheet 签名新增 / 已有的 `visible` 首参：config 域调用点全部更新；无域外消费方。
- 未新增跨域状态回传：控制器只反向引用 B26 守卫，不向 reader 半屏链路 / ViewModel 回传离场位。

### 7.4 二阶 / 三阶推演

- 二阶：离场段弹层子树仍在组合，继续截获宿主触摸；宿主列表滚动 / 点击在离场 ~200ms 内不与 Sheet 手势抢通道，与 B21 验收 §7.2 结论一致。
- 二阶：等效延时只引组件自有时长常数 `DURATION_MASK`（200ms）；`InkBottomSheet` 离场为单段 tween（对比 `InkHalfSheet` 的 MASK+RISE 两段），不另造魔法数。
- 三阶：日后组件若补 `onExitFinished` 完成回调，可把 `SheetExitHost` 内 `delay` 替换为回调触发，守卫阶段口径不变；延时常数跟随组件时长调整，不会漂移。
- 三阶：本单控制器为 config 域 Compose 层薄封装；若后续 dict / replace / autoTask / source 四域收口时出现第四个消费方，应评估把 B21 / B26 / config 三份包装合并为泛型纯 JVM 单一实现（届时再议，本单不提前抽象）。

## 8. 边界（锚点卡 §三，如实写明现状）

- 等效延时而非完成回调：`InkBottomSheet` 未暴露动画结束回调，沿用 B21 / B26 同一口径；延时依据 = 组件离场 tween 时长 `DURATION_MASK`=200ms。
- 离场段配置重建：控制器由 `remember` 持有，旋转 / 进程重建随 Composable 生命周期；离场中重建则阶段与离场位不跨重建存活（与 B20/B21/B26 同一边界，本单不解决）。
- 导航切换直摘按钮群未播离场动画（§6.4 N3）；这是有意保留的旧口径，不是遗漏。
- 域外 autoTask 4 / book·source 12 / dict 2 / replace 3 同形态项与 components 红线 1 条留档，按 B29 分域计划由其他单子收口；本单未改其代码。
- 范围外遗留沿用既有台账，本单不收口。

## 9. 未验证项

- 编译与单测：本地禁编译（无 gradle / JDK21 / SDK），未跑 `./gradlew` / `kotlinc`；`SheetExitControllerTest` 7 例是否全绿、新增 import 与签名变更是否通过编译，以云端 CI 为准，不假设既有 Verify 已绿。
- 真机交互：离场下降 / 遮罩回亮实际手感、200ms 延时与组件动画帧对齐、离场段触摸截获，未在真机走查；观感由既有 `InkBottomSheet` 组件保证，本单仅接线状态口径。
- 动画时序：摘子树时机为代码静态推断（延时依据 = `DURATION_MASK` 200ms），实际收束帧是否干净待 CI 出包后真机确认。
- 域外同形态项的实际影响面未逐条真机核验；本单判定基于源码条件渲染形态静态推断。

## 10. 交接备注

- 本单改动未 `git add / commit / push`；工作树改动待 CI 验收。docs 目录内既有 WIP（问题清单 / 台账 / B26/B27 文档）属其他单子产物，未并入本单口径。
- 后续若改 `InkBottomSheet` 离场时长常量，`SheetExitHost` 延时表达式自动跟随；若组件补完成回调，优先换回调、删等效延时分支。
- 其余四域收口时优先复用本单 `SheetExitController` 包装与 B26 守卫口径；出现第三个消费域前，评估合并守卫为泛型纯 JVM 单一实现。
