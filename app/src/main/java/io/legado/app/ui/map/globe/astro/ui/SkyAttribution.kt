package io.legado.app.ui.map.globe.astro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.astro.contract.SkyParams

/**
 * 星空数据署名文案（纯 JVM 可测，不含 Compose 类型）。
 *
 * 合规口径（台账 S-04：HYG Database v41，CC BY-SA 4.0）：BY-SA 要求署名 + 以同许可
 * 再分发派生数据。展示侧四要素缺一不可——数据名、作者/来源及其仓库链接、许可名与许可
 * 链接、本作品衍生自该数据的表述。文案逐字落台账，不改写、不翻译许可名。
 */
object SkyAttributionText {

    /** 数据名。 */
    const val DATA_NAME: String = "HYG Database v41"

    /** 作者 / 来源署名。 */
    const val AUTHOR: String = "astronexus"

    /** 来源仓库链接。 */
    const val SOURCE_URL: String = "https://github.com/astronexus/HYG-Database"

    /** 许可名。 */
    const val LICENSE_NAME: String = "CC BY-SA 4.0"

    /** 许可链接。 */
    const val LICENSE_URL: String = "https://creativecommons.org/licenses/by-sa/4.0/"

    /** 派生表述：本作品衍生自该数据。 */
    const val DERIVATIVE_NOTICE: String = "本作品衍生自该数据"

    /** 来源行：数据名 · 作者。 */
    val sourceLine: String = "数据来源：$DATA_NAME · $AUTHOR"

    /** 许可行：许可名 · 许可链接。 */
    val licenseLine: String = "许可：$LICENSE_NAME · $LICENSE_URL"

    /** 完整署名串（四要素拼接，供自检 / 首启许可页复用）。 */
    val fullNotice: String = "$sourceLine\n$licenseLine\n$DERIVATIVE_NOTICE"
}

/**
 * 星空数据署名展示组件（C 件，宿主可见处挂载）。
 *
 * 字号取既有出处字号 [SkyParams.SHEET_SOURCE_FONT_SP]（14sp，满足 ≥14sp 下限）；
 * 颜色走既有锚色 token：主行 [GlobeTokens.textSecondary]，链接与派生行
 * [GlobeTokens.textTertiary]；不用纯黑纯白，也不引入第 14 个色值。
 *
 * 本组件只做静态文本排版，不绑 Activity / Context；文案来源见 [SkyAttributionText]。
 */
@Composable
fun SkyAttribution(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(12.dp)) {
        BasicText(
            text = SkyAttributionText.sourceLine,
            style = TextStyle(
                color = Color(GlobeTokens.textSecondary),
                fontSize = SkyParams.SHEET_SOURCE_FONT_SP.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        BasicText(
            text = SkyAttributionText.licenseLine,
            style = TextStyle(
                color = Color(GlobeTokens.textTertiary),
                fontSize = SkyParams.SHEET_SOURCE_FONT_SP.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        BasicText(
            text = SkyAttributionText.DERIVATIVE_NOTICE,
            style = TextStyle(
                color = Color(GlobeTokens.textTertiary),
                fontSize = SkyParams.SHEET_SOURCE_FONT_SP.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}
