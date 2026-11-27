# 落文 · 规格设计 · 星空天象层 · 候选 A（Tycho-2 深度档 · 11.5 等）

段位：F-73 第①段「规格设计」产出。两份候选之一，供**未参与设计的独立选型代理**审验。
选型结论须由该代理另落 `docs/验收-选型-星空天象层.md`，本文件不含结论、不自评。
版本：候选 A · v2（对齐 F-77 八项、修正天球模型、收口色值）· 2026-11-27

本版相对上一稿（`候选A-星空天象层-Tycho2深度档.md`）改了什么，逐条列在末节，供 F-74 追溯。

---

## F-77 八项索引（选型代理按此逐项点到）

① 目的与用户可感效果 → 第 1 节
② 尺寸 / 间距 / 圆角 / 字号 → 第 5 节
③ 色值（只引既有 token，禁新造裸色值）→ 第 6 节
④ 全部状态（默认/悬停/按下/加载/禁用/空/错误/离线）→ 第 7 节
⑤ 边界与降级（无数据/断网/超长文本/低帧率/无权限）→ 第 11 节
⑥ 与其它模块接口 → 第 10 节
⑦ 逐条可测验收判据 → 第 12 节
⑧ 与全 App 既有资产一致性对照 → 第 13 节

---

## 〇、裁决记录（写码前必须知道的口径）

1. 深度取 11.5 等推荐档，星表走 Tycho-2（用户 2026-11-27 确认）。
2. 分野主表取《史记·天官书》二十八舍十二州 + 国别分野；《淮南子》《汉书》异说标"一作"，不合并、不仲裁。
3. 地上观一期固定 J2000 天球 + 可拖时间轴；实时天文（行星/彗星/岁差/大气折射）二期。
4. 分野层注册为地图第九入口（P-06 六主二附之外）。
5. 星官与星座两套体系都要、可切、默认星官。
6. 缩放线性连续、不设档数；既有档数制（`mesh/LodLevel.kt` 三档）一并改造。
7. 双视角：天外观（上帝视角）+ 地上观（从地球任意点抬头），可切换。

第 2–7 条均为用户未否决后按建议默认推定；若选型代理认为越权，按"退回设计段"处理并在报告写明。

---

## 一、① 目的与用户可感效果

用户动作序列（这是验收要复现的那条路径）：

1. 在长安城视图双指放大/缩小，穿过州、国、地球仪。
2. 继续捏合，地球连续缩成一颗金点，星空从四边连续浮上来，**全程无一次跳变、无一次"啪"地换层**。
3. 星空里先看见银河的暗带与暖白核心，再看见成片的暗星，最后亮星浮出四芒。
4. 头顶出现二十八宿名，沿黄道带排成一圈；切到「星座」后换成西方 88 星座连线，切回「星官」恢复。
5. 点一颗星，它亮起一圈暖金呼吸光，半屏 Sheet 浮上来：古名、今名、星官归属、宿序、星等、距离（有才显示）、古籍原文一句。
6. 点"井宿"，天球上井宿八星连成金色，地球那边雍州整片暖金脉冲亮起，州名竖排浮出；反过来点雍州，天球自动转向井宿。
7. 右上角切「地上」，镜头落到长安城头，地平线压暗、上方 5° 一圈暖辉，抬头看见同一片星，位置与刚才天外观严格对应。
8. 拖时间轴往前推两个时辰，星空整体西移，北斗绕北极转过去。

用户可感效果，五条（每条都能被指着说"做到了/没做到"）：

1. **连续**：从城到星，手指不抬、画面不断，中间没有任何一帧是"换了一张图"。
2. **深**：放大到局部天区，暗星一层层长出来，最后密到 11.5 等，不是一张画死的星空贴图。
3. **可读**：每颗星点了有话说，说的每句都能追到古籍原文或星表字段，不编。
4. **有根**：星宿不是飘在天上的名词，它对应着地上的一片州，点哪边另一边都应。
5. **美**：暖墨夜空、沉金连线、纸白星点；不冷、不蓝、不黑、不亮闪闪。

---

## 二、文件域（只允许碰这些，动别的即验收不合格）

新增：

1. `app/src/main/java/io/legado/app/ui/map/globe/astro/contract/SkyParams.kt`——星空参数单源（常量、纯函数、无 Android 依赖，可 JVM 单测）。
2. `app/src/main/java/io/legado/app/ui/map/globe/astro/contract/SkyFrame.kt`——帧快照数据类（见第 10 节）。
3. `app/src/main/java/io/legado/app/ui/map/globe/astro/contract/SkySpecColor.kt`——天文内容色（数据色，非 UI 配色，见第 6.2）。
4. `app/src/main/java/io/legado/app/ui/map/globe/astro/proj/StarProjector.kt`——赤道/地平坐标到屏幕的纯函数投影。
5. `app/src/main/java/io/legado/app/ui/map/globe/astro/data/StarStore.kt` + `StarCatalog.kt`——星表读取与内存索引。
6. `app/src/main/java/io/legado/app/ui/map/globe/astro/pick/StarPicker.kt`——网格分桶命中。
7. `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt`、`AsterismLayer.kt`、`FieldAllocationLayer.kt`——三套线框层。
8. `app/src/main/java/io/legado/app/ui/map/globe/host/SkyCanvasRenderer.kt`——星空三 pass 绘制（拆出宿主，见第 5 节末）。
9. `app/src/main/java/io/legado/app/data/entities/StarEntity.kt`、`AsterismEntity.kt`、`FieldAllocationEntity.kt` 与对应 `dao/`。
10. `app/src/main/assets/astro/**`——内置星官/星座矢量与分野表；Tycho 金字塔不入库。
11. `docs/落文-规格切片/切片-26-星空天象层.md`——新建。

改动（仅限以下，逐处写明改什么）：

12. `GlobeConfig.kt:106`——`CAMERA_MAX_DISTANCE_RATIO` 6.0f → 10.0f。理由见第 9.1。
13. `scene/GlobeScene.kt:79-80`、`:91-92`——两处 coerce 随常量自动生效，无需改代码；但 `:29` 的 `starField` 降级为兜底，新增 `sky` 状态字段。
14. `render/FrameSnapshot.kt:30`——增 `sky: SkyFrame? = null`；`:56` 构造处补装配（全仓仅此 1 处真实构造，已 grep 实测）。
15. `mesh/LodLevel.kt`、`mesh/LodSelector.kt`——三档保留为资源上限，选择器输出改为连续 `blendWeight`（见第 9.3）。
16. `GlobeTokens.kt`——追加 3 个夜空派生色出口（见第 6.3），不新建平行 palette。
17. `host/GlobeScreenHost.kt`——只加一行委托调用 `SkyCanvasRenderer.draw(...)`，净增 ≤5 行。理由：该文件实测 603 行，已超 600 债务阈值。
18. `offline/TextureKind.kt`——追加两个星空密度层枚举（见第 11.3），改后必须扫 `when` 穷尽与消费方。
19. `contract/GlobeParams.kt`——只增常量，既有冻结值（半径 100 / 自转 0.2°/s / 倾角 23.5° / 128×128 / 4dp / 1200ms）一律不动。
20. `docs/落文-规格切片/切片-20-3D沙盘与地球仪合并.md`——仅头部加一行"Stellarium 星图选项作废，见切片-26"。

绝对不碰：`page/provider/*`、`page/entities/*`、`page/api/*`、`page/delegate/*`（A1 铁律）；既有 `StarField.kt` 不删不改（兜底可复现性依赖其种子 9173）；`GlobeController` 既有公开签名只许新增不许改参。

---

## 三、数据源与许可（本候选的关键风险区）

Tycho-2（主数据源，锚点已验证）：

1. 目录页：CDS VizieR I/259，`https://cdsarc.cds.unistra.fr/viz-bin/cat/I/259`。
2. 事实：2,539,913 行（页面 Records 字段，可作下载校验行数）；Høg, Fabricius, Makarov et al.；bibcode 2000A&A...355L..27H。
3. 许可两条并存，照实登记：CDS 页标 CC-BY-NC-3.0 IGO（署名 + 非商用）；NASA Open Data Portal 镜像页标 usa.gov/government-works。
4. 处理：取 CDS 分发副本，署名串固定「Source: ESA Hipparcos mission; Tycho-2 Catalogue (Høg et al. 2000), CDS I/259」，许可声明随数据页与 About 展示。
5. NC 边界如实记录：本 App 当前为非商用个人项目，此源可用；若日后商业化，必须整体换源、金字塔与实体层同源同换，禁止新旧混用。此条进项目数据台账，不许只写代码注释。
6. **分发边界**：Tycho 派生素材不进 APK、不进 CI artifact、不进仓库；做首启可选下载 + 许可确认页（见第 11.3）。理由：NC 数据随构建产物分发，会把"非商用"边界粘到 APK 上，边界最脏。

字段来源细分（逐条，禁混）：

7. 位置（RA/Dec J2000）、自行、BT/VT 星等、HD/HIP 交叉、Bayer/Flamsteed：全部来自 Tycho-2，一源到底。
8. 距离：Tycho-2 无视差。只对"星官成员星 + 最亮 150 颗"逐条注 Hipparcos 视差值（标 ESA 1997）；其余星显示"未收录"，不显示就对了，不编。
9. 光谱型/B-V：Tycho-2 无光谱型。只对精选子集逐条查证；B-V 由 BT-VT 换算或精选子集带出；其余星不着色温，按星等映射到暖白段。
10. 星名：中文古名/星官名取公版古籍（《史记·天官书》《晋书·天文志》《步天歌》），逐条带原文出处；IAU 官方星名（约 450 个）取 WGSN 名单，名称本身是事实数据；西名中文译名自拟，不抄任何有版权星表。
11. 星座边界/连线：边界为 IAU 划定事实（Davenhall & Leggett 1989，VizieR 条款非商用可标用）；连线一律自绘，只参考 IAU 图样不复制像素，署名"参考 IAU/Sky & Telescope"。
12. 分野表：公版古籍原文，出处逐条标（《史记·天官书》卷二十七）。

下载链路（数据代理未验证前不许导入）：

13. 从 CDS FTP（I/259）或 HEASARC W3Browse tycho2 抓取，校验行数 = 2,539,913。
14. 抽 3 行与 SIMBAD/已知值人工核对（如天狼 RA/Dec）。
15. 内置层：mag ≤ 6.5 实体星全量（约 9,100 行）。按需层：密度金字塔（6.5 < mag ≤ 11.5，按天区瓦片，单瓦片 ≤512KB）。
16. 金字塔必须可离线缓存、可断网兜底（兜底走既有 `StarField.kt` 程序化星空，不空白）。

---

## 四、数据 schema（落库结构，禁自造字段）

star 表（实体亮星，内置）：

1. id（主键，稳定编号）
2. tychoId（回源用）
3. raDeg / decDeg（J2000，Double，必填）
4. magVt（Double，可空；空则不显示星等行）
5. bayer（String?）、flamsteed（String?）
6. hip（Int?）、hd（Int?）
7. colorBv（Float?）、spectralType（String?）、distanceLy（Float?）——三者可空是有意设计，空字段不渲染对应行，避免编数据
8. constellation（String，88 星座之一，必填）

star_name 表（一星多名的字面层）：

9. starId → star.id；name；lang（zh-xingguan / zh-modern / en-iau）；source（出处原文）；isPrimary。

asterism 表（星官与星座**分表**，理由：成员判定与样式完全不同，合并会造成"一套数据两个语义"）：

10. asterism：id、name、system（xingguan / constellation）、nameZh、nameLatin?、memberCount、source、note。
11. asterism_member：asterismId、starId、memberIndex（如"井宿一"）、linkToNext。
12. constellation_line：fromStarId、toStarId、constellationId、style。

field_allocation 表（分野）：

13. id、asterismId、regionName（如"雍州"）、stateName（如"秦"，可空）、source（原文）、isVariant（异说 true 并写"一作"来源）。理由：州与国分层，因《天官书》本身同时有十二州与国两套映射。

体积预算：star+star_name <1.5MB 内置；asterism/constellation/field_allocation <150KB 内置；密度金字塔按需 <60MB、可删可重建。

---

## 五、② 尺寸 / 间距 / 圆角 / 字号

**总原则：字号不随缩放变小，只随缩放显隐。** 理由：`机制-观感UX验收-回执.md:29` 的静态门槛是"最小字号 14.0sp，<14sp 即 FAIL"，`audit_ux()` 扫全 `*.kt`。若用 10sp/11sp 星名，必然扫红。所以远处只显示最重要的一层名（28 宿），近处显示全部星官名，字号恒为 14sp，靠 alpha 连续淡出控制密度。

星点直径（**屏幕像素 px，非 dp**，随星等 Pogson 插值）：

1. mag ≤ −1.5 → 9px
2. mag 0 → 5px
3. mag 2 → 3px
4. mag 4.5 → 1.5px
5. mag 6.5 → 1px
6. mag > 6.5（金字塔层）→ 0.8px，不再画更小，用 alpha 补偿
7. 理由：星点是"光点"不是控件，尺寸单位必须用屏幕像素才能在任何 dpi 下保持同一张星图的观感；用 dp 会让高分屏上星星变大变糊。

线宽与样式：

8. 星座连线 0.8px，透明度 0.45，虚线不适用（实线）。
9. 星官连线 1.0px，透明度 0.55。
10. 分野边界 1.5px 虚线（dash 6 / gap 4）。
11. 参考网格：赤经线 0.8px / 赤纬线 0.8px；黄道线 1.0px；天赤道线 1.0px。
12. 理由：三套线必须能一眼分层——星座最淡（参考）、星官次之（内容）、分野最重（联动锚）。

字号（全部 ≥14sp，逐条给用处）：

13. 星官名 14sp（宋体，竖排）。
14. 二十八宿名 14sp（宋体，竖排，沿黄道带外圈）。
15. 星座名 14sp（无衬线，横排，拉丁名与中文名两行）。
16. Sheet 标题 16sp，Sheet 正文 14sp，出处小字 14sp（不降级，用 `textTertiary` 弱化而非缩字号）。
17. 理由：16/14 是在 14sp 门槛之上的最小可用阶梯，不新造 15sp 这类无出处字号。

间距与圆角（全部引既有常量，不新造）：

18. 星名标签距星点 8dp（`ComponentPalette.BUBBLE_GAP_DP` 同值）。
19. 可点标签触达 ≥48dp×48dp（`机制-观感UX验收-回执.md:44` UX-03：可点文件内 `size/minHeight/minWidth` <48dp 即 FAIL 候选）；视觉标签只有 14sp 高，热区用透明外扩到 48dp。
20. 顶部「星官/星座」竖排切换条：宽 48dp（热区），视觉条 28dp 宽。
21. 右上「天外/地上」同 48dp 热区。
22. Sheet 顶部圆角 20dp（`SHEET_RADIUS_DP`）。
23. 星名印式小方框圆角 2dp（刻意的"硬"，像印章，不套 16dp 卡片圆角）。
24. 列表 `spacedBy` 8dp（UX-03 下限）。
25. Sheet 三段高度 30% / 60% / 90%（`SHEET_DETENT_LOW/MID/HIGH`），关闭阈值 0.12（`SHEET_DISMISS_THRESHOLD`）。

宿主拆分的尺寸理由：

26. `GlobeScreenHost.kt` 实测 603 行 > 600（`机制-总控必读.md:157` 第 5 条：新增文件 ≤600 行是先决条件）。星空绘制落 `SkyCanvasRenderer.kt`（≤400 行，内部再拆三个私有 renderer），宿主净增 ≤5 行。
27. 理由：不拆则 S4 lint 与 oversized 债务棘轮必红，且"为过门禁先拆再加"是项目明确允许路径。

---

## 六、③ 色值（只引既有 token）

**分层原则（照既有先例，不叫"例外"，叫"分类"）：**

1. **UI 叠加层**（标签、连线、Sheet、按钮、网格、分野高亮）——只用 12 色 token + 石青增补。依据 `ComponentPalette.kt:16-66` 与 `落文-模块契约索引.md:63`。
2. **天文内容色**（星点本身的颜色、银河亮度）——单独 `SkySpecColor` 声明。依据既有先例：`GlobeTokens.kt:55-62` 的 `GlobeSpecSurface` 明确"地表着色常量（地貌数据色，不是 UI 配色）……12 色 token 约束的是界面叠加层；这里的值来自规格表格本身，属于渲染数据"。星色同构：来自 B-V 物理量，是数据驱动内容色，禁用于任何 UI 控件。
3. **派生色集中**：UI 层若需 token 之外的明度偏移，扩 `GlobeTokens`（不新建平行 palette），单测断言"派生值 = 基础 token 明度偏移可复现"。依据 `docs/落文-模块契约索引.md:77` 红线与 `GlobeTokens.kt` 既有结构。

### 6.1 UI 层逐条映射（写码照此，禁写 hex）

4. 夜空底 = `textPrimary`（墨色 token）压暗 40%，出口 `GlobeTokens.skyNightBase`；具体结果值由派生函数计算，不在源码与文档里写死。
   理由：墨色本身偏暖（R26 > G20 > B16），压暗后仍是暖墨，天然满足"暗处偏暖不偏冷、禁纯黑"；不用冷蓝黑裸值。
5. 地平暖辉带（地上观）= `brandCopper` 0.10 alpha，出口 `GlobeTokens.skyHorizonGlow`。
6. 星官连线 = `brandGold`，alpha 0.55。
7. 星座连线 = `textTertiary`，alpha 0.45。
   理由：星座是"辅助参考层"，云灰正是"辅助/占位"语义；且不抢星官的金。**不借石青**——石青在 `ComponentPalette.kt:54-59` 已登记为"Toast 提示态专用 + 沙盘敌方势力色块"，第三种用途会造成语义污染。
8. 星官名标签：字 `brandGold`，底 `bgOverlay` 0.45，边 1px `brandCopper`。
   理由：古铜更沉，符合"金是沉金不是亮金"；不用朱砂边框——朱砂语义是失败/删除/强调，常驻星名下会误读为错误态。
9. 选中星 / 分野填充 = `colorGlow`（rgba(212,165,74,.3)）；分野边 = `brandGold` 虚线。
10. 参考网格 = `textTertiary` alpha 0.12；黄道线 = `brandGold` alpha 0.30；天赤道 = `stoneBlue` alpha 0.30。
    理由：黄道是"天球主道"用金；赤道是"测量基准"用石青——石青此处用途是"基准线"，与 Toast/势力语义不同，需在切片-26 显式声明"基准线专用"，否则视为语义污染。
11. 禁用态 = `textTertiary` alpha 0.30（`DISABLED_OPACITY`）。
12. Sheet 全部走 `bgSurface` / `textPrimary` / `textSecondary` / `textTertiary` / `colorDivider`。

### 6.2 天文内容色 `SkySpecColor`（数据色，非 UI）

13. 星色四锚（按 B-V 连续插值）：早型 `#D8E4F0`（低饱和月白）→ 中间 `#FFFDF6`（纸白）→ 晚型 `#FFEED8`（暖白）→ 极晚 `#FFB37A`（橙红）。
14. 为什么"去蓝化"不是完全去蓝：真实早型星确实偏蓝，但纯蓝白（比早型锚更艳一档）在暖绢底色上会"跳"；降到低饱和月白，既保留物理辨识，又只作点缀（全天约 5% 星、且集中在最亮几颗），整体观感仍是"暖夜微芒"。
15. 无 B-V 的星：按星等映射到"纸白→暖白"段，不猜色温。
16. 银河：暖白核心 `#FFFDF6` alpha 0.10 + `brandGold` alpha 0.06 叠加；**冷蓝外围取消**，改暖灰 `textTertiary` alpha 0.04。
17. 尘粒：`textTertiary` alpha 0.15。
18. 流星：`#FFFDF6` 头部 + 尾迹 alpha 0.4 渐隐。

### 6.3 派生色出口（扩 GlobeTokens，逐条）

19. `skyNightBase` = darken(TEXT_PRIMARY, 0.40)，单测断言。
20. `skyHorizonGlow` = BRAND_COPPER at 0.10。
21. `skyStarWarm` = 星色四锚的暖端（仅供 UI 图例/Sheet 小圆点示意用；星点本体走 `SkySpecColor`）。
22. 理由：三个出口覆盖星空全部 UI 需求，不再散落。

### 6.4 硬禁

23. 禁 `#000000`、禁 `#FFFFFF`、禁 Material 默认色、禁第 14 个基础色。
24. 判据：`grep -rn "#[0-9A-Fa-f]{6}" ui/map/globe/` 只允许命中 `SkySpecColor.kt`（数据色单点声明处）与 `GlobeSpecSurface` 既有表；其余命中即 FAIL。

---

## 七、④ 全部状态（八态，逐条给表现与判据）

1. **默认（星空就绪）**：`skyBlend=1`，星点按当前 magLimit 连续裁剪；标签按 alpha 连续显隐；判据：任意缩放位置截图与"星等-密度曲线"一致，无整层突现。
2. **悬停（手指按住未抬起）**：被压星点外扩 1.3× 光晕，120ms（`DURATION_MICRO`）；判据：抬手回弹，无残留。
3. **按下（命中确认瞬间）**：60ms 放大到 1.6×，随后进入选中呼吸（1200ms 周期，与 `MARKER_PULSE_PERIOD_MS` 同值）；判据：放大与呼吸可分辨为两个阶段。
4. **加载（金字塔瓦片下载中）**：已有星照常显示，缺失天区用密度底图（低层）覆盖，不出现空白洞；顶部一条 1px `brandGold` 进度线（非菊花、非转圈）；判据：`UX-05` 关键词命中（loading/占位）。
5. **禁用（低端机降级）**：`skyBlend` 仍连续，但 magLimit 封顶 6.5、银河关闭、尘粒关闭、流星关闭；判据：功能不消失，只减层。
6. **空（无星表且未下载）**：程序化兜底星空（`StarField.kt` 种子 9173）+ 一行提示"星表未载，当前为示意星空"；判据：绝不空白、绝不崩。
7. **错误（数据校验失败）**：回退到内置 mag ≤6.5 层，Sheet 顶部一行"星表校验未过，已回退内置亮星"（`textAccent` 一次性，不常驻）；判据：`UX-06` 关键词命中（重试/兜底）。
8. **离线（无网）**：已缓存瓦片全可用；未缓存天区走低层底图；判据：断网清缓存后仍出完整星官/星座线框（线框内置）、亮星全在。

补一条跨态约束：

9. 任何态下 Sheet 关闭后，选中态与视角一字不差（沿用阅读页 Sheet 铁律）；判据：关闭前后两帧相机四元数与选中 id 逐位相等。

---

## 八、渲染规格（3 pass，不超 DrawBudget）

预算事实：`DrawBudget.kt:9` "手机端 ≤15 draw call；LOD0 ≤10"，`GlobeConfig.kt:126` `MAX_DRAW_CALLS=15`。9 层顺序会顶破，必须合并为 3 pass。

1. **星空底 pass（1 张离屏位图）**：夜空渐变 + 银河 + 密度底图（当前层与相邻层 alpha 连续混合）+ 参考网格，合成一张，只在视角/缩放变化时重绘，静止帧复用。
2. **线框 pass（1 条 Path）**：星座线 + 星官线 + 分野边 + 选中光环，合并为单条 Path 一次描边。
3. **星点 pass（1 次 sprite 批绘）**：实体星 + 尘粒 + 流星，预烘焙 4 档 sprite atlas（1px/3px/5px/9px 四档，按星等取图），禁逐帧 `drawCircle`。
   先例：`docs/落文-模块契约索引.md:23` "噪点材质契约＝单张 `paper_noise_tile.png` + 进程级缓存表，禁逐帧 `drawCircle`"，星空同构。

几何模型（本版修正，重要）：

4. **天球不是世界里的固定球，是跟随相机的背景球**。若做成固定球（球心=地球），相机推到 9R 就站到球外，会看到球壳外表面，模型本身是错的。
5. 星空因此**不做球壳网格**，直接做 2D 投影：星点方向由 RA/Dec + 恒星时 + 相机姿态算出屏幕坐标，画在 2D 叠加层。这样无 icosphere、无球面细分，draw call 天然极低。
6. `SKY_SPHERE_RADIUS = 900` 保留为**投影深度常量**（深度排序与遮挡判定基准），不是几何球半径。
7. 理由：软渲染（C3 裁决 JVM 软渲染）下，2D 投影比球壳网格省一个量级的顶点与像素运算，且精度更高。

视觉参数（逐条）：

8. 夜空渐变：天顶 `skyNightBase` → 地平 `skyNightBase` 叠 `skyHorizonGlow` 0.10，禁纯黑。
9. 银河：暗带掩膜 + 三层 fbm 噪声，opacity 0.25；核心暖白，外围暖灰。
10. 实体星光晕三层（1.0 / 0.35 / 0.12 半径倍率），四芒只 mag < 0.5（`STAR_SPIKE_MAG`），闪烁只 mag < 2.5（`TWINKLE_MAG`）、独立相位种子、0.6–1.4Hz、±8%。
11. 尘粒 32 颗 1px、alpha 0.15、缓慢漂移；流星 90–240s 随机、1.2s 划 8–14°、尾迹 0.4s。

双视角：

12. 天外观：赤道坐标 + 相机自由旋转，地球悬于中央，星空为背景。
13. 地上观：地平坐标，视场 60°（`GROUND_VIEW_FOV_DEG`），地平线下压暗，地平线上 5° 辉光（`OBSERVER_HORIZON_GLOW_DEG`），观测点金色地针，可开山影剪影。
14. 过渡：天外↔地上 800ms 相机飞行（`VIEW_SWITCH_MS`），地平翻转用 spring 收尾，禁 linear（美学总纲"禁用 linear"）。

---

## 九、交互、分野与连续缩放

### 9.1 进入星空的机制（本版修正）

1. 相机上限 6.0R → 10.0R（`GlobeConfig.kt:106`）。
2. **为什么必须放宽**：6R 时地球角径约 18.9°，在 60° 视场、1080px 高下约 340px，远达不到"缩成星点"的 40px；只放宽相机不够，还要下面这条。
3. 10R 之后不再推相机，改由手势驱动 `skyBlend: Float`（0→1 连续）：地球视觉半径从约 200px 连续缩到 40px，星空 alpha 连续上升；反向手势完全对称。
4. 为什么这样才对：`skyBlend` 是连续标量，没有枚举、没有阈值档，天然满足"线性缩放不设档"；且避免"相机退到无穷远"这种物理上做不到的事。
5. 40px 阈值（`SKY_ENTER_EARTH_DIAMETER_PX`）只是 `skyBlend` 的终点锚，不是开关。

### 9.2 交互状态机

6. 模式互斥：`GLOBE`（既有地球仪，回放/标记/手势全不动）、`SKY_GOD`（天外观）、`SKY_GROUND`（地上观）。
7. 模式内状态：`idle` → `selected(star|asterism|constellation|region)` → `sheet(open/close)`。
8. 手势：单指拖动——天外观旋转天球，地上观平移方位角；双指捏合——线性缩放（相机距离与 `skyBlend` 与 magLimit 三者同一条曲线）；点星——`StarPicker` 24dp 网格分桶、20dp 候选半径、重叠取最亮；点连线或名——高亮全组；长按空白——显隐全部名称；双指放大 ≥2×——进局部星图并按需下载该天区更深层。
9. 顶部竖排「星官」「星座」：400ms 交叉淡入，默认星官。
10. 右上竖排「天外」「地上」：800ms 过场。
11. 一键"落到分野地观此宿"：从星宿 Sheet 发起，地面站飞到该州代表城，天空锁定该宿。

### 9.3 连续 LOD（修正：不做连续细分网格）

12. 地球 LOD：三档网格（128/256/512）**全部常驻缓存**，`LodSelector` 输出从"离散 target"改为连续 `blendWeight`，渲染层按权重插值顶点位置。
13. 为什么不做连续细分 icosphere：`LodSelector.kt:13-25` 已有 `framesLeft` / `morphProgress` / `isMorphing` 的 morph 底座，扩展成连续权重成本低；重造连续细分网格要重写 `IcosphereMesh` 生成器，成本高一个量级且回滚难。
14. **诚实边界**：这条做到的是"视觉连续"（任意缩放位置有唯一混合比例），几何仍是三档。规格里写明这句，验收不得要求"几何连续"；若用户后续要求几何连续，另立缺陷升级，不在本候选冒充。
15. 星空侧天然无档：星点数量由 `magLimit = f(zoom)` 连续函数决定，金字塔层间 alpha 连续混合。
16. `DrawBudget` 需增一条显式条款：blend 过渡期两档同时绘制，临时上限 +1，否则 bench 会偶发红。
17. 旧 bench 中"LOD 三档切换"相关断言同步更新，不留测旧档位的死代码。

### 9.4 分野联动（双向）

18. 点星宿（28 宿之一）→ 天球高亮该宿成员星 + 连线；地球对应州域 `colorGlow` 脉冲（1200ms）+ 州名竖排。
19. 点地球州域 → 天球自动转向该宿并高亮。
20. 地上观联动：观测点可飞至分野代表城（例：井鬼→雍州→长安）。
21. 州域多边形：周代州/国近似边界，tier 3"示意"标注；与 18 朝疆域层互斥显示。
22. 异说（如"毕觜参之分，魏地"与"益州"并存）：主表取《天官书》十二州，异说以"一作"小字脚注，不并合。

---

## 十、⑥ 与其它模块接口

1. **帧快照**：`FrameSnapshot` 增 `sky: SkyFrame? = null`。`SkyFrame` 字段：`mode`（GOD/GROUND）、`skyBlend`、`cameraYawPitch`、`observerLatLon`（地上观）、`siderealTimeDeg`、`magLimit`、`pyramidMix`、`selectedId`、`horizonDipDeg`。
2. 为什么用标量与 id、不用星对象列表：一帧 700 颗若每帧 new 列表，软渲染 GC 抖动吃满 16ms；标量 + 索引零分配。
3. **控制器**：`GlobeController` 只新增两个方法 `enterSky(mode)`、`setSkyBlend(v)`，既有签名不改（`GlobeScreenHost.kt:603` 已消费其公开面）。
4. **人文条目**：星官/星座/分野走 D16 通用 `EntityProvider` 的 `astronomy` 类别（`docs/落文-模块契约索引.md:155`"其余类别走通用 EntityProvider，不加新 Provider"）。**禁止新建 StarProvider**，否则 P-05 双轨。
5. **科学数据**：星表自建 `StarStore`，与 D16 astronomy 条目按 `starId` 关联，不混表。理由同"山川坐标归 LandscapeStore、城邑归 PlaceStore"（`索引:167`）。
6. **视图归属**：星空是 D17 壳体第四种视图（2D / 3D 沙盘 / 3D 地球 / 天球星空），走同一 `PlaceMapActivity` 单壳，不新立模块、不加第五顶层入口。
7. **搜索**：星名进既有搜索源 D1~D7，不新建搜索页（P-11/D25 已定"禁独立搜索页"）。
8. **离线**：复用 `AssetStatus / TextureCache / DegradationLevel`，不新建下载器；星空资产与地球资产**分两个独立 fallback 域**（见第 11.3）。

---

## 十一、⑤ 边界与降级

1. **无数据**：程序化兜底（`StarField.kt` 种子 9173）+ 明示"示意星空"。
2. **断网**：线框全内置可用，亮星全在，暗星缺则用低层密度底图。
3. **超长文本**：星名/古籍原文超长时 Sheet 内截断到 3 行 + 展开，展开不跳视角；判据：星名 40 字不破版。
4. **低帧率**：连续 3 帧 >16ms（`FRAME_BUDGET_MS`）即降层——先关流星、再关尘粒、再降 magLimit 到 9、最后关银河；降级不改变 UI 布局与选中态。
5. **无权限**：地上观需定位，无定位权限时默认取"长安"并把观测点标为"示意"，不弹权限墙。
6. **NC 商业化**：商业化即整体换源，金字塔与实体层同源同换。
7. **Tycho 下载不可达**：整候选回退候选 B，本候选标作废留档（不删）。
8. **降级域隔离（易漏点）**：`OfflineFallback.degradation()` 现按 `TextureKind.entries` 全量算；若不拆域，星空瓦片缺失会把地球纹理误判成程序化档。必须拆"地球域 / 星空域"两套独立算档。
9. **枚举扩展**：`TextureKind` 追加 `SKY_DENSITY_L9`、`SKY_DENSITY_L11` 会触发 `when` 穷尽与 `check-member-consumers.py --changed` 扫描，提交前必须扫全消费方。

---

## 十二、⑦ 验收判据（12 条判据 + 4 项 bench，逐条可测）

1. 长安→天球连续缩放录屏逐帧检查：无网格换面跳变、无星点突增、无某一帧整层淡入。
2. 「天外」「地上」切换 800ms；地上观选长安，天顶区星象与 J2000 恒星时公式一致（验收单给 3 个时刻的预期方位角/高度角，误差 ≤1°）。
3. 点井宿 → 地球雍州脉冲 + 州名浮现；点雍州 → 天球井鬼高亮；双向闭环各有截图。
4. 星官/星座切换无残留、默认星官；Sheet 三名字段齐全（有则显示，无距离的星不显示距离行）。
5. 断网清缓存 → 程序化兜底星空，不空白不崩。
6. 下载金字塔后局部放大：9–11.5 等暗星连续长出来；内置行数以导入日志校验 = mag≤6.5 全量，不许抽样入库。
7. 全屏无 `#000000`、UI 层无第 14 色；`grep` 判据见第 6.4。
8. 四项 bench 全过（见下）。
9. 数据台账含 Tycho-2 许可声明与 NC 边界记录。
10. 切片-26 落档、切片-20 头部作废标记落档。
11. 八态逐态截图（第 7 节），每态判据可复现。
12. Sheet 关闭前后相机四元数与选中 id 逐位相等。

bench 四项（并入既有 `ui/map/globe` bench 测试类，不新建 CI job，理由：B13 慢环节 + 红率）：

13. 星空投影 700 颗 <4ms。
14. 天球整帧 <16ms。
15. 双视角切换峰值 <24ms。
16. 点击命中 <5ms。

---

## 十三、⑧ 与全 App 既有资产一致性对照

对照 12 类卡片（`规格登记册.md:40` S-25：全部走 `CardBase`、圆角 16dp、五态、空态三变体）：

1. 星点 Sheet = 复用既有半屏 Sheet 容器与三段高度，不自造 Sheet；Sheet 内列表行走 `CardBase` 系列，不新造容器。
2. 星名标签方框**不套** 16dp 卡片圆角，用 2dp 印式硬边——已在第 5.23 显式声明差异，理由：标签是"星图注记"不是内容卡片。
3. 空态复用 D29 空态三变体之一（"无数据"型），文案走既有空态组件，不自绘空态。

对照 77 图标（24×24dp 线性矢量、1.5dp 线宽、圆角端点、无填充无阴影无渐变）：

4. 星空**不新增** `ic_*` 图标资产；「星官/星座」与「天外/地上」用文字竖排，不用图标。理由：星图本身已是强图形，叠图标会乱。
5. 若后续必须加图标（如"定位到观测点"），复用 D22 `ic_map_*` 域现有图标，不自造。

对照 12 色 token（`ComponentPalette.mainTokens` 12 项 + `STONE_BLUE` 唯一增补）：

6. UI 层全部映射见 6.1，零裸 hex；石青仅用于"天赤道基准线"一处，并在切片-26 声明用途。
7. 天文内容色走 `SkySpecColor`，与 `GlobeSpecSurface` 同类，禁用于任何 UI 控件。
8. 派生三色集中在 `GlobeTokens`，单测断言可复现。

对照动效与组件语言：

9. 星点呼吸 1200ms 与 `MARKER_PULSE_PERIOD_MS` 同值；点触 120ms 取 `DURATION_MICRO`；Sheet 弹起取 `SHEET_SPRING_*`；一律 spring，禁 linear、禁 Material 默认转场。
10. 加载不用菊花，用 1px `brandGold` 进度线——与"三个金点"同类语言（都是"细金线/金点"而非旋转器），差异已声明。

对照既有无障碍与 UX 九维：

11. 热区全 ≥48dp（第 5.19–5.21）；`spacedBy` ≥8dp；字号全 ≥14sp（第 5.13–5.16）。
12. UX-05 加载/空态关键词、UX-06 错误/离线关键词在第 7 节逐态命中，不留 WARN。

---

## 十四、契约常量（`SkyParams.kt`，13 条 + 1 条工程常量，逐条带理由）

1. `SKY_PROJECTION_FAR = 900.0`——投影深度常量（原"星空球半径 900"改名，因模型改为 2D 投影；数值沿用切片-19:46 口径不另立）。
2. `SKY_ENTER_EARTH_DIAMETER_PX = 40f`——`skyBlend` 终点锚；地球缩到 40px 时星空全显。
3. `SKY_TRANSITION_MS = 600L`——星空淡入与地球缩点共用；与既有回放过渡节奏一致。
4. `VIEW_SWITCH_MS = 800L`——天外↔地上相机飞行。
5. `MAG_DEPTH_LIMIT = 11.5f`——用户拍板值，写死不许改。
6. `MAG_BRIGHT_ENTITY = 6.5f`——实体星与密度层分界；肉眼极限与 Tycho-2 覆盖起点重合。
7. `PYRAMID_LEVELS = 2`——密度两级（9 等、11.5 等）；级数固定可测，级间 alpha 线性混合实现无档感。
8. `STAR_SPIKE_MAG = 0.5f`——四芒只给 mag < 0.5（约 16 颗）；全天最亮才配四芒。
9. `TWINKLE_MAG = 2.5f`——闪烁只给 mag < 2.5；暗星闪烁会成噪点。
10. `FIELD_PULSE_MS = 1200L`——分野脉冲，与 `MARKER_PULSE_PERIOD_MS` 同值。
11. `METEOR_INTERVAL_MIN_MS = 90_000L` / `METEOR_INTERVAL_MAX_MS = 240_000L`——流星随机区间，两值入常量便于测试注入。
12. `GROUND_VIEW_FOV_DEG = 60.0`——地上观视场；手机抬头舒适视场，再宽边缘畸变。
13. `OBSERVER_HORIZON_GLOW_DEG = 5.0`——地平辉光带宽，与 `TERMINATOR_BAND_DEG` 同语言。

另增一条工程常量（非视觉）：

14. `SKY_BLEND_FULL_ZOOM = 12.0f`——`skyBlend` 达 1 时的等效缩放标量（相机 10R 之后再走 2R 等效）；理由：给"连续"留出可见的过渡段，不在一帧内完成。

---

## 十五、风险、派单与自检

风险 7 条：NC 边界；morph 超预算 → 交叉淡化并明说；Tycho 链路不可达 → 回退 B；命中难 → 磁吸最近 3 颗；州域边界误差 → tier 3 + "示意"；流星/闪烁拖帧 → 降层；用户要全量距离 → 拒绝并说明"只有精选子集有可靠出处"。

派单边界（4 写 + 1 验，文件域互不重叠）：

1. 契约单：`astro/contract/**`（SkyParams / SkyFrame / SkySpecColor / StarProjector + 纯 JVM 单测 ≥10 条）。必须先绿。
2. 接线单：`GlobeConfig`、`GlobeScene`、`FrameSnapshot`、`LodSelector`、`GlobeController`、`GlobeTokens`、`TextureKind`。改公开成员，报告须带消费方扫描。
3. 数据单：`StarStore`、`dao/`、`entities/`、`assets/astro/**`、许可页文案。
4. 渲染交互单：`astro/layer/**`、`astro/pick/**`、`host/SkyCanvasRenderer.kt`、双视角、分野联动。
5. 验收单：独立代理逐条对照第 12 节，不复用写码代理。

自检：

6. 单项：八项逐项有节可指（见头部索引），每条判据带 `文件:行` 或常量名回源。
7. 整体：与 `机制-总控必读.md`（R/F/S/N）、`模块契约索引.md`（§0/§1/§1.3/§1.5/§2/§3.8）、`规格登记册.md`（S-19/S-25/档位）三处交叉，无自相矛盾。
8. 本版相对上一稿改了什么：① 色值从 6 个裸 hex 收口为 token + `SkySpecColor` 数据色；② 补 ②尺寸节、④八态表、⑧一致性对照；③ 天球模型由"固定球壳"改为"跟随相机的 2D 投影"，连带改 `SKY_SPHERE_RADIUS` 语义与进入机制（相机 10R + `skyBlend` 连续）；④ 连续 LOD 从"连续细分网格"降为"三档常驻 + 连续权重"，并写明视觉连续/几何三档的边界；⑤ 渲染 9 层压成 3 pass；⑥ 补 `GlobeConfig` / `FrameSnapshot` / `GlobeScreenHost` 三处硬阻断的改动点。
9. 未验证：连续权重在软渲染下的真实帧耗（待 CI bench）；Tycho 瓦片链路可达性与校验行数（待数据代理）；`SkySpecColor` 四锚在暖底上的实际观感（待帧复核）。
