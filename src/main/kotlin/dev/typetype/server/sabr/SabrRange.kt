package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.services.youtube.sabr.SabrBufferedRange as PipeRange

internal class SabrBufferedRange internal constructor(
    val itag: Int,
    val lastModified: Long,
    val xtags: String?,
    val startTimeMs: Long,
    val durationMs: Long,
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
    val timescale: Int,
) {
    internal var delegate = PipeRange(
        itag,
        lastModified,
        xtags,
        startTimeMs,
        durationMs,
        startSegmentIndex,
        endSegmentIndex,
        timescale,
    )

    fun summarize(): String = delegate.summarize()

    internal companion object {
        fun fromDelegate(delegate: PipeRange): SabrBufferedRange = SabrBufferedRange(
            delegate.itag,
            delegate.lastModified,
            delegate.xtags,
            delegate.startTimeMs,
            delegate.durationMs,
            delegate.startSegmentIndex,
            delegate.endSegmentIndex,
            delegate.timescale,
        ).also { it.delegate = delegate }
    }
}
