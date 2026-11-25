# 待接 · 截图基建（Paparazzi）暂存说明

> 状态：已接线（路径 A：独立 Gradle 模块 `:screenshot`），commit `07615c1`；5 个源码已迁入 `screenshot/src/test/java/io/legado/app/ci/screenshot/`，独立 workflow `.github/workflows/screenshot.yml` 不 gate 主 run。判据复验待 CI（按 F-107 未本地编译，等 CI）。本目录仅留 README 与原 job 逐字存档。

## 1. 这里是什么

本目录是 2026-11-25 为修 CI 红而从 `:app` 模块整建制移出的截图帧能力存档，**不是删除**：

- 5 个截图测试源码（4 个快照测试 + 1 个夹具对象）：路径 A 接线后已迁至 `screenshot/src/test/java/io/legado/app/ci/screenshot/`（包名不变），本目录不再保留源码副本。
- `workflow-截图job原文.yml`：原 `build.yml` 中 `screenshot-frames` job 的逐字存档（含 record 任务、帧收集、frames.json 清单、artifact 上传全步骤），供重新接线时对照。
- 配套脚本 `data_scripts/frame-manifest.py` 仍留在原路径，未随本目录移动；存档 job 里调用的就是该路径。

## 2. 为什么暂离 app 模块

实证 run：`45437003104`（commit `2056070`，以及同序列 `45436898609` / `45436642601` / `45436254544` / `45436013996`），777 tests completed、11 个测试类失败。绿基线 `7bb6e90` 之后唯一的构建改动是 commit `068b56a` + `4df70b8`（P0 截图基建）：

1. `app` 模块应用 `app.cash.paparazzi` 2.0.0-alpha05 插件；
2. `testImplementation(libs.paparazzi)`；
3. `android.testOptions.unitTests.includeAndroidResources = true`。

该改动把 Paparazzi 插件、layoutlib 及其传递依赖（含 kotlin-reflect 等）注入了 `:app` 单元测试运行类路径，与既有纯 JVM 单测共享同一条 test runtime classpath，引发三类红：

- 主族（10 个 JS/Rhino 相关测试类）：`java.lang.NoClassDefFoundError`，`Caused by: java.lang.ExceptionInInitializerError at SystemProperties.java:-2`（`org.htmlunit.corejs.javascript`，htmlunit-core-js 5.3.0-legado.4）。Rhino 桥接测试在被污染的类路径上静态初始化失败，后续断言类错误（AssertionError / NoSuchElementException / UnsatisfiedLinkError）均为该初始化失败的连锁反应。
- 次族：`RuntimeResourceCompatibilityTest > kotlin builtins loaders are absent from the runtime` 失败——测试运行时多出了 `kotlin.reflect.jvm.internal.impl.builtins.*` 三个类（kotlin-reflect 被传递依赖带上 test 类路径），与该用例"反射内建加载器不得进入运行时"的断言直接冲突。
- 报告生成：`GradleException: Could not generate test report …`，`Caused by: java.lang.NoClassDefFoundError: org/gradle/reporting/CodePanelRenderer$Data`，堆栈落在 `app.cash.paparazzi.gradle.reporting.PaparazziTestReporter.generateFiles(PaparazziTestReporter.kt:118)`——Paparazzi 测试报告器在 Gradle 8.14.4 下解析不到该 Gradle 内部类，测试跑完后报告环节把整个任务带崩。

在本栈（AGP 8.13.2 / Gradle 8.14.4 / JDK21 / Compose BOM 2026.06.01）内，无法在不砍测试、不放宽断言的前提下把这条污染链路从 app 模块内部剥干净；因此整建制移出，让 `:app` 测试类路径回到 `7bb6e90` 基线状态，截图能力以存档形式保留待接。

## 3. 重新接线的三条可选路径

### 路径 A：独立 Gradle 模块（首选评估）

- 新建 `:screenshot` Android library 模块，只在该模块应用 Paparazzi 插件与 `testImplementation(paparazzi)`，`includeAndroidResources` 也只开在该模块。
- 被截图的 UI 展示模型/组件若目前在 `:app` 内，需要下沉到可被库模块依赖的位置（或在库模块内重放夹具）；`:app` 不反向依赖 `:screenshot`。
- 截图测试源码从本目录 `app/src/test/...` 拷入新模块对应源集；workflow 新增独立 job 跑该模块的 `recordPaparazzi*` / `verifyPaparazzi*` 任务。

### 路径 B：独立 test source set + 独立 task

- 在 `:app` 内为截图另立独立测试源集（独立目录、独立 `Test` 任务、独立依赖配置），与基线 `testAppDebugUnitTest` 类路径完全隔离；基线分片清单不得纳入该源集。
- 风险：AGP 对自定义 unit test source set 的依赖隔离支持有限，需先验证 Paparazzi 装配不会回写到主测试变体；若隔离不干净，退回路径 A。

### 路径 C：AGP 原生 screenshotTest 源集

- 评估 AGP 8.13 原生 `screenshotTest` 源集与 Paparazzi 2.0.0-alpha05 的兼容性；原生源集与单元测试类路径天然分开。
- 备选档位：若 alpha05 在本栈报告器/类路径问题无法消除，需另选与 AGP 8.13.2 / Gradle 8.14.4 实测兼容的 Paparazzi 档位（CHANGELOG 依据留档），不得直接升到要求 AGP 9.0.0 的 alpha05.1。

## 4. 接线后必须满足的判据（两条同时成立才算接通）

1. `:app` 主 run（discover / test-shard / test-aggregate / build-shard）全绿：基线 3185 条单测 0 failures 0 errors 0 skipped，`RuntimeResourceCompatibilityTest` 仍断言 kotlin builtins 缺席，Rhino 族测试不回归。
2. 截图 run 自身出帧：独立 job 产出 PNG + `frames.json`，artifact `if-no-files-found: error` 兜底；不允许用 `continue-on-error`、`if: always()`、`@Ignore`、删断言等方式让截图 job 蒙绿。
3. 接线完成后回本目录销账：在 `docs/台账-三件缺项.md` 对应条目登记补齐 commit、门禁判据与复验结论；本存档目录随源码迁回后删除。

## 5. 遗留边界

- 本次移出前，截图测试在 CI 上从未成功出过帧（截图 job 随基线单测一同被红挡住）；仓库内不存在已冻结的 golden 帧。
- `data_scripts/frame-manifest.py` 仍在原位，但其唯一调用方（截图 job）已存档；在截图能力接回前，该脚本不会被任何 workflow 步骤触发。
