# 修复-Pathfinder 平局不确定性（Q-216：CI shard 1/4 红 PathfinderBoundaryTest.kt:104）

## 一、任务与范围

- 派单编号：Q-216（`docs/派单队列.md:238`、`docs/派单台账.md:243`）。
- CI 事实：Verify run `45783964800`，commit `b744fb42e9a643dc89fed8f4f6a8b9235b275a9e`（短 `b744fb4`）；shard 1/4 failure：`PathfinderBoundaryTest > 双等代价绕行路径 两次裁决逐格一致锁定确定性`，`java.lang.AssertionError at PathfinderBoundaryTest.kt:104`；shard 2/4 自 08:37Z 起挂死超 90 分钟；shard 3/4、4/4 success。失败日志落 `/tmp/job1.log:364-365`，`883 tests completed, 1 failed`。
- 锚点卡：docs/锚点/Q216-Pathfinder平局确定性.md
- 禁项（本单逐条遵守）：禁 `git add/commit/push/revert/checkout`；禁编译 / gradle / kotlinc；禁在仓库内新建测试文件；负样本只许落 `/tmp`。
- 取证范围：`app/src/test/java/io/legado/app/ui/map/crossing/engine/PathfinderBoundaryTest.kt`、`app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt`、`app/src/main/java/io/legado/app/ui/map/crossing/terrain/TerrainMap.kt`、`terrain/TerrainCost.kt`、`terrain/HeightRaster.kt`、`terrain/GeoProjection.kt`、`model/CampaignModels.kt`。

## 二、测试 Fixture 与断言链（逐字，文件:行）

- Fixture `PathfinderBoundaryTest.kt:39-44` `blockedMiddleMap()`：`IntArray(32*32){128}`，唯 `data[10*32+6]=0`；`HeightRaster.create(32,32,data,degradedHint=false)`；`TerrainMap(raster, projection, emptyList())`。
- 用例 `:94-99`：起点 `Cell(5,10)`、终点 `Cell(7,10)`、被堵 `Cell(6,10)`；两次独立 `Pathfinder(blockedMiddleMap(),"clear").find(start,target,ArmyType.REGULAR)` 存入 `first` / `second`。
- 断言链（现行工作区与 `b744fb4` blob 逐字一致，`git diff b744fb4 HEAD` 两文件均为空）：
  - `:101` `assertTrue("等代价绕行路径必须非空", first.isNotEmpty())`
  - `:102` `assertEquals("同一输入两次裁决应逐格一致（平局稳定）", first, second)`
  - `:103` `assertEquals("路径首格为出发格", start, first.first())`
  - `:104` `assertEquals("路径末格为目标格", target, first.last())`
  - `:105` `assertTrue("路径不得踏中被堵水面格", blocked !in first)`
  - `:108` `assertTrue("绕行中间格应为上绕格或下绕格", middle == Cell(6, 9) || middle == Cell(6, 11))`
- 关键事实：CI 栈点名的 `:104` 在当前源码里是**末格断言**（`first.last()` 必须等于 `target`），不是 `:102` 的两次相等断言。派单书把 `:104` 描述成"两次裁决逐格一致"，与现行源码行号对不上；本单以工程现实（源码行号 + 静态复现）为准。

## 三、根因分析（推理链 + 平局假设证伪）

### 3.1 派单假设：open 队列平局抖动（已被取证否定）

- `Pathfinder.kt:155` `val open = PriorityQueue<Node>(compareBy({ it.f }, { it.key }))`：开放队列比较器**已带次级键** `it.key`（节点编码 `row*n+col`，`:282`）。该次级键由提交 `25df6ae` 补入，且 `25df6ae` 是 `b744fb4` 的祖先（`git merge-base --is-ancestor` = YES），即 CI 构建的 blob 里次级键已存在。
- 已逐项排除的不确定源：`cameFrom`/`gScore`/`closed` 均为每次 `astar` 新建局部 `HashMap`/`HashSet`（`:150`、`:151`、`:157`），只做成员判定、不遍历；`neighbors8` 双固定循环产出 `ArrayList`（`TerrainMap.kt:137-145`）；`TerrainCost`/`GeoProjection`/`HeightRaster` 为纯计算；`Pathfinder` 内无 `Random`、无 `currentTimeMillis`、无 companion 缓存、无对象池；`enemySet` 仅成员判定（`:174`）。
- 按同 Fixture 在 Python 里逐行复刻 A*（固定邻接序 + `(f,key)` 全序堆）：两次运行路径完全相同，`first == second` 成立。**平局抖动不是本次红的触发点。**

### 3.2 真实根因：单格水洞被坡度闸门算成 48.35° 假悬崖，贴水干格被误堵，落点被 `nearestPassable` 钳离原目标

- 高程换算 `HeightRaster.kt:49` `metersFor(value) = (value.coerceIn(0,255)/255.0)*7000.0`：128 → `3513.725m`；0 → `0.0m`。
- 单格边长 `GeoProjection.kt:26` `cellSizeMeters = radiusKm*1000/(gridN/2) = 50*1000/16 = 3125.0m`。
- 坡度 `TerrainMap.kt:46-56`（改前）：对 `neighbors4` 全部邻格取最大高差反正切。贴水干格与水洞的高差 `de = 3513.725m`，坡度 `atan(3513.725/3125.0) = 48.35°`，超过 REGULAR 坡度上限 `45.0°`（`TerrainMap.kt:86-91`，`else -> 45.0`）。
- 复算（Python 实算，非估）：
  - `(5,10)` 起点、`(7,10)` 终点、`(6,9)` 上绕、`(6,11)` 下绕：`slope=48.3°`，`passable=False`。
  - 仅离水洞一格的 `(5,9)/(7,9)/(5,11)/(7,11)/(6,8)/(6,12)`：`slope=0.0`，`passable=True`。
- 链路：`find`（`Pathfinder.kt:32`）先 `nearestPassable(to,type)`；终点 `(7,10)` 被坡度闸门判不可通行，`nearestPassable`（`Pathfinder.kt:228-245`）把落点钳到半径 1 内首个可通行格 `(7,9)`；`astar` 只能走到 `(7,9)` 收尾，返回路径末格 `(7,9) ≠ (7,10)`，于是 `:104` 末格断言抛 `AssertionError`。
- Python 复刻整条 `find` 链（含 `passable`/`nearestPassable`/`astar`/`straightPath`）：
  - 返回 `first = [(5,10),(5,9),(6,8),(7,9)]`，`second` 相同；`first==second=True`，`first.first()==(5,10)=True`，`first.last()==(7,10)=False`，`nearestPassable((7,10))=(7,9)`。
  - 即 `:102` 两次相等通过、`:103` 首格通过、`:104` 末格失败——与 CI 栈行号 `:104` 吻合。
- 语义判断：水面高程被压到 0m，贴水干格与水面的高差是**岸线落差**，不是陆坡陡壁；把水面邻格计入陆坡比较，会把单格水洞周边干格误判成悬崖。渡口检测需要全邻域口径，故该口径保留给 `detectFords`，通行闸门改用陆地口径。

## 四、改动清单（文件:行 + before/after + grep 回证）

### 4.1 改动文件：`app/src/main/java/io/legado/app/ui/map/crossing/terrain/TerrainMap.kt`

> 偏离说明：派单记录的文件域是 `engine/Pathfinder.kt`；但静态取证表明红点在地形坡度闸门（`terrain/TerrainMap.kt`），Pathfinder 平局键已具备全序、无需改动。修复落在真实根因文件，已在 §八 交接节登记。

- before（改前 `TerrainMap.kt:46-56`，单一 `slopeDeg` 对全邻域取坡度）：
  - `fun slopeDeg(cell: Cell): Double {`
  - `for (n in neighbors4(cell)) {`
  - `val de = kotlin.math.abs(elevationMeters(n) - e0)`
  - `val slope = Math.toDegrees(atan(de / projection.cellSizeMeters.coerceAtLeast(1.0)))`
  - `maxSlope = max(maxSlope, slope)`
- after（现 `:46-67`）：抽出 `slopeInternal(cell, skipWater)`；`slopeDeg` 保持全邻域口径（渡口检测用）；新增 `landSlopeDeg` 跳过水面邻格：
  - `:46` `fun slopeDeg(cell: Cell): Double = slopeInternal(cell, skipWater = false)`
  - `:54` `private fun landSlopeDeg(cell: Cell): Double = slopeInternal(cell, skipWater = true)`
  - `:56` `private fun slopeInternal(cell: Cell, skipWater: Boolean): Double {`
  - `:61` `if (skipWater && isWater(n)) continue`
- 通行闸门改口径（现 `:96`）：
  - before `:85` `val slope = slopeDeg(cell)`
  - after `:96` `val slope = landSlopeDeg(cell)`
- 未改动：`detectFords` 仍调 `slopeDeg`（现 `:169`）；`TerrainCost.enterCost` 代价侧仍调 `map.slopeDeg`（`TerrainCost.kt:76`），只影响代价惩罚、不影响通行放行；`Pathfinder.kt` 一行未改。

### 4.2 关键 diff（5~10 行）

```diff
-    fun slopeDeg(cell: Cell): Double {
+    fun slopeDeg(cell: Cell): Double = slopeInternal(cell, skipWater = false)
+    private fun landSlopeDeg(cell: Cell): Double = slopeInternal(cell, skipWater = true)
+    private fun slopeInternal(cell: Cell, skipWater: Boolean): Double {
         if (!inBounds(cell)) return 0.0
         val e0 = elevationMeters(cell)
         var maxSlope = 0.0
         for (n in neighbors4(cell)) {
+            if (skipWater && isWater(n)) continue
             val de = kotlin.math.abs(elevationMeters(n) - e0)
             val slope = Math.toDegrees(atan(de / projection.cellSizeMeters.coerceAtLeast(1.0)))
             maxSlope = max(maxSlope, slope)
         }
         return maxSlope
     }
...
-        val slope = slopeDeg(cell)
+        val slope = landSlopeDeg(cell)
```

### 4.3 grep 回证（改动真实写入、旧口径清零）

```
$ grep -n "slopeDeg\|landSlopeDeg\|slopeInternal\|isWater(n)\|val slope" app/src/main/java/io/legado/app/ui/map/crossing/terrain/TerrainMap.kt
46:    fun slopeDeg(cell: Cell): Double = slopeInternal(cell, skipWater = false)
52:     * 双等代价绕行即被这道假悬崖切断）。渡口检测 [detectFords] 仍走全邻域口径 [slopeDeg]，不受影响。
54:    private fun landSlopeDeg(cell: Cell): Double = slopeInternal(cell, skipWater = true)
56:    private fun slopeInternal(cell: Cell, skipWater: Boolean): Double {
61:            if (skipWater && isWater(n)) continue
63:            val slope = Math.toDegrees(atan(de / projection.cellSizeMeters.coerceAtLeast(1.0)))
64:            maxSlope = max(maxSlope, slope)
96:        val slope = landSlopeDeg(cell)
97:        val slopeLimit = when (type) {
169:                if (wet >= 2 && dry >= 2 && slopeDeg(cell) <= FORD_SLOPE_DEG) {
```

- 语法预检：`python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/map/crossing/terrain/TerrainMap.kt` → `KOTLIN-LINT: PASS（1 个文件）`，退出码 0。

### 4.4 修复后复刻（Python 同 Fixture，陆地坡度口径）

- `(5,10)/(7,10)/(6,9)/(6,11)` 全部 `landSlope=0.0`、`passable=True`。
- 路径 `[(5,10),(6,9),(7,10)]`，两次一致，`last=(7,10)` 命中目标；上绕中格 `(6,9)`（编码 294）< 下绕 `(6,11)`（编码 358），次级键定序选上绕，确定。

## 五、二阶三阶影响 / 反例边界 / 认知成本

- 二阶影响：贴水岸线干格不再被坡度闸门误堵；代价侧 `TerrainCost.enterCost` 仍按全邻域坡度取惩罚系数，岸线格可能仍吃到封顶 2.0 惩罚，但不影响放行。渡口检测 `detectFords` 口径未变，不会新增/减少渡口判定。
- 三阶影响：大范围水带边缘（`blockedBandMap` Fixture）的干边格现在可通行，`PathfinderTest` 水带绕行用例的可通行邻域变大；断言只要求"路径格可通行、不踏非渡口水面"，口径不变，不构成反向破坏。
- 反例与边界：
  1. 真实临海悬崖：陆坡本应陡峻，跳开水面邻格后只按陆内邻格算坡。当前坡度阈值本就是粗口径（≤45°/30°/15°），未建模临海崖壁；若后续要区分"岸线陡坡"，需另单引入岸线剖面判据，本单不扩战场。
  2. 干格被水格三面包围（孤洲）：陆内邻格高差为 0 → 坡度 0 可通行，符合"洲上可走"直觉。
  3. 水洞内嵌套高程突变：`isWater` 仍在 `passable` 第一道闸（`:95`）拦截，水面本身不会被放行。
- 认知成本：新增 `landSlopeDeg` / `slopeInternal` 两层命名，注释写明两口径各自用途；`slopeDeg` 对外签名不变，调用方无需感知。

## 六、shard 2/4 挂死 Top5 可疑清单（静态取证，未在 CI 复跑）

> 判据：`while(true)` / `runBlocking` / `await` / `Thread.sleep` / 无界递归 / 网络调用。清单来自 `/tmp/cl/shard-2.txt`（160 类）；沙箱无 JDK、禁编译，以下为嫌疑排序与证据，最终挂死类须在 CI 单独重跑确认。

1. `io.legado.app.model.jsSource.JsSourceConfigTest` — `while (true)` 顶层脚本套 `withTimeout(500)`；若 Rhino 执行不响应协程取消，无限循环会逃出超时把 `runBlocking` 永久挂住。证据：`JsSourceConfigTest.kt:474` `runBlocking {`；`:475` `withTimeout(500) {`；`:476` `JsSourceConfig.extract("while (true) {}", currentCoroutineContext())`。
2. `io.legado.app.help.http.OkHttpRetryTest` — `runBlocking` 内走生产重试循环 `while(true)` + `suspendCancellableCoroutine` 回调恢复；回调若不恢复协程，`await` 永不返回。证据：`OkHttpRetryTest.kt:22` `fun redirectResponseIsReturnedWithoutRetry() = runBlocking {`；`:29` `client.newCallResponse(retry = 2) { url("https://example.com") }`；生产侧 `OkHttpUtils.kt:36` `while (true) {`；`:37` `val response = newCall(requestBuilder.build()).await()`。
3. `io.legado.app.ui.main.search.SearchCommandGateTest` — 执行器线程内 `CountDownLatch.await`；闸门回调若未触发，等待线程滞留。证据：`SearchCommandGateTest.kt:30` `releaseAction.await(5, TimeUnit.SECONDS)`；`:33` `assertTrue(actionStarted.await(5, TimeUnit.SECONDS))`；`:39` `assertTrue(stopStarted.await(5, TimeUnit.SECONDS))`。
4. `io.legado.app.RhinoScriptErrorContextTest` — `runBlocking` 调 `RhinoScriptEngine.evalSuspend`，脚本引擎在阻塞线程上求值，异常路径外的求值卡住会拖住 `runBlocking`。证据：`RhinoScriptErrorContextTest.kt:35` `fun evalSuspendErrorIncludesSourceContext() = runBlocking {`；`:40` `RhinoScriptEngine.evalSuspend(script, ScriptBindings())`。
5. `io.legado.app.help.source.RssSortUrlsTest` — 多处 `runBlocking` 跑排序 URL 脚本解析；脚本解析器内部若挂起，`runBlocking` 同步等待。证据：`RssSortUrlsTest.kt:33` `fun \`valid wrapped script result is parsed\`() = runBlocking {`；`:36` `resolveRssSortUrls(`。

- 已排除：shard-2 清单内未发现 `Thread.sleep`、真实 `openConnection`/`RealCall.execute()` 联网、无界递归；`repeat(n)` 均为有界循环。

## 七、未验证项（只能在云端 CI 验证，沙箱不编译）

- 本单沙箱无 JDK 编译链，未跑 `:app:testAppDebugUnitTest`；`PathfinderBoundaryTest` 三条相关断言是否转绿、`PathfinderTest` 水带/渡口用例是否仍通过，均以云端 CI Verify 为准。
- shard 2 挂死的真实类未在 CI 单独重跑定位；Top5 为静态嫌疑排序，不构成结论。
- 修复后路径选上绕 `(6,9)` 是次级键定序的确定性结果；测试不锁定上下绕，符合 `:106-108` 结构断言。

## 八、跨模块交接

- 派单台账 `docs/派单台账.md:243` 记录的文件域为 `engine/Pathfinder.kt`、根因描述为"平局抖动"；实际根因在 `terrain/TerrainMap.kt` 坡度口径。若后续要把岸线陡坡纳入建模，另单在地形层处理，本单不留兼容层、不扩战场。
- `Pathfinder.kt:187` 等代价父节点保留先到父（严格 `<` 改写）：在 `(f,key)` 全序堆下到达顺序确定，本次未改；若未来邻接序或代价口径再变，可在此处补等 g 按父节点编码定序的次级规则。

## 守则回执：

- 指纹：docs/机制-总控必读.md=f103a149eb76；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「没贴 grep 的改动视为未改」（docs/机制-子代理必读.md:18）
- 逐字引用 2：「行数、计数、字节、sha 不许估」（docs/机制-子代理必读.md:19）
- 逐字引用 3：「没编译、没跑测试、没真机 —— 一律显式写出来」（docs/机制-子代理必读.md:20）
- 适用条款：证据链 grep 回证（子代理必读 §1.2）；数字实算坡度/高差/格边长（§1.3）；未验证项显式声明（§1.4）；最小改动不重构、不扩战场（§3 不许做的事）；Kotlin 语法预检必跑（§7.1）。

## 新发现问题（2026-11-26 追补，F8）

1. 派单假设"比较器缺次级键"被取证否定：`Pathfinder.kt:155` 早有 `compareBy({it.f},{it.key})`；真因是坡度闸门把水洞周边干格算成 48.35° 全判不可通行 —— 已修：`TerrainMap.kt` 拆 `landSlopeDeg` 跳水面邻格。
2. 只改 `TerrainCost.kt` 惩罚口径会连带影响放行率与时序，属二阶风险 —— 判废：本次不动代价侧，只改放行闸门。
3. shard2 挂死 Top1 `JsSourceConfigTest.kt:474` 的 `while(true)` 不受 withTimeout 约束 —— 已入队：Q-220 加护栏，不改业务断言。
