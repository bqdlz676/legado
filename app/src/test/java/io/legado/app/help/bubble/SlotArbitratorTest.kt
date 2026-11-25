package io.legado.app.help.bubble

import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * B35 名物槽 / 多字词槽邻接仲裁纯 JVM 验收。
 *
 * 覆盖：等长不同类、长度不同两向、artifact 返回 null 不参与、word 命中时不重复产出、
 * O2 反向断言（artifact 命中后同一 span 不再冒出 word 气泡）。仲裁规则只在
 * [SlotArbitrator] 一处，本类同时覆盖仲裁器纯函数与 Resolver 两条链的接线结果。
 */
class SlotArbitratorTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
    }

    // ---------- 桩件与夹具 ----------

    private class Stub(
        private val tag: String,
        private val record: MutableList<String>? = null,
        private val result: BubbleContent? = null,
    ) : BubbleProvider {
        override fun query(token: String, anchor: ContextAnchor): BubbleContent? = strike()
        override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike()
        override fun matchAllusion(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike()
        override fun matchWord(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike()

        private fun strike(): BubbleContent? {
            record?.add(tag)
            return result
        }
    }

    private fun content(token: String) = BubbleContent(
        kind = BubbleKind.WORD,
        token = token,
        phonetic = null,
        headline = token,
        body = "body",
        detail = "detail",
        actions = emptyList(),
        candidates = emptyList(),
        source = "test",
    )

    private fun resolver(
        artifact: BubbleProvider,
        word: BubbleProvider,
        record: MutableList<String>? = null,
    ) = BubbleResolverImpl(
        date = Stub("date", record),
        allusion = Stub("allusion", record),
        person = Stub("person", record),
        place = Stub("place", record),
        word = word,
        char = Stub("char", record),
        missing = MissingProvider,
        artifact = artifact,
    )

    // ---------- 仲裁器纯函数 ----------

    @Test
    fun `等长候选名物槽按领域优先胜出`() {
        val artifactHit = content("白马")
        val wordHit = content("白马")
        assertSame(artifactHit, SlotArbitrator.chooseArtifactOrWord(artifactHit, wordHit))
    }

    @Test
    fun `名物候选更长时胜出词槽短词让位`() {
        val artifactHit = content("滕王阁")
        val wordHit = content("王阁")
        assertSame(artifactHit, SlotArbitrator.chooseArtifactOrWord(artifactHit, wordHit))
    }

    @Test
    fun `词槽候选更长时名物短词让位词槽`() {
        val artifactHit = content("赤兔")
        val wordHit = content("赤兔马驹")
        assertSame(wordHit, SlotArbitrator.chooseArtifactOrWord(artifactHit, wordHit))
    }

    @Test
    fun `名物返回null不参与裁决直接取词槽`() {
        val wordHit = content("君子")
        assertSame(wordHit, SlotArbitrator.chooseArtifactOrWord(null, wordHit))
    }

    @Test
    fun `词槽返回null不参与裁决直接取名物槽`() {
        val artifactHit = content("龙泉剑")
        assertSame(artifactHit, SlotArbitrator.chooseArtifactOrWord(artifactHit, null))
    }

    @Test
    fun `两侧皆空返回null交链尾兜底`() {
        assertNull(SlotArbitrator.chooseArtifactOrWord(null, null))
    }

    // ---------- 段落链接线（dispatch） ----------

    @Test
    fun `名物等长命中抑制同长词槽只出一张名物气泡`() {
        val record = mutableListOf<String>()
        val artifactHit = content("赤兔")
        val wordHit = content("赤兔")
        val r = resolver(
            artifact = Stub("artifact", record, artifactHit),
            word = Stub("word", record, wordHit),
            record = record,
        )

        val bubble = r.resolve("胯下赤兔日行千里", 2)

        // O2 反向断言：artifact 命中后同一 span 不再冒出 word 气泡；返回唯一胜出实例。
        assertSame(artifactHit, bubble)
        assertNotSame(wordHit, bubble)
        assertTrue(record.contains("artifact"))
        assertTrue(record.contains("word"))
    }

    @Test
    fun `段落链词槽更长时名物短词让位词槽`() {
        val artifactHit = content("赤兔")
        val wordHit = content("赤兔马驹")
        val r = resolver(
            artifact = Stub("artifact", result = artifactHit),
            word = Stub("word", result = wordHit),
        )

        val bubble = r.resolve("胯下赤兔马驹成群", 2)

        assertSame(wordHit, bubble)
        assertNotSame(artifactHit, bubble)
    }

    @Test
    fun `段落链名物更长时词槽短词让位名物槽`() {
        val artifactHit = content("滕王阁")
        val wordHit = content("王阁")
        val r = resolver(
            artifact = Stub("artifact", result = artifactHit),
            word = Stub("word", result = wordHit),
        )

        val bubble = r.resolve("滕王高阁临江渚", 0)

        assertSame(artifactHit, bubble)
        assertNotSame(wordHit, bubble)
    }

    @Test
    fun `段落链名物为空时词槽候选正常出泡`() {
        val wordHit = content("君子")
        val r = resolver(
            artifact = Stub("artifact"),
            word = Stub("word", result = wordHit),
        )

        assertSame(wordHit, r.resolve("君子之儒", 0))
    }

    // ---------- 整词链接线（queryToken） ----------

    @Test
    fun `整词链名物等长命中同样抑制词槽`() {
        val artifactHit = content("白马")
        val wordHit = content("白马")
        val r = resolver(
            artifact = Stub("artifact", result = artifactHit),
            word = Stub("word", result = wordHit),
        )

        val bubble = r.queryToken("白马")

        assertSame(artifactHit, bubble)
        assertNotSame(wordHit, bubble)
    }

    @Test
    fun `整词链两侧皆空返回null不抛异常`() {
        val r = resolver(
            artifact = Stub("artifact"),
            word = Stub("word"),
        )

        // 整词链无 Missing 兜底槽；两侧皆空且 char 桩亦为空时返回 null，属既有契约。
        assertNull(r.queryToken("龘"))
    }
}
