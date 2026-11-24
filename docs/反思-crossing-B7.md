# 反思 · crossing B7 分支存档收口

1. **做了什么**：在 main `4ff8abc` 上为 `CampaignController` 补据点归属表运行态成员，打通 `captureHold`/`pointHolders` 写入—存档—恢复—判定全链路；新增 `CampaignBranchIsolationTest.kt` 7 条隔离 / 降级用例；报告 `docs/修复-B7-分支存档收口.md`。

2. **踩的坑 / 教训**：
   - wip 半成品与 main 已漂移：main 的 B9 把情报改成「恢复后按军位重播种」，wip 的 `IntelRecordSnapshot`/`snapshotIntel`/`restoreIntel` 绝不能嫁回来。先逐符号比对 main 现状再动手，否则会回退 B9 语义。
   - 旧 `restorePointHolders` 用军 id 写计数表 key，与计数表的据点标识 key 命名空间错配，还 `clear()` 清掉刚回写的计数——嫁接时不能照抄 wip 思路，要先看 VictoryChecker 实际消费的 key 口径。
   - 测试断言里 `Map[key]` 返回可空，`assertEquals(int, Int?)` 有拆箱 NPE 风险，统一补 `?: 0`。

3. **我可能判断错的地方**：旧档计数基线取 1 是工程取舍（旧档无从得知历史占回合数），未与规格原文核对；开局播种归属表一行是否必要，取决于生产上是否存在「首回合前存档」的调用，当前仅测试可达。

4. **遗留未决项**：未编译、未跑单测，红绿未知；B9 两个测试文件 6 处 `!!` 未改（越界挂交接）；B10 接线前 save/restore 无生产调用点；多线程并发存档未加锁。

5. **给下任的一句话提醒**：接手前先 `grep -n "pointHolders\|captureHold"` 回仓库核对行号（本反思行号会漂）；据点两表 key 必须与 `VictoryChecker.pointKey` 同口径，禁止再按军 id 写键。
