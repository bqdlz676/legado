package io.legado.app.ci.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.legado.app.ui.main.bookshelf.BookshelfScreen
import io.legado.app.ui.main.bookshelf.ShelfState
import org.junit.Rule
import org.junit.Test

/**
 * 书架屏截图帧（锚点屏 1/3：Compose 整屏 + LazyVerticalGrid + 自绘粒子场）。
 *
 * 出帧口径：只灌展示模型与空回调，不起数据库订阅、不跳转页面。覆盖内容 / 首帧骨架 / 空架 / 失败四态，
 * 供 P1 观感判定与 P3 基线冻结取用；帧文件名由 Paparazzi 按 包名_类名_方法名 生成。
 */
class BookshelfSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

    @Test
    fun bookshelfContent() {
        paparazzi.snapshot {
            BookshelfScreen(
                state = ShelfState.CONTENT,
                books = ScreenshotFixtures.books(6),
                newBookCount = 2,
                onOpenBook = {},
                onImport = {},
                onDeleteBook = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun bookshelfLoading() {
        paparazzi.snapshot {
            BookshelfScreen(
                state = ShelfState.INIT_LOADING,
                books = emptyList(),
                newBookCount = 0,
                onOpenBook = {},
                onImport = {},
                onDeleteBook = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun bookshelfEmpty() {
        paparazzi.snapshot {
            BookshelfScreen(
                state = ShelfState.EMPTY,
                books = emptyList(),
                newBookCount = 0,
                onOpenBook = {},
                onImport = {},
                onDeleteBook = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun bookshelfError() {
        paparazzi.snapshot {
            BookshelfScreen(
                state = ShelfState.ERROR,
                books = emptyList(),
                newBookCount = 0,
                onOpenBook = {},
                onImport = {},
                onDeleteBook = {},
                onRetry = {},
            )
        }
    }
}
