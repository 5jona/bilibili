package pochita.speed

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure domain specification for playback speed normalization, presets, formatting, and slider mapping.
 */
internal object SpeedSpec {
    const val MIN_SPEED = 0.1f
    const val MAX_SPEED = 8.0f
    const val DEFAULT_NORMAL_SPEED = 1.0f
    const val DEFAULT_LONG_PRESS_SPEED = 2.0f

    const val SLIDER_STEP = 0.05f
    const val MATCH_EPSILON = 0.005f
    private const val PRECISION_SCALE = 100f

    /**
     * Descending preset list exposed in playback speed panels and player setting menus.
     */
    val NORMAL_PRESETS_DESC: List<Float> = listOf(
        4.0f,
        3.0f,
        2.5f,
        2.0f,
        1.5f,
        1.25f,
        1.0f,
        0.75f,
        0.5f,
        0.25f,
    )

    /**
     * Quick-switch presets offered inside the long-press speed configuration section.
     */
    val LONG_PRESS_PRESETS: List<Float> = listOf(
        2.0f,
        3.0f,
        4.0f,
    )

    val SLIDER_MAX_PROGRESS: Int = ((MAX_SPEED - MIN_SPEED) / SLIDER_STEP).roundToInt()

    /**
     * Coerces a finite speed into `[MIN_SPEED, MAX_SPEED]` rounded to two decimal places, or falls
     * back to [fallback] when non-finite or non-positive.
     */
    fun normalizeSpeed(speed: Float, fallback: Float = DEFAULT_NORMAL_SPEED): Float {
        if (!speed.isFinite() || speed <= 0f) {
            return fallback
        }
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        return (clamped * PRECISION_SCALE).roundToInt() / PRECISION_SCALE
    }

    fun normalizeLongPressSpeed(speed: Float): Float =
        normalizeSpeed(speed, DEFAULT_LONG_PRESS_SPEED)

    fun isSameSpeed(first: Float, second: Float): Boolean =
        abs(first - second) < MATCH_EPSILON

    fun isPresetNormalSpeed(speed: Float): Boolean =
        NORMAL_PRESETS_DESC.any { isSameSpeed(it, speed) }

    fun isPresetLongPressSpeed(speed: Float): Boolean =
        LONG_PRESS_PRESETS.any { isSameSpeed(it, speed) }

    /**
     * Formats a multiplier matching host display conventions (`1.0X`, `1.25X`, `2.35X`).
     */
    fun formatSpeedLabel(speed: Float): String = "${formatSpeedNumber(speed)}X"

    /**
     * Formats the numeric value cleanly with one or two decimals (`1.0`, `2.5`, `1.25`, `2.35`).
     */
    fun formatSpeedNumber(speed: Float): String {
        val normalized = normalizeSpeed(speed)
        val hundredths = (normalized * PRECISION_SCALE).roundToInt()
        return if (hundredths % 10 == 0) {
            String.format(Locale.US, "%.1f", hundredths / PRECISION_SCALE)
        } else {
            String.format(Locale.US, "%.2f", hundredths / PRECISION_SCALE)
        }
    }

    /**
     * Parses user-entered text (including trailing `X`, `x`, or localized multiplier suffixes) and
     * returns a normalized value if it lies within `[MIN_SPEED, MAX_SPEED]`, or `null` otherwise.
     */
    fun parseSpeedInput(raw: String): Float? {
        val cleaned = raw.trim()
            .removeSuffix("X")
            .removeSuffix("x")
            .removeSuffix("倍")
            .trim()
        if (cleaned.isEmpty()) return null
        val parsed = cleaned.toFloatOrNull() ?: return null
        if (!parsed.isFinite() || parsed < MIN_SPEED || parsed > MAX_SPEED) return null
        return normalizeSpeed(parsed)
    }

    fun stepSpeed(current: Float, delta: Float): Float =
        normalizeSpeed(current + delta, current)

    fun speedToSliderProgress(speed: Float): Int {
        val normalized = normalizeSpeed(speed)
        return ((normalized - MIN_SPEED) / SLIDER_STEP).roundToInt()
            .coerceIn(0, SLIDER_MAX_PROGRESS)
    }

    fun sliderProgressToSpeed(progress: Int): Float {
        val bounded = progress.coerceIn(0, SLIDER_MAX_PROGRESS)
        return normalizeSpeed(MIN_SPEED + bounded * SLIDER_STEP)
    }
}
