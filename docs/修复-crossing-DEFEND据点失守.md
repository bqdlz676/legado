> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-crossing DEFEND 据点失守（B5，兼解 A8 测试源集编译）

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`，开工前 crossing 主源集 `git diff` 为空。
- 任务范围：**只修 B5**——`VictoryChecker` 的 DEFEND 只查有生力量、不判目标点失守；`check()`/`evaluate()` 缺 `pointHolders` / `alliedSideIds` 形参，导致测试源集 `VictoryCheckerTest.kt`（未跟踪，已按规格写好 F4 用例）编译不过（清单 A8）。
- 红线遵守：未跑本地 gradle / kotlinc / assemble；未做任何 git 写操作；未碰 `shell/**`、`terrain/**`、`ui/config/**`、`ui/about/**`、`ui/book/source/**`、`ui/autoTask/**`、`ui/main/DetailSheet.kt`；未改 `app/build.gradle` / `.versioncode` / `app/luowen.jks` / `.github/workflows/build.yml`。
- 规格判据：`docs/落文-规格切片/落文-原文-06-四渡赤水引擎.md:105`「`defend`：目标点/城未失守，撑过所有回合」。

## 一、改动清单（文件:行 + before/after）

### 1.1 `engine/VictoryChecker.kt`（+22 / -8）

#### 1.1.1 签名变更表

| 项 | 改动前 | 改动后 |
|---|---|---|
| `check(...)` 形参 | `(def, corps, turn, duration, captureHoldTurns)` | `(def, corps, turn, duration, captureHoldTurns, pointHolders: Map<String, String> = emptyMap(), alliedSideIds: Set<String> = emptySet())` |
| `evaluate(...)` 形参（private） | 同 `check` 五参 | 同 `check` 七参，**无默认值**（内部必须显式透传） |
| 透传链路 | `check → evaluate`；COMPOUND 子条件递归 `evaluate(...)` | 两处递归调用均补传 `pointHolders`、`alliedSideIds` |

形参名、顺序与测试调用逐字对齐：测试 `pointHolders = ...`（`VictoryCheckerTest.kt:80/96/111`）、`alliedSideIds = ...`（`:97/:159`）。

**关于默认值的口径冲突留痕**：派单书写「不许把 `pointHolders` 塞成默认空 map 来兼容」；但同一测试源集有 7 处沿用五参旧形参表的调用（`:120/:131/:142/:169/:188/:192/:197`）不传这两个新形参，若形参无默认值，测试源集无法编译。测试是规格、断言不许改；按「指令冲突时以工程现实为准」，默认值仅挂在公开 `check` 上以兼容这些既有用例；private `evaluate` 不带默认值；**生产唯一调用方 `CampaignController` 显式传入实参，不依赖默认值**。默认值语义已在 KDoc 注释中写明（`VictoryChecker.kt:29-30`）。

#### 1.1.2 DEFEND 分支 before/after（`VictoryChecker.kt:74-86`）

改动前：

```kotlin
VictoryType.DEFEND -> {
    val defenders = corps.filter { it.sideId == def.side && it.combatReady() }
    if (defenders.isEmpty()) {
        Outcome(BattleStatus.DEFEAT, def.side, "防御方已被击溃")
    } else if (turn > duration) {
        Outcome(BattleStatus.VICTORY, def.side, "撑过全部 $duration 回合，防御成功")
    } else Ongoing
}
```

改动后：

```kotlin
VictoryType.DEFEND -> {
    val defenders = corps.filter { it.sideId == def.side && it.combatReady() }
    // 据点占据方：据点 key 与 captureHold / 测试用例同一口径（lng,lat 各四位小数）。
    val holder = def.point?.let { pointHolders[pointKey(it)] }
    // 被非盟友的敌方占据即失守；防御方己方或盟友占据均不算失守。
    val lost = holder != null && holder != def.side && holder !in alliedSideIds
    when {
        lost -> Outcome(BattleStatus.DEFEAT, def.side, "据点已被敌方占据，防御失败")
        defenders.isEmpty() -> Outcome(BattleStatus.DEFEAT, def.side, "防御方已被击溃")
        turn >= duration -> Outcome(BattleStatus.VICTORY, def.side, "撑过全部 $duration 回合，防御成功")
        else -> Ongoing
    }
}
```

#### 1.1.3 DEFEND 判定表

| 据点占据方 `holder` | 防御方有生力量 | 回合进度 | 判定 | 文案 |
|---|---|---|---|---|
| 非盟友敌方（`holder != def.side` 且 `holder ∉ alliedSideIds`） | 尚存 | 任意 | **DEFEAT**（优先于全灭判定） | 据点已被敌方占据，防御失败 |
| 盟友（`holder ∈ alliedSideIds`） | 尚存 | 未撑满 | ONGOING | 推演进行中 |
| 己方（`holder == def.side`） | 尚存 | 未撑满 | ONGOING | 推演进行中 |
| 无占据记录（表中无 key，或 `def.point == null`） | 尚存 | 未撑满 | ONGOING | 推演进行中 |
| 任意（含盟友/己方/无记录） | 全灭（无可战军队） | 任意 | **DEFEAT** | 防御方已被击溃 |
| 盟友/己方/无失守 | 尚存 | `turn >= duration` | **VICTORY** | 撑过全部 $duration 回合，防御成功 |
| 据点 key 未在表中（空表） | 尚存 | 撑满 | **VICTORY** | 同上（空表按「未失守」兜底） |

边界口径：
- 据点 key 复用既有 `pointKey()`（`"%.4f".format(lng),%.4f".format(lat)`），与 `captureHold` 计数、测试 `pointKey`（`VictoryCheckerTest.kt:26`）同一格式。
- 时间边界由旧 `turn > duration` 改为 `turn >= duration`：F4 用例三以 `turn=30, duration=30` 期望 VICTORY（`VictoryCheckerTest.kt:105-113`），等号即「撑满全部回合」；此改动**仅限 DEFEND 分支**，其余六分支时间比较未动。

### 1.2 `engine/CampaignController.kt`（+29 / -5）

#### 1.2.1 调用方补齐清单（全仓生产调用仅 1 处）

| 调用点 | 改动前实参 | 改动后实参 |
|---|---|---|
| `CampaignController.kt:150` `VictoryChecker.check(...)` | `def / corps / turn / duration / captureHoldTurns` | 同上 + `pointHolders = buildPointHolders()` + `alliedSideIds = alliedSidesWith(definition.victory.side)` |

#### 1.2.2 新增私有推导口径

- `buildPointHolders(): Map<String, String>`（`:343-348`）：
  - 仅取顶层胜利条件 `definition.victory.point`；`point == null` → `emptyMap()`。
  - 候选：`combatReady()`（存活 / 未投降 / 兵力 > 0）且 `nearPoint(..., point)`（沿用 `REACH_RADIUS_KM = 3.0` 公里半径，与既有 `nearPoint` 同口径）。
  - 同点多支军队：己方优先，其次任一可战他方；敌 / 友身份交由判定层按 `alliedSideIds` 区分。
  - 半径内无可战军队 → `emptyMap()`；判定层按「据点未失守」兜底。
- `alliedSidesWith(sideId: String?): Set<String>`（`:351-357`）：从 `definition.diplomacy` 取 `Relation.ALLIED` 对的另一方；`sideId == null` → 空集。
- `pointKey(point: Geo)`（`:359-360`）：从 `updateCaptureTracker` 内联表达式抽为私有复用，格式不变。
- `instantiateCorps()` 原内联盟友计算改为复用 `alliedSidesWith(ownSideId)`（`:74`），角色判定口径不变。

#### 1.2.3 `model/CampaignModels.kt`

**未改动**。本轮无需新增字段：据点持有表每回合由 `corps` 实时推导，不落入持久态。

## 二、grep 证据（真实输出）

### 2.1 两个主文件新形参全部命中

```
$ grep -n "pointHolders\|alliedSideIds" \
    app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt \
    app/src/main/java/io/legado/app/ui/map/crossing/engine/CampaignController.kt
VictoryChecker.kt:26:     * @param pointHolders 据点当前占据方表（key = 据点标识，value = 占据方 sideId）；
VictoryChecker.kt:28:     * @param alliedSideIds 与胜利条件方结盟的 sideId 集合；盟友占据据点不算失守。
VictoryChecker.kt:38:        pointHolders: Map<String, String> = emptyMap(),
VictoryChecker.kt:39:        alliedSideIds: Set<String> = emptySet(),
VictoryChecker.kt:41:        val outcome = evaluate(def, corps, turn, duration, captureHoldTurns, pointHolders, alliedSideIds)
VictoryChecker.kt:52:        pointHolders: Map<String, String>,
VictoryChecker.kt:53:        alliedSideIds: Set<String>,
VictoryChecker.kt:77:                val holder = def.point?.let { pointHolders[pointKey(it)] }
VictoryChecker.kt:79:                val lost = holder != null && holder != def.side && holder !in alliedSideIds
VictoryChecker.kt:118:                val results = def.sub.map { evaluate(it, corps, turn, duration, captureHoldTurns, pointHolders, alliedSideIds) }
CampaignController.kt:156:            pointHolders = buildPointHolders(),
CampaignController.kt:157:            alliedSideIds = alliedSidesWith(definition.victory.side),
```

### 2.2 全仓 `VictoryChecker.check(` 调用清点

```
$ grep -rn "VictoryChecker.check(" app/src
CampaignController.kt:150:        val outcome = VictoryChecker.check(            ← 生产唯一调用，已补两个显式实参
VictoryCheckerTest.kt:74:        val outcome = VictoryChecker.check(       ← 传 pointHolders；alliedSideIds 走默认空集
VictoryCheckerTest.kt:90:        val outcome = VictoryChecker.check(       ← pointHolders + alliedSideIds 均传
VictoryCheckerTest.kt:105:        val outcome = VictoryChecker.check(       ← 传 pointHolders；alliedSideIds 走默认空集
VictoryCheckerTest.kt:120:        VictoryChecker.check(def, blockers, turn = 5, duration = 30, emptyMap())   ← 两参默认
VictoryCheckerTest.kt:131:        VictoryChecker.check(def, blockers, turn = 5, duration = 30, emptyMap())   ← 两参默认
VictoryCheckerTest.kt:142:        VictoryChecker.check(def, wiped, turn = 5, duration = 30, emptyMap())      ← 两参默认
VictoryCheckerTest.kt:153:        val outcome = VictoryChecker.check(       ← 传 alliedSideIds；pointHolders 走默认空表
VictoryCheckerTest.kt:169:        val outcome = VictoryChecker.check(       ← 两参默认
VictoryCheckerTest.kt:188:        VictoryChecker.check(orDef, corps, turn = 5, duration = 30, emptyMap())   ← 两参默认
VictoryCheckerTest.kt:192:        VictoryChecker.check(andDef, corps, turn = 5, duration = 30, emptyMap())  ← 两参默认
VictoryCheckerTest.kt:197:        VictoryChecker.check(andPartial, oneSided, turn = 5, duration = 30, emptyMap()) ← 两参默认
```

生产调用 1 处：已补实参；测试调用 11 处：形参名 / 顺序与签名一致，缺省形参命中默认值，编译可对齐。

### 2.3 改动面 stat（证明只动两文件、DEFEND 分支与签名）

```
$ git diff --numstat -- app/src/main/java/io/legado/app/ui/map/crossing/
29  5  .../engine/CampaignController.kt
22  8  .../engine/VictoryChecker.kt

$ git diff --stat -- app/src/main/java/io/legado/app/ui/map/crossing/model/
（空，CampaignModels.kt 未改）
```

### 2.4 其它五（+ 复合）胜利类型 diff 为零核对

逐分支对照 `git diff`：

| 类型 | 分支位置 | 本轮 diff |
|---|---|---|
| ANNIHILATE | `VictoryChecker.kt:56-63` | 零改动（目标歼灭 / 回合耗尽文案、比较符原样） |
| CAPTURE | `:65-72` | 零改动（`captureHoldTurns` 语义、`holdTurns` 阈值原样） |
| ESCAPE | `:88-98` | 零改动 |
| REACH_POINT | `:100-106` | 零改动 |
| ROUT | `:108-115` | 零改动（未按 `alliedSideIds` 排除盟友——属 B3/B6 范畴，见交接） |
| COMPOUND | `:117-125` | 仅递归 `evaluate(...)` 补传两个新形参；AND/OR 口径、`all { it }` 谓词、耗尽判定原样 |

## 三、F4 三条用例逐条走查（输入 → 期望 → 实现为何满足）

### 3.1 用例一（`VictoryCheckerTest.kt:71-84`）「据点被敌方占据即判负，不论有生力量是否尚存」

- 输入：`def.side = "a"`，`corps = [a1 side "a" strength 8.0，combatReady]`；`pointHolders = {pointKey → "b"}`；`turn=5, duration=30`；`captureHoldTurns = emptyMap()`；`alliedSideIds` 缺省 `emptySet()`。
- 期望：`status = DEFEAT`；`reason` 含「据点」。
- 实现路径：`defenders` 非空 → `holder = "b"` → `lost = ("b" != null) && ("b" != "a") && ("b" ∉ {}) = true` → 首分支命中 `DEFEAT`，文案「据点已被敌方占据，防御失败」含「据点」。有生力量尚存不影响判负，满足规格「目标点被占即判负」。

### 3.2 用例二（`:87-100`）「盟友占据据点不算失守，防御方尚存即继续」

- 输入：`holders = {pointKey → "c"}`；`alliedSideIds = setOf("c")`；`turn=5, duration=30`；防御方尚存。
- 期望：`status = ONGOING`。
- 实现路径：`holder = "c"` → `lost = ("c" != "a") && ("c" ∉ {"c"}) = false` → 非失守；`defenders` 非空；`5 >= 30` 不成立 → `Ongoing`。盟友占据不算失守，满足。

### 3.3 用例三（`:103-114`）「撑满全部回合且据点未失守判胜」

- 输入：`holders = {pointKey → "a"}`；`turn=30, duration=30`；`alliedSideIds` 缺省空集；防御方尚存。
- 期望：`status = VICTORY`。
- 实现路径：`holder = "a"` → `lost = ("a" != "a") = false` → 非失守；`defenders` 非空；`30 >= 30` 成立 → `VICTORY`，文案「撑过全部 30 回合，防御成功」。等号边界由本轮在 DEFEND 分支校正为 `>=`，满足。

## 四、二阶三阶影响

1. **直接效果**：DEFEND 终局判定从「只看有生力量」扩为「有生力量 + 据点归属 + 盟友身份」；判定层签名多两个入参。
2. **连带接口影响**：
   - 生产唯一调用方 `CampaignController.step()`（`:150-158`）每回合新算一次据点持有表与盟友表；推导源为现有 `corps` / `definition.diplomacy`，不新增运行态字段。
   - COMPOUND 递归子条件随父条件透传同一 `pointHolders` / `alliedSideIds`；若子条件为 DEFEND，可独立判据点失守；其余类型拿到入参但不消费，行为不变。
   - 分支存档（`saveBranch` / `restoreBranch`）不持有据点表：`buildPointHolders()` 每回合从恢复后的 `corps` 实时重算，回档后不会沿用旧时间线的占据结果；`captureHold` 的存档缺口属 B7，见交接。
3. **用户行为 / 系统复杂度 / 维护成本**：
   - 玩家侧：据点被敌军突入即提前判负，与规格「守城」语义一致；盟友协防据点不会误判失守。
   - 复杂度：判定层仍为纯函数、不改状态；新增两个形参与一个私有推导函数，抽象层未加厚。
   - 维护成本：据点 key 格式在判定层、控制器、测试三处复用同一 `%.4f` 口径；后续若改 key 规则需三处同步，已在注释中标注口径来源。

## 五、反例与边界（先假设方案错误，逐条找反例）

1. **据点表为空 / 状态里没有据点持有记录**：`pointHolders` 空表时 `holder = null`，`lost = false`，回落原「有生力量 + 回合」逻辑；控制器侧目标点缺失或半径内无可战军队同样产出空表。不会崩、不会误判失守。反例风险：空表语义是「未知」而非「己方守住」，若上游长期不填表，DEFEND 的失守判负会被静默绕过——生产调用方已显式推导，默认空表仅为测试兼容。
2. **目标点 `def.point == null`**（如内置演示战役 `DemoCampaigns.kt:22` 为无 point 的 defend）：`holder = null`，失守判定跳过，走原全灭 / 撑满回合路径；不抛 NPE。
3. **同点敌我混驻**：己方优先计入 `buildPointHolders()`，即己方尚在点上就不报敌方占据；若设计要求「敌我同点即算失守」，当前口径偏宽松。此为口径取舍，已在 `buildPointHolders` 注释写明；规格原文未细化混驻规则。
4. **外交表缺失 / sideId 对不上**：`alliedSidesWith` 找不到 ALLIED 对 → 空集；表外阵营一律按非盟友处理（保守判敌），不会把敌军误当盟友。
5. **同名阵营 / sideId 拼写不一致**：匹配走精确字符串相等；JSON 侧拼写漂移会被当成敌对方，属数据质量问题，本轮不做模糊消歧。
6. **回合边界极端值**：`duration <= 0`、`turn` 回档（分支恢复后 turn 变小）：`turn >= duration` 只在当前回合与上限比较；回档后重算走既有快照逻辑，不产生跨回合串线。
7. **多据点 / 复合子条件各自有点**：本轮 `buildPointHolders` 只推导顶层胜利条件的一个点；COMPOUND 子条件自带 point 时，其据点归属不进持有表（子 DEFEND 会落到空表兜底）。多据点精确归属需随 B10 / 复合条件细化再补。

## 六、认知成本

- **新增概念**：两个入参（`pointHolders` 据点占据方表、`alliedSideIds` 盟友集合），含义在 KDoc 一句话说清；未新增枚举、未新增抽象层。
- **命名**：`pointHolders` / `alliedSideIds` 与测试形参逐字一致；控制器侧 `buildPointHolders` / `alliedSidesWith` / `pointKey` 三个私有函数职责单一，命名与既有 `updateCaptureTracker` / `nearPoint` 风格一致。
- **接手成本**：新人读 DEFEND 分支可按「失守 → 全灭 → 撑满」三档顺序理解；唯一需要留意的是公开 `check` 带默认值而 private `evaluate` 不带，注释已说明默认值仅为测试兼容、生产必须显式传参。
- **可简化项**：`pointKey` 在判定层与控制器各有一份私有实现（格式同源）；本轮按最小改动未抽公共工具，留待后续若出现第三处再收口。

## 七、未验证项（如实声明）

1. **未本地编译、未跑单测；需 CI confirm**：按红线未跑 `compileAppDebugKotlin` / `testDebugUnitTest`；本轮仅做静态签名对齐、形参名 / 顺序 / 类型核对与逐条用例走查。测试源集能否编译、F4 三条用例是否转绿，需 CI 复跑确认。
2. 运行期据点推导（`buildPointHolders` 半径判定、盟友集合推导）未在真机 / 模拟器上验证；`nearPoint` 半径沿用既有 3.0 公里口径，未实测距离精度。
3. DEFEND 时间边界 `>` → `>=` 仅由 F4 用例三的等号期望支撑；既有控制器测试未断言 DEFEND 终局状态，边界行为未跑回归。

## 八、交接（发现但未改，超出本工单红线）

1. **ROUT 盟友排除未落地（B3 / B6 范畴）**：`VictoryChecker.kt:108-115` 的 `enemies` 仍按 `sideId != def.side` 过滤，未读 `alliedSideIds`。测试 `VictoryCheckerTest.kt:147-163`（F2 盟友不计入 blockers）当前逻辑上不会转绿；本轮按派单要求未动 ROUT 分支。建议并入 B3（外交盟友当敌军）或 B6（ROUT 口径）工单处理。
2. **分支存档缺 `captureHold` / `pointHolders` 快照（B7 范畴）**：`CampaignControllerTest.kt:145-151`（F6）已引用 `branch.captureHold`、`branch.pointHolders`，而 `BranchSnapshot`（`CampaignModels.kt:219-224`）尚无这两个字段；测试源集在此处存在既有编译缺口，本轮未改 model 字段，留 B7 收口。
3. **复合条件多据点持有表**：见边界 7；若后续 COMPOUND 子条件含多个 DEFEND/ CAPTURE 点，`buildPointHolders` 需扩为多点表。
