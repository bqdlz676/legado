package io.legado.app.help.chrono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 契约 §三 纪年验收：
 *  鲁隐公元年 = 前722 = 周平王49年 = 郑庄公22年；
 *  对照时间轴同年并列；爵称省称可拆出国名。
 *
 * 干支口径说明：契约示例印作「辛酉」，但史料（Wikipedia 鲁隐公年表 / 字典网 / 抖音百科）
 * 一致记载前722为己未年，辛酉为前720（隐公3年）。本实现按天文纪年标准公式
 * （公元前 n 年先转天文纪年 -(n-1)，再套 (y-4) mod 60）取史料值，未按契约错值硬编码。
 */
class ChronologyTest {

    @Test
    fun `timeline aligns lu yin gong year 1 with zhou ping wang 49 and zhuang gong 22`() {
        val rows = Chronology.timelineFor(-722).toMap()
        assertEquals(49, rows["周平王"])
        assertEquals(1, rows["鲁隐公"])
        assertEquals(22, rows["郑庄公"])
    }

    @Test
    fun `timeline rows carry reign span and nth year`() {
        val rows = Chronology.timelineRows(-722)
        val zhou = rows.firstOrNull { it.state == "周" }
        requireNotNull(zhou)
        assertEquals("平王", zhou.title)
        assertEquals(49, zhou.nth)
        assertEquals(-770, zhou.start)
    }

    @Test
    fun `prince year conversion`() {
        assertEquals(-722, Chronology.princeYear("鲁隐公", 1))
        assertEquals(-722, Chronology.princeYear("郑庄公", 22))
        assertNull(Chronology.princeYear("鲁隐公", 99))
    }

    @Test
    fun `anchor from text full and short prince mention`() {
        val full = Chronology.anchorFromText("郑庄公二十二年")
        assertEquals(-722, full?.year)
        assertEquals("郑", full?.state)

        val short = Chronology.anchorFromText("隐公元年")
        assertEquals(-722, short?.year)
        assertEquals("鲁", short?.state)
    }

    @Test
    fun `parse rank title splits state and rank`() {
        assertEquals("楚" to "子", Chronology.parseStateTitle("楚子"))
        assertEquals("郑" to "伯", Chronology.parseStateTitle("郑伯"))
        assertNull(Chronology.parseStateTitle("孔子"))
    }

    @Test
    fun `ruler of state at year`() {
        val ruler = Chronology.rulerOf("周", -722)
        assertEquals("平王", ruler?.title)
        assertNull(Chronology.rulerOf("周", -800))
    }

    @Test
    fun `chinese number parsing`() {
        assertEquals(1, Chronology.chineseNumber("元"))
        assertEquals(22, Chronology.chineseNumber("二十二"))
        assertEquals(10, Chronology.chineseNumber("十"))
        assertNull(Chronology.chineseNumber(""))
    }

    @Test
    fun `year ganzhi follows astronomical convention`() {
        assertEquals("甲辰", Chronology.yearGanzhi(2024))
        assertEquals("辛未", Chronology.yearGanzhi(-770))
        // 史料值：前722 = 己未（契约示例「辛酉」系前720，见类注释说明）
        assertEquals("己未", Chronology.yearGanzhi(-722))
    }
}
