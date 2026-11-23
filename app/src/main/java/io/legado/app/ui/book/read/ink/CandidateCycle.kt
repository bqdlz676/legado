package io.legado.app.ui.book.read.ink

/**
 * 候选循环下标（纯函数，可单测）：环形回绕；候选为空时回 0，越界下标先收敛再 +1。
 *
 * 抽出独立文件是为了让候选切换的边界口径不依赖 Compose / Android，离线 JVM 可直接覆盖。
 */
internal fun nextCandidateIndex(size: Int, current: Int): Int {
    if (size <= 0) return 0
    val base = ((current % size) + size) % size
    return (base + 1) % size
}
