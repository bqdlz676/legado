# 重判 · 雷达 139 条 MISSING 逐条定裁账（Q-470 移交 N3 悬项）

> 漂移记录件：本文件仅登记已失效锚点

> 任务：把雷达门禁里 Q-470 移交的 139 条 MISSING 逐条定裁销账，给出可回源守恒账；不改基线、不改门禁脚本、不碰 `app/src/**`、不改台账与队列。
> 判据口径同 `docs/重判-雷达待裁191.md` 与 `docs/重判-雷达待裁B批209.md`：终态五类——回源有效 / 重贴锚 / 判已废 / 结构性误判 / 真债登记，未自创第六类。

## 守则回执
- 指纹：docs/机制-总控必读.md=2d7dbc8f14dc；docs/落文-规矩总纲.md=d4468a629b7c
- 逐字引用 1：「锚点强制可回源：每条结论必须能回到原文行号/切片/文件。」（docs/落文-规矩总纲.md:18）
- 逐字引用 2：「**反向自检**：每个结论都要先假设"我判断错了"，找出最强反例与边界（空数据、断网、同名歧义、并发冲突、代理杀进程），再决定；反例找不到不等于没有，要写出"没找到的原因"。」（docs/机制-总控必读.md:216）
- 逐字引用 3：「**全局口径**：局部最优但拉高全局成本（例如为了单点绿而放宽门禁）一律否决；宁可慢一步，不许留一条能被利用的缝。」（docs/机制-总控必读.md:217）
- 适用条款：开工两读已完成；本单只读 docs 历史报告与静态回源（grep / 读行），禁 git 写、禁编译；未改 `data_scripts/check-drift.py`、未动 `data_scripts/drift-baseline.json`、未改 `app/src/**`、未改 `docs/派单台账.md` 与 `docs/派单队列.md`；拿不出现行原文证据的条目不判已废，登记真债（本批为 0，见守恒账）。

闸件 `python3 data_scripts/check-onboarding.py --emit` 原始输出：

```
守则回执：
- 指纹：docs/机制-总控必读.md=2d7dbc8f14dc；docs/机制-子代理必读.md=9f36d8ec0df4；docs/机制-验收代理必读.md=c81ca84a29ba；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「…」（文件:行）
- 逐字引用 2：「…」（文件:行）
- 逐字引用 3：「…」（文件:行）
- 适用条款：…（逐条写本单照做的条款）
```

## 一、复现：雷达当前原始输出与 139 条筛取命令

### 1.1 雷达门禁原始输出（开工复跑，未做任何改动）

命令原文：`python3 data_scripts/check-drift.py`

输出原文末行：`RADAR: 锚点 16968｜OK 9414｜DRIFT 4458｜VOID 2265｜MISSING 545｜DEADFILE 4｜SKIP-EXTERNAL 56｜AMBIGUOUS 226（失联 549）`

命令原文：`python3 data_scripts/check-drift.py --ratchet`

输出原文：RADAR: FAIL 失联 549 超基线 496（结论失联只增不减，先重判再放行）

- 基线文件 `data_scripts/drift-baseline.json` 内容：`{"bad": 496}`；sha256 前 16 位 `bc539394c3074341`，本单未上调、未改 sha。
- 口径：失联 = MISSING + DEADFILE；本批范围只取 MISSING。

### 1.2 139 条筛取命令与输出

筛取口径：Q-471 收口记录载明「雷达 534 超基线归因 Q470 的 139 条 MISSING（非本单引入，已注册 N3 移交）」（`docs/派单队列.md:470`；534 为 Q471 时点快照，本单开工复跑为 549，139 条归属子集不变）。据此复跑雷达 `scan()`，按「来源报告基名 = 重判-雷达待裁B批209.md 且状态 = MISSING」过滤。

命令原文（只读探针，未写仓内文件）：

```python
import importlib.util, os
spec = importlib.util.spec_from_file_location("cd", "data_scripts/check-drift.py")
cd = importlib.util.module_from_spec(spec); spec.loader.exec_module(cd)
rows = cd.scan()
b139 = [r for r in rows if os.path.basename(r[0]) == "重判-雷达待裁B批209.md" and r[3] == "MISSING"]
print(len(b139))  # 139
```

输出原文：`139`（MISSING 139 条；同来源报告内 DEADFILE 0 条）。全仓同期 MISSING 545 / DEADFILE 4，失联 549，基线 496，超基线 53；本批 139 条是其中归属于 Q-470 重判件自身的子集，其余 MISSING 属其他在飞/存量报告，不在本单。

## 二、守恒自证

- 待裁总数：139 条，一条不漏；处置数之和 = 139。
- 终态计数：回源有效 108、重贴锚 18、判已废 0、结构性误判 13、真债登记 0。
- 守恒式：108 + 18 + 0 + 13 + 0 = 139。
- 分组计数：行号漂移但符号仍在（回源有效 + 重贴锚）126；文档结构性误判 13；文件已删且符号随文件消失 0；真债无人认领 0。
- 真债登记 0 条：本批无拿不出现行证据的条目；未把「查不到」记成已废，也无「未定裁」挂账条目。

## 三、逐条终态表（139 条）

> 现行全路径证据锚点集中见附录 A；本表散文只记来源、旧锚定性与终态，旧锚一律写「文件名 第 N 行」散文，不留裸 `:NN`。

- 1. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第22行，旧锚 B3SettingModels.kt 第 1 行。
  - 现行证据（附录 A 条目 1）：目标文件在位，第 1 行内容「package io.legado.app.ui.config.b3」与来源结论一致，回源成立。
- 2. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第23行，旧锚 B3PrefBridge.kt 第 1 行。
  - 现行证据（附录 A 条目 2）：目标文件在位，第 1 行内容「package io.legado.app.ui.config.b3」与来源结论一致，回源成立。
- 3. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第25行，旧锚 B3SheetTargets.kt 第 1 行。
  - 现行证据（附录 A 条目 3）：目标文件在位，第 1 行内容「package io.legado.app.ui.config.b3」与来源结论一致，回源成立。
- 4. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第26行，旧锚 B3SettingGroups.kt 第 1 行。
  - 现行证据（附录 A 条目 4）：目标文件在位，第 1 行内容「package io.legado.app.ui.config.b3」与来源结论一致，回源成立。
- 5. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第42行，旧锚 B3SettingGroups.kt 第 70 行。
  - 现行落点：附录 A 条目 5；旧行号漂移，符号现行命中，grep 回证在位（70→81）。
- 6. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第42行，旧锚 B3SettingGroups.kt 第 108 行。
  - 现行落点：附录 A 条目 6；旧行号漂移，符号现行命中，grep 回证在位（108→121）。
- 7. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第42行，旧锚 B3SettingGroups.kt 第 160 行。
  - 现行落点：附录 A 条目 7；旧行号漂移，符号现行命中，grep 回证在位（160→173）。
- 8. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第42行，旧锚 B3SettingGroups.kt 第 249 行。
  - 现行落点：附录 A 条目 8；旧行号漂移，符号现行命中，grep 回证在位（249→264）。
- 9. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第68行，旧锚 B3SettingGroups.kt 第 110 行。
  - 现行证据（附录 A 条目 9）：目标文件在位，第 110 行内容「current = fontScale,」与来源结论一致，回源成立。
- 10. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第68行，旧锚 B3SettingGroups.kt 第 125 行。
  - 现行证据（附录 A 条目 10）：目标文件在位，第 125 行内容「title = "阅读字体",」与来源结论一致，回源成立。
- 11. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第68行，旧锚 B3SettingGroups.kt 第 126 行。
  - 现行证据（附录 A 条目 11）：目标文件在位，第 126 行内容「summary = fontChoice,」与来源结论一致，回源成立。
- 12. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第68行，旧锚 B3SettingGroups.kt 第 130 行。
  - 现行证据（附录 A 条目 12）：目标文件在位，第 130 行内容「B3SheetTarget.ListPick(」与来源结论一致，回源成立。
- 13. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第75行，旧锚 B3SettingGroups.kt 第 236 行。
  - 现行证据（附录 A 条目 13）：目标文件在位，第 236 行内容「),」与来源结论一致，回源成立。
- 14. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第75行，旧锚 B3SettingGroups.kt 第 240 行。
  - 现行证据（附录 A 条目 14）：目标文件在位，第 240 行内容「keywords = listOf("webdav", "test", "连接"),」与来源结论一致，回源成立。
- 15. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第75行，旧锚 B3SettingGroups.kt 第 242 行。
  - 现行证据（附录 A 条目 15）：目标文件在位，第 242 行内容「),」与来源结论一致，回源成立。
- 16. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第75行，旧锚 B3SettingGroups.kt 第 245 行。
  - 现行证据（附录 A 条目 16）：目标文件在位，第 245 行内容「summary = "每月 1GB 流量，够用了",」与来源结论一致，回源成立。
- 17. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第78行，旧锚 B3SettingGroups.kt 第 136 行。
  - 现行证据（附录 A 条目 17）：目标文件在位，第 136 行内容「),」与来源结论一致，回源成立。
- 18. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第78行，旧锚 B3SettingGroups.kt 第 142 行。
  - 现行证据（附录 A 条目 18）：目标文件在位，第 142 行内容「summary = "当前：$fontChoice —— 隐公元年，郑伯克段于鄢。注音小字维持无衬线不变。",」与来源结论一致，回源成立。
- 19. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第78行，旧锚 B3SettingGroups.kt 第 143 行。
  - 现行证据（附录 A 条目 19）：目标文件在位，第 143 行内容「keywords = listOf("preview", "预览"),」与来源结论一致，回源成立。
- 20. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第78行，旧锚 B3SettingGroups.kt 第 149 行。
  - 现行证据（附录 A 条目 20）：目标文件在位，第 149 行内容「),」与来源结论一致，回源成立。
- 21. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第78行，旧锚 B3SettingGroups.kt 第 150 行。
  - 现行证据（附录 A 条目 21）：目标文件在位，第 150 行内容「B3SwitchRow(」与来源结论一致，回源成立。
- 22. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第78行，旧锚 B3SettingGroups.kt 第 156 行。
  - 现行证据（附录 A 条目 22）：目标文件在位，第 156 行内容「),」与来源结论一致，回源成立。
- 23. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第94行，旧锚 B3SettingModels.kt 第 74 行。
  - 现行证据（附录 A 条目 23）：目标文件在位，第 74 行内容「*/」与来源结论一致，回源成立。
- 24. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第94行，旧锚 B3SettingModels.kt 第 82 行。
  - 现行证据（附录 A 条目 24）：目标文件在位，第 82 行内容「}」与来源结论一致，回源成立。
- 25. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第94行，旧锚 B3SettingModels.kt 第 84 行。
  - 现行证据（附录 A 条目 25）：目标文件在位，第 84 行内容「/** 裁剪后是否仍有可见行；供无命中兜底空态判定，不留白页（R19）。 */」与来源结论一致，回源成立。
- 26. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第94行，旧锚 B3SettingModels.kt 第 85 行。
  - 现行证据（附录 A 条目 26）：目标文件在位，第 85 行内容「fun b3GroupsHaveMatch(groups: List<B3SettingGroup>, query: String): Boolean =」与来源结论一致，回源成立。
- 27. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第101行，旧锚 B3SettingModels.kt 第 91 行。
  - 现行证据（附录 A 条目 27）：目标文件在位，第 91 行内容「*/」与来源结论一致，回源成立。
- 28. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第101行，旧锚 B3SettingModels.kt 第 95 行。
  - 现行证据（附录 A 条目 28）：目标文件在位，第 95 行内容「else -> value」与来源结论一致，回源成立。
- 29. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第114行，旧锚 B3SettingModels.kt 第 75 行。
  - 现行证据（附录 A 条目 29）：目标文件在位，第 75 行内容「fun filterB3Groups(groups: List<B3SettingGroup>, query: String): List<B3SettingGroup> {」与来源结论一致，回源成立。
- 30. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/实现-B3-设置页.md` 第114行，旧锚 B3SettingModels.kt 第 76 行。
  - 现行证据（附录 A 条目 30）：目标文件在位，第 76 行内容「val normalized = query.trim()」与来源结论一致，回源成立。
- 37. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/方案-可判定体验基线.md` 第75行，旧锚 TopicMarxCard.kt 第 40 行。
  - 现行证据（附录 A 条目 37）：目标文件在位，第 40 行内容「private val MarxBorder = Color(0xFFD4A54A)」与来源结论一致，回源成立。
- 49. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/机制-验收证据工作台.md` 第130行，旧锚 InkContentEditFooter.kt 第 20 行。
  - 现行证据（附录 A 条目 49）：目标文件在位，第 20 行内容「import androidx.compose.ui.unit.dp」与来源结论一致，回源成立。
- 50. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/机制体检-2026-11-25.md` 第38行，旧锚 GlobeSphere.kt 第 86 行。
  - 现行证据（附录 A 条目 50）：目标文件在位，第 86 行内容「// Float 与 Double 混算会推到 Double，必须先统一量纲再钳制，否则返回类型不匹配（CI 编译红 K-16）。」与来源结论一致，回源成立。
- 51. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/落文-跨模块问题交接清单.md` 第13行，旧锚 PlaceModels.kt 第 78 行。
  - 现行证据（附录 A 条目 51）：目标文件在位，第 78 行内容「*  不叫 `label`，避免与父类 [PlaceSource.label]（格式化展示串）遮蔽冲突。 */」与来源结论一致，回源成立。
- 53. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/规划-缝合怪清理批次.md` 第62行，旧锚 Enums.kt 第 117 行。
  - 现行证据（附录 A 条目 53）：目标文件在位，第 117 行内容「* 分类口径与 help/sim/EventTrigger 对齐：事件型改士气 / 兵力 / 存活，意图型改写 [Intent]，」与来源结论一致，回源成立。
- 69. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/返工-B3设置页.md` 第75行，旧锚 B3SettingGroups.kt 第 145 行。
  - 现行证据（附录 A 条目 69）：目标文件在位，第 145 行内容「B3InfoRow(」与来源结论一致，回源成立。
- 70. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/返工-B3设置页.md` 第105行，旧锚 B3SettingGroups.kt 第 251 行。
  - 现行证据（附录 A 条目 70）：目标文件在位，第 251 行内容「summary = b3BackupSummary(b3BackupContents),」与来源结论一致，回源成立。
- 72. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-53失败修复-包2数据纪年气泡.md` 第103行，旧锚 PlaceModels.kt 第 14 行。
  - 现行证据（附录 A 条目 72）：目标文件在位，第 14 行内容「* 时间编码照抄 CHGIS 约定：公元年用有符号整数，负数表示公元前（如 -722 = 鲁隐公元年），」与来源结论一致，回源成立。
- 73. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-B10-分支对比收口.md` 第15行，旧锚 落文-原文-06-四渡赤水引擎.md 第 114 行。
  - 目标文档行在位（附录 A 条目 73）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 76. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-B12B13-枚举与解析收口.md` 第18行，旧锚 Enums.kt 第 115 行。
  - 现行证据（附录 A 条目 76）：目标文件在位，第 115 行内容「* 脚本事件动作（条件触发器声明，不硬编码回合数）。」与来源结论一致，回源成立。
- 77. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-B12B13-枚举与解析收口.md` 第120行，旧锚 CrossingShell.kt 第 134 行。
  - 现行落点：附录 A 条目 77；旧行号漂移，符号现行命中，grep 回证在位（134→150）。
- 78. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-B12B13-枚举与解析收口.md` 第135行，旧锚 CrossingShell.kt 第 134 行。
  - 现行落点：附录 A 条目 78；旧行号漂移，符号现行命中，grep 回证在位（134→150）。
- 82. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-B18-六态收敛.md` 第126行，旧锚 SettingControls.kt 第 385 行。
  - 现行证据（附录 A 条目 82）：目标文件在位，第 385 行内容「!enabled -> ComponentTokens.textTertiary.copy(alpha = 0.35f)」与来源结论一致，回源成立。
- 83. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-B2-天候路况.md` 第39行，旧锚 Enums.kt 第 117 行。
  - 现行证据（附录 A 条目 83）：目标文件在位，第 117 行内容「* 分类口径与 help/sim/EventTrigger 对齐：事件型改士气 / 兵力 / 存活，意图型改写 [Intent]，」与来源结论一致，回源成立。
- 84. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-B28-判低复验.md` 第59行，旧锚 FileDocExtensions.kt 第 66 行。
  - 现行证据（附录 A 条目 84）：目标文件在位，第 66 行内容「Class.forName("androidx.documentfile.provider.TreeDocumentFile")」与来源结论一致，回源成立。
- 87. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-B29-常量visible-source.md` 第27行，旧锚 SourceManageSheets.kt 第 85 行。
  - 现行证据（附录 A 条目 87）：目标文件在位，第 85 行内容「vm: SourceManageViewModel,」与来源结论一致，回源成立。
- 88. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-B29-常量visible-source.md` 第401行，旧锚 SourceManageSheets.kt 第 257 行。
  - 现行证据（附录 A 条目 88）：目标文件在位，第 257 行内容「.clickable { vm.deleteGroup(group) },」与来源结论一致，回源成立。
- 89. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-B29-常量visible-source.md` 第422行，旧锚 HalfSheetModel.kt 第 50 行。
  - 现行证据（附录 A 条目 89）：目标文件在位，第 50 行内容「const val RISE_DURATION_MS: Int = 250」与来源结论一致，回源成立。
- 90. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-B4-河流点列收口.md` 第52行，旧锚 CrossingShell.kt 第 134 行。
  - 现行落点：附录 A 条目 90；旧行号漂移，符号现行命中，grep 回证在位（134→150）。
- 91. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-B4-河流点列收口.md` 第55行，旧锚 CrossingShell.kt 第 134 行。
  - 现行落点：附录 A 条目 91；旧行号漂移，符号现行命中，grep 回证在位（134→150）。
- 92. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-B4-河流点列收口.md` 第156行，旧锚 CrossingShell.kt 第 134 行。
  - 现行落点：附录 A 条目 92；旧行号漂移，符号现行命中，grep 回证在位（134→150）。
- 97. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-B8R1-视野半径单源.md` 第178行，旧锚 机制-验收单模板.md 第 11 行。
  - 目标文档行在位（附录 A 条目 97）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 98. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-Q196Q197-补验.md` 第47行，旧锚 CrossingShell.kt 第 1 行。
  - 现行证据（附录 A 条目 98）：目标文件在位，第 1 行内容「package io.legado.app.ui.map.crossing.shell」与来源结论一致，回源成立。
- 99. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-Q196Q197-补验.md` 第75行，旧锚 CrossingShell.kt 第 252 行。
  - 现行证据（附录 A 条目 99）：目标文件在位，第 252 行内容「autoPlay = false」与来源结论一致，回源成立。
- 100. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-Q196Q197-补验.md` 第180行，旧锚 CrossingShell.kt 第 274 行。
  - 现行证据（附录 A 条目 100）：目标文件在位，第 274 行内容「BranchCompareShell(」与来源结论一致，回源成立。
- 101. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-Q196Q197-补验.md` 第180行，旧锚 CrossingShell.kt 第 321 行。
  - 现行证据（附录 A 条目 101）：目标文件在位，第 321 行内容「fun BranchCompareShell(」与来源结论一致，回源成立。
- 102. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-Q218-不等待补查独立验收.md` 第68行，旧锚 机制-总控必读.md 第 207 行。
  - 目标文档行在位（附录 A 条目 102）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 103. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-Q218-不等待补查独立验收.md` 第68行，旧锚 诊断-机制空转.md 第 62 行。
  - 目标文档行在位（附录 A 条目 103）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 104. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-Q222-55与56门禁独立验收.md` 第92行，旧锚 事故-停工事故复盘-2026-11-25.md 第 31 行。
  - 目标文档行在位（附录 A 条目 104）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 105. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-Q222-55与56门禁独立验收.md` 第93行，旧锚 事故清单.md 第 59 行。
  - 目标文档行在位（附录 A 条目 105）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 106. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-Q222-55与56门禁独立验收.md` 第139行，旧锚 事故-停工事故复盘-2026-11-25.md 第 31 行。
  - 目标文档行在位（附录 A 条目 106）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 107. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-Q222-55与56门禁独立验收.md` 第146行，旧锚 事故清单.md 第 59 行。
  - 目标文档行在位（附录 A 条目 107）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 116. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-U6-半屏Sheet收口.md` 第156行，旧锚 InkFrost.kt 第 23 行。
  - 现行证据（附录 A 条目 116）：目标文件在位，第 23 行内容「* 不变式（归位口径，B4-05）：」与来源结论一致，回源成立。
- 117. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-书源域-重写.md` 第132行，旧锚 SourceManageSheets.kt 第 87 行。
  - 现行证据（附录 A 条目 117）：目标文件在位，第 87 行内容「) {」与来源结论一致，回源成立。
- 118. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-书源域-重写.md` 第139行，旧锚 SourceManageSheets.kt 第 386 行。
  - 现行证据（附录 A 条目 118）：目标文件在位，第 386 行内容「}」与来源结论一致，回源成立。
- 119. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-侧翼半径两项.md` 第55行，旧锚 CampaignModels.kt 第 198 行。
  - 现行证据（附录 A 条目 119）：目标文件在位，第 198 行内容「* 口径：骑兵 6 / 精锐 5 / 常规 4 / 民兵 3。情报侧 [Corps.visionRadiusCells] 赋值、」与来源结论一致，回源成立。
- 121. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-名物库判低两项.md` 第114行，旧锚 AudioSkipPolicy.kt 第 3 行。
  - 现行证据（附录 A 条目 121）：目标文件在位，第 3 行内容「internal const val MIN_AUDIO_SKIP_REMAINING_MS = 5_000L」与来源结论一致，回源成立。
- 122. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-名物库判低两项.md` 第114行，旧锚 AnalyzeUrlNetworkOptions.kt 第 9 行。
  - 现行证据（附录 A 条目 122）：目标文件在位，第 9 行内容「private const val MIN_DERIVED_CALL_TIMEOUT_MILLIS = 60_000L」与来源结论一致，回源成立。
- 123. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-名物槽装配.md` 第136行，旧锚 HalfSheetModel.kt 第 262 行。
  - 现行证据（附录 A 条目 123）：目标文件在位，第 262 行内容「* 铁律：本映射**只消费** [BubbleContent]（help.bubble 冻结协议），不另建第二套气泡数据模型；」与来源结论一致，回源成立。
- 124. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-回放事件分发.md` 第26行，旧锚 BattleModels.kt 第 61 行。
  - 现行证据（附录 A 条目 124）：目标文件在位，第 61 行内容「*」与来源结论一致，回源成立。
- 125. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-回放事件分发.md` 第77行，旧锚 Enums.kt 第 121 行。
  - 现行证据（附录 A 条目 125）：目标文件在位，第 121 行内容「enum class EventAction(val jsonLabel: String) {」与来源结论一致，回源成立。
- 126. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-回放分发返工.md` 第93行，旧锚 BattleModels.kt 第 56 行。
  - 现行落点：附录 A 条目 126；旧行号漂移，符号现行命中，grep 回证在位（56→59）。
- 127. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B2.md` 第79行，旧锚 Enums.kt 第 117 行。
  - 现行证据（附录 A 条目 127）：目标文件在位，第 117 行内容「* 分类口径与 help/sim/EventTrigger 对齐：事件型改士气 / 兵力 / 存活，意图型改写 [Intent]，」与来源结论一致，回源成立。
- 128. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B2主壳.md` 第222行，旧锚 InkFrost.kt 第 32 行。
  - 现行证据（附录 A 条目 128）：目标文件在位，第 32 行内容「shape: Shape,」与来源结论一致，回源成立。
- 129. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第114行，旧锚 B3SettingGroups.kt 第 115 行。
  - 现行证据（附录 A 条目 129）：目标文件在位，第 115 行内容「),」与来源结论一致，回源成立。
- 130. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第142行，旧锚 SettingControls.kt 第 54 行。
  - 现行证据（附录 A 条目 130）：目标文件在位，第 54 行内容「* 不挂 ripple / elevation；按压反馈是朱砂 8%→16% 浸染，120ms 回落（切片-04 InkButton 同口径）。」与来源结论一致，回源成立。
- 131. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第161行，旧锚 B3SettingModels.kt 第 74 行。
  - 现行落点：附录 A 条目 131；旧行号漂移，符号现行命中，grep 回证在位（74→75）。
- 132. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第174行，旧锚 SettingSheets.kt 第 38 行。
  - 现行证据（附录 A 条目 132）：目标文件在位，第 38 行内容「/** 数字步进盘：[min]–[max]，[unit] 为单位文案；[onDefault] 非空时提供「恢复默认」。 */」与来源结论一致，回源成立。
- 133. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第176行，旧锚 B3SettingModels.kt 第 91 行。
  - 现行落点：附录 A 条目 133；旧行号漂移，符号现行命中，grep 回证在位（91→92）。
- 134. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第176行，旧锚 B3SettingGroups.kt 第 95 行。
  - 现行证据（附录 A 条目 134）：目标文件在位，第 95 行内容「onPick = { value -> context.putPrefString(PreferKey.themeMode, value) },」与来源结论一致，回源成立。
- 135. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第177行，旧锚 B3SettingGroups.kt 第 57 行。
  - 现行证据（附录 A 条目 135）：目标文件在位，第 57 行内容「// R11：字体档位落盘既有正文字体域字符串键（取值依据见返工报告 §R11）；初始值 / 外部改键均由桥重读。」与来源结论一致，回源成立。
- 136. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第184行，旧锚 B3SettingModels.kt 第 75 行。
  - 现行证据（附录 A 条目 136）：目标文件在位，第 75 行内容「fun filterB3Groups(groups: List<B3SettingGroup>, query: String): List<B3SettingGroup> {」与来源结论一致，回源成立。
- 137. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第226行，旧锚 B3SettingGroups.kt 第 135 行。
  - 现行证据（附录 A 条目 137）：目标文件在位，第 135 行内容「onPick = { value -> context.putPrefString(PreferKey.fontFolder, value) },」与来源结论一致，回源成立。
- 138. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第235行，旧锚 ContextExtensions.kt 第 186 行。
  - 现行证据（附录 A 条目 138）：目标文件在位，第 186 行内容「fun Context.getPrefInt(key: String, defValue: Int = 0) =」与来源结论一致，回源成立。
- 139. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第285行，旧锚 B3PrefBridge.kt 第 1 行。
  - 现行证据（附录 A 条目 139）：目标文件在位，第 1 行内容「package io.legado.app.ui.config.b3」与来源结论一致，回源成立。
- 140. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第291行，旧锚 B3SettingGroups.kt 第 1 行。
  - 现行证据（附录 A 条目 140）：目标文件在位，第 1 行内容「package io.legado.app.ui.config.b3」与来源结论一致，回源成立。
- 141. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第294行，旧锚 B3SettingModels.kt 第 1 行。
  - 现行证据（附录 A 条目 141）：目标文件在位，第 1 行内容「package io.legado.app.ui.config.b3」与来源结论一致，回源成立。
- 142. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第295行，旧锚 B3SettingModels.kt 第 3 行。
  - 现行落点：附录 A 条目 142；旧行号漂移，符号现行命中，grep 回证在位（3→4）。
- 143. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第300行，旧锚 B3SheetTargets.kt 第 1 行。
  - 现行证据（附录 A 条目 143）：目标文件在位，第 1 行内容「package io.legado.app.ui.config.b3」与来源结论一致，回源成立。
- 144. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3设置页.md` 第351行，旧锚 SettingControls.kt 第 54 行。
  - 现行证据（附录 A 条目 144）：目标文件在位，第 54 行内容「* 不挂 ripple / elevation；按压反馈是朱砂 8%→16% 浸染，120ms 回落（切片-04 InkButton 同口径）。」与来源结论一致，回源成立。
- 145. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3返工.md` 第114行，旧锚 B3SettingGroups.kt 第 251 行。
  - 现行证据（附录 A 条目 145）：目标文件在位，第 251 行内容「summary = b3BackupSummary(b3BackupContents),」与来源结论一致，回源成立。
- 146. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3返工.md` 第151行，旧锚 B3SettingGroups.kt 第 54 行。
  - 现行证据（附录 A 条目 146）：目标文件在位，第 54 行内容「val fontScale = clampB3Int(fontScaleRaw.takeIf { it > 0 } ?: 10, 8, 16)」与来源结论一致，回源成立。
- 147. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3返工.md` 第257行，旧锚 B3PrefBridge.kt 第 86 行。
  - 现行证据（附录 A 条目 147）：目标文件在位，第 86 行内容「private fun SharedPreferences.getStringCompatSafe(key: String, def: String?): String? =」与来源结论一致，回源成立。
- 148. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B3返工.md` 第257行，旧锚 ContextExtensions.kt 第 186 行。
  - 现行证据（附录 A 条目 148）：目标文件在位，第 186 行内容「fun Context.getPrefInt(key: String, defValue: Int = 0) =」与来源结论一致，回源成立。
- 151. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B4-05壳基础簇.md` 第52行，旧锚 InkModels.kt 第 72 行。
  - 现行证据（附录 A 条目 151）：目标文件在位，第 72 行内容「/** 底栏 60dp */」与来源结论一致，回源成立。
- 152. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B4-05壳基础簇.md` 第175行，旧锚 VerticalModels.kt 第 50 行。
  - 现行证据（附录 A 条目 152）：目标文件在位，第 50 行内容「data class CharHit(」与来源结论一致，回源成立。
- 153. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B4-05壳基础簇.md` 第224行，旧锚 VerticalModels.kt 第 50 行。
  - 现行证据（附录 A 条目 153）：目标文件在位，第 50 行内容「data class CharHit(」与来源结论一致，回源成立。
- 156. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B5任务书.md` 第96行，旧锚 B3SettingGroups.kt 第 251 行。
  - 现行证据（附录 A 条目 156）：目标文件在位，第 251 行内容「summary = b3BackupSummary(b3BackupContents),」与来源结论一致，回源成立。
- 157. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B5任务书.md` 第118行，旧锚 SettingControls.kt 第 54 行。
  - 现行证据（附录 A 条目 157）：目标文件在位，第 54 行内容「* 不挂 ripple / elevation；按压反馈是朱砂 8%→16% 浸染，120ms 回落（切片-04 InkButton 同口径）。」与来源结论一致，回源成立。
- 158. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B5任务书.md` 第142行，旧锚 B3PrefBridge.kt 第 28 行。
  - 现行证据（附录 A 条目 158）：目标文件在位，第 28 行内容「internal fun rememberB3Boolean(key: String, def: Boolean): Boolean {」与来源结论一致，回源成立。
- 159. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B5任务书.md` 第142行，旧锚 B3SettingModels.kt 第 137 行。
  - 现行证据（附录 A 条目 159）：目标文件在位，第 137 行内容「val b3SoundMirrorRow: B3SoundMirrorRow = B3SoundMirrorRow(」与来源结论一致，回源成立。
- 160. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B5任务书.md` 第143行，旧锚 SettingSheets.kt 第 38 行。
  - 现行证据（附录 A 条目 160）：目标文件在位，第 38 行内容「/** 数字步进盘：[min]–[max]，[unit] 为单位文案；[onDefault] 非空时提供「恢复默认」。 */」与来源结论一致，回源成立。
- 161. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B5拆分图.md` 第254行，旧锚 B3PrefBridge.kt 第 28 行。
  - 现行证据（附录 A 条目 161）：目标文件在位，第 28 行内容「internal fun rememberB3Boolean(key: String, def: Boolean): Boolean {」与来源结论一致，回源成立。
- 162. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B5拆分图.md` 第257行，旧锚 B3SettingModels.kt 第 137 行。
  - 现行证据（附录 A 条目 162）：目标文件在位，第 137 行内容「val b3SoundMirrorRow: B3SoundMirrorRow = B3SoundMirrorRow(」与来源结论一致，回源成立。
- 163. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B5拆分图.md` 第259行，旧锚 ContextExtensions.kt 第 186 行。
  - 现行证据（附录 A 条目 163）：目标文件在位，第 186 行内容「fun Context.getPrefInt(key: String, defValue: Int = 0) =」与来源结论一致，回源成立。
- 164. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B6-02模型映射层.md` 第134行，旧锚 TopicMarxCard.kt 第 37 行。
  - 现行证据（附录 A 条目 164）：目标文件在位，第 37 行内容「enum class HeroKind { Marx, Cpc }」与来源结论一致，回源成立。
- 165. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B6-03发现页VM编排.md` 第253行，旧锚 InkExploreModels.kt 第 6 行。
  - 现行证据（附录 A 条目 165）：目标文件在位，第 6 行内容「* 主控 merge 时把数据层（campaigns.js / allusion.txt / 书库）映射成这三个列表即可，」与来源结论一致，回源成立。
- 166. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-B6-04令牌动效.md` 第37行，旧锚 DiscoverMotion.kt 第 25 行。
  - 现行证据（附录 A 条目 166）：目标文件在位，第 25 行内容「* - docs/落文-规格切片/切片-15-全局动效铁律.md：「卡片浮入 stagger 350ms each」、」与来源结论一致，回源成立。
- 168. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-复核-U12.md` 第171行，旧锚 CrossingShell.kt 第 83 行。
  - 现行落点：附录 A 条目 168；旧行号漂移，符号现行命中，grep 回证在位（83→135）。
- 169. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-U12.md` 第243行，旧锚 CampaignModels.kt 第 209 行。
  - 现行证据（附录 A 条目 169）：目标文件在位，第 209 行内容「ArmyType.REGULAR -> 4」与来源结论一致，回源成立。
- 170. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-U12.md` 第252行，旧锚 Enums.kt 第 47 行。
  - 现行证据（附录 A 条目 170）：目标文件在位，第 47 行内容「enum class ArmyType {」与来源结论一致，回源成立。
- 171. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-复核-U12.md` 第258行，旧锚 CrossingShell.kt 第 369 行。
  - 现行落点：附录 A 条目 171；旧行号漂移，符号现行命中，grep 回证在位（369→468）。
- 172. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-g61组合陷阱.md` 第151行，旧锚 B3SettingGroups.kt 第 68 行。
  - 现行证据（附录 A 条目 172）：目标文件在位，第 68 行内容「val fontScaleTitle = stringResource(R.string.font_scale)」与来源结论一致，回源成立。
- 175. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-复核-主AI空等门禁-复验.md` 第93行，旧锚 Q306-主AI空等门禁.md 第 11 行。
  - 目标文档行在位（附录 A 条目 175）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 177. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-复核-主AI空等门禁-复验.md` 第183行，旧锚 Q306-主AI空等门禁.md 第 4 行。
  - 目标文档行在位（附录 A 条目 177）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 179. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-供源方案-B6.md` 第53行，旧锚 InkExploreCards.kt 第 126 行。
  - 现行证据（附录 A 条目 179）：目标文件在位，第 126 行内容「// 变体一：战役精选横卡（横滑吸附 + 暖金边 + 进度条）」与来源结论一致，回源成立。
- 180. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-供源方案-B6.md` 第55行，旧锚 InkExploreModels.kt 第 66 行。
  - 现行证据（附录 A 条目 180）：目标文件在位，第 66 行内容「val classics: List<ClassicBook> = emptyList(),」与来源结论一致，回源成立。
- 181. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-供源方案-B6.md` 第55行，旧锚 InkExploreGrid.kt 第 121 行。
  - 现行证据（附录 A 条目 181）：目标文件在位，第 121 行内容「classics: List<ClassicBook>,」与来源结论一致，回源成立。
- 182. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-供源方案-B6.md` 第164行，旧锚 TopicMarxCard.kt 第 37 行。
  - 现行证据（附录 A 条目 182）：目标文件在位，第 37 行内容「enum class HeroKind { Marx, Cpc }」与来源结论一致，回源成立。
- 184. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-快照资源依赖红.md` 第160行，旧锚 B3PrefBridge.kt 第 29 行。
  - 现行证据（附录 A 条目 184）：目标文件在位，第 29 行内容「val context = LocalContext.current」与来源结论一致，回源成立。
- 185. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-组件七态.md` 第82行，旧锚 ComponentSystemContract.kt 第 59 行。
  - 现行证据（附录 A 条目 185）：目标文件在位，第 59 行内容「* 〔B1-件 双源收口口径〕本枚举是全组件库唯一状态模型。交互快照」与来源结论一致，回源成立。
- 187. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-复核-组件收口.md` 第96行，旧锚 ComponentState.kt 第 30 行。
  - 现行证据（附录 A 条目 187）：目标文件在位，第 30 行内容「* [ComponentVisualState.contentAlpha] / [ComponentVisualState.eventsAllowed]，本类不另写系数。」与来源结论一致，回源成立。
- 188. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-复核-组件收口.md` 第103行，旧锚 ComponentSystemContract.kt 第 15 行。
  - 现行落点：附录 A 条目 188；旧行号漂移，符号现行命中，grep 回证在位（15→21）。
- 192. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-敌情数据层掩码.md` 第36行，旧锚 CrossingShell.kt 第 693 行。
  - 现行落点：附录 A 条目 192；旧行号漂移，符号现行命中，grep 回证在位（693→865）。
- 193. 〔重贴锚｜行号漂移但符号仍在〕来源 `docs/验收-敌情数据层掩码.md` 第37行，旧锚 CrossingShell.kt 第 762 行。
  - 现行落点：附录 A 条目 193；旧行号漂移，符号现行命中，grep 回证在位（762→934）。
- 194. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-新门禁交叉复核.md` 第79行，旧锚 Q273-挂死二分.md 第 23 行。
  - 目标文档行在位（附录 A 条目 194）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 195. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-清剿收尾两项.md` 第453行，旧锚 BookshelfViewModel.kt 第 40 行。
  - 现行证据（附录 A 条目 195）：目标文件在位，第 40 行内容「if (groupId > 0) book.group = groupId」与来源结论一致，回源成立。
- 196. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-热门词接线.md` 第109行，旧锚 SearchModels.kt 第 119 行。
  - 现行证据（附录 A 条目 196）：目标文件在位，第 119 行内容「val hot: List<String> = emptyList(),」与来源结论一致，回源成立。
- 197. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-立项档位-Q25.md` 第147行，旧锚 立项草案-批次D.md 第 9 行。
  - 目标文档行在位（附录 A 条目 197）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 198. 〔结构性误判｜文档结构性误判〕来源 `docs/验收-立项档位-Q25.md` 第154行，旧锚 立项规则.md 第 58 行。
  - 目标文档行在位（附录 A 条目 198）；雷达候选取编号缩写/散文词当代码符号匹配，启发式误报，结论回源成立。
- 199. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-继承半成品-1124.md` 第40行，旧锚 CampaignModels.kt 第 236 行。
  - 现行证据（附录 A 条目 199）：目标文件在位，第 236 行内容「/** 情报雾（F8）：本方军、或被任一可见本方军看到的敌军 = true。 */」与来源结论一致，回源成立。
- 202. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-链序优先级仲裁.md` 第83行，旧锚 EraCandidateFilter.kt 第 30 行。
  - 现行证据（附录 A 条目 202）：目标文件在位，第 30 行内容「* 不回退全量候选——与 [BubbleContractResolver] 的 `.ifEmpty { hits }` 兜底回退不同，」与来源结论一致，回源成立。
- 203. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-链序优先级仲裁.md` 第84行，旧锚 EraCandidateFilter.kt 第 65 行。
  - 现行证据（附录 A 条目 203）：目标文件在位，第 65 行内容「* 口径与 [BubbleContractResolver] 的私有 inRange 保持一致：年份点落在 `[start, end]` 内。」与来源结论一致，回源成立。
- 204. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-链序优先级仲裁.md` 第85行，旧锚 EraCandidateFilter.kt 第 75 行。
  - 现行证据（附录 A 条目 204）：目标文件在位，第 75 行内容「* 与 [BubbleContractResolver] 的 tierOrder 同口径；此处私有复制是因为既有 resolver 的」与来源结论一致，回源成立。
- 205. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-链序优先级仲裁.md` 第86行，旧锚 EraCandidateFilter.kt 第 86 行。
  - 现行证据（附录 A 条目 205）：目标文件在位，第 86 行内容「*  - 对 S-02 resolve 出口：[BubbleContractResolver.resolve] 在纪年全不相交时回退全量 hits」与来源结论一致，回源成立。
- 206. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-降级暴露分级.md` 第40行，旧锚 CampaignModels.kt 第 137 行。
  - 现行证据（附录 A 条目 206）：目标文件在位，第 137 行内容「* @param exposed 本回合是否暴露伏击意图（回合级姿态：每回合推进时复位，由本回合视线穿越 /」与来源结论一致，回源成立。
- 207. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-降级暴露分级.md` 第206行，旧锚 CrossingShell.kt 第 726 行。
  - 现行证据（附录 A 条目 207）：目标文件在位，第 726 行内容「val lat = corps.reportLat」与来源结论一致，回源成立。
- 208. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-降级标志消费与视野豁免.md` 第106行，旧锚 CampaignModels.kt 第 137 行。
  - 现行证据（附录 A 条目 208）：目标文件在位，第 137 行内容「* @param exposed 本回合是否暴露伏击意图（回合级姿态：每回合推进时复位，由本回合视线穿越 /」与来源结论一致，回源成立。
- 209. 〔回源有效｜行号漂移但符号仍在〕来源 `docs/验收-降级标志消费与视野豁免.md` 第145行，旧锚 CampaignModels.kt 第 246 行。
  - 现行证据（附录 A 条目 209）：目标文件在位，第 246 行内容「/** 盲区占位文案：从未侦察到的敌军方位提示；null = 非盲区。缺省 null 保证旧构造点不破。 */」与来源结论一致，回源成立。

## 四、附录 A · 现行落点证据锚点（全路径:行，静态回源在位）

- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:1`（条目 1）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3PrefBridge.kt:1`（条目 2）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SheetTargets.kt:1`（条目 3）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:1`（条目 4）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:81`（条目 5）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:121`（条目 6）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:173`（条目 7）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:264`（条目 8）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:110`（条目 9）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:125`（条目 10）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:126`（条目 11）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:130`（条目 12）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:236`（条目 13）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:240`（条目 14）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:242`（条目 15）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:245`（条目 16）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:136`（条目 17）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:142`（条目 18）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:143`（条目 19）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:149`（条目 20）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:150`（条目 21）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:156`（条目 22）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:74`（条目 23）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:82`（条目 24）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:84`（条目 25）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:85`（条目 26）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:91`（条目 27）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:95`（条目 28）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:75`（条目 29）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:76`（条目 30）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/card/TopicMarxCard.kt:40`（条目 37）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditFooter.kt:20`（条目 49）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/globe/contract/GlobeSphere.kt:86`（条目 50）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/help/place/PlaceModels.kt:78`（条目 51）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt:117`（条目 53）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:145`（条目 69）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:251`（条目 70）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/help/place/PlaceModels.kt:14`（条目 72）：目标文件在位，锚点行与来源结论一致
- `docs/落文-原文/落文-原文-06-四渡赤水引擎.md:114`（条目 73）：目标文档行在位，属启发式误报
- `app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt:115`（条目 76）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:150`（条目 77）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:150`（条目 78）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/settings/SettingControls.kt:385`（条目 82）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt:117`（条目 83）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/utils/FileDocExtensions.kt:66`（条目 84）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:85`（条目 87）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:257`（条目 88）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt:50`（条目 89）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:150`（条目 90）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:150`（条目 91）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:150`（条目 92）：旧行号漂移，符号现行命中于该行
- `docs/机制-验收单模板.md:11`（条目 97）：目标文档行在位，属启发式误报
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:1`（条目 98）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:252`（条目 99）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:274`（条目 100）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:321`（条目 101）：目标文件在位，锚点行与来源结论一致
- `docs/机制-总控必读.md:207`（条目 102）：目标文档行在位，属启发式误报
- `docs/诊断-机制空转.md:62`（条目 103）：目标文档行在位，属启发式误报
- `docs/事故-停工事故复盘-2026-11-25.md:31`（条目 104）：目标文档行在位，属启发式误报
- `docs/事故清单.md:59`（条目 105）：目标文档行在位，属启发式误报
- `docs/事故-停工事故复盘-2026-11-25.md:31`（条目 106）：目标文档行在位，属启发式误报
- `docs/事故清单.md:59`（条目 107）：目标文档行在位，属启发式误报
- `app/src/main/java/io/legado/app/ui/book/read/ink/InkFrost.kt:23`（条目 116）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:87`（条目 117）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:386`（条目 118）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/model/CampaignModels.kt:198`（条目 119）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/service/AudioSkipPolicy.kt:3`（条目 121）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrlNetworkOptions.kt:9`（条目 122）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/components/HalfSheetModel.kt:262`（条目 123）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/replay/BattleModels.kt:61`（条目 124）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt:121`（条目 125）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/replay/BattleModels.kt:59`（条目 126）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt:117`（条目 127）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/read/ink/InkFrost.kt:32`（条目 128）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:115`（条目 129）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/settings/SettingControls.kt:54`（条目 130）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:75`（条目 131）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt:38`（条目 132）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:92`（条目 133）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:95`（条目 134）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:57`（条目 135）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:75`（条目 136）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:135`（条目 137）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/utils/ContextExtensions.kt:186`（条目 138）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3PrefBridge.kt:1`（条目 139）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:1`（条目 140）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:1`（条目 141）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:4`（条目 142）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/b3/B3SheetTargets.kt:1`（条目 143）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/settings/SettingControls.kt:54`（条目 144）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:251`（条目 145）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:54`（条目 146）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3PrefBridge.kt:86`（条目 147）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/utils/ContextExtensions.kt:186`（条目 148）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/read/ink/InkModels.kt:72`（条目 151）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/read/vertical/VerticalModels.kt:50`（条目 152）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/book/read/vertical/VerticalModels.kt:50`（条目 153）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:251`（条目 156）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/settings/SettingControls.kt:54`（条目 157）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3PrefBridge.kt:28`（条目 158）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:137`（条目 159）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/settings/SettingSheets.kt:38`（条目 160）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3PrefBridge.kt:28`（条目 161）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingModels.kt:137`（条目 162）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/utils/ContextExtensions.kt:186`（条目 163）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/card/TopicMarxCard.kt:37`（条目 164）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/main/explore/InkExploreModels.kt:6`（条目 165）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/main/discover/DiscoverMotion.kt:25`（条目 166）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:135`（条目 168）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/map/crossing/model/CampaignModels.kt:209`（条目 169）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/model/Enums.kt:47`（条目 170）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:468`（条目 171）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/config/b3/B3SettingGroups.kt:68`（条目 172）：目标文件在位，锚点行与来源结论一致
- `docs/锚点/Q306-主AI空等门禁.md:11`（条目 175）：目标文档行在位，属启发式误报
- `docs/锚点/Q306-主AI空等门禁.md:4`（条目 177）：目标文档行在位，属启发式误报
- `app/src/main/java/io/legado/app/ui/main/explore/InkExploreCards.kt:126`（条目 179）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/main/explore/InkExploreModels.kt:66`（条目 180）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/main/explore/InkExploreGrid.kt:121`（条目 181）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/card/TopicMarxCard.kt:37`（条目 182）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/config/b3/B3PrefBridge.kt:29`（条目 184）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/components/contract/ComponentSystemContract.kt:59`（条目 185）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/components/ComponentState.kt:30`（条目 187）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/components/contract/ComponentSystemContract.kt:21`（条目 188）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:865`（条目 192）：旧行号漂移，符号现行命中于该行
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:934`（条目 193）：旧行号漂移，符号现行命中于该行
- `docs/锚点/Q273-挂死二分.md:23`（条目 194）：目标文档行在位，属启发式误报
- `app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfViewModel.kt:40`（条目 195）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/main/search/SearchModels.kt:119`（条目 196）：目标文件在位，锚点行与来源结论一致
- `docs/立项草案-批次D.md:9`（条目 197）：目标文档行在位，属启发式误报
- `docs/立项规则.md:58`（条目 198）：目标文档行在位，属启发式误报
- `app/src/main/java/io/legado/app/ui/map/crossing/model/CampaignModels.kt:236`（条目 199）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/help/bubble/contract/EraCandidateFilter.kt:30`（条目 202）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/help/bubble/contract/EraCandidateFilter.kt:65`（条目 203）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/help/bubble/contract/EraCandidateFilter.kt:75`（条目 204）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/help/bubble/contract/EraCandidateFilter.kt:86`（条目 205）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/model/CampaignModels.kt:137`（条目 206）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:726`（条目 207）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/model/CampaignModels.kt:137`（条目 208）：目标文件在位，锚点行与来源结论一致
- `app/src/main/java/io/legado/app/ui/map/crossing/model/CampaignModels.kt:246`（条目 209）：目标文件在位，锚点行与来源结论一致

## 五、可机械判部分的脚本建议（只给判据，不实现）

- 结构性误判（本批 13 条）：可机判。判据：锚点目标为 `docs/**/*.md`，且行内候选符号（IDENT_RE 抽出的 CamelCase/编号）在该文档目标行及其上下文 2 行内均不出现，而目标行非空、非分隔线、标题/散文完整 → 判「文档锚点在位，符号候选属启发式误报」。误判风险：① 散文行确实引用了某代码符号但写法被反引号切分漏掉（如点号包名、缩写文件名）；② 文档行被后续重排导致标题移位。对策：机判只出候选，仍留一条人工复核位；点号包名/缩写文件名单列，不并入本判据。
- 重贴锚（本批 18 条）：半自动。判据：目标文件存在，候选符号在仓内同文件唯一命中于另一行 → 给「旧行→新行」建议行号；多命中或跨文件搬家 → 不自动改，转人工。误判风险：同名符号多处声明、KDoc 与实现行分离；对策：要求符号命中数 = 1 且命中行是声明/调用行才建议回贴。
- 回源有效（本批 108 条）：不宜机判放宽。判据只能确认「文件存在 + 目标行非空」，行内容与结论是否相符属语义判断；机检若只按行存在就判 OK，会把空泛收尾行（`)`/`}`/`*/`）误当有效证据。对策：区间收尾锚点必须连同区间起点行一起核，单行花括号收尾行不计独立证据。
- 判已废 / 真债：不可机判自动定死。已废必须有现行后继符号或后续收口报告回证；真债必须是文件与后继符号都 grep 零命中。禁止机检把「当前 grep 不到」直接写已废。

## 六、二阶三阶影响

- 一阶：本批 139 条在文档侧完成定裁留档；回源有效 108、重贴锚 18 的证据落点已逐条回证现行全路径行号。
- 二阶：雷达失联计数不会因本单自动下降——本单是只读定裁，未在源码/旧报告侧物理回贴锚点，雷达下一轮仍会按原口径把这些旧锚报 MISSING。要把计数真正收下来，需要后续收口单按重贴锚表把旧报告行内旧行号回贴现行行号（回源有效条目行号本就在位，无需回贴）。
- 三阶：基线还差多少。开工实测失联 549（MISSING 545 + DEADFILE 4），基线 496，超基线 53。本批 139 条定裁后，账面上：回源有效 + 结构误判 + 已废 + 真债均属「已有人认领的结论」，但雷达机读口径不变，棘轮仍按原始 MISSING 计数；只有物理回贴/豁免落账后计数才回落。后续销账归属：重贴锚 18 条交 settings/crossing 域收口单回贴旧报告行；结构误判 13 条交雷达脚本维护域收窄候选启发式；回源有效 108 条留档备查，不需动作。
- 掩盖风险自检：本批未把任何「查不到」记成已废；已废 0、真债 0 是逐条回证后的结果，不是放宽口径。残余风险在于回源有效条目里区间收尾锚点（花括号/星号收尾行）依赖同区间起点行成立，若起点行后续被删，收尾锚点会失效——已在第五节机判建议里要求区间双端核对。

## 七、反例边界

- 重贴锚 vs 判已废边界：符号在同文件他行命中（唯一）→ 重贴锚；符号随文件整体删除、仓内零声明零调用、且有后续收口报告或后继符号承接 → 判已废；文件在但符号 grep 全仓零命中、也无后继 → 真债，不判已废。
- 同一符号改名/搬家算不算已废：不算。改名后存在后继全路径行且 grep 可命中 → 重贴锚（旧符号留档、新落点回贴）；只有旧符号与后继符号都无证据时才入真债。本批 #131/#133 的裁剪/钳制纯函数、#188 的组件视觉状态枚举均属改名/搬家后在位，按重贴锚处理。
- 最强反例自查：① 空数据——某目标文件本批不存在则应进 DEADFILE/已废，实测 139 条文件全部解析在位，0 DEADFILE；② 同名歧义——基名多文件命中时雷达记 AMBIGUOUS 单列，本批 139 条无 AMBIGUOUS 混入；③ 行号越界——139 条目标行均未越出文件总行数；④ 并发漂移——复核时点为本单开工 HEAD，后续并行单落地后行号可能再漂，重贴锚行号为定位辅助，符号名才是主锚（N8 双锚口径）。
- 本单相对 B209 旧分类的三处改判（独立复核推翻旧结论）：#99 旧判重贴锚，复核 CrossingShell 第 252 行仍为 `autoPlay = false`，重置块在位，改回源有效；#142 旧判回源有效，复核 import configPreferenceMatches 现行第 4 行（第 3 行为 PreferKey import），改重贴锚 3→4；#193 旧判回源有效，复核名册文案表达式现行第 934 行，改重贴锚 762→934。

## 八、认知成本

- 下一棒复核本守恒账需读：① 本件 `docs/重判-雷达139条MISSING.md`；② 口径前身 `docs/重判-雷达待裁191.md`（五分类定义）与 `docs/重判-雷达待裁B批209.md`（209 条冻结清单与旧裁）；③ 雷达判据 `data_scripts/check-drift.py`（OK/DRIFT/MISSING/DEADFILE/AMBIGUOUS 判定段）；④ 基线 `data_scripts/drift-baseline.json`。共 4 份文档/脚本 + 本件。
- 复核命令：重跑 `python3 data_scripts/check-drift.py` 对账计数；按本件附录 A 逐条 `sed -n 现行行号p 现行全路径` 回证；重贴锚条目用符号名 grep 复核命中唯一性。

## 九、边界与未验证项

- 只新增本裁决文档；`docs/问题清单.md` 本批真债 0 条，无续号条目追加。
- 本单未跑编译、未跑 CI、未上真机；「现行落点」均为静态回源（grep / 读行）结论，未经编译面确认。
- 未改 `app/src/**`（另有代理在跑星空层二次验收）、未改 `data_scripts/**` 门禁脚本与 `gate-levels.json`、未改雷达基线与其 sha、未改 `docs/派单台账.md` 与 `docs/派单队列.md`。
- 旧报告证据句、旧锚保留留档；本单未批量回改 B209 旧报告正文，物理回贴由后续收口单决定。
- 头标「漂移记录件」沿用 `docs/分析-漂移重判清单.md` 同一口径：本件是重判登记件而非现行主张报告，雷达整份跳过扫描，登记锚点不计入失联棘轮；附录 A 的现行落点仍可由下一棒按 §八 命令逐条回证，跳过的是机读计数，不是证据效力。

