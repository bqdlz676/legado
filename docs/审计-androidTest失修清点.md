# 审计-androidTest 失修清点（Q-292）

- 范围：`app/src/androidTest/**` 全量清点 + 逐条 import 机检 + 三类处置判定 + CI 盲区与反例推演。
- 边界：只读审计；沙箱无 JDK，未跑 `./gradlew` / kotlinc / 编译；未改任何源码、测试、脚本；未做 git 写操作。
- 机检手段：`find` / `grep` / `python3` 声明索引比对；声明索引覆盖 `app/src/main/java`、`app/src/debug/java`、`app/src/cronetRuntime/java`、`app/src/pdfRuntime/java`、`modules/book/src/main/java`、`modules/rhino/src/main/java`。
- 日期：2026-11-26。

## 一、总量计数（实算）

- androidTest 文件总数：75（`.kt` 63 个；`assets/` 12 个，含 1 个 `.gitattributes`）。
- `@Test` 用例总数：341（`grep -rh "^\s*@Test" app/src/androidTest --include=*.kt | wc -l` = 341；逐文件计数与脚本结果零偏差）。
- import 总数：2356。
  - 第三方/框架跳过：1571（`android.*` / `androidx.*` / `java.*` / `javax.*` / `kotlin.*` / `kotlinx.*` / `org.junit` / `org.hamcrest` / `okhttp3` / `okio` 等前缀）。
  - 通配 `.*` 跳过：0。
  - `R` 生成类跳过：38（aapt 生成，源码无声明属正常）。
  - 主仓符号解析命中：728。
  - 布局生成 binding 且 XML 存在：4。
  - 包名不一致待裁（pkg-mismatch）：0。
  - 未解析符号：11。
  - 布局缺失导致 binding 无法生成：4。
- 含硬错误 import 的文件数：9。
- 计数闭合校验：1571 + 0 + 38 + 728 + 4 + 0 + 11 + 4 = 2356。

## 二、全量文件清单（63 个 `.kt`，逐条用途一句话）

### 根包 `io.legado.app`

1. `AndroidJsTest.kt`（3）— Rhino/Android JS 引擎包名与 map 求值冒烟。
2. `HttpTest.kt`（1）— HTTP 下载、WebView 下载与 DownloadManager 通道。
3. `HttpTtsTest.kt`（1）— HTTP TTS 音频获取通道冒烟。
4. `MigrationTest.kt`（12）— 数据库 93→104 各版本迁移：自动任务、高亮规则与归属、封面拆分、源替换范围、RSS 分页规则等回归。
5. `UpdateTest.kt`（2）— 应用更新检查（beta / 正式通道）。

### `data/`

6. `data/BookMemoTest.kt`（5）— 书籍笔记：迁移保留、重导入保留 markdown 与时间戳、源替换搬迁笔记、清除恢复不复活旧文本、失败事务保留旧态。
7. `data/BookSourceCheckApiTest.kt`（2）— 书源校验 API：按请求 revision 返回、空选择不全量返回且非法选择被拒。
8. `data/BookSourceCheckStateTest.kt`（5）— 校验状态机：完成/中断条目重开存活、旧结果拒收、状态不污染源、104/105 升级初始化。
9. `data/HighlightRuleGroupTest.kt`（2）— 高亮规则分组：迁移保留既有规则内容与身份、分组操作作用域隔离。
10. `data/ReadRecordAuthorIdentityTest.kt`（20）— 阅读记录作者身份：106→113 迁移、设备/作者聚合查询、封面 CAS、备份恢复精确合并、并发写入只写本设备本作者。

### `help/book/`

11. `help/book/ContentReversalCacheTest.kt`（5）— 正文反转图片缓存：跨书代际与书架作用域、旧像素/在飞响应被新代际丢弃、旧响应被拒、反转还原与新下载走真实缓存内容、失败写不翻转状态。

### `help/http/`

12. `help/http/BackstageWebViewLifecycleTest.kt`（5）— 后台 WebView 池生命周期：取消启动不在加载前后取借、取消的 HTML/嗅探回调不影响下一借用者页面、空结果重试到真实 JS 值、在用嗅探脚本仍返回资源并可覆盖 URL。

### `help/source/`

13. `help/source/SourceNavigationUiTest.kt`（1）— 源页导航：opt-in 拦截规则类 UI，保留 HTTP 与交互详情可用。

### `help/storage/`

14. `help/storage/BackupOptionsTest.kt`（7）— 备份/恢复选项：本地/WebDAV 归档、自动间隔与失败不推进成功时间、未授权不上传、归档还原历史与双封面、显式自动设置走默认值、阅读器控件显式 false 保留。**【坏文件，见 §三/§四】**
15. `help/storage/BackupRestoreConcurrencyTest.kt`（2）— 备份恢复并发：本地恢复与并发备份/启动清理共存、排队取消不解压也不阻塞下次恢复。
16. `help/storage/CoverTitleAdaptiveBackupRestoreTest.kt`（5）— 封面自适应/字号/字体文件备份还原：忽略项不还原自适应、已删安装字体不阻断完整备份、样式备份连字体文件一起还原、自定义字号成组还原。
17. `help/storage/PreferenceSnapshotTest.kt`（1）— 偏好快照重复取用时不复用缓存文件。
18. `help/storage/ReadPaddingLinkBackupRestoreTest.kt`（2）— 阅读器边距/链接偏好独立备份还原，样式 zip 导出导入保留链接与旧默认 false。

### `model/`

19. `model/ReadBookCallbackLifecycleTest.kt`（1）— 阅读回调生命周期：销毁上一阅读器不取消当前阅读器在途工作。

### `model/localBook/`

20. `model/localBook/PdfOutlineTest.kt`（5）— PDF 大纲解析：旧版/名称树目的地与本地 goto、ContentUri 压缩对象不改已存进度、嵌套标题共享页与目的地顺序、重复大纲字典不死循环、无大纲文档空节点。

### `model/webBook/`

21. `model/webBook/BatchContentDownloadTest.kt`（10）— 批量章节下载：包装器/裸 JS 存真实章节文件、替换走章节绝对 URL 拒空输出、延迟批不覆盖权威编辑、阅读与朗读加入队列且取消一个等待者保留生产者、取消借用手动活回退单章、部分脚本失败只重试缺失章、懒启动前取消释放全部占用、手动缓存下载已在阅读器文本中的图、先到内容复查不再发网络请求、批关闭后回调拒收。
22. `model/webBook/SourceContentCompatibilityTest.kt`（3）— 源内容兼容：正文节点与替换规则、JS base64 与书名替换规则保留内容、目录反转不改源刷新顺序与章节身份。

### `service/`

23. `service/HttpTtsCacheInstrumentedTest.kt`（2）— HTTP TTS 缓存：预取音频同键命中不再开上游、失败上游不留可播放替代且成功重试可复用。

### `ui/about/`

24. `ui/about/ReadRecordHistoryTest.kt`（18）— 阅读记录历史：移除旧作者名保留时长与最新快照、长按编辑跨重建不改时长、元数据同步保留书签身份、固定卡可滚动行级动作、日夜兜底封面用真实封面与未设白色、布局切换与日夜开关刷新摘要行、空封面保持白与暗卡可辨。**【坏文件】**

### `ui/association/`

25. `ui/association/RuleSelectionShareTest.kt`（4）— 规则选择分享：高亮/替换/Toc/字典选中规则打包成可导入信封。**【坏文件】**
26. `ui/association/SharedFileImportTest.kt`（14）— 共享文件导入：书单确认后走启用源搜索并持久化匹配书、高亮/替换文件预览路由、Toc 预览改内容确认后才持久化、备份 zip 确认后恢复书源规则设置、txt/epub/pdf/书 zip 持久落地并打开真实阅读器、批次选择跨重建与文件夹结果保留。
27. `ui/association/SourceImportFilterUiTest.kt`（5）— 源导入过滤：书/RSS 过滤经编辑替换与重建保持、在线自动导入单预览与状态、特殊过滤匹配管理分组与登录语义、分组记忆书/RSS 共享且关闭即清。
28. `ui/association/SourceManualReplacementUiTest.kt`（6）— 源手动替换：书/RSS 手动与生效菜单走原始候选、替换菜单在重建/后台后恢复、自动源选项控制手动菜单、失败模式切换保留手动结果、旧手动标志与备份不变。

### `ui/book/audio/`

29. `ui/book/audio/AudioLyricParsingTest.kt`（2）— 歌词解析：缺失与仅元数据字幕无条目、元数据不遮蔽有效定时字幕。

### `ui/book/explore/`

30. `ui/book/explore/ExploreCategoriesTest.kt`（8）— 发现页分类：返回发现刷新共享阅读记录指示、全局开关渲染平衡行并重开存活、备份还原开关与旧备份复位、重叠页置顶保留可见书、分类切换拒收迟到响应且重建后保留页。

### `ui/book/manga/`

31. `ui/book/manga/MangaReadingDirectionTest.kt`（12）— 漫画阅读方向：旧页不覆盖请求章节位置、横排默认 LTR 手势/点按/按键、RTL 跨章节手势/点按/按键翻页、菜单开关跨重建重开恢复进度、竖排保持前向滚动并隐藏方向选项、RTL 吸附与动画关闭仍翻页、RTL 遍历默认章节标题分隔符双向、设置备份还原方向且旧备份用 LTR。

### `ui/book/read/`

32. `ui/book/read/ContentEditSearchTest.kt`（7）— 正文编辑搜索：纯文本编辑与冷恢复保留图片代码、隐藏光标在切换/还原原始模式时折叠、重置使已完成与待处理搜索失效、字面/正则/大小写/循环导航不改草稿、编辑刷新匹配不移动选区并存真实草稿、位置条拖动文本不动光标并随编辑更新、重建保留搜索草稿与位置（横竖屏截图）。
33. `ui/book/read/ContentReversalUiTest.kt`（4）— 正文反转 UI：主题变化与显式预载范围刷新、资源失败 loading 态与固定回退。
34. `ui/book/read/EpubHierarchyNavigationTest.kt`（5）— EPUB 层级导航：reporter 十节点与真实内容 URL、ncx/Epub3 容器别名跨资源片段保边界、旧元数据与生成缓存修复不改编辑与章节身份、旧导入目录支持递归 UI 并保留进度书签与阅读器目标、旧物理反转缓存保索引且容器点击不能开上一内容。
35. `ui/book/read/HighlightTriggerUiTest.kt`（6）— 高亮触发：双击需同目标并打开既有规则管理、长按选中链接图片与既有模式行为保留、漏处理拖拽保留选中且点按仍清除、手动高亮可清各通道并在重建后复用笔记、旧重复样式同清且不丢笔记与其它区间、同词不同位置保持独立高亮。
36. `ui/book/read/MouseWheelScrollTest.kt`（4）— 鼠标滚轮/旋钮滚动：默认半速/双速移动真实内容且切换停止移动、旋钮按滚动轴且非法/横屏事件不动、翻页模式防抖突发单页不同速、原生速度控件跨重建持久化并跟随滚轮开关。
37. `ui/book/read/ParagraphReadBackgroundUiTest.kt`（3）— 段落阅读背景：启用阅读器首开重建与尺寸变化保色、配置事件与模式切换页/段背景等价、内边距背景保窗口 insets 且不改共享 drawable。
38. `ui/book/read/ParagraphReadHighlightUiTest.kt`（4）— 段落高亮：朗读进度色原生文本不重绑不移列表、标题与长 HTML 段落用 UTF-16 坐标不拆 emoji、清除重绑保留源格式并拒收其它章节、回收项只恢复当前高亮且 E Ink 走下划线。
39. `ui/book/read/PdfOutlineNavigationTest.kt`（1）— PDF 大纲导航：大纲保留数据库位置并点击打开真实目标图。
40. `ui/book/read/PdfPagePositionTest.kt`（1）— PDF 页面位置：标题与 Unicode 分列后找到真实图偏移。
41. `ui/book/read/PdfZoomNavigationTest.kt`（4）— PDF 缩放导航：竖屏 pinch/平移/配置点击/章节过渡保比例、横屏双页平移旋转保缩放、reporters 教材单双页区域清晰、缩放滚动平移不改阅读位置且 pinch 回退恢复滑动。
42. `ui/book/read/ReadAloudMenuUiTest.kt`（15）— 朗读菜单：固定/可动控制条播放与暂停态长按停服务。
43. `ui/book/read/ReadAloudScaleUiTest.kt`（4）— 朗读条缩放：宽度控件整条与圆点触控边界匹配、旧尺寸与选中宽度跨阅读器重建存活、拖动位置跨禁用与重置保留、旧小宽度钳制且不透明度走默认。
44. `ui/book/read/ReadPaddingControlsTest.kt`（1）— 阅读边距控件：紧凑控件留出拖动空间、全区域按钮共享已保存模式。
45. `ui/book/read/ReaderSourceReimportUiTest.kt`（7）— 阅读器源重导入：当前库源预览取消不导入、确认导入跨重建且下次阅读请求用新规则、完成的旧导入器不能替换另一本书活动源、菜单配置与设置备份保留重导入动作。
46. `ui/book/read/ReadingLayoutTransitionTest.kt`（1）— 阅读版式过渡：封面滚动封面过渡后 supplied 样式保持整页。
47. `ui/book/read/TitleFontWeightRenderingTest.kt`（17）— 标题字重渲染：旧/独立字重到达真实 paint 与画布、高亮 pill 字形留在边框内并用页边距、仅填充 pill 整行绘制字形留边、高亮 pill 在透明审阅图两侧保持清晰。
48. `ui/book/read/TocReverseNavigationTest.kt`（4）— 目录反转导航：可见标题即时反转并重开保留、反转保留卷成员与序章、返回阅读器保留实际章节位置与书签目标、旧存储反序保留章节索引与解析偏好。

### `ui/book/searchContent/`

49. `ui/book/searchContent/SearchContentDirectionUiTest.kt`（4）— 搜索内容方向：还原结果记住查询与范围菜单立即双向搜索、再次提交取消旧任务不混结果、重建保留最新查询、重建保留未匹配/取消查询且不重启 Intent 搜索。

### `ui/book/source/`

50. `ui/book/source/BookSourceCheckUiTest.kt`（5）— 书源管理校验 UI：书架计数跟踪当前源与书架变化且不丢选择、状态过滤交集分组跨重建、默认关闭状态与导航开关纳入真实设置备份。**【坏文件】**
51. `ui/book/source/SourceDragOrderUiTest.kt`（5）— 源拖拽排序：过滤/降序书源、RSS、替换拖拽保留隐藏顺序、持有的书源拖到已删目标不移动他源。**【坏文件】**
52. `ui/book/source/SourceOrderMetadataUpdateTest.kt`（2）— 源顺序元数据更新：陈旧 RSS/替换开关与选择保留已提交顺序与元数据。**【坏文件】**

### `ui/browser/`

53. `ui/browser/WebViewFullscreenTest.kt`（5）— WebView 全屏：播放器全屏按钮旋转返回竖屏、默认横屏并还原原方向、全屏前加锁排队不旋转浏览器、页面可请求竖屏并解锁方向、返回退出全屏且重入隐藏只通知一次。

### `ui/code/`

54. `ui/code/CodeSelectionUiTest.kt`（21）— 代码编辑选中：长按多行/词选中并仅分享该文本、词外长按选中新词、只读长按词外重选、首次长按选词点按清除、只读代码可分享选中不暴露剪切粘贴、搜索结果长按后词外重选、原生复制剪切全选用当前选区、原生搜索仅分享选中文本且返回保留编辑器。**【坏文件】**

### `ui/font/`

55. `ui/font/FontSelectionInflationTest.kt`（1）— 字体条目用 AppCompat 主题 inflation。

### `ui/highlight/`

56. `ui/highlight/HighlightGroupUiTest.kt`（6）— 高亮分组 UI：字号与负间距经真实样式对话框持久化并可重置、pill 边距编辑持久化并可重置。

### `ui/main/explore/`

57. `ui/main/explore/ExploreExpansionUiTest.kt`（2）— 发现展开：冷缓存与切换展开不重叠他源、帧探针识别原位移动画。
58. `ui/main/explore/ExploreRefreshUiTest.kt`（4）— 发现刷新：内部开关在可见控件下伸缩、发现控件刷新回调跨垃圾回收保留、发现输入框保持在键盘上方可见。

### `ui/main/my/`

59. `ui/main/my/MyPageCustomizationTest.kt`（2）— 我的页定制：选择器只移动选中项且更多保留既有动作、真实配置还原与旧备份还原可见页。

### `ui/widget/dialog/`

60. `ui/widget/dialog/BookMemoDialogTest.kt`（2）— 笔记对话框：默认关菜单 markdown 草稿轮转保存清除、真实 Lan 归档还原笔记不复活已清内容。
61. `ui/widget/dialog/BottomWebViewDialogShowTest.kt`（13）— 底部 WebView 对话框：重复自定义回调取动态页可重开、重复请求不堆叠且关闭可立即重开、排队后台请求只开一窗、停止/销毁宿主拒新窗、底窗对齐底部、配置高度保持锚定、百分比高度无 fit-to-content 仍贴底、测得高度与 fit 模式变化保持贴底、显式偏移稀疏更新存活、全屏延迟高度更新仍回贴底部。

### `ui/widget/image/`

62. `ui/widget/image/CoverStylePreviewUiTest.kt`（2）— 封面字体样式预览：滚动设置更新真实预览并跨重建存活、字体选择独立且日夜预览用真实自定义背景。**【坏文件】**
63. `ui/widget/image/CoverTitleAdaptiveUiTest.kt`（8）— 封面标题自适应：自适应切换改实际渲染像素、自定义字号改像素且禁用还原两种原样式、四类字号触达横竖排版、等号字号在每种布局下等字距几何、选中字体改像素默认还原全部布局、字号盘四选择器跨重建持久化、记录封面两张图经 ActivityResult 复制、封面设置开关即时更新运行时。**【坏文件】**

### `assets/`（12 个）

- `database-112-metadata.sql` — `ReadRecordAuthorIdentityTest` 112 迁移夹具。
- `issue1074-containers-fragments.epub` — `EpubHierarchyNavigationTest` / `SharedFileImportTest` 容器碎片夹具。
- `issue1074-nav3-containers.epub` — `EpubHierarchyNavigationTest` EPUB3 nav 容器夹具。
- `issue1074-reporter.epub` — `EpubHierarchyNavigationTest` 十节点 reporter 夹具。
- `issue1074-fixtures.md` — 测试代码中无按名引用，待裁陈旧夹具（见 §七-2）。
- `issue1180-content-rules.json` — `SourceContentCompatibilityTest` 内容规则夹具。
- `issue1213-cover.json`、`issue1213-scroll.json` — `ReadingLayoutTransitionTest` 以 `issue1213-$name.json` 动态名加载的封面/滚动版式夹具。
- `issue1255-transparent-cat.svg` — `TitleFontWeightRenderingTest` 透明 SVG 字形夹具。
- `pdf-outline-compressed.pdf` — `PdfOutlineTest` 压缩对象大纲夹具。
- `pdf-outline-direct-named.pdf` — 测试代码中无按名引用，待裁陈旧夹具（见 §七-2）。
- `.gitattributes` — 标记 `*.pdf` 为 binary。

## 三、未解析 import 清单（硬错误，逐条 `文件:行号`）

判定口径：本地包前缀 `io.legado.` / `com.script.` / `ag2s.`；在主仓各 main 源集声明索引（`class`/`object`/`interface`/`enum class`/`annotation class`/`typealias`/`fun`/`val`/`var`，含扩展 receiver 与 owner 成员）中零命中即未解析；`databinding.*` 按 `res/layout` XML 存在性判定。

### 3.1 未解析符号（11 条）

1. `help/storage/BackupOptionsTest.kt:32` — `io.legado.app.ui.config.BackupConfigFragment`（主仓零声明；仅 `sections/BackupSection.kt:48` KDoc 注释提及旧名）。
2. `ui/association/RuleSelectionShareTest.kt:24` — `io.legado.app.ui.dict.rule.DictRuleAdapter`（零声明；新宿主 `DictRuleActivity` 配 `DictRuleListViewModel` + `DictRuleScreen.kt`）。
3. `ui/association/RuleSelectionShareTest.kt:26` — `io.legado.app.ui.replace.ReplaceRuleAdapter`（零声明；新宿主 `ReplaceRuleActivity` 配 `ReplaceRuleListViewModel` + `ReplaceRuleScreen.kt`）。
4. `ui/book/source/BookSourceCheckUiTest.kt:42` — `io.legado.app.ui.book.source.manage.BookSourceAdapter`（零声明；新栈 `SourceManageViewModel` + `SourceManageScreen.kt` + `SourceManageRow.kt`）。
5. `ui/book/source/SourceOrderMetadataUpdateTest.kt:16` — `io.legado.app.ui.replace.ReplaceRuleAdapter`（零声明，同 3）。
6. `ui/book/source/SourceOrderMetadataUpdateTest.kt:17` — `io.legado.app.ui.replace.ReplaceRuleViewModel`（零声明；改名 `ReplaceRuleListViewModel`）。
7. `ui/code/CodeSelectionUiTest.kt:81` — `io.legado.app.ui.book.source.edit.BookSourceEditAdapter`（零声明；新栈 `SourceEditScreen.kt`）。
8. `ui/code/CodeSelectionUiTest.kt:82` — `io.legado.app.ui.book.source.edit.BookSourceEditViewModel`（零声明；改名 `SourceEditViewModel`）。
9. `ui/widget/image/CoverStylePreviewUiTest.kt:26` — `io.legado.app.ui.config.CoverFontConfigFragment`（零声明；仅 `sections/CoverFontSection.kt:36` KDoc 提及旧名）。
10. `ui/widget/image/CoverTitleAdaptiveUiTest.kt:36` — `io.legado.app.ui.config.CoverConfigFragment`（零声明；仅 `sections/CoverSection.kt:18` KDoc 提及旧名）。
11. `ui/widget/image/CoverTitleAdaptiveUiTest.kt:37` — `io.legado.app.ui.config.CoverFontConfigFragment`（零声明，同 9）。

### 3.2 布局缺失导致 binding 无法生成（4 条）

12. `ui/about/ReadRecordHistoryTest.kt:45` — `io.legado.app.databinding.ActivityReadRecordBinding`；`res/layout/activity_read_record.xml` 缺失。
13. `ui/about/ReadRecordHistoryTest.kt:46` — `io.legado.app.databinding.ItemReadRecordDisplayBinding`；`res/layout/item_read_record_display.xml` 缺失。
14. `ui/book/source/BookSourceCheckUiTest.kt:33` — `io.legado.app.databinding.ItemBookSourceBinding`；`res/layout/item_book_source.xml` 缺失。
15. `ui/book/source/SourceDragOrderUiTest.kt:19` — `io.legado.app.databinding.ItemBookSourceBinding`；`res/layout/item_book_source.xml` 缺失。

### 3.3 对照：布局存在、binding 可正常生成（4 条，不计错误）

- `ui/book/explore/ExploreCategoriesTest.kt:27` — `ActivityExploreShowBinding` ← `activity_explore_show.xml` 存在。
- `ui/book/manga/MangaReadingDirectionTest.kt:35` — `ActivityMangaBinding` ← `activity_manga.xml` 存在。
- `ui/font/FontSelectionInflationTest.kt:10` — `ItemFontBinding` ← `item_font.xml` 存在。
- `ui/highlight/HighlightGroupUiTest.kt:47` — `ItemHighlightRuleBinding` ← `item_highlight_rule.xml` 存在。

## 四、三类处置判定（9 个坏文件逐条）

判定分桶：**可修**＝新结构有等价 API/状态出口，可做旧→新对照迁移；**判废**＝整文件只为已删功能存在，新架构无等价断言点；**待裁**＝信息不足，列明缺什么才能判。

### 4.1 可修（5 个）

#### A. `ui/association/RuleSelectionShareTest.kt`（4 @Test）

- 缺失：`DictRuleAdapter`（L24）、`ReplaceRuleAdapter`（L26）。
- 依据：分享信封序列化走高亮/替换/Toc/字典各自规则实体，实体与导入对话框存活；两处强转 Adapter 仅为读取勾选状态，新栈有 ViewModel 选中态可替代。
- 旧 → 新对照：
  - `recycler.adapter as ReplaceRuleAdapter` → `ReplaceRuleListViewModel` 的 uiState 选中 id 集合；选择操作 `enableSelection/disableSelection` 一类入口对应 `toggleSelect` / `selectAll` / `revertSelection` / `clearSelection`（`ui/replace/ReplaceRuleListViewModel.kt:116-135`）。
  - `as DictRuleAdapter` → `DictRuleListViewModel` + `DictRuleScreen.kt`。
  - RecyclerView 行内文案检视 → Compose 节点定位；当前新屏未发现 testTag，需先补定位钩子。
- 删除后失去覆盖（若误判废）：四类规则"仅分享勾选项"的导入信封字段完整性与预览样本。

#### B. `ui/book/source/BookSourceCheckUiTest.kt`（5 @Test）

- 缺失：`BookSourceAdapter`（L42）、`ItemBookSourceBinding`（L33）。
- 依据：书架计数、勾选态、调试文案的数据源在 `SourceManageViewModel`（书架计数绑定见 `SourceManageViewModel.kt:143` `bindBookshelfCounts`）；行渲染收口在 `SourceManageRow.kt`。
- 旧 → 新对照：
  - `recycler.adapter as BookSourceAdapter` → `SourceManageViewModel` uiState。
  - `ItemBookSourceBinding.bind(row).cbBookSource / tvBookshelfCount / ivDebugText` → `SourceManageRow.kt` Compose 节点；行 ViewBinding 随旧 XML 一并移除，无等价 binding 可取。
- 删除后失去覆盖：书源勾选态、书架计数文案、调试文本可见性的真机行级回归。

#### C. `ui/book/source/SourceOrderMetadataUpdateTest.kt`（2 @Test）

- 缺失：`ReplaceRuleAdapter`（L16）、`ReplaceRuleViewModel`（L17）。
- 依据：RSS 分支走 `RssSourceAdapter` / `RssSourceViewModel`（旧栈存活，未在缺失清单）；Replace 分支 ViewModel 改名存活，Adapter 行检视改 VM 状态断言即可。
- 旧 → 新对照：
  - `ReplaceRuleViewModel` → `ReplaceRuleListViewModel`。
  - `enableSelection(stale)` / `disableSelection(stale)` → `toggleSelect` / `selectAll` / `revertSelection` / `clearSelection`。
  - Adapter 行状态读取 → VM uiState 读取。
- 删除后失去覆盖：替换规则陈旧开关与选择状态下，已提交顺序与元数据保持不变。

#### D. `ui/code/CodeSelectionUiTest.kt`（21 @Test）

- 缺失：`BookSourceEditAdapter`（L81）、`BookSourceEditViewModel`（L82）。
- 依据：21 条中仅 1 条（`sourcePreviewEditsReturnToTheSameBookAndRssCandidateWithoutLosingLongCode`）耦合已删 Adapter/VM；其余 20 条经 `CodeEditActivity` / `CodeEditViewModel`（import 解析存活）。
- 旧 → 新对照：
  - `BookSourceEditViewModel` → `SourceEditViewModel`；`BookSourceEditAdapter` → `SourceEditScreen.kt` 行渲染。
  - 文件内反射 `CodeEditActivity::class.java.getDeclaredMethod("search")` 等（全文件反射点见 §七-3）需逐条核对新 Activity 方法名，不在 import 机检覆盖内。
- 删除后失去覆盖：源码编辑页长文本预览候选回显（1 条）；其余 20 条选中/分享行为不受本次 import 缺失影响，但 Compose 化后仍需运行期复核。

#### E. `ui/widget/image/CoverTitleAdaptiveUiTest.kt`（8 @Test）

- 缺失：`CoverConfigFragment`（L36）、`CoverFontConfigFragment`（L37）。
- 依据：`CoverImageView` 视图存活（`ui/widget/image/CoverImageView.kt`）；8 条中 5 条位图直测不依赖 Fragment；3 条设置入口用例走 Fragment / Preference 树。`ConfigTag.COVER_CONFIG` / `COVER_FONT_CONFIG` 常量存活（`ConfigTag.kt:13-14`）。
- 旧 → 新对照：
  - `supportFragmentManager.findFragmentByTag(ConfigTag.COVER_CONFIG) as CoverConfigFragment` → `CoverSection(actions)`（`ConfigActivity.kt:136` setContent 宿主分发）。
  - `as CoverFontConfigFragment` / `scrollToPreference(key)` → `CoverFontSection(actions)`。
  - 旧 NumberPicker 偏好弹窗 → Compose `NumberPickerSheet`（`ui/config/settings/NumberPickerSheet.kt`，`CoverFontSection.kt` 已 import）。
- 删除后失去覆盖：封面设置开关即时回写 `BookCover` 运行时态、四字号盘跨重建持久化、记录封面图片经 ActivityResult 复制这三条设置链路；位图级字形/字重/字体渲染断言可保留。

### 4.2 判废（1 个）

#### F. `ui/widget/image/CoverStylePreviewUiTest.kt`（2 @Test）

- 缺失：`CoverFontConfigFragment`（L26）。
- 判废理由：两条用例整条链路是"启动 `ConfigActivity`(COVER_FONT_CONFIG) → 强转 Fragment → 读 `preferenceScreen.preferenceCount` / `listView` 偏好键序 → 取 `CoverImageView` 预览位位图"。新 `CoverFontSection` 为 Compose 自绘样例卡：`CoverFontSection.kt` 全文无 `CoverImageView` 引用，`res/layout` 无 `cover_preview_short` / `cover_preview_long` id；旧偏好遍历 API（preferenceScreen 计数、`scrollToPreference`、`listView`）在 Compose 壳不存在，新屏也未提供等效预览位图出口。
- 删除后失去覆盖：封面样式开关（横排/自适应/标点保留/自定义字号）实时反映到预览；字体选择独立于阅读器字体；日夜自定义背景进入预览；重建后预览逐位一致。
- 备注：封面位图渲染本体由 E 文件直测视图的部分承接一部分；设置页预览联动目前无替代断言点。

### 4.3 待裁（3 个）

#### G. `help/storage/BackupOptionsTest.kt`（7 @Test）

- 缺失：`BackupConfigFragment`（L32）。
- 现状：5 条纯备份/恢复/WebDAV 逻辑用例（自动间隔与失败不推进成功时间、未授权不上传、归档还原历史与双封面、显式自动设置走默认值、阅读器控件显式 false 保留）只依赖 `Backup` / `Restore` / `AppWebDav` / `BackupConfig`（import 解析存活）；2 条 UI 用例强转 Fragment 并读 `Preference.parent/order` / `findPreference`。
- 缺什么信息才能判：新 `BackupSection` 对对应设置项是否暴露可 Compose 定位的节点或可读状态；若要保留 UI 断言，需确认 Compose 测试基建（`createComposeRule`）与 testTag 规划。
- 建议拆分：逻辑 5 条抽出为不依赖 Fragment 的 instrumented 用例；UI 2 条待 Compose 钩子落地后再判可修或判废。

#### H. `ui/about/ReadRecordHistoryTest.kt`（18 @Test）

- 缺失：`ActivityReadRecordBinding`（L45）、`ItemReadRecordDisplayBinding`（L46）；两份布局 XML 均缺失；`ReadRecordActivity` 已改 ComposeView（`ReadRecordActivity.kt:93-94`），旧内部 `RecordAdapter` 已移除（`ReadRecordActivity.kt:81` 注释）。
- 现状：DAO 聚合 / 作者归属 / 备份合并类用例依赖 `data.entities` 扩展函数（`saveWithCover` / `withDisplayMetadata` / `saveReadRecordSnapshot`，import 解析存活）；UI 用例经 binding 字段与反射 `getBinding()`（L918）取视图。
- 缺什么信息：新 Compose 记录页行结构与可定位节点；反射 `getBinding()` 在新 Activity 已不存在，需确认哪些 UI 断言可改 VM state 断言。
- 建议拆分：数据/备份类用例下沉保留；UI 行级断言待 Compose 钩子确认后再判可修或判废。

#### I. `ui/book/source/SourceDragOrderUiTest.kt`（5 @Test）

- 缺失：`ItemBookSourceBinding`（L19）。
- 现状：三分支 Book / RSS / Replace；RSS 分支仍旧 RecyclerView（`RssSourceAdapter` 存活）；Book / Replace 分支已 Compose。VM 层顺序逻辑存活：`moveRelativeTo`（`utils/CollectionExtensions.kt:3`）、`upOrder` 落库（`SourceManageViewModel.kt:332-334`）、`canDrag` 状态（`SourceManageState.kt:118`）；但 Compose 屏拖拽手势入口与旧 ItemTouchHelper 不等价，`SourceManageScreen.kt` / `SourceManageSheets.kt` 未见拖拽手势钩子（仅 `canDrag` 按钮态，`SourceManageSheets.kt:409,414`）。
- 缺什么信息：新 Compose 列表是否保留拖拽排序手势；若仅保留置顶/按钮排序，旧拖拽顺序用例的等价断言点需重定。

### 4.4 分桶计数

- 可修：5（A、B、C、D、E）。
- 判废：1（F）。
- 待裁：3（G、H、I）。

## 五、CI 盲区与两种加固方案

### 5.1 盲区事实（命令取证）

- `verify.yml:140-144`："Compile unit test sources" 执行 `:app:compileAppDebugUnitTestKotlin :app:compileAppDebugUnitTestJavaWithJavac`；后续 test-shard 跑 JVM 单测。全程不编译 androidTest 源集。
- `package.yml` 调 `.github/actions/build-apks/action.yml:10` `assembleDebug`、`:14` `assembleRelease`；assemble 只编 main 并打包 APK，不编译 androidTest。
- `.github/workflows/` 全量 grep `AndroidTest|androidTest` 零命中。
- `app/build.gradle:214` 注释明示 `unitTests.all` 只覆盖单测变体、不触碰 androidTest；`:268` / `:346` 虽有 `androidTestImplementation` 依赖，但没有任何 Gradle 任务去编译它。
- 结论：androidTest 当前无任何 CI 编译覆盖；本单 9 个坏文件是"存量、无人发现的必红"，只有真机跑 instrumentation 或执行 `assembleDebugAndroidTest` 才会炸。

### 5.2 甲方案：CI 加 `assembleDebugAndroidTest` job

- 做法：新增一个 job 调 `:app:assembleDebugAndroidTest`，编译并打包测试 APK（不执行用例）。
- 代价 1（时长）：新增 Android SDK 环境与 androidTest 依赖解析、编译开销，CI 流水线变长；构建缓存可部分抵消。
- 代价 2（先清存量）：按 §三 实测，当前一上 job 即 9 文件红；必须先把存量失修清干净（可修迁移 / 待裁落锤 / 判废删除）再开 job，否则门禁上线即永久红。
- 代价 3（只编译不回归）：`assembleDebugAndroidTest` 不跑 instrumentation；要真机/模拟器回归还需 `connectedCheck` + emulator 矩阵，另计时长、 flaky 与设备农场成本。

### 5.3 乙方案：静态未解析 import 门禁 `data_scripts/check-androidtest-imports.py`

- 做法：扫 androidTest 全部 import；第三方前缀 / 通配 / `R` 跳过；本地符号与主仓声明索引（含扩展 receiver、owner 成员、typealias）比对；databinding 按 `res/layout` XML 存在性判定；未解析即 FAIL。纯 python3，不引入编译依赖。
- 代价 1（覆盖边界）：只能抓 import 级硬错误；抓不到资源 id/字符串缺失、反射成员失效、方法签名漂移、Compose 节点缺失。
- 代价 2（维护成本）：需维护第三方前缀白名单、生成类规则、声明索引口径；同包不 import 的引用天然覆盖不到。
- 本单只出方案：不建 job、不建脚本（脚本/job 另单选）。

## 六、反例推演（必写三条）

### 6.1 静态比对 import 会不会误报；如何避免

- 同名字符串 / 同名类：按 FQN 包名校验；符号存在但包不一致单列 pkg-mismatch，不直接判未解析。本单实测 pkg-mismatch 0 条。
- 类型别名：`typealias` 纳入声明索引，不漏。
- `import ... as 别名`：解析按原 FQN，别名只记录不改写判定。
- 同包无需 import：该引用不产生 import 语句，机检天然覆盖不到——列入盲区，不做强判。
- 通配 `.*`：无法静态展开，跳过并计数（本单 0 条）。
- 生成类：`R` 跳过（38 条）；databinding 不靠源码声明，按布局 XML 存在性判定（4 存在 / 4 缺失）。
- 第三方：`android.*` / `androidx.*` / `java.*` / `javax.*` / `kotlin.*` / `kotlinx.*` / `org.junit` / `org.hamcrest` / `okhttp3` / `okio` 等前缀跳过（1571 条）。
- 扩展声明：`fun Receiver.symbol(...)`、`val Receiver.symbol`、`inline fun <reified T> ...` 已纳入 receiver 与类型参数解析。首版索引曾漏报 `defaultSharedPreferences` / `dpToPx` / `postEvent` 等扩展符号，第二版修正后这些条目回到 resolved。
- companion 成员扩展：`Owner.Companion.member` 按 owner 所在文件匹配（如 `AnalyzeRule.Companion.setCoroutineContext`，`AnalyzeRule.kt:1032`）。
- 仍可能误报/漏报的形态：Java 静态成员、深层嵌套内部类、同文件多声明同名；同包成员引用；资源与反射路径（见 §七）。

### 6.2 现在就加 `assembleDebugAndroidTest`，会有多少文件直接红

- 实算：9 个文件（§四 A–I 全列）。
- 名单：`BackupOptionsTest.kt`、`ReadRecordHistoryTest.kt`、`RuleSelectionShareTest.kt`、`BookSourceCheckUiTest.kt`、`SourceDragOrderUiTest.kt`、`SourceOrderMetadataUpdateTest.kt`、`CodeSelectionUiTest.kt`、`CoverStylePreviewUiTest.kt`、`CoverTitleAdaptiveUiTest.kt`。
- 硬错误 import 合计 15 条（11 未解析符号 + 4 缺失 binding）。
- 口径限制：这是 import / 生成类级确定红；资源 id 缺失、反射成员失效、Compose 节点缺失是否在编译期炸，静态门禁无法确认，未计入此数。

### 6.3 判废删除 instrumentation 测试会不会断掉真机回归线

- 判断：会断。现有 JVM 单测（`app/src/test`）不触真机系统控件、位图渲染、WebView 生命周期、PDFBox 原生、手势/滚轮/旋转；androidTest 是当前唯一覆盖这些真机行为的自动化层。整批判废后，封面像素、后台 WebView 池、PDF 大纲/缩放、漫画方向、滚轮旋钮、浏览器全屏、底部 WebView 对话框等将失去自动化回归。
- 替代方案：
  1. 数据/逻辑用例下沉 JVM 单测（DAO、迁移、备份打包、规则解析、缓存状态机），不依赖设备。
  2. 存活 UI 用例迁移 Compose UI test（`createComposeRule`），在 emulator/设备跑；前置是先补 testTag 钩子。
  3. 视觉回归走现有截图基建（`.github/workflows/screenshot.yml` 的 `recordPaparazziDebug`）兜静态观感。
  4. 残留真机交互列人工冒烟清单，版本前手工过。
- 建议：不整批删；按可修 5 / 待裁 3 先迁移，判废 1 条待 Compose 预览出口确认后再落锤。

## 七、新发现问题（状态词：新增 / 确认 / 待裁 / 保留 / 判废）

1. **确认**：9 个 androidTest 文件共 15 条硬错误 import，instrumentation / `assembleDebugAndroidTest` 必红；CI 无 androidTest 编译覆盖，长期未暴露。清单见 §三。
2. **待裁**：两份 fixture 在测试代码中无按名引用——`issue1074-fixtures.md`、`pdf-outline-direct-named.pdf`；需确认是否历史遗留可清，本单不删。
3. **待裁**：androidTest 共 50 处 `getDeclaredMethod` / `getDeclaredField` 反射访问；Compose 化后目标方法/字段可能已不存在，import 静态门禁抓不到，需逐文件运行期复核。
4. **待裁**：新 Compose 屏（manage / replace / dict / edit / config sections）未发现 testTag；Compose UI 测试改写缺定位钩子。
5. **确认**：设置壳 Compose 化后，旧 Preference 树断言 API（`findPreference` / `scrollToPreference` / `Preference.parent` / `Preference.order` / `preferenceScreen`）在 Compose 设置分区无对应物；8 个 androidTest 文件仍在使用这组 API。其中 3 个文件的目标 Fragment 已删（`BackupOptionsTest` → `BackupConfigFragment`、`CoverStylePreviewUiTest` → `CoverFontConfigFragment`、`CoverTitleAdaptiveUiTest` → 两个封面 Fragment）；另 5 个指向存活的旧 PreferenceFragment 嵌套类：`HighlightTriggerUiTest` / `MouseWheelScrollTest` → `MoreConfigDialog.ReadPreferenceFragment`，`ReadAloudMenuUiTest` → `ReadAloudConfigDialog.ReadAloudPreferenceFragment`，`ReadAloudScaleUiTest` → `ReadAloudControlsDialog.ControlsPreferenceFragment`，`MyPageCustomizationTest` → `MyFragment.MyPreferenceFragment`。
6. **确认**：RSS 源管理仍是旧 RecyclerView 栈（`RssSourceActivity` / `RssSourceAdapter`），与书源 / 替换 / 字典 / 编辑的 Compose 栈并存，迁移不一致。
7. **待裁**：4 个 binding 对应布局存在、binding 可生成，但其宿主 Activity/条目可能已 Compose 化，binding 字段与视图运行期可能失效——`ExploreCategoriesTest`（`ActivityExploreShowBinding`）、`MangaReadingDirectionTest`（`ActivityMangaBinding`）、`FontSelectionInflationTest`（`ItemFontBinding`）、`HighlightGroupUiTest`（`ItemHighlightRuleBinding`）；需运行期复核。

## 八、未验证项与边界

- 未编译、未跑真机；结论基于静态 grep 与主仓声明索引，不代表运行期通过。
- 资源 `R.id` / `R.string` / `R.layout` 漂移未做全量机检；粗扫结果混入 AppCompat / 框架 id 噪声，未计入 §三 清单。
- `app/src/test`（JVM 单测，631 文件）不在本单清点范围。
- 工作区在本单开始前已有他人未提交改动（git status 可见 M/D）；本单未改任何源码、测试、脚本，未执行 git 写操作。
- 本单未在 `data_scripts/` 落地门禁脚本；乙方案脚本另单选。

## 守则回执

- 只读审计：未改 kt / 脚本 / 文档（除本两份交付文档），未跑 gradle / kotlinc / assemble。
- 取证可回源：§三 每条均带 `文件:行号` 与符号名；§五 每条带 workflow / action.yml 行号。
- 计数闭合：2356 = 1571 + 0 + 38 + 728 + 4 + 0 + 11 + 4。
