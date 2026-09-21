package dev.typetype.server

import dev.typetype.server.routes.sabrRoutes
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.sabrProbeTokenServiceUrl
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@EnabledIfSystemProperty(named = "sabr.probe", matches = "true")
@Tag("network")
class SabrHttpRandomAccessInitProbeTest {
    private val streamService: StreamService = mockk(relaxed = true)
    private val sabrSessionStore = SabrSessionStore(tokenServiceUrl = sabrProbeTokenServiceUrl())

    @Test
    fun `descriptor init endpoints return bytes after non zero start`() = testApplication {
        try {
            installSabrApp()

            listOf(128_000L, 149_717L).forEach { playerTimeMs ->
                val descriptor = client.get(
                    "/sabr/session/ZNHSuMl7GnE?videoItag=136&audioItag=140&playerTimeMs=$playerTimeMs"
                )
                assertEquals(HttpStatusCode.OK, descriptor.status, descriptor.bodyAsText())
                val endpoints = Json.parseToJsonElement(descriptor.bodyAsText())
                    .jsonObject["endpoints"]
                    ?.jsonObject
                    ?: error("Missing endpoints")
                val audioInit = endpoints["audioInit"]?.jsonPrimitive?.content ?: error("Missing audioInit")
                val videoInit = endpoints["videoInit"]?.jsonPrimitive?.content ?: error("Missing videoInit")

                assertMp4Init(audioInit, "audio/mp4")
                assertMp4Init(videoInit, "video/mp4")
            }
        } finally {
            sabrSessionStore.release()
        }
    }

    private suspend fun ApplicationTestBuilder.assertMp4Init(path: String, expectedType: String): Unit {
        val response = client.get(path)
        val body = response.bodyAsBytes()
        val contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(";")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(expectedType, contentType)
        assertTrue(body.isNotEmpty(), "empty init body for $path")
    }

    private fun ApplicationTestBuilder.installSabrApp(): Unit = application {
        install(ContentNegotiation) { json() }
        routing {
            sabrRoutes(
                sabrSessionStore,
                streamService,
                authService = null,
                accessControlService = null,
                adminSettingsService = null,
                audioOnlyTokenService = null,
            )
        }
    }
}
