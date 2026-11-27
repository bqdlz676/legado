# 锚点卡 · 验收 g62 锚点存量棘轮 + CI 路径分流

- 自身路径：docs/锚点/验收-g62棘轮与CI车道.md
- 关联报告：docs/验收-复核-g62棘轮与CI车道.md
- 仓库根：/home/user/Doubao/chats/38454122521460994/legado
- 被验提交：`0ccc12b`（父提交 `2eec329`）；验收快照 HEAD `0ccc12bab387a2289c1d43f86d0c2470b69ac158`。窗口内并发单 `803ab16`（仅 `data_scripts/check-drift.py`）已入库，被验三件与 `0ccc12b` 逐字节一致，结论锚定 `0ccc12b`。
- 验收范围（本卡只覆盖两件）：
  - A：`data_scripts/check-line-anchors.py`（改）、`data_scripts/line-anchors-baseline.json`（新，3837 键）。
  - B：`.github/workflows/verify.yml`（改，三车道分流 + lane-summary）。
- 同单 bundled 但不在本验收范围：`data_scripts/check-test-corpus-honesty.py`、`data_scripts/gate-levels.json`、`data_scripts/rule-gate.sh`（g67 语料断言诚实性接线，归 g67 独立验收单）。

## 一、回源命令

- 锚点闸全仓复跑：`python3 data_scripts/check-line-anchors.py`
- 锚点闸自测（含棘轮四步夹具）：`python3 data_scripts/check-line-anchors.py --selftest`
- 基线临时下调（仅夹具/验收探针用，真仓不得随手跑）：`python3 data_scripts/check-line-anchors.py --baseline-update`
- YAML 解析：`python3 -c "import yaml; yaml.safe_load(open('.github/workflows/verify.yml'))"`
- 契约闸：`python3 data_scripts/check-workflow-contracts.py`
- 报告字段：`python3 data_scripts/check-report-fields.py docs/验收-复核-g62棘轮与CI车道.md`
- 锚点卡：`python3 data_scripts/check-anchor-card.py --strict docs/验收-复核-g62棘轮与CI车道.md docs/锚点/验收-g62棘轮与CI车道.md`
- 新发现问题：`python3 data_scripts/check-problem-finding.py docs/验收-复核-g62棘轮与CI车道.md`
- 守则回执：`python3 data_scripts/check-onboarding.py`

## 二、判据要点（供下一棒复核）

### A 组 · g62 锚点存量棘轮（六条）

1. 必失败复现：仓内造一条指向不存在文件的锚点报告，未入基线必须 rc=1 并点名 `NEW` 键；删净后回 PASS 末行。
2. 棘轮不倒退：基线删掉一条后重跑，该键必须当新增判红，不允许「基线掉了就放行」。
3. `--baseline-update`：只在显式传参时写基线；普通跑不得改写 JSON；写盘时必须打印「须在 docs/台账-三件缺项.md 记理由」类提示。
4. 真仓末行与申报对照：扫描份数 / 锚点数 / 新增 0 / 基线存量 3837 / SUSPECT 仅提示，rc=0；计数随在飞文档浮动，机制判据（新增 0、存量 3837、rc=0）为准。
5. fail-safe：基线文件移走后必须 rc=1 报大量 NEW，不得因缺基线放行；验完原样恢复并核对 sha256。
6. `--selftest` 末行 PASS；棘轮四步（无基线红 → 冻结 → 存量绿 → 再加一条新死锚点必红）源码存在且真跑。

### B 组 · CI 路径分流（五条）

7. YAML 可解析；actionlint 实跑 0 告警（本机未装则如实记未验证，不得伪造）。
8. 三车道判据接到 `test-shard` / `rhino-eval-tests` / `slow-bench-tests` / `test-aggregate` 四处；新增行范围内无 `continue-on-error`、无 `if: always()`。
9. `lane-summary` job 存在，`needs` 含 `classify-paths` 与 `static-gate`，`if` 为非码变（`code_changed == 'false'`），目的是给非码变车道显式 success 结论、替被跳过的 required check。
10. `classify-paths` 的 git 调用带 `core.quotepath=false`；内嵌 Python 为 bash heredoc（`python3 - <<'PY'`）形式，无非法 `shell: python3`。
11. 最近 30 提交离线实算分类（docs-only / scripts-only / code），与申报 18/7/5 对照；分类口径写入结论；窗口滚动导致的偏移单独归因。

## 三、关键静态落点

- 基线键定义：`data_scripts/check-line-anchors.py:252`（`引用文档|级别|目标文件|书写锚点`，不带报告自身行号）。
- fail-safe 空基线：`data_scripts/check-line-anchors.py:239`（`load_baseline` 缺失返回空集）。
- 写盘分支与台账提醒：`data_scripts/check-line-anchors.py:299`、`:313`。
- 棘轮自测四步断言：`data_scripts/check-line-anchors.py:422`、`:424`、`:426`、`:430`。
- 车道分类白名单：`.github/workflows/verify.yml:95`（前缀）、`:97`（精确名）、`:100`（CI 直调脚本）、`:116`（空集保守落 code）。
- 四下游车道闸：`.github/workflows/verify.yml:313`、`:481`、`:608`、`:732`。
- 非码变汇总：`.github/workflows/verify.yml:845`（lane-summary）、`:847`（needs）、`:848`（if）。
