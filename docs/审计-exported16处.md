# 审计 · AndroidManifest exported=true 16 处逐条判定（Q-239，M1 只读核查）
> 漂移重判：原锚已失效，本结论标注为已废（2026-11-27 W-436）——原因：被审计的 ReaderProvider 暴露面随 Q-282 整体删除，仓内零声明；现行 exported 正向断言以 app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt 为准，收口见 docs/修复-exported两处收口.md。

> 边界：本单为只读安全审计，**未改动 `app/src/main/AndroidManifest.xml` 任何一行**；未 git 写、未编译。判定依据为 manifest 声明 + 主源码拉起/路由证据，行号一律 `文件:行` 可回源。
> 锚点卡：docs/锚点/Q239-exported审计.md。

> **口径已失效（2026-11-26，Q-282）**：`ReaderProvider`（含 `readerProvider` authority、`ReaderProviderRoutes` 路由表/分发、`ReaderProviderRoutesTest`）已封死并整体清除，详见 `docs/修复-清除ReaderProvider.md`；本段保留为历史记录，勿据以施工。

## 一、核查范围与方法

- 扫描命令：`grep -n 'android:exported="true"' app/src/main/AndroidManifest.xml`，命中 **16 处**，行号：`:48`、`:60`、`:73`、`:86`、`:99`、`:112`、`:125`、`:139`、`:186`、`:435`、`:457`、`:482`、`:625`、`:655`、`:660`、`:670`。
- 其余 5 个 manifest 均为 0 处：`app/src/cronetRuntime/AndroidManifest.xml`、`app/src/pdfRuntime/AndroidManifest.xml`、`modules/book/src/main/AndroidManifest.xml`、`modules/rhino/src/main/AndroidManifest.xml`、`screenshot/src/main/AndroidManifest.xml`。
- 判定三档：**必要**（launcher / 桌面别名 / 系统广播或系统绑定且确需外部触发，已有权限守卫）、**可疑**（无 intent-filter 却 exported=true，或仅内部使用却对外暴露）、**待验证**（需运行期或全量字符串路由确认）。
- 分类计数：必要 13 处、可疑 3 处、待验证 0 处独立项（可疑项内附运行期待确认点）。

## 二、16 处逐条判定

### 2.1 必要（13 处）

**① `app/src/main/AndroidManifest.xml:47` `.ui.welcome.WelcomeActivity`（activity）**
- 守卫/过滤器：`:50-54` `<intent-filter>` `action.MAIN` + `category.LAUNCHER`。
- 判定：**必要**。桌面主入口，LAUNCHER category 要求组件 exported。
- 改法与影响：不可改 false；改 false 直接断桌面启动。

**② `:58` `.ui.welcome.Launcher1`（activity）**
- 守卫/过滤器：`:59` `android:enabled="false"`；`:63-67` MAIN + LAUNCHER；`:61` 图标 `@mipmap/launcher1`。
- 判定：**必要**。备用桌面图标组件（图标切换时由包管理器启用），LAUNCHER category 要求 exported；`enabled="false"` 期间不可被拉起。
- 改法与影响：不可改 false；改 false 会使备用图标无法进入桌面图标选择器。

**③ `:71` `.ui.welcome.Launcher2`（activity）**
- 守卫/过滤器：`:72` `enabled="false"`；`:76-80` MAIN + LAUNCHER；`:74` `@mipmap/launcher2`。判定：**必要**，理由同②。

**④ `:84` `.ui.welcome.Launcher3`（activity）**
- 守卫/过滤器：`:85` `enabled="false"`；`:89-93` MAIN + LAUNCHER；`:87` `@mipmap/launcher3`。判定：**必要**，理由同②。

**⑤ `:97` `.ui.welcome.Launcher4`（activity）**
- 守卫/过滤器：`:98` `enabled="false"`；`:102-106` MAIN + LAUNCHER；`:100` `@mipmap/launcher4`。判定：**必要**，理由同②。

**⑥ `:110` `.ui.welcome.Launcher5`（activity）**
- 守卫/过滤器：`:111` `enabled="false"`；`:115-119` MAIN + LAUNCHER；`:113` `@mipmap/launcher5`。判定：**必要**，理由同②。

**⑦ `:123` `.ui.welcome.Launcher6`（activity）**
- 守卫/过滤器：`:124` `enabled="false"`；`:128-132` MAIN + LAUNCHER；`:126` `@mipmap/launcher6`。判定：**必要**，理由同②。

**⑧ `:434` `.receiver.SharedReceiverActivity`（activity）**
- 守卫/过滤器：`:438-444` `action.PROCESS_TEXT` + `category.DEFAULT` + `text/plain`；`:445-451` `action.SEND` + `DEFAULT` + `text/plain`。
- 判定：**必要**。外部 App 选中文本「分享到落文」/ 文本分享入口。
- 改法与影响：不可改 false；改 false 断系统级文本分享与选中文本接入。

**⑨ `:455` `.ui.association.OnLineImportActivity`（activity）**
- 守卫/过滤器：`:459-467` `action.VIEW` + `DEFAULT` + `BROWSABLE`；scheme `legado`（`:465`）、`yuedu`（`:466`）。深链格式见 `app/src/main/java/io/legado/app/ui/association/OnLineImportActivity.kt:18` `legado://import/{path}?src={url}`。
- 判定：**必要**。书源/订阅源深链导入入口。
- 改法与影响：不可改 false；改 false 断 `legado://` / `yuedu://` 深链导入。

**⑩ `:481` `.ui.association.FileAssociationActivity`（activity）**
- 守卫/过滤器：`:485-505` `action.SEND`（json/epub/pdf/zip/rar/7z/mobi/azw/octet-stream 等）；`:506-510` `action.SEND_MULTIPLE` + `*/*`；`:513-542` `action.VIEW`（scheme app/content/file）；`:544-601` `action.VIEW` 按扩展名匹配 txt/json/js/epub/pdf/mobi/azw/azw3/zip/rar/7z。
- 判定：**必要**。「用其它应用打开」书籍文件 + 多文件分享导入入口。
- 改法与影响：不可改 false；改 false 断文件打开与分享导入。宽 mimeType 收敛建议见 §四 问题 5。

**⑪ `:624` `.service.WebTileService`（service）**
- 守卫/过滤器：`:629` `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`；`:631-633` `action.QS_TILE`。
- 判定：**必要**。快捷设置磁贴只能由系统按 `BIND_QUICK_SETTINGS_TILE` 绑定，权限守卫已在。
- 改法与影响：不可改 false；改 false 断下拉磁贴入口。

**⑫ `:654` `.service.AutoTaskJobService`（service）**
- 守卫/过滤器：`:656` `android:permission="android.permission.BIND_JOB_SERVICE"`。
- 判定：**必要**。JobScheduler 定时任务回调要求 service exported 并持 `BIND_JOB_SERVICE` 权限。
- 改法与影响：不可改 false；改 false 断定时任务调度绑定。

**⑬ `:659` `.receiver.MediaButtonReceiver`（receiver）**
- 守卫/过滤器：`:661-663` `action.MEDIA_BUTTON`；实现 `app/src/main/java/io/legado/app/receiver/MediaButtonReceiver.kt:29` `onReceive`，`:41` 校验 `ACTION_MEDIA_BUTTON`。
- 判定：**必要**。耳机 / 蓝牙 / 有线媒体按键广播属系统派发，标准媒体按键接收模式。
- 改法与影响：不可改 false；改 false 断媒体按键控制朗读与播放。

### 2.2 可疑（3 处）

**⑭ `:136` `.ui.main.MainActivity`（activity）—— 可疑：无 filter、无守卫，却 exported=true**
- 现状：`:135-141` 仅声明 `exported="true"`、`launchMode="singleTask"`，无 `<intent-filter>`，无 `permission`。
- 拉起方证据（均为应用内部显式 Intent）：
  - `app/src/main/java/io/legado/app/ui/welcome/WelcomeActivity.kt:48` `startActivity(Intent(this, MainActivity::class.java))`；
  - `app/src/main/java/io/legado/app/api/ShortCuts.kt:21` `buildIntent<MainActivity>(context)`（应用内快捷方式）；
  - `app/src/main/java/io/legado/app/receiver/SharedReceiverActivity.kt:55` `startActivity<MainActivity>()`（导出的分享入口内部转发）；
  - `app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfSearchHandoff.kt:75` `context.startActivity<MainActivity> { ... }`。
- 判定：**可疑**。无 intent-filter 却 exported=true，任意已安装 App 可按类名显式拉起主界面；代码内未见外部深链或第三方按类名直拉的路径。
- 最小改法：`android:exported="false"`。
- 功能影响：同进程内部显式 Intent 不受影响；应用内快捷方式由应用自身构建 Intent，也不受影响；风险点：若存在未在 Kotlin 源码中发现的外部按类名直拉路径（桌面第三方插件、残留脚本），关闭后该路径会抛 `SecurityException`——需运行期全量路由确认（见 §四 问题 1）。

**⑮ `:184` `.ui.book.read.ReadBookActivity`（activity）—— 可疑：legacy 横排通道，无 filter、无守卫**
- 现状：`:183-187` `exported="true"`、`launchMode="singleTask"`，无 `<intent-filter>`，无 `permission`；`:182` 注释「阅读界面（Legacy 横排通道，降级备选保留）」。
- 拉起证据：主流程打开书走 `app/src/main/java/io/legado/app/utils/ContextExtensions.kt:74` `else -> InkReadActivity::class.java`；main 生产源码未见 `startActivity(Intent(this, ReadBookActivity::class.java))` 显式拉起，引用均为类型判断（`activity is ReadBookActivity`）、Companion 常量或 androidTest（`app/src/androidTest/.../SharedFileImportTest.kt:850` 等测试件）。
- 判定：**可疑**。legacy 降级通道对外暴露，但生产拉起路径未在 main 源找到；任意 App 可带 `bookUrl` extra 显式拉起阅读页。
- 最小改法：`android:exported="false"`。
- 功能影响：若旧横排降级分支或残留路由仍按类名直拉此 activity，关闭后该路径断开；需先运行期确认降级切换代码与全量字符串路由是否还引用它（见 §四 问题 2）。

**⑯ `:667` `.api.ReaderProvider`（provider）—— 可疑：对外 ContentProvider 无调用方鉴权**
- 现状：`:666-671` `exported="true"`，authority `${applicationId}.readerProvider`，无 `android:permission` / `readPermission` / `writePermission`；manifest 全文无自定义 `<permission>` 声明。
- 代码侧鉴权核查：`app/src/main/java/io/legado/app/api/ReaderProvider.kt` 全文无 `checkCallingPermission` / `getCallingPackage` / 签名校验；`:60` `insert`、`:78` `query`、`:43` `delete` 直接按路由分发。
- 暴露面（`app/src/main/java/io/legado/app/api/ReaderProviderRoutes.kt:31-46`）：书源/订阅源 `insert`/`delete`/`query`、书架 `books/query`、书籍正文 `book/content/query`、章节列表、封面、目录刷新；`ReaderProvider.kt:101` `getBookshelf`、`:102` `getBookContent`。
- 判定：**可疑**。按上游设计这是对外互通 API（`ReaderProvider.kt:19` 注释 `Export book data to other app.`），但当前任意已安装 App 均可查询书架与正文、写入/删除书源，缺调用方鉴权。
- 最小改法：**不可直接 `exported="false"`**（会整体断掉外部互通 API）。建议：先在 manifest 声明 signature 级自定义 permission，挂到 provider 的 `android:permission`（或拆 `readPermission` / `writePermission`）；过渡期在 `query`/`insert`/`delete` 入口补 `callingPackage` 签名比对。
- 功能影响：未持有签名权限的第三方集成 App 将无法再读书架 / 正文 / 写源；是否保留开放互通、兼容哪些存量集成，需产品裁决（见 §四 问题 3）。

## 三、汇总

- 必要 13 处：①~⑦ 启动器与备用图标（`:48/:60/:73/:86/:99/:112/:125`）、⑧ 文本分享（`:435`）、⑨ 深链导入（`:457`）、⑩ 文件打开/分享（`:482`）、⑪ 磁贴服务（`:625`）、⑫ 定时任务服务（`:655`）、⑬ 媒体按键接收（`:660`）。
- 可疑 3 处：⑭ MainActivity（`:139`）、⑮ ReadBookActivity（`:186`）、⑯ ReaderProvider（`:670`）。
- 守卫现状：⑪⑫ 已用系统权限守卫；⑬ 为系统广播标准模式；⑭⑮ 无任何守卫；⑯ 无权限声明、无代码鉴权。

## 四、新发现问题

1. `MainActivity` exported=true 且无 filter 无守卫，四处拉起方证据均为内部显式 Intent —— **已入队**：下一单评估改 `exported="false"`，并补桌面启动、分享转发、快捷方式三条回归验证。
2. `ReadBookActivity` legacy 横排通道 exported=true，main 生产源未见显式拉起路径 —— **待裁**：先做运行期/全量字符串路由排查确认无外部直拉，再决定关 false 还是保留导出。
3. `ReaderProvider` 无调用方鉴权，书架、正文、书源可被任意 App 读写删 —— **待裁**：评估 signature permission 方案与存量互通生态兼容；禁止直接关 exported。
4. Launcher1~6 为 `enabled="false"` 备用图标，当前靠 activity 形式承载 LAUNCHER 过滤器；若后续迁移到 `activity-alias` 可进一步收窄组件面 —— **待裁**。
5. `FileAssociationActivity` 的 `application/octet-stream`（`:504`、`:541`）与 `SEND_MULTIPLE` 的 `*/*`（`:509`）mimeType 范围较宽 —— **已入队**：后续按扩展名白名单收敛，缩小被外部拉起时的类型面。

## 五、未验证项

- 本单为静态只读核查：未跑真机、未跑云端 CI；三个可疑项关闭 exported 后的运行期表现未验证。
- 第三方集成生态（哪些外部 App 按 authority 调 ReaderProvider、哪些按类名直拉 MainActivity / ReadBookActivity）未盘点，需运行期或清单核查。
- manifest 合并后（其它 module manifest 覆盖 / `tools:replace`）的最终导出状态未在编译产物上复核。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=50089f055160；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`："不许 add/commit/push/revert/checkout——提交由总控统一做。"
2. `docs/机制-子代理必读.md:12`："不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。"
3. `docs/机制-子代理必读.md:18`："每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。"
- 适用条款：
  - 只读审计：本单未改 manifest 与任何业务代码，仅 grep / Read 取证。
  - 禁 git 写操作：未执行 add/commit/push/revert/checkout（子代理必读 :11）。
  - 禁本地编译：未跑 gradle/kotlinc/assemble；仅 python3 跑门禁自检（子代理必读 :12）。
  - grep 回证：16 处行号、拉起方文件:行均以 grep / Read 现取现核（子代理必读 :18）。
  - 未验证项声明：运行期表现、第三方生态、合并产物状态已在第五节显式列出（子代理必读 :20）。

## 六、门禁自检输出

```
$ python3 data_scripts/check-onboarding.py docs/审计-exported16处.md
ONBOARD: PASS（本轮无报告改动）
rc=0

$ python3 data_scripts/check-onboarding.py --audit
ONBOARD-AUDIT: 0 份报告缺回执或不合规（历史留档可批量补，见 Q-199）
rc=0

$ python3 data_scripts/check-anchor-card.py --strict docs/审计-exported16处.md
汇总: PASS=1 FAIL=0（共 1 份）
rc=0

$ python3 data_scripts/check-problem-finding.py docs/审计-exported16处.md
INFO 历史欠账 5 份走基线（只许缩小）：修复-InkContentEditState-平台签名撞名.md、修复-全仓非UTF8复扫.md、修复-吞异常三文件.md、修复-空集假绿检测器.md、分析-不等待七阶推演.md
PROBLEM-FINDING: PASS（1 份报告均带新发现问题与处置状态）
rc=0

$ python3 data_scripts/check-unpushed-reports.py
UNPUSHED-REPORTS: PASS（本轮 0 份报告（含已提交未推送）回执合规）
rc=0
```
