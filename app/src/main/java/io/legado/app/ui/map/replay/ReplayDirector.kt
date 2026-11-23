package io.legado.app.ui.map.replay

/**
 * 出生编排阶段（切片-07 第一帧加载：地形出生）。
 *
 * GRID 0-400ms 经纬网格由暗到亮 → RISE 400-800ms 地形隆起 → FOG 800-1000ms
 * 雾气沉谷 → DROP 1000-1200ms 棋子落地带涟漪 → READY 1200ms 标题 / 面板入场。
 */
enum class BirthStage {
    GRID,
    RISE,
    FOG,
    DROP,
    READY,
}

/** 信息卡类别（回放内：棋子 / 箭头 / 阶段 / 战报）。 */
enum class ReplayInfoKind {
    PIECE,
    ARROW,
    PHASE,
    REPORT,
}

/** 悬浮信息卡：3 秒自动消失，不遮地形。 */
data class ReplayInfoCard(
    val kind: ReplayInfoKind,
    val title: String,
    val body: String,
    val shownAtMs: Long,
) {
    fun expired(nowMs: Long): Boolean = nowMs - shownAtMs >= ReplayConfig.INFO_CARD_TTL_MS
}

/**
 * 战役回放导演（纯 JVM，切片-07 全局编排核心）。
 *
 * 统管：出生编排阶段、推演时钟（播放 / 暂停 / 单步 / 跳转）、战役事件派发、
 * 顶栏 / 底栏空闲自动隐藏、剧场模式、信息卡超时、交战与遭遇效果生成。
 *
 * 时钟可注入：单测喂固定毫秒即可推进各阶段断言，不 sleep、不依赖系统时间。
 * 边界兜底：战役无棋子 / 无阶段时对应 pass 跳过，不崩、不空白态。
 */
class ReplayDirector(
    val campaign: Campaign,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val bornAtMs: Long = clock()

    /** 当前棋子状态（事件派发后就地复制替换）。 */
    private var armies: List<ArmyPiece> = campaign.armies

    private val appliedEvents = mutableSetOf<String>()
    private val routStartMs = mutableMapOf<String, Long>()

    // ── 推演时钟 ──

    var playing: Boolean = false
        private set

    /** 当前回合（可小数，供箭头 / 棋子连续跟随）。 */
    var turn: Double = 0.0
        private set

    // ── 选中 / 信息卡 ──

    var selectedPieceId: String? = null
        private set

    var infoCard: ReplayInfoCard? = null
        private set

    // ── 栏位显隐 ──

    var topBarVisible: Boolean = true
        private set

    var bottomBarExpanded: Boolean = true
        private set

    private var lastInteractionMs: Long = clock()

    // ── 剧场模式 ──

    var theaterMode: Boolean = false
        private set

    // ── 交战 / 遭遇效果 ──

    val clashes: MutableList<ClashEffect> = mutableListOf()
    val encounters: MutableList<EncounterRing> = mutableListOf()

    /** 交战触发距离阈值（公里）：敌我棋子进入此距离判定相邻交战。 */
    var clashThresholdKm: Double = CLASH_THRESHOLD_KM
        private set

    // ══════════════════════════ 出生编排 ══════════════════════════

    fun birthElapsedMs(nowMs: Long): Long = (nowMs - bornAtMs).coerceAtLeast(0)

    fun birthStage(nowMs: Long): BirthStage {
        val t = birthElapsedMs(nowMs)
        return when {
            t < ReplayConfig.BIRTH_GRID_END_MS -> BirthStage.GRID
            t < ReplayConfig.BIRTH_RISE_END_MS -> BirthStage.RISE
            t < ReplayConfig.BIRTH_FOG_END_MS -> BirthStage.FOG
            t < ReplayConfig.BIRTH_DROP_END_MS -> BirthStage.DROP
            else -> BirthStage.READY
        }
    }

    // ══════════════════════════ 推演时钟 ══════════════════════════

    fun play() { playing = true }

    fun pause() { playing = false }

    /** 暂停态单步前进一回合；播放中调用不生效（切片-06：▸▸ 仅 ⏸ 状态可用）。 */
    fun stepForward() {
        if (playing) return
        turn = (turn + 1.0).coerceAtMost(campaign.rounds.toDouble())
    }

    /** 拖动金线跳转回合；棋子瞬移、箭头跟切。 */
    fun seek(turnValue: Double) {
        turn = turnValue.coerceIn(0.0, campaign.rounds.toDouble())
    }

    /** 点阶段铜点：跳到该 phase 第一回合，地图箭头瞬切。 */
    fun jumpPhase(phase: BattlePhase) {
        turn = phase.turn.coerceIn(0, campaign.rounds).toDouble()
    }

    fun currentPhase(): BattlePhase? = campaign.phaseAt(turn.toInt())

    fun progress(): Double =
        if (campaign.rounds <= 0) 0.0 else (turn / campaign.rounds).coerceIn(0.0, 1.0)

    // ══════════════════════════ 交互与栏位 ══════════════════════════

    /** 用户碰屏：刷新空闲计时，顶栏 / 底栏重新显出；剧场模式下除退出按钮外不响应。 */
    fun interact() {
        if (theaterMode) return
        lastInteractionMs = clock()
        topBarVisible = true
        bottomBarExpanded = true
    }

    fun selectPiece(pieceId: String?) {
        if (theaterMode) return
        selectedPieceId = pieceId
    }

    fun showInfo(kind: ReplayInfoKind, title: String, body: String) {
        infoCard = ReplayInfoCard(kind, title, body, clock())
    }

    fun enterTheater() {
        theaterMode = true
        selectedPieceId = null
        infoCard = null
    }

    fun exitTheater() {
        theaterMode = false
        lastInteractionMs = clock()
    }

    // ══════════════════════════ 主更新循环 ══════════════════════════

    /**
     * 推进一帧。
     * @param dtMs 距上一帧的毫秒间隔；时钟用于事件 / 卡片 / 效果计时
     */
    fun update(dtMs: Long) {
        val now = clock()

        // 推演推进：每回合 1.2s；播完自动暂停。
        if (playing && !campaign.isComplete(turn)) {
            turn += dtMs.toDouble() / ReplayConfig.TURN_DURATION_MS
            if (campaign.isComplete(turn)) {
                turn = campaign.rounds.toDouble()
                playing = false
            }
        }

        dispatchEvents(now)
        detectClashes(now)
        cleanupEffects(now)

        // 信息卡 3 秒超时收回。
        val card = infoCard
        if (card != null && card.expired(now)) infoCard = null

        // 空闲 3 秒：顶栏淡出；底栏收缩成 4dp 金线。剧场模式下保持锁定不显 UI。
        if (!theaterMode && now - lastInteractionMs >= ReplayConfig.TOP_BAR_IDLE_MS) {
            topBarVisible = false
        }
        if (!theaterMode && now - lastInteractionMs >= ReplayConfig.BOTTOM_BAR_IDLE_MS) {
            bottomBarExpanded = false
        }
    }

    // ══════════════════════════ 事件派发 ══════════════════════════

    private fun dispatchEvents(now: Long) {
        val current = turn.toInt()
        campaign.events.forEachIndexed { index, event ->
            val key = "${event.targetId}#$index"
            if (appliedEvents.contains(key)) return@forEachIndexed
            val triggered = event.turn?.let { current >= it } ?: conditionTriggered(event)
            if (!triggered) return@forEachIndexed

            armies = armies.map { piece ->
                if (piece.id != event.targetId) return@map piece
                when (event.action) {
                    EventAction.MORALE_CHANGE ->
                        piece.withMorale(Morale.fromLabel(event.value))
                    EventAction.SUPPLY_CUT ->
                        if (piece.morale == Morale.ROUT) piece
                        else piece.withMorale(Morale.WAVERING)
                    EventAction.SURRENDER -> {
                        routStartMs[piece.id] = now
                        piece.withMorale(Morale.ROUT)
                    }
                }
            }
            appliedEvents.add(key)
        }
    }

    /** 条件触发事件（如兵力低于阈值）：本版本种子无兵力衰减，兜底为不触发。 */
    private fun conditionTriggered(event: BattleEvent): Boolean = false

    // ══════════════════════════ 交战 / 遭遇检测 ══════════════════════════

    private fun detectClashes(now: Long) {
        val own = armiesByRole(SideRole.OWN)
        val enemy = armiesByRole(SideRole.ENEMY)
        own.forEach { a ->
            enemy.forEach { b ->
                val dist = LatLng(a.lat, a.lng).distanceKm(LatLng(b.lat, b.lng))
                if (dist <= clashThresholdKm && !hasActiveClash(now)) {
                    val mid = LatLng((a.lat + b.lat) / 2, (a.lng + b.lng) / 2)
                    clashes.add(ClashEffect("clash-${a.id}-${b.id}", mid, now))
                }
            }
        }

        // 箭头相遇 → 300ms 朱砂遭遇光环。
        val arrows = arrows()
        arrows.forEachIndexed { i, a ->
            arrows.drop(i + 1).forEach { b ->
                if (a.role != b.role && a.meets(b, clashThresholdKm)) {
                    val mid = a.points.last()
                    if (encounters.none { it.active(now) && it.point.distanceKm(mid) <= clashThresholdKm }) {
                        encounters.add(EncounterRing("enc-${a.armyId}-${b.armyId}", mid, now))
                    }
                }
            }
        }
    }

    private fun hasActiveClash(now: Long): Boolean = clashes.any { !it.isFinished(now) }

    private fun cleanupEffects(now: Long) {
        clashes.removeAll { it.isFinished(now) }
        encounters.removeAll { it.isFinished(now) }
    }

    // ══════════════════════════ 派生视图数据 ══════════════════════════

    fun armies(): List<ArmyPiece> = armies

    fun armiesByRole(role: SideRole): List<ArmyPiece> =
        armies.filter { campaign.sideOf(it)?.role == role }

    private fun roleOf(piece: ArmyPiece): SideRole? = campaign.sideOf(piece)?.role

    /** 棋子视觉状态列表；溃散棋子按已持续毫秒决定碎片 / 残影圈。 */
    fun visuals(): List<PieceVisual> = armies.map { piece ->
        val routElapsed = routStartMs[piece.id]?.let { clock() - it }
        PieceVisual.of(
            piece = piece,
            role = roleOf(piece),
            selected = piece.id == selectedPieceId,
            routElapsedMs = routElapsed,
        )
    }

    /** 行军箭头列表；设伏棋子不出兵锋。 */
    fun arrows(): List<BattleArrow> = armies.mapNotNull { piece ->
        BattleArrow.fromPiece(piece, roleOf(piece) ?: SideRole.OWN)
    }

    companion object {
        /** 交战相邻阈值：敌我棋子相距 10 公里内判定相邻（地形视野内的近身距离）。 */
        const val CLASH_THRESHOLD_KM: Double = 10.0
    }
}
