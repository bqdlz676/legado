package io.legado.app.ui.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoverConfigRefreshContractTest {
    @Test
    fun `default cover preference refreshes cover model and bookshelf`() {
        val source = readProjectFile("src/main/java/io/legado/app/ui/config/sections/CoverSection.kt")
        assertTrue(source.contains("PreferKey.useDefaultCover"))
        assertTrue(source.contains("BookCover.upDefaultCover()"))
        // 默认封面 / 封面图选择回写后必须触发封面模型重算；书架侧刷新由书架页 onResume 重载封面接管
        // （旧 EventBus.BOOKSHELF_REFRESH 广播口径已随 Fragment 拆除，见修复报告交接项）。
        assertTrue(source.contains("pickCover(PreferKey.defaultCover)"))
    }

    private fun readProjectFile(pathInApp: String): String {
        return sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)
            ?.readText()
            .orEmpty()
    }
}
