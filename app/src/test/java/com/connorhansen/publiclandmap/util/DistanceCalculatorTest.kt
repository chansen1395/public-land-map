package com.connorhansen.publiclandmap.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceCalculatorTest {

    private val calculator = DistanceCalculator()

    @Test
    fun haversine_samePointIsZero() {
        assertEquals(0.0, calculator.haversineMeters(46.85, -121.76, 46.85, -121.76), 0.001)
    }

    @Test
    fun haversine_seattleToPortlandIsAbout234Km() {
        val meters = calculator.haversineMeters(47.6062, -122.3321, 45.5152, -122.6784)
        assertEquals(234_010.0, meters, 50.0)
    }

    @Test
    fun haversine_oneHundredthOfADegreeOfLatitudeIsAbout1112Meters() {
        val meters = calculator.haversineMeters(46.0, -120.0, 46.01, -120.0)
        assertEquals(1_111.9, meters, 1.0)
    }

    @Test
    fun unitConversionsRoundTrip() {
        assertEquals(1_000.0, calculator.feetToMeters(calculator.metersToFeet(1_000.0)), 1e-9)
        assertEquals(5.0, calculator.metersToMiles(calculator.milesToMeters(5.0)), 1e-9)
        assertEquals(5_280.0, calculator.metersToFeet(calculator.milesToMeters(1.0)), 1e-6)
    }

    @Test
    fun bearing_cardinalDirections() {
        assertEquals(0.0, calculator.bearing(46.0, -120.0, 47.0, -120.0), 0.001)
        assertEquals(180.0, calculator.bearing(47.0, -120.0, 46.0, -120.0), 0.001)
        assertEquals(90.0, calculator.bearing(0.0, 0.0, 0.0, 1.0), 0.001)
        assertEquals(270.0, calculator.bearing(0.0, 1.0, 0.0, 0.0), 0.001)
    }
}
