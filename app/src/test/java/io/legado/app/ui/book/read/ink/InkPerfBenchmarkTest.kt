package io.legado.app.ui.book.read.ink

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * 落文 · 阅读页性能红线 benchmark（纯 JVM，离线可跑）。
 *
 * 切片-08 红线：
 *  - 翻页全程 ≤16ms/帧：spring 积分 + 粒子明灭计算须落在 [InkTokens.FRAME_BUDGET_MS] 内；
 *  - 冷启 → 书架 ≤1.8s、峰值内存三档 ≤100 / 100–120 / >120MB、字体子集 ≤200KB：这里把红线常量写死断言，
 *    真实耗时 / 内存 / 字体体积需真机与 CI 资源测量，本测试不冒充真机结果。
 *
 * 注意：这是纯计算路径的预算基线；Compose 重组 / 真实绘制 / 触摸派发 / 内存峰值
 * 仍需真机复测，未在本机跑过的项在交付说明中标「未验证」。
 */
class InkPerfBenchmarkTest {

    @Test
    fun `翻页单帧 spring 积分与粒子明灭在 16ms 预算内`() {
        val spring = PeelSpring(
            stiffness = InkTokens.PEEL_SPRING_STIFFNESS,
            dampingRatio = InkTokens.PEEL_SPRING_DAMPING_RATIO,
        )
        val dt = 1f / 60f
        val start = System.nanoTime()
        repeat(24) { frame ->
            spring.update(dt)
            // 模拟一帧粒子明灭的三角函数计算
            sin(2f * Math.PI.toFloat() * frame / InkTokens.PARTICLE_TWINKLE_PERIOD_MS.toFloat())
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue(
            "翻页帧耗时 ${elapsedMs}ms 超出预算 ${InkTokens.FRAME_BUDGET_MS}ms",
            elapsedMs <= InkTokens.FRAME_BUDGET_MS,
        )
    }

    @Test
    fun `启动内存字体三条红线常量不被放宽`() {
        // 冷启 → 书架 ≤1.8s（真机启动耗时未在本机测量，仅收口上限值）。
        assertTrue(InkTokens.COLD_START_TO_BOOKSHELF_MS <= 1800.0)
        // 峰值内存三档线写死：过档线 100MB、中档界线 120MB（切片-21:27；真机内存峰值未在本机测量，仅收口阈值）。
        assertTrue(InkTokens.MEMORY_PEAK_PASS_MB == 100.0)
        assertTrue(InkTokens.MEMORY_PEAK_WARN_MB == 120.0)
        // 首屏字体子集 ≤200KB（子集打包由 U9 收口，体积未在本机测量）。
        assertTrue(InkTokens.FONT_SUBSET_MAX_KB <= 200.0)
    }

    @Test
    fun `章节排版缓存容量收口为前后各三章`() {
        // 切片-08：只保留前后各 3 章排版缓存，其余释放，峰值 ≤60MB。
        assertTrue(InkTokens.CHAPTER_CACHE_RADIUS == 3)
    }
}
