# 锚点卡 · Q-236 旧 Activity 双实现清点

> 对象：`app/src/main` 全部 Activity；审计报告 `docs/审计-旧Activity双实现清点.md`。本卡对应派单 Q-236，只读核查，未改任何代码、脚本与既有文档；证据不足处标「待运行期确认」，不猜。

> **口径已失效（2026-11-26，Q-280）**：旧关于页 `AboutActivity` 已整体删除，详见 `docs/修复-删除关于页.md`；本段保留为历史记录，勿据以施工。

## 一、事实锚点（逐字，含 文件:行）

- 清单口径：`git ls-files 'app/src/main/**/*Activity*.kt'` 得 70 文件；类声明 64 个，剔 2 个契约类后 Activity 62 个（含 1 抽象基类），具体屏幕 61 个；另 3 个独立抽象基类。
- 孤儿旧 Fragment：`MainActivity.kt:527` 装载 `InkShelfFragment`、:528 `ExploreInkFragment`、:529 `MyInkFragment`；`MyFragment`/`ExploreFragment`/`RssFragment` 仓内无实例化引用。
- 关于页双实现：旧 `ui/about/AboutActivity.kt:61`（AppCompatActivity）仅由 `ui/main/my/MyFragment.kt:273` 拉起；新 `ui/about/AboutInkActivity.kt:50` 由 `ui/main/my/MyInkFragment.kt:106` `open(AboutInkActivity::class.java)` 拉起。
- 阅读页双实现：旧 `ui/book/read/ReadBookActivity.kt:214`（继承 `BaseReadBookActivity`）无 `::class` 拉起；新 `ui/book/read/ink/InkReadActivity.kt:57`（ComponentActivity）由 `utils/ContextExtensions.kt:74`、`utils/FragmentExtensions.kt:102`、`ui/book/info/BookInfoActivity.kt:1516` 拉起；`receiver/MediaButtonReceiver.kt:105` 为 `isExistActivity` 存在性判定，非入口。
- 阅读路由分流：`utils/ContextExtensions.kt:71-74` 视频→`VideoPlayerActivity`、音频→`AudioPlayActivity`、漫画→`ReadMangaActivity`，其余 else→`InkReadActivity`。
- 新栈独有：`ui/book/read/ink/InkTimelineActivity.kt:51` 由 `ui/book/read/ink/InkActionDispatcher.kt:95` 拉起，无旧对应物。
- 强转耦合：`ReadBookActivity` 被 `config/AutoReadDialog.kt:49`、`BgTextConfigDialog.kt:136`、`ClickActionConfigDialog.kt:55`、`MoreConfigDialog.kt:57`、`PaddingConfigDialog.kt:81`、`ReadAloudControlsDialog.kt:31`、`ReadAloudDialog.kt:51`、`ReadStyleDialog.kt:40`、`ReaderMenuConfigDialog.kt:210`、`page/PageView.kt:61`、`widget/dialog/BookMemoDialog.kt:47` 等以宿主类型强转。

## 二、分类计数表

| 分组 | 数量 | 处置口径 |
|---|---|---|
| 框架抽象基类 | 4 | 不可下线 |
| 新 Ink 栈存活 | 5 | 存活不动 |
| 旧栈/混合壳存活 | 35 | 存活不动 |
| 入口孤儿/证据不足（待运行期确认） | 20 | B 档，不建议本轮删 |
| 双实现旧壳（含强转耦合） | 2 | A 档候选 1 个；ReadBookActivity 先解耦 |

> 注：双实现旧壳 2 个（`AboutActivity`、`ReadBookActivity`）分别是「待确认 20」与「阅读旧壳 1」的子集，不另计；合计抽象基类 4 + 具体屏幕 61 = 65 条目，其中 62 个落在 `*Activity*.kt` 清单内，3 个抽象基类在独立文件。

## 三、结论锚点

- C1 当前 runtime 关于页走 `AboutInkActivity`，阅读页走 `InkReadActivity`；两个旧壳均无新栈入口。
- C2 A 档可核后下线候选：`book/manage/BookshelfManageActivity.kt:70`、`book/import/remote/RemoteBookActivity.kt:36`、`about/AboutActivity.kt:61`；manifest 注册分别见 :344、:378、:297。
- C3 `ReadBookActivity` 虽无入口，但 20+ 对话框宿主强转未解耦，不具备下线条件，列 3.5 单列。
- C4 RSS 子树与 `AutoTask*` 链根入口挂孤儿 `RssFragment`/`MyFragment`，属 B 档；JS 桥、通知 PendingIntent、外部 intent-filter 静态无法穷举，须运行期点验。
- C5 计数勘误：派单「47 个旧 Activity」与实测 62 个 Activity 类（具体屏幕 61）不符，旧栈/混合壳存活 35、待确认 20；原始计数来源待总控核对。

## 四、回源

- 审计报告：`docs/审计-旧Activity双实现清点.md`（全量 62 类清单、双实现证据链、下线分级建议、新发现问题）。
- 源码根：`app/src/main/java/`（Activity 声明行与拉起方行号均可回源核对）。
