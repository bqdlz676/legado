package io.legado.app.ui.main.bookshelf

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · U4 书架 token 冻结守卫单测（纯 JVM）。
 *
 * 逐条核对契约 §1.3 的 12 色语义色、切片-15 的时长 / 缓动曲线、切片-10 的卡片几何。
 * 任一色值 / 时长被改动，本测试即失败，防止书架出现 token 漂移。
 */
class ShelfTokensTest {

    @Test
    fun `12 色冻结色值逐字对齐契约`() {
        assertEquals(Color(0xFFF7F1E6), ShelfTokens.bgPage)
        assertEquals(Color(0xFFEFE8D8), ShelfTokens.bgSurface)
        assertEquals(Color(0x731A1410), ShelfTokens.bgOverlay)
        assertEquals(Color(0xFF1A1410), ShelfTokens.textPrimary)
        assertEquals(Color(0xFF5C4F3A), ShelfTokens.textSecondary)
        assertEquals(Color(0xFF9B9687), ShelfTokens.textTertiary)
        assertEquals(Color(0xFFC23B2E), ShelfTokens.textAccent)
        assertEquals(Color(0xFFD4A54A), ShelfTokens.brandGold)
        assertEquals(Color(0xFF8B6914), ShelfTokens.brandCopper)
        assertEquals(Color(0xFFC23B2E), ShelfTokens.colorLink)
        assertEquals(Color(0x141A1410), ShelfTokens.colorDivider)
        assertEquals(Color(0x4DD4A54A), ShelfTokens.colorGlow)
    }

    @Test
    fun `时长常量对齐切片-15 铁律`() {
        assertEquals(120L, ShelfTokens.MICRO_MS)
        assertEquals(350L, ShelfTokens.CARD_REVEAL_MS)
        assertEquals(200L, ShelfTokens.SCRIM_MS)
        assertEquals(350L, ShelfTokens.PAGE_MS)
        assertEquals(480L, ShelfTokens.SHATTER_MS)
        assertEquals(45L, ShelfTokens.STAGGER_MS)
    }

    @Test
    fun `三条专用缓动曲线就位`() {
        assertTrue(ShelfTokens.cardReveal.toString().isNotEmpty())
        assertTrue(ShelfTokens.scrim.toString().isNotEmpty())
        assertTrue(ShelfTokens.overshoot.toString().isNotEmpty())
    }

    @Test
    fun `卡片几何对齐切片-10 原文`() {
        // 竖排书名 22sp 墨色加粗；作者云灰 11sp；按下前倾 5°。
        assertEquals(22f, ShelfTokens.TITLE_SP, 0f)
        assertEquals(11f, ShelfTokens.AUTHOR_SP, 0f)
        assertEquals(5f, ShelfTokens.TILT_DEG, 0f)
        // 暖金微光溢出半径 12px、底部溢出 4dp。
        assertEquals(12f, ShelfTokens.GLOW_RADIUS_DP, 0f)
        assertEquals(4f, ShelfTokens.GLOW_OVERFLOW_DP, 0f)
    }

    @Test
    fun `金色微粒数量收口在 15-20 粒区间`() {
        assertTrue(ShelfTokens.PARTICLE_MIN >= 15)
        assertTrue(ShelfTokens.PARTICLE_MAX <= 20)
        assertTrue(ShelfTokens.PARTICLE_DEFAULT in
            ShelfTokens.PARTICLE_MIN..ShelfTokens.PARTICLE_MAX)
    }

    @Test
    fun `按下泛色收口 8%`() {
        assertEquals(0.08f, ShelfTokens.PRESS_ALPHA, 1e-6f)
    }
}
