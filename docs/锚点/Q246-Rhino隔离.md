# 锚点卡 · Q-246 Rhino 测试隔离（4 类 + 独立 job）

> 对象：`app/src/test/java/io/legado/app/RhinoEvalTest.kt`（新建）、四个 Rhino 测试类、`app/build.gradle`、`.github/workflows/verify.yml`；回执报告 `docs/修复-Rhino测试隔离.md`。

## 一、事实锚点（逐字，含 文件:行）

- 分类接口：`RhinoEvalTest.kt:17` `interface RhinoEvalTest`；机制为 JUnit4 `@Category`（`junit:junit 4.13.2`，`gradle/libs.versions.toml:212`）。
- 打标四类：`JsSourceConfigTest.kt:22`、`McpServiceContractTest.kt:23`、`RhinoScriptErrorContextTest.kt:12`、`RssSortUrlsTest.kt:13`，均 `@Category(RhinoEvalTest::class)`。
- Gradle 分支（Q-250 修正）：`app/build.gradle:216-227` `testOptions.unitTests.all { testTask -> testTask.useJUnit { … } }`；`:220` `includeCategories`、`:223` `excludeCategories` 属 JUnitOptions，由 `-PincludeRhino` / `-PexcludeRhino` 触发；不得直接在 AndroidUnitTest 任务上调分类 API（run 45810466813 配置期真红根因）。
- 分片排除：`verify.yml:319` 分片命令带 `-PexcludeRhino`。
- 独立 job：`verify.yml:372` `rhino-eval-tests`，`:376` `timeout-minutes: 8`，`:455` 带 `-PincludeRhino`；产物 `:481` `test-results-rhino-eval`、`:489` `-failed`。
- 汇总接线：`verify.yml:497` `needs: [discover, test-shard, rhino-eval-tests]`；`:516` 下载 `pattern: test-results-rhino-*`；`:542` 归组标签 `rhino`。

## 二、参数表

- 基线总数 `BASELINE = 3185`（verify.yml aggregate）；隔离只换执行位置，断言总数不变。
- 独立 job 超时 8 分钟；分片超时维持 45 分钟不变。
- 分类全限定名：`io.legado.app.RhinoEvalTest`；分片属性 `excludeRhino`，独立 job 属性 `includeRhino`，二者互斥。

## 三、结论锚点

- C1 四类从 4 分片排除、平移到 `rhino-eval-tests` 独立执行；Rhino 阻塞线程挂死只红独立 job，不拖累分片。
- C2 aggregate 下载并求和 Rhino 产物；分类互斥保证跨 job 无重复执行，manifest 交叉核对仍通过。
- C3 未跑 CI：分类与 `--tests` 组合行为、独立 job 实际条数、`rhino` 归组表现均以云端 Verify 首轮回证为准；Q-250 已把分类 API 改到 `useJUnit` JUnitOptions 闭包，配置期能否通过仍待 CI 复跑。

## 四、回源

- 回执报告：`docs/修复-Rhino测试隔离.md`（§二 选型、§三 改动清单、§四 grep 回证、§五 总数不变性、§六 二三阶影响、§七 边界、§九 新发现问题处置状态、§十 配置期真红修复 Q-250）。
