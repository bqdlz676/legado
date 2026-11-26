# 修复-InkContentEditState-平台签名撞名

**机械改动·无新规格·锚点卡豁免理由**：本单只改三个手写函数名及其调用点，赋值行为、时序、默认值、可见性（`private set` 原样保留）均未动；属机械改名豁免锚点卡，不引入新规格、不重构、不加依赖。

## 守则回执

- docs/机制-总控必读.md=87180874ccf0
- docs/机制-子代理必读.md=c6806030a6d5
- docs/机制-验收代理必读.md=a46af85f0145
- docs/机制-文档子代理必读.md=6dd8114a6a74

逐条逐字引用（真读到的原句 + 文件:行）：

1. `docs/机制-子代理必读.md:8`："- **R57 一条缺陷一单**：只修任务书点名的那一条。发现别的缺陷 → 记进报告"交接"节，**不要动手改**。"
2. `docs/机制-子代理必读.md:11`："- **禁 git 写操作**：不许 add/commit/push/revert/checkout——提交由总控统一做。"
3. `docs/机制-子代理必读.md:12`："- **禁本地编译**：不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。"

## ANCHOR-ACK

1. 任务首行逐字："修红 CI Verify：InkContentEditState 平台签名撞名"
2. 绝对路径：
   - `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditState.kt`
   - `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt`
   - `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditInputs.kt`
3. 第一步命令：`sed -n '28,60p;105,150p' /home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditState.kt`

## CI 报错原文引用（run 45782540403，job 139781237369，步骤 Compile unit test sources: failure）

```
e: ...ink/InkContentEditState.kt:36:5 Platform declaration clash: ...
e: ...InkContentEditState.kt:44:9 Platform declaration clash: ...
    fun setSaving(value: Boolean): Unit defined in io.legado.app.ui.book.read.ink.InkContentEditState
e: ...InkContentEditState.kt:55:5 Platform declaration clash: ...
    fun setSearchError(error: String?): Unit defined in io.legado.app.ui.book.read.ink.InkContentEditState
e: ...InkContentEditState.kt:112:5 / :141:5 / :146:5 同上
```

根因：Kotlin 中 `var saving` / `var searchError` / `var titleEditing` 自动合成 JVM setter `setSaving` / `setSearchError` / `setTitleEditing`，文件内又手写了同名同签名函数，平台契名冲突，编译器中断。

## 改动清单（文件:行 旧 → 新）

### InkContentEditState.kt（3 处定义）

1. `:112` `fun setSaving(value: Boolean) {` → `:116` `fun markSaving(value: Boolean) {`；新增 KDoc 说明为何不再叫 `setSaving`（`var saving` 已合成同名 JVM 方法，手写同签名函数触发 Platform declaration clash）。
2. `:141` `fun setSearchError(error: String?) {` → `:149` `fun updateSearchError(error: String?) {`；新增 KDoc 说明避冲突理由，赋值行为与时序不变。
3. `:146` `fun setTitleEditing(editing: Boolean) {` → `:157` `fun setTitleEditingState(editing: Boolean) {`；KDoc 改写：`var titleEditing` 已合成 `setTitleEditing`，后缀 `State` 仅作命名区分，不改行为与时序。

### InkReadActivity.kt（2 处调用点）

4. `:354` `contentEditState.setSaving(true)` → `contentEditState.markSaving(true)`
5. `:371` `contentEditState.setSaving(false)` → `contentEditState.markSaving(false)`

### InkContentEditInputs.kt（6 处调用点）

6. `:80` `state.setTitleEditing(true)` → `state.setTitleEditingState(true)`
7. `:114` `state.setTitleEditing(false)` → `state.setTitleEditingState(false)`
8. `:125` `state.setTitleEditing(false)` → `state.setTitleEditingState(false)`
9. `:151` `state.setSearchError(null)` → `state.updateSearchError(null)`
10. `:159` `state.setSearchError(context.getString(R.string.content_search_invalid_regex))` → `state.updateSearchError(context.getString(R.string.content_search_invalid_regex))`
11. `:163` `state.setSearchError(null)` → `state.updateSearchError(null)`

合计：3 个文件、11 处改动（3 定义 + 8 调用点）。

## grep 回证

### 旧符号清零（DoD 2）

命令：`grep -rn "setSaving(\|setSearchError(\|setTitleEditing(" app/src/main/java`

输出：无任何行（grep 退出码 1，即 0 行命中）。

### 新符号就位

命令：`grep -rn "markSaving(\|updateSearchError(\|setTitleEditingState(" app/src/main/java`

输出：

```
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditInputs.kt:80:                            state.setTitleEditingState(true)
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditInputs.kt:114:                        state.setTitleEditingState(false)
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditInputs.kt:125:                state.setTitleEditingState(false)
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditInputs.kt:151:            state.updateSearchError(null)
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditInputs.kt:159:            state.updateSearchError(context.getString(R.string.content_search_invalid_regex))
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditInputs.kt:163:        state.updateSearchError(null)
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditState.kt:116:    fun markSaving(value: Boolean) {
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditState.kt:149:    fun updateSearchError(error: String?) {
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditState.kt:157:    fun setTitleEditingState(editing: Boolean) {
app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:354:        contentEditState.markSaving(true)
app/src/main/java/io/legado/app/ui/book/read/ink/InkReadActivity.kt:371:            contentEditState.markSaving(false)
```

### 反向证明：新函数与属性不撞（DoD 3）

`InkContentEditState.kt` 全部 `var`（合成 JVM setter 名）：

- `:21` visible → setVisible；`:24` target → setTarget；`:28` baselineContent → setBaselineContent；`:30` content → setContent；`:31` baselineTitle → setBaselineTitle；`:33` title → setTitle；`:36` titleEditing → setTitleEditing；`:39` loading → setLoading；`:41` loadError → setLoadError；`:43` saving → setSaving；`:45` message → setMessage；`:49` searchVisible → setSearchVisible；`:50` searchQuery → setSearchQuery；`:51` regex → setRegex；`:52` matchCase → setMatchCase；`:53` matches → setMatches；`:54` matchIndex → setMatchIndex；`:55` searchError → setSearchError；`:58` plainText → setPlainText；`:61` confirmDiscard → setConfirmDiscard。

全部手写 `fun set*`（改后）：

- `:140` `fun setMatches(matches: List<IntRange>, index: Int)`
- `:157` `fun setTitleEditingState(editing: Boolean)`

逐对比对：

- `markSaving`（:116）：不以 `set` 开头，不对应任何合成 setter；与 `var saving` 合成的 `setSaving` 不再重名。
- `updateSearchError`（:149）：不以 `set` 开头；与 `var searchError` 合成的 `setSearchError` 不再重名。
- `setTitleEditingState`（:157）：仓内不存在 `var titleEditingState`，无对应合成 setter；`var titleEditing` 合成的是 `setTitleEditing`，名字不同，不再重名。
- `setMatches`（:140）：手写签名为两参 `(List<IntRange>, Int)`，合成 `setMatches` 为单参 `(List<IntRange>)`；JVM 描述符分别为 `(Ljava/util/List;I)V` 与 `(Ljava/util/List;)V`，参数个数不同，不构成签名冲突。

### 同文件其它 `fun setX` 与 `var x` 同名排查（DoD 4）

命令：`grep -n "fun set" app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditState.kt` → 仅 `:140 setMatches`、`:157 setTitleEditingState` 两条，均已在上节逐对说明；无其它潜在同名冲突。ink 目录全域扫描：`grep -n "fun set" app/src/main/java/io/legado/app/ui/book/read/ink/*.kt` 除上述两条外无命中。

### 语法预检

`python3 data_scripts/kotlin-syntax-lint.py` 对 3 个改动文件：**KOTLIN-LINT: PASS（3 个文件）**。

## 二阶三阶影响

- 直接效果：消除三组平台签名冲突，CI Compile unit test sources 步骤应可越过声明冲突这一类错误。
- 连带影响：本状态类仅被同目录 `InkReadActivity`、`InkContentEditInputs` 引用（grep 全 `app/src/main/java` 无其它调用点，亦无 Java 调用方）；改名后调用点已全部同步，不存在悬空引用。属性本身及其合成 setter 未被外部按 JVM setter 方式调用（Compose 侧均直接读写属性）。
- 用户行为：保存按钮可用性（`enabled = !state.saving`）、搜索错误文案呈现、标题行内编辑进出，均走同一属性赋值，语义与时序不变，用户侧无可感知差异。
- 系统复杂度 / 维护成本：新增两个非 `set` 前缀动词（`markSaving` / `updateSearchError`），与既有 `markContentSaved` / `markTitleSaved` / `applyLoadError` / `showMessage` 命名口径一致；`setTitleEditingState` 保留 `set` 前缀但带 `State` 后缀，KDoc 已写明避冲突原因，后续接手者不会再把它改回 `setTitleEditing`。

## 反例与边界

- 边界 1：若后续有人新增 `var titleEditingState`，则 `setTitleEditingState` 会再次撞名。规避：KDoc 已标注命名缘由；验收时若新增同名属性需同步改名。
- 边界 2：若未来以 Java 代码（或反射 / 视图绑定）按 `setSaving(...)` JVM 名调用，改名后将找不到方法。现状 grep 确认无 Java 调用方、无反射字符串引用；若跨语言接入需改用新名。
- 边界 3：属性为 `private set`，外部本就不能直接赋值；手写函数是唯一写入通道。本次仅改通道名，未放开可见性，空值（`searchError = null`）、布尔翻转（`true`/`false`）、正则失败分支的赋值路径与原实现逐行一致。
- 边界 4：改名未触碰 `page/**` 分页引擎冻结区；改动文件全部位于 `ui/book/read/ink/` 目录内。

## 认知成本

- 本次改动让接手人更容易还是更难：更容易。冲突根因（Kotlin 合成 setter 与手写同名函数）在三处 KDoc 内各有一句解释，接手者无需翻 CI 记录即可理解命名约束。
- 新增概念 / 抽象：无新增类、接口、层级；仅三个函数改名 + 三段 KDoc。
- 命名澄清：`markSaving` / `updateSearchError` 为动作动词；`setTitleEditingState` 后缀 `State` 是唯一需要留意的特例，KDoc 已说明。

## 未处理项（发现但未改）

- `:140` `fun setMatches(matches: List<IntRange>, index: Int)` 与 `var matches` 合成的单参 `setMatches`：参数个数不同，JVM 描述符不同，不构成冲突；按红线不顺手重构，保留原样，仅登记备查。
- 未发现跨目录调用点；`setTitleEditing(` 的 3 个调用点全部位于 `InkContentEditInputs.kt`（ink 目录内），无跨目录清单需交总控。

## 未验证项

- **编译只在云端，待 CI 验证**：沙箱无 gradle/JDK，本单未本地编译、未跑单测、未真机验证；三处改名与八处调用点的最终编译闭环以云端 CI（Verify / Compile unit test sources）结果为准。
- lint 脚本仅做括号配平、尾逗号、`fontFamily` 直传、文件行数等静态预检，不能替代编译器的类型与签名检查。

## 总控补录（非本代理原文，可审计）

- 缺项：本报告交付时漏写「适用条款」行，推送体检 3.7 步判红（`ONBOARD: FAIL`，判据 `data_scripts/check-onboarding.py`）。
- 适用条款（依据：本单任务书原文 + 该代理回执指纹 `docs/机制-子代理必读.md=c6806030a6d5`）：R57 一条缺陷一单（只改点名文件）／禁 git 写操作／禁本地编译／B2 改动后 grep 回证／D5 工具预算 ≤40 次／E12 DoD 与止损。
- 补录人：总控；补录时间锚：`sessions/38454122521460994/agents/s_000imNg7JoT`（轨迹 38 行，工具调用 16 次）；**未改写本代理正文任何字句**。
- 为什么允许补录：适用条款是本单"照做了哪些条"的登记项，可从任务书与该代理回执直接回源核对；补录比让门禁红着更好，但必须标明补录者与依据，防以后被当成代理自述（呼应 §20 五阶"伪造证据动机"）。
