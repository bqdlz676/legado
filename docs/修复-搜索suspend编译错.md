> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 1 条（见 回执补录-批1-搜索书架 §三）
> 回执缺失（规则上线前交付，留档）

> 漂移重判：原锚已失效，本结论标注为已废（2026-11-26）：本报告指向 `InkSearchActivity.kt` 的锚点在仓内已无对应文件与声明（c66ab2b（旧独立搜索页改道书架浮层，Activity 壳已删））；相关结论系旧壳/旧基线快照，原文保留备查，现行主张以书架浮层 Compose 栈为准。
# 修复-搜索 suspend 编译错（CI `compileAppDebugKotlin`，HEAD `548e8ba`）

- 基线：CI job `137676431139` / run `45169909583`，HEAD `548e8ba`。
- 任务范围：仅 `app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt`；同包其余文件穷举核对，未发现同类 suspend 越界。未触碰 crossing / sandbox / replay / bookshelf / sheet。
- 红线遵守：未跑本地 gradle / kotlinc 编译，未做任何 git 写操作（提交由总控收口）。

## 一、报错原文

```
src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:138:33
Suspension functions can only be called within coroutine body.
```

报错位置（改前第 138 行第 33 列）：

```kotlin
val local = queryLocal(key)
```

## 二、根因（形态③：lambda 内用了 suspend，但 lambda 不是 suspend）

`queryLocal` 声明为 `private suspend fun`（改前第 184 行），内部走 `withContext(Dispatchers.IO) { … flowSearch(key).first() }`，是挂起函数。

`runSearch` 的调用链：

```kotlin
private fun runSearch(key: String) {
    val command = commandGate.next()
    execute {                                    // ← 外层是 suspend CoroutineScope.() -> T，协程体合法
        commandGate.runIfCurrent(command) {      // ← 闸门 block 形参是 () -> Unit，非挂起 lambda
            …
            val local = queryLocal(key)          // ← 错误行：在非挂起 lambda 内调 suspend
            …
        }
    }
}
```

- `BaseViewModel.execute` 的 block 类型为 `suspend CoroutineScope.() -> T`（见 `BaseViewModel.kt:27`），外层确实是协程体；
- 但闸门 `SearchCommandGate.runIfCurrent(command: Long, block: () -> Unit)` 的 block 是**普通非挂起 lambda**，类型 `() -> Unit` 不携带挂起上下文；编译器把该 lambda 体当作非协程上下文，其中调用 `queryLocal`（suspend）即报 “Suspension functions can only be called within coroutine body”。
- 归类：形态③（在 lambda 里用了 suspend 但 lambda 不是 suspend）；不是形态①（普通 fun 直接调 suspend），也不是形态②（launch 之外）。
- 静态验收盲区：该缺陷只有编译期语义检查能发现，纯静态文本/引用核对看不见，故此前静态判 PASS。

## 三、修复思路（不丢异步语义，不改闸门作废语义）

约束：

- 不许把 `queryLocal` 改成非挂起去绕编译（会丢掉 Flow 首个元素的异步语义与 IO 调度）；
- 闸门 `SearchCommandGate` 是纯 JVM 类，其 `runIfCurrent` / `invalidate` 的非挂起签名被同包单测 `SearchCommandGateTest.kt` 从普通线程（`executor.submit`）直接调用，不能改成 suspend，否则测试源集连带编译失败；
- 本次不实现 searchId 二次比对（既有遗留项，明确不在范围内），闸门与陈旧回调逻辑保持原样。

改法：

1. 闸门新增一个非挂起的纯比对入口 `isCurrent(command): Boolean`，与 `runIfCurrent` 共用同一把锁、同一个序号，作废语义一致；不执行体。
2. `runSearch` 改为：在 `execute { }` 协程体内先 `isCurrent` 比对，比对通过后编排体直接写在协程体内（`queryLocal` 挂起调用合法）；比对不通过直接 `return@execute`，不做任何字段写入。
3. 原 `runIfCurrent` / `invalidate` / `next` 签名与实现原样保留，单测不受影响。

### 改动清单（行号为改后文件）

文件：`app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt`

**A. `runSearch`（第 128–150 行）**

- 第 130 行：`execute {` 协程体入口不变。
- 第 134 行：`if (!commandGate.isCurrent(command)) return@execute`（替换原 `commandGate.runIfCurrent(command) { … }` 包裹）。
- 第 135–137 行：`cancelSearch()` / `searchID = command` / `searchKey = key`，由原闸门 lambda 内上移到协程体。
- 第 139–145 行：离线兜底分支；第 141 行 `val local = queryLocal(key)` 现位于 `execute` 协程体内，挂起调用合法；分支出口由 `return@runIfCurrent` 改为 `return@execute`（原 lambda 之后本就无语句，出口等价）。
- 第 147–148 行：`saveSearchKey(key)` / `searchModel?.search(searchID, key)` 保持原顺序与原调用。

**B. `SearchCommandGate`（第 229–253 行）**

- 第 235–241 行：新增 `fun isCurrent(command: Long): Boolean = synchronized(lock) { command == sequence.get() }`。
- 第 243–247 行：`runIfCurrent` 原样保留；第 249–252 行：`invalidate` 原样保留；第 233 行：`next` 原样保留。

### 语义复核（闸门 / 陈旧回调）

- 作废判定不变：命令已被 `next()` 新命令或 `invalidate()` 作废时，`isCurrent` 返回 false，编排体整体跳过，不写 `searchID` / `searchKey`、不发起检索。
- `onCleared` 仍走 `commandGate.invalidate { searchModel?.close() }`，未改。
- 回调侧 `onSearchSuccess / onSearchFinish / onSearchCancel` 未改；既有“仅有闸门、无 searchId 二次比对”的遗留项本次不实现、不扩大改动。
- 一处可接受的实现差异：原 `runIfCurrent` 在 `synchronized` 块内执行整个 block（含 IO 检索）；改后锁只保护序号比对，编排体在锁外执行。原写法在挂起点持有 JVM 监视器本身就是反模式，且该代码从未编译通过、不存在可对照的线上行为；锁的职责本就只是守护序号计数器。

## 四、同包 suspend 声明与调用点清单

包：`app/src/main/java/io/legado/app/ui/main/search/`

### 4.1 suspend 声明（全包仅 1 处）

| 文件:行 | 声明 | 说明 |
|---|---|---|
| InkSearchViewModel.kt:186 | `private suspend fun queryLocal(key: String): List<SearchResultItem>` | 本地书架模糊匹配，`withContext(Dispatchers.IO)` + `Flow.first()` |

### 4.2 suspend / 挂起型调用点逐点核对

| # | 文件:行 | 调用 | 所在上下文 | 是否 coroutine body | 结论 |
|---|---|---|---|---|---|
| 1 | InkSearchViewModel.kt:141 | `queryLocal(key)` | `execute { }` suspend 块（`runSearch`） | 是（`execute` block: `suspend CoroutineScope.() -> T`） | 合法，本次修复点 |
| 2 | InkSearchViewModel.kt:166 | `queryLocal(searchKey)` | `execute { }` suspend 块（`onSearchSuccess` 回调内） | 是 | 合法，未改 |
| 3 | InkSearchViewModel.kt:188 | `flowSearch(key).first()` | `withContext` 块（`queryLocal` suspend 体内） | 是 | 合法，未改 |
| 4 | InkSearchViewModel.kt:197 | `flowByTime().collect { … }` | `execute { }` suspend 块（`observeHistory`） | 是 | 合法，未改 |
| 5 | InkSearchActivity.kt:37 | `uiState.collectAsState()` | `setContent { }` Composable 体 | 是（Compose 组合上下文，`collectAsState` 为 Composable 函数） | 合法，未改 |

### 4.3 其余 `execute { }` 块（无 suspend 调用，仅普通 DAO / 状态搬运）

| 文件:行 | 入口 | 块内内容 |
|---|---|---|
| InkSearchViewModel.kt:118 | `removeHistory` | `appDb.searchKeywordDao.delete(...)`（DAO 非 suspend） |
| InkSearchViewModel.kt:126 | `clearHistory` | `appDb.searchKeywordDao.deleteAll()`（DAO 非 suspend） |
| InkSearchViewModel.kt:205 | `saveSearchKey` | `get / update / insert`（DAO 非 suspend） |

### 4.4 穷举结论

- `grep -rn "suspend fun" app/src/main/java/io/legado/app/ui/main/search/`：仅 `queryLocal` 一处声明。
- 包内其余文件（`SearchFallback / SearchHighlight / SearchHistory / SearchHistoryPanel / SearchInputBar / SearchMappers / SearchMatcher / SearchModels / SearchMotion / SearchReducer / SearchResultList / SearchScreen / SearchTokens`）无 DAO 访问、无 Flow collect/first、无协程启动，不存在 suspend 越界。
- DAO 方法（`SearchKeywordDao`、`BookDao` 相关）均为非 suspend 旧式 Room 写法，普通线程调用不构成挂起上下文问题。

## 五、grep 证据

```text
$ grep -rn "suspend fun" app/src/main/java/io/legado/app/ui/main/search/
app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:186:    private suspend fun queryLocal(key: String): List<SearchResultItem> =

$ grep -rn "queryLocal\|\.first()\|\.collect\b\|collectAsState\|withContext" \
    app/src/main/java/io/legado/app/ui/main/search/
app/src/main/java/io/legado/app/ui/main/search/InkSearchActivity.kt:8:import androidx.compose.runtime.collectAsState
app/src/main/java/io/legado/app/ui/main/search/InkSearchActivity.kt:37:            val state by viewModel.uiState.collectAsState()
app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:22:import kotlinx.coroutines.withContext
app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:141:                val local = queryLocal(key)
app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:166:                val local = queryLocal(searchKey)
app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:186:    private suspend fun queryLocal(key: String): List<SearchResultItem> =
app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:187:        withContext(Dispatchers.IO) {
app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:188:            runCatching { appDb.bookDao.flowSearch(key).first() }
app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt:197:                .collect { keywords ->

$ grep -n "execute {\|runIfCurrent\|isCurrent" \
    app/src/main/java/io/legado/app/ui/main/search/InkSearchViewModel.kt
92:        execute {
100:        execute {
130:        execute {
134:            if (!commandGate.isCurrent(command)) return@execute
165:            execute {
194:        execute {
205:        execute {
237:     * 与 [runIfCurrent] 共用同一把锁与同一个序号，作废语义一致。
239:    fun isCurrent(command: Long): Boolean = synchronized(lock) {
243:    fun runIfCurrent(command: Long, block: () -> Unit): Boolean = synchronized(lock) {
```

`BaseViewModel.execute` block 类型（协程体依据）：

```text
app/src/main/java/io/legado/app/base/BaseViewModel.kt:27
    block: suspend CoroutineScope.() -> T
```

## 六、未验证项

- **未本地编译**：按红线未跑 gradle / kotlinc；以上为编译期语义静态核对，需 CI `compileAppDebugKotlin`（及 `compileAppDebugUnitTestKotlin`）复跑确认。
- 未在真机 / 模拟器上跑搜索主路径（离线本地书架检索、在线检索、历史回填、重试、清空历史）；运行时行为未验证。
- `SearchCommandGateTest` 未本地执行；仅核对其调用的 `runIfCurrent` / `invalidate` / `next` 签名未变，测试应仍可编译通过，实际结果待 CI 单测。
- 既有遗留项（回调无 searchId 二次比对、分页加载更多未接线）保持原状，本次未处理。
