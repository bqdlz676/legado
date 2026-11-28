# 修复报告：Sheet 判据 4/12 与星点 ↔ 分区 code 反查索引（判据 3 · N1）

- 日期：2026-11-28
- 基线：`origin/main` = `64d6a7e`（开工前 `git fetch origin main`，本地 main 与之一致）
- 文件域（严格互斥，未越界）：
  - `app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt`
  - `app/src/main/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndex.kt`（新建）
  - `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/AsterismLayer.kt`
  - `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt`
  - `app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt`（新建）
  - `app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt`（新建）
  - `app/src/test/java/io/legado/app/ui/map/globe/astro/contract/SkySheetDismissStateTest.kt`（新建）
- 禁改清单遵守：未改 `app/src/main/java/io/legado/app/ui/map/globe/astro/data/StarCatalog.kt`、`app/src/main/java/io/legado/app/ui/map/globe/astro/data/StarStore.kt`、`app/src/main/java/io/legado/app/ui/map/globe/astro/data/StarStoreProvider.kt`、`app/src/main/java/io/legado/app/ui/map/globe/astro/input/SkyInputHandler.kt`、`app/src/main/java/io/legado/app/ui/map/globe/host/GlobeScreenHost.kt`、`app/src/main/java/io/legado/app/ui/map/globe/GlobeController.kt`、`app/src/main/java/io/legado/app/ui/map/globe/host/SkyCanvasRenderer.kt`、`screenshot/` 与他人报告；既有测试 `SkyContractTest.kt`、`SkyBenchTest.kt`、`SkyZoomContinuityTest.kt`、`StarCatalogPyramidTest.kt`、`StarCatalogBlendCoverageTest.kt` 未触碰。

## 一、开工守则回执（sha256 前 12 位 + 三条逐字引用）

### 1. `docs/机制-总控必读.md` — sha256 前 12 位 `2d7dbc8f14dc`

- `docs/机制-总控必读.md:3`：「**开工第一读就是本文件（2026-11-26 用户口令「把总控必读定为以后第一读」，门禁 ㊻ 机械判）**」
- `docs/机制-总控必读.md:48`：「`写 → grep 回证 → git add <限定文件域> → commit → push → 云端 CI`」
- `docs/机制-总控必读.md:168`：「事故七：**写完没人调＝死链路**（2026-11-28 星空天象层：`enterSky`/`setSkyBlend`/`draw(store)` 编译与单测全绿、CI 三绿，全仓零生产调用方，`scene.sky` 恒 null，渲染器首行 return，8920 颗星一颗不显示）」

### 2. `docs/落文-规矩总纲.md` — sha256 前 12 位 `d4468a629b7c`

- `docs/落文-规矩总纲.md:18`：「1. 锚点强制可回源：每条结论必须能回到原文行号/切片/文件。」
- `docs/落文-规矩总纲.md:20`：「3. 子 AI 禁止自行摘要——只读小段原文照做，靠索引+完整覆盖核对。」
- `docs/落文-规矩总纲.md:134`：「**D2 写码/验收代理一次性用完即弃，禁止唤醒复用（最严口径）。**」

### 3. `docs/机制-子代理必读.md` — sha256 前 12 位 `9f36d8ec0df4`

- `docs/机制-子代理必读.md:13`：「- **文件域独占**：只碰任务书白名单里的文件；工作区里别人未提交的改动（`git status` 里不是你的）**不许删、不许改、不许提交**。」
- `docs/机制-子代理必读.md:18`：「2. **grep 回证**：每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。没贴 grep 的改动视为未改。」
- `docs/机制-子代理必读.md:71`：「1. **Kotlin 语法预检必跑**：`python3 data_scripts/kotlin-syntax-lint.py <你的文件>` 必须 PASS。」

## 二、交付事项与行号锚点

### 事项 1：判据 4 补齐（Sheet 卡片）

#### 1.1 星名三级回退，任何一路不留空白 / 不显示 "null"

- 回退链实现：`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:103` `resolveSheetTitle(...)`。
  - 第 ① 路 星名索引命中：今名 → 星官名 → 西名，取首个可读字段（`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:110`）；
  - 第 ② 路 实体星表编号：HD → HIP → TYC → 拜耳 / 弗兰斯蒂德 → 星表 #主键（`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:126` `catalogDesignation`）；
  - 第 ③ 路 极简坐标串兜底：目录按主键命中后取 RA/Dec 度串（`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:115-120`）；
  - 第 ④ 路 末路：主键编号 `星表 #<id>`；未选中返回 `未选中星体`（`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:122`）。
- 字面 `"null"` / 空白串剔除：`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:93` `readableOrNull`，去空白并忽略大小写判 `"null"`。
- 卡片接线：`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:199` 标题改走回退链；`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:221` `BasicText` 不再带 `?:` 空态分支（回退链保证非空）。
- 测试：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt`
  - 今名优先：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:44`
  - 今名缺失回落星官名：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:50`
  - 星名全空回落 HD 编号：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:56`
  - 星名实体皆缺坐标串兜底、不含空白与 `null`：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:61`
  - 未选中空态：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:71`
  - 字面 `null` 串视为空：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:76`
  - 空白星名字段回落坐标串：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:82`
  - 无实体无目录主键编号末路：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:99`

#### 1.2 距离哨兵 100000.00 显式抑制

- 规格口径选择：§十四.4「无距离不显示距离行」，故**命中哨兵不渲染该行**（不另造「距离未知」行）；判据出处 `docs/验收-星空天象层.md:85`（CSV 205 行距离取 `100000.00`，既有 `isFinite()` 守卫拦不住有限哨兵值）。
- 具名常量：`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:83` `DISTANCE_SENTINEL_LY = 100_000.0`，注释写明为什么集中一处（魔法数不散落、DAO 就位后复用同一判据）。
- 守卫：`app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:86` `isValidDistanceLy`（null / 非有限 / 哨兵一律 false）；行渲染接线 `app/src/main/java/io/legado/app/ui/map/globe/astro/ui/SkySheet.kt:239`。
- 测试：`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:90`（哨兵、null、NaN 不渲染；有限正常值与零距放行）。

#### 1.3 Sheet 关闭快照 / 状态测试

- 见事项 2 测试 `app/src/test/java/io/legado/app/ui/map/globe/astro/contract/SkySheetDismissStateTest.kt:21`、`app/src/test/java/io/legado/app/ui/map/globe/astro/contract/SkySheetDismissStateTest.kt:47`：关闭后 `selectedId` 清空；宿主挂载条件 `snapshot.sky?.selectedId?.let { ... }`（`app/src/main/java/io/legado/app/ui/map/globe/host/GlobeScreenHost.kt:219`）不成立 → 卡片不可见。本单未改宿主文件，测试在门面层复现同一挂载门控。

### 事项 2：判据 12 补齐（关闭只清 selectedId、不动相机）

- 实现口径（后令优先）：关闭链路仅 `selectStar(null)`，无任何相机姿态 / 距离写回；既有实现本就如此（`GlobeController.selectStar` 只拷贝 `selectedId`，未触碰姿态，见禁改文件 `app/src/main/java/io/legado/app/ui/map/globe/GlobeController.kt:189-191`）。本单补齐此前缺失的关闭前后快照比对测试。
- 测试：`app/src/test/java/io/legado/app/ui/map/globe/astro/contract/SkySheetDismissStateTest.kt`
  - `app/src/test/java/io/legado/app/ui/map/globe/astro/contract/SkySheetDismissStateTest.kt:21` `关闭Sheet只清selectedId 相机yawpitchzoom逐位不变`：进入星空 → 设姿态 `(25.0, -10.0)` → 选中 `7L`；快照 yaw / pitch / 距离比；走宿主 `onDismiss` 同款调用 `selectStar(null)`（宿主调用点 `app/src/main/java/io/legado/app/ui/map/globe/host/GlobeScreenHost.kt:224`）；断言 yaw、pitch、zoom（距离比）三元组逐位相等，`selectedId == null`。
  - `app/src/test/java/io/legado/app/ui/map/globe/astro/contract/SkySheetDismissStateTest.kt:47` `关闭后选中态回落初始 与新鲜控制器空选中口径一致`：关闭后选中态与新鲜控制器空选中同为 null 口径。

### 事项 3：判据 3 · N1 缺口——starId ↔ 分区 code 双向反查索引

- 新索引：`app/src/main/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndex.kt`（纯 JVM，零 Android / Compose 依赖）。
  - 正向 code → 成员集：`app/src/main/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndex.kt:31` `membersOf(code)`；
  - 反向 starId → code：`app/src/main/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndex.kt:34` `codeOf(starId)`；
  - 装载期一次性建表、查询期只读不 new 列表：`app/src/main/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndex.kt:44` `build(...)`；空表回落 `app/src/main/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndex.kt:37` `empty()`。
  - 双向一致口径：一个 starId 只归属一个 code；跨 code 重复时后装载覆盖（脏行处置见第四节反例）。
- 真实调用点（非死链路）：
  - 星官层 `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/AsterismLayer.kt`：
    - 装载重建索引 `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/AsterismLayer.kt:55`；
    - 反查入口 `codeOfStar` `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/AsterismLayer.kt:72`；成员集 `membersOfCode` `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/AsterismLayer.kt:75`。
  - 星座层 `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt`：
    - 线段装载后重建 `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt:62` → `rebuildNameIndex` `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt:86`（建表 `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt:93`）；
    - 反查入口 `constellationOfStar` `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt:97`；成员集 `starsOfConstellation` `app/src/main/java/io/legado/app/ui/map/globe/astro/layer/ConstellationLayer.kt:100`。
- 测试：`app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt`
  - code → 成员非空、逐颗回查同一 code：`app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt:24`
  - 双向一致、不串宿：`app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt:35`
  - 未知 code 空列表 / 未知星 null：`app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt:46`
  - 重复成员去重：`app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt:53`
  - 空映射空索引：`app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt:60`；`empty()` 与空构建等价：`app/src/test/java/io/legado/app/ui/map/globe/astro/data/SkyNameIndexTest.kt:69`。

## 三、口径冲突待裁定（未自作主张改口径，两处原文原样列出）

### 冲突 A：关闭后「选中 id 逐位相等」 vs 「关闭即取消选中（置 null）」

- 规格原文 1：`docs/星空天象层-实施规格-合并定稿.md:217`（§七 跨态约束）：「Sheet 关闭后选中态与视角一字不差；判据：关闭前后相机四元数与选中 id 逐位相等。」
- 规格原文 2：`docs/星空天象层-实施规格-合并定稿.md:322`（§十四.12）：「Sheet 关闭前后相机四元数与选中 id 逐位相等。」
- 实现 / 后令口径：`docs/验收-星空天象层.md:147`：「① 选中 id 关闭后置 null，与规格 §七 末段「选中态一字不差 / 选中 id 逐位相等」口径不符——实现选择了关闭即取消选中；」；`docs/方案-剩余缺陷施工图-星空天象层.md:514`：「关闭只清 selectedId，相机与姿态一字不动（§十四.12）」。
- 本单处置：按任务书后令口径实现——关闭只清 `selectedId`、不动相机；测试证明相机三元组逐位相等、选中态回落 null。两种口径（保留选中 id / 关闭即取消）请调度层裁定后再回改测试断言。

### 冲突 B：相机状态「四元数」 vs 「yaw/pitch 双标量」

- 规格原文：`docs/验收-星空天象层.md:148`：「备注：代码内相机状态是 `CameraYawPitch`（yaw/pitch 双标量），规格文字称「四元数」；当前无四元数结构，快照比对口径需在测试落地时统一。」
- 实现现状：`app/src/main/java/io/legado/app/ui/map/globe/astro/contract/SkyFrame.kt` 内 `CameraYawPitch(yawDeg, pitchDeg)` 双标量；仓内无四元数结构。
- 本单处置：测试按既有双标量结构比对 yaw / pitch，zoom 用 `scene.distanceRatio`；未新增四元数类型。是否引入四元数姿态归一化由裁定结论决定。

### 口径说明（非冲突，记录实现选择）

- 任务书回退第②路「按 RA/Dec 命名规则自动生成『星表编号』」：规格定稿未固定编号格式。本单取实体可给的跨表主键（HD / HIP / TYC / 拜耳 / 弗兰斯蒂德）作为可读编号，实体也缺时回落 `星表 #<主键>`；第③路才是 RA/Dec 坐标串。若裁定要求第②路必须由坐标直接派生编号（如坐标位号式命名），需回改 `catalogDesignation` 与回退顺序。

## 四、二阶 / 三阶影响与最强反对者反例

### 4.1 二阶三阶影响

- 内存：双向索引在装载期各存一份 `code → List<Long>` 与 `Long → code` 装箱映射；内置锚点成员规模（`asterism_members.csv` 10 条成员级）与 88 星座线段规模下，额外内存为百条量级，可忽略；若后续并入金字塔层全量星表，需在装载点评估映射条数（见交接项 3）。
- 构建耗时：索引只在 `loadGroups` / `loadLines` 装载期建一次，查询期无分配、无遍历；不进每帧热路径。
- 卡片观感：回退链把原先「星名未录入」纯空态换成可读编号 / 坐标串；坐标串采用 `RA xx.xx° Dec ±yy.yy°` 极简格式，不挤占三名字段行（三名字段仍按 §十四.4 有则显示、无则不渲染）。距离哨兵行静默消失，不再出现 `100000.0 光年` 这种占位值上屏。
- 三阶维护：索引口径集中在 `SkyNameIndex` 一处；未来 DAO 就位后，成员映射来源从内置 CSV 换成 Room 查询，索引重建入口不变。

### 4.2 最强反对者反例与处置

- 反例 1：一颗星同时属两个星官（跨宿重复成员）。按当前实现后装载 code 覆盖前者，`codeOf` 只返回一个 code。处置：索引注释已标明该口径；若数据层确认存在多宿星，应在导入链路去重或在索引内改存 `List<String>`，本单不预判扩型。
- 反例 2：`selectedId` 指向金字塔层增量索引位（非 L0 主键）。`store.byId` 返回高位偏移编码，`raAt/decAt` 越界回落 0.0，坐标串会出现 `RA 0.00° Dec +0.00°` 兜底。处置：坐标串仅作末路观感兜底；增量星的名称 / 编号主路径仍走实体与星名表，不把 0 坐标当真值展示给用户。
- 反例 3：星名字段全为空白串或字面 `null` 文本。`readableOrNull` 已剔除；测试 `app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:76`、`app/src/test/java/io/legado/app/ui/map/globe/astro/ui/SkySheetFallbackTest.kt:82` 钉住该分支。
- 反例 4：距离值为 `Infinity`。既有 `isFinite()` 守卫保留，`isValidDistanceLy` 先判有限再判哨兵，两条守卫串联不互吞。

## 五、跨模块交接项（交给 host / astro-data 单）

1. **层装载接线（host / renderer 域，本单未改）**：`app/src/main/java/io/legado/app/ui/map/globe/host/SkyCanvasRenderer.kt` 当前未调用 `asterismLayer.loadGroups(...)` / `constellationLayer.loadLines(...)`；索引随这两个装载入口重建。host 单把星官组 / 星座线段从 `StarStore` 喂进层后，反查索引自动生效；在此之前 `codeOfStar` / `constellationOfStar` 返回 null、`membersOfCode` 返回空列表（不崩）。
2. **点选第一跳闭环（N1 正向链）**：拾取命中星 id 后，应经 `AsterismLayer.codeOfStar(starId)` 解析所属星官 code，再 `select(code)` 接通天球高亮 → `FieldAllocationLayer` 州域脉冲链路；调用编排属 host / 输入域，本单只提供反查入口。
3. **全量星表规模评估**：若金字塔 L1/L2 全量并入后再建双向索引，需复核装箱映射条数与装载耗时；必要时把 `Long → code` 改成原生 long 键结构。
4. **DAO 就位后复核**：`StarStore.entityById` 由 Room 提供后，回退第②路编号与距离行应拿真实实体值；哨兵抑制常量在导入链路归一化方案（验收 N2 备选：导入时把 `100000.00` 归一为 null）落地后可评估是否下放到数据层。
5. **口径裁定回灌**：第三节两处冲突裁定后，需同步更新本报告与 `SkySheetDismissStateTest` 断言口径（选中 id 是否保留、姿态比对是否改四元数）。

## 六、自测机检记录

- Kotlin 语法预检：`python3 data_scripts/kotlin-syntax-lint.py` 对 7 个改动 / 新增 `.kt` 全部 PASS（`KOTLIN-LINT: PASS（7 个文件）`）。
- 孤儿公开入口：`python3 data_scripts/check-orphan-entries.py --list` → 扫描声明 19 个；无外部引用 0 个；复跑判定 `ORPHAN-ENTRIES: PASS（扫描声明 19 个，孤儿 0）`。
- 测试函数名：17 个用例名逐字核查，不含小数点。
- 锚点规范化：`python3 data_scripts/check-anchor-normalize.py`；本报告内代码 / 文档锚点全部全路径化（`app/src/.../Foo.kt:行号` 形式）。仓内既有他人报告 `docs/修复-D43D45-降级分域与宿主拆分.md` 仍存历史裸基名锚点（非本单文件域，未改）；本单新增文档不引入新的裸锚点。
- 沙箱纪律：未做任何本地编译；编译判据以云端 CI 为准。
