package pochita.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SpeedSpec] normalization, preset matching, input parsing, formatting, and
 * slider conversions.
 */
class SpeedSpecTest {

    @Test
    fun presetsMatchExpectedDescendingOrder() {
        assertEquals(
            listOf(4.0f, 3.0f, 2.5f, 2.0f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f, 0.25f),
            SpeedSpec.NORMAL_PRESETS_DESC,
        )
        assertEquals(
            listOf(2.0f, 3.0f, 4.0f),
            SpeedSpec.LONG_PRESS_PRESETS,
        )
    }

    @Test
    fun normalizeSpeed_clampsAndRoundsToTwoDecimals() {
        assertEquals(0.1f, SpeedSpec.normalizeSpeed(0.01f), 0.0001f)
        assertEquals(8.0f, SpeedSpec.normalizeSpeed(12.5f), 0.0001f)
        assertEquals(2.35f, SpeedSpec.normalizeSpeed(2.346f), 0.0001f)
        assertEquals(1.0f, SpeedSpec.normalizeSpeed(Float.NaN), 0.0001f)
        assertEquals(1.0f, SpeedSpec.normalizeSpeed(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(2.0f, SpeedSpec.normalizeLongPressSpeed(-1.0f), 0.0001f)
    }

    @Test
    fun formatSpeedLabel_matchesHostDisplayStyle() {
        assertEquals("0.25X", SpeedSpec.formatSpeedLabel(0.25f))
        assertEquals("0.5X", SpeedSpec.formatSpeedLabel(0.5f))
        assertEquals("0.75X", SpeedSpec.formatSpeedLabel(0.75f))
        assertEquals("1.0X", SpeedSpec.formatSpeedLabel(1.0f))
        assertEquals("1.25X", SpeedSpec.formatSpeedLabel(1.25f))
        assertEquals("2.0X", SpeedSpec.formatSpeedLabel(2.0f))
        assertEquals("2.3X", SpeedSpec.formatSpeedLabel(2.3f))
        assertEquals("2.35X", SpeedSpec.formatSpeedLabel(2.35f))
        assertEquals("8.0X", SpeedSpec.formatSpeedLabel(8.0f))
    }

    @Test
    fun parseSpeedInput_validatesRangeAndSuffixes() {
        assertEquals(3.0f, SpeedSpec.parseSpeedInput("3")!!, 0.0001f)
        assertEquals(2.75f, SpeedSpec.parseSpeedInput(" 2.75x ")!!, 0.0001f)
        assertEquals(4.5f, SpeedSpec.parseSpeedInput("4.5X")!!, 0.0001f)
        assertEquals(1.85f, SpeedSpec.parseSpeedInput("1.85倍")!!, 0.0001f)
        assertEquals(0.1f, SpeedSpec.parseSpeedInput("0.1")!!, 0.0001f)
        assertEquals(8.0f, SpeedSpec.parseSpeedInput("8.0")!!, 0.0001f)

        assertNull(SpeedSpec.parseSpeedInput(""))
        assertNull(SpeedSpec.parseSpeedInput("0.09"))
        assertNull(SpeedSpec.parseSpeedInput("8.01"))
        assertNull(SpeedSpec.parseSpeedInput("-2.0"))
        assertNull(SpeedSpec.parseSpeedInput("abc"))
        assertNull(SpeedSpec.parseSpeedInput("NaN"))
    }

    @Test
    fun sliderMapping_roundTripsBoundariesAndSteps() {
        assertEquals(0, SpeedSpec.speedToSliderProgress(0.1f))
        assertEquals(0.1f, SpeedSpec.sliderProgressToSpeed(0), 0.0001f)

        val progressFor25 = SpeedSpec.speedToSliderProgress(2.5f)
        assertEquals(2.5f, SpeedSpec.sliderProgressToSpeed(progressFor25), 0.0001f)

        assertEquals(SpeedSpec.SLIDER_MAX_PROGRESS, SpeedSpec.speedToSliderProgress(8.0f))
        assertEquals(8.0f, SpeedSpec.sliderProgressToSpeed(SpeedSpec.SLIDER_MAX_PROGRESS), 0.0001f)
    }

    @Test
    fun presetDetectionAndStepping_respectBoundaries() {
        assertTrue(SpeedSpec.isPresetNormalSpeed(0.25f))
        assertTrue(SpeedSpec.isPresetNormalSpeed(4.0f))
        assertFalse(SpeedSpec.isPresetNormalSpeed(2.35f))

        assertTrue(SpeedSpec.isPresetLongPressSpeed(3.0f))
        assertFalse(SpeedSpec.isPresetLongPressSpeed(1.5f))
        assertFalse(SpeedSpec.isPresetLongPressSpeed(2.5f))

        assertEquals(0.1f, SpeedSpec.stepSpeed(0.1f, -0.1f), 0.0001f)
        assertEquals(8.0f, SpeedSpec.stepSpeed(8.0f, 0.1f), 0.0001f)
        assertEquals(2.1f, SpeedSpec.stepSpeed(2.0f, 0.1f), 0.0001f)
    }

    @Test
    fun formatSpeedNumber_andOutOfRangeEdgeCases_areSafe() {
        assertEquals("1.0", SpeedSpec.formatSpeedNumber(Float.NaN))
        assertEquals("2.35", SpeedSpec.formatSpeedNumber(2.346f))
        assertEquals("0.25", SpeedSpec.formatSpeedNumber(0.25f))
        assertEquals("8.0", SpeedSpec.formatSpeedNumber(99.0f))

        assertEquals(2.35f, SpeedSpec.parseSpeedInput("2.346")!!, 0.0001f)
        assertNull(SpeedSpec.parseSpeedInput("Infinity"))
        assertNull(SpeedSpec.parseSpeedInput("0"))
    }
}
