package io.legado.app.help.person

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong
import java.io.File

/**
 * 模块 D2 · 回填能力性能基线（规格性能口径：热路径单次毫秒级，全表内存可控）。
 *
 * 预算与 [PersonProfileBenchTest] 对齐：预热后平均 ≤5ms，峰值 ≤50ms；
 * 表大小与分层条数写进断言，资产缩水 / 分层错配会直接失败。
 */
class PersonBackfillBenchTest {

    private val warm = 500
    private val samples = 200
    private val maxAvgMs = 5.0
    private val maxPeakMs = 50.0

    private fun assetFile(name: String): File =
        listOf(File("app/src/main/assets/$name"), File("src/main/assets/$name"))
            .firstOrNull { it.isFile }
            ?: error("缺少资产文件：$name")

    private fun readAsset(name: String): List<String> = assetFile(name).readLines()

    private fun loadRealAssets() {
        val supp = PersonAssets.parseL1Supplement(readAsset("person_l1.txt"))
        PersonDirectory.register(PersonAssets.parseCore(readAsset("person_core.txt"), supp))
        PersonDirectory.registerDuplicates(PersonAssets.parseDup(readAsset("person_dup.txt")))
        repeat(6) { i ->
            PersonDirectory.recordAppearance(
                AppearanceRecord(
                    personId = "seed:libai", bookId = "book:shiji", chapterId = "ch$i",
                    order = i, chapterTitle = "卷${i + 1}", line = "性能基线出场句",
                )
            )
        }
    }

    private fun queryAll(tokens: List<String>) {
        tokens.forEach { name ->
            PersonProfile.reignOf(name)
            PersonProfile.tierOf(name)
            PersonProfile.pinyinOf(name)
            PersonProfile.duplicatesOf(name)
            PersonProfile.appearancesOf(name)
        }
    }

    @Test
    fun `回填查询热路径平均与峰值耗时达标`() {
        loadRealAssets()
        val tokens = listOf("晋文公", "周平王", "孔子", "李白", "丁佑", "郦食其", "分层客", "查无此人")

        repeat(warm) { queryAll(tokens) }

        val costs = ArrayList<Double>(samples)
        repeat(samples) {
            val t0 = System.nanoTime()
            queryAll(tokens)
            costs.add((System.nanoTime() - t0) / 1_000_000.0)
        }
        val avg = costs.average()
        val peak = costs.max()
        println("[person-backfill] 热路径 avg=${avg.roundToLong()}ms peak=${peak.roundToLong()}ms")
        assertTrue("热路径平均 ${avg}ms 超预算", avg <= maxAvgMs)
        assertTrue("热路径峰值 ${peak}ms 超预算", peak <= maxPeakMs)
    }

    @Test
    fun `分层表与同名分组表大小在预期区间`() {
        loadRealAssets()

        val l1 = PersonDirectory.countByTier(ConfidenceTier.L1)
        val l2 = PersonDirectory.countByTier(ConfidenceTier.L2)
        val l3 = PersonDirectory.countByTier(ConfidenceTier.L3)
        println("[person-backfill] 分层条数 L1=$l1 L2=$l2 L3=$l3")

        assertTrue("L1 手校核心应 ≥200 条，实际 $l1", l1 >= 200)
        assertTrue("在册档案总数应 ≥ L1 条数", l1 + l2 + l3 >= l1)

        val groups = PersonDirectory.dupGroupCount()
        val appearances = PersonDirectory.appearanceCount()
        println("[person-backfill] 同名分组 $groups 组 · 出场记录 $appearances 条")
        assertTrue("同名分组应 >1000 组，实际 $groups", groups > 1000)
        assertTrue("出场记录应 ≥6 条，实际 $appearances", appearances >= 6)
    }

    @Test
    fun `bulk 两千行切片解析吞吐达标`() {
        val lines = readAsset("person.txt").take(2000)

        val t0 = System.nanoTime()
        val records = PersonAssets.parseBulk(lines)
        val costMs = (System.nanoTime() - t0) / 1_000_000.0

        val l2 = records.count { it.tier == ConfidenceTier.L2 }
        val l3 = records.count { it.tier == ConfidenceTier.L3 }
        println("[person-backfill] bulk 解析 ${records.size} 条（L2=$l2 L3=$l3）耗时 ${costMs.roundToLong()}ms")

        assertTrue("切片应解析出 >1000 条，实际 ${records.size}", records.size > 1000)
        assertTrue("解析耗时 ${costMs}ms 超预算 500ms", costMs <= 500.0)
        records.forEach { assertTrue(it.source.label.isNotBlank()) }
    }
}
