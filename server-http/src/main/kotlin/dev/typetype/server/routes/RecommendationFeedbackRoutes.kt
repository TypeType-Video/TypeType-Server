package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.RecommendationFeedbackRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.RecommendationFeedbackService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.recommendationFeedbackRoutes(feedbackService: RecommendationFeedbackService, authService: AuthService) {
    get("/recommendations/feedback") {
        call.withJwtAuth(authService) { userId ->
            call.respond(feedbackService.getAll(userId))
        }
    }
    post("/recommendations/feedback") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<RecommendationFeedbackRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            if (request.feedbackType !in setOf("not_interested", "less_from_channel")) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid feedbackType"))
            }
            if (request.feedbackType == "not_interested" && request.videoUrl.isNullOrBlank()) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoUrl"))
            }
            if (request.feedbackType == "less_from_channel" && request.uploaderUrl.isNullOrBlank()) {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing uploaderUrl"))
            }
            call.respond(
                HttpStatusCode.Created,
                feedbackService.add(
                    userId = userId,
                    feedbackType = request.feedbackType,
                    videoUrl = request.videoUrl,
                    uploaderUrl = request.uploaderUrl,
                ),
            )
        }
    }
}
