package pochita.history.data

/**
 * Storage contract for local-only watch history persistence and queries.
 */
interface HistoryStore {
    /**
     * Inserts or updates [record], preserving richer metadata already stored for the same `kid`.
     */
    fun upsert(record: LocalHistoryRecord, preserveProgressIfMatchingCid: Boolean = false)

    /**
     * Returns a cursor-paginated slice `(rows, hasMore)` ordered newest-first before [maxViewAt].
     */
    fun queryCursor(
        business: String,
        maxViewAt: Long,
        limit: Int = LocalHistoryDb.DEFAULT_PAGE_SIZE,
    ): Pair<List<LocalHistoryRecord>, Boolean>

    /**
     * Returns `(rows, hasMore, totalCount)` matching [keyword] in title, subtitle, or author name.
     */
    fun search(
        business: String,
        keyword: String,
        page: Int,
        pageSize: Int = LocalHistoryDb.DEFAULT_PAGE_SIZE,
    ): Triple<List<LocalHistoryRecord>, Boolean, Int>

    /**
     * Deletes entries identified by `(kid, business)` pairs (or matching `kid` when business is blank).
     */
    fun deleteByKids(items: List<Pair<Long, String>>)

    /**
     * Deletes all rows for [business], or the entire history table when blank or `"all"`.
     */
    fun clear(business: String)

    /**
     * Returns the most recently viewed record matching a specific [cid], if any.
     */
    fun getByCid(cid: Long): LocalHistoryRecord?

    /**
     * Returns the most recently viewed record matching [oid], [seasonId], or [epid].
     */
    fun getByOidOrSeason(oid: Long, seasonId: Long = 0L, epid: Long = 0L): LocalHistoryRecord?
}
