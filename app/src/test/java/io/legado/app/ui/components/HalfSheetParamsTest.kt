package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-11 半屏 Sheet 参数单源单测：纯 JVM，只用 org.junit 与 kotlin 语言层，
 * 不引用任何 Android / Compose 类型，不读资源。
 * 规格：docs/锚点/S-11-半屏Sheet.md 参数表；切片-11 横杠条、下沉 8dp、暗化 40% 原文。
 */
class HalfSheetParamsTest {

    private val delta = 1e-6f

    @Test
    fun `拖拽横杠高度恒为四 dp`() {
        assertEquals(4, HalfSheetParams.GRABBER_BAR_HEIGHT_DP)
    }

    @Test
    fun `阅读页下沉量恒为八 dp`() {
        assertEquals(8, HalfSheetParams.HOST_SINK_DP)
    }

    @Test
    fun `遮罩暗化比恒为零点四`() {
        assertEquals(0.4f, HalfSheetParams.DIM_ALPHA, delta)
    }

    @Test
    fun `Sheet 背景取纸白 token 横杠色取暖金 token`() {
        assertEquals(ComponentPalette.BG_PAGE, HalfSheetParams.sheetBackground)
        assertEquals(ComponentPalette.BRAND_GOLD, HalfSheetParams.grabberColor)
    }

    @Test
    fun `四外观字段全部收口契约参数源 分隔线基色为云灰派生而非旧墨色八成`() {
        // 背景 / 横杠色 / 分隔线宽 / 分隔线色 四字段均从 HalfSheetParams 读取
        assertEquals(ComponentPalette.BG_PAGE, HalfSheetParams.sheetBackground)
        assertEquals(ComponentPalette.BRAND_GOLD, HalfSheetParams.grabberColor)
        assertEquals(1, HalfSheetParams.DIVIDER_WIDTH_DP)

        val dividerRgb = HalfSheetParams.dividerColor and 0xFFFFFFL
        // 色基：RGB 位取自 TEXT_TERTIARY 云灰（锚点 S-11:21）
        assertEquals(ComponentPalette.TEXT_TERTIARY and 0xFFFFFFL, dividerRgb)
        // 不得回退旧墨色 8%（COLOR_DIVIDER，rgba(26,20,16,.08)）那一支
        assertNotEquals(ComponentPalette.COLOR_DIVIDER and 0xFFFFFFL, dividerRgb)
        // 派生只改高位 alpha：0.25 × 255 = 63.75，四舍五入为 64
        assertEquals(64, (HalfSheetParams.dividerColor ushr 24).toInt())
    }

    @Test
    fun `分隔线宽一 dp 色由云灰 token 派生`() {
        assertEquals(1, HalfSheetParams.DIVIDER_WIDTH_DP)
        val tertiaryRgb = ComponentPalette.TEXT_TERTIARY and 0xFFFFFFL
        val dividerRgb = HalfSheetParams.dividerColor and 0xFFFFFFL
        assertEquals(tertiaryRgb, dividerRgb)
        // 派生只改高位 alpha：0.25 × 255 = 63.75，四舍五入为 64
        val dividerAlpha = (HalfSheetParams.dividerColor ushr 24).toInt()
        assertEquals(64, dividerAlpha)
        assertTrue("派生 alpha 必须低于实心", dividerAlpha < 255)
    }

    @Test
    fun `派生色不另造 RGB 只重算透明度`() {
        val tertiaryRgb = ComponentPalette.TEXT_TERTIARY and 0xFFFFFFL
        assertEquals(tertiaryRgb, HalfSheetParams.dividerColor and 0xFFFFFFL)
    }
}
