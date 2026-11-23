package io.legado.app.ui.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeFontScalePickerTest {

    @Test
    fun `font scale picker keeps the effective configured or system value`() {
        val source = readProjectFile("app/src/main/java/io/legado/app/ui/config/sections/ThemeSection.kt")
            .substringAfter("PreferKey.fontScale")
            .substringBefore("PreferKey.cover_config")

        // 已配置值优先；档位边界保持 8..16；不得在弹窗上写死初始 10。
        assertTrue(source.contains("context.getPrefInt(PreferKey.fontScale, 0)"))
        assertTrue(source.contains("NumberTarget(R.string.font_scale, 8, 16, scale)"))
        assertFalse(source.contains(".setValue(10)"))
    }

    private fun readProjectFile(pathInApp: String): String {
        return sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)
            ?.readText()
            .orEmpty()
    }
}
