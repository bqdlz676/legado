package io.legado.app.ui.book.read.ink

/**
 * 翻页被拒原因（纯 JVM 状态机结论，不绑定 View / 引擎）。
 */
enum class TurnDenied {
    /** 掀角翻页动画进行中：屏蔽新翻页，避免两次动画打架。 */
    Animating,

    /** 边界越界：首页再上翻 / 末页再下翻。 */
    AtBoundary,
}

/**
 * 翻页裁决结果。[allowed] 为 true 时 [denied] 必为 null；[allowed] 为 false 时 [denied] 必非 null。
 */
data class TurnDecision(
    val allowed: Boolean,
    val denied: TurnDenied?,
) {
    companion object {
        val ALLOWED = TurnDecision(true, null)

        fun denied(reason: TurnDenied) = TurnDecision(false, reason)
    }
}

/**
 * 翻页门禁状态机（纯 JVM，可离线单测）。
 *
 * 状态集合（由宿主每帧灌入的三个布尔派生，本闸不自持动画生命周期）：
 *  - **Idle**：[animating] = false；再按 [canPrev] / [canNext] 分可翻 / 边界两档；
 *  - **Animating**：[animating] = true，屏蔽 Prev / Next 一切新翻页；
 *  - **AtBoundary**：空闲但目标方向侧不可翻（首页 Prev / 末页 Next）。
 *
 * 裁决优先级（非法 / 冲突输入处置，不 throw、不崩溃）：
 *  1. 动画态优先于边界：动画中且处于首页，拒绝原因记 [TurnDenied.Animating]，不记边界；
 *  2. 宿主灌入的标志互相矛盾（如动画位 stuck=true 而引擎已 settled）时，以宿主标志为准，
 *     本闸不擅自复位；矛盾会累积进 [deniedAnimatingCount]，供上层排查 stuck 状态；
 *  3. 方向为枚举非空；若未来扩展方向，未收录分支按边界拒绝处理并记计数，不放行。
 *
 * 裁决口径与控制器既有翻页通道 [InkReadController.turnPage] 一致：
 *  1. 掀角动画进行中 → 屏蔽一切新翻页；
 *  2. 首页再上翻 / 末页再下翻 → 边界越界，拒绝；
 *  3. 其余放行，交引擎 prev() / next() 与掀角动画通道。
 *
 * 动画态 / 边界可翻态由宿主每帧从 View 与引擎灌入；本类不持有任何 Android 引用，
 * 便于把「什么情况下允许翻页」抽出来做表驱动单测与 bench。
 *
 * 交接注记：旧 `ReadBookActivity` 翻页闸位散落在 `ReadView` 动画回调与
 * `handleKeyPage` 链路内（旧实现留待 B4-31 下线）；本闸是新壳唯一翻页裁决入口。
 */
class PageTurnGate(
    var animating: Boolean,
    var canPrev: Boolean,
    var canNext: Boolean,
) {
    /** 累计裁决次数（含放行与拒绝），可观测计数，不写日志、不持上下文。 */
    var evaluatedCount: Int = 0
        private set

    /** 累计被动画态屏蔽次数。 */
    var deniedAnimatingCount: Int = 0
        private set

    /** 累计被边界拒绝次数。 */
    var deniedBoundaryCount: Int = 0
        private set

    /** 累计放行次数。 */
    var allowedCount: Int = 0
        private set

    /** 按方向裁决一次翻页请求。 */
    fun evaluate(direction: EdgeDirection): TurnDecision {
        evaluatedCount++
        val decision = when {
            animating -> {
                deniedAnimatingCount++
                TurnDecision.denied(TurnDenied.Animating)
            }
            direction == EdgeDirection.Prev && !canPrev -> {
                deniedBoundaryCount++
                TurnDecision.denied(TurnDenied.AtBoundary)
            }
            direction == EdgeDirection.Next && !canNext -> {
                deniedBoundaryCount++
                TurnDecision.denied(TurnDenied.AtBoundary)
            }
            else -> {
                allowedCount++
                TurnDecision.ALLOWED
            }
        }
        return decision
    }

    /** 清零可观测计数（翻页会话重置 / 章切换时调用；不改动三个裁决输入标志）。 */
    fun resetCounters() {
        evaluatedCount = 0
        deniedAnimatingCount = 0
        deniedBoundaryCount = 0
        allowedCount = 0
    }
}
