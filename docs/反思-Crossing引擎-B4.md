# 反思 · Crossing 引擎 B4（畸形河流点列 + load 顶层兜底）

## 1. 做了什么

在 parser 河流段补齐 `pts` 四态分流（缺失 / JsonNull / 非数组 / 数组）与 `rivers` 字段级错位 warning；`CampaignController.load` 拆出 `assemble` / `assembleDegraded`，装配链包顶层 `catch(Throwable)`，异常回落空战场降级态；新增 4 条单测（parser +3、controller +1）。改动基线：工作树未提交，未编译、未 push。

## 2. 踩的坑 / 教训

- **前任报告的行号会漂**：任务书写 `CampaignJsonParser.kt:257-267`、`CampaignController.kt:464`，实际工作树 parser 河流段在 267-294、`load` 在 797-812；必须以当前文件 grep 定位为准，不能照抄旧行号。
- **字段级错位 ≠ 元素级错位**：前任守卫把 `rivers` 整体错位兜成空表但没记 warning；验收报告 4.3 已点名该缺口。收口时要分清"字段类型错位"和"数组内元素畸形"两条路径，warning 各记各的。
- **止损判定先查耦合再动手**：动 `load` 前先核对 `saveBranch` / `restoreBranch` 是否共享字段或调用；本单确认二者无耦合才写 Controller 侧，避免波及 B7 存档域。

## 3. 我可能判断错的地方

- `assembleDegraded` 分支在公开 API 下无法被单测触发（没有可注入的必抛构造器），其可建性是空军队路径的静态推演，未实跑验证。
- 六类畸形在 Gson 2.14 下各自走哪条回落分支（catch 路径 vs `isFinite` 过滤路径）沿用验收报告的静态推断，异常子类未在本地 jar 复核。
- `catch(Throwable)` 覆盖 `Error` 只是语法层面；OOM 后的降级装配能否成功未验证，报告里已标不承诺。

## 4. 遗留未决项

- 点列 / 数组无点数上限，超大数组 OOM 边界未护栏。
- sides / diplomacy / events / ridges 字段级错位仍静默回落空表，warning 缺口未补。
- `parseVictoryDef` 递归深度无防护；壳层空战役渲染与 warning 遍历展示未复核。
- 全部单测未在 JVM 实跑，编译正确性待云端 CI。

## 5. 给下任的一句话提醒

本报告与前任 `修复-B4-畸形数据兜底.md` 中的行号、测试计数都已漂移，**复核结论必须回当前工作树 grep 重新定位**，不要照抄；接手前先跑 CI 确认这 4 个文件编译过，再判断降级分支是否需要补可注入异常的测试桩。
