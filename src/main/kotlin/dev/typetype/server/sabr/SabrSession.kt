package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrPoTokenProvider as PipeProvider
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo as PipeInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession as PipeSession
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState as PipeState

internal class YoutubeSabrSession(
    info: YoutubeSabrInfo,
    audioFormat: YoutubeSabrFormat,
    videoFormat: YoutubeSabrFormat,
    poTokenProvider: SabrPoTokenProvider,
) {
    private val delegate = PipeSession(
        info.delegate,
        audioFormat.delegate,
        videoFormat.delegate,
        object : PipeProvider {
            override fun getPoToken(pipeInfo: PipeInfo, pipeState: PipeState): ByteArray? =
                poTokenProvider.getPoToken(YoutubeSabrInfo(pipeInfo), YoutubeSabrStreamState.fromDelegate(pipeState))
        },
    )
    val streamState: YoutubeSabrStreamState = YoutubeSabrStreamState.fromDelegate(delegate.streamState)

    fun fetchSegment(request: SabrSegmentRequest, localization: Localization): SabrMediaSegment? =
        SabrMediaSegment.fromDelegate(delegate.fetchSegment(request.delegate, localization))

    fun addDiagnosticEvent(event: String): Unit = delegate.addDiagnosticEvent(event)
    val diagnosticTrace: String get() = delegate.diagnosticTrace
    fun pumpOnce(localization: Localization): List<SabrMediaSegment> =
        delegate.pumpOnce(localization).map(SabrMediaSegment::fromDelegate)
    fun pumpOnceStreaming(localization: Localization): Int = delegate.pumpOnceStreaming(localization)
    fun pumpOnceStreamingForStartup(localization: Localization): Int =
        delegate.pumpOnceStreamingForStartup(localization)
    fun pumpOnceStreamingUntilCached(localization: Localization, target: SabrSegmentRequest): Int =
        delegate.pumpOnceStreamingUntilCached(localization, target.delegate)
    fun pumpOnceStreamingForDemand(localization: Localization, target: SabrSegmentRequest): DemandResponseResult =
        delegate.pumpOnceStreamingForDemand(localization, target.delegate).let(::DemandResponseResult)
    val demandBackoffRemainingMs: Long get() = delegate.demandBackoffRemainingMs
    val mediaProgressVersion: Long get() = delegate.mediaProgressVersion
    fun setPlayHeadMs(value: Long): Unit = delegate.setPlayHeadMs(value)
    val cachedBytes: Long get() = delegate.cachedBytes
    val peakCachedBytes: Long get() = delegate.peakCachedBytes
    val totalResponseBytes: Long get() = delegate.totalResponseBytes
    val maxResponseBytes: Long get() = delegate.maxResponseBytes
    val maxUmpPartBytes: Long get() = delegate.maxUmpPartBytes
    val maxMediaPartPayloadBytes: Long get() = delegate.maxMediaPartPayloadBytes
    val maxSegmentBytes: Long get() = delegate.maxSegmentBytes
    val maxSegmentsPerResponse: Int get() = delegate.maxSegmentsPerResponse
    val maxStreamProtectionStatus: Int get() = delegate.maxStreamProtectionStatus
    val memoryDiagnosticSummary: String get() = delegate.memoryDiagnosticSummary
    fun clearCache(): Unit = delegate.clearCache()
    fun evictPlayed(): Unit = delegate.evictPlayed()
    fun getCachedSegment(request: SabrSegmentRequest): SabrMediaSegment? =
        delegate.getCachedSegment(request.delegate)?.let(SabrMediaSegment::fromDelegate)
    fun getReadableSegment(request: SabrSegmentRequest): SabrMediaSegment? =
        delegate.getReadableSegment(request.delegate)?.let(SabrMediaSegment::fromDelegate)
    fun awaitCachedSegment(request: SabrSegmentRequest, timeoutMs: Long): SabrMediaSegment? =
        delegate.awaitCachedSegment(request.delegate, timeoutMs)?.let(SabrMediaSegment::fromDelegate)
    fun awaitReadableSegment(request: SabrSegmentRequest, timeoutMs: Long): SabrMediaSegment? =
        delegate.awaitReadableSegment(request.delegate, timeoutMs)?.let(SabrMediaSegment::fromDelegate)
    fun discardCachedSegment(request: SabrSegmentRequest): Unit = delegate.discardCachedSegment(request.delegate)
    fun setTraceEnabled(value: Boolean): Unit = delegate.setTraceEnabled(value)
    fun isBeyondEnd(request: SabrSegmentRequest): Boolean = delegate.isBeyondEnd(request.delegate)
    val isComplete: Boolean get() = delegate.isComplete
    val isLive: Boolean get() = delegate.isLive
    val liveHeadSequenceNumber: Long get() = delegate.liveHeadSequenceNumber
    val isAtLiveEdge: Boolean get() = delegate.isAtLiveEdge
    val requestNumber: Int get() = delegate.requestNumber
    val sessionPolicyTranscript: List<String> get() = delegate.sessionPolicyTranscript
    fun prepareForMediaSegment(request: SabrSegmentRequest): Unit = delegate.prepareForMediaSegment(request.delegate)
    fun prepareForInitialization(format: YoutubeSabrFormat): Unit = delegate.prepareForInitialization(format.delegate)
    fun bootstrapInitialization(localization: Localization): Unit = delegate.bootstrapInitialization(localization)
    fun fetchInitializationData(format: YoutubeSabrFormat, localization: Localization, timeoutMs: Long, poToken: ByteArray): ByteArray =
        delegate.fetchInitializationData(format.delegate, localization, timeoutMs, poToken)
    fun prepareForRewind(request: SabrSegmentRequest): Unit = delegate.prepareForRewind(request.delegate)
    fun prepareForRewind(request: SabrSegmentRequest, value: Long): Unit = delegate.prepareForRewind(request.delegate, value)
    fun prepareForForwardJump(request: SabrSegmentRequest): Unit = delegate.prepareForForwardJump(request.delegate)
    fun prepareForForwardJump(request: SabrSegmentRequest, value: Long): Unit = delegate.prepareForForwardJump(request.delegate, value)
    fun prepareForMissingSegment(request: SabrSegmentRequest): Unit = delegate.prepareForMissingSegment(request.delegate)

    internal class DemandResponseResult internal constructor(
        private val delegate: PipeSession.DemandResponseResult,
    ) {
        val segmentCount: Int get() = delegate.segmentCount
        val targetTrackSegmentCount: Int get() = delegate.targetTrackSegmentCount
    }
}
