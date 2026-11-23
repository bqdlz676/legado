# 修复-B5 DEFEND 目标点失守判定收口

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`，`main`，HEAD `c3a2248`。
- 任务范围：**只做 B5**——`VictoryChecker` 的 DEFEND 分支从「只查有生力量」扩为「有生力量 + 据点归属 + 盟友身份」；`pointHolders` / `alliedSideIds` 两个形参去默认值、不留旧五参重载兼容层；盟友占点判定复用 B3 收口的友方谓词，不另写一套 sideId 比较。
- 规格判据：`docs/落文-规格切片/落文-原文-06-四渡赤水引擎.md:105`「`defend`：目标点/城未失守，撑过所有回合」；同节「胜利判定不是最后一回合才查——每回合都查，达到即结束」。
- 红线遵守：沙箱无 Android SDK / kotlinc，**未跑本地 gradle / kotlinc / assemble / test**；**未做任何 git 写操作**（提交由总控收口）；未碰 `terrain/**`、`shell/**`、`model/**`；未改 B3 落库的 `enemiesOf` / `alliesOf` / 情报双表与 B9 模糊半径；测试源集无 `assertTrue(true)`、无 `@Ignore`。

## 一、改动清单（文件:行 + before/after）

### 1.1 `engine/VictoryChecker.kt`

#### 1.1.1 签名：两个新形参去默认值（移除兼容层）

| 项 | 改动前（`75fd638` 遗留） | 改动后 |
|---|---|---|
| `check(...)` 形参 | `pointHolders: Map<String, String> = emptyMap()`、`alliedSideIds: Set<String> = emptySet()` | `pointHolders: Map<String, String>`、`alliedSideIds: Set<String>`（必传，无默认值） |
| `evaluate(...)` 形参（private） | 七参无默认值 | 不变，仍七参无默认值 |
| KDoc | 「默认空值仅为兼容测试源集五参旧形参表」 | 「两个形参为必传项：生产调用方与测试源集均须显式传入，不设默认值、不留旧五参重载兼容层」 |

形参名、顺序与生产 / 测试全部调用点逐字对齐；本单未新增第二重载，也未保留任何旧五参入口。

#### 1.1.2 DEFEND 分支：失守判定改走共享友方谓词（`VictoryChecker.kt:76-89`）

改动前（内联第二套 sideId 判敌口径）：

```kotlin
val holder = def.point?.let { pointHolders[pointKey(it)] }
// 被非盟友的敌方占据即失守；防御方己方或盟友占据均不算失守。
val lost = holder != null && holder != def.side && holder !in alliedSideIds
```

改动后：

```kotlin
val holder = def.point?.let { pointHolders[pointKey(it)] }
// 失守判定走 B3 收口的友方谓词：己方 / 盟友占据均不算失守，仅非友方占据判负；
// 不在本分支另写 sideId 相异判敌的第二套口径。
val lost = holder != null && !isFriendlySide(def.side, holder, alliedSideIds)
```

判定档位顺序不变：**失守 → 有生力量全灭 → 撑满回合判胜 → 继续推演**。时间边界沿用 `75fd638` 校正后的 `turn >= duration`（等号即「撑满全部回合」），其余六分支时间比较未动。

#### 1.1.3 新增共享纯谓词（`VictoryChecker.kt:148-154`）

```kotlin
/**
 * 阵营级友方判定（B3 收口；CampaignController 的私有 isFriendlySide 委托到本谓词）：
 * 同阵营或在 [allies] 盟友集合内即友；任一侧 sideId 为空一律非友。
 * 判定层与控制器共用本谓词，禁止在其它分支再按 sideId 相异自行判敌。
 */
internal fun isFriendlySide(side: String?, other: String?, allies: Set<String>): Boolean =
    side != null && other != null && (side == other || other in allies)
```

谓词为纯函数、不读运行态；盟友集合仍由控制器从 `diplomacy` 推导后传入。

### 1.2 `engine/CampaignController.kt`

#### 1.2.1 生产调用点复核（`:195-203`，实参未改）

```kotlin
val outcome = VictoryChecker.check(
    def = definition.victory,
    corps = corps,
    turn = turn,
    duration = definition.duration,
    captureHoldTurns = captureHold,
    pointHolders = buildPointHolders(),
    alliedSideIds = alliedSidesWith(definition.victory.side),
)
```

生产唯一调用点在 `75fd638` 已显式传两参；本单复核实参名 / 类型与新签名一致，未改推导函数 `buildPointHolders()`（`:507-512`）与 `alliedSidesWith()`（`:515-521`）。

#### 1.2.2 私有 `isFriendlySide` 委托到共享谓词（`:672-678`）

改动前（控制器内联实现，与判定层各一套）：

```kotlin
private fun isFriendlySide(side: String?, otherSide: String?): Boolean =
    side != null && otherSide != null &&
        (side == otherSide || otherSide in alliedSidesWith(side))
```

改动后：

```kotlin
private fun isFriendlySide(side: String?, otherSide: String?): Boolean =
    VictoryChecker.isFriendlySide(side, otherSide, alliedSidesWith(side))
```

- B3 收口的四个调用点（`:534`、`:539`、`:563`、`:664`）签名不变、行为不变，本单一行未动。
- 语义逐条等价：`side == null` → `alliedSidesWith(null)` 返回空集，谓词空值守卫返回 `false`；`otherSide == null` 同样 `false`；同阵营或盟友集合命中即友。

### 1.3 测试源集 `VictoryCheckerTest.kt`

- 13 个 `check(...)` 调用点（原 11 个 + 本单新增 2 个用例各 1 处）全部显式补齐 `pointHolders` / `alliedSideIds`，无一处走默认值、无一处五参位置调用。
- 新增 2 条 DEFEND 用例（见第四节 4.4 / 4.5）；既有用例断言口径未改。

## 二、DEFEND 判定表（本单改后口径）

| 据点占据方 `holder` | 防御方有生力量 | 回合进度 | 判定 | 文案 |
|---|---|---|---|---|
| 非友方（谓词判 `false`：非同阵营、不在盟友集合） | 尚存 | 任意 | **DEFEAT**（优先于全灭判定） | 据点已被敌方占据，防御失败 |
| 盟友（`alliedSideIds` 命中） | 尚存 | 未撑满 | ONGOING | 推演进行中 |
| 己方（`holder == def.side`） | 尚存 | 未撑满 | ONGOING | 推演进行中 |
| 无占据记录（表中无 key，或 `def.point == null`） | 尚存 | 未撑满 | ONGOING | 推演进行中 |
| 任意（含盟友 / 己方 / 无记录） | 全灭（无可战军队） | 任意 | **DEFEAT** | 防御方已被击溃 |
| 盟友 / 己方 / 无失守 | 尚存 | `turn >= duration` | **VICTORY** | 撑过全部 $duration 回合，防御成功 |
| 据点 key 未在表中（空表兜底） | 尚存 | 撑满 | **VICTORY** | 同上 |

## 三、grep 回证（真实输出）

### 3.1 签名无默认值、旧内联判敌表达式已消失

```
$ grep -n "emptyMap()\|emptySet()" VictoryChecker.kt
（无命中）

$ grep -n "holder != def.side\|holder !in" VictoryChecker.kt
（无命中，exit=1）

$ sed -n '34,42p' VictoryChecker.kt
    fun check(
        def: VictoryDef,
        corps: List<Corps>,
        turn: Int,
        duration: Int,
        captureHoldTurns: Map<String, Int>,
        pointHolders: Map<String, String>,
        alliedSideIds: Set<String>,
    ): Outcome {
```

### 3.2 全仓 `VictoryChecker.check(` 调用清点（1 生产 + 13 测试 = 14 处）

```
CampaignController.kt:195        生产唯一调用，显式传 pointHolders / alliedSideIds
VictoryCheckerTest.kt:75         F4 敌军占点判负
VictoryCheckerTest.kt:92         F4 盟友占据继续
VictoryCheckerTest.kt:107        F4 己方守住撑满判胜
VictoryCheckerTest.kt:123        F4 盟友协防撑满判胜（新增）
VictoryCheckerTest.kt:140        F4 非盟友第三方占点判负（新增）
VictoryCheckerTest.kt:156        F5 ROUT 未全溃继续
VictoryCheckerTest.kt:175        F5 ROUT 全 BROKEN 判胜
VictoryCheckerTest.kt:194        F5 投降 / 被歼不计入溃散
VictoryCheckerTest.kt:213        F2 盟友不计入 ROUT blockers
VictoryCheckerTest.kt:230        CAPTURE 占领回合判胜
VictoryCheckerTest.kt:251        COMPOUND OR 判胜
VictoryCheckerTest.kt:263        COMPOUND AND 判胜
VictoryCheckerTest.kt:276        COMPOUND AND 部分达成不 finished
```

测试源集 `pointHolders` / `alliedSideIds` / `captureHoldTurns` 各 13 处，与 13 个调用点一一对应；无 `emptyMap())` 五参位置调用残留。

### 3.3 共享谓词与委托落点

```
VictoryChecker.kt:82     val lost = holder != null && !isFriendlySide(def.side, holder, alliedSideIds)
VictoryChecker.kt:153    internal fun isFriendlySide(side: String?, other: String?, allies: Set<String>): Boolean =
CampaignController.kt:677 private fun isFriendlySide(side: String?, otherSide: String?): Boolean =
CampaignController.kt:678     VictoryChecker.isFriendlySide(side, otherSide, alliedSidesWith(side))
```

### 3.4 改动面（仅本单三个文件；parser / terrain / shell 为在飞他单产物，未触碰）

```
$ git diff --numstat -- app/src/main/java/io/legado/app/ui/map/crossing/engine/ app/src/test/java/io/legado/app/ui/map/crossing/engine/
3   3   CampaignController.kt
16  7   VictoryChecker.kt
92  6   VictoryCheckerTest.kt
```

## 四、DEFEND 单测逐条走查（5 条，≥3 条要求已满足）

### 4.1 `:71-86` 敌军占点判负（有生力量尚存不影响）

- 输入：`def.side = "a"`；防御方 `a1`（可战，strength 8.0）；`pointHolders = {pointKey → "b"}`；`alliedSideIds = emptySet()`；`turn=5, duration=30`。
- 期望：`DEFEAT`，reason 含「据点」。
- 路径：`holder = "b"` → `isFriendlySide("a", "b", {}) = false` → `lost = true` → 首分支 `DEFEAT`。

### 4.2 `:88-102` 盟友占据不算失守，防御方尚存即继续

- 输入：`holder = "c"`；`alliedSideIds = setOf("c")`；`turn=5`。
- 期望：`ONGOING`。
- 路径：`isFriendlySide("a", "c", {"c"}) = true` → `lost = false`；防御方非空；`5 >= 30` 不成立 → `Ongoing`。

### 4.3 `:104-117` 己方守住、撑满全部回合判胜

- 输入：`holder = "a"`；`turn=30, duration=30`；`alliedSideIds = emptySet()`。
- 期望：`VICTORY`。
- 路径：谓词 `true`（同阵营）→ 非失守；防御方非空；`30 >= 30` 成立 → `VICTORY`。

### 4.4 `:119-133` 盟友协防撑满回合，盟友占据算己方判胜（新增）

- 输入：`holder = "c"`；`alliedSideIds = setOf("c")`；`turn=30, duration=30`；防御方尚存。
- 期望：`VICTORY`。
- 路径：盟友谓词判友 → 非失守；撑满回合 → `VICTORY`。把守「盟友占点在胜利边界上同样算己方」，与 4.2 的中途 ONGOING 形成时间边界双覆盖。

### 4.5 `:135-150` 非盟友第三方占点判负，盟友集合不覆盖第三方（新增）

- 输入：`holder = "d"`；`alliedSideIds = setOf("c")`（`c` 是盟友但不是占据方）；`turn=5`。
- 期望：`DEFEAT`。
- 路径：`isFriendlySide("a", "d", {"c"}) = false` → `lost = true`。把守盟友集合不会外溢覆盖第三方阵营。

## 五、二阶三阶推演

### 5.1 形参传染面

1. **签名层**：`check`（公开）与 `evaluate`（私有）均为七参；`evaluate` 递归 COMPOUND 子条件时在 `:127` 同步透传两参，子 DEFEND 可独立判据点失守。
2. **调用层**：全仓生产调用仅 `CampaignController.step()` 一处；测试 13 处。本单移除默认值后，任何后续新增调用点都必须显式给齐两参，编译器会兜底拦截遗漏——不存在「忘传即静默按空表 / 空集兜底」的旧兼容路径。
3. **数据层**：未新增运行态字段；据点表每回合由 `corps` 实时推导，盟友表由 `diplomacy` 实时推导，不落入 `BranchSnapshot` 持久态（快照相关属 B7 范畴）。
4. **依赖方向**：控制器本就依赖 `VictoryChecker`（`:195` 调 `check`、`:689` 读 `REACH_RADIUS_KM`）；谓词收口到判定层未产生新的分层倒置。判定层仍是纯 JVM、不反向引用控制器。

### 5.2 与其他胜利条件的关系

- **ANNIHILATE / CAPTURE / ESCAPE / REACH_POINT**：接收两参但不消费，行为与改动前逐分支一致；本单未改这些分支的比较符、文案与阈值。
- **ROUT**：消费 `alliedSideIds` 排除盟友 blockers（B6 `da571c9` 口径）；其敌军过滤仍保留本分支自有的 sideId 比较表达式（见第九节交接），本单按红线未动。
- **COMPOUND**：两参随父条件透传全部子条件；OR 任一子条件达成即胜、AND 需全部达成的口径不变。
- **跨类型一致性风险**：若后续 ROUT / 情报层也要复用同一友方谓词，可直接调 `VictoryChecker.isFriendlySide`，无需再读控制器私有函数。

### 5.3 用户行为 / 复杂度 / 维护成本

- 玩家侧：敌军突入目标点即提前判负，与「守城」规格一致；盟友协防据点不会误判失守。
- 复杂度：判定层仍为纯函数、不改状态；新增一个 internal 纯谓词，未加厚抽象层。
- 维护成本：友方规则只剩一处实现（同阵营 ∪ 盟友集合 + 空值守卫）；据点 key 格式仍在判定层、控制器、测试三处同源 `%.4f` 口径，后续改 key 规则需三处同步（既有注释已标注）。

## 六、反例与边界（先假设方案错误，逐条找反例）

1. **据点表为空 / 无占据记录**：`holder = null` → `lost = false`，回落「有生力量 + 回合」原路径；不崩、不误判失守。空表语义是「未观测到占据」而非「己方守住」，生产调用方每回合实推，不会长期空转；测试侧已显式传空表。
2. **`def.point == null`**（内置演示战役存在无 point 的 defend）：`holder = null`，失守判定跳过，走全灭 / 撑满路径；不抛 NPE。
3. **`def.side` 为 null（畸形定义）**：谓词空值守卫返回 `false`；若此时存在占据方则按失守判负，与改动前 `holder != def.side` 的保守判敌方向一致，不放宽。
4. **同点敌我混驻**：`buildPointHolders()` 己方优先计入；己方尚在点上就不报敌方占据。若设计要求「敌我同点即算失守」，当前口径偏宽松——规格原文未细化混驻规则，沿用既有取舍。
5. **外交表缺失 / sideId 拼写漂移**：盟友集合推导不到即空集，表外阵营一律按非友方处理（保守判敌），不会把敌军误当盟友；精确字符串匹配，不做模糊消歧。
6. **回合边界极端值**：`duration <= 0`、回档后 `turn` 变小：`turn >= duration` 只与当前回合比较；回档重算走既有快照逻辑，不产生跨回合串线。
7. **多据点 / 复合子条件各自有点**：`buildPointHolders()` 只推顶层胜利条件的一个点；COMPOUND 子 DEFEND 的据点归属不进持有表，落到空表兜底。多据点精确归属留 B10 / 复合条件细化。

## 七、认知成本

- 新增概念仅一个 internal 谓词 `isFriendlySide(side, other, allies)`，KDoc 一句话说清同阵营 ∪ 盟友集合规则；未新增枚举、未新增抽象层。
- 命名：`pointHolders` / `alliedSideIds` 与形参、测试逐字一致；控制器委托函数名不变，B3 既有调用方无需改名。
- 接手路径：读 DEFEND 分支按「失守 → 全灭 → 撑满」三档顺序理解；友 / 敌身份一律查共享谓词，分支内不再出现 `sideId 相异` 裸比较。

## 八、未验证项（如实声明）

1. **未本地编译、未跑单测，需 CI 复核**：按红线未跑 `compileAppDebugKotlin` / `testDebugUnitTest`；本单仅做静态签名对齐、形参名 / 顺序 / 类型核对、括号配平检查与逐条用例走查。测试源集能否编译、5 条 DEFEND 用例与既有 ROUT / CAPTURE / COMPOUND 用例是否转绿，需 CI 复跑确认。
2. 运行期据点推导（`buildPointHolders` 半径判定、盟友集合推导）未在真机 / 模拟器验证；3.0 公里抵达半径沿用既有口径，未实测距离精度。
3. DEFEND 时间边界 `>=` 仅由撑满用例的等号期望支撑；既有控制器测试未断言 DEFEND 终局状态，边界行为未跑回归。
4. 共享谓词委托后，B3 四条情报 / 快照调用点的运行期友敌划分未做回归比对，需 CI 全量单测兜底。

## 九、交接（发现但未改，超出本单红线）

1. **ROUT 分支仍有内联 sideId 比较**：`VictoryChecker.kt:116` `it.sideId != def.side && it.sideId !in alliedSideIds` 未收口到共享谓词。属 B3 / B6 敌我统一范畴，本单按「不许动 B3/B9 刚落库的敌我判定」红线未改；建议后续单将该处也切换到 `!isFriendlySide(def.side, it.sideId, alliedSideIds)`。
2. **B13 在飞产物（非本单）**：工作区 `CampaignJsonParser.kt` 的 `heightMap` 守卫改动，以及 `shell/CrossingRasterBridge.kt`、`terrain/RasterDecoder.kt` 等未跟踪文件，属 B13 parser + terrain 在飞内容，本单未读取、未修改、未回滚。
3. **复合条件多据点持有表**：见边界 7；后续 COMPOUND 子条件含多个 DEFEND / CAPTURE 点时，`buildPointHolders` 需扩为多点表。
4. **分支快照缺据点表持久化**：`saveBranch()` 落 `pointHolders`（`CampaignController.kt:575`），恢复路径由 B7 收口；本单未改快照结构与 `model/CampaignModels.kt`。
