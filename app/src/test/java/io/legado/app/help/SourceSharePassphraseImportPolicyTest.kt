package io.legado.app.help

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceSharePassphraseImportPolicyTest {

    @Test
    fun resumeCheckRequiresPrivacyConsent() {
        assertTrue(SourceSharePassphraseImportPolicy.shouldScheduleOnResume(true))
        assertFalse(SourceSharePassphraseImportPolicy.shouldScheduleOnResume(false))
    }

    @Test
    fun focusRetryRequiresActiveMainActivity() {
        assertTrue(canAwaitWindowFocus())
        assertFalse(canAwaitWindowFocus(isResumed = false))
    }

    @Test
    fun clipboardReadRequiresFocusedActiveUnsavedActivity() {
        assertTrue(canReadClipboard())
        assertFalse(canReadClipboard(privacyPolicyOk = false))
        assertFalse(canReadClipboard(isFinishing = true))
        assertFalse(canReadClipboard(isResumed = false))
        assertFalse(canReadClipboard(isFragmentStateSaved = true))
        assertFalse(canReadClipboard(hasWindowFocus = false))
    }

    @Test
    fun pausedActivityInvalidatesQueuedRead() {
        val source = sequenceOf(
            File("src/main/java/io/legado/app/ui/main/MainActivity.kt"),
            File("app/src/main/java/io/legado/app/ui/main/MainActivity.kt")
        ).firstOrNull(File::isFile)?.readText().orEmpty()
        val onPause = source.section(
            "override fun onPause()",
            "override fun onWindowFocusChanged"
        )
        // 结束锚不能用另一个成员名：成员顺序会随重构变化（Q-330 把 upBottomBarSkin 提到 readSourceSharePassphrase 之前，
        // 旧锚点在 CI 直接抛 Missing section end）。改为「起点 + 定长窗口」，断言强度不变、与成员排序解耦。
        val delayedRead = source.window("private fun readSourceSharePassphrase(", 4000)

        assertTrue(onPause.contains("pendingPassphraseRead = false"))
        assertTrue(onPause.contains("passphraseReadGeneration++"))
        assertTrue(delayedRead.contains("generation != passphraseReadGeneration"))
    }

    private fun canAwaitWindowFocus(
        privacyPolicyOk: Boolean = true,
        isFinishing: Boolean = false,
        isResumed: Boolean = true,
        isFragmentStateSaved: Boolean = false
    ) = SourceSharePassphraseImportPolicy.canAwaitWindowFocus(
        privacyPolicyOk,
        isFinishing,
        isResumed,
        isFragmentStateSaved
    )

    private fun canReadClipboard(
        privacyPolicyOk: Boolean = true,
        isFinishing: Boolean = false,
        isResumed: Boolean = true,
        isFragmentStateSaved: Boolean = false,
        hasWindowFocus: Boolean = true
    ) = SourceSharePassphraseImportPolicy.canReadClipboard(
        privacyPolicyOk,
        isFinishing,
        isResumed,
        isFragmentStateSaved,
        hasWindowFocus
    )

    /**
     * 从 startMarker 起截取固定窗口：用于断言「某个函数体内含某行」，不依赖后续成员名是否存在或顺序。
     * 适用条件：被断言的行必须落在该函数开头的一段有限范围内（本用例为 launch 体内前几行）。
     */
    private fun String.window(startMarker: String, length: Int): String {
        val start = indexOf(startMarker)
        assertTrue("Missing marker: $startMarker", start >= 0)
        val end = (start + length).coerceAtMost(this.length)
        return substring(start, end)
    }

    private fun String.section(startMarker: String, endMarker: String): String {
        val start = indexOf(startMarker)
        val end = indexOf(endMarker, start + startMarker.length)
        assertTrue("Missing section start: $startMarker", start >= 0)
        assertTrue("Missing section end: $endMarker", end > start)
        return substring(start, end)
    }
}
