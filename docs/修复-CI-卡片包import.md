> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=c6806030a6d5；验收=aa92e3b27983；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批5-机制文档 §三）
> 回执缺失（规则上线前交付，留档）

# 修复 · CI 卡片包缺失 import

范围：`app/src/main/java/io/legado/app/ui/card/*.kt`（14 个文件）。只动 import 行，未增删业务代码；未触碰 map/crossing、book/read、ui/main。

## 补齐 / 修正明细

| 文件 | 补的 import | 支撑行号 |
|---|---|---|
| CardBase.kt | `androidx.compose.animation.core.animateFloat` | :138 `skeletonTransition.animateFloat(...)` |
| TopicCard.kt | `androidx.compose.ui.draw.clip` | :177 `.clip(CircleShape)` |
| EmptyStateCard.kt | 包名修正：`androidx.compose.ui.text.TextUnit` → `androidx.compose.ui.unit.TextUnit` | :223 `fontSize: TextUnit` |

> EmptyStateCard 一项为非扩展类导入：`TextUnit` 归属 `ui-unit`（`androidx.compose.ui.unit`），原 `ui.text` 包下不存在该类；包内其余 10 处均按 `unit.TextUnit` 导入，此文件为孤例。

## 全包扫描结论

- 清单内扩展函数（animateFloat / animateDpAsState / animateColorAsState / animateFloatAsState、clip / drawBehind / alpha / shadow / rotate / blur、offset / padding / size / heightIn / widthIn / fillMaxWidth / fillMaxHeight / matchParentSize、background / border / clickable、semantics、getValue / setValue、lerp）逐个比对真实调用点与 import，其余 11 个文件均齐全，无误报。
- `CardTokens.kt:87` 的 `drawBehind` 仅出现在 KDoc 注释里，非真实调用，未加 import。
- `BookCard.kt:88-91` 四处 `var ... by` 已正确导入 `setValue`；其余 `val ... by` 均有 `getValue`。
- `ShareCard.kt:60/67/74` 的 `lerp` 导入齐全。

## 括号配平差值（{} () []，代码扣除注释与字符串后统计）

14 个文件差值全部为 0：BattleCard / BookCard / CardBase / CardTokens / CategoryCard / ClassicCard / EmptyStateCard / EntityRowCard / PersonPopCard / SearchResultCard / SettingRowCard / ShareCard / TopicCard / TopicMarxCard。

## 为什么这类错误反复出现

扩展函数 / 扩展属性在 Kotlin 中依赖 import 才能解析，但 IDE 索引与智能补全常在未导入时也给出提示或临时推断，本地编写阶段不报红；只有 CI 全量按包编译时才暴露 `Unresolved reference`。此外编译器在首个断错文件即中止，后续文件的同类缺失当轮不会上报，修好一处后必须复扫全包，不能只看 CI 当轮报错清单。
