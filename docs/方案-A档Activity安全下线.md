# 方案 · A 档三 Activity 安全下线预研（Q-260，只读，不改代码）

> 预研对象：Q-236 清点的 A 档三候选——`BookshelfManageActivity`、`RemoteBookActivity`、`AboutActivity`。
> 方法：全仓穷举 `::class` / `startActivity` / `Intent` / manifest 声明与 intent-filter / 反射（`Class.forName`、`setClassName`、`ComponentName`）/ JS 桥拼类名 / 快捷方式 `ShortCuts` / 通知与 `PendingIntent` / `MediaButtonReceiver` / `AutoTask*` / `androidTest` / `screenshot` 源集；每条给 文件:行 + 判定（活引用 / 死引用 / 仅测试引用）。
> 边界：本单只读预研，未改一行代码、未跑本地编译、未 push；方案等 CI 全绿后再当施工图执行。证据不足处标「待运行期确认」，不猜。
> 锚点卡：docs/锚点/Q260-下线方案.md。

> **口径已失效（2026-11-26，Q-280）**：旧关于页 `AboutActivity` 已整体删除，详见 `docs/修复-删除关于页.md`；本段保留为历史记录，勿据以施工。

## 一、基线事实（三 Activity 现状）

| Activity | 声明位置 | 基类 | manifest 行 | intent-filter / exported |
|---|---|---|---|---|
| `AboutActivity` | `ui/about/AboutActivity.kt:61` | `AppCompatActivity`（Compose 宿主） | `AndroidManifest.xml:297` | 无 intent-filter；未显式 `exported`，默认不导出 | 已废（现行关于页 app/src/main/java/io/legado/app/ui/about/AboutInkActivity.kt:50 与后续报告 docs/修复-删除关于页.md 为准（旧 AboutActivity 已于 0b995d9 Q-280 删除））
| `BookshelfManageActivity` | `ui/book/manage/BookshelfManageActivity.kt:70` | `VMBaseActivity<ActivityArrangeBookBinding, BookshelfManageViewModel>` | `AndroidManifest.xml:344` | 无 intent-filter；默认不导出 |
| `RemoteBookActivity` | `ui/book/import/remote/RemoteBookActivity.kt:36` | `BaseImportBookActivity<RemoteBookViewModel>` | `AndroidManifest.xml:378` | 无 intent-filter；默认不导出 |

- 三页 manifest 块均无 `<intent-filter>`、无 scheme/host/action 深链声明；全仓无 `Class.forName` / `setClassName` / `ComponentName` 拼出这三个类名（仅有的 `setClassName` 在 `PermissionActivity.kt:129` 系统设置、`TextActionMenu.kt:250` 第三方应用信息，与本三页无关）。
- 名称撞名提示：`model/remote/RemoteBookWebDav`（WebDAV 数据模型）与 `ui/book/import/remote/`（UI 包）只是同名前缀，`model` 层不 import UI remote 包，下线 UI 包不得动 WebDAV 数据层。
- 跨切面零命中证据（穷举面，逐条回源）：
  - 快捷方式：`api/ShortCuts.kt:21` 快捷意图只指向 `MainActivity`，`:31` 朗读快捷意图指向 `SharedReceiverActivity`；无三页目标。
  - 通知 / `PendingIntent`：全仓 `PendingIntent` 构造点无三页类名（grep 三 Activity 名零命中）。
  - `MediaButtonReceiver`：`receiver/MediaButtonReceiver.kt:27` 为朗读按键接收器，manifest `:659` 注册，与三页无关。
  - `AutoTask*`：`model/AutoTask.kt`、`AutoTaskRunner.kt`、`AutoTaskProtocol.kt` 及 `App.kt`、`AppDatabase.kt`、`Restore.kt` 均不引用三 Activity 类名（grep 零命中）。
  - JS 桥 / WebView：`setClassName` 仅 `PermissionActivity.kt:129`、`TextActionMenu.kt:250`；`Class.forName` 仅 `utils/FileDocExtensions.kt:66`；均与三页无关，WebView 桥接代码无三页类名字符串。
  - 截图源集：`app/src` 下无 screenshot / shot 源集目录，三页名在测试截图资产零命中。

## 二、`AboutActivity`（关于页旧 Compose 宿主）

### 2.1 引用穷举

| 文件:行 | 原文片段（节选） | 引用形态 | 判定 |
|---|---|---|---|
| `AndroidManifest.xml:297` | `android:name=".ui.about.AboutActivity"` | manifest 注册 | 死引用（无活入口，待随类移除） |
| `ui/main/my/MyFragment.kt:27` | `import io.legado.app.ui.about.AboutActivity` | 旧宿主 import | 死引用（`MyFragment` 全仓无实例化，见 2.2） |
| `ui/main/my/MyFragment.kt:273` | `"about" -> startActivity<AboutActivity>()` | 旧宿主 preference 路由 | 死引用（宿主孤儿，路由不可达） |
| `ui/main/my/MyInkFragment.kt:19` | `import io.legado.app.ui.about.AboutActivity` | 未使用 import | 死引用（残留，新壳路由走 `AboutInkActivity`，见 :106） |
| `androidTest/.../book/read/ContentEditSearchTest.kt:20`、:48、:57 | `ActivityScenario<AboutActivity>?` / `ActivityScenario.launch(AboutActivity::class.java)` | UI 测试宿主（:57 后 `showNow(activity.supportFragmentManager, ...)` 挂自有 `ContentEditDialog`） | 仅测试引用（不依赖 AboutScreen 行功能） |
| `androidTest/.../widget/dialog/BottomWebViewDialogShowTest.kt:31`、:59、:64 | 同上 `ActivityScenario.launch(AboutActivity::class.java)` | UI 测试宿主（自插 WebView 对话框） | 仅测试引用 |
| `androidTest/.../widget/image/CoverTitleAdaptiveUiTest.kt:33`、:62、:85 | 同上；:90 `activity.setContentView(root)` 自插 Compose 根 | UI 测试宿主 | 仅测试引用 |
| `test/.../about/UpdateDialogLifecycleTest.kt:17-18` | 源码文本断言：`listOf("ui/about/AboutActivity.kt", "ui/main/my/MyFragment.kt")`，断言含 `"check_update" -> checkAppUpdate()` / `"check_beta_update" -> checkAppUpdate(beta = true)` | 单测源码文本锁定 | 仅测试引用（删类后必须改指或改写） |
| `ui/about/AboutActivity.kt:68`、:168 | `AboutScreen(activity = this@AboutActivity)` / `private fun AboutScreen(activity: AboutActivity)` | 类内自引 | 类本体，随类删除 | 已废（现行关于页 app/src/main/java/io/legado/app/ui/about/AboutInkActivity.kt:50 与后续报告 docs/修复-删除关于页.md 为准（旧 AboutActivity 已于 0b995d9 Q-280 删除））

### 2.2 孤儿宿主佐证

- `MyFragment()` 全仓仅 `MyFragment.kt:50` 类声明一处，无任何实例化 / `::class` 拉起；其 `pref_main.xml` 布局仅由 `MyFragment.kt:116` `addPreferencesFromResource(R.xml.pref_main)` 装载，`pref_main.xml:139` 的 `"about"` preference 行随孤儿宿主一并不可达。
- 新壳路由已存在：`MyInkFragment.kt:106` `open(io.legado.app.ui.about.AboutInkActivity::class.java)`；`MyInkFragment.kt:109` 另有独立「检查更新」行调 `checkAppUpdate()`。

### 2.3 替换表：旧壳行功能 → 新壳现状

| 旧壳行（`AboutActivity.kt:143-157` `onRowClick`） | 功能 | 新壳 `AboutInkActivity` 现状 | 替换判定 | 已废（现行关于页 AboutInkActivity.kt 第 50 行，全路径 app/src/main/java/io/legado/app/ui/about/AboutInkActivity.kt；与后续报告 docs/修复-删除关于页.md 为准（旧 AboutActivity 已于 0b995d9 Q-280 删除））
|---|---|---|---|
| `check_update`（:147 `checkAppUpdate()`） | 检查正式版更新 | `MyInkFragment.kt:109` 已有独立「检查更新」行 | 功能已迁移到 My 页，旧页此行可裁 |
| `check_beta_update`（:148 `checkAppUpdate(beta = true)`） | 检查 Beta 更新 | 新壳 / My 页均无 Beta 入口 | **缺口**：需补 Beta 行或明确裁撤 |
| `contributors`（:145 打开 `contributors_url`） | 贡献者链接 | 无 | **缺口** |
| `update_log`（:146 弹 `updateLog.md`） | 更新日志 | 无 | **缺口** |
| `crashLog`（:154 弹 `CrashLogsDialog`） | 崩溃日志查看 | 无；`MainActivity.kt:47` 有 `CrashLogsDialog` 未使用 import（残留） | **缺口**；对话框本体 `CrashLogsDialog.kt` 保留 |
| `saveLog`（:77-90 打包 logs/crash/logcat 到备份目录） | 保存日志包 | 无 | **缺口** |
| `createHeapDump`（:92-107 堆转储落备份目录） | 堆转储 | 无 | **缺口** |
| `privacyPolicy`（:152 弹 `privacyPolicy.md`） | 隐私政策 | 无 | **缺口** |
| `license`（:150 弹 `LICENSE.md`） | 开源协议 | 无 | **缺口** |
| `disclaimer`（:151 弹 `disclaimer.md`） | 免责声明 | 无 | **缺口** |
| `mail`（:149 `sendMail(email)`） | 邮件反馈 | 无 | **缺口** |
| `gzGzh`（:153 复制公众号） | 复制公众号口令 | 无（品牌页不展示） | **缺口** |
| 分享按钮（:224-231 复制 `app_share_description`） | 应用分享口令 | 无 | **缺口** |

- 结论：`AboutInkActivity`（`AboutInkActivity.kt:50-56`）当前只有品牌字、版本号、底部字幕，**不是功能等价替换**；直接删 `AboutActivity` 会丢掉上表 11 项缺口功能。
- 可保留的共享件（不随旧壳删）：`CheckAppUpdate.kt`、`CrashLogsDialog.kt`、`TextDialog`、`sendMail`/`sendToClip`/`openUrl` 工具；MD 资产 `updateLog.md`、`LICENSE.md`、`disclaimer.md`、`privacyPolicy.md` 当前仅 `AboutActivity.kt` 引用（grep 零外部命中），功能迁移前保留。

### 2.4 删除时需同步改的字符串 / 资源 / manifest 项

- manifest：移除 `AndroidManifest.xml:297-299` 整块。
- 代码：删 `MyFragment.kt:27` import 与 :273 路由（`MyFragment` 整体孤儿，随其下线单处理）；清 `MyInkFragment.kt:19` 未用 import；清 `MainActivity.kt:47` `CrashLogsDialog` 未使用 import。
- 测试：三个 androidTest 宿主改指 `AboutInkActivity`（同为 `AppCompatActivity`，`supportFragmentManager` / `setContentView` 可用）；`UpdateDialogLifecycleTest.kt:17-18` 改指新壳对应行或改写断言。
- 资源：字符串（`contributors`、`contributors_summary`、`contributors_url`、`update_log`、`crash_log`、`save_log`、`create_heap_dump`、`privacy_policy`、`license`、`disclaimer`、`email`、`legado_gzh`、`other`、`app_share_description` 等）与 4 个 MD 资产，待功能迁完并确认无第二引用后再清；本轮不判定可删。

## 三、`BookshelfManageActivity`（书架批量管理旧屏）

### 3.1 引用穷举

| 文件:行 | 原文片段（节选） | 引用形态 | 判定 |
|---|---|---|---|
| `AndroidManifest.xml:344` | `android:name=".ui.book.manage.BookshelfManageActivity"` | manifest 注册 | 死引用 |
| `ui/book/manage/BookshelfManageActivity.kt:70` | `class BookshelfManageActivity : VMBaseActivity<...>(), ...` | 类本体 | 待删 |
| `ui/book/manage/BookshelfManageViewModel.kt` | 整文件 | 专属 ViewModel，仅被本 Activity 引用（grep 仅两文件互引） | 伴生待删候选 |
| `ui/book/manage/BookAdapter.kt:22` | `class BookAdapter(context, val callBack: CallBack)` | 专属列表适配器；包内 `manage.BookAdapter` 无外部 import | 伴生待删候选 |
| `ui/book/manage/SourcePickerDialog.kt:41` | `class SourcePickerDialog : BaseDialogFragment(R.layout.dialog_source_picker)` | 专属对话框；仅由 `BookshelfManageActivity.kt:330` `showDialogFragment<SourcePickerDialog>()` 拉起 | 伴生待删候选 |
| `res/layout/activity_arrange_book.xml` | `ActivityArrangeBookBinding` 布局 | 仅本 Activity `:78` `viewBinding(ActivityArrangeBookBinding::inflate)` | 伴生布局候选 |
| `res/layout/item_arrange_book.xml` | `ItemArrangeBookBinding` 列表项 | 仅 `BookAdapter.kt:38` 使用 | 伴生布局候选 |
| `res/menu/bookshelf_manage.xml` | 选项菜单 | 仅 `BookshelfManageActivity.kt:153` `inflate(R.menu.bookshelf_manage, menu)` | 伴生菜单候选 |
| `res/menu/bookshelf_menage_sel.xml` | 多选动作菜单 | 仅 `BookshelfManageActivity.kt:214` `binding.selectActionBar.inflateMenu(...)` | 伴生菜单候选 |
| `test/.../main/TocUpdateRequestsTest.kt:190` | 源码文本路径 `".../ui/book/manage/BookshelfManageActivity.kt"`，配合 `bookshelf_menage_sel.xml` 断言 `menu_update_toc` 策略 | 单测源码文本锁定 | 仅测试引用 |

- main 侧拉起方：全仓无 `startActivity<BookshelfManageActivity>`、无 `Intent(..., BookshelfManageActivity::class.java)`、无反射 / 字符串类名 / 深链 / 快捷方式 / 通知 / 接收器 / AutoTask 引用。
- 共享不可删项：菜单 id 多处被其它屏复用（`menu_change_source` 9 个文件、`menu_sort_name` 3 个文件、`menu_clear_cache` / `menu_group_manage` 3 个文件等）；`res/layout/item_book_group_manage.xml` 属存活的 `ui/book/group/GroupManageDialog.kt:22`，不在本单删除面。

### 3.2 替换表：旧屏批量功能 → 新书架现状

| 旧屏能力（菜单 / 行） | 新书架 `InkShelfFragment` 现状 | 替换判定 |
|---|---|---|
| 多选 + 全选（`selectAll`，:165） | 新书架为单组平铺（`InkShelfFragment.kt:122` 注释明示无子分组层级），未见多选动作条 | **缺口**：批量选择无新壳承接 |
| 移入 / 移出分组（`add_to_group` / `remove_group` / `menu_group_manage`） | 分组管理另有存活的 `GroupManageDialog`，但新书架无入口把多选书送入分组 | **缺口**：分组数据层在，入口链断 |
| 批量换源（`menu_change_source` / `change_source_batch`） | 无 | **缺口** |
| 批量清理缓存 / 恢复网络源封面 / 恢复源封面 / 持久化封面（`menu_clear_cache`、`menu_restore_network_covers`、`menu_restore_source_covers`、`menu_persist_covers`） | 无 | **缺口** |
| 批量更新目录 / 启用禁用更新 / 建更新任务 / 导出全部用书源（`menu_update_toc`、`menu_update_enable/disable`、`menu_create_book_update_tasks`、`menu_export_all_use_book_source`） | 无；`AutoTask*` 体系独立存活，与本屏解耦 | **缺口** |
| 删除书文件（`delete_book_file`） | 无 | **缺口** |

- 结论：本屏批量管理能力在新书架**无等价承接**；下线前必须二选一——(a) 把上述批量动作补进新书架多选链路；(b) 由产品 / 规格明确裁撤对应功能并留痕。现状不具备直接删类条件。

### 3.3 删除时需同步改的字符串 / 资源 / manifest 项

- manifest：移除 `AndroidManifest.xml:344-346` 整块。
- 代码：删 `manage/` 包内 Activity + ViewModel + BookAdapter + SourcePickerDialog；确认 `dialog_source_picker.xml` 布局无第二引用后删。
- 菜单：`bookshelf_manage.xml`、`bookshelf_menage_sel.xml` 随伴生删除；菜单 id 与共享字符串（`change_source_batch`、`move_to_group`、`sure_del`、`no_group` 等）先 grep 第二引用再定去留，不得随菜单一并删字符串。
- 测试：`TocUpdateRequestsTest.kt:190` 源码断言需改写（断言对象迁到新批量入口或随功能裁撤）。

## 四、`RemoteBookActivity`（远程书库浏览导入旧屏）

### 4.1 引用穷举

| 文件:行 | 原文片段（节选） | 引用形态 | 判定 |
|---|---|---|---|
| `AndroidManifest.xml:378` | `android:name=".ui.book.import.remote.RemoteBookActivity"` | manifest 注册 | 死引用 |
| `ui/book/import/remote/RemoteBookActivity.kt:36` | `class RemoteBookActivity : BaseImportBookActivity<RemoteBookViewModel>(), ...` | 类本体 | 待删 |
| `remote/RemoteBookAdapter.kt` | 列表适配器 | 仅本 Activity + 自身文件 | 伴生待删候选 |
| `remote/RemoteBookViewModel.kt` | 专属 VM；读 `appDb.serverDao`、`AppConfig.remoteServerId`、`RemoteBookWebDav` | 仅本 Activity | 伴生待删候选 |
| `remote/RemoteBookSort.kt` | 排序枚举 | 仅 Activity / VM 内部 | 伴生待删候选 |
| `remote/ServerConfigDialog.kt` + `ServerConfigViewModel.kt` | 服务器配置对话框 | 成对自洽；仅被 `ServersDialog` 引用 | 伴生待删候选 |
| `remote/ServersDialog.kt` + `ServersViewModel.kt` | 服务器列表对话框 | 仅被 `RemoteBookActivity` 拉起 | 伴生待删候选 |
| `res/menu/book_remote.xml` | 远程屏选项菜单 | 仅 `RemoteBookActivity.kt:109` `inflate(R.menu.book_remote, menu)` | 伴生菜单候选 |
| `test/.../import/ImportSelectionCountResetTest.kt:17` | 源码文本路径 `".../ui/book/import/remote/RemoteBookActivity.kt"`，断言 `adapter.selectAll(false)` 口径 | 单测源码文本锁定 | 仅测试引用 |

- main 侧拉起方：`ImportBookActivity.kt` 不 import remote 包；全仓无 `startActivity<RemoteBookActivity>`、无反射 / 深链 / 快捷方式 / 通知 / JS 桥入口；`res/xml` 无远程导入偏好键。
- 数据层隔离：`model/remote/RemoteBookWebDav`、`help/AppWebDav.kt`、`appDb.serverDao`、`AppConfig.remoteServerId` 属 WebDAV 配置 / 同步链路，`CacheActivity.kt:190/221` 的 WebDAV 导出开关仍存活；删 UI remote 包不得连带删数据模型与服务器配置表。

### 4.2 替换表：旧屏能力 → 现状

| 旧屏能力 | 现状承接 | 替换判定 |
|---|---|---|
| 远程服务器列表 / 新增 / 编辑 WebDAV 服务器（`ServersDialog` / `ServerConfigDialog`） | 无 UI 入口；配置数据仍在 `serverDao`，WebDAV 导出在 `CacheActivity` 保留开关 | **缺口 / 待裁**：需确认远程浏览导入功能是否仍在产品范围内 |
| 远程目录浏览、排序（名称 / 时间）、勾选导入 | 无 | **缺口 / 待裁** |
| 本地导入 `ImportBookActivity` | 存活，与 remote 包解耦 | 不受影响 |

- 结论：本屏在当前运行期无入口；是否可整体下线取决于「远程书库浏览导入」功能保留与否。若裁撤，UI remote 包 8 个文件 + `book_remote.xml` 可成组下；数据层保留。

### 4.3 删除时需同步改的字符串 / 资源 / manifest 项

- manifest：移除 `AndroidManifest.xml:378-380` 整块。
- 代码：整组删 `ui/book/import/remote/` 8 文件；先复核 `serverDao` / `RemoteBookWebDav` / `AppWebDav` 无 UI remote 包反向依赖遗漏。
- 菜单 / 字符串：`book_remote.xml` 删除；`remote_book`、`select_book_folder`、`archive_not_found`、`server_config` 等字符串 grep 第二引用后再定；`help`/`log`/`refresh`/`sort` 为通用串，保留。
- 测试：`ImportSelectionCountResetTest.kt:17` 移除 remoteAction 分支（保留 localAction 断言）。

## 五、分步施工顺序（等 CI 全绿后照此执行；每步附验收锚点与回滚点）

> 总原则：先切路由与测试指向（让旧类不再被新代码引用）→ 再摘 manifest → 再删类与伴生 → 最后清资源；每步独立可回滚，禁止一步到位整包删。

### 步骤 1：路由切换与死引用清理（不动旧类）

1. 关于页：确认新壳功能缺口处置结论（补功能 or 裁撤）后，`MyInkFragment.kt:106` 已是唯一新入口；清 `MyInkFragment.kt:19` 未用 import、`MainActivity.kt:47` 未用 `CrashLogsDialog` import。
2. 书架 / 远程两屏：无新壳路由可切，本步骤仅记录「无入口可达」现状；不新增指向旧类的调用。
3. 验收锚点：全仓 grep 三 Activity 类名，main 侧除类本体与 manifest 外零命中；`check-gate-wiring.py` 接线不报错。
4. 回滚点：本步只删 import / 路由行，旧类文件未动；回滚即恢复 import 与路由行。

### 步骤 2：测试指向改写（类仍保留，先让测试不依赖旧宿主 / 旧源码）

1. 三个 androidTest 宿主由 `AboutActivity` 改指 `AboutInkActivity`（或一个空壳测试宿主），核对 `supportFragmentManager` / `setContentView` 注入路径不变。
2. `UpdateDialogLifecycleTest.kt:17-18`、`TocUpdateRequestsTest.kt:190`、`ImportSelectionCountResetTest.kt:17` 三处源码文本断言改指新对象或随功能裁撤改写。
3. 验收锚点：`app/src/test`、`app/src/androidTest` 编译期不再引用三 Activity；单测断言与新口径一致。
4. 回滚点：测试文件独立于产品类；回滚即还原测试改动，旧类仍在。

### 步骤 3：摘除 manifest 注册

1. 依次移除三块 manifest（`:297-299`、`:344-346`、`:378-380`）；每摘一块观察 CI 该模块编译与启动冒烟。
2. 验收锚点：manifest 无三 Activity 类名；`CI-CONTRACT`（workflow 契约）不红；冷启动主屏可进，无「找不到 Activity」崩溃。
3. 回滚点：manifest 块可单独还原；摘注册不删类，回滚后类仍在 classpath。

### 步骤 4：删类与伴生源码

1. `AboutActivity.kt`（含私有 `AboutScreen` / `AboutRow` / `AboutRowItem` / 扩展函数）。
2. `manage/` 包：Activity + ViewModel + BookAdapter + SourcePickerDialog（先确认 `BookAdapter` 包名无外部 import）。
3. `remote/` 包 8 文件成组删；保留 `model/remote`、`help/AppWebDav`、`serverDao` 数据层。
4. 验收锚点：编译无 unresolved reference；伴生类 grep 零外部引用；CI 单测全绿。
5. 回滚点：git 历史可整体还原对应文件；步骤 4 之前步骤 1-3 已合入，回滚时按 commit 粒度回退本步。

### 步骤 5：资源清理（菜单 / 布局 / 字符串 / MD 资产）

1. 菜单 XML 三件（`bookshelf_manage.xml`、`bookshelf_menage_sel.xml`、`book_remote.xml`）；伴生布局 `activity_arrange_book.xml`、`item_arrange_book.xml`、`dialog_source_picker.xml`（逐个复核零第二引用）。
2. 字符串与 MD 资产：逐条 grep 第二引用，确认共享串（`help`/`sort`/`group`/`delete` 等）保留；4 个 about MD 资产仅在功能迁移确认后删。
3. 验收锚点：lint / 编译无未解析资源；无残留菜单 id 引用；资源引用 grep 零悬空。
4. 回滚点：资源文件独立于编译 classpath，可单独还原。

## 六、反例推演（假设这套顺序是错的）

1. **外部深链 / 桌面快捷方式 404**：三页 manifest 均无 intent-filter、未导出，静态扫描未发现快捷方式或 PendingIntent 指向；残余风险是历史版本缓存的 PendingIntent 携带组件名，用户点旧通知直跳。兜底：删类前在 Release 清单核对无持久化 PendingIntent 指向三页；manifest 块可先保留一个版本周期观察 Crashlytics / 通知点击信号，再摘注册。
2. **启动 / 跳转崩溃 `ClassNotFoundException`**：若先删类后摘 manifest，或反过来先摘测试指向而类仍被旧路由引用，编译期即红；运行期崩溃只可能来自残留反射 / 字符串组件名。本仓反射面已扫（`Class.forName` 仅 `TreeDocumentFile` 兼容性探测，与三页无关）。兜底：每步编译 + 冷启动冒烟，崩溃栈首帧指向三页类名即回滚该步。
3. **测试源集编译断裂**：androidTest 三个宿主与三个单测源码断言仍引用旧类，若步骤 4 先于步骤 2 执行，测试编译直接 unresolved。兜底：严格按步骤 2 → 步骤 4 顺序；测试改动单独成 commit，便于回退。
4. **功能静默丢失，Crashlytics 无信号**：这是本单最大风险——批量管理、远程导入、关于页 11 项缺口功能被删后不崩，但用户入口消失且无异常上报。兜底：删类前必须有功能处置结论（迁移清单逐项销号 or 产品裁撤留痕）；缺口项未销号前不得进入步骤 3 以后。
5. **共享资源误删连锁红**：菜单 id / 通用字符串被其它屏复用，若随伴生菜单一并删，会打出资源 unresolved。兜底：步骤 5 每条资源先 grep 第二引用，共享项保留。
6. **WebDAV 数据层被连带下掉**：UI remote 包与 `model/remote` 同名前缀，误删会影响 `CacheActivity` WebDAV 导出。兜底：步骤 4 限定 `ui/book/import/remote/` 目录，数据层文件清单列入保留白名单。

## 七、CI 绿门槛与本轮可做项

### 7.1 必须等 CI 全绿后再做（施工动作）

- 步骤 1 的路由 / import 清理、步骤 2 测试改写、步骤 3 manifest 摘除、步骤 4 类与伴生删除、步骤 5 资源清理——全部涉及代码 / manifest / 测试改动，必须在当前 CI 绿基线之上分批提交，每批回 CI 编译 + 单测验证。
- 功能缺口（2.3、3.2、4.2 表）未销号前，对应 Activity 不得进入步骤 3 及以后。

### 7.2 现在可做、本轮不做（预研结论已留档）

- 引用穷举与死活判定（本单已完成）。
- 缺口清单与替换映射、伴生类 / 资源归属表、施工顺序与回滚点（本单已完成）。
- 不写任何产品代码、不调 manifest、不改测试；等功能处置结论与 CI 绿基线。

## 八、新发现问题

1. `AboutInkActivity` 是品牌壳，未接管旧关于页 11 项功能（待裁 / 待补）：见 2.3 缺口表；其中 Beta 更新、崩溃日志、日志包 / 堆转储、四份 MD 文档、邮件 / 公众号 / 分享口令均无新壳承接。证据行 `AboutActivity.kt:143-157`、`AboutInkActivity.kt:50-56`。 已废（现行关于页 app/src/main/java/io/legado/app/ui/about/AboutInkActivity.kt:50 与后续报告 docs/修复-删除关于页.md 为准（旧 AboutActivity 已于 0b995d9 Q-280 删除））
2. 新书架无批量多选 / 分组 / 批量换源 / 封面修复链路（待裁 / 待补）：`InkShelfFragment.kt:122` 明示单组平铺；`BookshelfManageActivity` 整屏批量动作无新入口。证据行 3.2 表。
3. `MainActivity.kt:47` 残留 `CrashLogsDialog` 未使用 import（已入队）：与 `MyInkFragment.kt:19` 同类残留，可随关于页清理单一并摘。
4. `UpdateDialogLifecycleTest.kt:17-18` 把 `AboutActivity.kt` 源码文本当作更新路由断言对象（已入队）：旧类下线后此断言无源可指，需迁到新壳更新行；注意新壳目前只有正式版检查，Beta 行断言无落点。
5. 远程书库功能存废未在规格层明确（待裁）：UI 包运行期无入口，但 WebDAV 服务器配置数据与 `CacheActivity` 导出开关仍存活；删 UI 前需产品确认「远程浏览导入」是否保留，避免把仍需的配置入口一起下掉。
6. 同名前缀易误删（已入队）：`ui/book/import/remote/` 与 `model/remote/RemoteBookWebDav` 仅前缀相同；施工步骤 4 必须限定 UI 目录，数据层进保留白名单。
7. 预研口径局限（已闭环）：本单为静态扫描；桌面快捷方式、JS 桥字符串化路由、外部 PendingIntent 历史残留无法静态穷举，步骤 3 摘 manifest 前需对照 Release 清单与通知代码再核一遍。

## 九、边界与未验证项

1. 本单只读：未改 kt / xml / 脚本 / 既有文档，未跑编译与单测，未执行 git 写操作。
2. 未在真机点验三页历史入口；「活引用 = 0」结论基于全仓静态引用扫描，深链 / 快捷方式 / 通知 PendingIntent 的运行期残留按反例第 1 条兜底。
3. 字符串 / 菜单 id / 布局的第二引用清单在施工步骤 5 时逐条 grep 复核；本单给出的共享判断为静态初判。
4. 工作区存在其他代理在途改动；本单以落盘现状为准，未回退、未代改他人文件。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`：「**禁 git 写操作**：不许 add/commit/push/revert/checkout——提交由总控统一做。」
2. `docs/机制-子代理必读.md:12`：「**禁本地编译**：不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。」
3. `docs/机制-验收代理必读.md:4`：「你只判、不改。你的结论要么"逐条销项 PASS"、要么"逐条 FAIL + 可执行返工要求"，没有第三种。」
- 适用条款：只读预研——只出引用清单、替换映射、施工顺序与反例兜底，未动任何产品代码；证据不足标「待运行期确认 / 待裁」，不猜运行期走向；一条缺陷一单——Q-260 只做 A 档三 Activity 下线方案；禁 git 写操作；禁本地编译。
