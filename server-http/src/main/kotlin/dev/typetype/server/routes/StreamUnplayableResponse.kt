package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall

internal suspend fun ApplicationCall.respondNoPlayableStreams(upstreamOutage: Boolean): Unit = respondTransientAware(
    if (upstreamOutage) HttpStatusCode.ServiceUnavailable else HttpStatusCode.UnprocessableEntity,
    ErrorResponse("No compatible stream is available for this video", "no_playable_streams"),
    SABR_PREPARATION_RETRY_AFTER_SECONDS,
)

internal suspend fun ApplicationCall.respondMediaHandleFailure(error: Exception): Unit = respondTransientAware(
    if (error is IllegalArgumentException) HttpStatusCode.UnprocessableEntity else HttpStatusCode.ServiceUnavailable,
    ErrorResponse(error.message ?: "Provider media handle service failed", "media_handle_unavailable"),
    MEDIA_HANDLE_RETRY_AFTER_SECONDS,
)

private const val SABR_PREPARATION_RETRY_AFTER_SECONDS = 15L
private const val MEDIA_HANDLE_RETRY_AFTER_SECONDS = 15L
