package io.legado.app.help.bubble

import io.legado.app.help.chrono.ChronoPlaces
import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 气泡数据层 · 解析入口验收（规格切片-02 §2 识别优先级、§5 验收用例）。
 *
 * 本类在宿主 JVM 上跑纯逻辑：Provider 用桩件注入，验证编排顺序、兜底与缓存；
 * 各字典真实命中留给数据表接入后的集成测试。无 Context 环境下真实 Provider 一律
 * 降级返回 null，由 Missing 兜底，不抛异常、不留空白态。
 */
class BubbleResolverTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
    }

    // ---------- 桩件 ----------

    private class Stub(
        private val tag: String,
        private val result: BubbleContent? = null,
        private val record: MutableList<String>? = null,
        private val throwOnCall: Boolean = false,
    ) : BubbleProvider {
        override fun query(token: String, anchor: ContextAnchor) = strike()
        override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor) = strike()
        override fun matchAllusion(paragraph: String, pos: Int, anchor: ContextAnchor) = strike()
        override fun matchWord(paragraph: String, pos: Int, anchor: ContextAnchor) = strike()

        private fun strike(): BubbleContent? {
            record?.add(tag)
            if (throwOnCall) throw IllegalStateException("boom")
            return result
        }
    }

    private fun content(kind: String, token: String, candidates: List<String> = emptyList()) =
        BubbleContent(
            kind = kind,
            token = token,
            phonetic = null,
            headline = token,
            body = "body",
            detail = "detail",
            actions = emptyList(),
            candidates = candidates,
            source = "test",
        )

    private fun resolver(
        date: BubbleProvider,
        allusion: BubbleProvider,
        person: BubbleProvider,
        place: BubbleProvider,
        word: BubbleProvider,
        char: BubbleProvider,
        missing: BubbleProvider = MissingProvider,
    ) = BubbleResolverImpl(date, allusion, person, place, word, char, missing)

    private val nullStub = Stub("null")

    // ---------- 兜底 ----------

    @Test
    fun `resolve empty paragraph returns missing bubble, never crash`() {
        val r = resolver(nullStub, nullStub, nullStub, nullStub, nullStub, nullStub)
        val bubble = r.resolve("", 0)
        assertNotNull(bubble)
        assertEquals(BubbleKind.MISSING, bubble!!.kind)
        assertTrue(bubble.allowWebSearch)
        assertTrue(bubble.allowNotebook)
    }

    @Test
    fun `resolve pos out of range falls back to last char missing bubble`() {
        val r = resolver(nullStub, nullStub, nullStub, nullStub, nullStub, nullStub)
        val bubble = r.resolve("春秋", 99)
        assertNotNull(bubble)
        assertEquals(BubbleKind.MISSING, bubble!!.kind)
        assertEquals("秋", bubble.token)
    }

    @Test
    fun `skeleton providers all null fall back to missing with web and notebook actions`() {
        val bubble = BubbleResolver.resolve("抽", 0)
        assertNotNull(bubble)
        assertEquals(BubbleKind.MISSING, bubble!!.kind)
        assertEquals("抽", bubble.token)
        assertTrue(bubble.allowWebSearch)
        assertTrue(bubble.allowNotebook)
        assertTrue(bubble.actions.isEmpty())
        assertTrue(bubble.candidates.isEmpty())
        assertNotNull(bubble.source)
    }

    @Test
    fun `provider exception is caught and falls back to missing`() {
        val throwing = Stub("boom", throwOnCall = true)
        val r = resolver(throwing, throwing, throwing, throwing, throwing, throwing)
        val bubble = r.resolve("晋", 0)
        assertNotNull(bubble)
        assertEquals(BubbleKind.MISSING, bubble!!.kind)
    }

    // ---------- 优先级链 ----------

    @Test
    fun `chain invokes providers in spec priority order`() {
        val record = mutableListOf<String>()
        val r = resolver(
            date = Stub("date", record = record),
            allusion = Stub("allusion", record = record),
            person = Stub("person", record = record),
            place = Stub("place", record = record),
            word = Stub("word", record = record),
            char = Stub("char", record = record),
        )
        r.resolve("鲁隐公元年，晋公子重耳出奔。", 1)
        assertEquals(listOf("date", "allusion", "person", "place", "word", "char"), record)
    }

    @Test
    fun `date beats allusion person place word char`() {
        val hit = content(BubbleKind.ERA, "鲁隐公元年")
        val r = resolver(
            date = Stub("date", hit),
            allusion = Stub("allusion"),
            person = Stub("person"),
            place = Stub("place"),
            word = Stub("word"),
            char = Stub("char"),
        )
        val bubble = r.resolve("鲁隐公元年", 3)
        assertSame(hit, bubble)
        assertEquals(BubbleKind.ERA, bubble!!.kind)
    }

    @Test
    fun `allusion beats person place word char`() {
        val hit = content(BubbleKind.ALLUSION, "退避三舍")
        val r = resolver(
            date = nullStub,
            allusion = Stub("allusion", hit),
            person = Stub("person"),
            place = Stub("place"),
            word = Stub("word"),
            char = Stub("char"),
        )
        val bubble = r.resolve("晋楚治兵，遇于中原，其辟君三舍。", 12)
        assertSame(hit, bubble)
        assertEquals(BubbleKind.ALLUSION, bubble!!.kind)
    }

    @Test
    fun `person beats place word char`() {
        val hit = content(BubbleKind.PERSON, "重耳")
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = Stub("person", hit),
            place = Stub("place"),
            word = Stub("word"),
            char = Stub("char"),
        )
        val bubble = r.resolve("晋公子重耳之亡", 4)
        assertSame(hit, bubble)
    }

    @Test
    fun `place beats word char`() {
        val hit = content(BubbleKind.PLACE, "晋")
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = nullStub,
            place = Stub("place", hit),
            word = Stub("word"),
            char = Stub("char"),
        )
        val bubble = r.resolve("晋侯、秦伯围郑", 0)
        assertSame(hit, bubble)
    }

    @Test
    fun `word beats char`() {
        val hit = content(BubbleKind.WORD, "重耳")
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = nullStub,
            place = nullStub,
            word = Stub("word", hit),
            char = Stub("char"),
        )
        val bubble = r.resolve("重耳", 1)
        assertSame(hit, bubble)
    }

    @Test
    fun `candidates carried through to bubble`() {
        val hit = content(BubbleKind.PLACE, "晋", candidates = listOf("晋（诸侯国）", "晋（今山西晋城）"))
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = nullStub,
            place = Stub("place", hit),
            word = nullStub,
            char = nullStub,
        )
        val bubble = r.resolve("晋", 0)
        assertEquals(listOf("晋（诸侯国）", "晋（今山西晋城）"), bubble!!.candidates)
    }

    // ---------- 缓存 ----------

    @Test
    fun `second resolve same position returns cached instance`() {
        val record = mutableListOf<String>()
        val r = resolver(
            date = Stub("date", record = record),
            allusion = nullStub,
            person = nullStub,
            place = nullStub,
            word = nullStub,
            char = nullStub,
        )
        val first = r.resolve("晋侯围郑", 0)
        val second = r.resolve("晋侯围郑", 0)
        assertSame(first, second)
        assertEquals(1, record.size)
    }

    @Test
    fun `clearCache forces re-dispatch`() {
        val record = mutableListOf<String>()
        val r = resolver(
            date = Stub("date", record = record),
            allusion = nullStub,
            person = nullStub,
            place = nullStub,
            word = nullStub,
            char = nullStub,
        )
        r.resolve("晋侯围郑", 0)
        r.clearCache()
        r.resolve("晋侯围郑", 0)
        assertEquals(2, record.size)
    }

    // ---------- 词窗 / 切词 ----------

    @Test
    fun `windows are emitted long word first`() {
        val spans = BubbleSupport.windows("晋公子重耳", 3, 5)
        assertTrue(spans.isNotEmpty())
        val lengths = spans.map { it.length }
        assertEquals(lengths.sortedDescending(), lengths)
        assertTrue(spans.first().length >= spans.last().length)
    }

    @Test
    fun `latin word extracted as one token`() {
        val text = "read hello world"
        val span = BubbleSupport.extractToken(text, 7)
        assertEquals("hello", span.token)
        assertEquals(text.indexOf("hello"), span.start)
        assertEquals(text.indexOf("hello") + "hello".length - 1, span.end)
    }

    @Test
    fun `han run extracted around click`() {
        val span = BubbleSupport.extractToken("晋公子重耳之亡", 3)
        assertEquals("晋公子重耳之亡", span.token)
    }

    @Test
    fun `era mention shape recognized`() {
        assertEquals("鲁隐公元年", BubbleSupport.eraMentionAt("鲁隐公元年春，公及邾仪父盟于蔑", 3))
        assertEquals("贞观三年", BubbleSupport.eraMentionAt("贞观三年春", 2))
        assertNull(BubbleSupport.eraMentionAt("春天三月", 1))
    }

    @Test
    fun `ganzhi and phase shape recognized`() {
        assertEquals("辛酉", BubbleSupport.ganzhiAt("岁在辛酉", 2))
        assertEquals("朔", BubbleSupport.phaseAt("朔日", 0))
        assertEquals("望", BubbleSupport.phaseAt("既望", 1))
    }

    @Test
    fun `queryToken walks provider chain`() {
        val hit = content(BubbleKind.PERSON, "重耳")
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = Stub("person", hit),
            place = nullStub,
            word = nullStub,
            char = nullStub,
        )
        assertSame(hit, r.queryToken("重耳"))
        assertNull(r.queryToken(""))
    }

    // ---------- Provider 纯逻辑（无 Context 降级路径） ----------

    @Test
    fun `missing provider always returns a non-null bubble`() {
        val bubble = MissingProvider.query("", ContextAnchor)
        assertEquals(BubbleKind.MISSING, bubble.kind)
        assertTrue(bubble.allowWebSearch)
        assertTrue(bubble.allowNotebook)
        assertTrue(bubble.actions.isEmpty())
        assertTrue(bubble.candidates.isEmpty())
    }

    @Test
    fun `phase hit builds shuo wang hui bubble without context`() {
        val bubble = DateProvider.hitAt("朔日，公及齐侯盟", 0, ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.PHASE, bubble!!.kind)
        assertEquals("朔", bubble.token)
        assertTrue(bubble.body.contains("初一"))
    }

    @Test
    fun `ganzhi hit builds cycle bubble without context`() {
        val bubble = DateProvider.hitAt("岁在辛酉，晋师灭赤狄", 2, ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.GANZHI, bubble!!.kind)
        assertEquals("辛酉", bubble.token)
        assertTrue(bubble.detail.contains("六十"))
    }

    @Test
    fun `prince year mention resolves from built-in seed timeline`() {
        val bubble = DateProvider.hitAt("鲁隐公元年春，公及邾仪父盟于蔑", 3, ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.ERA, bubble!!.kind)
        assertEquals("鲁隐公元年", bubble.token)
        val timeline = bubble.actions.filterIsInstance<Action.OpenTimeline>().singleOrNull()
        assertNotNull(timeline)
        assertEquals(-722, timeline!!.year)
        assertTrue(bubble.detail.contains("对照") || bubble.body.contains("对照"))
        // 干支口径定案：前 722 = 己未（辛酉为前 720，不允许回退错值）。
        assertTrue(bubble.headline.contains("己未") || bubble.detail.contains("己未"))
        assertFalse(bubble.headline.contains("辛酉") || bubble.detail.contains("辛酉"))
    }

    @Test
    fun `action sealed keeps exactly four frozen subclasses`() {
        val names = Action::class.sealedSubclasses.map { it.simpleName }.toSet()
        assertEquals(
            setOf("OpenMap", "OpenPerson", "OpenTimeline", "OpenRelation"),
            names,
        )
    }

    @Test
    fun `place default hit excludes low confidence and reference layer entities`() {
        ChronoPlaces.resetForTest()
        // 锚到《左传》春秋（前 722）：默认命中春秋晋都，今址参考层与 low 泛称只进候选。
        val resolved = ChronoPlaces.resolve("晋", -722)
        assertNotNull(resolved.hit)
        val hit = resolved.hit!!
        assertFalse(hit.refLayer)
        assertTrue(hit.confidenceTag.canDefault)
        assertEquals("P003", hit.id)
        val others = resolved.others.map { it.id }.toSet()
        assertTrue(others.contains("P057")) // 今址参考层（晋城）不进默认
        assertTrue(others.contains("A012")) // low 泛称不进默认

        // 无锚点年：不猜，hit = null，全部候选照收。
        val unanchored = ChronoPlaces.resolve("晋", null)
        assertNull(unanchored.hit)
        assertTrue(unanchored.candidates.isNotEmpty())
    }

    @Test
    fun `seed person resolves from in-memory profile without context`() {
        val bubble = PersonProvider.query("孔子", ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.PERSON, bubble!!.kind)
        assertEquals("孔子", bubble.token)
        assertTrue(bubble.actions.any { it is Action.OpenPerson })
        assertTrue(bubble.actions.any { it is Action.OpenRelation })
        assertTrue(bubble.source.isNotBlank())
    }

    @Test
    fun `char and word providers degrade to null without context`() {
        // 无 assets Context：字 / 词 Provider 不硬造释义，返回 null 交兜底。
        assertNull(CharProvider.query("抽", ContextAnchor))
        assertNull(WordProvider.query("退避三舍", ContextAnchor))
        assertNull(PlaceProvider.query("晋", ContextAnchor))
    }

    @Test
    fun `english token cleaned keeps apostrophe and hyphen`() {
        assertEquals("don't", BubbleSupport.cleanToken("don't！"))
        assertEquals("half-life", BubbleSupport.cleanToken("（half-life）"))
    }
}
