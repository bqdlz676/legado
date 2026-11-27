package io.legado.app.ui.main.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * B6-03 · DiscoverViewModel 编排单测（纯 JVM，不引 Android 类、不拉起 Compose 运行时）。
 *
 * 覆盖：五区初始状态、空数据、坏条目过滤计数、离线 / 异常分支、进度与掌握态不自存、
 * 数据源注入替身仅作调用计数、超长文本透传、并发刷新一致。每条断言带实际谓词。
 *
 * 测试替身只用于注入端口与计数，不顶替生产路径的真实只读入口。
 */
class DiscoverViewModelTest {

    private val sourceRoot: File by lazy {
        sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
    }

    // ─────────────────────────── 端口替身（带调用计数） ───────────────────────────

    private class CountingPort<S>(private val items: List<S>, private val boom: Throwable? = null) {
        val calls: ConcurrentLinkedQueue<Unit> = ConcurrentLinkedQueue()

        fun load(): List<S> {
            calls.add(Unit)
            if (boom != null) throw boom
            return items
        }
    }

    private fun vm(
        battle: List<BattleSource> = emptyList(),
        classic: List<ClassicSource> = emptyList(),
        topic: List<TopicSource> = emptyList(),
        hero: List<HeroSource> = emptyList(),
        notebook: List<NotebookEntrySource> = emptyList(),
        battleBoom: Throwable? = null,
    ): DiscoverViewModel {
        val bp = CountingPort(battle, battleBoom)
        val cp = CountingPort(classic)
        val tp = CountingPort(topic)
        val hp = CountingPort(hero)
        val np = CountingPort(notebook)
        val vm = DiscoverViewModel(
            battleSource = BattleSourcePort { bp.load() },
            classicSource = ClassicSourcePort { cp.load() },
            topicSource = TopicSourcePort { tp.load() },
            heroSource = HeroSourcePort { hp.load() },
            notebookSource = NotebookSourcePort { np.load() },
        )
        return vm
    }

    // ─────────────────────────── 初始状态 ───────────────────────────

    @Test
    fun `initial snapshot has five loading empty regions`() {
        val state = vm().snapshot()
        assertEquals(RegionStatus.LOADING, state.battle.status)
        assertEquals(RegionStatus.LOADING, state.classic.status)
        assertEquals(RegionStatus.LOADING, state.topic.status)
        assertEquals(RegionStatus.LOADING, state.hero.status)
        assertEquals(RegionStatus.LOADING, state.notebook.status)
        assertTrue(state.battle.items.isEmpty())
        assertTrue(state.classic.items.isEmpty())
        assertTrue(state.topic.items.isEmpty())
        assertTrue(state.hero.items.isEmpty())
        assertTrue(state.notebook.items.isEmpty())
        assertFalse(state.battle.offline)
        assertEquals("", state.battle.errorMessage)
    }

    // ─────────────────────────── 成功路径 ───────────────────────────

    @Test
    fun `battle refresh fills content with mapped titles`() {
        val model = vm(
            battle = listOf(
                BattleSource(title = "长平之战", era = "前260", context = "秦赵对峙"),
                BattleSource(title = "淝水之战", era = "383", context = "草木皆兵"),
            ),
        )
        model.refreshBattle()
        val state = model.battleState()
        assertEquals(RegionStatus.CONTENT, state.status)
        assertEquals(2, state.items.size)
        assertEquals("长平之战", state.items[0].title)
        assertEquals("前260", state.items[0].subtitle)
        assertEquals("秦赵对峙", state.items[0].description)
        assertEquals(0, state.items[0].watchedStage)
        assertEquals(0, state.dropped)
        assertFalse(state.offline)
    }

    @Test
    fun `empty sources yield five empty regions`() {
        val model = vm()
        model.refresh()
        val state = model.snapshot()
        assertEquals(RegionStatus.EMPTY, state.battle.status)
        assertEquals(RegionStatus.EMPTY, state.classic.status)
        assertEquals(RegionStatus.EMPTY, state.topic.status)
        assertEquals(RegionStatus.EMPTY, state.hero.status)
        assertEquals(RegionStatus.EMPTY, state.notebook.status)
        assertTrue(state.battle.items.isEmpty())
        // 生字本区口径：不是「零卡」，而是恒带一张入口卡，空态由 count==0 表达（本区只计数、
        // 不做掌握态判定）。这是 B6-05 装配时要注意的：EMPTY 状态也有一张卡，壳层不得再叠空态卡。
        assertEquals(1, state.notebook.items.size)
        assertEquals(0, state.notebook.items.first().count)
    }

    // ─────────────────────────── 坏条目 ───────────────────────────

    @Test
    fun `bad battle entries are dropped while good ones remain`() {
        val model = vm(
            battle = listOf(
                BattleSource(title = "长平之战", phases = listOf("对峙")),
                BattleSource(title = "   "),
                BattleSource(title = "垓下之战", rounds = 30),
            ),
        )
        model.refreshBattle()
        val state = model.battleState()
        assertEquals(RegionStatus.CONTENT, state.status)
        assertEquals(2, state.items.size)
        assertEquals(1, state.dropped)
        assertEquals("长平之战", state.items[0].title)
        assertEquals("垓下之战", state.items[1].title)
    }

    @Test
    fun `all bad entries yield empty region with dropped count`() {
        val model = vm(
            classic = listOf(
                ClassicSource(title = "  "),
                ClassicSource(title = ""),
            ),
        )
        model.refreshClassic()
        val state = model.classicState()
        assertEquals(RegionStatus.EMPTY, state.status)
        assertTrue(state.items.isEmpty())
        assertEquals(2, state.dropped)
    }

    // ─────────────────────────── 离线 / 异常分支 ───────────────────────────

    @Test
    fun `source failure marks offline on empty region`() {
        val model = vm(battleBoom = RuntimeException("断网"))
        model.refreshBattle()
        val state = model.battleState()
        assertEquals(RegionStatus.OFFLINE, state.status)
        assertTrue(state.offline)
        assertTrue(state.items.isEmpty())
        assertEquals("断网", state.errorMessage)
    }

    @Test
    fun `source failure keeps previous content in error state`() {
        val good = listOf(BattleSource(title = "长平之战", phases = listOf("对峙")))
        var failNext = false
        val battlePort = object : BattleSourcePort {
            override fun load(): List<BattleSource> {
                if (failNext) throw RuntimeException("二次刷新失败")
                return good
            }
        }
        val model = DiscoverViewModel(
            battleSource = battlePort,
            classicSource = ClassicSourcePort { CountingPort(emptyList<ClassicSource>()).load() },
            topicSource = TopicSourcePort { CountingPort(emptyList<TopicSource>()).load() },
            heroSource = HeroSourcePort { CountingPort(emptyList<HeroSource>()).load() },
            notebookSource = NotebookSourcePort { CountingPort(emptyList<NotebookEntrySource>()).load() },
        )
        model.refreshBattle()
        assertEquals(RegionStatus.CONTENT, model.battleState().status)
        failNext = true
        model.refreshBattle()
        val state = model.battleState()
        assertEquals(RegionStatus.ERROR, state.status)
        assertTrue(state.offline)
        assertEquals(1, state.items.size)
        assertEquals("长平之战", state.items.first().title)
    }

    // ─────────────────────────── 生字本计数 ───────────────────────────

    @Test
    fun `notebook refresh maps entry count and empty count yields empty`() {
        val entries = listOf(
            NotebookEntrySource("之", "史记", "五帝本纪", 1L),
            NotebookEntrySource("乎", "左传", "隐公元年", 2L),
        )
        val model = vm(notebook = entries)
        model.refreshNotebook()
        val state = model.notebookState()
        assertEquals(RegionStatus.CONTENT, state.status)
        assertEquals(2, state.items.first().count)

        val emptyModel = vm()
        emptyModel.refreshNotebook()
        assertEquals(RegionStatus.EMPTY, emptyModel.notebookState().status)
        assertEquals(0, emptyModel.notebookState().items.first().count)
    }

    // ─────────────────────────── 进度 / 掌握态不自存 ───────────────────────────

    @Test
    fun `vm does not store mastered or progress state`() {
        // 反射版（KClass.members）在本仓单测 classpath 上没有 kotlin-reflect，运行期抛
        // KotlinReflectionNotSupportedError；改为源码文本断言，判据不变、且不引依赖。
        val vmSource = File(sourceRoot, "io/legado/app/ui/main/discover/DiscoverViewModel.kt").readText()
        // 先剥注释面：本类 KDoc 正文本来就写「不伪造 mastered=1」，整文件 contains 会自证成红。
        val vmDeclarations = vmSource.lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/**") }
            .filter { it.contains("private var ") || it.contains("val ") && it.contains(": ") }
            .joinToString("\n")
        assertFalse("VM 不得持有 mastered 掌握标记位", vmDeclarations.contains("mastered"))
        assertFalse("VM 不得自存已看进度计数", vmDeclarations.contains("watchedStage"))
        assertFalse("VM 不得自存进度比例", vmDeclarations.contains("progress"))
        val modelsSource = File(sourceRoot, "io/legado/app/ui/main/discover/DiscoverModels.kt").readText()
        val cardBlock = modelsSource
            .substringAfter("data class NotebookEntryCard(")
            .substringBefore(")")
        val cardFields = cardBlock.split(',')
            .map { it.trim() }
            .filter { it.startsWith("val ") }
            .map { it.substringAfter("val ").substringBefore(":").trim() }
        assertEquals(listOf("count"), cardFields)
    }

    // ─────────────────────────── 端口调用计数 ───────────────────────────

    @Test
    fun `source port invocation counted on refresh`() {
        val battle = CountingPort(listOf(BattleSource(title = "长平之战")))
        val classic = CountingPort(emptyList<ClassicSource>())
        val topic = CountingPort(emptyList<TopicSource>())
        val hero = CountingPort(emptyList<HeroSource>())
        val notebook = CountingPort(emptyList<NotebookEntrySource>())
        val model = DiscoverViewModel(
            battleSource = BattleSourcePort { battle.load() },
            classicSource = ClassicSourcePort { classic.load() },
            topicSource = TopicSourcePort { topic.load() },
            heroSource = HeroSourcePort { hero.load() },
            notebookSource = NotebookSourcePort { notebook.load() },
        )
        model.refresh()
        assertEquals(1, battle.calls.size)
        assertEquals(1, classic.calls.size)
        assertEquals(1, topic.calls.size)
        assertEquals(1, hero.calls.size)
        assertEquals(1, notebook.calls.size)
        model.refreshBattle()
        assertEquals(2, battle.calls.size)
    }

    // ─────────────────────────── 超长文本 ───────────────────────────

    @Test
    fun `long title passes through verbatim without truncation`() {
        val longTitle = "长".repeat(200)
        val model = vm(battle = listOf(BattleSource(title = longTitle, phases = listOf("x"))))
        model.refreshBattle()
        val item = model.battleState().items.first()
        assertEquals(200, item.title.length)
        assertEquals(longTitle, item.title)
    }

    // ─────────────────────────── 并发刷新 ───────────────────────────

    @Test
    fun `concurrent refresh stays consistent without crash`() {
        val model = vm(
            battle = listOf(
                BattleSource(title = "长平之战", phases = listOf("对峙")),
                BattleSource(title = "淝水之战"),
            ),
            classic = listOf(ClassicSource(title = "史记")),
            topic = listOf(TopicSource(title = "春秋笔法", allusionCount = 28)),
            hero = emptyList(),
            notebook = listOf(NotebookEntrySource("之", "史记", "五帝本纪", 1L)),
        )
        val threads = (1..8).map {
            thread { repeat(20) { model.refresh() } }
        }
        threads.forEach { it.join() }
        val state = model.snapshot()
        assertEquals(2, state.battle.items.size)
        assertEquals(1, state.classic.items.size)
        assertEquals(1, state.topic.items.size)
        assertEquals(0, state.hero.items.size)
        assertEquals(1, state.notebook.items.size)
        assertTrue(state.battle.items.all { it.title.isNotBlank() })
    }
}
