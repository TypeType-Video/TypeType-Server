package dev.typetype.server

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.routes.streamRoutes
import dev.typetype.server.services.ProviderMediaHandleService
import dev.typetype.server.services.StreamService
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamRoutesFailureStatusTest {
    private val streamService: StreamService = mockk()

    @Test
    fun `provider blocks tell clients when to retry`() = testApplication {
        failWith(ExtractionResult.Failure("blocked", "provider_access_blocked", ExtractionFailureKind.ProviderAccessBlocked))

        val response = client.get(YOUTUBE)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("60", response.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun `rate limits answer 429 with the provider delay and keep their code`() = testApplication {
        failWith(ExtractionResult.Failure("slow down", "provider_rate_limited", ExtractionFailureKind.RateLimited, 42L))

        val response = client.get(BILIBILI)

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("42", response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("\"code\":\"provider_rate_limited\""))
    }

    @Test
    fun `upstream timeouts answer 504 without retry hint`() = testApplication {
        failWith(ExtractionResult.Failure("late", "upstream_timeout", ExtractionFailureKind.UpstreamTimeout))

        val response = client.get(YOUTUBE)

        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
        assertNull(response.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun `unavailable content answers 422 on every provider`() = testApplication {
        failWith(ExtractionResult.Failure("missing", "provider_data_missing", ExtractionFailureKind.Unavailable))

        assertEquals(HttpStatusCode.UnprocessableEntity, client.get(YOUTUBE).status)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.get(NICONICO).status)
    }

    @Test
    fun `niconico geographic restriction is a content answer not a bad request`() = testApplication {
        failWith(ExtractionResult.BadRequest("Only in Japan", "geographic_restriction"))

        val response = client.get(NICONICO)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("\"code\":\"geographic_restriction\""))
    }

    @Test
    fun `niconico provider blocks answer 503`() = testApplication {
        failWith(ExtractionResult.Failure("blocked", "provider_access_blocked", ExtractionFailureKind.ProviderAccessBlocked))

        val response = client.get(NICONICO)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("60", response.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun `unprepared sabr playback is a retryable outage`() = testApplication {
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(sabrResponse())
        install { _, _ -> null }

        val response: HttpResponse = client.get(YOUTUBE)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("15", response.headers[HttpHeaders.RetryAfter])
        assertTrue(response.bodyAsText().contains("\"code\":\"no_playable_streams\""))
    }

    @Test
    fun `expired provider media urls are content answers`() = testApplication {
        val status = mediaHandleFailureStatus(IllegalArgumentException("Provider media URL has expired"))

        assertEquals(HttpStatusCode.UnprocessableEntity, status)
    }

    @Test
    fun `media handle storage outages are retryable`() = testApplication {
        val status = mediaHandleFailureStatus(IllegalStateException("cache down"))

        assertEquals(HttpStatusCode.ServiceUnavailable, status)
    }

    private suspend fun ApplicationTestBuilder.mediaHandleFailureStatus(error: Exception): HttpStatusCode {
        val handles = mockk<ProviderMediaHandleService>()
        coEvery { handles.materialize(any(), any()) } throws error
        coEvery { streamService.getStreamInfo(any()) } returns ExtractionResult.Success(
            testStreamResponse(),
        )
        application {
            install(ContentNegotiation) { json() }
            routing { streamRoutes(streamService, providerMediaHandleService = handles) }
        }
        return client.get(NICONICO).status
    }

    private fun ApplicationTestBuilder.failWith(result: ExtractionResult<StreamResponse>) {
        coEvery { streamService.getStreamInfo(any()) } returns result
        install()
    }

    private fun ApplicationTestBuilder.install(
        sabrFilter: (suspend (String, StreamResponse) -> StreamResponse?)? = null,
    ) = application {
        install(ContentNegotiation) { json() }
        routing { streamRoutes(streamService, sabrStreamContractFilter = sabrFilter) }
    }

    private fun sabrResponse(): StreamResponse = testStreamResponse(
        videoOnlyStreams = listOf(testVideoStream().copy(deliveryMethod = "sabr")),
        audioStreams = listOf(testAudioStream(deliveryMethod = "sabr")),
    )

    private companion object {
        const val YOUTUBE = "/streams/youtube/sabr?url=https://youtube.com/watch?v=test"
        const val NICONICO = "/streams/niconico?url=https://www.nicovideo.jp/watch/sm9"
        const val BILIBILI = "/streams/bilibili?url=https://www.bilibili.com/video/BV1xx411c7mD"
    }
}
