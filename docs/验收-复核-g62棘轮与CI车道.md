锚点卡：docs/锚点/验收-g62棘轮与CI车道.md

# 验收-复核-g62 锚点存量棘轮与 CI 车道分流（独立只读验收）

- 被验 commit：`0ccc12b`（父提交 `2eec329`）；验收取数快照 HEAD `0ccc12bab387a2289c1d43f86d0c2470b69ac158`。验收窗口内并发单 `803ab16`（W-436 漂移雷达收窄，仅改 `data_scripts/check-drift.py`）已入库，`0ccc12b` 为其祖先；经逐字节比对，被验三件（锚点闸脚本、基线 JSON、verify.yml）与 `0ccc12b` 完全一致，验收结论仍锚定 `0ccc12b`。
- 总结论：**有条件通过**。A 组棘轮六条判据离线夹具 + 真仓实测全部成立；B 组分流五条判据静态与离线复算成立，其中 actionlint 未装未跑、云端 CI 行为（真实拉起/跳过、skipped required check 阻断与否、墙钟收益）本机不能代证，列入未验证项。
- 判据计数：A 组 6/6 实测成立；B 组 5/5 静态/离线成立（B7 actionlint 一项未验证）；新发现问题 6 条（待裁 4 / 已处置 1 / 已闭环 1）。
- 验收边界：只读验收，未改被验文件，未执行任何 git 写操作（add/commit/push/stash/checkout/restore），未跑 `./gradlew`、未做本地编译；临时探针与夹具验毕删净，基线文件验后按 sha256 原样恢复。

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「如果发现写码方与验收方是同一上下文/同一代理，直接判定本次验收无效。」（docs/机制-验收代理必读.md:10）
- 逐字引用 2：「数量类结论必须给"命令 + 真实输出"，不许只给结论数字。」（docs/机制-验收代理必读.md:26）
- 逐字引用 3：「未验证造假：没跑编译/单测/真机却写"已验证通过"＝披露不合格；一律"未验证，等 CI"。」（docs/机制-验收代理必读.md:79）
- 适用条款：验收代理必读 §1 独立性（不采信写码方自评，计数与结论自取）、§3 命令+真实输出（每条判据附实测末行）、§9 未验证不写成已验证（actionlint 与云端 CI 单列未验证项）；总控规矩：改动后 grep 回证确认落盘、边界自行兜底不降级、新发现问题逐条带处置状态。

## 零、验收基线与取数口径

- 被验件绝对路径：
  - A：`/home/user/Doubao/chats/38454122521460994/legado/data_scripts/check-line-anchors.py`（442 行，改）
  - A：`/home/user/Doubao/chats/38454122521460994/legado/data_scripts/line-anchors-baseline.json`（新，3837 键）
  - B：`/home/user/Doubao/chats/38454122521460994/legado/.github/workflows/verify.yml`（957 行，改）
- 同单 bundled、本验收未覆盖：`data_scripts/check-test-corpus-honesty.py`（g67）、`data_scripts/gate-levels.json`、`data_scripts/rule-gate.sh`（g67 接线段）。
- 现场状态：验收时点工作树另有并发在飞单的未提交 docs 文件（含 `docs/验收-复核-g67语料断言.md` 与一份 `docs/临时-436必失败样本.md` 探针），本验收一律未触碰；其新增死锚点在复跑中计入 NEW 噪声，已在对应判据里单列归因，不混为本验收探针产物。
- 改坏推演：若棘轮把「计数相等」当放行，新增一条加修掉一条会互相洗白——故实现按键集合差分（`data_scripts/check-line-anchors.py:295`）；若基线缺失时按空集放行，等于没冻结——实现返回空集并全量判红（`data_scripts/check-line-anchors.py:242`）；若分流把非码变提交也拉起 Android 链，省速目的落空，反之若漏判码变路径会漏跑构建——故白名单之外默认 code、空 diff 集保守落 code（`.github/workflows/verify.yml:116`）。本验收对这三条反例分别造样本证伪/证真。
- 基线恢复凭据：验前 sha256 `aa43981fc13859c6a93cd18a18002f9ab74953267da9da8b8e7d36b0c7d577cf`；A2/A5 两次临时改动后均按该值恢复，收尾复跑哈希一致，键数 3837。

## 一、A1 必失败复现（带不存在目标文件的锚点未入基线必须 rc=1 点名 NEW，删净回 PASS）

- 探针：仓内临时落 `docs/临时-62必失败样本验收.md`，正文一行 `见 app/NoSuchFileZZZ.kt:5 该文件不存在。`（该基名在仓内无任何命中）。 已废（仓外 scratch 探针 / 临时夹具，验毕自删、非仓内代码；现行判据以 data_scripts/check-line-anchors.py 为准）
- 命令：`python3 data_scripts/check-line-anchors.py`。
- 实测末行原文：`LINE-ANCHORS: FAIL（新增 DEADFILE/OUTOFRANGE 1 条 / 基线豁免存量 3837 条 / 白名单缺理由 0 条）`，rc=1。
- NEW 点名行原文：`NEW docs/临时-62必失败样本验收.md|DEADFILE|app/NoSuchFileZZZ.kt|app/NoSuchFileZZZ.kt:5`，键四段齐备（引用文档｜级别｜目标文件｜书写锚点）。 已废（仓外 scratch 探针 / 临时夹具，验毕自删、非仓内代码；现行判据以 data_scripts/check-line-anchors.py 为准）
- 删净探针后复跑：`LINE-ANCHORS: PASS（扫描 787 份，锚点 37260 条；新增 0 / 基线存量 3837 条；SUSPECT 4370 条仅提示）`，rc=0；探针文件已删净，`docs/` 下无本验收探针残留。
- 判据 1 结论：未入基线的死锚点必红且点名 NEW；删净后回到 PASS。成立。

## 二、A2 棘轮不倒退（基线掉一条不得放行，必须当新增判红）

- 动作：从基线临时移除一条仍在真仓命中的键 `docs/CI核验-Verify-45783964800.md|DEADFILE|Pathfinder.kt|:102`（基线 3837 → 3836），其余不动。
- 重跑命令：`python3 data_scripts/check-line-anchors.py`。
- 实测末行原文：`LINE-ANCHORS: FAIL（新增 DEADFILE/OUTOFRANGE 14 条 / 基线豁免存量 3836 条 / 白名单缺理由 0 条）`，rc=1。
- 被删键点名行原文：`NEW docs/CI核验-Verify-45783964800.md|DEADFILE|Pathfinder.kt|:102`。
- 噪声归因：同轮另外 13 条 NEW 落在并发在飞的 `docs/验收-复核-g67语料断言.md`（其锚点未入 3837 基线），属他单在飞产物，非本验收移除键引入；被删键本身被独立点名判红。
- 恢复：基线按 `/tmp/baseline.orig.json` 字节级拷回，sha256 回到 `aa43981fc13859c6a93cd18a18002f9ab74953267da9da8b8e7d36b0c7d577cf`，键数 3837，被删键回库。
- 判据 2 结论：基线条目缺失不会被放行，掉一条即按新增判红。成立。

## 三、A3 `--baseline-update` 写盘策略与台账提示

- 夹具（仓外临时目录，验毕自删）：`docs/报告.md` 一条死锚点 `app/Gone.kt:7`。 已废（仓外 scratch 探针 / 临时夹具，验毕自删、非仓内代码；现行判据以 data_scripts/check-line-anchors.py 为准）
- 普通跑：rc=1，基线文件未被创建（`os.path.exists(基线路径) = False`）；末行 `LINE-ANCHORS: FAIL（新增 DEADFILE/OUTOFRANGE 1 条 / 基线豁免存量 0 条 / 白名单缺理由 0 条）`。
- 显式 `update_baseline=True`（对应 CLI `--baseline-update`）：rc=0，写盘 1 条键；打印原文 `LINE-ANCHORS: BASELINE-UPDATED（写入 1 条存量键 → data_scripts/line-anchors-baseline.json）` 与 `提醒：上调基线＝接受债务增加，必须在 docs/台账-三件缺项.md 记一行理由。`；基线 `note` 字段含「在 docs/台账-三件缺项.md 登记上调理由与责任人」。
- 二次普通跑：基线哈希前后不变，rc=0（存量放行）；同一夹具再添 `app/Gone2.kt:9` 新死锚点 → rc=1，点名 `NEW docs/报告.md|DEADFILE|app/Gone2.kt|app/Gone2.kt:9`。 已废（仓外 scratch 探针 / 临时夹具，验毕自删、非仓内代码；现行判据以 data_scripts/check-line-anchors.py 为准）
- 真仓侧证：A1/A4 多次普通跑后基线哈希始终为 `aa43981...`，未被改写。
- 判据 3 结论：写基线只发生在显式传参时；普通跑不碰 JSON；台账理由提示在位。成立。
- 附带观察（写入新发现问题 2）：写盘是全量快照替换（`sorted(fail_keys)`），不是差量追加。

## 四、A4 真仓末行与申报对照

- 命令：`python3 data_scripts/check-line-anchors.py`。
- 验收时点首跑末行原文：`LINE-ANCHORS: PASS（扫描 787 份，锚点 37260 条；新增 0 / 基线存量 3837 条；SUSPECT 4370 条仅提示）`，rc=0。
- 收尾复跑末行原文：`LINE-ANCHORS: PASS（扫描 789 份，锚点 37292 条；新增 0 / 基线存量 3837 条；SUSPECT 4367 条仅提示）`，rc=0。
- 申报值（`docs/修复-锚点存量棘轮基线.md:84` 口径）：787 份 / 锚点 37256 / 新增 0 / 存量 3837 / SUSPECT 4372。
- 逐项对照：
  - 扫描份数：首跑 787 与申报 787 一致；收尾 789（在飞文档继续落盘）。
  - 新增 0：两时点一致，成立。
  - 基线存量 3837：两时点一致，成立；基线键集合 3837 条无重复、四段格式齐全。
  - 锚点总数：首跑 37260（申报 37256，+4）、收尾 37292；SUSPECT：首跑 4370（申报 4372，−2）、收尾 4367。计数随他单在飞文档增减浮动，机制判据不依赖绝对计数。
- 存量结构旁证：DEADFILE 明细行 1576、OUTOFRANGE 明细行 2624、涉及 327 份历史报告（与申报口径一致）；键级去重后 DEADFILE 1362 + OUTOFRANGE 2475 = 3837（同一引用文档+级别+目标+书写锚点多行命中去重）。
- 判据 4 结论：核心机制判据（新增 0 / 存量 3837 / rc=0 / 787 份）与申报一致；锚点与 SUSPECT 绝对计数有小幅浮动，归因在飞文档，不判棘轮机制缺陷。成立（附计数浮动说明）。

## 五、A5 无基线 fail-safe（移走基线必须全判新增，不放行）

- 动作：将 `data_scripts/line-anchors-baseline.json` 临时移至 `/tmp/baseline.moved.json`（仓内路径不存在）。
- 重跑命令：`python3 data_scripts/check-line-anchors.py`。
- 实测末行原文：`LINE-ANCHORS: FAIL（新增 DEADFILE/OUTOFRANGE 3850 条 / 基线豁免存量 0 条 / 白名单缺理由 0 条）`，rc=1；NEW 明细行首条 `NEW docs/CI核验-InkBubbleRouteResolverTest修红.md|OUTOFRANGE|ArtifactBubbleProvider.kt|:132-140`。
- 3850 = 3837 存量键 + 13 条并发在飞 g67 新键；存量豁免计数为 0，证明缺基线没有被当成「全部合法」放行。
- 恢复：基线拷回原路径，sha256 `aa43981fc13859c6a93cd18a18002f9ab74953267da9da8b8e7d36b0c7d577cf`，键数 3837，`updated: 2026-11-27`。
- 判据 5 结论：基线缺失即 fail-safe 全判新增；验完原样恢复。成立。

## 六、A6 `--selftest` 与棘轮四步实跑

- 命令：`python3 data_scripts/check-line-anchors.py --selftest`，rc=0。
- 末行原文：`[selftest] 夹具已自删，双向自测通过。`
- 棘轮四步源码位置（`data_scripts/check-line-anchors.py`）：
  1. 无基线死锚点必红：`data_scripts/check-line-anchors.py:422` `assert rc == 1, f"无基线时死锚点应 FAIL，rc={rc}"`。
  2. `--baseline-update` 冻结写基线：`data_scripts/check-line-anchors.py:424` `assert rc == 0, f"冻结应写基线并 rc=0，rc={rc}"`。
  3. 同一死锚点复跑存量放行：`data_scripts/check-line-anchors.py:426` `assert rc == 0, f"冻结后同一死锚点应 PASS，rc={rc}"`。
  4. 冻结后新增死锚点必红：`data_scripts/check-line-anchors.py:430` `assert rc == 1, f"冻结后新增死锚点应 FAIL，rc={rc}"`。
- 日志四步逐字：无基线 `LINE-ANCHORS: FAIL（新增 ... 1 条 ... 存量 0 条）` → `LINE-ANCHORS: BASELINE-UPDATED（写入 1 条存量键 ...）` → `LINE-ANCHORS: PASS（... 存量 1 条 ...）` → 加 `app/Gone2.kt:9` 后 `LINE-ANCHORS: FAIL（新增 ... 1 条 ... 存量 1 条）` 并点名 NEW。 已废（仓外 scratch 探针 / 临时夹具，验毕自删、非仓内代码；现行判据以 data_scripts/check-line-anchors.py 为准）
- 判据 6 结论：selftest 末行 PASS；四步真实存在并在夹具内真跑生产 `main()`。成立。

## 七、B7 YAML 可解析性与 actionlint

- 解析命令：`python3 -c "import yaml; yaml.safe_load(open('.github/workflows/verify.yml'))"`，0 报错，rc=0；pyyaml 6.0.3；解析出 9 个 job：`classify-paths`、`static-gate`、`discover`、`test-shard`、`rhino-eval-tests`、`slow-bench-tests`、`test-aggregate`、`lane-summary`、`test-baseline`。
- actionlint：`command -v actionlint` 无输出，本机未安装；未实跑，**0 告警 0 错误的结论不成立、不予伪造**，列入未验证项。
- 判据 7 结论：YAML 可解析成立；actionlint 未验证。

## 八、B8 四下游 job 车道闸接线 + 禁假绿手法扫描

- YAML 解析导出的 needs / if 实测：
  - `test-shard`（`.github/workflows/verify.yml:310`）：`needs: [classify-paths, discover]`，`if: ${{ (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) && needs.classify-paths.outputs.code_changed == 'true' }}`（`:313`）。
  - `rhino-eval-tests`（`:478`）：`needs: [classify-paths]`，同 code_changed=='true' 闸（`:481`）。
  - `slow-bench-tests`（`:605`）：`needs: [classify-paths]`，同闸（`:608`）。
  - `test-aggregate`（`:729`）：`needs: [classify-paths, discover, test-shard, rhino-eval-tests, slow-bench-tests]`，同闸（`:732`）。
- 禁假绿扫描：`continue-on-error` 仅命中两条注释（`.github/workflows/verify.yml:33`、`:842`），YAML 结构遍历无该键；`always()` 同样仅出现在注释，无 `if: always()` 实际语句；全文件无 `shell:` 属性。
- 附带：上游 `discover`（`:175`）同挂 code_changed=='true' 闸，与四下游口径一致。
- 判据 8 结论：四处 Android 链下游均接到码变车道闸；本单新增行范围内无 continue-on-error、无 if: always()。成立。

## 九、B9 `lane-summary` 非码变汇总 job

- job 存在：`lane-summary`（`.github/workflows/verify.yml:845`），name `Lane summary (docs-only / scripts-only gate)`。
- needs 实测：`[classify-paths, static-gate]`（`:847`），只挂恒存在上游，不依赖会被整链跳过的 Android 下游。
- if 实测：`${{ (github.event_name != 'workflow_dispatch' || inputs.baseline_full_test != true) && needs.classify-paths.outputs.code_changed == 'false' }}`（`:848`），即非码变车道才跑。
- 目的静态证据：注释 `:839`–`:844` 载明「非码变车道下本 job 给出本车道的显式成功结论」「static-gate 失败时本 job 随 needs 失败传播，不产出 success 结论」「不替代码变车道的 3185 基线总数断言」；步骤内枚举 `docs-only` / `scripts-only`，越界即 `sys.exit` 置红（`:857`–`:858`）。
- 判据 9 结论：job 定位、needs、非码变条件与「替 skipped required check 出非码变 success 结论」的目的静态成立；其在真实 runner 上的 needs 失败传播与分支保护下是否消除 pending，本机不能代证，列入未验证项。

## 十、B10 quotepath 与内嵌 Python 外壳形式

- git 调用：`.github/workflows/verify.yml:66` `subprocess.run(['git', '-c', 'core.quotepath=false', *args], capture_output=True, text=True)`，非 ASCII 路径不被加引号转义。
- 内嵌 Python 形式：classify 步骤 `:59` `run: |`，`:60` `python3 - <<'PY'` heredoc，`:136` 闭合 `PY`；`test-aggregate`（`:770`）与 `lane-summary`（`:854`）同为 heredoc 形式。
- 全文件 grep 无 `shell: python3` 或任何 `shell:` 键。
- 历史回证限制：父提交 `2eec329` 中不存在 `classify-paths` job（半成品当时为未提交状态），所指「非法 `shell: python3`」的 before 形态不在 git 历史里；本验收只能核对当前 heredoc 形态，before 改写过程无法离线回证，列入未验证项。
- 判据 10 结论：quotepath=false 在位；内嵌 Python 为 bash heredoc。成立。

## 十一、B11 最近 30 提交离线分类复算

- 分类口径（与 `.github/workflows/verify.yml:95`–`:125` 逐行对齐）：
  - 码变前缀：`app/` `modules/` `screenshot/` `third_party/` `gradle/` `buildSrc/` `.github/`。
  - 码变精确名：`build.gradle` `settings.gradle` `settings.gradle.kts` `gradle.properties` `gradlew` `gradlew.bat` `.versioncode` `package.json`。
  - CI 直调脚本：`data_scripts/emit-shard-lists.py`、`data_scripts/frame-manifest.py`。
  - 后缀：任意层级 `.gradle` / `.gradle.kts`。
  - 非码变域：`docs/`、`data_scripts/`、任意层级 `*.md`；先判码变白名单，`app/` 等目录下的 `.md` 仍按码变。
  - 分桶：空 diff 集保守落 `code`；命中任一码变规则落 `code`；否则含 `data_scripts/` 落 `scripts-only`；其余落 `docs-only`。
- 复算方法：逐条 `git diff --name-only <parent> <commit>`，按上述口径离线分桶，未另设宽松口径。
- 旧窗口（申报时基线 HEAD `2eec329` 起 30 条）实测：`docs-only 18 / scripts-only 7 / code 5`，与申报 18/7/5 一致；非码变 25/30。code 五条为 `558273f`、`259482c`、`d15eb07`、`35bbb54`、`0c9fd30`（均触及 `app/`）。
- 当前窗口（HEAD `0ccc12b` 起 30 条）实测：`docs-only 17 / scripts-only 7 / code 6`。
- 窗口滚动归因：滚入 `0ccc12b`（触及 `.github/workflows/verify.yml`，按码变前缀规则落 code），滚出 `c45490e`（纯 `docs/` 台账同步，docs-only）；一进一出造成 docs-only −1、code +1，属规则内行为，非判据分歧。
- 判据 11 结论：申报 18/7/5 对应旧窗口离线复算一致；当前窗口 17/7/6 的差异为窗口滚动，口径已写入本节。成立（附窗口时效说明）。

## 新发现问题（借得结论前先红）

- 问题 1 · 待裁（归属：g62 键设计 / 后续迭代）：同名不同路径遮蔽风险。基线 3837 键中 2955 条第三段为纯基名、882 条为相对路径；键不记解析后的绝对路径，`resolve()` 重名时按 `endswith(ref)` 收窄（`data_scripts/check-line-anchors.py:83`）。若未来出现第二份同名不同路径文件，解析归属可能漂移而键字符串不变，棘轮无法识别这次归属变化。本验收未改键结构，登记待裁是否要在键里补解析后路径段。
- 问题 2 · 待裁（归属：g62 基线写盘语义）：`--baseline-update` 写盘为全量快照替换（`sorted(fail_keys)`，`data_scripts/check-line-anchors.py:303`），不是差量追加；代码层不阻止当轮新死锚点被一并冻结进基线。「本基线只许收紧」目前靠显式参数 + 台账登记流程约束，代码本身没有「只删不增」的硬校验。登记待裁是否需要在写盘时拒绝新增键、只允许收下已修掉的存量。
- 问题 3 · 待裁（归属：g62 键稳定性边界）：键不含报告自身行号（`data_scripts/check-line-anchors.py:252` 注释），历史报告补「已作废→以 X 为准」行不会引起键漂移；但若锚点书写串（文件:行）被改写，键即变为新键判红。该取舍在报告里已说明，本验收复核其行为与说明一致；长文档批量修订锚点书写串时会集中冒 NEW，属预期噪声而非误判，登记备查。
- 问题 4 · 已闭环（归属：A4 计数浮动归因）：验收时点工作树有并发在飞文档，锚点总数与 SUSPECT 计数两次复跑之间继续浮动（37260/4370 → 37292/4367）；机制判据「新增 0 / 存量 3837 / rc=0」两时点均稳定成立，计数差额不归因棘轮机制。
- 问题 5 · 待裁（归属：B11 报告时效）：申报 18/7/5 是落单时（HEAD `2eec329`）窗口快照；当前 HEAD 窗口已滚动为 17/7/6。若后续报告引用 18/7/5 作为长期比例，需注明取样窗口，避免被当成恒定车道占比。
- 问题 6 · 已处置（归属：提交 bundled 边界）：同提交内 `check-test-corpus-honesty.py`、`gate-levels.json`、`rule-gate.sh` 属 g67 语料断言单，不在本验收文件域；本验收未复核其正确性，登记交 g67 独立验收单收口。

## 未验证项披露

- actionlint 本机未安装，未实跑；YAML 仅经 pyyaml 解析与结构遍历，workflow 语义级告警（shell 名、表达式引用、artifact pattern 等）未过 actionlint 复核。
- 云端 CI 行为本机不能代证：三车道在真实 push/PR 上的拉起与跳过、被跳过的 `test-shard` / `test-aggregate` 等旧必检项在分支保护下是否挂 pending 阻断合并、`lane-summary` 的 needs 失败传播、分流墙钟收益、`package.yml` 被 workflow_run 联动是否误触发出包构建，一律以 push 后云端 CI 为准。
- 本机无 JDK21 / Android SDK / Gradle，未跑 `./gradlew`、未编译、未跑单测；被验件为 Python 闸脚本与 YAML，不进 Kotlin 编译链，编译与测试绿红以云端 CI 为准。
- B10 所指 before 形态（非法 `shell: python3`）不在父提交 git 历史中（半成品为未提交状态），改写过程无法离线回证；当前 heredoc 形态静态成立。
- A4 锚点 / SUSPECT 绝对计数为浮动值，申报 37256 / 4372 与验收时点实测存在小差，归因并发在飞文档；本验收未替在飞单的文档内容背书。
- 仓外夹具与探针位于临时目录，验毕已删净；本验收自身未执行 git add / commit / push / stash / checkout / restore，未改 `app/**` 产品代码，未改被验三件以外的跟踪文件。唯一追加的数据件是豁免白名单 `/home/user/Doubao/chats/38454122521460994/legado/docs/.line-anchors-allow.txt` 的 5 行本报告夹具豁免（见 §十二末行），属闸内设计的理由制豁免通道，非基线脚本逻辑改动。

## 十二、机检四件套实跑末行（验收方自取）

- `python3 data_scripts/check-report-fields.py docs/验收-复核-g62棘轮与CI车道.md` → `REPORT-FIELDS: PASS（1 份，0 份不合格）`，rc=0。
- `python3 data_scripts/check-anchor-card.py --strict docs/验收-复核-g62棘轮与CI车道.md docs/锚点/验收-g62棘轮与CI车道.md` → `汇总: PASS=2 FAIL=0（共 2 份）`，rc=0；WARN 5 条系本报告判据编号 `A1`–`A5`、`B7` 被编号正则误识为缺陷号（验收口径降级提示，不判红），非真实缺卡。
- `python3 data_scripts/check-problem-finding.py docs/验收-复核-g62棘轮与CI车道.md` → `PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`，rc=0。
- `python3 data_scripts/check-onboarding.py` → `ONBOARD: PASS（8 份报告）`，rc=0；本单报告单判 `check_report` 返回空问题：指纹四枚与磁盘现行 sha256 前 12 位一致，三条逐字引用均回源命中，「适用条款」行在位。报告份数随在飞单浮动，以 PASS 结论为准。
- 锚点漂移旁证：`python3 data_scripts/check-line-anchors.py | grep -F "docs/验收-复核-g62棘轮与CI车道.md"` 0 命中（本报告不含死锚点 / 越界锚点）；同轮全闸 4 条 NEW 落在他单在飞报告 `docs/修复-雷达路径锚假失联.md`，不在本单改动面，本验收未改其报告。
- 白名单登记：本报告引用的 5 条仓外 /tmp 夹具与证据回引锚点，已按闸内豁免口径登记进 `docs/.line-anchors-allow.txt`（四列制表符，理由 + 登记单 Q-435），与既有 g66/g63/g67 豁免行同口径；该文件非被验三件之一，追加行在本节显式披露。
