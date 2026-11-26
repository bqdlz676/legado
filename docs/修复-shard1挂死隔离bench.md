# 修复 · shard1 挂死：重负载 bench 结构隔离 + Rhino 求值类全面补标（Q-261）

锚点卡：docs/锚点/Q261-bench隔离.md

> 边界：本单只做 CI 执行拓扑隔离与分类补标——新建 `SlowBenchTest` 标记、给真 bench 执行类打标、`app/build.gradle` 加 `includeSlowBench`/`excludeSlowBench` 属性分支与用例级日志、`verify.yml` 分片排除 + 独立 `slow-bench-tests` job + aggregate 纳入新产物；并把 Q-243 遗留的 13+ 触达 Rhino 求值类穷举复核后补打 `RhinoEvalTest`。未改任何测试断言、未改 main 业务代码；未 git 写、未本地编译，仅跑 `kotlin-syntax-lint.py`、`check-workflow-contracts.py`、`check-gradle-syntax.py` 与文档门禁。
> 上游事实：run 7530b9a 中 Discover/rhino/shard2/3/4 全 success，**shard1 自 12:19:35Z 起 in_progress 20 分钟以上无 XML**（历史上该分片 4 分钟即绿，判定为本轮新引入挂死或被新代码拖成超长）。

## 一、总结论

- **结论：PASS（结构隔离已落地；分类过滤组合行为与独立 job 实际收编条数以云端 CI 首轮回证为准，沙箱无 JDK 未本地跑 Gradle）。**
- bench 侧：9 个真 bench 执行类打 `SlowBenchTest` 独立 job 收编；3 个 bench 引擎 `object` 与 1 个报告 `data class` 不含 `@Test`、JUnit 不执行，不打标（判定表见 §三）。
- Rhino 侧：穷举 test 源集求值入口，新增 14 个真求值类补打 `RhinoEvalTest`；既有 4 个在册；合计 Rhino 18 类 + SlowBench 9 类 = 27 类，两分类互斥、全部在 discover 清单 643 类内（§七、§八）。
- 取证兜底：每个 Test 任务开 `testLogging` events `STARTED`/`FAILED`（`showStandardStreams=false`），下一次挂死能从日志 grep 出最后开始的测试类（§五）。

## 二、改动清单（文件:行）

1. 新建 `app/src/test/java/io/legado/app/SlowBenchTest.kt`：JUnit4 分类接口，KDoc 写明隔离原因、类粒度判据与打标口径。
2. `app/build.gradle`（`:216-242`）：`useJUnit { }` 内新增 `includeSlowBench`/`excludeSlowBench` 两分支；`testTask.testLogging { events "STARTED", "FAILED"; showStandardStreams = false }`。Rhino 两分支原样保留未改坏。
3. `.github/workflows/verify.yml`：
   - 分片命令 `:321` 追加 `-PexcludeSlowBench`；
   - 新增独立 job `slow-bench-tests`（`:499-617`，`timeout-minutes: 8`，`-PincludeSlowBench`，产物 `test-results-slow-bench` / `-failed`）；
   - aggregate `:624` `needs` 加 `slow-bench-tests`；`:647-651` 下载 `test-results-slow-bench-*`；`:678-682` 归组标签 `slowbench`。
4. 打标：SlowBenchTest 9 类、RhinoEvalTest 新增 14 类（清单与证据见 §三、§七）。

## 三、bench 真伪判定表（9 候选 + 3 补充断言类）

判定口径：只有含 `@Test` 方法、会被 JUnit 执行的 `class` 才打标；bench 引擎 `object` 与报告 `data class` 无 `@Test`，JUnit 空跑，不占分类。

| 候选（任务书点名） | 磁盘文件 | 形态 | 判定 | 处置 |
| --- | --- | --- | --- | --- |
| BubbleBenchmarkTest | `help/bubble/BubbleBenchmarkTest.kt:19` | `@Test`×3，200 热轮 + 500 批量 resolve | 真 bench | 打 SlowBenchTest |
| VerticalPageTurnBench | `ui/book/read/vertical/VerticalPageTurnBench.kt:15` | `@Test`×1，30 迭代 × 全章翻页 | 真 bench | 打 SlowBenchTest |
| ComponentBenchmark | `ui/components/ComponentBenchmark.kt:20` | `object` 引擎 + `BenchReport`/`BenchPhaseReport` data class，无 `@Test` | 非执行类 | 不打标；其断言类 `ComponentBenchTest` 打标 |
| HalfSheetBenchTest | `ui/components/HalfSheetBenchTest.kt:10` | `@Test`×1，调 `HalfSheetBenchmark.run()`（warmup 2000 + iter 50000） | 真 bench | 打 SlowBenchTest |
| InkSlateBenchmark | `ui/components/inkslate/InkSlateBenchmark.kt:21` | `object` 引擎 + 报告 data class，无 `@Test` | 非执行类 | 不打标；其断言类 `InkSlateBenchTest` 打标 |
| FontRegistryBenchTest | `ui/font/FontRegistryBenchTest.kt:16` | `@Test`×4，2000 热轮 + 5000 批量 | 真 bench | 打 SlowBenchTest |
| SearchBenchReport | `ui/main/search/SearchBenchmark.kt:24` | `data class` 报告模型，无 `@Test` | 非执行类 | 不打标；其断言类 `SearchBenchTest` 打标 |
| CrossingBenchTest | `ui/map/crossing/CrossingBenchTest.kt:21` | `@Test`×5，500 轮 measure + A* 20×50 + 60 回合整局 | 真 bench | 打 SlowBenchTest |
| ReplayBenchTest | `ui/map/replay/ReplayBenchTest.kt:13` | `@Test`×5，warmup 200 + loops 5000 × 4 类操作 | 真 bench | 打 SlowBenchTest |

补充发现的断言类（任务书只点了引擎对象，真正被 JUnit 执行的是它们）：

| 断言类 | 引擎调用 | 迭代预算 | 处置 |
| --- | --- | --- | --- |
| `ui/components/ComponentBenchTest.kt:11` | `ComponentBenchmark.run()` | 引擎 `ComponentBenchmark.kt:30-31` warmup 2000 + iter 50000 | 打 SlowBenchTest |
| `ui/components/inkslate/InkSlateBenchTest.kt:11` | `InkSlateBenchmark.run()` | 引擎 `InkSlateBenchmark.kt:28-29` warmup 2000 + iter 50000 | 打 SlowBenchTest |
| `ui/main/search/SearchBenchTest.kt:11` | `SearchBenchmark.run()` | 引擎 `SearchBenchmark.kt:32-33` warmup 500 + iter 5000×5 阶段 | 打 SlowBenchTest |

打标合计 9 个执行类：BubbleBenchmarkTest、VerticalPageTurnBench、ComponentBenchTest、HalfSheetBenchTest、InkSlateBenchTest、FontRegistryBenchTest、CrossingBenchTest、ReplayBenchTest、SearchBenchTest。

## 四、bench 在宿主 JVM 挂死的可能机制（读码取证）

### 4.1 阻塞原语扫描结果（9 执行类 + 4 引擎对象）

- `Thread.sleep`：**未发现**。
- `while (true)`：**未发现**。翻页循环 `VerticalPageTurnBench.kt:61` `while (engine.canNext)` 有 `canNext` 终止条件，非死循环。
- `CountDownLatch.await()` / `runBlocking` / Compose 运行时规则（`createComposeRule` 等）：**未发现**。这批 bench 注释明确「纯模型/状态解析耗时，不依赖 Compose 渲染」（`ComponentBenchTest.kt:10`、`SearchBenchTest.kt:10`、`SearchBenchmark.kt:13`）。
- `@Before` 大循环：未发现。`BubbleBenchmarkTest.kt:34`、`FontRegistryBenchTest.kt:46`、`ReplayBenchTest.kt:21` 的 `@Before` 只重置词典/注册表/director。

### 4.2 重负载来源：大批量 CPU 密集迭代 + 大数组排序

- 三组件引擎（`ComponentBenchmark.kt:30-31`、`HalfSheetBenchmark.kt:25-26`、`InkSlateBenchmark.kt:28-29`）：`WARMUP=2_000` + `ITERATIONS=50_000`，每阶段 `repeat(ITERATIONS)` 采样 5 万元素 `LongArray` 再取 p95（`ComponentBenchmark.kt:52-60`）；InkSlate 另有 undo/redo/阶段多轮（`:75-107`）。
- `FontRegistryBenchTest.kt:24-26`：热路径 2000 轮、批量 5000 轮；`ReplayBenchTest.kt:29-35`：warmup 200 + loops 5000 × 4 类回放操作。
- `CrossingBenchTest.kt:27-28`：measure 500 轮；A* 寻路 `:67-69` `repeat(20){ repeat(50){ finder.find(...) } }` = 1000 次网格寻路；整局 `:98` `controller.runToEnd(60)`；JSON 装载 `:106-108` 500 轮。
- `SearchBenchmark.kt:32-33`：warmup 500 + iter 5000 × 5 阶段；`BubbleBenchmarkTest.kt:27-29`：200 热轮 + 500 批量词典解析。

### 4.3 机制研判

- 这批不是睡在 sleep/latch 上，而是 **CPU 密集迭代在超卖共享 CI 虚拟核上被 GC/调度挤压**：单类 5 万次采样 + 5 万元素排序 + 多阶段 warmup，叠加 CI 共享核抖动，单类耗时从秒级被拖到分钟级；若某模型层（A* 网格放大、状态对象反复分配）在本轮回归里劣化，迭代放大后可能逼近或超过 worker 超时，表现为分片长时间 in_progress、无失败 XML。
- 另一族挂死机制是 Rhino 同步求值忽略 interrupt（Q-246 已述），对所有求值者一视同仁，故本单一并补标（§七）。两族隔离到独立 job，互不牵连分片。
- 未在代码里抓到可直接判死锁的阻塞原语；真实 culprit 类名要靠 §五 的 STARTED 日志在下一轮挂死时定位。

## 五、build.gradle 分类分支与用例级日志取证兜底

### 5.1 分类属性分支（`app/build.gradle:216-232`，复用 Q-250 结构）

```groovy
testOptions {
    unitTests.all { testTask ->
        testTask.useJUnit {
            if (project.hasProperty('includeRhino')) { includeCategories 'io.legado.app.RhinoEvalTest' }
            if (project.hasProperty('excludeRhino')) { excludeCategories 'io.legado.app.RhinoEvalTest' }
            if (project.hasProperty('includeSlowBench')) { includeCategories 'io.legado.app.SlowBenchTest' }
            if (project.hasProperty('excludeSlowBench')) { excludeCategories 'io.legado.app.SlowBenchTest' }
        }
        testTask.testLogging {
            events "STARTED", "FAILED"
            showStandardStreams = false
        }
    }
}
```

- Rhino 两分支原样保留；SlowBench 两分支同构，落在 `useJUnit { }` 闭包内（AGP `AndroidUnitTest` 任务实例无分类方法签名，API 属 JUnitOptions，Q-250 已踩坑）。
- 属性不传时两条 `hasProperty` 均 false，不设任何分类过滤，任务跑全量，与改动前口径一致；baseline 模式不传属性同样全量。

### 5.2 用例级日志（追加条：挂死时必须自带 culprit 类名）

- 背景：本轮 shard1 从 12:19:35Z 挂到超过 45 分钟，日志里**没有一条用例名**，root cause 无从取证（上一轮 shard2 同样如此）。
- 处置：`testLogging { events "STARTED", "FAILED"; showStandardStreams = false }`。只开事件名，不开 `showStandardStreams`——后者会把每个用例 stdout 全量灌入日志造成爆量；STARTED 已足够留下「最后开始的测试类」。
- 验收命令（分片 job 失败/超时后，在上传的 `gradle-shard-N.log` 上执行）：

```bash
# 最后 STARTED 的测试类/用例（JUnit4 testLogging 默认形态：io.legado.app.XxxTest > 用例名 STARTED）
grep -E 'STARTED' gradle-shard-1.log | tail -20
# 失败用例
grep -E ' FAILED$|FAILED ' gradle-shard-1.log | tail -20
```

- 日志中应出现的形态（JUnit4 `events STARTED` 默认输出）：

```
io.legado.app.ui.map.crossing.CrossingBenchTest > searchGridReachesTarget STARTED
io.legado.app.ui.map.crossing.CrossingBenchTest > searchGridReachesTarget FAILED
```

- 挂死取证口径：worker 超时被杀时，日志最后一条 `STARTED` 而无对应 `FAILED`/`PASSED` 的用例，即嫌疑 culprit；结合 §四 的迭代预算判是否重负载放大。

## 六、verify.yml 接线

1. 分片排除（`:305-307` 注释、`:321` 命令）：`./gradlew :app:testAppDebugUnitTest -PexcludeRhino -PexcludeSlowBench --no-daemon --build-cache --stacktrace "${ARGS[@]}"`。
2. 新增 `slow-bench-tests` job（`:499-617`）：
   - `:501-502` baseline 模式开关同口径；`:503` `timeout-minutes: 8`；
   - `:582` `./gradlew :app:testAppDebugUnitTest -PincludeSlowBench --no-daemon --build-cache --stacktrace`，不带 `--tests`，由 Gradle 分类过滤器选取；
   - `:585-601` 失败诊断（STARTED/FAILED/Exception/No tests found 抓取 + annotation）；
   - `:605-609` 绿跑产物 `test-results-slow-bench`；`:612-617` 失败兜底 `test-results-slow-bench-failed`。
3. aggregate 纳入：
   - `:624` `needs: [discover, test-shard, rhino-eval-tests, slow-bench-tests]`——bench job 失败阻断汇总，红灯不漏；
   - `:647-651` 下载 `pattern: test-results-slow-bench-*` 落 `results/`；
   - `:678-682` 归组：`shard-(\d+)` → 分片号，`rhino-eval` → `rhino`，`slow-bench` → `slowbench`，其余 `?`。

## 七、§Z 13+ 求值类全面打标（穷举复核）

### 7.1 穷举方法

- 关键字：`evalJS`、`RhinoScriptEngine`、`evaluateScript`、`Context.enter`、`runScript`、`runScriptWithContext`、`evalSuspend`、`JsExtensions`、`LoginUiScriptEvaluator`、`analyzeRule` 走求值入口、`JsSource`。
- 逐个打开确认：真调 Rhino 引擎求值（`RhinoScriptEngine.eval/compile/evalSuspend/run`、`runScriptWithContext`、`Context.enter`、`AnalyzeRule.evalJS`/`getElements("@js:")`、继承默认 `evalJS` 的真脚本源）才打标；只读源码字符串的契约测试、手写 `evalJS` 桩、jsoup DOM、URL 解析、异常包装器不打标。

### 7.2 新增打标 14 类（既有 4 类在册，合计 Rhino 18）

| # | 测试类（文件:行 求值证据） | 求值形态 |
| --- | --- | --- |
| 1 | `data/entities/rule/ContentRuleCompatibilityTest.kt:67` | `RhinoScriptEngine.eval(script, ScriptBindings())` |
| 2 | `help/book/BookExportFileNameTest.kt:43-44` | `RhinoScriptEngine.eval("null")`/`eval("undefined")`（字面量，走引擎，轻量；纳入口径一致） |
| 3 | `JsEngineCapabilitiesTest.kt:39/48` | `RhinoScriptEngine.eval` + `getRuntimeScope` 多例 |
| 4 | `JsTest.kt:37/41/72` | `RhinoScriptEngine.eval`/`run` 多例 |
| 5 | `model/AutoTaskCoreTest.kt:64/126/148/158` | `RhinoScriptEngine.eval(task.script)` 等 |
| 6 | `model/CryptoJsCompatibilityTest.kt:149/158/162/193` | `source.evalJS("CryptoJS.SHA256(...)")` 等 |
| 7 | `model/jsSource/JsSourceEngineTest.kt:231-233` | `getRuntimeScope` + 两次 `RhinoScriptEngine.eval` |
| 8 | `RhinoJsonInitializationTest.kt:28/55` | `Context.enter()` + `RhinoScriptEngine.eval` |
| 9 | `RuntimeConcurrencyTest.kt:31/39/54` | `runScriptWithContext` + `evalSuspend("bridge.hop()")` |
| 10 | `SourceCompatibilityTest.kt:48/70/81/105` | `RhinoScriptEngine.eval` 多例 |
| 11 | `ui/code/SafeEditorResultCodecTest.kt:158` | `RhinoScriptEngine.eval` 注入 runtime 跑 js_beautify |
| 12 | `model/analyzeRule/ReviewRuleParserTest.kt:420/430/444/463` | `analyzeRule.evalJS(...)` |
| 13 | `model/analyzeRule/AnalyzeRuleElementsNormalizationTest.kt:17` | `getElements("@js:[1, null, 2]")` 走 Mode.Js → `evalJS` |
| 14 | `data/entities/LoginInfoMapInitializationTest.kt:42-47` | `RhinoReentrantLoginSource` 未覆写 `evalJS`，继承真 Rhino，`getLoginInfoMap()` 求值 `@js:` loginUi 脚本 |

既有在册 4 类：`RhinoScriptErrorContextTest`、`help/source/RssSortUrlsTest`、`model/jsSource/JsSourceConfigTest`、`web/mcp/McpServiceContractTest`。

### 7.3 复核后不打标的排除类（非求值，附反例理由）

- 静态契约/读源码串：`ReviewWebApiContractTest`、`AutoTaskPersistenceContractTest`、`JsExtensionsRefreshContractTest`、`JsExtensionsShowBrowserTest`、`WebJsExtensionsConfigContractTest`、`WebJsExtensionsResultCacheTest`、`WebViewPoolJavascriptInterfaceContractTest`、`AnalyzeUrlLoginHeaderContractTest`、`ReviewRuleParserFallbackTest`（只拆规则串字段，不调求值方法）、`AnalyzeUrlUploadClientTest`、`RemoteBookWebDavUploadTest`、`ExploreCategoriesTest`、`LoginEntryRoutingTest`、`ShelfEntrySweepTest`、`RssReadRecordToggleContractTest`、`JsSourceAuthorGuideTest`、`JsSourceDispatchSentinelTest`、`JsSourceTocWriteBackSentinelTest`（三份 sentinel 均 `File.readText` 静态契约，非求值）。
- 手写 `evalJS` 桩、不调引擎：`BaseSourceHeaderTest.kt:72`（桩记录脚本串、抛预设异常、返固定 JSON）；`HtmlFormatterTest.kt:59`（`object: JsExtensions` 只实现 getSource/getTag，调 Kotlin `htmlFormat` 拼相对 URL，不走 Rhino）。
- jsoup DOM / URL 解析 / 正则：`AnalyzeByJSoupDomTest`（CSS 选择器，无 `@js:`）、`AnalyzeUrlNetworkOptionsTest`（OkHttp/Dns/Interceptor 选项）、`CurlAnalyzeUrlConverterTest`（curl 串→AnalyzeUrl 解析）、`JavaScriptIndentationConfigTest`（`Pattern.compile` 是 Java 正则，readText JSON 静态）。
- 异常包装器：`ui/login/LoginUiScriptEvaluatorTest.kt`——被测 `evaluateLoginUiScript`（`LoginUiScriptEvaluator.kt:8-23`）只包 try/catch，`block()` 是 Kotlin lambda 抛异常，不起 Rhino；测试 block 全是 `throw error`，无脚本求值。任务书点名嫌疑，实测排除。

### 7.4 类粒度判据（已写进 SlowBenchTest 与 RhinoEvalTest 注释）

- 为什么类粒度不是用例粒度：Gradle `includeCategories`/`excludeCategories`（JUnit4 Category）只能作用到测试类，无法按单个 `@Test` 方法开关。故判定单位是类——类内只要存在重负载 bench 或真求值用例，整类平移到独立 job；同类快速用例随之一起走，不影响总数与互斥并集。
- `@Ignore` 半跑用例不影响：分类按类注解生效，类内被 `@Ignore` 跳过的用例本就不计入 XML `tests=`；独立 job 收编整类、分片排除整类，半跑用例归属不变，总数求和口径一致。

### 7.5 本轮打标文件全路径清单（59 文件级回源）

> 口径：门禁 59 只认文件级回源——全相对路径命中，或带 `.kt` 后缀文件名作为独立 token 命中；仅写目录名不算。下表逐行列出 18 个 `RhinoEvalTest` 与 9 个 `SlowBenchTest` 执行类的完整 `app/src/...` 路径，供窗口内提交逐条回源。

**RhinoEvalTest 分类（18 个，既有 4 + 本轮新增 14）**

| # | 全路径 | 归属 |
| --- | --- | --- |
| 1 | `app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt` | 既有在册 |
| 2 | `app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt` | 既有在册 |
| 3 | `app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt` | 既有在册 |
| 4 | `app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt` | 既有在册 |
| 5 | `app/src/test/java/io/legado/app/data/entities/rule/ContentRuleCompatibilityTest.kt` | 本轮新增 |
| 6 | `app/src/test/java/io/legado/app/help/book/BookExportFileNameTest.kt` | 本轮新增 |
| 7 | `app/src/test/java/io/legado/app/JsEngineCapabilitiesTest.kt` | 本轮新增 |
| 8 | `app/src/test/java/io/legado/app/JsTest.kt` | 本轮新增 |
| 9 | `app/src/test/java/io/legado/app/model/AutoTaskCoreTest.kt` | 本轮新增 |
| 10 | `app/src/test/java/io/legado/app/model/CryptoJsCompatibilityTest.kt` | 本轮新增 |
| 11 | `app/src/test/java/io/legado/app/model/jsSource/JsSourceEngineTest.kt` | 本轮新增 |
| 12 | `app/src/test/java/io/legado/app/RhinoJsonInitializationTest.kt` | 本轮新增 |
| 13 | `app/src/test/java/io/legado/app/RuntimeConcurrencyTest.kt` | 本轮新增 |
| 14 | `app/src/test/java/io/legado/app/SourceCompatibilityTest.kt` | 本轮新增 |
| 15 | `app/src/test/java/io/legado/app/ui/code/SafeEditorResultCodecTest.kt` | 本轮新增 |
| 16 | `app/src/test/java/io/legado/app/model/analyzeRule/ReviewRuleParserTest.kt` | 本轮新增 |
| 17 | `app/src/test/java/io/legado/app/model/analyzeRule/AnalyzeRuleElementsNormalizationTest.kt` | 本轮新增 |
| 18 | `app/src/test/java/io/legado/app/data/entities/LoginInfoMapInitializationTest.kt` | 本轮新增 |

**SlowBenchTest 分类（9 个执行类）**

| # | 全路径 |
| --- | --- |
| 1 | `app/src/test/java/io/legado/app/help/bubble/BubbleBenchmarkTest.kt` |
| 2 | `app/src/test/java/io/legado/app/ui/book/read/vertical/VerticalPageTurnBench.kt` |
| 3 | `app/src/test/java/io/legado/app/ui/components/ComponentBenchTest.kt` |
| 4 | `app/src/test/java/io/legado/app/ui/components/HalfSheetBenchTest.kt` |
| 5 | `app/src/test/java/io/legado/app/ui/components/inkslate/InkSlateBenchTest.kt` |
| 6 | `app/src/test/java/io/legado/app/ui/font/FontRegistryBenchTest.kt` |
| 7 | `app/src/test/java/io/legado/app/ui/map/crossing/CrossingBenchTest.kt` |
| 8 | `app/src/test/java/io/legado/app/ui/map/replay/ReplayBenchTest.kt` |
| 9 | `app/src/test/java/io/legado/app/ui/main/search/SearchBenchTest.kt` |

**分类标记接口（2 个，非执行测试类）**

- `app/src/test/java/io/legado/app/SlowBenchTest.kt`（本轮新建）
- `app/src/test/java/io/legado/app/RhinoEvalTest.kt`（既有，Q-246）

## 八、总数不变性论证

- 互斥并集：分片 `-PexcludeRhino -PexcludeSlowBench` 排除 18 Rhino + 9 SlowBench 共 27 类；`rhino-eval-tests` `-PincludeRhino` 收编 18 类；`slow-bench-tests` `-PincludeSlowBench` 收编 9 类。两分类打标集合交集为空（脚本核对 `set(Rhino) & set(SlowBench) = ∅`），三车道并集 == discover 全量清单。
- manifest 交叉核对（`/tmp/lists/manifest.json`，643 类；shard1=161/shard2=161/shard3=161/shard4=160）：27 个打标类**全部在清单内**（Rhino 18/18、SlowBench 9/9），无清单外类；discover 枚举不识别分类，`shard-*.txt` 仍列这 27 类，靠 Gradle 分类过滤器在执行层排除。
- 求和与去重：aggregate `:672-696` 按 `classname` 建 `owner` 判跨 job 重复；分类互斥保证同一类不会同时出现在分片 XML 与任一独立 job XML，`dup` 为空。漏产物会被 `:666 BASELINE=3185` 硬断言抓住（总数少于基线即 FAIL 并打印差值），不会静默掉数。
- 基线口径：3185 来自 success run 45404217394 / commit a978f15（`verify.yml:30-32,664-665`）；本单只换执行位置，不删减用例，基线不下调。

## 九、新发现问题

1. 三组件 bench 引擎 5 万元素 `LongArray` 排序 + 2000 warmup 在共享核上耗时抖动不可控 —— **已修**：整类平移到 `slow-bench-tests` 独立 8 分钟 job，不拖分片。
2. Q-243 遗留 13+ 触达 Rhino 类只隔离了 4 个，求值者漏网 —— **已修**：穷举复核后新增 14 类补打 `RhinoEvalTest`，Rhino 侧合计 18 类在册。
3. 分片挂死日志无用例名，root cause 无从取证 —— **已修**：`testLogging` 开 STARTED/FAILED，附 grep 命令与日志形态（§五）。
4. discover 枚举层不识别分类，分片清单仍含 27 个被排除类，清单行数与实际执行类数不一致 —— **待裁**：是否在 discover Python 步骤按编译产物分类注解剔除清单条目，使清单与执行集合一致（沿用 Q-246 §九-2 遗留）。
5. `includeX`/`excludeX` 属性未做互斥断言，命令行双传会空跑且报错不直观 —— **已入队**：可在 Test 任务加 require 互斥校验（沿用 Q-246 §九-3 遗留）。
6. 沙箱无 JDK，分类过滤与 `--tests` 包含过滤的组合行为、独立 job 实际收编条数未本地验证 —— **已入队**：CI 首轮回证 `per_shard` 含 `rhino`/`slowbench` 分组且总数 3185 不掉。

## 十、自查反例 / 改坏推演

1. **分类标记未被 Gradle 识别**：`includeCategories` 匹配零测试 → 独立 job 空跑或报 No tests found。兜底：CI 首轮回证 `per_shard` 中 `slowbench`/`rhino` 条数 > 0、总数 = 3185；为零先查分类接口全限定名与 `includeCategories` 字符串是否一致。
2. **`-PexcludeSlowBench` 与 `-PincludeSlowBench` 双传**：分类过滤取交集结果为空；现状两条命令行各传一个，不存在同任务双传，边界靠 CI 命令行评审兜，脚本未加互斥断言（§九-5）。
3. **产物名漂移**：aggregate 下载 pattern 是 `test-results-slow-bench-*`；日后改名需同步改 `:608`/`:616`/`:650` 三处，否则总数闸门红。
4. **`--tests` 包含过滤与分类排除组合失效**：若当前 Gradle/AGP 版本下被分类排除的类仍被 `--tests` 拉起，分片挂死风险回归；此点只能 CI 实测确认（§十二）。
5. **打标粒度误判**：把 `object` 引擎或 data class 报告当执行类打标——这类无 `@Test`，分类过滤对其无实际作用，只会污染清单统计；本单已按「含 `@Test` 的 class 才打标」口径剔除（§三）。
6. **求值误判为桩**：`LoginInfoMapInitializationTest` 内既有手写桩 `ReentrantLoginSource`，又有继承默认 `evalJS` 的 `RhinoReentrantLoginSource`；按类粒度整类打标，桩用例随之进 Rhino job，不影响正确性（桩本就快），仅占用独立 job 时长。
7. **改坏 Rhino 既有分支**：本单在 `useJUnit { }` 内追加 SlowBench 两 `if`，未删改 Rhino 两 `if`；回证 §十二 grep `includeRhino/excludeRhino` 仍在。

## 十一、二三阶影响

- 一阶：重负载 bench 与漏网 Rhino 求值类离开 4 分片；挂死/慢速只落在两个独立 job，分片 worker 不再被单类拖死。
- 二阶·误红面：分类是类粒度，同类快速用例被一起平移；若某 bench 类后续拆出纯快速用例，会被一并留在 bench job，独立 job 时长略增，但不影响分片。
- 二阶·取证体积：`STARTED` 事件每用例一行，643 类量级日志增量可控；`showStandardStreams=false` 已关死 stdout 爆量。若未来用例数翻倍，需回看 STARTED 日志体积。
- 二阶·基线膨胀：分类在册类只增不减；漏打标的新 bench/求值类仍混分片。维护点：新增 bench 或求值测试类时必须补对应 `@Category`，否则隔离回归。
- 三阶：若未来引入 JUnit5，分类机制要换 `@Tag` + `useJUnitPlatform`，本闭包写法需随之调整（沿用 Q-250 §十-2 结论）。
- 三阶：baseline 对照模式（`test-baseline`）不带任何分类属性，仍单 job 跑全量，被隔离类在该模式照旧执行；该 job 超时 60 分钟，挂住只拖 baseline 单车道。
- 对用户行为/业务代码零影响：不改 main 源码、不改测试断言，只改 CI 执行拓扑与分类注解。

## 十二、未验证项

- 沙箱无 JDK，未跑 Gradle 配置与单测；Groovy 括号闭合、YAML 结构、Kotlin 语法已用静态门禁核对，真实红绿以云端 CI 为准。
- `excludeCategories`/`includeCategories` 与 `--tests` 包含过滤的组合生效情况、两个独立 job 实际收编类数与用例条数，未本地验证。
- aggregate Python 归组改动只做静态走读；`slowbench` 分组名、manifest 交叉核对在真实 XML 上的表现以 CI 运行为准。
- 工作流 YAML 已随 workflow-contracts 门禁解析通过；job 依赖图、artifact 上传/下载连通性未经真实 CI 跑过。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`："不许 add/commit/push/revert/checkout——提交由总控统一做。"
2. `docs/机制-子代理必读.md:12`："不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。"
3. `docs/机制-子代理必读.md:18`："每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。"
- 适用条款：
  - 禁 git 写操作：未执行 add/commit/push/revert/checkout（子代理必读 :11）。
  - 禁本地编译：未跑 gradle/kotlinc/assemble；仅 python3 跑 lint / 门禁与静态核对（子代理必读 :12）。
  - grep 回证：标记、注解、属性分支、workflow 接线、分类计数均以 grep/脚本现取现核（子代理必读 :18）。
  - 未验证项声明：分类过滤组合行为、独立 job 收编条数、aggregate 真实归组已在 §八、§十二 显式列出（子代理必读 :20）。
  - 不碰他人在飞文件：本单只动本清单内 1 新接口 + 23 打标类 + build.gradle + verify.yml；其他代理改动未读改。

## 十三、门禁自检输出

```
$ python3 data_scripts/kotlin-syntax-lint.py $(git status --porcelain app/src/test | awk '{print $2}')
KOTLIN-LINT: PASS（24 个文件）
rc=0

$ python3 data_scripts/check-workflow-contracts.py
CI-CONTRACT: PASS（workflow 4 个，workflow_run 引用与契约测试路径均一致）
rc=0

$ python3 data_scripts/check-gradle-syntax.py
GRADLE-SYNTAX: PASS（7 个 gradle 文件无行首 # 注释）
rc=0

$ python3 data_scripts/check-report-fields.py docs/修复-shard1挂死隔离bench.md
REPORT-FIELDS: PASS（1 份，0 份不合格）
rc=0

$ python3 data_scripts/check-evidence.py --strict docs/修复-shard1挂死隔离bench.md
汇总: PASS=1 FAIL=0（共 1 份）
rc=0

$ python3 data_scripts/check-onboarding.py docs/修复-shard1挂死隔离bench.md
ONBOARD: PASS（1 份报告）
rc=0
```

（anchor-card 仅 1 条次要提及 WARN：Q-250 已收口并入 Q-246，沿用其锚点卡，非 FAIL。）

## 十四、收红记录（Q-264）

> 背景：push `48d7b27`/`ff9c20c` 后全量门禁 `GATE: FAIL（3 项）`，三条由 Q-261 遗留或并发造成，按真实末行逐条修。本节记录三条红的红因、处置与复跑末行；本单不改 55 门禁脚本（Q-262 在飞改时间口径），不 push、不编译。

### 14.1 ㉖ 接手链 HANDOVER（已修）

- 红因：`b 快照 HEAD 落后 22 提交（> 20）`——入口 §6 快照 HEAD 停在 `ec66676`，当前 HEAD `ff9c20c`，差距 22 超阈值 20。
- 处置：按 `update-handover.py` 既定口径重生成快照（脚本整文件重写 §6/§7，稳定段 §0~§5/§8~§10 逐字保留断言通过；未手改快照段）。
- 复跑末行：

```
$ python3 data_scripts/check-handover.py
HANDOVER: PASS
rc=0
```

### 14.2 55 永远找问题 PROBLEM-FINDING（已修，只改报告未碰脚本）

- 红因：判红 2 份——`docs/修复-三条门禁红.md`（4/6 条缺处置状态）、`docs/修复-台账与队列对账.md`（1/4 条缺处置状态）；条目句末状态措辞不在在册词表（已修/已入队/判废/待裁/已闭环/已处置）。
- 处置（按事实填，未统一填待裁）：
  - `修复-三条门禁红.md`：N-1 台账状态待 Q-247 收口单回写 → **已入队**；N-2 git mv 改名已落地收口 → **已闭环**；N-5 台账状态格待各收口单回填 → **已入队**；N-6 队列闭环核查与检测器范围收窄归属未定 → **待裁**。N-3（待裁）、N-4（已入队）原已合规未动。
  - `修复-台账与队列对账.md`：N-4 四条在飞单起时由调度层回填、补登口径须带 HH:MM → **已入队**。N-1/N-2/N-3（待裁）原已合规未动。
- 复跑末行：

```
$ python3 data_scripts/check-problem-finding.py docs/修复-三条门禁红.md docs/修复-台账与队列对账.md
PROBLEM-FINDING: PASS（现行通过 2 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）
rc=0
```

### 14.3 59 提交来源 COMMIT-PROVENANCE（已修）

- 红因：`48d7b27` 三个 `.kt` 在 docs 报告里只有包内相对路径或类名，未以文件级全路径回源——`ContentRuleCompatibilityTest.kt`、`BookExportFileNameTest.kt`、`SafeEditorResultCodecTest.kt`；59 只认全相对路径命中或带 `.kt` 文件名 token 命中，目录名不算。
- 处置：本报告 §7.5 补「本轮打标文件全路径清单」表，18 个 RhinoEvalTest + 9 个 SlowBenchTest 逐行列出完整 `app/src/test/java/.../*.kt` 路径，另列 2 个标记接口。
- 复跑末行：

```
$ python3 data_scripts/check-commit-provenance.py
COMMIT-PROVENANCE: PASS（最近 10 条 commit 窗口内源码文件均有 docs 报告回源）
rc=0
```

### 14.4 全量 rule-gate 复跑末行

```
$ bash data_scripts/rule-gate.sh
55永远找问题   ok  PROBLEM-FINDING: PASS（现行通过 9 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）
59提交来源     ok  COMMIT-PROVENANCE: PASS（最近 10 条 commit 窗口内源码文件均有 docs 报告回源）
GATE: FAIL —— 失败步骤清单（6 项）：
  ✗ ⑯台账新鲜度｜FAIL 台账缺 1 个队列已登记的派单号（基线外新增欠账）：Q-266
  ✗ ⑭回执证据｜── docs/修复-分组显示名断言口径.md E1=0 E2=4(回源2) E3=1 → FAIL（缺 E1 命令证据）；幻影/越界 2 处：ImportSourceGroupTest.kt:71、FileAssociationViewModel.kt:217
  ✗ ㊵提交路径分离｜ e8ce4a8 ... 混合结单与代码：docs=['docs/索引-文档.md'] code=['.../ImportSourceGroupTest.kt'] COMMIT-SPLIT: FAIL
  ✗ ㊶队列台账一致｜FAIL Q-231/232/233 RUNNING 缺 agent id / 起始时间 / 判据锚 QUEUE-LEDGER: FAIL（3 条）
  ✗ ㊽未推送报告回执｜UNPUSHED-REPORTS: FAIL（1/6 份不合规） - docs/修复-分组显示名断言口径.md 缺锚点卡路径
  ✗ 51自测覆盖｜SELFTEST-COVERAGE: FAIL（基线滞留 1 条：已补 selftest 却仍挂在欠账里） - check-workflow-contracts.py
GATE_RC=1
```

- 归属说明：本单修复的 ㉖ HANDOVER、55 PROBLEM-FINDING、59 COMMIT-PROVENANCE 三项已全部转 PASS，不在上述 6 项失败清单内；剩余 6 项为其他在飞单/并发提交欠账（Q-266 台账缺号、分组显示名单回执与缺卡、e8ce4a8 混合 commit、Q-231/232/233 派单信息缺项、workflow-contracts selftest 基线滞留），不归本单，未代改。GATE 整体仍为 FAIL，待对应在飞单收口。

## 十五、收红记录（Q-267 收尾）

> 背景：承接 Q-264 剩余四条门禁红 + 一条判据补正。本单逐条给真实末行；不 push、不编译；不碰 Q-265 在改的 `data_scripts/check-workflow-contracts.py`。落点：本节续写于本报告（Q-264 §十四 同脉络），未新建第 4 份报告。

### 15.1 ⑯ 台账新鲜度 / 内容对账（已修）

- 红因（Q-264 复核快照）：`台账缺 1 个队列已登记的派单号（基线外新增欠账）：Q-266`；台账在飞行仅 4 列残缺，缺验收代理 / 验收产物 / 级别。
- 处置：
  - `docs/派单台账.md:281` Q-231/232/233 合并行按 7 列 schema 补齐：写码代理 `s_000idW8Xmyx`、文件域 `data_scripts/check-workflow-contracts.py；check-m42 类门禁；data_scripts/selftest-baseline.json`、状态 🟡 在飞、验收代理待派、判据锚 `docs/修复-53自测与M42三元组.md`、级别 L1（L2 待 CI）。
  - `docs/派单台账.md:282` Q-266 行补齐：查代理 `s_000iz0QmNHn`、CI 证据域（`.github/workflows/verify.yml`；bench/aggregate job；Verify run 45818022768）、在飞、验收产物预定、L1。
  - `docs/派单台账.md:283` 新增 Q-267 本单行四要素：机制代理 `s_000iz0QmuSB`、起时 21:2x、文件域四份、判据锚本报告 §十五。
  - `docs/派单台账.md:242-244` 旧三条 18:3x 待派行标「作废（已并入 21:1x 合并行）」，消一行两号双事实源歧义。
- 复跑末行：

```
$ python3 data_scripts/check-dispatch-ledger.py
ok    台账新鲜度：台账 mtime 距 HEAD -10 分钟内
ok    台账内容对账：队列 253 个 Q 号均在台账内或已在历史基线（基线 38 条待销）
ok    台账路径存在性：点名的报告均实存
ok    在飞计数：5 条（硬上限 5）
PASS  台账新鲜度与在飞计数合格
rc=0
```

### 15.2 ㊶ 队列↔台账结构与四要素（已修）

- 红因（Q-264 复核快照）：`Q-231/232/233 RUNNING 缺 agent id / 起始时间 / 判据锚`。
- 处置：复核时队列主表 `docs/派单队列.md:57` 合并行六列已齐（代理 `s_000idW8Xmyx`、起时 2026-11-26 21:1x、判据锚 `Q-212；docs/台账-机制缺陷.md M-42`、文件域 `data_scripts/check-workflow-contracts.py`）；本单在队列主表 `docs/派单队列.md:62` 补登 Q-267 RUNNING 六列（agent / 起时 / 文件域 / 判据锚齐），并把历史追加账 Q-264 行由 RUNNING 回写 DONE（该单 §十四 三门禁已 PASS），使台账在飞 5 与队列 RUNNING 5 双源对齐。
- 复跑末行：

```
$ python3 data_scripts/check-queue-ledger.py
QUEUE-LEDGER: PASS（主表 53 行结构/状态/四要素/双源一致）
rc=0
```

### 15.3 ⑭ 分组显示名报告 E1/E2/E3 证据（已修）

- 红因：`docs/修复-分组显示名断言口径.md E1=0 E2=4(回源2) E3=1 → FAIL（缺 E1 命令证据）；幻影/越界 2 处：ImportSourceGroupTest.kt:71、FileAssociationViewModel.kt:217`。纯文件名在深层测试目录解析不到，判幻影；报告无 `grep -n` / `python3 data_scripts/` 形态命令行，E1 不命中。
- 处置：
  - 补 §2.2 证据回源：`grep -n "importGroupDisplayName" app/src/main/java/io/legado/app/ui/main/bookshelf/ImportSourceGroup.kt` → `:65`；`grep -n 'assertEquals("a b"' app/src/test/java/io/legado/app/ui/main/bookshelf/ImportSourceGroupTest.kt` → `:73`；`python3 data_scripts/kotlin-syntax-lint.py app/src/test/java/io/legado/app/ui/main/bookshelf/ImportSourceGroupTest.kt` → `KOTLIN-LINT: PASS（1 个文件）` rc=0。
  - 两处幻影纯文件名改全路径：`app/src/test/java/io/legado/app/ui/main/bookshelf/ImportSourceGroupTest.kt:71`、`app/src/main/java/io/legado/app/ui/association/FileAssociationViewModel.kt:217`；§2.1 before/after 与源码口径行一并补全路径。
  - §2.1 补 before（`7530b9a` 版 `:71` `assertEquals("a  b", ...)`）/ after（`e8ce4a8` 版 `:71-73` 注释 + `assertEquals("a b", ...)`）原文对照，源码 `ImportSourceGroup.kt:65-66` 口径行未改。
- 复跑末行：

```
$ python3 data_scripts/check-evidence.py --strict docs/修复-分组显示名断言口径.md
汇总: PASS=1 FAIL=0（共 1 份）
rc=0
```

### 15.4 ㊽ 锚点卡对齐（已修）

- 红因：`UNPUSHED-REPORTS: FAIL - docs/修复-分组显示名断言口径.md 缺锚点卡路径`。
- 匹配规则核查（`data_scripts/check-anchor-card.py`）：R1 主对象反查按**文件名前缀编号**匹配 `docs/锚点/` 卡片，不是逐字命名约定；报告文件名无号时取正文首个非引用、非标题段落的首个缺陷号。既有卡 `docs/锚点/Q266-显示名断言.md` 前缀编号 Q266 已在册。
- 处置：报告 §一 补归属句 `Q-266` 与路径行 `锚点卡：docs/锚点/Q266-显示名断言.md`，使主对象号 Q-266 命中既有卡；**未改名**。改名前已 `grep -rn "Q266\|显示名断言" docs/` 全仓回查：除卡片自身与台账/队列文字提及外，无别处按旧路径引用，不产生断链。
- 复跑末行：

```
$ python3 data_scripts/check-anchor-card.py --strict docs/修复-分组显示名断言口径.md
汇总: PASS=1 FAIL=0（共 1 份）
rc=0
```

（保留 4 条次要提及 WARN：E1/E2/E3 为证据阶段编号被检测器 E 族正则误识，Q-267 为次要提及无独立卡；WARN 不判红，见 §十五 新发现问题 1。）

### 15.5 ㊵ 索引生成器产物单列 generated 桶（已修，判据补正）

- 红因：`e8ce4a8` 为「测试断言修正 + `docs/索引-文档.md` GEN 区间刷新」同提交，旧判据把索引产物当人工文档内容，与 `app/src/test/.../ImportSourceGroupTest.kt` 同提交即判混合结单。
- 判据补正（`data_scripts/check-commit-split.py`）：
  - 白名单 `GENERATED_INDEX_FILES = ('docs/索引-机制.md', 'docs/索引-文档.md')`，仅限这两份生成器产物。
  - 新增 `is_generated_only()`：取 `git show <sha>:<file>` 解析 `<!-- GEN:START -->…<!-- GEN:END -->` 区间，再用 `git diff -U0` 解析命中 hunk 的新文件侧行号；**全部改动行落在区间内**才进 `generated` 桶，不计文档内容桶；任一改动落在区间外人工节、标记缺失或 diff 不可解析，保守归入 docs 内容桶照旧红（无法核验即不放绿）。
  - 分桶由三桶扩为四桶：docs 内容 / code / R100 纯改名 / generated；混合判据仍只看 `docs_content and code`，generated 与代码同提交 PASS。
- 双向夹具（`--selftest`，夹具用完自删）：
  - 夹具 C：索引文件只改 GEN 区间内条目 + 代码同提交 → PASS，识别 1 条 generated。
  - 夹具 D：索引文件改区间外人工节 + 代码同提交 → FAIL，不进 generated 桶。
  - 既有夹具 A（R100 纯改名 + 代码 → PASS）、夹具 B（docs 内容改写 + 代码 → FAIL）保留。
- 复跑末行：

```
$ python3 data_scripts/check-commit-split.py --commit e8ce4a8
COMMIT-SPLIT: PASS（e8ce4a8 ... 路径已分离；生成器产物 ['docs/索引-文档.md']）
rc=0

$ python3 data_scripts/check-commit-split.py --count 5
COMMIT-SPLIT: PASS（最近 5 条 commit 文档/代码已分离；1 条仅含索引生成器产物（GEN 区间内刷新））
rc=0

$ python3 data_scripts/check-commit-split.py --selftest
[selftest] R100 纯改名+代码同提交 PASS 通路 OK，豁免： ['docs/new.md']
[selftest] docs 内容改写+代码同提交 FAIL 通路 OK
[selftest] 仅 GEN 区间内索引刷新+代码同提交 PASS 通路 OK，generated： ['docs/索引-文档.md']
[selftest] 区间外人工节改写+代码同提交 FAIL 通路 OK
[selftest] 夹具已自删，双向自测通过。
rc=0

$ python3 data_scripts/check-selftest-coverage.py
SELFTEST-COVERAGE: PASS（49 个门禁中 25 已自带 selftest，24 条历史欠账待销）
rc=0
```

### 15.6 本单改动文件全路径清单（59 文件级回源）

- `docs/派单台账.md`（⑯/㊶ 台账行补齐、旧三行作废并入、Q-267 登记）
- `docs/派单队列.md`（㊶ Q-267 主表行、Q-264 历史账回写 DONE）
- `docs/修复-分组显示名断言口径.md`（⑭ E1/E2/E3 证据、全路径幻影修正、㊽ 锚点路径行）
- `data_scripts/check-commit-split.py`（㊵ generated 桶 + 区间判定 + 双向夹具 C/D）
- `docs/锚点/Q266-显示名断言.md`（既有卡，本单未改内容，仅报告回指对齐）

### 15.7 全量 rule-gate 复跑末行与归属

```
$ bash data_scripts/rule-gate.sh
⑯台账新鲜度                         ok    PASS  台账新鲜度与在飞计数合格
㊶队列台账一致                      ok    QUEUE-LEDGER: PASS（主表 53 行结构/状态/四要素/双源一致）
⑭回执证据                            ok    10 份报告回证齐全
㊵提交路径分离                      ok    COMMIT-SPLIT: PASS（最近 5 条 commit 文档/代码已分离；1 条仅含索引生成器产物（GEN 区间内刷新））
㊽未推送报告回执                   FAIL  UNPUSHED-REPORTS: FAIL（1/11 份已提交未推送的报告不合规） - docs/修复-分片清单净化.md：缺「守则回执」块
51自测覆盖                             ok    SELFTEST-COVERAGE: PASS（49 个门禁中 25 已自带 selftest，24 条历史欠账待销）
55永远找问题                          ok    PROBLEM-FINDING: PASS（现行通过 14 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）
59提交来源                             ok    COMMIT-PROVENANCE: PASS（最近 10 条 commit 窗口内源码文件均有 docs 报告回源）
GATE: FAIL —— 失败步骤清单（3 项）：
  ✗ ⑲锚点卡｜── docs/修复-分片清单净化.md → FAIL（缺锚点卡路径）
  ✗ ㊹调度执行合规｜DISPATCH-B: FAIL RUNNING 行与真实在飞不一致： Q-262 s_000idMyGGqn：轨迹 12 分钟无更新（疑似僵尸 RUNNING）
  ✗ ㊽未推送报告回执｜UNPUSHED-REPORTS: FAIL - docs/修复-分片清单净化.md：缺「守则回执」块
GATE_RC=1
```

- 归属说明：本单负责的 ⑯ / ㊶ / ⑭ / ㊽（分组显示名报告）/ ㊵ / 51 六项均已转 PASS。剩余 3 红不归本单：⑲ 与 ㊽ 同指 `docs/修复-分片清单净化.md`（缺锚点卡路径、缺守则回执块），归该单代理补；㊹ B 点名 Q-262 `s_000idMyGGqn` 轨迹 12 分钟无更新，属在飞代理僵尸核查，归调度层。24 条 selftest 历史欠账中含 Q-265 在改的 `check-workflow-contracts.py`，本单未碰。

### 15.8 新发现问题

1. 锚点检测器 `ID_RE` 族名含 `E`，把证据阶段编号「E1 / E2 / E3」误识为 E 族缺陷号，对分组报告出 3 条次要提及 WARN（不判红）。状态：待裁（检测器词表排除纯阶段编号 `E\d`，或报告改用「证据一/二/三」措辞）。
2. Q-262 `s_000idMyGGqn` 轨迹 12 分钟无更新，㊹ DISPATCH-B 判疑似僵尸 RUNNING；该单在改 `data_scripts/check-problem-finding.py`（Q-262 历史口径），是否收口或续跑需调度层核查轨迹。状态：已入队（调度层核查 Q-262 队列闭环）。
3. `docs/修复-分片清单净化.md` 同时缺锚点卡路径（⑲）与守则回执块（㊽），双红同源。状态：已入队（归属该单代理补卡补回执）。
4. 队列起时沿用粗粒度 `21:2x`，`check-dispatch-compliance.py` 的 `TIME_RE` 只认 `HH:MM` 精确分钟，熔断核算只能落 INFO 不判红；Q-231/Q-262/Q-267/Q-263/Q-266 均在此列。状态：待裁（队列起时口径统一为精确分钟，或正则放宽到 `HH:Mx` 粗粒度并标注不可熔断）。
5. 生成器产物白名单目前仅两份索引；后续若新增索引类产物，必须同步登记 `GENERATED_INDEX_FILES` 并补区间判据，否则新产物会被当人工文档内容桶照旧红。状态：已入队（维护点已写进 `check-commit-split.py` 常量注释）。

### 15.9 守则回执

- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 逐字引用 2：「不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。」（docs/机制-子代理必读.md:12）
- 逐字引用 3：「每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。」（docs/机制-子代理必读.md:18）
- 适用条款：R57 一条缺陷一单；禁 git 写操作（未 add/commit/push）；禁本地编译（仅 python3 跑门禁 / lint / 静态核对）；grep 回证；未验证项显式声明；不碰 Q-265 在改的 `check-workflow-contracts.py`；E3 不凑 PASS。
- 未验证项：沙箱无 JDK，Kotlin/JVM 红绿以云端 CI 为准；㊵ 区间判定只在 /tmp git 夹具与 `e8ce4a8` 历史 commit 上验证，未在新生成器刷新 commit 上复验。

