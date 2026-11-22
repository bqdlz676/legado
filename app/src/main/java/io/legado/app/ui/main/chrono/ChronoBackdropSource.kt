package io.legado.app.ui.main.chrono

import android.graphics.Bitmap

/**
 * 落文 · 纪年页背面模糊源抽象（ui/main/chrono 独占）。
 *
 * 契约要求：底栏与面板背面是「当前 3D 地图的实时模糊」（模糊 18px、亮度 0.62）。
 * 该实时源依赖 3D 沙盘模块，未就绪前由 [PlaceholderBackdropSource] 兜住机制：
 *  - 抽象接口先行，Compose 层只认 [MapBackdropSource.snapshot]；
 *  - 沙盘落地后只需把 [ChronoBackdropRegistry.current] 换成地图快照源，画面层零改动；
 *  - 快照为 null 时，画面层用暗底暖光渐变兜底，不空白、不崩溃。
 */
interface MapBackdropSource {

    /** 当前地图帧快照；返回 null 表示暂无实时源，走占位渐变 */
    fun snapshot(): Bitmap?
}

/** 占位模糊源：沙盘未就绪期间恒返回 null，由画面层渲染占位渐变 */
object PlaceholderBackdropSource : MapBackdropSource {
    override fun snapshot(): Bitmap? = null
}

/** 全局可替换注册点：沙盘接线时在此注入真实地图快照源 */
object ChronoBackdropRegistry {

    @Volatile
    var current: MapBackdropSource = PlaceholderBackdropSource
}
