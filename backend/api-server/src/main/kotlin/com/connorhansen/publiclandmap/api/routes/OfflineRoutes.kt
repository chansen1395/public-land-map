package com.connorhansen.publiclandmap.api.routes

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.ErrorResponse
import com.connorhansen.publiclandmap.api.model.OfflineRegion
import com.connorhansen.publiclandmap.api.repository.OfflineRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Route.offlineRoutes(db: DatabaseConfig, config: ApplicationConfig) {
    val repository = OfflineRepository(db)
    val tilesDir = config.property("offline.tilesDir").getString()

    route("/api/v1/offline/regions") {
        get {
            val regions = repository.findAll()

            // Return regions without the internal file path
            val sanitized = regions.map { it.copy(tileFilePath = null) }

            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            call.respond(sanitized)
        }

        get("/{id}/metadata") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing region ID"))
                return@get
            }

            val region = repository.findById(id)
            if (region == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Offline region not found: $id"))
                return@get
            }

            // Return metadata without the internal file path
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            call.respond(region.copy(tileFilePath = null))
        }

        get("/{id}/download") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing region ID"))
                return@get
            }

            val region = repository.findById(id)
            if (region == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Offline region not found: $id"))
                return@get
            }

            val filePath = region.tileFilePath
            if (filePath.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Tile file not available for region: $id"))
                return@get
            }

            // Resolve tile file path — support both absolute and relative paths
            val tileFile = if (File(filePath).isAbsolute) {
                File(filePath)
            } else {
                File(tilesDir, filePath)
            }

            // Ensure the resolved path is within the tiles directory (path traversal prevention)
            val canonicalTilesDir = File(tilesDir).canonicalPath
            val canonicalTileFile = tileFile.canonicalPath
            if (!canonicalTileFile.startsWith(canonicalTilesDir + File.separator)) {
                application.log.warn("Path traversal attempt detected: {} resolved to {}", filePath, canonicalTileFile)
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid file path"))
                return@get
            }

            if (!tileFile.exists() || !tileFile.isFile) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Tile file not found on disk"))
                return@get
            }

            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${region.name}.mbtiles\"")
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            call.respondFile(tileFile)
        }
    }
}
