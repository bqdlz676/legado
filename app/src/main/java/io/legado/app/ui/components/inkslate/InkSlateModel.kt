package io.legado.app.ui.components.inkslate

// F-73 待规格化：U7 画板 8.3 WebView 预热/JS 分片/removeView 复用/双击撤销通路已被原生位图替代但切片未同步；通路等价性、8.4 批注章节名+正文文本传递、PNG/SVG 导出口径待规格化（审计 表三#4，待重做后回填规格出处）

import io.legado.app.ui.components.ComponentPalette
import kotlin.math.hypot

/**
 * U7 画板 InkSlate · 纯模型层（切片-12 逐字落地）。
 *
 * 本文件**不引用任何 Compose / Android 类型**，可在普通 JVM 单测中断言；
 * 绘制与手势由 [InkSlateCanvas] / [InkSlateToolbar] 承载。
 *
 * 保留功能（§8.1）：画笔 · 直线 · 箭头 · 矩形 · 圆 · 文字 · 橡皮 · 撤销/重做 ·
 * 图层（底图 + 墨迹两层）· PNG/SVG 导出。
 * 砍掉功能（§8.1，模型内不出现对应入口）：抓手 · 菱形 · 插入图片 · 激光笔 · 协作 ·
 * 填充色 · 透明度 · 粗糙度 · 字体选择器 · 字号多档 · 元素锁定。
 */

/** 画板工具枚举；顺序即顶部工具栏从左到右顺序（§8.2）。 */
enum class InkTool {
    /** ✏️ 画笔 */
    BRUSH,

    /** 📏 直线 */
    LINE,

    /** ➡️ 箭头 */
    ARROW,

    /** ⬜ 矩形 */
    RECT,

    /** ⭕ 圆形 */
    CIRCLE,

    /** 🔤 文字 */
    TEXT,

    /** 🧹 橡皮 */
    ERASER;

    companion object {
        /** 顶部工具栏全部工具，全部露出，没有「⋯ 更多」 */
        val toolbar: List<InkTool> = entries
    }
}

/** 图层：底图 + 墨迹两层（§8.1）。 */
enum class InkLayer { BASE, INK }

/**
 * 六色预设（§8.2）：朱砂 · 墨灰 · 花青 · 藤黄 · 暖金 · 石青。
 * 与全站 token 重合的四色直接复用 [ComponentPalette]；花青 / 藤黄为传统国画色，
 * 在本模型内收口为 ARGB 常数，不在组件各处另造色值。
 */
enum class InkPreset(val label: String, val argb: Long) {
    VERMILION("朱砂", ComponentPalette.TEXT_ACCENT),
    INK_GRAY("墨灰", ComponentPalette.TEXT_PRIMARY),
    INDIGO("花青", InkSlatePalette.HUA_QING),
    GAMBOGE("藤黄", InkSlatePalette.TENG_HUANG),
    WARM_GOLD("暖金", ComponentPalette.BRAND_GOLD),
    STONE_BLUE("石青", ComponentPalette.STONE_BLUE);

    companion object {
        /** 底部色条全部六色，顺序固定 */
        val bar: List<InkPreset> = entries

        fun fromArgb(argb: Long): InkPreset? = bar.firstOrNull { it.argb == argb }
    }
}

/** 画板专属国画色常数（ARGB, 0xAARRGGBB），仅花青 / 藤黄两色；其余走全站 token。 */
object InkSlatePalette {
    /** 花青：传统矿物花青，深靛蓝 */
    const val HUA_QING: Long = 0xFF2B3A55

    /** 藤黄：传统藤黄，暖赭黄 */
    const val TENG_HUANG: Long = 0xFFE0A92E

    /** 线宽下限（dp 当量） */
    const val WIDTH_MIN: Float = 1f

    /** 线宽上限（dp 当量） */
    const val WIDTH_MAX: Float = 12f

    /** 画笔默认线宽 */
    const val WIDTH_DEFAULT: Float = 4f

    /** 橡皮擦除半径（dp 当量，相对线宽放大） */
    const val ERASER_RADIUS_FACTOR: Float = 2.2f

    /** 双击画布判定间隔（毫秒）：双击 = 撤销（§8.3） */
    const val DOUBLE_TAP_MS: Long = 300L
}

/** 笔迹采样点：坐标 + 压感（0.35 ~ 1.0，慢速落笔压感高、笔触粗）。 */
data class StrokePoint(val x: Float, val y: Float, val pressure: Float)

/**
 * 一条笔迹 / 图形。
 *
 * @param tool 生成工具
 * @param layer 所属图层
 * @param color 预设色 ARGB
 * @param width 线宽（dp 当量）
 * @param points 采样点；直线 / 箭头 / 矩形 / 圆取首末两点
 * @param text 文字工具的文本内容
 */
data class Stroke(
    val id: Long,
    val tool: InkTool,
    val layer: InkLayer,
    val color: Long,
    val width: Float,
    val points: List<StrokePoint>,
    val text: String? = null,
) {
    /** 图形类工具是否只取首末点 */
    val isShape: Boolean
        get() = tool == InkTool.LINE || tool == InkTool.ARROW ||
            tool == InkTool.RECT || tool == InkTool.CIRCLE

    /** 任一点距 (x, y) 是否在 radius 内（橡皮命中判定） */
    fun hitTest(x: Float, y: Float, radius: Float): Boolean = points.any {
        hypot(it.x - x, it.y - y) <= radius
    }
}

/**
 * 批注元信息（§8.4）：Annotation 模式传给前端的不只是 bgImage 截图，
 * 还带上章节名 + 正文纯文本；导出图片底部自动附注。
 */
data class AnnotationMeta(
    val chapterTitle: String,
    val pageLabel: String,
    val plainText: String,
    val bgImageRef: String? = null,
) {
    /** 导出附注：《左传·隐公元年》第 3 页批注 */
    fun footnote(): String = "《$chapterTitle》${pageLabel}批注"

    companion object {
        /**
         * 阅读批注上下文 → 元信息（§8.4）。
         *
         * 书名空白视为自由画板，返回 null；章名空白时附注只落书名；
         * 页码从 0 起换算为「第 N 页」。纯口径收口在模型层，便于 JVM 单测。
         */
        fun fromReadContext(
            bookName: String,
            chapterName: String,
            pageIndex: Int,
            plainText: String,
            bgImageRef: String? = null,
        ): AnnotationMeta? {
            val book = bookName.trim()
            if (book.isBlank()) return null
            val chapter = chapterName.trim()
            val chapterTitle = if (chapter.isBlank()) book else "$book·$chapter"
            return AnnotationMeta(
                chapterTitle = chapterTitle,
                pageLabel = "第 ${pageIndex + 1} 页",
                plainText = plainText,
                bgImageRef = bgImageRef,
            )
        }
    }
}

/** 压感模拟：根据落笔速度换算压感。慢速 → 压感高（笔触粗），快速 → 压感低（笔触细）。 */
object InkPressure {
    const val MIN_PRESSURE: Float = 0.35f
    const val MAX_PRESSURE: Float = 1.0f

    /**
     * @param velocityPxPerEvent 相邻采样点位移（px / 事件）
     * @param referenceVelocity 参考速度：超过该位移视为快速扫笔
     */
    fun simulate(velocityPxPerEvent: Float, referenceVelocity: Float = 24f): Float {
        val v = velocityPxPerEvent.coerceAtLeast(0f)
        val t = (v / referenceVelocity).coerceIn(0f, 1f)
        // 速度越快压感越低；线性映射后夹紧到 [MIN, MAX]
        return (MAX_PRESSURE - (MAX_PRESSURE - MIN_PRESSURE) * t)
            .coerceIn(MIN_PRESSURE, MAX_PRESSURE)
    }
}

/**
 * 画板不可变状态。所有修改都返回新状态，撤销 / 重做各持一条栈，模型层可直接单测。
 */
data class InkSlateState(
    val strokes: List<Stroke> = emptyList(),
    val redoStack: List<Stroke> = emptyList(),
    val tool: InkTool = InkTool.BRUSH,
    val color: Long = InkPreset.VERMILION.argb,
    val width: Float = InkSlatePalette.WIDTH_DEFAULT,
    val layer: InkLayer = InkLayer.INK,
    val currentStroke: Stroke? = null,
    val annotation: AnnotationMeta? = null,
    val canvasWidth: Float = 0f,
    val canvasHeight: Float = 0f,
    private val nextId: Long = 1L,
) {

    val canUndo: Boolean get() = strokes.isNotEmpty() || currentStroke != null
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun selectTool(tool: InkTool): InkSlateState = copy(tool = tool)

    fun selectColor(preset: InkPreset): InkSlateState = copy(color = preset.argb)

    fun setWidth(width: Float): InkSlateState = copy(
        width = width.coerceIn(InkSlatePalette.WIDTH_MIN, InkSlatePalette.WIDTH_MAX),
    )

    fun setLayer(layer: InkLayer): InkSlateState = copy(layer = layer)

    fun bindAnnotation(meta: AnnotationMeta): InkSlateState = copy(annotation = meta)

    fun setCanvasSize(width: Float, height: Float): InkSlateState =
        copy(canvasWidth = width.coerceAtLeast(0f), canvasHeight = height.coerceAtLeast(0f))

    /** 落笔：开一条新笔迹；文字工具的文本由 [commitText] 收口。 */
    fun beginStroke(x: Float, y: Float, pressure: Float): InkSlateState {
        val point = clampPoint(x, y, pressure)
        val stroke = Stroke(
            id = nextId,
            tool = tool,
            layer = layer,
            color = color,
            width = width,
            points = listOf(point),
        )
        return copy(currentStroke = stroke, nextId = nextId + 1)
    }

    /** 拖动追加采样点；图形工具只保留首末两点。 */
    fun extendStroke(x: Float, y: Float, pressure: Float): InkSlateState {
        val stroke = currentStroke ?: return this
        val point = clampPoint(x, y, pressure)
        val points = when {
            stroke.isShape -> listOf(stroke.points.first(), point)
            else -> stroke.points + point
        }
        return copy(currentStroke = stroke.copy(points = points))
    }

    /** 文字工具：把文本挂到当前笔迹上并收笔。 */
    fun commitText(text: String): InkSlateState {
        val stroke = currentStroke ?: return this
        val finished = stroke.copy(text = text.ifBlank { null })
        return copy(
            strokes = strokes + finished,
            currentStroke = null,
            redoStack = emptyList(),
        )
    }

    /** 抬笔：当前笔迹入正式笔迹列表，清空重做栈。 */
    fun finishStroke(): InkSlateState {
        val stroke = currentStroke ?: return this
        // 单点笔迹（轻点一下）保留为点捺；空坐标笔迹丢弃兜底
        if (stroke.points.isEmpty()) return copy(currentStroke = null)
        return copy(
            strokes = strokes + stroke,
            currentStroke = null,
            redoStack = emptyList(),
        )
    }

    /** 撤销：弹出最近一条笔迹入重做栈；进行中的笔迹先收笔再撤。 */
    fun undo(): InkSlateState {
        val inFlight = currentStroke
        val base = if (inFlight != null) {
            copy(strokes = strokes + inFlight, currentStroke = null)
        } else this
        if (base.strokes.isEmpty()) return base
        val last = base.strokes.last()
        return base.copy(
            strokes = base.strokes.dropLast(1),
            redoStack = base.redoStack + last,
        )
    }

    /** 重做：从重做栈取回最近一条。 */
    fun redo(): InkSlateState {
        if (redoStack.isEmpty()) return this
        val last = redoStack.last()
        return copy(
            strokes = strokes + last,
            redoStack = redoStack.dropLast(1),
        )
    }

    /** 橡皮：擦除命中半径内的笔迹；被擦除笔迹不进重做栈（与原生画板语义一致）。 */
    fun eraseAt(x: Float, y: Float, pressure: Float = 1f): InkSlateState {
        val radius = width * InkSlatePalette.ERASER_RADIUS_FACTOR * pressure
        val kept = strokes.filterNot { it.hitTest(x, y, radius) }
        val current = currentStroke?.takeUnless { it.hitTest(x, y, radius) }
        return copy(strokes = kept, currentStroke = current)
    }

    /** 清空当前图层墨迹（底图不被清除）。 */
    fun clearLayer(target: InkLayer): InkSlateState = copy(
        strokes = strokes.filterNot { it.layer == target },
        currentStroke = currentStroke?.takeUnless { it.layer == target },
    )

    /** 模型自检：返回不合规说明，空列表表示通过。 */
    fun validate(): List<String> = buildList {
        if (width < InkSlatePalette.WIDTH_MIN || width > InkSlatePalette.WIDTH_MAX) {
            add("线宽 $width 超出 [${InkSlatePalette.WIDTH_MIN}, ${InkSlatePalette.WIDTH_MAX}]")
        }
        if (InkPreset.fromArgb(color) == null) add("色值 $color 不在六色预设内")
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) add("笔迹 ${stroke.id} 无采样点")
            if (stroke.tool == InkTool.TEXT && stroke.text == null) {
                add("文字笔迹 ${stroke.id} 缺少文本")
            }
            if (stroke.color.let { InkPreset.fromArgb(it) == null }) {
                add("笔迹 ${stroke.id} 色值不在预设内")
            }
        }
        currentStroke?.let {
            if (it.points.isEmpty()) add("进行中笔迹无采样点")
        }
    }

    private fun clampPoint(x: Float, y: Float, pressure: Float): StrokePoint {
        val cx = if (canvasWidth > 0f) x.coerceIn(0f, canvasWidth) else x
        val cy = if (canvasHeight > 0f) y.coerceIn(0f, canvasHeight) else y
        return StrokePoint(cx, cy, pressure.coerceIn(InkPressure.MIN_PRESSURE, InkPressure.MAX_PRESSURE))
    }

    companion object {
        /** 空白画板兜底（不允许空白态：画布壳 + 工具栏始终在） */
        val Empty: InkSlateState = InkSlateState()
    }
}

/** 导出包：SVG 标记 + 批注附注；PNG 位图编码由宿主 Android 层落 Bitmap，模型不持有上下文。 */
data class InkSlateExport(
    val svg: String,
    val footnote: String?,
    val strokeCount: Int,
)

/**
 * SVG 导出（§8.1 PNG/SVG 导出）：纯字符串拼装，JVM 可测。
 * 坐标系沿用画布 px；无填充色（§8.1 砍掉填充色），只有描边。
 */
object InkSlateSvg {

    fun build(state: InkSlateState): InkSlateExport {
        val w = state.canvasWidth.coerceAtLeast(1f)
        val h = state.canvasHeight.coerceAtLeast(1f)
        val body = buildString {
            state.strokes.forEach { appendStroke(it) }
            state.currentStroke?.let { appendStroke(it) }
        }
        val footnote = state.annotation?.footnote()
        val svg = buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
            append(w.toInt())
            append("\" height=\"")
            append(h.toInt())
            append("\" viewBox=\"0 0 ")
            append(w.toInt())
            append(' ')
            append(h.toInt())
            append("\">\n")
            append(body)
            if (footnote != null) {
                append("<text x=\"8\" y=\"")
                append((h - 8f).toInt())
                append("\" font-size=\"12\" fill=\"")
                append(toSvgColor(ComponentPalette.TEXT_SECONDARY))
                append("\">")
                append(escapeXml(footnote))
                append("</text>\n")
            }
            append("</svg>\n")
        }
        return InkSlateExport(
            svg = svg,
            footnote = footnote,
            strokeCount = state.strokes.size + (if (state.currentStroke != null) 1 else 0),
        )
    }

    private fun StringBuilder.appendStroke(stroke: Stroke) {
        if (stroke.points.isEmpty()) return
        val strokeColor = toSvgColor(stroke.color)
        val sw = stroke.width
        when (stroke.tool) {
            InkTool.LINE, InkTool.ARROW -> {
                val (a, b) = stroke.points.first() to stroke.points.last()
                append("<line x1=\"")
                append(a.x.toInt()); append("\" y1=\""); append(a.y.toInt())
                append("\" x2=\""); append(b.x.toInt()); append("\" y2=\""); append(b.y.toInt())
                append("\" stroke=\""); append(strokeColor)
                append("\" stroke-width=\""); append(sw)
                append("\" stroke-linecap=\"round\"/>\n")
                if (stroke.tool == InkTool.ARROW) appendArrowHead(a, b, strokeColor, sw)
            }
            InkTool.RECT -> {
                val (a, b) = stroke.points.first() to stroke.points.last()
                append("<rect x=\"")
                append(minOf(a.x, b.x).toInt())
                append("\" y=\"")
                append(minOf(a.y, b.y).toInt())
                append("\" width=\"")
                append(kotlin.math.abs(b.x - a.x).toInt())
                append("\" height=\"")
                append(kotlin.math.abs(b.y - a.y).toInt())
                append("\" fill=\"none\" stroke=\""); append(strokeColor)
                append("\" stroke-width=\""); append(sw)
                append("\"/>\n")
            }
            InkTool.CIRCLE -> {
                val (a, b) = stroke.points.first() to stroke.points.last()
                val cx = (a.x + b.x) / 2f
                val cy = (a.y + b.y) / 2f
                val r = hypot(b.x - a.x, b.y - a.y) / 2f
                append("<circle cx=\""); append(cx.toInt())
                append("\" cy=\""); append(cy.toInt())
                append("\" r=\""); append(r.toInt())
                append("\" fill=\"none\" stroke=\""); append(strokeColor)
                append("\" stroke-width=\""); append(sw)
                append("\"/>\n")
            }
            InkTool.TEXT -> {
                val a = stroke.points.first()
                append("<text x=\""); append(a.x.toInt())
                append("\" y=\""); append(a.y.toInt())
                append("\" font-size=\""); append((sw * 3).toInt())
                append("\" fill=\""); append(strokeColor)
                append("\">")
                append(escapeXml(stroke.text ?: ""))
                append("</text>\n")
            }
            else -> {
                // 画笔：折线串联采样点，压感体现在点径上（SVG 无可变线宽，按首点压感取线宽）
                val d = stroke.points.joinToString(" ") { p ->
                    "${p.x.toInt()},${p.y.toInt()}"
                }
                append("<polyline points=\""); append(d)
                append("\" fill=\"none\" stroke=\""); append(strokeColor)
                append("\" stroke-width=\""); append(sw)
                append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n")
            }
        }
    }

    private fun StringBuilder.appendArrowHead(
        a: StrokePoint,
        b: StrokePoint,
        color: String,
        sw: Float,
    ) {
        val angle = kotlin.math.atan2(b.y - a.y, b.x - a.x)
        val head = 10f + sw * 2f
        val left = angle + Math.PI * 5 / 6
        val right = angle - Math.PI * 5 / 6
        val lx = b.x + head * kotlin.math.cos(left).toFloat()
        val ly = b.y + head * kotlin.math.sin(left).toFloat()
        val rx = b.x + head * kotlin.math.cos(right).toFloat()
        val ry = b.y + head * kotlin.math.sin(right).toFloat()
        append("<polyline points=\"")
        append(lx.toInt()); append(','); append(ly.toInt())
        append(' '); append(b.x.toInt()); append(','); append(b.y.toInt())
        append(' '); append(rx.toInt()); append(','); append(ry.toInt())
        append("\" fill=\"none\" stroke=\""); append(color)
        append("\" stroke-width=\""); append(sw)
        append("\" stroke-linecap=\"round\"/>\n")
    }

    /** ARGB Long → #RRGGBB（SVG 不认 alpha 通道，描边不透明度由模型层另议）。 */
    fun toSvgColor(argb: Long): String {
        val rgb = (argb and 0x00FFFFFFL).toString(16).padStart(6, '0')
        return "#$rgb"
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
