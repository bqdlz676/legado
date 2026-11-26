# 锚点卡 · Q261 shard1 挂死 bench 隔离 + Rhino 补标

> 对象：run 7530b9a shard1 自 12:19:35Z 挂死 20+ 分钟无 XML。结构隔离重负载 bench（SlowBenchTest），穷举补标漏网 Rhino 求值类，加用例级 STARTED 日志取证。报告 `docs/修复-shard1挂死隔离bench.md`。

## 一、事实锚点（逐字，含 文件:行）

- 新标记：`app/src/test/java/io/legado/app/SlowBenchTest.kt` 接口；类粒度判据写 KDoc。
- gradle 分支：`app/build.gradle:227-231` include/excludeSlowBench；`:239-241` testLogging STARTED/FAILED、showStandardStreams=false；Rhino 分支 `:219-223` 原样保留。
- verify.yml：分片 `:321` `-PexcludeSlowBench`；job `slow-bench-tests` `:499`，`timeout-minutes: 8` `:503`，命令 `:582` `-PincludeSlowBench`，产物 `:608`/`:616`；aggregate `:624` needs、`:650` 下载 pattern、`:680-681` slowbench 归组。
- SlowBench 9 执行类：BubbleBenchmarkTest、VerticalPageTurnBench、ComponentBenchTest、HalfSheetBenchTest、InkSlateBenchTest、FontRegistryBenchTest、CrossingBenchTest、ReplayBenchTest、SearchBenchTest。
- Rhino 18 类：既有 4 + 新增 14（ContentRuleCompatibilityTest、BookExportFileNameTest、JsEngineCapabilitiesTest、JsTest、AutoTaskCoreTest、CryptoJsCompatibilityTest、JsSourceEngineTest、RhinoJsonInitializationTest、RuntimeConcurrencyTest、SourceCompatibilityTest、SafeEditorResultCodecTest、ReviewRuleParserTest、AnalyzeRuleElementsNormalizationTest、LoginInfoMapInitializationTest）。

## 二、参数表

- manifest 643 类（shard1=161/2=161/3=161/4=160）；打标 27 类全在清单，Rhino∩SlowBench=∅。
- 迭代预算：三组件引擎 warmup 2000 + iter 50000；Font 2000/5000；Replay 200/5000；Crossing 500 轮 + A* 20×50 + 60 回合；Search 500/5000×5。
- 阻塞原语扫描：无 sleep / while(true) / CountDownLatch.await / Compose 规则 / @Before 大循环。
- 基线 3185（success run 45404217394 / commit a978f15）；aggregate 硬断言总数不下调。
- 边界：不 push、不 commit、不本地编译；不改测试断言与 main 业务代码。

## 三、结论锚点

- C1 bench 9 类独立 job 收编；object 引擎与 data class 报告无 @Test 不打标。
- C2 Rhino 漏网 14 类补标；静态契约/手写桩/jsoup/URL 解析/异常包装器排除（LoginUiScriptEvaluatorTest 实测非求值）。
- C3 取证：挂死后 `grep STARTED gradle-shard-N.log | tail -20` 取最后开始类名；形态 `io.legado.app.XxxTest > 用例名 STARTED`。
- C4 总数不变：分片排除集 ∩ 两独立 job 包含集 = ∅，并集 = discover 全量；dup 判定为空。

## 四、回源

- 报告：`docs/修复-shard1挂死隔离bench.md`（判定表 / 机制证据 / 穷举排除表 / 全路径回源清单 / 总数论证 / 反例改坏 / 二三阶影响 / 守则回执 / §十四 Q-264 收红记录：㉖/55/59 三项转 PASS，余 6 项他人欠账）。
