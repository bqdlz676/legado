锚点卡：docs/锚点/B4-03-Sheet闸族状态簇.md

# 修复-B4-03 Sheet 离场闸族与壳状态簇归位

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「禁止先删后接、禁止留旧格式兼容层兜底」（docs/机制-总控必读.md:826）
- 适用条款：B1 单文件单职责；B2 改动后 grep 回证；C3 边界兜底不空白不崩；R1 四包零改动；R5 不增第二份开合记录；R14 回位铁律；§六二阶风险 5 闸族基类口径唯一；E8 沙箱禁编译。

结论：B4-03 闸族五件与回位接缝已落地；阶段判定收口到唯一基类，子类只登记负载；回位三场景与九条边界各有纯逻辑用例或静态兜底；四包双通道空集。编译与单测绿红以 CI 为准，本单未在沙箱编译。

## 一、单判据（六字段，逐字回源）

- 白名单目录：仅新建 / 改动 `app/src/main/java/io/legado/app/ui/book/read/ink/` 下五件——`InkShellState.kt`、`ReaderSheetState.kt`、`ReaderSheetGuard.kt`、`ReaderSheetExitGate.kt`、`SheetExitPhaseGate.kt`；单测落 `app/src/test/java/io/legado/app/ui/book/read/**`。
- 判据锚：docs/拆分-B4阅读页小单.md:45 B4-03 条目；docs/任务书-B4-阅读页-引擎定界.md:384 I-18、:388 I-19、:392 I-20、:396 I-21、:400 I-22；docs/任务书-B4-阅读页.md:48 R14；docs/任务书-B4-阅读页-引擎定界.md:518 §六二阶风险 5。
- 产物：上述五件 `.kt` + 两份纯 JVM 单测 + 本报告 + 锚点卡。
- 依赖：只引 `ReaderSnapshot` / `isRestoredExactly`（ui/components 只读契约）、`ReaderDetailRequest`（page/api 只读契约）、compose runtime 可观察态薄包装；不引 navigation-compose，不改 `build.gradle`，不新增依赖。
- 并行度：与 B4-02 翻页纯逻辑七件并行；本单不碰 `InkPageCommand` / `PageTurnGate` / `PeelSpring` / `ViewGestureBridge` / `ZoneClassifier` / `GestureRouter` / `CandidateCycle`。
- 放行条件：四包 `page/provider/**`、`page/entities/**`、`page/api/**`、`page/delegate/**` 零 diff（双通道回证空集）；旧阅读页实现（`ReadBookActivity`、旧 XML/Binding 侧）一律不动，新建件内交接注记「旧实现留待 B4-31 下线」。

## 二、逐文件改动面

- `app/src/main/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGate.kt:59`：唯一阶段判定基类。三态枚举 `ReaderSheetExitPhase`（OPEN / EXITING / REMOVED）、迁移规则、`visible` / `isComposed` / `canOpen` 派生口径全部在本类；新增拒收可观测计数 `rejectedTransitionCount`（:66）、`lastRejectedPhase`（:71）；`tryOpen`（:92）、`beginExit`（:107）、`finishExit`（:122）返回布尔表示迁移是否成立；`resetToRemoved`（:136）为宿主销毁强制收口；`safeFromOrdinal`（:25）兜底未知阶段回读。
- `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetExitGate.kt:28`：I-21 负载子类。只登记 / 清存 `retainedRequest`；`beginExit`（:49）改为先问基类迁移结果，迁移成功才登记请求；`releaseOnHostDestroy`（:57）走基类安全态回落。
- `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetGuard.kt:27`：I-20 纯快照比对守卫。保留既有开合前后快照记录与 `restoredExactly` 比对；新增 `saveBaseline`（:66）纯数据保存、`restoreBaseline`（:72）重建后恢复接缝。
- `app/src/main/java/io/legado/app/ui/book/read/ink/ReaderSheetState.kt:68`：I-19 组合状态持有者。新增代次闸纯规则 `SheetGenerationPolicy`（:17，`shouldAdmitRequest` :26）；`bindHostGeneration`（:106）宿主代次回写；`open`（:116）串联代次闸、离场闸、B20 守卫闸；`dismiss`（:141）两段式收口；`saveSnapshotBaseline`（:159）、`restoreAfterRecreation`（:165）、`releaseOnHostDestroy`（:175）回位与销毁接缝；`rejectedRequestCount` 汇总三类拒收。
- `app/src/main/java/io/legado/app/ui/book/read/ink/InkShellState.kt:34`：I-18 壳组合状态。气泡显隐、底栏显隐、派发模式组合；新增冗余 Hidden 关闭计数 `redundantHiddenDismissCount`（:43）；KDoc 列全气泡 / 底栏状态机与非法迁移处置。
- `app/src/test/java/io/legado/app/ui/book/read/ink/SheetExitPhaseGateRejectTest.kt:21`：基类拒收计数、未知 ordinal 兜底、强制收口、离场段重复关闭单测。
- `app/src/test/java/io/legado/app/ui/book/read/ink/ReaderSheetStateRestoreTest.kt:25`：回位三场景（进程重建 / 代次闸 / 离场后重进）与边界一、二、三、四单测。

## 三、每类不变式

- `SheetExitPhaseGate`：阶段判定只有本类一份事实源，子类禁止另写阶段判断；`phase` 只经本类内部 setter 改写；拒收只累加计数并停留在原安全态，不抛异常；派生属性全部由 `phase` 派生，不读子类负载槽位；未知阶段回落安全默认（不可见、子树不组合、可开）。
- `ReaderSheetExitGate`：`retainedRequest` 只是离场段保留子树的请求副本，不是第二份开合记录；收束必清存负载；宿主销毁强制收口同样清存负载，不回调宿主；不持 Activity / Context。
- `ReaderSheetGuard`：只读快照、不读写宿主阅读引擎；两次快照同一口径；可见性事实源不在本类；`snapshotBefore` 为首份基线，已开态重复打开不得冲掉；保存 / 恢复只搬运纯数据 `ReaderSnapshot`，不持 Android Bundle / Context。
- `ReaderSheetState`：可见性唯一事实源是 `request`（null 即关闭），`isOpen` 由它派生；离场段保留请求只存于 `ReaderSheetExitGate.retainedRequest`；代次闸先于开合判重；拒收可观测且不抛异常；Compose 可观察态只做薄包装，判定逻辑全部在纯函数 / 纯守卫层。
- `ReaderShellState`：`dispatchMode` 构造时固定；`bubbleState` 恒为 Hidden 或 Visible，Visible 必带非空锚点与命中；`barVisible` 为布尔态，翻转底栏不连带气泡状态；锚点由壳在命中时记录，本状态不解析命中、不碰引擎。

## 四、状态机迁移表（逐条陈述）

- 基类合法迁移一：REMOVED → OPEN，由 `tryOpen` 放行，子类 `open` 经继承的 `enterOpen` 进入在场段。
- 基类合法迁移二：OPEN → EXITING，由 `beginExit` 进入离场段，子树保留组合、可见性派生为 false。
- 基类合法迁移三：EXITING → REMOVED，由 `finishExit` 收束，回调 `onFinishedExit` 清存子类负载。
- 非法迁移一：OPEN / EXITING 段再调 `tryOpen`，拒收、阶段不动、计数 +1。
- 非法迁移二：REMOVED / EXITING 段调 `beginExit`，空操作、阶段不动、计数 +1。
- 非法迁移三：REMOVED / OPEN 段调 `finishExit`，空操作、阶段不动、计数 +1。
- 授权强制收口：任意阶段经 `resetToRemoved` 直接回落 REMOVED 并清存负载；这是拆链授权，不计非法迁移拒收。
- 气泡状态机：Hidden → Visible 由 `showBubble` 记录锚点；Visible → Hidden 由 `dismissBubble` 清气泡；Visible → Visible 重复 `showBubble` 以新命中覆盖；Hidden → Hidden 冗余关闭为安全空操作并记冗余计数。
- 底栏状态机：可见 ⇄ 收起由 `toggleBar` 翻转；任意态 → 可见由 `showBar` 置可见，重复调用幂等；冗余调用回退当前安全态，不抛异常。

## 五、边界兜底九条（逐条迂回方案）

- 边界一：弹层未开时请求关闭。`dismiss` 读到 `isOpen` 为 false 即空操作，不回写 after 快照、不改基线，`rejectedRequestCount` +1；用例见 `ReaderSheetStateRestoreTest.kt` 边界一。
- 边界二：连续两次离场请求。第二次 `beginExit` 由基类判非 OPEN 拒收，阶段停在 EXITING，保留请求不被新请求替换，拒收计数 +1；用例见 `SheetExitPhaseGateRejectTest.kt` 离场段重复 beginExit。
- 边界三：离场中途再次打开。`open` 先过 `exitGate.canOpen`，EXITING 段拒收；首请求保留在离场闸，不重采快照、不切换内容；用例见 `ReaderSheetStateRestoreTest.kt` 边界三。
- 边界四：宿主销毁时弹层仍开。`releaseOnHostDestroy` 走基类 `resetToRemoved` 强制回落 REMOVED、清存保留请求与当前请求；不回调已销毁宿主、不持宿主引用；守卫基线保留以备重建后回位比对；用例见 `ReaderSheetStateRestoreTest.kt` 边界四。
- 边界五：阶段判定收到未知 phase。`safeFromOrdinal` 对 null / 越界 ordinal 返回 null，调用方回落 REMOVED 安全态；派生属性对任何意外阶段给安全默认（不可见、不组合、可开），不猜测未知阶段；用例见 `SheetExitPhaseGateRejectTest.kt` 未知 ordinal。
- 边界六：重建后代次 0 的残留 / 恢复态请求自动弹层。`SheetGenerationPolicy.shouldAdmitRequest` 判 `requestGeneration > 0`，代次 0 拒收，不改基线、不登记会话；用例见 `ReaderSheetStateRestoreTest.kt` 场景二。
- 边界七：已开态重复打开（连点竞态，B20 闸）。守卫 `open` 按透传 `alreadyOpen` 拒收重复请求，首份 `snapshotBefore` 不被冲掉，旧 after 不残留。
- 边界八：离场段重复 `finishExitAnimation` 收尾回调。基类 `finishExit` 判非 EXITING 拒收空操作，不清掉下一轮会话负载，计数 +1。
- 边界九：进程重建后无基线可恢复。`restoreBaseline(null)` / `restoreAfterRecreation(null)` 回到未打开态，after 快照清空，不抛异常；用例见 `ReaderSheetStateRestoreTest.kt` 场景一尾部。

## 六、二阶三阶影响（含与主壳代次闸的双闸竞态推演）

- 一阶：本闸族全部纯 JVM，不引用 Android / Compose 运行时类型；B4-07 上屏接走时只接 `ReaderSheetState` 上的 Compose 薄包装与既有 `ReaderSheetOverlay` 调用面，判定逻辑不动。
- 二阶：与 `ShellRestorePolicy`（`app/src/main/java/io/legado/app/ui/main/shell/ShellRestorePolicy.kt:22`，`shouldApplyHostRequest(requestGeneration) = requestGeneration > 0`）的双闸竞态分析。主壳闸管底部 Tab 槽位恢复；本闸族管阅读页弹层会话，两层各管一槽，不互相替代。竞态场景：进程重建后冷启动恢复阅读位置，此前排队或残留的弹层 open 请求随恢复回路到达；若两套闸口径不一致，会出现恢复态被残留请求覆盖。优先级：外层主壳 `ShellRestorePolicy` 先定 Tab 槽，进入阅读壳后弹层代次闸再定是否自动弹层；主壳优先，阅读壳次之，弹层闸不回写主壳槽位。规避：两层共用同一判据 `requestGeneration > 0`；阅读壳代次由宿主 composition 经 `bindHostGeneration` 回写，不从自身 saveable 读第二份开合标记；不另存 sheet-open 持久化布尔（R5 不增第二份开合记录）。
- 三阶：若 B4-07 把弹层恢复也挂上 rememberSaveable，代次来源必须收口到同一宿主代次闸，避免出现第三份代次口径；本单已留纯数据基线接缝（`saveSnapshotBaseline` / `restoreAfterRecreation`），Bundle 桥接属上屏接线批次，本单不接。
- 反作用：代次闸默认 `LIVE_GENERATION = 1`，既有直连调用（`ReadBookActivity.openReaderSheet`）按活进程口径放行，现行为不变；只有宿主显式 `bindHostGeneration(0)` 才进入重建恢复态口径。若日后在未回写代次的路径上直接构造状态持有者，会默认活进程放行，这一点须在 B4-07 接线清单内复核。

## 七、自查反例（改坏推演）

- 反例一：子类各自写一套阶段判断，出现第二份迁移规则，连点 / 离场竞态下两套口径漂移。否证：基类收口唯一判定；`ReaderSheetExitGate.beginExit` 只看基类返回布尔登记负载，`NoteDialogExitGate` 静态回证无自写判断。
- 反例二：拒收时抛异常或改写阶段，连点竞态下崩在退场路径。否证：所有拒收分支只累加计数、停留原阶段，无 throw；计数纯增量，不参与派生判断。
- 反例三：保存 / 恢复接缝直接持 Bundle 或宿主引用，纯 JVM 单测无法构造。否证：接缝只搬运 `ReaderSnapshot` 纯数据；Bundle 桥接留待上屏批次，本层不引 Android 类型。
- 反例四：代次闸默认按重建口径（0）放行拦截，导致既有用户点字首开被误拒。否证：构造默认 `LIVE_GENERATION = 1`，既有无参构造调用路径行为不变；代次 0 仅由宿主显式回写进入。
- 反例五：销毁收口经过离场动画回调，回调打到已销毁宿主。否证：`releaseOnHostDestroy` 走基类 `resetToRemoved` 直接清负载，不触发宿主侧动画回调。
- 边界：本单只验证静态结构与纯逻辑推演；动画时序、真连点窗口、重建后持久化往返未在运行期验证。

## 八、新发现问题

- 问题一：`ReaderSheetExitGate.beginExit` 原有自写 `if (phase != OPEN) return` 阶段检查，与基类迁移规则重叠，违反定界 §六二阶风险 5 的基类口径唯一。处置：已闭环——改为 `super.beginExit()` 返回布尔，子类仅在迁移成功后登记请求，阶段判断只留基类一份。
- 问题二：`NoteDialogExitGate`（B26 兄弟子类）本单未改动；静态回证其 `open()` 仅调继承的 `enterOpen()`，无阶段判断重叠。处置：已闭环（静态回证，非运行期验证）。
- 问题三：`SheetExitController` / `SourceSheetExitController` 两个包装控制器直接消费基类 `visible` / `isComposed` / `canOpen` / `phase`；基类新增拒收计数为纯增量属性，未改既有签名。处置：待裁——B4-07 上屏接线时复核控制器是否需要透出拒收计数用于竞态观测。
- 问题四：采集时点工作区存在 B4-02 翻页七件并行未提交改动；本单 diff 以 HEAD 加当前工作树为准。处置：已入队——并发链落地后回执复跑四包空集与基线指纹比对，失配按 B4-01 第六节流程处置。

## 九、未验证项

- 沙箱禁编译（E8）：未本地跑 gradle / kotlinc；编译结果与单测全绿以 CI 为准，本单不报「已编译通过」。
- 新增两份单测未在沙箱执行；断言口径为静态推演，CI 跑通且 0 failed 后才算数。
- Bundle / saveable 桥接未接，属 B4-07 上屏批次；纯数据基线接缝已就位，持久化往返未做运行期验证。
- 未上真机；离场动画时长、连点时序沿用组件既有锚点，未做帧级复测。
- 旧阅读页实现（`ReadBookActivity`、旧 XML / Binding 侧）本单一律未动；其下线归 B4-31，交接注记已写在新建件 KDoc。

## 十、回执引用与机检记录

基线引用：B4-01 冻结基线 docs/修复-B4-01-冻结基线.md（HEAD 6075716）；四包 diff 空集见该单第二节；API 行哈希见第三节；paragraph 清单见第四节；只读引用集指纹见第五节。本单回执另附实跑四包空集双通道输出。

- 四包 diff 空集命令：`git diff --name-only HEAD -- app/src/main/java/io/legado/app/ui/book/read/page/provider app/src/main/java/io/legado/app/ui/book/read/page/entities app/src/main/java/io/legado/app/ui/book/read/page/api app/src/main/java/io/legado/app/ui/book/read/page/delegate`；实测输出空，退出码 0。
- 四包未跟踪回证命令：`git status --porcelain` 限定同四包路径；实测输出空。
- 采集时点 HEAD：`git rev-parse --short HEAD` 实测 `11cac19`。
- 机检记录见第十一节（落盘后实跑，末行原样粘贴）。

## 十一、机检记录（落盘后实跑，末行原样粘贴）

- 命令 1：`python3 data_scripts/check-report-fields.py docs/修复-B4-03-Sheet闸族状态簇.md`
  - 实测末行：`REPORT-FIELDS: PASS（1 份，0 份不合格）`；前置行 `PASS docs/修复-B4-03-Sheet闸族状态簇.md`；退出码 0。
- 命令 2：`python3 data_scripts/check-anchor-card.py --strict docs/修复-B4-03-Sheet闸族状态簇.md docs/锚点/B4-03-Sheet闸族状态簇.md`
  - 实测末行：`汇总: PASS=2 FAIL=0（共 2 份）`；退出码 0。
  - 非阻断 WARN 2 条：条款编号 C3、E8 被编号反查规则当作次要提及号，无对应锚点卡；按 R2 口径次要提及仅提示不判红，与 B4-01 先例同性质。
- 命令 3：`python3 data_scripts/check-onboarding.py`（按工作区改动报告集扫描，不接收单文件参数）
  - 实测末行：`ONBOARD: PASS（2 份报告）`；退出码 0；本单回执块、现行指纹、三条逐字引用回源、适用条款均通过。
- 命令 4：`python3 data_scripts/check-problem-finding.py docs/修复-B4-03-Sheet闸族状态簇.md`
  - 实测末行：`PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`；退出码 0。
- 命令 5：`python3 data_scripts/check-compose-pitfalls.py app/src/main/java/io/legado/app/ui/book/read/ink app/src/test/java/io/legado/app/ui/book/read/ink`
  - 实测末行：`COMPOSE-PITFALLS: PASS（60 个 Kotlin 文件，A/B/C 三类形态 0 命中）`；退出码 0。
- 命令 6：`python3 data_scripts/kotlin-syntax-lint.py`（本单七件 `.kt`）
  - 实测末行：`KOTLIN-LINT: PASS（7 个文件）`；退出码 0。
- 边界声明：未 git add / commit / push；未本地编译、未跑单测（E8）；编译与单测绿红以 CI 为准。
