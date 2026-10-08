package dev.typetype.server

import dev.typetype.server.models.LiveChatMessage
import dev.typetype.server.routes.youtubeLiveChatRoutes
import dev.typetype.server.services.YoutubeLiveChatEvent
import dev.typetype.server.services.YoutubeLiveChatOpenResult
import dev.typetype.server.services.YoutubeLiveChatService
import dev.typetype.server.services.YoutubeLiveChatSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeLiveChatRoutesTest {
    @Test
    fun `GET live-chat requires url`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { youtubeLiveChatRoutes(FakeService(YoutubeLiveChatOpenResult.CapacityReached)) }
        }
        assertEquals(HttpStatusCode.BadRequest, client.get("/live-chat").status)
    }

    @Test
    fun `GET live-chat streams messages and heartbeats`() = testApplication {
        val session = FakeSession()
        application { routing { youtubeLiveChatRoutes(FakeService(YoutubeLiveChatOpenResult.Opened(session))) } }
        val response = client.get("/live-chat?url=https://www.youtube.com/watch?v=video")
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Type"].orEmpty().contains("text/event-stream"))
        assertTrue(body.contains("event: message\ndata: {\"id\":\"chat-1\",\"text\":\"hello\",\"receivedAtMs\":1}"))
        assertTrue(body.contains(": keep-alive"))
        assertTrue(session.closed)
    }

    @Test
    fun `GET live-chat does not gzip event streams`() = testApplication {
        application {
            configureCompression()
            routing {
                youtubeLiveChatRoutes(FakeService(YoutubeLiveChatOpenResult.Opened(FakeSession())))
            }
        }
        val response = client.get("/live-chat?url=https://www.youtube.com/watch?v=video") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }
        response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentEncoding].isNullOrEmpty())
    }

    @Test
    fun `GET live-chat rejects unsupported videos`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                youtubeLiveChatRoutes(FakeService(YoutubeLiveChatOpenResult.Unsupported("not live")))
            }
        }
        assertEquals(
            HttpStatusCode.UnprocessableEntity,
            client.get("/live-chat?url=https://www.youtube.com/watch?v=video").status,
        )
    }

    @Test
    fun `GET live-chat applies capacity response`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { youtubeLiveChatRoutes(FakeService(YoutubeLiveChatOpenResult.CapacityReached)) }
        }
        val response = client.get("/live-chat?url=https://www.youtube.com/watch?v=video")
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("5", response.headers["Retry-After"])
    }

    private class FakeService(private val result: YoutubeLiveChatOpenResult) : YoutubeLiveChatService {
        override suspend fun openSession(url: String) = result
        override fun close() = Unit
    }

    private class FakeSession : YoutubeLiveChatSession {
        var closed = false
        override val events = flowOf(
            YoutubeLiveChatEvent.Message(LiveChatMessage("chat-1", "hello", 1L)),
            YoutubeLiveChatEvent.Heartbeat,
        )

        override fun close() {
            closed = true
        }
    }
}
