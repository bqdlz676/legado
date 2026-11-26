# 模板 · CI 后验收（第二道验收，绿了才算完成）

> 为什么有第二道：静态验收判的是"文档口径与编译面风险"，它没有编译能力；真正能证明"能跑"的只有 CI。第一道给"有条件通过"的单，必须在 CI 绿之后走这一道，否则永远只有一道没牙的验收。

## 一、取数（照抄命令，别手填）

1. 拿 HEAD 与 run：`git log --oneline -1`；`curl -s -H "Authorization: Bearer $TOKEN" "https://api.github.com/repos/bqdlz676/legado/actions/runs?per_page=4"`。
2. 判定绿红：只看 Verify（含 Aggregate & assert test totals）与 Screenshot 两个 workflow 的 conclusion；`Package Debug APK` 在 P0~P3 期恒 skipped，不算红。
3. 翻了就抓日志：`curl -sL -H "Authorization: Bearer $TOKEN" "https://api.github.com/repos/bqdlz676/legado/actions/jobs/<job_id>/logs"`，按 `e: file://`、`FAILED`、`failures=` 三类硬特征归因（与 `ci-red-rate.py` 同口径）。

## 二、判据（三值，不许自创）

- **PASS**：Verify success 且 Aggregate 报 failures=0 errors=0 skipped=0 且测试总条数 ≥ 3185；Screenshot success；本单改动面与第一道验收清单逐项对得上。
- **FAIL**：任一 workflow failure。此时第一道验收作废，必须回滚或补修，并登记到 `docs/台账-CI红率.md`（跑 `ci-red-rate.py --write`）。
- **有条件通过**：仅限"CI 还没跑到该 commit"（在飞/排队）；必须写明 run id 与预计判读时间，超过 2 小时未判读视同 FAIL。

## 三、必写内容

- 两个布尔：HEAD commit、CI 绿红（含单测全绿）。
- 若为 compile 类红：归到哪条机检没写（S1~S8 / 只读 setter / 无对应），并说明当晚落成什么。
- 若为 test 类红：指出失败用例名与断言口径，判断是"实现错"还是"测试写错"。
- 本单第一道验收里标"编译面待 CI"的风险项，逐条给结论（已验证 / 被 CI 抓出）。

## 四、归档

- 报告命名 `docs/验收-CI-<单号或主题>.md`，首行锚点卡指向前一份第一道验收报告。
- 台账回写：`docs/派单队列.md` 该单置 DONE；`docs/派单台账.md` 记 HEAD 与 run id。
