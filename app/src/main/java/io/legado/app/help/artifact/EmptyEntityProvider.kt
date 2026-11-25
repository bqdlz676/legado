package io.legado.app.help.artifact

import io.legado.app.help.bubble.contract.BubbleContent

/**
 * D16 通用 [EntityProvider] 空数据实现（本单只落接口 + 空实现，不接解析链、不造真实数据）。
 *
 * 覆盖全部十七类：无论命中哪一类、type 已知或未知、anchor 是否带年份，都回退到
 * [ArtifactFallback] 的结构完整气泡；不返回 null、不抛异常、不读网络、不读本地文件。
 *
 * 真实数据源（PlantProvider 800 / AnimalProvider 600 / MedicineProvider 1800 /
 * landscape 300+ 等）由后续数据组装单接入；本实现仅作契约占位与兜底验收基线。
 */
class EmptyEntityProvider : EntityProvider {

    override fun query(name: String, type: String, anchor: Any?): BubbleContent {
        // anchor 在此为空实现中不参与过滤：缺年份不做年代过滤（FallbackState.YEAR_MISSING 口径），
        // 年代锚属于后续消歧链职责，空实现不猜年代。
        val category = categoryForType(type)
        // 接口契约（09:15）返回气泡壳；兜底态由 ArtifactFallbackResult 承载，此处只取内嵌壳。
        return ArtifactFallback.notFound(name, category, FallbackState.NO_DATA).content
    }

    /**
     * 按类别直查的兜底入口，供十七类逐类验收与后续真实 Provider 对齐签名。
     * 空实现同样返回结构完整的未命中气泡。
     */
    fun queryByCategory(name: String, category: ArtifactCategory, anchor: Any?): BubbleContent =
        ArtifactFallback.notFound(name, category, FallbackState.NO_DATA).content

    /**
     * 由 schema type 反查类别；type 属于新表但无对应十七类条目（如 vehicle/seal/jade/armor），
     * 或表名 / type 未知时返回 null，走通用兜底，不抛异常。
     */
    private fun categoryForType(type: String): ArtifactCategory? = when (type) {
        "sword" -> ArtifactCategory.SWORD
        "horse" -> ArtifactCategory.HORSE
        "instrument" -> ArtifactCategory.INSTRUMENT
        "vessel" -> ArtifactCategory.VESSEL
        "textile" -> ArtifactCategory.TEXTILE
        "temple" -> ArtifactCategory.TEMPLE
        "daoist" -> ArtifactCategory.DAOIST
        "tower", "pavilion" -> ArtifactCategory.TOWER
        "star", "constellation", "planet", "asterism", "calendar_term" -> ArtifactCategory.ASTRONOMY
        "wine", "tea", "dish", "snack", "ingredient" -> ArtifactCategory.FOOD
        "mountain", "river", "lake", "sea", "waterfall", "cave", "boundary" -> ArtifactCategory.LANDSCAPE
        else -> null
    }
}
