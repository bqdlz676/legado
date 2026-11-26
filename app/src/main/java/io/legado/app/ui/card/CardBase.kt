package io.legado.app.ui.card

import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.R
import java.util.Collections

/**
 * 卡片交互五态（切片-25 验收判据 5：默认 / 悬浮 / 点击 / 骨架屏 / 禁用，全部在基座内实现）。
 *
 * - [Default]：纸白底 + 暖金微光 12dp 阴影，静止可交互的常态。
 * - [Hover]：暖金阴影加深到 20dp，卡片轻抬 2dp；适用指针悬停 / 触屏长按悬停。
 * - [Pressed]：朱砂暗影 8dp，阴影收窄表达下压贴近纸面；适用手指按下瞬间。
 * - [Loading]：同尺寸骨架灰块 + 暖金斜光带 1.5s 循环；适用数据未到的占位态。
 * - [Disabled]：整卡压淡到 30% 透明度、去投影；适用不可点击的禁用行。
 */
enum class CardState { Default, Hover, Pressed, Loading, Disabled }

/** 单态解析出的可视参数；阴影色 / 底色按态直接选定，数值量（elevation / lift）再走 spring 插值。 */
private data class CardVisual(
    val elevationDp: Float,
    val liftDp: Float,
    val shadow: Color,
    val background: Color,
)

/**
 * 卡片基座面：12 类具体卡片全部复用本组件，阴影 / 圆角 / 抬升 / 颗粒 / 骨架光带只在此写一份。
 *
 * 为什么不包 Material `Card`：规格要求纸白 + 暖金辉光 + 宣纸颗粒的东方美学口径，Material
 * 默认 ripple / 阴影体系不匹配；本组件只用稳定基础修饰符自绘，色值一律取 [CardTokens]。
 * 边框刻意不加：规格明确卡片靠阴影拉开层次，默认态 0 stroke（需 1px 边框的变体在内容层自补）。
 *
 * @param state 五态之一，决定阴影档位、抬升、底色与是否渲染骨架光带。
 * @param modifier 外部给定尺寸 / 对齐；同尺寸要求由调用方的宽高修饰符保证。
 * @param content 卡片内容槽；Loading 态不渲染内容，避免未到数据时文字闪现。
 */
@Composable
fun CardBase(
    state: CardState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 圆角 16dp 全态统一；shape 同时供 clip / shadow / background 复用，避免各处各造一个 Shape。
    val shape = RoundedCornerShape(CardTokens.cardRadius)

    // 状态 → 可视参数映射；颜色切换不做插值（避免两档暖金混出脏色），只对数值量做 spring。
    val target = when (state) {
        CardState.Default -> CardVisual(
            elevationDp = CardTokens.shadowDefaultElevation.value,
            liftDp = 0f,
            shadow = CardTokens.shadowDefault,
            background = CardTokens.cardPaper,
        )

        CardState.Hover -> CardVisual(
            elevationDp = CardTokens.shadowHoverElevation.value,
            liftDp = CardTokens.hoverLift.value,
            shadow = CardTokens.shadowHover,
            background = CardTokens.cardPaper,
        )

        CardState.Pressed -> CardVisual(
            elevationDp = CardTokens.shadowPressedElevation.value,
            liftDp = 0f,
            shadow = CardTokens.shadowPressed,
            background = CardTokens.cardPaper,
        )

        CardState.Loading -> CardVisual(
            elevationDp = CardTokens.shadowDefaultElevation.value,
            liftDp = 0f,
            shadow = CardTokens.shadowDefault,
            background = CardTokens.skeletonShade,
        )

        CardState.Disabled -> CardVisual(
            elevationDp = 0f,
            liftDp = 0f,
            shadow = CardTokens.shadowDefault,
            background = CardTokens.cardPaper,
        )
    }

    // 阴影高度 spring 插值：档位切换时投影大小连续过渡，不出现跳变；刚度 / 阻尼见 token。
    val elevation by animateFloatAsState(
        targetValue = target.elevationDp,
        animationSpec = spring(
            dampingRatio = CardTokens.cardSpringDamping,
            stiffness = CardTokens.cardSpringStiffness,
        ),
        label = "cardElevation",
    )

    // 抬升量 spring 插值：Hover 上抬 2dp、其余态回落 0；与阴影同曲线，视觉上是同一次浮起动作。
    val lift by animateFloatAsState(
        targetValue = target.liftDp,
        animationSpec = spring(
            dampingRatio = CardTokens.cardSpringDamping,
            stiffness = CardTokens.cardSpringStiffness,
        ),
        label = "cardLift",
    )

    // 骨架斜光带：1.5s 线性循环、Restart 从头扫；规格禁止 delay 硬切，故周期内匀速推进。
    val skeletonTransition = rememberInfiniteTransition(label = "cardSkeletonBand")
    val bandShift by skeletonTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = CardTokens.skeletonBandPeriodMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cardBandShift",
    )

    Box(
        modifier = modifier
            // 先裁圆角：后续阴影 / 底色 / 颗粒全部约束在 16dp 圆角内，不溢到卡片外。
            .clip(shape)
            // 三档阴影：ambient / spot 同取 token 色；elevation 由 spring 驱动连续变化。
            .shadow(
                elevation = elevation.dp,
                shape = shape,
                ambientColor = target.shadow,
                spotColor = target.shadow,
            )
            // 悬浮抬升：Y 轴负方向上移 2dp；非 Hover 态 lift 回弹到 0。
            .offset(y = -lift.dp)
            // 底面：四态纸白，Loading 态换骨架灰；shape 保证底色不越圆角。
            .background(target.background, shape)
            // 材质层：非骨架态叠 2px 宣纸噪点；骨架态换斜光带，二者互斥不叠加。
            .then(
                if (state == CardState.Loading) {
                    Modifier.skeletonBand(bandShift)
                } else {
                    Modifier.cardNoise()
                },
            )
            // 禁用态：整卡压淡 30%；投影已在 target 中归零，不再额外置灰底面。
            .then(
                if (state == CardState.Disabled) {
                    Modifier.alpha(CardTokens.disabledAlpha)
                } else {
                    Modifier
                },
            ),
    ) {
        // 骨架态只画灰块 + 光带；真实内容等数据到位、状态切回 Default 后再组合，防止文字闪一下。
        if (state != CardState.Loading) {
            content()
        }
    }
}

/**
 * 进程级噪点位图缓存：按资源 id 共享解码结果。为什么不只靠 remember——卡片在 LazyColumn
 * 滚动时每个新进屏 item 都是新组合槽位，remember 只保住当前在屏槽位，跨槽位会各解码一次，
 * 违反判据 8「滚动时无新 Bitmap 创建」；故缓存表挂在文件级单例，进程内同一 resId 只解一次。
 * 适用：所有密度、所有屏；key 为 drawable 资源 id。
 */
private val noiseTileCache: MutableMap<Int, ImageBitmap> =
    Collections.synchronizedMap(mutableMapOf())

/**
 * 取进程共享的噪点位图：synchronized 段内查缓存 → 未命中才解码；runCatching 兜底，
 * 任何异常（资源缺失 / 非位图 / 解码失败）都返回 null，由 [cardNoise] 静默降级，不崩溃。
 */
private fun noiseTile(res: Resources): ImageBitmap? = synchronized(noiseTileCache) {
    noiseTileCache[R.drawable.paper_noise_tile]?.let { return@synchronized it }
    runCatching {
        val drawable = res.getDrawable(R.drawable.paper_noise_tile, null)
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
            ?: throw IllegalStateException("paper_noise_tile 必须是位图资源")
        bitmap.asImageBitmap()
    }.onSuccess { noiseTileCache[R.drawable.paper_noise_tile] = it }
        .getOrNull()
}

/**
 * 宣纸颗粒叠层：单张 64×64 平铺 png（paper_noise_tile），ImageShader 按 TileMode.Repeated
 * 重复平铺；Android 平台底层等价于 BitmapShader(bmp, REPEAT, REPEAT)。规格判据 8：不逐卡
 * 生成、滚动不新建 Bitmap——位图由进程级 [noiseTileCache] 跨槽位共享，Brush 在 composition
 * 级 remember，drawBehind lambda 内零分配。适用：Default / Hover / Pressed / Disabled 四态。
 */
@Composable
private fun Modifier.cardNoise(): Modifier {
    val res = LocalContext.current.resources
    // 第一层 remember：挡重组重复取表；解码本身已由进程级缓存表兜底，跨槽位只解一次。
    // Log.w 放进 remember lambda：仅在取图失败的那次组合打一条，后续重组不重复刷屏；
    // 降级分支只做返回，不再触发副作用。静默降级原因：噪点只是材质装饰，缺它不影响可读性与交互。
    val noiseBitmap: ImageBitmap? = remember(res) {
        noiseTile(res).also {
            if (it == null) Log.w("CardBase", "paper_noise_tile 解码失败，噪点层静默降级（不影响可读性与交互）")
        }
    }
    if (noiseBitmap == null) return this
    // 第二层 remember：Brush 仅随 bitmap 实例变化重建；帧绘制只做 drawRect，不新建对象。
    val noiseBrush = remember(noiseBitmap) {
        ShaderBrush(
            ImageShader(
                image = noiseBitmap,
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated,
            ),
        )
    }
    return this.drawBehind { drawRect(brush = noiseBrush) }
}

/**
 * 骨架斜光带：沿对角线方向的暖金渐变条，随 [bandShift]（-1 → 1）从左扫到右，1.5s 一轮。
 * 为什么用线性渐变而非纯色矩形：光带两侧需淡出，硬边矩形会像贴了一条金箔；斜向起止点
 * 对应规格「斜光带扫过」的方向感。适用：仅 Loading 态。
 */
private fun Modifier.skeletonBand(bandShift: Float): Modifier = this.drawBehind {
    val bandCenterX = -size.width * 0.3f + bandShift * size.width * 1.6f
    val bandHalfWidth = size.width * 0.16f
    val brush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            CardTokens.warmGold.copy(alpha = CardTokens.skeletonBandAlpha),
            Color.Transparent,
        ),
        start = Offset(bandCenterX - bandHalfWidth, 0f),
        end = Offset(bandCenterX + bandHalfWidth, size.height),
    )
    drawRect(brush = brush)
}
