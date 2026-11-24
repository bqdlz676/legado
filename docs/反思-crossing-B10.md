# 反思 · crossing B10 分支对比

## 1. 做了什么
在 main 上新增 diff 纯函数层（`SnapshotDiff` / `TurnDiff` / `CorpsDiff` / `DiffField`，CampaignModels.kt 235–332）+ 独立并排展示子组件 `BranchCompareShell`（CrossingShell.kt 209–381、654–659）+ 表驱动单测 `SnapshotDiffTest.kt`（11 例 + 1 例边界）。未改单快照壳状态机，未碰 engine/terrain 他人域。

## 2. 踩的坑 / 教训
- Compose 状态不要在组合体里回写：初版把 `turnIndex` 在 body 里 `coerceIn` 再赋值，改为 `rawIndex` 源状态 + 读取处派生钳制，避免组合期写状态。
- `coerceIn(0, maxTurns-1)` 在 `maxTurns=0` 时区间反转会抛异常，上限要先 `coerceAtLeast(0)`；按钮禁用挡不住代码路径，钳制本身必须兜。
- 容差常量（兵力 0.05、坐标 0.0005 度）是拍的工程值，写进了对象伴生常量而非散在函数里，方便后续按真机观感回调。

## 3. 我可能判断错的地方
- 「两栏共用一个回合下标」是对 DoD「独立步进 + 同步滚动」的一种解释；若产品口径要求两栏各自停在不同回合，需要拆成双下标 + 独立滚动，当前实现不支持。
- 容差阈值未经真实结算数据回测，0.05 兵力 / 0.0005 度可能偏松或偏紧，需 CI 跑真实战役回放后校准。
- 未编译、未跑测试，类型引用（尤其 Compose animation API 签名）只做了仓库内同款用法比对，存在未发现的编译风险。

## 4. 遗留未决项
- 据点归属差异（HOLDING）挂起，等 B7 `pointHolders` 合并进 main 后接入。
- `BranchCompareShell` 无入口接线：分支存档读取、双 controller 装配、与单壳切换，待 B7/B11/B14 合并派单。
- `runToEnd()` 序列不含第 0 回合快照，首回合对照口径待接线方处理。

## 5. 给下任的一句话提醒
接手前先回仓库复核：① `DiffField` 是否已有 B7 补的 `HOLDING`；② `BranchCompareShell` 是否已被入口调用、下标口径是否改成双栏独立；③ 容差常量是否被真机观感回调过。本反思不替代仓库现状。
