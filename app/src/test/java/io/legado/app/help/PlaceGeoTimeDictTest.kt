package io.legado.app.help

import io.legado.app.help.chrono.ChronoPlaces
import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 模块 8 · 点晋闭环 / 地名时空消歧 —— 单元测试 + bench。
 *
 * 覆盖（对齐 core 契约 §一 / 数据 schema 契约 / §七 验收）：
 *  - [ContextAnchor] 纯 Kotlin 内核：按年代过滤、同名消歧、晋城「今址参考层」降级、
 *    锚点为空不猜、low 不进默认命中、空名/未知名/越界年份不崩；
 *  - 真实资产文件 place_entity.txt / place_name.txt：schema 完整性、外键完整、
 *    灵魂闭环 traces（晋/-700 → 春秋晋国；周/-700 → 洛邑；郑/-700 → 新郑）；
 *  - cities.js：现代晋城已移出主 CITIES 层，仅留 CITIES_REF 今址参考层（ref=true, low）；
 *  - powers.js：春秋晋势力色块存在，中心对齐晋都（绛/新田）；
 *  - bench：resolve 实测延迟（毫秒），数字打到测试日志。
 *
 * 说明：[PlaceGeo.of] / [PlaceTimeDict.resolve] 签名依赖 Android Context，JVM 单测无法
 * 直接实例化；其纯逻辑规则（区间命中 + 非 low 才进默认）在本测试的资产镜像索引中等价验证。
 * Android 桥接层本身未在本机真机/模拟器上验证，交付报告中显式标注。
 */
class PlaceGeoTimeDictTest {

    @Before
    fun setUp() {
        ChronoPlaces.resetForTest()
        ContextAnchor.personProvider = null
        ContextAnchor.allusionProvider = null
    }

    // ---------- A. 纯内核消歧（ChronoPlaces + ContextAnchor） ----------

    @Test
    fun `jin at -700 prefers spring-autumn jin state, modern jincheng is non-default low ref`() {
        val cands = ContextAnchor.resolve("晋", -700)
        assertTrue(cands.isNotEmpty())

        val hit = cands.first()
        assertEquals(ContextAnchor.Type.PLACE, hit.type)
        assertEquals("P003", hit.id)
        assertEquals("绛", hit.name)
        assertTrue(-700 in (hit.start ?: 0)..(hit.end ?: 0))
        assertTrue(hit.isDefault)
        assertTrue(hit.aliases.contains("晋国"))

        val modern = cands.firstOrNull { it.id == "P057" }
        assertNotNull("现代晋城应进今址参考层候选", modern)
        assertFalse(modern!!.isDefault)
        assertEquals(ContextAnchor.Confidence.LOW, modern.confidence)
    }

    @Test
    fun `zhou at -700 resolves to eastern zhou luoyi high confidence`() {
        val cands = ContextAnchor.resolve("周", -700)
        val hit = cands.first()
        assertEquals("P042", hit.id)
        assertEquals("洛邑", hit.name)
        assertTrue(hit.isDefault)
        assertEquals(ContextAnchor.Confidence.HIGH, hit.confidence)
    }

    @Test
    fun `zheng at -700 resolves to zheng capital xinzheng`() {
        val cands = ContextAnchor.resolve("郑", -700)
        val hit = cands.first()
        assertEquals("P008", hit.id)
        assertEquals("新郑", hit.name)
        assertTrue(hit.isDefault)
        assertTrue(-700 in (hit.start ?: 0)..(hit.end ?: 0))
    }

    @Test
    fun `lu at -722 resolves to lu capital qufu`() {
        val cands = ContextAnchor.resolve("鲁", -722)
        val hit = cands.first()
        assertEquals("P001", hit.id)
        assertEquals("曲阜", hit.name)
        assertTrue(hit.isDefault)
    }

    @Test
    fun `same name disambiguated by era: jiang at -700, xintian at -500`() {
        // -700：绛（故绛）在区间内；新田（迁侯马在前 585 年后）未到 → 绛
        val early = ContextAnchor.resolve("晋", -700)
        assertEquals("P003", early.first().id)

        // -500：绛、新田同在区间；区间更窄的新田优先（景公迁新田后）
        val late = ContextAnchor.resolve("晋", -500)
        assertEquals("P045", late.first().id)
        assertTrue(late.first().isDefault)
    }

    @Test
    fun `anchor year absent returns all candidates with no default`() {
        val cands = ContextAnchor.resolve("晋", null)
        assertTrue(cands.size > 1)
        assertTrue(cands.all { !it.isDefault })
    }

    @Test
    fun `low confidence only match never becomes default`() {
        // 2000 年：只有现代晋城在区间内且为 low → 不设默认命中
        val cands = ContextAnchor.resolve("晋", 2000)
        assertTrue(cands.isNotEmpty())
        assertTrue(cands.all { !it.isDefault })
        assertTrue(cands.any { it.confidence == ContextAnchor.Confidence.LOW })
    }

    @Test
    fun `blank and unknown names resolve empty without crash`() {
        assertTrue(ContextAnchor.resolve("", -700).isEmpty())
        assertTrue(ContextAnchor.resolve("   ", null).isEmpty())
        assertTrue(ContextAnchor.resolve("覅瓠蠿", -700).isEmpty())
    }

    @Test
    fun `out of range years do not crash and yield no in-span default`() {
        listOf(-3000, -10000, 3000, 30000).forEach { y ->
            val cands = ContextAnchor.resolve("晋", y)
            assertTrue(cands.isNotEmpty())
            assertTrue(cands.all { !it.isDefault })
        }
    }

    // ---------- B. 真实资产文件校验 + 镜像索引消歧 ----------

    private data class MirrorEnt(
        val id: String,
        val kind: Char,
        val name: String,
        val lng: Double,
        val lat: Double,
        val start: Int,
        val end: Int,
        val tier: Int,
        val note: String,
        val aliases: List<String>,
        val source: String,
        val confidence: String,
    ) {
        val isPoint: Boolean get() = kind == 'P'
        fun inSpan(year: Int): Boolean = year in start..end
    }

    private class MirrorIndex(ents: List<MirrorEnt>, nameRows: List<NameRow>) {
        data class NameRow(val name: String, val entId: String, val confidence: String)

        private val byId: Map<String, MirrorEnt> = ents.associateBy { it.id }
        private val byName: Map<String, List<MirrorEnt>>

        init {
            val map = HashMap<String, MutableList<MirrorEnt>>()
            ents.forEach { e ->
                map.getOrPut(e.name) { ArrayList(2) }.add(e)
                e.aliases.forEach { a -> map.getOrPut(a) { ArrayList(2) }.add(e) }
            }
            nameRows.forEach { row ->
                val e = byId[row.entId] ?: return@forEach
                map.getOrPut(row.name) { ArrayList(2) }.add(e)
            }
            byName = map.mapValues { (_, v) -> v.distinctBy { it.id } }
        }

        fun candidates(name: String): List<MirrorEnt> = byName[name.trim()].orEmpty()

        fun resolve(name: String, year: Int?): Pair<MirrorEnt?, List<MirrorEnt>> {
            val cands = candidates(name)
            if (cands.isEmpty()) return null to emptyList()
            if (year == null) return null to cands
            val pool = cands.filter { it.inSpan(year) }.ifEmpty { cands }
            val sorted = pool.sortedWith(
                compareBy<MirrorEnt> { if (it.inSpan(year)) 0 else 1 }
                    .thenBy { it.tier }
                    .thenBy { it.end - it.start }
                    .thenBy { if (it.isPoint) 0 else 1 }
            )
            val hit = sorted.firstOrNull()
            val others = cands.filter { it.id != hit?.id }
            return hit to others
        }

        fun isDefault(e: MirrorEnt, year: Int?): Boolean =
            year != null && e.confidence != "low" && e.inSpan(year)
    }

    private fun repositoryRoot(): File {
        val userDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDirectory) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
    }

    private fun loadMirror(): Triple<MirrorIndex, List<MirrorEnt>, List<MirrorIndex.NameRow>> {
        val root = repositoryRoot()
        val entFile = File(root, "app/src/main/assets/place_entity.txt")
        val nameFile = File(root, "app/src/main/assets/place_name.txt")
        assertTrue("place_entity.txt 必须存在", entFile.isFile)
        assertTrue("place_name.txt 必须存在", nameFile.isFile)

        val ents = ArrayList<MirrorEnt>()
        entFile.readLines().forEachIndexed { idx, line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val p = line.split('\t')
            if (p.size < 9) error("entity 行 $idx 列数不足: $line")
            val lng = p[3].toDoubleOrNull() ?: error("entity 行 $idx 经度不可解析: ${p[3]}")
            val lat = p[4].toDoubleOrNull() ?: error("entity 行 $idx 纬度不可解析: ${p[4]}")
            val start = p[5].toIntOrNull() ?: -1046
            val end = p[6].toIntOrNull() ?: 1911
            assertTrue("entity ${p[0]} 止年早于起年", end >= start)
            assertTrue("entity ${p[0]} 经度越界", lng in -180.0..180.0)
            assertTrue("entity ${p[0]} 纬度越界", lat in -90.0..90.0)
            val conf = p.getOrNull(15).orEmpty().ifBlank {
                when (p[8].toIntOrNull() ?: 3) { 1 -> "high"; 2 -> "medium"; else -> "low" }
            }
            assertTrue("entity ${p[0]} 置信度非法: $conf", conf in setOf("high", "medium", "low"))
            ents.add(
                MirrorEnt(
                    id = p[0],
                    kind = if (p[1].startsWith("A")) 'A' else 'P',
                    name = p[2],
                    lng = lng, lat = lat,
                    start = start, end = end,
                    tier = p[8].toIntOrNull() ?: 3,
                    note = p.getOrNull(9).orEmpty(),
                    aliases = p.getOrNull(10).orEmpty()
                        .split('|').map { it.trim() }.filter { it.isNotEmpty() },
                    source = p.getOrNull(13).orEmpty(),
                    confidence = conf,
                )
            )
        }

        val nameRows = ArrayList<MirrorIndex.NameRow>()
        val entIds = ents.map { it.id }.toSet()
        nameFile.readLines().forEachIndexed { idx, line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val p = line.split('\t')
            if (p.size < 2) error("name 行 $idx 列数不足: $line")
            assertTrue("name 行 $idx 外键 ${p[1]} 在实体表中不存在", p[1] in entIds)
            val conf = p.getOrNull(8).orEmpty().ifBlank { "medium" }
            assertTrue("name ${p[0]} 置信度非法: $conf", conf in setOf("high", "medium", "low"))
            nameRows.add(MirrorIndex.NameRow(p[0], p[1], conf))
        }
        return Triple(MirrorIndex(ents, nameRows), ents, nameRows)
    }

    @Test
    fun `asset schema integrity: fk, span, coords, confidence all valid`() {
        val (_, ents, nameRows) = loadMirror()
        assertTrue("实体数量下限 100", ents.size >= 100)
        assertTrue("名称快照行数下限 300", nameRows.size >= 300)
    }

    @Test
    fun `asset soul loop: jin zhou zheng lu resolve correctly at anchor years`() {
        val (idx, _, _) = loadMirror()

        val (jinHit, jinOthers) = idx.resolve("晋", -700)
        assertNotNull(jinHit)
        assertEquals("P003", jinHit!!.id)
        assertTrue(idx.isDefault(jinHit, -700))
        val jincheng = jinOthers.firstOrNull { it.id == "P057" }
        assertNotNull(jincheng)
        assertEquals("low", jincheng!!.confidence)
        assertFalse(idx.isDefault(jincheng, -700))

        val (zhouHit, _) = idx.resolve("周", -700)
        assertNotNull(zhouHit)
        assertEquals("P042", zhouHit!!.id)
        assertTrue(idx.isDefault(zhouHit, -700))

        val (zhengHit, _) = idx.resolve("郑", -700)
        assertNotNull(zhengHit)
        assertEquals("P008", zhengHit!!.id)

        val (luHit, _) = idx.resolve("鲁", -722)
        assertNotNull(luHit)
        assertEquals("P001", luHit!!.id)
    }

    @Test
    fun `asset modern jincheng sits in ref layer only, not main city layer`() {
        val root = repositoryRoot()
        val cities = File(root, "app/src/main/assets/map/cities.js").readText()

        val mainBlock = cities.substringAfter("window.CITIES =").substringBefore("window.CITIES_REF")
        assertFalse("主 CITIES 层不得收录现代晋城", mainBlock.contains("晋城"))

        val refBlock = cities.substringAfter("window.CITIES_REF")
        assertTrue("CITIES_REF 须收录晋城", refBlock.contains("\"晋城\""))
        assertTrue("晋城参考项须 ref=true", refBlock.contains("\"ref\": true"))
        assertTrue("晋城参考项须为 low", refBlock.contains("\"confidence\": \"low\""))
    }

    @Test
    fun `powers asset carries spring-autumn jin power centered on jin capital`() {
        val root = repositoryRoot()
        val powers = File(root, "app/src/main/assets/map/powers.js").readText()
        assertTrue("powers.js 须含春秋晋势力色块", powers.contains("\"name\":\"晋\",\"era\":\"春秋\""))
        // 中心 [35.7, 111.4] ≈ 绛（今翼城），不得落到现代晋城 [35.49, 112.85]
        assertTrue(powers.contains("[35.7,111.4]"))
        assertFalse(powers.contains("35.49"))
    }

    // ---------- C. bench：resolve 实测延迟 ----------

    @Test
    fun `bench resolve latency kernel and asset mirror`() {
        val n = 5_000

        // 1) 纯内核 ContextAnchor.resolve
        val queries = listOf("晋", "周", "郑", "鲁", "覅瓠")
        repeat(200) { queries.forEach { ContextAnchor.resolve(it, -700) } } // warmup
        val kernelStart = System.nanoTime()
        repeat(n) { queries.forEach { ContextAnchor.resolve(it, -700) } }
        val kernelTotalMs = (System.nanoTime() - kernelStart) / 1_000_000.0
        val kernelOps = n * queries.size
        val kernelAvgMs = kernelTotalMs / kernelOps
        println("[BENCH] kernel ContextAnchor.resolve n=$kernelOps total=${"%.2f".format(kernelTotalMs)}ms " +
            "avg=${"%.4f".format(kernelAvgMs)}ms/op")

        // 2) 资产镜像索引 resolve（含真实两表数据）
        val (idx, _, _) = loadMirror()
        repeat(200) { queries.forEach { idx.resolve(it, -700) } } // warmup
        val assetStart = System.nanoTime()
        repeat(n) { queries.forEach { idx.resolve(it, -700) } }
        val assetTotalMs = (System.nanoTime() - assetStart) / 1_000_000.0
        val assetAvgMs = assetTotalMs / kernelOps
        println("[BENCH] asset-mirror resolve n=$kernelOps total=${"%.2f".format(assetTotalMs)}ms " +
            "avg=${"%.4f".format(assetAvgMs)}ms/op")

        // 3) 两表冷加载一次的耗时（assets 读取在真机走 AssetManager，这里测文件解析口径）
        val loadStart = System.nanoTime()
        repeat(20) { loadMirror() }
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000.0 / 20
        println("[BENCH] mirror two-table parse avg=${"%.2f".format(loadMs)}ms/cold-load")

        // 红线参照：气泡热路径 ≤50ms；单次 resolve 期望亚毫秒～个位数毫秒
        assertTrue("内核单次 resolve 超过 5ms: $kernelAvgMs", kernelAvgMs < 5.0)
        assertTrue("资产镜像单次 resolve 超过 5ms: $assetAvgMs", assetAvgMs < 5.0)
    }
}
