package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.FocusAllowOnceChallengeRequest
import dev.typetype.server.models.FocusAllowOnceVerifyRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FocusAllowOnceChallengeResult
import dev.typetype.server.services.FocusAllowOnceVerifyResult
import dev.typetype.server.services.FocusService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
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
            when (val result = focusService.createAllowOnceChallenge(userId, request.videoUrl, request.timezone)) {
                is FocusAllowOnceChallengeResult.Success -> call.respond(result.response)
                FocusAllowOnceChallengeResult.InvalidVideo -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid video URL", "focus_invalid_video"),
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
                )
            ) {
                is FocusAllowOnceVerifyResult.Success -> call.respond(result.response)
                FocusAllowOnceVerifyResult.InvalidVideo -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid video URL", "focus_invalid_video"),
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
}
