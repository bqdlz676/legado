package io.legado.app.help.person

import android.content.Context

/**
 * person 资产解析层（交接清单 #17：旧 relation 资产经 [PersonDirectory.register] 扩展点回填）。
 *
 * 资产口径（设计规格 §2.1–2.3）：
 * - `person_core.txt`：id canonical dynasty birth death identity feat state source
 *   confidence proofread zi hao fief —— L1 手校核心库 + 王公纪年，含在位起讫（feat 内「在位 N 年（起—止）」）；
 * - `person_l1.txt`：本名 字 号 封号 生年 卒年 在位起 在位止 国 身份 事迹 —— 与 core 互校，补在位起讫与字/号/爵；
 * - `person_alias.txt`：别名 归属 类型 国 起年 止年 —— 谥称 / 爵称 / 字 / 号 / 省称；`@国` 为在位君主占位，
 *   归 C1 时空锚按年代解君，本层不落到具体人物；
 * - `person_dup.txt`：名 生 卒 朝代 身份 置信 别名 —— 同名分组持久化表；L2 有生卒，L3 孤证沉底；
 * - `person.txt`：本名 字/号 生 卒 国 身份 —— CBDB 兜底全量；有生卒判 L2，生卒缺判 L3，只进候选池不默认命中。
 *
 * 零 copyleft：本层不灌 Wikipedia / OSM / CC-CEDICT 文案；CBDB 兜底行只作候选索引，
 * 出处列如实标注「兜底索引 · 无收录号」，不伪造 CBDB Person ID。
 *
 * 容错口径（同字典表）：列数不足按空缺处理，坏行跳过，不抛异常；年份解析失败置 null，不补约数。
 */
object PersonAssets {

    /** person_l1.txt 解析出的补录项：在位起讫 / 字 / 号 / 爵，用于与 core 互校叠加。 */
    data class L1Supplement(
        val reignStart: Int?,
        val reignEnd: Int?,
        val birth: Int?,
        val death: Int?,
        val courtesy: String?,
        val style: String?,
        val fief: String?,
    )

    /** person_alias.txt 一行：别名 → 归属人物（@国 为君主占位）。 */
    data class AliasEntry(
        val alias: String,
        val owner: String,
        val type: String,
        val state: String,
        val reignStart: Int?,
        val reignEnd: Int?,
    )

    // ---- 纯行解析（宿主 JVM 单测直接喂行列表）----

    /** person_l1.txt → 本名补录项表。 */
    fun parseL1Supplement(lines: List<String>): Map<String, L1Supplement> {
        val out = HashMap<String, L1Supplement>(128)
        lines.forEach { ln ->
            if (ln.isBlank() || ln.startsWith("#")) return@forEach
            val p = ln.split('\t')
            if (p.size < 9) return@forEach
            val name = p[0].trim()
            if (name.isEmpty()) return@forEach
            out[name] = L1Supplement(
                reignStart = p.getOrNull(6)?.trim()?.toIntOrNull(),
                reignEnd = p.getOrNull(7)?.trim()?.toIntOrNull(),
                birth = p.getOrNull(4)?.trim()?.toIntOrNull(),
                death = p.getOrNull(5)?.trim()?.toIntOrNull(),
                courtesy = p[1].trim().ifBlank { null },
                style = p[2].trim().ifBlank { null },
                fief = p[3].trim().ifBlank { null },
            )
        }
        return out
    }

    /**
     * person_core.txt → 档案列表。[supplements] 为 l1 互校补录；core feat 正则未抽出在位起讫时，
     * 用补录项兜底。
     */
    fun parseCore(
        lines: List<String>,
        supplements: Map<String, L1Supplement> = emptyMap(),
    ): List<PersonProfile> {
        val out = ArrayList<PersonProfile>(lines.size)
        lines.forEach { ln ->
            if (ln.isBlank() || ln.startsWith("#")) return@forEach
            val p = ln.split('\t')
            if (p.size < 11) return@forEach
            val name = p[1].trim()
            if (name.isEmpty()) return@forEach
            val birth = p[3].trim().toIntOrNull()
            val death = p[4].trim().toIntOrNull()
            val identity = p[5].trim()
            val feat = p[6].trim()
            val state = p[7].trim()
            val sourceText = p[8].trim()
            val confidence = p[9].trim().ifBlank { null }
            val proofread = p.getOrNull(10)?.trim() == "1"
            val zi = p.getOrNull(11)?.trim()?.ifBlank { null }
            val hao = p.getOrNull(12)?.trim()?.ifBlank { null }
            val fief = p.getOrNull(13)?.trim()?.ifBlank { null }

            val supp = supplements[name]
            val (reignStart, reignEnd) = reignFromFeat(feat)
                ?: (supp?.reignStart to supp?.reignEnd)

            val tier = when {
                sourceText.contains("手校") || sourceText.contains("王公纪年") -> ConfidenceTier.L1
                else -> ConfidenceTier.fromConfidence(confidence, birth != null || death != null)
            }

            out += PersonProfile(
                personId = p[0].trim().ifBlank { "core:$name" },
                name = name,
                courtesyName = zi ?: supp?.courtesy,
                styleName = hao ?: supp?.style,
                titles = listOfNotNull(fief ?: supp?.fief),
                aliases = emptyList(),
                birthYear = birth ?: supp?.birth,
                deathYear = death ?: supp?.death,
                nationality = state,
                identity = identity,
                biography = feat.ifBlank { identity },
                lineage = emptyList(),
                notableEvent = feat,
                dynasty = p[2].trim().ifBlank { null },
                source = PersonSource.Classic(sourceText.ifBlank { "assets/person_core.txt" }),
                tier = tier,
                proofread = proofread,
                reignStart = reignStart,
                reignEnd = reignEnd,
            )
        }
        return out
    }

    /** person_alias.txt → 别名条目表。 */
    fun parseAlias(lines: List<String>): List<AliasEntry> {
        val out = ArrayList<AliasEntry>(lines.size)
        lines.forEach { ln ->
            if (ln.isBlank() || ln.startsWith("#")) return@forEach
            val p = ln.split('\t')
            if (p.size < 4) return@forEach
            val alias = p[0].trim()
            val owner = p[1].trim()
            if (alias.isEmpty() || owner.isEmpty()) return@forEach
            out += AliasEntry(
                alias = alias,
                owner = owner,
                type = p[2].trim(),
                state = p[3].trim(),
                reignStart = p.getOrNull(4)?.trim()?.toIntOrNull(),
                reignEnd = p.getOrNull(5)?.trim()?.toIntOrNull(),
            )
        }
        return out
    }

    /** person_dup.txt → 同名分组行。 */
    fun parseDup(lines: List<String>): List<PersonDupRow> {
        val out = ArrayList<PersonDupRow>(lines.size)
        lines.forEach { ln ->
            if (ln.isBlank() || ln.startsWith("#")) return@forEach
            val p = ln.split('\t')
            if (p.size < 6) return@forEach
            val name = p[0].trim()
            if (name.isEmpty()) return@forEach
            out += PersonDupRow(
                name = name,
                birth = p[1].trim().toIntOrNull(),
                death = p[2].trim().toIntOrNull(),
                era = p[3].trim(),
                identity = p[4].trim(),
                tier = ConfidenceTier.fromAssetMark(p.getOrNull(5)?.trim()),
                aliases = p.getOrNull(6)?.split('、').orEmpty().filter { it.isNotBlank() },
            )
        }
        return out
    }

    /**
     * person.txt（CBDB 兜底全量）→ 档案列表。有生卒判 L2，生卒缺判 L3；
     * 不伪造收录号，出处标注为兜底索引。批量灌入前由调用方按需加载（全量库只兜底，不堆进启动路径）。
     */
    fun parseBulk(lines: List<String>): List<PersonProfile> {
        val out = ArrayList<PersonProfile>(lines.size)
        lines.forEach { ln ->
            if (ln.isBlank()) return@forEach
            val p = ln.split('\t')
            if (p.size < 6) return@forEach
            val name = p[0].trim()
            if (name.length < 2) return@forEach
            val birth = p[2].trim().toIntOrNull()
            val death = p[3].trim().toIntOrNull()
            val aliases = p[1].split('、').filter { it.length in 2..6 }
            val tier = ConfidenceTier.fromConfidence(null, birth != null || death != null)
            out += PersonProfile(
                personId = "bulk:$name:${birth ?: "?"}:${death ?: "?"}",
                name = name,
                aliases = aliases,
                birthYear = birth,
                deathYear = death,
                nationality = p[4].trim(),
                identity = p[5].trim(),
                biography = p[5].trim(),
                notableEvent = "",
                dynasty = null,
                source = PersonSource.Classic("CBDB 兜底索引 · assets/person.txt（无收录号）"),
                tier = tier,
                proofread = false,
            )
        }
        return out
    }

    // ---- Context 灌入（register 扩展点；资产缺失时静默降级为空，不崩）----

    /** 灌入 L1 核心：core + l1 互校 + 别名归并（启动预载路径调用；资产缺失静默降级为空，不崩）。 */
    fun loadCore(context: Context) {
        registerCore(
            l1Lines = readAsset(context, "person_l1.txt"),
            coreLines = readAsset(context, "person_core.txt"),
            aliasLines = readAsset(context, "person_alias.txt"),
        )
    }

    /**
     * 纯 JVM 管线（单测 / bench 直喂资产行列表）：l1 互校补录 → core 解析 → 别名归并 →
     * 灌入 [PersonDirectory.register]。与 [loadCore] 同口径，仅剥离 Context 读资产这一步，
     * 便于宿主 JVM 单测直接验证「预载后目录可查 L1 人物」。
     */
    fun registerCore(l1Lines: List<String>, coreLines: List<String>, aliasLines: List<String>) {
        val supplements = parseL1Supplement(l1Lines)
        var records = parseCore(coreLines, supplements)
        val aliases = parseAlias(aliasLines)
        records = mergeAliases(records, aliases)
        if (records.isNotEmpty()) PersonDirectory.register(records)
    }

    /** 灌入同名分组持久化表（按需 / 后台加载，不进启动热路径，不拖慢启动）。 */
    fun loadDup(context: Context) {
        val rows = parseDup(readAsset(context, "person_dup.txt"))
        if (rows.isNotEmpty()) PersonDirectory.registerDuplicates(rows)
    }

    /** 灌入 CBDB 兜底全量（L2/L3 候选池；按需调用，不走启动热路径）。 */
    fun loadBulk(context: Context) {
        val records = parseBulk(readAsset(context, "person.txt"))
        if (records.isNotEmpty()) PersonDirectory.register(records)
    }

    /** 核心 + 同名分组 + 兜底全量一次灌入（仅供后台 / 调试批量回填；启动路径只调 [loadCore]）。 */
    fun loadAll(context: Context) {
        loadCore(context)
        loadDup(context)
        loadBulk(context)
    }

    // ---- 内部 ----

    /** 别名归并：非 `@国` 占位的别名按归属并到对应档案；同称呼去重。 */
    private fun mergeAliases(
        records: List<PersonProfile>,
        aliases: List<AliasEntry>,
    ): List<PersonProfile> {
        val byName = records.associateBy { it.name }.toMutableMap()
        aliases.forEach { a ->
            if (a.owner.startsWith("@")) return@forEach
            val owner = byName[a.owner] ?: return@forEach
            val extra = when (a.type) {
                "字" -> listOfNotNull(a.alias)
                "号" -> listOfNotNull(a.alias)
                else -> listOfNotNull(a.alias)
            }
            val merged = owner.copy(
                aliases = (owner.aliases + extra).distinct(),
                courtesyName = owner.courtesyName ?: a.alias.takeIf { a.type == "字" },
                styleName = owner.styleName ?: a.alias.takeIf { a.type == "号" },
            )
            byName[owner.name] = merged
        }
        return byName.values.toList()
    }

    /** feat 文案里抽「在位 N 年（起—止）」；抽不到返回 null。 */
    private fun reignFromFeat(feat: String): Pair<Int?, Int?>? {
        val m = Regex("在位\\d+年\\（(-?\\d+)—(-?\\d+)\\）").find(feat) ?: return null
        val start = m.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val end = m.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        return start to end
    }

    private fun readAsset(context: Context, name: String): List<String> =
        runCatching {
            context.assets.open(name).bufferedReader().use { it.readLines() }
        }.getOrDefault(emptyList())
}
