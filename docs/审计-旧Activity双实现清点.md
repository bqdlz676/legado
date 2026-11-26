# 审计 · 旧 Activity 与新 Ink 栈双实现清点（Q-236，只读核查）

> 清点对象：`app/src/main` 全部 `*Activity*.kt`；方法：`git ls-files 'app/src/main/**/*Activity*.kt'` 取文件清单，`grep class` 取类声明，manifest 解析注册与 intent-filter，全仓扫 `::class` / `startActivity<X>` / `Intent(this, X::class)` 拉起方。
> 边界：只读核查，未改任何代码、未改脚本与既有文档；没拿到运行期证据的条目标「待运行期确认」，不猜。
> 锚点卡：docs/锚点/Q236-旧Activity清点.md。

## 一、清点口径与总数

- 仓内 Activity 相关 kt 文件 70 份；剔除抽象基类、`ActivityResultContract`/契约工具类后，具体 Activity 类 **62 个**；另 4 个抽象基类不可独立下线。
- 形态判据：基类为 `ComponentActivity` 且 `setContent` 自绘＝新 Ink 壳；`VMBaseActivity`/`BaseActivity`/`FragmentActivity`/`AppCompatActivity` 承载 ViewBinding 或旧视图＝旧栈；`setContent` 但挂在旧基类上＝混合 Compose 宿主。
- 运行期证据优先级：manifest 注册与 intent-filter ＞ 新栈宿主 Fragment/调度器拉起 ＞ 服务/契约/通知 PendingIntent ＞ 旧 Fragment 拉起 ＞ 仅 import/类型强转。
- 三份旧 Fragment（`MyFragment`、`ExploreFragment`、`RssFragment`）在 `MainActivity` 适配器中均无实例化引用（`MainActivity.kt:529` 只产出 `MyInkFragment`，:525 `InkShelfFragment`，:527 `ExploreInkFragment`），判定为孤儿旧宿主；仅由它们拉起的 Activity 入口视为待确认，不直接判死。

## 二、同名 / 同屏双实现判定（核心结论）

### 2.1 关于页：AboutActivity ↔ AboutInkActivity

| 项 | 旧壳 AboutActivity | 新壳 AboutInkActivity |
|---|---|---|
| 文件:行 | `ui/about/AboutActivity.kt`（行 61） | `ui/about/AboutInkActivity.kt`（行 50） |
| 基类/形态 | AppCompatActivity + setContent（混合宿主） | AppCompatActivity + setContent（混合宿主） |
| manifest | 注册（manifest :297） | 注册（manifest :301） |
| 拉起方 | `ui/main/my/MyFragment.kt:273` `startActivity<AboutActivity>()` | `ui/main/my/MyInkFragment.kt:106` `open(AboutInkActivity::class.java)` |
| 运行期判定 | 入口挂在孤儿 `MyFragment`，新栈 `MyInkFragment` 不走它；**当前 runtime 实际走 AboutInkActivity** | 新栈宿主 `MyInkFragment` 直拉，**存活** |

- 证据链：`MainActivity.kt:529` 适配器 `else -> MyInkFragment(position)`，仓内无 `MyFragment()` 实例化；`MyInkFragment.kt:106` 为关于页唯一新栈入口。
- 残留引用：`MyInkFragment.kt:19` 仍 `import AboutActivity`（未使用残留，见第五节）。

### 2.2 阅读页：ReadBookActivity ↔ InkReadActivity

| 项 | 旧壳 ReadBookActivity | 新壳 InkReadActivity |
|---|---|---|
| 文件:行 | `ui/book/read/ReadBookActivity.kt`（行 214，继承 `BaseReadBookActivity`） | `ui/book/read/ink/InkReadActivity.kt`（行 57，ComponentActivity） |
| manifest | 注册（manifest :184，exported=false） | 注册（manifest，exported=false；含 `com.samsung.android.support.REMOTE_ACTION`） |
| 拉起方 | 仓内无 `::class` / `startActivity<>` 拉起；仅被配置对话框以宿主类型强转引用 | `utils/ContextExtensions.kt:74`、`utils/FragmentExtensions.kt:102`、`ui/book/info/BookInfoActivity.kt:1516`、`receiver/MediaButtonReceiver.kt:105` |
| 运行期判定 | 无代码入口；**当前 runtime 阅读走 InkReadActivity** | 四条路由均收敛到它，**存活** |

- 路由证据：`ContextExtensions.startActivityForBook`（`ContextExtensions.kt:70-75`）按书型分流——视频 `VideoPlayerActivity`、音频 `AudioPlayActivity`、漫画 `ReadMangaActivity`，其余 `else -> InkReadActivity::class.java`；`BookInfoActivity.kt:1515-1516` 同样非漫画落 `InkReadActivity`。
- 旧壳残留耦合（下线风险点，不可直接删）：`ReadBookActivity` 仍被一批阅读配置对话框以 `activity as ReadBookActivity` / `as? ReadBookActivity` 强转：`config/AutoReadDialog.kt:49,53,105`、`BgTextConfigDialog.kt:136,145,285`、`ClickActionConfigDialog.kt:55,59`、`MoreConfigDialog.kt:57,76,122,138,174,193,197,201`、`PaddingConfigDialog.kt:81,98`、`ReadAloudControlsDialog.kt:31,48,61,64`、`ReadAloudDialog.kt:51,55`、`ReadStyleDialog.kt:40,59,68`、`ReaderMenuConfigDialog.kt:210`；另有 `page/PageView.kt:61`、`widget/dialog/BookMemoDialog.kt:47,86`、`changesource/ChangeBookSourceDialog.kt:94`、`ChangeChapterSourceDialog.kt:108` 类型判断。新壳宿主是否接管这些对话框，需运行期逐一核对。
- 时间轴：`InkTimelineActivity`（`ui/book/read/ink/InkTimelineActivity.kt` 行 51）由 `InkActionDispatcher.kt:95` 拉起，仓内无旧版 TimelineActivity 对应物，属新栈独有，不是双实现。

## 三、全量 Activity 分类清单

### 3.1 框架抽象基类（4，非屏幕，不可下线）

1. `base/BaseActivity.kt`（行 51，abstract `BaseActivity<VB>`）——被 `OpenUrlConfirmActivity`、`VerificationCodeActivity`、`QrCodeActivity`、`RuleSubActivity`、`RssFavoritesActivity` 继承。
2. `base/VMBaseActivity.kt`（行 9，abstract `VMBaseActivity<VB,VM>`）——旧栈 ViewBinding 主控基类。
3. `book/import/BaseImportBookActivity.kt`（行 27，abstract）——`ImportBookActivity`、`RemoteBookActivity` 父类。
4. `book/read/BaseReadBookActivity.kt`（行 86，abstract）——`ReadBookActivity` 父类。

### 3.2 新 Ink 栈 / 新壳存活（5）

1. `welcome/WelcomeActivity.kt`（行 23，ComponentActivity）——桌面入口，`LauncherIconHelp.kt:53,59` 反射拉起；`enterMain` 落 `MainActivity`。Launcher1-6 为其占位子类。
2. `main/MainActivity.kt`（行 86，VMBaseActivity 宿主 + 新 Fragment 栈）——`WelcomeActivity.kt:48` 拉起；书架/发现/我的三槽均为 Ink Fragment。
3. `book/read/ink/InkReadActivity.kt`（行 57）——阅读主壳，见 2.2。
4. `book/read/ink/InkTimelineActivity.kt`（行 51）——纪年时间轴，`InkActionDispatcher.kt:95`。
5. `about/AboutInkActivity.kt`（行 50）——关于页新壳，见 2.1。

### 3.3 旧栈 / 混合壳存活（被新栈宿主、服务、契约或活动桥拉起，27）

| Activity（文件 行） | 形态 | 拉起方证据（文件:行） | 判定 |
|---|---|---|---|
| `book/audio/AudioPlayActivity.kt`（85） | VMBase/View | `ContextExtensions.kt:72`、`BookInfoActivity.kt:1502`、`MediaButtonReceiver.kt:110`、`FragmentExtensions.kt:100` | 存活（音频路由） |
| `book/manga/ReadMangaActivity.kt`（89） | VMBase/View | `ContextExtensions.kt:73`、`FragmentExtensions.kt:101`、`BookInfoActivity.kt:1515` | 存活（漫画路由） |
| `video/VideoPlayerActivity.kt`（96） | VMBase/View | `ContextExtensions.kt:71`、`VideoPlayService.kt:601`、`BookInfoActivity.kt:1507`、`SourceHelp.kt:198`、`ReadRss.kt:39` | 存活（视频路由） |
| `book/import/local/ImportBookActivity.kt`（52） | BaseImport | `InkShelfFragment.kt:221`、`MyInkFragment.kt:76` | 存活（新书架入口） |
| `book/bookmark/AllBookmarkActivity.kt`（28） | VMBase/View | `MyInkFragment.kt:79` | 存活 |
| `about/ReadRecordActivity.kt`（83） | AppCompat+Compose | `MyInkFragment.kt:82` | 存活 |
| `file/FileManageActivity.kt`（22） | Fragment+Compose | `MyInkFragment.kt:85` | 存活 |
| `map/globe/host/GlobeActivity.kt`（19） | AppCompat+Compose | `MyInkFragment.kt:91` | 存活 |
| `config/ConfigActivity.kt`（42） | AppCompat+Compose | `MyInkFragment.kt:68`（带 configTag） | 存活（注意旧 MyFragment 另有 :250/:259/:263 入口，已孤儿） |
| `book/info/BookInfoActivity.kt`（134） | VMBase/View | `AddToBookshelfDialog.kt:65,86`、`ExploreShowActivity.kt:353`、`BookshelfManageActivity.kt:510` | 存活（信息页，路由枢纽） |
| `book/info/edit/BookInfoEditActivity.kt`（38） | VMBase/View | `BookInfoActivity.kt:186` | 存活（链内） |
| `book/source/manage/BookSourceActivity.kt`（50） | Fragment+Compose | `ChangeBookSourceDialog.kt:405`、`ChangeChapterSourceDialog.kt:530` | 存活（对话框链内；`ExploreFragment.kt:274` 入口已孤儿） |
| `book/source/edit/BookSourceEditActivity.kt`（73） | Fragment+Compose | `AudioPlayActivity.kt:112`、`ChangeBookSourceDialog.kt:82`、`ChangeChapterSourceDialog.kt:86` | 存活 |
| `book/source/edit/JsSourceEditActivity.kt`（38） | Fragment+Compose | `BookSourceEditActivity.kt:86`、`BookSourceActivity.kt:112` | 存活（链内） |
| `book/source/debug/BookSourceDebugActivity.kt`（23） | Fragment+Compose | `BookSourceEditActivity.kt:188`、`JsSourceEditActivity.kt:51`、`BookSourceActivity.kt:137` | 存活（链内） |
| `code/CodeEditActivity.kt`（65） | VMBase/View | `AutoTaskEditActivity.kt:113`、`HttpTtsEditDialog.kt:126`、`BookSourceEditActivity.kt:292`、`JsSourceEditActivity.kt:58` | 存活（编辑链内） |
| `replace/ReplaceRuleActivity.kt`（35） | Fragment+Compose | `ImportBookSourceDialog.kt:452`、`ImportRssSourceDialog.kt:391`、`ReadBookActivity.kt:2262` | 存活（对话框链内） |
| `replace/edit/ReplaceEditActivity.kt`（21） | Fragment+Compose | 由 `ReplaceRuleActivity`/`EffectiveReplacesDialog` 链内打开 | 存活（链内，待运行期确认入口） |
| `book/toc/TocActivity.kt`（37） | VMBase/View | `TocActivityResult.kt:11`（ActivityResultContract） | 存活（契约拉起） |
| `qrcode/QrCodeActivity.kt`（17） | Base/View | `QrCodeResult.kt:11`（契约） | 存活 |
| `file/HandleFileActivity.kt`（66） | Fragment+Compose | `HandleFileContract.kt:24`（契约） | 存活 |
| `association/FileAssociationActivity.kt`（29） | VMBase/View | manifest `SEND/SEND_MULTIPLE/VIEW`；`OnLineImportActivity.kt:58` | 存活（外部分享入口） |
| `association/OnLineImportActivity.kt`（20） | VMBase/View | manifest `VIEW`；`JsExtensions.kt:1253`、`FileAssociationActivity.kt:73`、`BookInfoActivity.kt:642`、`WebViewActivity.kt:555` | 存活（外部链接/JS 桥） |
| `association/OpenUrlConfirmActivity.kt`（10） | Base/View | `JsExtensions.kt:1259`、`ContentTextView.kt:525` | 存活（JS 桥确认框） |
| `association/VerificationCodeActivity.kt`（16） | Base/View | `SourceVerificationHelp.kt:297` | 存活（验码链内） |
| `login/SourceLoginActivity.kt`（13） | VMBase/View | `AutoTaskEditActivity.kt:130`、`AudioPlayActivity.kt:214`、`BookInfoActivity.kt:379` | 存活（登录链内） |
| `lib/permission/PermissionActivity.kt`（25） | AppCompat/View | `Request.kt:84,169,176,185`（权限契约） | 存活 |
| `receiver/SharedReceiverActivity.kt`（13） | AppCompat/View | manifest `PROCESS_TEXT`/`SEND` 外部入口；`ShortCuts.kt` | 存活（外部分享） |

### 3.4 入口仅挂孤儿旧 Fragment / 静态证据不足（待运行期确认，18）

| Activity（文件 行） | 现有静态引用 | 疑点 |
|---|---|---|
| `about/AboutActivity.kt`（61） | 仅 `MyFragment.kt:273` | 双实现旧壳，见 2.1；候选下线，见第四节 A 档 |
| `autoTask/AutoTaskActivity.kt`（25） | 仅 `MyFragment.kt:254`；内部承载 `AutoTaskScreen` Compose | 入口随孤儿 Fragment 失效；新栈无替代入口 |
| `autoTask/AutoTaskEditActivity.kt`（24） | 由 `AutoTaskActivity.kt:62,79`、`AutoTaskEditActivity.kt:66` 链内拉起 | 上游入口死则链全断 |
| `autoTask/AutoTaskDebugActivity.kt`（43） | 由 `AutoTaskEditActivity.kt:66` 链内拉起；manifest :319 | 同上 |
| `config/DictRuleActivity.kt`（30） | 仅 `MyFragment.kt:256` | 规则管理入口是否另有对话框可达，待确认 |
| `book/toc/rule/TxtTocRuleActivity.kt`（53） | 仅 `MyFragment.kt:257` | 同上 |
| `book/explore/ExploreShowActivity.kt`（36） | `ExploreFragment.kt:298`（孤儿）、`RssJsExtensions.kt:241`（JS 桥） | JS 桥是否被真实规则调用，待运行期确认 |
| `rss/manage/RssSourceActivity.kt`（59） | `RssFragment.kt:92`（孤儿） | RSS 入口链 |
| `rss/favorites/RssFavoritesActivity.kt`（33） | `RssFragment.kt:93`（孤儿） | 同上 |
| `rss/article/RssSortActivity.kt`（43） | `RssFragment.kt:199`；`RssSortActivity.kt:457` 自拉起 | 自拉起需先有入口 |
| `rss/subscription/RuleSubActivity.kt`（38） | `RssFragment.kt:136`（孤儿） | 同上 |
| `rss/source/edit/RssSourceEditActivity.kt`（70） | `RssFragment.kt:226`、`RssSortActivity.kt:54`、`ReadRssActivity.kt:169`、`RssSourceActivity.kt:128` | 链内，根入口待确认 |
| `rss/source/debug/RssSourceDebugActivity.kt`（32） | `RssSourceEditActivity.kt:264` | 链内 |
| `rss/read/ReadRssActivity.kt`（117） | `ReadRssActivity.kt:924,939` 自拉起；旧 `RssFragment` 入口 | 根入口待确认 |
| `book/cache/CacheActivity.kt`（70） | `CacheBookService.kt:55`、`ExportBookService.kt:271` 通知 PendingIntent | 通知点击可达，存活；但无前台菜单入口，待确认是否仍发通知 |
| `main/PaintActivity.kt`（78） | manifest :174 已注册；仓内无 `::class`/`startActivity` 拉起 | 可能由 PendingIntent/深链/未提交分支拉起，待运行期确认 |
| `book/manage/BookshelfManageActivity.kt`（70） | 仓内无外部拉起（仅自引用） | 孤儿候选，见第四节 A 档 |
| `book/import/remote/RemoteBookActivity.kt`（36） | 仓内无外部拉起 | 孤儿候选，见第四节 A 档 |

### 3.5 旧壳强转耦合、下线前必须先解耦（1，单列）

- `book/read/ReadBookActivity.kt`（214）：见 2.2。无拉起入口，但被 20+ 对话框/视图以宿主类型强转；在新壳 `InkReadActivity` 接管这些对话框宿主接口之前，不具备下线条件。

## 四、可安全下线旧栈清单（分级建议，未动手）

### A 档：静态证据指向无存活入口，建议先核 manifest/深链再下线

1. `book/manage/BookshelfManageActivity.kt`（行 70，VMBase/View）
   - 被谁引用：全仓无外部 `::class`/`startActivity`/契约引用；仅类内自引用。manifest :344 仍注册。
   - 下线风险：若有桌面快捷方式/外部深链/未提交分支拉起，删后入口落空；风险等级中。
   - 替代动作：先确认书架管理功能是否已并入 `InkShelfFragment` 或其他界面；无替代则补新壳入口，再摘 manifest 注册与类文件。
2. `book/import/remote/RemoteBookActivity.kt`（行 36，BaseImport 子类）
   - 被谁引用：全仓无外部拉起；`BaseImportBookActivity` 另一子类 `ImportBookActivity` 由新书架存活。manifest :378 仍注册。
   - 下线风险：远程导入入口若被 JS 桥/分享 Intent 调用则落空；风险等级中。
   - 替代动作：核 `OnLineImportActivity`/`JsExtensions` 是否覆盖远程导入；确认后摘注册与类。
3. `about/AboutActivity.kt`（行 61，双实现旧壳）
   - 被谁引用：仅孤儿 `MyFragment.kt:273`；`MyInkFragment.kt:19` 残留未用 import。
   - 下线风险：低-中；新壳 `AboutInkActivity` 已接管关于页。
   - 替代动作：确认关于页全部功能在 AboutInkActivity 齐备后，删旧壳、清 `MyInkFragment` 残留 import 与 manifest :297 注册。

### B 档：入口挂孤儿旧 Fragment，需运行期确认后再定（不建议本轮删）

- `AutoTaskActivity` / `AutoTaskEditActivity` / `AutoTaskDebugActivity` 链：确认定时任务管理在新栈是否有 Compose 入口（`AutoTaskScreen` 已存在但宿主 Activity 入口死）。
- RSS 子树（`RssSourceActivity`/`RssFavoritesActivity`/`RssSortActivity`/`RuleSubActivity`/`RssSourceEditActivity`/`RssSourceDebugActivity`/`ReadRssActivity`）：确认 RSS 功能是否仍对外保留；若保留，需在 Ink 栈补入口；若裁撤，整链连同 manifest 注册一并清。
- `DictRuleActivity`、`TxtTocRuleActivity`、`ExploreShowActivity`、`CacheActivity`、`PaintActivity`：按第三节 3.4 疑点逐项跑一次运行期路由（通知点击、JS 桥调用、深链）再判。

### C 档：存活不动

- 3.2 新 Ink 栈 5 个；3.3 旧栈/混合壳存活 27 个；3.1 抽象基类 4 个。

## 五、新发现问题

1. 旧阅读壳 `ReadBookActivity` 无拉起入口但仍被 20+ 对话框强转宿主类型（待裁）：见 2.2 清单；新壳 `InkReadActivity` 未实现这批对话框期望的 `bottomDialog`/`upPageAnim`/`showReadAloudControls` 等宿主接口前，旧壳不能删。建议补一份「对话框宿主接口对照」，逐一把调用方迁到新壳协议。
2. 三份旧 Fragment 已成孤儿但仍挂着一批旧 Activity 入口（已入队）：`MyFragment`/`ExploreFragment`/`RssFragment` 无实例化引用，其 `startActivity<>` 目标构成 B 档待确认清单；建议调度层确认旧 Fragment 裁撤计划后，批量复核对应 Activity 去留。
3. `MyInkFragment.kt:19` 残留 `import AboutActivity` 未使用（已修候选，待随 AboutActivity 下线一并清）；同类未用 import 建议交由 lint 统一扫。
4. `PaintActivity` 已注册 manifest 但仓内无代码拉起（待裁）：需运行期确认是否存在 PendingIntent/深链/未提交分支引用；在确认前不得按孤儿删除。
5. 清点口径局限（已记录）：本次为静态扫描，JS 桥（`JsExtensions`/`RssJsExtensions`）字符串化路由、通知 PendingIntent、外部 intent-filter 入口无法静态穷举；B 档条目均须运行期点击复核，结论以运行期证据为准。
6. 计数口径提示（已记录）：派单所述「47 个旧 Activity」与仓内实测不符——具体 Activity 62 个，其中旧栈/混合壳约 45 个、新 Ink 栈 5 个、双实现旧壳 2 个；47 可能为某一历史时点或某一子集口径，建议总控核对原始计数来源。

## 六、边界与未验证项

1. 本单只读：未改 kt/脚本/文档，未跑编译与单测，未执行 git 写操作。
2. 未在真机/模拟器点验任何页面跳转；运行期路由以代码引用、manifest、契约为准，存疑条目均标「待运行期确认」。
3. 测试源集 `app/src/test` 不在本次清点范围；契约类 `TocActivityResult`、`SelectImageContract`、`ActivityResultLauncherAwait` 不计入 Activity 屏幕。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=e9560a5c333b；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`：「不许 add/commit/push/revert/checkout——提交由总控统一做。」
2. `docs/机制-子代理必读.md:12`：「不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。」
3. `docs/机制-验收代理必读.md:4`：「你只判、不改。」
- 适用条款：只读核查——只出判定表与建议，未动任何代码；一条缺陷一单——Q-236 只清点 Activity 双实现；禁 git 写操作；禁本地编译；证据不足写「待运行期确认」，不猜运行期走向。
