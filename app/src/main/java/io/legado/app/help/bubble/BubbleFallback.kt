package io.legado.app.help.bubble

import android.content.Intent
import android.net.Uri
import io.legado.app.help.chrono.ContextAnchor
import java.net.URLEncoder

/**
 * Missing 兜底入口的真实接线（规格 §2：全空走 Missing，允许「查网络 / 加入生字本」）。
 *
 * 为什么单列一层：兜底动作不是导航动作，不进 [Action] sealed 四类；
 * UI 读到 [BubbleContent.allowWebSearch] / [BubbleContent.allowNotebook] 后回调这里。
 *
 * - 联网检索：拼通用搜索引擎 URL，走 [Intent.ACTION_VIEW] 交给浏览器 / 搜索 App；
 *   无 Context（纯 JVM 单测）时返回 null，不抛异常。
 * - 生字本：落 [NotebookStore] 本地表；书名 / 章节缺省时回写 [ContextAnchor] 共享总线里的
 *   当前书 / 章，保证兜底词条带得上出处。
 */
object BubbleFallback {

    /** 联网检索 URL（百度网页搜索）；词条做 URL 编码，避免空格 / 中文破坏链接。 */
    fun webSearchUrl(token: String): String {
        val q = URLEncoder.encode(token.trim(), "UTF-8")
        return "https://www.baidu.com/s?wd=$q"
    }

    /** 装配联网检索 Intent；无可用 Context 返回 null（UI 据此禁用按钮，不崩）。 */
    fun webSearchIntent(token: String): Intent? {
        val ctx = BubbleEnv.ctx() ?: return null
        if (token.isBlank()) return null
        return runCatching {
            Intent(Intent.ACTION_VIEW, Uri.parse(webSearchUrl(token)))
        }.getOrNull()
    }

    /**
     * 加入生字本；落库成功返回 true，重复条目 / 空词条 / 无 Context 返回 false。
     * 书名 / 章节标题缺省时取共享锚点当前书 / 章。
     */
    fun saveToNotebook(
        token: String,
        bookName: String = ContextAnchor.bookId,
        chapterTitle: String = ContextAnchor.title,
    ): Boolean {
        val ctx = BubbleEnv.ctx() ?: return false
        return runCatching {
            NotebookStore.add(ctx, token, bookName, chapterTitle)
        }.getOrDefault(false)
    }
}
