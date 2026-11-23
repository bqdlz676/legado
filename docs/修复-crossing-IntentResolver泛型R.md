# 修复-crossing `IntentResolver` 泛型 `R` 推不出（CI run `45178766005`）

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`，HEAD `6509100`。
- 改动文件域（仅此一个）：`app/src/main/java/io/legado/app/ui/map/crossing/engine/IntentResolver.kt`。
- 任务范围：**只修** `farthestFromEnemies` 一个函数的泛型推断失败；未改语义、未重构、未换实现。
- 红线遵守：未跑本地 gradle / kotlinc 编译；未做任何 git 写操作；未碰 `CombatResolver` / `MoraleSystem` / `VictoryChecker` / `CrossingShell` / `model` / `terrain`；他人未提交文件 `shell/CrossingRasterBridge.kt`、`terrain/RasterDecoder.kt` 未删改。

## 一、报错原文（CI run `45178766005`）

```
IntentResolver.kt:131:24 Cannot infer type for type parameter 'R'. Specify it explicitly.
IntentResolver.kt:131:36 Cannot infer type for type parameter 'R'. Specify it explicitly.
IntentResolver.kt:132:13 Return type mismatch: expected
    'Comparable<uninferred R (of fun <T, R : Comparable<R>> Iterable<T>.maxByOrNull)>',
    actual 'Number & Comparable<CapturedType(*)>'
```

## 二、病灶代码（改动前，行号以现文件为准）

```kotlin
/** 溃逃落点：四邻中离所有敌军最近距离最远的一格。 */
private fun farthestFromEnemies(from: Cell, enemies: List<Corps>, map: TerrainMap): Cell? {
    val options = map.neighbors8(from).filter { map.passable(it, io.legado.app.ui.map.crossing.model.ArmyType.REGULAR) }
    if (options.isEmpty()) return null
    return options.maxByOrNull { c ->
        if (enemies.isEmpty()) Double.MAX_VALUE
        else enemies.minOf { octile(c, it.position) }
    }
}
```

## 三、为什么 `R` 推不出

1. `octile(a: Cell, b: Cell): Int`（本文件第 140–144 行，返回 `max(dc, dr) + min(dc, dr)`，**Int**）。
2. 非空分支 `enemies.minOf { octile(c, it.position) }`：stdlib 签名为
   `inline fun <T, R : Comparable<R>> Iterable<T>.minOf(selector: (T) -> R): R`。
   selector 返回 `Int`，故此处 `R = Int`，整个 `minOf` 结果是 **Int**。
3. 空敌分支 `Double.MAX_VALUE` 是 **Double**。
4. Kotlin 对 `if` 两分支取公共超类型：`Int` 与 `Double` 之间推不出单一具体类型，退化为捕获类型
   `Number & Comparable<CapturedType(*)>`。
5. `maxByOrNull` 签名为
   `inline fun <T, R : Comparable<R>> Iterable<T>.maxByOrNull(selector: (T) -> R): T?`，
   其约束是 `R : Comparable<R>`（自比型）。捕获交集 `Number & Comparable<CapturedType(*)>`
   无法满足这个自引用 `Comparable<R>` 约束，编译器既不能把它统一成 `Int` 也不能统一成 `Double`，
   于是类型形参 `R` 停留在未推断状态，报出 131:24 / 131:36 的 `Cannot infer type for type parameter 'R'`，
   132:13 再报期望 `Comparable<uninferred R>`、实参是 `Number & Comparable<CapturedType(*)>` 的不匹配。

一句话：**lambda 两个分支一个是 Double、一个是 Int，`maxByOrNull` 的自比泛型 `R` 找不到可落地的具体类型。**

## 四、修复方案与 after 代码

把非空分支结果显式收敛为 `Double`（`Int.toDouble()` 宽化），让 lambda 返回类型就是 `Double`，
`maxByOrNull` 的 `R` 自然推断为 `Double`。未给 `maxByOrNull` 硬塞显式类型实参，未改成 `sortedBy` 等其它实现。

```kotlin
/** 溃逃落点：四邻中离所有敌军最近距离最远的一格。 */
private fun farthestFromEnemies(from: Cell, enemies: List<Corps>, map: TerrainMap): Cell? {
    val options = map.neighbors8(from).filter { map.passable(it, io.legado.app.ui.map.crossing.model.ArmyType.REGULAR) }
    if (options.isEmpty()) return null
    return options.maxByOrNull { c ->
        if (enemies.isEmpty()) Double.MAX_VALUE
        else enemies.minOf { octile(c, it.position) }.toDouble()
    }
}
```

- 改动行：仅第 133 行，在 `enemies.minOf { octile(c, it.position) }` 后追加 `.toDouble()`；第 131、132 行未变。
- `minOf` 而非 `minOfOrNull` 保持原样：`else` 分支只在 `enemies` 非空时到达，不存在空集合抛异常的路径。

## 五、语义等价论证

1. **排序次序不变**：`octile` 返回的是非负有限 Int（行列差合成），`Int → Double` 是保序宽化，
   任意两个距离 `x < y` 经转换后仍为 `x.toDouble() < y.toDouble()`；最近距离的大小排名与取最远落点完全一致。
2. **空敌分支行为不变**：`enemies` 为空时所有候选格评分仍是 `Double.MAX_VALUE`，彼此并列；
   `maxByOrNull` 取首个最大值，与改动前一致。
3. **非空分支含义不变**：对每格取「到所有敌军的最近 octile 距离」（`minOf`），再在候选格中取该距离最大者，
   即注释所述「四邻中离所有敌军最近距离最远的一格」；`octile` 为对角距离，越大越远，方向未翻转。
4. **候选集与早退不变**：`neighbors8`、`passable` 过滤、`options.isEmpty() → null` 均未改动；
   返回值仍是 `Cell?`，调用方 `resolve` 中 `Intent.ROUT` 的落点取值不受影响。
5. **无溢出/精度新增风险**：地图格子规模下的 Int 距离在 Double 中可精确表示（远在 2^53 以内），不存在精度错排。

## 六、grep 证据（改动后原文）

```
$ grep -n "maxByOrNull\|Double.MAX_VALUE\|toDouble()" IntentResolver.kt
131:        return options.maxByOrNull { c ->
132:            if (enemies.isEmpty()) Double.MAX_VALUE
133:            else enemies.minOf { octile(c, it.position) }.toDouble()
```

人工核对两分支返回类型：

- 第 132 行：`Double.MAX_VALUE` —— `Double`。
- 第 133 行：`minOf { ... }` 结果 `Int`，`.toDouble()` —— `Double`。
- 两分支同为 `Double`，lambda 返回类型 `Double`，`maxByOrNull` 形参 `R = Double`，满足 `R : Comparable<R>`。

## 七、未验证项

- **未本地编译，需 CI confirm**：按红线未运行本地 gradle / kotlinc，类型推断是否在 CI 工具链下彻底消除上述三条报错，以 CI run 结果为准。
- 未跑单元测试 / 未在运行时演练溃逃路径；排序等价性为静态推导，未做实际对战回归。
- 仓库中其它既有的未提交改动（他人工作区文件）本轮未触碰、未核对，不在本修复范围内。
