# 锚点卡 · Q238 DEM 缓存
> 对象：`terrain/HeightMapDecoder.kt` 实例级 LRU 缓存；修复报告 `docs/修复-DEM双解码缓存.md`。消除同一高程源沙盘 / 回放二次解码，解码算法不动。

## 一、事实锚点（逐字，含 文件:行）

- 证据链路：`CampaignJsonParser.kt:69` `decodeInline` 探针；`:372-373` `decodeHeightMap` → `HeightMapDecoder.decode`；`CampaignController.kt:1289` 装配调用；解码本体 `HeightMapDecoder.kt:301` `decodeBytes`。
- 键与槽：`:91` `CACHE_CAPACITY=2`；`:100` `CacheKey(contentHash, width, height)`；`:102` `CacheSlot(key, raster, lastUsed)`；`:105` `rasterCache` 哈希索引；`:201-204` `sha256Hex16` = SHA-256 前 16 位。
- 计数：`:108` `cacheHits`、`:112` `cacheMisses`、`:116` `cacheEvictions`、`:120` `cacheSize`、`:123-126` `cacheEvictLog`（环形 16 条 `:94`）；通路 `:213-229` `cachedDecode` 命中摘出重插、未命中走 `decodeBytes`，null 不写槽；`:232-242` `evictIfNeeded` LRU 逐出最旧槽；`:246-252` `resetCache`；接线 `:272` 资产/路径、`:288` 内联。
- 夹具：`HeightMapDecoderTest.kt:421` 二次命中同实例；`:439` 坏 Base64/非 PNG 不入库；`:454` 超容量 LRU 逐出+台账；`:470` resetCache 清零。

## 二、参数表

| 项 | 值/口径 | 出处 |
|---|---|---|
| 缓存键 | 源字节 sha256[:16] + 栅格宽 × 高 | :100、:201-204 |
| 容量 | 2 槽，LRU（lastUsed 最旧先逐） | :91、:232-242 |
| 失败口径 | null 结果不写槽；合成回落场不入库 | :222-224、:266-278 |
| 可观测 | hits / misses / evictions / size / evictLog | :108-126 |
| 线程 | cachedDecode、resetCache @Synchronized | :212、:245 |

## 三、结论锚点

- C1 同一高程源二次装配走缓存命中，不再二次 inflate / 反滤波；解码结果与未命中一致。
- C2 坏 Base64 / 非 PNG / 畸形栅格不缓存；异常态每次重判，不会被缓存成命中。
- C3 超 2 份按 LRU 逐出并留台账；缓存挂单例，沙盘与回放共用同一份。
- C4 本单只改解码器本体与其单测；不碰其他代理文件、不 push、不本地编译。

## 四、回源

- 报告：`docs/修复-DEM双解码缓存.md`（证据链路 / 设计行号 / grep 回证 / 4 条夹具 / lint 输出 / 二阶三阶影响 / 新发现问题）。
