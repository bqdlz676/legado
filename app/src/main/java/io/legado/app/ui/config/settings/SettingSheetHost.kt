package io.legado.app.ui.config.settings

import androidx.compose.runtime.Composable
import io.legado.app.ui.components.InkBottomSheet

/**
 * 设置分区弹层宿主：薄封装 [InkBottomSheet]，统一标题 / 关闭手势 / 毛玻璃样式。
 * 分区只关心内容槽，不重复实现遮罩与拖拽。
 */
@Composable
fun InkBottomSheetHost(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    InkBottomSheet(visible = true, onDismiss = onDismiss, title = title, content = content)
}
