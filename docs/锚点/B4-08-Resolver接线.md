# 锚点卡 · B4-08 气泡 resolver 接线（先于旧解析桩删除）

- 自身路径：docs/锚点/B4-08-Resolver接线.md
- 主文档：docs/修复-B4-08-Resolver接线.md
- 任务：ink/ 内复核气泡创建链路唯一经 `BubbleResolver.resolve` / `queryToken` 单点入口；冻结契约五子类 `OpenMap / OpenPerson / OpenTimeline / OpenRelation / OpenArtifact` 在分发层穷尽路由；Provider 空值 / 未知符号 / 空段落 / 越界字位一律落 Missing 兜底，不崩不空屏；`help/bubble/**` 只读，四包零改动；旧解析桩保留不删；不做 git add/commit/push。
- 基线：落盘时点 HEAD 以调度层台账为准；四包、`help/bubble/**`、paragraph/**、vertical/VerticalLayoutEngine.kt、model/ReadBook.kt、*.gradle、res/layout 只读面 diff 空集；主代码与测试件经 kotlin-syntax-lint / compose-pitfalls / design-tokens / symbol-existence / test-assert-truth 静态回证；编译与单测绿红以下一轮 Verify CI 为准。

## 一、回源锚点（文件:行号）

- docs/拆分-B4阅读页小单.md:80：B4-08 条目（气泡 resolver 接线，先于旧解析桩删除）；:81：白名单 `ink/InkBubble.kt` 调用侧、`ink/InkActionDispatcher.kt`，`help/bubble/**` 只读，四包零改动；:82：判据锚 R9 / R25，定界 I-13 / I-16 / P-01 去向；:83：产物口径（气泡调用点全部经 resolver；Provider 空值落 Missing 兜底）；:84：依赖 B4-06，先于 B4-09 落位，§六二阶风险 1（resolver 未补齐前禁删旧解析桩）；:85：放行条件五条。
- docs/任务书-B4-阅读页.md:53：R9 气泡统一入口 `BubbleResolver.resolve(paragraph, pos, anchor)`，Provider 优先级链首个非空返回，Action 五子类冻结；:69：R25 批注链路（气泡弹开写 vocab_log，画板批注带章节名 + 正文纯文本）。
- docs/任务书-B4-阅读页-引擎定界.md:352：I-13 `InkBubble.kt`（展示映射纯函数保留，卡片 UI 重写）；:371：I-16 `InkActionDispatcher.kt`（路由纯逻辑保留，Context/Intent 接线随新壳重接）；:45：P-01 `ContentTextView.kt` 旧壳气泡弹窗桩去向；:514：§六二阶风险 1（先补 resolver 链再删 View 内旧解析桩，禁止留空命中态）。
- app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt:282 `BubbleResolver.resolve`；:336 `BubbleResolver.queryToken`；:185 `clearCache`；:281-286 解析兜底段。
- app/src/main/java/io/legado/app/ui/book/read/ink/InkActionDispatcher.kt:53 既有链 `route` 四分支；:79 `canDispatch`；:89 `routeContract` 契约五子类穷尽；:90 OpenMap；:95 OpenPerson；:102 OpenRelation；:108 OpenTimeline；:114 OpenArtifact → null；:118 `canDispatchContract`。
- app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt:448 `resolveBody`；:451-453 双空落 `HalfSheetFallback.MISSING_TEXT`；:441 `actionLabel` 既有链四类标签。
- app/src/main/java/io/legado/app/help/bubble/contract/BubbleContractModels.kt:40 `OpenMap`；:43 `OpenPerson`；:46 `OpenTimeline`；:49 `OpenRelation`；:52 `OpenArtifact`。
- app/src/main/java/io/legado/app/help/bubble/BubbleResolver.kt:64 空段落兜底；:66 越界位夹取；:72-74 异常兜落 Missing；:118-131 `missing` 完整气泡；:133-134 `safe` 兜异常。
- app/src/main/java/io/legado/app/help/bubble/MissingProvider.kt:27-39 Missing 兜底气泡结构。
- app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt（只读引用）:72 `charBubble`；:648 `showCharBubble`；:1131-1132 旧桩复位；旧解析族 :1174 / :1198 / :1270 / :1279 / :1292 / :1327 保留。
- app/src/test/java/io/legado/app/ui/book/read/ink/InkBubbleRouteResolverTest.kt：契约五子类逐一路由、既有链四类回归、Missing 兜底六例；12 个 `@Test`、172 行、48 条断言。

## 二、基线摘要

- 主代码改动两件：InkActionDispatcher.kt（契约五子类路由缝 + KDoc 订正）、InkBubble.kt（正文双空占位兜底）；测试新增一件 InkBubbleRouteResolverTest.kt；未新增密封子类 / 枚举成员，未改 `ReaderShellHost` 接口。
- 冻结回证：四包 git diff --name-only 0 行；help/bubble、ui/card、ui/components、data/dao、data/entities、help/allusion、assets diff 空集；paragraph/** 与 vertical/VerticalLayoutEngine.kt diff 空集；ReadBook.kt diff 0 行；build.gradle 系列与 res/layout 未改。
- 同源口径：气泡数据只由 Resolver 产出，ink 壳内 0 处直造 `BubbleContent`；候选重查只回传裸 query；未接通动作 chip 置灰，不兜底跳转。
- 交接：OpenArtifact 名物详情 Sheet 消费方未接线（问题 1 / 4）；新旧双轨动作模型退役归契约切壳批次（问题 2）；控制器极端 null 兜底待加固（问题 3）；旧详情半屏与契约链并轨归 B4-09 / B4-10 后批次（问题 5）。

## 三、机检命令（回源）

- `python3 data_scripts/check-report-fields.py docs/修复-B4-08-Resolver接线.md`
- `python3 data_scripts/check-anchor-card.py --strict docs/修复-B4-08-Resolver接线.md docs/锚点/B4-08-Resolver接线.md`
- `python3 data_scripts/check-problem-finding.py docs/修复-B4-08-Resolver接线.md`
- `python3 data_scripts/check-compose-pitfalls.py app/src`
- `python3 data_scripts/check-design-tokens.py`
- `python3 data_scripts/kotlin-syntax-lint.py <改动主代码与测试件>`
- `python3 data_scripts/check-symbol-existence.py`
- `python3 data_scripts/check-test-assert-truth.py --changed`
- `python3 data_scripts/check-onboarding.py`

## 四、回源命令（逐条结论可复跑）

- Resolver 单点入口：`grep -rn "BubbleResolver" app/src/main/java/io/legado/app/ui/book/read/ink/`
- 契约五子类定义：`grep -n "data class Open" app/src/main/java/io/legado/app/help/bubble/contract/BubbleContractModels.kt`
- 契约路由缝在位：`grep -n "routeContract\|canDispatchContract\|ContractAction\|OpenArtifact" app/src/main/java/io/legado/app/ui/book/read/ink/InkActionDispatcher.kt`
- 壳内不直造气泡：`grep -rn "BubbleContent(" app/src/main/java/io/legado/app/ui/book/read/ink/`
- 四包零改动：`git diff --name-only -- app/src/main/java/io/legado/app/ui/book/read/page/provider app/src/main/java/io/legado/app/ui/book/read/page/entities app/src/main/java/io/legado/app/ui/book/read/page/api app/src/main/java/io/legado/app/ui/book/read/page/delegate`
- 冻结目录零改动：`git diff --name-only -- app/src/main/java/io/legado/app/help/bubble app/src/main/java/io/legado/app/ui/card app/src/main/java/io/legado/app/ui/components app/src/main/java/io/legado/app/data/dao app/src/main/java/io/legado/app/data/entities app/src/main/java/io/legado/app/help/allusion app/src/main/assets`
- ReadBook 只读：`git diff --name-only -- app/src/main/java/io/legado/app/model/ReadBook.kt`
- 旧解析桩保留：`grep -n "showCharBubble\|charBubble" app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`
- 切片原文：`sed -n '80,86p' docs/拆分-B4阅读页小单.md`
- R9 原文：`sed -n '53p' docs/任务书-B4-阅读页.md`
