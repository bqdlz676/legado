package io.legado.app.ui.components

import io.legado.app.help.bubble.BubbleCandidate
import io.legado.app.help.bubble.BubbleContent
import io.legado.app.help.bubble.BubbleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U6 半屏 Sheet 映射器验收（切片-11 §5.2 人物 / §5.3 字词 / §5.4 状态不变）。
 *
 * 本类为普通 JVM 单测：只消费 [BubbleContent] 冻结协议，断言两套固定分区顺序、
 * 行数上限、兜底文案、同名节显隐，以及开合前后宿主状态快照全等。
 */
class HalfSheetMapperTest {

    // ---------- 桩件 ----------

    private fun personContent(candidates: List<BubbleCandidate> = emptyList()) = BubbleContent(
        kind = BubbleKind.PERSON,
        token = "寤生",
        phonetic = "jī wù shēng",
        headline = "郑庄公 · 春秋初霸",
        body = "克段于鄢\n郑国君主",
        detail = """
            本名　姬寤生
            字 / 号 / 别称　号 庄公
            生卒　前757—前701
            身份　郑国·君主
            小传　庄公寤生，惊姜氏
            事迹　克段于鄢
            谱系　姬姓郑氏
        """.trimIndent(),
        actions = emptyList(),
        candidates = candidates,
        source = "Harvard CBDB #12345",
    )

    private fun wordContent(detail: String, body: String = "部首 宀　7画　安定") = BubbleContent(
        kind = BubbleKind.CHAR,
        token = "安",
        phonetic = "ān",
        headline = "安",
        body = body,
        detail = detail,
        actions = emptyList(),
        candidates = emptyList(),
        source = "《说文解字》大徐本",
    )

    private fun section(model: HalfSheetContent, kind: HalfSheetSectionKind) =
        model.sections.first { it.kind == kind }

    // ---------- §5.2 人物 Sheet ----------

    @Test
    fun `人物 Sheet 五节固定顺序且自检通过`() {
        val model = HalfSheetMapper.build(personContent())
        requireNotNull(model)
        assertEquals(HalfSheetKind.PERSON, model.kind)
        assertEquals(
            listOf(
                HalfSheetSectionKind.PERSON_ACTION,
                HalfSheetSectionKind.PERSON_CAST,
                HalfSheetSectionKind.PERSON_TIMELINE,
                HalfSheetSectionKind.PERSON_ARCHIVE,
            ),
            model.sections.map { it.kind },
        )
        assertTrue(model.validate().toString(), model.validate().isEmpty())
    }

    @Test
    fun `本段行为首行加粗取 body 散行`() {
        val model = requireNotNull(HalfSheetMapper.build(personContent()))
        val action = section(model, HalfSheetSectionKind.PERSON_ACTION)
        assertTrue(action.boldFirst)
        assertEquals(listOf("克段于鄢", "郑国君主"), action.lines)
    }

    @Test
    fun `时间轴取生卒与事迹 档案剔除小传事迹`() {
        val model = requireNotNull(HalfSheetMapper.build(personContent()))
        val timeline = section(model, HalfSheetSectionKind.PERSON_TIMELINE)
        assertTrue(timeline.lines.any { it.startsWith("生卒") })
        assertTrue(timeline.lines.any { it.startsWith("事迹") })

        val archive = section(model, HalfSheetSectionKind.PERSON_ARCHIVE)
        assertTrue(archive.lines.any { it.startsWith("本名") })
        assertTrue(archive.lines.any { it.startsWith("谱系") })
        assertFalse(archive.lines.any { it.startsWith("事迹") })
        assertFalse(archive.lines.any { it.startsWith("小传") })
    }

    @Test
    fun `同名 0 人隐藏第五节 有名则标题计数且链接对齐 query`() {
        val none = requireNotNull(HalfSheetMapper.build(personContent()))
        assertFalse(none.sections.any { it.kind == HalfSheetSectionKind.PERSON_DOUBLETS })

        val candidates = listOf(
            BubbleCandidate("祭仲", "祭仲（周）"),
            BubbleCandidate("祭足", "祭足（郑）"),
        )
        val with = requireNotNull(HalfSheetMapper.build(personContent(candidates)))
        val doublets = section(with, HalfSheetSectionKind.PERSON_DOUBLETS)
        assertEquals("同名的其他 2 人 ›", doublets.title)
        assertEquals(listOf("祭仲（周）", "祭足（郑）"), doublets.lines)
        assertEquals(listOf("祭仲", "祭足"), doublets.links)
        assertTrue(with.validate().isEmpty())
    }

    @Test
    fun `人物协议缺字段分区显示无载不留空白态`() {
        val bare = personContent().copy(body = "", detail = "")
        val model = requireNotNull(HalfSheetMapper.build(bare))
        model.sections.forEach { section ->
            assertTrue(section.lines.isNotEmpty())
            assertTrue(section.lines.all { it.isNotBlank() })
        }
        assertTrue(section(model, HalfSheetSectionKind.PERSON_CAST).lines.first() == HalfSheetFallback.MISSING_TEXT)
    }

    // ---------- §5.3 字词 Sheet ----------

    @Test
    fun `字词 Sheet 四节固定顺序 音义汇总拼音部首笔画义项`() {
        val detail = """
            【义项】
            安定
            平安

            【说文】
            塞也。从宀九声。反切：居九切

            【用例】
            《诗经·小雅》例一
            《左传》例二
        """.trimIndent()
        val model = requireNotNull(HalfSheetMapper.build(wordContent(detail)))
        assertEquals(HalfSheetKind.WORD, model.kind)
        assertEquals(
            listOf(
                HalfSheetSectionKind.WORD_PRONUNCIATION,
                HalfSheetSectionKind.WORD_SHUOWEN,
                HalfSheetSectionKind.WORD_QUOTATIONS,
                HalfSheetSectionKind.WORD_WORDS,
            ),
            model.sections.map { it.kind },
        )
        val pron = section(model, HalfSheetSectionKind.WORD_PRONUNCIATION)
        assertTrue(pron.lines.contains("ān"))
        assertTrue(pron.lines.any { it.startsWith("部首") })
        assertTrue(pron.lines.contains("安定"))
        assertTrue(pron.lines.contains("平安"))

        val shuowen = section(model, HalfSheetSectionKind.WORD_SHUOWEN)
        assertTrue(shuowen.lines.first().contains("反切：居九切"))
        assertTrue(model.validate().isEmpty())
    }

    @Test
    fun `普通多字词无标签释义归入音义节不丢行`() {
        val word = BubbleContent(
            kind = BubbleKind.WORD,
            token = "社稷",
            phonetic = "shè jì",
            headline = "社稷",
            body = "土谷之神，代指国家",
            detail = "土谷之神，代指国家\n历代帝王祭祀社稷",
            actions = emptyList(),
            candidates = emptyList(),
            source = "zh_ci 词表种子",
        )
        val model = requireNotNull(HalfSheetMapper.build(word))
        val pron = section(model, HalfSheetSectionKind.WORD_PRONUNCIATION)
        assertTrue(pron.lines.any { it.contains("历代帝王祭祀社稷") })
        assertTrue(model.validate().isEmpty())
    }

    @Test
    fun `古籍用例上限三条 超出截断`() {
        val detail = """
            【用例】
            例一
            例二
            例三
            例四
            例五
        """.trimIndent()
        val model = requireNotNull(HalfSheetMapper.build(wordContent(detail)))
        val quotes = section(model, HalfSheetSectionKind.WORD_QUOTATIONS)
        assertEquals(3, quotes.lines.size)
        assertTrue(model.validate().isEmpty())
    }

    @Test
    fun `用例为空出引导 说文无载出占位 常见词出引导`() {
        val model = requireNotNull(HalfSheetMapper.build(wordContent("【义项】\n安也")))
        assertEquals(
            HalfSheetFallback.QUOTATION_EMPTY_GUIDE,
            section(model, HalfSheetSectionKind.WORD_QUOTATIONS).lines.first(),
        )
        assertEquals(
            HalfSheetFallback.MISSING_TEXT,
            section(model, HalfSheetSectionKind.WORD_SHUOWEN).lines.first(),
        )
        assertEquals(
            HalfSheetFallback.WORDS_EMPTY_GUIDE,
            section(model, HalfSheetSectionKind.WORD_WORDS).lines.first(),
        )
    }

    @Test
    fun `地名纪年等非两套形态返回 null 走自定义槽`() {
        val place = BubbleContent(
            kind = BubbleKind.PLACE,
            token = "鄢",
            phonetic = null,
            headline = "鄢",
            body = "今河南鄢陵",
            detail = "沿革　郑武公克鄢",
            actions = emptyList(),
            candidates = emptyList(),
            source = "本地方志",
        )
        assertNull(HalfSheetMapper.build(place))

        val missing = BubbleContent(
            kind = BubbleKind.MISSING,
            token = "龘",
            phonetic = null,
            headline = "龘",
            body = "未收录",
            detail = "未命中字典",
            actions = emptyList(),
            candidates = emptyList(),
            source = "本地词库未命中",
        )
        assertNull(HalfSheetMapper.build(missing))
    }

    // ---------- §5.4 关闭后状态一字不差 ----------

    @Test
    fun `开合前后宿主快照全等 页码偏移下划线逐项不变`() {
        val before = HalfSheetHostSnapshot(pageIndex = 2, scrollOffset = 120, highlightKeys = setOf("a", "b"))
        val after = before.copy()
        assertTrue("开合前后快照必须一字不差", before.matches(after))
    }

    @Test
    fun `页码偏移下划线任一变化快照即不等`() {
        val base = HalfSheetHostSnapshot(pageIndex = 2, scrollOffset = 120, highlightKeys = setOf("a", "b"))
        assertFalse(base.matches(base.copy(pageIndex = 3)))
        assertFalse(base.matches(base.copy(scrollOffset = 121)))
        assertFalse(base.matches(base.copy(highlightKeys = setOf("a"))))
        assertFalse(base.matches(base.copy(highlightKeys = setOf("a", "b", "c"))))
    }
}
