package pochita.history.domain

import pochita.history.data.HistoryStore
import pochita.history.data.LocalHistoryDb
import pochita.history.data.LocalHistoryRecord
import pochita.history.domain.HistoryFormatters.buildOgvUri
import pochita.history.domain.HistoryFormatters.buildUgcUri
import pochita.history.domain.HistoryFormatters.firstNonBlank
import pochita.history.domain.HistoryFormatters.firstPositiveLong
import java.util.Locale

/**
 * Normalized playback event extracted by player-specific hook adapters.
 */
data class PlaybackSnapshot(
    val aid: Long = 0L,
    val cid: Long = 0L,
    val seasonId: Long = 0L,
    val epid: Long = 0L,
    val bizTypeHint: String = "",
    val bvid: String = "",
    val title: String = "",
    val subtitle: String = "",
    val cover: String = "",
    val authorName: String = "",
    val authorMid: Long = 0L,
    val page: Int = 1,
    val videos: Int = 1,
    val badge: String = "",
    val progressSec: Long = 0L,
    val durationSec: Long = 0L,
    val lookupExistingInStore: Boolean = false,
    val requireKnownTitleWhenNew: Boolean = false,
    val cacheUgcMetadata: Boolean = false,
    val subtitleOnlyWhenMultiPart: Boolean = false,
)

/**
 * Unifies metadata caching, OGV/UGC classification, fallback enrichment, and persistence
 * across `theseus2`, legacy `theseus`, and inline feed playback hooks.
 */
internal class PlaybackHistoryRecorder(
    private val metadataCache: MetadataCache,
    private val storeProvider: () -> HistoryStore?,
    private val shouldRecordProvider: () -> Boolean,
    private val clockSecProvider: () -> Long = { System.currentTimeMillis() / 1000L },
) {

    /**
     * Caches UGC metadata on detail page open and writes an initial record preserving any
     * previously saved playback progress for the same `cid`.
     */
    fun recordUgcOpen(meta: UgcMeta) {
        if (meta.aid <= 0L) return
        metadataCache.putUgc(meta)
        val resolved = metadataCache.getUgc(meta.aid, meta.cid) ?: meta
        if (!shouldRecordProvider()) return
        persistUgc(
            aid = resolved.aid,
            cid = resolved.cid,
            bvid = resolved.bvid,
            title = resolved.title,
            subtitle = resolved.subtitle,
            cover = resolved.cover,
            authorName = resolved.resolveAuthorName(),
            authorMid = resolved.resolveAuthorMid(),
            progressSec = 0L,
            durationSec = resolved.durationSec,
            page = resolved.page,
            videos = resolved.videos,
            preserveProgressIfMatchingCid = true,
        )
    }

    /**
     * Caches OGV metadata on detail page open and writes an initial record preserving any
     * previously saved playback progress for the same `cid`.
     */
    fun recordOgvOpen(meta: OgvMeta) {
        if (firstPositiveLong(meta.seasonId, meta.epid, meta.aid) <= 0L) return
        metadataCache.putOgv(meta)
        val resolved = metadataCache.getOgv(meta.epid, meta.seasonId, meta.cid) ?: meta
        if (!shouldRecordProvider()) return
        persistOgv(
            seasonId = resolved.seasonId,
            epid = resolved.epid,
            aid = resolved.aid,
            cid = resolved.cid,
            title = resolved.title,
            subtitle = resolved.subtitle,
            cover = resolved.cover,
            badge = resolved.badge,
            progressSec = 0L,
            durationSec = 0L,
            preserveProgressIfMatchingCid = true,
        )
    }

    /**
     * Resolves cached/stored metadata for [snapshot] and persists the latest playback progress.
     */
    fun recordPlaybackProgress(snapshot: PlaybackSnapshot) {
        if (!shouldRecordProvider()) return

        val store = storeProvider()
        val cachedOgv = metadataCache.getOgv(snapshot.epid, snapshot.seasonId, snapshot.cid)
        val cachedUgc = metadataCache.getUgc(snapshot.aid, snapshot.cid)
        val existing = if (snapshot.lookupExistingInStore) {
            (if (snapshot.cid > 0L) store?.getByCid(snapshot.cid) else null)
                ?: store?.getByOidOrSeason(snapshot.aid, snapshot.seasonId, snapshot.epid)
        } else {
            null
        }

        val normalizedBiz = snapshot.bizTypeHint.lowercase(Locale.US)
        val isOgv = normalizedBiz == "pgc" ||
            normalizedBiz == "ogv" ||
            snapshot.seasonId > 0L ||
            snapshot.epid > 0L ||
            existing?.business == LocalHistoryDb.BUSINESS_PGC ||
            (cachedOgv != null && (cachedUgc == null || snapshot.lookupExistingInStore))

        if (isOgv) {
            recordOgvProgress(snapshot, cachedOgv, existing)
        } else {
            recordUgcProgress(snapshot, cachedUgc, existing)
        }
    }

    /**
     * Updates playback progress from inline player heartbeats that provide only `(cid, posSec)`.
     */
    fun recordInlineCidProgress(cid: Long, posSec: Long) {
        if (cid <= 0L || !shouldRecordProvider()) return
        val store = storeProvider()
        val existing = store?.getByCid(cid)
        if (existing != null) {
            store.upsert(
                existing.copy(progress = posSec, viewAt = clockSecProvider()),
                preserveProgressIfMatchingCid = false,
            )
            return
        }
        val cachedOgv = metadataCache.getOgvByCid(cid)
        if (cachedOgv != null) {
            persistOgv(
                seasonId = cachedOgv.seasonId,
                epid = cachedOgv.epid,
                aid = cachedOgv.aid,
                cid = cid,
                title = cachedOgv.title,
                subtitle = cachedOgv.subtitle,
                cover = cachedOgv.cover,
                badge = cachedOgv.badge,
                progressSec = posSec,
                durationSec = 0L,
                preserveProgressIfMatchingCid = false,
            )
            return
        }
        val cachedUgc = metadataCache.getUgcByCid(cid)
        if (cachedUgc != null && cachedUgc.aid > 0L) {
            persistUgc(
                aid = cachedUgc.aid,
                cid = cid,
                bvid = cachedUgc.bvid,
                title = cachedUgc.title,
                subtitle = cachedUgc.subtitle,
                cover = cachedUgc.cover,
                authorName = cachedUgc.resolveAuthorName(),
                authorMid = cachedUgc.resolveAuthorMid(),
                progressSec = posSec,
                durationSec = cachedUgc.durationSec,
                page = cachedUgc.page,
                videos = cachedUgc.videos,
                preserveProgressIfMatchingCid = false,
            )
        }
    }

    private fun recordOgvProgress(
        snapshot: PlaybackSnapshot,
        cachedOgv: OgvMeta?,
        existing: LocalHistoryRecord?,
    ) {
        val seasonId = firstPositiveLong(snapshot.seasonId, cachedOgv?.seasonId ?: 0L, existing?.seasonId ?: 0L)
        val epid = firstPositiveLong(snapshot.epid, cachedOgv?.epid ?: 0L, existing?.epid ?: 0L)
        val aid = firstPositiveLong(snapshot.aid, cachedOgv?.aid ?: 0L, existing?.oid ?: 0L)
        val cid = firstPositiveLong(snapshot.cid, cachedOgv?.cid ?: 0L, existing?.cid ?: 0L)
        val title = firstNonBlank(cachedOgv?.title, snapshot.title, existing?.title)
        if (snapshot.requireKnownTitleWhenNew && title.isBlank() && existing == null) return

        persistOgv(
            seasonId = seasonId,
            epid = epid,
            aid = aid,
            cid = cid,
            title = title,
            subtitle = firstNonBlank(cachedOgv?.subtitle, snapshot.subtitle, existing?.subtitle),
            cover = firstNonBlank(cachedOgv?.cover, snapshot.cover, existing?.cover),
            badge = firstNonBlank(cachedOgv?.badge, snapshot.badge, existing?.badge),
            progressSec = snapshot.progressSec,
            durationSec = firstPositiveLong(snapshot.durationSec, existing?.duration ?: 0L),
            preserveProgressIfMatchingCid = false,
        )
    }

    private fun recordUgcProgress(
        snapshot: PlaybackSnapshot,
        cachedUgc: UgcMeta?,
        existing: LocalHistoryRecord?,
    ) {
        val resolvedAid = firstPositiveLong(snapshot.aid, cachedUgc?.aid ?: 0L, existing?.oid ?: 0L)
        val title = firstNonBlank(cachedUgc?.title, snapshot.title, existing?.title)
        if (resolvedAid <= 0L) return
        if (snapshot.requireKnownTitleWhenNew && title.isBlank() && existing == null) return

        val resolvedCid = firstPositiveLong(snapshot.cid, cachedUgc?.cid ?: 0L, existing?.cid ?: 0L)
        val resolvedBvid = firstNonBlank(cachedUgc?.bvid, snapshot.bvid, existing?.bvid)
        val resolvedCover = firstNonBlank(cachedUgc?.cover, snapshot.cover, existing?.cover)
        val resolvedAuthor = firstNonBlank(
            cachedUgc?.resolveAuthorName(),
            snapshot.authorName,
            existing?.authorName,
        )
        val resolvedMid = firstPositiveLong(
            cachedUgc?.resolveAuthorMid() ?: 0L,
            snapshot.authorMid.coerceAtLeast(0L),
            existing?.authorMid ?: 0L,
        )
        val resolvedVideos = maxOf(cachedUgc?.videos ?: 1, snapshot.videos, existing?.videos ?: 1, 1)
        val resolvedPage = maxOf(cachedUgc?.page ?: 1, snapshot.page, existing?.page ?: 1, 1)
        val fallbackSubtitle = if (!snapshot.subtitleOnlyWhenMultiPart || resolvedVideos > 1) {
            snapshot.subtitle
        } else {
            ""
        }
        val resolvedSubtitle = firstNonBlank(cachedUgc?.subtitle, fallbackSubtitle, existing?.subtitle)
        val resolvedDuration = firstPositiveLong(
            snapshot.durationSec,
            cachedUgc?.durationSec ?: 0L,
            existing?.duration ?: 0L,
        )

        if (snapshot.cacheUgcMetadata && title.isNotBlank()) {
            metadataCache.putUgc(
                UgcMeta(
                    aid = resolvedAid,
                    cid = resolvedCid,
                    bvid = resolvedBvid,
                    title = title,
                    subtitle = resolvedSubtitle,
                    cover = resolvedCover,
                    page = resolvedPage,
                    videos = resolvedVideos,
                    durationSec = resolvedDuration,
                    authorRepoRef = cachedUgc?.authorRepoRef,
                    initialAuthorMid = resolvedMid,
                    initialAuthorName = resolvedAuthor,
                ),
            )
        }

        persistUgc(
            aid = resolvedAid,
            cid = resolvedCid,
            bvid = resolvedBvid,
            title = title,
            subtitle = resolvedSubtitle,
            cover = resolvedCover,
            authorName = resolvedAuthor,
            authorMid = resolvedMid,
            progressSec = snapshot.progressSec,
            durationSec = resolvedDuration,
            page = resolvedPage,
            videos = resolvedVideos,
            preserveProgressIfMatchingCid = false,
        )
    }

    private fun persistUgc(
        aid: Long,
        cid: Long,
        bvid: String,
        title: String,
        subtitle: String,
        cover: String,
        authorName: String,
        authorMid: Long,
        progressSec: Long,
        durationSec: Long,
        page: Int,
        videos: Int,
        preserveProgressIfMatchingCid: Boolean,
    ) {
        if (aid <= 0L) return
        storeProvider()?.upsert(
            LocalHistoryRecord(
                kid = aid,
                oid = aid,
                cid = cid,
                business = LocalHistoryDb.BUSINESS_ARCHIVE,
                tp = LocalHistoryDb.TP_ARCHIVE,
                title = title,
                subtitle = subtitle,
                cover = cover,
                authorName = authorName,
                authorMid = authorMid,
                bvid = bvid,
                uri = buildUgcUri(aid),
                progress = progressSec,
                duration = durationSec,
                page = page.coerceAtLeast(1),
                videos = videos.coerceAtLeast(1),
                viewAt = clockSecProvider(),
            ),
            preserveProgressIfMatchingCid = preserveProgressIfMatchingCid,
        )
    }

    private fun persistOgv(
        seasonId: Long,
        epid: Long,
        aid: Long,
        cid: Long,
        title: String,
        subtitle: String,
        cover: String,
        badge: String,
        progressSec: Long,
        durationSec: Long,
        preserveProgressIfMatchingCid: Boolean,
    ) {
        val kid = firstPositiveLong(seasonId, epid, aid)
        val oid = firstPositiveLong(seasonId, aid, epid)
        if (kid <= 0L || oid <= 0L) return
        storeProvider()?.upsert(
            LocalHistoryRecord(
                kid = kid,
                oid = oid,
                cid = cid,
                epid = epid,
                seasonId = seasonId,
                business = LocalHistoryDb.BUSINESS_PGC,
                tp = LocalHistoryDb.TP_PGC,
                title = title,
                subtitle = subtitle,
                cover = cover,
                badge = badge,
                uri = buildOgvUri(seasonId, epid, aid),
                progress = progressSec,
                duration = durationSec,
                viewAt = clockSecProvider(),
            ),
            preserveProgressIfMatchingCid = preserveProgressIfMatchingCid,
        )
    }
}
