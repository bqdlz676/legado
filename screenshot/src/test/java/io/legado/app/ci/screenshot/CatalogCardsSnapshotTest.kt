package io.legado.app.ci.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.legado.app.ui.book.read.ink.InkBottomBar
import io.legado.app.ui.card.BattleCard
import io.legado.app.ui.card.BattleCardData
import io.legado.app.ui.card.BookCard
import io.legado.app.ui.card.CardState
import io.legado.app.ui.card.ClassicBook
import io.legado.app.ui.card.ClassicCard
import io.legado.app.ui.card.EntityRowCard
import io.legado.app.ui.card.HeroKind
import io.legado.app.ui.card.SearchResultCard
import io.legado.app.ui.card.SearchResultCardData
import io.legado.app.ui.card.TopicCard
import io.legado.app.ui.card.TopicCardData
import io.legado.app.ui.card.TopicHeroCard
import org.junit.Rule
import org.junit.Test

/**
 * 书目 / 专题 / 战役 / 人物 / 搜索结果等目录卡片，以及阅读页 Compose 底栏截图帧。
 * 卡片均复用 [CardBase] 基座五态；底栏是阅读壳中可脱离分页引擎 View 独立渲染的片段。
 */
class CatalogCardsSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

    @Test
    fun classicCard() {
        paparazzi.snapshot {
            TopPadded {
                ClassicCard(
                    book = ClassicBook(title = "兰亭集序", dynasty = "东晋", wordCount = "3200字"),
                    state = CardState.Default,
                )
            }
        }
    }

    @Test
    fun bookCard() {
        paparazzi.snapshot {
            TopPadded {
                BookCard(title = "桃花源记", author = "陶渊明", wordCount = "2800字", progress = 0.6f)
            }
        }
    }

    @Test
    fun topicCard() {
        paparazzi.snapshot {
            TopPadded {
                TopicCard(
                    data = TopicCardData(title = "典故溯源", preview = "武陵人捕鱼为业", allusionCount = 8),
                    state = CardState.Default,
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun entityRowCard() {
        paparazzi.snapshot {
            TopPadded {
                EntityRowCard(
                    name = "王羲之",
                    meta = "东晋 · 书法家",
                    desc = "兰亭集序作者",
                    source = "史部",
                    onClick = {},
                    state = CardState.Default,
                )
            }
        }
    }

    @Test
    fun searchResultCard() {
        paparazzi.snapshot {
            TopPadded {
                SearchResultCard(
                    data = SearchResultCardData(
                        title = "洛神赋",
                        author = "曹植",
                        year = "三国魏",
                        wordCount = "1200字",
                        progress = "已读 40%",
                        sourceLabel = "在线源",
                        isOnline = true,
                    ),
                    query = "洛神",
                    state = CardState.Default,
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun battleCard() {
        paparazzi.snapshot {
            TopPadded {
                BattleCard(
                    data = BattleCardData(
                        title = "赤壁之战",
                        subtitle = "建安十三年",
                        description = "孙刘联军破曹",
                        watchedStage = 5,
                        totalStage = 10,
                    ),
                    state = CardState.Default,
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun topicHeroCard() {
        paparazzi.snapshot {
            TopPadded {
                TopicHeroCard(
                    kind = HeroKind.Marx,
                    title = "马克思",
                    subtitle = "思想源流",
                    yearStart = "1818",
                    yearEnd = "1883",
                    state = CardState.Default,
                )
            }
        }
    }

    @Test
    fun inkBottomBar() {
        paparazzi.snapshot {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                InkBottomBar(
                    visible = true,
                    progress = 0.4f,
                    onCatalog = {},
                    onTts = {},
                    onMenu = {},
                    onSeek = {},
                )
            }
        }
    }

    @Composable
    private fun TopPadded(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}
