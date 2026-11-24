# 反思 · terrain B13 地形栅格解码

## 1. 做了什么

基线 `b13f5b2`，新增 `terrain/HeightMapDecoder.kt`（纯 JVM 三类来源 PNG 解码 + 哈希确定性合成回落 + 超限降采样护栏），`CampaignJsonParser.kt` 接分类/内联解码并暴露 `decodeHeightMap` 既有 API，新增 12 个 JVM 单测。未编译、未跑测。

## 2. 踩的坑 / 教训

- **两套栅格口径并存**：既有 `HeightRaster` 是 0..255 IntArray，本单 `HeightMapRaster` 是 0..1 FloatArray。动手前先读 `HeightRaster.kt`/`CrossingRasterBridge.kt` 确认存量口径，别新造第三套值域；转换点留给接线单。
- **止损口径要先定**：任务书允许 BitmapFactory 快速路径，但纯 JVM 单测不可用 → 直接把字节→栅格抽成零依赖纯函数（Inflater + 自写反滤波），不要留半吊子 Android 引用导致引擎层无法单测。
- **护栏要分软硬两档**：软上限（2048/4MP）解码后降采样；硬上限（8MP/8192）在分配缓冲前拒绝。只做软上限会在 inflate 阶段先 OOM。

## 3. 我可能判断错的地方

- PNG 反滤波 Sub/Up/Average/Paeth 只做了代码推演，测试编码器只产 filter 0；真实工具导出的 PNG 常用 Paeth，首轮对拍可能出像素偏差。
- `Inflater` 解压后按 `n == raw.size` 严格相等校验；若编码器在 IDAT 内夹带附加块，可能误判失败回落合成。
- base64 启发式判定（无 `/`、长度 ≥32、字符集合法）可能把长资产名误判为 BASE64；现存素材未触发，但换素材后需复核。

## 4. 遗留未决项

- model 未接纳栅格字段；平台 reader 未注入；controller 未接线（B7 在飞）。
- 16-bit 深 / Adam7 隔行 / 非支持色彩类型显式回落合成，未实现解码。
- 内存峰值为估算，未 profiler；单测未在 JVM 实跑。

## 5. 给下任的一句话提醒

接 controller 装配时，先回仓库核对 `HeightRaster.create` 的 0..255 口径与 `CrossingRasterBridge.resample` 的重采样方向（行 0=北缘），再决定 `HeightMapRaster` 是转 IntArray 喂存量栅格还是整体替换；不要照抄本报告的内存估算数字，以 CI profiler 为准。
