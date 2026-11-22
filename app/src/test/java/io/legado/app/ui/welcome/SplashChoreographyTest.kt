package io.legado.app.ui.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 启动动画编排引擎单测（纯 JVM，无 Android 依赖）。
 *
 * 逐条核对《落文 · 启动动画》时间线相位、粒子预算、边界兜底与淡出次序。
 */
class SplashChoreographyTest {

    private val width = 1080f
    private val height = 2280f
    private val density = 2.75f

    private fun engineAt(tMs: Long): SplashChoreography {
        val e = SplashChoreography()
        e.update(tMs, width, height, density)
        return e
    }

    @Test
    fun `particle budget ink 48 gold 76 total under 140`() {
        val e = SplashChoreography()
        assertEquals(LuowenSplash.INK_COUNT, e.ink.size)
        assertEquals(LuowenSplash.GOLD_COUNT, e.gold.size)
        assertTrue(e.ink.size + e.gold.size <= 140)
        val round = e.ink.count { it.kind == SplashChoreography.InkKind.ROUND }
        val filament = e.ink.count { it.kind == SplashChoreography.InkKind.FILAMENT }
        val splat = e.ink.count { it.kind == SplashChoreography.InkKind.SPLAT }
        assertEquals(LuowenSplash.INK_ROUND, round)
        assertEquals(LuowenSplash.INK_FILAMENT, filament)
        assertEquals(LuowenSplash.INK_SPLAT, splat)
    }

    @Test
    fun `phase follows timeline drop burst hold gather gold hill sweep slogan fade done`() {
        assertEquals(SplashChoreography.Phase.DROP, engineAt(200).phaseAt(200))
        assertEquals(SplashChoreography.Phase.BURST, engineAt(500).phaseAt(500))
        assertEquals(SplashChoreography.Phase.HOLD, engineAt(750).phaseAt(750))
        assertEquals(SplashChoreography.Phase.GATHER, engineAt(900).phaseAt(900))
        assertEquals(SplashChoreography.Phase.GOLD_BURST, engineAt(1100).phaseAt(1100))
        assertEquals(SplashChoreography.Phase.HILL, engineAt(1400).phaseAt(1400))
        assertEquals(SplashChoreography.Phase.SWEEP, engineAt(1600).phaseAt(1600))
        assertEquals(SplashChoreography.Phase.SLOGAN, engineAt(1900).phaseAt(1900))
        assertEquals(SplashChoreography.Phase.FADE, engineAt(2500).phaseAt(2500))
        assertEquals(SplashChoreography.Phase.DONE, engineAt(2800).phaseAt(2800))
    }

    @Test
    fun `drop falls with gravity and ink hidden before burst`() {
        val e = engineAt(200)
        assertTrue(e.dropAlpha > 0f)
        assertTrue(e.dropY > 0f)
        assertTrue(e.ink.all { it.alpha == 0f })
    }

    @Test
    fun `burst particles stay inside 60dp radius`() {
        val e = engineAt(700)
        val cx = width * 0.5f
        val cy = height * 0.48f
        val radiusPx = LuowenSplash.BURST_RADIUS_DP * density
        e.ink.forEach { p ->
            assertTrue(kotlin.math.abs(p.x - cx) <= radiusPx * 1.05f)
            assertTrue(kotlin.math.abs(p.y - cy) <= radiusPx * 0.82f * 1.05f)
        }
    }

    @Test
    fun `glyph luo appears before wen and stroke grows`() {
        val early = engineAt(950)
        assertTrue(early.glyphLuoAlpha >= early.glyphWenAlpha)
        val late = engineAt(1200)
        assertTrue(late.luoStroke >= late.wenStroke)
        assertTrue(late.glyphLuoAlpha > 0f)
    }

    @Test
    fun `gold bursts then fades to zero by end of life`() {
        val burst = engineAt(1100)
        assertTrue(burst.gold.any { it.alpha > 0f })
        val gone = engineAt(LuowenSplash.GOLD_LIFE_MS)
        assertTrue(gone.gold.all { it.alpha == 0f })
    }

    @Test
    fun `hill fades before glyphs and wen stroke survives luo in late fade`() {
        val midFade = engineAt(2300)
        assertTrue(midFade.hillFadeAlpha() < midFade.hillAlpha)
        val lateFade = engineAt(2700)
        assertTrue(lateFade.glyphWenAlpha >= lateFade.glyphLuoAlpha)
    }

    @Test
    fun `slogan fades in after 1800 and holds copy`() {
        val before = engineAt(1700)
        assertEquals(0f, before.sloganAlpha, 1e-6f)
        val shown = engineAt(2100)
        assertTrue(shown.sloganAlpha > 0f)
    }

    @Test
    fun `fixed seed reproduces identical frame`() {
        val a = SplashChoreography(seed = SplashChoreography.DEFAULT_SEED)
        val b = SplashChoreography(seed = SplashChoreography.DEFAULT_SEED)
        a.update(600, width, height, density)
        b.update(600, width, height, density)
        a.ink.zip(b.ink).forEach { (x, y) ->
            assertEquals(x.x, y.x, 1e-4f)
            assertEquals(x.y, y.y, 1e-4f)
        }
    }

    @Test
    fun `elapsed clamps to total duration`() {
        val e = engineAt(9999)
        assertEquals(LuowenSplash.TOTAL_MS, e.elapsedMs)
        assertEquals(0f, e.fadeAlpha, 1e-6f)
    }

    @Test
    fun `sweep ends in gold dot at wen final stroke`() {
        val afterSweep = engineAt(1800 + 160)
        assertTrue(afterSweep.goldDotAlpha > 0f)
        assertTrue(afterSweep.sweepProgress >= 1f)
    }
}
