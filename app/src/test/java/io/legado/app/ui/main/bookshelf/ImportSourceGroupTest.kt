package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q-244 B34 纯函数夹具：来源键归一化、来源指纹、组位分配。
 * 不依赖 Android / Room；旧 0 组兼容口径：来源键为 null 时解析方回落 groupId = 0，
 * 由 `BookshelfViewModel.importBookshelfJson` 的 `if (groupId > 0)` 守卫保证旧未分组语义不变。
 */
class ImportSourceGroupTest {

    @Test
    fun httpSourceKeepsHostAndPathStripsQueryAndFragment() {
        val key = sourceImportGroupKey(
            "https://Example.com/BookList/top?token=abc#section"
        )
        assertEquals("example.com/BookList/top", key)
    }

    @Test
    fun sameListDifferentQueryResolvesToSameKey() {
        val a = sourceImportGroupKey("https://example.com/lists/1.json?share=1")
        val b = sourceImportGroupKey("https://example.com/lists/1.json?share=2")
        assertEquals(a, b)
    }

    @Test
    fun contentUriUsesDecodedFileNameAfterColon() {
        val key = sourceImportGroupKey(
            "content://com.android.externalstorage.documents/document/primary%3ABooks%2Fmy-list.json"
        )
        assertEquals("my-list.json", key)
    }

    @Test
    fun unstableSchemesResolveToNullSoImportFallsBackToUngrouped() {
        assertNull(sourceImportGroupKey(""))
        assertNull(sourceImportGroupKey("   "))
        assertNull(sourceImportGroupKey("file:///data/user/0/cache/shared_import_abc.json"))
        assertNull(sourceImportGroupKey("legado://import/bookshelf?src=https://example.com/x"))
        assertNull(sourceImportGroupKey("not a uri at all"))
    }

    @Test
    fun sourceTagIsStableEightHexCharsAndDiffersPerKey() {
        val tag = importGroupTag("example.com/lists/1.json")
        assertEquals(8, tag.length)
        assertTrue(tag.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(tag, importGroupTag("example.com/lists/1.json"))
        assertFalse(tag == importGroupTag("example.com/lists/2.json"))
    }

    @Test
    fun groupNameMatchesOnlyReservedPrefixWithOwnTag() {
        val tag = importGroupTag("example.com/lists/1.json")
        val reserved = "$IMPORT_GROUP_NAME_PREFIX$tag my-list.json"
        assertTrue(matchesImportGroupTag(reserved, tag))
        assertFalse(matchesImportGroupTag("我的书单", tag))
        assertFalse(matchesImportGroupTag("$IMPORT_GROUP_NAME_PREFIXothertag name", tag))
    }

    @Test
    fun displayNameFoldsWhitespaceAndTruncates() {
        val longKey = "example.com/" + "x".repeat(60)
        val display = importGroupDisplayName(longKey)
        assertTrue(display.length <= 40)
        assertEquals("a  b", importGroupDisplayName("a\n\t  b"))
    }

    @Test
    fun freeBitAllocationSkipsOccupiedMaskBits() {
        assertEquals(1L, allocateFreeGroupBit(0L))
        assertEquals(2L, allocateFreeGroupBit(1L))
        assertEquals(4L, allocateFreeGroupBit(3L))
        assertEquals(8L, allocateFreeGroupBit(1L or 2L or 4L))
    }

    @Test
    fun freeBitAllocationReturnsNullWhenAllPositiveBitsAreOccupied() {
        assertNull(allocateFreeGroupBit(Long.MAX_VALUE))
    }
}
