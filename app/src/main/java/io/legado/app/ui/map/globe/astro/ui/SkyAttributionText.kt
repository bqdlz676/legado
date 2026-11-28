package io.legado.app.ui.map.globe.astro.ui

/**
 * 星空数据署名文案（纯 JVM 可测，不含 Compose 类型）。
 *
 * 合规口径（台账 S-04：HYG Database v41，CC BY-SA 4.0）：BY-SA 要求署名 + 以同许可
 * 再分发派生数据。展示侧四要素缺一不可——数据名、作者/来源及其仓库链接、许可名与许可
 * 链接、本作品衍生自该数据的表述。文案逐字落台账，不改写、不翻译许可名。
 */
object SkyAttributionText {

    /** 数据名。 */
    const val DATA_NAME: String = "HYG Database v41"

    /** 作者 / 来源署名。 */
    const val AUTHOR: String = "astronexus"

    /** 来源仓库链接。 */
    const val SOURCE_URL: String = "https://github.com/astronexus/HYG-Database"

    /** 许可名。 */
    const val LICENSE_NAME: String = "CC BY-SA 4.0"

    /** 许可链接。 */
    const val LICENSE_URL: String = "https://creativecommons.org/licenses/by-sa/4.0/"

    /** 派生表述：本作品衍生自该数据。 */
    const val DERIVATIVE_NOTICE: String = "本作品衍生自该数据"

    /** 来源行：数据名 · 作者。 */
    val sourceLine: String = "数据来源：$DATA_NAME · $AUTHOR"

    /** 许可行：许可名 · 许可链接。 */
    val licenseLine: String = "许可：$LICENSE_NAME · $LICENSE_URL"

    /** 完整署名串（四要素拼接，供自检 / 首启许可页复用）。 */
    val fullNotice: String = "$sourceLine\n$licenseLine\n$DERIVATIVE_NOTICE"

    /**
     * 行 → 点击应打开的 URL；不可点行返回 null。
     *
     * 纯 JVM 映射：展示组件（`SkyAttribution.kt`）与单测共用同一份「哪行开哪个链接」
     * 口径，组件不另写一份常量引用，避免双源漂移；衍生表述行按 S-04 义务不可点，显式
     * 返回 null。
     */
    fun urlForLine(line: String): String? = when (line) {
        sourceLine -> SOURCE_URL
        licenseLine -> LICENSE_URL
        else -> null
    }
}
