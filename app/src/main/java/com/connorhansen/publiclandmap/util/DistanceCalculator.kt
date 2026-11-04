package com.connorhansen.publiclandmap.util

import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DistanceCalculator @Inject constructor() {

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val METERS_PER_FOOT = 0.3048
        private const val METERS_PER_MILE = 1_609.344
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun metersToFeet(meters: Double): Double {
        return meters / METERS_PER_FOOT
    }

    fun metersToMiles(meters: Double): Double {
        return meters / METERS_PER_MILE
    }

    fun feetToMeters(feet: Double): Double {
        return feet * METERS_PER_FOOT
    }

    fun milesToMeters(miles: Double): Double {
        return miles * METERS_PER_MILE
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
}
