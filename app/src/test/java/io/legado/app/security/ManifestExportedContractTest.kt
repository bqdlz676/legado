package io.legado.app.security

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Manifest 导出契约（Q-241）：
 * - 仅内部拉起的 MainActivity / ReadBookActivity 必须 exported=false；
 * - 桌面启动器、分享、深链、文件打开、系统绑定、媒体按键等外部入口必须保持 exported=true；
 * - ReaderProvider 本单不动，仍为 true，待另案补 signature 权限。
 * 防止后续重构把两处内部组件重新导出。
 */
class ManifestExportedContractTest {

    private val manifest = File("src/main/AndroidManifest.xml")

    private fun exportedByComponent(): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder().parse(manifest)
        val map = HashMap<String, String>()
        listOf("activity", "service", "receiver", "provider").forEach { tag ->
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as org.w3c.dom.Element
                val name = el.getAttribute("android:name")
                val exported = el.getAttribute("android:exported")
                if (name.isNotEmpty() && exported.isNotEmpty()) {
                    map[name] = exported
                }
            }
        }
        return map
    }

    @Test
    fun `internal-only activities are not exported`() {
        val map = exportedByComponent()
        assertEquals("MainActivity 仅内部显式 Intent 拉起，必须 exported=false", "false", map[".ui.main.MainActivity"])
        assertEquals("ReadBookActivity legacy 通道无外部拉起，必须 exported=false", "false", map[".ui.book.read.ReadBookActivity"])
    }

    @Test
    fun `external entry points stay exported`() {
        val map = exportedByComponent()
        val stillExported = listOf(
            ".ui.welcome.WelcomeActivity",
            ".ui.welcome.Launcher1",
            ".ui.welcome.Launcher2",
            ".ui.welcome.Launcher3",
            ".ui.welcome.Launcher4",
            ".ui.welcome.Launcher5",
            ".ui.welcome.Launcher6",
            ".receiver.SharedReceiverActivity",
            ".ui.association.OnLineImportActivity",
            ".ui.association.FileAssociationActivity",
            ".service.WebTileService",
            ".service.AutoTaskJobService",
            ".receiver.MediaButtonReceiver"
        )
        stillExported.forEach { component ->
            assertEquals("外部入口 $component 必须保持 exported=true", "true", map[component])
        }
    }

    @Test
    fun `reader provider untouched pending signature permission ticket`() {
        val map = exportedByComponent()
        assertEquals("ReaderProvider 本单不改，待另案补 signature 权限", "true", map[".api.ReaderProvider"])
    }
}
