package io.legado.app.ui.map.globe.astro.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * 署名链接映射纯 JVM 单测（台账 S-04 验收判据②；Q-506 / D53）。
 *
 * 覆盖：两个链接常量都是合法 https 且主机非空；来源行映射到 SOURCE_URL、许可行映射到
 * LICENSE_URL；衍生表述行与未知任意行返回 null（不可点）。纯 JVM：不碰 Compose 运行时、
 * 不拉起真机；真机点击两次分别打开 astronexus 仓库与 CC 许可页的补绿条件见交付报告
 * 「未验证项 / 黄标」节。
 */
class SkyAttributionLinkTest {

    @Test
    fun `两个链接常量都是合法https且主机非空`() {
        assertHttps(SkyAttributionText.SOURCE_URL)
        assertHttps(SkyAttributionText.LICENSE_URL)
    }

    @Test
    fun `来源行映射到SOURCE_URL`() {
        assertEquals(
            SkyAttributionText.SOURCE_URL,
            SkyAttributionText.urlForLine(SkyAttributionText.sourceLine),
        )
    }

    @Test
    fun `许可行映射到LICENSE_URL`() {
        assertEquals(
            SkyAttributionText.LICENSE_URL,
            SkyAttributionText.urlForLine(SkyAttributionText.licenseLine),
        )
    }

    @Test
    fun `衍生表述行返回null不可点`() {
        assertNull(SkyAttributionText.urlForLine(SkyAttributionText.DERIVATIVE_NOTICE))
    }

    @Test
    fun `未知任意行返回null不可点`() {
        assertNull(SkyAttributionText.urlForLine("任意一行既非来源也非许可"))
    }

    private fun assertHttps(url: String) {
        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertTrue(uri.host.isNotBlank())
    }
}
