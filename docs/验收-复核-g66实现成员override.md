锚点卡：docs/锚点/验收-g66实现成员override.md

# 验收-复核-g66 同签名实现成员缺 override 静态判据闸

- 被验对象：`data_scripts/check-member-override.py`（提交 `e42bae7` 入库；验收初读时为工作树未跟踪状态，复核末树已随他单提交）；接线 `data_scripts/rule-gate.sh:487`、分级 `data_scripts/gate-levels.json:962`；随单文档 `docs/修复-g66实现成员override.md`、`docs/锚点/g66实现成员override.md`。
- 验收方式：独立只读验收；未改被验脚本与任何产品代码，未做 git add / commit / push，未跑 gradle / kotlinc；全部数字由本验收方在仓外 scratch 临时目录与同一工作树自取，不采信写码方 `--selftest` 自评。
- 总结论：**有条件通过（编译面待 CI）**。七组判据中镜像双向、真仓终态、六类放行、间接沿链、接线同源、四类误伤探针实测成立；另探到四条待裁漏判/可审计性缺陷（改动面路径口径不匹配为首要项）、两条已处置登记口径边界与一条已闭环归属边界，逐条列于「新发现问题」节；clean 编译与 CI 绿红以云端 CI 为准。
- 判据计数：七组静态判据 7/7 按口径实测；新发现问题 7 条（问题 1/2/3/7 待裁，问题 4/5 已处置登记，问题 6 已闭环归编译链）。

## 守则回执

- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「永远找问题，解决问题，提出问题」（docs/机制-总控必读.md:546）
- 适用条款：B2（改后 grep 回证，每条夹具结论均回指命令与输出行）、C3（边界自行兜底不降级，待裁项逐条实测落空后果）、F8（新发现问题逐条带处置状态词与归属）、验收代理必读 §1 独立性（不采信写码方自评，计数自取）、验收代理必读 §9（新增文件结论最高有条件通过，编译面待 CI）、g66 本闸判据（接口表 + 实现方反查 + 六类放行 + 改动门口径）。

## 零、验收基线与取数口径

- 被验脚本现状：工作树 `data_scripts/check-member-override.py`，962 行；接口/抽象类成员表建模 `:576`，签名归一 `:337`，BFS 沿链追表 `:595`，实现方反查与红行 `:617`（方法 `:638`、属性 `:656`），改动面采集 `:675`，主判定 `:701`，audit 清单 `:732`，改动面 PASS 行 `:757`，自测夹具 `:784`。
- 夹具跑台：仓外 `/tmp/g66-verify/driver.py` 逐例 `tempfile.mkdtemp` 写夹具，直接 import 生产 `build_model([d])` / `evaluate(flat, table, judge_files=None)`，逐例断言退出码、红行标签、文件:行、成员名、放行计数，不只比退出码；夹具验完随临时目录自删，仓内零写入。
- 全量口径：`--audit` 与 `--changed` 在真实工作树实跑；独立复核计数由本验收方驱动遍历 `app/src`、`screenshot/src` 全部 `.kt` 自取，不引用写码方报告数字。
- 改坏推演：若成员表把抽象类默认 final 成员也收进去，同名 final 方法会被误报成缺 override；若签名归一不切默认值，接口带默认参、实现不带默认参的真 override 会被算成异签名漏判；若改动面文件集路径不归一，dirty 树上改动声明会被整批排除、闸面空转成假绿；若把扩展函数、局部函数、伴生同名、顶层同名都当类成员匹配，同名不同义写法会成片误红。本验收逐面对应构造探针证真/证伪。

## 一、判据 1：真事故复现（缺 override 必红，补上必绿）

- 红样本 A（仓外镜像，照事故原型自构）：`Host.kt` 接口声明 `fun currentTextPage(): TextPage?`、`fun textPageState(): InkState`、`val title: String`；`Controller.kt` `class Controller : Host` 三成员同签名声明全缺 `override`。驱动实跑 rc=1，3 条红行，标签均为 `MEMBER-OVERRIDE`：
  - `Controller.kt:3` 方法 `currentTextPage`，父类型 `Host`，原文 `fun currentTextPage(): TextPage? = null`。
  - `Controller.kt:4` 方法 `textPageState`，父类型 `Host`，原文 `fun textPageState(): InkState = InkState.Ok`。
  - `Controller.kt:5` 属性 `title`，父类型 `Host`，原文 `val title: String = "x"`。
  - 文件落点、行号、成员名、方法/属性类别四项齐备，不只退出码判红。
- 绿样本 B：同镜像三成员补齐 `override`（`override fun currentTextPage…`、`override fun textPageState…`、`override val title…`）。驱动实跑 rc=0，fails=0，已配对 override=3。
- 事故链回源：`git show 0c9fd30:app/src/main/java/io/legado/app/ui/book/read/ink/InkReadController.kt` 复核，`:141` `fun currentTextPage(): TextPage? =`、`:149` `fun textPageState(): InkTextPageState {` 均无 override；`git show 35bbb54` diff 仅两行 `-fun currentTextPage…` / `+override fun currentTextPage…`、`-fun textPageState…` / `+override fun textPageState…`，与镜像红→绿双向形态一致。
- 判据 1 结论：缺 override 必红且命中标签、文件行、成员名；补上 override 必绿并计数配对。判据成立。

## 二、判据 2：真仓终态（audit 候选 0，两条缝与两 FakeHost 均带 override）

- 命令与末行：`python3 data_scripts/check-member-override.py --audit` → `MEMBER-OVERRIDE AUDIT: 全仓清单（接口/抽象类 130 个 / 实现声明 227 个 / 候选缺 override 0 处 / 已配对 override 527 处；存量只登记不判红）`，rc=0。
- 独立复核：本验收方驱动 `build_model(collect_roots())` + `evaluate(..., judge_files=None)` 自取：接口/抽象类 130、实现声明 227、fails=0、matched=527，与 audit 末行逐项一致。
- 接口声明回源：`ReaderShellHost.kt:23` `interface ReaderShellHost`；`:72` `fun currentTextPage(): TextPage?`；`:78` `fun textPageState(): InkTextPageState`。
- 实现方现状回源：`InkReadController.kt:141` `override fun currentTextPage(): TextPage? =`；`InkReadController.kt:149` `override fun textPageState(): InkTextPageState {`，两条均带 override。
- 测试 FakeHost 回源：`GestureRouterTest.kt:27` `private class FakeHost : ReaderShellHost`，`:72` `override fun currentTextPage(): TextPage? = null`、`:74` `override fun textPageState(): InkTextPageState = InkTextPageState.NoChapter`；`ViewGestureBridgeTest.kt:29` 同类，`:69`、`:71` 两条 override 齐备。两个 FakeHost 对接口 11 个成员全部配对，audit 0 红与此一致。
- 判据 2 结论：真仓终态候选缺 override 为 0，事故两条缝与两个测试替身均带 override。判据成立。

## 三、判据 3：放行面六类逐类自构必绿

- SKIP:PRIVATE：接口 `fun bar(): Int`，实现方 `private fun bar(): Int = 1` 加一条 `override fun bar(): Int = 2`。驱动 rc=0，fails=0，放行计数 PRIVATE=1，配对 1。
- SKIP:EXTENSION：接口 `fun bar(x: Int)`，实现方 `fun Other.bar(x: Int) = Unit`（不同接收者扩展）加 `override fun bar(x: Int) {}`。驱动 rc=0，EXTENSION=1，配对 1。
- SKIP:DIFF-SIG：接口 `fun f(list: List<String>)`，实现方 `fun f(list: List<Long>) = Unit`。驱动 rc=0，DIFF-SIG=1；泛型实参不同按异签名放行，不判同签名。
- SKIP:LOCAL：接口 `fun bar()`，实现方 `override fun bar() { fun localBar() = Unit }`。驱动 rc=0，LOCAL=1，方法体内部局部函数不进类成员匹配。
- SKIP:COMPANION：接口 `fun bar()`，实现方 `companion object { fun bar() = Unit }` 加 `override fun bar() {}`。驱动 rc=0，fails=0，配对 1；伴生对象内同名不判类体重声明（计数缺口见新发现问题 2）。
- SKIP:TOPLEVEL：接口 `fun bar()`，实现方 `override fun bar() {}`，文件末尾另声明顶层 `fun bar() = Unit`。驱动 rc=0，fails=0，配对 1；类体外顶层同名不进类成员匹配（计数缺口见新发现问题 2）。
- 判据 3 结论：六类放行行为本身均成立，无误伤红行；其中 PRIVATE / EXTENSION / DIFF-SIG / LOCAL 四类计数落位，COMPANION / TOPLEVEL 两类放行成立但计数恒 0。

## 四、判据 4：间接实现沿链判红

- 镜像链：`Foo.kt` `interface Foo { fun bar() }`；`A.kt` `open class A : Foo { override fun bar() {} }`；`B.kt` `class B : A { fun bar() {} }`。驱动 rc=1，1 条红行：`B.kt:2` 方法 `bar`，父类型 `['A']`，原文 `fun bar() {}`。
- 沿链复核：`resolve_inherited`（`check-member-override.py:595`）BFS 从 `B` 的父类型 `A` 追到 `A` 的父类型 `Foo`，把 `Foo` 成员表合并进 `B`；`A` 自身已配对不判红，`B` 重声明同签名缺 override 判红。
- 边界判断：写码方自登「间接实现沿链追表」与实测一致；只追仓内 Kotlin 声明的链，链上仓内具体类若又继承别的接口/抽象类一并追入。本判据成立。

## 五、判据 5：已知边界三类逐类判定（仓内实例 + 影响面 + 定性）

- 边界一：中间具体类自带 `open fun` 不进父类型表。表构建 `:576` 只收 interface 全部成员与 abstract class 的 abstract/open 成员；`open class` 整体不入表。仓内实例：`Preference.kt:23` `open class Preference(...)`，`:121` `open fun onBindView(holder: PreferenceViewHolder)`；子类 `ColorPreference.kt:119` `override fun onBindView(holder: PreferenceViewHolder)`。若该 override 被删，本闸不会判红，属漏判方向（非误判）；kotlinc 编译链兜底。定性：漏判风险，范围限于仓内 `open class` 开放成员链，写码方自登成立。脚本在源码层本可读 open 修饰符，报告里「拿不到 JVM 契约」的理由偏弱，本质是范围取舍（见新发现问题 5）。
- 边界二：匿名对象表达式 `object : Foo {}` 内重写不建模。`CLASS_HEAD`（`:59`）只认带名 `class`/`object`，匿名对象头无标识符不命中；其体随属性初始化落在局部层，成员记 LOCAL 跳过。仓内实例：`LayoutManager.kt:12` 嵌套接口 `LayoutManagerFactory`，`:13` `fun create(...)`；`:21`、`:30`、`:39`、`:52`、`:66` 五处 `object : LayoutManagerFactory {`，体内 `override fun create(...)`。若匿名体内 override 被删，本闸不判红，属漏判方向；仓内该形态出现频率低，编译链兜底。定性：漏判风险，写码方自登成立（见新发现问题 4）。
- 边界三：Java/Android 父类方法不在表内。扫描只收仓内 `.kt` 声明，`Fragment`、`LifecycleService`、`RecyclerView.Adapter` 等 Java/Android 类型不在 `decl_by_name`，自然落空不误判。仓内实例：`BaseFragment.kt:20` `abstract class BaseFragment(...) : Fragment(layoutID)`，`:29` `override fun onViewCreated(...)`、`:38` `onMultiWindowModeChanged`、`:43` `onConfigurationChanged` 框架生命周期回调，本闸不查。定性：设计内保守，非本闸职责域，归编译链覆盖（见新发现问题 6）。
- 附带探针：函数类型构造参数 `class C(val f: (String) -> Unit) : Foo` 父类型仍正确识别为 `Foo` 并判红；父类型跨多行声明正常登记；同名不同元数重载（`fun bar()` 与 `fun bar(x: Int)`）不误伤。

## 六、判据 6：改动面与接线

- 改动面实跑：`python3 data_scripts/check-member-override.py --changed` → `MEMBER-OVERRIDE: PASS（改动面 0 个实现声明；缺 override 0；已配对 override 0；放行 ）`，rc=0；不带参数同口径实跑 rc=0。初读快照（HEAD `f2b60b8`）工作树改动 `.kt` 为 `InkActionDispatcher.kt`、`InkBubble.kt` 与未跟踪 `InkBubbleRouteResolverTest.kt`；复核末树（HEAD `34768cf`，改动 `.kt` 已随他单提交）复跑同一命令仍为 PASS、impl=0。改动面声明计数为 0 的原因见新发现问题 1，不能按「无声明改动」解读为闸面有效扫描过这些文件。
- 分级登记：`json.load(data_scripts/gate-levels.json)` 取 g66 条目：`id=g66`、`label=66`、`name=实现成员override`、`script=check-member-override.py --changed`、`nature=bad`、`scope=product-code`、`level=blocking`、`compensation=null`。
- 编排接线：`data_scripts/rule-gate.sh:487` `if gate_active g66; then`；`:488` `MO66=$(python3 data_scripts/check-member-override.py --changed 2>&1)`；`:489` 红态摘要 `grep -E 'MEMBER-OVERRIDE|FAIL'`，含 `MEMBER-OVERRIDE` 标签；`:490` `else skip_audit`。`gate_active` 未知 id 默认 blocking，g66 在分级表为 blocking 不被跳过。
- 接线机检：`python3 data_scripts/check-gate-wiring.py` → `GATE-WIRING: PASS（60 个门禁均接线；历史孤儿基线 0 个）`，rc=0。
- 语法机检：`bash -n data_scripts/rule-gate.sh` → `bash -n OK`，无语法错误输出。
- 判据 6 结论：分级 blocking、script 为 `--changed`、编排调用与红态摘要标签、接线机检、shell 语法五项成立；改动面文件集匹配存在口径缺陷（新发现问题 1）。

## 七、判据 7：误伤探针四类

- 探针一：同名不同参数普通函数。接口 `fun bar(x: Int)`，实现方另声明 `fun bar() = Unit` 加 `override fun bar(x: Int) {}`。驱动 rc=0，fails=0，配对 1；不同元数不按同签名判红，也不计 DIFF-SIG。
- 探针二：非 Kotlin（.java）实现。`Foo.kt` 接口 `fun bar()`，`C.java` `public class C implements Foo { public void bar() {} }`。驱动 `build_model` 只读 `.kt`，impl=0、fails=0；Java 实现不进表、不误伤。
- 探针三：同名属性与方法并存。接口 `fun bar()`，实现方 `val bar: Int = 1` 加 `override fun bar() {}`。驱动 rc=0，fails=0，配对 1；属性名与方法名同名互不卷入。
- 探针四：接口默认实现空体继承。接口 `fun bar() {}` 带默认实现，实现方 `class C : Foo {}` 空体继承。驱动 rc=0，fails=0，matched=0；未重声明的默认方法不被当成缺 override。
- 写码方 `--selftest` 实跑末行：`[selftest] 夹具已自删，双向自测通过（事故镜像红/补 override 绿；PRIVATE/EXTENSION/DIFF-SIG/TOPLEVEL/COMPANION/LOCAL 六类放行；间接实现沿链判红）。`，rc=0；本验收仅作交叉对照，未以此交差，结论均以仓外独立驱动为准。

## 八、机检四件套实跑末行（验收方自取）

- `python3 data_scripts/check-report-fields.py docs/验收-复核-g66实现成员override.md` → `REPORT-FIELDS: PASS（1 份，0 份不合格）`，rc=0。
- `python3 data_scripts/check-anchor-card.py --strict docs/验收-复核-g66实现成员override.md` → `汇总: PASS=1 FAIL=0（共 1 份）`，rc=0；WARN 四条为次要提及条款号 C3 / F8 / C5 / C6 无锚点卡（验收口径降级提示，不判红）。
- `python3 data_scripts/check-problem-finding.py docs/验收-复核-g66实现成员override.md` → `PROBLEM-FINDING: PASS（现行通过 1 份 / 历史豁免 0 份 / 基线豁免 0 份 / 判红 0 份）`，rc=0。
- `python3 data_scripts/check-onboarding.py` → `ONBOARD: PASS（3 份报告）`，rc=0；本单报告单判 `check_report` 返回空问题：指纹 `docs/机制-总控必读.md=c8fb35b71549` 与磁盘现行 sha256 前 12 位一致，三条逐字引用均回源命中，「适用条款」行在位。

## 新发现问题（借得结论前先红）

- 问题 1 · 待裁（归属：g66 闸脚本维护单）：改动面文件过滤路径口径不匹配，dirty 工作树上改动声明恒被排除，blocking 闸改动面空转。`check-member-override.py:561` 模型 `d.file` 取相对源码根路径（形如 `main/java/.../C.kt`）；`:675` `git_kt_diff` 返回仓内相对路径（形如 `app/src/main/java/.../C.kt`）；`:711` `if judge_files is not None and d.file not in judge_files` 直接比对，两处口径之间无归一。仓内实测：`git_kt_diff` 取到 3 个改动 `.kt`，模型直配 0 条；按仓内相对名归一后可配 3 条（`InkActionDispatcher.kt` 两条声明、`InkBubbleRouteResolverTest.kt` 一条声明）。仓外镜像复现：roots 挂在 `app/src` 下，坏样本 `C.kt` 缺 override，judge 用仓内相对名 → impl=0、fails=0，应红未红；同结构坏样本在 `judge_files=None` 全仓口径下正常判红（见判据 1 红样本 A）。后果：改动文件内出现 0c9fd30 同类缺 override 时，`--changed` 通路静默放过；clean 树 judge 为空集属「无改动可判」的设计内口径，不属本缺陷。修法方向：judge 名统一归一到模型相对根，或 `d.file` 存仓内相对名；修后补一份 dirty 树改动声明被真实判到的镜像证据。
- 问题 2 · 待裁（归属：g66 闸脚本维护单）：SKIP:COMPANION / SKIP:TOPLEVEL 放行计数器永不递增。`:437` 伴生对象整段配平跳过但未记 COMPANION；顶层函数不进类成员扫描，TOPLEVEL 无计数点；`:620` 两个键初始化为 0 后再无 `+= 1`。镜像 C5/C6 两类均绿、无误伤，放行行为成立；但末行「放行 …」中这两类恒 0，与文档「标签计数进末行、放行可审计」的口径不符。修法方向：两个跳过点补计数，或在文档与末行注明这两类不计数。
- 问题 3 · 待裁（归属：g66 闸脚本维护单）：主构造参数内声明的 override 属性不建模。成员扫描只认类体一层的 `val`/`var` 声明；镜像 `class C(val name: String) : Foo` 缺 override → impl=0、未判红（应红）。仓内实例：`AlertBuilder.kt:16` 接口 `AlertBuilder`、`:17` `val ctx: Context`；`AndroidAlertBuilder.kt:13` `internal class AndroidAlertBuilder(override val ctx: Context) : AlertBuilder<AlertDialog>`，构造参数属性 override 被本闸漏看。漏判方向（非误判），编译链兜底。修法方向：主构造参数表解析 `override val/var` 并入成员表，按属性名参与配对与反查。
- 问题 4 · 已处置（归属：g66 口径边界，登记待后续迭代）：匿名对象表达式 `object : Foo {}` 内重写不建模。仓内实例 `LayoutManager.kt:21/:30/:39/:52/:66` 五处匿名对象实现 `LayoutManagerFactory`；漏判风险低、编译链兜底，维持现状登记，真红样本出现后再补匿名对象块递归。
- 问题 5 · 已处置（归属：g66 口径边界，登记待后续迭代）：中间具体类自带 open fun 不进父类型表。仓内实例 `Preference.kt:121` → `ColorPreference.kt:119` 开放成员链；漏判风险限于 `open class` 开放成员，维持现状登记。附注：脚本源码层可读 open 修饰符，「拿不到 JVM 契约」并非技术上做不到，取舍是范围决定，后续若扩表追具体类 open 成员，需同步评估 final 同名误报面。
- 问题 6 · 已闭环（归属：编译链职责域，非本闸缺陷）：Java/Android 父类方法不在表内。仓内实例 `BaseFragment.kt:20` : Fragment，`:29/:38/:43` 框架回调 override 不查；这是有意边界，Java 契约不在仓内 Kotlin 声明表，天然不误判，也不归本闸拦。
- 问题 7 · 待裁（归属：g66 闸脚本维护单）：属性反查只按名字匹配，不核类型串。表内属性集合存名字（`:587`），实现方同名属性即入候选；接口 `val a: Int` 与实现 `val a: String` 这类类型不兼容情形，闸只判缺 override、不判类型错配。误报风险低（类型不兼容本就是编译错），语义完整性留后续迭代；修法方向：属性签名比照方法串归一后再配对。

## 未验证项披露

- 本机无编译链与 JVM/Android 运行环境，clean 编译、类型推断、重载解析、属性类型兼容与单测绿红以云端 CI 为准；被验闸脚本为 g66 单新增产物（Python 闸，不进 Kotlin 编译链），结论最高只到「有条件通过（编译面待 CI）」，未替 CI 背书。
- 三引号串嵌套引号、字符字面量转义、lambda 多层嵌套括号、泛型上下界等极端状态机组合未穷尽夹具；解析兜底取到末尾未抛异常，但边界覆盖不全。
- CI 日志原句 `hides member of supertype 'ReaderShellHost' and needs an 'override' modifier` 取自写码方文档与流水留档，本机未取到云端原始 CI 日志缓存；已用提交链 `0c9fd30`（缺 override）→ `35bbb54`（补两条 override）的 diff 复核事故形态。
- 验收时点：初读快照 HEAD `f2b60b8`，被验脚本为工作树未跟踪新增；复核末树 HEAD `34768cf`，g66 脚本与接线已随提交 `e42bae7` 入库，本验收未做 git add/commit/push。
