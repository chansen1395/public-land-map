package com.connorhansen.publiclandmap.api.repository

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.PublicLand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

class PublicLandRepository(private val db: DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(PublicLandRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PAGE_SIZE = 500
        private val AGENCY_CATEGORY_SQL = """
            CASE
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Bureau of Land Management%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%BLM%' THEN 'BLM'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Forest Service%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%USFS%' THEN 'USFS'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%National Park%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%NPS%' THEN 'National Park Service'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Fish and Wildlife%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%USFWS%' THEN 'Fish & Wildlife Service'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Army Corps%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Corps of Engineers%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Bureau of Reclamation%' THEN 'Army Corps / Reclamation'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Department of Energy%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Dept of Energy%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Defense%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Military%' THEN 'DOE / Defense'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Department of Natural Resources%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%DNR%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%State Forest%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Common School%' THEN 'DNR / State Trust'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%State Park%' THEN 'State Parks'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%State Fish%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%State Wildlife%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%WDFW%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%ODFW%' THEN 'State Wildlife'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%County%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%City%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Municip%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Local%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Regional%' THEN 'Local Government'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Tribal%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%American Indian%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Reservation%' THEN 'Tribal'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Wilderness%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Conservation%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Natural Area%' OR CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%NRCA%' THEN 'Wilderness / Conservation'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%Federal%' THEN 'Other Federal'
                WHEN CONCAT_WS(' ', managing_agency, designation_type, public_access, unit_name) ILIKE '%State%' THEN 'Other State'
                WHEN public_access IN ('Open', 'Restricted') THEN 'Open Access'
                ELSE 'Other Public'
            END
        """.trimIndent()
    }

    fun findByBbox(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        cursor: String?
    ): Triple<List<PublicLand>, String?, Boolean> {
        val results = mutableListOf<PublicLand>()

        val sql = buildString {
            append("""
                  SELECT id, unit_name, managing_agency, $AGENCY_CATEGORY_SQL AS agency_category, designation_type, public_access,
                       state, gis_acres, centroid_lat, centroid_lng,
                       ST_AsGeoJSON(geom) AS geojson, last_updated
                FROM public_lands
                WHERE ST_Intersects(geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))
            """.trimIndent())
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
                if (cursor != null) stmt.setString(idx++, cursor)
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

    fun findById(id: String): PublicLand? {
        val sql = """
                 SELECT id, unit_name, managing_agency, $AGENCY_CATEGORY_SQL AS agency_category, designation_type, public_access,
                   state, gis_acres, centroid_lat, centroid_lng,
                   ST_AsGeoJSON(geom) AS geojson, last_updated
            FROM public_lands
            WHERE id = ?
        """.trimIndent()

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    fun search(
        query: String,
        state: String?,
        cursor: String?
    ): Triple<List<PublicLand>, String?, Boolean> {
        val results = mutableListOf<PublicLand>()
        val pattern = "%$query%"

        val sql = buildString {
            append("""
                  SELECT id, unit_name, managing_agency, $AGENCY_CATEGORY_SQL AS agency_category, designation_type, public_access,
                       state, gis_acres, centroid_lat, centroid_lng,
                       ST_AsGeoJSON(geom) AS geojson, last_updated
                FROM public_lands
                WHERE (unit_name ILIKE ? OR managing_agency ILIKE ? OR designation_type ILIKE ?)
            """.trimIndent())
            if (state != null) append(" AND state = ?")
            if (cursor != null) append(" AND id > ?")
            append(" ORDER BY id ASC LIMIT ?")
        }

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                var idx = 1
                stmt.setString(idx++, pattern)
                stmt.setString(idx++, pattern)
                stmt.setString(idx++, pattern)
                if (state != null) stmt.setString(idx++, state)
                if (cursor != null) stmt.setString(idx++, cursor)
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

    private fun mapRow(rs: java.sql.ResultSet): PublicLand {
        val geojsonStr = rs.getString("geojson")
        val geometry: JsonElement? = if (geojsonStr != null) {
            json.parseToJsonElement(geojsonStr)
        } else null

        return PublicLand(
            id = rs.getString("id"),
            unitName = rs.getString("unit_name"),
            managingAgency = rs.getString("managing_agency"),
            agencyCategory = rs.getString("agency_category"),
            designationType = rs.getString("designation_type"),
            publicAccess = rs.getString("public_access"),
            state = rs.getString("state"),
            gisAcres = rs.getFloat("gis_acres").takeIf { !rs.wasNull() },
            centroidLat = rs.getDouble("centroid_lat").takeIf { !rs.wasNull() },
            centroidLng = rs.getDouble("centroid_lng").takeIf { !rs.wasNull() },
            geometry = geometry,
            lastUpdated = rs.getLong("last_updated").takeIf { !rs.wasNull() }
        )
    }
}
