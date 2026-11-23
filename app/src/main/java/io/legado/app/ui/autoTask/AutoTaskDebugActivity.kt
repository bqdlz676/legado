package io.legado.app.ui.autoTask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.legado.app.data.entities.AutoTaskRule
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRunner
import io.legado.app.model.Debug
import io.legado.app.ui.components.ComponentTokens
import io.legado.app.ui.components.InkEmptyState
import io.legado.app.ui.components.ToolAction
import io.legado.app.ui.components.ToolPageScaffold
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 落文 · 自动任务调试宿主（Compose 自绘壳）。
 *
 * 保留旧 [intent] 入口口径；调试执行仍走 [AutoTaskRunner] 与 [Debug] 回调链，
 * 日志排版由 Compose 承担。兜底：任务不存在 → 关闭；调试忙 → 文案提示；日志截断上限保留。
 */
class AutoTaskDebugActivity : FragmentActivity(), Debug.Callback {

    private var task: AutoTaskRule? = null
    private var debugJob: Job? = null
    private val output = StringBuilder()

    private var outputText by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var taskName by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToolPageScaffold(
                title = taskName.ifBlank { "任务调试" },
                onBack = { finish() },
                actions = listOf(ToolAction("运行", { runDebug() })),
            ) {
                when {
                    outputText.isEmpty() && !busy -> InkEmptyState(
                        message = "暂无调试日志",
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (busy) {
                            androidx.compose.foundation.text.BasicText(
                                "调试运行中…",
                                style = TextStyle(color = ComponentTokens.textTertiary, fontSize = 13.sp),
                            )
                        }
                        androidx.compose.foundation.text.BasicText(
                            outputText,
                            style = TextStyle(
                                color = ComponentTokens.textPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                lineHeight = 20.sp,
                            ),
                        )
                    }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val loaded = intent.getStringExtra(EXTRA_ID)?.let(AutoTask::get)
            withContext(Dispatchers.Main) {
                task = loaded
                if (loaded == null) {
                    finish()
                } else {
                    taskName = loaded.name
                    runDebug()
                }
            }
        }
    }

    private fun runDebug() {
        val current = task ?: return
        debugJob?.cancel()
        Debug.cancelDebug(this)
        output.clear()
        outputText = ""
        val sourceUrl = AutoTask.buildSource(current).bookSourceUrl
        if (!Debug.startSimpleDebug(this, sourceUrl)) {
            toastOnUi(io.legado.app.R.string.auto_task_debug_busy)
            return
        }
        busy = true
        debugJob = lifecycleScope.launch(Dispatchers.IO) {
            val result = AutoTaskRunner.runTask(this@AutoTaskDebugActivity, current, persist = false)
            appendLine(result.log)
            withContext(Dispatchers.Main) {
                Debug.cancelDebug(this@AutoTaskDebugActivity)
                busy = false
                outputText = output.toString()
            }
        }
    }

    override fun printLog(state: Int, msg: String) {
        runOnUiThread {
            appendLine(msg)
            outputText = output.toString()
        }
    }

    private fun appendLine(line: String) {
        if (output.isNotEmpty()) output.append('\n')
        output.append(line)
        if (output.length > MAX_OUTPUT) output.delete(0, output.length - MAX_OUTPUT)
    }

    override fun onDestroy() {
        debugJob?.cancel()
        Debug.cancelDebug(this)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "autoTaskId"
        private const val MAX_OUTPUT = 20_000

        fun intent(context: Context, id: String): Intent =
            Intent(context, AutoTaskDebugActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
