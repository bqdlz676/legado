package io.legado.app.ui.font

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * U9 字体模块契约核对（源码级，不靠运行时）：
 *  - 规格切片-14 的五个角色 / 体积红线 / 字数 / 字号在源码与 manifest 双处一致；
 *  - 子集已实际生成：manifest 状态 GENERATED，README / 脚本口径同步；
 *  - 启动 Activity 已挂接 InkFontLoader.init；
 *  - 统一规范：主代码无 hex 颜色字面量、不引入 Material。
 */
class FontModuleContractTest {

    private fun readProjectFile(path: String): String {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDir)) { it.parentFile }
            .map { File(it, path) }
            .first { it.isFile }
            .readText()
    }

    private fun projectFile(path: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDir)) { it.parentFile }
            .map { File(it, path) }
            .first { it.exists() }
    }

    private val mainDir = "app/src/main/java/io/legado/app/ui/font"
    private val assetsDir = "app/src/main/assets/fonts"

    @Test
    fun `spec constants present in FontRole source`() {
        val src = readProjectFile("$mainDir/FontRole.kt")
        assertTrue(src.contains("200L * 1024"))
        assertTrue(src.contains("2500"))
        assertTrue(src.contains("LABEL_SANS_SIZE_SP: Int = 10"))
        FontRole.entries.forEach { role ->
            assertTrue("源码缺角色 ${role.name}", src.contains(role.name))
        }
    }

    @Test
    fun `manifest matches enum roles and candidates and marks generated`() {
        val manifest = readProjectFile("$assetsDir/manifest.json")
        FontRole.entries.forEach { role ->
            assertTrue("manifest 缺角色 ${role.name}", manifest.contains(role.name))
            role.candidateAssets.forEach { candidate ->
                assertTrue("manifest 缺候选 $candidate", manifest.contains(candidate))
            }
        }
        assertTrue(manifest.contains("GENERATED"))
        assertTrue(manifest.contains("204800"))
        assertTrue(manifest.contains("20480"))
        assertTrue(manifest.contains("OFL-1.1"))
    }

    @Test
    fun `readme marks subsets generated and lists roles`() {
        val readme = readProjectFile("$assetsDir/README.md")
        assertTrue(readme.contains("子集已实际生成并打包"))
        assertTrue(readme.contains("Noto Serif CJK SC"))
        assertTrue(readme.contains("Noto Serif CJK SC SemiBold"))
        assertTrue(readme.contains("Noto Sans CJK SC Light"))
        assertTrue(readme.contains("Inter"))
        assertTrue(readme.contains("JetBrains Mono"))
    }

    @Test
    fun `subset generator script present with budget and ofl provenance`() {
        val script = readProjectFile("$assetsDir/subset_generate.py")
        assertTrue(script.contains("fontTools"))
        assertTrue(script.contains("BODY_BUDGET_BYTES = 200 * 1024"))
        assertTrue(script.contains("NUMBER_BUDGET_BYTES = 20 * 1024"))
        assertTrue(script.contains("luowen-core.txt"))
        assertTrue(script.contains("OFL"))
    }

    @Test
    fun `welcome activity hooks InkFontLoader init on early startup`() {
        val activity = readProjectFile(
            "app/src/main/java/io/legado/app/ui/welcome/WelcomeActivity.kt",
        )
        assertTrue(
            "启动 Activity 未挂接 InkFontLoader.init",
            activity.contains("InkFontLoader.init(this)"),
        )
        assertTrue(
            "缺少字体初始化兜底 runCatching",
            activity.contains("runCatching { InkFontLoader.init(this) }"),
        )
    }

    @Test
    fun `main code has no hex color literal and no Material import`() {
        val hexRegex = Regex("#[0-9a-fA-F]{3,8}\\b")
        projectFile(mainDir).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                assertTrue("${file.name} 出现 hex 颜色字面量", !hexRegex.containsMatchIn(text))
                assertTrue(
                    "${file.name} 引入了 Material",
                    !text.contains("com.google.android.material"),
                )
            }
    }

    @Test
    fun `loader and registry source files exist`() {
        listOf(
            "FontRole.kt",
            "FontAssetSource.kt",
            "FontLoadResult.kt",
            "FontRegistry.kt",
            "AndroidFontSource.kt",
            "InkFontLoader.kt",
        ).forEach { name ->
            val file = File(projectFile(mainDir), name)
            assertTrue("缺主代码文件 $name", file.isFile)
            assertTrue("$name 不应为空", file.length() > 0)
        }
    }
}
