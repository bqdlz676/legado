package io.legado.app.ui.welcome

import io.legado.app.ui.components.ComponentPalette
import io.legado.app.ui.splash.contract.SplashParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S-01 启动动画 · 拍板参数「渲染层消费」接线验收（纯 JVM 静态源码断言，
 * 不依赖 Android / Compose 运行时）。
 *
 * 契约值本身由 [SplashParamsTest] 断言；本测试只核对一件事：welcome 渲染层
 * （[SplashScene] / [SplashChoreography]）确实引用契约常量，而不是在画面里另写一份
 * 辉光半径 / 远山浓度 / 标语字号 / 标语色值 / 总时长。
 *
 * 覆盖 2026-11-25 拍板四条：
 *  ① 辉光半径 [SplashParams.GLOW_RADIUS_DP]（40dp）被扫光辉光引用；
 *  ② 远山浓度 [SplashParams.HILL_MAX_ALPHA]（0.25）被远山编排引用；
 *  ③ 标语字号 [SplashParams.SLOGAN_FONT_SIZE_SP]（12sp）被落款引用；
 *  ④ 标语色 [SplashParams.textTertiary] == [ComponentPalette.TEXT_TERTIARY]（云灰 #9B9687），
 *     且渲染层不再引用茶褐副本作标语色。
 */
class SplashRenderWiringTest {

    private val sourceRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    private fun welcomeSource(fileName: String): String =
        File(sourceRoot, "io/legado/app/ui/welcome/$fileName").readText()

    private val scene: String get() = welcomeSource("SplashScene.kt")
    private val engine: String get() = welcomeSource("SplashChoreography.kt")
    private val tokens: String get() = welcomeSource("LuowenSplashTokens.kt")

    @Test
    fun `glow radius is consumed from contract not welcome copy`() {
        assertTrue("扫光辉光必须引用 SplashParams.GLOW_RADIUS_DP", scene.contains("SplashParams.GLOW_RADIUS_DP"))
        assertFalse("渲染层不得再留辉光副本 GOLD_GLOW_DP", scene.contains("GOLD_GLOW_DP"))
        assertFalse("编排层不得再留辉光副本 GOLD_GLOW_DP", engine.contains("GOLD_GLOW_DP"))
        assertEquals(40, SplashParams.GLOW_RADIUS_DP)
    }

    @Test
    fun `hill max alpha is consumed from contract not welcome copy`() {
        assertTrue("远山编排必须引用 SplashParams.HILL_MAX_ALPHA", engine.contains("SplashParams.HILL_MAX_ALPHA"))
        assertFalse("渲染层不得再留远山浓度副本 HILL_MAX_ALPHA", scene.contains("HILL_MAX_ALPHA"))
        assertFalse("welcome token 副本不得再声明远山浓度", tokens.contains("const val HILL_MAX_ALPHA"))
        assertEquals(0.25f, SplashParams.HILL_MAX_ALPHA, 1e-6f)
    }

    @Test
    fun `slogan size and tertiary color are consumed from contract`() {
        assertTrue("落款字号必须引用 SplashParams.SLOGAN_FONT_SIZE_SP", scene.contains("SplashParams.SLOGAN_FONT_SIZE_SP"))
        assertTrue("落款色必须引用 SplashParams.textTertiary", scene.contains("SplashParams.textTertiary"))
        assertFalse("渲染层不得再留标语色副本 LuowenSplash.textTertiary", scene.contains("LuowenSplash.textTertiary"))
        assertFalse("渲染层不得内联云灰 hex #9B9687", scene.contains("9B9687"))
        assertEquals(12f, SplashParams.SLOGAN_FONT_SIZE_SP, 1e-6f)
        assertEquals(ComponentPalette.TEXT_TERTIARY, SplashParams.textTertiary)
    }

    @Test
    fun `welcome token copy keeps no duplicated decision constants`() {
        // 拍板外观 / 时序值单源在 SplashParams；welcome 副本里出现同名「声明」即第二份口径漂移。
        // 只匹配声明形态（val / const val 前缀），注释里的回源提及不算副本。
        listOf(
            "const val GOLD_GLOW_DP",
            "const val HILL_MAX_ALPHA",
            "const val SLOGAN_SP",
            "val textTertiary",
            "const val TOTAL_MS",
        ).forEach { banned ->
            assertFalse("welcome token 副本不得再声明 $banned", tokens.contains(banned))
        }
    }

    @Test
    fun `total duration consumed from contract in both scene and engine`() {
        assertTrue("场景帧时钟必须引用 SplashParams.TOTAL_MS", scene.contains("SplashParams.TOTAL_MS"))
        assertTrue("编排引擎必须引用 SplashParams.TOTAL_MS", engine.contains("SplashParams.TOTAL_MS"))
        assertEquals(2800, SplashParams.TOTAL_MS)
    }
}
