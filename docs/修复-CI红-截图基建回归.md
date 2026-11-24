# 修复记录 · CI 红（截图基建回归）

- 修复 commit：`b49de91eb95259a3f1f849f50bd8b1aa248a2799`（分支 `main`）
- 修复日期：2026-11-25
- 栈：AGP 8.13.2 / Gradle 8.14.4 / JDK21 / Kotlin 2.4.20 / Compose BOM 2026.06.01
- 红 run 序列：`45437003104`(2056070)、`45436898609`(d92c116)、`45436642601`(7010d83)、`45436254544`(41c4512)、`45436013996`(eccd156)；HEAD `cc01a70` 的 `45438966727` 仅改文档，结果同前。绿基线：`7bb6e90`。
- 本报告口径：报错原文逐字引用（日志文件:行 + 原文行）；结论只挂可回源证据。

## 1. 失败画像（逐字引用）

日志目录：`agents/o_00074KFWJhF/ci-logs/x/`。四个分片执行量（原文行）：

- `3_Unit tests shard 1_4.txt:702`：`754 tests completed, 21 failed`
- `4_Unit tests shard 2_4.txt`：`871 tests completed, 6 failed`
- `5_Unit tests shard 4_4.txt`：`784 tests completed, 41 failed`
- `6_Unit tests shard 3_4.txt`：`777 tests completed, 11 failed`

### 1.1 主族：JS/Rhino 测试类静态初始化失败

以 shard 1 为例（`3_Unit tests shard 1_4.txt`）：

- `:625`：`java.lang.NoClassDefFoundError at AnalyzeRuleElementsNormalizationTest.kt:14`
- `:626`：`Caused by: java.lang.ExceptionInInitializerError at SystemProperties.java:-2`
- `:629-630`：`java.lang.NoClassDefFoundError at ReviewRuleParserTest.kt:417` / `Caused by: java.lang.ExceptionInInitializerError at SystemProperties.java:-2`
- `:654-655`：`java.lang.NoClassDefFoundError at JsSourceReviewTest.kt:325` / `Caused by: java.lang.ExceptionInInitializerError at SystemProperties.java:-2`

同型失败覆盖：`JsSourceConfigTest`、`JsSourceReviewTest`、`ReviewRuleParserTest`、`ReviewRuleParserFallbackTest`、`JsSourceEngineTest`、`JsSourceBookTest`、`JsSourceUpsertTest`、`JsSourceAuthorGuideTest`、`LoginUiV2EngineTest`、`McpServiceContractTest`、`AnalyzeRuleElementsNormalizationTest`、`LoginInfoMapInitializationTest`。

`SystemProperties` 位于 `org.htmlunit.corejs.javascript`（htmlunit-core-js `5.3.0-legado.4`，本地 jar：`third_party/maven/org/htmlunit/htmlunit-core-js/5.3.0-legado.4/htmlunit-core-js-5.3.0-legado.4.jar`）。分片日志只打到二级 cause（`ExceptionInInitializerError`），三级 cause（静态块内实际抛出的异常）未在控制台输出，JUnit XML 当轮未归档到可读日志；本报告不对三级 cause 做超出证据的指认。

连锁次生错误（同一初始化失败后，引擎取不到桥接对象，断言/取值随之崩）：

- `3_...txt:633`：`java.util.NoSuchElementException at ReviewRuleParserTest.kt:387`
- `3_...txt:636`：`java.lang.AssertionError at ReviewRuleParserTest.kt:126`
- `3_...txt:622`：`java.lang.UnsatisfiedLinkError at LoginInfoMapInitializationTest.kt:44`
- 其余 `AssertionError` 分布在 `ReviewRuleParserTest` / `JsSourceReviewTest` 各用例，均挂在同一 `NoClassDefFoundError → ExceptionInInitializerError` 链上。

### 1.2 次族：kotlin builtins 加载器出现在测试运行时

`4_Unit tests shard 2_4.txt`：

- `:626`：`RuntimeResourceCompatibilityTest > kotlin builtins loaders are absent from the runtime FAILED`
- `:627`：`java.lang.AssertionError at RuntimeResourceCompatibilityTest.kt:14`

对应用例源码 `app/src/test/java/io/legado/app/config/RuntimeResourceCompatibilityTest.kt:12-18`：对 `kotlin.reflect.jvm.internal.impl.builtins.BuiltInsLoader`、`...KotlinBuiltIns`、`...BuiltInsResourceLoader` 三个类逐个 `Class.forName`，断言抛 `ClassNotFoundException`；失败说明测试运行时类路径上多出了 kotlin-reflect 及其 builtins 加载器。

### 1.3 报告生成被带崩：CodePanelRenderer$Data 缺失

`3_Unit tests shard 1_4.txt`：

- `:704`：`> Task :app:testAppDebugUnitTest FAILED`
- `:762`：`Caused by: org.gradle.api.GradleException: Could not generate test report to '/home/runner/work/legado/legado/app/build/reports/tests/testAppDebugUnitTest'.`
- `:763`：`at app.cash.paparazzi.gradle.reporting.PaparazziTestReporter.generateFiles(PaparazziTestReporter.kt:118)`
- `:764`：`at app.cash.paparazzi.gradle.reporting.PaparazziTestReporter.generateReport(PaparazziTestReporter.kt:38)`
- `:909`：`Caused by: java.lang.NoClassDefFoundError: org/gradle/reporting/CodePanelRenderer$Data`
- `:946`：`Caused by: java.lang.ClassNotFoundException: org.gradle.reporting.CodePanelRenderer$Data`

四个分片日志均出现同一对 `NoClassDefFoundError` / `ClassNotFoundException`（shard2 `:896-897`、shard3、shard4 同型）。报告器是 Paparazzi 插件注册进 `AbstractTestTask.createReporting` 的（`:765` `at org.gradle.api.tasks.testing.AbstractTestTask.createReporting(AbstractTestTask.java:634)`），即在测试跑完后的报告环节抛错，把整个 `testAppDebugUnitTest` 任务判失败。

### 1.4 附带族：出包产物名断言真值来源移位

`4_Unit tests shard 2_4.txt`：

- `:623`：`TestReleaseWorkflowTest > retained build workflow publishes only debug and release apk artifacts FAILED`
- `:624`：`java.lang.AssertionError at TestReleaseWorkflowTest.kt:39`

该用例原断言 `build.yml` 内含 `legado-debug-apk` / `legado-release-apk`；commit `b285dab` 把出包上传步骤抽进 composite action `.github/actions/build-apks/action.yml`（`:37` `name: legado-debug-apk`、`:44` `name: legado-release-apk`），`build.yml` 内不再出现这两个串。此红与 Paparazzi 无关，是抽取 composite 时漏改的测试真值来源。

## 2. 归因链条

### 2.1 变更面穷举（与绿基线 `7bb6e90` 逐行 diff）

`git diff 7bb6e90 HEAD -- app/build.gradle gradle/libs.versions.toml` 只含三组构建改动（commit `068b56a` + `4df70b8`）：

1. `app/build.gradle` plugins 块新增 `alias libs.plugins.paparazzi`（Paparazzi Gradle 插件）。
2. `app/build.gradle` 新增 `android.testOptions.unitTests.includeAndroidResources = true`。
3. `app/build.gradle` dependencies 新增 `testImplementation(libs.paparazzi)`；`gradle/libs.versions.toml` 新增 `paparazzi = "2.0.0-alpha05"` 版本、library 条目、plugin 条目。

`7bb6e90` 上上述 12 个 Rhino/JS 测试类与 `RuntimeResourceCompatibilityTest` 全部存在且 CI 全绿，故缺陷是这次构建改动引入，不是新测试写错。

### 2.2 污染如何进测试运行类路径

- Paparazzi 插件把 layoutlib、paparazzi runtime 及其传递依赖装配进 `:app` 的单元测试变体；`testImplementation(libs.paparazzi)` 再显式叠一份。这些依赖与 770 余条既有纯 JVM 单测共享同一条 `testAppDebugUnitTest` runtime classpath。
- `includeAndroidResources = true` 进一步把主源资源/R 类/资产目录纳入单元测试类路径，扩大了测试变体与 main 变体的类路径分叉面。
- 次族证据闭环：`RuntimeResourceCompatibilityTest` 断言缺席的三个 `kotlin.reflect.jvm.internal.impl.builtins.*` 类只可能由 kotlin-reflect 带入；基线依赖里 kotlin-reflect 被多处 exclude（`app/build.gradle` MCP SDK / Ktor CIO / Ktor test host 三处 `exclude group: 'org.jetbrains.kotlin', module: 'kotlin-reflect'`），基线测试类路径上没有它；Paparazzi/layoutlib 传递链把它带回 test runtime，断言即红。
- 主族证据闭环：同一批被传递依赖扩充的类路径上，htmlunit-core-js `SystemProperties` 静态初始化抛 `ExceptionInInitializerError`；基线类路径下同类同测试全绿。沙箱内无 gradle/SDK/JDK21、本地 Gradle 缓存无 paparazzi/layoutlib jar，三级 cause 无法在本环境复现剥离，故不指认具体冲突类（见 §6 遗留项）。
- 1.3 证据闭环：报告器堆栈起点是 `app.cash.paparazzi.gradle.reporting.PaparazziTestReporter`，它在生成 HTML 报告时引用 Gradle 内部类 `org.gradle.reporting.CodePanelRenderer$Data`；Gradle 8.14.4 的 VisitableURLClassLoader 找不到该内部类（`:897` `at org.gradle.internal.classloader.VisitableURLClassLoader$InstrumentingVisitableURLClassLoader.findClass(VisitableURLClassLoader.java:189)`）。这是 Paparazzi 2.0.0-alpha05 报告器与本栈 Gradle 版本之间的内部 API 错位：alpha05 CHANGELOG 只修了 `HtmlWriterTools` 的 NoClassDefFoundError（issue #2316），未覆盖 `CodePanelRenderer$Data` 这一内部类。该崩溃发生在测试执行之后的报告环节，即使单测全绿也会把任务带红。

### 2.3 为什么不在 app 模块内打补丁继续跑（Plan A 否决）

穷举过的 app 模块内修补路径及其反例：

- 精准 exclude 泄漏依赖（路径 b）：kotlin-reflect 可从 paparazzi 配置 exclude，但 §1.3 的报告器崩溃是 Paparazzi 插件代码直接引用 Gradle 内部类，exclude 任何依赖都修不到插件自身的类引用；且无法在沙箱内逐条列全 layoutlib 传递树做穷举排除，排不干净就仍是红。
- 换 Paparazzi 档位（路径 c）：alpha05.1 起要求 AGP 9.0.0 / Gradle 9.4+（CHANGELOG 原文：`Requires Gradle 9.4+`、`Android Gradle Plugin 9.0`），本栈 AGP 8.13.2 不可用；1.3.x 稳定档仅测 Compose 1.7.5 / AGP 8.4.2，与本栈 Compose 1.11.4 落差过大，且 1.3.x 同样向 app 测试变体装配 layoutlib，类路径污染问题不消失。
- 关 `includeAndroidResources` 保 Paparazzi：截图渲染解析不到 R 资源/drawable，出帧能力残缺；且报告器崩溃与 kotlin-reflect 泄漏不随此开关消失。
- 结论：在 `:app` 模块内部无法同时满足"基线 3185 条单测全绿 + 截图链路不污染 + 报告器不崩"。按任务优先级 (a)，把截图工程隔离出 app 模块；本轮先回退基线配置保绿，能力整建制存档待接。

## 3. 改动清单（文件:行 + 为什么）

### 3.1 `app/build.gradle`（回退到 `7bb6e90` 基线，`git diff 7bb6e90 -- app/build.gradle` 为空）

- 删除 plugins 块 Paparazzi 别名行及其注释（原 `:15-16`）：插件不再向 app 单元测试变体装配 layoutlib 与截图任务。现 `:14` 为 `alias libs.plugins.kotlin.compose`，plugins 块到此闭合。
- 删除 `testOptions { unitTests.includeAndroidResources = true }` 整块（原 `:171-176`）：不再把主源资源/资产塞进测试类路径。现 `:169` 直接进入 `packaging {`。
- 删除 `testImplementation(libs.paparazzi)` 行及注释（原 `:239-240`）：现 `:230` `testImplementation(libs.mockito.core)` 紧接 `:231` `add(mockitoAgent.name, libs.mockito.core)`。

### 3.2 `gradle/libs.versions.toml`（回退到基线，`git diff 7bb6e90 -- gradle/libs.versions.toml` 为空）

- 删除 `paparazzi = "2.0.0-alpha05"` 版本块及三行选型注释（原 `:34-37`）；现 `:33` `preference = "1.2.1"` 后直接 `quickChineseTransfer`。
- 删除 library 条目 `paparazzi = { module = "app.cash.paparazzi:paparazzi", ... }`（原 `:214-215`）；现 `:209` 为 `mockito-core`。
- 删除 plugin 条目 `paparazzi = { id = "app.cash.paparazzi", ... }`（原 `:268-269`）；现 `:260` 为 `download` 插件条目。

### 3.3 截图源码整建制移档（不删测试）

- 原 `app/src/test/java/io/legado/app/ci/screenshot/` 下 5 个文件 `git mv` 到 `docs/待接-截图基建/app/src/test/java/io/legado/app/ci/screenshot/`：
  - `BasicComponentsSnapshotTest.kt`
  - `BookshelfSnapshotTest.kt`
  - `CardFoundationSnapshotTest.kt`
  - `CatalogCardsSnapshotTest.kt`
  - `ScreenshotFixtures.kt`
- 原空目录 `app/src/test/java/io/legado/app/ci/screenshot/` 已 `rmdir` 清除。
- 移档原因：这些文件 `import app.cash.paparazzi.DeviceConfig / Paparazzi`，只要留在 app 测试源集，编译期就要求 Paparazzi 依赖在位；依赖回退后它们必须一并离开编译面，否则 `compileAppDebugUnitTestKotlin` 会报 unresolved reference。源码本身一行未改。

### 3.4 `.github/workflows/build.yml`

- 删除 `screenshot-frames` job（原 `:318-397`，含 Clean stale snapshots / Render frames with Paparazzi (record) / Collect frames / Generate frame manifest / Upload screenshot frames 全步骤）。job 原文逐字存档至 `docs/待接-截图基建/workflow-截图job原文.yml`（提取范围原 318-397 行，文件头三行存档说明除外）。
- 回退 discover 步骤里的 `SKIP_PACKAGE_PREFIXES = ('io.legado.app.ci.screenshot.',)` 分支（原 `:107-109`、`:119-121`），枚举逻辑回到基线：现 `:116` `classes.add(rel[:-6].replace(os.sep, '.'))`。截图包已不在 app 测试源集，前缀排除分支失去对象，一并回退避免死代码。
- 未使用 `continue-on-error`、未使用 `if: always()`、未给任何既有测试类加排除；`build-shard` / `build-baseline` 的 `needs` 链不引用已删 job。

### 3.5 `app/src/test/java/io/legado/app/ci/TestReleaseWorkflowTest.kt`（附带族修复，断言不放宽）

- 新增 `compositeActionText` 懒加载，读取 `.github/actions/build-apks/action.yml`（`:27-33`，注释说明 b285dab 抽取移位）。
- `retained build workflow publishes only debug and release apk artifacts` 用例（`:46-52`）：产物名正向断言改为对 `buildWorkflowText + compositeActionText` 合并文本判定 `legado-debug-apk` / `legado-release-apk` 仍在；三条反向断言（`ncipollo/release-action`、`lzy_web.py`、`Telegram`）仍只对 `buildWorkflowText` 判定。断言强度不变，只修正真值来源位置。

### 3.6 台账与存档文档

- `docs/台账-三件缺项.md` 追加 `G-09`：缺件"P0 截图出帧能力缺接线"，责任"下一棒/Q-10"，期限"下两棒内"，补齐前效力限制"P1 判帧不得宣称已验、观感验收不得出包"，状态"登记中"。
- 新增 `docs/待接-截图基建/README.md`：暂离原因、实证 run、三条重接路径（独立 Gradle 模块 / 独立 test source set + 独立 task / AGP 原生 screenshotTest 源集）、接通判据。
- `data_scripts/frame-manifest.py` 保留原位：其唯一调用方是已存档 job，接回时路径不变；脚本本身不参与编译与基线单测。

## 4. grep 证据（改动后实测）

命令与结果：

- `grep -n "paparazzi\|includeAndroidResources\|Paparazzi" app/build.gradle gradle/libs.versions.toml settings.gradle` → 0 命中（exit 1）。
- `grep -rln "paparazzi\|Paparazzi\|ci.screenshot" app/src --include="*.kt" --include="*.java" | wc -l` → `0`。
- `grep -rn "paparazzi" --include="*.gradle" --include="*.toml" .`（排除 `.git`）→ 0 命中。
- `grep -n "screenshot\|paparazzi\|recordPaparazzi\|frame-manifest" .github/workflows/build.yml` → 0 命中。
- `git diff 7bb6e90 --stat -- app/build.gradle gradle/libs.versions.toml settings.gradle` → 0 行差异（三文件与基线逐字节一致）。
- YAML 语法校验：`python3 -c "import yaml,sys; d=yaml.safe_load(open('.github/workflows/build.yml')); print('YAML OK, jobs=', list(d['jobs'].keys()))"` → `YAML OK, jobs= ['discover', 'test-shard', 'test-aggregate', 'test-baseline', 'build-shard', 'build-baseline']`。
- `git status` 复核：`app/src` 下截图源码残留 0（空目录已删）。

## 5. CI run 结论

- 修复 commit：`b49de91eb95259a3f1f849f50bd8b1aa248a2799`
- run：（轮询回填）
- 结论：（轮询回填）

## 6. 二阶 / 三阶推演

### 6.1 截图帧能力会不会在后续 CI 静默消失

- 本轮主动移除 `screenshot-frames` job，不是链路隐式断裂；能力以源码 + job 原文 + 清单脚本三件存档，并在台账 G-09 登记限权。后续观感工序若引用"已出帧"，G-09 限权条款阻断该引用。
- 风险点：存档目录在 `docs/` 下，不参与编译，后续重构若有人按目录树清理 docs 归档，源码可能丢失。对冲：README 写明相对路径与回拷位置；G-09 销账前该目录不得删。
- 基线 3185 条计数不受影响：discover 枚举原本就用 `SKIP_PACKAGE_PREFIXES` 把截图包排除在分片清单外，移出后编译产物里本就没有这 5 个类，汇总 job 的 `total < BASELINE` 断言不会因此差值报警。

### 6.2 test 变体与 main 变体类路径是否再次分叉

- 回退 `includeAndroidResources` 与 Paparazzi 依赖后，`testAppDebugUnitTest` 类路径回到基线形态：纯 JVM 单测只拿 testImplementation 依赖，不再叠 layoutlib / kotlin-reflect / 主源资源。
- 残留分叉面（基线既有，非本轮引入）：coreLibraryDesugaring、mockito agent、androidx test 库等基线既有 test 依赖保持原样。
- 重接截图时（路径 A/B/C），新隔离模块/源集必须自带独立依赖配置，不得把 Paparazzi/layoutlib 再写回 `:app` 的 `testImplementation`；否则本轮三类红会原样复现。

### 6.3 后续功能批（A 区 5 条 / B 区 P0 10 条）是否会被这套构建配置卡住

- 基线配置下纯 JVM 单测可正常编写运行；UI 层 Compose 预览/组件若需要截图佐证，在 G-09 接通前只能走人工出图，不得在 app 测试源集内新增 `app.cash.paparazzi` import。
- 重接截图前，任何新写的截图测试都应先落 `docs/待接-截图基建/` 存档路径，随接线批次统一入隔离模块。
- `TestReleaseWorkflowTest` 这类"读 workflow 文本"的守卫用例，后续再抽 composite action 时必须同步补真值来源文件，避免同类移位红。

### 6.4 反例与边界自查（先假设本方案错误）

- 反例 1：若 Rhino 族红的三级 cause 其实是 app 主源码缺陷而非类路径污染，回退构建配置后它们仍会红。边界：`7bb6e90` 上同一批类全绿，主源码在两次提交间未改（diff 仅构建面），回退后若仍红，下一轮需取 JUnit XML 三级 cause 再归因。
- 反例 2：若 Paparazzi 泄漏的不只是 kotlin-reflect，还有其他与 htmlunit-core-js 冲突的类，回退后理论上应全部消失；若 CI 仍有 Rhino 族红，说明 app 主源码或基线依赖本身存在独立缺陷，需另立案，不在本轮截图基建回归范围内。
- 反例 3：移出截图源码后，若有 main 源码或其他测试引用 `io.legado.app.ci.screenshot` 夹具，编译会断。已 grep 全 `app/src`，0 引用；存档夹具 `ScreenshotFixtures` 只被同包截图测试使用。
- 边界：沙箱无 gradle/SDK/JDK21，本轮未做本地编译；编译正确性由 CI 唯一验证。

## 7. 遗留项

1. `SystemProperties` 静态初始化失败的三级 cause 未取到：当轮 JUnit XML 未归档到可读日志。接 CI 后若 Rhino 族仍红，优先下载 `app/build/test-results/testAppDebugUnitTest/*.xml` 读 `<failure>` 完整堆栈定位具体冲突类。
2. 截图出帧能力未接通：存档路径、三路径方案、接通判据见 `docs/待接-截图基建/README.md`；台账 G-09 限期下两棒内。
3. `data_scripts/frame-manifest.py` 暂为孤立脚本，无 workflow 步骤调用；接回截图 job 时一并恢复调用。
4. 未评估 Paparazzi 后续稳定档对 AGP 8.13.2 / Gradle 8.14.4 的兼容状态；接线时需以当时 CHANGELOG 与实测为准，不得直升要求 AGP 9.0.0 的档位。
