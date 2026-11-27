锚点卡：docs/锚点/CI核验-InkBubbleRouteResolverTest修红.md

# 锚点卡 · InkBubbleRouteResolverTest 修红（Verify 45946032048 shard 3/4 两条单测失败）

> 对象：Verify run `45946032048`（HEAD `259482c2ad6d04317581054e4da9c21a22b5cd22`，failure）；失败 job `140331848090`（Unit tests shard 3/4）。回执报告 `docs/CI核验-InkBubbleRouteResolverTest修红.md`。

## 一、事实锚点（逐字，含 文件:行 / job id）

- 失败用例 1：`InkBubbleRouteResolverTest > 未知符号落 Missing 兜底，不强行释义 FAILED`，`org.junit.ComparisonFailure at InkBubbleRouteResolverTest.kt:137`（旧行号）；旧断言 token=`魑魅魍魉`，实际 Missing 兜底回守夹位首字 `魑`。
- 失败用例 2：`InkBubbleRouteResolverTest > 越界字位落 Missing 兜底，夹取末位不崩 FAILED`，`org.junit.ComparisonFailure at InkBubbleRouteResolverTest.kt:155`（旧行号）；`resolve("春秋", 99)` 经 safePos 夹到末位 1，词槽命中 WORD_TSV 词条 `春秋`（`DictSeedData.kt:533`），kind=`WORD`、token=`春秋`，不是 MISSING / 单字 `秋`。
- resolver 夹位语义：`BubbleResolver.kt:66` `pos.takeIf { it in paragraph.indices } ?: paragraph.indices.lastOrNull() ?: -1`；`BubbleResolver.kt:67` tokenFallback 取夹位字；`BubbleResolver.kt:72-74` dispatch 异常 / null 才落 Missing。
- 优先级链：`BubbleResolver.kt:105-115` date → allusion → person → place → artifact/word 仲裁 → char。
- 种子验证：`grep -rn "魑魅魍魉" app/src/main/java/` 零命中；`DictSeedData.kt:92/93` 收 `春`/`秋` 单字，`:533` 收 `春秋` 多字词；`AllusionSeed.kt` 79 条无 `春秋` 整词；人物 `PersonDirectory.kt` 27 名、地名 `PlaceSeedData.kt` 22 名、名物 `ArtifactSeedTable.kt` 均无此二词。
- 资产文本命中（单测链路不装载）：`app/src/main/assets/allusion.txt:3168` 收 `魑魅魍魉`；`ci_pinyin.bin` / `zh_ci.bin` 亦含。
- 改动面：仅 `app/src/test/java/io/legado/app/ui/book/read/ink/InkBubbleRouteResolverTest.kt`；12 个 `@Test` 不变；用例 1 断言 5→10，用例 2 断言 4→9；产品代码 / 种子数据零改动。
- 新用例 1 样例：非汉字符号 `※`（U+203B），机制上不进任何汉字槽；新用例 2：`resolve("春秋", 99)` 与 `resolve("春秋", 1)` 比对 kind / token 一致，锁「夹取末位不崩」，不锁槽位归属。
- 本地机检：`kotlin-syntax-lint.py` PASS；`check-test-assert-truth.py --changed` PASS（0 命中）；`check-test-fun-names.py` PASS；未跑 gradle（本机无编译链）。

## 二、判据（逐条可断言）

1. 两条红的根因是测试断言把「种子恰好未收录 / 整词未被词槽截胡」的数据事实当成代码事实，不是解析链回归；`龘` 用例与其余 11 条用例同 job PASS 可证链未塌。
2. 修红后断言只锁稳定不变量：不崩、不空白、结构完整、token 回守、越界夹取与原位解析一致；不断言易漂移的槽位归属。
3. 覆盖不降：用例数 12 不变，两条用例断言数净增；未删用例、未降断言、未改产品代码迁就测试。
4. CI 续核结论以 GitHub API 回源为准：Verify / Screenshot 各 job conclusion 回填报告第十节；未查到 `success` 不写「已绿」。

## 三、交接（发现但未改）

- `docs/修复-B4-08-Resolver接线.md:12,56`、`docs/锚点/B4-08-Resolver接线.md:5`、`docs/验收-复核-B4-08Resolver接线.md:82,101` 的旧口径描述与现行实现不符，归文档回订单。
- `:181-184` `queryToken("魑魅魍魉")` 未收录断言仍带种子漂移风险，归词库种子维护单。
