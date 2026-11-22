package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * powers.js 朝代图层分层验收（任务规格：春秋晋 / 战国晋 / 汉晋不混）：
 *  - 每条势力带 from/to 起止公元年，区间合法；
 *  - 名「晋」的势力只有春秋晋（-770~-453，三家分晋为止）；
 *  - 战国三晋为赵/魏/韩；汉以后「晋」拆为西晋 / 东晋，区间与春秋晋互斥；
 *  - 现代晋城只在 cities.js 今址参考层。
 */
class PowersLayerAssetTest {

    private data class Power(
        val name: String, val era: String, val from: Int, val to: Int, val group: String,
    )

    private fun repositoryRoot(): File {
        val userDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDirectory) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
    }

    private fun parsePowers(js: String, group: String): List<Power> {
        val regex = Regex(""""name":"([^"]+)","era":"([^"]+)","from":(-?\d+),"to":(-?\d+)""")
        return regex.findAll(js).map { m ->
            Power(m.groupValues[1], m.groupValues[2], m.groupValues[3].toInt(), m.groupValues[4].toInt(), group)
        }.toList()
    }

    private fun loadPowers(): List<Power> {
        val file = File(repositoryRoot(), "app/src/main/assets/map/powers.js")
        assertTrue("powers.js 必须存在", file.isFile)
        val text = file.readText()
        val poly = text.substringAfter("window.POWERS =").substringBefore("window.POWERS_CIRCLE")
        val circle = text.substringAfter("window.POWERS_CIRCLE")
        return parsePowers(poly, "poly") + parsePowers(circle, "circle")
    }

    @Test
    fun `every power carries valid from to span`() {
        val powers = loadPowers()
        assertTrue("势力条目数量下限 20", powers.size >= 20)
        powers.forEach { p ->
            assertTrue("${p.name} 止年早于起年", p.to >= p.from)
        }
    }

    @Test
    fun `jin name only appears as spring-autumn jin, range ends at three-family split`() {
        val powers = loadPowers()
        val jin = powers.filter { it.name == "晋" }
        assertEquals("名「晋」的势力有且仅有春秋晋", 1, jin.size)
        val chunqiu = jin.first()
        assertEquals("春秋", chunqiu.era)
        assertEquals(-770, chunqiu.from)
        assertEquals(-453, chunqiu.to)

        // 战国层不得再出现国号「晋」；三晋是赵/魏/韩
        val warring = powers.filter { it.era == "战国" }
        assertFalse("战国层不得混春秋晋", warring.any { it.name == "晋" })
        listOf("赵", "魏", "韩").forEach { zhao ->
            assertTrue("战国三晋应含 $zhao", warring.any { it.name == zhao })
        }
    }

    @Test
    fun `western and eastern jin are separate layers, disjoint from spring-autumn jin`() {
        val powers = loadPowers()
        val xiJin = powers.firstOrNull { it.name == "西晋" }
        val dongJin = powers.firstOrNull { it.name == "东晋" }
        assertTrue("须有西晋图层", xiJin != null)
        assertTrue("须有东晋图层", dongJin != null)
        assertEquals(265, xiJin!!.from)
        assertEquals(316, xiJin.to)
        assertEquals(317, dongJin!!.from)
        assertEquals(420, dongJin.to)

        val chunqiu = powers.first { it.name == "晋" }
        assertTrue("春秋晋与西晋区间不得重叠", chunqiu.to < xiJin.from)
        assertTrue("西晋与东晋区间相接不重叠", xiJin.to < dongJin.from)
    }

    @Test
    fun `modern jincheng stays in reference city layer only`() {
        val cities = File(repositoryRoot(), "app/src/main/assets/map/cities.js").readText()
        val mainBlock = cities.substringAfter("window.CITIES =").substringBefore("window.CITIES_REF")
        assertFalse("主 CITIES 层不得收录现代晋城", mainBlock.contains("晋城"))
        val refBlock = cities.substringAfter("window.CITIES_REF")
        assertTrue("CITIES_REF 须收录晋城", refBlock.contains("\"晋城\""))
    }
}
