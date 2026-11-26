# 修复 · Rhino 测试隔离（Q-246）

> 边界：本单只做 Rhino 阻塞求值类测试的 CI 隔离——新建分类标记、给四个既有测试类打标、`app/build.gradle` 加分类属性分支、`.github/workflows/verify.yml` 分片排除 + 独立 `rhino-eval-tests` job + aggregate 纳入新产物。未改任何测试断言、未改业务代码；未 git 写、未本地编译，仅跑 `kotlin-syntax-lint.py`、`check-workflow-contracts.py` 与文档门禁。
> 锚点卡：docs/锚点/Q246-Rhino隔离.md。

## 一、任务与背景

- 前序定位（Q-243 结论，本单沿用，不重复排查）：shard 2 挂死 30 分钟以上，无单一类可摘；风险集中在 4 个 Rhino 求值测试类。Rhino eval/compile 是同步阻塞调用，忽略线程 interrupt；JUnit `@Test(timeout=)` 只能在 runner 层判失败，不能真正杀掉阻塞在 Rhino 内的线程，这类测试混进分片会把 test worker 拖死。
- 本单动作是**隔离而不是继续加超时**：4 分片排除该分类；新增独立 job 只跑该分类、`timeout-minutes: 8`，失败即红但不拖累分片；aggregate 把新 job 的 JUnit XML 一并汇总，断言总数不变（基线 3185 不掉）。
- 四个待隔离类（磁盘现状路径）：
  - `app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt`
  - `app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt`
  - `app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt`
  - `app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt`

## 二、标记机制选型：JUnit4 `@Category`（不用自定义密封接口）

- 现状依据：`gradle/libs.versions.toml:212` `junit = { module = "junit:junit", version = "4.13.2" }`；四个类均 `import org.junit.Test`（JUnit4），仓内无 JUnit5 平台依赖。
- 选型：新建空接口 `RhinoEvalTest`，类上打 `@Category(RhinoEvalTest::class)`；Gradle Test 任务用原生 `excludeCategories` / `includeCategories` 识别 JUnit4 分类。
- 为什么不用自定义密封接口：Gradle 分类 DSL 只认 JUnit4 `@Category`（或 JUnit5 `@Tag`）；自定义接口不被 `excludeCategories`/`includeCategories` 识别，得自写测试过滤器，等于在 CI 层另造一套约定，维护成本更高。JUnit5 `@Tag` 不适用——本仓测试不是 JUnit5 平台。

## 三、改动清单（文件:行 + before/after）

### 3.1 新建分类标记

- 新建 `app/src/test/java/io/legado/app/RhinoEvalTest.kt`（17 行），`:17` `interface RhinoEvalTest`；文件头注释写清隔离原因、执行位置与选型理由。

### 3.2 四个既有测试类打标（断言一字未改）

- `app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt`
  - `:3` 新增 `import io.legado.app.RhinoEvalTest`；`:20` 新增 `import org.junit.experimental.categories.Category`；`:22` 新增 `@Category(RhinoEvalTest::class)`。
  - 求值入口证据：`:472` `@Test(timeout = 5_000)`、`:478` `JsSourceConfig.extract("while (true) {}", currentCoroutineContext())`——顶层脚本经 Rhino 编译/求值，死循环即挂死风险点。
- `app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt`
  - `:4` 新增 `import io.legado.app.RhinoEvalTest`；`:19` 新增 Category import；`:23` 新增 `@Category(RhinoEvalTest::class)`。
  - 求值入口证据：`:3` `import com.script.rhino.runScriptWithContext`；`:357` `val raw = runScriptWithContext { source.evalJS(js) }`。
- `app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt`
  - 同包 `io.legado.app`，无需 import 标记接口；`:10` 新增 Category import；`:12` 新增 `@Category(RhinoEvalTest::class)`。
  - 求值入口证据：`:26` `RhinoScriptEngine.eval(script, ScriptBindings())`、`:46` `evalSuspend`、`:64` `compile`、`:87` `compile(definitions).eval(scope)`。
- `app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt`
  - `:3` 新增 `import io.legado.app.RhinoEvalTest`；`:11` 新增 Category import；`:13` 新增 `@Category(RhinoEvalTest::class)`。
  - 求值路径证据：`:20` 起各用例 `@Test(timeout = 5_000)`，经 `parseRssSortUrls` / `resolveRssSortUrls` 的 `evaluateScript` 回调走脚本求值（主仓 `app/src/main/java/io/legado/app/help/source/RssSourceExtensions.kt:61` `evaluateScript(script)`）。

### 3.3 `app/build.gradle`：分类属性分支（`:222-229`）

```groovy
// 改前：tasks.withType(Test).configureEach { 仅挂 mockito javaagent，无分类过滤 }

// 改后（:222-229，接在既有 jvmArgumentProviders 之后）：
// Q-246：Rhino 阻塞求值类测试隔离。-PexcludeRhino 供 4 分片排除；
// -PincludeRhino 供专用 rhino-eval-tests job 只跑该分类。两者互斥，CI 命令行分别传入。
if (project.hasProperty('excludeRhino')) {
    excludeCategories 'io.legado.app.RhinoEvalTest'
}
if (project.hasProperty('includeRhino')) {
    includeCategories 'io.legado.app.RhinoEvalTest'
}
```

- 为什么走项目属性而不是写死在 test 任务：分片与独立 job 共用同一个 `:app:testAppDebugUnitTest` 任务；写死排除会把独立 job 也排空。属性由 CI 命令行分别传入，两条分支互斥。

### 3.4 `.github/workflows/verify.yml`

1. 分片命令排除分类（`:305-306` 注释、`:319` 命令）：

```yaml
# 改前
./gradlew :app:testAppDebugUnitTest --no-daemon --build-cache --stacktrace "${ARGS[@]}"

# 改后（:319）
./gradlew :app:testAppDebugUnitTest -PexcludeRhino --no-daemon --build-cache --stacktrace "${ARGS[@]}"
```

2. 新增独立 job `rhino-eval-tests`（`:372-491`）：
   - `:374` 与分片同口径的 baseline 模式开关；`:376` `timeout-minutes: 8`；
   - `:455` `./gradlew :app:testAppDebugUnitTest -PincludeRhino --no-daemon --build-cache --stacktrace`，不带 `--tests`，由 Gradle 分类过滤器选取；
   - `:478-483` 绿跑产物 `test-results-rhino-eval`；`:485-491` 失败兜底产物 `test-results-rhino-eval-failed`；
   - `:458-475` 失败诊断步骤（日志抓取 + annotation），口径与分片诊断一致。
3. aggregate 纳入新产物：
   - `:497` `needs: [discover, test-shard, rhino-eval-tests]`——Rhino job 失败会阻断汇总，红灯不漏；
   - `:513-517` 新增下载步骤，`pattern: test-results-rhino-*` 落到 `results/`；
   - `:542` 归组标签：`shard = m.group(1) if m else ('rhino' if 'rhino-eval' in x else '?')`，Rhino 产物在 `per_shard` 里记为 `rhino`，不再落到 `?`。

## 四、grep 回证（真实输出）

```
$ grep -n "@Category\|RhinoEvalTest\|import org.junit.experimental" \
  app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt \
  app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt \
  app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt \
  app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt
app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt:3:import io.legado.app.RhinoEvalTest
app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt:20:import org.junit.experimental.categories.Category
app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt:22:@Category(RhinoEvalTest::class)
app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt:4:import io.legado.app.RhinoEvalTest
app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt:19:import org.junit.experimental.categories.Category
app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt:23:@Category(RhinoEvalTest::class)
app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt:10:import org.junit.experimental.categories.Category
app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt:12:@Category(RhinoEvalTest::class)
app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt:3:import io.legado.app.RhinoEvalTest
app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt:11:import org.junit.experimental.categories.Category
app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt:13:@Category(RhinoEvalTest::class)

$ grep -n "excludeRhino\|includeRhino\|excludeCategories\|includeCategories" app/build.gradle
222:    // Q-246：Rhino 阻塞求值类测试隔离。-PexcludeRhino 供 4 分片排除；
223:    // -PincludeRhino 供专用 rhino-eval-tests job 只跑该分类。两者互斥，CI 命令行分别传入。
224:    if (project.hasProperty('excludeRhino')) {
225:        excludeCategories 'io.legado.app.RhinoEvalTest'
227:    if (project.hasProperty('includeRhino')) {
228:        includeCategories 'io.legado.app.RhinoEvalTest'

$ grep -n "excludeRhino\|includeRhino\|rhino-eval-tests\|test-results-rhino\|needs: \[discover\|Download Rhino\|'rhino' if" .github/workflows/verify.yml
319:          ./gradlew :app:testAppDebugUnitTest -PexcludeRhino --no-daemon --build-cache --stacktrace "${ARGS[@]}"
372:  rhino-eval-tests:
455:          ./gradlew :app:testAppDebugUnitTest -PincludeRhino --no-daemon --build-cache --stacktrace 2>&1 | tee gradle-rhino.log
481:          name: test-results-rhino-eval
489:          name: test-results-rhino-eval-failed
497:    needs: [discover, test-shard, rhino-eval-tests]
513:      - name: Download Rhino eval JUnit XML results
516:          pattern: test-results-rhino-*
542:              shard = m.group(1) if m else ('rhino' if 'rhino-eval' in x else '?')
```

改动行数（`git diff --numstat`，不含本单未触碰的既有改动）：

```
140  7  .github/workflows/verify.yml
  8  0  app/build.gradle
  2  0  app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt
  3  0  app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt
  3  0  app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt
  3  0  app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt
 新建  app/src/test/java/io/legado/app/RhinoEvalTest.kt（17 行）
```

## 五、aggregate 取新 job 产物与总数不变性论证

- 产物链：`rhino-eval-tests` 绿跑上传 `test-results-rhino-eval`（`:481`）；aggregate 第二步下载 `pattern: test-results-rhino-*`（`:516`），与分片 `test-results-shard-*`（`:509`）同落 `results/` 目录，Python 通配 `results/**/*.xml` 一并扫描（`:533`）。
- 总数不变：分片侧 `-PexcludeRhino` 不跑分类内用例，独立 job `-PincludeRhino` 跑同一批用例；两边并集 == 原全量，`tests=` 求和口径不变，基线 `BASELINE = 3185`（`:532`）断言不因此下调。
- 重复执行判定不误报：`:538-555` `owner` dict 按 `classname` 判跨 job 重复；分类互斥保证同一类不会同时出现在分片 XML 与 Rhino XML，`dup` 为空。
- manifest 交叉核对不撞车：discover 枚举清单 `manifest.json` 仍含这四个类（本单未改枚举逻辑）；Rhino XML 里的 classname 能在清单内找到，`:564-571` 的"报告类必须在清单内"校验自然通过。
- 失败兜底：Rhino job 失败时 `needs` 阻断 aggregate，`test-results-rhino-eval-failed` 留取证；aggregate 仅全绿时运行，失败产物不参与求和，与分片 `-failed` 产物口径一致。

## 六、二阶三阶影响

- 隔离后 Rhino 类真挂了谁发现：独立 `rhino-eval-tests` job 8 分钟超时兜底；job 红 → `needs` 阻断 aggregate → 整轮 Verify 红，诊断步骤 `:458-475` 抓失败用例名与日志尾段。不会再出现"挂死被分片 45 分钟兜底掩盖、分片间互相牵连"的情况。
- 总数校验会不会误判：
  - 若 Rhino job 产物漏下载（pattern 漂移），求和会少掉分类内用例数，触发 `total < BASELINE` 硬断言红——漏产物会被总数闸门抓住，不会静默掉数。
  - 若分类标记未被 Gradle 识别，独立 job 匹配到零个测试，Gradle 报"No tests found"或该 job XML 为空；空 XML 场景 aggregate 仍按求和断言，具体表现以 CI 为准（见 §八 未验证项）。
- 三阶：未来新增 Rhino 求值测试类，必须补 `@Category(RhinoEvalTest::class)` 才会被独立 job 收编；漏打标的类仍混在分片，挂死风险回归。这是本方案唯一的人为维护点。
- 三阶：baseline 对照模式（`test-baseline`，`:590` 起）不带任何分类属性，仍单 job 跑全量，Rhino 类在该模式下照旧执行；该 job 超时 60 分钟，若 Rhino 死循环在 baseline 模式挂住，只会拖 baseline 单 job，不影响分片车道。
- 对用户行为/业务代码零影响：本单不改 main 源码、不改测试断言，只改 CI 执行拓扑。

## 七、反例与边界

1. **分类未被 Gradle 识别（标记包名/Retention 不对）**：`includeCategories` 匹配零测试 → 独立 job 空跑或报 No tests found。兜底：CI 第一轮回证 `per_shard` 中 `rhino` 条数 > 0、总数 = 3185；若为零，优先查 `excludeCategories/includeCategories` 字符串与接口全限定名是否一致。
2. **`-PexcludeRhino` 与 `-PincludeRhino` 同时传入**：Gradle 分类过滤同时作用时取交集，结果为空。现状两个 job 命令行各传一个，不存在同任务双传；build.gradle 两条 `if` 独立判断，命令行若误拼双属性即空跑——此边界靠 CI 命令行评审兜，未在脚本里互斥断言。
3. **新 job 产物名漂移**：aggregate 下载 pattern 是 `test-results-rhino-*`；日后改名需同步改 pattern，否则总数闸门红。产物名常量集中在 `:481`/`:489`/`:516` 三处。
4. **分片清单仍列着四个类**：discover 枚举不识别分类，`shard-*.txt` 仍含这四类；靠 Gradle `excludeCategories` 在执行层排除。若 `--tests` 包含过滤与分类排除的组合行为在当前 Gradle/AGP 版本下不生效（分类类仍被拉起），分片挂死风险回归——此点只能 CI 实测确认（§八）。
5. **新增 Rhino 类漏打标**：见 §六 三阶；本单只收编 Q-243 点名的四类，其余触达 Rhino 的测试类未动。

## 八、认知成本

- 新增一个概念：`RhinoEvalTest` 分类标记；接手人需要知道"分片排除、独立 job 收编"这对属性开关（`-PexcludeRhino` / `-PincludeRhino`）。
- 改动面集中：标记接口 1 个文件、四个类各 2~3 行导入注解、build.gradle 8 行、verify.yml 一个 job + 两处接线；无新抽象层、无新共享状态。
- 命名提示：`RhinoEvalTest` 是 JUnit4 分类接口，不是测试基类；仓内若出现同名缩写需注意区分。

## 九、新发现问题

1. 仓内另有 13 个测试文件触达 Rhino/脚本求值（`JsTest.kt`、`JsEngineCapabilitiesTest.kt`、`JsSourceEngineTest.kt`、`RhinoJsonInitializationTest.kt`、`SourceCompatibilityTest.kt`、`RuntimeConcurrencyTest.kt`、`CryptoJsCompatibilityTest.kt`、`AutoTaskCoreTest.kt` 等），Q-243 未把它们列入挂死风险四类；是否存在同类阻塞挂死路径未逐一取证 —— **待裁**：下一单按"是否含 `while (true)` / 阻塞 eval / 无中断护栏"复核这批类，命中的补打 `RhinoEvalTest` 分类。
2. discover 枚举层不识别分类，分片清单仍含被排除的四类；清单行数与实际执行类数不一致，排障时容易误读分片规模 —— **待裁**：是否在 discover Python 步骤按编译产物里的分类注解剔除四类清单条目，使清单与执行集合一致。
3. build.gradle 两条属性分支未做互斥断言；命令行双传会空跑且错误信息不直观 —— **已入队**：下一单可在 test 任务加一行 `require` 互斥校验，双属性同传直接 fail 并提示。
4. 沙箱无 JDK，分类过滤与 `--tests` 包含过滤的组合行为、独立 job 零测试判定均未本地验证 —— **已入队**：CI 首轮回证 `per_shard` 含 `rhino` 分组且总数 3185 不掉；若分片仍拉起四类，回到本单 §七-4 排查。

## 十、未验证项

- 未跑本地编译、未跑单测；`kotlin-syntax-lint.py` 静态检查通过，红绿以云端 CI 为准。
- `excludeCategories`/`includeCategories` 与 `--tests` 包含过滤的组合生效情况、独立 job 实际收编类数与条数，未在本地验证（沙箱无 JDK）。
- aggregate Python 归组改动只做了静态走读；`rhino` 分组名、manifest 交叉核对在真实 XML 上的表现以 CI 运行结果为准。
- 工作流 YAML 已用 `yaml.safe_load` 解析通过；job 依赖图、artifact 上传/下载连通性未经真实 CI 跑过。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=e9560a5c333b；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`："不许 add/commit/push/revert/checkout——提交由总控统一做。"
2. `docs/机制-子代理必读.md:12`："不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。"
3. `docs/机制-子代理必读.md:18`："每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。"
- 适用条款：
  - 一条缺陷一单：本单只做 Rhino 四类的 CI 隔离；业务代码、其它测试断言未改。
  - 禁 git 写操作：未执行 add/commit/push/revert/checkout（子代理必读 :11）。
  - 禁本地编译：未跑 gradle/kotlinc/assemble；仅 python3 跑 lint / 门禁（子代理必读 :12）。
  - grep 回证：标记、注解、属性分支、workflow 接线均以 grep 现取现核（子代理必读 :18）。
  - 未验证项声明：分类过滤组合行为、独立 job 收编条数、aggregate 真实归组已在 §八、§十 显式列出（子代理必读 :20）。

## 十一、门禁自检输出

```
$ python3 data_scripts/kotlin-syntax-lint.py \
  app/src/test/java/io/legado/app/RhinoEvalTest.kt \
  app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt \
  app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt \
  app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt \
  app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt
KOTLIN-LINT: PASS（5 个文件）
rc=0

$ python3 data_scripts/check-workflow-contracts.py
CI-CONTRACT: PASS（workflow 4 个，workflow_run 引用与契约测试路径均一致）
rc=0

$ python3 data_scripts/check-onboarding.py --audit
ONBOARD-AUDIT: 0 份报告缺回执或不合规（历史留档可批量补，见 Q-199）
rc=0

$ python3 data_scripts/check-anchor-card.py --strict docs/修复-Rhino测试隔离.md
汇总: PASS=1 FAIL=0（共 1 份）
rc=0

$ python3 data_scripts/check-problem-finding.py docs/修复-Rhino测试隔离.md
PROBLEM-FINDING: PASS（1 份报告均带新发现问题与处置状态）
rc=0

$ python3 data_scripts/check-unpushed-reports.py
UNPUSHED-REPORTS: PASS（本轮 6 份报告（含已提交未推送）回执合规）
rc=0
```
