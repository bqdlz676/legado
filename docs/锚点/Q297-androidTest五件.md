# 锚点卡：Q297 androidTest 可修五件复核

- 缺陷：Q-292 审计判「可修」的 5 个 androidTest 文件，本单逐条复核未解析 import 与 main 侧现址；结论 **5 件全部改判「待裁」，10 条未解析本单改掉 0 条**。旧 Adapter / Fragment / ViewBinding 已删除，幸存新 VM / Compose 分区与测试调用点签名或形态不等价，最小换 import 无法编过。
- 判据：import 语句 261 条（2 条通配第三方跳过），逐条比对 259 条，未解析 10 条，其余 249 命中；lint 逐文件 PASS（语法级，不代表编译通过）；候选新类型 grep 回证存在但形态不等价。
- 硬错误清单（5 文件 10 条，文件:行号 + 符号 + 处置）：
  1. `ui/association/RuleSelectionShareTest.kt:24` `DictRuleAdapter` —— 已删除；→ `DictRuleListViewModel`（`ui/dict/rule/DictRuleListViewModel.kt:27`）+ `DictRuleScreen.kt`（Compose，无 testTag）。**待裁**。
  2. `ui/association/RuleSelectionShareTest.kt:26` `ReplaceRuleAdapter` —— 已删除；→ `ReplaceRuleListViewModel`（`ui/replace/ReplaceRuleListViewModel.kt:28`）+ `ReplaceRuleScreen.kt`。**待裁**。
  3. `ui/book/source/BookSourceCheckUiTest.kt:33` `ItemBookSourceBinding` —— 布局 `item_book_source.xml` 缺失，binding 不可生成；→ `SourceManageRow.kt` Compose 行。**待裁**。
  4. `ui/book/source/BookSourceCheckUiTest.kt:42` `BookSourceAdapter` —— 已删除；→ `SourceManageViewModel.kt:56` + `SourceManageScreen.kt`。**待裁**。
  5. `ui/book/source/SourceOrderMetadataUpdateTest.kt:16` `ReplaceRuleAdapter` —— 已删除。**待裁**。
  6. `ui/book/source/SourceOrderMetadataUpdateTest.kt:17` `ReplaceRuleViewModel` —— 改名 `ReplaceRuleListViewModel` 存活，但 `move(id,id,after)` / `enableSelection(list)` / `disableSelection(list)` 与新签名 `move(rule, down)` / 无参批量 API 不兼容。**待裁**。
  7. `ui/code/CodeSelectionUiTest.kt:81` `BookSourceEditAdapter` —— 已删除；→ `SourceEditScreen.kt`。**待裁**。
  8. `ui/code/CodeSelectionUiTest.kt:82` `BookSourceEditViewModel` —— 改名 `SourceEditViewModel.kt:51` 存活；旧可变 `bookSource` 属性与 `editEntities` 列表无等价出口（新状态 `fields: Map<EditTab, List<EditField>>`）。**待裁**。
  9. `ui/widget/image/CoverTitleAdaptiveUiTest.kt:36` `CoverConfigFragment` —— 已删除；→ `CoverSection.kt:24` Composable。**待裁**。
  10. `ui/widget/image/CoverTitleAdaptiveUiTest.kt:37` `CoverFontConfigFragment` —— 已删除；→ `CoverFontSection.kt:42` Composable。**待裁**。
- 不阻塞分支（import 级可解析，另单评估下沉/重写）：A 的 highlight/toc 两条；B 的两条 DAO/CheckSource 逻辑；C 的 RSS 条（`RssSourceAdapter`/`RssSourceViewModel` 签名匹配）；D 的其余 20 条；E 的 5 条位图直测。
- 新发现：
  - **待裁**：A L153 `R.menu.dict_rule_sel` 菜单 XML 零命中。
  - **待裁**：B `check_status_filter` / `menu_show_source_bookshelf_count` / `menu_block_source_navigation` / `menu_show_source_check_status` 四个 R 成员 main 零命中。
  - **待裁**：D L736/L742/L743/L1464/L1465 旧 RecyclerView 层级假设，运行期待复核。
  - **已入队**：反射点复活 `CoverImageView.currentNameBitmap`（`CoverImageView.kt:104`）、`CodeEditActivity.search/searchTxt`（:795/:886）；其余反射点沿用 Q-292 待裁。
  - **待裁**：六个新 Compose 落点 testTag/semantics 零命中。
  - **已闭环**：10 条清单与 Q-292 审计逐条吻合，261 条 import 语句（2 通配跳过）/ 259 逐条比对 / 10 unresolved / 249 命中，无漏报误报。
- 自证：lint 五次末行均 `KOTLIN-LINT: PASS（1 个文件）`；编译级未验证（沙箱无 JDK，CI 不编 androidTest）。
- 复核条件：① 任一待裁文件转 Compose UI 测试前，先补 testTag/语义钩子与 VM 状态出口；② 新 VM 若补按 id 移动、按列表批量启停方法，须回头重判 C；③ CI 若开 `assembleDebugAndroidTest` job，必须先清零 Q-292 全部 15 条硬错误再合入门禁；④ 反射成员、资源 id、Compose 节点存活须 emulator/真机 instrumentation 闭环，静态门禁不算数。
- 判定报告：`docs/修复-androidTest可修五件.md`。
