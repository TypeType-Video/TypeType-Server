package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import io.ktor.http.HttpStatusCode

internal fun youtubeExtractionFailureStatus(failure: ExtractionResult.Failure): HttpStatusCode =
    when (failure.kind) {
        ExtractionFailureKind.LiveEventNotStarted,
        ExtractionFailureKind.ScheduledPremiere -> HttpStatusCode.Conflict
        ExtractionFailureKind.ContentUnavailable -> HttpStatusCode.NotFound
        ExtractionFailureKind.ProviderAccessBlocked -> HttpStatusCode.ServiceUnavailable
        ExtractionFailureKind.Unknown,
        ExtractionFailureKind.YoutubeSessionRejected -> HttpStatusCode.BadGateway
    }

internal fun youtubeBadRequestStatus(code: String): HttpStatusCode = when (code) {
    "age_restricted",
    "geographic_restriction",
    "members_only",
    "paid_content",
    "private_content" -> HttpStatusCode.Forbidden
    else -> HttpStatusCode.BadRequest
}
