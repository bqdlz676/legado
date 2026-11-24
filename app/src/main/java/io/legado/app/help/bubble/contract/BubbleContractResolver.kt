package io.legado.app.help.bubble.contract

/**
 * S-02 气泡协议 · 消歧出口骨架（冻结签名 `resolve(name, year?) → List<Candidate>`，
 * 出处：`docs/立项草案-批次D.md:9`；`docs/立项-冲突裁决.md:97` 裁决取 Kotlin 原生，
 * 本出口纯 JVM，不引入 Node / WebView）。
 *
 * 本单只立骨架：候选源由构造器注入（当前为空注册表），真实字典 / 人物 / 地名 / 纪年 Provider
 * 在后续接线单按切片-02:21 优先级链接入。三条边界必须兜底，不留空白态、不抛异常：
 *  ① 空输入（空白名）；② 注册表未命中；③ 年份缺失或年代区间对不上。
 *  三态都返回一条结构完整的未命中 [Candidate]：confidence=[Confidence.LOW]，
 *  source=[MISSING_SOURCE]，isDefault=false——low 置信条不进默认命中（草案D:9 口径）。
 *
 * 二阶交接：
 *  - S-05 纪年时空联动：用 [year] 做年代选层 / 同期对照，锚点年为空时不猜，全候选回；
 *  - S-11 半屏 Sheet：直接读 [Candidate.name] / [aliases] / [confidence] / [source] 渲染，
 *    不另建第二套候选模型。
 */
class BubbleContractResolver(
    private val registry: List<Candidate> = emptyList(),
) {

    /**
     * 按名（含别名）消歧查询。[year] 为锚点公元年（负数为公元前）；null 表示年代缺失，
     * 此时不做纪年过滤、不猜归属，全部候选按 tier 排序返回。
     *
     * 返回值永不为 null：查不到时返回单元素未命中候选列表；注册表遍历抛异常也在内部兜住。
     */
    fun resolve(name: String, year: Int?): List<Candidate> {
        val token = name.trim()
        if (token.isEmpty()) return listOf(missCandidate(token))

        val hits = runCatching {
            registry.filter { entry -> entry.name == token || token in entry.aliases }
        }.getOrDefault(emptyList())

        if (hits.isEmpty()) return listOf(missCandidate(token))

        val visible = when (year) {
            // 年份缺失：不做区间过滤（锚点年为空不猜），全候选交上层按 tier 取舍
            null -> hits
            else -> hits.filter { inRange(it, year) }.ifEmpty { hits }
        }
        return visible.sortedWith(tierRank.thenBy { it.name })
    }

    /** 年代区间是否覆盖 [year]；起止年任一为空视为该侧不设限，不据此判出局。 */
    private fun inRange(entry: Candidate, year: Int): Boolean {
        val afterStart = entry.start == null || year >= entry.start
        val beforeEnd = entry.end == null || year <= entry.end
        return afterStart && beforeEnd
    }

    /** 兜底未命中条目：字段全部就位，仅置信 / 来源 / 默认位标为未命中，UI 不得据此跳转。 */
    private fun missCandidate(token: String): Candidate = Candidate(
        id = "",
        name = token.ifBlank { "未识" },
        type = CandidateType.ALLUSION,
        tier = "L3",
        start = null,
        end = null,
        aliases = emptyList(),
        confidence = Confidence.LOW,
        source = MISSING_SOURCE,
        isDefault = false,
    )

    private companion object {
        /** tier 排序：L1 手校核心优先，L2 多源核验次之，L3 孤证存疑垫底；未知 tier 按 L3 处理。 */
        val tierRank: Comparator<Candidate> = compareBy { tierOrder(it.tier) }

        fun tierOrder(tier: String): Int = when (tier) {
            "L1" -> 0
            "L2" -> 1
            else -> 2
        }
    }
}
