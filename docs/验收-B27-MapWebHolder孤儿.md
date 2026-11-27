> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/锚点/B27-MapWebHolder孤儿.md

# 验收报告 · B27 MapWebHolder 孤儿清剿

- 验收对象：docs/清理-B27-MapWebHolder孤儿.md（被验方自述，已逐字通读）
- 改动边界：commit `197d2e79c8beba7705ffa98d3cdf262bee27a7ad`（`清理B27:删孤儿MapWebHolder(60行)+同步摘DesignToken基线行`）
- 验收基线 HEAD：开工时 `1dc31aa`（文档:B27清剿报告入库+验收单Q-99登记）；验收过程中上游并发提交 `a4a6c4d` / `46e7036` / `a29c087` 入库，终态 HEAD `a29c087`。三笔提交均不触碰 `MapWebHolder.kt` 与 `DesignTokenGuardTest.kt`（见第九节），终态复跑 grep 仍零命中。
- 验收方式：只读静态核对（git show / Read / Grep / 静态正则模拟）。未改源码、测试、门禁与既有报告；未执行 git add/commit/push；未本地编译、未跑单测（硬约束）。
- 仓库绝对路径：`/home/user/Doubao/chats/38454122521460994/legado`
- 验收时间：2026-11-26（Asia/Shanghai）

## 一、结论

**PASS（静态验收）**

1. 代码域（主源码 / 测试 / 资源 / Manifest / assets / 前端 web 模块 / 反射与类名字符串通道）对 `MapWebHolder` 全部零命中，删除后无代码残留。
2. 四条隐式持有通道（反射、类名字符串、Application 级持有、WebView 池）逐一回证为空；`WebViewPool` 经读码确认为独立栈池，不耦合被删 Holder。
3. 基线同步正确：`legacyBaseline` 38 → 37，双向差集（unexpected / stale）静态模拟均为空；测试文件括号与大括号平衡。
4. commit `197d2e7` 仅两文件、61 行纯删除，无新增、无其他类改动。
5. 被删文件只持有自有 private 状态，未导出任何被他人复用的公共函数 / 常量。
6. docs 历史留档未被清理，属正常台账；本 commit 对 docs 零改动。
7. B24 → B27 链条一致，本单只处理 MapWebHolder 这一个孤儿；另登记 2 个空目录残留与 2 组疑似未接线符号，交调度层另立条目，不计为 B27 缺陷。

> 未验证声明：本结论为静态走查结论，未经云端 CI 复跑与真机运行验证。基线测试红绿以 GitHub Actions 单测结果为唯一验收手段；运行时反射 / JS 动态加载与真机 View 树残留未做运行期枚举。

## 二、判据 1：零引用回证独立复跑（四类 grep，逐类命中数与原始输出）

验收时点为删除后仓库状态（开工 HEAD `1dc31aa`，终态复跑 HEAD `a29c087`），下列命令均由验收方在仓库根目录真跑；终态 HEAD 复跑结果与开工时一致，全部零命中。

### 1.1 主源码 —— 0 命中

命令：

```
grep -rn "MapWebHolder" app/src/main modules/book/src/main modules/rhino/src/main screenshot/src/main
```

原始输出：空；`exit=1`。

分模块计数（逐目录真跑 `grep -rn ... | wc -l`）：

1. `app/src/main`：0
2. `modules/book/src/main`：0
3. `modules/rhino/src/main`：0
4. `screenshot/src/main`：0
5. `modules/web`：该模块为 Vue/TS 前端工程（`modules/web/src` 下为 `App.vue` / `main.ts` / `pages` / `store` 等，无 Kotlin 源）；补跑 `grep -rn "MapWebHolder" modules/web/src`，空，`exit=1`，0 命中。

### 1.2 测试域 —— 0 命中

命令：

```
grep -rn "MapWebHolder" app/src/test
grep -rn "MapWebHolder" screenshot/src/test
```

原始输出：均空；`exit=1`。仓内测试目录仅 `app/src/test` 与 `screenshot/src/test` 两处（`find . -path "*/src/test" -type d -not -path "*/build/*"` 实证）；modules 下无 `src/test` 目录。

### 1.3 资源 —— 0 命中

命令：

```
grep -rn "MapWebHolder" app/src/main/res
```

原始输出：空；`exit=1`。`modules/book/src/main/res`、`modules/rhino/src/main/res` 目录不存在（grep 报 `No such file or directory`，非命中）。

### 1.4 Manifest —— 0 命中

命令：

```
find . -name "AndroidManifest.xml" -not -path "*/build/*" -exec grep -n "MapWebHolder" {} \;
```

涉及清单 6 份：`app/src/cronetRuntime/AndroidManifest.xml`、`app/src/main/AndroidManifest.xml`、`app/src/pdfRuntime/AndroidManifest.xml`、`modules/book/src/main/AndroidManifest.xml`、`modules/rhino/src/main/AndroidManifest.xml`、`screenshot/src/main/AndroidManifest.xml`。原始输出：空，0 命中。

旁证：`app/src/main/AndroidManifest.xml` 注册的 main 包组件为 `MainActivity`（:136）、`PersonDetailActivity`（:144）、`RelationActivity`（:151）、`PlaceMapActivity`（:158）、`BattleActivity`（:168）、`PaintActivity`（:174）、`search.InkSearchActivity`（:297），无任何 Holder 类。

### 1.5 补充 sweep（assets / 全类型 / import）

1. `app/src/main/assets`（js / json / md / txt）：`grep -rn "MapWebHolder" app/src/main/assets`，空，`exit=1`。
2. import 语句：`grep -rn "import io.legado.app.ui.main.MapWebHolder" app/src modules/*/src`，空，`exit=1`。
3. 全仓非构建 sweep：`grep -rn "MapWebHolder" . | grep -v "/build/" | grep -v "^\./docs/"` 仅命中 `.git/index` 二进制、`.git/logs/HEAD`、`.git/logs/refs/heads/main`（commit 消息历史）与 `.gradle` 执行历史二进制，均为 VCS / 构建缓存元数据，非可达代码引用。

**判据 1：PASS。** 代码域四类外加 assets、import、前端模块全部零命中。

## 三、判据 2：隐式持有真排除（四条通道逐一回证，读码而非只看自述）

### 2.1 反射 / 动态类加载

命令：

```
grep -rn "Class.forName\|loadClass\|getDeclaredField\|getDeclaredConstructor" app/src/main modules/*/src/main | grep -i "holder\|web"
```

原始输出：空；`exit=1`。不存在按名字符串加载该 Holder 的反射通道；仓内其余反射命中均为 `::class.java`（Kotlin 映射类型 / Glide 位图 / Intent 目标 Activity），与本类无关。

### 2.2 类名字符串 / JS 桥回调

1. 源码域全类型 sweep（排除 build 与 docs）：0 命中（见 1.5）。
2. assets JS 脚本：0 命中（见 1.5）。被删文件内部亦未注册任何 `addJavascriptInterface`，不存在 JS 侧按名回调通道。

### 2.3 Application 级持有

Application 子类：`app/src/main/java/io/legado/app/App.kt`。命令：

```
grep -n "MapWebHolder\|WebView\|attach\|detach\|destroy" app/src/main/java/io/legado/app/App.kt
```

原始输出仅：

- `App.kt:160: override fun attachBaseContext(base: Context) {`
- `App.kt:161:     super.attachBaseContext(AppContextWrapper.wrap(base))`

为框架生命周期方法，与本 Holder 无关；无 Application 级单例持有、无生命周期回调注册、无全局容器挂载。

### 2.4 WebView 池 / 缓存复用 —— 读码确认独立

文件：`app/src/main/java/io/legado/app/help/webView/WebViewPool.kt`，验收方通读全 205 行：

1. `WebViewPool.kt:26`：`object WebViewPool`，与被删的 `object MapWebHolder` 是两个独立单例。
2. `WebViewPool.kt:30`：`private val idlePool = Stack<PooledWebView>()`，闲置栈；`:32`：`private val inUsePool = mutableMapOf<String, PooledWebView>()`，在役集合；池元素类型为 `PooledWebView`。
3. `WebViewPool.kt:137`：`createNewWebView()` 内构造 `VisibleWebView(MutableContextWrapper(appCtx))`，池内包装的是 `VisibleWebView`，不持有、不引用 `MapWebHolder`。
4. `grep -n "main\|Holder\|attach\|detach" WebViewPool.kt`：空。
5. 仓内其余 WebView 使用方（BackstageWebView / BookInfoActivity / WebViewActivity / WebViewLoginFragment / ReadRssActivity / VideoPlayerActivity / BottomWebViewDialog）均走 `WebViewPool.acquire / release`；1.1 全主源码 sweep 已证明这些文件无一处引用 `MapWebHolder`。

**判据 2：PASS。** 反射、字符串、Application 级持有、WebView 池四条隐式通道均零命中；`WebViewPool` 为独立 `PooledWebView` 栈池，与被删 Holder 无耦合。按锚点卡边界，删除处置正确，无需转「只登记不删」。

## 四、判据 3：基线同步正确（断言口径、双向差集、语法平衡）

测试文件：`app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt`（验收方通读全 186 行）。

### 4.1 断言口径（`:70-86`，`ui layer has no inline hex color outside the legacy baseline`）

1. `:72` `hexPattern`：`Regex("(?<![0-9A-Fa-f])-?0x[0-9A-Fa-f]{8}(?![0-9A-Fa-fLu])")`，匹配 8 位 ARGB 字面量，后缀 `L/uL` 位掩码不算色值。
2. `:73-79` `actualHexFiles`：`uiSourceRoot` 树下所有命中该模式的 `.kt` 文件，映射为 `UI_PREFIX` 前缀的仓库相对路径集合。
3. `:82-83` 第一条差集：`unexpected = actualHexFiles - tokenSourceFiles - legacyBaseline`，必须为空——基线外出现裸色值即 FAIL。
4. `:84-85` 第二条差集：`stale = legacyBaseline - actualHexFiles`，必须为空——基线条目失效却未移出基线也 FAIL。即「命中集 == 基线集」双向对账。

### 4.2 基线计数与 diff

1. 当前工作树基线条目数：37（对 `:144-184` 的 `legacyBaseline = setOf(...)` 块内 `.kt",` 字符串计数）。
2. 父提交 `197d2e7^` 基线条目数：38。
3. `git show 197d2e7 -- DesignTokenGuardTest.kt` 的 hunk `@@ -156,7 +156,6 @@` 仅删除一行：

```
-            "io/legado/app/ui/main/MapWebHolder.kt",
```

   上行为 `TopicMarxCard.kt`，下行为 `PersonDetailActivity.kt`；无其他基线条目增删，断言代码、`tokenSourceFiles`、`UI_PREFIX`、`palettePath` 均未改动，不存在改断言迁就删除。

### 4.3 摘行后两侧仍为空的静态核对（未跑测试，纯正则模拟）

验收方按测试同款正则在 `app/src/main/java/io/legado/app/ui` 树下静态扫描：

1. `legacyBaseline` 规模 37；`actualHexFiles` 规模 38；`tokenSourceFiles` 为 `ComponentPalette.kt` 单源 1 份。
2. `unexpected = actualHexFiles - tokenSourceFiles - legacyBaseline`：`[]`（空）。
3. `stale = legacyBaseline - actualHexFiles`：`[]`（空）。
4. 37 条基线条目对应文件在磁盘上全部存在，无悬空登记。

被删文件删前第 28 行 `it.setBackgroundColor(0xFFF7F1E6.toInt())`：`FFF7F1E6` 为八位十六进制，其后为 `.toInt`，不被 `(?![0-9A-Fa-fLu])` 排除，确属 `hexPattern` 命中项，原必须登记基线；源码删除后该路径自动离开 `actualHexFiles`，若基线保留此行则 `stale` 非空会 FAIL，同步摘除方向正确。

### 4.4 语法平衡核对

对当前测试文件做字符计数：`{` 21 个 / `}` 21 个；`(` 79 个 / `)` 79 个，大括号与小括号各自平衡。文件尾部 `:185` `)` 收束 `setOf(...)`，`:186` `}` 收束类体，结构闭合。

**判据 3：PASS。** 摘行后双向差集静态为空；断言逻辑未被改动；括号平衡。单测红绿仍以云端 CI 复跑为准。

## 五、判据 4：删除范围收敛（commit 仅两文件 61 行删除）

命令：`git show --stat 197d2e7`，原始统计：

```
 .../java/io/legado/app/ui/main/MapWebHolder.kt     | 60 ----------------------
 .../app/ui/components/DesignTokenGuardTest.kt      |  1 -
 2 files changed, 61 deletions(-)
```

1. 文件一：`app/src/main/java/io/legado/app/ui/main/MapWebHolder.kt`，60 行纯删除，0 新增。
2. 文件二：`app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt`，1 行纯删除，0 新增。
3. 合计 2 文件、61 行删除、0 行新增；无其他类、无其他资源、无 Manifest、无 docs 改动。
4. `git show --name-only --format= 197d2e7` 输出仅上述两路径，范围收敛。

**判据 4：PASS。**

## 六、判据 5：未误删共享物（被删文件公共面逐条核）

被删文件内容取自 `git show 197d2e7^:app/src/main/java/io/legado/app/ui/main/MapWebHolder.kt`，共 60 行、1839 字节，sha256 `fbf6bc1b9297cd8da765e24207330644ec169150da0521a8311dc5638373a550`，与清理报告第六节登记值一致。

逐条公共面核对：

1. `:13` `object MapWebHolder`：单例声明；1.1 全主源码 sweep 零命中，域外无任何引用。
2. `:15` `private var web: WebView?`：私有字段，外部不可见。
3. `:16` `private var lastUrl: String = ""`：私有字段，外部不可见。
4. `:19` `fun attach(container: ViewGroup, url: String)`：public 方法，仅操作自有 `web` / `lastUrl`；无静态常量产出，无对外回调注册。
5. `:48` `fun detach()`：public 方法，仅从父容器移除自有 `web`。
6. `:52` `fun destroy()`：public 方法，调 `detach()` 后销毁自有 `web` 并复位 `lastUrl`。
7. 文件内无顶层 `const val` / `val` 共享常量、无 companion object、无 top-level 函数、无自定义类型导出；import 仅 `SuppressLint` / `ViewGroup` / `WebView` 三个 Android 平台类型。

三个 public 方法的可达性完全依赖持有 `MapWebHolder` 单例引用；类名全仓零命中（判据 1）即证明不存在域外调用方，删除未切断任何在役调用链。

**判据 5：PASS。** 被删内容只含自有内部状态与三个生命周期方法，没有别人仍在用的公共函数或常量。

## 七、判据 6：留档与代码分离（docs 零改动）

1. `git show --name-only --format= 197d2e7`：docs 路径 0 条，本清剿 commit 未触碰任何文档。
2. 当前 HEAD 下 docs 仍有 12 份历史台账 / 修复 / 验收 / 派单文档含 `MapWebHolder` 字样，均为历史记录而非代码引用，未被清理，符合「留档与代码分离」预期：
   - `docs/修复-B1-点字半屏Sheet.md`
   - `docs/修复-B26-常量visible接线.md`
   - `docs/修复-DetailSheet贴皮.md`
   - `docs/接手/00-入口.md`
   - `docs/派单台账.md`
   - `docs/派单队列.md`
   - `docs/清理-B24-旧DetailSheet清剿.md`
   - `docs/清理-B27-MapWebHolder孤儿.md`
   - `docs/锚点/B27-MapWebHolder孤儿.md`
   - `docs/问题清单.md`
   - `docs/问题清单-附录-逐文件.md`
   - `docs/验收-B26-常量visible接线.md`
3. 后续文档入库提交 `1dc31aa` 为独立 commit（4 份 docs，212 新增 / 3 删除），与代码清剿 commit 分离，未混改源码。

**判据 6：PASS。**

## 八、判据 7：与 B24 链条一致，及新发现观察项

### 8.1 链条核对

1. `docs/清理-B24-旧DetailSheet清剿.md:171`（§6.3 第 3 条）登记：`MapWebHolder.kt` 在 B24 删除旧 DetailSheet 三文件后全仓生产源码已无调用点，原唯一调用点为 `DetailSheet.kt:132` 的 `MapWebHolder.detach()`；并提示后续清剿时须同步摘除 `DesignTokenGuardTest.kt:159` 基线条目。 已废（现行人名门面 app/src/main/java/io/legado/app/help/bubble/PersonProfileWiring.kt 与 app/src/main/java/io/legado/app/ui/main/PersonDetailActivity.kt 为准（旧 DetailSheet 三文件已于 2a6d687 清剿））
2. B24 清剿 commit `2a6d687`（`清理B24:删旧DetailSheet三文件(701行,四类grep零命中)`）；现磁盘 `find app/src -iname "*DetailSheet*" -o -iname "*MapWeb*"` 无结果，旧 Sheet 与本 Holder 均无残留文件。
3. 本单只处理锚点卡点名的 `MapWebHolder` 一个孤儿，未顺带清剿其他符号，与 B24「观察项只登记不删、交调度层另立条目」的处置衔接一致。

### 8.2 新发现观察项（非 B27 缺陷，登记交调度层另单判定）

1. 空目录残留一：`app/src/main/java/io/legado/app/ui/main/detail/`。目录内无文件，git 不跟踪空目录（`git ls-files` 该路径为空）；其历史来自 B24 删除 DetailSheet 三文件（`git log -- app/src/main/java/io/legado/app/ui/main/detail/` 首条即 `2a6d687`）。不影响编译与 CI 出包，属本地文件系统残留，可在后续清理单中顺手移除目录。
2. 空目录残留二：`app/src/main/java/io/legado/app/ui/file/utils/`。目录内无文件，无 git 跟踪记录，残留时间早于本批 B 系清剿，成因待查。
3. 疑似未接线符号一：`app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfViewModel.kt:45` `class BookshelfViewModel(application: Application) : BaseViewModel(application)`。全仓除自身定义外 `grep -rn "BookshelfViewModel" app/src --include=*.kt` 零引用；同目录 `InkShelfFragment.kt` 直接经 `appDb` + `flowWithLifecycleAndDatabaseChangeFirst` 订阅数据（`:15`、`:17`、`:21` import），未通过 `viewModels()` 绑定该 VM。疑似旧架构遗留 ViewModel，是否孤儿需另单结合历史接线记录判定，本单不扩大删除范围。
4. 疑似未接线符号二：`app/src/main/java/io/legado/app/ui/main/chrono/ChronoBackdropSource.kt:14` `interface MapBackdropSource`、`:21` `object PlaceholderBackdropSource : MapBackdropSource`、`:29` `var current: MapBackdropSource = PlaceholderBackdropSource`。除本文件内自洽实现与类型声明外，域外 `grep -rn "PlaceholderBackdropSource\|backdropSource\|BackdropSource"` 无消费者；可能是前瞻预留接缝，也可能是未接线死代码，交调度层另单核实。

**判据 7：PASS（B27 本体只处理这一个孤儿）；上述 4 项为独立观察项，不计入本单通过 / 不通过判定。**

## 九、工作树并发改动旁证（不在本 commit 范围）

验收开工时 `git status --short` 曾见 reader/note 域并发改动：`HighlightNoteDialog.kt`（M）、`NoteDialogExitGate.kt`（??）、`NoteDialogExitGateTest.kt`（??）、`docs/修复-B26-常量visible接线.md`（??）。验收过程中这些改动由上游另行提交：

1. `a4a6c4d`（修B26:笔记浮层改两段式关闭）：`HighlightNoteDialog.kt`、`NoteDialogExitGate.kt`、`NoteDialogExitGateTest.kt` 三文件，210 新增 / 2 删除，属 reader 域 B26 工单。
2. `46e7036`（文档:B26修复报告+B27验收入库+B29域外清单立条）：4 份 docs。
3. `a29c087`（文档:B26验收入库+B30边界立条）：5 份 docs 与 `锚点/B29-域外常量visible-config.md`。

三笔提交的 `git show --stat` 均不含 `MapWebHolder.kt`、`DesignTokenGuardTest.kt` 或任何本单判据涉及文件；验收方未读取、未改动上述 reader 域文件，其红绿归属 B26 在飞工单。终态 `git status --porcelain` 仅剩本验收报告一份 untracked 文件。

## 十、未验证项

1. 本地编译 / 单测：按硬约束未执行 `./gradlew` / `kotlinc` / 任何测试运行。第四节双向差集为静态正则模拟结论，`DesignTokenGuardTest` 是否在 CI 全绿，须以云端 Actions `:app:testAppDebugUnitTest` 复跑为准。
2. 反射 / 动态脚本兜底：`Class.forName`、字符串类名已静态 grep 零命中，未在运行时做类加载枚举；若后续存在 Rhino / JS 源按类名反射加载，需 CI 出包后另查。
3. 真机 View 树残留：静态层面已无可达引用；历史进程内若残留旧 WebView 实例，随进程销毁自然释放，真机行为复核归属后续验收单。
4. 8.2 两组疑似未接线符号仅做了文本引用扫描，未排除 Compose 约定式引用、反射实例化或跨模块字符串调用，定性需另单深查。
5. docs 台账中 B27 行状态翻转与附录逐文件清单更新属调度层文档工序，不在本静态清剿验收范围内。

## 十一、验收动作清单（可回源）

1. `git log --oneline -5`、`git status --short`：确认 HEAD 与工作树状态。
2. `git show --stat 197d2e7`、`git show --name-only --format= 197d2e7`：删除范围与文件清单。
3. 四类 grep + assets + import + web 模块 sweep：判据 1 证据。
4. 反射 grep、`App.kt` 持有关键行、通读 `WebViewPool.kt` 全 205 行：判据 2 证据。
5. 通读 `DesignTokenGuardTest.kt` 全 186 行；基线 38 / 37 计数；静态正则模拟双向差集；括号计数：判据 3 证据。
6. `git show 197d2e7^:.../MapWebHolder.kt` 通读 60 行；sha256 与字节数核对：判据 5 证据。
7. `git show --name-only 197d2e7` + docs 12 份留档名单：判据 6 证据。
8. B24 报告 `:171` 链条引用；空目录与疑似未接线符号 grep：判据 7 证据。
