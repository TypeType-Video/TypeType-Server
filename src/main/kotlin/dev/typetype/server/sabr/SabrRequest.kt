package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest as PipeRequest

internal class SabrSegmentRequest private constructor(
    val format: YoutubeSabrFormat,
    val isInitializationSegment: Boolean,
    val sequenceNumber: Int,
    private val delegateFactory: () -> PipeRequest,
) {
    private val delegateValue: PipeRequest by lazy(LazyThreadSafetyMode.SYNCHRONIZED, delegateFactory)

    internal val delegate: PipeRequest
        get() = delegateValue

    companion object {
        fun initialization(format: YoutubeSabrFormat): SabrSegmentRequest =
            SabrSegmentRequest(format, true, -1) {
                PipeRequest.initialization(format.delegate)
            }

        fun media(format: YoutubeSabrFormat, sequenceNumber: Int): SabrSegmentRequest =
            SabrSegmentRequest(format, false, sequenceNumber) {
                PipeRequest.media(format.delegate, sequenceNumber)
            }.also {
                require(sequenceNumber > 0) { "SABR media sequence number must be positive" }
            }
    }
}
