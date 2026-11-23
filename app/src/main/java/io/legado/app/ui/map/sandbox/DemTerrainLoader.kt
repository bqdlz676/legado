package io.legado.app.ui.map.sandbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * DEM 资产 Android 侧薄适配（ui/map/sandbox 独占，缺陷 D-APK-1）。
 *
 * 纯 JVM 采样核心 [DemTerrainElevation] 不碰 Android；本类只负责把 assets 灰度 PNG
 * 解码成 IntArray 网格再交给核心。首次进入沙盘时加载一次；解码失败 / 资产缺失 /
 * 栅格退化一律回落 [TerrainElevation.Flat]，降级日志全程只打一次，不反复刷 logcat。
 *
 * 为什么解码后取 R 通道：8-bit 灰度 PNG 解码后 R=G=B，任一通道即灰度值；强制
 * ARGB_8888 避免 565 压缩丢掉低位灰度。
 */
object DemTerrainLoader {

    private const val TAG = "SandboxDem"
    private const val ASSET_PATH = "map/china_height.png"

    @Volatile
    private var failureLogged = false

    /**
     * 加载高程栅格；任何异常 / 空解码结果都回落平面地形。
     * 返回的 [TerrainElevation] 注入 [SandboxScene.elevation]，模型层不感知高度来源。
     */
    fun load(context: Context): TerrainElevation {
        return try {
            context.assets.open(ASSET_PATH).use { input ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeStream(input, null, options)
                    ?: return fallback("decodeStream 返回 null（资产缺失或损坏）")
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                bitmap.recycle()

                val gray = IntArray(pixels.size) { i -> (pixels[i] ushr 16) and 0xFF }
                val elevation = DemTerrainElevation.create(width, height, gray)
                if (elevation.degraded) fallback("栅格尺寸 / 内容退化") else elevation
            }
        } catch (t: Throwable) {
            fallback("读取 DEM 资产异常: ${t.message}")
        }
    }

    private fun fallback(reason: String): TerrainElevation {
        if (!failureLogged) {
            failureLogged = true
            Log.w(TAG, "DEM 降级为平面地形：$reason")
        }
        return TerrainElevation.Flat
    }
}
