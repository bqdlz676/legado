package io.legado.app.ui.config.settings

import androidx.compose.runtime.Composable
import io.legado.app.ui.components.InkBottomSheet

/**
 * 设置分区弹层宿主：薄封装 [InkBottomSheet]，统一标题 / 关闭手势 / 毛玻璃样式。
 * 分区只关心内容槽，不重复实现遮罩与拖拽。
 *
 * B29：[visible] 由宿主两段式离场控制器派生（在场 true / 离场段 false），不再传常量 true；
 * 离场段子树保留组合，播组件内离场动画，收束后由宿主清空槽位摘子树。
 */
@Composable
fun InkBottomSheetHost(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    InkBottomSheet(visible = visible, onDismiss = onDismiss, title = title, content = content)
}
