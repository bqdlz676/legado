# 修复 · Q-282 清除 ReaderProvider（跨 App ContentProvider 封死）

锚点卡：docs/锚点/Q282-ReaderProvider.md

> 裁决（最终，不再讨论互通价值）：1) 不做跨 App 互通；2) `ReaderProvider` 直接封死；3) 16 条路由与
> `api/controller` 三件套中只被 Provider 使用的部分一并清除——死代码留着就是攻击面；4) 书源自动更新走
> App 内部下载 JSON，不经 ContentProvider，不受影响（本报告 §六 为反向验证判据）。
>
> 沙箱无 JDK：未跑 `./gradlew` / `kotlinc` / 任何编译；Kotlin 改动只过 `kotlin-syntax-lint.py`，
> manifest 只过 XML 解析；红绿以云端 CI 为准。未做 git 写。

> **口径已失效（2026-11-26）**：`ReaderProvider`（含 `readerProvider` authority、`ReaderProviderRoutes` 路由表/分发、`ReaderProviderRoutesTest`）已封死并整体清除（Q-282，即本报告）；旧关于页 `AboutActivity` 已整体删除（Q-280，见 `docs/修复-删除关于页.md`）。本段保留为历史记录，勿据以施工。

## 一、任务与产物

### 1.1 改动文件（3 改 4 删）

改动：

- `app/src/main/AndroidManifest.xml`：删除 `.api.ReaderProvider` 的 `<provider>` 注册块（原 `:666-670`）。
- `app/src/main/java/io/legado/app/App.kt`：`ShortCuts.buildShortCuts(this)` 由 Provider.onCreate 迁入
  `App.onCreate`（`:18` import，`:90-93` 调用点）。
- `app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt`：原"Provider 保持 true"
  断言改为正向安全断言——manifest 中不得再声明 `.api.ReaderProvider`。

删除：

- `app/src/main/java/io/legado/app/api/ReaderProvider.kt`（ContentProvider 本体）。
- `app/src/main/java/io/legado/app/api/ReaderProviderRoutes.kt`（17 值枚举 + 16 条路由 + 三个 dispatch）。
- `app/src/test/java/io/legado/app/api/ReaderProviderRoutesTest.kt`（路由表/dispatch 单测，随路由表删除）。
- `app/src/androidTest/java/io/legado/app/ExampleInstrumentedTest.kt`（唯一用例 `readerProviderReturnsRssCursor`
  直连 Provider authority，整文件随 Provider 删除）。

### 1.2 保留文件（非 Provider 专用，有复用调用点）

- `app/src/main/java/io/legado/app/api/ShortCuts.kt`：唯一快捷方式装配器，调用点已迁到 `App.kt:92`。
- `app/src/main/java/io/legado/app/api/ReturnData.kt`：Web/MCP/controller 链路共用返回包装，保留。
- `api/controller/BookController.kt`、`BookSourceController.kt`、`RssSourceController.kt`：类整体保留，
  方法级逐条核对复用点（§五），无 Provider 专用方法可删。
- 其余 4 个 controller（`BookSourceCheckController`、`HttpLogController`、`ReplaceRuleController`、
  `ReviewController`）只被 loopback HttpServer / MCP 调用，本单不碰。

## 二、引用取证（改动前逐条 文件:行）

取证范围：`app/src/main`、`app/src/test`、`app/src/androidTest`、`app/src/main/res`。

### 2.1 `ReaderProvider`（类名）

- `app/src/main/AndroidManifest.xml:667` `android:name=".api.ReaderProvider"`。
- `app/src/main/java/io/legado/app/api/ReaderProvider.kt:21` `class ReaderProvider : ContentProvider()`。
- `app/src/main/java/io/legado/app/api/ReaderProvider.kt:26,33,34,49,63,94`（Routes / dispatch 调用）。
- `app/src/main/java/io/legado/app/api/ReaderProviderRoutes.kt:3,23,25,28,31-46,52,54,58,60,61,65,71,72,78,81-86,89-95,100,103,105,107,109,110,112,115,118,121,125-134,138,139`。
- `app/src/test/java/io/legado/app/api/ReaderProviderRoutesTest.kt:10,15-30,33,35,41,48,58,59,74,75,89,90,94,95,107,108,121,122,134,135,142,143`。
- `app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt:12,71`（旧 true 断言，已改写）。
- `app/src/androidTest/java/io/legado/app/ExampleInstrumentedTest.kt:23,29,53`。

### 2.2 `ReaderProviderRoutes` / `ReaderProviderRequestCode`

- `ReaderProvider.kt:26` `ReaderProviderRoutes.all.forEach`；`:34` `requestForMatcherCode`。
- `ReaderProviderRoutes.kt:3` `internal enum class ReaderProviderRequestCode`；`:23`
  `internal data class ReaderProviderRoute`；`:28` `internal object ReaderProviderRoutes`。
- `ReaderProviderRoutesTest.kt:33,35,41,48` 路由表/匹配码断言。

### 2.3 `readerProvider`（小写 authority 字符串）

- `AndroidManifest.xml:668` `android:authorities="${applicationId}.readerProvider"`。
- `ReaderProvider.kt:25` `"${context?.applicationInfo?.packageName}.readerProvider"`。
- `ExampleInstrumentedTest.kt:25` `"${appContext.packageName}.readerProvider"`。
- `app/src/main/res`：零命中。

### 2.4 `dispatchReaderProvider`（delete / insert / query 三个分发）

- `ReaderProvider.kt:49` `dispatchReaderProviderDelete`；`:63` `dispatchReaderProviderInsert`；`:94`
  `dispatchReaderProviderQuery`。
- `ReaderProviderRoutes.kt:64` / `:77` / `:99` 三个 `internal fun` 定义。
- `ReaderProviderRoutesTest.kt:58,74,89,94,107,121,134,142` 分发单测。

### 2.5 三件套 controller（改动前引用点）

- `BookController`：`ReaderProvider.kt:13,71,72,101,102,103,104,105`；
  `app/src/main/java/io/legado/app/web/HttpServer.kt:6,91,92,93,94,98,145,146,147,148,152,153,154`；
  `app/src/test/java/io/legado/app/api/controller/BookControllerImageIsolationTest.kt:8,12`；
  `app/src/test/java/io/legado/app/config/AutoTaskPersistenceContractTest.kt:252`（协议不含 `refreshToc` 的反向断言）。
- `BookSourceController`：`ReaderProvider.kt:14,52,67,68,97,98`；
  `HttpServer.kt:7,62,66,82,85,86,90,126,137,138,142`；
  `app/src/main/java/io/legado/app/web/WebSocketServer.kt:4,19`；
  `app/src/main/java/io/legado/app/web/mcp/McpApplication.kt:11,27`；
  `app/src/main/java/io/legado/app/web/mcp/McpToolServer.kt:5,257`；
  `app/src/test/java/io/legado/app/api/JsSourceWebApiContractTest.kt` 多处；
  `app/src/test/java/io/legado/app/web/mcp/McpServiceContractTest.kt:29`。
- `RssSourceController`：`ReaderProvider.kt:15,53,69,70,99,100`；
  `HttpServer.kt:12,104,105,106,155,156`。

### 2.6 `buildShortCuts` / `ShortCuts`

- `ReaderProvider.kt:38` `ShortCuts.buildShortCuts(context)`（原唯一调用点）。
- `app/src/main/java/io/legado/app/api/ShortCuts.kt:12` `object ShortCuts`；`:41` `fun buildShortCuts`。

## 三、ShortCuts 迁移（先接后删，回证在前）

### 3.1 迁移必要性取证（非凭直觉）

- 全 main 源码 `ShortcutManagerCompat` / `setDynamicShortcuts` / `ShortcutInfoCompat` 仅出现在
  `ShortCuts.kt:5,6,20,22,30,33,42`；`buildShortCuts` 改动前唯一调用点是 `ReaderProvider.kt:38`。
- 即：若直接删 Provider 而不迁调用点，书架 / 朗读两条动态快捷方式将失去唯一重建入口，且不抛任何异常
  （系统保留旧快捷方式直到被清除/重装，表现为静默消失）。迁移不是可选项。
- 架构核对：`AndroidManifest.xml:33` `android:name=".App"`；全 main 仅 `App.kt` 一个 `Application()`
  子类，无 D 类第二实现，单落点迁移即可。

### 3.2 接入点（先接）

`App.kt`：

- `:18` `import io.legado.app.api.ShortCuts`。
- `:90-93`（`onCreate` 同步段，`registerActivityLifecycleCallbacks(LifecycleHelp)` 之后）：

```kotlin
// 动态快捷方式原由已移除的跨 App ContentProvider.onCreate 承载；封死后迁此重建。
// 快捷方式失败属可降级项，runCatching 兜底，绝不把启动拖成崩溃。
runCatching { ShortCuts.buildShortCuts(this) }
    .onFailure { AppLog.put("重建桌面快捷方式失败", it) }
```

- 兜底口径对齐本仓启动韧性约定（参照 `App.kt:97-99` Cronet 预下载 `runCatching` + `AppLog.put`）：
  快捷方式重建失败记日志，不阻断 `onCreate` 后续链路。

### 3.3 回证（删 Provider 之前已确认新调用点存在）

- `grep -n "ShortCuts.buildShortCuts" -r app/src/main` 迁移后、删除前：`App.kt:92` 与 `ReaderProvider.kt:38`
  两处并存；Provider 文件删除后收口仅剩 `App.kt:92` 一处（§七 E3）。
- 快捷方式目标仍在位：`ShortCuts.kt:21` `MainActivity`、`:31` `SharedReceiverActivity`；
  `SharedReceiverActivity.kt:38` `action == "readAloud"` 分支仍调 `MediaButtonReceiver.readAloud`；
  manifest `:430` 仍注册 `SharedReceiverActivity`。端到端行为不变。

## 四、16 条路由清单（随 `ReaderProviderRoutes.kt` 整文件删除）

路由表改动前位于 `ReaderProviderRoutes.kt:30-47`；枚举 17 值（含无路由的 `SaveBookProgress`，§八 N1）。

| # | path | `ReaderProviderRequestCode` | Provider 内 controller 回调 |
|---|------|------------------------------|-----------------------------|
| 1 | `bookSource/insert` | `SaveBookSource` | `BookSourceController.saveSource` |
| 2 | `bookSources/insert` | `SaveBookSources` | `BookSourceController.saveSources` |
| 3 | `bookSources/delete` | `DeleteBookSources` | `BookSourceController.deleteSources` |
| 4 | `bookSource/query` | `GetBookSource` | `BookSourceController.getSource` |
| 5 | `bookSources/query` | `GetBookSources` | `BookSourceController.sources` |
| 6 | `rssSource/insert` | `SaveRssSource` | `RssSourceController.saveSource` |
| 7 | `rssSources/insert` | `SaveRssSources` | `RssSourceController.saveSources` |
| 8 | `rssSources/delete` | `DeleteRssSources` | `RssSourceController.deleteSources` |
| 9 | `rssSource/query` | `GetRssSource` | `RssSourceController.getSource` |
| 10 | `rssSources/query` | `GetRssSources` | `RssSourceController.sources` |
| 11 | `book/insert` | `SaveBook` | `BookController.saveBook` |
| 12 | `books/query` | `GetBookshelf` | `BookController.bookshelf` |
| 13 | `book/refreshToc/query` | `RefreshToc` | `BookController.refreshToc` |
| 14 | `book/chapter/query` | `GetChapterList` | `BookController.getChapterList` |
| 15 | `book/content/query` | `GetBookContent` | `BookController.getBookContent` |
| 16 | `book/cover/query` | `GetBookCover` | `BookController.getCover` |

枚举第 17 值 `SaveBookProgress`（`ReaderProviderRoutes.kt:20`）在 `all` 路由表中无对应 path，
dispatch 分支只可能由 Provider 内部构造码命中；随路由表一并删除。

## 五、controller 分类处置（b 类：混在同类内逐条核对，不整类删）

判定口径：方法若仅被 Provider 回调使用则删；被 loopback HttpServer / WebSocket / MCP / 测试复用则保留，
调用点逐条贴证。结论：**三件套无 Provider 专用方法，类与方法全部保留。**

### 5.1 `BookController.kt`（类保留）

| 方法 / 属性 | Provider 回调点（已随 Provider 删除） | 非 Provider 复用点（保留依据） |
|---|---|---|
| `bookshelf` | `ReaderProvider.kt:101` | `HttpServer.kt:145` `/getBookshelf` |
| `getCover` | `ReaderProvider.kt:105` | `HttpServer.kt:152` `/cover` |
| `getImg` | —（本就非 Provider 路由） | `HttpServer.kt:153` `/image`；`BookControllerImageIsolationTest.kt` |
| `refreshToc` | `ReaderProvider.kt:103` | `HttpServer.kt:147` `/refreshToc` |
| `getChapterList` | `ReaderProvider.kt:104` | `HttpServer.kt:146` `/getChapterList` |
| `getBookContent` | `ReaderProvider.kt:102` | `HttpServer.kt:148` `/getBookContent` |
| `saveBook` | `ReaderProvider.kt:71` | `HttpServer.kt:91` `/saveBook` |
| `deleteBook` | — | `HttpServer.kt:92` `/deleteBook` |
| `saveBookProgress` | `ReaderProvider.kt:72` | `HttpServer.kt:93` `/saveBookProgress` |
| `addLocalBook` | — | `HttpServer.kt:94` `/addLocalBook` |
| `saveWebReadConfig` | — | `HttpServer.kt:98` `/saveReadConfig` |
| `getWebReadConfig` | — | `HttpServer.kt:154` `/getReadConfig` |
| `requireSafeUploadedBookFileName`（文件级私有校验） | — | 同文件 `addLocalBook` 内 `:314` 调用 |

### 5.2 `BookSourceController.kt`（类保留）

| 方法 / 属性 | Provider 回调点（已删） | 非 Provider 复用点（保留依据） |
|---|---|---|
| `sources` | `ReaderProvider.kt:98` | `HttpServer.kt:138` `/getBookSources` |
| `saveSource` | `ReaderProvider.kt:67` | `HttpServer.kt:82` `/saveBookSource` |
| `saveSources` | `ReaderProvider.kt:68` | `HttpServer.kt:85` `/saveBookSources` |
| `saveJsSource` | — | `HttpServer.kt:86` `/saveJsSource`；`McpToolServer.kt:257` |
| `validateJsSourceRequest` | — | `HttpServer.kt:62`；`JsSourceWebApiContractTest.kt:65,97,103,109,115,150,156` |
| `hasValidJsSourceApiToken` | — | `HttpServer.kt:66,126`；`JsSourceWebApiContractTest.kt:66,122,129,136,143` |
| `hasValidJsSourceWebSocketProtocol` | — | `WebSocketServer.kt:19`；`JsSourceWebApiContractTest.kt:73,79,85,91` |
| `jsSourceWebSocketProtocol`（internal） | — | 同文件 `hasValidJsSourceWebSocketProtocol` 内部；`JsSourceWebApiContractTest.kt:69,71` |
| `matchesJsSourceApiToken` | — | `McpApplication.kt:27`；`McpServiceContractTest.kt:29` |
| `isJsSourceApiTokenRequired` | — | `HttpServer.kt:142` |
| `getSource` | `ReaderProvider.kt:97` | `HttpServer.kt:137` `/getBookSource` |
| `deleteSources` | `ReaderProvider.kt:52` | `HttpServer.kt:90` `/deleteBookSources` |

### 5.3 `RssSourceController.kt`（类保留）

| 方法 / 属性 | Provider 回调点（已删） | 非 Provider 复用点（保留依据） |
|---|---|---|
| `sources` | `ReaderProvider.kt:100` | `HttpServer.kt:156` `/getRssSources` |
| `saveSource` | `ReaderProvider.kt:69` | `HttpServer.kt:104` `/saveRssSource` |
| `saveSources` | `ReaderProvider.kt:70` | `HttpServer.kt:105` `/saveRssSources` |
| `getSource` | `ReaderProvider.kt:99` | `HttpServer.kt:155` `/getRssSource` |
| `deleteSources` | `ReaderProvider.kt:53` | `HttpServer.kt:106` `/deleteRssSources` |

### 5.4 测试处置

- `ReaderProviderRoutesTest.kt`：整文件删除——被测对象（路由表 / 枚举 / 三个 dispatch）已不存在。
- `ExampleInstrumentedTest.kt`：整文件删除——唯一用例直连 `${packageName}.readerProvider` 三件套
  （insert / query / delete），Provider 封死后该 authority 不存在，用例必然失败。
- `ManifestExportedContractTest.kt`：原 `:69-72` 断言"Provider 保持 true"改写为
  `reader provider removed and must not be re-declared`：解析 manifest 全部 `<provider>`，
  断言 `.api.ReaderProvider` 不在名单（无论 exported 取何值都不得重新声明）。断言未删除，口径由
  "保持导出"翻为"不得注册"的正向安全断言。
- 其余 controller 测试（`BookControllerImageIsolationTest`、`JsSourceWebApiContractTest`、
  `HttpLogControllerTest`、`ReviewWebApiContractTest`、`McpServiceContractTest`）被测类与方法均保留，未改。

## 六、书源自动更新反向验证（不依赖 Provider 与三件套）

### 6.1 真实实现链路

- 触发：`app/src/main/java/io/legado/app/ui/main/MainViewModel.kt:110-118` `ruleSubsUp()`：
  遍历 `appDb.ruleSubDao.all`，按 `ruleSub.autoUpdate` 过滤，调 `RuleUpdate.cacheSource(ruleSub)`。
- 下载与解析：`app/src/main/java/io/legado/app/model/RuleUpdate.kt`
  - `:39-45` `okHttpClient.newCallResponseBody { url(url) … }`（App 内部 OkHttp 直拉 JSON）；
  - `:46` `.decompressed().byteStream()`；
  - `:48` / `:70` / `:88` `GSON.fromJsonArray<BookSource / RssSource / ReplaceRule>`；
  - `:60` `SourceHelp.insertBookSource(list)`；`:79` `SourceHelp.insertRssSource(list)`；
    `:98` `appDb.replaceRuleDao.insert(...)`；`:117` `ContentProcessor.upReplaceRules()`。
- 入库：`app/src/main/java/io/legado/app/help/source/SourceHelp.kt:129` `insertRssSource`、
  `:141` `insertBookSource`，直写 DAO。

### 6.2 依赖关系取证

- `RuleUpdate.kt:1-19` import 清单：`appDb`、实体、`ContentProcessor`、`ReplacePreviewConfig`、
  `okHttpClient` / `newCallResponseBody` / `decompressed`、`SourceHelp`、`requireSourceUrl`、`GSON`、
  `fromJsonArray`；**无任何 `Controller`、无 `Provider`、无 `ContentResolver`**
  （`grep -n "Controller\|Provider" app/src/main/java/io/legado/app/model/RuleUpdate.kt` 零命中）。
- 三件套 controller 的 `saveSource / saveSources / deleteSources / getSource / sources` 只被
  loopback `HttpServer.kt` 路由触达（§五），自动更新链路不经其手。
- 结论：书源 / 订阅源 / 替换规则的订阅与静默更新在 Provider 封死后功能不变；本单未改自动更新任何代码。

## 七、收口验证（真实机检末行）

### 7.1 符号 grep

```
$ grep -rn "ReaderProvider\|readerProvider" app/src/
app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt:13: * - ReaderProvider 已整体清除（Q-282）：manifest 不得再声明该 provider，
app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt:72:        // 正向安全断言：Q-282 裁决不做跨 App 互通，ReaderProvider 整体清除；
app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt:80:            "ReaderProvider 已封死清除，manifest 不得再声明 .api.ReaderProvider",
app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt:81:            providerNames.firstOrNull { it == ".api.ReaderProvider" }
```

生产代码、authority 字符串、import、类引用零命中；残留 4 行全部在契约测试内，是 §5.4  mandated 的
正向安全断言与其注释 / 报错文案（断言目标串必须写出组件名才能禁止其重新注册）。

```
$ grep -rn "dispatchReaderProvider" app/src/
（零命中，exit=1）

$ grep -n "ShortCuts.buildShortCuts" -r app/src/main
app/src/main/java/io/legado/app/App.kt:92:        runCatching { ShortCuts.buildShortCuts(this) }

$ grep -n "readerProvider\|\.api\.ReaderProvider" app/src/main/AndroidManifest.xml
（零命中，exit=1）

$ grep -c "AboutActivity" app/src/main/AndroidManifest.xml
0
```

`AboutActivity` 计数不归本单（另一张在飞单处置），本单未触碰其 activity 块。

附加零命中：`ReaderProviderRoutes` / `ReaderProviderRequestCode` / `ReaderProviderRoute` /
`requestForPath` / `requestForMatcherCode` / `requestByPath` / `unexpectedReaderProviderRequest` /
`ReaderProviderRoutesTest` / `ExampleInstrumentedTest` 在 `app/src/` 全部零命中；
`data_scripts/`、`.github/` 内 `ReaderProvider` 零命中；`app/src/main/res` 零命中。

### 7.2 Kotlin 语法预检

```
$ python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/App.kt \
    app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt \
    app/src/main/java/io/legado/app/api/ShortCuts.kt \
    app/src/main/java/io/legado/app/api/controller/BookController.kt \
    app/src/main/java/io/legado/app/api/controller/BookSourceController.kt \
    app/src/main/java/io/legado/app/api/controller/RssSourceController.kt
KOTLIN-LINT: PASS（6 个文件）
```

末行：`KOTLIN-LINT: PASS（6 个文件）`，退出码 0。

### 7.3 manifest XML 解析

```
$ python3 -c "import xml.dom.minidom;xml.dom.minidom.parse('app/src/main/AndroidManifest.xml');print('XML OK')"
XML OK
```

末行：`XML OK`。

### 7.4 边界完整性

- Provider 块上邻：`MediaButtonReceiver` receiver 块（`manifest:653-660`）完整保留；
- Provider 块下邻：`FileProvider` provider 块（`manifest:662-670`）完整保留；
- 本次 manifest 改动为精确单块 Edit（按 `<provider>` 起始行与 `/>` 收尾行锚定），未整文件重写，
  未用 `sed -i`；`tools` 命名空间仍被 `:20` / `:44` / `:626` 使用，`xmlns:tools` 保留。

## 八、反例推演（必写三条）

### 8.1 删 controller 后 UI 仍有引用 → 编译红

- 风险：若三件套中某方法仍被 UI / 导入导出 / 自动更新调用，整类或整方法删除后编译 unresolved reference。
- 本单处置：类与方法均未删，仅删除 Provider / Routes / dispatch 本体；每条保留方法的非 Provider 调用点
  已在 §五 逐条贴 `文件:行`。
- 沙箱无 JDK 的替代验证：
  1. 全仓符号 grep（main / test / androidTest / res），被删符号零命中（§7.1）；
  2. 保留 controller 的引用方 grep 到具体路由行（`HttpServer.kt` / `WebSocketServer.kt` /
     `McpApplication.kt` / `McpToolServer.kt`）；
  3. 静态盲区：反射、字符串路由、JS 桥按名调方法不在 grep 视野内——仓内 MCP 调用为直接方法引用
     （`McpToolServer.kt:257` 等），未见按类名字符串反射装配；最终 unresolved 判定仍以云端 CI 编译为准。

### 8.2 封死后第三方工具 / 脚本静默断链，如何被发现

- 现象：外部 App / 脚本按 `content://<pkg>.readerProvider/...` 访问，Provider 注册块删除后系统侧返回
  unknown authority（`SecurityException` / 空 Cursor），断链发生在第三方一侧，本 App 不抛错。
- 本 App 侧排查手段：
  1. 仓内已 grep 确认无 App 内 `ContentResolver` 跨进程访问本 authority（残留 `content://` 命中仅为
     媒体/下载 URI 工具：`RealPathUtil.kt:44`、`CoverImageView.kt:643`、`StringExtensions.kt` 内容 scheme 判定）；
  2. 若未来日志 / 崩溃上报出现 `Unknown authority` 或 provider 未注册堆栈，归属即本封死动作；
  3. 快捷方式重建失败走 `AppLog.put("重建桌面快捷方式失败", it)`（`App.kt:93`），可在日志面观测；
  4. 契约测试 `ManifestExportedContractTest` 防止后续重构把该 provider 重新注册回来。

### 8.3 `buildShortCuts` 迁移漏做 → 功能静默消失且无报错

- 风险：Provider 删除后若无替代调用点，动态快捷方式不再重建；系统不报错，旧快捷方式存留至被清除，
  表现为用户侧"朗读 / 书架快捷方式没了"。
- 本单防护：
  1. 调用点先接后删，删除前 grep 确认 `App.kt:92` 已存在；收口 grep 确认全 main 仅此一处
     `buildShortCuts` 调用（§7.1 E3）；
  2. 装配器 `ShortCuts.kt` 与两个目标 Activity / Receiver 均在位（§3.3）；
  3. `runCatching` + `AppLog` 兜底，装配异常可见于日志而非静默吞掉。

## 九、二阶 / 三阶影响

### 9.1 `api/` 包是否成空包

不成空包。删除后 `app/src/main/java/io/legado/app/api/` 剩余：

- `ReturnData.kt`（Web / MCP / controller 共用返回包装）；
- `ShortCuts.kt`（快捷方式装配，调用点迁 `App.kt`）；
- `controller/` 7 个控制器（Book / BookSource / BookSourceCheck / HttpLog / ReplaceRule / Review / RssSource）。

### 9.2 安全模型文档表述需同步的位置清单（历史快照，本单不改，列入文档回写台账）

- `api.md:261-368`：整节 `Content Provider` 文档（authority、16 条 content URI 用法）对应已删组件，
  需后续文档单整节摘除。
- `docs/修复-exported两处收口.md:3,10,11,36,51,64,81`：记载"ReaderProvider 本单不动 / 保持 true"。
- `docs/审计-exported16处.md:92-96,103,110,117`：⑯ 号可疑项判定与"待裁 signature 权限"记录。
- `docs/审计-历史包袱.md:75,102,146,234`：`api/` 外部调用面与 Provider 相关描述。
- `docs/挂死嫌疑-静态.txt:12`、`docs/挂死嫌疑-静态短名单.md:52,73`、`docs/锚点/Q276-静态短名单.md:23`：
  `ReaderProviderRoutesTest` 条目已随测试删除，短名单需重扫刷新。
- `docs/机制-机检收口.md:22`：枚举条目误报说明引用 `ReaderProviderRequestCode`。
- `docs/落文-全规格备案.md:443,498`：`api/` 文件清单与控制器清单。
- `docs/锚点/Q239-exported审计.md:12,24`、`docs/锚点/Q241-exported收口.md:10,11`：审计 / 收口锚点。
- `docs/问题清单-附录-逐文件.md:1787`：manifest 逐文件条目。
- `docs/卡片-10-分享卡.md:45`：以 `ReaderProvider.kt` 为 framework import 先例的措辞。

### 9.3 其他连带核对

- `app/proguard-rules.pro:146` `-keep class io.legado.app.api.ReturnData{*;}`：`ReturnData` 仍被
  Web/MCP 链路使用，keep 规则保留，无需改；仓内不存在针对 `ReaderProvider` / Routes 的 keep 规则。
- manifest `xmlns:tools` 仍被其他 `tools:ignore` / `tools:targetApi` 使用，命名空间保留。
- 自动更新 / 导入视图（`ImportBookSourceViewModel`、`ImportRssSourceViewModel`、
  `ImportReplaceRuleViewModel`、`MainViewModel`）只依赖 `RuleUpdate` 与 `SourceHelp`，不受本单影响。

### 9.4 未来恢复互通的重建成本（台账记 + 判废理由）

- 重建物：Provider 类 + 路由表 / 枚举 / 三 dispatch + manifest authority 注册块 + 第三方鉴权方案
  （signature permission 或调用方签名校验），一个聚焦工单可恢复代码骨架；但重新导出前必须先过一轮
  调用方鉴权评审——这是本次判废封死的根因，不建兼容层、不留旧入口。

## 十、新发现问题

1. **判废**：`ReaderProviderRequestCode.SaveBookProgress`（原 `Routes.kt:20`）在 `all` 路由表中无对应
   path，是只存在于 dispatch 分支的死枚举值；随路由表删除，不保留、不补路由。
2. **已入队**：`api.md:261-368` Content Provider 整节文档描述已删组件，需后续文档单摘除，避免误导
   第三方按失效 authority 对接。
3. **已入队**：§9.2 所列历史审计 / 锚点 / 短名单文档仍记载"exported=true、待裁 signature 权限"，
   需文档回写单统一改为"Q-282 已封死清除"口径；`挂死嫌疑` 两份清单需重扫刷新已删测试条目。
4. **待裁**：loopback `HttpServer` / `WebSocketServer` / MCP 链路及其 7 个 controller 仍是本机
   网络面（非跨 App Provider）；是否对 loopback 绑定地址、令牌、CORS 再做一轮收口，超出本单范围，
   另派单评估。
5. **已闭环**：`proguard-rules.pro:146` 的 `ReturnData` keep 规则保留——`ReturnData` 仍被
   `HttpServer.kt`、`McpToolServer.kt` 与 7 个 controller 使用，不属于 Provider 死代码。

## 十一、未验证项

- 沙箱无 JDK，未跑编译 / 单测 / 仪器测试；Kotlin 仅过括号配平与条件尾逗号两级静态预检，
  manifest 仅过 XML 解析；编译红绿、单测全绿以云端 CI 为准。
- merged manifest 产物未覆盖；Provider 删除后第三方已安装 App 的断链现象无法在本环境实测。
- `ExampleInstrumentedTest.kt` 删除后，`app/src/androidTest` 其余用例不依赖该 authority（改动前
  grep 已确认仅该文件命中 `readerProvider`），仪器测试链路以 CI 真机/模拟器为准。

## 守则回执

- 顺序纪律：引用取证（§二）→ ShortCuts 先接后删并 grep 回证（§三）→ 分类处置（§四 §五）→
  收口 grep + lint / XML 回证（§七），未颠倒。
- 边界：未碰 `BookshelfManageActivity` / `RemoteBookActivity` / `AboutActivity`；未改 `data_scripts/**`、
  `.github/**`；未做 git 写；未整文件重写 manifest。
- 每条保留方法的复用调用点均贴 `文件:行`；未把 Provider 专用推断成复用，也未把复用方法当死代码删。
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 逐字引用 2：「不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。」（docs/机制-子代理必读.md:12）
- 逐字引用 3：「每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。」（docs/机制-子代理必读.md:18）
- 适用条款：R57 一条缺陷一单——只封死 Provider 及其专用路由/分发/测试，三件套 controller 方法级逐条核对复用点后保留；禁 git 写操作——未执行 add/commit/push/revert/checkout；禁本地编译——沙箱无 JDK，仅过 `kotlin-syntax-lint.py` 与 manifest XML 解析；grep/行号回证——§二 引用取证清单、§7.1 收口 grep 末行；先接后删——ShortCuts 调用点先迁 `App.kt:92` 回证后再删 Provider；未验证项——编译红绿、单测与第三方断链现象以云端 CI 为准（§十一）。
- 自查反例（改坏推演）：见 §八 三条反例推演——删 controller 后 UI 仍有引用致编译红、封死后第三方工具静默断链如何发现、`buildShortCuts` 迁移漏做致快捷方式静默消失。
