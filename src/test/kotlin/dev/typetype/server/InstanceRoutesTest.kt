package dev.typetype.server

import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.OidcPublicConfig
import dev.typetype.server.routes.authRoutes
import dev.typetype.server.routes.publicMetadataRoutes
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.InstanceService
import dev.typetype.server.services.PasswordResetService
import dev.typetype.server.services.ProfileService
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InstanceRoutesTest {
    private val adminSettings = AdminSettingsService()
    private val passwordReset = PasswordResetService()
    private val profile = ProfileService()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() { TestDatabase.truncateAll() }

    @Test
    fun `instance returns defaults and cache header`() = testApplication {
        val auth = AuthService.fixed(TEST_USER_ID, hasUsers = false)
        val instanceService = InstanceService(auth, adminSettings)
        application {
            install(ContentNegotiation) { json() }
            routing { publicMetadataRoutes(instanceService::getInstance) }
        }
        val response = client.get("/instance")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store, no-cache, must-revalidate, max-age=0", response.headers[HttpHeaders.CacheControl])
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("TypeType", root["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.VERSION, root["version"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.REVISION, root["revision"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.SHORT_REVISION, root["shortRevision"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.BUILD_TIME, root["buildTime"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1, root["apiVersion"]?.jsonPrimitive?.int)
        assertEquals(true, root["registrationAllowed"]?.jsonPrimitive?.boolean)
        assertEquals(true, root["guestAllowed"]?.jsonPrimitive?.boolean)
        assertEquals(true, root["localLoginEnabled"]?.jsonPrimitive?.boolean)
        assertEquals(false, root["oidcEnabled"]?.jsonPrimitive?.boolean)
        assertEquals(false, root["youtubeRemoteLoginEnabled"]?.jsonPrimitive?.boolean)
        assertEquals(false, root["youtubeRemoteLoginReady"]?.jsonPrimitive?.boolean)
        assertEquals(false, root["parentalControlsEnabled"]?.jsonPrimitive?.boolean)
        assertEquals("disabled", root["youtubeRemoteLoginUnavailableReason"]?.jsonPrimitive?.contentOrNull)
        val rss = root["rss"]?.jsonObject
        assertEquals(false, rss?.get("enabled")?.jsonPrimitive?.boolean)
        assertEquals(10, rss?.get("maxFeedsPerUser")?.jsonPrimitive?.int)
        assertEquals(listOf(0, 5, 6), root["supportedServices"]?.jsonArray?.map { it.jsonPrimitive.int })
        assertEquals(null, root["androidPlayback"])
    }

    @Test
    fun `instance reflects custom settings and blocks registration when users exist`() = testApplication {
        adminSettings.upsert(AdminSettingsItem(
            name = "Custom Instance",
            tagline = "Privacy-respecting video platform",
            logoUrl = "https://cdn.example.com/typetype/logo.png",
            bannerUrl = "https://cdn.example.com/typetype/banner.jpg",
            minAndroidClientVersion = "0.1.0",
            allowRegistration = false,
            allowGuest = false,
            localLoginEnabled = false,
            oidcAutoRedirect = true,
            youtubeRemoteLoginEnabled = true,
            accessMode = "allow_list",
            rssEnabled = true,
            rssPublicBaseUrl = "https://video.example/",
            rssMaxFeedsPerUser = 4,
            rssMaxItems = 80,
            rssMinimumPollMinutes = 15,
            rssRateLimitPerMinute = 12,
        )
        )
        val auth = AuthService.fixed(TEST_USER_ID, hasUsers = true)
        val instanceService = InstanceService(
            auth,
            adminSettings,
            youtubeRemoteLoginAvailable = { true },
            oidcConfigProvider = { OidcPublicConfig(enabled = true, providerName = "Keycloak") },
        )
        application {
            install(ContentNegotiation) { json() }
            routing { publicMetadataRoutes(instanceService::getInstance); authRoutes(auth, passwordReset, profile, adminSettings) }
        }
        val response = client.get("/instance")
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Custom Instance", root["name"]?.jsonPrimitive?.contentOrNull)
        assertEquals(false, root["registrationAllowed"]?.jsonPrimitive?.boolean)
        assertEquals(false, root["guestAllowed"]?.jsonPrimitive?.boolean)
        assertEquals(false, root["localLoginEnabled"]?.jsonPrimitive?.boolean)
        assertEquals(true, root["oidcEnabled"]?.jsonPrimitive?.boolean)
        assertEquals("Keycloak", root["oidcProviderName"]?.jsonPrimitive?.contentOrNull)
        assertEquals(true, root["oidcAutoRedirect"]?.jsonPrimitive?.boolean)
        assertEquals(true, root["youtubeRemoteLoginEnabled"]?.jsonPrimitive?.boolean)
        assertEquals(true, root["youtubeRemoteLoginReady"]?.jsonPrimitive?.boolean)
        assertEquals(true, root["parentalControlsEnabled"]?.jsonPrimitive?.boolean)
        assertEquals(null, root["youtubeRemoteLoginUnavailableReason"]?.jsonPrimitive?.contentOrNull)
        val rss = root["rss"]!!.jsonObject
        assertEquals(true, rss["enabled"]?.jsonPrimitive?.boolean)
        assertEquals(4, rss["maxFeedsPerUser"]?.jsonPrimitive?.int)
        assertEquals(80, rss["maxItems"]?.jsonPrimitive?.int)
        assertEquals(15, rss["minimumPollMinutes"]?.jsonPrimitive?.int)
        assertEquals(12, rss["rateLimitPerMinute"]?.jsonPrimitive?.int)
        val register = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"new@test.local","password":"secret","name":"New"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, register.status)
    }

    @Test
    fun `version returns build info and cache header`() = testApplication {
        val auth = AuthService.fixed(TEST_USER_ID, hasUsers = false)
        val instanceService = InstanceService(auth, adminSettings)
        application {
            install(ContentNegotiation) { json() }
            routing { publicMetadataRoutes(instanceService::getInstance) }
        }
        val response = client.get("/version")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store, no-cache, must-revalidate, max-age=0", response.headers[HttpHeaders.CacheControl])
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("server", root["service"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.VERSION, root["version"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.REVISION, root["revision"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.SHORT_REVISION, root["shortRevision"]?.jsonPrimitive?.contentOrNull)
        assertEquals(BuildInfo.BUILD_TIME, root["buildTime"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `instance tells whether the optional recommender is there and switched on`() = testApplication {
        val auth = AuthService.fixed(TEST_USER_ID, hasUsers = false)
        var installed = false
        val instanceService = InstanceService(auth, adminSettings, recommenderInstalled = { installed })
        application {
            install(ContentNegotiation) { json() }
            routing { publicMetadataRoutes(instanceService::getInstance) }
        }
        suspend fun capability() = Json.parseToJsonElement(client.get("/instance").bodyAsText()).jsonObject["recommender"]?.jsonObject
        assertEquals(false, capability()?.get("installed")?.jsonPrimitive?.boolean)
        adminSettings.upsert(AdminSettingsItem(recommenderEnabled = true))
        assertEquals(false, capability()?.get("enabled")?.jsonPrimitive?.boolean)
        installed = true
        assertEquals(true, capability()?.get("installed")?.jsonPrimitive?.boolean)
        assertEquals(true, capability()?.get("enabled")?.jsonPrimitive?.boolean)
    }
}
