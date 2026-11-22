package io.legado.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 气泡数据协议：六变体 + Missing 兜底、候选切换、字段自检。
 */
class BubbleContentTest {

    private fun sample(kind: BubbleKind = BubbleKind.PERSON) = BubbleContent(
        kind = kind,
        token = "桓公",
        phonetic = "huán gōng",
        headline = "鲁桓公 · 鲁国第十五君",
        body = "在位十八年，隐公之弟。",
        detail = "完整档案",
        actions = listOf(BubbleAction.OpenPerson("鲁桓公")),
        candidates = listOf("鲁桓公", "齐桓公"),
        source = "CBDB #12345",
    )

    @Test
    fun `六变体均可构造且候选数大于一时提示切换`() {
        BubbleKind.entries.forEach { kind ->
            val content = sample(kind)
            assertTrue(content.hasCandidates)
            assertFalse(content.isMissing)
        }
    }

    @Test
    fun `Missing 兜底允许空 headline body`() {
        val missing = BubbleContent.missing("抽")
        assertEquals(BubbleKind.MISSING, missing.kind)
        assertTrue(missing.isMissing)
        assertTrue(missing.validate().isEmpty())
    }

    @Test
    fun `缺 token headline body 时自检给出对应问题`() {
        val broken = BubbleContent(
            kind = BubbleKind.CHAR,
            token = "",
            headline = "",
            body = "",
        )
        val problems = broken.validate()
        assertTrue(problems.any { it.contains("token") })
        assertTrue(problems.any { it.contains("headline") })
        assertTrue(problems.any { it.contains("body") })
    }

    @Test
    fun `动作四亚型均可携带参数`() {
        val actions: List<BubbleAction> = listOf(
            BubbleAction.OpenMap("chengzhou"),
            BubbleAction.OpenPerson("重耳"),
            BubbleAction.OpenTimeline(-636),
            BubbleAction.OpenRelation("狐偃"),
        )
        assertEquals(4, actions.size)
        assertTrue(actions[0] is BubbleAction.OpenMap)
        assertTrue(actions[1] is BubbleAction.OpenPerson)
        assertTrue(actions[2] is BubbleAction.OpenTimeline)
        assertTrue(actions[3] is BubbleAction.OpenRelation)
    }
}
