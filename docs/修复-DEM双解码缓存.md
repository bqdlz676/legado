# 修复-DEM 双解码缓存（Q-238）

> 对象：`app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt`（高程栅格解码器，纯 JVM 单例）。现状同一高程源被沙盘装配与回放通路各解码一次，无缓存；本单在解码器内加实例级 LRU 缓存层，不改解码算法本身。
> 边界：本单只改解码器本体与其单测；不碰其他代理文件、不 push、不本地编译（沙箱无 JDK），仅跑 `kotlin-syntax-lint.py` 并 grep 回证。
> 锚点卡：docs/锚点/Q238-DEM缓存.md。

## 一、任务与产物

- 改动 1：`terrain/HeightMapDecoder.kt` 新增实例级缓存：键＝高程源内容 sha256 前 16 位 + 解码栅格尺寸，值＝解码后栅格；沙盘与回放共用解码器单例这一份缓存。
- 改动 2：命中 / 未命中 / 逐出计数可观测；容量上限 2 份，超出按 LRU 逐出并留逐出台账；空数据 / 坏 Base64 / 非 PNG 等失败结果不入库，不缓存成命中。
- 改动 3：单测 `HeightMapDecoderTest.kt` 补 4 条夹具（二次命中、失败不入库、超容量 LRU 逐出、resetCache 清零）。
- 未改动：`decodeBytes` PNG 解码本体（签名 / IHDR / IDAT inflate / 反滤波 / 降采样）一行未动；未接 rule-gate / CI 脚本，未改壳层桥接。

## 二、双解码证据（现状链路，逐字回源）

- `CampaignJsonParser.kt:69`：解析期探针直接走 `HeightMapDecoder.decodeInline(heightMap)`，失败仅记 warning。
- `CampaignJsonParser.kt:372-373`：既有 API `decodeHeightMap` 转发 `HeightMapDecoder.decode(raw, reader)`。
- `CampaignController.kt:1289`：`resolveHeightMap` 经 `CampaignJsonParser.decodeHeightMap(raw, reader = null)` 装配沙盘栅格。
- 解码本体 `HeightMapDecoder.kt:301` `decodeBytes`（PNG 签名 / IHDR / IDAT inflate / 扫描线反滤波）；内联通路 `:282` `decodeInline`；统一入口 `:262` `decode`。同一内联高程源在解析探针与装配点各走一遍 inflate + 反滤波，无复用。

## 三、缓存层设计（行号 / 口径 / 回证）

### 3.1 缓存键与槽位

- `:91` `CACHE_CAPACITY = 2`：最多 2 份最近使用的真实解码栅格。
- `:100` `CacheKey(contentHash, width, height)`：内容哈希 sha256 前 16 位 + 栅格宽高；同字节源确定性产出同尺寸栅格，尺寸入键防哈希碰撞串味。
- `:102` `CacheSlot(key, raster, lastUsed)`：槽位持栅格、键与最后使用时间戳（`System.nanoTime()`）。
- `:105` `rasterCache = LinkedHashMap<String, CacheSlot>()`：按源字节哈希索引，解码前即可命中，免去二次 inflate / 反滤波；尺寸身份留在 `CacheKey`。
- `:201-204` `sha256Hex16`：`MessageDigest.getInstance("SHA-256")` 取摘要前 16 位十六进制，锚内容不锚行号 / 不锚字符串写法。

### 3.2 命中 / 未命中 / 逐出可观测计数

- `:108-109` `cacheHits`：缓存直接返回栅格、未走解码本体。
- `:112-113` `cacheMisses`：实际进入解码本体（含解码失败、未入缓存的坏数据）。
- `:116-117` `cacheEvictions`：LRU 逐出次数。
- `:120` `cacheSize`：当前在册槽位数。
- `:123-126` `cacheEvictLog`：最近逐出文本台账（环形 16 条，`:94` `EVICT_LOG_LIMIT`），纯 JVM 文本记录，不引 `android.util.Log`，保持解码器零 Android 依赖。

### 3.3 缓存通路与失败口径

- `:213-229` `cachedDecode`（`@Synchronized`）：先按哈希查槽；命中则摘出重插刷新 LRU 顺序、`cacheHits++` 返回同实例；未命中才调 `decodeBytes`，`cacheMisses++`；返回 null（空字节 / 坏 Base64 / 非 PNG / 畸形栅格）直接 return，**不写槽**。
- `:232-242` `evictIfNeeded`：在册数 ≥ 2 时逐出 `lastUsed` 最旧槽位，`cacheEvictions++`，逐出原因（被逐哈希 / 尺寸、新槽哈希 / 尺寸、容量上限）写入台账，超 16 条丢最旧一条。
- `:246-252` `resetCache`：清空槽位、台账与三项计数，供测试 / 排障隔离用例间污染。
- 接线点：`:272` 资产 / 本地路径分支 `cachedDecode(b, kind)`；`:288` 内联分支 `cachedDecode(bytes, SourceKind.BASE64)`。合成回落场（`degraded = true`）不经缓存通路，不入库。

### 3.4 grep 回证

```
$ grep -n "CACHE_CAPACITY\|CacheKey\|CacheSlot\|rasterCache\|cacheHits\|cacheMisses\|cacheEvictions\|cachedDecode\|evictIfNeeded\|resetCache\|MessageDigest" \
  app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt
3:import java.security.MessageDigest
91:    private const val CACHE_CAPACITY: Int = 2
100:    private data class CacheKey(val contentHash: String, val width: Int, val height: Int)
102:    private data class CacheSlot(val key: CacheKey, val raster: HeightMapRaster, val lastUsed: Long)
105:    private val rasterCache = LinkedHashMap<String, CacheSlot>()
108:    var cacheHits: Int = 0
112:    var cacheMisses: Int = 0
116:    var cacheEvictions: Int = 0
120:    val cacheSize: Int get() = rasterCache.size
123:    val cacheEvictLog: List<String>
126:    private val evictLog = mutableListOf<String>()
201:    private fun sha256Hex16(bytes: ByteArray): String {
202:        val digest = MessageDigest.getInstance("SHA-256")
213:    private fun cachedDecode(bytes: ByteArray, source: SourceKind): HeightMapRaster? {
214:        val hash = sha256Hex16(bytes)
215:        rasterCache[hash]?.let { slot ->
217:            rasterCache.remove(hash)
218:            rasterCache[hash] = slot.copy(lastUsed = System.nanoTime())
219:            cacheHits++
223:        cacheMisses++
225:        val key = CacheKey(hash, raster.width, raster.height)
226:        evictIfNeeded(key)
227:        rasterCache[hash] = CacheSlot(key, raster, System.nanoTime())
232:    private fun evictIfNeeded(insertingKey: CacheKey) {
233:        while (rasterCache.size >= CACHE_CAPACITY) {
234:            val victim = rasterCache.entries.minByOrNull { it.value.lastUsed } ?: break
235:            rasterCache.remove(victim.key)
236:            cacheEvictions++
246:    fun resetCache() {
272:                val raster = bytes?.let { b -> cachedDecode(b, kind) }
288:        return cachedDecode(bytes, SourceKind.BASE64)
```

## 四、单测夹具（HeightMapDecoderTest.kt，4 条）

- `:421` `Q238 同一内联 PNG 二次解码走缓存命中`：首次 miss=1 / hit=0 / size=1；二次 hit=1，且返回同一栅格实例（`second === first`）。
- `:439` `Q238 坏 Base64 与非 PNG 解码失败均不入库缓存`：非法字符集 Base64 在解码阶段即拒；合法 Base64 但内容非 PNG 进入缓存层后判 null，miss=1、size=0、hit=0、逐出 0。
- `:454` `Q238 超容量 2 份按 LRU 逐出最久未用槽位并留逐出台账`：三份不同 1×1 PNG 依次解码后 size=2、evictions=1、台账 1 条且以「DEM 缓存 LRU 逐出」开头。
- `:470` `Q238 resetCache 清空槽位与命中未命中逐出计数`：清零 size / hits / misses / evictions / 台账。

## 五、静态自检真实输出（沙箱无 JDK，不跑编译 / 单测）

```
$ python3 data_scripts/kotlin-syntax-lint.py \
  app/src/main/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoder.kt \
  app/src/test/java/io/legado/app/ui/map/crossing/terrain/HeightMapDecoderTest.kt
KOTLIN-LINT: PASS（2 个文件）
rc=0
```

括号配平、if/while 尾逗号、符号出处、import 包一致性、可见性传递等文本级判据全绿；JVM 单测与编译留云端 CI 兜底（本仓无 kotlinc / javac）。

## 六、新发现问题

1. 缓存命中查找走哈希索引，`CacheKey` 里的宽高未在命中时与新解码结果做二次比对；sha256 前 16 位（64 bit）碰撞概率当前可忽略，若未来槽位增多或来源不可信，可补「命中槽位尺寸不一致即按未命中处理」的兜底 —— 待裁。
2. `HeightMapRaster.data` 是 public 可变 `FloatArray`；缓存命中返回共享实例，当前消费侧（`toHeightRaster`、桥接重采样）只读不写，若未来有调用方写入 `data[]`，共享槽位会跨沙盘 / 回放串味；需要时可在命中返回时拷贝数组或把数组封装成只读视图 —— 已入队观察。
3. 合成回落场（`degraded = true`）刻意不入库缓存；同一坏源每次都会重走 `synthesize`（32×32 噪声，成本低），也不计入 hits/misses；若未来合成场尺寸放大或战役高频重试，可评估是否对确定性合成场单独缓存 —— 待裁。
4. 缓存挂在 object 单例上，进程生命周期内跨战役残留；战役切换不主动清槽，靠容量 2 的 LRU 自然挤掉旧槽；极端连续切换下旧战役栅格可能短暂驻留内存 —— 已入队观察。

## 七、二阶三阶影响与边界

- 同战役两次加载一致性：命中返回同一栅格实例，两次装配逐格相同；但一致性的根基仍是 `decodeBytes` 的确定性（纯函数、无 `Random`），缓存只省重复 inflate / 反滤波计算，不改变解码结果，缓存清空或未命中时结果不变。
- 异常态是否被缓存：坏 Base64、非 PNG、畸形栅格、空字节均不写槽；合成回落场不写槽；失败每次重新判定，不会把旧失败态或旧降级栅格缓存成后续调用的命中。
- 内存边界：在册上限 2 份栅格，单份受既有 `MAX_PIXELS = 4_000_000` 护栏约束（FloatArray 4 字节/格），最坏双槽驻留约 2×16MB 量级；逐出台账环形 16 条文本，不持有栅格。
- 线程口径：`cachedDecode` 与 `resetCache` 标 `@Synchronized`，槽位读写串行；`decodeBytes` 本体仍是无共享可变状态的纯函数。
- 三阶：解析探针（`:69`）解码成功后栅格已入缓存，后续装配点直接命中；探针判失败不污染缓存。未来若壳层经 `RasterDecoder` 注入平台解码器（`CrossingRasterBridge` 通路），那条路不经本缓存，不在本单范围。
- 边界：未改 `decodeBytes` 算法、未改壳层 / model 字段、未跑云端 CI；单测夹具待 CI JVM 环境实跑确认。

## 守则回执

守则回执：
- 指纹：docs/机制-总控必读.md=e9560a5c333b；docs/机制-子代理必读.md=c6806030a6d5；docs/机制-验收代理必读.md=a46af85f0145；docs/机制-文档子代理必读.md=6dd8114a6a74
1. `docs/机制-子代理必读.md:11`："不许 add/commit/push/revert/checkout——提交由总控统一做。"
2. `docs/机制-子代理必读.md:12`："不许跑 gradle / kotlinc / assemble。编译只在云端 CI 发生。"
3. `docs/机制-子代理必读.md:18`："每一处改动后用 `grep -n` 贴出真实输出（证明代码真的写入、证明旧符号真的清零）。"
- 适用条款：
  - 一条缺陷一单：本单只改 HeightMapDecoder.kt 与其单测，其他门禁脚本、壳层桥接、docs 条文未顺手改。
  - 禁 git 写操作：未执行 add/commit/push/revert/checkout（子代理必读 :11）。
  - 禁本地编译：未跑 gradle/kotlinc/assemble；沙箱无 JDK，仅跑 kotlin-syntax-lint.py（子代理必读 :12）。
  - grep 回证：缓存符号、接线点、夹具行号均以 `grep -n` 现取现核，未凭记忆写行号（子代理必读 :18）。
  - 未验证项声明：JVM 单测与编译留云端 CI；平台解码器通路（RasterDecoder）不在本单缓存范围。

## 八、门禁自检输出

```
$ python3 data_scripts/check-onboarding.py docs/修复-DEM双解码缓存.md
ONBOARD: PASS（1 份报告）
rc=0

$ python3 data_scripts/check-anchor-card.py --strict docs/修复-DEM双解码缓存.md
汇总: PASS=1 FAIL=0（共 1 份）
rc=0

$ python3 data_scripts/check-accept-claim.py
ACCEPT-CLAIM: PASS（47 个含「验收通过」的文件均带判据锚）
rc=0

$ python3 data_scripts/check-problem-finding.py docs/修复-DEM双解码缓存.md
INFO 历史欠账 5 份走基线（只许缩小）：修复-InkContentEditState-平台签名撞名.md、修复-全仓非UTF8复扫.md、修复-吞异常三文件.md、修复-空集假绿检测器.md、分析-不等待七阶推演.md
PROBLEM-FINDING: PASS（1 份报告均带新发现问题与处置状态）
rc=0

$ python3 data_scripts/check-unpushed-reports.py
UNPUSHED-REPORTS: PASS（本轮 1 份报告（含已提交未推送）回执合规）
rc=0
```

伴随回归实跑末行：check-selftest-coverage `SELFTEST-COVERAGE: PASS（47 个门禁中 21 已自带 selftest，26 条历史欠账待销）`；check-rule-ledger `RULE-LEDGER: PASS`；check-lock-rule `LOCK-RULE: PASS（现行规则无旧锁口径裸句）`。
