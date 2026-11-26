> 锚点卡：`docs/锚点/验收-主AI空等门禁.md`（本卡自身路径，供门禁⑲自校验）

> E1 命令证据：`cd /home/user/Doubao/chats/38454122521460994/legado && python3 data_scripts/check-idle-empty.py --selftest`（真实输出 `SELFTEST: PASS（好样本 1 PASS；坏样本 5 例判红；留档选择/行级锚定/接续三夹具 OK）`，rc=0）；`python3 data_scripts/check-idle-empty.py`（真实输出 `IDLE-EMPTY: PASS`，rc=0）。
# 锚点卡 · 验收-主AI空等门禁（Q-306 独立复核回执）

> 对象：`data_scripts/check-idle-empty.py`（门禁 g60，主 AI 空等守卫）、`data_scripts/rule-gate.sh`（g60 调用块）、`data_scripts/gate-levels.json`（g60 条目）、`data_scripts/seed-check.sh`（need 行）、`docs/种子-机制种子.md`（在岗登记）；上游写码回执 `docs/修复-主AI空等门禁.md`；上游锚点卡 `docs/锚点/Q306-主AI空等门禁.md`。
> 规格锚：`docs/待做-主AI空等门禁.md:14-17`（四条判据）、`:19-24`（交付形状）、`:29-31`（三反例）、`:35`（既有边界）。
> 本卡为验收类回执卡；验收结论以 `docs/验收-复核-主AI空等门禁.md` 为准。未 git 写操作；未本地编译。

## 一、验收判据锚（回原文）

- 判据① PENDING 悬挂：`docs/待做-主AI空等门禁.md:14`。
- 判据② 槽未满空转：`docs/待做-主AI空等门禁.md:15`。
- 判据③ 调度层休眠：`docs/待做-主AI空等门禁.md:16`（本轮口径经 2026-11-27 03:1x 修订为「行级锚定 + 接续过滤：只认本轮末条工具调用上的挂起」）。
- 判据④ 空集不可判定：`docs/待做-主AI空等门禁.md:17`。

## 二、实现锚（当前 HEAD 工作区，符号优先）

- 阈值常量：`check-idle-empty.py::STALE_MIN`（:58）、`::IDLE_MIN`（:59）、`::WAIT_SEC`（:60）、`::SLOT_CAP`（:65）、`::CONTROLLER_PREFIXES`（:79）、`::EMPTY_MSG`（:81）。
- 判据④ 空集：`check-idle-empty.py::run` :279-287。
- 判据① 悬挂：`check-idle-empty.py::run` :298-309；台账收口集合 `::ledger_done_ids` :124-139。
- 判据② 空转：`check-idle-empty.py::run` :311-319。
- 判据③ 休眠：活跃控制器 `::active_controller` :178-195；轨迹扫描 `::scan_controller_sleep` :198-268（行级锚定 :225-231；接续过滤 :254-267）；命中点名行号 `::run` :322-329。
- 自测夹具：`::selftest` :337-490（好样本 1 + 坏样本 5 + 边界夹具 A/B/C）；主入口 `::main` :493-515。
- 接线：`rule-gate.sh:789-792`（g60 块）；`gate-levels.json:891-900`（g60，blocking，compensation null）；`seed-check.sh:38`；`docs/种子-机制种子.md:26、:75`。

## 三、验收结论锚

- 总结论：返工（脚本行为侧 PASS；文档同步侧 D1~D8 未销项，返工范围限文档）。
- 缺陷清单与逐条证据：`docs/验收-复核-主AI空等门禁.md` 第五节。
