package com.connorhansen.publiclandmap.domain.usecase

import com.connorhansen.publiclandmap.util.DistanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MeasureDistanceUseCaseTest {

    private val useCase = MeasureDistanceUseCase(DistanceCalculator())

    @Before
    fun useUsLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun measureLine_withFewerThanTwoPointsIsZero() {
        val result = useCase.measureLine(listOf(MeasurePoint(46.0, -120.0)))
        assertEquals(0.0, result.totalDistanceMeters, 0.0)
        assertEquals(emptyList<Double>(), result.segmentDistancesMeters)
        assertNull(result.areaSquareMeters)
    }

    @Test
    fun measureLine_sumsSegments() {
        val points = listOf(
            MeasurePoint(46.00, -120.0),
            MeasurePoint(46.01, -120.0),
            MeasurePoint(46.02, -120.0)
        )
        val result = useCase.measureLine(points)
        assertEquals(2, result.segmentDistancesMeters.size)
        assertEquals(2_223.9, result.totalDistanceMeters, 2.0)
        assertEquals(result.segmentDistancesMeters.sum(), result.totalDistanceMeters, 1e-9)
        assertNull(result.areaSquareMeters)
    }

    @Test
    fun measurePolygon_closesTheRingAndComputesArea() {
        val square = listOf(
            MeasurePoint(46.00, -120.00),
            MeasurePoint(46.00, -119.99),
            MeasurePoint(46.01, -119.99),
            MeasurePoint(46.01, -120.00)
        )
        val result = useCase.measurePolygon(square)
        assertEquals(4, result.segmentDistancesMeters.size)
        assertNotNull(result.areaSquareMeters)
        assertEquals(858_820.0, result.areaSquareMeters!!, 2_000.0)
    }

    @Test
    fun measurePolygon_withTwoPointsFallsBackToLine() {
        val result = useCase.measurePolygon(listOf(MeasurePoint(46.0, -120.0), MeasurePoint(46.1, -120.0)))
        assertNull(result.areaSquareMeters)
        assertEquals(1, result.segmentDistancesMeters.size)
    }

    @Test
    fun formatDistance_imperial() {
        assertEquals("100 ft", useCase.formatDistance(30.48, useImperial = true))
        assertEquals("1.00 mi", useCase.formatDistance(1_609.344, useImperial = true))
    }

    @Test
    fun formatDistance_metric() {
        assertEquals("250 m", useCase.formatDistance(250.0, useImperial = false))
        assertEquals("2.50 km", useCase.formatDistance(2_500.0, useImperial = false))
    }

    @Test
    fun formatArea() {
        assertEquals("1.00 acres", useCase.formatArea(4_046.8564224, useImperial = true))
        assertEquals("100 sq ft", useCase.formatArea(9.290304, useImperial = true))
        assertEquals("2.50 ha", useCase.formatArea(25_000.0, useImperial = false))
        assertEquals("500 sq m", useCase.formatArea(500.0, useImperial = false))
    }
}
