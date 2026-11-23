package io.legado.app.ui.login

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LoginEntryRoutingTest {

    @Test
    fun `login entry points use unified login capability`() {
        val paths = listOf(
            // 自动任务列表已 Compose 化，登录入口收口到编辑宿主（旧 AutoTaskAdapter 拆除）。
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditActivity.kt",
            "src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt",
            "src/main/java/io/legado/app/ui/book/audio/AudioPlayActivity.kt",
            "src/main/java/io/legado/app/ui/book/read/ReadMenu.kt",
            // JS 书源编辑的登录能力判定在 JsSourceEditActivity（普通书源编辑壳的门控见报告交接项）。
            "src/main/java/io/legado/app/ui/book/source/edit/JsSourceEditActivity.kt",
            "src/main/java/io/legado/app/ui/video/VideoPlayerActivity.kt",
            "src/main/java/io/legado/app/ui/rss/read/RssJsExtensions.kt",
        )

        paths.forEach { path ->
            assertTrue("$path should use hasLogin()", File(path).readText().contains("hasLogin()"))
        }
    }

    @Test
    fun `auto task management login routes by task id`() {
        val source = File(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditActivity.kt"
        ).readText()

        assertTrue(source.contains("SourceLoginActivity"))
        assertTrue(source.contains("putExtra(\"type\", \"autoTask\")"))
        assertTrue(source.contains("putExtra(\"key\", saved.id)"))
    }
}
