锚点卡：docs/锚点/pre-push-EMPTY分流.md

# 修复-pre-push EMPTY 分流（rc=2 空集提示不再记步骤失败）

## 一、背景与旧行为复现

`data_scripts/pre-push-check.sh` 的 `run_step` 旧实现对所有非零退出码统一记失败：子进程退出码只要不是 0，就置 `FAILED=1` 并把步骤塞进失败清单（旧版函数体见改前复刻样本）。两个直接子闸把「空集无待检对象」约定为退出码 2（㊾ 空集不判 PASS 口径）：`data_scripts/check-report-fields.py:259` 与 `data_scripts/check-evidence.py:175`。干净树或本轮无报告改动时，这一步打印 EMPTY 提示并退出 2，旧逻辑却把它记成步骤 FAIL，本地预推出现假红；真正的红（退出码 1）识别不受影响。

改前先造样本复现旧行为。样本 harness 逐字复刻旧版 `run_step`（取自改前 `data_scripts/pre-push-check.sh`），替身命令一退 2、一退 1：

- 命令：`bash /tmp/repro-prepush/old-runstep.sh`
- 输出末段：

```
EMPTY: 未提供待检报告（空集不判 PASS；聚合口径走 changed_reports）
FAIL 3.5-报告字段(EMPTY样本) —— 子检查退出码 2（完整输出见 /tmp/repro-prepush/3.5-报告字段(EMPTY样本).log）
FAIL docs/修复-x.md：A 首行缺锚点卡
FAIL 真红样本 —— FAIL docs/修复-x.md：A 首行缺锚点卡
OLD-BEHAVIOR FAILED=1 失败步骤数=2
  ✗ 3.5-报告字段(EMPTY样本)｜子检查退出码 2（完整输出见 /tmp/repro-prepush/3.5-报告字段(EMPTY样本).log）
  ✗ 真红样本｜FAIL docs/修复-x.md：A 首行缺锚点卡
```

旧行为结论：退出码 2 的 EMPTY 提示与退出码 1 的真红同被记入失败清单，`FAILED=1`，空集假红成立。

真实子闸空集通路复现：在临时迷你空仓（`git init` 后复制 `data_scripts/check-report-fields.py`）里实跑：

- 命令：`python3 data_scripts/check-report-fields.py`
- 输出：`EMPTY: 未提供待检报告（空集不判 PASS；聚合口径走 changed_reports）`，`real-child rc=2`。
- 同法实跑 `data_scripts/check-evidence.py:175` 空仓通路：`EMPTY: 未提供待检报告（空集不判 PASS；diff 型调用请走 ㊽ 口径）`，`evidence rc=2`。

## 二、rc=2 穷举清单（一次性对齐，不逐点试）

对 `data_scripts/` 全部 `.py` / `.sh` 扫 `exit 2`、`return 2`、`sys.exit(2)`，再按是否在 pre-push 直接链上逐条定性：

1. `data_scripts/check-evidence.py:175` —— 在直接链上（第 3 步回执证据）；EMPTY 空集，非失败语义，rc=2，纳入分流。
2. `data_scripts/check-report-fields.py:259` —— 在直接链上（第 3.5 步报告字段）；EMPTY 空集，非失败语义，rc=2，纳入分流。
3. `data_scripts/kotlin-syntax-lint.py` —— 在直接链上；退出码仅 0/1，无 2 语义，不动。
4. `data_scripts/check-dependency-symbols.py` —— 在直接链上；退出码仅 0/1，无 2 语义，不动。
5. `data_scripts/check-onboarding.py` —— 在直接链上；空集分支退出 0（打印 ONBOARD PASS），不产 2，不动。
6. `data_scripts/check-readonly-setter.py` —— 在直接链上；退出码仅 0/1，不动。
7. `data_scripts/check-drift.py` —— 在直接链上；退出码仅 0/1，不动。
8. `data_scripts/cross-module-guard.py` —— 在直接链上；退出码仅 0/1，不动。
9. `data_scripts/debt-ratchet.py` —— 在直接链上；退出码仅 0/1，不动。
10. `data_scripts/rule-gate.sh` —— 第 6 步总门禁；自身退出码仅 0/1（开场定位失败 exit 1），不产 2；其内部非零即 bad 的聚合口径本单不改。
11. `data_scripts/update-handover.py` —— 第 2 步交接快照；退出码仅 0/1，不动。
12. 不在 pre-push 直接链上的 rc=2 出口，逐条登记不改：`data_scripts/acceptance-evidence.py:395` / `:402` / `:411` 是硬失败（取不到改动集合、幻影路径）；`data_scripts/build-delete-manifest.py:102` 是用法/错误退出；`data_scripts/check-anchor-card.py:240` 与 `data_scripts/check-triple-anchor.py:210` 是 EMPTY rc=2，门禁内已有非空守卫才调用；`data_scripts/check-design-tokens.py:272` / `:278` 是拒绝覆盖锁定契约与源码根不存在的错误语义；`data_scripts/check-lock-rule.py:205` 是 `--help` 用法退出；`data_scripts/check-package-trigger.py:211` / `:217` 是凭据缺失与基础设施错误硬失败；`data_scripts/check-problem-finding.py:421` 是 EMPTY rc=2（门禁调用处守卫情况见新发现问题节）；`data_scripts/emit-shard-lists.py:314`、`data_scripts/fetch-apk.py:566` / `:819`、`data_scripts/find-hang-culprit.py:315` / `:343`、`data_scripts/watch-ci.py:88` 是工具脚本不在链上；`data_scripts/is-legacy-headonly.py:31` 是缺参数用法退出，pre-push 调用处忽略其退出码；各脚本开场 `cd ... || exit 2` 是脚本自身定位失败语义，与子步骤判码无关。

## 三、分流实现

改动集中在 `data_scripts/pre-push-check.sh` 三处：

1. 状态区新增 `EMPTY_COUNT=0` 与 `EMPTY_STEPS=()`（`data_scripts/pre-push-check.sh:33` / `:34`），与既有 `FAILED` / `FAIL_STEPS` 并列。
2. `run_step` 分流（`data_scripts/pre-push-check.sh:43` 起）：先落 `/tmp` 日志再取子进程真实退出码；`rc=2` 走 EMPTY 分支——计数加一、步骤名入 `EMPTY_STEPS`、打印 EMPTY 行，不置 `FAILED`、不入 `FAIL_STEPS`；`rc=1` 及其他非零码走原真红分支，记失败并保留原有原因提取；`rc=0` 打印 PASS。
3. 末段聚合三态（`data_scripts/pre-push-check.sh:154` 起）：有真红 → 退出 1，失败清单末行附注 EMPTY 步数；无真红但有 EMPTY → 退出 2，末行汇总 EMPTY 步骤名并注明空集≠核验通过；全绿 → 退出 0。

二阶推演：rc=2 不折成普通成功态，聚合退出码保留 0/1/2 三态，外层按退出码判仍能区分全绿、真红、仅 EMPTY；EMPTY 步骤不中断遍历，后续步骤照常执行；除 rc=2 单列外，其余非零码仍按旧规则记 FAIL，既有判据未放宽。三阶影响：`rule-gate.sh` 不消费 pre-push 的退出码，本脚本三态只影响手动预推与外层调用方判码；子闸自身的 EMPTY 契约（打印 EMPTY、退出 2）未改。

## 四、自测与机检真实末行

语法与自测命令、末行：

- `bash -n data_scripts/pre-push-check.sh`：无输出错误，`bash -n OK`。
- `python3 data_scripts/check-report-fields.py --selftest` 末行：`[selftest] 夹具已自删，双向自测通过。`（rc=0；含空集通路断言退出码 2）。
- `python3 data_scripts/check-evidence.py --selftest` 末行：`SELFTEST: PASS（真路径文件 PASS，假路径文件 FAIL）`（rc=0）。
- `python3 data_scripts/check-gate-wiring.py` 末行：`GATE-WIRING: PASS（60 个门禁均接线；历史孤儿基线 0 个）`（rc=0）。

必失败样本（真红 rc=1，替身先退 1、后接退 0 步骤验证遍历不中断）：

- 命令：`bash /tmp/repro-prepush/sample-red.sh`，退出码 1。
- 末段：`PRE-PUSH: FAIL —— 失败步骤清单（1 项；另有 EMPTY 空集 0 步未记失败）：`，清单内 `✗ 真红样本｜FAIL docs/修复-x.md：A 首行缺锚点卡`；后续绿灯步仍打印 `PASS 后续绿灯步`。

必放行样本（空集 rc=2，替身先退 2、后接退 0 步骤验证继续后续步骤）：

- 命令：`bash /tmp/repro-prepush/sample-empty.sh`，退出码 2。
- 末段：`PRE-PUSH: PASS（无真红；EMPTY 空集 1 步：3.5-报告字段；空集≠核验通过，待检对象出现后须重跑）`；失败清单为空，后续绿灯步仍打印 `PASS 后续绿灯步`。

混合样本（rc=2 与 rc=1 同轮）：

- 命令：`bash /tmp/repro-prepush/sample-mixed.sh`，退出码 1。
- 末段：`PRE-PUSH: FAIL —— 失败步骤清单（1 项；另有 EMPTY 空集 1 步未记失败）：`，清单内 `✗ 真红样本｜FAIL docs/修复-x.md：D 证据锚点仅 0 处`。

## 五、自查反例

反例一：若把 rc=2 直接并入 PASS 分支，空集与全绿在退出码上无法区分，外层按 rc 判会把"没东西可验"当成"验过且合规"，㊾ 空集假绿口径被架空；故聚合保留退出码 2，不并入 0。

反例二：若把所有非零码（含 rc=1、脚本崩溃的其他码）都按 EMPTY 放行，真红会被静默放过；故实现里只有 `rc -eq 2` 走 EMPTY，其余非零码仍走原真红分支。

反例三：若 EMPTY 分支中断后续步骤，一个空集会跳过后面的编译面预检与债务棘轮，真红被漏检；故 EMPTY 只计数与提示，不 `exit`、不跳过后续 `run_step`。

## 新发现问题

1. `data_scripts/check-vacuous-pass.py:88` 旧豁免条目（三元组键内块指纹 `53fabe213b5e`）已随 `data_scripts/check-report-fields.py` 空集分支改 return 2 失配：现块复算指纹为 `38d46d4c8d6a`，且扫描器只对含 `return 0` 的空集分支建键，现分支 return 2 根本不进命中集，该条目查表时不被咨询，属死条目。处置状态：待裁——本单不顺手摘除；理由是豁免表维护属独立判据面，死条目当前不产生误报（㊾ 闸实跑 rc=0），按最小改动与边界约束留待下次维护豁免表时连同其他失配条目一并清理。归属：㊾ 空集假绿闸豁免表维护面。
2. `data_scripts/check-problem-finding.py:421` 空集退出码 2，而 `data_scripts/rule-gate.sh` 第 55 项直接以 `$? -eq 0` 判红绿，未做空集守卫；本轮工作区有改动报告未触发，干净树空集时该 rc=2 可能被记成 bad。处置状态：待裁——归属 `rule-gate.sh` 第 55 项与 `check-problem-finding.py` 调用口径；本单边界只改 pre-push 脚本，不动 gate 内部分流，登记交下一棒核对。
3. pre-push 直接链上其余 rc=2 出口已穷举核对完毕：仅两处 EMPTY 语义纳入分流，其余子脚本退出码仅 0/1。处置状态：已闭环——本单穷举面见第二节清单，无遗漏待裁项。

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「永远找问题，解决问题，提出问题」（docs/机制-总控必读.md:546）
- 适用条款：改动后先 grep 回证确认代码真写入、未生效不报完成；规格未覆盖边界自行兜底不降级不砍功能；每轮登记新发现问题与处置状态；空集不判 PASS，空集与真红分流记录。
