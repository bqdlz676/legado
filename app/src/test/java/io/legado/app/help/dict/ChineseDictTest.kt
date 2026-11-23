package io.legado.app.help.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 模块 D1 · ChineseDict 单测（对齐规格切片-02 §6 冻结 API）。
 *
 * 覆盖：
 * - getChar：命中条目字段完整、出处非空；未收录返回 null；String 入参重载；
 * - getShuowen：有说文条目返回大徐本释文；无说文 / 未收录返回 null；
 * - getUsage：返回《出处》用例列表；未收录返回空列表（永不 null）；
 * - wordAt：2~5 字长词优先、覆盖点击位置、标点截断、非汉字 / 越界返回 null；
 * - 种子完整性：每条带出处、义项非空、词长合规、用例带书名号。
 *
 * 说明：本测试在宿主 JVM 运行；真机发音、字形渲染等不在本模块范围内。
 */
class ChineseDictTest {

    @Before
    fun setUp() {
        ChineseDict.resetForBench()
    }

    // ---------- getChar ----------

    @Test
    fun `getChar returns full entry for seeded char`() {
        val entry = ChineseDict.getChar('天')
        assertNotNull("种子字「天」应命中", entry)
        requireNotNull(entry)
        assertEquals("天", entry.char)
        assertNotNull("拼音不应缺失", entry.pinyin)
        assertNotNull("部首不应缺失", entry.radical)
        assertNotNull("笔画不应缺失", entry.strokes)
        assertTrue("义项至少一条", entry.senses.isNotEmpty())
        assertTrue("出处标注不得为空", entry.source.isNotBlank())
    }

    @Test
    fun `getChar unknown char returns null`() {
        assertNull("未收录字不应编造释义", ChineseDict.getChar('龘'))
        assertNull("未收录字不应编造释义", ChineseDict.getChar('齉'))
    }

    @Test
    fun `seeded ordinary char chou resolves via char path not missing fallback`() {
        // 切片-02 §5 验收：点「抽」这类普通字，只出字义 + 说文 + 用例，不误弹典故。
        val entry = ChineseDict.getChar('抽')
        assertNotNull("「抽」必须在种子字库内，不得走 Missing 兜底", entry)
        requireNotNull(entry)
        assertEquals("抽", entry.char)
        assertEquals("扌", entry.radical)
        assertEquals(8, entry.strokes!!)
        assertTrue("义项至少一条", entry.senses.isNotEmpty())
        assertTrue("出处标注不得为空", entry.source.isNotBlank())

        val shuowen = ChineseDict.getShuowen('抽')
        assertNotNull("「抽」应有大徐本说文", shuowen)
        assertTrue(shuowen!!.startsWith("引也"))

        val usage = ChineseDict.getUsage('抽')
        assertTrue("「抽」应有公版典籍用例", usage.isNotEmpty())
        assertTrue(
            "用例须出自《孟子·离娄下》",
            usage.first().startsWith("《孟子·离娄下》")
        )

        // 单字位置不产生多字词 / 典故命中。
        assertNull("单字「抽」不应命中多字词", ChineseDict.wordAt("抽", 0))
    }

    @Test
    fun `getChar string overload rejects multi char`() {
        assertNotNull(ChineseDict.getChar("天"))
        assertNull("多字串不作为单字查询", ChineseDict.getChar("天下"))
        assertNull("空串返回 null", ChineseDict.getChar(""))
    }

    @Test
    fun `every seeded entry carries source and non empty senses`() {
        val counts = ChineseDict.counts()
        assertTrue("种子字头应达到百条级，实际 ${counts.charCount}", counts.charCount >= 100)
        // 逐条遍历种子：通过公开 API 无法穷举，这里抽查一批高频字。
        val sampled = listOf(
            "人", "山", "水", "火", "木", "金", "土", "田", "年", "春",
            "秋", "道", "德", "仁", "义", "礼", "信", "忠", "孝", "学",
            "诗", "书", "文", "武", "国", "家", "邦", "都", "城", "宫",
        )
        for (ch in sampled) {
            val entry = ChineseDict.getChar(ch)
            assertNotNull("抽查字 $ch 应在种子内", entry)
            requireNotNull(entry)
            assertTrue("$ch 出处不得为空", entry.source.isNotBlank())
            assertTrue("$ch 义项不得为空", entry.senses.isNotEmpty())
            entry.strokes?.let { assertTrue("$ch 笔画须为非负整数", it >= 0) }
        }
    }

    // ---------- getShuowen ----------

    @Test
    fun `getShuowen returns daxu text for seeded char`() {
        val sw = ChineseDict.getShuowen('天')
        assertNotNull("「天」有大徐本说文", sw)
        assertTrue(sw!!.isNotBlank())
        val entry = ChineseDict.getChar('天')
        assertTrue(
            "带说文条目的出处应标明大徐本公版",
            entry!!.source.contains("说文解字")
        )
    }

    @Test
    fun `getShuowen null when absent or unknown`() {
        // 「小」种子未录说文，不得返回编造文本。
        assertNull(ChineseDict.getShuowen('小'))
        assertNull(ChineseDict.getShuowen('龘'))
        assertNull(ChineseDict.getShuowen("天下"))
    }

    // ---------- getUsage ----------

    @Test
    fun `getUsage returns sourced classic examples`() {
        val usage = ChineseDict.getUsage('小')
        assertTrue("「小」应有公版典籍用例", usage.isNotEmpty())
        for (example in usage) {
            assertTrue("用例须带《出处》：$example", example.startsWith("《"))
            assertTrue("用例出处与例句不得为空", example.length > 2)
        }
    }

    @Test
    fun `getUsage unknown returns empty list not null`() {
        val usage = ChineseDict.getUsage('龘')
        assertNotNull("用例列表永不返回 null", usage)
        assertTrue(usage.isEmpty())
        assertTrue(ChineseDict.getUsage("天下").isEmpty())
    }

    // ---------- wordAt ----------

    @Test
    fun `wordAt hits two char word at click position`() {
        val text = "君子之学，不可已也。"
        val hit = ChineseDict.wordAt(text, 0)
        assertNotNull("开头「君子」应命中", hit)
        requireNotNull(hit)
        assertEquals("君子", hit.word)
        assertEquals(0, hit.start)
        assertEquals(2, hit.end)
        assertTrue(hit.start <= 0 && 0 < hit.end)
        assertTrue(hit.meaning.isNotBlank())
        assertTrue(hit.source.isNotBlank())
    }

    @Test
    fun `wordAt prefers longest word`() {
        val text = "学而时习之"
        val hit = ChineseDict.wordAt(text, 0)
        assertNotNull("五字成语应长词优先命中", hit)
        requireNotNull(hit)
        assertEquals("学而时习之", hit.word)
        assertEquals(5, hit.end - hit.start)
    }

    @Test
    fun `wordAt matches word covering clicked middle position`() {
        // 「诸侯」在位置 3~4，点击位置 3 仍应回退命中该词。
        val text = "天子与诸侯会于盟津"
        val hit = ChineseDict.wordAt(text, 3)
        assertNotNull("点击词中位置应命中", hit)
        requireNotNull(hit)
        assertEquals("诸侯", hit.word)
        assertTrue(hit.start <= 3 && 3 < hit.end)
    }

    @Test
    fun `wordAt splits at punctuation`() {
        val text = "君子，小人道长"
        // 位置 3 是「小」，标点隔开后不得回串到「君子」。
        val hit = ChineseDict.wordAt(text, 3)
        assertNotNull("标点后应命中「小人」", hit)
        requireNotNull(hit)
        assertEquals("小人", hit.word)
        assertEquals(3, hit.start)
    }

    @Test
    fun `wordAt returns null for non han or out of range`() {
        assertNull(ChineseDict.wordAt("abc", 0))
        assertNull(ChineseDict.wordAt("君子", -1))
        assertNull(ChineseDict.wordAt("君子", 9))
        assertNull(ChineseDict.wordAt("", 0))
        val punct = ChineseDict.wordAt("，。", 0)
        assertNull("纯标点不命中词", punct)
    }

    @Test
    fun `wordAt seeds are all two to five han chars`() {
        val counts = ChineseDict.counts()
        assertTrue("种子词目应达到百条级，实际 ${counts.wordCount}", counts.wordCount >= 100)
        // 抽查若干词目在任意包含位置均可命中。
        val cases = listOf(
            "社稷" to 0, "宗庙" to 1, "干戈" to 0, "琴瑟" to 2,
            "礼乐" to 0, "六艺" to 1, "中原" to 0, "富贵" to 2,
        )
        for ((word, pad) in cases) {
            val text = "X".repeat(pad) + word + "Y"
            val hit = ChineseDict.wordAt(text, pad)
            assertNotNull("词目 $word 应命中", hit)
            requireNotNull(hit)
            assertEquals(word, hit!!.word)
            assertTrue("词长须在 2~5 之间", hit.end - hit.start in 2..5)
            assertTrue(hit.meaning.isNotBlank())
            assertTrue(hit.source.isNotBlank())
        }
    }

    @Test
    fun `wordAt does not split seeded five char idiom into shorter words`() {
        val text = "温故而知新可以为师矣"
        val hit = ChineseDict.wordAt(text, 1)
        assertNotNull("五字成语应整体命中", hit)
        requireNotNull(hit)
        assertEquals("温故而知新", hit.word)
    }

    // ---------- 出处口径 ----------

    @Test
    fun `source labels avoid modern punctuated editions`() {
        val entry = ChineseDict.getChar('玉') ?: return
        assertFalse(
            "出处不得引用中华书局点校版",
            entry.source.contains("中华书局")
        )
        assertTrue("说文出处须标明公版版本", entry.source.contains("公版"))
    }

    // ---------- 灵魂闭环链路用字 ----------

    @Test
    fun `soul loop anchor chars are all seeded with full fields`() {
        // 切片-02 §5 / 总纲灵魂闭环：点「晋」命中春秋晋国；「鲁隐公元年」出纪年。
        val loop = listOf("晋", "鲁", "隐", "公", "元", "年", "己", "未", "春", "秋")
        for (ch in loop) {
            val entry = ChineseDict.getChar(ch)
            assertNotNull("灵魂闭环用字 $ch 必须在种子内", entry)
            requireNotNull(entry)
            assertTrue("$ch 拼音不得为空", !entry.pinyin.isNullOrBlank())
            assertTrue("$ch 义项不得为空", entry.senses.isNotEmpty())
            assertTrue("$ch 出处不得为空", entry.source.isNotBlank())
        }
    }

    // ---------- 《左传·隐公元年》全文用字覆盖 ----------

    @Test
    fun `yin gong yuan nian passage characters all seeded`() {
        // 公版经传原文（assets/books/春秋左传.txt 隐公元年经传），
        // 逐字校验字头齐备；未收字不得走 Missing 兜底。
        val passage = """
            惠公元妃孟子。孟子卒，继室以声子，生隐公。宋武公生仲子。仲子生而有文在其手，曰为鲁夫人，故仲子归于我。生桓公而惠公薨，是以隐公立而奉之。
            元年春王正月。三月，公及邾仪父盟于蔑。夏五月，郑伯克段于鄢。
            秋七月，天王使宰咺来归惠公仲子之赗。九月，及宋人盟于宿。冬十有二月，祭伯来。公子益师卒。
            元年春，王周正月。不书即位，摄也。
            三月，公及邾仪父盟于蔑，邾子克也。未王命，故不书爵。曰仪父，贵之也。公摄位而欲求好于邾，故为蔑之盟。
            夏四月，费伯帅师城郎。不书，非公命也。
            初，郑武公娶于申，曰武姜，生庄公及共叔段。庄公寤生，惊姜氏，故名曰寤生，遂恶之。爱共叔段，欲立之。亟请于武公，公弗许。及庄公即位，为之请制。公曰，制，岩邑也，虢叔死焉。佗邑唯命。请京，使居之，谓之京城大叔。祭仲曰，都，城过百雉，国之害也。先王之制，大都，不过参国之一，中，五之一，小，九之一。今京不度，非制也，君将不堪。公曰，姜氏欲之，焉辟害。对曰，姜氏何厌之有。不如早为之所，无使滋蔓。蔓，难图也。蔓草犹不可除，况君之宠弟乎。公曰，多行不义，必自毙，子姑待之。
            既而大叔命西鄙北鄙贰于己。公子吕曰，国不堪贰，君将若之何。欲与大叔，臣请事之。若弗与，则请除之，无生民心。公曰，无庸，将自及。
            大叔又收贰以为己邑，至于廪延。子封曰，可矣，厚将得众。公曰，不义不暱，厚将崩。
            大叔完聚，缮甲兵，具卒乘，将袭郑，夫人将启之。公闻其期，曰，可矣。命子封帅车二百乘以伐京。京叛大叔段，段入于鄢，公伐诸鄢。五月辛丑，大叔出奔共。
            书曰，郑伯克段于鄢。段不弟，故不言弟。如二君，故曰克。称郑伯，讥失教也，谓之郑志。不言出奔，难之也。
            遂寘姜氏于城颍，而誓之曰，不及黄泉，无相见也。既而悔之。
            颍考叔为颍谷封人，闻之，有献于公，公赐之食，食舍肉。公问之，对曰，小人有母，皆尝小人之食矣，未尝君之羹，请以遗之。公曰，尔有母遗，繄我独无。颍考叔曰，敢问何谓也。公语之故，且告之悔。对曰，君何患焉。若阙地及泉，隧而相见，其谁曰不然。公从之。公入而赋，大隧之中，其乐也融融。姜出而赋，大隧之外，其乐也泄泄。遂为母子如初。
            君子曰，颍考叔，纯孝也，爱其母，施及庄公。诗曰，孝子不匮，永锡尔类。其是之谓乎。秋七月，天王使宰咺来归惠公仲子之赗。缓，且子氏未薨，故名。天子七月而葬，同轨毕至。诸侯五月，同盟至。大夫三月，同位至。士逾月，外姻至。
            赠死不及尸，吊生不及哀，豫凶事，非礼也。
            八月，纪人伐夷。夷不告，故不书。
            有蜚。不为灾，亦不书。
            惠公之季年，败宋师于黄。公立而求成焉。九月，及宋人盟于宿，始通也。
            冬十月庚申，改葬惠公。公弗临，故不书。惠公之薨也，有宋师，太子少，葬故有阙，是以改葬。卫侯来会葬，不见公，亦不书。郑共叔之乱，公孙滑出奔卫。卫人为之伐郑，取廪延。郑人以王师虢师伐卫南鄙。请师于邾。邾子使私于公子豫，豫请往，公弗许，遂行。及邾人郑人盟于翼。不书，非公命也。
            新作南门。不书，亦非公命也。
            十二月，祭伯来，非王命也。
            众父卒。公不与小敛，故不书日。
        """.trimIndent()

        val missing = passage.asSequence()
            .filter { it.isHan() }
            .distinct()
            .filter { ChineseDict.getChar(it) == null }
            .map { it.toString() }
            .toList()
        assertTrue("《隐公元年》未收字头：$missing", missing.isEmpty())
    }

    // ---------- 种子字段齐备性全量扫描 ----------

    @Test
    fun `every seeded entry carries pinyin senses source and valid strokes`() {
        val entries = ChineseDict.seededEntries()
        assertTrue("字头种子应有数百条，实际 ${entries.size}", entries.size >= 300)
        for (entry in entries) {
            assertTrue("${entry.char} 拼音不得为空", !entry.pinyin.isNullOrBlank())
            assertTrue("${entry.char} 义项不得为空", entry.senses.isNotEmpty())
            assertTrue("${entry.char} 出处不得为空", entry.source.isNotBlank())
            entry.strokes?.let { assertTrue("${entry.char} 笔画须为非负整数", it >= 0) }

            val shuowen = ChineseDict.getShuowen(entry.char)
            if (shuowen != null) {
                assertTrue(
                    "${entry.char} 带说文则出处须标明大徐本公版",
                    entry.source.contains("说文解字")
                )
            }
            for (example in ChineseDict.getUsage(entry.char)) {
                assertTrue("${entry.char} 用例须带《出处》：$example", example.startsWith("《"))
            }
        }
    }

    private fun Char.isHan(): Boolean =
        code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF
}
