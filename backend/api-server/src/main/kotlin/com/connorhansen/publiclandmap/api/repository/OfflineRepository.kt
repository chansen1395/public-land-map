package com.connorhansen.publiclandmap.api.repository

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.OfflineRegion
import org.slf4j.LoggerFactory

class OfflineRepository(private val db: DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(OfflineRepository::class.java)

    fun findAll(): List<OfflineRegion> {
        val results = mutableListOf<OfflineRegion>()

        val sql = """
            SELECT id, name, state, county, min_lat, min_lng, max_lat, max_lng,
                   min_zoom, max_zoom, size_bytes, tile_file_path
            FROM offline_regions
            ORDER BY state, name
        """.trimIndent()

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(mapRow(rs))
                    }
                }
            }
        }

        return results
    }

    fun findById(id: String): OfflineRegion? {
        val sql = """
            SELECT id, name, state, county, min_lat, min_lng, max_lat, max_lng,
                   min_zoom, max_zoom, size_bytes, tile_file_path
            FROM offline_regions
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

    private fun mapRow(rs: java.sql.ResultSet): OfflineRegion {
        return OfflineRegion(
            id = rs.getString("id"),
            name = rs.getString("name"),
            state = rs.getString("state"),
            county = rs.getString("county"),
            minLat = rs.getDouble("min_lat"),
            minLng = rs.getDouble("min_lng"),
            maxLat = rs.getDouble("max_lat"),
            maxLng = rs.getDouble("max_lng"),
            minZoom = rs.getInt("min_zoom"),
            maxZoom = rs.getInt("max_zoom"),
            sizeBytes = rs.getLong("size_bytes").takeIf { !rs.wasNull() },
            tileFilePath = rs.getString("tile_file_path")
        )
    }
}
