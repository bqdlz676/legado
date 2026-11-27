锚点卡：docs/锚点/Q472-CI车道.md

# 修复-CI车道分流收口（Q472）

## 守则回执
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 适用条款：禁 git 写（未 add/commit/push/checkout）；禁本地编译（未跑 gradle/kotlinc/assemble，仅 py_compile 与门禁脚本）；改动后 grep 引原文回证；每条规则写简短注释解释为什么；不碰 app/**；报告逐条陈述不用表格。

## 一、本轮收口的三件事

1. `Package Debug APK` 在连续推送下被并发取消：`3cf24a5` 同批次两次 `cancelled`、一次 `success`，两次 cancelled 均未分配 runner（jobs 端点 `total_count=0`）。
2. `.github/workflows/package.yml` 触发条件未接车道判据：上游 Verify 在 docs-only 车道也由 lane-summary 给出显式 `success`，Package 只看 `conclusion == success` 就拉起 APK 长构建，docs-only 推送白白占出包车道。
3. `data_scripts/check-symbol-existence.py` 的 `main()` 不解析 `--changed`：参数传进来被直接忽略，全仓扫描口径无法按改动面收窄。

改造范围硬约束：本单写入的代码文件仅 `.github/workflows/package.yml`、`data_scripts/check-symbol-existence.py`；`verify.yml` 逻辑未改；本单未编辑任何 `app/**`、`modules/**`、`screenshot/**`、`third_party/**` 文件（工作区里 `app/` 下现存的改动是并发其他工作区产物，不在本单写入清单内）。

## 二、取证（改造前现状原文 + API 原文）

### 2.1 两个 workflow 的触发段、并发组、路径过滤现状

1. Package 触发段（改造前 HEAD `b4aaf26` 原文，现行 `.github/workflows/package.yml:3`）：`on:` 下只有 `workflow_run: workflows: ["Verify (unit tests)"]; types: [completed]` 与手动 `workflow_dispatch`。`workflow_run` 事件本身不支持 `paths` 路径过滤，车道判据只能在 workflow 内部补。
2. Package 并发现状（改造前 HEAD 原文）：`concurrency.group: package-${{ github.ref }}`，`cancel-in-progress: false`；旧注释只写了"打包长作业不许被新推取消"一句，未写清组键语义、排队/取消取舍与用户视角保证。
3. Verify 触发段（`.github/workflows/verify.yml:4`）：`push: branches: [main]`、`pull_request: branches: [main]`、`workflow_dispatch`；workflow 层同样没有 `paths` 块，车道判据在 `classify-paths` job 内部做。
4. Verify 并发（`.github/workflows/verify.yml:23`、`.github/workflows/verify.yml:27`）：`group: verify-${{ github.ref }}`，`cancel-in-progress: false`（排队，保证据链）。
5. Verify 车道判据白名单（`.github/workflows/verify.yml:95` 起，改造后 Package 逐字对齐）：
   - 码变前缀：`app/`、`modules/`、`screenshot/`、`third_party/`、`gradle/`、`buildSrc/`、`.github/`。
   - 码变精确文件：`build.gradle`、`settings.gradle`、`settings.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`.versioncode`、`package.json`。
   - 被 workflow 直接调用的数据脚本：`data_scripts/emit-shard-lists.py`、`data_scripts/frame-manifest.py`。
   - 后缀规则：`.gradle` / `.gradle.kts` 结尾一律按码变。
   - 非码变域：`docs/`、`data_scripts/`、任意层级 `*.md`；优先级先判码变白名单，落在 `app/` 等目录下的 `*.md` 仍按码变。

### 2.2 `3cf24a5` 同批次 cancelled 取证（GitHub API 原文，token 取自 `~/.git-credentials`，未打印 token）

提交全量 SHA：`3cf24a551da6f023671aef8dac91dab5d4b24ba2`（短号 `3cf24a5`，docs(q458) 独立验收）。

1. Verify run `46009459786`（run_number 212，event=push，head_branch=main）：`status=completed`，`conclusion=success`，`created_at=2026-11-27T13:42:43Z`，`updated_at=2026-11-27T13:44:42Z`。9 个 job 中：Classify changed paths `success`；Static gate `success`；Discover / 4 个 Unit tests shard / Rhino eval / Slow bench / Aggregate 全部 `skipped`；Lane summary `success`。即这是一条 docs-only 车道 Verify，测试链整链跳过，仍给出 success 结论。
2. Screenshot run `46009459776`（run_number 218）：`status=completed`，`conclusion=success`，`created_at=13:42:43Z`，`updated_at=13:46:09Z`，1 个 job `success`。
3. Package run `46009476318`（run_number 205，event=workflow_run，head_sha=`3cf24a5…`）：`status=completed`，`conclusion=cancelled`，`created_at=2026-11-27T13:42:50Z`，`updated_at=2026-11-27T13:44:18Z`；`/actions/runs/46009476318/jobs` 返回 `JOBS total_count = 0`——未分配 runner、未起任何 job 即被取消。
4. Package run `46009678151`（run_number 206，event=workflow_run，head_sha=`3cf24a5…`）：`status=completed`，`conclusion=cancelled`，`created_at=2026-11-27T13:44:16Z`，`updated_at=2026-11-27T13:44:46Z`；`/actions/runs/46009678151/jobs` 返回 `JOBS total_count = 0`。
5. Package run `46009744245`（run_number 207，event=workflow_run，head_sha=`3cf24a5…`）：`status=completed`，`conclusion=success`，`created_at=13:44:45Z`，`updated_at=14:00:59Z`；2 个 job：`Build & verify APKs (shard path)` `success`（started 13:50:20Z，completed 14:00:58Z），baseline path `skipped`。
6. 时序配对（API 时间戳原文）：206 创建于 `13:44:16Z`，205 终态 `13:44:18Z`；207 创建于 `13:44:45Z`，206 终态 `13:44:46Z`。后一条同组 run 创建后 1~2 秒，前一条置 cancelled，呈同组顶替时序特征。
7. 诚实标注：GitHub REST 的 runs/jobs 端点不暴露取消操作人、取消原因自由文本字段；本单能回源的原文只有 `conclusion=cancelled`、`total_count=0` 与上述时间戳。"同组顶替"是时序推断，不是 API 直给结论；既有配置写的是 `cancel-in-progress: false`，false 下仍观测到 cancelled 的残留机制疑问列入第八节 N2，待云端复验。
8. 对照前序报告遗留：`docs/修复-CI路径分流提速.md` §九.1 已登记"Package 触发条件未接车道判据，docs-only 推送也会拉起 APK 构建"；本轮 2.2 第 1 条取证复现了该遗留的现场——docs-only Verify success 后 Package 车道被拉起。

## 三、改造一：Package 触发条件接车道判据

### 3.1 路径清单（必须出包的构建面 / 可跳过的非构建面）

1. 必须拉 APK 构建（任一命中即 `code_changed=true`）：
   - `app/**`（含 `app/src/main/assets/**` 资产面，被 `app/` 前缀覆盖）、`modules/**`、`screenshot/**`、`third_party/**`。
   - `gradle/**`、`buildSrc/**`、`.github/**`（CI 配置与 composite action 改动本身要重新出包验证）。
   - 构建面精确文件：`build.gradle`、`settings.gradle`、`settings.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`.versioncode`、`package.json`。
   - 后缀 `.gradle` / `.gradle.kts` 的任意文件。
   - 两个被 workflow 直接调用的数据脚本：`data_scripts/emit-shard-lists.py`、`data_scripts/frame-manifest.py`。
2. 不拉 APK 构建（非码变车道）：`docs/**`、`data_scripts/**` 内除上述两个 CI 调用脚本外的其余脚本、任意层级 `*.md`。

### 3.2 判定逻辑（package.yml 新增 `classify-paths` job，`.github/workflows/package.yml:48`）

1. 为什么在 Package 侧再判一次：`workflow_run` 事件拿不到 Verify job 输出，Verify 的 `code_changed` 结果跨 workflow 不可读；Package 只能基于自身检出提交的 diff 复算车道。判据白名单与 `.github/workflows/verify.yml:95` 逐字对齐，两条车道对同一批文件给出同一结论，防止 verify 判 docs-only、package 判 code 的口径分裂。
2. 取 diff 口径：`workflow_dispatch` 手动触发保守按码变全量跑（baseline 对照/补跑不享受分流跳过）；`workflow_run` 事件载荷没有 `before` 字段，取触发提交 `HEAD~1..HEAD` 的 `--name-only` diff；取不到父提交（首推/浅克隆）时空集保守落 `code`，不允许跳过出包链。
3. 分流输出：`code_changed=true/false` 与 `lane`（`code` / `scripts-only` / `docs-only`）写入 `GITHUB_OUTPUT`。
4. 改造前 `build-shard` 闸门原文（HEAD `b4aaf26` 旧版；行号为改造前快照，现行行号以 `.github/workflows/package.yml:134` 为准）：
   `if: ${{ (github.event_name == 'workflow_dispatch' || github.event.workflow_run.conclusion == 'success') && (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) }}`
   ——只判上游结论，不判改动面。
5. 改造后 `build-shard` 闸门（`.github/workflows/package.yml:133`、`.github/workflows/package.yml:134`）：`needs: [classify-paths]`，`if:` 在原结论闸门之上追加 `&& needs.classify-paths.outputs.code_changed == 'true'`。上游非绿 Verify 依旧不拉包；docs-only / scripts-only 的绿 Verify 也不再拉包。
6. 新增非码变汇总 job `lane-summary`（`.github/workflows/package.yml:331`）：`needs: [classify-paths]`，`if: ${{ github.event_name != 'workflow_dispatch' && needs.classify-paths.outputs.code_changed == 'false' }}`；校验 `lane` 只在 `docs-only` / `scripts-only` 枚举内，否则 `::error::` 退出。needs 只挂恒存在的 classify，不依赖会被整链跳过的 build-shard，与 Verify 侧 `lane-summary` 同口径。
7. 本单未使用 `continue-on-error`、未使用 `if: always()`；classify 失败时 lane-summary 随 needs 失败传播，不产出放行结论。

## 四、改造二：并发策略口径定稿

1. 组键取值：`group: package-${{ github.ref }}`（`.github/workflows/package.yml:32`）。带 ref 的含义：同分支串行排队，跨分支互不进同一组、不互斥打断；组键不含事件名，`workflow_run` 自动触发与 `workflow_dispatch` 手动触发落到同一 ref 时排在同一队里，避免手动包插队在飞自动包。
2. 取消语义取值：`cancel-in-progress: false`（`.github/workflows/package.yml:33`），排队，不取消在飞。
3. 为什么排队比"新推取消旧跑"更适合出包车道：
   - APK 是全链最长杆。取证 2.2 第 5 条：成功轮 shard job 从 13:50:20Z 跑到 14:00:58Z，约 10 分钟；前段 JDK/SDK/Gradle 依赖解析与缓存预热占比高，打断在飞构建等于整段预热作废且零产物落地。
   - 若取 `true`（新推取消旧跑）：连推场景下每个新提交都掐掉上一轮在飞包，推送不断则包永远跑不完——正是本轮 2.2 两次 cancelled 的事故形态（在飞/排队 run 被顶替到 cancelled，没有可用包）。
   - 排队代价只是最多等一轮出包；Verify 车道已按 ref 排队（`.github/workflows/verify.yml:27` 同口径 false），Package 积压至多一条等待，不会出现 runner 堆积。
4. 用户视角保证：排队语义下每个码变提交的 Package run 都跑到终态，队尾那条对应当前 HEAD 所验证的提交；最后必有一个完成态 APK，不会出现"跑到一半被取消、没有可用包"。
5. 与 Verify 车道的取舍差异：Verify 只跑测试、红反馈优先；Package 出包长杆、产物完整性优先，所以两条车道都取 false，但理由各自写在注释里，不互相套用。

## 五、改造三：`check-symbol-existence.py` 接 `--changed`

### 5.1 参数解析现状行（HEAD `b4aaf26` 原文）

1. 入口（HEAD `b4aaf26` 旧版快照）：`if __name__ == '__main__':` 下只识别 `--selftest`，随后 `sys.exit(main())`；`--changed` 传进来没有任何解析分支。
2. 主函数签名（HEAD `b4aaf26` 旧版快照）：`def main(root: str = ROOT) -> int:`，函数体首行 `rc, all_issues, pkg_bad, stats = analyze(root)`；没有改动面参数。

### 5.2 改造后行（现行文件）

1. 主函数签名（`data_scripts/check-symbol-existence.py:878`）：`def main(root: str = ROOT, only_changed: bool = False) -> int:`。
2. 改动面解析（`data_scripts/check-symbol-existence.py:880`）：`changed = git_changed_kt(root) if only_changed else None`；改动面列表交 analyze 做调用扫描过滤。
3. 扫描过滤（`data_scripts/check-symbol-existence.py:815`）：`analyze(root, changed_files=None)`；`changed_files` 非空时只扫描改动面内的调用文件，定义索引仍由 `collect_def_files` 全仓构建——跨文件自有符号反查不随改动面收缩，否则改动文件 import 的未改动文件符号会被误判 NODEF。
4. 改动面解析器（`data_scripts/check-symbol-existence.py:430`）：`git_changed_kt(root)` 走 `git diff --name-only origin/main` 已跟踪改动 + `git status --porcelain -uall` 未跟踪新文件，只保留落在 `SOURCE_ROOTS` 下、磁盘真实存在的 `.kt`；git 不可用/取不到 base 时返回空表，不靠猜把存量文件拉进来判红。
5. 入口解析（`data_scripts/check-symbol-existence.py:1421`）：`only_changed = '--changed' in sys.argv`，随后 `sys.exit(main(only_changed=only_changed))`。
6. 空集口径：`--changed` 改动面无待检 `.kt` 时打印 EMPTY 中性结论（`data_scripts/check-symbol-existence.py:885`），不拿空集冒充全仓 PASS，也不判红；不带 `--changed` 维持全仓扫描，既有 CI 调用行为不变。

### 5.3 实跑证明（命令 + 输出末行）

1. 全仓口径（改造后复跑，末行原文）：
   命令：`python3 data_scripts/check-symbol-existence.py`
   末行：`SYMBOL-EXISTENCE: PASS（全仓扫描 2166 文件，调用 135819 处，NODEF 0 / ARITY 0 / PACKAGE 0，跳过生成 0 / 外部库 18588 / 标准库 43730 / 未知 70680）`，退出码 0。
2. `--changed` 口径（工作区改动面 7 个 `.kt`：`app/src/main` 下 DictRichContent、FileManageActivity、FileManageScreen、FileManageViewModel、FilePickerDialog、FilePickerViewModel 共 6 个，外加 `app/src/androidTest` 下 BookSourceCheckSessionTest 1 个；脚本扫描集即这 7 个文件，末行原文）：
   命令：`python3 data_scripts/check-symbol-existence.py --changed`
   末行：`SYMBOL-EXISTENCE: PASS（改动面扫描 7 文件，调用 363 处，NODEF 0 / ARITY 0 / PACKAGE 0，跳过生成 0 / 外部库 139 / 标准库 61 / 未知 142）`，退出码 0。
3. 过滤生效对照：未改造前 `--changed` 被忽略，输出应与全仓口径同为 2166 文件；改造后 `--changed` 扫描面收为 7 个改动面 `.kt`（全仓 2166 之外的文件不进扫描集），证明参数真的被解析并作用于扫描集。空集兜底路径由 selftest 夹具与 5.2 第 6 条口径锁定：改动面无待检 `.kt` 时走 EMPTY 中性分支，不冒充全仓 PASS。
4. 自测回归锁：`--selftest` 新增"改动面过滤"夹具（坏样本 `UseBad.kt` 不在改动面时不判红；全仓口径仍抓到其 NODEF）。命令：`python3 data_scripts/check-symbol-existence.py --selftest`，末行：`[selftest] 夹具已自删，双向自测通过。`，退出码 0。

## 六、三场景推演

### 场景 A：docs-only 推送（只改 `docs/**` 与/或非构建 `data_scripts/**` 脚本）

1. Verify 车道：classify 判 `docs-only`，静态门禁跑，测试分片整链跳过，lane-summary 显式 success。
2. Package 车道：`workflow_run` 事件照常拉起 Package run（事件层无法路径过滤）；Package 侧 classify 复算判 `docs-only`、`code_changed=false`；`build-shard` 闸门不满足，跳过；`lane-summary` 记录"本轮不出包"显式结论。
3. 最终产物：本轮不出 APK；APK 产物沿用最近一条码变车道的包。

### 场景 B：docs 推送后紧跟 app 代码推送（两次推送先后到达）

1. 第一次 docs 推送：同场景 A，Verify docs-only success；Package run P1 判 docs-only，不出包。
2. 第二次 app 代码推送（命中 `app/**` 或构建面白名单）：Verify 码变车道跑全量单测，success；Package run P2 判 `code`，`build-shard` 出包。
3. 并发：Package 组 `package-refs/heads/main` 排队（false）；P1 的 lane-summary 秒级结束，P2 排在其后，不打断 P1 已完成部分。
4. 最终产物：P2 产出对应当前 HEAD（app 代码提交）的 APK；docs 提交本身不单独产包。

### 场景 C：连续两次 app 代码推送

1. 第一次码变推送：Verify V1 跑测试 success；Package P1 排队/出包。
2. 第二次码变推送（V1 尚未结束或 P1 在飞）：Verify 组排队，V2 等 V1 结束再跑；Package 组排队，P2 等 P1 结束再跑，不取消 P1 在飞构建。
3. 最终产物：P1 产出第一次提交的 APK，P2 产出第二次提交（HEAD）的 APK；两条 Package run 都到终态，队尾 P2 对应 HEAD。若 V1 失败，其 Package run 因 `conclusion == success` 闸门跳过，不产包；V2 success 后 P2 正常出包。

## 七、自查反例

1. 反例：为降竞争把出包条件收得过窄，导致该出包时不出包。回证：构建面清单与 `.github/workflows/verify.yml:95` 白名单逐字对齐，`app/**`（含 `app/src/main/assets/**`）、`gradle/**`、`*.gradle.kts`、`.versioncode`、两个 CI 调用脚本均在码变侧；离线按最近 12 条提交回放判据：`46a8852`（fix source）、`f017cee`（fix config）、`267f775`（fix bookshelf）、`f896179`（feat read）、`df99692`（fix map）判 `code`；`3cf24a5`、`ba3b4fd`、`09b2150`、`60545a9`、`fcb0116` 等 docs 提交判 `docs-only`，方向无反例。
2. 反例：docs 改动混在同一个 commit 里带了一行代码。判定逻辑先判码变白名单再落车道，任一文件命中码变即 `code`，docs 同车不降低出包条件。
3. 反例：跨分支推送互相打断。组键带 `github.ref`，跨分支落不同组；同 ref 内串行。
4. 反例：`--changed` 把定义索引也收缩，导致未改动文件里的符号被误报 NODEF。回证：`analyze()` 只收缩调用扫描集，`collect_def_files` 仍全仓建索引（`data_scripts/check-symbol-existence.py:815` 注释与夹具锁定）。
5. 反例：空改动集被当成全仓 PASS 放行。回证：`--changed` 空集走 EMPTY 中性分支（`data_scripts/check-symbol-existence.py:885`），不打印 PASS。
6. 反例：并发取 true 追求"最新包优先"。推演：连续推送下在飞包反复被掐，推送不断则零产物落地，livelock；故 Package 车道取排队 false，理由见第四节。
7. 反例：用 `continue-on-error` 或 `if: always()` 兜住非码变汇总 job。回证：本单未引入这两个写法；lane-summary 的 needs 链失败即随失败传播。

## 八、新发现问题（每条带状态词）

1. 状态：已定位，低优先，本单未改。`.github/workflows/verify.yml:18` 至 `.github/workflows/verify.yml:21` 旧注释仍写"保留 true"，但现行 `.github/workflows/verify.yml:27` 已是 `cancel-in-progress: false`；注释与取值漂移，无运行时影响，留待下一轮文档注释收口。
2. 状态：已识别，云端复验中。既有 Package 配置写 `cancel-in-progress: false`，API 取证仍见两次 cancelled（2.2 第 3、4 条）；REST 端点不暴露取消操作人与原因字段，false 下被顶替的残留机制（是否存在 workflow_run 连带取消、组表达式在该事件下取值异常）未能在本地闭环，需下一次云端连推观察 Package 组排队/取消实况。
3. 状态：边界已识别，保守兜底。`workflow_run` 事件载荷无 `before` 字段，Package classify 只 diff `HEAD~1..HEAD`；单次推送含多个 commit 时，前序 commit 里的改动文件可能不在本轮 diff 内。空集/取不到父提交保守落 `code`（宁可多跑一次出包，不可漏出包）；多 commit 推送口径待云端样本积累后再评估是否补 before 区间。
4. 状态：已核对，无动作。`data_scripts/check-package-trigger.py:146` 按 `head_sha` 配对 Verify 与 Package，不认车道。本单改造后 docs-only Verify 对应的 Package run 仍会被事件拉起，只是 `build-shard` 跳过、`lane-summary` success，run 整体结论仍为 success，对账不会回归 MISSING；若未来改为事件层直接不创建 Package run，该脚本需同步认车道。
5. 状态：已定位，超本单改文件范围，待派单。`data_scripts/rule-gate.sh:461` 与 `data_scripts/gate-levels.json:933` 调 `check-symbol-existence.py` 仍是全仓口径，未接 `--changed`；本单只放开脚本参数能力，接入门禁由后续派单处理。
6. 状态：未跑，云端兜底。本地环境未安装 `actionlint`（`which actionlint` 无结果），workflow YAML 仅做 `yaml.safe_load` 解析与内嵌 Python `py_compile` 校验；Actions 表达式语义以云端 CI 首次实跑为准。

## 九、验收对照（三条判修好了标准）

1. docs-only 推送不再触发 APK 车道：改造前 `build-shard` 闸门无车道判据（3.2 第 4 条原文）；改造后追加 `needs.classify-paths.outputs.code_changed == 'true'`（`.github/workflows/package.yml:134`），docs-only / scripts-only 车道 `build-shard` 跳过、`lane-summary` 显式收束。
2. 同分支连推不再把上一轮 APK 构建打断到 cancelled：组键 `package-${{ github.ref }}`（`.github/workflows/package.yml:32`）同 ref 串行、跨 ref 互斥隔离；`cancel-in-progress: false` 排队（`.github/workflows/package.yml:33`），理由与用户视角保证见第四节；残留机制疑问见第八节 N2。
3. `check-symbol-existence.py --changed` 真能按改动文件过滤：现状行/改造行见 5.1、5.2；实跑对照见 5.3（全仓 2166 文件 vs `--changed` 改动面 7 文件）；selftest 改动面过滤夹具通过。

## 十、产物与自测记录

1. 改动文件（git 工作区未提交，遵守禁 git 写）：
   - `.github/workflows/package.yml`：新增 `classify-paths` job、`build-shard` 接车道闸门、新增 `lane-summary` job、并发注释口径定稿。
   - `data_scripts/check-symbol-existence.py`：`--changed` 参数解析、改动面扫描过滤、`git_changed_kt` 解析器、EMPTY 空集口径、selftest 回归夹具。
2. 未改（本单写入清单之外）：`.github/workflows/verify.yml` 逻辑、`app/**`、`modules/**`、`screenshot/**`、`third_party/**`。
3. 本地自测记录：
   - `python3 -m py_compile data_scripts/check-symbol-existence.py` 通过。
   - `python3 data_scripts/check-symbol-existence.py --selftest` 退出码 0。
   - `python3 data_scripts/check-symbol-existence.py` 退出码 0（全仓 PASS，2166 文件 / 135819 调用，NODEF/ARITY/PACKAGE 均 0）。
   - `python3 data_scripts/check-symbol-existence.py --changed` 退出码 0（改动面 7 文件 / 363 调用，NODEF/ARITY/PACKAGE 均 0）。
   - `python3 data_scripts/check-workflow-contracts.py` 退出码 0：`CI-CONTRACT: PASS（workflow 4 个，workflow_run 引用与契约测试路径均一致）`（闸口回源 `data_scripts/check-workflow-contracts.py:47`）。
   - 两个 workflow `yaml.safe_load` 解析通过；package classify 内嵌 Python 提取后 `py_compile` 通过。
4. 守则回执指纹由 `python3 data_scripts/check-onboarding.py --emit` 现取（闸口脚本 `data_scripts/check-onboarding.py:33`）。
