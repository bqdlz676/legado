> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B8R2-侧翼敌军半径.md

# 修复报告 · B8R3 名义八向实曼哈顿度量收口为真 octile

- **单目标**：crossing 战役引擎里邻居按八方向扩张，但情报侧 / 意图裁决侧的距离函数名是 `octile`、实现却是曼哈顿 `dx+dy`（即 `max+min`），斜向一格被当成两格；与侧翼寻路侧的真八向格距不一致。本单把全引擎距离度量收口到唯一一处真 octile 定义，其余位置全部委托；B8R1 半径表单源、B8R2 逐敌闸门语义一字未动。
- **写码时间**：2026-11-26（沙箱，未本地编译；编译结论：未验证，等云端 CI）。
- **锚点回源**：`docs/问题清单.md:35` B8R3 行；`docs/验收-复核-B8侧翼通视.md:105`「度量不一致（二阶，R3）」段、`:150` R3 行；`docs/验收-复核-B8侧翼通视.md:131`「`CampaignController.octile` 名实不符」段；`docs/修复-B8R2-逐敌半径.md` 第七节 R3 交接项；`docs/修复-B8R1-半径表单源.md` 第五节 R3 交接项。
- **文件域**：仅改 `engine/Pathfinder.kt`、`engine/CampaignController.kt`、`engine/IntentResolver.kt` 与对应测试；未触碰 `ui/**`、`help/**`、`data/**`、`app/src/main/res/**`、`model/**`、`terrain/**`、`shell/**`。`terrain/TerrainCost.kt` 经全量排查不含格距度量，本单未改。
- **禁项遵守**：未执行 git add / commit / push；未本地编译、未跑测试（沙箱无 gradle / JDK21 / SDK）；未把八向退回四向；未砍功能；新逻辑先接好委托再删旧私有公式，不留旧公式兼容层。

---

## 一、缺陷点穷举清单（改前回源实测，逐点行号）

穷举方法：在 `app/src/main/java/io/legado/app/ui/map/crossing` 全量 grep `octile|cellDist|manhattan|heuristic|cost|maxOf|minOf|max(|min(|abs(|col - |row - |hypot|sqrt`，再逐个判读是「栅格格距度量」还是「地理公里距离 / LOS 采样 / 步数夹紧 / 落点平局」。

### 1.1 名义八向、实际曼哈顿（本单修复）

1. `engine/CampaignController.kt:884-888`（改前）私有 `fun octile(a: Cell, b: Cell): Int`，函数体 `maxOf(dc, dr) + minOf(dc, dr)` —— 恒等于曼哈顿 `dc+dr`，名 octile 实曼哈顿。五个消费点：
   - `:472` 情报目睹闸门：`val distCells = octile(...)`，`:473` `distCells <= corps.visionRadiusCells && map.visible(...)`。
   - `:573` 遭遇战范围过滤：`octile(...) <= ENCOUNTER_RANGE`。
   - `:574` 最近敌军 `minByOrNull { octile(...) }`。
   - `:605` 溃逃落点 `maxByOrNull { octile(it, threat) }`。
   - `:682-683` 快照观察者闸门：`val d = octile(...)`，`d <= ob.visionRadiusCells && map.visible(...)`。
2. `engine/IntentResolver.kt:150-154`（改前）私有 `fun octile(a: Cell, b: Cell): Int`，函数体 `max(dc, dr) + min(dc, dr)` —— 同为曼哈顿。七个消费点：
   - `:40` 最近敌军 `minByOrNull { octile(...) }`；`:41` `nearestEnemyDist`（Int，缺省 `Int.MAX_VALUE`）。
   - `:68` 撤退补给点 `minByOrNull { octile(...) }`。
   - `:89-90` 支援相邻判定两处 `octile(...) <= ADJACENT_CELLS`。
   - `:105` 抢占关隘 `minByOrNull { octile(...) }`。
   - `:143` 溃逃方向 `enemies.minOf { octile(...) }.toDouble()`。

### 1.2 改前已是真 octile（保持，只改为委托单源，不改数值口径）

3. `engine/Pathfinder.kt:198-203`（改前）`heuristic`：`max(dc,dr) + (DIAGONAL_FACTOR - 1.0) * min(dc,dr)`，真八向；`DIAGONAL_FACTOR = 1.41421356`（`:274`）。
4. `engine/Pathfinder.kt:248-252`（改前）`cellDist`：同式真八向，侧翼闸门 `watchedBy`（`:176`）与直线兜底 `straightPath`（`:240`）共用。
5. `engine/Pathfinder.kt:187-190`（改前）`stepCost`：对角步 `base * DIAGONAL_FACTOR`，横纵 `base * 1.0`，移动代价口径已是 √2 对角；本单只把常数引用换成单源符号。

### 1.3 排查后判定「非格距度量」，本单不动（附理由）

6. `terrain/TerrainMap.kt:112` `visible`：`hypot(dc, dr)` 只用于算 LOS 采样点数 `samples`，不是距离闸门；山脊遮挡判定与格距口径无关。
7. `engine/TriggerEngine.kt:79-81`、`engine/VictoryChecker.kt:153-155`、`engine/CampaignController.kt:879-881`：`hypot(dLat, dLng)` 是经纬度公里距离（触发半径 / 胜利抵达半径），单位是公里不是格，与栅格八向度量无关。
8. `engine/Pathfinder.kt:211` `nearestPassable`：`dc*dc + dr*dr` 平方欧氏距离，只在「目标格不可通行」时做周边落点平局挑选，不是移动代价、不是视野闸门。
9. `engine/IntentResolver.kt:117-132` `surrounded`：四向格存在性枚举（拓扑包围判定），不是距离度量；不按八向改。
10. `help/sim/Pathfinder.kt:88/120/162`、`help/sim/IntentResolver.kt:174/179/205/212/223`、`help/sim/SimEngine.kt:209/225/246/305/448/465/474`：另一套静态旧模拟包，不同包、不同签名、与 crossing 引擎无继承 / 调用关系（B8R1 报告第五节已登记同口径结论）；本单文件域不含 `help/**`，留跨模块交接。

### 1.4 反例（验收-复核-B8:105 原文锁定）

偏移 `(3,2)`、半径 4：真 octile = `3 + (√2−1)·2 ≈ 3.828`，应判半径内可见；旧曼哈顿 = `3+2 = 5 > 4`，误判半径外不可见。另一组 `(2,2)`、半径 3：octile ≈ 2.828 在内，曼哈顿 4 在外。

## 二、修法（after，文件:行）

### 2.1 单源定义：`engine/Pathfinder.kt:295-320`

- 顶层常量 `OCTILE_DIAGONAL_FACTOR: Double = 1.41421356`（根号二近似），全引擎唯一一份对角步长系数；移动代价与 octile 度量共用，不得另立副本。
- 顶层纯函数 `fun octile(a: Cell, b: Cell): Double`：`max(dc,dr) + (OCTILE_DIAGONAL_FACTOR − 1.0) · min(dc,dr)`。纯几何函数，不判地形可通行、不做越界钳制；越界由调用方 `neighbors8` / `inBounds` 闸门负责。
- 顶层纯函数 `fun octileCells(a: Cell, b: Cell): Int = octile(a, b).roundToInt()`：整数近似出口，仅供必须落 Int 的存档字段存储；闸门 / 最近目标比较禁止先取整再比半径。
- 单源理由：`Cell` 是 model 层纯数据，几何度量被 `Pathfinder` / `CampaignController` / `IntentResolver` 三个 engine 件共同消费；函数落在 engine 包顶层，单向向下依赖 model，不反转依赖方向，也不把几何口径塞进 model 数据类。

### 2.2 `engine/Pathfinder.kt` 委托改造

- `heuristic`（:200）：改为 `= octile(a, b) * MIN_TERRAIN_FACTOR`，删掉函数体内重复的 `dc/dr/max/min` 公式。
- `cellDist`（:245）：改为 `= octile(a, b)`，保留公共签名（既有测试与 `watchedBy` 调用不动）。
- `stepCost`（:192）：对角系数引用由旧 `DIAGONAL_FACTOR` 改为 `OCTILE_DIAGONAL_FACTOR`。
- 删除 companion 内旧常量 `DIAGONAL_FACTOR`（旧 :274）；删除私有 `abs` 辅助函数（公式移走后无引用）；`heuristic` / `cellDist` 不再直接 `import kotlin.math.max/min`，math 导入收敛到顶层度量函数一处。
- KDoc（:76-78）：把「与情报口径一致」措辞补足为「B8R3 起两侧距离度量同为顶层单源 `octile`」；`cellDist` 注释标明委托单源。

### 2.3 `engine/CampaignController.kt`

- 删除私有曼哈顿 `octile`（旧 :884-888），原位留注释指向顶层单源；五个调用点（:472 / :573 / :574 / :605 / :682）直接解析同包顶层 `octile`，返回值由 Int 变为 Double。
- `:481` 存档字段 `seenDistCells`（Int）改存 `octileCells(corps.position, enemy.position)`；闸门 `:473` 仍用 Double 真值 `distCells` 与 `visionRadiusCells` 比较，不取整。
- 未改：`:246` 播种 `seenDistCells = enemy.type.visionRadiusCells`、`:625` 接触刷新置 0、`:758` 存档恢复置 0、`:831` 距离项 `seenDistCells / 2` 口径；半径取值仍走 `type.visionRadiusCells`（R1 单源未拆散）。

### 2.4 `engine/IntentResolver.kt`

- 删除私有曼哈顿 `octile`（旧 :150-154），原位留注释；七个调用点解析顶层 `octile`。
- `:38` `nearestEnemyDist` 类型由 `Int`（缺省 `Int.MAX_VALUE`）改为 `Double`（缺省 `Double.MAX_VALUE`）；`:140` 删掉多余的 `.toDouble()`。
- 清理不再使用的 `kotlin.math.abs/max/min` 导入。
- 未改：`surrounded` 四向拓扑包围判定、各距离半径常量（`ADJACENT_CELLS=1` / `AMBUSH_ENEMY_RANGE=5` / `HOLD_HIGH_GROUND_RANGE=3` / `SEIZE_ENEMY_RANGE=5`）数值。

### 2.5 测试

- 新增 `engine/GridMetricTest.kt`（10 条，纯 JVM）：轴向格距、对角一格等于根号二系数、横三纵二公式值、对角两步两倍根号二、重合为零且对称、`octileCells` 取整误差上界、`cellDist` 与顶层 `octile` 逐值相等、两组曼哈顿反例锁定、plainMap 装配 sanity。
- `engine/CampaignControllerTest.kt` 新增 1 条：`B8R3 斜向偏移三二格在四格视野内 按八向度量目睹留档`，(8,8)→(11,10) 横三纵二，octile 3.828 ≤ 常规兵半径 4，step 后 `reportRadiusCells == 0` 且视图 `observed` 为真；旧曼哈顿 5 > 4 下该用例不会落报告，可区分新旧实现。
- 既有 `PathfinderTest.kt` 14 条语义未改；`CampaignControllerTest.kt` 既有 28 条断言未改阈值（新增后合计 29 条）。

## 三、被否决方案与反例

1. **否决：保留曼哈顿实现，只把函数改名 / 改 KDoc 措辞。** 问题清单 R3 备选路径之一是「统一度量或改 KDoc 并给两侧对照用例」。反例：(3,2) 半径 4 在旧公式下仍判 5 > 4 不可见，情报侧与侧翼侧闸门结论继续不一致；名实不符只是被注释掩盖，缺陷本体还在。本单选统一度量。
2. **否决：把八向邻居退回四向以「规避」斜向口径问题。** 硬约束禁止降级；`neighbors8` 八向扩张、`stepCost` 对角加价、侧翼绕路行为全部保留。退四向会让对角贴墙 / 包抄路径形状整体失真，属砍功能。
3. **否决：全整数代价体系（横步 10、对角步 14，即 `10·max + 4·min`）。** 既有 A* `gScore`、`TerrainCost.enterCost`、坡度 / 天候系数全是 Double；再引入一套整数标度会形成第二套代价口径，跨体系比较（如 `VISIBILITY_PENALTY` 1.8 倍加价）需要反复换算。近似误差备查：14/10 对角比 1.4，对 √2 1.41421356 单步偏差约 0.0142，长路径按对角步数线性累积；本单不采用，仅在此记录量级。
4. **否决：把度量函数放进 model 层做 `Cell` 扩展。** 本单文件域限 engine/；model 被 shell / 视图层依赖，纯几何寻路口径放 engine 顶层即可满足三处复用，不必上提 model。R1 半径表是兵种固有属性才落在 model，两者性质不同，不混放。
5. **否决：闸门比较前先 `octileCells` 取整再比半径。** 反例：对角一格 octile 1.414，取整成 1 后会并入半径 1 的相邻环，`ADJACENT_CELLS=1` 语义被改写（旧曼哈顿下对角=2 也不相邻）；取整只允许出现在 `seenDistCells` 这类 Int 存档字段，闸门必须用 Double 真值。
6. **否决：地理公里距离（`hypot(dLat,dLng)`）一并改成 octile。** 那些闸门单位是公里（触发半径、胜利抵达半径），栅格格距换算需要 `cellKm` 投影系数，混用单位会引入新错；保留原样。

## 四、整数代价近似与误差上界

- 移动代价 / 启发式 / 闸门：全程 Double，对角步长系数 `OCTILE_DIAGONAL_FACTOR = 1.41421356`，对真值 √2 的截断误差约 `2.373e-9`（复数核：|1.41421356−√2|=2.3730950538e-9，相对 1.678e-9；原稿写 2.13e-7 是笔误，已按实数核定），单步可忽略；启发式可采纳性不变（见第六节断言 6）。
- 整数出口 `octileCells`：四舍五入到整格，`|octile − octileCells| ≤ 0.5` 格。
- 下游情报距离项：`recordRadius` 取 `seenDistCells / 2`，半距项误差上界 `0.25` 格；再经 `coerceIn(0, visionRadiusCells)` 封顶，不会放大到半径之外。
- 既有 B9-10 / B9-11 夹具均为轴向布位（dc=4 或 dc=2、dr=0），octile 与曼哈顿同值，留档距离 4 / 2 不变，既有半径断言不受取整影响。

## 五、二阶 / 三阶影响推演

1. **路径与闸门形状（二阶）**：情报 / 意图侧视野环由曼哈顿菱形改为 octile 圆角方形，斜向带状格位会被正确纳入半径。直接影响：目睹判定（`:473`）、观察者标记（`:683`）、遭遇范围（`:573`）、最近敌军 / 补给 / 关隘排序（`:574` / `:65` / `:102`）、溃逃远离方向（`:605` / `:140`）、支援相邻（`:86-87`）。轴向格位结论不变；只有斜向偏移格位结论可能翻转，翻转方向是「更早纳入」，与八向移动直觉一致。
2. **与 B8R2 逐敌半径的联动（二阶）**：侧翼 `watchedBy` 走 `cellDist`（= `octile`），改前侧翼侧已是真八向；本单后情报侧同公式，「情报看见的环」与「侧翼躲开的环」几何上严格一致。逐敌闸门 `enemies.any { watchedBy(it.position, next, it.radiusCells) }` 未动；半径仍逐条取 `EnemySight.radiusCells`，混编语义不变。
3. **与 B8R1 半径单源的联动（二阶）**：半径字面量表仍只在 `model/CampaignModels.kt:200-203` 一处；engine 两侧继续引用 `type.visionRadiusCells`。本单未新增任何半径字面量，单源未被拆散（回证见第七节 7.4）。
4. **推演耗时（二阶）**：octile 公式与旧曼哈顿同为 abs + max + min + 一次乘加，不调 sqrt、不调三角函数；每次调用操作数与旧实现持平。A* 启发式调用次数不变；情报闸门 O(敌军数) / 军，Double 比较 Int 阈值仅一次装箱比较，热路径开销可忽略。未引入逐帧浮点扩容。
5. **快照 / 回放兼容（三阶）**：`BranchSnapshot` 只深拷贝 `Corps`（位置、路线等），不序列化 `IntelRecord`；恢复存档时情报表清空重建（`:749-759`），`seenDistCells` 播种为 0。`CorpsView` 不下发 `seenDistCells`。因此旧存档里若残留曼哈顿口径的目击距离，不会被读回参与结算，无需批量重算；敌方路线在未观测时已被 `blurRoute` 清空。本单不改存档格式。
6. **同代价平局（三阶）**：A* 开放队列 `compareBy { it.f }`，f 相等时按入队顺序出队；本单未改平局规则。octile 下存在多条等代价最优路径时，选出哪一条仍由队列顺序决定，属既有行为。
7. **help/sim 旧包（跨模块，三阶）**：该包内 `hypot`（欧氏）与 `max(abs,abs)`（切比雪夫）混用，与 crossing 引擎不互通；是否另立一单统一，留后续派单，本单不连带修改。

## 六、断言清单（逐条可机械判）

1. **不可通行格**：度量函数不判地形；堵死仍由 `astar` `:151` `map.passable`、`straightPath` `:235` 过滤，`TerrainCost.BLOCKED_COST` 在 `stepCost` `:191` 短路。octile 改动不允许让不可通行格进入路径。
2. **边界外格**：`octile` 不做 `inBounds` 校验（纯几何）；所有邻居枚举走 `neighbors8`（自带越界过滤，`TerrainMap.kt:137-145`），落点挑选走 `nearestPassable:210` `inBounds` 检查；`IntentResolver` 位置取自 `Corps.position`，装配时已在栅格内。
3. **起点 = 终点**：`octile(a, a) = 0.0`；`find:30` 与 `flankRoute:94` 均有 `from == to` 早返回；`GridMetricTest` 重合用例断言 0.0。
4. **同代价多最优路径**：平局出队顺序不变；本单只改边权 / 距离度量，不改队列比较器。
5. **地形代价口径**：横纵步 `base·1.0`，对角步 `base·OCTILE_DIAGONAL_FACTOR`；`base` 仍由 `TerrainCost.enterCost` 给出，坡度 / 天候 / 疲劳链条未动。
6. **启发式可采纳性**：`h = octile · MIN_TERRAIN_FACTOR(1.0)`；任一步实际代价 `base ≥ 1.0`，横纵 ≥ 1.0、对角 ≥ √2，逐步 `g ≥ h`，A* 最优性不被破坏。
7. **闸门不取整**：所有半径 / 范围比较直接用 Double `octile`；`octileCells` 只出现在 `seenDistCells` 存档写入一处。
8. **单源**：全 crossing main 源码内，`max(dc,dr) + …·min(dc,dr)` 公式只出现一次（`Pathfinder.kt:312`）；根号二系数只声明一次（`:300`）。
9. **R1 半径单源不回退**：`6/5/4/3` 字面量仍只在 `CampaignModels.kt:200-203`；engine 无第二张半径表。
10. **既有断言保留**：`PathfinderTest` 14 条、`CampaignControllerTest` 既有 28 条阈值与夹具未改；新增用例只增不替。

## 七、回证命令与真实输出

### 7.1 距离公式唯一性（main 源码）

```text
$ grep -rn "max(dc\|maxOf(dc\|min(dc\|minOf(dc" app/src/main/java --include=*.kt
app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt:312:    return max(dc, dr) + (OCTILE_DIAGONAL_FACTOR - 1.0) * min(dc, dr)
```

说明：全 main 仅一处八向公式；旧两处曼哈顿 `maxOf+minOf` / `max+min` 已删除。

### 7.2 全部 octile / cellDist 引用点

```text
$ grep -rn "octile\|cellDist" app/src/main/java/io/legado/app/ui/map/crossing/engine/
CampaignController.kt:472:  val distCells = octile(corps.position, enemy.position)
CampaignController.kt:481:  seenDistCells = octileCells(corps.position, enemy.position)
CampaignController.kt:573:  val enemies = enemiesOf(corps).filter { octile(corps.position, it.position) <= ENCOUNTER_RANGE }
CampaignController.kt:574:  val target = enemies.minByOrNull { octile(corps.position, it.position) } ?: return
CampaignController.kt:605:  .maxByOrNull { octile(it, threat) }
CampaignController.kt:682:  val d = octile(ob.position, e.position)
CampaignController.kt:884:  // 八向格距统一走顶层单源 [octile]（B8R3）；本类不再私建距离公式。
CampaignController.kt:885:  // Int 存档字段用 [octileCells] 取整，半径闸门 / 最近目标比较直接用双精度 [octile]。
IntentResolver.kt:37:  val nearestEnemy = aliveEnemies.minByOrNull { octile(corps.position, it.position) }
IntentResolver.kt:38:  val nearestEnemyDist = nearestEnemy?.let { octile(corps.position, it.position) } ?: Double.MAX_VALUE
IntentResolver.kt:65:  .minByOrNull { octile(corps.position, it) }
IntentResolver.kt:86:  ally.combatReady() && octile(corps.position, ally.position) <= ADJACENT_CELLS &&
IntentResolver.kt:87:  aliveEnemies.any { octile(ally.position, it.position) <= ADJACENT_CELLS }
IntentResolver.kt:102: .minByOrNull { octile(corps.position, it) }
IntentResolver.kt:140: else enemies.minOf { octile(c, it.position) }
IntentResolver.kt:147: // 八向格距统一走顶层单源 [octile]（B8R3）；本裁决器不再私建曼哈顿公式。
Pathfinder.kt:178: if (cellDist(enemy, cell) > viewRadius) return false
Pathfinder.kt:200: private fun heuristic(a: Cell, b: Cell): Double = octile(a, b) * MIN_TERRAIN_FACTOR
Pathfinder.kt:237: cur = candidates.minByOrNull { cellDist(it, to) } ?: break
Pathfinder.kt:245: fun cellDist(a: Cell, b: Cell): Double = octile(a, b)
Pathfinder.kt:300: const val OCTILE_DIAGONAL_FACTOR: Double = 1.41421356
Pathfinder.kt:309: fun octile(a: Cell, b: Cell): Double {
Pathfinder.kt:320: fun octileCells(a: Cell, b: Cell): Int = octile(a, b).roundToInt()
```

### 7.3 旧常数 / 旧私有公式残留排查

```text
$ grep -rn "DIAGONAL_FACTOR" app/src --include=*.kt
Pathfinder.kt:192:  var cost = base * (if (diagonal) OCTILE_DIAGONAL_FACTOR else 1.0)
Pathfinder.kt:300: const val OCTILE_DIAGONAL_FACTOR: Double = 1.41421356
Pathfinder.kt:312:  return max(dc, dr) + (OCTILE_DIAGONAL_FACTOR - 1.0) * min(dc, dr)
GridMetricTest.kt:39:  assertEquals(OCTILE_DIAGONAL_FACTOR, octile(Cell(0, 0), Cell(1, 1)), 1e-9)
GridMetricTest.kt:44:  val expect = 3.0 + (OCTILE_DIAGONAL_FACTOR - 1.0) * 2.0
GridMetricTest.kt:51:  assertEquals(2.0 * OCTILE_DIAGONAL_FACTOR, octile(Cell(0, 0), Cell(2, 2)), 1e-9)
```

说明：旧名 `DIAGONAL_FACTOR` 已无残留；命中均为新名 `OCTILE_DIAGONAL_FACTOR`。

```text
$ grep -rn "private fun octile\|fun octile(a: io" app/src/main/java --include=*.kt
exit=1（无匹配：两处私有曼哈顿 octile 已删）
```

### 7.4 R1 半径表单源未被拆散

```text
$ grep -rn "CAVALRY -> 6\|ELITE -> 5\|REGULAR -> 4\|MILITIA -> 3" app/src --include=*.kt
model/CampaignModels.kt:200: ArmyType.CAVALRY -> 6
model/CampaignModels.kt:201: ArmyType.ELITE -> 5
model/CampaignModels.kt:202: ArmyType.REGULAR -> 4
model/CampaignModels.kt:203: ArmyType.MILITIA -> 3
```

### 7.5 词法配平（原始字符计数）

```text
Pathfinder.kt          大括号 32/32  圆括号 143/143  方括号 39/39  块注释 19/19
CampaignController.kt  大括号 213/213 圆括号 459/459  方括号 90/90  块注释 75/75
IntentResolver.kt      大括号 34/34  圆括号 61/61   方括号 2/2   块注释 10/10
```

### 7.6 测试计数与命名合法性

```text
$ grep -c "@Test" GridMetricTest.kt PathfinderTest.kt CampaignControllerTest.kt
GridMetricTest.kt:10
PathfinderTest.kt:14
CampaignControllerTest.kt:29
```

新增 11 条（GridMetricTest 10 + CampaignControllerTest 1）；测试反引号名逐项扫描，不含 `. : [ ] / ; < >` ASCII 非法字符；根号二在命名中写「根号二」。

### 7.7 文件改动清单（git status 只读，未 add / commit / push）

```text
M  app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt
M  app/src/main/java/io/legado/app/ui/map/crossing/engine/CampaignController.kt
M  app/src/main/java/io/legado/app/ui/map/crossing/engine/IntentResolver.kt
M  app/src/test/java/io/legado/app/ui/map/crossing/engine/CampaignControllerTest.kt
?? app/src/test/java/io/legado/app/ui/map/crossing/engine/GridMetricTest.kt
```

说明：`git status` 中其余 `help/artifact`、`help/bubble`、`data_scripts` 等改动为会话内既有他人改动，本单未触碰。

## 八、未验证项（沙箱禁编译 / 禁跑测）

- 编译咬合：沙箱无 gradle / JDK21 / SDK，未执行 `./gradlew` / `kotlinc`；Double 与 Int 阈值的混合比较解析、`minByOrNull` / `minOf` 类型推断、顶层函数同包可见性、导入清理是否有遗漏引用，均**未验证，等云端 CI**。
- 单测实跑：GridMetricTest 10 条与新增控制器用例未在本地执行；(3,2) 半径 4 目睹用例的几何结论（平场通视恒真、首回合落报告）为静态推演，需云端 `testAppDebugUnitTest` 复核。
- 意图裁决侧：未新增 `IntentResolver` 专门用例；`:38` Double 化后的各半径分支（设伏 5 / 制高点 3 / 夺隘 5 / 支援 1）几何翻转情况仅静态推演，需 CI 既有裁决相关测试 + 后续补测覆盖。
- 山地 LOS：斜向格位同时受山脊遮挡时 `watchedBy` / `map.visible` 的取值未实跑观察。
- 真机 / UI：无真机冒烟；情报模糊半径、暴露结算不在本单范围。
- help/sim 旧包：未复核其欧氏 / 切比雪夫混用是否需要另立一单。

## 九、交接项（别单，本单未修）

- **R4**：`FlankRoute.degraded / reason` 仍无生产消费方，被迫暴露路线不上送情报 / `corps.exposed` / 士气。
- **R5**：KDoc 仍称目标格不受视野剪枝，实现只豁免起点；第三级折线兜底无专门用例。
- **help/sim**：旧静态 `Pathfinder` / `IntentResolver` / `SimEngine` 距离口径混用，建议另立一单按 crossing octile 口径复核。
- **整数代价方案**：若后续性能侧要求全整数 A*，10/14 整数口径与 Double 地形代价体系的换算关系需另单评估，本单仅记录误差量级。

## 十、DoD 逐条自检

1. 全仓穷举：栅格格距度量点已逐类列全（第一节 1.1-1.4），非度量项（公里 hypot / LOS 采样 / 平方欧氏平局 / 四向包围）均附不动理由。✓
2. octile 公式落地：`max(dx,dy) + (√2−1)·min(dx,dy)`，对角步 √2；常数 1.41421356 单源；整数近似 `octileCells` 误差上界 0.5 格、半距项 0.25 格。✓
3. 单源：公式与根号二系数各只一处声明，Pathfinder / Controller / IntentResolver 全部委托；R1 半径表仍只在 model 一处。✓
4. 边界断言：不可通行、越界、起点终点重合、同代价平局、可采纳性逐条列入第六节。✓
5. 二阶推演：路径形状、B8R2 联动、B8R1 单源、耗时、快照回放、平局、help/sim 跨包均在第五节。✓
6. 文件域：仅 engine 三件主源码 + 两件测试；未碰 ui / help / data / res / model / terrain / shell。✓
7. 回证：第七节每条 grep 命令附真实输出；词法配平与测试计数已核。✓
8. 未验证项如实标注，未本地编译、未跑测、未 git 写操作。✓
