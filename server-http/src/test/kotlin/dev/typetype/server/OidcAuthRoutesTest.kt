package dev.typetype.server

import dev.typetype.server.routes.oidcAuthRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.OidcAuthService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OidcAuthRoutesTest {
    private val adminSettings = AdminSettingsService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
    }

    @Test
    fun `OIDC status reports disabled configuration`() = testApplication {
        val oidc = OidcAuthService(null, "test-secret", AuthService("test-secret"))
        application {
            install(ContentNegotiation) { json() }
            routing { oidcAuthRoutes(oidc, adminSettings) }
        }
        val response = client.get("/auth/oidc/status")
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"enabled\":false"))
        assertTrue(body.contains("\"localLoginEnabled\":true"))
        assertTrue(body.contains("\"autoRedirect\":false"))
    }

    @Test
    fun `OIDC start returns 400 when disabled`() = testApplication {
        val oidc = OidcAuthService(null, "test-secret", AuthService("test-secret"))
        application {
            install(ContentNegotiation) { json() }
            routing { oidcAuthRoutes(oidc, adminSettings) }
        }
        val response = client.get("/auth/oidc/start?redirectUri=http://localhost/callback")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
