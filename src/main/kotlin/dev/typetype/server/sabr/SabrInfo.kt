package dev.typetype.server.sabr

import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo as PipeInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat as PipeFormat
import java.util.IdentityHashMap

/** Provider-independent SABR metadata used by TypeType's orchestration layer. */
internal class YoutubeSabrInfo internal constructor(
    internal val delegate: PipeInfo,
    private val formatCache: IdentityHashMap<PipeFormat, YoutubeSabrFormat> = IdentityHashMap(),
) {
    val profile: YoutubeSabrClientProfile
        get() = YoutubeSabrClientProfile.fromDelegate(delegate.profile)
    val videoId: String get() = delegate.videoId
    val cpn: String get() = delegate.cpn
    val clientVersion: String get() = delegate.clientVersion
    val visitorData: String? get() = delegate.visitorData
    val serverAbrStreamingUrl: String? get() = delegate.serverAbrStreamingUrl
    val videoPlaybackUstreamerConfig: String? get() = delegate.videoPlaybackUstreamerConfig
    val isPlayerPoTokenAttached: Boolean get() = delegate.isPlayerPoTokenAttached
    val formats: List<YoutubeSabrFormat>
        get() = delegate.formats.map(::format)

    fun findBestAudioFormat(): YoutubeSabrFormat? = delegate.findBestAudioFormat()?.let(::format)
    fun findLowestVideoFormat(): YoutubeSabrFormat? = delegate.findLowestVideoFormat()?.let(::format)
    fun findFormatByItag(itag: Int): YoutubeSabrFormat? = delegate.findFormatByItag(itag)?.let(::format)

    private fun format(delegateFormat: PipeFormat): YoutubeSabrFormat =
        formatCache.getOrPut(delegateFormat) { YoutubeSabrFormat(delegateFormat) }
}
