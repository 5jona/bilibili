package pochita.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Shared preference identifiers stored inside the host application's private preferences.
 */
object ModulePrefs {
    const val PREFS_NAME = "bili_local_history_prefs"
    const val KEY_LOCAL_HISTORY_ENABLED = "local_history_enabled"
    const val KEY_LONG_PRESS_SPEED = "long_press_speed"

    const val DEFAULT_LOCAL_HISTORY_ENABLED = true
    const val DEFAULT_LONG_PRESS_SPEED = 2.0f
}

/**
 * Thread-safe, memory-cached accessor for module preferences in the host process.
 */
internal class LocalHistoryPrefs(
    private val contextProvider: () -> Context?,
    private val onWarn: (String, Throwable) -> Unit = { _, _ -> },
) {
    @Volatile
    private var enabledCache: Boolean? = null

    private val prefs: SharedPreferences?
        get() = contextProvider()?.getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns whether local watch history recording and rendering are currently enabled.
     */
    fun isEnabled(): Boolean {
        enabledCache?.let { return it }
        val sharedPrefs = prefs ?: return ModulePrefs.DEFAULT_LOCAL_HISTORY_ENABLED
        val persisted = runCatching {
            sharedPrefs.getBoolean(
                ModulePrefs.KEY_LOCAL_HISTORY_ENABLED,
                ModulePrefs.DEFAULT_LOCAL_HISTORY_ENABLED,
            )
        }.getOrDefault(ModulePrefs.DEFAULT_LOCAL_HISTORY_ENABLED)
        enabledCache = persisted
        return persisted
    }

    /**
     * Updates both the in-memory flag and the persisted [SharedPreferences] entry.
     */
    fun setEnabled(enabled: Boolean) {
        enabledCache = enabled
        runCatching {
            prefs?.edit()
                ?.putBoolean(ModulePrefs.KEY_LOCAL_HISTORY_ENABLED, enabled)
                ?.apply()
        }.onFailure { onWarn("Failed to persist local history preference", it) }
    }
}

/**
 * Thread-safe, memory-cached accessor for module playback speed preferences in the host process.
 */
internal class PlaybackSpeedPrefs(
    private val contextProvider: () -> Context?,
    private val normalizeSpeed: (Float) -> Float,
    private val onWarn: (String, Throwable) -> Unit = { _, _ -> },
) {
    @Volatile
    private var longPressSpeedCache: Float? = null

    private val prefs: SharedPreferences?
        get() = contextProvider()?.getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the persisted long-press temporary speed multiplier, defaulting to `2.0X`.
     */
    fun getLongPressSpeed(): Float {
        longPressSpeedCache?.let { return it }
        val sharedPrefs = prefs ?: return ModulePrefs.DEFAULT_LONG_PRESS_SPEED
        val persisted = runCatching {
            val raw = sharedPrefs.getFloat(
                ModulePrefs.KEY_LONG_PRESS_SPEED,
                ModulePrefs.DEFAULT_LONG_PRESS_SPEED,
            )
            normalizeSpeed(raw)
        }.getOrDefault(ModulePrefs.DEFAULT_LONG_PRESS_SPEED)
        longPressSpeedCache = persisted
        return persisted
    }

    /**
     * Persists a normalized long-press speed multiplier to [SharedPreferences] and cache.
     */
    fun setLongPressSpeed(speed: Float): Float {
        val normalized = normalizeSpeed(speed)
        longPressSpeedCache = normalized
        runCatching {
            prefs?.edit()
                ?.putFloat(ModulePrefs.KEY_LONG_PRESS_SPEED, normalized)
                ?.apply()
        }.onFailure { onWarn("Failed to persist long-press speed preference", it) }
        return normalized
    }
}
