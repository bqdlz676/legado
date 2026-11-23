package io.legado.app.ui.config

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.help.BottomBarSkinManager
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.config.settings.InkBottomSheetHost
import io.legado.app.ui.config.settings.SettingRowShell
import io.legado.app.ui.config.settings.SettingScaffold
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 底栏图集分配页（新写 Compose 宿主，替代旧 RecyclerView + RowAdapter/PaletteAdapter + Binding）。
 *
 * 4 个底栏槽位各选「选中（必选）/ 未选（可空）」图；素材来自 [BottomBarSkinManager] 暂存目录，
 * 保存逻辑仍走 [BottomBarSkinAssignViewModel] → [BottomBarSkinManager.saveSkin]。
 */
class BottomBarSkinAssignActivity : AppCompatActivity() {

    private val viewModel by viewModels<BottomBarSkinAssignViewModel>()

    private val sessionId: String by lazy { intent.getStringExtra("sessionId").orEmpty() }
    private val editName: String? by lazy { intent.getStringExtra("editName") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (sessionId.isEmpty()) {
            toastOnUi(R.string.bottom_bar_skin_invalid)
            finish()
            return
        }
        setContent {
            SettingScaffold(
                title = getString(if (editName != null) R.string.edit else R.string.bottom_bar_skin_import),
                onBack = {
                    if (!viewModel.isSaving) finish()
                },
            ) {
                AssignScreen(
                    sessionId = sessionId,
                    initialName = intent.getStringExtra("name").orEmpty(),
                    editName = editName,
                    viewModel = viewModel,
                    onSaved = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing && !viewModel.isSaving && sessionId.isNotEmpty()) {
            BottomBarSkinManager.discardSession(sessionId)
        }
        super.onDestroy()
    }
}

private data class RowState(
    val slot: String,
    val labelRes: Int,
    var selected: File?,
    var normal: File?,
)

@Composable
private fun AssignScreen(
    sessionId: String,
    initialName: String,
    editName: String?,
    viewModel: BottomBarSkinAssignViewModel,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScopeCompat()
    var name by remember { mutableStateOf(initialName) }
    var rows by remember { mutableStateOf<List<RowState>>(emptyList()) }
    var palette by remember { mutableStateOf<List<File>>(emptyList()) }
    var thumbs by remember { mutableStateOf<Map<File, Bitmap>>(emptyMap()) }
    var paletteFor by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    val slotLabels = linkedMapOf(
        "bookshelf" to R.string.bookshelf,
        "home" to R.string.discovery,
        "notes" to R.string.rss,
        "settings" to R.string.my,
    )

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val imgs = withContext(Dispatchers.IO) { BottomBarSkinManager.stagingImages(sessionId) }
        val t = withContext(Dispatchers.IO) {
            imgs.mapNotNull { f -> BottomBarSkinManager.previewBitmap(f, 56.dpToPxInt())?.let { f to it } }.toMap()
        }
        val decodable = t.keys.toList()
        if (decodable.isEmpty()) {
            context.toastOnUi(context.getString(R.string.bottom_bar_skin_no_images))
            return@LaunchedEffect
        }
        val prefill = withContext(Dispatchers.IO) { BottomBarSkinManager.buildPrefill(decodable) }
        thumbs = t
        palette = decodable
        rows = slotLabels.entries.map { (slot, labelRes) ->
            RowState(slot, labelRes, prefill[slot]?.selected, prefill[slot]?.normal)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = Observer<BottomBarSkinAssignViewModel.SaveOutcome?> { outcome ->
            outcome ?: return@Observer
            viewModel.consume(outcome)
            if (outcome.name != null) onSaved() else context.toastOnUi(R.string.bottom_bar_skin_invalid)
        }
        viewModel.saveResult.observe(lifecycleOwner, observer)
        onDispose { viewModel.saveResult.removeObserver(observer) }
    }

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = ComponentTokens.textPrimary,
                    fontSize = 16.sp(),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                ),
            )
        }
        items(rows.size) { index ->
            val row = rows[index]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicText(
                        text = context.getString(row.labelRes),
                        style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 16.sp()),
                    )
                }
                Thumb(row.selected, enabled = true) { paletteFor = index to true }
                Spacer(Modifier.size(12.dp))
                Thumb(row.normal, enabled = row.selected != null) { paletteFor = index to false }
            }
        }
        item {
            io.legado.app.ui.components.InkButton(context.getString(R.string.bottom_bar_skin_save), modifier = Modifier.fillMaxWidth()) {
                val assigns = LinkedHashMap<String, BottomBarSkinManager.SlotAssign>()
                rows.forEach { r ->
                    r.selected?.let { sel -> assigns[r.slot] = BottomBarSkinManager.SlotAssign(sel, r.normal) }
                }
                if (assigns.isEmpty()) {
                    context.toastOnUi(R.string.bottom_bar_skin_need_selected)
                    return@InkButton
                }
                viewModel.save(name.trim(), assigns, sessionId, editName)
            }
        }
    }

    paletteFor?.let { (index, isSelected) ->
        InkBottomSheetHost(title = context.getString(rows[index].labelRes), onDismiss = { paletteFor = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rows[index].selected != null) {
                    io.legado.app.ui.components.InkButton(context.getString(R.string.clear), variant = io.legado.app.ui.components.InkButtonVariant.GHOST) {
                        rows = rows.toMutableList().also {
                            it[index].selected = null
                            it[index].normal = null
                        }
                        paletteFor = null
                    }
                }
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(72.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(palette) { file ->
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ComponentTokens.bgSurface)
                                .pointerInput(file) {
                                    detectTapGestures(onTap = {
                                        rows = rows.toMutableList().also {
                                            if (isSelected) {
                                                it[index].selected = file
                                            } else {
                                                it[index].normal = file
                                            }
                                        }
                                        paletteFor = null
                                    })
                                },
                        ) {
                            thumbs[file]?.let { bmp ->
                                Image(bmp.asImageBitmap(), contentDescription = null, Modifier.size(56.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(file: File?, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ComponentTokens.bgSurface)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = if (file != null) "" else "+",
            style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textTertiary, fontSize = 18.sp()),
        )
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
private fun Int.dpToPxInt() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
