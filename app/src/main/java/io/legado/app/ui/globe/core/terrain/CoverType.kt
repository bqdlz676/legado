package io.legado.app.ui.globe.core.terrain

/**
 * 地表覆盖 7 种 + 极地冰盖子型（切片 17 / 19）。
 *
 * 切片 17 颜色映射：
 * | 覆盖类型 | 颜色 | 视觉 |
 * |---|---|---|
 * | 森林 | 深绿 #2D5A27 | 锥形凸起树冠，绒布质感 |
 * | 灌木/草地 | 黄绿 #7A9A3A | 平滑，浅半度，细微斑点 |
 * | 裸土/农田 | 赭褐 #A08060 | 平坦偏暖 |
 * | 沙漠 | 沙黄 #D4B896 | sin 噪声微波浪 |
 * | 雪线 | 白 #F0F0EE | 高程 >3500m 自动算，微反光 |
 * | 水域 | 蓝 #3C6E8A | 连续同色自动识别湖面 |
 * | 城市/建成区 | 灰褐 #8A7A6A | 极小方格纹理 |
 *
 * ICE 为切片 19 极地冰盖子型：从覆盖图读取（非高程算），冷白微蓝，specular 0.15。
 */
enum class CoverType(
    val hex: Int,
    val r: Int,
    val g: Int,
    val b: Int,
    val label: String,
) {
    FOREST(0x2D5A27, 0x2D, 0x5A, 0x27, "森林"),
    GRASS(0x7A9A3A, 0x7A, 0x9A, 0x3A, "灌木草地"),
    SOIL(0xA08060, 0xA0, 0x80, 0x60, "裸土农田"),
    DESERT(0xD4B896, 0xD4, 0xB8, 0x96, "沙漠"),
    SNOW(0xF0F0EE, 0xF0, 0xF0, 0xEE, "雪线"),
    WATER(0x3C6E8A, 0x3C, 0x6E, 0x8A, "水域"),
    URBAN(0x8A7A6A, 0x8A, 0x7A, 0x6A, "城市建成区"),
    ICE(0xDCE8EE, 0xDC, 0xE8, 0xEE, "极地冰盖");

    companion object {
        val TERRAIN_TYPES: List<CoverType> =
            listOf(FOREST, GRASS, SOIL, DESERT, SNOW, WATER, URBAN)

        /** 按 RGB 最近邻归类（覆盖图像素 → 类型）。 */
        fun nearest(r: Int, g: Int, b: Int): CoverType {
            var best = FOREST
            var bestDist = Long.MAX_VALUE
            for (t in entries) {
                val dr = r - t.r; val dg = g - t.g; val db = b - t.b
                val d = dr.toLong() * dr + dg * dg + db * db
                if (d < bestDist) { bestDist = d; best = t }
            }
            return best
        }
    }
}
