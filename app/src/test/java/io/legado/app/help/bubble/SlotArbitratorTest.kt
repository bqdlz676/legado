package io.legado.app.help.bubble

import io.legado.app.help.bubble.contract.MISSING_SOURCE
import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * B35 名物槽 / 多字词槽邻接仲裁纯 JVM 验收；B36 补 N1 调用计数桩与 N2 来源门禁断言。
 *
 * 覆盖：等长不同类、长度不同两向、artifact 返回 null 不参与、word 命中时不重复产出、
 * O2 反向断言（artifact 命中后同一 span 不再冒出 word 气泡）、N2 兜底壳不冒充命中。
 * 仲裁规则只在 [SlotArbitrator] 一处，本类同时覆盖仲裁器纯函数与 Resolver 两条链的接线结果。
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
        /** 被调用次数（计数桩：若实现回退 first-hit 短路，词槽不再被问，计数为 0，断言变红）。 */
        var callCount = 0
            private set
        /** 每次调用的入参（query 记 token，hitAt / matchWord 记段落串），供回源核对。 */
        val recordedArgs = mutableListOf<String>()

        override fun query(token: String, anchor: ContextAnchor): BubbleContent? = strike(token)
        override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike(paragraph)
        override fun matchAllusion(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike(paragraph)
        override fun matchWord(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? = strike(paragraph)

        private fun strike(arg: String? = null): BubbleContent? {
            callCount++
            if (arg != null) recordedArgs += arg
            record?.add(tag)
            return result
        }
    }

    private fun content(token: String, source: String = "test") = BubbleContent(
        kind = BubbleKind.WORD,
        token = token,
        phonetic = null,
        headline = token,
        body = "body",
        detail = "detail",
        actions = emptyList(),
        candidates = emptyList(),
        source = source,
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

    // ---------- N2：来源门禁（兜底壳 / 残缺件不冒充命中） ----------

    @Test
    fun `名物兜底壳未命中来源不参与仲裁让位词槽`() {
        // 壳：source 固定 MISSING_SOURCE（B25 五态兜底壳口径，见 ArtifactFallback.notFound）。
        val shell = content("白马", source = MISSING_SOURCE)
        val wordHit = content("白马")
        // 反向锁：若仲裁器退回只比长度，等长壳按领域优先胜出，本断言变红。
        assertSame(wordHit, SlotArbitrator.chooseArtifactOrWord(shell, wordHit))
    }

    @Test
    fun `名物兜底壳且词槽为空返回null不下钻壳`() {
        val shell = content("龙泉剑", source = MISSING_SOURCE)
        // 壳被门禁剔除后两侧皆空：返回 null 交链尾 char / Missing，绝不回传壳本身。
        assertNull(SlotArbitrator.chooseArtifactOrWord(shell, null))
    }

    @Test
    fun `名物候选无出处空来源按残缺件下钻词槽`() {
        // 协议要求每条数据带来源与版本；空 / 空白来源按残缺件处理，不进长度比较。
        val hollow = content("赤兔", source = "")
        val wordHit = content("赤兔")
        assertSame(wordHit, SlotArbitrator.chooseArtifactOrWord(hollow, wordHit))
    }

    @Test
    fun `真实名物候选带来源正常参与仲裁等长胜出`() {
        // 真实 Provider 候选：source 为真实出处标注，门禁放行，等长仍归名物领域优先。
        val artifactHit = content("白马", source = "seed:horse-baima")
        val wordHit = content("白马")
        assertSame(artifactHit, SlotArbitrator.chooseArtifactOrWord(artifactHit, wordHit))
    }

    // ---------- 段落链接线（dispatch） ----------

    @Test
    fun `名物等长命中抑制同长词槽只出一张名物气泡`() {
        val record = mutableListOf<String>()
        val artifactHit = content("赤兔")
        val wordHit = content("赤兔")
        val artifactStub = Stub("artifact", record, artifactHit)
        val wordStub = Stub("word", record, wordHit)
        val r = resolver(
            artifact = artifactStub,
            word = wordStub,
            record = record,
        )

        val bubble = r.resolve("胯下赤兔日行千里", 2)

        // O2 反向断言：artifact 命中后同一 span 不再冒出 word 气泡；返回唯一胜出实例。
        assertSame(artifactHit, bubble)
        assertNotSame(wordHit, bubble)
        assertTrue(record.contains("artifact"))
        assertTrue(record.contains("word"))
        // N1 计数桩：两槽各被查询一次；回退 first-hit 短路时 word 计数为 0，本断言变红。
        assertEquals(1, artifactStub.callCount)
        assertEquals(1, wordStub.callCount)
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
        val record = mutableListOf<String>()
        val artifactHit = content("滕王阁")
        val wordHit = content("王阁")
        val wordStub = Stub("word", record, wordHit)
        val r = resolver(
            artifact = Stub("artifact", record, artifactHit),
            word = wordStub,
            record = record,
        )

        val bubble = r.resolve("滕王高阁临江渚", 0)

        assertSame(artifactHit, bubble)
        assertNotSame(wordHit, bubble)
        // N1：名物更长命中分支下，词槽仍被查询一次，结果未回传。
        assertTrue(record.contains("word"))
        assertEquals(1, wordStub.callCount)
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

    @Test
    fun `名物桩返回兜底壳时词槽候选正常出泡不被壳压制`() {
        // N2 接线级反向锁：适配层若被改动放进兜底壳，仲裁器来源门禁仍须把壳剔除，词槽胜出。
        val shell = content("赤兔", source = MISSING_SOURCE)
        val wordHit = content("赤兔")
        val wordStub = Stub("word", result = wordHit)
        val r = resolver(
            artifact = Stub("artifact", result = shell),
            word = wordStub,
        )

        val bubble = r.resolve("胯下赤兔日行千里", 2)

        assertSame(wordHit, bubble)
        assertNotSame(shell, bubble)
        assertEquals(1, wordStub.callCount)
    }

    // ---------- 整词链接线（queryToken） ----------

    @Test
    fun `整词链名物等长命中同样抑制词槽`() {
        val record = mutableListOf<String>()
        val artifactHit = content("白马")
        val wordHit = content("白马")
        val artifactStub = Stub("artifact", record, artifactHit)
        val wordStub = Stub("word", record, wordHit)
        val r = resolver(
            artifact = artifactStub,
            word = wordStub,
            record = record,
        )

        val bubble = r.queryToken("白马")

        assertSame(artifactHit, bubble)
        assertNotSame(wordHit, bubble)
        // N1 调用记录桩（对齐段落链 #7）：artifact 命中后 word 侧仍被查询一次，入参为原 token，
        // 结果未回传；回退 first-hit 短路时 record 不含 "word"、计数为 0，本断言变红。
        assertTrue(record.contains("artifact"))
        assertTrue(record.contains("word"))
        assertEquals(1, artifactStub.callCount)
        assertEquals(1, wordStub.callCount)
        assertEquals("白马", wordStub.recordedArgs.single())
    }

    @Test
    fun `整词链名物更长时词槽仍被查询一次且让位名物槽`() {
        val record = mutableListOf<String>()
        val artifactHit = content("滕王阁")
        val wordHit = content("王阁")
        val wordStub = Stub("word", record, wordHit)
        val r = resolver(
            artifact = Stub("artifact", record, artifactHit),
            word = wordStub,
            record = record,
        )

        val bubble = r.queryToken("滕王阁")

        assertSame(artifactHit, bubble)
        assertNotSame(wordHit, bubble)
        assertTrue(record.contains("word"))
        assertEquals(1, wordStub.callCount)
        assertEquals("滕王阁", wordStub.recordedArgs.single())
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
