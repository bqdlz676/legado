# 验收-B4-08 气泡 resolver 接线 · 锚点卡

锚点卡：docs/锚点/验收-B4-08Resolver接线.md
本卡路径：docs/锚点/验收-B4-08Resolver接线.md
对应报告：docs/验收-复核-B4-08Resolver接线.md
被验提交：`d15eb07329885be0382db41eb8e2986f7dafeb22`（短 d15eb07；父提交 `8bd58423711168f532632203bed38a6f2c5f757f`，短 8bd5842）

## 验收判据（逐字回源）

- 判据原句（docs/拆分-B4阅读页小单.md:80-85 B4-08 条目）：标题「气泡 resolver 接线（先于旧解析桩删除）」；白名单目录 `ink/InkBubble.kt` 调用侧、`ink/InkActionDispatcher.kt`；`help/bubble/**` 只读；四包零改动；判据锚 R9 / R25；定界 I-13 / I-16 / P-01 去向；产物「气泡调用点全部经 resolver；Provider 空值落 Missing 兜底」；依赖 B4-06，必须先于 B4-09 落位；放行条件：`resolve` 单点入口、五子类在位、无 Provider 直出气泡、Missing 兜底不崩不空屏、四包 diff 空集。
- 判据原句（docs/任务书-B4-阅读页.md:53 R9）：「气泡统一入口 `BubbleResolver.resolve(paragraph, pos, anchor)`；Provider 按优先级链首个非空返回；Action 五子类 `OpenMap/OpenPerson/OpenTimeline/OpenRelation/OpenArtifact` 冻结」；逐字原文「统一入口只有一个：BubbleResolver.resolve(paragraph, pos, anchor) → BubbleContent，按下面顺序问各 Provider，第一个非空返回」。
- 定界锚（docs/任务书-B4-阅读页-引擎定界.md:352 I-13、:371 I-16、:45 P-01、:514 §六二阶风险 1）：I-13 `InkBubble.kt` 展示映射纯函数保留；I-16 `InkActionDispatcher.kt` 路由纯逻辑保留；P-01 旧壳气泡弹窗桩去向；resolver 链补齐前禁删旧解析桩，禁止留空命中态。

## 被验改动面（提交内 5 件，git show d15eb07 --numstat 自取）

- 主码两件（`app/src/main/java/io/legado/app/ui/book/read/ink/`）：
  - `InkActionDispatcher.kt`（M，55+/5-）：补契约五子类路由缝 `routeContract` / `canDispatchContract`；既有链 `route` / `canDispatch` / `dispatch` 签名与四分支零改动；KDoc 订正为五子类双轨口径。
  - `InkBubble.kt`（M，5+/2-）：`resolveBody` 正文 / 详情双空落 `HalfSheetFallback.MISSING_TEXT`；纯展示映射纯函数保留，卡片 UI 未重写。
- 测试一件（`app/src/test/java/io/legado/app/ui/book/read/ink/`）：`InkBubbleRouteResolverTest.kt`（A，172 行）：契约五子类逐一路由、旧链四类回归、Resolver Missing 兜底六例。
- 文档两件：`docs/修复-B4-08-Resolver接线.md`（A，148 行）；`docs/锚点/B4-08-Resolver接线.md`（A，52 行）。

## 依赖与边界

- 只读复用：`help/bubble/BubbleResolver.kt:64` 空段落兜底；:66 越界位夹取；:72-74 异常兜落 Missing；:118-131 missing 完整气泡；:133-134 safe 兜异常；`help/bubble/MissingProvider.kt:27-39` Missing 兜底结构；`help/bubble/ArtifactBubbleProvider.kt:76-89` 契约→旧链适配；:91-97 mapAction 五子类映射（OpenArtifact 置 null 丢弃）；`help/bubble/contract/BubbleContractModels.kt:40/:43/:46/:49/:52` 五子类定义。
- 冻结面：`page/provider`、`page/entities`、`page/api`、`page/delegate` 四包；`help/bubble/**`；`model/ReadBook.kt`；`paragraph/**`；`vertical/VerticalLayoutEngine.kt`；`ui/card`、`ui/components`、`data/dao`、`data/entities`、`help/allusion`、`assets`；`*.gradle` / `*.kts`；`res/layout/**`——提交内 diff 空集。
- 已知未接：OpenArtifact 名物详情 Sheet 消费方未接线（适配层丢弃动作，旧链 UI 不渲染 chip）；新旧双轨动作模型退役归契约切壳批次；控制器 `requestBubble` 极端 null 兜底未改（白名单不含控制器）；旧详情半屏 `ReaderSheetOverlay` 仍消费 `ReaderDetailRequest` 旧桩通道。
- 接缝归属：名物详情 / OpenArtifact 路由落地归后续名物接线单；契约内容模型整体切壳归切壳批次；控制器兜底加固归控制器加固单；详情半屏契约并轨归 B4-09 / B4-10 剥桩后批次。

## 验收方式

- L1 静态：`git show d15eb07 --name-status/--numstat` 取改动面；冻结目录逐目录 `git diff --name-only d15eb07^ d15eb07` 回证空集；全仓归类 `BubbleContent(` 构造点；ink/ 内 `BubbleResolver` 引用逐处回源；五子类定义行与 `routeContract` 映射行逐行对照；Missing 六类逐类验代码路径与测试覆盖；双轨沿真实调用链追生产消费方；断言逐行重算，扫反射 / 源码 contains / 恒真；机检脚本验收方重跑贴末行。
- L2 运行期：未本地编译、未跑 gradle/kotlinc/assemble、未跑全量单测、未真机；编译与单测绿红以云端 Verify CI 为准，本验收不替 CI 背书。
- 独立验收按本卡「验收判据」逐条对照源码；判低则换项目 AI、废代码重做再交验收。

## 回源命令（逐条结论可复跑）

- 改动面：`git show d15eb07 --name-status`；`git show d15eb07 --numstat`
- 单点入口：`grep -rn "BubbleResolver" app/src/main/java/io/legado/app/ui/book/read/ink/`
- 壳内不直造气泡：`grep -rn "BubbleContent(" app/src/main/java/io/legado/app/ui/book/read/ink/`
- 全仓构造点归类：`grep -rln "BubbleContent(" app/src/main/java/`
- 五子类定义：`grep -n "data class Open" app/src/main/java/io/legado/app/help/bubble/contract/BubbleContractModels.kt`
- 契约路由缝：`grep -n "routeContract\|canDispatchContract\|ContractAction\|OpenArtifact" app/src/main/java/io/legado/app/ui/book/read/ink/InkActionDispatcher.kt`
- 契约缝生产调用方：`grep -rn "routeContract\|canDispatchContract" app/src/`
- 冻结四包空集：`git diff --name-only d15eb07^ d15eb07 -- app/src/main/java/io/legado/app/ui/book/read/page/provider app/src/main/java/io/legado/app/ui/book/read/page/entities app/src/main/java/io/legado/app/ui/book/read/page/api app/src/main/java/io/legado/app/ui/book/read/page/delegate`
- 冻结目录空集：`git diff --name-only d15eb07^ d15eb07 -- app/src/main/java/io/legado/app/help/bubble app/src/main/java/io/legado/app/ui/card app/src/main/java/io/legado/app/ui/components app/src/main/java/io/legado/app/data/dao app/src/main/java/io/legado/app/data/entities app/src/main/java/io/legado/app/help/allusion app/src/main/assets`
- 切片原文：`sed -n '80,85p' docs/拆分-B4阅读页小单.md`；`sed -n '53p' docs/任务书-B4-阅读页.md`
- 机检：`python3 data_scripts/kotlin-syntax-lint.py <三件 kt>`；`python3 data_scripts/check-compose-pitfalls.py app/src`；`python3 data_scripts/check-design-tokens.py`；`python3 data_scripts/check-test-assert-truth.py --changed`；`python3 data_scripts/check-gate-wiring.py`；`python3 data_scripts/check-member-consumers.py`；`python3 data_scripts/check-member-override.py`；`python3 data_scripts/acceptance-evidence.py --rev d15eb07`
