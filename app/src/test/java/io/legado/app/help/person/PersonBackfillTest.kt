package io.legado.app.help.person

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 模块 D2 · 回填能力验收（交接清单 #17：在位起讫 / L1-L2-L3 置信分层 / 整本出场持久化表 /
 * 同名分组持久化 / 人名专用异读拼音）。
 *
 * 口径锁定（设计规格 §2.1–2.3 + 历史附件 §2.1）：
 * - L1 手校核心 + 王公纪年，默认展示；L2 多源核验有生卒，补充展示；L3 孤证存疑，只进候选池不默认命中；
 * - 同名分组按 朝代·身份·生卒 打标签，L2 在前、孤证沉底；
 * - 出场表行式持久化，同书同章重复登记覆盖；坏行跳过不抛异常；
 * - 异读拼音整词优先，兜底音节由注入方提供，查不到标「?」，不猜读音。
 *
 * 本类为宿主 JVM 纯逻辑测试，不依赖 android.Context；资产行直接从仓库 assets 目录读取。
 */
class PersonBackfillTest {

    // ---------- 资产读取辅助 ----------

    private fun assetFile(name: String): File =
        listOf(File("app/src/main/assets/$name"), File("src/main/assets/$name"))
            .firstOrNull { it.isFile }
            ?: error("缺少资产文件：$name")

    private fun readAsset(name: String): List<String> = assetFile(name).readLines()

    // ---------- 1. 在位起讫 ----------

    @Test
    fun `core 解析带入位起讫 l1 互校补全`() {
        val supp = PersonAssets.parseL1Supplement(readAsset("person_l1.txt"))
        val records = PersonAssets.parseCore(readAsset("person_core.txt"), supp)

        val jinwen = records.first { it.name == "晋文公" }
        assertEquals(-636, jinwen.reignStart)
        assertEquals(-628, jinwen.reignEnd)

        val pingwang = records.first { it.name == "周平王" }
        assertEquals(-770, pingwang.reignStart)
        assertEquals(-720, pingwang.reignEnd)
    }

    @Test
    fun `目录注册后 reignOf 查询在位起讫 无载为 null`() {
        val supp = PersonAssets.parseL1Supplement(readAsset("person_l1.txt"))
        val records = PersonAssets.parseCore(readAsset("person_core.txt"), supp)
        PersonDirectory.register(records)

        assertEquals(Pair(-636, -628), PersonProfile.reignOf("晋文公"))
        // 种子人物非君主，在位起讫如实为 null
        assertNull(PersonProfile.reignOf("孔子")?.first)
        assertNull(PersonProfile.reignOf("孔子")?.second)
        assertNull(PersonProfile.reignOf("查无此人"))
    }

    // ---------- 2. L1 / L2 / L3 置信分层 ----------

    @Test
    fun `种子档案全部 L1 且已校`() {
        PersonDirectory.allRecords().filter { it.personId.startsWith("seed:") }.forEach { p ->
            assertEquals("${p.name} 分层应为 L1", ConfidenceTier.L1, p.tier)
            assertTrue("${p.name} 种子档案应标已校", p.proofread)
        }
    }

    @Test
    fun `confidence 到分层映射口径`() {
        assertEquals(ConfidenceTier.L1, ConfidenceTier.fromConfidence("high", false))
        assertEquals(ConfidenceTier.L1, ConfidenceTier.fromConfidence("medium", false))
        assertEquals(ConfidenceTier.L3, ConfidenceTier.fromConfidence("low", true))
        assertEquals(ConfidenceTier.L2, ConfidenceTier.fromConfidence(null, true))
        assertEquals(ConfidenceTier.L3, ConfidenceTier.fromConfidence(null, false))
        assertEquals(ConfidenceTier.L2, ConfidenceTier.fromAssetMark("L2"))
        assertEquals(ConfidenceTier.L3, ConfidenceTier.fromAssetMark("L3"))
        assertEquals(ConfidenceTier.L3, ConfidenceTier.fromAssetMark(null))
    }

    @Test
    fun `L3 孤证不默认命中 但候选清单保留`() {
        val l2 = PersonProfile(
            personId = "tier:demo-l2", name = "分层客", nationality = "唐",
            identity = "有生卒核验", biography = "x", notableEvent = "x",
            source = PersonSource.Cbdb("tier-l2"),
            birthYear = 700, deathYear = 750, tier = ConfidenceTier.L2,
        )
        val l3 = PersonProfile(
            personId = "tier:demo-l3", name = "分层客", nationality = "唐",
            identity = "孤证存疑", biography = "y", notableEvent = "y",
            source = PersonSource.Cbdb("tier-l3"), tier = ConfidenceTier.L3,
        )
        // 先注册孤证，再注册核验项；默认命中仍须是 L2
        PersonDirectory.register(listOf(l3, l2))

        val hit = PersonProfile.profileOf("分层客")
        assertNotNull(hit)
        assertEquals("tier:demo-l2", hit!!.personId)
        assertEquals(ConfidenceTier.L2, hit.tier)

        val candidates = PersonProfile.candidatesOf("分层客")
        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.personId == "tier:demo-l3" })
        assertEquals(ConfidenceTier.L2, PersonProfile.tierOf("分层客"))
    }

    @Test
    fun `真实 core 资产全量 L1 且条数达标`() {
        val records = PersonAssets.parseCore(readAsset("person_core.txt"))
        assertTrue("core 档案应 ≥200 条，实际 ${records.size}", records.size >= 200)
        records.forEach { p ->
            assertEquals("${p.name} 应为 L1", ConfidenceTier.L1, p.tier)
            assertTrue("${p.name} 出处为空", p.source.label.isNotBlank())
        }
    }

    // ---------- 3. 同名分组持久化 ----------

    @Test
    fun `dup 资产解析同名分组 丁佑两人均 L2`() {
        val rows = PersonAssets.parseDup(readAsset("person_dup.txt"))
        val dingyou = rows.filter { it.name == "丁佑" }
        assertEquals(2, dingyou.size)
        assertTrue(dingyou.all { it.tier == ConfidenceTier.L2 })
        assertTrue(dingyou.any { it.era == "明" })
        assertTrue(dingyou.any { it.era == "唐" })
    }

    @Test
    fun `分组注册后 duplicatesOf 与标签可用 L2 在前`() {
        val rows = PersonAssets.parseDup(readAsset("person_dup.txt"))
        PersonDirectory.registerDuplicates(rows)

        val group = PersonProfile.duplicatesOf("丁佑")
        assertEquals(2, group.size)
        assertEquals(ConfidenceTier.L2, group.first().tier)
        val tag = group.first().tag()
        assertTrue(tag.contains("明") || tag.contains("唐"))

        val dupTag = PersonDirectory.dupTag("丁佑")
        assertNotNull(dupTag)
        assertTrue(dupTag!!.contains("同名 2 人"))
        assertTrue(PersonDirectory.dupGroupCount() > 0)
    }

    @Test
    fun `孤证分组标签标注不默认命中`() {
        val rows = listOf(
            PersonDupRow("孤证客", 1680, null, "清", "L2", ConfidenceTier.L2, emptyList()),
            PersonDupRow("孤证客", null, null, "清", "孤证", ConfidenceTier.L3, emptyList()),
        )
        PersonDirectory.registerDuplicates(rows)
        val tag = PersonDirectory.dupTag("孤证客")
        assertNotNull(tag)
        assertTrue(tag!!.contains("1 条孤证"))
        val group = PersonProfile.duplicatesOf("孤证客")
        assertEquals(ConfidenceTier.L2, group.first().tier)
    }

    // ---------- 4. 整本出场持久化表 ----------

    @Test
    fun `出场记录按书章排序 摘要首次关键末次齐备`() {
        val recs = listOf(
            AppearanceRecord("出场甲", "book:shiji", "ch3", 3, "卷三", "三度出场"),
            AppearanceRecord("出场甲", "book:shiji", "ch1", 1, "卷一", "初见"),
            AppearanceRecord("出场甲", "book:shiji", "ch2", 2, "卷二", "再遇"),
        )
        PersonDirectory.recordAppearances(recs)

        val list = PersonProfile.appearancesOf("出场甲")
        assertEquals(3, list.size)
        assertEquals("卷一", list.first().chapterTitle)
        assertEquals("卷三", list.last().chapterTitle)

        val summary = PersonDirectory.appearanceSummary("出场甲")
        assertTrue(summary.startsWith("首次　"))
        assertTrue(summary.contains("关键　"))
        assertTrue(summary.contains("末次　"))
        assertTrue(summary.contains("共 1 书 · 3 章 · 3 段"))
    }

    @Test
    fun `同书同章重复登记覆盖旧记录`() {
        val first = AppearanceRecord("覆盖乙", "book:tang", "ch1", 1, "卷一", "旧句")
        val second = AppearanceRecord("覆盖乙", "book:tang", "ch1", 1, "卷一", "新句")
        PersonDirectory.recordAppearance(first)
        PersonDirectory.recordAppearance(second)

        val list = PersonProfile.appearancesOf("覆盖乙")
        assertEquals(1, list.size)
        assertEquals("新句", list.first().line)
    }

    @Test
    fun `出场行编解码 round-trip 坏行容错`() {
        val rec = AppearanceRecord(
            personId = "seed:zhugeliang", bookId = "book:sanguo", chapterId = "ch5",
            order = 5, chapterTitle = "卷五", line = "孔明出场\t含制表符", eventTime = "208",
        )
        val line = AppearanceRecord.encode(rec)
        val back = AppearanceRecord.decode(line)
        assertNotNull(back)
        assertEquals(rec.personId, back!!.personId)
        assertEquals(rec.bookId, back.bookId)
        assertEquals(rec.order, back.order)
        assertEquals(rec.eventTime, back.eventTime)
        // 字段内 Tab 被剥离，一行恰为 7 个字段（6 个 Tab 分隔）
        assertEquals(6, line.count { it == '\t' })

        assertNull(AppearanceRecord.decode("只有两列\t不足"))
        assertNull(AppearanceRecord.decode(""))
    }

    @Test
    fun `出场表落盘文件可回填`() {
        val dir = kotlin.io.path.createTempDirectory("person-pce").toFile()
        val file = File(dir, "pce.txt")
        PersonDirectory.recordAppearance(
            AppearanceRecord("persist:demo", "book:demo", "ch9", 9, "卷九", "落盘验证句")
        )
        PersonDirectory.persistAppearances(file)
        assertTrue(file.exists())

        val restored = PersonDirectory.appearancesOf("persist:demo")
        assertEquals(1, restored.size)
        assertEquals("卷九", restored.first().chapterTitle)

        // 读失败 / 文件不存在保持空表，不抛异常
        PersonDirectory.restoreAppearances(File(dir, "not-exist.txt"))
    }

    // ---------- 5. 人名专用异读拼音 ----------

    @Test
    fun `整词异读命中史传固定读法`() {
        assertEquals("lì yì jī", PersonPinyin.of("郦食其"))
        assertEquals("fán wū jī", PersonPinyin.of("樊於期"))
        assertEquals("lào ǎi", PersonPinyin.of("嫪毐"))
        assertEquals("mò dú", PersonPinyin.of("冒顿"))
        assertEquals("fù yuè", PersonPinyin.of("傅说"))
        assertEquals("gāo yáo", PersonPinyin.of("皋陶"))
    }

    @Test
    fun `姓氏与复姓异读 单字异读回落`() {
        assertTrue(PersonPinyin.of("解狐").startsWith("xiè"))
        assertTrue(PersonPinyin.of("万俟卨").startsWith("mò qí"))
        // 无人名异读、兜底音节为空时标「?」，不猜
        val out = PersonPinyin.of("张三")
        assertTrue(out.endsWith("?"))
    }

    @Test
    fun `目录拼音查询 档案读音优先 注册覆盖次之 内置表兜底`() {
        val prof = PersonProfile(
            personId = "pinyin:demo", name = "拼音客", nationality = "唐",
            identity = "x", biography = "x", notableEvent = "x",
            source = PersonSource.Classic("测试"), pinyin = "demonstration reading",
        )
        PersonDirectory.register(listOf(prof))
        assertEquals("demonstration reading", PersonProfile.pinyinOf("拼音客"))

        PersonDirectory.registerPinyin(mapOf("覆盖客" to "fù gài kè"))
        assertEquals("fù gài kè", PersonProfile.pinyinOf("覆盖客"))

        // 内置整词表兜底
        assertEquals("lì yì jī", PersonProfile.pinyinOf("郦食其"))
        // 无专用读音返回 null，交 D1 字库逐字兜底
        assertNull(PersonProfile.pinyinOf("欧阳锋"))
    }

    // ---------- 资产容错与零 copyleft ----------

    @Test
    fun `bulk 解析有生卒 L2 孤证 L3 坏行跳过`() {
        val lines = listOf(
            " bulk客甲\t子长\t700\t750\t唐\t文学家",
            "bulk客乙\t\t\t\t宋\t",
            "坏行\t列数不足",
            "",
        )
        val records = PersonAssets.parseBulk(lines)
        assertEquals(2, records.size)
        val jia = records.first { it.name == "bulk客甲" }
        assertEquals(ConfidenceTier.L2, jia.tier)
        val yi = records.first { it.name == "bulk客乙" }
        assertEquals(ConfidenceTier.L3, yi.tier)
        // 兜底索引出处如实标注，不伪造 CBDB 收录号
        assertTrue(jia.source.label.contains("兜底索引"))
    }

    @Test
    fun `alias 解析 占位君主条目保留类型 普通人名归并`() {
        val entries = PersonAssets.parseAlias(readAsset("person_alias.txt"))
        assertTrue(entries.isNotEmpty())
        val ruler = entries.firstOrNull { it.owner.startsWith("@") }
        assertNotNull(ruler)
        val named = entries.firstOrNull { !it.owner.startsWith("@") }
        assertNotNull(named)
        assertTrue(named!!.alias.isNotBlank())
    }
}
