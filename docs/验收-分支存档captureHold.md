> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B8R2-侧翼敌军半径.md

# 独立验收 · B7 分支存档 captureHold（据点计数 / 归属串线）

- **被验方自述**：`docs/修复-B7-分支存档收口.md`（逐字读过，结论不采信自述，逐条独立 grep / 读码回源）。
- **被验提交**：`304ee9c`（fix(B7)：据点持有方入快照并恢复回写 + 7 例跨分支隔离）；前置模型补齐 `1851694`、测试编译回滚 `0ef4d42`；后续测试编译修补 `2ff867d`。`git merge-base --is-ancestor 304ee9c HEAD` 退出码 0，四提交均为当前 HEAD 祖先。
- **验收角色**：独立验收 AI，只判不改。本轮未改任何源码 / 测试 / 文档 / 门禁，未做 git add / commit / push；沙箱无 gradle / JDK21 / SDK，未本地编译、未跑单测红绿。
- **行号口径**：证据行号以当前 HEAD `c9414b3`（2026-11-26）工作树 blob 为准；本单生产树 `app/src/main/java/io/legado/app/ui/map/crossing/` 无未提交改动（`git status` 干净），工作区另有无关节点 `app/src/main/java/io/legado/app/help/bubble/SlotArbitrator.kt`（P06 名物槽域，未跟踪），不计入本单。
- **问题清单锚点**：`docs/问题清单.md:56` B7 行，旧证据锚 `CampaignModels.kt:219-224`、`CampaignController.kt:351-370` 为修复前行号；HEAD 现值见下文。

---

## 一、总结论

**静态验收：有条件通过。** 六条判据中判据 1 / 2 / 3 / 4 / 5 / 6 静态层全部 PASS；B7 主诉缺陷（快照缺 `captureHold`、恢复后据点计数串线）在当前 HEAD 上链路完整：字段落地、写入、整表回写、跨分支隔离均有代码与测试锁定。未发现阻断性代码缺陷。

判低 / 观察项汇总（详见第四节）：

- D-1（新发现缺陷，必做后续）：`_eventLog` 事件日志既不入快照、恢复分支时也不清空，被丢弃时间线的日志条目会残留到恢复后的分支面板；属跨时间线串线的同族残留，B10 分支对比接线后用户可见。
- D-2（观察项，建议顺手清）：`marchCells` 回合级行军格数表恢复分支时不清零，非移动部队残留上一时间线的格数，风险限于下一回合士气结算前的窗口。
- D-3（测试卫生）：隔离测试 `:58-59` 仍有两处 `!!`（上方已有 `assertNotNull` 守卫），被验报告「新测试文件 `!!` 计数 = 0」的自述在 HEAD 上已不成立。
- 运行态缺口：本沙箱未跑 `testAppDebugUnitTest`；HEAD 上 7 例隔离用例 + F6 回滚用例的红绿以云端 CI 终验为准。

---

## 二、六条判据逐条证据

### 判据 1 · 字段落地 —— PASS

- 快照结构 `BranchSnapshot` 定义于 `app/src/main/java/io/legado/app/ui/map/crossing/model/CampaignModels.kt:280-270`。
- `:266` `val captureHold: Map<String, Int> = emptyMap()`：据点连续占领计数，key 为据点标识（lng,lat 四位小数），value 为连续回合数。
- `:269` `val pointHolders: Map<String, String> = emptyMap()`：据点归属表，key 同上，value 为阵营 id。
- 默认值口径一致：两表均为 `= emptyMap()` 缺省空表，供旧档构造点兼容（旧五参构造 `BranchSnapshot(turn, elapsedDays, corps, firedEventIds)` 不传两表可直接编译，B7-3 / B7-7 用例即走此路径）。
- 序列化方式：`BranchSnapshot` 全仓 grep 无 JSON / encode / decode 消费点，存档仅内存流转（`saveBranch` 返回对象、`restoreBranch` 接收对象），无持久化 schema 兼容负担；两表均为不可变 `Map`，写入侧 `toMap()` 拷贝。
- 对照既有字段：`firedEventIds: Set<Int>`（`:264`）无缺省值，两表为兼容旧档显式带 `emptyMap()`，差异是有意为之，非口径分叉。

### 判据 2 · 写入点 —— PASS

- 运行态成员：`CampaignController.kt:96` `private val captureHold: MutableMap<String, Int>`；`:112` `private val pointHolders: MutableMap<String, String>`。
- 存档写出 `saveBranch()`：`CampaignController.kt:752-759`。
  - `:757` `captureHold = captureHold.toMap()`：把当时连续占领计数整表拷贝进快照。
  - `:758` `pointHolders = pointHolders.toMap()`：把当时据点归属整表拷贝进快照。
  - `:755` `corps = corps.map { it.copy() }`：兵力深拷贝；`:753` turn、`:754` elapsedDays、`:756` firedEventIds 一并入快照。
- 不是只声明字段：写入两侧均有 `toMap()` 实拷，快照与后续回合改动隔离（`:751` KDoc 注明「深拷贝运行态，避免后续回合改动污染存档」）。
- 成员表每回合刷新点 `updateCaptureTracker()`：`:664-672`，`:666` 归属表先清空、`:670` 计数自增或清零、`:671` 归属表按活军位置重建；快照取的是 tracker 刷新后的终态。

### 判据 3 · 读回点 —— PASS（非归零重建）

- 恢复入口 `restoreBranch()`：`CampaignController.kt:762-791`。
- 计数表回写：`:769` `captureHold.clear()` → `:770` `captureHold.putAll(branch.captureHold)`。先清空再整表替换快照计数，不是把 counts 归零；若快照非空，恢复后计数与存档时刻逐值一致。
- 归属表回写：`:771` `restorePointHolders(branch.pointHolders, branch.captureHold)`。
- 重建辅助 `restorePointHolders(holders, holdCounts)`：`CampaignController.kt:968-980`。
  - `:969` 归属表先清空；`:970-972` 快照归属非空 → `pointHolders.putAll(holders)` 整表回写后 `return`，不按恢复后军位重算，不沿用旧时间线活军推导。
  - `:974-975` 快照归属为空（旧档缺字段）→ 按恢复后军位 `buildPointHolders()` 重建归属。
  - `:977-978` 仅当旧档连计数表也缺（`holdCounts.isEmpty()`）时，对当前派生占据方补基线 1 回合；不臆造历史回合数。
- 旧缺陷已消除：修复前实现按军 id 向计数表写键（命名空间错配的死条目），且 `clear()` 清掉刚回写的快照计数；HEAD 上该旧单参签名 `restorePointHolders(holders)` grep 零命中，无残留重载。
- 后果排除：若此处归零，恢复后首回合 `VictoryChecker.check` 读到的占领回合数从 0 起算，`holdTurns` 胜利条件被错误推迟、易主判定沿用旧时间线——HEAD 代码不存在该路径。

### 判据 4 · 串线根因消除 —— PASS（据点两表）；同族残留见 D-1 / D-2

时序推演（连续 save → 改局面 → restore → 再 save）：

1. 存档 A：`step()` 后 `saveBranch()`，快照持有 `hA = captureHold`、`pA = pointHolders`、兵力 / 回合 / 已触发事件全套拷贝（`:752-759`）。
2. 改局面：再推若干回合，活军机动、交战、易主；活表经 `updateCaptureTracker()`（`:664-672`）变为 `hB / pB`，快照 A 因 `toMap()` + `corps.copy()` 不受污染。
3. 恢复 A：`restoreBranch(branchA)` 依次回滚兵力（`:763-764`）、回合 / 天数（`:765-766`）、已触发事件（`:767-768`）；计数表 `clear + putAll(hA)`（`:769-770`）；归属表整表回写 `pA`（`:771` → `:970-972`）；回合级士气降级标记清空（`:777`）；情报两表按恢复军位重建（`:775-786`，B9 既定口径）；状态重置进行中（`:787-789`）。
4. 再 save：新快照持有 `hA / pA`，不携带 `hB / pB`。隔离由 `B7-2 跨分支隔离` 用例锁定（A 记 a / 计数 1，手工造 B 记 b / 计数 2，切 B 再切 A，归属回到 a、计数回到 A 值，`CampaignBranchIsolationTest.kt:46-73`）。

快照 vs 实时状态全字段比对（逐个成员过堂，非抽样）：

- 已入快照：`corps`（`:755` 深拷）、`turn`（`:753`）、`elapsedDays`（`:754`）、`firedEvents`（`:756`）、`captureHold`（`:757`）、`pointHolders`（`:758`）。
- 有意重置、不入快照：`status` / `winnerSide` / `finishReason`（`:787-789` 恢复时强制 ONGOING / null / 空串）；分支恢复语义即重开战局，终局标志不回滚属设计选择。
- 有意重建、不入快照：`intel` / `reports`（`:775-786` 按恢复军位重新播种决策层情报，视图目睹记录清空）；B9 已收口口径，注释写明，不属 B7 缺陷。
- 载入期常量、非时间线可变状态：`heightMapRaster`（`:103`，解码产物随定义固定）、`windMps` / `temperatureC` / `weatherNotes`（`:168-172`，载入时归一一次）；无需入快照。
- 未入快照且恢复时未处理：`_eventLog`（D-1）、`marchCells`（D-2）、`moraleDowngradedThisRound`（`:777` 已清空，无缺口）。
- Corps 内部字段核对：`strength / fatigue / morale / intent / position / latLng / target / supply / supplyCutTurns / alive / surrendered / exposed / highGround / route / lastKnown / visionRadiusCells / losses / routeDegraded` 均随 `corps.copy()` 携带；`lastKnown` 虽是 `Map`，但写入点 `:497` 是整体换引用（`corps.lastKnown = known`，`known` 为本轮新建可变表），不存在对快照共享 map 的原地改写；`route` 为只读 `List<Cell>`。浅拷在此口径下隔离成立。

### 判据 5 · 断言锁定 —— PASS（测试存在；HEAD 红绿待 CI 回证）

- 专项隔离测试：`app/src/test/java/io/legado/app/ui/map/crossing/engine/CampaignBranchIsolationTest.kt`，7 例 `@Test`：
  - `B7-1 写入往返`：`:26-43`，两表非空、恢复后逐字段相等。
  - `B7-2 跨分支隔离`：`:46-73`，A→B→A 归属与计数不串线。
  - `B7-3 缺字段旧档重建`：`:75-99`，五参旧档构造，归属按军位重建、计数基线 1。
  - `B7-4 空据点表`：`:101-114`，无胜利点两表为空，恢复不崩。
  - `B7-5 重复存档 / 重复恢复不翻倍`：`:116-129`。
  - `B7-6 恢复后推进一回合计数 +1`：`:132-145`，计数 = 存档值 + 1，占据方不变。
  - `B7-7 空军队降级档`：`:148-162`，空存档恢复后两表为空，跑满回合不崩。
- 既有回归用例：`CampaignControllerTest.kt:187-198` `F6 分支存档回滚据点占领计数，不沿用旧时间线`（断言 `captureHold` / `pointHolders` 往返相等）；`:148-164` 回合 / 天数 / 兵力回滚。
- 测试夹具：`HOLD_A_JSON`（`:167-180`）驻点守军 + 远距敌军，首回合两表非空，压住「断言空转」缺口；`NO_POINT_JSON`（`:183-196`）无胜利点对照。
- 运行态未回证：最近一次全绿记录为 `a978f15`（A14：3185 单测全过），当时 B7-1/2/3/6 属 8 例失败后被收口的用例；本沙箱不能编译跑测，HEAD `c9414b3` 上 7 例 + F6 的红绿须云端 `testAppDebugUnitTest` 终验。
- 测试卫生偏差：`CampaignBranchIsolationTest.kt:58-59` 两处 `pointKey!!`（上方 `:51` 已 `assertNotNull` 守卫）；被验报告第二节「新测试文件 `!!` 计数 = 0」在 HEAD 不再成立，建议改局部非空 val，不影响生产逻辑。

### 判据 6 · 边界与只读约束 —— PASS

- 本轮只读：未改源码 / 测试 / 文档外文件，未编译、未提交。
- 工作区隔离：`git status --short app/src/main/java/io/legado/app/ui/map/crossing/` 无输出，engine / model 生产树与 HEAD 一致；B8R4R5 已提交（`9dffb8f`），其后续未以未提交改动落在 crossing 生产树。结论以 HEAD `c9414b3` 为准，不以工作区浮动为准。
- 路径可达性：`saveBranch` / `restoreBranch` 生产树无宿主调用方（grep 仅命中控制器自身与测试）；B10 分支并列对比 UI 尚未接线，本路径当前仅测试可达。D-1 在接线前为潜伏缺陷，接线后用户可见。
- 旧档兼容：存档只在内存流转，不落盘；旧格式反序列化边界不存在，缺字段路径由 B7-3 / B7-7 静态覆盖。

---

## 三、新发现缺陷

### D-1 · 事件日志未随存档回滚（同族跨时间线残留，必做后续）

- 现象：`_eventLog`（`CampaignController.kt:113`）只追加不清空：写入 `:916-917`（超容量队首淘汰），消费 `:730`（`snapshot().events = _eventLog.takeLast(LOG_TAIL)`，尾 8 条上送面板）。
- 缺口：`saveBranch()` 快照未携带日志；`restoreBranch()`（`:762-791`）恢复段没有 `_eventLog.clear()`，也没有从快照回填。
- 时序后果：存档 A（日志 L1）→ 推两回合（追加 L2、L3）→ 恢复 A，`_eventLog` 仍为 `[L1, L2, L3]`；恢复后面板 `takeLast(8)` 展示 L2 / L3 这些属于被丢弃时间线的条目，与回滚后的兵力 / 归属 / 回合对不上。性质与 B7 主诉同族：另一张可变量表没有进快照。
- 等级：展示 / 审计层串线，不影响胜负判定与据点计数；B10 接线分支回放后用户可见。
- 建议修法（不在本单实施）：快照增 `eventLog: List<String> = emptyList()`，save 侧 `_eventLog.toList()`，restore 侧 `clear + putAll`；或退一步 restore 时清空日志并补一条「已恢复分支存档」系统行。补断言：save A → 推进追加日志 → restore A → 断言日志尾段等于存档时刻日志。

### D-2 · 回合行军格数表恢复时未清零（观察项，建议顺手清）

- 现象：`marchCells`（`:116`）记录本回合各军行军格数，供士气推进消费（`:283`）。`restoreBranch()` 未清空该表。
- 风险窗口：恢复后下一回合，移动部队在 `marchAlong()`（`:579`）出发前先置 0、移动后写回（`:593`），士气读取（`:283`）在移动之后；残留值只对本回合不移动的部队有理论影响，且 `?: 0` 兜底。二阶影响低。
- 建议修法：`restoreBranch()` 恢复段与 `moraleDowngradedThisRound`（`:777`）一并 `marchCells.clear()`。

### D-3 · 测试两处 `!!` 与自述不符（测试卫生）

- `CampaignBranchIsolationTest.kt:58-59` `pointKey!!`；守卫在 `:51` `assertNotNull`。建议提取局部非空 val 后构造 map，消除非空断言穿透风险。

---

## 四、未验证项（显式）

- 未编译、未跑单测：沙箱无 gradle / JDK21 / SDK；HEAD 上 7 例隔离用例 + F6 回滚用例的红绿、泛型 / 重载匹配均为静态推断，以云端 CI `testAppDebugUnitTest` 终验为准。
- 未真机、未取 APK：分支对比 UI（B10）未接线，save / restore 链路当前无生产宿主，端到端分支切换画面未验。
- D-1 / D-2 未做运行时复现：日志残留与 marchCells 残留均为静态走查推演，未在引擎实跑中观测面板输出。
- 情报两表重建口径（B9 范围）只核对了恢复段代码与注释，未独立验收 B9 模糊半径 / 目睹重建的正确性。
- 旧档兼容路径（缺字段重建、基线 1 回合）仅静态走查 + 测试夹具覆盖；存档不落盘，无真实旧格式反序列化样本可验。

---

## 五、交接记录（发现未改，越界）

- `CampaignControllerTest.kt`、`CampaignIntelFogTest.kt` 历史上的 `!!` 断言属 B9 测试域，本单未动；D-3 仅指 B7 隔离测试自身两处。
- D-1 修复若增 `BranchSnapshot.eventLog` 字段，注意与 B10 `DiffField` 日志差异维度、`TurnSnapshot.events` 尾裁口径对齐，避免第二套日志快照源。
- 本单结论不覆盖 B10 分支对比 UI 接线验收；接线时须把 D-1 日志回滚一并纳入验收清单。
