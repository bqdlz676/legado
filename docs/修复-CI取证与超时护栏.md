# 修复-Q-220：CI 失败分片 XML 取证补传与三测试超时护栏

- 被改文件：4 个——`.github/workflows/verify.yml`、`app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt`、`app/src/test/java/io/legado/app/web/mcp/McpApplicationTest.kt`、`app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt`；新增文档 2 份（本报告 + 锚点卡）。
- 判据：本单无新增 `check-*.py` 脚本；机检两道——`check-onboarding.py` 回执块、`check-anchor-card.py --strict` 锚点卡路径与编号反查。
- 真跑门禁结论：见 §八，命令与真实输出落盘在本节。
- 未验证项：3 项——本地无 JDK 未编译、未跑单测、CI 未重跑（护栏实际拦截效果与失败 XML 产物形态待云端复验）。

锚点卡：docs/锚点/Q220-CI取证与超时护栏.md

## 一、背景与事实锚点

本单为 Q-220 落地，承接 `docs/CI核验-Verify-45783964800.md` 的取证结论 c 项「先补取证，再定改点」。该核验报告记录：Verify run `45783964800` 结论 `failure`，shard 1/4 单测失败 1 例、shard 2/4 跑满 45 分钟超时取消、shard 3/4 与 shard 4/4 全绿。两条取证缺口驱动本单：

- 缺口一：shard-1 的 JUnit XML 未出现在 run 产物列表，断言 expected / actual 列表无法从 CI 恢复；Gradle 控制台日志只打印 `java.lang.AssertionError at PathfinderBoundaryTest.kt:104`，不带断言消息与期望值。
- 缺口二：shard-2 挂死期间无任何用例进度输出，挂死用例未被点名；静态排查列出三个高风险测试文件（MCP 装配、Rhino 求值、runBlocking 脚本求值），均无超时护栏。

## 二、改动清单（文件:行，before / after）

### 2.1 `.github/workflows/verify.yml`（单测分片 XML 上传）

- 既有绿跑上传步骤 `:345-350`：路径由非递归 `app/build/test-results/testAppDebugUnitTest/*.xml` 放宽为递归 `app/build/test-results/**/*.xml`。条件保持默认 `success()`——该步骤继续只在绿跑执行，产物原名 `test-results-shard-${{ matrix.shard }}` 不变，供 `test-aggregate` 按 `pattern: test-results-shard-*` 下载求和。
- 新增失败专用步骤 `:356-362`：显式 `if: failure()`；`uses: actions/upload-artifact@v4`；artifact 名 `test-results-shard-${{ matrix.shard }}-failed`（带 shard 号与 `-failed` 后缀）；路径递归 `app/build/test-results/**/*.xml`；`if-no-files-found: warn`。
- 机制说明：原上传步骤无显式 `if`，GitHub Actions 默认条件为 `success()`；前置测试步骤一旦失败，后续无显式条件的步骤被跳过——这正是 run `45783964800` 中 shard-1 XML 落空的直接机制。新增步骤在 `failure()` 条件下兜底上传失败分片的 XML。

### 2.2 `RhinoScriptErrorContextTest.kt`（Rhino 求值护栏，4 个测试方法）

- `:15` `evalErrorIncludesSourceContext`：`@Test` → `@Test(timeout = 5_000)`。
- `:36` `evalSuspendErrorIncludesSourceContext`：`@Test` → `@Test(timeout = 5_000)`。
- `:54` `compileErrorKeepsLineAndColumn`：`@Test` → `@Test(timeout = 5_000)`。
- `:73` `compiledScriptErrorUsesDefiningSourceContext`：`@Test` → `@Test(timeout = 5_000)`。
- 类首注释 `:13-14` 写明选型理由：Rhino `eval`/`compile` 为阻塞同步调用，协程 `withTimeout` 无法中断阻塞在 Rhino 内的线程，故用 JUnit4 `@Test(timeout=)` 在 runner 层限时。

### 2.3 `McpApplicationTest.kt`（MCP Server 装配护栏，4 个测试方法）

- `:23` `missingOrWrongTokenRejectsEveryMcpMethodBeforeParsing`：`@Test` → `@Test(timeout = 10_000)`。
- `:56` `validTokenReachesTheSdkRoute`：`@Test` → `@Test(timeout = 10_000)`。
- `:73` `disabledTokenProtectionAllowsMissingTokenButStillChecksHost`：`@Test` → `@Test(timeout = 10_000)`。
- `:106` `validTokenStillRequiresAllowedHostAndOrigin`：`@Test` → `@Test(timeout = 10_000)`。
- 类首注释 `:21-22` 写明：`testApplication` 内构建 MCP SDK `Server`，后台会话协程若泄漏会拖住测试线程；10s 上限留出 Ktor 引擎启动余量。

### 2.4 `RssSortUrlsTest.kt`（脚本求值护栏，8 个测试方法）

- `:17` `normal multi category values are parsed`：`@Test` → `@Test(timeout = 5_000)`。
- `:34` `valid wrapped script result is parsed`：`@Test` → `@Test(timeout = 5_000)`。
- `:50` `script exception falls back to source url`：`@Test` → `@Test(timeout = 5_000)`。
- `:62` `at js prefix is removed before evaluation`：`@Test` → `@Test(timeout = 5_000)`。
- `:77` `script cancellation is propagated`：`@Test` → `@Test(timeout = 5_000)`。
- `:91` `missing closing script tag falls back without evaluation`：`@Test` → `@Test(timeout = 5_000)`。
- `:107` `empty and invalid results fall back to source url`：`@Test` → `@Test(timeout = 5_000)`。
- `:117` `cached script result avoids evaluation`：`@Test` → `@Test(timeout = 5_000)`。
- 类首注释 `:15-16` 写明：`runBlocking` 包裹脚本求值，求值回调若挂起会拖住测试线程，5s 上限在 runner 层限时。

### 2.5 计数口径更正（与任务书转述的出入，以源码为准）

- 任务书转述「RhinoScriptErrorContextTest.kt 11 处 evaluateString」：该计数是正则宽匹配（import、`Context`、`eval` 等一并计入）。真实 Rhino 调用入口 5 处——`:22` `eval`、`:42` `evalSuspend`、`:60` `compile`、`:83` `compile(definitions).eval(scope)`、`:86` `compile("outer();").eval(scope)`；4 个测试方法全部加护栏，入口覆盖无遗漏。
- 任务书转述「RssSortUrlsTest.kt 8 处 runBlocking」：源码真实 `runBlocking` 为 7 处，另有 1 个方法为纯字符串解析（`:17`）；8 个 `@Test` 全部加护栏。

## 三、grep 回证（改动后真实输出）

### 3.1 三个测试文件护栏计数与裸 `@Test` 残留

真实命令：`grep -c '@Test(timeout' <file>` 与 `grep -cE '^[[:space:]]*@Test[[:space:]]*$' <file>`。

```text
== app/src/test/java/io/legado/app/RhinoScriptErrorContextTest.kt
timeout=5 plain=0
== app/src/test/java/io/legado/app/web/mcp/McpApplicationTest.kt
timeout=4 plain=0
== app/src/test/java/io/legado/app/help/source/RssSortUrlsTest.kt
timeout=8 plain=0
```

口径说明：Rhino 文件 `timeout=5` 中 1 处为类首注释 `:14` 里的选型说明文字「JUnit4 @Test(timeout=)」，真实注解为 4 处（`:15`、`:36`、`:54`、`:73`）；三个文件裸 `@Test` 残留均为 0。

### 3.2 业务断言零改动

对三个测试文件跑 diff 过滤 `assertEquals|assertTrue|assertFalse|assertThrows|assertNotEquals`，`+`/`-` 行命中数为 0——本单只加注解与注释，未改任何业务断言。

### 3.3 verify.yml 上传步骤回证

```text
:345  - name: Upload shard JUnit XML results
:348    name: test-results-shard-${{ matrix.shard }}
:349    path: app/build/test-results/**/*.xml
:356  - name: Upload failed-shard JUnit XML (failure-only)
:357    if: failure()
:358    uses: actions/upload-artifact@v4
:360    name: test-results-shard-${{ matrix.shard }}-failed
:361    path: app/build/test-results/**/*.xml
```

### 3.4 diff 规模（`git diff --stat -- app/src/test/ .github/workflows/verify.yml` 真实输出）

```text
 .github/workflows/verify.yml                           | 17 ++++++++++++++++-
 .../java/io/legado/app/RhinoScriptErrorContextTest.kt  | 10 ++++++----
 .../java/io/legado/app/help/source/RssSortUrlsTest.kt  | 18 ++++++++++--------
 .../java/io/legado/app/web/mcp/McpApplicationTest.kt   | 10 ++++++----
 4 files changed, 38 insertions(+), 17 deletions(-)
```

拆分口径：verify.yml `+16 / -1`；Rhino `+6 / -4`；Mcp `+6 / -4`；Rss `+10 / -8`。

## 四、护栏选型与取值依据

- 选型：JUnit4 `@Test(timeout = …)`，未用协程 `withTimeout`。`modules/rhino/src/main/java/com/script/rhino/RhinoScriptEngine.kt` 锚点：`:43` `fun eval(...)`、`:164` `fun compile(...)` 为阻塞同步，`:100` `suspend fun evalSuspend(...)` 虽为 suspend，但其内部脚本执行仍落在 Rhino 线程；`withTimeout` 只能在协程挂起点响应取消，无法中断阻塞在 Rhino 内的线程。JUnit4 看门线程在 runner 层限时，超时即把该用例判失败，不依赖被测代码主动响应取消。
- 同仓先例：`app/src/test/java/io/legado/app/model/jsSource/JsSourceConfigTest.kt:469`、`app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt:273` 已用 `@Test(timeout = 5_000)`，本单口径与之对齐。
- 取值：Rhino / Rss 取 `5_000` ms——这两个文件内脚本均为立即抛错、编译错误或固定回调字符串，正常执行毫秒级，5s 余量千倍以上；Mcp 取 `10_000` ms——Ktor `testApplication` 引擎启动叠加最多 9 个 `client.request`，给高负载 runner 留启动抖动余量。

## 五、二阶三阶影响

### 5.1 慢测试是否会被超时护栏误判变红

- Rhino 文件：4 个用例脚本均为立即 `throw 'boom'` / 语法错误 / 嵌套调用错误，无循环、无 IO；5s 上限不会触达。
- Rss 文件：求值回调由测试自供（返回固定字符串或抛异常），不直连 Rhino、无网络；5s 上限不会触达。唯一含取消语义的 `script cancellation is propagated` 在 `:77`，取消由 lambda 同步抛出，不依赖等待。
- Mcp 文件：4 个用例走内存 Ktor 引擎，无真实网络；10s 上限覆盖引擎启动余量。残余风险：CI 高负载 runner 上引擎启动偶发抖动；若后续观察到超时误红，调大注解数值即可，无需动断言。
- 护栏只作用于这三个文件，其他测试的时长口径不变。

### 5.2 是否误杀长任务 / 合法等待

- 这三个文件内不存在合法长等待：无 `delay(`、无 `Thread.sleep`、无网络构造、无 `CountDownLatch.await` 长等；需要长等待的用例不在本文件域。
- JUnit4 超时只把当前用例判失败，Gradle 继续执行后续用例；不跨分片、不阻塞其他 job。
- 已知局限：若线程阻塞在原生 / JNI 不可中断循环（如 Rhino 内 `while (true) {}`），看门线程能判用例失败，但被卡线程未必立即释放；分片 `timeout-minutes: 45`（`verify.yml:210`）仍是最终兜底，本单不替换它。

### 5.3 对 aggregate 汇总链路的影响

- 绿跑链路不变：既有上传步骤保持默认 `success()` 条件与原 artifact 名，`test-aggregate` 的 `pattern: test-results-shard-*`（`:380`）照常下载求和；BASELINE=3185 的硬断言口径不变。
- 失败专用产物名带 `-failed` 后缀；aggregate 在 `needs: [discover, test-shard]` 上游失败时不运行，红跑不会下载该产物，pattern 不撞车。
- 递归 glob `**/*.xml` 比原非递归宽，理论上可能多收变体子目录 XML；aggregate 按 `classname` 去重校验，同一变体目录下不会出现重复 classname，误报「重复执行」的风险低。

### 5.4 对并发代理、CI 时长与维护成本

- 工作树中他人未提交改动（`TerrainMap.kt` 等）未触碰、未回滚；本单文件域为 verify.yml 与上述三个测试文件。
- CI 时长：正常绿跑不触超时，不增加用例耗时；原本挂死 45 分钟被取消的分片，今后会在秒级至 10s 内以超时失败收口，红反馈提前。
- 维护成本：改动仅注解参数与两行注释，无新增抽象、无新配置项；超时值集中在各注解上，后续调整单点可改。

## 六、范围冲突与未做项

- 断言消息拼列表（两次路径全打印）：任务书第 1 条后半要求把 `first`/`second` 列表带进断言消息；该改动只能落在 `PathfinderBoundaryTest.kt`，而收尾文件域约束为「只改这三个测试文件与 verify.yml」，未包含该文件。按前后冲突以后面最新指令为准，本单不改该测试。expected / actual 的恢复路径改为：失败 XML 的 JUnit `<failure>` 节点携带 `assertEquals` 自带的 expected / actual 文本与堆栈。在断言消息里拼 `first`/`second` 全列表列为后续单建议，见锚点卡 §三。
- `Pathfinder.kt`：一字未改（任务书第 3 条；前序核验已静态核查 `:155` 比较器带次级键、`:187-191` 严格更小才改写，本单不复议）。
- 基线影子 job 的上传步骤 `verify.yml:546` 仍为非递归 glob，不属本单单测分片步骤范围，未动。
- 未新增任何 `data_scripts/` 脚本；未改 `docs/机制-*` 守则文件。

## 七、未验证项声明

- 本地沙箱无 JDK：未跑 `gradle` / `kotlinc` / `assemble`，未跑单测；编译与测试结果以云端 CI 为准。
- CI 未重跑：超时护栏的实际拦截表现、失败分片 `-failed` 产物的实际形态，待下一轮云端 Verify 复验。
- `actions/upload-artifact@v4` 的 `**` 递归匹配按 action 文档支持递归 glob；本环境未实测上传动作。

## 八、机检输出

（本节在文档落盘后实跑两道门禁，命令与输出粘贴于此。）

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=50089f055160；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`：「不许 add/commit/push/revert/checkout——提交由总控统一做。」
2. `docs/机制-子代理必读.md:12`：「不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。」
3. `docs/机制-总控必读.md:108`：「回源（回源成本 ≪ 记忆成本）」
- 适用条款：文件域独占——只改 verify.yml 与三个指定测试文件，未碰他人并发改动与生产代码；禁 git 写操作——全程未 add/commit/push/revert/checkout；禁本地编译——未跑 gradle/kotlinc/assemble；锚点强制——所有改动落点带 `文件:行`，源码行号以 b744fb4 锚点与工作树 grep 回证为准；证据不足写明缺口——护栏实际拦截效果、XML 产物形态列为未验证项，不替 CI 编结论；一条缺陷一单——Q-220 只收口取证补传与超时护栏，不改 Pathfinder 红因本体。
