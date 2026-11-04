package com.connorhansen.publiclandmap.util

import com.connorhansen.publiclandmap.util.CoordinateFormatter.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateFormatterTest {

    private val formatter = CoordinateFormatter()

    @Test
    fun decimalDegrees_usesHemisphereLettersInsteadOfSigns() {
        assertEquals(
            "46.850000° N, 121.760000° W",
            formatter.format(46.85, -121.76, Format.DECIMAL_DEGREES)
        )
        assertEquals(
            "33.500000° S, 70.250000° E",
            formatter.format(-33.5, 70.25, Format.DECIMAL_DEGREES)
        )
    }

    @Test
    fun degreesMinutes() {
        assertEquals(
            "46° 51.0000' N, 121° 45.6000' W",
            formatter.format(46.85, -121.76, Format.DEGREES_MINUTES)
        )
    }

    @Test
    fun degreesMinutesSeconds() {
        assertEquals(
            "46° 51' 0.00\" N, 121° 45' 36.00\" W",
            formatter.format(46.85, -121.76, Format.DEGREES_MINUTES_SECONDS)
        )
    }

    @Test
    fun degreesMinutesSeconds_carriesInsteadOfPrintingSixtySeconds() {
        // 27 minutes 59.9976 seconds rounds up to 28 minutes 0.00 seconds
        assertEquals(
            "46° 28' 0.00\" N, 0° 0' 0.00\" E",
            formatter.format(46.466666, 0.0, Format.DEGREES_MINUTES_SECONDS)
        )
    }

    @Test
    fun degreesMinutes_carriesIntoNextDegree() {
        assertEquals(
            "47° 0.0000' N, 0° 0.0000' E",
            formatter.format(46.999999999, 0.0, Format.DEGREES_MINUTES)
        )
    }
}
