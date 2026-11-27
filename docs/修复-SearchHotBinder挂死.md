锚点卡：docs/锚点/Q277-挂死收口.md

# 修复 · SearchHotBinderTest 挂死收口（Q-277）

## 〇、基线与范围

- 仓库绝对路径 `/home/user/Doubao/chats/38454122521460994/legado`；开工 HEAD `49cc65954eb1cdbbd3e9631315ae03c989821eac`（`git log --oneline -3` 首行 `49cc659 docs(closeout): Q-270 收口单同步回填 HEAD 053d435 CI 真值与链尾`）。
- 本单边界：改测试文件 `app/src/test/java/io/legado/app/ui/main/search/SearchHotBinderTest.kt` 与 `app/build.gradle`（仅 `testLogging` 事件清单，不涉分类接线）；未改 `app/src/main/**` 生产逻辑，未改 `.github/workflows/verify.yml`，未改任何 `data_scripts/check-*.py`；无 git 写操作；沙箱无 JDK，未跑 `./gradlew` / `kotlinc` / 本地单测。
- 证据来源：两轮独立复现——Verify run `45825892031`（head `ff9c20c`）shard 2/4 job `139932664526`；Verify run `45833477069`（head `49cc659`）shard 3/4 job `139961108873`；既有核验 `docs/CI核验-ff9c20c-e8ce4a8.md` §5.1–5.2；挂死候选定位 `app/src/test/java/io/legado/app/ui/main/search/SearchHotBinderTest.kt:148-176`。

## 一、CI 事实（逐字回源，非推断）

- job `139932664526`（Unit tests shard 2/4）：`13:11:30Z → 13:56:44Z`，共 2714s（45m14s）被取消；570 条 `STARTED`、1 条 `FAILED`、**零 XML 产出**（`docs/CI核验-ff9c20c-e8ce4a8.md:30`、`:61`）。
- 最后一条 STARTED（tee 日志 `/tmp/q266/s2.log:1676` 逐字）：
  `2026-11-26T13:16:32.6645095Z SearchHotBinderTest > removing last history word overlapping seed keeps fallback source marker STARTED`；其后静默 2410.2s（40m10s）直到 `13:56:42.8708798Z ##[error]The operation was canceled.`（`/tmp/q266/s2.log:1677-1678`）。
- 同轮对照：shard 1/3/4 全绿，276s / 272s / 348s；bench 9 类 23 条、Rhino 18 类 214 条全绿（`docs/CI核验-ff9c20c-e8ce4a8.md:32-33`、`:42`、`:48`）。
- 同轮唯一失败用例：`ImportSourceGroupTest > displayNameFoldsWhitespaceAndTruncates FAILED`（`/tmp/q266/s2.log:1627-1628`），断言红发生在挂死之前，JUnit 单断言失败不中断 worker，不是挂死因（`docs/CI核验-ff9c20c-e8ce4a8.md:82`）。
- 日志中本类仅此一条 STARTED：grep `SearchHotBinderTest` 全份 shard 2 日志只命中 `:1676` 一行——JUnit4 方法排序把该用例排成本类首条，其余 5 条未起跑（日志无 PASSED 事件，不能反证它们通过）。
- **第二轮独立复现**（Verify `45833477069`，head `49cc659`，shard 3/4 job `139961108873`，tee 日志 `/tmp/shard3.log`）：任务 `14:01:58.8946246Z` 起，`14:17:13Z` 左右 15 分钟超时取消；658 条 `STARTED`、0 条 `FAILED`、零 XML。末段两条逐字：
  - `/tmp/shard3.log:1851`：`2026-11-26T14:06:14.9598576Z SearchHotBinderTest > removing last history word overlapping seed keeps fallback source marker STARTED`
  - `/tmp/shard3.log:1852`：`2026-11-26T14:17:10.9639293Z ##[error]The operation was canceled.`
  - 两条之间静默 656.0s（`14:06:14.9598576 → 14:17:10.9639293`，机器算得 656.004071s），期间零行输出；取消时收尾孤儿进程终止行 4 个 java + 1 个 tee（`/tmp/shard3.log:1868-1872`）。
- 第二轮同轮对照（父代理转达本轮 Verify 汇总）：shard 1/2/4 全 success，bench 与 rhino 全 success。清单净化把本类从 shard 2 挪到 shard 3，**类与用例未变**——挂死点不是分片划分问题，是这一条用例真的不返回；两轮同用例、同静默形态，排除偶然抖动。

## 二、兜底决策：选 a（测试内收口 + runner 层硬超时），未走 b 隔离

### 2.1 判定分支

- 任务书二选一：a) 用例本身用了无超时等待 → 测试内加硬超时；b) 根因在生产代码 → 不改生产，整类打 `@Category` 移入独立 8 分钟 job。
- 静态定位结论（§三）：泄漏点在**测试侧**——用例把两路无限流收集协程拉进 `runBlocking` 注入作用域，收尾前不显式取消；生产 `SearchHotBinder.close()` 在位，VM 生产路径 `InkSearchViewModel.detachOverlay()` 也会调 `close()`（`app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:92-93`）。根因不是生产死循环 / StateFlow 设计缺陷 / Rhino 未 release，故落 a 分支，不动生产、不新增隔离 job。
- 复现强度：两轮 Verify（`45825892031` shard 2、`45833477069` shard 3）同用例、同静默形态，已独立复现两次，非分片划分偶然。b 方案（整类打 `@Category` 移入独立 8 分钟 job）留作 a 验证不通过时的第二选择；本单不碰 `verify.yml`，避免与其它在途单冲突。

### 2.2 落地的两层护栏（测试收口层；取证日志改动见 §4.2）

1. **结构化收口（消除泄漏本体）**：6 个用例全部改为 `binder.start()` 后 `try { …断言… } finally { binder.close() }`；第 6 例重建出的第二个 binder 同样在 `finally` 取消。`close()` 逐路取消收集 Job（`app/src/main/java/io/legado/app/ui/main/search/SearchHotBinder.kt:89-92`），子协程在 StateFlow 收集挂起点响应取消并结束，`runBlocking` 收尾 join 才能完成。
2. **runner 层硬超时**：6 个方法标注 `@Test(timeout = 5_000)`；既有函数级 `withTimeout(5_000)` 全部保留不动。

### 2.3 超时数值与依据（为什么是 5_000ms，不是随便写 5 秒）

- **数值**：函数级 `withTimeout(5_000)` 与 runner 级 `@Test(timeout = 5_000)` 同为 5000ms；两层同值，外层看门不会先于内层护栏抢走超时判定，避免两套时钟竞争报出两种异常。
- **为什么不需要更大**：本模型是 `Dispatchers.Unconfined` 单线程 + 内存内 `MutableStateFlow` 同步回放，链路只有 List 变换与 CAS（§3.1），无 IO、无 Rhino、无网络；稳态收敛是微秒级。5000ms 相对微秒级留有约三个数量级余量，覆盖 CI 冷启动、worker GC 暂停与调度抖动；再大（如 15s）只会让真挂死用例多烧分片分钟数，不提升有效覆盖。
- **为什么不能更小**：仓内既有同型口径即 5000ms——`ChapterDownloadStateTest`、`HttpTtsDownloadTest` 的 `withTimeout(5_000)`，`RhinoScriptErrorContextTest.kt:17`、`:38` 的 `@Test(timeout = 5_000)`；压到 1s 以下会在慢 runner 上把健康用例误判超时，与既有 632 个测试类的口径脱节。
- **层级分工**：内层 `withTimeout` 管体内 `state.first { 谓词 }` 等待；外层 `@Test(timeout)` 管整法（含收尾 join 异常路径）；结构化 `finally close()` 才是消除泄漏本体的主修复，超时只是兜底护栏，不替代收口。

### 2.4 如果不做这一步，下一轮 CI 会怎样

- 任一分片再次跑到本类首条用例：两路收集协程泄漏 → `runBlocking` 收尾 join 永久阻塞测试线程；函数级 `withTimeout` 管不到收尾子协程 join（§三 点2），5s 超时不会触发。
- 现行分片 job 超时 `timeout-minutes: 15`（`.github/workflows/verify.yml:185`）：15 分钟后 job 被取消、零 XML；`test-aggregate` 因 `needs` 含失败/取消分片而 `skipped`，基线 3185 对账不执行（`.github/workflows/verify.yml:595`、`:690-691`）；整轮 Verify `cancelled`，已 STARTED 的数百条用例通过/失败状态全部丢失，证据链只剩 tee 里的 STARTED 序列——与本轮 `45825892031` 同型复现。

## 三、根因静态定位（调用链逐字原文 + 不返回点）

### 3.0 挂死点定位（两轮日志口径 + 静态停点）

- 日志口径：两轮的最后一条事件都是同一用例 STARTED，其后零终态（`/tmp/q266/s2.log:1676`、`/tmp/shard3.log:1851`）；日志无 PASSED/FAILED，无法从日志区分「断言中途挂起」还是「断言结束后收尾挂住」。
- 用例体最后两条原文（修复前 `SearchHotBinderTest.kt`）：

```kotlin
174	        // 兜底词表仍含「论语」字样，但来源标记必须是 SEED 兜底，不能记成 HISTORY / USAGE。
175	        assertTrue("种子兜底词表应仍收录论语", after.hot.contains("论语"))
176	    }
```

- 静态停点判定：体内两个等待点 `:164`、`:171` 均已包 `withTimeout(5_000)`，链路无不可取消段（§3.3 点3–点5 已排除）；永久停止点在 `:176` 闭合 `runBlocking` lambda 之后的**收尾子协程 join**（点2）。泄漏子协程起点在生产 binder 两路 `launch`：`SearchHotBinder.kt:65`（时间序收集）、`:74`（频次序收集）。该判定为源码级静态结论，jstack 栈帧未取到（§八）。

### 3.1 被测生产链（逐字，行号锚定现行工作树）

`app/src/main/java/io/legado/app/ui/main/search/SearchHotBinder.kt:64-83`（`start()` 两路收集）：

```kotlin
64	    fun start() {
65	        jobs += scope.launch(dispatcher) {
66	            timeFlow
67	                .catch { onError(it) }
68	                .collect { keywords ->
69	                    val history = SearchHistory.clean(keywords.map { it.word })
70	                    snapshot.updateAndGet { old -> old.copy(history = history) }
71	                    publishHot { it.copy(history = history) }
72	                }
73	        }
74	        jobs += scope.launch(dispatcher) {
75	            usageFlow
76	                .catch { onError(it) }
77	                .collect { keywords ->
78	                    val usage = keywords.map { it.word }
79	                    snapshot.updateAndGet { old -> old.copy(usage = usage) }
80	                    publishHot { it }
81	                }
82	        }
83	    }
```

`SearchHotBinder.kt:89-104`（`close()` 与 `publishHot`）：

```kotlin
89	    fun close() {
90	        jobs.forEach { it.cancel() }
91	        jobs.clear()
92	    }
93	
98	    private fun publishHot(statePatch: (SearchUiState) -> SearchUiState) {
99	        val current = snapshot.get()
100	        val hot = SearchHotResolver.resolve(current.usage, current.history)
101	        uiState.update { state ->
102	            statePatch(state).copy(hot = hot.keys, hotSource = hot.source)
103	        }
104	    }
```

解析链：`SearchHotResolver.resolve`（`SearchHotResolver.kt:60-89`，USAGE→HISTORY→SEED→EMPTY 四档回落，纯 List 变换）→ `SearchHistory.clean` / `SearchHistory.hot`（`SearchHistory.kt:19-25`、`:48-54`，去空白/去重/截断）→ `SearchMatcher.normalize`（`SearchMatcher.kt:15-16`，trim + 空白压缩）。全程无 `while` 死循环、无锁等待、无 Rhino 求值、无网络/IO。

### 3.2 挂死用例原文（修复前，`SearchHotBinderTest.kt:147-176` 逐字）

```kotlin
147	    @Test
148	    fun `removing last history word overlapping seed keeps fallback source marker`() = runBlocking {
149	        val state = MutableStateFlow(SearchUiState())
150	        // 情形二：被删词与种子兜底重合。「论语」属 SEED_KEYS，先以历史档出现；
151	        // 删空后两路皆空，热门行回落到种子兜底，档位必须记为 SEED，不冒充历史 / 高频。
152	        val timeFlow = MutableStateFlow(listOf(keyword("论语")))
153	        val usageFlow = MutableStateFlow<List<SearchKeyword>>(emptyList())
154	
155	        val binder = SearchHotBinder(
156	            uiState = state,
157	            timeFlow = timeFlow,
158	            usageFlow = usageFlow,
159	            scope = this,
160	            dispatcher = Dispatchers.Unconfined,
161	        )
162	        binder.start()
163	
164	        val before = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.HISTORY } }
165	        assertEquals(SearchHotSource.HISTORY, before.hotSource)
166	        assertEquals(listOf("论语"), before.hot)
167	
168	        // 等价于 DAO delete(SearchKeyword("论语")) 后历史流重发空快照，频次路本就为空。
169	        timeFlow.value = emptyList()
170	
171	        val after = withTimeout(5_000) { state.first { it.hotSource == SearchHotSource.SEED } }
172	        assertEquals(SearchHotSource.SEED, after.hotSource)
173	        assertEquals(SearchHotResolver.SEED_KEYS.take(SearchTokens.HOT_LIMIT), after.hot)
174	        // 兜底词表仍含「论语」字样，但来源标记必须是 SEED 兜底，不能记成 HISTORY / USAGE。
175	        assertTrue("种子兜底词表应仍收录论语", after.hot.contains("论语"))
176	    }
```

### 3.3 不返回点逐条标注

| 编号 | 位置 | 可能永不返回的点 | 为什么这里会停（一句话论证） | 下一步怎么证实（可执行判据） |
| --- | --- | --- | --- | --- |
| 点1 | `SearchHotBinder.kt:65` / `:74` 两路 `launch` 内 `MutableStateFlow.collect`（`:68` / `:77`） | 无限流收集协程永不完结 | `MutableStateFlow` 是热流，`collect` 不收到完成信号；两个 Job 作为 `runBlocking` 根协程的子协程一直处于 active。 | 用例体末尾打印 `coroutineContext[Job]!!.children?.count { it.isActive }`；修复前应得 2，`finally { close() }` 后归 0。 |
| 点2 | 用例体结束后的 `runBlocking` 收尾 join（`runBlocking { … }` 对应 `BlockingCoroutine.joinBlocking()`） | 根协程等待全部子协程结束才返回 | 结构化并发：根协程体返回后进入 `COMPLETING_WAITING_CHILDREN`，子协程 点1 不结束根就不 completed；`joinBlocking()` 循环 park 直到 `isCompleted`（kotlinx-coroutines 1.11.0 `Builders.kt:48-59`、`AbstractCoroutine.kt:98-102`）。既有 `withTimeout`（:164 / :171）只包住体内 `first` 等待，作用域覆盖不到这个收尾 join。 | jstack 测试 worker：测试线程应落在 `parkNanos` / `joinBlocking` 栈帧，另两个收集协程挂在 StateFlow 收集挂起点；或在收尾处加 5s `withTimeout` 包 `currentCoroutineContext()[Job]!!.join()` 观察是否抛 `TimeoutCancellationException`。 | （已废·W-436：行内 Builders / AbstractCoroutine 为 kotlinx-coroutines 库内部件，非本仓源码锚点；挂死收口以 docs/修复-同类裸等待收口.md 与仓内存活测试件为准。）
| 点3 | `:164` / `:171` `state.first { 谓词 }` 挂起点 | 谓词永不命中时的流等待 | 已被 `withTimeout(5_000)` 包住；StateFlow 收集挂起点响应协程取消，超时应在 5s 内抛 `TimeoutCancellationException` 转 FAILED。两轮静默 2410s / 656s 均无 FAILED，反证主停点不在此。 | 故意把谓词改成永不命中（如 `it.hotSource == EMPTY`），本地/CI 应在 5s 内见到超时异常；若 5s 不红则 点3 之外另有不可取消段，需重查。 |
| 点4 | `snapshot.updateAndGet`（`:70` / `:79`）、`uiState.update`（`:101`） | CAS 自旋循环 | 均为 `AtomicReference` / `MutableStateFlow` 原子 CAS，冲突重试有界，不跨线程等待外部锁；链上无其它持有者，不构成死锁。 | 静态复核：链内无 `synchronized` / `Lock` / `Semaphore`；grep 生产目录本类无锁原语即排除。 |
| 点5 | `SearchHotResolver.resolve` / `SearchHistory.clean` / `SearchMatcher.normalize` | 纯函数变换中的循环 | 只有 `mapNotNull` / `distinct` / `take` / `replace(Regex)` 有限集合遍历，无 `while`、无递归、无外部调用。 | grep 三个文件无 `while` / `recursive` / 外部 IO；已逐行通读排除。 |

### 3.4 静态结论与证据缺口

- 主停点判定：**点1 + 点2（测试侧泄漏）**，与既有核验 §5.2「阻塞发生在 `withTimeout` 作用域之外的同步调用，或收集协程在 `Dispatchers.Unconfined` 下未被取消」的推测同向（`docs/CI核验-ff9c20c-e8ce4a8.md:98`）。
- 缺口：本轮 worker 被取消时未留线程转储（`docs/CI核验-ff9c20c-e8ce4a8.md:178`），点2 为源码级静态结论，未经 jstack 实证；点3–点5 已静态排除但无运行时复核。沙箱无 JDK，本单不本地编译、不跑单测。

## 四、改动清单（文件:行 可回源）

共 2 个文件：测试收口 1 个 + 取证日志 1 个；生产代码、工作流 yaml、门禁脚本均未改。

### 4.1 `app/src/test/java/io/legado/app/ui/main/search/SearchHotBinderTest.kt`

- 类 KDoc 新增「收集器收口约定（Q-277 挂死收口）」段（现文件 `:37-46`）：写明 `runBlocking` 根协程等待子协程、无限流收集不取消则收尾 join 永久阻塞，以及 `try/finally { close() }` + `@Test(timeout)` 两层护栏的来由。
- 6 个用例签名：`@Test` → `@Test(timeout = 5_000)`（现文件 `:51`、`:79`、`:102`、`:132`、`:168`、`:204`）。
- 前 5 例：`binder.start()` 后断言包入 `try { … } finally { binder.close() }`（现文件 `:74-76`、`:97-99`、`:127-129`、`:163-165`、`:197-199`）。
- 第 6 例（关闭/重启）：`restarted` 提为可空局部变量，`finally` 内 `binder.close()` + `restarted?.close()`（现文件 `:213`、`:247-250`）；`close()` 幂等（`SearchHotBinder.kt:87` KDoc），中段已调一次不报错。
- 断言原文一条未改：三档来源标记、热门行内容、历史行补丁、关闭快照冻结、重启回填均与修复前逐字一致。

### 4.2 `app/build.gradle`（取证日志事件清单，非分类接线）

- `testTask.testLogging` 事件清单由 `events "STARTED", "FAILED"` 改为 `events "STARTED", "PASSED", "FAILED", "SKIPPED"`（现文件 `:241`）；`showStandardStreams = false` 保留（`:242`）。
- 理由：`data_scripts/find-hang-culprit.py:36-37` 已按 `STARTED` 与终态（`PASSED/FAILED/SKIPPED`）配对；只开 STARTED+FAILED 时绿跑用例全部落「未闭环」，挂死 culprit 只能靠末条 STARTED 倒推（该脚本 `:21` 注释已记录此盲区）。生命周期事件每用例一行，增量与 STARTED 同级（基线 3185 条，分片每片约数百行）；stdout/stderr 仍全量关闭，不引入爆量。
- 未改 `includeCategories` / `excludeCategories` 任何分类接线（`:217-232` 原样保留）。

机检：

```text
$ python3 data_scripts/kotlin-syntax-lint.py app/src/test/java/io/legado/app/ui/main/search/SearchHotBinderTest.kt
KOTLIN-LINT: PASS（1 个文件）

$ python3 data_scripts/check-gradle-syntax.py .
GRADLE-SYNTAX: PASS（7 个 gradle 文件无行首 # 注释）
```

YAML 复核（本单未改 `verify.yml`，仅确认现行工作流可解析）：

```text
$ python3 -c "import yaml; d=yaml.safe_load(open('.github/workflows/verify.yml')); print('YAML OK, jobs:', list(d['jobs'].keys()))"
YAML OK, jobs: ['discover', 'test-shard', 'rhino-eval-tests', 'slow-bench-tests', 'test-aggregate', 'test-baseline']
```

## 五、自查反例与改坏推演（brief §3 必写）

1. **兜底会不会掩盖真 bug？**
   - 本单走 a：不改生产、不删断言、不跳过用例；6 条断言原样保留，泄漏的收集协程在每个用例收尾被显式取消。若生产 binder 回填逻辑回归错误，对应断言仍会在分片内变红，不存在「隔离后永远不再拦人」的盲区。
   - 反例（若当初走 b 隔离）：整类挪进独立 job 后，分片日常不再执行这 6 条；生产侧 `attach/detach` 编排回归时，要等独立 job 才暴露，且独立 job 挂死同样会取消、不上传 XML（`docs/CI核验-ff9c20c-e8ce4a8.md:107`）。本单未采用 b，该盲区不成立；残余缺口是 VM 层「close→置空→pause」编排仍无用例覆盖（见 `docs/修复-浮层行为锁.md` 锁二），与本单泄漏是两件事。
2. **`@Test(timeout)` 会不会与 Robolectric 主线程调度冲突导致假失败？**
   - 本类声明纯 JVM：不碰 `viewModelScope`、`Dispatchers.Main` 与任何 Android framework（测试 KDoc 现文件 `:19-20` 同义）；全仓测试目录无 Robolectric runner 实例。JUnit4 `timeout` 是 runner 层看门线程判失败，不切换测试线程调度器，与主线程 Looper 无交互。
   - 边界：若将来本类引入 Robolectric / `Dispatchers.Main`，看门线程的中断会与 Looper 消息调度竞争，可能把框架等待误判超时；届时应退回 `withTimeout` + 显式 `cancel()`，不依赖 JUnit timeout。
3. **移出分片会不会让 Aggregate 总条数对不上 3185 基线？**
   - 本单未移动任何类、未改工作流：6 条用例仍在原分片执行，XML 归属不变，基线口径（`.github/workflows/verify.yml:637` `BASELINE = 3185`）不受影响。
   - 假设性走 b 的补账口径（备查，本轮不实施）：类打 `@Category` 后 `data_scripts/emit-shard-lists.py:117-118` 将其归入 `category` 桶、不进四分片清单，但仍写入 `manifest.classes`（`emit-shard-lists.py:211-217`）；独立 job 必须在 `test-aggregate` 补下载 pattern 与归组键，参照 Rhino（`.github/workflows/verify.yml:611-615`、`:649-650`）与 slow-bench（`:617-622`、`:651-652`）。漏接下载 pattern 会让 `total` 少计本类 6 条，触发 `total < BASELINE` 红（`.github/workflows/verify.yml:690-691`）；并集守恒口径：分片排除集 ∩ 独立 job 包含集 = ∅，跨 job 同 classname 不得重复（`:665-667`）。

## 六、二阶 / 三阶影响与同类风险清单

### 6.1 本改动的二阶 / 三阶推演

- 一阶：泄漏子协程在每个用例收尾取消，`runBlocking` 收尾 join 可完成；分片 worker 不再被本类拖住。
- 二阶：`finally` 内 `close()` 先于 runBlocking 返回执行；取消是协作式的，StateFlow 收集在下一个挂起点响应取消，残留 Job 不会进入下一个用例（每用例新建独立 binder 与独立 MutableStateFlow，无跨用例共享状态）。
- 三阶：测试线程结束后无残留收集协程持有 `uiState` / fake flow 引用，不影响同 worker 后续类的 GC 与状态隔离；`Dispatchers.Unconfined` 内联执行顺序不变，断言时序口径与修复前一致。

### 6.2 同类风险穷举（grep 口径见下）

扫描命令（测试源集 `app/src/test/java`）：`grep -rn "\.collect\b|awaitClose|runBlocking|CountDownLatch|while *( *true|Future\.get|\.join()|CompletableDeferred|Channel|\.receive()"`，再逐文件区分运行时等待与源码字符串断言。

**A 类 · 同型泄漏：`runBlocking` 作用域内拉起无限流收集协程、收尾不取消 —— 全仓 1 个文件，本单已修**

- `io.legado.app.ui.main.search.SearchHotBinderTest`：即本单。其余文件中 `launch` 命中均为生产源码字符串断言（如 `ReadBookHighlightIsolationTest.kt:85`、`RemoteBookWebDavUploadTest.kt:102`、`DialogViewLifecycleContractTest.kt`、`FileAssociationLoadingStateTest.kt:36`、`AudioEpisodeUnitTest.kt:117`、`JsSourceEditRedirectTest.kt:36`、`ReadBookPopupActionMigrationTest.kt:106-107`、`BottomWebViewDialogLifecycleContractTest.kt:66`），不运行时拉起协程，不计风险。

**B 类 · 分片内裸等待（keep 桶，无超时 latch / Deferred / join）—— 5 个文件，观察项**

1. `io.legado.app.model.jsSource.JsSourceUpsertTest.kt:182` `releaseFirst.await()`、`:186` `firstEntered.await()`、`:197` `first.await()`、`:198` `second.await()`：`CompletableDeferred` / `async(Default)` 会合等待；worker 不启动或锁不释放时永久挂起。`:194` 有 `withTimeoutOrNull(200)`、`:196` 有 `withTimeout(1_000)`，仅这两条有界。
2. `io.legado.app.model.DebugCallbackOwnershipTest.kt:85` `start.await()`：裸 `CountDownLatch.await()`；同用例 `:96` `done.await(2, TimeUnit.SECONDS)` 有界，主等待 latch 靠主随即 `start.countDown()`（`:94`）放行。
3. `io.legado.app.ui.font.FontSelectionStyleTest.kt:138` `start.await()`：裸 latch；`:147` `futures.map { it.get(5, TimeUnit.SECONDS) }` 有界兜底。
4. `io.legado.app.help.source.SourceNavigationPolicyTest.kt:17` `async { … }.await()`、`:18` `normalReader.await()`：被测函数为纯 suspend 即时返回，挂起深度浅，低风险观察项。
5. `io.legado.app.model.ChapterDownloadStateTest.kt:46`、`:47`、`:110`、`:119`、`:188`、`:189`、`:193` 多处 `result.await()` / `waiter.await()`：Deferred 由下载状态机 `finish` 收口；若状态机漏发完成才会挂，属状态机回归面，非协程泄漏。

**C 类 · 已在隔离集（不在分片挂死嫌疑面）**

- `RhinoEvalTest` 18 类：含 `CryptoJsCompatibilityTest.kt:610`、`:708` 裸 `start.await()`，`RuntimeConcurrencyTest.kt:89` 裸 `writer.join()`；由 `rhino-eval-tests` 独立 job 收编（`.github/workflows/verify.yml:345-349`）。
- `SlowBenchTest` 9 类：大批量迭代 / 寻路 / 推演压测；由 `slow-bench-tests` 独立 job 收编（`.github/workflows/verify.yml:470-474`）。
- 既有静态短名单：`docs/挂死嫌疑-静态短名单.md`（Q-276 `data_scripts/scan-blocking-hazards.py` 产物，19 个 keep 桶命中类）；本单补充其特征清单未覆盖的盲区：**收尾阶段子协程 join 等待**（特征 d 只看 runBlocking 体内有无 withTimeout，看不见用例结束后的泄漏 join）。

## 七、新发现问题（2026-11-26，Q-277）

1. `SearchHotBinderTest` 5/6 用例未在收尾取消两路无限流收集协程，`runBlocking` 收尾 join 永久阻塞测试线程——**已修**：`try/finally { binder.close() }` 收口 + `@Test(timeout = 5_000)` 看门，断言未改。两轮 Verify（`45825892031` shard 2、`45833477069` shard 3）同用例复现，修复效果以下一轮分片 XML 为准。
2. `testLogging` 只开 `STARTED`/`FAILED`，PASSED/SKIPPED 不进日志，`find-hang-culprit.py` 无法精确配对，绿跑用例全算「未闭环」，只能末条 STARTED 倒推——**已修**：`app/build.gradle:241` 事件清单补 `PASSED`/`SKIPPED`，`showStandardStreams` 仍关死；增量日志量与 STARTED 同级，stdout 不灌入。
3. Q-276 静态扫描器特征 a–k 不覆盖「用例收尾阶段泄漏子协程 join」这一形态：体内有 withTimeout 也会漏报——**已入队**：评估给 `scan-blocking-hazards.py` 补一条特征（runBlocking 内 `launch`/流收集 且 文件内无配对 `cancel`/`close`），本单不改 `data_scripts/**`。
4. B 类 5 个分片内文件存在裸 latch / Deferred 等待，当前靠「worker 随即放行」的时序假设兜底，无超时上界——**待裁**：下一轮若分片再挂，按 §6.2 清单逐条补 `await(timeout)` 或迁入隔离 job；本单不 preemptive 改动。
5. VM 层 `attach→detachOverlay` 编排（close binder → 置空引用 → pause 模型）仍无用例覆盖，`InkSearchViewModel` 依赖 `Application`，纯 JVM 无法实例化——**待裁**：随 ViewModel 可测性改造单独立项，不在本单范围。
6. 两轮挂死均无线程转储实证：点2 为源码级静态结论，jstack 栈帧未取到——**已入队**：下一轮若再现静默挂死，超时步骤保留 worker 线程转储（承接 `docs/CI核验-ff9c20c-e8ce4a8.md:170` 第 3 条）。

## 八、未验证项与证据缺口

- 沙箱无 JDK：未编译、未跑本地单测；修复后 6 条用例的运行时通过与否，以云端下一轮 Verify 分片 XML 为准。`build.gradle` 事件清单改动同样未经 Gradle 解析执行，仅过 `check-gradle-syntax.py` 行首注释守卫。
- 点2 收尾 join 挂死未经 jstack 实证；点3–点5 为静态排除。两轮日志只能定位到「该用例 STARTED 后无终态」，体内挂起与收尾挂住的区分依赖线程栈；若下一轮本类仍挂，按 §3.3 点2 判据取线程栈复核。
- 既有 1 条断言红 `ImportSourceGroupTest.kt:71`（期望 `"a  b"`）已在另一单改 `"a b"`，其转绿状态不在本单取证范围（`docs/CI核验-ff9c20c-e8ce4a8.md:139-148`）。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「每条结论必须带 `文件:行` 或 `commit sha`；下次要用直接 `sed -n 'X,Yp'` / `git show sha` 回源（回源成本 ≪ 记忆成本）。」（docs/机制-总控必读.md:108）
- 逐字引用 2：「任务书给的规格/原文/切片必须逐字对待，不许压缩、不许改写、不许"我理解大意是"。」（docs/机制-子代理必读.md:32）
- 逐字引用 3：「`文件:行` 或 grep 真实命中（命令 + 输出）；无证据的销项视为未验，判 FAIL。」（docs/机制-验收代理必读.md:64）
- 适用条款：F-01 两布尔回报（HEAD commit / CI 绿红，不报 APK、不报百分比）；锚点强制回源；只读核验不改生产与门禁脚本；证据不足处写明缺口，不替 CI 编结论；探索型结论标注静态推断与待实证项。

## 附：机检输出

```text
$ python3 data_scripts/kotlin-syntax-lint.py app/src/test/java/io/legado/app/ui/main/search/SearchHotBinderTest.kt
KOTLIN-LINT: PASS（1 个文件）

$ python3 data_scripts/check-gradle-syntax.py .
GRADLE-SYNTAX: PASS（7 个 gradle 文件无行首 # 注释）

$ python3 -c "import yaml; d=yaml.safe_load(open('.github/workflows/verify.yml')); print('YAML OK, jobs:', list(d['jobs'].keys()))"
YAML OK, jobs: ['discover', 'test-shard', 'rhino-eval-tests', 'slow-bench-tests', 'test-aggregate', 'test-baseline']
```
