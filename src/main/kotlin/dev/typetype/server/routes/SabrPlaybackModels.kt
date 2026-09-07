package dev.typetype.server.routes

import kotlinx.serialization.Serializable
import dev.typetype.server.sabr.SabrSegmentRequest

@Serializable
internal data class SabrPlaybackRequest(
    val videoItag: Int? = null,
    val audioItag: Int? = null,
    val audioTrackId: String? = null,
    val startTimeMs: Long? = null,
    val playerTimeMs: Long? = null,
    val audioOnly: Boolean = false,
    val isLive: Boolean = false,
)

@Serializable
internal data class SabrLivePlaybackResponse(
    val active: Boolean,
    val postLiveDvr: Boolean,
    val headSequence: Long,
    val headTimeMs: Long,
    val seekableStartMs: Long,
    val seekableEndMs: Long,
    val atLiveEdge: Boolean,
    val targetLatencyMs: Long,
)

@Serializable
internal data class SabrPlaybackResponse(
    val sessionId: String,
    val videoId: String,
    val manifestUrl: String? = null,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String? = null,
    val startTimeMs: Long,
    val generation: Long,
    val ready: Boolean,
    val status: String,
    val retryAfterMs: Long? = null,
    val live: SabrLivePlaybackResponse? = null,
)

@Serializable
internal data class SabrPlaybackStateResponse(
    val sessionId: String,
    val videoId: String,
    val state: String,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String? = null,
    val generation: Long,
    val requestedSeekTimeMs: Long? = null,
    val playerTimeMs: Long,
    val readerHeadMs: Long,
    val readerTailMs: Long,
    val bufferedEdgeMs: Long,
    val cachedBytes: Long,
    val requestNumber: Int,
    val pendingRefetch: String? = null,
    val pendingForwardSeek: String? = null,
    val pendingSegmentDemand: String? = null,
    val terminalError: String? = null,
    val diagnosticTrace: String? = null,
    val live: SabrLivePlaybackResponse? = null,
)

@Serializable
internal data class SabrPlaybackWindowRequest(
    val generation: Long,
    val playerTimeMs: Long,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String? = null,
    val playbackRate: Float = 1.0f,
    val bufferGoalMs: Long = 30_000L,
    val backBufferMs: Long = 30_000L,
    val bufferedRanges: List<SabrPlaybackBufferedRange> = emptyList(),
    val audioOnly: Boolean = false,
)

@Serializable
internal data class SabrPlaybackPositionRequest(
    val generation: Long,
    val playerTimeMs: Long,
    val videoItag: Int,
    val audioItag: Int,
    val audioTrackId: String? = null,
    val playbackRate: Float = 1.0f,
    val bufferedRanges: List<SabrPlaybackBufferedRange> = emptyList(),
    val audioOnly: Boolean = false,
)

@Serializable
internal data class SabrPlaybackBufferedRange(
    val itag: Int,
    val startMs: Long,
    val endMs: Long,
    val startSequence: Int? = null,
    val endSequence: Int? = null,
)

@Serializable
internal data class SabrPlaybackPositionResponse(
    val sessionId: String,
    val generation: Long,
    val playerTimeMs: Long,
    val readerHeadMs: Long,
    val readerTailMs: Long,
    val bufferedEdgeMs: Long,
    val live: SabrLivePlaybackResponse? = null,
)

@Serializable
internal data class SabrPlaybackPrefetchResponse(
    val sessionId: String,
    val generation: Long,
    val ready: Boolean,
    val retryAfterMs: Long?,
    val status: String,
    val segmentsUrl: String,
    val stateUrl: String,
    val blockedBy: String?,
    val playerTimeMs: Long,
    val readerHeadMs: Long,
    val readerTailMs: Long,
    val bufferedEdgeMs: Long,
    val pendingRefetch: String? = null,
    val pendingForwardSeek: String? = null,
    val pendingSegmentDemand: String? = null,
    val terminalError: String? = null,
    val recoveryAction: String? = null,
    val retryVideoItags: List<Int> = emptyList(),
    val live: SabrLivePlaybackResponse? = null,
)

@Serializable
internal data class SabrPlaybackWindowReadyResponse(
    val sessionId: String,
    val generation: Long,
    val ready: Boolean,
    val retryAfterMs: Long?,
    val durationMs: Long,
    val endOfStream: Boolean,
    val audio: SabrPlaybackWindowTrack,
    val video: SabrPlaybackWindowTrack? = null,
    val startTimeMs: Long = 0L,
    val live: SabrLivePlaybackResponse? = null,
)

@Serializable
internal data class SabrPlaybackWindowTrack(
    val mime: String,
    val initUrl: String,
    val segments: List<SabrPlaybackWindowSegment>,
)

@Serializable
internal data class SabrPlaybackWindowSegment(
    val url: String,
    val startMs: Long,
    val durationMs: Long,
)

@Serializable
internal data class SabrPlaybackWindowPreparingResponse(
    val sessionId: String,
    val generation: Long,
    val ready: Boolean,
    val retryAfterMs: Long,
    val status: String,
    val blockedBy: String,
    val playerTimeMs: Long,
    val readerHeadMs: Long,
    val readerTailMs: Long,
    val bufferedEdgeMs: Long,
    val pendingRefetch: String? = null,
    val pendingForwardSeek: String? = null,
    val pendingSegmentDemand: String? = null,
    val terminalError: String? = null,
    val recoveryAction: String? = null,
    val retryVideoItags: List<Int> = emptyList(),
    val live: SabrLivePlaybackResponse? = null,
)

internal data class SabrPlaybackWindowBuildResult(
    val response: SabrPlaybackWindowReadyResponse,
    val blockedBy: String?,
    val blockedRequests: List<SabrSegmentRequest>,
    val isReady: Boolean,
)
