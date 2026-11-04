package com.connorhansen.publiclandmap.api.routes

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.ErrorResponse
import com.connorhansen.publiclandmap.api.model.PaginatedResponse
import com.connorhansen.publiclandmap.api.model.Regulation
import com.connorhansen.publiclandmap.api.repository.RegulationRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.regulationRoutes(db: DatabaseConfig) {
    val repository = RegulationRepository(db)

    route("/api/v1/regulations") {
        get {
            val bboxParam = call.request.queryParameters["bbox"]
            if (bboxParam == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing required parameter: bbox"))
                return@get
            }

            val bbox = parseBbox(bboxParam)
            if (bbox == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                    "Invalid bbox format. Expected: west,south,east,north (e.g., -122.5,47.0,-122.0,47.5)"
                ))
                return@get
            }

            val type = call.request.queryParameters["type"]
            if (type != null && type !in RegulationRepository.VALID_TYPES) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                    "Invalid regulation type. Allowed values: ${RegulationRepository.VALID_TYPES.joinToString(", ")}"
                ))
                return@get
            }

            val cursorParam = call.request.queryParameters["cursor"]
            val cursor = cursorParam?.toIntOrNull()
            if (cursorParam != null && cursor == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid cursor value"))
                return@get
            }

            val (regulations, nextCursor, hasMore) = repository.findByBbox(
                bbox.west, bbox.south, bbox.east, bbox.north, type, cursor
            )

            call.response.header(HttpHeaders.CacheControl, "public, max-age=21600")
            call.respond(PaginatedResponse(
                data = regulations,
                nextCursor = nextCursor?.toString(),
                hasMore = hasMore,
                count = regulations.size
            ))
        }
    }
}
