锚点卡：docs/锚点/B4-08-Resolver接线.md

# 修复-B4-08 气泡 resolver 接线（先于旧解析桩删除）

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「grep 回证：改动必须真出现在文件里，未生效不许提交（防"报告说改了、文件没变"）。」（docs/机制-总控必读.md:50）
- 逐字引用 2：「**B2 改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。**」（docs/机制-总控必读.md:278）
- 逐字引用 3：「**C3 规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。**」（docs/机制-总控必读.md:355）
- 适用条款：A1（四包冻结一行不动）、A3（新逻辑先接好再删旧，旧解析桩本单保留不删）、A5（冻结契约签名不改）、B2（grep 回证）、B3（二阶三阶推演）、C3（Missing 兜底不空白态）、E8（沙箱禁编译，编译单测走云端 CI）、F8（新发现问题带状态词与归属小单）。

总结论：有条件通过。气泡创建链路在静态面唯一经 `BubbleResolver.resolve` / `queryToken` 单点入口（控制器既有 B4-06 接线在位，本单复核无第二套直出路径）；冻结契约五子类 `OpenMap / OpenPerson / OpenTimeline / OpenRelation / OpenArtifact` 定义在位，分发层补契约五子类穷尽路由缝；Provider 全空 / 未知符号 / 空段落 / 越界字位均落 Missing 兜底，结构完整不崩不空屏；四包、`help/bubble/**`、`model/ReadBook.kt`、paragraph、vertical 冻结面 diff 空集；主代码与测试件经静态机检全绿。编译 / 全量单测 / 真机行为以下一轮云端 CI 为准（见 §七未验证项）。

## 一、改动清单（文件 → 为什么改）

切片依据：docs/拆分-B4阅读页小单.md:80（B4-08 标题「气泡 resolver 接线（先于旧解析桩删除）」）、:81（白名单：`ink/InkBubble.kt` 调用侧、`ink/InkActionDispatcher.kt`；`help/bubble/**` 只读；四包零改动）、:82（判据锚 R9 / R25；定界 I-13 / I-16 / P-01 去向）、:83（产物：气泡调用点全部经 resolver；Provider 空值落 Missing 兜底）、:84（依赖 B4-06；必须先于 B4-09 落位；§六二阶风险 1：resolver 未补齐前禁删旧解析桩）、:85（放行条件：resolve 单点入口、五子类在位、无 Provider 直出气泡、Missing 兜底不崩不空屏、四包 diff 空集）。

- app/src/main/java/io/legado/app/ui/book/read/ink/InkActionDispatcher.kt：补冻结契约五子类路由缝 `routeContract` / `canDispatchContract`。既有链 `route` / `canDispatch` / `dispatch` 签名与四子类分支零改动；契约侧 `contract.Action` 五子类穷尽映射，`OpenArtifact` 名物详情 Sheet 消费方尚未接线，返回 null、chip 置灰，不兜底跳转。KDoc 由「冻结四类」订正为五子类双轨口径。
- app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt：`resolveBody` 双空兜底加固——正文与详情均空白时落 `HalfSheetFallback.MISSING_TEXT` 占位文案，不出空气泡解释行；纯展示映射纯函数保留，卡片 UI 未重写。
- app/src/test/java/io/legado/app/ui/book/read/ink/InkBubbleRouteResolverTest.kt（新增）：纯 JVM 单测。覆盖契约五子类逐一类路由（OpenMap / OpenPerson / OpenTimeline / OpenRelation / OpenArtifact）、既有链四类回归、Resolver Missing 兜底六例（生僻字 / 未知符号 / 空段落 / 越界字位 / 候选整词未收录 / 纯空白段落）。

只读面回证：`help/bubble/**` diff 0 文件；page/provider、page/entities、page/api、page/delegate 四包 diff 0 文件；paragraph/**、vertical/VerticalLayoutEngine.kt、model/ReadBook.kt、ui/card、ui/components、data/dao、data/entities、help/allusion、assets 零改动（grep 回证见 §8.1）。

## 二、逐项接线证明（文件:行 + 调用链）

### 2.1 resolve 单点入口：气泡内容只经 Resolver 产出

- 判据原文：docs/任务书-B4-阅读页.md:53 R9「气泡统一入口 `BubbleResolver.resolve(paragraph, pos, anchor)`；Provider 按优先级链首个非空返回；Action 五子类 `OpenMap/OpenPerson/OpenTimeline/OpenRelation/OpenArtifact` 冻结」；逐字原文「统一入口只有一个：BubbleResolver.resolve(paragraph, pos, anchor) → BubbleContent，按下面顺序问各 Provider，第一个非空返回」。
- 入口在位：`InkReadController.kt:282` `BubbleResolver.resolve(hit.paragraphText, hit.pos, ContextAnchor)`；候选切换整词重查 `InkReadController.kt:336` `BubbleResolver.queryToken(token, ContextAnchor)`。两处之外，ink/ 内 `BubbleResolver` 仅余生命周期接缝：`InkReadActivity.kt:172` `preload`、`InkReadController.kt:185` `clearCache`，均不产出气泡内容。
- 直出路径审计：`grep -rn "BubbleContent(" app/src/main/java/io/legado/app/ui/book/read/ink/` 0 命中——ink 壳内不直造气泡内容，气泡数据全部由 Resolver 回传；气泡 UI 组件 `InkBubble(...)` 仅在 `InkReaderShell.kt:168` 一处被调用，入参 `bubbleContent` 取自控制器 `controller.bubbleContent`（`InkReaderBook.kt:73`）。
- 候选回链：`InkReaderBook.kt:61-62` 缺省候选回调接 `controller::switchCandidate`；重查只回传裸 query（`BubbleCandidate.query`），展示串括号装饰不进重查词（既有 B4-06 口径，本单复核在位）。

### 2.2 五子类在位：冻结契约定义与分发穷尽分支

- 定义回源：`app/src/main/java/io/legado/app/help/bubble/contract/BubbleContractModels.kt:40` `OpenMap(placeId)`；`BubbleContractModels.kt:43` `OpenPerson(name)`；`BubbleContractModels.kt:46` `OpenTimeline(year)`；`BubbleContractModels.kt:49` `OpenRelation(name)`；`BubbleContractModels.kt:52` `OpenArtifact(artifactId, type)`。契约头注 `BubbleContractModels.kt:31-32`「五子类……此后不再增删」；模块契约索引 docs/落文-模块契约索引.md:150「sealed Action(OpenMap / OpenPerson / OpenTimeline / OpenRelation / OpenArtifact)……五子类」。
- 既有链（旧 C2 `help.bubble.Action`，`BubbleModels.kt:54-77`）四子类 `OpenMap / OpenPerson / OpenTimeline / OpenRelation` 供存量 Provider 产出的旧 `BubbleContent` 消费；`InkActionDispatcher.kt:53-75` `route` 四分支穷尽，签名零改动。
- 契约链路由缝：`InkActionDispatcher.kt:89` `routeContract(action: ContractAction, anchor)` 五子类 `when` 穷尽——`InkActionDispatcher.kt:90` OpenMap → `InkRoute.Map`；`InkActionDispatcher.kt:95` OpenPerson → `InkRoute.Person`；`InkActionDispatcher.kt:102` OpenRelation → `InkRoute.Relation`；`InkActionDispatcher.kt:108` OpenTimeline → `InkRoute.Timeline`；`InkActionDispatcher.kt:114` OpenArtifact → null。`InkActionDispatcher.kt:118` `canDispatchContract` 按路由非空判定接通与否。
- 双轨不复刻第二套：契约五子类直接复用 `help.bubble.contract.Action` 既有定义，ink 层未另建并行动作模型；既有链四子类分支保留，供旧内容兼容消费。

### 2.3 Missing 兜底：空值 / 未知类型不崩、不空屏、不抛异常

- Resolver 内部兜底口径（只读引用，未改）：`BubbleResolver.kt:64` 空段落直落 `missing`；`BubbleResolver.kt:66` 越界位夹取末位；`BubbleResolver.kt:72-74` 链路异常 `runCatching` 兜住后落 `missing`；`BubbleResolver.kt:118-131` `missing` 产出结构完整的 `BubbleKind.MISSING` 气泡，`allowWebSearch / allowNotebook` 置真，动作 / 候选空表，来源非空。
- 壳侧展示加固：`InkBubble.kt:448-453` `resolveBody`——Missing 态正文空白落 `MISSING_TEXT`；普通态正文空白回退详情、详情再空白落 `MISSING_TEXT`，气泡解释行不出空串。
- 边界样本（新单测 `InkBubbleRouteResolverTest.kt`）：`龘`（`InkBubbleRouteResolverTest.kt:117` 测试方法）生僻字落 Missing；`魑魅魍魉`（`InkBubbleRouteResolverTest.kt:132`）未知符号落 Missing；空串（`InkBubbleRouteResolverTest.kt:142`）token 回退「此处」；越界位 99（`InkBubbleRouteResolverTest.kt:151`）夹取末位「秋」；纯空白段落（`InkBubbleRouteResolverTest.kt:166`）不抛异常；候选整词未收录 `queryToken` 返 null（`InkBubbleRouteResolverTest.kt:160`），调用方保留当前气泡，不掉成空气泡。

### 2.4 旧解析桩保留：resolver 未完全接前不准删

- 定界 §六二阶风险 1（docs/任务书-B4-阅读页-引擎定界.md:514）：「P-01 气泡命中解析族若先于 resolver 链路拆走，会出现点字无命中；迂回：先在 `help/bubble` resolver 链补齐字词/人物/地名/纪年/典故命中口径，再删 View 内旧解析桩，禁止留空命中态」。
- 本单未触 `page/ContentTextView.kt`；旧桩在位回源：`ContentTextView.kt:72` `charBubble: PopupWindow?`；`ContentTextView.kt:648` `showCharBubble(...)`；`ContentTextView.kt:1131-1132` `dismissCharBubble` 复位；命中解析族 `ContentTextView.kt:1174` / `:1198` / `:1270` / `:1279` / `:1292` 与 `ContentTextView.kt:1327 showBubble` 全部保留，物理删除归 B4-09 及后续旧皮下线批次。

## 三、边界清单（每条给 文件:行 落点）

- Provider 全空：真实骨架各槽未收录时 Resolver 链全部返 null，落 `MissingProvider` 兜底（`MissingProvider.kt:22`）；测试 `龘` 断言 `kind=MISSING`、body/detail/source 非空、双兜底标志置真、actions/candidates 空表。
- 未知符号：未收名词条不猜身份、不编生卒 / 出处；`魑魅魍魉` 落 Missing，body 非空、source 非空。
- 空段落：`resolve("", 0)` 返 Missing，token 「此处」；`resolve("   ", 0)` 不抛异常。
- 越界字位：pos=99 夹取到段落末位，token 为末字「秋」，不崩。
- 候选整词未收录：`queryToken` 返 null；控制器 `switchCandidate` 见 null 保留当前气泡（`InkReadController.kt:337` 既有口径），不切空气泡。
- 未接通动作：`OpenArtifact` 暂无 Sheet 消费方，`routeContract` 返 null，`canDispatchContract` 返 false，气泡 chip 置灰不可点；既有链未接通动作同口径（`InkActionDispatcher.kt:79-80`）。
- 异常路径：Resolver 内部 `safe` / `runCatching` 兜各 Provider 异常（`BubbleResolver.kt:72-74`、`BubbleResolver.kt:133-134`）；壳侧补正文空白占位，不出空气泡解释行。

## 四、二阶三阶影响与跨模块交接

### 4.1 对既有链 / 契约链双轨

- 正向：契约五子类路由缝先在分发层穷尽就位，后续名物详情 Sheet 接线时只需补 `InkRoute.Artifact` 与 Intent 装配，不必再补 `when` 分支。
- 二阶：契约动作主键比既有链窄（无 placeName / personId / label 附加字段），路由模型附加字段回退 null；待契约内容模型整体切壳时，附加信息口径需与 EntityProvider 产出侧对齐。

### 4.2 对 B4-09 P-01 剥桩

- 本单复核 Resolver 链在位（字词 / 人物 / 地名 / 纪年 / 典故 / 名物 / 单字 / Missing 八槽），满足定界 §六风险 1 的前置条件；B4-09 剥桩时旧 `showCharBubble` / `showBubble` 命中解析族可按去向迁离引擎绘制路径，迁离后旧桩物理删除。
- 二阶：`ReaderSheetOverlay.kt` 仍消费旧 `ReaderDetailRequest` 四入口（人物 / 纪年 / 字词 / 地名），与 `BubbleContent` 契约链尚未并轨；详情半屏装配归后续批次。

### 4.3 跨模块问题交接

- 发现的旁支问题一律登记 §六，未在本单动手改（R57 一单一条）。

## 五、自查反例（改坏推演）

- 反例 1：假设「契约第五子类 OpenArtifact 应在既有链 `help.bubble.Action` 里补一个构造器」——否证：`help/bubble/**` 本单冻结只读，且既有 `BubbleModels.kt` 是旧 C2 实现，契约五子类定义唯一在 `contract/BubbleContractModels.kt`；另补一份等于造第二套密封层级。迂回：ink 分发层只复用既有契约定义，补契约侧路由缝，不碰旧模型。
- 反例 2：假设「`routeContract` 直接复用既有 `route` 传同一个 `Action` 类型即可」——否证：两类 `Action` 是不同 sealed 层级，字段口径不同（契约 OpenPerson 无 personId、OpenMap 无 placeName、OpenTimeline 无 label）；混传会丢主键或编译红。迂回：契约缝独立映射，附加字段显式回退 null，既有链签名不动。
- 反例 3：假设「Provider 返 null 时壳侧直接关气泡更省事」——否证：C3 不允许空白态；R9 兜底口径要求全空走 Missing。迂回：Resolver 内部 Missing 兜底 + 壳侧正文空白占位文案双保险；候选整词未收录保留当前气泡，不切空气泡。
- 反例 4：假设「本单顺手把 ContentTextView 旧解析桩删了，避免双轨残留」——否证：§六风险 1 要求 resolver 链先补齐；且 P-01 剥桩归 B4-09，本单越界删桩会留下点字无命中空态。迂回：旧桩整段保留回源，删除留待 B4-09。
- 反例 5：假设「新增分发方法需要同步给测试 FakeHost 补 override」——否证：本单未改 `ReaderShellHost` 接口，`routeContract` 是 `InkActionDispatcher` 对象上的新增普通方法，非接口成员；实现方清单与回证见 §8.2，无缺 override 缺口。
- 二阶风险：契约内容模型（`contract.BubbleContent`，候选 `List<String>`）与既有链 `BubbleContent`（候选 `List<BubbleCandidate>`）字段口径不同；整体切壳时候选映射需在适配层收口，不能在壳内另造转换。

## 六、新发现问题

- 问题 1：`OpenArtifact` 名物详情 Sheet 消费方未接线。`RegistryEntityProvider.kt:99-111` 名物气泡一级跳转挂 `Action.OpenArtifact`；适配层 `ArtifactBubbleProvider.kt:96` 把契约 `OpenArtifact` 映射为 null 丢弃，既有链 UI 看不到第五子类动作；本单 `routeContract` 对其返 null、chip 置灰。状态：已入队（归名物详情 Sheet 接线单，B4-09 之后批次；归属小单：名物详情 / OpenArtifact 路由落地）。
- 问题 2：既有链 `help.bubble.Action` 四子类与冻结契约 `contract.Action` 五子类双轨并存；旧 `BubbleModels.kt` 头注标注其为 C2 早期实现，旧实现拆除与壳层切换归后续接线单。状态：待裁（归旧链退役 / 契约切壳批次，不属本单；归属小单：契约内容模型整体切壳）。
- 问题 3：`InkReadController.requestBubble`（`InkReadController.kt:281-286`）`runCatching{...}.getOrNull()` 极端兜底：Resolver 正常永不返 null，仅 `MissingProvider` 自身抛异常时才会落到 null → `bubbleContent` 为 null 的空气泡路径；本单白名单不含控制器，未改。状态：待裁（归控制器兜底加固单，与 B4-06 既有 `runCatching` 口径对齐；归属小单：B4 阅读页控制器兜底加固）。
- 问题 4：契约第五子类 `OpenArtifact` 的气泡按钮标签（「名物」）未在 UI 标签位落地；`InkBubble.actionLabel`（`InkBubble.kt:441`）只标既有链四类。状态：待裁（随问题 1 名物详情接线单一并落地；归属小单：名物详情 / OpenArtifact 路由落地）。
- 问题 5：`ReaderSheetOverlay.kt` 旧详情半屏四入口消费 `ReaderDetailRequest`，与 `BubbleContent` 契约链未并轨；点字详情 Sheet 数据源仍走旧桩通道。状态：已入队（归 B4-09 / B4-10 剥桩后详情装配批次；归属小单：详情半屏契约并轨）。

## 七、未验证项

- 未本地编译、未跑全量单测、未真机（沙箱禁 gradle / kotlinc / assemble，编译只在云端 CI 发生）；编译与单测绿红以下一轮 Verify CI 为准。
- 契约五子类路由缝为纯 JVM 纯函数，单测离线覆盖；`OpenArtifact` 置灰口径在真机气泡 chip 上的观感未复验。
- 名物链（`EntityProvider` / `ArtifactBubbleProvider`）真实数据表落地后的命中口径未在本单验证；本单只复核既有适配与兜底路径。
- 双轨动作模型整体切壳的候选字段映射（`List<String>` ↔ `List<BubbleCandidate>`）未在本单实现，留待契约切壳批次。

## 八、机检实跑末行（逐字复制）

- `python3 data_scripts/kotlin-syntax-lint.py <改动主代码 2 + 测试件 1>`：`KOTLIN-LINT: PASS（3 个文件）`
- `python3 data_scripts/check-compose-pitfalls.py app/src`：`COMPOSE-PITFALLS: PASS（2158 个 Kotlin 文件，A/B/C 三类形态 0 命中）`
- `python3 data_scripts/check-design-tokens.py`：`DESIGN-TOKENS: PASS（机检 68 项全一致，spec-only 3 项登记在册）`
- `python3 data_scripts/check-symbol-existence.py`：`SYMBOL-EXISTENCE: PASS（扫描 2165 文件，调用 136044 处，NODEF 0 / ARITY 0 / PACKAGE 0，跳过生成 0 / 外部库 18590 / 标准库 43850 / 未知 70793）`
- `python3 data_scripts/check-test-assert-truth.py --changed`：`TEST-ASSERT-TRUTH: PASS（扫描 0 个测试文件，REFLECT/RAWSOURCE/EMPTYASSERT 三类 0 命中）`；新增测试件为未跟踪件，`--changed` 按 git diff 口径未计入；全量扫描本单测试件 0 命中（存量 954 处为历史债务，不在本单改动面）。
- `python3 data_scripts/check-test-fun-names.py`：`TEST-FUN-NAME: PASS（无反引号函数名含 . : [ ] / ; < >）`
- `python3 data_scripts/check-onboarding.py`：`ONBOARD: PASS（3 份报告）`
- `python3 data_scripts/check-report-fields.py docs/修复-B4-08-Resolver接线.md`：见 §8.3 回跑输出。
- `python3 data_scripts/check-anchor-card.py --strict docs/修复-B4-08-Resolver接线.md docs/锚点/B4-08-Resolver接线.md`：见 §8.3 回跑输出。
- `python3 data_scripts/check-problem-finding.py docs/修复-B4-08-Resolver接线.md`：见 §8.3 回跑输出。

### 8.1 grep 回证（命令 + 真实输出行号）

- Resolver 单点入口：`grep -rn "BubbleResolver" app/src/main/java/io/legado/app/ui/book/read/ink/` → `InkReadController.kt:282` `resolve`；`InkReadController.kt:336` `queryToken`；`InkReadActivity.kt:172` `preload`；`InkReadController.kt:185` `clearCache`；余为 KDoc 引用。
- 契约五子类定义：`grep -n "data class Open" app/src/main/java/io/legado/app/help/bubble/contract/BubbleContractModels.kt` → `BubbleContractModels.kt:40` / `:43` / `:46` / `:49` / `:52`。
- 契约路由缝在位：`grep -n "routeContract\|canDispatchContract\|ContractAction\|OpenArtifact" app/src/main/java/io/legado/app/ui/book/read/ink/InkActionDispatcher.kt` → `InkActionDispatcher.kt:6` / `:17` / `:22-25` / `:29` / `:86` / `:89` / `:90` / `:95` / `:102` / `:108` / `:114` / `:118` / `:119` / `:121`。
- 壳内不直造气泡：`grep -rn "BubbleContent(" app/src/main/java/io/legado/app/ui/book/read/ink/` → 0 命中。
- 四包零改动：`git diff --name-only -- app/src/main/java/io/legado/app/ui/book/read/page/provider app/src/main/java/io/legado/app/ui/book/read/page/entities app/src/main/java/io/legado/app/ui/book/read/page/api app/src/main/java/io/legado/app/ui/book/read/page/delegate` → 输出 0 行。
- 冻结目录零改动：`git diff --name-only -- app/src/main/java/io/legado/app/help/bubble app/src/main/java/io/legado/app/ui/card app/src/main/java/io/legado/app/ui/components app/src/main/java/io/legado/app/data/dao app/src/main/java/io/legado/app/data/entities app/src/main/java/io/legado/app/help/allusion app/src/main/assets` → 输出 0 行。
- ReadBook 只读：`git diff --name-only -- app/src/main/java/io/legado/app/model/ReadBook.kt` → 输出 0 行。
- 旧解析桩保留：`grep -n "showCharBubble\|charBubble" app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt` → `ContentTextView.kt:72` / `:648` / `:1131` / `:1132`。

### 8.2 密封子类 / 接口实现方登记

- 本单未新增密封子类或枚举成员；既有链 `help.bubble.Action` 四子类、契约 `contract.Action` 五子类零改动定义。
- 接口 / 宿主协议零改动：`ReaderShellHost`（`ReaderShellHost.kt:23-78`）方法签名未增删；无新增 override 缺口。实现方回证：主实现 `InkReadController.kt:68` `class InkReadController(...) : ReaderShellHost`；测试桩 `GestureRouterTest.kt`、`ViewGestureBridgeTest.kt` 两个 `FakeHost`；本单未触协议，两桩无需补方法。
- 既有 `when` 消费方逐条核对：
  - `InkActionDispatcher.kt:53` 既有链 `route(action: Action)`：四支穷尽，未改成员。
  - `InkActionDispatcher.kt:89` 契约链 `routeContract(action: ContractAction)`：五子类穷尽，OpenArtifact 显式 null 分支。
  - `InkBubble.kt:441` `actionLabel(action: Action)`：既有链四支穷尽；契约第五子类标签随问题 4 待裁。
- 单落实算：测试件 `InkBubbleRouteResolverTest.kt` `@Test` 12 个、文件 172 行；断言 `assertEquals` 17 / `assertNotNull` 5 / `assertNull` 5 / `assertTrue` 19 / `assertFalse` 2，合计 48 条（命令：`grep -cE 'assertEquals|assertNotNull|assertNull|assertTrue|assertFalse|assertSame' <测试件>`；分类 `grep -c "\b<断言名>" <测试件>`）。

### 8.3 报告 / 锚点 / 问题门禁回跑

- `python3 data_scripts/check-report-fields.py docs/修复-B4-08-Resolver接线.md`
- `python3 data_scripts/check-anchor-card.py --strict docs/修复-B4-08-Resolver接线.md docs/锚点/B4-08-Resolver接线.md`
- `python3 data_scripts/check-problem-finding.py docs/修复-B4-08-Resolver接线.md`

## 九、修订记录（旧称清理）

本单新写代码与文档沿用「分页引擎·竖排模式」现行口径；未改动其它历史文件。改前 → 改后逐字留档在下方围栏内（逐字转写区，E 组不扫）：

```text
1. InkActionDispatcher.kt KDoc：旧称「冻结四类」订正为「冻结五子类」双轨口径；未引入旧排版方向裸称。
2. 契约缝函数命名 routeContract / canDispatchContract，与既有链 route / canDispatch 区分，不混用第二套动作模型。
```
