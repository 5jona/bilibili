package pochita.history

import android.content.Context
import pochita.config.LocalHistoryPrefs
import pochita.config.PlaybackSpeedPrefs
import pochita.speed.SpeedSpec
import pochita.history.data.HistoryStore
import pochita.history.data.LocalHistoryDb
import pochita.history.domain.HistorySwitchCoordinator
import pochita.history.domain.MetadataCache
import pochita.history.domain.PlaybackHistoryRecorder
import pochita.xposed.HookRuntime

/**
 * Shared runtime container wiring configuration, storage, state coordination, and hook runtime
 * inside Bilibili's main process (`tv.danmaku.bili`).
 */
internal class HistoryModuleContext(
    val runtime: HookRuntime,
) {
    @Volatile
    var appContext: Context? = null
        private set

    val prefs = LocalHistoryPrefs(
        contextProvider = ::ensureContext,
        onWarn = runtime::logWarn,
    )

    val speedPrefs = PlaybackSpeedPrefs(
        contextProvider = ::ensureContext,
        normalizeSpeed = SpeedSpec::normalizeLongPressSpeed,
        onWarn = runtime::logWarn,
    )

    val metadataCache = MetadataCache()

    val switchCoordinator = HistorySwitchCoordinator(initialCloudClosed = true)

    val playbackRecorder = PlaybackHistoryRecorder(
        metadataCache = metadataCache,
        storeProvider = ::store,
        shouldRecordProvider = ::shouldUseLocalHistory,
    )

    val isCloudHistoryClosed: Boolean
        get() = switchCoordinator.isCloudHistoryClosed

    /**
     * Returns `true` only when official cloud history is turned off and module local history is on.
     */
    fun shouldUseLocalHistory(): Boolean = isCloudHistoryClosed && prefs.isEnabled()

    /**
     * Binds and warms the host application [Context] and SQLite store once `Application.onCreate` runs.
     */
    fun attachApplication(context: Context) {
        appContext = context.applicationContext ?: context
        runCatching { LocalHistoryDb.getInstance(context) }
            .onFailure { runtime.logWarn("Failed to initialize LocalHistoryDb", it) }
    }

    /**
     * Resolves the host [Context], falling back to `ActivityThread.currentApplication()` when
     * hooks execute before `Application.onCreate` finishes caching [appContext].
     */
    fun ensureContext(): Context? {
        appContext?.let { return it }
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val app = activityThread.getMethod("currentApplication").invoke(null) as? Context
            if (app != null) {
                appContext = app.applicationContext ?: app
            }
            appContext
        }.getOrNull()
    }

    fun store(): HistoryStore? = ensureContext()?.let { LocalHistoryDb.getInstance(it) }
}
