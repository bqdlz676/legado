package io.legado.app.ui.main.bookshelf.search

import io.legado.app.ui.components.ComponentPalette

/**
 * S-13 书架页下拉搜索浮层 · 容器参数单源（平台无关层，纯数值，不引用任何 Android / Compose 类型，
 * 普通 JVM 单测可直接断言）。
 *
 * 〔锚点原文〕
 *  - `docs/落文-规格切片/切片-13-搜索页与字体.md:7`：「九、搜索页居中一个输入框，无搜索按钮
 *    输入框下方一行字（云灰 12sp）："搜书名、作者、或正文中的任意一句" 输入时下方渐显结果
 *    列表，匹配文字片段用朱砂高亮 纯文本列表，不配图」
 *  - `docs/问题清单.md:142`（D25）：「搜索做书架页下拉浮层，禁独立搜索页」。
 *  - `docs/立项-冲突裁决.md` 第七节 B2：裁决取书架页下拉浮层；「无搜索按钮」的意图（禁搜索入口
 *    按钮）保留，独立 Search Activity 作废。
 *
 * 〔为什么参数集中在一处〕
 * 提示语字号、提示语颜色、高亮色、浮层最大高度比例只在本表定义；将来浮层接线时渲染代码只允许
 * 引用本表，不在画面里另写字号、色值或高度比，避免渲染层、既有 token 副本、单测三处口径漂移。
 *
 * 〔颜色口径〕
 * 提示语云灰取 [ComponentPalette.TEXT_TERTIARY]，匹配片段朱砂取 [ComponentPalette.TEXT_ACCENT]，
 * 本文件不写任何十六进制色值。
 *
 * 〔本单边界〕
 * 本对象只管浮层外观参数；书名 / 作者 / 正文命中判定、片段高亮与排序归既有
 * `ui.main.search` 检索栈（SearchMatcher / SearchMerger / SearchReducer）；
 * 历史词、热门词、联网书源检索不在本单范围（归既有 D1~D7 链路）。
 */
object SearchOverlayParams {

    /**
     * 提示语字号 sp：12sp（切片-13:7「云灰 12sp」）。
     * sp ↔ px 换算留给将来渲染层接入时自行完成，本对象不持有 Android TypedValue。
     */
    const val HINT_TEXT_SIZE_SP: Int = 12

    /** 提示语颜色：云灰，取 [ComponentPalette.TEXT_TERTIARY]（切片-13:7「云灰」）。 */
    val hintTextColor: Long get() = ComponentPalette.TEXT_TERTIARY

    /** 匹配片段高亮色：朱砂，取 [ComponentPalette.TEXT_ACCENT]（切片-13:7「朱砂高亮」）。 */
    val highlightColor: Long get() = ComponentPalette.TEXT_ACCENT

    /**
     * 浮层最大高度占屏高比例：0.6 默认。
     *
     * 规格未给浮层高度比例（切片-13:7 只给输入框 / 提示语 / 结果列表 / 高亮色，未写高度），
     * 此处取默认 0.6；接线单如按 S-11 半屏 Sheet 中段 0.60f 对齐可在引用处核对，
     * 本常数不做 dp / px 换算，也不替代浮层自身的分段回弹逻辑。
     */
    const val MAX_HEIGHT_RATIO: Float = 0.6f

    /**
     * 输入框占位提示语（切片-13:7 原文逐字，未改写）：
     * 「搜书名、作者、或正文中的任意一句」。
     */
    const val PLACEHOLDER_TEXT: String = "搜书名、作者、或正文中的任意一句"

    // 口径注记：按切片-13:7「无搜索按钮」与 B2 裁决，本参数单源不设任何「搜索按钮」字段；
    // 浮层输入即查，提交动作由输入框文本变化驱动，不出现独立按钮入口。
}
