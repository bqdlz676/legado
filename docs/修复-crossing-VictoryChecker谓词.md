> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-crossing VictoryChecker 谓词缺失（CI run `45178766005`）

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`，HEAD `6509100`。
- 报错文件：`app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt`。
- 任务范围：**只修** COMPOUND 分支一处编译错误；未改胜负判定逻辑、未改任何阈值；未碰 `IntentResolver` / `CombatResolver` / `MoraleSystem` / `CrossingShell` / `model` / `terrain`；未跑本地 gradle / kotlinc；未做 git 写操作。

## 一、报错原文（CI run `45178766005`）

```
VictoryChecker.kt:106:93 No value passed for parameter 'predicate'
```

## 二、根因复核：派单假设与代码事实的偏差

### 2.1 派单假设（已被代码事实推翻）

派单描述称：「`Outcome` 的第三个形参名是 `predicate`，这里传的是位置实参且前面参数对不齐」。

### 2.2 代码事实：`Outcome` 签名原文（`VictoryChecker.kt:16-20`，定义只读未改）

```kotlin
data class Outcome(
    val status: BattleStatus,
    val winnerSide: String?,
    val reason: String,
) {
    val finished: Boolean get() = status != BattleStatus.ONGOING
}
```

- 形参共 3 个：`status: BattleStatus`、`winnerSide: String?`、`reason: String`；
- **不存在**名为 `predicate` 的形参；全 crossing 主源集 `grep -rn "predicate"` 在改动前零命中（见 §五 门禁证据）。
- 因此「把说明字符串写成 `predicate = "..."`」这一方向若照做，会对没有该形参的构造函数报 `No parameter with name 'predicate' found`，属于反向引入编译错，未采纳。

### 2.3 真实根因：stdlib `Iterable.all(predicate)` 没有无参重载

报错位置 `VictoryChecker.kt:106:93` 落在 COMPOUND 分支的这一行（改动前）：

```kotlin
val passed = if (def.op.equals("OR", ignoreCase = true)) hit.any() else hit.all()
```

- `hit` 类型为 `List<Boolean>`（`results.map { it.finished && it.status == BattleStatus.VICTORY }`）。
- Kotlin 标准库集合扩展：
  - `any()` 有无参重载，也有 `any(predicate)` 重载；
  - `none()` 有无参重载，也有 `none(predicate)` 重载；
  - **`all(predicate: (T) -> Boolean)` 只有带谓词的重载，没有无参 `all()`**。
- `hit.all()` 只能解析到唯一的 `all(predicate)` 重载，编译器按形参名报 `No value passed for parameter 'predicate'`——报错里的 `predicate` 是 **stdlib `all()` 的形参名**，不是 `Outcome` 的形参名。列号 93 对应该行尾部 `.all()` 调用处。
- 旁证（只读核对本地 gradle 缓存中的 stdlib 1.9.20 jar，未执行编译）：`kotlin/collections/CollectionsKt___CollectionsKt.class` 常量池内同时存在 `all` / `any` / `none` / `predicate` 标识符；布尔返回的 Iterable 扩展同时存在无参描述符 `(Iterable)Z` 与谓词描述符 `(Iterable;Function1)Z`。

## 三、改法：仅给 `all()` 补显式谓词 `{ it }`

### 3.1 改动前后对照（唯一改动调用点）

文件：`app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt`，COMPOUND 分支（改动前第 106 行 / 改动后第 107 行）。

| 版本 | 代码 |
|---|---|
| 改动前 | `val passed = if (def.op.equals("OR", ignoreCase = true)) hit.any() else hit.all()` |
| 改动后 | `// all() 无无参重载，必须显式给 predicate；{ it } 即“每项均为 true”，与 AND 语义一致。`<br>`val passed = if (def.op.equals("OR", ignoreCase = true)) hit.any() else hit.all { it }` |

改动内容仅两项：

1. `hit.all()` → `hit.all { it }`，补上 `all` 唯一重载所要求的谓词实参；
2. 行内加一条注释，说明 `all()` 无无参重载、`{ it }` 的语义，防止后续维护者再次删成裸 `all()`。

### 3.2 同 `when` 其它分支核对结论

逐分支复核了 `Outcome(...)` 全部 15 处构造点（含 COMPOUND 的 VICTORY / DEFEAT 两句），实参均为 3 个位置实参，顺序与签名 `(status: BattleStatus, winnerSide: String?, reason: String)` 一一对应，不存在派单假设中的「错位」，**一律未改**。

## 四、判定逻辑未变的论证

1. **OR 口径未动**：`def.op` 忽略大小写等于 `"OR"` 时走 `hit.any()`，无参 `any()` 语义为「至少一项为 true」，与改动前一致。
2. **AND 口径等价**：改动前意图是 `all()`（全部命中才胜）；`hit: List<Boolean>`，`all { it }` 的谓词是恒等函数，谓词为 true 当且仅当元素本身为 true。故 `hit.all { it }` 与「`hit` 中所有元素均为 true」完全等价，不存在放宽或收紧。
3. **判定输入未动**：`results = def.sub.map { evaluate(...) }`、`hit` 的生成式、`turn > duration` 的回合耗尽判定、`Outcome` 的 `status` / `winnerSide` / 文案字符串全部原样保留。
4. **阈值未动**：未改 `duration`、`holdTurns`、`REACH_RADIUS_KM` 等任何常量或比较边界。
5. **未新增/删除分支**：七个 `VictoryType` 分支结构不变，COMPOUND 仍是 `passed → VICTORY`；`turn > duration → DEFEAT`；否则 `Ongoing`。

二阶 / 三阶影响排查：

- 全 `app/src/main/java` 穷举裸 `.all()` / `.none()`：其余命中（`AutoTask.all()`、`PlaceDict.all()`、DAO `.all()` 等）均为业务实体或 DAO 自有无参方法，不是 stdlib `Iterable.all`，不在本次范围，也不存在同类缺谓词问题。
- crossing 主源集内除本处外，无第二处裸 `.all()` / `.none()` / `.any()` 误用。

## 五、grep 证据

### 5.1 `Outcome(` 全部命中（定义 1 处 + 构造 15 处）

```
$ grep -n "Outcome(" app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt
16:    data class Outcome(
50:                    Outcome(BattleStatus.VICTORY, def.side, "目标「${def.target}」已被歼灭或投降")
52:                    Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，目标未被歼灭")
59:                    Outcome(BattleStatus.VICTORY, def.side, "据点已占领 $held 回合")
61:                    Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，据点未守住")
68:                    Outcome(BattleStatus.DEFEAT, def.side, "防御方已被击溃")
70:                    Outcome(BattleStatus.VICTORY, def.side, "撑过全部 $duration 回合，防御成功")
80:                    Outcome(BattleStatus.VICTORY, def.side, "目标已抵达逃脱点")
82:                    Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，目标未能脱身")
89:                if (reached) Outcome(BattleStatus.VICTORY, def.side, "我方军队抵达目标点")
90:                else if (turn > duration) Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，未能抵达目标点")
97:                    Outcome(BattleStatus.VICTORY, def.side, "敌方所有军队均已溃散")
99:                    Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，敌方尚未全溃")
108:                if (passed) Outcome(BattleStatus.VICTORY, def.side, "组合条件达成（${def.op}）")
109:                else if (turn > duration) Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，组合条件未达成")
125:    private val Ongoing = Outcome(BattleStatus.ONGOING, null, "推演进行中")
```

逐点核对：15 处构造均为「`BattleStatus` 枚举 + `def.side`/`null`（`String?`）+ 字符串字面量（`String`）」，3 个位置实参与签名 `(status, winnerSide, reason)` 个数、顺序、类型一致。

### 5.2 `predicate` 在 crossing 主源集的命中

```
$ grep -rn "predicate" app/src/main/java/io/legado/app/ui/map/crossing/
app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt:106:                // all() 无无参重载，必须显式给 predicate；{ it } 即“每项均为 true”，与 AND 语义一致。
```

除本轮新增注释外，主源集无任何名为 `predicate` 的声明或实参；`Outcome` 签名内亦无该形参（§2.2）。

### 5.3 改后落盘复核（COMPOUND 分支）

```
$ grep -n "hit.all\|hit.any" app/src/main/java/io/legado/app/ui/map/crossing/engine/VictoryChecker.kt
107:                val passed = if (def.op.equals("OR", ignoreCase = true)) hit.any() else hit.all { it }
```

### 5.4 相对 HEAD 的完整 diff

```diff
@@ -103,7 +103,8 @@ object VictoryChecker {
             VictoryType.COMPOUND -> {
                 val results = def.sub.map { evaluate(it, corps, turn, duration, captureHoldTurns) }
                 val hit = results.map { it.finished && it.status == BattleStatus.VICTORY }
-                val passed = if (def.op.equals("OR", ignoreCase = true)) hit.any() else hit.all()
+                // all() 无无参重载，必须显式给 predicate；{ it } 即“每项均为 true”，与 AND 语义一致。
+                val passed = if (def.op.equals("OR", ignoreCase = true)) hit.any() else hit.all { it }
                 if (passed) Outcome(BattleStatus.VICTORY, def.side, "组合条件达成（${def.op}）")
                 else if (turn > duration) Outcome(BattleStatus.DEFEAT, def.side, "回合耗尽，组合条件未达成")
                 else Ongoing
             }
```

## 六、红线遵守自查

- 仅改 `VictoryChecker.kt` 内 1 行可执行代码 + 1 行注释；`Outcome` 定义签名未动；判定逻辑与阈值未动。
- 未触碰 `IntentResolver` / `CombatResolver` / `MoraleSystem` / `CrossingShell` / `model` / `terrain`；`shell/CrossingRasterBridge.kt`、`terrain/RasterDecoder.kt` 未删改。
- 未执行本地 gradle / kotlinc 编译；未做任何 git 写操作（提交由总控收口）。

## 七、未验证项（如实声明）

1. **未本地编译，需 CI confirm**：按红线未跑 `compileAppDebugKotlin`；本轮仅做静态签名 / 实参类型 / stdlib 重载核对。CI 复跑后本条 `predicate` 报错是否消除，需总控在 CI 上确认。
2. 运行期行为（AND/OR 组合胜败、回合耗尽判负）本轮未执行单测，沿用既有判定路径，未在本轮重跑。
3. **跨模块交接（超出本工单红线范围，未改）**：测试源集 `app/src/test/java/io/legado/app/ui/map/crossing/engine/VictoryCheckerTest.kt`（当前为未跟踪文件）仍以 `pointHolders = ...`、`alliedSideIds = ...` 具名实参调用 `VictoryChecker.check(...)`；而主源集现签名为 `check(def, corps, turn, duration, captureHoldTurns)`，这两个形参已在历史重构中移除。若 CI 编译测试源集，可能另行报「No parameter with name 'pointHolders' / 'alliedSideIds' found」，需另立工单对齐测试与现签名（恢复形参或改测试），本轮未擅动。
4. 派单中「`Outcome` 第三形参为 `predicate`」的前提与仓库事实不符，本报告 §2 已留痕；若总控依据旧前提复核，请以 §2.2 的签名原文为准。
