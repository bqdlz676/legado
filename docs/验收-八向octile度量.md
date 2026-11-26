> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B8R2-侧翼敌军半径.md

# 独立验收 · B8R3 八向寻路度量（名义八向实曼哈顿收口为真 octile）

- **被验方自述**：`docs/修复-八向octile度量.md`（逐字读过，结论不采信自述，逐条独立 grep / 读码回源）。
- **被验提交**：`38c2edc`（修 B8R3，三 main）、`3850709`（测 B8R3，两测试）。两提交均为当前 HEAD 祖先（`git merge-base --is-ancestor 3850709 HEAD` 退出码 0）。
- **验收角色**：独立验收 AI，只判不改。本轮未改任何源码 / 测试 / 文档 / 门禁，未做 git add / commit / push，未本地编译、未跑单测（沙箱无 gradle / JDK21 / SDK）。
- **行号口径**：证据行号以提交 `38c2edc` / `3850709` 内 blob 为准；工作树当前另叠有未提交的 B8R4 / B8R5 在飞改动，部分行号已后移，文内一并标注工作树现值。
- **环境漂移提示**：验收时点工作树 `app/src/main/java/io/legado/app/ui/map/crossing/` 下 `CampaignController.kt`、`Pathfinder.kt`、`model/CampaignModels.kt`、`shell/CrossingShell.kt` 为未提交修改（B8R4 `routeDegraded` 降级入账、B8R5 目标格豁免），不属本单两提交；本报告结论锚定提交 diff，不把漂移计入 B8R3 功过。

---

## 一、总结论

**静态验收：有条件通过。** 八条判据中判据 1 / 2 / 4 / 6 / 7 / 8 为 PASS；判据 3 静态 PASS，编译咬合待云端 CI；判据 5 有条件通过（平局边界无测试锁定、路径级「起点=终点」早返回无专门用例）。未发现阻断性代码缺陷；未发现第三处「名义八向实曼哈顿」副本；未发现 Double 化引入的浮点 `==` 比较、HashMap key 或存档序列化类型错配。

判低 / 观察项汇总（详见第四节）：
- D1：同代价平局出队顺序无任何测试断言锁定；路径级 `from == to` 早返回亦无专门用例（度量层重合断言已有）。
- D2：被验报告第四节「√2 截断误差约 2.13e-7」量级失准，实测约 2.373e-9（文档级笔误，不影响代码）。
- D3：`IntentResolver` 无专门单测；Double 化后斜向分支的几何翻转仅静态推演，待 CI 既有裁决相关测试实跑。
- D4：工作树存在未提交 B8R4 / B8R5 改动叠加，回源与后续派单须按提交隔离，勿混入本单。

---

## 二、八条判据逐条证据

### 判据 1 · 单源成立 —— PASS

- 八向公式形态全 main 仅 1 处。
  命令：`grep -rn "max(dc\|maxOf(dc\|min(dc\|minOf(dc" app/src/main/java --include=*.kt`
  输出（提交内 blob 行号）：
  - `app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt:312:    return max(dc, dr) + (OCTILE_DIAGONAL_FACTOR - 1.0) * min(dc, dr)`
  - 工作树现值：同文件 `:315`（B8R5 KDoc 插入 3 行所致，公式本体未变）。
- 旧私有曼哈顿副本 grep 为 0。
  命令：`grep -rn "private fun octile" app/src/main/java --include=*.kt`，退出码 1，无匹配。
  顶层仅存 `fun octile(a: Cell, b: Cell): Double`（`Pathfinder.kt:309`）与 `fun octileCells(a: Cell, b: Cell): Int`（`:320`），均为单源出口。
- 对角系数常量仅 1 处声明。
  命令：`grep -rn "DIAGONAL_FACTOR" app/src --include=*.kt | grep -v "OCTILE_DIAGONAL_FACTOR"`，退出码 1，旧名 `DIAGONAL_FACTOR` 零残留。
  声明唯一：`Pathfinder.kt:300: const val OCTILE_DIAGONAL_FACTOR: Double = 1.41421356`；`:192`（stepCost）与 `:312`（octile 公式）为引用，非第二声明。
- 提交 diff 复核：`38c2edc` 删除了 `CampaignController.kt` 旧私有 `maxOf(dc,dr)+minOf(dc,dr)` 与 `IntentResolver.kt` 旧私有 `max(dc,dr)+min(dc,dr)`，两处函数体均恒等于曼哈顿 `dc+dr`；删除后原位留注释指向顶层单源（`CampaignController.kt:884-885`、`IntentResolver.kt:147`）。

### 判据 2 · 八向度量全覆盖 —— PASS（help/sim 定性为孤立旧包，不与新口径冲突）

穷举命令：`grep -rn "hypot\|maxOf\|minOf\|max(\|min(\|abs(\|col - \|row - \|dc \*\|dr \*\|neighbors8\|octile\|cellDist\|manhattan" app/src/main/java/io/legado/app/ui/map/crossing --include=*.kt`。

crossing main 内全部距离 / 邻接点逐一定性：

- 真八向格距，已收口顶层单源 `octile`：
  - `Pathfinder.kt:200` `heuristic = octile(a, b) * MIN_TERRAIN_FACTOR`；`:245` `cellDist = octile(a, b)`；`:237` 直线兜底 `minByOrNull { cellDist(it, to) }`；`:178` 侧翼闸门 `cellDist(enemy, cell) > viewRadius`。
  - `CampaignController.kt:472` 情报目睹闸门、`:573` 遭遇范围过滤、`:574` 最近敌军排序、`:605` 溃逃远离排序、`:682` 快照观察者闸门；`:481` 存档整数出口 `octileCells`。
  - `IntentResolver.kt:37` 最近敌军排序、`:38` 最近距离、`:65` 补给点排序、`:86` / `:87` 支援相邻、`:102` 关隘排序、`:140` 溃逃方向。
- 非格距度量，本单不动且定性正确：
  - `CampaignController.kt:777`、`Pathfinder.kt:211`：`dc*dc + dr*dr` 平方欧氏，仅用于目标不可通行时周边落点平局挑选，不是移动代价 / 视野闸门。
  - `CampaignController.kt:879-881`、`TriggerEngine.kt:79-81`、`VictoryChecker.kt:153-155`：`hypot(dLat, dLng)` 经纬度公里距离（触发半径 / 胜利抵达半径），单位公里，与栅格八向度量无关。
  - `terrain/TerrainMap.kt:112`：`hypot(col, row)` 只算 LOS 采样点数 `samples`，不是距离闸门；`:194` `max(abs,abs)` 是河流栅格化采样步数（切比雪夫步数），不是移动度量；`:127` `neighbors4` 仅用于坡度四邻；`:137` `neighbors8` 为八向邻居表，自带 `inBounds` 过滤（`:137-145`）。
  - `shell/CrossingShell.kt:1080` / `:1096`：`hypot(dx, dy)` 是手势像素位移，非栅格度量。
  - `IntentResolver.kt:118-120`：四向格存在性枚举（`surrounded` 拓扑包围判定），不是距离度量，保留四向正确。
  - `terrain/TerrainCost.kt`：grep 无任何距离符号，不含格距度量。
- 未发现第三处「名义八向实曼哈顿」副本：crossing main 内除已删两处外，无 `dx+dy` / `max+min` 形态格距公式。

help/sim 旧包结论：

- 命令：`grep -rn "import io.legado.app.help.sim" app/src --include=*.kt`，退出码 1——crossing 及其余 main 包对 help/sim **零 import**。
- `help/sim/SimEngine.kt:338` 调用的 `IntentResolver.resolve(army, this)` 是同包 `io.legado.app.help.sim.IntentResolver`（无 import 即同包），与 crossing engine 的 `IntentResolver` 是两套类型、两套签名，无继承 / 调用关系。
- help/sim 内部度量为欧氏 `hypot`（`Pathfinder.kt:88/120/162`、`SimEngine.kt:209/225/246`、`IntentResolver.kt:212/223`、`TerrainAnalyzer.kt:91/97/281/352`）与切比雪夫 `max(abs,abs)`（`IntentResolver.kt:174` `cheb`、`:179`、`:205`；`SimEngine.kt:305/448/465/474`）混用；包内无 `octile` 命名函数、无 `max+min` 曼哈顿公式。
- 测试侧仅 `app/src/test/java/io/legado/app/help/sim/EventTriggerTest.kt` 覆盖同包 `EventTrigger`；`SimEngine` / 旧 `Pathfinder` / 旧 `IntentResolver` 无生产实例化点（`SimEngine` 仅被自身 companion `:518` 构造）。
- 冲突判定：help/sim 与 crossing 不共享 `Cell` 类型（旧件用 `SimArmy.r/c`），不共享度量函数，新 octile 口径不会反向污染旧包；旧包内部欧氏 / 切比雪夫混用是否另立一单，维持被验报告第九节交接项，本单不连带修改。

### 判据 3 · 类型改动安全 —— PASS（静态；编译咬合待 CI）

`CampaignController` 5 个调用点（提交内行号）逐一核上下文：

- `:472` `val distCells = octile(...)`（Double）；`:473` `distCells <= corps.visionRadiusCells`，右侧 `Corps.visionRadiusCells: Int`（`model/CampaignModels.kt:162`）。Double 与 Int 比较走 Kotlin stdlib `Double.compareTo(Int)` 重载；无 `==` 浮点相等比较；`distCells` 未落 HashMap key、未序列化。
- `:481` `seenDistCells = octileCells(...)`，`octileCells` 返回 Int，`IntelRecord.seenDistCells: Int`（`:156`），类型对齐。
- `:573` `octile(...) <= ENCOUNTER_RANGE`，`ENCOUNTER_RANGE: Int = 1`（`:962`）；比较方向 `<=`，无相等判定。
- `:574` `minByOrNull { octile(...) }`：仅排序选择器，Double 排序无阈值比较。
- `:605` `maxByOrNull { octile(it, threat) }`：仅排序选择器。
- `:682` `val d = octile(...)`；`:683` `d <= ob.visionRadiusCells`（Int），同上比较重载。

`IntentResolver` 7 个 octile 表达式（提交内行号）逐一核上下文：

- `:37` `minByOrNull { octile(...) }`：排序选择器。
- `:38` `nearestEnemyDist = nearestEnemy?.let { octile(...) } ?: Double.MAX_VALUE`：类型 Int→Double，缺省值同步由 `Int.MAX_VALUE` 改 `Double.MAX_VALUE`；无残留 Int 缺省。
- `:41` `nearestEnemyDist <= ADJACENT_CELLS`（Int=1，`:150`）。
- `:58` `nearestEnemyDist < AMBUSH_ENEMY_RANGE`（Int=5，`:153`）。
- `:65` `minByOrNull { octile(...) }`：排序选择器。
- `:70` `nearestEnemyDist < HOLD_HIGH_GROUND_RANGE`（Int=3，`:159`）。
- `:86` / `:87` `octile(...) <= ADJACENT_CELLS`（Int=1）两处。
- `:98` `nearestEnemyDist > SEIZE_ENEMY_RANGE`（Int=5，`:165`）。
- `:102` `minByOrNull { octile(...) }`：排序选择器。
- `:140` `enemies.minOf { octile(c, it.position) }`：`minOf` 选择器返回 Double，供 `maxByOrNull` 选择器；旧 `.toDouble()` 已删，类型链自洽。

专项排查结果：

- 浮点 `==`：命令 `grep -n "nearestEnemyDist\|distCells\|.toInt()\|== " IntentResolver.kt CampaignController.kt`，距离变量无任何 `==` / `!=` 相等比较；命中的 `==` 均为枚举 / 军 id / 位置相等判定，与距离 Double 无关。
- HashMap key：A* `gScore` / `cameFrom` / `closed` 键为 `Long` 编码（`Pathfinder.kt:132-136`，`encode` `:257`），距离不作键。
- 序列化 / 视图下发：`nearestEnemyDist` 为函数局部 val，`Decision` 只携带 `intent / target / reason`（`IntentResolver.kt:20-24`），不外泄距离；测试侧 `grep -rn "nearestEnemyDist" app/src/test` 退出码 1，无测试按旧 Int 类型断言。
- 残留导入：`IntentResolver.kt` 已删 `kotlin.math.abs/max/min`，命令 `grep -n "abs\|max(\|min(\|kotlin.math" IntentResolver.kt` 退出码 1；`Pathfinder.kt` 保留的 `abs/max/min/roundToInt` 均被顶层度量函数使用。
- 待 CI 项：Double 与 Int 阈值的混合比较解析、`minOf` / `minByOrNull` 类型推断、顶层函数同包可见性，沙箱未编译，静态层面未见类型错配，最终咬合以云端 `testAppDebugUnitTest` 为准。

### 判据 4 · 存档口径 —— PASS

- `BranchSnapshot` 字段（`model/CampaignModels.kt:280-259`）：`turn / elapsedDays / corps: List<Corps> / firedEventIds / captureHold / pointHolders`，**不含** `IntelRecord` / `seenDistCells`。
- 存档写入：`CampaignController.kt:726-733` `saveBranch()` 仅 `corps.map { it.copy() }` 深拷贝 Corps，情报表不进快照。
- 存档恢复：`:749-759` `restoreBranch()` 先 `intel.clear()` / `reports.clear()`，再按恢复后军位重新播种，`seenDistCells = 0`；旧档里即便残留曼哈顿口径目击距离，也不会被读回参与结算。
- 视图不下发：`CorpsView`（`CampaignModels.kt:207-237`）无 `seenDistCells` 字段；命令 `grep -n "seenDistCells" model/CampaignModels.kt` 退出码 1。
- 落盘链路：crossing 包内无 `Serializable` / `@SerialName` / `Json.encode` / `toJson`；`BranchSnapshot` / `saveBranch` / `restoreBranch` 在 shell / globe replay 包零引用（命令 `grep -rn "BranchSnapshot\|saveBranch" app/src/main/java/io/legado/app/ui/map/crossing/shell/ app/src/main/java/io/legado/app/ui/map/globe/` 退出码 1），快照仅存于内存分支对比。
- 旧档兼容测试在档：`CampaignBranchIsolationTest.kt:82-88` 以只传核心四字段的 `oldStyle` 快照恢复成功；`CampaignControllerTest.kt:276-289` 同构旧档用例。
- 口径一致性：`seenDistCells` 唯一新写入点 `:481` 走 `octileCells` 四舍五入；既有 B9-10（轴向 dc=4）、B9-11（轴向 dc=2）夹具轴向布位，octile 与曼哈顿同值，留档距离 4 / 2 不变；斜向格位留档值由旧曼哈顿改为 octile 取整，属本单预期口径修正，且该字段不持久化、不跨版本读回，无老档行为突变。
- 下游半距项：`:831` `(record.seenDistCells / 2).coerceIn(0, type.visionRadiusCells)` 仍为 Int 整数除法，取整误差上界 0.5 格、半距项 0.25 格，再经封顶不外溢。

### 判据 5 · 断言映射 —— 有条件通过（D1：平局边界无断言；路径级重合无专门用例）

`GridMetricTest.kt` 10 条逐条映射（提交 `3850709` blob，`@Test` 计数 10）：

1. `:32-35` 轴向：`(3,0)` / `(0,3)` 断言 3.0（容差 1e-9）。
2. `:38-40` 对角一格：`(1,1)` 断言等于 `OCTILE_DIAGONAL_FACTOR`。
3. `:43-47` 横三纵二：`(3,2)` 公式值 + 区间 `3.8..3.9` 双断言。
4. `:50-52` 对角两步：`(2,2)` 断言 `2.0 * OCTILE_DIAGONAL_FACTOR`。
5. `:55-60` 重合与对称：`octile(a,a)=0.0`；`octile(a,b)=octile(b,a)`（容差 1e-12）。
6. `:63-73` `octileCells` 取整：5 组夹具误差 ≤ 0.5+1e-9；显式断言 `(1,1)→1`、`(3,2)→4`、`(2,2)→3`。
7. `:76-88` 委托相等：5 组夹具 `finder.cellDist(a,b)` 与顶层 `octile(a,b)` 逐值相等（容差 1e-12）。
8. `:91-97` 反例一：`(3,2)` octile ≤ 4.0；旧曼哈顿 `3+2=5 > 4`，新旧实现可区分。
9. `:100-104` 反例二：`(2,2)` octile ≤ 3.0；旧曼哈顿 `2+2=4 > 3`。
10. `:107-112` sanity：平场 `Pathfinder` 可装配，`find` 路径非空。

`CampaignControllerTest.kt` 新增 1 条（提交内 `@Test` 计数 29，即既有 28 + 新增 1）：

- `:392-405`（工作树行号，`@Test` 在 `:392`、函数在 `:393`）`B8R3 斜向偏移三二格在四格视野内 按八向度量目睹留档`：`a1 (8,8)` → `b1 (11,10)`，横三纵二；双 `regular`（半径 4，夹具 `RADIUS_JSON:546-559`）；`step()` 后断言 `reportRadiusCells("b1") == 0` 且视图 `observed` 为真。旧曼哈顿 5 > 4 下该用例不落报告，可区分新旧实现。

四类边界覆盖核查：

- **起点 = 终点**：度量层有断言（用例 5，`:58` `octile(a,a)=0.0`）；路径层早返回 `Pathfinder.kt:13`（`find`）、`:94`（`flankRoute`）无专门测试用例（D1）。
- **边界外**：`TerrainMapTest.kt:72` `越界单元按不可通行处理` 断言 `Cell(-1,0)` / `Cell(32,0)` `passable` 为 false；`octile` 按设计为纯几何函数不做 `inBounds` 钳制，越界过滤由 `neighbors8`（`TerrainMap.kt:137-145`）与 `nearestPassable` 的 `inBounds` 检查负责；无直接向 `octile` 喂越界格的断言，属设计内部分层。
- **不可通行**：`PathfinderTest.kt:48-57` 水带绕行全程可通行；`:146-153` 深水目标落点钳制到可通行格；用例 10 平场 sanity 不触地形分支。
- **平局**：全 crossing 测试目录 grep `平局 / 同代价 / tie / 相等代价` 零命中；A* 开放队列 `compareBy { it.f }`（`Pathfinder.kt:134`）平局按入队顺序出队，本单未改比较器，但该行为无测试锁定（D1）。

断言独立性观察：用例 2 / 3 / 4 的期望值由同一常量 `OCTILE_DIAGONAL_FACTOR` 推导，常量若写错这三条会同步通过；独立锚点为用例 1 轴向 3.0、用例 3 区间 3.8..3.9、用例 6 取整整数 1 / 4 / 3 与半格误差界、用例 8 / 9 半径界与曼哈顿反例。

### 判据 6 · B8R1 / B8R2 未破 —— PASS

- R1 半径表单源：`model/CampaignModels.kt:200-203` 唯一字面量表 `CAVALRY -> 6 / ELITE -> 5 / REGULAR -> 4 / MILITIA -> 3`；命令 `grep -rn "CAVALRY\|ELITE\|MILITIA" app/src/main/java/io/legado/app/ui/map/crossing/engine --include=*.kt` 退出码 1，engine 内无第二张半径表。装配赋值 `CampaignController.kt:225` 取 `def.type.visionRadiusCells`；R1 锁定测试 `CampaignVisionRadiusSourceTest.kt:30-33` 四兵种 6 / 5 / 4 / 3 断言在档未改。
- R2 逐敌闸门语义未动：提交内 `Pathfinder.kt:156` `val watched = enemies.any { watchedBy(it.position, next, it.radiusCells) }`；`watchedBy` `:177-180` 两段式（格距 ≤ 自身 `viewRadius` 且 `map.visible`）；`EnemySight(position, radiusCells: Int)`（`:280-283`）；控制器 `:545` 传 `EnemySight(it.position, it.visionRadiusCells)`。`38c2edc` diff 未触及闸门行、`watchedBy`、`EnemySight` 与混编用例（`PathfinderTest.kt:156-187`）。
- 意图侧半径常量值未改：`ADJACENT_CELLS=1`、`AMBUSH_ENEMY_RANGE=5`、`HOLD_HIGH_GROUND_RANGE=3`、`SEIZE_ENEMY_RANGE=5`（`IntentResolver.kt:150/153/159/165`）。

### 判据 7 · 边界未越 —— PASS

- `38c2edc` 文件清单（`git show --name-only`）：
  - `app/src/main/java/io/legado/app/ui/map/crossing/engine/CampaignController.kt`
  - `app/src/main/java/io/legado/app/ui/map/crossing/engine/IntentResolver.kt`
  - `app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt`
- `3850709` 文件清单：
  - `app/src/test/java/io/legado/app/ui/map/crossing/engine/CampaignControllerTest.kt`
  - `app/src/test/java/io/legado/app/ui/map/crossing/engine/GridMetricTest.kt`（新增）
- 越界目录核查：`git diff --name-only 38c2edc~1 3850709 | grep -vE "crossing/engine/(Pathfinder|CampaignController|IntentResolver)\.kt|crossing/engine/(GridMetricTest|CampaignControllerTest)\.kt"` 退出码 1，`ui/**`（除 engine）、`help/**`、`data/**`、`res/**`、`model/**`、`terrain/**`、`shell/**` 在两提交内零 diff。
- diffstat：5 文件，+171 / -36；main 三文件 +41 / -36，测试两文件 +130。
- 环境说明：工作树另有未提交的 B8R4 / B8R5 改动（见首节漂移提示），不属于这两个提交，不计入本判据；该漂移已核：`model/CampaignModels.kt` 仅新增 `routeDegraded` 字段，半径表 6 / 5 / 4 / 3 未动。

### 判据 8 · 数值自证 —— PASS

手算公式：`octile = max(dx,dy) + (1.41421356 − 1) · min(dx,dy)`；`octileCells = roundToInt(octile)`。计算器复核值：

- 正交组 `(3,0)`：max=3、min=0 → 3.0。对照用例 1（`GridMetricTest.kt:33-34`）断言 3.0，一致。
- 斜向混合组 `(3,2)`：`3 + 0.41421356 × 2 = 3.82842712`。对照用例 3（`:45-46`）公式值与区间 `3.8..3.9`、用例 8（`:94`）`≤ 4.0`、取整 `round(3.82842712)=4`（`:71`），逐一相符；旧曼哈顿 `3+2=5 > 4`，反例可区分。
- 对角组 `(2,2)`：`2 × 1.41421356 = 2.82842712`。对照用例 4（`:51`）、用例 9（`:102`）`≤ 3.0`、取整 `round(2.82842712)=3`（`:72`），逐一相符；旧曼哈顿 `2+2=4 > 3`。
- 附加夹具 `(5,5)`：`5 × 1.41421356 = 7.0710678`，取整 7，误差 `|7.0710678 − 7| = 0.071 ≤ 0.5`，对应用例 6 误差界断言。
- 控制器留档用例：`(8,8)→(11,10)` 即 `(3,2)`，octile 3.82842712 ≤ 常规兵半径 4，平场通视恒真，首回合目睹 `reportRadiusCells = 0`（`recordRadius` 龄期 `age ≤ 0` 直接返回 0，`CampaignController.kt:830`）；旧曼哈顿 5 > 4 不会落报告。
- 期望非按实现反推：轴向 3.0、区间 3.8..3.9、取整整数、半径界 4 / 3 与曼哈顿反例均为独立几何锚点；仅对角单步 / 两步 / 横三纵二三条公式值与共享常数耦合（见判据 5 观察项）。

---

## 三、被验报告自述回源核对

- 报告第七节 grep 回证在提交 blob 内复跑通过：公式唯一性、octile / cellDist 引用点、旧常数残留、R1 半径表、测试计数（GridMetricTest 10 / PathfinderTest 14 / CampaignControllerTest 29）均与自述一致。
- 报告第一节穷举清单与本次独立穷举结果一致：1.1 两处曼哈顿副本已删；1.2 寻路侧真八向三处改委托；1.3 非度量项定性（公里 hypot / LOS 采样 / 平方欧氏平局 / 四向包围）成立；1.4 反例 `(3,2)` 半径 4、`(2,2)` 半径 3 数值复核无误。
- 报告第八节未验证项如实标注：编译咬合、单测实跑、IntentResolver 专门用例缺失、山地 LOS、真机冒烟、help/sim 复核，本次验收均未能在沙箱内关闭，结论保持「待 CI」。

## 四、新发现缺陷 / 判低项

- **D1（测试强度，非代码缺陷）**：同代价多最优路径的平局出队顺序无任何测试断言；路径级 `from == to` 早返回（`Pathfinder.kt:31` / `:94`）无专门用例。建议后续别单补：双等代价路径 Fixture 锁定出队确定性、起终点重合直接返回起点格。
- **D2（文档笔误）**：报告第四节称对角系数「对真值 √2 的截断误差约 2.13e-7」；计算器复核 `√2 − 1.41421356 ≈ 2.373×10^-9`，报告量级高估约百倍。代码常量本身无误，仅报告数值描述需订正。
- **D3（覆盖缺口）**：`IntentResolver` 无专门单测，Double 化后设伏 5 / 制高点 3 / 夺隘 5 / 支援 1 各斜向分支的几何翻转仅静态推演；既有 `CampaignDiplomacyTest.kt:149-151` 布位同行轴向（octile=曼哈顿，不受影响），JSON 驱动用例待 CI 实跑观察。
- **D4（环境隔离）**：工作树叠加未提交 B8R4（`routeDegraded` 降级入账）/ B8R5（目标格视野豁免）改动；验收回源须以 `38c2edc` / `3850709` blob 为准，后续派单勿把漂移行号当作 B8R3 产物。
- **D5（既有遗留，非本单引入）**：`help/sim` 旧包欧氏 / 切比雪夫度量混用仍在；`FlankRoute.degraded / reason` 生产消费（R4）、KDoc 目标格豁免表述（R5）维持被验报告第九节交接状态，不在本单判定范围。

## 五、未验证项（沙箱静态验收关闭不了）

- 编译咬合：Double / Int 混合比较解析、`minOf` / `minByOrNull` 类型推断、顶层函数同包可见性、导入清理是否有遗漏引用，未本地编译，等云端 CI `testAppDebugUnitTest`。
- 单测实跑：GridMetricTest 10 条与控制器留档 1 条未在本地执行；`(3,2)` 平场通视首回合目睹为静态推演。
- 山地 LOS：斜向格位同时受山脊遮挡时 `watchedBy` / `map.visible` 取值未实跑观察。
- 真机 / UI：无真机冒烟；情报模糊半径、暴露结算不在本单范围。
- help/sim 旧包是否另立一单统一度量，未复核。

## 六、验收动作合规自查

- 只读代码 / 文档 / 提交元数据；未改源码、测试、文档、门禁；未 git add / commit / push；未本地编译、未跑测。
- 每条结论附文件 : 行与 grep 命令 / 输出；数值结论经计算器复核。
- 本报告为唯一写入产物，路径：`docs/验收-八向octile度量.md`。
