package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.routes.preserveTooManyRequestsBody
import dev.typetype.server.services.YouTubeSubtitleContentResult
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes

internal suspend fun ApplicationCall.respondYouTubeSubtitle(result: YouTubeSubtitleContentResult) {
    when (result) {
        is YouTubeSubtitleContentResult.Ready -> {
            response.headers.append(
                HttpHeaders.CacheControl,
                if (result.isLive) LIVE_CACHE_CONTROL else VOD_CACHE_CONTROL,
                safeOnly = false,
            )
            respondBytes(result.content, ContentType.parse(result.format.contentType))
        }
        YouTubeSubtitleContentResult.InvalidRequest -> respondSubtitleError(
            HttpStatusCode.BadRequest,
            "Invalid YouTube subtitle URL",
            "subtitle_request_invalid",
        )
        YouTubeSubtitleContentResult.NotFound -> respondSubtitleError(
            HttpStatusCode.NotFound,
            "Subtitle track not found",
            "subtitle_track_not_found",
        )
        YouTubeSubtitleContentResult.Throttled -> respondSubtitleError(
            HttpStatusCode.TooManyRequests,
            "YouTube temporarily throttled subtitle retrieval",
            "subtitle_upstream_throttled",
        )
        YouTubeSubtitleContentResult.Expired -> respondSubtitleError(
            HttpStatusCode.BadGateway,
            "YouTube subtitle URL expired after refresh",
            "subtitle_url_expired",
        )
        YouTubeSubtitleContentResult.InvalidPayload -> respondSubtitleError(
            HttpStatusCode.BadGateway,
            "YouTube returned invalid subtitle content",
            "subtitle_payload_invalid",
        )
        YouTubeSubtitleContentResult.Unavailable -> respondSubtitleError(
            HttpStatusCode.BadGateway,
            "YouTube subtitle retrieval failed",
            "subtitle_upstream_unavailable",
        )
    }
}

internal suspend fun ApplicationCall.respondYouTubeSubtitleInvalidRequest() = respondSubtitleError(
    HttpStatusCode.BadRequest,
    "Invalid YouTube subtitle request",
    "subtitle_request_invalid",
)

private suspend fun ApplicationCall.respondSubtitleError(
    status: HttpStatusCode,
    message: String,
    code: String,
) {
    if (status == HttpStatusCode.TooManyRequests) preserveTooManyRequestsBody()
    respond(status, ErrorResponse(message, code))
}

private const val VOD_CACHE_CONTROL = "public, max-age=21600, stale-while-revalidate=3600"
private const val LIVE_CACHE_CONTROL = "no-store"
