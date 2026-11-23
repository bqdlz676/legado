package io.legado.app.ui.map.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 沙盘场景单测（切片-06）：年代过滤、配额上限、城池级别、河流选版、相机钳制、
 * 交互状态与信息卡超时。纯 JVM，不引 Android / Compose。
 */
class SandboxSceneTest {

    private fun buildScene(year: Int? = null): SandboxScene {
        val scene = SandboxScene(
            powers = PowerLayer(SeedSandbox.powers),
            cities = SeedSandbox.cities,
            rivers = SeedSandbox.rivers,
            sites = SeedSandbox.sites,
            chokes = SeedSandbox.chokes,
            trails = emptyList(),
        )
        scene.year = year
        return scene
    }

    // ── 势力图层 ──

    @Test
    fun `势力按年代显隐，超界势力不渲染`() {
        val spring = buildScene(-700)
        assertTrue(spring.visiblePowers().any { it.id == "jin" })
        val late = buildScene(-300)
        assertFalse(late.visiblePowers().any { it.id == "jin" })
    }

    @Test
    fun `势力多边形判点射线法命中与落空`() {
        val jin = SeedSandbox.powers.first { it.id == "jin" }
        // 晋南一带在晋多边形内；胶东半岛在其外。
        assertTrue(jin.contains(35.60, 112.00))
        assertFalse(jin.contains(36.80, 119.53))
    }

    @Test
    fun `势力数量不超过 12 个上限`() {
        val scene = buildScene()
        assertTrue(scene.visiblePowers().size <= SandboxConfig.MAX_POWER_SECTORS)
    }

    @Test
    fun `中立势力无填充只画边界`() {
        val zhou = SeedSandbox.powers.first { it.id == "zhou" }
        assertEquals(SandboxConfig.NEUTRAL_FILL_ALPHA, zhou.fillAlpha(), 0f)
    }

    // ── 城池图层 ──

    @Test
    fun `三级城池光柱高度对齐规格`() {
        val capital = SeedSandbox.cities.first { it.tier == CityTier.CAPITAL }
        val county = SeedSandbox.cities.first { it.tier == CityTier.COUNTY }
        val pass = SeedSandbox.cities.first { it.tier == CityTier.PASS_FERRY }
        assertEquals(SandboxConfig.CAPITAL_PILLAR_HEIGHT, capital.pillarHeight(), 1e-9)
        assertEquals(SandboxConfig.COUNTY_PILLAR_HEIGHT, county.pillarHeight(), 1e-9)
        assertEquals(SandboxConfig.PASS_PILLAR_HEIGHT, pass.pillarHeight(), 1e-9)
        assertEquals(0, pass.particleCount())
    }

    @Test
    fun `都城光柱最多 8 根其余降级亮点`() {
        val scene = buildScene()
        val pillars = scene.pillarCities()
        assertTrue(pillars.size <= SandboxConfig.MAX_CAPITAL_PILLARS)
        assertEquals(pillars.size, scene.cityRender().count { it.pillar })
    }

    @Test
    fun `光柱粒子总数不超过 96 粒预算`() {
        val scene = buildScene()
        val total = scene.pillarCities().sumOf { scene.budgetedParticles(it) }
        assertTrue(total <= SandboxConfig.MAX_PILLAR_PARTICLES)
    }

    @Test
    fun `占领城池光柱转朱砂且粒子下落`() {
        val city = CityNode("xiyang", "夕阳", 34.0, 110.0, CityTier.CAPITAL, occupied = true)
        assertEquals(SandboxTokens.textAccent, city.pillarColor())
        assertTrue(city.particlesFall())
    }

    @Test
    fun `标签全部合进一张 atlas`() {
        val scene = buildScene()
        val atlas = scene.labelAtlas()
        assertEquals(scene.cityRender().size, atlas.size)
        assertEquals(SandboxConfig.LABEL_ATLAS_COUNT, 1)
    }

    // ── 河流图层 ──

    @Test
    fun `同名黄河按年代选改道版本`() {
        val scene = buildScene(-700)
        val preqin = scene.visibleRivers().first { it.name == "黄河" }
        assertEquals("唐前", preqin.version)

        val tang = buildScene(700).visibleRivers().first { it.name == "黄河" }
        assertEquals("唐宋", tang.version)

        val qing = buildScene(1900).visibleRivers().first { it.name == "黄河" }
        assertEquals("明清", qing.version)
    }

    @Test
    fun `同时可见河流不超过 8 条`() {
        val scene = buildScene()
        assertTrue(scene.visibleRivers().size <= SandboxConfig.MAX_RIVERS)
    }

    @Test
    fun `河道样条采样点数不少于控制点`() {
        val river = SeedSandbox.rivers.first { it.id == "changjiang" }
        val sampled = river.sampledPath()
        assertTrue(sampled.size >= river.pts.size)
    }

    // ── 性能核算 ──

    @Test
    fun `draw call 估算不超过 20 上限`() {
        val scene = buildScene()
        assertFalse(scene.overDrawCallBudget())
        assertTrue(scene.estimateDrawCalls() <= SandboxConfig.MAX_DRAW_CALLS)
    }

    // ── 相机 ──

    @Test
    fun `倾角钳制在 55 度内`() {
        val rig = CameraRig(LatLng(35.6, 112.0))
        rig.rotateBy(0.0, 90.0)
        assertTrue(rig.tiltDeg <= SandboxConfig.TILT_MAX_DEG)
        rig.rotateBy(0.0, -90.0)
        assertTrue(rig.tiltDeg >= 0.0)
    }

    @Test
    fun `缩放距离钳制在 90 到 560 单位`() {
        val rig = CameraRig(LatLng(35.6, 112.0))
        rig.pinchZoom(10.0)
        assertTrue(rig.distance >= SandboxConfig.CAMERA_MIN_DISTANCE)
        rig.pinchZoom(0.01)
        assertTrue(rig.distance <= SandboxConfig.CAMERA_MAX_DISTANCE)
    }

    @Test
    fun `双击空地重置视角，双击棋子飞 45 度俯瞰`() {
        val rig = CameraRig(LatLng(35.6, 112.0))
        rig.rotateBy(40.0, 20.0)
        rig.pinchZoom(0.5)
        rig.reset()
        assertEquals(CameraRig.DEFAULT_TILT_DEG, rig.tiltDeg, 1e-9)
        val target = LatLng(34.34, 108.94)
        rig.flyOver(target)
        assertEquals(SandboxConfig.PIECE_FLYOVER_TILT_DEG, rig.tiltDeg, 1e-9)
        assertEquals(target, rig.focus)
    }

    @Test
    fun `双指平移期间锁定旋转`() {
        val rig = CameraRig(LatLng(35.6, 112.0))
        rig.panBy(10.0, 5.0)
        assertTrue(rig.rotationLocked)
        val before = rig.yawDeg
        rig.rotateBy(30.0, 10.0)
        assertEquals(before, rig.yawDeg, 1e-9)
        rig.endPan()
        assertFalse(rig.rotationLocked)
    }

    // ── 交互 ──

    @Test
    fun `选中棋子后下令生成预演虚线，点空地取消`() {
        val interaction = SandboxInteraction()
        interaction.selectPiece("baiqi")
        interaction.issueOrder(LatLng(35.85, 112.85), OrderIntent.FLANK)
        assertEquals("baiqi", interaction.orderPreview?.pieceId)
        interaction.clearSelection()
        assertFalse(interaction.selectedPieceId != null)
    }

    @Test
    fun `信息卡 3 秒后自动消失`() {
        var now = 0L
        val interaction = SandboxInteraction(clock = { now })
        interaction.showInfo(InfoKind.ARROW, "行军", "里程 120 里 · 3 日")
        assertTrue(interaction.update() != null)
        now = SandboxConfig.INFO_CARD_TTL_MS
        assertFalse(interaction.update() != null)
    }

    @Test
    fun `双棋子测距返回公里与行军天数`() {
        val interaction = SandboxInteraction()
        val result = interaction.measure(LatLng(34.34, 108.94), LatLng(34.62, 112.45))
        assertTrue(result.km > 0.0)
        assertTrue(result.marchDays >= 1)
    }

    @Test
    fun `剧场模式锁定选中与信息卡，退出后恢复`() {
        val interaction = SandboxInteraction()
        interaction.selectPiece("baiqi")
        interaction.enterTheater()
        assertTrue(interaction.theaterMode)
        interaction.selectPiece("wanghe")
        assertFalse(interaction.selectedPieceId == "wanghe")
        interaction.exitTheater()
        assertFalse(interaction.theaterMode)
    }

    @Test
    fun `回合跳转钳制在 0 到总回合，阶段跳转落到首回合`() {
        val interaction = SandboxInteraction()
        interaction.seek(99.0, 30)
        assertEquals(30.0, interaction.turn, 1e-9)
        interaction.seek(-5.0, 30)
        assertEquals(0.0, interaction.turn, 1e-9)
        interaction.jumpPhase(8, "出击", 30)
        assertEquals(8.0, interaction.turn, 1e-9)
        assertEquals("出击", interaction.currentPhaseLabel)
    }

    @Test
    fun `尾迹粒子 8 到 12 粒且浓度向尾部递减`() {
        val trail = MarchTrail(
            "trail1",
            listOf(LatLng(35.0, 112.0), LatLng(35.1, 112.1)),
            10,
        )
        assertTrue(trail.particleCount in
            SandboxConfig.TRAIL_PARTICLES_MIN..SandboxConfig.TRAIL_PARTICLES_MAX)
        assertTrue(trail.particleT(0) > trail.particleT(trail.particleCount - 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `尾迹粒子数越界直接抛错兜底`() {
        MarchTrail("bad-trail", emptyList(), 5)
    }

    @Test
    fun `无宽度系数时河宽取基准宽度`() {
        val river = SeedSandbox.rivers.first { it.id == "weishui" }
        assertEquals(river.valleyWidth, river.widthAt(0.5), 1e-9)
    }

    @Test
    fun `单步仅在暂停态可用`() {
        val interaction = SandboxInteraction()
        interaction.play()
        interaction.stepForward(30)
        assertEquals(0.0, interaction.turn, 1e-9)
        interaction.pause()
        interaction.stepForward(30)
        assertEquals(1.0, interaction.turn, 1e-9)
    }
}
