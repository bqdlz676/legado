package io.legado.app.ui.map.globe.astro.data

/**
 * 星点 ↔ 分区 code 双向反查索引（纯 JVM，零 Android / Compose 依赖；判据 3 · N1）。
 *
 * 〔缺口由来（验收-星空天象层.md:73、:183）〕既有 `StarStore.memberIdsByAsterism`
 * 只有 code → 成员星主键单向映射；点选一颗星之后无法反查它所属的星官 / 分区 code，
 * 正向分野触发链（点星宿 → 天球高亮该宿 → 地球州域脉冲）缺第一跳。本索引在装载期
 * 由同一批 code → 成员列表一次性补建反向 `starId → code` 映射；查询期只读、不 new 列表。
 *
 * 〔双向一致口径〕一个 starId 只归属一个 code；同一 starId 出现在多个 code 列表时，
 * 后装载的 code 覆盖前者（成员表跨宿重复属数据脏行，处置见交付报告「反例与边界」节）。
 *
 * 〔内存 / 构建耗时〕映射在装载期一次性构建，键为 Long / String 装箱；查询期不产生
 * 装箱分配，[membersOf] 返回表内只读列表引用，调用方不得回写。
 */
class SkyNameIndex private constructor(
    private val membersByCode: Map<String, List<Long>>,
    private val codeByStarId: Map<Long, String>,
) {

    /** code → 成员星主键列表；未知 code 返回空列表，不返回 null。 */
    fun membersOf(code: String): List<Long> = membersByCode[code].orEmpty()

    /** 星表主键 → 所属 code；未收录返回 null。 */
    fun codeOf(starId: Long): String? = codeByStarId[starId]

    /** 全部在册 code（只读视图）。 */
    fun codes(): Set<String> = membersByCode.keys

    /** 已建反向映射的星点总数。 */
    fun size(): Int = codeByStarId.size

    companion object {

        /** 空索引（占位态 / DAO 未就位回落；查询恒空，不崩）。 */
        fun empty(): SkyNameIndex = SkyNameIndex(emptyMap(), emptyMap())

        /**
         * 由 `code → 成员星主键列表` 构建双向索引；成员去重、保序，空 code 不入库。
         *
         * @param members 分区 code 到成员星 id 的映射；空表 → 空索引。
         */
        fun build(members: Map<String, List<Long>>): SkyNameIndex {
            val byCode = HashMap<String, List<Long>>()
            val byStar = HashMap<Long, String>()
            members.forEach { (code, starIds) ->
                val dedup = starIds.distinct()
                if (dedup.isEmpty()) return@forEach
                byCode[code] = dedup
                dedup.forEach { starId -> byStar[starId] = code }
            }
            return SkyNameIndex(byCode, byStar)
        }
    }
}
