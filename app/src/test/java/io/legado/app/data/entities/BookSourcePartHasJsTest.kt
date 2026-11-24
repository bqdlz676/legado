package io.legado.app.data.entities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * hasJs 投影契约测试。
 *
 * 数据层（BookSourcePart 视图 / Room 迁移 / schema）原样保留；旧 BookSourceAdapter 增量刷新与
 * item_book_source.xml 角标布局随 RecyclerView 壳拆除，角标改由 SourceManageRow 按 state.hasJs
 * 条件拼装，本类断言指向 Compose 行实现。
 */
class BookSourcePartHasJsTest {

    private val partSource = projectFile(
        "src/main/java/io/legado/app/data/entities/BookSourcePart.kt"
    ).readText()
    private val normalizedPartSource = partSource.replace(Regex("\\s+"), " ")
    private val databaseSource = projectFile(
        "src/main/java/io/legado/app/data/AppDatabase.kt"
    ).readText()
    private val schemaSource = projectFile(
        "schemas/io.legado.app.data.AppDatabase/93.json"
    ).readText()
    private val rowSource = projectFile(
        "src/main/java/io/legado/app/ui/book/source/manage/SourceManageRow.kt"
    ).readText()
    private val stringsSource = projectFile(
        "src/main/res/values/strings.xml"
    ).readText()

    @Test
    fun `database view exposes js source state`() {
        assertTrue(
            partSource.contains("(mainJs is not null and trim(mainJs) <> '') hasJs")
        )
        assertTrue(
            normalizedPartSource.contains(
                "eventListener, bookSourceType, " +
                    "(mainJs is not null and trim(mainJs) <> '') hasJs"
            )
        )
        assertFalse(BookSourcePart().hasJs)
        assertTrue(BookSourcePart(hasJs = true).hasJs)
    }

    @Test
    fun `schema history preserves js source projection`() {
        val databaseVersion = Regex("""version = (\d+)""")
            .find(databaseSource)
            ?.groupValues
            ?.get(1)
            ?.toInt()
        assertTrue(requireNotNull(databaseVersion) >= 93)
        assertTrue(databaseSource.contains("AutoMigration(from = 92, to = 93)"))
        assertTrue(schemaSource.contains("\"version\": 93"))
        assertTrue(
            schemaSource.contains("(mainJs is not null and trim(mainJs) <> '') hasJs")
        )
    }

    @Test
    fun `adapter refreshes js badge without leaving hidden spacing`() {
        // Compose 行按 hasJs 条件把 JS 角标（走字符串资源）追加进 badges 列表；无角标时不预留占位槽位。
        assertTrue(rowSource.contains("if (source.hasJs) badges += stringResource(R.string.js_source_badge)"))
        assertTrue(rowSource.contains("val badges = mutableListOf<String>()"))
        // 名称列占满剩余权重，角标只在有内容时随 badges 渲染（旧 gone() 隐藏间距关注点的新落点）。
        assertTrue(rowSource.contains("modifier = Modifier.weight(1f)"))
        assertFalse(rowSource.contains("tvJsBadge"))
    }

    @Test
    fun `source name is constrained through an initially gone js badge`() {
        // 角标文案资源保留；行内条件拼装即旧“初始 gone、有 JS 才显示”的 Compose 等价物。
        assertTrue(stringsSource.contains("<string name=\"js_source_badge\">JS</string>"))
        val badgeAppend = rowSource.indexOf("if (source.hasJs) badges += stringResource(R.string.js_source_badge)")
        assertTrue("js badge must be gated by hasJs", badgeAppend >= 0)
        // 名称列权重在前、角标列表在后，名称不被固定角标宽度挤压。
        assertTrue(rowSource.indexOf("weight(1f)") in 0..<badgeAppend)
    }

    private fun projectFile(pathInApp: String): File {
        return sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .first { it.isFile }
    }
}
