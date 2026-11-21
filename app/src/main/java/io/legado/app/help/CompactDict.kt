package io.legado.app.help

import android.content.Context
import io.legado.app.utils.printOnDebug
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * 只读大词表的紧凑载入器。
 *
 * 原来的做法是把 zh_ci / en_dict 逐行解析进 HashMap<String, String>：
 * 26 万条中文词要装 26 万个 String 键 + 26 万个释义 String，40 万条英文词同理，
 * 十二三兆的文本能涨到七八十兆堆，低内存机第一次点词直接卡住甚至 OOM。
 *
 * 这里改成「整块字节数组 + 有序索引 + 二分」：
 * 文件只解一次压，之后是一块连续 byte[]（UTF-8 不转 UTF-16，内存约为原文件大小），
 * 查词走 O(log n) 二分，不再为每条词建对象。查找结果按需解码成 String。
 *
 * 文件格式（由 tools/gen_dict.py 生成，大端）：
 *   char[4] magic "LDIC" | int32 count | int32 idxOff | int32 dataOff
 *   索引区 idxOff 起 count × int32，值为相对 dataOff 的偏移
 *   数据区 dataOff 起每条 UTF-8 "key\trest\n"
 */
object CompactDict {

    private const val MAGIC = "LDIC"

    class Table(
        private val buf: ByteArray,
        private val count: Int,
        private val idxOff: Int,
        private val dataOff: Int,
    ) {
        private val bb: ByteBuffer = ByteBuffer.wrap(buf)

        val size: Int get() = count

        /** 查键，返回 \t 之后的原文（释义／音标+释义）；未命中返回 null */
        fun find(key: String): String? {
            var lo = 0
            var hi = count - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val off = dataOff + bb.getInt(idxOff + mid * 4)
                val tab = tabAt(off)
                if (tab < 0) return null
                val k = String(buf, off, tab - off, StandardCharsets.UTF_8)
                val cmp = k.compareTo(key)
                when {
                    cmp == 0 -> {
                        var end = tab + 1
                        while (end < buf.size && buf[end] != '\n'.code.toByte()) end++
                        return if (end > tab + 1) {
                            String(buf, tab + 1, end - tab - 1, StandardCharsets.UTF_8)
                        } else ""
                    }
                    cmp < 0 -> lo = mid + 1
                    else -> hi = mid - 1
                }
            }
            return null
        }

        /** 顺序遍历全部键，用于建首字索引这类一次性工作 */
        fun forEachKey(action: (String) -> Unit) {
            var i = 0
            while (i < count) {
                val off = dataOff + bb.getInt(idxOff + i * 4)
                val tab = tabAt(off)
                if (tab > off) action(String(buf, off, tab - off, StandardCharsets.UTF_8))
                i++
            }
        }

        private fun tabAt(off: Int): Int {
            var p = off
            while (p < buf.size && buf[p] != '\t'.code.toByte() && buf[p] != '\n'.code.toByte()) p++
            return if (p < buf.size && buf[p] == '\t'.code.toByte()) p else -1
        }
    }

    @Volatile
    private val cache = HashMap<String, Table>(4)

    fun open(context: Context, name: String): Table? {
        cache[name]?.let { return it }
        synchronized(this) {
            cache[name]?.let { return it }
            val t = runCatching {
                val bytes = context.applicationContext.assets.open(name).use { it.readBytes() }
                if (bytes.size < 16) return null
                val bb = ByteBuffer.wrap(bytes)
                val magic = String(bytes, 0, 4, StandardCharsets.US_ASCII)
                if (magic != MAGIC) {
                    throw IllegalStateException("$name 不是 LDIC 格式")
                }
                val count = bb.getInt(4)
                val idxOff = bb.getInt(8)
                val dataOff = bb.getInt(12)
                if (count <= 0 || idxOff <= 0 || dataOff <= idxOff) return null
                Table(bytes, count, idxOff, dataOff)
            }.onFailure { it.printOnDebug() }.getOrNull()
            if (t != null) cache[name] = t
            return t
        }
    }
}
