package io.legado.app.help.bubble

import android.content.Context
import io.legado.app.help.chrono.ContextAnchor
import io.legado.app.help.relation.PersonPinyin
import io.legado.app.help.relation.PersonProfile

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

    /** 预热内置档案 + CBDB 外部档案（App 启动协程调用，失败不阻启动）。 */
    fun warm(context: Context) {
        runCatching { PersonProfile.ensureLoaded(context) }
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
        val ctx = BubbleEnv.ctx()
        val clean = BubbleSupport.cleanToken(token)
        if (clean.length < 2) return null

        val p = runCatching { PersonProfile.profileOf(clean) }.getOrNull() ?: return null
        val canonical = runCatching { PersonProfile.canonical(clean) }.getOrNull() ?: p.name

        val pinyin = ctx?.let { runCatching { PersonPinyin.of(it, canonical) }.getOrNull() }
            ?.takeIf { it.isNotBlank() && it != "?" }

        // 同名消歧：锚点年过滤后的候选，默认命中之外的进 candidates 供一键切换。
        val others = runCatching { ContextAnchor.resolve(clean, anchor.getYear()) }
            .getOrNull()
            ?.filter { it.type == ContextAnchor.Type.PERSON && !it.isDefault }
            ?.map { it.name }
            .orEmpty()
            .distinct()

        val lifeSpan = runCatching { PersonProfile.lifeSpan(p) }.getOrNull().orEmpty()

        val bodyParts = ArrayList<String>(4)
        if (lifeSpan.isNotBlank()) bodyParts += lifeSpan
        if (p.state.isNotBlank()) bodyParts += p.state
        if (p.identity.isNotBlank()) bodyParts += p.identity
        if (p.alias.isNotEmpty()) bodyParts += "称谓　" + p.alias.joinToString("、")

        val detailParts = ArrayList<String>(4)
        detailParts += "本名　${p.name}"
        if (p.alias.isNotEmpty()) detailParts += "字 / 号 / 别称　" + p.alias.joinToString("、")
        if (lifeSpan.isNotBlank()) detailParts += "生卒　$lifeSpan"
        if (p.identity.isNotBlank()) detailParts += "事迹　${p.identity}"

        // 内置种子档案标本地来源；外部 CBDB 档案标 Harvard CBDB（收录号随 personId 透传）。
        val fromSeed = runCatching { PersonProfile.find(clean) != null }.getOrNull() == true
        val source = if (fromSeed) "内置人物档案（公版史料）" else "Harvard CBDB"
        val personId = if (fromSeed) null else "person:$canonical"

        return BubbleContent(
            kind = BubbleKind.PERSON,
            token = canonical,
            phonetic = pinyin,
            headline = buildString {
                append(canonical)
                if (p.identity.isNotBlank()) append(" · ").append(p.identity)
            },
            body = bodyParts.joinToString("\n"),
            detail = detailParts.joinToString("\n"),
            actions = listOf(
                Action.OpenPerson(canonical, personId),
                Action.OpenRelation(canonical),
            ),
            candidates = others,
            source = source,
        )
    }
}
