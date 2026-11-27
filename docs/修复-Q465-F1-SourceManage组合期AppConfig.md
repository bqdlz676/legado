锚点卡：docs/锚点/Q465-F1-SourceManage.md

# 修复 · F1 书源管理组合期直读 AppConfig（Q-461 登记 F-1，Paparazzi 同形态风险）

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`，HEAD `60545a9`；缺陷由 Q-461 复核件 `docs/锚点/Q461-B29book-source.md` §五 F-1 登记，落点 `app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt:131`。
- 文件域（白名单，仅 `app/src/main/java/io/legado/app/ui/book/source/manage/` 下三个 Kotlin 文件）：`SourceManageSheets.kt`、`SourceManageState.kt`、`SourceManageViewModel.kt`。`ui/book/source/` 下 `debug/`、`edit/` 子域与 `SourceSheetExitController.kt`、`SourceManageRow.kt`、`BookSourceGroupFilter.kt` 本单零改动；bookshelf 已验证实现、config / autoTask / dict / replace 四域一律未碰。
- 红线遵守：沙箱无 gradle / JDK / SDK，未跑 `./gradlew`、未跑单测、未编译；未做任何 git add / commit / push / revert / checkout；静态检查仅用现成 `data_scripts/kotlin-syntax-lint.py`。
- 结论：**静态 PASS**。组合期直读点已上提 VM-state，Compose 屏 `AppConfig` 命中数为 0；编译、单测、截图帧表现以云端 CI 及上机为准（见「未验证项」）。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=c8fb35b71549；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：「B2 改动后先 grep 确认代码真写入，未生效不许编译更不许报完成。」（docs/机制-总控必读.md:278）
- 逐字引用 2：「C3 规格未覆盖的边界自行判断兜底，不降级、不砍、不允许空白态或崩溃。」（docs/机制-总控必读.md:355）
- 逐字引用 3：「不许 add/commit/push/revert/checkout——提交由总控统一做。」（docs/机制-子代理必读.md:11）
- 适用条款：B2 改动后 grep 回证代码真写入（本单 §5 列命令与命中行号）；C3 偏好读取路径在截图环境无 Application 时不留崩溃口，状态镜像给默认值兜底；子代理禁 git 写操作（本单未 add/commit/push）；禁本地编译（未跑 gradle / kotlinc / assemble，`docs/机制-子代理必读.md:12`）；文件域白名单内改动，不碰他人在飞文件；每条改动处写简短注释解释为什么 / 适用条件。

## 一、缺陷回源（Q-461 F-1）

- Q-461 锚点卡 `docs/锚点/Q461-B29book-source.md` §五 F-1 登记：`MGT/SourceManageSheets.kt:131` Compose 组合期直读 `AppConfig.blockSourceNavigation`，getter 走 `appCtx`（`app/src/main/java/io/legado/app/help/config/AppConfig.kt:47-49`）；建议补入 `SourceManageState` 由 VM 读入透传，对照同屏 `showCheckStatus` / `showBookshelfCount` 既有口径。本单即按该建议收口。
- 旧代码落点（修前）：`SourceManageSheets.kt:131` 在 `@Composable private fun MainOptionsSheet(...)` 函数体内，`SheetToggle(...)` 的 `checked` 实参直接写 `io.legado.app.help.config.AppConfig.blockSourceNavigation`；实参在组合期求值，等于组合期触发 `AppConfig` 类初始化。
- 同屏既有正确口径：`SourceManageSheets.kt:121` `checked = state.showCheckStatus`、`:126` `checked = state.showBookshelfCount`，两个开关的值都来自 VM 流入的 `state`，本单把第三个开关对齐到同一口径。

## 二、全量命中审计与定级（修前基线）

审计命令：`grep -rn "AppConfig" app/src/main/java/io/legado/app/ui/book/source/`（递归覆盖 `manage/`、`edit/`、`debug/` 全部子域）。修前目录文本命中共 7 处，逐条定级如下（不用表格，逐条陈述）：

1. `SourceManageViewModel.kt:10` — `import io.legado.app.help.config.AppConfig`，纯导入语句，不是读取；所在文件为 VM（宿主侧数据主控），非组合期。**安全，保留**。
2. `SourceManageViewModel.kt:68` — `init {}` 块内读 `AppConfig.showSourceCheckStatus`；VM 由宿主 Activity `viewModels()` 在 `onCreate` 构造（`BookSourceActivity.kt:52`），init 跑在宿主侧、真实 Application 已就绪，不在 Compose 组合期。**安全，保留**。
3. `SourceManageViewModel.kt:69` — `init {}` 块内读 `AppConfig.showSourceBookshelfCount`，定级同第 2 条。**安全，保留**。
4. `SourceManageViewModel.kt:219` — `toggleShowCheckStatus()` 用户点击事件回调内写偏好；事件路径由 UI 点击触发，非组合期求值。**安全，保留**。
5. `SourceManageViewModel.kt:230` — `toggleShowBookshelfCount()` 事件回调内写偏好，定级同第 4 条。**安全，保留**。
6. `SourceManageViewModel.kt:235`（旧行号）— `toggleBlockSourceNavigation()` 事件回调内读+写 `AppConfig.blockSourceNavigation`；定级同第 4 条，事件路径非组合期。**安全，保留位置，改取反口径**（见 §3）。
7. `SourceManageSheets.kt:131` — `@Composable MainOptionsSheet` 体内组合期直读 `AppConfig.blockSourceNavigation`，实参在组合期求值即触发 `AppConfig` 类初始化。**真缺陷，唯一一处组合期直读，本单修**。

补充排查（同家族风险，非 AppConfig 直引）：`edit/`、`debug/` 子域 Compose 屏内 grep `SourceConfig` / `LocalConfig` / `PreferKey` / `getPrefBoolean` 空命中；`SourceManageRow.kt`、`BookSourceGroupFilter.kt`、`SourceSheetExitController.kt` 内 grep `Config` / `appCtx` / `ACache` 空命中。本域除上述 7 处外无第二处组合期配置直读。

## 三、修法：偏好值上提 VM-state，Compose 屏零 AppConfig 依赖

### 3.1 状态模型补字段（`SourceManageState.kt`）

- `SourceManageState.kt:99` 新增 `val blockSourceNavigation: Boolean = false,`；默认值 `false` 与偏好默认值对齐——`AppConfig.kt:48` `getPrefBoolean(PreferKey.blockSourceNavigation, false)`，默认语义等价，不改 UI 行为。
- `SourceManageState.kt:74` KDoc 补参数说明；`:98` 行注释解释口径：偏好开关镜像由 VM init 从 AppConfig 读入，Compose 只渲染 state，不在组合期碰 AppConfig。

### 3.2 VM 侧读入与写回（`SourceManageViewModel.kt`）

- init 回填（`SourceManageViewModel.kt:70`）：`blockSourceNavigation = AppConfig.blockSourceNavigation,`，与同块 `:68` / `:69` 两个开关并列；读取发生在 VM 构造期，宿主侧 Application 已就绪。
- 取反回调（`SourceManageViewModel.kt:234-240`）改为以 state 镜像为准：

```
fun toggleBlockSourceNavigation() {
    // 以 state 镜像为准取反再落偏好：组合期不回读 AppConfig，口径与另两个开关一致。
    val next = !_uiState.value.blockSourceNavigation
    AppConfig.blockSourceNavigation = next
    _uiState.update { it.copy(blockSourceNavigation = next) }
}
```

- 语义核对：旧实现 `AppConfig.blockSourceNavigation = !AppConfig.blockSourceNavigation` 直接从偏好读反再写；新实现先取 state 镜像的反，再落偏好并回写镜像。全仓 grep 确认该偏好除本 VM 外无第二处写入方（读方 `JsExtensions.kt:1300`、`SourceNavigationBridge.kt:15` 均为运行期业务 / 桥路径，不写），镜像与偏好始终相等，取反语义与旧实现等价。

### 3.3 Compose 屏改读 state（`SourceManageSheets.kt`）

- `SourceManageSheets.kt:131` 改为 `checked = state.blockSourceNavigation,`；`onToggle = { vm.toggleBlockSourceNavigation() }` 不变。
- 该文件不再出现 `AppConfig` / `help.config` / `appCtx` 任何引用（回证见 §5.2）；没有用 `remember { }` 内补读偏好绕过去，值只来自 `state`。

### 3.4 完整读值链路（宿主 / VM → Compose）

1. 宿主 `BookSourceActivity.kt:79` `val state by vm.uiState.collectAsState()`；`:80` 调 `SourceManageScreen(state = state, vm = vm, actions = this@BookSourceActivity)`。
2. `SourceManageScreen.kt:138` `ManageSheets(state = state, vm = vm, actions = actions)` 透传 state。
3. `SourceManageSheets.kt:55` `MainOptionsSheet(state, vm, actions)` 分发到主选项 Sheet；`:131` 渲染 `state.blockSourceNavigation`。
4. 点击开关走 `vm.toggleBlockSourceNavigation()`（`SourceManageViewModel.kt:235`），VM 落偏好并更新镜像，state 流回驱动重组。

## 四、与书架四帧修复的同源因果链，以及这次为什么不会再崩

### 4.1 崩因链条（两案同一形态）

- `AppConfig` 是 Kotlin `object` 单例（`AppConfig.kt:32`）；访问它的任何成员都会先触发类初始化，其 getter 内解引用 splitties `appCtx`（`AppConfig.kt:28` import，`:42` / `:45` / `:48` 各 getter）。
- `appCtx` 由真实 `Application.onCreate` 阶段注册；Paparazzi 截图环境不创建真实 Application，`appCtx` 为空。组合期一旦触达 `AppConfig` 成员，空解引用抛 NPE，被包成 `ExceptionInInitializerError`，当前组合帧失败。
- 书架先例（提交 `4738fb4`）：`BookshelfScreen` 组合期读 `AppConfig.shelfParallaxEnabled`，同一形态炸掉四个书架截图帧；当时修法是偏好读写上移宿主 Fragment，屏改 `parallaxEnabled: Boolean = true` 形参 + `onPersistParallax` 回调，屏内零 AppConfig 依赖（`BookshelfScreen.kt:206-207`、`InkShelfFragment.kt:95-96`）。
- 本案 `SourceManageSheets.kt:131` 与先例逐点同形：都是 Compose 组合期实参直读 `AppConfig` 布尔偏好；截图环境无 Application → 同一 NPE / `ExceptionInInitializerError` 路径。Q-461 锚点卡 §三口径锁亦写明「Compose 侧禁引入依赖 Application 初始化的配置读取（Paparazzi 截图环境无 Application）」。

### 4.2 这次不会再崩的理由

- 组合路径不再命名 `AppConfig`：修后 `SourceManageSheets.kt` 内 `AppConfig` 命中为 0（§5.2），重组时不会触发 `AppConfig` 类初始化；即便截图环境 `appCtx` 为空，组合帧也碰不到那条空解引用路径。
- 唯一保留的 `AppConfig` 触碰点在 VM：init 构造读入、点击回调写回。VM 由宿主在真实 Activity 生命周期内创建 / 触发，Application 已就绪；这与同屏已验证的 `showCheckStatus` / `showBookshelfCount` 两个开关是同一处位置、同一口径，不是新造的风险口。
- 兜底默认值：state 字段默认 `false` 与偏好默认值一致；即便 VM 尚未完成 init 回填的极短窗口，渲染取 `false` 也与「偏好未设置」的旧表现一致，不出现空白态、不抛崩溃（C3 兜底口径）。
- 二阶排查：本域 `edit/`、`debug/` Compose 屏与其余 manage 文件未发现第二处同类直读（§二补充排查），风险口在本文件域内已收口，不留第二个未修帧。

## 五、grep 回证（修前 / 修后计数与行号）

### 5.1 修前基线（改动前快照取数）

- 命令：`grep -rn "AppConfig" app/src/main/java/io/legado/app/ui/book/source/`
- `SourceManageSheets.kt`：1 处命中（`:131` 全限定名组合期直读）。
- `SourceManageViewModel.kt`：6 处命中（`:10` import；`:68` / `:69` init 读；`:219` / `:230` / `:235` 事件回调读写）。
- 目录合计 7 处文本命中；真组合期直读集合 = { `SourceManageSheets.kt:131` }，仅 1 处。

### 5.2 修后命中（工作树当前快照）

- 命令：`grep -rn "AppConfig" app/src/main/java/io/legado/app/ui/book/source/`
- `SourceManageSheets.kt`：**0 处命中**；另跑 `grep -n "help.config\|appCtx" SourceManageSheets.kt` 空命中（exit=1）。
- `SourceManageViewModel.kt`：7 处代码命中——`:10` import；`:68` / `:69` / `:70` init 读；`:220` / `:231` / `:238` 事件回调写。全部位于 VM，非组合期。
- `SourceManageState.kt`：2 处提及均为注释 / KDoc 文本（`:74` 参数说明、`:98` 口径注释），无代码读取。

### 5.3 新字段 / 新链路命中行号

命令：`grep -n "blockSourceNavigation" app/src/main/java/io/legado/app/ui/book/source/manage/`

- `SourceManageState.kt:74` — KDoc 参数说明。
- `SourceManageState.kt:99` — `val blockSourceNavigation: Boolean = false,` 状态字段声明。
- `SourceManageViewModel.kt:70` — init 从 `AppConfig.blockSourceNavigation` 读入镜像。
- `SourceManageViewModel.kt:237` — `val next = !_uiState.value.blockSourceNavigation` 以镜像取反。
- `SourceManageViewModel.kt:238` — `AppConfig.blockSourceNavigation = next` 落偏好。
- `SourceManageViewModel.kt:239` — `_uiState.update { it.copy(blockSourceNavigation = next) }` 回写镜像。
- `SourceManageSheets.kt:131` — `checked = state.blockSourceNavigation,` 组合渲染点。

### 5.4 调用点传参行号

- `BookSourceActivity.kt:79` — `val state by vm.uiState.collectAsState()`。
- `BookSourceActivity.kt:80` — `SourceManageScreen(state = state, vm = vm, actions = this@BookSourceActivity)`。
- `SourceManageScreen.kt:138` — `ManageSheets(state = state, vm = vm, actions = actions)`。
- `SourceManageSheets.kt:55` — `MainOptionsSheet(state, vm, actions)` 分发。
- 全仓 `toggleBlockSourceNavigation` 调用点仅 `SourceManageSheets.kt:132` 一处（`onToggle` 回调），无遗漏接线。

### 5.5 静态语法 lint

命令：`python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt`

结果：`KOTLIN-LINT: PASS（3 个文件）`，退出码 0（括号配平、条件尾逗号、符号出处等硬错无命中）。

## 六、自查反例 / 改坏推演

- 反例一：若在 `MainOptionsSheet` 里用 `remember { AppConfig.blockSourceNavigation }` 包一层再读。`remember` 块仍在组合期执行，照样触发 `AppConfig` 类初始化，截图环境同样崩；本单禁止该绕法，值只来自 VM 流入的 `state`。
- 反例二：若把偏好读取留在 Sheet、只把写回挪到 VM。组合期实参仍直读 `AppConfig`，崩因未除；本单读、写两侧都收口到 VM，屏内零引用。
- 反例三：若 state 字段默认值取 `true` 或不写默认。与偏好默认 `false`（`AppConfig.kt:48`）语义不等价，首次进入界面开关显示会跳变；现默认 `false`，与旧偏好口径逐字对齐。
- 反例四：若取反仍从偏好读（`next = !AppConfig.blockSourceNavigation`）再写。等于组合路径之外又留了一次偏好回读，且与 state 镜像可能错位；现以镜像为准取反，单一事实源在 state，写回双向同步。
- 反例五（二阶）：若顺手把同屏 `showCheckStatus` / `showBookshelfCount` 也改成形参直传宿主。两个开关已是验证过的 VM-state 口径，改动属扩战场、增认知成本；本单只把第三个开关对齐到既有口径，不动前两个。
- 反例六（三阶）：若 VM init 读偏好失败时抛异常中断构造。init 回填走 `_uiState.update`，字段自带默认 `false`；即便读取异常，state 仍有合法初始值，不把异常抛到组合层。现有 `bindSources` 等链路 `.catch { AppLogPut(...) }` 兜底口径不变。
- 反例七：若改开关文案、排序或 Sheet 开合时机。本单只换值来源，`SheetToggle` 的 label、`onToggle`、其余行项与离场控制器全部未动，UI 行为语义零变更。

## 七、二阶 / 三阶影响推演

- 状态面：`SourceManageUiState` 新增带默认值字段，属向后兼容扩字段；既有 JVM 测试未直接构造该 state（`app/src/test/` grep 无 `SourceManageUiState` / `blockSourceNavigation` 命中），不受字段新增影响。
- 数据流面：`uiState` Flow 形态不变，`collectAsState` 订阅链不变；开关切换后镜像与偏好同步更新，重组渲染与旧实现一致，不引入新线程、新持久化键、新依赖。
- 消费面：`blockSourceNavigation` 的运行期读方 `SourceNavigationBridge.kt:15`、`JsExtensions.kt:1300` 直接读 `AppConfig` 偏好，不经本屏；本单改写偏好的值与旧实现一致，桥与 JS 侧读到的结果不变。
- 历史包袱：旧直读串已随本单删除，不留旧直读兼容层；VM 内 `AppConfig` import 保留（init 与回调仍需触碰偏好），与前两个开关文件级口径一致。
- 边界未扩：bookshelf 先例实现、config / autoTask / dict / replace 四域、components 契约红线文件均未触碰；Q-461 锚点卡 §五 F-2（宿主级闸未接）、F-3（部分动作行不自动关层）、F-4（控制器测试待 CI）、F-5（remember 持有不跨重建）、F-6（四份包装并存）为既有留观项，本单不代收口，原样留待各自派单。

## 八、新发现问题

本单新发现共 2 条，逐条带处置状态：

- 一条：`SourceManageSheets.kt:360` `remember { mutableStateOf(CheckSource.keyword) }` 与 `:377` `keyword.ifBlank { CheckSource.keyword }` 在组合期读 `CheckSource.keyword`。核查 `app/src/main/java/io/legado/app/model/CheckSource.kt:20` 该值是内存 `var keyword = "我的"`，不碰 `appCtx` / 偏好，当前无截图崩溃风险；但结构上同属「组合期读静态单例」家族。**留观**（不属本单 AppConfig 文件域，未改动；若后续 `keyword` 接入持久化或 Context，需按本单同口径上提 VM-state）。
- 一条：`app/src/main/java/io/legado/app/help/source/SourceNavigationBridge.kt:15` 与 `app/src/main/java/io/legado/app/help/JsExtensions.kt:1300` 运行期读 `AppConfig.blockSourceNavigation`。两处属 help 域业务 / 桥路径，调用时有真实 `context`，非截图组合期；但与本开关共享同一偏好键，后续若做全局 AppConfig 组合期巡检需一并登记。**已登记待裁**（本单文件域不含 help 域，不动手；交由对应 help 域派单评估）。

## 九、未验证项（显式）

- 未编译、未跑单测：沙箱无 gradle / JDK / SDK；Kotlin 类型贯通、state 字段在 Flow 拷贝中的传递、回调签名匹配均为静态推断，待云端 CI `Verify` 编译与单测终验。
- 截图帧回归：Paparazzi 书源管理页帧在无 Application 环境下的组合表现未上机 / 上帧验证；本单静态结论为组合路径已不触达 `AppConfig`，帧级绿红以 CI Screenshot 任务为准。
- 真机交互：开关取反后偏好持久化、回后台再返回的镜像一致性未上机观察；§3.2 同步推演基于「全仓仅此一处写入方」的 grep 事实。

## 十、变更清单（绝对路径）

生产（3 个，均在白名单内）：

- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageState.kt`
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageViewModel.kt`
- `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/book/source/manage/SourceManageSheets.kt`

未触碰：`app/src/main/java/io/legado/app/ui/book/source/` 下 `edit/`、`debug/` 子域及其余 manage 文件；`app/src/main/java/io/legado/app/ui/main/bookshelf/` 全部已验证实现；`ui/config/`、`ui/autoTask/`、`ui/dict/`、`ui/replace/` 四域；`app/src/test/**`；`app/src/main/assets/**`；`data_scripts/**`；既有 docs 报告。未做 git 写操作。

## 十一、结论

**静态 PASS**：`SourceManageSheets.kt:131` 组合期直读 `AppConfig.blockSourceNavigation` 已收口为 VM-state 镜像——VM init 读入、点击回调写回并同步镜像，Compose 屏 `AppConfig` 命中为 0；默认值与偏好默认 `false` 等价，开关取反语义不变，UI 行为零改动。因果形态与书架四帧先例（`4738fb4`）同源，修法同口径：偏好读写留在宿主 / VM 侧，组合路径不触达依赖 Application 初始化的配置单例。编译、单测与截图帧表现以云端 CI 为准。
