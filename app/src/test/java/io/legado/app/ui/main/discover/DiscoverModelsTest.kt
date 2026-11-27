package io.legado.app.ui.main.discover

import io.legado.app.ui.card.ClassicBook
import io.legado.app.ui.card.HeroKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B6-02 发现页模型映射层单测（纯 JVM，不引 Android 类、不拉起 Compose 运行时）。
 *
 * 覆盖：四类卡片转换成功路径、坏条目过滤与计数、空列表、单条坏数据、部分坏数据、
 * 字段缺失、超长标题、重复条目、null 可选字段、Entry 四字段不预判新列、
 * 同名旧 ClassicBook 不跨包引用。每条断言均带实际谓词，不只断言不抛异常。
 */
class DiscoverModelsTest {

    private val sourceRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    private val modelsSource: String by lazy {
        File(sourceRoot, "io/legado/app/ui/main/discover/DiscoverModels.kt").readText()
    }

    // ─────────────────────────── 四类转换成功路径 ───────────────────────────

    @Test
    fun `battle maps title subtitle description and watchedStage stays zero`() {
        val source = BattleSource(
            title = "  长平之战 ",
            era = " 战国 · 秦赵 ",
            context = " 长平对峙 ",
            phases = listOf("对峙", "出击", "合围", "收官"),
            rounds = 60,
        )
        val card = source.toBattleCard()
        assertEquals("长平之战", card.title)
        assertEquals("战国 · 秦赵", card.subtitle)
        assertEquals("长平对峙", card.description)
        assertEquals(0, card.watchedStage)
        assertEquals(4, card.totalStage)
    }

    @Test
    fun `battle totalStage prefers phases length over rounds`() {
        val source = BattleSource(
            title = "feishui",
            phases = listOf("a", "b"),
            rounds = 20,
        )
        assertEquals(2, source.toBattleCard().totalStage)
    }

    @Test
    fun `battle falls back to rounds when phases empty`() {
        val source = BattleSource(title = "gaixia", phases = emptyList(), rounds = 30)
        assertEquals(30, source.toBattleCard().totalStage)
    }

    @Test
    fun `battle totalStage zero when phases and rounds both empty`() {
        val source = BattleSource(title = "sanjia", phases = emptyList(), rounds = 0)
        assertEquals(0, source.toBattleCard().totalStage)
    }

    @Test
    fun `classic maps title dynasty wordCount with null normalized`() {
        val book = ClassicSource(title = " 史记 ", dynasty = null, wordCount = null)
            .toClassicBook()
        assertEquals("史记", book.title)
        assertEquals("", book.dynasty)
        assertEquals("", book.wordCount)
    }

    @Test
    fun `topic maps title preview and clamps negative count to zero`() {
        val topic = TopicSource(title = " 春秋笔法 ", preview = " 围魏救赵 ", allusionCount = -3)
            .toTopicCard()
        assertEquals("春秋笔法", topic.title)
        assertEquals("围魏救赵", topic.preview)
        assertEquals(0, topic.allusionCount)
    }

    @Test
    fun `hero draft maps kind and five text params`() {
        val hero = HeroSource(
            kind = HeroKind.Marx,
            title = " 马克思 ",
            subtitle = null,
            yearStart = null,
            yearEnd = null,
        ).toHeroDraft()
        assertEquals(HeroKind.Marx, hero.kind)
        assertEquals("马克思", hero.title)
        assertEquals("", hero.subtitle)
        assertEquals("", hero.yearStart)
        assertEquals("", hero.yearEnd)
    }

    // ─────────────────────────── 批量过滤与计数 ───────────────────────────

    @Test
    fun `empty list yields empty outcome with zero dropped`() {
        val outcome = emptyList<BattleSource>().toBattleCards()
        assertTrue(outcome.items.isEmpty())
        assertEquals(0, outcome.dropped)
        assertTrue(outcome.isEmpty)
    }

    @Test
    fun `single blank title entry is dropped and counted`() {
        val outcome = listOf(BattleSource(title = "   ")).toBattleCards()
        assertTrue(outcome.items.isEmpty())
        assertEquals(1, outcome.dropped)
    }

    @Test
    fun `partial bad entries keep good ones and count drops`() {
        val sources = listOf(
            BattleSource(title = "长平之战", phases = listOf("x")),
            BattleSource(title = ""),
            BattleSource(title = "垓下之战", rounds = 30),
        )
        val outcome = sources.toBattleCards()
        assertEquals(2, outcome.items.size)
        assertEquals(1, outcome.dropped)
        assertEquals("长平之战", outcome.items[0].title)
        assertEquals("垓下之战", outcome.items[1].title)
    }

    @Test
    fun `all bad entries yield empty items dropped equal size`() {
        val sources = listOf(
            ClassicSource(title = "  "),
            ClassicSource(title = ""),
            ClassicSource(title = "   "),
        )
        val outcome = sources.toClassicBooks()
        assertTrue(outcome.items.isEmpty())
        assertEquals(3, outcome.dropped)
    }

    // ─────────────────────────── 字段缺失与边界 ───────────────────────────

    @Test
    fun `blank optional fields normalize to empty strings`() {
        val hero = HeroSource(
            kind = HeroKind.Cpc,
            title = "党史",
            subtitle = "   ",
            yearStart = "  ",
            yearEnd = "",
        ).toHeroDraft()
        assertEquals("", hero.subtitle)
        assertEquals("", hero.yearStart)
        assertEquals("", hero.yearEnd)
    }

    @Test
    fun `long title preserved verbatim without truncation`() {
        val longTitle = "长".repeat(200)
        val card = BattleSource(title = longTitle, phases = listOf("x")).toBattleCard()
        assertEquals(200, card.title.length)
        assertEquals(longTitle, card.title)
    }

    @Test
    fun `duplicate entries are both preserved`() {
        val source = ClassicSource(title = "左传", dynasty = "春秋", wordCount = "12万字")
        val outcome = listOf(source, source).toClassicBooks()
        assertEquals(2, outcome.items.size)
        assertEquals("左传", outcome.items[0].title)
        assertEquals("左传", outcome.items[1].title)
    }

    @Test
    fun `null optional fields normalize to empty string`() {
        val topic = TopicSource(title = "史记名场面", preview = null, allusionCount = 28)
            .toTopicCard()
        assertEquals("", topic.preview)
        assertEquals(28, topic.allusionCount)
    }

    // ─────────────────────────── Entry 四字段不预判新列 ───────────────────────────

    @Test
    fun `entry source declares exactly four known fields with no new column`() {
        val block = modelsSource
            .substringAfter("data class NotebookEntrySource(")
            .substringBefore(")", "")
        val fieldNames = block.lines()
            .map { it.trim() }
            .filter { it.startsWith("val ") }
            .map { it.substringAfter("val ").substringBefore(":").trim() }
        assertEquals(listOf("token", "bookName", "chapterTitle", "timestamp"), fieldNames)
        // 只查代码面、不查注释面：本类 KDoc 正文就要写「不预判 mastered / review_count」，
        // 整文件 contains 会把这句话自证成红（2026-11-27 CI 实测就栽在这里）。
        val codeLines = modelsSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/**") }
            .joinToString("\n")
        assertFalse("映射层不得预判 mastered 新列", codeLines.contains("mastered"))
        assertFalse("映射层不得预判 reviewCount 新列", codeLines.contains("reviewCount"))
    }

    @Test
    fun `notebook entry card count maps list size only`() {
        val entries = listOf(
            NotebookEntrySource("之", "史记", "五帝本纪", 1L),
            NotebookEntrySource("乎", "左传", "隐公元年", 2L),
        )
        val card = entries.toNotebookEntryCard()
        assertEquals(2, card.count)
    }

    // ─────────────────────────── 同名旧 ClassicBook 不跨包引用 ───────────────────────────

    @Test
    fun `classicBook resolves to card package and source has no explore import`() {
        assertEquals("io.legado.app.ui.card", ClassicBook::class.java.`package`?.name)
        assertFalse("不得跨包引用旧 explore 同名模型", modelsSource.contains("ui.main.explore"))
        assertTrue("只允许引用卡片族 ClassicBook",
            modelsSource.contains("import io.legado.app.ui.card.ClassicBook"))
    }
}
