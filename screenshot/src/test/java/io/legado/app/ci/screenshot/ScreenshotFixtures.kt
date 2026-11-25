package io.legado.app.ci.screenshot

import io.legado.app.ui.main.bookshelf.ShelfBookUi

/**
 * 截图基建专用离线夹具（纯 JVM，不碰 Room / 网络 / Context）。
 *
 * 为什么独立成对象：截图测试只喂展示模型，不接 ViewModel；夹具集中在此便于后续扩屏时复用同一批样例文案。
 * 文案取古典篇目，贴合东方美学口径，仅用于出帧观感判定，不代表真实书架数据。
 */
object ScreenshotFixtures {

    private val titles = listOf("洛神赋", "兰亭集序", "桃花源记", "岳阳楼记", "滕王阁序", "归去来兮辞")
    private val authors = listOf("曹植", "王羲之", "陶渊明", "范仲淹", "王勃", "陶渊明")

    /** 构造 [count] 条书架展示模型；进度 / 百分比按序号兜底，字段缺失不抛异常。 */
    fun books(count: Int): List<ShelfBookUi> = List(count) { i ->
        ShelfBookUi(
            id = "screenshot-book-$i",
            title = titles[i % titles.size],
            author = authors[i % authors.size],
            chapterText = "第 ${i + 1} 章",
            progress = ((i + 1).toFloat() / titles.size).coerceIn(0f, 1f),
            percentText = "${(((i + 1).toFloat() / titles.size) * 100).toInt()}%",
            isNew = i % 2 == 0,
        )
    }
}
