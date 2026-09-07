package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaHeader as PipeHeader
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment as PipeSegment
import java.io.InputStream

internal class SabrMediaHeader internal constructor(
    private val delegate: PipeHeader,
) {
    val headerId: Int get() = delegate.headerId
    val videoId: String? get() = delegate.videoId
    val itag: Int get() = delegate.itag
    val lastModified: Long get() = delegate.lastModified
    val xtags: String? get() = delegate.xtags
    val startRange: Long get() = delegate.startRange
    val compressionAlgorithm: Int get() = delegate.compressionAlgorithm
    val isInitSegment: Boolean get() = delegate.isInitSegment
    val sequenceNumber: Int get() = delegate.sequenceNumber
    val bitrateBps: Long get() = delegate.bitrateBps
    val startMs: Long get() = delegate.startMs
    val durationMs: Long get() = delegate.durationMs
    val contentLength: Long get() = delegate.contentLength
    val timeRangeStartTicks: Long get() = delegate.timeRangeStartTicks
    val timeRangeDurationTicks: Long get() = delegate.timeRangeDurationTicks
    val timeRangeTimescale: Int get() = delegate.timeRangeTimescale
    val sequenceLastModified: Long get() = delegate.sequenceLastModified
    fun summarize(): String = delegate.summarize()
}

internal class SabrMediaSegment private constructor(
    internal val delegate: PipeSegment,
) {
    val header: SabrMediaHeader = SabrMediaHeader(delegate.header)
    val data: ByteArray get() = delegate.data
    fun openStream(): InputStream = delegate.openStream()
    val isDiskBacked: Boolean get() = delegate.isDiskBacked
    val isComplete: Boolean get() = delegate.isComplete
    val hasFailed: Boolean get() = delegate.hasFailed()
    fun delete(): Unit = delegate.delete()
    val length: Int get() = delegate.length

    companion object {
        internal fun fromDelegate(segment: PipeSegment): SabrMediaSegment = SabrMediaSegment(segment)
    }
}
