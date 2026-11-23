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
}
