package pochita.history.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [HistoryFormatters].
 */
class HistoryFormattersTest {

    @Test
    fun decodeKotlinDurationSec_decodesNanosecondAndMillisecondPackedValues() {
        // 45 seconds encoded as nanoseconds (raw = (45 * 1_000_000_000) shl 1)
        val rawNs = (45L * 1_000_000_000L) shl 1
        assertEquals(45L, HistoryFormatters.decodeKotlinDurationSec(rawNs))

        // 125 seconds encoded as milliseconds (raw = ((125 * 1_000) shl 1) or 1)
        val rawMs = ((125L * 1_000L) shl 1) or 1L
        assertEquals(125L, HistoryFormatters.decodeKotlinDurationSec(rawMs))

        assertEquals(0L, HistoryFormatters.decodeKotlinDurationSec(-1L))
        assertEquals(0L, HistoryFormatters.decodeKotlinDurationSec(0L))
    }

    @Test
    fun formatDurationText_formatsHoursAndMinutes() {
        assertEquals("00:00", HistoryFormatters.formatDurationText(-5L))
        assertEquals("02:05", HistoryFormatters.formatDurationText(125L))
        assertEquals("1:01:09", HistoryFormatters.formatDurationText(3669L))
    }

    @Test
    fun buildOgvUri_prefersEpidThenSeasonThenAid() {
        assertEquals("bilibili://pgc/season/ep/20", HistoryFormatters.buildOgvUri(10L, 20L, 30L))
        assertEquals("bilibili://pgc/season/10", HistoryFormatters.buildOgvUri(10L, 0L, 30L))
        assertEquals("bilibili://video/30", HistoryFormatters.buildOgvUri(0L, 0L, 30L))
        assertEquals("", HistoryFormatters.buildOgvUri(0L, 0L, 0L))
    }
}
