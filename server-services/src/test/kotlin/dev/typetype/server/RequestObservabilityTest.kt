package dev.typetype.server

import dev.typetype.server.models.ErrorResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestObservabilityTest {
    @Test
    fun `request id is echoed and included in error body`() = testApplication {
        application {
            installRequestObservability()
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing {
                get("/bad") {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request", "bad_request"))
                }
            }
        }

        val response = client.get("/bad") { header(REQUEST_ID_HEADER, "request-test-123") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("request-test-123", response.headers[REQUEST_ID_HEADER])
        val body = response.bodyAsText()
        assertTrue(body.contains("\"code\":\"bad_request\""))
        assertTrue(body.contains("\"requestId\":\"request-test-123\""))
    }

    @Test
    fun `invalid request id is replaced`() = testApplication {
        application {
            installRequestObservability()
            routing { get("/ok") { call.respond(HttpStatusCode.NoContent) } }
        }

        val response = client.get("/ok") { header(REQUEST_ID_HEADER, "bad") }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertNotNull(response.headers[REQUEST_ID_HEADER])
        assertNotEquals("bad", response.headers[REQUEST_ID_HEADER])
    }
}
