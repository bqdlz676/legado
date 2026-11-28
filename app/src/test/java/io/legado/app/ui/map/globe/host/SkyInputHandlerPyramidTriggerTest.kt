package io.legado.app.ui.map.globe.host

import io.legado.app.ui.map.globe.GlobeController
import io.legado.app.ui.map.globe.astro.contract.SkyViewMode
import io.legado.app.ui.map.globe.astro.data.PyramidResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 金字塔宿主触发链单验收（A 件 · 方案 §4.1–§4.3）。
 *
 * 纯 JVM：注入 loader 记录被触发的层，注入可控时钟。覆盖——满混合触发 L1 与 L2 各一次；
 * 中混合只触发 L1 不触 L2；失败（Corrupt）进入冷却，冷却期内不再重复触发同一层，错误态
 * 回调收到提示。在途去重与冷却口径与生产一致，不依赖真机渲染。
 *
 * 每条用例注释写明「改坏会红在哪一行」。
 */
class SkyInputHandlerPyramidTriggerTest {

    private lateinit var controller: GlobeController
    private val requested = mutableListOf<Int>()
    private var errorMsg: String? = null
    private var now = 0L

    @Before
    fun setUp() {
        requested.clear()
        errorMsg = null
        now = 1_000L
        controller = GlobeController()
        controller.enterSky(SkyViewMode.SKY_HEAVEN)
    }

    private fun newHandler(loader: (Int) -> PyramidResult): SkyInputHandler =
        SkyInputHandler(
            controller,
            SkyCanvasRenderer,
            pyramidLoader = { level -> requested += level; loader(level) },
            onPyramidError = { errorMsg = it },
            clock = { now },
        )

    @Test
    fun `满混合触发 L1 与 L2 各一次`() {
        val handler = newHandler { level -> PyramidResult.Loaded(level, 1) }
        controller.setSkyBlend(1f) // magLimit 11.5，越过 L2 分界 9.0
        handler.evaluatePyramidDemand()
        // 改坏：若同层在一次评估内重复触发，requested 会出现重复值；下一行有序等值断言变红。
        assertEquals(listOf(1, 2), requested)
    }

    @Test
    fun `中混合只触发 L1 不触发 L2`() {
        val handler = newHandler { level -> PyramidResult.Loaded(level, 1) }
        controller.setSkyBlend(0.5f) // magLimit 9.0，恰在 L1/L2 分界；>9.0 才触 L2
        handler.evaluatePyramidDemand()
        // 改坏：若边界把 9.0 也算进 L2，requested 会多出 2，下一行等值断言变红。
        assertEquals(listOf(1), requested)
    }

    @Test
    fun `失败冷却期内不再触发同一层且错误回调收到提示`() {
        val handler = newHandler { PyramidResult.Corrupt }
        controller.setSkyBlend(1f)
        handler.evaluatePyramidDemand()
        val firstPass = requested.size
        // 冷却 30s 内再次评估：在途已结束，冷却未过，不得重复拉片。
        handler.evaluatePyramidDemand()
        // 改坏：若无冷却，第二次评估会再追加 level 记录，下一行条数等值断言变红。
        assertEquals(firstPass, requested.size)
        assertNotNull("坏片必须回填错误态提示", errorMsg)
        assertTrue(errorMsg!!.contains("回落内置亮星层"))
    }
}
