# 修复-crossing Pathfinder 调用签名不匹配（CI run `45171948572`）

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`；报错文件 `app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt`。
- 任务范围：**只修** `Pathfinder` 第 30 行一处调用签名不匹配；未改寻路算法、未改 A* 逻辑、未动其它函数，未碰 shell / model / terrain / 其它域。
- 红线遵守：未跑本地 gradle / kotlinc 编译；未做任何 git 写操作（提交由总控收口）。

## 一、报错原文（CI run `45171948572`）

```
engine/Pathfinder.kt:30:20 No value passed for parameter 'enemyPositions'
engine/Pathfinder.kt:30:53 No parameter with name 'visibilityPenalty' found
engine/Pathfinder.kt:30:73 Cannot infer type for type parameter 'T'
```

三条报错同指第 30 行 `find()` 内对私有 `astar(...)` 的那一次调用。

## 二、被调函数的真实签名

`Pathfinder.kt:46-52`（定义处，工作树现状，与 HEAD 一致，未改动）：

```kotlin
private fun astar(
    from: Cell,
    to: Cell,
    type: ArmyType,
    avoid: Set<Cell>,
    enemyPositions: List<Cell>,
): List<Cell>
```

要点：

- 形参共 5 个，第 5 个名为 `enemyPositions: List<Cell>`，**不存在** `visibilityPenalty` 形参；
- `astar` **不是泛型函数**，没有类型参数 `<T>`；
- 视线惩罚倍数是伴生常量 `VISIBILITY_PENALTY: Double = 1.8`（第 184 行），在私有 `stepCost(...)` 内部读取使用，从来不是函数形参；
- 实参类型：`Cell`（`model/CampaignModels.kt:25` `data class Cell(val col: Int, val row: Int)`）、`ArmyType`（`model/Enums.kt:47` 枚举）、`TerrainMap`、`Set<Cell>`、`List<Cell>`。

## 三、改法抉择：**(a) 调用处补齐实参，删除不存在的具名实参**

### 3.1 依据

1. **定义不缺参数**：`astar` 本就声明了 `enemyPositions: List<Cell>`，报错 1「No value passed for parameter 'enemyPositions'」是调用方少传了这个位置实参，不是定义处漏形参，故不走 (b)。
2. **`visibilityPenalty` 形参本就不该存在**：报错 2「No parameter with name 'visibilityPenalty' found」说明调用方把第 5 实参错标成了一个不存在的名字；视线加价倍数走伴生常量 `VISIBILITY_PENALTY`，语义上不要求把倍数做成可传形参，因此**不在定义处新增** `visibilityPenalty` 形参（也避免给调用方强加一个本不需要的入口）。
3. **泛型 T 报错是级联产物**：报错 3「Cannot infer type for type parameter 'T'」并非 `astar` 真有类型参数——定义处已确认非泛型；它是具名实参 `visibilityPenalty = emptyList()` 解析失败后，编译器回退推断时抛出的连带错误。调用恢复为位置实参后，`emptyList()` 由形参类型 `List<Cell>` 做目标类型推导，直接推出 `List<Cell>`，**不需要**显式类型实参，更不用 `Any` 糊住。
4. **语义保持**：`find()` 是普通行军入口，调用方上下文里没有敌军坐标，第 5 位传空列表 `emptyList()`；`stepCost` 中 `enemyPositions.any { enemy -> map.visible(enemy, next) }` 对空列表恒为 `false`，不触发视线加价。侧翼威胁只由 `flankPath(...)` 传入真实敌军坐标（第 40 行既有写法，未改）。

### 3.2 改动前后逐行对照

文件：`app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt`

| 行号 | 改动前（HEAD / CI 报错版本） | 改动后（工作树现状） |
|---|---|---|
| 30 | `val path = astar(from, target, type, avoid, visibilityPenalty = emptyList())` | `val path = astar(from, target, type, avoid, emptyList())` |

改动说明（仅这一行）：

- 删除非法具名实参 `visibilityPenalty =`；
- 第 5 位改回位置实参 `emptyList()`，对应形参 `enemyPositions: List<Cell>`；
- 其余 4 个位置实参 `from, target, type, avoid` 顺序、类型均未动；A* 循环、代价函数、兜底折线等算法逻辑一行未改。

> 备注：本轮核对时工作树该行已是改后形态（相对 HEAD 的未暂存修改）；经 `Read` 与 `grep` 复核字节内容确已落盘，未再二次改写，也未触碰同文件其它行。

## 四、`Pathfinder` 对外签名清单（参数名 + 类型 + 默认值）

### 4.1 构造函数（第 21–24 行）

```kotlin
class Pathfinder(
    private val map: TerrainMap,
    private val weatherCondition: String?,
)
```

| 形参 | 类型 | 默认值 |
|---|---|---|
| `map` | `TerrainMap` | 无（必传） |
| `weatherCondition` | `String?` | 无（必传，可空） |

### 4.2 公开成员函数

| 函数 | 完整签名 |
|---|---|
| `find` | `fun find(from: Cell, to: Cell, type: ArmyType, avoid: Set<Cell> = emptySet()): List<Cell>` |
| `flankPath` | `fun flankPath(from: Cell, to: Cell, type: ArmyType, enemyPositions: List<Cell>): List<Cell>` |
| `cellDist` | `fun cellDist(a: Cell, b: Cell): Double` |

各形参明细：

**`find`**

| 形参 | 类型 | 默认值 |
|---|---|---|
| `from` | `Cell` | 无 |
| `to` | `Cell` | 无 |
| `type` | `ArmyType` | 无 |
| `avoid` | `Set<Cell>` | `emptySet()` |

**`flankPath`**

| 形参 | 类型 | 默认值 |
|---|---|---|
| `from` | `Cell` | 无 |
| `to` | `Cell` | 无 |
| `type` | `ArmyType` | 无 |
| `enemyPositions` | `List<Cell>` | 无 |

**`cellDist`**

| 形参 | 类型 | 默认值 |
|---|---|---|
| `a` | `Cell` | 无 |
| `b` | `Cell` | 无 |

### 4.3 私有成员（不对外，列出供完整性核对，本轮未改）

| 函数 | 签名 |
|---|---|
| `astar` | `private fun astar(from: Cell, to: Cell, type: ArmyType, avoid: Set<Cell>, enemyPositions: List<Cell>): List<Cell>` |
| `stepCost` | `private fun stepCost(from: Cell, next: Cell, type: ArmyType, enemyPositions: List<Cell>): Double` |
| `heuristic` | `private fun heuristic(a: Cell, b: Cell): Double` |
| `nearestPassable` | `private fun nearestPassable(target: Cell, type: ArmyType): Cell?` |
| `straightPath` | `private fun straightPath(from: Cell, to: Cell, type: ArmyType): List<Cell>` |
| `reconstruct` | `private fun reconstruct(cameFrom: Map<Long, Long>, endKey: Long, n: Int): List<Cell>` |
| `encode` | `private fun encode(c: Cell, n: Int): Long` |
| `decode` | `private fun decode(key: Long, n: Int): Cell` |
| `abs` | `private fun abs(v: Int): Int` |

## 五、调用方 ↔ 定义一致性核对

### 5.1 主源集调用点（`engine/CampaignController.kt`）

| 行号 | 调用 | 命中定义 | 一致性 |
|---|---|---|---|
| 651 | `Pathfinder(map, definition.weather?.condition)` | 构造函数 `(TerrainMap, String?)`；`weather?.condition` 为 `String?` | 一致 |
| 305 | `pathfinder.flankPath(corps.position, target, corps.type, knownEnemyCells)` | `flankPath(Cell, Cell, ArmyType, List<Cell>)`；`knownEnemyCells = corps.lastKnown.values.map { projection.toCell(it) }` 为 `List<Cell>` | 一致 |
| 307 | `pathfinder.find(corps.position, target, corps.type)` | `find(Cell, Cell, ArmyType, Set<Cell> = emptySet())`；`avoid` 取默认值 | 一致 |

### 5.2 测试源集调用点（只读核对，未改）

| 文件:行 | 调用 | 一致性 |
|---|---|---|
| `PathfinderTest.kt:38` | `Pathfinder(plainMap(), "clear")` | 一致 |
| `PathfinderTest.kt:39 / 48 / 69` | `finder.find(Cell, Cell, ArmyType.REGULAR)` | 一致（`avoid` 默认） |
| `PathfinderTest.kt:60` | `finder.flankPath(Cell, Cell, ArmyType.REGULAR, listOf(enemy))` | 一致（`List<Cell>`） |
| `CrossingBenchTest.kt:58` | `Pathfinder(controller.map, "clear")` | 一致 |
| `CrossingBenchTest.kt:59 / 63` | `finder.find(Cell, Cell, ArmyType.REGULAR)` | 一致 |

### 5.3 同包内部调用点（`Pathfinder.kt` 内）

| 行号 | 调用 | 命中定义 | 一致性 |
|---|---|---|---|
| 30 | `astar(from, target, type, avoid, emptyList())` | `astar(Cell, Cell, ArmyType, Set<Cell>, List<Cell>)`，5 个位置实参顺序、类型一一对应 | 一致 |
| 40 | `astar(from, target, type, emptySet(), enemyPositions)` | 同上 | 一致 |
| 74 | `stepCost(cur, next, type, enemyPositions)` | `stepCost(Cell, Cell, ArmyType, List<Cell>)` | 一致 |

## 六、grep 证据

门禁命令 1：全 `app/src` 检索 `visibilityPenalty`，应为零命中（定义处也不存在该形参，惩罚倍数以常量 `VISIBILITY_PENALTY` 命名，属另一标识符）。

```
$ grep -rn "visibilityPenalty" app/src
（无命中，exit=1）
```

门禁命令 2：现存视线惩罚常量（大写 `VISIBILITY_PENALTY`，伴生对象内，非函数形参）。

```
$ grep -n "VISIBILITY_PENALTY" app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt
102:            cost *= VISIBILITY_PENALTY
184:        const val VISIBILITY_PENALTY: Double = 1.8
```

门禁命令 3：第 30 行改后落盘内容复核。

```
$ sed -n '30p' app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt
        val path = astar(from, target, type, avoid, emptyList())
```

门禁命令 4：`astar` 定义与全部调用点全量核对（仅一处定义，两处内部调用）。

```
$ grep -n "fun astar\|astar(" app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt
30:        val path = astar(from, target, type, avoid, emptyList())
40:        val path = astar(from, target, type, emptySet(), enemyPositions)
46:    private fun astar(
```

## 七、未验证项（如实声明）

1. **未编译，需 CI**：按红线未在本地执行 gradle / kotlinc；本轮仅做静态签名核对（形参名 / 类型 / 默认值 / 位置顺序 / 调用方实参类型比对）。`compileAppDebugKotlin` 与 `compileAppDebugUnitTestKotlin` 是否转绿，需总控推 CI 复跑确认。
2. 单测运行期行为（普通寻路、侧翼绕视线、深水落点钳制、bench 阈值）本轮未执行，沿用既有测试断言，未在本轮重跑。
3. 第三条「Cannot infer type for type parameter 'T'」判定为具名实参解析失败的级联报错；因未允许本地编译，级联消除这一点同样待 CI 编译复核。
4. 同包调用方 `CampaignController.kt` 仅做签名一致性只读核对，未改动；若 CI 后续报出该文件内其它问题，属另立工单范围。
