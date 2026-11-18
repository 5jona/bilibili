package pochita.history.hook

import pochita.bilibili.contract.BiliContracts.MetadataCapture
import pochita.bilibili.contract.BiliUiStrings
import pochita.history.HistoryModuleContext
import pochita.history.domain.HistoryFormatters.firstNonBlank
import pochita.history.domain.HistoryFormatters.firstPositiveLong
import pochita.history.domain.OgvMeta
import pochita.history.domain.UgcMeta
import pochita.xposed.ReflectOps
import java.lang.ref.WeakReference

/**
 * Hooks UGC and OGV player/detail services (`theseus2` and legacy `theseus`) to populate
 * [pochita.history.domain.MetadataCache] and write initial local history entries on open.
 */
internal class PlaybackCaptureHooks(
    private val context: HistoryModuleContext,
) {
    private val runtime = context.runtime
    private val cache = context.metadataCache
    private val recorder = context.playbackRecorder

    fun install() {
        installTheseus2Hooks()
        installLegacyTheseusHooks()
    }

    private fun installTheseus2Hooks() {
        runtime.hookAllConstructors(MetadataCapture.THESEUS2_UGC_SUPPLEMENT) { chain ->
            runCatching { onUgcSupplementCreated(chain.thisObject) }
                .onFailure { runtime.logWarn("UGCSupplementService init hook error", it) }
        }
        runtime.hookInvokeSuspendIfPresent("${MetadataCapture.THESEUS2_UGC_SUPPLEMENT}\$1") { chain ->
            onUgcSupplementCreated(ReflectOps.getField(chain.thisObject, "this\$0"))
        }
        runtime.hookMethodIfPresent(
            MetadataCapture.THESEUS2_VIEW_DATA,
            "c",
            MetadataCapture.THESEUS2_VIEW_REPLY,
        ) { chain ->
            val res = chain.proceed()
            runCatching { onTheseus2ViewUpdated(chain.thisObject) }
                .onFailure { runtime.logWarn("theseus2 view.a.c hook error", it) }
            res
        }

        runtime.hookAllConstructors(MetadataCapture.THESEUS2_OGV_SUPPLEMENT) { chain ->
            runCatching { onOgvSupplementCreated(chain.thisObject) }
                .onFailure { runtime.logWarn("OgvSupplementService init hook error", it) }
        }
        runtime.hookInvokeSuspendIfPresent("${MetadataCapture.THESEUS2_OGV_SUPPLEMENT}\$1") { chain ->
            onOgvSupplementCreated(ReflectOps.getField(chain.thisObject, "this\$0"))
        }
    }

    private fun installLegacyTheseusHooks() {
        runtime.hookAllConstructors(MetadataCapture.LEGACY_VIEW_DATA) { chain ->
            runCatching { onLegacyViewDataCreated(chain.thisObject) }
                .onFailure { runtime.logWarn("legacy view.a init hook error", it) }
        }
        runtime.hookAllConstructors(MetadataCapture.LEGACY_UGC_SUPPLEMENT) { chain ->
            runCatching { onLegacyUgcSupplementCreated(chain.thisObject) }
                .onFailure { runtime.logWarn("legacy UGCSupplementService init hook error", it) }
        }
        runtime.hookInvokeSuspendIfPresent("${MetadataCapture.LEGACY_UGC_SUPPLEMENT}\$1\$1") { chain ->
            onLegacyUgcSupplementCreated(ReflectOps.getField(chain.thisObject, "this\$0"))
        }
        runtime.hookAllConstructors(MetadataCapture.LEGACY_OGV_SUPPLEMENT) { chain ->
            runCatching { onLegacyOgvSupplementCreated(chain.thisObject) }
                .onFailure { runtime.logWarn("legacy OgvSupplementService init hook error", it) }
        }
        runtime.hookInvokeSuspendIfPresent("${MetadataCapture.LEGACY_OGV_SUPPLEMENT}\$1\$1") { chain ->
            onLegacyOgvSupplementCreated(ReflectOps.getField(chain.thisObject, "this\$0"))
        }
    }

    private fun onUgcSupplementCreated(service: Any?) {
        if (service == null) return
        val startParams = ReflectOps.getField(service, "c")
        val unitedEp = ReflectOps.getField(service, "d")
        val viewData = ReflectOps.getField(service, "e")
        val authorRepo = ReflectOps.getField(service, "i")
        val epList = ReflectOps.getField(service, "j") as? List<*>
        val mediaplayB = ReflectOps.getField(service, "n")
        val kw0c = ReflectOps.invokeZeroArg(mediaplayB, "a")

        val aid = firstPositiveLong(
            (ReflectOps.invokeZeroArg(viewData, "a") as? Number)?.toLong() ?: 0L,
            ReflectOps.getLong(unitedEp, "a"),
            ReflectOps.getLong(startParams, "a"),
            ReflectOps.getLong(kw0c, "a"),
        )
        val cid = firstPositiveLong(
            (ReflectOps.invokeZeroArg(viewData, "e") as? Number)?.toLong() ?: 0L,
            ReflectOps.getLong(unitedEp, "b"),
            ReflectOps.getLong(startParams, "b"),
            ReflectOps.getLong(kw0c, "b"),
        )
        if (aid <= 0L) return

        val bvid = firstNonBlank(
            ReflectOps.invokeZeroArg(viewData, "d") as? String,
            ReflectOps.getString(kw0c, "f"),
        )
        val archiveTitle = firstNonBlank(
            ReflectOps.invokeZeroArg(viewData, "h") as? String,
            ReflectOps.getString(unitedEp, "j"),
            ReflectOps.getString(kw0c, "e"),
        )
        val epTitle = ReflectOps.getString(unitedEp, "d")
        val cover = firstNonBlank(
            ReflectOps.getString(viewData, "o"),
            ReflectOps.getString(unitedEp, "k"),
            ReflectOps.getString(kw0c, "g"),
        )
        val videos = maxOf(epList?.size ?: 0, ReflectOps.getInt(kw0c, "u", 1), 1)
        val idx = epList?.indexOf(unitedEp) ?: -1
        val unitedEpIndex = ReflectOps.getInt(unitedEp, "c", 0)
        val page = when {
            idx >= 0 -> idx + 1
            unitedEpIndex > 0 -> unitedEpIndex
            else -> maxOf(ReflectOps.getInt(kw0c, "n", 0), ReflectOps.getInt(kw0c, "l", 0), 1)
        }
        val durationSec = firstPositiveLong(
            (ReflectOps.invokeZeroArg(viewData, "i") as? Number)?.toLong() ?: 0L,
            ReflectOps.getLong(unitedEp, "f") / 1000L,
        )
        val authorMid = firstPositiveLong(
            (ReflectOps.invokeZeroArg(authorRepo, "i") as? Number)?.toLong() ?: 0L,
            ReflectOps.getLong(kw0c, "J").coerceAtLeast(0L),
        )
        val authorName = firstNonBlank(
            ReflectOps.invokeZeroArg(authorRepo, "j") as? String,
            ReflectOps.getString(kw0c, "K"),
            ReflectOps.getString(kw0c, "L"),
        )

        recorder.recordUgcOpen(
            UgcMeta(
                aid = aid,
                cid = cid,
                bvid = bvid,
                title = archiveTitle,
                subtitle = if (videos > 1 && epTitle.isNotBlank()) epTitle else "",
                cover = cover,
                page = page.coerceAtLeast(1),
                videos = videos.coerceAtLeast(1),
                durationSec = durationSec.coerceAtLeast(0L),
                authorRepoRef = authorRepo?.let { WeakReference(it) },
                initialAuthorMid = authorMid.coerceAtLeast(0L),
                initialAuthorName = authorName,
            ),
        )
    }

    private fun onTheseus2ViewUpdated(viewData: Any?) {
        recordViewDataUpdate(
            viewData = viewData,
            aidMethod = "a",
            cidMethod = "e",
            bvidMethod = "d",
            titleMethod = "h",
            durationMethod = "i",
            coverField = "o",
        )
    }

    private fun onLegacyViewDataCreated(viewData: Any?) {
        recordViewDataUpdate(
            viewData = viewData,
            aidMethod = "a",
            cidMethod = "d",
            bvidMethod = "c",
            titleMethod = "g",
            durationMethod = "h",
            coverField = "l",
        )
    }

    private fun recordViewDataUpdate(
        viewData: Any?,
        aidMethod: String,
        cidMethod: String,
        bvidMethod: String,
        titleMethod: String,
        durationMethod: String,
        coverField: String,
    ) {
        if (viewData == null) return
        val aid = (ReflectOps.invokeZeroArg(viewData, aidMethod) as? Number)?.toLong() ?: 0L
        if (aid <= 0L) return
        val cid = (ReflectOps.invokeZeroArg(viewData, cidMethod) as? Number)?.toLong() ?: 0L
        val bvid = (ReflectOps.invokeZeroArg(viewData, bvidMethod) as? String).orEmpty()
        val title = (ReflectOps.invokeZeroArg(viewData, titleMethod) as? String).orEmpty()
        val durationSec = (ReflectOps.invokeZeroArg(viewData, durationMethod) as? Number)?.toLong() ?: 0L
        val cover = ReflectOps.getString(viewData, coverField)

        val cached = cache.getUgc(aid, cid)
        recorder.recordUgcOpen(
            UgcMeta(
                aid = aid,
                cid = firstPositiveLong(cid, cached?.cid ?: 0L),
                bvid = firstNonBlank(bvid, cached?.bvid),
                title = firstNonBlank(title, cached?.title),
                subtitle = cached?.subtitle.orEmpty(),
                cover = firstNonBlank(cover, cached?.cover),
                page = (cached?.page ?: 1).coerceAtLeast(1),
                videos = (cached?.videos ?: 1).coerceAtLeast(1),
                durationSec = firstPositiveLong(durationSec, cached?.durationSec ?: 0L),
                authorRepoRef = cached?.authorRepoRef,
                initialAuthorMid = cached?.resolveAuthorMid() ?: 0L,
                initialAuthorName = cached?.resolveAuthorName().orEmpty(),
            ),
        )
    }

    private fun onLegacyUgcSupplementCreated(service: Any?) {
        if (service == null) return
        val viewData = ReflectOps.getField(service, "c")
        val viewV = ReflectOps.getField(service, "f")
        val viewH = ReflectOps.getField(viewV, "b")
        val viewR = ReflectOps.getField(viewH, "b")
        val authorRepo = ReflectOps.getField(service, "g")
        val epList = ReflectOps.getField(service, "h") as? List<*>

        val aid = (ReflectOps.invokeZeroArg(viewData, "a") as? Number)?.toLong() ?: 0L
        val cid = (ReflectOps.invokeZeroArg(viewData, "d") as? Number)?.toLong() ?: 0L
        if (aid <= 0L) return

        val bvid = (ReflectOps.invokeZeroArg(viewData, "c") as? String).orEmpty()
        val title = firstNonBlank(
            ReflectOps.invokeZeroArg(viewData, "g") as? String,
            ReflectOps.getString(viewR, "a"),
        )
        val cover = firstNonBlank(
            ReflectOps.getString(viewData, "l"),
            ReflectOps.getString(viewR, "b"),
        )
        val durationSec = (ReflectOps.invokeZeroArg(viewData, "h") as? Number)?.toLong() ?: 0L
        val videos = maxOf(epList?.size ?: 0, 1)
        val authorMid = ((ReflectOps.invokeZeroArg(authorRepo, "f") as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
        val authorName = (ReflectOps.invokeZeroArg(authorRepo, "g") as? String).orEmpty()

        recorder.recordUgcOpen(
            UgcMeta(
                aid = aid,
                cid = cid,
                bvid = bvid,
                title = title,
                subtitle = "",
                cover = cover,
                page = 1,
                videos = videos,
                durationSec = durationSec,
                authorRepoRef = authorRepo?.let { WeakReference(it) },
                initialAuthorMid = authorMid,
                initialAuthorName = authorName,
            ),
        )
    }

    private fun onOgvSupplementCreated(service: Any?) {
        if (service == null) return
        val mediaplayB = ReflectOps.getField(service, "d")
        val kw0c = ReflectOps.invokeZeroArg(mediaplayB, "a")
        val episode = ReflectOps.getField(service, "e")
        val season = ReflectOps.getField(service, "f")

        val epid = firstPositiveLong(ReflectOps.getLong(episode, "a"), ReflectOps.getLong(kw0c, "y"))
        val aid = firstPositiveLong(ReflectOps.getLong(episode, "f"), ReflectOps.getLong(kw0c, "a"))
        val cid = firstPositiveLong(ReflectOps.getLong(episode, "g"), ReflectOps.getLong(kw0c, "b"))
        val seasonId = firstPositiveLong(ReflectOps.getLong(season, "a"), ReflectOps.getLong(kw0c, "x"))
        if (firstPositiveLong(seasonId, epid, aid) <= 0L) return

        val seasonTitle = firstNonBlank(
            ReflectOps.invokeZeroArg(season, "d") as? String,
            ReflectOps.getString(season, "e"),
            ReflectOps.getString(kw0c, "e"),
        )
        val shortTitle = (ReflectOps.invokeZeroArg(episode, "c") as? String).orEmpty()
        val longTitle = (ReflectOps.invokeZeroArg(episode, "b") as? String).orEmpty()
        val cover = firstNonBlank(
            ReflectOps.invokeZeroArg(episode, "a") as? String,
            ReflectOps.getString(season, "j"),
            ReflectOps.getString(kw0c, "g"),
        )

        recorder.recordOgvOpen(
            OgvMeta(
                seasonId = seasonId,
                epid = epid,
                aid = aid,
                cid = cid,
                title = seasonTitle,
                subtitle = BiliUiStrings.formatEpisodeLabel(shortTitle, longTitle),
                cover = cover,
            ),
        )
    }

    private fun onLegacyOgvSupplementCreated(service: Any?) {
        if (service == null) return
        val season = ReflectOps.getField(service, "d")
        val epManager = ReflectOps.getField(service, "j")
        val epFlow = ReflectOps.getField(epManager, "c")
        val episode = ReflectOps.invokeZeroArg(epFlow, "getValue")

        val seasonId = ReflectOps.getLong(season, "a")
        val epid = firstPositiveLong(
            (ReflectOps.invokeZeroArg(episode, "a") as? Number)?.toLong() ?: 0L,
            ReflectOps.getLong(episode, "a"),
        )
        val aid = firstPositiveLong(
            (ReflectOps.invokeZeroArg(episode, "f") as? Number)?.toLong() ?: 0L,
            ReflectOps.getLong(episode, "f"),
        )
        val cid = ReflectOps.getLong(episode, "g")
        if (firstPositiveLong(seasonId, epid, aid) <= 0L) return

        val seasonTitle = firstNonBlank(
            ReflectOps.invokeZeroArg(season, "d") as? String,
            ReflectOps.getString(season, "e"),
        )
        val shortTitle = (ReflectOps.invokeZeroArg(episode, "c") as? String).orEmpty()
        val longTitle = (ReflectOps.invokeZeroArg(episode, "b") as? String).orEmpty()
        val cover = firstNonBlank(
            ReflectOps.getString(season, "j"),
            ReflectOps.getString(episode, "h"),
        )

        recorder.recordOgvOpen(
            OgvMeta(
                seasonId = seasonId,
                epid = epid,
                aid = aid,
                cid = cid,
                title = seasonTitle,
                subtitle = BiliUiStrings.formatEpisodeLabel(shortTitle, longTitle),
                cover = cover,
            ),
        )
    }
}
