package io.legado.app.ui.font.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-14 字体回退契约单测：[resolveFontChain] / [shouldFallback] 纯函数口径锁定，
 * 纯 JVM，不引用 Android 类型。
 */
class FontFallbackContractTest {

    @Test
    fun `preferred font sits at chain head`() {
        val chain = resolveFontChain("MySong", listOf("MySong", "system-serif"))
        assertEquals("MySong", chain.first())
    }

    @Test
    fun `chain tail is system serif`() {
        val chain = resolveFontChain(
            "MySong",
            listOf("MySong", "Noto Serif CJK SC", "system-serif")
        )
        assertEquals("system-serif", chain.last())
    }

    @Test
    fun `blank preferred does not throw`() {
        val chain = resolveFontChain("", listOf("Noto Serif CJK SC", "system-serif"))
        assertNotNull(chain)
        assertFalse(chain.isEmpty())
        assertEquals("system-serif", chain.last())
    }

    @Test
    fun `empty available does not throw`() {
        val chain = resolveFontChain("MySong", emptyList())
        assertNotNull(chain)
        assertFalse(chain.isEmpty())
        assertEquals("system-serif", chain.last())
    }

    @Test
    fun `zero missing glyphs does not fallback`() {
        assertFalse(shouldFallback("Noto Serif", 0))
    }

    @Test
    fun `positive missing glyphs triggers fallback`() {
        assertTrue(shouldFallback("Noto Serif", 3))
        assertTrue(shouldFallback("", 1))
    }

    @Test
    fun `load timeout constant equals 800`() {
        assertEquals(800L, FontFallbackParams.LOAD_TIMEOUT_MS)
    }
}
