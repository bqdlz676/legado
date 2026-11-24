package io.legado.app.help.bubble.contract

/**
 * S-05 点晋纪年时空联动 · 纪年过滤纯函数（纯 JVM，不 import 任何 Android / Compose 类型，
 * 保证普通 JVM 单测可直接实例化断言）。
 *
 * 逐字出处：
 *  - `docs/落文-规格切片/切片-05-点晋纪年时空联动.md:3`：「用时空上下文过滤——先靠纪年换算
 *    + 人物朝代，锚定「当前这章是哪一年」；读《左传》锚到 -722~-453，就命中「春秋晋国」，
 *    把「唐以后的晋」全排掉」
 *  - `docs/落文-规格切片/切片-05-点晋纪年时空联动.md:4`：「时间命中优先，其余同名收进
 *    「还有 N 个同名」让用户一键切」
 *  - `docs/锚点/S-05-点晋纪年联动.md` §二参数表 / §三判据 1-4。
 *
 * 本单只交付过滤与文案两个纯函数：不接真实字典 / 人物 / 地名 Provider，不碰 UI；
 * 候选模型直接复用 S-02 冻结契约 [Candidate]，不另建第二套候选模型。
 */

/**
 * 按章内锚点公元年过滤同名 / 多义候选，并按「纪年命中优先 → tier 高者优先 → 同名按名升序」
 * 稳定排序后返回。
 *
 * @param candidates 按名字拉出的全量候选（允许几十上百条）；永不抛异常，空输入返回空列表。
 * @param chapterYear 章内锚点公元年（负数为公元前）；null 表示年代缺失，此时不做纪年过滤、
 * 不猜归属，全候选按 tier 排序回（锚点卡 §二「年份缺失：不做纪年过滤，全候选按 tier 排序回，不猜」）。
 *
 * 纪年口径：候选 `start..end` 区间与 [chapterYear] 相交即命中；区间任一端为 null 视为该侧
 * 不设限（年代不可考那一侧不据此判出局）。`chapterYear != null` 且全部候选都不相交时，
 * 返回空列表（锚点卡 §三判据 4：任何情况下不返回 null、不抛异常；本单口径为全过滤回空，
 * 不回退全量候选——与 [BubbleContractResolver] 的 `.ifEmpty { hits }` 兜底回退不同，
 * 取舍归后续接线单裁决，见本文件尾部二阶交接注记）。
 */
fun filterByEra(candidates: List<Candidate>, chapterYear: Int?): List<Candidate> {
    val survivors = when (chapterYear) {
        // 年份缺失：不做区间过滤，全候选交排序层按 tier 取舍
        null -> candidates
        else -> candidates.filter { inEraRange(it, chapterYear) }
    }

    // 命中优先：纪年相交者排前；年份缺失时无锚点可判命中，全员同组。
    val hitRank: (Candidate) -> Int = when (chapterYear) {
        null -> { _ -> 0 }
        else -> { entry -> if (inEraRange(entry, chapterYear)) 0 else 1 }
    }

    return survivors.sortedWith(
        compareBy<Candidate> { hitRank(it) }
            .thenBy { tierOrder(it.tier) }
            .thenBy { it.name }
    )
}

/**
 * 「还有 N 个同名」段文案。[filteredOutCount] 必须来自实际被过滤掉的同名余项数量
 * （锚点卡 §三判据 3：同名余项文案里的 N 必须等于实际被过滤掉的数量，不许写死）。
 * N <= 0 时返回空串（没有余项就不渲染这一段，不留空态文案）。
 */
fun sameNameLabel(filteredOutCount: Int): String {
    if (filteredOutCount <= 0) return ""
    return "还有 $filteredOutCount 个同名"
}

/**
 * 候选年代区间是否覆盖 [year]；起止年任一为 null 视为该侧不设限，不据此判出局。
 * 口径与 [BubbleContractResolver] 的私有 inRange 保持一致：年份点落在 `[start, end]` 内。
 */
private fun inEraRange(entry: Candidate, year: Int): Boolean {
    val afterStart = entry.start == null || year >= entry.start
    val beforeEnd = entry.end == null || year <= entry.end
    return afterStart && beforeEnd
}

/**
 * tier 排序：L1 手校核心优先，L2 多源核验次之，L3 孤证存疑垫底；未知 tier 按 L3 处理。
 * 与 [BubbleContractResolver] 的 tierOrder 同口径；此处私有复制是因为既有 resolver 的
 * 分层映射挂在其私有 companion，本单不许改既有文件，故在纯函数侧留一份同值常量映射。
 */
private fun tierOrder(tier: String): Int = when (tier) {
    "L1" -> 0
    "L2" -> 1
    else -> 2
}

/*
 * 二阶交接注记（不进运行时逻辑，仅供后续接线单对照）：
 *  - 对 S-02 resolve 出口：[BubbleContractResolver.resolve] 在纪年全不相交时回退全量 hits
 *    （`.ifEmpty { hits }`），本纯函数全过滤回空列表；最终出口采用哪一种兜底策略，
 *    由接线单按锚点卡 §三判据 4「不抛异常、不留空态」统一裁决，本单不擅自改 resolver。
 *  - 对 S-11 半屏 Sheet：Sheet 第⑤段「同名的其他 N 人」只消费本函数返回的排好序的
 *    [Candidate] 列表与 [sameNameLabel] 文案；被纪年排掉的余项数量由调用方算
 *    （入参总数 - 过滤后条数）传入 [sameNameLabel]，本函数不替 UI 决定余项如何分组渲染。
 */
