# 锚点卡 · 验收 Q465 F1 书源管理组合期 AppConfig 直读（SourceManage 独立复核）

- 自身路径：docs/锚点/验收-Q465-F1-SourceManage.md
- 验收报告：docs/验收-复核-Q465-F1-SourceManage.md
- 被验实现报告：docs/修复-Q465-F1-SourceManage组合期AppConfig.md
- 被验写码方锚点卡：docs/锚点/Q465-F1-SourceManage.md
- 缺陷回源：docs/锚点/Q461-B29book-source.md §五 F-1（`:56`）
- 被验入库提交：46a8852（在 09b2150 之后；本单编译面待云端 CI）
- 代码包：app/src/main/java/io/legado/app/ui/book/source/manage/（SourceManageSheets.kt、SourceManageState.kt、SourceManageViewModel.kt 三文件）

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「B2 改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「C3 规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 适用条款：验收方独立取数不采信写码方计数；只读验收不改被验源码与修复报告；禁 git 写与本地编译；每条销项附 文件:行 证据锚点。

## 一、事实锚点（可回源，≥5 处）

1. app/src/main/java/io/legado/app/help/config/AppConfig.kt:47-49：`var blockSourceNavigation: Boolean`，getter `appCtx.getPrefBoolean(PreferKey.blockSourceNavigation, false)`，setter `appCtx.putPrefBoolean(PreferKey.blockSourceNavigation, value)`；默认 `false`。
2. app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:131（修后）：`checked = state.blockSourceNavigation,`；`:132` `onToggle = { vm.toggleBlockSourceNavigation() }`。修前旧快照同行原为 `checked = io.legado.app.help.config.AppConfig.blockSourceNavigation,`（组合期直读，本单真缺陷点）。
3. app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt:99：`val blockSourceNavigation: Boolean = false,`；`:74` KDoc 参数说明；`:98` 口径注释。修前 State 无该字段。
4. app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:70：init 回填 `blockSourceNavigation = AppConfig.blockSourceNavigation,`；`:237` `val next = !_uiState.value.blockSourceNavigation`；`:238` `AppConfig.blockSourceNavigation = next`；`:239` `_uiState.update { it.copy(blockSourceNavigation = next) }`。
5. 读值链路：app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:79 `val state by vm.uiState.collectAsState()`；`:80` `SourceManageScreen(state = state, vm = vm, actions = this@BookSourceActivity)`；app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageScreen.kt:138 `ManageSheets(state = state, vm = vm, actions = actions)`；app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:55 `MainOptionsSheet(state, vm, actions)`。
6. 运行期只读消费方：app/src/main/java/io/legado/app/help/source/SourceNavigationBridge.kt:15 `private fun allowed() = !shouldSuppressSourceNavigation(AppConfig.blockSourceNavigation, context)`；app/src/main/java/io/legado/app/help/JsExtensions.kt:1300 `if (!shouldSuppressSourceNavigation(AppConfig.blockSourceNavigation, context)) return true`。两处不写偏好、不经 state 镜像。
7. 偏好键：app/src/main/java/io/legado/app/constant/PreferKey.kt:23 `const val blockSourceNavigation = "blockSourceNavigation"`；全仓写方仅 SourceManageViewModel.kt:238 一处。
8. 既有同口径范式：app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:121 `checked = state.showCheckStatus`、`:126` `checked = state.showBookshelfCount`，本单第三个开关对齐同一 VM-state 口径。

## 二、验收判据逐条对照

1. 组合期零触碰：Sheets / Screen / Activity 组合路径 `AppConfig` / `appCtx` / `help.config` / `PreferKey` / `getPrefBoolean` 命中为 0；Activity:83 `LocalConfig` 读取在 onCreate 方法体、setContent 闭包之外，属宿主生命周期阶段；VM init 与事件回调内的 AppConfig 命中均非组合期；无 `remember { }` 内偏好补读。
2. 语义等价：state 默认 `false` 对齐偏好默认 `false`；取反由偏好直读改为镜像取反，落偏好并回写镜像；写回时机仍为点击回调内同步落偏好；等价前提为全仓唯一写方（grep 取证）。
3. 二阶影响：Bridge:15 与 JsExtensions:1300 运行期直读偏好，与镜像不打架；旋转保留 VM、进程重建新 VM init 重读偏好，勾选态可复原；Flow 形态与订阅链未变。
4. 测试面：全仓 `SourceManageUiState(` 构造仅 VM:58 默认构造一处，test / androidTest 无构造点；新字段带默认值向后兼容；JVM 契约测试只按路径 / 方法名文本匹配；androidTest 直读偏好键，键名未变。
5. 静态可编译性：kotlin-syntax-lint 三文件 PASS；类型贯通为静态推理，clean 编译与单测待云端 CI；本单无新文件 / import / 签名 / 可见性改动。

## 三、边界与未验证

- 只复核 app/src/main/java/io/legado/app/ui/book/source/manage/ 下三文件；edit/、debug/ 子域与 help 域不在本单文件域。
- 未编译、未跑单测、未上机；截图源集当前无本屏 Paparazzi 帧；N3 androidTest legacy 绑定漂移为既有问题，交 androidTest 域定性。
- 验收结论：有条件通过（编译面待 CI）；详见验收报告 §二销项、§四新发现问题、§五未验证项。

## 四、新发现问题回指

- N1 待裁 · 留观：SourceManageSheets.kt:360 / :377 组合期读 `CheckSource.keyword`（内存 var，当前无崩溃口）。
- N2 已登记待裁：SourceNavigationBridge.kt:15 / JsExtensions.kt:1300 共享偏好键的运行期只读路径，交 help 域派单。
- N3 待裁 · 既有漂移：BookSourceCheckUiTest.kt 与 SourceDragOrderUiTest.kt 引用 legacy `ItemBookSourceBinding`，布局源在 46a8852^ 与 HEAD 均缺位，非本单引入。
- N4 已入队：SourceManageSheets.kt 649 行棘轮线为修前存量，本单净增 0 行，拆分另单收口。
