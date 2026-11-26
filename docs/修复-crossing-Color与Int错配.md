> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-crossing `CrossingShell.kt` 中 `Color` 与 `Int` 类型错配（327 行）

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`。
- 改动文件域（仅此一个、仅此一行）：`app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt:327`。
- 任务范围：**只修** `Color(...)` 包裹一个已经是 `Color` 的返回值这一处；未改 `pieceColor` 签名与配色值，未改其它调用点，未碰 `engine/`、`model/`、`terrain/`。
- 红线遵守：未跑本地 gradle / kotlinc 编译；未做任何 git 写操作；他人未提交文件 `shell/CrossingRasterBridge.kt`、`terrain/RasterDecoder.kt` 未删改。

## 一、CI 报错原文

```
CrossingShell.kt:327:39 Argument type mismatch: actual type is 'Color', but 'Int' was expected
```

## 二、病灶说明

- 第 393 行取色函数签名（本轮未动）：
  ```kotlin
  private fun pieceColor(role: FactionRole): Color = when (role) { ... }
  ```
  返回的是 Compose 的 `androidx.compose.ui.graphics.Color`（文件头第 25 行 `import androidx.compose.ui.graphics.Color`）。
- 第 327 行误写成 `color = Color(pieceColor(corps.role))`：又用 `Color(...)` 构造器去包一个**已经是 `Color`** 的值。`Color` 构造器只收 `Int` / `Long` / `(Float,Float,Float,Float)`，不收 `Color`，编译器在候选构造器间先匹配到 `Color(color: Int)`，于是报「actual type is 'Color', but 'Int' was expected」。
- `TextStyle.color` 形参本身就是 `Color`，直接传返回值即可，无需任何包装。
- 排除的错误改法（本轮均未采用）：
  - 未改 `pieceColor(...).toArgb()` 再包 `Color(...)`——会白白丢精度并多一次 ARGB 往返；
  - 未改 `pieceColor` 的签名或任何配色值；
  - 未动 268 / 272 行：那两处是先 `val pieceColor = pieceColor(...)` 接住 `Color`，再 `Color.copy(alpha = ...)`，写法本来就对。

## 三、before / after

文件：`app/src/main/java/io/legado/app/ui/map/crossing/shell/CrossingShell.kt`，`CorpsRoster` 内棋子名文本：

**before（327 行）**
```kotlin
BasicText(
    text = corps.name,
    style = TextStyle(
        color = Color(pieceColor(corps.role)),
        fontSize = 12.sp,
        fontFamily = FontFamily.Serif,
    ),
    modifier = Modifier.width(72.dp),
)
```

**after（327 行）**
```kotlin
BasicText(
    text = corps.name,
    style = TextStyle(
        color = pieceColor(corps.role),
        fontSize = 12.sp,
        fontFamily = FontFamily.Serif,
    ),
    modifier = Modifier.width(72.dp),
)
```

`git diff`（只读核对，未提交）：
```diff
@@ -324,7 +324,7 @@ private fun CorpsRoster(
                 BasicText(
                     text = corps.name,
                     style = TextStyle(
-                        color = Color(pieceColor(corps.role)),
+                        color = pieceColor(corps.role),
                         fontSize = 12.sp,
                         fontFamily = FontFamily.Serif,
                     ),
```

## 四、`grep -n "Color(" CrossingShell.kt` 全文命中与逐条分类

取值类型前提：`CrossingTokens.kt` 中 `bgPage / bgSurface / textPrimary / textSecondary / textTertiary / textAccent / brandGold / brandCopper / colorGlow / stoneBlue` 等均为 `Long`（`0xFF…L`），`Color(Long)` 构造器合法。

### 4.1 合法构造：`Color(CrossingTokens.xxx: Long)`，`Color(Long)` 重载匹配，无错

| 行号 | 代码 | 判定 |
| --- | --- | --- |
| 91 | `color = Color(CrossingTokens.textPrimary),` | 合法 |
| 100 | `color = Color(CrossingTokens.textSecondary),` | 合法 |
| 118 | `color = Color(CrossingTokens.textPrimary),` | 合法 |
| 127 | `color = Color(CrossingTokens.textSecondary),` | 合法 |
| 137 | `color = Color(CrossingTokens.textAccent),` | 合法 |
| 147 | `color = Color(CrossingTokens.brandGold),` | 合法 |
| 205 | `drawRect(Color(CrossingTokens.bgPage))` | 合法 |
| 221 | `val riverColor = Color(CrossingTokens.stoneBlue).copy(alpha = 0.32f)` | 合法 |
| 234 | `drawCircle(Color(CrossingTokens.colorGlow), ...)` | 合法 |
| 236 | `color = Color(CrossingTokens.brandGold),` | 合法 |
| 277 | `color = Color(CrossingTokens.colorGlow),` | 合法 |
| 296 | `color = Color(CrossingTokens.textSecondary),` | 合法 |
| 336 | `color = Color(CrossingTokens.textSecondary),` | 合法 |
| 344 | `color = Color(CrossingTokens.brandGold),` | 合法 |
| 371 | `!enabled -> Color(CrossingTokens.textTertiary)` | 合法 |
| 372 | `else -> Color(CrossingTokens.brandGold)` | 合法 |
| 386 | `TerrainKind.WATER -> Color(CrossingTokens.stoneBlue).copy(alpha = 0.18f)` | 合法 |
| 387 | `TerrainKind.PLAIN -> Color(CrossingTokens.bgSurface).copy(alpha = 0.55f)` | 合法 |
| 388 | `TerrainKind.HILL -> Color(CrossingTokens.brandCopper).copy(alpha = 0.10f)` | 合法 |
| 389 | `TerrainKind.MOUNTAIN -> Color(CrossingTokens.brandCopper).copy(alpha = 0.22f)` | 合法 |
| 390 | `TerrainKind.SNOW -> Color(CrossingTokens.textTertiary).copy(alpha = 0.18f)` | 合法 |
| 394 | `FactionRole.OWN -> Color(CrossingTokens.brandGold)`（`pieceColor` 函数体） | 合法 |
| 395 | `FactionRole.ENEMY -> Color(CrossingTokens.stoneBlue)`（`pieceColor` 函数体） | 合法 |
| 396 | `FactionRole.ALLY -> Color(CrossingTokens.brandCopper)`（`pieceColor` 函数体） | 合法 |
| 397 | `FactionRole.NEUTRAL -> Color(CrossingTokens.textTertiary)`（`pieceColor` 函数体） | 合法 |
| 401 | `FactionRole.OWN -> Color(CrossingTokens.brandGold)`（`routeColor` 函数体） | 合法 |
| 402 | `FactionRole.ENEMY -> Color(CrossingTokens.stoneBlue)`（`routeColor` 函数体） | 合法 |
| 403 | `FactionRole.ALLY -> Color(CrossingTokens.brandCopper)`（`routeColor` 函数体） | 合法 |
| 404 | `FactionRole.NEUTRAL -> Color(CrossingTokens.textTertiary)`（`routeColor` 函数体） | 合法 |

### 4.2 非构造调用：标识符含 `Color` 字样，不是 `Color(...)` 构造，无需改

| 行号 | 代码 | 判定 |
| --- | --- | --- |
| 246 | `val color = routeColor(corps.role)` | 直接接 `Color` 返回值，下游 250 行 `color.copy(alpha = ...)`，正确 |
| 268 | `val pieceColor = pieceColor(corps.role)` | 局部 `val` 接住 `Color`；269 / 272 行用 `pieceColor.copy(alpha = ...)`，正确 |
| 393 | `private fun pieceColor(role: FactionRole): Color = when (role) {` | 函数签名返回类型，非调用 |
| 400 | `private fun routeColor(role: FactionRole): Color = when (role) {` | 函数签名返回类型，非调用 |

### 4.3 同类错误（本轮唯一修复点）

| 行号 | 修前代码 | 判定 | 处置 |
| --- | --- | --- | --- |
| 327 | `color = Color(pieceColor(corps.role)),` | 把已是 `Color` 的返回值又包进 `Color(...)`，构造器无 `Color` 入参重载，CI 报 `Color` vs `Int` 错配 | 已改为 `color = pieceColor(corps.role),`；其余同类命中本单只报告、不擅自改 |

## 五、跨目录同款排查

命令：
```
grep -rn "Color(pieceColor\|Color(charColor\|Color(sideColor" app/src/main/java/io/legado/app/ui/map/crossing/
```

- 修复前：仅命中 `shell/CrossingShell.kt:327` 一处（即本单病灶）。
- 修复后：该模式在整个 `crossing/` 目录下**无任何命中**，未发现别的「包了一层 `Color`」同款。
- 附带核对：`routeColor(...)` 的调用点（246 行）为直接取值 + `.copy(alpha=)`，不存在同款包装。

## 六、未验证项

- **未本地编译**：本单按红线未跑 gradle / kotlinc，改动仅经静态核对（类型链、构造器重载、grep 证据）。能否消除 CI 该条报错，**需 CI confirm**。
- 未做运行期渲染核验：327 行文本颜色在真机 / 预览中的实际呈现未验证；类型层面 `TextStyle.color` 收 `Color`，与 `pieceColor` 返回类型一致。
- 本单未复核 `crossing/` 其它文件是否存在「`Color` 包装非 `Long/Int` 源」的变体模式（本次仅按指定三个模式名排查）；如 CI 再报同类错，另派单处理。
