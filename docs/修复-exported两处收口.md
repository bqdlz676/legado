# 修复 · exported 两处收口（Q-241，接 Q-239 判定）

> 边界：本单只改 `app/src/main/AndroidManifest.xml` 两处 `exported`，新增一条 manifest 契约测试；`ReaderProvider` 一律不动（signature 权限另案）。未 git 写、未本地编译，仅跑 `kotlin-syntax-lint.py` 与 grep 回证。
> 锚点卡：docs/锚点/Q241-exported收口.md。

## 一、任务与产物

- 改动 1：`AndroidManifest.xml:139` `.ui.main.MainActivity` `exported` `true → false`。
- 改动 2：`AndroidManifest.xml:186` `.ui.book.read.ReadBookActivity` `exported` `true → false`。
- 新增测试：`app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt`，XML 解析 manifest，断言两处内部组件为 false、13 处外部入口仍为 true、ReaderProvider 保持 true。
- 未改动：ReaderProvider 及其它 13 处导出组件、任何业务 Kotlin 代码。

## 二、manifest diff（仅两行）

```diff
@@ :136 MainActivity
-            android:exported="true"
+            android:exported="false"
@@ :184 ReadBookActivity
-            android:exported="true"
+            android:exported="false"
```

grep 回证（改后计数）：

```
$ grep -c 'android:exported="true"' app/src/main/AndroidManifest.xml
14
$ grep -c 'android:exported="false"' app/src/main/AndroidManifest.xml
14
$ grep -n 'android:exported' app/src/main/AndroidManifest.xml | sed -n '8p;15p'
139:            android:exported="false"
186:            android:exported="false"
```

改动前 true 16 处；改后 true 14 处（13 处外部入口 + ReaderProvider），false 含本次两处。

## 三、ReadBookActivity 三条排查结论（改 false 前取证）

1. **全仓 `ReadBookActivity::class` 外部拉起排查**：`app/src/main` 与 `modules/` 生产源码 **0 处** `ReadBookActivity::class` 显式拉起；全部 22 处命中位于 `app/src/androidTest/`（如 `SharedFileImportTest.kt:850`、`ContentReversalUiTest.kt:202` 等 Instrumentation 测试件）。main 源中的引用均为类型判断（`activity is ReadBookActivity`）、Companion 常量 `RESULT_DELETED` 或字典预传 `this@ReadBookActivity`，不构成拉起。
2. **intent-filter 排查**：`AndroidManifest.xml:183-187` 该 activity 自闭合声明，块内无 `<intent-filter>` 子节点；不响应任何 action/category/scheme，不存在深链或系统匹配入口。
3. **系统唤起排查**：该组件不是 `<receiver>`、不是 `AppWidgetProvider`、不是 `TileService`、不监听 `BOOT_COMPLETED`；`InkReadActivity.kt:83` 的匿名 `BroadcastReceiver` 是代码内 register/unregister 的内存接收器，与本组件无关。`WelcomeActivity.kt:20` 注释载明「最后阅读直达分支（旧版 lastReadBook → ReadBookActivity）属共享入口接线，留主控 merge 时接回」——当前未接线，主流程打开书走 `ContextExtensions.kt:74` `InkReadActivity::class.java`。

## 四、契约测试

- 文件：`app/src/test/java/io/legado/app/security/ManifestExportedContractTest.kt`。
- 解析方式：`DocumentBuilderFactory` 解析 `src/main/AndroidManifest.xml`，遍历 `activity` / `service` / `receiver` / `provider`，按 `android:name` 收集 `android:exported`。
- 用例：
  - `internal-only activities are not exported`：断言 `.ui.main.MainActivity`、`.ui.book.read.ReadBookActivity` 均为 `false`。
  - `external entry points stay exported`：13 处外部入口逐一点名断言 `true`——WelcomeActivity、Launcher1~6、SharedReceiverActivity、OnLineImportActivity、FileAssociationActivity、WebTileService、AutoTaskJobService、MediaButtonReceiver。
  - `reader provider untouched pending signature permission ticket`：断言 `.api.ReaderProvider` 仍为 `true`（本单不动）。
- Python 等价解析预检（沙箱无 JDK，替代编译期核验）：两处 false、13 处 true、Provider true，取值与断言清单一致。

## 五、二阶三阶影响（外部直拉类名会不会断）

- **MainActivity**：四处拉起方均为同进程显式 Intent——`WelcomeActivity.kt:48`、`ShortCuts.kt:21`、`SharedReceiverActivity.kt:55`、`ShelfSearchHandoff.kt:75`；同 uid 内部启动不受 `exported=false` 影响。二阶：外部 App 若按类名显式直拉 MainActivity，将被系统抛 `SecurityException` 拦截；本仓代码内未见此外部路径。三阶：桌面快捷方式由应用内 `ShortCuts` 构建 Intent，不走外部组件解析，不受影响。
- **ReadBookActivity**：二阶：任何残留外部路由 / 第三方插件按类名直拉该 activity 会断开；排查未发现此类路径。三阶：androidTest 的 `ActivityScenario.launch(Intent(context, ReadBookActivity::class.java))` 运行在 Instrumentation 同 uid 上下文，通常可启动非导出组件，但该行为需在 CI / 真机复核（见 §六 问题 1）。
- **不受影响的外部能力**：文本分享（SharedReceiverActivity）、`legado://`/`yuedu://` 深链（OnLineImportActivity）、文件打开与多文件分享（FileAssociationActivity）、快捷磁贴、定时任务、媒体按键均仍导出，未改动。

## 六、新发现问题

1. androidTest 中 22 处 `ActivityScenario.launch(Intent(context, ReadBookActivity::class.java))` 依赖测试上下文启动该非导出组件；Instrumentation 同 uid 一般允许，但沙箱无 JDK 无法本地验证 —— **待裁**：CI 跑完读 androidTest 报告，若出现 `SecurityException` 再评估测试侧改为内部壳或保留导出例外。
2. `WelcomeActivity.kt:20` 注释的旧版「lastReadBook → ReadBookActivity」直达分支尚未接线；后续若接回，须先判定走内部显式 Intent（保持 false）还是外部深链（另开 filter 并重新评估导出） —— **已入队观察**。
3. `ReaderProvider` 仍无调用方鉴权（Q-239 问题 3），本单按判定不动 —— **已入队**：另案评估 signature 级 permission 与存量互通生态兼容。
4. 契约测试只读 `src/main/AndroidManifest.xml` 源文件，未覆盖 manifest merger 合并后的 build 中间产物（其它 module manifest 覆盖 / `tools:replace` 后的最终导出态） —— **待裁**：是否在 CI 产物校验环节补 merged manifest 断言。

## 七、未验证项

- 未跑本地编译、未跑单元测试与 androidTest；测试仅过 `kotlin-syntax-lint.py` 静态检查，红绿以云端 CI 为准。
- 非导出组件在真机上的内部拉起（书架点开书、分享转发进主界面、快捷方式）未做运行期回归。
- 第三方 App 按类名直拉的存量调用面未外部盘点。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=e9560a5c333b；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`："不许 add/commit/push/revert/checkout——提交由总控统一做。"
2. `docs/机制-子代理必读.md:12`："不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。"
3. `docs/机制-子代理必读.md:18`："每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。"
- 适用条款：
  - 一条缺陷一单：本单只收口两处 exported 并补契约测试；ReaderProvider 与其它导出组件未顺手改。
  - 禁 git 写操作：未执行 add/commit/push/revert/checkout（子代理必读 :11）。
  - 禁本地编译：未跑 gradle/kotlinc/assemble；仅 python3 跑 lint 与门禁自检（子代理必读 :12）。
  - grep 回证：两处 false 行号、13 处入口清单、androidTest 命中数均以 grep / Read 现取现核（子代理必读 :18）。
  - 未验证项声明：CI 红绿、真机回归、第三方调用面已在第七节显式列出（子代理必读 :20）。

## 八、门禁自检输出

```
（写完回填真实末行）
```
