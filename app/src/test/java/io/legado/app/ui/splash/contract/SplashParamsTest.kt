package io.legado.app.ui.splash.contract

import io.legado.app.ui.components.ComponentPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S-01 启动动画 · 拍板数值契约验收（纯 JVM，只用 org.junit + java.*，
 * 不依赖 Android / Compose / 资源）。
 *
 * 覆盖 2026-11-25 用户拍板落码值：
 *  ① 远山最高不透明度 0.25f；② 辉光半径 40dp；③ 标语字号 12sp；
 *  ④ 标语色 == [ComponentPalette.TEXT_TERTIARY]（云灰 #9B9687，不得回退茶褐）。
 */
class SplashParamsTest {

    @Test
    fun `远山最高不透明度拍板为零点二五`() {
        assertEquals(0.25f, SplashParams.HILL_MAX_ALPHA, 1e-6f)
    }

    @Test
    fun `辉光半径拍板为四十dp`() {
        assertEquals(40, SplashParams.GLOW_RADIUS_DP)
    }

    @Test
    fun `标语字号保持十二sp`() {
        assertEquals(12f, SplashParams.SLOGAN_FONT_SIZE_SP, 1e-6f)
    }

    @Test
    fun `标语色取云灰TEXT_TERTIARY`() {
        assertEquals(ComponentPalette.TEXT_TERTIARY, SplashParams.textTertiary)
    }
}
