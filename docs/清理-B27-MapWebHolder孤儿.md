# 清理报告 · B27 MapWebHolder 孤儿清剿

- 工单：问题清单 B27（`docs/问题清单.md` 第 71 行）
- 锚点卡：`docs/锚点/B27-MapWebHolder孤儿.md`（五条判据与边界以它为准）
- 上游证据：`docs/清理-B24-旧DetailSheet清剿.md` §6.3 第 3 条（第 171 行，零调用事实与基线登记提示）、第 4 条（第 181 行，隐式通道待排查边界）
- 执行环境：云主机 vm，仓库绝对路径 `/home/user/Doubao/chats/38454122521460994/legado`
- 执行时间：2026-11-26（Asia/Shanghai）
- 工作树基线：删除前 HEAD `881f0b3`；删除仅落工作区，未执行 `git add` / `commit` / `push`（硬约束）。

## 一、E1 命令证据（删除前四类 grep，原样输出与计数）

仓库根目录：`/home/user/Doubao/chats/38454122521460994/legado`。grep 退出码：`0` = 有命中，`1` = 零命中，`2` = 对应路径不存在（已注明实际存在的目录）。

### E1.1 主源码

命令（逐模块计数）：

```
for d in app/src/main modules/book/src/main modules/rhino/src/main modules/web/src/main screenshot/src/main; do
  [ -d "$d" ] && c=$(grep -rn "MapWebHolder" "$d" | wc -l) && echo "$d : $c"
done
```

命中计数：

- `app/src/main`：1 命中 —— `app/src/main/java/io/legado/app/ui/main/MapWebHolder.kt:13:object MapWebHolder {`（文件自身声明）
- `modules/book/src/main`：0
- `modules/rhino/src/main`：0
- `modules/web/src/main`：0（该模块无 Kotlin 源文件）
- `screenshot/src/main`：0

域外回证（排除自身声明文件后再 grep 主源码）：

```
grep -rn "MapWebHolder" app/src/main modules/*/src/main | grep -v "ui/main/MapWebHolder.kt:"
```

原样输出：空；`exit=1`（零外部调用点，与 B24 §6.3 第 3 条「唯一调用点 DetailSheet.kt:132 已随 B24 删除」一致；`DetailSheet.kt` 现存磁盘已不存在，复核 `find app/src -name "DetailSheet*"` 无结果）。

### E1.2 测试域

命令：

```
grep -rn "MapWebHolder" app/src/test modules/*/src/test
```

命中计数：`app/src/test` 1 命中 —— `app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt:159: "io/legado/app/ui/main/MapWebHolder.kt",`（legacyBaseline 基线条目，本单第五节同步摘除）；modules 测试目录无额外命中。

### E1.3 资源

命令：

```
for d in app/src/main/res modules/*/src/main/res; do [ -d "$d" ] && grep -rn "MapWebHolder" "$d"; done
```

原样输出：空；`exit=1`（0 命中；modules 下无 `src/main/res` 目录）。

### E1.4 Manifest

命令：

```
find . -name "AndroidManifest.xml" -not -path "*/build/*" -exec grep -n "MapWebHolder" {} \;
```

涉及清单 6 份：`app/src/cronetRuntime/AndroidManifest.xml`、`app/src/main/AndroidManifest.xml`、`app/src/pdfRuntime/AndroidManifest.xml`、`modules/book/src/main/AndroidManifest.xml`、`modules/rhino/src/main/AndroidManifest.xml`、`screenshot/src/main/AndroidManifest.xml`。原样输出：空；0 命中。Manifest 已注册组件回证：`app/src/main/AndroidManifest.xml` 第 136/144/151/158/168/174/297 行注册的 main 包组件为 MainActivity / PersonDetailActivity / RelationActivity / PlaceMapActivity / BattleActivity / PaintActivity / InkSearchActivity，无任何 Holder 类。

## 二、E2 隐式持有排除（文件:行）

按锚点卡判据 2 与边界段，逐条排查反射、类名字符串、Application 级持有、WebView 池/缓存复用四类隐式通道。

### E2.1 反射 / 动态类加载

命令：

```
grep -rn "Class.forName\|loadClass\|getDeclaredField\|getDeclaredConstructor" app/src/main modules/*/src/main | grep -i "holder\|web"
grep -rn "forName\|loadClass" app/src/main modules/*/src/main | grep -i "holder\|web"
```

原样输出：空；`exit=1`。全仓反射命中均为 `Map::class.java`（Kotlin 映射类型）、Glide `Bitmap::class.java`、Intent 目标 Activity 等无关项，无任何按名字符串加载 `MapWebHolder` 的通道。

### E2.2 类名字符串（全类型文件 sweep）

命令（排除构建目录与 docs 历史留档）：

```
grep -rn "MapWebHolder" . | grep -v "/build/" | grep -v "^\./docs/"
```

原样输出仅两行：

- `./app/src/main/java/io/legado/app/ui/main/MapWebHolder.kt:13:object MapWebHolder {`
- `./app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt:159: "io/legado/app/ui/main/MapWebHolder.kt",`

`app/src/main/assets`（js / md / txt / json）0 命中；不存在 JS 桥按名字符串回调该 Holder 的通道。import 语句复核：

```
grep -rn "import io.legado.app.ui.main.MapWebHolder" . | grep -v "/build/"
```

原样输出：空；`exit=1`。

### E2.3 Application 级持有

Application 子类：`app/src/main/java/io/legado/app/App.kt`。复核命令：

```
grep -n "MapWebHolder\|WebView\|attach\|detach\|destroy" app/src/main/java/io/legado/app/App.kt
```

原样输出仅第 160–161 行框架方法 `attachBaseContext(base: Context) { super.attachBaseContext(...) }`，与本 Holder 无关；无 Application 级单例持有、无生命周期回调注册。

### E2.4 WebView 池 / 缓存复用

仓内独立 WebView 池：`app/src/main/java/io/legado/app/help/webView/WebViewPool.kt:26:object WebViewPool`，内部为 `Stack<PooledWebView>` 闲置栈 + `mutableMapOf<String, PooledWebView>` 在役集合（`WebViewPool.kt` 第 30–33 行），与被删 Holder 不是同一抽象。复核命令：

```
grep -n "main\|Holder\|attach\|detach" app/src/main/java/io/legado/app/help/webView/WebViewPool.kt
```

原样输出：空。其余 WebView 使用方（`help/http/BackstageWebView.kt:154,185`、`ui/book/info/BookInfoActivity.kt:704,1611`、`ui/browser/WebViewActivity.kt:111,373`、`ui/login/WebViewLoginFragment.kt:83,174`、`ui/rss/read/ReadRssActivity.kt:177,526`、`ui/video/VideoPlayerActivity.kt:325,823`、`ui/widget/dialog/BottomWebViewDialog.kt:803,1013`）全部走 `WebViewPool.acquire / release`，无一处引用 `MapWebHolder`。

**E2 结论：反射、字符串、Application 级持有、WebView 池四条隐式通道均零命中，不存在隐式持有；按锚点卡边界，本单转「删除」而非「只登记不删」。**

## 三、E3 删后复跑结论（同一组 grep，零命中）

删除后复跑命令与 E1/E2 同组：

- E3.1 主源码：`grep -rn "MapWebHolder" app/src/main modules/*/src/main` → 空，`exit=1`。
- E3.2 测试：`grep -rn "MapWebHolder" app/src/test` → 空，`exit=1`。
- E3.3 资源：`grep -rn "MapWebHolder" app/src/main/res` → 空，`exit=1`。
- E3.4 Manifest：6 份清单 `grep -n "MapWebHolder"` → 0 命中。
- E3.5 全仓非构建目录全类型：`grep -rn "MapWebHolder" . | grep -v "/build/" | grep -v "^\./docs/"` → 空，`exit=1`。

**E3 结论标记：PASS —— 代码域（主源码 / 测试 / 资源 / Manifest / assets / 反射与字符串通道）零残留。**

## 四、五条判据逐条自检

1. **先回证零引用：主源码、测试、资源、Manifest 四类 grep，逐类给命中数。** PASS。计数见 E1.1（主源码 1，且为自身声明；外部调用点 0）、E1.2（测试 1，基线登记）、E1.3（资源 0）、E1.4（Manifest 0）。
2. **排除隐式持有：反射 / 类名字符串 / 全局容器 / WebView 池；有则转「只登记不删」。** PASS。E2.1–E2.4 四条通道零命中；`WebViewPool` 为独立栈池，不持有本 Holder；无需转登记保留。
3. **删除时同步摘除 `DesignTokenGuardTest.kt:159` 基线行，并说明摘行后测试仍可通过。** PASS。摘除动作与断言口径见第五节；基线集合内条目 38 → 37，恰少本单这一条（计数口径：仅 `legacyBaseline = setOf(...)` 块内字符串，不含 `UI_PREFIX` / `palettePath` 等常量）。
4. **只删该文件与其基线行，不顺手改别的类。** PASS。`git diff --stat`：`MapWebHolder.kt` 60 行删除；`DesignTokenGuardTest.kt` 1 行删除；无其他类、无其他基线条目、无断言逻辑改动。
5. **删后复跑同一组 grep 零命中。** PASS。E3.1–E3.5 全部零命中。

## 五、基线行摘除说明

测试文件：`/home/user/Doubao/chats/38454122521460994/legado/app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt`

摘除的原第 159 行（基线集合字符串条目，整行删除）：

```
"io/legado/app/ui/main/MapWebHolder.kt",
```

### 5.1 断言口径（读测试 `ui layer has no inline hex color outside the legacy baseline`，第 70–86 行）

- `hexPattern`（第 72 行）：匹配 8 位 ARGB 字面量（`0x` + 8 位十六进制，后缀 `L/uL` 位掩码不算色值）。
- `actualHexFiles`（第 73–79 行）：`uiSourceRoot` 树下所有含该模式的 `.kt` 文件，映射为仓库相对路径集合。
- 第一条差集（第 82–83 行）：`unexpected = actualHexFiles - tokenSourceFiles - legacyBaseline`，必须为空——基线外出现裸色值即 FAIL。
- 第二条差集（第 84–85 行）：`stale = legacyBaseline - actualHexFiles`，必须为空——基线条目失效（文件被清理或改干净）却未移出基线也 FAIL。这是「命中集 == 基线集」的双向对账。

### 5.2 摘行后仍可通过的理由

- 被删文件第 28 行 `it.setBackgroundColor(0xFFF7F1E6.toInt())` 命中 `hexPattern`（`FFF7F1E6` 八位；其后为 `.toInt`，不被 `(?![0-9A-Fa-fLu])` 排除），因此原本必须登记在基线内。
- 源码文件删除后，该路径自动离开 `actualHexFiles`；若基线保留第 159 行，`stale` 将非空，第二条差集断言 FAIL。
- 同步摘除该基线条目后：`actualHexFiles` 与 `legacyBaseline` 两侧均不含此路径，两个差集仍为空，断言通过。
- 本次仅摘除基线集合中的一个字符串条目；断言代码、`tokenSourceFiles`、其余 37 条基线条目均未改动；不存在「改断言迁就删除」。

## 六、删除前后文件清单（绝对路径）

### 删除前

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/main/MapWebHolder.kt` —— 1839 字节，60 行，sha256 `fbf6bc1b9297cd8da765e24207330644ec169150da0521a8311dc5638373a550`。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt` 第 159 行 —— 基线条目（测试文件本身保留）。

### 删除后

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/main/MapWebHolder.kt` —— 不存在。
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt` —— 保留；原第 159 行基线条目已摘除，现第 159 行为 `"io/legado/app/ui/main/PersonDetailActivity.kt",`。

### 工作树状态（未 add / commit / push）

```
 D app/src/main/java/io/legado/app/ui/main/MapWebHolder.kt
 M app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt
```

`git diff --stat`（仅本单两个文件）：1 个源码文件 60 行删除 + 1 个测试文件 1 行删除，合计 61 行删除。

跨模块交接记录（非本单产出，未触碰，交对应在飞工单）：共享工作树内另有 reader/note 域并发改动 —— `ui/book/read/HighlightNoteDialog.kt`（M）、`ui/book/read/NoteDialogExitGate.kt`（??）、`ui/book/read/NoteDialogExitGateTest.kt`（??）；另有一份 B18 历史留档文档 untracked。上述文件均不在本单 diff 范围内。

## 七、边界遵守

- 删除不可逆：E1 / E2 证据先贴出、回证零引用后才删除；未使用 `git rm -r` 删目录，只对逐条回证过的单个文件执行 `rm`。
- 未触碰 B18 / B19 / B20 / B21 / P04 / B26 已改或在改域（`ui/card`、`ui/components` 其余组件、reader 域、`ui/note`、`help/allusion`）；本单唯一测试改动即锚卡点点名的基线文件 `DesignTokenGuardTest.kt`。
- 未本地编译、未跑任何测试；未执行 `git add` / `commit` / `push`。
- 未改动任何反引号测试函数名；既有函数名不含 `. : [ ] / ; < >`。

## 八、未验证项

1. **本地编译 / 单测**：按硬约束未执行 `./gradlew` / `kotlinc` / 任何测试运行；第五节断言口径为静态阅读测试逻辑得出，编译与单测红绿以云端 CI 为唯一验收手段。
2. **反射 / 动态脚本兜底**：`Class.forName`、字符串类名已静态 grep 零命中，未在运行时做类加载枚举；若后续存在 Rhino / JS 源按类名反射，需 CI 出包后另查。
3. **真机 View 树残留**：静态层面已无可达引用；历史进程内若残留旧 WebView 实例，随进程销毁自然释放，真机行为复核归属后续验收单，不在本单静态清剿范围内。
4. **docs 历史留档**：`docs/` 下 9 份历史台账 / 修复 / 验收 / 派单文档仍含 `MapWebHolder` 字样（`修复-B1-点字半屏Sheet.md`、`修复-DetailSheet贴皮.md`、`接手/00-入口.md`、`派单台账.md`、`派单队列.md`、`清理-B24-旧DetailSheet清剿.md`、`锚点/B27-MapWebHolder孤儿.md`、`问题清单-附录-逐文件.md`、`问题清单.md`），属历史记录，非代码引用，本单不清理；`问题清单.md` B27 行状态翻转与附录逐文件清单更新属调度层 / 文档工序，不在本单范围。
