package io.legado.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B18 六态收敛 · 文件分发动作面板闸门断言（纯 JVM，源码静态回证）。
 *
 * 覆盖锚点卡判据 2 第四处：HandleFileSheet 动作行不再裸 clickable，
 * 改走 rememberInkGesture 墨触闸，透明度读契约 contentAlpha。
 */
class B18HandleFileSheetGateTest {

    private val sheetSource: String by lazy {
        File(sourceRoot, "io/legado/app/ui/components/HandleFileSheet.kt").readText()
    }

    @Test
    fun `动作行不再裸 clickable`() {
        val codeLines = sheetSource.lines()
            .filter { !isCommentLine(it) }
            .map { it.substringBefore("//") }
        assertFalse("动作行不得保留裸 .clickable(",
            codeLines.any { it.contains(".clickable(") })
        assertFalse("不得再创建裸 MutableInteractionSource",
            codeLines.any { it.contains("MutableInteractionSource") })
    }

    @Test
    fun `动作行经墨触闸并消费契约 contentAlpha`() {
        assertTrue("动作行必须经 rememberInkGesture 绑手势",
            sheetSource.contains("rememberInkGesture("))
        assertTrue("动作行必须经 toVisualState 取契约可视态",
            sheetSource.contains("toVisualState()"))
        assertTrue("动作行透明度必须读 contentAlpha",
            sheetSource.contains("contentAlpha"))
    }

    private fun isCommentLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
    }

    private val sourceRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }
}
