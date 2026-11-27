> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=6ab072c0e6de；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批3-阅读器贴皮 §三）
> 回执缺失（规则上线前交付，留档）

锚点卡：docs/事故复盘-四-债务棘轮顶出基线.md

# 修复 · Ink 内容编辑面板拆分后编译红（只修红，最小改动，不动逻辑）

- 基线 HEAD：`88e62bc`（已推送）。CI 在该提交双 workflow（Verify + Screenshot）全红，24 条编译错全部集中在「731 行面板拆 4 文件」后漏带 / 写错 import 的 3 个 Ink 文件。
- 文件域白名单（本单只碰这 3 个代码文件 + 本报告）：
  - `app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditFooter.kt`
  - `app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditRules.kt`
- 红线遵守：禁本地编译（沙箱无 Android SDK，编译由 CI 判）；禁 git add/commit/push；不加功能、不改业务口径、不砍拖拽 / 进度条能力。
- 改动量（`git diff --numstat`，仅本单 3 文件）：Editor +12/-6；Footer +1/-0；Rules +3/-1。

## 1. 逐条报错 → 根因 → 改动 → grep 回证

### 1.1 `InkContentEditEditor.kt` — `Orientation` 包名写错

- 报错：`:4:36`、`:147:35` `Unresolved reference 'Orientation'`。
- 根因：拆分时把 import 抄成 `androidx.compose.foundation.Orientation`；该符号真实包是 `androidx.compose.foundation.gestures.Orientation`（仓内已编译基线 `ui/components/InkBottomSheet.kt:7`、`InkHalfSheet.kt:11` 同包可证）。
- 实证：解包缓存的 `foundation-android/1.12.1` aar，类路径 `androidx/compose/foundation/gestures/Orientation.class` 存在；`foundation/` 根包下无此类。
- 改动：`:4` import 由 `foundation.Orientation` 改为 `foundation.gestures.Orientation`；`:153` 用法 `Orientation.Horizontal` 不变。
- grep 回证：

```
$ grep -n "Orientation" app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt
4:import androidx.compose.foundation.gestures.Orientation
153:                    orientation = Orientation.Horizontal,
```

### 1.2 `InkContentEditEditor.kt` — `TextFieldValue` 包名写错（连带 5 条类型推断错）

- 报错：`:30:33` `Unresolved reference 'TextFieldValue'`；连带 `:83/:85` `Cannot infer type for type parameter 'T'`、`:87 selection`、`:88 composition`、`:94 field`、`:96 text` 同一根因——类型未解析，`mutableStateOf` / lambda 形参全部推不出。
- 根因：import 写成 `androidx.compose.ui.text.TextFieldValue`；真实包是 `androidx.compose.ui.text.input.TextFieldValue`。
- 实证：解包缓存的 `ui-text-android/1.12.1` aar，类路径 `androidx/compose/ui/text/input/TextFieldValue.class` 存在；`ui/text/` 根包下无此类。S5 种子表 `data_scripts/import-pack-map.json` 登记 `TextFieldValue -> androidx.compose.ui.text.input`。
- 改动：`:33` import 改到 `ui.text.input.TextFieldValue`；`:85/:87` 构造与 `selection` / `composition` / `field.text` 用法不变，类型解析恢复后连带错一并消除。
- grep 回证：

```
$ grep -n "TextFieldValue" app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt
33:import androidx.compose.ui.text.input.TextFieldValue
44: * 〔职责边界〕本件只装正文录入（[TextFieldValue] 选区保留、命中段暖金底纹标注、
83:    // 本地持有 TextFieldValue：选区随输入保留；正文外部变化（加载 / 切纯文本 / 命中刷新）时
85:    var fieldValue by remember { mutableStateOf(TextFieldValue(annotated)) }
87:        fieldValue = TextFieldValue(
```

### 1.3 `InkContentEditEditor.kt` — `ScrollState.value` 写 setter 私有，改用公开 API

- 报错：`:135:41`、`:143:41` `Cannot access 'value': it is private in 'androidx.compose.foundation.ScrollState'`；`:143:47`、`:144:43/:144:46` 是随之而来的 Int/Float 类型错。
- 实证（先查依赖再处置，不凭记忆）：解包 `foundation-android/1.12.1` aar 的 `ScrollState.class`，常量池字符串确认公开成员含 `value`（读 getter）、`maxValue`、`scrollTo`、`scrollBy`、`dispatchRawDelta`；方法描述符 `(F)F` = `dispatchRawDelta(Float):Float`、`(I)V` = `scrollTo(Int):Unit`、`(I)Ljava/lang/Integer;` = `scrollBy(Int):Int`。`value` 的写 setter 私有（CI 报错原文已点名），读 getter 公开——`:127/:128` 读 `scrollState.value` / `maxValue` 保留不动。
- 处置（二选一取「确为私有」分支；不反射、不 `@Suppress`、不删进度条 / 拖拽）：
  - 点按定位：`rememberCoroutineScope()` 取作用域，`scope.launch { scrollState.scrollTo(target) }`（suspend 定位走公开 `scrollTo`）。
  - 拖拽增量：`rememberDraggableState { delta -> scrollState.dispatchRawDelta(delta) }`；`dispatchRawDelta` 内部按 `0..maxValue` 夹取回卷，替代原手写 `(value + delta).coerceIn(...)`。
- 改动落点：`:22` 加 `rememberCoroutineScope` import；`:38` 加 `kotlinx.coroutines.launch` import；`:126` 取 scope；`:140-141` 点按改 `scrollTo`；`:149-150` 拖拽改 `dispatchRawDelta`。
- grep 回证：

```
$ grep -n "scrollState.value =" app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt
zero-hit（无私有 setter 直写残留）

$ grep -n "scrollTo\|dispatchRawDelta\|rememberCoroutineScope" app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt
22:import androidx.compose.runtime.rememberCoroutineScope
126:    val scope = rememberCoroutineScope()
141:                            scope.launch { scrollState.scrollTo(target) }
150:                            scrollState.dispatchRawDelta(delta)
```

### 1.4 `InkContentEditEditor.kt` — `dp` import 复核（CI 清单外，同文件必然报错项）

- 任务书提示 `Column(Modifier.padding(horizontal = 16.dp, …))` 等用了 `.dp` 可能缺 `androidx.compose.ui.unit.dp`。
- 复核结果：HEAD 该文件 `:34` 已存在 `import androidx.compose.ui.unit.dp`，未缺失；本单保留不动。grep 回证见 1.5 的 `:34` 行。

### 1.5 `InkContentEditFooter.kt` — 漏 `dp` import

- 报错：`:50:45`、`:50:62`、`:53:61`、`:65:34`、`:66:61`、`:92:35` `Unresolved reference 'dp'`（共 6 处 `.dp` 用法）。
- 根因：拆分时 `dp` 未随迁；基线 `InkHalfSheet.kt:51` 包名 `androidx.compose.ui.unit.dp` 可证。
- 改动：`:20` 补 `import androidx.compose.ui.unit.dp`；6 处用法不变。
- grep 回证：

```
$ grep -n "unit.dp" app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditFooter.kt
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt:34:import androidx.compose.ui.unit.dp 〔W-451 回源有效〕
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditFooter.kt:20:import androidx.compose.ui.unit.dp 〔W-451 回源有效〕
```

### 1.6 `InkContentEditRules.kt` — public 函数外泄 internal 参数类型

- 报错：`:82:9` `'public' function exposes its 'internal' parameter type 'ContentEditProjection'`。
- 根因：`fun projectEdit(projection: ContentEditProjection, …)` 是 public，而 `ContentEditProjection` 是 `ui/book/read/ContentEditProjection.kt:6` 的 `internal class`。按任务书口径：降函数可见性为 `internal`，**不**反过来把类型提 public（那会把旧页内部类型外泄成新 API）。
- 调用方复核：全仓 `projectEdit` 唯一非注释调用点是同模块 `InkContentEditEditor.kt:104`；`ContentEditProjection` 另一处使用在同模块旧页 `ContentEditDialog.kt`（private 字段），无跨模块 / Java 调用，降 internal 不破坏外接。
- 改动：`:83` `fun projectEdit(` → `internal fun projectEdit(`，并补注释说明为什么降可见性。
- grep 回证：

```
$ grep -n "internal fun projectEdit\|internal class ContentEditProjection" \
    app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditRules.kt \
    app/src/main/java/io/legado/app/ui/book/read/ContentEditProjection.kt
app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditRules.kt:83:    internal fun projectEdit(
app/src/main/java/io/legado/app/ui/book/read/ContentEditProjection.kt:6:internal class ContentEditProjection(val raw: String) {
```

## 2. 穷举核对：4 个拆分文件未解析符号一次扫齐

按任务书「穷举不逐点试」，把同批拆出的 4 个文件（Sheet / Inputs / Editor / Footer）每个 `androidx.compose.*` / `androidx.compose.ui.*` 用法与已编译基线 `InkBottomSheet.kt` / `InkHalfSheet.kt` 逐个对包：

- `InkContentEditSheet.kt`：import 齐备（`Animatable`、`Canvas`、`background`、`border`、layout 簇、`rememberScrollState`、`RoundedCornerShape`、`BasicText`、`Composable`/`LaunchedEffect`/`remember`、`Alignment`、`blur`/`clip`、`Offset`/`Size`/`Color`、`LocalDensity`/`LocalContext`、`TextStyle`/`FontFamily`、`IntOffset`/`dp`/`sp`、`Dialog`/`DialogProperties`）；`weight` 属 `ColumnScope`/`RowScope` 扩展，无需 import。无缺包。
- `InkContentEditInputs.kt`：import 齐备（`BasicTextField`、`alpha`、`clip`、`FontWeight`、`delay` 等）；`weight` 同属行作用域扩展。无缺包。
- `InkContentEditEditor.kt`：见 §1.1–1.4，修毕。
- `InkContentEditFooter.kt`：见 §1.5，修毕。

静态语法预检（5 文件全跑）：

```
$ python3 data_scripts/kotlin-syntax-lint.py \
    app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditEditor.kt \
    app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditFooter.kt \
    app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditRules.kt \
    app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditSheet.kt \
    app/src/main/java/io/legado/app/ui/book/read/ink/InkContentEditInputs.kt
KOTLIN-LINT: PASS（5 个文件）
```

## 3. 二阶 / 三阶影响

- 直接效果：3 文件的 unresolved reference、私有成员访问、可见性外泄三类编译阻断消除；选区保留、高亮标注、进度条拖拽 / 点按、纯文本映射回填的业务口径一律未改。
- 连带影响：
  - `PositionBar` 的协程作用域随组合生命周期走，面板关闭时作用域取消，未完成的 `scrollTo` 一并取消，不会落到已解散的状态上。
  - `dispatchRawDelta` 替代手写 `coerceIn`：夹取职责回收进 `ScrollState` 公开实现，行为等价；拖拽回调仍是非挂起 lambda，无需在回调内开协程。
  - `projectEdit` 降 internal：同模块调用不受影响；若未来有模块外调用方，编译期会直接报可见性错（比外泄内部类型更安全）。
- 对用户行为 / 复杂度 / 维护成本：无新增交互路径；仅多一个 `rememberCoroutineScope` 局部作用域与两处公开滚动 API 替换，注释已写明为何不能直写 `value`，下任接手不需要再翻 aar 验证一次。

## 4. 反例与边界（假设本单判断有误时的兜底推演）

- `maxValue == 0`（内容不足一屏）：点按 / 拖拽入口均有 `if (scrollState.maxValue > 0)` 守卫；进度比例 `fraction` 取 `0f`，不发起任何滚动调用。
- 点按位置越界：`f` 经 `coerceIn(0f, 1f)`，`target` 落在 `0..maxValue` 内。
- 拖拽大增量 / 快速甩动：`dispatchRawDelta` 内部夹取到 `0..maxValue`，不会滚出负值或越界上限。
- 面板在滚动协程在途时关闭：作用域随组合取消，`scrollTo` 中断，无泄漏、无崩溃。
- 纯文本映射失败：`projectEdit` 返回 null 的兜底口径（整段回写）沿用原状，本单未触碰。
- 若 CI 仍报 `dispatchRawDelta` / `scrollTo` 签名不符：备选公开入口为 `scrollBy(Int):Int`（同样 suspend，作用域内调用）；本单未采用，留作 CI 反馈后的最小替换位。

## 5. 认知成本

- 未新增抽象层、未改命名；唯一新局部概念是「`ScrollState.value` 只读、写入走 `scrollTo` / `dispatchRawDelta`」，已在代码注释与本报告 §1.3 写明适用条件。
- 接手人读 `PositionBar` 即可看出：点按走协程定位、拖拽走原始增量分发，不需要额外上下文。

## 6. 未验证项（显式声明，不以静态结论冒充编译通过）

- 沙箱无 Android SDK，本单**未本地编译、未跑单测、未上真机**；编译绿红与运行行为以云端 CI 为准。
- `dispatchRawDelta` 的夹取行为依据依赖 aar 方法签名与公开 API 口径推断，未在真机滚动序列上逐帧量过；属待 CI / 后续真机观察项。
- 4 个拆分文件其余符号的解析完整性，经基线包名对照 + 语法 lint 双重核对，但最终 unresolved 清单仍以 CI `compileAppDebugKotlin` 输出为准。

## 7. 交接（发现但未改的跨模块项）

- 工作区存在本单文件域之外的在飞改动（其他 docs 回执补录、`kotlin-syntax-lint.py` S5、`import-pack-map.json`、交接快照等），按文件域独占原则**未读未改未提交**，留对应单子收口。
- 未发现需要移交其他模块的新缺陷；本单报错全部收口在上述 3 文件内。

## 守则回执

- 指纹：docs/机制-总控必读.md=9ec91a4f8331；docs/机制-子代理必读.md=2764066b816e；docs/机制-验收代理必读.md=ef27bd6ac2b4；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「编译只在云端 CI 发生」（docs/机制-子代理必读.md:12）
- 逐字引用 2：「证明代码真的写入、证明旧符号真的清零」（docs/机制-子代理必读.md:18）
- 逐字引用 3：「改动必须真出现在文件里，未生效不许提交」（docs/机制-总控必读.md:50）
- 适用条款：
  - R57 一条缺陷一单：只修任务书点名的编译红，未顺手优化 / 扩范围（子代理必读 §0）。
  - 禁本地编译 / 禁 git 写操作：未跑 gradle/kotlinc，未 add/commit/push（子代理必读 §0）。
  - grep 回证：每处改动均贴 `grep -n` 真实输出，旧错包名 / 私有写值零残留（子代理必读 §1 第 2 条；总控必读 §5）。
  - Kotlin 语法预检必跑：5 个拆分文件 `kotlin-syntax-lint.py` 全 PASS（子代理必读 §7 第 1 条）。
  - 未验证项显式声明：编译 / 单测 / 真机均标未验，等 CI（验收代理必读 §8 第 5 类陷阱）。
  - 先实证再处置：`ScrollState` 可见性与签名以解包依赖 aar 为准，未凭记忆写包名 / API。
