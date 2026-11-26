package io.legado.app

/**
 * JUnit4 分类标记（Q-246）：Rhino 阻塞求值类测试。
 *
 * 为什么单独成类：Rhino eval/compile 是同步阻塞调用，忽略线程 interrupt；
 * JUnit `@Test(timeout=)` 只能在 runner 层判失败，不能真正杀掉阻塞在 Rhino 内的线程，
 * 这类测试若混进 4 分片，会把 test worker 拖死（shard 2 挂死 30 分钟以上即此路径）。
 *
 * 隔离方式：分片任务带 `-PexcludeRhino` 排除本分类；专用 `rhino-eval-tests` job
 * 带 `-PincludeRhino` 只跑本分类，失败即红但不拖累分片。断言总数不变，只换执行位置。
 *
 * 选 JUnit4 `@Category` 而非自定义密封接口：现有测试全是 JUnit4（junit:junit 4.13.2），
 * Gradle Test 任务的 `excludeCategories` / `includeCategories` 原生识别 JUnit4 分类，
 * 无需自写测试过滤器；自定义接口不被 Gradle 分类 DSL 识别。
 */
interface RhinoEvalTest
