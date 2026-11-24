# 反思 · help/sim 事件触发 · B15 复审

1. **做了什么**：复核 `help/sim/EventTrigger.kt` 13 种补登 action 分支全覆盖与可观察结果；补复审注释口径（全局事件显式 no-op、注册表未枚举化的适用条件、意图型可观察结果）；重写 `EventTriggerTest.kt` 为 13 动作矩阵 + 9 边界用例，断言触发次数与状态增量。基线 HEAD `adde7c4`，分支实现沿用 `7e6b491`。

2. **踩的坑 / 教训**：
   - 任务书写「13 种」，实扫 JSON 是 17 种标签（5 既有 + 13 补登，另有未入 JSON 的 strengthDelta）；先跑脚本拿到真实全集再对账，别照任务书数字列清单。
   - 工作区有他人 crossing 在途改动（`git diff` 可见），改前先 `git diff --stat` 圈定自己的白名单文件，避免顺手碰他人域。
   - 规格切片路径是 `docs/落文-原文/落文-原文-06-....md`，任务书写成 `docs/落文-原文-06-....md`，先 `ls` 定位再读。

3. **我可能判断错的地方**：
   - 「13 种均无需降级 no-op」是对照切片 06 文本得出；切片未覆盖的战役史实动作（siegeEngine / navalRout 等）语义为推断，未经规格原文逐字背书。
   - crossing 侧是否重复计数属他人域推断，未读 crossing 源码核实。
   - 测试断言为静态推演，未编译未跑，CI 可能暴露字段 / 签名偏差。

4. **遗留未决项**：双栈是否合并；事件意图是否应压过规则裁决；折损比例数值口径；capture 军级 / 据点级语义；附录落点补登。均见复审报告 §8。

5. **给下任的一句话提醒**：动作注册表唯一入口是 `EventTrigger.kt` 的 `when (ev.action)`，新增 action 必须三处同改（help/sim when、crossing 枚举表、crossing when）并跑 `selfcheck-data.py` / `selfcheck-flow.py`；本报告的 crossing 结论必须回仓库复核，不能照抄。
