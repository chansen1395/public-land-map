package com.connorhansen.publiclandmap.domain.usecase

import com.connorhansen.publiclandmap.util.DistanceCalculator
import javax.inject.Inject
import kotlin.math.abs

data class MeasurePoint(
    val latitude: Double,
    val longitude: Double
)

data class MeasureResult(
    val points: List<MeasurePoint>,
    val totalDistanceMeters: Double,
    val segmentDistancesMeters: List<Double>,
    val areaSquareMeters: Double?
)

class MeasureDistanceUseCase @Inject constructor(
    private val distanceCalculator: DistanceCalculator
) {

    fun measureLine(points: List<MeasurePoint>): MeasureResult {
        if (points.size < 2) {
            return MeasureResult(
                points = points,
                totalDistanceMeters = 0.0,
                segmentDistancesMeters = emptyList(),
                areaSquareMeters = null
            )
        }

        val segmentDistances = mutableListOf<Double>()
        var totalDistance = 0.0

        for (i in 1 until points.size) {
            val distance = distanceCalculator.haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
            segmentDistances.add(distance)
            totalDistance += distance
        }

        return MeasureResult(
            points = points,
            totalDistanceMeters = totalDistance,
            segmentDistancesMeters = segmentDistances,
            areaSquareMeters = null
        )
    }

    fun measurePolygon(points: List<MeasurePoint>): MeasureResult {
        if (points.size < 3) {
            return measureLine(points)
        }

        val segmentDistances = mutableListOf<Double>()
        var perimeter = 0.0

        for (i in 1 until points.size) {
            val distance = distanceCalculator.haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
            segmentDistances.add(distance)
            perimeter += distance
        }

        val closingDistance = distanceCalculator.haversineMeters(
            points.last().latitude, points.last().longitude,
            points.first().latitude, points.first().longitude
        )
        segmentDistances.add(closingDistance)
        perimeter += closingDistance

        val area = calculateSphericalExcessArea(points)

        return MeasureResult(
            points = points,
            totalDistanceMeters = perimeter,
            segmentDistancesMeters = segmentDistances,
            areaSquareMeters = area
        )
    }

    private fun calculateSphericalExcessArea(points: List<MeasurePoint>): Double {
        val earthRadiusMeters = 6_371_000.0
        var sum = 0.0
        val n = points.size

        for (i in 0 until n) {
            val j = (i + 1) % n
            val lat1 = Math.toRadians(points[i].latitude)
            val lon1 = Math.toRadians(points[i].longitude)
            val lat2 = Math.toRadians(points[j].latitude)
            val lon2 = Math.toRadians(points[j].longitude)

            sum += (lon2 - lon1) * (2 + Math.sin(lat1) + Math.sin(lat2))
        }

        val areaRadians = abs(sum / 2.0)
        return areaRadians * earthRadiusMeters * earthRadiusMeters
    }

    fun formatDistance(meters: Double, useImperial: Boolean = true): String {
        return if (useImperial) {
            val miles = distanceCalculator.metersToMiles(meters)
            if (miles < 0.1) {
                val feet = distanceCalculator.metersToFeet(meters)
                "%.0f ft".format(feet)
            } else {
                "%.2f mi".format(miles)
            }
        } else {
            if (meters < 1000) {
                "%.0f m".format(meters)
            } else {
                "%.2f km".format(meters / 1000.0)
            }
        }
    }

    fun formatArea(squareMeters: Double, useImperial: Boolean = true): String {
        return if (useImperial) {
            val acres = squareMeters / 4046.8564224
            if (acres < 1.0) {
                val sqFeet = squareMeters * 10.7639104
                "%.0f sq ft".format(sqFeet)
            } else {
                "%.2f acres".format(acres)
            }
        } else {
            if (squareMeters < 10000) {
                "%.0f sq m".format(squareMeters)
            } else {
                val hectares = squareMeters / 10000.0
                "%.2f ha".format(hectares)
            }
        }
    }
}
