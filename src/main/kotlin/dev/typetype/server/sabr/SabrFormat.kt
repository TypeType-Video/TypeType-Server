package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat as PipeFormat

/** TypeType's stable representation of a provider media format. */
internal class YoutubeSabrFormat internal constructor(
    internal val delegate: PipeFormat,
) {
    val isAudio: Boolean get() = delegate.isAudio
    val isVideo: Boolean get() = delegate.isVideo
    val itag: Int get() = delegate.itag
    val lastModified: Long get() = delegate.lastModified
    val xtags: String? get() = delegate.xtags
    val mimeType: String? get() = delegate.mimeType
    val audioTrackId: String? get() = delegate.audioTrackId
    val audioTrackDisplayName: String? get() = delegate.audioTrackDisplayName
    val isAudioDefault: Boolean get() = delegate.isAudioDefault
    val isOriginalAudio: Boolean get() = delegate.isOriginalAudio
    val qualityLabel: String? get() = delegate.qualityLabel
    val audioQuality: String? get() = delegate.audioQuality
    val isDrc: Boolean get() = delegate.isDrc
    val width: Int get() = delegate.width
    val height: Int get() = delegate.height
    val bitrate: Int get() = delegate.bitrate
    val contentLength: Long get() = delegate.contentLength
    val approxDurationMs: Long get() = delegate.approxDurationMs
    val initializationUrl: String? get() = delegate.initializationUrl
    val initRangeStart: Long get() = delegate.initRangeStart
    val initRangeEnd: Long get() = delegate.initRangeEnd
}
