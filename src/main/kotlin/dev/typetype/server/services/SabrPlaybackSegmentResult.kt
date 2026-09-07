package dev.typetype.server.services

import dev.typetype.server.sabr.SabrMediaSegment

internal sealed class SabrPlaybackSegmentResult {
    data class Ready(val mimeType: String, val bytes: ByteArray) : SabrPlaybackSegmentResult()
    data class Stream(
        val mimeType: String,
        val segment: SabrMediaSegment,
        val holder: SabrSessionHolder,
        val generation: Long,
    ) : SabrPlaybackSegmentResult()
    data class Retry(val holder: SabrSessionHolder, val status: String) : SabrPlaybackSegmentResult()
    data class Stale(val holder: SabrSessionHolder) : SabrPlaybackSegmentResult()
    data object InvalidSequence : SabrPlaybackSegmentResult()
    data object InvalidGeneration : SabrPlaybackSegmentResult()
}
