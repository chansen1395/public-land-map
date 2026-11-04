package com.connorhansen.publiclandmap.api.repository

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.Regulation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

class RegulationRepository(private val db: DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(RegulationRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PAGE_SIZE = 500
        val VALID_TYPES = setOf(
            "fire_restriction", "closure", "ohv", "camping", "motor_vehicle"
        )
    }

    fun findByBbox(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        type: String?,
        cursor: Int?
    ): Triple<List<Regulation>, Int?, Boolean> {
        val results = mutableListOf<Regulation>()

        val sql = buildString {
            append("""
                SELECT id, area_id, area_name, regulation_type, managing_agency, state,
                       title, description, restriction_level,
                       start_date::TEXT AS start_date, end_date::TEXT AS end_date,
                       ST_AsGeoJSON(geom) AS geojson, source_url, last_updated
                FROM regulations
                WHERE ST_Intersects(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))
            """.trimIndent())
            if (type != null) append(" AND regulation_type = ?")
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
                if (type != null) stmt.setString(idx++, type)
                if (cursor != null) stmt.setInt(idx++, cursor)
                stmt.setInt(idx, PAGE_SIZE + 1)

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(mapRow(rs))
                    }
                }
            }
        }

        val hasMore = results.size > PAGE_SIZE
        if (hasMore) results.removeAt(results.size - 1)
        val nextCursor = if (hasMore) results.lastOrNull()?.id else null

        return Triple(results, nextCursor, hasMore)
    }

    fun findByAreaId(areaId: String): List<Regulation> {
        val results = mutableListOf<Regulation>()

        val sql = """
            SELECT id, area_id, area_name, regulation_type, managing_agency, state,
                   title, description, restriction_level,
                   start_date::TEXT AS start_date, end_date::TEXT AS end_date,
                   ST_AsGeoJSON(geom) AS geojson, source_url, last_updated
            FROM regulations
            WHERE area_id = ?
            ORDER BY id ASC
        """.trimIndent()

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, areaId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(mapRow(rs))
                    }
                }
            }
        }

        return results
    }

    private fun mapRow(rs: java.sql.ResultSet): Regulation {
        val geojsonStr = rs.getString("geojson")
        val geometry: JsonElement? = if (geojsonStr != null) {
            json.parseToJsonElement(geojsonStr)
        } else null

        return Regulation(
            id = rs.getInt("id"),
            areaId = rs.getString("area_id"),
            areaName = rs.getString("area_name"),
            regulationType = rs.getString("regulation_type"),
            managingAgency = rs.getString("managing_agency"),
            state = rs.getString("state"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            restrictionLevel = rs.getString("restriction_level"),
            startDate = rs.getString("start_date"),
            endDate = rs.getString("end_date"),
            geometry = geometry,
            sourceUrl = rs.getString("source_url"),
            lastUpdated = rs.getLong("last_updated").takeIf { !rs.wasNull() }
        )
    }
}
