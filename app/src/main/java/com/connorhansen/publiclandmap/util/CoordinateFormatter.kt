package com.connorhansen.publiclandmap.util

import java.util.Locale
import javax.inject.Inject

class CoordinateFormatter @Inject constructor() {

    enum class Format {
        DECIMAL_DEGREES,
        DEGREES_MINUTES,
        DEGREES_MINUTES_SECONDS
    }

    fun format(latitude: Double, longitude: Double, format: Format = Format.DECIMAL_DEGREES): String {
        return when (format) {
            Format.DECIMAL_DEGREES -> formatDecimalDegrees(latitude, longitude)
            Format.DEGREES_MINUTES -> formatDegreesMinutes(latitude, longitude)
            Format.DEGREES_MINUTES_SECONDS -> formatDegreesMinutesSeconds(latitude, longitude)
        }
    }

    private fun formatDecimalDegrees(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.6f° %s, %.6f° %s",
            Math.abs(lat), latDir, Math.abs(lon), lonDir
        )
    }

    // Degrees and minutes are split with integer arithmetic on the printed precision
    // (0.0001 minute), so a value like 59.99996 minutes carries into the next degree
    // instead of printing as 60.0000.
    private fun formatDegreesMinutes(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        val latUnits = Math.round(Math.abs(lat) * 600_000.0)
        val lonUnits = Math.round(Math.abs(lon) * 600_000.0)
        return String.format(
            Locale.US,
            "%d° %.4f' %s, %d° %.4f' %s",
            latUnits / 600_000, (latUnits % 600_000) / 10_000.0, latDir,
            lonUnits / 600_000, (lonUnits % 600_000) / 10_000.0, lonDir
        )
    }

    // Same idea at 0.01 second precision.
    private fun formatDegreesMinutesSeconds(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        val latUnits = Math.round(Math.abs(lat) * 360_000.0)
        val lonUnits = Math.round(Math.abs(lon) * 360_000.0)
        return String.format(
            Locale.US,
            "%d° %d' %.2f\" %s, %d° %d' %.2f\" %s",
            latUnits / 360_000, (latUnits % 360_000) / 6_000, (latUnits % 6_000) / 100.0, latDir,
            lonUnits / 360_000, (lonUnits % 360_000) / 6_000, (lonUnits % 6_000) / 100.0, lonDir
        )
    }
}
