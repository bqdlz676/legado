package io.legado.app.ui.map.crossing.terrain

import io.legado.app.ui.map.crossing.engine.Pathfinder
import io.legado.app.ui.map.crossing.model.ArmyType
import io.legado.app.ui.map.crossing.model.Cell
import io.legado.app.ui.map.crossing.model.Geo
import io.legado.app.ui.map.crossing.model.TerrainFocus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 水洞坡度闸门补测（Q-225，纯 JVM，不依赖 Android runtime）。
 *
 * 背景：Q-216 把通行闸门 [TerrainMap.passable] 的坡度口径从全邻域 [TerrainMap.slopeDeg]
 * 改为跳过水面邻格的 `landSlopeDeg`（`TerrainMap.kt:96`），渡口检测 `:169` 仍走全邻域
 * `slopeDeg`。本文件锁住这两条口径，防止下次重构静默退回旧口径或把陆地陡坡一并放宽。
 *
 * 栅格口径：32×32，focus 半径 50km，单格边长 3125m；像素 0→0m、128→3513.7m、255→7000m。
 */
class TerrainSlopeWaterGateTest {

    private val focus = TerrainFocus(Geo(0.0, 0.0), 50.0)

    private fun mapOf(data: IntArray): TerrainMap =
        TerrainMap(HeightRaster.create(32, 32, data, degradedHint = false), GeoProjection(focus, 32), emptyList())

    @Test
    fun `水洞周边干格 全邻域坡度仍记岸线落差但通行闸门按陆地坡度放行`() {
        // 锁 `TerrainMap.kt:96`：passable 用陆地口径（跳过水面邻格），贴水干格不再被岸线落差误判成悬崖。
        val data = IntArray(32 * 32) { 128 }
        data[10 * 32 + 6] = 0
        val map = mapOf(data)
        val dry = Cell(6, 9)

        assertTrue("贴水干格本身不是水面", !map.isWater(dry))
        // 旧全邻域口径仍把水面邻格高差计入：48.35° ≥ 45°，证明 slopeDeg 口径未被改写。
        assertTrue("全邻域 slopeDeg 仍计入水面邻格高差（≥45°）", map.slopeDeg(dry) >= 45.0)
        // 新陆地口径下该干格陆内邻格高差为 0，应放行。
        assertTrue("陆地口径下贴水干格 REGULAR 应可通行", map.passable(dry, ArmyType.REGULAR))
    }

    @Test
    fun `纯陆地陡坡不靠水面邻格 仍被通行闸门堵死`() {
        // 防放宽过头：陡坡由陆内高差造成时，跳过水面邻格不影响其被堵。
        val data = IntArray(32 * 32) { 128 }
        data[10 * 32 + 10] = 255
        data[11 * 32 + 10] = 1
        val map = mapOf(data)
        val steep = Cell(10, 10)

        assertTrue("陡坡格本身不是水面", !map.isWater(steep))
        assertTrue("陆内高差形成的坡度应 >45°", map.slopeDeg(steep) > 45.0)
        assertFalse("纯陆地陡坡 REGULAR 仍不可通行", map.passable(steep, ArmyType.REGULAR))
    }

    @Test
    fun `渡口检测仍走全邻域 slopeDeg 口径 宽水带不新生渡口`() {
        // 锁 `TerrainMap.kt:169`：detectFords 仍调全邻域 slopeDeg，本单未把它切到陆地口径。
        val data = IntArray(32 * 32) { i -> if (i / 32 in 14..17) 0 else 128 }
        val map = mapOf(data)
        val edge = Cell(5, 13)

        assertTrue("岸线干格全邻域坡度仍计入水面邻格", map.slopeDeg(edge) > 0.0)
        // 水带边缘格即便湿/干邻格都 ≥2，岸线坡度 48.35° 仍超渡口坡度上限 12°；改动不得凭空造出渡口。
        assertTrue("宽水带两侧不构成渡口", map.fordCells.isEmpty())
    }

    @Test
    fun `目标格不可通行时 find 兜底钳到最近可通行格 不踏水面`() {
        // 边界兜底：nearestPassable 为私有（Pathfinder.kt:228），经公共 find 入口验证落点钳制。
        val data = IntArray(32 * 32) { 128 }
        data[10 * 32 + 7] = 0
        val map = mapOf(data)
        val target = Cell(7, 10)

        assertTrue("目标格为非渡口水面", map.isWater(target) && !map.isFord(target))
        val finder = Pathfinder(map, "clear")
        val path = finder.find(Cell(5, 10), target, ArmyType.REGULAR)

        assertTrue("兜底路径必须非空", path.isNotEmpty())
        assertNotEquals("落点应被钳离原水面目标格", target, path.last())
        assertTrue("兜底末格必须可通行", map.passable(path.last(), ArmyType.REGULAR))
        assertFalse("兜底末格不得踏上非渡口水面", map.isWater(path.last()))
    }
}
