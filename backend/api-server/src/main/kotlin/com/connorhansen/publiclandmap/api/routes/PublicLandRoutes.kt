package com.connorhansen.publiclandmap.api.routes

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.ErrorResponse
import com.connorhansen.publiclandmap.api.model.PaginatedResponse
import com.connorhansen.publiclandmap.api.model.PublicLand
import com.connorhansen.publiclandmap.api.model.Regulation
import com.connorhansen.publiclandmap.api.repository.PublicLandRepository
import com.connorhansen.publiclandmap.api.repository.RegulationRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.publicLandRoutes(db: DatabaseConfig) {
    val repository = PublicLandRepository(db)
    val regulationRepository = RegulationRepository(db)

    route("/api/v1/public-lands") {
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

            val cursor = call.request.queryParameters["cursor"]

            val (lands, nextCursor, hasMore) = repository.findByBbox(
                bbox.west, bbox.south, bbox.east, bbox.north, cursor
            )

            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respond(PaginatedResponse(
                data = lands,
                nextCursor = nextCursor,
                hasMore = hasMore,
                count = lands.size
            ))
        }

        get("/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing public land ID"))
                return@get
            }

            val land = repository.findById(id)
            if (land == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Public land not found: $id"))
                return@get
            }

            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respond(land)
        }

        get("/search") {
            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing required parameter: q"))
                return@get
            }

            if (query.length < 2) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Search query must be at least 2 characters"))
                return@get
            }

            if (query.length > 200) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Search query must be at most 200 characters"))
                return@get
            }

            val state = call.request.queryParameters["state"]?.uppercase()
            val cursor = call.request.queryParameters["cursor"]

            val (lands, nextCursor, hasMore) = repository.search(query, state, cursor)

            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respond(PaginatedResponse(
                data = lands,
                nextCursor = nextCursor,
                hasMore = hasMore,
                count = lands.size
            ))
        }

        get("/{id}/regulations") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing public land ID"))
                return@get
            }

            // Verify the public land exists first
            val land = repository.findById(id)
            if (land == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Public land not found: $id"))
                return@get
            }

            val regulations = regulationRepository.findByAreaId(id)

            call.response.header(HttpHeaders.CacheControl, "public, max-age=21600")
            call.respond(regulations)
        }
    }
}
