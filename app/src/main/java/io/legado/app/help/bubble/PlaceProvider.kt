package io.legado.app.help.bubble

import android.content.Context
import io.legado.app.help.PlaceDict
import io.legado.app.help.PlaceGeo
import io.legado.app.help.PlaceTimeDict
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
 */
object PlaceProvider : BubbleProvider {

    /** 预热古地名表 + 时空本体（App 启动协程调用，失败不阻启动）。 */
    fun warm(context: Context) {
        runCatching { PlaceDict.ensureLoaded(context) }
        runCatching { PlaceGeo.ensureLoaded(context) }
    }

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        val ctx = BubbleEnv.ctx() ?: return null

        val entry = runCatching { PlaceDict.matchAt(ctx, paragraph, pos) }
            .getOrNull()
            ?: runCatching { PlaceDict.matchEnglish(ctx, paragraph, pos) }
                .getOrNull()
            ?: return null

        val year = anchor.getYear()
        val resolved = runCatching { PlaceTimeDict.resolve(ctx, entry.name, year) }
            .getOrNull()

        resolved?.hit?.let { ent -> return timeContent(ent, resolved.others, entry, year != null) }
        return legacyContent(entry)
    }

    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val ctx = BubbleEnv.ctx() ?: return null
        val clean = BubbleSupport.cleanToken(token)
        if (clean.length < 2) return null

        val entry = runCatching { PlaceDict.find(ctx, clean) }.getOrNull() ?: return null
        val year = anchor.getYear()
        val resolved = runCatching { PlaceTimeDict.resolve(ctx, entry.name, year) }
            .getOrNull()
        resolved?.hit?.let { ent -> return timeContent(ent, resolved.others, entry, year != null) }
        return legacyContent(entry)
    }

    // ---------- 组装 ----------

    private fun timeContent(
        ent: PlaceTimeDict.Ent,
        others: List<PlaceTimeDict.Ent>,
        legacy: PlaceDict.Entry,
        anchored: Boolean,
    ): BubbleContent {
        val kindLabel = if (ent.isPoint) "城邑" else "辖区"
        val headline = buildString {
            append(ent.name)
            append("（").append(ent.era).append("·").append(kindLabel)
            append("·").append(ent.tierLabel)
            if (!anchored) append("·年份未锚定")
            append("）")
        }

        val body = buildString {
            if (ent.note.isNotBlank()) append(ent.note)
            if (legacy.modern.isNotBlank() && !ent.note.contains(legacy.modern)) {
                if (isNotEmpty()) append("\n")
                append("今地：").append(legacy.modern)
            }
            if (isEmpty()) append(if (ent.isPoint) "古城邑" else "古政区")
        }

        val detail = buildString {
            append(ent.spanLabel()).append("　").append(ent.tierLabel)
            if (ent.superior != null) append("\n上级政区：").append(ent.superior)
            if (ent.note.isNotBlank()) append("\n").append(ent.note)
            if (legacy.modern.isNotBlank()) append("\n今地：").append(legacy.modern)
            if (ent.tier >= 3 && ent.isPoint) append("\n坐标由文献推定，只作定位参考")
            if (others.isNotEmpty()) append("\n另有同名 ").append(others.size).append(" 处，可切换")
        }

        val candidates = others.take(6).map { o ->
            "${o.name}（${o.era} ${o.spanLabel()}${if (!o.isPoint) "·辖区" else ""}）"
        }

        return BubbleContent(
            kind = BubbleKind.PLACE,
            token = ent.name,
            phonetic = null,
            headline = headline,
            body = body,
            detail = detail,
            actions = listOf(Action.OpenMap(ent.id, ent.name)),
            candidates = candidates,
            source = ent.source?.takeIf { it.isNotBlank() } ?: "CHGIS / 谭其骧《中国历史地图集》",
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
}
