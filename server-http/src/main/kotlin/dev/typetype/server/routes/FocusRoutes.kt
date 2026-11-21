package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.FocusSettingsRequest
import dev.typetype.server.models.FocusStudyChannelRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FocusService
import dev.typetype.server.services.FocusStudyChannelRemoveResult
import dev.typetype.server.services.FocusStudyChannelResult
import dev.typetype.server.services.FocusUpdateResult
import dev.typetype.server.services.HomeRecommendationWarmup
import dev.typetype.server.services.NoopHomeRecommendationWarmup
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.focusRoutes(
    focusService: FocusService,
    authService: AuthService,
    warmupService: HomeRecommendationWarmup = NoopHomeRecommendationWarmup,
) {
    get("/focus") {
        call.withJwtAuth(authService) { userId -> call.respond(focusService.get(userId)) }
    }
    put("/focus") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<FocusSettingsRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            when (val result = focusService.update(userId, request)) {
                is FocusUpdateResult.Success -> call.respond(result.item)
                FocusUpdateResult.InvalidGroup -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Choose an existing subscription group", "focus_invalid_group"),
                )
                FocusUpdateResult.InvalidDays -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Days must be between 1 and 365", "focus_invalid_days"),
                )
                FocusUpdateResult.InvalidSchedule -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Choose a schedule and a valid timezone", "focus_invalid_schedule"),
                )
            }
        }
    }
    post("/focus/study-channels") {
        call.withJwtAuth(authService) { userId ->
            val request = runCatching { call.receive<FocusStudyChannelRequest>() }.getOrElse {
                return@withJwtAuth call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
            }
            when (
                val result = focusService.addStudyChannel(
                    userId = userId,
                    rawChannelUrl = request.channelUrl,
                    rawName = request.name,
                    rawAvatarUrl = request.avatarUrl,
                )
            ) {
                is FocusStudyChannelResult.Success -> {
                    warmupService.invalidateAndWarm(userId)
                    call.respond(result.response)
                }
                FocusStudyChannelResult.InvalidChannel -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid channel URL", "focus_invalid_channel"),
                )
                FocusStudyChannelResult.NoStudyGroup -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Select a study group in Focus settings first", "focus_no_study_group"),
                )
                FocusStudyChannelResult.EntertainmentConflict -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(
                        "Entertainment channels cannot be added to the study group while Focus is active",
                        "focus_entertainment_conflict",
                    ),
                )
            }
        }
    }
    delete("/focus/study-channels") {
        call.withJwtAuth(authService) { userId ->
            val channelUrl = call.request.queryParameters["url"]
                ?: return@withJwtAuth call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Missing 'url' parameter", "focus_invalid_channel"),
                )
            when (focusService.removeStudyChannel(userId, channelUrl)) {
                FocusStudyChannelRemoveResult.Success -> {
                    warmupService.invalidateAndWarm(userId)
                    call.respond(HttpStatusCode.NoContent)
                }
                FocusStudyChannelRemoveResult.InvalidChannel -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid channel URL", "focus_invalid_channel"),
                )
                FocusStudyChannelRemoveResult.NoStudyGroup -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Select a study group in Focus settings first", "focus_no_study_group"),
                )
                FocusStudyChannelRemoveResult.NotFound -> call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Channel is not in the study group", "focus_study_channel_not_found"),
                )
            }
        }
    }
}
