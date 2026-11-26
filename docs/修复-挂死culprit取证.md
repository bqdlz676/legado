# 修复-Q-269：CI 分片挂死 culprit 取证脚本

- 新增文件：`data_scripts/find-hang-culprit.py`（用例级 STARTED/终态配对取证器）；本报告；锚点卡 `docs/锚点/Q269-culprit取证.md`。
- 未触碰：`app/src/**`、既有 `check-*.py` 门禁、`app/build.gradle`；无 git 写操作；沙箱无 JDK，未跑 gradle / kotlinc / 单测，仅做 Python `py_compile` 语法校验与离线夹具自测。
- 锚点卡：docs/锚点/Q269-culprit取证.md

## 一、背景与事实锚点

- 现象：CI 分片 job 反复出现「45 分钟静默挂死」——Gradle 不产出任何 JUnit XML、无逐用例进度，人工只能下载 job 日志肉眼翻最后一条用例名。既往挂死记录见 `docs/诊断-shard2挂死定位.md:5`（run `45801836915` shard 2/4，2714s 命中 `timeout-minutes: 45`，窗内无逐用例进度行、无 XML 产物）。
- 取证前提已落地：`app/build.gradle:239-241` 的 `testOptions.unitTests.all` 内：

  ```groovy
  testTask.testLogging {
      events "STARTED", "FAILED"
      showStandardStreams = false
  }
  ```

  实测 shard 日志中 `STARTED` 行 830 条，形态为 `io.legado.app.XxxTest > 用例名 STARTED`（本单任务书实测事实）。
- 既有件排查（文档子代理必读 §1：先证不是重复造）：
  - `data_scripts/fetch-ci-log.sh`：run 级日志 zip 下载 + 编译错/单测失败/资源打包错三类归因，不做用例级事件配对，定位不到「最后开始但没走完」的用例。
  - `data_scripts/watch-ci.py`：轮询 run 绿红 / 在跑状态，不解析 job 日志内的用例事件。
  - 缺口即本单所补：把「肉眼找 culprit」变成一条命令，按事件配对点名未闭环用例。

## 二、脚本能力与入口

脚本：`data_scripts/find-hang-culprit.py`，三入口：

1. `--job-id <id>`：直取单个 job 日志 `https://api.github.com/repos/bqdlz676/legado/actions/jobs/<job_id>/logs`。
2. `--run-id <id>`：先列 run 下全部 job（`/actions/runs/<run_id>/jobs?per_page=100`），逐个取日志分别出结论；单个 job 401 不中断其余 job。
3. `--log-file <path>`：离线兜底，直接读本地已下载日志。
4. 可选 `--top N`（默认列最后 10 条嫌疑）、`--repo owner/name`、`--selftest`。

凭据与 HTTP：

- token 只读 `~/.git-credentials` 里 `https://<user>:<token>@github.com` 条目；token 不回显、不落盘、不写进报告。
- HTTP 走 `urllib`；跳转签名 URL（`results-receiver...`）时剥离 `Authorization` 头，等价于 `curl -sL` 而**不加** `--location-trusted`（重发 token 到跨主机签名 URL 会被 403）。
- 在跑 job 的 logs 接口返回 401：归类为 `LOGS_UNAVAILABLE`，打印「日志尚不可取，job 仍在跑（或日志已过保留期）」，退出码 3，不抛栈崩掉；凭据缺失退出码 4；其他 HTTP 错误打印状态码并退出码 2。

配对口径（事件正则 `^<类名> > <用例名> (STARTED|PASSED|FAILED|SKIPPED)$`，先剥 ISO 时间戳前缀）：

- 每个 `(类名, 用例名)` 维护一个 LIFO 栈：`STARTED` 压栈；`PASSED/FAILED/SKIPPED` 弹出最近一次 `STARTED`。
- 日志末尾仍在栈上的用例即「STARTED 后无终态」嫌疑；按 STARTED 行号升序，输出最后 N 条，每条给类名、用例名、STARTED 行号、其后到日志末尾的行数。
- 另输出全日志最后一个已完成用例（最后一条终态事件），作为「挂死前最后活着的点」。
- 重复 `STARTED`（并行交错 / retry 重跑）单列警告；无开放 `STARTED` 的孤儿终态单列，供人工核对交错情况。

四种诊断状态（任务书 §3 三态 + 配置缺口一态）：

- `NO_STARTED`：全文无一条 `STARTED` → testLogging 未生效，或该 job 没跑到单测阶段；附核对建议。
- `FORMAT_MISMATCH`：有 `STARTED`，终态关键词出现过但正则对不上 → 事件格式与假设不符；打印前 20 条原始样例行供对口径。
- `WARN_STARTED_ONLY`：有 `STARTED`、全文无任何终态关键词 → 当前 `testLogging` 只开了 `STARTED+FAILED`，绿跑 `PASSED` 不进日志；未闭环列表混着实际通过的用例，脚本显式标注不可直接当挂死嫌疑。
- `LOGS_UNAVAILABLE`：401，job 仍在跑或日志过期。

## 三、拿到一次真挂死日志后的 3 步定位

第 1 步：从 job 页或 run 的 jobs 列表拿到 `job_id`；在 shell 里读 token（token 字符串不打印、不贴报告）：

```bash
TOKEN=$(python3 -c "import re,os;[print(m.group(1)) for l in open(os.path.expanduser('~/.git-credentials')) if (m:=re.match(r'https://[^:@\s]+:([^@\s]+)@github\.com',l.strip()))]")
```

第 2 步：拉日志（`-L` 跟随跳转，但**不要**加 `--location-trusted`；跨主机签名 URL 重发 token 会 403/401）：

```bash
curl -sL -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/bqdlz676/legado/actions/jobs/<job_id>/logs" \
  -o /tmp/hang-job.log
```

若返回 401：job 仍在跑，等收尾后重拉；离线已有日志则跳过本步。

第 3 步：跑取证器看嫌疑：

```bash
python3 data_scripts/find-hang-culprit.py --job-id <job_id>
# 或离线：
python3 data_scripts/find-hang-culprit.py --log-file /tmp/hang-job.log --top 10
```

读法：先看「状态」与「最后已完成」；「挂死嫌疑」段列出的是 STARTED 后再无终态的用例，按行号倒序取末尾几条，结合其类名回到测试源文件查阻塞面（对照 `docs/诊断-shard2挂死定位.md` §四 的阻塞特征清单）。状态为 `WARN_STARTED_ONLY` 时，嫌疑列表只作线索，不能直接定罪。

## 四、反例推演：并行 fork 与 retry 重跑

反例前提：Gradle 并行执行（`maxParallelForks > 1`）时多 worker 日志交错；同一用例也可能因 retry 串行重跑。若配对逻辑按「类名+用例名」唯一键做单值覆盖：

- 并行交错：A 轮 `STARTED` 未终态，B 轮 `STARTED` 又来了，唯一键后写覆盖先写，A 轮的开放记录被吞——真正挂死的那一轮可能消失。
- retry 重跑：第一轮 `STARTED` 后失败重跑，第二轮 `STARTED` + `PASSED` 闭环；唯一键若见终态即把整键判闭环，第一轮残留被静默清掉，误报「全绿」。

本单处理方式：

- 按行序处理，同键用栈（LIFO）而非单值：终态出现只清除最近一次 `STARTED`，旧记录继续留到末尾参与未闭环判定。
- 同键已有未闭环 `STARTED` 时再收到 `STARTED`：记入「重复 STARTED 警告」（带行号与此前开放次数），不提前清栈。
- 终态到来时栈为空：记入「孤儿终态」列表，不静默吞掉。
- 二阶影响：retry 最终成功时，较早那一轮 `STARTED` 会残留为一条未闭环，出现在嫌疑列表尾部；脚本用重复 STARTED 警告提示人工甄别，而不是替它静默闭环——清掉它会漏掉真正挂死的那一轮，两害相权保留可见性。
- 三阶影响：将来若 `testLogging.events` 补 `PASSED/SKIPPED`，配对口径自动收紧，脚本正则与状态机无需改动；`WARN_STARTED_ONLY` 分支仅在终态关键词全缺时触发。

## 五、自测与机检真实输出

### 5.1 Python 语法校验（沙箱无 JDK，不涉及 Gradle 编译）

```text
$ python3 -m py_compile data_scripts/find-hang-culprit.py
py_compile RC=0
```

### 5.2 离线夹具自测（`--selftest`，三份 /tmp 夹具跑完自删）

夹具内容与判定：

1. `normal.log`：4 个用例，`PASSED/FAILED/SKIPPED` 全部闭环 → 状态 `CLEAN`，未闭环 0 条；最后已完成识别为 `evalErrorIncludesSourceContext FAILED`。
2. `hang.log`：5 个用例闭环后，末尾 2 条 `STARTED` 再无终态（`recordKeepsLastReadChapter`、`layoutBreaksLongLine`）→ 状态 `HANG_SUSPECT`，未闭环集合精确点名这 2 条；最后已完成识别为 `listRoutesRequiresAuth PASSED`；`after_lines` 按总行数减 STARTED 行号核算。
3. `no_started.log`：纯编译失败行，无 `STARTED` → 状态 `NO_STARTED`。
4. 附加反向抽查：同键两次 `STARTED` + 一次 `PASSED` 的 retry 片段 → 重复 STARTED 警告 1 条，LIFO 残留 1 条未闭环。

真实末行：

```text
$ python3 data_scripts/find-hang-culprit.py --selftest
PASS 3/3 selftest 夹具全判定正确：normal 全闭环 / hang 点名末尾 2 条未闭环嫌疑 / no-started 正确归类；retry 重复 STARTED 走 LIFO 残留校验通过；夹具已自删。
selftest RC=0
```

### 5.3 门禁机检

锚点卡守卫（`--strict`）：

```text
$ python3 data_scripts/check-anchor-card.py --strict docs/修复-挂死culprit取证.md
WARN  修复-挂死culprit取证.md：次要提及 F-01 无锚点卡（若为已收口并入号可忽略）
汇总: PASS=1 FAIL=0（共 1 份）
RC=0
```

新发现问题门禁：

```text
$ python3 data_scripts/check-problem-finding.py docs/修复-挂死culprit取证.md
PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）
RC=0
```

回执块机检（单文件直跑）：

```text
PROBLEMS: []
RESULT: PASS
```

说明：anchor 的 WARN 为次要提及编号 F-01（调度条款号，非缺陷号），按门禁 R2 仅提示不判红。

## 六、新发现问题

1. 当前 `app/build.gradle:240` `events "STARTED", "FAILED"` 不含 `PASSED/SKIPPED`，绿跑用例的终态不进日志；本脚本在该口径下只能靠 STARTED 线索排查，未闭环列表会混入实际通过的用例（`WARN_STARTED_ONLY` 分支已标注）——待裁：是否在另一单把 `PASSED, "SKIPPED"` 补进 events；本单按任务书边界不碰 build.gradle。
2. 挂死若发生在测试类静态初始化块 / 单例首次装配期，用例级 `STARTED` 不会出现，脚本落到 `NO_STARTED` 或 `WARN_STARTED_ONLY`，需结合 discover 类清单与类首行日志另查——已入队：下一轮真挂死取证时核对日志中最后一个加载的测试类。
3. logs 接口 401 同时覆盖「job 在跑」与「token 权限不足/日志过期」两种情形，单看状态码无法区分；脚本已拆分出口径：凭据缺失单独退出码 4 并提示，非 401 的 HTTP 错误单独打印状态码，401 才归「日志尚不可取」——已闭环。

## 七、边界与未验证项

- 未对真实在跑 / 已挂死 job 实跑取数：本轮沙箱无可取的挂死日志，配对口径以三份离线夹具与手工构造的交错片段验证；真日志首次命中时若格式漂移，走 `FORMAT_MISMATCH` 样例行对口径。
- `--run-id` 模式遍历全部 job，不含 XML 产物比对；XML 缺失判定仍由 Q-220 产物链与既有归因脚本承担。
- 本单不新增门禁：脚本是取证工具，不挂入 `rule-gate.sh`；是否转为机检判据由后续单决定。
- 索引登记（`docs/索引-文档.md` GEN 区间）由文档索引生成器下次重跑自动收录，本单不手改生成区间。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「**锚点强制**：每条结论必须带 `文件:行` 或 `commit sha`；下次要用直接 `sed -n 'X,Yp'` / `git show sha` 回源（回源成本 ≪ 记忆成本）。」（docs/机制-总控必读.md:108）
- 逐字引用 2：「**禁 git 写操作**：不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 逐字引用 3：「**禁本地编译**：不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。」（docs/机制-子代理必读.md:12）
- 适用条款：F-01 两布尔回报（本单无 commit / 无 CI 重跑，只报脚本与夹具自测事实）；锚点强制回源；只读分析不改 `app/` 与既有门禁；数量类结论给命令 + 真实输出；证据不足处写明缺口（§七）。
