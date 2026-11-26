> 回执补录：原交付无回执，本代理 2026-11-26 现读四份必读（指纹 总控=9ec91a4f8331；子代理=2764066b816e；验收=ef27bd6ac2b4；文档=6dd8114a6a74）并复核 3 条结论，漂移 0 条（见 回执补录-批2-战役回放 §三）
> 回执缺失（规则上线前交付，留档）

# 修复 CI 红 · crossing 测试域 5 文件编译错误

- 范围：仅 `app/src/test/java/io/legado/app/ui/map/crossing/` 下 5 个测试文件；`app/src/main` 零改动（`git diff --stat app/src/main` 为空）。
- 沙箱禁编译；本轮只做编译层修复，不断言阈值、不删用例、不 `@Ignore`。

## 逐条错误 → 修法 → 行号

### A. `engine/CampaignBranchIsolationTest.kt`
- L57 `Map<String?, Int>` 不匹配 `Map<String, Int>`；L58 `Map<String?, String>` 不匹配 `Map<String, String>`。
- 成因：`pointKey` 来自 `firstOrNull()`，键可空；L51 已 `assertNotNull` 判空。
- 修法：L58-59 改 `mapOf<String, Int>(pointKey!! to 2)` / `mapOf<String, String>(pointKey!! to "b")`，显式类型参数对齐 `BranchSnapshot` 非空键签名；未改生产签名。

### B. `engine/CampaignControllerTest.kt`
- L220/221 `Unresolved reference 'isNaN'`：原写 `Double.isNaN(view.lat/lng)`（顶层静态调用不存在）。`view.lat/lng` 为 `Double`，改实例方法 `view.lat.isNaN()` / `view.lng.isNaN()`。
- L338、L365 反引号方法名含非法字符 `/`：`1/4/10 → 3/6/8`、`1/4/10 → 2/5/8` 中 `/` 替换为 `-`；断言逻辑未动。grep 确认无字符串形式引用这两个用例名。

### C. `engine/CampaignJsonParserTest.kt`
- L182 反引号名 `对象/字符串/数字/布尔/JsonNull/空数组` 含 `/`，全部替换为 `-`；断言未动。

### D. `shell/BeatAndSpringTest.kt`
- L91-96 `Cannot infer type for type parameter 'T'`：`springFor(role)` 为生产侧泛型 `<T> -> SpringSpec<T>`，测试脱离 Composable 上下文无上下文类型可推断。
- 修法：六处显式 `springFor<Float>(…)`（阻尼/刚度均为 `Float`，与 `SpringSpec` 字段类型对齐）；未在 `assertEquals` 上硬糊类型参数。

### E. `terrain/HeightMapDecoderTest.kt`
- 根因：编码器签名 `(width, height, colorType, channels, pixel, filter, palette)`，尾随 lambda 被绑到末位 `palette`，致 `pixel` 缺失（L144 类）或实参超位（L360 类）。
- 修法：7 个尾随 lambda 调用点全部改具名实参 `pixel = { x, y -> … }`；L360 同时保留 `palette = palette` 具名。已具名的 L316/323/330/337 不动。

## 自检
- 括号配平（去字符串/注释后）：5 文件 `()` `{}` `[]` 全部相等；反引号数均为偶数。
- grep 回证：`isNaN` 仅剩 2 处 `.isNaN()` 实例调用；反引号名内已无 `/`；`encodePng(` 10 个调用点（含定义 1）全部带 `pixel =`；`springFor` 6 处均为 `<Float>`。
- 改动文件：5 个测试文件；main 源零改动；未执行 git 写操作、未编译。
