package io.legado.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Toast 轻提示：成功（暖金）/ 失败（朱砂）/ 提示（石青）三色。
 *
 * 空消息不渲染；离线提示走 INFO 色；动作按钮可选。宿主负责定时收起，本组件只做呈现与回调。
 */
enum class ToastKind { SUCCESS, FAILURE, INFO }

data class ToastMessage(
    val id: Long,
    val kind: ToastKind,
    val text: String,
    val actionLabel: String? = null,
) {
    /** 自检：空文本不允许入队 */
    val isValid: Boolean get() = text.isNotBlank()

    companion object {
        fun success(id: Long, text: String, actionLabel: String? = null) =
            ToastMessage(id, ToastKind.SUCCESS, text, actionLabel)

        fun failure(id: Long, text: String, actionLabel: String? = null) =
            ToastMessage(id, ToastKind.FAILURE, text, actionLabel)

        fun info(id: Long, text: String, actionLabel: String? = null) =
            ToastMessage(id, ToastKind.INFO, text, actionLabel)
    }
}

object ToastPalette {
    /** 各色主题条 / 圆点取色（纯数值，供单测断言） */
    fun colorOf(kind: ToastKind): Long = when (kind) {
        ToastKind.SUCCESS -> ComponentPalette.BRAND_GOLD
        ToastKind.FAILURE -> ComponentPalette.TEXT_ACCENT
        ToastKind.INFO -> ComponentPalette.STONE_BLUE
    }

    fun composeColor(kind: ToastKind): Color = Color(colorOf(kind))
}

@Composable
fun InkToastHost(
    toasts: List<ToastMessage>,
    modifier: Modifier = Modifier,
    onAction: (ToastMessage) -> Unit = {},
    onDismiss: (ToastMessage) -> Unit = {},
) {
    if (toasts.none { it.isValid }) return
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        toasts.filter { it.isValid }.forEach { toast ->
            InkToast(toast, onAction = { onAction(toast) }, onDismiss = { onDismiss(toast) })
        }
    }
}

@Composable
fun InkToast(
    toast: ToastMessage,
    modifier: Modifier = Modifier,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    if (!toast.isValid) return

    val accent = ToastPalette.composeColor(toast.kind)
    val enter = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(ComponentPalette.DURATION_CARD, easing = ComponentTokens.cardEnterEasing),
        label = "toastEnter",
    ).value

    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(enter)
            .clip(shape)
            .background(ComponentTokens.bgSurface)
            .border(1.dp, ComponentTokens.colorDivider, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧色条
        Box(
            Modifier
                .width(4.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.text.BasicText(
            text = toast.text,
            style = TextStyle(
                color = ComponentTokens.textPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Serif,
            ),
            modifier = Modifier.weight(1f),
        )
        if (!toast.actionLabel.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            val gesture = rememberInkGesture(enabled = true, onClick = onAction)
            Box(
                Modifier
                    .then(gesture.gestureModifier)
                    .clip(RoundedCornerShape(ComponentPalette.BUTTON_RADIUS_DP.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = toast.actionLabel,
                    style = TextStyle(
                        color = ComponentTokens.colorLink,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
            }
        }
    }
}
