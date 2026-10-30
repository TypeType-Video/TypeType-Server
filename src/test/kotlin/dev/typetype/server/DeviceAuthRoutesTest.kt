package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.routes.deviceAuthRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.device.DeviceAuthorizationService
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceAuthRoutesTest {
    private val adminSettings = AdminSettingsService()
    private val auth = AuthService("test-secret")
    private var clock = 5_000_000L
    private val service = DeviceAuthorizationService(
        cache = FakeCacheService(),
        secret = "test-secret",
        enabled = { adminSettings.get().deviceLoginEnabled },
        ownerOf = { null },
        now = { clock },
    )

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        AdminSettingsService.clearCache()
    }

    private fun ApplicationTestBuilder.install() = application {
        install(ContentNegotiation) { json() }
        routing { deviceAuthRoutes(service, auth) }
    }

    private suspend fun HttpClient.json(path: String, body: String, token: String? = null): HttpResponse = post(path) {
        contentType(ContentType.Application.Json)
        token?.let(::bearerAuth)
        setBody(body)
    }

    private fun HttpResponse.field(name: String): String = runBlocking {
        Json.parseToJsonElement(bodyAsText()).jsonObject.getValue(name).jsonPrimitive.content
    }

    private fun accountToken(): String = runBlocking {
        auth.register("tv-owner@test.local", "secret", "Owner")
        requireNotNull(auth.login("tv-owner@test.local", "secret")).accessToken
    }

    @Test
    fun `phone approval signs the device in like a password login`() = testApplication {
        install()
        val token = accountToken()
        val started = client.json("/auth/device/start", """{"deviceName":"TypeType TV · Box"}""")
        assertEquals(HttpStatusCode.OK, started.status)
        assertEquals("no-store", started.headers[HttpHeaders.CacheControl])
        val deviceCode = started.field("deviceCode")
        val userCode = started.field("userCode")

        val pending = client.json("/auth/device/token", """{"deviceCode":"$deviceCode"}""")
        assertEquals("authorization_pending", pending.field("code"))
        val lookup = client.get("/auth/device/lookup?code=$userCode") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, lookup.status)
        assertEquals("TypeType TV · Box", lookup.field("deviceName"))
        val approve = client.json("/auth/device/approve", """{"userCode":"$userCode"}""", token)
        assertEquals(HttpStatusCode.NoContent, approve.status)

        clock += 2_000
        val signedIn = client.json("/auth/device/token", """{"deviceCode":"$deviceCode"}""")
        assertEquals(HttpStatusCode.OK, signedIn.status)
        assertTrue(auth.verify(signedIn.field("accessToken")) != null)
        assertTrue(signedIn.headers.getAll(HttpHeaders.SetCookie).orEmpty().any { it.startsWith("refresh_token=") })

        clock += 2_000
        val again = client.json("/auth/device/token", """{"deviceCode":"$deviceCode"}""")
        assertEquals(HttpStatusCode.BadRequest, again.status)
        assertEquals("expired_token", again.field("code"))
    }

    @Test
    fun `phone refusal is reported to the device`() = testApplication {
        install()
        val token = accountToken()
        val started = client.json("/auth/device/start", "{}")
        val deny = client.json("/auth/device/deny", """{"userCode":"${started.field("userCode")}"}""", token)
        assertEquals(HttpStatusCode.NoContent, deny.status)
        val refused = client.json("/auth/device/token", """{"deviceCode":"${started.field("deviceCode")}"}""")
        assertEquals("access_denied", refused.field("code"))
        assertFalse(refused.headers.contains(HttpHeaders.SetCookie))
    }

    @Test
    fun `approval needs a signed in account and refuses guests`() = testApplication {
        install()
        val userCode = client.json("/auth/device/start", "{}").field("userCode")
        val anonymous = client.json("/auth/device/approve", """{"userCode":"$userCode"}""")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        val guest = client.json("/auth/device/approve", """{"userCode":"$userCode"}""", auth.guestLogin())
        assertEquals(HttpStatusCode.Forbidden, guest.status)
        assertEquals("guest_forbidden", guest.field("code"))
    }

    @Test
    fun `admin can turn device login off`() = testApplication {
        install()
        adminSettings.upsert(AdminSettingsItem(deviceLoginEnabled = false))
        AdminSettingsService.clearCache()
        assertFalse(adminSettings.get().deviceLoginEnabled)
        val started = client.json("/auth/device/start", "{}")
        assertEquals(HttpStatusCode.Forbidden, started.status)
        assertEquals("device_login_disabled", started.field("code"))
    }

    @Test
    fun `unknown codes answer not found`() = testApplication {
        install()
        val token = accountToken()
        val lookup = client.get("/auth/device/lookup?code=BBBB-BBBB") { bearerAuth(token) }
        assertEquals(HttpStatusCode.NotFound, lookup.status)
        assertEquals("code_not_found", lookup.field("code"))
    }
}
