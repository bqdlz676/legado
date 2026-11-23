package io.legado.app.ui.config.sections

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.settings.NumberPickerSheet
import io.legado.app.ui.config.settings.NumberTarget
import io.legado.app.ui.config.settings.ColorSwatchRow
import io.legado.app.ui.config.settings.SettingGroup
import io.legado.app.ui.config.settings.SettingList
import io.legado.app.ui.config.settings.SectionActions
import io.legado.app.ui.config.settings.SwitchRow
import io.legado.app.ui.config.settings.TextEditSheet
import io.legado.app.ui.config.settings.ValueRow
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref

/**
 * 主题 / 外观分区（对应旧 pref_config_theme.xml + ThemeConfigFragment）。
 *
 * 颜色行复用既有 MATERIAL_COLORS 预设色板（数据能力），选择结果仍写回原颜色偏好键；
 * 背景图、保存主题、主题列表、底栏皮肤等动作按旧口径分发到既有帮助类 / 新壳页面。
 */
@Composable
fun ThemeSection(actions: SectionActions) {
    val context = LocalContext.current
    var numberTarget by remember { mutableStateOf<NumberTarget?>(null) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var themeNameTarget by remember { mutableStateOf<String?>(null) }
    var bgImageTarget by remember { mutableStateOf<String?>(null) }

    val iconEntries = context.resources.getStringArray(R.array.icon_names).toList()
    val iconValues = context.resources.getStringArray(R.array.icons).toList()
    val currentIcon = context.getPrefString(PreferKey.launcherIcon) ?: "ic_launcher"

    val groups = listOf(
        SettingGroup(
            null,
            listOf(
                ValueRow(stringRes(R.string.change_icon), stringRes(R.string.change_icon_summary)) {
                    actions.showIconPicker(iconEntries, iconValues, currentIcon) {
                        io.legado.app.help.LauncherIconHelp.changeIcon(it)
                    }
                },
                ValueRow(stringRes(R.string.welcome_style), stringRes(R.string.welcome_style_summary)) {
                    actions.openConfig(ConfigTag.WELCOME_CONFIG)
                },
                SwitchRow(PreferKey.transparentStatusBar, stringRes(R.string.immersion_status_bar), stringRes(R.string.status_bar_immersion), true),
                SwitchRow(PreferKey.immNavigationBar, stringRes(R.string.imm_navigation_bar), stringRes(R.string.imm_navigation_bar_s), true),
                ValueRow(stringRes(R.string.bar_elevation), AppConfig.elevation.toString()) {
                    numberTarget = NumberTarget(R.string.bar_elevation, 0, 32, AppConfig.elevation) { AppConfig.elevation = it }
                },
                ValueRow(stringRes(R.string.font_scale), stringRes(R.string.font_scale_summary)) {
                    val scale = context.getPrefInt(PreferKey.fontScale, 0).takeIf { it > 0 } ?: 10
                    numberTarget = NumberTarget(R.string.font_scale, 8, 16, scale) { context.putPrefInt(PreferKey.fontScale, it) }
                },
                ValueRow(stringRes(R.string.cover_config), stringRes(R.string.cover_config_summary)) {
                    actions.openConfig(ConfigTag.COVER_CONFIG)
                },
                ValueRow(stringRes(R.string.theme_list), stringRes(R.string.theme_list_summary)) {
                    actions.showDialog(io.legado.app.ui.config.ThemeListDialog::class.java)
                },
                ValueRow(stringRes(R.string.bottom_bar_skin), stringRes(R.string.bottom_bar_skin_summary)) {
                    actions.openIntent(android.content.Intent(context, io.legado.app.ui.config.BottomBarSkinActivity::class.java))
                },
                SwitchRow(PreferKey.disablePredictiveBack, stringRes(R.string.disable_predictive_back), stringRes(R.string.disable_predictive_back_summary), false),
                SwitchRow(PreferKey.wallpaperColorFollow, stringRes(R.string.wallpaper_color_follow), stringRes(R.string.wallpaper_color_follow_summary), false),
                SwitchRow(PreferKey.wallpaperColorAutoUpdate, stringRes(R.string.wallpaper_color_auto_update), stringRes(R.string.wallpaper_color_auto_update_summary), true),
            ),
        ),
        SettingGroup(
            stringRes(R.string.day),
            listOf(
                ColorSwatchRow(PreferKey.cPrimary, stringRes(R.string.primary), stringRes(R.string.day_color_primary), context.getPrefInt(PreferKey.cPrimary, Color.BLACK)) { colorTarget = ColorTarget(PreferKey.cPrimary, it) },
                ColorSwatchRow(PreferKey.cAccent, stringRes(R.string.accent), stringRes(R.string.day_color_accent), context.getPrefInt(PreferKey.cAccent, Color.BLACK)) { colorTarget = ColorTarget(PreferKey.cAccent, it) },
                ColorSwatchRow(PreferKey.cBackground, stringRes(R.string.background_color), stringRes(R.string.day_background_color), context.getPrefInt(PreferKey.cBackground, Color.BLACK)) { colorTarget = ColorTarget(PreferKey.cBackground, it) },
                ColorSwatchRow(PreferKey.cBBackground, stringRes(R.string.navbar_color), stringRes(R.string.day_navbar_color), context.getPrefInt(PreferKey.cBBackground, Color.BLACK)) { colorTarget = ColorTarget(PreferKey.cBBackground, it) },
                ValueRow(stringRes(R.string.background_image)) { bgImageTarget = PreferKey.bgImage },
                SwitchRow(PreferKey.tNavBar, stringRes(R.string.immersion_nav_bar), stringRes(R.string.day_nav_bar_immersion), false),
                ValueRow(stringRes(R.string.save_theme_config), stringRes(R.string.save_day_theme_summary)) { themeNameTarget = "saveDayTheme" },
            ),
        ),
        SettingGroup(
            stringRes(R.string.night),
            listOf(
                ColorSwatchRow(PreferKey.cNPrimary, stringRes(R.string.primary), stringRes(R.string.night_primary), context.getPrefInt(PreferKey.cNPrimary, Color.BLACK)) { colorTarget = ColorTarget(PreferKey.cNPrimary, it) },
                ColorSwatchRow(PreferKey.cNAccent, stringRes(R.string.accent), stringRes(R.string.night_accent), context.getPrefInt(PreferKey.cNAccent, Color.BLACK)) { colorTarget = ColorTarget(PreferKey.cNAccent, it) },
                ColorSwatchRow(PreferKey.cNBackground, stringRes(R.string.background_color), stringRes(R.string.night_background_color), context.getPrefInt(PreferKey.cNBackground, Color.BLACK)) { colorTarget = ColorTarget(PreferKey.cNBackground, it) },
                ColorSwatchRow(PreferKey.cNBBackground, stringRes(R.string.navbar_color), stringRes(R.string.night_navbar_color), context.getPrefInt(PreferKey.cNBBackground, Color.BLACK)) { colorTarget = ColorTarget(PreferKey.cNBBackground, it) },
                ValueRow(stringRes(R.string.background_image)) { bgImageTarget = PreferKey.bgImageN },
                SwitchRow(PreferKey.tNavBarN, stringRes(R.string.immersion_nav_bar), stringRes(R.string.night_nav_bar_immersion), false),
                ValueRow(stringRes(R.string.save_theme_config), stringRes(R.string.save_night_theme_summary)) { themeNameTarget = "saveNightTheme" },
            ),
        ),
    )

    SettingList(groups)

    numberTarget?.let { t ->
        NumberPickerSheet(true, stringRes(t.titleRes), t.min, t.max, t.current,
            onConfirm = t.apply, onDismiss = { numberTarget = null })
    }

    colorTarget?.let { target ->
        ColorPickerSheet(
            title = target.title,
            current = context.getPrefInt(target.key, Color.BLACK),
            onPick = { context.putPrefInt(target.key, it) },
            onDismiss = { colorTarget = null },
        )
    }

    themeNameTarget?.let { mode ->
        TextEditSheet(true, stringRes(R.string.theme_name), "", "name", onConfirm = { name ->
            if (name.isNotBlank()) {
                if (mode == "saveDayTheme") ThemeConfig.saveDayTheme(context, name)
                else ThemeConfig.saveNightTheme(context, name)
            }
        }, onDismiss = { themeNameTarget = null })
    }

    bgImageTarget?.let { key ->
        BackgroundImageSheet(
            onPickImage = { actions.pickImage { uri -> uri?.let { context.putPrefString(key, it.toString()) } } },
            onClear = { context.removePref(key) },
            onDismiss = { bgImageTarget = null },
        )
    }
}

/** 颜色选择弹层待决目标（仅本分区使用）。 */
private data class ColorTarget(val key: String, val title: String)

@Composable
private fun ColorPickerSheet(
    title: String,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = remember {
        runCatching { com.jaredrummler.android.colorpicker.ColorPickerDialog.MATERIAL_COLORS.toList() }
            .getOrDefault(emptyList())
    }
    io.legado.app.ui.config.settings.InkBottomSheetHost(title = title, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.take(12).forEach { c ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color(c))
                            .pointerTap { onPick(c); onDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundImageSheet(
    onPickImage: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    io.legado.app.ui.config.settings.InkBottomSheetHost(title = stringRes(R.string.background_image), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            io.legado.app.ui.components.InkButton(stringRes(R.string.select_image), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY, onClick = onPickImage)
            io.legado.app.ui.components.InkButton(stringRes(R.string.delete), variant = io.legado.app.ui.components.InkButtonVariant.GHOST, onClick = onClear)
        }
    }
}

private fun Modifier.pointerTap(onTap: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        androidx.compose.foundation.gestures.detectTapGestures(onTap = { onTap() })
    }

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
