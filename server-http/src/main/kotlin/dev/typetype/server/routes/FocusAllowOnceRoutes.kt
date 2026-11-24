package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.FocusAllowOnceChallengeRequest
import dev.typetype.server.models.FocusAllowOnceVerifyRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FocusAllowOnceCancelResult
import dev.typetype.server.services.FocusAllowOnceChallengeResult
import dev.typetype.server.services.FocusAllowOnceVerifyResult
import dev.typetype.server.services.FocusService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post

internal fun Route.focusAllowOnceRoutes(
    focusService: FocusService,
    authService: AuthService,
): Unit {
    post("/focus/allow-once/challenge") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<FocusAllowOnceChallengeRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            when (val result = focusService.createAllowOnceChallenge(userId, request.videoUrl, request.timezone, request.kind)) {
                is FocusAllowOnceChallengeResult.Success -> call.respond(result.response)
                FocusAllowOnceChallengeResult.InvalidVideo -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid video URL", "focus_invalid_video"),
                )
                FocusAllowOnceChallengeResult.InvalidKind -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid allow-once kind", "focus_invalid_kind"),
                )
                FocusAllowOnceChallengeResult.NotEligible -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Strict Focus blocking is not active", "focus_allow_once_not_needed"),
                )
                FocusAllowOnceChallengeResult.Exhausted -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("Daily allow-once limit reached", "focus_allow_once_exhausted"),
                )
            }
        }
    }
    post("/focus/allow-once/verify") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<FocusAllowOnceVerifyRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            when (
                val result = focusService.verifyAllowOnceChallenge(
                    userId = userId,
                    challengeId = request.challengeId,
                    rawVideoUrl = request.videoUrl,
                    rawCode = request.code,
                    requestedTimezone = request.timezone,
                    rawKind = request.kind,
                )
            ) {
                is FocusAllowOnceVerifyResult.Success -> call.respond(result.response)
                FocusAllowOnceVerifyResult.InvalidVideo -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid video URL", "focus_invalid_video"),
                )
                FocusAllowOnceVerifyResult.InvalidKind -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid allow-once kind", "focus_invalid_kind"),
                )
                FocusAllowOnceVerifyResult.InvalidChallenge -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Verification code is incorrect or expired", "focus_invalid_captcha"),
                )
                FocusAllowOnceVerifyResult.NotEligible -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Strict Focus blocking is not active", "focus_allow_once_not_needed"),
                )
                FocusAllowOnceVerifyResult.Exhausted -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("Daily allow-once limit reached", "focus_allow_once_exhausted"),
                )
            }
        }
    }
    delete("/focus/allow-once/pending") {
        call.withJwtAuth(authService) { userId ->
            val videoUrl = call.request.queryParameters["url"]
                ?: return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Missing 'url' parameter", "focus_invalid_video"),
                )
            val timezone = call.request.queryParameters["timezone"]
            when (val result = focusService.cancelPendingAllowOnce(userId, videoUrl, timezone)) {
                is FocusAllowOnceCancelResult.Success -> call.respond(result.response)
                FocusAllowOnceCancelResult.InvalidVideo -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid video URL", "focus_invalid_video"),
                )
                FocusAllowOnceCancelResult.NotFound -> call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("No pending allow-once cooling-off grant found", "focus_allow_once_pending_not_found"),
                )
            }
        }
    }
}
