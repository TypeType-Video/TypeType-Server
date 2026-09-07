package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest

internal fun SabrSessionHolder.resolveSegmentDemand(
    request: SabrSegmentRequest,
    identity: String,
    onResolved: (SabrMediaSegment) -> Unit = {},
): Boolean {
    val requested = session.getCachedSegment(request)
    val live = livePlaybackSnapshot()?.active == true
    val rebased = if (requested != null) null else session.findCachedMediaAt(
        format = request.format,
        targetMs = playbackSegmentStartMs(request.format, request.sequenceNumber),
        predictedSequence = request.sequenceNumber,
        fallbackDurationMs = if (live) playbackSegmentDurationMs(request.format, request.sequenceNumber) else null,
        allowFollowing = live,
    )
    val resolved = requested ?: rebased ?: return false
    if (!clearSegmentDemand(request, identity)) return false
    observeMediaSegment(resolved)
    onResolved(resolved)
    if (rebased != null) session.streamState.jumpBufferedTo(request.format, rebased.header.sequenceNumber)
    return true
}
