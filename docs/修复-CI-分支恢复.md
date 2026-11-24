# 修复 · CI 红：分支存档/归属 + 感知去重（5 例）

- 基线：main HEAD `82e1afc`；沙箱禁编译，结论为静态走查 + 同口径 Python 仿真复现，待 CI 复核。
- 改动生产：`engine/CampaignController.kt`、`engine/IntentResolver.kt`；未改任何测试、未删用例、未加 `@Ignore`。

## 失败行 → 根因 → 修法 → 改后行为

| 用例/行 | 根因 | 修法 | 改后行为 |
| --- | --- | --- | --- |
| B7-1 `:32` 归属表空 | `HOLD` 驻点守军被意图裁决规则 9（抢占空关隘 SEIZE）/规则 7（三倍优势 ADVANCE）抢先，首回合即沿寻路离点；仿真：a1 自胜利点移 2 格、距点 9.47 km > 3 km 半径，`buildPointHolders()` 落空；`captureHold` 写入 0 故计数表非空、归属表为空。 | `IntentResolver` 规则 7/9 增加 `corps.intent != Intent.HOLD` 闸门；HOLD 建制只在接敌/溃散/被围等生存威胁下易地，机会主义机动不拖离防地。 | a1 原地据守，首回合归属表 `{据点key: "a"}`、计数 1；存档/恢复逐字段往返一致。 |
| B7-2 `:51` 存档 A 无据点标识 | 同上：存档时归属表已空，`pointHolders.keys` 取空。 | 同上（Resolver HOLD 闸门）。 | 分支 A 归属 `a`、分支 B 归属 `b`；恢复 A 不串成 B，计数各自回写。 |
| B7-3 `:92` 旧档重建后 key 空 | 同上：旧档按恢复后军位重建时，军位已离点，重建表为空。 | 同上；旧档缺两表仍走 `restorePointHolders` 从军位重建、计数基线 1（逻辑不变）。 | 重建归属为 `a`，计数基线 1；随后推进/跑满回合不崩。 |
| B7-6 `:143` 计数未按存档值 +1 | 同上：恢复后再推进，守军已离点，`captureHold` 被置 0 而非 `存档值+1`。 | 同上（Resolver HOLD 闸门）。 | 恢复后守一回合：计数 = 存档值 +1，占据方不变，不翻倍。 |
| B9-7 `:298` 感知集条目不符 | 仿真复现为 `count=0`（非重复）：DUO 同格 2v1，b1 一回合内被 a1、a2 接连两场遭遇，士气被连降两级 STEADY→WAVERING→BROKEN，士气阶段即 `disband()`，掉出 `enemiesOf`/感知集。另按题意在感知出口按 id 去重兜底。 | ① 新增回合级 `moraleDowngradedThisRound`：同一军队一回合交战至多降一级士气（事件直置 BROKEN 不经此表，解散语义不变）；`step()`/`restoreBranch()` 清零。② `perceivedEnemies` 末尾 `distinctBy { it.id }`，同敌只落一条，保留首条，不用 slice/limit 糊断言。 | b1 一回合仅降一级至 WAVERING、存活在感知集；同敌 id 唯一；报告不重复落条。 |

## 穷举排查记录（均已排除）

- `step()` 已写 `captureHold`/`pointHolders`（tracker 每回合 clear+重建）；`saveBranch()` 两表 `toMap()` 拷贝；`restoreBranch()` 整表回写、旧档从军位重建——往返链路无漏拷。
- key 口径：`pointKey`（lng,lat 四位小数）controller 与 VictoryChecker 一致，无 restore 后对不上。
- 解析层：side/pos/point 字段名与模型一致，非空进入。

## 自检

- 括号配平：`CampaignController.kt` `{}()[]` 差 0；`IntentResolver.kt` 差 0（脚本剥离串/注释后统计）。
- 写入回证（grep 行号）：
  - `captureHold`：`Controller.kt:96` 成员；`:639` 写入；`:726` 存档；`:738-739` 恢复回写；`:948` 旧档基线。
  - `pointHolders`：`:112` 成员；`:230` 开局播种；`:635/640` tracker；`:727` 存档；`:740` 恢复；`:939/941/945` 重建。
  - `perceivedEnemiesOf`：`:810` 出口；`:517` `distinctBy { it.id }`。
  - 士气降级表：`:124` 成员；`:267` 回合清零；`:581/:583` 闸门写入；`:746` 恢复清零。
  - Resolver HOLD 闸门：`IntentResolver.kt:80`（ADVANCE）、`:98`（SEIZE）。
- 未触碰：`terrain/**`、`ui/card/**`、`ui/book/**`、`ui/main/**`；测试文件零改动。

## 二阶/三阶影响与边界

- HOLD 闸门只挡 ADVANCE/SEIZE 两类机会主义机动；FIGHT/ROUT/BREAKOUT/AMBUSH/RETREAT/SUPPORT 优先级不变，盟友被攻仍转 SUPPORT（B3 支援用例不受影响）。
- 士气每回合至多降一级：事件 `MORALE_CHANGE` 直置 BROKEN 仍当回合解散（TRIGGER 用例不变）；疲劳 >4 降级仍走 MoraleSystem，不在本闸门内。
- 未编译/未跑单测（沙箱限制）；符号、泛型、重载匹配为静态推断，红绿以 CI `testAppDebugUnitTest` 为准。
