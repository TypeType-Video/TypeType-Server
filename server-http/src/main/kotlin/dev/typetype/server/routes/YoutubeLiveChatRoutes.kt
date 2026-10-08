package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.YoutubeLiveChatEvent
import dev.typetype.server.services.YoutubeLiveChatOpenResult
import dev.typetype.server.services.YoutubeLiveChatService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.youtubeLiveChatRoutes(
    liveChatService: YoutubeLiveChatService,
    authService: AuthService? = null,
    adminSettingsService: AdminSettingsService? = null,
) {
    get("/live-chat") {
        if (!call.requirePublicAccessOrRespond(authService, adminSettingsService)) return@get
        val url = call.request.queryParameters["url"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'url' parameter"))

        when (val result = liveChatService.openSession(url)) {
            is YoutubeLiveChatOpenResult.Unsupported -> call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message))
            is YoutubeLiveChatOpenResult.Unavailable -> call.respond(HttpStatusCode.BadGateway, ErrorResponse(result.message))
            YoutubeLiveChatOpenResult.CapacityReached -> {
                call.response.headers.append(HttpHeaders.RetryAfter, "5")
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("Live chat capacity is temporarily full"))
            }
            is YoutubeLiveChatOpenResult.Opened -> {
                call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                call.response.headers.append("X-Accel-Buffering", "no")
                try {
                    call.respondBytesWriter(contentType = ContentType.Text.EventStream, status = HttpStatusCode.OK) {
                        writeFully(": connected\n\n".encodeToByteArray())
                        flush()
                        result.session.events.catch { error ->
                            if (error is CancellationException) throw error
                            emit(YoutubeLiveChatEvent.Error(ErrorResponse("Live chat retrieval failed")))
                        }.collect { event ->
                            when (event) {
                                is YoutubeLiveChatEvent.Message -> writeSse("message", Json.encodeToString(event.message))
                                is YoutubeLiveChatEvent.Error -> writeSse("error", Json.encodeToString(event.error))
                                YoutubeLiveChatEvent.Heartbeat -> {
                                    writeFully(": keep-alive\n\n".encodeToByteArray())
                                    flush()
                                }
                            }
                        }
                    }
                } finally {
                    result.session.close()
                }
            }
        }
    }
}

private suspend fun io.ktor.utils.io.ByteWriteChannel.writeSse(event: String, data: String) {
    writeFully("event: $event\ndata: $data\n\n".encodeToByteArray())
    flush()
}
