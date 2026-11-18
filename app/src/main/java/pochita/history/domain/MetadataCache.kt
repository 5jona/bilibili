package pochita.history.domain

import pochita.xposed.ReflectOps
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory metadata captured when opening a UGC video detail page.
 */
data class UgcMeta(
    val aid: Long,
    val cid: Long,
    val bvid: String,
    val title: String,
    val subtitle: String,
    val cover: String,
    val page: Int,
    val videos: Int,
    val durationSec: Long,
    val authorRepoRef: WeakReference<Any>?,
    val initialAuthorMid: Long = 0L,
    val initialAuthorName: String = "",
) {
    /**
     * Resolves the uploader's UID, querying the live `AuthorRepository` if loaded asynchronously.
     */
    fun resolveAuthorMid(): Long {
        val repo = authorRepoRef?.get() ?: return initialAuthorMid
        val liveMid = (ReflectOps.invokeZeroArg(repo, "i") as? Number)?.toLong()
            ?.takeIf { it > 0L }
            ?: (ReflectOps.invokeZeroArg(repo, "f") as? Number)?.toLong()
                ?.takeIf { it > 0L }
        return liveMid ?: initialAuthorMid
    }

    /**
     * Resolves the uploader's display name, querying the live `AuthorRepository` if populated.
     */
    fun resolveAuthorName(): String {
        val repo = authorRepoRef?.get() ?: return initialAuthorName
        val liveName = (ReflectOps.invokeZeroArg(repo, "j") as? String)?.takeIf { it.isNotBlank() }
            ?: (ReflectOps.invokeZeroArg(repo, "g") as? String)?.takeIf { it.isNotBlank() }
        return liveName ?: initialAuthorName
    }
}

/**
 * In-memory metadata captured when opening an OGV/PGC bangumi or cinema detail page.
 */
data class OgvMeta(
    val seasonId: Long,
    val epid: Long,
    val aid: Long,
    val cid: Long,
    val title: String,
    val subtitle: String,
    val cover: String,
    val badge: String = "",
)

/**
 * Bounded thread-safe cache linking playback identifiers (`aid`, `cid`, `epid`, `seasonId`)
 * to rich detail-page metadata so playback progress callbacks can assemble complete history cards.
 */
class MetadataCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val ugcByAid = ConcurrentHashMap<Long, UgcMeta>()
    private val ugcByCid = ConcurrentHashMap<Long, UgcMeta>()
    private val ogvByCid = ConcurrentHashMap<Long, OgvMeta>()
    private val ogvByEpid = ConcurrentHashMap<Long, OgvMeta>()
    private val ogvBySeasonId = ConcurrentHashMap<Long, OgvMeta>()

    fun putUgc(meta: UgcMeta) {
        if (ugcByCid.size > maxEntries) ugcByCid.clear()
        if (ugcByAid.size > maxEntries) ugcByAid.clear()
        val prev = getUgcByCid(meta.cid) ?: getUgcByAid(meta.aid)
        val merged = if (prev != null && (meta.aid <= 0L || prev.aid <= 0L || prev.aid == meta.aid)) {
            UgcMeta(
                aid = if (meta.aid > 0L) meta.aid else prev.aid,
                cid = if (meta.cid > 0L) meta.cid else prev.cid,
                bvid = meta.bvid.ifBlank { prev.bvid },
                title = meta.title.ifBlank { prev.title },
                subtitle = meta.subtitle.ifBlank { prev.subtitle },
                cover = meta.cover.ifBlank { prev.cover },
                page = if (meta.page > 0) meta.page else prev.page.coerceAtLeast(1),
                videos = maxOf(meta.videos, prev.videos, 1),
                durationSec = if (meta.durationSec > 0L) meta.durationSec else prev.durationSec,
                authorRepoRef = meta.authorRepoRef ?: prev.authorRepoRef,
                initialAuthorMid = if (meta.initialAuthorMid > 0L) meta.initialAuthorMid else prev.initialAuthorMid,
                initialAuthorName = meta.initialAuthorName.ifBlank { prev.initialAuthorName },
            )
        } else {
            meta
        }
        if (merged.aid > 0L) ugcByAid[merged.aid] = merged
        if (merged.cid > 0L) ugcByCid[merged.cid] = merged
    }

    fun getUgcByCid(cid: Long): UgcMeta? = if (cid > 0L) ugcByCid[cid] else null

    fun getUgcByAid(aid: Long): UgcMeta? = if (aid > 0L) ugcByAid[aid] else null

    fun getUgc(aid: Long, cid: Long): UgcMeta? = getUgcByCid(cid) ?: getUgcByAid(aid)

    fun putOgv(meta: OgvMeta) {
        if (ogvByCid.size > maxEntries) ogvByCid.clear()
        if (ogvByEpid.size > maxEntries) ogvByEpid.clear()
        if (ogvBySeasonId.size > maxEntries) ogvBySeasonId.clear()
        val prev = getOgv(meta.epid, meta.seasonId, meta.cid)
        val merged = if (prev != null) {
            OgvMeta(
                seasonId = if (meta.seasonId > 0L) meta.seasonId else prev.seasonId,
                epid = if (meta.epid > 0L) meta.epid else prev.epid,
                aid = if (meta.aid > 0L) meta.aid else prev.aid,
                cid = if (meta.cid > 0L) meta.cid else prev.cid,
                title = meta.title.ifBlank { prev.title },
                subtitle = meta.subtitle.ifBlank { prev.subtitle },
                cover = meta.cover.ifBlank { prev.cover },
                badge = meta.badge.ifBlank { prev.badge },
            )
        } else {
            meta
        }
        if (merged.cid > 0L) ogvByCid[merged.cid] = merged
        if (merged.epid > 0L) ogvByEpid[merged.epid] = merged
        if (merged.seasonId > 0L) ogvBySeasonId[merged.seasonId] = merged
    }

    fun getOgvByCid(cid: Long): OgvMeta? = if (cid > 0L) ogvByCid[cid] else null

    fun getOgvByEpid(epid: Long): OgvMeta? = if (epid > 0L) ogvByEpid[epid] else null

    fun getOgvBySeasonId(seasonId: Long): OgvMeta? = if (seasonId > 0L) ogvBySeasonId[seasonId] else null

    fun getOgv(epid: Long, seasonId: Long, cid: Long): OgvMeta? =
        getOgvByCid(cid) ?: getOgvByEpid(epid) ?: getOgvBySeasonId(seasonId)

    companion object {
        const val DEFAULT_MAX_ENTRIES = 256
    }
}
