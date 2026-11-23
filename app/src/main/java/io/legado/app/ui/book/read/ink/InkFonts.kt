package io.legado.app.ui.book.read.ink

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

/**
 * 阅读页字体注入点（规格切片-09 §3.1：正文 Noto Serif SC 思源宋体）。
 *
 * 字体资产由 U9 字体系统模块（切片-14）收口：子集字体就绪后，在 App 启动协程里
 * 调 [injectSerif] 注入解析好的 [Typeface] / [FontFamily]；注入前阅读层一律回退
 * 系统 serif（[Typeface.SERIF] / [FontFamily.Serif]），不硬编码字体文件路径、
 * 不在本层猜测字体名。
 *
 * 状态为进程级单例：注入一次后竖排 View 测量、正文绘制、气泡文案三处共用同一宋体。
 */
object InkFonts {

    /** 竖排 View / Paint 侧宋体实例；null = 未注入，回退系统 serif。 */
    @Volatile
    var serifTypeface: Typeface? = null

    /** Compose 侧宋体族；null = 未注入，回退 [FontFamily.Serif]。 */
    @Volatile
    var serifFamily: FontFamily? = null

    /**
     * 注入思源宋体。字体系统模块在字体子集加载成功后回调本方法；
     * 传入 null 表示卸载 / 回退，阅读层自动落回系统 serif。
     */
    fun injectSerif(typeface: Typeface?, family: FontFamily? = null) {
        serifTypeface = typeface
        serifFamily = family
    }

    /** View 侧取用：未注入时系统 serif 兜底。 */
    val viewTypeface: Typeface get() = serifTypeface ?: Typeface.SERIF

    /** Compose 侧取用：未注入时系统 serif 兜底。 */
    val composeFamily: FontFamily get() = serifFamily ?: FontFamily.Serif
}
