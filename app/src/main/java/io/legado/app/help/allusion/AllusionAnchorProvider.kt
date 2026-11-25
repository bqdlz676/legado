package io.legado.app.help.allusion

import io.legado.app.help.chrono.ContextAnchor

/**
 * 模块 D4 · 典故候选 → 全 app 唯一消歧总线 [ContextAnchor.allusionProvider] 的接线适配。
 *
 * 为什么独立成适配层（对齐 ContextAnchor.kt §一 注释口径）：
 * - 人物 / 典故来源由各自模块在预载时注册，anchor 总线不反向依赖典故词表；
 * - 本对象只做「典故条目 → [ContextAnchor.Candidate]」的字段映射，不碰消歧算法、
 *   排序口径与默认命中闸（这些仍由 [ContextAnchor.resolve] 统一负责）。
 *
 * 注册时机（对齐 PersonProvider.bindAnchorProvider 双保险）：
 * - [init] 块在本对象首次被触达时即注册一次；
 * - [bindAnchorProvider] 单独暴露，供 BubbleResolver.preload 预热后显式重绑，
 *   不依赖对象初始化时序；可重复调用，幂等覆盖为本适配入口。
 *
 * 优雅降级：词表未就绪 / 查询抛异常时返回空列表，总线候选为空但不抛异常、不崩；
 * 未注册时 [ContextAnchor.allusionProvider] 保持 null，[ContextAnchor.resolve] 照常工作。
 */
object AllusionAnchorProvider {

    init {
        bindAnchorProvider()
    }

    /**
     * 注册典故候选入口到共享消歧总线。幂等：重复调用只覆盖为本适配的查询入口。
     */
    fun bindAnchorProvider() {
        ContextAnchor.allusionProvider = { name -> anchorCandidates(name) }
    }

    /**
     * 词形 → 典故候选列表；查无此典故返回空列表，不塞假数据、不凭字面猜典故。
     * 异常一律兜成空列表，保证总线 [ContextAnchor.resolve] 不会被本路拖崩。
     */
    private fun anchorCandidates(name: String): List<ContextAnchor.Candidate> {
        val clean = name.trim()
        if (clean.isEmpty()) return emptyList()
        return runCatching { AllusionDict.find(clean) }
            .getOrNull()
            ?.let(::toCandidate)
            ?.let { listOf(it) }
            ?: emptyList()
    }

    /**
     * 条目 → 总线候选。映射口径（字段只取 [AllusionEntry] 真实承载的值，缺项如实留空，不凭空造值）：
     * - id：条目稳定主键 [AllusionEntry.id]；
     * - name：典故词形 [AllusionEntry.word]；
     * - type：固定 [ContextAnchor.Type.ALLUSION]；
     * - tier：条目无「实测 / 考证 / 推定」分层字段，置 0，对齐纪年 ERA 候选口径，
     *   不参与 tier 档位比较；
     * - start / end：条目无本事年代区间字段，留 null，不编造生卒 / 年代；
     *   时空过滤档不命中该候选，但候选照收，不被丢弃；
     * - aliases：条目无别名字段，置空列表；
     * - confidence：单源公版典籍（装载 / 注册强制校验出处含书名号与公版口径），
     *   对齐人物 PersonSource.Classic 与纪年 ERA 的 MEDIUM 口径；HIGH 需三源交叉
     *   不满足，LOW 的「待考·来源冲突」标签与单源公版实情不符，故取 MEDIUM；
     * - source：出处标注 [AllusionEntry.source] 原样透传；
     * - note：一句话释义 [AllusionEntry.body]，供候选列表副标展示。
     */
    private fun toCandidate(e: AllusionEntry): ContextAnchor.Candidate =
        ContextAnchor.Candidate(
            id = e.id,
            name = e.word,
            type = ContextAnchor.Type.ALLUSION,
            tier = 0,
            start = null,
            end = null,
            aliases = emptyList(),
            confidence = ContextAnchor.Confidence.MEDIUM,
            source = e.source,
            note = e.body,
        )
}
