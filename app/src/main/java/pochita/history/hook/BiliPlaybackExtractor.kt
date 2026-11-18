package pochita.history.hook

import pochita.history.domain.HistoryFormatters
import pochita.history.domain.HistoryFormatters.firstNonBlank
import pochita.history.domain.HistoryFormatters.firstPositiveLong
import pochita.history.domain.PlaybackSnapshot
import pochita.xposed.ReflectOps

/**
 * Extracts normalized [PlaybackSnapshot] payloads from obfuscated Bilibili player objects.
 */
internal object BiliPlaybackExtractor {

    /**
     * Decodes progress in seconds from Bilibili's Kotlin-duration position holder.
     * Objects ending with `$a` represent an uninitialized sentinel and return `-1L`.
     */
    fun extractProgressSec(posObj: Any?): Long {
        if (posObj == null || posObj.javaClass.name.endsWith("\$a")) return -1L
        val rawDuration = ReflectOps.getLong(posObj, "a", 0L)
        return HistoryFormatters.decodeKotlinDurationSec(rawDuration).coerceAtLeast(0L)
    }

    fun fromTheseus2Service(
        service: Any,
        posObj: Any?,
        cidArg: Long,
        aidArg: Long,
        stateObj: Any?,
    ): PlaybackSnapshot? {
        val kw0c = ReflectOps.getField(service, "h") ?: return null
        val bizTypeObj = ReflectOps.getField(kw0c, "V")
        val bizName = firstNonBlank(
            ReflectOps.invokeZeroArg(bizTypeObj, "getValue") as? String,
            (bizTypeObj as? Enum<*>)?.name,
        )
        if (bizName.equals("pugv", ignoreCase = true)) return null

        val sceneObj = ReflectOps.getField(kw0c, "r")
        val sceneName = (sceneObj as? Enum<*>)?.name.orEmpty()
        if (sceneName.contains("INLINE", ignoreCase = true)) return null

        val aid = firstPositiveLong(aidArg, ReflectOps.getLong(kw0c, "a"))
        val cid = firstPositiveLong(cidArg, ReflectOps.getLong(kw0c, "b"))
        val seasonId = ReflectOps.getLong(kw0c, "x")
        val epid = ReflectOps.getLong(kw0c, "y")

        return PlaybackSnapshot(
            aid = aid,
            cid = cid,
            seasonId = seasonId,
            epid = epid,
            bizTypeHint = bizName,
            bvid = ReflectOps.getString(kw0c, "f"),
            title = ReflectOps.getString(kw0c, "e"),
            subtitle = ReflectOps.getString(kw0c, "o"),
            cover = ReflectOps.getString(kw0c, "g"),
            authorName = firstNonBlank(ReflectOps.getString(kw0c, "K"), ReflectOps.getString(kw0c, "L")),
            authorMid = ReflectOps.getLong(kw0c, "J").coerceAtLeast(0L),
            page = maxOf(ReflectOps.getInt(kw0c, "n", 0), ReflectOps.getInt(kw0c, "l", 0), 1),
            videos = maxOf(ReflectOps.getInt(kw0c, "u", 1), 1),
            progressSec = extractProgressSec(posObj),
            durationSec = HistoryFormatters.decodeKotlinDurationSec(ReflectOps.getLong(stateObj, "a")),
            cacheUgcMetadata = true,
            subtitleOnlyWhenMultiPart = true,
        )
    }

    fun fromLegacyTheseusService(service: Any?): PlaybackSnapshot? {
        if (service == null) return null
        val params = ReflectOps.getField(service, "h") ?: return null
        val dObj = ReflectOps.getField(service, "d")
        val kObj = ReflectOps.getField(service, "k")
        val lObj = ReflectOps.getField(service, "l")

        val progressSec = if (lObj != null) {
            extractProgressSec(lObj)
        } else {
            HistoryFormatters.decodeKotlinDurationSec(ReflectOps.getLong(kObj, "b")).coerceAtLeast(0L)
        }

        return PlaybackSnapshot(
            aid = (ReflectOps.invokeZeroArg(params, "getAvid") as? Number)?.toLong() ?: 0L,
            cid = (ReflectOps.invokeZeroArg(params, "getCid") as? Number)?.toLong() ?: 0L,
            seasonId = (ReflectOps.invokeZeroArg(params, "getSeasonId") as? Number)?.toLong() ?: 0L,
            epid = (ReflectOps.invokeZeroArg(params, "getEpIdForReport") as? Number)?.toLong() ?: 0L,
            bvid = (ReflectOps.invokeZeroArg(params, "getBvId") as? String).orEmpty(),
            title = (ReflectOps.invokeZeroArg(params, "getTitle") as? String).orEmpty(),
            subtitle = (ReflectOps.invokeZeroArg(params, "getPageTitle") as? String).orEmpty(),
            cover = (ReflectOps.invokeZeroArg(params, "getCover") as? String).orEmpty(),
            authorName = (ReflectOps.invokeZeroArg(params, "getAuthor") as? String).orEmpty(),
            authorMid = ((ReflectOps.invokeZeroArg(params, "getMid") as? Number)?.toLong() ?: 0L).coerceAtLeast(0L),
            page = ((ReflectOps.invokeZeroArg(params, "getPage") as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            videos = maxOf((ReflectOps.invokeZeroArg(dObj, "itemCount") as? Number)?.toInt() ?: 1, 1),
            progressSec = progressSec,
            durationSec = HistoryFormatters.decodeKotlinDurationSec(ReflectOps.getLong(kObj, "a")),
            cacheUgcMetadata = true,
            subtitleOnlyWhenMultiPart = true,
        )
    }

    fun fromInlineReportParams(inlineA: Any?): PlaybackSnapshot? {
        if (inlineA == null) return null
        val cid = ReflectOps.getLong(inlineA, "d")
        val aid = ReflectOps.getLong(inlineA, "e")
        val seasonId = ReflectOps.getLong(inlineA, "f")
        val epid = ReflectOps.getLong(inlineA, "g")
        if (aid <= 0L && seasonId <= 0L && epid <= 0L) return null

        return PlaybackSnapshot(
            aid = aid,
            cid = cid,
            seasonId = seasonId,
            epid = epid,
            progressSec = ReflectOps.getLong(inlineA, "h"),
            durationSec = ReflectOps.getLong(inlineA, "i").coerceAtLeast(0L),
            lookupExistingInStore = true,
            requireKnownTitleWhenNew = true,
        )
    }
}
