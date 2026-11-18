package pochita.history.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite implementation of [HistoryStore] backed by Bilibili's private app database directory.
 */
class LocalHistoryDb private constructor(
    context: Context,
) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION), HistoryStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                kid INTEGER PRIMARY KEY,
                oid INTEGER NOT NULL,
                cid INTEGER NOT NULL DEFAULT 0,
                epid INTEGER NOT NULL DEFAULT 0,
                season_id INTEGER NOT NULL DEFAULT 0,
                business TEXT NOT NULL,
                tp INTEGER NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                subtitle TEXT NOT NULL DEFAULT '',
                cover TEXT NOT NULL DEFAULT '',
                author_name TEXT NOT NULL DEFAULT '',
                author_mid INTEGER NOT NULL DEFAULT 0,
                bvid TEXT NOT NULL DEFAULT '',
                uri TEXT NOT NULL DEFAULT '',
                progress INTEGER NOT NULL DEFAULT 0,
                duration INTEGER NOT NULL DEFAULT 0,
                page INTEGER NOT NULL DEFAULT 1,
                videos INTEGER NOT NULL DEFAULT 1,
                badge TEXT NOT NULL DEFAULT '',
                view_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_history_view_at ON $TABLE_NAME(view_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_history_cid ON $TABLE_NAME(cid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_history_oid ON $TABLE_NAME(oid)")
        createCompositeCursorIndex(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createCompositeCursorIndex(db)
        }
    }

    @Synchronized
    override fun upsert(record: LocalHistoryRecord, preserveProgressIfMatchingCid: Boolean) {
        if (record.kid <= 0L || record.oid <= 0L) return
        val existing = getByKidLocked(record.kid)
        val merged = record.mergeWith(
            existing = existing,
            preserveProgressIfMatchingCid = preserveProgressIfMatchingCid,
            fallbackViewAtSec = System.currentTimeMillis() / 1000L,
        )
        writableDatabase.insertWithOnConflict(TABLE_NAME, null, merged.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    override fun queryCursor(
        business: String,
        maxViewAt: Long,
        limit: Int,
    ): Pair<List<LocalHistoryRecord>, Boolean> {
        val safeLimit = limit.coerceAtLeast(1)
        val spec = LocalHistorySqlSpec.buildCursorQuery(business, maxViewAt, safeLimit)
        val rows = queryList(spec)
        val hasMore = rows.size > safeLimit
        return (if (hasMore) rows.subList(0, safeLimit) else rows) to hasMore
    }

    @Synchronized
    override fun search(
        business: String,
        keyword: String,
        page: Int,
        pageSize: Int,
    ): Triple<List<LocalHistoryRecord>, Boolean, Int> {
        val (where, whereArgs) = LocalHistorySqlSpec.buildSearchFilter(business, keyword)
        val total = countWhere(where, whereArgs)
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceAtLeast(1)
        val offset = (safePage - 1) * safeSize
        val rows = queryList(
            LocalHistorySqlSpec.QueryClause(
                selection = where,
                selectionArgs = whereArgs,
                orderBy = LocalHistorySqlSpec.ORDER_NEWEST_FIRST,
                limit = "$offset,$safeSize",
            ),
        )
        val hasMore = offset + rows.size < total
        return Triple(rows, hasMore, total)
    }

    @Synchronized
    override fun deleteByKids(items: List<Pair<Long, String>>) {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((kid, business) in items) {
                if (kid <= 0L) continue
                if (business.isNotBlank()) {
                    db.delete(TABLE_NAME, "kid = ? AND business = ?", arrayOf(kid.toString(), business))
                } else {
                    db.delete(TABLE_NAME, "kid = ?", arrayOf(kid.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    override fun clear(business: String) {
        if (business.isBlank() || business == "all") {
            writableDatabase.delete(TABLE_NAME, null, null)
        } else {
            writableDatabase.delete(TABLE_NAME, "business = ?", arrayOf(business))
        }
    }

    @Synchronized
    override fun getByCid(cid: Long): LocalHistoryRecord? {
        if (cid <= 0L) return null
        return queryList(
            LocalHistorySqlSpec.QueryClause(
                selection = "cid = ?",
                selectionArgs = arrayOf(cid.toString()),
                orderBy = LocalHistorySqlSpec.ORDER_VIEW_AT_DESC,
                limit = "1",
            ),
        ).firstOrNull()
    }

    @Synchronized
    override fun getByOidOrSeason(oid: Long, seasonId: Long, epid: Long): LocalHistoryRecord? {
        val spec = LocalHistorySqlSpec.buildLookupByOidOrSeason(oid, seasonId, epid) ?: return null
        return queryList(spec).firstOrNull()
    }

    private fun createCompositeCursorIndex(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_local_history_business_view_at ON $TABLE_NAME(business, view_at DESC, kid DESC)",
        )
    }

    private fun getByKidLocked(kid: Long): LocalHistoryRecord? =
        queryList(
            LocalHistorySqlSpec.QueryClause(
                selection = "kid = ?",
                selectionArgs = arrayOf(kid.toString()),
                orderBy = null,
                limit = "1",
            ),
        ).firstOrNull()

    private fun countWhere(where: String?, args: Array<String>?): Int {
        val sql = buildString {
            append("SELECT COUNT(*) FROM ").append(TABLE_NAME)
            if (!where.isNullOrEmpty()) {
                append(" WHERE ").append(where)
            }
        }
        readableDatabase.rawQuery(sql, args).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun queryList(spec: LocalHistorySqlSpec.QueryClause): List<LocalHistoryRecord> {
        val result = ArrayList<LocalHistoryRecord>()
        readableDatabase.query(
            TABLE_NAME,
            LocalHistorySqlSpec.COLUMNS,
            spec.selection,
            spec.selectionArgs,
            null,
            null,
            spec.orderBy,
            spec.limit,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += readProjectedRow(cursor)
            }
        }
        return result
    }

    /**
     * Reads a row using fixed indices aligned with [LocalHistorySqlSpec.COLUMNS] to avoid
     * repetitive column-name hash lookups inside cursor loops.
     */
    private fun readProjectedRow(c: Cursor): LocalHistoryRecord = LocalHistoryRecord(
        kid = c.getLong(0),
        oid = c.getLong(1),
        cid = c.getLong(2),
        epid = c.getLong(3),
        seasonId = c.getLong(4),
        business = c.getString(5),
        tp = c.getInt(6),
        title = c.getString(7),
        subtitle = c.getString(8),
        cover = c.getString(9),
        authorName = c.getString(10),
        authorMid = c.getLong(11),
        bvid = c.getString(12),
        uri = c.getString(13),
        progress = c.getLong(14),
        duration = c.getLong(15),
        page = c.getInt(16),
        videos = c.getInt(17),
        badge = c.getString(18),
        viewAt = c.getLong(19),
    )

    private fun LocalHistoryRecord.toContentValues(): ContentValues = ContentValues().apply {
        put("kid", kid)
        put("oid", oid)
        put("cid", cid)
        put("epid", epid)
        put("season_id", seasonId)
        put("business", business)
        put("tp", tp)
        put("title", title)
        put("subtitle", subtitle)
        put("cover", cover)
        put("author_name", authorName)
        put("author_mid", authorMid)
        put("bvid", bvid)
        put("uri", uri)
        put("progress", progress)
        put("duration", duration)
        put("page", page)
        put("videos", videos)
        put("badge", badge)
        put("view_at", viewAt)
    }

    companion object {
        const val DB_NAME = "bili_local_history.db"
        const val TABLE_NAME = "local_history"
        const val DB_VERSION = 2

        const val BUSINESS_ARCHIVE = "archive"
        const val BUSINESS_PGC = "pgc"
        const val TP_ARCHIVE = 3
        const val TP_PGC = 4
        const val DEFAULT_PAGE_SIZE = 20

        @Volatile
        private var instance: LocalHistoryDb? = null

        fun getInstance(context: Context): LocalHistoryDb {
            return instance ?: synchronized(this) {
                instance ?: LocalHistoryDb(context.applicationContext).also { instance = it }
            }
        }
    }
}
