package com.connorhansen.publiclandmap.api.routes

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.model.ErrorResponse
import com.connorhansen.publiclandmap.api.model.PaginatedResponse
import com.connorhansen.publiclandmap.api.model.Parcel
import com.connorhansen.publiclandmap.api.repository.ParcelRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.parcelRoutes(db: DatabaseConfig) {
    val repository = ParcelRepository(db)

    route("/api/v1/parcels") {
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

            val state = call.request.queryParameters["state"]?.uppercase()
            if (state != null && state !in setOf("WA", "OR")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid state. Allowed values: WA, OR"))
                return@get
            }

            val cursor = call.request.queryParameters["cursor"]

            val (parcels, nextCursor, hasMore) = repository.findByBbox(
                bbox.west, bbox.south, bbox.east, bbox.north, state, cursor
            )

            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respond(PaginatedResponse(
                data = parcels,
                nextCursor = nextCursor,
                hasMore = hasMore,
                count = parcels.size
            ))
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
            if (state != null && state !in setOf("WA", "OR")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid state. Allowed values: WA, OR"))
                return@get
            }

            val cursor = call.request.queryParameters["cursor"]

            val (parcels, nextCursor, hasMore) = repository.search(query, state, cursor)

            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respond(PaginatedResponse(
                data = parcels,
                nextCursor = nextCursor,
                hasMore = hasMore,
                count = parcels.size
            ))
        }

        get("/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing parcel ID"))
                return@get
            }

            val parcel = repository.findById(id)
            if (parcel == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Parcel not found: $id"))
                return@get
            }

            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respond(parcel)
        }
    }
}
