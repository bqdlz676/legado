package io.legado.app.ui.book.read.ink

import io.legado.app.help.bubble.Action
import io.legado.app.help.bubble.BubbleKind
import io.legado.app.help.bubble.BubbleResolver
import io.legado.app.help.bubble.contract.Action as ContractAction
import io.legado.app.help.chrono.ChronoPlaces
import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * B4-08 气泡 resolver 接线验收（纯 JVM，无 Android / Compose 环境依赖）。
 *
 * 覆盖两类面：
 *  1. 冻结契约五子类（contract.Action：OpenMap / OpenPerson / OpenTimeline /
 *     OpenRelation / OpenArtifact）经 [InkActionDispatcher.routeContract] 穷尽归类；
 *     OpenArtifact 名物详情 Sheet 消费方尚未接线，路由为空、chip 置灰。
 *  2. 解析兜底：Provider 全空 / 未知符号 / 空段落落 Missing 兜底气泡，结构完整、不抛异常、
 *     不留空白态；越界字位先夹取到末位下标，再按正常优先级链解析（末位所在整词被词槽
 *     命中时出词气泡，未收录才回落单字 / Missing），夹取本身不抛异常；候选整词未收录
 *     返回 null，调用方保留当前气泡。
 *
 * 本类不反射取成员、不整份读源文件 contains、不用恒真断言；断言锁定冻结契约字段与
 * 兜底口径，非断言自身实现写法。
 */
class InkBubbleRouteResolverTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
        ChronoPlaces.resetForTest()
        ContextAnchor.setYear(-722)
    }

    // ---------- 冻结契约五子类路由归类 ----------

    @Test
    fun `contract OpenMap 映射地图路由，透传 placeId 与锚点年`() {
        val route = InkActionDispatcher.routeContract(
            ContractAction.OpenMap("P-JIN"), ContextAnchor,
        ) as InkRoute.Map
        assertEquals("P-JIN", route.placeName)
        assertEquals(-722, route.year)
        assertTrue(InkActionDispatcher.canDispatchContract(
            ContractAction.OpenMap("P-JIN"), ContextAnchor,
        ))
    }

    @Test
    fun `contract OpenPerson 映射人物路由，主键回退 null，锚点年代一并携带`() {
        val route = InkActionDispatcher.routeContract(
            ContractAction.OpenPerson("孔子"), ContextAnchor,
        ) as InkRoute.Person
        assertEquals("孔子", route.name)
        assertNull(route.personId)
        assertEquals(-722, route.anchorYear)
        assertTrue(route.anchorLabel?.contains("春秋") == true)
    }

    @Test
    fun `contract OpenTimeline 映射时间轴路由，公元前负数与空标题透传`() {
        val route = InkActionDispatcher.routeContract(
            ContractAction.OpenTimeline(-722), ContextAnchor,
        ) as InkRoute.Timeline
        assertEquals(-722, route.year)
        assertNull(route.label)
        assertTrue(InkActionDispatcher.canDispatchContract(
            ContractAction.OpenTimeline(618), ContextAnchor,
        ))
    }

    @Test
    fun `contract OpenRelation 映射关系图路由，透传姓名与锚点年代`() {
        val route = InkActionDispatcher.routeContract(
            ContractAction.OpenRelation("孔子"), ContextAnchor,
        ) as InkRoute.Relation
        assertEquals("孔子", route.name)
        assertEquals(-722, route.anchorYear)
        assertTrue(route.anchorLabel?.contains("春秋") == true)
    }

    @Test
    fun `contract OpenArtifact 暂无接通消费方，路由为空且 chip 置灰`() {
        val route = InkActionDispatcher.routeContract(
            ContractAction.OpenArtifact("ARTIFACT-1", "weapon"), ContextAnchor,
        )
        assertNull(route)
        assertFalse(
            InkActionDispatcher.canDispatchContract(
                ContractAction.OpenArtifact("ARTIFACT-1", "weapon"), ContextAnchor,
            ),
        )
    }

    // ---------- 既有链四类回归 ----------

    @Test
    fun `既有链四类动作均有接通消费方，路由非空`() {
        val legacyActions = listOf(
            Action.OpenMap("P1"),
            Action.OpenPerson("孔子"),
            Action.OpenTimeline(-722),
            Action.OpenRelation("孔子"),
        )
        legacyActions.forEach { action ->
            assertTrue(InkActionDispatcher.canDispatch(action, ContextAnchor))
            assertTrue(InkActionDispatcher.route(action, ContextAnchor) != null)
        }
    }

    // ---------- Resolver Missing 兜底 ----------

    @Test
    fun `未收录生僻字落 Missing 兜底，结构完整不崩不空屏`() {
        val bubble = BubbleResolver.resolve("龘", 0)
        assertNotNull(bubble)
        assertEquals(BubbleKind.MISSING, bubble!!.kind)
        assertEquals("龘", bubble.token)
        assertTrue(bubble.body.isNotBlank())
        assertTrue(bubble.detail.isNotBlank())
        assertTrue(bubble.source.isNotBlank())
        assertTrue(bubble.allowWebSearch)
        assertTrue(bubble.allowNotebook)
        assertTrue(bubble.actions.isEmpty())
        assertTrue(bubble.candidates.isEmpty())
    }

    @Test
    fun `未知符号落 Missing 兜底，不强行释义`() {
        // 非汉字符号「※」：纪年正则、典故整词、人物 / 地名称谓、名物槽、多字词槽与单字槽
        // 的入口都先判汉字（BubbleSupport.isHan）或清洗成空串，机制上不可能命中任何种子；
        // 兜底气泡结构完整：token 回守符号本身，body/detail/source 非空，不进动作与候选。
        val bubble = BubbleResolver.resolve("※", 0)
        assertNotNull(bubble)
        assertEquals(BubbleKind.MISSING, bubble!!.kind)
        assertEquals("※", bubble.token)
        assertTrue(bubble.body.isNotBlank())
        assertTrue(bubble.detail.isNotBlank())
        assertTrue(bubble.source.isNotBlank())
        assertTrue(bubble.allowWebSearch)
        assertTrue(bubble.allowNotebook)
        assertTrue(bubble.actions.isEmpty())
        assertTrue(bubble.candidates.isEmpty())
    }

    @Test
    fun `空段落落 Missing 兜底，token 回退此处`() {
        val bubble = BubbleResolver.resolve("", 0)
        assertNotNull(bubble)
        assertEquals(BubbleKind.MISSING, bubble!!.kind)
        assertEquals("此处", bubble.token)
        assertTrue(bubble.body.isNotBlank())
    }

    @Test
    fun `越界字位夹取末位不崩，结果与末位原位解析一致`() {
        // 越界 pos（99）由 resolve 内 safePos 夹到末位下标；夹取后走的槽位与直接点末位
        // 完全一致：kind / token 相同，不抛异常、不留空白态。槽位归属不在此锁定——末位所在
        // 整词若被多字词槽命中，token 是整词；未收录才回落单字或 Missing，两种形态都允许。
        val paragraph = "春秋"
        val clamped = BubbleResolver.resolve(paragraph, 99)
        val atLast = BubbleResolver.resolve(paragraph, paragraph.lastIndex)
        assertNotNull(clamped)
        assertNotNull(atLast)
        assertEquals(atLast!!.kind, clamped!!.kind)
        assertEquals(atLast.token, clamped.token)
        assertTrue(clamped.token.isNotBlank())
        assertTrue(clamped.token.contains(paragraph.last()))
        assertTrue(clamped.body.isNotBlank())
        assertTrue(clamped.detail.isNotBlank())
        assertTrue(clamped.source.isNotBlank())
    }

    @Test
    fun `候选整词未收录返回 null，调用方保留当前气泡`() {
        val switched = BubbleResolver.queryToken("魑魅魍魉", ContextAnchor)
        assertNull(switched)
    }

    @Test
    fun `纯空白段落解析不抛异常并落兜底`() {
        val bubble = BubbleResolver.resolve("   ", 0)
        assertNotNull(bubble)
        assertEquals(BubbleKind.MISSING, bubble!!.kind)
        assertTrue(bubble.body.isNotBlank())
    }
}
