package pochita.history.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for parameterized SQL builders in [LocalHistorySqlSpec].
 */
class LocalHistorySqlSpecTest {

    @Test
    fun buildCursorQuery_omitsBusinessFilterForAllAndIncludesMaxViewAt() {
        val spec = LocalHistorySqlSpec.buildCursorQuery(business = "all", maxViewAt = 1700L, limit = 20)
        assertEquals("view_at < ?", spec.selection)
        assertArrayEquals(arrayOf("1700"), spec.selectionArgs)
        assertEquals(LocalHistorySqlSpec.ORDER_NEWEST_FIRST, spec.orderBy)
        assertEquals("21", spec.limit)
    }

    @Test
    fun buildCursorQuery_filtersSpecificBusiness() {
        val spec = LocalHistorySqlSpec.buildCursorQuery(business = LocalHistoryDb.BUSINESS_PGC, maxViewAt = 0L, limit = 10)
        assertEquals("business = ?", spec.selection)
        assertArrayEquals(arrayOf("pgc"), spec.selectionArgs)
        assertEquals("11", spec.limit)
    }

    @Test
    fun buildSearchFilter_trimsKeywordAndMatchesThreeColumns() {
        val (where, args) = LocalHistorySqlSpec.buildSearchFilter(business = "archive", keyword = "  bilibili  ")
        assertEquals("business = ? AND (title LIKE ? OR subtitle LIKE ? OR author_name LIKE ?)", where)
        assertArrayEquals(arrayOf("archive", "%bilibili%", "%bilibili%", "%bilibili%"), args)
    }

    @Test
    fun buildLookupByOidOrSeason_returnsNullWhenAllIdsInvalid() {
        assertNull(LocalHistorySqlSpec.buildLookupByOidOrSeason(0L, 0L, 0L))
    }
}
