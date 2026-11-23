package io.legado.app.ui.about

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadRecordAuthorLayoutTest {

    private val activitySource: String by lazy {
        readProjectFile("src/main/java/io/legado/app/ui/about/ReadRecordActivity.kt")
    }

    @Test
    fun `read record row reserves an optional author line before reading time`() {
        val rowStart = activitySource.indexOf("private fun RecordRow(")
        assertTrue("RecordRow composable missing", rowStart >= 0)
        val row = activitySource.substring(rowStart)

        val authorIndex = row.indexOf("context.getString(R.string.author_show, author)")
        val timeRowIndex = row.indexOf("formatDuring(record.readTime")
        assertTrue("author line missing in row", authorIndex >= 0)
        assertTrue("reading time block missing in row", timeRowIndex >= 0)
        assertTrue("author line must precede reading time", authorIndex < timeRowIndex)
    }

    @Test
    fun `read record last time follows the tallest reading time field`() {
        val rowStart = activitySource.indexOf("private fun RecordRow(")
        assertTrue("RecordRow composable missing", rowStart >= 0)
        val row = activitySource.substring(rowStart)

        val readTimeIndex = row.indexOf("formatDuring(record.readTime")
        val lastReadIndex = row.indexOf("dateFormat.format(record.lastRead)")
        assertTrue("reading time field missing", readTimeIndex >= 0)
        assertTrue("last read field missing", lastReadIndex >= 0)
        assertTrue("last read must follow reading time", readTimeIndex < lastReadIndex)
    }

    private fun readProjectFile(pathInApp: String): String {
        return sequenceOf(File(pathInApp), File("app/$pathInApp"))
            .firstOrNull(File::isFile)
            ?.readText()
            .orEmpty()
    }
}
