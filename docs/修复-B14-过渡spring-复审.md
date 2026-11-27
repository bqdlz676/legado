> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-B14 · 过渡 spring / 自动推进节拍复审

## 一、任务与范围

- 缺陷：B14 过渡无 spring/easing，自动推进是固定 `delay(600ms)` 硬切（U12-F13：`CrossingShell.kt:447-452,65`）。
- 文件域白名单：`app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt`、`app/src/test/java/io/legado/app/ui/map/crossing/shell/`。未碰 `engine/**`、`model/**`、`terrain/**`、`help/sim/**`、`ui/book/read/**`。
- 方案选型：采用「动画跟随状态（幂等）」路线。状态先推进，弹簧/Crossfade 追新目标；不挂「动画播完再下一步」栅栏，避免与 duce 状态机深度耦合（止损条款允许的首选路线）。
- 基线核对：任务书写 HEAD `d9d37ff`，实际 `git log` 为 `4ff8abc`（B4 返工在飞）；本单未做任何 git 写操作。

## 二、改动清单（文件:行 + before/after）

### `CrossingShell.kt`（1019 → 1118 行，净 +99）

1. 自动推演节拍循环（before L73-82 / after L77-87）
   - before：`delay(AUTO_STEP_DELAY_MS)`，固定 600ms，单一常量。
   - after：`delay(beatDelayMs(snapshot.events, snapshot.turn))`；节拍由纯函数按事件档位 + 回合进度计算；单一协程逐回合驱动，终局 `break` 退出循环。
2. 回合切换过渡（after L96-107，新增）
   - `MapCanvas` 外包 `Crossfade(targetState = snapshot.turn, animationSpec = springFor(SpringRole.TURN_CROSSFADE))`，透明度跟随回合状态淡入淡出；旧快照在弹簧期间短暂保留，落位后替换。
3. 三处既有高亮弹簧改走角色工厂（参数口径不变，仅收口）
   - L364 对比行差异高亮：`compareSpring<Float>()` → `springFor(SpringRole.REVEAL_HIGHLIGHT)`
   - L554 意图改写高亮：`compareSpring<Float>()` → `springFor(SpringRole.INTENT_OVERRIDE)`
   - L668 面板选中 chip：`compareSpring<Float>()` → `springFor(SpringRole.PALETTE_CHIP)`
4. 节拍纯函数区（after L1012-1076，新增）
   - `BeatKind { COMBAT, EVENT, MARCH, QUIET }`；`BEAT_BASE_MS` 表：战斗 620 / 事件 540 / 行军 460 / 静默 320（ms），注释写明取值依据与适用条件。
   - `LONG_CAMPAIGN_TURN=50`、`BATCH_BEAT_FLOOR_MS=180`、`LONG_CAMPAIGN_COMPRESS=0.012`。
   - `classifyBeat(events): BeatKind`（关键词表驱动，空列表 → QUIET）；`beatDelayMs(events, turn): Long`（长局按斜率向地板压缩，回合 0/短局不压缩，负值钳到地板）。
5. 弹簧角色区（after L1078-1117，新增）
   - `SpringRole(dampingRatio, stiffness, rationale)` 四角色：REVEAL_HIGHLIGHT / INTENT_OVERRIDE / PALETTE_CHIP（REVEAL 口径 0.80/360）、TURN_CROSSFADE（BUBBLE 口径 0.55/300）；每角色注释「为什么这个阻尼/刚度」。
   - `springFor(role): SpringSpec<T>` 唯一工厂；Composable 内不再出现弹簧数值。
6. 删除旧符号（before L999、L1003/1006、L1013/1014、L1016-1019）
   - `AUTO_STEP_DELAY_MS=600L`、未用的 `ShellSpringDamping/Stiffness`、`CompareRowSpringDamping/Stiffness`、`compareSpring()` 全部移除，不留兼容层。

### `BeatAndSpringTest.kt`（新增，101 行，10 条 `@Test`）

覆盖：战斗档节拍 / 行军档 / 脚本事件档 / 混合事件取最高优先级 / 空事件静默档 / 回合 0 不压缩 / 长局批量压缩向地板收拢 / 深回合钳地板 / 静默档长局压缩 / 弹簧角色参数与组件库口径一致且理由非空。

## 三、grep 证据（改后真实输出）

```
$ grep -rn "delay(" app/src/main/java/io/legado/app/ui/map/crossing/shell/
CrossingShell.kt:85:            delay(beatDelayMs(snapshot.events, snapshot.turn)) 〔W-451 重贴锚：delay(beatDelayMs(…)) 现行第102行〕

$ grep -rn "600L\|delay(600" app/src/main/java/io/legado/app/ui/map/crossing/shell/
（0 命中）

$ grep -rn "tween(\|dampingRatio = 0\.\|stiffness = [0-9]" app/src/main/java/io/legado/app/ui/map/crossing/shell/
（0 命中：无 tween、无内联弹簧 magic number）

$ grep -rn "springFor(" app/src/main/java/io/legado/app/ui/map/crossing/shell/
CrossingShell.kt:101:  animationSpec = springFor(SpringRole.TURN_CROSSFADE),
CrossingShell.kt:364:  animationSpec = springFor(SpringRole.REVEAL_HIGHLIGHT),
CrossingShell.kt:554:  animationSpec = springFor(SpringRole.INTENT_OVERRIDE),
CrossingShell.kt:668:  animationSpec = springFor(SpringRole.PALETTE_CHIP),
CrossingShell.kt:1117: internal fun <T> springFor(role: SpringRole): SpringSpec<T> =

$ grep -rn "compareSpring\|AUTO_STEP_DELAY_MS\|ShellSpring" app/src/
（0 命中，旧符号清零）
```

## 四、二阶三阶影响

### 与 B10 双栏同步滚动的关系
`BranchCompareShell` 的两栏共用同一回合下标，本次未改其步进逻辑。`CompareCorpsRow` 高亮弹簧只是从旧 `compareSpring()` 改名到 `REVEAL_HIGHLIGHT` 角色，参数同源 REVEAL（0.80/360），双栏同回合步进时两侧弹簧参数一致、同步落位，不影响同步滚动。

### 与 B11 手势 spring 的关系
手势识别纯函数（`resolveGesture`/`dialIntent`/阈值表）一字未动。意图改写高亮弹簧改挂 `INTENT_OVERRIDE` 角色，仍取 REVEAL 口径；拖拽预览向量 `dialIntent` 不经过弹簧，跟手性不受影响。

### 快进 / 暂停 / 存档恢复时的动画状态
- 暂停：`autoPlay=false` 时 `LaunchedEffect` 协程取消，节拍 `delay` 挂起点一并取消，无残留睡眠协程；恢复后从当前 `snapshot` 继续。
- 快进：长局节拍压缩在 `beatDelayMs` 内完成，单一 while 循环逐帧驱动，不为每步另起协程 sleep；弹簧跟随状态 retarget。
- 存档恢复：壳层 `host` 为 `remember` 内对象，重置/换控制器时整体重建，`snapshot` 同步重置，Crossfade 以新 `turn` 重新淡入，无跨控制器动画状态残留。存档读取/双控制器装配属 B7、D24 接线范围，本单未接。

### 连带与长期
- 二阶：节拍关键词表与引擎日志措辞耦合，引擎改事件文案需同步关键词表（见交接）。
- 三阶：节拍基础值与压缩斜率目前为壳层常量；未来若加用户可调播放速度，应把这组表提到偏好层，而不是在 Composable 里再加分支。

## 五、反例与边界

1. **连点推进**：状态先推进、动画跟随新目标，无完成栅栏；快速连点时弹簧直接改追新目标，不会出现「动画未结束就进入下一回合」的错位。Crossfade 旧内容淡出期间被新目标替换。
2. **中途退出 / 暂停**：组合离开或 `autoPlay=false` 时协程随作用域取消；重置按钮重建 `host`，本地 `overrides`/`autoPlay` 清空，无悬挂动画状态。
3. **无事件回合**：`classifyBeat(emptyList()) = QUIET`，320ms 短节拍；长局同样向地板压缩，不堆时长。
4. **空军队 / 降级态**：空快照走既有兜底文案；节拍函数对空 `events` 列表安全返回，不抛异常。
5. **深回合压缩系数为负**：`compressed.toLong().coerceAtLeast(BATCH_BEAT_FLOOR_MS)` 钳底，不产出负节拍（测试 `turn=400` 守住 180ms）。
6. **未知事件措辞**：关键词表未命中时归 QUIET 档，按静默节拍掠过，不崩、不降效。

## 六、认知成本

- 新增概念两个：**节拍档位**（COMBAT/EVENT/MARCH/QUIET，按信息密度定时长）、**弹簧角色**（REVEAL 稳落位 / BUBBLE 允许 overshoot）。命名与表注释自解释，接手人调手感只改 `BEAT_BASE_MS` 与 `SpringRole` 两处。
- 删除旧名 `compareSpring` 与两个 `@Suppress("unused")` 弹簧常量，抽象层不增反降；旧符号在 `app/src/` 内已 0 引用。

## 七、未验证项

- **未编译**：沙箱禁 gradle/kotlinc，本单只写码 + grep 自检，编译与单测均在云端 CI 发生。
- 10 条单测未在本地运行；断言数值由公式手工复算（turn60 战斗 545ms、turn100 战斗 248ms、turn400 钳 180ms、turn80 静默 204ms）。
- 弹簧手感、Crossfade 在多棋子长局下的帧率未经真机/模拟器验证。
- 任务书基线 HEAD `d9d37ff` 与实际 `4ff8abc` 不一致，未核对差异内容（B4 返工在飞，属他人域）。

## 八、交接（发现但未改）

- 事件文案关键词表（`COMBAT/EVENT/MARCH_KEYWORDS`）与 `engine/CampaignController.kt` 日志措辞耦合；引擎调整事件文案时需同步本表，否则档位误判为 QUIET。属 `engine/**` 域，B4 在飞，未动手。
- 存档恢复后的动画状态复位、双控制器装配属 D24 架构层接线；壳层目前仅在 host 重建时自然重置。
- J5 其余 81 处 `@Suppress("unused")`、K3 其余 20 处 `delay(N)` 不在本文件域，未处理。
