package io.legado.app.help.bubble

import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.person.ConfidenceTier
import io.legado.app.help.person.PersonProfile
import io.legado.app.help.person.PersonSource
import kotlin.math.abs

/**
 * 人名 Provider（优先级链第 3 层）。
 *
 * 钩子对齐规格 §6：PersonProfile.profileOf / canonical；生卒取选中档案自身字段。
 * - 一人十几种称呼（本名 / 字 / 号 / 谥号 / 爵号 / 邦称 / 排行）归并到本名；
 * - 同名人物按 [anchor] 锚定年代与上下文印证消歧，其余同名收进 candidates 一键切换；
 * - 命中后填 kind=人名，headline=本名+身份，body=生卒+国籍+一句话小传+别名+谱系，
 *   actions=OpenPerson / OpenRelation，source=Harvard CBDB 收录号等权威出处。
 * - 不凭姓名猜身份、不编生卒；生卒无载如实写「生卒无载」。
 *
 * 年代消歧（验收 4.1-3）：
 * - 本 Provider 在初始化时把自身查询入口注册进 [ContextAnchor.personProvider]，
 *   供全 app 唯一消歧入口 [ContextAnchor.resolve] 按锚点年 / 区间做时空过滤；
 * - [query] 内部不再走「年份盲查」：先取 [PersonProfile.candidatesOf] 全部同名档案，
 *   再按锚点年（精确年优先）/ 锚点区间在气泡层做生卒区间覆盖 → 中点就近 → preferred 排序，
 *   选中默认项，其余同名进候选。D2 facade 未暴露 year 形参，故排序口径复刻
 *   PersonDirectory.disambiguate，档案生卒字段本身就是消歧依据，不另造派生数据。
 */
object PersonProvider : BubbleProvider {

    init {
        bindAnchorProvider()
    }

    /**
     * 注册到共享消歧总线：称呼 → 人物候选列表。可重复调用（幂等覆盖为本 Provider 入口）。
     * 单独暴露成函数，便于 Resolver.preload 预热后显式重绑，不依赖对象初始化时序。
     */
    fun bindAnchorProvider() {
        ContextAnchor.personProvider = { name -> anchorCandidates(name) }
    }

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

        // 全部同名档案（含默认命中项）；锚点年代透传后在本地排序选默认。
        val all = runCatching { PersonProfile.candidatesOf(clean) }.getOrNull().orEmpty()
        val selected = selectByAnchor(all, anchor)
            ?: runCatching { PersonProfile.profileOf(clean) }.getOrNull()
            ?: return null

        val others = all.filter { it.personId != selected.personId }
        val canonical = selected.name.ifBlank {
            runCatching { PersonProfile.canonical(clean) }.getOrNull().orEmpty()
        }

        // 生卒取选中档案自身字段，避免再走一次年份盲查的 lifeSpan。
        val birth = selected.birthYear
        val death = selected.deathYear

        val callNames = buildList {
            selected.courtesyName?.let { add("字 $it") }
            selected.styleName?.let { add("号 $it") }
            addAll(selected.titles.map { "爵谥 $it" })
            addAll(selected.aliases)
        }.distinct()

        val bodyParts = ArrayList<String>(4)
        bodyParts += BubbleSupport.lifeSpanText(birth, death)
        if (selected.nationality.isNotBlank()) bodyParts += selected.nationality
        if (selected.identity.isNotBlank()) bodyParts += selected.identity
        if (selected.biography.isNotBlank()) bodyParts += selected.biography
        if (callNames.isNotEmpty()) bodyParts += "称谓　" + callNames.joinToString("、")

        val detailParts = ArrayList<String>(6)
        detailParts += "本名　${selected.name}"
        if (callNames.isNotEmpty()) detailParts += "字 / 号 / 别称　" + callNames.joinToString("、")
        detailParts += "生卒　" + BubbleSupport.lifeSpanText(birth, death)
        if (selected.identity.isNotBlank()) detailParts += "身份　${selected.nationality}·${selected.identity}"
        if (selected.biography.isNotBlank()) detailParts += "小传　${selected.biography}"
        if (selected.notableEvent.isNotBlank()) detailParts += "事迹　${selected.notableEvent}"
        if (selected.lineage.isNotEmpty()) detailParts += "谱系　" + selected.lineage.joinToString("、")

        val candidates = others.map { other ->
            val decor = listOfNotNull(other.dynasty, other.identity)
                .filter { it.isNotBlank() }
                .joinToString("·")
            BubbleCandidate(
                query = other.name,
                display = if (decor.isBlank()) other.name else "${other.name}（$decor）",
            )
        }

        // 注音走人名专用异读门面（档案读音 > 注册整词覆盖 > 内置异读表）；
        // 专用读音未命中保持 null，交上层字库兜底，本层不猜读音。
        val phonetic = runCatching {
            PersonProfile.pinyinOf(canonical.ifBlank { selected.name })
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return BubbleContent(
            kind = BubbleKind.PERSON,
            token = canonical,
            phonetic = phonetic,
            headline = buildString {
                append(canonical)
                if (selected.identity.isNotBlank()) append(" · ").append(selected.identity)
            },
            body = bodyParts.joinToString("\n"),
            detail = detailParts.joinToString("\n"),
            actions = listOf(
                Action.OpenPerson(canonical, selected.personId),
                Action.OpenRelation(canonical),
            ),
            candidates = candidates,
            source = selected.source.label,
        )
    }

    // ---------- 锚点消歧 ----------

    /** 称呼 → 注册给 [ContextAnchor.personProvider] 的候选列表；查无此人返回空列表，不塞假数据。 */
    private fun anchorCandidates(name: String): List<ContextAnchor.Candidate> {
        val clean = BubbleSupport.cleanToken(name)
        if (clean.length < 2) return emptyList()
        return runCatching { PersonProfile.candidatesOf(clean) }
            .getOrNull()
            .orEmpty()
            .map(::toAnchorCandidate)
    }

    private fun toAnchorCandidate(p: PersonProfile): ContextAnchor.Candidate =
        ContextAnchor.Candidate(
            id = p.personId,
            name = p.name,
            type = ContextAnchor.Type.PERSON,
            tier = if (p.preferred) 1 else 0,
            start = p.birthYear,
            end = p.deathYear,
            aliases = p.allCallNames,
            confidence = when (p.source) {
                // CBDB 学术库收录按 HIGH；公版典籍单源按 MEDIUM；low 不进默认命中。
                is PersonSource.Cbdb -> ContextAnchor.Confidence.HIGH
                is PersonSource.Classic -> ContextAnchor.Confidence.MEDIUM
            },
            source = p.source.label,
            note = listOfNotNull(p.identity, p.biography).filter { it.isNotBlank() }.joinToString(" · "),
        )

    /**
     * 同名消歧排序（对齐 PersonDirectory.disambiguate 口径）：
     * 0. 分层过滤：L3 孤证（[ConfidenceTier.canDefault] = false）不进默认命中；
     *    全部为孤证时才回退孤证池；其余同名者仍留在候选清单，不丢弃；
     * 1. 锚点精确年被生卒区间覆盖者优先；
     * 2. 锚点区间与生卒区间相交者优先；
     * 3. 生卒中点距锚点（精确年 / 区间中点）最近者；
     * 4. 仍无法区分取 [PersonProfile.preferred]，否则首位。
     */
    private fun selectByAnchor(
        candidates: List<PersonProfile>,
        anchor: ContextAnchor,
    ): PersonProfile? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // L3 孤证不进默认命中；全是孤证时才回退孤证池，避免一上来抛脏数据。
        val defaultable = candidates.filter { it.tier.canDefault }
        val pool = defaultable.ifEmpty { candidates }

        val year = anchor.getYear()
        if (year != null) {
            pool.firstOrNull { inSpan(it, year) }?.let { return it }
            nearestMidpoint(pool, year)?.let { return it }
        }

        val range = anchor.getRange() ?: anchor.candidateYears.firstOrNull()
        if (range != null) {
            pool.firstOrNull { overlaps(it, range) }?.let { return it }
            nearestMidpoint(pool, (range.first + range.last) / 2)?.let { return it }
        }

        return pool.firstOrNull { it.preferred } ?: pool.first()
    }

    private fun inSpan(p: PersonProfile, year: Int): Boolean =
        p.birthYear != null && p.deathYear != null && year in p.birthYear!!..p.deathYear!!

    private fun overlaps(p: PersonProfile, range: IntRange): Boolean =
        p.birthYear != null && p.deathYear != null &&
            p.birthYear!! <= range.last && p.deathYear!! >= range.first

    private fun nearestMidpoint(candidates: List<PersonProfile>, target: Int): PersonProfile? {
        val withMid = candidates.mapNotNull { p ->
            val b = p.birthYear ?: return@mapNotNull null
            val d = p.deathYear ?: return@mapNotNull null
            p to (b + d) / 2
        }
        if (withMid.isEmpty()) return null
        return withMid.minByOrNull { abs(it.second - target) }!!.first
    }
}
