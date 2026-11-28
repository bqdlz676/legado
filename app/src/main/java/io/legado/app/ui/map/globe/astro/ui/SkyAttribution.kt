package io.legado.app.ui.map.globe.astro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.map.globe.GlobeTokens
import io.legado.app.ui.map.globe.astro.contract.SkyParams

/**
 * 星空数据署名展示组件（C 件，宿主可见处挂载）。
 *
 * 字号取既有出处字号 [SkyParams.SHEET_SOURCE_FONT_SP]（14sp，满足 ≥14sp 下限）；
 * 颜色走既有锚色 token：主行 [GlobeTokens.textSecondary]，链接与派生行
 * [GlobeTokens.textTertiary]；不用纯黑纯白，也不引入第 14 个色值。
 *
 * 可点口径（S-04 验收判据②，Q-506）：来源行 / 许可行整行可点，点击经
 * [LocalUriHandler] 打开 [SkyAttributionText.urlForLine] 映射出的 URL；衍生表述行
 * 保持静态。可点行热区高 ≥ [SkyParams.HIT_ZONE_SIDE_DP]（48dp，UX-03 下限），并带
 * [Role.Button] 语义与 onClickLabel 供读屏识别。组件不绑 Activity / Context；文案与
 * 行→URL 映射见 [SkyAttributionText]。
 */
@Composable
fun SkyAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier.padding(12.dp)) {
        AttributionLinkRow(
            text = SkyAttributionText.sourceLine,
            color = Color(GlobeTokens.textSecondary),
            onClickLabel = "打开 HYG 数据来源仓库",
            uriHandler = uriHandler,
        )
        AttributionLinkRow(
            text = SkyAttributionText.licenseLine,
            color = Color(GlobeTokens.textTertiary),
            onClickLabel = "打开 CC BY-SA 4.0 许可页",
            uriHandler = uriHandler,
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

/**
 * 可点署名行：整行热区 ≥48dp，点击把 [SkyAttributionText.urlForLine] 映射出的 URL
 * 交给 [uriHandler]；映射为 null 的行不进入本组件（静态行见 [SkyAttribution] 末行）。
 */
@Composable
private fun AttributionLinkRow(
    text: String,
    color: Color,
    onClickLabel: String,
    uriHandler: UriHandler,
) {
    val url = SkyAttributionText.urlForLine(text)
    BasicText(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = SkyParams.SHEET_SOURCE_FONT_SP.sp,
            fontFamily = FontFamily.Serif,
        ),
        modifier = Modifier
            .heightIn(min = SkyParams.HIT_ZONE_SIDE_DP.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = onClickLabel,
            ) {
                url?.let(uriHandler::openUri)
            },
    )
}
