package com.connorhansen.publiclandmap.api.routes

/**
 * Parsed bounding box coordinates.
 */
data class BBox(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double
)

/**
 * Parse a bbox query parameter string "west,south,east,north" into a [BBox].
 * Returns null if the format is invalid.
 *
 * Validates:
 * - Exactly 4 comma-separated values
 * - All values are valid doubles
 * - Longitude: -180 to 180
 * - Latitude: -90 to 90
 * - west < east, south < north
 */
fun parseBbox(raw: String): BBox? {
    val parts = raw.split(",")
    if (parts.size != 4) return null

    val values = parts.map { it.trim().toDoubleOrNull() ?: return null }

    val west = values[0]
    val south = values[1]
    val east = values[2]
    val north = values[3]

    // Validate ranges
    if (west < -180.0 || west > 180.0) return null
    if (east < -180.0 || east > 180.0) return null
    if (south < -90.0 || south > 90.0) return null
    if (north < -90.0 || north > 90.0) return null

    // Validate ordering
    if (south >= north) return null
    if (west >= east) return null

    return BBox(west, south, east, north)
}
