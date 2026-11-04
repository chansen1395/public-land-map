package com.connorhansen.publiclandmap.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Route tests that run without PostGIS. The connection pool points at a closed port, so only
 * requests that are rejected before any query runs can be checked here: parameter validation
 * and the health endpoint. The SQL in the repositories needs a real PostGIS database and is
 * not covered by automated tests.
 */
class ApplicationTest {

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:postgresql://127.0.0.1:1/none",
                "database.user" to "test",
                "database.password" to "test",
                "database.maxPoolSize" to "2",
                "database.connectionTimeoutMs" to "1000",
                "offline.tilesDir" to "build/tmp/offline-test"
            )
        }
        application { module() }
        block()
    }

    @Test
    fun healthReportsDegradedWithoutADatabase() = withApp {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertContains(response.bodyAsText(), "degraded")
        assertContains(response.bodyAsText(), "disconnected")
    }

    @Test
    fun publicLandsRequiresBbox() = withApp {
        val response = client.get("/api/v1/public-lands")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Missing required parameter: bbox")
    }

    @Test
    fun publicLandsRejectsMalformedBbox() = withApp {
        val response = client.get("/api/v1/public-lands?bbox=1,2,3")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Invalid bbox format")
    }

    @Test
    fun publicLandsSearchChecksQueryLength() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/public-lands/search").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/public-lands/search?q=a").status)
        val tooLong = "x".repeat(201)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/public-lands/search?q=$tooLong").status)
    }

    @Test
    fun parcelsRejectUnknownState() = withApp {
        val response = client.get("/api/v1/parcels?bbox=-122.5,47.0,-122.0,47.5&state=ID")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Allowed values: WA, OR")
    }

    @Test
    fun parcelSearchRejectsUnknownState() = withApp {
        val response = client.get("/api/v1/parcels/search?q=main&state=CA")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun regulationsRejectUnknownTypeAndBadCursor() = withApp {
        val badType = client.get("/api/v1/regulations?bbox=-122.5,47.0,-122.0,47.5&type=bogus")
        assertEquals(HttpStatusCode.BadRequest, badType.status)
        assertContains(badType.bodyAsText(), "Invalid regulation type")

        val badCursor = client.get("/api/v1/regulations?bbox=-122.5,47.0,-122.0,47.5&cursor=abc")
        assertEquals(HttpStatusCode.BadRequest, badCursor.status)
        assertContains(badCursor.bodyAsText(), "Invalid cursor value")
    }

    @Test
    fun unknownRouteIsNotFound() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/nothing-here").status)
    }
}
