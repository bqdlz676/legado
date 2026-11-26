> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复 B6：crossing ROUT（溃退）胜利判定口径

> 工单：`docs/问题清单.md` B6——「ROUT 口径『无可战』，未要求士气 BROKEN」，判据：**敌方存活军队士气全 BROKEN 才判胜**。
> 范围（最小集）：仅 `VictoryChecker.kt` 的 ROUT 分支 + 同步该分支消费的 `alliedSideIds` 参数 KDoc；签名未变，生产 / 测试调用点零改动。

## 一、任务与范围

### 1.1 规格与测试依据（逐字抄录）

- 规格切片 `docs/落文-规格切片/落文-原文-06-四渡赤水引擎.md:108`：`| rout | 敌方所有军队士气broken |`。
- 契约索引 `docs/落文-模块契约索引.md`：无 crossing 胜利判定专条（仅有「签名咬合级」分类描述，L175）；本单按测试 + `model/CampaignModels.kt` 现有字段推导。
- 测试源集 `app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt` 四条 ROUT 用例（断言原文照抄，未改写）：

| 用例（行号） | 输入（corps / def / 回合 / 实参） | 期望 |
|---|---|---|
| L117 `F5 ROUT 敌方存活且士气未全溃时继续推演` | `corps("b1", "b", morale = Morale.HIGH)`（strength 5.0 / alive true / surrendered false / role OWN）；`VictoryDef(VictoryType.ROUT, null, "a", null, 3, "AND", emptyList())`；`check(def, blockers, turn = 5, duration = 30, emptyMap())`，`pointHolders`/`alliedSideIds` 走默认 | `BattleStatus.ONGOING` |
| L125 `F5 ROUT 敌方存活军队全部 BROKEN 才判胜` | `corps("b1", "b", morale = Morale.BROKEN)`、`corps("b2", "b", morale = Morale.BROKEN)`；同 def；`turn = 5, duration = 30, emptyMap()`，两新参默认 | `BattleStatus.VICTORY` |
| L136 `F5 投降或被歼不计入溃散，blockers 为空不据 ROUT 判胜` | `corps("b1", "b", alive = false, strength = 0.0)`、`corps("b2", "b", surrendered = true)`；同 def；`turn = 5, duration = 30, emptyMap()`，两新参默认 | `BattleStatus.ONGOING` |
| L147 `F2 盟友不计入 ROUT blockers` | `corps("c1", "c", morale = Morale.HIGH, role = FactionRole.ALLY)`、`corps("b1", "b", morale = Morale.BROKEN)`；`alliedSideIds = setOf("c")`，`pointHolders` 默认；`turn = 5, duration = 30, captureHoldTurns = emptyMap()`；注释：「盟友 c1 士气高昂但被排除；敌军 b1 已全溃 → 判胜。」 | `BattleStatus.VICTORY` |

### 1.2 模型层既有口径（`model/`，未改动）

- `CampaignModels.kt:173`：`fun combatReady(): Boolean = alive && !surrendered && strength > 0.0`。BROKEN 士气**不** disqualify combatReady，故 BROKEN 军队仍是「在岗敌军」，需单独比士气。
- `Enums.kt:12-16`：`Morale { HIGH, STEADY, WAVERING, BROKEN }`；全溃只认 `BROKEN` 严格相等，WAVERING 不算。
- `CampaignModels.kt:41-49`：`VictoryDef(type, target, side, point, holdTurns, op, sub)`，测试位置实参与此顺序一致。

## 二、改动清单（file:line + before/after）

唯一改动文件：`app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt`

### 2.1 新增 import（L5）

```diff
 import io.legado.app.ui.map.crossing.model.BattleStatus
 import io.legado.app.ui.map.crossing.model.Corps
+import io.legado.app.ui.map.crossing.model.Morale
 import io.legado.app.ui.map.crossing.model.VictoryDef
 import io.legado.app.ui.map.crossing.model.VictoryType
```

### 2.2 KDoc 同步：`alliedSideIds` 现被 DEFEND 与 ROUT 两个分支消费（L29–30）

```diff
-     * @param alliedSideIds 与胜利条件方结盟的 sideId 集合；盟友占据据点不算失守。
+     * @param alliedSideIds 与胜利条件方结盟的 sideId 集合；DEFEND 中盟友占据据点不算失守，
+     *        ROUT 中盟友不计入敌方 blockers。
```

### 2.3 ROUT 分支体重写（L110–123）

before（旧：`enemies.isEmpty()` 直接判胜，L108–115 旧行号）：

```kotlin
VictoryType.ROUT -> {
    val enemies = corps.filter { it.sideId != def.side && it.combatReady() }
    if (enemies.isEmpty()) {
        Outcome(BattleStatus.VICTORY, def.side, "敌方所有军队均已溃散")
    } else if (turn > duration) {
        Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，敌方尚未全溃")
    } else Ongoing
}
```

after（现 L110–123）：

```kotlin
VictoryType.ROUT -> {
    // 溃退口径（规格 §四 rout：敌方所有军队士气 broken）：
    // 敌军 = 非胜利方、非盟友、仍可作战（存活 / 未投降 / 兵力>0）的军队。
    // 投降、被歼者不计入溃散；敌军被清空本身不构成 ROUT 胜利，只能拖到回合耗尽判负。
    val enemies = corps.filter {
        it.sideId != def.side && it.sideId !in alliedSideIds && it.combatReady()
    }
    val routed = enemies.isNotEmpty() && enemies.all { it.morale == Morale.BROKEN }
    when {
        routed -> Outcome(BattleStatus.VICTORY, def.side, "敌方所有军队士气均已溃散")
        turn > duration -> Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，敌方尚未全溃")
        else -> Ongoing
    }
}
```

判定语义（三个合取条件，缺一不可）：

1. `enemies`：`sideId != def.side` 且 `sideId !in alliedSideIds` 且 `combatReady()`；
2. `routed`：`enemies` 非空 **且** 每一支在岗敌军 `morale == Morale.BROKEN`；
3. 未达成且 `turn > duration` → DEFEAT；否则 Ongoing。

### 2.4 未触碰项（硬约束回证）

- 签名 `check(...)`（L34–41）与 `evaluate(...)`（L48–55）形参表**未变**；`pointHolders`/`alliedSideIds` 默认值保留。
- `ANNIHILATE`（L58–65）、`CAPTURE`（L67–74）、`DEFEND`（L76–88）、`ESCAPE`（L90–100）、`REACH_POINT`（L102–108）、`COMPOUND`（L125–133）分支体零改动；COMPOUND 仅既有的 `evaluate(...)` 递归透传两参（L126），本单未改该行。
- 未改任何测试断言；未新增测试（既有四用例即为规格）。

## 三、grep 回证

### 3.1 新符号 / 新谓词在仓内命中

```text
$ grep -n "Morale\|BROKEN\|alliedSideIds" app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt
5:import io.legado.app.ui.map.crossing.model.Morale
29:     * @param alliedSideIds 与胜利条件方结盟的 sideId 集合；DEFEND 中盟友占据据点不算失守，
30:     *        ROUT 中盟友不计入敌方 blockers。
41:        alliedSideIds: Set<String> = emptySet(),
43:        val outcome = evaluate(def, corps, turn, duration, captureHoldTurns, pointHolders, alliedSideIds)
55:        alliedSideIds: Set<String>,
81:                val lost = holder != null && holder !in alliedSideIds
115:                    it.sideId != def.side && it.sideId !in alliedSideIds && it.combatReady()
117:                val routed = enemies.isNotEmpty() && enemies.all { it.morale == Morale.BROKEN }
126:                val results = def.sub.map { evaluate(it, corps, turn, duration, captureHoldTurns, pointHolders, alliedSideIds) }
```

旧谓词清零回证：旧代码 `enemies.isEmpty()` 判胜路径已不存在：

```text
$ grep -n "enemies.isEmpty" app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt
（无命中）
```

### 3.2 全仓 `VictoryChecker.check(` 调用点清点（共 12 处：生产 1 + 测试 11）

```text
$ grep -rn "VictoryChecker.check(" app/src
app/src/main/java/io/legado/app/ui/map/crossing/engine/CampaignController.kt:150:        val outcome = VictoryChecker.check(
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:74:        val outcome = VictoryChecker.check(
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:90:        val outcome = VictoryChecker.check(
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:105:        val outcome = VictoryChecker.check(
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:120:        VictoryChecker.check(def, blockers, turn = 5, duration = 30, emptyMap())
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:131:        VictoryChecker.check(def, blockers, turn = 5, duration = 30, emptyMap())
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:142:        VictoryChecker.check(def, wiped, turn = 5, duration = 30, emptyMap())
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:153:        val outcome = VictoryChecker.check(
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:169:        val outcome = VictoryChecker.check(
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:188:        val orOutcome = VictoryChecker.check(orDef, corps, turn = 5, duration = 30, emptyMap())
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:192:        val andOutcome = VictoryChecker.check(andDef, corps, turn = 5, duration = 30, emptyMap())
app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt:197:        val andPartial = VictoryChecker.check(andDef, oneSided, turn = 5, duration = 30, emptyMap())
```

逐点补参情况（签名未变，**无需补参**；仅列现状）：

| 调用点 | 分支 / 用例 | 实参情况 |
|---|---|---|
| `CampaignController.kt:150-158` | 生产唯一调用 | `def / corps / turn / duration / captureHoldTurns / pointHolders = buildPointHolders() / alliedSideIds = alliedSidesWith(definition.victory.side)`，七参齐全，无需改动 |
| `VictoryCheckerTest.kt:74` | F4 DEFEND | 传 `pointHolders`；`alliedSideIds` 默认空集 |
| `VictoryCheckerTest.kt:90` | F4 DEFEND | 传 `pointHolders` + `alliedSideIds = setOf("c")` |
| `VictoryCheckerTest.kt:105` | F4 DEFEND | 传 `pointHolders`；`alliedSideIds` 默认 |
| `VictoryCheckerTest.kt:120` | F5 ROUT T1（HIGH 未全溃） | 五参位置式；两新参默认 |
| `VictoryCheckerTest.kt:131` | F5 ROUT T2（双 BROKEN） | 五参位置式；两新参默认 |
| `VictoryCheckerTest.kt:142` | F5 ROUT T3（阵亡 + 投降） | 五参位置式；两新参默认 |
| `VictoryCheckerTest.kt:153` | F2 ROUT T4（盟友排除） | 传 `alliedSideIds = setOf("c")`；`pointHolders` 默认 |
| `VictoryCheckerTest.kt:169` | CAPTURE | 两新参默认 |
| `VictoryCheckerTest.kt:188` | COMPOUND OR | 两新参默认 |
| `VictoryCheckerTest.kt:192` | COMPOUND AND | 两新参默认 |
| `VictoryCheckerTest.kt:197` | COMPOUND AND 半边 | 两新参默认 |

签名唯一性回证：全仓 `fun check(` 在 crossing 引擎内仅 `VictoryChecker.kt:34` 一处，无重载歧义。

### 3.3 `git diff --numstat` 原文（含工作树内其它代理在飞改动，未触碰）

```text
35	13	app/src/main/java/io/legado/app/ui/dict/DictDialog.kt
15	7	app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt
81	0	app/src/test/java/io/legado/app/ui/map/crossing/engine/CampaignControllerTest.kt
22	0	app/src/test/java/io/legado/app/ui/map/crossing/engine/CampaignJsonParserTest.kt
4	2	app/src/test/java/io/legado/app/ui/map/crossing/engine/PathfinderTest.kt
21	0	app/src/test/java/io/legado/app/ui/map/crossing/terrain/TerrainCostTest.kt
6	0	"docs/问题清单.md"
```

本单文件单独统计：

```text
$ git diff --numstat -- app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt
15	7	app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt
```

（15 行新增 / 7 行删除；其余 numstat 行属其它代理在飞改动，本单未读改其内容。）

## 四、二阶三阶影响（铁律 59）

**① 直接效果**：ROUT 终局判定从「敌军（在岗）清空即胜」收紧为「在岗敌军非空且士气全部 BROKEN 才胜」；阵亡、投降、兵力归零者不计入溃散；盟友不计入敌军。

**② 连带影响（被影响文件 / 接口）**：

- `CampaignController.kt:308-158`：每回合 `step()` 消费 `Outcome.finished`。口径收紧后，ROUT 战役在「敌军被歼光 / 投降光」时不再提前判 VICTORY，而会继续推演到 `turn > duration` 判 DEFEAT；回放快照序列可能变长，`status / winnerSide / finishReason` 落库时机后移。
- `MoraleSystem`（`CampaignController.kt:292-146`）：每回合推进士气，是敌军走向 BROKEN 的上游；本单不改其推进规则，只消费结果。
- `DemoCampaigns.kt:22` 当前演示战役胜利类型为 `defend`，无 `rout` JSON；生产数据暂无 ROUT 战役，本单影响面限于测试源集 + 后续新增 rout 战役配置。
- COMPOUND 子条件为 ROUT 时，经 `evaluate` 递归透传 `alliedSideIds`（L126），聚合 AND/OR 语义不变。

**③ 二阶三阶后果**：

- 用户行为：策划/数据侧配 `rout` 战役时，必须确保敌方士气存在被战斗、疲劳、补给压到 BROKEN 的路径；若设计意图是「杀光即可」，应配 `annihilate` 而非 `rout`。两种胜利类型在规格 §四 本就分列，收紧后二者不再串口径。
- 系统复杂度：判定谓词与四条 F5 用例一一对应，未新增抽象层；后续每加一个 VictoryType 需同步补「正向达成 / 未达成 / 清空边界」三态用例，否则同类口径错会复发。
- 维护成本：`alliedSideIds` 现由 DEFEND、ROUT 两分支共享，KDoc 已注明；接手者改盟友口径时需同时复查两处。

## 五、反例与边界（铁律 61：先自我否证）

最强反对者假设：「在岗敌军全 BROKEN 才胜」可能在以下边界翻车，逐条给出兜底与结论。

1. **盟友占点 / 盟友军队混入 blockers**：盟友 sideId 命中 `alliedSideIds` 即从 `enemies` 剔除（L115），其 HIGH 士气不阻断判胜——T4（L147）实证：c1(HIGH, 盟友) + b1(BROKEN) → VICTORY。否证检查：若 `diplomacy` 表漏配 ALLIED 关系，`alliedSidesWith` 返回空集，盟友会被误判为敌；此为上游装配问题，见交接节 B3，本单判定层口径无误。
2. **据点全失**：ROUT 分支不消费 `pointHolders`；据点失守是 DEFEND 分支职责（L77–81）。ROUT 战役中据点被占不直接判负，统一在 `turn > duration` 时判负。若 COMPOUND 同时挂 DEFEND 子条件，失守由该子条件独立判负，两路不串扰（L126 递归各判各的）。
3. **敌仍有 1 单位且非 BROKEN**：`enemies` 非空，`all { morale == BROKEN }` 为 false → Ongoing。T1（L117，HIGH）实证；WAVERING 同样不算 BROKEN（严格相等，不做降级归并）。
4. **双方全灭 / 敌军清空**：阵亡（`alive=false`）、投降（`surrendered=true`）、兵力归零（`strength=0.0`）均被 `combatReady()` 剔除 → `enemies` 为空 → `routed=false`；`turn ≤ duration` → Ongoing，`turn > duration` → DEFEAT。T3（L136）实证清空不据 ROUT 判胜。边界备注：己方全灭在 ROUT 分支内不判负，己方存活由其它胜利类型 / 控制器状态兜底，超出本单范围，未越界改。
5. **duration 边界**：判负沿用 `turn > duration`（与 ANNIHILATE / CAPTURE / ESCAPE / REACH_POINT / COMPOUND 一致）；`turn == duration` 时先查 `routed`，达成即胜，未达成不判负。DEFEND 分支既有 `turn >= duration` 口径属另一分支，本单不动。
6. **COMPOUND 嵌套 ROUT**：子 ROUT 经 `evaluate` 递归，`alliedSideIds`/`pointHolders` 随参透传（L126）；AND 需全部子条件胜、OR 任一胜。两层嵌套 ROUT 时，盟友排除在每一层各自生效；空 `sub` 列表走既有 `all()/any()` 语义，本单未改。
7. **补充边界：`strength=0` 但 `alive=true`**：`combatReady()` 为 false，不计入 `enemies`，视同被歼；不会因 morale 字段残留 BROKEN/STEADY 而误判。
8. **补充边界：morale 缺字段 / 未知标签**：`Morale.fromLabel`（`Enums.kt:36-42`）未知值兜底 STEADY，不返回 null；未知字符串不会被误归 BROKEN。

否证结论：以上 8 条边界下实现均有确定去向（Ongoing / VICTORY / DEFEAT），无空白态、无异常穿透；方案在测试四用例与规格 §四 rout 行上成立。

## 六、认知成本（铁律 63）

- 改动规模：1 个文件 `VictoryChecker.kt`（改后 151 行）；判断落点 L110–123。
- 接手所需上下文：本文件全文 + `CampaignModels.kt:173`（`combatReady`）+ `Enums.kt:12-24`（`Morale` 四级）+ 测试 L117/125/136/147 四用例；合计约 200 行内可读完，无需回放壳层或 JSON 解析层上下文。
- 新增概念：无。复用既有 `enemies` 过滤、`alliedSideIds` 形参、`combatReady()`、`Morale.BROKEN`；局部变量 `routed` 与胜因文案「士气均已溃散」同口径，命名自解释。
- 简化执行：未新增形参（复用 B5 引入的 `alliedSideIds`），12 个调用点零改动；KDoc 已同步标注两分支消费关系，避免后人误以为该参仅 DEFEND 专用。
- 遗留复杂度：ROUT 与 ANNIHILATE 都可能表现为「战场无人」，区别在退出原因（士气溃散 vs 被歼/投降）；判定层已用 `combatReady` + morale 两层区分，建议后续在壳层胜因文案上保持这一区分（本单未动壳层）。

## 七、历史包袱审计（铁律 62）

- `git log --follow`：`VictoryChecker.kt` 三次提交——`22cefd1`（初始 U12 引擎）、`ea70156`（谓词 `all{it}` 修补）、`75fd638`（B5：DEFEND 据点失守 + 补两形参）。
- 旧 `enemies.isEmpty() → 胜` 谓词：`git log -S "enemies.isEmpty()"` 定位到 `22cefd1` 初始引擎即写入，属**历史遗留口径**，与规格 §四 rout 行及 F5 测试冲突，本单直接纠正，不留旧判定兼容分支。
- `combatReady()` 在岗过滤：**有意为之**，与 DEFEND/ESCAPE/REACH_POINT 一致，保留。
- `alliedSideIds` 形参：`75fd638` 为 DEFEND 引入；本单让 ROUT 复用同一形参，不新增第二套盟友表。
- 未发现「为解决已不存在问题而引入」的可删死代码。

## 八、跨领域对照（铁律 60）

- 外域 1（象棋 / 军棋）：「将死」不是清空对方棋子——对方尚有孤子未被将死即续战。ROUT 同理：敌军番号仍在岗（存活、未降、有兵力）就不算溃，与本单 `enemies.isNotEmpty()` 前置条件一致。
- 外域 2（队列统计口径）：离职率 / 阵亡率先把已退出队列个体剔除，再对在岗队列算比例。本单先用 `combatReady()` 剔除阵亡 / 投降 / 归零者，再对在岗者判「全 BROKEN」，避免已退出者混入分母被误算成溃散。
- 采纳 / 不采纳：采纳「在岗敌军全 BROKEN」；不采纳「清空即胜」——那是 ANNIHILATE（目标被消灭或投降）的口径，规格 §四 两类型分列，不合并。

## 九、最慢环节分析（铁律 64）

- ROUT 判定为单趟 `corps.filter` + `all`，O(n)，与改前同阶；无 IO、无协程阻塞、无字符串重复构造。
- 本单未引入新慢点；crossing 域既有慢点（`runBlocking` / `Thread.sleep` / clean 编译占大头）不在本文件，未触碰。

## 十、返工复盘沉淀（铁律 65）

- 根因类别：**口径错**（规格 rout 行 vs 实现谓词），与 A5（`predicate` 缺失）、B5（据点失守谓词缺失）同属一族——「判定谓词未按规格 / 测试写全，只写了宽松近似」。
- 预防手段（建议沉淀规则）：每个 VictoryType 在测试源集至少备三态用例——正向达成、未达成续推、清空 / 投降边界；验收时逐条对照规格 §四 表格行，不许只跑正向。本单 F5 四用例已覆盖上述三态 + 盟友排除。

## 十一、59–66 逐条落地结论

- **59 二阶三阶推演**：已写第四节，含直接效果、受影响文件（`CampaignController.kt:292-158`、`MoraleSystem`、COMPOUND 递归）、对策划配置与维护成本的三阶后果。
- **60 跨领域对照**：已写第八节，象棋将死、队列统计两个外域方案，说明采纳 / 不采纳理由。
- **61 自我否证**：已写第五节，8 条反例边界（盟友、据点全失、剩 1 单位、双方全灭、duration、COMPOUND 嵌套、归零未死、morale 缺字段），每条有兜底去向与否证结论。
- **62 历史包袱审计**：已写第七节，分有意为之 / 历史遗留 / 可删死代码三类，附提交号与 `-S` 定位。
- **63 认知成本**：已写第六节，文件数行数、所需上下文、新增概念数、简化动作与遗留复杂度。
- **64 最慢环节**：已写第九节，复杂度 O(n) 与改前同阶，未引入新慢点。
- **65 返工复盘**：已写第十节，根因归簇「口径错 / 谓词写不全」，给出三态用例预防规则。
- **66 开工前必读**：本单开工前已读 `docs/机制-子代理必读.md`、`docs/落文-规矩总纲.md` 59–66 全文、`docs/问题清单.md` B6 条目；附录 `问题清单-附录-逐文件.md` 无 B6/ROUT 落点专条（已在交接节记录）。

## 十二、未验证项清单

1. 本机无 Android SDK / JDK 17 环境，**未跑** `gradle assemble`、**未跑** JVM 单测；编译与测试结果只在云端 CI 发生。
2. 上述四条 F5 用例结论为**静态读码 + 手工推演**，非实际执行输出；`VictoryCheckerTest` 全绿状态待 CI 验证（A8 备注：B5 两形参补齐后测试源集方可编译）。
3. 未在真机 / 模拟器跑完整回合推演序列，`finishReason` 文案展示未做走查。
4. 工作树内 `CampaignControllerTest.kt` 等测试文件为其它代理在飞改动，本单未核对其 ROUT 相关断言（grep 无 `ROUT/rout/BROKEN/溃散/溃退` 命中）。

## 十三、交接（发现但未改的跨模块问题）

1. **B3 外交口径**（`CampaignController.kt:393-397` 一带，遭遇 / 支援 / 溃逃方向未读 diplomacy）：本单 ROUT 的盟友排除依赖 `alliedSidesWith`（`CampaignController.kt:351-357`）从 `diplomacy` ALLIED 对装配；若 B3 未修，战斗结算层仍可能把盟友当敌打，仅胜利判定层排除已就位。建议 B3 工单复核战斗结算与 `alliedSidesWith` 口径一致。
2. **附录补登**：`docs/问题清单-附录-逐文件.md` 无 B6/ROUT 落点条目，建议总控补登 `VictoryChecker.kt:110-123`。
3. **在飞文件**：`CampaignControllerTest.kt`、`CampaignJsonParserTest.kt`、`PathfinderTest.kt`、`TerrainCostTest.kt`、`DictDialog.kt`、`问题清单.md` 工作树改动非本单产物，未读改、未提交。
