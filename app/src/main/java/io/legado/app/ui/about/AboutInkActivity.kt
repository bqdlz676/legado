package io.legado.app.ui.about

// F-73 冻结：本模块无具体规格，暂停扩写与新派单；须先出 ≥2 份候选规格→独立选型→再重做（审计 表四#9，C1 关于页，层级/字段规格缺失）

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.ui.components.ComponentTokens

/**
 * 落文 · 品牌关于页（新写 Compose 宿主，类名保留供 My 页 startActivity）。
 *
 * 竖排「落文」两字居中，下接一道朱砂短竖与版本号，底部字幕横向缓走。
 * 动画全部走 Compose 动画 API；字幕用定长 tween 往返，不使用平台 ValueAnimator。
 */
class AboutInkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AboutInkBrand(onBack = { finish() }) }
    }
}

@Composable
private fun AboutInkBrand(onBack: () -> Unit) {
    val density = LocalDensity.current
    Column(
        Modifier.fillMaxSize().background(ComponentTokens.bgPage).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))

        // 竖排「落文」
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("落", "文").forEach { char ->
                androidx.compose.foundation.text.BasicText(
                    text = char,
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 56.sp,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        // 朱砂短竖
        Box(
            Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ComponentTokens.brandCopper),
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.foundation.text.BasicText(
            text = "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
            style = TextStyle(color = ComponentTokens.textSecondary, fontSize = 13.sp),
        )

        Spacer(Modifier.weight(1f))

        // 底部字幕 marquee
        MarqueeMotto(Modifier.fillMaxWidth().padding(bottom = 48.dp))
    }
}

@Composable
private fun MarqueeMotto(modifier: Modifier = Modifier) {
    val motto = "读其文，知其人，通其时。"
    val transition = rememberInfiniteTransition(label = "motto")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mottoShift",
    )
    val offsetPx = with(LocalDensity.current) { 60.dp.toPx() }
    Box(modifier = modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.text.BasicText(
            text = motto,
            style = TextStyle(
                color = ComponentTokens.textTertiary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.graphicsLayer { translationX = (shift - 0.5f) * offsetPx },
        )
    }
}
