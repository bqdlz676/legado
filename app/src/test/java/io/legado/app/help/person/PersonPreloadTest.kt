package io.legado.app.help.person

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 模块 D2 · 启动预载接线验收（回填验收观察项 O1：PersonAssets.loadCore 此前无生产调用方，
 * 运行时目录仅内置种子档案；本批把 L1 核心预载接入启动路径）。
 *
 * 口径锁定：
 * - 启动热路径只灌 L1 手校核心（core + l1 互校 + 别名归并）；dup 同名分组 / bulk 兜底全量
 *   按需 / 后台加载，不进启动路径（数据分层：APK 只内置核心小数据，大词典 / 全量库按需下载）；
 * - 本类为宿主 JVM 纯逻辑测试，不依赖 android.Context：[PersonAssets.registerCore] 是
 *   [PersonAssets.loadCore] 剥离 Context 读资产后的同口径管线，单测直喂仓库 assets 行列表；
 * - 预载后 [PersonDirectory.register] 索引可查 L1 人物：本名 / 字（别名）命中、在位起讫、
 *   分层、出处齐备；查无此人返回 null，不凭姓名猜身份。
 */
class PersonPreloadTest {

    private fun assetFile(name: String): File =
        listOf(File("app/src/main/assets/$name"), File("src/main/assets/$name"))
            .firstOrNull { it.isFile }
            ?: error("缺少资产文件：$name")

    private fun readAsset(name: String): List<String> = assetFile(name).readLines()

    /** 复刻生产 loadCore 的纯 JVM 管线（不读 Context.assets）。 */
    private fun preloadCore() {
        PersonAssets.registerCore(
            l1Lines = readAsset("person_l1.txt"),
            coreLines = readAsset("person_core.txt"),
            aliasLines = readAsset("person_alias.txt"),
        )
    }

    @Test
    fun `预载后 L1 人物本名可查 分层与在位起讫齐备`() {
        preloadCore()

        val jinwen = PersonProfile.profileOf("晋文公")
        assertNotNull("预载后应可查晋文公", jinwen)
        assertEquals("晋文公", jinwen!!.name)
        assertEquals(ConfidenceTier.L1, jinwen.tier)
        assertNull("手校 high 行不应带待校标", jinwen.reviewTag)
        assertEquals(Pair(-636, -628), PersonProfile.reignOf("晋文公"))
        assertTrue("出处不能为空", jinwen.source.label.isNotBlank())

        val pingwang = PersonProfile.profileOf("周平王")
        assertNotNull(pingwang)
        assertEquals(Pair(-770, -720), PersonProfile.reignOf("周平王"))
    }

    @Test
    fun `预载后字与别名归并到本名 称呼可命中`() {
        preloadCore()

        // alias 资产：晋文公 · 重耳 · 字（非 @国 占位，归并到本名档案）
        val byCourtesy = PersonProfile.profileOf("重耳")
        assertNotNull("别名「重耳」应归并到晋文公", byCourtesy)
        assertEquals("晋文公", byCourtesy!!.name)
        assertEquals("晋文公", PersonProfile.canonical("重耳"))
    }

    @Test
    fun `core 全量登记 L1 条数达标 出处非空`() {
        preloadCore()

        val l1 = PersonDirectory.countByTier(ConfidenceTier.L1)
        assertTrue("L1 手校核心应 ≥200 条，实际 $l1", l1 >= 200)

        PersonDirectory.allRecords().forEach { p ->
            assertTrue("${p.name} 出处为空", p.source.label.isNotBlank())
        }
    }

    @Test
    fun `空资产行容错 不抛异常 目录不被清空`() {
        val before = PersonDirectory.allRecords().size
        PersonAssets.registerCore(emptyList(), emptyList(), emptyList())
        assertTrue("空行管线不应抛异常，且不应清空在册档案", PersonDirectory.allRecords().size >= before)
    }

    @Test
    fun `查无此人返回 null 不凭姓名猜身份`() {
        preloadCore()
        assertNull(PersonProfile.profileOf("查无此人"))
        assertNull(PersonProfile.reignOf("查无此人"))
    }
}
