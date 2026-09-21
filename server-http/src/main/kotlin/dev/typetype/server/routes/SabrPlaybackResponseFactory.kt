package dev.typetype.server.routes

import dev.typetype.server.services.SabrSessionHolder
import dev.typetype.server.services.livePlaybackSnapshot
import dev.typetype.server.services.liveRetryAfterMs

internal fun SabrSessionHolder.toPlaybackResponse(
    videoId: String,
    startTimeMs: Long,
    ready: Boolean,
    retryAfterMs: Long?,
): SabrPlaybackResponse = SabrPlaybackResponse(
    sessionId = sessionToken,
    videoId = videoId,
    manifestUrl = if (ready) SabrPlaybackPaths.manifestPath(sessionToken) else null,
    videoItag = videoFormat.itag,
    audioItag = audioFormat.itag,
    audioTrackId = audioFormat.audioTrackId,
    startTimeMs = startTimeMs,
    generation = activeGeneration(),
    ready = ready,
    status = if (ready) "ready" else playbackState().name.lowercase(),
    retryAfterMs = if (ready) null else retryAfterMs,
    live = livePlaybackSnapshot()?.toResponse(),
)

internal fun SabrSessionHolder.toRetryPlaybackResponse(status: String, retryAfterMs: Long): SabrPlaybackResponse =
    SabrPlaybackResponse(
        sessionId = sessionToken,
        videoId = key.videoId,
        manifestUrl = null,
        videoItag = videoFormat.itag,
        audioItag = audioFormat.itag,
        audioTrackId = audioFormat.audioTrackId,
        startTimeMs = playerTimeMs(),
        generation = activeGeneration(),
        ready = false,
        status = status,
        retryAfterMs = if (livePlaybackSnapshot()?.active == true) liveRetryAfterMs() else retryAfterMs,
        live = livePlaybackSnapshot()?.toResponse(),
    )

internal fun dev.typetype.server.services.SabrLivePlaybackSnapshot.toResponse(): SabrLivePlaybackResponse =
    SabrLivePlaybackResponse(
        active = active,
        postLiveDvr = postLiveDvr,
        headSequence = headSequence,
        headTimeMs = headTimeMs,
        seekableStartMs = seekableStartMs,
        seekableEndMs = seekableEndMs,
        atLiveEdge = atLiveEdge,
        targetLatencyMs = targetLatencyMs,
    )
