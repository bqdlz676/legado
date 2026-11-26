package io.legado.app

/**
 * JUnit4 分类标记（Q-261）：重负载模型层 bench 测试。
 *
 * 为什么单独成类：这批 bench 在宿主 JVM 上跑大批量迭代（warmup 数千 + 测量数万轮，
 * 或 A* 寻路 / 整局推演 / 批量词典解析），单类占用 test worker 时间显著长于普通单测；
 * 混进 4 分片会把某一片拖到超时无 XML（shard 1 自 12:19:35Z 挂死 20 分钟以上即此路径）。
 * 与 RhinoEvalTest 同族做法：不靠 JUnit `@Test(timeout=)`——timeout 只能在 runner 层
 * 判失败，杀不掉已进入大批量循环的线程；分类隔离让挂死/慢速只落在独立 bench job。
 *
 * 隔离方式：分片任务带 `-PexcludeSlowBench` 排除本分类；专用 `slow-bench-tests` job
 * 带 `-PincludeSlowBench` 只跑本分类，失败即红但不拖累分片。断言总数不变，只换执行位置。
 *
 * 类粒度而非用例粒度：Gradle `includeCategories`/`excludeCategories`（JUnit4 Category）
 * 只能作用到测试类，无法按单个 `@Test` 方法开关；故判定单位是类——类内只要存在重负载
 * bench 用例，整类平移到 bench job，普通快速用例随之一起走（不影响总数与互斥并集）。
 *
 * 打标口径：只有真正被 JUnit 执行的 `class`（含 `@Test` 方法）才打标；bench 引擎 `object`
 * 与报告 `data class` 不含 `@Test`，JUnit 不会执行，不打标（见报告判定表）。
 */
interface SlowBenchTest
