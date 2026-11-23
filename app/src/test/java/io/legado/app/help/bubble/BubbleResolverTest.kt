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
 * 本类在宿主 JVM 上跑纯逻辑：编排顺序 / 兜底 / 缓存放行桩件注入；
 * 各字典真实命中走 D1~D4 / C1 纯内存种子。无 Context 环境下兜底入口
 * （联网 / 生字本）返回 null / false，不抛异常、不留空白态。
 */
class BubbleResolverTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
        ChronoPlaces.resetForTest()
    }

    // ---------- 桩件 ----------

    private class Stub(
        private val tag: String,
        private val result: BubbleContent? = null,
        private val record: MutableList<String>? = null,
        private val throwOnCall: Boolean = false,
        private val byToken: Map<String, BubbleContent?> = emptyMap(),
    ) : BubbleProvider {
        override fun query(token: String, anchor: ContextAnchor) = strike(token)
        override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor) = strike()
        override fun matchAllusion(paragraph: String, pos: Int, anchor: ContextAnchor) = strike()
        override fun matchWord(paragraph: String, pos: Int, anchor: ContextAnchor) = strike()

        private fun strike(token: String? = null): BubbleContent? {
            record?.add(tag)
            if (throwOnCall) throw IllegalStateException("boom")
            return if (token != null && byToken.isNotEmpty()) byToken[token] else result
        }
    }

    private fun content(kind: String, token: String, candidates: List<BubbleCandidate> = emptyList()) =
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
    fun `skeleton providers all null fall back to missing with web and notebook flags`() {
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
        val bubble = r.resolve("退避三舍，以报楚成王", 1)
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
        val hit = content(BubbleKind.WORD, "君子")
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = nullStub,
            place = nullStub,
            word = Stub("word", hit),
            char = Stub("char"),
        )
        val bubble = r.resolve("君子", 1)
        assertSame(hit, bubble)
    }

    @Test
    fun `candidates carried through to bubble`() {
        val hit = content(
            BubbleKind.PLACE,
            "晋",
            candidates = listOf(
                BubbleCandidate("晋", "晋（诸侯国）"),
                BubbleCandidate("晋", "晋（今山西晋城）"),
            ),
        )
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = nullStub,
            place = Stub("place", hit),
            word = nullStub,
            char = nullStub,
        )
        val bubble = r.resolve("晋", 0)
        assertEquals(
            listOf(
                BubbleCandidate("晋", "晋（诸侯国）"),
                BubbleCandidate("晋", "晋（今山西晋城）"),
            ),
            bubble!!.candidates,
        )
    }

    @Test
    fun `candidate switch re-queries bare token not decorated display name`() {
        val jiang = content(BubbleKind.PLACE, "绛")
        val candidate = BubbleCandidate(query = "绛", display = "绛（晋 城邑 前722–前376）")
        val place = Stub("place", byToken = mapOf("绛" to jiang))
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = nullStub,
            place = place,
            word = nullStub,
            char = nullStub,
        )

        val switched = r.queryToken(candidate.query, ContextAnchor)
        assertNotNull(switched)
        assertEquals("绛", switched!!.token)
        assertSame(jiang, switched)

        // 展示串带括号装饰时查不到（旧缺陷：整串污染重查词），证明重查必须走裸 query。
        assertNull(r.queryToken(candidate.display, ContextAnchor))
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
        val hit = content(BubbleKind.PERSON, "孔子")
        val r = resolver(
            date = nullStub,
            allusion = nullStub,
            person = Stub("person", hit),
            place = nullStub,
            word = nullStub,
            char = nullStub,
        )
        assertSame(hit, r.queryToken("孔子"))
        assertNull(r.queryToken(""))
    }

    @Test
    fun `english token cleaned keeps apostrophe and hyphen`() {
        assertEquals("don't", BubbleSupport.cleanToken("don't！"))
        assertEquals("half-life", BubbleSupport.cleanToken("（half-life）"))
    }

    // ---------- Provider 纯逻辑（纯内存种子，无 Context 依赖） ----------

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
        // 同年对照：周平王四十九年。
        assertTrue(bubble.body.contains("四十九") || bubble.detail.contains("四十九"))
    }

    @Test
    fun `seed char resolves with senses shuowen and usage`() {
        val bubble = CharProvider.query("一", ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.CHAR, bubble!!.kind)
        assertEquals("一", bubble.token)
        assertEquals("yi", bubble.phonetic)
        assertTrue(bubble.detail.contains("义项"))
        assertTrue(bubble.detail.contains("说文"))
        assertTrue(bubble.detail.contains("用例"))
        assertTrue(bubble.source.isNotBlank())
    }

    @Test
    fun `plain seeded-out char does not pop allusion`() {
        // 「抽」不在种子字库：单字 Provider 返回 null，交 Missing 兜底；
        // 典故槽只做 2~4 字整词匹配，绝不把单字拆成典故。
        assertNull(CharProvider.query("抽", ContextAnchor))
        assertNull(WordProvider.matchAllusion("抽", 0, ContextAnchor))
        val bubble = BubbleResolver.resolve("抽", 0)
        assertNotNull(bubble)
        assertFalse(bubble!!.kind == BubbleKind.ALLUSION)
    }

    @Test
    fun `underlined allusion word hits as allusion not split into chars`() {
        val bubble = WordProvider.matchAllusion("退避三舍，以报楚成王", 1, ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.ALLUSION, bubble!!.kind)
        assertEquals("退避三舍", bubble.token)
        assertTrue(bubble.source.contains("《"))
        assertTrue(bubble.detail.isNotBlank())
    }

    @Test
    fun `common word hits word slot`() {
        val bubble = WordProvider.matchWord("君子之儒，小人之比", 0, ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.WORD, bubble!!.kind)
        assertEquals("君子", bubble.token)
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
        assertTrue(bubble.detail.contains("本名"))
    }

    @Test
    fun `place anchored to spring and autumn lists modern reference as candidate`() {
        // 锚到《左传》春秋区间（前 722~前 453）：默认命中春秋晋地，今址参考层只进候选。
        ContextAnchor.setYearRange(-722..-453, "鲁")
        val bubble = PlaceProvider.query("晋", ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.PLACE, bubble!!.kind)
        assertTrue(bubble.actions.any { it is Action.OpenMap })
        assertTrue(bubble.candidates.any { it.display.contains("晋城") })
    }

    @Test
    fun `place anchored to tang dynasty resolves jinzhou`() {
        // 读唐史（贞观年间）：同名异地晋州进入区间，春秋晋国出区间排后。
        ContextAnchor.setYear(629, "唐")
        val bubble = PlaceProvider.query("晋", ContextAnchor)
        assertNotNull(bubble)
        assertEquals(BubbleKind.PLACE, bubble!!.kind)
        val mapAction = bubble!!.actions.filterIsInstance<Action.OpenMap>().singleOrNull()
        assertNotNull(mapAction)
        assertEquals("P-JINZHOU", mapAction!!.placeId)
        assertTrue(bubble.body.contains("晋州") || bubble.detail.contains("晋州"))
    }

    @Test
    fun `action sealed keeps exactly four frozen subclasses`() {
        val names = Action::class.sealedSubclasses.map { it.simpleName }.toSet()
        assertEquals(
            setOf("OpenMap", "OpenPerson", "OpenTimeline", "OpenRelation"),
            names,
        )
    }

    // ---------- 生字本 ----------

    @Test
    fun `notebook store encode decode round trip preserves token book chapter timestamp`() {
        val entries = listOf(
            NotebookStore.Entry("重耳", "春秋左传", "隐公元年", 1737000000000L),
            NotebookStore.Entry("退避三舍", "春秋左传", "僖公二十三年", 1737000001000L),
        )
        val raw = NotebookStore.encode(entries)
        val restored = NotebookStore.decode(raw)
        assertEquals(entries, restored)
    }

    @Test
    fun `notebook store decode skips malformed lines without throwing`() {
        val raw = buildString {
            appendLine("重耳\t春秋左传\t隐公元年\t1737000000000")
            appendLine("坏行\t只有两列")
            appendLine("\t\t\tnot-a-number")
        }
        val restored = NotebookStore.decode(raw)
        assertEquals(1, restored.size)
        assertEquals("重耳", restored.first().token)
    }

    @Test
    fun `fallback web search url encodes token and notebook degrades without context`() {
        val url = BubbleFallback.webSearchUrl("退避三舍")
        assertTrue(url.startsWith("https://www.baidu.com/s?wd="))
        // 宿主 JVM 无 applicationContext：联网 Intent 与落库均安全降级为 null / false。
        assertNull(BubbleFallback.webSearchIntent("退避三舍"))
        assertFalse(BubbleFallback.saveToNotebook("重耳", "春秋左传", "隐公元年"))
    }
}
