# 锚点卡 · Q278 设计 token 漂移修复

> 用途：Q-278 交付回源。逐字引文带 `文件:行`；修复全文 `docs/修复-设计token漂移.md`。

## 一、事实锚点

- 门禁脚本：`data_scripts/check-design-tokens.py`（本单未改判定逻辑）；契约：`docs/契约-设计token.json`（本单未改）。
- 改前门禁末行：`DESIGN-TOKENS: FAIL（3 项）`，三项明细：
  - `card-radius-dp 契约值 16 / 源码实际值 14 ← app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfTokens.kt:84`
  - `stale-180mb-constant 命中 1 处：app/src/main/java/io/legado/app/ui/book/read/ink/InkTokens.kt:251`
  - `stale-180mb-doc 命中 1 处：app/src/main/assets/ui_tokens.txt:29`
- 圆角规格出处：`docs/落文-规格切片/切片-25-卡片设计规格.md:13` 逐字 `| 圆角 | 16dp |`；`:64`「圆角 16」Token 唯一来源；`docs/落文-规格切片/切片-10-书架页.md` 无圆角数值。
- 内存三档规格出处：`docs/落文-规格切片/切片-21-架构层.md:27` 逐字 `≤100MB 过；100–120MB 注「待优化」并登记优化项；>120MB 打回。取代旧 ≤180MB 红线`；`:53`「旧 180MB 红线清零」。
- 同源上游圆角：`ComponentPalette.kt:146 = 16`、`CardTokens.kt:76 = 16.dp`，改前即与规格一致。

## 二、结论锚点

- C1（项 1，改源码）：规格 16dp 属实，契约值正确；`ShelfTokens.kt:84` `14f` 为源码漂移，改 `16f`，注释回指切片-25:13。
- C2（项 2，改源码）：旧单档 `PEAK_MEMORY_MB = 180.0` 为历史残留；按三档拆 `MEMORY_PEAK_PASS_MB = 100.0` / `MEMORY_PEAK_WARN_MB = 120.0`；旧符号 main/test 零消费方，删名不留兼容别名。
- C3（项 3，改文本）：`ui_tokens.txt:29` 改三档口径「≤100MB 过；100–120MB 待优化；>120MB 打回」。
- C4（连带测试）：`InkTokensTest.kt:70-71`、`InkPerfBenchmarkTest.kt:45-46` 两条断言随常量改名改值同步；类头/行内注释旧 180MB 措辞一并清除。
- C5（行号守恒）：新常量块与原块等行数，契约锚点 `InkTokens.kt:254`（字体子集 200KB）、`:257`（章缓存半径 3）位置未位移；契约 JSON 未改。

## 三、机检锚点

- `python3 data_scripts/check-design-tokens.py` 末行：`DESIGN-TOKENS: PASS（机检 67 项全一致，spec-only 3 项登记在册）`，退出码 0。
- `python3 data_scripts/kotlin-syntax-lint.py`（4 个改动 Kotlin 文件）末行：`KOTLIN-LINT: PASS（4 个文件）`。
- 残留检索：`app/src/main` 与 `app/src/main/assets/*.txt` 内 `180MB` / `180 MB` 零命中；`PEAK_MEMORY_MB` 在 app 源码与测试零命中。
- 未机检：真机 PSS 峰值、CI 编译与单测、截图像素帧（沙箱无 JDK，待外部环境）。

## 四、调用面锚点

- 圆角 token 三处消费：`ShelfBookCard.kt:144`（外形 RoundedCornerShape）、`ShelfBookCard.kt:167`（辉光 CornerRadius 随动）、`BookshelfScreen.kt:781`（clip 轮廓）。
- 内存新符号消费：仅 `InkTokensTest.kt:70-71`、`InkPerfBenchmarkTest.kt:45-46` 断言；无运行时 PSS 消费方（F-6 / W-2 未落地）。
- 无关 `180.0`：角度/经纬度数学常数（π/180、±180° 边界），清单见修复报告 §3.3，未动。

## 五、回源路径

- 修复报告：`docs/修复-设计token漂移.md`
- 契约：`docs/契约-设计token.json`（memory-peak-ceiling-mb 100 / memory-peak-warn-mb 120 / dict-memory-mb 15，三条 spec-only）
- 规格切片：`docs/落文-规格切片/切片-25-卡片设计规格.md:13,64`、`docs/落文-规格切片/切片-21-架构层.md:27,53`
- 方案登记：`docs/方案-可判定体验基线.md:121-126`（F-1/F-2/F-3 本单已修，销账见报告 N4）
- 边界：本单未写 `data_scripts/**` / `.github/**` / 契约 JSON；未编译；未做 git 写操作；规格切片原文未改。
