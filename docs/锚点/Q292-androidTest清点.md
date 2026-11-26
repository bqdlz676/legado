# 锚点卡：Q292 androidTest 失修清点

- 缺陷：`app/src/androidTest/**` 63 个 `.kt`（341 条 `@Test`）中，9 个文件存在未解析 import 或缺失布局 binding，instrumentation / `assembleDebugAndroidTest` 必红；CI 现有 Verify（仅 JVM 单测编译）与 Package（仅 `assembleDebug/assembleRelease`）都不编译 androidTest 源集，存量失修长期无人发现。
- 判据：全量 import 2356 条机检——第三方跳过 1571、通配 0、`R` 生成类跳过 38、主仓解析命中 728、布局存在 binding 4、包名不一致 0、未解析符号 11、布局缺失 binding 4；计数闭合 1571+0+38+728+4+0+11+4=2356。
- 硬错误清单（9 文件 15 条）：
  1. `help/storage/BackupOptionsTest.kt:32` `BackupConfigFragment`（待裁；5 条备份逻辑可拆留，2 条 Preference UI 待 Compose 钩子）。
  2. `ui/about/ReadRecordHistoryTest.kt:45,46` `ActivityReadRecordBinding` / `ItemReadRecordDisplayBinding`（待裁；布局 XML 缺失，Activity 已 ComposeView 化）。
  3. `ui/association/RuleSelectionShareTest.kt:24,26` `DictRuleAdapter` / `ReplaceRuleAdapter`（可修；→ `DictRuleListViewModel` / `ReplaceRuleListViewModel` + 对应 Screen）。
  4. `ui/book/source/BookSourceCheckUiTest.kt:33,42` `ItemBookSourceBinding` / `BookSourceAdapter`（可修；→ `SourceManageViewModel` + `SourceManageScreen/Row`）。
  5. `ui/book/source/SourceDragOrderUiTest.kt:19` `ItemBookSourceBinding`（待裁；RSS 旧栈存活，Book/Replace Compose 拖拽手势入口待确认）。
  6. `ui/book/source/SourceOrderMetadataUpdateTest.kt:16,17` `ReplaceRuleAdapter` / `ReplaceRuleViewModel`（可修；→ `ReplaceRuleListViewModel`，选择态 `toggleSelect/selectAll/revertSelection/clearSelection`）。
  7. `ui/code/CodeSelectionUiTest.kt:81,82` `BookSourceEditAdapter` / `BookSourceEditViewModel`（可修；→ `SourceEditScreen` / `SourceEditViewModel`；21 条仅 1 条耦合）。
  8. `ui/widget/image/CoverStylePreviewUiTest.kt:26` `CoverFontConfigFragment`（判废；新 `CoverFontSection` 为 Compose 自绘样例卡，无 `CoverImageView` 预览位与偏好遍历出口）。
  9. `ui/widget/image/CoverTitleAdaptiveUiTest.kt:36,37` `CoverConfigFragment` / `CoverFontConfigFragment`（可修；5 条位图直测保留，3 条设置入口用例 → `CoverSection` / `CoverFontSection`）。
- 分桶：可修 5、判废 1、待裁 3。
- 结论（本单只清点出方案，不落码）：禁止删除测试文件、禁止为止血改断言；可修文件按旧→新对照迁移 Compose UI 测试；待裁文件先补信息（Compose testTag、拖拽手势入口、新页行结构）再落锤；判废条目待新屏预览出口确认后再定。
- CI 盲区：`.github/workflows/verify.yml:144` 只编 `compileAppDebugUnitTestKotlin`；`.github/actions/build-apks/action.yml:10,14` 只 `assembleDebug/assembleRelease`；workflows 内 `AndroidTest/androidTest` 零命中。加固两方案：甲 `assembleDebugAndroidTest` job（先清存量否则上线即红，且只编译不跑用例）；乙静态未解析 import 门禁 `data_scripts/check-androidtest-imports.py`（秒级无编译依赖，但抓不到资源/反射/签名漂移）。本单不建 job、不建脚本。
- 反例口径：静态门禁已规避同名/别名/typealias/扩展 receiver/companion 成员误报；同包不 import、通配展开、资源 id、反射成员（50 处 `getDeclaredMethod/Field`）为已知盲区。
- 判定报告：`docs/审计-androidTest失修清点.md`。
- 复核条件：① 任一可修文件迁移 Compose UI 测试后，须在真机/emulator 跑通对应 instrumentation；② 待裁三文件补齐 Compose 钩子与手势入口信息后重新分桶；③ 若后续 CI 开 androidTest 编译 job，必须先清零本单 15 条硬错误再合入门禁。
