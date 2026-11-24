package io.legado.app.ui.components.inkslate.contract

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-12 画板 InkSlate 契约单测（纯 JVM，只用 org.junit + java.*）。
 *
 * 对账口径：切片-12 §8.1 工具集、§8.2 六色、§8.4 导出三件；
 * 色值只认 [ComponentPalette] 既有 token，不造 hex。
 */
class InkSlateContractTest {

    @Test
    fun `工具枚举数量与原文一致`() {
        assertEquals(InkTool.SPEC_COUNT, InkTool.all.size)
        val expected = listOf(
            "画笔", "直线", "箭头", "矩形", "圆", "文字", "橡皮",
            "撤销/重做", "图层（底图+墨迹两层）", "PNG/SVG 导出",
        )
        assertEquals(expected, InkTool.all.map { it.label })
    }

    @Test
    fun `六色数量与原文一致`() {
        assertEquals(InkPalette.SPEC_COUNT, InkPalette.bar.size)
        val expected = listOf("朱砂", "墨灰", "花青", "藤黄", "暖金", "石青")
        assertEquals(expected, InkPalette.bar.map { it.label })
    }

    @Test
    fun `六色每个都映射到 token 且不等于零`() {
        val knownTokens = ComponentPalette.mainTokens + ComponentPalette.STONE_BLUE
        InkPalette.bar.forEach { preset ->
            assertTrue("${preset.label} token 为 0", preset.token != 0L)
            assertTrue("${preset.label} 未收口到既有 token", preset.token in knownTokens)
        }
    }

    @Test
    fun `三件齐全时导出判定为真`() {
        val payload = InkExportPayload(
            bitmapRef = "ink-slate-001.png",
            chapterTitle = "左传·隐公元年",
            plainText = "郑伯克段于鄢。",
        )
        assertTrue(InkExportPayload.hasAllThreeParts(payload))
    }

    @Test
    fun `缺一件时导出判定为假`() {
        val noBitmap = InkExportPayload(
            bitmapRef = null,
            chapterTitle = "左传·隐公元年",
            plainText = "郑伯克段于鄢。",
        )
        assertFalse(InkExportPayload.hasAllThreeParts(noBitmap))

        val blankChapter = InkExportPayload(
            bitmapRef = "ink-slate-001.png",
            chapterTitle = "   ",
            plainText = "郑伯克段于鄢。",
        )
        assertFalse(InkExportPayload.hasAllThreeParts(blankChapter))
    }

    @Test
    fun `缺两件时导出判定为假`() {
        val missingTwo = InkExportPayload(
            bitmapRef = "ink-slate-001.png",
            chapterTitle = null,
            plainText = "",
        )
        assertFalse(InkExportPayload.hasAllThreeParts(missingTwo))
    }

    @Test
    fun `超限尺寸按比例降采样到上限内`() {
        val scaled = InkExportPayload.targetBitmapSize(8192, 4096)
        assertEquals(4096, scaled.width)
        assertEquals(2048, scaled.height)
        assertTrue(maxOf(scaled.width, scaled.height) <= InkExportPayload.MAX_SIDE)
    }

    @Test
    fun `非法宽高返回安全默认尺寸`() {
        val zero = InkExportPayload.targetBitmapSize(0, 0)
        assertEquals(InkExportPayload.MAX_SIDE, zero.width)
        assertEquals(InkExportPayload.MAX_SIDE, zero.height)

        val negative = InkExportPayload.targetBitmapSize(-5, 300)
        assertEquals(InkExportPayload.MAX_SIDE, negative.width)
        assertEquals(InkExportPayload.MAX_SIDE, negative.height)
    }

    @Test
    fun `未超限尺寸原样返回`() {
        val kept = InkExportPayload.targetBitmapSize(1080, 1920)
        assertEquals(1080, kept.width)
        assertEquals(1920, kept.height)
    }

    @Test
    fun `缺 token 的三色已标记待补登记`() {
        val pending = InkPalette.pendingRegistration.map { it.label }
        assertEquals(listOf("墨灰", "花青", "藤黄"), pending)
        InkPalette.bar.filterNot { it.fallback }.forEach {
            assertTrue("${it.label} 应为 token 直接对应", !it.fallback)
        }
        assertEquals(
            listOf("朱砂", "暖金", "石青"),
            InkPalette.bar.filterNot { it.fallback }.map { it.label },
        )
    }
}
