package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall

internal suspend fun ApplicationCall.respondNoPlayableStreams(upstreamOutage: Boolean): Unit = respondTransientAware(
    if (upstreamOutage) HttpStatusCode.ServiceUnavailable else HttpStatusCode.UnprocessableEntity,
    ErrorResponse("No compatible stream is available for this video", "no_playable_streams"),
    SABR_PREPARATION_RETRY_AFTER_SECONDS,
)

// Not an overload: the token service answers but cannot get tokens from YouTube, which is most often a network
// problem of the token container (DNS, firewall, proxy). Clients show what to check instead of "busy".
internal suspend fun ApplicationCall.respondTokenServiceUnavailable(): Unit = respondTransientAware(
    HttpStatusCode.ServiceUnavailable,
    ErrorResponse("The token service could not get a YouTube token", "token_service_unavailable"),
    SABR_PREPARATION_RETRY_AFTER_SECONDS,
)

internal suspend fun ApplicationCall.respondMediaHandleFailure(error: Exception): Unit = respondTransientAware(
    if (error is IllegalArgumentException) HttpStatusCode.UnprocessableEntity else HttpStatusCode.ServiceUnavailable,
    ErrorResponse(error.message ?: "Provider media handle service failed", "media_handle_unavailable"),
    MEDIA_HANDLE_RETRY_AFTER_SECONDS,
)

private const val SABR_PREPARATION_RETRY_AFTER_SECONDS = 15L
private const val MEDIA_HANDLE_RETRY_AFTER_SECONDS = 15L
