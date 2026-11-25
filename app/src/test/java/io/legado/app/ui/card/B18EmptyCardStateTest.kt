package io.legado.app.ui.card

import io.legado.app.ui.components.contract.ComponentVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B18 六态收敛 · legacy 空态卡状态映射与 0.35 裁决断言（纯 JVM）。
 *
 * 覆盖锚点卡判据 4（自持状态收敛到契约六态，不立第二套「能不能点」）与
 * 判据 5（离线局部 0.35 压淡与契约 OFFLINE=1f 冲突，裁决：契约单源，0.35 移除）。
 *
 * 说明：EmptyStateCard.kt 含 Compose 注解，单测不直接加载其 Composable；映射口径以
 * 源码条款 + 契约枚举值双侧断言回证，避免在 JVM 单测里拉起 Compose 运行时。
 */
class B18EmptyCardStateTest {

    private val emptyCardSource: String by lazy {
        File(sourceRoot, "io/legado/app/ui/card/EmptyStateCard.kt").readText()
    }

    // ─────────────────────────── 判据 4：自持状态映射统一六态 ───────────────────────────

    @Test
    fun `空态卡映射覆盖 禁用 离线 空 加载 四档`() {
        val clauses = emptyCardSource.lines().filter { it.startsWith("    ") }
        assertTrue("Disabled 档必须映射契约 DISABLED",
            clauses.any { it.contains("CardState.Disabled") && it.contains("DISABLED") })
        assertTrue("Offline 变体必须映射契约 OFFLINE",
            clauses.any { it.contains("EmptyKind.Offline") && it.contains("OFFLINE") })
        assertTrue("NotFound 变体必须映射契约 EMPTY",
            clauses.any { it.contains("EmptyKind.NotFound") && it.contains("EMPTY") })
        assertTrue("Loading 档必须映射契约 LOADING",
            clauses.any { it.contains("CardState.Loading") && it.contains("LOADING") })
    }

    @Test
    fun `行动按钮走墨触闸 许可来自契约 eventsAllowed`() {
        assertTrue("按钮必须经 rememberInkGesture 绑手势",
            emptyCardSource.contains("rememberInkGesture("))
        assertTrue("按钮必须读契约 eventsAllowed 作为许可",
            emptyCardSource.contains("visual.eventsAllowed"))
        val codeLines = emptyCardSource.lines()
            .filter { !isCommentLine(it) }
            .map { it.substringBefore("//") }
        assertFalse("按钮不得保留裸 clickable",
            codeLines.any { it.contains(".clickable(") })
    }

    @Test
    fun `卡片内不存在第二套独立可点布尔`() {
        // 除契约映射产出的 visual.eventsAllowed 外，不允许再出现 enabled/isClickable 之类自持开关
        val codeLines = emptyCardSource.lines()
            .filter { !isCommentLine(it) }
            .map { it.substringBefore("//") }
        assertFalse(
            "空态卡不得另立 isClickable / interactive 布尔",
            codeLines.any { Regex("isClickable|interactive\\s*=").containsMatchIn(it) },
        )
    }

    // ─────────────────────────── 判据 5：0.35 裁决 ───────────────────────────

    @Test
    fun `离线局部三零点三五压淡已移除 代码行无残留`() {
        val codeLines = emptyCardSource.lines()
            .filter { !isCommentLine(it) }
            .map { it.substringBefore("//") }
        assertFalse("离线文字 / 断环不得再 copy(alpha = 0.35f)",
            codeLines.any { it.contains("0.35f") })
    }

    @Test
    fun `契约离线档保持 1f 不压淡 事件仍禁点 枚举语义未改`() {
        assertEquals(1f, ComponentVisualState.OFFLINE.contentAlpha, 1e-6f)
        assertFalse(ComponentVisualState.OFFLINE.eventsAllowed)
        assertEquals(1f, ComponentVisualState.EMPTY.contentAlpha, 1e-6f)
        assertTrue(ComponentVisualState.EMPTY.eventsAllowed)
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
