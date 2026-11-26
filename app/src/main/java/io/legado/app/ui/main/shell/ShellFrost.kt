package io.legado.app.ui.main.shell

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import io.legado.app.ui.book.read.ink.InkSpec
import io.legado.app.ui.book.read.ink.frostedSurface
import io.legado.app.ui.components.ComponentTokens

/**
 * B2 壳底栏磨砂面（N6 临时归属：跨域抽取到组件层 frostedSurface 归 B1；本文件只做壳内接线，
 * 不改 read/ink 域源码）。
 *
 * 〔口径回源〕三档兜底全部复用既有可引用修饰 [frostedSurface]：
 *  - 模糊半径 / 底色透明度 / 噪点网格与透明度单源 [InkTokens]，壳内不另造 dp / alpha / 色值；
 *  - API 31（S）及以上 BlurEffect 高斯；API 26–30 半透明绢帛 + 固定种子噪点颗粒；
 *  - 低端档位退近纯色绢帛，不做模糊与噪点叠层，保帧预算。
 *
 * 〔边界〕Android RenderEffect 模糊的是图层自身内容，并非截取背后背景做 backdrop blur；
 * 真正窗口级 backdrop 采样由窗口层统一 mediate（与既有磨砂修饰同一说明口径）。
 */
object ShellFrost {

    /** 是否支持 RenderEffect 高斯模糊（API 31+）。 */
    val supportsRenderEffect: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 低档位判定复用阅读页启发式（核数 / 堆内存），壳内不自造第二套低端判定。 */
    val lowSpec: Boolean
        get() = InkSpec.isLowSpec()
}

/**
 * 磨砂容器底：直接委托 [frostedSurface] 的三档降级口径。
 *
 * @param shape 底栏圆角（顶部分段圆角）
 */
fun Modifier.shellFrostSurface(
    shape: Shape = RoundedCornerShape(ComponentTokens.sheetRadius),
): Modifier = this.frostedSurface(shape = shape, lowSpec = ShellFrost.lowSpec)
