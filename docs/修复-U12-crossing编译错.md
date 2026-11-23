# 修复-U12-crossing 编译错（CI `Run all module unit tests and benches`，commit `22cefd1` / `819ce86`）

- 基线：main，HEAD `bd0cd36`；报错 CI job `137670907681`（run `45168303260`）/ `137670519235`（run `45168191471`）。
- 任务范围：仅 `app/src/main/java/io/legado/app/ui/map/crossing/` 与 `app/src/test/java/io/legado/app/ui/map/crossing/`；未触碰 sandbox / replay / bookshelf / search / sheet。
- 红线遵守：未跑本地 gradle / kotlinc 编译；未做任何 git 写操作（add / commit / push 一律未执行，提交由总控收口）；只读 `git log / show / diff`。

## 一、第一现场原文（CI 245 条 `e:` 错误中的唯一根因行）

```
e: file:///.../ui/map/crossing/model/Enums.kt:221:1 Syntax error: Unclosed comment.
```

下游级联（符号缺失导致，非独立错误）：`CampaignModels.kt`、`engine/*`、`terrain/*`、`shell/*` 中
`Unresolved reference 'Morale' / 'Intent' / 'ArmyType' / 'BattleStatus' / 'EventAction' / 'FactionRole' / 'Relation' / 'VictoryType'`。

## 二、根因

Kotlin 的块注释**支持嵌套**（与 Java 不同）：注释内再次出现的 `/*` 会把嵌套深度 +1，必须有对应的 `*/` 才能逐层闭合。

`Enums.kt` 顶部 KDoc（第 3 行 `/**` 开启，嵌套深度 1）第 7 行在反引号路径里写了文件通配符：

```
`assets/map/campaigns/*.json`
```

其中 `campaigns/` 与 `*.json` 相邻处的字符序列正是 `/*`，被词法器识别为**嵌套块注释开符**（深度 +1 → 2）。第 9 行的 `*/` 只闭合了这层嵌套，外层 KDoc 始终停在深度 1 未闭合；自第 11 行起的全部 `enum class` 源码都被吞进注释，不产出任何符号。编译器直到 EOF（第 221:1，文件尾）才报 `Unclosed comment`；下游 12 个引用文件因此全部 Unresolved reference。

修复方式：改写第 7 行注释措辞，去掉 glob 通配符写法，保留原注释语义（字段字符串与 `assets/map/campaigns/` 目录下既有 JSON 数据资产口径对齐）；**未删除任何注释内容**。

## 三、改动清单（行号为改后文件）

### 1. `app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt`

- **第 7 行（根因行，唯一改动行）**：
  - 改前：` * §四 胜利条件；字段字符串与 `assets/map/campaigns/*.json` 既有数据资产口径对齐。`
  - 改后：` * §四 胜利条件；字段字符串与 `assets/map/campaigns/` 目录下既有 JSON 数据资产口径对齐。`
- 其余代码、注释、`fromLabel` 兜底分支一律未动（见第六节复核表）。

### 未改动

- `crossing/` 主源集其余 16 个文件、测试源集全部 7 个文件：词法扫描配平，未改一字。
- 包外目录（sandbox / replay / bookshelf / search / sheet 等）：只读，未碰。

## 四、24 文件词法配平表（主源码 17 + 测试 7）

扫描器为自写 Python 词法分析：先剔除字符串字面量（含转义双引号、三引号原始串、字符字面量）与行注释 `//`，再按 Kotlin 嵌套规则统计块注释深度，并对代码区统计 `()` / `[]` / `{}` 配平。

| # | 文件（相对 `crossing/`） | `/*` | `*/` | 注释终值深度 | `()` | `[]` | `{}` | 结论 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| 1 | engine/CampaignController.kt | 14 | 14 | 0 | 0 | 0 | 0 | 配平 |
| 2 | engine/CampaignJsonParser.kt | 3 | 3 | 0 | 0 | 0 | 0 | 配平 |
| 3 | engine/CombatResolver.kt | 4 | 4 | 0 | 0 | 0 | 0 | 配平 |
| 4 | engine/DemoCampaigns.kt | 2 | 2 | 0 | 0 | 0 | 0 | 配平 |
| 5 | engine/IntentResolver.kt | 10 | 10 | 0 | 0 | 0 | 0 | 配平 |
| 6 | engine/MoraleSystem.kt | 6 | 6 | 0 | 0 | 0 | 0 | 配平 |
| 7 | engine/Pathfinder.kt | 11 | 11 | 0 | 0 | 0 | 0 | 配平 |
| 8 | engine/TriggerEngine.kt | 6 | 6 | 0 | 0 | 0 | 0 | 配平 |
| 9 | engine/VictoryChecker.kt | 3 | 3 | 0 | 0 | 0 | 0 | 配平 |
| 10 | model/CampaignModels.kt | 25 | 25 | 0 | 0 | 0 | 0 | 配平 |
| 11 | model/Enums.kt | 17 | 17 | 0 | 0 | 0 | 0 | 配平（修复前见下注） |
| 12 | shell/CrossingShell.kt | 4 | 4 | 0 | 0 | 0 | 0 | 配平 |
| 13 | shell/CrossingTokens.kt | 17 | 17 | 0 | 0 | 0 | 0 | 配平 |
| 14 | terrain/GeoProjection.kt | 5 | 5 | 0 | 0 | 0 | 0 | 配平 |
| 15 | terrain/HeightRaster.kt | 11 | 11 | 0 | 0 | 0 | 0 | 配平 |
| 16 | terrain/TerrainCost.kt | 9 | 9 | 0 | 0 | 0 | 0 | 配平 |
| 17 | terrain/TerrainMap.kt | 11 | 11 | 0 | 0 | 0 | 0 | 配平 |
| 18 | CrossingBenchTest.kt | 5 | 5 | 0 | 0 | 0 | 0 | 配平 |
| 19 | engine/CampaignControllerTest.kt | 1 | 1 | 0 | 0 | 0 | 0 | 配平 |
| 20 | engine/CampaignJsonParserTest.kt | 1 | 1 | 0 | 0 | 0 | 0 | 配平 |
| 21 | engine/PathfinderTest.kt | 2 | 2 | 0 | 0 | 0 | 0 | 配平 |
| 22 | engine/TriggerEngineTest.kt | 1 | 1 | 0 | 0 | 0 | 0 | 配平 |
| 23 | terrain/TerrainCostTest.kt | 1 | 1 | 0 | 0 | 0 | 0 | 配平 |
| 24 | terrain/TerrainMapTest.kt | 1 | 1 | 0 | 0 | 0 | 0 | 配平 |

- 修复前（HEAD `bd0cd36` blob）`Enums.kt`：`/*`=18、`*/`=17、终值深度 1、扫描结束状态仍为 `block`；事件序列第 3 行 `/*`（KDoc 开）、第 7 行 `/*`（通配符误开嵌套）、第 9 行 `*/`（仅闭合嵌套层），此后无闭合符。
- 修复后：24 文件全部 `depth=0`、括号配平、扫描结束状态 `code`，**全绿**。
- 同类风险穷举：全包检索非 KDoc 起始的 `/*` 序列，仅两处——`Enums.kt:7`（本次根因，已修）与 `CrossingShell.kt:73` 行内块注释 `/* 预留棋盘点选：点棋子展开意图面板 */`（自身成对闭合，扫描配平，无需改）。

## 五、`when` 穷尽性判定（含 CI 报错行 `CampaignController.kt:110:51`）

### 5.1 `CampaignController.kt:110` —— 判定：级联副产物，**非真实遗漏**

```kotlin
private fun visionRadius(def: ArmyDef): Int = when (def.type) {
    ArmyType.CAVALRY -> 6
    ArmyType.ELITE -> 5
    ArmyType.REGULAR -> 4
    ArmyType.MILITIA -> 3
}
```

- `ArmyType` 全部常量仅 4 个：`ELITE` / `REGULAR` / `MILITIA` / `CAVALRY`（`Enums.kt:47-51`），when 四分支一一覆盖，无遗漏。
- CI 报 `'when' expression must be exhaustive. Add an 'else' branch.` 的成因：`Enums.kt` 整文件被吞进注释后，`ArmyType` 符号 Unresolved，编译器无法把 `def.type` 识别为枚举类型，也就无法核验分支穷尽性，遂按错误类型报“非穷尽”。根因消除、符号恢复后该报错应自行消失；**不补 else**（补 else 会掩盖未来新增兵种时的穷尽性检查）。

### 5.2 全包其余枚举 `when` 静态比对（穷举复核）

| 位置 | subject 类型 | 已覆盖分支 | 兜底 | 穷尽性结论 |
|---|---|---|---|---|
| CampaignController.kt:110 | `ArmyType` | CAVALRY / ELITE / REGULAR / MILITIA（4/4） | 无 else | 穷尽（报错为级联） |
| CampaignController.kt:200 | `EventAction` | MORALE_CHANGE / SURRENDER / SUPPLY_CUT / SET_INTENT / SET_TARGET / STRENGTH_CHANGE / ROUT（7/7） | 无 else | 穷尽 |
| CampaignController.kt:266 | `Intent` | FLANK；AMBUSH, HOLD | `else` | 有兜底，成立 |
| VictoryChecker.kt:46 | `VictoryType` | ANNIHILATE / CAPTURE / DEFEND / ESCAPE / REACH_POINT / ROUT / COMPOUND（7/7） | 无 else | 穷尽 |
| Enums.kt:19 | `Morale`（downgrade） | HIGH / STEADY / WAVERING / BROKEN（4/4） | 无 else | 穷尽 |
| Enums.kt:27 | `Morale`（upgrade） | HIGH / STEADY / WAVERING / BROKEN（4/4） | 无 else | 穷尽 |
| Enums.kt:55 | `ArmyType`（speedBase） | CAVALRY / ELITE / REGULAR / MILITIA（4/4） | 无 else | 穷尽 |
| CrossingShell.kt:262 | `FactionRole` | OWN / ENEMY / ALLY / NEUTRAL（4/4） | 无 else | 穷尽 |
| CrossingShell.kt:385 | `TerrainKind` | WATER / PLAIN / HILL / MOUNTAIN / SNOW（5/5） | 无 else | 穷尽 |
| CrossingShell.kt:393 | `FactionRole` | OWN / ENEMY / ALLY / NEUTRAL（4/4） | 无 else | 穷尽 |
| CrossingShell.kt:400 | `FactionRole` | OWN / ENEMY / ALLY / NEUTRAL（4/4） | 无 else | 穷尽 |
| TerrainCost.kt:17 | `TerrainKind` | PLAIN / HILL / MOUNTAIN / SNOW / WATER（5/5） | 无 else | 穷尽 |
| TerrainMap.kt:86 | `ArmyType` | CAVALRY / MILITIA | `else` | 有兜底，成立 |

结论：全包不存在真实的 when 穷尽性遗漏；CI 那一条属符号缺失的级联报错。

## 六、`fromLabel` 兜底复核（规格要求：未知字符串不得抛异常）

修复仅触及第 7 行注释，解析代码未动；逐项复核兜底出口仍在：

| 枚举 | 解析入口（行号） | 未知 / 缺失兜底 | 结论 |
|---|---|---|---|
| `Morale` | `fromLabel`（36） | `else -> STEADY`（41） | 兜底保留 |
| `ArmyType` | `fromLabel`（66） | `else -> REGULAR`（71） | 兜底保留 |
| `Intent` | `fromLabel`（96） | `else -> HOLD`（109） | 兜底保留 |
| `EventAction` | `fromLabel`（125） | `else -> null`（133，可空返回） | 兜底保留 |
| `VictoryType` | `fromLabel`（149） | `else -> ANNIHILATE`（157） | 兜底保留 |
| `TerrainKind` | `fromMeters`（172） | `else -> PLAIN`（177） | 兜底保留 |
| `FactionRole` | `fromSideIds`（190） | `else -> ENEMY`（194） | 兜底保留 |
| `Relation` | `fromLabel`（206） | `else -> NEUTRAL`（209） | 兜底保留 |
| `BattleStatus` | 无字符串解析入口 | 纯运行态状态枚举，不涉及解析兜底 | 不适用 |

## 七、grep 证据

### 7.1 修复前（HEAD blob，只读 `git show`）

```
$ git show HEAD:.../model/Enums.kt | grep -o '/\*' | wc -l
18
$ git show HEAD:.../model/Enums.kt | grep -o '\*/' | wc -l
17
$ grep -n campaigns <HEAD blob>
7: * §四 胜利条件；字段字符串与 `assets/map/campaigns/*.json` 既有数据资产口径对齐。
```

### 7.2 修复后（工作区，24 文件标记计数；`grep -o` 逐文件）

```
14 14 engine/CampaignController.kt
 3  3 engine/CampaignJsonParser.kt
 4  4 engine/CombatResolver.kt
 2  2 engine/DemoCampaigns.kt
10 10 engine/IntentResolver.kt
 6  6 engine/MoraleSystem.kt
11 11 engine/Pathfinder.kt
 6  6 engine/TriggerEngine.kt
 3  3 engine/VictoryChecker.kt
25 25 model/CampaignModels.kt
17 17 model/Enums.kt
 4  4 shell/CrossingShell.kt
17 17 shell/CrossingTokens.kt
 5  5 terrain/GeoProjection.kt
11 11 terrain/HeightRaster.kt
 9  9 terrain/TerrainCost.kt
11 11 terrain/TerrainMap.kt
 5  5 CrossingBenchTest.kt
 1  1 engine/CampaignControllerTest.kt
 1  1 engine/CampaignJsonParserTest.kt
 2  2 engine/PathfinderTest.kt
 1  1 engine/TriggerEngineTest.kt
 1  1 terrain/TerrainCostTest.kt
 1  1 terrain/TerrainMapTest.kt
```

两列依次为 `/*` 数 / `*/` 数，逐文件相等；词法扫描器另验注释深度终值 0、三类括号配平、结束状态回到 `code`。

### 7.3 改动行回证

```
$ git diff -- app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt
@@ -4,7 +4,7 @@
- * §四 胜利条件；字段字符串与 `assets/map/campaigns/*.json` 既有数据资产口径对齐。
+ * §四 胜利条件；字段字符串与 `assets/map/campaigns/` 目录下既有 JSON 数据资产口径对齐。

$ git status --short app/src/main/java/io/legado/app/ui/map/crossing app/src/test/java/io/legado/app/ui/map/crossing
 M app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt
```

`crossing/` 包内仅此一个文件被修改。

### 7.4 下游引用符号存在性抽查

测试源集 import 的模型符号在主源集均可回查：`Geo`（CampaignModels.kt:12）、`Cell`（:25）、`TerrainFocus`（:28）、`TriggerDef`（:81）、`EventDef`（:90）、`RiverDef`（:103）、`Corps`（:141）；八个枚举定义于 `Enums.kt:12/47/80/115/139/163/183/200/215`。

## 八、未验证项（如实声明）

1. **编译仍未经 CI 复跑**：按红线未在本地执行 gradle / kotlinc；本次仅为静态词法扫描、枚举常量清单比对与符号存在性回查。245 条下游级联报错是否随根因消除而清零，须由总控推 CI 复跑 `Run all module unit tests and benches` 确认。
2. 下游文件中是否还藏有符号恢复后才会暴露的独立编译错（类型不匹配、重载歧义等），本轮无法静态穷尽数尽，待 CI 结果复核。
3. 单测 / bench 的运行期行为（推演回合、寻路、地形代价、JSON 解析、性能阈值）本轮未执行，沿用既有测试断言，未在本轮重跑。
4. `when` 穷尽性判定为静态比对枚举常量清单得出，未经编译器核验；`CampaignController.kt:110` 不补 else 的判断同样待 CI 编译确认。
