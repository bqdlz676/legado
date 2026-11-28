# CI 核验报告 · commit ff9c20c（Verify 45825892031）+ 续核 e8ce4a8（Verify 45826704483）

锚点卡：docs/锚点/Q266-CI核验.md

- **首行布尔：ff9c20c Verify `45825892031` = `cancelled`（shard 2 命中 45 分钟超时挂死，Aggregate 跳过，基线 3185 未能对账）；e8ce4a8 Verify `45826704483` = `cancelled`（0 个 job，排队阶段被后续推送连带取消，断言修复未获云端回证）。**
- 核验仓库：`bqdlz676/legado`（本地路径 `/home/user/Doubao/chats/38454122521460994/legado`）。
- 被核 commit：`ff9c20c` 全 sha `ff9c20cc975f9d7ddcc06df87a94117e34860f97`；续核 `e8ce4a8` 全 sha `e8ce4a87466c1a560779ca990333847f6845abe0`。
- 关联 run：Verify `45825892031`（ff9c20c）、Screenshot `45825892062` `success`；Verify `45826704483`（e8ce4a8）、Screenshot `45826704472` `success`。
- 核验时间：2026-11-26（Asia/Shanghai）；CI 时间戳均为 UTC。
- 核验方式：GitHub REST API 取 run / jobs / artifacts / job 日志；JUnit XML 下载后用 Python `xml.etree` 逐 testsuite / testcase 求和 tests / failures / errors / skipped 与 classname；源码行号以远端 blob 与工作树回源。全程只读：未编译、未跑本地单测、未改 `app/` 与 `data_scripts/`、无 git 写操作。

## 一、总结论（布尔）

- **ff9c20c Verify `45825892031`：`cancelled`。** Discover、Slow bench isolated、Rhino eval isolated 三套前置 job 全部 `success`；shard 1 / 3 / 4 `success`；shard 2 命中 45 分钟超时被取消；Aggregate `skipped`。
- **shard 1 未复现「45 分钟静默无 XML」老病。** shard 1 用时 276s 正常出 XML（151 份、830 条、0 失败），`testLogging` 的 STARTED 日志在该轮真实生效（830 条 STARTED、0 条 FAILED）。老病本轮迁移到 shard 2（对照 Q-253：上一轮挂死在 shard 1）。
- **shard 2 挂死 culprit 候选**：最后一条 STARTED 为 `SearchHotBinderTest > removing last history word overlapping seed keeps fallback source marker`，其后静默 2410.2s（40m10s）直至超时取消；tee 日志无该用例的 FAILED / PASSED 行，阻塞线程栈未入日志，属候选而非 CI 已证实结论。
- **Aggregate 未保住基线对账。** Aggregate job `skipped`，CI 侧求和 / dup / 清单外类断言均未执行；本单对已下载 5 份 XML 自行求和 2816 条、跨 job dup 0 条，但 shard 2 无 XML，差额不可拆分，不能判漏测也不能判全绿。
- **`ImportSourceGroupTest.displayNameFoldsWhitespaceAndTruncates` 转绿：未验证。** ff9c20c 树 `ImportSourceGroupTest.kt:71` 仍是旧期望 `"a  b"`，shard 2 该用例 `ComparisonFailure` 红；e8ce4a8 树 `:73` 已改 `"a b"`，但其 Verify run 在排队阶段被取消、0 个 job，断言修复没有在任何云端分片里跑过。
- 进度口径按 F-01 两布尔：HEAD commit 以远端 `e8ce4a87466c1a560779ca990333847f6845abe0` 为核对基准；CI 红（ff9c20c `cancelled`、e8ce4a8 `cancelled`，含单测：shard 2 挂死、Aggregate 未对账）。不报 APK、不报百分比。

## 二、ff9c20c 终态表（jobs API 回源）

| job id | 名称 | 结论 | 起止（UTC） | 耗时 |
| --- | --- | --- | --- | --- |
| `139929802811` | Discover test classes (shard lists) | `success` | 13:06:19 → 13:11:27 | 308s |
| `139929803276` | Slow bench tests (isolated) | `success` | 13:06:19 → 13:12:10 | 351s |
| `139929803401` | Rhino eval tests (isolated) | `success` | 13:06:19 → 13:11:54 | 335s |
| `139929805915` | Baseline full unit tests (single job) | `skipped` | 13:06:17 → 13:06:16 | — |
| `139932664534` | Unit tests shard 1/4 | `success` | 13:11:30 → 13:16:06 | 276s |
| `139932664526` | Unit tests shard 2/4 | `cancelled` | 13:11:30 → 13:56:44 | 2714s（45m14s） |
| `139932664487` | Unit tests shard 3/4 | `success` | 13:11:30 → 13:16:02 | 272s |
| `139932664874` | Unit tests shard 4/4 | `success` | 13:11:30 → 13:17:18 | 348s |
| `139958112725` | Aggregate & assert test totals | `skipped` | 13:56:45 → 13:56:44 | — |

- 平行车道：Screenshot `45825892062` = `success`。
- Discover 清单：`test-class-lists` 产物 `manifest.json` 共 644 类，`shard-1.txt` … `shard-4.txt` 各 161 行。

## 三、三套隔离 job 取证（ff9c20c 轮）

### 3.1 Slow bench isolated（9 类 23 条，全绿）

- job `139929803276` `success`，351s；XML 产物 `test-results-slow-bench`（6045 B）逐 suite 求和：23 tests / 0 failures / 0 errors / 0 skipped。
- 9 个 suite（tests）：`BubbleBenchmarkTest` 3、`VerticalPageTurnBench` 1、`ComponentBenchTest` 1、`HalfSheetBenchTest` 1、`InkSlateBenchTest` 1、`FontRegistryBenchTest` 4、`SearchBenchTest` 2、`CrossingBenchTest` 5、`ReplayBenchTest` 5。
- 时长口径（XML `time` 属性实读）：最重 suite 为 `CrossingBenchTest` 7.05s / 5 例；单例最长 `CrossingBenchTest::单回合主循环在预算内` 2.973s。隔离 job 8 分钟超时（`.github/workflows/verify.yml:378`）内收编，分片 XML 中无这 9 个类（见 §六.3）。

### 3.2 Rhino eval isolated（18 类 214 条，全绿）

- job `139929803401` `success`，335s；XML 产物 `test-results-rhino-eval`（16791 B）逐 suite 求和：214 tests / 0 failures / 0 errors / 0 skipped。
- 18 个 suite 含 `JsSourceConfigTest` 40、`AutoTaskCoreTest` 26、`JsEngineCapabilitiesTest` 25、`CryptoJsCompatibilityTest` 21、`SourceCompatibilityTest` 12、`ReviewRuleParserTest` 12、`JsTest` 11、`JsSourceEngineTest` 11、`SafeEditorResultCodecTest` 10、`BookExportFileNameTest` 9、`RssSortUrlsTest` 8、`McpServiceContractTest` 8 等。
- 时长口径（XML `time` 属性实读）：`CryptoJsCompatibilityTest` suite 1.526s / 21 例（单例最长 0.207s）；全 job 单例最长 `SourceCompatibilityTest::jsEncodeOverloadsRemainCallableInsideWithAndEvalScopes` 0.520s。分片 XML 中无这 18 个类（见 §六.3）。

### 3.3 隔离结论

- bench 与 Rhino 求值类本轮均在独立 job 内真收编且全绿，二者不是 shard 2 挂死源；阻塞点仍残留在普通分片用例集合内（§五）。

## 四、四个分片结果（用时 / 条数 / 结论）

| shard | job id | 结论 | 起止（UTC） | 耗时 | XML 数 | tests | fail / err / skip |
| --- | --- | --- | --- | --- | --- | --- | --- |
| shard 1/4 | `139932664534` | `success` | 13:11:30 → 13:16:06 | 276s | 151 | 830 | 0 / 0 / 0 |
| shard 2/4 | `139932664526` | `cancelled` | 13:11:30 → 13:56:44 | 2714s | 0（无产物） | 570（仅 STARTED 计数） | 1 FAILED，余不可取证 |
| shard 3/4 | `139932664487` | `success` | 13:11:30 → 13:16:02 | 272s | 148 | 922 | 0 / 0 / 0 |
| shard 4/4 | `139932664874` | `success` | 13:11:30 → 13:17:18 | 348s | 150 | 827 | 0 / 0 / 0 |

### 4.1 shard 1 老病复核：未复发

- shard 1 `BUILD SUCCESSFUL in 3m 46s`，有完整 JUnit XML 产物，未出现 Q-253「45 分钟静默无 XML」形态。
- STARTED 日志生效取证（`gradle-shard-1.log`）：STARTED 总行数 **830**，与 XML tests 数 830 相等；FAILED 行数 **0**。前三条 STARTED 原文：

```
2026-11-26T13:15:56.7745185Z BundlePayloadContractTest > bookshelf single-path entry sources exist and stay readable STARTED
2026-11-26T13:15:56.7774583Z BundlePayloadContractTest > migrated recycler payloads use platform Bundle STARTED
2026-11-26T13:15:56.7834639Z BundlePayloadContractTest > compose list hosts do not leak androidx bundleOf STARTED
```

- 日志配置回源：`app/build.gradle:239-242` `testTask.testLogging { events "STARTED", "FAILED"; showStandardStreams = false }`；分片命令 `.github/workflows/verify.yml:292` 经 `-PexcludeRhino -PexcludeSlowBench` 排除两个隔离分类。

### 4.2 shard 2 唯一失败用例

- 日志行 1627：`ImportSourceGroupTest > displayNameFoldsWhitespaceAndTruncates FAILED`，下一行 `org.junit.ComparisonFailure at ImportSourceGroupTest.kt:71`。
- tee 日志未带 expected / actual 文本（`showStandardStreams = false`）；shard 2 被取消，失败专用 XML 兜底步骤未触发（§五.4），断言细节无法从产物回捞。
- 该断言失败发生在挂死之前；JUnit 单断言失败不中断 worker，静默窗内被卡住的是其后用例，失败本身不是挂死因。

## 五、shard 2 挂死取证（第六次同型超时，阻塞点落在 shard 2）

### 5.1 时间线与静默窗

- job `139932664526`：`13:11:30Z → 13:56:44Z`，2714s（45m14s），命中远端树 `.github/workflows/verify.yml:214` `timeout-minutes: 45` 兜底超时（ff9c20c / e8ce4a8 两棵远端树均为 45 分钟；15 分钟收口到 `49cc659` 树才生效，见 §十.2）。
- 测试任务 `> Task :app:testAppDebugUnitTest` 起于 13:16:27.666Z；首条 STARTED 13:16:27.669Z（`ExampleUnitTest > addition_isCorrect`）。
- 最后一条 STARTED：`2026-11-26T13:16:32.6645095Z SearchHotBinderTest > removing last history word overlapping seed keeps fallback source marker STARTED`。
- 取消行：`2026-11-26T13:56:42.8708798Z ##[error]The operation was canceled.`；静默窗 **2410.2s（40m10s）**，窗内无后续 STARTED、无 FAILED、无 tests completed 行。
- 清理期 `Cleaning up orphan processes` 终止：java pid 2487 / 3179 / 3374 / 3481 + tee pid 2488，与 Q-253 孤儿进程形态一致（上一轮为 java 2296 / 2988 / 3183 / 3329 + tee 2297）。

### 5.2 culprit 候选（最后一条 STARTED 且无后续 FAILED / PASSED）

- 候选用例原文：`SearchHotBinderTest > removing last history word overlapping seed keeps fallback source marker`，源码 `app/src/test/java/io/legado/app/ui/main/search/SearchHotBinderTest.kt:148-176`。
- 紧邻前一条 STARTED：`SearchCommandGateTest > stopInvalidatesQueuedCommandAndWaitsForRunningCommand`（`SearchCommandGateTest.kt:17-44`，四条 `CountDownLatch.await` 均带 5s / 100ms 超时上界）。
- 静态回源：候选用例为 `runBlocking { … }`，两处 `state.first { … }` 均包 `withTimeout(5_000)`（`SearchHotBinderTest.kt:164`、`:171`）；若谓词永不命中，5s 内应抛 `TimeoutCancellationException` 转 FAILED，而非静默 40 分钟。实测 40m 不退出，说明超时护栏未能打断阻塞点——可能阻塞发生在 `withTimeout` 作用域之外的同步调用，或收集协程在 `Dispatchers.Unconfined` 下未被取消；无线程栈，不能定论。
- 取证限制：本轮 `testLogging` 只开 `STARTED` / `FAILED`，未开 PASSED；日志无法区分「前一条已通过」与「前一条卡住、下一条 STARTED 已打印」。按 Q-261 取证方案，最后一条 STARTED 即标准 culprit 落点，候选结论待线程栈复核。

### 5.3 STARTED 计数与清单对照

- shard 2 清单 161 类；日志 STARTED 共 **570** 条，FAILED 1 条；对比 shard 1 830、shard 3 922、shard 4 827，shard 2 在跑到清单后部时被卡住。

### 5.4 产物缺口

- artifacts 列表无 `test-results-shard-2`，也无 `test-results-shard-2-failed`。失败专用上传步骤 `.github/workflows/verify.yml:335-340` 条件为 `if: failure()`；job 以 `cancelled` 结束，该步骤不触发，阻塞用例 XML 与断言 expected/actual 均未上传。

## 六、Aggregate 基线对账（3185）

### 6.1 已下载 XML 自行求和

| 来源 | XML 数 | tests | failures | errors | skipped | 类数 |
| --- | --- | --- | --- | --- | --- | --- |
| shard 1 | 151 | 830 | 0 | 0 | 0 | 151 |
| shard 3 | 148 | 922 | 0 | 0 | 0 | 148 |
| shard 4 | 150 | 827 | 0 | 0 | 0 | 150 |
| rhino-eval | 18 | 214 | 0 | 0 | 0 | 18 |
| slow-bench | 9 | 23 | 0 | 0 | 0 | 9 |
| **合计** | **476** | **2816** | **0** | **0** | **0** | **476** |

- 基线口径：`.github/workflows/verify.yml:637` `BASELINE = 3185`；求和与断言脚本在 `:630-699`。
- 已下载集合较基线少 369 条，但差额全部落在无 XML 的 shard 2 上；shard 2 日志显示 570 条已 STARTED，其完整条数未知，**不能据此判漏测，也不能判全绿**。

### 6.2 dup 去重判定

- 按 Aggregate 同口径（`:662-667`，跨 job 同 `classname` 判重复执行）对 5 份 XML 比对：**dup 0 条**；476 个 classname 分属唯一 job。
- 隔离泄漏复核：9 个 bench 类、18 个 Rhino 类均未出现在 shard 1 / 3 / 4 的 XML testcase 中，分类排除在分片侧生效。
- CI 侧 Aggregate job `139958112725` `skipped`（上游 shard 2 cancelled），其 dup 判定、清单外类判定（`:676-685`）、failures/errors/skipped 硬断言（`:686-689`）与基线差额断言（`:690-691`）均未执行；§6.1 / §6.2 为本单只读复算，不替代 CI 汇总结论。

### 6.3 最终对账结论

- **基线 3185 本轮未保住对账：Aggregate 跳过，全量条数不可得；已下载 2816 条 0 失败 0 错误 0 跳过、跨 job 无重复，仅为部分分片 + 两个隔离 job 的局部和。**

## 七、断言口径核对：`displayNameFoldsWhitespaceAndTruncates`

### 7.1 ff9c20c 轮：仍红（旧期望）

- 远端 blob `ff9c20c` 树 `ImportSourceGroupTest.kt:71`：`assertEquals("a  b", importGroupDisplayName("a\n\t  b"))`（双空格旧期望）。
- 源码口径 `app/src/main/java/io/legado/app/ui/main/bookshelf/ImportSourceGroup.kt:65-66`：`replace(Regex("\\s+"), " ").trim().take(40)`，连续空白折叠为单空格；输入 `a\n\t  b` 产物应为 `"a b"`。断言期望写错，源码无缺陷（口径回执见 `docs/修复-分组显示名断言口径.md`）。
- 云端表现：shard 2 该用例 `ComparisonFailure at ImportSourceGroupTest.kt:71`（§4.2）。

### 7.2 e8ce4a8 轮：修复未获云端回证

- 远端 blob `e8ce4a8` 树 `ImportSourceGroupTest.kt:73`：`assertEquals("a b", importGroupDisplayName("a\n\t  b"))`（单空格新期望）。
- 但其 Verify run `45826704483` 结论 `cancelled`，jobs API 返回 **0 个 job**；run `run_started_at` 13:11:19Z，`updated_at` 13:46:02Z，`runner_id` 为空，未分到 runner、未进入分片阶段。断言修复在该轮**没有被任何云端用例执行**，转绿与否无法从该轮证据判定。
- 旁证：同一 e8ce4a8 的 Screenshot run `45826704472` `success`，与 Verify 车道无关。
- 在飞旁车：最新 Verify run `45833477069`（head `49cc659`）远端树 `:73` 已为 `"a b"`，其分片结果可作后续回证；不在本单取证范围，记入交接节。

## 八、二阶 / 三阶影响与取舍

- 一阶：bench / Rhino 隔离 job 本轮全绿且无泄漏，证明 Q-261 的分类收编在「跑得到」层面成立；挂死不在两个隔离集合内，阻塞源是普通分片用例。
- 二阶：挂死落点在 shard 1 ↔ shard 2 之间漂移（Q-253 shard 1，本轮 shard 2），按类名固定嫌疑会误判；STARTED 日志本轮首次在绿分片（shard 1 830 条）验证可用，下一轮挂死分片可直接用末条 STARTED 定位。
- 三阶：`cancelled` 不触发 `if: failure()` 诊断 / XML 上传，超时挂死轮的证据链比普通失败轮更薄；若不改兜底条件，后续每次挂死都只能拿到 tee 里的 STARTED 序列，拿不到阻塞用例 XML 与线程栈。
- 取舍：本单不替未执行的 Aggregate 判基线达标；不把候选 culprit 写成已证实根因；不为取证去改工作流或测试代码（本单只读）。

## 九、自查反例、改坏推演与边界

1. **会不会 bench / Rhino 仍是挂死源，只是隔离 job 碰巧跑得快？** 反证：两个隔离 job 0 失败收编，且其 9 + 18 个类在分片 XML 中零出现（§6.2）；分片命令第 292 行带 `-PexcludeRhino -PexcludeSlowBench`，阻塞类不在隔离集合内。
2. **会不会 shard 2 是被那条 `ComparisonFailure` 断言红拖死？** 反例保留：断言失败行 13:16:32.567，末条 STARTED 13:16:32.664，静默窗在其后；JUnit 单断言失败不中断 worker，阻塞点另在后续用例。
3. **会不会 2816 条已足以说明基线达标？** 不能：shard 2 整份 XML 缺失，570 条已 STARTED 用例的通过 / 失败 / 条数均未知；Aggregate 未运行，dup 与清单外类判定也是本单复算而非 CI 结论。
4. **e8ce4a8 被取消会不会是测试编译红或断言红？** 反证：run 0 job、runner_id 为空、无编译日志；形态是排队 / 调度阶段取消，不是测试失败。取消诱因与同 ref 后续连推有关（`concurrency.group: verify-${{ github.ref }}`，`.github/workflows/verify.yml:22-27`，`cancel-in-progress: false`），具体取消触发事件云端未给明细，记为证据缺口。
5. **候选 `SearchHotBinderTest` 会不会其实已通过、卡住的是更前一条？** 有可能：日志无 PASSED 事件，顺序上只能确认「末条 STARTED 之后无进展」；`SearchCommandGateTest` 的 latch 均带超时上界，静态风险低于候选，但不能排除。
6. **改坏推演：** 若下一轮为消挂死而把 bench / Rhino 分类标签再扩大，可能把普通慢用例也挪进 8 分钟隔离 job，反而把超时红面扩大；隔离集与分片集必须保持互斥，并集等于 discover 全量清单（口径见 `:276-279` 注释）。

## 十、新发现问题（2026-11-26，Q-266）

1. shard 2 同型超时挂死复发（2714s / 45m14s，静默 2410.2s，4 java 孤儿 + 1 tee，无 XML），末条 STARTED 候选 `SearchHotBinderTest.kt:148` `removing last history word overlapping seed keeps fallback source marker` —— **已入队**：下一轮挂死分片取 worker 线程转储 / jstack 复核候选；评估给普通分片用例补 JUnit `@Test(timeout=…)` 护栏，先核对 `withTimeout` 作用域是否覆盖阻塞调用。
2. 15 分钟超时收口在 ff9c20c / e8ce4a8 两棵远端树尚未生效（两树 `.github/workflows/verify.yml:214` 均为 `timeout-minutes: 45`）；远端最新 `49cc659` 树该值已为 15（`:185`）。挂死轮取证时延在这两轮仍维持 45 分钟量级 —— **待裁**：确认 15 分钟超时随后续 Verify 轮生效后，挂死轮等待窗是否同步压到 15 分钟。
3. `cancelled` 不触发 `if: failure()` 的诊断步骤与失败 XML 上传（`.github/workflows/verify.yml:295`、`:335`），超时挂死轮拿不到阻塞用例 XML、expected/actual 与堆栈 —— **已入队**：评估把诊断 / XML 兜底条件放宽为 `if: always()` 或单独覆盖 `cancelled`，并在超时步骤保留线程转储。
4. e8ce4a8 Verify `45826704483` 排队阶段被取消、0 job，断言口径修复（`ImportSourceGroupTest.kt:73` `"a b"`）未获云端回证 —— **待裁**：在在飞 run `45833477069`（`49cc659`）分片结果中复核该用例转绿；若该轮再被连推取消，需另起一轮干净 Verify 回证。
5. bench 隔离 job 内 `CrossingBenchTest` suite 7.05s / 5 例、单例最长 2.973s，为重载最重类 —— **判废**：不再拆分；隔离 job 8 分钟超时已兜住，分片侧零泄漏，拆分会增加分类维护面。
6. 分类隔离有效性复核通过：9 bench 类 + 18 Rhino 类未泄漏进分片 XML，跨 job dup 0 条 —— **已闭环**：本轮复算确认，后续轮次由 Aggregate 脚本同口径自动判定。

## 十一、未验证项与证据缺口

- e8ce4a8 断言转绿：云端无执行证据；本单不判绿，待在飞 run 或后续干净 Verify 回证。
- shard 2 阻塞用例：末条 STARTED 为候选，非已证实根因；无线程栈、无阻塞调用点、无崩溃堆栈。
- shard 2 失败断言的 expected / actual 文本：tee 未输出、XML 未上传，仅有 `ComparisonFailure at ImportSourceGroupTest.kt:71` 一行。
- Aggregate 的 CI 侧求和 / dup / 清单外类 / 基线差额断言均未执行；§六为本单只读复算。
- e8ce4a8 run 被取消的具体触发事件（排队 supersession 还是人工取消）云端事件流未取到明细。
- 沙箱无 JDK，未本地编译、未跑单测；所有红绿以云端 jobs / artifacts / XML 为准。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-验收代理必读.md:62`：「HEAD commit / CI 绿红（含单测全绿）」
2. `docs/机制-总控必读.md:108`：「回源（回源成本 ≪ 记忆成本）」
3. `docs/机制-验收代理必读.md:24`：「自己写脚本比对（例：逐块逐字节比对原文与重排产物）」
- 适用条款：F-01 两布尔回报（HEAD commit / CI 绿红含单测全绿，不报 APK、不报百分比）；验收独立取数、数量结论给命令与真实输出；锚点强制 `文件:行` 或 commit sha 可回源；只读核验，不改 `app/` 与 `data_scripts/`、不做 git 写、不本地编译；证据不足处写明缺口，不替 CI 编结论。

## 附：机检输出

```
$ python3 data_scripts/check-report-fields.py docs/CI核验-ff9c20c-e8ce4a8.md
$ python3 data_scripts/check-onboarding.py   # 直跑 check_report()
$ python3 data_scripts/check-anchor-card.py --strict docs/CI核验-ff9c20c-e8ce4a8.md
$ python3 data_scripts/check-problem-finding.py docs/CI核验-ff9c20c-e8ce4a8.md
```

- XML 计数复核（Python `xml.etree` 解析已下载产物）：shard1 830 / shard3 922 / shard4 827 / rhino 214 / bench 23，合计 2816，0 failures 0 errors 0 skipped；shard2 无产物，570 条 STARTED 仅日志计数。
