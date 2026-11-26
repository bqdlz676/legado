锚点卡：docs/锚点/B13-heightMap解码.md

> 证据三件套留档：本报告交付于证据三件套规则上线之前，E1/E2/E3 未回填（回填等于伪造证据）；已挂号 Q-207 待重判。

> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-B13 heightMap 字符串无 PNG→栅格解码通路

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`；main，HEAD `c3a2248`；缺陷条目 `docs/问题清单.md:61`（B13）。
- 任务范围：**只做 B13**。独占文件域：
  - 新增 `app/src/main/java/io/legado/app/ui/map/crossing/terrain/RasterDecoder.kt`
  - 新增 `app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingRasterBridge.kt`
  - `app/src/main/java/io/legado/app/ui/map/crossing/engine/CampaignJsonParser.kt` **仅改 heightMap 取值一处**
  - 新增 `app/src/test/java/io/legado/app/ui/map/crossing/shell/CrossingRasterBridgeTest.kt`
  - 本报告。
- 红线遵守：沙箱无 Android SDK / kotlinc / gradle，**未跑本地编译 / 单测**；**未做任何 git 写操作**；未碰 `CampaignController.kt`、`VictoryChecker.kt`、`ui/main/search/`、`ui/tools/`。工作区里 `CampaignController.kt` / `VictoryChecker.kt` / `VictoryCheckerTest.kt` / `autoTask/*` 的既有改动属其他在飞代理，原样保留未动。

## 一、缺陷回源与实测数据形态

### 1.1 条目

- `docs/问题清单.md:61`：
  > | B13 | `heightMap` 只有字符串，无 PNG→栅格解码通路 | U12-F12：`CampaignJsonParser.kt:55`；`shell/` | 🔴 | 解码失败回落合成场并置降级标志 |
- 改前现状：`CampaignDefinition.heightMap: String?` 只存字符串（`CampaignModels.kt:127`），全仓没有任何把该字符串变成 `HeightRaster` 的通路；`CampaignController.load(json, raster, …)`（`CampaignController.kt:791`）的 `raster` 入参在壳层无人装配，缺省即合成兜底场，沙盘永远平地形。

### 1.2 资产实测（不假装解码，逐项目盘点）

`grep -rn "heightMap" app/src/main/assets/map/campaigns/` 仅命中 1 场战役：

| 形态 | 位置 | 实测内容 | 本通路是否消费 |
|---|---|---|---|
| ① 文件名串 | `assets/map/campaigns/gaixia.json:13` `"heightMap": "gaixia.png"` | 资产基名，约定在 `map/` 目录查找 | 是，经注入的 `RasterDecoder` 解码 |
| ② 内嵌灰度栅格 | 同文件 `terrain.heightPixels` | 扁平数组 **9216 = 96×96**，0..255，行优先；配 `bbox:[116.3357, 32.7793, 118.0643, 34.2207]`（西/南/东/北）、`minEl:11`、`maxEl:153` | 桥接备好入口 `fromInlinePixels`；解析 / 模型层接线见 §七交接 #2 |
| ③ Web base64 | `assets/map/china_height.js` `CHINA_H_B64` | base64 解码后 1048576 字节 = 1024×1024 原始灰度字节；仅 legacy web 使用，战役 JSON 不引用 | 否 |

现存 PNG 资产实测（`find app/src/main/assets -name "*.png"`）：灰度 DEM 仅 `assets/map/china_height.png`，`file` 识别为 **PNG 1024×1024 8-bit grayscale**；**`gaixia.png` 在 assets 中不存在**。即形态①在当前资产下必然走兜底——本单如实标注，不伪造一张解码成功的栅格。

### 1.3 WIP 半成品评估（`wip/engine-terrain-pack`，只当线索）

| WIP 文件 | 评估 | 处置 |
|---|---|---|
| `terrain/RasterDecoder.kt` | `fun interface` 契约方向正确：纯 JVM、失败返回 null、不抛回引擎；与引擎层零 android import 的既有分层一致 | **采纳骨架**，补全契约注释（行 0 = 北缘 / 列 0 = 西缘、灰度口径、缓存归属 D13） |
| `shell/CrossingRasterBridge.kt` `fromDecoder` + 合成兜底 + 种子 1207L | 装配顺序与兜底分支正确（decoder null / 名空 / null 结果 / 异常 → 合成场） | **采纳**，异常兜底保留 |
| 同上 `fromElevation(elevation, focus, gridN)` | 把 sandbox `TerrainElevation` / `DemTerrainElevation` 类型引进 crossing 桥接，破坏「引擎层零 sandbox 引用」红线；且 sandbox 采样归 sandbox 域，与 D13 缓存口径重叠 | **不采纳**。crossing 桥接只吃 `HeightRaster`，不反向依赖 sandbox |
| WIP 桥接缺重采样 | 解码器返回的 1024×1024 / 96×96 栅格直接交给 `gridN=32` 的 `TerrainMap`，`pixel()` 越界钳边只会采到源图左上角 32×32，棋盘错位 | **本单补 `resample` 双线性重采样**到 gridN×gridN（§2.3） |
| WIP 无内嵌栅格入口 | 形态②（96×96 内嵌数组）在 WIP 中无通路 | **本单补 `fromInlinePixels`**（§2.4） |

## 二、改动清单（逐处，改后行号以工作区为准）

### 2.1 新增平台解码器契约 `terrain/RasterDecoder.kt:17-25`

```kotlin
fun interface RasterDecoder {
    fun decode(assetName: String): HeightRaster?
}
```

- 纯 JVM 函数式接口；Android 薄适配（Context.assets + BitmapFactory）由壳层注入，引擎 / 单测层不引 Android 类型。
- 契约：失败 / 缺资产 / 退化 / 内部异常一律返回 null，不抛回引擎；灰度 0..255、行优先、行 0 = 北缘、列 0 = 西缘；0 ↔ 0m、255 ↔ 7000m，与 `HeightRaster.DEM_MAX_METERS=7000.0`、sandbox `DemTerrainElevation` 同口径。

### 2.2 解析器 heightMap 取值收口（`CampaignJsonParser.kt:56-58`，唯一改动行）

- before（`c3a2248`）：
  ```kotlin
  val heightMap = obj.get("heightMap")?.takeIf { it.isJsonPrimitive }?.asString
  ```
- after：
  ```kotlin
  // B13：heightMap 统一走 strOrNull 守卫——非 primitive（对象 / 数组 / JsonNull）回落 null，
  // 空白串裁掉后回落 null；与 B4 其余字段同一兜底口径，不把类型错位抛到壳层。
  val heightMap = strOrNull(obj, "heightMap")
  ```
- 行为差异（写明）：原写法对数字 primitive 会 `asString` 成 `"123"` 保留；新写法经 `strOrNull`（`CampaignJsonParser.kt:350-352`）只接受字符串 primitive，且 `trim().ifBlank { null }`。非字符串 / 空白一律 null，与 B4 收口方向一致。本单**未动** `parseTerrain`、`strOrNull` 本体及其余任何行。

### 2.3 新增桥接装配 `shell/CrossingRasterBridge.kt:46-103`

- `fromDecoder(decoder, assetName, gridN)`（`:46-56`）：名空白 / decoder 缺失 → 合成场；`decode` 包 try/catch，异常与 null 结果 → 合成场；成功 → `resample`。
- `resample(source, gridN)`（`:76-103`）：双线性插值重采样到 `n×n`（n 钳制 16..128，与 `HeightRaster.synthetic` 合法区间一致）；源退化再兜合成场；灰度夹紧 0..255；方向口径不变（行 0 北、列 0 西）。
- `syntheticFallback`（`:106-107`）：两层正弦缓丘谷地，`degraded=true`；种子 `RASTER_SEED=1207L`（`:110`），与 controller 兜底种子 `42L` 区分便于追溯。

### 2.4 内嵌栅格入口 `fromInlinePixels(width, height, pixels, gridN)`（`:64-68`）

- 先经 `HeightRaster.create` 校验尺寸 / 空数组 / 长度不符；退化 → 合成场；合法 → 重采样。供后续解析层把 `terrain.heightPixels` 接入（§七 #2）。

### 2.5 坐标映射口径（防棋盘错位）

- 栅格行优先 `data[row * width + col]`；**行 0 = 北缘（纬度最高），列 0 = 西缘（经度最小）**，与 `GeoProjection.toGeo`（`GeoProjection.kt:36-42`：`lat = center.lat - (fy-0.5)*extentLatDeg`，row 增大向南）同向，不旋转 / 不翻转。
- 目标栅格 gridN×gridN 与 `GeoProjection` 同网格；单元中心经纬度由投影给出，像素与推演单元一一对应。
- 源栅格自带 bbox 时：`u=(lng-west)/spanLng`、`v=(north-lat)/spanLat`，越界钳边缘（口径同 `DemTerrainElevation.heightAt` `:47-48`）；bbox 对齐属解码器 / sandbox 职责，本桥接只在像素空间重采样。

### 2.6 新增单测 `CrossingRasterBridgeTest.kt`（6 条，纯 JVM）

| # | 测试 | 断言 |
|---|---|---|
| 1 | `解码器成功：栅格重采样到 gridN 且不降级` | 64×64 平场 200 → 32×32，`degraded=false`，逐像素 200 无漂移 |
| 2 | `重采样保持东西向灰度梯度：西缘暗、东缘亮` | 梯度源场重采样后列 0 < 列 31，同行单调不减 |
| 3 | `解码器缺失或资产名空白时回落合成场并置降级` | decoder null / 名 `"   "` / 名 null 三例：`degraded=true`，32×32 |
| 4 | `解码器返回 null 或抛异常时回落合成场且不抛穿` | null 结果 / 抛 `IllegalStateException` 两例：降级、32×32、1024 像素 |
| 5 | `内嵌像素合法：96 乘 96 缩放到 gridN 且不降级` | 96×96 平场 128 → 32×32，`degraded=false`，逐像素 128 |
| 6 | `内嵌像素尺寸不符或为空时回落合成场并降级` | 长度 100 / null 像素 / 0×0 三例：降级、边长为正、`size=width*height` |

## 三、grep 回证（代码真实写入）

```text
$ grep -n "fun interface\|fun decode" app/src/main/java/io/legado/app/ui/map/crossing/terrain/RasterDecoder.kt
17:fun interface RasterDecoder {
25:    fun decode(assetName: String): HeightRaster?

$ grep -n "fun fromDecoder\|fun fromInlinePixels\|fun resample\|RASTER_SEED\|syntheticFallback" app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingRasterBridge.kt
46:    fun fromDecoder(decoder: RasterDecoder?, assetName: String?, gridN: Int): HeightRaster {
48:        if (decoder == null || name.isEmpty()) return syntheticFallback(gridN)
54:        } ?: return syntheticFallback(gridN)
64:    fun fromInlinePixels(width: Int, height: Int, pixels: IntArray?, gridN: Int): HeightRaster {
66:        if (source.degraded) return syntheticFallback(gridN)
76:    fun resample(source: HeightRaster, gridN: Int): HeightRaster {
78:        if (source.degraded) return syntheticFallback(n)
106:    private fun syntheticFallback(gridN: Int): HeightRaster =
107:        HeightRaster.synthetic(seed = RASTER_SEED, gridN = gridN, degraded = true)
110:    private const val RASTER_SEED: Long = 1207L

$ grep -n "strOrNull(obj" app/src/main/java/io/legado/app/ui/map/crossing/engine/CampaignJsonParser.kt
58:        val heightMap = strOrNull(obj, "heightMap")
348:        strOrNull(obj, key) ?: default
350:    private fun strOrNull(obj: JsonObject, key: String): String? {

$ grep -c "@Test" app/src/test/java/io/legado/app/ui/map/crossing/shell/CrossingRasterBridgeTest.kt
6
```

- 禁用模式扫描（`assertTrue(true)` / `@Ignore` / `kotlinc` / `gradle`）：三文件 0 命中。
- `git diff CampaignJsonParser.kt` 仅 `:56-58` 一处 hunk，其余行未动。

## 四、二阶 / 三阶影响推演

### 4.1 直接效果

- `heightMap` 字符串第一次有了到 `HeightRaster` 的装配通路；失败路径全部落合成场 + `degraded=true`，`CampaignController.degraded`（`CampaignController.kt:93`）随之置真，壳层顶栏「演示降级」标识可亮起。

### 4.2 二阶连带

- **网格对齐**：`TerrainMap`（`TerrainMap.kt:42-43`）按 `gridN` 格索引栅格；重采样后栅格边长 = gridN，消除了 WIP 直传大栅格时 `pixel()` 钳边只采左上角的错位。`MapCanvas` 绘制循环（`CrossingShell.kt:208-218`）按 `raster.height/width` 遍历，边长对齐后不再越界。
- **解析口径**：`heightMap` 非字符串 / 空白 → null；全仓下游目前无第二处读 `definition.heightMap`（`grep heightMap` 仅 model 字段、parser、legacy `SimEngine.kt:105` 三处），无连带破坏面。
- **内存占用**（IntArray，4 字节 / 像素，自算）：32×32 = 4096 B；96×96 = 36864 B；1024×1024 = 4194304 B（4.0 MiB）。桥接只持有目标 n×n 栅格；源栅格在 `resample` 返回后即失去引用，可被回收。Android 适配器侧 Bitmap 必须 `recycle()`（照 sandbox `DemTerrainLoader.kt:42` 既有做法），否则 1024² ARGB_8888 位图常驻约 4 MiB 叠加在 IntArray 之上。
- **加载耗时**：桥接内重采样为 O(n²)，n=32 即 1024 次双线性采样，毫秒级可忽略；主要耗时在平台 PNG 解码（1024² BitmapFactory），属适配器职责，本单未度量。

### 4.3 三阶与 D13 缓存关系

- **D13（`docs/问题清单.md:123`：DEM 被沙盘 / 回放各解码一次，无实例缓存）**：本桥接是**无状态装配层**，自身不缓存栅格；缓存键（资产名 → `HeightRaster` / `TerrainElevation`）应建在解码器外壳或 D13 统一缓存层，避免沙盘 `DemTerrainLoader`、crossing 桥接、回放三处重复解码 1024² PNG。本单不越界实现缓存，只把接口留成「解码器返回一次、桥接纯函数装配」的可缓存形态。
- **反复切仗**：无缓存时每场战役首次载入各解码一次；合成兜底场无解码成本。
- **维护成本**：新增两个概念（解码器契约 / 装配桥），命名与 sandbox `DemTerrainLoader` / `DemTerrainElevation` 平行；后续接手者可对照 sandbox 既有 DEM 链路理解。

## 五、反例与边界（假设本方案错误时的兜底核查）

| # | 边界 | 实现兜底 |
|---|---|---|
| 1 | 资产名 null / 空白 / 纯空格 | `fromDecoder` 先 `trim`，空串直接合成场（测试 #3） |
| 2 | 解码器为 null（壳层未接入） | 同 #1，合成场，不 NPE |
| 3 | 解码器内部抛任何 `Throwable` | try/catch 收为 null，不抛穿到 UI（测试 #4） |
| 4 | 解码器返回 null（资产缺失 / 损坏） | 合成场（测试 #4） |
| 5 | 栅格 0×0 / 空数组 / 长度与宽高不符 | `HeightRaster.create` 内部合成场；桥接见 `degraded` 再兜一层（测试 #6） |
| 6 | 灰度越界（<0 / >255） | `create` 构造夹紧；`resample` 输出 `coerceIn(0,255)` |
| 7 | 源栅格非方形 / 1×1 | 双线性插值 x/y 方向独立取整钳边；n=1 分支防除零（`:83`、`:88`） |
| 8 | 源栅格自带退化标志 | `resample` 首行检查 `source.degraded`，不把退化场带进推演网格 |
| 9 | 解码成功但尺寸远大于 gridN | 重采样降采样，不把 1024² 直接塞进 32 格棋盘 |
| 10 | JSON `heightMap` 被写成对象 / 数组 / JsonNull | parser `strOrNull` 回落 null，不抛 `IllegalStateException` |

## 六、认知成本评估

- 新增抽象 2 层：`RasterDecoder`（平台能力契约）+ `CrossingRasterBridge`（纯 JVM 装配 / 兜底 / 重采样）；引擎 model/engine 仍不感知解码细节。
- 命名对照：sandbox 域 `DemTerrainLoader`（Android 适配）↔ crossing 域 `RasterDecoder` 实现；sandbox `DemTerrainElevation`（采样核心）↔ crossing `HeightRaster`（栅格核心）。无新造术语。
- 残留认知债：内嵌 `heightPixels` 的 bbox / minEl / maxEl 尚未进模型（§七 #2），接手者需知道桥接入口已备而解析侧未接线。

## 七、交接（发现但未改，属其他单子域）

1. **Android 解码器实现未落地**：需在 shell 宿主侧写 `RasterDecoder` 适配（Context.assets 打开 `map/<assetName>`，`BitmapFactory.Options.inPreferredConfig=ARGB_8888`，取 R 通道，解码后 `bitmap.recycle()`，失败返回 null），样式照 `sandbox/DemTerrainLoader.kt:30-51`；并在调用 `CampaignController.load(json, raster, …)` 处注入。当前 `CrossingShell.kt:50` 仅 `CampaignController.demo()`，真实战役载入接线属壳层组装单。
2. **内嵌 `terrain.heightPixels` 未进战役定义**：`TerrainDef` / `CampaignDefinition`（`CampaignModels.kt:106-131`）无字段承载 96×96 数组与 bbox/minEl/maxEl；parser `parseTerrain` 未解析该数组。桥接 `fromInlinePixels` 已就绪，待模型 + parser 接线后即可消费形态②。注意 legacy `help/sim/TerrainAnalyzer.kt:247-260` 有自己一套 heightPixels 口径（按 minEl/maxEl 线性折米），两套栈收敛另立单。
3. **`gaixia.png` 资产缺失**：要么补资产到 `assets/map/`，要么接线 #2 后直接用内嵌 96×96 栅格；当前该战役走合成兜底场。
4. **D13 缓存**：解码器结果按资产名缓存，与沙盘 DEM 实例共享，避免三处重复解码。
5. **bbox 感知采样**：内嵌栅格自带 bbox 与 `terrainFocus` 范围可能不一致；当前桥接只做像素空间重采样，地理对齐依赖投影口径；若后续出现栅格 bbox 与焦点范围错位，需在采样层按 bbox 归一化（口径见 `DemTerrainElevation.heightAt:47-51`）。

## 八、未验证项（照实声明）

- 沙箱无 Android SDK / gradle / kotlinc：**本单代码未编译、单测未在本地运行**；6 条单测与三文件的编译结论只认云端 CI。
- 未真机 / 未出包：Bitmap 解码耗时、1024² 内存峰值、双线性重采样视觉效果均未实测。
- 形态①端到端未验证：当前 assets 无 `gaixia.png`，解码器成功路径只由伪解码器单测覆盖，真实 PNG 解码链路待 #1 适配器落地后随 CI / 真机验证。
- 基线口径：任务书基线 3039 tests；本单新增 6 条，未在本地核对全量绿红。
- 未核对 `china_height.js` base64 字节序与 PNG 灰度是否逐像素一致（legacy web 资产，不在本通路）。
