package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 书架粒子几何单测（纯 JVM）。
 *
 * 覆盖：微粒数量收口 15-20、种子可复现、点位不越界、碎墨 / 字粒扩散几何、循环相位。
 */
class ShelfParticlesTest {

    @Test
    fun `金色微粒数量越界一律夹回 15-20`() {
        assertEquals(ShelfTokens.PARTICLE_MIN, ShelfParticles.seedGoldMotes(5).size)
        assertEquals(ShelfTokens.PARTICLE_MAX, ShelfParticles.seedGoldMotes(50).size)
        assertEquals(ShelfTokens.PARTICLE_DEFAULT, ShelfParticles.seedGoldMotes(18).size)
    }

    @Test
    fun `同一种子产出完全一致的微粒序列`() {
        val first = ShelfParticles.seedGoldMotes(18, seed = 7L)
        val second = ShelfParticles.seedGoldMotes(18, seed = 7L)
        assertEquals(first, second)
    }

    @Test
    fun `微粒点位落在画布之内不越界`() {
        val motes = ShelfParticles.seedGoldMotes(18)
        val width = 1080f
        val height = 1920f
        repeat(20) { frame ->
            val t = frame / 20f
            motes.forEach { mote ->
                val pos = ShelfParticles.goldMoteAt(mote, t, width, height)
                assertTrue("x 越界: ${pos.x}", pos.x in 0f..width)
                assertTrue("y 越界: ${pos.y}", pos.y in 0f..height)
            }
        }
    }

    @Test
    fun `碎墨滴在消散起点居中、终点向外扩散`() {
        val droplets = ShelfParticles.seedInkDroplets(14)
        val width = 300f
        val height = 440f
        droplets.forEach { d ->
            val start = ShelfParticles.dropletAt(d, 0f, width, height)
            assertEquals(width / 2f, start.x, 1e-4f)
            assertEquals(height / 2f, start.y, 1e-4f)
            val end = ShelfParticles.dropletAt(d, 1f, width, height)
            val dist = kotlin.math.hypot(end.x - width / 2f, end.y - height / 2f)
            assertTrue("墨滴未扩散: $dist", dist > 1f)
        }
    }

    @Test
    fun `书名字粒一字一粒，随进度向外爆开`() {
        val title = "春秋左传"
        val motes = ShelfParticles.seedTitleMotes(title)
        assertEquals(title.length, motes.size)
        val width = 300f
        val height = 440f
        motes.forEach { mote ->
            val start = ShelfParticles.titleMoteAt(mote, 0f, width, height)
            assertEquals(width / 2f, start.x, 1e-4f)
            assertEquals(height / 2f, start.y, 1e-4f)
            val end = ShelfParticles.titleMoteAt(mote, 1f, width, height)
            assertTrue(kotlin.math.hypot(end.x - width / 2f, end.y - height / 2f) > 1f)
        }
    }

    @Test
    fun `循环相位取模回到首粒附近，不硬跳`() {
        val mote = ShelfParticles.seedGoldMotes(18).first()
        val width = 1080f
        val height = 1920f
        val atStart = ShelfParticles.goldMoteAt(mote, 0f, width, height)
        val atEnd = ShelfParticles.goldMoteAt(mote, 1f, width, height)
        // 0 与 1 是同一循环相位，位置应当一致
        assertEquals(atStart.x, atEnd.x, 1e-4f)
        assertEquals(atStart.y, atEnd.y, 1e-4f)
    }
}
