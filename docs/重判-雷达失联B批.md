锚点卡：docs/锚点/Q437-雷达失联B批.md

# 重判-雷达存量失联 B 批（Q-437，W-436）

> 一句话：对漂移雷达 B 批存量失联锚点逐条回源重判——文件真删且结论失效的标「已废」并写明承接物；符号改名/搬家的重贴到现行全路径:行并 grep 回证；找不到对应物的不硬标，登记待裁。只追加注记，不删证据，不改旧句语义。

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「5. **锚点强制**：每条结论必须带 `文件:行` 或 `commit sha`；下次要用直接 `sed -n 'X,Yp'` / `git show sha` 回源（回源成本 ≪ 记忆成本）。」（docs/机制-总控必读.md:108）
- 逐字引用 2：「5. **债务棘轮不许上调基线**：新增文件 ≤600 行是先决条件（事故四：731 行面板把 oversized_warn 顶到 51，停摆推送）。确需拆分就派"只拆不改"的单，不许 `--upgrade-baseline` 了事。」（docs/机制-总控必读.md:157）
- 逐字引用 3：「- 事故四：731 行新文件顶出债务棘轮 → lint S4 + 写码守则量化上限 + 台账 M-24。」（docs/机制-总控必读.md:165）
- 适用条款：锚点强制（每条处置保留可回源定位，旧锚不删）；债务棘轮不许上调基线（未改 drift-baseline.json，靠重判压回基线内）；改动后 grep 回证真写入；找不到对应物不硬标已废，转重贴锚并登记待裁。

## 一、批次范围与实测起数

- 批次边界：`/tmp/q436/临时-436切分.json` 的 `B` 数组，共 93 份报告；`A` 数组 77 份另有代理负责，本单未碰 A 内任何文件。
- 本批开始实测 **318 条**：DEADFILE（目标文件全仓已不存在）109 条，MISSING（目标文件在位、候选符号未在窗口命中）209 条。
- 本批结束实测 **209 条**：DEADFILE 0 条，MISSING 209 条，分布 77 份报告。即 **本批开始 318 条 → 结束 209 条**，本批清出 109 条（已废 VOID 99 条 + 重贴锚 10 条）。
- 全仓雷达口径另见第七节机检末行；基线 496 冻结于 e8f296c 雷达上线，本单未上调。

## 二、处置结论汇总

- 已废（VOID）99 条：目标文件或符号真删、原结论失效；行内或紧邻下行追加「已废（<原因>；以 <现行符号或后续报告> 为准）」，旧句与旧锚点保留为证据。承接物分六类，明细见锚点卡第四节。
- 重贴锚（repaste）10 条：符号改名/搬家，锚点改为现行全路径:行，行尾追加「（重贴锚 W-436，原 <基名> 第 <旧行> 行）」；10 条新锚均已回证文件存在、行号在界内。明细见锚点卡第三节。
- 待裁 209 条：目标文件在位、结论回源成立，未硬标已废；根因与未验证范围见第四节。

## 三、逐文件处理统计（已处置 25 份）

- docs/方案-A档Activity安全下线.md：已废 4。
- docs/机制-机检收口.md：已废 7。
- docs/机制-验收证据工作台.md：已废 2。
- docs/验收-B27-MapWebHolder孤儿.md：已废 1。
- docs/验收-B8-侧翼通视收口.md：已废 2。
- docs/验收-D2-O2O3下游消费与口径修正.md：已废 4。
- docs/验收-Q222-55与56门禁独立验收.md：已废 2。
- docs/验收-删除单编译风险.md：已废 11。
- docs/验收-复核-B3返工.md：重贴锚 6。
- docs/验收-复核-B6-03修补与g64加固.md：重贴锚 4。
- docs/验收-复核-C2R6.md：已废 2。
- docs/验收-复核-g61组合陷阱.md：已废 3。
- docs/验收-复核-g62棘轮与CI车道.md：已废 6。
- docs/验收-复核-g63影子声明.md：已废 8。
- docs/验收-复核-g63行注释栈.md：已废 2。
- docs/验收-复核-g63门禁加固.md：已废 7。
- docs/验收-复核-g65消费方反查.md：已废 15。
- docs/验收-复核-g65解析能力.md：已废 6。
- docs/验收-复核-g66实现成员override.md：已废 4。
- docs/验收-复核-g66改动面路径.md：已废 2。
- docs/验收-复核-g67语料断言.md：已废 1。
- docs/验收-复核-门禁自测第三批.md：已废 6。
- docs/验收-复核-门禁自测第二批.md：已废 2。
- docs/验收-层1-气泡链路.md：已废 1。
- docs/验收-新门禁交叉复核.md：已废 1。

待裁 77 份现存 MISSING 按目标文件簇聚合（条数）：B3SettingGroups.kt 30、B3SettingModels.kt 16、CrossingShell.kt 15、CampaignModels.kt 8、Enums.kt 7、B3PrefBridge.kt 6、ComponentState.kt 6、ComponentSystemContract.kt 4、SettingControls.kt 4、SourceManageSheets.kt 4、EraCandidateFilter.kt 4、TopicMarxCard.kt 3、ComponentStateCompat.kt 3、SettingSheets.kt 3、ContextExtensions.kt 3，其余 B3SheetTargets.kt、PlaceModels.kt、HalfSheetModel.kt、InkFrost.kt、BattleModels.kt 及散见文档锚各 1–2 条。文档锚 65 条（规格切片 / 台账 / 复盘 / 规则清单等 .md 引用），代码锚 144 条。

## 四、未验证项 / 待裁分类

- 文档锚 65 条：目标 .md 文件在位，雷达按代码符号匹配规格词、卡片名、编号缩写等候选，属结构性误判；结论回源成立，未逐条刷新行号，待脚本侧口径收窄后复扫。
- 代码锚 144 条：目标 .kt 文件在位，MISSING 主因有二——一是文件名 stem（纯数据 / 状态类 Model、State 文件）不在文件内部复现，候选符号匹配不上；二是历史行号相对现行版本漂移。未逐条核对现行落点行号，不硬标已废。
- 组件簇遗留：ComponentState / ComponentSystemContract / ComponentStateCompat 内旧 legacy 七态枚举与旧档位映射已在 B18 收口删除；现行承接为 contract 包 ComponentVisualState 枚举与 ComponentState.kt 内 InkInteraction（resolve / accentOverlayAlpha）。本批只回源读了现行源码确认承接物，旧报告逐条锚点未逐一映射到现行行号，登记待裁。
- B3 / crossing / settings / explore 簇：部分现行行号地图已核（B3SettingModels / B3SettingGroups / B3PrefBridge / B3SettingModelsTest、CrossingShell / CampaignModels / Enums、DiscoverModels / DiscoverViewModelTest、InkExploreModels），其余散见条目未核。
- 仓外样本锚点（如 Foo.kt 第 521 行、NoSuchFileXyz.kt 探针）属调度层 / 他批夹具，不在 B 数组，本单未碰。

## 五、新发现问题

- 注记锚点归属劫持（本轮已修）：已废 / 重贴注记里若写全 `路径.kt:行` 成功落点，锚点闸会把它设为后续裸区间锚点的归属文件；原指向已删旧文件的裸行号被劫持到现行存活文件，由 DEADFILE 变成新的 OUTOFRANGE。本批四报告共 22 条新 OUTOFRANGE 即此根因。修法：注记内成功落点写成散文「<文件> 第 N 行」，全路径不带冒号-数字相邻；完整旧锚 / 新锚串落在锚点卡。建议后续在锚点闸口径文档里补此条。
- 测试文件行数增长致旧行号整体漂移：DiscoverViewModelTest.kt 现行 381 行，早期快照约 219 行；旧用例裸行号锚点已散文化为「第 N 行」，不再作可解析锚点。
- 锚点闸提示 41 条基线键已不再命中（可跑 `--baseline-update` 收紧存量），属既有存量，不在本批处置范围，未动基线。
- 现存 MISSING 中相当比例是雷达候选提取口径造成的结构性假红；要继续降失联数，方向是脚本侧收窄候选（路径 / 通用词 / 文件名 stem 自指），而不是再改历史报告。本单无权改 `data_scripts/**`。

## 六、变更清单绝对路径

本单改动的历史报告（25 份，均在仓库内 docs/ 下，只追加注记 / 重贴锚，未删证据）：

- /home/user/Doubao/chats/38454122521460994/legado/docs/方案-A档Activity安全下线.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/机制-机检收口.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/机制-验收证据工作台.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-B27-MapWebHolder孤儿.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-B8-侧翼通视收口.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-D2-O2O3下游消费与口径修正.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-Q222-55与56门禁独立验收.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-删除单编译风险.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-B3返工.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-B6-03修补与g64加固.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-C2R6.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g61组合陷阱.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g62棘轮与CI车道.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g63影子声明.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g63行注释栈.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g63门禁加固.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g65消费方反查.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g65解析能力.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g66实现成员override.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g66改动面路径.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-g67语料断言.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-门禁自测第三批.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-门禁自测第二批.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-层1-气泡链路.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/验收-新门禁交叉复核.md

本单新建交付物：

- /home/user/Doubao/chats/38454122521460994/legado/docs/重判-雷达失联B批.md
- /home/user/Doubao/chats/38454122521460994/legado/docs/锚点/Q437-雷达失联B批.md

## 七、机检末行（本单收口实跑）

- 漂移棘轮 `python3 data_scripts/check-drift.py --ratchet`：`RATCHET: PASS 当前值 427 小于等于基线 496，基线未动`
- 漂移雷达全量末行：`RADAR: 锚点 14281｜OK 7530｜DRIFT 3838｜VOID 2213｜MISSING 395｜DEADFILE 32｜SKIP-EXTERNAL 47｜AMBIGUOUS 226（失联 427）`
- 锚点行号闸 `python3 data_scripts/check-line-anchors.py`：`LINE-ANCHORS: FAIL（新增 DEADFILE/OUTOFRANGE 8 条 / 基线存量 3795 条 / 白名单缺理由 0 条）`。本批两份新文档（本报告 + Q437 锚点卡）复算新增 0；8 条新增全部属并发在飞的陀螺仪实现单（`docs/实现-陀螺仪接管沙盘.md`、`docs/实现-书架陀螺仪视差.md`）与 W438 判据单（`docs/验收-复核-W436雷达判据.md`），不在 B 数组，本单未碰。
- 守则回执闸 `python3 data_scripts/check-onboarding.py`：`ONBOARD: FAIL（3 份报告）`。本批重判报告文件名不属写码 / 验收 / 实现 / 组装模式，未被回执闸纳入；失败项为 2 份陀螺仪实现单缺回执块及 1 份他批报告，均非本批。

本批口径复测：B 批开始 318 条 → 结束 209 条（MISSING 209、DEADFILE 0，77 份报告）；本批两份新文档自身锚点 3 条（OK 2、VOID 1），无 MISSING / DEADFILE。

## 八、边界声明

- 未做 git 写，未编译，未改 `app/**`，未改 `data_scripts/**`，未碰 A 数组文件。
- 未上调漂移棘轮基线；未用白名单豁免本轮新锚点。
- 仓内未留临时清单文件（清单在 /tmp/q436/，按 DISPATCH-A 要求移出仓库）。
