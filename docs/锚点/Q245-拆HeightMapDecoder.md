# 锚点卡 · Q-245 拆 HeightMapDecoder 缓存层（546 + 101 行）

> 对象：`app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt` 与新建 `HeightMapRasterCache.kt`；回执报告 `docs/修复-拆HeightMapDecoder.md`。

## 一、事实锚点（逐字，含 文件:行）

- 拆前 `HeightMapDecoder.kt` 603 行；拆后 `wc -l`：decoder 546 行、cache 101 行。
- 搬迁：`CacheKey` / `CacheSlot` / `rasterCache` / `cacheHits` / `cacheMisses` / `cacheEvictions` / `cacheSize` / `cacheEvictLog` / `evictIfNeeded` / `resetCache` 全部入 `HeightMapRasterCache.kt`；单一入口 `getOrDecode(contentHash, producer)`。
- 解码器门面：`HeightMapDecoder.kt:91` `cacheHits`、`:94` `cacheMisses`、`:97` `cacheEvictions`、`:100` `cacheSize`、`:103` `cacheEvictLog`、`:106` `resetCache()` 转发缓存对象；`:194` `HeightMapRasterCache.getOrDecode(hash) { decodeBytes(bytes, source) }`。
- 外部调用：`CampaignJsonParser.kt:66` `classify`、`:69` `decodeInline`、`:373` `decode`，公开面未变。
- 测试 `HeightMapDecoderTest.kt` 未改；4 个 Q238 用例经门面断言计数 / 台账 / 同实例。

## 二、参数表

- 槽位上限 2；逐出台账环形 16 条；缓存键 = sha256 前 16 位 + 宽 × 高。
- 锁：`getOrDecode` / `resetCache` `@Synchronized`（缓存 object 监视器）；哈希计算移出锁；生产者锁内纯解码。
- 阈值：`check-frankenstein.py:43` `LINE_WARN=600`、`:44` `LINE_HARD=1200`。

## 三、结论锚点

- C1 decoder 546 行回到 600 以下，缓存独立 101 行；失败不入库、LRU 逐出、计数语义与原实现等价。
- C2 缓存为进程级单例，跨战役靠 2 槽自然逐出；`resetCache` 仍是唯一显式清槽入口。
- C3 未命中并发时同字节源可能重复解码一次，结果确定性不变；未跑 CI，红绿以云端为准。

## 四、回源

- 回执报告：`docs/修复-拆HeightMapDecoder.md`（§二 搬迁清单、§三 不变性、§四 锁边界、§五 diff、§六 生命周期、§七 新发现问题处置状态）。
