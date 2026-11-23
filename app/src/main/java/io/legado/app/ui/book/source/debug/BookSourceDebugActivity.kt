package io.legado.app.ui.book.source.debug

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import io.legado.app.R
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.toastOnUi

/**
 * 落文 · 书源调试页宿主（Compose 重写；类名保留以兼容外部入口 `key` extra）。
 *
 * 旧编排：ActivitySourceDebugBinding + BookSourceDebugAdapter + SearchView + 旧菜单，全部拆除；
 * 本类只承担：契约注册、分项原始响应对话框、发现项选择器、帮助文档。调试状态收口在
 * [SourceDebugViewModel]，渲染收口在 [SourceDebugScreen]。
 */
class BookSourceDebugActivity : FragmentActivity(), SourceDebugActions {

    private val vm by viewModels<SourceDebugViewModel>()

    private val qrResult = registerForActivityResult(QrCodeResult()) { text ->
        text?.let { onSubmit(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.uiState.collectAsState()
            SourceDebugScreen(state = state, vm = vm, actions = this@BookSourceDebugActivity)
        }
        vm.init(intent.getStringExtra("key")) { /* 发现项载入在 VM.init 内完成 */ }
    }

    // ───────────────────────────── 宿主动作 ─────────────────────────────

    override fun onBack() = finish()

    override fun onSubmit(key: String) {
        val query = key.trim().ifBlank { return }
        vm.clearLogs()
        vm.startDebug(
            key = query,
            onError = { error -> toastOnUi(error.localizedMessage ?: getString(R.string.error)) },
        )
    }

    override fun onScan() = qrResult.launch(null)

    override fun onViewSrc(kind: DebugSrcKind) {
        val state = vm.uiState.value
        val src = when (kind) {
            DebugSrcKind.SEARCH -> state.searchSrc
            DebugSrcKind.BOOK -> state.bookSrc
            DebugSrcKind.TOC -> state.tocSrc
            DebugSrcKind.CONTENT -> state.contentSrc
        } ?: return
        showDialog(TextDialog("html", src, TextDialog.Mode.MD, showToc = true))
    }

    override fun onRefreshExplore() = vm.refreshExploreKinds()

    override fun onHelp() {
        val mdText = runCatching {
            String(assets.open("web/help/md/debugHelp.md").readBytes())
        }.getOrNull() ?: "帮助文档缺失：debugHelp"
        showDialog(TextDialog(getString(R.string.help), mdText, TextDialog.Mode.MD, showToc = true))
    }

    override fun onPickExplore() {
        val kinds = vm.uiState.value.exploreKinds
        if (kinds.isEmpty()) return
        val titles: List<CharSequence> = kinds.map { it.title.ifBlank { it.url.orEmpty() } }
        selector(getString(R.string.debug_pick_explore), titles) { _, index ->
            val explore = kinds.getOrNull(index) ?: return@selector
            val query = "${explore.title}::${explore.url}"
            vm.setKeyword(query)
            onSubmit(query)
        }
    }

    private fun showDialog(dialog: DialogFragment) {
        dialog.show(supportFragmentManager, dialog::class.simpleName)
    }
}
