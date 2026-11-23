package io.legado.app.ui.book.read.ink

import io.legado.app.help.bubble.BubbleCandidate

/**
 * 候选循环（规格切片-02 §4「候选切换」）。
 *
 * 为什么单独抽成纯逻辑类：候选回绕 / 下标收敛不依赖 Compose 与 Android，
 * 离线 JVM 单测可直接覆盖边界（空列表、单候选、负下标、越界下标），
 * 气泡 UI 只负责把 [display] 画出来、把 [query] 回传给解析链重查。
 *
 * 双字段口径（C2 冻结契约）：
 *  - [BubbleCandidate.query] 是回传解析链重查的裸名 / 主键，不带括号装饰；
 *  - [BubbleCandidate.display] 是气泡上展示的名字，可附朝代 / 辖区 / 年代说明。
 * 点选切换时只回传 [query]，避免展示串里的括号装饰把重查词污染成查不到的整串。
 *
 * @param candidates 同名 / 多义候选；空列表表示当前义项唯一，不出切换行。
 * @param initial 初始下标；越界 / 负值先收敛到合法区间。
 */
class CandidateCycle(
    val candidates: List<BubbleCandidate>,
    initial: Int = 0,
) {
    /** 候选总数。 */
    val size: Int get() = candidates.size

    /** 当前下标（恒落在 [0, size) 内；空列表恒为 0）。 */
    var index: Int = normalize(initial)
        private set

    /** 当前候选；空列表返回 null，UI 据此隐藏候选行，不出空气泡。 */
    val current: BubbleCandidate? get() = candidates.getOrNull(index)

    /** 当前展示名（带朝代 / 辖区括号装饰）；无候选返回 null。 */
    val display: String? get() = current?.display

    /** 重查裸词（不带括号装饰）；无候选返回 null。 */
    val query: String? get() = current?.query

    /** 是否需要出「还有 N 种解释」切换行：多于一个候选才可循环。 */
    fun hasCandidates(): Boolean = candidates.size > 1

    /** 前进一位并环形回绕；返回切换后的候选；空列表返回 null。 */
    fun next(): BubbleCandidate? {
        if (candidates.isEmpty()) return null
        index = nextCandidateIndex(size, index)
        return current
    }

    /** 直接定位到指定候选；越界先收敛；空列表返回 null。 */
    fun selectAt(target: Int): BubbleCandidate? {
        if (candidates.isEmpty()) return null
        index = normalize(target)
        return current
    }

    private fun normalize(i: Int): Int =
        if (candidates.isEmpty()) 0 else ((i % size) + size) % size

    companion object {
        /**
         * 环形回绕下标：[current] 先收敛到合法区间再 +1，末尾回绕到 0。
         * 空列表 / 非正 size 兜底回 0，不除零。
         */
        fun nextIndex(size: Int, current: Int): Int {
            if (size <= 0) return 0
            val base = ((current % size) + size) % size
            return (base + 1) % size
        }
    }
}

/** 顶层便捷函数，供旧调用点与 bench 直接取用环形回绕口径。 */
internal fun nextCandidateIndex(size: Int, current: Int): Int =
    CandidateCycle.nextIndex(size, current)

/**
 * 候选行文案（切片-02 §4：气泡顶部一行「还有 N 种解释」）。
 * 首项展示总数引导；切到后续候选时标明序号与当前展示名。
 */
fun candidateLabel(cycle: CandidateCycle): String {
    if (cycle.size == 0) return ""
    return if (cycle.index == 0) {
        "还有 ${cycle.size} 种解释 ›"
    } else {
        "候选 ${cycle.index + 1}/${cycle.size}：${cycle.display.orEmpty()} ›"
    }
}
