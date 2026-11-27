锚点卡：docs/锚点/验收-符号门禁PACKAGE层.md

## ANCHOR-ACK

1. 任务首行逐字：独立验收单——审两道门禁改造与一笔 CI 修红提交，仓库 /home/user/Doubao/chats/38454122521460994/legado；只做验收，不许改脚本、不许改代码、不许 git add/commit/push。被验对象：`data_scripts/check-symbol-existence.py` 新增的 PACKAGE 层（含扩展函数索引、宽松兜底、坏样本夹具）；`app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt` 删掉跨包 VerticalPageView import 的修红改动。
2. 绝对路径：
   - /home/user/Doubao/chats/38454122521460994/legado/docs/验收-复核-符号门禁PACKAGE层.md（本报告）
   - /home/user/Doubao/chats/38454122521460994/legado/docs/锚点/验收-符号门禁PACKAGE层.md（本单锚点卡）
   - /home/user/Doubao/chats/38454122521460994/legado/data_scripts/check-symbol-existence.py（被验门禁脚本，只读）
   - /home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt（被验修红文件，只读）
3. 第一步命令：`git show 4131445 --stat` 取改动面；再按判据 1~5 各自构造仓外迷你仓夹具、实跑生产 `main(root)` 取数；全仓门禁与两道回归脚本实跑贴末行；最后反扫 loose/chain 放行清单核对有无现存被遮蔽真红。

## 守则回执

- 独立性：本单为独立验收代理，未复用写码上下文；所有计数与结论由验收方自己重算，不采信写码方自评。
- 逐字对照：判据五条逐条回源到脚本行号与实跑输出；规格三类（判据/契约/验收标准）不压缩。
- 红线：未改任何脚本与代码，未做 git add/commit/push，未跑 gradle/kotlinc/assemble；仓外探针夹具放代理 scratch 目录，跑完自删，仓内零写入。
- 编译面口径（验收必读 §9）：本单含 import 改动，静态层结论最高「有条件通过（编译面待 CI）」，不替 CI 背书。

- 指纹：docs/机制-总控必读.md=038492e5cf88
- 逐字引用 1：「每条下限必须能被一条命令判真假，判据落在脚本里，不落在文档措辞里。」（docs/机制-总控必读.md）
- 逐字引用 2：「新判据必须带「必失败样本 + 放行样本」，且每个样本能单独跑红/跑绿，不许靠聚合结果凑。」（docs/机制-总控必读.md）
- 逐字引用 3：「存量与新增分开判：存量按 audit + 补偿通道登记，新增按 blocking 拦。」（docs/机制-总控必读.md）
- 适用条款：机制-总控必读「质量下限」条（原编号 F-八）第 1/2/3 条（下限落脚本可判真假、必失败与放行双侧样本、存量 audit 与新增 blocking 分判）；总纲验收独立；含 import 改动最高判「有条件通过（编译面待 CI）」。
## 机器头

- 被验 commit：`4131445d9f9204dd7b143a9cede5175deb1f217e`（短 4131445）；采集时点 HEAD `fc3e19c`，`git merge-base --is-ancestor 4131445 HEAD` 退出码 0，被验提交在祖先链上。
- 改动面（`git diff --name-status 4131445^ 4131445`）：`M app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt`；`M data_scripts/check-symbol-existence.py`。门禁脚本自 4131445 至 HEAD diff 行数 0，验收对象即当下磁盘版本。
- 工作区披露：采集期间工作区有他单在飞的未提交改动（ink 族：修改 `InkReadActivity.kt`、`InkReadController.kt`、`InkReaderShell.kt`、`ReaderShellHost.kt`、`GestureRouterTest.kt`、`ViewGestureBridgeTest.kt`，新增 `InkTextPageState.kt`、`InkTextPageSurface.kt`、`ReadBookTextDataSource.kt`、`InkTextPageStateTest.kt`），不在本单验收范围；本单事实以 4131445 快照与 HEAD 版本为准。被验修红文件 `InkReadController.kt` 的 HEAD 快照为 440 行，工作区 459 行（他单在飞），残留使用点行号在两版中均为第 79 行。
- 总结论：**有条件通过（编译面待 CI）**。静态层五条判据中判据 1/2/3/5 销项 PASS；判据 4 坏样本可单独证红，但自测断言粒度与夹具覆盖存在缺口；无阻断本单收口的缺陷；6 条新发现问题（2 条假阴性/覆盖盲区、2 条自测缺口、1 条接线观察项、1 条存量债）另立加固批次跟进。
- 判据：5 组中 4 组销项 PASS，1 组（判据 4）有条件销项——坏样本逐条可证红成立，缺口列入加固条件。
- 新缺陷数：阻断 0；非阻断盲区/缺口 4；观察项 1；存量债 1（非本单引入）。

## 一、验收范围与对象核对

- 门禁脚本新增 PACKAGE 层落点：`data_scripts/check-symbol-existence.py:531-599`（`package_import_issues`）；索引建表 `data_scripts/check-symbol-existence.py:543-564`；三重放行：前缀链回退 `data_scripts/check-symbol-existence.py:578-586`、同包宽松兜底 `data_scripts/check-symbol-existence.py:589-593`、名字仓内无归 NODEF `data_scripts/check-symbol-existence.py:595-596`；退出码并入 `data_scripts/check-symbol-existence.py:643-653`。
- 修红落点：`git show 4131445 -- InkReadController.kt` 显示仅删一行 `-import io.legado.app.ui.book.read.vertical.VerticalPageView`（旧版位于文件第 23 行）。
- 门禁接线：`data_scripts/rule-gate.sh:460-461` 调 `check-symbol-existence.py`；`data_scripts/gate-levels.json:930-940` g63 登记 blocking，compensation 为 null。

## 二、逐条销项

### 销项 1：PACKAGE 层真能拦（自构三迷你仓样本）—— PASS

判据原句：类在 A 包却从 B 包 import（应 FAIL）；同名扩展函数从正确包 import（应 PASS）；嵌套类/伴生成员从外层 import（应 PASS）；不许只用仓内已有样本。

验收方自取数：仓外 tempfile 迷你仓，夹具驱动直接调用生产函数 `main(root)`（不走常量比对），文件按 `app/src/main/java/...` 布局对齐 `SOURCE_ROOTS`。夹具文件名为仓外临时产物，不在仓内，下文只记夹具内角色与门禁输出条目，不构成仓内文件锚点。

- 样本 A（跨包类错引，应 FAIL）：alpha 包定义 `class AlphaBox(val n: Int)`；导入方在 use 包，错写 `import io.legado.app.beta.AlphaBox`，调用 `AlphaBox(1)`。实跑门禁输出 PACKAGE 条目：夹具 use 侧文件第 3 行，`import io.legado.app.beta.AlphaBox → 仓内同名定义实际在 io.legado.app.alpha.AlphaBox`；末行 `SYMBOL-EXISTENCE: FAIL（扫描 2 文件，调用 2 处，NODEF 0 / ARITY 0 / PACKAGE 1 ...）`，rc=1，与期望 1 一致。
- 样本 B（正确包扩展函数，应 PASS）：gamma 包定义 `fun String.gammaMark(): String = this + "!"`；use 侧 `import io.legado.app.gamma.gammaMark`，调用 `s.gammaMark()`。实跑末行 `SYMBOL-EXISTENCE: PASS（扫描 2 文件，调用 1 处，NODEF 0 / ARITY 0 / PACKAGE 0 ...）`，rc=0，与期望 0 一致。
- 样本 C（嵌套类/伴生成员外层 import，应 PASS）：delta 包定义 `class Outer { class Nested(val x: Int); companion object { const val TAG = "outer"; fun companionMember(): String = TAG } }`；use 侧分别 `import io.legado.app.delta.Outer.Nested`、`import io.legado.app.delta.Outer.companionMember`，调用 `Nested(1).let { companionMember() }`。实跑末行 `SYMBOL-EXISTENCE: PASS（扫描 2 文件，调用 3 处，NODEF 0 / ARITY 0 / PACKAGE 0 ...）`，rc=0，与期望 0 一致；前缀链回退通路（`data_scripts/check-symbol-existence.py:578-586`）放行成立。
- 附加探针 D（跨包扩展错引）：扩展定义在 alpha，导入方在 use 错引 beta.alphaExt，实跑 PACKAGE 条目 `import io.legado.app.beta.alphaExt → 仓内同名定义实际在 io.legado.app.alpha.alphaExt`，rc=1；PACKAGE 层对扩展换包同样拦得住。
- 附加探针 E（双包同名类）：alpha 与 omega 各有 `class Dup`，use 错引 beta.Dup，实跑 PACKAGE 条目 `import io.legado.app.beta.Dup → 仓内同名: io.legado.app.alpha.Dup / io.legado.app.omega.Dup`，rc=1；多同名场景报告列出前两条，未误放行。

### 销项 2：假红控制（全仓计数）—— PASS

判据原句：全仓跑 `python3 data_scripts/check-symbol-existence.py` 必须看到 NODEF 0 / ARITY 0 / PACKAGE 0；不为 0 时逐条判定真错/假红并给改法建议。

验收方自取数（仓根实跑，新鲜输出）：

- 末行（验收终态复跑，工作区含他单在飞未提交 kt）：`SYMBOL-EXISTENCE: PASS（扫描 2164 文件，调用 135946 处，NODEF 0 / ARITY 0 / PACKAGE 0，跳过生成 0 / 外部库 18586 / 标准库 43804 / 未知 70745）`，退出码 0。三类计数全 0，无需逐条甄别假红。
- 存量脏 import 复核（hint 点名两条，验收方独立回源）：`app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt:51` 与 `app/src/main/java/io/legado/app/ui/widget/dialog/BottomWebViewDialog.kt:92` 均为 `import io.legado.app.utils.get`。仓内同名扩展：`app/src/main/java/io/legado/app/utils/MapExtensions.kt:12` `fun <T> HashMap<String, T>.get(key: String, ignoreCase: Boolean = false): T?`；另一同名在 `app/src/main/java/io/legado/app/help/http/OkHttpUtils.kt:120`、`app/src/main/java/io/legado/app/help/http/OkHttpUtils.kt:132`（`Request.Builder.get`）。判定：这两条是真实存在的遗留脏 import，但 kotlinc 能过（HashMap 同名扩展兜底），属存量债，不是门禁假红；门禁未报 PACKAGE 与该判定一致。
- 遮蔽反扫（验收方外挂只读探针，复刻 `package_import_issues` 索引与查表，单列 loose/chain 放行项）：前缀链放行 221 条；loose 放行 613 条，其中「仓内另有同名声明」的仅上述两条 `utils.get`；严格口径重判仍应报 PACKAGE 的条目 0 条。结论：当前仓内不存在被宽松兜底遮蔽的未知真红。
- 改法建议（非阻断，转存量债批次）：两条 `utils.get` 脏 import 应删除或改为确切接收者类型的调用；泛型接收者扩展未进索引的根因见 N6。

### 销项 3：修红改动正确性 —— PASS

判据原句：确认该类确实定义在同包（给文件与行号）；确认删除 import 后该符号在文件内仍可解析（同包无需 import）；扫全仓确认没有其他地方也写了同样的跨包 import。

验收方自取数：

- 类定义位置：`app/src/main/java/io/legado/app/ui/book/read/ink/VerticalPageView.kt:1` `package io.legado.app.ui.book.read.ink`；`app/src/main/java/io/legado/app/ui/book/read/ink/VerticalPageView.kt:45` `class VerticalPageView @JvmOverloads constructor(`，主构造形参 `context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0`。
- 修红文件包声明：`app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt:1` `package io.legado.app.ui.book.read.ink`，与定义同包；同包声明无需 import 即可见。残留使用点：`app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt:79` `val pageView: VerticalPageView = VerticalPageView(context)`，实参 `context` 对齐主构造首参 `context: Context`，其余两参有默认值。
- 删除行回源：`git show 4131445 -- InkReadController.kt` diff 仅 `-import io.legado.app.ui.book.read.vertical.VerticalPageView` 一行；修红前旧版该行位于文件第 23 行（`git show 4131445^:.../InkReadController.kt` 实跑命中）。
- 全仓扫描：`grep -rn "import io.legado.app.ui.book.read.vertical.VerticalPageView" app/src screenshot/src --include=*.kt` 退出码 1（0 命中）；`grep -rn "import.*VerticalPageView" app/src screenshot/src --include=*.kt` 退出码 1（全仓无任何文件 import 该类）。
- 同包另一引用方：`app/src/main/java/io/legado/app/ui/book/read/ink/InkReaderShell.kt:1` 同为 ink 包；4131445 快照中 VerticalPageView 引用在第 132、143 行两处（`(engineView as? VerticalPageView)`），工作区他单在飞已挪至第 139、150 行；grep 该文件 VerticalPageView import 退出码 1，同包解析，无需补 import。
- 保留的 vertical 包 import 逐条回源均有定义：`VerticalLayoutEngine` → `app/src/main/java/io/legado/app/ui/book/read/vertical/VerticalLayoutEngine.kt:20`；`VerticalLayoutEngineFactory` → `app/src/main/java/io/legado/app/ui/book/read/vertical/AndroidVerticalFactory.kt:43`（object）；`VerticalPage` → `app/src/main/java/io/legado/app/ui/book/read/vertical/VerticalModels.kt:41`（data class）；`verticalTitle` → `app/src/main/java/io/legado/app/ui/book/read/vertical/LegadoChapterAdapter.kt:18`；`verticalBodyText` → `app/src/main/java/io/legado/app/ui/book/read/vertical/LegadoChapterAdapter.kt:21`。无连带误删。

### 销项 4：必失败样本质量（--selftest 逐条审查）—— 有条件销项

判据原句：审 `--selftest` 里每个坏样本是否单独可证红（而不是靠聚合结果凑出来），并指出至少一处盲区。

验收方自取数：`python3 data_scripts/check-symbol-existence.py --selftest` 退出码 0，六夹具各自独立 tempfile 迷你仓、各自调用生产 `main(t#)`、finally 自删。

- 坏样本一 NODEF（`data_scripts/check-symbol-existence.py:676-687`）：独立仓 use 侧 `import io.legado.app.lib.missingSym` + `missingSym(1, 2)`；断言 `rc == 1`（`data_scripts/check-symbol-existence.py:684`）。实跑该仓输出 NODEF 条目：missingSym 自有 import 指向的符号仓内反查不到 fun/构造定义，NODEF 1 条，单独可证红。
- 坏样本二 ARITY（`data_scripts/check-symbol-existence.py:690-701`）：独立仓 `import io.legado.app.lib.one` + `one(1, 2, 3)`；断言 `rc == 1`（`data_scripts/check-symbol-existence.py:698`）。实跑输出 ARITY 条目：调用 3 参，可核对重载 arity=1(top)，无一可容纳，ARITY 1 条，单独可证红。
- 坏样本三 PACKAGE（`data_scripts/check-symbol-existence.py:752-767`）：独立仓 vertical 侧文件 `import io.legado.app.ui.book.read.vertical.Widget`，定义 Widget 在 ink 包；断言 `rc == 1`（`data_scripts/check-symbol-existence.py:764`）。实跑输出 PACKAGE 条目：import vertical.Widget → 仓内同名定义实际在 ink.Widget，PACKAGE 1 条，单独可证红。
- 三条放行夹具：两参重载命中（`data_scripts/check-symbol-existence.py:704-715`）、一参匹配（`data_scripts/check-symbol-existence.py:718-729`）、`?.` 安全调用打到同名扩展（`data_scripts/check-symbol-existence.py:733-749`），各自独立仓 rc=0，实跑输出均 PASS。
- 缺口（不阻断本单，列入加固条件）：
  - 断言粒度：三处坏样本断言只比退出码 rc，不校验命中的 severity 标签；若夹具因别类红退出 1，断言仍会通过（`data_scripts/check-symbol-existence.py:684`、`data_scripts/check-symbol-existence.py:698`、`data_scripts/check-symbol-existence.py:764`）。
  - 夹具覆盖：无扩展函数跨包错引夹具（探针 D 证明当前能拦，但无回归锁）；无嵌套类/伴生成员放行链夹具（样本 C 通路未被 selftest 锁定）；无泛型接收者扩展宽松兜底夹具（MapExtensions.get 形态未上锁）。
- 盲区（实跑复现，详见第三节 N1/N2）：loose 兜底 fun 分支越过声明位匹配函数体提及；通配符 import 整行不进 PACKAGE 判定。

### 销项 5：回归风险 —— PASS

判据原句：跑 `python3 data_scripts/check-compose-pitfalls.py app/src` 与 `kotlin-syntax-lint.py`（被改的 kt）确认无连带问题。

验收方自取数：

- `python3 data_scripts/check-compose-pitfalls.py app/src`：末行 `COMPOSE-PITFALLS: PASS（2157 个 Kotlin 文件，A/B/C 三类形态 0 命中）`，退出码 0。
- `python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt`：末行 `KOTLIN-LINT: PASS（1 个文件）`，退出码 0。
- 开工四机检补跑：`cross-module-guard.py` → `GUARD: PASS（无代码改动）`；`debt-ratchet.py` → `RATCHET: PASS（当前 {'dual': 8, 'compat': 2, 'oversized_warn': 50, 'deprecated': 1, 'wip': 2}，未超基线）`；`check-combo-coverage.py` → `COMBO-COV: PASS（关键对 27 条，缺陷 9 条均已落层归因）`；`check-report-fields.py` → `REPORT-FIELDS: PASS（本轮无报告改动）`。
- 证据包：`python3 data_scripts/acceptance-evidence.py --rev 4131445` 三值建议 `有条件通过（编译面待 CI）`，依据为存在 import 改动；边界为真类型推断、重载解析、泛型约束、协程契约只有编译器能判。

## 三、新发现问题

- N1【非阻断·假阴性盲区，归属 g63 门禁加固单】宽松兜底 fun 分支越界匹配函数体提及。位置 `data_scripts/check-symbol-existence.py:589-591`，loose 正则 fun 段为 `fun\s+.*?\b<simple>\b`，`.*?` 可越过声明位吃到函数体。探针 F 复现：alpha 包定义 `AlphaBox`，beta 包 Helper 函数体内文本提及 `AlphaBox::class`，use 文件错引 `beta.AlphaBox`；实跑 rc=0（期望 1），真跨包错引被放行。当前仓内反扫严格口径 BAD=0，无现存被遮蔽真红；触发条件较窄（错包源文件 fun 体提及同名），不阻断本单收口。加固方向：fun 段只匹配声明位（fun 名与接收者之后的形参列表位置），不得 `.*?` 跨入函数体；或要求同名在目标包以声明形态（class/object/interface/val/var/fun 签名行）出现。
- N2【非阻断·覆盖盲区，归属 g63 门禁加固单】通配符 import 整行不参与 PACKAGE 判定。探针 G 复现：`import io.legado.app.beta.*` 形态下 PACKAGE 层无感（`data_scripts/check-symbol-existence.py:574-598` 按末段简单名查 `by_name`，`*` 反查落空归未知跳过）。仓内现存自有通配符 import 10 余条，已核样例：`app/src/main/java/io/legado/app/help/ReplaceAnalyzer.kt:5`、`app/src/main/java/io/legado/app/help/storage/ImportOldData.kt:14`、`app/src/main/java/io/legado/app/model/Debug.kt:7`；这些包内符号若换包，本闸不报警。加固方向：通配符 import 至少登记被通配包名，配合调用点简单名反查候选包集合。
- N3【非阻断·自测缺口，归属 g63 自测加固单】坏样本断言只比退出码，不校验 severity 标签（`data_scripts/check-symbol-existence.py:684`、`data_scripts/check-symbol-existence.py:698`、`data_scripts/check-symbol-existence.py:764`）。加固方向：断言输出行首类别分别为 NODEF / ARITY / PACKAGE，防止别类红蒙混。
- N4【非阻断·自测缺口，归属 g63 自测加固单】selftest 缺三条通路夹具：扩展函数跨包错引、嵌套类/伴生成员外层放行链、泛型接收者扩展宽松兜底。加固方向：按本单探针 D/C 与 MapExtensions.get 形态各补一正一反夹具。
- N5【观察项，归属 g63 接线维护】`data_scripts/rule-gate.sh:461` 红态摘要 `grep -E 'NODEF|ARITY'` 未含 PACKAGE 标签；PACKAGE-only 红时退出码仍判 1，但摘要明细不列 PACKAGE 行。另 `data_scripts/gate-levels.json:930-940` g63 理由文本只写 NODEF/ARITY，未补 PACKAGE 形态描述。加固方向：摘要 grep 扩 `PACKAGE`，分级理由补第三类。
- N6【存量债·非本单引入，归属 utils 清理批次】`app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt:51`、`app/src/main/java/io/legado/app/ui/widget/dialog/BottomWebViewDialog.kt:92` 两条 `import io.legado.app.utils.get` 为遗留脏 import；根因之一是 `data_scripts/check-symbol-existence.py:525-528` 的 `EXT_DECL_RE` 接收者字符类不含空格，`app/src/main/java/io/legado/app/utils/MapExtensions.kt:12` `fun <T> HashMap<String, T>.get(...)` 未进 fqn 索引，靠 loose 兜底放行。建议存量债批次清理 import，并扩接收者字符类以覆盖带空格/逗号的泛型接收者。

## 四、收口条件（均可机械判定）

- C-1：云端 CI 被验提交链 Verify 单测全绿且 Screenshot 绿，编译面收口。本单验收方未直拉 CI run 日志；仓库台账 `dbe70ce` 记录 44f5a6b Verify + Screenshot 双绿，该记录由后续 CI 状态复核，本单不替 CI 背书。
- C-2：N1/N2/N3/N4 加固批次落地后，复跑 `python3 data_scripts/check-symbol-existence.py --selftest` 全绿，且全仓 `check-symbol-existence.py` 仍 NODEF 0 / ARITY 0 / PACKAGE 0，不新增假红。

## 五、未验证项

- 真类型推断、重载解析、泛型约束、协程契约：静态闸不覆盖，只有 clean 编译与单测能判（证据包边界口径）。
- 真机观感、性能、GLES 表现不在本单范围。
- 未直接拉取 CI run 日志复核 44f5a6b 双绿结论；采信仓库台账文字记录，待 CI 状态收口。
- 仓外探针夹具位于代理 scratch 目录（`pkg_accept_driver.py` / `pkg_blind_driver.py` / `pkg_loose_audit.py`），不属仓库交付物；夹具进程结束后 tempfile 目录已自删。

## 六、复跑命令与真实输出

- 全仓门禁：`python3 data_scripts/check-symbol-existence.py` → `SYMBOL-EXISTENCE: PASS（扫描 2164 文件，调用 135946 处，NODEF 0 / ARITY 0 / PACKAGE 0，跳过生成 0 / 外部库 18586 / 标准库 43804 / 未知 70745）`，rc=0。
- 门禁自测：`python3 data_scripts/check-symbol-existence.py --selftest` → 六通路 OK，`[selftest] 夹具已自删，双向自测通过。`，rc=0。
- Compose 陷阱：`python3 data_scripts/check-compose-pitfalls.py app/src` → `COMPOSE-PITFALLS: PASS（2157 个 Kotlin 文件，A/B/C 三类形态 0 命中）`，rc=0。
- 语法 lint：`python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt` → `KOTLIN-LINT: PASS（1 个文件）`，rc=0。
- 证据包：`python3 data_scripts/acceptance-evidence.py --rev 4131445` → 三值建议 `有条件通过（编译面待 CI）`，rc=0。
- 改动面：`git diff --name-status 4131445^ 4131445` → 上述 kt 与 py 两文件 M；`git diff 4131445 HEAD -- data_scripts/check-symbol-existence.py` 行数 0。
