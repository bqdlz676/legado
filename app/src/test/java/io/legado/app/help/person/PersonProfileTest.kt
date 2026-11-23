package io.legado.app.help.person

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块 D2 · 人物 Provider 验收（规格切片-02 §6 PersonProvider 行 + §升格段「人名消歧做扎实」）。
 *
 * 必含用例：
 * - 别名归并：字 / 号 / 封号 / 邦称 / 别称全部并到本名；
 * - 同名消歧：《史记》两位「韩信」（淮阴侯 / 韩王信）必须分开，默认命中与主键精确定位两条路径都验；
 * - 出处约束：每条种子档案必须带非空出处；CBDB 标注格式为 Harvard CBDB #收录号；
 * - 生卒无载如实为 null，查无此人返回 null，不猜身份。
 *
 * 本类为宿主 JVM 纯逻辑测试，不依赖 android.Context。
 */
class PersonProfileTest {

    // ---------- 别名归并 ----------

    @Test
    fun `字归并到本名`() {
        assertEquals("司马迁", PersonProfile.canonical("子长"))
        assertEquals("张良", PersonProfile.canonical("子房"))
        assertEquals("诸葛亮", PersonProfile.canonical("孔明"))
        assertEquals("李白", PersonProfile.canonical("太白"))
    }

    @Test
    fun `号归并到本名`() {
        assertEquals("李白", PersonProfile.canonical("青莲居士"))
        assertEquals("苏轼", PersonProfile.canonical("东坡居士"))
        assertEquals("杜甫", PersonProfile.canonical("少陵野老"))
        assertEquals("庄子", PersonProfile.canonical("南华真人"))
    }

    @Test
    fun `封号爵号归并到本名`() {
        assertEquals("白起", PersonProfile.canonical("武安君"))
        assertEquals("张良", PersonProfile.canonical("留侯"))
        assertEquals("萧何", PersonProfile.canonical("酂侯"))
        assertEquals("吕不韦", PersonProfile.canonical("文信侯"))
        assertEquals("诸葛亮", PersonProfile.canonical("武乡侯"))
    }

    @Test
    fun `邦称官称别称归并到本名`() {
        assertEquals("刘邦", PersonProfile.canonical("沛公"))
        assertEquals("刘邦", PersonProfile.canonical("汉高祖"))
        assertEquals("屈原", PersonProfile.canonical("三闾大夫"))
        assertEquals("张仪", PersonProfile.canonical("张子"))
        assertEquals("范雎", PersonProfile.canonical("张禄"))
        assertEquals("曹操", PersonProfile.canonical("阿瞒"))
    }

    @Test
    fun `profileOf 经别名命中同一档案`() {
        val p = PersonProfile.profileOf("张子")
        assertTrue(p != null && p.personId == "seed:zhangyi")
        assertEquals("张仪", p!!.name)
    }

    @Test
    fun `称呼带引号括号与空白仍可归并`() {
        assertEquals("张良", PersonProfile.canonical("「子房」"))
        assertEquals("张良", PersonProfile.canonical("（子房）"))
        assertEquals("张良", PersonProfile.canonical(" 子房 "))
    }

    // ---------- 查无此人兜底 ----------

    @Test
    fun `查无此人返回 null 且 canonical 原样回写`() {
        assertNull(PersonProfile.profileOf("欧阳锋"))
        assertNull(PersonProfile.lifeSpan("欧阳锋"))
        assertEquals("欧阳锋", PersonProfile.canonical("欧阳锋"))
        assertNull(PersonProfile.profileOf(""))
        assertEquals("", PersonProfile.canonical(""))
        assertTrue(PersonProfile.candidatesOf("不存在的人").isEmpty())
    }

    // ---------- 档案字段完整性 ----------

    @Test
    fun `档案字段齐备 本名身份国籍小传谱系事迹出处`() {
        val p = PersonProfile.profileOf("孔子")!!
        assertEquals("孔子", p.name)
        assertEquals("鲁", p.nationality)
        assertTrue(p.identity.isNotBlank())
        assertTrue(p.biography.isNotBlank())
        assertTrue(p.lineage.isNotEmpty())
        assertTrue(p.notableEvent.isNotBlank())
        assertTrue(p.source.label.isNotBlank())
        assertEquals(-551, p.birthYear)
        assertEquals(-479, p.deathYear)
    }

    @Test
    fun `生卒无载如实为 null 不补约数`() {
        val p = PersonProfile.profileOf("王翦")!!
        assertNull(p.birthYear)
        assertNull(p.deathYear)
        assertEquals(Pair(null, null), PersonProfile.lifeSpan("王翦"))
        assertEquals(Pair(null, -309), PersonProfile.lifeSpan("张仪"))
        assertEquals(Pair(-232, -202), PersonProfile.lifeSpan("项羽"))
    }

    // ---------- 同名消歧（两位韩信） ----------

    @Test
    fun `同名分组返回两位候选 不丢不并`() {
        val candidates = PersonProfile.candidatesOf("韩信")
        assertEquals(2, candidates.size)
        val ids = candidates.map { it.personId }.toSet()
        assertTrue(ids.contains("seed:hanxin-huaiyin"))
        assertTrue(ids.contains("seed:hanxin-hanwang"))
    }

    @Test
    fun `无上下文时默认命中 preferred 的淮阴侯`() {
        val p = PersonProfile.profileOf("韩信")!!
        assertEquals("seed:hanxin-huaiyin", p.personId)
        assertTrue(p.identity.contains("名将"))
    }

    @Test
    fun `按主键精确定位另一位同名者韩王信`() {
        val p = PersonProfile.profileOf("韩信", "seed:hanxin-hanwang")!!
        assertEquals("seed:hanxin-hanwang", p.personId)
        assertTrue(p.identity.contains("诸侯王"))
        assertEquals("韩", p.nationality)
    }

    @Test
    fun `韩王信别称归并到韩王而非淮阴侯`() {
        val byAlias = PersonProfile.profileOf("韩王信")!!
        assertEquals("seed:hanxin-hanwang", byAlias.personId)
        assertEquals("韩信", PersonProfile.canonical("韩王信"))

        val byTitle = PersonProfile.profileOf("韩王")!!
        assertEquals("seed:hanxin-hanwang", byTitle.personId)

        val huaiyin = PersonProfile.profileOf("淮阴侯")!!
        assertEquals("seed:hanxin-huaiyin", huaiyin.personId)
    }

    // ---------- 出处约束 ----------

    @Test
    fun `每条种子档案都带非空出处`() {
        val all = PersonDirectory.allRecords()
        assertTrue(all.size > 10)
        all.forEach { p ->
            assertTrue("${p.name} 出处为空", p.source.label.isNotBlank())
            assertTrue("${p.name} 本名空", p.name.isNotBlank())
        }
    }

    @Test
    fun `Sheet 渲染依赖的称谓朝代同名候选字段齐备`() {
        val libai = PersonProfile.profileOf("李白")!!
        assertEquals("太白", libai.courtesyName)
        assertEquals("青莲居士", libai.styleName)
        assertTrue(libai.aliases.contains("谪仙人"))
        assertEquals("唐", libai.dynasty)

        val hanxin = PersonProfile.profileOf("韩信")!!
        assertTrue(hanxin.titles.contains("淮阴侯"))

        // 同名候选行标签（朝代 · 身份 · 生卒）所需字段均可读
        PersonProfile.candidatesOf("韩信").forEach { c ->
            assertTrue(c.name.isNotBlank())
            assertTrue(c.identity.isNotBlank())
        }
    }

    @Test
    fun `CBDB 出处标注 Harvard CBDB 收录号`() {
        val source = PersonSource.Cbdb("12345")
        assertEquals("Harvard CBDB #12345", source.label)
    }

    @Test
    fun `外部 CBDB 档案可注册并入索引 无出处记录被拒绝`() {
        val ext = PersonProfile(
            personId = "cbdb:88888",
            name = "测试CBDB客",
            nationality = "唐",
            identity = "待校人物",
            biography = "注册扩展点验证用",
            notableEvent = "扩展点验证",
            source = PersonSource.Cbdb("88888"),
        )
        PersonDirectory.register(listOf(ext))
        assertEquals("cbdb:88888", PersonProfile.profileOf("测试CBDB客")!!.personId)
        assertEquals("Harvard CBDB #88888", PersonProfile.profileOf("测试CBDB客")!!.source.label)

        val invalid = PersonProfile(
            personId = "cbdb:bad",
            name = "无出处客",
            nationality = "唐",
            identity = "x",
            biography = "x",
            notableEvent = "x",
            source = PersonSource.Classic(""),
        )
        var thrown = false
        try {
            PersonDirectory.register(listOf(invalid))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("无出处记录必须拒绝注册", thrown)
        assertNull(PersonProfile.profileOf("无出处客"))
    }
}
