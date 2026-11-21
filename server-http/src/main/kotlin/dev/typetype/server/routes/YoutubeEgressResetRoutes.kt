package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.YoutubeEgressResetRequest
import dev.typetype.server.services.YoutubeEgressResetResult
import dev.typetype.server.services.YoutubeEgressResetService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json

fun Route.youtubeEgressResetRoutes(service: YoutubeEgressResetService): Unit {
    post("/internal/provider/youtube/reset") {
        if (!service.isAuthorized(call.internalResetToken())) {
            return@post call.respond(HttpStatusCode.NotFound)
        }
        when (val result = service.reset(call.parseResetReason())) {
            is YoutubeEgressResetResult.Completed -> call.respond(HttpStatusCode.OK, result.response)
            is YoutubeEgressResetResult.Throttled -> {
                call.response.headers.append(HttpHeaders.RetryAfter, result.retryAfterSeconds.toString())
                call.preserveTooManyRequestsBody()
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("YouTube egress reset is in cooldown", "egress_reset_cooldown"),
                )
            }
        }
    }
}

private fun ApplicationCall.internalResetToken(): String? =
    request.headers[INTERNAL_HEADER]
        ?: request.headers[HttpHeaders.Authorization]
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)

private suspend fun ApplicationCall.parseResetReason(): String {
    val body = receiveText().trim()
    if (body.isEmpty()) return YoutubeEgressResetService.DEFAULT_REASON
    return runCatching { leniencyJson.decodeFromString<YoutubeEgressResetRequest>(body).reason }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: YoutubeEgressResetService.DEFAULT_REASON
}

private const val INTERNAL_HEADER = "X-Internal-Token"
private val leniencyJson = Json { ignoreUnknownKeys = true }
