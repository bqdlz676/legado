package io.legado.app.ui.main.bookshelf.search

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S-13 书架下拉浮层 · 外观参数单源验收（纯 JVM，只用 org.junit，不依赖 Android / Compose）。
 *
 * 逐条断言 [SearchOverlayParams] 五个字段的具体默认值（不许只断言非空）：
 * 提示语字号、提示语颜色、高亮色、最大高比、占位文案；颜色必须回指 12 色 token，不许裸 hex。
 */
class SearchOverlayParamsTest {

    @Test
    fun `hint text size is 12sp per slice-13`() {
        assertEquals(12, SearchOverlayParams.HINT_TEXT_SIZE_SP)
    }

    @Test
    fun `hint text color derives from tertiary token`() {
        assertEquals(ComponentPalette.TEXT_TERTIARY, SearchOverlayParams.hintTextColor)
    }

    @Test
    fun `highlight color derives from accent token`() {
        assertEquals(ComponentPalette.TEXT_ACCENT, SearchOverlayParams.highlightColor)
    }

    @Test
    fun `max height ratio defaults to 0 point 6`() {
        assertEquals(0.6f, SearchOverlayParams.MAX_HEIGHT_RATIO, 1e-6f)
    }

    @Test
    fun `placeholder text matches slice-13 verbatim`() {
        assertEquals(
            "搜书名、作者、或正文中的任意一句",
            SearchOverlayParams.PLACEHOLDER_TEXT,
        )
    }
}
