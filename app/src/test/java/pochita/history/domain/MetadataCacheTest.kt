package pochita.history.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pochita.config.ModulePrefs

/**
 * Unit tests for [MetadataCache] field merging and [ModulePrefs] constants.
 */
class MetadataCacheTest {

    @Test
    fun ugcMeta_mergesPartialFieldsAcrossAidAndCidUpdates() {
        val cache = MetadataCache()
        val aid = 900_001L
        val cid = 800_001L

        cache.putUgc(
            UgcMeta(
                aid = aid,
                cid = cid,
                bvid = "BV1test900001",
                title = "Sample UGC Video",
                subtitle = "",
                cover = "https://example.com/cover.jpg",
                page = 1,
                videos = 1,
                durationSec = 120L,
                authorRepoRef = null,
                initialAuthorMid = 1001L,
                initialAuthorName = "Pochita",
            ),
        )

        cache.putUgc(
            UgcMeta(
                aid = aid,
                cid = 0L,
                bvid = "",
                title = "",
                subtitle = "P2 Extra",
                cover = "",
                page = 2,
                videos = 3,
                durationSec = 0L,
                authorRepoRef = null,
                initialAuthorMid = 0L,
                initialAuthorName = "",
            ),
        )

        val resolved = cache.getUgc(aid, cid)
        assertEquals(aid, resolved?.aid)
        assertEquals(cid, resolved?.cid)
        assertEquals("BV1test900001", resolved?.bvid)
        assertEquals("Sample UGC Video", resolved?.title)
        assertEquals("P2 Extra", resolved?.subtitle)
        assertEquals("https://example.com/cover.jpg", resolved?.cover)
        assertEquals(2, resolved?.page)
        assertEquals(3, resolved?.videos)
        assertEquals(120L, resolved?.durationSec)
        assertEquals(1001L, resolved?.resolveAuthorMid())
        assertEquals("Pochita", resolved?.resolveAuthorName())
    }

    @Test
    fun ogvMeta_resolvesByCidEpidAndSeasonId() {
        val cache = MetadataCache()
        val seasonId = 700_001L
        val epid = 700_002L
        val aid = 700_003L
        val cid = 700_004L

        cache.putOgv(
            OgvMeta(
                seasonId = seasonId,
                epid = epid,
                aid = aid,
                cid = cid,
                title = "Sample Bangumi",
                subtitle = "Ep 1 Beginning",
                cover = "https://example.com/ogv.jpg",
                badge = "Exclusive",
            ),
        )

        assertEquals("Sample Bangumi", cache.getOgv(0L, 0L, cid)?.title)
        assertEquals("Ep 1 Beginning", cache.getOgv(epid, 0L, 0L)?.subtitle)
        assertEquals("Exclusive", cache.getOgv(0L, seasonId, 0L)?.badge)
        assertNull(cache.getOgv(0L, 0L, 0L))
    }

    @Test
    fun modulePrefs_defaultsLocalHistoryEnabled() {
        assertEquals("bili_local_history_prefs", ModulePrefs.PREFS_NAME)
        assertEquals("local_history_enabled", ModulePrefs.KEY_LOCAL_HISTORY_ENABLED)
        assertEquals(true, ModulePrefs.DEFAULT_LOCAL_HISTORY_ENABLED)
    }
}
