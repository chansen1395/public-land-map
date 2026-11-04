package com.connorhansen.publiclandmap.api.repository

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.Parcel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

class ParcelRepository(private val db: DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(ParcelRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PAGE_SIZE = 500
        private val VALID_STATES = setOf("WA", "OR")

        // Parcel search matches the parcel number only: not owner names, and not addresses
        internal const val SEARCH_FILTER = "parcel_number ILIKE ?"
    }

    fun findByBbox(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        state: String?,
        cursor: String?
    ): Triple<List<Parcel>, String?, Boolean> {
        val parcels = mutableListOf<Parcel>()

        val sql = buildString {
            append("""
                SELECT id, parcel_number, owner_name,
                       county, state, acreage, land_use_code, land_use_desc,
                       centroid_lat, centroid_lng, ST_AsGeoJSON(geom) AS geojson, last_updated
                FROM parcels
                WHERE ST_Intersects(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))
            """.trimIndent())
            if (state != null) append(" AND state = ?")
            if (cursor != null) append(" AND id > ?")
            append(" ORDER BY id ASC LIMIT ?")
        }

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                var idx = 1
                stmt.setDouble(idx++, west)
                stmt.setDouble(idx++, south)
                stmt.setDouble(idx++, east)
                stmt.setDouble(idx++, north)
                if (state != null) stmt.setString(idx++, state)
                if (cursor != null) stmt.setString(idx++, cursor)
                stmt.setInt(idx, PAGE_SIZE + 1)

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        parcels.add(mapRowToParcel(rs))
                    }
                }
            }
        }

        val hasMore = parcels.size > PAGE_SIZE
        if (hasMore) parcels.removeAt(parcels.size - 1)
        val nextCursor = if (hasMore) parcels.lastOrNull()?.id else null

        return Triple(parcels, nextCursor, hasMore)
    }

    fun findById(id: String): Parcel? {
        val sql = """
            SELECT id, parcel_number, owner_name,
                   county, state, acreage, land_use_code, land_use_desc,
                   centroid_lat, centroid_lng, ST_AsGeoJSON(geom) AS geojson, last_updated
            FROM parcels
            WHERE id = ?
        """.trimIndent()

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapRowToParcel(rs) else null
                }
            }
        }
    }

    fun search(
        query: String,
        state: String?,
        cursor: String?
    ): Triple<List<Parcel>, String?, Boolean> {
        val parcels = mutableListOf<Parcel>()
        val pattern = "%$query%"

        val sql = buildString {
            append("""
                SELECT id, parcel_number, owner_name,
                       county, state, acreage, land_use_code, land_use_desc,
                       centroid_lat, centroid_lng, ST_AsGeoJSON(geom) AS geojson, last_updated
                FROM parcels
                WHERE ($SEARCH_FILTER)
            """.trimIndent())
            if (state != null) append(" AND state = ?")
            if (cursor != null) append(" AND id > ?")
            append(" ORDER BY id ASC LIMIT ?")
        }

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                var idx = 1
                stmt.setString(idx++, pattern)
                if (state != null) stmt.setString(idx++, state)
                if (cursor != null) stmt.setString(idx++, cursor)
                stmt.setInt(idx, PAGE_SIZE + 1)

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        parcels.add(mapRowToParcel(rs))
                    }
                }
            }
        }

        val hasMore = parcels.size > PAGE_SIZE
        if (hasMore) parcels.removeAt(parcels.size - 1)
        val nextCursor = if (hasMore) parcels.lastOrNull()?.id else null

        return Triple(parcels, nextCursor, hasMore)
    }

    private fun mapRowToParcel(rs: java.sql.ResultSet): Parcel {
        val geojsonStr = rs.getString("geojson")
        val geometry: JsonElement? = if (geojsonStr != null) {
            json.parseToJsonElement(geojsonStr)
        } else null

        return Parcel(
            id = rs.getString("id"),
            parcelNumber = rs.getString("parcel_number"),
            ownerName = rs.getString("owner_name"),
            county = rs.getString("county"),
            state = rs.getString("state"),
            acreage = rs.getFloat("acreage").takeIf { !rs.wasNull() },
            landUseCode = rs.getString("land_use_code"),
            landUseDesc = rs.getString("land_use_desc"),
            centroidLat = rs.getDouble("centroid_lat"),
            centroidLng = rs.getDouble("centroid_lng"),
            geometry = geometry,
            lastUpdated = rs.getLong("last_updated").takeIf { !rs.wasNull() }
        )
    }
}
