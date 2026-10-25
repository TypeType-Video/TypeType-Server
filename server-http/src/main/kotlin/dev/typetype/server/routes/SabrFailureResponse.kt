package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SabrFailureKind
import dev.typetype.server.services.SabrSessionHolder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond

internal data class SabrFailureResponse(
    val status: HttpStatusCode,
    val body: ErrorResponse,
    val retryAfterSeconds: Long? = null,
) {
    companion object {
        fun of(holder: SabrSessionHolder): SabrFailureResponse = of(
            holder.playbackStatus.terminalFailureKind()
                ?: if (holder.networkFailure() != null) SabrFailureKind.UPSTREAM_TIMEOUT else SabrFailureKind.UNKNOWN,
        )

        fun of(kind: SabrFailureKind, retryAfterSeconds: Long? = null): SabrFailureResponse = when (kind) {
            SabrFailureKind.UPSTREAM_REFUSED -> unavailable(
                "YouTube refused this playback session",
                "sabr_upstream_refused",
                retryAfterSeconds ?: UPSTREAM_REFUSED_RETRY_SECONDS,
            )
            SabrFailureKind.PROVIDER_SESSION_CHANGED -> unavailable(
                "The YouTube session changed, create a new playback session",
                "sabr_session_expired",
                retryAfterSeconds ?: SESSION_RETRY_SECONDS,
            )
            SabrFailureKind.UPSTREAM_TIMEOUT -> unavailable(
                "YouTube did not answer in time",
                "sabr_upstream_timeout",
                retryAfterSeconds ?: TRANSIENT_RETRY_SECONDS,
            )
            SabrFailureKind.UNKNOWN -> unavailable(
                "SABR playback session failed",
                "sabr_session_failed",
                retryAfterSeconds ?: TRANSIENT_RETRY_SECONDS,
            )
            SabrFailureKind.CONTENT_UNAVAILABLE -> SabrFailureResponse(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("SABR playback is not available for this video", "sabr_content_unavailable"),
            )
        }

        private fun unavailable(message: String, code: String, retryAfterSeconds: Long): SabrFailureResponse =
            SabrFailureResponse(HttpStatusCode.ServiceUnavailable, ErrorResponse(message, code), retryAfterSeconds)

        private const val UPSTREAM_REFUSED_RETRY_SECONDS = 30L
        private const val SESSION_RETRY_SECONDS = 2L
        private const val TRANSIENT_RETRY_SECONDS = 5L
    }
}

internal suspend fun ApplicationCall.respondSabrFailure(failure: SabrFailureResponse): Unit {
    failure.retryAfterSeconds?.let { response.header(HttpHeaders.RetryAfter, it.toString()) }
    respond(failure.status, failure.body)
}
