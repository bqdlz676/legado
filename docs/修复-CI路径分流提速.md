锚点卡：docs/锚点/Q429-CI路径分流.md

# 修复-CI 路径分流提速（Q-429：三车道条件补全 + 非码变车道汇总 + 离线收益回算）

## 一、任务与范围

本单承接 `docs/派单队列.md:431` 的 Q-429：在半成品 `.github/workflows/verify.yml`（上一棒已落 `classify-paths` 车道判定与 `static-gate` 常驻门禁）基础上收口，把车道条件补全到全部 Android 链下游 job，并为非码变车道给出明确 success 结论。

- 改动文件域：仅 `.github/workflows/verify.yml` 与本报告 / 锚点卡；未改 `app/**`、`data_scripts/**`、其它 workflow、队列 / 台账 / 接手目录。
- 红线遵守：未执行任何 git 写操作（add/commit/push/stash/checkout/restore 全禁）；未跑 gradle / JDK / SDK 本地编译；未推倒重写半成品，未 `git checkout --` 丢弃既有改动。
- 总结论：**有条件通过**——离线静态自检（YAML 解析、workflow 契约闸、actionlint、四道报告门禁）全绿；分流真实墙钟收益与 skipped required check 的 GitHub 下游行为以 push 后 CI 为准（见「未验证项」）。

## 二、改动清单（文件:行 + before/after）

### 2.1 `classify-paths`：git 调用关闭 quotepath 转义（保守性加固）

- 出处：`.github/workflows/verify.yml:66`。
- before：`subprocess.run(['git', *args], ...)`，非 ASCII 路径被 git 加引号转义后，`app/`、`data_scripts/` 等前缀匹配会被开头引号挡掉，中文文件名的码变路径可能误判 docs-only。
- after：`subprocess.run(['git', '-c', 'core.quotepath=false', *args], ...)`，路径按原始字符返回，前缀判定不被转义干扰。

### 2.2 下游 Android 链 job 补车道闸

| job | 位置 | before | after |
|---|---|---|---|
| `test-shard` | `.github/workflows/verify.yml:310-313` | `if` 仅排除 baseline dispatch；`needs: discover` | `needs: [classify-paths, discover]`；`if` 追加 `needs.classify-paths.outputs.code_changed == 'true'` |
| `rhino-eval-tests` | `.github/workflows/verify.yml:478-481` | 无 `needs`；`if` 仅排除 baseline dispatch | `needs: [classify-paths]`；`if` 追加 `code_changed == 'true'` |
| `slow-bench-tests` | `.github/workflows/verify.yml:605-608` | 无 `needs`；`if` 仅排除 baseline dispatch | `needs: [classify-paths]`；`if` 追加 `code_changed == 'true'` |
| `test-aggregate` | `.github/workflows/verify.yml:729-732` | `needs: [discover, test-shard, rhino-eval-tests, slow-bench-tests]`；`if` 仅排除 baseline dispatch | `needs: [classify-paths, discover, test-shard, rhino-eval-tests, slow-bench-tests]`；`if` 追加 `code_changed == 'true'` |

说明：读文件核实，半成品状态下 `rhino-eval-tests` 与 `slow-bench-tests` 原本就没有挂 `needs: discover`（任务书描述与磁盘现状有出入，以磁盘为准）；本单只补 `classify-paths` 以便读取车道输出，未改动它们与 discover 之间的既有依赖关系。

### 2.3 新增非码变车道汇总 job `lane-summary`

- 位置：`.github/workflows/verify.yml:845-861`。
- 定位：docs-only / scripts-only 车道的显式 success 结论 job。`needs: [classify-paths, static-gate]`，只依赖恒存在的上游；`if` 条件为非 baseline dispatch 且 `code_changed == 'false'`。
- 步骤语义：读取 `lane` 输出，枚举必须落在 `docs-only` / `scripts-only`，否则 `sys.exit` 置红；打印本车道结论，并显式声明本结论不替代码变车道的 3185 基线总数断言。

### 2.4 内嵌 Python 步骤外壳修正

- 半成品两处 `shell: python3`（classify 步骤与新汇总步骤）不被 GitHub Actions 内置外壳名接受（actionlint `[shell-name]` 告警）；改为仓库既有惯例的默认 bash 外壳内 `python3 - <<'PY'` heredoc，与 `test-aggregate` 既有写法（`.github/workflows/verify.yml:770`）一致。脚本本体逻辑未改。

## 三、grep 回证

车道条件落盘回证（`grep -n "code_changed == " .github/workflows/verify.yml`）：

```
175:    if: ${{ (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) && needs.classify-paths.outputs.code_changed == 'true' }}
313:    if: ${{ (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) && needs.classify-paths.outputs.code_changed == 'true' }}
481:    if: ${{ (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) && needs.classify-paths.outputs.code_changed == 'true' }}
608:    if: ${{ (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) && needs.classify-paths.outputs.code_changed == 'true' }}
732:    if: ${{ (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) && needs.classify-paths.outputs.code_changed == 'true' }}
848:    if: ${{ (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) && needs.classify-paths.outputs.code_changed == 'false' }}
```

禁用车道假绿手法回证：`grep -n "continue-on-error\|always()" .github/workflows/verify.yml` 仅命中两条注释说明（`.github/workflows/verify.yml:33`、`.github/workflows/verify.yml:841`），无任何 `continue-on-error:` 键或 `if: always()` 实际语句。

job 图回证（YAML 解析导出）：

```
classify-paths | needs: 无
static-gate | needs: classify-paths
discover | needs: classify-paths | if: code_changed == 'true'
test-shard | needs: [classify-paths, discover] | if: code_changed == 'true'
rhino-eval-tests | needs: [classify-paths] | if: code_changed == 'true'
slow-bench-tests | needs: [classify-paths] | if: code_changed == 'true'
test-aggregate | needs: [classify-paths, discover, test-shard, rhino-eval-tests, slow-bench-tests] | if: code_changed == 'true'
lane-summary | needs: [classify-paths, static-gate] | if: code_changed == 'false'
test-baseline | if: workflow_dispatch && baseline_full_test == true
```

## 四、车道设计：为什么不是假绿

### 4.1 双车道结论互不替代

- 码变车道：`test-aggregate` 照旧下载全部分片 / Rhino / bench JUnit XML，求和并硬断言总数 ≥ 基线 3185，failures / errors / skipped 任一非零即红（断言本体未改，见 `.github/workflows/verify.yml:729` 起）。分流只决定这条链是否拉起，不放宽任何一条既有断言。
- 非码变车道：`lane-summary` 的上游只有 `classify-paths` 与 `static-gate`。`static-gate` 失败时按 GitHub 默认 needs 失败传播，`lane-summary` 不会产出 success 结论；本单没有给它加 `if: always()` 或 `continue-on-error` 去兜底放行。
- 被跳过的只是本轮在非码变车道本就不该执行的 Android 构建链；非码变车道的报告里不声称"测试已跑过"，基线总数断言也不在该车道冒充通过。

### 4.2 与三种被禁假绿手法的对照

- `continue-on-error`：未使用。任何步骤失败都按退出码向上传播。
- `if: always()`：未使用。常驻只有 `static-gate`（靠 needs 天然常驻，不靠 always）；车道汇总 job 走显式车道 `if:` 表达式。
- 跳过测试冒充通过：未发生。码变车道测试集合与排除分类口径一字未动；非码变车道不执行测试链，也不输出测试通过结论。

### 4.3 skipped required check 的下游不确定性（未验证）

分支保护上的必检项名单在沙箱不可读。GitHub 对「被跳过的必检状态检查」在不同版本 / 配置下行为不一：一部分语境下 skipped 不计阻断、可合并；另一部分语境下 skipped 显示 pending 卡住合并。本单能离线保证的是：两条车道各有一个会真实跑出 success 结论的 job（码变 `test-aggregate`、非码变 `lane-summary`）。若 push 后发现某条被跳过的旧必检项（如 `Unit tests shard */4`、`Aggregate & assert test totals`）在 PR 合并框挂 pending，需要在分支保护设置里按车道调整必检名单，或后续再加一个挂双车道的 umbrella 汇总 job；该调整属仓库设置 / 跨 workflow 面，不在本单文件域。

## 五、保守性证明：code 车道穷举表与漏判检查

### 5.1 判据出处

码变判定在 `.github/workflows/verify.yml:95-108`：白名单前缀 + 根构建文件精确名 + CI 直调脚本 + `.gradle` / `.gradle.kts` 后缀；白名单之外一律保守落 code（空 diff 集也按 code 处理，见 `.github/workflows/verify.yml:116-120`）。

### 5.2 会被判为 code 车道的路径穷举

| # | 路径形态 | 理由 |
|---|---|---|
| 1 | `app/` 前缀全部（源码 / 资源 / 测试 / Manifest） | 应用本体，影响 APK 与单测 |
| 2 | `modules/` 前缀全部 | 模块本体 |
| 3 | `screenshot/` 前缀全部 | 截图基建与截图任务，影响截图产物 |
| 4 | `third_party/` 前缀全部 | 三方源码内嵌 |
| 5 | `gradle/` 前缀全部（wrapper properties、`libs.versions.toml` 等） | Gradle 构建面 |
| 6 | `buildSrc/` 前缀全部 | 构建脚本本体；当前仓无跟踪文件，前缀保留兜底 |
| 7 | `.github/` 前缀全部（verify / package / screenshot workflow、composite action） | CI 配置本身改动可能改变构建语义 |
| 8 | 根文件精确名：`build.gradle`、`settings.gradle`、`settings.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat` | 构建入口与 wrapper |
| 9 | `.versioncode`、`package.json` | 版本号唯一源 / 打包脚本元数据 |
| 10 | 任意层级 `.gradle` / `.gradle.kts` 后缀文件 | 模块构建脚本 |
| 11 | `data_scripts/emit-shard-lists.py`、`data_scripts/frame-manifest.py` | 被 workflow 直接调用、参与构建 / 清单生成的数据脚本 |

### 5.3 任务点名项逐项核对

- `screenshot/`：在码变前缀表（#3），不会漏。
- `gradle/`：在码变前缀表（#5），不会漏。
- `buildSrc/`：在码变前缀表（#6），不会漏。
- `package.json`：在精确名表（#9），不会漏。
- `.versioncode`：在精确名表（#9），不会漏。
- `data_scripts/emit-shard-lists.py`：在 CI 直调脚本表（#11），不会漏。
- `data_scripts/frame-manifest.py`：在 CI 直调脚本表（#11），不会漏。

### 5.4 残余边界与兜底

- 根目录 `.gitignore`、`.handover-key` 未入白名单，落 docs-only 车道：二者不参与 APK 构建产物收集，保守性可接受；若未来 gitignore 规则影响打包资源归集，须补进白名单。
- `local.properties`、`build/`、`dist/`、`.gradle/` 未被 git 跟踪（`git ls-files` 不含），不进 diff 判据。
- 非 ASCII 文件名：已由 `.github/workflows/verify.yml:66` 的 quotepath=false 兜底，中文文件名不再被转义引号挡掉前缀匹配。
- `docs/` 全部、`data_scripts/` 其余脚本、任意层级 `*.md` 落非码变车道；`data_scripts/` 其余脚本仍由 `static-gate` 的 `py_compile` 与四道文档 / 回执门禁覆盖（`.github/workflows/verify.yml:141` 起）。

## 六、分流收益：最近 30 条提交离线回算

### 6.1 回算方法

取 `git log -30` 最近 30 条提交，逐条用 verify.yml 内同一套判据（同白名单 / 精确名 / 后缀规则）离线跑 `git diff --name-only <parent> <commit>` 分桶；判据脚本与 workflow 内嵌逻辑逐行对齐，未另设宽松口径。

### 6.2 分桶结果

| 车道 | 提交数 | 占比 | 提交 SHA（短） |
|---|---|---|---|
| docs-only | 18 | 60.0% | 2eec329, 511e274, 7e74199, b20c3b4, cff78b1, 34768cf, 8bd5842, f2b60b8, 259ec2e, bbf7c2d, c9e87c7, 50002da, 0c29eb4, c0923aa, 432b33d, 727ada7, da06622, c45490e |
| scripts-only | 7 | 23.3% | ad24db3, 4c9fcb8, 2960cc4, d588467, e42bae7, 8696b7e, 9be7346 |
| code | 5 | 16.7% | 558273f, 259482c, d15eb07, 35bbb54, 0c9fd30 |

非码变提交合计 25 / 30 = 83.3%。

### 6.3 job 槽位量化对比

分流前 push/PR 单轮固定拉起的 Android 链 job：`discover` 1 + `test-shard` 4 + `rhino-eval-tests` 1 + `slow-bench-tests` 1 + `test-aggregate` 1 = 8 个（`test-baseline` 在 push/PR 恒 skipped，不计）。

分流后：

- code 车道：`classify-paths` 1 + `static-gate` 1 + 上述 8 个 Android 链 job = 10 个 job 槽位（前两个不装 JDK / Gradle）。
- 非码变车道：`classify-paths` 1 + `static-gate` 1 = 2 个 job 槽位，Android 链 0 个。

按 30 条提交外推：

| 口径 | 分流前 | 分流后 | 差值 |
|---|---|---|---|
| Android 链重 job 槽位 | 8 × 30 = 240 | 5 × 8 = 40 | 省 200（−83.3%） |
| 总 job 槽位 | 240 | 5 × 10 + 25 × 2 = 100 | 省 140（−58.3%） |

墙钟分钟数未离线测算（沙箱无 runner、无 Gradle 缓存实测），列入未验证项；上表只算 job 槽位，不等同于时长节省。

## 七、二阶三阶影响 / 反例与边界 / 认知成本

### 7.1 二阶三阶推演

- 二阶：`package.yml` 由 `workflow_run` 监听 Verify 的 `conclusion == 'success'` 触发（`.github/workflows/package.yml:38` 一带）。非码变车道 Verify 总 conclusion 仍由真实跑出的 `classify-paths` / `static-gate` / `lane-summary` 决定，口径未变；但 package 自身未接车道判据，docs-only 推送 Verify 成功后是否仍拉起出包构建，见「交接」节登记。
- 二阶：`discover` 不上传分片清单产物时，下游 `test-shard` 下载清单步骤随之跳过；非码变车道 aggregate 不下载任何 XML，既有产物名 pattern 未改，不存在撞车。
- 三阶：维护成本上，新增一个 `lane-summary` job 与一套 `code_changed` 闸条件；后续新增 Android 链下游 job 必须同挂 `code_changed == 'true'`，否则会在非码变车道误拉起——此点写入锚点卡判据。
- 三阶：scripts-only 车道仍吃 `static-gate` 的全量 `py_compile` 与报告 / 锚点 / 找问题 / 回执四闸，门禁面未随分流收缩。

### 7.2 反例与边界

- 反例一：若把非码变车道也挂上 `test-aggregate` 的 needs，上游全跳会把汇总 job 拖成 skipped，反而堵分支保护；故 `lane-summary` 只挂恒存在上游，不依赖被跳过的 Android 链。
- 反例二：若空 diff 集（首推 / 取不到 base）按 docs-only 放行，取不到改动时会漏跑构建；现有实现空集保守落 code（`.github/workflows/verify.yml:116-120`），本单未改。
- 反例三：若 quotepath 保持默认，中文文件名的码变路径会被引号挡在白名单前缀之外，误判 docs-only；已在 `.github/workflows/verify.yml:66` 关闭转义兜底。
- 边界：workflow_dispatch 手动触发且未勾 baseline 时，`discover` 链条件与车道闸叠加；勾 baseline_full_test 时 `test-baseline` 单 job 全量跑，既有语义不变。
- 边界：PR 事件取 merge-base 与 HEAD 的 diff；取不到 base 时回退 `before` / `HEAD~1`，与半成品既有回退一致。

### 7.3 认知成本

本单新增概念一个：车道结论双 job 制（码变看 `test-aggregate`、非码变看 `lane-summary`）。命名上 `lane-summary` 与 `test-aggregate` 对称，接手人按 job 图即可定位；未引入新的抽象层或新配置文件。既有 job 名 / 产物名 / artifact pattern 全部保留，历史 CI 记录里的 job 名仍可回查。

## 八、自查反例

- 反例 A：若 `lane-summary` 误用 `if: always()`，static-gate 红时它仍会跑出 success，等于把静态门禁失败洗白；实现里未用 always，失败靠 needs 传播阻断。
- 反例 B：若非码变车道静默跳过部分已执行测试并在汇总里报绿，属假绿；实现里非码变车道根本不进测试链，且汇总步骤显式打印"不替代码变车道基线断言"，不输出测试通过结论。
- 反例 C：若白名单漏收 `screenshot/` 或 `gradle/`，截图 / 构建面改动会被当文档跳过；第五节逐项核对七类点名路径，均在码变表内。
- 反例 D：若车道判定把 `.github/workflows/*.yml` 当 docs-only，CI 自身改动将不再触发构建验证；现 `.github/` 前缀在码变白名单（#7）。

## 九、新发现问题

1. `package.yml` 未接车道判据：`workflow_run` 只看 Verify conclusion == success，docs-only / scripts-only 推送 Verify 绿之后，出包 build job 仍可能被触发拉起。处置状态：待裁——归属 `package.yml` 触发口径与出包链提速面，本单文件域不含，登记交下一棒核对是否需要在 package 侧补车道判断。
2. 分支保护必检项名单沙箱不可读：被跳过的 `test-shard` / `test-aggregate` 等旧必检名在 GitHub 下游是否挂 pending 未验证。处置状态：待裁——push 后在 PR 合并框与分支保护设置核对；若卡合并，按车道调整必检名单或补 umbrella 汇总 job。
3. `data_scripts/` 下除两个 CI 直调脚本外的脚本改动落 scripts-only 车道，`static-gate` 只做 `py_compile` 与文档类门禁，不校验这些脚本可能间接影响的构建产物。处置状态：已闭环——与任务书判据一致；后续若有新脚本被 verify / package / screenshot workflow 直接调用，须补进 `.github/workflows/verify.yml:100-101` 的 CI 直调脚本表。

## 十、未验证项

1. 分流的真实墙钟收益（分钟 / 秒级）未测：沙箱无 runner、无 JDK21 / SDK / Gradle 缓存，离线只回算了 job 槽位数量；时长以 push 后 CI 实测为准。
2. skipped required check 在分支保护下是否阻断合并：GitHub 行为随版本与配置不一，本单无法离线确认；以 push 后 PR 合并框状态为准。
3. `lane-summary` 在真实 runner 上的 needs 失败传播与结论输出未在云端复验；本单只做了 YAML 解析、契约闸与 actionlint 静态校验。
4. 非码变车道 Verify 成功后 `package.yml` 是否误触发出包构建，未在云端观察。
5. 本单未本地编译、未跑单测、未上真机；编译与测试结论一律以云端 CI 为准。

## 十一、交接（发现但未改的跨模块问题）

- 交给出包链 / CI 配置维护方：`.github/workflows/package.yml:38` 的触发条件可考虑接入车道判断，避免纯文档 / 纯脚本提交仍拉起 APK 构建；本单未改其它 workflow（红线）。
- 交给后续 CI 收口单：若分支保护要求单一名义必检项，可评估加一个双车道 umbrella 汇总 job（needs 仅挂恒存在上游，按 `lane` 分发结论）；本单按任务书优先采用了独立非码变汇总 job 方案。
- 工作区既有他人未提交改动（`data_scripts/check-line-anchors.py`、`docs/.no-wait-since`、`docs/派单队列.md`、`docs/派单台账.md` 等）本单一律未碰。

## 十二、自检门禁末行

- YAML 解析：`python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/verify.yml'))"` 0 报错（退出码 0，YAML-OK）。
- workflow 契约闸（门禁 53，判据见 `data_scripts/check-workflow-contracts.py:11`）：命令 `python3 data_scripts/check-workflow-contracts.py`，末行：`CI-CONTRACT: PASS（workflow 4 个，workflow_run 引用与契约测试路径均一致）`（rc=0）。
- actionlint：本沙箱下载 actionlint 1.7.7 实跑 `.github/workflows/verify.yml`，0 告警 0 错误（rc=0，无输出行）；若环境重装后缺失，应明写「未装、未跑」。
- 报告字段闸（判据见 `data_scripts/check-report-fields.py:9`）：命令 `python3 data_scripts/check-report-fields.py docs/修复-CI路径分流提速.md`，末行：`REPORT-FIELDS: PASS（1 份，0 份不合格）`（rc=0）。
- 锚点卡闸（判据见 `data_scripts/check-anchor-card.py:14`）：命令 `python3 data_scripts/check-anchor-card.py --strict docs/修复-CI路径分流提速.md docs/锚点/Q429-CI路径分流.md`，末行：`汇总: PASS=2 FAIL=0（共 2 份）`（rc=0）。
- 找问题闸（判据见 `data_scripts/check-problem-finding.py:9`）：命令 `python3 data_scripts/check-problem-finding.py docs/修复-CI路径分流提速.md`，末行：`PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`（rc=0）。
- 回执闸（判据见 `data_scripts/check-onboarding.py:9`）：命令 `python3 data_scripts/check-onboarding.py`，末行：`ONBOARD: PASS（5 份报告）`（rc=0；份数随本轮在飞报告浮动，以 PASS 结论为准）。
- 行锚点闸：命令 `python3 data_scripts/check-line-anchors.py | grep -F "docs/修复-CI路径分流提速.md"`，命中 0 条 NEW（本单报告无 DEADFILE / OUTOFRANGE / SUSPECT 新键；闸整体退出码 1 来自他单在飞报告的存量 NEW，与本单无关）。
- 未跑：`python3 data_scripts/check-compose-pitfalls.py` 与本单无关，按任务书未跑成结论。

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 逐字引用 2：「编译只在云端 CI 发生。」（docs/机制-子代理必读.md:12）
- 逐字引用 3：「没编译、没跑测试、没真机 —— 一律显式写出来，不许用"应该没问题"代替。」（docs/机制-子代理必读.md:20）
- 适用条款：禁 git 写操作、禁本地编译；改动后 grep 回证确认代码真写入；未验证项显式声明不拿"应该没问题"代替；一条缺陷一单，发现的跨模块问题只登记不动手；空集 / 边界自行兜底不降级。
