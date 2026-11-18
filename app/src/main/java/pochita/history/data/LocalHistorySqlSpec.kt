package pochita.history.data

/**
 * Parameterized SQL query specification and fixed column projection for [LocalHistoryDb].
 */
internal object LocalHistorySqlSpec {

    val COLUMNS: Array<String> = arrayOf(
        "kid",
        "oid",
        "cid",
        "epid",
        "season_id",
        "business",
        "tp",
        "title",
        "subtitle",
        "cover",
        "author_name",
        "author_mid",
        "bvid",
        "uri",
        "progress",
        "duration",
        "page",
        "videos",
        "badge",
        "view_at",
    )

    const val ORDER_NEWEST_FIRST = "view_at DESC, kid DESC"
    const val ORDER_VIEW_AT_DESC = "view_at DESC"

    data class QueryClause(
        val selection: String?,
        val selectionArgs: Array<String>?,
        val orderBy: String?,
        val limit: String?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QueryClause) return false
            return selection == other.selection &&
                selectionArgs.contentEqualsNullable(other.selectionArgs) &&
                orderBy == other.orderBy &&
                limit == other.limit
        }

        override fun hashCode(): Int {
            var result = selection?.hashCode() ?: 0
            result = 31 * result + (selectionArgs?.contentHashCode() ?: 0)
            result = 31 * result + (orderBy?.hashCode() ?: 0)
            result = 31 * result + (limit?.hashCode() ?: 0)
            return result
        }

        private fun Array<String>?.contentEqualsNullable(other: Array<String>?): Boolean =
            if (this == null) other == null else other != null && this.contentEquals(other)
    }

    fun buildCursorQuery(business: String, maxViewAt: Long, limit: Int): QueryClause {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        appendBusinessFilter(clauses, args, business)
        if (maxViewAt > 0L) {
            clauses += "view_at < ?"
            args += maxViewAt.toString()
        }
        val fetchLimit = (limit.coerceAtLeast(1) + 1).toString()
        return QueryClause(
            selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            selectionArgs = args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            orderBy = ORDER_NEWEST_FIRST,
            limit = fetchLimit,
        )
    }

    fun buildSearchFilter(business: String, keyword: String): Pair<String?, Array<String>?> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        appendBusinessFilter(clauses, args, business)
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) {
            val like = "%$trimmed%"
            clauses += "(title LIKE ? OR subtitle LIKE ? OR author_name LIKE ?)"
            args += like
            args += like
            args += like
        }
        return clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ") to
            args.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    fun buildLookupByOidOrSeason(oid: Long, seasonId: Long, epid: Long): QueryClause? {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (oid > 0L) {
            clauses += "oid = ?"
            args += oid.toString()
            clauses += "kid = ?"
            args += oid.toString()
        }
        if (seasonId > 0L) {
            clauses += "season_id = ?"
            args += seasonId.toString()
            clauses += "kid = ?"
            args += seasonId.toString()
        }
        if (epid > 0L) {
            clauses += "epid = ?"
            args += epid.toString()
        }
        if (clauses.isEmpty()) return null
        return QueryClause(
            selection = clauses.joinToString(" OR "),
            selectionArgs = args.toTypedArray(),
            orderBy = ORDER_VIEW_AT_DESC,
            limit = "1",
        )
    }

    private fun appendBusinessFilter(
        clauses: MutableList<String>,
        args: MutableList<String>,
        business: String,
    ) {
        if (business.isNotEmpty() && business != "all") {
            clauses += "business = ?"
            args += business
        }
    }
}
