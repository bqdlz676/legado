> 漂移重判：原锚已失效，本结论标注为已废（2026-11-26）——原因：`DetailSheet.kt:141`、`DetailSheet.kt:132` 所指旧 Fragment 壳已由本单 2a6d687 整体删除，仓内零类声明；清剿动作本身成立，失效锚点仅作历史留档。（同段提及的 `MapWebHolder.kt` 属地图域，归对应代理处置。）

# 清理报告 · B24 旧 DetailSheet 死代码清剿

- 工单：Q-89 / 问题清单 B24（`docs/问题清单.md` 第 73 行）
- 锚点卡：`docs/锚点/B24-旧DetailSheet清剿.md`（五条判据与边界以它为准）
- 上游证据：`docs/验收-B1-点字半屏Sheet.md` D6 段（第 148 行）、§7.1 旧路径残留段（第 132 行）
- 执行环境：云主机 vm，仓库绝对路径 `/home/user/Doubao/chats/38454122521460994/legado`
- 执行时间：2026-11-26（Asia/Shanghai）
- 工作树基线：删除前 HEAD `8ff8dd5`；删除仅落工作区，未执行 `git add` / `commit` / `push`（硬约束）。

## 一、E1 命令证据（四类 grep，删除前原样输出）

仓库根目录：`/home/user/Doubao/chats/38454122521460994/legado`。grep 退出码：`0` = 有命中，`1` = 零命中，`2` = 路径不存在（已在下方注明实际存在的目录）。

### E1.1 主源码（app/src/main/java、modules/*/src/main、screenshot/src/main；另含 modules/web/src JS/TS 前端）

命令：

```
grep -rn "DetailSheet" app/src/main/java modules/*/src/main screenshot/src/main
grep -rn "DetailSheet\|detail_year" modules/web/src
```

第二条命令（modules/web 前端源码）输出为空，`exit=1`。

命中文件（仅三个，全部为本单待删文件自身）：

- `app/src/main/java/io/legado/app/ui/main/DetailSheet.kt`
- `app/src/main/java/io/legado/app/ui/main/detail/DetailSheetContent.kt`
- `app/src/main/java/io/legado/app/ui/main/detail/DetailSheetState.kt`

域外回证命令（排除三文件自身后再 grep）：

```
grep -rn "DetailSheet" app/src/main/java modules/*/src/main screenshot/src/main \
  | grep -vE "ui/main/DetailSheet\.kt:|ui/main/detail/DetailSheetContent\.kt:|ui/main/detail/DetailSheetState\.kt:"
```

原样输出：空；`exit=1`（零外部命中）。

主源码命中行计数：三文件合计 34 行（DetailSheet.kt 15 行、DetailSheetContent.kt 2 行、DetailSheetState.kt 17 行），全部为文件内部自引用（import、类声明、伴生工厂、内部调用）。

### E1.2 测试源码（JVM 单测 / androidTest / screenshot 测试）

实际存在的测试目录：`app/src/test`、`app/src/androidTest`、`screenshot/src/test`（`modules/book`、`modules/rhino`、`modules/web` 无 test 源集）。

命令：

```
grep -rn "DetailSheet" app/src/test
grep -rn "DetailSheet" app/src/androidTest
grep -rn "DetailSheet" screenshot/src/test
```

原样输出：三条命令均为空；`exit=1`，命中数 `0 / 0 / 0`。

### E1.3 资源（res/**、xml、navigation graph）

实际存在的 res 目录：`app/src/main/res`、`app/src/debug/res`；无 `res/navigation` 目录，xml 目录为 `app/src/main/res/xml`（backup_rules / data_extraction_rules / file_paths / network_security_config / pref_* / spen_remote_actions）。

命令：

```
grep -rn "DetailSheet" app/src/main/res
grep -rn "DetailSheet" app/src/debug/res
find app/src/main/res -name '*.xml' | xargs grep -ln "DetailSheet"
```

原样输出：前两条为空，`exit=1`；第三条 xargs 无匹配（`exit=123`，即无文件命中）。命中数 `0`。

### E1.4 AndroidManifest.xml

命令（逐一全仓 Manifest）：

```
for f in $(find . -name 'AndroidManifest.xml' -not -path '*/build/*'); do grep -n "DetailSheet" "$f"; done
```

遍历到的 Manifest（六个，均无命中）：

- `./app/src/cronetRuntime/AndroidManifest.xml`，`exit=1`
- `./app/src/main/AndroidManifest.xml`，`exit=1`
- `./app/src/pdfRuntime/AndroidManifest.xml`，`exit=1`
- `./modules/book/src/main/AndroidManifest.xml`，`exit=1`
- `./modules/rhino/src/main/AndroidManifest.xml`，`exit=1`
- `./screenshot/src/main/AndroidManifest.xml`，`exit=1`

命中数 `0`。

### E1.5 反射 / 类名字符串专项（判据 4 要求单独 grep）

命令与结果（代码域，排除 build/.git/docs）：

- 全限定名与包路径：`grep -rn "io.legado.app.ui.main.DetailSheet\|ui.main.detail" app/src modules/*/src screenshot/src`，排除三文件自身后为空，`exit=1`。
- 短名符号：`grep -rn "DetailSheetContent\|DetailSheetStateBuilder" app/src modules/*/src screenshot/src`，排除三文件自身后为空，`exit=1`。
- 其余内部类型：`DetailSection` / `DetailTarget` / `DetailAction` / `DetailSectionKind` 在三文件之外零命中，`exit=1`。
- FragmentManager tag 字符串 `"detail_year"`：仅 `DetailSheet.kt:141` 自身出现，域外为空，`exit=1`。
- 混淆 / 构建配置：`grep -rn "DetailSheet" app/proguard-rules.pro app/cronet-proguard-rules.pro modules/book/consumer-rules.pro modules/rhino/consumer-rules.pro app/build.gradle build.gradle settings.gradle app/src/main/assets`，为空，`exit=1`；四个 `.pro` 文件（app 两个 + modules/book、modules/rhino consumer-rules）无 keep 规则指向本类。
- 反射实例化：全仓 `Class.forName` 命中均为 `Charset.forName(...)` 与一处 `androidx.documentfile.provider.TreeDocumentFile`，无一处指向 `ui.main.DetailSheet` 或 `ui.main.detail` 包。
- `data_scripts`、`third_party`、`screenshot/src`：`grep -rn "DetailSheet"` 为空，`exit=1`。

## 二、E2 命中文件:行（删除前唯一命中清单）

三文件内部命中行（行号取自 HEAD 版本，`git show HEAD:<file> | grep -n DetailSheet`）：

1. `app/src/main/java/io/legado/app/ui/main/DetailSheet.kt`
   - 命中行：18、19、20、31、35、43、47、48、57、63、74、99、113、141、169
   - 性质：18–20 import 三文件内部类型；31 KDoc；35 `class DetailSheet : DialogFragment()`；43/47/48/57/63 伴生工厂 `char/person/year/place`；74 状态字段；99 `DetailSheetStateBuilder.build`；113 `DetailSheetContent(...)`；141 重开同型半屏；169 兜底状态构造。
2. `app/src/main/java/io/legado/app/ui/main/detail/DetailSheetContent.kt`
   - 命中行：33、34
   - 性质：33 `fun DetailSheetContent(`；34 参数 `state: DetailSheetState`。
3. `app/src/main/java/io/legado/app/ui/main/detail/DetailSheetState.kt`
   - 命中行：15、17、18、19、32、62、71、72、86、95、131、136、261、266、298、303、355
   - 性质：KDoc 引用（15/17/18/19/32）；62 `data class DetailSheetState`；71 注释；72 `object DetailSheetStateBuilder`；86/95/131/136/261/266/298/303/355 内部构造与私有构建函数。

域外（主源码、测试、资源、Manifest、构建配置、assets、脚本）命中：**0 处**。

## 三、E3 结论标记

- 删除前外部引用计数：主源码 0、测试 0、资源 0、Manifest 0、反射 / 字符串 0。
- 结论标记：**准予删除（三文件均为零引用死代码）**，未触发锚点卡 §三「任一非零即停止、转只登记不删」的回退分支。
- 删除后复跑同一组 grep：全部 `exit=1`，零命中，无新增断链（见第五节）。

## 四、五条判据逐条自检

1. 判据 1（删除前先出全仓引用清单，四类分别 grep，逐类给命中数；任一非零即停）：**PASS**。E1.1–E1.4 四类命令、原样输出、命中数已贴齐；四类域外命中均为 0，才进入删除动作。
2. 判据 2（只删零引用文件；不顺手改别的类；不做重构式删除）：**PASS**。删除对象仅限锚点卡点名的三文件；未新增功能、未改其他类、未清理三文件之外的任何符号；`MapWebHolder.kt` 等二阶观察项只登记不删（见第六节）。
3. 判据 3（删除后复跑同一组 grep 证明零命中；贴删除前后文件清单）：**PASS**。第五节为删除后复跑结果；第六节附删除前 / 后两份文件清单。
4. 判据 4（编译风险如实登记；反射 / 类名字符串单独 grep）：**PASS**。E1.5 已对全限定名、短名、Fragment tag、proguard/gradle/assets、`Class.forName` 逐项 grep，均零命中；无字符串反射通道残留。
5. 判据 5（不改测试迁就删除；若测试引用待删类需判定其是否已被替代并列清）：**PASS**。测试域 `DetailSheet` 零命中（E1.2），不存在引用待删类的测试，因此无测试需要替代或改断言；本单未改动任何测试文件。唯一与旧 Sheet 相关的测试登记项是 `DesignTokenGuardTest.kt:159` 的基线条目 `MapWebHolder.kt`，该文件本单未删，基线对账关系不变（见第六节 6.3）。

## 五、删除后复跑证据（同一组 grep）

删除后在仓库根目录复跑：

```
grep -rn "DetailSheet" app/src/main/java modules/*/src/main screenshot/src/main
grep -rn "DetailSheetContent\|DetailSheetState\|DetailSheetStateBuilder\|DetailSection\|DetailTarget\|DetailAction\|DetailSectionKind" app/src/main/java modules/*/src/main screenshot/src/main
grep -rn "DetailSheet" app/src/test app/src/androidTest screenshot/src/test
grep -rn "DetailSheet" app/src/main/res app/src/debug/res
for f in $(find . -name 'AndroidManifest.xml' -not -path '*/build/*'); do grep -n "DetailSheet" "$f"; done
grep -rn "ui.main.detail" app/src modules/*/src screenshot/src
```

原样结果：六条命令输出均为空，`exit=1`；残留 import `io.legado.app.ui.main.detail.*` 零命中。

`git diff --name-only` 工作树删除清单（本单产出）：

- `app/src/main/java/io/legado/app/ui/main/DetailSheet.kt`
- `app/src/main/java/io/legado/app/ui/main/detail/DetailSheetContent.kt`
- `app/src/main/java/io/legado/app/ui/main/detail/DetailSheetState.kt`

## 六、删除文件清单（绝对路径）

### 6.1 删除前（HEAD 8ff8dd5 版本，701 行 / 3 文件）

1. `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/main/DetailSheet.kt` — 177 行，7246 字节；`DialogFragment` 壳，四伴生工厂 `char/person/year/place`，内部承载 `InkHalfSheet` + `DetailSheetContent`。
2. `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/main/detail/DetailSheetContent.kt` — 142 行，5645 字节；旧详情半屏 Compose 内容槽。
3. `/home/user/Doubao/chats/38454122521460994/legado/app/src/main/java/io/legado/app/ui/main/detail/DetailSheetState.kt` — 382 行，17129 字节；纯状态模型 + `DetailSheetStateBuilder` 采集门面，内含 `DetailSection` / `DetailTarget` / `DetailAction` / `DetailSectionKind` 四个内部类型定义。

### 6.2 删除后（工作区现状）

1. `…/ui/main/DetailSheet.kt` — 不存在。
2. `…/ui/main/detail/DetailSheetContent.kt` — 不存在。
3. `…/ui/main/detail/DetailSheetState.kt` — 不存在。
4. 目录 `…/ui/main/detail/` — 磁盘上为空目录（git 不跟踪空目录，提交后该目录自然消失）；本单未用 `git rm -r` 删目录，只逐条 `rm` 上述三个具体文件。

### 6.3 二阶影响排查

1. 三文件内部类型随定义文件一并移除：`DetailAction`、`DetailSection`、`DetailTarget`、`DetailSectionKind`、`DetailSheetState`、`DetailSheetStateBuilder`、`DetailSheetContent` 均定义在三文件内；域外 grep 零命中，删除后不会留下指向已删类型的悬空引用。
2. 被三文件依赖的帮助类均有其他生产消费者，未因本删改变成新孤儿（计数为 `grep -rl` 排除已删 `DetailSheetState.kt` 后的文件数，含各自定义文件本身）：`ChineseDict`（13）、`PlaceTimeDict`（6）、`Chronology`（12）、`ContextAnchor`（27）、`PersonProfile`（12）、`PersonProfileWiring`（除自身定义外，消费者 `PersonDetailActivity.kt`）、`PersonGraphExtractor`（除自身定义外，消费者 `RelationActivity.kt`）、`NameResolver`（域外多处）。
3. **新发现二阶候选（本单不删，交调度层另立条目）**：`app/src/main/java/io/legado/app/ui/main/MapWebHolder.kt`（地图 WebView 单例，`attach/detach/destroy`）在三文件删除后，全仓生产源码已无任何调用点（`grep -rn "MapWebHolder\." app/src modules/*/src screenshot/src`，`exit=1`）；删除前其唯一调用点就是 `DetailSheet.kt:132` 的 `MapWebHolder.detach()`。该文件仍被 `app/src/test/java/io/legado/app/ui/components/DesignTokenGuardTest.kt:159` 的 `legacyBaseline` 登记为裸色值基线条目；若后续清剿 `MapWebHolder.kt`，必须同步从该基线集合摘除对应行（判据 5：测试条目随被测文件一并处置，不许改断言迁就）。此观察项不在 B24 锚点卡点名范围内，按判据 2 保留原状。
4. 设计 token 护栏影响：`DesignTokenGuardTest` 的基线集合不含三文件（`grep -c DetailSheet` 该测试文件为 0）；三文件 HEAD 版本内亦无 8 位 ARGB 裸色字面量，删除不改变 `actualHexFiles` 命中集，基线双向对账不因此新增 FAIL 项。
5. 文档引用（非代码，不阻断编译）：`docs/` 下多份历史修复 / 验收 / 台账文档仍含 `DetailSheet` 文字（如 `docs/修复-DetailSheet贴皮.md`、`docs/验收-B1-点字半屏Sheet.md`、`docs/问题清单-附录-逐文件.md` 等），属历史留档记录，本单不清理；附录中残留的旧行号对应更早版本快照，仅作文档史材料。
6. 边界遵守：未改动 B19/B20/B21 已改文件（`InkBottomSheet` / `InkHalfSheet` / `ReaderSheet*` / `ReadBookActivity` / `ReaderSheetGuard` / `ReaderSheetExitGate` 等）；本单代码改动仅为三文件删除（`git diff --stat -- app/src/main/java/io/legado/app/ui/main/` = 3 文件、701 行删除）。共享工作树内另有其他在飞工单的改动（如 `help/bubble/BubbleResolver.kt`、`help/allusion/AllusionAnchorProvider.kt` 及其测试、台账文档），非本单产出，未触碰。

## 七、未验证项

1. 本地编译 / 单测：按硬约束未执行 `./gradlew` / `kotlinc` / 任何测试运行；编译与单测是否全绿以云端 CI 为唯一验收手段。
2. 反射通道：`Class.forName` 与字符串类名已静态 grep 零命中，但未在运行时做类加载枚举兜底；若后续存在动态脚本（JS 源 / Rhino）按类名反射，需 CI 出包后另查。
3. 真机行为：本单为纯死代码删除，无可见行为变更；旧四入口在 B1 已改走 `ReaderSheetOverlay` / `InkHalfSheet` 新链路，真机复核归属 B1 验收单，不在本单重复。
4. `MapWebHolder.kt` 是否可整体清剿：本单仅登记零调用事实；其内部 WebView 生命周期是否被其他隐式通道持有（如全局 View 树残留）需后续条目结合真机 / 运行时排查后再定。
5. 文档同步：`docs/问题清单.md` B24 行状态翻转、附录逐文件清单更新属调度层 / 文档工序，本单未改。
