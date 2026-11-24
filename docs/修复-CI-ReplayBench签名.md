# 修复-CI A12：ReplayBenchTest 尾随 lambda 错绑 elevation（具名实参 clock）

> 清单编号：**A12**（`docs/问题清单.md:30`）
> 一手证据：云端 CI run `45206888778`（commit `741b1e1`），`:app:compileAppDebugUnitTestKotlin` 失败，4 条全部落在 `ReplayBenchTest.kt`。
> 备案锚点：`docs/落文-全规格备案.md` §4.2（回放链 `ReplayDirector` 契约）、§1.1（三布尔现状）。⚠️已作废：以 F-01（`docs/口径冻结-2026-11-24-15点后.md:8`，两布尔：HEAD commit / CI 绿红）为准（2026-11-25 清理）。

## 1. 任务与范围

- **本单只做**：把 `app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt` 第 21、42 行两处
  `ReplayDirector(SeedReplay.changping) { now }`
  改为显式具名实参
  `ReplayDirector(SeedReplay.changping, clock = { now })`。
- **白名单**：仅 `app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt`。
- **禁区**：`app/src/main/**` 全部、其它任何测试文件、`docs/**`（本报告除外）、`ui/map/crossing/**`（他单未提交产物）。
- **红线执行情况**：未做任何 git 写操作（无 add/commit/push）；未跑 gradle / kotlinc（沙箱禁编译）。

## 2. 根因（已核实，未另行重构）

`app/src/main/java/io/legado/app/ui/map/replay/ReplayDirector.kt:46-56` 构造签名：

```kotlin
class ReplayDirector(
    val campaign: Campaign,
    private val clock: () -> Long = { System.currentTimeMillis() },
    val elevation: TerrainElevation = TerrainElevation.Flat,
)
```

形参顺序为 `campaign → clock: () -> Long → elevation: TerrainElevation`，其中 `TerrainElevation` 是
`(Double, Double) -> Double` 类型。Kotlin 尾随 lambda 语法在有多个函数类型形参时，**绑定到最后一个形参**；
故 `ReplayDirector(SeedReplay.changping) { now }` 的尾随 lambda 被推断为 `elevation` 的实参：

- 期望 `(Double, Double) -> Double`，实参却是 `() -> Double`（`{ now }` 只取零参，`now: Long`）；
- 返回期望 `Double`，实际 `Long`。

`clock` 形参虽有默认值，但尾随 lambda 不会越过它去绑定第二形参，这是本次 4 条编译错误的唯一根因。

### 2.1 CI 四条报错逐条销项

| # | CI 报错行 | 报错原文 | 销项方式 |
|---|---|---|---|
| 1 | `ReplayBenchTest.kt:21:57` | Argument type mismatch: actual type is '() -> Double', but '(Double, Double) -> Double' was expected. | 第 21 行改为 `clock = { now }`，lambda 显式绑定第二形参 `clock: () -> Long` |
| 2 | `ReplayBenchTest.kt:21:59` | Return type mismatch: expected 'Double', actual 'Long'. | 同上；`clock` 返回 `Long`，与 `now: Long` 一致，返回类型错配消失 |
| 3 | `ReplayBenchTest.kt:42:50` | Argument type mismatch: actual type is '() -> Double', but '(Double, Double) -> Double' was expected. | 第 42 行改为 `clock = { now }`，同 #1 |
| 4 | `ReplayBenchTest.kt:42:52` | Return type mismatch: expected 'Double', actual 'Long'. | 同 #2 |

四条同源（尾随 lambda 错绑 `elevation`），两处调用点各产生两条实参/返回类型错配；修两处即覆盖四条。

## 3. 改动清单（文件:行 + before/after）

文件：`app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt`

**第 21 行（`@Before setUp`）**

```diff
-        director = ReplayDirector(SeedReplay.changping) { now }
+        director = ReplayDirector(SeedReplay.changping, clock = { now })
```

**第 42 行（`bench director build under 4ms` 构建采样 lambda）**

```diff
-            ReplayDirector(SeedReplay.changping) { now }
+            ReplayDirector(SeedReplay.changping, clock = { now })
```

未改动项（逐字保留）：

- 5 条 `assertTrue` 断言与阈值文本：`4.0` / `2.0` / `2.0` / `1.0` / `0.5` 一字未动；
- `bench` 的 `warmup = 200`、`loops = 5_000` 未动；
- `now: Long = 0L` 类型与 `setUp()` 重置逻辑未动；
- `ReplayDirector` 实现未动；其余 3 个测试方法（update / visuals / arrows / clashSparks）未动。

## 4. 自检证据（真实命令输出）

### 4.1 调用点回证（两行都必须是 `clock = { now }` 形态）

```
$ grep -n "ReplayDirector(SeedReplay.changping" app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt
21:        director = ReplayDirector(SeedReplay.changping, clock = { now })
42:            ReplayDirector(SeedReplay.changping, clock = { now })
```

### 4.2 断言条数与阈值文本回证（与改前逐字一致）

```
$ grep -n "assertTrue" app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt
3:import org.junit.Assert.assertTrue
44:        assertTrue("导演构建超 4ms: $buildAvg", buildAvg < 4.0)
52:        assertTrue("导演单帧 update 超 2ms: $updateAvg", updateAvg < 2.0)
60:        assertTrue("棋子视觉派生超 2ms: $visualAvg", visualAvg < 2.0)
68:        assertTrue("箭头派生超 1ms: $arrowAvg", arrowAvg < 1.0)
77:        assertTrue("火花生成超 0.5ms: $sparkAvg", sparkAvg < 0.5)
```

改前同命令输出：导入行 + 第 44/52/60/68/77 行共 5 条断言，阈值文本与上表逐字相同；改后条数、文案、阈值无变化。

### 4.3 diff 与行数统计

```
$ git diff -- app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt
diff --git a/app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt b/app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt
@@ -18,7 +18,7 @@ class ReplayBenchTest {
     @Before
     fun setUp() {
         now = 0L
-        director = ReplayDirector(SeedReplay.changping) { now }
+        director = ReplayDirector(SeedReplay.changping, clock = { now })
     }
@@ -39,7 +39,7 @@ class ReplayBenchTest {
     @Test
     fun `bench director build under 4ms`() {
         val buildAvg = bench("directorBuild") {
-            ReplayDirector(SeedReplay.changping) { now }
+            ReplayDirector(SeedReplay.changping, clock = { now })
         }
```

```
$ git diff --stat -- app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt
 app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt | 4 ++--
 1 file changed, 2 insertions(+), 2 deletions(-)
```

仅 1 文件、+2/-2，与任务书允许范围一致。

### 4.4 相邻测试只读核对（均未改动，本单不动）

- `ReplayDirectorTest.kt:17`：`return ReplayDirector(campaign, clock::get) to clock` —— 位置实参，第二形参正是 `clock`，类型 `() -> Long`，本来就正确，未动。
- `ReplayTerrainTest.kt`：多处使用 `elevation = dem` / `elevation = TerrainElevation.Flat` 具名实参或默认值，未动。
- `ReplayTerrainBenchTest.kt:60`：`ReplayDirector(SeedReplay.changping, elevation = dem)` 具名实参，未动。
- `git diff --stat -- app/src/main/` 输出为空：`main/` 零改动。

## 5. 二阶三阶影响

- **直接效果**：两处调用的 lambda 正确绑定 `clock: () -> Long`，实参类型与返回类型均匹配，4 条编译错误销项；`elevation` 走默认值 `TerrainElevation.Flat`，与改前（编译不过、根本到不了运行期）相比，运行期地形口径与该 bench 类既有用例一致——bench 不关心 DEM 贴地，Flat 默认值正是其应有口径。
- **二阶影响（受影响面）**：
  - 仅测试源集 `ReplayBenchTest` 内部；生产代码 `ReplayDirector` 签名、默认值、调用方零改动，无生产行为变化。
  - 同域 `ReplayDirectorTest` / `ReplayTerrainTest` / `ReplayTerrainBenchTest` 的构造调用路径不受影响：它们本来就用位置实参或 `elevation =` 具名实参，不存在同类尾随 lambda 错绑。
  - bench 语义不变：`clock` 仍喂固定毫秒 `now`，不依赖系统时间，单测内不 sleep；构建采样（第 42 行）每次仍新构导演，与原意图一致。
- **三阶影响（用户行为 / 系统复杂度 / 维护成本）**：
  - 用户侧无感知：改动只影响测试能否编译通过，不改 APK 任何运行路径。
  - 维护成本下降：具名实参 `clock = { now }` 把形参意图写在调用点，后来者不必回看构造签名顺序即可判断 lambda 绑给谁；相比之下尾随 lambda 在「多函数类型形参」构造上是陷阱，本单是把该陷阱在本调用点显式消除。
  - 对后续在 `ReplayDirector` 构造上再加形参的潜在演变：具名实参不受形参插入位置影响（只要 `clock` 形参名不变），比位置实参更抗签名演化；这也提示同域新写调用一律用具名实参。

## 6. 反例与边界（先假设本方案错误，找反证）

1. **反例：把 lambda 改绑 `clock` 后，`elevation` 缺省会不会漏注 DEM？**
   本类是模型层性能 bench，断言只压耗时（构建 4ms、update/visuals 2ms、arrows 1ms、sparks 0.5ms），不验证贴地高度；DEM 注入路径由 `ReplayTerrainTest` / `ReplayTerrainBenchTest` 覆盖。本类走 `Flat` 默认值与原设计意图一致，不存在漏注。若未来 bench 需要贴地口径，应显式写 `elevation = ...`，不在本单范围。
2. **边界：`now` 始终为 `0L`，时钟不推进，会不会让构建/派生路径走不到真实阶段？**
   改前改后均如此：`setUp` 重置 `now = 0L`，lambda 读闭包变量。bench 测的是单位操作耗时，不依赖时间推进；时间推进断言在 `ReplayDirectorTest`。该边界属既有测试设计，本单不改变其语义。
3. **边界：第 42 行在 `bench` 的 warmup / loops 循环内反复构造导演，闭包 `{ now }` 每次捕获同一 `now`**
   与改前行为一致；lambda 捕获的是 `now` 变量引用，非快照，此处 `now` 在 bench 期间不变，无差异。
4. **反例：是否存在第二种修法——把尾随 lambda 挪到 `elevation` 位置或调整构造形参顺序？**
   任务书已禁改 `ReplayDirector` 实现与形参顺序；且调签名顺序会波及其余三个测试文件的位置实参（`ReplayDirectorTest:17`），战场扩大。具名实参是最小、最局部的修法，符合 R57 一条缺陷一单。
5. **边界：Kotlin 版本对尾随 lambda 绑定规则是否有变化？**
   仓内 Kotlin `2.4.20`（`gradle/libs.versions.toml:3`）；尾随 lambda 绑最后一个形参是语言稳定规则，与版本无关。若未来构造只保留单一函数类型形参，尾随 lambda 才会重新合法；当前签名两函数类型形参并存，具名实参写法在任何情况下都成立。
6. **空数据 / 断网等边界**：本单不触网、不读资产；`SeedReplay.changping` 为固定种子战役，无空战役、无缺字段路径需要新兜。

## 7. 认知成本

- **新增概念**：无。未引入新类型、新抽象、新命名；改动只是把 Kotlin 尾随 lambda 的隐式绑定改成显式具名实参。
- **接手难度**：下降。调用点自注释「这个 lambda 是时钟」，接手者无需先翻 `ReplayDirector` 构造签名顺序即可读懂；反过来，改前的写法对不熟悉尾随 lambda 规则的读者是隐性陷阱。
- **命名澄清**：`clock` 形参名在生产签名中已存在，本单只是在测试调用侧复用该名，无新增术语。
- **遗留认知债（不在本单清偿）**：构造上并存两个函数类型形参（`clock`、`elevation`）本身容易诱发同类尾随 lambda 错绑；后续可考虑在构造 KDoc 中提示「多函数形参请用具名实参」，但属文档增强，需另派单，本单不动 `main/**`。

## 8. 未验证项（显式声明，不以"应该没问题"代替）

- **未经云端 CI 编译验证**：沙箱禁 gradle / kotlinc，未本地编译；4 条报错是否在云端 run 中消失，需总控提交后由下一次 `:app:compileAppDebugUnitTestKotlin` 确认。
- **未经单测运行**：bench 的 warmup / loops / 阈值实际耗时未在本环境跑过；4ms / 2ms / 1ms / 0.5ms 红线是否在真机达标，仍待真机压测（与本单编译修复无关，属既有待验证项）。
- **未经真机**：无任何设备验证。
- 三布尔现状（HEAD commit / CI 绿红 / 有无 APK）以备案 §1.1 与总控轮询为准，本单不报百分比。⚠️已作废：本条"三布尔含 APK"旧口径作废，以 F-01 / F1（`docs/落文-规矩总纲.md:198`）两布尔为准（2026-11-25 清理）。

## 9. 交接（发现但未改的跨模块问题）

- 同域构造签名「双函数类型形参」存在诱发尾随 lambda 错绑的结构性风险；建议后续在 `ReplayDirector` 构造 KDoc 补一句调用提示，或评估形参分组。证据：`ReplayDirector.kt:46-56`。归属：回放域（`ui/map/replay/`），不属本单。
- 工作区 `ui/map/crossing/**` 存在他单未提交产物（`CrossingRasterBridge.kt`、`RasterDecoder.kt`、`VictoryCheckerTest.kt` 及 crossing 四个测试文件改动），本单未读、未改、未提交。
