package dev.typetype.server

import dev.typetype.server.models.ServerNoticeRequest
import dev.typetype.server.routes.serverEventRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ServerNoticeHub
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerEventRoutesTest {
    private val auth = AuthService("test-secret")

    @Test
    fun `the stream greets, relays a notice, then ends on shutdown with a restart notice`() = testApplication {
        val hub = ServerNoticeHub(bootId = "boot-1", now = { 7L })
        application { routing { serverEventRoutes(hub, auth) } }
        val (response, body) = coroutineScope {
            launch {
                hub.subscribers.first { it > 0 }
                hub.announce(ServerNoticeRequest(kind = "update", message = "New version", resumeInSeconds = 30))
                hub.shutdown()
            }
            val response = client.get("/server/events")
            response to response.bodyAsText()
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Type"].orEmpty().contains("text/event-stream"))
        assertTrue(body.startsWith("event: hello\ndata: {\"bootId\":\"boot-1\""), body)
        assertTrue(
            body.contains("event: notice\ndata: {\"kind\":\"update\",\"message\":\"New version\",\"resumeInSeconds\":30,\"issuedAt\":7}"),
            body,
        )
        assertTrue(body.trimEnd().endsWith("{\"kind\":\"restart\",\"resumeInSeconds\":20,\"issuedAt\":7}"), body)
    }

    @Test
    fun `announcing and clearing a notice needs an admin`() = testApplication {
        val hub = ServerNoticeHub()
        application {
            install(ContentNegotiation) { json() }
            routing { serverEventRoutes(hub, auth) }
        }

        val announce = client.post("/admin/server/notice") {
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"update"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, announce.status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/admin/server/notice").status)
        assertEquals(null, hub.hello().notice)
    }
}
