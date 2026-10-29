package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import dev.typetype.server.routes.providerMediaHandleRoutes
import dev.typetype.server.services.ProviderMediaAwareProxyService
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.ProxyService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ProviderMediaHandleAlternatesRoutesTest {
    @Test
    fun mediaRouteForwardsCachedAlternatesToProviderProxy() = testApplication {
        val primary = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=9999999999"
        val alternate = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s?deadline=9999999999"
        val handles = ProviderMediaHandleService(FakeCacheService())
        val path = handles.createPath(primary, alternateUrls = listOf(alternate))
        val proxy = RecordingProviderProxy()
        application {
            install(ContentNegotiation) { json() }
            routing { providerMediaHandleRoutes(handles, proxy) }
        }

        val response = client.get(path)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(alternate, proxy.receivedAlternates?.firstOrNull())
        assertEquals("x", response.bodyAsText())
    }
}

private class RecordingProviderProxy : ProxyService, ProviderMediaAwareProxyService {
    var receivedAlternates: List<String>? = null
        private set

    override suspend fun pipe(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
    ): ExtractionResult<ProxyResponse> = error("unexpected plain proxy call")

    override suspend fun pipeProviderMedia(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
    ): ExtractionResult<ProxyResponse> = error("expected alternate-aware proxy call")

    override suspend fun pipeProviderMediaWithAlternates(
        url: String,
        rangeHeader: String?,
        domandBid: String?,
        alternateUrls: List<String>,
    ): ExtractionResult<ProxyResponse> {
        receivedAlternates = alternateUrls
        return ExtractionResult.Success(
            ProxyResponse(200, "text/plain", 1, null, null, ByteArrayInputStream("x".toByteArray()), {}),
        )
    }
}
