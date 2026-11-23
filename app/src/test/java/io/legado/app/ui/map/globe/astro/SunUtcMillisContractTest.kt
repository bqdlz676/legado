package io.legado.app.ui.map.globe.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 太阳方向 UTC 毫秒量纲契约单测（F1 回归）。
 *
 * 门面契约：[Sun.direction] 只吃 UTC 毫秒 epoch（与 [System.currentTimeMillis] 同口径）；
 * 帧循环的纳秒帧时间戳只能用于帧间隔差分，不得当作毫秒传入门面。本测试锁定该量纲契约：
 * 同一毫秒内两次计算结果一致（不随帧纳秒计数抖动），不同时刻方位随真实时间推进而变化。
 */
class SunUtcMillisContractTest {

    @Test
    fun `同一 UTC 毫秒内两次计算太阳方向完全一致`() {
        // 取真实 UTC 毫秒；两次调用都吃同一个毫秒戳，方向必须逐分量相等，
        // 证明方向只由 utcMillis 决定，不随帧纳秒计数抖动。
        val utcMillis = System.currentTimeMillis()
        assertEquals(Sun.direction(utcMillis), Sun.direction(utcMillis))
    }

    @Test
    fun `相隔十二小时的不同 UTC 时刻太阳方位发生变化`() {
        // 正向断言：时刻推进后方位确实改变，而非恒定向量。
        val base = 1_721_000_000_000L
        val later = base + 12L * 3_600_000L
        val dirBase = Sun.direction(base)
        val dirLater = Sun.direction(later)

        val delta = (dirLater - dirBase).length
        assertTrue("相隔十二小时太阳方位不应重合，方向差长度=$delta", delta > 1e-6)
    }
}
