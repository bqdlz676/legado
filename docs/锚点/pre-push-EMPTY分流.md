# 锚点卡 · pre-push EMPTY 分流（rc=2 空集提示 vs rc=1 真红）

路径：docs/锚点/pre-push-EMPTY分流.md
回源：bash -n data_scripts/pre-push-check.sh
回源：python3 data_scripts/check-report-fields.py --selftest
回源：python3 data_scripts/check-gate-wiring.py

存在类别：本地预推门禁脚本分流修复；直接子闸 check-evidence.py / check-report-fields.py 的 EMPTY 退出码 2；㊾ 空集不判 PASS 口径。

## 一、对象与改动面

- 只改 `data_scripts/pre-push-check.sh`：`run_step` 退出码分流与末段三态聚合；不改 `app/src` 任何产品代码，不改其他门禁脚本判据。
- 分流前现状：`run_step` 对 rc≠0 统一记失败（旧 :39），EMPTY 空集提示（子闸退出码 2）被一并记成步骤 FAIL，干净树本地预推出现假红。
- 分流后口径：rc=0 PASS；rc=2 EMPTY（空集无待检对象，不记失败、计入 EMPTY 清单后继续下一步）；rc=1 及其他非零码按真红记 FAIL。
- 聚合退出码三态：0 全绿（无 FAIL 无 EMPTY）；1 有真红；2 无真红但有 EMPTY（空集≠核验通过，外层按退出码可与全绿区分）。

## 二、机械可判判据

1. 真红样本：子命令退出码 1 → 必须进 FAIL_STEPS，`FAILED=1`，聚合退出码 1，末行列出失败步骤。
2. 空集样本：子命令退出码 2 → 不进 FAIL_STEPS、不置 FAILED，计入 EMPTY_COUNT/EMPTY_STEPS，后续步骤照常执行；聚合无真红时退出码 2，末行汇总 EMPTY 步骤名。
3. 混合样本：同一轮内 rc=2 与 rc=1 并存 → 真红记 FAIL、EMPTY 计数仍进末行附注，聚合退出码 1。
4. 不放宽既有判据：除 rc=2 单列 EMPTY 外，其余非零码仍按旧规则记 FAIL；未改动任何子闸的红/绿判据。

## 三、穷举面（pre-push 直接链上 rc=2 归属）

- `data_scripts/check-evidence.py:175`：EMPTY 空集，非失败语义，rc=2。
- `data_scripts/check-report-fields.py:259`：EMPTY 空集，非失败语义，rc=2。
- 其余直接子脚本（Kotlin 语法、依赖符号、守则回执、只读 setter、漂移棘轮、跨模块预检、债务棘轮）退出码仅 0/1，无 2 语义。
- `data_scripts/rule-gate.sh` 自身退出码仅 0/1；第 2 步交接快照脚本退出码仅 0/1。

## 四、口径边界

- rc=2 只认子闸既有契约：空集提示分支打印 EMPTY 并退出 2；不得把 rc=2 折成普通成功态传给上层。
- 本单不改 `rule-gate.sh` 内部分流；gate 内其他子闸的 EMPTY rc=2 守卫情况在修复报告「新发现问题」节登记。
- ㊾ 豁免表失配死条目的摘除属豁免表维护面，本单不顺手改，登记待裁。
