# 修复 CI 红：HeightMap PNG 解码与装配 2 个失败用例

CI run 45398476801；HEAD 82e1afc。范围：terrain/HeightMapDecoder.kt + 两个归属测试文件；未编译（沙箱禁编译，CI 为唯一编译手段）。

## 失败 1：M2 色彩类型 2 RGB 取通道平均亮度
- 失败行：`HeightMapDecoderTest.kt:352`，期望 90/255f，实得 120/255f。
- 夹具：`encodePng(1, 2, …)` 宽 1 高 2，lambda 却用 `x` 区分两行；宽 1 时 `x` 恒为 0，两行都写成 (60,120,180)，第二行必然不等于 90。
- 根因判定：**测试夹具笔误，非解码器错位**。解码器行优先 `data[row*width+col]`，1×2 时 data[0]=行0、data[1]=行1，与 PNG 规范及实现一致；`1 乘 1 图`等既有用例证明逐行取值正确。穷举同文件其余 `encodePng`：仅本例误用 `x`；`2×1` 调色板用例按 `x` 取值正确，其余为常量或 2×2 双坐标，无同类笔误。
- 修法：lambda 改按行坐标 `y` 取值（`{ _, y -> if (y == 0) … }`），期望值 120 / 90 不动；注释标明宽 1 时 x 恒 0。

## 失败 2：M5 内联合法 PNG 经装配链转为 0..255 栅格并挂 internal 原始栅格
- 失败行：`HeightMapWiringTest.kt:77`，`degraded` 为 true。
- 链路：`CampaignController.resolveHeightMap` → `CampaignJsonParser.decodeHeightMap` → `HeightMapDecoder.decode` → `classify`。
- 根因：1×1 灰度 PNG 仅 67 字节，base64 92 字符 < `BASE64_MIN_LENGTH=200`，`looksLikeBase64` 判 false → 回落 ASSET；reader 为 null → `synthesize` 合成场并置 `degraded=true`。解码器本身可正常解该图（签名/IHDR/单行扫描线/filter 0/inflate 2 字节全部通过，`1 乘 1 图`单测佐证）。1×1 未被任何尺寸护栏拒绝。
- 修法（仅改 HeightMapDecoder.kt）：classify 在长度阈值之外加一道 **PNG 签名探针**——短串若 base64 字符集合法且解出的前 8 字节命中 PNG 签名 `89 50 4E 47 0D 0A 1A 0A`，判 BASE64；解码异常 / 长度不足 / 签名不符仍落 ASSET。抽出 `isBase64Charset` 复用原字符集与填充位规则；探针按无符号 Int 比对字节，规避 Byte/Int 字面量 `==` 类型不符恒 false。
- 契约保持：M4「11 字符 base64 样式短串落 ASSET」仍成立（解出 7 字节、签名不符）；路径 / file:// / data: / 长串分类顺序不变；探针失败仍走既有合成兜底，不抛异常。

## 为何不弱化断言
- M2：未改期望值与容差，只把夹具坐标对齐图的真实维度（y 行）。
- M5：`assertFalse(…degraded)` 原样保留，语义仍是「合法 PNG 不得 degraded」；未改成 assertTrue、未删断言。失败由分类根因消除，而非放宽验收。
- 未触碰 `CampaignController.kt`（另一代理独占）；degraded 置位在 controller:1204-1206 仅如实转述 `decoded.degraded`，根因不在该处，无需甲代理改动。

## 自检
- 三文件括号配平差 0（裸计数 HEAD 与工作区一致；解析器剥离误差已比对排除）。
- 新增回归单测：短 base64 极小 PNG 凭探针判 BASE64，对照串仍落 ASSET。
- classify 11 条既有/新断言口径经 Python 仿真全部通过。
