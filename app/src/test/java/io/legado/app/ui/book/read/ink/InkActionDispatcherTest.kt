package io.legado.app.ui.book.read.ink

import io.legado.app.help.bubble.Action
import io.legado.app.help.chrono.ContextAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 落文 · 气泡动作分发路由单测（切片-02 §1 Action 冻结四类；模块契约 §2 气泡契约）。
 *
 * 只测纯函数 [InkActionDispatcher.route] 与 [InkActionDispatcher.canDispatch]：
 * 四类 Action → Map / Person / Relation / Timeline 路由映射、字段透传、地名回退口径、
 * 年代取共享锚点年。Intent 装配属 Android 层，由静态走查核对 extra 键，不在 JVM 单测构造 Context。
 */
class InkActionDispatcherTest {

    @Before
    fun setUp() {
        ContextAnchor.reset()
        ContextAnchor.setYear(-722)
    }

    @Test
    fun `OpenMap 有地名取地名，缺省回退 placeId，年代取锚点年`() {
        val named = InkActionDispatcher.route(
            Action.OpenMap(placeId = "P3", placeName = "洛阳"), ContextAnchor,
        ) as InkRoute.Map
        assertEquals("洛阳", named.placeName)
        assertEquals(-722, named.year)

        val fallback = InkActionDispatcher.route(
            Action.OpenMap(placeId = "P1", placeName = null), ContextAnchor,
        ) as InkRoute.Map
        assertEquals("P1", fallback.placeName)

        val blankName = InkActionDispatcher.route(
            Action.OpenMap(placeId = "P2", placeName = "  "), ContextAnchor,
        ) as InkRoute.Map
        assertEquals("P2", blankName.placeName)
    }

    @Test
    fun `OpenPerson 姓名与 personId 透传，id 可空，锚点年代一并携带`() {
        val withId = InkActionDispatcher.route(
            Action.OpenPerson("孔子", "CBDB-1"), ContextAnchor,
        ) as InkRoute.Person
        assertEquals("孔子", withId.name)
        assertEquals("CBDB-1", withId.personId)
        assertEquals(-722, withId.anchorYear)
        // 标签含朝代与干支：春秋 · 公元前722 年（己未）…
        assertTrue(withId.anchorLabel?.contains("春秋") == true)
        assertTrue(withId.anchorLabel?.contains("己未") == true)

        val noId = InkActionDispatcher.route(Action.OpenPerson("颜回"), ContextAnchor) as InkRoute.Person
        assertEquals("颜回", noId.name)
        assertNull(noId.personId)
        assertEquals(-722, noId.anchorYear)
    }

    @Test
    fun `OpenRelation 映射关系图路由并透传姓名与锚点年代`() {
        val route = InkActionDispatcher.route(Action.OpenRelation("孔子"), ContextAnchor) as InkRoute.Relation
        assertEquals("孔子", route.name)
        assertEquals(-722, route.anchorYear)
        assertTrue(route.anchorLabel?.contains("春秋") == true)
    }

    @Test
    fun `锚点未定年时人物 / 关系路由年代为 null，不猜年份`() {
        ContextAnchor.reset()
        val person = InkActionDispatcher.route(Action.OpenPerson("孔子"), ContextAnchor) as InkRoute.Person
        assertNull(person.anchorYear)
        assertNull(person.anchorLabel)

        val relation = InkActionDispatcher.route(Action.OpenRelation("孔子"), ContextAnchor) as InkRoute.Relation
        assertNull(relation.anchorYear)
        assertNull(relation.anchorLabel)
    }

    @Test
    fun `OpenTimeline 公元年（含公元前负数）与标题透传`() {
        val route = InkActionDispatcher.route(
            Action.OpenTimeline(-722, "隐公元年"), ContextAnchor,
        ) as InkRoute.Timeline
        assertEquals(-722, route.year)
        assertEquals("隐公元年", route.label)

        ContextAnchor.reset()
        val noLabel = InkActionDispatcher.route(Action.OpenTimeline(618), ContextAnchor) as InkRoute.Timeline
        assertEquals(618, noLabel.year)
        assertNull(noLabel.label)
    }

    @Test
    fun `锚点未定年时地图年代为 null，不猜年份`() {
        ContextAnchor.reset()
        val route = InkActionDispatcher.route(Action.OpenMap("P1"), ContextAnchor) as InkRoute.Map
        assertNull(route.year)
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
            assertTrue(InkActionDispatcher.canDispatch(action, ContextAnchor))
            assertTrue(InkActionDispatcher.route(action, ContextAnchor) != null)
        }
    }
}
