package io.legado.app.ui.config.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.settings.NumberPickerSheet
import io.legado.app.ui.config.settings.SettingGroup
import io.legado.app.ui.config.settings.SettingList
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SwitchRow
import io.legado.app.ui.config.settings.ValueRow
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString

/**
 * 启动页分区（对应旧 pref_config_welcome.xml + WelcomeConfigFragment）。
 *
 * 启动图 / 文案按日 / 夜分别落盘；展示时长用数字步进盘（毫秒，0–5000，默认 500）。
 */
@Composable
fun WelcomeSection(actions: SectionActions) {
    val context = LocalContext.current
    var timeTarget by remember { mutableStateOf(false) }
    var imageTarget by remember { mutableStateOf<String?>(null) }

    val groups = listOf(
        SettingGroup(
            null,
            listOf(
                ValueRow(stringRes(R.string.welcome_show_time), context.getPrefInt(PreferKey.welcomeShowTime, 500).toString()) {
                    timeTarget = true
                },
                SwitchRow(PreferKey.customWelcome, stringRes(R.string.custom_welcome), stringRes(R.string.custom_welcome_summary), false),
            ),
        ),
        SettingGroup(
            stringRes(R.string.day),
            listOf(
                ValueRow(stringRes(R.string.background_image), AppConfig.welcomeImage ?: stringRes(R.string.select_image)) {
                    imageTarget = PreferKey.welcomeImage
                },
                SwitchRow(PreferKey.welcomeShowText, stringRes(R.string.show_welcome_text), stringRes(R.string.welcome_text), true),
            ),
        ),
        SettingGroup(
            stringRes(R.string.night),
            listOf(
                ValueRow(stringRes(R.string.background_image), AppConfig.welcomeImageDark ?: stringRes(R.string.select_image)) {
                    imageTarget = PreferKey.welcomeImageDark
                },
                SwitchRow(PreferKey.welcomeShowTextDark, stringRes(R.string.show_welcome_text), stringRes(R.string.welcome_text), true),
            ),
        ),
    )

    SettingList(groups)

    if (timeTarget) {
        NumberPickerSheet(
            visible = true,
            title = stringRes(R.string.welcome_show_time),
            min = 0,
            max = 5000,
            current = context.getPrefInt(PreferKey.welcomeShowTime, 500),
            unit = "ms",
            onConfirm = { context.putPrefInt(PreferKey.welcomeShowTime, it) },
            onDismiss = { timeTarget = false },
        )
    }

    imageTarget?.let { key ->
        io.legado.app.ui.config.settings.InkBottomSheetHost(
            title = stringRes(R.string.background_image),
            onDismiss = { imageTarget = null },
        ) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                io.legado.app.ui.components.InkButton(stringRes(R.string.select_image), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY) {
                    actions.pickImage { uri -> if (uri != null) context.putPrefString(key, uri.toString()) }
                    imageTarget = null
                }
                io.legado.app.ui.components.InkButton(stringRes(R.string.delete), variant = io.legado.app.ui.components.InkButtonVariant.GHOST) {
                    context.putPrefString(key, null)
                    imageTarget = null
                }
            }
        }
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
