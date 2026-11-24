package io.legado.app.ui.motion.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-15 动效规格单测：七组 spring 可解析、reduce-motion 降级、未知名回退、大小写不敏感、
 * 时长三档取值。纯 JVM，不触 Android 动画类。
 */
class MotionSpecTest {

    @Test
    fun `allSevenSpecsAreResolvable`() {
        val names = listOf("PRESS", "SHEET", "BUBBLE", "PAGE", "PANEL", "TOAST", "MAP_RISE")
        for (name in names) {
            val spec = springSpecFor(name, reduceMotion = false)
            assertTrue("stiffness positive for $name", spec.stiffness > 0f)
            assertTrue("damping positive for $name", spec.dampingRatio > 0f)
            assertTrue("label not blank for $name", spec.label.isNotBlank())
        }
    }

    @Test
    fun `reduceMotionDampingRatioIsOne`() {
        val spec = springSpecFor("BUBBLE", reduceMotion = true)
        assertEquals(1.0f, spec.dampingRatio, 0f)
        assertEquals(1000f, spec.stiffness, 0f)
    }

    @Test
    fun `reduceMotionDurationIsZero`() {
        assertEquals(0, motionDurationMs("PAGE", reduceMotion = true))
        assertEquals(0, motionDurationMs("SHEET", reduceMotion = true))
        assertEquals(0, motionDurationMs("MAP_RISE", reduceMotion = true))
    }

    @Test
    fun `unknownMotionFallsBackToPressWithoutThrowing`() {
        val spec = springSpecFor("no_such_motion", reduceMotion = false)
        assertEquals(MotionSpec.PRESS, spec)
        assertEquals(MotionSpec.PRESS.stiffness, spec.stiffness, 0f)
        assertEquals(120, motionDurationMs("no_such_motion", reduceMotion = false))
    }

    @Test
    fun `motionNameLookupIsCaseInsensitive`() {
        assertEquals(MotionSpec.BUBBLE, springSpecFor("bubble", reduceMotion = false))
        assertEquals(MotionSpec.MAP_RISE, springSpecFor("Map_Rise", reduceMotion = false))
        assertEquals(MotionSpec.PRESS, springSpecFor("press", reduceMotion = false))
    }

    @Test
    fun `pressStiffnessMatchesConfig`() {
        val spec = springSpecFor("PRESS", reduceMotion = false)
        assertEquals(MotionSpec.PRESS.stiffness, spec.stiffness, 0f)
        assertEquals(MotionSpec.PRESS.dampingRatio, spec.dampingRatio, 0f)
    }

    @Test
    fun `mapRiseSpecExists`() {
        val spec = springSpecFor("MAP_RISE", reduceMotion = false)
        assertEquals(MotionSpec.MAP_RISE, spec)
        assertTrue(spec.stiffness > 0f)
        assertTrue(spec.dampingRatio > 0f)
        assertEquals("map_rise", spec.label)
    }

    @Test
    fun `durationThreeTiersResolveCorrectly`() {
        assertEquals(120, motionDurationMs("PRESS", reduceMotion = false))
        assertEquals(250, motionDurationMs("SHEET", reduceMotion = false))
        assertEquals(250, motionDurationMs("BUBBLE", reduceMotion = false))
        assertEquals(350, motionDurationMs("PAGE", reduceMotion = false))
        assertEquals(350, motionDurationMs("PANEL", reduceMotion = false))
        assertEquals(350, motionDurationMs("MAP_RISE", reduceMotion = false))
    }
}
