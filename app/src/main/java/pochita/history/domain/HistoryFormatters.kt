package pochita.history.domain

import java.util.Locale

/**
 * Pure formatting and value-resolution utilities for local history domain logic.
 */
internal object HistoryFormatters {

    /**
     * Decodes a Kotlin `Duration` raw packed `Long` (nanoseconds when bit 0 is `0`,
     * milliseconds when bit 0 is `1`) into whole non-negative seconds.
     */
    fun decodeKotlinDurationSec(rawPackedDuration: Long): Long {
        if (rawPackedDuration <= 0L) return 0L
        val magnitude = rawPackedDuration shr 1
        return if ((rawPackedDuration and 1L) == 0L) {
            magnitude / 1_000_000_000L
        } else {
            magnitude / 1_000L
        }
    }

    /**
     * Formats [totalSec] into `H:MM:SS` or `MM:SS` display strings.
     */
    fun formatDurationText(totalSec: Long): String {
        val clamped = totalSec.coerceAtLeast(0L)
        val hours = clamped / 3600L
        val minutes = (clamped % 3600L) / 60L
        val seconds = clamped % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Builds the canonical Bilibili deep-link URI for a UGC archive.
     */
    fun buildUgcUri(aid: Long): String = if (aid > 0L) "bilibili://video/$aid" else ""

    /**
     * Builds the most specific Bilibili deep-link URI available for an OGV/PGC entry.
     */
    fun buildOgvUri(seasonId: Long, epid: Long, aid: Long): String = when {
        epid > 0L -> "bilibili://pgc/season/ep/$epid"
        seasonId > 0L -> "bilibili://pgc/season/$seasonId"
        aid > 0L -> "bilibili://video/$aid"
        else -> ""
    }

    fun firstPositiveLong(vararg values: Long): Long = values.firstOrNull { it > 0L } ?: 0L

    fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}
