# 锚点卡 · Q-282 清除 ReaderProvider（跨 App ContentProvider 封死）

> 对象：`app/src/main/AndroidManifest.xml` provider 块删除；`api/ReaderProvider.kt`、
> `api/ReaderProviderRoutes.kt` 整文件删除；`ShortCuts.buildShortCuts` 迁入 `App.onCreate`；
> 路由/dispatch 单测与 Provider 仪器测试随删；契约测试改为"不得重新注册"正向断言。
> 回执报告：`docs/修复-清除ReaderProvider.md`。

> **口径已失效（2026-11-26）**：`ReaderProvider` 已封死并整体清除（Q-282，见 `docs/修复-清除ReaderProvider.md`）；旧关于页 `AboutActivity` 已整体删除（Q-280，见 `docs/修复-删除关于页.md`）。本段保留为历史记录，勿据以施工。

## 一、事实锚点（逐字，含 文件:行）

- 裁决：不做跨 App 互通；Provider 直接封死；16 条路由随 dispatch 清除；书源自动更新走 App 内部
  OkHttp 拉 JSON，不经 Provider。
- 删除文件 4 个：
  - `app/src/main/java/io/legado/app/api/ReaderProvider.kt`（原 `:21` `class ReaderProvider`，
    `:38` 原 `ShortCuts.buildShortCuts(context)`，`:49/:63/:94` 三个 dispatch 调用）。
  - `app/src/main/java/io/legado/app/api/ReaderProviderRoutes.kt`（`:3` 17 值枚举，`:30-47` 16 条路由，
    `:64/:77/:99` 三个 `dispatchReaderProvider*`）。
  - `app/src/test/java/io/legado/app/api/ReaderProviderRoutesTest.kt`。
  - `app/src/androidTest/java/io/legado/app/ExampleInstrumentedTest.kt`（唯一用例
    `readerProviderReturnsRssCursor`，原 `:25` authority `${appContext.packageName}.readerProvider`）。
- 改动文件 3 个：
  - `AndroidManifest.xml`：原 `:666-670` `.api.ReaderProvider` 块（`exported="true"`，
    authority `${applicationId}.readerProvider`）整块删除；上邻 `MediaButtonReceiver` receiver
    （现 `:653-660`）与下邻 `FileProvider`（现 `:662-670`）完整保留。
  - `app/src/main/java/io/legado/app/App.kt`：`:18` `import io.legado.app.api.ShortCuts`；
    `:90-93` `runCatching { ShortCuts.buildShortCuts(this) }.onFailure { AppLog.put(...) }`。
  - `app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt`：
    旧"Provider 保持 true"断言（原 `:69-72`）改写为 `reader provider removed and must not be
    re-declared`，遍历全部 `<provider>` 断言 `.api.ReaderProvider` 不在名单。
- 保留三件套：`BookController.kt` / `BookSourceController.kt` / `RssSourceController.kt` 类与方法
  全部保留；复用点均在 loopback 链路——`HttpServer.kt`（BookController `:91,92,93,94,98,145,146,147,148,152,153,154`；
  BookSourceController `:62,66,82,85,86,90,126,137,138,142`；RssSourceController `:104,105,106,155,156`）、
  `WebSocketServer.kt:19`、`McpApplication.kt:27`、`McpToolServer.kt:257`。无 Provider 专用方法可删。
- 快捷方式目标在位：`ShortCuts.kt:21` `MainActivity`、`:31` `SharedReceiverActivity`；
  `SharedReceiverActivity.kt:38` `readAloud` 分支仍调 `MediaButtonReceiver.readAloud`。

## 二、参数表

- 路由 16 条（`bookSource/bookSources/rssSource/rssSources/book/books` 各 insert/delete/query 与
  `book/refreshToc`、`chapter`、`content`、`cover`），清单见回执报告 §四。
- 枚举 17 值：16 值有路由；`SaveBookProgress` 无路由，死分支随表删除。
- `api/` 包删后剩余：`ReturnData.kt`、`ShortCuts.kt`、`controller/` 7 个控制器，不成空包。
- 自动更新链路：`MainViewModel.kt:110-118` → `RuleUpdate.cacheSource` →
  `okHttpClient.newCallResponseBody`（`RuleUpdate.kt:39-46`）→ GSON 解析 →
  `SourceHelp.insertBookSource/insertRssSource`（`:60/:79`）；`RuleUpdate.kt` 无 Controller/Provider import。

## 三、结论锚点

- C1 Provider 注册块、类、路由表、枚举、三 dispatch、两组测试全部清除；manifest 中该 authority 零命中。
- C2 动态快捷方式重建入口由 Provider.onCreate 迁至 `App.onCreate`（`App.kt:92`），单调用点 grep 回证；
  装配失败 `runCatching` + `AppLog` 兜底，不拖垮启动。
- C3 三件套 controller 保留给 loopback Web / WebSocket / MCP；自动更新与导入视图不经过 Provider，功能不受影响。
- C4 未编译（沙箱禁 JDK）：Kotlin 仅过 `kotlin-syntax-lint.py`，manifest 仅过 XML 解析；
  编译红绿、单测全绿以云端 CI 为准。

## 四、机检末行（本单收口）

- `grep -rn "dispatchReaderProvider" app/src/`：零命中（exit=1）。
- `grep -n "ShortCuts.buildShortCuts" -r app/src/main`：仅 `App.kt:92` 一处。
- `grep -n "readerProvider\|\.api\.ReaderProvider" app/src/main/AndroidManifest.xml`：零命中（exit=1）。
- `grep -c "AboutActivity" app/src/main/AndroidManifest.xml`：0（另一在飞单处置，本单未动）。
- Kotlin 预检末行：`KOTLIN-LINT: PASS（6 个文件）`（exit=0）。
- manifest 解析末行：`XML OK`。
- 残留说明：`ReaderProvider` 字样仅存于 `ManifestExportedContractTest.kt:13,72,80,81`，为正向安全
  断言组件名与其注释 / 文案；生产代码、authority、import 零命中。

## 五、回源

- 回执报告：`docs/修复-清除ReaderProvider.md`（§二 引用取证、§三 迁移证据、§四 16 条路由、
  §五 controller 复用点、§六 自动更新反向验证、§七 收口回证、§八 反例三条、§九 二阶三阶影响、
  §十 新发现问题处置状态）。
- 历史文档待回写清单：见回执报告 §9.2（`api.md:261-368`、Q239/Q241 锚点、exported 审计、挂死短名单等）。
