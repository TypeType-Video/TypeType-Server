package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.SabrSessionStore
import dev.typetype.server.services.pendingSegmentDemandSummary
import dev.typetype.server.services.livePlaybackSnapshot
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import dev.typetype.server.sabr.SabrSegmentRequest

internal class SabrPlaybackStateHandler(private val sabrSessionStore: SabrSessionStore) {
    suspend fun get(call: ApplicationCall, sessionId: String) {
        val holder = sabrSessionStore.lookupByToken(sessionId)
            ?: return call.respond(HttpStatusCode.NotFound, ErrorResponse("No active SABR playback session"))
        call.respond(holder.toStateResponse())
    }

    private fun SabrSessionHolder.toStateResponse(): SabrPlaybackStateResponse = SabrPlaybackStateResponse(
        sessionId = sessionToken,
        videoId = key.videoId,
        state = playbackState().name.lowercase(),
        videoItag = videoFormat.itag,
        audioItag = audioFormat.itag,
        audioTrackId = audioFormat.audioTrackId,
        generation = activeGeneration(),
        requestedSeekTimeMs = requestedSeekTimeMs(),
        playerTimeMs = playerTimeMs(),
        readerHeadMs = readerHeadMs(),
        readerTailMs = readerTailMs(),
        bufferedEdgeMs = session.streamState.getMinBufferedEndMs(),
        cachedBytes = session.cachedBytes,
        requestNumber = session.requestNumber,
        pendingRefetch = pendingRefetchRequest()?.summary(),
        pendingForwardSeek = pendingForwardSeekRequest()?.summary(),
        pendingSegmentDemand = pendingSegmentDemandSummary(),
        terminalError = terminalFailure() ?: networkFailure(),
        diagnosticTrace = session.diagnosticTrace,
        live = livePlaybackSnapshot()?.toResponse(),
    )

    private fun SabrSegmentRequest.summary(): String = "${format.itag}:$sequenceNumber"
}
