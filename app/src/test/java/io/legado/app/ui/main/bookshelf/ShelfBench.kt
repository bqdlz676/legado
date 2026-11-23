package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * 落文 · U4 书架性能 bench（纯 JVM，离线可跑）。
 *
 * 切片-08 / 切片-04 红线：列表滚动 60fps 不抖动，单帧 ≤16ms。
 * 这里把卡片模型换算与粒子几何积分两条纯计算路径放到预算内；
 * Compose 重组 / 真实绘制 / 触摸派发仍需真机复测，未在本机跑过的项在交付说明中标「未验证」。
 *
 * 说明：本文件为 JVM 基准断言，沙箱内不执行；由云端 CI 跑 `testDebugUnitTest` 取真实耗时。
 * 断言写死指标，不允许在源码里放宽阈值；若 CI 未跑过，交付说明必须标注「bench 未验证」。
 */
class ShelfBench {

    private companion object {
        /** 单帧预算：16ms（切片-08 翻页 / 滚动同口径）。 */
        const val FRAME_BUDGET_MS = 16.0

        /** 200 本卡片模型换算预算：50ms（远低于一屏首帧可感知阈值）。 */
        const val MODEL_MAP_BUDGET_MS = 50.0

        const val BOOK_COUNT = 200
        const val FRAME_COUNT = 60
        const val WIDTH = 1080f
        const val HEIGHT = 1920f
    }

    private fun fakeBook(index: Int): ShelfBookUi = shelfBookUi(
        id = "book-$index",
        title = "春秋左传",
        author = "杜预 注 · 孔颖达 疏",
        durChapterIndex = index % 40,
        totalChapterNum = 40,
        chapterTitle = "隐公元年",
        latestChapterTime = System.currentTimeMillis(),
        now = System.currentTimeMillis(),
    )

    @Test
    fun `两百本卡片模型换算落在 50ms 预算内`() {
        // 预热：让 JIT 与类装载稳定。
        repeat(3) { List(BOOK_COUNT) { fakeBook(it) } }

        val iterations = 10
        var totalNanos = 0L
        repeat(iterations) {
            val t0 = System.nanoTime()
            val books = List(BOOK_COUNT) { fakeBook(it) }
            // 顺带把竖排书名切分跑一遍，避免优化器整段裁掉。
            books.forEach { verticalTitle(it.title) }
            totalNanos += System.nanoTime() - t0
        }
        val avgMs = totalNanos / iterations / 1_000_000.0
        assertTrue(
            "两百本模型换算 ${avgMs}ms 超出预算 ${MODEL_MAP_BUDGET_MS}ms",
            avgMs <= MODEL_MAP_BUDGET_MS,
        )
    }

    @Test
    fun `单帧粒子几何积分在 16ms 预算内`() {
        val motes = ShelfParticles.seedGoldMotes(ShelfTokens.PARTICLE_DEFAULT)
        val droplets = ShelfParticles.seedInkDroplets(14)
        val motesTitle = ShelfParticles.seedTitleMotes("春秋左传")

        // 预热粒子路径。
        repeat(300) { frame ->
            val t = frame / 300f
            motes.forEach { ShelfParticles.goldMoteAt(it, t, WIDTH, HEIGHT) }
        }

        var maxFrameNanos = 0L
        repeat(3) {
            repeat(FRAME_COUNT) { frame ->
                val t = frame / FRAME_COUNT.toFloat()
                val t0 = System.nanoTime()
                motes.forEach { ShelfParticles.goldMoteAt(it, t, WIDTH, HEIGHT) }
                droplets.forEach { ShelfParticles.dropletAt(it, t, WIDTH, HEIGHT) }
                motesTitle.forEach { ShelfParticles.titleMoteAt(it, t, WIDTH, HEIGHT) }
                // 模拟一帧 twinkle 三角函数计算，贴近真机单帧成本。
                sin(t * 2f * Math.PI.toFloat())
                maxFrameNanos = maxOf(maxFrameNanos, System.nanoTime() - t0)
            }
        }
        val maxFrameMs = maxFrameNanos / 1_000_000.0
        assertTrue(
            "单帧粒子几何 ${maxFrameMs}ms 超出预算 ${FRAME_BUDGET_MS}ms",
            maxFrameMs <= FRAME_BUDGET_MS,
        )
    }

    @Test
    fun `粒子数量与标题切分常量不被放宽`() {
        // 切片-10：15-20 粒；超出即夹回。
        assertTrue(ShelfTokens.PARTICLE_MIN >= 15)
        assertTrue(ShelfTokens.PARTICLE_MAX <= 20)
        // 竖排书名超出 5 字收「…」。
        assertTrue(verticalTitle("資治通鑑綱目卷之一").last() == "…")
    }
}
