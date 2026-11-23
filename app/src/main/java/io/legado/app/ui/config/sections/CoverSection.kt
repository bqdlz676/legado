package io.legado.app.ui.config.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.model.BookCover
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.settings.SettingGroup
import io.legado.app.ui.config.settings.SettingList
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SwitchRow
import io.legado.app.ui.config.settings.ValueRow
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref

/**
 * 封面分区（对应旧 pref_config_cover.xml + CoverConfigFragment）。
 *
 * 封面生成 / 默认封面兜底复用 [BookCover]；封面图选择走宿主图片契约，
 * 选择 / 清除后回吐 [BookCover.upDefaultCover] 刷新默认封面。
 */
@Composable
fun CoverSection(actions: SectionActions) {
    val context = LocalContext.current
    var pendingImageKey by remember { mutableStateOf<String?>(null) }

    fun pickCover(key: String) {
        actions.pickImage { uri ->
            if (uri != null) {
                putPrefString(key, uri.toString())
                BookCover.upDefaultCover()
            }
        }
    }

    val groups = listOf(
        SettingGroup(
            null,
            listOf(
                SwitchRow(PreferKey.loadCoverOnlyWifi, stringRes(R.string.only_wifi), stringRes(R.string.only_wifi_summary), false),
                ValueRow(stringRes(R.string.cover_rule), stringRes(R.string.cover_rule_summary)) {
                    actions.showDialog(io.legado.app.ui.config.CoverRuleConfigDialog::class.java)
                },
                SwitchRow(PreferKey.useDefaultCover, stringRes(R.string.use_default_cover), stringRes(R.string.use_default_cover_s), false) {
                    BookCover.upDefaultCover()
                },
                ValueRow(stringRes(R.string.cover_font_config)) { actions.openConfig(ConfigTag.COVER_FONT_CONFIG) },
            ),
        ),
        SettingGroup(
            stringRes(R.string.day),
            listOf(
                ValueRow(stringRes(R.string.default_cover), context.getPrefString(PreferKey.defaultCover) ?: stringRes(R.string.select_image)) {
                    pendingImageKey = PreferKey.defaultCover
                    pickCover(PreferKey.defaultCover)
                },
                SwitchRow(PreferKey.coverShowName, stringRes(R.string.cover_show_name), stringRes(R.string.cover_show_name_summary), true) {
                    BookCover.upDefaultCover()
                },
                SwitchRow(PreferKey.coverShowAuthor, stringRes(R.string.cover_show_author), stringRes(R.string.cover_show_author_summary), true) {
                    BookCover.upDefaultCover()
                },
            ),
        ),
        SettingGroup(
            stringRes(R.string.night),
            listOf(
                ValueRow(stringRes(R.string.default_cover), context.getPrefString(PreferKey.defaultCoverDark) ?: stringRes(R.string.select_image)) {
                    pickCover(PreferKey.defaultCoverDark)
                },
                SwitchRow(PreferKey.coverShowNameN, stringRes(R.string.cover_show_name), stringRes(R.string.cover_show_name_summary), true) {
                    BookCover.upDefaultCover()
                },
                SwitchRow(PreferKey.coverShowAuthorN, stringRes(R.string.cover_show_author), stringRes(R.string.cover_show_author_summary), true) {
                    BookCover.upDefaultCover()
                },
            ),
        ),
        SettingGroup(
            stringRes(R.string.read_record_covers),
            listOf(
                ValueRow(stringRes(R.string.read_record_cover_day), context.getPrefString(PreferKey.readRecordCover) ?: stringRes(R.string.select_image)) {
                    pickCover(PreferKey.readRecordCover)
                },
                ValueRow(stringRes(R.string.read_record_cover_night), context.getPrefString(PreferKey.readRecordCoverDark) ?: stringRes(R.string.select_image)) {
                    pickCover(PreferKey.readRecordCoverDark)
                },
            ),
        ),
    )

    SettingList(groups)
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
