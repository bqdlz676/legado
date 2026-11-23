# 验收报告 · U10 DEM 地形接线（缺陷 D-APK-1）

- 验收对象：缺陷 **D-APK-1**——3D 沙盘地形当前是平地形，真实 DEM 资产 `assets/map/china_height.png` 进包却零读取。
- 基线：branch `main`，HEAD `98ee6d0`。
- 验收方式：**只读静态验收 + 纯 JVM 推演核对**；沙箱内**未编译、未跑单测/bench、未上真机**（硬红线 1：禁本地 gradle/kotlinc 编译；测试真跑走云端 CI）。
- 文件域：`ui/map/sandbox/**`（含新增 DEM 采样器与 Android 侧薄适配）+ `app/src/test/java/io/legado/app/ui/map/sandbox/**`；未触碰 `ui/map/replay`、`ui/map/globe`、`help/**`。

## 一、最终结论

**静态接线完成：DEM 栅格已有 Kotlin 读取方，沙盘默认地形由 Flat 改为注入 DEM 采样器；纯 JVM 采样核心零 Android 依赖，配 9 个单测用例 + 1 个 bench。编译结果、单测/bench 运行结果、真机隆起观感均未验证，待云端 CI 复验。**

判定依据：`china_height.png` 路径在 Kotlin 侧由 `DemTerrainLoader` 经 `assets.open` 读取；`SandboxScene.elevation` 不再带 `Flat` 默认值，生产场景由壳层注入 DEM；空网格 / 越界 / NaN 全部兜底 0.0，不抛异常、不出 NaN。违规点（FAIL 级）：**静态扫描未发现**。观察级缺口见第六节。

## 二、文件清单

### 新增（main）

| 文件 | 职责 |
|---|---|
| `app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainElevation.kt` | 纯 JVM DEM 采样核心：灰度网格 + 经纬度包围盒 → 归一化 → 双线性插值 → 0–255 映射 0–7000m → 沙盘世界单位；越界钳制、空网格 / 退化包围盒 / NaN 兜底 0.0。 |
| `app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainLoader.kt` | Android 侧薄适配：`BitmapFactory` 解码 assets `map/china_height.png`，取 R 通道灰度成 `IntArray` 交核心；延迟加载，失败 / 退化回落 `TerrainElevation.Flat`，降级日志全程只打一次。 |

### 新增（test，纯 JVM）

| 文件 | 内容 |
|---|---|
| `app/src/test/java/io/legado/app/ui/map/sandbox/DemTerrainElevationTest.kt` | 9 个用例：0↔0m、255↔7000m/63 单位、中点双线性、越界钳制、空网格兜底、尺寸不符兜底、退化包围盒兜底、NaN 兜底、ByteArray 无符号字节口径。 |
| `app/src/test/java/io/legado/app/ui/map/sandbox/DemTerrainBenchTest.kt` | 1024×1024 栅格采样 10 万次 bench，阈值 avg < 3µs/op。 |

### 修改（main）

| 文件 | 改动 |
|---|---|
| `app/src/main/java/io/legado/app/ui/map/sandbox/SandboxScene.kt` | `elevation` 参数去掉 `= TerrainElevation.Flat` 默认值，改为必注入；注释说明由壳层注入 DEM，解码失败时注入 Flat。 |
| `app/src/main/java/io/legado/app/ui/map/sandbox/SandboxScreen.kt` | 新增 `rememberSandboxScene()`（`LocalContext.current` 取上下文，`remember` 缓存一次）；`defaultScene(context)` 构造场景时注入 `DemTerrainLoader.load(context)`。 |

### 修改（test，显式补 Flat 注入）

| 文件 | 改动 |
|---|---|
| `app/src/test/java/io/legado/app/ui/map/sandbox/SandboxSceneTest.kt` | 2 处 `SandboxScene(...)` 构造补 `elevation = TerrainElevation.Flat`。 |
| `app/src/test/java/io/legado/app/ui/map/sandbox/SandboxBenchTest.kt` | 2 处 `SandboxScene(...)` 构造补 `elevation = TerrainElevation.Flat`。 |

## 三、采样口径公式

**输入**：行优先灰度网格 `data[r * width + c]`（0..255，行 0 = 北缘）；包围盒 `Bbox(south, north, west, east)`。

1. 归一化（越界钳到 [0,1]）：
   - `u = (lng - west) / (east - west)`
   - `v = (north - lat) / (north - south)`
2. 栅格坐标：`gx = u * (width - 1)`，`gy = v * (height - 1)`；四角 `(x0,y0)/(x1,y1)` 钳在网格内，双线性加权得灰度 `gray`。
3. 灰度 → 米制：`meters = gray / 255 × 7000.0`（0 ↔ 0m，255 ↔ 7000m，与 `crossing/terrain/HeightRaster.DEM_MAX_METERS`、`globe/terrain/FbmNoise` 7000m 口径一致）。
4. 米制 → 沙盘世界单位：`units = meters × 0.009`（旧版沙盘垂直夸张 K=0.009；7000m ↔ 63 世界单位，对照 300 单位盘面与 12 单位光柱，隆起保持克制）。
5. 兜底：栅格空 / 尺寸不符 / 包围盒退化 / 坐标 NaN/Inf → 返回 `0.0`；结果非有限值 → 返回 `0.0`；全程不抛异常。

**默认包围盒**：104–122°E / 24–42°N（legacy `HG`，`docs/落文-3D沙盘逆向.md` 第二节；即 `china_height.png` 覆盖范围）。

**资产实测**（静态解码统计，未在 App 内读取）：PNG 1024×1024，8-bit grayscale，灰度 min 0 / max 118 / mean 22.21，为真实高程场而非纯色假图。

## 四、grep 证据

### 1. `china_height` 在 Kotlin 侧有读取方

```
$ grep -rn "china_height" app/src/main/java --include=*.kt
app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainElevation.kt:81:  /** 高程图默认包围盒：104–122°E / 24–42°N（legacy HG，china_height.png 覆盖范围）。 */
app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainLoader.kt:21:  private const val ASSET_PATH = "map/china_height.png"
```

读取链路：`DemTerrainLoader.kt:32` `context.assets.open(ASSET_PATH)` → `:36` `BitmapFactory.decodeStream` → `:41` `getPixels` → `:46` `DemTerrainElevation.create(...)`。

### 2. `TerrainElevation.Flat` 不再作为沙场默认

```
$ grep -rn "Flat" app/src/main/java/io/legado/app/ui/map/sandbox/
DemTerrainLoader.kt:13   注释：回落 [TerrainElevation.Flat]
DemTerrainLoader.kt:58   return TerrainElevation.Flat          // 仅失败兜底分支
SandboxScene.kt:19       注释：解码失败时注入 [TerrainElevation.Flat]
TerrainElevation.kt:19   object Flat : TerrainElevation { ... } // 接口兜底实现定义处
```

`SandboxScene.kt:20` 构造参数为 `val elevation: TerrainElevation`（无默认值）；生产默认场景 `SandboxScreen.kt:199` 注入 DEM 加载结果。`Flat` 仅剩：接口兜底定义、加载器失败回落、测试显式注入。

### 3. 纯 JVM 类零 Android / Compose import

```
$ grep -n "import android\|androidx" DemTerrainElevation.kt DemTerrainElevationTest.kt DemTerrainBenchTest.kt
（无输出）
```

Android 依赖（`Context` / `Bitmap` / `BitmapFactory` / `Log`）只出现在 `DemTerrainLoader.kt` 与壳层 `SandboxScreen.kt`；采样核心可在普通 JVM 单测直接构造。

### 4. 无颜色 hex 硬编码、无 Material 默认组件

- 新文件内 `0xFF` 仅为字节 / 通道位掩码（`and 0xFF`），无颜色十六进制；未引入 `MaterialTheme` / `colorScheme` / Material 默认组件，壳层配色仍只走 `SandboxTokens`。

## 五、单测 / bench 清单

### `DemTerrainElevationTest.kt`（fixture：2×2 与 1×2 手算网格，包围盒 24/42/104/122）

| # | 用例 | 核对点 | 手算期望 |
|---|---|---|---|
| 1 | `灰度 0 映射 0 米 0 世界单位` | 西北角灰度 0 | 0.0 |
| 2 | `灰度 255 映射 7000 米与 63 世界单位` | 东南角灰度 255 | 7000 × 0.009 = 63.0 |
| 3 | `中点双线性插值取四边加权平均` | 正中点 (33°N, 113°E) | 灰度 127.5 → 31.5 单位 |
| 4 | `越界经纬度钳到边缘像素不抛异常` | 北西 / 南东 / 北东三组越界坐标 | 分别钳到 0 / 63 / 63 单位 |
| 5 | `空网格构造为退化实例且高度恒 0` | width/height=0、data=null | `degraded=true`，恒 0.0 |
| 6 | `数组长度与尺寸不符时兜底 0` | 2×2 配 3 元素数组 | `degraded=true`，恒 0.0 |
| 7 | `退化包围盒兜底 0 不抛异常` | south>north | `degraded=true`，恒 0.0 |
| 8 | `NaN 坐标兜底 0 不产出 NaN` | lat/lng 为 NaN | 0.0；正常点非 NaN |
| 9 | `ByteArray 按无符号字节取灰度` | 补码 -1 字节 = 255 | 北缘 63.0，南缘 0.0 |

### `DemTerrainBenchTest.kt`

- 网格：1024×1024 确定性梯度场；采样 10 万次，经纬在包围盒内确定性遍历。
- 阈值：**avg < 0.003 ms/op（3µs/op）**。
- 依据：60fps 单帧预算 16.7ms；每帧贴地采样的势力顶点 / 河道样条 / 箭头落点合计不过数百点，单点 3µs 仍有百倍余量；该阈值为宽松上限，只防插值退化到毫秒级，真机上线前再压一轮。

## 六、未验证项（静态接线，均未真跑）

1. **编译未验证**：未执行 gradle / kotlinc（硬红线）；类型推断、重载解析（`IntArray?` / `ByteArray?` 双入口）、Compose `remember(context)` 调用形式待云端 CI 编译确认。
2. **单测 / bench 未真跑**：9 个用例与 10 万次采样 bench 的通过情况、实测耗时未验证；报告内期望数值为手算推演值。
3. **DEM 资产实解码未验证**：真机上 `BitmapFactory` 对该 8-bit 灰度 PNG 的解码配置、R 通道取值、像素行列方向（行 0 = 北缘）未在设备上核对；若解码后通道次序与预期不符，山脊会与城池错位，需真机首帧目检。
4. **降级路径未验证**：资产缺失 / 解码失败时回落 Flat、日志只打一次的行为未在异常环境实测。
5. **隆起观感未验证**：0.009 夸张系数下 7000m ↔ 63 单位的视觉高度、与光柱（12/6 单位）/ 雾气（260 单位）的比例关系未上真机目检。
6. **渲染 pass 贴地尚未落地**：当前沙盘壳层只有背景 Canvas + 手势 / 时间轴；河流样条、行军箭头的逐点贴地高度采样属渲染 pass 职责，本批次只把 `scene.elevation` 实例备好，未接绘制层。

## 七、跨模块问题与交接

### 1. 对 U11 回放（`ui/map/replay`）：当前不共用地形，后续若贴地应共享同一实例

- 静态扫描：`replay/` 全目录无 `elevation` / `Elevation` / `heightAt` / `china_height` / `Terrain` 引用，`ReplayGeo.kt` 无高程概念，回放棋子 / 箭头目前按平面坐标推演。
- 交接建议：若 U11 回放箭头 / 棋子后续需要贴地，应由回放层注入沙盘同一个 `TerrainElevation` 实例（或抽成共享地形源），**不要第二次解码 `china_height.png`**，避免两套采样口径 / 两份栅格内存。
- 现状结论：本批次未改 replay 任何文件（文件域红线），此条仅登记给 U11 排期核对。

### 2. globe / crossing 高程口径对齐情况

- `crossing/terrain/HeightRaster.kt` 与 `globe/terrain/FbmNoise.kt` 均为 7000m 满幅口径；本批 DEM 核心沿用同一 `DEM_MAX_METERS=7000.0`。
- 世界单位换算各模块尺度不同：globe 用 `meters/7000 × 2.0`（球面位移），沙盘沿用旧版 `meters × 0.009`（300 单位盘面）。两者不可互换，本批未改动 globe / crossing（文件域红线）。

### 3. 贴地链路确认（任务点 5）

- `PowerSector.sampleSurface(elevation)`（`PowerSector.kt:64-65`）逐顶点取高度；调用方传入的是 `scene.elevation` 同一注入实例，不存在第二套高程源。势力色块贴地走该实例；河流 / 箭头贴地待渲染 pass 接线时同样取 `scene.elevation`。

### 4. 降级标志未在 UI 明示

- `DemTerrainElevation.degraded` 已暴露，加载器在退化时回落 Flat；但壳层目前没有「地形降级为平面」的可见提示。规格未要求提示文案，登记为观察项，后续如需明示可在信息卡 / 顶栏补一行只读状态。

## 八、自检记录（交付前逐项过）

- [x] 纯 JVM 核心（`DemTerrainElevation.kt`）零 Android / Compose import。
- [x] `china_height.png` 在 Kotlin 侧有读取方（`assets.open` 链路完整）。
- [x] `Flat` 不再是 `SandboxScene` 构造默认值；测试显式注入 Flat。
- [x] 空网格 / 尺寸不符 / 退化包围盒 / NaN / Inf / 越界经纬度均有兜底，不抛异常、不出 NaN。
- [x] 未新增颜色 hex、未引入 Material 默认组件。
- [x] 文件域未越界：未改 `replay` / `globe` / `help`。
- [x] 未执行本地编译、未做 git 写操作。
- [ ] 编译 / 单测 / bench / 真机观感——未验证，见第六节。
