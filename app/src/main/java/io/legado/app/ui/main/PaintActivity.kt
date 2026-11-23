package io.legado.app.ui.main

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.rememberInkGesture
import io.legado.app.ui.components.inkslate.AnnotationMeta
import io.legado.app.ui.components.inkslate.InkSlateBitmap
import io.legado.app.ui.components.inkslate.InkSlateBottomBar
import io.legado.app.ui.components.inkslate.InkSlateCanvas
import io.legado.app.ui.components.inkslate.InkSlateSvg
import io.legado.app.ui.components.inkslate.InkSlateState
import io.legado.app.ui.components.inkslate.InkSlateTopBar
import io.legado.app.ui.components.inkslate.StrokePoint
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 墨板 · 画板宿主（切片-12，Compose 原生自绘，不挂旧网页画板壳）。
 *
 * - 工具 / 颜色 / 线宽 / 撤销重做 / 图层 / 导出由 [InkSlateTopBar] / [InkSlateBottomBar] /
 *   [InkSlateCanvas] 承载；本类只做 Intent 接线、批注元信息、底图解码与落盘分享；
 * - 批注模式（§8.4）：带入章节名 + 正文纯文本 + 正文页截图，导出图底部自动附注
 *   「《左传·隐公元年》第 3 页批注」；
 * - PNG 走原生 [InkSlateBitmap] 位图渲染，SVG 走 [InkSlateSvg] 字符串拼装；
 *   导出失败只给朱砂提示，不崩、不留空按钮；
 * - 入参键与旧网页画板版逐点对齐：bookName / chapterName / pageIndex / pageText / bgImage。
 */
class PaintActivity : ComponentActivity() {

    private var state by mutableStateOf(InkSlateState.Empty)
    private var bgBitmap by mutableStateOf<Bitmap?>(null)
    private var errorNotice by mutableStateOf<String?>(null)
    private var showExportPanel by mutableStateOf(false)
    private var textAnchor by mutableStateOf<StrokePoint?>(null)

    private val bookName: String by lazy { intent.getStringExtra(EXTRA_BOOK_NAME).orEmpty() }
    private val chapterName: String by lazy { intent.getStringExtra(EXTRA_CHAPTER_NAME).orEmpty() }
    private val pageIndex: Int by lazy { intent.getIntExtra(EXTRA_PAGE_INDEX, 0) }
    private val pageText: String by lazy { intent.getStringExtra(EXTRA_PAGE_TEXT).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindAnnotation()
        loadBackground()
        setContent {
            SlateScreen(
                state = state,
                bgBitmap = bgBitmap,
                errorNotice = errorNotice,
                showExportPanel = showExportPanel,
                textAnchor = textAnchor,
                onState = { state = it },
                onExportPanelToggle = { showExportPanel = it },
                onExportPng = { exportPng() },
                onExportSvg = { exportSvg() },
                onTextAnchor = { textAnchor = it },
                onConfirmText = { input -> commitText(input) },
                onNoticeDismiss = { errorNotice = null },
            )
        }
        overridePendingTransition(R.anim.ink_slide_in, R.anim.ink_none)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.ink_none, R.anim.ink_slide_out)
    }

    /** 批注元信息：拼接口径与页码换算收口在 [AnnotationMeta.fromReadContext]；自由画板不带批注。 */
    private fun bindAnnotation() {
        val meta = AnnotationMeta.fromReadContext(
            bookName = bookName,
            chapterName = chapterName,
            pageIndex = pageIndex,
            plainText = pageText.take(PLAIN_TEXT_LIMIT),
            bgImageRef = intent.getStringExtra(EXTRA_BG_IMAGE),
        ) ?: return
        state = state.bindAnnotation(meta)
    }

    /** 正文页截图：先按绝对路径解码，回落旧版 filesDir/annot_bg 目录；解码失败留无底图，不崩。 */
    private fun loadBackground() {
        val path = intent.getStringExtra(EXTRA_BG_IMAGE) ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val file = resolveBgFile(path)
                    BitmapFactory.decodeFile(file.absolutePath)
                }.getOrNull()
            }
            bgBitmap = bitmap
        }
    }

    private fun resolveBgFile(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        val legacy = File(filesDir, "annot_bg/${File(path).name}")
        if (legacy.exists()) return legacy
        return direct
    }

    private fun commitText(input: String) {
        val anchor = textAnchor
        textAnchor = null
        if (anchor == null || input.isBlank()) return
        state = state.beginStroke(anchor.x, anchor.y, 1f).commitText(input.trim())
    }

    private fun exportPng() {
        runCatching {
            val density = resources.displayMetrics.density
            val bitmap = InkSlateBitmap.render(state, bgBitmap, density)
            val file = createExportFile("png")
            val encoded = FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (!encoded) throw IllegalStateException("PNG 编码失败")
            shareFile(file, "image/png")
            errorNotice = null
            toastOnUi("PNG 已导出：${file.name}")
        }.onFailure {
            errorNotice = "PNG 导出失败，请重试"
            toastOnUi("PNG 导出失败：${it.localizedMessage}")
        }
    }

    private fun exportSvg() {
        runCatching {
            val export = InkSlateSvg.build(state)
            val file = createExportFile("svg")
            file.writeText(export.svg, Charsets.UTF_8)
            shareFile(file, "image/svg+xml")
            errorNotice = null
            toastOnUi("SVG 已导出：${file.name}")
        }.onFailure {
            errorNotice = "SVG 导出失败，请重试"
            toastOnUi("SVG 导出失败：${it.localizedMessage}")
        }
    }

    private fun createExportFile(ext: String): File {
        val dir = getExternalFilesDir("paint") ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return File(dir, "ink-$stamp.$ext")
    }

    private fun shareFile(file: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileProvider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, file.name))
    }

    @Composable
    private fun SlateScreen(
        state: InkSlateState,
        bgBitmap: Bitmap?,
        errorNotice: String?,
        showExportPanel: Boolean,
        textAnchor: StrokePoint?,
        onState: (InkSlateState) -> Unit,
        onExportPanelToggle: (Boolean) -> Unit,
        onExportPng: () -> Unit,
        onExportSvg: () -> Unit,
        onTextAnchor: (StrokePoint?) -> Unit,
        onConfirmText: (String) -> Unit,
        onNoticeDismiss: () -> Unit,
    ) {
        LaunchedEffect(errorNotice) {
            if (errorNotice != null) {
                delay(NOTICE_MS)
                onNoticeDismiss()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(ComponentTokens.bgPage)
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Column(Modifier.fillMaxSize()) {
                InkSlateTopBar(state, onState)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    bgBitmap?.let { bmp ->
                        Image(
                            painter = BitmapPainter(bmp.asImageBitmap()),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    InkSlateCanvas(
                        state = state,
                        onState = onState,
                        onTextRequest = { onTextAnchor(it) },
                    )
                }
                InkSlateBottomBar(state, onState, onExport = { onExportPanelToggle(true) })
                if (showExportPanel) {
                    ExportPanel(
                        onPng = {
                            onExportPanelToggle(false)
                            onExportPng()
                        },
                        onSvg = {
                            onExportPanelToggle(false)
                            onExportSvg()
                        },
                        onCancel = { onExportPanelToggle(false) },
                    )
                }
            }

            errorNotice?.let { message ->
                NoticeBar(
                    message = message,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        textAnchor?.let {
            TextInputDialog(
                onConfirm = { input -> onConfirmText(input) },
                onDismiss = { onTextAnchor(null) },
            )
        }
    }

    @Composable
    private fun ExportPanel(
        onPng: () -> Unit,
        onSvg: () -> Unit,
        onCancel: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlateActionButton("存 PNG", accent = true, onClick = onPng)
            SlateActionButton("存 SVG", accent = true, onClick = onSvg)
            SlateActionButton("取消", accent = false, onClick = onCancel)
        }
    }

    @Composable
    private fun NoticeBar(message: String, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ComponentTokens.textAccent)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            BasicText(
                text = message,
                style = TextStyle(
                    color = ComponentTokens.bgPage,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }

    @Composable
    private fun TextInputDialog(
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        var draft by remember { mutableStateOf("") }
        Box(
            Modifier
                .fillMaxSize()
                .background(ComponentTokens.bgOverlay),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(ComponentTokens.bgSurface)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BasicText(
                    text = "题字",
                    style = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                )
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ComponentTokens.textPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SlateActionButton("取消", accent = false, onClick = onDismiss)
                    SlateActionButton("落墨", accent = true, onClick = { onConfirm(draft) })
                }
            }
        }
    }

    @Composable
    private fun SlateActionButton(
        text: String,
        accent: Boolean,
        onClick: () -> Unit,
    ) {
        val gesture = rememberInkGesture(enabled = true, onClick = onClick)
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (accent) ComponentTokens.textAccent else ComponentTokens.bgPage)
                .then(gesture.gestureModifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text,
                style = TextStyle(
                    color = if (accent) ComponentTokens.bgPage else ComponentTokens.textPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                ),
            )
        }
    }

    companion object {
        /** 入参键与旧版逐点对齐，启动方无需改动。 */
        const val EXTRA_BOOK_NAME = "bookName"
        const val EXTRA_CHAPTER_NAME = "chapterName"
        const val EXTRA_PAGE_INDEX = "pageIndex"
        const val EXTRA_PAGE_TEXT = "pageText"
        const val EXTRA_BG_IMAGE = "bgImage"

        /** 正文纯文本带入上限（与旧桥接口径一致）。 */
        private const val PLAIN_TEXT_LIMIT = 2000

        /** 失败提示自动消退时长。 */
        private const val NOTICE_MS = 3000L
    }
}
