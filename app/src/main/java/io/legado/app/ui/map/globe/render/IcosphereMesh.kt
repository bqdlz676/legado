package io.legado.app.ui.map.globe.render

import io.legado.app.ui.map.globe.math.Vec3
import kotlin.math.sqrt

/**
 * 球面三角片（纯 JVM，渲染核心几何单元）。
 *
 * 为什么单独成类：软渲染管线按三角形逐个剔除 / 投影 / 光栅化，索引三元组比
 * 裸 IntArray 下标更不容易把面序写反；面序约定逆时针朝外（与 mesh/SphereGrid 一致）。
 */
data class Tri(val a: Int, val b: Int, val c: Int)

/**
 * 二十面体递归细分球面网格（纯 JVM，无 Android / GL 依赖）。
 *
 * 与 [io.legado.app.ui.map.globe.mesh.SphereGrid] 的分工：经纬网格是上传 GPU 的
 * 主几何（带 UV、seam 闭合列）；本网格是渲染核心的均匀三角片表示——二十面体基础上
 * 对每条边取中点、一分为四并重新归一化到球面，三角形大小均匀，没有经纬网格在两极
 * 的顶点挤压，适合软渲染管线做逐片剔除 / 深度测试与几何正确性单测。
 *
 * 细分层级与 LOD 的映射见 [RenderLod]；真正的 GPU morph 过渡仍由 mesh/LodSelector
 * 驱动，本类只产出某一层级的静态三角片。
 *
 * @param subdivisions 递归细分次数（0 = 基础二十面体，20 片）
 * @param radius       球半径（世界单位）
 * @param positions    顶点世界 / 模型空间坐标，xyz 交错
 * @param normals      单位法线，xyz 交错
 * @param indices      三角形索引，每 3 个组成一片
 */
data class IcosphereMesh(
    val subdivisions: Int,
    val radius: Float,
    val positions: FloatArray,
    val normals: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    fun triangle(index: Int): Tri {
        val i = index * 3
        return Tri(indices[i], indices[i + 1], indices[i + 2])
    }

    fun positionOf(v: Int): Vec3 =
        Vec3(positions[v * 3].toDouble(), positions[v * 3 + 1].toDouble(), positions[v * 3 + 2].toDouble())

    fun normalOf(v: Int): Vec3 =
        Vec3(normals[v * 3].toDouble(), normals[v * 3 + 1].toDouble(), normals[v * 3 + 2].toDouble())

    companion object {

        /** 基础二十面体 12 个顶点（黄金比 t），先归一化到单位球再细分。 */
        private fun baseVertices(): List<Vec3> {
            val t = (1.0 + sqrt(5.0)) / 2.0
            val raw = listOf(
                Vec3(-1.0, t, 0.0), Vec3(1.0, t, 0.0),
                Vec3(-1.0, -t, 0.0), Vec3(1.0, -t, 0.0),
                Vec3(0.0, -1.0, t), Vec3(0.0, 1.0, t),
                Vec3(0.0, -1.0, -t), Vec3(0.0, 1.0, -t),
                Vec3(t, 0.0, -1.0), Vec3(t, 0.0, 1.0),
                Vec3(-t, 0.0, -1.0), Vec3(-t, 0.0, 1.0),
            )
            return raw.map { it.normalize() }
        }

        /** 基础二十面体 20 个面（逆时针朝外）。 */
        private val BASE_FACES = listOf(
            Tri(0, 11, 5), Tri(0, 5, 1), Tri(0, 1, 7), Tri(0, 7, 10), Tri(0, 10, 11),
            Tri(1, 5, 9), Tri(5, 11, 4), Tri(11, 10, 2), Tri(10, 7, 6), Tri(7, 1, 8),
            Tri(3, 9, 4), Tri(3, 4, 2), Tri(3, 2, 6), Tri(3, 6, 8), Tri(3, 8, 9),
            Tri(4, 9, 5), Tri(2, 4, 11), Tri(6, 2, 10), Tri(8, 6, 7), Tri(9, 8, 1),
        )

        /**
         * 构建细分球面。
         * @param subdivisions 递归细分次数，必须 ≥ 0
         * @param radius       球半径
         * @param displace     沿法线方向的位移（世界单位），入参为单位法线；默认 0（正球）
         */
        fun build(
            subdivisions: Int,
            radius: Float,
            displace: (normal: Vec3) -> Float = { 0f },
        ): IcosphereMesh {
            require(subdivisions >= 0) { "细分次数必须 ≥ 0，got subdivisions=$subdivisions" }
            require(radius > 0f) { "球半径必须 > 0，got radius=$radius" }

            val verts = ArrayList(baseVertices())
            var faces = ArrayList(BASE_FACES)

            repeat(subdivisions) {
                val midCache = HashMap<Long, Int>()
                val next = ArrayList<Tri>(faces.size * 4)
                for (f in faces) {
                    val m01 = midpointIndex(verts, midCache, f.a, f.b)
                    val m12 = midpointIndex(verts, midCache, f.b, f.c)
                    val m20 = midpointIndex(verts, midCache, f.c, f.a)
                    // 每个老面拆四个小面，保持逆时针朝外。
                    next.add(Tri(f.a, m01, m20))
                    next.add(Tri(m01, f.b, m12))
                    next.add(Tri(m20, m12, f.c))
                    next.add(Tri(m01, m12, m20))
                }
                faces = next
            }

            val n = verts.size
            val positions = FloatArray(n * 3)
            val normals = FloatArray(n * 3)
            val indices = IntArray(faces.size * 3)

            for (i in 0 until n) {
                val unit = verts[i].normalize()
                // 兜底：退化零向量归一化后仍是零，这里退化为单位 Z，避免 NaN 顶点进管线。
                val safe = if (unit.lengthSquared() < 1e-12) Vec3.UNIT_Z else unit
                val offset = displace(safe)
                val rr = radius + offset
                positions[i * 3] = (safe.x * rr).toFloat()
                positions[i * 3 + 1] = (safe.y * rr).toFloat()
                positions[i * 3 + 2] = (safe.z * rr).toFloat()
                normals[i * 3] = safe.x.toFloat()
                normals[i * 3 + 1] = safe.y.toFloat()
                normals[i * 3 + 2] = safe.z.toFloat()
            }

            faces.forEachIndexed { k, f ->
                indices[k * 3] = f.a
                indices[k * 3 + 1] = f.b
                indices[k * 3 + 2] = f.c
            }

            return IcosphereMesh(
                subdivisions = subdivisions,
                radius = radius,
                positions = positions,
                normals = normals,
                indices = indices,
            )
        }

        /** 边中点缓存：有序顶点对作 key，重复边共享同一个中点，避免缝处顶点分裂。 */
        private fun midpointIndex(
            verts: ArrayList<Vec3>,
            cache: HashMap<Long, Int>,
            u: Int,
            v: Int,
        ): Int {
            val lo = minOf(u, v)
            val hi = maxOf(u, v)
            val key = lo.toLong() * 1_000_003L + hi
            cache[key]?.let { return it }
            val mid = (verts[u] + verts[v]) * 0.5
            verts.add(mid.normalize())
            val idx = verts.size - 1
            cache[key] = idx
            return idx
        }
    }
}
