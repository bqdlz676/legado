package io.legado.app.ui.about

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import io.legado.app.utils.showDialogFragment
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadRecordAuthors
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.help.book.ReadRecordCoverCache
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkButton
import io.legado.app.ui.components.InkButtonVariant
import io.legado.app.ui.main.bookshelf.ShelfSearchRouter
import io.legado.app.utils.cnCompare
import io.legado.app.utils.getPrefString
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

internal fun formatDuring(mss: Long, useDays: Boolean = false, showSeconds: Boolean = true): String {
    val totalHours = mss / (1000 * 60 * 60)
    val days = if (useDays) totalHours / 24 else 0
    val hours = if (useDays) totalHours % 24 else totalHours
    val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
    val seconds = mss % (1000 * 60) / 1000
    val h = if (hours > 0) "${hours}小时" else ""
    val d = if (days > 0) "${days}天" else ""
    val m = if (minutes > 0) "${minutes}分钟" else ""
    val s = if (showSeconds && seconds > 0) "${seconds}秒" else ""
    return "$d$h$m$s".ifBlank { if (showSeconds) "0秒" else "0分钟" }
}

/**
 * 阅读记录页（新写 Compose 宿主，类名保留供 My 页 startActivity）。
 *
 * 数据仍走 readRecordDao / bookDao；列表、搜索、排序、布局与时长开关逐条对照旧实现。
 * 旧内部 RecordAdapter 已移除；封面复用 ImageLoader 加载。
 */
class ReadRecordActivity : AppCompatActivity() {

    private var searchKey by mutableStateOf("")
    private var sortMode by mutableStateOf(LocalConfig.getInt("readRecordSort", 0))
    private var records by mutableStateOf<List<ReadRecordShow>>(emptyList())
    private var allRecords by mutableStateOf<List<ReadRecordShow>>(emptyList())
    private var booksByIdentity by mutableStateOf<Map<Pair<String, String>, Book>>(emptyMap())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setContent {
                RecordScreen(
                    activity = this@ReadRecordActivity,
                    searchKey = searchKey,
                    onSearch = { searchKey = it; loadRecords(it) },
                    sortMode = sortMode,
                    onSortMode = { sortMode = it; LocalConfig.edit { putInt("readRecordSort", it) }; loadRecords() },
                    records = records,
                    allRecords = allRecords,
                    booksByIdentity = booksByIdentity,
                    onOpen = ::openBook,
                    onEdit = ::openEdit,
                    onDelete = ::confirmDelete,
                    onClearAll = ::clearRecords,
                )
            }
        })
        loadRecords()
    }

    override fun onResume() {
        super.onResume()
        loadRecords()
    }

    internal fun onRecordMetadataSaved() = loadRecords()

    private fun loadRecords(key: String? = searchKey) {
        lifecycleScope.launch {
            val (all, sorted, books) = withContext(Dispatchers.IO) {
                val shelf = appDb.bookDao.all.associateBy { it.name to it.author }
                val allShow = appDb.readRecordDao.allShow
                val filtered = if (key.isNullOrBlank()) allShow else appDb.readRecordDao.search(key)
                val sortedList = when (sortMode) {
                    1 -> filtered.sortedByDescending { it.readTime }
                    2 -> filtered.sortedByDescending { it.lastRead }
                    else -> filtered.sortedWith { o1, o2 ->
                        o1.bookName.cnCompare(o2.bookName).takeIf { it != 0 }
                            ?: o1.displayAuthor.cnCompare(o2.displayAuthor)
                    }
                }
                Triple(allShow, sortedList, shelf)
            }
            allRecords = all
            records = sorted
            booksByIdentity = books
        }
    }

    private fun openBook(item: ReadRecordShow) {
        lifecycleScope.launch {
            val book = withContext(Dispatchers.IO) {
                appDb.bookDao.findByName(item.bookName)
                    .filter { it.author == item.author }
                    .maxByOrNull { it.durChapterTime }
            }
            if (book == null) ShelfSearchRouter.openShelfSearch(this@ReadRecordActivity, item.bookName)
            else startActivityForBook(book)
        }
    }

    private fun openEdit(item: ReadRecordShow) {
        showDialogFragment(ReadRecordEditDialog().apply {
            arguments = android.os.Bundle().apply {
                putString("bookName", item.bookName)
                putString("author", item.author)
            }
        })
    }

    private fun confirmDelete(item: ReadRecordShow) {
        alert(R.string.delete) {
            val author = item.displayAuthor.ifBlank { getString(R.string.read_record_no_author) }
            setMessage(getString(R.string.sure_del_any, "${item.bookName} · $author"))
            yesButton {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        appDb.readRecordDao.deleteByBook(item.bookName, item.author)
                        ReadRecordCoverCache.prune()
                    }
                    loadRecords()
                }
            }
            noButton()
            if (item.hasCombinedAuthors && ReadRecordAuthors.decode(item.author).size > 1) {
                neutralButton(R.string.read_record_remove_author) { removeAuthor(item) }
            }
        }
    }

    private fun removeAuthor(item: ReadRecordShow) {
        val authors = ReadRecordAuthors.decode(item.author).toList()
        alert(R.string.read_record_remove_author) {
            items(authors) { _, index ->
                val removed = authors[index]
                alert(R.string.read_record_remove_author) {
                    setMessage(getString(R.string.read_record_remove_author_confirm, removed))
                    yesButton {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                appDb.readRecordDao.removeLegacyAuthor(item.bookName, item.author, removed)
                                ReadRecordCoverCache.prune()
                            }
                            loadRecords()
                        }
                    }
                    noButton()
                }
            }
            noButton()
        }
    }

    private fun clearRecords() {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        appDb.readRecordDao.clear()
                        ReadRecordCoverCache.prune()
                    }
                    loadRecords()
                }
            }
            noButton()
        }
    }
}

@Composable
private fun RecordScreen(
    activity: ReadRecordActivity,
    searchKey: String,
    onSearch: (String) -> Unit,
    sortMode: Int,
    onSortMode: (Int) -> Unit,
    records: List<ReadRecordShow>,
    allRecords: List<ReadRecordShow>,
    booksByIdentity: Map<Pair<String, String>, Book>,
    onOpen: (ReadRecordShow) -> Unit,
    onEdit: (ReadRecordShow) -> Unit,
    onDelete: (ReadRecordShow) -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    val useDays = AppConfig.readRecordUseDays
    val showSeconds = AppConfig.readRecordShowSeconds
    val simple = AppConfig.readRecordSimpleLayout
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val totalDuration = formatDuring(allRecords.sumOf { it.readTime }, useDays, showSeconds)

    Column(Modifier.fillMaxSize().background(ComponentTokens.bgPage)) {
        // 顶栏：返回 + 标题 + 搜索
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            InkButton(context.getString(R.string.back), variant = InkButtonVariant.GHOST) { activity.finish() }
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchKey,
                onValueChange = onSearch,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
                decorationBox = { inner ->
                    Box(Modifier.padding(8.dp)) {
                        if (searchKey.isEmpty()) {
                            androidx.compose.foundation.text.BasicText(
                                text = context.getString(R.string.search),
                                style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 14.sp14()),
                            )
                        }
                        inner()
                    }
                },
            )
        }

        // 汇总卡
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            androidx.compose.foundation.text.BasicText(
                text = context.getString(R.string.read_record_book_count, allRecords.size),
                style = TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
            )
            androidx.compose.foundation.text.BasicText(
                text = context.getString(R.string.read_record_total_duration, totalDuration),
                style = TextStyle(color = ComponentTokens.textSecondary, fontSize = 13.sp13()),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                SortChip(context.getString(R.string.sort_by_name), sortMode == 0) { onSortMode(0) }
                SortChip(context.getString(R.string.reading_time_sort), sortMode == 1) { onSortMode(1) }
                SortChip(context.getString(R.string.last_read_time_sort), sortMode == 2) { onSortMode(2) }
                InkButton(context.getString(R.string.delete), variant = InkButtonVariant.GHOST, onClick = onClearAll)
            }
        }

        LazyColumn(Modifier.padding(top = 12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(records) { item ->
                RecordRow(
                    record = item,
                    simple = simple,
                    useDays = useDays,
                    showSeconds = showSeconds,
                    chapter = booksByIdentity[item.bookName to item.author]?.durChapterTitle,
                    dateFormat = dateFormat,
                    onOpen = { onOpen(item) },
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                )
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    InkButton(label, variant = if (selected) InkButtonVariant.PRIMARY else InkButtonVariant.GHOST, onClick = onClick)
}

@Composable
private fun RecordRow(
    record: ReadRecordShow,
    simple: Boolean,
    useDays: Boolean,
    showSeconds: Boolean,
    chapter: String?,
    dateFormat: SimpleDateFormat,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val author = if (record.hasCombinedAuthors) {
        context.getString(R.string.read_record_legacy_authors, record.displayAuthor)
    } else {
        record.displayAuthor.ifBlank { context.getString(R.string.read_record_no_author) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { onOpen() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!simple) {
            CoverThumb(record.coverUrl)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            androidx.compose.foundation.text.BasicText(
                text = record.bookName,
                style = TextStyle(color = ComponentTokens.textPrimary, fontSize = 15.sp15()),
            )
            androidx.compose.foundation.text.BasicText(
                text = context.getString(R.string.author_show, author),
                style = TextStyle(color = ComponentTokens.textSecondary, fontSize = 13.sp13()),
            )
            if (!simple) {
                androidx.compose.foundation.text.BasicText(
                    text = chapter?.takeIf { it.isNotBlank() } ?: record.lastChapterTitle
                        ?: context.getString(R.string.read_record_no_chapter),
                    style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 12.sp12()),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.text.BasicText(
                    text = formatDuring(record.readTime, useDays, showSeconds),
                    style = TextStyle(color = ComponentTokens.textAccent, fontSize = 12.sp12()),
                )
                if (record.lastRead > 0) {
                    androidx.compose.foundation.text.BasicText(
                        text = dateFormat.format(record.lastRead),
                        style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 12.sp12()),
                    )
                }
            }
        }
        InkButton(context.getString(R.string.delete), variant = InkButtonVariant.GHOST, onClick = onDelete)
    }
}

@Composable
private fun CoverThumb(coverUrl: String?) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
        factory = { ImageView(it) },
        update = { image ->
            ImageLoader.load(context, coverUrl).into(image)
        },
    )
}

private fun Int.sp15() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp14() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp13() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private fun Int.sp12() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
