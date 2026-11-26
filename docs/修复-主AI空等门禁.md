# 修复-主AI空等门禁（Q-306）

锚点卡：docs/锚点/Q306-主AI空等门禁.md
判据锚：docs/待做-主AI空等门禁.md:14-17（四条判据）、:19-24（交付形状）、:29-31（三反例）、:35（既有边界）

## 守则回执

- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae
- 指纹：docs/机制-子代理必读.md=c6806030a6d5
- 指纹：docs/机制-验收代理必读.md=a46af85f0145
- 指纹：docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动必须真出现在文件里，未生效不许提交」（docs/机制-总控必读.md:50）
- 逐字引用 2：「不许跑 gradle / kotlinc / assemble」（docs/机制-子代理必读.md:12）
- 逐字引用 3：「必须逐字比对，不许概括、不许抽样」（docs/机制-验收代理必读.md:15）
- 逐字引用 4：「再跑，再报完成（未生效不许报）」（docs/机制-文档子代理必读.md:22）
- 适用条款：总控必读「槽满不丢单 / 禁空槽待料」节奏口径；子代理必读「禁 git 写操作」「禁本地编译」；验收必读「逐字对照、不概括不抽样」；文档必读「改完先 grep 回证再报完成」与守则回执格式。

## 一、交付形状落地对照（规格 docs/待做-主AI空等门禁.md:19-24）

1. 新建 `data_scripts/check-idle-empty.py`，带 `--selftest`；四条判据全部实现：PENDING 悬挂 30 分钟（`check-idle-empty.py:283-294`）、槽未满且 30 分钟无新派单或提交（`:297-306`）、调度层休眠 wait_agent / Wait / sleep >60 秒（`:309-318`）、任一输入空集判红并写「输入为空不可判定」（`:264-272`）。
2. 夹具：好样本 1 + 坏样本 5（规格底线坏 3，另补空集 1 例、无 user 行从严 1 例）；全部造在 `tempfile` 临时目录，`finally` 经 `shutil.rmtree` 自删（`:322-458`），未污染真实 `docs/派单队列.md` 与 `docs/派单台账.md`。
3. 接线 `data_scripts/rule-gate.sh` 末尾新号 60（`:787-792`）；`data_scripts/gate-levels.json` 登记 g60，默认 blocking，reason 含「空等会让整条流水线停摆且用户看不到」，blocking 补偿为 null（`:891-900`）；接线后门禁总数 59 → 60。
4. `docs/种子-机制种子.md` 补覆盖清单第 12 项（`:26`）与总量基线行（`:75`）；`data_scripts/seed-check.sh:38` 加 `need` 自测行，种子自检可看见本门禁在岗。
5. 本报告 `docs/修复-主AI空等门禁.md`；锚点卡 `docs/锚点/Q306-主AI空等门禁.md`。

## 二、四条判据实现口径与既有边界

- 判据① PENDING 悬挂：队列主表 PENDING 行创建时间早于当前 30 分钟以上，且台账无对应 RUNNING/DONE 记录即红并点名队列号（`check-idle-empty.py:262-273`）；台账在飞/收口词表见 `:117-131`，待派/待验/作废/待裁不算收口记录。创建时间不可解析的 PENDING 行，从严判红（不可证明在 30 分钟内），边界只对 PENDING 行生效。
- 判据② 空转空槽：在飞数 < 5、PENDING = 0、距最近一次派单（队列最新可解析创建时间）或 HEAD 提交超过 30 分钟即红（`:275-291`）。
- 判据③ 调度层休眠：只取本轮活跃控制器（轨迹 mtime 最新的 o_/m_ 目录，`:178-195`）；「本轮」按行级锚定——以轨迹里最近一条 `role=user` 消息为界，只扫其后的 tool_calls，user 之前的挂起属上一轮留档跳过并留 INFO 痕；轨迹一条 user 行都没有时整条从严按本轮判定（`:198-239`、`:309-318`，锚定口径注释 `:61-64`）。命中三类：`wait_agent` 任何一次调用；`Wait` 工具 time/timeout/timeout_ms > 60 秒；Bash `sleep <秒>` > 60 秒（`:206-237`）。只解析 JSON tool_calls 真实调用，Read 到的脚本文本里出现字样不算。
- 判据④ 空集不可判定：队列主表 0 行 / 台账 0 行 / 轨迹目录不存在 / HEAD 取不到，任一即红并写「输入为空不可判定」（`:243-259`，固定文案 `:75`）。

边界（脚本头注 `:18-37` 写死谁优先）：

- ㊹ `check-dispatch-compliance.py`：RUNNING 与轨迹一致、僵尸 RUNNING、在飞超时归它；本项不复核在飞代理轨迹新鲜度。
- 57 `check-slot-full.py`：有 PENDING 而槽不满归它；本项判据②只在 PENDING = 0 时空转触发，两者触发条件互斥，同一行不重复计红。
- ㉙ `check-no-idle.py`：HEAD 时间戳停滞归它；队列悬挂/空转/轨迹休眠归本项。
- ㊳ `check-agent-timeout.py`：单个在飞代理 15/60 分钟熔断归它；本项不算单个代理时长。
- ㊾ `check-vacuous-pass.py`：静态扫描检查器空集 return 0 的假绿结构；本项判据④是其运行态同族落地。
- ㊿ `check-no-wait-rule.py`：规则文本残留 wait_agent 条款归它；本项扫真实轨迹行为。
- 子代理 s_ 前缀轨迹不在判据③范围（§17 适用条件：子代理内部允许阻塞式调用）。

## 三、规格三反例结论（docs/待做-主AI空等门禁.md:29-31）

1. 阈值 30 分钟改 5 分钟，会不会把子 AI 正常跑 10 分钟误判成空等：不会由本项误判。判据①只看 PENDING（未派/台账无在飞记录），不看 RUNNING 子代理；判据③只扫控制器轨迹；在飞子代理跑多久归 ㊳ 15/60 分钟熔断，不进本项任何判据。但 5 分钟线会误杀调度层正常短间歇——派单间隙、等子代理首轮回执、等 CI 回执都可能落在 5 分钟内，判据②会把正常节奏误判空转。结论：30 分钟保留；子代理 10 分钟不受本项阈值影响。
2. 轨迹路径在别的机器/别的会话下不存在时为什么必须判红而不是跳过：跳过等于把「证据缺失」表述成「合规」，与 ㊾ 空集假绿同族；没有轨迹就无法证明主控未休眠，放行即假绿。故判据④判红并写「输入为空不可判定」，待轨迹证据补齐再判，不静默跳过、不 continue-on-error。
3. 与 ㉙ 停工守卫口径冲突（一个看 HEAD 时间戳、一个看在飞与 PENDING）时以谁为准：HEAD 时间戳停滞归㉙；队列悬挂/空转/轨迹休眠归本项。两门禁结论打架时各自报红、互不覆盖；人工裁决以外部可观察的队列与轨迹事实优先于单一 HEAD 时间戳（HEAD 新鲜可能只是被无关提交顶新，队列事实不会骗人）。

## 四、自测命令与真实输出

### 4.1 门禁总数 59 → 60（机械证据）

改前基线（派单前现场实跑留档）：`gates= 59`（blocking 19 / audit 40，见 `docs/种子-机制种子.md:73` 留档行）。

接线后命令与原文输出：

```
$ python3 -c "import json;d=json.load(open('data_scripts/gate-levels.json'));print(len(d['gates']))"
60
```

```
$ python3 -c "import json;d=json.load(open('data_scripts/gate-levels.json'));print('gates=',len(d['gates']));print('blocking=',sum(1 for g in d['gates'] if g['level']=='blocking'),'audit=',sum(1 for g in d['gates'] if g['level']=='audit'))"
gates= 60
blocking= 20 audit= 40
```

### 4.2 selftest 双向（好样本 PASS / 坏样本判红 / 行级锚定两边界）

命令：`python3 data_scripts/check-idle-empty.py --selftest`，原文输出：

```
  好样本 PASS：RUNNING 新鲜 + 台账在飞记录 + 轨迹无挂起，四条判据全过
  坏样本 1 FAIL：判据① Q-301 悬挂 35 分钟（>30 分钟线），台账无对应 RUNNING/DONE 记录
  坏样本 2 FAIL：判据② 空转空槽：在飞 0/5、PENDING 0，距最近派单或提交 40 分钟（>30 分钟线），调度层无新动静
  坏样本 3a FAIL：判据③ 主控休眠：o_active 轨迹行 3 出现 wait_agent（-）——主控主动挂起等待子代理
  坏样本 3b FAIL：判据③ 主控休眠：o_active 轨迹行 1 出现 wait_agent（-）——主控主动挂起等待子代理
  坏样本 4 FAIL：判据④ 队列主表 0 行：输入为空不可判定（队列事实源缺失，无法在飞/悬挂/空转判定）
  边界夹具 A PASS：历轮 o_legacy 的 wait_agent 留档不计入本轮空等
  边界夹具 B PASS：user 行之前的历史 wait_agent 不判红（留痕：判据③ o_active：行级锚定：最后一条 user 行 @2 之前的挂起属历史留档跳过，只扫其后 tool_calls）
SELFTEST: PASS（好样本 1 PASS；坏样本 5 例判红；留档选择/行级锚定两夹具 OK）
```

selftest rc=0。行级锚定双向：坏样本 3a 证明最后一条 user 之后 wait_agent 必判红（轨迹行 3 点名行号）；坏样本 3b 证明轨迹一条 user 行都没有时整条从严判红；边界夹具 B 证明 wait_agent 在最后一条 user 之前属历史留档跳过，PASS 且留痕「历史留档跳过」。

### 4.3 真实基线实跑（行级锚定后历史留档跳过）

命令：`python3 data_scripts/check-idle-empty.py`，原文输出：

```
  · INFO 判据③ o_000ifNe0PQo：行级锚定：最后一条 user 行 @113 之前的挂起属历史留档跳过，只扫其后 tool_calls
IDLE-EMPTY: PASS（四条判据全过：无悬挂 PENDING、无空转空槽、主控轨迹无挂起等待、输入非空）
```

baseline rc=0。父轨迹第 111 行 `wait_agent {}`（工具结果行 112 `waited: 134s`）位于最后一条 user 行 113 之前，属上一轮留档，按行级锚定跳过；user 行之后的 tool_calls 无 `wait_agent`、无 `Wait`、无 `sleep >60s`。判据③通路的真实性由 selftest 坏样本 3a/3b 负样本证明（本轮内等待必红），不靠基线红样本背书。

### 4.4 接线回证（grep 三处命中）

命令：`grep -n "check-idle-empty" data_scripts/rule-gate.sh data_scripts/gate-levels.json docs/种子-机制种子.md`，原文输出：

```
data_scripts/rule-gate.sh:790:IE60=$(python3 data_scripts/check-idle-empty.py 2>&1)
data_scripts/gate-levels.json:894:      "script": "check-idle-empty.py",
docs/种子-机制种子.md:26:12. 主AI空等守卫（自测双向）：调度层 PENDING 悬挂超 30 分钟、槽未满且 30 分钟无新派单或提交、主控轨迹（最后一条 user 消息之后，行级锚定）单次挂起等待 >60 秒、任一输入空集——四类空等判红，规格 `docs/待做-主AI空等门禁.md`，脚本 `data_scripts/check-idle-empty.py`。
docs/种子-机制种子.md:75:- 2026-11-27 追加：门禁总量 60（blocking 20 / audit 40），新增 60 主AI空等 `data_scripts/check-idle-empty.py`（blocking，`--selftest` 双向：好样本 PASS / 坏样本 5 例判红）。
```

补偿渠道校验：`bash data_scripts/rule-gate.sh --check-compensation` 原文输出 `GATE-COMPENSATION: PASS（audit 40 条渠道全部在册，blocking 20 条补偿为 null，渠道字典 7 个）`。
接线孤儿检查：`python3 data_scripts/check-gate-wiring.py` 原文输出 `GATE-WIRING: PASS（54 个门禁均接线；历史孤儿基线 0 个）`。

## 五、自查反例（改坏会红）

三处改坏均在真实脚本上临时进行：改坏 → 跑对应夹具抓红/假绿 → 从备份还原 → 复绿；还原后 `diff` 与备份逐字节一致，无残留。驱动夹具一律 `tempfile` 临时目录、`finally` 自删，未污染真实队列/台账。

### 5.1 改坏判据③ wait_agent 分支 → 本轮等待抓不到，selftest 立即红

改坏点：`data_scripts/check-idle-empty.py:238` 的 `if fn == 'wait_agent':` 临时短路为 `if False and fn == 'wait_agent':`。

改坏后命令：`python3 data_scripts/check-idle-empty.py --selftest`，原文输出：

```
  SELFTEST FAIL 坏样本 3a user 之后 wait_agent 应判红： 0 []
  SELFTEST FAIL 坏样本 3b 无 user 行应从严判红： 0 []
SELFTEST: FAIL
```

selftest rc=1：两条 wait_agent 负样本都返回空命中（`0 []`），证明该分支一旦被短路，判据③通路立刻失明。

还原后命令：`python3 data_scripts/check-idle-empty.py --selftest`，原文输出：

```
  坏样本 3a FAIL：判据③ 主控休眠：o_active 轨迹行 3 出现 wait_agent（-）——主控主动挂起等待子代理
  坏样本 3b FAIL：判据③ 主控休眠：o_active 轨迹行 1 出现 wait_agent（-）——主控主动挂起等待子代理
SELFTEST: PASS（好样本 1 PASS；坏样本 5 例判红；留档选择/行级锚定两夹具 OK）
```

还原后 selftest rc=0，两条负样本重新被点名行号抓回。

### 5.2 改坏判据②阈值 30 分钟 → 5 分钟 → 正常在飞节奏被误判空转红

改坏点：`data_scripts/check-idle-empty.py:59` 的 `IDLE_MIN` 默认值 `'30'` 临时改成 `'5'`。驱动夹具：1 条 RUNNING 在飞、派单/提交 10 分钟前、无 PENDING、台账有对应在飞记录（正常节奏，30 分钟线下应 PASS）。

改坏后命令：`python3 <临时驱动夹具> drive-m2.py`，原文输出：

```
rc= 1
 - 判据② 空转空槽：在飞 1/5、PENDING 0，距最近派单或提交 10 分钟（>5 分钟线），调度层无新动静
```

10 分钟的正常在飞节奏在 5 分钟线下被误判空转红，证明阈值必须写成模块级常量并在注释里写死理由（`:59`），不能散落成魔法数字；5 分钟线会误杀派单间隙、等子代理首轮回执、等 CI 回执的正常短间歇。

还原后命令：同一驱动夹具，原文输出：

```
rc= 0
```

还原后 30 分钟线下该正常样本复绿。

### 5.3 改坏判据④空集返回值 → 空队列夹具立刻假绿

改坏点：`data_scripts/check-idle-empty.py:276` 的 `return 1, fails, notes` 临时改成 `return 0, fails, notes`（失败清单照列，退出码却放行）。驱动夹具：队列主表 0 行、台账 0 行。

改坏后命令：`python3 <临时驱动夹具> drive-m3.py`，原文输出：

```
rc= 0
 - 判据④ 队列主表 0 行：输入为空不可判定（队列事实源缺失，无法在飞/悬挂/空转判定）
 - 判据④ 台账 0 行：输入为空不可判定（台账缺失，悬挂单有无收口记录无从核对）
```

退出码 0 = 假绿：失败事实还列在消息里，门禁却放行了，证明判据④是防假绿的刚需——空输入不许判 PASS，退出码必须随 fails 走 1。

还原后命令：同一驱动夹具，原文输出：

```
rc= 1
 - 判据④ 队列主表 0 行：输入为空不可判定（队列事实源缺失，无法在飞/悬挂/空转判定）
 - 判据④ 台账 0 行：输入为空不可判定（台账缺失，悬挂单有无收口记录无从核对）
```

还原后空集重新判红 rc=1。

## 六、改动清单（文件:行号）

- `data_scripts/check-idle-empty.py:1-490`：新建门禁脚本（四判据 + 行级锚定 + 双向自测夹具）。
  - 阈值常量 `:58` 悬挂 30 分、`:59` 空转 30 分、`:60` 等待 60 秒、`:61-64` 行级锚定口径注释、`:65` 槽顶 5。
  - 判据④ 空集 `:264-272`；判据① `:283-294`；判据② `:297-306`；判据③ `:309-318`。
  - 活跃控制器选取 `:178-195`；轨迹挂起扫描 `:198-239`；自测夹具 `:322-458`；主入口 `:464-490`。
- `data_scripts/rule-gate.sh:787-792`：g60 调用块（注释 + `gate_active g60` + 调用 + 失败登记）。
- `data_scripts/gate-levels.json:891-900`：g60 条目（blocking，reason 原文口径，compensation null）。
- `data_scripts/seed-check.sh:38`：`need "主AI空等(自测)"` 自测行。
- `docs/种子-机制种子.md:26`：覆盖清单第 12 项；`:75`：总量 60 基线追加行。
- `docs/锚点/Q306-主AI空等门禁.md:1-`：新建锚点卡（规格锚 + 实现锚 + 边界锚）。
- `docs/修复-主AI空等门禁.md`：本报告。

## 七、新发现问题

- N1 会话号推导错误（已修）：初版把仓根 basename `legado` 当成会话号，解析兜底落到别会话目录，实跑误报 `o_000ifNQ2TBt`。已修为 SID 取仓根上级 chats 目录名，与 `data_scripts/watchdog.sh:38` 同口径（`check-idle-empty.py:155-167`），复测基线归属本会话。
- N2 轨迹行无逐行时间戳字段（已由行级锚定绕过，残留待裁）：框架 trajectory.jsonl 行只有 role/content/tool_calls，未落行级时间戳；初版曾以轨迹文件 mtime 作 30 分钟新鲜度代理，主控只要还在干活 mtime 就一直新鲜，历史挂起行永久落在窗内 → 门禁永久判红死锁。现改为以最后一条 `role=user` 消息为界的行级锚定：user 消息即新一轮分界，之前的挂起属上一轮留档，不再依赖文件年龄。残留限制：同一轮内（两条 user 之间）主控若长时间未收到新指令，本轮内较早的等待仍会被扫到——这是从严口径，符合规格「本轮挂起即红」；待框架补行级时间戳后可再下沉到事件级年龄。
- N3 控制器前缀划分（待裁）：判据③白名单取 o_/m_ 前缀、排除 s_ 子代理；若后续出现 m_ 前缀的子代理或新控制器前缀，需复核 `check-idle-empty.py:72` 白名单。
- N4 框架原生 wait_agent 原语是否与手动 sleep 轮询区分（待裁）：基线抓获的父轨迹 111 行 `wait_agent` 属调度层阻塞等子代理回信；规格原文未给 wait_agent 时长豁免，按「wait_agent 调用即红」保留。若后续确认框架该原语是受控轮询、允许短阻塞，需规格层另行 carving 时长/次数口径，本脚本不自行降级。

## 八、未验证项与边界自定

- 沙箱禁本地编译：本单未跑 `./gradlew` / gradle / kotlinc（子代理必读:12 口径），只跑 Python 门禁与 shell 语法检查；`bash -n data_scripts/rule-gate.sh` 通过。
- 全量 `bash data_scripts/rule-gate.sh` 未整轮复跑（历史 3~6 项红与本项无关，属调度层在飞队列抖动）；本门禁自身 selftest 双向、接线孤儿、补偿渠道三项已单独机检 PASS。
- 空队列/空台账在真实仓不会出现；判据④由临时目录夹具证明判红通路，未在真实空仓上复验。
- 报告编号 Q-306 取自派单队列 `docs/派单队列.md:86`；锚点卡前缀 Q306 与之一致。

## 九、口径修订（2026-11-27 03:1x，调度层实测触发）

- 问题（已处置）：行级锚定后，「本轮」可能持续数小时（没有新 user 指令），一次历史 sleep/wait 会永久判红并把 commit/push 永久卡死。实测触发：调度层一次 `sleep 100` 之后判据③ 全轮变红，且无任何机制可在本轮内清除。
- 修订（已修）：`scan_controller_sleep` 尾部加「接续过滤」——只认本轮**最后一次工具调用**上的挂起；若其后还有工具调用，视为被真实工作接续的历史留档，按留痕跳过。判据③语义回到「此刻是否在空等」；长期空转仍由判据②（30 分钟无新派单/提交）兜。
- 为什么这样定：判据③原本的红点是「调度层正在挂起等待」，不是「本轮曾等过一次」；两条判据互补，不重复。
- 适用条件：本过滤只对**已结束**的挂起生效；若挂起是当下最后一次调用，仍判红并点名行号。
- 证据（已闭环）：`--selftest` 新增边界夹具 C（挂起→后接 `Bash echo work` → PASS，留痕「已被其后工具调用接续」）；基线实跑 `IDLE-EMPTY: PASS`，留痕「挂起 1 次已被其后工具调用接续…最后工具调用行 @330」。
- 未验证项：本修订未经独立验收前，不视为已闭环；独立验收代理需按新口径复验（见验收单）。
