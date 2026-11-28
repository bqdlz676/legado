package io.legado.app.ui.map.globe.host

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * D45 宿主拆分基线机检：[GlobeScreenHost] 行数不得超 604 父基线 + ≤5 净增口径（609）；
 * 回放控制台五组件下沉 [ReplayConsole]、地球仪态 pass 下沉 [EarthGlobePass]，
 * 宿主只留壳职责；回调接线（返回 / 重试 / Sheet 关闭）与三棵手势 pointerInput 不丢。
 *
 * 改坏推演：下沉不彻底导致宿主 >609 行，行数断言先红；旧私有定义残留会被
 * indexOf 扫描命中；回调接线被写成空 lambda，contains 断言红。
 */
class GlobeHostBaselineTest {

    private companion object {
        const val HOST_LIMIT = 609
        const val PASS_FILE_LIMIT = 400
        val hostSource = "io/legado/app/ui/map/globe/host/GlobeScreenHost.kt"
        val replaySource = "io/legado/app/ui/map/globe/host/ReplayConsole.kt"
        val earthPassSource = "io/legado/app/ui/map/globe/host/EarthGlobePass.kt"
    }

    private fun sourceRoot(): File =
        sequenceOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    private fun read(relative: String): String = File(sourceRoot(), relative).readText()

    @Test
    fun `宿主行数不超 604 基线加五净增口径`() {
        val lines = read(hostSource).lines().size
        assertTrue("GlobeScreenHost.kt 行数 $lines 超过 $HOST_LIMIT 基线口径", lines <= HOST_LIMIT)
    }

    @Test
    fun `下沉后的控制台与地球 pass 文件各自不超四百行`() {
        assertTrue("ReplayConsole.kt 超 $PASS_FILE_LIMIT 行", read(replaySource).lines().size <= PASS_FILE_LIMIT)
        assertTrue("EarthGlobePass.kt 超 $PASS_FILE_LIMIT 行", read(earthPassSource).lines().size <= PASS_FILE_LIMIT)
    }

    @Test
    fun `宿主接线下沉聚合入口且回调闭包保留`() {
        val host = read(hostSource)
        assertTrue("宿主应调用 ReplayConsole 聚合入口", host.contains("ReplayConsole("))
        assertTrue("宿主应调用 EarthGlobePass 地球 pass", host.contains("EarthGlobePass("))
        assertTrue("返回闭包 onBack 接线丢失", host.contains("onBack()"))
        assertTrue("重试闭包 retryAction 接线丢失", host.contains("retryAction"))
        assertTrue("Sheet 关闭 selectStar(null) 接线丢失", host.contains("selectStar(null)"))
        assertTrue("帧节拍 LaunchedEffect 丢失", host.contains("LaunchedEffect(Unit)"))
        assertTrue("三棵手势 pointerInput 丢失", host.split("pointerInput").size - 1 >= 3)
    }

    @Test
    fun `宿主无私有控制台与地球 pass 旧定义残留`() {
        val host = read(hostSource)
        val movedDefinitions = listOf(
            "private fun CampaignInfo",
            "private fun CampaignChips",
            "private fun TimelineRow",
            "private fun ControlRow",
            "private fun PowerLegend",
            "private fun projOf",
            "private fun surfaceColor",
        )
        movedDefinitions.forEach { signature ->
            assertTrue(
                "宿主残留旧定义 $signature",
                host.indexOf(signature) < 0,
            )
        }
    }
}
