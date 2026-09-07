package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrStreamState as PipeState

internal class YoutubeSabrStreamState private constructor(
    internal val delegate: PipeState,
) {
    companion object {
        const val TRACK_MODE_VIDEO_AND_AUDIO: Int = PipeState.TRACK_MODE_VIDEO_AND_AUDIO
        const val TRACK_MODE_AUDIO_ONLY: Int = PipeState.TRACK_MODE_AUDIO_ONLY
        const val TRACK_MODE_VIDEO_ONLY: Int = PipeState.TRACK_MODE_VIDEO_ONLY
        internal fun fromDelegate(delegate: PipeState): YoutubeSabrStreamState = YoutubeSabrStreamState(delegate)
    }

    constructor(audio: YoutubeSabrFormat, video: YoutubeSabrFormat) : this(PipeState(audio.delegate, video.delegate))

    fun ingest(segment: SabrMediaSegment): Boolean = delegate.ingest(segment.delegate)
    fun ingestInitializationData(format: YoutubeSabrFormat, data: ByteArray): Boolean =
        delegate.ingestInitializationData(format.delegate, data)
    val bufferedRanges: List<SabrBufferedRange>
        get() = delegate.bufferedRanges.map(SabrBufferedRange::fromDelegate)
    fun setBufferedRangesOverride(ranges: List<SabrBufferedRange>?): Unit =
        delegate.setBufferedRangesOverride(ranges?.map { it.delegate })
    val playerTimeMs: Long get() = delegate.playerTimeMs
    fun getMinBufferedEndMs(): Long = delegate.minBufferedEndMs
    fun getBufferedEndMs(format: YoutubeSabrFormat): Long = delegate.getBufferedEndMs(format.delegate)
    fun setPlayerTimeMs(value: Long): Unit = delegate.setPlayerTimeMs(value)
    fun clearPlayerTimeMsOverride(): Unit = delegate.clearPlayerTimeMsOverride()
    val playbackCookie: ByteArray? get() = delegate.playbackCookie
    fun setPoToken(value: ByteArray): Unit = delegate.setPoToken(value)
    val poToken: ByteArray? get() = delegate.poToken
    val isComplete: Boolean get() = delegate.isComplete
    val isLive: Boolean get() = delegate.isLive
    val isPostLiveDvr: Boolean get() = delegate.isPostLiveDvr
    val liveHeadSequenceNumber: Long get() = delegate.liveHeadSequenceNumber
    val liveHeadTimeMs: Long get() = delegate.liveHeadTimeMs
    fun isAtLiveEdge(audio: YoutubeSabrFormat, video: YoutubeSabrFormat): Boolean =
        delegate.isAtLiveEdge(audio.delegate, video.delegate)
    fun getMaxSegment(format: YoutubeSabrFormat): Int = delegate.getMaxSegment(format.delegate)
    fun getEndSegment(format: YoutubeSabrFormat): Long = delegate.getEndSegment(format.delegate)
    fun hasSegmentIndex(format: YoutubeSabrFormat): Boolean = delegate.hasSegmentIndex(format.delegate)
    fun isComplete(format: YoutubeSabrFormat): Boolean = delegate.isComplete(format.delegate)
    fun assumeBufferedUntil(format: YoutubeSabrFormat, segment: Int): Unit = delegate.assumeBufferedUntil(format.delegate, segment)
    fun rewindBufferedTo(format: YoutubeSabrFormat, segment: Int): Unit = delegate.rewindBufferedTo(format.delegate, segment)
    fun jumpBufferedTo(format: YoutubeSabrFormat, segment: Int): Unit = delegate.jumpBufferedTo(format.delegate, segment)
    fun setFullyBuffered(format: YoutubeSabrFormat, value: Boolean): Unit = delegate.setFullyBuffered(format.delegate, value)
    fun setLastOnlyRange(format: YoutubeSabrFormat, value: Boolean): Unit = delegate.setLastOnlyRange(format.delegate, value)
    fun setLastOnlyRangesUseObservedTiming(value: Boolean): Unit = delegate.setLastOnlyRangesUseObservedTiming(value)
    fun setBufferedRangeSegmentIndexOffset(value: Int): Unit = delegate.setBufferedRangeSegmentIndexOffset(value)
    fun setBufferedRangeSegmentIndexOffsets(audio: Int, video: Int): Unit = delegate.setBufferedRangeSegmentIndexOffsets(audio, video)
    fun setRequestTrackMode(mode: Int, audio: Boolean, video: Boolean): Unit = delegate.setRequestTrackMode(mode, audio, video)
    fun setActiveTrackTypes(video: Boolean, audio: Boolean): Unit = delegate.setActiveTrackTypes(video, audio)
    fun setAudioOnlyRequestMode(): Unit = delegate.setAudioOnlyRequestMode()
    fun setVideoOnlyRequestMode(): Unit = delegate.setVideoOnlyRequestMode()
    fun setVideoAndAudioRequestMode(): Unit = delegate.setVideoAndAudioRequestMode()
    fun setClientViewport(width: Int, height: Int): Unit = delegate.setClientViewport(width, height)
    fun setBandwidthEstimate(value: Long): Unit = delegate.setBandwidthEstimate(value)
    val bandwidthEstimate: Long get() = delegate.bandwidthEstimate
    val nextRequestPolicy: SabrNextRequestPolicy?
        get() = delegate.nextRequestPolicy?.let(::SabrNextRequestPolicy)
    fun setPlaybackRate(value: Float): Unit = delegate.setPlaybackRate(value)
    fun setWriteTopLevelPlayerTimeMs(value: Boolean): Unit = delegate.setWriteTopLevelPlayerTimeMs(value)
    fun setClientAbrVisibility(value: Int?): Unit = delegate.setClientAbrVisibility(value)
    fun setWriteLastManualSelectedResolution(value: Boolean): Unit = delegate.setWriteLastManualSelectedResolution(value)
    fun setWriteAllPreferredFormats(value: Boolean): Unit = delegate.setWriteAllPreferredFormats(value)
    fun setWriteOfficialWebPreferredFormats(value: Boolean): Unit = delegate.setWriteOfficialWebPreferredFormats(value)
    fun setSelectVideoFormatBeforeAudio(value: Boolean): Unit = delegate.setSelectVideoFormatBeforeAudio(value)
    fun setWriteBufferedRangeTimeRange(value: Boolean): Unit = delegate.setWriteBufferedRangeTimeRange(value)
    fun setStickyResolutionOverride(value: Int?): Unit = delegate.setStickyResolutionOverride(value)
    fun setOfficialWebClientAbrTimingOverrides(a: Long?, b: Long?, c: Long?, d: Long?): Unit =
        delegate.setOfficialWebClientAbrTimingOverrides(a, b, c, d)
    fun setOfficialField68Override(value: Long?): Unit = delegate.setOfficialField68Override(value)
    fun setSabrReportRequestCancellationInfoOverride(value: Int?): Unit =
        delegate.setSabrReportRequestCancellationInfoOverride(value)
    fun setWriteOfficialWebClientAbrFields(value: Boolean): Unit = delegate.setWriteOfficialWebClientAbrFields(value)
    fun summarizeBufferedRanges(): String = delegate.summarizeBufferedRanges()
    fun getAverageSegmentDurationMs(format: YoutubeSabrFormat): Long = delegate.getAverageSegmentDurationMs(format.delegate)
    fun getSegmentStartMs(format: YoutubeSabrFormat, sequence: Int): Long = delegate.getSegmentStartMs(format.delegate, sequence)
    fun getSegmentEndMs(format: YoutubeSabrFormat, sequence: Int): Long = delegate.getSegmentEndMs(format.delegate, sequence)
    fun getSegmentNumberAtOrAfterTimeMs(format: YoutubeSabrFormat, timeMs: Long): Int =
        delegate.getSegmentNumberAtOrAfterTimeMs(format.delegate, timeMs)
}
