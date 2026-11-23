package io.legado.app.help.bubble

import android.content.Context
import io.legado.app.help.PlaceDict
import io.legado.app.help.PlaceGeo
import io.legado.app.help.PlaceTimeDict
import io.legado.app.help.chrono.ChronoPlaces
import io.legado.app.help.chrono.ContextAnchor

/**
 * 地名 Provider（优先级链第 4 层）。
 *
 * 钩子对齐规格 §6：PlaceDict.find + PlaceGeo.of + [ContextAnchor] 按年代选层。
 * - 同名异地 / 一地多名（长安 / 咸阳 / 京兆）按锚点年过滤：读《左传》锚到春秋，
 *   命中春秋晋国，唐以后的同名地排后进 candidates；
 * - 点 / 面分开：城邑画点，辖区（如京兆）画面；坐标可信度分 tier，推定只画色块不画点；
 * - 命中后填 kind=地名，headline=古名，body=古名今址，detail=沿革 + 治所迁移，
 *   actions=OpenMap(placeId, 地名)，source=CHGIS / 谭其骧等考证出处。
 * - 锚点年为空时不猜图层：候选照收，默认命中不强行指定。
 *
 * 默认命中收口在 [ChronoPlaces.resolve] 的闸内：low 置信度（≤1/3 单源 / 来源冲突）
 * 与今址参考层实体永不进默认命中，只进候选列表；闸后无可默认条目时回退 legacy 条目，
 * 不把低可信 / 今址参考层实体冒充默认所指。
 */
object PlaceProvider : BubbleProvider {

    /** 预热古地名表 + 时空本体（App 启动协程调用，失败不阻启动）。 */
    fun warm(context: Context) {
        runCatching { PlaceDict.ensureLoaded(context) }
        runCatching { PlaceGeo.ensureLoaded(context) }
        // 资产表灌入 ChronoPlaces 纯 Kotlin 消歧内核（按 id 覆盖种子）。
        runCatching { PlaceTimeDict.ensureLoaded(context) }
    }

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        val ctx = BubbleEnv.ctx() ?: return null

        val entry = runCatching { PlaceDict.matchAt(ctx, paragraph, pos) }
            .getOrNull()
            ?: runCatching { PlaceDict.matchEnglish(ctx, paragraph, pos) }
                .getOrNull()
            ?: return null

        return compose(entry, anchor)
    }

    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val ctx = BubbleEnv.ctx() ?: return null
        val clean = BubbleSupport.cleanToken(token)
        if (clean.length < 2) return null

        val entry = runCatching { PlaceDict.find(ctx, clean) }.getOrNull() ?: return null
        return compose(entry, anchor)
    }

    // ---------- 组装 ----------

    private fun compose(entry: PlaceDict.Entry, anchor: ContextAnchor): BubbleContent? {
        val year = anchor.getYear()

        // 带闸消歧：锚点年过滤 → 排序 → 默认命中只取「区间内 + 非参考层 + 可默认(high/medium)」。
        val resolved = runCatching { ChronoPlaces.resolve(entry.name, year) }
            .getOrNull()

        resolved?.hit?.let { seed ->
            return seedContent(seed, resolved.others, entry, year != null)
        }
        // 闸后无默认命中（未锚定 / 区间内全 low 或参考层）：不猜图层，legacy 条目兜底，候选照收。
        return legacyContent(entry)
    }

    private fun seedContent(
        seed: ChronoPlaces.PlaceSeed,
        others: List<ChronoPlaces.PlaceSeed>,
        legacy: PlaceDict.Entry,
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
            if (legacy.modern.isNotBlank() && !seed.note.contains(legacy.modern)) {
                if (isNotEmpty()) append("\n")
                append("今地：").append(legacy.modern)
            }
            if (isEmpty()) append(if (seed.isPoint) "古城邑" else "古政区")
        }

        val detail = buildString {
            append(spanLabel(seed)).append("　").append(tierLabel(seed.tier))
            if (seed.note.isNotBlank()) append("\n").append(seed.note)
            if (legacy.modern.isNotBlank()) append("\n今地：").append(legacy.modern)
            if (seed.tier >= 3 && seed.isPoint) append("\n坐标由文献推定，只作定位参考")
            if (others.isNotEmpty()) append("\n另有同名 ").append(others.size).append(" 处，可切换")
        }

        val candidates = others.take(6).map { o ->
            "${o.name}（${o.era.ifBlank { "古" }} ${spanLabel(o)}${if (!o.isPoint) "·辖区" else ""}）"
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

    private fun legacyContent(e: PlaceDict.Entry): BubbleContent {
        val detail = buildString {
            if (e.note.isNotBlank()) append(e.note)
            if (e.modern.isNotBlank() && !e.note.contains(e.modern)) {
                if (isNotEmpty()) append("\n")
                append("今地：").append(e.modern)
            }
            if (e.en != null) {
                if (isNotEmpty()) append("\n")
                append("原名：").append(e.en)
            }
            if (isEmpty()) append(if (e.world) "世界地名" else "先秦地名")
        }
        return BubbleContent(
            kind = BubbleKind.PLACE,
            token = e.name,
            phonetic = null,
            headline = e.name,
            body = e.note.ifBlank { e.modern.ifBlank { if (e.world) "世界地名" else "古地名" } },
            detail = detail,
            actions = listOf(Action.OpenMap(e.name, e.name)),
            candidates = emptyList(),
            source = if (e.world) "世界地名表" else "place.txt 古地名表",
        )
    }

    // ---------- 展示小工具 ----------

    private fun tierLabel(tier: Int): String = when (tier) {
        1 -> "遗址实测"
        2 -> "考证坐标"
        else -> "文献推定"
    }

    private fun spanLabel(seed: ChronoPlaces.PlaceSeed): String =
        if (seed.start == seed.end) yearText(seed.start)
        else "${yearText(seed.start)}–${yearText(seed.end)}"

    private fun yearText(y: Int): String = if (y <= 0) "前${1 - y}" else "$y"
}
