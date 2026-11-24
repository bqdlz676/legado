package io.legado.app.ui.components.inkslate.contract

/**
 * S-12 画板 InkSlate · 工具集契约枚举（切片-12 §8.1 逐字落地）。
 *
 * 保留项（§8.1 原文逐条）：画笔 · 直线 · 箭头 · 矩形 · 圆 · 文字 · 橡皮 ·
 * 撤销/重做 · 图层（底图+墨迹两层）· PNG/SVG 导出。
 *
 * 砍掉项（§8.1，本枚举内不出现对应入口，也不预留兼容位）：抓手 · 菱形 · 插入图片 ·
 * 激光笔 · 协作 · 填充色 · 透明度 · 粗糙度 · 字体选择器 · 字号多档 · 元素锁定。
 *
 * 本文件只做工具集契约，不引用任何 Compose / Android 类型，可在普通 JVM 单测中断言；
 * 绘制与手势由既有 InkSlateCanvas / InkSlateToolbar 承载，本枚举不改动它们。
 */
enum class InkTool(val label: String) {

    /** ✏️ 画笔 */
    BRUSH("画笔"),

    /** 📏 直线 */
    LINE("直线"),

    /** ➡️ 箭头 */
    ARROW("箭头"),

    /** ⬜ 矩形 */
    RECT("矩形"),

    /** ⭕ 圆 */
    CIRCLE("圆"),

    /** 🔤 文字 */
    TEXT("文字"),

    /** 🧹 橡皮 */
    ERASER("橡皮"),

    /** ↩↪ 撤销/重做（§8.1 合并为一项；§8.2 底行 ↩ 重做分列操作） */
    UNDO_REDO("撤销/重做"),

    /** 图层：底图 + 墨迹两层（§8.1 原文括注） */
    LAYER("图层（底图+墨迹两层）"),

    /** PNG/SVG 导出（§8.1 保留导出口径） */
    EXPORT("PNG/SVG 导出");

    companion object {
        /** §8.1 保留项总数，供单测与原文逐项对账 */
        const val SPEC_COUNT: Int = 10

        /** 工具栏全部露出，没有「⋯ 更多」（§8.2）；顺序即 §8.1 原文顺序 */
        val all: List<InkTool> get() = entries
    }
}
