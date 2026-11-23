package io.legado.app.ui.globe.core.mesh

import io.legado.app.ui.globe.core.geo.Geo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 球面网格（纯 JVM，几何数据可直接上传 GL，也可在单测里校验）。
 *
 * 对应切片 20：`PlaneGeometry(SIZE, SIZE)` → `SphereGeometry(100, 128, 128)`。
 * 经纬网格在两极收束，经度 seam 处复制一列顶点保证 UV 闭合；
 * 从赤道到北极连续不中断。
 *
 * @param displace 沿法线方向的位移（世界单位），入参为纬度、经度、单位法线；默认 0（正球）。
 */
data class SphereMesh(
    val segments: Int,
    val rings: Int,
    val radius: Float,
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val lats: DoubleArray,
    val lngs: DoubleArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = lats.size

    companion object {

        fun build(
            level: LodLevel,
            radius: Float,
            displace: (lat: Double, lng: Double, nx: Double, ny: Double, nz: Double) -> Float =
                { _, _, _, _, _ -> 0f },
        ): SphereMesh = build(level.segments, level.rings, radius, displace)

        fun build(
            segments: Int,
            rings: Int,
            radius: Float,
            displace: (lat: Double, lng: Double, nx: Double, ny: Double, nz: Double) -> Float =
                { _, _, _, _, _ -> 0f },
        ): SphereMesh {
            require(segments > 2 && rings > 2) { "球面段数必须 > 2，got segments=$segments rings=$rings" }
            val cols = segments + 1
            val rows = rings + 1
            val n = rows * cols

            val positions = FloatArray(n * 3)
            val normals = FloatArray(n * 3)
            val uvs = FloatArray(n * 2)
            val lats = DoubleArray(n)
            val lngs = DoubleArray(n)

            var i = 0
            for (r in 0 until rows) {
                // phi: 0 = 北极，π = 南极
                val phi = PI * r / rings
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)
                val lat = 90.0 - phi * Geo.RAD2DEG
                for (s in 0 until cols) {
                    val theta = 2.0 * PI * s / segments
                    val nx = sinPhi * cos(theta)
                    val ny = cosPhi
                    val nz = sinPhi * sin(theta)
                    val lng = Geo.wrapLng(theta * Geo.RAD2DEG - 180.0)
                    val offset = displace(lat, lng, nx, ny, nz)
                    val rr = radius + offset

                    positions[i * 3] = (nx * rr).toFloat()
                    positions[i * 3 + 1] = (ny * rr).toFloat()
                    positions[i * 3 + 2] = (nz * rr).toFloat()
                    normals[i * 3] = nx.toFloat()
                    normals[i * 3 + 1] = ny.toFloat()
                    normals[i * 3 + 2] = nz.toFloat()
                    uvs[i * 2] = s.toFloat() / segments
                    uvs[i * 2 + 1] = r.toFloat() / rings
                    lats[i] = lat
                    lngs[i] = lng
                    i++
                }
            }

            val idx = ArrayList<Int>(rings * segments * 6)
            for (r in 0 until rings) {
                for (s in 0 until segments) {
                    val a = r * cols + s
                    val b = a + 1
                    val c = a + cols
                    val d = c + 1
                    // 逆时针朝外
                    idx.add(a); idx.add(c); idx.add(b)
                    idx.add(b); idx.add(c); idx.add(d)
                }
            }

            return SphereMesh(
                segments = segments,
                rings = rings,
                radius = radius,
                positions = positions,
                normals = normals,
                uvs = uvs,
                lats = lats,
                lngs = lngs,
                indices = idx.toIntArray(),
            )
        }
    }
}
