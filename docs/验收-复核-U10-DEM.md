# 验收复核报告 · U10 DEM 地形接线（缺陷 D-APK-1，第二轮独立复核）

- **验收角色**：独立验收 AI，只审不改。本轮未改任何源码 / 测试 / 他人文档（本报告除外），未做 git 操作，未执行本地 gradle / kotlinc 编译，未跑单测 / bench，未上真机。
- **复核对象**：`app/src/main/java/io/legado/app/ui/map/sandbox/`（`DemTerrainElevation.kt`、`DemTerrainLoader.kt`、`SandboxScene.kt`、`SandboxScreen.kt`）与该目录测试；旁证复核 `app/src/test/java/io/legado/app/ui/map/replay/ReplayTerrainTest.kt`（同一 DEM 双重载的编译面）。
- **背景**：缺陷 D-APK-1 修复包经历两轮——写码交付 → CI 报编译错（`import kotlin.math.isFinite` 非法，commit `dd13e9a`）→ 另一代理修复并推送。本报告为修复后的独立复核；前一轮验收代理因审「编译不过的包」被终止，其结论未被沿用。
- **CI 现状声明**：修复提交刚推、CI 未回。凡「编译是否转绿 / 单测是否通过 / bench 是否达标 / 真机观感」一律记入第七节「需 CI / 真机定论项」，本报告不猜 PASS。

---

## 一、总结论

**部分 PASS（静态核对）。**

- 静态链路成立：`china_height.png` 在 Kotlin 侧有唯一读取方；`SandboxScene.elevation` 已改为必注入；纯 JVM 采样核心零 Android 依赖；采样公式（归一化 → 双线性 → 0–255↔0–7000m → ×0.009 世界单位）与逆向口径逐条手算一致；空网格 / 尺寸不符 / 退化包围盒 / NaN / 越界钳制兜底闭环；非法 `kotlin.math.isFinite` 顶层 import 已清零；沙盘目录 4 个 `SandboxScene(` 测试构造点与 1 个生产构造点均已补 `elevation` 实参。
- **FAIL 嫌疑 1 条（待 CI 单测源集编译终判）**：`ReplayTerrainTest.kt:215` 仍向 `DemTerrainElevation.create(0, 0, null, bbox)` 传裸 `null`，落在 `IntArray?` / `ByteArray?` 两个互不构成子类型的可体重载之间，按修复报告自述的 Kotlin 重载解析规则，存在 Overload resolution ambiguity 高嫌疑；修复包只在沙盘测试内做了类型标注，漏了这一处跨包测试。
- 编译结果、单测 / bench 实跑、真机隆起观感、首帧耗时与内存实测：**均未验证**。

**FAIL 条数：1（嫌疑级，需 CI `compileAppDebugUnitTestKotlin` 终判；若该点编译通过则降级为观察项）。**

**回归面漏调用点：沙盘域内无漏网**（`SandboxScene(` 全部 5 处构造点均已注入 elevation）；另发现 1 处跨包测试的裸 null 漏网（见第四节 B-1）。

---

## 二、判据表（规格原句 → 行号 → 结论 → 证据）

### 2.1 规格与契约原句逐条对照

| # | 判据原句（出处，逐字引用） | 代码落点（文件:行） | 结论 | 证据 / 复核说明 |
|---|---|---|---|---|
| 1 | 切片-06 §一 L7：「把 `powers_poly.js` 的每个多边形**贴在地形表面**——用 `THREE.ShapeGeometry` 按多边形顶点采样地形高度，生成一张贴合地表起伏的半透明色块。」 | `PowerSector.kt:64-65` `sampleSurface(elevation)` 逐点 `elevation.heightAt(...)`；`SandboxScene.kt:20` 持有注入实例 | PASS（接线层）/ 观察（消费层） | 采样接口已按注入实例逐顶点取高；但沙盘主源集内目前**无 `sampleSurface` 调用方**，势力色块绘制 pass 尚未落地（沿用前轮验收 §6.6 登记项），本批只备实例。 |
| 2 | 切片-06 §三 L64：「每条河用 `CatmullRomCurve3` → `TubeGeometry` 画成**液态金线**，管径随河谷宽窄自动变化（**从 DEM 读河床宽度**）。」 | `RiverLine.kt:42-59` 样条；`RiverLine.kt:62-69` 管径 | 观察（部分未落地） | 样条与宽度系数已在模型层；河道贴地高度采样属渲染 pass 职责，本批未接；管径「从 DEM 读河床宽度」当前仍走 `widths` 系数曲线，未读 DEM。不属本批 U10 接线红线，登记待渲染 pass 核对。 |
| 3 | 原文-09 L12（手机版）：「高度图 \| 512×512 灰度 PNG」；原文-06 L56：「**高度图从 PNG 读**。地形分析层所有输出都是相对地形」 | `DemTerrainLoader.kt:21` `ASSET_PATH = "map/china_height.png"`；`:32` `assets.open`；`:36` `decodeStream`；`:41` `getPixels`；`:45` `create(...)` | PASS | 资产静态复核（Python zlib 解码，未走 App）：`china_height.png` 1024×1024、8-bit grayscale（colortype 0）、非交错；灰度 min 0 / max 118 / mean 22.21，为真实高程场而非纯色假图。全 main 源集仅此一处读取该 PNG。 |
| 4 | 缺陷 D-APK-1 定义（`交付-APK-98ee6d0-静态自检.md:45-49`）：「3D 沙盘地形是平地形，真实 DEM 未被消费」；修法：「等经纬度采样 + 双线性插值，0–255↔0–7000m」「无数据回落 Flat」；影响口径「3D 沙盘地形数据是真实 DEM，**不许回退成假数据**」 | `DemTerrainElevation.kt:75` `DEM_MAX_METERS = 7000.0`；`:78` `VERTICAL_EXAGGERATION = 0.009`；`:67-68` 米制 / 世界单位换算；`TerrainElevation.kt:19-21` `Flat` 兜底；`DemTerrainLoader.kt:58` 失败回落 `Flat` | PASS | 全 main 源集 `TerrainElevation` 实现仅 `Flat` 与 `DemTerrainElevation` 两个；沙盘 / 回放域未引用 `FbmNoise` / `HeightRaster` 造丘；兜底出口是 Flat（基准面 0.0），不是噪声假地形。 |
| 5 | 逆向文档 §二 L35：`HG = { w:104, e:122, s:24, n:42, px:1024 }`；§三 L52-59：双线性四点加权，`h/255*TOP`（现版 7000），`y = h*K`（现版 0.009）；L137 / L147：「1024×1024 真实 DEM……上限 7000 m」「0–255 ↔ 0–7000 m」 | `DemTerrainElevation.kt:81` `DEFAULT_BBOX = Bbox(24.0, 42.0, 104.0, 122.0)`；`:47-48` 归一化；`:50-57` 栅格坐标与四点；`:63-65` 双线性；`:67-68` 映射 | PASS | 归一化方向（经度自西向东、纬度自北向南，行 0 = 北缘）与逆向 §二 L38 现版线性口径一致，未回退墨卡托；常量 7000 / 0.009 / bbox 四值逐项吻合。 |
| 6 | 契约索引 1.5 E L93：「**3D 地形隆起**：高程决定高度、覆盖决定颜色 / 微地形」 | `DemTerrainElevation.kt:38-70` `heightAt`；`SandboxScene.kt:20` 注入 | PASS（高度通道）/ 观察（颜色通道） | 高度通道已通；覆盖颜色 / 微地形（森林绒布质感、雪线等）属渲染 pass，本批未涉及。 |
| 7 | 契约索引 1.5 G5 L106：「好看不牺牲性能……内存≤180MB、**首开≤800ms**」；G6 L107：「UI 域无 `#` hex 硬编码（12 token 定义处除外）、无 Material 色值 / 默认组件引用」 | 解码 / 内存：`DemTerrainLoader.kt:30-51`；配色：`SandboxScreen.kt` 全量 import + `SandboxTokens.kt` | 静态 PASS / 实测待定论 | 静态估算见第五节 5.3；配色与组件禁区静态扫描见第五节 5.4。800ms / 180MB 均未真机测量。 |

### 2.2 缺陷原口径核对（D-APK-1 三条要求）

| 缺陷要求 | 现状 | 结论 |
|---|---|---|
| 真实 DEM 读取，不再零读取 | `DemTerrainLoader` 经 `assets.open → decodeStream → getPixels → R 通道灰度 → create` 完整成链；资产在包内且静态解码出非平场数据 | PASS（静态） |
| 0–255 ↔ 0–7000m 映射 | `:67` `gray.coerceIn(0.0,255.0)/255.0*7000.0`；`:75` 常量 7000.0 | PASS（手算见第三节） |
| 不许假噪声地形 | 沙盘 / 回放无随机噪声高程实现；失败回落 `Flat` 基准面 | PASS |

---

## 三、采样公式逐项手算复核（不看断言，独立推演）

**fixture**：2×2 网格，行优先，行 0 = 北缘：`row0 = [0, 255]`，`row1 = [0, 255]`（`DemTerrainElevationTest.kt:20-23`）。bbox：south=24, north=42, west=104, east=122，spanLat=spanLng=18。

公式链（`DemTerrainElevation.kt:47-69`）：
`u=(lng-west)/spanLng`，`v=(north-lat)/spanLat`，均 `coerceIn(0,1)`；`gx=u*(width-1)`，`gy=v*(height-1)`；四点双线性得 `gray`；`meters=gray/255*7000`；`units=meters*0.009`；非有限值兜底 0.0。

| 用例 | 坐标 | 手算过程 | 期望 | 断言 | 结论 |
|---|---|---|---|---|---|
| 西北角 | (42.0, 104.0) | u=0, v=0；x0=y0=0；nw=0；gray=0 → 0m → 0.0 | 0.0 | `:33` 0.0 | PASS |
| 东南角 | (24.0, 122.0) | u=1, v=1；x0=y0=1；se=255；255/255×7000=7000m；×0.009=63.0 | 63.0 | `:40-43` 63.0 | PASS |
| 正中点 | (33.0, 113.0) | u=0.5, v=0.5；fx=fy=0.5；top=0+(255-0)×0.5=127.5，bottom=127.5；gray=127.5；127.5/255×7000=3500m；×0.009=31.5 | 31.5 | `:50-55` 31.5 | PASS |
| 越界·北西 | (50.0, 100.0) | v 钳 0，u 钳 0 → NW=0 | 0.0 | `:64` 0.0 | PASS |
| 越界·南东 | (10.0, 130.0) | v 钳 1，u 钳 1 → SE=255 → 63.0 | 63.0 | `:66` 63.0 | PASS |
| 越界·北东 | (45.0, 125.0) | v 钳 0，u 钳 1 → NE=`data[0*2+1]`=255 → 63.0 | 63.0 | `:68` 63.0 | PASS |
| 空网格 | create(0,0,null,bbox) | `valid=false`（width>0 不成立）→ degraded，`heightAt` 首行返 0.0 | degraded / 恒 0.0 | `:76-79` | PASS |
| 尺寸不符 | 2×2 配 3 元素数组 | `data.size != width*height` → degraded | degraded / 恒 0.0 | `:84-86` | PASS |
| 退化包围盒 | south=42 > north=24 | `north>south` 不成立 → degraded | degraded / 恒 0.0 | `:91-96` | PASS |
| NaN 坐标 | lat=NaN / lng=NaN | `:40` `!isFinite()` 拦截 → 0.0；正常点结果 `isNaN()` 为 false | 0.0 / 非 NaN | `:102-104` | PASS |
| ByteArray 无符号 | 1×2，`byteArrayOf(-1, 0)` | -1 `toInt() and 0xFF`=255（行 0 北），0（行 1 南）；宽 1 时 gx≡0；北缘 255→63.0，南缘 0→0.0 | 63.0 / 0.0 | `:112-115` | PASS |

**测试未覆盖、但静态推演通过的边界**：

1. **1×1 / 1×N / N×1 退化网格**：`gx=u*0=0`，`x1=coerceAtMost(width-1,0)=0`，`fx=0`；索引全部钳在 `[0,width-1]` / `[0,height-1]`，无越界访问。
2. **包围盒含 NaN**：`spanLat/spanLng` 为 NaN 时 `<= 0.0` 判定不拦截，归一化链路可产生 NaN；但 `:69` 末道 `units.isFinite()` 兜底返 0.0，不向外抛 NaN。
3. **灰度越界**：IntArray 入口在构造时 `:105` 统一 `coerceIn(0,255)`；ByteArray 入口 `:116` 位掩码封顶；插值结果 `:67` 再钳一次。
4. **栅格坐标恰为边缘**：`gx=width-1` 时 `x0=width-1`、`x1=width-1`、`fx=0`，不重复取右列。

**观察项（非阻断）**：资产灰度 max=118，即数据未饱和到 255；按标称满幅口径，实际最高灰度对应 `118/255×7000 ≈ 3247m`。255↔7000m 是量程口径而非数据实测峰值，与规格不冲突，记录备查。

---

## 四、回归面结论（必做核查 2）

### 4.1 `SandboxScene(` 全部构造点（`grep -rn "SandboxScene(" app/src --include=*.kt`）

| # | 调用点 | elevation 实参 | 结论 |
|---|---|---|---|
| 1 | `SandboxScreen.kt:192-200`（`defaultScene`，生产路径） | `:199` `DemTerrainLoader.load(context)` | PASS |
| 2 | `SandboxSceneTest.kt:15-23`（`buildScene`） | `:22` `elevation = TerrainElevation.Flat` | PASS |
| 3 | `SandboxSceneTest.kt:141-149`（`withoutChokes`） | `:148` `elevation = TerrainElevation.Flat` | PASS |
| 4 | `SandboxBenchTest.kt:20-28`（`setUp`） | `:27` `elevation = TerrainElevation.Flat` | PASS |
| 5 | `SandboxBenchTest.kt:50-58`（`bench sceneBuild`） | `:57` `elevation = TerrainElevation.Flat` | PASS |

- `SandboxScene.kt:20` 构造形参 `val elevation: TerrainElevation` 无默认值；上述 5 处全部显式传参，**沙盘域内无漏网构造点**。
- 全 app/src 未发现其他包构造 `SandboxScene`，也未发现 `.copy(...)` 重建场景；`replay` 域自有 `ReplayDirector.elevation`，不构造沙盘场景。

### 4.2 B-1 漏网：跨包测试裸 null 重载嫌疑（FAIL 嫌疑 1 条）

- **位置**：`app/src/test/java/io/legado/app/ui/map/replay/ReplayTerrainTest.kt:215`
  ```
  val degraded = DemTerrainElevation.create(0, 0, null, bbox)
  ```
- **重载面**（`DemTerrainElevation.kt:87-107` 与 `:110-118`）：`create(Int, Int, IntArray?, Bbox=...)` 与 `create(Int, Int, ByteArray?, Bbox=...)`；`IntArray?` 与 `ByteArray?` 互不构成子类型。
- **规则依据**：修复报告 `docs/修复-U10-DEM编译错.md` §三 自述——「二者类型互不构成子类型。裸 `null` 作为第三实参传入，按 Kotlin 重载解析规则无法在两个无关可空类型间选出最具体候选，`compileAppDebugUnitTestKotlin` 阶段存在 Overload resolution ambiguity 风险」。同一规则、同一 Kotlin 2.4.20（`gradle/libs.versions.toml:3`）、同一测试源集，该点与沙盘测试被修掉的原 `:75` 行情形完全同构，但修复包未覆盖到本文件。
- **对照**：沙盘测试 `DemTerrainElevationTest.kt:76-77` 已改为 `val emptyData: IntArray? = null` 显式标注类型；本处仍是裸 `null`。
- **其余 create 调用排查**：main 源集仅 `DemTerrainLoader.kt:45` 一处，实参为非空 `IntArray`，无歧义；`ReplayTerrainBenchTest.kt:17`、`ReplayTerrainTest.kt:23`、沙盘各测试实参均为非空 `IntArray` / `ByteArray`，无裸 null。`TerrainMapTest.kt:26` 的 `HeightRaster.create(0,0,null,...)` 属 crossing 单重载（`HeightRaster.kt:56` 仅 `IntArray?` 一个入口），不构成歧义。
- **定性**：高嫌疑编译错误，**需 CI `compileAppDebugUnitTestKotlin` 终判**；在 CI 结论回来前不计已通过。

---

## 五、分项核查结论

### 5.1 解码通道（必做核查 3）

| 核查点 | 现状（行号） | 结论 |
|---|---|---|
| 延迟解码 | `SandboxScreen.kt:187-190` `rememberSandboxScene()` 内 `remember(context) { defaultScene(context) }`；进入沙盘首组合时解码，非 App 启动期 eager | PASS（静态） |
| 失败回落 Flat | 三出口：`:37` `decodeStream` 返回 null → fallback；`:48-49` `catch (t: Throwable)` → fallback；`:46` `elevation.degraded` → fallback；`:58` 返 `TerrainElevation.Flat` | PASS |
| 降级日志只一次 | `:23-24` `@Volatile failureLogged`；`:54-57` 仅首次写日志，之后静默返 Flat | PASS（静态） |
| 主线程解码对首帧 ≤800ms 风险 | 解码（`decodeStream` 1024² ARGB_8888）+ `getPixels` 百万级拷贝 + gray 数组分配，全部在组合线程（主线程）同步执行；无异步 offload、无 `LaunchedEffect` 后台预解码；Loader 无进程内缓存 | **风险登记，需真机 / CI 性能定论**（见第七节） |
| 内存静态估算 | 终态灰度网格 1024²×4B ≈ 4.0 MiB；瞬时峰值：位图 4.0 MiB + pixels 4.0 MiB + gray 4.0 MiB ≈ 12.6 MiB，位图 `:42` 已 `recycle()`。静态量级在 ≤180MB 红线内；未真机测量 | 静态 PASS / 实测待定论 |

**观察项**：`failureLogged` 成功路径不复位——首次失败打日志后，即便后续重试成功也不再记录恢复事件；符合「全程只打一次」口径，但失败→恢复在 logcat 不可见，登记备查。

### 5.2 贴地链路（必做核查 4）

- **沙盘势力色块**：`PowerSector.sampleSurface(elevation)`（`PowerSector.kt:64-65`）形参即注入的 `scene.elevation` 同一实例；沙盘主源集内当前无调用方（绘制 pass 未落地）。
- **回放棋子 / 箭头 / 势力多边形**：`ReplayDirector.elevation` 单实例流向 `BattleArrow.fromPiece(..., elevation)`（`ReplayDirector.kt:279,301`）、`PieceVisual`（`PieceVisual.kt:112`）、`ReplayFront.sampleSurface`（`ReplayDirector.kt:348`），均经 `ReplayGround.heightAt` 取高；同一高程源，回放域内不二次换算。
- **二次解码排查**：全 main 源集 `china_height.png` 仅 `DemTerrainLoader.kt:21,32` 一处读取；回放域无第二个 PNG 读取器。
- **潜伏二次解码路径（观察项，非阻断）**：`ReplayScreen.kt:52` `val resolvedElevation = elevation ?: remember(context) { DemTerrainLoader.load(context) }`——宿主未显式传入共享实例时，回放会独立调 Loader 再解码一份；Loader 自身无缓存字段。当前 main 源码未发现任何外部宿主组装 `SandboxScreenHost` / `ReplayScreenHost`（导航接线不在本批），此路径属潜伏；建议后续由宿主注入同一实例，或在 Loader 内做进程内单例缓存。

### 5.3 纯度与禁区（必做核查 5）

| 禁区 | 扫描命令与命中 | 结论 |
|---|---|---|
| 纯 JVM 核零 android import | `grep -n "import android\|import androidx" DemTerrainElevation.kt DemTerrainElevationTest.kt DemTerrainBenchTest.kt` → 无命中（exit=1） | PASS |
| 无 hex 颜色硬编码 | 四个主文件内无 6 位颜色 hex；`and 0xFF` / `ushr 16` 为通道位掩码，非色值 | PASS |
| 无 Material 默认组件 | `SandboxScreen.kt` import 仅 foundation / layout / runtime / ui 基础包，无 material3 / `MaterialTheme` / `colorScheme`；配色全部取自 `SandboxTokens`（`textPrimary/textSecondary/textAccent/brandGold/brandCopper/colorGlow/bgSurface` 均在 `SandboxTokens.kt` 有定义） | PASS |
| Android 依赖收口位置 | `Context/Bitmap/BitmapFactory/Log` 仅出现在 `DemTerrainLoader.kt` 与壳层 `SandboxScreen.kt`；采样核心与测试可在普通 JVM 构造 | PASS |

### 5.4 修复质量（必做核查 6）

| 核查点 | 证据 | 结论 |
|---|---|---|
| 非法 import 残留 | `grep -rn "import kotlin.math.is" app/src/` → 无命中（exit=1）；`DemTerrainElevation.kt:3` 仅剩 `import kotlin.math.floor`（真实顶层函数） | PASS |
| 成员调用合法性 | `:40` `lat.isFinite()` / `lng.isFinite()`、`:69` `units.isFinite()` 为 `Double` 成员函数，无需顶层 import | PASS |
| 沙盘测试 null 消歧 | `DemTerrainElevationTest.kt:76` `val emptyData: IntArray? = null`，`:77` 传入；类型固定到 `IntArray?` 重载，`ByteArray?` 候选不适用，歧义消除 | PASS |
| 跨包测试 null 消歧 | `ReplayTerrainTest.kt:215` 仍为裸 `null`，未标注类型 | **FAIL 嫌疑，见 4.2** |
| 包内其余 `kotlin.math` 导入 | `Geo.kt`（asin/atan2/cos/pow/roundToInt/sin/sqrt）、`RiverLine.kt`（max/min）均为真实顶层函数，无 `isFinite/isNaN/isInfinite` 类误导入 | PASS |

### 5.5 CI 现状（必做核查 7）

- `dd13e9a` 曾红（`compileAppDebugKotlin` 唯一报错：`Unresolved reference 'isFinite'`，见修复报告 §一）。
- 修复提交刚推、**CI 未回**。`compileAppDebugKotlin`、`compileAppDebugUnitTestKotlin` 是否转绿，本报告一律记为「需 CI 定论」，不猜 PASS；文档 `docs/ci-compile-report.md` 最新绿记录停留在旧 commit `965d75a`，不代表本批。

---

## 六、返工清单

| # | 级别 | 文件 | 判据 / 问题 | 建议改法（仅供写码方参考，本验收不改码） |
|---|---|---|---|---|
| R1 | **FAIL 嫌疑（待 CI 终判）** | `app/src/test/java/io/legado/app/ui/map/replay/ReplayTerrainTest.kt:215` | 裸 `null` 传入 `IntArray?` / `ByteArray?` 双可体重载，按修复报告自述规则存在 Overload resolution ambiguity 高嫌疑；与沙盘测试已修情形同构，属修复漏网 | 比照 `DemTerrainElevationTest.kt:76-77`：先 `val emptyData: IntArray? = null` 再传入；或直接传 `IntArray(0)` 走非空 `IntArray` 重载。改后由 CI `compileAppDebugUnitTestKotlin` 复核 |
| R2 | 观察（非阻断） | `DemTerrainLoader.kt` / `ReplayScreen.kt:52` | Loader 无进程内缓存；回放兜底路径可独立二次解码 PNG，存在两份 4MiB 网格 + 瞬时位图峰值的冗余 | Loader 内缓存首次解码实例（@Volatile 单例），或由宿主把沙盘同一 `TerrainElevation` 实例注入回放屏 |
| R3 | 观察（非阻断，性能待实测） | `DemTerrainLoader.kt:30-51` / `SandboxScreen.kt:187-190` | 1024² 解码与像素拷贝在主线程同步完成，首帧 ≤800ms 红线未量；无后台预解码 | 评估异步预解码 / 首帧后补发路径；真机量首帧耗时与堆内存，对照 ≤800ms / ≤180MB |
| R4 | 观察（待渲染 pass） | 沙盘绘制层（河流样条、行军箭头、势力色块） | `scene.elevation` 实例已备好，但沙盘内尚无 `sampleSurface` / 河道贴地 / 箭头贴地的消费方；河流「从 DEM 读河床宽度」未落地 | 属后续渲染 pass 批次职责；接线时统一取 `scene.elevation`，不得另起高程源 |

---

## 七、需 CI / 真机定论项（本批均未验证，不得当已验证）

1. **编译转绿**：`:app:compileAppDebugKotlin` 与 `:app:compileAppDebugUnitTestKotlin` 两个源集是否 0 条 `e:`；重点盯 R1 裸 null 歧义是否如嫌疑报错。
2. **单测实跑**：`DemTerrainElevationTest` 9 个用例（0↔0m、255↔63 单位、中点 31.5、三组越界钳制、空网格 / 尺寸不符 / 退化 bbox / NaN / ByteArray 无符号口径）是否全部通过。
3. **bench 实跑**：`DemTerrainBenchTest` 10 万次采样 avg < 3µs/op；`SandboxBenchTest` 场景构建 <4ms、判点 <0.5ms、河道采样 <1ms 等阈值真机 / CI 容器实测值。
4. **资产实解码**：真机上 `BitmapFactory` 对该 8-bit 灰度 PNG 的通道次序、像素行列方向（行 0 = 北缘）；若通道 / 方向错位，山脊会与城池错位，需首帧目检。
5. **降级路径**：资产缺失 / 解码失败 / 栅格退化时回落 Flat、日志全程仅一次，未在异常环境实测。
6. **隆起观感**：×0.009 夸张系数下满幅 63 世界单位，与光柱 12 / 6 单位、雾气 260 单位盘面的比例关系未上真机目检。
7. **性能红线**：首帧 / 首开 ≤800ms、内存 ≤180MB 未真机测量；主线程解码耗时未采样。
8. **R1 若 CI 编译通过**：说明该 Kotlin 版本对本处 null 解析另有取向，则 R1 降级为观察项；以 CI 日志为准。

---

## 八、验收边界与未做事项声明

- 本报告全部结论基于只读静态扫描、公式手算与既有 fixture 推演；未执行 gradle / kotlinc / 单测 / bench，未装机。
- 文件域：仅复核 U10 DEM 接线及其直接编译面（同重载的 replay 测试）；未改动 `replay` / `globe` / `crossing` 任何源码。
- 前轮 `docs/验收-U10-DEM地形接线.md` 的未验证项（编译、单测、真机观感、渲染 pass 贴地）在本报告第七节继续挂账，未被本轮静态核对消解。
