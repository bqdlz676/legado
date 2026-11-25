package io.legado.app.ui.config.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkCard
import io.legado.app.ui.config.settings.NumberPickerSheet
import io.legado.app.ui.config.settings.SettingGroup
import io.legado.app.ui.config.settings.SettingList
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SheetExitHost
import io.legado.app.ui.config.settings.SwitchRow
import io.legado.app.ui.config.settings.ValueRow
import io.legado.app.ui.config.settings.rememberSheetExitController
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt

/**
 * 封面字体分区（对应旧 pref_config_cover_font.xml + CoverFontConfigFragment）。
 *
 * 字体选择复用字体域 [io.legado.app.ui.font.FontSelectDialog] 入口；字号区间 50–200（默认 100），
 * 与旧 NumberPickerDialog 口径一致。底部为封面字体预览（Compose 自绘样例卡片）。
 */
@Composable
fun CoverFontSection(actions: SectionActions) {
    val context = LocalContext.current
    var sizeTarget by remember { mutableStateOf<SizeTarget?>(null) }
    // B29：字号盘两段式离场控制器；离场段拒收重复打开。
    val sizeExit = rememberSheetExitController()

    val sizeRows = listOf(
        PreferKey.coverTitleLargeSize to R.string.cover_title_large_size,
        PreferKey.coverTitleSmallSize to R.string.cover_title_small_size,
        PreferKey.coverAuthorLargeSize to R.string.cover_author_large_size,
        PreferKey.coverAuthorSmallSize to R.string.cover_author_small_size,
    )

    val groups = listOf(
        SettingGroup(
            null,
            listOf(
                SwitchRow(PreferKey.coverHorizontal, stringRes(R.string.cover_horizontal), stringRes(R.string.cover_horizontal_summary), false),
                SwitchRow(PreferKey.coverTitleAdaptive, stringRes(R.string.cover_title_adaptive), stringRes(R.string.cover_title_adaptive_summary), true),
                SwitchRow(PreferKey.coverKeepPunctuation, stringRes(R.string.cover_keep_punctuation), stringRes(R.string.cover_keep_punctuation_summary), false),
                ValueRow(stringRes(R.string.cover_font_select), context.getPrefString(PreferKey.coverFont)?.ifBlank { null }) {
                    actions.showDialog(io.legado.app.ui.font.FontSelectDialog::class.java)
                },
                SwitchRow(PreferKey.coverCustomFontSize, stringRes(R.string.cover_custom_font_size), stringRes(R.string.cover_custom_font_size_summary), false),
            ) + sizeRows.map { (key, titleRes) ->
                ValueRow(stringRes(titleRes), context.getPrefInt(key, 100).toString()) {
                    if (sizeExit.canOpen) {
                        sizeTarget = SizeTarget(key, titleRes, context.getPrefInt(key, 100).coerceIn(50, 200))
                        sizeExit.open()
                    }
                }
            },
        ),
    )

    SettingList(groups)
    CoverPreviewCard()

    sizeTarget?.let { t ->
        SheetExitHost(sizeExit, onFinished = { sizeTarget = null }) {
            NumberPickerSheet(
                visible = sizeExit.visible,
                title = stringRes(t.titleRes),
                min = 50,
                max = 200,
                current = t.current,
                onConfirm = { context.putPrefInt(t.key, it) },
                onDismiss = { sizeExit.beginExit() },
            )
        }
    }
}

/** 封面字体预览样例：书名 + 作者，按当前字号偏好渲染（兜底样例，不接真实封面）。 */
@Composable
private fun CoverPreviewCard() {
    val context = LocalContext.current
    val titleLarge = context.getPrefInt(PreferKey.coverTitleLargeSize, 100)
    val authorLarge = context.getPrefInt(PreferKey.coverAuthorLargeSize, 100)
    InkCard {
        Column(Modifier.padding(20.dp)) {
            BasicText(
                text = "书名预览",
                style = TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = (titleLarge / 10f).sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "作者预览",
                style = TextStyle(
                    color = ComponentTokens.textSecondary,
                    fontSize = (authorLarge / 10f).sp,
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
    }
}

/** 字号盘待决目标（仅本分区使用）。 */
private data class SizeTarget(val key: String, val titleRes: Int, val current: Int)

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
