package io.legado.app.ui.map.globe.astro.data

import android.content.res.AssetManager

/**
 * 星表懒加载门面（接线层，§十二.5）：`assets/astro/stars.csv` → [StarCatalog.fromAssets]
 * → [StarStore]，进程内只解析一次；帧循环只读缓存引用，不在每帧重建目录数组。
 *
 * 〔失败口径（§七.6、§十三.1）〕assets 缺失 / 损坏 / 零行时，[StarCatalog.fromAssets]
 * 已收敛为 `isPlaceholder = true` 的空目录；本门面照样包成 [StarStore] 缓存住，渲染器
 * 见到占位标志回落种子 9173 程序化散点 + 示意横幅，绝不向上抛、绝不每帧重试重建。
 *
 * 〔不持有 Context〕只吃应用级 [AssetManager] 资源句柄，不存 Activity / Context 引用；
 * DAO 一律传空：关系路径（星官成员 / 分野反查）回落空列表不崩，Room 注入留 AppDatabase
 * 接线单（见交接项），本单不新建全局可变单例持有 Activity。
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
            val store = StarStore(catalog, null, null, null)
            attempted = true
            cached = store
            return store
        }
    }

    /** 是否已尝试过加载（占位态也算已尝试；状态面 / 自检报告用）。 */
    fun hasAttempted(): Boolean = attempted
}
