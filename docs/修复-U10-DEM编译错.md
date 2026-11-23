# 修复-U10-DEM 编译错（CI `compileAppDebugKotlin`，commit `dd13e9a`）

- 基线：main，HEAD `5f936ac`（已含报错）；Kotlin 2.4.20。
- 任务范围：仅 `app/src/main/java/io/legado/app/ui/map/sandbox/` 下 DEM 两个主文件与对应测试；未触碰其他包、未改已提交的 Sheet / 接线包文件。
- 红线遵守：未跑本地 gradle / kotlinc 编译，未做任何 git 操作（提交由总控收口）。

## 一、错误原文（CI job `137664541357` / run `45166453713`）

```
e: file:///.../app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainElevation.kt:4:20
   Unresolved reference 'isFinite'.
```

`compileAppDebugKotlin` 仅此一条报错。

## 二、根因

`DemTerrainElevation.kt` 第 4 行写了 `import kotlin.math.isFinite`，但 Kotlin 标准库里：

- `kotlin.math` 包只有顶层函数（`floor / ceil / abs / sqrt / pow / round / sin / cos / atan2 / max / min` 等），**没有** `isFinite`、`isNaN`、`isInfinite` 这三个顶层符号；
- `Double.isFinite()` / `Double.isNaN()` / `Double.isInfinite()` 是 **`kotlin.Double`（及 `Float`）上的成员函数**，同包默认可见，**不需要、也不能**用顶层 import 引入。

故编译器在导入处解析不到 `isFinite`，报 Unresolved reference。类内三处 `xxx.isFinite()` 成员调用本身写法正确，删掉非法 import 后即可解析。

## 三、改动清单（行号为改后文件）

### 1. `app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainElevation.kt`

- **删除** 原第 4 行：`import kotlin.math.isFinite`（非法顶层导入，根因行）。
- 保留第 3 行 `import kotlin.math.floor`（`floor` 是 `kotlin.math` 真实顶层函数，第 53–54 行在用）。
- 成员调用未改：第 40 行 `lat.isFinite()` / `lng.isFinite()`、第 69 行 `units.isFinite()` 均为 `Double` 成员函数，无需 import。

### 2. `app/src/test/java/io/legado/app/ui/map/sandbox/DemTerrainElevationTest.kt`

- **主动排除一处潜在重载歧义**（非本次 CI 报错，属穷举同类风险时发现）：`create` 在伴生对象里有两个可空数组重载——`IntArray?` 与 `ByteArray?`，二者类型互不构成子类型。原第 75 行把裸 `null` 作为第三实参传入，按 Kotlin 重载解析规则无法在两个无关可空类型间选出最具体候选，`compileAppDebugUnitTestKotlin` 阶段存在 “Overload resolution ambiguity” 风险。
- 改法（行为不变，仅显式标注 null 类型）：
  - 第 76 行：`val emptyData: IntArray? = null`
  - 第 77 行：`val dem = DemTerrainElevation.create(0, 0, emptyData, bbox)`
- 备注：该歧义只在单测源集出现；主源集 `DemTerrainLoader` 传入的是非空 `IntArray`，不触发。

### 未改动

- `DemTerrainLoader.kt`：import 与符号逐项核对均有效（见下节），无需改动。
- `SandboxScene.kt` / `SandboxScreen.kt` 及其他包：只读核对，未改。

## 四、穷举核对清单

### 4.1 主文件 import 逐项核对

#### `DemTerrainElevation.kt`

| 行 | import | 是否存在 | 依据 |
|---|---|---|---|
| 3 | `kotlin.math.floor` | 存在 | `kotlin.math.floor(Double): Double` 是标准库顶层函数；第 53、54 行 `floor(gx)` / `floor(gy)` 在用。 |
| 4（已删） | `kotlin.math.isFinite` | **不存在** | `isFinite` 是 `Double`/`Float` 成员函数，`kotlin.math` 包无此顶层符号。已删除。 |

#### `DemTerrainLoader.kt`

| 行 | import | 是否存在 | 依据 |
|---|---|---|---|
| 3 | `android.content.Context` | 存在 | Android 框架类；`load(context: Context)` 形参类型。 |
| 4 | `android.graphics.Bitmap` | 存在 | Android 框架类；`decodeStream` 返回值 / `getPixels` 接收位图。 |
| 5 | `android.graphics.BitmapFactory` | 存在 | Android 框架类；静态方法 `decodeStream`、内部类 `Options`。 |
| 6 | `android.util.Log` | 存在 | Android 框架类；`Log.w(TAG, msg)` 在用。 |

### 4.2 主文件调用符号逐项核对

#### `DemTerrainElevation.kt`

| 符号 / 调用 | 存在性 | 依据 |
|---|---|---|
| `lat.isFinite()` / `lng.isFinite()` / `units.isFinite()` | 存在 | `Double` 成员函数，无需 import。 |
| `floor(gx)` / `floor(gy)` | 存在 | 顶层 `kotlin.math.floor`，已 import。 |
| `Double.coerceIn(0.0, 1.0)`、`Int.coerceAtMost(...)`、`Int.coerceIn(0, 255)`、`Double.coerceIn(0.0, 255.0)`、`Int.coerceAtLeast(0)` | 存在 | `kotlin` 包默认导入的标准库扩展 / 成员函数。 |
| `IntArray(n) { ... }`、`data.size`、`data[it]`、`bytes[i].toInt() and 0xFF` | 存在 | JVM / Kotlin 内置数组与位运算。 |
| `TerrainElevation`（接口实现） | 存在 | 同包 `TerrainElevation.kt`，`fun interface`，`heightAt(lat: Double, lng: Double): Double` 签名一致。 |
| `Bbox(south, north, west, east)` 嵌套 data class | 存在 | 本类第 32–37 行定义，四个 `Double` 只读属性。 |
| 伴生常量 `DEM_MAX_METERS` / `VERTICAL_EXAGGERATION` / `DEFAULT_BBOX` | 存在 | 第 76、79、82 行定义，均为 `public`。 |

#### `DemTerrainLoader.kt`

| 符号 / 调用 | 存在性 | 依据 |
|---|---|---|
| `context.assets.open(ASSET_PATH)` | 存在 | `Context.getAssets(): AssetManager`，`AssetManager.open(String): InputStream`。 |
| `InputStream.use { ... }` | 存在 | `kotlin.io.use` 内联扩展，`kotlin.io.*` 属默认导入包。 |
| `BitmapFactory.Options()`、`inPreferredConfig` | 存在 | `BitmapFactory.Options` 公开可写字段。 |
| `Bitmap.Config.ARGB_8888` | 存在 | `Bitmap.Config` 枚举常量。 |
| `BitmapFactory.decodeStream(input, null, options)` | 存在 | 签名 `decodeStream(InputStream, Rect?, Options?): Bitmap?`；null 返回分支已处理。 |
| `bitmap.width` / `bitmap.height` | 存在 | `Bitmap` 公开属性。 |
| `bitmap.getPixels(pixels, 0, width, 0, 0, width, height)` | 存在 | 签名 `getPixels(IntArray, Int, Int, Int, Int, Int, Int)`。 |
| `bitmap.recycle()` | 存在 | `Bitmap` 实例方法。 |
| `(pixels[i] ushr 16) and 0xFF` | 存在 | Kotlin 位运算，R 通道取灰度。 |
| `DemTerrainElevation.create(width, height, gray)` | 存在 | 命中 `create(Int, Int, IntArray?, Bbox = DEFAULT_BBOX)` 重载；`gray` 为非空 `IntArray`。 |
| `elevation.degraded` | 存在 | `DemTerrainElevation` 公开只读属性 `degraded: Boolean`。 |
| `TerrainElevation.Flat` / `Log.w(TAG, ...)` | 存在 | 同包接口内 `object Flat`；`Log.w(String, String)`。 |

### 4.3 测试文件 import 与符号核对

#### `DemTerrainElevationTest.kt`

| 项 | 存在性 | 依据 |
|---|---|---|
| `org.junit.Assert.assertEquals / assertFalse / assertTrue` | 存在 | JUnit 4 断言；`app/build.gradle` 已 `testImplementation(libs.junit)`。 |
| `org.junit.Test` | 存在 | JUnit 4 注解。 |
| `intArrayOf / IntArray / byteArrayOf`、`Double.NaN`、`Double.isNaN()` | 存在 | Kotlin 内置；`isNaN()` 为 `Double` 成员，无需 import。 |
| `DemTerrainElevation.DEFAULT_BBOX` / `DEM_MAX_METERS` / `VERTICAL_EXAGGERATION` / `Bbox(...)` | 存在 | 伴生对象与嵌套 data class，属性名、类型与定义一致。 |

#### `DemTerrainBenchTest.kt`

| 项 | 存在性 | 依据 |
|---|---|---|
| `org.junit.Assert.assertTrue` / `org.junit.Test` | 存在 | JUnit 4。 |
| `System.nanoTime()`、`repeat(...)`、`"%.2f".format(...)` | 存在 | JDK / Kotlin 内置。 |
| `DemTerrainElevation.create(n, n, data, DEFAULT_BBOX)` | 存在 | 命中 `IntArray?` 重载，实参类型顺序一致。 |

#### 同目录既有测试（只读核对，未改）

- `SandboxSceneTest.kt`、`SandboxBenchTest.kt`：import 仅 `org.junit.Assert.*` / `org.junit.Test` / `org.junit.Before`，均为 JUnit 4 真实符号；未引用 DEM 类型，`elevation = TerrainElevation.Flat` 与 `SandboxScene` 构造参数一致。

### 4.4 签名一致性核对（调用方 → 定义方）

| 调用点 | 形参实参 | 定义 | 结论 |
|---|---|---|---|
| `DemTerrainLoader.load(context)` 内 `DemTerrainElevation.create(width: Int, height: Int, gray: IntArray)` | `(Int, Int, IntArray)`，bbox 取默认值 | `create(Int, Int, IntArray?, Bbox = DEFAULT_BBOX)` | 一致。 |
| Test L24 `create(2, 2, data: IntArray, bbox: Bbox)` | `(Int, Int, IntArray, Bbox)` | `IntArray?` 重载 | 一致。 |
| Test L77 `create(0, 0, emptyData: IntArray?, bbox)` | `(Int, Int, IntArray?, Bbox)` | `IntArray?` 重载（消歧后） | 一致。 |
| Test L82 `create(2, 2, IntArray(3), bbox)` | `(Int, Int, IntArray, Bbox)` | `IntArray?` 重载 | 一致。 |
| Test L92 `create(2, 2, intArrayOf(...), badBbox: Bbox)` | `(Int, Int, IntArray, Bbox)` | `IntArray?` 重载 | 一致。 |
| Test L110 `create(1, 2, byteArrayOf(-1, 0), bbox)` | `(Int, Int, ByteArray, Bbox)` | `ByteArray?` 重载 | 一致。 |
| Bench L17 `create(n, n, data: IntArray, DEFAULT_BBOX)` | `(Int, Int, IntArray, Bbox)` | `IntArray?` 重载 | 一致。 |
| `Bbox(south=…, north=…, west=…, east=…)` | 四个 `Double` 命名实参 | data class 四属性同序同名 | 一致。 |
| `SandboxScreen.kt:199` `DemTerrainLoader.load(context)` → `SandboxScene(elevation = …)` | 返回 `TerrainElevation` | `SandboxScene.elevation: TerrainElevation`（第 20 行） | 一致。 |
| 类名 / 包名 | 主源与测试均在 `io.legado.app.ui.map.sandbox` | 定义同包 | 一致。 |

### 4.5 包内其余 `kotlin.math` 导入顺带核对（非改动文件）

`Geo.kt`：`asin / atan2 / cos / pow / roundToInt / sin / sqrt`；`RiverLine.kt`：`max / min`。均为 `kotlin.math` 真实顶层函数，无 `isFinite / isNaN / isInfinite` 类误导入。

## 五、grep 证据（命令与命中）

命令 1：全 src 检索非法顶层数学导入，应为空。

```
$ grep -rn "import kotlin.math.is" app/src/
（无命中，exit=1）
```

命令 2：`DemTerrainElevation.kt` 现存 import 与 `isFinite` 成员调用。

```
$ grep -n "^import\|isFinite()" app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainElevation.kt
3:import kotlin.math.floor
40:        if (!lat.isFinite() || !lng.isFinite()) return 0.0
69:        return if (units.isFinite()) units else 0.0
```

命令 3：sandbox 主源集全部 `kotlin.math` 导入，仅剩真实顶层函数。

```
$ grep -rn "import kotlin.math" app/src/main/java/io/legado/app/ui/map/sandbox/
DemTerrainElevation.kt:3:import kotlin.math.floor
Geo.kt:3:import kotlin.math.asin
Geo.kt:4:import kotlin.math.atan2
Geo.kt:5:import kotlin.math.cos
Geo.kt:6:import kotlin.math.pow
Geo.kt:7:import kotlin.math.roundToInt
Geo.kt:8:import kotlin.math.sin
Geo.kt:9:import kotlin.math.sqrt
RiverLine.kt:3:import kotlin.math.max
RiverLine.kt:4:import kotlin.math.min
```

命令 4：测试内 null 重载消歧落点。

```
$ grep -n "emptyData\|create(0, 0" app/src/test/java/io/legado/app/ui/map/sandbox/DemTerrainElevationTest.kt
76:        val emptyData: IntArray? = null
77:        val dem = DemTerrainElevation.create(0, 0, emptyData, bbox)
```

命令 5：Loader 调用 Elevation 与兜底出口。

```
$ grep -n "DemTerrainElevation.create\|elevation.degraded\|TerrainElevation.Flat" app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainLoader.kt
45:                val elevation = DemTerrainElevation.create(width, height, gray)
46:                if (elevation.degraded) fallback("栅格尺寸 / 内容退化") else elevation
58:        return TerrainElevation.Flat
```

## 六、未验证项（如实声明）

1. **编译仍未经 CI 复跑验证**：按红线未在本地执行 gradle / kotlinc；本次仅为静态核对（import 存在性、API 签名、重载解析规则比对）。`compileAppDebugKotlin` 与 `compileAppDebugUnitTestKotlin` 是否转绿，需总控推 CI 复跑确认。
2. 单测运行期行为（灰度映射、双线性插值、边界钳制、bench 阈值 3µs/op）本次未执行，沿用既有测试断言，未在本轮重跑。
3. 资产 `app/src/main/assets/map/china_height.png` 已确认存在于 assets 目录；但其灰度位深 / 尺寸与 `DEFAULT_BBOX` 的匹配属运行期表现，未在本轮解码验证。
4. 裸 `null` 重载歧义的判定依据为 Kotlin 语言规范“最具体候选 / 子类型转发”规则与公开先例（两无关可空类型间 null 实参报 Overload resolution ambiguity）；因未允许本地编译，该点同样待 CI 单测源集编译复核。
