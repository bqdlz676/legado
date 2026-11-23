package io.legado.app.help.bubble

import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.person.PersonProfile

/**
 * 人名 Provider（优先级链第 3 层）。
 *
 * 钩子对齐规格 §6：PersonProfile.profileOf / canonical / lifeSpan。
 * - 一人十几种称呼（本名 / 字 / 号 / 谥号 / 爵号 / 邦称 / 排行）归并到本名；
 * - 同名人物按 [anchor] 锚定年代与上下文印证消歧，其余同名收进 candidates 一键切换；
 * - 命中后填 kind=人名，headline=本名+身份，body=生卒+国籍+一句话小传+别名+谱系，
 *   actions=OpenPerson / OpenRelation，source=Harvard CBDB 收录号等权威出处。
 * - 不凭姓名猜身份、不编生卒；生卒无载如实写「生卒无载」。
 */
object PersonProvider : BubbleProvider {

    override fun hitAt(paragraph: String, pos: Int, anchor: ContextAnchor): BubbleContent? {
        if (pos !in paragraph.indices) return null
        // 先按 2~5 字长词窗口查称谓 / 全名（谥号、爵称、排行称谓在别名库归并）。
        for (span in BubbleSupport.windows(paragraph, pos, 5)) {
            if (span.length < 2) continue
            query(span.token, anchor)?.let { return it }
        }
        return null
    }

    override fun query(token: String, anchor: ContextAnchor): BubbleContent? {
        val clean = BubbleSupport.cleanToken(token)
        if (clean.length < 2) return null

        val p = runCatching { PersonProfile.profileOf(clean) }.getOrNull() ?: return null
        val canonical = runCatching { PersonProfile.canonical(clean) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: p.name

        val span = runCatching { PersonProfile.lifeSpan(clean) }.getOrNull()
        val birth = span?.first ?: p.birthYear
        val death = span?.second ?: p.deathYear

        // 同名消歧：默认命中之外的同名者进 candidates，供气泡「还有 N 种解释」一键切换。
        val others = runCatching { PersonProfile.candidatesOf(clean) }
            .getOrNull()
            .orEmpty()
            .filter { it.personId != p.personId }
            .distinctBy { it.personId }

        val callNames = buildList {
            p.courtesyName?.let { add("字 $it") }
            p.styleName?.let { add("号 $it") }
            addAll(p.titles.map { "爵谥 $it" })
            addAll(p.aliases)
        }.distinct()

        val bodyParts = ArrayList<String>(4)
        bodyParts += BubbleSupport.lifeSpanText(birth, death)
        if (p.nationality.isNotBlank()) bodyParts += p.nationality
        if (p.identity.isNotBlank()) bodyParts += p.identity
        if (p.biography.isNotBlank()) bodyParts += p.biography
        if (callNames.isNotEmpty()) bodyParts += "称谓　" + callNames.joinToString("、")

        val detailParts = ArrayList<String>(6)
        detailParts += "本名　${p.name}"
        if (callNames.isNotEmpty()) detailParts += "字 / 号 / 别称　" + callNames.joinToString("、")
        detailParts += "生卒　" + BubbleSupport.lifeSpanText(birth, death)
        if (p.identity.isNotBlank()) detailParts += "身份　${p.nationality}·${p.identity}"
        if (p.biography.isNotBlank()) detailParts += "小传　${p.biography}"
        if (p.notableEvent.isNotBlank()) detailParts += "事迹　${p.notableEvent}"
        if (p.lineage.isNotEmpty()) detailParts += "谱系　" + p.lineage.joinToString("、")

        val candidates = others.map { other ->
            val decor = listOfNotNull(other.dynasty, other.identity)
                .filter { it.isNotBlank() }
                .joinToString("·")
            BubbleCandidate(
                query = other.name,
                display = if (decor.isBlank()) other.name else "${other.name}（$decor）",
            )
        }

        return BubbleContent(
            kind = BubbleKind.PERSON,
            token = canonical,
            phonetic = null,
            headline = buildString {
                append(canonical)
                if (p.identity.isNotBlank()) append(" · ").append(p.identity)
            },
            body = bodyParts.joinToString("\n"),
            detail = detailParts.joinToString("\n"),
            actions = listOf(
                Action.OpenPerson(canonical, p.personId),
                Action.OpenRelation(canonical),
            ),
            candidates = candidates,
            source = p.source.label,
        )
    }
}
