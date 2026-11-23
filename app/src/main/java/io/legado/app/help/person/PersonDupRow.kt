package io.legado.app.help.person

/**
 * 同名分组行（assets/person_dup.txt 灌入，设计规格 §2.1 同名消歧）。
 *
 * CBDB 里「丁佑」有两人（唐 749–801 / 明 1450–），不分组就会把两个人生平混成一条。
 * [tier]：有生卒核验为 [ConfidenceTier.L2]，单条孤证为 [ConfidenceTier.L3]（沉底，不默认命中）。
 *
 * [tag] 是检索候选行要展示的那一列：朝代 · 身份 生卒区间；全缺时如实写「生卒无载」，不补约数。
 */
data class PersonDupRow(
    val name: String,
    val birth: Int?,
    val death: Int?,
    val era: String,
    val identity: String,
    val tier: ConfidenceTier,
    val aliases: List<String>,
) {
    /** 区分标签：时代 + 身份 + 生卒区间，供气泡「还有 N 个同名」候选行展示。 */
    fun tag(): String = buildString {
        if (era.isNotBlank()) append(era)
        if (identity.isNotBlank()) {
            if (isNotEmpty()) append("·")
            append(identity)
        }
        if (birth != null || death != null) {
            if (isNotEmpty()) append(' ')
            append(birth?.let { fmtYear(it) } ?: "?")
            append("–")
            append(death?.let { fmtYear(it) } ?: "?")
        }
        if (isBlank()) append("生卒无载")
    }

    private fun fmtYear(y: Int): String = if (y <= 0) "前${1 - y}" else "$y"
}
