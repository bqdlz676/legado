package io.legado.app.ui.map.globe.mesh

/**
 * 球面 LOD 层级（切片 19 §球体精度 / 切片 20 §LOD 系统）。
 *
 * | 距离 | LOD | 段数 | 纹理 |
 * |---|---|---|---|
 * | 远距（整球可见） | LOD0 | 128×128 | 2K Blue Marble |
 * | 中距（大洲可见） | LOD1 | 256×256 | 4K + Natural Earth 地形阴影 |
 * | 近距（国家可见） | LOD2 | 512×512 | 8K + 高程 bump map 微凸起 |
 *
 * maxDrawCalls 对齐切片 20：LOD0 ≤10，LOD3（战场高精）≤18。
 */
enum class LodLevel(
    val segments: Int,
    val rings: Int,
    val textureTier: String,
    val maxDrawCalls: Int,
) {
    LOD0(128, 128, "2K", 10),
    LOD1(256, 256, "4K", 14),
    LOD2(512, 512, "8K", 18);

    val vertexCount: Int get() = (rings + 1) * (segments + 1)
    val indexCount: Int get() = rings * segments * 6
}
