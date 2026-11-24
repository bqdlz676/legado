# 反思 · crossing B9 壳层门控（子代理交接棒）

## 1. 做了什么
基线 `fa13e48`；白名单 3 文件：`CampaignModels.kt`（CorpsView +2 可选字段）、`CrossingShell.kt`（盲区门控 / 虚线圈 / 文案 / 全域提示条 / NaN 哨兵 / 名册盲区文案）、`CampaignControllerTest.kt`（B9-10/11/12 三例记录级半径）。未碰引擎、未编译、未 git 写。

## 2. 踩的坑 / 教训
- 记录级老化在公开表面上没有现成接缝：`reports` 表与 `IntelRecord` 私有，不能直接注入已知 `seenDistCells`。绕法：载入后直接写 public 的 `corps.position/latLng` 确定性布位 + 传入平场 `HeightRaster` 保证通视恒真，首回合目睹落条后再把敌军拉到视野外老化。
- NaN 必须在进 `projection.toCell` 之前拦；`toInt()` 会把 NaN 钳到 `cell(0,0)` 左上角，只在绘子处判不够，线路路点和 `geoToOffset` 内都要兜。
- Compose `Canvas` 的 DrawScope 不是 Composable，不能在循环里 per-unit 开 `animateFloatAsState`；弹簧只能提升到外层一个 `blindAlpha` 统一驱动。

## 3. 我可能判断错的地方
- B9-10/11 的布位依赖合成平场栅格通视恒真、双方 HOLD 不动、11 回合内不解散；这些是代码静态推演，**沙箱没跑过**，CI 若红先查这三条假设。
- 模型 `observed` 默认值保留 `false` 未按任务书写成 `true`，属我对隐私口径的取舍，若总控要求 true 需回改。
- 引擎 `toView` 未回填两个新字段，壳层回读 `host` 内部出口；这是过渡接法，不是终态。

## 4. 遗留未决项
- 记录级负龄期归零、距离项封顶兵种视野两条无接缝直断言，挂交接（见修复报告 §七-3）。
- 盲区名册行手势通道未禁用（B11 域）。
- 新用例 CI 实跑绿档无记录；真机观感 / 性能未验。

## 5. 给下任的一句话提醒
接手前先回仓库复核：① `recordRadius` 公式与常量（`STALE_DRIFT_CELLS_PER_TURN=1`、`MAX_STALE_RADIUS_CELLS=8`、`visionRadiusFor`）有没有再改；② 引擎若已把半径 / 文案并入 `toView`，壳层的 `host.xxx()` 回读支路可删；③ 本反思未跑测试，期望值必须以 CI 实跑为准，别照抄。
