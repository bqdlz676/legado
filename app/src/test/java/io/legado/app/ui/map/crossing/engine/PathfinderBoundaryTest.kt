package io.legado.app.ui.map.crossing.engine

import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.TerrainFocus
import io.legado.app.ui.map.crossing.terrain.GeoProjection
import io.legado.app.ui.map.crossing.terrain.HeightRaster
import io.legado.app.ui.map.crossing.terrain.TerrainMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1 判低项补强（纯 JVM，不依赖 Android runtime）。
 *
 * 覆盖验收 `docs/验收-八向octile度量.md` 判据 5 两条缺口：
 * 1. 路径层「起点 = 终点」早返回：度量层重合断言已在 [GridMetricTest]，本类补 `find` /
 *    `flankRoute` 两条路径入口的专门用例；
 * 2. 同代价多条最优路径的平局确定性：构造上下对称的双等代价绕行 Fixture，锁定同一输入
 *    两次独立裁决逐格一致；不锁定选中哪一条等价路径。
 */
class PathfinderBoundaryTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)
    private val projection = GeoProjection(focus, 32)

    private fun plainMap(): TerrainMap {
        val data = IntArray(32 * 32) { 128 }
        val raster = HeightRaster.create(32, 32, data, degradedHint = false)
        return TerrainMap(raster, projection, emptyList())
    }

    /**
     * 单格水面堵在直行通道中央：(6,10) 为非渡口水面、不可通行；上下两侧 (6,9) / (6,11)
     * 均为平地，构成两条对称的斜向绕行通道。
     */
    private fun blockedMiddleMap(): TerrainMap {
        val data = IntArray(32 * 32) { 128 }
        data[10 * 32 + 6] = 0
        val raster = HeightRaster.create(32, 32, data, degradedHint = false)
        return TerrainMap(raster, projection, emptyList())
    }

    @Test
    fun `主入口起终点重合 路径仅含起点且代价为零`() {
        // D1-①：`find` 在 `from == to` 时早返回 listOf(from)（Pathfinder.kt 主入口）。
        // 度量层 octile(a,a)=0.0 已由 GridMetricTest 锁定，这里补路径层：长度一、仅含起点、
        // 首尾格距代价为零。
        val finder = Pathfinder(plainMap(), "clear")
        val cell = Cell(7, 12)
        val path = finder.find(cell, cell, ArmyType.REGULAR)

        assertEquals("重合路径长度应为一", 1, path.size)
        assertEquals("重合路径仅含起点格", listOf(cell), path)
        assertEquals("重合路径首尾格距代价应为零", 0.0, finder.cellDist(path.first(), path.last()), 1e-12)
    }

    @Test
    fun `侧翼入口起终点重合 早返回起点且不置降级`() {
        // D1-①：`flankRoute` 在 `from == to` 时早返回 FlankRoute(listOf(from), degraded=false)。
        // 即便传入敌军条目，重合起点也不应进入视野闸门或兜底链条。
        val finder = Pathfinder(plainMap(), "clear")
        val cell = Cell(7, 12)
        val route = finder.flankRoute(
            cell,
            cell,
            ArmyType.REGULAR,
            listOf(EnemySight(Cell(20, 20), 4)),
        )

        assertEquals("重合侧翼路径长度应为一", 1, route.path.size)
        assertEquals("重合侧翼路径仅含起点格", listOf(cell), route.path)
        assertFalse("起终点重合不应置降级标志", route.degraded)
        assertEquals("早返回理由应为起点即终点", "起点即终点", route.reason)
        assertEquals("重合路径首尾格距代价应为零", 0.0, finder.cellDist(route.path.first(), route.path.last()), 1e-12)
    }

    @Test
    fun `双等代价绕行路径 两次裁决逐格一致锁定确定性`() {
        // D1-②：起点 (5,10) → 终点 (7,10)，正中 (6,10) 为非渡口水面。
        // 上绕 (5,10)→(6,9)→(7,10) 与下绕 (5,10)→(6,11)→(7,10) 各两步斜向，
        // 平地代价同为两步根号二，是两条等价最优解；其余绕行均多步、代价更高，不构成第三解。
        //
        // 为什么不断言「选哪条」：A* 开放队列 PriorityQueue 仅按 f 值比较，同 f 节点的出队
        // 先后在 JDK 契约中属任意；cameFrom 仅在严格更小时改写父节点，因此上绕 / 下绕的归属
        // 取决于堆内布局与邻接展开顺序。选哪一条等价路径本身不影响最优性，规格只要求路径最优；
        // 本用例只锁定同一输入两次独立裁决逐格一致（确定性 / 稳定性），不把平局选择写死。
        //
        // 平局实现备注：当前实现未给比较器加次级键（如节点编码），跨 JVM / 堆实现若观察到
        // 平局抖动，应另单给 open 队列比较器补次级确定性键；本单不动 Pathfinder.kt
        // （该文件当前叠加在飞 B8R5 目标格豁免改动，避免触碰他人改动区域）。
        val start = Cell(5, 10)
        val target = Cell(7, 10)
        val blocked = Cell(6, 10)

        val first = Pathfinder(blockedMiddleMap(), "clear").find(start, target, ArmyType.REGULAR)
        val second = Pathfinder(blockedMiddleMap(), "clear").find(start, target, ArmyType.REGULAR)

        assertTrue("等代价绕行路径必须非空", first.isNotEmpty())
        assertEquals("同一输入两次裁决应逐格一致（平局稳定）", first, second)
        assertEquals("路径首格为出发格", start, first.first())
        assertEquals("路径末格为目标格", target, first.last())
        assertTrue("路径不得踏中被堵水面格", blocked !in first)
        // 结构断言：中间格只可能是上下两条对称绕行格之一；不锁定具体选中哪一条。
        val middle = first[1]
        assertTrue("绕行中间格应为上绕格或下绕格", middle == Cell(6, 9) || middle == Cell(6, 11))
    }
}
