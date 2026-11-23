package io.legado.app.config

import io.legado.app.data.entities.AutoTaskRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutoTaskPersistenceContractTest {

    private val root = repositoryRoot()

    @Test
    fun `database and dao keep ordered non-destructive task storage`() {
        val database = file("app/src/main/java/io/legado/app/data/AppDatabase.kt").readText()
        val dao = file("app/src/main/java/io/legado/app/data/dao/AutoTaskRuleDao.kt").readText()
        val autoTask = file("app/src/main/java/io/legado/app/model/AutoTask.kt").readText()
        // 列表订阅旧由 AutoTaskActivity 持有；Compose 重写后归 AutoTaskListViewModel。
        val listViewModel = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt"
        ).readText()
        val databaseVersion = Regex("""version = (\d+)""")
            .find(database)
            ?.groupValues
            ?.get(1)
            ?.toInt()
        assertTrue(requireNotNull(databaseVersion) >= 94)
        assertTrue(database.contains("AutoMigration(from = 93, to = 94)"))
        assertTrue(database.contains("AutoTaskRule::class"))
        assertTrue(dao.contains("ORDER BY customOrder"))
        assertTrue(dao.contains("@Upsert"))
        assertFalse(dao.contains("deleteAll"))
        assertTrue(autoTask.contains("fun reorder("))
        assertMutationChecksLegacyFirst(
            autoTask.substringAfter("fun upsert(").substringBefore("fun delete("),
            "appDb.autoTaskRuleDao.upsert"
        )
        assertMutationChecksLegacyFirst(
            autoTask.substringAfter("fun delete(").substringBefore("fun reorder("),
            "appDb.autoTaskRuleDao.deleteByIds"
        )
        assertMutationChecksLegacyFirst(
            autoTask.substringAfter("fun reorder(").substringBefore("fun updateEnabled("),
            "appDb.autoTaskRuleDao.update"
        )
        assertMutationChecksLegacyFirst(
            autoTask.substringAfter("fun get(").substringBefore("fun upsert("),
            "appDb.autoTaskRuleDao.getById"
        )
        // 列表流订阅点随 Compose 壳迁至 ViewModel：必须直订 DAO 的 flowAll()。
        assertTrue(listViewModel.contains("appDb.autoTaskRuleDao.flowAll()"))
    }

    @Test
    fun `batch cron update changes only cron and refreshes scheduling once`() {
        val dao = file("app/src/main/java/io/legado/app/data/dao/AutoTaskRuleDao.kt").readText()
        val autoTask = file("app/src/main/java/io/legado/app/model/AutoTask.kt").readText()
        // 旧 AutoTaskAdapter 随 Compose 重写拆除；选择集归 ViewModel，批量定时入口归 Screen。
        val listViewModel = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt"
        ).readText()
        val screen = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt"
        ).readText()
        val update = autoTask.substringAfter("fun updateCron(").substringBefore("fun updateRunState(")
        val cronQuery = Regex("""@Query\("([^"]+)"\)\s+fun updateCron""")
            .find(dao)
            ?.groupValues
            ?.get(1)

        assertEquals("UPDATE auto_task_rules SET cron = :cron WHERE id IN (:ids)", cronQuery)
        assertMutationChecksLegacyFirst(update, "appDb.autoTaskRuleDao.updateCron")
        assertTrue(update.contains("ids.chunked(900).sumOf"))
        assertTrue(update.contains("if (changed > 0) AutoTaskScheduler.refresh(context)"))
        assertFalse(update.contains("autoTaskRuleDao.update("))
        assertTrue(listViewModel.contains("CronSchedule.parse(cron)"))
        assertTrue(listViewModel.contains("AutoTask.updateCron(selectedIds(), cron, context)"))
        // 选择集仍是有序字符串集合；旧 Adapter 的 linkedSetOf / DragSelectTouchHelper 已随壳拆除。
        assertTrue(listViewModel.contains("private fun selectedIds(): List<String>"))
        assertTrue(screen.contains("onOpenBatchCron"))
        assertTrue(screen.contains("onBatchCron"))
        assertFalse(listViewModel.contains("DragSelectTouchHelper"))
    }

    @Test
    fun `batch selection actions update only selected state and refresh scheduling once`() {
        val dao = file("app/src/main/java/io/legado/app/data/dao/AutoTaskRuleDao.kt").readText()
        val autoTask = file("app/src/main/java/io/legado/app/model/AutoTask.kt").readText()
        // 旧 auto_task_sel.xml 菜单随 Compose 重写拆除；批量动作归 ViewModel + SelectionBar。
        val listViewModel = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt"
        ).readText()
        val screen = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt"
        ).readText()
        val update = autoTask.substringAfter("fun updateEnabled(").substringBefore("fun updateCron(")
        val enabledQuery = Regex("""@Query\("([^"]+)"\)\s+fun updateEnabled""")
            .find(dao)
            ?.groupValues
            ?.get(1)

        assertEquals(
            "UPDATE auto_task_rules SET enable = :enabled WHERE id IN (:ids)",
            enabledQuery
        )
        assertMutationChecksLegacyFirst(update, "appDb.autoTaskRuleDao.updateEnabled")
        assertTrue(update.contains("ids.chunked(900).sumOf"))
        assertTrue(update.contains("if (changed > 0) AutoTaskScheduler.refresh(context)"))
        assertFalse(update.contains("autoTaskRuleDao.update("))
        assertTrue(listViewModel.contains("AutoTask.updateEnabled(selectedIds(), true, context)"))
        assertTrue(listViewModel.contains("AutoTask.updateEnabled(selectedIds(), false, context)"))
        assertTrue(listViewModel.contains("AutoTask.delete(ids, context)"))
        // SelectionBar 接线：启用 / 停用 / 批量定时 / 删除四个批量动作仍在。
        assertTrue(screen.contains("onEnableSelection"))
        assertTrue(screen.contains("onDisableSelection"))
        assertTrue(screen.contains("onOpenBatchCron"))
        assertTrue(screen.contains("onDeleteSelection"))
        assertFalse(listViewModel.contains("menu_batch_cron"))
        assertFalse(listViewModel.contains("menu_enable_selection"))
        assertFalse(listViewModel.contains("menu_disable_selection"))
    }

    @Test
    fun `clearing automatic task log keeps its last run time`() {
        val dao = file("app/src/main/java/io/legado/app/data/dao/AutoTaskRuleDao.kt").readText()
        val autoTask = file("app/src/main/java/io/legado/app/model/AutoTask.kt").readText()
        // 日志弹窗旧由 Activity showLog 承载；Compose 重写后归 Screen LogSheet + ViewModel.clearLog。
        val screen = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt"
        ).readText()
        val listViewModel = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt"
        ).readText()
        val clearQuery = dao.substringBefore("fun clearRunLog").substringAfterLast("@Query")
        val clearModel = autoTask.substringAfter("fun clearRunLog(")
            .substringBefore("fun updateRunState(")
        val logSheet = screen.substringAfter("private fun LogSheet(")

        assertTrue(clearQuery.contains("SET lastResult = NULL"))
        assertTrue(clearQuery.contains("lastError = NULL"))
        assertTrue(clearQuery.contains("lastLog = NULL"))
        assertFalse(clearQuery.contains("lastRunAt"))
        assertTrue(clearModel.contains("autoTaskRuleDao.clearRunLog(id)"))
        assertTrue(logSheet.contains("task.lastLog ?: task.lastError ?: task.lastResult"))
        assertTrue(logSheet.contains("onClear"))
        assertTrue(listViewModel.contains("AutoTask.clearRunLog(id)"))
        assertFalse(logSheet.contains("lastRunAt"))
    }

    @Test
    fun `automatic task search preserves hidden selections`() {
        // 旧 activity_auto_task.xml / AutoTaskAdapter 随 Compose 重写拆除；
        // 搜索过滤归 ViewModel.visibleRules()，选择集回收归 ViewModel.observeRules()，空态归 Screen。
        val listViewModel = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskListViewModel.kt"
        ).readText()
        val screen = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskScreen.kt"
        ).readText()
        val models = file(
            "app/src/main/java/io/legado/app/ui/autoTask/AutoTaskModels.kt"
        ).readText()
        val observeBlock = listViewModel.substringAfter("private fun observeRules()")
            .substringBefore("fun retry()")

        assertTrue(screen.contains("SearchBox("))
        assertTrue(listViewModel.contains("it.name.contains(query, ignoreCase = true)"))
        assertTrue(screen.contains("visibleRules.isEmpty()"))
        // 选择集随数据流回收：仅保留当前仍存在的任务 id（旧 retainAll 口径）。
        assertTrue(observeBlock.contains("state.selection.intersect(retained)"))
        assertTrue(models.contains("val selection: Set<String>"))
        assertTrue(models.contains("val selectionCount: Int get() = selection.size"))
        assertFalse(observeBlock.contains("retainAll"))
    }

    @Test
    fun `exported room schema contains automatic task table`() {
        val schema = file("app/schemas/io.legado.app.data.AppDatabase/95.json")
        assertTrue("Room schema 95 must be committed", schema.isFile)
        val text = schema.readText()
        assertTrue(text.contains("\"version\": 95"))
        assertTrue(text.contains("\"tableName\": \"auto_task_rules\""))
        assertTrue(text.contains("\"columnName\": \"customOrder\""))
    }

    @Test
    fun `persisted jobs and settings keep lifecycle contract`() {
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        val scheduler = file("app/src/main/java/io/legado/app/service/AutoTaskScheduler.kt").readText()
        val service = file("app/src/main/java/io/legado/app/service/AutoTaskJobService.kt").readText()
        val preferences = file("app/src/main/res/xml/pref_main.xml").readText()
        val myFragment = file("app/src/main/java/io/legado/app/ui/main/my/MyFragment.kt").readText()
        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.permission.BIND_JOB_SERVICE"))
        assertTrue(manifest.contains(".service.AutoTaskJobService"))
        assertTrue(scheduler.contains("setPersisted(true)"))
        assertTrue(scheduler.contains("JOB_ID_FIRST"))
        assertTrue(scheduler.contains("JOB_ID_SECOND"))
        assertTrue(scheduler.contains("cancelPendingJobs(scheduler)"))
        assertTrue(scheduler.contains("AutoTaskSchedulePolicy.nextAfterBatchAt"))
        assertTrue(service.contains("cancelPendingAlternate"))
        assertTrue(service.contains("return AutoTaskScheduler.shouldRetry(this)"))
        assertTrue(service.contains("start = CoroutineStart.LAZY"))
        assertTrue(service.contains("jobs.remove(jobId, currentJob)"))
        val scheduleNext = service.indexOf(
            "val nextScheduled = AutoTaskScheduler.refresh(this, afterBatch = true)"
        )
        val runDueTasks = service.indexOf("AutoTaskRunner.runDueTasks(this@AutoTaskJobService)")
        assertTrue(scheduleNext >= 0)
        assertTrue(runDueTasks > scheduleNext)
        val saveJob = service.indexOf("jobs[jobId] = currentJob")
        val startJob = service.indexOf("currentJob.start()")
        assertTrue(saveJob >= 0)
        assertTrue(startJob > saveJob)
        val completion = service.substringAfter("} finally {")
            .substringBefore("override fun onStopJob")
        val stoppedFlag = completion.indexOf("stoppedJobs.remove(jobId)")
        val completionRelease = completion.indexOf("AutoTaskScheduler.markStopped(jobId)")
        val jobFinished = completion.indexOf("jobFinished(params, retry)")
        assertTrue(stoppedFlag >= 0)
        assertTrue(completionRelease > stoppedFlag)
        assertTrue(jobFinished > completionRelease)
        val destroy = service.substringAfter("override fun onDestroy()")
        val stopMarker = destroy.indexOf("stoppedJobs.add(jobId)")
        val cancelJob = destroy.indexOf("jobs.remove(jobId)?.cancel()")
        val releaseSlot = destroy.indexOf("AutoTaskScheduler.markStopped(jobId)")
        val cancelScope = destroy.indexOf("scope.cancel()")
        assertTrue(stopMarker >= 0)
        assertTrue(cancelJob > stopMarker)
        assertTrue(releaseSlot > cancelJob)
        assertTrue(cancelScope > releaseSlot)
        assertTrue(preferences.contains("android:key=\"autoTaskService\""))
        assertTrue(myFragment.contains("val appContext = requireContext().applicationContext"))
        assertTrue(myFragment.contains("Coroutine.async { AutoTaskScheduler.refresh(appContext) }"))
        assertTrue(myFragment.contains("AutoTaskScheduler.cancelAll(appContext)"))
    }

    @Test
    fun `startup refresh stays in background and notification denial is non-fatal`() {
        val app = file("app/src/main/java/io/legado/app/App.kt").readText()
        val protocol = file("app/src/main/java/io/legado/app/model/AutoTaskProtocol.kt").readText()
        val runner = file("app/src/main/java/io/legado/app/model/AutoTaskRunner.kt").readText()
        val asyncIndex = app.indexOf("Coroutine.async {")
        val refreshIndex = app.indexOf("AutoTaskScheduler.refresh(this@App)")
        assertTrue(asyncIndex >= 0 && refreshIndex > asyncIndex)
        assertTrue(protocol.contains("areNotificationsEnabled()"))
        assertTrue(protocol.contains("catch (error: RuntimeException)"))
        assertTrue(protocol.contains("Notification skipped"))
        assertFalse(protocol.contains("BookController.refreshToc"))
        assertTrue(protocol.contains("WebBook.getContentAwait"))
        assertTrue(protocol.contains("newContentChapters"))
        assertFalse(protocol.contains("CacheBook.start"))
        assertFalse(protocol.contains("runBlocking"))
        assertTrue(protocol.contains("WebBook.getBookInfoAwait"))
        assertTrue(protocol.contains("WebBook.getChapterListAwait"))
        assertTrue(protocol.contains("appDb.runInTransaction"))
        assertTrue(runner.contains("runScriptWithContext {"))
        assertTrue(runner.contains("error.autoTaskCancellation()?.let { throw it }"))
        assertTrue(runner.contains("is RhinoInterruptError -> cause as? CancellationException"))
    }

    @Test
    fun `backup and restore include persisted automatic tasks`() {
        val backup = file("app/src/main/java/io/legado/app/help/storage/Backup.kt").readText()
        val restore = file("app/src/main/java/io/legado/app/help/storage/Restore.kt").readText()

        assertTrue(backup.contains("\"autoTask.json\""))
        assertTrue(backup.contains("appDb.autoTaskRuleDao.all()"))
        assertTrue(restore.contains("fileToListT<AutoTaskRule>(path, \"autoTask.json\")"))
        assertTrue(restore.contains("appDb.autoTaskRuleDao.upsert"))
        val preferenceRestore =
            restore.indexOf("readPreferenceSnapshot(appCtx, path, \"config\")")
        val taskUpsert = restore.indexOf("appDb.autoTaskRuleDao.upsert")
        val scheduleRefresh = restore.indexOf("AutoTaskScheduler.refresh(appCtx)")
        assertTrue(preferenceRestore >= 0)
        assertTrue(taskUpsert > preferenceRestore)
        assertTrue(scheduleRefresh > taskUpsert)
        assertFalse(restore.contains("autoTaskRuleDao.delete"))
    }

    @Test
    fun `automatic task backup json preserves every persisted field`() {
        val rule = AutoTaskRule(
            id = "task-id",
            name = "task-name",
            enable = false,
            cron = "1 2 3 4 5",
            loginUrl = "https://example.com/login",
            loginUi = "login-ui",
            loginCheckJs = "login-check",
            comment = "comment",
            script = "script",
            header = "header",
            jsLib = "library",
            concurrentRate = "2/1000",
            enabledCookieJar = false,
            customOrder = 7,
            lastRunAt = 8L,
            lastResult = "result",
            lastError = "error",
            lastLog = "log",
        )

        val restored = GSON.fromJsonArray<AutoTaskRule>(GSON.toJson(listOf(rule)))
            .getOrThrow()
            .single()

        assertEquals(rule, restored)
    }

    private fun file(path: String) = File(root, path)

    private fun assertMutationChecksLegacyFirst(function: String, mutation: String) {
        val legacyCheck = function.indexOf("all()")
        val mutationCall = function.indexOf(mutation)
        assertTrue(legacyCheck >= 0)
        assertTrue(mutationCall > legacyCheck)
    }

    private fun repositoryRoot(): File {
        val userDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDirectory) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
    }
}
