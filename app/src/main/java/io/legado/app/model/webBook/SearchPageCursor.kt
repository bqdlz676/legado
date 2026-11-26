package io.legado.app.model.webBook

/**
 * 检索页码游标（纯 JVM，可单测）：按「已成功取页数」推进，派发时不占页。
 *
 * Q-179 / 验收 N1 修复：旧实现在同 id 派发时即 `searchPage++`，失败页重试会再占一页，
 * 重试从 N+1 页起拉取、跳过失败的第 N 页。新口径：
 * - [reset] 新检索（换词 / 首查）回到第 1 页；
 * - [currentPage] 取当前待取页码；续页与失败重试返回同一值，重试不推高页号；
 * - [markSuccess] 仅在本页正常收口（成功）后前进一页；失败 / 取消不前进，重试再取同一页。
 */
internal class SearchPageCursor {

    /** 下一次待取页码（1 起）。 */
    private var nextPage = 1

    /** 新检索复位：下一页从第 1 页起。 */
    fun reset() {
        nextPage = 1
    }

    /** 当前待取页码（1 起）；续页与同页重试取值相同。 */
    fun currentPage(): Int = nextPage

    /** 本页成功收口：前进到下一页。失败 / 取消路径不得调用本方法。 */
    fun markSuccess() {
        nextPage += 1
    }
}
