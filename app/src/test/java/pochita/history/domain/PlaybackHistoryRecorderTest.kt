package pochita.history.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pochita.history.data.HistoryStore
import pochita.history.data.LocalHistoryDb
import pochita.history.data.LocalHistoryRecord

/**
 * Unit tests for unified recording, fallback enrichment, and resume preservation in
 * [PlaybackHistoryRecorder].
 */
class PlaybackHistoryRecorderTest {

    private class FakeHistoryStore : HistoryStore {
        private val rows = LinkedHashMap<Long, LocalHistoryRecord>()
        var nextClockSec = 1000L

        override fun upsert(record: LocalHistoryRecord, preserveProgressIfMatchingCid: Boolean) {
            val existing = rows[record.kid]
            rows[record.kid] = record.mergeWith(
                existing = existing,
                preserveProgressIfMatchingCid = preserveProgressIfMatchingCid,
                fallbackViewAtSec = nextClockSec,
            )
        }

        override fun queryCursor(
            business: String,
            maxViewAt: Long,
            limit: Int,
        ): Pair<List<LocalHistoryRecord>, Boolean> = emptyList<LocalHistoryRecord>() to false

        override fun search(
            business: String,
            keyword: String,
            page: Int,
            pageSize: Int,
        ): Triple<List<LocalHistoryRecord>, Boolean, Int> = Triple(emptyList(), false, 0)

        override fun deleteByKids(items: List<Pair<Long, String>>) = Unit

        override fun clear(business: String) = rows.clear()

        override fun getByCid(cid: Long): LocalHistoryRecord? =
            rows.values.filter { it.cid == cid }.maxByOrNull { it.viewAt }

        override fun getByOidOrSeason(oid: Long, seasonId: Long, epid: Long): LocalHistoryRecord? =
            rows.values.filter {
                (oid > 0L && (it.oid == oid || it.kid == oid)) ||
                    (seasonId > 0L && (it.seasonId == seasonId || it.kid == seasonId)) ||
                    (epid > 0L && it.epid == epid)
            }.maxByOrNull { it.viewAt }

        fun getByKid(kid: Long): LocalHistoryRecord? = rows[kid]
    }

    @Test
    fun recordUgcOpen_preservesExistingProgressForSameCidOnly() {
        val cache = MetadataCache()
        val store = FakeHistoryStore()
        var nowSec = 1_700_000_000L
        val recorder = PlaybackHistoryRecorder(cache, { store }, { true }, { nowSec })

        val aid = 12345L
        val cid1 = 555L
        val cid2 = 666L

        recorder.recordPlaybackProgress(
            PlaybackSnapshot(
                aid = aid,
                cid = cid1,
                bvid = "BV1xx",
                title = "Original Video",
                authorName = "Alice",
                authorMid = 42L,
                progressSec = 88L,
                durationSec = 300L,
            ),
        )
        assertEquals(88L, store.getByKid(aid)?.progress)

        // Re-opening same cid resets incoming progress=0 on initial page load -> must preserve 88L.
        nowSec += 10L
        recorder.recordUgcOpen(
            UgcMeta(
                aid = aid,
                cid = cid1,
                bvid = "BV1xx",
                title = "Original Video",
                subtitle = "",
                cover = "https://cover",
                page = 1,
                videos = 2,
                durationSec = 300L,
                authorRepoRef = null,
            ),
        )
        assertEquals(88L, store.getByKid(aid)?.progress)

        // Opening another part (cid2) should start at 0 rather than carrying over cid1 progress.
        recorder.recordUgcOpen(
            UgcMeta(
                aid = aid,
                cid = cid2,
                bvid = "BV1xx",
                title = "Original Video",
                subtitle = "P2",
                cover = "https://cover",
                page = 2,
                videos = 2,
                durationSec = 300L,
                authorRepoRef = null,
            ),
        )
        assertEquals(0L, store.getByKid(aid)?.progress)
    }

    @Test
    fun recordInlineReport_enrichesFromExistingStoreAndSkipsUnknownTitle() {
        val cache = MetadataCache()
        val store = FakeHistoryStore()
        val recorder = PlaybackHistoryRecorder(cache, { store }, { true }, { 2_000L })

        // Unknown inline video with no title in cache/store must be skipped.
        recorder.recordPlaybackProgress(
            PlaybackSnapshot(
                aid = 999L,
                cid = 888L,
                progressSec = 15L,
                lookupExistingInStore = true,
                requireKnownTitleWhenNew = true,
            ),
        )
        assertNull(store.getByKid(999L))

        // Seed existing entry, then send minimal inline progress update.
        store.upsert(
            LocalHistoryRecord(
                kid = 999L,
                oid = 999L,
                cid = 888L,
                business = LocalHistoryDb.BUSINESS_ARCHIVE,
                tp = LocalHistoryDb.TP_ARCHIVE,
                title = "Saved Title",
                authorName = "AuthorA",
                duration = 120L,
                viewAt = 1_500L,
            ),
        )
        recorder.recordPlaybackProgress(
            PlaybackSnapshot(
                aid = 999L,
                cid = 888L,
                progressSec = 64L,
                lookupExistingInStore = true,
                requireKnownTitleWhenNew = true,
            ),
        )

        val updated = store.getByKid(999L)
        assertNotNull(updated)
        assertEquals("Saved Title", updated?.title)
        assertEquals("AuthorA", updated?.authorName)
        assertEquals(64L, updated?.progress)
    }

    @Test
    fun recordPlaybackProgress_noOpsWhenDisabled() {
        val cache = MetadataCache()
        val store = FakeHistoryStore()
        val recorder = PlaybackHistoryRecorder(cache, { store }, { false }, { 2_000L })

        recorder.recordPlaybackProgress(
            PlaybackSnapshot(aid = 101L, cid = 202L, title = "Should Not Persist", progressSec = 30L),
        )
        assertNull(store.getByKid(101L))
    }
}
