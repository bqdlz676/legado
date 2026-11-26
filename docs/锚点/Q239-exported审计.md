# 锚点卡 · Q-239 exported=true 16 处安全审计（只读判定）

> 对象：`app/src/main/AndroidManifest.xml` 全部 16 处 `android:exported="true"`；回执报告 `docs/审计-exported16处.md`。本单只读，未改 manifest。

## 一、事实锚点（逐字，含 文件:行）

- 必要 启动器：`AndroidManifest.xml:47` `WelcomeActivity`（`:50-54` MAIN+LAUNCHER）；`:58/:71/:84/:97/:110/:123` `Launcher1~6`，均 `enabled="false"` + MAIN+LAUNCHER（`:63-67` 等）。
- 必要 分享/深链/文件：`:434` `SharedReceiverActivity`（`:438-451` PROCESS_TEXT/SEND text/plain）；`:455` `OnLineImportActivity`（`:465-466` scheme legado/yuedu，深链格式 `OnLineImportActivity.kt:18`）；`:481` `FileAssociationActivity`（`:485-601` SEND/SEND_MULTIPLE/VIEW）。
- 必要 系统绑定：`:624` `WebTileService`（`:629` `BIND_QUICK_SETTINGS_TILE`，`:632` QS_TILE）；`:654` `AutoTaskJobService`（`:656` `BIND_JOB_SERVICE`）；`:659` `MediaButtonReceiver`（`:662` MEDIA_BUTTON，实现 `MediaButtonReceiver.kt:29`）。
- 可疑 ⑭ `:136` `MainActivity`：`:135-141` 无 filter 无 permission；拉起方 `WelcomeActivity.kt:48`、`ShortCuts.kt:21`、`SharedReceiverActivity.kt:55`、`ShelfSearchHandoff.kt:75` 均内部。
- 可疑 ⑮ `:184` `ReadBookActivity`：`:182` 注释 Legacy 横排通道；主流程走 `ContextExtensions.kt:74` `InkReadActivity::class.java`，main 源未见显式拉起。
- 可疑 ⑯ `:667` `ReaderProvider`：`:666-671` 无 permission；`ReaderProvider.kt:43/60/78` delete/insert/query 无 callingPackage 校验；路由 `ReaderProviderRoutes.kt:31-46` 暴露书架/正文/源读写删。

## 二、参数表

- 计数：必要 13、可疑 3、待验证独立项 0；其余 5 个 module manifest exported=true 均 0 处。
- 可疑改法：⑭⑮ 倾向 `exported="false"`（待运行期确认）；⑯ 禁直接关 exported，走 signature permission 或 callingPackage 签名比对。

## 三、结论锚点

- C1 七个 launcher/备用图标、文本分享、深链、文件打开、磁贴、定时任务、媒体按键为系统/桌面/外部交互所必需，保留导出。
- C2 MainActivity 仅内部拉起，关闭 exported 不影响同进程显式 Intent；风险为未发现的外部类名直拉。
- C3 ReadBookActivity 生产拉起路径未在 main 源找到，降级通道归属待运行期确认。
- C4 ReaderProvider 是对外互通 API 设计，但当前无鉴权，任意 App 可读书架正文、写删源；鉴权方案待产品裁决。静态核查，未跑 CI，红绿以云端为准。

## 四、回源

- 回执报告：`docs/审计-exported16处.md`（§二 16 条逐条判定、§三 汇总、§四 新发现问题处置状态、§五 未验证项）。
