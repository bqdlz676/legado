package io.legado.app.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadRecordAuthors
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.contract.ComponentVisualState
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读记录元信息编辑弹窗（新写 Compose 宿主，类名保留供长按行调用）。
 *
 * 重命名 / 改作者走 [ReadRecordEditModel] → `readRecordDao.renameBook`；旧 Binding 已移除。
 */
class ReadRecordEditDialog : DialogFragment() {

    private val model by viewModels<ReadRecordEditModel>()
    private val oldName get() = requireArguments().getString("bookName").orEmpty()
    private val oldAuthor get() = requireArguments().getString("author").orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            EditRecordDialog(
                initialName = savedInstanceState?.getString("name") ?: oldName,
                initialAuthor = savedInstanceState?.getString("author") ?: ReadRecordAuthors.display(oldAuthor),
                saving = model.saving.value == true,
                error = model.error.value,
                onSave = { name, author ->
                    model.save(oldName, oldAuthor, name,
                        if (author == ReadRecordAuthors.display(oldAuthor)) oldAuthor else author)
                },
                onDismiss = { dismissAllowingStateLoss() },
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.saved.observe(viewLifecycleOwner) {
            if (it) {
                (activity as? ReadRecordActivity)?.onRecordMetadataSaved()
                dismissAllowingStateLoss()
            }
        }
        model.error.observe(viewLifecycleOwner) { /* 错误文本在表单内展示 */ }
    }
}

@Composable
private fun EditRecordDialog(
    initialName: String,
    initialAuthor: String,
    saving: Boolean,
    error: String?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var author by remember { mutableStateOf(initialAuthor) }
    var fieldError by remember { mutableStateOf<String?>(error) }

    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.foundation.text.BasicText(
            text = context.getString(R.string.read_record_edit),
            style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 18.sp18()),
        )
        androidx.compose.foundation.text.BasicText(
            text = context.getString(R.string.read_record_edit_summary),
            style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textSecondary, fontSize = 13.sp13()),
        )
        FormField(context.getString(R.string.book_name), name, fieldError) {
            name = it; fieldError = null
        }
        FormField(context.getString(R.string.author), author, null) { author = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            InkButton(
                context.getString(R.string.cancel),
                variant = InkButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
                enabled = !saving,
                state = if (saving) ComponentVisualState.DISABLED else ComponentVisualState.DEFAULT,
                onClick = onDismiss,
            )
            InkButton(
                context.getString(R.string.action_save),
                modifier = Modifier.weight(1f),
                enabled = !saving,
                state = if (saving) ComponentVisualState.LOADING else ComponentVisualState.DEFAULT,
            ) {
                val trimmedName = name.trim()
                val trimmedAuthor = author.trim()
                if (trimmedName.isEmpty()) {
                    fieldError = context.getString(R.string.no_book_name)
                } else {
                    onSave(trimmedName, trimmedAuthor)
                }
            }
        }
    }
}

@Composable
private fun FormField(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textSecondary, fontSize = 13.sp13()),
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
        )
        if (!error.isNullOrBlank()) {
            androidx.compose.foundation.text.BasicText(
                text = error,
                style = androidx.compose.ui.text.TextStyle(color = ComponentTokens.brandCopper, fontSize = 12.sp12()),
            )
        }
    }
}

class ReadRecordEditModel : ViewModel() {
    val saving = MutableLiveData(false)
    val saved = MutableLiveData(false)
    val error = MutableLiveData<String?>()

    fun save(oldName: String, oldAuthor: String, name: String, author: String) {
        if (saving.value == true) return
        saving.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { appDb.readRecordDao.renameBook(oldName, oldAuthor, name, author) }
                saved.value = true
            } catch (e: Exception) {
                error.value = e.localizedMessage ?: e.toString()
                saving.value = false
            }
        }
    }
}

private fun Int.sp18() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp15() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp13() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp12() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
