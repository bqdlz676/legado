package io.legado.app.help.person

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong
import java.io.File

/**
 * 模块 D2 · 启动预载性能基线（回填验收观察项 O1 接线）。
 *
 * 预算口径：启动→书架 ≤1.8s 总红线（规矩总纲性能红线）；L1 核心预载在 App 启动后台协程执行，
 * 不阻塞主线程 onCreate。本 bench 计时覆盖「l1 互校补录 + core 解析 + 别名归并 + register 建索引」
 * 整条 [PersonAssets.registerCore] 管线；不含 assets 文件 IO（生产走 Context.assets.open，
 * IO 由后台协程兜底，JVM 单测无法复现 android 资产句柄）。
 *
 * 阈值写死：平均 ≤20ms、峰值 ≤100ms；L1 条数 ≥200，资产缩水 / 分层错配直接失败。
 * 注：bench 循环内重复 register 会在目录 external 列表累积（生产为单次预载），阈值按后台
 * 预载宽松口径留余量；真机（荣耀 WIN 12GB）表现待云端 CI 与真机复验。
 */
class PersonPreloadBenchTest {

    private val warm = 10
    private val samples = 30
    private val maxAvgMs = 20.0
    private val maxPeakMs = 100.0

    private fun assetFile(name: String): File =
        listOf(File("app/src/main/assets/$name"), File("src/main/assets/$name"))
            .firstOrNull { it.isFile }
            ?: error("缺少资产文件：$name")

    private fun readAsset(name: String): List<String> = assetFile(name).readLines()

    @Test
    fun `L1 核心预载管线耗时在启动预算内`() {
        val l1Lines = readAsset("person_l1.txt")
        val coreLines = readAsset("person_core.txt")
        val aliasLines = readAsset("person_alias.txt")

        repeat(warm) {
            PersonAssets.registerCore(l1Lines, coreLines, aliasLines)
        }

        val costs = ArrayList<Double>(samples)
        repeat(samples) {
            val t0 = System.nanoTime()
            PersonAssets.registerCore(l1Lines, coreLines, aliasLines)
            costs.add((System.nanoTime() - t0) / 1_000_000.0)
        }
        val avg = costs.average()
        val peak = costs.max()
        println("[person-preload] L1 核心预载 avg=${avg.roundToLong()}ms peak=${peak.roundToLong()}ms")

        assertTrue("预载平均 ${avg}ms 超预算 ${maxAvgMs}ms", avg <= maxAvgMs)
        assertTrue("预载峰值 ${peak}ms 超预算 ${maxPeakMs}ms", peak <= maxPeakMs)

        val l1 = PersonDirectory.countByTier(ConfidenceTier.L1)
        println("[person-preload] L1 在册 $l1 条")
        assertTrue("L1 手校核心应 ≥200 条，实际 $l1", l1 >= 200)
    }
}
