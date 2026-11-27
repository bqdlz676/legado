package io.legado.app.ui.dict

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.legado.app.constant.AppLog
import io.legado.app.help.glide.ImageLoader
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isDataUrl

/**
 * 落文 · 词典条目富文本块模型。
 *
 * 旧链（上游 Legado）走 Markwon + GlideImagesPlugin / `TextView.setHtml` + [GlideImageGetter]，
 * 图片以 Drawable 内嵌进 Spannable；本编排层重写后改为 Compose 块序列：文本段 + 图片块交错排列。
 * 解析只负责把 `<img>` 与 Markdown `![alt](url)` 从条目 HTML/Markdown 中切出来，
 * **不改既有取词解析语义**（词条正文仍由 `DictRule.search` 产出，本层只做渲染适配）。
 */
sealed interface DictRichBlock {

    /** 普通文本段；残余 HTML 标签按现状原样透传，不在本层重写富文本排版。 */
    data class Text(val text: String) : DictRichBlock

    /**
     * 图片块。
     *
     * @param src 已按 baseUrl 收口的绝对地址；`data:` 协议原样保留
     * @param widthAttr 宽度口径：`"50%"` 百分比或像素数值；null 表示按图片原生宽度
     * @param align 对齐：`center` / `right`；其余按左对齐
     * @param click 旧链 option 中的 `click` 动作脚本；null 表示点击只看大图
     * @param alt 图片占位说明（contentDescription）
     */
    data class Image(
        val src: String,
        val widthAttr: String?,
        val align: String?,
        val click: String?,
        val alt: String?,
    ) : DictRichBlock
}

/**
 * 词典条目富文本解析器（只读适配：不回写原文、不改取词规则）。
 *
 * 口径对齐旧链 `GlideImageGetter`：
 * - 图片尾部可挂 `,{"width":"50%","style":"center","click":"..."}` 选项（[AnalyzeUrl.paramPattern] 切分）；
 * - `<img>` 的 `width` 属性 / `style="width:…"` / 选项 `width` 逐级收口；
 * - 相对地址以词条规则 URL 为 base 收口为绝对地址。
 */
object DictRichParser {

    private val tokenRegex = Regex(
        "<img\\b[^>]*>|!\\[[^\\]]*\\]\\([^)\\s]+\\)",
        RegexOption.IGNORE_CASE,
    )

    private val mdImageRegex = Regex(
        "!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)",
        RegexOption.IGNORE_CASE,
    )

    private fun attr(tag: String, name: String): String? {
        val regex = Regex("$name\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        return regex.find(tag)?.groupValues?.get(1)?.trim()?.ifBlank { null }
    }

    private fun styleWidth(style: String?): String? {
        if (style.isNullOrBlank()) return null
        return Regex("width\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(style)?.groupValues?.get(1)?.trim()?.trim('"', '\'')?.ifBlank { null }
    }

    private fun styleAlign(style: String?): String? {
        if (style.isNullOrBlank()) return null
        return Regex("text-align\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(style)?.groupValues?.get(1)?.trim()?.ifBlank { null }
    }

    /** 按旧链口径把 `url,{"width":…}` 拆成地址与选项表。 */
    private fun splitSourceOption(rawSrc: String): Pair<String, Map<String, String>?> {
        val matcher = AnalyzeUrl.paramPattern.matcher(rawSrc)
        if (!matcher.find()) return rawSrc.trim() to null
        val url = rawSrc.substring(0, matcher.start()).trim()
        val optionStr = rawSrc.substring(matcher.end())
        val option = GSON.fromJsonObject<Map<String, String>>(optionStr).getOrNull()
        return url to option
    }

    private fun toImageBlock(token: String, baseUrl: String?): DictRichBlock.Image? {
        return if (token.startsWith("<img", ignoreCase = true)) {
            val rawSrc = attr(token, "src")
                ?: attr(token, "data-src")
                ?: attr(token, "data-original")
                ?: return null
            val (url, option) = splitSourceOption(rawSrc)
            if (url.isBlank()) return null
            val widthAttr = attr(token, "width")
                ?: styleWidth(attr(token, "style"))
                ?: option?.get("width")
            val align = attr(token, "align")
                ?: styleAlign(attr(token, "style"))
                ?: option?.get("style")
            val resolved = if (url.isDataUrl()) {
                url
            } else {
                NetworkUtils.getAbsoluteURL(baseUrl, url)
            }
            DictRichBlock.Image(
                src = resolved,
                widthAttr = widthAttr,
                align = align,
                click = option?.get("click"),
                alt = attr(token, "alt"),
            )
        } else {
            val match = mdImageRegex.matchEntire(token) ?: return null
            val alt = match.groupValues[1]
            val url = match.groupValues[2].trim()
            if (url.isBlank()) return null
            val resolved = if (url.isDataUrl()) {
                url
            } else {
                NetworkUtils.getAbsoluteURL(baseUrl, url)
            }
            DictRichBlock.Image(
                src = resolved,
                widthAttr = null,
                align = null,
                click = null,
                alt = alt.ifBlank { null },
            )
        }
    }

    /**
     * 把词条正文切成文本段与图片块交错序列。
     *
     * @param raw `DictRule.search` 返回的原始 HTML / Markdown 文本
     * @param baseUrl 词条规则 URL，用于收口相对图片地址；可为空（空则相对地址原样透传，由加载链兜底）
     */
    fun parse(raw: String, baseUrl: String?): List<DictRichBlock> {
        val cleaned = raw.removePrefix("<md>").removeSuffix("</md>")
        val blocks = mutableListOf<DictRichBlock>()
        var cursor = 0
        tokenRegex.findAll(cleaned).forEach { match ->
            if (match.range.first > cursor) {
                val text = cleaned.substring(cursor, match.range.first)
                if (text.isNotBlank()) blocks.add(DictRichBlock.Text(text))
            }
            toImageBlock(match.value, baseUrl)?.let { blocks.add(it) }
            cursor = match.range.last + 1
        }
        if (cursor < cleaned.length) {
            val text = cleaned.substring(cursor)
            if (text.isNotBlank()) blocks.add(DictRichBlock.Text(text))
        }
        return blocks
    }
}

/**
 * 词典富文本容器：按条目可用宽度约束图片，文本段与图片块竖排交错。
 *
 * @param onImageTap 单击图片：带 `click` 动作时回传脚本，否则由宿主打开大图
 * @param onImageLongClick 长按图片：旧链口径为查看大图
 */
@Composable
fun DictRichContent(
    blocks: List<DictRichBlock>,
    onImageTap: (DictRichBlock.Image) -> Unit,
    onImageLongClick: (DictRichBlock.Image) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is DictRichBlock.Text -> BasicText(
                        text = block.text,
                        style = TextStyle(
                            color = ComponentTokens.textPrimary,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 24.sp,
                        ),
                    )

                    is DictRichBlock.Image -> DictRichImage(
                        block = block,
                        availableWidthPx = availableWidthPx,
                        onTap = onImageTap,
                        onLongClick = onImageLongClick,
                    )
                }
            }
        }
    }
}

/**
 * 单张词典图片：Glide 解码为 Bitmap 后推入 Compose [Image]，不走 `AndroidView{ ImageView }` 贴皮。
 *
 * 状态机：加载中（纸色骨架块）→ 成功（按宽高比约束显示）/ 失败（墨色小字 + 原文 URL，不留空白态）。
 * 加载与释放收口在 [DisposableEffect]：离开组合或换图时经 `Glide.clear` 回收目标，避免泄漏。
 */
@Composable
private fun DictRichImage(
    block: DictRichBlock.Image,
    availableWidthPx: Int,
    onTap: (DictRichBlock.Image) -> Unit,
    onLongClick: (DictRichBlock.Image) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(block.src) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(block.src) { mutableStateOf(false) }
    // 重试计数：点重试后自增，作为加载副作用的第二把钥匙，强制 Glide 重新解码 / 重新取图。
    var retryTick by remember(block.src) { mutableStateOf(0) }

    DisposableEffect(block.src, retryTick) {
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                // 复制为可变位图，避免 Glide 位图池回收后 Compose 绘制到已回收像素。
                bitmap = resource.copy(resource.config ?: Bitmap.Config.ARGB_8888, true)
                failed = false
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                bitmap = null
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                failed = true
                AppLog.put("词典图片加载失败: ${block.src}")
            }
        }
        val request = ImageLoader.loadBitmap(context, block.src)
        // 解码宽度下压到条目可用宽度：对齐旧 Markwon 链 override(imgAvailableWidth)，
        // 避免超大原图按原生尺寸解码撑爆位图内存；宽高比由 Glide 保持。
        if (availableWidthPx > 0) {
            request.apply(RequestOptions().override(availableWidthPx))
        }
        request.into(target)
        onDispose {
            Glide.with(context).clear(target)
        }
    }

    val current = bitmap
    val intrinsicW = current?.width ?: 0
    val intrinsicH = current?.height ?: 0
    val density = LocalDensity.current
    val sizePx = remember(block.widthAttr, intrinsicW, intrinsicH, availableWidthPx) {
        // 口径对齐 GlideImageGetter.getDrawableRect：百分比按可用宽度收口；像素值取属性；
        // 未指定则用原生宽度；位图未到时（原生宽 0）骨架按可用宽度兜底；
        // 超过可用宽度时收口到可用宽度，高度按原生宽高比等比缩放。
        val raw = block.widthAttr?.trim()
        val fallbackWidth = availableWidthPx.coerceAtLeast(1)
        val imgWidth = when {
            raw == null -> intrinsicW.takeIf { it > 0 } ?: fallbackWidth
            raw.endsWith("%", ignoreCase = true) -> {
                val pct = raw.dropLast(1).toIntOrNull() ?: 80
                availableWidthPx * pct / 100
            }

            else -> raw.toIntOrNull()?.takeIf { it > 0 }
                ?: intrinsicW.takeIf { it > 0 }
                ?: fallbackWidth
        }
        val showWidth = availableWidthPx.takeIf { it in 1..<imgWidth } ?: imgWidth
        val showHeight = if (intrinsicW > 0 && intrinsicH > 0) {
            (intrinsicH * showWidth / intrinsicW.toFloat()).toInt()
        } else {
            0
        }
        showWidth to showHeight
    }
    val (showWidthPx, showHeightPx) = sizePx

    val sheetAlign = when (block.align?.lowercase()) {
        "center" -> Alignment.Center
        "right" -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = sheetAlign,
    ) {
        when {
            failed -> DictImageFailure(
                src = block.src,
                onRetry = {
                    bitmap = null
                    failed = false
                    retryTick++
                },
                onOpenOriginal = { onTap(block) },
            )

            current == null -> DictImageSkeleton(showWidthPx, showHeightPx)

            else -> Image(
                bitmap = current.asImageBitmap(),
                contentDescription = block.alt,
                modifier = Modifier
                    .width(with(density) { showWidthPx.toDp() })
                    .then(
                        if (showHeightPx > 0) {
                            Modifier.height(with(density) { showHeightPx.toDp() })
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(ComponentTokens.cardRadius))
                    .pointerInput(block.src) {
                        detectTapGestures(
                            onTap = { onTap(block) },
                            onLongPress = { onLongClick(block) },
                        )
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** 加载中骨架：纸面色块，宽按已收口口径、高未知时给保底占位高度。 */
@Composable
private fun DictImageSkeleton(widthPx: Int, heightPx: Int) {
    val density = LocalDensity.current
    val skeletonHeight = if (heightPx > 0) {
        with(density) { heightPx.toDp() }
    } else {
        120.dp
    }
    Box(
        Modifier
            .width(with(density) { widthPx.toDp() })
            .height(skeletonHeight)
            .clip(RoundedCornerShape(ComponentTokens.cardRadius))
            .background(ComponentTokens.bgSurface),
    )
}

/**
 * 失败兜底：墨色小字说明 + 原文 URL + 重试按钮；占位区可点放大原文。
 * 重试仍失败时落回本占位（文案 + URL），不吞异常（异常已由 AppLog 落档），不留空白态。
 *
 * @param onRetry 重新解码 / 重新取图：由调用方自增重试钥匙并重置位图与失败标记
 * @param onOpenOriginal 占位区点击打开原图；查看器内调用时传 null（已在大图层）
 */
@Composable
private fun DictImageFailure(
    src: String,
    onRetry: () -> Unit,
    onOpenOriginal: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onOpenOriginal != null) { onOpenOriginal?.invoke() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            text = "图片未能载入",
            style = TextStyle(
                color = ComponentTokens.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            ),
        )
        BasicText(
            text = src,
            style = TextStyle(
                color = ComponentTokens.textTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.SansSerif,
            ),
        )
        InkButton("重试", onRetry, variant = InkButtonVariant.GHOST)
    }
}

/**
 * 大图查看器（挂在统一 [InkHalfSheet] 内，不新造弹层）。
 * 竖向滚动交由半屏内容槽自带的 verticalScroll，此处只补横滑，避免双层竖滑抢手势。
 */
@Composable
fun DictImageViewer(src: String) {
    val context = LocalContext.current
    var bitmap by remember(src) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(src) { mutableStateOf(false) }
    // 重试计数：大图查看器内点重试同样强制 Glide 重新取图；钥匙口径与条目内图片一致。
    var retryTick by remember(src) { mutableStateOf(0) }

    DisposableEffect(src, retryTick) {
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                bitmap = resource.copy(resource.config ?: Bitmap.Config.ARGB_8888, true)
                failed = false
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                bitmap = null
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                failed = true
                AppLog.put("词典大图加载失败: $src")
            }
        }
        val screenW = context.resources.displayMetrics.widthPixels
        ImageLoader.loadBitmap(context, src)
            .apply(RequestOptions().override(screenW))
            .into(target)
        onDispose {
            Glide.with(context).clear(target)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
        Column(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val current = bitmap
            when {
                failed -> DictImageFailure(
                    src = src,
                    onRetry = {
                        bitmap = null
                        failed = false
                        retryTick++
                    },
                )
                current != null -> Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )

                else -> DictImageSkeleton(widthPx, 0)
            }
        }
    }
}
