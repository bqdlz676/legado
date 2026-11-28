package io.legado.app.ui.map.globe.astro.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 星空缩放「连续曲线、非档位制」契约机检（本单只加测试，不改任何生产代码）。
 *
 * 覆盖三条纯函数在 [0,1] / 锚点区间上的连续性口径：
 *  - [SkyParams.magLimitFromBlend]：blend 0 → 6.5 等实体层，blend 1 → 11.5 等深度上限，
 *    单条线性曲线，等步长采样无跳变；
 *  - [SkyParams.skyBlendFromDiameter]：地球视觉直径 400px → 40px 连续映射到 blend 0 → 1，
 *    端点外侧钳平不震荡；
 *  - [SkyParams.starDiameterPx]：星点大小按星等锚点插值、单调不增——这是「星等 → 尺寸」
 *    契约，不是缩放档位，本文件只锁它不出现反向。
 *
 * 〔为什么单独成文件〕既有 [SkyContractTest] / SkyBlendCurveTest 覆盖常量值与主曲线；本文件
 *  专门锁「防将来被改回档位制」的判据：端点精确引用契约常量、相邻步长有上限、越界回退确定、
 *  区间不反转。纯 JVM，不依赖 Android / Compose / 资产。
 */
class SkyZoomContinuityTest {

    private companion object {
        /** 端点 / 锚点相等判定容差：线性插值在样点上有 1 ULP 级误差，1e-6 足够盖掉。 */
        const val EPS = 1e-6f

        /** 单调方向判定的回退容差（比等值差小两个量级，真实回退即红）。 */
        const val MONO_EPS = 1e-7f

        /** 连续性采样段数：段数越密，档边单步吞掉整段等值差越容易被步长上限抓到。 */
        const val SAMPLE_STEPS = 40
    }

    // ─────────────── magLimitFromBlend：blend → 星等上限 ───────────────

    /**
     * 端点精确性：blend 0 / 1 必须正好等于契约常量，不写字面量副本。
     *
     * 改坏推演：若有人把区间改成 6.5 / 9.0 / 11.5 三档枚举阶梯，或挪动端点锚，
     * 下两行分别取不到 [SkyParams.MAG_BRIGHT_ENTITY] / [SkyParams.MAG_DEPTH_LIMIT]，
     * assertEquals 在对应行红。
     */
    @Test
    fun magLimit_endpointEqualsContractConstants() {
        assertEquals(SkyParams.MAG_BRIGHT_ENTITY, SkyParams.magLimitFromBlend(0f), EPS)
        assertEquals(SkyParams.MAG_DEPTH_LIMIT, SkyParams.magLimitFromBlend(1f), EPS)
    }

    /**
     * 单调不减 + 相邻步长不超过 1 倍等值差，证明是单曲线而非档位切换。
     *
     * 改坏推演：阶梯制在档边会让单步吞下整段等值差（例如一步从 6.5 跳到 9.0），
     * 「无跳变」断言在那个采样点超上限红；曲线回退（blend 增大上限反而变小）则在
     * 「单调不减」断言红。
     */
    @Test
    fun magLimit_monotonicNonDecreasingWithNoJump() {
        val expectedStep =
            (SkyParams.MAG_DEPTH_LIMIT - SkyParams.MAG_BRIGHT_ENTITY) / SAMPLE_STEPS
        var prev = SkyParams.magLimitFromBlend(0f)
        for (i in 1..SAMPLE_STEPS) {
            val blend = i / SAMPLE_STEPS.toFloat()
            val limit = SkyParams.magLimitFromBlend(blend)

            // 单调不减：回退即红。
            assertTrue("blend=$blend 星等上限不得回退", limit >= prev - MONO_EPS)

            val delta = limit - prev
            // 无跳变：相邻步长不得超过 1 个等值差；档边单步吞整段差值时此处超上限红。
            assertTrue(
                "blend=$blend 单步增量 $delta 超出等值差 $expectedStep（档位跳变）",
                delta <= expectedStep + EPS,
            )
            prev = limit
        }
    }

    /**
     * 越界 / 非有限输入必须有确定回退（按现有实现写实，不假设新口径）。
     *
     * 现有实现（SkyParams.kt:263-266）：非有限输入按 blend=0 回落实体层；有限输入先
     * coerceIn(0,1) 再线性映射。改坏推演：若越界未钳制，blend=2 会推出 >11.5 的上限、
     * blend=-1 会推出 <6.5 的下限；非有限输入若抛异常或返回 NaN，对应断言行红。
     */
    @Test
    fun magLimit_outOfFiniteRangeFallsBackDeterministically() {
        assertEquals(SkyParams.MAG_BRIGHT_ENTITY, SkyParams.magLimitFromBlend(-1f), EPS)
        assertEquals(SkyParams.MAG_DEPTH_LIMIT, SkyParams.magLimitFromBlend(2f), EPS)
        assertEquals(SkyParams.MAG_BRIGHT_ENTITY, SkyParams.magLimitFromBlend(Float.NaN), EPS)
        assertEquals(
            SkyParams.MAG_BRIGHT_ENTITY,
            SkyParams.magLimitFromBlend(Float.POSITIVE_INFINITY),
            EPS,
        )
        assertEquals(
            SkyParams.MAG_BRIGHT_ENTITY,
            SkyParams.magLimitFromBlend(Float.NEGATIVE_INFINITY),
            EPS,
        )
    }

    // ─────────────── skyBlendFromDiameter：地球直径 px → blend ───────────────

    /**
     * 端点与中点闭合：40px 锚 blend=1、400px 全尺寸 blend=0、中点对称 0.5。
     *
     * 改坏推演：端点若被改成「到 40px 才切星空」的硬阈值，40px 一侧不会连续落到 1；
     * 映射若不再对称，220px 中点取不到 0.5，对应 assertEquals 红。
     */
    @Test
    fun blendFromDiameter_endpointsAndMidpointClose() {
        assertEquals(
            1f,
            SkyParams.skyBlendFromDiameter(SkyParams.SKY_ENTER_EARTH_DIAMETER_PX),
            EPS,
        )
        assertEquals(
            0f,
            SkyParams.skyBlendFromDiameter(SkyParams.EARTH_DIAMETER_PX_FULL),
            EPS,
        )
        val mid = (SkyParams.SKY_ENTER_EARTH_DIAMETER_PX +
            SkyParams.EARTH_DIAMETER_PX_FULL) / 2f
        assertEquals(0.5f, SkyParams.skyBlendFromDiameter(mid), EPS)
    }

    /**
     * 直径 40px → 400px 区间内 blend 单调不增。
     *
     * 改坏推演：直径变大（地球缩小）blend 反而增大即映射方向写反；分段曲线在段间
     * 出现反向凸起，「不增」断言在对应采样点红。
     */
    @Test
    fun blendFromDiameter_monotonicNonIncreasingAcrossSpan() {
        val lo = SkyParams.SKY_ENTER_EARTH_DIAMETER_PX
        val hi = SkyParams.EARTH_DIAMETER_PX_FULL
        var prev = SkyParams.skyBlendFromDiameter(lo)
        for (i in 1..SAMPLE_STEPS) {
            val px = lo + (hi - lo) * i / SAMPLE_STEPS.toFloat()
            val blend = SkyParams.skyBlendFromDiameter(px)
            assertTrue("px=$px blend 不得反向增大", blend <= prev + MONO_EPS)
            prev = blend
        }
    }

    /**
     * 越过端点不震荡：40px 以内整片钳在 1，400px 以外整片钳在 0，非有限输入回落 0。
     *
     * 改坏推演：外侧若按开关式来回切换，小直径侧会出现 blend<1 的凹陷、大直径侧出现
     * blend>0 的凸起；非有限输入若抛异常或返回 NaN，对应断言行红。
     */
    @Test
    fun blendFromDiameter_plateauOutsideEndpointsNoOscillation() {
        for (px in floatArrayOf(
            0f,
            10f,
            20f,
            SkyParams.SKY_ENTER_EARTH_DIAMETER_PX,
        )) {
            assertEquals("px=$px 应钳在 blend=1", 1f, SkyParams.skyBlendFromDiameter(px), EPS)
        }
        for (px in floatArrayOf(
            SkyParams.EARTH_DIAMETER_PX_FULL,
            500f,
            1000f,
            2000f,
        )) {
            assertEquals("px=$px 应钳在 blend=0", 0f, SkyParams.skyBlendFromDiameter(px), EPS)
        }
        assertEquals(0f, SkyParams.skyBlendFromDiameter(Float.NaN), EPS)
        assertEquals(0f, SkyParams.skyBlendFromDiameter(Float.POSITIVE_INFINITY), EPS)
    }

    // ─────────────── starDiameterPx：星等 → 星点直径（尺寸契约，非缩放档位） ───────────────

    /**
     * 六档锚点逐条命中规格像素：-1.5/0/2/4.5/6.5/7 → 9/5/3/1.5/1/0.8。
     *
     * 改坏推演：锚点表被挪动或插值分段改口径，对应 assertEquals 在该星等行红；
     * 7 等暗于 6.5 进金字塔层，直径兜底 0.8px，不再变小。
     */
    @Test
    fun starDiameter_anchorMagnitudesHitSpecPixels() {
        assertEquals(9f, SkyParams.starDiameterPx(-1.5f), EPS)
        assertEquals(5f, SkyParams.starDiameterPx(0f), EPS)
        assertEquals(3f, SkyParams.starDiameterPx(2f), EPS)
        assertEquals(1.5f, SkyParams.starDiameterPx(4.5f), EPS)
        assertEquals(1.0f, SkyParams.starDiameterPx(6.5f), EPS)
        assertEquals(0.8f, SkyParams.starDiameterPx(7f), EPS)
    }

    /**
     * 星等采样序列上直径单调不增（星越暗点越小），相邻采样不出现反向。
     *
     * 改坏推演：锚点间插值方向写反、或暗星段被错误放大，直径在某个采样点反增，
     * 「不增」断言在该行红。
     */
    @Test
    fun starDiameter_nonIncreasingAcrossMagnitudes() {
        val samples = floatArrayOf(-3f, -1.5f, 0f, 2f, 4.5f, 6.5f, 7f, 11.5f)
        var prev = SkyParams.starDiameterPx(samples[0])
        for (i in 1 until samples.size) {
            val size = SkyParams.starDiameterPx(samples[i])
            assertTrue("mag=${samples[i]} 直径不得反增", size <= prev + EPS)
            prev = size
        }
    }
}
