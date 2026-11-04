package com.connorhansen.publiclandmap.api.routes

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.repository.MetadataRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.metadataRoutes(db: DatabaseConfig) {
    val repository = MetadataRepository(db)

    route("/api/v1/metadata") {
        get("/last-updated") {
            val metadata = repository.getLastUpdated()

            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respond(metadata)
        }
    }
}
