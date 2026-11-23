package io.legado.app.ui.book.read.ink

import io.legado.app.help.bubble.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 落文 · 气泡动作分发路由单测（切片-02 §1 Action 冻结四类）。
 *
 * 只测纯函数 [InkActionDispatcher.route] 与 [InkActionDispatcher.canDispatch]：
 * 四类 Action → Map / Person / Relation / Timeline 路由映射、字段透传、地名回退口径；
 * Intent 装配属 Android 层，由静态走查核对 extra 键，不在 JVM 单测内构造 Context。
 */
class InkActionDispatcherTest {

    private companion object {
        const val ANCHOR_YEAR = -722
    }

    @Test
    fun `OpenMap 有地名取地名，缺省回退 placeId，年代取锚点年`() {
        val named = InkActionDispatcher.route(
            Action.OpenMap(placeId = "P3", placeName = "洛阳"), ANCHOR_YEAR,
        ) as InkRoute.Map
        assertEquals("洛阳", named.placeName)
        assertEquals(ANCHOR_YEAR, named.year)

        val fallback = InkActionDispatcher.route(
            Action.OpenMap(placeId = "P1", placeName = null), ANCHOR_YEAR,
        ) as InkRoute.Map
        assertEquals("P1", fallback.placeName)

        val blankName = InkActionDispatcher.route(
            Action.OpenMap(placeId = "P2", placeName = "  "), ANCHOR_YEAR,
        ) as InkRoute.Map
        assertEquals("P2", blankName.placeName)
    }

    @Test
    fun `OpenPerson 姓名与 personId 透传，id 可空`() {
        val withId = InkActionDispatcher.route(Action.OpenPerson("孔子", "CBDB-1"), ANCHOR_YEAR) as InkRoute.Person
        assertEquals("孔子", withId.name)
        assertEquals("CBDB-1", withId.personId)

        val noId = InkActionDispatcher.route(Action.OpenPerson("颜回"), ANCHOR_YEAR) as InkRoute.Person
        assertEquals("颜回", noId.name)
        assertNull(noId.personId)
    }

    @Test
    fun `OpenRelation 映射关系图路由并透传姓名`() {
        val route = InkActionDispatcher.route(Action.OpenRelation("孔子"), ANCHOR_YEAR) as InkRoute.Relation
        assertEquals("孔子", route.name)
    }

    @Test
    fun `OpenTimeline 公元年（含公元前负数）与标题透传`() {
        val route = InkActionDispatcher.route(Action.OpenTimeline(-722, "隐公元年"), ANCHOR_YEAR) as InkRoute.Timeline
        assertEquals(-722, route.year)
        assertEquals("隐公元年", route.label)

        val noLabel = InkActionDispatcher.route(Action.OpenTimeline(618), null) as InkRoute.Timeline
        assertEquals(618, noLabel.year)
        assertNull(noLabel.label)
    }

    @Test
    fun `四类动作均有接通消费方，canDispatch 为 true`() {
        val actions = listOf(
            Action.OpenMap("P1"),
            Action.OpenPerson("孔子"),
            Action.OpenTimeline(-722),
            Action.OpenRelation("孔子"),
        )
        actions.forEach { action ->
            assertTrue(InkActionDispatcher.canDispatch(action, ANCHOR_YEAR))
            assertTrue(InkActionDispatcher.route(action, ANCHOR_YEAR) != null)
        }
    }
}
