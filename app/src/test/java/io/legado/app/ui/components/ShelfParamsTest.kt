package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-10 书架参数单测：纯 JVM，只用 org.junit 与 kotlin 语言层，
 * 不引用任何平台类型，不读资源（避免测试类 NoClassDefFoundError）。
 * 规格：docs/锚点/S-10-书架页.md 参数表；切片-10 第 8、9、22 行；
 * 粒子保底口径对齐 docs/立项-补条款-第二批.md 条文 1.2。
 */
class ShelfParamsTest {

    private val delta = 1e-9

    @Test
    fun `进度为0时线长记0`() {
        assertEquals(0.0, progressLineHeight(0f, 200.0), delta)
    }

    @Test
    fun `进度为负数时夹到0`() {
        assertEquals(0.0, progressLineHeight(-0.5f, 200.0), delta)
    }

    @Test
    fun `进度大于1时夹到满卡高`() {
        assertEquals(200.0, progressLineHeight(1.5f, 200.0), delta)
    }

    @Test
    fun `进度为NaN时记0不抛异常`() {
        val line = progressLineHeight(Float.NaN, 200.0)
        assertEquals(0.0, line, delta)
        assertFalse(line.isNaN())
        assertFalse(line.isInfinite())
    }

    @Test
    fun `进度0点5时线长为卡高一半`() {
        assertEquals(100.0, progressLineHeight(0.5f, 200.0), delta)
    }

    @Test
    fun `卡高非正数时线长记0`() {
        assertEquals(0.0, progressLineHeight(0.5f, -10.0), delta)
        assertEquals(0.0, progressLineHeight(0.5f, 0.0), delta)
    }

    @Test
    fun `常规档粒子取上限20粒`() {
        assertEquals(ShelfParams.PARTICLE_MAX, particleCount(2))
        assertEquals(ShelfParams.PARTICLE_MAX, particleCount(3))
    }

    @Test
    fun `降级档减半后按15保底且恒在区间内`() {
        // 上限 20 减半 = 10，低于切片下限 15，必须按 15 保底（条文 1.2 阶梯不击穿下限）
        assertEquals(ShelfParams.PARTICLE_MIN, particleCount(1))
        assertEquals(ShelfParams.PARTICLE_MIN, particleCount(0))
        for (level in -2..4) {
            val count = particleCount(level)
            assertTrue("粒子数 $count 越出 15-20 区间", count in ShelfParams.PARTICLE_MIN..ShelfParams.PARTICLE_MAX)
        }
    }

    @Test
    fun `倾角恒为5点0度`() {
        assertEquals(5.0, cardTiltDegrees(), delta)
        assertEquals(ShelfParams.CARD_TILT_DEGREES, cardTiltDegrees(), delta)
    }

    @Test
    fun `书名作者竖线颜色分别取token单源`() {
        assertEquals(ComponentPalette.TEXT_PRIMARY, ShelfParams.titleColor)
        assertEquals(ComponentPalette.TEXT_TERTIARY, ShelfParams.authorColor)
        assertEquals(ComponentPalette.TEXT_ACCENT, ShelfParams.progressLineColor)
    }

    @Test
    fun `阴影色金RGB派生自BRAND_GOLD透明度0点3`() {
        val goldRgb = ComponentPalette.BRAND_GOLD and 0xFFFFFFL
        val shadowRgb = ShelfParams.shadowColor and 0xFFFFFFL
        assertEquals(goldRgb, shadowRgb)
        // 0.3 × 255 ≈ 76.5，四舍五入 alpha = 77（0x4D），与冻结暖金辉光 token 一致
        assertEquals(77, (ShelfParams.shadowColor ushr 24).toInt())
        assertEquals(ComponentPalette.COLOR_GLOW, ShelfParams.shadowColor)
    }

    @Test
    fun `常量与锚点卡参数表逐一吻合`() {
        assertEquals(22.0, ShelfParams.TITLE_FONT_SIZE_SP, delta)
        assertTrue(ShelfParams.TITLE_BOLD)
        assertEquals(11.0, ShelfParams.AUTHOR_FONT_SIZE_SP, delta)
        assertEquals(1.0, ShelfParams.PROGRESS_LINE_WIDTH_DP, delta)
        assertEquals(5.0, ShelfParams.CARD_TILT_DEGREES, delta)
        assertEquals(15, ShelfParams.PARTICLE_MIN)
        assertEquals(20, ShelfParams.PARTICLE_MAX)
        assertEquals(12.0, ShelfParams.SHADOW_BLUR_DP, delta)
        assertEquals(0.3, ShelfParams.SHADOW_ALPHA, delta)
    }
}
