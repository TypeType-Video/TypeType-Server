package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.FocusSettingsRequest
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FocusService
import dev.typetype.server.services.FocusUpdateResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.focusRoutes(focusService: FocusService, authService: AuthService) {
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
}
