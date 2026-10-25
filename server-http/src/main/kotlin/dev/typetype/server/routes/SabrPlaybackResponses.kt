package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SabrPlaybackSegmentResult
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.markServed
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal suspend fun ApplicationCall.respondSabrPreparation(
    sessionStore: SabrSessionStore,
    holder: SabrSessionHolder,
    videoId: String,
    startTimeMs: Long,
    ready: Boolean,
    retryAfterMs: Long,
): Unit {
    if (holder.terminalFailure() != null || holder.networkFailure() != null) {
        sessionStore.release(holder)
        return respondSabrFailure(SabrFailureResponse.of(holder))
    }
    respond(if (ready) HttpStatusCode.OK else HttpStatusCode.Accepted, holder.toPlaybackResponse(videoId, startTimeMs, ready, retryAfterMs))
}

internal suspend fun ApplicationCall.respondSabrSegment(result: SabrPlaybackSegmentResult, retryAfterMs: Long): Unit = when (result) {
    is SabrPlaybackSegmentResult.Ready -> respondSabrMediaBytes(result.mimeType, result.bytes)
    is SabrPlaybackSegmentResult.Stream -> respondSabrMediaStream(
        result.mimeType,
        result.segment.length.toLong(),
        result.segment::openStream,
    ) { result.holder.markServed(result.segment, result.generation) }
    is SabrPlaybackSegmentResult.Retry -> respond(
        HttpStatusCode.Accepted,
        result.holder.toRetryPlaybackResponse(result.status, retryAfterMs),
    )
    is SabrPlaybackSegmentResult.Stale -> respond(HttpStatusCode.Conflict, ErrorResponse("Stale SABR playback generation"))
    is SabrPlaybackSegmentResult.Failed -> respondSabrFailure(SabrFailureResponse.of(result.holder))
    SabrPlaybackSegmentResult.InvalidSequence -> respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid seq"))
    SabrPlaybackSegmentResult.InvalidGeneration -> respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid generation"))
}
