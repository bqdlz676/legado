package io.legado.app.ui.book.source

// Q474：从已拆除的 BookSourceCheckUiTest.kt 拆出的两条执行层校验会话回归。
// 为什么只留这两条：旧文件其余用例依附已随 Compose 重写删除的 RecyclerView / Adapter /
// 旧行 ViewBinding / 旧菜单；本文件只触 DAO、CheckSource、Debug 会话等保留执行层，
// 不引用任何 View 层符号。拆除依据、回归点与接管映射见 docs/修复-androidTest悬空ViewBinding.md。

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.model.CheckSource
import io.legado.app.model.Debug
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BookSourceCheckSessionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val id = UUID.randomUUID().toString()
    private val group = "Check session $id"
    private val sources = listOf(BookSource(
        bookSourceUrl = "https://check.invalid/$id/0",
        bookSourceName = "Check fixture 0",
        bookSourceGroup = group,
        bookSourceComment = "Original comment",
    ))
    // 为什么快照标志位：CheckSource.checkXxx 是进程内可变静态，用例改完必须还原，
    // 否则同进程后续校验用例继承被篡改的域名/搜索/发现等开关。
    private val savedFlags = listOf(
        CheckSource.checkDomain, CheckSource.checkSearch, CheckSource.checkDiscovery,
        CheckSource.checkInfo, CheckSource.checkCategory, CheckSource.checkContent,
    )

    @Before fun setup() {
        appDb.bookSourceDao.insert(*sources.toTypedArray())
    }

    @After fun cleanup() {
        Debug.currentCheckSession()?.let { CheckSource.stop(context, it) }
        waitUntil { !Debug.isChecking }
        appDb.bookSourceDao.delete(*sources.toTypedArray())
        CheckSource.checkDomain = savedFlags[0]
        CheckSource.checkSearch = savedFlags[1]
        CheckSource.checkDiscovery = savedFlags[2]
        CheckSource.checkInfo = savedFlags[3]
        CheckSource.checkCategory = savedFlags[4]
        CheckSource.checkContent = savedFlags[5]
    }

    @Test fun realServicePersistsFailureAndSuccessWithoutChangingGroups() = runBlocking {
        CheckSource.checkDomain = false
        CheckSource.checkDiscovery = false
        CheckSource.checkInfo = false
        CheckSource.checkCategory = false
        CheckSource.checkContent = false
        CheckSource.checkSearch = true // Missing search rule fails without a network request.
        val dao = appDb.bookSourceDao
        val url = sources[0].bookSourceUrl
        var session = Debug.tryStartCheckSession()!!
        CheckSource.start(context, listOf(dao.getBookSourcePart(url)!!), session)
        waitUntil { !Debug.isChecking(session) }
        assertEquals("FAILED", dao.getCheckState(url)!!.status)
        assertEquals(group, dao.getBookSource(url)!!.bookSourceGroup)
        assertEquals("Original comment", dao.getBookSource(url)!!.bookSourceComment)
        CheckSource.checkSearch = false
        session = Debug.tryStartCheckSession()!!
        CheckSource.start(context, listOf(dao.getBookSourcePart(url)!!), session)
        waitUntil { !Debug.isChecking(session) }
        assertEquals("PASSED", dao.getCheckState(url)!!.status)
    }

    @Test fun nonHttpDomainIsUncheckableAfterOtherChecksPass() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "local://check-$id",
            bookSourceName = "Local check fixture",
            bookSourceGroup = group,
            bookSourceComment = "Original comment",
            mainJs = "function search(key, page) { return [{ name: 'fixture', bookUrl: 'fixture' }]; }",
        )
        val dao = appDb.bookSourceDao
        dao.insert(source)
        try {
            CheckSource.checkDomain = true
            CheckSource.checkSearch = true
            CheckSource.checkDiscovery = false
            CheckSource.checkInfo = false
            CheckSource.checkCategory = false
            CheckSource.checkContent = false
            val session = Debug.tryStartCheckSession()!!
            CheckSource.start(context, listOf(dao.getBookSourcePart(source.bookSourceUrl)!!), session)
            waitUntil { !Debug.isChecking(session) }
            assertEquals("UNCHECKABLE", dao.getCheckState(source.bookSourceUrl)!!.status)
            assertEquals(source.bookSourceGroup, dao.getBookSource(source.bookSourceUrl)!!.bookSourceGroup)
            assertEquals(source.bookSourceComment, dao.getBookSource(source.bookSourceUrl)!!.bookSourceComment)
            source.mainJs = "function search(key, page) { return []; }"
            dao.update(source)
            val failedSession = Debug.tryStartCheckSession()!!
            CheckSource.start(context, listOf(dao.getBookSourcePart(source.bookSourceUrl)!!), failedSession)
            waitUntil { !Debug.isChecking(failedSession) }
            assertEquals("FAILED", dao.getCheckState(source.bookSourceUrl)!!.status)
        } finally {
            dao.delete(source)
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        assertTrue("Condition did not become true", predicate())
    }
}
