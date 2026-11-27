# 锚点卡 · Q465 F1 书源管理组合期 AppConfig 直读（SourceManage）

> 用途：F1 单收口依据。被修报告 `docs/修复-Q465-F1-SourceManage组合期AppConfig.md`；缺陷出处 `docs/锚点/Q461-B29book-source.md` §五 F-1；同源先例提交 `4738fb4`（书架视差偏好上移宿主 Fragment，Compose 屏零 AppConfig 依赖）。
> 逐字引文带 文件:行，禁转述；锚点禁裸 `:NNN`。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「B2 改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「C3 规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 适用条款：grep 回证先行；文件域白名单内改动；禁 git 写与本地编译；改动处注释写明为什么 / 适用条件。

## 一、事实锚点（可回源，≥5 处）

- `app/src/main/java/io/legado/app/help/config/AppConfig.kt:32`：`object AppConfig : SharedPreferences.OnSharedPreferenceChangeListener`——Kotlin 单例，成员访问即触发类初始化。
- `app/src/main/java/io/legado/app/help/config/AppConfig.kt:28`：`import splitties.init.appCtx`；`:47-49` `blockSourceNavigation` getter `appCtx.getPrefBoolean(PreferKey.blockSourceNavigation, false)`、setter `appCtx.putPrefBoolean(...)`。
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:131`（修前）：`checked = io.legado.app.help.config.AppConfig.blockSourceNavigation,`，位于 `@Composable private fun MainOptionsSheet(...)`（`:83`）体内，组合期求值直读——本单真缺陷点。
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:121` / `:126`：同屏 `state.showCheckStatus`、`state.showBookshelfCount` 既有 VM-state 口径，为本单对齐范式。
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt:99`：修后 `val blockSourceNavigation: Boolean = false,`（默认 `false` 对齐偏好默认值，`AppConfig.kt:48`）。
- `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt:70`：init 回填 `blockSourceNavigation = AppConfig.blockSourceNavigation,`；`:237-239` 取反、落偏好、回写镜像三句。
- `app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt:79-80`：`collectAsState()` 后 `SourceManageScreen(state = state, ...)`；`SourceManageScreen.kt:138` `ManageSheets(state = state, ...)`；`SourceManageSheets.kt:55` 分发 `MainOptionsSheet(state, vm, actions)`。
- 同源先例：`app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfScreen.kt:206-207` `parallaxEnabled: Boolean = true` + `onPersistParallax: (Boolean) -> Unit = {}`；`InkShelfFragment.kt:95-96` 宿主读偏好传参、回调落偏好（提交 `4738fb4`）。

## 二、判据（验收逐条对照）

1. 修后 `SourceManageSheets.kt`（及 `SourceManageScreen.kt`、`SourceManageRow.kt` 等本域 Compose 屏）`AppConfig` / `help.config` / `appCtx` 命中数为 0；值只来自形参或 VM state。
2. 读值链路完整可追：宿主 / VM 侧读入 → state 流 → Compose 渲染；禁止 `remember { }` 内补读偏好掩盖。
3. 默认值与原子语义等价：state 默认 `false` 对齐偏好默认；取反语义与旧实现一致，不改 UI 行为、不改文案与开合时机。
4. 同目录其余命中逐条定级：VM init 读、事件回调读写属宿主侧路径，非组合期，保留；除 `:131` 外无第二处组合期直读（修前目录 7 处文本命中，真组合期 1 处）。
5. 写回经 VM 回调落偏好并同步镜像；全仓该偏好仅本 VM 一处写入方。
6. 静态检查仅用 `python3 data_scripts/kotlin-syntax-lint.py`；禁跑 gradlew / kotlinc / assemble；禁 git 写操作。

## 三、边界

- 只动 `app/src/main/java/io/legado/app/ui/book/source/manage/` 下 `SourceManageSheets.kt`、`SourceManageState.kt`、`SourceManageViewModel.kt` 三个文件。
- 不碰 bookshelf 已验证实现；不碰 config / autoTask / dict / replace 四域；不碰 components 契约红线；`edit/`、`debug/` 子域零改动。
- Q-461 锚点卡 §五 F-2（宿主级闸未接）、F-3（动作行不自动关层）、F-4（控制器测试待 CI）、F-5（remember 不跨重建）、F-6（四份包装并存）为既有留观项，本单不代收口。

## 四、验收

- 独立验收代理逐条对照本卡 §二判据与被修报告 §5 grep 回证行号；编译面以云端 CI `Verify` 为准，截图帧以 CI Screenshot 任务为准。
- 自查反例与新发现问题见被修报告 §六、§八；未验证项见报告 §九。
