package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.SessionActivityRequest
import dev.typetype.server.models.SessionPlaybackProgressRequest
import dev.typetype.server.models.SessionPlaybackStartRequest
import dev.typetype.server.models.SessionPlaybackStopRequest
import dev.typetype.server.services.ActiveSessionService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.PresenceService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.sessionActivityRoutes(
    authService: AuthService,
    activeSessionService: ActiveSessionService,
    presenceService: PresenceService = PresenceService(),
): Unit {
    post("/sessions/activity") {
        call.withJwtAuth(authService) { userId ->
            val body = runCatching { call.receive<SessionActivityRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            activeSessionService.reportActivity(userId, body, call.request.headers[HttpHeaders.UserAgent])
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/sessions/playback/start") {
        call.withJwtAuth(authService) { userId ->
            val body = runCatching { call.receive<SessionPlaybackStartRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (body.videoUrl.isBlank() || body.title.isBlank()) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing now playing fields"))
            }
            activeSessionService.reportPlaybackStart(userId, body, call.request.headers[HttpHeaders.UserAgent])
            presenceService.reportPlaybackStart(userId, body)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/sessions/playback/progress") {
        call.withJwtAuth(authService) { userId ->
            val body = runCatching { call.receive<SessionPlaybackProgressRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            activeSessionService.reportPlaybackProgress(userId, body, call.request.headers[HttpHeaders.UserAgent])
            presenceService.reportPlaybackProgress(userId, body)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/sessions/playback/stop") {
        call.withJwtAuth(authService) { userId ->
            val body = runCatching { call.receive<SessionPlaybackStopRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            activeSessionService.reportPlaybackStop(userId, body, call.request.headers[HttpHeaders.UserAgent])
            presenceService.reportPlaybackStop(userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
