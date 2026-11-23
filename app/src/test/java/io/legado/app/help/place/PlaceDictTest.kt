package io.legado.app.help.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块 D3 · 地名 Provider 验收（规格切片-05 §「点晋纪年时空联动」「坐标可信度分层」
 * 「扩到 18 套」+ 切片-02 §6 PlaceProvider 行）。
 *
 * 必含用例：
 * - 点「晋」、读《左传》（年代锚 -722~-453）命中春秋晋国 P003 绛 / P045 新田；
 * - 现代「晋城」走今址参考层，恒 low，不进默认候选；
 * - 18 套图层年代区间与切片-05 表格逐字一致；
 * - 每条坐标带 tier + source；Tier3 只给县界色块、不产点位；
 * - 每条地名带 fromYear/toYear，支持按年代过滤，负数纪年可用。
 *
 * 本类为宿主 JVM 纯逻辑测试，不依赖 android.Context。
 */
class PlaceDictTest {

    // ---------- 18 套图层：区间逐字核对 ----------

    @Test
    fun `18 套图层齐全且年代区间与切片-05 逐字一致`() {
        val layers = PlaceDict.layers()
        assertEquals(18, layers.size)

        val expected = listOf(
            "西周" to (-1046 to -771),
            "春秋" to (-770 to -476),
            "战国" to (-475 to -221),
            "秦" to (-221 to -206),
            "西汉" to (-202 to 8),
            "东汉" to (25 to 220),
            "三国" to (220 to 280),
            "西晋" to (265 to 316),
            "东晋/十六国" to (317 to 420),
            "南北朝" to (420 to 589),
            "隋" to (581 to 618),
            "唐" to (618 to 907),
            "五代十国" to (907 to 960),
            "北宋/辽" to (960 to 1127),
            "南宋/金/西夏" to (1127 to 1279),
            "元" to (1271 to 1368),
            "明" to (1368 to 1644),
            "清" to (1644 to 1912),
        )
        assertEquals(expected.size, layers.size)
        expected.forEach { (era, span) ->
            val layer = layers.firstOrNull { it.era == era }
            assertTrue("缺少图层：$era", layer != null)
            assertEquals("$era 起年", span.first, layer!!.fromYear)
            assertEquals("$era 止年", span.second, layer.toYear)
        }
    }

    @Test
    fun `图层按单年与区间命中 含负数纪年`() {
        assertTrue(PlaceLayers.at(-722).any { it.era == "春秋" })
        assertTrue(PlaceLayers.at(-453).any { it.era == "春秋" })
        assertTrue(PlaceLayers.at(750).any { it.era == "唐" })
        assertTrue(PlaceLayers.overlaps(-722..-453).any { it.era == "春秋" })
        assertTrue(PlaceLayers.at(1950).isEmpty())
    }

    // ---------- 点「晋」：《左传》年代锚定春秋晋国 ----------

    @Test
    fun `读左传锚 -722 到 -453 命中绛与新田 排掉后世晋`() {
        val candidates = PlaceDict.find("晋", -722..-453)
        val ids = candidates.map { it.placeId }.toSet()

        assertTrue("春秋晋国绛应在候选中", ids.contains("P003"))
        assertTrue("春秋晋国新田应在候选中", ids.contains("P045"))
        assertTrue("晋国实体应在候选中", ids.contains("P-JIN"))
        assertFalse("北魏唐宋晋州不应落在春秋锚点内", ids.contains("P-JINZHOU"))
        assertFalse("现代晋城不应落在春秋锚点内", ids.contains("P-JINCHENG"))

        val hit = PlaceDict.defaultOf(candidates)
        assertTrue("年代锚点内应有可默认命中的春秋晋候选", hit != null)
        assertTrue(hit!!.placeId in setOf("P003", "P045", "P-JIN"))
    }

    @Test
    fun `单年锚点 -600 落在绛与新田之间`() {
        val jiang = PlaceDict.find("绛", year = -600).firstOrNull()
        val xintian = PlaceDict.find("新田", year = -500).firstOrNull()
        assertEquals("P003", jiang?.placeId)
        assertEquals("P045", xintian?.placeId)
    }

    @Test
    fun `同名晋返回候选列表 含诸侯国 晋州 今址晋城`() {
        val all = PlaceDict.find("晋")
        val ids = all.map { it.placeId }.toSet()
        assertTrue(ids.contains("P003"))
        assertTrue(ids.contains("P045"))
        assertTrue(ids.contains("P-JIN"))
        assertTrue(ids.contains("P-JINZHOU"))
        assertTrue(ids.contains("P-JINCHENG"))
    }

    // ---------- 今址参考层 ----------

    @Test
    fun `现代晋城走参考层 恒 low 不进默认候选`() {
        val candidates = PlaceDict.find("晋城")
        assertEquals(1, candidates.size)
        val jincheng = candidates.first()
        assertEquals("P-JINCHENG", jincheng.placeId)
        assertTrue(jincheng.refLayer)
        assertEquals(PlaceConfidence.LOW, jincheng.confidence)
        assertFalse(jincheng.canDefault)
        assertNull(PlaceDict.defaultOf(candidates))

        val geo = PlaceGeo.of("P-JINCHENG")
        assertTrue(geo != null && geo.refLayer)
        assertFalse("今址参考层不落到古地图点位", geo!!.canPlotPoint)
    }

    // ---------- 坐标可信度分层 ----------

    @Test
    fun `每条记录都带 tier 与非空出处`() {
        val all = PlaceDict.all()
        assertTrue(all.size > 10)
        all.forEach { entry ->
            assertTrue("${entry.name} tier 为空", entry.tier.label.isNotBlank())
            assertTrue("${entry.name} 出处为空", entry.source.label.isNotBlank())
            assertTrue("${entry.name} 置信度为空", entry.confidence.wire.isNotBlank())
        }
    }

    @Test
    fun `Tier1 遗址实测 high Tier2 考证 medium Tier3 推定 low`() {
        val all = PlaceDict.all()
        all.first { it.placeId == "P001" }.let {
            assertEquals(PlaceTier.T1_SITE, it.tier)
            assertEquals(PlaceConfidence.HIGH, it.confidence)
        }
        all.first { it.placeId == "P003" }.let {
            assertEquals(PlaceTier.T2_ATLAS, it.tier)
            assertEquals(PlaceConfidence.MEDIUM, it.confidence)
        }
        all.first { it.placeId == "P050" }.let {
            assertEquals(PlaceTier.T3_TEXT, it.tier)
            assertEquals(PlaceConfidence.LOW, it.confidence)
        }
    }

    @Test
    fun `Tier3 只给县界色块 不产出点位`() {
        PlaceDict.all().filter { it.tier == PlaceTier.T3_TEXT }.forEach { entry ->
            val geo = entry.geo
            assertTrue("Tier3 必须有地理色块信息", geo != null)
            assertFalse("Tier3 禁止落点位", geo!!.canPlotPoint)
            assertTrue("Tier3 必须是色块 / 面", geo.isBlock)
            assertTrue("Tier3 必须给县界说明", !geo.regionLabel.isNullOrBlank())
            assertNull("Tier3 不得带经纬度", geo.lng)
            assertNull("Tier3 不得带经纬度", geo.lat)
        }
    }

    @Test
    fun `三源交叉打分 3源high 2源medium 1源low 参考层恒low`() {
        val academic = setOf(PlaceChannel.ARCHAEOLOGY, PlaceChannel.ATLAS, PlaceChannel.TEXT)
        assertEquals(
            PlaceConfidence.HIGH,
            PlaceConfidence.grade(academic, refLayer = false, PlaceTier.T2_ATLAS),
        )
        assertEquals(
            PlaceConfidence.MEDIUM,
            PlaceConfidence.grade(setOf(PlaceChannel.ATLAS, PlaceChannel.TEXT), false, PlaceTier.T2_ATLAS),
        )
        assertEquals(
            PlaceConfidence.LOW,
            PlaceConfidence.grade(setOf(PlaceChannel.TEXT), false, PlaceTier.T3_TEXT),
        )
        assertEquals(
            PlaceConfidence.LOW,
            PlaceConfidence.grade(academic, refLayer = true, PlaceTier.T1_SITE),
        )
        assertEquals(
            PlaceConfidence.HIGH,
            PlaceConfidence.grade(emptySet(), false, PlaceTier.T1_SITE),
        )
    }

    // ---------- 两表：名称快照随年代改名 ----------

    @Test
    fun `place_name 快照按名归并到实体并带年代与出处`() {
        val zhou = PlaceDict.find("成周").firstOrNull()
        val luoyang = PlaceDict.find("雒阳").firstOrNull()
        assertEquals("P020", zhou?.placeId)
        assertEquals("P020", luoyang?.placeId)
        assertEquals("东汉", luoyang?.era)
        assertTrue(zhou!!.fromYear <= -770)
        assertTrue(luoyang!!.source.label.isNotBlank())
    }

    // ---------- 地理信息门面 ----------

    @Test
    fun `PlaceGeo of 按主键返回坐标 未知主键返回 null`() {
        val jiang = PlaceGeo.of("P003")
        assertTrue(jiang != null && jiang.canPlotPoint)
        assertEquals(111.70, jiang!!.lng!!, 0.01)
        assertEquals(35.73, jiang!!.lat!!, 0.01)

        val block = PlaceGeo.of("P050")
        assertTrue(block != null && !block!!.canPlotPoint && block.isBlock)

        assertNull(PlaceGeo.of("P-NO-SUCH"))
        assertNull(PlaceDict.geoOf("P-NO-SUCH"))
    }

    // ---------- 兜底与校验 ----------

    @Test
    fun `查无地名返回空列表 空串不崩`() {
        assertTrue(PlaceDict.find("欧阳锋岛").isEmpty())
        assertTrue(PlaceDict.find("").isEmpty())
        assertTrue(PlaceDict.find("  ").isEmpty())
        assertTrue(PlaceDict.find("「绛」").isNotEmpty())
    }

    @Test
    fun `注册校验拒绝无出处 倒置年份 Tier3 带坐标 悬空名称快照`() {
        val badSource = PlaceEntity(
            placeId = "seed:bad1", kind = PlaceKind.POINT, canonicalName = "无出处邑",
            fromYear = -500, toYear = -400, tier = PlaceTier.T2_ATLAS,
            channels = setOf(PlaceChannel.ATLAS),
            source = PlaceSource.ClassicText(""),
        )
        assertThrowsIllegalArg { PlaceDict.register(listOf(badSource)) }

        val badSpan = PlaceEntity(
            placeId = "seed:bad2", kind = PlaceKind.POINT, canonicalName = "倒置邑",
            fromYear = -400, toYear = -500, tier = PlaceTier.T2_ATLAS,
            channels = setOf(PlaceChannel.ATLAS),
            source = PlaceSource.TanAtlas("谭图·测试卷"),
        )
        assertThrowsIllegalArg { PlaceDict.register(listOf(badSpan)) }

        val badTier3 = PlaceEntity(
            placeId = "seed:bad3", kind = PlaceKind.POLYGON, canonicalName = "越界县",
            fromYear = -500, toYear = -400, tier = PlaceTier.T3_TEXT,
            channels = setOf(PlaceChannel.TEXT),
            source = PlaceSource.ClassicText("《测试·地理志》"),
            lng = 112.0, lat = 35.0,
        )
        assertThrowsIllegalArg { PlaceDict.register(listOf(badTier3)) }

        val danglingName = PlaceNameRow("悬空", "P-NO-SUCH", -500, -400, null, PlaceSource.ClassicText("《测试》"))
        assertThrowsIllegalArg { PlaceDict.register(emptyList(), listOf(danglingName)) }
    }

    private fun assertThrowsIllegalArg(block: () -> Unit) {
        var thrown = false
        try {
            block()
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("应当抛出 IllegalArgumentException", thrown)
    }
}
