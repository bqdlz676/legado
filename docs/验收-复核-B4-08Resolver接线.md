锚点卡：docs/锚点/验收-B4-08Resolver接线.md

# 验收-复核-B4-08 气泡 resolver 接线

## ANCHOR-ACK

1. 任务首行逐字：独立验收单 Q-420「B4-08 气泡 resolver 接线」，仓库 /home/user/Doubao/chats/38454122521460994/legado；只读验收，不改任何 app 代码 / 脚本，不做 git add/commit/push，不跑 gradle/kotlinc。被验提交 `d15eb07329885be0382db41eb8e2986f7dafeb22`（短 d15eb07；父提交 `8bd58423711168f532632203bed38a6f2c5f757f`，短 8bd5842）。被验主张清单取自写码方报告 `docs/修复-B4-08-Resolver接线.md`，其自述一律不作为证据，每条结论由验收方自己取数回源。
2. 绝对路径：
   - /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-B4-08Resolver接线.md（本报告）
   - /home/user/Doubao/chats/38454122521460994/legado/docs/锚点/验收-B4-08Resolver接线.md（本单锚点卡）
   - /home/user/Doubao/chats/38454122521460994/legado/docs/修复-B4-08-Resolver接线.md（被验报告，只读）
   - /home/user/Doubao/chats/38454122521460994/legado/docs/锚点/B4-08-Resolver接线.md（被验锚点卡，只读）
   - /home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/（被验主码面，只读）
3. 第一步命令：`git show d15eb07 --name-status` 与 `git show d15eb07 --numstat` 取改动面；冻结目录逐目录 `git diff --name-only d15eb07^ d15eb07 -- <path>` 回证空集；单点入口按全仓 `BubbleContent(` / `BubbleResolver` / 弹窗组件调用点逐环节 grep 回源；五子类定义行与映射行逐行对照；Missing 六类逐类验代码路径与测试覆盖；最后实跑机检脚本贴真实末行。

## 守则回执

- 指纹（当前磁盘 sha256 前 12 位，验收方自取 `hashlib.sha256(...).hexdigest()[:12]`）：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74。
- 逐字引用 1：「你只判、不改。」（docs/机制-验收代理必读.md:4）
- 逐字引用 2：「不采信写码方的自评」（docs/机制-验收代理必读.md:8）
- 逐字引用 3：「无证据的销项视为未验，判 FAIL」（docs/机制-验收代理必读.md:64）
- 适用条款：验收必读 §1 独立性（计数与签名自己重算，不采信自评）；§2 逐字对照（规格 / 契约 / 判据三类禁压缩）；§3 自己取数（命令 + 真实输出）；§9 编译面（新增文件 / import / 签名改动，结论最高「有条件通过（编译面待 CI）」）；§10 证据包（acceptance-evidence.py --rev 指向被验提交）；§11 --rev 提交验收口径；§8 陷阱 1 死装饰（零调用点反查）、陷阱 4 双源。本单红线：不改 app/** 与 data_scripts/**、不 git 写操作、不本地编译。

## 机器头

- 被验 commit：`d15eb07329885be0382db41eb8e2986f7dafeb22`（短 d15eb07；父 8bd5842）。验收时点本地 HEAD 即 d15eb07；`git -c core.quotepath=false status --porcelain` 工作区另有他人在飞未提交改动：`data_scripts/check-report-fields.py`、`data_scripts/check-symbol-existence.py`、`docs/派单台账.md`、`docs/派单队列.md` 修改，`docs/修复-g63影子声明.md`、`docs/修复-报告字段闸quotepath.md`、`docs/锚点/g63影子声明.md`、`docs/锚点/报告字段闸quotepath.md` 未跟踪。本验收未触碰、未代为提交，被验裁定面以 d15eb07 入库内容为准。
- 总结论：**有条件通过（编译面待 CI）**。静态七组判据：改动面边界 PASS；单点入口 PASS（静态面）；五子类路由缝 PASS（附死缝披露）；Missing 兜底部分 PASS（Resolver 侧四类有测试锁定，壳侧三态仅静态走查无测试）；双轨影响面已查清并披露；测试质量 PASS（断言计数失准一条，见 D-1）；机检与接线 PASS。编译 / 全量单测 / 真机行为以下一轮云端 Verify CI 为准，本验收不替 CI 背书。
- 判据计数：改动面 1/1 PASS；单点入口 1/1 PASS（静态）；五子类 1/1 PASS；Missing 兜底 4/6 有测试锁定、3/6 仅代码走查；双轨 1/1 已披露；测试质量 1/1 PASS（含计数偏差）；机检 5/5 PASS，g65/g66 未误判。
- 新缺陷数：阻断 0 条；D 级 2 条（D-1 计数失准、D-2 契约缝死装饰）；O 级观察项 4 条（O-1~O-4）。
- 编译面声明：本单未本地编译、未跑 gradle/kotlinc/assemble、未在 JVM 跑全量单测、未真机；真类型推断、重载解析、泛型约束、协程契约只有编译器能判。证据包三值建议「有条件通过（编译面待 CI）」，依据：import 改动、新增测试文件、签名 / 可见性改动、疑似漏 import 启发式命中。

## 一、销项 1：改动面边界 —— PASS（冻结面 diff 空集逐项回证）

判据原句（docs/拆分-B4阅读页小单.md:81）：白名单目录 `ink/InkBubble.kt` 调用侧、`ink/InkActionDispatcher.kt`；`help/bubble/**` 只读；四包零改动。放行条件（:85）：resolve 单点入口；五子类在位；无 Provider 直出气泡；Missing 兜底不崩不空屏；四包 diff 空集。

验收方自取数：

- 改动面清单（命令 `git show d15eb07 --name-status`）：M `app/src/main/java/io/legado/app/ui/book/read/ink/InkActionDispatcher.kt`；M `app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt`；A `app/src/test/java/io/legado/app/ui/book/read/ink/InkBubbleRouteResolverTest.kt`；A `docs/修复-B4-08-Resolver接线.md`；A `docs/锚点/B4-08-Resolver接线.md`。主码两件全部落在白名单 `ink/` 内；测试件与两件 docs 为该单产物口径内文件，无其他 app 路径。
- 增删行数（命令 `git show d15eb07 --numstat`）：Dispatcher 55+/5-；InkBubble 5+/2-；测试件 172+/0；被验报告 148+/0；被验锚点卡 52+/0。合计 432 插入 / 7 删除。
- 四包空集（命令 `git diff --name-only d15eb07^ d15eb07 -- app/src/main/java/io/legado/app/ui/book/read/page/provider app/src/main/java/io/legado/app/ui/book/read/page/entities app/src/main/java/io/legado/app/ui/book/read/page/api app/src/main/java/io/legado/app/ui/book/read/page/delegate`）：输出 0 行。
- `help/bubble/**` 只读面（命令 `git diff --name-only d15eb07^ d15eb07 -- app/src/main/java/io/legado/app/help/bubble`）：输出 0 行。
- ReadBook 只读（命令 `git diff --name-only d15eb07^ d15eb07 -- app/src/main/java/io/legado/app/model/ReadBook.kt`）：输出 0 行。
- 其余冻结目录（命令 `git diff --name-only d15eb07^ d15eb07 -- app/src/main/java/io/legado/app/ui/book/read/paragraph app/src/main/java/io/legado/app/ui/book/read/vertical app/src/main/java/io/legado/app/ui/card app/src/main/java/io/legado/app/ui/components app/src/main/java/io/legado/app/data/dao app/src/main/java/io/legado/app/data/entities app/src/main/java/io/legado/app/help/allusion app/src/main/assets`）：输出 0 行。`vertical/VerticalLayoutEngine.kt` 随 vertical 目录一并空集。
- 构建与资源冻结面（命令 `git diff --name-only d15eb07^ d15eb07 -- "*.gradle" "*.kts" gradle/ app/src/main/res/layout/`）：输出 0 行。
- 非 app/docs 路径：`git diff --name-only d15eb07^ d15eb07 | grep -v '^app/' | grep -v '^docs/'` 0 命中；无脚本 / 配置 / 资源改动。

## 二、销项 2：单点入口 —— PASS（静态面；ink 壳内 0 直造气泡，全仓构造点归类回源）

判据原句（docs/任务书-B4-阅读页.md:53 R9）：「气泡统一入口 `BubbleResolver.resolve(paragraph, pos, anchor)`；Provider 按优先级链首个非空返回」；逐字原文「统一入口只有一个：BubbleResolver.resolve(paragraph, pos, anchor) → BubbleContent，按下面顺序问各 Provider，第一个非空返回」。

验收方自定扫描口径（不沿用写码方给的行号）：

- 口径 A：全 main 仓扫 `BubbleContent(` 构造点，按包归类；口径 B：ink/ 全目录扫 `BubbleResolver` 全部引用；口径 C：扫气泡弹窗 / Sheet 组件的直接调用点；口径 D：查是否存在不经 resolver 直造气泡内容的第二路径。
- 口径 B 结果（命令 `grep -rn "BubbleResolver" app/src/main/java/io/legado/app/ui/book/read/ink/`）：`InkReadController.kt:282` `BubbleResolver.resolve(hit.paragraphText, hit.pos, ContextAnchor)`；`InkReadController.kt:336` `BubbleResolver.queryToken(token, ContextAnchor)`；`InkReadActivity.kt:172` `runCatching { BubbleResolver.preload(this) }`；`InkReadController.kt:185` `runCatching { BubbleResolver.clearCache() }`；余为 KDoc 指位（InkModels.kt:32/:35、InkReadController.kt:43/:48、InkReaderBook.kt:20、README.md:38）。产出气泡内容的调用点仅 :282 与 :336 两处；preload / clearCache 为生命周期接缝，不产出内容。
- 口径 A 结果（命令 `grep -rln "BubbleContent(" app/src/main/java/`）：构造点全部落在 `help/bubble/` 各 Provider（CharProvider、DateProvider、PersonProvider、PlaceProvider、PlaceProvider、WordProvider、MissingProvider、ArtifactBubbleProvider 适配层、BubbleResolver.missing 兜底）与 `help/artifact/` 契约 Provider（ArtifactFallback、RegistryEntityProvider）；`ui/` 壳层 0 处构造。ink/ 目录内 `BubbleContent(` 0 命中（命令 `grep -rn "BubbleContent(" app/src/main/java/io/legado/app/ui/book/read/ink/`）。
- 口径 C 结果：气泡组件 `InkBubble(` 全仓仅定义处 `InkBubble.kt:73` 与调用处 `InkReaderShell.kt:168` 两处；入参 `bubbleContent` 取自控制器 `controller.bubbleContent`（`InkReaderBook.kt:73`），控制器赋值点仅 `InkReadController.kt:277`（缓存命中）、`:286`（resolve 回填）、`:338`（候选切换回填）、`:187/:253/:290/:352/:433`（关闭置 null），无第二路直出。
- 口径 D 排查：旧壳 `page/ContentTextView.kt` 保留 `charBubble: PopupWindow?`（:72）、`showCharBubble(...)`（:648/:1135）、`showBubble(...)`（:574/:641/:659/:1327）旧解析桩；该桩走旧命中解析族，不构造 `BubbleContent`，属定界 P-01 保留侧，物理删除归 B4-09 剥桩批次（docs/任务书-B4-阅读页-引擎定界.md:514 §六二阶风险 1：resolver 链补齐前禁删旧桩）。本单改动面未触该桩，与白名单一致。
- 链路完整性结论：新壳点字 → `onCenterTap` → `requestBubble` → `BubbleResolver.resolve` → Provider 优先级链 → 回传 `BubbleContent` → 壳内组装 `InkBubble`，每个环节均回到真实代码行；静态面未发现绕过 resolver 直造气泡的弹窗路径。

## 三、销项 3：五子类路由缝 —— PASS（定义行 / 映射行逐行回源；穷尽性由 sealed 编译期保证；附死缝披露）

判据原句（docs/任务书-B4-阅读页.md:53 R9）：Action 五子类 `OpenMap/OpenPerson/OpenTimeline/OpenRelation/OpenArtifact` 冻结。

验收方逐类回源：

- 定义行（命令 `grep -n "data class Open" app/src/main/java/io/legado/app/help/bubble/contract/BubbleContractModels.kt`）：`:40` `OpenMap(val placeId: String)`；`:43` `OpenPerson(val name: String)`；`:46` `OpenTimeline(val year: Int)`；`:49` `OpenRelation(val name: String)`；`:52` `OpenArtifact(val artifactId: String, val type: String)`。契约头注 :31-32「五子类……此后不再增删」；sealed 类声明 :37。
- 映射行（`InkActionDispatcher.kt` `routeContract`，:89 起 `when (action)` 表达式）：`:90` `is ContractAction.OpenMap -> InkRoute.Map(placeName = action.placeId, year = anchor.getYear())`；`:95` `is ContractAction.OpenPerson -> InkRoute.Person(name = action.name, personId = null, anchorYear = anchor.getYear(), anchorLabel = anchor.eraLabel())`；`:102` `is ContractAction.OpenRelation -> InkRoute.Relation(name = action.name, anchorYear = anchor.getYear(), anchorLabel = anchor.eraLabel())`；`:108` `is ContractAction.OpenTimeline -> InkRoute.Timeline(year = action.year, label = null)`；`:114` `is ContractAction.OpenArtifact -> null`。`:118` `canDispatchContract` 按路由非空判定。
- 穷尽性 / 静默默认排查：两处 `when`（`route` :53、`routeContract` :89）均为 sealed 层级表达式，分支列表内无 `else` 兜底分支。Kotlin sealed `when` 表达式要求编译期穷尽，漏映射一个子类即编译报错（不是静默返回默认值）；OpenArtifact 为显式 null 分支，不是默认兜底。适配层 `ArtifactBubbleProvider.kt:91` `mapAction` 同为五子类穷尽 `when`，无 `else`。
- 死缝披露（对应 D-2）：`routeContract` / `canDispatchContract` 全仓调用方扫描（命令 `grep -rn "routeContract\|canDispatchContract" app/src/`）：主码面 0 处生产调用，仅测试件 `InkBubbleRouteResolverTest.kt:42/:47/:54/:65/:70/:77/:87/:92` 引用。契约动作产出方（RegistryEntityProvider、ArtifactFallback）经适配层 `toLegacy` 收敛为旧链 Action；新路由缝当前尚未被任何生产路径消费。

## 四、销项 4：Missing 兜底 —— 部分 PASS（Resolver 侧四类有测试锁定；壳侧三态仅静态走查；优先级互斥已核）

判据口径（docs/拆分-B4阅读页小单.md:83 产物）：Provider 空值落 Missing 兜底；C3 不允许空白态或崩溃。

六类逐类验（代码路径 + 测试覆盖分列）：

- 正文空（body 空白、detail 非空）：壳侧 `resolveBody`（`InkBubble.kt:453`）`content.body.ifBlank { content.detail }` 回退 detail；代码路径静态走查成立。测试覆盖：无（`resolveBody` 为私有纯函数，测试面 0 引用，见 O-4）。
- 详情空（body 非空、detail 空白）：同 :453 首段返回 body；代码路径成立。测试覆盖：无（O-4）。
- 双空（body / detail 均空白）：:453 末段 `.ifBlank { HalfSheetFallback.MISSING_TEXT }`；Missing 态分支 :451 `content.body.ifBlank { HalfSheetFallback.MISSING_TEXT }`，不出空气泡解释行。`MISSING_TEXT` 定义 `HalfSheetModel.kt:174`「无载」。代码路径成立。测试覆盖：无（O-4）。
- 未知符号：`BubbleResolver.resolve("魑魅魍魉", 0)` 落 `BubbleKind.MISSING`，body / source 非空（测试件 :134-138）；Resolver 异常兜落 `missing`（`BubbleResolver.kt:72-74`），`safe` 兜 Provider 异常（:133-134）。有测试锁定。
- 空段落：`resolve("", 0)` 落 Missing，token 回退「此处」（`BubbleResolver.kt:64` 空段早返 `missing("", anchor)`；:121 空 token 回退「此处」；测试件 :144-147）。有测试锁定。
- 越界字位：pos=99 夹取段落末位「秋」不崩（`BubbleResolver.kt:66` `pos.takeIf { it in paragraph.indices } ?: paragraph.indices.lastOrNull()`；测试件 :153-156）。有测试锁定。
- 附加边界：候选整词未收录 `queryToken` 返 null，控制器 `switchCandidate` 见 null 保留当前气泡不掉空气泡（`InkReadController.kt:337` `?: return`；测试件 :162）；纯空白段落 `"   "` 解析不抛异常落兜底（测试件 :168-170）。
- 优先级互斥：Resolver `dispatch`（`BubbleResolver.kt:103-116`）纪年 → 典故 → 人名 → 地名 → 名物/多字词仲裁（`SlotArbitrator.chooseArtifactOrWord`）→ 单字，首个非空返回；名物槽不重复查人物 / 地名 / 典故（`ArtifactSlots` 登记表隔离；`ArtifactBubbleProvider.kt:40` 防双轨）。各谓词按早返顺序互斥，任意输入归入唯一槽位。
- 结构完整性：Missing 气泡 `allowWebSearch / allowNotebook` 置真、actions / candidates 空表、source 非空（`MissingProvider.kt:27-39`；测试件 :119-128 逐项断言）。

## 五、销项 5：双轨风险 —— 影响面已查清（旧链生产在跑；契约链骨架待切壳；OpenArtifact 旧链 UI 不可见）

验收方按真实调用链逐层追（不只看类定义）：

- 生产在跑的旧链：`InkReadController.requestBubble`（:281-286）→ `BubbleResolver.resolve` → 默认装配 `DefaultBubbleResolver`（`BubbleResolver.kt:149-158`：DateProvider / WordProvider 典故 / PersonProvider / PlaceProvider / ArtifactBubbleProvider(EmptyEntityProvider) / WordProvider / CharProvider / MissingProvider）→ 旧 `help.bubble.BubbleContent` → `InkBubble` 渲染 → chips 经 `InkBubble.kt:280` `InkActionDispatcher.canDispatch(action)` 判定置灰，点击经 `InkReaderBook.kt:57` `InkActionDispatcher.dispatch` 拉起目标页。旧链 `help.bubble.Action` 四子类（`BubbleModels.kt:54-77`：OpenMap / OpenPerson / OpenTimeline / OpenRelation）服务该链。
- 契约链现状：`help.bubble.contract.BubbleContent / Action` 由 `help/artifact/` 契约 Provider 产出（RegistryEntityProvider、ArtifactFallback、EmptyEntityProvider）；`BubbleContractResolver` 全 main 仓 0 处实例化（命令 `grep -rn "BubbleContractResolver(" app/src/main/` 仅命中自身类声明 :19），消费方仅 `BubbleContractResolverTest.kt`。契约气泡经 `ArtifactBubbleProvider.toLegacy`（:76-89）适配为旧链气泡；契约动作经 `mapAction`（:91-97）映射，OpenMap / OpenPerson / OpenTimeline / OpenRelation 透传，OpenArtifact 置 null 丢弃。
- 双轨并存事实：旧 `BubbleModels.kt` 四子类与契约 `contract.Action` 五子类两套 sealed 层级并存，旧实现未退役；契约内容模型候选字段为 `List<String>`，旧链候选为 `List<BubbleCandidate>`，整体切壳时候选映射需在适配层收口（写码方报告 §4.2 二阶风险登记一致）。
- OpenArtifact 用户感知核查：默认装配名物槽注入的是 `EmptyEntityProvider`（`BubbleResolver.kt:49/:157`），兜底壳 `source = MISSING_SOURCE` 被适配层 :49 过滤下钻，当前生产面名物槽不出真实命中。即便后续注入 `RegistryEntityProvider`，其 `Action.OpenArtifact` 在适配层 `:96` 被 mapAction 丢弃，旧链壳内不渲染该 chip——即用户看到的是「名物详情入口缺失」，而不是一颗置灰按钮；写码方报告 §2.2 / 问题 1 所称「chip 置灰」对应的是新契约缝 `routeContract` 返 null 的判定口径，旧链 UI 实际收不到该动作。契约第五子类按钮标签「名物」未在 `actionLabel`（`InkBubble.kt:441-446` 仅标旧链四类）落地。
- 二阶影响推演：契约缝先在分发层就位，后续名物详情 Sheet 接线时只需补 `InkRoute.Artifact` 与 Intent 装配，不必再补 `when` 分支；但在契约壳整体切壳前，新增的 `routeContract` 缝不参与任何运行期决策（D-2）。

## 六、销项 6：测试质量 —— PASS（12 用例实算一致；断言实算 44 条与自报 48 不符，见 D-1；无反射 / 源码 contains / 恒真断言）

测试件：`app/src/test/java/io/legado/app/ui/book/read/ink/InkBubbleRouteResolverTest.kt`（172 行，命令 `wc -l` 自取）。

- 用例计数（命令 `grep -c "@Test" <测试件>`）：12 个 `@Test`，与自报一致。逐方法清单：契约 OpenMap 路由（:40）、契约 OpenPerson 路由（:53）、契约 OpenTimeline 路由（:64）、契约 OpenRelation 路由（:76）、契约 OpenArtifact 空路由置灰（:86）、旧链四类回归（:100）、生僻字 Missing（:116）、未知符号 Missing（:131）、空段落 Missing（:141）、越界字位 Missing（:150）、候选未收录 null（:159）、纯空白段落兜底（:167）。
- 断言计数（验收方逐行列行、剔除 import 行，命令 `grep -oE "assertEquals|assertNotNull|assertNull|assertTrue|assertFalse|assertSame" <测试件> | wc -l` 与分类 `grep -oE "\b<断言名>\(" | wc -l` 双口径对账）：实算断言语句 44 条——assertEquals 16、assertNotNull 5、assertNull 4、assertTrue 18、assertFalse 1、assertSame 0。写码方报告 §8.2 自报「assertEquals 17 / assertNotNull 5 / assertNull 5 / assertTrue 19 / assertFalse 2，合计 48」，验收方未复现 48：差额 4 恰为测试件顶部 4 行 import（assertEquals :9、assertFalse :10、assertNull :11、assertTrue :12）被计入断言计数；实算以 44 语句为准。
- 陷阱排查（命令 `grep -nE "::class.java|getDeclared|readText|File\(|contains\(\"|assertTrue\(true\)|assertTrue\(1 == 1\)|assertNotNull\(null\)" <测试件>`）：0 命中。无反射取成员、无整份读源文件 contains、无字面量恒真 / 恒假断言。
- 自测 vs 契约边界：被测对象为 `InkActionDispatcher.routeContract` 纯映射与 `BubbleResolver` 兜底行为；断言锁定契约字段透传（placeId / 主键 / 锚点年 / 标签回退 null）与 Missing 结构口径，非断言自身实现写法。两处 `assertTrue(route.anchorLabel?.contains("春秋") == true)`（:60/:82）为等值包裹写法，强度偏弱但非恒真：label 为 null 时 `null?.contains(...)` 为 null，`== true` 判 false，断言会真实失败。
- 覆盖缺口（不判红，列未验证项 / O-4）：壳侧 `resolveBody` 三态（正文空 / 详情空 / 双空）无单测；契约缝仅隔离单测，无生产闭环用例；Android 挂摘 / 真机观感不在纯 JVM 单测范围。

## 七、销项 7：机检与接线 —— PASS（五道末行验收方重跑；g65/g66 未被本单改动面误判）

### 7.1 五道末行自跑（命令 + 真实末行）

- `python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/book/read/ink/InkActionDispatcher.kt app/src/main/java/io/legado/app/ui/book/read/ink/InkBubble.kt app/src/test/java/io/legado/app/ui/book/read/ink/InkBubbleRouteResolverTest.kt`：`KOTLIN-LINT: PASS（3 个文件）`，退出码 0。
- `python3 data_scripts/check-compose-pitfalls.py app/src`：`COMPOSE-PITFALLS: PASS（2158 个 Kotlin 文件，A/B/C 三类形态 0 命中）`，退出码 0。
- `python3 data_scripts/check-design-tokens.py`：`DESIGN-TOKENS: PASS（机检 68 项全一致，spec-only 3 项登记在册）`，退出码 0。
- `python3 data_scripts/check-test-assert-truth.py --changed`：`TEST-ASSERT-TRUTH: PASS（扫描 0 个测试文件，REFLECT/RAWSOURCE/EMPTYASSERT 三类 0 命中）`，退出码 0。口径披露：`--changed` 按工作区 git 改动取文件；被验测试件已随 d15eb07 入库、工作区代码面干净，故扫描数为 0；验收方另对测试件人工复核三类陷阱 0 命中（见 §六）。全仓存量 RAWSOURCE 命中均在历史测试件（RuntimeMediaStabilityTest / SelectionIntervalGuardTest 等），不在本单改动面。
- `python3 data_scripts/check-gate-wiring.py`：`GATE-WIRING: PASS（60 个门禁均接线；历史孤儿基线 0 个）`，退出码 0。

### 7.2 g65 / g66 新增门禁复核

- g65 消费方反查 `python3 data_scripts/check-member-consumers.py`：`MEMBER-CONSUMERS: PASS（改动面 0 文件；删除 0 / 新增 0，残留 0；新增面 WARN 0 条）`，退出码 0。
- g66 实现成员缺 override `python3 data_scripts/check-member-override.py`：`MEMBER-OVERRIDE: PASS（改动面 0 个实现声明；缺 override 0；已配对 override 0；放行 ）`，退出码 0。
- 误判核查：本单未改 `ReaderShellHost` 或任何接口 / 协议；`routeContract` / `canDispatchContract` 是 `InkActionDispatcher` 对象上的普通新增方法，非接口成员，无 override 缺口；新增测试件为纯 JVM 用例，不实现宿主协议。两道门禁对本单改动面无误判。

### 7.3 证据包（acceptance-evidence.py --rev d15eb07，§10 / §11 口径）

- 命令：`python3 data_scripts/acceptance-evidence.py --rev d15eb07`（rev 逐字指向被验提交，未填 HEAD、未走工作区模式）。
- 改动面：Dispatcher（M，184 行）、InkBubble（M，454 行）、测试件（A，172 行）、被验报告 / 锚点卡两件。
- 编译面风险清单：Dispatcher:6 新增 import `io.legado.app.help.bubble.contract.Action`；跨包 import 8 条；签名变化 `routeContract(action: ContractAction, anchor: ContextAnchor)`、`canDispatchContract(action: ContractAction, anchor: ContextAnchor = ContextAnchor)`；测试件新增 12 条 import；启发式「疑似漏 import」列出 Missing / OpenArtifact / OpenMap 等——验收方人工复核为误报：这些符号经 `ContractAction.X` / `Action.X` 限定名引用或位于 sealed 分支 `is` 判定内，不需裸名 import。
- 三值建议：**有条件通过（编译面待 CI）**；依据：import 改动、新增文件、签名 / 可见性改动。

## 八、改坏推演（若本单判低放行，会怎样）

- 反例一：若单点入口复核只看 import 不追调用点，壳内可能存在第二套直造气泡路径而漏网。本验收全仓归类 `BubbleContent(` 构造点：ui 壳层 0 处，构造全部收敛在 resolver 链 Provider 与适配层；气泡组件调用点全仓仅一处。
- 反例二：若契约五子类漏映射某一子类而 `when` 写了 `else` 静默兜底，漏接动作会被默认分支吞掉不被发现。本验收确认两处路由 `when` 均无 `else`，sealed 穷尽性由编译期强制；OpenArtifact 是显式 null 分支。
- 反例三：若 Missing 兜底在 Provider 全空 / 异常时抛空屏或崩溃，新壳点字即白屏。本验收沿 Resolver 空段早返、越界夹取、runCatching 兜底、MissingProvider 完整结构四段回源，六类边界逐类验代码路径；Resolver 侧四类有测试锁定。
- 反例四：若双轨动作模型被误判为「契约链已接壳」，会高估名物 / 详情入口的可用性。本验收追真实调用链：生产在跑旧链，契约缝与契约 Resolver 当前均无生产消费方；OpenArtifact 动作在适配层即被丢弃，旧链 UI 不渲染 chip。
- 反例五：若把报告自报断言计数直接采信，测试质量账面会虚高 4 条。本验收逐行重算断言语句 44 条，定位差额来自 import 行误计。

## 九、新发现问题

1. D-1（计数失准，不影响运行行为）：被验报告 §8.2 自报测试「48 断言（17/5/5/19/2）」，验收方实算 44 条（16/5/4/18/1）；差额 4 为 4 行 import 被计入断言。状态：待裁（归 B4-08 文档订正 / 单测计数口径统一；代码与测试行为本身不受影响）。归属小单：B4-08 收口订正。
2. D-2（死装饰 / 死缝）：`InkActionDispatcher.routeContract` / `canDispatchContract` 全 main 仓 0 生产调用方，仅测试引用；契约动作在适配层 `ArtifactBubbleProvider.kt:96` 已被丢弃 OpenArtifact，新路由缝当前是未消费接缝。状态：已入队（归契约内容模型整体切壳批次；切壳时补生产调用闭环并复核缝契约）。归属小单：契约切壳接线 / 名物详情路由落地。
3. O-1（功能入口不可见）：OpenArtifact 名物详情在旧链 UI 不可见——适配层 :96 丢弃动作，壳内不渲染 chip（非置灰）；契约第五子类按钮标签「名物」未在 `actionLabel` 落地。状态：已入队（归名物详情 Sheet 接线单；与写码方报告问题 1 / 问题 4 同队列）。归属小单：名物详情 / OpenArtifact 路由落地。
4. O-2（极端 null 兜底未加固）：`InkReadController.requestBubble`（:281-286）`runCatching{...}.getOrNull()` 极端兜底：Resolver 内部 Missing 兜底正常不返 null，仅 MissingProvider 自身抛异常时落 null → `bubbleContent` 置 null 关闭气泡；本单白名单不含控制器，未改。状态：待裁（归控制器兜底加固单；与写码方报告问题 3 同队列）。归属小单：B4 阅读页控制器兜底加固。
5. O-3（旧详情半屏未并轨）：`ReaderSheetOverlay.kt` 仍消费旧桩 `ReaderDetailRequest` 四入口（人物 / 纪年 / 字词 / 地名），与 `BubbleContent` 契约链未并轨；点字详情 Sheet 数据源仍走旧桩通道。状态：已入队（归 B4-09 / B4-10 剥桩后详情装配批次；与写码方报告问题 5 同队列）。归属小单：详情半屏契约并轨。
6. O-4（壳侧空态映射无测试）：`resolveBody` 正文空 / 详情空 / 双空三态为私有纯函数，测试面 0 覆盖；Resolver 侧兜底已有测试，壳侧展示回退路径未被单测锁定。状态：待裁（归壳侧展示映射单测补全批）。归属小单：InkBubble 展示映射单测补全。

## 十、未验证项（显式，不替 CI 背书）

- 本单未本地编译、未跑全量单测、未真机运行；编译与单测绿红以下一轮云端 Verify CI 为准。真类型推断、重载解析、泛型约束、协程契约只有编译器能判；静态机检不替代 clean 编译。
- 契约五子类路由缝为纯 JVM 纯函数，单测离线覆盖；OpenArtifact 置灰 / 不可见口径在真机气泡 chip 上的观感未复验。
- 名物链（EntityProvider / RegistryEntityProvider）真实数据表落地后的命中口径未在本单验证；本单只复核既有适配与兜底路径。
- 双轨动作模型整体切壳的候选字段映射（`List<String>` ↔ `List<BubbleCandidate>`）未在本单实现，留待契约切壳批次。
- 符号门禁为静态启发式：不做类型推断、重载解析、泛型约束与协程契约校验；本件无 NODEF/ARITY/PACKAGE 命中不等于 clean 编译通过。
- 工作区在飞门禁脚本（`check-report-fields.py`、`check-symbol-existence.py`）与本单 app 面无代码关联；其 CI 绿红不在本验收范围。`check-line-anchors.py` 全仓恒 FAIL（DEADFILE 1539 / OUTOFRANGE 2591 / SUSPECT 4326 为存量老账），本件两份文档在该脚本输出中 0 条命中标记。

## 十一、机检记录（落盘后复跑回填）

- 命令 1：`python3 data_scripts/check-report-fields.py docs/验收-复核-B4-08Resolver接线.md`（落盘后实跑，末行见本节回填）。
- 命令 2：`python3 data_scripts/check-anchor-card.py --strict docs/验收-复核-B4-08Resolver接线.md docs/锚点/验收-B4-08Resolver接线.md`（落盘后实跑，末行见本节回填）。
- 命令 3：`python3 data_scripts/check-problem-finding.py docs/验收-复核-B4-08Resolver接线.md`（落盘后实跑，末行见本节回填）。
- 命令 4：`python3 data_scripts/check-onboarding.py`（落盘后实跑；他人在飞 g63 / g64 报告存量命中不计入本单）。
- 命令 5-9：§7.1 五道机检末行已在该节逐字粘贴，为验收方落盘前实跑所得；§7.2 g65/g66、§7.3 证据包同节回源。

### 11.1 落盘后复跑回填

- 命令 1 实测末行：`REPORT-FIELDS: PASS（1 份，0 份不合格）`，退出码 0。
- 命令 2 实测末行：`汇总: PASS=2 FAIL=0（共 2 份）`，退出码 0。WARN 3 条：主对象 Q-420 无对应锚点卡（验收报告降级提示，本卡按 B4-08 编号命名）；D-1、C3 次要提及无独立锚点卡；按门禁 R2 口径次要提及仅提示不判红。
- 命令 3 实测末行：`PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`，退出码 0。
- 命令 4 实测末行：`ONBOARD: PASS（3 份报告）`，退出码 0。聚合入选三份：本单报告（单文件复核 `check_report` 返回 `[]`）、他人在飞 `docs/修复-g63影子声明.md`、`docs/修复-报告字段闸quotepath.md`；后两份不在本单阻断面。
- 回填复核：命令 1-3 退出码均 0；本单报告回执块含总控现行指纹 c8fb35b71549、三条逐字回源命中引用（验收必读 :4 / :8 / :64）与「适用条款」行；锚点卡路径 `docs/锚点/验收-B4-08Resolver接线.md` 真实存在。
