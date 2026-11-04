package com.connorhansen.publiclandmap.api.repository

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.LastUpdatedResponse
import com.connorhansen.publiclandmap.api.model.MetadataEntry
import org.slf4j.LoggerFactory

class MetadataRepository(private val db: DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(MetadataRepository::class.java)

    fun getLastUpdated(): LastUpdatedResponse {
        val entries = mutableListOf<MetadataEntry>()
        var lastUpdated: String? = null

        val sql = """
            SELECT key, value, updated_at::TEXT AS updated_at
            FROM data_metadata
            ORDER BY key
        """.trimIndent()

        db.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val entry = MetadataEntry(
                            key = rs.getString("key"),
                            value = rs.getString("value"),
                            updatedAt = rs.getString("updated_at")
                        )
                        entries.add(entry)
                        if (entry.key == "last_updated") {
                            lastUpdated = entry.value
                        }
                    }
                }
            }
        }

        return LastUpdatedResponse(
            lastUpdated = lastUpdated,
            entries = entries
        )
    }
}
