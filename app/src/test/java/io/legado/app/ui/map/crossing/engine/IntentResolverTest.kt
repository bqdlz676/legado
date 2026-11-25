package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Corps
import io.legado.app.ui.map.crossing.model.FactionRole
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.Intent
import io.legado.app.ui.map.crossing.model.Morale
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D3 判低项补强（纯 JVM，不依赖 Android runtime）。
 *
 * 验收 `docs/验收-八向octile度量.md` D3：[IntentResolver] 此前无专门单测，Double 化后
 * 设伏五 / 制高点三 / 夺隘五 / 支援一各斜向分支的几何翻转只靠静态推演。本类锁定：
 * - 正交命中与斜向命中的应战边界；
 * - 各半径阈值的「恰好等于」与「半径加一」两侧行为；
 * - nearestEnemyDist 由 Int 改 Double 后的比较：等阈值不越线、斜向略小于阈值才触发；
 * - 相邻盟友遭攻的支援分支；无敌军空集不崩。
 *
 * 距离口径全部走顶层单源 octile：横纵一步 1.0，对角一步根号二约 1.4142。
 */
class IntentResolverTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val projection = GeoProjection(focus, 32)

    private fun plainMap(): TerrainMap {
        val data = IntArray(32 * 32) { 128 }
        val raster = HeightRaster.create(32, 32, data, degradedHint = false)
        return TerrainMap(raster, projection, emptyList())
    }

    private fun corps(
        id: String,
        position: Cell,
        strength: Double = 8.0,
        morale: Morale = Morale.STEADY,
        intent: Intent = Intent.HOLD,
        exposed: Boolean = false,
        highGround: Boolean = false,
        supply: Int = 5,
        supplyCutTurns: Int = 0,
    ) = Corps(
        id = id,
        name = id,
        sideId = "own",
        role = FactionRole.OWN,
        type = ArmyType.REGULAR,
        strength = strength,
        fatigue = 0.0,
        morale = morale,
        intent = intent,
        position = position,
        latLng = Geo(0.0, 0.0),
        target = null,
        supply = supply,
        supplyCutTurns = supplyCutTurns,
        alive = true,
        surrendered = false,
        exposed = exposed,
        highGround = highGround,
        route = emptyList(),
        lastKnown = emptyMap(),
        visionRadiusCells = 4,
    )

    @Test
    fun `正交相邻一格内敌军触发应战意图`() {
        // 正交邻接 octile 等于 1.0，落在 ADJACENT_CELLS 闭区间内，最高优先级应战。
        val map = plainMap()
        val own = corps("own", Cell(10, 10))
        val enemy = corps("enemy", Cell(11, 10))

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, emptyList(), emptyList())

        assertEquals(Intent.FIGHT, decision.intent)
        assertEquals(Cell(11, 10), decision.target)
    }

    @Test
    fun `斜向相邻敌军格距根号二 不触发应战落入后续裁决`() {
        // 斜向邻接 octile 等于根号二约 1.4142，大于 ADJACENT_CELLS 1，不触发应战；
        // 旧曼哈顿口径记为 2，二者都大于 1，但本用例锁定距离值走 octile 双精度口径。
        val map = plainMap()
        val own = corps("own", Cell(10, 10))
        val enemy = corps("enemy", Cell(11, 11))

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, emptyList(), emptyList())

        assertFalse("斜向邻接不应触发应战", decision.intent == Intent.FIGHT)
        assertEquals("据守意图落空到最终原地据守", Intent.HOLD, decision.intent)
    }

    @Test
    fun `制高点敌军恰好三格 等阈值不触发凭险据守`() {
        // HOLD_HIGH_GROUND_RANGE 为严格小于 3；正交三格 octile 恰为 3.0，等阈值不越线。
        val map = plainMap()
        val own = corps("own", Cell(10, 10), highGround = true)
        val enemy = corps("enemy", Cell(13, 10))

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, emptyList(), emptyList())

        assertEquals(Intent.HOLD, decision.intent)
        assertFalse("格距恰好等于三格不应走制高点据守分支", decision.reason.contains("制高点"))
    }

    @Test
    fun `制高点敌军三格外一格 超半径不触发凭险据守`() {
        // 半径加一：正交四格 octile 4.0，大于 3，不触发制高点分支。
        val map = plainMap()
        val own = corps("own", Cell(10, 10), highGround = true)
        val enemy = corps("enemy", Cell(14, 10))

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, emptyList(), emptyList())

        assertEquals(Intent.HOLD, decision.intent)
        assertFalse("格距四格不应走制高点据守分支", decision.reason.contains("制高点"))
    }

    @Test
    fun `制高点斜向两格敌军格距小于三格 触发凭险据守`() {
        // 斜向二对二 octile 等于二倍根号二约 2.8284，比阈值 3 小约 0.1716，严格小于成立；
        // 旧曼哈顿口径为 4，大于 3 不会触发，本条锁定 Double 化后斜向分支的几何翻转。
        val map = plainMap()
        val own = corps("own", Cell(10, 10), highGround = true)
        val enemy = corps("enemy", Cell(12, 12))

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, emptyList(), emptyList())

        assertEquals(Intent.HOLD, decision.intent)
        assertTrue("斜向格距小于三格应走制高点据守分支", decision.reason.contains("制高点"))
    }

    @Test
    fun `设伏敌军恰好五格 等阈值不触发关隘设伏`() {
        // AMBUSH_ENEMY_RANGE 为严格小于 5；正交五格 octile 恰为 5.0，等阈值不越线。
        val map = plainMap()
        val choke = Cell(10, 10)
        val own = corps("own", choke)
        val enemy = corps("enemy", Cell(15, 10))

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, listOf(choke), emptyList())

        assertFalse("格距恰好等于五格不应触发设伏", decision.intent == Intent.AMBUSH)
        assertEquals(Intent.HOLD, decision.intent)
    }

    @Test
    fun `设伏斜向三格敌军格距小于五格 触发关隘设伏`() {
        // 斜向三对三 octile 等于三倍根号二约 4.2426，小于 5；旧曼哈顿口径为 6，大于 5，
        // 本条锁定斜向略小于阈值时设伏分支成立。
        val map = plainMap()
        val choke = Cell(10, 10)
        val own = corps("own", choke)
        val enemy = corps("enemy", Cell(13, 13))

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, listOf(choke), emptyList())

        assertEquals(Intent.AMBUSH, decision.intent)
    }

    @Test
    fun `设伏敌军五格加一 超半径不触发关隘设伏`() {
        // 半径加一：正交六格 octile 6.0，大于 5，不触发设伏。
        val map = plainMap()
        val choke = Cell(10, 10)
        val own = corps("own", choke)
        val enemy = corps("enemy", Cell(16, 10))

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, listOf(choke), emptyList())

        assertFalse("格距六格不应触发设伏", decision.intent == Intent.AMBUSH)
        assertEquals(Intent.HOLD, decision.intent)
    }

    @Test
    fun `夺隘敌军恰好五格 等阈值不触发抢占关隘`() {
        // SEIZE_ENEMY_RANGE 为严格大于 5；正交五格 octile 恰为 5.0，不满足大于条件。
        val map = plainMap()
        val own = corps("own", Cell(10, 10), intent = Intent.ADVANCE)
        val enemy = corps("enemy", Cell(15, 10))
        val freeChoke = Cell(20, 20)

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, listOf(freeChoke), emptyList())

        assertFalse("格距恰好等于五格不应抢占关隘", decision.intent == Intent.SEIZE)
    }

    @Test
    fun `夺隘敌军五格加一 触发抢占空关隘`() {
        // 半径加一：正交六格 octile 6.0，严格大于 5；关隘无盟友驻守，输出抢占意图与落点。
        val map = plainMap()
        val own = corps("own", Cell(10, 10), intent = Intent.ADVANCE)
        val enemy = corps("enemy", Cell(16, 10))
        val freeChoke = Cell(20, 20)

        val decision = IntentResolver.resolve(own, emptyList(), listOf(enemy), map, listOf(freeChoke), emptyList())

        assertEquals(Intent.SEIZE, decision.intent)
        assertEquals(freeChoke, decision.target)
    }

    @Test
    fun `相邻盟友遭攻击 转支援意图`() {
        // 支援分支：本军与盟友正交相邻 octile 1.0，盟友与敌军正交相邻 octile 1.0，
        // 两处相邻判定均走 ADJACENT_CELLS 闭区间；本军与敌军隔一格 octile 2.0，不先触发应战。
        val map = plainMap()
        val own = corps("own", Cell(10, 10))
        val ally = corps("ally", Cell(10, 11))
        val enemy = corps("enemy", Cell(10, 12))

        val decision = IntentResolver.resolve(own, listOf(ally), listOf(enemy), map, emptyList(), emptyList())

        assertEquals(Intent.SUPPORT, decision.intent)
        assertEquals(Cell(10, 11), decision.target)
    }

    @Test
    fun `无敌军空集裁决不抛异常并落到维持意图`() {
        // 空敌军集：nearestEnemy 为 null、nearestEnemyDist 取 Double.MAX_VALUE，
        // 各距离分支均不触发，最终按当前意图 CONTINUE 收尾，不崩、不误判。
        val map = plainMap()
        val own = corps("own", Cell(10, 10), intent = Intent.CONTINUE)

        val decision = IntentResolver.resolve(own, emptyList(), emptyList(), map, emptyList(), emptyList())

        assertEquals(Intent.CONTINUE, decision.intent)
    }
}
