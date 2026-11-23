package io.legado.app.ui.globe.core.math

import kotlin.math.sqrt

/**
 * 三维向量（纯 JVM，无 Android / GL 依赖）。
 * 渲染层把它拷进 float[] 上传 GPU；纯逻辑层直接用它做几何与光照计算。
 */
data class Vec3(val x: Double, val y: Double, val z: Double) {

    constructor(x: Float, y: Float, z: Float) : this(x.toDouble(), y.toDouble(), z.toDouble())

    companion object {
        val ZERO: Vec3 = Vec3(0.0, 0.0, 0.0)
        val UNIT_X: Vec3 = Vec3(1.0, 0.0, 0.0)
        val UNIT_Y: Vec3 = Vec3(0.0, 1.0, 0.0)
        val UNIT_Z: Vec3 = Vec3(0.0, 0.0, 1.0)
    }

    val length: Double get() = sqrt(x * x + y * y + z * z)

    fun lengthSquared(): Double = x * x + y * y + z * z

    operator fun plus(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)

    operator fun minus(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)

    operator fun times(s: Number): Vec3 = Vec3(x * s.toDouble(), y * s.toDouble(), z * s.toDouble())

    operator fun div(s: Double): Vec3 = Vec3(x / s, y / s, z / s)

    fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 =
        Vec3(
            y * o.z - z * o.y,
            z * o.x - x * o.z,
            x * o.y - y * o.x,
        )

    /** 单位化；零向量返回零向量，不抛异常（边界兜底）。 */
    fun normalize(): Vec3 {
        val l = length
        return if (l < 1e-12) ZERO else Vec3(x / l, y / l, z / l)
    }

    fun lerp(o: Vec3, t: Double): Vec3 =
        Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)

    fun toFloatArray(): FloatArray = floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())

    override fun toString(): String = "Vec3(%.3f, %.3f, %.3f)".format(x, y, z)
}
