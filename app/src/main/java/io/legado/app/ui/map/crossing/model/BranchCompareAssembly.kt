package io.legado.app.ui.map.crossing.model

/**
 * B10 分支对比装配态（纯 JVM，不依赖 Compose / 控制器实例）。
 *
 * 为什么密封层前置：壳层只按 [CompareSession] 穷举渲染，「有没有存档 / 存档是否失效 /
 * 两线是否一致」的判定全部落在本纯函数，可表驱动单测锁定；壳层不另写一套启用与空态口径。
 * 装配只消费不可变 [TurnSnapshot] 列表，不回写任何控制器（串线防护见接线报告二阶三阶节）。
 */
sealed interface CompareSession {

    /**
     * 存档不足：当前推演线在册，但没有可对照的分支存档（只存了一条线）。
     * [archivedCount] 为已存档条数，[currentTurns] 为当前线已推进回合数，供壳层写提示。
     */
    data class NeedArchive(val archivedCount: Int, val currentTurns: Int) : CompareSession

    /**
     * 分支已失效：存档回放线为空，或线内每一回合都无可视军队（全军溃散 / 旧档残缺）。
     * [reason] 为可读失效原因，壳层原样展示，不留白屏。
     */
    data class Invalidated(val reason: String, val archivedCount: Int) : CompareSession

    /**
     * 可进双栏对照。[identical] = 两线逐回合全部一致（无军队差异、无单侧事件、胜负档位一致）；
     * 为 true 时壳层出「两线一致」横幅，双栏仍可步进回看。
     */
    data class Ready(
        val left: List<TurnSnapshot>,
        val right: List<TurnSnapshot>,
        val warningsLeft: List<String>?,
        val warningsRight: List<String>?,
        val identical: Boolean,
    ) : CompareSession
}

/**
 * 分支对比装配纯函数（B10 接线）。
 *
 * 入参两条不可变快照线 + 两侧战役级告警台账；出参 [CompareSession] 三态穷举。
 * 告警台账为 null 表示该侧未下发（旧档 / 降级装配未收集），壳层显式写「未知」，不臆造空告警。
 */
object BranchCompareAssembly {

    /** 当前线全空时的失效原因：本线无可视快照，对照无从谈起。 */
    const val REASON_CURRENT_EMPTY: String = "当前推演线为空，暂无可对照的本线"

    /** 存档线全空 / 全军溃散时的失效原因。 */
    const val REASON_ARCHIVE_DEAD: String = "分支存档已失效：线内无可视军队"

    fun prepare(
        current: List<TurnSnapshot>,
        archived: List<TurnSnapshot>?,
        warningsLeft: List<String>?,
        warningsRight: List<String>?,
    ): CompareSession {
        val archive = archived.orEmpty()
        if (archive.isEmpty()) {
            return CompareSession.NeedArchive(archivedCount = archive.size, currentTurns = current.size)
        }
        if (current.isEmpty()) {
            return CompareSession.Invalidated(REASON_CURRENT_EMPTY, archivedCount = archive.size)
        }
        val archiveDead = archive.all { it.corps.isEmpty() }
        if (archiveDead) {
            return CompareSession.Invalidated(REASON_ARCHIVE_DEAD, archivedCount = archive.size)
        }
        val identical = linesIdentical(current, archive)
        return CompareSession.Ready(current, archive, warningsLeft, warningsRight, identical)
    }

    /**
     * 两线是否逐回合一致：长度必须相同；逐回合 [SnapshotDiff.diff] 的 [TurnDiff.turnIdentical]
     * 全为 true。长度不同即视为不同线（回合数本身就是差异），不做截断对齐。
     */
    private fun linesIdentical(a: List<TurnSnapshot>, b: List<TurnSnapshot>): Boolean {
        if (a.size != b.size) return false
        return a.indices.all { i -> SnapshotDiff.diff(a[i], b[i]).turnIdentical }
    }
}

/**
 * 入口按钮可用性纯判定（行为锁，供单测；壳层按钮启用 / 禁用文案只读本结构，不另写口径）。
 *
 * @property canSave 是否允许存档当前分支（当前战场有可视军队才可存）
 * @property canCompare 是否允许打开对比面板（有本线且至少一条存档）
 * @property compareDisabledReason 禁用原因；null = 可对比
 */
data class CompareEntry(
    val canSave: Boolean,
    val canCompare: Boolean,
    val compareDisabledReason: String?,
) {
    companion object {
        fun decide(archivedCount: Int, currentAlive: Boolean): CompareEntry {
            val canSave = currentAlive
            val canCompare = currentAlive && archivedCount >= 1
            val reason = when {
                !currentAlive -> "当前战场无可视军队"
                archivedCount < 1 -> "先存档一条分支再对照"
                else -> null
            }
            return CompareEntry(canSave, canCompare, reason)
        }
    }
}
