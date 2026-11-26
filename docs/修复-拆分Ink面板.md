# 锚点卡：docs/锚点/B31-清剿残留物.md

## 机器头

- 被改文件数：4（改写 1 件 `InkContentEditSheet.kt`；新增 3 件 `InkContentEditInputs.kt` / `InkContentEditEditor.kt` / `InkContentEditFooter.kt`）。为什么记 4：本单改动面仅限这四件，其余在飞件不碰。
- 新增断言数：0。为什么：本单是纯结构搬迁，不改判定口径，既有 `InkContentEditRulesTest.kt` 一条断言不动。
- 真跑门禁结论：`debt-ratchet.py` PASS（oversized_warn 51 → 50，回到基线）；`check-frankenstein.py` PASS；`kotlin-syntax-lint.py` 全量 PASS（本域 57 件）。
- 未验证项数：3（Kotlin 编译期解析 / 本地单测 / 真机 Compose 观感，均等 CI）。
- HEAD 区间：任务单记 `1e43cfd`；开工实测 `git rev-parse HEAD = 16bc12b`（并发推进），按开工守则以磁盘为准。为什么写明区间：避免后续按旧 HEAD 回查时对不上文件状态。

## 一、任务与基线

- 问题件：`app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditSheet.kt`，拆前 731 行，触发棘轮 `oversized_warn` 由基线 50 增到 51，`debt-ratchet.py` FAIL。
- 棘轮阈值（读 `data_scripts/check-frankenstein.py:43-44` 实测）：`LINE_WARN = 600`（≥600 行进候选计数），`LINE_HARD = 1200`。为什么以脚本为准：棘轮口径只认脚本报的阈值，不拍脑袋估。
- 本单唯一目标：行数降到阈值内，行为与观感零变化；只做拆文件 + 挪 import，不改逻辑、不改文案、不改令牌值、不改名。

## 二、在飞改动认脸（本单不碰的文件）

- `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt`：在飞已修改（M），本单只读其调用点 `:273`，未改一行。为什么认脸：开工守则要求先 `git status --porcelain` 分清谁的在飞件，避免覆盖他人改动。
- `docs/操作/00-入口.md`：在飞已修改（M），不碰。
- `InkContentEditRules.kt` / `InkContentEditState.kt` / `InkContentEditStore.kt` / `InkContentEditRulesTest.kt`：更早一轮在飞拆出的未跟踪件，本单只读引用，未改。为什么不动：状态层 / 规则层 / 数据桥已自洽，本单只收编剩下的面板 Composable。

## 三、拆前拆后行数

- 拆前：`InkContentEditSheet.kt` 731 行（≥600，计入 oversized_warn）。
- 拆后四件（`wc -l` 实测，全部低于 600 候选线，更远低于 1200 重点线）：
  - `InkContentEditSheet.kt` 300 行（骨架 + 浮层）。
  - `InkContentEditInputs.kt` 271 行（标题行 + 搜索栏 + 芯片 / 箭头小控件）。
  - `InkContentEditEditor.kt` 166 行（正文编辑区 + 位置进度条）。
  - `InkContentEditFooter.kt` 103 行（底部动作区 + 次级文字钮）。
- 行数总和由 731 升到 840：增量来自每文件独立的 package / import 头与件级职责 KDoc，不代表代码膨胀。为什么说明：防止验收把总行数增量误判为债务反弹。
- 同域既有在飞件行数（未改，仅登记）：Rules 126、State 229、Store 132。

## 四、切分理由（按内聚性逐件陈述）

- `InkContentEditSheet.kt` 留骨架：公开入口 `InkContentEditSheet` + `ContentEditGrabber`（金杠指示器）+ `EditMessageBar`（消息条）+ `DiscardConfirm`（脏数据离场确认浮层）。为什么聚在一件：这一簇都是 Dialog / 遮罩 / Sheet 外壳与浮层，持有弹入弹簧 `progress` 与共享滚动态 `editorScroll`，是面板的装配根，离了它其余子件无处挂载。
- `InkContentEditInputs.kt` 收输入与导航：`TitleRow`（标题行内改名）+ `SearchBar`（查询 / 正则 / 大小写 / 上下条 / 计数）+ `Chip`（开关芯片）+ `NavArrow`（上一条 / 下一条 / 关闭箭头）。为什么独立：标题录入与搜索录入同属「文本录入 + 命中导航」内聚簇，二者共用 `LocalContext`、`ComponentTokens` 文本样式与墨触手势口径，与正文编辑区无共享状态。
- `InkContentEditEditor.kt` 收正文编辑区与进度条：`EditorBody`（`TextFieldValue` 选区保留、命中段暖金 35% 底纹、纯文本可见串回填映射）+ `PositionBar`（滚动比例进度条）。为什么独立：二者共用同一个 `ScrollState`，进度条比例直接取自编辑器滚动值，拆开会把这条共享状态切断；与标题 / 搜索输入无状态耦合。
- `InkContentEditFooter.kt` 收底部动作区：`FooterActions`（次级动作一行 + 取消 / 保存主按钮一行）+ `FooterLink`（次级文字钮）。为什么独立：动作区只依赖状态只读字段与回调签名，不碰滚动态、不碰输入框，内聚于「结果动作出口」。

## 五、职责切片对账（对照任务单五簇）

- 面板骨架（状态与布局）：落在 `InkContentEditSheet.kt`。为什么归这里：布局装配与外壳动效只此一处，状态提升位置未动。
- 输入与高亮（`TextFieldValue`、命中段标注）：落在 `InkContentEditEditor.kt`（正文录入 + 高亮标注）与 `InkContentEditInputs.kt`（标题 / 搜索录入）。为什么分两件：正文与搜索各自的输入状态不互通，分件后改搜索栏不会牵动正文选区逻辑。
- 校验与脏数据判定：本单复核，Sheet 内无残留判定逻辑；校验三态 `validateTitle` / `validateContent`、脏数据 `isDirty`、映射 `projectEdit` 全部在既有在飞件 `InkContentEditRules.kt`，本件只调用不改写。为什么登记：任务单点名这一簇，需说明它已在更早一轮下沉，本单不重复搬。
- 离场裁决：裁决纯逻辑 `exitDecision` 与状态收口 `requestDismiss` 在 `InkContentEditState.kt` / `InkContentEditRules.kt`；本单只把确认浮层 UI `DiscardConfirm` 留在骨架件。为什么 UI 留骨架：浮层是覆盖在 Sheet 外壳之上的装配层，与骨架同生命周期。
- 文案与令牌常量：原文件无本地字符串常量块；文案全部走 `R.string.*`，颜色 / 尺寸令牌全部走 `ComponentTokens` / `HalfSheetParams` / `HalfSheetAnchors`，本单无可独立成件的常量。为什么说明：任务单点名这一簇，需回证不存在遗漏的本地常量表。

## 六、可见性改动清单

- `TitleRow`：`private` → `internal`（`InkContentEditInputs.kt:53`）。为什么：跨件被 `InkContentEditSheet.kt:150` 调用，Kotlin 文件私有跨件不可见，`internal` 是维持原模块内作用域的最小放宽。
- `SearchBar`：`private` → `internal`（`InkContentEditInputs.kt:145`）。为什么：跨件被 `InkContentEditSheet.kt:153` 调用，同上。
- `EditorBody`：`private` → `internal`（`InkContentEditEditor.kt:55`）。为什么：跨件被 `InkContentEditSheet.kt:184` 调用，同上。
- `PositionBar`：`private` → `internal`（`InkContentEditEditor.kt:121`）。为什么：跨件被 `InkContentEditSheet.kt:188` 调用，同上。
- `FooterActions`：`private` → `internal`（`InkContentEditFooter.kt:38`）。为什么：跨件被 `InkContentEditSheet.kt:190` 调用，同上。
- 保持 `private` 不变：`Chip`、`NavArrow`（仅 `SearchBar` 同文件调用）；`FooterLink`（仅 `FooterActions` 同文件调用）；`ContentEditGrabber`、`EditMessageBar`、`DiscardConfirm`（仅骨架件同文件调用）。为什么保持私有：调用方未跨件，收窄在文件内不形成新可见面。
- 公开签名 `fun InkContentEditSheet(...)` 八个参数原样保留，未改名、未改类型、未改顺序。为什么：宿主 `InkReadActivity.kt:273` 按命名实参调用，签名一动即断链。
- 无任何符号重命名；无 `public` 新增。

## 七、import 处置与补齐

- 四件按各自实际引用重新归集 import；扫描结果：四件均无未使用 import（`getValue` / `setValue` 由 `by remember { mutableStateOf(...) }` 委托隐式消费，脚本按字面名扫不到，属误报，已人工复核保留）。为什么逐条核对：未用 import 会被后续清理轮误删，悬空引用会在编译期红。
- 补齐一条漏列 import：`InkContentEditSheet.kt:16` 增加 `androidx.compose.foundation.layout.offset`。为什么补：骨架体原本就使用 `.offset { IntOffset(...) }`（原在飞文件 import 清单漏列），搬迁时一并补齐；纯 import 补齐，不涉及任何逻辑改动。
- 同包 top-level 函数无需 import：四件与宿主同在 `io.legado.app.ui.book.read.ink` 包内，跨件调用不写 import。为什么说明：回证时不把「缺 import」误判成断链。

## 八、三脚本实跑输出（逐条原文回贴）

- 棘轮 `python3 data_scripts/debt-ratchet.py`（拆前 FAIL，拆后 PASS）：
  - 拆前：`FAIL 债务增长：oversized_warn 由 50 增到 51` / `RATCHET: FAIL（债务只许减不许增…）`，退出码 1。
  - 拆后：`RATCHET: PASS（当前 {'dual': 8, 'compat': 2, 'oversized_warn': 50, 'deprecated': 1, 'wip': 2}，未超基线）`，退出码 0。为什么过线：`oversized_warn` 回到基线 50，其余四项与基线持平，无新增债务。
- 缝合怪守卫 `python3 data_scripts/check-frankenstein.py`：
  - `[1] 双轨并存: 8 命中（在册 8，未在册 0）`
  - `[3] 职责混装(提示,不单独FAIL): ≥600 行 50，≥1200 行 14`
  - `[4] @Deprecated 仍被调用: 1 命中（在册 1，未在册 0）`
  - `[5] 半成品 wip 分支: 2 分支（未在册 0）`
  - 末行：`FRANKENSTEIN: PASS`，退出码 0。为什么过线：本单未引入新双轨 / 兼容层 / 弃用调用 / wip 分支。
- 语法预检 `python3 data_scripts/kotlin-syntax-lint.py`：
  - 默认 git 改动模式：`KOTLIN-LINT: PASS（1 个文件）`，退出码 0。为什么只查 1 件：脚本默认只取 git 本轮改动 / 新增跟踪件，未跟踪新件不在其扫描面。
  - 本域显式全量：`python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/book/read/ink/*.kt app/src/test/java/io/legado/app/ui/book/read/ink/*.kt` → `KOTLIN-LINT: PASS（57 个文件）`，退出码 0。为什么补跑：确保三件新增件与既有在飞件括号配平、无条件尾逗号硬错。

## 九、grep 回证（命令与结论）

- 符号归属回证：对 `TitleRow / SearchBar / Chip / NavArrow / EditorBody / PositionBar / FooterActions / FooterLink / ContentEditGrabber / EditMessageBar / DiscardConfirm` 逐个 `grep -rn "fun <sym>\|<sym>("` 四件。结论：每个跨件被调符号定义在归属件、调用点只在骨架件；`Chip / NavArrow / FooterLink` 定义与调用同在归属件；骨架件内三件私有浮层定义与调用同文件。无一处定义悬空，无一处重复定义。
- 跨件同名冲突回证：`grep -rn "^internal fun \(TitleRow\|SearchBar\|EditorBody\|PositionBar\|FooterActions\)"` 全包扫描，五个 internal 符号各只出现一次定义。为什么查：同包 top-level 重名会编译冲突。
- 跨包引用回证：`grep -rn "book.read.ink.\(TitleRow\|SearchBar\|EditorBody\|PositionBar\|FooterActions\|Chip\|NavArrow\|FooterLink\)" app/src` 零命中。为什么查：internal 虽模块可见，但确认没有包外既存引用被拆分波及。
- 宿主调用回证：`InkReadActivity.kt:273` 仍以八个命名实参调用 `InkContentEditSheet(...)`，与公开签名逐参对应；调用点未改。
- 文案令牌回证：`R.string.retry / content_empty / title / edit / ok / cancel / content_search_invalid_regex / regex / content_search_match_case / search / copy_all / content_edit_plain_text / reset / action_save / exit_no_save / exit` 在四件中的命中数与拆前原文逐条一致（title 3、cancel 3、exit 系 2，其余各 1）。为什么回证：任务单禁改文案与令牌值，命中数是最直接的逐字对账。
- 关键字面量回证：`0.35f`（命中底纹透明度）、`delay(120)`（搜索防抖）、`"0/0"`、`"‹" / "›" / "✕"`、`32.dp`（箭头钮）、`20.dp`（指示器高 / 确认卡内边距）、`26.sp`（正文行高）、`24.sp`（确认文案行高）均在归属件原位。为什么回证：观感参数一旦漂移即偏离形态基线。
- 单测引用面回证：`InkContentEditRulesTest.kt` 仅引用 `InkContentEditRules` 与校验结果类型，不触碰任何被拆 Composable，拆分不断测试编译锁。

## 十、二阶 / 三阶推演

- 对其他模块：拆分只改文件归属，不改 public 签名；包外唯一调用方 `InkReadActivity` 不受影响。为什么无连锁：Composable 调用是按函数名解析，文件位置对调用方透明。
- 对 Compose 重组：状态提升位置原样保留——`editorScroll`、`progress` 仍在骨架入口件，子件只收状态与回调；重组范围、重组时机与拆前一致。为什么推演：若把 `rememberScrollState` 下移到子件，进度条与编辑器会脱节，本单未做这类挪动。
- 对可测性：纯逻辑仍在 Rules 层供 JVM 单测；UI 件拆分不新增也不减少可测面。为什么说明：验收金字塔口径不变。
- 对长期维护：后续改搜索栏只需翻 `Inputs.kt`，改正文录入只翻 `Editor.kt`，单件阅读长度均在 300 行内；代价是同包跨件调用走 `internal`，新增子件时需注意别把 internal 当 public API 扩散。
- 对棘轮：本单只减不增，未上调基线；后续这一簇再加长，应优先在四件内继续按职责细分，不允许回堆到骨架件。

## 十一、边界清单

- 本单为纯结构搬迁，原文件既有兜底分支逐字保留：加载态 `InkLoadingDots`、加载失败 `InkEmptyState` + 重试、空正文 `InkEmptyState`、正则非法提示、纯文本映射失败整段回写兜底，均未新增、未删除、未改判据。为什么列：开工守则要求每种边界有可读兜底，搬迁后需回证兜底未被切碎。
- 空数据 / 断网 / 进程重建等边界口径仍由 `InkContentEditState` 结果态驱动，本单不碰。

## 十二、改坏推演（改坏会红在哪）

- 若把五个 `internal` 退回 `private`：骨架件跨件调用 unresolved reference，CI 编译红。对应守卫：本报告第六节可见性清单逐条可回查。
- 若漏补 `foundation.layout.offset`：骨架 `.offset { }` unresolved reference，编译红。对应守卫：第七节 import 补齐记录。
- 若某件漏归集 `TextFieldValue` / `SpanStyle` / `SolidColor` 等 import：语法 lint 只查括号配平与条件尾逗号，抓不到 import 缺失，要到编译期才红；本单已逐件人工核对 import 与符号使用对应关系。
- 若误改 `0.35f` / `120ms` / 行高字号等字面量：观感或防抖口径偏离基线，静态脚本不一定红，需真机 / 截图验收；第九节字面量回证先兜一层。
- 若把 `Chip` / `NavArrow` / `FooterLink` 误升 `internal`：不致编译红，但可见面被不必要放宽，后续易被包内他件误用；清单已标注其保持 private 的理由。

## 十三、跨模块交接

- 未发现跨模块问题；本单改动面全部限定在 `ui/book/read/ink/**` 与本单三件新增文件。
- 交接提示给后续棒次：`InkReadActivity.kt` 是在飞修改件，其内容编辑回调接线（`saveContentEdit / resetContentEdit / renameContentEdit / copyAllContent / togglePlainText / reloadContentEdit / handleContentEditDismiss`）由该件负责，本单未核对其内部实现，若后续编译报回调缺符号，应回到该在飞件查，不归本单。

## 十四、未验证项

- Kotlin 编译期解析（import 解析、类型推断、Compose 注解处理）：本地禁编译链，未验证，等 CI。
- 单元测试：本地未跑 `InkContentEditRulesTest`，等 CI；本单未改规则层，预期不受影响。
- 真机 / 截图观感：Compose 重组、手势、动效上机表现未验证，等 CI 截图与真机验收。
- 未做：未 `git add / commit / push`；未改 `data_scripts/**`；未改 `docs/**`（本报告一份除外）；未编译。

> 回执缺失（规则上线前交付，留档）：本单交付时守则回执规范尚未生效，已在 `docs/台账-机制缺陷.md` 与 Q-199 补账批次挂号；内容本身不回填以免伪造，后续同类报告一律要求回执。
