package io.legado.app.ui.map.crossing.terrain

import java.util.Base64
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 高程图来源类别（B13）。
 *
 * 规格切片（`落文-原文-06-四渡赤水引擎.md` L15）：`"heightMap": "gaixia_height.png"`；
 * 实测资产形态见 `CrossingRasterBridge` 注释。本类把字符串声明归成三类可达通路：
 * - [ASSET] 资产基名（如 `gaixia.png`）：由平台 reader 在 assets / map 目录查找；
 * - [LOCAL_PATH] 本地绝对路径或 `file://` URI：由平台 reader 读文件系统；
 * - [BASE64] 内联 base64（含 `data:image/png;base64,` 前缀或裸 base64 串）：纯 JVM 直解；
 * - [SYNTHETIC] 解码失败后的确定性合成回落场；[NONE] 字段空白，按无图处理。
 */
enum class SourceKind { ASSET, LOCAL_PATH, BASE64, SYNTHETIC, NONE }

/**
 * 高程栅格解码结果（纯 JVM，零 Android 依赖）。
 *
 * 口径：行优先 `data[row * width + col]`；行 0 = 北缘、列 0 = 西缘，与 [HeightRaster] /
 * [TerrainMap] 同方向；每格 0..1 归一化高程（0 = 海面，1 = 满幅 7000m，米制换算仍走
 * [HeightRaster.metersFor]）。[degraded] = true 表示该栅格是合成回落场，不是真实 PNG 解码产物。
 */
data class HeightMapRaster(
    val width: Int,
    val height: Int,
    /** 行优先，每格 0..1；构造时统一夹紧，不含 NaN / Infinity。 */
    val data: FloatArray,
    /** true = 合成回落场；false = 真实图片解码产物。 */
    val degraded: Boolean,
    val source: SourceKind,
) {
    val size: Int get() = width * height

    fun inBounds(col: Int, row: Int): Boolean = col in 0 until width && row in 0 until height

    /** 取高程；越界按边缘钳制返回，不抛。 */
    fun at(col: Int, row: Int): Float {
        val c = col.coerceIn(0, width - 1)
        val r = row.coerceIn(0, height - 1)
        return data[r * width + c]
    }
}

/**
 * `heightMap` 解码器（B13，纯 JVM）。
 *
 * 设计取舍（止损口径）：Android `BitmapFactory` 在普通 JVM 单测里不可用，故把
 * 「字节数组 → 灰度栅格」抽成零依赖纯函数 [decodeBytes]，PNG 头 / IHDR / IDAT inflate
 * （[Inflater]）与扫描线反滤波全部自己实现；Bitmap 路径仅作为未来壳层可选快速路径，
 * 本对象不引用任何 `android.*` 类型，引擎层可在 JVM 单测中断言。
 *
 * 兜底铁律（规格「不许空白态」）：资产缺失 / 断网 / 空字节 / 非 PNG / 畸形栅格 / 超大图
 * 解码失败，任一命中都回落 [synthesize] 确定性合成场并置 [HeightMapRaster.degraded] = true，
 * 不抛异常到 UI，不返回空白栅格。
 */
object HeightMapDecoder {

    // ── 护栏常量（F-04 内存红线：双引擎峰值 ≤100MB） ──────────────────────

    /** 单维上限（像素）：超出先按比例降采样再入栅格。 */
    const val MAX_DIMENSION: Int = 2048

    /** 像素总数上限：超出降采样；FloatArray 4 字节/格，400 万格峰值约 16MB，留足推演网格余量。 */
    const val MAX_PIXELS: Int = 4_000_000

    /**
     * 硬拒绝上限：IHDR 声明超出此规模直接判畸形回落合成，避免分配前就 OOM。
     * 口径（F-04 ≤100MB）：8MP 下 raw 字节缓冲 ≤32MB、灰度 IntArray ≤32MB、归一化 FloatArray ≤32MB，
     * 三态峰值约 96MB；常规高程图 ≤1024²（1MP），远低于此线。
     */
    private const val HARD_PIXEL_CAP: Long = 8_000_000L

    /** 硬拒绝单维：防止恶意 / 损坏头声明出天文尺寸。 */
    private const val HARD_DIMENSION: Int = 8192

    /** 合成回落场默认边长：与 `CampaignController.GRID_N = 32` 同口径；区间对齐 HeightRaster.synthetic [16,128]。 */
    const val DEFAULT_GRID_N: Int = 32

    /** 无图（字段空白）时合成场的固定种子；与按 heightMap 串哈希的回落场区分来源。 */
    private const val BLANK_SEED: String = "blank-heightmap"

    // ── 来源分类 ──────────────────────────────────────────────

    /**
     * 裸 base64 最小长度：标准 PNG 编码产物远长于此；短串即便字符集像 base64 也按资产基名兜底，
     * 避免文件名 / 短代号被误当内联栅格送 Base64 解码。
     */
    private const val BASE64_MIN_LENGTH: Int = 200

    /**
     * 判定 [raw] 的来源类别（纯函数，不读盘）。分类优先级（M4 修：base64 字符集判定先于路径分隔符判定）：
     *
     * | 形态 | 判定 |
     * |---|---|
     * | 空 / 空白串 | [SourceKind.NONE] |
     * | `data:` 前缀 | [SourceKind.BASE64] |
     * | 长度 ≥ [BASE64_MIN_LENGTH] 且全部字符属 base64 字母表（`=` 仅作尾部填充） | [SourceKind.BASE64]（即便含 `/` 或 `+`） |
     * | 以 `/` 或 `file://` 开头 | [SourceKind.LOCAL_PATH] |
     * | 含 `/` 且长度 <200（如 `assets/map/x.png`） | [SourceKind.ASSET] |
     * | 其余（短串 / 无分隔符 / 字符集不合法） | [SourceKind.ASSET] 兜底桶（INLINE/UNKNOWN 待 model 接纳栅格字段时再细分） |
     *
     * 为什么字符集判定必须在前：标准 base64 字母表本身含 `/` 与 `+`；旧实现先用 `!contains('/')`
     * 排除路径，导致含 `/` 的裸标准 base64 被误判为 ASSET，内联通路被跳过。
     */
    fun classify(raw: String?): SourceKind {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return SourceKind.NONE
        if (s.startsWith("data:")) return SourceKind.BASE64
        // 优先级①：纯 base64 字符集 + 足够长 → BASE64；此时允许串内出现 '/' 与 '+'。
        if (looksLikeBase64(s)) return SourceKind.BASE64
        // 优先级②：路径形态。绝对路径 / file:// → LOCAL_PATH；含 '/' 的短相对路径 → ASSET。
        if (s.startsWith("/") || s.startsWith("file://")) return SourceKind.LOCAL_PATH
        if (s.contains('/') && s.length < BASE64_MIN_LENGTH) return SourceKind.ASSET
        return SourceKind.ASSET
    }

    private fun looksLikeBase64(s: String): Boolean {
        if (s.length < BASE64_MIN_LENGTH) return false
        val tailEquals = s.count { it == '=' }
        if (tailEquals > 2) return false
        // base64 填充位 `=` 只能出现在串尾；中段出现 `=` 判非法字符集。
        val padStart = s.indexOf('=')
        if (padStart >= 0 && padStart < s.length - 2) return false
        return s.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
    }

    // ── 装配入口 ──────────────────────────────────────────────

    /**
     * 统一装配入口：按 [raw] 类别取字节解码；任何失败都回落确定性合成场，永不抛异常。
     *
     * @param reader 平台侧注入的字节读取器（assets / 文件系统）；ASSET / LOCAL_PATH 且 reader
     *        为 null 时直接走合成场，不在引擎层引 Android 资源 API。
     */
    fun decode(raw: String?, reader: ((String) -> ByteArray?)?): HeightMapRaster {
        val kind = classify(raw)
        val key = raw?.trim().orEmpty()
        return when (kind) {
            SourceKind.BASE64 -> {
                val raster = try { decodeInline(key) } catch (_: Throwable) { null }
                raster ?: synthesize(key.ifBlank { BLANK_SEED })
            }
            SourceKind.ASSET, SourceKind.LOCAL_PATH -> {
                val bytes = try { reader?.invoke(key) } catch (_: Throwable) { null }
                val raster = bytes?.let { b ->
                    try { decodeBytes(b, kind) } catch (_: Throwable) { null }
                }
                raster ?: synthesize(key.ifBlank { BLANK_SEED })
            }
            SourceKind.NONE -> synthesize(BLANK_SEED).copy(source = SourceKind.NONE)
            // SYNTHETIC 是产物类别，不会作为输入类别出现。
            SourceKind.SYNTHETIC -> synthesize(BLANK_SEED)
        }
    }

    /** 内联 base64 解码：剥 data URI 前缀 → Base64 → [decodeBytes]；任何异常返回 null。 */
    fun decodeInline(base64: String?): HeightMapRaster? {
        val s = base64?.trim().orEmpty()
        if (s.isEmpty()) return null
        val payload = if (s.startsWith("data:")) s.substringAfter("base64,", missingDelimiterValue = "") else s
        if (payload.isEmpty()) return null
        val bytes = try { Base64.getDecoder().decode(payload) } catch (_: Throwable) { return null }
        return decodeBytes(bytes, SourceKind.BASE64)
    }

    // ── 纯 JVM PNG 解码（8-bit 灰度 / RGB / RGBA / 调色板） ──────────────

    /**
     * 字节数组 → 灰度高程栅格；不可解（空字节 / 签名错 / 非 8-bit / 隔行 / 畸形尺寸 / inflate 失败）
     * 返回 null，由调用方回落合成场。
     *
     * 支持色彩类型：0 灰度、2 RGB、3 调色板（PLTE）、4 灰度+Alpha、6 RGBA；
     * Alpha 通道不参与高程（视为不透明，亮度取 RGB 平均）。16-bit 深与 Adam7 隔行不支持，回落合成。
     * CRC 不校验：纯 JVM 通路不做完整性核验，损坏像素由值域夹紧与降级标志兜底。
     */
    fun decodeBytes(bytes: ByteArray?, source: SourceKind = SourceKind.ASSET): HeightMapRaster? {
        if (bytes == null || bytes.size < 8) return null
        // PNG 签名：89 50 4E 47 0D 0A 1A 0A
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (i in 0..7) if (bytes[i] != signature[i]) return null

        var offset = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = -1
        var interlace = 0
        val idat = java.io.ByteArrayOutputStream()
        val palette = ArrayList<IntArray>()

        while (offset + 8 <= bytes.size) {
            val length = readBE32(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            // 畸形长度（负数 / 越界）直接回落合成，不让 chunk 边界在坏头上漂移。
            if (length < 0 || dataEnd > bytes.size || dataEnd + 4 > bytes.size) return null
            when (type) {
                "IHDR" -> {
                    if (length < 13) return null
                    width = readBE32(bytes, dataStart)
                    height = readBE32(bytes, dataStart + 4)
                    bitDepth = bytes[dataStart + 8].toInt() and 0xFF
                    colorType = bytes[dataStart + 9].toInt() and 0xFF
                    interlace = bytes[dataStart + 12].toInt() and 0xFF
                }
                "PLTE" -> {
                    palette.clear()
                    var p = dataStart
                    while (p + 3 <= dataEnd) {
                        palette.add(intArrayOf(
                            bytes[p].toInt() and 0xFF,
                            bytes[p + 1].toInt() and 0xFF,
                            bytes[p + 2].toInt() and 0xFF,
                        ))
                        p += 3
                    }
                }
                "IDAT" -> idat.write(bytes, dataStart, length)
                "IEND" -> break
            }
            offset = dataEnd + 4 // 跳过 CRC
        }

        if (width <= 0 || height <= 0) return null
        if (interlace != 0) return null
        if (bitDepth != 8) return null
        // 硬尺寸护栏：分配像素缓冲前先拒绝天文尺寸，避免 inflate / 栅格阶段 OOM。
        if (width > HARD_DIMENSION || height > HARD_DIMENSION ||
            width.toLong() * height.toLong() > HARD_PIXEL_CAP
        ) return null

        val channels = channelsOf(colorType) ?: return null
        if (colorType == 3 && palette.isEmpty()) return null

        val expectedRaw = height * (1 + width * channels)
        val inflated = try {
            val inflater = Inflater(false)
            inflater.setInput(idat.toByteArray())
            // 为什么不能单次 inflate：zlib 流可由多个 deflate 块拼接而成，一次 inflate 只解出当前块，
            // 剩余字节仍留在 inflater 内部缓冲；大 IDAT / 多块压缩场景下单次调用会短读，
            // 再用「解压长度 == 期望长度」比对就会把合法流误判畸形而错误回落合成。
            // 故循环充气直到 finished()；needsInput() 为真而尚未 finished 说明输入截断，直接判 null。
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(65536)
            while (!inflater.finished()) {
                if (inflater.needsInput()) return null
                val n = inflater.inflate(buf)
                if (n <= 0) return null
                out.write(buf, 0, n)
            }
            inflater.end()
            if (out.size() != expectedRaw) return null
            out.toByteArray()
        } catch (_: Throwable) {
            return null
        }

        val gray = IntArray(width * height)
        val stride = width * channels
        var prev = IntArray(stride)
        var curr = IntArray(stride)
        var rawOffset = 0
        for (row in 0 until height) {
            val filter = inflated[rawOffset].toInt() and 0xFF
            if (filter !in 0..4) return null
            rawOffset += 1
            for (i in 0 until stride) {
                val raw = inflated[rawOffset + i].toInt() and 0xFF
                curr[i] = reconstruct(filter, i, channels, raw, curr, prev)
            }
            for (col in 0 until width) {
                gray[row * width + col] = toGray(colorType, col, channels, curr, palette)
            }
            val swap = prev; prev = curr; curr = swap
            rawOffset += stride
        }

        val normalized = FloatArray(gray.size) { i -> gray[i] / 255f }
        var raster = HeightMapRaster(width, height, normalized, degraded = false, source = source)
        if (raster.size > MAX_PIXELS || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            raster = downsample(raster, MAX_DIMENSION, MAX_DIMENSION, MAX_PIXELS)
        }
        return raster
    }

    private fun channelsOf(colorType: Int): Int? = when (colorType) {
        0 -> 1
        2 -> 3
        3 -> 1
        4 -> 2
        6 -> 4
        else -> null
    }

    private fun toGray(
        colorType: Int,
        col: Int,
        channels: Int,
        line: IntArray,
        palette: List<IntArray>,
    ): Int = when (colorType) {
        0 -> line[col]
        4 -> line[col * 2]
        2 -> {
            val r = line[col * 3]; val g = line[col * 3 + 1]; val b = line[col * 3 + 2]
            (r + g + b) / 3
        }
        6 -> {
            val r = line[col * 4]; val g = line[col * 4 + 1]; val b = line[col * 4 + 2]
            (r + g + b) / 3
        }
        3 -> {
            val idx = line[col]
            val entry = palette.getOrElse(idx) { intArrayOf(0, 0, 0) }
            (entry[0] + entry[1] + entry[2]) / 3
        }
        else -> 0
    }

    /** PNG 扫描线反滤波（filter 0..4）；bpp = 通道数（8-bit 深）。 */
    private fun reconstruct(
        filter: Int,
        i: Int,
        bpp: Int,
        raw: Int,
        curr: IntArray,
        prev: IntArray,
    ): Int {
        val left = if (i >= bpp) curr[i - bpp] else 0
        val up = prev[i]
        val upLeft = if (i >= bpp) prev[i - bpp] else 0
        val recon = when (filter) {
            0 -> raw
            1 -> raw + left
            2 -> raw + up
            3 -> raw + (left + up) / 2
            4 -> raw + paeth(left, up, upLeft)
            else -> return -1
        }
        return recon and 0xFF
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    // ── 超大图护栏：等比最近邻降采样 ──────────────────────────────────────

    /**
     * 超护栏栅格等比缩到 [maxDimension] 见方且不超 [maxPixels]；最近邻采样，确定性可复算。
     * 抽成独立纯函数便于单测直接喂小栅格验证比例，不必真的分配超大图。
     */
    fun downsample(
        raster: HeightMapRaster,
        maxDimension: Int,
        maxPixels: Int,
    ): HeightMapRaster = downsample(raster, maxDimension, maxDimension, maxPixels)

    private fun downsample(
        raster: HeightMapRaster,
        maxW: Int,
        maxH: Int,
        maxPixels: Int,
    ): HeightMapRaster {
        val sw = raster.width
        val sh = raster.height
        val scale = minOf(
            maxW.toDouble() / sw,
            maxH.toDouble() / sh,
            sqrt(maxPixels.toDouble() / (sw * sh).toDouble()),
        ).coerceAtMost(1.0)
        if (scale >= 1.0) return raster
        val nw = maxOf(1, floor(sw * scale).toInt())
        val nh = maxOf(1, floor(sh * scale).toInt())
        val out = FloatArray(nw * nh)
        for (row in 0 until nh) {
            val sy = floor(row.toDouble() / (nh - 1) * (sh - 1)).toInt().coerceIn(0, sh - 1)
            for (col in 0 until nw) {
                val sx = floor(col.toDouble() / (nw - 1) * (sw - 1)).toInt().coerceIn(0, sw - 1)
                out[row * nw + col] = raster.data[sy * sw + sx]
            }
        }
        return raster.copy(width = nw, height = nh, data = out)
    }

    // ── 确定性合成回落场 ──────────────────────────────────────

    /**
     * 按 [seed] 字符串做 FNV-1a 哈希播种的多倍频值噪声合成高程场（类柏林噪声）。
     *
     * 为什么用哈希合成、适用条件：
     * - 适用：真实高程图缺失 / 解码失败 / 断网未读到资产；规格要求「不许空白态」，推演与渲染
     *   必须有一层缓丘谷地兜底，且降级标志明示非真实地形。
     * - 确定性：同一字符串两次调用逐格相同（单测断言），同一战役每次载入地形一致，回放可复算；
     *   不使用 `Random`，避免进程内随机导致快照 / 通视性结果漂移。
     * - 边界：合成场只保证「有地形可跑」，不代表真实地势；坡度 / 关隘 / 通视性结论不得外推为史实。
     */
    fun synthesize(seed: String, width: Int = DEFAULT_GRID_N, height: Int = DEFAULT_GRID_N): HeightMapRaster {
        val w = width.coerceIn(1, 128)
        val h = height.coerceIn(1, 128)
        val seedLong = fnv1a(seed.ifBlank { BLANK_SEED })
        val data = FloatArray(w * h)
        val octaves = intArrayOf(4, 8, 16)
        val weights = floatArrayOf(0.5f, 0.3f, 0.2f)
        for (row in 0 until h) {
            for (col in 0 until w) {
                val fx = col.toDouble() / w
                val fy = row.toDouble() / h
                var v = 0.0
                var weightSum = 0.0
                for (o in octaves.indices) {
                    v += valueNoise(fx * octaves[o], fy * octaves[o], seedLong + o) * weights[o]
                    weightSum += weights[o]
                }
                // 中央低洼带：与 HeightRaster.synthetic 谷地口径同向，便于河流折线叠加。
                val valley = 1.0 - kotlin.math.abs(fy - 0.5) * 1.6
                val combined = (v / weightSum) * 0.75 + valley.coerceIn(0.0, 1.0) * 0.25
                data[row * w + col] = combined.coerceIn(0.0, 1.0).toFloat()
            }
        }
        return HeightMapRaster(w, h, data, degraded = true, source = SourceKind.SYNTHETIC)
    }

    /** 二维晶格值噪声：哈希晶格 → smoothstep 双线性插值。 */
    private fun valueNoise(x: Double, y: Double, seed: Long): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val n00 = lattice(x0, y0, seed)
        val n10 = lattice(x0 + 1, y0, seed)
        val n01 = lattice(x0, y0 + 1, seed)
        val n11 = lattice(x0 + 1, y0 + 1, seed)
        val top = n00 + (n10 - n00) * sx
        val bottom = n01 + (n11 - n01) * sx
        return top + (bottom - top) * sy
    }

    // 六个混排常数均为 64 位无符号哈希常数（xxHash 混合 / FNV-1a 偏移基）。
    // Kotlin 有符号 Long 字面量不接受 ≥2^63 的十六进制值，故先以 uL 按 64 位无符号字面量落入
    // ULong，再 .toLong() 按位重解释为负 Long；xor / 乘 / ushr 在二补码下与无符号运算逐位一致，
    // 哈希碰撞率保持 64 位口径，不得降级成 32 位 Int（会显著抬高晶格噪声的碰撞概率）。
    private fun lattice(ix: Int, iy: Int, seed: Long): Double {
        var h = seed xor (ix.toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor (iy.toLong() * 0xC2B2AE3D27D4EB4FuL.toLong())
        h = (h xor (h ushr 33)) * 0xFF51AFD7ED558CCDuL.toLong()
        h = (h xor (h ushr 33)) * 0xC4CEB9FE1A85EC53uL.toLong()
        h = h xor (h ushr 33)
        return (h and 0xFFFFFFFFL).toDouble() / 0xFFFFFFFFL
    }

    private fun fnv1a(s: String): Long {
        // FNV-1a 64 位偏移基 offset-basis = 14695981039346656037，同样 ≥2^63，走 uL 重解释。
        var h = 0xCBF29CE842222325uL.toLong()
        for (ch in s) {
            h = h xor ch.code.toLong()
            h *= 0x100000001B3L
        }
        return h
    }

    private fun readBE32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

}
