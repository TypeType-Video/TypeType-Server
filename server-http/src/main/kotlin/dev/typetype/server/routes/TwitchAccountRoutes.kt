package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.TwitchChatMessageRequest
import dev.typetype.server.models.TwitchDevicePollRequest
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.TwitchLiveResponse
import dev.typetype.server.services.TwitchService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun Route.twitchAccountRoutes(
    service: TwitchService,
    authService: AuthService,
    adminSettingsService: AdminSettingsService?,
) {
    suspend fun ApplicationCall.signedIn(block: suspend (String) -> Unit) {
        if (!twitchEnabled(service)) return
        withJwtAuth(authService) { userId ->
            if (userId.startsWith("guest:")) {
                respond(HttpStatusCode.Forbidden, ErrorResponse("Sign in to link a Twitch account", "account_required"))
            } else {
                block(userId)
            }
        }
    }

    suspend fun ApplicationCall.respondLinked(response: TwitchLiveResponse?) {
        if (response == null) {
            respond(HttpStatusCode.Conflict, ErrorResponse("No Twitch account linked", "twitch_not_linked"))
        } else {
            respondLive(response)
        }
    }

    get("/twitch/account") { call.signedIn { userId -> call.respond(service.status(userId)) } }
    post("/twitch/account/device") { call.signedIn { call.respondLive(service.startDevice()) } }
    post("/twitch/account/poll") {
        call.signedIn { userId ->
            val request = runCatching { call.receive<TwitchDevicePollRequest>() }.getOrNull()
            if (request == null || request.deviceCode.isBlank() || request.deviceCode.length > MAX_DEVICE_CODE) {
                return@signedIn call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            val (status, body) = service.poll(userId, request.deviceCode)
            if (body == null) {
                call.respond(HttpStatusCode.fromValue(status), ErrorResponse("Twitch login failed", "twitch_login_failed"))
            } else {
                call.respond(body)
            }
        }
    }
    delete("/twitch/account") {
        call.signedIn { userId ->
            service.unlink(userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get("/twitch/me/live") {
        call.signedIn { userId -> call.respondLinked(service.authed(userId, "/v1/me/live", call.page())) }
    }
    get("/twitch/me/follows") {
        call.signedIn { userId -> call.respondLinked(service.authed(userId, "/v1/me/follows", call.page())) }
    }
    get("/twitch/me/follows/{login}") {
        val login = call.pathParam("login", twitchLoginPattern) ?: return@get
        call.signedIn { userId -> call.respondLinked(service.authed(userId, "/v1/me/follows/$login", emptyMap())) }
    }
    put("/twitch/me/follows/{login}") {
        val login = call.pathParam("login", twitchLoginPattern) ?: return@put
        call.signedIn { userId -> call.respondLinked(service.setFollow(userId, login, follow = true)) }
    }
    delete("/twitch/me/follows/{login}") {
        val login = call.pathParam("login", twitchLoginPattern) ?: return@delete
        call.signedIn { userId -> call.respondLinked(service.setFollow(userId, login, follow = false)) }
    }
    post("/twitch/chat/{login}/messages") {
        val login = call.pathParam("login", twitchLoginPattern) ?: return@post
        call.signedIn { userId ->
            val request = runCatching { call.receive<TwitchChatMessageRequest>() }.getOrNull()
            val message = request?.message?.trim().orEmpty()
            if (message.isEmpty() || message.length > MAX_MESSAGE) {
                return@signedIn call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid message"))
            }
            val json = Json.encodeToString(buildJsonObject { put("message", message) })
            call.respondLinked(service.sendChat(userId, login, json))
        }
    }
    get("/twitch/chat/{login}/events") {
        val login = call.pathParam("login", twitchLoginPattern) ?: return@get
        if (!call.twitchEnabled(service)) return@get
        call.accessProfileOrRespond(authService, null, adminSettingsService) ?: return@get
        call.relayChat(service, login)
    }
}

private suspend fun ApplicationCall.relayChat(service: TwitchService, login: String) {
    val upstream = runCatching { withContext(Dispatchers.IO) { service.openChat(login) } }.getOrElse {
        return respond(HttpStatusCode.BadGateway, ErrorResponse("Chat unavailable"))
    }
    upstream.use { response ->
        if (!response.isSuccessful) {
            return respond(HttpStatusCode.BadGateway, ErrorResponse("Chat unavailable"))
        }
        respondBytesWriter(ContentType.Text.EventStream, HttpStatusCode.OK) {
            val source = response.body.source()
            val buffer = ByteArray(CHAT_BUFFER)
            while (true) {
                val read = withContext(Dispatchers.IO) { source.read(buffer) }
                if (read < 0) break
                writeFully(buffer, 0, read)
                flush()
            }
        }
    }
}

private fun ApplicationCall.page(): Map<String, String> =
    listOf("first", "after").mapNotNull { name -> request.queryParameters[name]?.let { name to it.take(MAX_PARAM) } }.toMap()

private const val MAX_DEVICE_CODE = 200
private const val MAX_MESSAGE = 500
private const val MAX_PARAM = 200
private const val CHAT_BUFFER = 8 * 1024
