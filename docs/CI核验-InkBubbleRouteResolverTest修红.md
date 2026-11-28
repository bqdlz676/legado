锚点卡：docs/锚点/CI核验-InkBubbleRouteResolverTest修红.md

# CI 核验报告 · InkBubbleRouteResolverTest 修红（Verify run 45946032048，shard 3/4 两条单测失败）

## 判据锚

- 核验仓库：`bqdlz676/legado`（本地路径 `/home/user/Doubao/chats/38454122521460994/legado`，分支 `main`）。
- 红 run：Verify `45946032048`，HEAD `259482c2ad6d04317581054e4da9c21a22b5cd22`，run conclusion `failure`；失败 job `140331848090`（Unit tests shard 3/4）。
- 被改测试件：`app/src/test/java/io/legado/app/ui/book/read/ink/InkBubbleRouteResolverTest.kt`（本单唯一允许改动的代码文件）。
- 核验时间：2026-11-27（Asia/Shanghai）；CI 时间戳均为 UTC。
- 核验方式：GitHub REST API 回源 run / jobs / job 日志；种子命中用 grep 与 Python 抽取脚本逐字比对；本地只跑静态机检脚本，未跑 gradle / kotlinc（本机无编译链，CI 即编译面）。
- 凭据：token 仅从 `~/.git-credentials` 本地读取用于 API 调用，未写入本报告或任何文件。

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「不许跑 gradle / kotlinc / assemble。」（docs/机制-子代理必读.md:12）
- 逐字引用 3：「报告写清这条判据对应哪份原始事实的哪一行；锚点可回源。」（docs/机制-文档子代理必读.md:16）
- 适用条款：R57 一条缺陷一单（只动任务书点名的测试件）；文件域独占（工作区他人未提交改动不删不改不提交）；grep 回证每条改动贴真实输出；禁本地编译；测试断言必须是「改坏会红」的真断言；测试函数名反引号内无 JVM 非法字符。

## 一、CI 红原句（API 回源，逐字）

- run `45946032048`：`completed / failure`，workflow `Verify (unit tests)`。
- jobs 结论（`/actions/runs/45946032048/jobs`）：Discover `140330830840` `success`；Rhino eval tests `140330831075` `success`；Slow bench tests `140330831114` `success`；Baseline full unit tests `140330832594` `skipped`；shard 1/4 `140331848087` `success`；**shard 3/4 `140331848090` `failure`**；shard 2/4 `140331848093` `success`；shard 4/4 `140331848114` `success`；Aggregate `140333633760` `skipped`。
- job 日志 `140331848090` 内两条失败用例（日志行 2083-2103）：
  - `InkBubbleRouteResolverTest > 未知符号落 Missing 兜底，不强行释义 FAILED`，`org.junit.ComparisonFailure at InkBubbleRouteResolverTest.kt:137`（旧行号）。
  - `InkBubbleRouteResolverTest > 越界字位落 Missing 兜底，夹取末位不崩 FAILED`，`org.junit.ComparisonFailure at InkBubbleRouteResolverTest.kt:155`（旧行号）。
- job 末尾 error 原句（日志行 4435）：`##[error]shard 3/4 失败用例: InkBubbleRouteResolverTest > 未知符号落 Missing 兜底，不强行释义 FAILED;InkBubbleRouteResolverTest > 越界字位落 Missing 兜底，夹取末位不崩 FAILED;> Task :app:testAppDebugUnitTest FAILED;`
- 同文件其余用例在该 job 内 PASSED，包括 `未收录生僻字落 Missing 兜底，结构完整不崩不空屏`（`龘`）、`空段落落 Missing 兜底，token 回退此处`、`候选整词未收录返回 null，调用方保留当前气泡`、`纯空白段落解析不抛异常并落兜底`；不是全链塌，是这两条断言的预期与真实链不符。

## 二、源码优先链复核（逐字读 `help/bubble/BubbleResolver.kt`）

- `resolve`（`BubbleResolver.kt:63-78`）：空段落直接 `missing("", anchor)`；否则 `safePos = pos.takeIf { it in paragraph.indices } ?: paragraph.indices.lastOrNull() ?: -1`（行 66，越界夹到末位下标）；`tokenFallback = paragraph.getOrNull(safePos)?.toString().orEmpty()`（行 67）；`dispatch` 全程 `runCatching`，异常或返回 null 才落 `missing(tokenFallback, anchor)`（行 72-74）。
- `dispatch` 优先级链（`BubbleResolver.kt:103-116`）：date → allusion → person → place → artifact/word 仲裁（`SlotArbitrator.chooseArtifactOrWord`）→ char；全空返回 null，交 Missing 兜底。
- `queryToken`（`BubbleResolver.kt:81-94`）：date → allusion → person → place → artifact/word 仲裁 → char；整词未收录返回 null。
- 夹位语义：越界 pos 只改「从哪个字起窗」，不改变后续链的槽位判断；末位所在整词若被词槽命中，出的是词气泡，不是单字气泡，更不是必然 Missing。

## 三、种子数据验证结论（命令 + 命中位置，逐字 grep / 脚本抽取）

测试 JVM 环境只读纯内存种子：典故在 `help/allusion/AllusionSeed.kt`，单字 / 多字词在 `help/dict/DictSeedData.kt`，人物在 `help/person/PersonDirectory.kt`，地名在 `help/place/PlaceSeedData.kt`，名物在 `help/artifact/seed/ArtifactSeedTable.kt`；`app/src/main/assets/` 下的 txt / bin 是后续资产解析层数据，单测链路当前不装载。

- 命令 1：`grep -rl "魑魅魍魉" app/src/main/assets/`
  - 命中：`app/src/main/assets/allusion.txt:3168`（`魑魅魍魉	chī mèi wǎng liǎng	…`）、`app/src/main/assets/ci_pinyin.bin`、`app/src/main/assets/zh_ci.bin`。
  - 结论：资产文本内有该四字词，但不进 JVM 单测的内存索引；不能据此断言单测链路会命中。
- 命令 2：`grep -rn "魑魅魍魉" app/src/main/java/` → 零命中（典故 / 字库 / 人物 / 地名 / 名物种子均无）。
- 命令 3：`grep -n "魑\|魅\|魍\|魉" app/src/main/java/io/legado/app/help/dict/DictSeedData.kt` → 零命中（四字及其单字均不在 CHAR_TSV / WORD_TSV）。
- 命令 4：Python 抽取各种子词形后比对（脚本一次跑完）：
  - 典故 79 条 word 字段：无 `魑魅魍魉`、无 `春秋` 整词；`春秋` 只出现在 `source = classic("《…春秋…》"…)` 出处串里（如 `AllusionSeed.kt:210,223,236,530,540,551,555,672…`），不是词条。
  - 人物 27 个本名 + 别名列表：无 `春秋`、无 `魑魅魍魉`。
  - 地名 22 个 canonicalName + 别名列表：无 `春秋`、无 `魑魅魍魉`。
  - 名物种子 id/name/aliases：无 `春秋` 实体、无 `魑 / 魅 / 魍 / 魉`；`《吴越春秋》` 只在干将 / 莫邪条目的出处串内（`ArtifactSeedTable.kt:143,151,152`）。
- `春秋` 实际命中位置（内存种子内）：
  - `DictSeedData.kt:533`：`春秋	四季；年纪；编年体史书。	classic`（WORD_TSV 多字词条）。
  - `DictSeedData.kt:92`：`春	chun	日	9	…`；`DictSeedData.kt:93`：`秋	qiu	禾	9	…`（CHAR_TSV 单字条）。
- 装配口径：默认 resolver 的名物槽是 `ArtifactBubbleProvider(EmptyEntityProvider())`（`BubbleResolver.kt:157`），空实现只回兜底壳并被 `MISSING_SOURCE` 门禁挡成 null（`ArtifactBubbleProvider.kt:49`），名物种子表当前不进解析链。

## 四、两条样例的实际命中推演（复核后结论，不照抄线索）

- `resolve("魑魅魍魉", 0)`：safePos=0，tokenFallback=`魑`。date 正则不命中；allusion 2~4 字窗在 79 条典故种子内无整词；person 清洗后无档案；place 无地名；artifact 空实现；word 多字词窗（4→2 字）在 WORD_TSV 内无命中；char 槽 `魑` 不在 CHAR_TSV。dispatch 返回 null → `missing("魑")`：kind=`MISSING`，token=`魑`（夹位首字），不是四字整串。旧用例 第 136 行 的 kind 断言能过，第 137 行 的 token 断言期望 `魑魅魍魉`、实际 `魑`，`ComparisonFailure` 红。
- `resolve("春秋", 99)`：safePos 夹到末位下标 1，tokenFallback=`秋`。date 无纪年 / 干支 / 朔望形状；allusion 无 `春秋` 词条；person / place 无档案；artifact 空实现；word 槽 `ChineseDict.wordAt("春秋", 1)` 命中 WORD_TSV:533 的 `春秋`，kind=`WORD`，token=`春秋`（整词，长词优先于单字槽）。旧用例 第 155 行 期望 kind=`MISSING`、实际 `WORD`，`ComparisonFailure` 红；第 156 行 期望 token=`秋`、实际 `春秋`，同样不成立。
- 根因定性：这两条断言把「本地词库恰好未收录 / 整词未被词槽截胡」这个会随种子数据漂移的事实，当成了代码层固定事实；代码层真正稳定的不变量只有「不崩、不空白、结构完整」，以及「越界先夹末位再按正常链解析」。

## 五、改动 diff 要点（before / after + grep 回证）

改动文件仅一件：`app/src/test/java/io/legado/app/ui/book/read/ink/InkBubbleRouteResolverTest.kt`。

- 类 KDoc（旧 第 24 - 25 行 → 新 第 24 - 27 行）：旧述「Provider 全空 / 未知符号 / 空段落 / 越界字位一律落 Missing 兜底」；改为「全空 / 未知符号 / 空段落落 Missing；越界字位先夹取末位下标，再按正常优先级链解析，末位整词被词槽命中时出词气泡，未收录才回落单字 / Missing」。
- 用例 1（旧 第 132 - 140 行 → 新 第 134 - 150 行）：
  - before：`resolve("魑魅魍魉", 0)`，断言 kind=`MISSING`、token=`魑魅魍魉`、body / source 非空。
  - after：样例换非汉字符号 `※`（U+203B，不在 `isHan` 区间 0x4E00..0x9FFF / 0x3400..0x4DBF，也不是拉丁 / 数字；纪年正则、称谓清洗、词槽与单字槽入口均先判汉字或清洗成空串，机制上不可能命中种子，不依赖「某词恰好未收录」）。断言：非 null；kind=`MISSING`；token 回守 `※`；body / detail / source 非空；`allowWebSearch` / `allowNotebook` 为 true；actions / candidates 为空。
- 用例 2（旧 第 151 - 158 行 → 新 第 161 - 178 行，方法名改为 `越界字位夹取末位不崩，结果与末位原位解析一致`）：
  - before：`resolve("春秋", 99)`，断言 kind=`MISSING`、token=`秋`、body 非空。
  - after：并行取 `resolve(paragraph, 99)` 与 `resolve(paragraph, paragraph.lastIndex)`；断言两者均非 null，kind 相同、token 相同（夹取后走的槽位与原位点末位完全一致）；token 非空且包含末位字；body / detail / source 非空。槽位归属不锁定：词槽命中出整词、未收录回落单字或 Missing，两种形态都允许。
- grep 回证（新文件真实行号）：
  - `grep -n "※" InkBubbleRouteResolverTest.kt` → 第 136 行 注释、第 139 行 `resolve("※", 0)`、第 142 行 `assertEquals("※", bubble.token)`。
  - `grep -n "clamped\|atLast" InkBubbleRouteResolverTest.kt` → 第 167 行 `val clamped = …(paragraph, 99)`、第 168 行 `val atLast = …(paragraph, paragraph.lastIndex)`、第 169 - 172 行 两条 assertNotNull 与两条 assertEquals。
  - 旧符号清零：`grep -n "魑魅魍魉\|越界字位落 Missing" InkBubbleRouteResolverTest.kt` 在本测试件内零命中（`魑魅魍魉` 仅保留在 第 182 行 的 `queryToken` 未收录用例，属任务书禁止改动的其余用例，未动）。

## 六、覆盖不降说明

- 测试方法数：改前 `grep -c "@Test"` = 12，改后 = 12；未删用例、未合并用例。
- 断言数：用例 1 旧 5 条 → 新 10 条；用例 2 旧 4 条 → 新 9 条；两条只增不减。
- 保留的三不变量：不抛异常（resolve 本身 runCatching 兜底，新用例两次调用均未包 try/catch，抛异常即红）；不空白（body / detail / source 三条非空断言，两件用例都在）；结构完整（token 回守、actions / candidates 空、兜底标志位在位）。
- 未被削弱的原有面：`龘` 未收录生僻字用例、空段落、纯空白、候选整词未收录 null、契约五子类路由、旧链四类回归，全部原样保留。

## 七、本地机检结果（未跑编译）

- `python3 data_scripts/kotlin-syntax-lint.py app/src/test/java/…/InkBubbleRouteResolverTest.kt` → `KOTLIN-LINT: PASS（1 个文件）`，rc=0。
- `python3 data_scripts/check-test-assert-truth.py --changed` → `TEST-ASSERT-TRUTH: PASS（扫描 1 个测试文件，REFLECT/RAWSOURCE/EMPTYASSERT 三类 0 命中）`，rc=0。
- `python3 data_scripts/check-test-assert-truth.py`（全测试根）→ 本文件零命中；其余文件的存量 RAWSOURCE 命中是历史债务，不在本单改动面。
- `python3 data_scripts/check-test-fun-names.py <本文件>` → `TEST-FUN-NAME: PASS`，rc=0；新反引号方法名不含 `. : [ ] / ; < >`。
- 未验证项：本机无 gradle / kotlinc 编译链，单测实际红绿以云端 Verify 为准；本报告不含本地跑测试结论。

## 八、边界与二阶三阶影响

- 边界 1：`※` 若未来被某 Provider 作为专名收录——现各槽入口先判汉字或清洗成空串，非汉字符号不进索引；该兜底判定是机制口径，不随词库增长漂移。
- 边界 2：越界夹取一致性断言依赖 `resolve` 的缓存键（`paragraph.hashCode():length:pos:anchor`，`BubbleResolver.kt:137`）按 safePos 归一；两次调用 safePos 相同，缓存命中同一条目，kind / token 必然一致。
- 边界 3：`token.contains(末位字)` 对词槽整词、单字槽、Missing 回守三种形态都成立（候选窗覆盖点击位，整词必含末位字）；若未来有 Provider 返回不含点击字的 token，此断言会红并暴露口径偏差，属有意保留的防漂移断言。
- 二阶影响：只改测试件，产品代码零改动；不改 Provider 优先级、不改种子数据、不改气泡模型字段。
- 认知成本：新用例 2 把「夹取不崩」与「槽位归属」两个断言面拆开，读测试的人能直接看出哪条是代码保证、哪条是种子现状；未新增抽象或新类型。

## 九、待裁项与新发现问题（状态词 + 归属）

- 【观察项 · 归属：词库种子维护单】`第 181 - 184 行` `候选整词未收录返回 null` 仍用 `魑魅魍魉` 断言 queryToken 返回 null；当前内存种子确实未收录，用例 PASS，但同旧用例一样带「恰好未收录」的漂移风险。本单按约束未动该用例；后续可换成非汉字串或多源交叉口径。
- 【文档偏差 · 归属：B4-08 文档回订单】`docs/修复-B4-08-Resolver接线.md:12,56`、`docs/锚点/B4-08-Resolver接线.md:5`、`docs/验收-复核-B4-08Resolver接线.md:82,101` 仍写「越界字位落 Missing、token 为末字秋」「未知符号落 Missing」的旧口径，与现行实现（词槽可截胡整词）不符；历史报告留档不改，后续文档回订单一并修正。
- 【存量债务 · 归属：测试治理单】全量 `check-test-assert-truth.py` 扫描出的其余测试件 RAWSOURCE / REFLECT 命中为历史债务，本单不扩大战场；清单以脚本输出为准。
- 【待续核 · 归属：本单 CI 回查节】push 后新 run 的 Verify / Screenshot 各 job conclusion 以 GitHub API 回源为准，结论回填到下节；API 未查到 `conclusion=success` 之前，本报告不写「已绿」。

## 十、CI 续核结论（push 后 API 回填）

- 待回填：新 commit sha、Verify run id、Screenshot run id、各 job success/failure。
