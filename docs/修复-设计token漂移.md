# 修复-Q278 设计 token 漂移（Q-278）

锚点卡：docs/锚点/Q278-token漂移.md

> 对象：设计 token 契约门禁 `python3 data_scripts/check-design-tokens.py` 实测红灯 3 项——书架卡圆角 14f vs 规格 16dp；旧 ≤180MB 内存常量残留；随包参考文本残留 180MB。
> 边界：本单只改上述三处 + grep 出的必要连带点（两处契约测试）+ 本单两份文档；禁碰 `data_scripts/**`、`.github/**`、`docs/契约-设计token.json` 的判定逻辑与锚点；沙箱无 JDK，禁 `./gradlew` / kotlinc / 编译；改完只跑 `kotlin-syntax-lint.py` 与门禁脚本；未做 git 写操作。

## 一、任务与门禁基线

- 门禁基线（改前实测，末行逐字）：`DESIGN-TOKENS: FAIL（3 项）`，退出码 1。
- 改前三项 FAIL 明细（逐字）：
  - `[FAIL] card-radius-dp 契约值 16 / 源码实际值 14 ← app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfTokens.kt:84`
  - `[FAIL] 残留口径 stale-180mb-constant 命中 1 处：app/src/main/java/io/legado/app/ui/book/read/ink/InkTokens.kt:251（规格 docs/落文-规格切片/切片-21-架构层.md:27）` 〔W-451 回源有效〕
  - `[FAIL] 残留口径 stale-180mb-doc 命中 1 处：app/src/main/assets/ui_tokens.txt:29（规格 docs/落文-规格切片/切片-21-架构层.md:27）`
- 判定原则：先逐条读原文与规格出处，再定「改源码」还是「改契约」；不看行号就改。本单三项核对结果均为源码/文本漂移，契约值与规格一致，**契约 JSON 一行未动**。

## 二、逐项核对与归属判定

### 2.1 项 1：书架卡圆角 14f → 判定：改源码（规格 16dp 属实）

**源码原文（改前，`ShelfTokens.kt:83-84`，含上下文 `:76-90` 逐字）：**

```kotlin
    // —— 卡片几何（切片-10 原文尺寸，dp/sp）——
    /** 竖排书名 22sp 墨色加粗 */
    const val TITLE_SP: Float = 22f
    /** 作者 / 注疏 云灰 11sp */
    const val AUTHOR_SP: Float = 11f
    /** 章节小字 10sp */
    const val CHAPTER_SP: Float = 10f
    /** 卡片圆角 14dp */
    const val CARD_RADIUS_DP: Float = 14f
    /** 暖金微光溢出半径 12px（0 0 12px rgba(212,165,74,.3)） */
    const val GLOW_RADIUS_DP: Float = 12f
```

**规格原文核对：**

- `docs/落文-规格切片/切片-25-卡片设计规格.md:13`（通用卡片 Token 表）逐字：`| 圆角 | 16dp |`。
- 同文件 `:64` 逐字：`Token 唯一来源：CardTokens.kt 里纸白/三档阴影/圆角 16/间距 12/颗粒 0.03 各一处定义`。
- 书架域切片 `docs/落文-规格切片/切片-10-书架页.md` 全文 grep「圆角 / 14 / 16dp / 半径」**零命中**：书架切片未另立圆角数值，卡片外形圆角回落到切片-25 通用表 16dp。
- 同源两份上游实现均为 16：`app/src/main/java/io/legado/app/ui/components/ComponentPalette.kt:146` `const val CARD_RADIUS_DP: Int = 16`；`app/src/main/java/io/legado/app/ui/card/CardTokens.kt:76` `val cardRadius: Dp = 16.dp`。

**裁决：** 规格为 16dp，契约值 16 正确；`ShelfTokens.kt:84` 的 `14f` 是源码漂移。改源码 `14f` → `16f`，注释同步改为 16dp 并回指切片-25:13。不构成「契约写错」情形，故不改契约。

**改后行原文：**

```kotlin
    /** 卡片圆角 16dp（切片-25:13 通用卡片 Token「圆角 16dp」；变体不得自行改圆角）。 */
    const val CARD_RADIUS_DP: Float = 16f
```

### 2.2 项 2：旧 180MB 内存常量 → 判定：源码残留，按三档口径拆常量

**源码原文（改前，`InkTokens.kt:250-251`，含上下文 `:247-257` 逐字）：**

```kotlin
    /** 冷启动 → 书架可交互红线 ≤1.8s（切片-08 启动速度；bench 写死断言）。 */
    const val COLD_START_TO_BOOKSHELF_MS: Double = 1800.0

    /** 阅读页峰值内存红线 ≤180MB（切片-08 内存；bench 写死断言）。 */
    const val PEAK_MEMORY_MB: Double = 180.0

    /** 字体子集首屏体积红线 ≤200KB（切片-14 / 切片-08 字体加载；bench 写死断言）。 */
    const val FONT_SUBSET_MAX_KB: Double = 200.0

    /** 章节排版缓存只保留前后各 3 章（切片-08 阅读缓存；其余释放）。 */
    const val CHAPTER_CACHE_RADIUS: Int = 3
```

**规格原文核对：**

- `docs/落文-规格切片/切片-21-架构层.md:27`（第 7 项 内存·三档判定）逐字：`双引擎峰值内存：≤100MB 过；100–120MB 注「待优化」并登记优化项；>120MB 打回。取代旧 ≤180MB 红线（F-04、冲突 9）`；验收列逐字：`峰值 PSS ≤100MB 过；100–120MB 记「待优化」+优化项编号；>120MB 打回；旧 180MB 口径 grep 0 残留（文档/注释/门禁）`。
- 同文件 `:53` 逐字：`内存 ≤100MB 三档：≤100 过 / 100–120 待优化 / >120 打回；峰值采集进 bench，旧 180MB 红线清零。`
- 契约 `memory-peak-ceiling-mb = 100`、`memory-peak-warn-mb = 120` 两条均 `check=spec-only`（源码暂无对应常量，待 bench 接线），出处同切片-21:27。

**裁决：** 旧单档 `180.0` 是历史残留，规格已作废。处置不是把 180 改成另一个单档天花板（100 是过档线、不是打回线；单档会抹掉 100–120 待优化档），而是按三档口径拆成两条阈值常量：过档线 100.0、中档界线 120.0。旧符号 `PEAK_MEMORY_MB` 全仓 main/test 零运行时消费方，先接好新常量再删旧名，不留兼容别名。

**改后行原文（`InkTokens.kt:250-251`，行数与原块相等，未位移后续锚点行）：**

```kotlin
    /** 峰值内存三档判定（切片-21:27，bench 写死断言）：≤100MB 过；100–120MB 注「待优化」挂账；>120MB 打回；旧单档红线已作废。 */
    const val MEMORY_PEAK_PASS_MB: Double = 100.0; const val MEMORY_PEAK_WARN_MB: Double = 120.0
```

> 行号约束：契约锚点按行号锁定 `InkTokens.kt:254`（字体子集 200KB）与 `:257`（章缓存半径 3）。初版把注释扩成多行块，把后续声明顶偏 5 行，门禁随即在这两条锚点报「锚点行无法解析」；已改回与原块等行数（1 注释行 + 1 声明行），`:254`、`:257` 位置复位，契约 JSON 未改。

### 2.3 项 3：随包参考文本 180MB → 判定：文本残留，改三档口径

**原文（改前，`app/src/main/assets/ui_tokens.txt:29`，上下文 `:24-32` 逐字）：**

```
# 性能预算（超了就是不合格，不是「有点慢」）
启动 → 见书架        ≤ 1.8s
首触气泡 冷/热        ≤ 300ms / ≤ 80ms
翻页                 ≤ 16ms/frame
地图页进入           ≤ 2s 地形渲染完成
内存峰值（含 WebView）≤ 180MB
字典加载内存（4 表）  ≤ 15MB
字体首屏子集          ≤ 200KB（woff2 subset，仅 WebView 生效）
```

**规格出处：** 同切片-21:27 三档口径；契约残留规则 `stale-180mb-doc` 扫描 `app/src/main/assets/**/*.txt`，正则 `180\s*MB`。

**改后行原文：**

```
内存峰值（含 WebView）≤100MB 过；100–120MB 待优化；>120MB 打回
```

## 三、连带点清单（grep 全仓）

### 3.1 旧内存符号引用方（必要连带，已一起改）

- `app/src/test/java/io/legado/app/ui/book/read/ink/InkTokensTest.kt:68-71`：原断言 `assertEquals(180.0, InkTokens.PEAK_MEMORY_MB, 0.0)`，改为三档两条断言。
  - 改后逐字：
    ```kotlin
            // 冷启 → 书架 ≤1.8s；峰值内存三档 ≤100 / 100–120 / >120MB；字体子集 ≤200KB。
            assertEquals(1800.0, InkTokens.COLD_START_TO_BOOKSHELF_MS, 0.0)
            assertEquals(100.0, InkTokens.MEMORY_PEAK_PASS_MB, 0.0)
            assertEquals(120.0, InkTokens.MEMORY_PEAK_WARN_MB, 0.0)
            assertEquals(200.0, InkTokens.FONT_SUBSET_MAX_KB, 0.0)
    ```
- `app/src/test/java/io/legado/app/ui/book/read/ink/InkPerfBenchmarkTest.kt:12`（类头注释）、`:44-46`（断言体）：原 `assertTrue(InkTokens.PEAK_MEMORY_MB <= 180.0)`，改为两条阈值写死断言。
  - 改后逐字：
    ```kotlin
            // 峰值内存三档线写死：过档线 100MB、中档界线 120MB（切片-21:27；真机内存峰值未在本机测量，仅收口阈值）。
            assertTrue(InkTokens.MEMORY_PEAK_PASS_MB == 100.0)
            assertTrue(InkTokens.MEMORY_PEAK_WARN_MB == 120.0)
    ```

### 3.2 旧符号终态检索

- `PEAK_MEMORY_MB` 在 `app/src/main`、`app/src/test` 零命中；仓内仅剩两处非源码命中：契约残留正则定义 `docs/契约-设计token.json:1206`（门禁检测规则本身，保留）；方案报告历史记录 `docs/方案-可判定体验基线.md:122`（另一单交付件，本单边界不改动，销账见 N4）。
- `180MB` / `180 MB` 在 `app/src/main/**` 与 `app/src/main/assets/**/*.txt` 零命中（门禁 `stale-180mb-constant`、`stale-180mb-doc` 均 0 命中）。

### 3.3 同名 `180.0` 无关项（经核对不动）

下列 `180.0` 是角度/经纬度数学常数（π/180 弧度换算、经度 ±180° 边界），与内存口径无关，本单一律不动：

- `app/src/main/java/io/legado/app/help/sim/TerrainAnalyzer.kt:145,227`
- `app/src/main/java/io/legado/app/ui/map/globe/contract/GlobeSphere.kt:23,31,32` 〔W-451 回源有效〕
- `app/src/main/java/io/legado/app/ui/map/globe/geo/Geo.kt:22,23,28,29`
- `app/src/main/java/io/legado/app/ui/map/globe/mesh/SphereGrid.kt:61`
- `app/src/main/java/io/legado/app/ui/map/globe/replay/power/PowerPolygon.kt:107,112,130,136`
- `app/src/main/java/io/legado/app/ui/map/globe/terrain/TerrainSource.kt:23,24`
- 测试侧经度边界夹具：`PlaceGeoTimeDictTest.kt:216`、`GlobeBenchTest.kt:97`、`GlobeSphereContractTest.kt:105-106`、`GeoTest.kt:62,63,88,90,134`、`GlobeProjectionTest.kt:63`、`SphereGridTest.kt:67`、`GlobeCameraTest.kt:86,98`、`RenderBenchTest.kt:48`

## 四、回证（改后实测）

### 4.1 契约门禁

- 命令：`python3 data_scripts/check-design-tokens.py`
- 末行逐字：`DESIGN-TOKENS: PASS（机检 67 项全一致，spec-only 3 项登记在册）`
- 改前末行：`DESIGN-TOKENS: FAIL（3 项）`；改后退出码 0。

### 4.2 改后行 grep 原文

- `ShelfTokens.kt:84`：`const val CARD_RADIUS_DP: Float = 16f`
- `InkTokens.kt:251`：`const val MEMORY_PEAK_PASS_MB: Double = 100.0; const val MEMORY_PEAK_WARN_MB: Double = 120.0`
- `ui_tokens.txt:29`：`内存峰值（含 WebView）≤100MB 过；100–120MB 待优化；>120MB 打回`
- `InkTokensTest.kt:70-71`：`assertEquals(100.0, InkTokens.MEMORY_PEAK_PASS_MB, 0.0)` / `assertEquals(120.0, InkTokens.MEMORY_PEAK_WARN_MB, 0.0)`
- `InkPerfBenchmarkTest.kt:45-46`：`assertTrue(InkTokens.MEMORY_PEAK_PASS_MB == 100.0)` / `assertTrue(InkTokens.MEMORY_PEAK_WARN_MB == 120.0)`

### 4.3 Kotlin 语法级预检

- 命令：`python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfTokens.kt app/src/main/java/io/legado/app/ui/book/read/ink/InkTokens.kt app/src/test/java/io/legado/app/ui/book/read/ink/InkTokensTest.kt app/src/test/java/io/legado/app/ui/book/read/ink/InkPerfBenchmarkTest.kt`
- 末行逐字：`KOTLIN-LINT: PASS（4 个文件）`

## 五、二阶影响面

### 5.1 圆角 token 调用点（`ShelfTokens.CARD_RADIUS_DP`）

1. `app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfBookCard.kt:144`：`val cardShape = RoundedCornerShape(ShelfTokens.CARD_RADIUS_DP.dp)`——卡片外形轮廓形状。
2. `app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfBookCard.kt:167`：辉光 `CornerRadius(ShelfTokens.CARD_RADIUS_DP.dp.toPx() + inset, …)`——自绘三层叠晕的角半径随卡片圆角同步外扩，形状保持同心，不出现圆角与辉光错位。
3. `app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfScreen.kt:781`：`.clip(RoundedCornerShape(ShelfTokens.CARD_RADIUS_DP.dp))`——卡片裁剪轮廓，与外形形状一致。

上游同源转发（均已是 16，本次未动、不受影响）：`ComponentPalette.kt:146`（Int 16）→ `ComponentSystemParams.kt:39`（`RADIUS_CARD_DP` 转发）、`ComponentTokens.kt:84`（`cardRadius` 派生）；`CardTokens.kt:76`（16.dp）。

**14f 是否兼作描边/间距：** 否。`ShelfTokens.kt` 内除该圆角常量外无第二个 14 dp/sp 字面量；全仓其余 `14.dp` / `14.sp` 均为无关组件的内边距或字号，不经过本 token。描边宽度在规格中为「无」（切片-25:14），`CardTokens.kt:82` `cardBorderWidth = 0.dp`，与圆角不同源。

### 5.2 内存阈值常量引用面

- 新符号 `MEMORY_PEAK_PASS_MB` / `MEMORY_PEAK_WARN_MB` 当前仅被上述两处契约测试断言；运行时 PSS 采集与三档判定尚未接线（契约两条 memory 仍为 spec-only，沿用 F-6 / 工单 W-2）。本单不新增运行时消费方。
- 三阶推演：未来接 PSS bench 时，判定顺序应为 `≤ PASS 过；PASS–WARN 待优化挂账；> WARN 打回`，两条阈值不得再合并回单档天花板；若真机 bench 推翻 100/120 界线，须先改规格切片-21:27 与契约，再回改源码常量。

### 5.3 本单改动文件清单（5 个）

- `app/src/main/java/io/legado/app/ui/main/bookshelf/ShelfTokens.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ink/InkTokens.kt`
- `app/src/main/assets/ui_tokens.txt`
- `app/src/test/java/io/legado/app/ui/book/read/ink/InkTokensTest.kt`
- `app/src/test/java/io/legado/app/ui/book/read/ink/InkPerfBenchmarkTest.kt`

> 工作区里另有 `SearchHotBinderTest.kt`、`app/build.gradle` 等改动为本单开工前既存，不属本单产物，未触碰、未计入上文清单。

## 六、反例推演：截图像素基线会不会被这次改红

### 6.1 现状回源（实证，非推测）

- 仓内不存在已冻结 golden 帧：`docs/待接-截图基建/README.md:55` 逐字：`本次移出前，截图测试在 CI 上从未成功出过帧（截图 job 随基线单测一同被红挡住）；仓库内不存在已冻结的 golden 帧。`
- 基线指纹文件 `docs/.visual_baseline.sha256` 不存在；`check-visual-baseline.py:83-85` 对缺失基线判 WARN 退出 0，不算 FAIL。
- 截图 job `.github/workflows/screenshot.yml` 不挂主 run gate（README §1 与 yml:4 一致）；`screenshot/src/test/.../BookshelfSnapshotTest.kt` 四态（content/loading/empty/error）走 Paparazzi `snapshot` 出帧。
- 仓内无 `frames.json`，无 committed 对比 PNG（资源目录外零命中）。

### 6.2 判断

- **当下**：14→16 不会让任何已有截图基线失效——基线尚未冻结，没有可对比的像素快照。
- **将来冻结后**：圆角变化只影响卡片四角的环状窄边（含 `ShelfBookCard.kt:167` 辉光角半径），不会整片画面变红；但 Paparazzi 原始像素 diff 没有分区语义，圆角外圈的抗锯齿 fringe 仍可能在四角区域报像素差。100/120 内存常量是纯数值，不进像素帧，对截图基线零影响。

### 6.3 首轮像素基线重录的留痕与防后门

1. **冻结时机**：本规格内的圆角值先落地，再做首轮 golden 冻结；不得在 14dp 旧值上先冻一批、再用 16dp 覆盖。
2. **冻结留痕**：`check-visual-baseline.py --freeze` 的 `note` 必须带本单编号 Q-278、规格出处切片-25:13、冻结 commit；逐帧 sha256 入 `.visual_baseline.sha256`（Paparazzi 侧为 frames.json + artifact），帧清单与哈希成对提交。
3. **record / verify 分离**：`recordPaparazzi*`（重生成 golden）与 `verifyPaparazzi*`（对比）是独立 Gradle 任务；verify 必须在主 run 每个提交上跑；record 只允许在专设冻结流程里由验收代理触发，业务开发单不得自行重录。
4. **防批量重录绕过审查**：manifest 逐帧登记；一次重录必须列出变更帧清单与前后 diff 摘要；整批重录消红需要挂工单 + 规格/验收签字，不允许用 `--freeze` 整目录覆盖来压掉红差。
5. **容差从严**：若后续为圆角抗锯齿 fringe 加像素容差，容差档位须经规格/验收签字登记，不许在组件代码里悄悄放宽；容差只覆盖 AA 边缘，不覆盖整卡配色与布局漂移。
6. **反向情形**：若冻结时发现 golden 仍是按 14dp 旧值生成，该批帧定性为过期基线，须按 Q-278 引用重冻，不得静默覆盖后报「基线一致」。

## 七、新发现问题

> 状态词五选一口径：`未派` / `在飞` / `已修·待验证` / `已验·关闭` / `作废`。本单修复的三项状态为 **已修·待验证**（沙箱无 JDK，编译与单测待 CI 绿）。

- **N1（未派）规格侧文档仍残留旧 180MB 口径**：`docs/落文-规格切片/切片-04-组件系统.md:28` 逐字 `| 内存峰值 | ≤ 180MB（含 WebView） | \`performance.memory\` |`；`docs/落文-设计规格.md:528` 逐字 `| 内存峰值 | 180MB | performance.memory |`；`docs/落文-规格切片/落文-原文-08-Windows原生.md:86` 为逐字转录件，同行已内嵌批注 `**≤ 100MB**`。规格原文属规格层归属，本单边界禁改；需规格维护单按切片-21:27 三档口径回改前两份，转录件保留原样仅补批注。—— 状态：待裁（规格层归属，规格维护单尚未派发，待裁后回改）。
- **N2（未派）书架面板底角内联 14.dp 未走圆角 token**：`BookshelfScreen.kt:906` `.clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))`，是面板（非卡片）底角内联字面量，不消费 `CARD_RADIUS_DP`；是否随卡片 16dp 口径统一，待规格/组件裁决，本单未改。—— 状态：待裁（口径统一与否待规格/组件裁决）。
- **N3（未派）契约 note 过时描述**：`docs/契约-设计token.json:313` note 仍写「已知漂移：ShelfTokens.CARD_RADIUS_DP=14f」，修复落地后该注释已过时；契约文件本单禁碰，需契约维护单同步 note（判定逻辑与锚点不受影响）。—— 状态：待裁（契约维护单尚未派发，待裁后同步 note）。
- **N4（未派）方案报告 F-2 行待销账**：`docs/方案-可判定体验基线.md:122` 仍记 F-2「已入队，改接三档判定或删除该常量」；本单已落地，需该报告维护单把 F-1/F-2/F-3 状态改判关闭并回链本报告。—— 状态：待裁（方案报告销账行待裁移交报告维护单）。
- **N5（未落地）三档内存阈值无运行时 PSS 接线**：新常量目前仅单测断言，峰值采集与三档判定函数未建；沿用方案报告 F-6 与工单 W-2，归属性能 bench 接线。—— 状态：已入队（挂工单 W-2 与方案报告 F-6 登记，归属性能 bench 接线单）。
- **N6（在册维持）既有两项漂移本单未触及**：F-4 `SearchTokens.kt:78` `DURATION_CARD = 350L` 与同源 250ms 同名漂移；F-5 进度竖线宽切片-09:7 作 1dp、切片-25:19 作 2dp 两歧。状态沿用方案报告第七节登记，不在本单处置范围。—— 状态：已闭环（在册维持；F-4/F-5 沿用方案报告第七节登记，本单不处置、不重开）。

## 八、自检记录

- 门禁：`check-design-tokens.py` 末行 `DESIGN-TOKENS: PASS（机检 67 项全一致，spec-only 3 项登记在册）`。
- 语法：`kotlin-syntax-lint.py` 4 个改动 Kotlin 文件末行 `KOTLIN-LINT: PASS（4 个文件）`。
- 边界：未改 `data_scripts/**`、`.github/**`、`docs/契约-设计token.json`；未编译、未跑 gradle、未做 git 写操作；规格切片原文未改。
- 残留：`app/src/main` 与 `app/src/main/assets/*.txt` 内 `180MB` 零命中；旧符号 `PEAK_MEMORY_MB` 在 app 源码与测试零命中。
- 未覆盖项：真机 PSS 峰值、CI 编译与单测结果、截图像素帧，均待外部环境验证（见 N5 与 §6）。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=66fbbf7bd6ae；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
- 逐字引用 1：`docs/机制-子代理必读.md:18`：「每一处改动后用 `grep -n` 贴出真实输出」
- 逐字引用 2：`docs/机制-子代理必读.md:19`：「行数、计数、字节、sha 不许估」
- 逐字引用 3：`docs/机制-子代理必读.md:20`：「没编译、没跑测试、没真机」

适用条款：子代理证据链（grep 回证 / 数字实算 / 未验证项显式声明）；本单常量值、命中计数与 lint 末行均实跑取数，真机 PSS、CI 编译单测与截图帧列为未覆盖项。
