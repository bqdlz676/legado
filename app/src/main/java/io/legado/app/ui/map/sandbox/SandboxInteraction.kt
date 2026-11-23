package io.legado.app.ui.map.sandbox

/** 给军队下达的新意图（切片-06 棋子交互：进军 / 包抄）。 */
enum class OrderIntent {
    ADVANCE,
    FLANK,
}

/** 选中棋子后拖拽出的预演虚线：从棋子到目标地形点，标明意图。 */
data class OrderPreview(
    val pieceId: String,
    val target: LatLng,
    val intent: OrderIntent,
)

/** 信息卡类别（切片-06 信息查看：箭头 / 渡口 / 遗址战报 / 棋子）。 */
enum class InfoKind {
    ARROW,
    FERRY,
    SITE_REPORT,
    PIECE,
    PHASE_NOTE,
}

/**
 * 悬浮信息卡：3 秒后自动消失，不遮挡地形。
 * [shownAtMs] 由注入时钟打点，单测可喂固定时间，不依赖系统时钟。
 */
data class InfoCard(
    val kind: InfoKind,
    val title: String,
    val body: String,
    val shownAtMs: Long,
) {
    fun expired(nowMs: Long): Boolean =
        nowMs - shownAtMs >= SandboxConfig.INFO_CARD_TTL_MS
}

/** 两棋子捏合测距结果：球面公里数 + 行军预计天数。 */
data class MeasureResult(val km: Double, val marchDays: Int)

/**
 * 沙盘交互状态（纯 JVM，切片-06 §棋子交互 / 时间轴 / 推演播放 / 剧场模式 / 信息查看 / 模式切换）。
 *
 * 为什么时钟可注入：信息卡 3 秒超时、剧场锁定等状态都依赖时间；注入时钟后单测能直接
 * 推进毫秒断言，不必 sleep。手势命中（点中哪枚棋子 / 哪个箭头）由壳层做坐标拾取后回调。
 */
class SandboxInteraction(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    // ── 棋子选中与下令 ──

    var selectedPieceId: String? = null
        private set

    var orderPreview: OrderPreview? = null
        private set

    fun selectPiece(pieceId: String) {
        selectedPieceId = pieceId
        // 重新选中棋子时清掉上一条预演虚线，避免两支军队的命令叠在一起。
        orderPreview = null
    }

    /** 选中棋子后再点地形：下达进军 / 包抄意图，记录预演虚线。 */
    fun issueOrder(target: LatLng, intent: OrderIntent) {
        val piece = selectedPieceId ?: return
        orderPreview = OrderPreview(piece, target, intent)
    }

    /** 点空地：取消选中与预演虚线。 */
    fun clearSelection() {
        selectedPieceId = null
        orderPreview = null
    }

    // ── 信息卡 ──

    var infoCard: InfoCard? = null
        private set

    fun showInfo(kind: InfoKind, title: String, body: String) {
        infoCard = InfoCard(kind, title, body, clock())
    }

    /** 每帧调用：超时的信息卡自动收回；返回当前仍有效的卡片。 */
    fun update(): InfoCard? {
        val card = infoCard
        if (card != null && card.expired(clock())) infoCard = null
        return infoCard
    }

    // ── 双棋子测距 ──

    fun measure(a: LatLng, b: LatLng, dailyLi: Int = SandboxConfig.DAILY_LI_MARCH): MeasureResult {
        val km = a.distanceKm(b)
        return MeasureResult(km, LatLng.marchDays(km, dailyLi))
    }

    // ── 时间轴与推演播放 ──

    var playing: Boolean = false
        private set

    /** 当前回合（可小数，供箭头 / 棋子连续跟随）。 */
    var turn: Double = 0.0
        private set

    /** 当前阶段名；长按篆章时把阶段简介写进信息卡。 */
    var currentPhaseLabel: String? = null
        private set

    fun play() { playing = true }

    fun pause() { playing = false }

    /** 暂停状态下单步前进一回合；播放中调用不生效（切片-06：▸▸ 仅 ⏸ 状态可用）。 */
    fun stepForward(maxTurn: Int) {
        if (playing) return
        turn = (turn + 1.0).coerceAtMost(maxTurn.toDouble())
    }

    /** 拖动金线跳转回合；棋子瞬移、箭头跟切。 */
    fun seek(turnValue: Double, maxTurn: Int) {
        turn = turnValue.coerceIn(0.0, maxTurn.toDouble())
    }

    /** 点篆章节点：跳到该 phase 第一回合。 */
    fun jumpPhase(firstTurn: Int, label: String, maxTurn: Int) {
        turn = firstTurn.coerceIn(0, maxTurn).toDouble()
        currentPhaseLabel = label
    }

    /** 长按篆章：弹出阶段简介信息卡。 */
    fun showPhaseNote(label: String, note: String) {
        currentPhaseLabel = label
        showInfo(InfoKind.PHASE_NOTE, label, note)
    }

    /** 播放中两指缩放：只改相机，不暂停（切片-06 推演播放末条）。 */
    fun zoomDuringPlay() { /* 播放态保持不变，占位供壳层确认不触发 pause */ }

    // ── 剧场模式 ──

    var theaterMode: Boolean = false
        private set

    /** 交战回合自动推近聚焦的冲突点；null 表示无聚焦。 */
    var focusedClash: LatLng? = null
        private set

    fun enterTheater() {
        theaterMode = true
        // 剧场模式锁定所有交互，只保留左下「退出剧场」出口。
        clearSelection()
        infoCard = null
    }

    fun exitTheater() {
        theaterMode = false
        focusedClash = null
    }

    /** 交战回合自动推近镜头聚焦冲突点。 */
    fun focusClash(point: LatLng?) {
        focusedClash = point
    }

    // ── 2D / 3D 模式切换 ──

    /** 切换前记录地图中心点；切回时复原，保证中心点不变（切片-06 模式切换末条）。 */
    var lastCenter: LatLng? = null
        private set

    fun preserveCenter(center: LatLng) {
        lastCenter = center
    }
}
