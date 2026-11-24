# 复盘 · CI 连红的共同根因与机械守卫（2026-11-24）

> 触发：`f7a0e48` / `4508967` / `f08c4ba` / `d264ff6` 四连红。用户铁律「复盘 Bug 的共同根因，沉淀成规则或自动化，别反复付同样的成本」。
> 结论先行：**这次连红不是 13 个互不相关的失误，而是 5 类"语法合法但编译必红"的手写陷阱**，每一类都能用静态检查做掉，不必再靠人肉发现。

## 一、四连红的错误清单（共 78 条 `e:` 行，13 文件）

| 文件 | 条数 | 归类 |
|---|---|---|
| `map/crossing/shell/CrossingShell.kt` | 39 | ① Cascade（上游语法错拖垮下游）+ 3 处独立缺陷 |
| `map/crossing/terrain/HeightMapDecoder.kt` | 7 | ⑤ 常量宽度 |
| `ui/card/EmptyStateCard.kt` | 4 | ② 漏 import + ③ 错包 |
| `ui/card/BookCard.kt` | 4 | ④ 臆造 API 参数名 |
| `ui/card/CardBase.kt` | 3 | ② 漏 import（扩展函数） |
| `ui/book/read/ShadowEditDialog.kt` | 3 | ⑥ 作用域 receiver 误判 |
| `ui/book/read/HighlightNoteDialog.kt` | 3 | ⑦ 字符串模板转义 |
| `map/crossing/engine/CampaignController.kt` | 2 | ⑧ 嵌套注释吞代码 |
| `ui/main/bookshelf/BookshelfScreen.kt` | 2 | ⑨ 调用点与签名漂移 |
| `ui/card/ShareCard.kt` | 2 | ③ 错包（`android.annotation.RequiresApi`） |
| `ui/main/MainActivity.kt` | 1 | ⑨ 被重写删掉的方法 |
| `ui/card/TopicCard.kt` | 1 | ② 漏 import（`Modifier.clip`） |
| `ui/card/SearchResultCard.kt` | 1 | ② 漏 import（`withStyle`） |

## 二、五类根因（为什么"看着对"却编译红）

1. **扩展函数漏 import**（4 例）：`InfiniteTransition.animateFloat`、`Modifier.clip`、`Modifier.alpha`、`AnnotatedString.withStyle`、`layout.Box`。这些名字在 IDE 里"点一下就有补全"，手写或子代理照猫画虎时漏 import **语法完全合法**，只有编译报 `Unresolved reference 'x' on receiver of type 'y'`。
2. **错包 import**（2 例）：`android.annotation.RequiresApi`（实际在 `androidx.annotation`）、`androidx.compose.ui.text.TextUnit`（实际在 `ui.unit`）。名字对、包不对，同样是编译才炸。
3. **臆造 API 参数名**（1 例）：`detectHorizontalDragGestures(onHorizontalDragChange = …)` —— 真实参数叫 `onHorizontalDrag`。子代理"按语义猜名字"就会踩。
4. **作用域 receiver 误判**（1 例）：`offset { }` 块里的 receiver 是 `Density`，没有 `size`；写 `size.width` 编译报"Function invocation 'size(...)' expected"。
5. **字面量/语法边界**（3 例）：`const val` 持有 `.dp`；`"…%2$d"` 的 `$d` 被当模板；KDoc 里写 `model/**` 里的 `/*` 触发 Kotlin **嵌套块注释**，文末 `*/` 只回退一层 → 整段代码被吞（最阴的一种，报的是 `Unclosed comment`，位置却在 1257 行）。

另有 2 例属**架构漂移**：`MainActivity` 调 `ExploreInkFragment.compressExplore()` 而该方法在 D25 重写时消失；`BookshelfScreen` 的 `OverlayClearButton(onClear, modifier)` 尾随 lambda 错落 `modifier` 位。

## 三、沉淀（已落地的自动化，不再靠人肉）

新增 `data_scripts/check-ext-imports.py`，接入 `rule-gate.sh ⑫`（GATE 不过不许 push）。它做四件事：

1. **扩展函数 import 守卫**：点调用形态 + KEXT 白名单（30 个常见 Compose 扩展），剔注释/字符串，只查含 `androidx.compose` 的文件（避免旧 View API 假报），`matchParentSize` 等 BoxScope 成员不查。
2. **错包 import 守卫**：`WRONG_IMPORT` 名单，命中即红。
3. **块注释嵌套守卫**：块注释体内出现 `/*` 即红（Kotlin 嵌套注释）。
4. **`const val` 非编译期常量守卫**：`const val … = ….dp/.sp/Color()/listOf()` 即红；另查 **16 位十六进制字面量**（≥2^63）缺 `L/u` 后缀。

设计取舍（为什么这么定）：
- 只查"必然红"的规则，不查风格问题 —— 门禁一旦出现假报就会被绕过，可信度归零；本轮 88 条假报（8 位色值）就是被我即时收窄成"仅 16 位"的。
- 不替代编译：沙箱禁 gradle/JDK21，唯一编译来源是 CI，故静态守卫是**前置拦截**，CI 仍是终裁。

## 四、仍然无法机械化的两件事（留给派单纪律）

- **臆造 API 参数名 / 作用域 receiver**：只能靠"派单时贴真实签名 + 要求逐个比对 import 与调用点"。
- **架构漂移（方法被重写删掉）**：只能靠"模块改动必须同步 grep 全仓调用点"。

这两条已写进本轮修红派单与报告模板要求；后续派单沿用。

## 五、本轮修红结果

| commit | 内容 |
|---|---|
| `d264ff6` | 8 文件：CampaignController 嵌套注释、CrossingShell 3 处、HeightMapDecoder 5 常数、CardBase/TopicCard import、EmptyStateCard 错包、两个 read dialog、BookshelfScreen 签名、ExploreInkFragment 补 compressExplore |
| `e53809e` | 卡片第 2 轮 3 处：BookCard 手势参数名、EmptyStateCard `Box` import、SearchResultCard `withStyle` import |
| `8d4e604` | ShareCard `RequiresApi` 错包 |
| 本轮（脚本） | `check-ext-imports.py` 四类守卫 + rule-gate ⑫ |
