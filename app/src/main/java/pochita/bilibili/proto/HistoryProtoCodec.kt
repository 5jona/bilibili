package pochita.bilibili.proto

import io.github.libxposed.api.XposedInterface
import pochita.bilibili.contract.BiliContracts.HistoryMoss
import pochita.bilibili.contract.BiliContracts.ResumePlayback
import pochita.bilibili.contract.BiliUiStrings
import pochita.history.HistoryModuleContext
import pochita.history.data.LocalHistoryDb
import pochita.history.data.LocalHistoryRecord
import pochita.history.domain.HistoryFormatters
import pochita.xposed.ReflectOps

/**
 * Translates between local SQLite history models and Bilibili's obfuscated protobuf / Moss
 * request and response types.
 */
internal class HistoryProtoCodec(
    private val context: HistoryModuleContext,
) {
    private val runtime = context.runtime

    fun handleAsyncMoss(
        chain: XposedInterface.Chain,
        buildReply: (Any?) -> Any?,
    ) {
        val req = chain.getArg(0)
        val handler = chain.getArg(1)
        val reply = runCatching { buildReply(req) }
            .onFailure { runtime.logWarn("HistoryMoss async error", it) }
            .getOrNull()
        if (handler != null && reply != null) {
            deliverMossSuccess(handler, reply)
        }
    }

    fun buildCursorV2Reply(req: Any?): Any? {
        val business = (ReflectOps.invokeZeroArg(req, "getBusiness") as? String).orEmpty()
        val reqCursor = ReflectOps.invokeZeroArg(req, "getCursor")
        val maxViewAt = (ReflectOps.invokeZeroArg(reqCursor, "getMax") as? Number)?.toLong() ?: 0L

        val (rows, hasMore) = if (context.prefs.isEnabled()) {
            context.store()?.queryCursor(
                business = business,
                maxViewAt = maxViewAt,
                limit = LocalHistoryDb.DEFAULT_PAGE_SIZE,
            ) ?: (emptyList<LocalHistoryRecord>() to false)
        } else {
            emptyList<LocalHistoryRecord>() to false
        }

        val replyCls = runtime.loadClassOrNull(HistoryMoss.CURSOR_V2_REPLY) ?: return null
        val cursorCls = runtime.loadClassOrNull(HistoryMoss.CURSOR) ?: return null
        val builder = ReflectOps.invokeStaticZeroArg(replyCls, "newBuilder") ?: return null

        val items = rows.mapNotNull(::toCursorItem)
        val nextMax = rows.lastOrNull()?.viewAt ?: 0L
        val cursorObj = ReflectOps.invokeStaticZeroArg(cursorCls, "newBuilder")?.let { cb ->
            ReflectOps.invokeOneArg(cb, "setMax", Long::class.javaPrimitiveType!!, nextMax)
            ReflectOps.invokeOneArg(cb, "setMaxTp", Int::class.javaPrimitiveType!!, rows.lastOrNull()?.tp ?: 0)
            ReflectOps.invokeZeroArg(cb, "build")
        }

        if (cursorObj != null) {
            ReflectOps.invokeOneArg(builder, "setCursor", cursorCls, cursorObj)
        }
        ReflectOps.invokeOneArg(builder, "setHasMore", Boolean::class.javaPrimitiveType!!, hasMore)
        if (items.isNotEmpty()) {
            ReflectOps.invokeOneArg(builder, "addAllItems", Iterable::class.java, items)
        }
        return ReflectOps.invokeZeroArg(builder, "build")
    }

    fun buildSearchReply(req: Any?): Any? {
        val business = (ReflectOps.invokeZeroArg(req, "getBusiness") as? String).orEmpty()
        val keyword = (ReflectOps.invokeZeroArg(req, "getKeyword") as? String).orEmpty()
        val pn = ((ReflectOps.invokeZeroArg(req, "getPn") as? Number)?.toInt() ?: 1).coerceAtLeast(1)

        val (rows, hasMore, total) = if (context.prefs.isEnabled()) {
            context.store()?.search(
                business = business,
                keyword = keyword,
                page = pn,
                pageSize = LocalHistoryDb.DEFAULT_PAGE_SIZE,
            ) ?: Triple(emptyList(), false, 0)
        } else {
            Triple(emptyList(), false, 0)
        }

        val replyCls = runtime.loadClassOrNull(HistoryMoss.SEARCH_REPLY) ?: return null
        val pageCls = runtime.loadClassOrNull(HistoryMoss.PAGE) ?: return null
        val builder = ReflectOps.invokeStaticZeroArg(replyCls, "newBuilder") ?: return null

        val items = rows.mapNotNull(::toCursorItem)
        val pageObj = ReflectOps.invokeStaticZeroArg(pageCls, "newBuilder")?.let { pb ->
            ReflectOps.invokeOneArg(pb, "setPn", Long::class.javaPrimitiveType!!, pn.toLong())
            ReflectOps.invokeOneArg(pb, "setTotal", Long::class.javaPrimitiveType!!, total.toLong())
            ReflectOps.invokeZeroArg(pb, "build")
        }

        ReflectOps.invokeOneArg(builder, "setHasMore", Boolean::class.javaPrimitiveType!!, hasMore)
        if (pageObj != null) {
            ReflectOps.invokeOneArg(builder, "setPage", pageCls, pageObj)
        }
        if (items.isNotEmpty()) {
            ReflectOps.invokeOneArg(builder, "addAllItems", Iterable::class.java, items)
        }
        return ReflectOps.invokeZeroArg(builder, "build")
    }

    fun handleDelete(req: Any?): Any? {
        val infoList = ReflectOps.invokeZeroArg(req, "getHisInfoList") as? List<*>
        if (!infoList.isNullOrEmpty()) {
            val targets = infoList.mapNotNull { info ->
                val kid = (ReflectOps.invokeZeroArg(info, "getKid") as? Number)?.toLong() ?: 0L
                val business = (ReflectOps.invokeZeroArg(info, "getBusiness") as? String).orEmpty()
                if (kid > 0L) kid to business else null
            }
            context.store()?.deleteByKids(targets)
        }
        return buildNoReply()
    }

    fun handleClear(req: Any?): Any? {
        val business = (ReflectOps.invokeZeroArg(req, "getBusiness") as? String).orEmpty()
        context.store()?.clear(business)
        return buildNoReply()
    }

    fun buildHistoryTabReply(req: Any?): Any? {
        val focusBusiness = (ReflectOps.invokeZeroArg(req, "getBusiness") as? String).orEmpty()
        val replyCls = runtime.loadClassOrNull(HistoryMoss.HISTORY_TAB_REPLY) ?: return null
        val tabCls = runtime.loadClassOrNull(HistoryMoss.CURSOR_TAB) ?: return null
        val builder = ReflectOps.invokeStaticZeroArg(replyCls, "newBuilder") ?: return null

        for ((biz, name, router) in BiliUiStrings.LOCAL_TABS) {
            val tabBuilder = ReflectOps.invokeStaticZeroArg(tabCls, "newBuilder") ?: continue
            val isFocus = if (focusBusiness.isBlank()) biz == "all" else biz == focusBusiness
            ReflectOps.invokeOneArg(tabBuilder, "setBusiness", String::class.java, biz)
            ReflectOps.invokeOneArg(tabBuilder, "setName", String::class.java, name)
            ReflectOps.invokeOneArg(tabBuilder, "setRouter", String::class.java, router)
            ReflectOps.invokeOneArg(tabBuilder, "setFocus", Boolean::class.javaPrimitiveType!!, isFocus)
            val tabObj = ReflectOps.invokeZeroArg(tabBuilder, "build") ?: continue
            ReflectOps.invokeOneArg(builder, "addTab", tabCls, tabObj)
        }
        return ReflectOps.invokeZeroArg(builder, "build")
    }

    fun buildLocalResumeHistory(reply: Any?): Any? {
        if (reply == null || !context.shouldUseLocalHistory()) return null
        val playArc = ReflectOps.invokeZeroArg(reply, "getPlayArc") ?: return null
        val cid = (ReflectOps.invokeZeroArg(playArc, "getCid") as? Number)?.toLong() ?: 0L
        val aid = (ReflectOps.invokeZeroArg(playArc, "getAid") as? Number)?.toLong() ?: 0L
        if (cid <= 0L && aid <= 0L) return null

        val store = context.store()
        val record = (if (cid > 0L) store?.getByCid(cid) else store?.getByOidOrSeason(aid)) ?: return null
        if (record.progress <= 0L) return null
        if (cid > 0L && record.cid > 0L && record.cid != cid) return null

        val clsToast = runtime.loadClassOrNull(ResumePlayback.CLS_TOAST_INFO) ?: return null
        val clsInfo = runtime.loadClassOrNull(ResumePlayback.CLS_HISTORY_INFO) ?: return null
        val clsWrapper = runtime.loadClassOrNull(ResumePlayback.CLS_HISTORY_WRAPPER) ?: return null

        val toast = ReflectOps.newInstanceNoArg(clsToast) ?: return null
        ReflectOps.setField(toast, "a", BiliUiStrings.formatResumeToast(HistoryFormatters.formatDurationText(record.progress)))
        ReflectOps.setField(toast, "b", null)

        val currentVideo = ReflectOps.newInstanceNoArg(clsInfo) ?: return null
        ReflectOps.setField(currentVideo, "a", record.cid)
        ReflectOps.setField(currentVideo, "b", record.progress)
        ReflectOps.setField(currentVideo, "c", toast)
        ReflectOps.setField(currentVideo, "d", toast)
        ReflectOps.setField(currentVideo, "e", record.oid)

        val historyWrapper = ReflectOps.newInstanceNoArg(clsWrapper) ?: return null
        ReflectOps.setField(historyWrapper, "a", currentVideo)
        ReflectOps.setField(historyWrapper, "b", null)
        return historyWrapper
    }

    private fun deliverMossSuccess(handler: Any, reply: Any) {
        runCatching {
            ReflectOps.invokeOneArg(handler, "onNext", Any::class.java, reply)
            ReflectOps.invokeZeroArg(handler, "onCompleted")
        }.onFailure { runtime.logWarn("deliverMossSuccess failed", it) }
    }

    private fun buildNoReply(): Any? {
        val noReplyCls = runtime.loadClassOrNull(HistoryMoss.NO_REPLY) ?: return null
        return ReflectOps.invokeStaticZeroArg(noReplyCls, "getDefaultInstance")
    }

    private fun toCursorItem(rec: LocalHistoryRecord): Any? {
        val itemCls = runtime.loadClassOrNull(HistoryMoss.CURSOR_ITEM) ?: return null
        val itemBuilder = ReflectOps.invokeStaticZeroArg(itemCls, "newBuilder") ?: return null

        ReflectOps.invokeOneArg(itemBuilder, "setTitle", String::class.java, rec.title)
        ReflectOps.invokeOneArg(itemBuilder, "setUri", String::class.java, rec.uri)
        ReflectOps.invokeOneArg(itemBuilder, "setViewAt", Long::class.javaPrimitiveType!!, rec.viewAt)
        ReflectOps.invokeOneArg(itemBuilder, "setKid", Long::class.javaPrimitiveType!!, rec.kid)
        ReflectOps.invokeOneArg(itemBuilder, "setOid", Long::class.javaPrimitiveType!!, rec.oid)
        ReflectOps.invokeOneArg(itemBuilder, "setBusiness", String::class.java, rec.business)
        ReflectOps.invokeOneArg(itemBuilder, "setTp", Int::class.javaPrimitiveType!!, rec.tp)

        attachDeviceType(itemBuilder)
        if (rec.business == LocalHistoryDb.BUSINESS_PGC || rec.tp == LocalHistoryDb.TP_PGC) {
            attachOgvCard(itemBuilder, rec)
        } else {
            attachUgcCard(itemBuilder, rec)
        }
        return ReflectOps.invokeZeroArg(itemBuilder, "build")
    }

    private fun attachDeviceType(itemBuilder: Any) {
        val dtCls = runtime.loadClassOrNull(HistoryMoss.DEVICE_TYPE) ?: return
        val dtBuilder = ReflectOps.invokeStaticZeroArg(dtCls, "newBuilder") ?: return
        ReflectOps.invokeOneArg(dtBuilder, "setTypeValue", Int::class.javaPrimitiveType!!, MOBILE_DEVICE_TYPE)
        val dtObj = ReflectOps.invokeZeroArg(dtBuilder, "build") ?: return
        ReflectOps.invokeOneArg(itemBuilder, "setDt", dtCls, dtObj)
    }

    private fun attachOgvCard(itemBuilder: Any, rec: LocalHistoryRecord) {
        val ogvCls = runtime.loadClassOrNull(HistoryMoss.CARD_OGV) ?: return
        val ogvBuilder = ReflectOps.invokeStaticZeroArg(ogvCls, "newBuilder") ?: return
        ReflectOps.invokeOneArg(ogvBuilder, "setCover", String::class.java, rec.cover)
        ReflectOps.invokeOneArg(ogvBuilder, "setSubtitle", String::class.java, rec.subtitle)
        ReflectOps.invokeOneArg(ogvBuilder, "setBadge", String::class.java, rec.badge)
        ReflectOps.invokeOneArg(ogvBuilder, "setDuration", Long::class.javaPrimitiveType!!, rec.duration.coerceAtLeast(0L))
        ReflectOps.invokeOneArg(ogvBuilder, "setProgress", Long::class.javaPrimitiveType!!, rec.progress)
        val ogvObj = ReflectOps.invokeZeroArg(ogvBuilder, "build") ?: return
        ReflectOps.invokeOneArg(itemBuilder, "setCardOgv", ogvCls, ogvObj)
    }

    private fun attachUgcCard(itemBuilder: Any, rec: LocalHistoryRecord) {
        val ugcCls = runtime.loadClassOrNull(HistoryMoss.CARD_UGC) ?: return
        val ugcBuilder = ReflectOps.invokeStaticZeroArg(ugcCls, "newBuilder") ?: return
        ReflectOps.invokeOneArg(ugcBuilder, "setCover", String::class.java, rec.cover)
        ReflectOps.invokeOneArg(ugcBuilder, "setProgress", Long::class.javaPrimitiveType!!, rec.progress)
        ReflectOps.invokeOneArg(ugcBuilder, "setDuration", Long::class.javaPrimitiveType!!, rec.duration.coerceAtLeast(0L))
        ReflectOps.invokeOneArg(ugcBuilder, "setName", String::class.java, rec.authorName)
        ReflectOps.invokeOneArg(ugcBuilder, "setMid", Long::class.javaPrimitiveType!!, rec.authorMid.coerceAtLeast(0L))
        ReflectOps.invokeOneArg(ugcBuilder, "setCid", Long::class.javaPrimitiveType!!, rec.cid.coerceAtLeast(0L))
        ReflectOps.invokeOneArg(ugcBuilder, "setPage", Int::class.javaPrimitiveType!!, rec.page.coerceAtLeast(1))
        ReflectOps.invokeOneArg(ugcBuilder, "setSubtitle", String::class.java, rec.subtitle)
        ReflectOps.invokeOneArg(ugcBuilder, "setBvid", String::class.java, rec.bvid)
        ReflectOps.invokeOneArg(ugcBuilder, "setVideos", Long::class.javaPrimitiveType!!, rec.videos.coerceAtLeast(1).toLong())
        val ugcObj = ReflectOps.invokeZeroArg(ugcBuilder, "build") ?: return
        ReflectOps.invokeOneArg(itemBuilder, "setCardUgc", ugcCls, ugcObj)
    }

    companion object {
        private const val MOBILE_DEVICE_TYPE = 2
    }
}
