package io.legado.app.ui.map.globe.astro.data

import android.content.res.AssetManager
import io.legado.app.ui.map.globe.astro.layer.FieldEntry

/**
 * 星表懒加载门面（接线层，§十二.5）：`assets/astro/stars.csv` → [StarCatalog.fromAssets]
 * → [StarStore]，进程内只解析一次；帧循环只读缓存引用，不在每帧重建目录数组。
 *
 * 〔失败口径（§七.6、§十三.1）〕assets 缺失 / 损坏 / 零行时，[StarCatalog.fromAssets]
 * 已收敛为 `isPlaceholder = true` 的空目录；本门面照样包成 [StarStore] 缓存住，渲染器
 * 见到占位标志回落种子 9173 程序化散点 + 示意横幅，绝不向上抛、绝不每帧重试重建。
 *
 * 〔不持有 Context〕只吃应用级 [AssetManager] 资源句柄，不存 Activity / Context 引用；
 * DAO 一律传空：关系路径（星官成员 / 分野反查）回落内置 CSV 解析表或空列表不崩，
 * Room 注入留 AppDatabase 接线单（见交接项），本单不新建全局可变单例持有 Activity。
 */
object StarStoreProvider {

    @Volatile
    private var cached: StarStore? = null

    @Volatile
    private var attempted: Boolean = false

    private val lock = Any()

    /**
     * 取星表；首次调用同步解析 assets，之后直接返回缓存。
     * @param assets Compose 侧 `LocalContext.current.assets`；null 时目录进占位态。
     */
    fun get(assets: AssetManager?): StarStore {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val catalog = StarCatalog.fromAssets(assets)
            val entries = loadFieldEntries(assets)
            val members = loadMemberMap(assets)
            val store = StarStore(catalog, null, null, null, entries, members)
            attempted = true
            cached = store
            return store
        }
    }

    /** 错误态重试：清掉缓存与已试标志，下次 [get] 重新解析内置表（§七.7 一次性提示可重试）。 */
    fun invalidate() {
        synchronized(lock) {
            cached = null
            attempted = false
        }
    }

    /** 是否已尝试过加载（占位态也算已尝试；状态面 / 自检报告用）。 */
    fun hasAttempted(): Boolean = attempted

    /** 解析分野表；读不出 / 缺列时回落空列表，不抛。 */
    private fun loadFieldEntries(assets: AssetManager?): List<FieldEntry> {
        if (assets == null) return emptyList()
        return try {
            assets.open("astro/field_allocations.csv").bufferedReader().useLines { lines ->
                val out = ArrayList<FieldEntry>()
                var header = true
                for (line in lines) {
                    if (header) { header = false; continue }
                    if (line.isBlank()) continue
                    val f = line.split(',')
                    if (f.size < 5) continue
                    out += FieldEntry(
                        asterismCode = f[0],
                        regionName = f[1],
                        stateName = f[2].ifBlank { null },
                        source = f[3].ifBlank { null },
                        isVariant = f[4].trim().equals("True", ignoreCase = true),
                    )
                }
                out
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 解析星官锚点成员表：星官内码 → 成员星主键列表；读不出回落空表。 */
    private fun loadMemberMap(assets: AssetManager?): Map<String, List<Long>> {
        if (assets == null) return emptyMap()
        return try {
            val map = HashMap<String, MutableList<Long>>()
            assets.open("astro/asterism_members.csv").bufferedReader().useLines { lines ->
                var header = true
                for (line in lines) {
                    if (header) { header = false; continue }
                    if (line.isBlank()) continue
                    val f = line.split(',')
                    if (f.size < 2) continue
                    val starId = f[1].toLongOrNull() ?: continue
                    map.getOrPut(f[0]) { ArrayList() }.add(starId)
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
