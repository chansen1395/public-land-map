package com.connorhansen.publiclandmap.api.routes

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val database: String
)

fun Route.healthRoutes(db: DatabaseConfig) {
    get("/health") {
        val dbHealthy = db.isHealthy()
        val status = if (dbHealthy) "healthy" else "degraded"
        val httpStatus = if (dbHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

        call.respond(httpStatus, HealthResponse(
            status = status,
            database = if (dbHealthy) "connected" else "disconnected"
        ))
    }
}
