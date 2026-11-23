package io.legado.app.ui.font

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * U9 打包子集验收（切片-14 / 切片-08 红线）：
 *  - 五枚子集真实落盘，文件名命中角色候选清单；
 *  - 体积红线：正文 / 标题 ≤ 200KB，数字 ≤ 20KB；manifest 登记字节数与磁盘一致；
 *  - 二进制头合法（OTTO / TrueType）；
 *  - coverage.json 无缺字，灵魂闭环 / 纪年必覆盖字在字表与清单内；
 *  - 零 copyleft：许可仅 OFL-1.1，不含其他著佐权类许可证名。
 *
 * 说明：cmap 逐字解析由生成侧 fonttools 完成（见 coverage.json 的 missingChars），
 * 本测试在宿主 JVM 上守护资产落盘、体积、清单一致与许可红线，不依赖 Robolectric。
 */
class FontAssetPackagingTest {

    private fun repositoryRoot(): File {
        val userDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDirectory) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
    }

    private val fontsDir: File by lazy {
        File(repositoryRoot(), "app/src/main/assets/fonts")
    }

    private fun readJson(name: String): JsonObject {
        val file = File(fontsDir, name)
        assertTrue("$name 必须存在", file.isFile)
        return JsonParser.parseString(file.readText()).asJsonObject
    }

    private fun magicOk(bytes: ByteArray, fileName: String): Boolean {
        val otf = bytes.size >= 4 && bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'T'.code.toByte() && bytes[2] == 'T'.code.toByte() &&
            bytes[3] == 'O'.code.toByte()
        val ttf = bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 1.toByte() &&
            bytes[2] == 0.toByte() && bytes[3] == 0.toByte()
        return otf || ttf || fileName.lowercase().endsWith(".ttc")
    }

    @Test
    fun `every role packaged file exists matches candidate and stays under budget`() {
        val manifest = readJson("manifest.json")
        val roles = manifest.getAsJsonArray("roles")
        assertEquals(FontRole.entries.size, roles.size())

        FontRole.entries.forEach { role ->
            val entry = roles.firstOrNull {
                it.asJsonObject.get("role").asString == role.name
            }?.asJsonObject ?: error("manifest 缺角色 ${role.name}")
            val packaged = entry.getAsJsonObject("packaged")
            val fileName = packaged.get("file").asString
            val asset = File(fontsDir, fileName)

            assertTrue("${role.name} 打包子集不存在：$fileName", asset.isFile)
            assertTrue(
                "${role.name} 文件名未命中候选清单",
                role.candidateAssets.any { it.equals(fileName, ignoreCase = true) },
            )

            val diskBytes = asset.length()
            val manifestBytes = packaged.get("bytes").asLong
            assertEquals("${role.name} manifest 字节数与磁盘不一致", manifestBytes, diskBytes)

            role.maxSubsetBytes?.let { budget ->
                assertTrue(
                    "${role.name} 子集 ${diskBytes}B 超红线 ${budget}B",
                    diskBytes <= budget,
                )
            }

            val bytes = asset.readBytes()
            assertTrue("${role.name} 二进制头非法", magicOk(bytes, fileName))
            assertEquals("${role.name} 许可必须 OFL-1.1", "OFL-1.1", packaged.get("license").asString)
        }
    }

    @Test
    fun `body and title subsets within 200kb hard redline`() {
        val manifest = readJson("manifest.json")
        manifest.getAsJsonArray("roles").forEach { element ->
            val role = element.asJsonObject
            val name = role.get("role").asString
            val bytes = role.getAsJsonObject("packaged").get("bytes").asLong
            if (name == "BODY_SERIF" || name == "TITLE_SERIF") {
                assertTrue(
                    "$name ${bytes}B 超过 200KB 红线",
                    bytes <= FontRole.BODY_SUBSET_BUDGET_BYTES,
                )
            }
            if (name == "NUMBER_TABULAR") {
                assertTrue(
                    "$name ${bytes}B 超过 20KB 红线",
                    bytes <= FontRole.NUMBER_SUBSET_BUDGET_BYTES,
                )
            }
        }
    }

    @Test
    fun `coverage report has no missing glyph and covers soul loop`() {
        val coverage = readJson("coverage.json")
        val mandatory = coverage.get("mandatoryText").asString
        // 灵魂闭环 + 干支 + 纪年关键锚点
        listOf("晋", "鲁隐公元年", "己未", "甲", "癸", "子", "亥", "年", "月", "日")
            .forEach { anchor ->
                assertTrue("必覆盖锚点缺失：$anchor", mandatory.contains(anchor))
            }

        val jobs = coverage.getAsJsonArray("jobs")
        assertEquals(FontRole.entries.size, jobs.size())
        jobs.forEach { element ->
            val job = element.asJsonObject
            val role = job.get("role").asString
            val missing = job.get("missingChars").asString
            assertTrue("$role 存在缺字：$missing", missing.isEmpty())
            val covered = job.get("coveredChars").asInt
            assertTrue("$role 覆盖字符数为 0", covered > 0)
            if (role == "BODY_SERIF" || role == "TITLE_SERIF" || role == "LABEL_SANS") {
                assertTrue("$role CJK 覆盖不足", covered >= 500)
            }
        }
    }

    @Test
    fun `core char list carries mandatory soul loop glyphs`() {
        val charList = File(fontsDir, "chars/luowen-core.txt")
        assertTrue("字表文件不存在", charList.isFile)
        val text = charList.readText()
        listOf("晋", "鲁", "隐", "公", "元", "年", "己", "未", "春", "秋", "干", "支")
            .forEach { assertTrue("字表缺必覆盖字 $it", text.contains(it)) }
    }

    @Test
    fun `licenses are ofl only with no copyleft markers`() {
        val manifest = readJson("manifest.json")
        val redline = manifest.get("licenseRedline").asString
        assertTrue(redline.contains("OFL"))

        val licenseFile = File(fontsDir, "LICENSE-OFL.txt")
        assertTrue("OFL 许可文件不存在", licenseFile.isFile)
        val licenseText = licenseFile.readText()
        assertTrue(licenseText.contains("SIL OPEN FONT LICENSE"))
        listOf("Adobe", "Inter", "JetBrains").forEach {
            assertTrue("许可出处缺版权方 $it", licenseText.contains(it))
        }

        // 用完整许可证名短语匹配，避免 bare 缩写误命中 EXEMPLARY 等普通单词。
        val copyleft = listOf(
            "GNU General Public", "GNU Lesser General", "GNU Affero General",
            "Mozilla Public License", "Eclipse Public License", "CC-BY-SA",
        )
        copyleft.forEach { marker ->
            assertFalse("许可文件出现 copyleft 标记 $marker", licenseText.contains(marker))
        }
    }
}
