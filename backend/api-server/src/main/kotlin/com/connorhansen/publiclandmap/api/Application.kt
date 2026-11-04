package com.connorhansen.publiclandmap.api

import com.connorhansen.publiclandmap.api.config.DatabaseConfig
import com.connorhansen.publiclandmap.api.routes.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val dbConfig = DatabaseConfig.init(environment.config)
    environment.monitor.subscribe(ApplicationStopped) { dbConfig.close() }

    configurePlugins()
    configureRouting(dbConfig)
}

private fun Application.configurePlugins() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }

    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Accept)
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            isLenient = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            val duration = call.processingTimeMillis()
            "$method $uri -> $status (${duration}ms)"
        }
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to (cause.message ?: "Resource not found")))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal server error")
            )
        }
    }
}

private fun Application.configureRouting(dbConfig: DatabaseConfig) {
    val config = environment.config
    routing {
        healthRoutes(dbConfig)
        parcelRoutes(dbConfig)
        publicLandRoutes(dbConfig)
        regulationRoutes(dbConfig)
        offlineRoutes(dbConfig, config)
        metadataRoutes(dbConfig)
    }
}
