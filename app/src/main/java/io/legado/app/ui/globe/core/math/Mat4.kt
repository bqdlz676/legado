package io.legado.app.ui.globe.core.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 列主序 4×4 矩阵（纯 JVM），布局与 OpenGL ES `glUniformMatrix4fv` 一致。
 * 只实现地球仪用到的：透视投影、观察矩阵、模型旋转、矩阵相乘。
 */
class Mat4 {

    val m: FloatArray = FloatArray(16)

    companion object {
        fun identity(): Mat4 = Mat4().apply {
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
        }

        /** 透视投影（fovY 弧度，aspect 宽高比，near/far）。 */
        fun perspective(fovY: Double, aspect: Double, near: Float, far: Float): Mat4 {
            val out = Mat4()
            val f = 1.0 / tan(fovY / 2.0)
            val nf = 1.0 / (near - far)
            out.m[0] = (f / aspect).toFloat()
            out.m[5] = f.toFloat()
            out.m[10] = ((far + near) * nf).toFloat()
            out.m[11] = -1f
            out.m[14] = (2f * far * near * nf).toFloat()
            return out
        }

        /** 观察矩阵：eye 看向 target，up 为上方向。 */
        fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Mat4 {
            val out = Mat4()
            val z = (eye - target).normalize()
            val x = up.cross(z).normalize()
            val y = z.cross(x)
            out.m[0] = x.x.toFloat(); out.m[4] = x.y.toFloat(); out.m[8] = x.z.toFloat()
            out.m[1] = y.x.toFloat(); out.m[5] = y.y.toFloat(); out.m[9] = y.z.toFloat()
            out.m[2] = z.x.toFloat(); out.m[6] = z.y.toFloat(); out.m[10] = z.z.toFloat()
            out.m[12] = -x.dot(eye).toFloat()
            out.m[13] = -y.dot(eye).toFloat()
            out.m[14] = -z.dot(eye).toFloat()
            out.m[15] = 1f
            return out
        }

        fun rotationX(rad: Double): Mat4 = Mat4().apply {
            m[0] = 1f
            m[5] = cos(rad).toFloat(); m[6] = sin(rad).toFloat()
            m[9] = -sin(rad).toFloat(); m[10] = cos(rad).toFloat()
            m[15] = 1f
        }

        fun rotationY(rad: Double): Mat4 = Mat4().apply {
            m[0] = cos(rad).toFloat(); m[2] = -sin(rad).toFloat()
            m[5] = 1f
            m[8] = sin(rad).toFloat(); m[10] = cos(rad).toFloat()
            m[15] = 1f
        }

        fun rotationZ(rad: Double): Mat4 = Mat4().apply {
            m[0] = cos(rad).toFloat(); m[1] = sin(rad).toFloat()
            m[4] = -sin(rad).toFloat(); m[5] = cos(rad).toFloat()
            m[10] = 1f; m[15] = 1f
        }
    }

    /** 用本矩阵变换齐次点（w=1），返回世界空间坐标。 */
    fun transformPoint(v: Vec3): Vec3 {
        val x = m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12]
        val y = m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13]
        val z = m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14]
        return Vec3(x, y, z)
    }

    /** this = a × b（列主序）。 */
    fun multiply(a: Mat4, b: Mat4): Mat4 {
        val aa = a.m; val bb = b.m; val o = m
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                o[col * 4 + row] = (
                    aa[row] * bb[col * 4] +
                        aa[4 + row] * bb[col * 4 + 1] +
                        aa[8 + row] * bb[col * 4 + 2] +
                        aa[12 + row] * bb[col * 4 + 3]
                    )
            }
        }
        return this
    }
}
