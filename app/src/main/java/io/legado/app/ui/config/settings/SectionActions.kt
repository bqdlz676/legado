package io.legado.app.ui.config.settings

import android.content.Intent
import android.net.Uri
import io.legado.app.ui.config.ConfigViewModel

/**
 * 分区页面与宿主 Activity 之间的动作收口。
 *
 * 分区 Composable 只发动作（选目录 / 选文件 / 跳外部页 / 弹对话框 / 调 VM），
 * 文件选择契约、ActivityResult、DialogFragment 事务都在宿主里实现，分区不碰 Android 契约。
 */
class SectionActions(
    val vm: ConfigViewModel,
    /** 选系统目录（tree uri 回吐字符串）。 */
    val pickFolder: (onResult: (String?) -> Unit) -> Unit,
    /** 选图片 / 文件，Uri 回吐。 */
    val pickImage: (onResult: (Uri?) -> Unit) -> Unit,
    /** 选单个文件；[extensions] 为允许后缀（可空）。 */
    val pickFile: (extensions: Array<String>?, onResult: (Uri?) -> Unit) -> Unit,
    /** 打开外部 Activity。 */
    val openIntent: (Intent) -> Unit,
    /** 打开同壳另一设置分区（按 configTag）。 */
    val openConfig: (String) -> Unit,
    /** 启动图标选择（展示名 + 图标值数组，当前值，选中回调）。 */
    val showIconPicker: (entries: List<String>, values: List<String>, current: String, onPick: (String) -> Unit) -> Unit,
    /** 弹既有 DialogFragment（按类名）。 */
    val showDialog: (Class<*>) -> Unit,
    /** 视频播放设置弹窗（video 域既有 DialogFragment，跨域入口）。 */
    val showVideoSettings: () -> Unit,
    /** 轻提示。 */
    val toast: (String) -> Unit,
    /** 扫描二维码（局域网备份接收）。 */
    val scanQr: (onResult: (String?) -> Unit) -> Unit,
)
