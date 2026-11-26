> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）
> 机械改动：纯编译错修正——仅把 standalone `object` 内的 `companion` 成员原地展开到 `object` 顶层（3 个 engine 文件），本轮另把报告内证据锚点由省略号路径补全为仓内全路径（锚点规范化，不改结论）；无规格语义变更（2026-11-26 补记）。
> 证据三件套留档：本报告规则上线前交付，本轮只补全证据锚点路径，不回填 E1/E2/E3 —— 按机制-验收证据工作台口径，旧结论不回填伪装成新证据；原证据段保留备查（2026-11-26）。

# 修复-crossing `object` 内嵌套 `companion object`（CI run `45171948572` / `d301fbd`）

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`。
- 改动文件域（仅此 3 个）：
  - `app/src/main/java/io/legado/app/ui/map/crossing/engine/CombatResolver.kt`
  - `app/src/main/java/io/legado/app/ui/map/crossing/engine/MoraleSystem.kt`
  - `app/src/main/java/io/legado/app/ui/map/crossing/engine/IntentResolver.kt`
- 任务范围：**只修** Kotlin 禁止在 standalone `object` 内再声明 `companion object` 这一个编译错；把 `companion` 里的成员**原地展开到 `object` 顶层**。未重构、未改数据结构、未改算法、未改调用方、未新增 `@JvmStatic`。
- 红线遵守：未跑本地 gradle / kotlinc 编译；未做任何 git 写操作（提交由总控收口）；未碰 `CrossingShell.kt`、`model/`、`terrain/`；`shell/CrossingRasterBridge.kt`、`terrain/RasterDecoder.kt`（他人未提交新文件）未删改。

## 一、报错原文（CI run `45171948572`，日志副本 `/tmp/jd30.log`）

```
e: app/src/main/java/io/legado/app/ui/map/crossing/engine/CombatResolver.kt:50:5  Modifier 'companion' is not applicable inside 'standalone object'.
e: app/src/main/java/io/legado/app/ui/map/crossing/engine/MoraleSystem.kt:57:5     Modifier 'companion' is not applicable inside 'standalone object'.
e: app/src/main/java/io/legado/app/ui/map/crossing/engine/IntentResolver.kt:146:5 Modifier 'companion' is not applicable inside 'standalone object'.
```

根因：Kotlin 中 standalone `object X { … }` 本身已是单例，不能在其内部再声明 `companion object`（companion 是给普通 `class` / `data class` / `enum class` 挂静态伴生用的）。三条报错行均为 `companion object {` 声明本身。

> 另：`IntentResolver.kt:131:24`、`131:36`、`132:13` 的泛型 `R` 推断错（`maxByOrNull` 返回 `Double` 与 `Number & Comparable<…>` 不匹配）是**另一个问题**，另有代理处理，本轮**未碰**（127–135 行 `farthestFromEnemies` 方法体原样保留）。

## 二、逐行 before / after

### 2.1 `CombatResolver.kt`（60 → 58 行，净 -2）

外层：`object CombatResolver { … }`（standalone object，本身即单例）。

**Before（CI 报错版，L50–L59）**

```kotlin
    companion object {
        /** 伏击方伤害倍率（§三：伏击中伏击方优先判定、伤害 +2x）。 */
        const val AMBUSH_MULTIPLIER: Double = 2.0

        /** 制高点防御倍率（§三：一方在制高点 → 防御 +1.5x）。 */
        const val HIGH_GROUND_MULTIPLIER: Double = 1.5

        /** 单场遭遇基础损耗率（占双方兵力均值的比例）。 */
        const val CASUALTY_RATE: Double = 0.4
    }
}
```

**After（L50–L58）**

```kotlin
    /** 伏击方伤害倍率（§三：伏击中伏击方优先判定、伤害 +2x）。 */
    const val AMBUSH_MULTIPLIER: Double = 2.0

    /** 制高点防御倍率（§三：一方在制高点 → 防御 +1.5x）。 */
    const val HIGH_GROUND_MULTIPLIER: Double = 1.5

    /** 单场遭遇基础损耗率（占双方兵力均值的比例）。 */
    const val CASUALTY_RATE: Double = 0.4
}
```

改动：删除 `companion object {` 开括号 1 行 + 闭括号 1 行；3 条 `const val` 从 8 空格解缩进为 4 空格；注释同步解缩进；类型（`Double`）、值（`2.0` / `1.5`）、可见性（public 默认）全部不变。

### 2.2 `MoraleSystem.kt`（70 → 68 行，净 -2）

外层：`object MoraleSystem { … }`（standalone object）。

**Before（CI 报错版，L57–L69）**

```kotlin
    companion object {
        /** 疲劳上限（0..5）。 */
        const val FATIGUE_MAX: Double = 5.0

        /** 疲劳超过该值 → 士气降一级。 */
        const val FATIGUE_MORALE_DROP: Double = 4.0

        /** 断粮超过该连续回合数 → 疲劳加速。 */
        const val SUPPLY_CUT_TURNS: Int = 3

        /** 断粮每回合追加疲劳。 */
        const val SUPPLY_CUT_FATIGUE_ADD: Double = 0.5
    }
}
```

**After（L57–L68）**

```kotlin
    /** 疲劳上限（0..5）。 */
    const val FATIGUE_MAX: Double = 5.0

    /** 疲劳超过该值 → 士气降一级。 */
    const val FATIGUE_MORALE_DROP: Double = 4.0

    /** 断粮超过该连续回合数 → 疲劳加速。 */
    const val SUPPLY_CUT_TURNS: Int = 3

    /** 断粮每回合追加疲劳。 */
    const val SUPPLY_CUT_FATIGUE_ADD: Double = 0.5
}
```

改动：移除 companion 开闭括号 2 行；4 条 `const val` 解缩进；`Double` / `Int` 类型与值不变；注释不变。

### 2.3 `IntentResolver.kt`（168 → 166 行，净 -2）

外层：`object IntentResolver { … }`（standalone object）。

**Before（CI 报错版，L146–L167）**

```kotlin
    companion object {
        /** 相邻判定半径（格）。 */
        const val ADJACENT_CELLS: Int = 1

        /** 设伏：敌军进入该距离（格）内才触发。 */
        const val AMBUSH_ENEMY_RANGE: Int = 5

        /** 断粮超过该连续回合数 → 撤退。 */
        const val SUPPLY_CUT_TURNS: Int = 3

        /** 制高点据守：敌军该距离（格）内依托防御。 */
        const val HOLD_HIGH_GROUND_RANGE: Int = 3

        /** 兵力优势倍数：达到该倍数主动进军。 */
        const val STRENGTH_SUPERIORITY: Double = 3.0

        /** 抢占关隘：敌军超过该距离（格）才考虑夺隘。 */
        const val SEIZE_ENEMY_RANGE: Int = 5

        /** 被包围所需受压方向数。 */
        const val SURROUND_DIRS: Int = 3
    }
}
```

**After（L146–L166）**

```kotlin
    /** 相邻判定半径（格）。 */
    const val ADJACENT_CELLS: Int = 1

    /** 设伏：敌军进入该距离（格）内才触发。 */
    const val AMBUSH_ENEMY_RANGE: Int = 5

    /** 断粮超过该连续回合数 → 撤退。 */
    const val SUPPLY_CUT_TURNS: Int = 3

    /** 制高点据守：敌军该距离（格）内依托防御。 */
    const val HOLD_HIGH_GROUND_RANGE: Int = 3

    /** 兵力优势倍数：达到该倍数主动进军。 */
    const val STRENGTH_SUPERIORITY: Double = 3.0

    /** 抢占关隘：敌军超过该距离（格）才考虑夺隘。 */
    const val SEIZE_ENEMY_RANGE: Int = 5

    /** 被包围所需受压方向数。 */
    const val SURROUND_DIRS: Int = 3
}
```

改动：移除 companion 开闭括号 2 行；7 条 `const val` 解缩进；`Int` / `Double` 类型与值不变；注释不变。`farthestFromEnemies`（L127–L135）及其泛型 `R` 推断错**原样未动**。

## 三、改动行数汇总

| 文件 | Before 行数 | After 行数 | 净变化 | 展开常量数 |
|---|---:|---:|---:|---:|
| `engine/CombatResolver.kt` | 60 | 58 | -2 | 3 |
| `engine/MoraleSystem.kt` | 70 | 68 | -2 | 4 |
| `engine/IntentResolver.kt` | 168 | 166 | -2 | 7 |
| **合计** | **298** | **292** | **-6** | **14** |

每个文件仅删除 `companion object {` 开括号 1 行 + 闭括号 1 行；常量与注释解缩进，无逻辑行增删。

## 四、grep 证据

### 4.1 门禁①：3 个目标文件 `companion object` 必须为 0 命中

命令：

```bash
grep -n "companion object" \
  app/src/main/java/io/legado/app/ui/map/crossing/engine/CombatResolver.kt \
  app/src/main/java/io/legado/app/ui/map/crossing/engine/MoraleSystem.kt \
  app/src/main/java/io/legado/app/ui/map/crossing/engine/IntentResolver.kt
```

原文输出（无 stdout 行；退出码 1 = 无匹配）：

```
$ echo $?
1
```

### 4.2 门禁②：`crossing/` 目录其余命中清单（为何不动）

命令：

```bash
grep -rn "companion object" app/src/main/java/io/legado/app/ui/map/crossing/
```

原文输出（14 条，按外层声明归类）：

```
app/.../crossing/engine/CampaignController.kt:442:    companion object {
app/src/main/java/io/legado/app/ui/map/crossing/engine/Pathfinder.kt:176:    companion object {
app/.../crossing/model/CampaignModels.kt:14:    companion object {
app/.../crossing/model/Enums.kt:34:    companion object {
app/.../crossing/model/Enums.kt:65:    companion object {
app/.../crossing/model/Enums.kt:94:    companion object {
app/.../crossing/model/Enums.kt:124:    companion object {
app/.../crossing/model/Enums.kt:148:    companion object {
app/.../crossing/model/Enums.kt:170:    companion object {
app/.../crossing/model/Enums.kt:189:    companion object {
app/.../crossing/model/Enums.kt:205:    companion object {
app/.../crossing/terrain/GeoProjection.kt:55:    companion object {
app/.../crossing/terrain/HeightRaster.kt:38:    companion object {
app/.../crossing/terrain/TerrainMap.kt:216:    companion object {
```

逐条外层声明核对（`grep -nE "^(object|class|data class|enum class) "`）与不动理由：

| 文件 : 行 | 外层声明 | 外层类型 | 为何不动 |
|---|---|---|---|
| `engine/CampaignController.kt:442` | `class CampaignController private constructor(`（L33） | 普通 `class` | `companion object` 在普通 class 内是 **Kotlin 合法用法**，CI 未报错；且不在本轮 3 文件域内 |
| `engine/Pathfinder.kt:176` | `class Pathfinder(`（L21） | 普通 `class` | 同上：class 内伴生合法；非本轮文件 |
| `model/CampaignModels.kt:14` | `data class Geo(val lat: Double, val lng: Double)`（L12） | `data class` | data class 内伴生合法；`model/` 为红线禁改域 |
| `model/Enums.kt:34` | `enum class Morale`（L12） | `enum class` | enum class 内伴生合法；`model/` 红线 |
| `model/Enums.kt:65` | `enum class ArmyType`（L47） | `enum class` | 同上 |
| `model/Enums.kt:94` | `enum class Intent`（L80） | `enum class` | 同上 |
| `model/Enums.kt:124` | `enum class EventAction`（L115） | `enum class` | 同上 |
| `model/Enums.kt:148` | `enum class VictoryType`（L139） | `enum class` | 同上 |
| `model/Enums.kt:170` | `enum class TerrainKind`（L163） | `enum class` | 同上 |
| `model/Enums.kt:189` | `enum class FactionRole`（L183） | `enum class` | 同上 |
| `model/Enums.kt:205` | `enum class Relation`（L200） | `enum class` | 同上 |
| `terrain/GeoProjection.kt:17` | `class GeoProjection(`（L17） | 普通 `class` | class 内伴生合法；`terrain/` 红线 |
| `terrain/HeightRaster.kt:38` | `data class HeightRaster(`（L15） | `data class` | data class 内伴生合法；`terrain/` 红线 |
| `terrain/TerrainMap.kt:21` | `class TerrainMap(`（L21） | 普通 `class` | class 内伴生合法；`terrain/` 红线 |

结论：本轮 3 个报错文件的外层都是 standalone `object`（单例），Kotlin 不允许其内再挂 companion；其余 14 条外层均为 `class` / `data class` / `enum class`，companion 是合法静态伴生位，CI 未报 `Modifier 'companion' is not applicable` 错，故不动。

### 4.3 其它门禁核对

- `@JvmStatic`：3 个目标文件 `grep -n "@JvmStatic"` 退出码 1（0 命中），本轮未新增、也未删除任何 `@JvmStatic`。
- 他人未提交新文件：`shell/CrossingRasterBridge.kt`、`terrain/RasterDecoder.kt` 保持 `??`（untracked）状态，未删改；两文件内 `grep "companion object"` 退出码 1。
- 括号配平（改后静态核对）：`CombatResolver` `{`=2 / `}`=2；`MoraleSystem` `{`=5 / `}`=5；`IntentResolver` `{`=35 / `}`=35。

## 五、等价性说明

1. **语义等价**：`object X { companion object { const val C: T = v } }` 展开为 `object X { const val C: T = v }`。standalone `object` 本身已是单例，其顶层成员天然是静态可见的；把常量从嵌套 companion 提到 object 顶层，不改变初始化时机（`const val` 为编译期常量）、值、类型与可见性。
2. **调用方不变**：外部写法 `X.C` 在改前解析为 `X` 的 companion 成员，改后解析为 `X` 自身的顶层成员；调用点语法完全一致，本轮未改任何调用方。3 个文件内的内部裸引用（如 `resolve` 体内直接用 `AMBUSH_MULTIPLIER`、`advance` 体内直接用 `FATIGUE_MAX`）改前解析 companion 作用域，改后解析 object 作用域，名字与可见性均不变。
3. **可见性不变**：14 条常量改前均无显式修饰符（public 默认），改后仍为 public 默认；3 个文件的 companion 内不存在 `private` / `protected` / `internal` 成员，无需调整可见性。
4. **未引入新约束**：未新增 `@JvmStatic`、未新增方法、未改 data class（`Outcome` / `TurnEffect` / `Decision`）签名、未改私有辅助函数（`surrounded` / `farthestFromEnemies` / `mapCellOf` / `octile`）。
5. **外部引用核对**：`grep -rn` 14 条常量名（`AMBUSH_MULTIPLIER` / `FATIGUE_MAX` / `ADJACENT_CELLS` 等）在 `app/src`（含测试源集）除 3 个目标文件本身外**无任何点引用**；`SUPPLY_CUT_TURNS` 在 `MoraleSystem` 与 `IntentResolver` 各有一条同名内部常量，亦无外部点引用。

## 六、红线遵守记录

- 文件域：仅 `engine/CombatResolver.kt`、`engine/MoraleSystem.kt`、`engine/IntentResolver.kt` 三个；`git status --short` 在这 3 个路径上为 `M`，`shell/`、`model/`、`terrain/` 无本轮改动。
- 未改数据结构、未改算法；data class 字段、方法体、私有辅助函数原样保留。
- 未碰 `CrossingShell.kt`、`model/`、`terrain/`；`shell/CrossingRasterBridge.kt`、`terrain/RasterDecoder.kt` 未删改。
- 未跑本地 gradle / kotlinc；未做任何 git 写操作（add / commit / push 均未执行）。

## 七、未验证项

- **未本地编译，需 CI 复跑 confirm**：本轮仅做静态 grep、括号配平与逐行对照核对，未在本地执行 gradle build / kotlinc；需 CI 重跑确认 `Modifier 'companion' is not applicable inside 'standalone object'` 三条报错消除。
- `IntentResolver.kt:131:24` / `131:36` / `132:13` 泛型 `R` 推断错属另一问题，本轮未碰、未验证，由对应代理处理。
- 调用方未做运行期回归：已 grep 确认无外部 `X.CONST` 点引用，但未跑测试 / 真机回归。
