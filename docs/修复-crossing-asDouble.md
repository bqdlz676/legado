> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复-crossing `CampaignJsonParser` 中 `asDouble` 未解析（CI run `45171948572`）

- 基线：仓库 `/home/user/Doubao/chats/38454122521460994/legado`。
- 改动文件域（仅此一个）：`app/src/main/java/io/legado/app/ui/map/crossing/engine/CampaignJsonParser.kt`。
- 任务范围：**只修** `asDouble` 未解析这一个问题；未重构、未改其它方法、未动 `campaigns/*.json` 资产，未碰 shell / model / terrain / 其它域。
- 红线遵守：未跑本地 gradle / kotlinc 编译；未做任何 git 写操作（提交由总控收口）。

## 一、报错原文（CI run `45171948572`，`/tmp/jd30.log`）

```
engine/CampaignJsonParser.kt:262:29 None of the following candidates is applicable:
engine/CampaignJsonParser.kt:262:43 Unresolved reference 'asDouble'.
engine/CampaignJsonParser.kt:263:29 None of the following candidates is applicable:
engine/CampaignJsonParser.kt:263:43 Unresolved reference 'asDouble'.
```

CI 候选清单（`fun <T> Array<out T>.getOrNull(index: Int): T?`、`List<T>.getOrNull`、各基本类型数组 `getOrNull` 等）全部因 receiver 类型不匹配而不可用。

## 二、JSON 类型确认

该文件使用的是 **Gson**（非 `org.json.JSONObject`，也非自写 `JsonValue`）：

- 文件头既有 import（第 3–5 行，本轮未增删）：
  ```kotlin
  import com.google.gson.JsonElement
  import com.google.gson.JsonObject
  import com.google.gson.JsonParser
  ```
- 依赖声明：`app/build.gradle:277` `implementation(libs.gson)`；`gradle/libs.versions.toml:21` `gson = "2.14.0"`。Gson 本就已在 app 主源集，**本轮没有为它新引入任何依赖**。

## 三、根因

1. **`JsonArray` 在 Gson 2.14.0 中不是 `List`**。`javap` 核对该 jar：
   ```
   public final class com.google.gson.JsonArray extends com.google.gson.JsonElement
       implements java.lang.Iterable<com.google.gson.JsonElement>
   ```
   它只实现 `Iterable`，自带 `public JsonElement get(int)`，**没有**实现 `List<JsonElement>`。HEAD 报错行写的是 `arr.getOrNull(0)` / `arr.getOrNull(1)`，而 stdlib 的 `getOrNull` 扩展只挂在 `List` / 数组 / 基本类型数组上，故全部候选不可用；receiver 退化为错误类型后，其上的 `?.asDouble` 连带报「Unresolved reference 'asDouble'」。
2. **`.asDouble` 是 `getAsDouble()` 的 Kotlin 合成属性写法**。本轮不依赖合成属性名推导，改走 Java 直调方法 `getAsNumber()`，再用 stdlib `Number.toDouble()` 转双精度——与本主源集既有数字取值先例一致（`app/src/main/java/io/legado/app/utils/GsonExtensions.kt:170` `prim.asNumber.toInt()`、`:233` `val num: Number = prim.asNumber`、`:237/:240` `num.toDouble()`）。
3. `getAsNumber()` 经 `javap` 核对确为 `JsonElement` 上已声明方法：
   ```
   public java.lang.Number getAsNumber();
   ```
   （同包 `JsonPrimitive` 上亦有同名方法与 `isNumber()`。）

## 四、兜底语义与兜底值依据

规格铁律：畸形 JSON 不许抛到 UI；字段缺失 / 类型错位 / 非数一律回落默认值。本轮三处取值点的兜底通道全部保留，**未改任何默认实参**：

| 取值点 | 兜底通道（改前 → 改后一致） | 兜底值依据 |
|---|---|---|
| `guardedPoint`（河流单点） | 外层 `try/catch (t: Throwable) → null`；`x/y` 非有限值亦返回 `null` | 不设数值默认；点列元素非法即放弃该点，由 `parseTerrain` 汇总后按「有效点列不足 2 个」记 warning 并跳过该河 |
| `dbl`（对象字段取数） | 元素非 primitive → 返回调用方传入的 `default`；primitive 但非数 → `getAsNumber()` 抛异常 → `catch` → `default` | 沿用各调用点既有兜底：`focus.radius` 50.0、`wind` 0.0、`temperature` 15.0、`strength` 5.0、`fatigue` 0.0、ridge `x/y/h` 0.5、`w` 0.2、`duration` 经 `int()` 落 `DEFAULT_DURATION`(30)。本轮一个默认值都未改 |
| `geo`（`[lng, lat]` 数组取经纬度） | 外层 `try/catch (t: Throwable) → null`；长度不足 2 提前返回 `null` | 非法点位返回 `null`，由调用方各自回落（如 `focus.center` / `initialPos` `?: Geo(0.0, 0.0)`），本轮未动调用方 |

## 五、逐行改动对照

文件：`app/src/main/java/io/legado/app/ui/map/crossing/engine/CampaignJsonParser.kt`

### 5.1 `guardedPoint`（河流点列元素）

| 版本 | 行号 | 代码 |
|---|---|---|
| HEAD / CI 报错版 | 262 | `val x = arr.getOrNull(0)?.asDouble ?: 0.0` |
| HEAD / CI 报错版 | 263 | `val y = arr.getOrNull(1)?.asDouble ?: 0.0` |
| 改前工作树 | 293 | `val x = arr[0].asDouble` |
| 改前工作树 | 294 | `val y = arr[1].asDouble` |
| **改后** | **295** | `val x = arr[0].getAsNumber().toDouble()` |
| **改后** | **296** | `val y = arr[1].getAsNumber().toDouble()` |

改后 293–294 行为两行说明注释（取值方法与兜底理由），无逻辑变更。

### 5.2 `dbl`（通用字段取数）

| 版本 | 行号 | 代码 |
|---|---|---|
| HEAD | 313 | `if (el.isJsonPrimitive) el.asDouble else default` |
| 改前工作树 | 339 | `if (el.isJsonPrimitive) el.asDouble else default` |
| **改后** | **341** | `if (el.isJsonPrimitive) el.getAsNumber().toDouble() else default` |

### 5.3 `geo`（经纬度数组）

| 版本 | 行号 | 代码 |
|---|---|---|
| HEAD | 326 | `Geo(lat = arr[1].asDouble, lng = arr[0].asDouble)` |
| 改前工作树 | 352 | `Geo(lat = arr[1].asDouble, lng = arr[0].asDouble)` |
| **改后** | **354** | `Geo(lat = arr[1].getAsNumber().toDouble(), lng = arr[0].getAsNumber().toDouble())` |

> 说明：CI 日志只点了 262/263 两条；HEAD 313/326 与工作树 339/352 是同一标识符 `asDouble` 的其余出现位置。门禁要求 `app/src` 内 `asDouble` 归零，故同根因四处一并消除；除此之外未改任何方法体。

## 六、grep / 依赖证据

### 6.1 门禁①：`asDouble` 全 `app/src` 归零

```
$ grep -rn "asDouble" app/src
（无命中，exit=1）
```

### 6.2 新取值方法在依赖中的定义核对

Gson 2.14.0 jar（本地缓存 `com.google.code.gson/gson/2.14.0/.../gson-2.14.0.jar`）：

```
$ javap -classpath .../gson-2.14.0.jar com.google.gson.JsonElement | grep getAsNumber
  public java.lang.Number getAsNumber();

$ javap -classpath .../gson-2.14.0.jar com.google.gson.JsonArray | grep "get(int)"
  public com.google.gson.JsonElement get(int);
```

- `getAsNumber()`：`JsonElement` 已声明，返回 `java.lang.Number`。
- `JsonArray.get(int)`：已声明，`arr[0]` / `arr[1]` 的 `[]` 运算符映射到该方法。
- `Number.toDouble()`：kotlin-stdlib 标准库扩展，无需 import。

### 6.3 import 核对（门禁③）

本轮**未新增任何 import**。文件头仍为既有的 `com.google.gson.JsonElement / JsonObject / JsonParser` 三个 Gson import 与 model 包 import；`getAsNumber` 是已导入类型 `JsonElement` 的成员方法，`toDouble()` 是 stdlib 扩展，不存在指向不存在包的新 import。

### 6.4 同主源集先例（既有可编译写法）

```
$ grep -n "asNumber" app/src/main/java/io/legado/app/utils/GsonExtensions.kt
170:                    prim.asNumber.toInt()
233:                        val num: Number = prim.asNumber
237:                        return if (ceil(num.toDouble()) == num.toLong().toDouble()) {
240:                            num.toDouble()
```

## 七、未改动项核对

- 未改 `campaigns/*.json` 等任何资产文件；
- 未改 `str / strOrNull / int / fallback / parseTerrain / parseFocus / parseWeather / parseVictory / parseSides / parseDiplomacy / parseArmies / parseEvents` 等其它方法签名与函数体；
- 未在引擎包外新增或改动文件；本轮文件域仅限 `CampaignJsonParser.kt`。

## 八、未验证项（如实声明）

1. **未编译，需 CI**：按红线未在本地执行 gradle / kotlinc；本轮仅做静态核对（方法存在性、receiver 类型、兜底通道、行号落盘）。`:app:compileAppDebugKotlin` 是否转绿，需总控推 CI 复跑确认。
2. `.asDouble` 合成属性为何在当前 Kotlin（`2.4.20`）+ Gson 2.14.0 组合下未解析，其编译器内部原因本轮未做本地编译定论；改法已绕开合成属性、直调 `getAsNumber()`，该风险点随 CI 编译一并验证。
3. 运行期行为（畸形点列、非数字 primitive、布尔 / 字符串 primitive 走兜底分支）本轮未执行单测；既有 `CampaignJsonParserTest.kt` 未覆盖河流点列取数，相关断言待 CI 与后续测试补充。
4. 同一次 CI 日志中 `CombatResolver.kt` / `IntentResolver.kt` / `MoraleSystem.kt` / `Pathfinder.kt` 另有报错，属其它工单范围，本轮未触碰。
