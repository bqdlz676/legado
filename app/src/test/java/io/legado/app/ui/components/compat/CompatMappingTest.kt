package io.legado.app.ui.components.compat

import io.legado.app.ui.components.HalfSheetHostSnapshot
import io.legado.app.ui.components.ReaderSnapshot
import io.legado.app.ui.components.isRestoredExactly
import io.legado.app.ui.components.inkslate.contract.InkDerivedColors
import io.legado.app.ui.components.inkslate.contract.InkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-11/S-12 双源收口映射单测（纯 JVM，只用 org.junit + java.*）。
 *
 * 对账口径：旧 [HalfSheetHostSnapshot] → 新 [ReaderSnapshot] 单向映射逐字段断言；
 * §1.3 派生三色由基础 token 明度偏移公式复现；六色取名非零且大体互异；
 * 未知工具名回退墨灰不抛异常。
 */
class CompatMappingTest {

    @Test
    fun `旧快照映射后五字段与新契约签名逐字段一致`() {
        val old = HalfSheetHostSnapshot(
            pageIndex = 3,
            scrollOffset = 240,
            highlightKeys = setOf("seg-a", "seg-b"),
        )

        val mapped: ReaderSnapshot = old.toReaderSnapshot()

        assertEquals(old.pageIndex, mapped.chapterIndex)
        assertEquals(old.scrollOffset, mapped.scrollPosition)
        assertEquals(old.highlightKeys, mapped.selectedSegments)
        // 旧契约无段内偏移与可见性布尔，两项兜底值由新契约口径规定
        assertEquals(0, mapped.segmentOffset)
        assertEquals(false, mapped.underlineVisible)
    }

    @Test
    fun `派生三色由基础 token 明度偏移两次复现结果相同`() {
        val grayAgain = InkDerivedColors.darken(
            InkPalette.INK_GRAY.token,
            InkDerivedColors.INK_GRAY_DARKEN_RATIO,
        )
        val indigoAgain = InkDerivedColors.lighten(
            InkPalette.INDIGO.token,
            InkDerivedColors.INDIGO_LIGHTEN_RATIO,
        )
        val gambogeAgain = InkDerivedColors.lighten(
            InkPalette.GAMBOGE.token,
            InkDerivedColors.GAMBOGE_LIGHTEN_RATIO,
        )

        assertEquals(grayAgain, InkDerivedColors.inkGray)
        assertEquals(indigoAgain, InkDerivedColors.indigo)
        assertEquals(gambogeAgain, InkDerivedColors.gamboge)
        // 同一公式第二次调用与第一次一致，差值可复现
        assertEquals(grayAgain, InkDerivedColors.darken(InkPalette.INK_GRAY.token, InkDerivedColors.INK_GRAY_DARKEN_RATIO))
        assertEquals(indigoAgain, InkDerivedColors.lighten(InkPalette.INDIGO.token, InkDerivedColors.INDIGO_LIGHTEN_RATIO))
        assertEquals(gambogeAgain, InkDerivedColors.lighten(InkPalette.GAMBOGE.token, InkDerivedColors.GAMBOGE_LIGHTEN_RATIO))
        // 派生色相对基础 token 确实发生了明度偏移，未原样回抄
        assertNotEquals(InkPalette.INK_GRAY.token, InkDerivedColors.inkGray)
        assertNotEquals(InkPalette.INDIGO.token, InkDerivedColors.indigo)
        assertNotEquals(InkPalette.GAMBOGE.token, InkDerivedColors.gamboge)
    }

    @Test
    fun `六色工具名均返回非零颜色且至少四色互异`() {
        // 说明：花青与石青同属青系就近 token，暖金与藤黄同属黄系，历史临时映射允许相近，
        // 故此处只断言至少四色互异，不要求六色两两不同。
        val names = listOf("朱砂", "墨灰", "花青", "藤黄", "暖金", "石青")
        val colors = names.map { InkDerivedColors.derivedInkColor(it) }

        colors.forEach { color -> assertTrue("派生颜色为 0", color != 0) }
        assertTrue("互异色不足四色", colors.toSet().size >= 4)
    }

    @Test
    fun `未知工具名回退默认墨灰且不抛异常`() {
        val fallback = InkDerivedColors.derivedInkColor("不存在的色名")
        val gray = InkDerivedColors.derivedInkColor("墨灰")

        assertEquals(gray, fallback)
        assertTrue(fallback != 0)
    }

    @Test
    fun `两份一致旧快照映射后还原判定为真`() {
        val beforeOld = HalfSheetHostSnapshot(
            pageIndex = 3,
            scrollOffset = 240,
            highlightKeys = setOf("seg-a", "seg-b"),
        )
        val afterOld = beforeOld.copy()

        val before = beforeOld.toReaderSnapshot()
        val after = afterOld.toReaderSnapshot()

        assertTrue(isRestoredExactly(before, after))
    }
}
