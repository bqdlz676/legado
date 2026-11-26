# 锚点卡 · Q-241 exported 两处收口（MainActivity / ReadBookActivity 转非导出）

> 对象：`app/src/main/AndroidManifest.xml` 两处 `exported` 收口 + 契约测试 `app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt`；回执报告 `docs/修复-exported两处收口.md`。

> **口径已失效（2026-11-26，Q-282）**：`ReaderProvider`（含 `readerProvider` authority、`ReaderProviderRoutes` 路由表/分发、`ReaderProviderRoutesTest`）已封死并整体清除，详见 `docs/修复-清除ReaderProvider.md`；本段保留为历史记录，勿据以施工。

## 一、事实锚点（逐字，含 文件:行）

- 改动 1：`AndroidManifest.xml:136` `.ui.main.MainActivity`，`:139` `android:exported="false"`；内部拉起 `WelcomeActivity.kt:48`、`ShortCuts.kt:21`、`SharedReceiverActivity.kt:55`、`ShelfSearchHandoff.kt:75`。
- 改动 2：`AndroidManifest.xml:184` `.ui.book.read.ReadBookActivity`，`:186` `android:exported="false"`；`:183-187` 无 intent-filter；主流程走 `ContextExtensions.kt:74` `InkReadActivity::class.java`；旧直达分支见 `WelcomeActivity.kt:20` 注释。
- 排查：main/modules 生产源 0 处 `ReadBookActivity::class` 拉起；22 处命中均在 `app/src/androidTest/`（如 `SharedFileImportTest.kt:850`）。
- 契约测试：`ManifestExportedContractTest.kt` 三用例——两处 false、13 处外部入口 true、`.api.ReaderProvider` 保持 true。
- 未动：`AndroidManifest.xml:667` `.api.ReaderProvider` 仍 `exported="true"`，signature 权限另案。

## 二、参数表

- 改后计数：exported=true 14（13 外部入口 + Provider），exported=false 14（含本次两处）。
- 13 处 true 清单：WelcomeActivity、Launcher1~6、SharedReceiverActivity、OnLineImportActivity、FileAssociationActivity、WebTileService、AutoTaskJobService、MediaButtonReceiver。

## 三、结论锚点

- C1 两处内部组件转非导出，同 uid 显式 Intent 不受影响；外部按类名直拉将被 SecurityException 拦截，仓内未见此外部路径。
- C2 分享 / 深链 / 文件打开 / 磁贴 / 定时任务 / 媒体按键入口仍导出，外部能力不断。
- C3 androidTest 启动非导出组件需 CI/真机复核；merged manifest 产物未覆盖。未编译，红绿以云端 CI 为准。

## 四、回源

- 回执报告：`docs/修复-exported两处收口.md`（§二 diff 与 grep 回证、§三 三条排查、§四 契约测试、§五 二阶三阶影响、§六 新发现问题处置状态）。
