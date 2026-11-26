# CI 核验报告 · commit 52a2ece（Verify 45816690884 编译红；续核 7530b9ab Verify 45818022768）

锚点卡：docs/锚点/Q253-CI核验.md

- **首行布尔：52a2ece Verify = 红（run conclusion `failure`，测试源码编译错误，4 分片与 Aggregate 均未执行）；分类隔离配置期不再崩；续核 7530b9ab = `cancelled`（shard1 45 分钟超时挂死，Aggregate 跳过）。**
- 核验仓库：`bqdlz676/legado`（本地路径 `/home/user/Doubao/chats/38454122521460994/legado`）
- 被核 commit：短号 `52a2ece`，全 sha `52a2ece4665bbeb57736832ba27f4acc1e5b668a`；跟随修复轮 `7530b9ab`（字符串模板加花括号）。
- 关联 run：Verify `45816690884`、Screenshot `45816690773`、Package APK `45817502939`；续核 Verify `45818022768`。
- 核验时间：2026-11-26（Asia/Shanghai）；CI 时间戳均为 UTC。
- 核验方式：GitHub REST API 取 run / jobs / artifacts / job 日志；JUnit XML 下载后用 Python `xml.etree` 统计 tests / failures / errors / skipped 与 classname；源码行号以远端 blob 与工作树回源。全程只读：未编译、未跑本地单测、未改 `app/` 与 `data_scripts/`、无 git 写操作。

## 一、总结论（布尔）

- **52a2ece Verify `45816690884`：红（`failure`）。** 红因是测试源码 `ImportSourceGroupTest.kt:63` 字符串模板缺花括号，编译期 `Unresolved reference`；Discover 与 Rhino isolated 两个 job 同红，shard 矩阵与 Aggregate 被上游跳过，无任何分片测试结论。
- **分类隔离配置期：未再崩。** 本轮日志无 `No signature of method AndroidUnitTest.includeCategories`；`useJUnit { }` 闭包（`app/build.gradle:216-227`）通过配置阶段，构建进入编译任务。
- **52a2ece Rhino isolated job：未收到可执行用例。** 测试任务 `:app:testAppDebugUnitTest` 未启动，0 份 XML，4 个标注类本轮无一被求值；不能据此判断隔离 job 的用例红绿。
- **续核 7530b9ab Verify `45818022768`：`cancelled`。** 编译解锁后 Discover 成功枚举 643 类；Rhino isolated 成功跑出 4 类 60 条；shard 2/3/4 全绿；shard1 命中 45 分钟超时被取消，Aggregate 跳过，总数无法对账基线。
- 进度口径按 F-01 两布尔：HEAD commit `52a2ece4665bbeb57736832ba27f4acc1e5b668a`（origin/main）；CI 红（含单测：分片结论缺失）。不报 APK、不报百分比。

## 二、52a2ece 终态表（jobs API 回源）

| job id | 名称 | 结论 | 起止（UTC） | 耗时 |
| --- | --- | --- | --- | --- |
| `139898135377` | Discover test classes (shard lists) | `failure` | 12:07:18 → 12:12:04 | 286s |
| `139898136123` | Rhino eval tests (isolated) | `failure` | 12:07:17 → 12:12:28 | 311s |
| `139898138342` | Baseline full unit tests (single job) | `skipped` | 12:07:15 → 12:07:15 | — |
| `139900674138` | Unit tests shard 1/4（矩阵占位） | `skipped` | 12:12:04 → 12:12:04 | — |
| `139900888771` | Aggregate & assert test totals | `skipped` | 12:12:29 → 12:12:28 | — |

- 平行车道：Screenshot `45816690773` = `success`；Package APK `45817502939` = `skipped`。
- artifacts：`45816690884` 产物总数 **0**（编译失败前无测试 XML；Rhino 失败专用上传步骤告警 `No files were found … No artifacts will be uploaded`）。

## 三、Rhino isolated job 四件事取证（52a2ece 轮）

### 3.1 配置期是否还崩

- 结论：**未崩在配置期。** Rhino 日志全文 grep `No signature` / `includeCategories` / `excludeCategories` / `Could not find method` 零命中；任务序列推进到 `:app:compileAppDebugKotlin`（`12:09:25Z`）、`:app:kspAppDebugUnitTestKotlin`（`12:11:51Z`）、`:app:compileAppDebugUnitTestKotlin`（`12:12:25Z FAILED`）。
- 对照上一轮（`45810466813`）红因是 `AndroidUnitTest.includeCategories()` 无签名；现行配置 `app/build.gradle:216-227` 改为 `testOptions.unitTests.all { testTask -> testTask.useJUnit { includeCategories / excludeCategories … } }`，分类过滤走 JUnitOptions，配置阶段通过。

### 3.2 该 job 是否真收到 4 个类

- 结论：**未收到、未求值。** 测试任务未启动，无 testsuite、无 tests 数、0 XML。4 个 `@Category(RhinoEvalTest)` 标注类（本地回源）：
  - `app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt:12`
  - `app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt:13`
  - `app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt:22`
  - `app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt:23`
- 该轮无法从 XML 核对类名与条数；有效性核对放到续核 7530b9ab（§五）。

### 3.3 四个分片是否正常跑起来

- 结论：**分片未启动。** Discover 编译失败，shard 矩阵 job `139900674138` 直接 `skipped`；不存在 shard 2 用时，也不存在分片 XML。
- 本轮不构成对「`-PexcludeRhino` 生效后 shard 2 是否仍 45 分钟挂死」的取证；该问题在续核轮观察（§五.3）。

### 3.4 aggregate 总数是否保住

- 结论：**未求和。** Aggregate `139900888771` `skipped`；基线 `verify.yml:532` `BASELINE = 3185` 本轮无对账输入。

## 四、首个失败用例与 culprit 类别（52a2ece）

### 4.1 红牌原文（Discover 日志行 495 / Rhino 日志行 477，各 1 条 `e:`）

```
e: file:///home/runner/work/legado/legado/app/src/test/java/io/legado/app/ui/main/bookshelf/ImportSourceGroupTest.kt:63:45 Unresolved reference 'IMPORT_GROUP_NAME_PREFIXothertag'.
```

### 4.2 根因（源码回源）

- `ImportSourceGroupTest.kt:63`：`assertFalse(matchesImportGroupTag("$IMPORT_GROUP_NAME_PREFIXothertag name", tag))`。
- Kotlin 字符串模板 `$IDENTIFIER` 会把后续字母数字一并吃进标识符，`$IMPORT_GROUP_NAME_PREFIXothertag` 被解析成单个标识符，编译器在常量 `IMPORT_GROUP_NAME_PREFIX` 上找不到拼接 `othertag` 的写法；正确写法是 `${IMPORT_GROUP_NAME_PREFIX}othertag name`。
- 符号本体存在且可解析：`ImportSourceGroup.kt:15` `internal const val IMPORT_GROUP_NAME_PREFIX = "导入·"`。
- **culprit 类别：测试源码 Kotlin 字符串模板插值缺花括号，编译期 unresolved reference。** 不是运行时断言失败，不是 Rhino 求值阻塞，不是分类过滤器签名问题，也不是本轮 terrain 只读面重构 / evict 台账修复 / 缓存 4 用例引入。

### 4.3 跟随修复

- `7530b9ab` `fix(test): 字符串模板加花括号（$IMPORT_GROUP_NAME_PREFIXothertag 被解析成单个标识符，远端 compileAppDebugUnitTestKotlin 报 unresolved）`，补丁仅改 `ImportSourceGroupTest.kt:63` 一行。

## 五、续核 7530b9ab（Verify `45818022768`）取证

### 5.1 Discover

- job `139902682957` `success`，`12:15:55Z → 12:19:32Z`（217s），`BUILD SUCCESSFUL in 3m`；清单 `测试类总数=643，分片大小=[161, 161, 161, 160]`。编译解锁确认。

### 5.2 Rhino isolated（4 类 60 条，全绿）

- job `139902683458` `success`，`12:15:55Z → 12:20:16Z`（261s），`BUILD SUCCESSFUL in 3m 35s`。XML 产物 `test-results-rhino-eval`（3264 B）逐 suite 求和：

| testsuite | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| `io.legado.app.RhinoScriptErrorContextTest` | 4 | 0 | 0 | 0 |
| `io.legado.app.help.source.RssSortUrlsTest` | 8 | 0 | 0 | 0 |
| `io.legado.app.model.jsSource.JsSourceConfigTest` | 40 | 0 | 0 | 0 |
| `io.legado.app.web.mcp.McpServiceContractTest` | 8 | 0 | 0 | 0 |
| **合计** | **60** | **0** | **0** | **0** |

- 隔离有效性：shard2/3/4 已下载 XML 中无上述 4 个类的 testcase；`RhinoJsonInitializationTest`（出现在 shard2）未标注 `@Category(RhinoEvalTest)`，不属隔离集合。

### 5.3 四个分片结果

| shard | job id | 结论 | 起止（UTC） | 耗时 | tests | XML 数 | fail / err / skip |
| --- | --- | --- | --- | --- | --- | --- | --- |
| shard 2/4 | `139904626762` | `success` | 12:19:35 → 12:24:03 | 268s | 957 | 155 | 0 / 0 / 0 |
| shard 3/4 | `139904626807` | `success` | 12:19:35 → 12:24:54 | 319s | 864 | 152 | 0 / 0 / 0 |
| shard 4/4 | `139904626650` | `success` | 12:19:35 → 12:24:03 | 268s | 858 | 157 | 0 / 0 / 0 |
| shard 1/4 | `139904626808` | `cancelled` | 12:19:35 → 13:04:51 | 2716s（45m16s） | 无 XML | 0 | 不可取证 |

- **shard 2 本轮用时 268s、有 XML（957 条），未复现前四轮 45 分钟挂死。** 前四轮挂死均落在 shard2；本轮阻塞点迁移到 shard1（见 §六）。
- Aggregate `139929014134` `skipped`（上游 shard1 cancelled）。

### 5.4 已出 XML 合计与基线对账

- 已执行 XML：Rhino 60 + shard2 957 + shard3 864 + shard4 858 = **2739** tests，0 failures / 0 errors / 0 skipped。
- shard1（161 类）无 XML，条数缺失；基线 `verify.yml:532` = 3185，**本轮总数无法对账，差额含 shard1 全部用例，不能判漏测也不能判全绿**。

## 六、shard1 挂死取证（续核轮，第五次同型超时）

- job `139904626808`：`12:19:35Z → 13:04:51Z`，2716s，命中 `verify.yml:214` shard job `timeout-minutes: 45` 兜底超时。
- 测试窗：`:app:testAppDebugUnitTest` `12:24:16.532Z` 启动 → `13:04:48.553Z` `##[error]The operation was canceled.`，静默窗 **2432s（40m32s）**，窗内无后续逐用例进度、无 `tests completed` 行。
- 清理期 `Cleaning up orphan processes`：终止 java pid 2296 / 2988 / 3183 / 3329 + tee pid 2297，与前几轮孤儿进程形态一致。
- 首条且唯一失败行：`ImportSourceGroupTest > displayNameFoldsWhitespaceAndTruncates FAILED`，`org.junit.ComparisonFailure at ImportSourceGroupTest.kt:71`。
  - 断言行 `:71`：`assertEquals("a  b", importGroupDisplayName("a\n\t  b"))`。
  - 源 `ImportSourceGroup.kt:66`：`key.replace(Regex("\\s+"), " ").trim().take(40)`，把连续空白折叠为**单**空格；输入 `a\n\t  b` 期望产物应为 `a b`，测试期望 `a  b`（双空格），口径分歧。
  - 该断言失败发生在挂死之前；失败本身不会阻塞 worker，静默窗内被卡住的是其后某个未在 tee 日志留名的用例，阻塞用例名属证据缺口。
- 静态嫌疑（不等同 CI 证实）：shard1 清单 161 类中含 `BubbleBenchmarkTest`、`CronetInitializationContractTest`、`Socks5ProtocolTest`、`PersonPreloadTest`、`CryptoJsCompatibilityTest`、`SourceVerificationHelpTest` 等可能涉及网络 / 初始化 / 压测的类；需下一轮逐用例日志或线程栈定位。

## 七、二阶 / 三阶影响与取舍

- 一阶：52a2ece 编译红同时打红 Discover 与 Rhino isolated——两者共享同一 `testAppDebugUnitTestKotlin` 源集，任一测试类编译错误都会让隔离 job 连带红，即使分类配置本身正确。归因时须先区分「编译红」与「测试红」。
- 二阶：Discover 是分片矩阵的 `needs` 上游，编译失败使 4 个分片不启动；Aggregate 又 `needs` 全部分片与 Rhino，因此整条汇总链被跳过，红灯只停在编译层，不产生分片测试结论。
- 三阶：分类隔离机制本轮在续核轮验证有效——4 个标注类只在 Rhino job 求值、分片 XML 无重复；但挂死并未被隔离消灭，只是从 shard2 迁移到 shard1，说明阻塞源不全在 Rhino 标注集合内，仍有非标注的慢 / 阻塞用例残留在分片。
- 取舍：隔离 job 8 分钟超时（`verify.yml:376`）只罩标注类；分片 45 分钟超时（`verify.yml:214`）对非标注阻塞类只能兜底取消，不能杀线程、不能定位用例。

## 八、自查反例、改坏推演与边界

1. **会不会是 52a2ece 的 terrain 重构 / evict 修复 / 缓存用例引入编译红？** 反证：唯一 `e:` 指向 `ImportSourceGroupTest.kt:63`，与 terrain 包无关；编译错误为符号解析失败，非类型不匹配。跟随修复 `7530b9ab` 也只改该测试文件一行。
2. **会不会是分类配置又在配置期崩？** 反证：Rhino 日志任务序列越过配置阶段进入 main 编译与单测编译；无 `No signature` 行；续核轮 Rhino job 成功跑出 60 条。
3. **shard1 挂死会不会由 `:71` 断言失败直接导致？** 反例保留：JUnit 单断言失败不中断 worker；静默窗在失败行之后，阻塞点另在其后用例，证据不足以下结论到具体类。
4. **已出 2739 条能否代替全量对账？** 不能：shard1 161 类无 XML，差额不可拆分；Aggregate 未运行，重复执行 / 清单外类判定均未执行。
5. **`RhinoJsonInitializationTest` 出现在 shard2 是否算隔离泄漏？** 不算：该类无 `@Category(RhinoEvalTest)` 标注，属普通分片用例。

## 九、新发现问题（2026-11-26，Q-253）

1. `ImportSourceGroupTest.kt:71` 断言与源折叠口径分歧——源 `ImportSourceGroup.kt:66` 折叠为单空格，测试期望双空格 `a  b`，`ComparisonFailure` —— 待裁：确认以「单空格折叠」为准改测试期望，还是改源保留双空格；修复前该用例在 shard1 持续红。
2. 分片阻塞挂死迁移到 shard1（第五次同型：45m16s 超时、2432s 静默窗、4 java 孤儿、无 XML）—— 已入队：下一轮取 shard1 逐用例日志 / 线程栈，在静态嫌疑类（benchmark / cronet / socks5 / preload / crypto）中定位阻塞点；隔离标注集合未覆盖该阻塞源。
3. 编译失败使 Discover 与 Rhino job 连带红、整条分片链跳过，红灯归因易被误读为隔离配置问题 —— 已闭环：本轮已区分编译红与测试红，配置期 `useJUnit` 路径验证通过。
4. 隔离 job 与分片共享测试源集，测试源码编译错误会同时拖红两侧 —— 判废：不为隔离 job 单独拆源集；保留共享编译，靠 Discover 前置编译尽早暴露。

## 十、未验证项与证据缺口

- 52a2ece 分片 / Aggregate 未执行，无分片测试红绿结论；本单不替其判绿。
- shard1 阻塞用例名、阻塞线程栈、`ComparisonFailure` 的 expected/actual 文本均未进 tee 日志，XML 未上传，无法回捞；需下一轮保留失败 XML 或 worker 线程转储。
- 续核轮总数 2739 为部分分片 + Rhino 之和，非全量；基线 3185 对账未完成。
- 沙箱无 JDK，未本地编译、未跑单测；所有红绿以云端 jobs / XML 为准。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-验收代理必读.md:62`：「HEAD commit / CI 绿红（含单测全绿）」
2. `docs/机制-总控必读.md:108`：「回源（回源成本 ≪ 记忆成本）」
3. `docs/机制-验收代理必读.md:24`：「自己写脚本比对（例：逐块逐字节比对原文与重排产物）」
- 适用条款：F-01 两布尔回报（HEAD commit / CI 绿红含单测全绿，不报 APK、不报百分比）；验收独立取数、数量结论给命令与真实输出；锚点强制 `文件:行` 或 commit sha 可回源；只读分析，不改 `app/` 与 `data_scripts/`、不做 git 写、不本地编译；证据不足处写明缺口，不替 CI 编结论。

## 附：机检输出

```
$ python3 data_scripts/check-onboarding.py   # 直跑 check_report()
ONBOARD: PASS（probs: []）

$ python3 data_scripts/check-anchor-card.py --strict docs/CI核验-52a2ece.md
汇总: PASS=1 FAIL=0（共 1 份）
RC=0

$ python3 data_scripts/check-problem-finding.py <报告绝对路径>
PROBLEM-FINDING: PASS（1 份报告均带新发现问题与处置状态）
RC=0
```

- XML 计数复核（Python `xml.etree` 解析已下载产物）：Rhino 60 / shard2 957 / shard3 864 / shard4 858，合计 2739，0 failures 0 errors 0 skipped；shard1 无产物。
