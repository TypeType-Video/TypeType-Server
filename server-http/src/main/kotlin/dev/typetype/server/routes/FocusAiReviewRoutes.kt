package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.FocusAiReviewRequest
import dev.typetype.server.models.FocusAiReviewResponse
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FocusAiReviewResult
import dev.typetype.server.services.FocusService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

internal fun Route.focusAiReviewRoutes(
    focusService: FocusService,
    authService: AuthService,
): Unit {
    post("/focus/ai-review") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<FocusAiReviewRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            when (
                val result = focusService.reviewWithAi(
                    userId = userId,
                    rawVideoUrl = request.videoUrl,
                    rawReason = request.reason,
                    requestedTimezone = request.timezone,
                )
            ) {
                is FocusAiReviewResult.Approved -> call.respond(
                    FocusAiReviewResponse(status = "approved", expiresAt = result.expiresAt),
                )
                is FocusAiReviewResult.Rejected -> call.respond(
                    FocusAiReviewResponse(
                        status = "rejected",
                        cooldownMs = result.cooldownMs,
                        rejectionExpiresAt = result.rejectionExpiresAt,
                    ),
                )
                FocusAiReviewResult.Unavailable -> call.respond(
                    FocusAiReviewResponse(status = "unavailable"),
                )
                is FocusAiReviewResult.CooldownActive -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("AI review cooldown active", "focus_ai_rejection_cooldown"),
                )
                FocusAiReviewResult.InvalidVideo -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid video URL", "focus_invalid_video"),
                )
                FocusAiReviewResult.InvalidReason -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Provide a valid reason", "focus_ai_invalid_reason"),
                )
                FocusAiReviewResult.NotEligible,
                FocusAiReviewResult.NotNeeded,
                -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Video is not eligible for Focus AI review", "focus_ai_not_eligible"),
                )
            }
        }
    }
}
