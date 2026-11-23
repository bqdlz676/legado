package io.legado.app.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U6 Sheet 内容模型：人物 / 字词两类分区固定顺序与行数上限自检。
 */
class HalfSheetContentTest {

    private fun personSections() = listOf(
        HalfSheetSection(HalfSheetSectionKind.PERSON_ACTION, "本段行为", listOf("郑庄公克段于鄢"), boldFirst = true),
        HalfSheetSection(HalfSheetSectionKind.PERSON_CAST, "同场人物", listOf("祭仲", "公子吕")),
        HalfSheetSection(HalfSheetSectionKind.PERSON_TIMELINE, "时间轴", listOf("初出场", "关键事迹", "末次")),
        HalfSheetSection(HalfSheetSectionKind.PERSON_ARCHIVE, "档案", listOf("本名：姬寤生")),
        HalfSheetSection(HalfSheetSectionKind.PERSON_DOUBLETS, "同名他人", listOf("同名 2 人 ›")),
    )

    private fun wordSections(quotations: Int, words: Int) = listOf(
        HalfSheetSection(HalfSheetSectionKind.WORD_PRONUNCIATION, "音义", listOf("yān · 邑部 · 7 画")),
        HalfSheetSection(HalfSheetSectionKind.WORD_SHUOWEN, "说文", listOf("反切：於乾切")),
        HalfSheetSection(
            HalfSheetSectionKind.WORD_QUOTATIONS,
            "用例",
            List(quotations) { "用例 $it" },
        ),
        HalfSheetSection(
            HalfSheetSectionKind.WORD_WORDS,
            "常见词",
            List(words) { "词 $it" },
        ),
    )

    @Test
    fun `人物 Sheet 固定顺序自检通过`() {
        val content = HalfSheetContent(HalfSheetKind.PERSON, personSections())
        assertTrue(content.validate().toString(), content.validate().isEmpty())
    }

    @Test
    fun `字词 Sheet 三用例十四词上限内通过`() {
        val content = HalfSheetContent(HalfSheetKind.WORD, wordSections(3, 14))
        assertTrue(content.validate().isEmpty())
    }

    @Test
    fun `用例超三条自检报错`() {
        val content = HalfSheetContent(HalfSheetKind.WORD, wordSections(4, 14))
        assertTrue(content.validate().any { it.contains("WORD_QUOTATIONS") })
    }

    @Test
    fun `常见词超十四个自检报错`() {
        val content = HalfSheetContent(HalfSheetKind.WORD, wordSections(3, 15))
        assertTrue(content.validate().any { it.contains("WORD_WORDS") })
    }

    @Test
    fun `分区顺序错乱自检报错`() {
        val reversed = personSections().reversed()
        val content = HalfSheetContent(HalfSheetKind.PERSON, reversed)
        assertTrue(content.validate().any { it.contains("顺序") })
    }

    @Test
    fun `跨形态混入分区自检报错`() {
        val mixed = personSections().toMutableList().also {
            it.add(HalfSheetSection(HalfSheetSectionKind.WORD_SHUOWEN, "说文", listOf("x")))
        }
        val content = HalfSheetContent(HalfSheetKind.PERSON, mixed)
        assertTrue(content.validate().any { it.contains("不属于") })
    }

    @Test
    fun `空内容自检报错走 EmptyState 兜底`() {
        assertTrue(HalfSheetContent.Empty.validate().any { it.contains("sections") })
    }
}
