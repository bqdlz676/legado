package io.legado.app.ui.book.read.ink

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 设计 token 冻结守卫单测（纯 JVM）。
 *
 * 逐条核对切片-03 的 12 色语义色、切片-15 的时长 / 缓动曲线，以及切片-08 的性能红线常量。
 * 任一色值 / 时长被改动，本测试即失败，防止阅读页出现 token 漂移。
 */
class InkTokensTest {

    @Test
    fun `12 色冻结色值逐字对齐契约`() {
        assertEquals(Color(0xFFF7F1E6), InkTokens.BG_PAGE)
        assertEquals(Color(0xFFEFE8D8), InkTokens.BG_SURFACE)
        assertEquals(Color(0x731A1410), InkTokens.BG_OVERLAY)
        assertEquals(Color(0xFF1A1410), InkTokens.TEXT_PRIMARY)
        assertEquals(Color(0xFF5C4F3A), InkTokens.TEXT_SECONDARY)
        assertEquals(Color(0xFF9B9687), InkTokens.TEXT_TERTIARY)
        assertEquals(Color(0xFFC23B2E), InkTokens.TEXT_ACCENT)
        assertEquals(Color(0xFFD4A54A), InkTokens.BRAND_GOLD)
        assertEquals(Color(0xFF8B6914), InkTokens.BRAND_COPPER)
        assertEquals(Color(0xFFC23B2E), InkTokens.COLOR_LINK)
        assertEquals(Color(0x141A1410), InkTokens.COLOR_DIVIDER)
        assertEquals(Color(0x4DD4A54A), InkTokens.COLOR_GLOW)
    }

    @Test
    fun `camelCase 别名与冻结名同色`() {
        assertEquals(InkTokens.BG_PAGE, InkTokens.bgPage)
        assertEquals(InkTokens.BG_SURFACE, InkTokens.bgSurface)
        assertEquals(InkTokens.TEXT_PRIMARY, InkTokens.textPrimary)
        assertEquals(InkTokens.TEXT_ACCENT, InkTokens.textAccent)
        assertEquals(InkTokens.BRAND_GOLD, InkTokens.brandGold)
        assertEquals(InkTokens.COLOR_LINK, InkTokens.colorLink)
    }

    @Test
    fun `时长常量对齐切片-15 铁律`() {
        assertEquals(120, InkTokens.DURATION_MICRO)
        assertEquals(250, InkTokens.DURATION_CARD)
        assertEquals(250, InkTokens.DURATION_BUBBLE)
        assertEquals(350, InkTokens.DURATION_PAGE)
        assertEquals(200, InkTokens.DURATION_MASK)
        assertEquals(250, InkTokens.DURATION_SHEET)
        // 翻页掀角独立 400ms（切片-09 §3.3）。
        assertEquals(400, InkTokens.DURATION_PEEL)
    }

    @Test
    fun `四条专用缓动曲线就位`() {
        // 卡片浮入 / 气泡回弹 / 掀角 / 遮罩；只断言实例存在，曲线系数由构造冻结。
        assertTrue(InkTokens.cardEnterEasing.toString().isNotEmpty())
        assertTrue(InkTokens.bubbleEasing.toString().isNotEmpty())
        assertTrue(InkTokens.peelEasing.toString().isNotEmpty())
        assertTrue(InkTokens.maskEasing.toString().isNotEmpty())
    }

    @Test
    fun `性能红线常量写死`() {
        // 翻页单帧 ≤16ms；气泡热路径 ≤80ms。
        assertEquals(16.0, InkTokens.FRAME_BUDGET_MS, 0.0)
        assertEquals(80.0, InkTokens.BUBBLE_HOT_BUDGET_MS, 0.0)
        // 冷启 → 书架 ≤1.8s；阅读峰值内存 ≤180MB；字体子集 ≤200KB。
        assertEquals(1800.0, InkTokens.COLD_START_TO_BOOKSHELF_MS, 0.0)
        assertEquals(180.0, InkTokens.PEAK_MEMORY_MB, 0.0)
        assertEquals(200.0, InkTokens.FONT_SUBSET_MAX_KB, 0.0)
        // 章节排版缓存只留前后各 3 章。
        assertEquals(3, InkTokens.CHAPTER_CACHE_RADIUS)
    }

    @Test
    fun `掀角背面墨透 15% 与静置暂停口径`() {
        assertEquals(0.15f, InkTokens.BACK_TRANSMISSION_ALPHA, 1e-6f)
        // 静置 3 秒后氛围粒子自动暂停（切片-03）。
        assertEquals(3000L, InkTokens.PARTICLE_IDLE_PAUSE_MS)
    }
}
