package io.legado.app.ui.globe.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import io.legado.app.ui.globe.core.GlobeConfig
import io.legado.app.ui.globe.core.data.GlobePlace
import io.legado.app.ui.globe.core.data.PlaceTier
import io.legado.app.ui.globe.core.data.SeedAtlas

/**
 * 地名标签 atlas（GL 层）。
 *
 * 切片 20：所有标签合并到一张 atlas 纹理（draw call 22→1 的同款思路）；
 * 切片 19：Billboard Sprite 朝向相机、固定球面位置；都城常显，一般地名远距淡出。
 *
 * 图集内每个条目记录 UV 矩形，渲染层按球面位置贴 billboard 四边形。
 */
data class LabelEntry(
    val place: GlobePlace,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
)

class AtlasLabels(
    val bitmap: Bitmap,
    val entries: List<LabelEntry>,
) {
    fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        private const val ATLAS_W = 1024
        private const val ROW_H = 48
        private const val PAD = 12

        fun build(): AtlasLabels {
            val places = SeedAtlas.places
            val rows = places.size
            val h = (rows * ROW_H).coerceAtLeast(ROW_H)
            val bitmap = Bitmap.createBitmap(ATLAS_W, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.SERIF
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }

            val entries = ArrayList<LabelEntry>(rows)
            places.forEachIndexed { i, place ->
                val y = i * ROW_H
                paint.textSize = if (place.tier == PlaceTier.CAPITAL) 26f else 20f
                paint.color = if (place.tier == PlaceTier.CAPITAL) {
                    GlobeConfig.TOKEN_BRAND_GOLD
                } else {
                    GlobeConfig.TOKEN_TEXT_TERTIARY
                }
                paint.alpha = if (place.tier == PlaceTier.CAPITAL) 255 else 200
                canvas.drawText(place.name, PAD.toFloat(), (y + ROW_H / 2 + 9).toFloat(), paint)

                val textW = paint.measureText(place.name)
                val u1 = ((PAD * 2 + textW) / ATLAS_W).coerceIn(0f, 1f)
                entries.add(
                    LabelEntry(
                        place = place,
                        u0 = 0f,
                        v0 = y.toFloat() / h,
                        u1 = u1,
                        v1 = (y + ROW_H).toFloat() / h,
                    ),
                )
            }
            return AtlasLabels(bitmap, entries)
        }
    }
}
