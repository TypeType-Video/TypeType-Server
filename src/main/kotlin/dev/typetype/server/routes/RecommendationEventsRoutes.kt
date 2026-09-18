package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.RecommendationEventRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.HomeRecommendationContextualBandit
import dev.typetype.server.services.HomeRecommendationDeviceClass
import dev.typetype.server.services.HomeRecommendationSessionIntent
import dev.typetype.server.services.RecommendationEventService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.recommendationEventsRoutes(eventService: RecommendationEventService, authService: AuthService) {
    get("/recommendations/events") {
        call.withJwtAuth(authService) { userId ->
            call.respond(eventService.getAll(userId))
        }
    }
    post("/recommendations/events") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<RecommendationEventRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (request.eventType !in setOf("impression", "click", "watch", "favorite", "watch_later_add", "short_skip")) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid eventType"))
            }
            if ((request.eventType == "click" || request.eventType == "watch") && request.videoUrl.isNullOrBlank()) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            }
            val watchRatio = request.watchRatio
            if (watchRatio != null && (watchRatio < 0.0 || watchRatio > 1.0)) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid watchRatio"))
            }
            val watchDurationMs = request.watchDurationMs
            if (watchDurationMs != null && watchDurationMs < 0) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid watchDurationMs"))
            }
            val providedContextKey = request.contextKey
            if (providedContextKey != null && providedContextKey.length > 120) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid contextKey"))
            }
            val contextKey = request.contextKey ?: HomeRecommendationContextualBandit.contextKey(
                serviceId = request.serviceId ?: 0,
                intent = HomeRecommendationSessionIntent.parse(request.intent),
                deviceClass = HomeRecommendationDeviceClass.parse(call.request.headers["User-Agent"]),
                now = java.time.LocalDateTime.now(),
            )
            call.respond(
                HttpStatusCode.Created,
                eventService.add(
                    userId = userId,
                    eventType = request.eventType,
                    videoUrl = request.videoUrl,
                    uploaderUrl = request.uploaderUrl,
                    title = request.title,
                    watchRatio = request.watchRatio,
                    watchDurationMs = request.watchDurationMs,
                    contextKey = contextKey,
                ),
            )
        }
    }
}
