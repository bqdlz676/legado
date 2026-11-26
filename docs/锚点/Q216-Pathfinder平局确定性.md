# 锚点卡 · Q-216 Pathfinder 平局确定性 / 坡度假悬崖收口

> 对象：`app/src/main/java/io/legado/app/ui/map/crossing/terrain/TerrainMap.kt`；回执报告 `docs/修复-Pathfinder平局不确定性.md`。对应派单 `docs/派单队列.md:238` Q-216。

## 一、事实锚点（逐字，含 文件:行）

- 失败用例 `PathfinderBoundaryTest.kt:104` `assertEquals("路径末格为目标格", target, first.last())`；两次相等断言在 `:102` `assertEquals("同一输入两次裁决应逐格一致（平局稳定）", first, second)`。
- 开放队列次级键已存在 `Pathfinder.kt:155` `val open = PriorityQueue<Node>(compareBy({ it.f }, { it.key }))`；编码 `:282` `private fun encode(c: Cell, n: Int): Long = c.row.toLong() * n + c.col`。
- 坡度改前口径 `TerrainMap.kt:46` 原 `fun slopeDeg(cell: Cell): Double {`；邻域循环 `:50` `for (n in neighbors4(cell)) {`。
- 通行闸门改后 `TerrainMap.kt:96` `val slope = landSlopeDeg(cell)`；陆地坡度 `:54` `private fun landSlopeDeg(cell: Cell): Double = slopeInternal(cell, skipWater = true)`；跳水面邻格 `:61` `if (skipWater && isWater(n)) continue`；渡口口径保留 `:169` `slopeDeg(cell) <= FORD_SLOPE_DEG`。

## 二、参数表

| 项 | 值/口径 | 出处 |
|---|---|---|
| 格边长 | `50*1000/(32/2)=3125.0m` | `GeoProjection.kt:26` |
| 128/0 高程 | `3513.725m` / `0.0m`；高差 `3513.725m` | `HeightRaster.kt:49` |
| 假悬崖坡度 | `atan(3513.725/3125.0)=48.35°` > REGULAR `45.0°` | `TerrainMap.kt:97-101` |
| 绕格编码 | 上绕 `(6,9)=294`，下绕 `(6,11)=358` | `Pathfinder.kt:282` |

## 三、结论锚点

- C1 红点是末格断言 `:104` 失败，不是 `:102` 两次不一致；Python 复刻两次路径相同。
- C2 根因：单格水洞被全邻域坡度算成 48.35° 假悬崖，贴水干格被误堵，`nearestPassable` 把落点钳到 `(7,9)`。
- C3 修复：通行闸门改用跳过水面邻格的 `landSlopeDeg`；渡口检测与代价侧仍用全邻域 `slopeDeg`。未编译，红绿以 CI Verify run `45783964800` 复跑为准。

## 四、回源

- 回执报告：`docs/修复-Pathfinder平局不确定性.md`（§三 根因推理链、§四 diff 与 grep 回证、§六 shard2 Top5、§七 未验证项）。
- 派单：`docs/派单队列.md:238` Q-216；台账 `docs/派单台账.md:243`。
