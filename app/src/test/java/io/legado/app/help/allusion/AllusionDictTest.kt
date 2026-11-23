package io.legado.app.help.allusion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块 D4 · 典故 Provider 验收（规格切片-02 §3 典故行 + §5 验收测试 + §升格段「数据可信 = 命」）。
 *
 * 必含用例：
 * - 已下划线的 2~4 字典故词整词命中，不拆成单字；
 * - 普通单字（如「抽」）不误弹典故；
 * - 每条种子条目带出处与版本标注，无来路数据不允许存在；
 * - 词长 2~4 字约束、headline 等于典故词；
 * - 注册扩展点：无出处 / 撞词条目被拒绝。
 *
 * 本类为宿主 JVM 纯逻辑测试，不依赖 android.Context。
 */
class AllusionDictTest {

    // ---------- 整词命中 ----------

    @Test
    fun `两字三字四字典故词整词命中`() {
        val hits = listOf(
            "破釜沉舟", "四面楚歌", "鸿门宴", "项庄舞剑", "卧薪尝胆",
            "完璧归赵", "负荆请罪", "纸上谈兵", "毛遂自荐", "脱颖而出",
            "韦编三绝", "图穷匕见", "退避三舍", "唇亡齿寒", "假道伐虢",
            "狐假虎威", "画蛇添足", "亡羊补牢", "狡兔三窟", "鹬蚌相争",
            "老马识途", "滥竽充数", "守株待兔", "自相矛盾", "揠苗助长",
            "缘木求鱼", "邯郸学步", "井底之蛙", "庖丁解牛", "鹏程万里",
            "刻舟求剑", "掩耳盗铃", "杞人忧天", "愚公移山", "精卫填海",
            "塞翁失马", "举一反三", "温故知新", "见贤思齐", "任重道远",
            "逝者如斯", "祸起萧墙", "天作之合", "小心翼翼", "他山之石",
            "高山仰止", "如履薄冰", "投笔从戎", "马革裹尸", "三顾茅庐",
            "乐不思蜀", "闻鸡起舞", "东山再起", "洛阳纸贵", "望梅止渴",
            "七步成诗", "凿壁偷光", "入木三分", "画龙点睛", "青梅竹马",
            "寸草春晖", "春风得意", "柳暗花明", "水落石出", "乘风破浪",
            "孟母三迁",
        )
        hits.forEach { word ->
            val entry = AllusionDict.find(word)
            assertNotNull("典故「$word」应命中，实际 null", entry)
            assertEquals(word, entry!!.word)
            assertEquals(word, entry.headline)
            assertTrue("「$word」拼音为空", !entry.phonetic.isNullOrBlank())
            assertTrue("「$word」一句话释义为空", entry.body.isNotBlank())
            assertTrue("「$word」全文与出处为空", entry.detail.isNotBlank())
            assertTrue("「$word」出处为空", entry.source.isNotBlank())
            assertTrue("「$word」出处缺书名号", entry.source.contains("《"))
        }
    }

    @Test
    fun `命中条目 detail 含典籍引句与本事`() {
        val entry = AllusionDict.find("破釜沉舟")!!
        assertTrue(entry.detail.contains("项羽"))
        assertTrue(entry.detail.contains("沉船") || entry.detail.contains("破釜"))
        assertTrue(entry.source.contains("史记"))
    }

    // ---------- 不误弹：单字 / 非典故词 / 越长短词 ----------

    @Test
    fun `普通单字不误弹典故 含规格点名的抽`() {
        // 切片-02 §5：点「抽」这类普通字，只出字义 + 说文 + 用例，不误弹典故。
        assertNull(AllusionDict.find("抽"))
        assertNull(AllusionDict.find("人"))
        assertNull(AllusionDict.find("山"))
        assertNull(AllusionDict.find("水"))
        assertNull(AllusionDict.find("的"))
    }

    @Test
    fun `空串空白与非典故二字词返回 null`() {
        assertNull(AllusionDict.find(""))
        assertNull(AllusionDict.find("   "))
        assertNull(AllusionDict.find("桌子"))
        assertNull(AllusionDict.find("吃饭"))
        assertNull(AllusionDict.find("现代"))
    }

    @Test
    fun `超过四字的串不进典故索引`() {
        assertNull(AllusionDict.find("破釜沉舟之计"))
        assertNull(AllusionDict.find("有志者事竟成"))
    }

    @Test
    fun `只做整词精确匹配 不做子串包含`() {
        // 「沉舟」不是独立收录词，不能因「破釜沉舟」在表中而被子串命中。
        assertNull(AllusionDict.find("沉舟"))
        assertNull(AllusionDict.find("三顾"))
        assertNull(AllusionDict.find("杯薪"))
    }

    // ---------- 入参归一化 ----------

    @Test
    fun `两侧引号括号空白剥除后仍命中`() {
        assertEquals("破釜沉舟", AllusionDict.find("「破釜沉舟」")!!.word)
        assertEquals("四面楚歌", AllusionDict.find("（四面楚歌）")!!.word)
        assertEquals("守株待兔", AllusionDict.find("(守株待兔)")!!.word)
        assertEquals("负荆请罪", AllusionDict.find(" 负荆请罪 ")!!.word)
        assertEquals("狐假虎威", AllusionDict.find("『狐假虎威』")!!.word)
    }

    // ---------- 全量种子出处与字段约束 ----------

    @Test
    fun `种子规模不少于五十条 且每条出处版本齐备`() {
        val all = AllusionDict.allRecords()
        assertTrue("种子规模应 ≥ 50，实际 ${all.size}", all.size >= 50)
        all.forEach { entry ->
            assertTrue("${entry.word} 词长越界", entry.word.length in 2..4)
            assertEquals("${entry.word} headline 不等于典故词", entry.word, entry.headline)
            assertTrue("${entry.word} 拼音为空", !entry.phonetic.isNullOrBlank())
            assertTrue("${entry.word} 释义为空", entry.body.isNotBlank())
            assertTrue("${entry.word} 全文为空", entry.detail.isNotBlank())
            assertTrue("${entry.word} 出处为空", entry.source.isNotBlank())
            assertTrue("${entry.word} 出处缺书名号", entry.source.contains("《"))
            assertTrue("${entry.word} 出处未标版本口径", entry.source.contains("公版"))
            assertTrue("${entry.word} id 为空", entry.id.isNotBlank())
        }
    }

    @Test
    fun `典故词与 id 均无重复`() {
        val all = AllusionDict.allRecords()
        assertEquals(all.size, all.map { it.word }.distinct().size)
        assertEquals(all.size, all.map { it.id }.distinct().size)
    }

    @Test
    fun `出处口径不采现代点校本 标注公版`() {
        AllusionDict.allRecords().forEach { entry ->
            assertFalse(
                "${entry.word} 出处出现中华书局点校版表述",
                entry.source.contains("中华书局"),
            )
            assertTrue(
                "${entry.word} 出处未写明公版口径",
                entry.source.contains("公版"),
            )
        }
    }

    // ---------- 出处标签口径 ----------

    @Test
    fun `出处标签含书名篇卷 公版口径与非点校本声明`() {
        val classic = AllusionSource.classic("《史记·项羽本纪》", "西汉·司马迁撰")
        assertTrue(classic.contains("《史记·项羽本纪》"))
        assertTrue(classic.contains("西汉·司马迁撰"))
        assertTrue(classic.contains("公版"))
        assertTrue(classic.contains("非现代点校本"))

        val poem = AllusionSource.poem("唐·李白", "长干行")
        assertTrue(poem.contains("《长干行》"))
        assertTrue(poem.contains("公版"))

        val joined = AllusionSource.join("甲", "甲", "", null, "乙")
        assertEquals("甲；乙", joined)
    }

    // ---------- 注册扩展点 ----------

    @Test
    fun `合法外部条目可注册并入索引 撞词被拒绝`() {
        val valid = AllusionEntry(
            id = "seed:test-valid-1",
            word = "测试新典",
            phonetic = null,
            headline = "测试新典",
            body = "注册扩展点验证用。",
            detail = "扩展点验证全文。",
            source = "《列子·汤问》（旧题战国·列御寇撰，公版影印本；非现代点校本）",
        )
        AllusionDict.register(listOf(valid))
        assertEquals("测试新典", AllusionDict.find("测试新典")!!.word)

        val dup = valid.copy(id = "seed:test-dup")
        var thrown = false
        try {
            AllusionDict.register(listOf(dup))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("撞词条目必须拒绝注册", thrown)
    }

    @Test
    fun `无出处与字段残缺的外部条目被拒绝`() {
        val noSource = AllusionEntry(
            id = "seed:test-nosource",
            word = "测试无出处",
            phonetic = null,
            headline = "测试无出处",
            body = "x",
            detail = "x",
            source = "",
        )
        var thrown = false
        try {
            AllusionDict.register(listOf(noSource))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("无出处条目必须拒绝", thrown)
        assertNull(AllusionDict.find("测试无出处"))

        val badLength = AllusionEntry(
            id = "seed:test-badlength",
            word = "测",
            phonetic = null,
            headline = "测",
            body = "x",
            detail = "x",
            source = "《测试》（公版）",
        )
        thrown = false
        try {
            AllusionDict.register(listOf(badLength))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("词长越界条目必须拒绝", thrown)
    }
}
