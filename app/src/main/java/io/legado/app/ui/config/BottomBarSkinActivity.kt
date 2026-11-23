package io.legado.app.ui.config

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.BottomBarSkinManager
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.config.settings.ConfirmSheet
import io.legado.app.ui.config.settings.InkBottomSheetHost
import io.legado.app.ui.config.settings.SettingScaffold
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileDoc
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * 底栏图集列表页（新写 Compose 宿主，替代旧 RecyclerView + SkinAdapter + Binding）。
 *
 * 图集数据 / 预览位图 / 导入导出复用 [BottomBarSkinManager]；默认图集用内置图标占位。
 * 点选切换在用图集，长按弹编辑 / 导出 / 分享 / 删除。
 */
class BottomBarSkinActivity : AppCompatActivity() {

    private var importSession: AtomicReference<String?> = AtomicReference(null)

    private val importDoc = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val extracted = withContext(Dispatchers.IO) {
                uri.inputStream(this@BottomBarSkinActivity).mapCatching { input ->
                    input.use { BottomBarSkinManager.extractImages(it).getOrThrow() }
                }.also { r -> importSession.set(r.getOrNull()) }
            }
            if (extracted.isFailure) {
                toastOnUi(R.string.bottom_bar_skin_invalid)
                return@launch
            }
            val displayName = withContext(Dispatchers.IO) {
                runCatching { FileDoc.fromUri(uri, false).name }.getOrNull().orEmpty()
                    .substringBeforeLast('.')
            }
            val sessionId = extracted.getOrThrow()
            startActivity(
                Intent(this@BottomBarSkinActivity, BottomBarSkinAssignActivity::class.java)
                    .putExtra("name", displayName)
                    .putExtra("sessionId", sessionId),
            )
            importSession.getAndSet(null)?.let { sid ->
                withContext(Dispatchers.IO) { BottomBarSkinManager.discardSession(sid) }
            }
        }
    }

    private val exportDoc = registerForActivityResult(HandleFileContract()) { result ->
        if (result.uri != null) toastOnUi(R.string.export_success)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingScaffold(title = getString(R.string.bottom_bar_skin), onBack = { finish() }) {
                SkinList(
                    onImport = {
                        importDoc.launch {
                            mode = HandleFileContract.FILE
                            title = getString(R.string.bottom_bar_skin_import)
                            allowExtensions = arrayOf("zip", "ZIP")
                        }
                    },
                    onExport = { name ->
                        lifecycleScope.launch {
                            val bytes = withContext(Dispatchers.IO) { BottomBarSkinManager.buildZipBytes(name) }.getOrNull()
                            if (bytes == null) {
                                toastOnUi(R.string.bottom_bar_skin_invalid)
                                return@launch
                            }
                            exportDoc.launch {
                                mode = HandleFileContract.EXPORT
                                fileData = HandleFileContract.FileData("$name.zip", bytes, "application/zip")
                            }
                        }
                    },
                    onShare = { name ->
                        lifecycleScope.launch {
                            val file = withContext(Dispatchers.IO) { BottomBarSkinManager.cacheShareZip(name) }.getOrNull()
                            if (file == null) toastOnUi(R.string.bottom_bar_skin_invalid) else share(file, "application/zip")
                        }
                    },
                    onDelete = { name ->
                        lifecycleScope.launch {
                            if (withContext(Dispatchers.IO) { BottomBarSkinManager.delete(name) }) {
                                postEvent(EventBus.BOTTOM_BAR_SKIN, "")
                            } else {
                                toastOnUi(R.string.bottom_bar_skin_invalid)
                            }
                        }
                    },
                    onEdit = { name ->
                        lifecycleScope.launch {
                            val staged = withContext(Dispatchers.IO) { BottomBarSkinManager.stageExisting(name) }
                            val sessionId = staged.getOrNull()
                            if (sessionId == null) {
                                toastOnUi(R.string.bottom_bar_skin_invalid)
                                return@launch
                            }
                            startActivity(
                                Intent(this@BottomBarSkinActivity, BottomBarSkinAssignActivity::class.java)
                                    .putExtra("name", name)
                                    .putExtra("editName", name)
                                    .putExtra("sessionId", sessionId),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SkinList(
    onImport: () -> Unit,
    onExport: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var previews by remember { mutableStateOf<Map<String, List<Bitmap>>>(emptyMap()) }
    var active by remember { mutableStateOf(BottomBarSkinManager.active) }
    var menuName by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { BottomBarSkinManager.list() }
        val maps = withContext(Dispatchers.IO) {
            loaded.associateWith { BottomBarSkinManager.getPreviewBitmaps(it, 24.dpToPxInt()) }
        }
        names = loaded
        previews = maps
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SkinCard(
                title = context.getString(R.string.bottom_bar_skin_default),
                bitmaps = emptyList(),
                active = active.isEmpty(),
                isDefault = true,
                onClick = {
                    BottomBarSkinManager.active = ""
                    active = ""
                    postEvent(EventBus.BOTTOM_BAR_SKIN, "")
                },
                onLongClick = {},
            )
        }
        items(names) { name ->
            SkinCard(
                title = name,
                bitmaps = previews[name].orEmpty(),
                active = active == name,
                isDefault = false,
                onClick = {
                    BottomBarSkinManager.active = name
                    active = name
                    postEvent(EventBus.BOTTOM_BAR_SKIN, "")
                },
                onLongClick = { menuName = name },
            )
        }
    }

    menuName?.let { name ->
        InkBottomSheetHost(title = name, onDismiss = { menuName = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                io.legado.app.ui.components.InkButton(context.getString(R.string.edit), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY) { menuName = null; onEdit(name) }
                io.legado.app.ui.components.InkButton(context.getString(R.string.export), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY) { menuName = null; onExport(name) }
                io.legado.app.ui.components.InkButton(context.getString(R.string.share), variant = io.legado.app.ui.components.InkButtonVariant.SECONDARY) { menuName = null; onShare(name) }
                io.legado.app.ui.components.InkButton(context.getString(R.string.delete), variant = io.legado.app.ui.components.InkButtonVariant.GHOST) { menuName = null; confirmDelete = name }
            }
        }
    }

    confirmDelete?.let { name ->
        ConfirmSheet(
            visible = true,
            title = context.getString(R.string.delete),
            message = context.getString(R.string.bottom_bar_skin_delete_confirm),
            danger = true,
            onConfirm = { onDelete(name) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun SkinCard(
    title: String,
    bitmaps: List<Bitmap>,
    active: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) ComponentTokens.brandGold.copy(alpha = 0.10f) else ComponentTokens.bgSurface)
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            }
            .padding(14.dp),
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                bitmaps.forEach { bmp ->
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(24.dp))
                }
                if (isDefault) {
                    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                        drawCircle(ComponentTokens.textTertiary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.text.BasicText(
                text = title,
                style = androidx.compose.ui.text.TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 14.sp(),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                ),
            )
        }
    }
}

private fun Int.dpToPxInt() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
