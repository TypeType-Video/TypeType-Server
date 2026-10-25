package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal fun extractionFailureStatus(failure: ExtractionResult.Failure, youtube: Boolean): HttpStatusCode =
    when (failure.kind) {
        ExtractionFailureKind.ProviderAccessBlocked -> HttpStatusCode.ServiceUnavailable
        ExtractionFailureKind.RateLimited -> HttpStatusCode.TooManyRequests
        ExtractionFailureKind.UpstreamTimeout -> HttpStatusCode.GatewayTimeout
        ExtractionFailureKind.Unavailable -> HttpStatusCode.UnprocessableEntity
        ExtractionFailureKind.LiveEventNotStarted,
        ExtractionFailureKind.ScheduledPremiere -> if (youtube) HttpStatusCode.Conflict else HttpStatusCode.UnprocessableEntity
        ExtractionFailureKind.ContentUnavailable -> if (youtube) HttpStatusCode.NotFound else HttpStatusCode.UnprocessableEntity
        ExtractionFailureKind.Unknown,
        ExtractionFailureKind.YoutubeSessionRejected -> if (youtube) HttpStatusCode.BadGateway else HttpStatusCode.UnprocessableEntity
    }

internal fun extractionBadRequestStatus(code: String, youtube: Boolean): HttpStatusCode = when (code) {
    in CONTENT_RESTRICTION_CODES -> if (youtube) HttpStatusCode.Forbidden else HttpStatusCode.UnprocessableEntity
    else -> HttpStatusCode.BadRequest
}

internal suspend fun ApplicationCall.respondExtractionFailure(failure: ExtractionResult.Failure, youtube: Boolean): Unit =
    respondTransientAware(
        extractionFailureStatus(failure, youtube),
        ErrorResponse(failure.message, failure.code),
        failure.retryAfterSeconds,
    )

internal suspend fun ApplicationCall.respondExtractionBadRequest(result: ExtractionResult.BadRequest, youtube: Boolean): Unit =
    respond(extractionBadRequestStatus(result.code, youtube), ErrorResponse(result.message, result.code))

internal suspend fun ApplicationCall.respondTransientAware(
    status: HttpStatusCode,
    body: ErrorResponse,
    retryAfterSeconds: Long? = null,
): Unit {
    if (status == HttpStatusCode.TooManyRequests || status == HttpStatusCode.ServiceUnavailable) {
        response.headers.append(HttpHeaders.RetryAfter, (retryAfterSeconds ?: DEFAULT_RETRY_AFTER_SECONDS).toString())
    }
    if (status == HttpStatusCode.TooManyRequests) preserveTooManyRequestsBody()
    respond(status, body)
}

private val CONTENT_RESTRICTION_CODES = setOf(
    "age_restricted",
    "geographic_restriction",
    "members_only",
    "paid_content",
    "private_content",
)

private const val DEFAULT_RETRY_AFTER_SECONDS = 60L
