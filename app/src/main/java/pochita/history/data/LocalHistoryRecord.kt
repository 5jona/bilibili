package pochita.history.data

/**
 * Immutable local watch history entry persisted inside Bilibili's private SQLite database.
 */
data class LocalHistoryRecord(
    val kid: Long,
    val oid: Long,
    val cid: Long = 0L,
    val epid: Long = 0L,
    val seasonId: Long = 0L,
    val business: String,
    val tp: Int,
    val title: String = "",
    val subtitle: String = "",
    val cover: String = "",
    val authorName: String = "",
    val authorMid: Long = 0L,
    val bvid: String = "",
    val uri: String = "",
    val progress: Long = 0L,
    val duration: Long = 0L,
    val page: Int = 1,
    val videos: Int = 1,
    val badge: String = "",
    val viewAt: Long = 0L,
) {
    /**
     * Merges non-empty metadata fields from [existing] into this incoming record while optionally
     * preserving existing resume [progress] on initial page opens for the same `cid`.
     */
    fun mergeWith(
        existing: LocalHistoryRecord?,
        preserveProgressIfMatchingCid: Boolean,
        fallbackViewAtSec: Long,
    ): LocalHistoryRecord {
        val effectiveProgress = if (
            preserveProgressIfMatchingCid &&
            existing != null &&
            (cid <= 0L || existing.cid == cid)
        ) {
            existing.progress
        } else {
            progress
        }
        return LocalHistoryRecord(
            kid = kid,
            oid = oid,
            cid = if (cid > 0L) cid else existing?.cid ?: 0L,
            epid = if (epid > 0L) epid else existing?.epid ?: 0L,
            seasonId = if (seasonId > 0L) seasonId else existing?.seasonId ?: 0L,
            business = business.ifBlank { existing?.business ?: LocalHistoryDb.BUSINESS_ARCHIVE },
            tp = if (tp > 0) tp else existing?.tp ?: LocalHistoryDb.TP_ARCHIVE,
            title = title.ifBlank { existing?.title.orEmpty() },
            subtitle = subtitle.ifBlank { existing?.subtitle.orEmpty() },
            cover = cover.ifBlank { existing?.cover.orEmpty() },
            authorName = authorName.ifBlank { existing?.authorName.orEmpty() },
            authorMid = if (authorMid > 0L) authorMid else existing?.authorMid ?: 0L,
            bvid = bvid.ifBlank { existing?.bvid.orEmpty() },
            uri = uri.ifBlank { existing?.uri.orEmpty() },
            progress = effectiveProgress,
            duration = if (duration > 0L) duration else existing?.duration ?: 0L,
            page = if (page > 0) page else existing?.page ?: 1,
            videos = maxOf(videos, existing?.videos ?: 1, 1),
            badge = badge.ifBlank { existing?.badge.orEmpty() },
            viewAt = if (viewAt > 0L) viewAt else fallbackViewAtSec,
        )
    }
}
