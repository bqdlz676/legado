package io.legado.app.ui.components.inkslate.contract

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-12 派生语义色同包 contract 单测（纯 JVM，只用 org.junit + java.*，不引 Compose / Android）。
 *
 * 对账口径（docs/落文-模块契约索引.md §1.3）：
 *  - 墨灰 = text-tertiary #9B9687 降明度 18%；
 *  - 花青 = stone-blue #3A6B66 提明度 6%；
 *  - 藤黄 = brand-copper #8B6914 提明度 12%。
 * 断言两层：① 三色具体 ARGB 可复现（不是只断言非空）；② 派生规则——明度偏移比例、
 * alpha 高位字节原样保留、通道级公式重算与集中声明值一致。
 */
class InkDerivedColorsTest {

    private fun alpha(c: Long): Int = (c ushr 24).toInt()
    private fun red(c: Long): Int = ((c shr 16) and 0xFFL).toInt()
    private fun green(c: Long): Int = ((c shr 8) and 0xFFL).toInt()
    private fun blue(c: Long): Int = (c and 0xFFL).toInt()

    @Test
    fun `墨灰由云灰降明度百分之十八派生为固定 ARGB`() {
        // 派生比例口径：18% 降明度
        assertEquals(0.18f, InkDerivedColors.INK_GRAY_DARKEN_RATIO, 1e-6f)
        // 基础色基 = TEXT_TERTIARY 云灰，不取正文墨色 TEXT_PRIMARY
        assertEquals(ComponentPalette.TEXT_TERTIARY, InkPalette.INK_GRAY.token)
        assertNotEquals(ComponentPalette.TEXT_PRIMARY, InkPalette.INK_GRAY.token)

        // 复算：darken 通道公式 c' = round(c × (1 − 0.18))
        val base = ComponentPalette.TEXT_TERTIARY
        val expected = InkDerivedColors.darken(base, InkDerivedColors.INK_GRAY_DARKEN_RATIO)
        assertEquals(expected, InkDerivedColors.inkGray)

        // 具体 ARGB：#9B9687 × 0.82 → R127 G123 B111 = 0xFF7F7B6F
        assertEquals(0xFF7F7B6FL, InkDerivedColors.inkGray)
        assertEquals(127, red(InkDerivedColors.inkGray))
        assertEquals(123, green(InkDerivedColors.inkGray))
        assertEquals(111, blue(InkDerivedColors.inkGray))

        // 派生后相对基础云灰确实变暗，未原样回抄
        assertNotEquals(base, InkDerivedColors.inkGray)
        assertTrue("墨灰应整体暗于基础云灰", red(InkDerivedColors.inkGray) < red(base))
    }

    @Test
    fun `花青由石青提明度百分之六派生为固定 ARGB`() {
        assertEquals(0.06f, InkDerivedColors.INDIGO_LIGHTEN_RATIO, 1e-6f)
        assertEquals(ComponentPalette.STONE_BLUE, InkPalette.INDIGO.token)

        val base = ComponentPalette.STONE_BLUE
        val expected = InkDerivedColors.lighten(base, InkDerivedColors.INDIGO_LIGHTEN_RATIO)
        assertEquals(expected, InkDerivedColors.indigo)

        // 具体 ARGB：#3A6B66 提 6% → R70 G116 B111 = 0xFF46746F
        assertEquals(0xFF46746FL, InkDerivedColors.indigo)
        assertEquals(70, red(InkDerivedColors.indigo))
        assertEquals(116, green(InkDerivedColors.indigo))
        assertEquals(111, blue(InkDerivedColors.indigo))

        assertNotEquals(base, InkDerivedColors.indigo)
        assertTrue("花青应整体亮于基础石青", red(InkDerivedColors.indigo) > red(base))
    }

    @Test
    fun `藤黄由古铜提明度百分之十二派生为固定 ARGB`() {
        assertEquals(0.12f, InkDerivedColors.GAMBOGE_LIGHTEN_RATIO, 1e-6f)
        assertEquals(ComponentPalette.BRAND_COPPER, InkPalette.GAMBOGE.token)

        val base = ComponentPalette.BRAND_COPPER
        val expected = InkDerivedColors.lighten(base, InkDerivedColors.GAMBOGE_LIGHTEN_RATIO)
        assertEquals(expected, InkDerivedColors.gamboge)

        // 具体 ARGB：#8B6914 提 12% → R153 G123 B48 = 0xFF997B30
        assertEquals(0xFF997B30L, InkDerivedColors.gamboge)
        assertEquals(153, red(InkDerivedColors.gamboge))
        assertEquals(123, green(InkDerivedColors.gamboge))
        assertEquals(48, blue(InkDerivedColors.gamboge))

        assertNotEquals(base, InkDerivedColors.gamboge)
        assertTrue("藤黄应整体亮于基础古铜", red(InkDerivedColors.gamboge) > red(base))
    }

    @Test
    fun `派生只偏移明度 alpha 高位字节原样保留`() {
        listOf(
            InkDerivedColors.inkGray,
            InkDerivedColors.indigo,
            InkDerivedColors.gamboge,
        ).forEach { color ->
            assertEquals("派生色 alpha 高位须保留实心 255", 255, alpha(color))
            assertTrue("派生色不得为 0", color != 0L)
        }
    }

    @Test
    fun `六色收口解析与反查闭环 未知色回退墨灰`() {
        // 派生三色走派生值，其余三色色直取基础 token
        assertEquals(InkDerivedColors.inkGray, InkDerivedColors.resolvedColor(InkPalette.INK_GRAY))
        assertEquals(InkDerivedColors.indigo, InkDerivedColors.resolvedColor(InkPalette.INDIGO))
        assertEquals(InkDerivedColors.gamboge, InkDerivedColors.resolvedColor(InkPalette.GAMBOGE))
        assertEquals(
            ComponentPalette.TEXT_ACCENT,
            InkDerivedColors.resolvedColor(InkPalette.VERMILION),
        )
        assertEquals(
            ComponentPalette.BRAND_GOLD,
            InkDerivedColors.resolvedColor(InkPalette.WARM_GOLD),
        )
        assertEquals(
            ComponentPalette.STONE_BLUE,
            InkDerivedColors.resolvedColor(InkPalette.STONE_BLUE),
        )

        // 反查闭环：每个收口色都能回查到对应预设
        InkPalette.bar.forEach { preset ->
            assertNotNull(InkDerivedColors.presetOfArgb(InkDerivedColors.resolvedColor(preset)))
        }

        // 未知工具名回退墨灰，不抛异常
        val fallback = InkDerivedColors.derivedInkColor("不存在的色名")
        assertEquals((InkDerivedColors.inkGray and 0xFFFFFFFFL).toInt(), fallback)
    }
}
