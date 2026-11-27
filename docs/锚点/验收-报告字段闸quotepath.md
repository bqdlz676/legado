# 锚点卡 · 验收-报告字段闸 quotepath 路径识别

路径：docs/锚点/验收-报告字段闸quotepath.md
回源：python3 data_scripts/check-report-fields.py --selftest
回源：python3 data_scripts/check-report-fields.py（聚合模式）
回源：python3 data_scripts/check-report-fields.py docs/修复-报告字段闸quotepath.md（显式路径）

存在类别：机制闸 3.5 报告字段门禁（data_scripts/check-report-fields.py）独立验收；被验缺陷形态＝聚合口径中文未跟踪/修改报告被八进制转义串漏识别，空集分支由旧 PASS 改为 EMPTY rc=2。

## 一、验收对象与边界

- 被验件：data_scripts/check-report-fields.py（工作树与 HEAD 一致，git diff HEAD 无差异）；对照旧版取 git show 4cbb174:data_scripts/check-report-fields.py。
- 验收只读：不改被验脚本、不做 git 写操作、不跑 gradle/kotlinc；独立夹具在 /tmp 临时迷你 git 仓内构造与还原。
- 边界：仓内其它同族 quotepath 缺口脚本只复核写码方登记清单是否准确，不代改。

## 二、六组判据对应位置

1. 必失败样本复现：旧版聚合识别 0 份中文报告并报旧版空集 PASS；新版聚合识别在飞中文报告并逐份判定。
2. 放行样本：英文前缀未跟踪报告识别；非报告 docs 与代码件不混入；git mv 重命名行取箭头右侧且旧路径不残留；显式传路径绕开聚合直判。
3. EMPTY 语义：空集报 EMPTY、rc=2；逐一核对 rule-gate.sh、pre-push-check.sh 及其它脚本/工作流调用方是否按 rc 判红。
4. is_anchor_card：跳过规则只看目录不看文件名；与 check-anchor-card.classify 的 other 分支口径同构；评估真报告被漏判的残余风险。
5. 字段判据：A/B/C/D 四类字段、only_backfill 豁免、is_fixture 排除与旧版逐字比对，只允许路径获取改动。
6. 自测接线：--selftest 末行、check-gate-wiring.py、bash -n rule-gate.sh；五个同类缺口登记清单与行号逐条 grep 复核。

## 三、机检回源命令

- python3 data_scripts/check-report-fields.py（聚合）
- python3 data_scripts/check-report-fields.py --selftest
- python3 data_scripts/check-anchor-card.py --strict
- python3 data_scripts/check-problem-finding.py
- python3 data_scripts/check-onboarding.py
- python3 data_scripts/check-gate-wiring.py
- bash -n data_scripts/rule-gate.sh
