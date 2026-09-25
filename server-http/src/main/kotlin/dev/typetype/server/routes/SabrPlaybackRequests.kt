package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive

internal suspend fun ApplicationCall.playbackRequest(): SabrPlaybackRequest {
    val body = runCatching { receive<SabrPlaybackRequest>() }.getOrNull()
    return SabrPlaybackRequest(
        videoItag = body?.videoItag ?: request.queryParameters["videoItag"]?.toIntOrNull(),
        audioItag = body?.audioItag ?: request.queryParameters["audioItag"]?.toIntOrNull(),
        audioTrackId = body?.audioTrackId ?: request.queryParameters["audioTrackId"],
        startTimeMs = body?.startTimeMs ?: request.queryParameters["startTimeMs"]?.toLongOrNull(),
        playerTimeMs = body?.playerTimeMs ?: request.queryParameters["playerTimeMs"]?.toLongOrNull(),
        audioOnly = body?.audioOnly ?: request.queryParameters["audioOnly"]?.toBooleanStrictOrNull() ?: false,
        isLive = body?.isLive ?: request.queryParameters["isLive"]?.toBooleanStrictOrNull() ?: false,
    )
}

internal fun SabrPlaybackRequest.effectiveStartTimeMs(): Long =
    (playerTimeMs ?: startTimeMs ?: 0L).coerceAtLeast(0L)

internal fun SabrPlaybackRequest.keepsCurrentFormats(holder: SabrSessionHolder): Boolean =
    (videoItag == null || videoItag == holder.videoFormat.itag) &&
        (audioItag == null || audioItag == holder.audioFormat.itag) &&
        (audioTrackId.isNullOrBlank() || audioTrackId == holder.audioFormat.audioTrackId)
