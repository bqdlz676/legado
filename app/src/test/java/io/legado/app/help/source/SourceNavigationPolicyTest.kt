package io.legado.app.help.source

import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceNavigationPolicyTest {
    // 被测函数只做 CoroutineContext 元素读取（纯内存、无 IO/无 delay）；仓内未引入
    // kotlinx-coroutines-test，故保留 runBlocking，仅在裸 await 外包 5s 兜底（既有 5s 口径）。
    @Test(timeout = 5_000) fun restrictionFollowsOnlyItsOperationAndRequiresOptIn() = runBlocking {
        val normalReader = async { shouldSuppressSourceNavigation(true, currentCoroutineContext()) }
        withContext(SuppressSourceNavigation) {
            assertFalse(shouldSuppressSourceNavigation(false, currentCoroutineContext()))
            assertTrue(shouldSuppressSourceNavigation(true, currentCoroutineContext()))
            assertTrue(withTimeout(5_000) { async { shouldSuppressSourceNavigation(true, currentCoroutineContext()) }.await() })
            assertFalse(withTimeout(5_000) { normalReader.await() })
        }
        assertFalse(shouldSuppressSourceNavigation(true, currentCoroutineContext()))
    }
}
