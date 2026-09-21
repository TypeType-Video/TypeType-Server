package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.YoutubeRemoteBrowserCompleteRequest
import dev.typetype.server.models.YoutubeRemoteBrowserStartRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.YoutubeRemoteBrowserCompleteResult
import dev.typetype.server.services.YoutubeRemoteBrowserService
import dev.typetype.server.services.YoutubeRemoteBrowserStartResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket

fun Route.youtubeRemoteBrowserRoutes(service: YoutubeRemoteBrowserService, authService: AuthService): Unit {
    post("/youtube-session/browser/start") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<YoutubeRemoteBrowserStartRequest>() }
                .getOrDefault(YoutubeRemoteBrowserStartRequest())
            when (val result = service.start(userId, request.returnTo)) {
                is YoutubeRemoteBrowserStartResult.Started -> call.respond(HttpStatusCode.Created, result.response)
                YoutubeRemoteBrowserStartResult.Disabled -> call.respond(HttpStatusCode.Forbidden, ErrorResponse("YouTube remote login is disabled", "youtube_remote_login_disabled"))
                YoutubeRemoteBrowserStartResult.Misconfigured -> call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("YouTube remote login is unavailable", "youtube_remote_login_unavailable"))
                YoutubeRemoteBrowserStartResult.AlreadyActive -> call.respond(HttpStatusCode.Conflict, ErrorResponse("A YouTube remote login session is already active", "youtube_remote_login_already_active"))
                YoutubeRemoteBrowserStartResult.CapacityReached -> call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("Too many YouTube remote login sessions", "youtube_remote_login_capacity"))
                YoutubeRemoteBrowserStartResult.TokenUnavailable -> call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("YouTube remote login is unavailable", "youtube_remote_login_unavailable"))
            }
        }
    }

    webSocket("/youtube-session/browser/{sessionId}") {
        val sessionId = call.parameters["sessionId"].orEmpty()
        val token = call.request.queryParameters["token"]
        service.bridge(sessionId, token, this)
    }

    delete("/youtube-session/browser/{sessionId}") {
        call.withJwtAuth(authService) { userId ->
            val sessionId = call.parameters["sessionId"].orEmpty()
            if (service.cancel(userId, sessionId)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Remote login session not found", "youtube_remote_login_not_found"))
            }
        }
    }

    post("/internal/youtube-remote-login/callback") { call.respondCompletion(service) }
    post("/internal/youtube-session/browser/complete") { call.respondCompletion(service) }
}

private suspend fun ApplicationCall.respondCompletion(service: YoutubeRemoteBrowserService) {
    val payload = runCatching { receive<YoutubeRemoteBrowserCompleteRequest>() }.getOrElse {
        return respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
    }
    when (service.complete(payload, request.headers[INTERNAL_HEADER])) {
        YoutubeRemoteBrowserCompleteResult.Completed -> respond(HttpStatusCode.NoContent)
        YoutubeRemoteBrowserCompleteResult.Unauthorized -> respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized", "unauthorized"))
        YoutubeRemoteBrowserCompleteResult.NotFound -> respond(HttpStatusCode.NotFound, ErrorResponse("Remote login session not found", "youtube_remote_login_not_found"))
        YoutubeRemoteBrowserCompleteResult.InvalidPayload -> respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid remote login payload", "youtube_remote_login_invalid_payload"))
        YoutubeRemoteBrowserCompleteResult.InvalidCredentials -> respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid YouTube credentials", "youtube_credentials_invalid"))
        YoutubeRemoteBrowserCompleteResult.Unavailable -> respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("YouTube Session is unavailable", "youtube_session_unavailable"))
    }
}

private const val INTERNAL_HEADER = "X-Internal-Token"
