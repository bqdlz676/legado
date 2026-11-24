# 台账 · 观感 UX 双维验收（Q-20 / 工序四补节九维）

> 来源：用户原话「美感验收那一轮还要加入优化用户体验的规则」（`docs/派单队列.md` Q-20 L30）。
> 配套工序：`docs/工序-观感打磨.md` 四补节 UX-01~UX-09；机械脚本：`data_scripts/check-visual-baseline.py --ux`。
> 本台账是 UX 违例的唯一事实源：每条违例逐条闭环，关闭条件见各维；禁口头销项、禁"感觉没问题"关闭。

## 状态语义（三档，不许混写）

- **FAIL（红线候选）**：静态可判且命中红线阈值，脚本退出码 1；须改到达标或经验收代理书面豁免（豁免理由 + `文件:行` 回源）后关闭。
- **WARN（静态线索，需人眼复核）**：关键词/配对缺失，静态证明不了也证伪不了；必须由验收代理人眼/帧/集成走查后回填结论，不许直接当 FAIL 也不许直接销项。
- **DEFER（需帧/真机）**：截图帧与静态代码判不了的项；登记替代口径与降级证据，真机/帧基建就绪前保持"待验"，禁止写成 PASS。

## 审计入口与当前快照（2026-11-25 首轮静态扫描）

- 命令：`python3 data_scripts/check-visual-baseline.py --ux`（默认扫 `app/src/main`；`--ux <目录>` 可换源码根）。
- 首轮结果：**FAIL 262 项**（UX-01 219 / UX-03 43）；**WARN 159 项**（UX-04 18 / UX-05 25 / UX-06 78 / UX-07 38）；**DEFER 10 条**（跨 UX-01/02/03/04/05/06/07/08/09）。
- 扫描范围说明：本轮代码库处于改版中期，存量 Legado 旧实现与新卡片/阅读壳并存；FAIL/WARN 为静态初筛候选，是否真违例由对应模块重做链路（F-73 三段流水线）与独立验收代理逐条核定。脚本未接入 `data_scripts/rule-gate.sh`（本任务红线不改该文件），不构成提交硬门禁，仅作 P5/P6 自检输入。

---

## UX-01 可读性（字号 / 行距 / 对比度）

- 判据：正文最小字号 ≥14sp（静态正则兼容 `13.sp`、`13.sp13()`、`sp12()` 三种写法；`letterSpacing`/`lineHeight` 不计入字号）；行距 ≥1.4 倍字号；正文对比度 ≥4.5:1（WCAG）。
- 谁来验：脚本机械初筛字号；行距/对比度由验收代理对渲染帧复核。何时验：P5 模块出口 + P6 整体自检。
- 当前状态：静态字号 FAIL 219 项；行距与对比度 DEFER（需帧）。
- 违例条目（样本，全量以 `--ux` 输出为准）：
  - `app/src/main/java/io/legado/app/ui/about/AboutActivity.kt:204` 字号 13sp <14sp；`:208` 同；`:248` 同。
  - `app/src/main/java/io/legado/app/ui/about/AboutInkActivity.kt:94` 字号 13sp <14sp。
  - `app/src/main/java/io/legado/app/ui/about/AppLogDialog.kt:136` 字号 12sp <14sp；`:150` 同。
  - 字号重灾区：`ui/map/crossing/shell/CrossingShell.kt`（21 处）、`ui/map/globe/host/GlobeScreenHost.kt`（12 处）、`ui/main/bookshelf/BookshelfScreen.kt`（10 处）、`ui/main/explore/InkExploreCards.kt`（9 处）、`ui/book/read/ink/InkBubble.kt`（7 处）。
  - 分布：8sp×5、9sp×4、10sp×30、10.5sp×2、11sp×25、12sp×71、13sp×82。
- 关闭条件：相关模块重做后 `--ux` 该维字号 FAIL 清零；行距 ≥1.4 倍字号与对比度 ≥4.5:1 有帧像素证据（取色值 + 对比度计算过程）；验收代理复核签字。

## UX-02 暖金辉光 / 暗色下正文对比度保底

- 判据：暗色/纯黑主题下，正文前景对背景（含辉光叠加层）对比度仍 ≥4.5:1；辉光透明度不得把正文对比压到 3:1 以下。
- 谁来验：验收代理对暗色帧像素取色；脚本仅查暗色主题色是否走 token、不硬编码近黑文本。何时验：P1 暗色帧判定 + P6 复验。
- 当前状态：DEFER（需暗色渲染帧）；本轮无帧可判，未冒充通过。
- 违例条目：暂无帧证据；待 P0 截图基建产出暗色帧后回填。
- 关闭条件：暗色帧正文/背景取色对比度 ≥4.5:1 留档（色值 + 计算式）；辉光叠加层下正文对比 ≥3:1；无暗色帧前条目保持"待验"。

## UX-03 点击目标尺寸与间距

- 判据：可点元素热区 ≥48dp×48dp（`size`/`minHeight`/`minWidth` 声明，裸 `width`/`height` 多为分隔条/描边不计）；列表/卡片间距 ≥8dp（`spacedBy`）。
- 谁来验：脚本静态初筛；热区是否真为可点归属由验收代理对帧/代码复核。何时验：P5 模块出口 + P6 整体自检。
- 当前状态：静态 FAIL 43 项（间距 + 热区混合）。
- 违例条目（样本）：
  - 间距：`ui/about/AboutActivity.kt:193` spacedBy 6dp；`ui/about/AboutInkActivity.kt:68` spacedBy 4dp；`ui/about/AppLogDialog.kt:132` spacedBy 2dp；`ui/book/read/ink/InkBubble.kt:172` spacedBy 6dp；`ui/book/source/manage/SourceManageRow.kt:83` spacedBy 4dp、`:247` spacedBy 3dp。
  - 热区：`ui/components/InkButton.kt:66` 8dp；`ui/components/inkslate/InkSlateToolbar.kt:138` 24dp、`:192` 44dp、`:66` 44dp；`ui/config/BottomBarSkinActivity.kt:271` 24dp、`:274` 24dp；`ui/main/search/SearchInputBar.kt:178` 28dp。
- 关闭条件：可点元素热区静态 ≥48dp 且帧上归属确认；目标间间距 ≥8dp；内部排版间距（非目标间）若豁免，须在条目里写明豁免理由与对应视觉依据。

## UX-04 点击到视觉反馈 ≤100ms

- 判据：目标口径＝手势落点到反馈可见（按压态/辉光/音效起）≤100ms。静态线索：交互路径 `delay`/`delayMillis` >100ms 列入 WARN 复核（轮询/限流/Toast 等待豁免）；时延本体截图帧测不到时间，走帧序列序号差推算或真机 bench。
- 谁来验：脚本静态线索 + 验收代理；真机样本后置。何时验：P5 出口静态项；P6 帧序列；真机项交付前复核。
- 当前状态：WARN 18 项；反馈时延本体 DEFER（需帧序列/真机）。
- 违例条目（样本）：`ui/about/AboutActivity.kt:82` delay 3000ms（Toast 等待嫌疑，待复核）；`:97` 同；`ui/book/changecover/ChangeCoverDialog.kt:76` delay 1000ms；`ui/book/changesource/ChangeBookSourceDialog.kt:280` delay 1000ms、`:299` delay 500ms。
- 关闭条件：反馈链路无 >100ms 前置等待（静态复核排除轮询/Toast）；帧序列或真机样本给出反馈时延 ≤100ms 证据；无真机时台账必须写明"替代口径 + 降级证据"，禁止静默放过。

## UX-05 加载与空态

- 判据：凡 `collectAsState`/`LaunchedEffect`/`produceState` 等异步入口，代码必须出现骨架屏/占位/loading/空态文案之一；三变体 loading/empty/offline 齐全。
- 谁来验：脚本静态配对 + 验收代理人眼复核。何时验：P5 模块出口 + P6 整体自检。
- 当前状态：WARN 25 项（UI 层异步入口未见占位关键词）。
- 违例条目（样本）：`ui/autoTask/AutoTaskActivity.kt`；`ui/autoTask/AutoTaskEditActivity.kt`；`ui/book/read/ink/InkReadActivity.kt`；`ui/book/read/ink/InkReaderBook.kt`；`ui/book/read/ink/InkReaderShell.kt`。
- 关闭条件：每个异步入口有对应骨架/占位/空态实现，帧上可见 loading/empty/offline 三变体；静态关键词配对通过且人眼复核无空白态。

## UX-06 错误恢复

- 判据：错误/离线/空数据分支必须有重试动作或兜底文案（retry/重试/兜底/离线/offline/empty 之一）；崩溃回归以单测/异常捕获证据为准。
- 谁来验：脚本静态配对 + 验收代理全链路走查 + 单测。何时验：P5 模块出口 + P6 断网与空数据全链路。
- 当前状态：WARN 78 项（UI 层错误分支未见重试/兜底关键词）。
- 违例条目（样本）：`ui/about/CheckAppUpdate.kt`；`ui/about/ReadRecordEditDialog.kt`；`ui/association/AddToBookshelfDialog.kt`；`ui/association/BaseAssociationViewModel.kt`；`ui/association/FileAssociationActivity.kt`。
- 关闭条件：断网/缺数据路径走查有重试或兜底文案、无异常逃逸；对应单测/异常捕获证据留档；静态配对通过。

## UX-07 手势可发现性

- 判据：可翻页/可缩放区域首次可见提示（角标/轻扫动画/文案 hint/引导浮层）；静态查提示关键词或引导组件，首访可见性转人眼/帧复核。
- 谁来验：脚本静态 + 验收代理人眼/帧。何时验：P1 帧判定 + P6 整体自检。
- 当前状态：WARN 38 项（UI 层手势区未见首访提示关键词）。
- 违例条目（样本）：`ui/book/manga/recyclerview/WebtoonFrame.kt`；`ui/book/manga/recyclerview/WebtoonRecyclerView.kt`；`ui/book/read/ReadMenu.kt`；`ui/book/read/ink/PageTurnGate.kt`；`ui/book/read/ink/PeelSpring.kt`。
- 关闭条件：翻页/缩放入口首帧可见引导提示，帧证据留档；提示不被后续操作顶掉；静态引导组件在位。

## UX-08 返回栈一致性

- 判据：返回后恢复阅读进度与地图视角（相机位置/缩放/选中状态）；保存-读取成对存在；真生效转集成走查/真机复核。
- 谁来验：脚本静态配对（`rememberSaveable`/`onSaveInstanceState`/进度与相机状态保存读取）+ 验收代理集成走查。何时验：P5 模块出口 + P6 整体自检。
- 当前状态：DEFER（集成/真机走查）；本轮未建静态配对规则，列入下一轮脚本补强项。
- 违例条目：待阅读页/地图壳集成走查后回填。
- 关闭条件：阅读进度、地图视角、气泡/筛选上下文跨返回不丢失；集成走查记录（操作步骤 + 结果）留档；保存与读取代码成对可回源。

## UX-09 长文性能手感

- 判据：目标口径＝连续翻页 ≥50 帧无掉帧（帧率 ≥50fps，单帧 ≤16.7ms）。替代口径：滚动/列表 item lambda 内禁新建 Bitmap 或大对象（呼应 F-50）；帧序列测帧间隔方差；真机 bench 后置。
- 谁来验：脚本静态 + 帧序列分析；真机 bench 后置。何时验：P6 整体自检；真机 bench 交付前复核。
- 当前状态：DEFER（需真机 bench / 帧序列）；静态 Bitmap 分配检查列入下一轮脚本补强项。
- 违例条目：待长文翻页 bench / 帧序列产出后回填。
- 关闭条件：真机或帧序列给出连续 ≥50 帧无掉帧证据；滚动路径无新建 Bitmap/大对象；替代口径结论不得冒充真机帧率。

---

## 跨维遗留项（本轮未闭环，转交后续轮次）

1. UX-08 静态配对规则未落地：下一轮在脚本补"进度/相机状态保存-读取成对"扫描。
2. UX-09 静态 Bitmap/大对象分配检查未落地：下一轮补 item lambda 内分配扫描。
3. P0 截图基建未就绪前，UX-01 行距/对比度、UX-02 暗色保底、UX-07 首访可见性全部 DEFER；帧基建产出后回填帧证据。
4. UX-04 时延与 UX-09 帧率的真机 bench 口径待真机/bench 环境；期间只认替代口径 + 降级证据。
5. 存量 262 项 FAIL 属改版中期旧实现候选，随各模块 F-73 重做链路逐条核定；核定为豁免的须在本台账条目写明理由与 `文件:行` 回源。
