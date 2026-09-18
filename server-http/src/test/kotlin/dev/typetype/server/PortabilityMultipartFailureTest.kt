package dev.typetype.server

import dev.typetype.server.cache.CacheJson
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class PortabilityMultipartFailureTest {
    @Test
    fun `multipart scanner limit is a typed 413 not a 500`() = testApplication {
        application {
            install(ContentNegotiation) { json(CacheJson) }
            configureStatusPages()
            routing {
                post("/portability/imports") {
                    throw IOException("Limit of 536870912 bytes exceeded while searching for boundary")
                }
                post("/unrelated") {
                    throw IOException("disk failure")
                }
            }
        }
        val response = client.post("/portability/imports")
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(response.bodyAsText().contains("portability_upload_too_large"))
        assertEquals(HttpStatusCode.InternalServerError, client.post("/unrelated").status)
    }
}
