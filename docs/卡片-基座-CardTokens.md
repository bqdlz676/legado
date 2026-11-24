# 卡片基座 · CardTokens / CardBase 落地报告

范围：只做基座，不做任何具体卡片。规格来源 `docs/落文-规格切片/切片-25-卡片设计规格.md`「通用卡片 Token」表。

## 产出文件
- `app/src/main/java/io/legado/app/ui/card/CardTokens.kt`（token 唯一来源）
- `app/src/main/java/io/legado/app/ui/card/CardBase.kt`（`CardSurface` 五态基座）

## Token 表落地对照（规格 → 代码）
| 规格 | token | 行号 |
|---|---|---|
| 底色 纸白 #F7F1E6 | `cardPaper 0xFFF7F1E6` | CardTokens:26 |
| 默认阴影 12px rgba(212,165,74,0.3) | `shadowDefault 0x4DD4A54A` / `shadowDefaultElevation 12.dp` | :46/:49 |
| 悬浮阴影 20px rgba(212,165,74,0.45) + 抬 2dp | `shadowHover 0x73D4A54A` / `20.dp` / `hoverLift 2.dp` | :52/:55/:58 |
| 点击阴影 8px rgba(194,59,46,0.3) | `shadowPressed 0x4DC23B2E` / `8.dp` | :61/:64 |
| 圆角 16dp | `cardRadius 16.dp` | :69 |
| 无边框（禁 stroke） | `cardBorderWidth 0.dp`，基座无 border 修饰符 | :72 |
| 2px noise / 不透明度 0.03 | `noiseGrid 2.dp` / `noiseAlpha 0.03f` | :80/:84 |
| 卡片间距 12dp | `cardGap 12.dp` | :78 |
| 语义色 墨/石青/云灰/暖金/朱砂 | `ink 0xFF1A1410` `stoneBlue 0xFF3A6B66` `cloudGray 0xFF9B9687` `warmGold 0xFFD4A54A` `cinnabar 0xFFC23B2E` | :29/:32/:35/:38/:41 |
| 字号 8/9/10/11/12/14/16/18/22sp | `text8…text22` | :117-141 |

## 五态实现（CardSurface，CardBase.kt）
- `Default`：纸白底 + 暖金 12dp 微光；`Hover`：暖金 20dp + 上抬 2dp；`Pressed`：朱砂 8dp 暗影；`Loading`：同尺寸骨架灰块 + 暖金斜光带，`infiniteRepeatable` + `tween 1500ms`，`RepeatMode.Restart` 无 delay 硬切；`Disabled`：整体 alpha 0.30、投影归零。
- 数值量（elevation / lift）走 `animateFloatAsState` + `spring(0.80, 360)`；阴影色按态直取 token，不做颜色插值。
- 宣纸颗粒：`Modifier.drawBehind` 固定种子 2dp 网格撒点，无图片资源、不新建 Bitmap。

## 自检输出
- ① grep 回证：`0xFFF7F1E6`(:26)、`0xFFD4A54A`(:38)、`16.dp`(:69)、`0.03f`(:84) 均命中；三档阴影 / 20dp / 8dp / 2dp / 12dp / 9 档字号全部命中（行号见上表）。
- ② import 对照：CardTokens 5 个（Color/Dp/TextUnit/dp/sp，body 均命中）；CardBase 23 个，逐个脚本比对全部命中；`getValue` 为 `by` 委托导入（3 处 `by`，先例 InkCard.kt:19）；反向扫描无未解析大写标识。
- ③ 括号配平（python3）：两文件 `{}` `()` `[]` 差值均 = 0。
- 禁项核查：无 `border`/`stroke` 修饰符、无 `painterResource`/`R.drawable`/Bitmap 创建。

## 未验证项
- 未编译，沙箱禁 gradle；Compose import 靠人工对照，未经编译期解析。
- 阴影 elevation 与规格 px 的视觉对应、骨架光带实际扫动节奏未在真机 / 模拟器目检；弹簧参数沿用组件库 reveal 口径，未做帧率实测。
