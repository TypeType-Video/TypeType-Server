package dev.typetype.server

import dev.typetype.server.models.DeepHealthResponse
import dev.typetype.server.routes.internalObservabilityRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InternalObservabilityRoutesTest {
    @Test
    fun `internal health requires token`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { testRoutes() }
        }

        val response = client.get("/internal/health/deep")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `internal health returns checks with token`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { testRoutes() }
        }

        val response = client.get("/internal/health/deep") { header("X-Internal-Token", "secret") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok","checks":{"postgres":"ok"}}""", response.bodyAsText())
    }

    @Test
    fun `internal metrics returns key value text with token`() = testApplication {
        application { routing { testRoutes() } }

        val response = client.get("/internal/metrics") { header("X-Internal-Token", "secret") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("requests.total=1"))
    }

    private fun io.ktor.server.routing.Route.testRoutes() = internalObservabilityRoutes(
        healthProvider = { DeepHealthResponse("ok", mapOf("postgres" to "ok")) },
        metricsProvider = { "requests.total=1" },
        tokenProvider = { "secret" },
    )
}
