# 实现报告 · Ink 承接件第 1 件——内容编辑面板

> 锚点卡：`docs/锚点/B31-清剿残留物.md`
> 背景依据：`docs/修复-B2-缝合怪二批.md` §2.2 / §4——10 个旧 XML 页面全部有活引用、无法删除，根因是「旧弹窗没有 Ink 承接目标」：`InkReadActivity` 菜单通道原为占位 `onMenu = { Unit }`，`ReaderSheetOverlay` 只分发 PERSON/YEAR/CHAR/PLACE 四种点字气泡。本单先造承接件，旧弹窗改道与删除留给下一单。
> 开工 HEAD：`7fc0b88`。
> 本单范围：新建 Compose 自绘「内容编辑」半屏面板（字段 / 校验 / 保存取消 / 脏数据离场确认，能力覆盖旧 `ContentEditDialog` 全部可编辑项）；接入 `InkReadActivity` 菜单通道；观感走既有 Ink 令牌与 spring 动效；边界兜底齐全；纯逻辑抽纯函数加「改坏会红」JVM 断言。

## 一、文件清单（绝对路径）

源码（`app/src/main/java/io/legado/app/ui/book/read/ink/`，全部为本单新增）：

1. `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditRules.kt`
   — 纯逻辑规则层：正文 / 标题脏数据判定、正文三态校验、标题三态校验、离场裁决、纯文本模式可见串 → 草稿原文映射。为什么单独成层：校验 / 脏数据 / 离场决策是可判定纯逻辑，下沉普通 JVM 单测可断言，Composable 只做状态搬运与绘制。
2. `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditStore.kt`
   — 数据桥：`InkEditTarget`（书 URL + 章序号 + 章内位置）；`EditLoadResult`（Ok / BookMissing / ChapterMissing / ContentMissing / Failed）；`EditSaveResult`（Ok / BookMissing / ChapterMissing / Failed）；`load` / `save` / `rename` / `reset` 四个 suspend 函数，IO 调度，`runCatching` 收敛异常不抛穿。为什么收口在本件：面板与状态层只收结果态，DAO / 磁盘 / 书源读写不泄漏到 UI 层。
3. `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditState.kt`
   — Compose 状态持有者：草稿 / 基线 / 标题 / 搜索态 / 纯文本 / 确认层 / 消息；`open` / `applyLoaded` / `applyLoadError` / `setSaving` / `showMessage` / `setMatches` / `setSearchError` / `setTitleEditing` / `requestDismiss` / `confirmDiscardClose` / `cancelDiscard` / `close` / `markContentSaved` / `markTitleSaved` / `saveToBundle` / `restoreFromBundle`。为什么不直接在 Composable 里堆状态：离场裁决与进程重建快照需要一处可独立推演的状态收口。
4. `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditSheet.kt`
   — 面板 Composable：`InkContentEditSheet(state, onSave, onReset, onRename, onCopyAll, onTogglePlainText, onReload, onDismiss)`；私有件 Grabber / TitleRow / SearchBar / Chip / NavArrow / EditorBody / PositionBar / FooterActions / FooterLink / EditMessageBar / DiscardConfirm。
5. `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt`
   — 最小必要接线修改（详见第五节）；未改任何旧页。

单测：

6. `/home/user/Doubao/chats/38454122521460994/legado/app/src/test/java/io/legado/app/ui/book/read/ink/InkContentEditRulesTest.kt`
   — 11 个 `@Test`，25 条 JUnit 断言（15 `assertTrue` / 3 `assertFalse` / 7 `assertEquals`）；普通 JVM，不触碰 Compose / Android 框架。

## 二、旧页能力对照清单（逐项覆盖 / 未覆盖）

对照旧 `ui/book/read/ContentEditDialog.kt` 与其 `res/menu/content_edit.xml`，逐项核对如下；旧实现只作功能对照，未复用其代码。

1. 章节正文多行编辑——已覆盖。`EditorBody` 的 `BasicTextField` 多行竖滑编辑；纯逻辑层不重复造，旧 `EditText` 实现未沿用。
2. 章节标题改名——已覆盖。旧版点 toolbar 弹 AlertDialog 输入；本单改为标题行点击进入行内编辑，规则层 `validateTitle` 三态校验，确认回调 `onRename` 落盘。形态不同、能力等价。
3. 正文搜索开关——已覆盖。底部次级动作「搜索」切换 `searchVisible`，与旧版 `menu_search` 同口径。
4. 搜索查询输入框——已覆盖。`SearchBar` 内单行 `BasicTextField` 绑定 `searchQuery`。
5. 正则开关——已覆盖。`Chip` 绑定 `regex`；扫描走仓内既有 `findContentMatches`，不重写正则。
6. 忽略大小写开关——已覆盖。`Chip` 绑定 `matchCase`。
7. 上一条 / 下一条——已覆盖。`NavArrow` 走仓内既有 `cycleContentMatchIndex` 循环切换命中下标。
8. 命中计数 n/m——已覆盖。`BasicText` 渲染 `${matchIndex + 1}/${matches.size}`，无命中显示 `0/0`。
9. 命中高亮——已覆盖（受限）。非纯文本模式下命中段叠暖金 35% 底纹（对照旧版 `BackgroundColorSpan` 35% 透明度）；纯文本模式可见串与原文偏移不一致，暂不叠高亮避免错位标色。
10. 非法正则错误提示——已覆盖。`findContentMatches` 抛异常时 `setSearchError(content_search_invalid_regex)`，消息条朱砂色呈现。
11. 保存落盘——已覆盖。`saveContentEdit` → `InkContentEditStore.save` → `BookHelp.saveText(book, chapter, content)`；命中当前章回调 `ReadBook.loadContent(index, resetPageOffset = false)` 刷新阅读页。
12. 重置（清缓存 + 拉源）——已覆盖。`resetContentEdit` → `store.reset`：`BookHelp.delContent` 删本地缓存；非本地书经 `WebBook.getContentAwait` 重新拉源；随后回读正文刷新草稿与基线。本地书仅清缓存，与旧版口径一致。
13. 复制全部（标题 + 正文）——已覆盖。`copyAllContent` 拼 `标题\n正文` 交 `sendToClip`；回执轻提示走 `copy_complete`。
14. 纯文本模式开关 + 偏好持久化——已覆盖。`togglePlainText` 切状态并 `putPrefBoolean("contentEditPlainText", ...)`，偏好键与旧版一致，老用户设置不丢；可见串经 `ContentEditProjection` 隐藏图片 / 书评标记，改动经 `projectEdit` 回填保留隐藏标记。
15. 位置进度条拖动滚动——已覆盖（精度未上机验证）。`PositionBar` 与编辑器共用同一 `ScrollState`，点按 / 拖动映射滚动比例；旧版按行高定位，本单按滚动像素比例映射，像素级对齐未真机量测。
16. 离场自动保存（旧版取消时若有改动自动落盘）——未沿用，属语义偏离。本单按任务书口径改为「脏数据离场确认」：脏数据离场先弹确认层（继续编辑 / 放弃改动），不再取消即自动保存。偏离原因：任务书明确要求脏数据离场确认，旧版自动保存语义与新承接件冲突；差异在此登记，供后续批次复核是否需要补回自动保存选项。
17. 脏数据判定——已覆盖。`contentDirty`（正文逐字比对，首尾空白改动也算脏）/ `titleDirty`（标题 trim 后比对）/ `isDirty`（任一变动即脏），纯函数可断言。
18. 进程重建恢复——部分覆盖。已恢复：草稿正文、基线、标题、搜索 query、搜索可见态、正则、大小写、纯文本开关、目标书章；未恢复：文本选区、滚动 Y 位置、搜索命中锚点。原因：`savedInstanceState` 只序列化纯值类型，选区 / 滚动 Y 属编辑器布局态，本轮未接；进程死亡连同 savedInstanceState 一并丢失时回退重新从磁盘加载。
19. 加载态指示——已覆盖。`loading` 态渲染 `InkLoadingDots`。
20. 空内容兜底——已覆盖。`content.isBlank()` 渲染 `InkEmptyState(content_empty)`，保存按钮禁用。
21. 超长内容兜底——已覆盖（新增防御性上限）。正文 `MAX_CONTENT_LENGTH = 200_000`、标题 `MAX_TITLE_LENGTH = 60`，超限校验拦截保存并给提示；旧版编辑器无显式上限，本单取宽松防御口径，超出给可读提示而非静默截断或崩溃。
22. 保存失败兜底——已覆盖。`EditSaveResult.Failed` / BookMissing / ChapterMissing 各给失败消息条，面板不关闭，正文保留可重试。
23. 加载失败兜底——已覆盖。`BookMissing` / `ChapterMissing` / `ContentMissing` / `Failed` 四类分别给可读文案，空态区带「重试」回调 `onReload`。
24. 搜索防抖 120ms——已覆盖。`SearchBar` 的 `LaunchedEffect` 内 `delay(120)` 后再跑正则扫描，与旧版 `scheduleSearch` 同口径。

## 三、面板结构

1. 遮罩层：`bgOverlay` 按 `DIM_ALPHA` 暗化，`MASK_BLUR_DP` 模糊，点击走 `rememberInkGesture` 墨触手势触发 `onDismiss`，不引 Material ripple。为什么不用 ripple：旧纹波是 Material 默认观感，与东方暖金纸面口径冲突。
2. Sheet 主体：底部对齐，`heightIn(max = 屏幕高 × MAX_HEIGHT_FRACTION)`，顶部 `sheetRadius` 圆角，绢帛 `sheetBackground` 纸白 + 暖金 1dp 描边。为什么限高：半屏面板不遮满屏，留出阅读页上下文。
3. 金杠指示器 `ContentEditGrabber`：Canvas 自绘 40dp 直边金杠 + 两端金点，尺寸取 `HalfSheetAnchors` / `HalfSheetParams` 既有锚点。
4. 消息条 `EditMessageBar`：成功暖金 / 失败朱砂 `textAccent` / 提示石青 `stoneBlue`，空消息不渲染。
5. 标题行 `TitleRow`：常态展示标题 + 「编辑」提示；点击入行内编辑，确认走规则层校验，空白 / 超长给本地朱砂提示不落库。
6. 搜索栏 `SearchBar`：查询框 + 正则芯片 + 大小写芯片 + 上一条 / 下一条 / 计数 / 关闭；非法正则走错误提示行。
7. 编辑区 `EditorBody`：竖滑滚动；加载态 / 失败态 / 空态 / 编辑器四分支；非纯文本模式命中段暖金 35% 底纹；本地持有 `TextFieldValue` 保留选区，外部状态变化时回填注解文本并保留当前选区。
8. 位置条 `PositionBar`：金线进度 + 茶褐底线，点按 / 拖拽回卷滚动。
9. 底部动作区 `FooterActions`：次级行（搜索 / 复制全部 / 仅显示正文 / 重置）横滑；主行取消（SECONDARY）/ 保存（PRIMARY，带 saving 加载态）。
10. 离场确认层 `DiscardConfirm`：茶玻压底 + 绢帛确认卡，「取消」留在面板、「退出」放弃改动收口会话。
11. 弹入动效：`Animatable` 0→1 走 `ComponentTokens.sheetSpring()` 弹簧，纵向上移 120px 进场；刚度 / 阻尼取组件库单源，不在本层另造物理数。

## 四、接线点（`InkReadActivity.kt`，`文件:行`）

1. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:72` — `contentEditState = InkContentEditState()`，面板状态持有者。
2. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:73` — `contentEditStore = InkContentEditStore()`，数据桥。
3. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:182` — `restoreFromBundle(savedInstanceState, STATE_EDIT_PANEL)`，进程重建恢复。
4. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:183-184` — 带草稿跳过二次磁盘加载；无草稿回 `reloadContentEdit()` 兜底。
5. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:266` — `onMenu = { openContentEdit() }`，原占位 `Unit` 已换成真实分发；grep 回证无 TODO。
6. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:273` — `InkContentEditSheet(...)` 面板组合点。
7. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:275` — `onSave = { saveContentEdit() }`。
8. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:276` — `onReset = { resetContentEdit() }`。
9. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:277` — `onRename = { renameContentEdit(it) }`。
10. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:278` — `onCopyAll = { copyAllContent() }`。
11. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:279` — `onTogglePlainText = { togglePlainText(it) }`。
12. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:280` — `onReload = { reloadContentEdit() }`。
13. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:281` — `onDismiss = { handleContentEditDismiss() }`。
14. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:319` — `openContentEdit()`：从 `ReadBook` 取当前书 / 章 / 标题构造 `InkEditTarget`，开面板并回读磁盘正文。
15. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:333` — `reloadContentEdit()`：加载结果五分支兜底。
16. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:352` — `saveContentEdit()`：保存结果三分支，成功刷基线并收口，失败留面板。
17. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:376` — `renameContentEdit()`：改名落盘，成功回填展示标题。
18. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:393` — `resetContentEdit()`：清缓存拉源回读。
19. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:407` — `copyAllContent()`：标题 + 正文进剪贴板。
20. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:413` — `togglePlainText(plain)`：状态 + 偏好持久化。
21. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:419` — `handleContentEditDismiss()`：离场请求交状态层裁决。
22. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:425` — `saveToBundle(outState, STATE_EDIT_PANEL)`：进程重建快照。
23. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:458` — `PREF_PLAIN_TEXT = "contentEditPlainText"`，偏好键与旧版一致。
24. `app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:461` — `STATE_EDIT_PANEL = "inkContentEdit"`，快照前缀隔离宿主其它状态键。

## 五、令牌复用情况（未新造一套设计令牌）

颜色 / 圆角 / 缓动全部引既有组件库，未在本单新造色值或物理参数：

1. `ComponentTokens` 复用：`bgOverlay`、`bgPage`、`bgSurface`、`brandGold`、`brandCopper`、`stoneBlue`、`textAccent`、`textPrimary`、`textSecondary`、`textTertiary`、`colorDivider`、`sheetRadius`、`buttonRadius`、`cardRadius`、`disabledAlpha`、`sheetSpring()`。为什么复用：同一套暖金 / 绢帛 / 石青口径保证新面板与既有 Ink 半屏观感一致。
2. `HalfSheetParams` 复用：`sheetBackground`、`grabberColor`、`DIM_ALPHA`、`GRABBER_BAR_HEIGHT_DP`。
3. `HalfSheetAnchors` 复用：`MAX_HEIGHT_FRACTION`、`MASK_BLUR_DP`、`GRABBER_BAR_WIDTH_DP`、`GRABBER_DOT_RADIUS_DP`。
4. 既有组件复用：`InkButton` / `InkButtonVariant`（PRIMARY / SECONDARY / GHOST）、`InkEmptyState`（message / compact / actionLabel / onAction）、`InkLoadingDots`、`rememberInkGesture` 墨触手势。
5. 既有逻辑复用：`findContentMatches` / `cycleContentMatchIndex`（`ui/book/read/ContentSearch.kt`）、`ContentEditProjection`（图片 / 书评标记隐藏与回填）。只复用规则与解析逻辑，未复用旧页 UI 实现。
6. 字符串资源复用：`search`、`action_save`、`reset`、`copy_all`、`edit`、`cancel`、`ok`、`exit`、`retry`、`regex`、`content_search_match_case`、`content_search_invalid_regex`、`content_edit_plain_text`、`content_empty`、`book_not_exist`、`unknown_error`、`success`、`copy_complete`、`exit_no_save`、`title`；均已 grep 存在于 `values/` 与 `values-zh/`。
7. 外观红线：新文件无 `MaterialTheme` 取色、无 Material `colorScheme`、无 ripple / elevation；grep 命中的 `MaterialTheme` / `ripple` 字样仅出现在注释说明中。为什么守这条：任务书要求不出现 Material 默认控件外观。

## 六、边界兜底清单

1. 空正文：编辑区走 `InkEmptyState(content_empty)`，保存按钮因 `ContentValidation.Empty` 禁用；不崩、不留白。
2. 超长正文 / 标题：规则层 `TooLong` 拦截保存，消息条给提示；不静默截断、不写盘。
3. 加载失败：书缺失 / 章缺失 / 内容缺失 / 异常四类结果态各配可读文案，空态区带重试按钮；重试回 `store.load` 重新走完整链路。
4. 保存失败：`Failed` / 书缺失 / 章缺失消息条呈现，面板不关闭，草稿保留可改后重试。
5. 进程重建：`saveToBundle` 落草稿 / 基线 / 标题 / 搜索态 / 纯文本；`restoreFromBundle` 恢复，带草稿不二次覆盖；无草稿回磁盘重载；连 savedInstanceState 一并丢失时回退重新加载，空内容由空态兜底。
6. 纯文本映射对齐失败：`projectEdit` 前后缀对不齐返回 null，调用方按可见串整段回写；隐藏图片标记可能丢失，登记为未验证项，不崩。
7. 非法正则：扫描异常收敛为搜索错误态，清空命中，不抛穿到编辑器。
8. 保存进行中离场：`exitDecision` 判 `KEEP_OPEN` 按住面板，避免半路离场丢掉写盘结果。
9. 搜索空查询 / 非可见态：命中列表清空、计数 `0/0`、错误态收口。
10. 重置拉源失败：`delContent` 后拉源 / 回读任一环节异常收敛为 `ContentMissing` / `Failed`，面板走失败提示，不崩。

## 七、断言与改坏推演

单测 `InkContentEditRulesTest.kt`：11 例 25 断言，全部为普通 JVM 可判定逻辑；Compose 观感不上机、不假称验证。

1. 正文脏数据：`contentDirty("原文", "原文 ")` 判脏、`contentDirty("原文", "原文")` 判净。改坏推演：若比对改成 `trim` 后比较，首条断言转红——首尾空白改动会被吞掉。
2. 标题脏数据：`titleDirty("旧标题", "新标题")` 判脏、`titleDirty("旧标题", " 旧标题 ")` 判净。改坏推演：若改成逐字比对不 trim，第二条断言转红——标题首尾空白被误判为改动。
3. 整体脏数据：正文变 / 标题变 / 两者均净三分支各一断言。改坏推演：任一 `||` 误写成 `&&`，对应单变动断言转红。
4. 正文校验三态：合法 / 空白 / 超长（含实际长度与上限 `MAX_CONTENT_LENGTH` 比对）各一断言；另例恰好上限长度内合法。改坏推演：上限边界用 `>=` 而非 `>`，恰好上限用例转红；空态判空逻辑改成判 `isEmpty` 不判 `isBlank`，纯空白用例转红。
5. 标题校验三态：合法 / 空白 / 超长（含上限比对）。改坏推演：trim 归一化漏掉，纯空白标题被误判合法。
6. 离场裁决：保存中无论脏净一律 `KEEP_OPEN`；脏数据 `CONFIRM_DISCARD`、干净 `DISMISS`。改坏推演：裁决顺序把 dirty 判断提到 saving 之前，保存中脏数据用例会转红。
7. 投影映射三例：可见串未变原样返回草稿；中段插入保留隐藏图片标记；整段替换仍保留隐藏标记。改坏推演：映射改成整段重写原文，后两例的图片标记断言转红；前后缀对齐边界条件改坏，中段插入例转红。

门禁回证：函数名反引号内已无 `/`（`grep -n "fun \`" InkContentEditRulesTest.kt` 11 条全部为空格分隔）；静态预检 `python3 data_scripts/kotlin-syntax-lint.py` 对 6 个文件 PASS（括号配平 + if 条件尾逗号；非编译）。

## 八、未验证项（如实登记，不假称上机）

1. Compose 观感、弹簧动效、IME 弹出 / 收起、键盘遮挡、窄屏排版均未真机验证；本单硬约束禁编译、禁出包，单测只覆盖纯逻辑层。
2. 纯文本模式下光标偏移、选区随投影回填、`displayOffset` / `rawOffset` 对齐未上机验证；映射失败兜底整段回写可能丢失隐藏图片标记。
3. 搜索上一条 / 下一条只做循环切换下标与命中高亮，未做按字符偏移的像素级滚动定位（旧版 `getLineTop` 滚到命中行）；当前命中行可能留在可视区外。
4. 进程重建未恢复文本选区、滚动 Y 位置、搜索命中锚点；旧版恢复的 `restoredScrollY` / 选区本轮未接。
5. 搜索初始锚点固定从第 0 个命中起（旧版按光标位置锚定）；`indexOfFirst { it.first >= 0 }` 退化为首命中。
6. 标题超长错误提示复用标题占位文案，未单独造超长提示字符串。
7. 搜索芯片长文案在窄屏可能溢出；次级动作行已横滑兜底，芯片折行表现未量测。
8. 位置进度条按滚动像素比例映射，与旧版按行高定位的精度差异未真机比对。
9. 保存成功后面板立即收口，成功消息条随面板关闭不可见；回执与旧版一致走落盘收口，未另补全局 toast。
10. 重置对非本地书拉源只传章节、未传下一章节 URL；沿用仓内 `getContentAwait` 默认参数口径，跨章衔接场景未上机验证。
11. 底部「仅显示正文」脚注未回显选中态（旧版菜单项 `isChecked` 打勾）；当前只以开关行为生效，视觉上看不出当前是否处于纯文本模式。

## 九、硬约束遵守情况

1. 只新增 `ui/book/read/ink/**` 下 5 个新文件 + `InkReadActivity` 最小接线；旧 `ContentEditDialog.kt` / `ContentEditProjection.kt` / `ContentSearch.kt` / `res/menu/content_edit.xml` git status 为空，未触碰。
2. 未动 `data_scripts/**`、未动 `help/artifact/**`。
3. 禁编译：本单未执行 Gradle 编译 / 打包；静态预检仅跑仓内既有括号配平脚本。
4. 禁 `git add/commit/push`：本单未执行任何 git 写操作。
5. 菜单通道 grep 回证：`InkReadActivity.kt:266` 为 `onMenu = { openContentEdit() }`，`:273` 面板 Composable 真实组合，无 TODO 占位。
6. 他人在飞文件（`docs/工具/00-开场.md`、未跟踪台账）未触碰。
