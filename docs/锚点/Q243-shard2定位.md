# 锚点卡 · Q-243 shard2 挂死静态定位

> 对象：Verify run `45801836915`（a398229）shard 2/4 job `139849477456` 的挂死嫌疑类排查。回执报告 `docs/诊断-shard2挂死定位.md`。本卡对应派单 Q-243，承接 Q-220 超时护栏复盘与 Q-228 CI 核验。

## 一、事实锚点（逐字，含 文件:行）

- 类清单口径：discover 产物 `test-class-lists` 内 `shard-2.txt`，160 条；158 条命中源文件，2 条无源（`HalfSheetBenchReport`、`SearchBenchReport`）。
- 最终状态：job `139849477456` completed/cancelled `10:34:42Z → 11:19:56Z`（2714s / 45m14s）；测试窗 `10:39:14.772Z → 11:19:54.856Z`（2440.1s），无逐用例进度、无 XML；4 java 孤儿 + 1 tee。远端命令不带 `-PexcludeRhino`，无独立 rhino job，Rhino 候选本轮在分片内执行。
- 首行布尔：**否**——未定位到单一最可疑类。
- `JsSourceConfigTest.kt:468` `@Test(timeout = 5_000)`；`:474` `runBlocking {`；`:475` `withTimeout(500) {`；`:476` `extract("while (true) {}", currentCoroutineContext())`。40 例仅此 1 例护栏。
- `McpServiceContractTest.kt:273` `@Test(timeout = 5_000)`；`:276` `runBlocking {`；`:277` `withTimeout(250) {`；`:278` `evaluate(BookSource(), "while (true) {}")`；助手 `:351-356` `runScriptWithContext { source.evalJS(js) }`。8 例仅此 1 例护栏。
- `CrossingBenchTest.kt:24-25` `WARM_ROUNDS=50` / `MEASURE_ROUNDS=500`；`:64-66` `repeat(20) { repeat(50) { finder.find(...) } }` 1000 次寻路；无 JUnit 护栏。
- `OkHttpRetryTest.kt:22/:36/:52` 三处 `runBlocking`，mock 固定 `Buffer`，无护栏。
- `SearchCommandGateTest.kt:30/:33/:39/:40/:42/:43/:44` latch / future 均带 5s / 100ms 超时；`:51` `shutdownNow()`。
- `SearchHotBinderTest.kt:43/:68/:88/:115/:148/:181` 六处 `runBlocking`，谓词收集均包 `withTimeout(5_000)`。
- Rhino 取消链：`RhinoScriptEngine.kt:294` `instructionObserverThreshold = 10000`；`:314-318` `observeInstructionCount` → `ensureActive()`；`RhinoContext.kt:339-345` `coroutineContext?.ensureActive()`，取消包装 `RhinoInterruptError`；`RhinoExtensions.kt:30-40` 上下文装入 RhinoContext。
- 引擎语义：指令边界可取消；边界之间不可中断的 Java/native 调用不被协程抢占；JUnit 看门线程不能杀死残留工作线程。

## 二、判据（逐条可断言）

1. 永久挂死 ≠ 测试失败：候选必须能让工作线程在无超时原语下阻塞；仅断言失败的用例不入 Top3。
2. 已有 `@Test(timeout=)` 但方法内部阻塞的，注明 JUnit 只能报失败、不能杀线程。
3. 定长压测（repeat 固定轮次）列为"跑长"候选，不列为无限挂死候选。
4. 静态扫描未命中不代表排除：类加载期静态初始化链路未逐类追踪，列入取证缺口。
5. 本单只读：不改任何测试文件，不 git 写，不本地编译。

## 三、边界与取舍

- 结束后日志仍无逐用例进度行、无 XML，挂死用例名不可考；Top3 为静态风险排序，待后续完整跑出的分片 XML 或离线复现后复核。
- 扫描特征集覆盖常见阻塞原语；未覆盖反射、JNI、第三方库内部线程，相关面在缺口节声明。
- 压测类是否移出单测分片属架构取舍，本单只给建议，不动代码。

## 四、验收方式

- 静态回证：每个候选带 `文件:行` 与原句；护栏有无以 `@Test(timeout=` 正则逐文件计数；引擎语义以 `modules/rhino` 源码回源。
- 云端复验（本单不执行）：shard 2 结束后下载 JUnit XML，定位最后执行 / 未完成用例；若 Top3 之外出现挂死用例，回写本卡排序。
- 机检：`check-onboarding.py`（直跑 `check_report`）、`check-anchor-card.py --strict docs/诊断-shard2挂死定位.md`、`check-problem-finding.py <报告绝对路径>` 均 PASS。
