# 锚点卡 · Q-269 CI 分片挂死 culprit 取证脚本

> 对象：`data_scripts/find-hang-culprit.py`（新增）；回执报告 `docs/修复-挂死culprit取证.md`。本卡对应派单 Q-269，承接 Q-243 `docs/诊断-shard2挂死定位.md` §五-1「挂死用例名不可定位，待后续取证」的缺口。

## 一、事实锚点（逐字，含 文件:行）

- 挂死既往记录：`docs/诊断-shard2挂死定位.md:5` —— run `45801836915` shard 2/4 `10:34:42Z → 11:19:56Z`（2714s / 45m14s，命中 `verify.yml:213` `timeout-minutes: 45`），窗内无逐用例进度行、无 XML 产物。
- 用例级日志前提：`app/build.gradle:239-241`：

  ```groovy
  testTask.testLogging {
      events "STARTED", "FAILED"
      showStandardStreams = false
  }
  ```

- 实测事件行形态：`io.legado.app.XxxTest > 用例名 STARTED`；实测 shard 日志 `STARTED` 行 830 条（任务书实测事实）。
- 既有同类件：`data_scripts/fetch-ci-log.sh`（run 级 zip 三类归因，无用例级配对）；`data_scripts/watch-ci.py`（绿红轮询，不解析用例事件）。
- logs 接口事实：在跑 job 的 logs 接口返回 401；跳转为签名 URL 后重发 GitHub token 会被拒，等价 curl 不加 `--location-trusted`。

## 二、判据（逐条可断言）

1. 三入口齐备：`--job-id`、`--run-id`、`--log-file`；`--top N` 默认 10；`--selftest` 跑三份夹具并自删，末行 `PASS 3/3 ...`。
2. 配对只认四类事件 `STARTED/PASSED/FAILED/SKIPPED`；未闭环用例输出类名、用例名、STARTED 行号、其后到末尾行数；另输出最后一个已完成用例。
3. 三态区分：无 `STARTED` → `NO_STARTED`；有 `STARTED` 终态对不上格式 → `FORMAT_MISMATCH` 并打前 20 条原始样例；401 → `LOGS_UNAVAILABLE`（job 仍在跑/日志过期），不崩栈。
4. 并行 / retry 反例：同键多 `STARTED` 走 LIFO 栈，终态只弹最近一次；重复 `STARTED` 与孤儿终态分别列警告，不按唯一键单值覆盖。
5. token 只读 `~/.git-credentials` github.com 条目，不回显、不落盘、不进报告。
6. 边界：不新增门禁、不改 `app/src/**`、不改既有 `check-*.py` 与 build.gradle；无 git 写；沙箱无 JDK，不跑 gradle/kotlinc。

## 三、边界与取舍

- 当前 events 只开 `STARTED+FAILED`，`PASSED/SKIPPED` 不进日志；脚本以 `WARN_STARTED_ONLY` 标注此口径，未闭环列表只作线索，不直接定罪；补 events 属另一单（报告 §六-1，状态待裁）。
- 类加载期 / 静态初始化阻塞不会产生用例级 `STARTED`，脚本对此只能落到 `NO_STARTED` 分支，需另查 discover 清单与类首行日志。
- `--run-id` 模式逐个 job 取日志；单个 job 401 标注后继续其余 job，不整体中断。

## 四、验收方式

- 静态回证：`python3 -m py_compile data_scripts/find-hang-culprit.py` RC=0；`python3 data_scripts/find-hang-culprit.py --selftest` 末行 `PASS 3/3 ...` RC=0。
- 夹具断言：normal 全闭环 0 未闭环；hang 精确点名末尾 2 条无终态用例；no_started 归 `NO_STARTED`；retry 片段重复 STARTED 警告 1 条、LIFO 残留 1 条。
- 云端复验（本单不执行）：下一轮挂死 job 收尾后，`--job-id` 取日志，嫌疑段应点名最后 STARTED 的 1~数个用例；若日志为 `WARN_STARTED_ONLY`，先补 events 再配对。
