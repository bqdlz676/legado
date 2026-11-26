# 诊断报告 · shard 2/4 挂死静态定位（Q-243，Verify 45801836915 / commit a398229）

- **首行布尔：是否已定位到单一最可疑类：否。** 160 个分片类全量静态扫描后，未发现可单独解释"整个 test worker 永久挂住"的无界阻塞点；残留风险集中在 3 组候选，见 §四。
- 核验仓库：`bqdlz676/legado`（本地路径 `/home/user/Doubao/chats/38454122521460994/legado`）
- 复核 run：Verify `45801836915`（head sha `a398229874cbfc7002ace661fb2dba6e7a091e3f`）；shard 2/4 job `139849477456` 最终 **completed / cancelled**，`10:34:42Z → 11:19:56Z`（2714s / 45m14s，命中 `verify.yml:213` `timeout-minutes: 45`）；测试任务 `:app:testAppDebugUnitTest` `10:39:14.772Z → 11:19:54.856Z`（2440.1s / 40m40.1s），窗内无逐用例进度行，无 XML 产物；清理期终止 4 个 java 孤儿 + 1 个 tee。静态定位结论不变；挂死用例名仍为证据缺口。
- 类清单口径：**discover 产物** `test-class-lists` artifact 内 `shard-2.txt`（run `45801836915` 下载，`/tmp/listsx/shard-2.txt`），160 条；非字母序近似分片。`/tmp/cl/shard-2.txt` 已不存在，未采用。
- 核验方式：只读静态扫描，未改任何测试文件，未编译、未跑本地单测、无 git 写操作。下文 `文件:行` 锚定本地工作树；远端 `a398229` 同名文件个别行号差 1（远端行号在 Q-228 报告 §7.2 并列）。
- 锚点卡：docs/锚点/Q243-shard2定位.md

## 一、扫描口径与覆盖

- 清单 160 条，按 FQN 映射 `app/src/test/java/<包路径>.kt`：158 条命中源文件，2 条无源文件（见 §五-3）。
- 扫描特征（逐文件逐行）：`while(true)`、`Thread.sleep` / `SystemClock.sleep`、`CountDownLatch.await()` 无超时、`awaitTermination()` 无超时、`runBlocking` 包外部回调、`Future.get()` 无超时、`Thread(` 创建、`Executors` / `ExecutorService` / `GlobalScope` / `CoroutineScope`、阻塞队列 `.take()`、裸 `.join()`、网络直连（`Socket` / `openConnection` / `HttpURLConnection` / `ServerSocket` / `URL(`）、`delay(`、`ProcessBuilder` / `Runtime.exec`、`Semaphore` / `CompletableDeferred` / `Channel.receive`。
- 护栏现状：分片内带 JUnit `@Test(timeout=)` 的文件 4 个——`RhinoScriptErrorContextTest`（4 例 5_000ms）、`RssSortUrlsTest`（8 例 5_000ms）、`JsSourceConfigTest`（40 例中仅 1 例 5_000ms）、`McpServiceContractTest`（8 例中仅 1 例 5_000ms）。
- 远端执行口径（job 日志行 251 原文）：`./gradlew :app:testAppDebugUnitTest --no-daemon --build-cache --stacktrace "${ARGS[@]}"`，**不带** `-PexcludeRhino`；远端 a398229 工作流无独立 `rhino-eval-tests` job。本轮 160 类全部在分片内求值，Rhino 候选在分片内是**活的**；Q-246 分类隔离（`excludeCategories RhinoEvalTest` + 独立 job）仅存在于本地未推送工作树，不解释本轮挂死。

## 二、命中类逐条核查（文件:行 + 阻塞机制 + 护栏状态 + 判定）

### 2.1 `io.legado.app.model.jsSource.JsSourceConfigTest`（shard 2，护栏：部分）

- `app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt:468` `@Test(timeout = 5_000)`；`:474` `runBlocking {`；`:475` `withTimeout(500) {`；`:476` `JsSourceConfig.extract("while (true) {}", currentCoroutineContext())`。
- 机制：唯一执行死循环 JS 的用例；`withTimeout(500)` 取消协程，Rhino 指令观察器在指令边界检查 `coroutineContext`（见 §三）。
- 残余风险：若死循环体在两次指令边界之间进入不可被协程中断的 Java/native 调用，`withTimeout` 只能等边界；JUnit 5s 看门线程判失败后**不能杀死仍阻塞在 Rhino/Java 内的工作线程**。
- 其余 39 例仅做静态脚本字符串提取（`:41`–`:391` 多处 `JsSourceConfig.extract(...)`），不执行引擎，无阻塞面。

### 2.2 `io.legado.app.web.mcp.McpServiceContractTest`（shard 2，护栏：部分）

- `app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt:273` `@Test(timeout = 5_000)`；`:276` `runBlocking {`；`:277` `withTimeout(250) {`；`:278` `evaluate(BookSource(), "while (true) {}")`。
- 求值助手 `:351-356`：`withContext(Dispatchers.IO) { val context = currentCoroutineContext(); runScriptWithContext { source.evalJS(js) } … }`，协程上下文经 `runScriptWithContext` 装入 RhinoContext。
- 残余风险：与 2.1 同型——指令边界可取消，边界之间的不可中断调用仍是缺口；`:251` 另一例 `runBlocking` 求值的是立即返回的字面量表达式（`"({message: 'ok'…})"` 等），无循环。

### 2.3 `io.legado.app.ui.map.crossing.CrossingBenchTest`（shard 2，无 JUnit 护栏）

- `app/src/test/java/io/legado/app/ui/map/crossing/CrossingBenchTest.kt:24-25` `WARM_ROUNDS = 50`、`MEASURE_ROUNDS = 500`；`:42` 预热 50 步推演；`:45` 500 轮度量；`:59` 路径搜索预热 50 次；`:64-66` `repeat(20) { repeat(50) { finder.find(Cell(2, 5), Cell(28, 26), ArmyType.REGULAR) } }` = 1000 次 A* 寻路；`:83-86` 快照 50+500 轮；`:103-105` 战役载入 50+500 轮。
- 机制：定长 CPU 压测，**不是无限循环**；但在冷缓存 / 慢 CI runner 上 1000 次寻路 + 千轮推演会累积可观墙钟时间，且无 `@Test(timeout=)` 兜底。
- 同类压测：`GlobeHostBenchTest.kt:22-23`（WARM 100 / MEASURE 1_000，三组千轮）；`DemTerrainBenchTest.kt:29` `loops = 100_000` 采样；`ComponentBenchTest.kt:14`、`InkSlateBenchTest.kt:14` 各一组 p95 预算断言。

### 2.4 `io.legado.app.help.http.OkHttpRetryTest`（shard 2，无 JUnit 护栏）

- `app/src/test/java/io/legado/app/help/http/OkHttpRetryTest.kt:22`、`:36`、`:52` 三处 `runBlocking { }`；拦截器返回固定 `Buffer` mock 响应（`:77-96` `TrackingResponseBody`），不发真实网络。
- 判定：阻塞面低；未加护栏属口径不一致，不是本轮挂死的首要解释。

### 2.5 `io.legado.app.ui.main.search.SearchCommandGateTest`（shard 2，无 JUnit 护栏）

- `app/src/test/java/io/legado/app/ui/main/search/SearchCommandGateTest.kt:24` `Executors.newFixedThreadPool(2)`；`:30` `releaseAction.await(5, TimeUnit.SECONDS)`；`:33` `actionStarted.await(5, SECONDS)`；`:39` `stopStarted.await(5, SECONDS)`；`:40` `stopFinished.await(100, MILLISECONDS)`；`:42-43` `running.get(5, SECONDS)` / `stop.get(5, SECONDS)`；`:44` `stopFinished.await(5, SECONDS)`；`:51` `executor.shutdownNow()` 在 `finally`。
- 判定：所有 latch / future 均带超时，线程池 `finally` 关停；无无界等待。

### 2.6 `io.legado.app.ui.main.search.SearchHotBinderTest`（shard 2，无 JUnit 护栏，协程级超时在位）

- `app/src/test/java/io/legado/app/ui/main/search/SearchHotBinderTest.kt:43`、`:68`、`:88`、`:115`、`:148`、`:181` 六处 `runBlocking`；每处 `state.first { 谓词 }` 均包 `withTimeout(5_000)`（`:59`、`:82`、`:102`、`:109`、`:131`、`:139`、`:164`、`:171`、`:194`、`:218`）；注入 `Dispatchers.Unconfined`，不碰 `viewModelScope` / `Dispatchers.Main`。
- 判定：谓词永不命中时 5s 抛 `TimeoutCancellationException`，不构成永久挂死。

### 2.7 其余命中（假阳性归类）

- `BookControllerImageIsolationTest.kt:27-40`、`WebJsExtensionsConfigContractTest.kt:26`、`ReplayDirectorTest.kt:25-29/:172-178`：命中串均为源码子串断言或 `clock.get()` 误匹配 `.get()`，无运行时阻塞。
- `HighlightStyleDialogTest.kt:104`：`UiThread(` 被 `Thread(` 正则误匹配，方法体内无线程创建。
- `SourceCompatibilityTest.kt:252/:289`：仅构造 `Request.Builder` / mock `Response`，无 `newCall().execute()`。
- 全分片未检出：`ProcessBuilder` / `Runtime.exec`、真实 `Thread(` 创建、`wait()`、`Semaphore`、`ServerSocket`、`CompletableFuture.get()` 无超时、`awaitTermination()` 无超时。

## 三、引擎侧事实（Rhino 能否被超时打断）

- `modules/rhino/src/main/java/com/script/rhino/RhinoScriptEngine.kt:294` `cx.instructionObserverThreshold = 10000`；`:314-318` `observeInstructionCount` 回调 `cx.ensureActive()`。
- `modules/rhino/src/main/java/com/script/rhino/RhinoContext.kt:339-345` `ensureActive()`：`coroutineContext?.ensureActive()`，`CancellationException` 包装为 `RhinoInterruptError` 抛出。
- `modules/rhino/src/main/java/com/script/rhino/RhinoExtensions.kt:30-40` `runScriptWithContext` 把当前 `coroutineContext`（去掉 `ContinuationInterceptor`）装入 RhinoContext；`:42-44` suspend 重载取 `coroutineContext`。
- 结论：带 Job 上下文的 `while(true)` JS 会在 1 万条指令边界被取消；**边界之间若进入不可中断的 Java/native 调用，协程取消不抢占**；JUnit `@Test(timeout=)` 看门线程只能让测试判失败，不能强制回收仍在原生调用里的工作线程。

## 四、Top3 残留风险候选（静态排序，非 CI 已证实挂死用例）

1. **`JsSourceConfigTest.kt:468-480`** —— 死循环 JS 用例，双护栏（`withTimeout(500)` + JUnit 5s）在位，但取消依赖指令边界；若 Rhino 内进入不可中断段，工作线程可能残留。最小护法：把求值放进单线程 `ExecutorService`，`future.get(5, TimeUnit.SECONDS)` 超时后 `shutdownNow()`；或在 Rhino 观察器里叠加墙钟截止时间（`System.nanoTime` 差值），不只看协程活跃态。
2. **`McpServiceContractTest.kt:273-281`** —— 同型死循环 JS 用例，上下文经 `runScriptWithContext` 装入；护法同 1：独立线程池 + `future.get` 硬超时兜底，覆盖边界之间的不可中断调用。
3. **`CrossingBenchTest.kt:24-25/:59-66`** —— 1000 次 A* 寻路 + 千轮推演的定长压测，无 JUnit 超时；慢 runner 上是"分片跑长"的最可能贡献者。最小护法：压测类打 `@Tag("bench")` 移出单测分片，或给每个 bench 用例补 `@Test(timeout=…)`（按本机实测预算放大余量）；迭代轮次改为 CI 环境变量可调。

备选观察项（不入 Top3）：`OkHttpRetryTest.kt:22/:36/:52` 补 `@Test(timeout=5_000)` 与仓内口径对齐；`GlobeHostBenchTest` / `DemTerrainBenchTest` 随第 3 条一并评估。

## 五、取证缺口与未排除项

1. 静态扫描无法看到 Gradle worker 内正在执行哪个用例：本轮结束后 job 日志仍无逐用例进度行，无 `test-results-shard-2` XML，失败诊断步骤 `if: failure()` 对 `cancelled` 不触发；挂死用例名本轮不可定位，须待后续完整跑出的分片 XML 或离线复现。
2. 未排除"测试类静态初始化块 / 单例首次装配"阻塞：扫描覆盖方法体特征，未逐类追踪 `companion object` 静态初始化链路；若挂死发生在类加载期，用例级护栏不生效。
3. 两条无源清单条目：`io.legado.app.ui.components.HalfSheetBenchReport`、`io.legado.app.ui.main.search.SearchBenchReport`；源树对应文件为 `HalfSheetBenchmark.kt` / `SearchBenchmark.kt`，疑为编译产物清单缓存残留（见 §五-3 新发现问题）。
4. 本轮 shard 2 已确认挂死至 45 分钟超时取消（§首行），但 Top3 排序仍是静态嫌疑，不代表 CI 已实证挂死用例。

## 新发现问题（2026-11-26，Q-243）

1. Q-220 护栏只覆盖 Rhino / Rss / Mcp 三个文件，shard 2 内 `JsSourceConfigTest`（40 例仅 1 例护栏）、`McpServiceContractTest`（8 例仅 1 例护栏）、三个 bench 类与 `OkHttpRetryTest` 均无 JUnit 超时 —— 待裁：按 §四 Top3 决定补护栏范围，压测类是否移出单测分片。
2. 协程 `withTimeout` 不能抢占 Rhino 指令边界之间的不可中断调用；JUnit 看门线程不能杀死残留工作线程 —— 已入队：评估独立线程池 `future.get` 硬超时 + `shutdownNow` 兜底，或 Rhino 观察器叠加墙钟截止。
3. discover 清单混入 `HalfSheetBenchReport` / `SearchBenchReport` 两个无源类名（源文件为 `*Benchmark.kt`），疑为构建缓存枚举残留 —— 已入队：discover 步骤核对编译产物清单来源。
4. shard 2 挂死第三次复现（b744fb4 45m15s 超时取消；575ede0 13m50s 被连推取消；a398229 45m14s 超时取消），Q-220 三文件护栏已在远端树但未能避免 worker 挂死；Q-246 Rhino 分类隔离尚未推送上线 —— 待裁：隔离改动上线后复测，若仍挂死再按 Top3 扩护栏。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=e9560a5c333b；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-验收代理必读.md:62`：「HEAD commit / CI 绿红（含单测全绿）」
2. `docs/机制-总控必读.md:108`：「回源（回源成本 ≪ 记忆成本）」
3. `docs/机制-验收代理必读.md:24`：「自己写脚本比对（例：逐块逐字节比对原文与重排产物）」
- 适用条款：F-01 两布尔回报（HEAD commit / CI 绿红含单测全绿，不报 APK、不报百分比）；验收独立取数、数量类结论必须给命令与真实输出；锚点强制 `文件:行` 或 commit sha 可回源；只读分析，不改 `app/` 与 `data_scripts/`、不做 git 写操作、不本地编译；证据不足处写明缺口，不替 CI 编结论。

## 附：机检输出

### 8.1 回执块机检（onboarding，直跑 `check_report`）

CLI 入口 `python3 data_scripts/check-onboarding.py`：

```text
ONBOARD: PASS（本轮无报告改动）
RC=0
```

说明：CLI 按 `git status --porcelain` 通配扫描，中文文件名经 git 八进制转义后 `fnmatch` 漏判，故 CLI 口径为「本轮无报告改动」；回执块本身直跑单文件：

```text
$ python3 -c "import importlib.util,sys; spec=importlib.util.spec_from_file_location('co','data_scripts/check-onboarding.py'); co=importlib.util.module_from_spec(spec); spec.loader.exec_module(co); probs=co.check_report('docs/诊断-shard2挂死定位.md'); print('PROBLEMS:',probs); print('RESULT:','PASS' if not probs else 'FAIL')"
PROBLEMS: []
RESULT: PASS
```

### 8.2 锚点卡守卫（`--strict`）

```text
$ python3 data_scripts/check-anchor-card.py --strict docs/诊断-shard2挂死定位.md
汇总: PASS=1 FAIL=0（共 1 份）
RC=0
```

### 8.3 新发现问题门禁

```text
$ python3 data_scripts/check-problem-finding.py /home/user/Doubao/chats/38454122521460994/legado/docs/诊断-shard2挂死定位.md
PROBLEM-FINDING: PASS（1 份报告均带新发现问题与处置状态）
RC=0
```

回执块机检结论：指纹至少一枚命中现行磁盘版本；3 条逐字引用全部回源命中；「适用条款」行在位；新发现问题 3 条均带处置状态词。
