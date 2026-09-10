package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.providerMediaHandleRoutes
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.ProxyService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ProviderMediaHandleRoutesTest {
    private val proxyService: ProxyService = mockk()

    @Test
    fun `media route forwards opaque handles and ranges`() = runTest {
        val service = ProviderMediaHandleService(FakeCacheService())
        val raw = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=9999999999"
        val path = service.createPath(raw)
        coEvery { proxyService.pipe(raw, "bytes=0-3", null) } returns ExtractionResult.Success(
            ProxyResponse(206, "video/mp4", 4, "bytes 0-3/4", "bytes", ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), {}),
        )

        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { providerMediaHandleRoutes(service, proxyService) }
            }
            val response = client.get(path) { header("Range", "bytes=0-3") }
            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals("\u0001\u0002\u0003\u0004", response.bodyAsText())
        }

        coVerify(exactly = 1) { proxyService.pipe(raw, "bytes=0-3", null) }
    }

    @Test
    fun `expired or unknown handles return not found`() = testApplication {
        val service = ProviderMediaHandleService(FakeCacheService())
        application {
            install(ContentNegotiation) { json() }
            routing { providerMediaHandleRoutes(service, proxyService) }
        }

        val response = client.get("/media/m1_123456789012345678901234")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("media_handle_not_found"))
    }
}
