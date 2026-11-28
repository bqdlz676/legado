package io.legado.app.ui.map.globe.astro.contract

import io.legado.app.ui.map.globe.astro.data.StarCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 星表覆盖随 blend 单调不减：blend 0（6.5 等）→ blend 1（11.5 等），可见星数只增不减。
 *
 * 〔数据口径〕只读仓库内置 `app/src/main/assets/astro/stars.csv` 真实数据，经
 * [StarCatalog.parseRows] 离线解析，不在测试里造星。内置 L0 亮星层星等全部 ≤6.5，
 * 故本离线样本在 blend=0 即已饱和；暗星增量属金字塔层（L1/L2），需缓存片或远程片源，
 * 不属离线单测面，本文件不构造夹具星。
 *
 * 纯 JVM：不依赖 Android AssetManager；文件定位范式与 SkyBenchTest 一致，从 user.dir
 * 向上找含 `app/src/main/assets/astro/stars.csv` 的仓库根。
 */
class StarCatalogBlendCoverageTest {

    private companion object {
        /** blend 0→1 的采样段数；与 [SkyZoomContinuityTest] 同口径。 */
        const val SAMPLE_STEPS = 20
    }

    private fun builtinCatalog(): StarCatalog {
        val cwd = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(cwd) { it.parentFile }
            .first { File(it, "app/src/main/assets/astro/stars.csv").isFile }
        val lines = File(root, "app/src/main/assets/astro/stars.csv").readLines()
        val rows = lines.drop(1).filter { it.isNotBlank() }.map { it.split(",") }
        return StarCatalog.parseRows(rows)
    }

    /**
     * 随 blend 递增，`mag ≤ magLimit` 的星数单调不减；端点覆盖口径与内置层一致。
     *
     * 改坏推演：若星等上限曲线被改回档位制、或裁剪口径在 blend 增大时误丢亮星，
     * 相邻采样点可见星数回退，「单调不减」断言在对应 blend 行红。
     * 注：内置 L0 全量 ≤6.5 等，首末计数都等于目录规模；本断言锁的是「不回退」，
     * 暗星随 blend 放开的增量由金字塔层测试与渲染侧裁剪单测覆盖。
     */
    @Test
    fun visibleCountMonotonicNonDecreasingAsBlendGrows() {
        val catalog = builtinCatalog()
        assertTrue("内置星表离线解析不得为空", catalog.size > 0)

        val counts = mutableListOf<Int>()
        for (i in 0..SAMPLE_STEPS) {
            val blend = i / SAMPLE_STEPS.toFloat()
            val limit = SkyParams.magLimitFromBlend(blend)
            var visible = 0
            for (k in 0 until catalog.size) {
                val mag = catalog.magAt(k)
                if (mag.isFinite() && mag <= limit) visible++
            }
            counts += visible
        }

        for (i in 1 until counts.size) {
            val blend = i / SAMPLE_STEPS.toFloat()
            assertTrue(
                "blend=$blend 可见星数回退：${counts[i - 1]} -> ${counts[i]}",
                counts[i] >= counts[i - 1],
            )
        }

        // 内置亮星层在 6.5 等上限内全量可见：首末计数都等于目录规模。
        assertEquals(catalog.size, counts.first())
        assertEquals(catalog.size, counts.last())
    }
}
