> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-replay 裸 null 重载歧义（DEM 复核点名的唯一 FAIL）

- 基线：main；Kotlin 2.4.20。
- 任务范围：仅 `app/src/test/java/io/legado/app/ui/map/replay/` 与 `.../sandbox/` 两个测试目录内 `DemTerrainElevation.create(` 调用穷举；主源码一行未碰。
- 红线遵守：未跑本地 gradle / kotlinc 编译；未做任何 git 写操作（提交由总控收口）。

## 一、根因

`DemTerrainElevation` 伴生对象里有两个可空数组重载（`app/src/main/java/io/legado/app/ui/map/sandbox/DemTerrainElevation.kt`）：

- 第 87–107 行：`fun create(width: Int, height: Int, data: IntArray?, bbox: Bbox = ...)`；
- 第 110–118 行：`fun create(width: Int, height: Int, data: ByteArray?, bbox: Bbox = ...)`。

`IntArray?` 与 `ByteArray?` 互不构成子类型。第三实参传**裸 `null`** 时，重载解析无法在两个无关可空类型间选出最具体候选，存在 “Overload resolution ambiguity” 高嫌疑。沙盘测试同类一处已在前一次修复中改为显式类型（`DemTerrainElevationTest.kt:76`），replay 包这处跨包漏改。

## 二、两重载语义核对（null 路径是否等价）

| 重载 | 非空口径 | null 输入 |
| --- | --- | --- |
| `IntArray?`（主入口，:87） | 灰度值构造时 `coerceIn(0, 255)` 夹紧；长度不符 / 包围盒退化 → 退化实例 | `data != null` 校验失败 → 退化实例，`heightAt` 恒 `0.0` |
| `ByteArray?`（:110） | `bytes[i].toInt() and 0xFF` 无符号字节口径，转成 `IntArray` 后委托给上面的 `IntArray?` 重载 | `data?.let { ... }` 得 `null` → 委托 `IntArray?` 重载 → 同样退化实例，`heightAt` 恒 `0.0` |

**结论：null 路径两重载收敛到同一退化实例，用例意图（空网格兜底返回 `0.0` 且标记降级）在任一重载下都成立。** 本用例选 `IntArray?`，理由：

1. null 路径语义等价，选主入口 `IntArray?`，不经过 ByteArray 委托层；
2. 本文件其余 fixture（第 23 行 `intArrayOf(...)`）全部走 IntArray 族，口径一致；
3. 与沙盘已修先例 `DemTerrainElevationTest.kt:76`（`val emptyData: IntArray? = null`）写法对齐，便于后续检索。

## 三、穷举清单（两目录全部 `DemTerrainElevation.create(` 调用）

| # | 文件 | 行号（改后） | 第 3 参表达式 | 形态判定 | 处置 |
| --- | --- | --- | --- | --- | --- |
| 1 | replay/ReplayTerrainTest.kt | 23 | `intArrayOf(0, 255, 0, 255)` | IntArray 字面量，无歧义 | 不动 |
| 2 | replay/ReplayTerrainTest.kt | 216–217 | `emptyData`（`val emptyData: IntArray? = null`） | **原裸 `null`，歧义，已修** | 本次修复 |
| 3 | replay/ReplayTerrainBenchTest.kt | 22 | `data`（局部 `IntArray(n*n)`） | IntArray 非空，无歧义 | 不动 |
| 4 | replay/ReplayBenchTest.kt | — | 无 `create` 调用 | — | 不动 |
| 5 | replay/ReplayDirectorTest.kt | — | 无 `create` 调用 | — | 不动 |
| 6 | sandbox/DemTerrainElevationTest.kt | 24 | `data`（局部 `IntArray`） | IntArray 非空，无歧义 | 不动 |
| 7 | sandbox/DemTerrainElevationTest.kt | 76–77 | `emptyData`（`IntArray?`） | 前次已修为显式类型，无歧义 | 不动 |
| 8 | sandbox/DemTerrainElevationTest.kt | 84 | `IntArray(3)` | IntArray 构造器，无歧义 | 不动 |
| 9 | sandbox/DemTerrainElevationTest.kt | 94 | `intArrayOf(0, 255, 0, 255)` | IntArray 字面量，无歧义 | 不动 |
| 10 | sandbox/DemTerrainElevationTest.kt | 112 | `byteArrayOf(-1, 0)` | ByteArray 字面量，无歧义 | 不动 |
| 11 | sandbox/DemTerrainBenchTest.kt | 17 | `data`（局部 `IntArray(n*n)`） | IntArray 非空，无歧义 | 不动 |
| 12 | sandbox/SandboxSceneTest.kt / SandboxBenchTest.kt | — | 无 `create` 调用 | — | 不动 |

歧义形态排查补充：两目录内未发现 `emptyArray<Int>()` / `emptyArray<Byte>()` 之外的其它可空实参形态；`intArrayOf()`、`byteArrayOf()` 空数组字面量本身类型确定，不构成歧义。

## 四、改动明细（仅 1 个文件，共 1 处）

### `app/src/test/java/io/legado/app/ui/map/replay/ReplayTerrainTest.kt`

用例 `退化 DEM 实例高度恒 0 且标记降级`（原第 214–215 行）：

- 改动前：
  ```kotlin
  val degraded = DemTerrainElevation.create(0, 0, null, bbox)
  ```
- 改动后：
  ```kotlin
  // 显式标注 null 类型：create 同时有 IntArray? / ByteArray? 两个重载，裸 null 会触发重载歧义。
  val emptyData: IntArray? = null
  val degraded = DemTerrainElevation.create(0, 0, emptyData, bbox)
  ```

**测试语义保持不变**，断言原样保留：

- `assertTrue(director.terrainDegraded)`（降级标记）；
- `assertEquals(0.0, director.visuals().first().groundHeight, 1e-9)`（空网格兜底高度恒 0.0）。

仅把第三实参的静态类型从“裸 null（类型未知）”收窄为 `IntArray?`，构造路径、降级判定、采样口径均未改。

## 五、范围外观察（未改，仅登记）

- `crossing/terrain/TerrainMapTest.kt:26`：`HeightRaster.create(0, 0, null, degradedHint = false)`。核对主源码 `HeightRaster.kt:56–59`，该 `create` **只有一个 `IntArray?` 重载**，不存在第二候选，裸 `null` 无歧义；且该文件不在本次文件域内，未触碰。

## 六、grep 回证

命令：

```bash
grep -rn "DemTerrainElevation\.create(" \
  app/src/test/java/io/legado/app/ui/map/replay/ \
  app/src/test/java/io/legado/app/ui/map/sandbox/
```

最终命中行（改后）：

```
replay/ReplayTerrainTest.kt:23:        DemTerrainElevation.create(2, 2, intArrayOf(0, 255, 0, 255), bbox)
replay/ReplayTerrainTest.kt:217:        val degraded = DemTerrainElevation.create(0, 0, emptyData, bbox)
replay/ReplayTerrainBenchTest.kt:22:        return DemTerrainElevation.create(n, n, data, DemTerrainElevation.DEFAULT_BBOX)
sandbox/DemTerrainElevationTest.kt:24:        return DemTerrainElevation.create(2, 2, data, bbox)
sandbox/DemTerrainElevationTest.kt:77:        val dem = DemTerrainElevation.create(0, 0, emptyData, bbox)
sandbox/DemTerrainElevationTest.kt:84:        val dem = DemTerrainElevation.create(2, 2, IntArray(3), bbox)
sandbox/DemTerrainElevationTest.kt:94:        val dem = DemTerrainElevation.create(2, 2, intArrayOf(0, 255, 0, 255), badBbox)
sandbox/DemTerrainElevationTest.kt:112:        val dem = DemTerrainElevation.create(1, 2, byteArrayOf(-1, 0), bbox)
sandbox/DemTerrainBenchTest.kt:17:        return DemTerrainElevation.create(n, n, data, DemTerrainElevation.DEFAULT_BBOX)
```

replay 目录已无裸 `null` 实参；`ReplayTerrainTest.kt:216` 为显式 `val emptyData: IntArray? = null`。

## 七、未验证项

- **未编译**：遵守禁本地编译红线，未跑 `compileAppDebugUnitTestKotlin` / kotlinc；歧义是否彻底消除需 CI 复核。
- **未跑测试**：该用例运行时行为（降级标记、`0.0` 兜底）仅做静态语义核对，未在本地 JVM 执行；断言文本与改动前逐字一致，预期行为不变。
- 主源码 `DemTerrainElevation.kt` 两个重载签名本次未改动，仅作为语义核对依据阅读。
