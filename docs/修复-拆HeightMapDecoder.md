# 修复 · 拆 HeightMapDecoder 缓存层（Q-245）

> 边界：本单只碰 terrain 域两个文件——`HeightMapDecoder.kt` 瘦身、新建 `HeightMapRasterCache.kt`；`HeightMapDecoderTest.kt` 断言一字未改。未 git 写、未本地编译，仅跑 `kotlin-syntax-lint.py` 与 `check-frankenstein.py`。
> 锚点卡：docs/锚点/Q245-拆HeightMapDecoder.md。

## 一、任务与产物

- 现状（拆前）：`HeightMapDecoder.kt` 603 行，跨过 `check-frankenstein.py` `LINE_WARN=600` 候选线，[3] 职责混装提示命中。
- 拆后真实行数（`wc -l`）：

```
$ wc -l app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt \
        app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapRasterCache.kt
546 app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt
101 app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapRasterCache.kt
```

- `HeightMapDecoder.kt` 546 行，回到 600 以下；缓存层独立成 101 行的 `HeightMapRasterCache.kt`。

## 二、职责搬迁清单

迁入 `HeightMapRasterCache.kt`（object，同包 `…terrain`）：

- `CacheKey`（contentHash + width + height）、`CacheSlot`（key + raster + lastUsed）；
- `rasterCache`（LinkedHashMap 哈希索引）；
- 计数 `cacheHits` / `cacheMisses` / `cacheEvictions`、观测 `cacheSize`、台账 `cacheEvictLog`；
- `evictIfNeeded`（LRU 逐出 + 16 条环形台账）；
- `resetCache`；
- 单一入口 `getOrDecode(contentHash, producer)`：命中摘出重插刷新 LRU；未命中调生产者解码，null/异常只记未命中、不写槽位。

留在 `HeightMapDecoder.kt`：

- PNG 解码本体 `decodeBytes` 与反滤波 / 调色板 / 降采样 / 合成回落；
- 来源分类 `classify` 与 base64 字符集 / 签名探针；
- 装配入口 `decode` / `decodeInline`；
- 哈希计算 `sha256Hex16`（纯 CPU、局部变量，随解码侧保留）；
- 缓存观测门面：`cacheHits` / `cacheMisses` / `cacheEvictions` / `cacheSize` / `cacheEvictLog` / `resetCache` 只读转发到缓存对象。

## 三、对外行为与计数语义不变性

- 测试 `HeightMapDecoderTest.kt` 未改动；4 个 Q-238 缓存用例仍经 `HeightMapDecoder` 门面访问计数与台账：
  - `Q238 同一内联 PNG 二次解码走缓存命中…`：`cacheMisses` / `cacheHits` / `cacheSize` / 同实例 `===`；
  - `Q238 坏 Base64 与非 PNG 解码失败均不入库缓存`：`cacheSize` / `cacheMisses` / `cacheHits` / `cacheEvictions`；
  - `Q238 超容量 2 份按 LRU 逐出…`：`cacheSize` / `cacheEvictions` / `cacheEvictLog`；
  - `Q238 resetCache 清空槽位与计数`：四项计数与台账清空。
- 门面转发签名与原公开面一致：`var` 计数在测试侧只读断言，转发为 `val get()`，语义等价；`resetCache()` 委托缓存对象同步清空。
- 失败口径不变：生产者返回 null 或抛异常记 `cacheMisses++`，不写槽位；合成回落场不入缓存。
- 外部调用面未变：`CampaignJsonParser.kt:66/:69/:373` 只用到 `classify` / `decodeInline` / `decode`，均保留在解码器。

## 四、并发与锁边界

- 拆前：`cachedDecode` 标 `@Synchronized`，监视器是 `HeightMapDecoder` object；哈希计算、缓存查命中、`decodeBytes`、写槽位全在同一把锁内。
- 拆后：
  - `HeightMapRasterCache.getOrDecode` 与 `resetCache` 标 `@Synchronized`，监视器是缓存 object；缓存查命中、生产者解码、LRU 逐出、写槽位在锁内；
  - `sha256Hex16` 哈希计算移出缓存锁（纯 CPU、局部变量，不触碰共享状态）；
  - `rasterCache` / `evictLog` / 三个计数只被缓存对象方法读写，解码器门面只读，不回写。
- 竞态核查：缓存共享状态仍单锁保护，无第二把锁交叉；生产者 `decodeBytes` 是纯函数（无外部回调、不触碰缓存），锁内执行不会回调 UI 或其它组件。
- 二阶差异（如实记录）：未命中并发时，同一字节源可能在两线程各自解码一次后再串行写槽位；拆前锁内串行只解码一次。结果确定性不变（同输入同输出、命中返回同一栅格实例），仅瞬时 CPU 可能重复一次解码；槽位与计数不出现竞争。

## 五、关键 diff（接线前后）

解码器侧（门面转发，替换原缓存状态字段）：

```kotlin
// 改前：私有状态与计数全部挂在解码器 object 上
private val rasterCache = LinkedHashMap<String, CacheSlot>()
var cacheHits: Int = 0; private set
var cacheMisses: Int = 0; private set
var cacheEvictions: Int = 0; private set
val cacheSize: Int get() = rasterCache.size
val cacheEvictLog: List<String> get() = evictLog.toList()

// 改后：只读门面转发
val cacheHits: Int get() = HeightMapRasterCache.cacheHits
val cacheMisses: Int get() = HeightMapRasterCache.cacheMisses
val cacheEvictions: Int get() = HeightMapRasterCache.cacheEvictions
val cacheSize: Int get() = HeightMapRasterCache.cacheSize
val cacheEvictLog: List<String> get() = HeightMapRasterCache.cacheEvictLog
fun resetCache() = HeightMapRasterCache.resetCache()
```

缓存接线（替换原 `cachedDecode` 锁内全套逻辑）：

```kotlin
// 改前：cachedDecode 自己查 map / LRU / 逐出 / 计数
@Synchronized
private fun cachedDecode(bytes: ByteArray, source: SourceKind): HeightMapRaster? { … }

// 改后：哈希留在解码器，槽位逻辑委托缓存单一入口
private fun cachedDecode(bytes: ByteArray, source: SourceKind): HeightMapRaster? {
    val hash = sha256Hex16(bytes)
    return HeightMapRasterCache.getOrDecode(hash) { decodeBytes(bytes, source) }
}
```

## 六、二阶三阶影响：持有者与跨战役生命周期

- 谁持有：缓存是 `internal object HeightMapRasterCache`，进程级单例，与原解码器 object 同生命周期（进程存活即在册）；解码器不再持任何栅格状态，只保留无状态解码函数与门面。
- 跨战役：槽位上限仍为 2、LRU 逐出口径不变；战役切换时旧战役栅格靠容量上限自然逐出。`resetCache()` 仍是唯一显式清槽入口，测试与排障调用路径不变。
- 三阶：未来若缓存键策略变化（加盐 / 版本号 / 尺寸入键调整），改动面收在缓存对象；解码器只传哈希，不感知键结构。
- 三阶：门面使测试与外部代码继续挂 `HeightMapDecoder`，后续若要把观测面迁到缓存对象，需同步调测试归属（见 §七 问题 2）。

## 七、新发现问题

1. 缓存为进程级单例，跨战役切换链路未见显式 `resetCache` 调用；旧战役栅格靠 2 槽上限自然逐出，极端顺序下新战役前两次解码可能命中旧槽哈希——**已入队观察**：核查战役切换 / 回放重载链路是否需要主动清缓存。
2. 缓存计数门面仍挂在 `HeightMapDecoder` 上，测试未直连缓存对象；两层命名容易让后续接手人误以为状态在解码器里 —— **待裁**：是否在下一轮把测试断言迁到 `HeightMapRasterCache` 直连，门面仅作兼容。
3. `sha256Hex16` 留在解码器侧，缓存键策略（哈希位宽 / 是否加盐）跨两个文件约定；未来调键需同步两处 —— **待裁**：是否把哈希函数一并收进缓存对象，解码器只传字节。
4. 沙箱无 JDK，拆分后 `HeightMapDecoderTest` 全量未本地运行；计数与 LRU 语义为代码等价推演 —— **已入队**：CI 跑完 terrain 包单测后回证 4 个 Q238 用例全绿。

## 八、未验证项

- 未跑本地编译、未跑单测；`kotlin-syntax-lint.py` 静态检查通过，红绿以云端 CI 为准。
- 未在并发压力下实测未命中重复解码的 CPU 影响；二阶差异为代码推演。
- 其它 module / 外部脚本未引用本缓存私有成员（已 grep 全仓 main 源确认）。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=e9560a5c333b；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`："不许 add/commit/push/revert/checkout——提交由总控统一做。"
2. `docs/机制-子代理必读.md:12`："不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。"
3. `docs/机制-子代理必读.md:18`："每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。"
- 适用条款：
  - 一条缺陷一单：本单只拆 terrain 域解码器与缓存两个文件；测试断言与其它业务代码未改。
  - 禁 git 写操作：未执行 add/commit/push/revert/checkout（子代理必读 :11）。
  - 禁本地编译：未跑 gradle/kotlinc/assemble；仅 python3 跑 lint / 门禁（子代理必读 :12）。
  - grep 回证：搬迁清单、门面行号、外部调用点均以 grep / Read 现取现核，旧私有符号在解码器侧清零（子代理必读 :18）。
  - 未验证项声明：CI 单测、并发压力、跨战役清理链路已在第七、八节显式列出（子代理必读 :20）。

## 九、门禁自检输出

```
$ wc -l app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt \
        app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapRasterCache.kt
546 app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt
101 app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapRasterCache.kt

$ python3 data_scripts/kotlin-syntax-lint.py app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapRasterCache.kt
KOTLIN-LINT: PASS（2 个文件）
rc=0

$ python3 data_scripts/check-frankenstein.py
[3] 职责混装(提示,不单独FAIL): ≥600 行 50，≥1200 行 14
FRANKENSTEIN: PASS
rc=0
（HeightMapDecoder / HeightMapRasterCache 均未进入 ≥600 行 oversized 清单）

$ python3 data_scripts/check-onboarding.py --audit
ONBOARD-AUDIT: 0 份报告缺回执或不合规（历史留档可批量补，见 Q-199）
rc=0

$ python3 data_scripts/check-anchor-card.py --strict docs/修复-拆HeightMapDecoder.md
汇总: PASS=1 FAIL=0（共 1 份）
rc=0

$ python3 data_scripts/check-problem-finding.py docs/修复-拆HeightMapDecoder.md
INFO 历史欠账 5 份走基线（只许缩小）：修复-InkContentEditState-平台签名撞名.md、修复-全仓非UTF8复扫.md、修复-吞异常三文件.md、修复-空集假绿检测器.md、分析-不等待七阶推演.md
PROBLEM-FINDING: PASS（1 份报告均带新发现问题与处置状态）
rc=0

$ python3 data_scripts/check-unpushed-reports.py
UNPUSHED-REPORTS: PASS（本轮 4 份报告（含已提交未推送）回执合规）
rc=0
```
