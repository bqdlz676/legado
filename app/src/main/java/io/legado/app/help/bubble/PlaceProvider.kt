package io.legado.app.help.bubble

import io.legado.app.help.chrono.ChronoPlaces
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.place.PlaceDict
import io.legado.app.help.place.PlaceEntry

/**
 * 地名 Provider（优先级链第 4 层）。
 *
 * 钩子对齐规格 §6：PlaceDict.find + PlaceGeo.of + [ContextAnchor] 按年代选层。
 * - 同名异地 / 一地多名（长安 / 咸阳 / 京兆）按锚点年过滤：读《左传》锚到春秋，
 *   命中春秋晋地，唐以后的同名地排后进 candidates；
 * - 点 / 面分开：城邑画点，辖区（如京兆）画面；坐标可信度分 tier，推定只画色块不画点；
 * - 命中后填 kind=地名，headline=古名，body=古名今址，detail=沿革 + 治所迁移，
 *   actions=OpenMap(placeId, 地名)，source=CHGIS / 谭其骧等考证出处。
 * - 锚点年为空时不猜图层：候选照收，默认命中不强行指定。
 *
 * 默认命中收口在 [ChronoPlaces.resolve] 的置信度闸内：low 置信度与今址参考层实体
 * 永不进默认命中，只进候选列表；闸后无可默认条目时回退 D3 legacy 条目，
 * 不把低可信 / 今址参考层实体冒充默认所指。
 */
object PlaceProvider : BubbleProvider {

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null

        // 2~4 字窗口长词优先；地名多为双字国名 / 邑名，但先秦诸侯国名常为单字
        // （晋 / 齐 / 鲁 / 郑 / 秦 / 楚 …，见种子别名表），单字窗同样要进地名链，
        // 查无命中再回落多字词 / 单字槽（规格切片-02 §5 验收：点「晋」出地名气泡）。
        for (span in BubbleSupport.windows(paragraph, pos, 4)) {
            compose(span.token, anchor)?.let { return it }
        }
        return null
    }

    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val clean = BubbleSupport.cleanToken(token)
        // 地名槽不设字数下限：单字诸侯国名是种子别名的合法命中形态；仅清洗后为空才放行。
        if (clean.isEmpty()) return null
        return compose(clean, anchor)
    }

    // ---------- 组装 ----------

    private fun compose(name: String, anchor: ContextAnchor): BubbleContent? {
        val year = anchor.getYear()
        val range = anchor.getRange()

        // 带闸消歧：锚点年 / 区间过滤 → 排序 → 默认命中只取「区间内 + 非参考层 + 可默认」。
        val resolved = runCatching { ChronoPlaces.resolve(name, year, range) }.getOrNull()

        resolved?.hit?.let { seed ->
            return seedContent(seed, resolved.others, anchored = year != null || range != null)
        }

        // 闸后无默认命中（未锚定 / 区间内全 low 或参考层）：不猜图层，D3 条目兜底，候选照收。
        val entries = runCatching {
            when {
                year != null -> PlaceDict.find(name, year)
                range != null -> PlaceDict.find(name, range)
                else -> PlaceDict.find(name)
            }
        }.getOrNull().orEmpty()
        if (entries.isEmpty()) return null

        val defaultEntry = PlaceDict.defaultOf(entries) ?: entries.first()
        val others = entries.filter { it.placeId != defaultEntry.placeId }
        return legacyContent(defaultEntry, others)
    }

    private fun seedContent(
        seed: ChronoPlaces.PlaceSeed,
        others: List<ChronoPlaces.PlaceSeed>,
        anchored: Boolean,
    ): BubbleContent {
        val kindLabel = if (seed.isPoint) "城邑" else "辖区"
        val headline = buildString {
            append(seed.name)
            append("（").append(seed.era.ifBlank { "古地名" }).append("·").append(kindLabel)
            append("·").append(tierLabel(seed.tier))
            if (!anchored) append("·年份未锚定")
            append("）")
        }

        val body = buildString {
            if (seed.note.isNotBlank()) append(seed.note)
            if (isEmpty()) append(if (seed.isPoint) "古城邑" else "古政区")
        }

        val detail = buildString {
            append(spanLabel(seed)).append("　").append(tierLabel(seed.tier))
            if (seed.note.isNotBlank()) append("\n").append(seed.note)
            if (seed.tier >= 3 && seed.isPoint) append("\n坐标由文献推定，只作定位参考")
            if (others.isNotEmpty()) append("\n另有同名 ").append(others.size).append(" 处，可切换")
        }

        val candidates = others.take(6).map { o ->
            BubbleCandidate(query = o.name, display = candidateDisplay(o))
        }

        return BubbleContent(
            kind = BubbleKind.PLACE,
            token = seed.name,
            phonetic = null,
            headline = headline,
            body = body,
            detail = detail,
            actions = listOf(Action.OpenMap(seed.id, seed.name)),
            candidates = candidates,
            source = seed.source.takeIf { it.isNotBlank() } ?: "CHGIS / 谭其骧《中国历史地图集》",
        )
    }

    private fun legacyContent(entry: PlaceEntry, others: List<PlaceEntry>): BubbleContent {
        val detail = buildString {
            if (entry.note.isNotBlank()) append(entry.note)
            if (entry.superior != null && entry.superior.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("上级政区：").append(entry.superior)
            }
            if (entry.geo?.regionLabel != null) {
                if (isNotEmpty()) append("\n")
                append("今地：").append(entry.geo!!.regionLabel)
            }
            if (isEmpty()) append(if (entry.kind.code == 'A') "古政区" else "古城邑")
        }

        val candidates = others.take(6).map { o ->
            val decor = listOfNotNull(o.era, if (o.kind.code == 'A') "辖区" else "城邑")
                .filter { it.isNotBlank() }
            BubbleCandidate(
                query = o.name,
                display = if (decor.isEmpty()) o.name else "${o.name}（${decor.joinToString("·")}）",
            )
        }

        return BubbleContent(
            kind = BubbleKind.PLACE,
            token = entry.name,
            phonetic = null,
            headline = entry.name,
            body = entry.note.ifBlank { entry.geo?.regionLabel ?: "古地名" },
            detail = detail,
            actions = listOf(Action.OpenMap(entry.placeId, entry.name)),
            candidates = candidates,
            source = entry.source.label,
        )
    }

    // ---------- 展示小工具 ----------

    private fun tierLabel(tier: Int): String = when (tier) {
        1 -> "遗址实测"
        2 -> "考证坐标"
        else -> "文献推定"
    }

    private fun spanLabel(seed: ChronoPlaces.PlaceSeed): String =
        if (seed.start == seed.end) BubbleSupport.yearText(seed.start)
        else "${BubbleSupport.yearText(seed.start)}–${BubbleSupport.yearText(seed.end)}"

    private fun candidateDisplay(o: ChronoPlaces.PlaceSeed): String {
        val parts = ArrayList<String>(3)
        if (o.era.isNotBlank()) parts += o.era
        parts += if (o.isPoint) "城邑" else "辖区"
        if (o.refLayer) {
            // 今址参考层：把现代对照地名带进展示串，便于读者辨认同名异地。
            parts += o.source.ifBlank { "今址参考层" }
        }
        return "${o.name}（${parts.joinToString("·")}）"
    }
}
