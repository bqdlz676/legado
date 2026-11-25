package io.legado.app.ui.map.replay

import io.legado.app.ui.map.sandbox.TerrainElevation

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
    /**
     * 地形高度采样器；由壳层注入沙盘同一个 [TerrainElevation] 实例（DEM 解码失败时
     * 注入 [TerrainElevation.Flat]）。回放域内不二次解码 DEM 资产，只取接口高度。
     */
    val elevation: TerrainElevation = TerrainElevation.Flat,
) {

    private val bornAtMs: Long = clock()

    /** 当前棋子状态（事件派发后就地复制替换）。 */
    private var armies: List<ArmyPiece> = campaign.armies

    /**
     * 地形是否降级为平面（Flat 兜底或 DEM 栅格退化）。
     * 仅供壳层显示一行可见标志；降级不阻断时钟推进 / 事件派发 / 交战检测。
     */
    val terrainDegraded: Boolean get() = ReplayGround.isDegraded(elevation)

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

    /**
     * 派发 / 边界告警台账：未知动作、目标棋子缺失、缺字段被拒绝等不生效项逐条登记，
     * 供壳层观测；与活栈 warnings 口径一致——不生效必须可观测，不许静默空转。
     * 上限 [MAX_WARNINGS]：超出截断最旧条目、保最新 N 条；复位时机为导演实例新建
     * （一场战役一个导演）或壳层重开回放时调用 [clearWarnings]。
     */
    val warnings: MutableList<String> = mutableListOf()

    /** 登记一条告警；超出上限截断最旧条目，保最新 N 条。 */
    fun recordWarning(message: String) {
        warnings.add(message)
        while (warnings.size > MAX_WARNINGS) warnings.removeAt(0)
    }

    /** 清空告警台账；重开一场战役 / 回放复位时调用（导演新建时台账本为空）。 */
    fun clearWarnings() {
        warnings.clear()
    }

    // 上一帧仍相邻的敌我对子。交战只在「由不相邻变为相邻」的沿边触发：
    // 否则一场交战 DONE 被清理后，位置没动的同一对子下一帧会立刻再炸一场，动画永远清不空。
    private val adjacentClashPairs = mutableSetOf<String>()

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
        // 时间轴瞬移后位置关系重算，旧的相邻沿边状态作废，避免跳回开战前被沿边标志压住。
        adjacentClashPairs.clear()
    }

    /** 点阶段铜点：跳到该 phase 第一回合，地图箭头瞬切。 */
    fun jumpPhase(phase: BattlePhase) {
        turn = phase.turn.coerceIn(0, campaign.rounds).toDouble()
        adjacentClashPairs.clear()
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

        // 推演推进：每回合 1.2s；播完自动暂停。已在末回合时按播放也要立即钳制停稳，
        // 不能因为外层「未播完」条件不成立就跳过收尾，否则 playing 会一直挂在结束态。
        if (playing) {
            if (!campaign.isComplete(turn)) {
                turn += dtMs.toDouble() / ReplayConfig.TURN_DURATION_MS
            }
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

            // 边界：目标棋子 id 在战役中不存在；记告警不抛异常，事件仍登记为已点燃，不重复结算。
            val target = armies.firstOrNull { it.id == event.targetId }
            if (target == null) {
                recordWarning("事件「${event.reason}」：目标 ${event.targetId} 缺失，动作 ${event.action} 未生效")
                appliedEvents.add(key)
                return@forEachIndexed
            }

            armies = armies.map { piece ->
                if (piece.id != event.targetId) return@map piece
                when (event.action) {
                    EventAction.MORALE_CHANGE ->
                        piece.withMorale(Morale.fromLabel(event.value))
                    EventAction.SUPPLY_CUT ->
                        // 断粮口径对齐活栈 / 并行栈：清补给，不降士气；已溃散棋子不重复结算。
                        if (piece.morale == Morale.ROUT) piece else piece.withSupply(0)
                    EventAction.SURRENDER -> {
                        routStartMs[piece.id] = now
                        piece.withMorale(Morale.ROUT)
                    }

                    // ── 本批补接：状态位已在 ArmyPiece 上，渲染层已消费 ──
                    EventAction.SET_INTENT -> {
                        val intent = Intent.fromLabelOrNull(event.value)
                        if (intent == null) {
                            recordWarning(
                                "事件「${event.reason}」：SET_INTENT 意图标签非法" +
                                    "（value=${event.value}），目标 ${piece.id} 未生效",
                            )
                            piece
                        } else {
                            piece.withIntent(intent)
                        }
                    }
                    EventAction.SET_TARGET -> {
                        val targetPoint = parseTargetPoint(event.value)
                        if (targetPoint == null) {
                            recordWarning(
                                "事件「${event.reason}」：SET_TARGET 坐标缺失或格式非法" +
                                    "（value=${event.value}），目标 ${piece.id} 未生效",
                            )
                            piece
                        } else {
                            piece.withTarget(targetPoint.first, targetPoint.second)
                        }
                    }
                    EventAction.STRENGTH_CHANGE -> {
                        val delta = event.value?.toIntOrNull()
                        if (delta == null) {
                            recordWarning(
                                "事件「${event.reason}」：STRENGTH_CHANGE 兵力增减量非法" +
                                    "（value=${event.value}），目标 ${piece.id} 未生效",
                            )
                            piece
                        } else {
                            piece.withStrengthDelta(delta)
                        }
                    }
                    EventAction.AMBUSH -> piece.withIntent(Intent.AMBUSH)
                    EventAction.FLANK -> piece.withIntent(Intent.FLANK)
                    EventAction.RETREAT -> piece.withIntent(Intent.RETREAT)

                    // ── 显式拒绝：回放模型缺 alive / surrendered / exposed 位，写明理由不静默 ──
                    EventAction.DEFEND_FALL -> rejectMissingField(
                        piece, event, "模型缺 alive 出局位：城破殉国的阵亡 / 出局态在回放棋子上无法落地",
                    )
                    EventAction.CAPTURE -> rejectMissingField(
                        piece, event, "模型缺 surrendered 被俘位：被俘收编态在回放棋子上无法落地",
                    )
                    EventAction.ANNIHILATE -> rejectMissingField(
                        piece, event, "模型缺 alive 出局位：全军覆没的阵亡态在回放棋子上无法落地",
                    )
                    EventAction.FEIGNED_RETREAT -> rejectMissingField(
                        piece, event, "模型缺 exposed 暴露位：佯退诱敌的位置暴露态在回放棋子上无法落地",
                    )

                    else -> {
                        // 兜底：部分缺失动作（ROUT / FIRE_ATTACK / SIEGE_ENGINE / BREAKTHROUGH /
                        // NAVAL_ROUT / ESCAPE）与枚举后续扩充、数据管道灌入的新动作，一律在此
                        // 显式拒绝 + 告警，不许静默空转。
                        recordWarning(
                            "事件「${event.reason}」：回放栈暂不支持动作 ${event.action}（目标 ${piece.id}），未生效",
                        )
                        piece
                    }
                }
            }
            appliedEvents.add(key)
        }
    }

    /** 条件触发事件（如兵力低于阈值）：本版本种子无兵力衰减，兜底为不触发。 */
    private fun conditionTriggered(event: BattleEvent): Boolean = false

    /**
     * 解析 SET_TARGET 的 value 坐标串，格式 "lat,lng"；非法 / 缺项返回 null，由调用方记告警。
     * 回放事件模型无独立 targetPos 字段（对照活栈 EventDef.targetPos），坐标暂走 value 串承载。
     */
    private fun parseTargetPoint(value: String?): Pair<Double, Double>? {
        val parts = value?.split(",")?.map { it.trim() } ?: return null
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        return lat to lng
    }

    /** 缺字段动作的显式拒绝：登记告警写明缺哪个状态位，棋子原样返回、状态不变。 */
    private fun rejectMissingField(piece: ArmyPiece, event: BattleEvent, missing: String): ArmyPiece {
        recordWarning(
            "事件「${event.reason}」：动作 ${event.action} 回放栈显式拒绝——$missing（目标 ${piece.id}），未生效",
        )
        return piece
    }

    // ══════════════════════════ 交战 / 遭遇检测 ══════════════════════════

    private fun detectClashes(now: Long) {
        val own = armiesByRole(SideRole.OWN)
        val enemy = armiesByRole(SideRole.ENEMY)
        val currentAdjacent = mutableSetOf<String>()
        own.forEach { a ->
            enemy.forEach { b ->
                val dist = LatLng(a.lat, a.lng).distanceKm(LatLng(b.lat, b.lng))
                val pairKey = "${a.id}~${b.id}"
                if (dist <= clashThresholdKm) {
                    currentAdjacent.add(pairKey)
                    // 沿边触发：上一帧不相邻、这一帧才贴近才开战；同一场交战未结束前不重复生成。
                    if (!adjacentClashPairs.contains(pairKey) && !hasActiveClash(now)) {
                        val mid = LatLng((a.lat + b.lat) / 2, (a.lng + b.lng) / 2)
                        clashes.add(
                            ClashEffect(
                                id = "clash-${a.id}-${b.id}",
                                point = mid,
                                startedAtMs = now,
                                groundHeight = ReplayGround.heightAt(elevation, mid.lat, mid.lng),
                            ),
                        )
                    }
                }
            }
        }
        adjacentClashPairs.clear()
        adjacentClashPairs.addAll(currentAdjacent)

        // 箭头相遇 → 300ms 朱砂遭遇光环。
        val arrows = arrows()
        arrows.forEachIndexed { i, a ->
            arrows.drop(i + 1).forEach { b ->
                if (a.role != b.role && a.meets(b, clashThresholdKm)) {
                    val mid = a.points.last()
                    if (encounters.none { it.active(now) && it.point.distanceKm(mid) <= clashThresholdKm }) {
                        encounters.add(
                            EncounterRing(
                                id = "enc-${a.armyId}-${b.armyId}",
                                point = mid,
                                startedAtMs = now,
                                groundHeight = ReplayGround.heightAt(elevation, mid.lat, mid.lng),
                            ),
                        )
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

    /** 棋子视觉状态列表；溃散棋子按已持续毫秒决定碎片 / 残影圈；基座高度按注入高程贴地采样。 */
    fun visuals(): List<PieceVisual> = armies.map { piece ->
        val routElapsed = routStartMs[piece.id]?.let { clock() - it }
        PieceVisual.of(
            piece = piece,
            role = roleOf(piece),
            selected = piece.id == selectedPieceId,
            routElapsedMs = routElapsed,
            elevation = elevation,
        )
    }

    /** 行军箭头列表；设伏棋子不出兵锋；箭身节点按注入高程贴地采样。 */
    fun arrows(): List<BattleArrow> = armies.mapNotNull { piece ->
        BattleArrow.fromPiece(piece, roleOf(piece) ?: SideRole.OWN, elevation)
    }

    /** 势力 / 战线多边形列表；无多边形数据时为空，渲染 pass 跳过。 */
    fun frontSectors(): List<ReplayFrontSector> = campaign.sectors

    /** 多边形逐顶点贴地采样结果（多边形 → 地表点序列），供渲染 pass 拼贴起伏色块。 */
    fun sectorSurface(): List<Pair<ReplayFrontSector, List<GroundPoint>>> =
        campaign.sectors.map { it to it.sampleSurface(elevation) }

    companion object {
        /** 交战相邻阈值：敌我棋子相距 10 公里内判定相邻（地形视野内的近身距离）。 */
        const val CLASH_THRESHOLD_KM: Double = 10.0

        /** 告警台账上限：超出截断最旧条目、保最新 N 条，避免长战役只增不减。 */
        const val MAX_WARNINGS: Int = 20
    }
}
