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
 * 翻页裁决结果。[allowed] 为 true 时 [denied] 必为 null。
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
 * 裁决口径与控制器既有翻页通道 [InkReadController.turnPage] 一致：
 *  1. 掀角动画进行中 → 屏蔽一切新翻页；
 *  2. 首页再上翻 / 末页再下翻 → 边界越界，拒绝；
 *  3. 其余放行，交引擎 prev() / next() 与掀角动画通道。
 *
 * 动画态 / 边界可翻态由宿主每帧从 View 与引擎灌入；本类不持有任何 Android 引用，
 * 便于把「什么情况下允许翻页」抽出来做表驱动单测与 bench。
 */
class PageTurnGate(
    var animating: Boolean,
    var canPrev: Boolean,
    var canNext: Boolean,
) {

    /** 按方向裁决一次翻页请求。 */
    fun evaluate(direction: EdgeDirection): TurnDecision = when {
        animating -> TurnDecision.denied(TurnDenied.Animating)
        direction == EdgeDirection.Prev && !canPrev -> TurnDecision.denied(TurnDenied.AtBoundary)
        direction == EdgeDirection.Next && !canNext -> TurnDecision.denied(TurnDenied.AtBoundary)
        else -> TurnDecision.ALLOWED
    }
}
