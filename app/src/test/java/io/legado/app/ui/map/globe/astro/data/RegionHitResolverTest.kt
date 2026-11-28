package io.legado.app.ui.map.globe.astro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.legado.app.ui.map.globe.astro.data.RegionAnchor

/**
 * 地球侧点按 → 分野州域反查纯 JVM 单测（D39 第一跳；§十一.4 tier3 示意锚点）。
 *
 * 覆盖：半径内最近邻命中、超半径回落 null、空表回落 null、非有限输入回落 null、
 * 并列夹角取表内先出现者。纯 JVM：只用 org.junit 与既有 Geo 球面换算。
 */
class RegionHitResolverTest {

    private fun twoAnchorResolver(): RegionHitResolver = RegionHitResolver(
        listOf(
            RegionAnchor("雍州", 34.34, 108.94, 8.0),
            RegionAnchor("青州", 36.60, 118.50, 6.0),
        ),
    )

    @Test
    fun `半径内最近邻命中 长安坐标落雍州`() {
        val resolver = twoAnchorResolver()
        // 改坏推演：若实现不判半径只取最近邻，远海用例（下一条）会错误命中；
        // 若夹角算成平面距离，长安用例可能落到青州，在此 assertEquals 变红。
        assertEquals("雍州", resolver.resolve(34.26, 108.94))
    }

    @Test
    fun `超半径远海坐标回落null`() {
        val resolver = twoAnchorResolver()
        // 改坏推演：若去掉半径判定，远海点会取到最近锚点而非 null，下面断言变红。
        assertNull(resolver.resolve(0.0, -150.0))
    }

    @Test
    fun `空锚点表恒返回null 不抛异常`() {
        val resolver = RegionHitResolver(emptyList())
        // 改坏推演：若空表直接取 anchors.first() 抛 NPE，本用例编译通过但运行红；
        // 现有实现先判 empty 返回 null，断言保持 null。
        assertNull(resolver.resolve(34.0, 109.0))
    }

    @Test
    fun `非有限输入回落null`() {
        val resolver = twoAnchorResolver()
        assertNull(resolver.resolve(Double.NaN, 108.94))
        assertNull(resolver.resolve(34.34, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `并列夹角取表内先出现者`() {
        // 两锚点同位同半径：触点与两锚夹角完全相等，严格小于比较保留先出现者。
        val resolver = RegionHitResolver(
            listOf(
                RegionAnchor("冀州", 37.70, 115.40, 8.0),
                RegionAnchor("幽州", 37.70, 115.40, 8.0),
            ),
        )
        // 改坏推演：若并列时取后出现者，下面 assertEquals 得到 "幽州" 而变红。
        assertEquals("冀州", resolver.resolve(37.70, 115.40))
    }

    @Test
    fun `内置锚点表十三州域齐全 长安默认点落雍州`() {
        val resolver = RegionHitResolver(RegionHitResolver.defaultAnchors())
        assertEquals("雍州", resolver.resolve(34.34, 108.94))
    }
}
