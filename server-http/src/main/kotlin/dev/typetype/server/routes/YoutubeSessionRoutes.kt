package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.YoutubeSessionCompleteRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.YoutubeSessionCompleteResult
import dev.typetype.server.services.YoutubeSessionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.youtubeSessionRoutes(youtubeSessionService: YoutubeSessionService, authService: AuthService): Unit {
    post("/youtube-session/pairing") {
        call.withJwtAuth(authService) { userId ->
            if (!youtubeSessionService.isConfigured) {
                return@withJwtAuth call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("YouTube Session is unavailable", "youtube_session_unavailable"),
                )
            }
            call.respond(HttpStatusCode.Created, youtubeSessionService.createPairing(userId))
        }
    }
    post("/youtube-session/complete") {
        if (!youtubeSessionService.isConfigured) {
            return@post call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("YouTube Session is unavailable", "youtube_session_unavailable"),
            )
        }
        val request = runCatching { call.receive<YoutubeSessionCompleteRequest>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        }
        when (youtubeSessionService.complete(request)) {
            YoutubeSessionCompleteResult.Completed -> call.respond(HttpStatusCode.NoContent)
            YoutubeSessionCompleteResult.InvalidCode -> {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Invalid pairing code", "youtube_pairing_invalid"))
            }
            YoutubeSessionCompleteResult.ExpiredCode -> {
                call.respond(HttpStatusCode.Gone, ErrorResponse("Pairing code expired", "youtube_pairing_expired"))
            }
            YoutubeSessionCompleteResult.InvalidCredentials -> {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid YouTube credentials", "youtube_credentials_invalid"))
            }
            YoutubeSessionCompleteResult.Unavailable -> {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("YouTube Session is unavailable", "youtube_session_unavailable"))
            }
        }
    }
    get("/youtube-session/status") {
        call.withJwtAuth(authService) { userId ->
            call.respond(youtubeSessionService.status(userId))
        }
    }
    delete("/youtube-session") {
        call.withJwtAuth(authService) { userId ->
            youtubeSessionService.delete(userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
