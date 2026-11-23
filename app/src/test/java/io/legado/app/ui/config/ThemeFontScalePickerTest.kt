package io.legado.app.ui.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeFontScalePickerTest {

    @Test
    fun `font scale picker keeps the effective configured or system value`() {
        // 切片口径修正：原来用 substringAfter("PreferKey.fontScale") 会把断言里要匹配的
        // "PreferKey.fontScale" 本身切掉，导致断言永远为假（测试自身缺陷，非实现问题）。
        // 改成"从该分区起始行到下一分区之前"，断言内容不变、覆盖强度不降。
        val full = readProjectFile("src/main/java/io/legado/app/ui/config/sections/ThemeSection.kt")
        val from = full.indexOf("ValueRow(stringRes(R.string.font_scale)").let { if (it >= 0) it else 0 }
        val to = full.indexOf("PreferKey.cover_config", from).let { if (it > from) it else full.length }
        val source = full.substring(from, to)

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
