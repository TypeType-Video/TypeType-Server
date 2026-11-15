package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ServerNoticeRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ServerEvent
import dev.typetype.server.services.ServerNoticeHub
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val HEARTBEAT_MS = 15_000L

fun Route.serverEventRoutes(hub: ServerNoticeHub, authService: AuthService) {
    get("/server/events") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.response.headers.append("X-Accel-Buffering", "no")
        call.respondBytesWriter(contentType = ContentType.Text.EventStream, status = HttpStatusCode.OK) {
            stopOnClientDisconnect { writeServerEvents(hub) }
        }
    }
    post("/admin/server/notice") {
        call.withAdminAuth(authService) { _ ->
            val body = runCatching { call.receive<ServerNoticeRequest>() }.getOrElse {
                return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val notice = hub.announce(body)
                ?: return@withAdminAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("SERVER_NOTICE_INVALID"))
            call.respond(notice)
        }
    }
    delete("/admin/server/notice") {
        call.withAdminAuth(authService) { _ ->
            hub.clear()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal suspend fun ByteWriteChannel.writeServerEvents(hub: ServerNoticeHub, heartbeatMs: Long = HEARTBEAT_MS): Unit =
    coroutineScope {
        val events = hub.subscribe().produceIn(this)
        try {
            while (writeNext(events, heartbeatMs)) Unit
        } finally {
            events.cancel()
        }
    }

private suspend fun ByteWriteChannel.writeNext(events: ReceiveChannel<ServerEvent>, heartbeatMs: Long): Boolean {
    val event = withTimeoutOrNull(heartbeatMs) { events.receiveCatching().getOrNull() ?: ServerEvent.Closed }
    when (event) {
        null -> writeFully(": keep-alive\n\n".encodeToByteArray())
        is ServerEvent.Hello -> writeEvent("hello", Json.encodeToString(event.hello))
        is ServerEvent.Notice -> writeEvent("notice", Json.encodeToString(event.notice))
        ServerEvent.Cleared -> writeEvent("clear", "{}")
        ServerEvent.Closed -> return false
    }
    flush()
    return true
}

private suspend fun ByteWriteChannel.writeEvent(event: String, data: String) {
    writeFully("event: $event\ndata: $data\n\n".encodeToByteArray())
}
